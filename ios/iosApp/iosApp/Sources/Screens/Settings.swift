import SwiftUI

enum SettingsPage: String, CaseIterable, Identifiable {
    case account, general, pen, highlights, study, appearance, privacy, about
    var id: String { rawValue }

    var title: String {
        switch self {
        case .account: return L("الحساب", "Account")
        case .general: return L("عام", "General")
        case .pen: return L("القلم والإيماءات", "Pen & gestures")
        case .highlights: return L("التمييز والملخص", "Highlights & summary")
        case .study: return L("المذاكرة", "Study")
        case .appearance: return L("المظهر", "Appearance")
        case .privacy: return L("الخصوصية والأمان", "Privacy & security")
        case .about: return L("حول", "About")
        }
    }

    var lead: String {
        switch self {
        case .account: return L("حسابك المرتبط بهامش واشتراكك.", "Your account linked to Hamish and your subscription.")
        case .general: return L("اللغة والمستندات وطريقة فتح التطبيق.", "Language, documents and how the app opens.")
        case .pen: return L("Apple Pencil، والكتابة بالقلم أو الإصبع، وشريط الأدوات.", "Apple Pencil, pen or finger writing, and the toolbar.")
        case .highlights: return L("كيف يعمل الهايلايتر والملخصات.", "How the highlighter and summaries work.")
        case .study: return L("مؤقت الدراسة والتذكير اليومي.", "Study timer and your daily reminder.")
        case .appearance: return L("اجعل هامش يبدو كما تحب.", "Make Hamish look and feel the way you like.")
        case .privacy: return L("قفل المستندات وما يحفظه التطبيق.", "Document lock and what the app stores.")
        case .about: return L("الإصدار والتراخيص والشروط.", "Version, licenses and terms.")
        }
    }

    var icon: String {
        switch self {
        case .account: return "ph_user"
        case .general: return "ph_sliders"
        case .pen: return "ph_pen"
        case .highlights: return "ph_highlighter"
        case .study: return "ph_timer"
        case .appearance: return "ph_palette"
        case .privacy: return "ph_lock"
        case .about: return "ph_info"
        }
    }

    var color: Color {
        switch self {
        case .account: return Palette.navy
        case .general: return Color(hex: 0xFF6B7A90)
        case .pen: return Color(hex: 0xFF2F6FDB)
        case .highlights: return Color(hex: 0xFFD9A21B)
        case .study: return Color(hex: 0xFF2E9D6B)
        case .appearance: return Color(hex: 0xFF8B5CF6)
        case .privacy: return Color(hex: 0xFFB0413E)
        case .about: return Color(hex: 0xFF667085)
        }
    }
}

private let TileGray = Color(hex: 0xFF6B7A90)
private let TileBlue = Color(hex: 0xFF2F6FDB)
private let TileGold = Color(hex: 0xFFD9A21B)
private let TileGreen = Color(hex: 0xFF2E9D6B)
private let TilePurple = Color(hex: 0xFF8B5CF6)
private let TileRed = Color(hex: 0xFFB0413E)
private let TileSlate = Color(hex: 0xFF667085)

