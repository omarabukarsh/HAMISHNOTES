import SwiftUI

// قائمة منسدلة تحت الزر مباشرة (مثل DropdownMenu في أندرويد): بطاقة بيضاء بحواف دائرية وظل،
// تُرسم فوق الشاشة كلها من مضيف واحد (dropdownHost)، فتعمل داخل الأشرطة القابلة للتمرير
// والمصغّرة دون مشاكل النوافذ المنبثقة. نقرة خارجها تغلقها.

struct DropdownItem {
    let anchor: Anchor<CGRect>
    let width: CGFloat?
    /// نوافذ شريط الأدوات: تخرج من فتحة في حافة الزر وتنثني عائدة إليها (EdgeMenu في أندرويد).
    let fromEdge: Bool
    let content: () -> AnyView
    let dismiss: () -> Void
}

struct DropdownKey: PreferenceKey {
    static var defaultValue: [DropdownItem] = []
    static func reduce(value: inout [DropdownItem], nextValue: () -> [DropdownItem]) { value += nextValue() }
}

private struct DropdownModifier<C: View>: ViewModifier {
    @Binding var isPresented: Bool
    let width: CGFloat?
    let fromEdge: Bool
    let panel: () -> C

    func body(content: Content) -> some View {
        let shown = isPresented
        let b = $isPresented
        let make = panel
        return content.anchorPreference(key: DropdownKey.self, value: .bounds) { a in
            shown ? [DropdownItem(anchor: a, width: width, fromEdge: fromEdge, content: { AnyView(make()) }, dismiss: { b.wrappedValue = false })] : []
        }
    }
}

extension View {
    /// قائمة تنسدل تحت هذا الزر ما دام isPresented صحيحًا.
    /// fromEdge: نافذة شريط أدوات تخرج من فتحة في حافة الزر وتنثني عائدة إليها.
    func dropdown<C: View>(isPresented: Binding<Bool>, width: CGFloat? = nil, fromEdge: Bool = false, @ViewBuilder content: @escaping () -> C) -> some View {
        modifier(DropdownModifier(isPresented: isPresented, width: width, fromEdge: fromEdge, panel: content))
    }

    /// يرسم القوائم المنسدلة لكل ما بداخله.
    func dropdownHost() -> some View {
        overlayPreferenceValue(DropdownKey.self) { items in
            GeometryReader { geo in
                DropdownLayer(item: items.last, rect: items.last.map { geo[$0.anchor] }, area: geo.size)
            }
        }
    }
}

/// يُبقي القائمة حيّة أثناء حركة إغلاقها: تخرج من أيقونتها بنابض وتنكمش إليها (DropdownMenu في أندرويد).
private struct DropdownLayer: View {
    let item: DropdownItem?
    let rect: CGRect?
    let area: CGSize
    @State private var kept: DropdownItem? = nil
    @State private var keptRect: CGRect = .zero
    @State private var p: CGFloat = 0
    /// تقدّم الانثناء (نوافذ الشريط فقط).
    @State private var c: CGFloat = 0
    @State private var gen = 0

    var body: some View {
        ZStack {
            if let it = item ?? kept {
                DropdownPanel(item: it, rect: rect ?? keptRect, area: area, p: p, c: c)
                    .allowsHitTesting(item != nil)
            }
        }
        .onAppear { if item != nil { opened() } }
        .onChange(of: item != nil) { _, v in if v { opened() } else { closed() } }
        .onChange(of: rect) { _, v in if let v { keptRect = v } }
    }

    /// تخرج من مركز الأيقونة: 25% ← 100% بنابض grow.
    private func opened() {
        gen += 1
        kept = item
        keptRect = rect ?? .zero
        var still = Transaction()
        still.disablesAnimations = true
        withTransaction(still) { c = 0 }
        if item?.fromEdge == true {
            guard p < 1 else { return }
            withTransaction(still) { p = 0 }
            // الإطار الأول يبني النافذة ويقيسها، ثم تبدأ الحركة: بناء المحتوى لا يأكل أول إطاراتها
            let g = gen
            DispatchQueue.main.async { if g == gen { withAnimation(EdgeMotion.open) { p = 1 } } }
        } else {
            withAnimation(Motion.grow) { p = 1 }
        }
    }

