import UIKit
import PDFKit
import PencilKit
import SwiftUI

/// التصدير: نسخة PDF مع كل ما كُتب عليها، وملخصات بصيغة PDF.
enum Exporter {
    private static var outDir: URL {
        let u = FileManager.default.temporaryDirectory.appendingPathComponent("export", isDirectory: true)
        try? FileManager.default.createDirectory(at: u, withIntermediateDirectories: true)
        return u
    }

    /// نسخة نهائية: كل صفحة مع التمييز والصور والنصوص والكتابة.
    static func annotatedPDF(_ docId: String) -> URL? {
        let repo = Repository.shared
        guard let e = AppController.shared.entry(docId), let pdf = PDFDocument(url: repo.pdfURL(docId)) else { return nil }
        let data = repo.loadData(docId)
        let url = outDir.appendingPathComponent(safeFileName(e.name) + L(" - مع التعليقات.pdf", " - annotated.pdf"))
        let session = DocSession(entry: e)
        let renderer = UIGraphicsPDFRenderer(bounds: CGRect(x: 0, y: 0, width: 595, height: 842))
        do {
            try renderer.writePDF(to: url) { ctx in
                for i in 0..<pdf.pageCount {
                    guard let page = pdf.page(at: i) else { continue }
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
                    drawOverlay(c, size: size, pid: pid, data: data, session: session, docId: docId)
                }
            }
        } catch { return nil }
        return url
    }

    /// يرسم ما فوق الصفحة (التمييز، الصور، الملاحظات، النصوص، الحبر) بإحداثيات الصفحة.
    static func drawOverlay(_ c: CGContext, size: CGSize, pid: String, data: DocData, session: DocSession, docId: String) {
        c.saveGState()
        c.setBlendMode(.multiply)
        for m in data.marks where m.page == pid && !m.free {
            c.setFillColor(UIColor(argb: m.color).withAlphaComponent(m.level > 1 ? 0.62 : 0.42).cgColor)
            for r in m.rects { c.fill(r.scaled(size.width)) }
        }
        c.restoreGState()
        for im in data.images where im.page == pid {
            session.image(for: im)?.draw(in: CGRect(x: im.x, y: im.y, width: im.w, height: im.w / max(0.01, im.aspect)))
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
        }
        for b in data.boxes where b.page == pid {
            let p = NSMutableParagraphStyle()
            p.baseWritingDirection = .natural
            (b.text as NSString).draw(in: CGRect(x: b.x, y: b.y, width: b.w, height: size.height - b.y), withAttributes: [
                .font: b.font.isEmpty ? Fonts.ui(b.size) : (UIFont(name: b.font, size: b.size) ?? Fonts.ui(b.size)),
                .foregroundColor: UIColor(argb: b.linkDoc != nil ? 0xFF1B365D : b.color), .paragraphStyle: p
            ])
        }
        let ink = Repository.shared.loadInk(docId, page: pid)
        if !ink.strokes.isEmpty {
            ink.image(from: CGRect(origin: .zero, size: size), scale: 3).draw(in: CGRect(origin: .zero, size: size))
        }
    }

    /// ملخص مستند واحد (PDF): نفس أجزاء درج الملخصات (عناوين، فقرات، جداول، والمهم بخط عريض).
    static func summaryPDF(_ s: DocSession) -> URL? {
        let app = AppController.shared
        let blocks = Summary.view(s.allMarks(), tidy: app.summaryTidy, edits: s.summaryEdits)
        guard !blocks.isEmpty else {
            app.message(L("لا توجد ملخصات لحفظها بعد", "No summaries to save yet"))
            return nil
        }
        return renderHTML("<h1>\(esc(s.title))</h1>" + html(blocks), name: L("ملخص ", "Summary ") + safeFileName(s.title))
    }