struct SettingsView: View {
    @EnvironmentObject var app: AppController
    @State private var page: SettingsPage? = .general
    @State private var doc: LegalDoc? = nil
    @State private var toolbarOpen = false
    @State private var templateOpen = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        GeometryReader { g in
            let wide = g.size.width >= 600
            Group {
                if wide {
                    HStack(spacing: 0) {
                        sidebar(selected: page ?? .general).frame(width: 250)
                        Rectangle().fill(Palette.divider).frame(width: 1)
                        pane(page ?? .general, back: nil)
                    }
                } else if let p = page {
                    pane(p, back: { page = nil })
                } else {
                    sidebar(selected: nil)
                }
            }
        }
        .background(Palette.background.ignoresSafeArea())
        .sheet(item: $doc) { LegalSheet(doc: $0) }
        .sheet(isPresented: $toolbarOpen) { ToolbarCustomizeSheet().environmentObject(app).environment(\.layoutDirection, .leftToRight) }
        .sheet(isPresented: $templateOpen) { TemplatePickerSheet(mode: .defaultTemplate) { _, _, _ in app.objectWillChange.send() } }
    }

    private func sidebar(selected: SettingsPage?) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(L("الإعدادات", "Settings")).font(Fonts.display(22)).foregroundColor(Palette.navy)
                Spacer()
                if selected == nil { CloseButton { dismiss() } }
            }
            .padding(.leading, 8).padding(.bottom, 12)
            Button { page = .account } label: {
                HStack(spacing: 10) {
                    if let email = app.accountEmail {
                        Avatar(name: app.accountName ?? email, size: 38)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(app.accountName ?? email).font(Fonts.sans(14, .bold)).foregroundColor(Palette.textPrimary).lineLimit(1)
                            Text(email).font(Fonts.sans(11)).foregroundColor(Palette.textSecondary).lineLimit(1)
                        }
                    } else {
                        IconTile(icon: "ph_user", color: Palette.navy, size: 38)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(L("تسجيل الدخول", "Sign in")).font(Fonts.sans(14, .bold)).foregroundColor(Palette.textPrimary)
                            Text(L("اربط حسابك في Apple", "Link your Apple account")).font(Fonts.sans(11)).foregroundColor(Palette.textSecondary)
                        }
                    }
                    Spacer(minLength: 0)
                }
                .padding(10)
                .background(RoundedRectangle(cornerRadius: 14).fill(LinearGradient(colors: [Color(hex: 0xFFFFFDF7), Color(hex: 0xFFFCEBC2)], startPoint: .topLeading, endPoint: .bottomTrailing)))
            }
            Spacer().frame(height: 12)
            ScrollView {
                VStack(spacing: 2) {
                    ForEach(SettingsPage.allCases) { p in
                        let on = p == selected
                        Button { page = p } label: {
                            HStack(spacing: 10) {
                                IconTile(icon: p.icon, color: p.color, size: 28)
                                Text(p.title).font(Fonts.sans(14, .bold)).foregroundColor(on ? Palette.navy : Palette.textPrimary)
                                Spacer()
                                if selected == nil { Text(app.english ? "›" : "‹").foregroundColor(Palette.textSecondary) }
                                if on { RoundedRectangle(cornerRadius: 2).fill(Palette.gold).frame(width: 3, height: 18) }
                            }
                            .padding(.horizontal, 10).padding(.vertical, 7)
                            .background(RoundedRectangle(cornerRadius: 12).fill(on ? Palette.navySoft : Color.clear))
                        }
                    }
                }
            }
            Text(L("هامش", "Hamish") + " " + appVersion).font(Fonts.sans(11)).foregroundColor(Palette.textSecondary).padding(.leading, 10).padding(.top, 8)
        }
        .padding(.horizontal, 12).padding(.vertical, 16)
        .frame(maxHeight: .infinity)
        .background(Color.white)
    }

    private func pane(_ p: SettingsPage, back: (() -> Void)?) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                if let b = back { Button(action: b) { Text(app.english ? "‹" : "›").font(.system(size: 28)).foregroundColor(Palette.navy) }.padding(.trailing, 6) }
                Text(p.title).font(Fonts.display(24)).foregroundColor(Palette.textPrimary)
                Spacer()
                CloseButton { dismiss() }
            }
            .padding(.leading, back != nil ? 12 : 24).padding(.trailing, 14).padding(.top, 14)
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text(p.lead).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary).padding(.top, 2).padding(.bottom, 6)
                    content(p)
                }
                .padding(.horizontal, 24).padding(.bottom, 28)
            }
        }
    }

    @ViewBuilder private func content(_ p: SettingsPage) -> some View {
        switch p {
        case .account: AccountPane()
        case .general:
            GroupLabel(L("اللغة", "Language"))
            SettingsGroup {
                GroupRow(icon: "ph_translate", color: TileBlue, title: L("لغة التطبيق", "App language")) {
                    Segmented(options: ["العربية", "English"], selected: app.english ? 1 : 0) { app.setEnglish($0 == 1) }
                }
            }
            GroupLabel(L("المستندات", "Documents"))
            SettingsGroup {
                SwitchRow(icon: "ph_recent", color: TileGreen, title: L("المتابعة من حيث توقفت", "Continue where you left off"),
                          desc: L("تُفتح المستندات التي كانت مفتوحة عند تشغيل التطبيق", "Reopen your last documents when the app starts"), isOn: $app.resumeLast)
                GroupDivider()
                SwitchRow(icon: "ph_pages", color: TileGray, title: L("فتح المستندات في تبويبات", "Open documents as tabs"),
                          desc: L("كل مستند في تبويب جديد. أوقفه ليحل الجديد محل المفتوح", "Each document opens in a new tab. Turn off to replace the open one"), isOn: $app.openAsTabs)
                GroupDivider()
                let d = Templates.defaultChoice()
                GroupRow(icon: "ph_note", color: TileGold, title: L("القالب الافتراضي للملاحظات", "Default notebook template"), action: { templateOpen = true }) {
                    ValueChevron(value: Templates.labelOf(d.kind) + " · " + Templates.sizeOf(d.size).label + (d.landscape ? L(" · أفقي", " · landscape") : ""))
                }
            }
        case .pen:
            GroupLabel("Apple Pencil")
            SettingsGroup {
                SwitchRow(icon: "ph_pen_nib", color: TileBlue, title: L("النقر المزدوج على القلم", "Pencil double-tap"),
                          desc: L("يبدّل بين الممحاة وآخر قلم استخدمته", "Switches between the eraser and your last pen"), isOn: $app.penDoubleTapEnabled)
                GroupDivider()
                SwitchRow(icon: "ph_hand_pointing", color: TileGold, title: L("الكتابة بالإصبع", "Write with your finger"),
                          desc: L("أوقفه ليكتب القلم فقط ويبقى الإصبع للتمرير والتكبير", "Turn off so only the pencil writes and your finger scrolls and zooms"), isOn: $app.fingerWrites)
            }
            GroupLabel(L("الأصابع", "Fingers"))
            SettingsGroup {
                SwitchRow(icon: "ph_undo", color: TileGreen, title: L("نقرة بإصبعين للتراجع، وبثلاثة للإعادة", "Two-finger tap to undo, three to redo"),
                          desc: L("انقر الصفحة بإصبعين للتراجع، وبثلاثة لإعادة ما تراجعت عنه", "Tap the page with two fingers to undo, with three to redo"), isOn: $app.fingerUndo)
            }
            GroupLabel(L("شريط الأدوات", "Toolbar"))
            SettingsGroup {
                GroupRow(icon: "ph_sliders", color: TileGray, title: L("تخصيص شريط الأدوات", "Toolbar customization"),
                         desc: L("الشكل، والأدوات الظاهرة، واستعادة الافتراضي", "Style, which tools it shows, and restoring the default"), action: { toolbarOpen = true }) { Chevron() }
            }
        case .highlights:
            GroupLabel(L("الملخصات", "Summaries"))
            SettingsGroup {
                SwitchRow(icon: "ph_summary", color: TileGold, title: L("تفعيل الملخصات", "Enable summaries"),
                          desc: L("أوقفه لإخفاء زر الملخصات وعدم إضافة التمييز إليها", "Turn off to hide the summaries button and stop adding highlights"), isOn: $app.summariesOn)
                GroupDivider()
                SwitchRow(icon: "ph_text", color: TileGray, title: L("ترتيب نص الملخص", "Tidy summary text"),
                          desc: L("تنظيف المسافات وإكمال الجمل المقطوعة بين الأسطر", "Clean spacing and join sentences broken across lines"), isOn: $app.summaryTidy)
            }
            GroupLabel(L("الهايلايتر", "Highlighter"))
            SettingsGroup {
                SwitchRow(icon: "ph_snap", color: TileBlue, title: L("التقاط النص", "Text snap"),
                          desc: L("يلتصق الهايلايتر بالكلمات ويُضاف نصها إلى الملخص", "The highlighter snaps to words and adds their text to the summary"),
                          isOn: Binding(get: { app.highlightSnap }, set: { v in if !v || Premium.shared.require(.snap) { app.highlightSnap = v } }))
                GroupDivider()
                SwitchRow(icon: "ph_highlighter", color: TileGold, title: L("إضافة التمييز إلى الملخص", "Add highlights to summary"),
                          desc: L("أوقفه لتظلّل على الصفحة فقط دون إضافة النص", "Turn off to highlight on the page only, without adding text"), isOn: $app.highlightToSummary)
            }
            GroupLabel(L("ألوان التمييز كتصنيفات", "Highlight colors as labels"))
            SettingsGroup {
                ForEach(app.highlightPalette, id: \.self) { c in
                    HStack(spacing: 12) {
                        Circle().fill(Color(hex: c)).frame(width: 22, height: 22)
                        TextField("", text: Binding(get: { app.labelOf(c) }, set: { app.setLabel(c, $0) })).font(Fonts.sans(14))
                    }
                    .padding(.horizontal, 14).padding(.vertical, 10)
                    if c != app.highlightPalette.last { GroupDivider() }
                }
            }
        case .study:
            GroupLabel(L("مؤقت الدراسة", "Study timer"))
            SettingsGroup {
                GroupRow(icon: "ph_timer", color: TileGreen, title: L("مدة الدراسة", "Study length")) {
                    Stepper(L("\(app.studyMin) د", "\(app.studyMin) min"), value: Binding(get: { app.studyMin }, set: { app.setPomodoro(study: $0, rest: app.breakMin) }), in: 5...180, step: 5)
                        .font(Fonts.sans(14)).fixedSize()
                }
                GroupDivider()
                GroupRow(icon: "ph_pause", color: TileGold, title: L("مدة الراحة", "Break length")) {
                    Stepper(L("\(app.breakMin) د", "\(app.breakMin) min"), value: Binding(get: { app.breakMin }, set: { app.setPomodoro(study: app.studyMin, rest: $0) }), in: 1...60)
                        .font(Fonts.sans(14)).fixedSize()
                }
            }
            GroupLabel(L("التذكير اليومي", "Daily reminder"))
            SettingsGroup {
                SwitchRow(icon: "ph_recent", color: TileBlue, title: L("ذكّرني بالمذاكرة كل يوم", "Remind me to study every day"),
                          desc: L("إشعار في الوقت الذي تختاره", "A notification at the time you choose"),
                          isOn: Binding(get: { app.reminderOn }, set: { app.setReminder($0) }))
                if app.reminderOn {
                    GroupDivider()
                    GroupRow(icon: "ph_timer", color: TileSlate, title: L("الوقت", "Time")) {
                        DatePicker("", selection: Binding(get: {
                            Calendar.current.date(bySettingHour: app.reminderMinute / 60, minute: app.reminderMinute % 60, second: 0, of: Date()) ?? Date()
                        }, set: { d in
                            let c = Calendar.current.dateComponents([.hour, .minute], from: d)
                            app.setReminder(true, minute: (c.hour ?? 20) * 60 + (c.minute ?? 0))
                        }), displayedComponents: .hourAndMinute).labelsHidden()
                    }
                }
            }
            GroupLabel(L("الهدف اليومي", "Daily goal"))
            SettingsGroup {
                GroupRow(icon: "ph_star", color: TileGold, title: L("هدفك اليومي", "Your daily goal")) {
                    Stepper(formatReading(Int64(app.goalMinutes) * 60_000), value: Binding(get: { app.goalMinutes }, set: { app.setGoal($0) }), in: 10...600, step: 10)
                        .font(Fonts.sans(14)).fixedSize()
                }
            }
        case .appearance:
            GroupLabel(L("السمة", "Theme"))
            SettingsGroup {
                HStack(spacing: 14) {
                    ThemeCard(label: L("كحلي", "Navy"), theme: .navy)
                    ThemeCard(label: L("فاتح", "Light"), theme: .light)
                }
                .padding(14)
            }
            GroupLabel(L("الأحجام", "Sizes"))
            SettingsGroup {
                let bars: [CGFloat] = [0.85, 1, 1.15, 1.3]
                GroupRow(icon: "ph_sliders", color: TileGray, title: L("حجم شريط الأدوات", "Toolbar size")) {
                    Segmented(options: [L("صغير", "S"), L("عادي", "M"), L("كبير", "L"), L("أكبر", "XL")],
                              selected: bars.firstIndex { abs($0 - app.toolbarScale) < 0.01 } ?? 1) { app.toolbarScale = bars[$0] }
                }
                GroupDivider()
                let texts: [CGFloat] = [0.9, 1, 1.15, 1.3]
                GroupRow(icon: "ph_text", color: TilePurple, title: L("حجم الكتابة", "Text size")) {
                    Segmented(options: [L("أ", "Aa"), L("أ", "Aa"), L("أ", "Aa"), L("أ", "Aa")],
                              selected: texts.firstIndex { abs($0 - app.textScale) < 0.01 } ?? 1, sizes: [11, 13, 15, 17]) { app.textScale = texts[$0] }
                }
                GroupDivider()
                SwitchRow(icon: "ph_moon", color: TileSlate, title: L("القراءة الليلية", "Night reading"),
                          desc: L("صفحات داكنة لراحة العين", "Dark pages to rest your eyes"), isOn: $app.nightPages)
            }
        case .privacy:
            GroupLabel(L("قفل المستندات", "Document lock"))
            SettingsGroup {
                GroupRow(icon: "ph_lock", color: TileRed, title: L("قفل بالرمز أو Face ID", "Lock with PIN or Face ID"),
                         desc: L("اقفل مستندًا من قائمته في المكتبة", "Lock a document from its menu in the library"),
                         action: app.hasPin() ? { app.setPinFor = "" } : nil) {
                    if app.hasPin() { ValueChevron(value: L("تغيير الرمز", "Change PIN")) }
                }
            }
            GroupLabel(L("الخصوصية", "Privacy"))
            SettingsGroup {
                GroupRow(icon: "ph_eye", color: TileSlate, title: L("سياسة الخصوصية", "Privacy policy"),
                         desc: L("ما يحفظه التطبيق وأين", "What the app stores and where"), action: { doc = .privacy }) { Chevron() }
            }
        case .about:
            SettingsGroup {
                HStack(spacing: 14) {
                    AppLogo(size: 56)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(L("هامش", "Hamish")).font(Fonts.display(20)).foregroundColor(Palette.textPrimary)
                        Text(L("الإصدار", "Version") + " " + appVersion).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary)
                    }
                    Spacer()
                }
                .padding(18)
            }
            GroupLabel(L("قانوني", "Legal"))
            SettingsGroup {
                GroupRow(icon: "ph_list", color: TileSlate, title: L("تراخيص المكتبات المفتوحة", "Open-source licenses"),
                         desc: L("المكوّنات المستخدمة وتراخيصها", "Components used and their licenses"), action: { doc = .licenses }) { Chevron() }
                GroupDivider()
                GroupRow(icon: "ph_note", color: TileGray, title: L("شروط الاستخدام", "Terms of use"),
                         desc: L("حدود المسؤولية واستخدام الملفات", "Liability and use of your files"), action: { doc = .terms }) { Chevron() }
            }
        }
    }
}

