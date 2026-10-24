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
    lateinit var appContext: Context
        private set
    private var initialized = false

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** آخر لحظة كتب فيها المستخدم بالقلم؛ تتوقف القراءة الضوئية مؤقتًا بعدها كي لا تؤخّر الكتابة. */
    @Volatile
    var lastInkAt: Long = 0L


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
        examDates.remove(old)?.let { examDates[n] = it; saveExamDates() }
        saveFolders()
        saveLibrary()
    }

    fun deleteFolder(name: String) {
        folders.remove(name)
        for (k in library.indices) if (library[k].folder == name) library[k] = library[k].copy(folder = "")
        if (libraryFolder == name) libraryFolder = null
        if (examDates.remove(name) != null) saveExamDates()
        saveFolders()
        saveLibrary()
    }

    fun moveToFolder(id: String, folder: String) {
        if (folder.isNotEmpty()) addFolder(folder)
        updateEntry(id) { it.copy(folder = folder) }
        saveLibrary()
        messages.tryEmit(if (folder.isEmpty()) L("أُزيل المستند من المادة", "Document removed from the subject") else L("نُقل المستند إلى «$folder»", "Document moved to “$folder”"))
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
        scope.launch {
            repo.saveStats(snap)
            StudyWidget.refresh(appContext)
        }
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
    /** التمييز الجديد يُضاف إلى الملخصات (false = تمييز على الصفحة فقط). */
    var highlightToSummary by mutableStateOf(true)
        private set

    fun toggleHighlightToSummary() {
        highlightToSummary = !highlightToSummary
        prefs.edit().putBoolean("hl_summary", highlightToSummary).apply()
        messages.tryEmit(
            if (highlightToSummary) L("التمييز الجديد يُضاف إلى الملخصات", "New highlights are added to summaries")
            else L("التمييز الجديد يبقى على الصفحة فقط ولا يظهر في الملخصات", "New highlights stay on the page only and are not added to summaries")
        )
    }

    var highlightSnap by mutableStateOf(false)
        private set

    /** عرض الملخصات مرتبةً (جمل منظمة) بدل النص الأصلي كما التُقط. */
    var summaryTidy by mutableStateOf(true)
        private set

    fun toggleHighlightSnap() {
        highlightSnap = !highlightSnap
        prefs.edit().putBoolean("hl_snap", highlightSnap).apply()
        messages.tryEmit(
            if (highlightSnap) L("التقاط النص مفعّل: مرّر الهايلايتر على السطر ليُميَّز ويُضاف إلى الملخصات", "Text snap on: run the highlighter over a line to mark it and add it to summaries")
            else L("التقاط النص متوقف: الهايلايتر يرسم بحرية", "Text snap off: the highlighter draws freely")
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

    /** آخر أداة كتابة (قلم أو هايلايتر)، لزر القلم في نمط «شريط الأقلام». */
    var lastWriting by mutableStateOf(Tool.FOUNTAIN)
        private set

    /** أداة التحديد الحر (لاسو) فعّالة بدل أداة الكتابة. */
    var lassoActive by mutableStateOf(false)

    fun selectTool(t: Tool) {
        lassoActive = false
        tool = t
        if (t.isPen()) lastPen = t
        if (t.isPen() || t == Tool.HIGHLIGHTER) lastWriting = t
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
    /** مدة جلسة الدراسة والراحة بالدقائق (يختارها المستخدم). */
    var studyMin by mutableStateOf(25)
        private set
    var breakMin by mutableStateOf(5)
        private set
    private val STUDY_MS get() = studyMin * 60_000L
    private val BREAK_MS get() = breakMin * 60_000L

    fun setPomodoro(study: Int, rest: Int) {
        studyMin = study.coerceIn(5, 180)
        breakMin = rest.coerceIn(1, 60)
        prefs.edit().putInt("pomo_study", studyMin).putInt("pomo_break", breakMin).apply()
    }

    /** المؤقت متوقف مؤقتًا (يحتفظ بالوقت المتبقي). */
    var pomoPaused by mutableStateOf(false)
        private set

    fun startPomodoro() {
        pomoPhase = 1
        pomoPaused = false
        pomoLeftMs = STUDY_MS
        runPomodoro()
    }

    fun pausePomodoro() {
        if (pomoPhase == 0 || pomoPaused) return
        pomoLeftMs = (pomoEndsAt - System.currentTimeMillis()).coerceAtLeast(0L)
        pomoPaused = true
        pomoJob?.cancel()
        pomoJob = null
    }

    fun resumePomodoro() {
        if (pomoPhase == 0 || !pomoPaused) return
        pomoPaused = false
        runPomodoro()
    }

    /** يشغّل العدّ التنازلي من الوقت المتبقي الحالي. */
    private fun runPomodoro() {
        pomoEndsAt = System.currentTimeMillis() + pomoLeftMs
        pomoJob?.cancel()
        pomoJob = scope.launch {
            while (isActive && pomoPhase != 0 && !pomoPaused) {
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
                        messages.tryEmit(L("انتهت جلسة الدراسة. استرح $breakMin دقائق", "Study session finished. Take a $breakMin-minute break"))
                    } else {
                        pomoPhase = 1
                        pomoEndsAt = now + STUDY_MS
                        messages.tryEmit(L("انتهت الراحة. جلسة دراسة جديدة $studyMin دقيقة", "Break over. New $studyMin-minute study session"))
                    }
                    pomoLeftMs = pomoEndsAt - now
                }
            }
        }
    }

    fun stopPomodoro() {
        pomoPhase = 0
        pomoPaused = false
        pomoJob?.cancel()
        pomoJob = null
    }

    // ---------- ألوان التمييز كتصنيفات ----------

    private val defaultLabels get() = mapOf(
        0xFFFFEB3B.toInt() to L("تعريف", "Definition"),
        0xFF69F0AE.toInt() to L("دواء", "Drug"),
        0xFFFF80AB.toInt() to L("ملاحظة امتحان", "Exam note"),
        0xFF40C4FF.toInt() to L("مفهوم", "Concept"),
        0xFFFFAB40.toInt() to L("مهم", "Important")
    )
    private val colorLabels = mutableStateMapOf<Int, String>()

    /** تجميع الملخص حسب لون التمييز (كل لون قسم بعنوان تصنيفه). */
    var summaryByColor by mutableStateOf(false)
        private set

    fun labelOf(c: Color): String {
        val k = c.toArgb()
        return colorLabels[k] ?: defaultLabels[k] ?: L("لون آخر", "Other color")
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
    // ---------- إيماءات القلم والأصابع ----------

    /** نقرتان على القلم (أو زره) تبدّلان بين الممحاة وآخر قلم مستخدم. */
    var penDoubleTapEnabled by mutableStateOf(true)
        private set

    // ---------- أقلامي المفضلة ----------

    class PenPreset(val tool: Tool, val color: Int, val size: Int)

    val presets = mutableStateListOf<PenPreset>()

    private fun savePresets() {
        val a = org.json.JSONArray()
        presets.forEach { a.put(org.json.JSONObject().put("t", it.tool.name).put("c", it.color).put("s", it.size)) }
        prefs.edit().putString("pen_presets", a.toString()).apply()
    }

    /** يحفظ الأداة الحالية بلونها وحجمها كقلم مفضل. */
    fun addPreset() {
        val t = tool
        if (!(t.isPen() || t == Tool.HIGHLIGHTER)) return
        val p = PenPreset(t, colorFor(t).toArgb(), sizeFor(t))
        if (presets.any { it.tool == p.tool && it.color == p.color && it.size == p.size }) return
        if (presets.size >= 12) presets.removeAt(0)
        presets.add(p)
        savePresets()
        messages.tryEmit(L("حُفظ في أقلامي المفضلة (اضغط مطولًا لحذفه)", "Saved to favorite pens (long-press to remove)"))
    }

    fun applyPreset(p: PenPreset) {
        selectTool(p.tool)
        setColor(Color(p.color))
        setSize(p.size)
    }

    fun removePreset(p: PenPreset) {
        presets.remove(p)
        savePresets()
    }

    // ---------- القراءة الليلية ----------

    /** عكس ألوان الصفحات (خلفية داكنة ونص فاتح) مع الحفاظ على ألوان الكتابة والتمييز. */
    var nightPages by mutableStateOf(false)
        private set

    fun toggleNightPages() {
        nightPages = !nightPages
        prefs.edit().putBoolean("night_pages", nightPages).apply()
    }

    // ---------- مواعيد الامتحانات ----------

    /** موعد امتحان كل مادة (epochDay). */
    val examDates = mutableStateMapOf<String, Long>()

    private fun saveExamDates() {
        val o = org.json.JSONObject()
        examDates.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString("exam_dates", o.toString()).apply()
        Reminders.schedule(appContext)
    }

    fun setExamDate(folder: String, epochDay: Long?) {
        if (epochDay == null) examDates.remove(folder) else examDates[folder] = epochDay
        saveExamDates()
    }

    /** أيام متبقية حتى امتحان المادة (0 = اليوم)، أو null إن لم يُحدد أو مضى. */
    fun daysToExam(folder: String): Long? {
        val d = examDates[folder] ?: return null
        val left = d - java.time.LocalDate.now().toEpochDay()
        return if (left < 0) null else left
    }

    /** الصفحات التي لم تصلها بعد في مستندات المادة. */
    fun pagesLeft(folder: String): Int =
        library.filter { it.folder == folder }.sumOf { e -> (e.pageCount - (e.lastPage + 1)).coerceAtLeast(0) }

    // ---------- التذكير اليومي ----------

    var reminderOn by mutableStateOf(false)
        private set
    var reminderMinute by mutableStateOf(20 * 60)
        private set

    fun setReminder(on: Boolean, minuteOfDay: Int = reminderMinute) {
        reminderOn = on
        reminderMinute = minuteOfDay.coerceIn(0, 24 * 60 - 1)
        prefs.edit().putBoolean("reminder_on", reminderOn).putInt("reminder_min", reminderMinute).apply()
        Reminders.schedule(appContext)
    }

    // ---------- قفل المستندات ----------

    /** مستندات فُتح قفلها في هذه الجلسة (تُقفل من جديد عند إعادة تشغيل التطبيق). */
    val unlocked = mutableStateListOf<String>()

    /** مستند ينتظر إدخال الرمز أو البصمة قبل فتحه. */
    var unlockRequest by mutableStateOf<String?>(null)

    /** طلب تعيين رمز جديد (ثم قفل هذا المستند). */
    var setPinFor by mutableStateOf<String?>(null)

    fun hasPin(): Boolean = prefs.getString("lock_pin", null) != null

    private fun hashPin(pin: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(("mynotes:" + pin).toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun setPin(pin: String) {
        prefs.edit().putString("lock_pin", hashPin(pin)).apply()
    }

    fun checkPin(pin: String): Boolean = prefs.getString("lock_pin", null) == hashPin(pin)

    fun toggleLock(id: String) {
        val e = library.firstOrNull { it.id == id } ?: return
        if (!e.locked && !hasPin()) {
            setPinFor = id
            return
        }
        updateEntry(id) { it.copy(locked = !it.locked) }
        if (e.locked) unlocked.remove(id) else unlocked.add(id)
        saveLibrary()
        messages.tryEmit(if (e.locked) L("أُلغي قفل المستند", "Document unlocked") else L("قُفل المستند: يحتاج الرمز أو البصمة لفتحه", "Document locked: needs your PIN or fingerprint to open"))
    }

    /** بعد التحقق: يُفتح المستند المطلوب. */
    fun unlockGranted() {
        val id = unlockRequest ?: return
        unlockRequest = null
        if (id !in unlocked) unlocked.add(id)
        openDocument(id)
    }

    /** فتح آخر مستند (من أداة الشاشة الرئيسية). */
    fun openLast() {
        scope.launch {
            while (!libraryLoaded) kotlinx.coroutines.delay(50)
            library.filter { !it.locked }.maxByOrNull { it.lastOpened }?.let { openDocument(it.id) }
        }
    }

    /** لغة الواجهة. */
    fun setEnglish(en: Boolean) {
        I18n.english = en
        prefs.edit().putBoolean("lang_en", en).apply()
    }

    fun togglePenDoubleTap() {
        penDoubleTapEnabled = !penDoubleTapEnabled
        prefs.edit().putBoolean("pen_dtap", penDoubleTapEnabled).apply()
    }

    /** نقرة بإصبعين للتراجع، وبثلاثة للإعادة. */
    var fingerUndo by mutableStateOf(true)
        private set

    fun toggleFingerUndo() {
        fingerUndo = !fingerUndo
        prefs.edit().putBoolean("finger_undo", fingerUndo).apply()
    }

    /** رمز المفتاح الذي يرسله القلم عند النقر المزدوج (يتعلّمه التطبيق إن لم يكن معروفًا). */
    var penKeyCode by mutableStateOf(-1)
        private set
    var penKeyLearning by mutableStateOf(false)
        private set
    private var learnJob: Job? = null

    /** يبدأ تعلّم إشارة القلم لمدة 20 ثانية فقط، حتى لا يُلتقط مفتاح لوحة مفاتيح لاحقًا بالخطأ. */
    fun learnPenKey(on: Boolean) {
        learnJob?.cancel()
        penKeyLearning = on
        if (on) learnJob = scope.launch {
            delay(20_000)
            if (penKeyLearning) {
                penKeyLearning = false
                messages.tryEmit(L("لم تصل إشارة من القلم، أُلغي التعليم", "No signal from the pen; teaching cancelled"))
            }
        }
    }

    private var lastPenToggleAt = 0L

    fun penDoubleTap() {
        if (!penDoubleTapEnabled || activeId == null) return
        val now = System.currentTimeMillis()
        if (now - lastPenToggleAt < 600L) return
        lastPenToggleAt = now
        if (tool == Tool.ERASER) selectTool(lastWriting) else selectTool(Tool.ERASER)
    }

    /**
     * مفاتيح القلم: أزرار القلم في أندرويد 14 (308..311)، و718 الذي يرسله قلم هواوي M-Pencil
     * عند النقر المزدوج (يصل مرتين لكل نقرة مزدوجة، فنعدّهما نقرة واحدة).
     */
    private val stylusKeys = setOf(308, 309, 310, 311, 718)

    /** يعالج مفتاحًا من القلم؛ true = استُهلك. */
    fun handlePenKey(code: Int, down: Boolean, repeat: Int): Boolean {
        if (penKeyLearning && down && code != android.view.KeyEvent.KEYCODE_BACK &&
            code != android.view.KeyEvent.KEYCODE_HOME
        ) {
            learnPenKey(false)
            penKeyCode = code
            prefs.edit().putInt("pen_key", code).apply()
            messages.tryEmit(L("تم: نقرتا القلم تبدّلان الآن بين الممحاة وآخر قلم", "Done: double-tapping the pen now switches between the eraser and your last pen"))
            return true
        }
        if (code in stylusKeys || (penKeyCode >= 0 && code == penKeyCode)) {
            if (down && repeat == 0) penDoubleTap()
            return activeId != null && penDoubleTapEnabled
        }
        return false
    }

    // ---------- الإعدادات: شكل شريط الأدوات والأدوات الظاهرة ----------

    /** نافذة الإعدادات (تُفتح من المكتبة أو من داخل المستند). */
    var settingsOpen by mutableStateOf(false)

    var toolbarStyle by mutableStateOf(ToolbarStyle.PEN_BAR)
        private set

    fun updateToolbarStyle(st: ToolbarStyle) {
        toolbarStyle = st
        prefs.edit().putString("toolbar_style", st.name).apply()
    }

    /** شريط الأقلام الثاني مفتوح (في نمط «شريط الأقلام»). */
    var penBarOpen by mutableStateOf(false)

    /** الأدوات الاختيارية المخفية من شريط الأدوات (الأدوات الأساسية لا تُخفى). */
    val hiddenTools = mutableStateListOf<String>()

    fun toolVisible(id: String): Boolean = id !in hiddenTools

    /** الأدوات المخفية في الشريط الافتراضي (تظهر بقية الأدوات). */
    // الشريط الافتراضي: الأدوات الأساسية فقط (تحديد، أقلام، ممحاة، نص، صورة، ملاحظة، ليزر، تسجيل، بحث، ذكاء، إضافة صفحة، حفظ، تراجع)،
    // وبقية الأدوات تُضاف من «تخصيص شريط الأدوات»
    val DEFAULT_HIDDEN_TOOLS = listOf(
        "zoomwin", "ocr", "zoom", "night", "nav", "bookmark", "timer", "link", "split", "fit", "focus", "readtimer"
    )

    fun resetToolbar() {
        hiddenTools.clear()
        hiddenTools.addAll(DEFAULT_HIDDEN_TOOLS)
        prefs.edit().putString("hidden_tools", hiddenTools.joinToString(",")).apply()
    }

    fun setToolVisible(id: String, visible: Boolean) {
        if (visible) hiddenTools.remove(id) else if (id !in hiddenTools) hiddenTools.add(id)
        prefs.edit().putString("hidden_tools", hiddenTools.joinToString(",")).apply()
    }

    var shapeSnap by mutableStateOf(true)
        private set

    fun toggleShapeSnap() {
        shapeSnap = !shapeSnap
        prefs.edit().putBoolean("shape_snap", shapeSnap).apply()
    }

    var laserMode by mutableStateOf(LaserMode.DOT)
        private set

    var eraserMode by mutableStateOf(EraserMode.STROKE)
        private set

    fun updateEraserMode(m: EraserMode) {
        eraserMode = m
        prefs.edit().putString("eraser_mode", m.name).apply()
    }

    fun updateLaserMode(m: LaserMode) {
        laserMode = m
        prefs.edit().putString("laser_mode", m.name).apply()
    }

    /** يختار الأداة، ويذكّر مرة واحدة فقط بأن النقرة الثانية تفتح إعداداتها. */
    fun selectToolWithHint(t: Tool) {
        selectTool(t)
        if (!prefs.getBoolean("tool_hint_shown", false)) {
            prefs.edit().putBoolean("tool_hint_shown", true).apply()
            messages.tryEmit(L("اضغط على الأداة مرة ثانية لفتح إعداداتها: النوع واللون والحجم", "Tap the tool again to open its settings: type, color and size"))
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
        appContext = app
        Ai.init(app)
        repo = LibraryRepository(app)
        AudioNotes.init(app)
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
        highlightToSummary = prefs.getBoolean("hl_summary", true)
        shapeSnap = prefs.getBoolean("shape_snap", true)
        penDoubleTapEnabled = prefs.getBoolean("pen_dtap", true)
        fingerUndo = prefs.getBoolean("finger_undo", true)
        penKeyCode = prefs.getInt("pen_key", -1)
        I18n.english = prefs.getBoolean("lang_en", false)
        studyMin = prefs.getInt("pomo_study", 25)
        breakMin = prefs.getInt("pomo_break", 5)
        nightPages = prefs.getBoolean("night_pages", false)
        reminderOn = prefs.getBoolean("reminder_on", false)
        reminderMinute = prefs.getInt("reminder_min", 20 * 60)
        try {
            val a = org.json.JSONArray(prefs.getString("pen_presets", "[]") ?: "[]")
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                presets.add(PenPreset(Tool.valueOf(o.getString("t")), o.getInt("c"), o.getInt("s")))
            }
        } catch (e: Throwable) {
        }
        try {
            val o = org.json.JSONObject(prefs.getString("exam_dates", "{}") ?: "{}")
            for (k in o.keys()) examDates[k] = o.getLong(k)
        } catch (e: Throwable) {
        }
        eraserMode = try {
            EraserMode.valueOf(prefs.getString("eraser_mode", "STROKE") ?: "STROKE")
        } catch (e: Throwable) {
            EraserMode.STROKE
        }
        toolbarStyle = try {
            ToolbarStyle.valueOf(prefs.getString("toolbar_style", "PEN_BAR") ?: "PEN_BAR")
        } catch (e: Throwable) {
            ToolbarStyle.PEN_BAR
        }
        hiddenTools.clear()
        // أول تشغيل: الشريط الافتراضي، ثم يضيف المستخدم ما يريد من الإعدادات
        // ترقية مرة واحدة إلى الشريط الافتراضي الجديد (MyNotes Pro)
        if (prefs.getInt("toolbar_v", 1) < 2) {
            prefs.edit().putString("hidden_tools", DEFAULT_HIDDEN_TOOLS.joinToString(",")).putInt("toolbar_v", 2).apply()
        }
        val savedHidden = prefs.getString("hidden_tools", null)
        (savedHidden?.split(",")?.filter { it.isNotBlank() } ?: DEFAULT_HIDDEN_TOOLS).forEach { hiddenTools.add(it) }
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
        resumeLast = prefs.getBoolean("resume_last", true)
        openAsTabs = prefs.getBoolean("open_as_tabs", true)

        scope.launch {
            val list = withContext(Dispatchers.IO) { repo.loadIndex() }
            library.clear()
            library.addAll(list)
            // مواد موجودة في المستندات ولم تُسجَّل بعد
            list.map { it.folder }.filter { it.isNotEmpty() && it !in folders }.distinct().forEach { folders.add(it) }
            // دمج بالجمع: لا يضيع وقت سُجّل قبل انتهاء التحميل
            withContext(Dispatchers.IO) { repo.loadStats() }.forEach { (k, v) -> stats[k] = (stats[k] ?: 0L) + v }
            statsLoaded = true
            // «المتابعة من حيث توقفت»: إن أُطفئ يبدأ التطبيق من المكتبة
            val ids = if (!resumeLast) emptyList() else (prefs.getString("open", "") ?: "").split(",").filter { it.isNotBlank() }
            val active = prefs.getString("active", null)
            ids.forEach { id ->
                // المستندات المقفلة لا تُفتح تلقائيًا بعد إعادة تشغيل التطبيق
                library.firstOrNull { it.id == id && !it.locked }?.let { openSession(it, activate = false) }
            }
            activeId = active?.takeIf { a -> tabs.any { it.id == a } }
            libraryLoaded = true
        }
    }

    // ---------- التبويبات ----------

    fun openDocument(id: String) {
        val e0 = library.firstOrNull { it.id == id }
        if (e0 != null && e0.locked && id !in unlocked) {
            unlockRequest = id
            return
        }
        if (tabs.any { it.id == id }) {
            activate(id)
            return
        }
        val entry = library.firstOrNull { it.id == id } ?: return
        // «فتح المستندات في تبويبات» مطفأ: المستند الجديد يحل محل المفتوح
        if (!openAsTabs) tabs.map { it.id }.forEach { closeTab(it) }
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
            }
        }
        saveSession()
    }

    // ---------- عرض مستندين جنبًا إلى جنب ----------

    /** المستند الظاهر في النصف الثاني من الشاشة؛ null = بدون تقسيم. */
    var splitId by mutableStateOf<String?>(null)
        private set
    /** نسبة عرض المستند الأول (0.25..0.75). */
    var splitFrac by mutableStateOf(0.5f)

    fun openSplit(id: String) {
        val entry = library.firstOrNull { it.id == id } ?: return
        if (entry.locked && id !in unlocked) {
            messages.tryEmit(L("افتح قفل المستند أولًا", "Unlock the document first"))
            return
        }
        if (id == activeId) return
        if (tabs.none { it.id == id }) openSession(entry, activate = false)
        splitId = id
        updateEntry(id) { it.copy(lastOpened = System.currentTimeMillis()) }
    }

    fun closeSplit() {
        splitId = null
    }

    /** إعدادات المستندات (MyNotes Pro). */
    var resumeLast by mutableStateOf(true)
        private set
    var openAsTabs by mutableStateOf(true)
        private set
    fun toggleResumeLast() { resumeLast = !resumeLast; prefs.edit().putBoolean("resume_last", resumeLast).apply() }
    fun toggleOpenAsTabs() { openAsTabs = !openAsTabs; prefs.edit().putBoolean("open_as_tabs", openAsTabs).apply() }

    fun activate(id: String?) {
        if (id != null && tabs.none { it.id == id }) return
        if (id == null || id == splitId) splitId = null
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
        if (splitId == id) splitId = null
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
        tabs.forEach { it.flushPersist() }
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
            if (e.locked && e.id !in unlocked) continue
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
    /** الرجوع من آخر رابط تبعته (المستند والصفحة). */
    var linkBack by mutableStateOf<Pair<String, Int>?>(null)

    /** يتبع رابطًا: يفتح المستند على الصفحة، ويحفظ مكانك للرجوع. */
    fun followLink(from: DocSession, docId: String, page: Int) {
        if (library.none { it.id == docId }) {
            messages.tryEmit(L("المستند المرتبط لم يعد موجودًا", "The linked document no longer exists"))
            return
        }
        linkBack = from.id to from.listState.firstVisibleItemIndex
        openPage(docId, page)
    }

    /** يفتح مستندًا على صفحة محددة. */
    fun openPage(id: String, page: Int) {
        openDocument(id)
        val s = tabs.firstOrNull { it.id == id } ?: return
        if (s.status is SessionStatus.Ready) {
            scope.launch { s.listState.scrollToItem(page.coerceIn(0, maxOf(0, (s.pdf?.count ?: 1) - 1))) }
        } else {
            s.pendingJump = page
        }
    }

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
        // الملخص يُقرأ من الملفات المحفوظة: ما لم يُحفظ بعد من المستندات المفتوحة يُكتب أولًا
        tabs.forEach { it.flushPersist() }
        exporting = L("جارٍ حفظ ملخص المادة…", "Saving subject summary…")
        scope.launch {
            try {
                val pages = withContext(Dispatchers.IO) {
                    val sections = docs.mapNotNull { e ->
                        val data = repo.loadDocData(e.id)
                        val marks = data.marks.values.flatten().filter { it.inSummary }.sortedWith(
                            compareBy<TextMark>({ it.page }, { m -> m.rects.minOfOrNull { it.t } ?: 0f }, { m -> m.rects.minOfOrNull { it.l } ?: 0f })
                        )
                        val blocks = Summary.display(marks, summaryTidy, data.edits, null, summaryByColor) { labelOf(it) }
                        if (blocks.isEmpty()) null else e.name to blocks
                    }
                    if (sections.isEmpty()) 0
                    else Exporter.exportSummarySections(context.applicationContext, L("ملخص مادة: $folder", "Subject summary: $folder"), sections, uri)
                }
                messages.tryEmit(if (pages == 0) L("لا توجد ملخصات في مستندات هذه المادة", "No summaries in this subject’s documents") else L("تم حفظ ملخص المادة ($pages صفحة)", "Subject summary saved ($pages pages)"))
            } catch (t: Throwable) {
                messages.tryEmit(L("تعذر حفظ ملخص المادة", "Could not save the subject summary"))
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
                messages.tryEmit(L("أُضيفت الصورة. اسحبها لتحريكها واسحب النقطة في زاويتها لتغيير حجمها", "Image added. Drag to move it, drag the corner dot to resize"))
            } catch (t: Throwable) {
                messages.tryEmit(L("تعذر إضافة الصورة", "Could not add the image"))
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
        if (req.sticky) {
            s.addTextBox(
                TextBox(
                    id = UUID.randomUUID().toString(), page = req.page,
                    x = 0.82f, y = 0.08f, w = 0.05f,
                    text = clean, color = Color(0xFF6B4E00), size = 0.022f, sticky = true
                )
            )
            messages.tryEmit(L("أُضيفت الملاحظة اللاصقة. انقر عليها لفتحها، واسحبها لتحريكها", "Sticky note added. Tap to open it, drag to move it"))
            return
        }
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
        messages.tryEmit(L("أُضيف النص. اسحبه بإصبعك لتحريكه، وانقر عليه مرة ثانية لتعديله", "Text added. Drag it with your finger to move it, tap it again to edit"))
    }

    // ---------- التصدير ----------

    /** نص حالة التصدير الجاري (null = لا يوجد تصدير). */
    var exporting by mutableStateOf<String?>(null)
        private set

    fun exportDocument(context: Context, s: DocSession, uri: Uri) {
        if (exporting != null) return
        val snap = Exporter.snapshotOf(s) ?: return
        val total = snap.pages.size
        exporting = L("جارٍ حفظ PDF…", "Saving PDF…")
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
                        }, uri) { i -> exporting = L("جارٍ حفظ PDF: صفحة ${i + 1} من $total", "Saving PDF: page ${i + 1} of $total") }
                        true
                    } catch (t: Throwable) {
                        // احتياط: صفحات كصور (الطريقة السابقة)
                        Exporter.exportAnnotated(context.applicationContext, snap, uri) { i ->
                            exporting = L("جارٍ حفظ PDF كصور: صفحة ${i + 1} من $total", "Saving PDF as images: page ${i + 1} of $total")
                        }
                        false
                    }
                }
                messages.tryEmit(
                    if (selectable) L("تم حفظ المستند مع التعليقات ($total صفحة)، والنص قابل للتحديد", "Document saved with annotations ($total pages), text is selectable")
                    else L("تم حفظ المستند مع التعليقات ($total صفحة) كصور", "Document saved with annotations ($total pages) as images")
                )
            } catch (t: Throwable) {
                messages.tryEmit(L("تعذر حفظ ملف PDF", "Could not save the PDF"))
            } finally {
                exporting = null
            }
        }
    }

    fun exportSummary(context: Context, s: DocSession, uri: Uri) {
        if (exporting != null) return
        // MyNotes Pro: يُحفظ من التبويب المفتوح؛ «✦ ذكاء» يحفظ الملخص المرتّب بالذكاء الاصطناعي
        val aiText = if (s.summaryAiMode) AiOrganizer.result(s.id) else null
        if (s.summaryAiMode && aiText == null) {
            messages.tryEmit(L("لا يوجد ملخص مرتّب بالذكاء الاصطناعي لهذا المستند بعد", "No AI-organized summary for this document yet"))
            return
        }
        val blocks = if (aiText != null) AiOrganizer.toBlocks(aiText)
            else Summary.display(s.allMarks(), summaryTidy, s.summaryEdits, null, summaryByColor) { labelOf(it) }
        val title = s.title
        exporting = L("جارٍ حفظ الملخصات…", "Saving summaries…")
        scope.launch {
            try {
                val pages = withContext(Dispatchers.IO) {
                    Exporter.exportSummary(context.applicationContext, title, blocks, uri)
                }
                messages.tryEmit(L("تم حفظ الملخصات ($pages صفحة)", "Summaries saved ($pages pages)"))
            } catch (t: Throwable) {
                messages.tryEmit(L("تعذر حفظ الملخصات", "Could not save the summaries"))
            } finally {
                exporting = null
            }
        }
    }

    // ---------- الصفحات والملاحظات ----------

    fun createNote(name: String, choice: PaperChoice) {
        scope.launch {
            try {
                val (pw, ph) = choice.points()
                val e = withContext(Dispatchers.IO) { repo.createNote(name, choice.kind, choice.color, pw, ph) }
                library.add(0, e)
                saveLibrary()
                openDocument(e.id)
            } catch (t: Throwable) {
                messages.tryEmit(L("تعذر إنشاء الملاحظة", "Could not create the note"))
            }
        }
    }

    fun onPagesChanged(id: String, count: Int) {
        updateEntry(id) { it.copy(pageCount = count) }
        saveLibrary()
    }

    /** يضيف صفحة فارغة كاملة بعد الصفحة رقم after (من 0). */
    fun addBlankPage(s: DocSession, after: Int, template: String, color: Int, pageRatio: Float? = null) {
        val doc = s.pdf ?: return
        val base = after.coerceIn(0, maxOf(0, doc.count - 1))
        val ratio = pageRatio ?: doc.ratio(base)
        s.insertPages(after, listOf(PageRef(Templates.src(template, color), 0, ratio)))
        scope.launch { s.listState.scrollToItem((after + 1).coerceAtLeast(0)) }
        messages.tryEmit(L("أُضيفت صفحة جديدة (${doc.count} صفحة الآن)", "New page added (${doc.count} pages now)"))
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
                messages.tryEmit(L("أُضيفت ${refs.size} صفحة من الملف (${doc.count} صفحة الآن)", "Added ${refs.size} pages from the file (${doc.count} pages now)"))
            } catch (t: Throwable) {
                messages.tryEmit(L("تعذر إضافة الملف. تأكد أنه PDF سليم.", "Could not add the file. Make sure it is a valid PDF."))
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
                failed == 0 && ok == 1 -> L("تم استيراد المستند، وتبدأ قراءة نصه تلقائيًا", "Document imported; its text is read automatically")
                failed == 0 -> L("تم استيراد $ok مستندات، وتبدأ قراءة نصوصها تلقائيًا", "Imported $ok documents; their text is read automatically")
                ok == 0 -> L("تعذر الاستيراد. تأكد أن الملف PDF سليم.", "Import failed. Make sure the file is a valid PDF.")
                else -> L("تم استيراد $ok وتعذر استيراد $failed", "Imported $ok, failed to import $failed")
            }
            messages.tryEmit(msg)
            added.forEach { queueOcr(it) }
        }
    }

    /**
     * يفتح ملف PDF جاء من تطبيق آخر. إن كان في المكتبة نفس الملف (نفس الاسم والحجم) يُفتح هو،
     * وإلا يُستورد ثم يُفتح، فتبقى كتابتك محفوظة عليه في المكتبة.
     */
    /** عرض باوربوينت ينتظر التحويل إلى PDF (تعرضه نافذة التحويل). */
    var pptJob by mutableStateOf<Presentations.Job?>(null)

    fun openExternal(uri: Uri, mime: String? = null) {
        scope.launch {
            // ننتظر تحميل المكتبة حتى لا يتكرر الملف
            while (!libraryLoaded) kotlinx.coroutines.delay(50)
            val info = withContext(Dispatchers.IO) {
                try {
                    appContext.contentResolver.query(
                        uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE),
                        null, null, null
                    )?.use { cur ->
                        if (cur.moveToFirst()) {
                            val n = cur.getString(0)
                            val sz = if (cur.isNull(1)) -1L else cur.getLong(1)
                            n to sz
                        } else null
                    }
                } catch (t: Throwable) {
                    null
                }
            }
            val baseName = info?.first?.substringBeforeLast('.', info.first)
            val size = info?.second ?: -1L
            val realMime = mime ?: try {
                appContext.contentResolver.getType(uri)
            } catch (t: Throwable) {
                null
            }
            // باوربوينت: نسخة مؤقتة تُرسل إلى أداة التحويل
            if (Presentations.isPresentation(realMime, info?.first)) {
                val name = info?.first ?: "presentation.pptx"
                try {
                    val f = withContext(Dispatchers.IO) {
                        val dir = Presentations.dir(appContext)
                        dir.listFiles()?.forEach { it.delete() }
                        val out = File(dir, name.replace(Regex("[\\/:*?\"<>|]"), "-"))
                        appContext.contentResolver.openInputStream(uri)!!.use { ins -> out.outputStream().use { ins.copyTo(it) } }
                        out
                    }
                    pptJob = Presentations.Job(name, f, Presentations.mimeFor(name))
                } catch (t: Throwable) {
                    messages.tryEmit(L("تعذر قراءة ملف العرض", "Could not read the presentation"))
                }
                return@launch
            }
            val existing = if (baseName != null && size > 0) {
                library.firstOrNull { !it.isNote && it.name == baseName && it.sizeBytes == size }
            } else null
            if (existing != null) {
                openDocument(existing.id)
                return@launch
            }
            importing = true
            try {
                val e = withContext(Dispatchers.IO) { repo.importFile(uri) }
                library.add(0, e)
                saveLibrary()
                openDocument(e.id)
                queueOcr(e)
            } catch (t: Throwable) {
                messages.tryEmit(L("تعذر فتح الملف. تأكد أنه PDF سليم.", "Could not open the file. Make sure it is a valid PDF."))
            } finally {
                importing = false
            }
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
        // المستند المفتوح قد يكون له حفظ منتظر: يُكتب أولًا حتى تشمله النسخة
        tabs.firstOrNull { it.id == entry.id }?.flushPersist()
        scope.launch {
            try {
                val copy = withContext(Dispatchers.IO) { repo.duplicate(entry, L(" (نسخة)", " (copy)")) }
                library.add(0, copy)
                saveLibrary()
                messages.tryEmit(L("تم إنشاء نسخة من المستند", "Document duplicated"))
            } catch (t: Throwable) {
                messages.tryEmit(L("تعذر نسخ المستند", "Could not duplicate the document"))
            }
        }
    }

    fun delete(entry: DocEntry) {
        tabs.firstOrNull { it.id == entry.id }?.cancelPendingPersist()
        closeTab(entry.id)
        library.removeAll { it.id == entry.id }
        saveLibrary()
        scope.launch { withContext(Dispatchers.IO) { repo.delete(entry) } }
        messages.tryEmit(L("تم حذف المستند", "Document deleted"))
    }
}
