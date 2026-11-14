import UIKit
import UIKit.UIGestureRecognizerSubclass
import PDFKit
import SwiftUI

/// طبقة شفافة تحمل عناصر (نصوص، ملاحظات، إطار صورة): لا تأخذ اللمسة لنفسها أبدًا،
/// فتصل لمسة القلم أو الإصبع إلى طبقة الكتابة تحتها إلا إذا وقعت على عنصر.
final class PassThroughView: UIView {
    /// القلم يكتب فوق النصوص والملاحظات والصور (كما في أندرويد)؛ تُلمس هذه العناصر بالإصبع.
    var pencilPasses: () -> Bool = { true }

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        if pencilPasses(), let ts = event?.allTouches, !ts.isEmpty, ts.allSatisfy({ $0.type == .pencil }) { return nil }
        let v = super.hitTest(point, with: event)
        return v === self ? nil : v
    }
}

/// يتابع حركة القلم دون أن يعترضها: هل بقي القلم ثابتًا لحظة قبل رفعه؟ («ارسم وأبقِ»)
/// ويبلّغ بموضعه (لدائرة الممحاة) وبثباته والقلم ما زال على الشاشة (لمعاينة الشكل المرتب مباشرة).
final class HoldTracker: UIGestureRecognizer {
    private(set) var held = false
    private var lastMove: CFTimeInterval = 0
    private var lastPt: CGPoint = .zero
    var fingerWrites = false
    /// القلم ثبت وهو على الشاشة: نقاط الخط حتى الآن. قائمة فارغة = تحرك من جديد.
    var onHold: (([CGPoint]) -> Void)?
    /// موضع القلم الآن؛ nil عند رفعه.
    var onTrack: ((CGPoint?) -> Void)?
    private var pts: [CGPoint] = []
    private var timer: Timer?
    private var tracking = false
    private var fired = false

    private func arm() {
        timer?.invalidate()
        let t = Timer(timeInterval: 0.45, repeats: false) { [weak self] _ in
            guard let self = self, self.tracking else { return }
            self.fired = true
            self.onHold?(self.pts)
        }
        RunLoop.main.add(t, forMode: .common)
        timer = t
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        held = false
        lastMove = CACurrentMediaTime()
        lastPt = touches.first?.location(in: view) ?? .zero
        guard let t = touches.first, t.type == .pencil || fingerWrites, (event.allTouches?.count ?? 1) == 1 else { tracking = false; return }
        tracking = true
        fired = false
        pts = [lastPt]
        onTrack?(lastPt)
        arm()
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
        guard let p = touches.first?.location(in: view) else { return }
        if tracking { pts.append(p); onTrack?(p) }
        if hypot(p.x - lastPt.x, p.y - lastPt.y) > 6 {
            lastMove = CACurrentMediaTime(); lastPt = p
            if tracking {
                if fired { fired = false; onHold?([]) }
                arm()
            }
        }
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
        held = CACurrentMediaTime() - lastMove >= 0.45
        stop()
        state = .failed
    }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) { held = false; stop(); onHold?([]); state = .failed }
    private func stop() {
        timer?.invalidate(); timer = nil
        if tracking { onTrack?(nil) }
        tracking = false
    }
    override func reset() { }
    /// يصلح سواء وصل رفع القلم إلى هنا قبل حفظ الخط أو بعده.
    var heldNow: Bool { held || (lastMove > 0 && CACurrentMediaTime() - lastMove >= 0.45) }
    func consume() { held = false; lastMove = 0 }
}

/// طبقة فوق كل صفحة: التمييز، الصور، الكتابة بالقلم، النصوص، الملاحظات اللاصقة، الليزر، والأشكال.
/// كل الإحداثيات المحفوظة بنقاط الصفحة (الأصل أعلى اليسار)؛ k = نسبة عرض الطبقة إلى عرض الصفحة.
final class PageOverlayView: UIView, UIGestureRecognizerDelegate {
    let pageId: String
    weak var session: DocSession?
    weak var pdfView: PDFView?
    weak var page: PDFPage?
    let pageSize: CGSize

    let canvas = InkCanvas()
    private let hold = HoldTracker()
    private let marksView = MarksView()
    private let imagesLayer = UIView()
    private let topLayer = PassThroughView()
    private let laser = LaserView()
    private let shapes = ShapeCaptureView()
    private let lasso = LassoView()
    private let searchLayer = CAShapeLayer()
    private let searchCurrent = CAShapeLayer()
    private let insertLayer = CAShapeLayer()
    private var selectionFrame: ImageSelectionView?
    private var inlineView: InlineTextView?
    private let textDrag = UIPanGestureRecognizer()
    private let textDragLayer = CAShapeLayer()
    private let holdPreview = CAShapeLayer()
    private let holdFill = CAShapeLayer()
    private let eraserCursor = CAShapeLayer()
    private let dropLayer = CAShapeLayer()
    private let wordSelLayer = CAShapeLayer()
    private let ribbon = UIImageView()
    private let pageNo = UILabel()
    private var miniBar: InlineMiniBar?
    private var noteEditor: NoteTextEditor?
    /// مضلع ملء الشكل المرتب الأخير (ارسم وأبقِ مع الملء)، بإحداثيات الكانفاس.
    private var pendingFill: [CGPoint]?
    private var lastTouchType: UITouch.TouchType = .direct
    private var k: CGFloat = 1
    /// كل ما فوق الصفحة داخل هذه الحاوية: حجمها بنقاط الشاشة الفعلية (حجم الصفحة × نسبة العرض)،
    /// وتُصغَّر بعكس تكبير عارض PDF، فيُرسم الحبر والنص والتمييز بدقة الشاشة (كما في أندرويد)
    /// بدل رسمها بحجم الصفحة الصغير ثم تكبيرها.
    private let content = UIView()
    /// نسبة نقاط الشاشة إلى نقاط الطبقة التي رُسم بها المحتوى الآن.
    private var renderS: CGFloat = 1

