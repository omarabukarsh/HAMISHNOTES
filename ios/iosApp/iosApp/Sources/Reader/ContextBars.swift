import SwiftUI
import PDFKit

// الأشرطة فوق العنصر المحدد (تحديد نص، تحديد حر، صورة، مربع نص، ملاحظة، لصق، كلمة محوّلة):
// تظهر فوق العنصر مباشرة بسهم صغير يشير إليه، وتحته إن لم يتسع فوقه، وتتبعه مع التمرير والتكبير.
//
// المقاسات موحّدة (نفس أندرويد): كل زر بارتفاع 34 وحشوة 11 على الجانبين، والمسافة بين أي زرين 4،
// والفراغ حول الأزرار داخل الشريط 4 من كل جهة، وزاوية الشريط = زاوية الزر (10) + 4.

/// ما يُشار إليه: مستطيل بنقاط الصفحة (الأصل أعلى اليسار)، أو نص محدد في PDF.
enum BarAnchor {
    case page(Int, CGRect)
    case selection(PDFSelection)
}

/// يزيد كلما تحركت الصفحات (تمرير، تكبير): الأشرطة فوق العناصر وحدها تتابعه.
final class PageGeo: ObservableObject {
    @Published var tick = 0
}

/// مكان الضغط المطوّل على الصفحة (لفقاعة «لصق» فوقه).
struct PastePoint: Equatable {
    var page: Int
    var at: CGPoint
}

/// جسم الشريط: الأزرار في صف واحد بمسافات متساوية.
struct CtxBar<Content: View>: View {
    @ViewBuilder let content: () -> Content
    @Environment(\.ctxReveal) private var reveal
    var body: some View {
        // الأزرار تظهر بعد أن تتمدد القطرة إلى الشريط
        HStack(spacing: 4) { content() }
            .opacity(reveal).offset(y: (1 - reveal) * 4)
            .padding(4)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Palette.chromeBg)
                    .shadow(color: .black.opacity(0.18), radius: 8, y: 3)
            )
            .fixedSize()
    }
}

private struct CtxRevealKey: EnvironmentKey { static let defaultValue: CGFloat = 1 }
extension EnvironmentValues {
    /// ظهور محتوى الشريط (0..1) أثناء حركة القطرة.
    var ctxReveal: CGFloat {
        get { self[CtxRevealKey.self] }
        set { self[CtxRevealKey.self] = newValue }
    }
}

/// حركة «قطرة الماء»: تنبع قطرة صغيرة من رأس السهم، ترتفع وتتمدد إلى الشريط مع ارتداد خفيف.
/// p من 0 إلى 1 → (عرض، ارتفاع، إزاحة رأسية، شفافية).
func dropAt(_ p: CGFloat) -> (CGFloat, CGFloat, CGFloat, CGFloat) {
    let keys: [(CGFloat, CGFloat, CGFloat, CGFloat, CGFloat)] = [
        (0, 0.04, 0.30, 6, 0), (0.35, 0.07, 0.60, 0, 1), (0.72, 1.06, 0.86, 4, 1), (0.88, 0.98, 1.04, -1, 1), (1, 1, 1, 0, 1)
    ]
    let q = min(max(p, 0), 1)
    for i in 1..<keys.count {
        let a = keys[i - 1], b = keys[i]
        if q <= b.0 {
            let t = b.0 == a.0 ? 1 : (q - a.0) / (b.0 - a.0)
            return (a.1 + (b.1 - a.1) * t, a.2 + (b.2 - a.2) * t, a.3 + (b.3 - a.3) * t, a.4 + (b.4 - a.4) * t)
        }
    }
    return (1, 1, 0, 1)
}

/// يطبّق شكل القطرة في كل إطار من الحركة (p قابل للتحريك).
private struct DropEffect: ViewModifier, Animatable {
    var p: CGFloat
    let anchor: UnitPoint
    let below: Bool
    var animatableData: CGFloat {
        get { p }
        set { p = newValue }
    }
    func body(content: Content) -> some View {
        let d = dropAt(p)
        content
            .environment(\.ctxReveal, min(max((p - 0.7) / 0.3, 0), 1))
            .scaleEffect(x: d.0, y: d.1, anchor: anchor)
            .offset(y: below ? -d.2 : d.2)
            .opacity(d.3)
    }
}

