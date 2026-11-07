import SwiftUI
import StoreKit

/// الخصائص المدفوعة في هامش Premium.
enum PremiumFeature: String, CaseIterable, Identifiable {
    case docs, summaries, snap, ocr, toText, translate, record, split, stats, cards, lock
    var id: String { rawValue }

    var icon: String {
        switch self {
        case .docs: return "ph_books"
        case .summaries: return "ph_summary"
        case .snap: return "ph_snap"
        case .ocr: return "ph_scan"
        case .toText: return "ph_text_scan"
        case .translate: return "ph_translate"
        case .record: return "ph_mic"
        case .split: return "ph_split"
        case .stats: return "ph_timer"
        case .cards: return "ph_cards"
        case .lock: return "ph_lock"
        }
    }

    var title: String {
        switch self {
        case .docs: return L("مستندات بلا حد", "Unlimited documents")
        case .summaries: return L("الملخصات", "Summaries")
        case .snap: return L("التقاط النص", "Text snap")
        case .ocr: return L("قراءة النص", "Read text")
        case .toText: return L("تحويل الكتابة إلى نص", "Handwriting to text")
        case .translate: return L("الترجمة", "Translation")
        case .record: return L("تسجيل المحاضرة", "Lecture recording")
        case .split: return L("مستندان جنبًا إلى جنب", "Split view")
        case .stats: return L("الإحصاءات الكاملة", "Full study stats")
        case .cards: return L("البطاقات", "Cards")
        case .lock: return L("قفل المستندات", "Document lock")
        }
    }

    var benefit: String {
        switch self {
        case .docs: return L("أضف مستندات وملاحظات بلا حد.", "Add as many documents and notes as you like.")
        case .summaries: return L("كل ما تظلله يُجمع مرتبًا في ملخص تعدّله بنفسك.", "Everything you highlight is collected into a neat summary you can edit.")
        case .snap: return L("يلتصق الهايلايتر بالكلمات ويُضاف نصها إلى الملخص.", "The highlighter snaps to words and adds their text to your summary.")
        case .ocr: return L("اقرأ نص الصفحات ليعمل البحث والتقاط النص في أي ملف.", "Read page text so search and text snap work in any file.")
        case .toText: return L("حوّل كتابتك اليدوية إلى نص مكتوب بلمسة.", "Turn your handwriting into typed text in one tap.")
        case .translate: return L("حدد أي نص إنجليزي واحصل عليه بالعربية فورًا، على جهازك.", "Select any English text and get it in Arabic instantly, right on your device.")
        case .record: return L("سجّل المحاضرة واسمع ما قيل لحظة كتابة كل سطر.", "Record the lecture and hear what was said as you wrote each line.")
        case .split: return L("اعرض الكتاب والملخص جنبًا إلى جنب.", "Show your book and notes side by side.")
        case .stats: return L("مخطط الشهر الكامل وتقرير وقتك في كل مادة.", "The full monthly chart and a report of your time in each subject.")
        case .cards: return L("اصنع بطاقات مراجعة واقلبها للمذاكرة.", "Make review cards and flip them to study.")
        case .lock: return L("اقفل مستنداتك بالرمز أو بصمة الوجه.", "Lock your documents with a PIN or Face ID.")
        }
    }
}

/// حالة الاشتراك. نسخة المتجر: الاشتراك السنوي عبر App Store (StoreKit 2).
/// نسخة التجربة (TEST_PREMIUM): زر الاشتراك يفعّل وضعًا تجريبيًا محليًا، مثل نسخة أندرويد التجريبية.
final class Premium: ObservableObject {
    static let shared = Premium()
    static let freeDocs = 3
    static let trialDays = 7
    static let yearPrice = "$4"
    static let productId = "hamish_premium"

    @Published private(set) var active = false
    @Published var lockedFeature: PremiumFeature? = nil
    @Published var paywallOpen = false
    @Published var limitOpen = false
    @Published private(set) var product: Product? = nil
    @Published private(set) var busy = false
    @Published private(set) var expires: Date? = nil
    @Published private(set) var trial = false

    #if TEST_PREMIUM
    let testMode = true
    #else
    let testMode = false
    #endif

    private var updates: Task<Void, Never>?

    private init() {
        #if TEST_PREMIUM
        active = UserDefaults.standard.bool(forKey: "test_premium")
        #endif
        updates = Task.detached { [weak self] in
            for await u in Transaction.updates {
                if case .verified(let t) = u { await t.finish() }
                await self?.refresh()
            }
        }
        Task { await self.refresh(); await self.load() }
    }

