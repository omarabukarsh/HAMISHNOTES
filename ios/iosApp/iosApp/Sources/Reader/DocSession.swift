import SwiftUI
import PDFKit
import PencilKit
import Vision

/// خطوة قابلة للتراجع.
enum EditStep {
    case ink(page: String, before: PKDrawing, after: PKDrawing)
    case data(before: DocData, after: DocData)
    /// تعديل الحبر والبيانات معًا (التحديد الحر) خطوةً واحدة.
    indirect case both(EditStep, EditStep)
}

/// منطقة نافذة الكتابة المكبّرة على صفحة (بنقاط الصفحة).
struct ZoomBox: Equatable {
    var page: Int
    var x: CGFloat
    var y: CGFloat
    var w: CGFloat
    var h: CGFloat
}

/// مستند مفتوح في تبويب: الملف، وما كُتب عليه، وحالة الواجهة.
final class DocSession: ObservableObject, Identifiable {
    let id: String
    @Published var title: String
    let isMap: Bool
    let repo = Repository.shared

    @Published var pdf: PDFDocument?
    @Published var failed: String? = nil
    @Published var data = DocData()
    @Published var currentPage = 0
    @Published var pageCount = 0
    /// يتغير عند أي تعديل في بنية الصفحات (لإعادة رسم الصور المصغّرة).
    @Published var pagesVersion = 0
    @Published var inkVersion = 0

    // ---------- الواجهة ----------
    @Published var drawerOpen = false
    @Published var summaryOpen = false
    @Published var pageMenuOpen = false
    @Published var searchOpen = false
    @Published var searchQuery = ""
    @Published var searchHits: [SearchHit] = []
    @Published var searchIndex = 0
    @Published var trayMode: String? = nil      // "stickers" أو "photos"
    @Published var mapOpen = false
    /// نصوص من الملخصات تُوضع الآن في الخريطة (تفتح الخريطة لاختيار مكانها).
    @Published var mapPlacing: [String]? = nil
    @Published var ocrRunning = false
    @Published var ocrProgress = 0
    @Published var scalePercent = 100
    @Published var editingBox: TextBox? = nil
    @Published var editingNote: StickyNote? = nil
    @Published var selectedImage: String? = nil
    /// نافذة الكتابة المكبّرة: المنطقة المعروضة من الصفحة (بنقاط الصفحة).
    @Published var zoomBox: ZoomBox? = nil
    /// ما حدده التحديد الحر في صفحة.
    @Published var lassoSel: LassoSel? = nil

    // ---------- التراجع ----------
    @Published private(set) var undoStack: [EditStep] = []
    @Published private(set) var redoStack: [EditStep] = []

    var drawings: [String: PKDrawing] = [:]
    var dirtyInk: Set<String> = []
    var dataDirty = false
    private var pdfDirty = false
    private var saveWork: DispatchWorkItem?

    /// خطافات يضبطها عارض PDF.
    var goToHook: ((Int) -> Void)?
    var zoomHook: ((CGFloat) -> Void)?
    var fitHook: (() -> Void)?
    var reloadHook: (() -> Void)?
    var applyInkHook: ((String, PKDrawing) -> Void)?

    init(entry: DocEntry) {
        id = entry.id
        title = entry.name
        isMap = entry.isMap
        data = repo.loadData(entry.id)
        if entry.isMap {
            if data.map == nil { data.map = MindMapData(title: entry.name) }
            return
        }
        let url = repo.pdfURL(entry.id)
        if let doc = PDFDocument(url: url) {
            pdf = doc
            pageCount = doc.pageCount
            // ملف قديم أو معدّل: نضمن معرّفًا لكل صفحة
            if data.pages.count != doc.pageCount {
                while data.pages.count < doc.pageCount { data.pages.append(PageInfo(id: UUID().uuidString)) }
                if data.pages.count > doc.pageCount { data.pages.removeLast(data.pages.count - doc.pageCount) }
                dataDirty = true
            }
            currentPage = max(0, min(doc.pageCount - 1, entry.lastPage))
            currentOrder = data.pages
            loadOcr()
        } else {
            failed = L("الملف تالف أو محمي بكلمة مرور", "The file is damaged or password protected")
        }
    }