    /// ملخص مادة كاملة: كل مستنداتها بالترتيب.
    static func folderSummary(_ folder: String) -> URL? {
        let app = AppController.shared
        var body = "<h1>\(esc(L("ملخص مادة", "Subject summary"))): \(esc(folder))</h1>"
        var any = false
        for e in app.library.filter({ $0.folder == folder && !$0.isMap }).sorted(by: { $0.name < $1.name }) {
            let s = app.tabs.first { $0.id == e.id } ?? DocSession(entry: e)
            let blocks = Summary.view(s.allMarks(), tidy: app.summaryTidy, edits: s.summaryEdits)
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

/// مشاركة أو حفظ: نسخة نهائية مع التعليقات، أو الأصل، أو الملخصات.
struct ShareExportSheet: View {
    @EnvironmentObject var app: AppController
    let docIds: [String]
    @State private var items: [URL]? = nil
    @State private var busy = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        SheetScaffold(title: docIds.count == 1 ? L("مشاركة أو حفظ", "Share or save") : L("مشاركة \(docIds.count) مستندات", "Share \(docIds.count) documents")) {
            VStack(alignment: .leading, spacing: 10) {
                if let urls = items {
                    ShareSheet(items: urls).frame(minHeight: 380)
                } else {
                    option("ph_pen", L("نسخة مع كل ما كتبته", "A copy with everything you wrote"),
                           L("ملف PDF نهائي: الكتابة والتظليل والنصوص والصور مثبتة في الصفحات", "A final PDF: handwriting, highlights, text and images fixed into the pages")) {
                        run { docIds.compactMap { Exporter.annotatedPDF($0) } }
                    }
                    option("ph_books", L("الملف الأصلي", "The original file"), L("كما استوردته، دون الكتابة", "As you imported it, without your writing")) {
                        run {
                            docIds.compactMap { id -> URL? in
                                guard let e = app.entry(id) else { return nil }
                                let u = FileManager.default.temporaryDirectory.appendingPathComponent(safeFileName(e.name) + ".pdf")
                                try? FileManager.default.removeItem(at: u)
                                try? FileManager.default.copyItem(at: Repository.shared.pdfURL(id), to: u)
                                return u
                            }
                        }
                    }
                    option("ph_summary", L("الملخصات", "Summaries"), L("كل ما ظللته مرتبًا في ملف PDF", "Everything you highlighted, neatly in a PDF")) {
                        run {
                            docIds.compactMap { id -> URL? in
                                guard let e = app.entry(id) else { return nil }
                                return Exporter.summaryPDF(app.tabs.first { $0.id == id } ?? DocSession(entry: e))
                            }
                        }
                    }
                    if busy { ProgressView().frame(maxWidth: .infinity).padding() }
                }
            }
            .padding(.horizontal, 22).padding(.bottom, 20)
        }
    }

    private func run(_ make: @escaping () -> [URL]) {
        busy = true
        app.tabs.filter { docIds.contains($0.id) }.forEach { $0.flush() }
        DispatchQueue.main.async {
            let urls = make()
            busy = false
            if urls.isEmpty { app.message(L("لا يوجد ما يُشارك", "Nothing to share")) } else { items = urls }
        }
    }

    private func option(_ icon: String, _ t: String, _ d: String, _ a: @escaping () -> Void) -> some View {
        Button(action: a) {
            HStack(spacing: 14) {
                PhIcon(icon, Palette.navy, 22).frame(width: 44, height: 44).background(RoundedRectangle(cornerRadius: 12).fill(Palette.navySoft))
                VStack(alignment: .leading, spacing: 2) {
                    Text(t).font(Fonts.sans(15, .bold)).foregroundColor(Palette.textPrimary)
                    Text(d).font(Fonts.sans(12)).foregroundColor(Palette.textSecondary)
                }
                Spacer()
            }
            .padding(12)
            .background(RoundedRectangle(cornerRadius: 16).fill(Color.white))
            .roundedBorder(Palette.divider, radius: 16)
        }
        .disabled(busy)
    }
}