    init(pageId: String, session: DocSession, pdfView: PDFView, page: PDFPage, size: CGSize) {
        self.pageId = pageId
        self.session = session
        self.pdfView = pdfView
        self.page = page
        self.pageSize = size
        super.init(frame: CGRect(origin: .zero, size: size))
        backgroundColor = .clear
        isOpaque = false
        clipsToBounds = true
        content.backgroundColor = .clear
        addSubview(content)
        renderS = PageOverlayView.clampScale(pdfView.scaleFactor, size: size, screen: UIScreen.main.scale)

        marksView.isUserInteractionEnabled = false
        marksView.backgroundColor = .clear
        content.addSubview(marksView)
        imagesLayer.isUserInteractionEnabled = false
        content.addSubview(imagesLayer)

        canvas.pageW = size.width
        canvas.onBegin = { [weak self] in self?.inkBegan() }
        canvas.onEnd = { [weak self] in self?.inkEnded() }
        canvas.onStroke = { [weak self] st in self?.newStroke(st) }
        canvas.onErase = { [weak self] d in self?.erased(d) }
        hold.cancelsTouchesInView = false
        hold.delaysTouchesBegan = false
        hold.delaysTouchesEnded = false
        hold.delegate = self
        canvas.addGestureRecognizer(hold)
        content.addSubview(canvas)

        topLayer.backgroundColor = .clear
        topLayer.pencilPasses = { let c = AppController.shared; return !c.textActive && !c.stickyActive && !c.lassoActive }
        hold.onHold = { [weak self] pts in self?.showHoldPreview(pts) }
        hold.onTrack = { [weak self] p in self?.showEraserCursor(p) }
        content.addSubview(topLayer)

        laser.isHidden = true
        content.addSubview(laser)
        searchLayer.fillColor = UIColor(argb: 0xFFFFEB3B).withAlphaComponent(0.45).cgColor
        searchCurrent.fillColor = UIColor(argb: 0xFFFFAB40).withAlphaComponent(0.55).cgColor
        marksView.layer.addSublayer(searchLayer)
        marksView.layer.addSublayer(searchCurrent)
        insertLayer.fillColor = UIColor(Palette.gold).withAlphaComponent(0.08).cgColor
        insertLayer.strokeColor = UIColor(Palette.goldDeep).cgColor
        insertLayer.lineWidth = 1.5
        insertLayer.lineDashPattern = [6, 5]
        marksView.layer.addSublayer(insertLayer)
        lasso.isHidden = true
        lasso.session = session
        lasso.pageId = pageId
        content.addSubview(lasso)
        shapes.isHidden = true
        shapes.onShape = { [weak self] strokes in self?.commitShape(strokes) }
        content.addSubview(shapes)

        // أداة النص: سحب القلم يرسم مربع الكتابة بالعرض المطلوب
        textDrag.addTarget(self, action: #selector(onTextDrag(_:)))
        textDrag.delegate = self
        textDrag.isEnabled = false
        addGestureRecognizer(textDrag)
        textDragLayer.fillColor = UIColor(Palette.navy).withAlphaComponent(0.05).cgColor
        textDragLayer.strokeColor = UIColor(Palette.navy).cgColor
        textDragLayer.lineWidth = 1.5
        textDragLayer.lineDashPattern = [6, 4]
        content.layer.addSublayer(textDragLayer)

        // ضغط مطوّل بالإصبع على نص الكتاب: تحديده (ثم السحب يمدّه)
        let press = UILongPressGestureRecognizer(target: self, action: #selector(onPress(_:)))
        press.minimumPressDuration = 0.45
        press.allowedTouchTypes = [NSNumber(value: UITouch.TouchType.direct.rawValue)]
        press.delegate = self
        addGestureRecognizer(press)

        let tap = UITapGestureRecognizer(target: self, action: #selector(onTap(_:)))
        tap.delegate = self
        tap.cancelsTouchesInView = false
        addGestureRecognizer(tap)

        // نقرة بإصبعين للتراجع، وبثلاثة للإعادة
        let two = UITapGestureRecognizer(target: self, action: #selector(onUndoTap))
        two.numberOfTouchesRequired = 2
        two.delegate = self
        addGestureRecognizer(two)
        let three = UITapGestureRecognizer(target: self, action: #selector(onRedoTap))
        three.numberOfTouchesRequired = 3
        three.delegate = self
        addGestureRecognizer(three)

        // شريط العلامة الذهبي ورقم الصفحة (كما في أندرويد)، تحت الكتابة
        ribbon.image = UIImage(named: "ph_bookmark_fill")?.withRenderingMode(.alwaysTemplate)
        ribbon.tintColor = UIColor(Palette.gold)
        ribbon.contentMode = .scaleAspectFit
        ribbon.isHidden = true
        marksView.addSubview(ribbon)
        pageNo.font = Fonts.ui(11)
        pageNo.textColor = UIColor(Palette.textSecondary).withAlphaComponent(0.7)
        marksView.addSubview(pageNo)
        wordSelLayer.fillColor = UIColor(Palette.navy).withAlphaComponent(0.25).cgColor
        marksView.layer.addSublayer(wordSelLayer)
        // معاينة الشكل المرتب والقلم ما زال على الشاشة، ودائرة الممحاة، وإطار الإفلات
        for l in [holdFill, holdPreview, eraserCursor, dropLayer] {
            l.zPosition = 50
            content.layer.addSublayer(l)
        }
        holdPreview.fillColor = UIColor.clear.cgColor
        holdPreview.lineCap = .round
        holdPreview.lineJoin = .round
        eraserCursor.fillColor = UIColor.white.withAlphaComponent(0.35).cgColor
        eraserCursor.strokeColor = UIColor(Palette.textSecondary).cgColor
        eraserCursor.lineWidth = 1.2
        dropLayer.fillColor = UIColor.clear.cgColor
        dropLayer.strokeColor = UIColor(argb: 0xFF2E7D32).cgColor
        dropLayer.lineWidth = 4

        layoutContent()
        setDrawing(session.drawing(for: pageId))
        applyTool()
        reloadItems()
    }

    required init?(coder: NSCoder) { fatalError() }

    /// إطار أخضر على الصفحة التي سيُفلت فوقها عنصر من الدرج.
    func setDropHighlight(_ on: Bool) {
        dropLayer.path = on ? UIBezierPath(rect: content.bounds.insetBy(dx: 2, dy: 2)).cgPath : nil
    }

    /// القلم ثبت وهو على الشاشة: يظهر الشكل المرتب فورًا (ويُملأ إن كان الملء مفعّلًا)، ويُثبَّت عند رفع القلم.
    private func showHoldPreview(_ pts: [CGPoint]) {
        let c = AppController.shared
        guard pts.count > 3, c.drawAndHold, c.tool.isPen, !c.shapeActive, !c.lassoActive, !canvas.isHidden, canvas.isUserInteractionEnabled,
              let r = ShapeRecognizer.recognize(pts, pageW: pageSize.width * k) else {
            holdPreview.path = nil
            holdFill.path = nil
            return
        }
        let p = UIBezierPath()
        for path in r {
            guard let f = path.first else { continue }
            p.move(to: f)
            for q in path.dropFirst() { p.addLine(to: q) }
        }
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        holdPreview.strokeColor = UIColor(argb: c.colorFor(c.tool)).cgColor
        holdPreview.lineWidth = max(1, Sizes.width(c.tool, c.sizeFor(c.tool)) * k)
        holdPreview.path = p.cgPath
        if c.shapeFill, let poly = PageOverlayView.closedPolygon(r) {
            let fp = UIBezierPath()
            fp.move(to: poly[0])
            for q in poly.dropFirst() { fp.addLine(to: q) }
            fp.close()
            holdFill.fillColor = UIColor(argb: c.colorFor(c.tool)).withAlphaComponent(CGFloat(c.shapeFillAlpha)).cgColor
            holdFill.path = fp.cgPath
        } else {
            holdFill.path = nil
        }
        CATransaction.commit()
    }

    /// الشكل مغلق (بدايته عند نهايته)؟ يعيد مضلعه.
    static func closedPolygon(_ paths: [[CGPoint]]) -> [CGPoint]? {
        guard paths.count == 1, let p = paths.first, p.count > 3, let a = p.first, let b = p.last else { return nil }
        let xs = p.map { $0.x }, ys = p.map { $0.y }
        let diag = hypot((xs.max() ?? 0) - (xs.min() ?? 0), (ys.max() ?? 0) - (ys.min() ?? 0))
        return hypot(a.x - b.x, a.y - b.y) <= max(2, diag * 0.05) ? p : nil
    }

    /// دائرة تبيّن مكان الممحاة وحجمها.
    private func showEraserCursor(_ p: CGPoint?) {
        let c = AppController.shared
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        if let p = p, c.tool == .eraser, !c.lassoActive, canvas.isUserInteractionEnabled {
            let r = c.eraserMode == .stroke ? 9 : max(4, Sizes.width(.eraser, c.sizeFor(.eraser)) * k / 2)
            eraserCursor.path = UIBezierPath(ovalIn: CGRect(x: p.x - r, y: p.y - r, width: r * 2, height: r * 2)).cgPath
        } else {
            eraserCursor.path = nil
        }
        CATransaction.commit()
    }

    /// حد أعلى لنسبة الرسم حتى لا تكبر مساحة الحبر عن الذاكرة المعقولة (نحو 20 مليون بكسل للصفحة).
    static func clampScale(_ eff: CGFloat, size: CGSize, screen: CGFloat) -> CGFloat {
        let cap = sqrt(20_000_000 / max(1, size.width * size.height * screen * screen))
        guard eff.isFinite, eff > 0 else { return 1 }
        return max(0.25, min(eff, cap))
    }

    /// يضبط الحاوية: حجمها بنقاط الشاشة، ومصغّرة بعكس تكبير العارض. يعيد true إن تغيّرت نسبة الرسم.
    @discardableResult
    private func layoutContent() -> Bool {
        let s = renderS
        let size = CGSize(width: bounds.width * s, height: bounds.height * s)
        content.transform = .identity
        content.bounds = CGRect(origin: .zero, size: size)
        content.center = CGPoint(x: bounds.midX, y: bounds.midY)
        content.transform = CGAffineTransform(scaleX: 1 / s, y: 1 / s)
        for v in [marksView, imagesLayer, canvas, topLayer, laser, shapes, lasso] as [UIView] { v.frame = content.bounds }
        // الليزر أثر عابر: دقة محدودة تكفيه
        laser.contentScaleFactor = min(UIScreen.main.scale, 2400 / max(1, max(size.width, size.height)))
        let nk = size.width / max(1, pageSize.width)
        guard abs(nk - k) > 0.0001 else { return false }
        k = nk
        return true
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        if layoutContent() {
            // الحبر محفوظ بنقاط الصفحة (متجهات): يُعاد رسمه بالنسبة الجديدة من الأصل، لا بتكبير المرسوم
            if let s = session { setDrawing(s.drawing(for: pageId)) }
            applyTool()
            reloadItems()
        }
        marksView.k = k * pageSize.width
        marksView.pk = k
        marksView.rebuild()
        lasso.k = k
        lasso.refresh()
        searchLayer.frame = marksView.bounds
        searchCurrent.frame = marksView.bounds
        if !lastHits.isEmpty { showSearch(lastHits, current: lastHitIndex) }
        showInsert()
        showWordSel()
        layoutBadges()
    }

    private var lastHits: [SearchHit] = []
    private var lastHitIndex = 0

    /// نتائج البحث في خط اليد والصفحات المقروءة لهذه الصفحة.
    func showSearch(_ hits: [SearchHit], current: Int) {
        lastHits = hits
        lastHitIndex = current
        guard let idx = session?.index(of: pageId) else { return }
        let all = UIBezierPath(), cur = UIBezierPath()
        for (i, h) in hits.enumerated() where h.page == idx {
            guard let r = h.rect else { continue }
            let rr = CGRect(x: r.minX * k, y: r.minY * k, width: r.width * k, height: r.height * k)
            (i == current ? cur : all).append(UIBezierPath(roundedRect: rr, cornerRadius: rr.height * 0.25))
        }
        searchLayer.path = all.cgPath
        searchCurrent.path = cur.cgPath
    }

    /// مربع الإضافة المنقط (بعد ∧).
    func showInsert() {
        insertLayer.frame = marksView.bounds
        if let it = AutoFont.shared.insertTarget, it.pageId == pageId, it.docId == session?.id {
            let r = it.area
            insertLayer.path = UIBezierPath(roundedRect: CGRect(x: r.minX * k, y: r.minY * k, width: r.width * k, height: r.height * k), cornerRadius: 10).cgPath
        } else {
            insertLayer.path = nil
        }
    }

    func refreshLasso() {
        lasso.k = k
        lasso.refresh()
    }

    func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }

    func gestureRecognizer(_ g: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        if touch.view is UIControl { return false }
        if let t = g as? UITapGestureRecognizer, t.numberOfTouchesRequired == 1 { lastTouchType = touch.type }
        if g.numberOfTouches > 1 || (g as? UITapGestureRecognizer)?.numberOfTouchesRequired ?? 1 > 1 {
            return AppController.shared.fingerUndo && touch.type == .direct
        }
        return true
    }

    // ======================================================== الأداة

    /// يطبّق الأداة المختارة على الكانفاس.
    func applyTool() {
        let c = AppController.shared
        let finger = c.fingerWrites
        canvas.fingerWrites = finger
        hold.fingerWrites = finger
        if c.tool != .eraser { eraserCursor.path = nil }
        laser.fingerWrites = finger
        shapes.fingerWrites = finger
        let laserOn = c.tool == .laser && !c.lassoActive
        let shapeOn = c.shapeActive && !c.lassoActive && c.tool.isWriting
        let tapTools = c.textActive || c.stickyActive
        laser.isHidden = !laserOn
        laser.color = UIColor(argb: c.colorFor(.laser))
        laser.width = Sizes.width(.laser, c.sizeFor(.laser)) * k
        laser.mode = c.laserMode
        shapes.isHidden = !shapeOn
        canvas.isUserInteractionEnabled = !laserOn && !shapeOn && !tapTools
        canvas.writingEnabled = canvas.isUserInteractionEnabled && !c.lassoActive
        textDrag.isEnabled = c.textActive
        textDrag.allowedTouchTypes = finger ? [NSNumber(value: UITouch.TouchType.pencil.rawValue), NSNumber(value: UITouch.TouchType.direct.rawValue)]
                                            : [NSNumber(value: UITouch.TouchType.pencil.rawValue)]

        lasso.isHidden = !c.lassoActive
        lasso.fingerWrites = finger
        lasso.rect = c.lassoRect
        lasso.k = k
        if c.lassoActive {
            canvas.isUserInteractionEnabled = false
            lasso.refresh()
            return
        }
        canvas.config = PageOverlayView.inkConfig(k: k)
        if shapeOn {
            shapes.ink = shapeInk()
            shapes.kind = c.shapeKind
            shapes.style = c.shapeStyle
            shapes.rounded = c.shapeRounded
            shapes.arrowStart = c.arrowStart
            shapes.arrowEnd = c.arrowEnd
            shapes.fill = c.shapeFill
            shapes.fillAlpha = CGFloat(c.shapeFillAlpha)
        }
    }

    /// إعدادات الكتابة للأداة المختارة. k = نسبة نقاط الطبقة إلى نقاط الصفحة (لحجم الممحاة على الشاشة).
    static func inkConfig(k: CGFloat) -> InkToolConfig {
        let c = AppController.shared
        var cfg = InkToolConfig()
        cfg.tool = c.tool
        cfg.color = c.colorFor(c.tool) | 0xFF000000
        cfg.width = Sizes.width(c.tool, c.sizeFor(c.tool))
        cfg.style = c.tool.isPen ? c.penStyle : .solid
        cfg.eraserMode = c.eraserMode
        // «الخط كاملًا»: دائرة صغيرة ثابتة على الشاشة؛ «الجزء الملموس»: بحجم الممحاة المختار
        cfg.eraserRadius = c.eraserMode == .stroke ? 9 / max(0.01, k) : max(1, Sizes.width(.eraser, c.sizeFor(.eraser)) / 2)
        cfg.pressure = c.penPressure
        cfg.sharpness = c.penSharpness
        cfg.stabilize = c.tool == .highlighter ? c.hlStabilize : c.penStabilize
        cfg.straight = c.hlStraight
        cfg.canErase = { c.canErase($0) }
        return cfg
    }

    private func shapeInk() -> (UInt32, CGFloat) {
        let c = AppController.shared
        return (c.shapeColor | 0xFF000000, Sizes.width(.ballpoint, c.shapeSize) * k)
    }

    // ======================================================== الحبر

    func setDrawing(_ d: InkDrawing) {
        canvas.k = k
        canvas.setDrawing(d)
    }

    private func inkBegan() {
        AppController.shared.noteInteraction()
        AutoFont.shared.penDown()
        // لمس الصفحة للكتابة يغلق لوحة خيارات الصفحة
        if session?.pageMenuOpen == true { session?.pageMenuOpen = false }
    }

    private func inkEnded() {
        AutoFont.shared.penUp()
        holdPreview.path = nil
        holdFill.path = nil
        eraserCursor.path = nil
        // الرجوع إلى القلم تلقائيًا بعد المسح
        let c = AppController.shared
        if c.tool == .eraser && c.autoDeselect {
            DispatchQueue.main.async { c.selectTool(c.lastWriting == .eraser ? c.lastPen : c.lastWriting) }
        }
    }

    /// انتهى مسح: الحبر الجديد خطوة تراجع واحدة، ونص الحروف الممسوحة كلها يُحذف.
    private func erased(_ d: InkDrawing) {
        guard let s = session else { return }
        s.inkChanged(pageId, d)
        if s.pruneFills(pageId) { reloadMarks() }
    }

    /// خط جديد اكتمل (بنقاط الصفحة).
    private func newStroke(_ st: InkStroke) {
        guard let s = session else { return }
        let app = AppController.shared
        // هايلايتر بالتقاط النص: يصير تمييزًا على الكلمات تحته
        if st.tool == .highlighter, !app.shapeActive, handleHighlight(st) { return }
        var add = [st]
        var shape = false
        if st.tool.isPen, !app.shapeActive, let made = restyle(st) {
            add = made
            shape = true
        }
        hold.consume()
        holdPreview.path = nil
        holdFill.path = nil
        if !shape && st.tool.isPen {
            // شخبطة أو خط فوق كلمة متحوّلة = حذفها، و∧ = مكان إضافة: الخط لا يُحفظ
            if AutoFont.shared.gesture(s, pageId: pageId, stroke: st) { return }
            // الكتابة بالقلم على ملاحظة لاصقة تصير جزءًا منها (تتحرك معها)
            let b = st.renderBounds
            if let n = s.data.notes.last(where: { $0.page == pageId && !$0.collapsed && CGRect(x: $0.x, y: $0.y, width: $0.w, height: $0.h).insetBy(dx: -2, dy: -2).contains(b) }) {
                var ink = n.ink.flatMap { InkDrawing.load($0) } ?? InkDrawing()
                ink.strokes.append(st.transformed(CGAffineTransform(translationX: -n.x, y: -n.y)))
                var m = n
                m.ink = ink.dataRepresentation()
                s.upsertNote(m)
                return
            }
        }
        canvas.append(add)
        s.inkChanged(pageId, canvas.drawing)
        if !shape && st.tool.isPen { AutoFont.shared.onStroke(s, pageId: pageId, stroke: st) }
    }

    /// «ارسم وأبقِ»: القلم ثبت لحظة قبل رفعه والخط يشبه شكلًا، فيصير شكلًا مرتبًا (ويُملأ إن كان الملء مفعّلًا).
    private func restyle(_ st: InkStroke) -> [InkStroke]? {
        let c = AppController.shared
        guard c.drawAndHold, hold.heldNow, st.pts.count > 3,
              let r = ShapeRecognizer.recognize(st.pts.map { $0.cg }, pageW: pageSize.width) else { return nil }
        let fillPoly = c.shapeFill ? PageOverlayView.closedPolygon(r) : nil
        return r.map { path in
            var s = InkStroke(points: path, color: st.color, width: st.width, tool: st.tool, t: st.t, style: st.style, shape: true)
            // الشكل المغلق: آخر نقطة هي الأولى تمامًا (زاوية حادة، وملء إن كان مفعّلًا)
            if fillPoly != nil, s.pts.count > 3, let a = s.pts.first {
                s.pts[s.pts.count - 1] = a
                s.shapeFill = true
                s.fillAlpha = CGFloat(c.shapeFillAlpha)
            }
            return s
        }
    }

    private func commitShape(_ strokes: [InkStroke]) {
        guard let s = session, !strokes.isEmpty else { return }
        let page = strokes.map { $0.transformed(CGAffineTransform(scaleX: 1 / k, y: 1 / k)) }
        canvas.append(page)
        s.inkChanged(pageId, canvas.drawing)
    }

    // ======================================================== التمييز والتقاط النص

    /// نقطة من الطبقة إلى إحداثيات PDF.
    private func toPdf(_ p: CGPoint) -> CGPoint? {
        guard let v = pdfView, let page = page else { return nil }
        return v.convert(content.convert(p, to: v), to: page)
    }

    /// مستطيل PDF إلى إحداثيات الصفحة المحفوظة.
    private func fromPdf(_ r: CGRect) -> CGRect? {
        guard let v = pdfView, let page = page else { return nil }
        let vr = content.convert(v.convert(r, from: page), from: v)
        return CGRect(x: vr.minX / k, y: vr.minY / k, width: vr.width / k, height: vr.height / k)
    }

    /// خط هايلايتر جديد: مع «التقاط النص» يُستبدل بتمييز على الكلمات تحته ويُضاف نصه إلى الملخص
    /// (نفس snapHighlight في أندرويد). دونه يبقى رسمًا حرًا ولا يدخل الملخص.
    private func handleHighlight(_ stroke: InkStroke) -> Bool {
        guard let s = session, let index = s.index(of: pageId) else { return false }
        let c = AppController.shared
        guard c.highlightSnap && Premium.shared.active else { return false }
        let W = max(1, pageSize.width)
        let pts = InkErase.samples(stroke, step: 3).map { RelPt(x: $0.x / W, y: $0.y / W) }
        let band = Sizes.width(.highlighter, c.sizeFor(.highlighter)) / 600
        if s.snapHighlight(index, pts: pts, color: c.colorFor(.highlighter), band: band) {
            reloadMarks()
            return true
        }
        c.say(.error, L("لا يوجد نص مقروء تحت التمييز، فحُفظ كتمييز حر", "No readable text under the highlight, so it was kept as a free highlight"))
        return false
    }

    func reloadMarks() {
        marksView.marks = session?.marks(onPage: pageId).filter { !$0.free } ?? []
        marksView.fills = session?.data.fills.filter { $0.page == pageId } ?? []
        marksView.pk = k
        marksView.k = k * pageSize.width
        marksView.rebuild()
    }

    // ======================================================== الصور والنصوص والملاحظات

    func reloadItems() {
        guard let s = session else { return }
        reloadMarks()
        imagesLayer.subviews.forEach { $0.removeFromSuperview() }
        let idxNow = s.index(of: pageId)
        ribbon.isHidden = !(idxNow.map { s.isBookmarked($0) } ?? false)
        pageNo.text = idxNow.map { "\($0 + 1)" } ?? ""
        layoutBadges()
        let night = AppController.shared.nightPages
        for im in s.data.images where im.page == pageId {
            // القراءة الليلية تعكس الورقة والحبر فقط: الصور تبقى بألوانها
            let v = UIImageView(image: night ? NightImages.inverted(im.file, s.image(for: im)) : s.image(for: im))
            v.contentMode = .scaleAspectFit
            v.frame = CGRect(x: im.x * k, y: im.y * k, width: im.w * k, height: im.w / max(0.01, im.aspect) * k)
            imagesLayer.addSubview(v)
        }
        // المربع الذي يُكتب فيه يبقى كما هو (لا يُعاد إنشاؤه فلا تختفي لوحة المفاتيح)
        let keepInline = inlineView.flatMap { v in s.inlineEditId == v.boxId && s.data.boxes.contains { $0.id == v.boxId && $0.page == pageId } ? v : nil }
        noteEditor?.commit()
        noteEditor = nil
        topLayer.subviews.forEach { if $0 !== keepInline { $0.removeFromSuperview() } }
        miniBar = nil
        if keepInline == nil { inlineView = nil }
        for n in s.data.notes where n.page == pageId {
            let v = StickyNoteView(note: n, k: k, selected: s.selectedNote == n.id)
            v.onTap = { [weak self] in
                guard let s = self?.session else { return }
                // نقرة تحدد الملاحظة (وشريطها أسفل الشاشة)، والمطوية تُفتح
                if n.collapsed { var m = n; m.collapsed = false; s.upsertNote(m); return }
                // نقرة ثانية على الملاحظة المحددة: الكتابة فيها بلوحة المفاتيح مباشرة
                if s.selectedNote == n.id { self?.editNoteInline(n) } else { s.selectedNote = n.id }
            }
            v.onResize = { [weak self] size in
                guard let self = self else { return }
                var m = n
                let minSide = self.pageSize.width * 0.08
                m.w = max(minSide, size.width / self.k)
                m.h = max(minSide, size.height / self.k)
                self.session?.upsertNote(m)
                self.reloadItems()
            }
            v.onMove = { [weak self] delta in
                var m = n
                m.x += delta.x / (self?.k ?? 1)
                m.y += delta.y / (self?.k ?? 1)
                self?.session?.upsertNote(m)
                self?.reloadItems()
            }
            topLayer.addSubview(v)
        }
        for b in s.data.boxes where b.page == pageId {
            if b.id == s.inlineEditId {
                if let v = keepInline { v.apply(b, k: k) } else {
                    let v = InlineTextView(box: b, k: k)
                    v.text = s.inlineText
                    v.onChange = { [weak s] t in s?.inlineText = t }
                    topLayer.addSubview(v)
                    inlineView = v
                    DispatchQueue.main.async { v.becomeFirstResponder() }
                }
                if let v = inlineView { addMiniBar(over: v, box: b) }
                continue
            }
            let v = TextBoxView(box: b, k: k, selected: s.selectedBox == b.id)
            v.onTap = { [weak self] in
                guard let self = self, let s = self.session else { return }
                // بأداة النص: نقرة تكتب فيه؛ والرابط بنقرة ثانية يُفتح؛ وغير ذلك نقرة تحدده (وشريطه أسفل الشاشة)
                if AppController.shared.textActive { s.beginInlineEdit(b.id); return }
                if let d = b.linkDoc, s.selectedBox == b.id { AppController.shared.followLink(from: s, docId: d, page: b.linkPage, pageId: b.linkPageId); return }
                if s.selectedBox == b.id { s.beginInlineEdit(b.id) } else { s.finishInlineEdit(); s.selectedBox = b.id }
            }
            v.onMove = { [weak self] delta in
                var m = b
                m.x += delta.x / (self?.k ?? 1)
                m.y += delta.y / (self?.k ?? 1)
                self?.session?.upsertBox(m)
                self?.reloadItems()
            }
            v.onResize = { [weak self] w in
                guard let self = self else { return }
                var m = b
                m.w = min(max(self.pageSize.width * 0.06, w / self.k), self.pageSize.width)
                self.session?.upsertBox(m)
                self.reloadItems()
            }
            topLayer.addSubview(v)
        }
        if let sel = s.selectedImage, let im = s.data.images.first(where: { $0.id == sel && $0.page == pageId }) {
            let f = ImageSelectionView(frame: CGRect(x: im.x * k, y: im.y * k, width: im.w * k, height: im.w / max(0.01, im.aspect) * k))
            f.onChange = { [weak self] r in
                guard let self = self else { return }
                var m = im
                m.x = r.minX / self.k
                m.y = r.minY / self.k
                m.w = r.width / self.k
                self.session?.upsertImage(m)
                self.reloadItems()
            }
            f.onDelete = { [weak self] in
                self?.session?.deleteImage(im.id)
                self?.session?.selectedImage = nil
                self?.reloadItems()
            }
            topLayer.addSubview(f)
            selectionFrame = f
        } else {
            selectionFrame = nil
        }
    }

    private func layoutBadges() {
        let b = marksView.bounds
        ribbon.frame = CGRect(x: b.width - 18 - 30, y: -3, width: 30, height: 30)
        pageNo.sizeToFit()
        pageNo.frame.origin = CGPoint(x: b.width - 10 - pageNo.frame.width, y: b.height - 10 - pageNo.frame.height)
    }

    /// الشريط الصغير فوق مربع الكتابة: الخط، تصغير، تكبير، حذف، تم.
    private func addMiniBar(over v: InlineTextView, box b: TextBox) {
        let bar = InlineMiniBar()
        bar.onAction = { [weak self] i in
            guard let s = self?.session else { return }
            switch i {
            case 0: s.fontPickRequest = true
            case 1: s.scaleInlineText(1 / 1.15)
            case 2: s.scaleInlineText(1.15)
            case 3: s.inlineText = ""; s.finishInlineEdit()
            default: s.finishInlineEdit()
            }
        }
        let size = bar.fitting()
        var y = v.frame.minY - size.height - 6
        if y < 2 { y = v.frame.maxY + 6 }
        let x = min(max(2, v.frame.minX), max(2, content.bounds.width - size.width - 2))
        bar.frame = CGRect(origin: CGPoint(x: x, y: y), size: size)
        topLayer.addSubview(bar)
        miniBar = bar
    }

    /// الكتابة بلوحة المفاتيح داخل الملاحظة اللاصقة مباشرة.
    private func editNoteInline(_ n: StickyNote) {
        noteEditor?.commit()
        let w = n.w * k
        let e = NoteTextEditor(frame: CGRect(x: n.x * k, y: n.y * k, width: w, height: n.h * k).insetBy(dx: w * 0.07, dy: w * 0.07))
        e.font = Fonts.ui(max(9, w * 0.075))
        e.text = n.text
        e.onEnd = { [weak self] t in
            guard let s = self?.session, t != n.text, var m = s.data.notes.first(where: { $0.id == n.id }) else { return }
            m.text = t
            s.upsertNote(m)
        }
        topLayer.addSubview(e)
        noteEditor = e
        e.becomeFirstResponder()
    }

    /// تحديد الكلمات المقروءة (صفحات ممسوحة ضوئيًا وخط اليد) يظهر مظللًا.
    func showWordSel() {
        guard let s = session, let sel = s.wordSel, s.index(of: pageId) == sel.page else { wordSelLayer.path = nil; return }
        let W = pageSize.width * k
        let p = UIBezierPath()
        for w in sel.words { p.append(UIBezierPath(roundedRect: CGRect(x: w.l * W, y: w.t * W, width: (w.r - w.l) * W, height: (w.b - w.t) * W).insetBy(dx: -1, dy: -1), cornerRadius: 2)) }
        wordSelLayer.frame = marksView.bounds
        wordSelLayer.path = p.cgPath
    }

    // ======================================================== حدّة الرسم (بدقة الشاشة دائمًا)

    /// يقيس نسبة ظهور الطبقة على الشاشة الآن، وإن تغيّرت يعيد رسم كل شيء بها.
    /// يُستدعى عند ظهور الصفحة وبعد انتهاء التكبير أو التصغير (لا أثناءه).
    func updateRenderScale() {
        guard let v = pdfView, bounds.width > 0 else { return }
        var eff = v.scaleFactor * pageSize.width / bounds.width
        if window != nil, isDescendant(of: v) {
            let m = convert(bounds, to: v).width / bounds.width
            if m.isFinite, m > 0.05 { eff = m }
        }
        let s = PageOverlayView.clampScale(eff, size: bounds.size, screen: window?.screen.scale ?? UIScreen.main.scale)
        guard abs(s - renderS) > renderS * 0.01 else { return }
        renderS = s
        setNeedsLayout()
        layoutIfNeeded()
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        updateRenderScale()
        linkMultiTouchTaps()
    }

    private var linkedScroll: UIScrollView?

    /// نقرة الإصبعين (تراجع) والثلاثة (إعادة) تنتظر فشل التكبير والتحريك:
    /// فلا يُحسب التكبير بإصبعين تراجعًا.
    private func linkMultiTouchTaps() {
        var v: UIView? = superview
        while let x = v, !(x is UIScrollView) { v = x.superview }
        guard let sv = v as? UIScrollView, sv !== linkedScroll else { return }
        linkedScroll = sv
        for g in gestureRecognizers ?? [] {
            guard let t = g as? UITapGestureRecognizer, t.numberOfTouchesRequired > 1 else { continue }
            if let pinch = sv.pinchGestureRecognizer { t.require(toFail: pinch) }
            t.require(toFail: sv.panGestureRecognizer)
        }
    }

    @objc private func onTap(_ g: UITapGestureRecognizer) {
        guard let s = session else { return }
        let c = AppController.shared
        let p = g.location(in: content)
        let pp = CGPoint(x: p.x / k, y: p.y / k)
        if let idx = s.index(of: pageId) { s.setCurrent(idx) }
        // نافذة الكتابة المكبّرة مفتوحة: النقر ينقل منطقتها إلى هنا
        if let zb = s.zoomBox, let idx = s.index(of: pageId) {
            var b = zb
            b.page = idx
            b.x = min(max(0, pp.x - b.w / 2), max(0, pageSize.width - b.w))
            b.y = min(max(0, pp.y - b.h / 2), max(0, pageSize.height - b.h))
            s.zoomBox = b
            return
        }
        // نقرة بالإصبع خارج الأدراج تغلقها
        if lastTouchType == .direct, s.drawerOpen || s.summaryOpen || s.pageMenuOpen {
            withAnimation(Motion.exit) { s.drawerOpen = false; s.summaryOpen = false; s.pageMenuOpen = false }
            return
        }
        if s.pageMenuOpen { s.pageMenuOpen = false }
        // نقرة تلغي تحديد نص الكتاب
        if s.textSel != nil || s.wordSel != nil { s.textSel = nil; s.wordSel = nil; return }
        // عنصر من الدرج: يوضع حيث نقرت
        if let pend = s.pendingPlace, let idx = s.index(of: pageId) {
            s.pendingPlace = nil
            switch pend {
            case .sticker(let key): s.insertSticker(key, page: idx, at: pp)
            case .images(let imgs): for (i, im) in imgs.enumerated() { s.insertImage(im, page: idx, at: pp, index: i, select: i == imgs.count - 1) }
            }
            return
        }
        if c.textActive {
            // نقرة أثناء الكتابة تنهيها؛ وإلا تضع مربعًا جديدًا وتبدأ الكتابة فيه
            if s.inlineEditId != nil { s.finishInlineEdit(); return }
            s.placeTextBox(pageId: pageId, at: pp)
            // كما في أندرويد: الأداة تنطفئ بعد وضع مربع واحد (تبقى الكتابة فيه)
            c.textActive = false
            return
        }
        if s.inlineEditId != nil { s.finishInlineEdit() }
        if s.selectedBox != nil { s.selectedBox = nil }
        // لصق ملاحظة منسوخة أو مقصوصة حيث نقرت
        if s.pastingNote, var n = c.noteClipboard {
            s.pastingNote = false
            n.id = UUID().uuidString
            n.page = pageId
            n.x = max(0, pp.x - n.w / 2)
            n.y = max(0, pp.y - n.h / 2)
            s.upsertNote(n)
            reloadItems()
            return
        }
        if c.stickyActive {
            let w = pageSize.width * 0.26
            let n = StickyNote(id: UUID().uuidString, page: pageId, x: pp.x - w / 2, y: pp.y - w / 2, w: w, h: w, color: c.stickyColor,
                               style: c.stickyStyle, author: c.noteAuthor)
            s.upsertNote(n)
            s.selectedNote = n.id
            reloadItems()
            c.say(.tip, L("أُضيفت الملاحظة. انقر لفتحها، واسحبها لتحريكها", "Sticky note added. Tap to open it, drag to move it"))
            return
        }
        if s.selectedNote != nil { s.selectedNote = nil }
        // نقرة بالإصبع على كلمة محوّلة: قراءات أخرى لتصحيحها
        if s.altPopup != nil { s.altPopup = nil }
        if lastTouchType == .direct, c.wordAlts, !(c.fingerWrites && c.tool.isWriting),
           let ap = s.altWord(pageId: pageId, at: RelPt(x: pp.x / max(1, pageSize.width), y: pp.y / max(1, pageSize.width))) {
            s.altPopup = ap
            return
        }
        // نقرة على صورة تحددها، ونقرة خارجها تلغي التحديد
        let hit = s.data.images.last { im in
            im.page == pageId && CGRect(x: im.x, y: im.y, width: im.w, height: im.w / max(0.01, im.aspect)).contains(pp)
        }
        if let h = hit, !(selectionFrame?.frame.contains(p) ?? false) {
            s.selectedImage = h.id
            reloadItems()
        } else if hit == nil && s.selectedImage != nil && !(selectionFrame?.frame.contains(p) ?? false) {
            s.selectedImage = nil
            reloadItems()
        }
    }

    private var textDragStart: CGPoint = .zero
    private var selAnchor: CGPoint?

    @objc private func onPress(_ g: UILongPressGestureRecognizer) {
        guard let s = session, let page = page, let pt = toPdf(g.location(in: content)) else { return }
        // الإصبع يكتب: الضغط المطوّل للكتابة لا للتحديد
        if AppController.shared.fingerWrites && AppController.shared.tool.isWriting { return }
        let lp = g.location(in: content)
        let W = max(1, pageSize.width * k)
        let rel = RelPt(x: lp.x / W, y: lp.y / W)
        switch g.state {
        case .began:
            selAnchor = pt
            wordAnchor = nil
            if let sel = page.selectionForWord(at: pt), !(sel.string ?? "").trimmingCharacters(in: .whitespaces).isEmpty {
                s.wordSel = nil
                s.textSel = sel
            } else {
                selAnchor = nil
                beginWordSel(rel)
            }
        case .changed:
            if let a = wordAnchor, let idx = s.index(of: pageId) {
                let ws = s.words(idx)
                if let j = PageOverlayView.nearestWord(ws, rel), a < ws.count {
                    s.wordSel = WordSel(page: idx, words: Array(ws[min(a, j)...max(a, j)]))
                }
                return
            }
            guard let a = selAnchor, let sel = page.selection(from: a, to: pt) else { return }
            s.textSel = sel
        default:
            selAnchor = nil
            wordAnchor = nil
        }
    }

    private var wordAnchor: Int?

    /// صفحة بلا نص رقمي: التحديد على الكلمات المقروءة ضوئيًا (تُقرأ الصفحة عند الحاجة) وخط اليد.
    private func beginWordSel(_ rel: RelPt) {
        guard let s = session, let idx = s.index(of: pageId) else { return }
        let ws = s.words(idx)
        if let i = PageOverlayView.wordAt(ws, rel) {
            wordAnchor = i
            s.textSel = nil
            s.wordSel = WordSel(page: idx, words: [ws[i]])
            return
        }
        guard s.ocr[pageId] == nil, (page?.string ?? "").trimmingCharacters(in: .whitespacesAndNewlines).count <= 20 else { return }
        AppController.shared.message(L("جارٍ قراءة الصفحة…", "Reading page…"))
        s.ocrPage(idx) { [weak self] in
            guard let self = self, let s = self.session else { return }
            let ws = s.words(idx)
            if let i = PageOverlayView.wordAt(ws, rel) { s.wordSel = WordSel(page: idx, words: [ws[i]]) }
        }
    }

    static func wordAt(_ ws: [OcrWord], _ p: RelPt) -> Int? {
        ws.firstIndex { p.x >= $0.l - 0.004 && p.x <= $0.r + 0.004 && p.y >= $0.t - 0.004 && p.y <= $0.b + 0.004 }
    }

    static func nearestWord(_ ws: [OcrWord], _ p: RelPt) -> Int? {
        if let i = wordAt(ws, p) { return i }
        var best: Int? = nil
        var bd = CGFloat.greatestFiniteMagnitude
        for (i, w) in ws.enumerated() {
            let dx = max(w.l - p.x, 0, p.x - w.r), dy = max(w.t - p.y, 0, p.y - w.b)
            let d = dy * 3 + dx
            if d < bd { bd = d; best = i }
        }
        return best
    }

    @objc private func onTextDrag(_ g: UIPanGestureRecognizer) {
        let p = g.location(in: content)
        switch g.state {
        case .began:
            textDragStart = p
            session?.finishInlineEdit()
        case .changed:
            let r = CGRect(x: min(textDragStart.x, p.x), y: min(textDragStart.y, p.y), width: abs(p.x - textDragStart.x), height: abs(p.y - textDragStart.y))
            textDragLayer.path = UIBezierPath(roundedRect: r, cornerRadius: 4).cgPath
        case .ended:
            textDragLayer.path = nil
            let r = CGRect(x: min(textDragStart.x, p.x), y: min(textDragStart.y, p.y), width: abs(p.x - textDragStart.x), height: abs(p.y - textDragStart.y))
            if r.width > content.bounds.width * 0.04 {
                session?.placeTextBox(pageId: pageId, at: CGPoint(x: r.minX / k, y: (r.minY + min(r.height, 30)) / k), width: r.width / k)
                AppController.shared.textActive = false
            }
        default:
            textDragLayer.path = nil
        }
    }

    @objc private func onUndoTap() { if AppController.shared.fingerUndo { session?.undo() } }
    @objc private func onRedoTap() { if AppController.shared.fingerUndo { session?.redo() } }
}

// ======================================================== رسم التمييز

/// التمييز وملء الأشكال: طبقات متجهة (لا صورة بحجم الصفحة)، فتبقى حادة بأي تكبير ولا تأخذ ذاكرة.
final class MarksView: UIView {
    var marks: [TextMark] = []
    /// عرض الصفحة بالنقاط على الشاشة (مستطيلات التمييز نسبية لعرض الصفحة).
    var k: CGFloat = 1
    /// ملء الأشكال (بنقاط الصفحة) ونسبة الطبقة إلى الصفحة.
    var fills: [ShapeFill] = []
    var pk: CGFloat = 1
    private let host = CALayer()

    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = false
        // التمييز يُمزج مع الصفحة بـ«الضرب» (كما في أندرويد): يلوّن الورق ويبقى النص تحته أسود واضحًا
        host.compositingFilter = "multiplyBlendMode"
        layer.insertSublayer(host, at: 0)
    }

    required init?(coder: NSCoder) { fatalError() }

    func rebuild() {
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        host.frame = bounds
        host.sublayers?.forEach { $0.removeFromSuperlayer() }
        // ملء الأشكال تحت الكتابة
        for f in fills where f.pts.count > 2 {
            let l = CAShapeLayer()
            let p = UIBezierPath()
            p.move(to: CGPoint(x: f.pts[0].x * pk, y: f.pts[0].y * pk))
            for q in f.pts.dropFirst() { p.addLine(to: CGPoint(x: q.x * pk, y: q.y * pk)) }
            p.close()
            l.path = p.cgPath
            l.fillColor = UIColor(argb: f.color).withAlphaComponent(f.alpha).cgColor
            host.addSublayer(l)
        }
        func add(_ r: MarkRect, _ color: UIColor) {
            let rr = r.scaled(k)
            let l = CALayer()
            l.frame = rr
            l.cornerRadius = rr.height * 0.32
            l.backgroundColor = color.cgColor
            host.addSublayer(l)
        }
        // لون شفاف فوق الصفحة: حتى إن لم يُطبَّق المزج يبقى النص ظاهرًا (والنتيجة على الورق الأبيض هي لون أندرويد نفسه)
        func tint(_ argb: UInt32, strong: Bool) -> UIColor { UIColor(argb: argb | 0xFF000000).withAlphaComponent(strong ? 0.9 : 0.55) }
        for m in marks {
            let c = tint(m.color, strong: m.level >= 2)
            for r in m.rects { add(r, c) }
            if m.level < 2 {
                let sc = tint(m.color, strong: true)
                for r in m.strongRects { add(r, sc) }
            }
            // خط ذهبي تحت المهم (المظلَّل مرتين)
            for r in (m.level >= 2 ? m.rects : m.strongRects) {
                let rr = r.scaled(k)
                let l = CALayer()
                l.frame = CGRect(x: rr.minX, y: rr.maxY - 2, width: rr.width, height: 2)
                l.backgroundColor = UIColor(Palette.goldDeep).cgColor
                host.addSublayer(l)
            }
        }
        CATransaction.commit()
    }
}

// ======================================================== الليزر

final class LaserView: UIView {
    var color: UIColor = .red { didSet { if color != oldValue { styleLayers() } } }
    var width: CGFloat = 6 { didSet { if width != oldValue { styleLayers() } } }
    var mode: LaserMode = .dot
    var fingerWrites = false
    private var trail: [(CGPoint, CFTimeInterval)] = []
    private var dot: CGPoint?
    private var link: CADisplayLink?
    /// الأثر: توهج عريض خافت، ثم خط بلون الليزر، ثم قلب أبيض رفيع (كشعاع ضوء). كلها على المعالج الرسومي.
    private let glow = CAShapeLayer()
    private let beam = CAShapeLayer()
    private let core = CAShapeLayer()
    private let head = CAShapeLayer()
    private let headCore = CAShapeLayer()

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isOpaque = false
        for l in [glow, beam, core, head, headCore] {
            l.fillColor = UIColor.clear.cgColor
            l.lineCap = .round
            l.lineJoin = .round
            l.actions = ["path": NSNull(), "opacity": NSNull(), "position": NSNull(), "bounds": NSNull()]
            layer.addSublayer(l)
        }
        styleLayers()
        // القلم يحوم فوق الشاشة دون لمسها: الليزر يتبعه (آيباد يدعم التحويم)
        addGestureRecognizer(UIHoverGestureRecognizer(target: self, action: #selector(onHover(_:))))
    }

    required init?(coder: NSCoder) { fatalError() }

    override func layoutSubviews() {
        super.layoutSubviews()
        let sc = window?.screen.scale ?? UIScreen.main.scale
        for l in [glow, beam, core, head, headCore] { l.frame = bounds; l.contentsScale = sc }
    }

    private func styleLayers() {
        let c = color.cgColor
        glow.strokeColor = color.withAlphaComponent(0.25).cgColor
        glow.lineWidth = width * 2.2
        glow.shadowColor = c
        glow.shadowOpacity = 0.9
        glow.shadowRadius = width * 1.2
        glow.shadowOffset = .zero
        beam.strokeColor = color.withAlphaComponent(0.9).cgColor
        beam.lineWidth = width
        core.strokeColor = UIColor.white.withAlphaComponent(0.85).cgColor
        core.lineWidth = max(1, width * 0.32)
        head.fillColor = c
        head.shadowColor = c
        head.shadowOpacity = 1
        head.shadowRadius = width * 1.6
        head.shadowOffset = .zero
        headCore.fillColor = UIColor.white.withAlphaComponent(0.9).cgColor
    }

    @objc private func onHover(_ g: UIHoverGestureRecognizer) {
        switch g.state {
        case .began, .changed:
            add(g.location(in: self))
            startLink()
        default:
            dot = nil
            render()
        }
    }

    private func accept(_ t: UITouch) -> Bool { fingerWrites || t.type == .pencil }

    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        if let t = event?.allTouches?.first, !accept(t) { return false }
        return super.point(inside: point, with: event)
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t = touches.first, accept(t) else { return }
        add(t.preciseLocation(in: self))
        startLink()
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t = touches.first, accept(t) else { return }
        for c in event?.coalescedTouches(for: t) ?? [t] { add(c.preciseLocation(in: self), render: false) }
        render()
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) { dot = nil; render() }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { dot = nil; render() }

    private func add(_ p: CGPoint, render now: Bool = true) {
        dot = p
        // نقاط متقاربة جدًا لا تضيف شيئًا (وتثقل المسار)
        if let last = trail.last?.0, hypot(p.x - last.x, p.y - last.y) < 0.8 { trail[trail.count - 1].1 = CACurrentMediaTime() } else {
            trail.append((p, CACurrentMediaTime()))
        }
        if now { render() }
    }

    private func startLink() {
        guard link == nil else { return }
        let l = CADisplayLink(target: self, selector: #selector(tick))
        l.add(to: .main, forMode: .common)
        link = l
    }

    @objc private func tick() {
        let now = CACurrentMediaTime()
        let life: CFTimeInterval = mode == .line ? 1.5 : 0.35
        trail.removeAll { now - $0.1 > life }
        render()
        if trail.isEmpty && dot == nil { link?.invalidate(); link = nil }
    }

    /// مسار ناعم عبر نقاط الأثر.
    private func smooth(_ pts: [CGPoint]) -> CGPath? {
        guard let f = pts.first, pts.count > 1 else { return nil }
        let p = CGMutablePath()
        p.move(to: f)
        if pts.count > 2 {
            for i in 1..<(pts.count - 1) {
                p.addQuadCurve(to: CGPoint(x: (pts[i].x + pts[i + 1].x) / 2, y: (pts[i].y + pts[i + 1].y) / 2), control: pts[i])
            }
        }
        p.addLine(to: pts[pts.count - 1])
        return p
    }

    private func render() {
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        defer { CATransaction.commit() }
        let path = smooth(trail.map { $0.0 })
        // الأثر يخفت كله كلما قصر (لا قطع بشفافيات مختلفة تظهر حبيبات عند التقائها)
        let life: CFTimeInterval = mode == .line ? 1.5 : 0.35
        let age = trail.first.map { CACurrentMediaTime() - $0.1 } ?? life
        let fade = Float(max(0.35, 1 - age / life * 0.5))
        for l in [glow, beam, core] {
            l.path = path
            l.opacity = fade
        }
        if let d = dot {
            let r = width * 0.95
            head.path = CGPath(ellipseIn: CGRect(x: d.x - r, y: d.y - r, width: r * 2, height: r * 2), transform: nil)
            let rc = width * 0.42
            headCore.path = CGPath(ellipseIn: CGRect(x: d.x - rc, y: d.y - rc, width: rc * 2, height: rc * 2), transform: nil)
        } else {
            head.path = nil
            headCore.path = nil
        }
    }
}

// ======================================================== أداة الأشكال

final class ShapeCaptureView: UIView {
    var kind: ShapeKind = .line
    /// لون الشكل وسماكته (بنقاط هذه الطبقة).
    var ink: (UInt32, CGFloat) = (0xFF000000, 2)
    var fingerWrites = false
    var style: LineStyle = .solid
    var rounded = true
    var arrowStart: ArrowHead = .none
    var arrowEnd: ArrowHead = .none
    var fill = false
    var fillAlpha: CGFloat = 0.2
    /// خطوط الشكل (بنقاط هذه الطبقة): الجسم (مملوء إن كان مغلقًا والملء مفعّلًا) ورؤوس الأسهم.
    var onShape: (([InkStroke]) -> Void)?
    private let fillPreview = CAShapeLayer()
    private let headPreview = CAShapeLayer()
    private var start: CGPoint?
    private var end: CGPoint?
    private let preview = CAShapeLayer()

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        preview.fillColor = UIColor.clear.cgColor
        preview.lineCap = .round
        preview.lineJoin = .miter
        headPreview.lineJoin = .miter
        layer.addSublayer(fillPreview)
        layer.addSublayer(preview)
        layer.addSublayer(headPreview)
    }

    required init?(coder: NSCoder) { fatalError() }

    private func accept(_ t: UITouch) -> Bool { fingerWrites || t.type == .pencil }

    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        if let t = event?.allTouches?.first, !accept(t) { return false }
        return super.point(inside: point, with: event)
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t = touches.first, accept(t) else { return }
        start = t.location(in: self)
        end = start
        update()
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t = touches.first, start != nil else { return }
        end = t.location(in: self)
        update()
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        defer { clear() }
        guard let a = start, let b = end, hypot(b.x - a.x, b.y - a.y) > 6 else { return }
        onShape?(shapeStrokes(a, b))
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { clear() }

    private func clear() {
        start = nil; end = nil
        preview.path = nil; fillPreview.path = nil; headPreview.path = nil
    }

    /// مضلع الملء للأشكال المغلقة.
    func fillPolygon(_ a: CGPoint, _ b: CGPoint) -> [CGPoint]? {
        guard fill, ![.line, .elbow, .curve].contains(kind) else { return nil }
        return Shapes.paths(kind, a, b, rounded: rounded).first
    }

    /// الشكل خطوطًا: الجسم بنمطه، ورؤوس الأسهم (المغلق مثلث مملوء بلون الخط). كلها بوقت واحد.
    func shapeStrokes(_ a: CGPoint, _ b: CGPoint) -> [InkStroke] {
        let parts = styledParts(a, b)
        let t = InkKey.now()
        var out: [InkStroke] = []
        if var body = parts.body.first, body.count > 1 {
            let closed = ![.line, .elbow, .curve].contains(kind)
            if closed, let f = body.first { body[body.count - 1] = f }
            var s = InkStroke(points: body, color: ink.0, width: ink.1, tool: .ballpoint, t: t, style: style, shape: true)
            if closed && fill {
                s.shapeFill = true
                s.fillAlpha = fillAlpha
            }
            out.append(s)
        }
        for h in parts.heads where h.count > 1 {
            var s = InkStroke(points: h, color: ink.0, width: ink.1, tool: .ballpoint, t: t, shape: true)
            if h.count > 3, h.first == h.last {
                s.shapeFill = true
                s.fillAlpha = 1
            }
            out.append(s)
        }
        return out
    }

    private func update() {
        guard let a = start, let b = end else { return }
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        defer { CATransaction.commit() }
        let parts = styledParts(a, b)
        let col = InkRender.cg(ink.0)
        preview.strokeColor = col
        preview.lineWidth = ink.1
        let d = InkRender.dash(style, width: ink.1)
        preview.lineDashPattern = d.isEmpty ? nil : d.map { NSNumber(value: Double($0)) }
        let p = UIBezierPath()
        for pts in parts.body {
            guard let f = pts.first else { continue }
            p.move(to: f)
            for q in pts.dropFirst() { p.addLine(to: q) }
        }
        preview.path = p.cgPath
        let hp = UIBezierPath()
        for h in parts.heads {
            guard let f = h.first else { continue }
            hp.move(to: f)
            for q in h.dropFirst() { hp.addLine(to: q) }
        }
        headPreview.strokeColor = col
        headPreview.fillColor = arrowEnd == .closed || arrowStart == .closed ? col : UIColor.clear.cgColor
        headPreview.lineWidth = ink.1
        headPreview.path = hp.cgPath
        if let poly = fillPolygon(a, b), let f = poly.first {
            let fp = UIBezierPath()
            fp.move(to: f)
            for q in poly.dropFirst() { fp.addLine(to: q) }
            fp.close()
            fillPreview.fillColor = InkRender.cg(ink.0, alpha: fillAlpha)
            fillPreview.path = fp.cgPath
        } else {
            fillPreview.path = nil
        }
    }
}

extension ShapeCaptureView {
    /// الشكل بنمطه: الزوايا المستديرة، ورؤوس الأسهم للخطوط (النمط المتقطع يُرسم من خصائص الخط).
    func styledParts(_ a: CGPoint, _ b: CGPoint) -> (body: [[CGPoint]], heads: [[CGPoint]]) {
        var paths = Shapes.paths(kind, a, b, rounded: rounded)
        var heads: [[CGPoint]] = []
        if [.line, .elbow, .curve].contains(kind), var main = paths.first, main.count > 1 {
            let len = StrokeKit.headLen(ink.1)
            // السهم المنحني سهمٌ كما في أيقونته: رأس في نهايته حتى لو لم يُختر رأس
            let endHead: ArrowHead = kind == .curve && arrowEnd == .none ? .open : arrowEnd
            let rev = Array(main.reversed())
            if let last = main.last, let h = StrokeKit.arrow(endHead, tip: last, from: StrokeKit.pointBack(main, len), width: ink.1) { heads.append(h) }
            if let first = main.first, let h = StrokeKit.arrow(arrowStart, tip: first, from: StrokeKit.pointBack(rev, len), width: ink.1) { heads.append(h) }
            // الخط يتوقف عند قاعدة الرأس المغلق فيبقى طرفه حادًا
            let cut = len * cos(StrokeKit.spread)
            if endHead == .closed { main = StrokeKit.trimEnd(main, cut) }
            if arrowStart == .closed { main = Array(StrokeKit.trimEnd(main.reversed(), cut).reversed()) }
            paths[0] = main
        }
        return (paths, heads)
    }

    /// كل خطوط الشكل كنقاط (الجسم ثم الرؤوس).
    func styledPaths(_ a: CGPoint, _ b: CGPoint) -> [[CGPoint]] {
        let p = styledParts(a, b)
        return p.body + p.heads
    }
}

/// نقاط الأشكال (كل شكل قائمة خطوط، وكل خط نقاط متقاربة).
enum Shapes {
    /// نقطة على منحنى بيزييه تكعيبي.
    static func cubic(_ p0: CGPoint, _ p1: CGPoint, _ p2: CGPoint, _ p3: CGPoint, _ t: CGFloat) -> CGPoint {
        let u = 1 - t
        let a = u * u * u, b = 3 * u * u * t, c = 3 * u * t * t, d = t * t * t
        let x = a * p0.x + b * p1.x + c * p2.x + d * p3.x
        let y = a * p0.y + b * p1.y + c * p2.y + d * p3.y
        return CGPoint(x: x, y: y)
    }

    static func dense(_ pts: [CGPoint], closed: Bool) -> [CGPoint] {
        var all = pts
        if closed, let f = pts.first { all.append(f) }
        var out: [CGPoint] = []
        for i in 0..<max(0, all.count - 1) {
            let a = all[i], b = all[i + 1]
            let n = max(2, Int(hypot(b.x - a.x, b.y - a.y) / 3))
            for j in 0..<n {
                let t = CGFloat(j) / CGFloat(n)
                out.append(CGPoint(x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t))
            }
        }
        if let l = all.last { out.append(l) }
        return out
    }

    static func paths(_ kind: ShapeKind, _ a: CGPoint, _ b: CGPoint, rounded: Bool = false) -> [[CGPoint]] {
        let r = CGRect(x: min(a.x, b.x), y: min(a.y, b.y), width: abs(b.x - a.x), height: abs(b.y - a.y))
        switch kind {
        case .line:
            return [dense([a, b], closed: false)]
        case .elbow:
            return [dense([a, CGPoint(x: b.x, y: a.y), b], closed: false)]
        case .curve:
            // مثل أيقونتها: منحنى يبدأ أفقيًا ويصعد، ثم جزء مستقيم حتى النهاية (ورأس سهم)
            let m = CGPoint(x: a.x + (b.x - a.x) * 0.62, y: b.y)
            let c1 = CGPoint(x: a.x + (m.x - a.x) * 0.6, y: a.y)
            let c2 = CGPoint(x: a.x + (m.x - a.x) * 0.4, y: m.y)
            var pts: [CGPoint] = []
            for i in 0...40 {
                let t = CGFloat(i) / 40
                pts.append(Shapes.cubic(a, c1, c2, m, t))
            }
            let tail: [CGPoint] = dense([m, b], closed: false)
            pts.append(contentsOf: tail.dropFirst())
            return [pts]
        case .rect, .triangle, .diamond:
            let c: [CGPoint]
            switch kind {
            case .rect: c = [CGPoint(x: r.minX, y: r.minY), CGPoint(x: r.maxX, y: r.minY), CGPoint(x: r.maxX, y: r.maxY), CGPoint(x: r.minX, y: r.maxY)]
            case .triangle: c = [CGPoint(x: r.midX, y: r.minY), CGPoint(x: r.maxX, y: r.maxY), CGPoint(x: r.minX, y: r.maxY)]
            default: c = [CGPoint(x: r.midX, y: r.minY), CGPoint(x: r.maxX, y: r.midY), CGPoint(x: r.midX, y: r.maxY), CGPoint(x: r.minX, y: r.midY)]
            }
            return [rounded ? StrokeKit.rounded(c) : dense(c, closed: true)]
        case .circle, .ellipse:
            var rr = r
            if kind == .circle {
                let s = max(r.width, r.height)
                rr = CGRect(x: a.x - s / 2 + (b.x - a.x) / 2, y: a.y - s / 2 + (b.y - a.y) / 2, width: s, height: s)
            }
            let pts = (0...72).map { i -> CGPoint in
                let t = CGFloat(i) / 72 * 2 * .pi
                return CGPoint(x: rr.midX + cos(t) * rr.width / 2, y: rr.midY + sin(t) * rr.height / 2)
            }
            return [pts]
        }
    }
}

// ======================================================== عناصر فوق الصفحة

/// نقطة الزاوية لتغيير الحجم (مربع النص المحدد والملاحظة المحددة).
final class CornerDot: UIView {
    init() {
        super.init(frame: CGRect(x: 0, y: 0, width: 44, height: 44))
        backgroundColor = .clear
        let dot = UIView(frame: CGRect(x: 13, y: 13, width: 18, height: 18))
        dot.backgroundColor = UIColor(Palette.navy)
        dot.layer.cornerRadius = 9
        dot.layer.borderColor = UIColor.white.cgColor
        dot.layer.borderWidth = 2
        dot.isUserInteractionEnabled = false
        addSubview(dot)
    }
    required init?(coder: NSCoder) { fatalError() }
}

/// مربع نص: نقرة تفتحه للتعديل (أو تتبع الرابط)، وسحب بالإصبع ينقله، ونقطة الزاوية تغيّر عرضه.
final class TextBoxView: UILabel {
    var onTap: (() -> Void)?
    var onMove: ((CGPoint) -> Void)?
    var onResize: ((CGFloat) -> Void)?
    private var dragStart: CGPoint = .zero
    private var pad: UIEdgeInsets = .zero
    private var startW: CGFloat = 0
    private var corner: CornerDot?

    init(box: TextBox, k: CGFloat, selected: Bool = false) {
        super.init(frame: .zero)
        numberOfLines = 0
        let size = box.size * k
        font = textBoxFont(box, size: size)
        var width = box.w * k
        if box.linkDoc != nil {
            // الرابط شارة بيضاوية بأيقونة رابط (كما في أندرويد)
            let navy = UIColor(Palette.navy)
            let a = NSMutableAttributedString()
            if let icon = UIImage(named: "ph_link")?.withTintColor(navy, renderingMode: .alwaysOriginal) {
                let att = NSTextAttachment()
                att.image = icon
                att.bounds = CGRect(x: 0, y: -size * 0.18, width: size, height: size)
                a.append(NSAttributedString(attachment: att))
                a.append(NSAttributedString(string: " "))
            }
            a.append(NSAttributedString(string: box.text, attributes: [.font: font as Any, .foregroundColor: navy]))
            attributedText = a
            pad = UIEdgeInsets(top: size * 0.25, left: size * 0.6, bottom: size * 0.25, right: size * 0.6)
            layer.backgroundColor = UIColor(Palette.navySoft).cgColor
            layer.borderColor = navy.withAlphaComponent(0.35).cgColor
            layer.borderWidth = 1
            let natural = a.boundingRect(with: CGSize(width: width, height: .greatestFiniteMagnitude), options: [.usesLineFragmentOrigin], context: nil)
            width = min(width, ceil(natural.width) + pad.left + pad.right + 2)
        } else {
            attributedText = textBoxAttributed(box, k: k)
        }
        let fit = sizeThatFits(CGSize(width: width - pad.left - pad.right, height: .greatestFiniteMagnitude))
        frame = CGRect(x: box.x * k, y: box.y * k, width: width, height: max(fit.height, size * 1.4) + pad.top + pad.bottom)
        if box.linkDoc != nil { layer.cornerRadius = min(frame.height / 2, size * 1.1) }
        if selected {
            layer.borderColor = UIColor(Palette.navy).cgColor
            layer.borderWidth = 1.5
            if box.linkDoc == nil { layer.cornerRadius = 4 }
            let c = CornerDot()
            c.center = CGPoint(x: bounds.maxX, y: bounds.maxY)
            c.autoresizingMask = [.flexibleLeftMargin, .flexibleTopMargin]
            let rp = UIPanGestureRecognizer(target: self, action: #selector(resize(_:)))
            c.addGestureRecognizer(rp)
            addSubview(c)
            corner = c
        }
        isUserInteractionEnabled = true
        addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(tap)))
        let pan = UIPanGestureRecognizer(target: self, action: #selector(pan(_:)))
        pan.allowedTouchTypes = [NSNumber(value: UITouch.TouchType.direct.rawValue)]
        addGestureRecognizer(pan)
    }

    required init?(coder: NSCoder) { fatalError() }

    override func drawText(in rect: CGRect) { super.drawText(in: rect.inset(by: pad)) }

    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        if let c = corner, c.frame.contains(point) { return true }
        return super.point(inside: point, with: event)
    }

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        if let c = corner, c.frame.contains(point) { return c }
        return super.hitTest(point, with: event)
    }

    @objc private func tap() { onTap?() }

    @objc private func resize(_ g: UIPanGestureRecognizer) {
        if g.state == .began { startW = frame.width }
        let w = max(30, startW + g.translation(in: superview).x)
        switch g.state {
        case .changed: frame.size.width = w
        case .ended: onResize?(w)
        default: frame.size.width = startW
        }
    }

    @objc private func pan(_ g: UIPanGestureRecognizer) {
        let t = g.translation(in: superview)
        switch g.state {
        case .changed: transform = CGAffineTransform(translationX: t.x, y: t.y)
        case .ended:
            transform = .identity
            onMove?(t)
        default: transform = .identity
        }
    }
}

/// الشريط الصغير فوق مربع الكتابة (نفس InlineChip في أندرويد): Aa، A−، A+، حذف، ✓ تم.
final class InlineMiniBar: UIView {
    var onAction: ((Int) -> Void)?
    private let stack = UIStackView()