/// زر في الشريط: عادي، أو أساسي بخلفية كحلية — بنفس المقاس في الحالتين.
struct CtxBtn: View {
    let label: String
    var primary = false
    var color: Color = Palette.chromeFg
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(Fonts.sans(13.5, primary ? .bold : .medium))
                .foregroundColor(primary ? .white : color)
                .lineLimit(1).fixedSize()
                .padding(.horizontal, 11).frame(height: 34)
                .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(primary ? Palette.navy : Color.clear))
                .contentShape(Rectangle())
        }
        .buttonStyle(PressScale())
    }
}

/// «⋯»: بقية الأوامر في نافذة صغيرة.
struct CtxMore<Content: View>: View {
    @ViewBuilder let content: (_ close: @escaping () -> Void) -> Content
    @State private var open = false
    var body: some View {
        CtxBtn(label: "⋯") { open = true }
            .popover(isPresented: $open) {
                VStack(alignment: .leading, spacing: 0) { content { open = false } }
                    .padding(.vertical, 6)
                    .frame(minWidth: 220)
                    .presentationCompactAdaptation(.popover)
            }
    }
}

struct CtxItem: View {
    let label: String
    var color: Color = Palette.textPrimary
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label).font(Fonts.sans(15)).foregroundColor(color)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16).padding(.vertical, 11)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// سهم صغير (مثلث) أسفل الشريط أو أعلاه.
private struct BarArrow: Shape {
    let down: Bool
    func path(in r: CGRect) -> Path {
        var p = Path()
        if down {
            p.move(to: CGPoint(x: r.minX, y: r.minY)); p.addLine(to: CGPoint(x: r.maxX, y: r.minY)); p.addLine(to: CGPoint(x: r.midX, y: r.maxY))
        } else {
            p.move(to: CGPoint(x: r.midX, y: r.minY)); p.addLine(to: CGPoint(x: r.maxX, y: r.maxY)); p.addLine(to: CGPoint(x: r.minX, y: r.maxY))
        }
        p.closeSubpath()
        return p
    }
}

/// يضع الشريط فوق العنصر (وتحته إن لم يتسع فوقه). preferBelow: يُفضّل تحته (مثل «تم التحويل» تحت السطر).
struct AnchoredBar<Bar: View>: View {
    @ObservedObject var session: DocSession
    @ObservedObject var geo: PageGeo
    let anchor: BarAnchor
    var preferBelow = false
    /// false: الشريط يرجع قطرةً ويختفي (أثناء السحب، أو قبل اكتمال التحديد).
    var visible = true
    @ViewBuilder let bar: () -> Bar
    @State private var size: CGSize = .zero
    @State private var prog: CGFloat = 0

    init(session: DocSession, anchor: BarAnchor, preferBelow: Bool = false, visible: Bool = true, @ViewBuilder bar: @escaping () -> Bar) {
        self.visible = visible
        _session = ObservedObject(wrappedValue: session)
        _geo = ObservedObject(wrappedValue: session.geo)
        self.anchor = anchor
        self.preferBelow = preferBelow
        self.bar = bar
    }

    private struct Spot { var x: CGFloat; var y: CGFloat; var ax: CGFloat; var ay: CGFloat; var down: Bool }

    var body: some View {
        GeometryReader { g in
            let origin = g.frame(in: .global).origin
            let _ = geo.tick
            if let el = session.anchorHook?(anchor), let spot = place(el.offsetBy(dx: -origin.x, dy: -origin.y),
                                                                       session.viewportHook?()?.offsetBy(dx: -origin.x, dy: -origin.y),
                                                                       g.size) {
                let ox = size.width > 0 ? min(max((spot.ax + 8 - spot.x) / size.width, 0), 1) : 0.5
                ZStack(alignment: .topLeading) {
                    bar()
                        .onGeometryChange(for: CGSize.self) { $0.size } action: { size = $0 }
                        .modifier(DropEffect(p: prog, anchor: UnitPoint(x: ox, y: spot.down ? 1 : 0), below: !spot.down))
                        .offset(x: spot.x, y: spot.y)
                        .allowsHitTesting(prog > 0.5)
                    BarArrow(down: spot.down).fill(Palette.chromeBg)
                        .frame(width: 16, height: 8)
                        .scaleEffect(x: 1, y: 0.4 + 0.6 * min(max((prog - 0.7) / 0.3, 0), 1), anchor: spot.down ? .top : .bottom)
                        .opacity(min(max((prog - 0.7) / 0.3, 0), 1))
                        .offset(x: spot.ax, y: spot.ay)
                        .allowsHitTesting(false)
                }
            }
        }
        .onAppear { show(visible) }
        .onChange(of: visible) { _, v in show(v) }
    }

