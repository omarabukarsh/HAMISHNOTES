import UIKit
import UIKit.UIGestureRecognizerSubclass
import PDFKit
import PencilKit
import SwiftUI

/// كانفاس PencilKit لا يقبل لمس الإصبع إلا إن سُمح للإصبع بالكتابة،
/// فتمر لمسات الإصبع إلى عارض PDF للتمرير والتكبير.
final class InkCanvas: PKCanvasView {
    var fingerWrites = false
}

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
final class HoldTracker: UIGestureRecognizer {
    private(set) var held = false
    private var lastMove: CFTimeInterval = 0
    private var lastPt: CGPoint = .zero

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        held = false
        lastMove = CACurrentMediaTime()
        lastPt = touches.first?.location(in: view) ?? .zero
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
        guard let p = touches.first?.location(in: view) else { return }
        if hypot(p.x - lastPt.x, p.y - lastPt.y) > 6 { lastMove = CACurrentMediaTime(); lastPt = p }
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
        held = CACurrentMediaTime() - lastMove >= 0.45
        state = .failed
    }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) { held = false; state = .failed }
    override func reset() { }
    /// يصلح سواء وصل رفع القلم إلى هنا قبل حفظ الخط أو بعده.
    var heldNow: Bool { held || (lastMove > 0 && CACurrentMediaTime() - lastMove >= 0.45) }
    func consume() { held = false; lastMove = 0 }
}

/// طبقة فوق كل صفحة: التمييز، الصور، الكتابة بالقلم، النصوص، الملاحظات اللاصقة، الليزر، والأشكال.
/// كل الإحداثيات المحفوظة بنقاط الصفحة (الأصل أعلى اليسار)؛ k = نسبة عرض الطبقة إلى عرض الصفحة.
final class PageOverlayView: UIView, PKCanvasViewDelegate, UIGestureRecognizerDelegate {
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
    private var applying = false
    private var lastStrokeCount = 0
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

        canvas.backgroundColor = .clear
        canvas.isOpaque = false
        canvas.delegate = self
        canvas.isScrollEnabled = false
        canvas.contentInsetAdjustmentBehavior = .never
        canvas.showsVerticalScrollIndicator = false
        canvas.showsHorizontalScrollIndicator = false
        canvas.minimumZoomScale = 1
        canvas.maximumZoomScale = 1
        hold.cancelsTouchesInView = false
        hold.delaysTouchesBegan = false
        hold.delaysTouchesEnded = false
        hold.delegate = self
        canvas.addGestureRecognizer(hold)
        content.addSubview(canvas)

        topLayer.backgroundColor = .clear
        topLayer.pencilPasses = { let c = AppController.shared; return !c.textActive && !c.stickyActive && !c.lassoActive }
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
        shapes.onShape = { [weak self] strokes, fill in self?.commitShape(strokes, fill: fill) }
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

