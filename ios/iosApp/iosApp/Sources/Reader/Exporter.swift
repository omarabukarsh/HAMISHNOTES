import UIKit
import PDFKit
import SwiftUI
import CryptoKit

/// التصدير: نسخة PDF مع كل ما كُتب عليها، وملخصات بصيغة PDF.
enum Exporter {
    private static var outDir: URL {
        let u = FileManager.default.temporaryDirectory.appendingPathComponent("export", isDirectory: true)
        try? FileManager.default.createDirectory(at: u, withIntermediateDirectories: true)
        return u
    }

    /// نسخة نهائية: كل صفحة مع التمييز والصور والنصوص والكتابة. تعمل في الخلفية؛ progress(الصفحة، العدد).
    static func annotatedPDF(_ docId: String, name: String, suffix: String = L(" - نهائي", " - final"),
                             progress: ((Int, Int) -> Void)? = nil) -> URL? {
        let repo = Repository.shared
        guard let pdf = PDFDocument(url: repo.pdfURL(docId)), !pdf.isLocked else { return nil }
        let data = repo.loadData(docId)
        let url = outDir.appendingPathComponent(safeFileName(name) + suffix + ".pdf")
        try? FileManager.default.removeItem(at: url)
        let renderer = UIGraphicsPDFRenderer(bounds: CGRect(x: 0, y: 0, width: 595, height: 842))
        let n = pdf.pageCount
        do {
            try renderer.writePDF(to: url) { ctx in
                for i in 0..<n {
                    autoreleasepool {
                        guard let page = pdf.page(at: i) else { return }
                        let b = page.bounds(for: .cropBox)
                        let rotated = page.rotation % 180 != 0
                        let size = rotated ? CGSize(width: b.height, height: b.width) : b.size
                        ctx.beginPage(withBounds: CGRect(origin: .zero, size: size), pageInfo: [:])
                        let c = ctx.cgContext
                        c.saveGState()
                        c.translateBy(x: 0, y: size.height)
                        c.scaleBy(x: 1, y: -1)
                        page.draw(with: .cropBox, to: c)
                        c.restoreGState()
                        let pid = i < data.pages.count ? data.pages[i].id : ""
                        drawOverlay(c, size: size, pid: pid, data: data, docId: docId)
                        progress?(i + 1, n)
                    }
                }
            }
        } catch { return nil }
        return url
    }

    /// نسخة قابلة للتعديل بالصيغة الموحدة (v2) مع تطبيق أندرويد: تظهر الكتابة في أي تطبيق، وداخل الملف مرفقات قياسية:
    /// الأصل النظيف، والبيانات بمخطط أندرويد (الحبر نقاط)، والصور، ومعها حبر الآيباد وبياناته
    /// ليعود الملف هنا بلا فقد. nil = تعذّر (تُصنع نسخة نهائية).
    static func editablePDF(_ docId: String, name: String, progress: ((Int, Int) -> Void)? = nil) -> URL? {
        guard let url = annotatedPDF(docId, name: name, suffix: "", progress: progress) else { return nil }
        let repo = Repository.shared
        let fm = FileManager.default
        guard let orig = try? Data(contentsOf: repo.pdfURL(docId)),
              let dj = try? Data(contentsOf: repo.dir(docId).appendingPathComponent("data.json")),
              let pdf = PDFDocument(url: repo.pdfURL(docId)) else { return nil }
        let data = repo.loadData(docId)
        let widths = (0..<pdf.pageCount).map { AndroidSchema.pageSize(pdf, $0).width }
        let raw = (try? Data(contentsOf: repo.dir(docId).appendingPathComponent(EditablePDF.androidRaw))).flatMap { AJSON.parse($0) }
        let media = repo.mediaDir(docId)
        let ex = AndroidSchema.export(data, widths: widths, ink: { repo.loadInk(docId, page: $0) }, raw: raw) { item in
            if item.file.hasPrefix("sticker:") { return image(docId, item)?.pngData() }
            guard Repository.safeName(item.file) else { return nil }
            return try? Data(contentsOf: media.appendingPathComponent(item.file))
        }
        var files: [(name: String, data: Data)] = [
            (EditablePDF.dataName, ex.json),
            // counts فارغة: نسخ أندرويد القديمة (1.0.271) ترفض الاستعادة وتفتح الملف نسخة نهائية
            (EditablePDF.metaName, Data("{\"v\":2,\"counts\":[]}".utf8)),
            (EditablePDF.origName, orig),
            (EditablePDF.iosData, dj),
            (EditablePDF.iosMeta, Data("{\"v\":1,\"sha256\":\"\(EditablePDF.sha256(ex.json))\"}".utf8))
        ]
        for im in ex.images { files.append((EditablePDF.imgPrefix + im.name, im.data)) }
        let inkDir = repo.dir(docId).appendingPathComponent("ink", isDirectory: true)
        for f in (try? fm.contentsOfDirectory(atPath: inkDir.path)) ?? [] where PdfAttachments.isSafe(f) {
            if let d = try? Data(contentsOf: inkDir.appendingPathComponent(f)) { files.append((EditablePDF.iosInk + f, d)) }
        }
        return PdfAttachments.append(to: url, files: files) ? url : nil
    }

