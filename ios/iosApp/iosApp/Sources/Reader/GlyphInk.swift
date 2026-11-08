import UIKit
import CoreText
import PencilKit

// تجميل الكتابة إلى حبر قلم (نفس GlyphInk.kt وInkFill.kt في أندرويد 1.0.271): شكل كل حرف يؤخذ من الخط نفسه،
// ويُرسم حبرًا بخطوط PencilKit (حدود الحرف وخطوط متقاربة تملأ داخله)، فيُمسح بالممحاة ويُحدَّد ويُلوَّن
// مثل أي كتابة، و«الجزء الملموس» يقطع الحروف.

/// علامة خطوط الحبر المجمَّل (لا تُقرأ كخط يد ولا تتحول مرة أخرى).
let glyphSeed: UInt32 = 0x61F7

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

    /// قطع أفقية تملأ داخل الحروف (قاعدة الالتفاف غير الصفرية كما يرسمها الخط).
    static func scanlines(_ polys: [[CGPoint]], step: CGFloat) -> [(CGFloat, CGFloat, CGFloat)] {
        let all = polys.flatMap { $0 }
        guard let minY = all.map({ $0.y }).min(), let maxY = all.map({ $0.y }).max(), step > 0 else { return [] }
        var out: [(CGFloat, CGFloat, CGFloat)] = []
        var y = minY + step / 2
        while y < maxY {
            var xs: [(CGFloat, Int)] = []
            for poly in polys {
                for i in poly.indices {
                    let a = poly[i], b = poly[(i + 1) % poly.count]
                    if (a.y <= y && b.y > y) || (b.y <= y && a.y > y) {
                        let x = a.x + (y - a.y) / (b.y - a.y) * (b.x - a.x)
                        xs.append((x, b.y > a.y ? 1 : -1))
                    }
                }
            }
            xs.sort { $0.0 < $1.0 }
            var wind = 0
            var start: CGFloat = 0
            for (x, d) in xs {
                let was = wind
                wind += d
                if was == 0 && wind != 0 { start = x }
                else if was != 0 && wind == 0 && x - start > 0.05 { out.append((y, start, x)) }
            }
            y += step
        }
        return out
    }

    private static func stroke(_ pts: [CGPoint], width: CGFloat, color: UIColor, date: Date) -> PKStroke {
        let sp = pts.enumerated().map { i, p in
            PKStrokePoint(location: p, timeOffset: TimeInterval(i) * 0.001, size: CGSize(width: width, height: width),
                          opacity: 1, force: 1, azimuth: 0, altitude: .pi / 2)
        }
        return PKStroke(ink: PKInk(.monoline, color: color), path: PKStrokePath(controlPoints: sp, creationDate: date),
                        transform: .identity, mask: nil, randomSeed: glyphSeed)
    }

    /// سطر نص بخط وحجم على شكل حبر: الحدود والملء. كل الخطوط بوقت إنشاء واحد (مفتاح السطر).
    static func strokes(_ lay: Layout, origin: CGPoint, size: CGFloat, color: UIColor, bold: Bool, date: Date) -> [PKStroke] {
        let polys = contours(lay, origin: origin)
        guard !polys.isEmpty else { return [] }
        let wOut = max(0.35, size * (bold ? 0.075 : 0.04))
        let wIn = max(0.6, size * 0.065)
        var out: [PKStroke] = []
        // داخل الحروف: قطع أفقية متقاربة
        for (y, x0, x1) in scanlines(polys, step: wIn * 0.8) {
            let inset = min(wIn / 2, (x1 - x0) / 2)
            let a = x0 + inset, b = x1 - inset
            out.append(stroke(b > a ? [CGPoint(x: a, y: y), CGPoint(x: b, y: y)] : [CGPoint(x: (x0 + x1) / 2, y: y), CGPoint(x: (x0 + x1) / 2 + 0.01, y: y)],
                              width: wIn, color: color, date: date))
        }
        // الحدود: ناعمة بسماكة الحبر
        for p in polys {
            out.append(stroke(p + [p[0]], width: wOut, color: color, date: date))
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