        layoutContent()
        setDrawing(session.drawing(for: pageId))
        applyTool()
        reloadItems()
    }

    required init?(coder: NSCoder) { fatalError() }

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
        canvas.drawingPolicy = finger ? .anyInput : .pencilOnly
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
        if let tool = PageOverlayView.pkTool(scale: k) { canvas.tool = tool }
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

    /// أداة PencilKit للأداة المختارة، بعرض مضروب في scale (نسبة الشاشة إلى نقاط الصفحة).
    static func pkTool(scale k: CGFloat) -> PKTool? {
        let c = AppController.shared
        let t = c.tool
        let col = UIColor(argb: c.colorFor(t))
        let w = Sizes.width(t, c.sizeFor(t)) * k
        switch t {
        case .ballpoint: return PKInkingTool(.monoline, color: col, width: w)
        case .fountain: return PKInkingTool(.fountainPen, color: col, width: w * 1.4)
        case .pencil: return PKInkingTool(.pencil, color: col, width: w * 1.6)
        case .brush: return PKInkingTool(.watercolor, color: col, width: w * 3)
        case .highlighter: return PKInkingTool(.marker, color: col.withAlphaComponent(0.6), width: w)
        case .eraser: return c.eraserMode == .stroke ? PKEraserTool(.vector) : PKEraserTool(.bitmap, width: w)
        case .laser: return nil
        }
    }

    private func shapeInk() -> (PKInk, CGFloat) {
        let c = AppController.shared
        return (PKInk(.monoline, color: UIColor(argb: c.shapeColor)), Sizes.width(.ballpoint, c.shapeSize) * k)
    }

    // ======================================================== الحبر

    func setDrawing(_ d: PKDrawing) {
        applying = true
        canvas.drawing = k == 1 ? d : d.transformed(using: CGAffineTransform(scaleX: k, y: k))
        applying = false
        lastStrokeCount = canvas.drawing.strokes.count
    }

    /// الحبر بإحداثيات الصفحة.
    private var pageDrawing: PKDrawing {
        k == 1 ? canvas.drawing : canvas.drawing.transformed(using: CGAffineTransform(scaleX: 1 / k, y: 1 / k))
    }

    func canvasViewDrawingDidChange(_ canvasView: PKCanvasView) {
        guard !applying, let s = session else { return }
        let app = AppController.shared
        let baseCount = lastStrokeCount
        app.noteInteraction()
        // فلتر الممحاة: ما يحميه المستخدم لا يُمسح
        if app.tool == .eraser {
            let old = s.drawing(for: pageId)
            if let kept = PenProcessing.protect(old: old, new: pageDrawing, canErase: app.canErase) {
                setDrawing(kept)
                if kept.dataRepresentation() == old.dataRepresentation() { return }
            }
        }
        // خط جديد: تثبيت الخط، حساسية الضغط، حدة الطرف، وخط الهايلايتر المستقيم
        if canvas.drawing.strokes.count == lastStrokeCount + 1, let last = canvas.drawing.strokes.last,
           (app.tool.isPen || app.tool == .highlighter), !app.shapeActive, last.randomSeed != shapeSeed,
           let ns = PenProcessing.apply(last, tool: app.tool, pageW: pageSize.width * k,
                                        stabilize: app.tool == .highlighter ? app.hlStabilize : app.penStabilize,
                                        pressure: app.penPressure, sharpness: app.penSharpness, straight: app.hlStraight) {
            var d = canvas.drawing
            d.strokes[d.strokes.count - 1] = ns
            applying = true
            canvas.drawing = d
            applying = false
        }
        let strokes = canvas.drawing.strokes
        // خط هايلايتر جديد: يُلتقط النص تحته
        if strokes.count == lastStrokeCount + 1, let last = strokes.last, last.ink.inkType == .marker,
           AppController.shared.tool == .highlighter, !AppController.shared.shapeActive {
            lastStrokeCount = strokes.count
            if handleHighlight(last) { return }
        }
        let added = strokes.count == lastStrokeCount + 1
        var converted = false
        if added, AppController.shared.tool.isPen, !AppController.shared.shapeActive, let last = strokes.last {
            converted = restyle(last)
            // شخبطة أو خط فوق كلمة متحوّلة = حذفها، و∧ = مكان إضافة: الخط لا يُحفظ
            if !converted, let pl = pageDrawing.strokes.last, AutoFont.shared.gesture(s, pageId: pageId, stroke: pl) {
                var d = canvas.drawing
                d.strokes.removeLast()
                applying = true
                canvas.drawing = d
                applying = false
                lastStrokeCount = d.strokes.count
                hold.consume()
                return
            }
        }
        // الكتابة بالقلم على ملاحظة لاصقة تصير جزءًا منها (تتحرك معها)
        if added, !converted, app.tool.isPen, let last = canvas.drawing.strokes.last, last.randomSeed != shapeSeed {
            let b = last.renderBounds
            let pb = CGRect(x: b.minX / k, y: b.minY / k, width: b.width / k, height: b.height / k)
            if let n = s.data.notes.last(where: { $0.page == pageId && !$0.collapsed && CGRect(x: $0.x, y: $0.y, width: $0.w, height: $0.h).insetBy(dx: -2, dy: -2).contains(pb) }) {
                var d = canvas.drawing
                d.strokes.removeLast()
                applying = true
                canvas.drawing = d
                applying = false
                lastStrokeCount = d.strokes.count
                var st = last
                st.transform = last.transform.concatenating(CGAffineTransform(scaleX: 1 / k, y: 1 / k)).concatenating(CGAffineTransform(translationX: -n.x, y: -n.y))
                var ink = (n.ink.flatMap { try? PKDrawing(data: $0) }) ?? PKDrawing()
                ink.strokes.append(st)
                var m = n
                m.ink = ink.dataRepresentation()
                s.upsertNote(m)
                return
            }
        }
        // الهايلايتر تحت الكتابة (كما في أندرويد): خطوطه تُنقل تحت خطوط القلم
        if canvas.drawing.strokes.count == baseCount + 1, let last = canvas.drawing.strokes.last, last.ink.inkType == .marker,
           canvas.drawing.strokes.dropLast().contains(where: { $0.ink.inkType != .marker }) {
            var d = canvas.drawing
            let all = d.strokes
            let firstInk = all.firstIndex { $0.ink.inkType != .marker } ?? all.count - 1
            d.strokes.removeLast()
            d.strokes.insert(last, at: firstInk)
            applying = true
            canvas.drawing = d
            applying = false
        }
        lastStrokeCount = canvas.drawing.strokes.count
        let pd = pageDrawing
        s.inkChanged(pageId, pd)
        if app.tool == .eraser, s.pruneFills(pageId) { reloadMarks() }
        if added, !converted, AppController.shared.tool.isPen, !AppController.shared.shapeActive, let last = pd.strokes.last {
            AutoFont.shared.onStroke(s, pageId: pageId, stroke: last)
        }
    }

    /// «ارسم وأبقِ» (شكل مرتب) ونمط خط القلم (متقطع/منقّط) للخط الجديد. true = صار شكلًا.
    private func restyle(_ last: PKStroke) -> Bool {
        let c = AppController.shared
        let pts = last.path.interpolatedPoints(by: .distance(2)).map { $0.location.applying(last.transform) }
        let w = last.path.first?.size.width ?? Sizes.width(c.tool, c.sizeFor(c.tool)) * k
        var paths: [[CGPoint]]? = nil
        var shape = false
        if c.drawAndHold && hold.heldNow, let r = ShapeRecognizer.recognize(pts, pageW: pageSize.width * k) {
            paths = r
            shape = true
        }
        hold.consume()
        if c.penStyle != .solid {
            let base = paths ?? [pts]
            paths = base.flatMap { StrokeKit.split($0, style: c.penStyle, width: w) }
        }
        guard let ps = paths else { return false }
        var d = canvas.drawing
        d.strokes.removeLast()
        d.strokes.append(contentsOf: StrokeKit.strokes(ps, ink: last.ink, width: w, seed: shape ? shapeSeed : last.randomSeed, date: last.path.creationDate))
        applying = true
        canvas.drawing = d
        applying = false
        return shape
    }

    func canvasViewDidBeginUsingTool(_ canvasView: PKCanvasView) { AutoFont.shared.penDown() }
    func canvasViewDidEndUsingTool(_ canvasView: PKCanvasView) {
        AutoFont.shared.penUp()
        // الرجوع إلى القلم تلقائيًا بعد المسح
        let c = AppController.shared
        if c.tool == .eraser && c.autoDeselect {
            DispatchQueue.main.async { c.selectTool(c.lastWriting == .eraser ? c.lastPen : c.lastWriting) }
        }
    }

    private func commitShape(_ strokes: [PKStroke], fill: [CGPoint]?) {
        guard let s = session else { return }
        var d = canvas.drawing
        d.strokes.append(contentsOf: strokes)
        applying = true
        canvas.drawing = d
        applying = false
        lastStrokeCount = d.strokes.count
        var f: ShapeFill? = nil
        if let poly = fill, let first = strokes.first {
            let c = AppController.shared
            f = ShapeFill(id: UUID().uuidString, page: pageId, pts: poly.map { CGPoint(x: $0.x / k, y: $0.y / k) },
                          color: c.shapeColor, alpha: CGFloat(c.shapeFillAlpha), key: first.path.creationDate.timeIntervalSinceReferenceDate)
        }
        s.addShape(pageId, pageDrawing, fill: f)
        reloadMarks()
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
    private func handleHighlight(_ stroke: PKStroke) -> Bool {
        guard let s = session, let index = s.index(of: pageId) else { return false }
        let c = AppController.shared
        guard c.highlightSnap && Premium.shared.active else { return false }
        let W = pageSize.width * k
        var pts: [RelPt] = []
        for p in stroke.path.interpolatedPoints(by: .distance(3)) {
            let q = p.location.applying(stroke.transform)
            pts.append(RelPt(x: q.x / W, y: q.y / W))
        }
        let band = Sizes.width(.highlighter, c.sizeFor(.highlighter)) / 600
        if s.snapHighlight(index, pts: pts, color: c.colorFor(.highlighter), band: band) {
            var d = canvas.drawing
            d.strokes.removeLast()
            applying = true
            canvas.drawing = d
            applying = false
            lastStrokeCount = d.strokes.count
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
        for im in s.data.images where im.page == pageId {
            let v = UIImageView(image: s.image(for: im))
            v.contentMode = .scaleAspectFit
            v.frame = CGRect(x: im.x * k, y: im.y * k, width: im.w * k, height: im.w / max(0.01, im.aspect) * k)
            imagesLayer.addSubview(v)
        }
        // المربع الذي يُكتب فيه يبقى كما هو (لا يُعاد إنشاؤه فلا تختفي لوحة المفاتيح)
        let keepInline = inlineView.flatMap { v in s.inlineEditId == v.boxId && s.data.boxes.contains { $0.id == v.boxId && $0.page == pageId } ? v : nil }
        topLayer.subviews.forEach { if $0 !== keepInline { $0.removeFromSuperview() } }
        if keepInline == nil { inlineView = nil }
        for n in s.data.notes where n.page == pageId {
            let v = StickyNoteView(note: n, k: k, selected: s.selectedNote == n.id)
            v.onTap = { [weak self] in
                guard let s = self?.session else { return }
                // نقرة تحدد الملاحظة (وشريطها أسفل الشاشة)، والمطوية تُفتح
                if n.collapsed { var m = n; m.collapsed = false; s.upsertNote(m); return }
                s.selectedNote = s.selectedNote == n.id ? nil : n.id
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
                continue
            }
            let v = TextBoxView(box: b, k: k, selected: s.selectedBox == b.id)
            v.onTap = { [weak self] in
                guard let self = self, let s = self.session else { return }
                // بأداة النص: نقرة تكتب فيه؛ والرابط بنقرة ثانية يُفتح؛ وغير ذلك نقرة تحدده (وشريطه أسفل الشاشة)
                if AppController.shared.textActive { s.beginInlineEdit(b.id); return }
                if let d = b.linkDoc, s.selectedBox == b.id { AppController.shared.followLink(from: s, docId: d, page: b.linkPage); return }
                if s.selectedBox == b.id { s.beginInlineEdit(b.id) } else { s.finishInlineEdit(); s.selectedBox = b.id }
            }
            v.onMove = { [weak self] delta in
                var m = b
                m.x += delta.x / (self?.k ?? 1)
                m.y += delta.y / (self?.k ?? 1)
                self?.session?.upsertBox(m)
                self?.reloadItems()
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
        // نقرة تلغي تحديد نص الكتاب
        if s.textSel != nil { s.textSel = nil; return }
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
            return
        }
        if s.selectedNote != nil { s.selectedNote = nil }
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
        switch g.state {
        case .began:
            selAnchor = pt
            if let sel = page.selectionForWord(at: pt), !(sel.string ?? "").trimmingCharacters(in: .whitespaces).isEmpty {
                s.textSel = sel
            } else {
                selAnchor = nil
            }
        case .changed:
            guard let a = selAnchor, let sel = page.selection(from: a, to: pt) else { return }
            s.textSel = sel
        default:
            selAnchor = nil
        }
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
        for m in marks {
            let c = markColor(m.color, strong: m.level >= 2)
            for r in m.rects { add(r, c) }
            if m.level < 2 {
                let sc = markColor(m.color, strong: true)
                for r in m.strongRects { add(r, sc) }
            }
        }
        CATransaction.commit()
    }
}

// ======================================================== الليزر

final class LaserView: UIView {
    var color: UIColor = .red
    var width: CGFloat = 6
    var mode: LaserMode = .dot
    var fingerWrites = false
    private var trail: [(CGPoint, CFTimeInterval)] = []
    private var dot: CGPoint?
    private var link: CADisplayLink?

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isOpaque = false
    }

    required init?(coder: NSCoder) { fatalError() }

    private func accept(_ t: UITouch) -> Bool { fingerWrites || t.type == .pencil }

    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        if let t = event?.allTouches?.first, !accept(t) { return false }
        return super.point(inside: point, with: event)
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t = touches.first, accept(t) else { return }
        add(t.location(in: self))
        startLink()
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t = touches.first, accept(t) else { return }
        for c in event?.coalescedTouches(for: t) ?? [t] { add(c.location(in: self)) }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) { dot = nil }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { dot = nil }

    private func add(_ p: CGPoint) {
        dot = p
        trail.append((p, CACurrentMediaTime()))
        setNeedsDisplay()
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
        setNeedsDisplay()
        if trail.isEmpty && dot == nil { link?.invalidate(); link = nil }
    }

    override func draw(_ rect: CGRect) {
        guard let c = UIGraphicsGetCurrentContext() else { return }
        let now = CACurrentMediaTime()
        let life: CFTimeInterval = mode == .line ? 1.5 : 0.35
        c.setLineCap(.round)
        c.setLineJoin(.round)
        if trail.count > 1 {
            for i in 1..<trail.count {
                let a = 1 - CGFloat((now - trail[i].1) / life)
                c.setStrokeColor(color.withAlphaComponent(max(0, a) * 0.85).cgColor)
                c.setLineWidth(width * (mode == .line ? 1 : 0.7))
                c.move(to: trail[i - 1].0)
                c.addLine(to: trail[i].0)
                c.strokePath()
            }
        }
        if let d = dot {
            c.setShadow(offset: .zero, blur: width * 2.5, color: color.cgColor)
            c.setFillColor(color.cgColor)
            c.fillEllipse(in: CGRect(x: d.x - width, y: d.y - width, width: width * 2, height: width * 2))
            c.setFillColor(UIColor.white.withAlphaComponent(0.85).cgColor)
            c.fillEllipse(in: CGRect(x: d.x - width * 0.4, y: d.y - width * 0.4, width: width * 0.8, height: width * 0.8))
        }
    }
}