    // ======================================================== الصفحات

    func pageId(_ index: Int) -> String? { data.pages.indices.contains(index) ? data.pages[index].id : nil }

    func index(of pageId: String) -> Int? { data.pages.firstIndex { $0.id == pageId } }

    func pageSize(_ index: Int) -> CGSize {
        guard let p = pdf?.page(at: index) else { return CGSize(width: 595, height: 842) }
        let b = p.bounds(for: .cropBox)
        let r = (p.rotation % 180) != 0
        return r ? CGSize(width: b.height, height: b.width) : b.size
    }

    func isBlank(_ index: Int) -> Bool { data.pages.indices.contains(index) && data.pages[index].src.hasPrefix("blank:") }

    func goTo(page: Int) {
        let p = max(0, min(pageCount - 1, page))
        currentPage = p
        goToHook?(p)
    }

    func setCurrent(_ p: Int) {
        guard p != currentPage else { return }
        currentPage = p
        AppController.shared.setLastPage(id, p)
    }

    // ======================================================== الحبر

    func drawing(for pageId: String) -> PKDrawing {
        if let d = drawings[pageId] { return d }
        let d = repo.loadInk(id, page: pageId)
        drawings[pageId] = d
        return d
    }

    /// الكانفاس غيّر الحبر: يُسجَّل للتراجع ويُحفظ لاحقًا.
    func inkChanged(_ pageId: String, _ new: PKDrawing) {
        let old = drawing(for: pageId)
        if old.dataRepresentation() == new.dataRepresentation() { return }
        drawings[pageId] = new
        dirtyInk.insert(pageId)
        push(.ink(page: pageId, before: old, after: new))
        scheduleSave()
        scheduleHandRead(pageId)
    }

    /// يضع حبرًا جديدًا على الصفحة (من الأشكال أو اللصق) ويحدّث الكانفاس.
    func setInk(_ pageId: String, _ new: PKDrawing, record: Bool = true) {
        let old = drawing(for: pageId)
        drawings[pageId] = new
        dirtyInk.insert(pageId)
        if record { push(.ink(page: pageId, before: old, after: new)) }
        applyInkHook?(pageId, new)
        inkVersion += 1
        scheduleSave()
    }

    func addStrokes(_ pageId: String, _ strokes: [PKStroke]) {
        var d = drawing(for: pageId)
        d.strokes.append(contentsOf: strokes)
        setInk(pageId, d)
    }

    // ======================================================== البيانات

    /// تعديل على البيانات مع تسجيله للتراجع.
    func edit(_ f: (inout DocData) -> Void) {
        let before = data
        f(&data)
        push(.data(before: before, after: data))
        dataDirty = true
        scheduleSave()
    }

    func push(_ s: EditStep) {
        undoStack.append(s)
        if undoStack.count > 200 { undoStack.removeFirst() }
        redoStack.removeAll()
    }

    func undo() {
        lassoSel = nil
        guard let s = undoStack.popLast() else { return }
        apply(s, reverse: true)
        redoStack.append(s)
    }

    func redo() {
        lassoSel = nil
        guard let s = redoStack.popLast() else { return }
        apply(s, reverse: false)
        undoStack.append(s)
    }

    private func apply(_ s: EditStep, reverse: Bool) {
        switch s {
        case let .ink(page, before, after):
            let d = reverse ? before : after
            drawings[page] = d
            dirtyInk.insert(page)
            applyInkHook?(page, d)
            inkVersion += 1
        case let .data(before, after):
            let structure = (reverse ? before : after).pages != data.pages
            data = reverse ? before : after
            dataDirty = true
            if structure { rebuildPDFOrder() }
        case let .both(a, b):
            apply(a, reverse: reverse)
            apply(b, reverse: reverse)
            return
        }
        scheduleSave()
    }