    /// تنكمش إلى الأيقونة في 160ms ثم تُزال.
    private func closed() {
        let g = gen
        if kept?.fromEdge == true {
            // تنثني إلى الفتحة ثم تُزال
            withAnimation(EdgeMotion.close) { c = 1 }
            DispatchQueue.main.asyncAfter(deadline: .now() + EdgeMotion.closeDuration) {
                guard g == gen else { return }
                kept = nil
                var still = Transaction()
                still.disablesAnimations = true
                withTransaction(still) { p = 0; c = 0 }
            }
            return
        }
        withAnimation(Motion.acc(0.16)) { p = 0 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.16) { if g == gen { kept = nil } }
    }
}

private struct DropdownPanel: View {
    let item: DropdownItem
    let rect: CGRect
    let area: CGSize
    let p: CGFloat
    var c: CGFloat = 0
    @State private var size: CGSize = .zero

    var body: some View {
        let edge = item.fromEdge
        // نوافذ الشريط تخرج من حافة الزر نفسها، والقوائم الأخرى تحته بمسافة صغيرة
        let top = rect.maxY + (edge ? 0 : 6)
        let below = area.height - top - 12
        // لا مكان تحت الزر: تنفتح فوقه (إلا نوافذ الشريط: دائمًا تحته، ويُمرَّر محتواها إن طال)
        let up = !edge && below < min(size.height, 260) && rect.minY > below
        let maxH = max(120, up ? rect.minY - 18 : below)
        let w = size.width
        let x = min(max(8, rect.midX - w / 2), max(8, area.width - w - 8))
        let h = min(size.height, maxH)
        let y = up ? rect.minY - 6 - h : top
        // نقطة الانطلاق: مركز الأيقونة نسبةً إلى القائمة
        let anchor = (w > 0 && h > 0) ? UnitPoint(x: (rect.midX - x) / w, y: (rect.midY - y) / h) : .top
        ZStack(alignment: .topLeading) {
            Color.black.opacity(0.001)
                .contentShape(Rectangle())
                .onTapGesture { item.dismiss() }
            ScrollView(.vertical, showsIndicators: false) {
                item.content()
                    .modifier(EdgeContentFade(p: edge ? p : 1, c: edge ? c : 0))
                    .frame(width: item.width)
                    .fixedSize(horizontal: item.width == nil, vertical: true)
                    .background(GeometryReader { g in
                        Color.clear
                            .onAppear { size = g.size }
                            .onChange(of: g.size) { _, v in size = v }
                    })
            }
            .scrollDisabled(size.height <= maxH)
            .frame(width: w > 0 ? w : nil, height: size.height > 0 ? h : nil)
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(Palette.surface))
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(Palette.divider.opacity(0.6), lineWidth: 0.5))
            .modifier(PanelMotion(edge: edge, p: p, c: c, anchor: anchor, slitX: rect.midX - x, slitWidth: rect.width * edgeSlitRatio))
            .offset(x: x, y: y)
            .opacity(size == .zero ? 0 : 1)
            .environment(\.layoutDirection, .leftToRight)
        }
        .frame(width: area.width, height: area.height, alignment: .topLeading)
    }
}

/// حركة القائمة: نوافذ الشريط تخرج من فتحة في حافة الزر وتنثني إليها (مع ظلها في آخر الفتح)،
/// والقوائم الأخرى تكبر من مركز أيقونتها كما كانت.
private struct PanelMotion: ViewModifier {
    let edge: Bool
    let p: CGFloat
    let c: CGFloat
    let anchor: UnitPoint
    let slitX: CGFloat
    let slitWidth: CGFloat

    @ViewBuilder
    func body(content: Content) -> some View {
        if edge {
            content.modifier(EdgeFold(p: p, c: c, localX: slitX, slitWidth: slitWidth))
        } else {
            content
                .shadow(color: .black.opacity(0.16), radius: 14, y: 6)
                .modifier(DropReveal(p: p, anchor: anchor))
        }
    }
}
