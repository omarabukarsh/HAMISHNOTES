import SwiftUI

// نافذة حوار بنفس شكل AlertDialog في نسخة أندرويد: بطاقة عاجية بحواف دائرية (24)،
// عنوان بخط العرض، نص رمادي، وأزرار نصية في الأسفل على اليمين. تظهر في وسط الشاشة فوق خلفية معتمة.

struct AlertDialogCard<Content: View, Buttons: View>: View {
    let title: String
    var width: CGFloat = 520
    @ViewBuilder let content: () -> Content
    @ViewBuilder let buttons: () -> Buttons

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(title).font(Fonts.display(22)).foregroundColor(Palette.textPrimary)
                .frame(maxWidth: .infinity, alignment: .leading)
            content()
                .font(Fonts.sans(14)).foregroundColor(Palette.textSecondary)
            HStack(spacing: 8) {
                Spacer(minLength: 0)
                buttons()
            }
        }
        .padding(24)
        .frame(width: width)
        .background(RoundedRectangle(cornerRadius: 24, style: .continuous).fill(Palette.ivory))
        .shadow(color: .black.opacity(0.22), radius: 24, y: 10)
    }
}

/// زر نصي في نافذة الحوار (مثل TextButton في أندرويد).
struct DialogButton: View {
    let label: String
    var color: Color = Palette.navy
    var bold = false
    var enabled = true
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label).font(Fonts.sans(14, bold ? .bold : .medium)).foregroundColor(color.opacity(enabled ? 1 : 0.38))
                .padding(.horizontal, 12).padding(.vertical, 10)
                .contentShape(Rectangle())
        }
        .buttonStyle(PressScale())
        .disabled(!enabled)
    }
}

private struct DialogHost<C: View>: View {
    let onDismiss: () -> Void
    let content: (@escaping () -> Void) -> C
    @State private var shown = false

    var body: some View {
        ZStack {
            Color.black.opacity(shown ? 0.32 : 0).ignoresSafeArea()
                .onTapGesture { close() }
            content(close)
                .scaleEffect(shown ? 1 : 0.94)
                .opacity(shown ? 1 : 0)
                .padding(24)
        }
        .environment(\.layoutDirection, .leftToRight)
        .onAppear { withAnimation(Motion.enter) { shown = true } }
    }

    private func close() {
        withAnimation(.easeOut(duration: 0.14)) { shown = false }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.14) { onDismiss() }
    }
}

private struct AppDialogModifier<C: View>: ViewModifier {
    @Binding var isPresented: Bool
    let content: (@escaping () -> Void) -> C
    @State private var cover = false

    func body(content base: Content) -> some View {
        base
            .fullScreenCover(isPresented: $cover) {
                DialogHost(onDismiss: { isPresented = false }, content: content)
                    .environmentObject(AppController.shared)
                    .presentationBackground(.clear)
            }
            .onChange(of: isPresented) { _, v in show(v) }
            // أُغلقت من داخلها (dismiss): تُصفَّر الحالة الخارجية أيضًا
            .onChange(of: cover) { _, v in if !v && isPresented { isPresented = false } }
            .onAppear { if isPresented { show(true) } }
    }

    /// النافذة تظهر وتختفي بحركتها الخاصة (تلاشٍ وتكبير)، لا بانزلاق النوافذ الكاملة.
    private func show(_ v: Bool) {
        var t = Transaction()
        t.disablesAnimations = true
        withTransaction(t) { cover = v }
    }
}

extension View {
    /// نافذة حوار في وسط الشاشة لمحتوى يُغلق نفسه بـ dismiss (مثل NameSheet).
    func dialogSheet<C: View>(isPresented: Binding<Bool>, @ViewBuilder content: @escaping () -> C) -> some View {
        appDialog(isPresented: isPresented) { _ in content() }
    }

    /// نافذة حوار لعنصر محدد.
    func dialogSheet<T: Identifiable, C: View>(item: Binding<T?>, @ViewBuilder content: @escaping (T) -> C) -> some View {
        appDialog(isPresented: Binding(get: { item.wrappedValue != nil }, set: { if !$0 { item.wrappedValue = nil } })) { _ in
            if let t = item.wrappedValue { content(t) }
        }
    }

    /// نافذة حوار بشكل نسخة أندرويد. المحتوى يأخذ دالة الإغلاق.
    func appDialog<C: View>(isPresented: Binding<Bool>, @ViewBuilder content: @escaping (_ dismiss: @escaping () -> Void) -> C) -> some View {
        modifier(AppDialogModifier(isPresented: isPresented, content: content))
    }
}

/// حقل نص بإطار وعنوان يطفو فوقه عند الكتابة (مثل OutlinedTextField في أندرويد).
struct OutlinedField: View {
    let label: String
    @Binding var text: String
    var autoFocus = true
    @FocusState private var focused: Bool

    var body: some View {
        let up = focused || !text.isEmpty
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 4, style: .continuous)
                .stroke(focused ? Palette.navy : Palette.textSecondary.opacity(0.6), lineWidth: focused ? 2 : 1)
                .frame(height: 56)
                .padding(.top, 8)
            TextField("", text: $text)
                .font(Fonts.sans(16)).foregroundColor(Palette.textPrimary)
                .focused($focused)
                .padding(.horizontal, 16)
                .frame(height: 56)
                .padding(.top, 8)
            Text(label)
                .font(Fonts.sans(up ? 12 : 16))
                .foregroundColor(focused ? Palette.navy : Palette.textSecondary)
                .padding(.horizontal, up ? 4 : 0)
                .background(up ? Palette.ivory : Color.clear)
                .offset(x: up ? 12 : 16, y: up ? 0 : 26)
                .allowsHitTesting(false)
                .animation(.easeOut(duration: 0.15), value: up)
        }
        .frame(height: 64)
        .onAppear { if autoFocus { DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) { focused = true } } }
    }
}
