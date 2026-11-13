package com.hamishnotes.app

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    private val stroke = Ink(listOf(Pt(0.1f, 0.2f, 0.33333334f), Pt(1.0E-4f, 0.70710677f, 1f)), Color.Black, 0.004f, Tool.BALLPOINT, 5L)

    private fun data(n: Int) = DocData(mapOf(0 to List(n) { stroke }), emptyMap())

    private fun annotations(id: String) = File(ctx.filesDir, "annotations/$id.json")

    /** النقاط تُكتب بأقصر صيغة وتُقرأ كما هي تمامًا. */
    @Test
    fun strokePointsRoundTripExactlyAndCompactly(): Unit = runBlocking {
        val repo = LibraryRepository(ctx)
        repo.saveDocData("d-float", data(1))
        val text = annotations("d-float").readText()
        assertTrue(text, text.contains("[0.1,0.2,0.33333334,1.0E-4,0.70710677,1]"))
        assertFalse(text.contains("0.10000000149"))
        val back = repo.loadDocData("d-float").ink[0]!![0].pts
        assertEquals(stroke.pts.map { Triple(it.x, it.y, it.p) }, back.map { Triple(it.x, it.y, it.p) })
    }

    /** ملف كتابة تالف: يُقرأ آخر حفظ سليم قبله، والحفظ التالي لا يضع التالف مكان النسخة السليمة. */
    @Test
    fun corruptAnnotationsFallBackToThePreviousSave(): Unit = runBlocking {
        val repo = LibraryRepository(ctx)
        repo.saveDocData("d-bak", data(1))
        repo.saveDocData("d-bak", data(2))
        annotations("d-bak").writeText("{ broken")

        val again = LibraryRepository(ctx)
        assertEquals(1, again.loadDocDataOrNull("d-bak")!!.ink[0]!!.size)
        again.saveDocData("d-bak", data(3))
        assertEquals(3, again.loadDocData("d-bak").ink[0]!!.size)
        // النسخة الاحتياطية ما زالت الحفظ السليم الأول، لا الملف التالف
        annotations("d-bak").writeText("{ broken again")
        assertEquals(1, LibraryRepository(ctx).loadDocDataOrNull("d-bak")!!.ink[0]!!.size)
    }

    /** تالف بلا نسخة سابقة: null (لا بيانات فارغة يُكتب بها فوق الملف)، ومستند بلا ملف: بيانات فارغة. */
    @Test
    fun unreadableAnnotationsAreReportedNotReplacedByEmptyData(): Unit = runBlocking {
        val repo = LibraryRepository(ctx)
        annotations("d-bad").apply { parentFile!!.mkdirs() }.writeText("{ broken")
        assertNull(repo.loadDocDataOrNull("d-bad"))
        assertTrue(File(ctx.filesDir, "annotations/d-bad.json.bad").exists())
        assertTrue(repo.loadDocDataOrNull("d-none")!!.ink.isEmpty())
    }
}
