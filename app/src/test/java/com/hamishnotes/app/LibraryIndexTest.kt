package com.hamishnotes.app

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * فهرس المكتبة: تلفه لا يُفرغ المكتبة (يُستعاد من النسخة السابقة)، والحفظ يترك نسخة احتياطية.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryIndexTest {
    private lateinit var ctx: Context
    private val index get() = File(ctx.filesDir, "library.json")
    private val backup get() = File(ctx.filesDir, "library.json.bak")

    private fun entry(id: String, name: String) = DocEntry(
        id = id, name = name, type = DocType.PDF, fileName = "$id.pdf", sizeBytes = 10, pageCount = 1,
        addedAt = 1L, lastOpened = 0L, favorite = false, lastPage = 0, locked = true
    )

    @Before
    fun setUp() {
        ctx = RuntimeEnvironment.getApplication()
        index.delete()
        backup.delete()
    }

    @Test
    fun saveKeepsPreviousIndexAsBackup(): Unit = runBlocking {
        val repo = LibraryRepository(ctx)
        repo.saveIndex(listOf(entry("a", "First")))
        repo.saveIndex(listOf(entry("a", "First"), entry("b", "Second")))
        assertTrue(backup.exists())
        assertEquals(listOf("a"), LibraryRepository(ctx).let { r ->
            // النسخة الاحتياطية = الفهرس السابق
            index.delete()
            r.loadIndex().map { it.id }
        })
    }

    @Test
    fun corruptIndexRecoversFromBackup(): Unit = runBlocking {
        val repo = LibraryRepository(ctx)
        repo.saveIndex(listOf(entry("a", "First"), entry("b", "Second")))
        repo.saveIndex(listOf(entry("a", "First"), entry("b", "Second")))
        index.writeText("{ not json")
        val loaded = LibraryRepository(ctx).loadIndex()
        assertEquals(listOf("a", "b"), loaded.map { it.id })
        // علامة القفل تبقى بعد الاستعادة
        assertTrue(loaded.all { it.locked })
        assertTrue(File(ctx.filesDir, "library.json.bad").exists())
    }

    @Test
    fun writeErrorsAreReported(): Unit = runBlocking {
        val repo = LibraryRepository(ctx)
        var reported = 0
        repo.onWriteError = { reported++ }
        // مجلد بنفس اسم الفهرس: الكتابة تفشل، ويجب أن يُبلَّغ بها لا أن تُبلع
        index.delete()
        index.mkdirs()
        File(index, "x").writeText("x")
        repo.saveIndex(listOf(entry("a", "First")))
        assertTrue(reported > 0)
        index.deleteRecursively()
    }
}
