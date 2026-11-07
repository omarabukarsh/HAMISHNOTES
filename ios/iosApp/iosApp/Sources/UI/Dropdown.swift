import SwiftUI

// قائمة منسدلة تحت الزر مباشرة (مثل DropdownMenu في أندرويد): بطاقة بيضاء بحواف دائرية وظل،
// تُرسم فوق الشاشة كلها من مضيف واحد (dropdownHost)، فتعمل داخل الأشرطة القابلة للتمرير
// والمصغّرة دون مشاكل النوافذ المنبثقة. نقرة خارجها تغلقها.

struct DropdownItem {
    let anchor: Anchor<CGRect>
    let width: CGFloat?
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
    let panel: () -> C

    func body(content: Content) -> some View {
        let shown = isPresented
        let b = $isPresented
        let make = panel
        return content.anchorPreference(key: DropdownKey.self, value: .bounds) { a in
            shown ? [DropdownItem(anchor: a, width: width, content: { AnyView(make()) }, dismiss: { b.wrappedValue = false })] : []
        }
    }
}

extension View {
    /// قائمة تنسدل تحت هذا الزر ما دام isPresented صحيحًا.
    func dropdown<C: View>(isPresented: Binding<Bool>, width: CGFloat? = nil, @ViewBuilder content: @escaping () -> C) -> some View {
        modifier(DropdownModifier(isPresented: isPresented, width: width, panel: content))
    }

    /// يرسم القوائم المنسدلة لكل ما بداخله.
    func dropdownHost() -> some View {
        overlayPreferenceValue(DropdownKey.self) { items in
            GeometryReader { geo in
                if let it = items.last {
                    DropdownPanel(item: it, rect: geo[it.anchor], area: geo.size)
                }
            }
        }
    }
}

private struct DropdownPanel: View {
    let item: DropdownItem
    let rect: CGRect
    let area: CGSize
    @State private var size: CGSize = .zero

    var body: some View {
        let top = rect.maxY + 6
        let below = area.height - top - 12
        // لا مكان تحت الزر: تنفتح فوقه
        let up = below < min(size.height, 260) && rect.minY > below
        let maxH = max(120, up ? rect.minY - 18 : below)
        let w = size.width
        let x = min(max(8, rect.midX - w / 2), max(8, area.width - w - 8))
        let h = min(size.height, maxH)
        let y = up ? rect.minY - 6 - h : top
        ZStack(alignment: .topLeading) {
            Color.black.opacity(0.001)
                .contentShape(Rectangle())
                .onTapGesture { item.dismiss() }
            ScrollView(.vertical, showsIndicators: false) {
                item.content()
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
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(Color.white))
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(Palette.divider.opacity(0.6), lineWidth: 0.5))
            .shadow(color: .black.opacity(0.16), radius: 14, y: 6)
            .offset(x: x, y: y)
            .opacity(size == .zero ? 0 : 1)
            .environment(\.layoutDirection, .leftToRight)
        }
        .frame(width: area.width, height: area.height, alignment: .topLeading)
        .transition(.opacity)
    }
}
