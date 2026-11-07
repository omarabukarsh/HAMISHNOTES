import SwiftUI
import PencilKit

// التحديد الحر (نفس التحديد في أندرويد): حر أو مستطيل، يختار الكتابة والهايلايتر والأشكال
// والصور والنصوص والملاحظات حسب الإعدادات. يُسحب المحدد لنقله، ومقبض الزاوية يكبّره ويصغّره،
// وشريط أسفل الشاشة: تغيير الخط، إلى نص، ملصق، نسخ، الألوان، الصوت، حذف.

/// علامة الخطوط التي رسمتها أداة الأشكال (للتمييز بينها وبين الكتابة في التحديد).
let shapeSeed: UInt32 = 0x5A5E

struct LassoSel: Equatable {
    var pageId: String
    var strokes: [Int]
    var images: [String] = []
    var boxes: [String] = []
    var notes: [String] = []
    var isEmpty: Bool { strokes.isEmpty && images.isEmpty && boxes.isEmpty && notes.isEmpty }
}

extension DocSession {
    /// يختار ما داخل المضلع (بنقاط الصفحة).
    func lassoSelect(pageId: String, poly: [CGPoint]) {
        guard poly.count >= 3 else { lassoSel = nil; return }
        func inside(_ p: CGPoint) -> Bool {
            var c = false
            var j = poly.count - 1
            for i in poly.indices {
                let a = poly[i], b = poly[j]
                if (a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x { c.toggle() }
                j = i
            }
            return c
        }
        let app = AppController.shared
        let d = drawing(for: pageId)
        var picked: [Int] = []
        for (i, st) in d.strokes.enumerated() {
            let isShape = st.randomSeed == shapeSeed
            let isHl = st.ink.inkType == .marker
            let ok = isShape ? app.lassoShapes : (isHl ? app.lassoHighlight : app.lassoInk)
            guard ok else { continue }
            let pts = st.path.interpolatedPoints(by: .distance(4)).map { $0.location.applying(st.transform) }
            guard !pts.isEmpty else { continue }
            if Double(pts.filter(inside).count) >= Double(pts.count) * 0.6 { picked.append(i) }
        }
        let imgs = !app.lassoImages ? [] : data.images.filter { $0.page == pageId && inside(CGPoint(x: $0.x + $0.w / 2, y: $0.y + $0.w / max(0.01, $0.aspect) / 2)) }.map { $0.id }
        let boxes = !app.lassoTexts ? [] : data.boxes.filter { $0.page == pageId && inside(CGPoint(x: $0.x + $0.w / 2, y: $0.y + boxHeight($0) / 2)) }.map { $0.id }
        let notes = !app.lassoStickies ? [] : data.notes.filter { $0.page == pageId && inside(CGPoint(x: $0.x + $0.w / 2, y: $0.y + $0.h / 2)) }.map { $0.id }
        let sel = LassoSel(pageId: pageId, strokes: picked, images: imgs, boxes: boxes, notes: notes)
        lassoSel = sel.isEmpty ? nil : sel
        if sel.isEmpty { AppController.shared.message(L("لم يُحدَّد شيء", "Nothing selected")) }
    }

    func boxHeight(_ b: TextBox) -> CGFloat {
        max(b.size * 1.45, b.size * 1.45 * CGFloat(max(1, b.text.components(separatedBy: "\n").count)))
    }

    /// حدود التحديد بنقاط الصفحة.
    func lassoBounds() -> CGRect? {
        guard let sel = lassoSel else { return nil }
        var r = CGRect.null
        let d = drawing(for: sel.pageId)
        for i in sel.strokes where d.strokes.indices.contains(i) { r = r.union(d.strokes[i].renderBounds) }
        for im in data.images where sel.images.contains(im.id) { r = r.union(CGRect(x: im.x, y: im.y, width: im.w, height: im.w / max(0.01, im.aspect))) }
        for b in data.boxes where sel.boxes.contains(b.id) { r = r.union(CGRect(x: b.x, y: b.y, width: b.w, height: boxHeight(b))) }
        for n in data.notes where sel.notes.contains(n.id) { r = r.union(CGRect(x: n.x, y: n.y, width: n.w, height: n.h)) }
        return r.isNull ? nil : r.insetBy(dx: -5, dy: -5)
    }

    /// يطبّق تغييرًا على الخطوط المحددة وعلى العناصر، خطوةً واحدة في التراجع.
    private func lassoChange(strokes f: (PKStroke) -> PKStroke?, items g: ((inout DocData, LassoSel) -> Void)? = nil, newSel: ((LassoSel, [Int]) -> LassoSel?)? = nil) {
        guard let sel = lassoSel else { return }
        let old = drawing(for: sel.pageId)
        var next: [PKStroke] = []
        var kept: [Int] = []
        for (i, st) in old.strokes.enumerated() {
            if sel.strokes.contains(i) {
                if let t = f(st) { kept.append(next.count); next.append(t) }
            } else {
                next.append(st)
            }
        }
        var nd = old
        nd.strokes = next
        var step: EditStep = .ink(page: sel.pageId, before: old, after: nd)
        drawings[sel.pageId] = nd
        dirtyInk.insert(sel.pageId)
        applyInkHook?(sel.pageId, nd)
        inkVersion += 1
        if let g = g {
            let before = data
            g(&data, sel)
            dataDirty = true
            step = .both(step, .data(before: before, after: data))
        }
        push(step)
        scheduleSave()
        var ns = sel
        ns.strokes = kept
        lassoSel = newSel?(ns, kept) ?? (ns.isEmpty ? nil : ns)
    }

    /// تحريك بمقدار (dx, dy) وتكبير بنسبة k حول نقطة.
    func lassoTransform(dx: CGFloat, dy: CGFloat, k: CGFloat = 1, pivot: CGPoint = .zero) {
        let t = CGAffineTransform(translationX: -pivot.x, y: -pivot.y)
            .concatenating(CGAffineTransform(scaleX: k, y: k))
            .concatenating(CGAffineTransform(translationX: pivot.x + dx, y: pivot.y + dy))
        lassoChange(strokes: { st in
            var s = st
            s.transform = st.transform.concatenating(t)
            return s
        }, items: { d, sel in
            for i in d.images.indices where sel.images.contains(d.images[i].id) {
                let p = CGPoint(x: d.images[i].x, y: d.images[i].y).applying(t)
                d.images[i].x = p.x; d.images[i].y = p.y; d.images[i].w *= k
            }
            for i in d.boxes.indices where sel.boxes.contains(d.boxes[i].id) {
                let p = CGPoint(x: d.boxes[i].x, y: d.boxes[i].y).applying(t)
                d.boxes[i].x = p.x; d.boxes[i].y = p.y; d.boxes[i].w *= k; d.boxes[i].size *= k
            }
            for i in d.notes.indices where sel.notes.contains(d.notes[i].id) {
                let p = CGPoint(x: d.notes[i].x, y: d.notes[i].y).applying(t)
                d.notes[i].x = p.x; d.notes[i].y = p.y
            }
        })
    }

    func lassoRecolor(_ argb: UInt32) {
        let col = UIColor(argb: argb)
        lassoChange(strokes: { st in
            let a: CGFloat = st.ink.inkType == .marker ? 0.6 : 1
            var s = st
            s.ink = PKInk(st.ink.inkType, color: col.withAlphaComponent(a))
            return s
        }, items: { d, sel in
            for i in d.boxes.indices where sel.boxes.contains(d.boxes[i].id) { d.boxes[i].color = argb }
        })
    }

    func lassoDelete() {
        lassoChange(strokes: { _ in nil }, items: { d, sel in
            d.images.removeAll { sel.images.contains($0.id) }
            d.boxes.removeAll { sel.boxes.contains($0.id) }
            d.notes.removeAll { sel.notes.contains($0.id) }
        }, newSel: { _, _ in nil })
        lassoSel = nil
    }

    /// نسخة مزاحة قليلًا من التحديد، وتصبح هي المحددة.
    func lassoDuplicate() {
        guard let sel = lassoSel else { return }
        let off = pageSize(index(of: sel.pageId) ?? 0).width * 0.03
        let shift = CGAffineTransform(translationX: off, y: off)
        let old = drawing(for: sel.pageId)
        var nd = old
        var copies: [Int] = []
        for i in sel.strokes where old.strokes.indices.contains(i) {
            var s = old.strokes[i]
            s.transform = s.transform.concatenating(shift)
            copies.append(nd.strokes.count)
            nd.strokes.append(s)
        }
        let before = data
        var newImgs: [String] = [], newBoxes: [String] = [], newNotes: [String] = []
        for im in data.images where sel.images.contains(im.id) {
            var c = im; c.id = UUID().uuidString; c.x += off; c.y += off
            data.images.append(c); newImgs.append(c.id)
        }
        for b in data.boxes where sel.boxes.contains(b.id) {
            var c = b; c.id = UUID().uuidString; c.x += off; c.y += off
            data.boxes.append(c); newBoxes.append(c.id)
        }
        for n in data.notes where sel.notes.contains(n.id) {
            var c = n; c.id = UUID().uuidString; c.x += off; c.y += off
            data.notes.append(c); newNotes.append(c.id)
        }
        drawings[sel.pageId] = nd
        dirtyInk.insert(sel.pageId)
        dataDirty = true
        applyInkHook?(sel.pageId, nd)
        inkVersion += 1
        push(.both(.ink(page: sel.pageId, before: old, after: nd), .data(before: before, after: data)))
        scheduleSave()
        lassoSel = LassoSel(pageId: sel.pageId, strokes: copies, images: newImgs, boxes: newBoxes, notes: newNotes)
    }

    /// الخطوط المحددة كرسم مستقل.
    func lassoDrawing() -> PKDrawing? {
        guard let sel = lassoSel else { return nil }
        let d = drawing(for: sel.pageId)
        let strokes = sel.strokes.filter { d.strokes.indices.contains($0) }.map { d.strokes[$0] }
        return strokes.isEmpty ? nil : PKDrawing(strokes: strokes)
    }

    /// صورة المحدد (للملصقات).
    func lassoImage() -> UIImage? {
        guard let d = lassoDrawing() else { return nil }
        let r = d.bounds.insetBy(dx: -6, dy: -6)
        return d.image(from: r, scale: 3)
    }

    /// وقت كتابة أول خط محدد.
    func lassoFirstTime() -> Int64? {
        guard let d = lassoDrawing() else { return nil }
        guard let first = d.strokes.map({ $0.path.creationDate }).min() else { return nil }
        return Int64(first.timeIntervalSince1970 * 1000)
    }
}

/// طبقة التحديد الحر فوق الصفحة: ترسم مسار التحديد، وإطار المحدد مع مقبض التكبير، وتنقله بالسحب.
final class LassoView: UIView {
    weak var session: DocSession?
    var pageId = ""
    var k: CGFloat = 1
    var fingerWrites = false
    var rect = false
    private var path: [CGPoint] = []
    private var mode = 0           // 0 تحديد، 1 نقل، 2 تكبير
    private var startPt: CGPoint = .zero
    private var livePt: CGPoint = .zero
    private let pathLayer = CAShapeLayer()
    private let boxLayer = CAShapeLayer()
    private let handle = UIView()

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isOpaque = false
        pathLayer.fillColor = UIColor(Palette.navy).withAlphaComponent(0.06).cgColor
        pathLayer.strokeColor = UIColor(Palette.navy).cgColor
        pathLayer.lineWidth = 1.5
        pathLayer.lineDashPattern = [6, 5]
        layer.addSublayer(pathLayer)
        boxLayer.fillColor = UIColor(Palette.navy).withAlphaComponent(0.05).cgColor
        boxLayer.strokeColor = UIColor(Palette.navy).cgColor
        boxLayer.lineWidth = 1.5
        boxLayer.lineDashPattern = [6, 5]
        layer.addSublayer(boxLayer)
        handle.backgroundColor = .white
        handle.layer.borderColor = UIColor(Palette.navy).cgColor
        handle.layer.borderWidth = 2
        handle.layer.cornerRadius = 9
        handle.isUserInteractionEnabled = false
        handle.isHidden = true
        addSubview(handle)
    }

