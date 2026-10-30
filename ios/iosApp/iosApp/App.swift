import SwiftUI
import UIKit
import Shared

/// نسخة iPad من «هامش»: التطبيق كله مكتوب بـ Kotlin + Compose (ios/shared)، وهنا يُعرض ملء الشاشة.
/// أسماء SwiftUI مكتوبة كاملة لأن إطار Kotlin يصدّر أصنافًا بأسماء مثل Context و Color.
@main
struct HamishApp: SwiftUI.App {
    var body: some SwiftUI.Scene {
        SwiftUI.WindowGroup {
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

struct ComposeView: SwiftUI.UIViewControllerRepresentable {
    typealias UIViewControllerType = UIKit.UIViewController

    func makeUIViewController(context: SwiftUI.UIViewControllerRepresentableContext<ComposeView>) -> UIKit.UIViewController {
        MainActivityKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIKit.UIViewController, context: SwiftUI.UIViewControllerRepresentableContext<ComposeView>) {}
}