// ======================================================== أداة الأشكال

final class ShapeCaptureView: UIView {
    var kind: ShapeKind = .line
    var ink: (PKInk, CGFloat) = (PKInk(.monoline, color: .black), 2)
    var fingerWrites = false
    var style: LineStyle = .solid
    var rounded = true
    var arrowStart: ArrowHead = .none
    var arrowEnd: ArrowHead = .none
    var fill = false
    var fillAlpha: CGFloat = 0.2
    /// الخطوط، ومضلع الملء (للأشكال المغلقة إن كان الملء مفعّلًا).
    var onShape: (([PKStroke], [CGPoint]?) -> Void)?
    private let fillPreview = CAShapeLayer()
    private var start: CGPoint?
    private var end: CGPoint?
    private let preview = CAShapeLayer()

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        preview.fillColor = UIColor.clear.cgColor
        preview.lineCap = .round
        preview.lineJoin = .round
        layer.addSublayer(fillPreview)
        layer.addSublayer(preview)
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
        preview.strokeColor = ink.0.color.cgColor
        preview.lineWidth = ink.1
        update()
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t = touches.first, start != nil else { return }
        end = t.location(in: self)
        update()
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        defer { start = nil; end = nil; preview.path = nil; fillPreview.path = nil }
        guard let a = start, let b = end, hypot(b.x - a.x, b.y - a.y) > 6 else { return }
        let strokes = StrokeKit.strokes(styledPaths(a, b), ink: ink.0, width: ink.1)
        onShape?(strokes, fillPolygon(a, b))
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        start = nil; end = nil; preview.path = nil; fillPreview.path = nil
    }

    /// مضلع الملء للأشكال المغلقة.
    func fillPolygon(_ a: CGPoint, _ b: CGPoint) -> [CGPoint]? {
        guard fill, ![.line, .elbow, .curve].contains(kind) else { return nil }
        return Shapes.paths(kind, a, b, rounded: rounded).first
    }

    private func update() {
        guard let a = start, let b = end else { return }
        let p = UIBezierPath()
        for pts in styledPaths(a, b) {
            guard let f = pts.first else { continue }
            p.move(to: f)
            for q in pts.dropFirst() { p.addLine(to: q) }
        }
        preview.path = p.cgPath
        if let poly = fillPolygon(a, b), let f = poly.first {
            let fp = UIBezierPath()
            fp.move(to: f)
            for q in poly.dropFirst() { fp.addLine(to: q) }
            fp.close()
            fillPreview.fillColor = ink.0.color.withAlphaComponent(fillAlpha).cgColor
            fillPreview.path = fp.cgPath
        } else {
            fillPreview.path = nil
        }
    }
}

