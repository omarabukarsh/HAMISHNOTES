import UIKit
import SwiftUI

/// شريط لاصق فوق الصفحة يخفي ما تحته (للمراجعة): نقرة عليه تكشف ما تحته، ونقرة ثانية تعيد إخفاءه.
/// pts = نقاط مسار الشريط (x0, y0, x1, y1، …) بنقاط الصفحة، width = عرضه بنقاط الصفحة، revealed = مكشوف الآن.
/// في ملف أندرويد (hamish-data.json) يُحفظ تحت "tapes" بإحداثيات نسبية لعرض الصفحة.
struct Tape: Codable, Identifiable, Equatable {
    var id: String
    var page: String
    var pts: [CGFloat]
    var width: CGFloat
    var color: UInt32
    var revealed = false
    /// شكل الشريط (TapeStyle): 0 واشي مخطّط، 1 ورقي ممزّق، 2 منقّط، 3 ناعم بسيط. يُحفظ في ملف أندرويد تحت "st".
    var style = TapeStyle.soft

    init(id: String = UUID().uuidString, page: String, pts: [CGFloat], width: CGFloat, color: UInt32, revealed: Bool = false, style: Int = TapeStyle.soft) {
        self.id = id
        self.page = page
        self.pts = pts
        self.width = width
        self.color = color
        self.revealed = revealed
        self.style = style
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        page = try c.decode(String.self, forKey: .page)
        pts = try c.decodeIfPresent([CGFloat].self, forKey: .pts) ?? []
        width = try c.decodeIfPresent(CGFloat.self, forKey: .width) ?? 16
        color = try c.decodeIfPresent(UInt32.self, forKey: .color) ?? TapeTool.palette[0]
        revealed = try c.decodeIfPresent(Bool.self, forKey: .revealed) ?? false
        let st = try c.decodeIfPresent(Int.self, forKey: .style) ?? TapeStyle.soft
        style = min(3, max(0, st))
    }

    /// نقاط المسار.
    var points: [CGPoint] {
        stride(from: 0, to: pts.count - 1, by: 2).map { CGPoint(x: pts[$0], y: pts[$0 + 1]) }
    }

    /// طول المسار بنقاط الصفحة.
    var length: CGFloat {
        let p = points
        guard p.count > 1 else { return 0 }
        return (1..<p.count).reduce(CGFloat(0)) { $0 + hypot(p[$1].x - p[$1 - 1].x, p[$1].y - p[$1 - 1].y) }
    }

    /// هل النقطة (بنقاط الصفحة) فوق الشريط؟ slack = سماحة إضافية.
    func hits(_ q: CGPoint, slack: CGFloat = 3) -> Bool {
        let p = points
        guard p.count > 1 else { return false }
        let r = width / 2 + slack
        for i in 1..<p.count where Tape.segDist(q, p[i - 1], p[i]) <= r { return true }
        return false
    }

    private static func segDist(_ q: CGPoint, _ a: CGPoint, _ b: CGPoint) -> CGFloat {
        let dx = b.x - a.x, dy = b.y - a.y
        let l2 = dx * dx + dy * dy
        let t = l2 <= 0 ? 0 : min(1, max(0, ((q.x - a.x) * dx + (q.y - a.y) * dy) / l2))
        return hypot(q.x - (a.x + t * dx), q.y - (a.y + t * dy))
    }

    /// مسار الشريط بمقياس k (نقاط الطبقة لكل نقطة صفحة).
    func path(k: CGFloat) -> CGPath {
        let path = CGMutablePath()
        let p = points
        guard let f = p.first else { return path }
        path.move(to: CGPoint(x: f.x * k, y: f.y * k))
        for q in p.dropFirst() { path.addLine(to: CGPoint(x: q.x * k, y: q.y * k)) }
        return path
    }

    /// بذرة ثابتة للحواف الممزّقة والملمس: من نقطة البداية (لا تتغير بين المسودة والشريط المحفوظ).
    private var seed: UInt64 { UInt64(bitPattern: Int64(pts[0] * 1000) &* 31 &+ Int64(pts[1] * 1000)) }