    // ---------- العلامات المرجعية ----------

    func toggleBookmark(_ page: Int) {
        guard let pid = pageId(page) else { return }
        edit { d in
            if let i = d.bookmarks.firstIndex(of: pid) { d.bookmarks.remove(at: i) } else { d.bookmarks.append(pid) }
        }
    }

    func isBookmarked(_ page: Int) -> Bool { pageId(page).map { data.bookmarks.contains($0) } ?? false }

    // ---------- التمييز ----------

    func marks(onPage pid: String) -> [TextMark] { data.marks.filter { $0.page == pid } }

    /// تمييزات الملخص بترتيب القراءة: الصفحة، ثم أعلى مستطيل، ثم أقصى اليسار (مثل MarkMerge.readingOrder).
    func allMarks() -> [TextMark] {
        data.marks.filter { $0.inSummary && !$0.free }.sorted { a, b in
            let ia = index(of: a.page) ?? 0, ib = index(of: b.page) ?? 0
            if ia != ib { return ia < ib }
            let ta = a.rects.map { $0.t }.min() ?? 0, tb = b.rects.map { $0.t }.min() ?? 0
            if ta != tb { return ta < tb }
            return (a.rects.map { $0.l }.min() ?? 0) < (b.rects.map { $0.l }.min() ?? 0)
        }
    }

    // ---------- كلمات الصفحة والتقاط النص ----------

    private var wordCache: [String: [OcrWord]] = [:]
    var handWords: [String: [OcrWord]] = [:]
    private var handKeys: [String: String] = [:]
    private var handWork: [String: DispatchWorkItem] = [:]

    /// كلمات الصفحة: من طبقة نص PDF، وإلا من القراءة الضوئية المحفوظة.
    func words(_ index: Int) -> [OcrWord] {
        guard let pid = pageId(index) else { return [] }
        if let w = wordCache[pid] { return w }
        var w: [OcrWord] = []
        if let p = pdf?.page(at: index) { w = TextLayout.pdfWords(p) }
        if w.count < 3, let o = ocr[pid] { w = o }
        // كلمات خط اليد تُضاف دائمًا (تُقرأ في الخلفية بعد الكتابة)
        if let h = handWords[pid], !h.isEmpty {
            let off = (w.map { $0.line }.max() ?? -1) + 1
            w += h.map { var x = $0; x.line += off; return x }
        } else {
            scheduleHandRead(pid)
        }
        wordCache[pid] = w
        return w
    }

    // ---------- خط اليد كنص ----------

    /// يقرأ خط اليد في الصفحة بعد توقف الكتابة لحظة.
    func scheduleHandRead(_ pid: String, delay: Double = 1.2) {
        let d = drawing(for: pid)
        guard d.strokes.contains(where: HandwritingWords.eligible) || handWords[pid] != nil else { return }
        let lang = AppController.shared.hwLang
        let k = HandwritingWords.key(d, lang: lang)
        if handKeys[pid] == k { return }
        handWork[pid]?.cancel()
        guard let idx = index(of: pid) else { return }
        let w = pageSize(idx).width
        let work = DispatchWorkItem { [weak self] in
            let words = HandwritingWords.read(d, pageW: w, lang: lang)
            DispatchQueue.main.async {
                guard let self = self else { return }
                self.handWords[pid] = words
                self.handKeys[pid] = k
                self.wordCache.removeValue(forKey: pid)
            }
        }
        handWork[pid] = work
        DispatchQueue.global(qos: .utility).asyncAfter(deadline: .now() + delay, execute: work)
    }

