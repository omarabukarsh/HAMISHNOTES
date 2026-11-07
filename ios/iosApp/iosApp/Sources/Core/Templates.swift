import UIKit
import PDFKit
import SwiftUI

/// اختيار الورقة: القالب، لون الورقة، المقاس، والاتجاه.
struct PaperChoice: Codable, Equatable {
    var kind: String
    var color: UInt32
    var size: String
    var landscape: Bool

    /// مقاس الصفحة بالنقاط.
    var points: CGSize {
        let s = Templates.sizeOf(size)
        return landscape ? CGSize(width: s.h, height: s.w) : CGSize(width: s.w, height: s.h)
    }
}

/// قوالب الصفحات بأقسامها، مع لون الورقة والمقاس.
enum Templates {
    struct Kind: Identifiable { let id: String; let label: () -> String }
    struct Category { let title: () -> String; let kinds: [Kind] }
    struct Size: Identifiable { let id: String; let label: String; let w: CGFloat; let h: CGFloat }

    static let categories: [Category] = [
        Category(title: { L("الأساسية", "Essentials") }, kinds: [
            Kind(id: "plain") { L("فارغة", "Blank") },
            Kind(id: "dots") { L("منقّطة", "Dotted") },
            Kind(id: "lined_n") { L("مسطّرة ضيقة", "Ruled narrow") },
            Kind(id: "lined") { L("مسطّرة عريضة", "Ruled wide") },
            Kind(id: "grid") { L("مربعات", "Squared") },
            Kind(id: "grid_s") { L("مربعات صغيرة", "Small squares") }
        ]),
        Category(title: { L("أوراق الكتابة", "Writing papers") }, kinds: [
            Kind(id: "cornell") { L("كورنيل", "Cornell") },
            Kind(id: "legal") { L("بهامش", "Legal") },
            Kind(id: "header") { L("بعنوان", "Single column") },
            Kind(id: "columns2") { L("عمودان", "Two columns") },
            Kind(id: "columns3") { L("ثلاثة أعمدة", "Three columns") }
        ]),
        Category(title: { L("الدراسة والتخطيط", "Study & planner") }, kinds: [
            Kind(id: "table") { L("جدول", "Table") },
            Kind(id: "qa") { L("سؤال وجواب", "Q & A") },
            Kind(id: "todo") { L("مهام", "To-dos") },
            Kind(id: "weekly") { L("أسبوعي", "Weekly") },
            Kind(id: "monthly") { L("شهري", "Monthly") }
        ]),
        Category(title: { L("موسيقى", "Music") }, kinds: [
            Kind(id: "music") { L("نوتة", "Music paper") }
        ])
    ]

    static var sizes: [Size] { [
        Size(id: "a4", label: "A4", w: 595, h: 842),
        Size(id: "a5", label: "A5", w: 420, h: 595),
        Size(id: "b5", label: "B5", w: 499, h: 709),
        Size(id: "letter", label: "Letter", w: 612, h: 792),
        Size(id: "legal", label: "Legal", w: 612, h: 1008),
        Size(id: "tabloid", label: "Tabloid", w: 792, h: 1224),
        Size(id: "square", label: L("مربعة", "Square"), w: 700, h: 700)
    ] }

    static func sizeOf(_ id: String) -> Size { sizes.first { $0.id == id } ?? sizes[0] }

    static func labelOf(_ kind: String) -> String {
        categories.flatMap { $0.kinds }.first { $0.id == kind }?.label() ?? L("فارغة", "Blank")
    }

    /// ألوان الورق الجاهزة.
    static var paperColors: [(UInt32, String)] { [
        (0xFFFFFFFF, L("أبيض", "White")),
        (0xFFFFF8E1, L("كريمي", "Cream")),
        (0xFFFFF59D, L("أصفر", "Yellow")),
        (0xFFE3F2FD, L("أزرق فاتح", "Light blue")),
        (0xFFE8F5E9, L("أخضر فاتح", "Light green")),
        (0xFFFCE4EC, L("وردي", "Pink")),
        (0xFFECEFF1, L("رمادي", "Gray")),
        (0xFF1E232B, L("داكن", "Dark"))
    ] }

    // ---------- القالب الافتراضي ----------

    static func defaultChoice() -> PaperChoice {
        if let d = UserDefaults.standard.data(forKey: "default_paper"),
           let c = try? JSONDecoder().decode(PaperChoice.self, from: d) { return c }
        return PaperChoice(kind: "lined", color: 0xFFFFF8E1, size: "a4", landscape: false)
    }

    static func saveDefault(_ c: PaperChoice) {
        if let d = try? JSONEncoder().encode(c) { UserDefaults.standard.set(d, forKey: "default_paper") }
    }

    static func src(_ kind: String, _ bg: UInt32) -> String { "blank:\(kind):" + String(format: "%08X", bg) }

