import SwiftUI
import PDFKit
import PencilKit
import UserNotifications
import LocalAuthentication
import Combine

/// أنواع الرسائل المنبثقة أسفل الشاشة.
enum MsgKind { case tip, done, error }

/// حالة التطبيق كله: المكتبة، التبويبات المفتوحة، الأدوات، والإعدادات.
final class AppController: ObservableObject {
    static let shared = AppController()

    let repo = Repository.shared
    private let prefs = UserDefaults.standard

    // ---------- المكتبة ----------
    @Published var library: [DocEntry] = []
    @Published var libraryLoaded = false
    @Published var importing = false
    @Published var libraryFilter: LibraryFilter = .recent
    @Published var libraryFolder: String? = nil
    @Published var folders: [String] = []
    @Published var librarySort: SortMode = .lastOpened { didSet { prefs.set(librarySort.rawValue, forKey: "lib_sort") } }
    @Published var libraryQuery = ""

    // ---------- التبويبات ----------
    @Published var tabs: [DocSession] = []
    @Published var activeId: String? = nil
    @Published var splitId: String? = nil
    @Published var focusMode = false
    @Published var linkBack: (String, Int)? = nil

    // ---------- نوافذ عامة ----------
    @Published var settingsOpen = false
    @Published var toast: String? = nil
    /// زر في الرسالة المنبثقة (مثل «↶ تراجع» بعد نقل صفحة).
    @Published var toastAction: (String, () -> Void)? = nil
    @Published var openDeck: String? = nil   // "*" = كل البطاقات
    @Published var statsOpen = false
    private var toastWork: DispatchWorkItem?

    // ---------- الحساب ----------
    @Published var accountEmail: String? = nil
    @Published var accountName: String? = nil
    @Published var loginSeen = true

    // ---------- الأدوات ----------
    @Published private(set) var tool: Tool = .fountain
    @Published private(set) var lastPen: Tool = .fountain
    @Published private(set) var lastWriting: Tool = .fountain
    @Published private var colorOf: [String: UInt32] = [:]
    @Published private var sizeOf: [String: Int] = [:]
    @Published var customColors: [UInt32] = []
    @Published var lassoActive = false
    @Published var shapeActive = false
    @Published var textActive = false
    // الرسائل المنبثقة حسب النوع
    @Published var tipsOn = true { didSet { prefs.set(tipsOn, forKey: "msg_tips") } }
    @Published var doneOn = true { didSet { prefs.set(doneOn, forKey: "msg_done") } }
    @Published var errorsOn = true { didSet { prefs.set(errorsOn, forKey: "msg_err") } }
    @Published var stickyActive = false
    @Published var shapeKind: ShapeKind = .line
    @Published var stickyColor: UInt32 = 0xFFFFF59D
    /// نمط الملاحظة: 0 سادة، 1 شريط، 2 أسطر، 3 مربعات.
    @Published var stickyStyle = 0 { didSet { prefs.set(stickyStyle, forKey: "sticky_style") } }
    /// الاسم على الملاحظات (اختياري).
    @Published var noteAuthor = "" { didSet { prefs.set(noteAuthor, forKey: "note_author") } }
    /// ملاحظة منسوخة أو مقصوصة تنتظر اللصق.
    @Published var noteClipboard: StickyNote? = nil
    @Published var penBarOpen = false
    @Published var fingerWrites = false { didSet { prefs.set(fingerWrites, forKey: "finger_writes") } }
    @Published var toolbarStyle: ToolbarStyle = .penBar { didSet { prefs.set(toolbarStyle.rawValue, forKey: "toolbar_style") } }
    @Published var hiddenTools: [String] = [] { didSet { prefs.set(hiddenTools, forKey: "hidden_tools") } }
    @Published var laserMode: LaserMode = .dot { didSet { prefs.set(laserMode.rawValue, forKey: "laser_mode") } }
    @Published var eraserMode: EraserMode = .stroke { didSet { prefs.set(eraserMode.rawValue, forKey: "eraser_mode") } }
    @Published var presets: [PenPreset] = [] { didSet { saveCodable(presets, "pen_presets") } }
    // الأشكال: النمط والسماكة واللون والزوايا ورؤوس الأسهم، و«ارسم وأبقِ»
    @Published var shapeStyle: LineStyle = .solid { didSet { prefs.set(shapeStyle.rawValue, forKey: "shape_style") } }
    @Published var shapeSize = 2 { didSet { prefs.set(shapeSize, forKey: "shape_size") } }
    @Published var shapeRounded = true { didSet { prefs.set(shapeRounded, forKey: "shape_round") } }
    /// ملء الأشكال المغلقة بلونها، وشفافية الملء.
    @Published var shapeFill = false { didSet { prefs.set(shapeFill, forKey: "shape_fill") } }
    @Published var shapeFillAlpha: Double = 0.2 { didSet { prefs.set(shapeFillAlpha, forKey: "shape_fill_a") } }
    @Published var arrowStart: ArrowHead = .none { didSet { prefs.set(arrowStart.rawValue, forKey: "arrow_s") } }
    @Published var arrowEnd: ArrowHead = .none { didSet { prefs.set(arrowEnd.rawValue, forKey: "arrow_e") } }
    @Published var shapeColor: UInt32 = 0xFF000000 { didSet { prefs.set(Int(shapeColor), forKey: "shape_color") } }
    @Published var drawAndHold = true { didSet { prefs.set(drawAndHold, forKey: "draw_hold") } }
    /// نمط خط الأقلام (متصل، متقطع، منقّط).
    @Published var penStyle: LineStyle = .solid { didSet { prefs.set(penStyle.rawValue, forKey: "pen_style") } }
    // إعدادات القلم المتقدمة (كما في أندرويد): حدة الطرف، حساسية الضغط، تثبيت الخط
    @Published var penSharpness: Double = 0.25 { didSet { prefs.set(penSharpness, forKey: "pen_sharp") } }
    @Published var penPressure: Double = 0.5 { didSet { prefs.set(penPressure, forKey: "pen_pressure") } }
    @Published var penStabilize: Double = 0 { didSet { prefs.set(penStabilize, forKey: "pen_stab") } }
    // الهايلايتر: تثبيت الخط والرسم بخط مستقيم
    @Published var hlStabilize: Double = 0.3 { didSet { prefs.set(hlStabilize, forKey: "hl_stab") } }
    @Published var hlStraight = true { didSet { prefs.set(hlStraight, forKey: "hl_straight") } }
    // فلتر الممحاة: ما تمسحه الممحاة، والرجوع إلى القلم بعد المسح
    @Published var erasePen = true { didSet { prefs.set(erasePen, forKey: "er_pen") } }
    @Published var erasePencil = true { didSet { prefs.set(erasePencil, forKey: "er_pencil") } }
    @Published var eraseHighlighter = true { didSet { prefs.set(eraseHighlighter, forKey: "er_hl") } }
    @Published var eraseShapes = true { didSet { prefs.set(eraseShapes, forKey: "er_shapes") } }
    @Published var autoDeselect = false { didSet { prefs.set(autoDeselect, forKey: "er_auto") } }
    /// لغة قراءة خط اليد: 0 عربي، 1 إنجليزي، 2 كلاهما.
    @Published var hwLang = 2 { didSet { prefs.set(hwLang, forKey: "hw_lang"); if started { InkReader.prefetch(hwLang) } } }
    /// انتهى تحميل الإعدادات (لا تنزيل لحزم خط اليد أثناء التحميل).
    private var started = false
    // التحديد الحر: نوعه (حر/مستطيل) وما يدخل فيه
    @Published var lassoRect = false { didSet { prefs.set(lassoRect, forKey: "lasso_rect") } }
    @Published var lassoInk = true { didSet { prefs.set(lassoInk, forKey: "lasso_ink") } }
    @Published var lassoHighlight = true { didSet { prefs.set(lassoHighlight, forKey: "lasso_hl") } }
    @Published var lassoShapes = true { didSet { prefs.set(lassoShapes, forKey: "lasso_shapes") } }
    @Published var lassoImages = true { didSet { prefs.set(lassoImages, forKey: "lasso_images") } }
    @Published var lassoTexts = true { didSet { prefs.set(lassoTexts, forKey: "lasso_texts") } }
    @Published var lassoStickies = true { didSet { prefs.set(lassoStickies, forKey: "lasso_stickies") } }