    init() {
        super.init(frame: .zero)
        backgroundColor = .white
        layer.cornerRadius = 12
        layer.shadowColor = UIColor.black.cgColor
        layer.shadowOpacity = 0.18
        layer.shadowRadius = 6
        layer.shadowOffset = CGSize(width: 0, height: 2)
        stack.axis = .horizontal
        stack.spacing = 4
        stack.semanticContentAttribute = .forceLeftToRight
        let titles = ["Aa", "A−", "A+", L("حذف", "Delete"), "✓ " + L("تم", "Done")]
        for (i, t) in titles.enumerated() {
            let b = UIButton(type: .system)
            b.tag = i
            b.setTitle(t, for: .normal)
            b.titleLabel?.font = Fonts.ui(14, bold: i == 4)
            b.contentEdgeInsets = UIEdgeInsets(top: 6, left: 10, bottom: 6, right: 10)
            b.layer.cornerRadius = 9
            if i == 4 {
                b.backgroundColor = UIColor(Palette.navy)
                b.setTitleColor(.white, for: .normal)
            } else {
                b.backgroundColor = UIColor(argb: 0xFFEEF0F3)
                b.setTitleColor(i == 3 ? UIColor(Palette.danger) : UIColor(Palette.navy), for: .normal)
            }
            b.addTarget(self, action: #selector(hit(_:)), for: .touchUpInside)
            stack.addArrangedSubview(b)
        }
        addSubview(stack)
    }

    required init?(coder: NSCoder) { fatalError() }

    func fitting() -> CGSize {
        let s = stack.systemLayoutSizeFitting(UIView.layoutFittingCompressedSize)
        return CGSize(width: s.width + 10, height: s.height + 10)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        stack.frame = bounds.insetBy(dx: 5, dy: 5)
    }

    @objc private func hit(_ b: UIButton) { onAction?(b.tag) }
}

/// الكتابة بلوحة المفاتيح داخل ملاحظة لاصقة.
final class NoteTextEditor: UITextView, UITextViewDelegate {
    var onEnd: ((String) -> Void)?
    private var done = false

