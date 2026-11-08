import SwiftUI
import UIKit

/// نسخة iPad من «هامش»: مكتوبة كلها بـ Swift (SwiftUI + PDFKit + PencilKit)،
/// بنفس تصميم تطبيق أندرويد وميزاته.
@main
struct HamishApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    @StateObject private var app = AppController.shared
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(app)
                .environment(\.layoutDirection, .leftToRight)
                .environment(\.locale, Locale(identifier: app.english ? "en" : "ar"))
                .onOpenURL { url in
                    if url.scheme == "hamish" {
                        if url.host == "open-last" { WidgetBridge.openLast() }
                        return
                    }
                    let scoped = url.startAccessingSecurityScopedResource()
                    defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                    // نسخة مؤقتة: الملف المفتوح من تطبيق آخر قد لا يبقى متاحًا
                    let tmp = FileManager.default.temporaryDirectory.appendingPathComponent(url.lastPathComponent)
                    try? FileManager.default.removeItem(at: tmp)
                    if (try? FileManager.default.copyItem(at: url, to: tmp)) != nil { app.openExternal(tmp) } else { app.openExternal(url) }
                }
        }
        .onChange(of: phase) { p in
            app.setVisible(p == .active)
            if p != .active { WidgetBridge.update(force: true) }
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        AppController.shared.initialize()
        _ = Premium.shared
        // تشغيل تجريبي (لقطات المحاكي): -hamishDemo /path/file.pdf -hamishScreen <name>
        Demo.run(args: ProcessInfo.processInfo.arguments)
        return true
    }
}

struct RootView: View {
    @EnvironmentObject var app: AppController
    @ObservedObject var pm = Premium.shared

    var body: some View {
        Group {
            if !app.loginSeen {
                SignInScreen()
            } else {
                main
            }
        }
        .preferredColorScheme(app.theme == .light ? .light : .dark)
    }

    private var main: some View {
        ZStack(alignment: .bottom) {
            VStack(spacing: 0) {
                if !app.tabs.isEmpty && !(app.focusMode && app.activeId != nil) { TabBar() }
                ZStack {
                    if let id = app.activeId, let active = app.tabs.first(where: { $0.id == id }) {
                        if let sid = app.splitId, sid != id, let second = app.tabs.first(where: { $0.id == sid }), !app.focusMode {
                            SplitReaders(first: active, second: second)
                        } else {
                            ReaderScreen(session: active).id(active.id)
                        }
                    } else {
                        LibraryScreen()
                    }
                }
                .transition(.opacity)
                .animation(Motion.enter, value: app.activeId)
            }
            .background((app.tabs.isEmpty ? Lib.bg : Palette.tabRail).ignoresSafeArea())
            .simultaneousGesture(TapGesture().onEnded { app.noteInteraction() })
            if let t = app.toast { ToastView(text: t) }
            PremiumHost()
        }
        .bigSheet(isPresented: $app.settingsOpen) {
            SettingsView().environmentObject(app).environment(\.layoutDirection, .leftToRight)
        }
        .bigSheet(isPresented: $app.statsOpen, width: 0.92, height: 0.92, maxHeight: 800) {
            StatsView().environmentObject(app).environment(\.layoutDirection, .leftToRight)
        }
        .bigSheet(isPresented: Binding(get: { app.openDeck != nil }, set: { if !$0 { app.openDeck = nil } }), width: 0.82, height: 0.85, maxHeight: 700, minHeight: 460, fitContent: true) {
            DeckView(deck: app.openDeck == "*" ? nil : app.openDeck).environmentObject(app).environment(\.layoutDirection, .leftToRight)
        }
        .sheet(item: Binding(get: { app.setPinFor.map { IdString(id: $0) } }, set: { app.setPinFor = $0?.id })) { t in
            SetPinSheet(docId: t.id).environmentObject(app).environment(\.layoutDirection, .leftToRight)
        }
        .sheet(item: $app.pinPrompt) { p in
            PinPromptSheet(prompt: p).environmentObject(app).environment(\.layoutDirection, .leftToRight)
        }
        .onChange(of: app.unlockRequest) { id in
            if let id = id { app.requestUnlock(id) }
        }
    }
}