    let penPalette: [UInt32] = [0xFF000000, 0xFF455A64, 0xFF1565C0, 0xFFC62828, 0xFF2E7D32, 0xFFEF6C00, 0xFF6A1B9A, 0xFF795548]
    let highlightPalette: [UInt32] = [0xFFFFEB3B, 0xFF69F0AE, 0xFFFF80AB, 0xFF40C4FF, 0xFFFFAB40]
    let stickyPalette: [UInt32] = [0xFFFFF59D, 0xFFFFCC80, 0xFFF8BBD0, 0xFFB3E5FC, 0xFFC8E6C9, 0xFFE1BEE7]

    // ---------- الهايلايتر والملخصات ----------
    @Published var highlightToSummary = true {
        didSet {
            prefs.set(highlightToSummary, forKey: "hl_summary")
            if started, oldValue != highlightToSummary {
                say(.tip, highlightToSummary ? L("التمييز الجديد يُضاف إلى الملخصات", "New highlights are added to summaries")
                                             : L("التمييز الجديد يبقى على الصفحة فقط ولا يُضاف إلى الملخصات", "New highlights stay on the page only and are not added to summaries"))
            }
        }
    }
    @Published var highlightSnap = false {
        didSet {
            prefs.set(highlightSnap, forKey: "hl_snap")
            if started, oldValue != highlightSnap {
                say(.tip, highlightSnap ? L("التقاط النص مفعّل: مرّر الهايلايتر على السطر ليُظلَّل ويُضاف إلى الملخصات", "Text snap on: run the highlighter over a line to mark it and add it to summaries")
                                        : L("التقاط النص متوقف: الهايلايتر يرسم بحرية", "Text snap off: the highlighter draws freely"))
            }
        }
    }
    @Published var summaryTidy = true { didSet { prefs.set(summaryTidy, forKey: "sum_tidy") } }
    @Published var summariesOn = true { didSet { prefs.set(summariesOn, forKey: "summaries_on") } }
    @Published var summaryByColor = false { didSet { prefs.set(summaryByColor, forKey: "sum_by_color") } }
    @Published var colorLabels: [String: String] = [:] { didSet { prefs.set(colorLabels, forKey: "color_labels") } }

    // ---------- المظهر واللغة ----------
    @Published var theme: AppTheme = .navy
    @Published var english = false
    @Published var toolbarScale: CGFloat = 1 { didSet { prefs.set(Double(toolbarScale), forKey: "toolbar_scale") } }
    @Published var textScale: CGFloat = 1 { didSet { prefs.set(Double(textScale), forKey: "text_scale"); Fonts.scale = textScale } }
    @Published var nightPages = false { didSet { prefs.set(nightPages, forKey: "night_pages") } }

    // ---------- المستندات ----------
    @Published var resumeLast = true { didSet { prefs.set(resumeLast, forKey: "resume_last") } }
    @Published var openAsTabs = true { didSet { prefs.set(openAsTabs, forKey: "open_as_tabs") } }
    /// تصدير مستندات بالاسم نفسه: 0 اسأل كل مرة، 1 أبقِ الجميع بترقيم الاسم، 2 صدّر الأول فقط.
    @Published var exportSameName = 0 { didSet { prefs.set(exportSameName, forKey: "export_same_name") } }
    @Published var penDoubleTapEnabled = true { didSet { prefs.set(penDoubleTapEnabled, forKey: "pen_dbl") } }
    @Published var fingerUndo = true { didSet { prefs.set(fingerUndo, forKey: "finger_undo") } }
    /// نقرة على كلمة محوّلة تعرض قراءات أخرى لتصحيحها.
    @Published var wordAlts = true { didSet { prefs.set(wordAlts, forKey: "word_alts") } }

    var exportSameNameLabel: String {
        switch exportSameName {
        case 1: return L("ترقيم الأسماء", "Number the names")
        case 2: return L("الأول فقط", "First only")
        default: return L("اسأل كل مرة", "Ask every time")
        }
    }

    // ---------- المذاكرة ----------
    @Published var stats: [String: Int64] = [:]
    @Published var goalMinutes = 60 { didSet { prefs.set(goalMinutes, forKey: "goal_min") } }
    @Published var examDates: [String: Int] = [:] { didSet { prefs.set(examDates, forKey: "exam_dates") } }
    @Published var reminderOn = false
    @Published var reminderMinute = 20 * 60
    /// 0 متوقف، 1 دراسة، 2 راحة.
    @Published private(set) var pomoPhase = 0
    @Published private(set) var pomoLeftMs: Int64 = 0
    @Published private(set) var pomoPaused = false
    @Published private(set) var studyMin = 25
    @Published private(set) var breakMin = 5
    private var pomoEndsAt: Int64 = 0
    private var pomoTimer: Timer?

    // ---------- البطاقات ----------
    @Published var cards: [Card] = [] { didSet { repo.saveJSON("cards.json", cards) } }

    // ---------- القفل ----------
    @Published var unlocked: Set<String> = []
    @Published var unlockRequest: String? = nil
    @Published var setPinFor: String? = nil

    private var lastInteraction: Int64 = 0
    private var readTimer: Timer?
    private var visible = true

    private init() {}

    // ======================================================== البداية

