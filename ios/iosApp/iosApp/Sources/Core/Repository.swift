import Foundation
import PDFKit
import UIKit

/// تخزين المكتبة على الجهاز:
///   Documents/Hamish/library.json           فهرس المستندات
///   Documents/Hamish/docs/<id>/doc.pdf      المستند
///   Documents/Hamish/docs/<id>/data.json    التمييز والنصوص والصور والعلامات
///   Documents/Hamish/docs/<id>/ink/<page>   حبر كل صفحة (محرك الحبر الخاص)
///   Documents/Hamish/docs/<id>/media/       الصور والتسجيلات
final class Repository {
    static let shared = Repository()
    /// طابور الكتابة على القرص (الحبر والبيانات) حتى لا تتوقف الواجهة أثناء الحفظ.
    static let io = DispatchQueue(label: "hamish.io", qos: .utility)

    let root: URL
    private let fm = FileManager.default
    private let thumbCache = NSCache<NSString, UIImage>()
    private let enc: JSONEncoder = { let e = JSONEncoder(); return e }()
    private let dec = JSONDecoder()

    private init() {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        root = docs.appendingPathComponent("Hamish", isDirectory: true)
        try? FileManager.default.createDirectory(at: root.appendingPathComponent("docs"), withIntermediateDirectories: true)
        try? FileManager.default.createDirectory(at: root.appendingPathComponent("stickers"), withIntermediateDirectories: true)
    }

    // ---------- الفهرس ----------

    private var libraryURL: URL { root.appendingPathComponent("library.json") }

    /// true إن أُصلحت المكتبة عند قراءتها (من النسخة الاحتياطية أو بإرجاع مستندات غابت عن الفهرس)،
    /// فيحفظها التطبيق فورًا ويخبر المستخدم.
    private(set) var libraryRepaired = false

    func loadLibrary() -> [DocEntry] {
        libraryRepaired = false
        var list: [DocEntry] = []
        // كل مستند يُقرأ وحده: مدخل تالف لا يُضيع المكتبة كلها
        if let d = try? Data(contentsOf: libraryURL), let items = try? dec.decode([Lossy<DocEntry>].self, from: d) {
            list = items.compactMap { $0.value }
            if list.count != items.count { keepUnreadable(libraryURL) }
        } else {
            keepUnreadable(libraryURL)
            // الفهرس تالف أو مفقود: نسخته السابقة تحفظ أسماء المستندات ومواده
            if let d = try? Data(contentsOf: backupURL(libraryURL)),
               let items = try? dec.decode([Lossy<DocEntry>].self, from: d) {
                list = items.compactMap { $0.value }
                libraryRepaired = true
            }
        }
        list = list.filter { fm.fileExists(atPath: pdfURL($0.id).path) || $0.isMap }
        // مستندات على الجهاز ليست في الفهرس (فهرس تالف، أو مدخل لم يُقرأ): تعود إلى المكتبة بدل أن تختفي
        let found = missingDocs(known: Set(list.map { $0.id }))
        if !found.isEmpty {
            list += found
            libraryRepaired = true
        }
        return list
    }

    /// مجلدات مستندات لا يذكرها الفهرس: يُصنع لكل منها مدخل من ملفاته.
    private func missingDocs(known: Set<String>) -> [DocEntry] {
        let docsDir = root.appendingPathComponent("docs", isDirectory: true)
        var out: [DocEntry] = []
        for id in (try? fm.contentsOfDirectory(atPath: docsDir.path)) ?? [] where !known.contains(id) {
            guard fm.fileExists(atPath: pdfURL(id).path) else { continue }
            let data = loadData(id)
            let isMap = data.map != nil && data.pages.isEmpty
            let pdf = isMap ? nil : PDFDocument(url: pdfURL(id))
            if !isMap && pdf == nil { continue }
            let created = (try? fm.attributesOfItem(atPath: dir(id).path)[.creationDate] as? Date) ?? Date()
            let title = data.map?.title ?? (pdf?.documentAttributes?[PDFDocumentAttribute.titleAttribute] as? String) ?? ""
            let name = title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                ? L("مستند مستعاد", "Recovered document") : title
            out.append(DocEntry(
                id: id, name: name, sizeBytes: fileSize(id), pageCount: pdf?.pageCount ?? 0,
                addedAt: Int64(created.timeIntervalSince1970 * 1000),
                isNote: data.pages.first?.src.hasPrefix("blank:") ?? false, isMap: isMap
            ))
        }
        return out
    }

