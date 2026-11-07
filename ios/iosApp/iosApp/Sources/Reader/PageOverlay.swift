import UIKit
import PDFKit
import PencilKit
import SwiftUI

/// كانفاس PencilKit لا يقبل لمس الإصبع إلا إن سُمح للإصبع بالكتابة،
/// فتمر لمسات الإصبع إلى عارض PDF للتمرير والتكبير.
final class InkCanvas: PKCanvasView {
    var fingerWrites = false
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
    private let marksView = MarksView()
    private let imagesLayer = UIView()
    private let topLayer = UIView()
    private let laser = LaserView()
    private let shapes = ShapeCaptureView()
    private var selectionFrame: ImageSelectionView?
    private var applying = false
    private var lastStrokeCount = 0
    private var k: CGFloat = 1

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

        marksView.isUserInteractionEnabled = false
        marksView.backgroundColor = .clear
        addSubview(marksView)
        imagesLayer.isUserInteractionEnabled = false
        addSubview(imagesLayer)

        canvas.backgroundColor = .clear
        canvas.isOpaque = false
        canvas.delegate = self
        canvas.isScrollEnabled = false
        canvas.contentInsetAdjustmentBehavior = .never
        canvas.showsVerticalScrollIndicator = false
        canvas.showsHorizontalScrollIndicator = false
        canvas.minimumZoomScale = 1
        canvas.maximumZoomScale = 1
        addSubview(canvas)

        topLayer.backgroundColor = .clear
        addSubview(topLayer)

        laser.isHidden = true
        addSubview(laser)
        shapes.isHidden = true
        shapes.onShape = { [weak self] strokes in self?.commitShape(strokes) }
        addSubview(shapes)

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