    func initialize() {
        let p = prefs
        theme = AppTheme(rawValue: p.string(forKey: "theme") ?? "") ?? .navy
        ThemeState.theme = theme
        english = p.bool(forKey: "english")
        I18n.english = english
        toolbarScale = CGFloat(p.object(forKey: "toolbar_scale") as? Double ?? 1)
        textScale = CGFloat(p.object(forKey: "text_scale") as? Double ?? 1)
        Fonts.scale = textScale
        summariesOn = p.object(forKey: "summaries_on") as? Bool ?? true
        highlightToSummary = p.object(forKey: "hl_summary") as? Bool ?? true
        highlightSnap = p.bool(forKey: "hl_snap")
        lassoRect = p.bool(forKey: "lasso_rect")
        shapeStyle = LineStyle(rawValue: p.integer(forKey: "shape_style")) ?? .solid
        shapeSize = p.object(forKey: "shape_size") as? Int ?? 2
        shapeRounded = p.object(forKey: "shape_round") as? Bool ?? true
        shapeFill = p.bool(forKey: "shape_fill")
        shapeFillAlpha = min(1, max(0.1, p.object(forKey: "shape_fill_a") as? Double ?? 0.2))
        arrowStart = ArrowHead(rawValue: p.string(forKey: "arrow_s") ?? "") ?? .none
        arrowEnd = ArrowHead(rawValue: p.string(forKey: "arrow_e") ?? "") ?? .none
        shapeColor = UInt32(truncatingIfNeeded: p.object(forKey: "shape_color") as? Int ?? 0xFF000000)
        drawAndHold = p.object(forKey: "draw_hold") as? Bool ?? true
        stickyStyle = p.integer(forKey: "sticky_style")
        noteAuthor = p.string(forKey: "note_author") ?? ""
        tipsOn = p.object(forKey: "msg_tips") as? Bool ?? true
        doneOn = p.object(forKey: "msg_done") as? Bool ?? true
        errorsOn = p.object(forKey: "msg_err") as? Bool ?? true
        penStyle = LineStyle(rawValue: p.integer(forKey: "pen_style")) ?? .solid
        hwLang = p.object(forKey: "hw_lang") as? Int ?? 2
        penSharpness = p.object(forKey: "pen_sharp") as? Double ?? 0.25
        penPressure = p.object(forKey: "pen_pressure") as? Double ?? 0.5
        penStabilize = p.object(forKey: "pen_stab") as? Double ?? 0
        hlStabilize = p.object(forKey: "hl_stab") as? Double ?? 0.3
        hlStraight = p.object(forKey: "hl_straight") as? Bool ?? true
        erasePen = p.object(forKey: "er_pen") as? Bool ?? true
        erasePencil = p.object(forKey: "er_pencil") as? Bool ?? true
        eraseHighlighter = p.object(forKey: "er_hl") as? Bool ?? true
        eraseShapes = p.object(forKey: "er_shapes") as? Bool ?? true
        autoDeselect = p.object(forKey: "er_auto") as? Bool ?? false
        defer { started = true }
        lassoInk = p.object(forKey: "lasso_ink") as? Bool ?? true
        lassoHighlight = p.object(forKey: "lasso_hl") as? Bool ?? true
        lassoShapes = p.object(forKey: "lasso_shapes") as? Bool ?? true
        lassoImages = p.object(forKey: "lasso_images") as? Bool ?? true
        lassoTexts = p.object(forKey: "lasso_texts") as? Bool ?? true
        lassoStickies = p.object(forKey: "lasso_stickies") as? Bool ?? true
        summaryTidy = p.object(forKey: "sum_tidy") as? Bool ?? true
        summaryByColor = p.bool(forKey: "sum_by_color")
        colorLabels = p.dictionary(forKey: "color_labels") as? [String: String] ?? [:]
        resumeLast = p.object(forKey: "resume_last") as? Bool ?? true
        openAsTabs = p.object(forKey: "open_as_tabs") as? Bool ?? true
        exportSameName = min(2, max(0, p.integer(forKey: "export_same_name")))
        penDoubleTapEnabled = p.object(forKey: "pen_dbl") as? Bool ?? true
        fingerUndo = p.object(forKey: "finger_undo") as? Bool ?? true
        wordAlts = p.object(forKey: "word_alts") as? Bool ?? true
        fingerWrites = p.bool(forKey: "finger_writes")
        nightPages = p.bool(forKey: "night_pages")
        toolbarStyle = ToolbarStyle(rawValue: p.string(forKey: "toolbar_style") ?? "") ?? .penBar
        hiddenTools = p.stringArray(forKey: "hidden_tools") ?? Self.defaultHiddenTools
        laserMode = LaserMode(rawValue: p.string(forKey: "laser_mode") ?? "") ?? .dot
        eraserMode = EraserMode(rawValue: p.string(forKey: "eraser_mode") ?? "") ?? .stroke
        librarySort = SortMode(rawValue: p.string(forKey: "lib_sort") ?? "") ?? .lastOpened
        folders = p.stringArray(forKey: "folders") ?? []
        examDates = p.dictionary(forKey: "exam_dates") as? [String: Int] ?? [:]
        goalMinutes = p.object(forKey: "goal_min") as? Int ?? 60
        studyMin = p.object(forKey: "pomo_study") as? Int ?? 25
        breakMin = p.object(forKey: "pomo_break") as? Int ?? 5
        reminderOn = p.bool(forKey: "reminder_on")
        reminderMinute = p.object(forKey: "reminder_min") as? Int ?? 20 * 60
        colorOf = p.dictionary(forKey: "tool_colors") as? [String: UInt32] ?? [:]
        sizeOf = p.dictionary(forKey: "tool_sizes") as? [String: Int] ?? [:]
        customColors = (p.array(forKey: "custom_colors") as? [UInt32]) ?? []
        if let t = Tool(rawValue: p.string(forKey: "tool") ?? "") { tool = t.isWriting ? t : .fountain }
        if let t = Tool(rawValue: p.string(forKey: "last_pen") ?? ""), t.isPen { lastPen = t }
        lastWriting = tool.isWriting ? tool : lastPen
        presets = loadCodable("pen_presets") ?? []
        accountEmail = p.string(forKey: "account_email")
        accountName = p.string(forKey: "account_name")
        loginSeen = p.bool(forKey: "login_seen")
        stats = (p.dictionary(forKey: "stats") as? [String: Int64]) ?? [:]
        cards = repo.loadList("cards.json", Card.self)
        pruneStats()

        library = repo.loadLibrary()
        libraryLoaded = true
        if repo.libraryRepaired {
            // الفهرس كان تالفًا أو ناقصًا وأُصلح: يُحفظ الآن كي لا يتكرر الإصلاح في كل تشغيل
            saveLibrary()
            DispatchQueue.main.async {
                self.say(.error, L("كان فهرس المكتبة تالفًا، فاستُعيدت مستنداتك من الجهاز", "The library index was damaged, so your documents were recovered from the device"))
            }
        }
        // المستندات التي كانت مفتوحة
        if resumeLast, let open = p.stringArray(forKey: "open_tabs") {
            for id in open where library.contains(where: { $0.id == id && !$0.locked }) { openDocument(id, activate: false) }
            if let a = p.string(forKey: "active_tab"), tabs.contains(where: { $0.id == a }) { activeId = a }
        }
        startReadTimer()
        Repository.io.async { Repository.cleanTemp() }
    }

    private func saveCodable<T: Encodable>(_ v: T, _ key: String) {
        if let d = try? JSONEncoder().encode(v) { prefs.set(d, forKey: key) }
    }

    private func loadCodable<T: Decodable>(_ key: String) -> T? {
        guard let d = prefs.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(T.self, from: d)
    }

    /// رسالة منبثقة حسب نوعها (لكل نوع مفتاح في الإعدادات، كما في أندرويد 1.0.271).
    func say(_ kind: MsgKind, _ s: String) {
        switch kind {
        case .tip: if !tipsOn { return }
        case .done: if !doneOn { return }
        case .error: if !errorsOn { return }
        }
        message(s)
    }

    /// رسالة بزر (تبقى أطول قليلًا).
    func message(_ s: String, action: String, _ run: @escaping () -> Void) {
        message(s)
        toastAction = (action, run)
    }

