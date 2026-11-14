import SwiftUI
import PDFKit

// نافذة الكتابة المكبّرة (نفس ZoomWriter.kt في أندرويد): تعرض منطقة صغيرة من الصفحة مكبّرة أسفل الشاشة.
// ما تكتبه فيها يظهر صغيرًا ومرتبًا في مكانه على الصفحة. النقر على الصفحة ينقل المنطقة،
// والأسهم تنقلها جانبيًا أو إلى سطر جديد.

struct ZoomPanel: View {
    @ObservedObject var session: DocSession
    @EnvironmentObject var app: AppController

    var body: some View {
        if let zb = session.zoomBox {
            HStack(spacing: 8) {
                ZoomCanvasRep(session: session, box: zb, inkVersion: session.inkVersion, toolKey: toolKey)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.gold, lineWidth: 1.5))
                VStack(spacing: 0) {
                    Spacer(minLength: 0)
                    panelButton(rotate: -90, L("يمين", "Right")) { move(dx: zb.w * 0.85, dy: 0) }
                    Spacer(minLength: 0)
                    panelButton(rotate: 90, L("يسار", "Left")) { move(dx: -zb.w * 0.85, dy: 0) }
                    Spacer(minLength: 0)
                    panelButton(rotate: 0, L("سطر جديد", "New line")) { move(dx: 0, dy: zb.h * 0.8, newLine: true) }
                    Spacer(minLength: 0)
                    panelButton(icon: "ph_close", rotate: 0, L("إغلاق النافذة المكبّرة", "Close zoom window")) { session.zoomBox = nil }
                    Spacer(minLength: 0)
                }
                .frame(width: 48)
            }
            .padding(8)
            .frame(height: 230)
            .background(Palette.drawerBg)
        }
    }

    /// يتغير مع الأداة أو اللون أو الحجم فتُحدَّث أداة الكانفاس.
    private var toolKey: String {
        "\(app.tool)|\(app.colorFor(app.tool))|\(app.sizeFor(app.tool))|\(app.fingerWrites)|\(app.highlightSnap)|\(app.eraserMode)"
    }

    private func panelButton(icon: String = "ph_caret_down", rotate: Double, _ desc: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            PhIcon(icon, Palette.navy, 20, desc)
                .rotationEffect(.degrees(rotate))
                .frame(width: 44, height: 44)
                .background(RoundedRectangle(cornerRadius: 12).fill(Color.white))
        }
        .buttonStyle(PressScale())
    }

    private func move(dx: CGFloat, dy: CGFloat, newLine: Bool = false) {
        guard var b = session.zoomBox else { return }
        let size = session.pageSize(b.page)
        var nx = b.x + dx
        if newLine { nx = I18n.english ? size.width * 0.04 : size.width * 0.96 - b.w }
        b.x = min(max(0, nx), max(0, size.width - b.w))
        b.y = min(max(0, b.y + dy), max(0, size.height - b.h))
        session.zoomBox = b
    }
}

/// المنطقة المكبّرة: صورة الصفحة وفوقها طبقة كتابة تحمل حبر الصفحة مكبّرًا.
private struct ZoomCanvasRep: UIViewRepresentable {
    let session: DocSession
    let box: ZoomBox
    let inkVersion: Int
    let toolKey: String

    func makeUIView(context: Context) -> ZoomCanvasView {
        let v = ZoomCanvasView()
        v.session = session
        return v
    }

    func updateUIView(_ v: ZoomCanvasView, context: Context) {
        v.update(box: box, inkVersion: inkVersion)
        v.applyTool()
    }
}