    /// ملف تعذرت قراءته: تبقى منه نسخة بجانبه قبل أن يُكتب فوقه، فلا تضيع البيانات بصمت.
    private func keepUnreadable(_ url: URL) {
        guard fm.fileExists(atPath: url.path) else { return }
        let copy = url.deletingPathExtension().appendingPathExtension("unreadable-\(nowMs()).json")
        try? fm.copyItem(at: url, to: copy)
    }

    /// النسخة السابقة من الملف: data.json ← data.bak.json
    private func backupURL(_ url: URL) -> URL {
        url.deletingPathExtension().appendingPathExtension("bak.json")
    }

    /// يكتب الملف ويُبقي نسخته السابقة بجانبه (backupURL)، فإن تلف الملف بعد ذلك يُقرأ السابق بدل أن تضيع البيانات.
    private func writeKeepingBackup(_ d: Data, to url: URL) {
        let tmp = url.deletingLastPathComponent().appendingPathComponent(url.lastPathComponent + ".tmp")
        do {
            try d.write(to: tmp)
            if fm.fileExists(atPath: url.path) {
                _ = try fm.replaceItemAt(url, withItemAt: tmp, backupItemName: backupURL(url).lastPathComponent,
                                         options: .withoutDeletingBackupItem)
            } else {
                try fm.moveItem(at: tmp, to: url)
            }
        } catch {
            // تعذّر الاستبدال مع النسخة الاحتياطية: كتابة عادية آمنة (atomic) أفضل من لا شيء
            try? fm.removeItem(at: tmp)
            try? d.write(to: url, options: .atomic)
        }
    }

    func saveLibrary(_ list: [DocEntry]) {
        if let d = try? enc.encode(list) { writeKeepingBackup(d, to: libraryURL) }
    }

    func loadJSON<T: Decodable>(_ name: String, _ type: T.Type) -> T? {
        let url = root.appendingPathComponent(name)
        if let d = try? Data(contentsOf: url) {
            if let v = try? dec.decode(T.self, from: d) { return v }
            keepUnreadable(url)
        }
        guard let b = try? Data(contentsOf: backupURL(url)) else { return nil }
        return try? dec.decode(T.self, from: b)
    }

    /// قائمة تُقرأ عنصرًا عنصرًا: العنصر التالف يُتخطى وحده.
    func loadList<T: Decodable>(_ name: String, _ type: T.Type) -> [T] {
        let url = root.appendingPathComponent(name)
        if let d = try? Data(contentsOf: url), let items = try? dec.decode([Lossy<T>].self, from: d) {
            let list = items.compactMap { $0.value }
            if list.count != items.count { keepUnreadable(url) }
            return list
        }
        keepUnreadable(url)
        guard let b = try? Data(contentsOf: backupURL(url)),
              let items = try? dec.decode([Lossy<T>].self, from: b) else { return [] }
        return items.compactMap { $0.value }
    }

    func saveJSON<T: Encodable>(_ name: String, _ value: T) {
        if let d = try? enc.encode(value) { writeKeepingBackup(d, to: root.appendingPathComponent(name)) }
    }

    // ---------- المستند ----------

    func dir(_ id: String) -> URL { root.appendingPathComponent("docs/\(id)", isDirectory: true) }
    func pdfURL(_ id: String) -> URL { dir(id).appendingPathComponent("doc.pdf") }
    func mediaDir(_ id: String) -> URL {
        let u = dir(id).appendingPathComponent("media", isDirectory: true)
        try? fm.createDirectory(at: u, withIntermediateDirectories: true)
        return u
    }
    var stickersDir: URL { root.appendingPathComponent("stickers", isDirectory: true) }

    /// كيف قُرئت بيانات المستند.
    enum DataState {
        /// لا ملف بيانات ولا نسخة احتياطية (مستند جديد).
        case fresh
        case ok
        /// الملف تالف أو مفقود، فقُرئت نسخته السابقة.
        case fromBackup
        /// الملف موجود ولا يُقرأ، ولا نسخة احتياطية صالحة.
        case unreadable
    }