private struct AccountPane: View {
    @EnvironmentObject var app: AppController
    @ObservedObject var pm = Premium.shared
    @State private var confirmOut = false

    var body: some View {
        GroupLabel(L("حسابك", "Your account"))
        SettingsGroup {
            if let email = app.accountEmail {
                HStack(spacing: 12) {
                    Avatar(name: app.accountName ?? email, size: 46)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(app.accountName ?? email).font(Fonts.sans(15, .bold)).foregroundColor(Palette.textPrimary)
                        Text(email).font(Fonts.sans(12)).foregroundColor(Palette.textSecondary)
                    }
                    Spacer()
                    Button(L("تسجيل الخروج", "Sign out")) { confirmOut = true }.font(Fonts.sans(13, .bold)).foregroundColor(Palette.danger)
                }
                .padding(14)
            } else {
                GroupRow(icon: "ph_user", color: Palette.navy, title: L("تسجيل الدخول", "Sign in"), desc: L("اربط حسابك في Apple", "Link your Apple account"),
                         action: { app.loginSeen = false }) { Chevron() }
            }
        }
        .alert(L("تسجيل الخروج؟", "Sign out?"), isPresented: $confirmOut) {
            Button(L("تسجيل الخروج", "Sign out"), role: .destructive) { app.signOutAccount() }
            Button(L("إلغاء", "Cancel"), role: .cancel) {}
        } message: { Text(L("تبقى ملفاتك وملاحظاتك على هذا الجهاز.", "Your files and notes stay on this device.")) }
        GroupLabel(L("الاشتراك", "Subscription"))
        SettingsGroup {
            if pm.active && pm.testMode && pm.product == nil {
                GroupRow(icon: "ph_star_fill", color: TileGold, title: L("هامش Premium", "Hamish Premium"), desc: L("وضع تجريبي · كل المزايا مفتوحة", "Test mode · every feature unlocked")) {
                    Button(L("إنهاء التجربة", "End test")) { pm.endTest() }.font(Fonts.sans(13, .bold)).foregroundColor(Palette.goldDeep)
                }
            } else if pm.active {
                let end = pm.expires.map { $0.formatted(date: .abbreviated, time: .omitted) }
                GroupRow(icon: "ph_star_fill", color: TileGold, title: L("هامش Premium", "Hamish Premium"),
                         desc: end == nil ? L("كل المزايا مفتوحة", "Every feature unlocked") : pm.trial ? L("فترة تجربة · تنتهي \(end!)", "Free trial · ends \(end!)") : L("يتجدد في \(end!)", "Renews on \(end!)")) {
                    Button(L("إدارة", "Manage")) {
                        if let u = URL(string: "https://apps.apple.com/account/subscriptions") { UIApplication.shared.open(u) }
                    }.font(Fonts.sans(13, .bold)).foregroundColor(Palette.goldDeep)
                }
            } else {
                GroupRow(icon: "ph_star", color: TileGold, title: L("الخطة المجانية", "Free plan"),
                         desc: L("\(app.library.count) من \(Premium.freeDocs) مستندات", "\(app.library.count) of \(Premium.freeDocs) documents"), action: { pm.paywallOpen = true }) {
                    Text(L("اشترك", "Upgrade")).font(Fonts.sans(13, .bold)).foregroundColor(Color(hex: 0xFF1E2A44))
                        .padding(.horizontal, 14).padding(.vertical, 7).background(Capsule().fill(Lib.accent))
                }
            }
        }
        GroupLabel(L("البيانات", "Data"))
        SettingsGroup {
            GroupRow(icon: "ph_folder", color: TileGreen, title: L("أين تُحفظ ملاحظاتك", "Where your notes are stored"), desc: L("على هذا الجهاز فقط", "On this device only")) { EmptyView() }
        }
    }
}