final class ZoomCanvasView: UIView {
    weak var session: DocSession?
    private let imageView = UIImageView()
    private let baseline = UIView()
    private let canvas = InkCanvas()
    private var box: ZoomBox?
    private var shownVersion = -1
    private var pageImage: (Int, UIImage)?

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .white
        clipsToBounds = true
        imageView.contentMode = .topLeft
        imageView.clipsToBounds = true
        addSubview(imageView)
        baseline.backgroundColor = UIColor(Palette.gold).withAlphaComponent(0.35)
        baseline.isUserInteractionEnabled = false
        addSubview(baseline)
        canvas.onBegin = { AppController.shared.noteInteraction() }
        canvas.onStroke = { [weak self] st in self?.added(st) }
        canvas.onErase = { [weak self] d in self?.erased(d) }
        addSubview(canvas)
    }

    required init?(coder: NSCoder) { fatalError() }

    /// نسبة التكبير: عرض النافذة إلى عرض المنطقة.
    private var zoom: CGFloat {
        guard let b = box, b.w > 0 else { return 1 }
        return bounds.width / b.w
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        imageView.frame = bounds
        canvas.frame = bounds
        baseline.frame = CGRect(x: 0, y: bounds.height * 0.78, width: bounds.width, height: 1.5)
        // ارتفاع المنطقة على الصفحة يتبع نسبة النافذة
        if let b = box, bounds.width > 0 {
            let h = b.w * bounds.height / bounds.width
            if abs(h - b.h) > 0.01 {
                DispatchQueue.main.async { [weak self] in
                    guard let s = self?.session, var nb = s.zoomBox else { return }
                    nb.h = h
                    s.zoomBox = nb
                }
            }
        }
        reloadImage()
        reloadInk()
    }

    func update(box newBox: ZoomBox, inkVersion: Int) {
        let moved = box != newBox
        box = newBox
        if moved {
            reloadImage()
            reloadInk()
        } else if inkVersion != shownVersion {
            reloadInk()
        }
        shownVersion = inkVersion
    }

    func applyTool() {
        let c = AppController.shared
        canvas.fingerWrites = c.fingerWrites
        canvas.writingEnabled = c.tool != .laser
        canvas.config = PageOverlayView.inkConfig(k: zoom)
        if let s = session, let b = box { canvas.pageW = s.pageSize(b.page).width }
    }

    /// صورة الصفحة بدقة عالية، ثم يُعرض منها الجزء المكبّر.
    private func reloadImage() {
        guard let s = session, let b = box, bounds.width > 0 else { return }
        if pageImage?.0 != b.page, let page = s.pdf?.page(at: b.page) {
            let size = s.pageSize(b.page)
            let w: CGFloat = 1600
            let img = page.thumbnail(of: CGSize(width: w, height: w * size.height / max(1, size.width)), for: .cropBox)
            pageImage = (b.page, img)
        }
        guard let img = pageImage?.1, let cg = img.cgImage else { return }
        let size = s.pageSize(b.page)
        let px = CGFloat(cg.width) / max(1, size.width)
        let crop = CGRect(x: b.x * px, y: b.y * px, width: b.w * px, height: b.h * px).integral
            .intersection(CGRect(x: 0, y: 0, width: cg.width, height: cg.height))
        guard !crop.isEmpty, let part = cg.cropping(to: crop) else { imageView.image = nil; return }
        let r = UIGraphicsImageRenderer(size: bounds.size)
        imageView.image = r.image { _ in
            let z = bounds.width / max(1, crop.width)
            UIImage(cgImage: part).draw(in: CGRect(x: 0, y: 0, width: crop.width * z, height: crop.height * z))
        }
        let night = AppController.shared.nightPages
        imageView.layer.filters = nil
        imageView.alpha = night ? 0.85 : 1
        backgroundColor = night ? UIColor(red: 0.12, green: 0.14, blue: 0.17, alpha: 1) : .white
    }

    private func reloadInk() {
        guard let s = session, let b = box, let pid = s.pageId(b.page), bounds.width > 0 else { return }
        canvas.k = zoom
        canvas.origin = CGPoint(x: b.x, y: b.y)
        canvas.setDrawing(s.drawing(for: pid))
        applyTool()
    }

    /// خط جديد كُتب في النافذة: يُحفظ في الصفحة كما لو كُتب عليها.
    private func added(_ st: InkStroke) {
        guard let s = session, let b = box, let pid = s.pageId(b.page) else { return }
        let c = AppController.shared
        // تمييز بالتقاط النص: يُستبدل الخط بتمييز على الكلمات تحته
        if st.tool == .highlighter && c.highlightSnap && Premium.shared.active {
            let W = s.pageSize(b.page).width
            let pts = InkErase.samples(st, step: 1).map { RelPt(x: $0.x / W, y: $0.y / W) }
            let band = Sizes.width(.highlighter, c.sizeFor(.highlighter)) / 600
            if s.snapHighlight(b.page, pts: pts, color: c.colorFor(.highlighter), band: band) { return }
        }
        shownVersion = s.inkVersion + 1
        canvas.append([st])
        s.addStrokes(pid, [st])
        s.scheduleHandRead(pid)
    }

    private func erased(_ d: InkDrawing) {
        guard let s = session, let b = box, let pid = s.pageId(b.page) else { return }
        shownVersion = s.inkVersion + 1
        s.setInk(pid, d)
        // مسح حروف سطر محوَّل كله يحذف نصه، وما بقي يُقرأ للبحث والتقاط النص
        s.pruneFills(pid)
        s.scheduleHandRead(pid)
    }
}
