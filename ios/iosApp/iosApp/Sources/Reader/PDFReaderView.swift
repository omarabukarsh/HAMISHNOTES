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
        session.visibleCenterHook = { [weak self] i in self?.visibleCenter(i) }
        // سحب ملصق أو صورة من الدرج وإفلاتها على الصفحة في مكانها
        h.addInteraction(UIDropInteraction(delegate: self))
        // أي تغيير في البيانات (تراجع، حذف تمييز من الدرج...) يعيد رسم عناصر الصفحات الظاهرة
        session.$data.dropFirst().receive(on: RunLoop.main)
            .sink { [weak self] _ in self?.overlays.values.forEach { $0.reloadItems() } }.store(in: &bag)
        AutoFont.shared.$insertTarget.receive(on: RunLoop.main)
            .sink { [weak self] _ in self?.overlays.values.forEach { $0.showInsert() } }.store(in: &bag)
        session.$lassoSel.receive(on: RunLoop.main)
            .sink { [weak self] _ in self?.overlays.values.forEach { $0.refreshLasso() } }.store(in: &bag)
        session.$selectedImage.dropFirst().receive(on: RunLoop.main)
            .sink { [weak self] _ in self?.overlays.values.forEach { $0.reloadItems() } }.store(in: &bag)
        // نتائج البحث مظللة في الصفحة، والنتيجة الحالية محددة
        session.$searchHits.combineLatest(session.$searchIndex).receive(on: RunLoop.main)
            .sink { [weak self] hits, idx in
                guard let self = self, let v = self.pdfView else { return }
                let sels = hits.compactMap { $0.sel }
                sels.forEach { $0.color = UIColor(argb: 0xFFFFEB3B).withAlphaComponent(0.6) }
                v.highlightedSelections = sels.isEmpty ? nil : sels
                // خط اليد والصفحات المقروءة: مستطيلات في طبقة الصفحة
                self.overlays.values.forEach { $0.showSearch(hits, current: idx) }
                if hits.indices.contains(idx), let sel = hits[idx].sel {
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
            self.applyScrolling()
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
        if !session.searchHits.isEmpty { o.showSearch(session.searchHits, current: session.searchIndex) }
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
        applyScrolling()
    }

    /// التمرير مع أدوات الكتابة (كما في أندرويد): القلم يكتب ولا يحرّك الصفحة،
    /// والإصبع يحرّكها؛ وإن كان الإصبع يكتب فالتحريك بإصبعين.
    func applyScrolling() {
        guard let v = pdfView, let sv = PDFCoordinator.scrollView(in: v) else { return }
        let c = AppController.shared
        let writing = c.tool != .laser || c.lassoActive || c.shapeActive
        let pan = sv.panGestureRecognizer
        pan.allowedTouchTypes = writing ? [NSNumber(value: UITouch.TouchType.direct.rawValue)]
                                        : [NSNumber(value: UITouch.TouchType.direct.rawValue), NSNumber(value: UITouch.TouchType.pencil.rawValue)]
        pan.minimumNumberOfTouches = writing && c.fingerWrites ? 2 : 1
    }

    /// وسط الجزء الظاهر من الصفحة بنقاط الصفحة.
    func visibleCenter(_ i: Int) -> CGPoint? {
        guard let v = pdfView, let page = v.document?.page(at: i) else { return nil }
        let r = v.convert(page.bounds(for: v.displayBox), from: page)
        let vis = r.intersection(v.bounds)
        guard !vis.isNull, r.height > 1 else { return nil }
        let size = session.pageSize(i)
        return CGPoint(x: size.width / 2, y: (vis.midY - r.minY) / r.height * size.height)
    }

    /// الصفحة تحت نقطة في العارض، والنقطة بنقاط الصفحة (الأصل أعلى اليسار).
    func pagePoint(_ pt: CGPoint) -> (Int, CGPoint)? {
        guard let v = pdfView, let doc = v.document, let page = v.page(for: pt, nearest: true) else { return nil }
        let i = doc.index(for: page)
        let r = v.convert(page.bounds(for: v.displayBox), from: page)
        guard r.width > 1, r.height > 1 else { return nil }
        let size = session.pageSize(i)
        return (i, CGPoint(x: (pt.x - r.minX) / r.width * size.width, y: (pt.y - r.minY) / r.height * size.height))
    }

    static func scrollView(in v: UIView) -> UIScrollView? {
        for s in v.subviews {
            if let sv = s as? UIScrollView { return sv }
            if let sv = scrollView(in: s) { return sv }
        }
        return nil
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

// ---------- الإفلات على الصفحة ----------

/// بادئة ملصقات الدرج المسحوبة.
let StickerDragPrefix = "hamish-sticker:"

extension PDFCoordinator: UIDropInteractionDelegate {
    func dropInteraction(_ interaction: UIDropInteraction, canHandle session: UIDropSession) -> Bool {
        session.canLoadObjects(ofClass: NSString.self) || session.canLoadObjects(ofClass: UIImage.self)
    }

    func dropInteraction(_ interaction: UIDropInteraction, sessionDidUpdate session: UIDropSession) -> UIDropProposal {
        UIDropProposal(operation: .copy)
    }

    func dropInteraction(_ interaction: UIDropInteraction, performDrop drop: UIDropSession) {
        guard let v = pdfView, let hit = pagePoint(drop.location(in: v)) else { return }
        let (page, at) = hit
        let s = session
        if drop.canLoadObjects(ofClass: NSString.self) {
            _ = drop.loadObjects(ofClass: NSString.self) { items in
                guard let str = items.first as? String, str.hasPrefix(StickerDragPrefix) else { return }
                DispatchQueue.main.async { s.insertSticker(String(str.dropFirst(StickerDragPrefix.count)), page: page, at: at) }
            }
        } else {
            _ = drop.loadObjects(ofClass: UIImage.self) { items in
                let imgs = items.compactMap { $0 as? UIImage }
                DispatchQueue.main.async {
                    for (i, img) in imgs.enumerated() { s.insertImage(img, page: page, at: at, index: i, select: i == imgs.count - 1) }
                }
            }
        }
    }
}
