import UIKit
import Shared

/// نسخة iPad من «هامش»: التطبيق كله مكتوب بـ Kotlin + Compose (ios/shared)، وهنا يُعرض ملء الشاشة.
/// شاشة Compose هي الشاشة الجذرية مباشرة، فتتحكم بلون شريط الحالة حسب الثيم.
@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        let w = UIWindow(frame: UIScreen.main.bounds)
        w.rootViewController = MainActivityKt.MainViewController()
        w.makeKeyAndVisible()
        window = w
        return true
    }

    /// ملف PDF فُتح بالتطبيق من «الملفات» أو من المشاركة
    func application(_ app: UIApplication, open url: URL,
                     options: [UIApplication.OpenURLOptionsKey: Any] = [:]) -> Bool {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        IosApp.shared.openFile(path: url.path)
        return true
    }
}
