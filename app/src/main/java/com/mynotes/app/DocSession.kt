package com.mynotes.app

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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

fun eraseStrokes(list: List<Ink>, at: Offset, radius: Float): List<Ink> =
    list.filterNot { s -> s.pts.any { hypot(it.x - at.x, it.y - at.y) < radius } }

class EditSnapshot(
    val ink: Map<Int, List<Ink>>,
    val images: Map<Int, List<ImageItem>>
)

/**
 * حالة مستند مفتوح في تبويب. تبقى حية عند تبديل التبويبات وعند تدوير الشاشة،
 * والكتابة والصور وبنية الصفحات تُحفظ على القرص بعد كل تعديل.
 */
class DocSession(entry: DocEntry) {
    val id: String = entry.id

    var title by mutableStateOf(entry.name)
    var status by mutableStateOf<SessionStatus>(SessionStatus.Loading)
    var pdf by mutableStateOf<PdfDoc?>(null)

    // ---- التكبير والتحريك ----
    var scale by mutableStateOf(1f)
    var panX by mutableStateOf(0f)

    /** تضبطه شاشة القراءة؛ يكبّر/يصغّر حول مركز العرض (لأزرار + و −). */
    var zoomHook: ((Float) -> Unit)? = null

    // ---- درج الصفحات ----
    var drawerOpen by mutableStateOf(false)
    var drawerWidth by mutableStateOf(150f)

    var annotations by mutableStateOf<Map<Int, List<Ink>>>(emptyMap())
    var images by mutableStateOf<Map<Int, List<ImageItem>>>(emptyMap())
    var selectedImageId by mutableStateOf<String?>(null)

    val undoStack = mutableStateListOf<EditSnapshot>()
    val redoStack = mutableStateListOf<EditSnapshot>()

    val listState = LazyListState(entry.lastPage.coerceIn(0, maxOf(0, entry.pageCount - 1)), 0)

    // ---- القراءة الضوئية والتحديد والبحث ----
    val ocr = mutableStateMapOf<Int, OcrPage>()
    var ocrRunning by mutableStateOf(false)
    var ocrDone by mutableStateOf(0)
    var ocrTotal by mutableStateOf(0)
    var pageBusy by mutableStateOf<Int?>(null)
    var selection by mutableStateOf<TextSelection?>(null)

    var searchOpen by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    var hits by mutableStateOf<List<SearchHit>>(emptyList())
    var hitIndex by mutableStateOf(0)
    var hitNav by mutableStateOf(0)

    private var ocrJob: Job? = null
    private var snapshot: EditSnapshot? = null
    private var disposed = false

    suspend fun load(file: File, repo: LibraryRepository) {
        try {
            val loaded = withContext(Dispatchers.IO) {
                if (!file.exists()) throw FileNotFoundException()
                val doc = PdfDoc.open(id, file, repo.partsDir(id), repo.loadPages(id))
                Triple(doc, repo.loadDocData(id), repo.loadOcrAll(id))
            }
            if (disposed) {
                loaded.first.close()
                return
            }
            pdf = loaded.first
            annotations = loaded.second.ink
            images = loaded.second.images
            ocr.putAll(loaded.third)
            status = SessionStatus.Ready
        } catch (e: FileNotFoundException) {
            status = SessionStatus.Failed("الملف غير موجود، ربما حُذف أو نُقل.")
        } catch (e: SecurityException) {
            status = SessionStatus.Failed("الملف محمي بكلمة مرور ولا يمكن فتحه.")
        } catch (t: Throwable) {
            status = SessionStatus.Failed("تعذر فتح الملف، قد يكون تالفًا.")
        }
    }

    // ---------- التحرير والتراجع ----------

    fun beginEdit() {
        snapshot = EditSnapshot(annotations, images)
    }

    fun addInk(page: Int, ink: Ink) {
        annotations = annotations + (page to ((annotations[page] ?: emptyList()) + ink))
    }

    fun eraseAt(page: Int, at: Offset, radius: Float) {
        val cur = annotations[page] ?: return
        val next = eraseStrokes(cur, at, radius)
        if (next.size != cur.size) annotations = annotations + (page to next)
    }