    /// خط هايلايتر بالتقاط النص (نفس DocSession.snapHighlight في أندرويد).
    /// pts بإحداثيات نسبية لعرض الصفحة. true = صار تمييزًا على النص.
    @discardableResult
    func snapHighlight(_ index: Int, pts: [RelPt], color: UInt32, band: CGFloat) -> Bool {
        guard let pid = pageId(index) else { return false }
        let ws = words(index)
        let idx = TextLayout.wordsUnder(ws, pts, band: band)
        guard !idx.isEmpty else { return false }
        let c = AppController.shared
        let existing = marks(onPage: pid)
        guard let res = MarkMerge.add(existing: existing, page: pid, words: ws, idx: idx, color: color, now: nowMs(),
                                      inSummary: c.highlightToSummary && c.summariesOn) else { return false }
        edit { d in d.marks = d.marks.filter { $0.page != pid } + res.marks }
        if let m = res.message { c.message(m) }
        return true
    }

    // ---------- تحرير الملخص (نقل DocSession.kt) ----------

    var summaryEdits: SummaryEdits { data.summaryEdits ?? SummaryEdits() }

    func changeEdits(_ f: (SummaryEdits) -> SummaryEdits) {
        let next = f(summaryEdits)
        if next == summaryEdits { return }
        edit { $0.summaryEdits = next }
    }

    /// kind: "h" عنوان، "p" نص عادي، nil = كما يكتشفه البرنامج.
    func setBlockKind(_ key: String, _ kind: String?) {
        changeEdits { e in var x = e; if let k = kind { x.kinds[key] = k } else { x.kinds.removeValue(forKey: key) }; return x }
    }

    func moveBlock(_ shown: [String], _ key: String, _ delta: Int) {
        guard let i = shown.firstIndex(of: key) else { return }
        let j = i + delta
        guard j >= 0 && j < shown.count else { return }
        var next = shown
        next[i] = shown[j]
        next[j] = key
        changeEdits { e in var x = e; x.order = next; return x }
    }

    func makeTable(_ keys: [String], cols: Int) {
        guard !keys.isEmpty else { return }
        changeEdits { e in
            var expanded: [String] = []
            var absorbed = Set<String>()
            for k in keys {
                if k.hasPrefix("g:"), let g = e.tables.first(where: { "g:" + $0.id == k }) {
                    expanded += g.keys
                    absorbed.insert(g.id)
                } else { expanded.append(k) }
            }
            var uniq: [String] = []
            for k in expanded where !uniq.contains(k) { uniq.append(k) }
            let group = TableGroup(id: UUID().uuidString, keys: uniq, cols: cols)
            let gKey = "g:" + group.id
            let memberKeys = Set(keys).union(uniq)
            let firstPos = e.order.firstIndex { memberKeys.contains($0) }
            var order = e.order
            if let fp = firstPos {
                order = e.order.enumerated().compactMap { i, k in i == fp ? gKey : (memberKeys.contains(k) ? nil : k) }
            }
            var x = e
            x.tables = e.tables.filter { !absorbed.contains($0.id) && !$0.keys.contains(where: { uniq.contains($0) }) } + [group]
            x.order = order
            return x
        }
    }

    func saveTable(_ blockKey: String, rows: [[String]], header: Bool) {
        changeEdits { e in
            var x = e
            if blockKey.hasPrefix("g:") {
                x.tables = e.tables.map { g in var y = g; if "g:" + g.id == blockKey { y.rows = rows; y.header = header }; return y }
            } else {
                let g = TableGroup(id: UUID().uuidString, keys: [blockKey], rows: rows, header: header)
                x.tables.append(g)
                x.order = e.order.map { $0 == blockKey ? "g:" + g.id : $0 }
            }
            return x
        }
    }