    func load() async {
        let p = try? await Product.products(for: [Self.productId]).first
        await MainActor.run { self.product = p }
    }

    var price: String { product?.displayPrice ?? Self.yearPrice }

    /// يقرأ الاشتراك الحالي من App Store.
    func refresh() async {
        #if TEST_PREMIUM
        let test = UserDefaults.standard.bool(forKey: "test_premium")
        await MainActor.run { if test { self.active = true } }
        #endif
        var ok = false
        var exp: Date? = nil
        var tr = false
        for await r in Transaction.currentEntitlements {
            if case .verified(let t) = r, t.productID == Self.productId, t.revocationDate == nil {
                if let e = t.expirationDate, e < Date() { continue }
                ok = true
                exp = t.expirationDate
                tr = t.offerType == .introductory
            }
        }
        #if TEST_PREMIUM
        if UserDefaults.standard.bool(forKey: "test_premium") { ok = true }
        #endif
        let fin = ok, fexp = exp, ftr = tr
        await MainActor.run {
            self.active = fin
            self.expires = fexp
            self.trial = ftr
        }
    }

    /// true إن كانت الخاصية متاحة؛ وإلا تظهر نافذة الخاصية المقفلة.
    @discardableResult
    func require(_ f: PremiumFeature) -> Bool {
        if active { return true }
        lockedFeature = f
        return false
    }

    /// هل يمكن إضافة n مستندات؟ وإلا تظهر نافذة الحد.
    func canAdd(_ n: Int = 1) -> Bool {
        if active || AppController.shared.library.count + n <= Self.freeDocs { return true }
        limitOpen = true
        return false
    }

    func purchase() async {
        #if TEST_PREMIUM
        if product == nil {
            await MainActor.run {
                UserDefaults.standard.set(true, forKey: "test_premium")
                self.active = true
                AppController.shared.message(L("وضع تجريبي: فُتحت كل المزايا", "Test mode: every feature unlocked"))
            }
            return
        }
        #endif
        guard let p = product else {
            await MainActor.run { AppController.shared.message(L("تعذر الاتصال بـ App Store، حاول لاحقًا", "Couldn’t reach the App Store, try again later")) }
            return
        }
        await MainActor.run { self.busy = true }
        do {
            let r = try await p.purchase()
            if case .success(let v) = r, case .verified(let t) = v {
                await t.finish()
                await refresh()
            }
        } catch {
            await MainActor.run { AppController.shared.message(L("لم يكتمل الشراء", "The purchase didn’t complete")) }
        }
        await MainActor.run { self.busy = false }
    }

    func restore() async {
        await MainActor.run { self.busy = true }
        try? await AppStore.sync()
        await refresh()
        await MainActor.run {
            self.busy = false
            AppController.shared.message(self.active ? L("استُعيد اشتراكك", "Your subscription was restored") : L("لا يوجد اشتراك على هذا الحساب", "No subscription on this account"))
        }
    }

    func endTest() {
        UserDefaults.standard.set(false, forKey: "test_premium")
        active = false
        Task { await refresh() }
    }

    var trialAvailable: Bool { product?.subscription?.introductoryOffer != nil || testMode }
}

// ======================================================== النوافذ

/// نوافذ Premium كلها، تُعرض من جذر التطبيق.
struct PremiumHost: View {
    @ObservedObject var pm = Premium.shared