        applying = true
        canvas.drawing = session.drawing(for: pageId)
        applying = false
        lastStrokeCount = canvas.drawing.strokes.count
        applyTool()
        reloadItems()
    }

    required init?(coder: NSCoder) { fatalError() }

    override func layoutSubviews() {
        super.layoutSubviews()
        let nk = bounds.width / max(1, pageSize.width)
        for v in [marksView, imagesLayer, canvas, topLayer, laser, shapes] as [UIView] { v.frame = bounds }
        if abs(nk - k) > 0.0001 {
            let old = k
            k = nk
            applying = true
            canvas.drawing = canvas.drawing.transformed(using: CGAffineTransform(scaleX: nk / old, y: nk / old))
            applying = false
            applyTool()
            reloadItems()
        }
        marksView.k = k
        marksView.setNeedsDisplay()
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

        if c.lassoActive {
            canvas.tool = PKLassoTool()
            return
        }
        let t = c.tool
        let col = UIColor(argb: c.colorFor(t))
        let w = Sizes.width(t, c.sizeFor(t)) * k
        switch t {
        case .ballpoint: canvas.tool = PKInkingTool(.monoline, color: col, width: w)
        case .fountain: canvas.tool = PKInkingTool(.fountainPen, color: col, width: w * 1.4)
        case .pencil: canvas.tool = PKInkingTool(.pencil, color: col, width: w * 1.6)
        case .brush: canvas.tool = PKInkingTool(.watercolor, color: col, width: w * 3)
        case .highlighter: canvas.tool = PKInkingTool(.marker, color: col.withAlphaComponent(0.6), width: w)
        case .eraser:
            canvas.tool = c.eraserMode == .stroke ? PKEraserTool(.vector) : PKEraserTool(.bitmap, width: w)
        case .laser: break
        }
        if shapeOn {
            shapes.ink = shapeInk()
            shapes.kind = c.shapeKind
        }
    }

    private func shapeInk() -> (PKInk, CGFloat) {
        let c = AppController.shared
        let t = c.tool
        let col = UIColor(argb: c.colorFor(t))
        let w = Sizes.width(t, c.sizeFor(t)) * k
        if t == .highlighter { return (PKInk(.marker, color: col.withAlphaComponent(0.6)), w) }
        return (PKInk(.monoline, color: col), w)
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
        AppController.shared.noteInteraction()
        let strokes = canvas.drawing.strokes
        // خط هايلايتر جديد: يُلتقط النص تحته
        if strokes.count == lastStrokeCount + 1, let last = strokes.last, last.ink.inkType == .marker,
           AppController.shared.tool == .highlighter, !AppController.shared.shapeActive {
            lastStrokeCount = strokes.count
            if handleHighlight(last) { return }
        }
        lastStrokeCount = strokes.count
        s.inkChanged(pageId, pageDrawing)
    }

    private func commitShape(_ strokes: [PKStroke]) {
        guard let s = session else { return }
        var d = canvas.drawing
        d.strokes.append(contentsOf: strokes)
        applying = true
        canvas.drawing = d
        applying = false
        lastStrokeCount = d.strokes.count
        s.inkChanged(pageId, pageDrawing)
    }

    // ======================================================== التمييز والتقاط النص

    /// نقطة من الطبقة إلى إحداثيات PDF.
    private func toPdf(_ p: CGPoint) -> CGPoint? {
        guard let v = pdfView, let page = page else { return nil }
        return v.convert(convert(p, to: v), to: page)
    }

    /// مستطيل PDF إلى إحداثيات الصفحة المحفوظة.
    private func fromPdf(_ r: CGRect) -> CGRect? {
        guard let v = pdfView, let page = page else { return nil }
        let vr = convert(v.convert(r, from: page), from: v)
        return CGRect(x: vr.minX / k, y: vr.minY / k, width: vr.width / k, height: vr.height / k)
    }

    /// يلتقط نص السطر تحت خط الهايلايتر. true = استُبدل الخط بتمييز مرتب.
    private func handleHighlight(_ stroke: PKStroke) -> Bool {
        guard let s = session, let page = page else { return false }
        let c = AppController.shared
        var pts: [CGPoint] = []
        for p in stroke.path.interpolatedPoints(by: .distance(4)) { pts.append(p.location.applying(stroke.transform)) }
        var lo = Int.max, hi = Int.min
        for p in pts {
            guard let q = toPdf(p) else { continue }
            let i = page.characterIndex(at: q)
            if i >= 0 { lo = min(lo, i); hi = max(hi, i) }
        }
        var text = ""
        var rects: [CGRect] = []
        if lo <= hi, let sel = page.selection(for: NSRange(location: lo, length: hi - lo + 1)) {
            text = sel.string ?? ""
            for line in sel.selectionsByLine() {
                if let r = fromPdf(line.bounds(for: page)) { rects.append(r) }
            }
        } else {
            // صفحة ممسوحة: النص من القراءة الضوئية
            let bb = stroke.renderBounds
            let pb = CGRect(x: bb.minX / k, y: bb.minY / k, width: bb.width / k, height: bb.height / k)
            let lines = (s.ocr[pageId] ?? []).filter { $0.rect.rect.intersects(pb) }
            text = lines.map { $0.text }.joined(separator: "\n")
            rects = lines.map { $0.rect.rect.intersection(pb.insetBy(dx: 0, dy: -4)) }
        }
        text = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let col = c.colorFor(.highlighter)
        if c.highlightSnap && Premium.shared.active && !rects.isEmpty {
            // التمييز المرتب يحل محل الخط الحر
            var d = canvas.drawing
            d.strokes.removeLast()
            applying = true
            canvas.drawing = d
            applying = false
            lastStrokeCount = d.strokes.count
            // التظليل مرتين على نفس السطر = مهم
            if let old = s.marks(onPage: pageId).first(where: { m in !m.free && m.rects.contains { r in rects.contains { $0.intersects(r.rect.insetBy(dx: 2, dy: 2)) } } }),
               old.text == text || text.isEmpty {
                s.updateMark(old.id) { $0.level = 2 }
            } else {
                s.addMark(TextMark(id: UUID().uuidString, page: pageId, rects: rects.map(MarkRect.init), color: col,
                                   text: text, createdAt: nowMs(), inSummary: c.highlightToSummary && c.summariesOn))
            }
            reloadMarks()
            return true
        }
        if !text.isEmpty && c.highlightToSummary && c.summariesOn {
            let bb = stroke.renderBounds
            s.addMark(TextMark(id: UUID().uuidString, page: pageId,
                               rects: [MarkRect(CGRect(x: bb.minX / k, y: bb.minY / k, width: bb.width / k, height: bb.height / k))],
                               color: col, text: text, createdAt: nowMs(), free: true))
        }
        return false
    }

    func reloadMarks() {
        marksView.marks = session?.marks(onPage: pageId).filter { !$0.free } ?? []
        marksView.k = k
        marksView.setNeedsDisplay()
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
        topLayer.subviews.forEach { $0.removeFromSuperview() }
        for n in s.data.notes where n.page == pageId {
            let v = StickyNoteView(note: n, k: k)
            v.onTap = { [weak self] in self?.session?.editingNote = n }
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
            let v = TextBoxView(box: b, k: k)
            v.onTap = { [weak self] in
                guard let self = self, let s = self.session else { return }
                if let d = b.linkDoc { AppController.shared.followLink(from: s, docId: d, page: b.linkPage) }
                else { s.editingBox = b }
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

    @objc private func onTap(_ g: UITapGestureRecognizer) {
        guard let s = session else { return }
        let c = AppController.shared
        let p = g.location(in: self)
        let pp = CGPoint(x: p.x / k, y: p.y / k)
        if let idx = s.index(of: pageId) { s.setCurrent(idx) }
        if c.textActive {
            let box = TextBox(id: UUID().uuidString, page: pageId, x: pp.x, y: pp.y, w: pageSize.width * 0.4, text: "",
                              size: max(12, pageSize.width * 0.024))
            s.editingBox = box
            c.textActive = false
            c.objectWillChange.send()
            return
        }
        if c.stickyActive {
            let w = pageSize.width * 0.26
            let n = StickyNote(id: UUID().uuidString, page: pageId, x: pp.x - w / 2, y: pp.y - w / 2, w: w, h: w, color: c.stickyColor)
            s.upsertNote(n)
            s.editingNote = n
            reloadItems()
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

    @objc private func onUndoTap() { if AppController.shared.fingerUndo { session?.undo() } }
    @objc private func onRedoTap() { if AppController.shared.fingerUndo { session?.redo() } }
}

// ======================================================== رسم التمييز

final class MarksView: UIView {
    var marks: [TextMark] = []
    var k: CGFloat = 1

    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = false
        contentMode = .redraw
    }

    required init?(coder: NSCoder) { fatalError() }

    override func draw(_ rect: CGRect) {
        guard let c = UIGraphicsGetCurrentContext() else { return }
        c.setBlendMode(.multiply)
        for m in marks {
            let col = UIColor(argb: m.color).withAlphaComponent(m.level > 1 ? 0.62 : 0.42)
            c.setFillColor(col.cgColor)
            for r in m.rects {
                let rr = CGRect(x: r.l * k, y: r.t * k, width: (r.r - r.l) * k, height: (r.b - r.t) * k)
                c.addPath(UIBezierPath(roundedRect: rr.insetBy(dx: -1, dy: -0.5), cornerRadius: 2 * k).cgPath)
                c.fillPath()
            }
        }
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
    var onShape: (([PKStroke]) -> Void)?
    private var start: CGPoint?
    private var end: CGPoint?
    private let preview = CAShapeLayer()

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        preview.fillColor = UIColor.clear.cgColor
        preview.lineCap = .round
        preview.lineJoin = .round
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
        defer { start = nil; end = nil; preview.path = nil }
        guard let a = start, let b = end, hypot(b.x - a.x, b.y - a.y) > 6 else { return }
        let strokes = Shapes.paths(kind, a, b).map { pts -> PKStroke in
            let sp = pts.enumerated().map { i, p in
                PKStrokePoint(location: p, timeOffset: TimeInterval(i) * 0.005, size: CGSize(width: ink.1, height: ink.1),
                              opacity: 1, force: 1, azimuth: 0, altitude: .pi / 2)
            }
            return PKStroke(ink: ink.0, path: PKStrokePath(controlPoints: sp, creationDate: Date()))
        }
        onShape?(strokes)
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        start = nil; end = nil; preview.path = nil
    }

    private func update() {
        guard let a = start, let b = end else { return }
        let p = UIBezierPath()
        for pts in Shapes.paths(kind, a, b) {
            guard let f = pts.first else { continue }
            p.move(to: f)
            for q in pts.dropFirst() { p.addLine(to: q) }
        }
        preview.path = p.cgPath
    }
}

/// نقاط الأشكال (كل شكل قائمة خطوط، وكل خط نقاط متقاربة).
enum Shapes {
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

    static func paths(_ kind: ShapeKind, _ a: CGPoint, _ b: CGPoint) -> [[CGPoint]] {
        let r = CGRect(x: min(a.x, b.x), y: min(a.y, b.y), width: abs(b.x - a.x), height: abs(b.y - a.y))
        switch kind {
        case .line:
            return [dense([a, b], closed: false)]
        case .elbow:
            return [dense([a, CGPoint(x: b.x, y: a.y), b], closed: false)]
        case .curve:
            let c = CGPoint(x: (a.x + b.x) / 2 - (b.y - a.y) * 0.3, y: (a.y + b.y) / 2 + (b.x - a.x) * 0.3)
            let pts = (0...40).map { i -> CGPoint in
                let t = CGFloat(i) / 40
                let x = (1 - t) * (1 - t) * a.x + 2 * (1 - t) * t * c.x + t * t * b.x
                let y = (1 - t) * (1 - t) * a.y + 2 * (1 - t) * t * c.y + t * t * b.y
                return CGPoint(x: x, y: y)
            }
            return [pts]
        case .rect:
            return [dense([CGPoint(x: r.minX, y: r.minY), CGPoint(x: r.maxX, y: r.minY), CGPoint(x: r.maxX, y: r.maxY), CGPoint(x: r.minX, y: r.maxY)], closed: true)]
        case .triangle:
            return [dense([CGPoint(x: r.midX, y: r.minY), CGPoint(x: r.maxX, y: r.maxY), CGPoint(x: r.minX, y: r.maxY)], closed: true)]
        case .diamond:
            return [dense([CGPoint(x: r.midX, y: r.minY), CGPoint(x: r.maxX, y: r.midY), CGPoint(x: r.midX, y: r.maxY), CGPoint(x: r.minX, y: r.midY)], closed: true)]
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

    init(box: TextBox, k: CGFloat) {
        super.init(frame: .zero)
        numberOfLines = 0
        text = box.text
        let size = box.size * k
        font = box.font.isEmpty ? Fonts.ui(size) : (UIFont(name: box.font, size: size) ?? Fonts.ui(size))
        textColor = box.linkDoc != nil ? UIColor(Palette.navy) : UIColor(argb: box.color)
        if box.linkDoc != nil {
            attributedText = NSAttributedString(string: "🔗 " + box.text, attributes: [
                .underlineStyle: NSUnderlineStyle.single.rawValue, .font: font as Any, .foregroundColor: UIColor(Palette.navy)
            ])
        }
        textAlignment = .natural
        let fit = sizeThatFits(CGSize(width: box.w * k, height: .greatestFiniteMagnitude))
        frame = CGRect(x: box.x * k, y: box.y * k, width: box.w * k, height: max(fit.height, size * 1.4))
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

    init(note: StickyNote, k: CGFloat) {
        let collapsed = note.collapsed
        let side: CGFloat = 34
        super.init(frame: collapsed
                   ? CGRect(x: note.x * k, y: note.y * k, width: side, height: side)
                   : CGRect(x: note.x * k, y: note.y * k, width: note.w * k, height: note.h * k))
        backgroundColor = UIColor(argb: note.color)
        layer.cornerRadius = collapsed ? 8 : 4
        layer.shadowColor = UIColor.black.cgColor
        layer.shadowOpacity = 0.18
        layer.shadowRadius = 4
        layer.shadowOffset = CGSize(width: 0, height: 2)
        if collapsed {
            let l = UILabel(frame: bounds)
            l.text = "✎"
            l.textAlignment = .center
            addSubview(l)
        } else {
            if note.style == 1 {
                let bar = UIView(frame: CGRect(x: 0, y: 0, width: 6, height: bounds.height))
                bar.backgroundColor = UIColor.black.withAlphaComponent(0.12)
                addSubview(bar)
            }
            let l = UILabel(frame: bounds.insetBy(dx: bounds.width * 0.07, dy: bounds.width * 0.07))
            l.numberOfLines = 0
            l.font = Fonts.ui(max(9, note.w * k * 0.075))
            l.textColor = UIColor(argb: 0xFF1F2933)
            l.text = note.text.isEmpty ? L("انقر للكتابة", "Tap to write") : note.text
            l.alpha = note.text.isEmpty ? 0.45 : 1
            l.sizeToFit()
            l.frame.size.width = bounds.width * 0.86
            addSubview(l)
        }
        addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(tap)))
        addGestureRecognizer(UIPanGestureRecognizer(target: self, action: #selector(pan(_:))))
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
        layer.borderColor = UIColor(Palette.gold).cgColor
        layer.borderWidth = 2
        backgroundColor = UIColor.white.withAlphaComponent(0.04)
        addGestureRecognizer(UIPanGestureRecognizer(target: self, action: #selector(dragMove(_:))))
        addGestureRecognizer(UIPinchGestureRecognizer(target: self, action: #selector(pinch(_:))))
        let handle = UIView(frame: CGRect(x: bounds.width - 14, y: bounds.height - 14, width: 28, height: 28))
        handle.autoresizingMask = [.flexibleLeftMargin, .flexibleTopMargin]
        handle.backgroundColor = UIColor(Palette.gold)
        handle.layer.cornerRadius = 14
        handle.addGestureRecognizer(UIPanGestureRecognizer(target: self, action: #selector(resize(_:))))
        addSubview(handle)
        let del = UIButton(type: .system)
        del.frame = CGRect(x: -14, y: -14, width: 28, height: 28)
        del.backgroundColor = UIColor(Palette.danger)
        del.tintColor = .white
        del.layer.cornerRadius = 14
        del.setImage(UIImage(named: "ph_trash")?.withRenderingMode(.alwaysTemplate), for: .normal)
        del.imageEdgeInsets = UIEdgeInsets(top: 6, left: 6, bottom: 6, right: 6)
        del.addTarget(self, action: #selector(deleteTap), for: .touchUpInside)
        addSubview(del)
        clipsToBounds = false
    }

    required init?(coder: NSCoder) { fatalError() }

    @objc private func deleteTap() { onDelete?() }

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