    static func image(_ docId: String, _ item: ImageItem) -> UIImage? { DocSession.image(docId: docId, item: item) }

    /// يرسم ما فوق الصفحة (الملء، التمييز، الصور، الملاحظات، النصوص، الحبر) بإحداثيات الصفحة.
    /// ink: حبر الصفحة إن كان في اليد (وإلا يُقرأ من الملف). inkScale: دقة صورة الحبر.
    static func drawOverlay(_ c: CGContext, size: CGSize, pid: String, data: DocData, docId: String,
                            ink: InkDrawing? = nil, inkScale: CGFloat = 3) {
        // الحبر يُرسم بألوانه الأصلية دائمًا (لا يتأثر بالمظهر الداكن)
        UITraitCollection(userInterfaceStyle: .light).performAsCurrent {
            drawOverlayItems(c, size: size, pid: pid, data: data, docId: docId, ink: ink, inkScale: inkScale)
        }
    }

    private static func drawOverlayItems(_ c: CGContext, size: CGSize, pid: String, data: DocData, docId: String,
                                         ink given: InkDrawing?, inkScale: CGFloat) {
        for f in data.fills where f.page == pid && f.pts.count > 2 {
            c.saveGState()
            c.setFillColor(UIColor(argb: f.color).withAlphaComponent(f.alpha).cgColor)
            c.addLines(between: f.pts)
            c.closePath()
            c.fillPath()
            c.restoreGState()
        }
        c.saveGState()
        c.setBlendMode(.multiply)
        for m in data.marks where m.page == pid && !m.free {
            c.setFillColor(markColor(m.color, strong: m.level >= 2).cgColor)
            for r in m.rects { c.fill(r.scaled(size.width)) }
        }
        c.restoreGState()
        for im in data.images where im.page == pid {
            image(docId, im)?.draw(in: CGRect(x: im.x, y: im.y, width: im.w, height: im.w / max(0.01, im.aspect)))
        }
        for n in data.notes where n.page == pid {
            let r = n.collapsed ? CGRect(x: n.x, y: n.y, width: 18, height: 18) : CGRect(x: n.x, y: n.y, width: n.w, height: n.h)
            c.setFillColor(UIColor(argb: n.color).cgColor)
            c.fill(r)
            if !n.collapsed && !n.text.isEmpty {
                (n.text as NSString).draw(in: r.insetBy(dx: n.w * 0.06, dy: n.w * 0.06), withAttributes: [
                    .font: Fonts.ui(max(6, n.w * 0.075)), .foregroundColor: UIColor(argb: 0xFF1F2933)
                ])
            }
            if !n.collapsed, let d = n.ink, let ink = InkDrawing.load(d), !ink.strokes.isEmpty {
                c.saveGState()
                c.clip(to: r)
                c.translateBy(x: n.x, y: n.y)
                ink.draw(in: c)
                c.restoreGState()
            }
        }
        for b in data.boxes where b.page == pid {
            let p = NSMutableParagraphStyle()
            p.baseWritingDirection = .natural
            p.alignment = b.align == 1 ? .center : (b.align == 2 ? (b.text.unicodeScalars.contains { $0.value >= 0x0600 && $0.value <= 0x06FF } ? .left : .right) : .natural)
            var font = b.font.isEmpty ? Fonts.ui(b.size) : (UIFont(name: b.font, size: b.size) ?? Fonts.ui(b.size))
            var traits: UIFontDescriptor.SymbolicTraits = []
            if b.bold { traits.insert(.traitBold) }
            if b.italic { traits.insert(.traitItalic) }
            if !traits.isEmpty, let d = font.fontDescriptor.withSymbolicTraits(traits) { font = UIFont(descriptor: d, size: b.size) }
            var attrs: [NSAttributedString.Key: Any] = [
                .font: font, .foregroundColor: UIColor(argb: b.linkDoc != nil ? 0xFF1B365D : b.color), .paragraphStyle: p
            ]
            if b.underline { attrs[.underlineStyle] = NSUnderlineStyle.single.rawValue }
            (b.text as NSString).draw(in: CGRect(x: b.x, y: b.y, width: b.w, height: size.height - b.y), withAttributes: attrs)
        }
        let ink = given ?? Repository.shared.loadInk(docId, page: pid)
        // الحبر متجهات (حاد بأي تكبير)، والهايلايتر وملء الأشكال تحته ممزوجان مع الصفحة
        if !ink.strokes.isEmpty {
            c.saveGState()
            ink.draw(in: c)
            c.restoreGState()
        }
    }