    private func dataURL(_ id: String) -> URL { dir(id).appendingPathComponent("data.json") }

    func loadDataChecked(_ id: String) -> (DocData, DataState) {
        let url = dataURL(id)
        let exists = fm.fileExists(atPath: url.path)
        if let d = try? Data(contentsOf: url) {
            if let v = try? dec.decode(DocData.self, from: d) { return (v, .ok) }
            keepUnreadable(url)
        }
        if let b = try? Data(contentsOf: backupURL(url)), let v = try? dec.decode(DocData.self, from: b) {
            return (v, .fromBackup)
        }
        return (DocData(), exists ? .unreadable : .fresh)
    }

    func loadData(_ id: String) -> DocData { loadDataChecked(id).0 }

    /// يعدّل بيانات مستند غير مفتوح في تبويب. لا يكتب شيئًا إن وُجد الملف وتعذرت قراءته.
    @discardableResult
    func updateData(_ id: String, _ f: (inout DocData) -> Void) -> Bool {
        var (data, state) = loadDataChecked(id)
        if state == .unreadable { return false }
        f(&data)
        saveData(id, data)
        return true
    }

    func saveData(_ id: String, _ data: DocData) {
        try? fm.createDirectory(at: dir(id), withIntermediateDirectories: true)
        if let d = try? enc.encode(data) { writeKeepingBackup(d, to: dataURL(id)) }
    }

    /// بيانات المستند لا تُقرأ: حبر صفحاته مربوط بمعرّفات صفحات ضاعت، فيُنقل جانبًا (ink-unreadable-…)
    /// بدل أن يُعدّ لاحقًا حبر صفحات محذوفة ويُمسح.
    func setAsideInk(_ id: String) {
        let folder = dir(id).appendingPathComponent("ink", isDirectory: true)
        guard fm.fileExists(atPath: folder.path) else { return }
        try? fm.moveItem(at: folder, to: dir(id).appendingPathComponent("ink-unreadable-\(nowMs())", isDirectory: true))
    }

    /// حبر صفحة (الصيغة الجديدة، أو صيغة PencilKit القديمة تُحوَّل عند القراءة).
    func loadInk(_ id: String, page: String) -> InkDrawing {
        guard Repository.safeName(page) else { return InkDrawing() }
        let u = dir(id).appendingPathComponent("ink/\(page)")
        guard let d = try? Data(contentsOf: u) else { return InkDrawing() }
        if let dr = InkDrawing.load(d) { return dr }
        // لا يُقرأ: نسخة جانبًا قبل أن يُكتب فوقه
        try? fm.copyItem(at: u, to: u.deletingLastPathComponent().appendingPathComponent("\(page).unreadable-\(nowMs())"))
        return InkDrawing()
    }

    /// اسم ملف آمن من معرّف (صفحة أو صورة): بلا مسارات ولا أسماء خاصة، فلا يُكتب ملف خارج مجلده.
    static func safeName(_ s: String) -> Bool {
        !s.isEmpty && s.count <= 200 && s != "." && s != ".." && !s.hasPrefix(".") &&
            !s.contains("/") && !s.contains("\\") && !s.contains(":") && !s.unicodeScalars.contains { $0.value < 0x20 }
    }

    func saveInk(_ id: String, page: String, _ drawing: InkDrawing) {
        guard Repository.safeName(page) else { return }
        let folder = dir(id).appendingPathComponent("ink", isDirectory: true)
        try? fm.createDirectory(at: folder, withIntermediateDirectories: true)
        let u = folder.appendingPathComponent(page)
        if drawing.strokes.isEmpty { try? fm.removeItem(at: u); return }
        try? drawing.dataRepresentation().write(to: u, options: .atomic)
    }

    func savePDF(_ id: String, _ doc: PDFDocument) -> Bool {
        let tmp = dir(id).appendingPathComponent("doc.tmp.pdf")
        guard doc.write(to: tmp) else { return false }
        do {
            if fm.fileExists(atPath: pdfURL(id).path) {
                _ = try fm.replaceItemAt(pdfURL(id), withItemAt: tmp)
            } else {
                try fm.moveItem(at: tmp, to: pdfURL(id))
            }
        } catch {
            try? fm.removeItem(at: tmp)
            return false
        }
        invalidateThumb(id)
        return true
    }

