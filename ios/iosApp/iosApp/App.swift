import SwiftUI
import Shared

/// نسخة iPad من «هامش»: التطبيق كله مكتوب بـ Kotlin + Compose (ios/shared)، وهنا يُعرض ملء الشاشة.
@main
struct HamishApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.all)
                // ملف PDF فُتح بالتطبيق من «الملفات» أو من المشاركة
                .onOpenURL { url in
                    let scoped = url.startAccessingSecurityScopedResource()
                    defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                    IosApp.shared.openFile(path: url.path)
                }
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainActivityKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