    init(frame: CGRect) {
        super.init(frame: frame, textContainer: nil)
        backgroundColor = UIColor.white.withAlphaComponent(0.5)
        textColor = UIColor(argb: 0xFF1F2933)
        textContainerInset = .zero
        textContainer.lineFragmentPadding = 0
        layer.cornerRadius = 3
        delegate = self
    }

    required init?(coder: NSCoder) { fatalError() }

    func commit() {
        guard !done else { return }
        done = true
        onEnd?(text ?? "")
    }

    func textViewDidEndEditing(_ textView: UITextView) { commit() }
}

/// الصور معكوسة الألوان للقراءة الليلية (الطبقة الليلية تعكسها ثانيةً فتظهر بألوانها).
enum NightImages {
    private static let cache = NSCache<NSString, UIImage>()

    static func inverted(_ key: String, _ img: UIImage?) -> UIImage? {
        guard let img = img else { return nil }
        if let c = cache.object(forKey: key as NSString) { return c }
        let maxSide: CGFloat = 1600
        let sc = min(1, maxSide / max(1, max(img.size.width, img.size.height) * img.scale))
        let size = CGSize(width: img.size.width * sc, height: img.size.height * sc)
        let fmt = UIGraphicsImageRendererFormat()
        fmt.scale = img.scale
        fmt.opaque = false
        let rect = CGRect(origin: .zero, size: size)
        let out = UIGraphicsImageRenderer(size: size, format: fmt).image { ctx in
            img.draw(in: rect)
            ctx.cgContext.setBlendMode(.difference)
            UIColor.white.setFill()
            ctx.cgContext.fill(rect)
            // يبقى شفافًا ما كان شفافًا
            img.draw(in: rect, blendMode: .destinationIn, alpha: 1)
        }
        cache.setObject(out, forKey: key as NSString)
        return out
    }
}

/// ملاحظة لاصقة.
final class StickyNoteView: UIView {
    var onTap: (() -> Void)?
    var onMove: ((CGPoint) -> Void)?
    var onResize: ((CGSize) -> Void)?
    private var corner: CornerDot?
    private var startSize: CGSize = .zero

    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        if let c = corner, c.frame.contains(point) { return true }
        return super.point(inside: point, with: event)
    }

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        if let c = corner, c.frame.contains(point) { return c }
        return super.hitTest(point, with: event)
    }