    func fileSize(_ id: String) -> Int64 {
        (try? fm.attributesOfItem(atPath: pdfURL(id).path)[.size] as? Int64) ?? 0
    }

    // ---------- الإنشاء ----------

    /// يستورد ملف PDF إلى المكتبة.
    /// progress(نسبة 0..1، المرحلة) أثناء النسخ وقراءة الصفحات؛ cancelled() يوقف الإضافة.
    func importPDF(from url: URL, name: String? = nil, folder: String = "",
                   progress: ((Double, String) -> Void)? = nil, cancelled: (() -> Bool)? = nil) -> DocEntry? {
        let id = UUID().uuidString
        try? fm.createDirectory(at: dir(id), withIntermediateDirectories: true)
        // نسخ على دفعات حتى تظهر نسبة التقدم (والإلغاء ممكن)
        let total = (try? url.resourceValues(forKeys: [.fileSizeKey]))?.fileSize ?? 0
        var copied = false
        if let ins = InputStream(url: url), fm.createFile(atPath: pdfURL(id).path, contents: nil), let out = try? FileHandle(forWritingTo: pdfURL(id)) {
            ins.open()
            var buf = [UInt8](repeating: 0, count: 256 * 1024)
            var done = 0
            var lastP = -1
            copied = true
            while true {
                if cancelled?() == true { copied = false; break }
                let n = ins.read(&buf, maxLength: buf.count)
                if n < 0 { copied = false; break }
                if n == 0 { break }
                out.write(Data(buf[0..<n]))
                done += n
                if total > 0 {
                    let p = done * 85 / total
                    if p != lastP { lastP = p; progress?(Double(p) / 100, L("نسخ الملف…", "Copying the file…")) }
                }
            }
            ins.close()
            try? out.close()
        }
        if cancelled?() == true { try? fm.removeItem(at: dir(id)); return nil }
        if !copied {
            try? fm.removeItem(at: pdfURL(id))
            do {
                try fm.copyItem(at: url, to: pdfURL(id))
            } catch {
                guard let d = try? Data(contentsOf: url) else { return nil }
                try? d.write(to: pdfURL(id))
            }
        }
        progress?(0.9, L("قراءة الصفحات…", "Reading the pages…"))
        guard let pdf = PDFDocument(url: pdfURL(id)) else {
            try? fm.removeItem(at: dir(id))
            return nil
        }
        // نسخة «قابلة للتعديل» من هامش: يعود الأصل ومعه الكتابة والتمييز والملاحظات
        if EditablePDF.restore(pdf: pdfURL(id), into: dir(id)), let orig = PDFDocument(url: pdfURL(id)) {
            var data = loadData(id)
            if data.pages.count == orig.pageCount {
                data.recordings = []
                saveData(id, data)
                let n = name ?? url.deletingPathExtension().lastPathComponent
                return DocEntry(id: id, name: n, sizeBytes: fileSize(id), pageCount: orig.pageCount, addedAt: nowMs(), folder: folder)
            }
            try? fm.removeItem(at: dir(id).appendingPathComponent("ink"))
            return freshEntry(id, orig, name: name, url: url, folder: folder)
        }
        return freshEntry(id, pdf, name: name, url: url, folder: folder)
    }

    private func freshEntry(_ id: String, _ pdf: PDFDocument, name: String?, url: URL, folder: String) -> DocEntry {
        var data = DocData()
        data.pages = (0..<pdf.pageCount).map { _ in PageInfo(id: UUID().uuidString) }
        data.outline = Repository.outline(of: pdf)
        saveData(id, data)
        let n = name ?? url.deletingPathExtension().lastPathComponent
        return DocEntry(id: id, name: n, sizeBytes: fileSize(id), pageCount: pdf.pageCount, addedAt: nowMs(), folder: folder)
    }