    required init?(coder: NSCoder) { fatalError() }

    private func accept(_ t: UITouch) -> Bool { fingerWrites || t.type == .pencil }

    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        // لمسات الإصبع تمر للتمرير ما لم يكن الإصبع يكتب، إلا داخل المحدد
        if let t = event?.allTouches?.first, t.type == .direct, !fingerWrites {
            if let b = selBounds() { return b.insetBy(dx: -16, dy: -16).contains(point) }
            return false
        }
        return super.point(inside: point, with: event)
    }

    /// حدود المحدد على الشاشة.
    private func selBounds() -> CGRect? {
        guard let s = session, let sel = s.lassoSel, sel.pageId == pageId, let r = s.lassoBounds() else { return nil }
        return CGRect(x: r.minX * k, y: r.minY * k, width: r.width * k, height: r.height * k)
    }

    func refresh() {
        guard let b = selBounds() else {
            boxLayer.path = nil
            handle.isHidden = true
            return
        }
        var r = b
        if mode == 1 { r = r.offsetBy(dx: livePt.x - startPt.x, dy: livePt.y - startPt.y) }
        if mode == 2 {
            let sc = scaleFactor(b)
            r = CGRect(x: b.minX, y: b.minY, width: b.width * sc, height: b.height * sc)
        }
        boxLayer.path = UIBezierPath(roundedRect: r, cornerRadius: 6).cgPath
        handle.isHidden = false
        handle.frame = CGRect(x: r.maxX - 9, y: r.maxY - 9, width: 18, height: 18)
    }

    private func scaleFactor(_ b: CGRect) -> CGFloat {
        let d0 = hypot(b.width, b.height)
        let d1 = hypot(livePt.x - b.minX, livePt.y - b.minY)
        return max(0.2, min(5, d1 / max(1, d0)))
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t = touches.first else { return }
        let p = t.location(in: self)
        startPt = p
        livePt = p
        if let b = selBounds() {
            if hypot(p.x - b.maxX, p.y - b.maxY) < 28 { mode = 2; return }
            if b.contains(p) { mode = 1; return }
        }
        guard accept(t) else { mode = -1; return }
        mode = 0
        session?.lassoSel = nil
        path = [p]
        refresh()
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t = touches.first, mode >= 0 else { return }
        let p = t.location(in: self)
        livePt = p
        if mode == 0 {
            path.append(p)
            let bp = UIBezierPath()
            if rect {
                bp.append(UIBezierPath(rect: CGRect(x: min(startPt.x, p.x), y: min(startPt.y, p.y), width: abs(p.x - startPt.x), height: abs(p.y - startPt.y))))
            } else if let f = path.first {
                bp.move(to: f)
                for q in path.dropFirst() { bp.addLine(to: q) }
                bp.close()
            }
            pathLayer.path = bp.cgPath
        } else {
            refresh()
        }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        defer { mode = 0; path = []; pathLayer.path = nil; refresh() }
        guard let s = session else { return }
        switch mode {
        case 0:
            var poly: [CGPoint]
            if rect {
                let a = startPt, b = livePt
                poly = [a, CGPoint(x: b.x, y: a.y), b, CGPoint(x: a.x, y: b.y)]
            } else {
                poly = path
            }
            poly = poly.map { CGPoint(x: $0.x / k, y: $0.y / k) }
            if hypot(livePt.x - startPt.x, livePt.y - startPt.y) < 8 { s.lassoSel = nil; return }
            s.lassoSelect(pageId: pageId, poly: poly)
        case 1:
            let dx = (livePt.x - startPt.x) / k, dy = (livePt.y - startPt.y) / k
            if abs(dx) + abs(dy) > 0.5 { s.lassoTransform(dx: dx, dy: dy) }
        case 2:
            if let b = selBounds() {
                let sc = scaleFactor(b)
                s.lassoTransform(dx: 0, dy: 0, k: sc, pivot: CGPoint(x: b.minX / k, y: b.minY / k))
            }
        default: break
        }
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        mode = 0; path = []; pathLayer.path = nil; refresh()
    }
}