    @objc private func resize(_ g: UIPanGestureRecognizer) {
        if g.state == .began { startSize = frame.size }
        let t = g.translation(in: superview)
        let size = CGSize(width: max(40, startSize.width + t.x), height: max(40, startSize.height + t.y))
        switch g.state {
        case .changed: frame.size = size
        case .ended: onResize?(size)
        default: frame.size = startSize
        }
    }

    /// الملاحظة بنمطها (سادة، شريط، أسطر، مربعات)، وكتابة القلم عليها، ونصها، والاسم عليها.
    init(note: StickyNote, k: CGFloat, selected: Bool = false) {
        let collapsed = note.collapsed
        let side: CGFloat = 34 * k
        super.init(frame: collapsed
                   ? CGRect(x: note.x * k, y: note.y * k, width: side, height: side)
                   : CGRect(x: note.x * k, y: note.y * k, width: note.w * k, height: note.h * k))
        backgroundColor = UIColor(argb: note.color)
        layer.cornerRadius = collapsed ? 8 : 4
        layer.shadowColor = UIColor.black.cgColor
        layer.shadowOpacity = 0.18
        layer.shadowRadius = 4
        layer.shadowOffset = CGSize(width: 0, height: 2)
        if selected {
            layer.borderColor = UIColor(Palette.navy).cgColor
            layer.borderWidth = 1.5
        }
        if collapsed {
            let iv = UIImageView(image: UIImage(named: "ph_sticky_note")?.withRenderingMode(.alwaysTemplate))
            iv.tintColor = UIColor(argb: 0xFF6B4E00)
            iv.frame = bounds.insetBy(dx: 7, dy: 7)
            iv.contentMode = .scaleAspectFit
            addSubview(iv)
        } else {
            let w = bounds.width, h = bounds.height
            let paper = UIView(frame: bounds)
            paper.isUserInteractionEnabled = false
            let lines = CAShapeLayer()
            let path = UIBezierPath()
            let step = max(8, w * 0.1)
            switch note.style {
            case 1:
                let bar = UIView(frame: CGRect(x: 0, y: 0, width: max(5, w * 0.05), height: h))
                bar.backgroundColor = UIColor.black.withAlphaComponent(0.12)
                paper.addSubview(bar)
            case 2:
                var y = step * 1.6
                while y < h - 4 { path.move(to: CGPoint(x: w * 0.06, y: y)); path.addLine(to: CGPoint(x: w * 0.94, y: y)); y += step }
            case 3:
                var y = step
                while y < h { path.move(to: CGPoint(x: 0, y: y)); path.addLine(to: CGPoint(x: w, y: y)); y += step }
                var x = step
                while x < w { path.move(to: CGPoint(x: x, y: 0)); path.addLine(to: CGPoint(x: x, y: h)); x += step }
            default: break
            }
            lines.path = path.cgPath
            lines.strokeColor = UIColor.black.withAlphaComponent(0.14).cgColor
            lines.lineWidth = 0.8
            paper.layer.addSublayer(lines)
            addSubview(paper)
            if !note.text.isEmpty || note.ink == nil {
                let l = UILabel(frame: bounds.insetBy(dx: w * 0.07, dy: w * 0.07))
                l.numberOfLines = 0
                l.font = Fonts.ui(max(9, note.w * k * 0.075))
                l.textColor = UIColor(argb: 0xFF1F2933)
                l.text = note.text.isEmpty ? L("اكتب بالقلم هنا", "Write here with your pen") : note.text
                l.alpha = note.text.isEmpty ? 0.45 : 1
                l.sizeToFit()
                l.frame.size.width = w * 0.86
                addSubview(l)
            }
            // كتابة القلم على الملاحظة (بنقاط الملاحظة)
            if let raw = note.ink, let d = InkDrawing.load(raw), !d.strokes.isEmpty {
                let iv = UIImageView(frame: bounds)
                let scaled = d.transformed(using: CGAffineTransform(scaleX: k, y: k))
                iv.image = scaled.lightImage(from: bounds, scale: UIScreen.main.scale * 2)
                iv.isUserInteractionEnabled = false
                addSubview(iv)
            }
            if !note.author.isEmpty {
                let a = UILabel()
                a.font = Fonts.ui(max(7, note.w * k * 0.055))
                a.textColor = UIColor.black.withAlphaComponent(0.5)
                a.text = "— " + note.author
                a.sizeToFit()
                a.frame.origin = CGPoint(x: w - a.frame.width - w * 0.05, y: h - a.frame.height - w * 0.03)
                addSubview(a)
            }
        }
        // الملاحظة المحددة: نقطة الزاوية تغيّر حجمها
        if selected && !collapsed {
            let c = CornerDot()
            c.center = CGPoint(x: bounds.maxX, y: bounds.maxY)
            c.autoresizingMask = [.flexibleLeftMargin, .flexibleTopMargin]
            c.addGestureRecognizer(UIPanGestureRecognizer(target: self, action: #selector(resize(_:))))
            addSubview(c)
            corner = c
        }
        addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(tap)))
        let pan = UIPanGestureRecognizer(target: self, action: #selector(pan(_:)))
        pan.allowedTouchTypes = [NSNumber(value: UITouch.TouchType.direct.rawValue)]
        addGestureRecognizer(pan)
    }

    required init?(coder: NSCoder) { fatalError() }

    @objc private func tap() { onTap?() }

    @objc private func pan(_ g: UIPanGestureRecognizer) {
        let t = g.translation(in: superview)
        switch g.state {
        case .changed: transform = CGAffineTransform(translationX: t.x, y: t.y)
        case .ended:
            transform = .identity
            onMove?(t)
        default: transform = .identity
        }
    }
}

/// إطار تحديد الصورة: سحب ينقلها، والمقبض يغيّر حجمها، وزر الحذف.
final class ImageSelectionView: UIView {
    var onChange: ((CGRect) -> Void)?
    var onDelete: (() -> Void)?
    private var startFrame: CGRect = .zero