    /// ينشئ PDF من صور (المسح بالكاميرا).
    func importImages(_ images: [UIImage], name: String, folder: String) -> DocEntry? {
        let doc = PDFDocument()
        for (i, img) in images.enumerated() {
            if let p = PDFPage(image: img) { doc.insert(p, at: i) }
        }
        guard doc.pageCount > 0 else { return nil }
        let tmp = fm.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".pdf")
        guard doc.write(to: tmp) else { return nil }
        defer { try? fm.removeItem(at: tmp) }
        return importPDF(from: tmp, name: name, folder: folder)
    }

    /// أطول ضلع لصورة تصبح صفحة (بكسل): واضحة للقراءة والكتابة عليها دون ملف ضخم (كما في أندرويد).
    static let imagePageMax: CGFloat = 2200

    /// ملاحظة من صور (كما في أندرويد): كل صورة صفحة على مقاسها، العرض عرض A4 والارتفاع حسب الصورة.
    /// الصورة تُضمَّن JPEG كما هي فيبقى الملف صغيرًا. progress(رقم الصورة) قبل كل صورة.
    /// يعيد (المستند أو nil، عدد الصور التي تعذرت قراءتها). خارج الخيط الرئيسي.
    func createNoteFromImages(_ images: [Data], name: String, folder: String,
                              progress: ((Int) -> Void)? = nil, cancelled: (() -> Bool)? = nil) -> (DocEntry?, Int) {
        var failed = 0
        var pages: [(CGImage, CGSize)] = []
        for (i, d) in images.enumerated() {
            if cancelled?() == true { return (nil, failed) }
            progress?(i)
            guard let p = Repository.pageJPEG(d) else { failed += 1; continue }
            pages.append(p)
        }
        guard !pages.isEmpty else { return (nil, failed) }
        let pw: CGFloat = 595
        let r = UIGraphicsPDFRenderer(bounds: CGRect(x: 0, y: 0, width: pw, height: 842))
        let data = r.pdfData { ctx in
            for (img, size) in pages {
                // صورة بانورامية أو طويلة جدًا: نحد شكل الصفحة ونضع الصورة في وسطها
                let ph = min(max(pw * size.height / size.width, pw / 4), pw * 4)
                let k = min(pw / size.width, ph / size.height)
                let dw = size.width * k, dh = size.height * k
                ctx.beginPage(withBounds: CGRect(x: 0, y: 0, width: pw, height: ph), pageInfo: [:])
                UIImage(cgImage: img).draw(in: CGRect(x: (pw - dw) / 2, y: (ph - dh) / 2, width: dw, height: dh))
            }
        }
        if cancelled?() == true { return (nil, failed) }
        let tmp = fm.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".pdf")
        guard (try? data.write(to: tmp)) != nil else { return (nil, failed) }
        defer { try? fm.removeItem(at: tmp) }
        let n = name.trimmingCharacters(in: .whitespaces).isEmpty ? L("ملاحظة من الصور", "Photo note") : name
        guard var e = importPDF(from: tmp, name: n, folder: folder) else { return (nil, failed) }
        e.isNote = true
        return (e, failed)
    }

    /// يقرأ صورة لتصبح صفحة: باتجاهها الصحيح، أطول ضلع لها imagePageMax على الأكثر، ومضغوطة JPEG
    /// (الشفافية تصير بيضاء). الصورة الناتجة مبنية على بيانات JPEG فيضمّنها ملف PDF كما هي.
    static func pageJPEG(_ data: Data) -> (CGImage, CGSize)? {
        guard let src = UIImage(data: data), src.size.width > 0, src.size.height > 0 else { return nil }
        let px = CGSize(width: src.size.width * src.scale, height: src.size.height * src.scale)
        let k = min(1, imagePageMax / max(px.width, px.height))
        let out = CGSize(width: max(1, (px.width * k).rounded()), height: max(1, (px.height * k).rounded()))
        let fmt = UIGraphicsImageRendererFormat()
        fmt.scale = 1
        fmt.opaque = true
        // الرسم يطبّق اتجاه الصورة (EXIF) ويصغّرها ويجعل الشفافية بيضاء
        let flat = UIGraphicsImageRenderer(size: out, format: fmt).image { c in
            UIColor.white.setFill()
            c.fill(CGRect(origin: .zero, size: out))
            src.draw(in: CGRect(origin: .zero, size: out))
        }
        guard let jpeg = flat.jpegData(compressionQuality: 0.9),
              let provider = CGDataProvider(data: jpeg as CFData),
              let cg = CGImage(jpegDataProviderSource: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent)
        else { return nil }
        return (cg, out)
    }

    /// يجمع عدة ملفات PDF في مستند واحد بترتيبها (كما في أندرويد).
    /// progress(رقم الملف، النسبة، المرحلة). يعيد (المستند أو nil، عدد الملفات التي تعذرت). خارج الخيط الرئيسي.
    func importMerged(_ urls: [URL], name: String, folder: String,
                      progress: ((Int, Double, String) -> Void)? = nil, cancelled: (() -> Bool)? = nil) -> (DocEntry?, Int) {
        let merged = PDFDocument()
        var failed = 0
        var firstName: String? = nil
        for (i, u) in urls.enumerated() {
            if cancelled?() == true { return (nil, failed) }
            progress?(i, 0.3, L("قراءة الصفحات…", "Reading the pages…"))
            let scoped = u.startAccessingSecurityScopedResource()
            defer { if scoped { u.stopAccessingSecurityScopedResource() } }
            guard let doc = PDFDocument(url: u), doc.pageCount > 0, !doc.isLocked else { failed += 1; continue }
            if firstName == nil { firstName = u.deletingPathExtension().lastPathComponent }
            for k in 0..<doc.pageCount {
                if let page = doc.page(at: k)?.copy() as? PDFPage { merged.insert(page, at: merged.pageCount) }
            }
            progress?(i, 1, L("تم", "Done"))
        }
        guard merged.pageCount > 0, cancelled?() != true else { return (nil, failed) }
        let tmp = fm.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".pdf")
        guard merged.write(to: tmp) else { return (nil, failed) }
        defer { try? fm.removeItem(at: tmp) }
        let n = name.trimmingCharacters(in: .whitespaces).isEmpty ? (firstName ?? L("مستند", "Document")) : name
        let last = max(0, urls.count - 1)
        let e = importPDF(from: tmp, name: n, folder: folder, progress: { f, ph in progress?(last, f, ph) }, cancelled: cancelled)
        return (e, failed)
    }

    /// ملاحظة جديدة: صفحة واحدة بالقالب المختار.
    func createNote(name: String, choice: PaperChoice, folder: String) -> DocEntry? {
        let id = UUID().uuidString
        try? fm.createDirectory(at: dir(id), withIntermediateDirectories: true)
        let doc = PDFDocument()
        guard let page = Templates.makePage(kind: choice.kind, bg: choice.color, size: choice.points) else { return nil }
        doc.insert(page, at: 0)
        guard doc.write(to: pdfURL(id)) else { return nil }
        var data = DocData()
        data.pages = [PageInfo(id: UUID().uuidString, src: Templates.src(choice.kind, choice.color))]
        saveData(id, data)
        return DocEntry(id: id, name: name, sizeBytes: fileSize(id), pageCount: 1, addedAt: nowMs(), isNote: true, folder: folder)
    }

    /// خريطة ذهنية مستقلة: بلا صفحات.
    func createMap(name: String, folder: String) -> DocEntry {
        let id = UUID().uuidString
        try? fm.createDirectory(at: dir(id), withIntermediateDirectories: true)
        var data = DocData()
        data.map = MindMapData(title: name)
        saveData(id, data)
        // ملف PDF من صفحة واحدة يبقي المستند صالحًا للتصدير والنسخ
        if let p = Templates.makePage(kind: "plain", bg: 0xFFFFFFFF, size: CGSize(width: 595, height: 842)) {
            let d = PDFDocument(); d.insert(p, at: 0); d.write(to: pdfURL(id))
        }
        return DocEntry(id: id, name: name, pageCount: 0, addedAt: nowMs(), folder: folder, isMap: true)
    }

    func duplicate(_ e: DocEntry, newName: String) -> DocEntry? {
        let id = UUID().uuidString
        do { try fm.copyItem(at: dir(e.id), to: dir(id)) } catch { return nil }
        var n = e
        n.id = id
        n.name = newName
        n.addedAt = nowMs()
        n.lastOpened = 0
        n.readMs = 0
        return n
    }

    func delete(_ id: String) {
        try? fm.removeItem(at: dir(id))
        invalidateThumb(id)
    }

    // ---------- الصور المصغّرة ----------

    private var thumbsDir: URL {
        let u = root.appendingPathComponent("thumbs", isDirectory: true)
        try? fm.createDirectory(at: u, withIntermediateDirectories: true)
        return u
    }

    func invalidateThumb(_ id: String) {
        thumbCache.removeObject(forKey: id as NSString)
        try? fm.removeItem(at: thumbsDir.appendingPathComponent(id + ".jpg"))
    }

    /// الصفحة الأولى مصغّرة (مع الحبر).
    func thumbnail(_ id: String, width: CGFloat = 320) -> UIImage? {
        if let c = thumbCache.object(forKey: id as NSString) { return c }
        let file = thumbsDir.appendingPathComponent(id + ".jpg")
        if let img = UIImage(contentsOfFile: file.path) {
            thumbCache.setObject(img, forKey: id as NSString)
            return img
        }
        guard let pdf = PDFDocument(url: pdfURL(id)), let page = pdf.page(at: 0) else { return nil }
        let data = loadData(id)
        let b = page.bounds(for: .mediaBox)
        let k = width / max(1, b.width)
        let size = CGSize(width: width, height: b.height * k)
        let img = UIGraphicsImageRenderer(size: size).image { ctx in
            UIColor.white.setFill()
            ctx.fill(CGRect(origin: .zero, size: size))
            let c = ctx.cgContext
            c.saveGState()
            c.translateBy(x: 0, y: size.height)
            c.scaleBy(x: k, y: -k)
            page.draw(with: .mediaBox, to: c)
            c.restoreGState()
            if let pid = data.pages.first?.id {
                let dr = loadInk(id, page: pid)
                if !dr.strokes.isEmpty {
                    let im = dr.lightImage(from: CGRect(origin: .zero, size: b.size), scale: k * 2)
                    im.draw(in: CGRect(origin: .zero, size: size))
                }
            }
        }
        if let jpg = img.jpegData(compressionQuality: 0.8) { try? jpg.write(to: file) }
        thumbCache.setObject(img, forKey: id as NSString)
        return img
    }

    /// ملفات مؤقتة قديمة (نسخ الاستيراد والتحويل والتصدير) تُحذف عند التشغيل بعد يوم من إنشائها.
    static func cleanTemp() {
        let fm = FileManager.default
        let tmp = fm.temporaryDirectory
        let limit = Date().addingTimeInterval(-24 * 3600)
        func old(_ u: URL) -> Bool {
            let d = (try? u.resourceValues(forKeys: [.contentModificationDateKey]))?.contentModificationDate
            return (d ?? Date()) < limit
        }
        for u in (try? fm.contentsOfDirectory(at: tmp, includingPropertiesForKeys: [.contentModificationDateKey])) ?? [] {
            let name = u.lastPathComponent
            if name == "export" || name == "share" {
                for f in (try? fm.contentsOfDirectory(at: u, includingPropertiesForKeys: [.contentModificationDateKey])) ?? [] where old(f) {
                    try? fm.removeItem(at: f)
                }
            } else if (name.hasPrefix("convert-") || u.pathExtension.lowercased() == "pdf") && old(u) {
                try? fm.removeItem(at: u)
            }
        }
    }

    /// ملفات حبر لصفحات لم تعد في المستند (حُذفت في جلسة سابقة).
    func removeOrphanInk(_ id: String, keep: Set<String>) {
        let folder = dir(id).appendingPathComponent("ink", isDirectory: true)
        let files = (try? fm.contentsOfDirectory(atPath: folder.path)) ?? []
        // لا ملف حبر يطابق أي صفحة حالية: الأرجح أن معرّفات الصفحات هي التي تغيّرت (بيانات ضاعت)،
        // لا أن كل صفحات الحبر حُذفت، فلا يُمسح شيء
        guard files.contains(where: { keep.contains($0) }) else { return }
        for f in files where !keep.contains(f) && !f.contains(".unreadable-") {
            try? fm.removeItem(at: folder.appendingPathComponent(f))
        }
    }

    // ---------- الفهرس الداخلي للـ PDF ----------

    static func outline(of pdf: PDFDocument) -> [OutlineEntry] {
        var out: [OutlineEntry] = []
        func walk(_ o: PDFOutline, _ level: Int) {
            for i in 0..<o.numberOfChildren {
                guard let ch = o.child(at: i) else { continue }
                if let p = ch.destination?.page {
                    out.append(OutlineEntry(title: ch.label ?? "", level: level, page: pdf.index(for: p)))
                }
                walk(ch, level + 1)
            }
        }
        if let r = pdf.outlineRoot { walk(r, 0) }
        return out
    }
}