    /// أجزاء ملخص مستند من ملفه المحفوظ (بلا فتحه في جلسة).
    static func summaryBlocks(_ docId: String, tidy: Bool) -> [SBlock] {
        let data = Repository.shared.loadData(docId)
        var order: [String: Int] = [:]
        for (i, p) in data.pages.enumerated() { order[p.id] = i }
        let marks = data.marks.filter { $0.inSummary && !$0.free }.sorted { a, b in
            let ia = order[a.page] ?? 0, ib = order[b.page] ?? 0
            if ia != ib { return ia < ib }
            let ta = a.rects.map { $0.t }.min() ?? 0, tb = b.rects.map { $0.t }.min() ?? 0
            if ta != tb { return ta < tb }
            return (a.rects.map { $0.l }.min() ?? 0) < (b.rects.map { $0.l }.min() ?? 0)
        }
        return Summary.view(marks, tidy: tidy, edits: data.summaryEdits ?? SummaryEdits())
    }

    /// ملخص مستند محفوظ (للمشاركة من المكتبة). يُستدعى على الخيط الرئيسي.
    static func summaryPDF(docId: String, name: String) -> URL? {
        let blocks = summaryBlocks(docId, tidy: AppController.shared.summaryTidy)
        guard !blocks.isEmpty else { return nil }
        return renderHTML("<h1>\(esc(name))</h1>" + html(blocks), name: L("ملخص ", "Summary ") + safeFileName(name))
    }

    /// ملخص مادة كاملة: كل مستنداتها بالترتيب. (المستندات المفتوحة تُحفظ أولًا.)
    static func folderSummary(_ folder: String) -> URL? {
        let app = AppController.shared
        app.tabs.forEach { $0.flush() }
        var body = "<h1>\(esc(L("ملخص مادة", "Subject summary"))): \(esc(folder))</h1>"
        var any = false
        for e in app.library.filter({ $0.folder == folder && !$0.isMap && !$0.locked }).sorted(by: { $0.name < $1.name }) {
            let blocks = summaryBlocks(e.id, tidy: app.summaryTidy)
            if blocks.isEmpty { continue }
            any = true
            body += "<h2>\(esc(e.name))</h2>" + html(blocks)
        }
        guard any else { return nil }
        return renderHTML(body, name: L("ملخص مادة ", "Subject summary ") + safeFileName(folder))
    }

    private static func html(_ blocks: [SBlock]) -> String {
        blocks.map { b -> String in
            switch b {
            case .heading(let t, _, _): return "<h3>\(esc(t))</h3>"
            case .para(let t, let e, _, _):
                var out = ""
                var i = t.startIndex
                for r in Summary.emphasisRanges(t, e) where r.lowerBound >= i {
                    out += esc(String(t[i..<r.lowerBound])) + "<b class='em'>" + esc(String(t[r])) + "</b>"
                    i = r.upperBound
                }
                out += esc(String(t[i...]))
                return "<p>\(out)</p>"
            case .table(let rows, _, _, let header):
                let cols = rows.map { $0.count }.max() ?? 1
                let trs = rows.enumerated().map { ri, r -> String in
                    let tag = header && ri == 0 ? "th" : "td"
                    return "<tr>" + (0..<cols).map { "<\(tag)>\(esc($0 < r.count ? r[$0] : ""))</\(tag)>" }.joined() + "</tr>"
                }.joined()
                return "<table>\(trs)</table>"
            }
        }.joined()
    }

    private static func esc(_ s: String) -> String {
        s.replacingOccurrences(of: "&", with: "&amp;").replacingOccurrences(of: "<", with: "&lt;").replacingOccurrences(of: ">", with: "&gt;")
    }

    private static func renderHTML(_ body: String, name: String) -> URL? {
        let dir = I18n.english ? "ltr" : "rtl"
        let html = """
        <html dir='\(dir)'><head><meta charset='utf-8'><style>
        body { font-family: 'Almarai', -apple-system; font-size: 13pt; color: #1A2230; line-height: 1.7; }
        h1 { color: #1B365D; font-size: 22pt; border-bottom: 2px solid #C9A44C; padding-bottom: 6px; }
        h2 { color: #1B365D; font-size: 17pt; margin-top: 22px; }
        h3 { color: #1B365D; font-size: 15pt; }
        .pg { color: #8C6A1F; font-size: 10pt; margin-top: 16px; }
        p { margin: 8px 0; }
        b.em { font-weight: 800; background: #F4E9CB; }
        table { border-collapse: collapse; width: 100%; margin: 6px 0; font-size: 10pt; }
        th, td { border: 0.5px solid #E4DFD3; padding: 4px 6px; vertical-align: top; }
        th { background: #E8EDF5; }
        </style></head><body>\(body)</body></html>
        """
        let fmt = UIMarkupTextPrintFormatter(markupText: html)
        let r = UIPrintPageRenderer()
        r.addPrintFormatter(fmt, startingAtPageAt: 0)
        let pageRect = CGRect(x: 0, y: 0, width: 595, height: 842)
        r.setValue(pageRect, forKey: "paperRect")
        r.setValue(pageRect.insetBy(dx: 44, dy: 50), forKey: "printableRect")
        let data = NSMutableData()
        UIGraphicsBeginPDFContextToData(data, pageRect, nil)
        r.prepare(forDrawingPages: NSRange(location: 0, length: r.numberOfPages))
        for i in 0..<r.numberOfPages {
            UIGraphicsBeginPDFPage()
            r.drawPage(at: i, in: UIGraphicsGetPDFContextBounds())
        }
        UIGraphicsEndPDFContext()
        let url = outDir.appendingPathComponent(name + ".pdf")
        do { try data.write(to: url) } catch { return nil }
        return url
    }
}