    func addToTable(_ tableKey: String, keys: [String], newRows: [[String]]) {
        changeEdits { e0 in
            var absorbed = Set<String>()
            var add: [String] = []
            for k in keys where k != tableKey {
                if k.hasPrefix("g:"), let g = e0.tables.first(where: { "g:" + $0.id == k }) {
                    absorbed.insert(g.id)
                    add += g.keys
                } else { add.append(k) }
            }
            if add.isEmpty { return e0 }
            var e = e0
            e.tables = e0.tables.filter { !absorbed.contains($0.id) }
            let order = e.order.filter { !add.contains($0) && !absorbed.contains(String($0.dropFirst(2))) }
            if tableKey.hasPrefix("g:") {
                e.tables = e.tables.map { g in
                    var y = g
                    if "g:" + g.id == tableKey {
                        var ks = g.keys
                        for k in add where !ks.contains(k) { ks.append(k) }
                        y.keys = ks
                        if let r = g.rows { y.rows = r + newRows }
                    }
                    return y
                }
                e.order = order
            } else {
                let g = TableGroup(id: UUID().uuidString, keys: [tableKey] + add)
                e.tables.append(g)
                e.order = order.map { $0 == tableKey ? "g:" + g.id : $0 }
            }
            return e
        }
    }

    func removeTable(_ groupKey: String) {
        changeEdits { e in var x = e; x.tables = e.tables.filter { "g:" + $0.id != groupKey }; return x }
    }

    func resetBlock(_ key: String) {
        changeEdits { e in
            var x = e
            x.kinds.removeValue(forKey: key)
            x.order = e.order.filter { $0 != key }
            x.tables = e.tables.filter { "g:" + $0.id != key }.map { g in var y = g; y.keys = g.keys.filter { $0 != key }; return y }.filter { !$0.keys.isEmpty }
            return x
        }
    }

    func updateMarkText(_ id: String, _ text: String) { updateMark(id) { $0.text = text } }

    func addMark(_ m: TextMark) { edit { $0.marks.append(m) } }

    func updateMark(_ id: String, _ f: (inout TextMark) -> Void) {
        edit { d in if let i = d.marks.firstIndex(where: { $0.id == id }) { f(&d.marks[i]) } }
    }

    func deleteMark(_ id: String) { edit { $0.marks.removeAll { $0.id == id } } }

    // ---------- النصوص والصور والملاحظات ----------

    func upsertBox(_ b: TextBox) {
        edit { d in
            if let i = d.boxes.firstIndex(where: { $0.id == b.id }) { d.boxes[i] = b } else { d.boxes.append(b) }
        }
    }

    func deleteBox(_ id: String) { edit { $0.boxes.removeAll { $0.id == id } } }

    func upsertImage(_ im: ImageItem) {
        edit { d in
            if let i = d.images.firstIndex(where: { $0.id == im.id }) { d.images[i] = im } else { d.images.append(im) }
        }
    }

    func deleteImage(_ id: String) { edit { $0.images.removeAll { $0.id == id } } }

    func upsertNote(_ n: StickyNote) {
        edit { d in
            if let i = d.notes.firstIndex(where: { $0.id == n.id }) { d.notes[i] = n } else { d.notes.append(n) }
        }
    }

    func deleteNote(_ id: String) { edit { $0.notes.removeAll { $0.id == id } } }

    /// يضيف صورة إلى الصفحة الحالية في وسطها.
    func insertImage(_ img: UIImage, page: Int? = nil) {
        let p = page ?? currentPage
        guard let pid = pageId(p), let jpg = img.jpegData(compressionQuality: 0.88) else { return }
        let name = "img-\(UUID().uuidString).jpg"
        try? jpg.write(to: repo.mediaDir(id).appendingPathComponent(name))
        let size = pageSize(p)
        let w = size.width * 0.5
        let aspect = img.size.width / max(1, img.size.height)
        let item = ImageItem(id: UUID().uuidString, file: name, page: pid,
                             x: (size.width - w) / 2, y: size.height * 0.2, w: w, aspect: aspect)
        upsertImage(item)
        selectedImage = item.id
    }

