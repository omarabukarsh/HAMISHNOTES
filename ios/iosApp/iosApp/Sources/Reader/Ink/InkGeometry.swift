import Foundation
#if canImport(CoreGraphics)
import CoreGraphics
#endif

// هندسة الحبر: الحروف المملوءة (InkFill.kt)، الممحاة بنوعيها (eraseStrokes وeraseStrokesArea في أندرويد)،
// التحديد، ومعالجة القلم (PenPrefs.kt). بلا UIKit: يُختبر على أي حاسوب.

/// حبر مملوء (حرف من خط): نقاط الحبر حدود شكل الحرف، وقد يكون للحرف أكثر من حد (حلقة o، نقطة i).
/// نقطة ضغطها سالب = بداية حد جديد. يُملأ بقاعدة nonzero فتبقى الفراغات داخل الحروف فارغة.
enum InkFill {
    static let start: CGFloat = -1

    /// يقسم نقاط الحبر المملوء إلى حدوده (كل حد مضلّع مغلق).
    static func contours(_ pts: [InkPt]) -> [[CGPoint]] {
        var out: [[CGPoint]] = []
        var cur: [CGPoint] = []
        for p in pts {
            if p.p < 0 && !cur.isEmpty {
                out.append(cur)
                cur = []
            }
            cur.append(p.cg)
        }
        if !cur.isEmpty { out.append(cur) }
        return out
    }

    /// نقاط الحبر من حدود، مع علامة بداية كل حد.
    static func encode(_ contours: [[CGPoint]]) -> [InkPt] {
        var out: [InkPt] = []
        for c in contours {
            for (i, p) in c.enumerated() { out.append(InkPt(p.x, p.y, i == 0 ? start : 1)) }
        }
        return out
    }

    /// عدد الالتفاف حول النقطة (غير صفر = داخل الشكل).
    static func winding(_ pt: CGPoint, _ contours: [[CGPoint]]) -> Int {
        var wn = 0
        for c in contours {
            let n = c.count
            if n < 3 { continue }
            for i in 0..<n {
                let a = c[i], b = c[(i + 1) % n]
                let side = (b.x - a.x) * (pt.y - a.y) - (pt.x - a.x) * (b.y - a.y)
                if a.y <= pt.y {
                    if b.y > pt.y && side > 0 { wn += 1 }
                } else if b.y <= pt.y && side < 0 {
                    wn -= 1
                }
            }
        }
        return wn
    }

