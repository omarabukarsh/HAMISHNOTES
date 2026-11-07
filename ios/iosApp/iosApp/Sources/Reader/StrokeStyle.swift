import SwiftUI
import PencilKit

// أنماط الخط (متصل، متقطع، منقّط)، ورؤوس الأسهم، والزوايا المستديرة، والتعرف على الأشكال
// المرسومة باليد عند إبقاء القلم ثابتًا («ارسم وأبقِ») — نفس Shapes.kt وInkRender.kt في أندرويد.

enum LineStyle: Int, Codable { case solid = 0, dashed = 1, dotted = 2 }
enum ArrowHead: String, Codable, CaseIterable { case none, open, closed }

enum StrokeKit {
    /// يقسم مسارًا إلى قطع (متقطع) أو نقاط (منقّط) بحسب عرض الخط.
    static func split(_ pts: [CGPoint], style: LineStyle, width: CGFloat) -> [[CGPoint]] {
        guard style != .solid, pts.count > 1 else { return [pts] }
        let on: CGFloat = style == .dashed ? width * 3.2 + 6 : 0.1
        let off: CGFloat = style == .dashed ? width * 2.6 + 5 : width * 2.2 + 3
        var out: [[CGPoint]] = []
        var cur: [CGPoint] = [pts[0]]
        var drawing = true
        var left = on
        var p = pts[0]
        var i = 1
        while i < pts.count {
            let q = pts[i]
            let seg = hypot(q.x - p.x, q.y - p.y)
            if seg <= 0.0001 { i += 1; continue }
            if seg < left {
                left -= seg
                if drawing { cur.append(q) }
                p = q
                i += 1
            } else {
                let t = left / seg
                let m = CGPoint(x: p.x + (q.x - p.x) * t, y: p.y + (q.y - p.y) * t)
                if drawing {
                    cur.append(m)
                    if style == .dotted { cur.append(CGPoint(x: m.x + 0.05, y: m.y + 0.05)) }
                    out.append(cur)
                    cur = []
                } else {
                    cur = [m]
                }
                drawing.toggle()
                left = drawing ? on : off
                p = m
            }
        }
        if drawing && cur.count > 1 { out.append(cur) }
        return out
    }

    /// رأس سهم عند tip باتجاه من from إلى tip.
    static func arrow(_ head: ArrowHead, tip: CGPoint, from: CGPoint, width: CGFloat) -> [CGPoint]? {
        guard head != .none else { return nil }
        let ang = atan2(tip.y - from.y, tip.x - from.x)
        let h = max(10, width * 4.5)
        let a1 = ang + .pi * 0.84, a2 = ang - .pi * 0.84
        let p1 = CGPoint(x: tip.x + h * cos(a1), y: tip.y + h * sin(a1))
        let p2 = CGPoint(x: tip.x + h * cos(a2), y: tip.y + h * sin(a2))
        return head == .open ? [p1, tip, p2] : [p1, tip, p2, p1]
    }

    /// مضلع بزوايا مستديرة (نقاط كثيفة).
    static func rounded(_ corners: [CGPoint]) -> [CGPoint] {
        let n = corners.count
        guard n >= 3 else { return corners }
        var minEdge = CGFloat.greatestFiniteMagnitude
        for i in 0..<n { let a = corners[i], b = corners[(i + 1) % n]; minEdge = min(minEdge, hypot(b.x - a.x, b.y - a.y)) }
        let r = minEdge * 0.14
        var out: [CGPoint] = []
        for i in 0..<n {
            let prev = corners[(i - 1 + n) % n], c = corners[i], next = corners[(i + 1) % n]
            func toward(_ a: CGPoint, _ b: CGPoint, _ d: CGFloat) -> CGPoint {
                let l = max(0.001, hypot(b.x - a.x, b.y - a.y))
                return CGPoint(x: a.x + (b.x - a.x) / l * d, y: a.y + (b.y - a.y) / l * d)
            }
            let s = toward(c, prev, r), e = toward(c, next, r)
            for k in 0...8 {
                let t = CGFloat(k) / 8
                out.append(CGPoint(x: (1 - t) * (1 - t) * s.x + 2 * (1 - t) * t * c.x + t * t * e.x,
                                   y: (1 - t) * (1 - t) * s.y + 2 * (1 - t) * t * c.y + t * t * e.y))
            }
        }
        if let f = out.first { out.append(f) }
        return Shapes.dense(out, closed: false)
    }