    var body: some View {
        ZStack {
            if let f = pm.lockedFeature {
                Scrim { pm.lockedFeature = nil }
                SheetCard {
                    FeatureIcon(icon: f.icon)
                    Spacer().frame(height: 18)
                    Text(L("\(f.title) من مزايا Premium", "\(f.title) is part of Premium"))
                        .font(Fonts.display(20)).foregroundColor(Pw.ink).multilineTextAlignment(.center)
                    Spacer().frame(height: 8)
                    Text(f.benefit).font(Fonts.sans(14)).foregroundColor(Pw.muted).multilineTextAlignment(.center).lineSpacing(4)
                    Spacer().frame(height: 22)
                    GoldButton(label: L("شاهد Premium · \(Premium.trialDays) أيام مجانًا", "See Premium · \(Premium.trialDays) days free")) {
                        pm.lockedFeature = nil
                        pm.paywallOpen = true
                    }
                    QuietButton(label: L("ليس الآن", "Not now")) { pm.lockedFeature = nil }
                }
                .padding(24)
                .transition(.scale(scale: 0.9).combined(with: .opacity))
            }
            if pm.limitOpen {
                Scrim { pm.limitOpen = false }
                SheetCard {
                    FeatureIcon(icon: "ph_books")
                    Spacer().frame(height: 18)
                    Text(L("وصلت إلى \(Premium.freeDocs) مستندات", "You’ve reached \(Premium.freeDocs) documents"))
                        .font(Fonts.display(20)).foregroundColor(Pw.ink).multilineTextAlignment(.center)
                    Spacer().frame(height: 8)
                    Text(L("الخطة المجانية تتسع لـ \(Premium.freeDocs) مستندات. مستنداتك الحالية بأمان، واشترك لتضيف ما تشاء.",
                           "The free plan keeps up to \(Premium.freeDocs) documents. Your current documents stay safe. Upgrade to add as many as you like."))
                        .font(Fonts.sans(14)).foregroundColor(Pw.muted).multilineTextAlignment(.center).lineSpacing(4)
                    Spacer().frame(height: 18)
                    Group {
                    let used = min(AppController.shared.library.count, Premium.freeDocs)
                    GeometryReader { g in
                        ZStack(alignment: .leading) {
                            Capsule().fill(Color(hex: 0xFFF2EDE1))
                            Capsule().fill(Pw.goldButton).frame(width: g.size.width * CGFloat(used) / CGFloat(Premium.freeDocs))
                        }
                    }.frame(height: 10)
                    HStack {
                        Text(L("\(used) من \(Premium.freeDocs)", "\(used) of \(Premium.freeDocs) used")).font(Fonts.sans(12)).foregroundColor(Pw.muted)
                        Spacer()
                        Text(L("الخطة المجانية", "Free plan")).font(Fonts.sans(12)).foregroundColor(Pw.muted)
                    }.padding(.top, 6)
                    }
                    Spacer().frame(height: 20)
                    GoldButton(label: L("مستندات بلا حد", "Get unlimited documents")) {
                        pm.limitOpen = false
                        pm.paywallOpen = true
                    }
                    QuietButton(label: L("ليس الآن", "Not now")) { pm.limitOpen = false }
                }
                .padding(24)
                .transition(.scale(scale: 0.9).combined(with: .opacity))
            }
            if pm.paywallOpen {
                Scrim { pm.paywallOpen = false }
                PaywallView { pm.paywallOpen = false }
                    .transition(.scale(scale: 0.94).combined(with: .opacity))
            }
        }
        .animation(Motion.enter, value: pm.paywallOpen)
        .animation(Motion.enter, value: pm.limitOpen)
        .animation(Motion.enter, value: pm.lockedFeature)
    }
}

struct FeatureIcon: View {
    let icon: String
    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            PhIcon(icon, Palette.navy, 38)
                .frame(width: 76, height: 76)
                .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(Palette.navySoft))
            PremiumBadge(size: 24).padding(3).background(Circle().fill(Color.white)).offset(x: 6, y: 6)
        }
    }
}

/// شاشة الاشتراك: المزايا على جانب، والخطة والزر على الجانب الآخر.
struct PaywallView: View {
    @ObservedObject var pm = Premium.shared
    let onClose: () -> Void
    @State private var doc: LegalDoc? = nil

    var body: some View {
        GeometryReader { g in
            let wide = g.size.width >= 640
            let w = min(900, g.size.width * 0.94), h = g.size.height * 0.92
            Group {
                if wide {
                    HStack(spacing: 0) {
                        benefits.frame(width: w * 0.48)
                        plans
                    }
                } else {
                    ScrollView { VStack(spacing: 0) { benefits; plans } }
                }
            }
            .frame(width: w, height: h)
            .background(Color(hex: 0xFFFFFDF7))
            .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
            .position(x: g.size.width / 2, y: g.size.height / 2)
        }
        .sheet(item: $doc) { d in LegalSheet(doc: d) }
    }