// ---------------------------------------------------------------- شريط التبويبات

private struct TabBar: View {
    @EnvironmentObject var app: AppController

    var body: some View {
        let libraryActive = app.activeId == nil
        let activeBg = libraryActive ? Palette.spine : Palette.desk
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(alignment: .bottom, spacing: 4) {
                TabItem(title: L("المكتبة", "Library"), active: libraryActive, home: true, activeBg: activeBg, onClose: nil) { app.activate(nil) }
                ForEach(Array(app.tabs.enumerated()), id: \.element.id) { i, s in
                    TabItem(title: s.title, active: app.activeId == s.id, home: false, activeBg: activeBg, onClose: { app.closeTab(s.id) }) { app.activate(s.id) }
                        .contextMenu {
                            if i > 0 { Button(L("تحريك يمينًا", "Move left")) { app.moveTab(from: i, to: i - 1) } }
                            if i < app.tabs.count - 1 { Button(L("تحريك يسارًا", "Move right")) { app.moveTab(from: i, to: i + 1) } }
                            if app.splitId == nil, app.activeId != s.id, app.activeId != nil {
                                Button(L("عرضه بجانب المستند الحالي", "Show beside the current document")) {
                                    if Premium.shared.require(.split) { app.openSplit(s.id) }
                                }
                            }
                            Button(role: .destructive) { app.closeTab(s.id) } label: { Text(L("إغلاق التبويب", "Close tab")) }
                        }
                }
            }
            .padding(.horizontal, 10).padding(.top, 8)
        }
        .background(Palette.tabRail)
    }
}

/// تبويب على شكل لسان ملف بزوايا علوية مستديرة.
private struct TabItem: View {
    @Environment(\.colorScheme) private var themeTick // يعيد الرسم عند تغيير الثيم
    let title: String
    let active: Bool
    let home: Bool
    let activeBg: Color
    let onClose: (() -> Void)?
    let onTap: () -> Void

    var body: some View {
        let bg = active ? activeBg : Palette.tabIdle
        let dark = bg.luminance < 0.4
        let fg = dark ? Color(hex: 0xFFEDE6D4) : Palette.navy
        let muted = dark ? Color(hex: 0xFFB4BFD0) : Palette.textSecondary
        HStack(spacing: 0) {
            if home {
                PhIcon(active ? "ph_books_fill" : "ph_books", active ? Palette.gold : muted, 18)
                Spacer().frame(width: 7)
            } else if active {
                Circle().fill(Palette.gold).frame(width: 6, height: 6)
                Spacer().frame(width: 8)
            }
            Text(title).font(Fonts.sans(14, active ? .bold : .regular)).foregroundColor(active ? fg : muted).lineLimit(1)
                .frame(maxWidth: 180, alignment: .leading).fixedSize(horizontal: true, vertical: false)
            if let close = onClose {
                Spacer().frame(width: 2)
                Button(action: close) { PhIcon("ph_close", muted, 15, L("إغلاق التبويب", "Close tab")).frame(width: 30, height: 30) }
            }
        }
        .padding(.leading, 14).padding(.trailing, onClose == nil ? 16 : 4)
        .frame(height: active ? 40 : 36)
        .background(UnevenRoundedRectangle(topLeadingRadius: 12, bottomLeadingRadius: 0, bottomTrailingRadius: 0, topTrailingRadius: 12, style: .continuous).fill(bg))
        .contentShape(Rectangle())
        .onTapGesture(perform: onTap)
        .animation(Motion.enter, value: active)
    }
}

// ---------------------------------------------------------------- مستندان جنبًا إلى جنب

private struct SplitReaders: View {
    @EnvironmentObject var app: AppController
    let first: DocSession
    let second: DocSession
    @State private var frac: CGFloat = 0.5
    @GestureState private var drag: CGFloat = 0