// ---------------------------------------------------------------- الشريط والقائمة

/// شريط التحديد أسفل القارئ.
struct LassoBar: View {
    @ObservedObject var session: DocSession
    @EnvironmentObject var app: AppController
    @ObservedObject var pm = Premium.shared
    @Binding var sheet: ReaderSheet?

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 2) {
                Button {
                    if pm.require(.toText) { sheet = .changeFont }
                } label: {
                    Text("Aa " + L("تغيير الخط", "Change font")).font(Fonts.sans(14, .bold)).foregroundColor(.white)
                        .padding(.horizontal, 12).padding(.vertical, 8)
                        .background(RoundedRectangle(cornerRadius: 12).fill(Palette.navy))
                }
                .padding(.trailing, 4)
                barButton(L("إلى نص", "To text")) { if pm.require(.toText) { sheet = .lassoToText } }
                barButton("☺ " + L("ملصق", "Sticker")) {
                    if let img = session.lassoImage() { StickerStore.shared.add(img) }
                }
                barButton(L("نسخ", "Duplicate")) { session.lassoDuplicate() }
                ForEach(Array(app.penPalette.prefix(6)), id: \.self) { col in
                    Button { session.lassoRecolor(col) } label: {
                        Circle().fill(Color(hex: col)).frame(width: 22, height: 22)
                            .overlay(Circle().stroke(Palette.divider, lineWidth: 1))
                            .frame(width: 32, height: 32)
                    }
                }
                if let t = session.lassoFirstTime(),
                   let rec = session.data.recordings.first(where: { t >= $0.start && t <= $0.start + $0.durationMs }) {
                    barButton(L("▶ الصوت", "▶ Audio"), bold: true) {
                        AudioNotes.shared.play(session, rec, at: max(0, TimeInterval(t - rec.start) / 1000 - 2))
                    }
                }
                barButton(L("حذف", "Delete"), color: Color(hex: 0xFFC62828)) { session.lassoDelete() }
                barButton(L("تم", "Done")) { session.lassoSel = nil }
            }
            .padding(.horizontal, 8).padding(.vertical, 4)
        }
        .fixedSize(horizontal: true, vertical: false)
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.white).shadow(color: .black.opacity(0.18), radius: 8, y: 3))
    }

    private func barButton(_ label: String, color: Color = Palette.navy, bold: Bool = false, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(Fonts.sans(14, bold ? .bold : .regular)).foregroundColor(color).padding(.horizontal, 10).padding(.vertical, 10)
        }
    }
}