    func message(_ s: String) {
        toastAction = nil
        toastWork?.cancel()
        withAnimation(Motion.enter) { toast = s }
        let w = DispatchWorkItem { [weak self] in withAnimation(Motion.exit) { self?.toast = nil; self?.toastAction = nil } }
        toastWork = w
        DispatchQueue.main.asyncAfter(deadline: .now() + 3.2, execute: w)
    }

    // ======================================================== الحساب والمظهر

    func setAccount(email: String?, name: String?) {
        accountEmail = email
        accountName = name
        prefs.set(email, forKey: "account_email")
        prefs.set(name, forKey: "account_name")
        loginSeen = true
        prefs.set(true, forKey: "login_seen")
    }

    func signOutAccount() {
        accountEmail = nil
        accountName = nil
        prefs.removeObject(forKey: "account_email")
        prefs.removeObject(forKey: "account_name")
    }

    /// حذف الحساب: بيانات الحساب وربط Apple في هذا التطبيق (الملفات تبقى على الجهاز).
    func deleteAccount() {
        signOutAccount()
        prefs.removeObject(forKey: "apple_user")
        prefs.removeObject(forKey: "account_apple_id")
    }

    func skipLogin() {
        loginSeen = true
        prefs.set(true, forKey: "login_seen")
    }

    func setTheme(_ t: AppTheme) {
        ThemeState.theme = t
        withAnimation(Motion.enter) { theme = t }
        prefs.set(t.rawValue, forKey: "theme")
    }

    func setEnglish(_ en: Bool) {
        I18n.english = en
        english = en
        prefs.set(en, forKey: "english")
        objectWillChange.send()
    }

    // ======================================================== المواد

    @discardableResult
    func addFolder(_ name: String) -> String? {
        let n = name.trimmingCharacters(in: .whitespaces)
        guard !n.isEmpty else { return nil }
        if !folders.contains(n) { folders.append(n); prefs.set(folders, forKey: "folders") }
        return n
    }

    func renameFolder(_ old: String, _ new: String) {
        let n = new.trimmingCharacters(in: .whitespaces)
        guard !n.isEmpty, n != old, let i = folders.firstIndex(of: old) else { return }
        // الاسم الجديد موجود: تُدمج المادتان في واحدة بدل ظهور مادتين بالاسم نفسه
        let merging = folders.contains(n)
        if merging { folders.remove(at: i) } else { folders[i] = n }
        prefs.set(folders, forKey: "folders")
        for k in library.indices where library[k].folder == old { library[k].folder = n }
        if let d = examDates.removeValue(forKey: old), examDates[n] == nil { examDates[n] = d }
        if libraryFolder == old { libraryFolder = n }
        for k in cards.indices where cards[k].folder == old { cards[k].folder = n }
        saveLibrary()
    }

    /// عدد البطاقات المسجّلة على المادة نفسها (لا بطاقات مستنداتها).
    func folderCardCount(_ name: String) -> Int { cards.filter { $0.folder == name }.count }

    /// deleteCards: تُحذف بطاقات المادة معها؛ وإلا تبقى البطاقات بلا مادة.
    func deleteFolder(_ name: String, deleteCards: Bool = false) {
        if cards.contains(where: { $0.folder == name }) {
            if deleteCards {
                cards.removeAll { $0.folder == name }
            } else {
                var list = cards
                for k in list.indices where list[k].folder == name { list[k].folder = "" }
                cards = list
            }
        }
        folders.removeAll { $0 == name }
        prefs.set(folders, forKey: "folders")
        for k in library.indices where library[k].folder == name { library[k].folder = "" }
        examDates.removeValue(forKey: name)
        if libraryFolder == name { libraryFolder = nil }
        saveLibrary()
    }

    func moveToFolder(_ id: String, _ folder: String) {
        if !folder.isEmpty { addFolder(folder) }
        update(id) { $0.folder = folder }
        say(.done, folder.isEmpty ? L("أُزيل المستند من المادة", "Document removed from the subject")
                                  : L("نُقل المستند إلى «\(folder)»", "Document moved to “\(folder)”"))
    }

    // ======================================================== المستندات

    func saveLibrary() { repo.saveLibrary(library) }

    func entry(_ id: String) -> DocEntry? { library.first { $0.id == id } }

    func update(_ id: String, _ f: (inout DocEntry) -> Void) {
        guard let i = library.firstIndex(where: { $0.id == id }) else { return }
        f(&library[i])
        saveLibrary()
    }

    func toggleFavorite(_ id: String) { update(id) { $0.favorite.toggle() } }

    func rename(_ id: String, _ name: String) {
        let n = name.trimmingCharacters(in: .whitespaces)
        guard !n.isEmpty else { return }
        update(id) { $0.name = n }
        tabs.first { $0.id == id }?.title = n
    }

    func setCover(_ id: String, color: UInt32, sticker: String) {
        update(id) { $0.coverColor = color; $0.sticker = sticker }
    }

    func duplicate(_ e: DocEntry) {
        // المستند المقفل: نسخه يحتاج الرمز أو البصمة، والنسخة تبقى مقفلة
        if e.locked && !unlocked.contains(e.id) {
            authenticate(reason: L("إنشاء نسخة من المستند المقفل", "Duplicate the locked document")) { ok in
                guard ok else { return }
                self.unlocked.insert(e.id)
                self.duplicate(e)
            }
            return
        }
        guard Premium.shared.canAdd() else { return }
        tabs.first { $0.id == e.id }?.flush()
        if let n = repo.duplicate(e, newName: e.name + L(" (نسخة)", " (copy)")) {
            library.append(n)
            saveLibrary()
            say(.done, L("تم تكرار المستند", "Document duplicated"))
        } else {
            say(.error, L("تعذر تكرار المستند", "Could not duplicate the document"))
        }
    }

    func delete(_ e: DocEntry) {
        if AudioNotes.shared.recordingDoc == e.id { AudioNotes.shared.stop() }
        pendingRead.removeValue(forKey: e.id)
        // تفاصيل وقت هذا المستند في الأيام (مجموع كل يوم يبقى)
        let suffix = "|" + e.id
        if stats.keys.contains(where: { $0.hasSuffix(suffix) }) {
            stats = stats.filter { !$0.key.hasSuffix(suffix) }
            prefs.set(stats, forKey: "stats")
        }
        closeTab(e.id)
        repo.delete(e.id)
        library.removeAll { $0.id == e.id }
        cards.removeAll { $0.docId == e.id }
        saveLibrary()
        say(.done, L("تم حذف المستند", "Document deleted"))
    }

