import UIKit
import UIKit.UIGestureRecognizerSubclass

// طبقة الكتابة (بدل PKCanvasView): تعرض حبر الصفحة، وتستقبل القلم (والإصبع إن سُمح له)، وترسم الخط الحي
// بطبقة متجهة على المعالج الرسومي (لا إعادة رسم للصفحة أثناء الكتابة)، وتمسح بالممحاة.
// الحبر محفوظ بنقاط الصفحة؛ k = نسبة نقاط هذه الطبقة إلى نقاط الصفحة.

/// إعدادات الأداة الحالية للكتابة.
struct InkToolConfig {
    var tool: Tool = .fountain
    var color: UInt32 = 0xFF000000
    /// سماكة القلم بنقاط الصفحة.
    var width: CGFloat = 1.5
    var style: LineStyle = .solid
    var eraserMode: EraserMode = .stroke
    /// نصف قطر الممحاة بنقاط الصفحة.
    var eraserRadius: CGFloat = 5
    /// حساسية الضغط 0…1 (0.5 بلا تغيير)، وحدة الطرف، وتثبيت الخط.
    var pressure: Double = 0.5
    var sharpness: Double = 0.25
    var stabilize: Double = 0
    /// الهايلايتر بخط مستقيم.
    var straight = false
    var canErase: (InkStroke) -> Bool = { _ in true }
}

/// يلتقط حركة القلم مباشرة (بلا التأخير الذي يفرضه التمرير على اللمسات العادية).
final class InkInput: UIGestureRecognizer {
    weak var canvas: InkCanvas?
    private var touch: UITouch?

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        guard let c = canvas else { state = .failed; return }
        if touch != nil {
            // إصبع ثانٍ أثناء الكتابة بالإصبع: تمرير أو تكبير، لا كتابة
            if touches.contains(where: { $0.type == .direct }) && touch?.type == .direct {
                c.cancelStroke()
                touch = nil
                state = .cancelled
            }
            return
        }
        guard touches.count == 1, let t = touches.first, (event.allTouches?.count ?? 1) == 1, c.accepts(t) else {
            state = .failed
            return
        }
        touch = t
        c.beginStroke(t, event)
        state = .began
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
        guard let t = touch, touches.contains(t) else { return }
        canvas?.moveStroke(t, event)
        state = .changed
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
        guard let t = touch, touches.contains(t) else { return }
        canvas?.endStroke(t, event)
        touch = nil
        state = .ended
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
        guard let t = touch, touches.contains(t) else { return }
        canvas?.cancelStroke()
        touch = nil
        state = .cancelled
    }

    override func reset() {
        super.reset()
        touch = nil
    }

    override func canPrevent(_ preventedGestureRecognizer: UIGestureRecognizer) -> Bool { false }
    override func canBePrevented(by preventingGestureRecognizer: UIGestureRecognizer) -> Bool { false }
}

/// حبر الصفحة المثبّت: يُرسم مرة، ويُعاد رسم الجزء المتغير فقط.
final class InkStaticView: UIView {
    var strokes: [InkStroke] = []
    var k: CGFloat = 1
    /// نقطة الصفحة في أعلى يسار الطبقة (للنافذة المكبّرة التي تعرض جزءًا من الصفحة).
    var origin: CGPoint = .zero

    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = false
        backgroundColor = .clear
        isUserInteractionEnabled = false
        contentMode = .redraw
        layer.drawsAsynchronously = true
    }

    required init?(coder: NSCoder) { fatalError() }

    override func draw(_ rect: CGRect) {
        guard let c = UIGraphicsGetCurrentContext(), !strokes.isEmpty, k > 0 else { return }
        // الحبر بألوانه كما اختيرت في كل المظاهر
        c.scaleBy(x: k, y: k)
        c.translateBy(x: -origin.x, y: -origin.y)
        let pr = CGRect(x: rect.minX / k + origin.x, y: rect.minY / k + origin.y, width: rect.width / k, height: rect.height / k)
        let visible = strokes.filter { let b = $0.renderBounds; return !b.isNull && b.intersects(pr) }
        InkRender.drawStack(visible, in: c)
    }
}

final class InkCanvas: UIView, UIGestureRecognizerDelegate {
    /// الإصبع يكتب أيضًا (وإلا القلم وحده، والإصبع يمرّر الصفحة).
    var fingerWrites = false
    /// تقبل الكتابة الآن (تُطفأ مع الليزر والأشكال والتحديد).
    var writingEnabled = true
    var config = InkToolConfig()
    /// عرض الصفحة بنقاطها (لحدة الطرف وسرعة الإصبع).
    var pageW: CGFloat = 600