    /// خطوط PencilKit من مسارات (بعرض ثابت).
    static func strokes(_ paths: [[CGPoint]], ink: PKInk, width: CGFloat, seed: UInt32 = shapeSeed, date: Date = Date()) -> [PKStroke] {
        paths.filter { !$0.isEmpty }.map { pts in
            let sp = pts.enumerated().map { i, p in
                PKStrokePoint(location: p, timeOffset: TimeInterval(i) * 0.005, size: CGSize(width: width, height: width),
                              opacity: 1, force: 1, azimuth: 0, altitude: .pi / 2)
            }
            return PKStroke(ink: ink, path: PKStrokePath(controlPoints: sp, creationDate: date), transform: .identity, mask: nil, randomSeed: seed)
        }
    }
}

/// التعرف على الشكل المرسوم باليد: خط مستقيم، سهم، مثلث، مستطيل، دائرة/بيضاوي (نفس ShapeRecognizer).
enum ShapeRecognizer {
    private static func d(_ a: CGPoint, _ b: CGPoint) -> CGFloat { hypot(a.x - b.x, a.y - b.y) }
    private static func length(_ p: [CGPoint]) -> CGFloat { zip(p, p.dropFirst()).reduce(0) { $0 + d($1.0, $1.1) } }
    private static func perp(_ p: CGPoint, _ a: CGPoint, _ b: CGPoint) -> CGFloat {
        let l = d(a, b)
        if l < 1e-6 { return d(p, a) }
        return abs((b.x - a.x) * (a.y - p.y) - (a.x - p.x) * (b.y - a.y)) / l
    }
    private static func simplify(_ p: [CGPoint], _ eps: CGFloat) -> [CGPoint] {
        guard p.count >= 3 else { return p }
        var idx = 0, maxD: CGFloat = 0
        for i in 1..<(p.count - 1) { let dd = perp(p[i], p.first!, p.last!); if dd > maxD { maxD = dd; idx = i } }
        if maxD <= eps { return [p.first!, p.last!] }
        let l = simplify(Array(p[0...idx]), eps), r = simplify(Array(p[idx...]), eps)
        return l.dropLast() + r
    }
    private static func straight(_ p: [CGPoint]) -> Bool {
        guard p.count >= 2 else { return false }
        let a = p.first!, b = p.last!, l = d(a, b)
        if l < 1e-4 { return false }
        return (p.map { perp($0, a, b) }.max() ?? 0) < 0.06 * l && length(p) < 1.18 * l
    }