    override init(frame: CGRect) {
        super.init(frame: frame)
        // إطار كحلي رفيع ومقبض دائري صغير في الزاوية (كما في أندرويد)؛ الحذف من الشريط السفلي
        layer.borderColor = UIColor(Palette.navy).cgColor
        layer.borderWidth = 2
        backgroundColor = .clear
        addGestureRecognizer(UIPanGestureRecognizer(target: self, action: #selector(dragMove(_:))))
        addGestureRecognizer(UIPinchGestureRecognizer(target: self, action: #selector(pinch(_:))))
        let touch = UIView(frame: CGRect(x: bounds.width - 22, y: bounds.height - 22, width: 44, height: 44))
        touch.autoresizingMask = [.flexibleLeftMargin, .flexibleTopMargin]
        touch.backgroundColor = .clear
        let dot = UIView(frame: CGRect(x: 12, y: 12, width: 20, height: 20))
        dot.backgroundColor = UIColor(Palette.navy)
        dot.layer.cornerRadius = 10
        dot.isUserInteractionEnabled = false
        let inner = UIView(frame: CGRect(x: 6, y: 6, width: 8, height: 8))
        inner.backgroundColor = .white
        inner.layer.cornerRadius = 4
        dot.addSubview(inner)
        touch.addSubview(dot)
        touch.addGestureRecognizer(UIPanGestureRecognizer(target: self, action: #selector(resize(_:))))
        addSubview(touch)
        clipsToBounds = false
    }

    required init?(coder: NSCoder) { fatalError() }


    /// المقبض يبرز خارج الإطار قليلًا: يُلمس هناك أيضًا.
    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        bounds.insetBy(dx: -22, dy: -22).contains(point)
    }

    @objc private func dragMove(_ g: UIPanGestureRecognizer) {
        if g.state == .began { startFrame = frame }
        let t = g.translation(in: superview)
        frame = startFrame.offsetBy(dx: t.x, dy: t.y)
        if g.state == .ended { onChange?(frame) }
    }

    @objc private func pinch(_ g: UIPinchGestureRecognizer) {
        if g.state == .began { startFrame = frame }
        let w = max(30, startFrame.width * g.scale), h = w * startFrame.height / max(1, startFrame.width)
        frame = CGRect(x: startFrame.midX - w / 2, y: startFrame.midY - h / 2, width: w, height: h)
        if g.state == .ended { onChange?(frame) }
    }

    @objc private func resize(_ g: UIPanGestureRecognizer) {
        if g.state == .began { startFrame = frame }
        let t = g.translation(in: superview)
        let w = max(30, startFrame.width + t.x)
        frame = CGRect(x: startFrame.minX, y: startFrame.minY, width: w, height: w * startFrame.height / max(1, startFrame.width))
        if g.state == .ended { onChange?(frame) }
    }
}
