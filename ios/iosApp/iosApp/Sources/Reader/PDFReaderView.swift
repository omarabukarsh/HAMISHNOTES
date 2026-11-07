import SwiftUI
import PDFKit
import PencilKit
import Combine

/// عارض PDF من النظام (تمرير وتكبير وحدّة بأي تكبير)، وفوق كل صفحة طبقة الكتابة الخاصة بها.
struct PDFReaderView: UIViewRepresentable {
    @ObservedObject var session: DocSession
    @EnvironmentObject var app: AppController

    func makeCoordinator() -> PDFCoordinator { PDFCoordinator(session) }

    func makeUIView(context: Context) -> ReaderHostView {
        let host = ReaderHostView()
        let v = host.pdfView
        v.displayMode = .singlePageContinuous
        v.displayDirection = .vertical
        v.autoScales = true
        v.pageShadowsEnabled = true
        v.pageBreakMargins = UIEdgeInsets(top: 10, left: 10, bottom: 14, right: 10)
        v.backgroundColor = UIColor(Palette.desk)
        v.pageOverlayViewProvider = context.coordinator
        v.isInMarkupMode = true
        v.document = session.pdf
        context.coordinator.attach(host)
        return host
    }

    func updateUIView(_ host: ReaderHostView, context: Context) {
        host.pdfView.backgroundColor = UIColor(Palette.desk)
        host.night.isHidden = !app.nightPages
        context.coordinator.refreshTools()
    }
}

/// المضيف: عارض PDF وطبقة القراءة الليلية فوقه.
final class ReaderHostView: UIView {
    let pdfView = PDFView()
    let night = UIView()

    override init(frame: CGRect) {
        super.init(frame: frame)
        addSubview(pdfView)
        night.isUserInteractionEnabled = false
        night.backgroundColor = .white
        night.layer.compositingFilter = "differenceBlendMode"
        night.isHidden = true
        addSubview(night)
        // النقر المزدوج على Apple Pencil
        let pencil = UIPencilInteraction()
        pencil.delegate = PencilDelegate.shared
        addInteraction(pencil)
    }

    required init?(coder: NSCoder) { fatalError() }

    override func layoutSubviews() {
        super.layoutSubviews()
        pdfView.frame = bounds
        night.frame = bounds
    }
}

final class PencilDelegate: NSObject, UIPencilInteractionDelegate {
    static let shared = PencilDelegate()
    func pencilInteractionDidTap(_ interaction: UIPencilInteraction) {
        AppController.shared.penDoubleTap()
    }
}

final class PDFCoordinator: NSObject, PDFPageOverlayViewProvider {
    let session: DocSession
    weak var host: ReaderHostView?
    private var overlays: [String: PageOverlayView] = [:]
    private var bag: Set<AnyCancellable> = []
    private var lastToolKey = ""
    private var fitWidth = true

    init(_ s: DocSession) { session = s }

    var pdfView: PDFView? { host?.pdfView }