    /// حدود الشريط مضلعًا مغلقًا بمقياس k: جانباه على بعد نصف عرضه من مساره، وطرفاه بشكل نوعه
    /// (متعرّج للواشي، ممزّق للورقي، مائل للمنقّط، مستدير للناعم). نفس الحساب في أندرويد.
    func outline(k: CGFloat) -> [CGPoint] {
        let p = points.map { CGPoint(x: $0.x * k, y: $0.y * k) }
        let n = p.count
        guard n >= 2 else { return [] }
        let h = width * k / 2
        func dir(_ i: Int) -> CGPoint {
            let dx = p[i + 1].x - p[i].x, dy = p[i + 1].y - p[i].y
            let l = max(0.0001, hypot(dx, dy))
            return CGPoint(x: dx / l, y: dy / l)
        }
        var left: [CGPoint] = [], right: [CGPoint] = []
        for i in 0..<n {
            let a = dir(max(0, i - 1)), b = dir(min(i, n - 2))
            var nx = -(a.y + b.y), ny = a.x + b.x
            let nl = hypot(nx, ny)
            if nl < 0.001 { nx = -b.y; ny = b.x } else { nx /= nl; ny /= nl }
            let cosv = min(1, max(0.5, nx * -b.y + ny * b.x))
            let kk = h / cosv
            left.append(CGPoint(x: p[i].x + nx * kk, y: p[i].y + ny * kk))
            right.append(CGPoint(x: p[i].x - nx * kk, y: p[i].y - ny * kk))
        }
        var rng = TapeRandom(seed: seed)
        let tw = 2 * h
        func cap(_ out: inout [CGPoint], _ a: CGPoint, _ b: CGPoint, _ o: CGPoint) {
            switch style {
            case TapeStyle.washi:
                let teeth = 4
                for j in 1..<(teeth * 2) {
                    let f = CGFloat(j) / CGFloat(teeth * 2)
                    let d: CGFloat = j % 2 == 1 ? -tw * 0.16 : 0
                    out.append(CGPoint(x: a.x + (b.x - a.x) * f + o.x * d, y: a.y + (b.y - a.y) * f + o.y * d))
                }
            case TapeStyle.torn:
                let steps = 7
                for j in 1..<steps {
                    let f = CGFloat(j) / CGFloat(steps)
                    let d = -rng.next() * tw * 0.24
                    out.append(CGPoint(x: a.x + (b.x - a.x) * f + o.x * d, y: a.y + (b.y - a.y) * f + o.y * d))
                }
            case TapeStyle.soft:
                let cx = (a.x + b.x) / 2, cy = (a.y + b.y) / 2
                let rx = a.x - cx, ry = a.y - cy
                for j in 1..<10 {
                    let th = CGFloat.pi * CGFloat(j) / 10
                    out.append(CGPoint(x: cx + rx * cos(th) + o.x * h * sin(th), y: cy + ry * cos(th) + o.y * h * sin(th)))
                }
            default: break
            }
        }
        let s0 = dir(0), e0 = dir(n - 2)
        let slant: CGFloat = style == TapeStyle.dots ? h * 0.35 : 0
        let inset: CGFloat = style == TapeStyle.soft ? h : 0
        left[0].x += s0.x * (inset - slant); left[0].y += s0.y * (inset - slant)
        right[0].x += s0.x * (inset + slant); right[0].y += s0.y * (inset + slant)
        left[n - 1].x -= e0.x * (inset - slant); left[n - 1].y -= e0.y * (inset - slant)
        right[n - 1].x -= e0.x * (inset + slant); right[n - 1].y -= e0.y * (inset + slant)
        var out = left
        cap(&out, left[n - 1], right[n - 1], e0)
        out += right.reversed()
        cap(&out, right[0], left[0], CGPoint(x: -s0.x, y: -s0.y))
        return out
    }