// ---------------------------------------------------------------- مكوّنات الإعدادات

struct IconTile: View {
    let icon: String
    let color: Color
    var size: CGFloat = 32
    var body: some View {
        PhIcon(icon, .white, size * 0.55).frame(width: size, height: size)
            .background(RoundedRectangle(cornerRadius: size * 0.28, style: .continuous).fill(color))
    }
}

private struct Avatar: View {
    let name: String
    let size: CGFloat
    var body: some View {
        Text(String(name.prefix(1)).uppercased()).font(Fonts.sans(size * 0.42, .bold)).foregroundColor(.white)
            .frame(width: size, height: size).background(Circle().fill(Palette.navy))
    }
}

private struct GroupLabel: View {
    let text: String
    init(_ t: String) { text = t }
    var body: some View {
        Text(text).font(Fonts.sans(12, .bold)).foregroundColor(Palette.textSecondary).padding(.top, 18).padding(.bottom, 6).padding(.leading, 4)
    }
}

private struct SettingsGroup<C: View>: View {
    @ViewBuilder let content: () -> C
    var body: some View {
        VStack(spacing: 0) { content() }
            .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Color.white))
            .roundedBorder(Color(hex: 0xFFF1EEE7), radius: 16)
    }
}

private struct GroupDivider: View {
    var body: some View { Rectangle().fill(Color(hex: 0xFFF1EEE7)).frame(height: 1).padding(.leading, 56) }
}

