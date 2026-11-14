import UIKit

// رسم الحبر: نقل مباشر لـ InkRender.kt في أندرويد، فيظهر كل قلم كما يظهر هناك.
// السياق مكبَّر مسبقًا بحيث وحدته نقطة الصفحة، فالخطوط تُرسم بإحداثياتها المحفوظة كما هي.

enum InkRender {
    static func cg(_ argb: UInt32, alpha: CGFloat = 1) -> CGColor {
        let a = CGFloat((argb >> 24) & 0xFF) / 255
        return UIColor(red: CGFloat((argb >> 16) & 0xFF) / 255, green: CGFloat((argb >> 8) & 0xFF) / 255,
                       blue: CGFloat(argb & 0xFF) / 255, alpha: (a == 0 ? 1 : a) * alpha).cgColor
    }

    /// شفافية القلم (الهايلايتر وقلم الرصاص شفافان).
    static func alpha(_ tool: Tool) -> CGFloat {
        switch tool {
        case .highlighter: return 0.35
        case .pencil: return 0.78
        default: return 1
        }
    }

    /// مسار ناعم عبر النقاط (منحنيات تمر بمنتصفات النقاط)؛ extra نقاط تنبؤية تُضاف في النهاية.
    static func smoothPath(_ pts: [InkPt], extra: [InkPt] = []) -> CGPath {
        let all = extra.isEmpty ? pts : pts + extra
        let path = CGMutablePath()
        guard let f = all.first else { return path }
        path.move(to: f.cg)
        if all.count == 1 {
            path.addLine(to: CGPoint(x: f.x + 0.01, y: f.y))
            return path
        }
        if all.count > 2 {
            for i in 1..<(all.count - 1) {
                let m = CGPoint(x: (all[i].x + all[i + 1].x) / 2, y: (all[i].y + all[i + 1].y) / 2)
                path.addQuadCurve(to: m, control: all[i].cg)
            }
        }
        path.addLine(to: all[all.count - 1].cg)
        return path
    }

    /// مسار بخطوط مستقيمة (للأشكال: زوايا حادة). الشكل المغلق يُغلق فتكون زاوية البداية حادة أيضًا.
    static func polyPath(_ pts: [InkPt], closed: Bool) -> CGPath {
        let path = CGMutablePath()
        guard let f = pts.first else { return path }
        path.move(to: f.cg)
        if pts.count == 1 { path.addLine(to: CGPoint(x: f.x + 0.01, y: f.y)) }
        if closed {
            for p in pts.dropFirst().dropLast() { path.addLine(to: p.cg) }
            path.closeSubpath()
        } else {
            for p in pts.dropFirst() { path.addLine(to: p.cg) }
        }
        return path
    }

    /// شكل حرف مملوء من حدوده.
    static func glyphPath(_ pts: [InkPt]) -> CGPath {
        let path = CGMutablePath()
        for c in InkFill.contours(pts) where c.count >= 2 {
            path.move(to: c[0])
            for p in c.dropFirst() { path.addLine(to: p) }
            path.closeSubpath()
        }
        return path
    }

    /// نمط التقطيع لعرض خط (بنقاط الصفحة).
    static func dash(_ style: LineStyle, width: CGFloat) -> [CGFloat] {
        switch style {
        case .dashed: return [width * 3.2 + 3, width * 2.6 + 2.5]
        case .dotted: return [0.1, width * 2.2 + 1.5]
        case .solid: return []
        }
    }

    /// يرسم خطًا واحدًا في طبقة الكتابة حسب نوع القلم. extra: نقاط تنبؤية للخط الحي (لا تُحفظ).
    static func draw(_ s: InkStroke, in c: CGContext, extra: [InkPt] = []) {
        if s.fill { drawGlyph(s, in: c); return }
        let n = s.pts.count + extra.count
        guard n > 0, s.width > 0 else { return }
        c.saveGState()
        defer { c.restoreGState() }
        c.setLineCap(.round)
        // خط متقطع/منقّط أو شكل: مسار بعرض ثابت
        if s.style != .solid || s.shape {
            let w = s.width * (s.tool == .pencil ? 0.85 : 1)
            c.setStrokeColor(cg(s.color, alpha: alpha(s.tool)))
            c.setLineWidth(w)
            // الأشكال بوصلات حادة (الزوايا المستديرة مبنية في نقاط الشكل نفسه)
            c.setLineJoin(s.shape ? .miter : .round)
            c.setMiterLimit(10)
            let d = dash(s.style, width: w)
            if !d.isEmpty { c.setLineDash(phase: 0, lengths: d) }
            c.addPath(s.shape ? polyPath(s.pts + extra, closed: s.isClosed && extra.isEmpty) : smoothPath(s.pts, extra: extra))
            c.strokePath()
            return
        }
        switch s.tool {
        case .fountain, .brush:
            // السماكة تتبع الضغط نقطةً نقطة؛ الفرشاة أكثر تأثرًا بالضغط من قلم الحبر
            let base: CGFloat = s.tool == .brush ? 0.15 : 0.4
            let gain: CGFloat = s.tool == .brush ? 1.5 : 1
            let col = cg(s.color)
            func pt(_ i: Int) -> InkPt { i < s.pts.count ? s.pts[i] : extra[i - s.pts.count] }
            if n == 1 {
                let a = pt(0)
                let r = s.width * (base + gain * max(0, a.p)) / 2
                c.setFillColor(col)
                c.fillEllipse(in: CGRect(x: a.x - r, y: a.y - r, width: r * 2, height: r * 2))
                return
            }
            c.setStrokeColor(col)
            for j in 1..<n {
                let a = pt(j - 1), b = pt(j)
                c.setLineWidth(max(0.05, s.width * (base + gain * max(0, b.p))))
                c.move(to: a.cg)
                c.addLine(to: b.cg)
                c.strokePath()
            }
        default:
            c.setLineJoin(.round)
            c.setStrokeColor(cg(s.color, alpha: alpha(s.tool)))
            c.setLineWidth(s.width * (s.tool == .pencil ? 0.85 : 1))
            c.addPath(smoothPath(s.pts, extra: extra))
            c.strokePath()
        }
    }