/// النسخة القابلة للتعديل: الأصل والبيانات والحبر داخل ملف PDF نفسه (كائن مضاف بتحديث تراكمي سليم).
enum EditablePDF {
    private static let head = "/Type /HamishEditable /Length "
    private static let open = " >>\nstream\n"

    static func append(to url: URL, payload: [String: Data]) -> Bool {
        guard let file = try? Data(contentsOf: url),
              let blob = try? PropertyListSerialization.data(fromPropertyList: payload, format: .binary, options: 0) else { return false }
        let from = max(0, file.count - 8192)
        guard let tail = String(data: file.subdata(in: from..<file.count), encoding: .isoLatin1),
              let sx = tail.range(of: "startxref", options: .backwards),
              let tr = tail.range(of: "trailer", options: .backwards, range: tail.startIndex..<sx.lowerBound) else { return false }
        let dict = String(tail[tr.upperBound..<sx.lowerBound])
        let prevDigits = tail[sx.upperBound...].drop { !$0.isNumber }.prefix { $0.isNumber }
        guard let prev = Int(prevDigits), let size = firstInt(dict, "/Size\\s+(\\d+)"),
              let root = firstMatch(dict, "/Root\\s+(\\d+\\s+\\d+\\s+R)") else { return false }
        let info = firstMatch(dict, "/Info\\s+(\\d+\\s+\\d+\\s+R)").map { " /Info " + $0 } ?? ""
        var out = Data()
        func add(_ s: String) { out.append(s.data(using: .isoLatin1) ?? Data()) }
        add("\n")
        let objOffset = file.count + out.count
        add("\(size) 0 obj\n<< " + head + "\(blob.count)" + open)
        out.append(blob)
        add("\nendstream\nendobj\n")
        let xrefOffset = file.count + out.count
        add("xref\n\(size) 1\n" + String(format: "%010d 00000 n \n", objOffset))
        add("trailer\n<< /Size \(size + 1) /Root \(root)\(info) /Prev \(prev) >>\nstartxref\n\(xrefOffset)\n%%EOF\n")
        guard let h = try? FileHandle(forWritingTo: url) else { return false }
        defer { try? h.close() }
        h.seekToEndOfFile()
        h.write(out)
        return true
    }

    /// ما أُرفق داخل الملف، إن كان نسخة قابلة للتعديل.
    static func extract(_ url: URL) -> [String: Data]? {
        guard let file = try? Data(contentsOf: url, options: .mappedIfSafe),
              let r = file.range(of: Data(head.utf8), options: .backwards) else { return nil }
        var i = r.upperBound
        var len = 0
        while i < file.count, file[i] >= 48, file[i] <= 57 { len = len * 10 + Int(file[i] - 48); i += 1 }
        let o = Data(open.utf8)
        guard len > 0, file.count >= i + o.count + len, file.subdata(in: i..<(i + o.count)) == o else { return nil }
        let blob = file.subdata(in: (i + o.count)..<(i + o.count + len))
        return (try? PropertyListSerialization.propertyList(from: blob, options: [], format: nil)) as? [String: Data]
    }

    // ---------- الصيغة الموحدة (v2): مرفقات PDF قياسية يقرؤها أندرويد والآيباد ----------
    static let origName = "hamish-orig.pdf"
    static let dataName = "hamish-data.json"
    static let metaName = "hamish-meta.json"
    static let imgPrefix = "hamish-img-"
    /// مرفقات الآيباد: بياناته وحبره، وبصمة hamish-data.json كما كتبها (إن تغيّرت فقد عُدّل الملف في أندرويد).
    static let iosData = "hamish-ios-data.json"
    static let iosMeta = "hamish-ios-meta.json"
    static let iosInk = "hamish-ios-ink-"
    /// ملف أندرويد الخام بجانب المستند المستورد منه: ما لا يفهمه الآيباد يعاد كما هو عند التصدير.
    static let androidRaw = "android.json"

    static func sha256(_ d: Data) -> String { SHA256.hash(data: d).map { String(format: "%02x", $0) }.joined() }

