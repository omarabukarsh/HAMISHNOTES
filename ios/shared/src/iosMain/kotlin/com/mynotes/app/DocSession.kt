package com.mynotes.app

import kotlinx.coroutines.IO

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

/** يمسح كل خط تلمسه الممحاة كاملًا. يعيد القائمة نفسها إن لم يتغير شيء. */
fun eraseStrokes(list: List<Ink>, at: Offset, radius: Float, canErase: (Ink) -> Boolean = { true }): List<Ink> {
    val next = list.filterNot { s -> canErase(s) && s.pts.any { hypot(it.x - at.x, it.y - at.y) < radius } }
    return if (next.size == list.size) list else next
}

/**
 * يمسح الجزء الذي تلمسه الممحاة فقط: يُقسم الخط عند دائرة الممحاة إلى قطعتين ويبقى الباقي.
 * تُضاف نقاط وسيطة أولًا حتى لا تعبر الممحاة بين نقطتين متباعدتين دون أن تقطع الخط.
 */
fun eraseStrokesArea(list: List<Ink>, at: Offset, radius: Float, canErase: (Ink) -> Boolean = { true }): List<Ink> {
    fun inside(p: Pt) = hypot(p.x - at.x, p.y - at.y) < radius
    var changed = false
    val out = ArrayList<Ink>(list.size + 2)
    for (s in list) {
        if (!canErase(s)) {
            out.add(s); continue
        }
        // رفض سريع بالمستطيل المحيط
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (p in s.pts) {
            if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y
        }
        if (at.x < minX - radius || at.x > maxX + radius || at.y < minY - radius || at.y > maxY + radius) {
            out.add(s); continue
        }
        val step = (radius * 0.4f).coerceAtLeast(1e-4f)
        val dense = ArrayList<Pt>(s.pts.size * 2)
        for (i in s.pts.indices) {
            val b = s.pts[i]
            if (i > 0) {
                val a = s.pts[i - 1]
                val d = hypot(b.x - a.x, b.y - a.y)
                val n = (d / step).toInt().coerceAtMost(200)
                for (k in 1 until n) {
                    val f = k.toFloat() / n
                    dense.add(Pt(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f, a.p + (b.p - a.p) * f))
                }
            }
            dense.add(b)
        }
        if (dense.none { inside(it) }) {
            out.add(s); continue
        }
        changed = true
        var cur = ArrayList<Pt>()
        for (p in dense) {
            if (inside(p)) {
                if (cur.size >= 2) out.add(s.copy(pts = cur))
                cur = ArrayList()
            } else {
                cur.add(p)
            }
        }
        if (cur.size >= 2) out.add(s.copy(pts = cur))
    }
    return if (changed) out else list
}

/** طلب فتح نافذة مربع النص: boxId = null لإنشاء مربع جديد في الصفحة page. */
data class TextDialogReq(val boxId: String?, val page: Int, val sticky: Boolean = false)

/** منطقة نافذة الكتابة المكبّرة على الصفحة (إحداثيات الصفحة، h يُحسب من نسبة النافذة). */
data class ZoomBox(val page: Int, val x: Float, val y: Float, val w: Float, val h: Float = w * 0.3f)

/** خطوط محددة بأداة التحديد الحر في صفحة واحدة (المقارنة بالمرجع ===). */
class LassoSel(
    val page: Int,
    val strokes: List<Ink>,
    val imageIds: List<String> = emptyList(),
    val textIds: List<String> = emptyList()
) {
    fun isEmpty() = strokes.isEmpty() && imageIds.isEmpty() && textIds.isEmpty()
}

class EditSnapshot(
    val ink: Map<Int, List<Ink>>,
    val images: Map<Int, List<ImageItem>>,
    val marks: Map<Int, List<TextMark>>,
    val texts: Map<Int, List<TextBox>>,
    val edits: SummaryEdits,
    val notes: Map<Int, List<StickyNote>> = emptyMap()
)

/**
 * حالة مستند مفتوح في تبويب. تبقى حية عند تبديل التبويبات وعند تدوير الشاشة،
 * والكتابة والصور وبنية الصفحات تُحفظ على القرص بعد كل تعديل.
 */