    private static func drawGlyph(_ s: InkStroke, in c: CGContext) {
        let path = glyphPath(s.pts)
        let col = cg(s.color)
        c.saveGState()
        c.addPath(path)
        c.setFillColor(col)
        c.fillPath(using: .winding)
        if s.width > 0 {
            c.addPath(path)
            c.setStrokeColor(col)
            c.setLineWidth(s.width)
            c.setLineJoin(.round)
            c.setLineCap(.round)
            c.strokePath()
        }
        c.restoreGState()
    }

    /// ما يُرسم من هذا الخط في طبقة الخلفية: الهايلايتر، وملء الشكل المغلق.
    static func drawUnder(_ s: InkStroke, in c: CGContext, extra: [InkPt] = []) {
        if s.tool == .highlighter {
            draw(s, in: c, extra: extra)
        } else if s.shapeFill, s.pts.count > 2 {
            c.saveGState()
            c.addPath(polyPath(s.pts, closed: true))
            c.setFillColor(cg(s.color, alpha: s.fillAlpha))
            c.fillPath(using: .winding)
            c.restoreGState()
        }
    }

    /// يرسم مجموعة خطوط بترتيب الطبقات: الخلفية (هايلايتر وملء أشكال) ثم الكتابة.
    /// multiplyUnder: الهايلايتر يُمزج مع ما تحته (فيبقى النص تحته واضحًا) — للتصدير والصور.
    static func drawStack(_ strokes: [InkStroke], in c: CGContext, multiplyUnder: Bool = false) {
        if multiplyUnder {
            c.saveGState()
            c.setBlendMode(.multiply)
        }
        for s in strokes where s.inUnderlay { drawUnder(s, in: c) }
        if multiplyUnder { c.restoreGState() }
        for s in strokes where s.inOverlay { draw(s, in: c) }
    }
}

extension InkDrawing {
    /// صورة الحبر في مستطيل (بنقاط الصفحة)، بألوانه كما هي.
    func lightImage(from rect: CGRect, scale: CGFloat) -> UIImage {
        guard rect.width > 0, rect.height > 0, rect.width.isFinite, rect.height.isFinite else { return UIImage() }
        let f = UIGraphicsImageRendererFormat()
        f.scale = max(0.1, scale)
        f.opaque = false
        f.preferredRange = .standard
        return UIGraphicsImageRenderer(size: rect.size, format: f).image { ctx in
            let c = ctx.cgContext
            c.translateBy(x: -rect.minX, y: -rect.minY)
            InkRender.drawStack(strokes, in: c)
        }
    }

    /// يرسم الحبر في سياق وحدته نقطة الصفحة (التصدير والصور المصغّرة).
    func draw(in c: CGContext, multiplyUnder: Bool = true) {
        InkRender.drawStack(strokes, in: c, multiplyUnder: multiplyUnder)
    }
}

/// قصّ الحروف المملوءة بالممحاة (InkCut.kt): تُطرح دائرة الممحاة من شكل الحرف بعمليات المسارات،
/// فيبقى ما لم تمر عليه الممحاة بحدود ناعمة، والأجزاء التي تنفصل تصير قطعًا مستقلة.
enum InkCut {
    static func install() {
        InkErase.cutGlyph = { s, at, r in cut(s, at, r) }
    }

    static func cut(_ s: InkStroke, _ at: CGPoint, _ radius: CGFloat) -> [InkStroke]? {
        guard InkFill.hit(s.pts, at, radius: radius, outline: s.width) else { return nil }
        let shape = InkRender.glyphPath(s.pts)
        // التعريض حول الحرف يُرسم فوق الحواف: تُكبَّر الدائرة بقدره حتى يظهر القص بحجم الممحاة
        let r = radius + s.width / 2
        let hole = CGPath(ellipseIn: CGRect(x: at.x - r, y: at.y - r, width: r * 2, height: r * 2), transform: nil)
        let result = shape.subtracting(hole, using: .winding)
        if result.isEmpty { return [] }
        let line = InkFill.lineOf(s)
        var out: [InkStroke] = []
        for part in result.componentsSeparated(using: .winding) {
            let cs = GlyphInk.flatten(part).filter { $0.count >= 3 && abs(InkFill.area($0)) > 1e-4 }
            guard !cs.isEmpty else { continue }
            var piece = s
            piece.pts = InkFill.encode(InkFill.orient(cs))
            // خط القاعدة والحجم يبقيان كما كانا، بنسب جديدة لحدود هذا الجزء
            if let l = line {
                let (lf, ef) = InkFill.fractions(piece.pts, base: l.base, em: l.em)
                piece.lineF = lf
                piece.emF = ef
            }
            out.append(piece)
        }
        return out
    }
}