    /// يعيد المستند المستورد إلى أصله مع بياناته وحبره وصوره. true = كان نسخة قابلة للتعديل.
    static func restore(pdf: URL, into dir: URL) -> Bool {
        if let files = PdfAttachments.read(pdf), files[origName] != nil, files[dataName] != nil {
            return restoreUnified(files, pdf: pdf, into: dir)
        }
        return restoreLegacy(pdf: pdf, into: dir)
    }

    /// الصيغة الموحدة: الأصل المرفق يصير هو المستند. ملف الآيباد الذي لم يُعدَّل خارجه يعود بحبره كما هو،
    /// وغيره (ملف أندرويد، أو ملف آيباد عُدّل في أندرويد) يُبنى حبره من النقاط.
    private static func restoreUnified(_ files: [String: Data], pdf: URL, into dir: URL) -> Bool {
        guard let orig = files[origName], let dj = files[dataName],
              let doc = PDFDocument(data: orig), doc.pageCount > 0 else { return false }
        let fm = FileManager.default
        let inkDir = dir.appendingPathComponent("ink", isDirectory: true)
        let mediaDir = dir.appendingPathComponent("media", isDirectory: true)
        let ios = files[iosData]
        let stamp = files[iosMeta].flatMap { AJSON.parse($0) }?["sha256"] as? String
        let untouched = ios != nil && stamp == sha256(dj)

        var dataFile: Data
        var inkFiles: [String: Data] = [:]
        if untouched, let ios = ios {
            dataFile = ios
            for (k, v) in files where k.hasPrefix(iosInk) {
                let name = String(k.dropFirst(iosInk.count))
                if PdfAttachments.isSafe(name) && Repository.safeName(name) { inkFiles[name] = v }
            }
            // معرّفات الصفحات وأسماء الصور تصير أسماء ملفات: ما ليس آمنًا منها يُرفض (ملف معدّل)
            if let d = try? JSONDecoder().decode(DocData.self, from: ios),
               !d.pages.allSatisfy({ Repository.safeName($0.id) }) || !d.images.allSatisfy({ $0.file.hasPrefix("sticker:") ? !$0.file.contains("/") : Repository.safeName($0.file) }) {
                return false
            }
        } else {
            let old = ios.flatMap { try? JSONDecoder().decode(DocData.self, from: $0) }
            let keep = old != nil && old!.pages.count == doc.pageCount && old!.pages.allSatisfy { Repository.safeName($0.id) }
            let pages = keep ? old!.pages : (0..<doc.pageCount).map { _ in PageInfo(id: UUID().uuidString) }
            let widths = (0..<doc.pageCount).map { AndroidSchema.pageSize(doc, $0).width }
            guard let imp = AndroidSchema.importJSON(dj, pages: pages, widths: widths) else { return false }
            var data = imp.data
            // ما لا مقابل له في أندرويد يعود من بيانات الآيباد: الفهرس والخريطة وقوالب الصفحات (في pages)
            data.outline = keep ? old!.outline : Repository.outline(of: doc)
            if keep { data.map = old!.map }
            guard let enc = try? JSONEncoder().encode(data) else { return false }
            dataFile = enc
            for (pid, d) in imp.ink { inkFiles[pid] = d.dataRepresentation() }
        }
        do {
            try orig.write(to: pdf, options: .atomic)
            try dataFile.write(to: dir.appendingPathComponent("data.json"), options: .atomic)
        } catch { return false }
        try? fm.createDirectory(at: inkDir, withIntermediateDirectories: true)
        try? fm.createDirectory(at: mediaDir, withIntermediateDirectories: true)
        for (name, d) in inkFiles where Repository.safeName(name) { try? d.write(to: inkDir.appendingPathComponent(name)) }
        for (k, v) in files where k.hasPrefix(imgPrefix) {
            try? v.write(to: mediaDir.appendingPathComponent(AndroidSchema.safeName(String(k.dropFirst(imgPrefix.count)))))
        }
        try? dj.write(to: dir.appendingPathComponent(androidRaw))
        return true
    }

    /// الصيغة القديمة للآيباد (كائن /HamishEditable): تبقى تُفتح.
    private static func restoreLegacy(pdf: URL, into dir: URL) -> Bool {
        guard let p = extract(pdf), let orig = p["pdf"], let data = p["data"] else { return false }
        let fm = FileManager.default
        do {
            try orig.write(to: pdf, options: .atomic)
            try data.write(to: dir.appendingPathComponent("data.json"), options: .atomic)
        } catch { return false }
        try? fm.createDirectory(at: dir.appendingPathComponent("ink"), withIntermediateDirectories: true)
        try? fm.createDirectory(at: dir.appendingPathComponent("media"), withIntermediateDirectories: true)
        for (k, v) in p where k.hasPrefix("ink/") || k.hasPrefix("media/") {
            let name = (k as NSString).lastPathComponent
            guard !name.isEmpty, name != "..", name != "." else { continue }
            try? v.write(to: dir.appendingPathComponent(k.hasPrefix("ink/") ? "ink" : "media").appendingPathComponent(name))
        }
        return true
    }