extension ShapeCaptureView {
    /// الشكل بنمطه: الزوايا المستديرة، ورؤوس الأسهم للخطوط، والتقطيع.
    func styledPaths(_ a: CGPoint, _ b: CGPoint) -> [[CGPoint]] {
        var paths = Shapes.paths(kind, a, b, rounded: rounded)
        var heads: [[CGPoint]] = []
        if [.line, .elbow, .curve].contains(kind), var main = paths.first, main.count > 1 {
            let len = StrokeKit.headLen(ink.1)
            // السهم المنحني سهمٌ كما في أيقونته: رأس في نهايته حتى لو لم يُختر رأس
            let endHead: ArrowHead = kind == .curve && arrowEnd == .none ? .open : arrowEnd
            let rev = Array(main.reversed())
            if let h = StrokeKit.arrow(endHead, tip: main.last!, from: StrokeKit.pointBack(main, len), width: ink.1) {
                heads.append(h)
                if endHead == .closed { heads += StrokeKit.fillTriangle(h, width: ink.1) }
            }
            if let h = StrokeKit.arrow(arrowStart, tip: main.first!, from: StrokeKit.pointBack(rev, len), width: ink.1) {
                heads.append(h)
                if arrowStart == .closed { heads += StrokeKit.fillTriangle(h, width: ink.1) }
            }
            // الخط يتوقف عند قاعدة الرأس المغلق فيبقى طرفه حادًا
            let cut = len * cos(StrokeKit.spread)
            if endHead == .closed { main = StrokeKit.trimEnd(main, cut) }
            if arrowStart == .closed { main = Array(StrokeKit.trimEnd(main.reversed(), cut).reversed()) }
            paths[0] = main
        }
        var body = paths
        if style != .solid, let main = body.first { body = StrokeKit.split(main, style: style, width: ink.1) + body.dropFirst() }
        return body + heads.map { Shapes.dense($0, closed: false) }
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

/// مربع نص: نقرة تفتحه للتعديل (أو تتبع الرابط)، وسحب بالإصبع ينقله.
final class TextBoxView: UILabel {
    var onTap: (() -> Void)?
    var onMove: ((CGPoint) -> Void)?
    private var dragStart: CGPoint = .zero

    init(box: TextBox, k: CGFloat, selected: Bool = false) {
        super.init(frame: .zero)
        numberOfLines = 0
        let size = box.size * k
        font = textBoxFont(box, size: size)
        if box.linkDoc != nil {
            attributedText = NSAttributedString(string: "🔗 " + box.text, attributes: [
                .underlineStyle: NSUnderlineStyle.single.rawValue, .font: font as Any, .foregroundColor: UIColor(Palette.navy)
            ])
        } else {
            attributedText = textBoxAttributed(box, k: k)
        }
        let fit = sizeThatFits(CGSize(width: box.w * k, height: .greatestFiniteMagnitude))
        frame = CGRect(x: box.x * k, y: box.y * k, width: box.w * k, height: max(fit.height, size * 1.4))
        if selected {
            layer.borderColor = UIColor(Palette.navy).cgColor
            layer.borderWidth = 1.5
            layer.cornerRadius = 4
        }
        isUserInteractionEnabled = true
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

/// ملاحظة لاصقة.
final class StickyNoteView: UIView {
    var onTap: (() -> Void)?
    var onMove: ((CGPoint) -> Void)?

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
            if let raw = note.ink, let d = try? PKDrawing(data: raw), !d.strokes.isEmpty {
                let iv = UIImageView(frame: bounds)
                let scaled = d.transformed(using: CGAffineTransform(scaleX: k, y: k))
                iv.image = scaled.image(from: bounds, scale: UIScreen.main.scale * 2)
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