/** حجم الملاحظة المطوية (نسبة لعرض الصفحة). */
const val NOTE_COLLAPSED = 0.045f

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

    /** الملاحظات اللاصقة والمحددة منها. */
    var notes by mutableStateOf<Map<Int, List<StickyNote>>>(emptyMap())
    var selectedNoteId by mutableStateOf<String?>(null)
    /** ملاحظة يُكتب نصها الآن بلوحة المفاتيح. */
    var noteTextEditId by mutableStateOf<String?>(null)

    var textDialog by mutableStateOf<TextDialogReq?>(null)

    // ---- درج الملخصات ----
    var summaryOpen by mutableStateOf(false)

    /** درج خيارات الصفحة على اليمين. */
    var pageMenuOpen by mutableStateOf(false)

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
    /** صفحة يُنتقل إليها بعد اكتمال فتح المستند (من رابط)، -1 = لا شيء. */
    var pendingJump = -1

    fun applyPending() {
        val j = pendingJump
        if (j >= 0) {
            pendingJump = -1
            AppController.scope.launch { listState.scrollToItem(j.coerceIn(0, maxOf(0, (pdf?.count ?: 1) - 1))) }
        }
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
        AppController.messages.tryEmit(L("أُضيفت البطاقة", "Card added"))
    }

    fun updateCustomCard(cardId: String, front: String, back: String) {
        val f = front.trim()
        val b = back.trim()
        if (f.isEmpty() || b.isEmpty()) return
        customCards = customCards.map { if (it.id == cardId) it.copy(front = f, back = b) else it }
        persist()
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
        AppController.messages.tryEmit(if (page in bookmarks) L("أُضيفت علامة على الصفحة ${page + 1}", "Bookmarked page ${page + 1}") else L("أُزيلت العلامة", "Bookmark removed"))
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
    /** آخر لحظة تمرير (تُحدَّث من شاشة القراءة). */
    @Volatile var lastScrollAt = 0L
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

    /** فهرس عناوين الملف الأصلي (فارغ إن لم يكن فيه فهرس). */
    var outline by mutableStateOf<List<PdfTextLayer.OutlineItem>>(emptyList())

    // ---- الفهرس القابل للتعديل ----
    private val outlinePrefs by lazy {
        AppController.appContext.getSharedPreferences("mynotes_outline", android.content.Context.MODE_PRIVATE)
    }

    /** فهرس عدّله المستخدم؛ null = فهرس الملف الأصلي كما هو. */
    var outlineCustom by mutableStateOf<List<OutlineEntry>?>(null)
        private set
    private var outlineLoaded = false

    /** العناوين المعروضة في درج الصفحات. */
    fun outlineEntries(): List<OutlineEntry> {
        if (!outlineLoaded) {
            outlineLoaded = true
            outlineCustom = try {
                val raw = outlinePrefs.getString(id, null)
                if (raw == null) null else {
                    val a = org.json.JSONArray(raw)
                    (0 until a.length()).map { i ->
                        val o = a.getJSONObject(i)
                        OutlineEntry(o.getString("t"), o.optInt("l", 0), o.optInt("p", 0))
                    }
                }
            } catch (t: Throwable) {
                null
            }
        }
        return outlineCustom ?: outline.map { OutlineEntry(it.title, it.level, pageOfMain(it.pageIndex)) }
    }

    fun saveOutline(list: List<OutlineEntry>) {
        outlineCustom = list
        val a = org.json.JSONArray()
        list.forEach { a.put(org.json.JSONObject().put("t", it.title).put("l", it.level).put("p", it.page)) }
        outlinePrefs.edit().putString(id, a.toString()).apply()
    }

    /** يضيف عنوانًا للصفحة في موضعه حسب ترتيب الصفحات. */
    fun addOutlineEntry(page: Int, title: String) {
        val list = outlineEntries().toMutableList()
        val at = list.indexOfFirst { it.page > page }.let { if (it < 0) list.size else it }
        list.add(at, OutlineEntry(title, 0, page))
        saveOutline(list)
    }

    fun resetOutline() {
        outlineCustom = null
        outlinePrefs.edit().remove(id).apply()
    }

    /** رقم الصفحة الحالي لصفحة من الملف الأصلي (بعد أي إضافة أو ترتيب)، أو -1. */
    fun pageOfMain(mainIndex: Int): Int =
        pdf?.pages?.indexOfFirst { it.src == "main" && it.index == mainIndex } ?: -1
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
            // فهرس الملف (إن وُجد) يُقرأ في الخلفية
            val layer = textLayer
            AppController.scope.launch {
                val items = withContext(Dispatchers.IO) { layer?.outline("main") ?: emptyList() }
                if (!disposed) outline = items
            }
            annotations = loaded.second.ink
            notes = loaded.second.notes
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
            status = SessionStatus.Failed(L("الملف غير موجود، ربما حُذف أو نُقل.", "File not found; it may have been deleted or moved."))
        } catch (e: SecurityException) {
            status = SessionStatus.Failed(L("الملف محمي بكلمة مرور ولا يمكن فتحه.", "The file is password-protected and cannot be opened."))
        } catch (t: Throwable) {
            status = SessionStatus.Failed(L("تعذر فتح الملف، قد يكون تالفًا.", "Could not open the file; it may be damaged."))
        }
    }

    // ---------- التحرير والتراجع ----------

    private fun snap() = EditSnapshot(annotations, images, marks, texts, summaryEdits, notes)

    private fun restore(s: EditSnapshot) {
        annotations = s.ink
        images = s.images
        marks = s.marks
        texts = s.texts
        summaryEdits = s.edits
        notes = s.notes
        selectedNoteId = null
        selectedImageId = null
        selectedTextId = null
        lassoSel = null
    }

    fun beginEdit() {
        snapshot = snap()
    }

    // ---------- التحديد الحر (لاسو) ----------

    var lassoSel by mutableStateOf<LassoSel?>(null)

    /** نافذة الكتابة المكبّرة (null = مغلقة). */
    var zoomBox by mutableStateOf<ZoomBox?>(null)

    /** يحدد الخطوط التي يقع معظمها داخل المضلع (إحداثيات الصفحة). */
    fun lassoSelect(page: Int, poly: List<Pt>) {
        val list = annotations[page] ?: emptyList()
        if (poly.size < 3) {
            lassoSel = null
            return
        }
        fun inside(x: Float, y: Float): Boolean {
            var c = false
            var j = poly.size - 1
            for (i in poly.indices) {
                val a = poly[i]
                val b = poly[j]
                if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) c = !c
                j = i
            }
            return c
        }
        val picked = list.filter { s ->
            PenPrefs.lassoable(s) && s.pts.isNotEmpty() && s.pts.count { inside(it.x, it.y) } >= s.pts.size * 0.6f
        }
        // الصور ومربعات النص والملاحظات اللاصقة: يكفي أن يقع مركزها داخل التحديد
        val imgs = if (!PenPrefs.lassoImages) emptyList() else (images[page] ?: emptyList()).filter { im ->
            inside(im.x + im.w / 2f, im.y + im.w / im.aspect / 2f)
        }.map { it.id }
        val txts = (texts[page] ?: emptyList()).filter { tb ->
            (if (tb.sticky) PenPrefs.lassoStickies else PenPrefs.lassoTexts) && inside(tb.x + tb.w / 2f, tb.y + textHeightOf(tb) / 2f)
        }.map { it.id }
        val sel = LassoSel(page, picked, imgs, txts)
        lassoSel = if (sel.isEmpty()) null else sel
        if (sel.isEmpty()) AppController.messages.tryEmit(L("لم تحدد أي شيء", "Nothing selected"))
    }

    /** المستطيل المحيط بالتحديد (l, t, r, b) بإحداثيات الصفحة. */
    fun lassoBounds(): FloatArray? {
        val sel = lassoSel ?: return null
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (s in sel.strokes) for (p in s.pts) {
            if (p.x < l) l = p.x; if (p.x > r) r = p.x
            if (p.y < t) t = p.y; if (p.y > b) b = p.y
        }
        fun box(x0: Float, y0: Float, x1: Float, y1: Float) {
            if (x0 < l) l = x0; if (x1 > r) r = x1
            if (y0 < t) t = y0; if (y1 > b) b = y1
        }
        (images[sel.page] ?: emptyList()).filter { it.id in sel.imageIds }.forEach { box(it.x, it.y, it.x + it.w, it.y + it.w / it.aspect) }
        (texts[sel.page] ?: emptyList()).filter { it.id in sel.textIds }.forEach { box(it.x, it.y, it.x + it.w, it.y + textHeightOf(it)) }
        if (l > r) return null
        val pad = 0.008f
        return floatArrayOf(l - pad, t - pad, r + pad, b + pad)
    }

    /** يستبدل الخطوط المحددة بنسخ معدّلة (دون تسجيل في التراجع؛ يُلف بـ beginEdit/endEdit). */
    private fun lassoReplace(transform: (Ink) -> Ink?) {
        val sel = lassoSel ?: return
        val list = annotations[sel.page] ?: return
        val picked = sel.strokes
        val next = ArrayList<Ink>(list.size)
        val newSel = ArrayList<Ink>()
        for (s in list) {
            if (picked.any { it === s }) {
                val t = transform(s)
                if (t != null) {
                    next.add(t)
                    newSel.add(t)
                }
            } else next.add(s)
        }
        annotations = annotations + (sel.page to next)
        val ns = LassoSel(sel.page, newSel, sel.imageIds, sel.textIds)
        lassoSel = if (ns.isEmpty()) null else ns
    }

    /** ارتفاع تقريبي لمربع نص (نسبة لعرض الصفحة). */
    fun textHeightOf(tb: TextBox): Float =
        if (tb.sticky) 0.045f else (tb.size * 1.45f * tb.text.lines().size.coerceAtLeast(1)).coerceAtLeast(0.02f)

    /** تحريك بمقدار (dx, dy) وتكبير بنسبة k حول النقطة (px, py). */
    fun lassoTransform(dx: Float, dy: Float, k: Float = 1f, px: Float = 0f, py: Float = 0f) {
        val sel = lassoSel ?: return
        if (sel.imageIds.isNotEmpty()) {
            val list = images[sel.page] ?: emptyList()
            images = images + (sel.page to list.map { im ->
                if (im.id in sel.imageIds) im.copy(x = px + (im.x - px) * k + dx, y = py + (im.y - py) * k + dy, w = im.w * k) else im
            })
        }
        if (sel.textIds.isNotEmpty()) {
            val list = texts[sel.page] ?: emptyList()
            texts = texts + (sel.page to list.map { tb ->
                if (tb.id in sel.textIds) tb.copy(
                    x = px + (tb.x - px) * k + dx, y = py + (tb.y - py) * k + dy,
                    w = tb.w * k, size = if (tb.sticky) tb.size else tb.size * k
                ) else tb
            })
        }
        lassoReplace { s ->
            s.copy(
                pts = s.pts.map { p -> Pt(px + (p.x - px) * k + dx, py + (p.y - py) * k + dy, p.p) },
                width = s.width * k
            )
        }
    }

    fun lassoRecolor(color: Color) {
        val sel = lassoSel ?: return
        beginEdit()
        if (sel.textIds.isNotEmpty()) {
            texts = texts + (sel.page to (texts[sel.page] ?: emptyList()).map { if (it.id in sel.textIds && !it.sticky) it.copy(color = color) else it })
        }
        lassoReplace { it.copy(color = color) }
        endEdit()
    }

    fun lassoDelete() {
        val sel = lassoSel ?: return
        beginEdit()
        if (sel.imageIds.isNotEmpty()) images = images + (sel.page to (images[sel.page] ?: emptyList()).filter { it.id !in sel.imageIds })
        if (sel.textIds.isNotEmpty()) texts = texts + (sel.page to (texts[sel.page] ?: emptyList()).filter { it.id !in sel.textIds })
        lassoSel = LassoSel(sel.page, sel.strokes)
        lassoReplace { null }
        lassoSel = null
        endEdit()
    }

    /** نسخة مزاحة قليلًا من التحديد، وتصبح هي المحددة. */
    fun lassoDuplicate() {
        val sel = lassoSel ?: return
        beginEdit()
        val copies = sel.strokes.map { s -> s.copy(pts = s.pts.map { Pt(it.x + 0.03f, it.y + 0.03f, it.p) }) }
        annotations = annotations + (sel.page to ((annotations[sel.page] ?: emptyList()) + copies))
        val imgCopies = (images[sel.page] ?: emptyList()).filter { it.id in sel.imageIds }
            .map { it.copy(id = java.util.UUID.randomUUID().toString(), x = it.x + 0.03f, y = it.y + 0.03f) }
        if (imgCopies.isNotEmpty()) images = images + (sel.page to ((images[sel.page] ?: emptyList()) + imgCopies))
        val txtCopies = (texts[sel.page] ?: emptyList()).filter { it.id in sel.textIds }
            .map { it.copy(id = java.util.UUID.randomUUID().toString(), x = it.x + 0.03f, y = it.y + 0.03f) }
        if (txtCopies.isNotEmpty()) texts = texts + (sel.page to ((texts[sel.page] ?: emptyList()) + txtCopies))
        lassoSel = LassoSel(sel.page, copies, imgCopies.map { it.id }, txtCopies.map { it.id })
        endEdit()
    }

    fun addInk(page: Int, ink: Ink) {
        annotations = annotations + (page to ((annotations[page] ?: emptyList()) + ink))
    }

    fun eraseAt(page: Int, at: Offset, radius: Float) {
        val cur = annotations[page]
        if (cur != null) {
            val next = if (AppController.eraserMode == EraserMode.AREA) eraseStrokesArea(cur, at, radius, PenPrefs::erasable)
            else eraseStrokes(cur, at, radius, PenPrefs::erasable)
            if (next !== cur) annotations = annotations + (page to next)
        }
        eraseNotesAt(page, at, radius)
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
            s.edits === summaryEdits && s.notes === notes
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

    // ---------- الملاحظات اللاصقة ----------

    fun note(id: String?): StickyNote? =
        if (id == null) null else notes.values.firstNotNullOfOrNull { l -> l.firstOrNull { it.id == id } }

    fun addNote(n: StickyNote) {
        beginEdit()
        notes = notes + (n.page to ((notes[n.page] ?: emptyList()) + n))
        selectedNoteId = n.id
        endEdit()
    }

    /** يعدّل ملاحظة دون تسجيله في التراجع؛ يُلف بـ beginEdit/endEdit. */
    fun updateNote(id: String, transform: (StickyNote) -> StickyNote) {
        val page = notes.entries.firstOrNull { e -> e.value.any { it.id == id } }?.key ?: return
        notes = notes + (page to (notes[page] ?: emptyList()).map { if (it.id == id) transform(it) else it })
    }

    fun editNote(id: String, transform: (StickyNote) -> StickyNote) {
        beginEdit()
        updateNote(id, transform)
        endEdit()
    }

    fun deleteNote(id: String) {
        val page = notes.entries.firstOrNull { e -> e.value.any { it.id == id } }?.key ?: return
        beginEdit()
        notes = notes + (page to (notes[page] ?: emptyList()).filter { it.id != id })
        if (selectedNoteId == id) selectedNoteId = null
        endEdit()
    }

    fun duplicateNote(id: String) {
        val n = note(id) ?: return
        addNote(n.copy(id = UUID.randomUUID().toString(), x = n.x + 0.03f, y = n.y + 0.03f))
    }

    /** أعلى ملاحظة (غير مطوية) تحت النقطة على الصفحة. */
    fun noteAt(page: Int, x: Float, y: Float, includeCollapsed: Boolean = false): StickyNote? =
        (notes[page] ?: emptyList()).lastOrNull { n ->
            val w = if (n.collapsed) NOTE_COLLAPSED else n.w
            val h = if (n.collapsed) NOTE_COLLAPSED else n.h
            (includeCollapsed || !n.collapsed) && x >= n.x && x <= n.x + w && y >= n.y && y <= n.y + h
        }

    /** يضيف خطًا إلى ملاحظة (نقاط الصفحة تُحوَّل إلى إحداثيات الملاحظة). */
    fun addNoteInk(id: String, ink: Ink) {
        updateNote(id) { n ->
            n.copy(ink = n.ink + ink.copy(pts = ink.pts.map { Pt((it.x - n.x) / n.w, (it.y - n.y) / n.w, it.p) }, width = ink.width / n.w))
        }
    }

    /** الممحاة على الملاحظات: تمسح من خطوطها حسب نمط الممحاة. */
    fun eraseNotesAt(page: Int, at: Offset, radius: Float) {
        val list = notes[page] ?: return
        var changed = false
        val next = list.map { n ->
            if (n.collapsed || n.ink.isEmpty() || at.x < n.x - radius || at.x > n.x + n.w + radius || at.y < n.y - radius || at.y > n.y + n.h + radius) n
            else {
                val local = Offset((at.x - n.x) / n.w, (at.y - n.y) / n.w)
                val r = radius / n.w
                val ink = if (AppController.eraserMode == EraserMode.AREA) eraseStrokesArea(n.ink, local, r, PenPrefs::erasable)
                else eraseStrokes(n.ink, local, r, PenPrefs::erasable)
                if (ink !== n.ink) { changed = true; n.copy(ink = ink) } else n
            }
        }
        if (changed) notes = notes + (page to next)
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

    // ---------- ترتيب الصفحات: حذف، نسخ، تحريك ----------

    /**
     * يعيد بناء المستند بترتيب جديد: order[i] = رقم الصفحة القديمة التي تصبح في الموضع i
     * (صفحة محذوفة لا تظهر، وصفحة منسوخة تظهر مرتين). الكتابة والصور والتمييز تنتقل مع صفحاتها.
     */
    fun rearrangePages(order: List<Int>) {
        val doc = pdf ?: return
        val old = doc.pages
        if (order.isEmpty() || order.any { it !in old.indices }) return
        stopOcr()
        doc.replacePages(order.map { old[it] })
        val seen = HashSet<Int>()
        val nAnn = HashMap<Int, List<Ink>>()
        val nImg = HashMap<Int, List<ImageItem>>()
        val nMarks = HashMap<Int, List<TextMark>>()
        val nTexts = HashMap<Int, List<TextBox>>()
        val nNotes = HashMap<Int, List<StickyNote>>()
        val nBm = HashSet<Int>()
        val nOcr = HashMap<Int, OcrPage>()
        order.forEachIndexed { ni, oi ->
            val first = seen.add(oi)
            fun newId(id: String) = if (first) id else UUID.randomUUID().toString()
            annotations[oi]?.let { nAnn[ni] = it }
            images[oi]?.let { l -> nImg[ni] = l.map { it.copy(page = ni, id = newId(it.id)) } }
            marks[oi]?.let { l -> nMarks[ni] = l.map { it.copy(page = ni, id = newId(it.id)) } }
            texts[oi]?.let { l -> nTexts[ni] = l.map { it.copy(page = ni, id = newId(it.id)) } }
            notes[oi]?.let { l -> nNotes[ni] = l.map { it.copy(page = ni, id = newId(it.id)) } }
            if (oi in bookmarks) nBm.add(ni)
            ocr[oi]?.let { nOcr[ni] = it }
        }
        annotations = nAnn
        images = nImg
        marks = nMarks
        texts = nTexts
        notes = nNotes
        bookmarks = nBm
        ocr.clear()
        ocr.putAll(nOcr)
        afterPagesChanged()
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
        notes = notes.entries.associate { (k, l) -> shift(k) to l.map { it.copy(page = shift(k)) } }
        bookmarks = bookmarks.map { shift(it) }.toSet()
        val shiftedOcr = HashMap<Int, OcrPage>()
        ocr.forEach { (k, v) -> shiftedOcr[shift(k)] = v }
        ocr.clear()
        ocr.putAll(shiftedOcr)
        afterPagesChanged()
    }

    /** يغيّر مصدر صفحة (مثل قالب صفحة فارغة)، وتبقى كتابتها كما هي. */
    fun setPageSrc(page: Int, src: String) {
        val doc = pdf ?: return
        val old = doc.pages
        if (page !in old.indices) return
        doc.replacePages(old.mapIndexed { i, r -> if (i == page) r.copy(src = src) else r })
        afterPagesChanged()
    }

    /** يمسح عناصر من صفحة: الكتابة اليدوية، التظليل، النصوص والملاحظات، الصور. يمكن التراجع عنه. */
    fun clearPageItems(page: Int, ink: Boolean, highlights: Boolean, texts: Boolean, pics: Boolean) {
        beginEdit()
        if (ink) annotations = annotations - page
        if (highlights) marks = marks - page
        if (texts) {
            this.texts = this.texts - page
            notes = notes - page
        }
        if (pics) images = images - page
        endEdit()
    }

    /** بعد تغيّر أرقام الصفحات: مسح التراجع والتحديد، والحفظ، وإعادة تشغيل القراءة. */
    private fun afterPagesChanged() {
        val doc = pdf ?: return
        lassoSel = null
        zoomBox = null
        undoStack.clear()
        redoStack.clear()
        snapshot = null
        selection = null
        selectedImageId = null
        selectedTextId = null
        selectedNoteId = null
        if (searchOpen && searchQuery.isNotBlank()) runSearch(searchQuery, navigate = false) else hits = emptyList()

        val pagesSnap = doc.pages
        val data = DocData(annotations, images, marks, texts, summaryEdits, customCards, starred, bookmarks, notes)
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
                // القراءة في الخلفية تتنحى أثناء الكتابة والتمرير حتى لا تبطئ رسم الصفحات
                while (isActive && (
                        System.currentTimeMillis() - AppController.lastInkAt < 1500L ||
                            listState.isScrollInProgress ||
                            System.currentTimeMillis() - lastScrollAt < 800L
                        )
                ) {
                    delay(250)
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
            marks[page] ?: emptyList(), page, words, idx, color, System.currentTimeMillis(),
            inSummary = AppController.highlightToSummary && UiPrefs.summariesOn
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
                AppController.messages.tryEmit(L("لا يوجد نص مقروء تحت التمييز، فحُفظ كتمييز حر", "No readable text under the highlight, so it was kept as a free highlight"))
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
        AppController.messages.tryEmit(
            if (AppController.highlightToSummary && UiPrefs.summariesOn) L("أُضيف النص إلى الملخصات", "Text added to summaries") else L("مُيّز النص دون إضافته إلى الملخصات", "Text highlighted without adding it to summaries")
        )
    }

    /** كل التمييزات مرتبة حسب الصفحة ثم موضعها في الصفحة. */
    /** تمييزات الملخص فقط (بدون التمييز المخصص للصفحة وحدها). */
    fun allMarks(): List<TextMark> = MarkMerge.readingOrder(marks.values).filter { it.inSummary }

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

    private var saveJob: Job? = null

    /**
     * يحفظ المستند بعد 1.5 ثانية من آخر تغيير (لا بعد كل خط)، فلا يُعاد تحويل المستند كله
     * إلى JSON أثناء الكتابة. الحفظ فوري عند إغلاق المستند وعند خروج التطبيق للخلفية (flushPersist).
     */
    fun persist() {
        saveJob?.cancel()
        saveJob = AppController.scope.launch {
            delay(1500)
            saveJob = null
            saveNow()
        }
    }

    /** يحفظ الآن إن كان هناك حفظ منتظر. */
    fun flushPersist() {
        val j = saveJob ?: return
        saveJob = null
        j.cancel()
        saveNow()
    }

    /** يلغي الحفظ المنتظر (عند حذف المستند). */
    fun cancelPendingPersist() {
        saveJob?.cancel()
        saveJob = null
    }

    private fun saveNow() {
        val data = DocData(annotations, images, marks, texts, summaryEdits, customCards, starred, bookmarks, notes)
        AppController.scope.launch { AppController.repo.saveDocData(id, data) }
    }

    fun dispose() {
        flushPersist()
        disposed = true
        ocrJob?.cancel()
        textLayer?.close()
        textLayer = null
        pdf?.close()
        pdf = null
    }
}