    var body: some View {
        GeometryReader { g in
            let w = g.size.width
            let f = max(0.25, min(0.75, frac + drag / max(1, w)))
            HStack(spacing: 0) {
                ReaderScreen(session: first).id(first.id).frame(width: w * f - 5)
                ZStack {
                    Palette.tabRail
                    Capsule().fill(Palette.gold).frame(width: 4, height: 44)
                    VStack {
                        Button { app.closeSplit() } label: { PhIcon("ph_close", .white, 12).frame(width: 22, height: 22).background(Circle().fill(Palette.navy)) }
                            .padding(.top, 8)
                        Spacer()
                    }
                }
                .frame(width: 10)
                .gesture(DragGesture().updating($drag) { v, st, _ in st = app.english ? v.translation.width : -v.translation.width }
                    .onEnded { v in frac = max(0.25, min(0.75, frac + (app.english ? v.translation.width : -v.translation.width) / max(1, w))) })
                ReaderScreen(session: second).id(second.id)
            }
        }
    }
}

// ---------------------------------------------------------------- رمز القفل

private struct SetPinSheet: View {
    @EnvironmentObject var app: AppController
    let docId: String
    @State private var pin = ""
    @State private var again = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        SheetScaffold(title: app.hasPin() ? L("تغيير الرمز", "Change PIN") : L("رمز قفل المستندات", "Document lock PIN")) {
            VStack(alignment: .leading, spacing: 12) {
                Text(L("يُستخدم الرمز إن لم ينجح Face ID أو Touch ID.", "The PIN is used if Face ID or Touch ID doesn’t work.")).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary)
                SecureField(L("الرمز (4 أرقام أو أكثر)", "PIN (4+ digits)"), text: $pin).keyboardType(.numberPad).font(Fonts.sans(16))
                    .padding(12).background(RoundedRectangle(cornerRadius: 12).fill(Color.white))
                SecureField(L("أعد كتابة الرمز", "Repeat the PIN"), text: $again).keyboardType(.numberPad).font(Fonts.sans(16))
                    .padding(12).background(RoundedRectangle(cornerRadius: 12).fill(Color.white))
                HStack {
                    Spacer()
                    NavyButton(label: L("إلغاء", "Cancel"), filled: false) { dismiss() }
                    NavyButton(label: L("حفظ", "Save")) {
                        guard pin.count >= 4, pin == again else {
                            app.say(.done, L("الرمزان غير متطابقين أو أقصر من 4 أرقام", "The PINs don’t match or are shorter than 4 digits"))
                            return
                        }
                        app.setPin(pin)
                        dismiss()
                        if !docId.isEmpty { DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { app.toggleLock(docId) } }
                    }
                }
            }
            .padding(.horizontal, 22)
        }
        .presentationDetents([.height(330)])
    }
}

private struct PinPromptSheet: View {
    @EnvironmentObject var app: AppController
    let prompt: AppController.PinPrompt
    @State private var pin = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        SheetScaffold(title: prompt.reason) {
            VStack(alignment: .leading, spacing: 12) {
                SecureField(L("الرمز", "PIN"), text: $pin).keyboardType(.numberPad).font(Fonts.sans(18))
                    .padding(12).background(RoundedRectangle(cornerRadius: 12).fill(Color.white))
                    .onSubmit(check)
                HStack {
                    Spacer()
                    NavyButton(label: L("إلغاء", "Cancel"), filled: false) { prompt.done(false); dismiss() }
                    NavyButton(label: L("فتح", "Unlock"), action: check)
                }
            }
            .padding(.horizontal, 22)
        }
        .presentationDetents([.height(230)])
    }

    private func check() {
        if app.checkPin(pin) {
            dismiss()
            prompt.done(true)
        } else {
            pin = ""
            app.say(.done, L("الرمز غير صحيح", "Wrong PIN"))
        }
    }
}