    func insertSticker(_ key: String, page: Int? = nil, at point: CGPoint? = nil) {
        let p = page ?? currentPage
        guard let pid = pageId(p) else { return }
        let size = pageSize(p)
        let w = size.width * 0.16
        let c = point ?? CGPoint(x: size.width / 2, y: size.height * 0.3)
        let item = ImageItem(id: UUID().uuidString, file: "sticker:" + key, page: pid, x: c.x - w / 2, y: c.y - w / 2, w: w, aspect: 1)
        upsertImage(item)
        selectedImage = item.id
    }

    func image(for item: ImageItem) -> UIImage? {
        if item.file.hasPrefix("sticker:") {
            let key = String(item.file.dropFirst(8))
            if key.hasPrefix("mine:") {
                return UIImage(contentsOfFile: repo.stickersDir.appendingPathComponent(String(key.dropFirst(5))).path)
            }
            return UIImage(named: "st_" + key) ?? emojiImage(key)
        }
        return UIImage(contentsOfFile: repo.mediaDir(id).appendingPathComponent(item.file).path)
    }

    private func emojiImage(_ s: String) -> UIImage {
        let size = CGSize(width: 160, height: 160)
        return UIGraphicsImageRenderer(size: size).image { _ in
            (s as NSString).draw(in: CGRect(x: 0, y: 10, width: 160, height: 150),
                                 withAttributes: [.font: UIFont.systemFont(ofSize: 120)])
        }
    }

    // ======================================================== بنية الصفحات

    /// يضيف صفحة فارغة بقالب بعد الصفحة after.
    func addBlankPage(after: Int, kind: String, color: UInt32, size: CGSize? = nil) {
        guard let doc = pdf else { return }
        let s = size ?? pageSize(max(0, min(pageCount - 1, after)))
        guard let page = Templates.makePage(kind: kind, bg: color, size: s) else { return }
        let at = max(0, min(doc.pageCount, after + 1))
        edit { d in d.pages.insert(PageInfo(id: UUID().uuidString, src: Templates.src(kind, color)), at: at) }
        doc.insert(page, at: at)
        structureChanged()
        goTo(page: at)
    }

    /// يضيف صفحات ملف PDF آخر بعد الصفحة after.
    func addPdfPages(from url: URL, after: Int) {
        guard let doc = pdf else { return }
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        guard let other = PDFDocument(url: url), other.pageCount > 0 else {
            AppController.shared.message(L("تعذر فتح الملف", "Could not open the file"))
            return
        }
        var at = max(0, min(doc.pageCount, after + 1))
        let start = at
        var infos: [PageInfo] = []
        for i in 0..<other.pageCount {
            guard let p = other.page(at: i)?.copy() as? PDFPage else { continue }
            doc.insert(p, at: at)
            infos.append(PageInfo(id: UUID().uuidString))
            at += 1
        }
        edit { d in d.pages.insert(contentsOf: infos, at: start) }
        structureChanged()
        goTo(page: start)
    }

    func deletePage(_ index: Int) {
        guard let doc = pdf, doc.pageCount > 1, data.pages.indices.contains(index) else {
            AppController.shared.message(L("لا يمكن حذف الصفحة الوحيدة", "You can’t delete the only page"))
            return
        }
        if let p = doc.page(at: index) { trash[data.pages[index].id] = p }
        edit { d in d.pages.remove(at: index) }
        doc.removePage(at: index)
        structureChanged()
    }

