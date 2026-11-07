import SwiftUI
import PDFKit
import PencilKit
import Vision

/// كلمة أو سطر مقروء بالقراءة الضوئية (إحداثيات الصفحة، الأصل أعلى اليسار).
struct OcrLine: Codable, Equatable {
    var text: String
    var rect: MarkRect
}

/// خطوة قابلة للتراجع.
enum EditStep {
    case ink(page: String, before: PKDrawing, after: PKDrawing)
    case data(before: DocData, after: DocData)
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
    @Published var searchHits: [PDFSelection] = []
    @Published var searchIndex = 0
    @Published var trayMode: String? = nil      // "stickers" أو "photos"
    @Published var mapOpen = false
    @Published var ocrRunning = false
    @Published var ocrProgress = 0
    @Published var scalePercent = 100
    @Published var editingBox: TextBox? = nil
    @Published var editingNote: StickyNote? = nil
    @Published var selectedImage: String? = nil

    // ---------- التراجع ----------
    @Published private(set) var undoStack: [EditStep] = []
    @Published private(set) var redoStack: [EditStep] = []

    private var drawings: [String: PKDrawing] = [:]
    private var dirtyInk: Set<String> = []
    private var dataDirty = false
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

    private func push(_ s: EditStep) {
        undoStack.append(s)
        if undoStack.count > 200 { undoStack.removeFirst() }
        redoStack.removeAll()
    }

    func undo() {
        guard let s = undoStack.popLast() else { return }
        apply(s, reverse: true)
        redoStack.append(s)
    }

    func redo() {
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

    /// كل التمييزات مرتبة حسب الصفحة ثم موضعها.
    func allMarks() -> [TextMark] {
        data.marks.sorted { a, b in
            let ia = index(of: a.page) ?? 0, ib = index(of: b.page) ?? 0
            if ia != ib { return ia < ib }
            return (a.rects.first?.t ?? 0) < (b.rects.first?.t ?? 0)
        }
    }

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

    private func scheduleSave() {
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
        var hits = doc.findString(q, withOptions: [.caseInsensitive, .diacriticInsensitive])
        // نص الصفحات المقروءة ضوئيًا
        if hits.isEmpty { hits = [] }
        searchHits = hits
        searchIndex = 0
        if let first = hits.first, let p = first.pages.first { goTo(page: doc.index(for: p)) }
    }

    func ocrHits(_ q: String) -> [(Int, String)] {
        var out: [(Int, String)] = []
        for (i, info) in data.pages.enumerated() {
            for l in ocr[info.id] ?? [] where l.text.range(of: q, options: [.caseInsensitive, .diacriticInsensitive]) != nil {
                out.append((i, l.text))
            }
        }
        return out
    }

    // ======================================================== القراءة الضوئية

    @Published var ocr: [String: [OcrLine]] = [:]

    private var ocrURL: URL { repo.dir(id).appendingPathComponent("ocr.json") }

    func loadOcr() {
        if let d = try? Data(contentsOf: ocrURL), let v = try? JSONDecoder().decode([String: [OcrLine]].self, from: d) { ocr = v }
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
                let lines = Self.recognize(page)
                DispatchQueue.main.async {
                    self.ocr[info.id] = lines
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

    static func recognize(_ page: PDFPage) -> [OcrLine] {
        let b = page.bounds(for: .cropBox)
        let scale: CGFloat = 2.5
        let img = page.thumbnail(of: CGSize(width: b.width * scale, height: b.height * scale), for: .cropBox)
        guard let cg = img.cgImage else { return [] }
        let req = VNRecognizeTextRequest()
        req.recognitionLevel = .accurate
        req.usesLanguageCorrection = true
        let supported = (try? req.supportedRecognitionLanguages()) ?? []
        req.recognitionLanguages = ["ar-SA", "en-US"].filter { supported.contains($0) }
        let h = VNImageRequestHandler(cgImage: cg, options: [:])
        try? h.perform([req])
        let w = b.width, hh = b.height
        return (req.results ?? []).compactMap { o in
            guard let t = o.topCandidates(1).first?.string else { return nil }
            let r = o.boundingBox
            let rect = CGRect(x: r.minX * w, y: (1 - r.maxY) * hh, width: r.width * w, height: r.height * hh)
            return OcrLine(text: t, rect: MarkRect(rect))
        }
    }
}
