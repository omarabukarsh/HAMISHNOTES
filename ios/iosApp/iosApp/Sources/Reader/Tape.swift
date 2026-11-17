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

    init(id: String = UUID().uuidString, page: String, pts: [CGFloat], width: CGFloat, color: UInt32, revealed: Bool = false) {
        self.id = id
        self.page = page
        self.pts = pts
        self.width = width
        self.color = color
        self.revealed = revealed
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        page = try c.decode(String.self, forKey: .page)
        pts = try c.decodeIfPresent([CGFloat].self, forKey: .pts) ?? []
        width = try c.decodeIfPresent(CGFloat.self, forKey: .width) ?? 16
        color = try c.decodeIfPresent(UInt32.self, forKey: .color) ?? TapeTool.palette[0]
        revealed = try c.decodeIfPresent(Bool.self, forKey: .revealed) ?? false
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

    /// يرسم الشريط: مغطٍّ بلونه ونقشه، أو مكشوف (شفاف بخط متقطع). forExport = المكشوف لا يُرسم.
    func draw(in c: CGContext, k: CGFloat = 1, forExport: Bool = false) {
        guard pts.count >= 4 else { return }
        if revealed && forExport { return }
        let tw = width * k
        let col = UIColor(argb: color | 0xFF000000)
        c.saveGState()
        c.setLineCap(.butt)
        c.setLineJoin(.round)
        if !revealed {
            c.addPath(path(k: k))
            c.setStrokeColor(col.cgColor)
            c.setLineWidth(tw)
            c.strokePath()
            c.addPath(path(k: k))
            c.setStrokeColor(UIColor.white.withAlphaComponent(0.22).cgColor)
            c.setLineWidth(tw * 0.62)
            c.setLineDash(phase: 0, lengths: [tw * 0.28, tw * 0.28])
            c.strokePath()
        } else {
            c.addPath(path(k: k))
            c.setStrokeColor(col.withAlphaComponent(0.16).cgColor)
            c.setLineWidth(tw)
            c.strokePath()
            c.addPath(path(k: k))
            c.setStrokeColor(col.withAlphaComponent(0.7).cgColor)
            c.setLineWidth(max(1, tw * 0.06))
            c.setLineCap(.round)
            c.setLineDash(phase: 0, lengths: [tw * 0.3, tw * 0.22])
            c.strokePath()
        }
        c.restoreGState()
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
            draft = Tape(id: "draft", page: "", pts: pts, width: w, color: AppController.shared.tapeColor)
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

/// شريط أداة الشريط اللاصق: السماكة، اللون، الشريط المستقيم، الكشف والإخفاء، والإزالة من الصفحة.
struct TapeBar: View {
    @EnvironmentObject var app: AppController
    @ObservedObject var session: DocSession

    var body: some View {
        let pid = session.pageId(session.currentPage) ?? ""
        let list = session.data.tapes.filter { $0.page == pid }
        let anyHidden = list.contains { !$0.revealed }
        HStack(spacing: 0) {
            ForEach(0..<TapeTool.widths.count, id: \.self) { i in
                let sel = app.tapeSize == i
                TBtn(selected: sel, action: { app.tapeSize = i }) {
                    RoundedRectangle(cornerRadius: 2)
                        .fill(sel ? Color(hex: app.tapeColor) : Palette.textSecondary.opacity(0.55))
                        .frame(width: 22, height: CGFloat(5 + i * 4))
                }
            }
            ToolbarSep()
            ForEach(TapeTool.palette, id: \.self) { col in
                let sel = col == app.tapeColor
                Button { app.tapeColor = col } label: {
                    Circle().fill(Color(hex: col))
                        .frame(width: sel ? 26 : 22, height: sel ? 26 : 22)
                        .overlay(Circle().stroke(sel ? Palette.navy : Palette.divider, lineWidth: sel ? 2.5 : 1))
                        .frame(width: 34, height: 34)
                        .contentShape(Circle())
                }
                .buttonStyle(PressScale(scale: 0.9))
            }
            ToolbarSep()
            Button { app.tapeStraight.toggle() } label: {
                HStack(spacing: 4) {
                    if app.tapeStraight { PhIcon("ph_check", .white, 14) }
                    Text(L("شريط مستقيم", "Straight tape")).font(Fonts.sans(12)).foregroundColor(app.tapeStraight ? .white : Palette.textPrimary).lineLimit(1)
                }
                .padding(.horizontal, 10).padding(.vertical, 7)
                .background(RoundedRectangle(cornerRadius: 14).fill(app.tapeStraight ? Palette.navy : Palette.background))
            }
            .buttonStyle(PressScale())
            .padding(.trailing, 6)
            TBtn(action: { session.revealAllTapes(page: pid, anyHidden) }) {
                PhIcon("ph_eye", Palette.textSecondary, 20,
                       anyHidden ? L("كشف كل أشرطة الصفحة", "Reveal all tape on the page") : L("إخفاء كل أشرطة الصفحة", "Hide all tape on the page"))
            }
            TBtn(action: { session.removeAllTapes(page: pid) }) {
                PhIcon("ph_trash", Palette.danger, 20, L("إزالة كل الأشرطة من الصفحة", "Remove all tape from the page"))
            }
            ToolbarSep()
            TBtn(action: { app.tapeActive = false }) { PhIcon("ph_close", Palette.textSecondary, 18, L("إغلاق الشريط اللاصق", "Close tape")) }
        }
        .padding(.horizontal, 10).padding(.vertical, 4)
        .background(Capsule().fill(Color.white))
        .overlay(Capsule().stroke(Palette.divider, lineWidth: 1))
        .shadow(color: Palette.ink.opacity(0.15), radius: 6, y: 2)
        .padding(.bottom, 6)
    }
}