private struct GroupRow<Trailing: View>: View {
    let icon: String
    let color: Color
    let title: String
    var desc: String? = nil
    var action: (() -> Void)? = nil
    @ViewBuilder let trailing: () -> Trailing

    var body: some View {
        let row = HStack(spacing: 12) {
            IconTile(icon: icon, color: color, size: 32)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(Fonts.sans(14, .bold)).foregroundColor(Palette.textPrimary)
                if let d = desc { Text(d).font(Fonts.sans(12)).foregroundColor(Palette.textSecondary).fixedSize(horizontal: false, vertical: true) }
            }
            Spacer(minLength: 8)
            trailing()
        }
        .padding(.horizontal, 14).padding(.vertical, 11)
        .contentShape(Rectangle())
        if let a = action { Button(action: a) { row } } else { row }
    }
}

private struct SwitchRow: View {
    let icon: String
    let color: Color
    let title: String
    let desc: String
    @Binding var isOn: Bool
    var body: some View {
        GroupRow(icon: icon, color: color, title: title, desc: desc) {
            Toggle("", isOn: $isOn).labelsHidden().tint(Palette.navy)
        }
    }
}

private struct Chevron: View {
    @EnvironmentObject var app: AppController
    var body: some View { Text(app.english ? "›" : "‹").font(.system(size: 20)).foregroundColor(Palette.textSecondary) }
}

