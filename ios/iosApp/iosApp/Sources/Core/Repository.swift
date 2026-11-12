import Foundation
import PDFKit
import PencilKit
import UIKit

/// تخزين المكتبة على الجهاز:
///   Documents/Hamish/library.json           فهرس المستندات
///   Documents/Hamish/docs/<id>/doc.pdf      المستند
///   Documents/Hamish/docs/<id>/data.json    التمييز والنصوص والصور والعلامات
///   Documents/Hamish/docs/<id>/ink/<page>   حبر كل صفحة (PencilKit)
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

    func loadLibrary() -> [DocEntry] {
        guard let d = try? Data(contentsOf: libraryURL) else { return [] }
        // كل مستند يُقرأ وحده: مدخل تالف لا يُضيع المكتبة كلها
        guard let items = try? dec.decode([Lossy<DocEntry>].self, from: d) else {
            keepUnreadable(libraryURL)
            return []
        }
        let list = items.compactMap { $0.value }
        if list.count != items.count { keepUnreadable(libraryURL) }
        return list.filter { fm.fileExists(atPath: pdfURL($0.id).path) || $0.isMap }
    }

    /// ملف تعذرت قراءته: تبقى منه نسخة بجانبه قبل أن يُكتب فوقه، فلا تضيع البيانات بصمت.
    private func keepUnreadable(_ url: URL) {
        guard fm.fileExists(atPath: url.path) else { return }
        let copy = url.deletingPathExtension().appendingPathExtension("unreadable-\(nowMs()).json")
        try? fm.copyItem(at: url, to: copy)
    }

    func saveLibrary(_ list: [DocEntry]) {
        if let d = try? enc.encode(list) { try? d.write(to: libraryURL, options: .atomic) }
    }

    func loadJSON<T: Decodable>(_ name: String, _ type: T.Type) -> T? {
        let url = root.appendingPathComponent(name)
        guard let d = try? Data(contentsOf: url) else { return nil }
        if let v = try? dec.decode(T.self, from: d) { return v }
        keepUnreadable(url)
        return nil
    }

    /// قائمة تُقرأ عنصرًا عنصرًا: العنصر التالف يُتخطى وحده.
    func loadList<T: Decodable>(_ name: String, _ type: T.Type) -> [T] {
        let url = root.appendingPathComponent(name)
        guard let d = try? Data(contentsOf: url) else { return [] }
        guard let items = try? dec.decode([Lossy<T>].self, from: d) else {
            keepUnreadable(url)
            return []
        }
        let list = items.compactMap { $0.value }
        if list.count != items.count { keepUnreadable(url) }
        return list
    }

    func saveJSON<T: Encodable>(_ name: String, _ value: T) {
        if let d = try? enc.encode(value) { try? d.write(to: root.appendingPathComponent(name), options: .atomic) }
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

    func loadData(_ id: String) -> DocData {
        let url = dir(id).appendingPathComponent("data.json")
        guard let d = try? Data(contentsOf: url) else { return DocData() }
        if let v = try? dec.decode(DocData.self, from: d) { return v }
        keepUnreadable(url)
        return DocData()
    }

    /// يعدّل بيانات مستند غير مفتوح في تبويب. لا يكتب شيئًا إن وُجد الملف وتعذرت قراءته.
    @discardableResult
    func updateData(_ id: String, _ f: (inout DocData) -> Void) -> Bool {
        let url = dir(id).appendingPathComponent("data.json")
        var data = DocData()
        if let d = try? Data(contentsOf: url) {
            guard let v = try? dec.decode(DocData.self, from: d) else { return false }
            data = v
        }
        f(&data)
        saveData(id, data)
        return true
    }

    func saveData(_ id: String, _ data: DocData) {
        try? fm.createDirectory(at: dir(id), withIntermediateDirectories: true)
        if let d = try? enc.encode(data) { try? d.write(to: dir(id).appendingPathComponent("data.json"), options: .atomic) }
    }

    func loadInk(_ id: String, page: String) -> PKDrawing {
        let u = dir(id).appendingPathComponent("ink/\(page)")
        guard let d = try? Data(contentsOf: u), let dr = try? PKDrawing(data: d) else { return PKDrawing() }
        return dr
    }

    func saveInk(_ id: String, page: String, _ drawing: PKDrawing) {
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
    func importPDF(from url: URL, name: String? = nil, folder: String = "") -> DocEntry? {
        let id = UUID().uuidString
        try? fm.createDirectory(at: dir(id), withIntermediateDirectories: true)
        do {
            try fm.copyItem(at: url, to: pdfURL(id))
        } catch {
            guard let d = try? Data(contentsOf: url) else { return nil }
            try? d.write(to: pdfURL(id))
        }
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
        n.locked = false
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
