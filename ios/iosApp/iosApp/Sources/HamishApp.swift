import SwiftUI
import UIKit

/// نسخة iPad من «هامش»: مكتوبة كلها بـ Swift (SwiftUI + PDFKit، ومحرك حبر خاص)،
/// بنفس تصميم تطبيق أندرويد وميزاته.
@main
struct HamishApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    @StateObject private var app = AppController.shared
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup {
            RootView()
                // يسجّل مكان آخر لمسة، فتخرج النوافذ والقوائم من الزر الذي فُتحت منه
                .background(TouchOriginInstaller())
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
        .onChange(of: phase) { _, p in
            app.setVisible(p == .active)
            if p != .active { WidgetBridge.update(force: true) }
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        // الممحاة «الجزء الملموس» تقص الحروف المملوءة بعمليات المسارات
        InkCut.install()
        AppController.shared.initialize()
        _ = Premium.shared
        // تشغيل تجريبي (لقطات المحاكي): -hamishDemo /path/file.pdf -hamishScreen <name>
        Demo.run(args: ProcessInfo.processInfo.arguments)
        return true
    }
}

struct RootView: View {
    /// التنقل بين التبويبات: القديم يختفي بسرعة ثم يظهر الجديد، فلا يتداخل نصّا المستندين.
    static let tabSwitch = AnyTransition.asymmetric(
        insertion: .opacity.animation(.easeOut(duration: 0.16).delay(0.09)),
        removal: .opacity.animation(.easeIn(duration: 0.09))
    )
    @EnvironmentObject var app: AppController
    @ObservedObject var pm = Premium.shared
    /// مظهر الجهاز (حين يكون المظهر «تلقائي» لا نفرض لونًا، فيصل هنا مظهر الجهاز نفسه).
    @Environment(\.colorScheme) private var deviceScheme

    var body: some View {
        Group {
            if !app.loginSeen {
                SignInScreen()
            } else {
                main
            }
        }
        .preferredColorScheme(app.themeFollowsSystem ? nil : (app.theme == .light ? .light : .dark))
        // «تلقائي»: يتبع مظهر الجهاز ويتغير وحده معه
        .onAppear { if app.themeFollowsSystem { app.applySystemTheme(dark: deviceScheme == .dark) } }
        .onChange(of: deviceScheme) { _, s in if app.themeFollowsSystem { app.applySystemTheme(dark: s == .dark) } }
        .onChange(of: app.themeFollowsSystem) { _, on in if on { app.applySystemTheme(dark: deviceScheme == .dark) } }
    }

    private var main: some View {
        ZStack(alignment: .bottom) {
            ImportProgressOverlay()
                .zIndex(10)
            VStack(spacing: 0) {
                if !app.tabs.isEmpty && !(app.focusMode && app.activeId != nil) { TabBar() }
                ZStack {
                    if let id = app.activeId, let active = app.tabs.first(where: { $0.id == id }) {
                        if let sid = app.splitId, sid != id, let second = app.tabs.first(where: { $0.id == sid }), !app.focusMode {
                            SplitReaders(first: active, second: second)
                        } else {
                            ReaderScreen(session: active).id(active.id).transition(Self.tabSwitch)
                        }
                    } else {
                        LibraryScreen().transition(Self.tabSwitch)
                    }
                }
                .animation(.easeOut(duration: 0.16), value: app.activeId)
            }
            .background((app.tabs.isEmpty ? Lib.bg : Palette.tabRail).ignoresSafeArea())
            .simultaneousGesture(TapGesture().onEnded { app.noteInteraction() })
            if let t = app.toast { ToastView(text: t, action: app.toastAction) }
            PremiumHost()
        }
        .bigSheet(isPresented: $app.settingsOpen) {
            SettingsView().environmentObject(app).environment(\.layoutDirection, .leftToRight)
        }
        .bigSheet(isPresented: $app.statsOpen, width: 0.92, height: 0.92, maxHeight: 800, maxWidth: 900) {
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
        .onChange(of: app.unlockRequest) { _, id in
            if let id = id { app.requestUnlock(id) }
        }
    }
}

// ---------------------------------------------------------------- شريط التبويبات

private struct TabBar: View {
    @EnvironmentObject var app: AppController
    @State private var avail: CGFloat = 800
    @State private var content: CGFloat = 0