    /// يرسم الشريط: المغطّي بشكله ونقشه، والمكشوف حوافه فقط بتعبئة شفافة خفيفة جدًا، بدون أي خط في وسطه.
    /// forExport = المكشوف لا يُرسم.
    func draw(in c: CGContext, k: CGFloat = 1, forExport: Bool = false) {
        guard pts.count >= 4 else { return }
        if revealed && forExport { return }
        let o = outline(k: k)
        guard o.count >= 3 else { return }
        let shape = CGMutablePath()
        shape.addLines(between: o)
        shape.closeSubpath()
        let tw = width * k
        let base = UIColor(argb: color | 0xFF000000)
        c.saveGState()
        defer { c.restoreGState() }
        if revealed {
            c.addPath(shape)
            c.setFillColor(base.withAlphaComponent(0.10).cgColor)
            c.fillPath()
            c.addPath(shape)
            c.setLineJoin(.round)
            c.setLineWidth(max(1.2, tw * 0.07))
            c.setStrokeColor(TapeStyle.darker(color, 0.12).cgColor)
            c.strokePath()
            return
        }
        if style == TapeStyle.soft {
            // ظل خفيف جدًا تحت الشريط
            c.saveGState()
            c.translateBy(x: 0, y: max(1, tw * 0.06))
            c.addPath(shape)
            c.setFillColor(UIColor.black.withAlphaComponent(0.12).cgColor)
            c.fillPath()
            c.restoreGState()
        }
        c.addPath(shape)
        c.setFillColor(base.cgColor)
        c.fillPath()
        let b = shape.boundingBoxOfPath
        switch style {
        case TapeStyle.washi:
            c.saveGState()
            c.addPath(shape); c.clip()
            c.setStrokeColor(TapeStyle.lighter(color, 0.2).cgColor)
            c.setLineWidth(tw * 0.16)
            let step = tw * 0.36
            var x = b.minX - b.height
            while x < b.maxX + b.height {
                c.move(to: CGPoint(x: x, y: b.maxY)); c.addLine(to: CGPoint(x: x + b.height, y: b.minY))
                x += step
            }
            c.strokePath()
            c.restoreGState()
        case TapeStyle.dots:
            c.saveGState()
            c.addPath(shape); c.clip()
            c.setFillColor(TapeStyle.lighter(color, 0.45).cgColor)
            let step = tw * 0.32, r = tw * 0.06
            var row = 0
            var y = b.minY + step / 2
            while y < b.maxY + step {
                var x = b.minX + (row % 2 == 1 ? step / 2 : 0)
                while x < b.maxX + step { c.fillEllipse(in: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r)); x += step }
                y += step; row += 1
            }
            c.restoreGState()
        case TapeStyle.torn:
            // ملمس ورق: ألياف قصيرة فاتحة وداكنة قليلًا
            c.saveGState()
            c.addPath(shape); c.clip()
            var rng = TapeRandom(seed: seed &* 31)
            c.setLineWidth(max(0.6, tw * 0.025))
            c.setLineCap(.round)
            let count = min(400, max(6, Int(b.width * b.height / (tw * tw) * 9)))
            for _ in 0..<count {
                let x = b.minX + rng.next() * b.width, y = b.minY + rng.next() * b.height
                let a = rng.next() * 6.283, l = tw * (0.08 + rng.next() * 0.14)
                c.setStrokeColor(rng.next() < 0.5 ? UIColor.white.withAlphaComponent(0.18).cgColor : UIColor.black.withAlphaComponent(0.06).cgColor)
                c.move(to: CGPoint(x: x, y: y)); c.addLine(to: CGPoint(x: x + cos(a) * l, y: y + sin(a) * l))
                c.strokePath()
            }
            c.restoreGState()
        default: break
        }
    }
}

/// أشكال الشريط اللاصق (نفس أرقام أندرويد).
enum TapeStyle {
    static let washi = 0
    static let torn = 1
    static let dots = 2
    static let soft = 3
    static let all = [washi, torn, dots, soft]

    static func name(_ s: Int) -> String {
        switch s {
        case washi: return L("واشي مخطّط", "Striped washi")
        case torn: return L("ورقي ممزّق", "Torn paper")
        case dots: return L("منقّط", "Dotted")
        default: return L("ناعم بسيط", "Soft")
        }
    }

    private static func rgb(_ c: UInt32) -> (CGFloat, CGFloat, CGFloat) {
        (CGFloat((c >> 16) & 0xFF) / 255, CGFloat((c >> 8) & 0xFF) / 255, CGFloat(c & 0xFF) / 255)
    }
    static func lighter(_ c: UInt32, _ f: CGFloat) -> UIColor {
        let (r, g, b) = rgb(c)
        return UIColor(red: r + (1 - r) * f, green: g + (1 - g) * f, blue: b + (1 - b) * f, alpha: 1)
    }
    static func darker(_ c: UInt32, _ f: CGFloat) -> UIColor {
        let (r, g, b) = rgb(c)
        return UIColor(red: r * (1 - f), green: g * (1 - f), blue: b * (1 - f), alpha: 1)
    }
}