    private var benefits: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                AppLogo(size: 52).background(RoundedRectangle(cornerRadius: 14).fill(Color(hex: 0xFFFFFDF7)))
                VStack(alignment: .leading, spacing: 2) {
                    Text(L("هامش", "Hamish")).font(Fonts.display(20)).foregroundColor(.white)
                    Text("PREMIUM").font(Fonts.sans(11, .bold)).kerning(1).foregroundColor(Pw.ink)
                        .padding(.horizontal, 8).padding(.vertical, 2)
                        .background(RoundedRectangle(cornerRadius: 7).fill(Pw.gold))
                }
            }
            Spacer().frame(height: 22)
            Text(L("ذاكر بذكاء مع", "Study smarter with")).font(Fonts.display(28)).foregroundColor(.white)
            Text(L("هامش Premium", "Hamish Premium")).font(Fonts.display(28)).foregroundColor(Pw.gold)
            Spacer().frame(height: 8)
            Text(L("كل ما تحتاجه للمحاضرات والملاحظات والامتحانات.", "Everything you need for lectures, notes and exams."))
                .font(Fonts.sans(13)).foregroundColor(.white.opacity(0.8))
            Spacer().frame(height: 18)
            ForEach([PremiumFeature.docs, .summaries, .ocr, .translate, .record, .split, .stats]) { f in
                HStack(spacing: 12) {
                    PhIcon(f.icon, Pw.gold, 16).frame(width: 30, height: 30)
                        .background(RoundedRectangle(cornerRadius: 9).fill(Color.white.opacity(0.12)))
                    Text(f.title).font(Fonts.sans(14, .bold)).foregroundColor(.white)
                }
                .padding(.vertical, 5)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 30).padding(.vertical, 28)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(LinearGradient(colors: [Color(hex: 0xFF16294A), Color(hex: 0xFF1B365D), Color(hex: 0xFF2F4B7C)],
                                   startPoint: .topLeading, endPoint: .bottomTrailing))
    }

    private var plans: some View {
        ZStack(alignment: .topTrailing) {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    if pm.active {
                        Text(L("أنت مشترك في Premium", "You’re on Premium")).font(Fonts.display(22)).foregroundColor(Pw.ink)
                        Spacer().frame(height: 6)
                        Text(L("كل المزايا مفتوحة لك.", "Every feature is unlocked for you.")).font(Fonts.sans(13)).foregroundColor(Pw.muted)
                        Spacer().frame(height: 24)
                        GoldButton(label: L("تم", "Done"), action: onClose)
                    } else {
                        Text(L("خطة Premium", "Your Premium plan")).font(Fonts.display(22)).foregroundColor(Pw.ink)
                        Text(L("جرّب كل شيء مجانًا \(Premium.trialDays) أيام، وألغِ متى شئت.", "Try everything free for \(Premium.trialDays) days. Cancel anytime."))
                            .font(Fonts.sans(13)).foregroundColor(Pw.muted)
                        Spacer().frame(height: 20)
                        ZStack(alignment: .topLeading) {
                            HStack(spacing: 14) {
                                Circle().strokeBorder(Pw.goldDeep, lineWidth: 7).frame(width: 22, height: 22)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(L("سنوي", "Yearly")).font(Fonts.display(16)).foregroundColor(Pw.ink)
                                    Text(L("\(Premium.trialDays) أيام مجانًا، ثم \(pm.price) في السنة", "\(Premium.trialDays)-day free trial, then \(pm.price)/year"))
                                        .font(Fonts.sans(12)).foregroundColor(Pw.muted)
                                }
                                Spacer()
                                VStack(alignment: .trailing, spacing: 2) {
                                    Text(pm.price).font(Fonts.display(22)).foregroundColor(Pw.ink)
                                    Text(L("في السنة", "per year")).font(Fonts.sans(11)).foregroundColor(Pw.muted)
                                }
                            }
                            .padding(.horizontal, 18).padding(.vertical, 16)
                            .background(RoundedRectangle(cornerRadius: 18).fill(Color.white))
                            .roundedBorder(Pw.gold, radius: 18, width: 2)
                            .padding(.top, 10)
                            Text(L("\(Premium.trialDays) أيام مجانًا", "\(Premium.trialDays) DAYS FREE")).font(Fonts.sans(11, .bold)).foregroundColor(.white)
                                .padding(.horizontal, 9).padding(.vertical, 3)
                                .background(RoundedRectangle(cornerRadius: 8).fill(Palette.navy))
                                .padding(.leading, 18)
                        }
                        Group {
                        Spacer().frame(height: 16)
                        CompareTable()
                        Spacer().frame(height: 22)
                        GoldButton(label: pm.busy ? L("جارٍ التحقق…", "Verifying…")
                                   : pm.trialAvailable ? L("ابدأ \(Premium.trialDays) أيام مجانًا", "Start \(Premium.trialDays)-day free trial")
                                   : L("اشترك الآن", "Subscribe now")) {
                            Task {
                                await pm.purchase()
                                if pm.active { onClose() }
                            }
                        }
                        Spacer().frame(height: 8)
                        Text(pm.testMode && pm.product == nil
                             ? L("وضع تجريبي: الدفع غير مربوط بعد، ولن يُخصم أي مبلغ.", "Test mode: payment isn’t connected yet, nothing will be charged.")
                             : L("يتجدد سنويًا عبر App Store، وتلغيه متى شئت من إعدادات حسابك.", "Renews yearly through the App Store. Cancel anytime in your account settings."))
                            .font(Fonts.sans(11.5)).foregroundColor(Pw.muted).multilineTextAlignment(.center).frame(maxWidth: .infinity)
                        Spacer().frame(height: 10)
                        HStack(spacing: 16) {
                            link(L("استعادة الشراء", "Restore purchase")) { Task { await pm.restore() } }
                            link(L("الشروط", "Terms")) { doc = .terms }
                            link(L("الخصوصية", "Privacy")) { doc = .privacy }
                        }
                        .frame(maxWidth: .infinity)
                        }
                    }
                }
                .padding(.horizontal, 30).padding(.vertical, 28)
            }
            Button(action: onClose) {
                PhIcon("ph_close", Pw.muted, 18, L("إغلاق", "Close")).frame(width: 36, height: 36)
                    .background(Circle().fill(Color(hex: 0xFFF1EDE3)))
            }
            .padding(16)
        }
    }

    private func link(_ t: String, _ a: @escaping () -> Void) -> some View {
        Button(action: a) { Text(t).font(Fonts.sans(12, .bold)).foregroundColor(Palette.navy).padding(4) }
    }
}