    /** يثبّت العملية في سجل التراجع فقط إذا تغيّر شيء فعلًا، ثم يحفظ على القرص. */
    fun endEdit() {
        val s = snapshot ?: return
        snapshot = null
        if (s.ink === annotations && s.images === images) return
        undoStack.add(s)
        if (undoStack.size > 100) undoStack.removeAt(0)
        redoStack.clear()
        persist()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(EditSnapshot(annotations, images))
        val s = undoStack.removeAt(undoStack.lastIndex)
        annotations = s.ink
        images = s.images
        selectedImageId = null
        persist()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(EditSnapshot(annotations, images))
        val s = redoStack.removeAt(redoStack.lastIndex)
        annotations = s.ink
        images = s.images
        selectedImageId = null
        persist()
    }

    // ---------- الصور ----------

    fun addImage(item: ImageItem) {
        beginEdit()
        images = images + (item.page to ((images[item.page] ?: emptyList()) + item))
        selectedImageId = item.id
        endEdit()
    }

    /** يعدّل صورة دون تسجيلها في التراجع؛ يجب لفّها بـ beginEdit/endEdit. */
    fun updateImage(imageId: String, transform: (ImageItem) -> ImageItem) {
        val page = images.entries.firstOrNull { e -> e.value.any { it.id == imageId } }?.key ?: return
        val list = images[page] ?: return
        images = images + (page to list.map { if (it.id == imageId) transform(it) else it })
    }

    fun deleteSelectedImage() {
        val imageId = selectedImageId ?: return
        val page = images.entries.firstOrNull { e -> e.value.any { it.id == imageId } }?.key
        if (page == null) {
            selectedImageId = null
            return
        }
        beginEdit()
        images = images + (page to (images[page] ?: emptyList()).filter { it.id != imageId })
        selectedImageId = null
        endEdit()
    }

    // ---------- إدراج صفحات ----------

    /**
     * يُدرج صفحات بعد الصفحة رقم after (من 0؛ -1 = في البداية).
     * تُزاح كتابات المستخدم والصور ونتائج القراءة الضوئية لتبقى مع صفحاتها،
     * ويُمسح سجل التراجع لأن أرقام الصفحات تغيّرت.
     */
    fun insertPages(after: Int, refs: List<PageRef>) {
        val doc = pdf ?: return
        val n = refs.size
        if (n == 0) return
        stopOcr()
        val old = doc.pages
        val at = (after + 1).coerceIn(0, old.size)
        doc.replacePages(old.subList(0, at) + refs + old.subList(at, old.size))

        fun shift(k: Int): Int = if (k >= at) k + n else k
        annotations = annotations.mapKeys { shift(it.key) }
        val movedImages = HashMap<Int, List<ImageItem>>()
        images.forEach { (k, list) ->
            val nk = shift(k)
            movedImages[nk] = list.map { it.copy(page = nk) }
        }
        images = movedImages
        val shiftedOcr = HashMap<Int, OcrPage>()
        ocr.forEach { (k, v) -> shiftedOcr[shift(k)] = v }
        ocr.clear()
        ocr.putAll(shiftedOcr)

        undoStack.clear()
        redoStack.clear()
        snapshot = null
        selection = null
        selectedImageId = null
        if (searchOpen && searchQuery.isNotBlank()) runSearch(searchQuery, navigate = false) else hits = emptyList()

        val pagesSnap = doc.pages
        val ink = annotations
        val imgs = images
        val ocrSnap = ocr.toMap()
        AppController.onPagesChanged(id, pagesSnap.size)
        AppController.scope.launch {
            AppController.repo.savePages(id, pagesSnap)
            AppController.repo.saveDocData(id, ink, imgs)
            withContext(Dispatchers.IO) { AppController.repo.rewriteOcr(id, ocrSnap) }
            if (!disposed) startOcr()
        }
    }

    // ---------- القراءة الضوئية ----------