    func importDocuments(_ urls: [URL]) {
        // عروض الباوربوينت ومستندات Word وKeynote: تُحوَّل إلى PDF أولًا
        let office = urls.filter { OfficeConverter.handles($0) }
        if !office.isEmpty {
            let rest = urls.filter { !OfficeConverter.handles($0) }
            importing = true
            message(L("جارٍ تحويل الملف إلى PDF…", "Converting the file to PDF…"))
            var converted: [URL] = []
            var left = office.count
            for u in office {
                OfficeConverter.convert(u) { out in
                    if let o = out { converted.append(o) } else {
                        self.say(.error, L("تعذر تحويل «\(u.lastPathComponent)». صدّره إلى PDF ثم افتحه", "Couldn’t convert “\(u.lastPathComponent)”. Export it to PDF, then open it"))
                    }
                    left -= 1
                    if left == 0 {
                        self.importing = false
                        if !converted.isEmpty || !rest.isEmpty { self.importDocuments(rest + converted) }
                    }
                }
            }
            return
        }
        let pdfs = urls.filter { $0.pathExtension.lowercased() == "pdf" }
        guard !pdfs.isEmpty else {
            if !urls.isEmpty { message(L("هذا النوع من الملفات غير مدعوم بعد. افتح ملف PDF", "This file type isn’t supported yet. Open a PDF")) }
            return
        }
        guard Premium.shared.canAdd(pdfs.count) else { return }
        importing = true
        let folder = libraryFolder ?? ""
        DispatchQueue.global(qos: .userInitiated).async {
            var added: [DocEntry] = []
            for u in pdfs {
                let scoped = u.startAccessingSecurityScopedResource()
                defer { if scoped { u.stopAccessingSecurityScopedResource() } }
                if let e = self.repo.importPDF(from: u, folder: folder) { added.append(e) }
            }
            DispatchQueue.main.async {
                self.importing = false
                self.library.append(contentsOf: added)
                self.saveLibrary()
                let failed = pdfs.count - added.count
                let auto = Premium.shared.active
                if added.count == 1 && failed == 0 {
                    self.openDocument(added[0].id)
                    self.say(.done, auto ? L("تم استيراد المستند، ويُقرأ نصه تلقائيًا", "Document imported; its text is read automatically")
                                         : L("تم استيراد المستند", "Document imported"))
                } else if added.isEmpty {
                    self.say(.error, L("فشل الاستيراد. تأكد أن الملف PDF صالح.", "Import failed. Make sure the file is a valid PDF."))
                } else if failed > 0 {
                    self.say(.error, L("استُورد \(added.count)، وفشل استيراد \(failed)", "Imported \(added.count), failed to import \(failed)"))
                } else {
                    self.say(.done, auto ? L("تم استيراد \(added.count) مستندات، ويُقرأ نصها تلقائيًا", "Imported \(added.count) documents; their text is read automatically")
                                         : L("تم استيراد \(added.count) مستندات", "Imported \(added.count) documents"))
                }
            }
        }
    }

    func importScan(_ images: [UIImage]) {
        guard !images.isEmpty, Premium.shared.canAdd() else { return }
        let f = DateFormatter()
        f.dateFormat = "d MMM HH:mm"
        f.locale = Locale(identifier: english ? "en" : "ar")
        if let e = repo.importImages(images, name: L("مسح ", "Scan ") + f.string(from: Date()), folder: libraryFolder ?? "") {
            library.append(e)
            saveLibrary()
            openDocument(e.id)
        } else {
            say(.error, L("تعذر حفظ المسح. تأكد من وجود مساحة كافية على الجهاز", "Couldn’t save the scan. Make sure the device has enough free space"))
        }
    }

    /// ملف فُتح من تطبيق آخر: يُضاف إلى المكتبة ويظهر مباشرة.
    func openExternal(_ url: URL) {
        importDocuments([url])
    }

    func createNote(_ name: String, _ choice: PaperChoice) {
        guard Premium.shared.canAdd() else { return }
        let n = name.trimmingCharacters(in: .whitespaces).isEmpty ? L("ملاحظة جديدة", "New note") : name
        if let e = repo.createNote(name: n, choice: choice, folder: libraryFolder ?? "") {
            library.append(e)
            saveLibrary()
            openDocument(e.id)
        } else {
            say(.error, L("تعذر إنشاء الملاحظة", "Could not create the note"))
        }
    }

    func createMap(_ name: String, folder: String) {
        guard Premium.shared.canAdd() else { return }
        let e = repo.createMap(name: name, folder: folder)
        if !folder.isEmpty { addFolder(folder) }
        library.append(e)
        saveLibrary()
        openDocument(e.id)
    }

    // ======================================================== التبويبات

    func openDocument(_ id: String, activate: Bool = true) {
        guard let e = entry(id) else { return }
        if e.locked && !unlocked.contains(id) {
            unlockRequest = id
            return
        }
        if tabs.first(where: { $0.id == id }) == nil {
            let s = DocSession(entry: e)
            if !openAsTabs, let cur = activeId, let i = tabs.firstIndex(where: { $0.id == cur }) {
                tabs[i].finishInlineEdit()
                tabs[i].flush()
                tabs[i] = s
            } else {
                tabs.append(s)
            }
        }
        update(id) { $0.lastOpened = nowMs() }
        if activate { self.activate(id) }
        saveSession()
    }

    func openPage(_ id: String, _ page: Int) {
        openDocument(id)
        tabs.first { $0.id == id }?.goTo(page: page)
    }

    func openAt(_ id: String, _ page: Int, query: String) {
        openPage(id, page)
        if let s = tabs.first(where: { $0.id == id }) {
            s.searchQuery = query
            s.searchOpen = true
            s.searchPreferPage = page
            s.runSearch()
        }
    }

    func followLink(from s: DocSession, docId: String, page: Int, pageId: String? = nil) {
        guard entry(docId) != nil else {
            say(.error, L("المستند المرتبط لم يعد موجودًا", "The linked document no longer exists"))
            return
        }
        linkBack = (s.id, s.currentPage)
        openDocument(docId)
        guard let t = tabs.first(where: { $0.id == docId }) else { return }
        // الصفحة نفسها إن عُرفت (حتى لو تغيّر رقمها)، وإلا الرقم المحفوظ
        t.goTo(page: pageId.flatMap { t.index(of: $0) } ?? page)
    }

    /// معرّف صفحة في مستند (مفتوح أو لا) برقمها.
    func pageId(of docId: String, index: Int) -> String? {
        if let t = tabs.first(where: { $0.id == docId }) { return t.pageId(index) }
        let pages = repo.loadData(docId).pages
        return pages.indices.contains(index) ? pages[index].id : nil
    }

    func openLast() {
        if let e = library.filter({ $0.lastOpened > 0 }).max(by: { $0.lastOpened < $1.lastOpened }) { openDocument(e.id) }
    }

    func activate(_ id: String?) {
        if let cur = activeId { tabs.first { $0.id == cur }?.flush() }
        withAnimation(Motion.enter) { activeId = id }
        if id == nil { focusMode = false }
        prefs.set(id, forKey: "active_tab")
    }

    func closeTab(_ id: String) {
        guard let i = tabs.firstIndex(where: { $0.id == id }) else { return }
        tabs[i].finishInlineEdit()
        tabs[i].flush()
        tabs.remove(at: i)
        if splitId == id { splitId = nil }
        if activeId == id {
            activate(tabs.isEmpty ? nil : tabs[max(0, i - 1)].id)
        }
        saveSession()
    }

    func moveTab(from: Int, to: Int) {
        guard tabs.indices.contains(from), tabs.indices.contains(to) else { return }
        let t = tabs.remove(at: from)
        tabs.insert(t, at: to)
        saveSession()
    }

    func openSplit(_ id: String) {
        openDocument(id, activate: false)
        withAnimation(Motion.enter) { splitId = id }
    }

    func closeSplit() { withAnimation(Motion.enter) { splitId = nil } }