    /// pts بنقاط الصفحة، pageW عرض الصفحة. يعيد مسارات الشكل أو nil.
    static func recognize(_ pts: [CGPoint], pageW: CGFloat) -> [[CGPoint]]? {
        guard pts.count >= 6 else { return nil }
        let xs = pts.map { $0.x }, ys = pts.map { $0.y }
        let minX = xs.min()!, maxX = xs.max()!, minY = ys.min()!, maxY = ys.max()!
        let diag = hypot(maxX - minX, maxY - minY)
        if diag < 0.06 * pageW { return nil }
        let total = length(pts)
        let first = pts.first!, last = pts.last!
        // محيط الدائرة ≈ 2.22 × قطر مربعها، والشكل البيضوي أقل: 2.2 كانت ترفض الدوائر المرسومة بعناية
        // (خط يذهب ويعود يُرفض لاحقًا لأنه لا يطابق الشكل البيضوي ولا المضلع)
        let closed = d(first, last) < 0.2 * diag && total > 2.0 * diag
        if !closed {
            if straight(pts) { return [Shapes.dense([first, last], closed: false)] }
            guard let k = pts.indices.max(by: { d(pts[$0], first) < d(pts[$1], first) }), k >= 4, k < pts.count - 2 else { return nil }
            let shaft = Array(pts[0...k]), head = Array(pts[k...])
            let main = d(first, pts[k]), headLen = length(head)
            if straight(shaft) && headLen > 0.08 * main && headLen < 0.9 * main {
                let tip = pts[k]
                let ang = atan2(tip.y - first.y, tip.x - first.x)
                let h = min(0.18 * main, 0.05 * pageW)
                let a1 = ang + .pi * 0.84, a2 = ang - .pi * 0.84
                return [Shapes.dense([first, tip], closed: false),
                        Shapes.dense([CGPoint(x: tip.x + h * cos(a1), y: tip.y + h * sin(a1)), tip, CGPoint(x: tip.x + h * cos(a2), y: tip.y + h * sin(a2))], closed: false)]
            }
            return nil
        }
        var poly = simplify(pts, 0.07 * diag)
        if poly.count >= 2 && d(poly.first!, poly.last!) < 0.2 * diag { poly.removeLast() }
        var changed = true
        while changed && poly.count > 3 {
            changed = false
            for i in poly.indices {
                let a = poly[(i - 1 + poly.count) % poly.count], c = poly[(i + 1) % poly.count]
                if perp(poly[i], a, c) < 0.07 * diag { poly.remove(at: i); changed = true; break }
            }
        }
        if poly.count == 3 { return [Shapes.dense(poly, closed: true)] }
        if poly.count == 4 {
            let right = (0..<4).allSatisfy { i -> Bool in
                let a = poly[(i + 3) % 4], b = poly[i], c = poly[(i + 1) % 4]
                let v1 = CGPoint(x: a.x - b.x, y: a.y - b.y), v2 = CGPoint(x: c.x - b.x, y: c.y - b.y)
                let cosA = (v1.x * v2.x + v1.y * v2.y) / max(1e-6, hypot(v1.x, v1.y) * hypot(v2.x, v2.y))
                return abs(cosA) < 0.35
            }
            if right {
                let aligned = (0..<4).allSatisfy { i -> Bool in
                    let a = poly[i], b = poly[(i + 1) % 4]
                    var ang = atan2(b.y - a.y, b.x - a.x) * 180 / .pi
                    ang = (ang.truncatingRemainder(dividingBy: 90) + 90).truncatingRemainder(dividingBy: 90)
                    return ang < 12 || ang > 78
                }
                let rect = aligned ? [CGPoint(x: minX, y: minY), CGPoint(x: maxX, y: minY), CGPoint(x: maxX, y: maxY), CGPoint(x: minX, y: maxY)] : poly
                return [Shapes.dense(rect, closed: true)]
            }
        }
        let cx = (minX + maxX) / 2, cy = (minY + maxY) / 2
        let rx = max(1e-4, (maxX - minX) / 2), ry = max(1e-4, (maxY - minY) / 2)
        let radii = pts.map { sqrt(pow(($0.x - cx) / rx, 2) + pow(($0.y - cy) / ry, 2)) }
        let mean = radii.reduce(0, +) / CGFloat(radii.count)
        let sd = sqrt(radii.map { ($0 - mean) * ($0 - mean) }.reduce(0, +) / CGFloat(radii.count))
        if sd < 0.13 * mean {
            return [(0...96).map { i in
                let t = CGFloat(i) / 96 * 2 * .pi
                return CGPoint(x: cx + rx * cos(t), y: cy + ry * sin(t))
            }]
        }
        return nil
    }
}

/// عينة نمط الخط.
struct LineStyleSample: View {
    let style: LineStyle
    var color: Color = Palette.textPrimary
    var width: CGFloat = 3
    var body: some View {
        Canvas { ctx, size in
            var p = Path()
            p.move(to: CGPoint(x: 4, y: size.height / 2))
            p.addLine(to: CGPoint(x: size.width - 4, y: size.height / 2))
            let dash: [CGFloat] = style == .dashed ? [width * 3.2 + 4, width * 2.6 + 3] : (style == .dotted ? [0.1, width * 2.2 + 2] : [])
            ctx.stroke(p, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: .round, dash: dash))
        }
        .frame(width: 44, height: 16)
    }
}

