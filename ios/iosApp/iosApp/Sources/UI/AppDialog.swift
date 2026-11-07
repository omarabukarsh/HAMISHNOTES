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
    /// نافذة حوار بشكل نسخة أندرويد. المحتوى يأخذ دالة الإغلاق.
    func appDialog<C: View>(isPresented: Binding<Bool>, @ViewBuilder content: @escaping (_ dismiss: @escaping () -> Void) -> C) -> some View {
        modifier(AppDialogModifier(isPresented: isPresented, content: content))
    }
}