    var k: CGFloat = 1 {
        didSet {
            guard abs(k - oldValue) > 0.0001 else { return }
            staticView.k = k
            staticView.setNeedsDisplay()
        }
    }

    /// نقطة الصفحة في أعلى يسار الطبقة (0 للصفحة كاملة).
    var origin: CGPoint = .zero {
        didSet {
            guard origin != oldValue else { return }
            staticView.origin = origin
            staticView.setNeedsDisplay()
        }
    }

    /// من نقاط الصفحة إلى نقاط الطبقة.
    private var toLayer: CGAffineTransform {
        CGAffineTransform(translationX: -origin.x, y: -origin.y).concatenating(CGAffineTransform(scaleX: k, y: k))
    }

    /// بدأت الكتابة أو المسح، وانتهت.
    var onBegin: (() -> Void)?
    var onEnd: (() -> Void)?
    /// خط جديد اكتمل (بنقاط الصفحة): المضيف يقرر ما يفعل به ثم يضيفه بـ append.
    var onStroke: ((InkStroke) -> Void)?
    /// انتهى مسح غيّر الحبر: الحبر الجديد كاملًا.
    var onErase: ((InkDrawing) -> Void)?

    private(set) var drawing = InkDrawing()
    let input = InkInput()
    private let staticView = InkStaticView()
    /// الخط الحي: مسار بعرض ثابت، أو مسار مملوء لقلم الحبر والفرشاة.
    private let liveLine = CAShapeLayer()
    private let liveFill = CAShapeLayer()
    private let livePredict = CAShapeLayer()
    private var livePath = CGMutablePath()

