package com.mynotes.app

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import kotlin.math.hypot

sealed interface SessionStatus {
    object Loading : SessionStatus
    object Ready : SessionStatus
    class Failed(val message: String) : SessionStatus
}

fun eraseStrokes(list: List<Ink>, at: Offset): List<Ink> =
    list.filterNot { s -> s.pts.any { hypot(it.x - at.x, it.y - at.y) < 0.015f } }

/**
 * حالة مستند مفتوح في تبويب. تبقى حية عند تبديل التبويبات وعند تدوير الشاشة،
 * والكتابة تُحفظ على القرص بعد كل تعديل.
 */
class DocSession(entry: DocEntry) {
    val id: String = entry.id

    var title by mutableStateOf(entry.name)
    var status by mutableStateOf<SessionStatus>(SessionStatus.Loading)
    var pdf by mutableStateOf<PdfDoc?>(null)
    var annotations by mutableStateOf<Map<Int, List<Ink>>>(emptyMap())
    var scale by mutableStateOf(1f)

    val undoStack = mutableStateListOf<Map<Int, List<Ink>>>()
    val redoStack = mutableStateListOf<Map<Int, List<Ink>>>()

    val listState = LazyListState(entry.lastPage.coerceIn(0, maxOf(0, entry.pageCount - 1)), 0)
    val hScroll = ScrollState(0)

    private var snapshot: Map<Int, List<Ink>>? = null
    private var disposed = false

    suspend fun load(file: File, repo: LibraryRepository) {
        try {
            val loaded = withContext(Dispatchers.IO) {
                if (!file.exists()) throw FileNotFoundException()
                val doc = PdfDoc.open(id, file)
                Pair(doc, repo.loadAnnotations(id))
            }
            if (disposed) {
                loaded.first.close()
                return
            }
            pdf = loaded.first
            annotations = loaded.second
            status = SessionStatus.Ready
        } catch (e: FileNotFoundException) {
            status = SessionStatus.Failed("الملف غير موجود، ربما حُذف أو نُقل.")
        } catch (e: SecurityException) {
            status = SessionStatus.Failed("الملف محمي بكلمة مرور ولا يمكن فتحه.")
        } catch (t: Throwable) {
            status = SessionStatus.Failed("تعذر فتح الملف، قد يكون تالفًا.")
        }
    }

    // ---------- التحرير ----------

    fun beginEdit() {
        snapshot = annotations
    }

    fun addInk(page: Int, ink: Ink) {
        annotations = annotations + (page to ((annotations[page] ?: emptyList()) + ink))
    }

    fun eraseAt(page: Int, at: Offset) {
        val cur = annotations[page] ?: return
        val next = eraseStrokes(cur, at)
        if (next.size != cur.size) annotations = annotations + (page to next)
    }

    /** يثبّت العملية في سجل التراجع فقط إذا تغيّر شيء فعلًا، ثم يحفظ على القرص. */
    fun endEdit() {
        val s = snapshot ?: return
        snapshot = null
        if (s === annotations) return
        undoStack.add(s)
        if (undoStack.size > 100) undoStack.removeAt(0)
        redoStack.clear()
        persist()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(annotations)
        annotations = undoStack.removeAt(undoStack.lastIndex)
        persist()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(annotations)
        annotations = redoStack.removeAt(redoStack.lastIndex)
        persist()
    }

    private fun persist() {
        val data = annotations
        AppController.scope.launch { AppController.repo.saveAnnotations(id, data) }
    }

    fun dispose() {
        disposed = true
        pdf?.close()
        pdf = null
    }
}