private struct CompareTable: View {
    var body: some View {
        VStack(spacing: 0) {
            row("", L("مجاني", "Free"), "Premium", head: true)
            Divider().background(Color(hex: 0xFFF3EEE2))
            row(L("المستندات", "Documents"), "\(Premium.freeDocs)", L("بلا حد", "Unlimited"))
            Divider().background(Color(hex: 0xFFF3EEE2))
            row(L("الأقلام والهايلايتر والممحاة", "Pens, highlighter, eraser"), "✓", "✓")
            Divider().background(Color(hex: 0xFFF3EEE2))
            row(L("الملخصات وقراءة النص والترجمة", "Summaries, OCR, translation"), "—", "✓")
        }
        .background(RoundedRectangle(cornerRadius: 14).fill(Color.white))
        .roundedBorder(Pw.line, radius: 14)
    }

    private func row(_ l: String, _ f: String, _ p: String, head: Bool = false) -> some View {
        HStack {
            Text(l).font(Fonts.sans(12, head ? .bold : .regular)).foregroundColor(head ? Pw.muted : Pw.ink)
            Spacer()
            Text(f).font(Fonts.sans(12, head ? .bold : .regular)).foregroundColor(head ? Pw.muted : Pw.ink).frame(width: 70)
            Text(p).font(Fonts.sans(12, .bold)).foregroundColor(head ? Pw.muted : Pw.ink).frame(width: 80)
        }
        .padding(.horizontal, 14).padding(.vertical, 8)
    }
}

/// بطاقة صغيرة تدعو للاشتراك (في المكتبة والإحصاءات).
struct PremiumTeaser: View {
    let title: String
    let desc: String
    var body: some View {
        Button { Premium.shared.paywallOpen = true } label: {
            HStack(spacing: 14) {
                PremiumBadge(size: 34)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(Fonts.display(15)).foregroundColor(Pw.ink)
                    Text(desc).font(Fonts.sans(12)).foregroundColor(Pw.muted)
                }
                Spacer(minLength: 0)
            }
            .padding(16)
            .background(RoundedRectangle(cornerRadius: 20).fill(LinearGradient(colors: [Color(hex: 0xFFFFF8E4), Color(hex: 0xFFFCEBC2)], startPoint: .topLeading, endPoint: .bottomTrailing)))
            .roundedBorder(Color(hex: 0xFFF1DFAE), radius: 20)
        }
        .buttonStyle(PressScale(scale: 0.97))
    }
}
