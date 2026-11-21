import UIKit
import WebKit
import UniformTypeIdentifiers

/// عروض الباوربوينت ومستندات Word وKeynote: تُعرض بمحرك الويب في الجهاز ثم تُطبع إلى PDF،
/// فتُضاف إلى المكتبة كأي ملف PDF (في أندرويد تُرسل إلى أداة تحويل خارجية).
final class OfficeConverter: NSObject, WKNavigationDelegate {
    static let exts: Set<String> = ["ppt", "pptx", "pps", "ppsx", "pptm", "key", "doc", "docx", "pages", "rtf", "xls", "xlsx", "numbers"]
    static var types: [UTType] {
        [.pdf, .presentation, .spreadsheet, .rtf] + ["pptx", "ppt", "docx", "doc", "key", "pages", "xlsx", "xls", "numbers"].compactMap { UTType(filenameExtension: $0) }
    }

    static func handles(_ url: URL) -> Bool { exts.contains(url.pathExtension.lowercased()) }

    private var web: WKWebView?
    private var done: ((URL?) -> Void)?
    private var src: URL?
    private static var running: [OfficeConverter] = []

    /// يحوّل الملف إلى PDF مؤقت (على الخيط الرئيسي).
    static func convert(_ url: URL, completion: @escaping (URL?) -> Void) {
        let c = OfficeConverter()
        running.append(c)
        c.start(url) { out in
            running.removeAll { $0 === c }
            completion(out)
        }
    }

    private func start(_ url: URL, completion: @escaping (URL?) -> Void) {
        done = completion
        // نسخة محلية (الملفات من «الملفات» محمية بإذن مؤقت)
        let tmp = FileManager.default.temporaryDirectory.appendingPathComponent("convert-\(UUID().uuidString)", isDirectory: true)
        try? FileManager.default.createDirectory(at: tmp, withIntermediateDirectories: true)
        let local = tmp.appendingPathComponent(url.lastPathComponent)
        let scoped = url.startAccessingSecurityScopedResource()
        try? FileManager.default.copyItem(at: url, to: local)
        if scoped { url.stopAccessingSecurityScopedResource() }
        src = local
        let w = WKWebView(frame: CGRect(x: 0, y: 0, width: 1024, height: 768))
        w.navigationDelegate = self
        web = w
        // يجب أن يكون في نافذة ليُرسم المحتوى
        if let win = UIApplication.shared.connectedScenes.compactMap({ ($0 as? UIWindowScene)?.windows.first }).first {
            w.alpha = 0.01
            w.isUserInteractionEnabled = false
            win.insertSubview(w, at: 0)
        }
        w.loadFileURL(local, allowingReadAccessTo: tmp)
        DispatchQueue.main.asyncAfter(deadline: .now() + 45) { [weak self] in self?.finish(nil) }
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        // مهلة قصيرة حتى تكتمل الصفحات والصور
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { [weak self] in self?.render() }
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) { finish(nil) }
    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) { finish(nil) }

    private func render() {
        guard let w = web, let src = src else { finish(nil); return }
        let r = UIPrintPageRenderer()
        r.addPrintFormatter(w.viewPrintFormatter(), startingAtPageAt: 0)
        let ext = src.pathExtension.lowercased()
        let landscape = ["ppt", "pptx", "pps", "ppsx", "pptm", "key"].contains(ext)
        let page = landscape ? CGRect(x: 0, y: 0, width: 842, height: 595) : CGRect(x: 0, y: 0, width: 595, height: 842)
        r.setValue(page, forKey: "paperRect")
        r.setValue(page.insetBy(dx: 18, dy: 18), forKey: "printableRect")
        let data = NSMutableData()
        UIGraphicsBeginPDFContextToData(data, page, nil)
        r.prepare(forDrawingPages: NSRange(location: 0, length: max(1, r.numberOfPages)))
        for i in 0..<r.numberOfPages {
            UIGraphicsBeginPDFPage()
            r.drawPage(at: i, in: UIGraphicsGetPDFContextBounds())
        }
        UIGraphicsEndPDFContext()
        guard r.numberOfPages > 0 else { finish(nil); return }
        let out = src.deletingPathExtension().appendingPathExtension("pdf")
        do { try data.write(to: out); finish(out) } catch { finish(nil) }
    }

    private func finish(_ url: URL?) {
        guard let d = done else { return }
        done = nil
        web?.removeFromSuperview()
        web = nil
        d(url)
    }
}