    private static func firstMatch(_ s: String, _ pattern: String) -> String? {
        guard let re = try? NSRegularExpression(pattern: pattern),
              let m = re.firstMatch(in: s, range: NSRange(s.startIndex..., in: s)), m.numberOfRanges > 1,
              let r = Range(m.range(at: 1), in: s) else { return nil }
        return String(s[r])
    }

    private static func firstInt(_ s: String, _ pattern: String) -> Int? { firstMatch(s, pattern).flatMap { Int($0) } }
}

/// حفظ ملفات على الجهاز (تطبيق الملفات): ملف واحد أو عدة ملفات في مجلد تختاره.
struct FileSaver: UIViewControllerRepresentable {
    let urls: [URL]
    let done: (Bool) -> Void
    func makeCoordinator() -> C { C(done) }
    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let p = UIDocumentPickerViewController(forExporting: urls, asCopy: true)
        p.delegate = context.coordinator
        return p
    }
    func updateUIViewController(_ uiViewController: UIDocumentPickerViewController, context: Context) {}
    final class C: NSObject, UIDocumentPickerDelegate {
        let done: (Bool) -> Void
        init(_ d: @escaping (Bool) -> Void) { done = d }
        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) { done(true) }
        func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) { done(false) }
    }
}

/// مشاركة أو تصدير (نفس ShareExportDialog وShareManyDialog في أندرويد): نوع النسخة (قابلة للتعديل أو نهائية)،
/// ثم مشاركة أو حفظ على الجهاز، وكذلك الملخصات. التجهيز في الخلفية مع شريط تقدم.
struct ShareExportSheet: View {
    @EnvironmentObject var app: AppController
    let docIds: [String]
    @State private var editable = true
    @State private var busy = false
    @State private var progress: Double = 0
    @State private var status = ""
    @State private var shareItems: [URL]? = nil
    @State private var saveItems: [URL]? = nil
    /// مستندات بالاسم نفسه: التصدير ينتظر اختيار المستخدم.
    @State private var sameNameThen: (([URL]) -> Void)? = nil
    @State private var sameNameAlways = false
    @Environment(\.dismiss) private var dismiss

    private var entries: [DocEntry] { docIds.compactMap { app.entry($0) }.filter { !$0.isMap } }
    private var locked: Int { entries.filter { $0.locked && !app.unlocked.contains($0.id) }.count }