/// قائمة إعدادات التحديد: النوع وما يدخل فيه.
struct LassoMenuContent: View {
    @EnvironmentObject var app: AppController
    var onOff: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(L("التحديد", "Lasso selection")).font(Fonts.sans(14, .bold)).foregroundColor(Palette.textPrimary)
            Text(L("نوع التحديد", "Lasso type")).font(Fonts.sans(12, .bold)).foregroundColor(Palette.textSecondary)
            HStack(spacing: 8) {
                option(L("حر", "Freehand"), "ph_lasso", !app.lassoRect) { app.lassoRect = false }
                option(L("مستطيل", "Rectangular"), "ph_select_rect", app.lassoRect) { app.lassoRect = true }
            }
            Text(L("يدخل في التحديد", "Included in selection")).font(Fonts.sans(12, .bold)).foregroundColor(Palette.textSecondary).padding(.top, 6)
            Toggle(L("الكتابة اليدوية", "Handwriting"), isOn: $app.lassoInk)
            Toggle(L("الهايلايتر", "Highlighter"), isOn: $app.lassoHighlight)
            Toggle(L("الأشكال والأسهم", "Shapes and arrows"), isOn: $app.lassoShapes)
            Toggle(L("الصور", "Images"), isOn: $app.lassoImages)
            Toggle(L("مربعات النص", "Text boxes"), isOn: $app.lassoTexts)
            Toggle(L("الملاحظات اللاصقة", "Sticky notes"), isOn: $app.lassoStickies)
            Button(action: onOff) {
                Text(L("إيقاف التحديد", "Turn off lasso")).font(Fonts.sans(14, .bold)).foregroundColor(Palette.navy)
            }
            .padding(.top, 4)
        }
        .font(Fonts.sans(14))
        .tint(Palette.navy)
        .padding(16)
        .frame(width: 280)
    }

    private func option(_ label: String, _ icon: String, _ on: Bool, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 4) {
                PhIcon(icon, on ? .white : Palette.textPrimary, 20)
                Text(label).font(Fonts.sans(12, .bold)).foregroundColor(on ? .white : Palette.textPrimary)
            }
            .frame(maxWidth: .infinity).padding(.vertical, 10)
            .background(RoundedRectangle(cornerRadius: 12).fill(on ? Palette.navy : Palette.navySoft))
        }
    }
}