    static func parse(_ src: String) -> (String, UInt32) {
        let parts = src.replacingOccurrences(of: "blank:", with: "").split(separator: ":").map(String.init)
        let kind = parts.first ?? "plain"
        let bg = parts.count > 1 ? UInt32(parts[1], radix: 16) ?? 0xFFFFFFFF : 0xFFFFFFFF
        return (kind, bg)
    }

    /// مواضع أسطر الكتابة في القالب (لوضع النص المتحول على السطر).
    static func lineYs(kind: String, w: CGFloat, h: CGFloat) -> [CGFloat] {
        let unit = min(w, h * 0.707)
        let step = unit / 24
        func seq(_ from: CGFloat, _ to: CGFloat, _ st: CGFloat) -> [CGFloat] {
            var out: [CGFloat] = []
            var y = from
            while y < to { out.append(y); y += st }
            return out
        }
        switch kind {
        case "lined": return seq(step * 2.5, h - step, step)
        case "lined_n": return seq(step * 2.5, h - step, step * 0.75)
        case "grid": return seq(step, h, step)
        case "grid_s": return seq(step * 0.6, h, step * 0.6)
        case "legal": return seq(step * 3, h - step * 0.5, step)
        case "header": return seq(h * 0.10 + step * 1.2, h - step * 0.5, step)
        case "cornell": return seq(h * 0.085 + step * 1.2, h * 0.80 - step * 0.3, step)
        default: return []
        }
    }

    // ---------- الرسم ----------

    private static func ruled(_ c: CGContext, _ x0: CGFloat, _ x1: CGFloat, _ y0: CGFloat, _ y1: CGFloat, _ step: CGFloat) {
        var y = y0
        while y < y1 {
            c.move(to: CGPoint(x: x0, y: y)); c.addLine(to: CGPoint(x: x1, y: y))
            y += step
        }
        c.strokePath()
    }

    private static func line(_ c: CGContext, _ x0: CGFloat, _ y0: CGFloat, _ x1: CGFloat, _ y1: CGFloat) {
        c.move(to: CGPoint(x: x0, y: y0)); c.addLine(to: CGPoint(x: x1, y: y1)); c.strokePath()
    }