    /** يقرأ صفحة واحدة (إن لم تكن مقروءة أو تغيّرت كتابة المستخدم عليها). */
    suspend fun ocrPage(page: Int): OcrPage? {
        val doc = pdf ?: return null
        val strokes = annotations[page] ?: emptyList()
        val ver = OcrEngine.versionOf(strokes)
        val existing = ocr[page]
        if (existing != null && existing.version == ver) return existing

        val result: OcrPage? = try {
            withContext(Dispatchers.Default) {
                val ref = doc.pages.getOrNull(page) ?: return@withContext null
                val base = doc.renderFresh(ref, 1800) ?: return@withContext null
                try {
                    val src = OcrEngine.compose(base, strokes)
                    try {
                        OcrPage(OcrEngine.recognize(src), ver)
                    } finally {
                        if (src !== base) src.recycle()
                    }
                } finally {
                    base.recycle()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            null
        }

        if (result != null) {
            ocr[page] = result
            withContext(Dispatchers.IO) { AppController.repo.saveOcrPage(id, page, result) }
        }
        return result
    }

    /** يقرأ كل الصفحات غير المقروءة في الخلفية، ويتوقف مؤقتًا أثناء الكتابة بالقلم. */
    fun startOcr(): Job? {
        if (ocrJob?.isActive == true) return ocrJob
        val doc = pdf ?: return null
        val job = AppController.scope.launch {
            ocrRunning = true
            ocrTotal = doc.count
            ocrDone = 0
            var p = 0
            while (p < doc.count) {
                if (!isActive || disposed) break
                while (isActive && System.currentTimeMillis() - AppController.lastInkAt < 1500L) {
                    delay(300)
                }
                ocrPage(p)
                ocrDone = p + 1
                if (searchOpen && searchQuery.isNotBlank() && p % 5 == 4) {
                    runSearch(searchQuery, navigate = false)
                }
                p++
            }
            ocrRunning = false
            if (searchOpen && searchQuery.isNotBlank()) runSearch(searchQuery, navigate = false)
        }
        ocrJob = job
        return job
    }

    fun stopOcr() {
        ocrJob?.cancel()
        ocrRunning = false
    }

    // ---------- التحديد والنسخ ----------

    fun selectionText(): String {
        val sel = selection ?: return ""
        val words = ocr[sel.page]?.words ?: return ""
        val a = minOf(sel.start, sel.end)
        val b = maxOf(sel.start, sel.end)
        val sb = StringBuilder()
        var lastLine = -1
        for (i in a..b) {
            val w = words.getOrNull(i) ?: continue
            if (lastLine != -1) sb.append(if (w.line == lastLine) " " else "\n")
            sb.append(w.text)
            lastLine = w.line
        }
        return sb.toString()
    }

    // ---------- البحث ----------

    fun runSearch(q: String, navigate: Boolean = true) {
        searchQuery = q
        val tokens = q.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            hits = emptyList()
            hitIndex = 0
            return
        }
        val out = ArrayList<SearchHit>()
        for (page in ocr.keys.sorted()) {
            val words = ocr[page]?.words ?: continue
            var i = 0
            while (i + tokens.size <= words.size) {
                var ok = true
                for (j in tokens.indices) {
                    if (!words[i + j].text.lowercase().contains(tokens[j])) {
                        ok = false
                        break
                    }
                }
                if (ok) {
                    out.add(SearchHit(page, i, i + tokens.size - 1))
                    i += tokens.size
                } else {
                    i++
                }
            }
        }
        hits = out
        if (navigate) {
            hitIndex = 0
            hitNav++
        } else if (hitIndex >= out.size) {
            hitIndex = 0
        }
    }

    fun stepHit(delta: Int) {
        if (hits.isEmpty()) return
        hitIndex = (hitIndex + delta + hits.size) % hits.size
        hitNav++
    }

    fun closeSearch() {
        searchOpen = false
        searchQuery = ""
        hits = emptyList()
        hitIndex = 0
    }

    private fun persist() {
        val ink = annotations
        val imgs = images
        AppController.scope.launch { AppController.repo.saveDocData(id, ink, imgs) }
    }

    fun dispose() {
        disposed = true
        ocrJob?.cancel()
        pdf?.close()
        pdf = null
    }
}