private struct ValueChevron: View {
    let value: String
    var body: some View {
        HStack(spacing: 6) {
            Text(value).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary).lineLimit(1)
            Chevron()
        }
    }
}

struct Segmented: View {
    let options: [String]
    let selected: Int
    var sizes: [CGFloat]? = nil
    let onSelect: (Int) -> Void
    var body: some View {
        HStack(spacing: 2) {
            ForEach(Array(options.enumerated()), id: \.offset) { i, o in
                Button { onSelect(i) } label: {
                    Text(o).font(Fonts.sans(sizes?[i] ?? 13, i == selected ? .bold : .regular))
                        .foregroundColor(i == selected ? .white : Palette.textPrimary)
                        .padding(.horizontal, 12).padding(.vertical, 6)
                        .background(RoundedRectangle(cornerRadius: 9).fill(i == selected ? Palette.navy : Color.clear))
                }
            }
        }
        .padding(3)
        .background(RoundedRectangle(cornerRadius: 11).fill(Palette.background))
    }
}

private struct ThemeCard: View {
    @EnvironmentObject var app: AppController
    let label: String
    let theme: AppTheme
    var body: some View {
        let sel = app.theme == theme
        Button { app.setTheme(theme) } label: {
            VStack(spacing: 8) {
                HStack(spacing: 0) {
                    Rectangle().fill(theme == .navy ? Color(hex: 0xFF0A0A0A) : Color.white).frame(width: 38)
                    Rectangle().fill(theme == .navy ? Color.black : Color(hex: 0xFFFAF4E6))
                        .overlay(RoundedRectangle(cornerRadius: 4).fill(Lib.accent).frame(width: 30, height: 38).padding(10), alignment: .topLeading)
                }
                .frame(height: 90)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .roundedBorder(sel ? Palette.gold : Palette.divider, radius: 12, width: sel ? 3 : 1)
                Text(label).font(Fonts.sans(14, sel ? .bold : .regular)).foregroundColor(Palette.textPrimary)
            }
        }
        .frame(maxWidth: .infinity)
    }
}