    private var pts: [InkPt] = []
    private var smooth: CGPoint = .zero
    private var speed = InkPen.SpeedWidth()
    private var erasing = false
    private var eraseChanged = false
    private var lastErase: CGPoint?
    private var active = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = false
        backgroundColor = .clear
        isMultipleTouchEnabled = true
        staticView.frame = bounds
        staticView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        addSubview(staticView)
        for l in [liveFill, liveLine, livePredict] {
            l.fillColor = UIColor.clear.cgColor
            l.lineCap = .round
            l.lineJoin = .round
            l.actions = ["path": NSNull(), "opacity": NSNull(), "strokeColor": NSNull(), "fillColor": NSNull(), "lineWidth": NSNull()]
            layer.addSublayer(l)
        }
        input.canvas = self
        input.delegate = self
        input.cancelsTouchesInView = false
        input.delaysTouchesBegan = false
        input.delaysTouchesEnded = false
        addGestureRecognizer(input)
    }

    required init?(coder: NSCoder) { fatalError() }

    override func layoutSubviews() {
        super.layoutSubviews()
        staticView.frame = bounds
        // حد لذاكرة صورة الحبر (نحو 16 مليون بكسل)، فالصفحة المكبّرة جدًا لا تستهلك الذاكرة
        let screen = window?.screen.scale ?? UIScreen.main.scale
        let px = max(1, bounds.width * bounds.height)
        let s = min(screen, sqrt(16_000_000 / px))
        if abs(staticView.contentScaleFactor - s) > 0.01 {
            staticView.contentScaleFactor = max(0.5, s)
            staticView.setNeedsDisplay()
        }
        for l in [liveFill, liveLine, livePredict] { l.frame = bounds; l.contentsScale = screen }
    }

    func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }

    // ---------------------------------------------------------------- الحبر

    /// يضع حبر الصفحة (بنقاطها) ويعيد رسمه.
    func setDrawing(_ d: InkDrawing) {
        drawing = d
        staticView.strokes = d.strokes
        staticView.isHidden = d.strokes.isEmpty
        staticView.setNeedsDisplay()
    }

    /// يضيف خطًا (أو أكثر) في آخر الحبر ويرسمه وحده.
    func append(_ strokes: [InkStroke]) {
        guard !strokes.isEmpty else { return }
        drawing.strokes.append(contentsOf: strokes)
        staticView.strokes = drawing.strokes
        staticView.isHidden = false
        let r = strokes.reduce(CGRect.null) { $0.union($1.renderBounds) }
        redraw(r)
    }

    private func redraw(_ pageRect: CGRect) {
        guard !pageRect.isNull, pageRect.width.isFinite else { staticView.setNeedsDisplay(); return }
        staticView.setNeedsDisplay(pageRect.applying(toLayer).insetBy(dx: -2, dy: -2))
    }

    // ---------------------------------------------------------------- اللمس

    func accepts(_ t: UITouch) -> Bool {
        guard writingEnabled, isUserInteractionEnabled, !isHidden else { return false }
        return t.type == .pencil || (fingerWrites && t.type == .direct)
    }

    private func pagePoint(_ t: UITouch) -> CGPoint {
        let p = t.preciseLocation(in: self)
        return CGPoint(x: p.x / max(0.0001, k) + origin.x, y: p.y / max(0.0001, k) + origin.y)
    }

    private func pressure(_ t: UITouch, at p: CGPoint) -> CGFloat {
        let raw: CGFloat
        if t.type == .pencil {
            raw = InkPen.pencilPressure(force: t.force, maxForce: t.maximumPossibleForce)
        } else {
            raw = speed.next(p, time: t.timestamp, pageW: pageW)
        }
        return InkPen.applyPressure(raw, sensitivity: config.pressure)
    }

    func beginStroke(_ t: UITouch, _ event: UIEvent) {
        active = true
        onBegin?()
        let p = pagePoint(t)
        if config.tool == .eraser {
            erasing = true
            eraseStart = drawing.strokes
            eraseChanged = false
            lastErase = nil
            eraseTo(p)
            return
        }
        erasing = false
        speed = InkPen.SpeedWidth()
        smooth = p
        pts = [InkPt(p, pressure(t, at: p))]
        livePath = CGMutablePath()
        updateLive(predicted: [])
    }

    func moveStroke(_ t: UITouch, _ event: UIEvent) {
        guard active else { return }
        let all = event.coalescedTouches(for: t) ?? [t]
        if erasing {
            for c in all { eraseTo(pagePoint(c)) }
            return
        }
        let follow = CGFloat(1 - config.stabilize * 0.85)
        let minGap = 0.4 / max(0.0001, k)
        for c in all {
            let raw = pagePoint(c)
            if config.stabilize > 0.01 {
                smooth.x += (raw.x - smooth.x) * follow
                smooth.y += (raw.y - smooth.y) * follow
            } else {
                smooth = raw
            }
            guard let last = pts.last, hypot(smooth.x - last.x, smooth.y - last.y) >= minGap else { continue }
            pts.append(InkPt(smooth, pressure(c, at: smooth)))
        }
        let predicted = (event.predictedTouches(for: t) ?? []).map { InkPt(pagePoint($0), pts.last?.p ?? 0.5) }
        updateLive(predicted: predicted)
    }

    func endStroke(_ t: UITouch, _ event: UIEvent) {
        guard active else { return }
        active = false
        if erasing {
            erasing = false
            lastErase = nil
            if eraseChanged { onErase?(drawing) }
            onEnd?()
            return
        }
        // آخر نقطة في موضع القلم الحقيقي حتى لا يقصر الخط بعد التثبيت
        let end = pagePoint(t)
        if let last = pts.last, hypot(end.x - last.x, end.y - last.y) > 0.05 { pts.append(InkPt(end, last.p)) }
        let stroke = finishedStroke()
        clearLive()
        if let s = stroke { onStroke?(s) }
        onEnd?()
    }

    func cancelStroke() {
        guard active else { return }
        active = false
        if erasing {
            erasing = false
            // المسح الملغى يُعاد كما كان
            if eraseChanged { setDrawing(InkDrawing(strokes: eraseStart)) }
        }
        pts = []
        clearLive()
        onEnd?()
    }

    /// الحبر قبل بدء المسح (يعود إن أُلغي).
    private var eraseStart: [InkStroke] = []

    /// الخط المكتمل بعد معالجة القلم.
    private func finishedStroke() -> InkStroke? {
        guard !pts.isEmpty else { return nil }
        let tool = config.tool
        var p = pts
        if tool == .highlighter && config.straight { p = InkPen.straighten(p) }
        p = InkPen.taper(p, tool: tool, sharpness: CGFloat(config.sharpness), pageW: pageW)
        var s = InkStroke(pts: p, color: config.color, width: config.width, tool: tool, t: InkKey.unused(in: drawing.strokes))
        if tool.isPen { s.style = config.style }
        pts = []
        return s
    }

    // ---------------------------------------------------------------- الخط الحي

    private func clearLive() {
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        liveLine.path = nil
        liveFill.path = nil
        livePredict.path = nil
        CATransaction.commit()
        livePath = CGMutablePath()
    }

    private func updateLive(predicted: [InkPt]) {
        guard !pts.isEmpty else { return }
        let tool = config.tool
        let scale = toLayer
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        defer { CATransaction.commit() }
        let col = InkRender.cg(config.color)
        let variable = (tool == .fountain || tool == .brush) && config.style == .solid
        if variable {
            // قلم الحبر والفرشاة: كل مقطع بسماكته حسب الضغط، يُضاف إلى مسار مملوء (الجديد فقط في كل حركة)
            let base: CGFloat = tool == .brush ? 0.15 : 0.4
            let gain: CGFloat = tool == .brush ? 1.5 : 1
            let done = livePathCount
            if pts.count == 1 {
                let a = pts[0]
                let r = config.width * (base + gain * a.p) / 2 * k
                livePath = CGMutablePath()
                let c = a.cg.applying(scale)
                livePath.addEllipse(in: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2))
            } else {
                for i in max(1, done)..<pts.count {
                    let seg = CGMutablePath()
                    seg.move(to: pts[i - 1].cg.applying(scale))
                    seg.addLine(to: pts[i].cg.applying(scale))
                    let w = max(0.05, config.width * (base + gain * pts[i].p) * k)
                    livePath.addPath(seg.copy(strokingWithWidth: w, lineCap: .round, lineJoin: .round, miterLimit: 1))
                }
            }
            livePathCount = pts.count
            liveFill.fillColor = col
            liveFill.fillRule = .nonZero
            liveFill.path = livePath
            liveLine.path = nil
            // التنبؤ: امتداد رفيع يقلل الإحساس بالتأخير
            if let last = pts.last, !predicted.isEmpty {
                let pp = CGMutablePath()
                pp.move(to: last.cg.applying(scale))
                for q in predicted { pp.addLine(to: q.cg.applying(scale)) }
                livePredict.strokeColor = col
                livePredict.lineWidth = max(0.5, config.width * (base + gain * last.p) * k)
                livePredict.opacity = 1
                livePredict.path = pp
            } else {
                livePredict.path = nil
            }
            return
        }
        livePathCount = 0
        // أقلام بعرض ثابت: مسار ناعم واحد، والشفافية على الطبقة كلها (فلا تغمق التقاطعات)
        let showPts: [InkPt]
        if tool == .highlighter && config.straight, let f = pts.first {
            showPts = [f, predicted.last ?? pts[pts.count - 1]]
        } else {
            showPts = pts
        }
        let path = InkRender.smoothPath(showPts, extra: tool == .highlighter && config.straight ? [] : predicted)
        var tr = scale
        liveLine.path = path.copy(using: &tr)
        liveLine.strokeColor = col
        liveLine.lineWidth = config.width * (tool == .pencil ? 0.85 : 1) * k
        liveLine.opacity = Float(InkRender.alpha(tool))
        let d = InkRender.dash(config.style, width: config.width * k)
        liveLine.lineDashPattern = d.isEmpty || !tool.isPen ? nil : d.map { NSNumber(value: Double($0)) }
        liveFill.path = nil
        livePredict.path = nil
    }

    private var livePathCount = 0

    // ---------------------------------------------------------------- الممحاة

    private func eraseTo(_ p: CGPoint) {
        let r = config.eraserRadius
        // نقاط بين الموضع السابق والحالي حتى لا تقفز الممحاة فوق خط رفيع
        var samples: [CGPoint] = [p]
        if let a = lastErase {
            let d = hypot(p.x - a.x, p.y - a.y)
            let n = min(60, Int(d / max(0.3, r * 0.5)))
            if n > 1 { samples = (1...n).map { i in CGPoint(x: a.x + (p.x - a.x) * CGFloat(i) / CGFloat(n), y: a.y + (p.y - a.y) * CGFloat(i) / CGFloat(n)) } }
        }
        lastErase = p
        var cur = drawing.strokes
        var dirty = CGRect.null
        for q in samples {
            let touched = cur.filter { config.canErase($0) && InkErase.hits($0, at: q, radius: r) }
            guard !touched.isEmpty else { continue }
            let next = config.eraserMode == .area
                ? InkErase.area(cur, at: q, radius: r, canErase: config.canErase)
                : InkErase.whole(cur, at: q, radius: r, canErase: config.canErase)
            if let n = next {
                cur = n
                dirty = touched.reduce(dirty) { $0.union($1.renderBounds) }
            }
        }
        guard !dirty.isNull else { return }
        eraseChanged = true
        drawing.strokes = cur
        staticView.strokes = cur
        redraw(dirty)
    }
}