/// مولّد عشوائي بسيط ثابت البذرة (لحواف الشريط الممزّق وملمسه).
struct TapeRandom {
    private var state: UInt64
    init(seed: UInt64) { state = seed == 0 ? 0x9E3779B97F4A7C15 : seed }
    /// رقم بين 0 و1.
    mutating func next() -> CGFloat {
        state ^= state << 13; state ^= state >> 7; state ^= state << 17
        return CGFloat(state % 10_000) / 10_000
    }
}

/// إعدادات أداة الشريط اللاصق (نفس قيم أندرويد).
enum TapeTool {
    /// عرض الشريط نسبةً لعرض الصفحة: رفيع، متوسط، عريض.
    static let widths: [CGFloat] = [0.018, 0.028, 0.042]
    static let palette: [UInt32] = [0xFFF2A65A, 0xFFEF8E8E, 0xFFF2D35B, 0xFF8CC08B, 0xFF7FB0DE, 0xFFAB93D6, 0xFF9AA3AD]

    /// مسار الشريط من نقاط السحب (بنقاط الصفحة): المستقيم من البداية إلى النهاية
    /// (يستقيم أفقيًا أو عموديًا إن كان قريبًا منهما)، والحر بنقاط متباعدة قليلًا.
    static func pathOf(_ raw: [CGPoint], width: CGFloat, straight: Bool) -> [CGFloat] {
        guard raw.count > 1, let a = raw.first, var b = raw.last else { return [] }
        if straight {
            var ang = atan2(b.y - a.y, b.x - a.x) * 180 / .pi
            ang = (ang.truncatingRemainder(dividingBy: 180) + 180).truncatingRemainder(dividingBy: 180)
            if ang < 6 || ang > 174 { b.y = a.y } else if abs(ang - 90) < 6 { b.x = a.x }
            return [a.x, a.y, b.x, b.y]
        }
        var out: [CGFloat] = []
        var last: CGPoint? = nil
        for (i, p) in raw.enumerated() {
            if let l = last, i != raw.count - 1, hypot(p.x - l.x, p.y - l.y) < width * 0.35 { continue }
            out += [p.x, p.y]
            last = p
        }
        return out
    }
}

// ======================================================== الجلسة

extension DocSession {
    /// يضيف شريطًا (خطوة تراجع).
    func addTape(_ t: Tape) { edit { $0.tapes.append(t) } }

    /// نقرة على شريط: تكشف ما تحته أو تعيد إخفاءه (لا تُسجَّل في التراجع). true إن أصابت شريطًا.
    @discardableResult
    func toggleTape(at p: CGPoint, page: String) -> Bool {
        guard let i = data.tapes.lastIndex(where: { $0.page == page && $0.hits(p) }) else { return false }
        data.tapes[i].revealed.toggle()
        dataDirty = true
        scheduleSave()
        return true
    }

    /// يكشف كل أشرطة الصفحة أو يخفيها.
    func revealAllTapes(page: String, _ reveal: Bool) {
        guard data.tapes.contains(where: { $0.page == page }) else { return }
        for i in data.tapes.indices where data.tapes[i].page == page { data.tapes[i].revealed = reveal }
        dataDirty = true
        scheduleSave()
    }

    /// يزيل كل أشرطة الصفحة (خطوة تراجع).
    func removeAllTapes(page: String) {
        guard data.tapes.contains(where: { $0.page == page }) else { return }
        edit { $0.tapes.removeAll { $0.page == page } }
    }

    /// يزيل أشرطة بمعرّفاتها (الممحاة؛ خطوة تراجع واحدة).
    func removeTapes(_ ids: Set<String>) {
        guard !ids.isEmpty else { return }
        edit { $0.tapes.removeAll { ids.contains($0.id) } }
    }
}

// ======================================================== طبقة الصفحة