    /// يظهر بعد اكتمال التحديد بلحظة قصيرة كقطرة، ويرجع قطرةً عند الإخفاء.
    private func show(_ v: Bool) {
        if v {
            withAnimation(.timingCurve(0.25, 0.8, 0.3, 1, duration: 0.52).delay(prog == 0 ? 0.12 : 0)) { prog = 1 }
        } else {
            withAnimation(.timingCurve(0.5, 0, 0.75, 0, duration: 0.2)) { prog = 0 }
        }
    }

    private func place(_ r: CGRect, _ vp: CGRect?, _ box: CGSize) -> Spot? {
        let margin: CGFloat = 8, gap: CGFloat = 10
        let top = (vp?.minY ?? 0) + margin
        let bottom = (vp?.maxY ?? box.height) - margin
        if r.maxY < top || r.minY > bottom { return nil } // العنصر خارج الشاشة
        let above = r.minY - gap - size.height
        let below = r.maxY + gap
        let useBelow = preferBelow ? (below + size.height <= bottom || above < top) : above < top
        let y = min(max(useBelow ? below : above, top), max(top, bottom - size.height))
        let x = min(max(r.midX - size.width / 2, margin), max(margin, box.width - margin - size.width))
        let ax = min(max(r.midX - 8, x + 12), max(x + 12, x + size.width - 12 - 16))
        let ay = useBelow ? y - 8 + 1 : y + size.height - 1
        return Spot(x: x, y: y, ax: ax, ay: ay, down: !useBelow)
    }
}

extension DocSession {
    /// حدود النص المحدد بالكلمات المقروءة (نسبية لعرض الصفحة) بنقاط الصفحة.
    func wordSelRect(_ ws: WordSel) -> CGRect? {
        guard !ws.words.isEmpty else { return nil }
        let w = pageSize(ws.page).width
        var r = CGRect.null
        for x in ws.words { r = r.union(CGRect(x: x.l * w, y: x.t * w, width: (x.r - x.l) * w, height: (x.b - x.t) * w)) }
        return r.isNull ? nil : r
    }

    /// الكلمة المحوّلة التي تُعرض قراءاتها الأخرى، بنقاط الصفحة.
    func altRect(_ p: AltPopup) -> (Int, CGRect)? {
        guard let line = data.converted.first(where: { $0.id == p.lineId }), let idx = index(of: line.page),
              line.words.indices.contains(p.word) else { return nil }
        let w = pageSize(idx).width
        let x = line.words[p.word]
        return (idx, CGRect(x: x.l * w, y: x.t * w, width: (x.r - x.l) * w, height: (x.b - x.t) * w))
    }
}

/// بعد الضغط المطوّل على مكان فارغ: لصق المنسوخ هنا، أو إضافة سريعة في المكان نفسه.
struct PasteBar: View {
    @ObservedObject var session: DocSession
    @ObservedObject var clipboard = PageClipboard.shared
    let point: PastePoint

    var body: some View {
        CtxBar {
            if clipboard.clip != nil {
                CtxBtn(label: L("لصق", "Paste"), primary: true) { session.pasteAt = nil; session.pasteClip(point.page, at: point.at) }
            }
            CtxBtn(label: L("صورة", "Image")) { session.pasteAt = nil; session.trayMode = "photos" }
            CtxBtn(label: L("مربع نص", "Text box")) {
                session.pasteAt = nil
                guard let pid = session.pageId(point.page) else { return }
                let w = session.pageSize(point.page).width
                let id = UUID().uuidString
                session.edit { d in
                    d.boxes.append(TextBox(id: id, page: pid, x: max(0, min(point.at.x - 8, w * 0.6)), y: point.at.y, w: w * 0.4, text: ""))
                }
                session.beginInlineEdit(id)
            }
        }
    }
}