    private func saveSession() {
        prefs.set(tabs.map { $0.id }, forKey: "open_tabs")
        prefs.set(activeId, forKey: "active_tab")
    }

    func flush() {
        commitReading()
        tabs.forEach { $0.flush() }
        saveLibrary()
        prefs.set(stats, forKey: "stats")
    }

    func onPagesChanged(_ id: String, count: Int) {
        update(id) { $0.pageCount = count; $0.sizeBytes = repo.fileSize(id) }
    }

    func setLastPage(_ id: String, _ page: Int) {
        guard let i = library.firstIndex(where: { $0.id == id }), library[i].lastPage != page else { return }
        library[i].lastPage = page
    }

    // ======================================================== الأدوات

    func selectTool(_ t: Tool) {
        tool = t
        if t.isPen { lastPen = t; prefs.set(t.rawValue, forKey: "last_pen") }
        if t.isWriting { lastWriting = t }
        if t == .eraser || t == .laser { shapeActive = false }
        lassoActive = false
        textActive = false
        stickyActive = false
        prefs.set(t.rawValue, forKey: "tool")
    }

    /// استعادة إعدادات القلم الافتراضية.
    func resetPen() {
        penStyle = .solid
        penSharpness = 0.25
        penPressure = 0.5
        penStabilize = 0
        setSize(2)
    }

    /// هل تمسح الممحاة هذا الخط؟ (فلتر الممحاة)
    func canErase(_ s: PKStroke) -> Bool {
        if s.randomSeed == shapeSeed { return eraseShapes }
        switch s.ink.inkType {
        case .marker: return eraseHighlighter
        case .pencil: return erasePencil
        default: return erasePen
        }
    }

    func selectToolWithHint(_ t: Tool) {
        let was = tool
        selectTool(t)
        if was == t { return }
        if t == .highlighter && highlightSnap {
            message(L("التقاط النص مفعّل: مرّر الهايلايتر على السطر", "Text snap on: run the highlighter over a line"))
        }
    }

    func colorFor(_ t: Tool) -> UInt32 {
        if let c = colorOf[t.rawValue] { return c }
        switch t {
        case .highlighter: return 0xFFFFEB3B
        case .laser: return 0xFFFF1744
        default: return 0xFF000000
        }
    }

    func sizeFor(_ t: Tool) -> Int {
        if let s = sizeOf[t.rawValue] { return s }
        switch t {
        case .highlighter: return 4
        case .eraser: return 3
        case .laser: return 4
        default: return 2
        }
    }

    func paletteFor(_ t: Tool) -> [UInt32] { t == .highlighter ? highlightPalette : penPalette }

    func setColor(_ c: UInt32) {
        colorOf[tool.rawValue] = c
        prefs.set(colorOf, forKey: "tool_colors")
    }

    func setSize(_ i: Int) {
        sizeOf[tool.rawValue] = max(0, min(Sizes.count - 1, i))
        prefs.set(sizeOf, forKey: "tool_sizes")
    }

    func addCustomColor(_ c: UInt32) {
        customColors.removeAll { $0 == c }
        customColors.insert(c, at: 0)
        if customColors.count > 8 { customColors.removeLast() }
        prefs.set(customColors, forKey: "custom_colors")
    }

    func addPreset() {
        presets.append(PenPreset(tool: tool, color: colorFor(tool), size: sizeFor(tool)))
    }

    func applyPreset(_ p: PenPreset) {
        selectTool(p.tool)
        colorOf[p.tool.rawValue] = p.color
        sizeOf[p.tool.rawValue] = p.size
        prefs.set(colorOf, forKey: "tool_colors")
        prefs.set(sizeOf, forKey: "tool_sizes")
    }

    func toolVisible(_ id: String) -> Bool { !hiddenTools.contains(id) }

    func setToolVisible(_ id: String, _ v: Bool) {
        if v { hiddenTools.removeAll { $0 == id } } else if !hiddenTools.contains(id) { hiddenTools.append(id) }
    }

    /// الشريط الافتراضي (نفس أندرويد): الأدوات الأساسية فقط، والباقي يُضاف من «تخصيص شريط الأدوات».
    static let defaultHiddenTools = ["zoomwin", "ocr", "zoom", "night", "nav", "bookmark", "timer", "link", "split", "fit", "focus", "readtimer"]

    func resetToolbar() {
        hiddenTools = Self.defaultHiddenTools
        toolbarStyle = .penBar
    }

    /// النقر المزدوج على Apple Pencil: يبدّل بين الممحاة وآخر قلم.
    func penDoubleTap() {
        guard penDoubleTapEnabled else { return }
        if tool == .eraser { selectTool(lastWriting) } else { selectTool(.eraser) }
    }

    func labelOf(_ c: UInt32) -> String {
        if let l = colorLabels[String(c)] { return l }
        switch c {
        case 0xFFFFEB3B: return L("تعريف", "Definition")
        case 0xFF69F0AE: return L("دواء", "Drug")
        case 0xFFFF80AB: return L("ملاحظة امتحان", "Exam note")
        case 0xFF40C4FF: return L("مفهوم", "Concept")
        case 0xFFFFAB40: return L("مهم", "Important")
        default: return L("لون آخر", "Other color")
        }
    }

    func setLabel(_ c: UInt32, _ label: String) { colorLabels[String(c)] = label }

    // ======================================================== وقت القراءة والإحصاءات

