import UIKit
import CoreText

// تجميل الكتابة إلى حبر قلم (نفس GlyphInk.kt وInkFill.kt في أندرويد): شكل كل حرف يؤخذ من الخط نفسه،
// ويُحفظ حرفًا مملوءًا بحدوده (خط fill)، فيبقى ناعمًا بأي تكبير، ويُمسح بالممحاة ويُحدَّد ويُلوَّن
// مثل أي كتابة، و«الجزء الملموس» يقص الحرف.

enum GlyphInk {
    struct Layout {
        let line: CTLine
        let width: CGFloat
        let ascent: CGFloat
        let descent: CGFloat
    }

    static func layout(_ text: String, font: UIFont) -> Layout {
        let attr = NSAttributedString(string: text, attributes: [.font: font])
        let line = CTLineCreateWithAttributedString(attr)
        var asc: CGFloat = 0, desc: CGFloat = 0, lead: CGFloat = 0
        let w = CGFloat(CTLineGetTypographicBounds(line, &asc, &desc, &lead))
        return Layout(line: line, width: w, ascent: asc, descent: desc)
    }

    /// حدود الحروف كمضلعات (بنقاط الصفحة)؛ origin = بداية السطر على خط القاعدة.
    static func contours(_ lay: Layout, origin: CGPoint) -> [[CGPoint]] {
        var out: [[CGPoint]] = []
        guard let runs = CTLineGetGlyphRuns(lay.line) as? [CTRun] else { return [] }
        for run in runs {
            let attrs = CTRunGetAttributes(run) as NSDictionary
            guard let fontRef = attrs[kCTFontAttributeName as String] else { continue }
            let font = fontRef as! CTFont
            let n = CTRunGetGlyphCount(run)
            guard n > 0 else { continue }
            var glyphs = [CGGlyph](repeating: 0, count: n)
            var pos = [CGPoint](repeating: .zero, count: n)
            CTRunGetGlyphs(run, CFRange(location: 0, length: n), &glyphs)
            CTRunGetPositions(run, CFRange(location: 0, length: n), &pos)
            for i in 0..<n {
                var t = CGAffineTransform(translationX: origin.x + pos[i].x, y: origin.y - pos[i].y).scaledBy(x: 1, y: -1)
                guard let path = CTFontCreatePathForGlyph(font, glyphs[i], &t) else { continue }
                out += flatten(path)
            }
        }
        return out.filter { $0.count >= 3 }
    }

    /// يحوّل المسار إلى مضلعات (المنحنيات تُقسَّم إلى قطع صغيرة).
    static func flatten(_ path: CGPath) -> [[CGPoint]] {
        let f = Flattener()
        path.applyWithBlock { e in f.add(e.pointee) }
        f.finish()
        return f.out
    }

    private final class Flattener {
        var out: [[CGPoint]] = []
        var cur: [CGPoint] = []
        var last = CGPoint.zero

        func add(_ el: CGPathElement) {
            let p = el.points
            switch el.type {
            case .moveToPoint:
                finish()
                cur = [p[0]]
                last = p[0]
            case .addLineToPoint:
                cur.append(p[0])
                last = p[0]
            case .addQuadCurveToPoint:
                quad(last, p[0], p[1])
                last = p[1]
            case .addCurveToPoint:
                for k in 1...8 { cur.append(Shapes.cubic(last, p[0], p[1], p[2], CGFloat(k) / 8)) }
                last = p[2]
            case .closeSubpath:
                finish()
            @unknown default:
                break
            }
        }

        private func quad(_ a: CGPoint, _ c: CGPoint, _ e: CGPoint) {
            for k in 1...6 {
                let t = CGFloat(k) / 6
                let u = 1 - t
                let w0 = u * u, w1 = 2 * u * t, w2 = t * t
                let x = w0 * a.x + w1 * c.x + w2 * e.x
                let y = w0 * a.y + w1 * c.y + w2 * e.y
                cur.append(CGPoint(x: x, y: y))
            }
        }

        func finish() {
            if cur.count > 2 { out.append(cur) }
            cur = []
        }
    }

    /// سطر نص بخط وحجم على شكل حروف مملوءة. كل حرف (أو حروف متصلة) خط مستقل يحمل كلمته،
    /// وكل خطوط السطر بوقت واحد t (مفتاح السطر). origin = بداية السطر على خط القاعدة.
    static func strokes(_ text: String, _ lay: Layout, origin: CGPoint, size: CGFloat, color: UInt32, bold: Bool, t: Int64) -> [InkStroke] {
        let polys = contours(lay, origin: origin)
        guard !polys.isEmpty else { return [] }
        let ws = words(text, lay, origin: origin)
        var out: [InkStroke] = []
        for g in InkFill.group(polys) {
            let cs = g.map { polys[$0] }
            var s = InkStroke(pts: InkFill.encode(cs), color: color | 0xFF000000, width: bold ? size * 0.035 : 0, tool: .ballpoint, t: t)
            s.fill = true
            let (lf, ef) = InkFill.fractions(s.pts, base: origin.y, em: size)
            s.lineF = lf
            s.emF = ef
            let bb = s.pointBounds
            if !bb.isNull, !ws.isEmpty {
                let cx = bb.midX
                var best = 0
                var bd = CGFloat.greatestFiniteMagnitude
                for (i, w) in ws.enumerated() {
                    let d = max(0, max(w.1.minX - cx, cx - w.1.maxX))
                    if d < bd { bd = d; best = i }
                }
                s.word = ws[best].0
                s.wid = t &* 1024 &+ Int64(best)
            }
            out.append(s)
        }
        return out
    }

    /// كلمات السطر بمواضعها (بنقاط الصفحة) للبحث والتقاط النص والملخصات.
    static func words(_ text: String, _ lay: Layout, origin: CGPoint) -> [(String, CGRect)] {
        var out: [(String, CGRect)] = []
        let ns = text as NSString
        let re = try? NSRegularExpression(pattern: "\\S+")
        for m in re?.matches(in: text, range: NSRange(location: 0, length: ns.length)) ?? [] {
            let r = m.range
            let a = CTLineGetOffsetForStringIndex(lay.line, r.location, nil)
            let b = CTLineGetOffsetForStringIndex(lay.line, r.location + r.length, nil)
            let x0 = origin.x + min(a, b), x1 = origin.x + max(a, b)
            out.append((ns.substring(with: r), CGRect(x: x0, y: origin.y - lay.ascent, width: max(1, x1 - x0), height: lay.ascent + lay.descent)))
        }
        return out
    }
}