/// طبقة الأشرطة فوق الكتابة والملاحظات. مع أداة الشريط تأخذ القلم (أو الإصبع إن كان يكتب) وترسم شريطًا بالسحب؛
/// والنقرات تذهب إلى الصفحة (تكشف الشريط أو تخفيه).
final class TapeView: UIView {
    var k: CGFloat = 1
    var tapes: [Tape] = []
    var draft: Tape?
    /// أشرطة تلمسها الممحاة الآن (تُخفى حتى تُزال عند رفع القلم).
    var erasing: Set<String> = []
    var capturing = false
    var fingerWrites = false
    /// عرض الصفحة بنقاطها (لعرض الشريط).
    var pageW: CGFloat = 595
    /// شريط جديد اكتمل (بنقاط الصفحة).
    var onTape: (([CGFloat], CGFloat) -> Void)?
    private var raw: [CGPoint] = []
    private var startPt: CGPoint?
    private var dragged = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isOpaque = false
        contentMode = .redraw
    }

    required init?(coder: NSCoder) { fatalError() }

    private func accept(_ t: UITouch) -> Bool { capturing && (fingerWrites || t.type == .pencil) }

    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        guard capturing else { return false }
        if let t = event?.allTouches?.first, !accept(t) { return false }
        return super.point(inside: point, with: event)
    }

    override func draw(_ rect: CGRect) {
        guard let c = UIGraphicsGetCurrentContext() else { return }
        for t in tapes where !erasing.contains(t.id) { t.draw(in: c, k: k) }
        draft?.draw(in: c, k: k)
    }

    private var currentWidth: CGFloat { TapeTool.widths[min(max(0, AppController.shared.tapeSize), TapeTool.widths.count - 1)] * pageW }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t = touches.first, accept(t) else { return }
        let p = t.location(in: self)
        startPt = p
        dragged = false
        raw = [CGPoint(x: p.x / k, y: p.y / k)]
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t = touches.first, let s = startPt else { return }
        let p = t.location(in: self)
        if !dragged && hypot(p.x - s.x, p.y - s.y) > 8 { dragged = true }
        guard dragged else { return }
        raw.append(CGPoint(x: p.x / k, y: p.y / k))
        let w = currentWidth
        let pts = TapeTool.pathOf(raw, width: w, straight: AppController.shared.tapeStraight)
        if pts.count >= 4 {
            draft = Tape(id: "draft", page: "", pts: pts, width: w, color: AppController.shared.tapeColor, style: AppController.shared.tapeStyle)
            setNeedsDisplay()
        }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        defer { clear() }
        guard dragged, startPt != nil else { return }
        if let p = touches.first?.location(in: self) { raw.append(CGPoint(x: p.x / k, y: p.y / k)) }
        let w = currentWidth
        let pts = TapeTool.pathOf(raw, width: w, straight: AppController.shared.tapeStraight)
        let probe = Tape(page: "", pts: pts, width: w, color: 0)
        if pts.count >= 4 && probe.length > w * 0.8 { onTape?(pts, w) }
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { clear() }

    private func clear() {
        startPt = nil
        dragged = false
        raw = []
        if draft != nil { draft = nil; setNeedsDisplay() }
    }
}

// ======================================================== شريط الأداة

/// شريط أداة الشريط اللاصق: أيقونة الأداة (تفتح نافذة الإعدادات)، السماكة، اللون، والإغلاق.
struct TapeBar: View {
    @EnvironmentObject var app: AppController
    @ObservedObject var session: DocSession