    func attach(_ h: ReaderHostView) {
        host = h
        let v = h.pdfView
        NotificationCenter.default.publisher(for: .PDFViewPageChanged, object: v)
            .sink { [weak self] _ in self?.pageChanged() }.store(in: &bag)
        NotificationCenter.default.publisher(for: .PDFViewScaleChanged, object: v)
            .sink { [weak self] _ in self?.scaleChanged() }.store(in: &bag)
        session.goToHook = { [weak self] i in self?.go(i) }
        session.zoomHook = { [weak self] f in self?.zoom(f) }
        session.fitHook = { [weak self] in self?.fit() }
        session.reloadHook = { [weak self] in self?.reload() }
        session.applyInkHook = { [weak self] pid, d in self?.overlays[pid]?.setDrawing(d) }
        // أي تغيير في البيانات (تراجع، حذف تمييز من الدرج...) يعيد رسم عناصر الصفحات الظاهرة
        session.$data.dropFirst().receive(on: RunLoop.main)
            .sink { [weak self] _ in self?.overlays.values.forEach { $0.reloadItems() } }.store(in: &bag)
        session.$selectedImage.dropFirst().receive(on: RunLoop.main)
            .sink { [weak self] _ in self?.overlays.values.forEach { $0.reloadItems() } }.store(in: &bag)
        // نتائج البحث مظللة في الصفحة، والنتيجة الحالية محددة
        session.$searchHits.combineLatest(session.$searchIndex).receive(on: RunLoop.main)
            .sink { [weak self] hits, idx in
                guard let v = self?.pdfView else { return }
                hits.forEach { $0.color = UIColor(argb: 0xFFFFEB3B).withAlphaComponent(0.6) }
                v.highlightedSelections = hits.isEmpty ? nil : hits
                if hits.indices.contains(idx) {
                    let sel = hits[idx]
                    sel.color = UIColor(argb: 0xFFFFAB40)
                    v.setCurrentSelection(sel, animate: true)
                    v.go(to: sel)
                } else {
                    v.clearSelection()
                }
            }.store(in: &bag)
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.go(self.session.currentPage, animated: false)
            self.scaleChanged()
        }
    }

    // ---------- الطبقات ----------

    func pdfView(_ view: PDFView, overlayViewFor page: PDFPage) -> UIView? {
        guard let doc = view.document else { return nil }
        let i = doc.index(for: page)
        guard let pid = session.pageId(i) else { return nil }
        if let o = overlays[pid], o.page === page { return o }
        let o = PageOverlayView(pageId: pid, session: session, pdfView: view, page: page, size: session.pageSize(i))
        overlays[pid] = o
        return o
    }

    func pdfView(_ pdfView: PDFView, willDisplayOverlayView overlayView: UIView, for page: PDFPage) {
        (overlayView as? PageOverlayView)?.applyTool()
    }

    func pdfView(_ pdfView: PDFView, willEndDisplayingOverlayView overlayView: UIView, for page: PDFPage) {
        guard let o = overlayView as? PageOverlayView else { return }
        // الكانفاس يحفظ حبره في الجلسة عند كل تغيير، فيمكن إسقاطه
        if overlays[o.pageId] === o { overlays.removeValue(forKey: o.pageId) }
    }

    /// يعيد تطبيق الأداة على الصفحات الظاهرة إن تغيّرت.
    func refreshTools() {
        let c = AppController.shared
        let key = "\(c.tool)|\(c.colorFor(c.tool))|\(c.sizeFor(c.tool))|\(c.lassoActive)|\(c.shapeActive)|\(c.shapeKind)|\(c.textActive)|\(c.stickyActive)|\(c.fingerWrites)|\(c.eraserMode)|\(c.laserMode)"
        guard key != lastToolKey else { return }
        lastToolKey = key
        overlays.values.forEach { $0.applyTool() }
    }

    // ---------- التنقل والتكبير ----------

    private func pageChanged() {
        guard let v = pdfView, let p = v.currentPage, let doc = v.document else { return }
        session.setCurrent(doc.index(for: p))
    }

    private func scaleChanged() {
        guard let v = pdfView else { return }
        let base = v.scaleFactorForSizeToFit
        session.scalePercent = Int((v.scaleFactor / max(0.01, base) * 100).rounded())
    }

    private func go(_ i: Int, animated: Bool = true) {
        guard let v = pdfView, let p = v.document?.page(at: i) else { return }
        v.go(to: p)
    }

    private func zoom(_ f: CGFloat) {
        guard let v = pdfView else { return }
        v.autoScales = false
        let base = v.scaleFactorForSizeToFit
        v.scaleFactor = max(base * 0.5, min(base * 4, v.scaleFactor * f))
        scaleChanged()
    }

    /// ملاءمة الصفحة كاملة، ونقرة ثانية تعيد ملء العرض.
    private func fit() {
        guard let v = pdfView, let p = v.currentPage else { return }
        fitWidth.toggle()
        if fitWidth {
            v.autoScales = true
        } else {
            v.autoScales = false
            let b = p.bounds(for: v.displayBox)
            let k = min(v.bounds.width / b.width, (v.bounds.height - 20) / b.height)
            v.scaleFactor = k
            v.go(to: p)
        }
        scaleChanged()
    }

    private func reload() {
        guard let v = pdfView else { return }
        let cur = session.currentPage
        overlays.removeAll()
        if v.document !== session.pdf {
            v.document = session.pdf
        } else {
            // إعادة ضبط المستند تجبر العارض على إعادة بناء الصفحات والطبقات
            let d = v.document
            v.document = nil
            v.document = d
        }
        go(cur, animated: false)
    }
}