    var body: some View {
        let libraryActive = app.activeId == nil
        // في المستند: التبويب النشط بلون بطاقة الأدوات (أبيض) ويتصل بها، والشريط بلون مكتب القراءة
        let activeBg = libraryActive ? Palette.spine : Palette.chromeBg
        let railBg = libraryActive ? Palette.tabRail : Palette.chromeRail
        // «المكتبة» ثابتة في البداية؛ تبويبات المستندات بعدها تتقاسم العرض (حتى 240)،
        // وتصغر حتى 170 ثم يثبت عرضها ويُسحب الباقي لإظهاره
        let n = app.tabs.count
        let tabW = n == 0 ? 0 : min(240, max(170, (avail - CGFloat(n)) / CGFloat(n)))
        HStack(alignment: .bottom, spacing: 0) {
            TabItem(title: L("المكتبة", "Library"), active: libraryActive, home: true, activeBg: activeBg, onClose: nil) { app.activate(nil) }
            ScrollViewReader { proxy in
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(alignment: .bottom, spacing: 0) {
                        ForEach(Array(app.tabs.enumerated()), id: \.element.id) { i, s in
                            let prevActive = i == 0 ? libraryActive : app.activeId == app.tabs[i - 1].id
                            if !prevActive && app.activeId != s.id { TabSeparator() }
                            TabItem(title: s.title, active: app.activeId == s.id, home: false, activeBg: activeBg, fixedWidth: tabW, onClose: { app.closeTab(s.id) }) { app.activate(s.id) }
                                .id(s.id)
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
                    .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { content = $0 }
                }
                .scrollBounceBehavior(.basedOnSize)
                // التبويب النشط يظهر دائمًا (عند فتحه أو التنقل إليه)
                .onChange(of: app.activeId) { _, id in
                    if let id { withAnimation(.easeOut(duration: 0.25)) { proxy.scrollTo(id) } }
                }
                // تلاشٍ على الطرف إن كانت هناك تبويبات مخفية
                .mask(HStack(spacing: 0) {
                    LinearGradient(colors: [.black.opacity(content > avail + 1 ? 0 : 1), .black], startPoint: .leading, endPoint: .trailing).frame(width: 30)
                    Color.black
                    LinearGradient(colors: [.black, .black.opacity(content > avail + 1 ? 0 : 1)], startPoint: .leading, endPoint: .trailing).frame(width: 30)
                })
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { avail = $0 }
        }
        .padding(.horizontal, 26).padding(.top, 8)
        .background(railBg)
    }
}

/// فاصل رفيع بين تبويبين غير نشطين.
private struct TabSeparator: View {
    var body: some View {
        Rectangle().fill(Palette.textSecondary.opacity(0.25)).frame(width: 1, height: 16).padding(.bottom, 12)
    }
}

/// زاويتان مقعّرتان أسفل التبويب النشط فينساب في البطاقة التي تحته بلا زوايا حادة.
private struct TabFillets: Shape {
    let r: CGFloat
    func path(in rect: CGRect) -> Path {
        var p = Path()
        let h = rect.maxY
        // اليسار
        p.move(to: CGPoint(x: rect.minX, y: h - r))
        p.addLine(to: CGPoint(x: rect.minX, y: h))
        p.addLine(to: CGPoint(x: rect.minX - r, y: h))
        p.addArc(center: CGPoint(x: rect.minX - r, y: h - r), radius: r, startAngle: .degrees(90), endAngle: .degrees(0), clockwise: true)
        p.closeSubpath()
        // اليمين
        p.move(to: CGPoint(x: rect.maxX, y: h - r))
        p.addLine(to: CGPoint(x: rect.maxX, y: h))
        p.addLine(to: CGPoint(x: rect.maxX + r, y: h))
        p.addArc(center: CGPoint(x: rect.maxX + r, y: h - r), radius: r, startAngle: .degrees(90), endAngle: .degrees(180), clockwise: false)
        p.closeSubpath()
        return p
    }
}

/// تبويب كتبويبات المستندات: غير النشط نص فقط بلا خلفية، والنشط بلون ما تحته بزوايا علوية مستديرة.
private struct TabItem: View {
    @Environment(\.colorScheme) private var themeTick // يعيد الرسم عند تغيير الثيم
    let title: String
    let active: Bool
    let home: Bool
    let activeBg: Color
    var fixedWidth: CGFloat? = nil
    let onClose: (() -> Void)?
    let onTap: () -> Void

    var body: some View {
        let under = active ? activeBg : (activeBg == Palette.spine ? Palette.tabRail : Palette.chromeRail)
        let dark = under.luminance < 0.4
        let fg = dark ? Color(hex: 0xFFEDE6D4) : Palette.navy
        let muted = dark ? Color(hex: 0xFFB4BFD0) : Palette.textSecondary
        Button(action: onTap) {
            HStack(spacing: 0) {
                if home {
                    PhIcon(active ? "ph_books_fill" : "ph_books", active ? Palette.gold : muted, 18)
                    Spacer().frame(width: 7)
                } else if active {
                    Circle().fill(Palette.gold).frame(width: 6, height: 6)
                    Spacer().frame(width: 8)
                }
                if fixedWidth != nil {
                    Text(title).font(Fonts.sans(14, active ? .bold : .regular)).foregroundColor(active ? fg : muted).lineLimit(1)
                        .truncationMode(.tail).frame(maxWidth: .infinity, alignment: .leading)
                } else {
                    Text(title).font(Fonts.sans(14, active ? .bold : .regular)).foregroundColor(active ? fg : muted).lineLimit(1)
                        .frame(maxWidth: 200, alignment: .leading).fixedSize(horizontal: true, vertical: false)
                }
                // مكان زر الإغلاق (يوضع فوقه خارج زر التبويب)
                if onClose != nil { Spacer().frame(width: 32) }
            }
            .padding(.leading, 14).padding(.trailing, onClose == nil ? 16 : 4)
            .frame(width: fixedWidth, height: 40)
            .contentShape(Rectangle())
        }
        // أثر النقر: إضاءة ناعمة داخل شكل التبويب تتلاشى (بدل حدود حادة)
        .buttonStyle(TabPressStyle(tint: fg))
        .overlay(alignment: .trailing) {
            if let close = onClose {
                Button(action: close) { PhIcon("ph_close", muted, 15, L("إغلاق التبويب", "Close tab")).frame(width: 30, height: 30) }
                    .padding(.trailing, 4)
            }
        }
        .background(
            ZStack {
                if active {
                    TabFillets(r: 12).fill(activeBg)
                    UnevenRoundedRectangle(topLeadingRadius: 12, bottomLeadingRadius: 0, bottomTrailingRadius: 0, topTrailingRadius: 12, style: .continuous).fill(activeBg)
                }
            }
        )
        .animation(Motion.enter, value: active)
    }
}

/// ضغط التبويب: إضاءة خفيفة بزوايا مستديرة داخله، تظهر بسرعة وتتلاشى بهدوء.
private struct TabPressStyle: ButtonStyle {
    let tint: Color
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(tint.opacity(configuration.isPressed ? 0.10 : 0))
                    .padding(3)
                    .animation(.easeOut(duration: configuration.isPressed ? 0.09 : 0.22), value: configuration.isPressed)
            )
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
                    .padding(12).background(RoundedRectangle(cornerRadius: 12).fill(Palette.surface))
                SecureField(L("أعد كتابة الرمز", "Repeat the PIN"), text: $again).keyboardType(.numberPad).font(Fonts.sans(16))
                    .padding(12).background(RoundedRectangle(cornerRadius: 12).fill(Palette.surface))
                HStack {
                    Spacer()
                    NavyButton(label: L("إلغاء", "Cancel"), filled: false) { dismiss() }
                    NavyButton(label: L("حفظ", "Save")) {
                        guard pin.count >= 4, pin == again else {
                            app.say(.error, L("الرمزان غير متطابقين أو أقصر من 4 أرقام", "The PINs don’t match or are shorter than 4 digits"))
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
    @State private var answered = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        SheetScaffold(title: prompt.reason) {
            VStack(alignment: .leading, spacing: 12) {
                SecureField(L("الرمز", "PIN"), text: $pin).keyboardType(.numberPad).font(Fonts.sans(18))
                    .padding(12).background(RoundedRectangle(cornerRadius: 12).fill(Palette.surface))
                    .onSubmit(check)
                HStack {
                    Spacer()
                    NavyButton(label: L("إلغاء", "Cancel"), filled: false) { answered = true; prompt.done(false); dismiss() }
                    NavyButton(label: L("فتح", "Unlock"), action: check)
                }
            }
            .padding(.horizontal, 22)
        }
        .presentationDetents([.height(230)])
        // إغلاق النافذة بالسحب = إلغاء، فلا يبقى طلب الفتح معلّقًا
        .onDisappear { if !answered { answered = true; prompt.done(false) } }
    }

    private func check() {
        if app.checkPin(pin) {
            answered = true
            dismiss()
            prompt.done(true)
        } else {
            pin = ""
            app.say(.error, L("الرمز غير صحيح", "Wrong PIN"))
        }
    }
}


/// نافذة إضافة ملف: الاسم والنسبة والمرحلة وزر الإلغاء؛ التطبيق يبقى يستجيب أثناءها.
private struct ImportProgressOverlay: View {
    @EnvironmentObject var app: AppController
    var body: some View {
        if let p = app.importProgress {
            ZStack {
                Color.black.opacity(0.25).ignoresSafeArea()
                VStack(spacing: 0) {
                    ProgressView().controlSize(.large).tint(Palette.navy)
                    Text(p.title ?? (p.count > 1 ? L("جارٍ إضافة الملفات (\(p.index + 1) من \(p.count))…", "Adding files (\(p.index + 1) of \(p.count))…")
                                     : L("جارٍ إضافة الملف…", "Adding the file…")))
                        .font(Fonts.sans(17, .bold)).foregroundColor(Palette.textPrimary).padding(.top, 12)
                    Text(p.name).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary).lineLimit(1).padding(.top, 4)
                    ProgressView(value: p.fraction).tint(Palette.navy).padding(.top, 14)
                        .animation(.easeOut(duration: 0.2), value: p.fraction)
                    HStack {
                        Text("\(Int(p.fraction * 100))%")
                        Spacer()
                        Text(p.phase)
                    }
                    .font(Fonts.sans(12.5)).foregroundColor(Palette.textSecondary).padding(.top, 8)
                    Button { app.importCancel = true } label: {
                        Text(L("إلغاء", "Cancel")).font(Fonts.sans(14)).foregroundColor(Palette.danger).padding(.vertical, 8).padding(.horizontal, 16)
                    }
                    .padding(.top, 8)
                }
                .padding(22)
                .frame(width: 360)
                .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(Palette.surface))
                .shadow(color: .black.opacity(0.25), radius: 20, y: 8)
            }
            .transition(.opacity)
        }
    }
}
