package com.mynotes.app

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * حالة التطبيق كاملة. كائن وحيد يعيش مع العملية، لذلك تبقى التبويبات وسجل التراجع
 * عند تدوير الشاشة. وتُحفظ قائمة التبويبات في SharedPreferences لاستعادتها بعد إغلاق التطبيق.
 */
object AppController {
    lateinit var repo: LibraryRepository
        private set
    private lateinit var prefs: SharedPreferences
    private var initialized = false

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** آخر لحظة كتب فيها المستخدم بالقلم؛ تتوقف القراءة الضوئية مؤقتًا بعدها كي لا تؤخّر الكتابة. */
    @Volatile
    var lastInkAt: Long = 0L

    private var ocrNoticeShown = false

    val library = mutableStateListOf<DocEntry>()
    val tabs = mutableStateListOf<DocSession>()

    /** null يعني أن المكتبة هي الشاشة الظاهرة. */
    var activeId by mutableStateOf<String?>(null)
        private set
    var libraryLoaded by mutableStateOf(false)
        private set
    var importing by mutableStateOf(false)
        private set

    var libraryFilter by mutableStateOf(LibraryFilter.RECENT)
    var librarySort by mutableStateOf(SortMode.LAST_OPENED)
    var libraryQuery by mutableStateOf("")

    // ---------- إعدادات الأدوات ----------

    var tool by mutableStateOf(Tool.FOUNTAIN)
        private set
    var lastPen by mutableStateOf(Tool.FOUNTAIN)
        private set

    private val colorOf = mutableStateMapOf<Tool, Color>()
    private val sizeOf = mutableStateMapOf<Tool, Int>()

    val penPalette = listOf(
        Color(0xFF000000),
        Color(0xFF455A64),
        Color(0xFF1565C0),
        Color(0xFFC62828),
        Color(0xFF2E7D32),
        Color(0xFFEF6C00),
        Color(0xFF6A1B9A),
        Color(0xFF795548)
    )
    val highlightPalette = listOf(
        Color(0xFFFFEB3B),
        Color(0xFF69F0AE),
        Color(0xFFFF80AB),
        Color(0xFF40C4FF),
        Color(0xFFFFAB40)
    )
    val customColors = mutableStateListOf<Color>()

    fun paletteFor(t: Tool): List<Color> =
        if (t == Tool.HIGHLIGHTER) highlightPalette else penPalette

    fun colorFor(t: Tool): Color = colorOf[t] ?: when (t) {
        Tool.HIGHLIGHTER -> Color(0xFFFFEB3B)
        Tool.LASER -> Color(0xFFFF1744)
        else -> Color(0xFF000000)
    }

    fun sizeFor(t: Tool): Int = sizeOf[t] ?: when (t) {
        Tool.HIGHLIGHTER -> 4
        Tool.ERASER -> 3
        Tool.LASER -> 4
        else -> 2
    }

    fun selectTool(t: Tool) {
        tool = t
        if (t.isPen()) lastPen = t
    }

    fun setColor(c: Color) {
        if (tool != Tool.ERASER) colorOf[tool] = c
    }

    fun setSize(i: Int) {
        sizeOf[tool] = i.coerceIn(0, Sizes.COUNT - 1)
    }

    fun addCustomColor(c: Color) {
        customColors.remove(c)
        customColors.add(0, c)
        while (customColors.size > 10) customColors.removeAt(customColors.lastIndex)
        prefs.edit()
            .putString("custom_colors", customColors.joinToString(",") { it.toArgb().toString() })
            .apply()
    }

    // ---------- التهيئة ----------

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        val app = context.applicationContext
        repo = LibraryRepository(app)
        prefs = app.getSharedPreferences("mynotes_session", Context.MODE_PRIVATE)

        (prefs.getString("custom_colors", "") ?: "")
            .split(",")
            .mapNotNull { it.toIntOrNull() }
            .forEach { customColors.add(Color(it)) }