// ---------------------------------------------------------------- تخصيص شريط الأدوات

struct ToolbarCustomizeSheet: View {
    @EnvironmentObject var app: AppController

    private var tools: [(String, String, String)] { [
        ("nav", "ph_caret_down", L("التنقل بين الصفحات", "Page navigation")),
        ("bookmark", "ph_bookmark", L("العلامة المرجعية", "Bookmark")),
        ("timer", "ph_timer", L("مؤقت الدراسة", "Study timer")),
        ("snap", "ph_snap", L("التقاط النص", "Text snap")),
        ("lasso", "ph_lasso", L("التحديد الحر", "Lasso")),
        ("shapetool", "ph_shapes", L("الأشكال", "Shapes")),
        ("autofont", "ph_text_scan", L("Aa تحويل الكتابة تلقائياً", "Aa Auto-convert handwriting")),
        ("image", "ph_image", L("الصور", "Photos")),
        ("stickers", "ph_sticker", L("الملصقات", "Stickers")),
        ("text", "ph_text", L("مربع نص", "Text box")),
        ("sticky", "ph_note", L("ملاحظة لاصقة", "Sticky note")),
        ("link", "ph_link", L("رابط", "Link")),
        ("split", "ph_split", L("مستندان جنبًا إلى جنب", "Split view")),
        ("record", "ph_mic", L("تسجيل المحاضرة", "Lecture recording")),
        ("addpage", "ph_add_page", L("إضافة صفحة", "Add page")),
        ("ocr", "ph_scan", L("قراءة النص", "Read text")),
        ("search", "ph_search", L("البحث", "Search")),
        ("export", "ph_export", L("المشاركة والحفظ", "Share and save")),
        ("undo", "ph_undo", L("تراجع وإعادة", "Undo and redo")),
        ("zoom", "ph_zoom_in", L("التكبير", "Zoom")),
        ("fit", "ph_fit", L("ملاءمة الصفحة", "Fit page")),
        ("night", "ph_moon", L("القراءة الليلية", "Night reading")),
        ("focus", "ph_eye", L("وضع التركيز", "Focus mode")),
        ("zoomwin", "ph_zoom_write", L("نافذة الكتابة المكبّرة", "Zoom writing window")),
        ("readtimer", "ph_timer", L("عداد وقت القراءة", "Reading time counter"))
    ] }

