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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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

    // ---------- المواد (مجلدات المكتبة) ----------

    /** أسماء المواد بالترتيب. null في libraryFolder = كل المواد. */
    val folders = mutableStateListOf<String>()
    var libraryFolder by mutableStateOf<String?>(null)

    private fun saveFolders() {
        prefs.edit().putString("folders", org.json.JSONArray(folders.toList()).toString()).apply()
    }

    fun addFolder(name: String): String? {
        val n = name.trim()
        if (n.isEmpty()) return null
        if (n !in folders) {
            folders.add(n)
            saveFolders()
        }
        return n
    }

    fun renameFolder(old: String, new: String) {
        val n = new.trim()
        if (n.isEmpty() || n == old) return
        val i = folders.indexOf(old)
        if (i < 0) return
        if (n in folders) folders.removeAt(i) else folders[i] = n
        for (k in library.indices) if (library[k].folder == old) library[k] = library[k].copy(folder = n)
        if (libraryFolder == old) libraryFolder = n
        saveFolders()
        saveLibrary()
    }

    fun deleteFolder(name: String) {
        folders.remove(name)
        for (k in library.indices) if (library[k].folder == name) library[k] = library[k].copy(folder = "")
        if (libraryFolder == name) libraryFolder = null
        saveFolders()
        saveLibrary()
    }

    fun moveToFolder(id: String, folder: String) {
        if (folder.isNotEmpty()) addFolder(folder)
        updateEntry(id) { it.copy(folder = folder) }
        saveLibrary()
        messages.tryEmit(if (folder.isEmpty()) "أُزيل المستند من المادة" else "نُقل المستند إلى «$folder»")
    }

    // ---------- إحصاءات الدراسة ----------

    /** "yyyy-MM-dd" ← مللي ثانية لذلك اليوم، و"yyyy-MM-dd|id" ← وقت المستند في اليوم. */
    val stats = mutableStateMapOf<String, Long>()
    var goalMinutes by mutableStateOf(60)
        private set

    fun setGoal(minutes: Int) {
        goalMinutes = minutes.coerceIn(5, 24 * 60)
        prefs.edit().putInt("goal_min", goalMinutes).apply()
    }

    fun todayKey(): String = java.time.LocalDate.now().toString()

    private fun addStudy(id: String, dt: Long) {
        val day = todayKey()
        stats[day] = (stats[day] ?: 0L) + dt
        val k = "$day|$id"
        stats[k] = (stats[k] ?: 0L) + dt
    }

    private var statsLoaded = false

    private fun saveStats() {
        if (!statsLoaded) return
        val snap = stats.toMap()
        scope.launch { repo.saveStats(snap) }
    }
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

    /** عند تفعيله يلتقط الهايلايتر النص الذي يمرّ عليه ويضعه خلفه، ويضيفه إلى «ملخصات». */
    var highlightSnap by mutableStateOf(false)
        private set

    /** عرض الملخصات مرتبةً (جمل منظمة) بدل النص الأصلي كما التُقط. */
    var summaryTidy by mutableStateOf(true)
        private set

    fun toggleHighlightSnap() {
        highlightSnap = !highlightSnap
        prefs.edit().putBoolean("hl_snap", highlightSnap).apply()
        messages.tryEmit(
            if (highlightSnap) "التقاط النص مفعّل: مرّر الهايلايتر على السطر ليُميَّز ويُضاف إلى الملخصات"
            else "التقاط النص متوقف: الهايلايتر يرسم بحرية"
        )
    }

    fun toggleSummaryTidy() {
        summaryTidy = !summaryTidy
        prefs.edit().putBoolean("sum_tidy", summaryTidy).apply()
    }

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

    fun setTheme(t: AppTheme) {
        ThemeState.theme = t
        prefs.edit().putString("theme", t.name).apply()
    }

    // ---------- وضع التركيز ومؤقت الدراسة (25 دقيقة دراسة ثم 5 راحة) ----------

    var focusMode by mutableStateOf(false)

    /** 0 متوقف، 1 دراسة، 2 راحة. */
    var pomoPhase by mutableStateOf(0)
        private set
    var pomoLeftMs by mutableStateOf(0L)
        private set
    private var pomoEndsAt = 0L
    private var pomoJob: Job? = null
    private const val STUDY_MS = 25L * 60_000L
    private const val BREAK_MS = 5L * 60_000L

    fun startPomodoro() {
        pomoPhase = 1
        pomoEndsAt = System.currentTimeMillis() + STUDY_MS
        pomoLeftMs = STUDY_MS
        pomoJob?.cancel()
        pomoJob = scope.launch {
            while (isActive && pomoPhase != 0) {
                delay(1000)
                val now = System.currentTimeMillis()
                // أثناء جلسة الدراسة يُحسب وقت القراءة حتى بدون لمس الشاشة
                if (pomoPhase == 1) noteInteraction()
                pomoLeftMs = (pomoEndsAt - now).coerceAtLeast(0L)
                if (now >= pomoEndsAt) {
                    if (pomoPhase == 1) {
                        val k = "pomo|" + todayKey()
                        stats[k] = (stats[k] ?: 0L) + 1L
                        saveStats()
                        pomoPhase = 2
                        pomoEndsAt = now + BREAK_MS
                        messages.tryEmit("انتهت جلسة الدراسة. استرح 5 دقائق")
                    } else {
                        pomoPhase = 1
                        pomoEndsAt = now + STUDY_MS
                        messages.tryEmit("انتهت الراحة. جلسة دراسة جديدة 25 دقيقة")
                    }
                    pomoLeftMs = pomoEndsAt - now
                }
            }
        }
    }

    fun stopPomodoro() {
        pomoPhase = 0
        pomoJob?.cancel()
        pomoJob = null
    }

    // ---------- ألوان التمييز كتصنيفات ----------

    private val defaultLabels = mapOf(
        0xFFFFEB3B.toInt() to "تعريف",
        0xFF69F0AE.toInt() to "دواء",
        0xFFFF80AB.toInt() to "ملاحظة امتحان",
        0xFF40C4FF.toInt() to "مفهوم",
        0xFFFFAB40.toInt() to "مهم"
    )
    private val colorLabels = mutableStateMapOf<Int, String>()

    /** تجميع الملخص حسب لون التمييز (كل لون قسم بعنوان تصنيفه). */
    var summaryByColor by mutableStateOf(false)
        private set

    fun labelOf(c: Color): String {
        val k = c.toArgb()
        return colorLabels[k] ?: defaultLabels[k] ?: "لون آخر"
    }

    fun setLabel(c: Color, label: String) {
        colorLabels[c.toArgb()] = label
        prefs.edit().putString(
            "color_labels",
            org.json.JSONObject().apply { colorLabels.forEach { (k, v) -> put(k.toString(), v) } }.toString()
        ).apply()
    }

    fun toggleSummaryByColor() {
        summaryByColor = !summaryByColor
        prefs.edit().putBoolean("sum_by_color", summaryByColor).apply()
    }

    /** تحويل رسم القلم إلى أشكال مرتبة (خط، سهم، مستطيل، مثلث، دائرة). */
    var shapeSnap by mutableStateOf(true)
        private set

    fun toggleShapeSnap() {
        shapeSnap = !shapeSnap
        prefs.edit().putBoolean("shape_snap", shapeSnap).apply()
    }

    var laserMode by mutableStateOf(LaserMode.DOT)
        private set

    fun updateLaserMode(m: LaserMode) {
        laserMode = m
        prefs.edit().putString("laser_mode", m.name).apply()
    }

    /** يختار الأداة، ويذكّر مرة واحدة فقط بأن النقرة الثانية تفتح إعداداتها. */
    fun selectToolWithHint(t: Tool) {
        selectTool(t)
        if (!prefs.getBoolean("tool_hint_shown", false)) {
            prefs.edit().putBoolean("tool_hint_shown", true).apply()
            messages.tryEmit("اضغط على الأداة مرة ثانية لفتح إعداداتها: النوع واللون والحجم")
        }
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
        try {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(app)
        } catch (t: Throwable) {
        }
        prefs = app.getSharedPreferences("mynotes_session", Context.MODE_PRIVATE)

        (prefs.getString("custom_colors", "") ?: "")
            .split(",")
            .mapNotNull { it.toIntOrNull() }
            .forEach { customColors.add(Color(it)) }
        highlightSnap = prefs.getBoolean("hl_snap", false)
        shapeSnap = prefs.getBoolean("shape_snap", true)
        summaryByColor = prefs.getBoolean("sum_by_color", false)
        try {
            val o = org.json.JSONObject(prefs.getString("color_labels", "{}") ?: "{}")
            for (k in o.keys()) k.toIntOrNull()?.let { colorLabels[it] = o.getString(k) }
        } catch (e: Throwable) {
        }
        goalMinutes = prefs.getInt("goal_min", 60)
        try {
            val arr = org.json.JSONArray(prefs.getString("folders", "[]") ?: "[]")
            for (i in 0 until arr.length()) folders.add(arr.getString(i))
        } catch (e: Throwable) {
        }
        ThemeState.theme = try {
            AppTheme.valueOf(prefs.getString("theme", "NAVY") ?: "NAVY")
        } catch (e: Throwable) {
            AppTheme.NAVY
        }
        laserMode = try {
            LaserMode.valueOf(prefs.getString("laser_mode", "DOT") ?: "DOT")
        } catch (e: Throwable) {
            LaserMode.DOT
        }
        summaryTidy = prefs.getBoolean("sum_tidy", true)

        scope.launch {
            val list = withContext(Dispatchers.IO) { repo.loadIndex() }
            library.clear()
            library.addAll(list)
            // مواد موجودة في المستندات ولم تُسجَّل بعد
            list.map { it.folder }.filter { it.isNotEmpty() && it !in folders }.distinct().forEach { folders.add(it) }
            // دمج بالجمع: لا يضيع وقت سُجّل قبل انتهاء التحميل
            withContext(Dispatchers.IO) { repo.loadStats() }.forEach { (k, v) -> stats[k] = (stats[k] ?: 0L) + v }
            statsLoaded = true
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
                s.applyPending()
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
            messages.tryEmit("يُقرأ نص الملف مباشرة (العربية مدعومة). الصفحات الممسوحة ضوئيًا تُقرأ بالإنجليزية فقط")
        }
    }

    fun activate(id: String?) {
        if (id != null && tabs.none { it.id == id }) return
        recordPage(activeId)
        activeId = id
        noteInteraction()
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
        if (stats.isNotEmpty()) saveStats()
        saveSession()
    }

    // ---------- البحث في نص كل المكتبة ----------

    class LibraryHit(val docId: String, val docName: String, val page: Int, val snippet: String)

    /** يبحث في النص المقروء لكل المستندات (طبقة النص أو القراءة الضوئية المحفوظة). */
    suspend fun searchLibrary(query: String): List<LibraryHit> = withContext(Dispatchers.IO) {
        val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return@withContext emptyList()
        val out = ArrayList<LibraryHit>()
        for (e in library.toList()) {
            val pages = repo.loadOcrAll(e.id)
            for (page in pages.keys.sorted()) {
                val words = pages[page]?.words ?: continue
                var i = 0
                var perPage = 0
                while (i + tokens.size <= words.size && perPage < 3) {
                    val ok = tokens.indices.all { j -> words[i + j].text.lowercase().contains(tokens[j]) }
                    if (ok) {
                        val a = (i - 6).coerceAtLeast(0)
                        val z = (i + tokens.size + 10).coerceAtMost(words.size)
                        val snippet = (if (a > 0) "… " else "") + words.subList(a, z).joinToString(" ") { it.text } +
                            (if (z < words.size) " …" else "")
                        out.add(LibraryHit(e.id, e.name, page, snippet))
                        perPage++
                        i += tokens.size
                    } else {
                        i++
                    }
                }
                if (out.size >= 300) return@withContext out
            }
        }
        out
    }

    /** يفتح مستندًا ويبحث فيه عن النص وينتقل إلى الصفحة. */
    fun openAt(id: String, page: Int, query: String) {
        openDocument(id)
        val s = tabs.firstOrNull { it.id == id } ?: return
        s.pendingQuery = query
        s.pendingPage = page
        if (s.status is SessionStatus.Ready) s.applyPending()
    }

    // ---------- ملخص المادة كاملة ----------

    /** يجمع ملخصات كل مستندات المادة في ملف PDF واحد، قسم لكل مستند. */
    fun exportFolderSummary(context: Context, folder: String, uri: Uri) {
        if (exporting != null) return
        val docs = library.filter { it.folder == folder }
        if (docs.isEmpty()) return
        exporting = "جارٍ حفظ ملخص المادة…"
        scope.launch {
            try {
                val pages = withContext(Dispatchers.IO) {
                    val sections = docs.mapNotNull { e ->
                        val data = repo.loadDocData(e.id)
                        val marks = data.marks.values.flatten().sortedWith(
                            compareBy<TextMark>({ it.page }, { m -> m.rects.minOfOrNull { it.t } ?: 0f }, { m -> m.rects.minOfOrNull { it.l } ?: 0f })
                        )
                        val blocks = Summary.display(marks, summaryTidy, data.edits, null, summaryByColor) { labelOf(it) }
                        if (blocks.isEmpty()) null else e.name to blocks
                    }
                    if (sections.isEmpty()) 0
                    else Exporter.exportSummarySections(context.applicationContext, "ملخص مادة: $folder", sections, uri)
                }
                messages.tryEmit(if (pages == 0) "لا توجد ملخصات في مستندات هذه المادة" else "تم حفظ ملخص المادة ($pages صفحة)")
            } catch (t: Throwable) {
                messages.tryEmit("تعذر حفظ ملخص المادة")
            } finally {
                exporting = null
            }
        }
    }

    // ---------- عداد وقت القراءة ----------

    /** آخر لمسة أو حركة قلم في التطبيق؛ يتوقف العداد إن لم يتفاعل المستخدم 5 دقائق. */
    @Volatile
    var lastInteractionAt: Long = 0L

    private var clockJob: Job? = null
    private const val IDLE_MS = 5L * 60L * 1000L
    private const val TICK_MS = 15_000L

    fun noteInteraction() {
        lastInteractionAt = System.currentTimeMillis()
    }

    /** يُستدعى من MainActivity عند ظهور التطبيق واختفائه. يحسب الوقت للمستند المفتوح فقط. */
    fun setVisible(visible: Boolean) {
        clockJob?.cancel()
        clockJob = null
        if (!visible) return
        noteInteraction()
        clockJob = scope.launch {
            var last = System.currentTimeMillis()
            var ticks = 0
            while (isActive) {
                delay(TICK_MS)
                val now = System.currentTimeMillis()
                val dt = (now - last).coerceIn(0L, TICK_MS * 2)
                last = now
                val id = activeId ?: continue
                if (now - lastInteractionAt > IDLE_MS) continue
                updateEntry(id) { it.copy(readMs = it.readMs + dt) }
                addStudy(id, dt)
                ticks++
                if (ticks % 4 == 0) {
                    saveLibrary()
                    saveStats()
                }
            }
        }
    }

    fun readMsOf(id: String): Long = library.firstOrNull { it.id == id }?.readMs ?: 0L

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

    // ---------- مربعات النص ----------

    /** ينشئ مربع نص جديدًا في أعلى الصفحة بلون القلم الحالي، أو يعدّل نص مربع موجود. */
    fun saveTextBox(s: DocSession, req: TextDialogReq, text: String) {
        val clean = text.trimEnd()
        val existing = s.textBox(req.boxId)
        if (existing != null) {
            if (clean.isBlank()) {
                s.deleteTextBox(existing.id)
            } else {
                s.beginEdit()
                s.updateTextBox(existing.id) { it.copy(text = clean) }
                s.endEdit()
            }
            return
        }
        if (clean.isBlank()) return
        val pen = if (tool.isPen()) tool else lastPen
        s.addTextBox(
            TextBox(
                id = UUID.randomUUID().toString(),
                page = req.page,
                x = 0.12f,
                y = 0.12f,
                w = 0.6f,
                text = clean,
                color = colorFor(pen),
                size = 0.024f
            )
        )
        messages.tryEmit("أُضيف النص. اسحبه بإصبعك لتحريكه، وانقر عليه مرة ثانية لتعديله")
    }

    // ---------- التصدير ----------

    /** نص حالة التصدير الجاري (null = لا يوجد تصدير). */
    var exporting by mutableStateOf<String?>(null)
        private set

    fun exportDocument(context: Context, s: DocSession, uri: Uri) {
        if (exporting != null) return
        val snap = Exporter.snapshotOf(s) ?: return
        val total = snap.pages.size
        exporting = "جارٍ حفظ PDF…"
        scope.launch {
            try {
                val selectable = withContext(Dispatchers.IO) {
                    val entry = library.firstOrNull { it.id == s.id }
                    val partsDir = repo.partsDir(s.id)
                    try {
                        // أولًا: نص قابل للتحديد فوق صفحات الملف الأصلية
                        Exporter.exportVector(context.applicationContext, snap, { src ->
                            when {
                                src == "main" && entry != null -> repo.file(entry)
                                src.startsWith("part:") -> File(partsDir, src.removePrefix("part:"))
                                else -> null
                            }
                        }, uri) { i -> exporting = "جارٍ حفظ PDF: صفحة ${i + 1} من $total" }
                        true
                    } catch (t: Throwable) {
                        // احتياط: صفحات كصور (الطريقة السابقة)
                        Exporter.exportAnnotated(context.applicationContext, snap, uri) { i ->
                            exporting = "جارٍ حفظ PDF كصور: صفحة ${i + 1} من $total"
                        }
                        false
                    }
                }
                messages.tryEmit(
                    if (selectable) "تم حفظ المستند مع التعليقات ($total صفحة)، والنص قابل للتحديد"
                    else "تم حفظ المستند مع التعليقات ($total صفحة) كصور"
                )
            } catch (t: Throwable) {
                messages.tryEmit("تعذر حفظ ملف PDF")
            } finally {
                exporting = null
            }
        }
    }

    fun exportSummary(context: Context, s: DocSession, uri: Uri) {
        if (exporting != null) return
        val blocks = Summary.display(s.allMarks(), summaryTidy, s.summaryEdits, null, summaryByColor) { labelOf(it) }
        val title = s.title
        exporting = "جارٍ حفظ الملخصات…"
        scope.launch {
            try {
                val pages = withContext(Dispatchers.IO) {
                    Exporter.exportSummary(context.applicationContext, title, blocks, uri)
                }
                messages.tryEmit("تم حفظ الملخصات ($pages صفحة)")
            } catch (t: Throwable) {
                messages.tryEmit("تعذر حفظ الملخصات")
            } finally {
                exporting = null
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