    var body: some View {
        HStack(spacing: 0) {
            // أيقونة الأداة: تفتح نافذة الإعدادات تحتها
            TBtn(selected: true, action: { app.tapeMenuOpen = true }) {
                PhIcon("ph_tape", Palette.chromeAccent, 22, L("إعدادات الشريط اللاصق", "Tape settings"))
            }
            // نافذة الإعدادات تخرج من حافة الزر وتنثني إليه (مثل باقي نوافذ الشريط)
            .dropdown(isPresented: $app.tapeMenuOpen, fromEdge: true) {
                TapeMenu(session: session).environmentObject(app)
            }
            ToolbarSep()
            ForEach(0..<TapeTool.widths.count, id: \.self) { i in
                let sel = app.tapeSize == i
                TBtn(selected: sel, action: { app.tapeSize = i }) {
                    RoundedRectangle(cornerRadius: 2)
                        .fill(sel ? Color(hex: app.tapeColor) : Palette.chromeMuted.opacity(0.55))
                        .frame(width: 22, height: CGFloat(5 + i * 4))
                }
            }
            ToolbarSep()
            ForEach(TapeTool.palette, id: \.self) { col in
                let sel = col == app.tapeColor
                Button { app.tapeColor = col } label: {
                    Circle().fill(Color(hex: col))
                        .frame(width: sel ? 26 : 22, height: sel ? 26 : 22)
                        .overlay(Circle().stroke(sel ? Palette.chromeAccent : Palette.chromeLine, lineWidth: sel ? 2.5 : 1))
                        .barItem()
                        .contentShape(Circle())
                }
                .buttonStyle(PressScale(scale: 0.9))
            }
            ToolbarSep()
            TBtn(action: { app.tapeActive = false; app.tapeMenuOpen = false }) { PhIcon("ph_close", Palette.chromeMuted, 18, L("إغلاق الشريط اللاصق", "Close tape")) }
        }
        .padding(.horizontal, 2).padding(.vertical, 4)
        .modifier(BarCapsule())
    }
}

/// نافذة إعدادات الشريط اللاصق (بنفس شكل نوافذ إعدادات القلم والممحاة).
private struct TapeMenu: View {
    @EnvironmentObject var app: AppController
    @ObservedObject var session: DocSession

    var body: some View {
        let pid = session.pageId(session.currentPage) ?? ""
        let list = session.data.tapes.filter { $0.page == pid }
        let anyHidden = list.contains { !$0.revealed }
        VStack(alignment: .leading, spacing: 8) {
            Text(L("شكل الشريط", "Tape style")).font(Fonts.sans(12.5, .semibold)).foregroundColor(Palette.textSecondary)
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
                ForEach(TapeStyle.all, id: \.self) { st in
                    let sel = app.tapeStyle == st
                    Button { app.tapeStyle = st } label: {
                        VStack(spacing: 6) {
                            TapeStylePreview(style: st, color: app.tapeColor).frame(width: 56, height: 26)
                            Text(TapeStyle.name(st)).font(Fonts.sans(13)).foregroundColor(Palette.textPrimary).lineLimit(1)
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                        .background(RoundedRectangle(cornerRadius: 12).fill(sel ? Palette.navySoft : Palette.background))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(sel ? Palette.navy : Color.clear, lineWidth: 1.5))
                    }
                    .buttonStyle(PressScale())
                }
            }
            Rectangle().fill(Palette.divider).frame(height: 1).padding(.vertical, 4)
            Toggle(isOn: $app.tapeStraight) {
                Text(L("شريط مستقيم", "Straight tape")).font(Fonts.sans(14)).foregroundColor(Palette.textPrimary)
            }
            .toggleStyle(DrawnSwitch())
            menuRow("ph_eye", anyHidden ? L("كشف كل أشرطة الصفحة", "Reveal all tape on the page") : L("إخفاء كل أشرطة الصفحة", "Hide all tape on the page"),
                    Palette.textPrimary, enabled: !list.isEmpty) { session.revealAllTapes(page: pid, anyHidden); app.tapeMenuOpen = false }
            menuRow("ph_trash", L("إزالة كل الأشرطة من الصفحة", "Remove all tape from the page"), Palette.danger, enabled: !list.isEmpty) {
                session.removeAllTapes(page: pid); app.tapeMenuOpen = false
            }
        }
        .padding(16)
        .frame(width: 320)
    }

    private func menuRow(_ icon: String, _ label: String, _ color: Color, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 10) {
                PhIcon(icon, color, 20)
                Text(label).font(Fonts.sans(14)).foregroundColor(color)
                Spacer(minLength: 0)
            }
            .padding(.vertical, 10).contentShape(Rectangle())
        }
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.4)
    }
}

/// معاينة صغيرة لشكل الشريط.
struct TapeStylePreview: View {
    let style: Int
    let color: UInt32

    var body: some View {
        Canvas { ctx, size in
            let t = Tape(id: "preview", page: "", pts: [size.width * 0.1, size.height / 2, size.width * 0.9, size.height / 2],
                         width: size.height * 0.8, color: color, style: style)
            ctx.withCGContext { t.draw(in: $0) }
        }
    }
}