    /// يرسم القالب في مستطيل w×h (الأصل أعلى اليسار).
    static func draw(_ c: CGContext, w: CGFloat, h: CGFloat, kind: String, bg: UInt32) {
        c.saveGState()
        defer { c.restoreGState() }
        c.setFillColor(UIColor(argb: bg).cgColor)
        c.fill(CGRect(x: 0, y: 0, width: w, height: h))
        if kind == "plain" { return }
        let lum = (0.299 * Double((bg >> 16) & 0xFF) + 0.587 * Double((bg >> 8) & 0xFF) + 0.114 * Double(bg & 0xFF)) / 255
        let dark = lum < 0.4
        let lineColor = dark ? UIColor(argb: 0x55FFFFFF) : UIColor(argb: 0xFFB9C5D8)
        let accent = dark ? UIColor(argb: 0x88E57373) : UIColor(argb: 0xFFE8A0A0)
        let unit = min(w, h * 0.707)
        let thin = max(0.5, unit / 650)
        let strongW = max(1, unit / 330)
        let step = unit / 24
        let m = step * 0.8
        c.setStrokeColor(lineColor.cgColor)
        c.setFillColor(lineColor.cgColor)
        c.setLineWidth(thin)
        func strong(_ body: () -> Void) {
            c.saveGState(); c.setLineWidth(strongW); body(); c.restoreGState()
        }
        switch kind {
        case "lined": ruled(c, step, w - step, step * 2.5, h - step, step)
        case "lined_n": ruled(c, step, w - step, step * 2.5, h - step, step * 0.75)
        case "grid", "grid_s":
            let st = kind == "grid" ? step : step * 0.6
            var x = st
            while x < w { c.move(to: CGPoint(x: x, y: 0)); c.addLine(to: CGPoint(x: x, y: h)); x += st }
            var y = st
            while y < h { c.move(to: CGPoint(x: 0, y: y)); c.addLine(to: CGPoint(x: w, y: y)); y += st }
            c.strokePath()
        case "dots":
            let r = max(0.9, unit / 420)
            var y = step
            while y < h {
                var x = step
                while x < w { c.fillEllipse(in: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r)); x += step }
                y += step
            }
        case "cornell":
            let top = h * 0.085, cueX = w * 0.30, sumY = h * 0.80
            strong {
                line(c, 0, top, w, top); line(c, cueX, top, cueX, sumY); line(c, 0, sumY, w, sumY)
            }
            ruled(c, cueX + step * 0.5, w - step * 0.6, top + step * 1.2, sumY - step * 0.3, step)
            ruled(c, step * 0.6, w - step * 0.6, sumY + step * 1.2, h - step * 0.5, step)
        case "legal":
            ruled(c, 0, w, step * 3, h - step * 0.5, step)
            strong { c.setStrokeColor(accent.cgColor); line(c, w * 0.12, 0, w * 0.12, h) }
        case "header":
            let top = h * 0.10
            strong { line(c, m, top, w - m, top) }
            ruled(c, m, w - m, top + step * 1.2, h - step * 0.5, step)
        case "columns2", "columns3":
            let n = kind == "columns2" ? 2 : 3
            let top = h * 0.08
            strong { line(c, m, top, w - m, top) }
            let colW = (w - 2 * m) / CGFloat(n)
            for k in 0..<n {
                let x0 = m + CGFloat(k) * colW
                if k > 0 { strong { line(c, x0, top, x0, h - m) } }
                ruled(c, x0 + step * 0.4, x0 + colW - step * 0.4, top + step * 1.2, h - step * 0.5, step)
            }
        case "table":
            let top = h * 0.08, bottom = h - m
            let colW = (w - 2 * m) / 4
            strong { c.stroke(CGRect(x: m, y: top, width: w - 2 * m, height: bottom - top)) }
            for k in 1..<4 { line(c, m + CGFloat(k) * colW, top, m + CGFloat(k) * colW, bottom) }
            strong { line(c, m, top + step * 1.4, w - m, top + step * 1.4) }
            ruled(c, m, w - m, top + step * 2.8, bottom, step * 1.4)
        case "qa":
            var y = h * 0.06
            let block = step * 6
            while y + block < h {
                strong { c.setStrokeColor(accent.cgColor); line(c, m, y + step, w - m, y + step) }
                ruled(c, m + step, w - m, y + step * 2.2, y + block - step * 0.4, step)
                y += block
            }
        case "todo":
            let top = h * 0.10
            strong { line(c, m, top, w - m, top) }
            var y = top + step * 1.4
            while y < h - step {
                line(c, m, y, w - m, y)
                let bx = m + step * 0.2
                c.stroke(CGRect(x: bx, y: y - step * 0.8, width: step * 0.6, height: step * 0.6))
                y += step * 1.2
            }
        case "weekly":
            let top = h * 0.08
            strong { line(c, m, top, w - m, top) }
            let rows = 4
            let cellW = (w - 2 * m) / 2, cellH = (h - m - top) / CGFloat(rows)
            for r in 0..<rows {
                for k in 0..<2 {
                    let x0 = m + CGFloat(k) * cellW, y0 = top + CGFloat(r) * cellH
                    c.stroke(CGRect(x: x0 + step * 0.2, y: y0 + step * 0.2, width: cellW - step * 0.4, height: cellH - step * 0.4))
                    strong { line(c, x0 + step * 0.2, y0 + step * 1.2, x0 + cellW - step * 0.2, y0 + step * 1.2) }
                }
            }
        case "monthly":
            let top = h * 0.12
            strong { line(c, m, top, w - m, top) }
            let cellW = (w - 2 * m) / 7
            let gridTop = top + step * 1.6
            let cellH = min((h - m - gridTop) / 6, cellW * 1.6)
            for r in 0...6 { line(c, m, gridTop + CGFloat(r) * cellH, w - m, gridTop + CGFloat(r) * cellH) }
            for k in 0...7 { line(c, m + CGFloat(k) * cellW, gridTop, m + CGFloat(k) * cellW, gridTop + 6 * cellH) }
        case "music":
            let gap = step * 0.45
            var y = h * 0.08
            while y + gap * 4 < h - m {
                for k in 0..<5 { c.move(to: CGPoint(x: m, y: y + CGFloat(k) * gap)); c.addLine(to: CGPoint(x: w - m, y: y + CGFloat(k) * gap)) }
                c.strokePath()
                y += gap * 4 + step * 2.2
            }
        default: break
        }
    }

    /// صورة مصغّرة للقالب (لنافذة الاختيار).
    static func preview(kind: String, bg: UInt32, size: CGSize) -> UIImage {
        let r = UIGraphicsImageRenderer(size: size)
        return r.image { ctx in
            // القالب مرسوم بمقاس A4 مصغّرًا
            let k = size.width / 595
            ctx.cgContext.scaleBy(x: k, y: k)
            draw(ctx.cgContext, w: 595, h: size.height / k, kind: kind, bg: bg)
        }
    }

    /// صفحة PDF فارغة بالقالب (تُدرج في مستند الملاحظة).
    static func makePage(kind: String, bg: UInt32, size: CGSize) -> PDFPage? {
        let data = UIGraphicsPDFRenderer(bounds: CGRect(origin: .zero, size: size)).pdfData { ctx in
            ctx.beginPage()
            draw(ctx.cgContext, w: size.width, h: size.height, kind: kind, bg: bg)
        }
        return PDFDocument(data: data)?.page(at: 0)
    }
}