    static func dayKey(_ d: Date = Date()) -> String {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: d)
    }

    func todayKey() -> String { Self.dayKey() }

    func noteInteraction() { lastInteraction = nowMs() }

    func setVisible(_ v: Bool) {
        visible = v
        if !v { flush() }
    }

    private func startReadTimer() {
        readTimer?.invalidate()
        readTimer = Timer.scheduledTimer(withTimeInterval: 5, repeats: true) { [weak self] _ in self?.tickReading() }
    }

    /// كل 5 ثوانٍ: إن كان مستند مفتوحًا والمستخدم تفاعل خلال دقيقتين، يُضاف الوقت.
    private func tickReading() {
        guard visible, let id = activeId, nowMs() - lastInteraction < 120_000 else { return }
        // الوقت يُجمع هنا ويُضاف كل نصف دقيقة: لا تُكتب المكتبة ولا تُعاد رسم الواجهة كل 5 ثوانٍ
        pendingRead[id, default: 0] += 5000
        pendingTicks += 1
        if pendingTicks >= 6 { commitReading() }
    }

    /// تفاصيل «اليوم|المستند» الأقدم من 13 شهرًا تُحذف (يبقى مجموع كل يوم)، فلا تكبر الإحصاءات بلا حد.
    private func pruneStats() {
        let limit = Self.dayKey(Date().addingTimeInterval(-400 * 86400))
        let old = stats.keys.filter { k in
            guard !k.hasPrefix("pomo|"), let bar = k.firstIndex(of: "|") else { return false }
            return String(k[..<bar]) < limit
        }
        guard !old.isEmpty else { return }
        var st = stats
        old.forEach { st.removeValue(forKey: $0) }
        stats = st
        prefs.set(stats, forKey: "stats")
    }

    private var pendingRead: [String: Int64] = [:]
    private var pendingTicks = 0

    /// يضيف وقت القراءة المجموع إلى المستندات والإحصاءات ويحفظه.
    private func commitReading() {
        pendingTicks = 0
        guard !pendingRead.isEmpty else { return }
        let add = pendingRead
        pendingRead = [:]
        let k = todayKey()
        var lib = library
        var st = stats
        for (id, ms) in add {
            if let i = lib.firstIndex(where: { $0.id == id }) { lib[i].readMs += ms }
            st[k, default: 0] += ms
            // وقت كل مستند في كل يوم (للتقرير حسب المادة)، بنفس مفاتيح أندرويد: «اليوم|المستند»
            st["\(k)|\(id)", default: 0] += ms
        }
        library = lib
        stats = st
        saveLibrary()
        prefs.set(stats, forKey: "stats")
        WidgetBridge.update()
    }

    func setGoal(_ minutes: Int) { goalMinutes = max(5, min(24 * 60, minutes)); WidgetBridge.update(force: true) }

    /// عدد الأيام المتتالية التي قرأت فيها دقيقة أو أكثر (حتى اليوم أو أمس).
    func studyStreak() -> Int {
        let goal: Int64 = 60_000
        var d = Date()
        var n = 0
        if (stats[Self.dayKey(d)] ?? 0) < goal { d = Calendar.current.date(byAdding: .day, value: -1, to: d)! }
        while (stats[Self.dayKey(d)] ?? 0) >= goal {
            n += 1
            d = Calendar.current.date(byAdding: .day, value: -1, to: d)!
        }
        return n
    }

    /// رقم اليوم (منذ 1970) لتاريخ كما يراه المستخدم في تقويمه، بلا تأثر بفرق التوقيت عن UTC.
    static func civilDay(_ d: Date = Date()) -> Int {
        var local = Calendar(identifier: .gregorian)
        local.timeZone = .current
        let c = local.dateComponents([.year, .month, .day], from: d)
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(secondsFromGMT: 0) ?? .current
        guard let u = utc.date(from: DateComponents(year: c.year, month: c.month, day: c.day)) else {
            return Int(d.timeIntervalSince1970 / 86400)
        }
        return Int((u.timeIntervalSince1970 / 86400).rounded())
    }

    /// بداية ذلك اليوم بتوقيت المستخدم.
    static func date(civilDay n: Int) -> Date {
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(secondsFromGMT: 0) ?? .current
        let c = utc.dateComponents([.year, .month, .day], from: Date(timeIntervalSince1970: Double(n) * 86400))
        var local = Calendar(identifier: .gregorian)
        local.timeZone = .current
        return local.date(from: DateComponents(year: c.year, month: c.month, day: c.day)) ?? Date(timeIntervalSince1970: Double(n) * 86400)
    }

    func setExamDate(_ folder: String, _ date: Date?) {
        if let d = date { examDates[folder] = Self.civilDay(d) }
        else { examDates.removeValue(forKey: folder) }
    }

    func examDate(_ folder: String) -> Date? {
        examDates[folder].map { Self.date(civilDay: $0) }
    }

    func daysToExam(_ folder: String) -> Int? {
        guard let d = examDate(folder) else { return nil }
        let cal = Calendar.current
        let n = cal.dateComponents([.day], from: cal.startOfDay(for: Date()), to: cal.startOfDay(for: d)).day ?? 0
        return n < 0 ? nil : n
    }

    /// موعد الامتحان محفوظ لكنه مضى.
    func examPassed(_ folder: String) -> Bool { examDate(folder) != nil && daysToExam(folder) == nil }

    func pagesLeft(_ folder: String) -> Int {
        library.filter { $0.folder == folder && !$0.isMap }.reduce(0) { $0 + max(0, $1.pageCount - ($1.lastPage + 1)) }
    }

    // ======================================================== مؤقت الدراسة

    func setPomodoro(study: Int, rest: Int) {
        studyMin = max(5, min(180, study))
        breakMin = max(1, min(60, rest))
        prefs.set(studyMin, forKey: "pomo_study")
        prefs.set(breakMin, forKey: "pomo_break")
    }

    func startPomodoro() {
        pomoPhase = 1
        pomoPaused = false
        pomoLeftMs = Int64(studyMin) * 60_000
        runPomodoro()
    }

    func pausePomodoro() {
        guard pomoPhase != 0, !pomoPaused else { return }
        pomoLeftMs = max(0, pomoEndsAt - nowMs())
        pomoPaused = true
        pomoTimer?.invalidate()
        schedulePomoNotice()
    }

    func resumePomodoro() {
        guard pomoPhase != 0, pomoPaused else { return }
        pomoPaused = false
        runPomodoro()
    }

    func stopPomodoro() {
        pomoPhase = 0
        pomoPaused = false
        pomoTimer?.invalidate()
        schedulePomoNotice()
    }

    /// إشعار بانتهاء الجلسة الحالية إن كان التطبيق في الخلفية (في الواجهة تظهر الرسالة المنبثقة).
    private func schedulePomoNotice() {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: ["pomo"])
        guard pomoPhase != 0, !pomoPaused else { return }
        let ends = pomoEndsAt
        let study = pomoPhase == 1
        let rest = breakMin, work = studyMin
        center.requestAuthorization(options: [.alert, .sound]) { ok, _ in
            guard ok else { return }
            DispatchQueue.main.async {
                // المؤقت أُوقف أو تغيّر قبل وصول الإذن
                guard self.pomoPhase != 0, !self.pomoPaused, self.pomoEndsAt == ends else { return }
                let secs = Double(ends - nowMs()) / 1000
                guard secs > 1 else { return }
                let c = UNMutableNotificationContent()
                c.title = study ? L("انتهت جلسة الدراسة", "Study session finished") : L("انتهت الراحة", "Break over")
                c.body = study ? L("استرح \(rest) دقائق", "Take a \(rest)-minute break") : L("جلسة دراسة جديدة \(work) دقيقة", "New \(work)-minute study session")
                c.sound = .default
                center.add(UNNotificationRequest(identifier: "pomo", content: c,
                                                 trigger: UNTimeIntervalNotificationTrigger(timeInterval: secs, repeats: false)))
            }
        }
    }

    private func runPomodoro() {
        pomoEndsAt = nowMs() + pomoLeftMs
        schedulePomoNotice()
        pomoTimer?.invalidate()
        pomoTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            let now = nowMs()
            if self.pomoPhase == 1 { self.noteInteraction() }
            self.pomoLeftMs = max(0, self.pomoEndsAt - now)
            if now >= self.pomoEndsAt {
                if self.pomoPhase == 1 {
                    self.stats["pomo|" + self.todayKey(), default: 0] += 1
                    self.pomoPhase = 2
                    self.pomoEndsAt = now + Int64(self.breakMin) * 60_000
                    self.say(.done, L("انتهت جلسة الدراسة. استرح \(self.breakMin) دقائق", "Study session finished. Take a \(self.breakMin)-minute break"))
                } else {
                    self.pomoPhase = 1
                    self.pomoEndsAt = now + Int64(self.studyMin) * 60_000
                    self.say(.done, L("انتهت الراحة. جلسة دراسة جديدة \(self.studyMin) دقيقة", "Break over. New \(self.studyMin)-minute study session"))
                }
                self.pomoLeftMs = self.pomoEndsAt - now
                self.schedulePomoNotice()
                UINotificationFeedbackGenerator().notificationOccurred(.success)
            }
        }
    }

    // ======================================================== التذكير اليومي

    func setReminder(_ on: Bool, minute: Int? = nil) {
        if let m = minute { reminderMinute = m }
        prefs.set(reminderMinute, forKey: "reminder_min")
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: ["daily"])
        guard on else {
            reminderOn = false
            prefs.set(false, forKey: "reminder_on")
            return
        }
        center.requestAuthorization(options: [.alert, .sound]) { ok, _ in
            DispatchQueue.main.async {
                self.reminderOn = ok
                self.prefs.set(ok, forKey: "reminder_on")
                guard ok else {
                    self.say(.error, L("اسمح بالإشعارات من إعدادات الجهاز", "Allow notifications in the device settings"))
                    return
                }
                let c = UNMutableNotificationContent()
                c.title = L("وقت المذاكرة", "Study time")
                c.body = L("افتح especial notes وتابع من حيث توقفت", "Open especial notes and continue where you left off")
                c.sound = .default
                var dc = DateComponents()
                dc.hour = self.reminderMinute / 60
                dc.minute = self.reminderMinute % 60
                let req = UNNotificationRequest(identifier: "daily", content: c,
                                                trigger: UNCalendarNotificationTrigger(dateMatching: dc, repeats: true))
                center.add(req)
            }
        }
    }

    // ======================================================== قفل المستندات

    func hasPin() -> Bool { PinStore.load() != nil || prefs.string(forKey: "lock_pin") != nil }

    /// الصيغة القديمة للرمز (تبقى تُقبل مرة واحدة ثم تُحوَّل إلى الجديدة).
    private func legacyHash(_ pin: String) -> String {
        var h: UInt64 = 1469598103934665603
        for b in ("hamish:" + pin).utf8 { h = (h ^ UInt64(b)) &* 1099511628211 }
        return String(h, radix: 16)
    }

    func setPin(_ pin: String) {
        PinStore.save(pin)
        prefs.removeObject(forKey: "lock_pin")
    }

    func checkPin(_ pin: String) -> Bool {
        if let stored = PinStore.load() { return PinStore.matches(pin, stored) }
        guard prefs.string(forKey: "lock_pin") == legacyHash(pin) else { return false }
        setPin(pin)
        return true
    }

    func toggleLock(_ id: String) {
        guard let e = entry(id) else { return }
        if e.locked {
            authenticate(reason: L("إلغاء قفل المستند", "Unlock the document")) { ok in
                if ok {
                    self.update(id) { $0.locked = false }
                    self.say(.done, L("أُلغي قفل المستند", "Document unlocked"))
                }
            }
            return
        }
        guard Premium.shared.require(.lock) else { return }
        if !hasPin() {
            setPinFor = id
            return
        }
        update(id) { $0.locked = true }
        unlocked.remove(id)
        closeTab(id)
        message(L("قُفل المستند: يحتاج رمزك أو بصمتك لفتحه", "Document locked: needs your PIN or fingerprint to open"))
    }

    /// Face ID / Touch ID، ثم الرمز إن لم تنجح.
    func authenticate(reason: String, _ done: @escaping (Bool) -> Void) {
        let ctx = LAContext()
        var err: NSError?
        if ctx.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &err) {
            ctx.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason) { ok, _ in
                DispatchQueue.main.async {
                    if ok { done(true) } else { self.pinPrompt = PinPrompt(reason: reason, done: done) }
                }
            }
        } else {
            pinPrompt = PinPrompt(reason: reason, done: done)
        }
    }

    struct PinPrompt: Identifiable {
        let id = UUID()
        let reason: String
        let done: (Bool) -> Void
    }

    @Published var pinPrompt: PinPrompt? = nil

    func requestUnlock(_ id: String) {
        authenticate(reason: L("فتح المستند المقفل", "Open the locked document")) { ok in
            self.unlockRequest = nil
            if ok {
                self.unlocked.insert(id)
                self.openDocument(id)
            }
        }
    }

    // ======================================================== البحث في المكتبة

    struct LibraryHit: Identifiable {
        let id = UUID()
        let docId: String
        let docName: String
        let page: Int
        let snippet: String
    }

    func searchLibrary(_ q: String, _ done: @escaping ([LibraryHit]) -> Void) {
        let query = q.trimmingCharacters(in: .whitespaces)
        let docs = library.filter { !$0.isMap && !($0.locked && !unlocked.contains($0.id)) }
        DispatchQueue.global(qos: .userInitiated).async {
            var hits: [LibraryHit] = []
            for e in docs {
                guard let pdf = PDFDocument(url: self.repo.pdfURL(e.id)) else { continue }
                let found = pdf.findString(query, withOptions: [.caseInsensitive, .diacriticInsensitive])
                for sel in found.prefix(30) {
                    guard let p = sel.pages.first else { continue }
                    let ext = sel.copy() as! PDFSelection
                    ext.extendForLineBoundaries()
                    let snippet = (ext.string ?? query).replacingOccurrences(of: "\n", with: " ")
                    hits.append(LibraryHit(docId: e.id, docName: e.name, page: pdf.index(for: p), snippet: snippet))
                }
                // الصفحات التي قُرئ نصها (صور ممسوحة) والكتابة المجمَّلة
                let data = self.repo.loadData(e.id)
                var pageIndex: [String: Int] = [:]
                for (i, p) in data.pages.enumerated() { pageIndex[p.id] = i }
                var texts: [Int: [String]] = [:]
                if let d = try? Data(contentsOf: self.repo.dir(e.id).appendingPathComponent("ocr.json")),
                   let ocr = try? JSONDecoder().decode([String: [OcrWord]].self, from: d) {
                    for (pid, words) in ocr {
                        guard let i = pageIndex[pid], (pdf.page(at: i)?.string ?? "").count <= 20 else { continue }
                        texts[i, default: []].append(words.map { $0.text }.joined(separator: " "))
                    }
                }
                for line in data.converted {
                    guard let i = pageIndex[line.page] else { continue }
                    texts[i, default: []].append(line.words.map { $0.text }.joined(separator: " "))
                }
                for i in texts.keys.sorted() {
                    let text = (texts[i] ?? []).joined(separator: " ")
                    guard let r = text.range(of: query, options: [.caseInsensitive, .diacriticInsensitive]) else { continue }
                    let from = text.index(r.lowerBound, offsetBy: -40, limitedBy: text.startIndex) ?? text.startIndex
                    let to = text.index(r.upperBound, offsetBy: 60, limitedBy: text.endIndex) ?? text.endIndex
                    hits.append(LibraryHit(docId: e.id, docName: e.name, page: i, snippet: String(text[from..<to])))
                }
                if hits.count > 200 { break }
            }
            DispatchQueue.main.async { done(hits) }
        }
    }

    // ======================================================== البطاقات

    func addCard(front: String, back: String, folder: String, docId: String? = nil, source: String = "") {
        cards.append(Card(id: UUID().uuidString, front: front, back: back, folder: folder, docId: docId, source: source))
    }

    func cardsIn(_ deck: String?) -> [Card] {
        guard let d = deck else { return cards }
        if d.hasPrefix("doc:") { let id = String(d.dropFirst(4)); return cards.filter { $0.docId == id } }
        return cards.filter { $0.folder == d }
    }
}