    static func segDist(_ p: CGPoint, _ a: CGPoint, _ b: CGPoint) -> CGFloat {
        let dx = b.x - a.x, dy = b.y - a.y
        let l2 = dx * dx + dy * dy
        let t = l2 <= 0 ? 0 : min(1, max(0, ((p.x - a.x) * dx + (p.y - a.y) * dy) / l2))
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    /// دائرة (نصف قطرها radius) تلمس الحرف: داخله أو قريبة من حدوده.
    static func hit(_ pts: [InkPt], _ at: CGPoint, radius: CGFloat, outline: CGFloat = 0) -> Bool {
        guard !pts.isEmpty else { return false }
        let r = radius + outline / 2
        var minX = CGFloat.greatestFiniteMagnitude, minY = minX, maxX = -minX, maxY = -minX
        for p in pts {
            if p.x < minX { minX = p.x }
            if p.x > maxX { maxX = p.x }
            if p.y < minY { minY = p.y }
            if p.y > maxY { maxY = p.y }
        }
        if at.x < minX - r || at.x > maxX + r || at.y < minY - r || at.y > maxY + r { return false }
        let cs = contours(pts)
        if winding(at, cs) != 0 { return true }
        for c in cs {
            for i in c.indices where segDist(at, c[i], c[(i + 1) % c.count]) < r { return true }
        }
        return false
    }

    /// المساحة الموجّهة لمضلع.
    static func area(_ c: [CGPoint]) -> CGFloat {
        var s: CGFloat = 0
        for i in c.indices {
            let j = (i + 1) % c.count
            s += c[i].x * c[j].y - c[j].x * c[i].y
        }
        return s / 2
    }

    private static func inside(_ p: CGPoint, _ c: [CGPoint]) -> Bool {
        var inside = false
        var j = c.count - 1
        for i in c.indices {
            let a = c[i], b = c[j]
            if (a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x { inside.toggle() }
            j = i
        }
        return inside
    }

    private static func box(_ c: [CGPoint]) -> CGRect {
        c.reduce(CGRect.null) { $0.union(CGRect(x: $1.x, y: $1.y, width: 0, height: 0)) }
    }

    /// يوجّه الحدود حسب تداخلها (الشكل باتجاه والفراغ داخله بعكسه) فتبقى الفراغات فارغة بقاعدة nonzero.
    static func orient(_ cs: [[CGPoint]]) -> [[CGPoint]] {
        let boxes = cs.map(box)
        return cs.enumerated().map { i, c in
            guard let f = c.first else { return c }
            var depth = 0
            for j in cs.indices where j != i && boxes[i].intersects(boxes[j].insetBy(dx: -0.001, dy: -0.001)) && inside(f, cs[j]) { depth += 1 }
            return (area(c) > 0) == (depth % 2 == 0) ? c : Array(c.reversed())
        }
    }

    /// يجمع الحدود في قطع: المتداخلة (حلقة داخل حرف) قطعة واحدة، والمنفصلة قطع مستقلة.
    static func group(_ cs: [[CGPoint]]) -> [[Int]] {
        let n = cs.count
        var parent = Array(0..<n)
        func find(_ i: Int) -> Int {
            var x = i
            while parent[x] != x { parent[x] = parent[parent[x]]; x = parent[x] }
            return x
        }
        let boxes = cs.map(box)
        func anyInside(_ a: Int, _ b: Int) -> Bool {
            let step = max(1, cs[a].count / 24)
            var k = 0
            while k < cs[a].count {
                if inside(cs[a][k], cs[b]) { return true }
                k += step
            }
            return false
        }
        for a in 0..<n {
            for b in (a + 1)..<max(a + 1, n) {
                if !boxes[a].intersects(boxes[b]) || find(a) == find(b) { continue }
                if anyInside(a, b) || anyInside(b, a) { parent[find(a)] = find(b) }
            }
        }
        var groups: [Int: [Int]] = [:]
        for i in 0..<n { groups[find(i), default: []].append(i) }
        return groups.values.map { $0.sorted() }.sorted { boxes[$0[0]].minX < boxes[$1[0]].minX }
    }

    /// خط القاعدة وحجم الخط المحفوظان في الحرف، أو nil.
    static func lineOf(_ s: InkStroke) -> (base: CGFloat, em: CGFloat)? {
        let bb = s.pointBounds
        guard s.lineF >= 0, s.emF > 0, !bb.isNull, bb.height > 0 else { return nil }
        return (bb.minY + s.lineF * bb.height, s.emF * bb.height)
    }

    /// نسبتا خط القاعدة والحجم لحدود هذه النقاط (للحفظ مع الحرف).
    static func fractions(_ pts: [InkPt], base: CGFloat, em: CGFloat) -> (CGFloat, CGFloat) {
        let bb = InkStroke(pts: pts).pointBounds
        let h = max(1e-6, bb.isNull ? 1e-6 : bb.height)
        return ((base - (bb.isNull ? 0 : bb.minY)) / h, em / h)
    }
}

/// الممحاة والتحديد.
enum InkErase {
    /// قصّ الحروف المملوءة في «الجزء الملموس» (يحتاج عمليات المسارات في CoreGraphics، فيُضبط من التطبيق).
    /// يعيد nil إن لم تلمس الممحاة الحرف، وقائمة فارغة إن مُسح كله.
    static var cutGlyph: ((InkStroke, CGPoint, CGFloat) -> [InkStroke]?)?

    /// هل تلمس دائرة الممحاة هذا الخط؟ (الخط بسماكته، وداخل الشكل المملوء، وداخل الحرف المملوء)
    static func hits(_ s: InkStroke, at: CGPoint, radius: CGFloat) -> Bool {
        if s.fill { return InkFill.hit(s.pts, at, radius: radius, outline: s.width) }
        let r = radius + s.maxDrawnWidth / 2
        let b = s.pointBounds
        if b.isNull { return false }
        if at.x < b.minX - r || at.x > b.maxX + r || at.y < b.minY - r || at.y > b.maxY + r { return false }
        if s.pts.count == 1 { return hypot(s.pts[0].x - at.x, s.pts[0].y - at.y) < r }
        for i in 1..<s.pts.count where InkFill.segDist(at, s.pts[i - 1].cg, s.pts[i].cg) < r { return true }
        // شكل مملوء: لمس داخله يمسحه أيضًا
        return s.shapeFill && InkFill.winding(at, [s.pts.map { $0.cg }]) != 0
    }

    /// «الخط كاملًا»: يمسح كل خط تلمسه الممحاة. nil = لم يتغير شيء.
    static func whole(_ list: [InkStroke], at: CGPoint, radius: CGFloat, canErase: (InkStroke) -> Bool = { _ in true }) -> [InkStroke]? {
        let next = list.filter { !(canErase($0) && hits($0, at: at, radius: radius)) }
        return next.count == list.count ? nil : next
    }

    /// «الجزء الملموس»: يُقسم الخط عند دائرة الممحاة ويبقى الباقي. nil = لم يتغير شيء.
    static func area(_ list: [InkStroke], at: CGPoint, radius: CGFloat, canErase: (InkStroke) -> Bool = { _ in true }) -> [InkStroke]? {
        func inside(_ p: InkPt) -> Bool { hypot(p.x - at.x, p.y - at.y) < radius }
        var changed = false
        var out: [InkStroke] = []
        out.reserveCapacity(list.count + 2)
        for s in list {
            if !canErase(s) { out.append(s); continue }
            // حرف مملوء (تجميل الخط): يُقص منه ما مرت عليه الممحاة فقط
            if s.fill {
                if let cut = cutGlyph {
                    if let parts = cut(s, at, radius) { changed = true; out.append(contentsOf: parts) } else { out.append(s) }
                } else if InkFill.hit(s.pts, at, radius: radius, outline: s.width) {
                    changed = true
                } else {
                    out.append(s)
                }
                continue
            }
            let b = s.pointBounds
            if b.isNull { out.append(s); continue }
            // رفض سريع بالمستطيل المحيط
            if at.x < b.minX - radius || at.x > b.maxX + radius || at.y < b.minY - radius || at.y > b.maxY + radius {
                out.append(s); continue
            }
            // نقاط وسيطة حتى لا تعبر الممحاة بين نقطتين متباعدتين دون أن تقطع الخط
            let step = max(0.05, radius * 0.4)
            var dense: [InkPt] = []
            dense.reserveCapacity(s.pts.count * 2)
            for i in s.pts.indices {
                let q = s.pts[i]
                if i > 0 {
                    let a = s.pts[i - 1]
                    let n = min(200, Int(hypot(q.x - a.x, q.y - a.y) / step))
                    if n > 1 {
                        for k in 1..<n {
                            let f = CGFloat(k) / CGFloat(n)
                            dense.append(InkPt(a.x + (q.x - a.x) * f, a.y + (q.y - a.y) * f, a.p + (q.p - a.p) * f))
                        }
                    }
                }
                dense.append(q)
            }
            if !dense.contains(where: inside) { out.append(s); continue }
            changed = true
            var cur: [InkPt] = []
            func flush() {
                // الشكل المقطوع لم يعد مغلقًا: بلا ملء
                if cur.count >= 2 { var piece = s; piece.pts = cur; piece.shapeFill = false; out.append(piece) }
                cur = []
            }
            for p in dense {
                if inside(p) { flush() } else { cur.append(p) }
            }
            flush()
        }
        return changed ? out : nil
    }

    /// نقاط على الخط كل step تقريبًا (للتحديد: الخطوط المستقيمة نقاطها قليلة).
    static func samples(_ s: InkStroke, step: CGFloat) -> [CGPoint] {
        guard let f = s.pts.first else { return [] }
        var out: [CGPoint] = [f.cg]
        for i in 1..<max(1, s.pts.count) {
            let a = s.pts[i - 1], b = s.pts[i]
            // في الحروف المملوءة: لا وصلة بين حد وآخر
            if s.fill && b.p < 0 { out.append(b.cg); continue }
            let n = min(400, max(1, Int(hypot(b.x - a.x, b.y - a.y) / max(0.1, step))))
            for k in 1...n {
                let t = CGFloat(k) / CGFloat(n)
                out.append(CGPoint(x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t))
            }
        }
        return out
    }

    static func inside(_ p: CGPoint, polygon poly: [CGPoint]) -> Bool {
        var c = false
        var j = poly.count - 1
        for i in poly.indices {
            let a = poly[i], b = poly[j]
            if (a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x { c.toggle() }
            j = i
        }
        return c
    }

    /// الخط داخل مضلع التحديد إن وقع فيه 60% من نقاطه على الأقل.
    static func lassoed(_ s: InkStroke, polygon poly: [CGPoint]) -> Bool {
        let pts = samples(s, step: 4)
        guard !pts.isEmpty else { return false }
        var n = 0
        for p in pts where inside(p, polygon: poly) { n += 1 }
        return Double(n) >= Double(pts.count) * 0.6
    }

    /// أعلى خط تحت نقطة (نقرة التحديد): داخل الشكل المملوء أو الحرف، أو قريب من الخط.
    static func topmost(_ list: [InkStroke], at: CGPoint, tolerance: CGFloat, where ok: (InkStroke) -> Bool = { _ in true }) -> Int? {
        // ما فوق يُختار أولًا: الكتابة فوق الهايلايتر وملء الأشكال
        for i in list.indices.reversed() where ok(list[i]) && list[i].inOverlay && !list[i].shapeFill && hits(list[i], at: at, radius: tolerance) { return i }
        for i in list.indices.reversed() where ok(list[i]) && hits(list[i], at: at, radius: tolerance) { return i }
        return nil
    }
}

/// معالجة نقاط القلم (نفس PenPrefs في أندرويد).
enum InkPen {
    /// الضغط بعد تطبيق حساسية الضغط (sensitivity من 0 إلى 1، و0.5 = بلا تغيير).
    static func applyPressure(_ p: CGFloat, sensitivity: Double) -> CGFloat {
        let k = CGFloat(sensitivity * 2)
        return min(1, max(0.05, 0.55 + (p - 0.55) * k))
    }

    /// قوة قلم Apple Pencil (force ÷ أقصاها) إلى ضغط 0.2…0.9 كما يعاير أندرويد أقلامه.
    static func pencilPressure(force: CGFloat, maxForce: CGFloat) -> CGFloat {
        guard maxForce > 0, force > 0 else { return 0.55 }
        // القوة 1 = لمسة عادية؛ ما فوق 2.2 كتابة قوية جدًا
        let n = min(1, max(0, force / min(maxForce, 2.2)))
        return 0.2 + 0.7 * n
    }

    /// تدبيب طرفي الخط حسب حدة الطرف (لقلم الحبر والفرشاة). pageW = عرض الصفحة بالنقاط.
    static func taper(_ pts: [InkPt], tool: Tool, sharpness: CGFloat, pageW: CGFloat) -> [InkPt] {
        guard sharpness > 0.01, pts.count >= 4, tool == .fountain || tool == .brush else { return pts }
        var total: CGFloat = 0
        for i in 1..<pts.count { total += hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y) }
        guard total > 0 else { return pts }
        let span = min(total * 0.3, (0.02 + sharpness * 0.03) * pageW)
        var out = pts
        var d: CGFloat = 0
        for i in pts.indices {
            if i > 0 { d += hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y) }
            let f = min(1, min(d, total - d) / max(0.0001, span))
            out[i].p = pts[i].p * ((1 - sharpness) + sharpness * f)
        }
        return out
    }

    /// خط الهايلايتر المستقيم: من أول نقطة إلى آخر نقطة.
    static func straighten(_ pts: [InkPt]) -> [InkPt] {
        guard pts.count >= 2, let a = pts.first, let b = pts.last else { return pts }
        return (0...12).map { i in
            let f = CGFloat(i) / 12
            return InkPt(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f, 0.5)
        }
    }

    /// عرض الخط حسب السرعة: للإصبع (لا ضغط له).
    struct SpeedWidth {
        private var last: CGPoint = .zero
        private var lastT: TimeInterval = -1
        private var v: CGFloat = 0.6

        /// at بنقاط الصفحة، pageW عرض الصفحة، time بالثواني.
        mutating func next(_ at: CGPoint, time: TimeInterval, pageW: CGFloat) -> CGFloat {
            if lastT < 0 || pageW <= 0 {
                last = at
                lastT = time
                return v
            }
            let dt = max(0.001, time - lastT)
            // عرض الصفحة في الثانية
            let speed = hypot(at.x - last.x, at.y - last.y) / pageW / CGFloat(dt)
            last = at
            lastT = time
            let target = min(0.8, max(0.38, 0.78 - 0.22 * speed))
            v += (target - v) * 0.25
            return v
        }
    }
}