    var body: some View {
        SheetScaffold(title: L("تخصيص شريط الأدوات", "Toolbar customization")) {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text(L("الشكل", "Style")).font(Fonts.sans(13, .bold)).foregroundColor(Palette.textSecondary)
                    HStack(spacing: 12) {
                        style(.penBar, L("شريط الأقلام", "Pen bar"), L("زر قلم واحد يفتح شريطًا بكل الأقلام والألوان", "One pen button opens a bar with every pen and color"))
                        style(.classic, L("كلاسيكي", "Classic"), L("القلم والهايلايتر زرّان، ونقرة ثانية للإعدادات", "Pen and highlighter are two buttons; tap again for settings"))
                    }
                    Text(L("الأدوات الظاهرة", "Visible tools")).font(Fonts.sans(13, .bold)).foregroundColor(Palette.textSecondary)
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 200), spacing: 10)], spacing: 10) {
                        ForEach(tools, id: \.0) { t in
                            let on = app.toolVisible(t.0)
                            Button { app.setToolVisible(t.0, !on) } label: {
                                HStack(spacing: 10) {
                                    PhIcon(t.1, on ? Palette.navy : Palette.textSecondary, 20)
                                    Text(t.2).font(Fonts.sans(13)).foregroundColor(Palette.textPrimary).lineLimit(1)
                                    Spacer()
                                    PhIcon(on ? "ph_check_circle" : "ph_close", on ? Palette.navy : Palette.textSecondary.opacity(0.5), 18)
                                }
                                .padding(12)
                                .background(RoundedRectangle(cornerRadius: 12).fill(on ? Palette.navySoft : Color.white))
                                .roundedBorder(Palette.divider, radius: 12)
                            }
                        }
                    }
                    HStack {
                        Spacer()
                        NavyButton(label: L("استعادة الافتراضي", "Restore default"), filled: false) { app.resetToolbar() }
                    }
                }
                .padding(.horizontal, 22).padding(.bottom, 22)
            }
        }
    }

    private func style(_ s: ToolbarStyle, _ t: String, _ d: String) -> some View {
        let sel = app.toolbarStyle == s
        return Button { app.toolbarStyle = s } label: {
            VStack(alignment: .leading, spacing: 4) {
                Text(t).font(Fonts.sans(15, .bold)).foregroundColor(sel ? Palette.navy : Palette.textPrimary)
                Text(d).font(Fonts.sans(12)).foregroundColor(Palette.textSecondary).multilineTextAlignment(.leading)
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 14).fill(Color.white))
            .roundedBorder(sel ? Palette.gold : Palette.divider, radius: 14, width: sel ? 2.5 : 1)
        }
    }
}
