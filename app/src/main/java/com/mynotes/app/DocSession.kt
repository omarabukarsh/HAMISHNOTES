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
    val texts: Map<Int, List<TextBox>>
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
            marks = loaded.second.marks
            texts = loaded.second.texts
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

    private fun snap() = EditSnapshot(annotations, images, marks, texts)

    private fun restore(s: EditSnapshot) {
        annotations = s.ink
        images = s.images
        marks = s.marks
        texts = s.texts
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
        // الممحاة تزيل أيضًا التمييز الملتقط الذي تمرّ عليه (ويختفي من الملخصات)
        val ms = marks[page] ?: return
        val keep = ms.filterNot { m ->
            m.rects.any { r ->
                at.x >= r.l - radius && at.x <= r.r + radius && at.y >= r.t - radius && at.y <= r.b + radius
            }
        }
        if (keep.size != ms.size) marks = marks + (page to keep)
    }

    /** يثبّت العملية في سجل التراجع فقط إذا تغيّر شيء فعلًا، ثم يحفظ على القرص. */
    fun endEdit() {
        val s = snapshot ?: return
        snapshot = null
        if (s.ink === annotations && s.images === images && s.marks === marks && s.texts === texts) return
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
        val data = DocData(annotations, images, marks, texts)
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

    // ---------- التمييز الملتقط والملخصات ----------

    /**
     * يضيف تمييزًا ملتقطًا. إن ظُلّل نفس النص مرة ثانية لا يُكرَّر في الملخص؛ بل يصبح «مهمًا»:
     *  - نفس المساحة تقريبًا  → التمييز القديم كله مهم (خط عريض وتظليل أقوى)
     *  - جزء داخل تمييز قديم → هذا الجزء وحده يُبرز داخل التمييز القديم
     *  - تمييز أكبر يحتوي القديم → يحلّ محلّه، ويبقى الجزء القديم بارزًا
     */
    private fun addMark(page: Int, words: List<OcrWord>, idx: List<Int>, color: Color) {
        val text = TextLayout.buildText(words, idx).trim()
        if (text.isEmpty()) return
        val rects = TextLayout.rectsFor(words, idx)
        val newArea = TextLayout.area(rects).coerceAtLeast(1e-9f)
        val existing = marks[page] ?: emptyList()

        var next: List<TextMark>? = null
        var message: String? = null
        fun addStrong(list: List<MarkRect>, add: List<MarkRect>): List<MarkRect> =
            if (TextLayout.overlap(add, list) / newArea >= 0.6f) list else list + add

        // 1) نفس المساحة أو جزء داخل تمييز قديم
        for (old in existing) {
            val ov = TextLayout.overlap(rects, old.rects)
            if (ov <= 0f) continue
            val oldArea = TextLayout.area(old.rects).coerceAtLeast(1e-9f)
            val ofNew = ov / newArea
            val ofOld = ov / oldArea
            if (ofNew >= 0.6f && ofOld >= 0.6f) {
                next = existing.map { if (it.id == old.id) it.copy(level = 2, strongRects = it.rects) else it }
                message = "ظُلّل النص مرة ثانية، فصار مهمًا في الملخص"
                break
            }
            if (ofNew >= 0.6f) {
                val flat = TextTidy.clean(text.replace("# ", "").replace(" | ", " "))
                next = existing.map {
                    if (it.id == old.id) it.copy(
                        emphasis = (it.emphasis + flat).distinct(),
                        strongRects = if (it.level >= 2) it.strongRects else addStrong(it.strongRects, rects)
                    ) else it
                }
                message = "أُبرز هذا الجزء كمهم في الملخص"
                break
            }
        }
        // 2) تمييز أكبر يحتوي تمييزات قديمة: يحلّ محلّها كلها ويحتفظ بما كان مهمًا فيها
        if (next == null) {
            val contained = existing.filter { old ->
                val oldArea = TextLayout.area(old.rects).coerceAtLeast(1e-9f)
                TextLayout.overlap(rects, old.rects) / oldArea >= 0.6f
            }
            if (contained.isNotEmpty()) {
                var emphasis = emptyList<String>()
                var strong = emptyList<MarkRect>()
                for (old in contained) {
                    val oldFlat = TextTidy.clean(old.text.replace("# ", "").replace(" | ", " "))
                    emphasis = if (old.level >= 2) emphasis + oldFlat else emphasis + old.emphasis
                    strong = strong + (if (old.level >= 2) old.rects else old.strongRects)
                }
                val merged = TextMark(
                    id = UUID.randomUUID().toString(), page = page, rects = rects, color = color,
                    text = text, createdAt = contained.minOf { it.createdAt },
                    emphasis = emphasis.distinct(), strongRects = strong.distinct()
                )
                val ids = contained.map { it.id }.toSet()
                next = existing.filter { it.id !in ids } + merged
            }
        }
        // 3) تمييز ملاصق لتمييز سابق (جزء ثم الجزء الذي يليه): يُدمجان في تمييز واحد يُعاد بناء نصه
        //    من كلمات الصفحة بترتيبها الصحيح، بدل أن يظهرا مقطعين معكوسين في الملخص
        if (next == null) {
            // ترتيب القراءة لكل كلمات الصفحة، لمعرفة هل الكلمات متتالية فعلًا (لا مجرد قريبة)
            val order = TextLayout.visualRows(words, words.indices.toList()).flatten()
            val pos = IntArray(words.size).also { p -> order.forEachIndexed { k, i -> p[i] = k } }
            fun wordsIn(m: TextMark): List<Int> = words.indices.filter { i ->
                val w = words[i]
                val cx = (w.l + w.r) / 2f
                val cyw = (w.t + w.b) / 2f
                m.rects.any { r -> cx >= r.l && cx <= r.r && cyw >= r.t && cyw <= r.b }
            }
            val newMin = idx.minOf { pos[it] }
            val newMax = idx.maxOf { pos[it] }
            fun touches(old: TextMark): Boolean {
                if (old.color != color) return false
                val o = wordsIn(old)
                if (o.isEmpty()) return false
                val oMin = o.minOf { pos[it] }
                val oMax = o.maxOf { pos[it] }
                return oMax + 1 >= newMin && newMax + 1 >= oMin
            }
            val adjacent = existing.filter { touches(it) }
            if (adjacent.isNotEmpty()) {
                val union = HashSet<Int>(idx)
                for (old in adjacent) union.addAll(wordsIn(old))
                val first = adjacent.minByOrNull { it.createdAt }!!
                val merged = TextMark(
                    id = UUID.randomUUID().toString(),
                    page = page,
                    rects = TextLayout.rectsFor(words, union),
                    color = first.color,
                    text = TextLayout.buildText(words, union).trim(),
                    createdAt = first.createdAt,
                    // ما كان مهمًا قبل الدمج يبقى مهمًا كجزء بارز داخل التمييز الجديد
                    emphasis = adjacent.flatMap {
                        if (it.level >= 2) listOf(TextTidy.clean(it.text.replace("# ", "").replace(" | ", " ")))
                        else it.emphasis
                    }.distinct(),
                    strongRects = adjacent.flatMap { if (it.level >= 2) it.rects else it.strongRects }.distinct()
                )
                val ids = adjacent.map { it.id }.toSet()
                next = existing.filter { it.id !in ids } + merged
            }
        }
        val list = next ?: (existing + TextMark(
            id = UUID.randomUUID().toString(),
            page = page,
            rects = rects,
            color = color,
            text = text,
            createdAt = System.currentTimeMillis()
        ))
        // قد يكتمل هذا بعد قراءة الصفحة في الخلفية أثناء حركة أخرى للمستخدم؛ لا نكسر لقطتها
        val nested = snapshot != null
        if (!nested) beginEdit()
        marks = marks + (page to list)
        if (!nested) endEdit()
        message?.let { AppController.messages.tryEmit(it) }
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
    fun allMarks(): List<TextMark> =
        marks.values.flatten().sortedWith(
            compareBy<TextMark>({ it.page }, { m -> m.rects.minOfOrNull { it.t } ?: 0f }, { m -> m.rects.minOfOrNull { it.l } ?: 0f })
        )

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

    private fun persist() {
        val data = DocData(annotations, images, marks, texts)
        AppController.scope.launch { AppController.repo.saveDocData(id, data) }
    }

    fun dispose() {
        disposed = true
        ocrJob?.cancel()
        pdf?.close()
        pdf = null
    }
}