    func duplicatePage(_ index: Int) {
        guard let doc = pdf, let p = doc.page(at: index)?.copy() as? PDFPage, let old = pageId(index) else { return }
        let nid = UUID().uuidString
        let src = data.pages[index].src
        doc.insert(p, at: index + 1)
        setInk(nid, drawing(for: old), record: false)
        edit { d in
            d.pages.insert(PageInfo(id: nid, src: src), at: index + 1)
            d.boxes += d.boxes.filter { $0.page == old }.map { var b = $0; b.id = UUID().uuidString; b.page = nid; return b }
            d.images += d.images.filter { $0.page == old }.map { var b = $0; b.id = UUID().uuidString; b.page = nid; return b }
            d.notes += d.notes.filter { $0.page == old }.map { var b = $0; b.id = UUID().uuidString; b.page = nid; return b }
        }
        structureChanged()
    }

    func movePage(_ from: Int, to: Int) {
        guard let doc = pdf, data.pages.indices.contains(from), to >= 0, to < data.pages.count, from != to,
              let page = doc.page(at: from) else { return }
        edit { d in
            let p = d.pages.remove(at: from)
            d.pages.insert(p, at: to)
        }
        doc.removePage(at: from)
        doc.insert(page, at: to)
        structureChanged()
    }

    func rotatePage(_ index: Int) {
        guard let p = pdf?.page(at: index) else { return }
        p.rotation = (p.rotation + 90) % 360
        structureChanged()
    }

    /// بعد تراجع عن تغيير في الصفحات: نعيد ترتيب صفحات PDF حسب البيانات.
    private func rebuildPDFOrder() {
        guard let doc = pdf else { return }
        // صفحات الملف الآن حسب معرّفاتها، ومعها الصفحات المحذوفة (لاسترجاعها بالتراجع)
        var pagesById: [String: PDFPage] = trash
        for (i, info) in currentOrder.enumerated() { if let p = doc.page(at: i) { pagesById[info.id] = p } }
        let n = PDFDocument()
        for (i, info) in data.pages.enumerated() {
            if let p = pagesById[info.id] {
                n.insert(p.copy() as? PDFPage ?? p, at: i)
                trash.removeValue(forKey: info.id)
            } else {
                let (kind, bg) = Templates.parse(info.src)
                if let p = Templates.makePage(kind: info.src.hasPrefix("blank:") ? kind : "plain", bg: bg, size: CGSize(width: 595, height: 842)) {
                    n.insert(p, at: i)
                }
            }
        }
        pdf = n
        structureChanged()
    }

    /// الصفحات المحذوفة في هذه الجلسة.
    private var trash: [String: PDFPage] = [:]

    /// ترتيب صفحات الملف المفتوح الآن (يتبع البيانات بعد كل تعديل).
    private var currentOrder: [PageInfo] = []

    private func structureChanged() {
        pageCount = pdf?.pageCount ?? 0
        currentOrder = data.pages
        pdfDirty = true
        pagesVersion += 1
        currentPage = max(0, min(pageCount - 1, currentPage))
        reloadHook?()
        AppController.shared.onPagesChanged(id, count: pageCount)
        scheduleSave()
    }

    // ======================================================== الحفظ