/// نمط الخط والسماكة (للأقلام والأشكال).
struct StrokeStyleSection: View {
    @Environment(\.colorScheme) private var themeTick // يعيد الرسم عند تغيير الثيم
    @Binding var style: LineStyle
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(L("نمط الخط", "Line style")).font(Fonts.sans(12)).foregroundColor(Palette.textSecondary)
            HStack(spacing: 6) {
                ForEach([(LineStyle.solid, L("متصل", "Solid")), (.dashed, L("متقطع", "Dashed")), (.dotted, L("منقّط", "Dotted"))], id: \.0) { st, label in
                    let on = style == st
                    Button { style = st } label: {
                        VStack(spacing: 4) {
                            LineStyleSample(style: st, color: on ? .white : Palette.textPrimary)
                            Text(label).font(Fonts.sans(11, .bold)).foregroundColor(on ? .white : Palette.textPrimary)
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 8)
                        .background(RoundedRectangle(cornerRadius: 10).fill(on ? Palette.navy : Palette.background))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- معالجة الخط بعد كتابته

/// تثبيت الخط، حساسية الضغط، حدة الطرف، وخط الهايلايتر المستقيم (نفس PenPrefs في أندرويد).
enum PenProcessing {
    /// الخط بعد المعالجة، أو nil إن لم يتغير شيء. pageW = عرض الصفحة بوحدات الكانفاس.
    static func apply(_ s: PKStroke, tool: Tool, pageW: CGFloat,
                      stabilize: Double, pressure: Double, sharpness: Double, straight: Bool) -> PKStroke? {
        let pts = Array(s.path)
        guard pts.count >= 2 else { return nil }
        let isPen = tool.isPen
        let sharp = CGFloat((tool == .fountain || tool == .brush) ? sharpness : 0)
        let doStab = stabilize > 0.01
        let doPressure = isPen && abs(pressure - 0.5) > 0.01
        let doTaper = isPen && sharp > 0.01 && pts.count >= 4
        let doStraight = tool == .highlighter && straight
        guard doStab || doPressure || doTaper || doStraight else { return nil }

        var out = pts
        if doStraight {
            let a = pts.first!, b = pts.last!
            let w = pts.map { $0.size.width }.sorted()[pts.count / 2]
            let h = pts.map { $0.size.height }.sorted()[pts.count / 2]
            out = (0...12).map { i in
                let f = CGFloat(i) / 12
                return PKStrokePoint(location: CGPoint(x: a.location.x + (b.location.x - a.location.x) * f,
                                                       y: a.location.y + (b.location.y - a.location.y) * f),
                                     timeOffset: a.timeOffset + (b.timeOffset - a.timeOffset) * Double(f),
                                     size: CGSize(width: w, height: h), opacity: a.opacity, force: a.force,
                                     azimuth: a.azimuth, altitude: a.altitude)
            }
        } else if doStab {
            // كل نقطة تقترب من موضع القلم بنسبة (أقل = أنعم)
            let follow = CGFloat(1 - stabilize * 0.85)
            var sx = pts[0].location.x, sy = pts[0].location.y
            out = pts.map { p in
                sx += (p.location.x - sx) * follow
                sy += (p.location.y - sy) * follow
                return point(p, at: CGPoint(x: sx, y: sy), size: p.size)
            }
            // آخر نقطة في موضع القلم الحقيقي حتى لا يقصر الخط
            if let l = pts.last { out[out.count - 1] = l }
        }
        if doPressure {
            let k = CGFloat(pressure * 2)
            let mw = out.reduce(0) { $0 + $1.size.width } / CGFloat(out.count)
            let mh = out.reduce(0) { $0 + $1.size.height } / CGFloat(out.count)
            out = out.map { p in
                let w = max(mw * 0.1, mw + (p.size.width - mw) * k)
                let h = max(mh * 0.1, mh + (p.size.height - mh) * k)
                return point(p, at: p.location, size: CGSize(width: w, height: h))
            }
        }
        if doTaper {
            var total: CGFloat = 0
            for i in 1..<out.count { total += hypot(out[i].location.x - out[i - 1].location.x, out[i].location.y - out[i - 1].location.y) }
            if total > 0 {
                let span = min(total * 0.3, (0.02 + sharp * 0.03) * pageW)
                var d: CGFloat = 0
                for i in out.indices {
                    if i > 0 { d += hypot(out[i].location.x - out[i - 1].location.x, out[i].location.y - out[i - 1].location.y) }
                    let f = min(1, min(d, total - d) / max(0.001, span))
                    let k = (1 - sharp) + sharp * f
                    out[i] = point(out[i], at: out[i].location, size: CGSize(width: out[i].size.width * k, height: out[i].size.height * k))
                }
            }
        }
        let path = PKStrokePath(controlPoints: out, creationDate: s.path.creationDate)
        return PKStroke(ink: s.ink, path: path, transform: s.transform, mask: s.mask, randomSeed: s.randomSeed)
    }

    private static func point(_ p: PKStrokePoint, at l: CGPoint, size: CGSize) -> PKStrokePoint {
        PKStrokePoint(location: l, timeOffset: p.timeOffset, size: size, opacity: p.opacity, force: p.force, azimuth: p.azimuth, altitude: p.altitude)
    }

    /// فلتر الممحاة: ما مسحته الممحاة من خطوط محمية يعود كما كان. nil = لا شيء محمي.
    static func protect(old: PKDrawing, new: PKDrawing, canErase: (PKStroke) -> Bool) -> PKDrawing? {
        let kept = old.strokes.filter { !canErase($0) }
        guard !kept.isEmpty else { return nil }
        let rest = new.strokes.filter { canErase($0) }
        let merged = (kept + rest).enumerated().sorted { a, b in
            a.element.path.creationDate != b.element.path.creationDate
                ? a.element.path.creationDate < b.element.path.creationDate : a.offset < b.offset
        }.map { $0.element }
        return PKDrawing(strokes: merged)
    }
}