    var body: some View {
        let many = entries.count > 1
        SheetScaffold(title: many ? L("مشاركة أو حفظ \(entries.count) مستندات", "Share or save \(entries.count) documents") : L("مشاركة أو تصدير", "Share or export")) {
            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    let names = entries.prefix(4).map { $0.name }.joined(separator: "، ")
                    Text(names + (entries.count > 4 ? L(" و\(entries.count - 4) غيرها", " and \(entries.count - 4) more") : ""))
                        .font(Fonts.sans(13)).foregroundColor(Palette.textSecondary).lineLimit(2)
                    if many && locked > 0 {
                        Text(L("\(locked) مستند مقفل سيُتخطى", "\(locked) locked document(s) will be skipped"))
                            .font(Fonts.sans(12)).foregroundColor(Palette.danger)
                    }
                    Text(L("نوع النسخة", "Copy type")).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary).padding(.top, 6)
                    copyOption(L("قابلة للتعديل", "Editable"),
                               many ? L("تظهر كتابتك في أي تطبيق، وعند فتحها في especial notes تعود قابلة للتعديل.", "Your writing shows in any app, and becomes editable again in especial notes.")
                                    : L("تظهر كتابتك في أي تطبيق، وعند فتحها في especial notes تعود الكتابة والتمييز والملاحظات قابلة للتعديل.", "Your writing shows in any app, and when opened in especial notes the writing, highlights and notes become editable again."),
                               editable) { editable = true }
                    copyOption(L("نهائية", "Final"),
                               many ? L("الكتابة مثبتة في الصفحات؛ مناسبة لإرسالها للآخرين.", "Writing is fixed into the pages; good for sending to others.")
                                    : L("الكتابة مثبتة في الصفحات ولا يمكن تعديلها؛ مناسبة لإرسالها للآخرين.", "Writing is fixed into the pages and can’t be edited; good for sending to others."),
                               !editable) { editable = false }
                    HStack(spacing: 8) {
                        Button { makeDocs { shareItems = $0 } } label: {
                            HStack(spacing: 6) {
                                PhIcon("ph_export", .white, 18)
                                Text(L("مشاركة", "Share")).font(Fonts.sans(15, .bold)).foregroundColor(.white)
                            }
                            .frame(maxWidth: .infinity).frame(height: 44)
                            .background(RoundedRectangle(cornerRadius: 22).fill(Palette.navy))
                        }
                        Button { makeDocs { saveItems = $0 } } label: {
                            Text(many ? L("حفظ في مجلد", "Save to folder") : L("حفظ على الجهاز", "Save to device"))
                                .font(Fonts.sans(15, .bold)).foregroundColor(Palette.navy)
                                .frame(maxWidth: .infinity).frame(height: 44)
                                .roundedBorder(Palette.navy, radius: 22)
                        }
                    }
                    .padding(.top, 8)
                    .disabled(busy)
                    Text(many ? L("المشاركة ترسل كل الملفات معًا (تلغرام، واتساب، البريد…). الحفظ يضعها في مجلد تختاره.", "Share sends all files together (Telegram, WhatsApp, email…). Save puts them in a folder you choose.")
                              : L("المشاركة تفتح قائمة التطبيقات: تلغرام، واتساب، البريد…", "Share opens the app list: Telegram, WhatsApp, email…"))
                        .font(Fonts.sans(11)).foregroundColor(Palette.textSecondary)
                    if !many && app.summariesOn {
                        Text(L("الملخصات", "Summaries")).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary).padding(.top, 10)
                        HStack(spacing: 4) {
                            DialogButton(label: L("مشاركة الملخصات", "Share summaries")) { makeSummary { shareItems = $0 } }
                            DialogButton(label: L("حفظ الملخصات", "Save summaries")) { makeSummary { saveItems = $0 } }
                        }
                        .disabled(busy)
                    }
                    if let then = sameNameThen {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(L("بعض المستندات المختارة لها الاسم نفسه", "Some of the selected documents have the same name"))
                                .font(Fonts.sans(14, .bold)).foregroundColor(Palette.textPrimary)
                            Text(L("ملفان بالاسم نفسه يكتب أحدهما فوق الآخر. اختر ما تريد:", "Two files with the same name overwrite each other. Choose what to do:"))
                                .font(Fonts.sans(12)).foregroundColor(Palette.textSecondary).fixedSize(horizontal: false, vertical: true)
                            Toggle(isOn: $sameNameAlways) {
                                Text(L("اجعل اختياري دائمًا (يتغير من الإعدادات)", "Always use my choice (change it in Settings)")).font(Fonts.sans(13)).foregroundColor(Palette.textPrimary)
                            }
                            .tint(Palette.navy)
                            HStack(spacing: 4) {
                                DialogButton(label: L("أبقِ الجميع بترقيم الاسم", "Keep all, number the names"), bold: true) { chooseSameName(1, then) }
                                DialogButton(label: L("صدّر الأول فقط", "Export the first only")) { chooseSameName(2, then) }
                                DialogButton(label: L("إلغاء", "Cancel"), color: Palette.textSecondary) { sameNameThen = nil }
                            }
                        }
                        .padding(12)
                        .background(RoundedRectangle(cornerRadius: 12).fill(Palette.goldSoft))
                        .padding(.top, 10)
                    }
                    if busy {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(status).font(Fonts.sans(12.5)).foregroundColor(Palette.navy)
                            ProgressView(value: progress).tint(Palette.navy)
                        }
                        .padding(.top, 10)
                    }
                }
                .padding(.horizontal, 22).padding(.bottom, 20)
            }
        }
        .sheet(item: Binding(get: { shareItems.map { IdURLs(urls: $0) } }, set: { shareItems = $0?.urls })) { u in
            ShareSheet(items: u.urls)
        }
        .sheet(item: Binding(get: { saveItems.map { IdURLs(urls: $0) } }, set: { saveItems = $0?.urls })) { u in
            FileSaver(urls: u.urls) { ok in
                saveItems = nil
                guard ok else { return }
                if u.urls.count > 1 { app.say(.done, L("حُفظ \(u.urls.count) ملفات في المجلد الذي اخترته", "Saved \(u.urls.count) files to the folder you chose")) }
                else if u.urls.first?.lastPathComponent.hasPrefix(L("ملخص ", "Summary ")) == true { app.say(.done, L("حُفظت الملخصات", "Summaries saved")) }
                else { app.say(.done, editable ? L("حُفظت نسخة قابلة للتعديل: افتحها في التطبيق لتعديل الكتابة", "Editable copy saved: open it in the app to edit the writing") : L("حُفظت نسخة نهائية مع التعليقات", "Final copy saved with annotations")) }
                dismiss()
            }
        }
    }

    private func copyOption(_ title: String, _ desc: String, _ selected: Bool, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(alignment: .top, spacing: 10) {
                ZStack {
                    Circle().stroke(selected ? Palette.navy : Palette.textSecondary, lineWidth: 2).frame(width: 20, height: 20)
                    if selected { Circle().fill(Palette.navy).frame(width: 10, height: 10) }
                }
                .padding(.top, 2)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(Fonts.sans(15, .bold)).foregroundColor(Palette.textPrimary)
                    Text(desc).font(Fonts.sans(12)).foregroundColor(Palette.textSecondary).multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            .padding(10)
            .background(RoundedRectangle(cornerRadius: 12).fill(selected ? Palette.navySoft : Color.clear))
            .roundedBorder(selected ? Palette.navy : Palette.divider, radius: 12)
        }
        .buttonStyle(.plain)
        .disabled(busy)
    }

    private func chooseSameName(_ mode: Int, _ then: @escaping ([URL]) -> Void) {
        if sameNameAlways { app.exportSameName = mode }
        sameNameThen = nil
        makeDocs(sameName: mode, then)
    }

    /// يجهّز ملفات PDF في الخلفية (صفحة صفحة مع التقدم) ثم يسلّمها.
    /// sameName: ما يُفعل بمستندات لها الاسم نفسه (nil = حسب الإعداد، ويُسأل المستخدم إن لم يختر بعد).
    private func makeDocs(sameName: Int? = nil, _ then: @escaping ([URL]) -> Void) {
        let all = entries.filter { !($0.locked && !app.unlocked.contains($0.id)) }
        let mode = sameName ?? app.exportSameName
        let bases = all.map { safeFileName($0.name) }
        if Set(bases).count != bases.count && mode == 0 {
            sameNameAlways = false
            sameNameThen = then
            return
        }
        // اسم ملف فريد لكل مستند: المكرر يُرقَّم «اسم (2)» أو يُتخطى حسب الاختيار
        var usedNames = Set<String>()
        var fileNames: [String: String] = [:]
        var list: [DocEntry] = []
        for (e, base) in zip(all, bases) {
            if usedNames.insert(base).inserted {
                fileNames[e.id] = e.name
                list.append(e)
            } else if mode == 1 {
                var n = 2
                while !usedNames.insert(safeFileName("\(e.name) (\(n))")).inserted { n += 1 }
                fileNames[e.id] = "\(e.name) (\(n))"
                list.append(e)
            }
        }
        let sameSkipped = all.count - list.count
        let skipped = entries.count - all.count
        guard !list.isEmpty else {
            app.say(.error, L("افتح قفل المستندات أولًا", "Unlock the documents first"))
            return
        }
        app.tabs.filter { docIds.contains($0.id) }.forEach { $0.flush() }
        busy = true
        progress = 0
        status = L("جارٍ تجهيز PDF…", "Preparing PDF…")
        let wantEditable = editable
        let jobs = list.map { ($0.id, fileNames[$0.id] ?? $0.name) }
        DispatchQueue.global(qos: .userInitiated).async {
            var urls: [URL] = []
            var failed = 0
            var fellBack = false
            for (k, job) in jobs.enumerated() {
                let report: (Int, Int) -> Void = { i, n in
                    DispatchQueue.main.async {
                        progress = (Double(k) + Double(i) / Double(max(1, n))) / Double(jobs.count)
                        status = jobs.count > 1 ? L("جارٍ تجهيز \(k + 1) من \(jobs.count)…", "Preparing \(k + 1) of \(jobs.count)…")
                                                : L("جارٍ تجهيز PDF: صفحة \(i) من \(n)", "Preparing PDF: page \(i) of \(n)")
                    }
                }
                var u: URL? = nil
                if wantEditable {
                    u = Exporter.editablePDF(job.0, name: job.1, progress: report)
                    if u == nil { fellBack = true }
                }
                if u == nil { u = Exporter.annotatedPDF(job.0, name: job.1, progress: report) }
                if let u = u { urls.append(u) } else { failed += 1 }
            }
            DispatchQueue.main.async {
                busy = false
                if fellBack && !urls.isEmpty {
                    app.say(.error, L("تعذر صنع نسخة قابلة للتعديل لهذا الملف؛ صُنعت نسخة نهائية", "Couldn’t make an editable copy of this file; a final copy was made"))
                }
                if failed > 0 {
                    app.say(.error, jobs.count > 1 ? L("تعذر تجهيز \(failed) من الملفات", "Could not prepare \(failed) of the files") : L("تعذر تجهيز ملف PDF", "Could not prepare the PDF"))
                }
                if skipped > 0 { app.say(.done, L("تُخطي \(skipped) مستند مقفل", "Skipped \(skipped) locked document(s)")) }
                if sameSkipped > 0 { app.say(.done, L("تُخطي \(sameSkipped) مستند بالاسم نفسه", "Skipped \(sameSkipped) document(s) with the same name")) }
                if !urls.isEmpty { then(urls) }
            }
        }
    }

    private func makeSummary(_ then: @escaping ([URL]) -> Void) {
        guard let e = entries.first else { return }
        app.tabs.filter { $0.id == e.id }.forEach { $0.flush() }
        busy = true
        progress = 0.5
        status = L("جارٍ تجهيز الملخصات…", "Preparing summaries…")
        DispatchQueue.main.async {
            let u = Exporter.summaryPDF(docId: e.id, name: e.name)
            busy = false
            if let u = u { then([u]) } else { app.say(.error, L("لا توجد ملخصات لحفظها بعد", "No summaries to save yet")) }
        }
    }
}

struct IdURLs: Identifiable {
    let urls: [URL]
    var id: String { urls.map { $0.path }.joined(separator: "|") }
}