    func scheduleSave() {
        saveWork?.cancel()
        let w = DispatchWorkItem { [weak self] in self?.flush() }
        saveWork = w
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5, execute: w)
    }

    func flush() {
        saveWork?.cancel()
        if currentOrder.isEmpty { currentOrder = data.pages }
        for pid in dirtyInk { repo.saveInk(id, page: pid, drawings[pid] ?? PKDrawing()) }
        let inkChanged = !dirtyInk.isEmpty
        dirtyInk.removeAll()
        if dataDirty || pdfDirty || inkChanged { repo.saveData(id, data); dataDirty = false }
        if pdfDirty, let doc = pdf {
            if repo.savePDF(id, doc) { pdfDirty = false }
            AppController.shared.onPagesChanged(id, count: doc.pageCount)
        }
        if inkChanged { repo.invalidateThumb(id) }
    }

    // ======================================================== البحث

    func runSearch() {
        let q = searchQuery.trimmingCharacters(in: .whitespaces)
        guard let doc = pdf, !q.isEmpty else { searchHits = []; return }
        var hits: [SearchHit] = doc.findString(q, withOptions: [.caseInsensitive, .diacriticInsensitive]).compactMap { sel in
            guard let p = sel.pages.first else { return nil }
            return SearchHit(page: doc.index(for: p), sel: sel, rect: nil)
        }
        // خط اليد والصفحات المقروءة ضوئيًا
        let tokens = q.split(separator: " ").map(String.init)
        for (i, info) in data.pages.enumerated() {
            let w = pageSize(i).width
            let words = (handWords[info.id] ?? []) + ((doc.page(at: i)?.string ?? "").count > 20 ? [] : (ocr[info.id] ?? []))
            for line in Dictionary(grouping: words, by: { $0.line }).values {
                let sorted = line.sorted { $0.l < $1.l }
                let text = sorted.map { $0.text }.joined(separator: " ")
                guard text.range(of: q, options: [.caseInsensitive, .diacriticInsensitive]) != nil else { continue }
                let hitWords = sorted.filter { wd in tokens.contains { wd.text.range(of: $0, options: [.caseInsensitive, .diacriticInsensitive]) != nil } }
                let r = hitWords.reduce(CGRect.null) { $0.union(CGRect(x: $1.l * w, y: $1.t * w, width: ($1.r - $1.l) * w, height: ($1.b - $1.t) * w)) }
                if !r.isNull { hits.append(SearchHit(page: i, sel: nil, rect: r.insetBy(dx: -2, dy: -2))) }
            }
        }
        hits.sort { a, b in
            if a.page != b.page { return a.page < b.page }
            let ya = a.rect?.minY ?? 0, yb = b.rect?.minY ?? 0
            return ya < yb
        }
        searchHits = hits
        searchIndex = 0
        if let first = hits.first { goTo(page: first.page) }
    }

    func ocrHits(_ q: String) -> [(Int, String)] {
        var out: [(Int, String)] = []
        for (i, info) in data.pages.enumerated() {
            let ws = ocr[info.id] ?? []
            let lines = Dictionary(grouping: ws) { $0.line }.values.map { $0.map { $0.text }.joined(separator: " ") }
            for l in lines where l.range(of: q, options: [.caseInsensitive, .diacriticInsensitive]) != nil {
                out.append((i, l))
            }
        }
        return out
    }

    // ======================================================== القراءة الضوئية

    @Published var ocr: [String: [OcrWord]] = [:]

    private var ocrURL: URL { repo.dir(id).appendingPathComponent("ocr.json") }

    func loadOcr() {
        if let d = try? Data(contentsOf: ocrURL), let v = try? JSONDecoder().decode([String: [OcrWord]].self, from: d) { ocr = v }
    }

    private func saveOcr() {
        if let d = try? JSONEncoder().encode(ocr) { try? d.write(to: ocrURL, options: .atomic) }
    }

    /// يقرأ نص الصفحات التي ليس فيها نص (صور ممسوحة) بـ Vision.
    func startOcr() {
        guard let doc = pdf, !ocrRunning else { return }
        ocrRunning = true
        ocrProgress = 0
        let pages = data.pages
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            for (i, info) in pages.enumerated() {
                guard let self = self, self.ocrRunning else { break }
                guard let page = doc.page(at: i) else { continue }
                if (page.string ?? "").trimmingCharacters(in: .whitespacesAndNewlines).count > 20 { continue }
                let ws = TextLayout.ocrWords(page)
                DispatchQueue.main.async {
                    self.ocr[info.id] = ws
                    self.wordCache.removeValue(forKey: info.id)
                    self.ocrProgress = i + 1
                }
            }
            DispatchQueue.main.async {
                guard let self = self else { return }
                self.ocrRunning = false
                self.saveOcr()
                AppController.shared.message(L("اكتملت قراءة النص", "Text reading finished"))
            }
        }
    }

    func stopOcr() { ocrRunning = false }

}
