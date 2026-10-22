package com.mynotes.app

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import kotlin.math.hypot

sealed interface SessionStatus {
    object Loading : SessionStatus
    object Ready : SessionStatus
    class Failed(val message: String) : SessionStatus
}

fun eraseStrokes(list: List<Ink>, at: Offset, radius: Float): List<Ink> =
    list.filterNot { s -> s.pts.any { hypot(it.x - at.x, it.y - at.y) < radius } }

/** طلب فتح نافذة مربع النص: boxId = null لإنشاء مربع جديد في الصفحة page. */
data class TextDialogReq(val boxId: String?, val page: Int)

class EditSnapshot(
    val ink: Map<Int, List<Ink>>,
    val images: Map<Int, List<ImageItem>>,
    val marks: Map<Int, List<TextMark>>,
    val texts: Map<Int, List<TextBox>>,
    val edits: SummaryEdits
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

    /** تمييزات النص الملتقطة (مصدر درج «ملخصات»)، ومربعات النص المكتوب. */
    var marks by mutableStateOf<Map<Int, List<TextMark>>>(emptyMap())
    var texts by mutableStateOf<Map<Int, List<TextBox>>>(emptyMap())
    var selectedTextId by mutableStateOf<String?>(null)

    var textDialog by mutableStateOf<TextDialogReq?>(null)

    // ---- درج الملخصات ----
    var summaryOpen by mutableStateOf(false)

    /** تعديلات المستخدم على الملخص (عنوان/نص، جداول، ترتيب) فوق الترتيب التلقائي. */
    var summaryEdits by mutableStateOf(SummaryEdits())

    // ---- بطاقات المستخدم، والنجوم، والعلامات المرجعية ----
    var customCards by mutableStateOf<List<Card>>(emptyList())
    var starred by mutableStateOf<Set<String>>(emptySet())
    var bookmarks by mutableStateOf<Set<Int>>(emptySet())

    /** نص يُبحث عنه فور فتح المستند (من البحث في كل المكتبة). */
    var pendingQuery: String? = null

    var pendingPage: Int = 0

    /** يطبق بحثًا مؤجلًا (من البحث في كل المكتبة): يفتح شريط البحث وينتقل لأول نتيجة في الصفحة المطلوبة. */
    fun applyPending() {
        val q = pendingQuery ?: return
        pendingQuery = null
        searchOpen = true
        runSearch(q, navigate = false)
        val i = hits.indexOfFirst { it.page >= pendingPage }
        hitIndex = if (i >= 0) i else 0
        if (hits.isNotEmpty()) hitNav++
    }

    fun addCustomCard(front: String, back: String) {
        val f = front.trim()
        val b = back.trim()
        if (f.isEmpty() || b.isEmpty()) return
        customCards = customCards + Card("u:" + UUID.randomUUID().toString(), f, b)
        persist()
        AppController.messages.tryEmit("أُضيفت البطاقة")
    }

    fun deleteCustomCard(cardId: String) {
        customCards = customCards.filter { it.id != cardId }
        starred = starred - cardId
        persist()
    }

    fun toggleStar(cardId: String) {
        starred = if (cardId in starred) starred - cardId else starred + cardId
        persist()
    }

    fun toggleBookmark(page: Int) {
        bookmarks = if (page in bookmarks) bookmarks - page else bookmarks + page
        persist()
        AppController.messages.tryEmit(if (page in bookmarks) "أُضيفت علامة على الصفحة ${page + 1}" else "أُزيلت العلامة")
    }

    /** تضبطهما شاشة القراءة: الانتقال إلى موضع تمييز، وملاءمة الصفحة كاملة/عرض الشاشة. */
    var jumpHook: ((TextMark) -> Unit)? = null
    var fitHook: (() -> Unit)? = null

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

    /** النص الرقمي داخل ملفات PDF (يدعم العربية). يُنشأ عند فتح المستند. */
    private var textLayer: PdfTextLayer? = null
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
            val partsDir = repo.partsDir(id)
            textLayer = PdfTextLayer { src ->
                when {
                    src == "main" -> file
                    src.startsWith("part:") -> File(partsDir, src.removePrefix("part:"))
                    else -> null
                }
            }
            annotations = loaded.second.ink
            images = loaded.second.images
            marks = loaded.second.marks
            texts = loaded.second.texts
            summaryEdits = loaded.second.edits
            customCards = loaded.second.cards
            starred = loaded.second.starred
            bookmarks = loaded.second.bookmarks
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

    private fun snap() = EditSnapshot(annotations, images, marks, texts, summaryEdits)

    private fun restore(s: EditSnapshot) {
        annotations = s.ink
        images = s.images
        marks = s.marks
        texts = s.texts
        summaryEdits = s.edits
        selectedImageId = null
        selectedTextId = null
    }

    fun beginEdit() {
        snapshot = snap()
    }

    fun addInk(page: Int, ink: Ink) {
        annotations = annotations + (page to ((annotations[page] ?: emptyList()) + ink))
    }

    fun eraseAt(page: Int, at: Offset, radius: Float) {
        val cur = annotations[page]
        if (cur != null) {
            val next = eraseStrokes(cur, at, radius)
            if (next.size != cur.size) annotations = annotations + (page to next)
        }
        // الممحاة تزيل من التمييز الملتقط الكلمات التي تمرّ عليها فقط، ويبقى باقي التمييز ونصه في الملخص
        val ms = marks[page] ?: return
        val words = ocr[page]?.words
        var changed = false
        val next = ms.mapNotNull { m ->
            val touched = m.rects.any { r ->
                at.x >= r.l - radius && at.x <= r.r + radius && at.y >= r.t - radius && at.y <= r.b + radius
            }
            if (!touched) return@mapNotNull m
            if (words == null) {
                // بدون كلمات الصفحة: نزيل المقاطع التي لمستها الممحاة فقط
                val rest = m.rects.filterNot { r ->
                    at.x >= r.l - radius && at.x <= r.r + radius && at.y >= r.t - radius && at.y <= r.b + radius
                }
                changed = true
                return@mapNotNull if (rest.isEmpty()) null else m.copy(rects = rest)
            }
            val inside = TextLayout.wordsInside(words, m.rects)
            val erased = inside.filter { i ->
                val w = words[i]
                at.x >= w.l - radius && at.x <= w.r + radius && at.y >= w.t - radius && at.y <= w.b + radius
            }.toSet()
            if (erased.isEmpty()) return@mapNotNull m
            changed = true
            val remaining = inside.filter { it !in erased }
            if (remaining.isEmpty()) return@mapNotNull null
            val rects = TextLayout.rectsFor(words, remaining)
            m.copy(
                rects = rects,
                text = TextLayout.buildText(words, remaining).trim(),
                strongRects = m.strongRects.filter { s -> TextLayout.overlap(listOf(s), rects) > 0f }
            )
        }
        if (changed) marks = marks + (page to next)
    }

    /** يثبّت العملية في سجل التراجع فقط إذا تغيّر شيء فعلًا، ثم يحفظ على القرص. */
    fun endEdit() {
        val s = snapshot ?: return
        snapshot = null
        if (s.ink === annotations && s.images === images && s.marks === marks && s.texts === texts &&
            s.edits === summaryEdits
        ) return
        undoStack.add(s)
        if (undoStack.size > 100) undoStack.removeAt(0)
        redoStack.clear()
        persist()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(snap())
        restore(undoStack.removeAt(undoStack.lastIndex))
        persist()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(snap())
        restore(redoStack.removeAt(redoStack.lastIndex))
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
        val movedMarks = HashMap<Int, List<TextMark>>()
        marks.forEach { (k, list) ->
            val nk = shift(k)
            movedMarks[nk] = list.map { it.copy(page = nk) }
        }
        marks = movedMarks
        val movedTexts = HashMap<Int, List<TextBox>>()
        texts.forEach { (k, list) ->
            val nk = shift(k)
            movedTexts[nk] = list.map { it.copy(page = nk) }
        }
        texts = movedTexts
        bookmarks = bookmarks.map { shift(it) }.toSet()
        val shiftedOcr = HashMap<Int, OcrPage>()
        ocr.forEach { (k, v) -> shiftedOcr[shift(k)] = v }
        ocr.clear()
        ocr.putAll(shiftedOcr)

        undoStack.clear()
        redoStack.clear()
        snapshot = null
        selection = null
        selectedImageId = null
        selectedTextId = null
        if (searchOpen && searchQuery.isNotBlank()) runSearch(searchQuery, navigate = false) else hits = emptyList()

        val pagesSnap = doc.pages
        val data = DocData(annotations, images, marks, texts, summaryEdits, customCards, starred, bookmarks)
        val ocrSnap = ocr.toMap()
        AppController.onPagesChanged(id, pagesSnap.size)
        AppController.scope.launch {
            AppController.repo.savePages(id, pagesSnap)
            AppController.repo.saveDocData(id, data)
            withContext(Dispatchers.IO) { AppController.repo.rewriteOcr(id, ocrSnap) }
            if (!disposed) startOcr()
        }
    }

    // ---------- القراءة الضوئية ----------

    /** يقرأ صفحة واحدة (إن لم تكن مقروءة أو تغيّرت كتابة المستخدم عليها). */
    suspend fun ocrPage(page: Int): OcrPage? {
        val doc = pdf ?: return null
        val strokes = annotations[page] ?: emptyList()
        // الإصدار يشمل مصدر القراءة (طبقة النص)، فتُعاد قراءة الصفحات المحفوظة بالطريقة القديمة مرة واحدة
        val ver = OcrEngine.versionOf(strokes) * 31 + 2
        val existing = ocr[page]
        if (existing != null && existing.version == ver) return existing

        val result: OcrPage? = try {
            withContext(Dispatchers.Default) {
                val ref = doc.pages.getOrNull(page) ?: return@withContext null
                // 1) النص الرقمي داخل الملف: دقيق ويدعم العربية
                val layerWords = try {
                    textLayer?.words(ref) ?: emptyList()
                } catch (t: Throwable) {
                    emptyList()
                }
                val handwriting = strokes.any { it.tool != Tool.HIGHLIGHTER }
                if (layerWords.size >= 3 && !handwriting) return@withContext OcrPage(layerWords, ver)
                // 2) القراءة الضوئية: للصفحات الممسوحة ضوئيًا، ولخط اليد فوق صفحة رقمية
                val base = doc.renderFresh(ref, 1800) ?: return@withContext null
                try {
                    val src = OcrEngine.compose(base, strokes)
                    try {
                        val seen = OcrEngine.recognize(src)
                        if (layerWords.size >= 3) {
                            // نص الملف + ما قرأته القراءة الضوئية من خط اليد فقط (الكلمات التي لا تقع على نص الملف)
                            val offset = (layerWords.maxOfOrNull { it.line } ?: 0) + 1
                            val extra = seen.filter { w ->
                                val cx = (w.l + w.r) / 2f
                                val cy = (w.t + w.b) / 2f
                                layerWords.none { o -> cx >= o.l && cx <= o.r && cy >= o.t && cy <= o.b }
                            }.map { it.copy(line = it.line + offset) }
                            OcrPage(layerWords + extra, ver)
                        } else {
                            OcrPage(seen, ver)
                        }
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

    // ---------- التمييز الملتقط والملخصات ----------

    /** يضيف تمييزًا ملتقطًا (منطق الدمج في MarkMerge). */
    private fun addMark(page: Int, words: List<OcrWord>, idx: List<Int>, color: Color) {
        val res = MarkMerge.add(
            marks[page] ?: emptyList(), page, words, idx, color, System.currentTimeMillis()
        ) { UUID.randomUUID().toString() } ?: return
        // قد يكتمل هذا بعد قراءة الصفحة في الخلفية أثناء حركة أخرى للمستخدم؛ لا نكسر لقطتها
        val nested = snapshot != null
        if (!nested) beginEdit()
        marks = marks + (page to res.marks)
        if (!nested) endEdit()
        res.message?.let { AppController.messages.tryEmit(it) }
    }

    /**
     * يحوّل خط هايلايتر إلى تمييز ملتقط على النص. إن لم تكن الصفحة مقروءة بعد تُقرأ أولًا.
     * إن لم يوجد نص مقروء تحت الخط يُحفظ كتمييز حر عادي كي لا يضيع ما رسمه المستخدم.
     */
    fun snapHighlight(page: Int, pts: List<Pt>, color: Color, width: Float) {
        fun applyTo(words: List<OcrWord>?) {
            val idx = if (words == null) emptyList() else TextLayout.wordsUnder(words, pts, width)
            if (words != null && idx.isNotEmpty()) {
                addMark(page, words, idx, color)
            } else {
                val nested = snapshot != null
                if (!nested) beginEdit()
                addInk(page, Ink(pts, color, width, Tool.HIGHLIGHTER))
                if (!nested) endEdit()
                AppController.messages.tryEmit("لا يوجد نص مقروء تحت التمييز، فحُفظ كتمييز حر")
            }
        }
        val ready = ocr[page]?.words
        if (ready != null) {
            applyTo(ready)
            return
        }
        val refAtStart = pdf?.pages?.getOrNull(page)
        AppController.scope.launch {
            pageBusy = page
            val res = try {
                ocrPage(page)
            } finally {
                pageBusy = null
            }
            // إن أُضيفت صفحات أثناء القراءة تغيّر رقم الصفحة؛ نتجاهل التمييز بدل وضعه في صفحة خاطئة
            if (disposed || pdf?.pages?.getOrNull(page) !== refAtStart) return@launch
            applyTo(res?.words)
        }
    }

    /** يميّز النص المحدد بالضغط المطوّل ويضيفه إلى الملخصات. */
    fun markSelection(color: Color) {
        val sel = selection ?: return
        val words = ocr[sel.page]?.words ?: return
        val a = minOf(sel.start, sel.end)
        val b = maxOf(sel.start, sel.end)
        addMark(sel.page, words, (a..b).toList(), color)
        selection = null
        AppController.messages.tryEmit("أُضيف النص إلى الملخصات")
    }

    /** كل التمييزات مرتبة حسب الصفحة ثم موضعها في الصفحة. */
    fun allMarks(): List<TextMark> = MarkMerge.readingOrder(marks.values)

    fun updateMarkText(markId: String, text: String) {
        val page = marks.entries.firstOrNull { e -> e.value.any { it.id == markId } }?.key ?: return
        beginEdit()
        marks = marks + (page to (marks[page] ?: emptyList()).map { if (it.id == markId) it.copy(text = text) else it })
        endEdit()
    }

    fun deleteMark(markId: String) {
        val page = marks.entries.firstOrNull { e -> e.value.any { it.id == markId } }?.key ?: return
        beginEdit()
        marks = marks + (page to (marks[page] ?: emptyList()).filter { it.id != markId })
        endEdit()
    }

    // ---------- تحرير الملخص ----------

    private fun changeEdits(f: (SummaryEdits) -> SummaryEdits) {
        val next = f(summaryEdits)
        if (next == summaryEdits) return
        beginEdit()
        summaryEdits = next
        endEdit()
    }

    /** kind: "h" عنوان، "p" نص عادي، null = كما يكتشفه البرنامج. */
    fun setBlockKind(key: String, kind: String?) = changeEdits { e ->
        e.copy(kinds = if (kind == null) e.kinds - key else e.kinds + (key to kind))
    }

    /** يحرك جزءًا خطوة لأعلى (-1) أو لأسفل (+1) في الترتيب المعروض حاليًا. */
    fun moveBlock(shown: List<String>, key: String, delta: Int) {
        val i = shown.indexOf(key)
        val j = i + delta
        if (i < 0 || j < 0 || j >= shown.size) return
        val next = shown.toMutableList()
        next[i] = shown[j]
        next[j] = key
        changeEdits { it.copy(order = next) }
    }

    /** يجمع أجزاء مختارة في جدول بعدد أعمدة cols (0 = كل نص صف). */
    fun makeTable(keys: List<String>, cols: Int) = makeTableImpl(keys, oneRow = false, cols = cols)

    /** يجمع أجزاء مختارة في جدول. الجداول التي كانت تضم أحدها تُدمج فيه. */
    fun makeTable(keys: List<String>, oneRow: Boolean) = makeTableImpl(keys, oneRow, 0)

    private fun makeTableImpl(keys: List<String>, oneRow: Boolean, cols: Int) {
        if (keys.isEmpty()) return
        changeEdits { e ->
            val expanded = ArrayList<String>()
            val absorbed = HashSet<String>()
            for (k in keys) {
                val g = if (k.startsWith("g:")) e.tables.firstOrNull { "g:" + it.id == k } else null
                if (g != null) {
                    expanded.addAll(g.keys)
                    absorbed.add(g.id)
                } else {
                    expanded.add(k)
                }
            }
            val group = TableGroup(UUID.randomUUID().toString(), expanded.distinct(), oneRow = oneRow, cols = cols)
            val gKey = "g:" + group.id
            // إن كان هناك ترتيب يدوي: الجدول يأخذ مكان أول عضو فيه
            val memberKeys = keys.toSet() + expanded
            val firstPos = e.order.indexOfFirst { it in memberKeys }
            val order = if (firstPos < 0) e.order else e.order.mapIndexedNotNull { i, k ->
                when {
                    i == firstPos -> gKey
                    k in memberKeys -> null
                    else -> k
                }
            }
            e.copy(
                tables = e.tables.filter { it.id !in absorbed && it.keys.none { k -> k in expanded } } + group,
                order = order
            )
        }
    }

    /** يحفظ محتوى جدول عدّله المستخدم في محرر الجدول (لجدول تلقائي أو جدول من صنعه). */
    fun saveTable(blockKey: String, rows: List<List<String>>, header: Boolean) = changeEdits { e ->
        if (blockKey.startsWith("g:")) {
            e.copy(tables = e.tables.map { g ->
                if ("g:" + g.id == blockKey) g.copy(rows = rows, header = header) else g
            })
        } else {
            val g = TableGroup(UUID.randomUUID().toString(), listOf(blockKey), rows = rows, header = header)
            e.copy(
                tables = e.tables + g,
                order = e.order.map { if (it == blockKey) "g:" + g.id else it }
            )
        }
    }

    /** يضم أجزاء أخرى إلى جدول موجود كصفوف جديدة. newRows = نصوصها كصفوف (لجدول معدَّل يدويًا). */
    fun addToTable(tableKey: String, keys: List<String>, newRows: List<List<String>>) = changeEdits { e0 ->
        // جداول من صنع المستخدم ضمن الاختيار: تُفك إلى أجزائها وتُحذف كجداول مستقلة
        val absorbed = HashSet<String>()
        val add = ArrayList<String>()
        for (k in keys) {
            if (k == tableKey) continue
            val g = if (k.startsWith("g:")) e0.tables.firstOrNull { "g:" + it.id == k } else null
            if (g != null) {
                absorbed.add(g.id)
                add.addAll(g.keys)
            } else {
                add.add(k)
            }
        }
        if (add.isEmpty()) return@changeEdits e0
        val e = e0.copy(tables = e0.tables.filter { it.id !in absorbed })
        val order = e.order.filter { it !in add && it.removePrefix("g:") !in absorbed }
        if (tableKey.startsWith("g:")) {
            e.copy(
                tables = e.tables.map { g ->
                    if ("g:" + g.id == tableKey) g.copy(
                        keys = (g.keys + add).distinct(),
                        rows = g.rows?.let { it + newRows }
                    ) else g
                },
                order = order
            )
        } else {
            val g = TableGroup(UUID.randomUUID().toString(), listOf(tableKey) + add)
            e.copy(tables = e.tables + g, order = order.map { if (it == tableKey) "g:" + g.id else it })
        }
    }

    fun removeTable(groupKey: String) = changeEdits { e ->
        e.copy(tables = e.tables.filter { "g:" + it.id != groupKey })
    }

    /** يعيد جزءًا واحدًا إلى ما اكتشفه البرنامج تلقائيًا. */
    fun resetBlock(key: String) = changeEdits { e ->
        e.copy(
            kinds = e.kinds - key,
            order = e.order - key,
            tables = e.tables.filter { "g:" + it.id != key }.map { g -> g.copy(keys = g.keys - key) }.filter { it.keys.isNotEmpty() }
        )
    }

    fun resetSummaryEdits() = changeEdits { SummaryEdits() }

    // ---------- مربعات النص ----------

    fun addTextBox(item: TextBox) {
        beginEdit()
        texts = texts + (item.page to ((texts[item.page] ?: emptyList()) + item))
        selectedTextId = item.id
        selectedImageId = null
        endEdit()
    }

    fun textBox(boxId: String?): TextBox? =
        if (boxId == null) null else texts.values.firstNotNullOfOrNull { l -> l.firstOrNull { it.id == boxId } }

    /** يعدّل مربع نص دون تسجيله في التراجع؛ يجب لفّها بـ beginEdit/endEdit. */
    fun updateTextBox(boxId: String, transform: (TextBox) -> TextBox) {
        val page = texts.entries.firstOrNull { e -> e.value.any { it.id == boxId } }?.key ?: return
        val list = texts[page] ?: return
        texts = texts + (page to list.map { if (it.id == boxId) transform(it) else it })
    }

    fun deleteTextBox(boxId: String) {
        val page = texts.entries.firstOrNull { e -> e.value.any { it.id == boxId } }?.key ?: return
        beginEdit()
        texts = texts + (page to (texts[page] ?: emptyList()).filter { it.id != boxId })
        if (selectedTextId == boxId) selectedTextId = null
        endEdit()
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

    fun persist() {
        val data = DocData(annotations, images, marks, texts, summaryEdits, customCards, starred, bookmarks)
        AppController.scope.launch { AppController.repo.saveDocData(id, data) }
    }

    fun dispose() {
        disposed = true
        ocrJob?.cancel()
        textLayer?.close()
        textLayer = null
        pdf?.close()
        pdf = null
    }
}
