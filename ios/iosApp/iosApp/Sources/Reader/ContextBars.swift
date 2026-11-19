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
    var body: some View {
        HStack(spacing: 4) { content() }
            .padding(4)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Palette.chromeBg)
                    .shadow(color: .black.opacity(0.18), radius: 8, y: 3)
            )
            .fixedSize()
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
    @ViewBuilder let bar: () -> Bar
    @State private var size: CGSize = .zero

    init(session: DocSession, anchor: BarAnchor, preferBelow: Bool = false, @ViewBuilder bar: @escaping () -> Bar) {
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
                ZStack(alignment: .topLeading) {
                    bar()
                        .onGeometryChange(for: CGSize.self) { $0.size } action: { size = $0 }
                        .offset(x: spot.x, y: spot.y)
                    BarArrow(down: spot.down).fill(Palette.chromeBg)
                        .frame(width: 16, height: 8)
                        .offset(x: spot.ax, y: spot.ay)
                        .allowsHitTesting(false)
                }
            }
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