        scope.launch {
            val list = withContext(Dispatchers.IO) { repo.loadIndex() }
            library.clear()
            library.addAll(list)
            val ids = (prefs.getString("open", "") ?: "").split(",").filter { it.isNotBlank() }
            val active = prefs.getString("active", null)
            ids.forEach { id ->
                library.firstOrNull { it.id == id }?.let { openSession(it, activate = false) }
            }
            activeId = active?.takeIf { a -> tabs.any { it.id == a } }
            libraryLoaded = true
        }
    }

    // ---------- التبويبات ----------

    fun openDocument(id: String) {
        if (tabs.any { it.id == id }) {
            activate(id)
            return
        }
        val entry = library.firstOrNull { it.id == id } ?: return
        openSession(entry, activate = true)
        updateEntry(id) { it.copy(lastOpened = System.currentTimeMillis()) }
        saveLibrary()
    }

    private fun openSession(entry: DocEntry, activate: Boolean) {
        val s = DocSession(entry)
        tabs.add(s)
        if (activate) activeId = entry.id
        scope.launch {
            s.load(repo.file(entry), repo)
            if (s.status is SessionStatus.Ready) {
                // قراءة النص تبدأ تلقائيًا دون انتظار المستخدم
                s.startOcr()
                noteOcrLimits(s)
            }
        }
        saveSession()
    }

    private fun noteOcrLimits(s: DocSession) {
        val total = s.pdf?.count ?: 0
        if (!ocrNoticeShown && s.ocr.size < total) {
            ocrNoticeShown = true
            messages.tryEmit("قراءة النص التلقائية تدعم الإنجليزية فقط، ولا تدعم العربية")
        }
    }

    fun activate(id: String?) {
        if (id != null && tabs.none { it.id == id }) return
        recordPage(activeId)
        activeId = id
        saveLibrary()
        saveSession()
    }

    fun closeTab(id: String) {
        val idx = tabs.indexOfFirst { it.id == id }
        if (idx < 0) return
        val s = tabs[idx]
        recordPage(id)
        tabs.removeAt(idx)
        s.dispose()
        if (activeId == id) {
            activeId = tabs.getOrNull(minOf(idx, tabs.lastIndex))?.id
        }
        saveLibrary()
        saveSession()
    }

    private fun recordPage(id: String?) {
        if (id == null) return
        val s = tabs.firstOrNull { it.id == id } ?: return
        if (s.status !is SessionStatus.Ready) return
        val page = s.listState.firstVisibleItemIndex
        updateEntry(id) { it.copy(lastPage = page) }
    }

    fun flush() {
        tabs.forEach { recordPage(it.id) }
        saveLibrary()
        saveSession()
    }

    // ---------- قراءة النص في الخلفية للملفات المستوردة ----------

    private val ocrQueue = ArrayDeque<DocEntry>()
    private var ocrWorker: Job? = null

    /** يضع مستندًا مستوردًا في طابور القراءة الضوئية؛ يُقرأ تباعًا في الخلفية دون فتحه. */
    fun queueOcr(entry: DocEntry) {
        if (entry.isNote) return
        ocrQueue.addLast(entry)
        if (ocrWorker?.isActive == true) return
        ocrWorker = scope.launch {
            while (ocrQueue.isNotEmpty()) {
                val e = ocrQueue.removeFirst()
                if (library.none { it.id == e.id }) continue
                if (tabs.any { it.id == e.id }) continue
                val s = DocSession(e)
                try {
                    s.load(repo.file(e), repo)
                    if (s.status is SessionStatus.Ready) s.startOcr()?.join()
                } finally {
                    s.dispose()
                }
            }
        }
    }

    // ---------- الصور ----------

    fun insertImage(s: DocSession, uri: Uri, page: Int) {
        scope.launch {
            try {
                val res = withContext(Dispatchers.IO) { repo.importImage(s.id, uri) }
                s.addImage(
                    ImageItem(
                        id = UUID.randomUUID().toString(),
                        file = res.first,
                        page = page,
                        x = 0.275f,
                        y = 0.15f,
                        w = 0.45f,
                        aspect = res.second
                    )
                )
                messages.tryEmit("أُضيفت الصورة. اسحبها لتحريكها واسحب النقطة في زاويتها لتغيير حجمها")
            } catch (t: Throwable) {
                messages.tryEmit("تعذر إضافة الصورة")
            }
        }
    }

    // ---------- الصفحات والملاحظات ----------

    fun createNote(name: String, template: String, color: Int) {
        scope.launch {
            try {
                val e = withContext(Dispatchers.IO) { repo.createNote(name, template, color) }
                library.add(0, e)
                saveLibrary()
                openDocument(e.id)
            } catch (t: Throwable) {
                messages.tryEmit("تعذر إنشاء الملاحظة")
            }
        }
    }

    fun onPagesChanged(id: String, count: Int) {
        updateEntry(id) { it.copy(pageCount = count) }
        saveLibrary()
    }

    /** يضيف صفحة فارغة كاملة بعد الصفحة رقم after (من 0). */
    fun addBlankPage(s: DocSession, after: Int, template: String, color: Int) {
        val doc = s.pdf ?: return
        val base = after.coerceIn(0, maxOf(0, doc.count - 1))
        val ratio = doc.ratio(base)
        s.insertPages(after, listOf(PageRef(Templates.src(template, color), 0, ratio)))
        scope.launch { s.listState.scrollToItem((after + 1).coerceAtLeast(0)) }
        messages.tryEmit("أُضيفت صفحة جديدة (${doc.count} صفحة الآن)")
    }

    /** يضيف كل صفحات ملف PDF آخر إلى هذا المستند بعد الصفحة رقم after. */
    fun addPdfPages(s: DocSession, uri: Uri, after: Int) {
        val doc = s.pdf ?: return
        scope.launch {
            try {
                val refs = withContext(Dispatchers.IO) {
                    val part = repo.importPart(s.id, uri)
                    doc.addSource("part:" + part.first, File(repo.partsDir(s.id), part.first))
                }
                s.insertPages(after, refs)
                s.listState.scrollToItem((after + 1).coerceAtLeast(0))
                messages.tryEmit("أُضيفت ${refs.size} صفحة من الملف (${doc.count} صفحة الآن)")
            } catch (t: Throwable) {
                messages.tryEmit("تعذر إضافة الملف. تأكد أنه PDF سليم.")
            }
        }
    }

    // ---------- المكتبة ----------

    private fun updateEntry(id: String, transform: (DocEntry) -> DocEntry) {
        val i = library.indexOfFirst { it.id == id }
        if (i < 0) return
        library[i] = transform(library[i])
    }

    private fun saveLibrary() {
        if (!libraryLoaded) return
        val snap = library.toList()
        scope.launch { repo.saveIndex(snap) }
    }

    private fun saveSession() {
        if (!libraryLoaded && tabs.isEmpty()) return
        prefs.edit()
            .putString("open", tabs.joinToString(",") { it.id })
            .putString("active", activeId)
            .apply()
    }

    fun importDocuments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            importing = true
            var ok = 0
            var failed = 0
            val added = ArrayList<DocEntry>()
            for (u in uris) {
                try {
                    val e = withContext(Dispatchers.IO) { repo.importFile(u) }
                    library.add(0, e)
                    added.add(e)
                    ok++
                } catch (t: Throwable) {
                    failed++
                }
            }
            saveLibrary()
            importing = false
            val msg = when {
                failed == 0 && ok == 1 -> "تم استيراد المستند، وتبدأ قراءة نصه تلقائيًا"
                failed == 0 -> "تم استيراد $ok مستندات، وتبدأ قراءة نصوصها تلقائيًا"
                ok == 0 -> "تعذر الاستيراد. تأكد أن الملف PDF سليم."
                else -> "تم استيراد $ok وتعذر استيراد $failed"
            }
            messages.tryEmit(msg)
            added.forEach { queueOcr(it) }
        }
    }

    fun toggleFavorite(id: String) {
        updateEntry(id) { it.copy(favorite = !it.favorite) }
        saveLibrary()
    }

    fun rename(id: String, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        updateEntry(id) { it.copy(name = clean) }
        tabs.firstOrNull { it.id == id }?.title = clean
        saveLibrary()
    }

    fun duplicate(entry: DocEntry) {
        scope.launch {
            try {
                val copy = withContext(Dispatchers.IO) { repo.duplicate(entry, " (نسخة)") }
                library.add(0, copy)
                saveLibrary()
                messages.tryEmit("تم إنشاء نسخة من المستند")
            } catch (t: Throwable) {
                messages.tryEmit("تعذر نسخ المستند")
            }
        }
    }

    fun delete(entry: DocEntry) {
        closeTab(entry.id)
        library.removeAll { it.id == entry.id }
        saveLibrary()
        scope.launch { withContext(Dispatchers.IO) { repo.delete(entry) } }
        messages.tryEmit("تم حذف المستند")
    }
}
