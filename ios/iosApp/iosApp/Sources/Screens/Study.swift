import SwiftUI
import Charts
import AuthenticationServices

/// أزرار مؤقت الدراسة: تشغيل، ثم إيقاف مؤقت / متابعة، وإنهاء.
struct PomodoroControl: View {
    @EnvironmentObject var app: AppController
    var showStartLabel = true

    var body: some View {
        HStack(spacing: 0) {
            if app.pomoPhase == 0 {
                Button { app.startPomodoro() } label: {
                    HStack(spacing: 6) {
                        PhIcon("ph_timer", Palette.navy, 20, L("تشغيل مؤقت الدراسة", "Start study timer"))
                        if showStartLabel { Text(L("مؤقت \(app.studyMin) د", "Timer \(app.studyMin) min")).font(Fonts.sans(13)).foregroundColor(Palette.navy) }
                    }
                    .padding(10)
                }
            } else {
                let left = app.pomoLeftMs / 1000
                let study = app.pomoPhase == 1
                Text((study ? L("دراسة ", "Study ") : L("راحة ", "Break ")) + String(format: "%02d:%02d", left / 60, left % 60))
                    .font(Fonts.sans(14, .bold))
                    .foregroundColor(app.pomoPaused ? Palette.textSecondary : study ? Palette.navy : Palette.goldDeep)
                    .monospacedDigit()
                    .padding(.horizontal, 6)
                Button { if app.pomoPaused { app.resumePomodoro() } else { app.pausePomodoro() } } label: {
                    PhIcon(app.pomoPaused ? "ph_play" : "ph_pause", Palette.navy, 18, app.pomoPaused ? L("متابعة المؤقت", "Resume timer") : L("إيقاف مؤقت", "Pause")).padding(8)
                }
                Button { app.stopPomodoro() } label: { PhIcon("ph_stop", Palette.textSecondary, 18, L("إنهاء المؤقت", "Stop timer")).padding(8) }
            }
        }
    }
}

// ---------------------------------------------------------------- الإحصاءات

/// لون النص الذهبي: داكن في الفاتح، فاتح في الداكن.
private var goldText: Color { ThemeState.light ? Lib.accentDeep : Lib.accent }

/// إحصاءات الدراسة (نفس StatsDialog في أندرويد).
struct StatsView: View {
    @EnvironmentObject var app: AppController
    @ObservedObject var pm = Premium.shared
    @State private var month = Calendar.current.date(from: Calendar.current.dateComponents([.year, .month], from: Date()))!
    @State private var goalOpen = false
    @Environment(\.dismiss) private var dismiss

    private struct Report { let name: String; let total: Int64; let days: Int; let docs: [(String, Int64)] }

    private func ms(_ d: Date) -> Int64 { app.stats[AppController.dayKey(d)] ?? 0 }

    var body: some View {
        let cal = Calendar.current
        let days: [Date] = (0..<(cal.range(of: .day, in: .month, for: month)?.count ?? 30)).map { cal.date(byAdding: .day, value: $0, to: month)! }
        let keys = Set(days.map { AppController.dayKey($0) })
        let values = days.map { ms($0) }
        let monthTotal = values.reduce(0, +)
        let studied = values.filter { $0 >= 60_000 }.count
        let goalMs = Int64(app.goalMinutes) * 60_000
        let goalDays = values.filter { $0 >= goalMs }.count
        let pomos = app.stats.filter { $0.key.hasPrefix("pomo|") && keys.contains(String($0.key.dropFirst(5))) }.values.reduce(0, +)
        let todayMs = ms(Date())
        let thisMonth = cal.isDate(month, equalTo: Date(), toGranularity: .month)
        return GeometryReader { g in
            let wide = g.size.width >= 640
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 12) {
                        Text(L("إحصاءات الدراسة", "Study stats")).font(Fonts.display(28)).foregroundColor(Lib.text).lineLimit(1)
                        Spacer()
                        monthSwitcher(canNext: !thisMonth)
                        IconCircleButton(icon: "ph_close", label: L("إغلاق", "Close")) { dismiss() }
                    }
                    Spacer().frame(height: 18)
                    todayHero(todayMs, goalMs, showWeek: wide)
                    Spacer().frame(height: 16)
                    let tiles: [(String, Color, String, String)] = [
                        ("ph_timer", Color(hex: 0xFFD9A21B), formatReading(monthTotal), L("مجموع الشهر", "Total this month")),
                        ("ph_books", Color(hex: 0xFF2F6FDB), "\(studied)", L("أيام الدراسة", "Study days")),
                        ("ph_check_circle", Color(hex: 0xFF2E9D6B), L("\(goalDays) يوم", "\(goalDays) days"), L("حققت الهدف", "Goal reached")),
                        ("ph_recent", Color(hex: 0xFF8B5CF6), studied > 0 ? formatReading(monthTotal / Int64(studied)) : "—", L("متوسط يوم الدراسة", "Avg study day")),
                        ("ph_timer_fill", Color(hex: 0xFFD9534F), "\(pomos)", L("جلسات التركيز", "Focus sessions"))
                    ]
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 12), count: wide ? 5 : 2), spacing: 12) {
                        ForEach(0..<tiles.count, id: \.self) { i in kpi(tiles[i]) }
                    }
                    if !pm.active {
                        Spacer().frame(height: 16)
                        PremiumTeaser(title: L("افتح الإحصاءات الكاملة", "Unlock full study stats"), desc: L("مخطط الشهر وتقرير وقتك في كل مادة.", "The monthly chart and a report of your time in each subject."))
                    } else {
                        Spacer().frame(height: 16)
                        VStack(alignment: .leading, spacing: 14) {
                            HStack {
                                Text(L("وقت الدراسة اليومي", "Daily study time")).font(Fonts.display(16)).foregroundColor(Lib.text)
                                Spacer()
                                legend(Lib.accent, L("حققت الهدف", "Goal reached"))
                                legend(Lib.iconBlue, L("دون الهدف", "Below goal")).padding(.leading, 14)
                            }
                            MonthBars(values: values, goalMs: goalMs, todayIndex: thisMonth ? cal.component(.day, from: Date()) : nil)
                        }
                        .padding(.horizontal, 20).padding(.vertical, 18)
                        .background(RoundedRectangle(cornerRadius: 22).fill(Lib.card))
                        .roundedBorder(Lib.line, radius: 22)
                        Spacer().frame(height: 24)
                        Text(L("المواد هذا الشهر", "Subjects this month")).font(Fonts.display(18)).foregroundColor(Lib.text)
                        Spacer().frame(height: 12)
                        let reports = self.reports(keys)
                        if reports.isEmpty {
                            Text(L("لا توجد قراءة مسجلة في هذا الشهر.", "No reading recorded this month.")).font(Fonts.sans(14)).foregroundColor(Lib.muted)
                        }
                        let maxTotal = max(1, reports.map { $0.total }.max() ?? 1)
                        let colors: [Color] = [Color(hex: 0xFFF4C95D), Color(hex: 0xFF2F4B7C), Color(hex: 0xFF2E9D6B), Color(hex: 0xFF8B5CF6), Color(hex: 0xFFD9534F), Color(hex: 0xFF6B7A90)]
                        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 14), count: wide ? 2 : 1), spacing: 14) {
                            ForEach(0..<reports.count, id: \.self) { i in
                                subjectCard(reports[i], colors[i % colors.count], maxTotal, monthTotal)
                            }
                        }
                    }
                }
                .padding(.horizontal, 28).padding(.vertical, 24)
            }
        }
        .background(Lib.side.ignoresSafeArea())
        .sheet(isPresented: $goalOpen) { GoalSheet().environmentObject(app) }
    }

    private func reports(_ keys: Set<String>) -> [Report] {
        var perDoc: [String: [String: Int64]] = [:]
        for (k, v) in app.stats {
            guard let bar = k.firstIndex(of: "|"), !k.hasPrefix("pomo|") else { continue }
            let day = String(k[..<bar])
            if keys.contains(day) { perDoc[String(k[k.index(after: bar)...]), default: [:]][day] = v }
        }
        var byFolder: [String: [(String, [String: Int64])]] = [:]
        for (id, m) in perDoc {
            guard let e = app.entry(id) else { continue }
            byFolder[e.folder.isEmpty ? L("بدون مادة", "No subject") : e.folder, default: []].append((e.name, m))
        }
        return byFolder.map { f, docs in
            var daySet = Set<String>()
            docs.forEach { _, m in m.forEach { d, v in if v >= 60_000 { daySet.insert(d) } } }
            return Report(name: f, total: docs.reduce(0) { $0 + $1.1.values.reduce(0, +) }, days: daySet.count,
                          docs: docs.map { ($0.0, $0.1.values.reduce(0, +)) }.sorted { $0.1 > $1.1 })
        }.sorted { $0.total > $1.total }
    }

    private func monthTitle(_ d: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: app.english ? "en" : "ar")
        f.dateFormat = "LLLL yyyy"
        return f.string(from: d)
    }

    private func monthSwitcher(canNext: Bool) -> some View {
        HStack(spacing: 0) {
            Button { month = Calendar.current.date(byAdding: .month, value: -1, to: month)! } label: {
                PhIcon("ph_caret_down", Lib.text, 16, L("الشهر السابق", "Previous month")).rotationEffect(.degrees(app.english ? 90 : -90)).frame(width: 42, height: 42)
            }
            Text(monthTitle(month)).font(Fonts.display(14)).foregroundColor(Lib.text).lineLimit(1)
            Button { month = Calendar.current.date(byAdding: .month, value: 1, to: month)! } label: {
                PhIcon("ph_caret_down", canNext ? Lib.text : Lib.line, 16, L("الشهر التالي", "Next month")).rotationEffect(.degrees(app.english ? -90 : 90)).frame(width: 42, height: 42)
            }
            .disabled(!canNext)
        }
        .frame(height: 42)
        .background(Capsule().fill(Lib.card))
        .overlay(Capsule().stroke(Lib.line, lineWidth: 1))
    }

    private func todayHero(_ todayMs: Int64, _ goalMs: Int64, showWeek: Bool) -> some View {
        let gold = Color(hex: 0xFFF4C95D)
        let frac = min(1, Double(todayMs) / Double(max(1, goalMs)))
        let streak = app.studyStreak()
        return HStack(spacing: 24) {
            ZStack {
                Circle().stroke(Color.white.opacity(0.14), lineWidth: 12)
                Circle().trim(from: 0, to: frac).stroke(gold, style: StrokeStyle(lineWidth: 12, lineCap: .round)).rotationEffect(.degrees(-90))
                VStack(spacing: 0) {
                    Text(formatReading(todayMs)).font(Fonts.display(20)).foregroundColor(.white).lineLimit(1).minimumScaleFactor(0.6)
                    Text(L("من \(formatReading(goalMs))", "of \(formatReading(goalMs))")).font(Fonts.sans(12)).foregroundColor(.white.opacity(0.75)).lineLimit(1)
                }
                .padding(14)
            }
            .frame(width: 124, height: 124)
            VStack(alignment: .leading, spacing: 0) {
                Text(todayMs >= goalMs ? L("حققت هدف اليوم", "Today’s goal reached") : L("باقي \(formatReading(goalMs - todayMs)) لهدف اليوم", "\(formatReading(goalMs - todayMs)) left for today’s goal"))
                    .font(Fonts.display(20)).foregroundColor(.white).lineSpacing(6)
                Spacer().frame(height: 8)
                HStack(spacing: 10) {
                    Text(L("هدفك اليومي: \(formatReading(goalMs))", "Daily goal: \(formatReading(goalMs))")).font(Fonts.sans(13)).foregroundColor(.white.opacity(0.8))
                    Button { goalOpen = true } label: {
                        Text(L("تغيير", "Change")).font(Fonts.sans(12, .bold)).foregroundColor(gold).padding(.horizontal, 12).padding(.vertical, 5)
                            .background(Capsule().fill(Color.white.opacity(0.14)))
                    }
                }
                Spacer().frame(height: 12)
                HStack(spacing: 8) {
                    FlameShape().fill(Color(hex: 0xFFE8864A)).frame(width: 14, height: 14)
                    Text(streak > 0 ? L("سلسلة دراسة: \(streak) \(streak == 1 ? "يوم" : "أيام") متتالية", "\(streak)-day study streak") : L("ابدأ سلسلة دراسة اليوم", "Start a study streak today"))
                        .font(Fonts.sans(13, .bold)).foregroundColor(Color(hex: 0xFFFFE7A3))
                }
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(Capsule().fill(gold.opacity(0.18)))
            }
            Spacer(minLength: 0)
            if showWeek {
                HStack(spacing: 9) {
                    ForEach((0...6).reversed(), id: \.self) { back in
                        let d = Calendar.current.date(byAdding: .day, value: -back, to: Date())!
                        let v = ms(d)
                        VStack(spacing: 6) {
                            ZStack {
                                if v >= goalMs { Circle().fill(gold); PhIcon("ph_check", Color(hex: 0xFF1E2A44), 14) }
                                else { Circle().stroke(v >= 60_000 ? gold : Color.white.opacity(0.25), lineWidth: 2) }
                                if back == 0 { Circle().stroke(Color.white, lineWidth: 2) }
                            }
                            .frame(width: 28, height: 28)
                            Text(weekday(d)).font(Fonts.sans(11)).foregroundColor(.white.opacity(0.85)).lineLimit(1)
                        }
                    }
                }
            }
        }
        .padding(.horizontal, 24).padding(.vertical, 22)
        .background(RoundedRectangle(cornerRadius: 24).fill(LinearGradient(colors: [Color(hex: 0xFF1B365D), Color(hex: 0xFF2F4B7C), Color(hex: 0xFF3A5A8F)], startPoint: .topLeading, endPoint: .bottomTrailing)))
    }

    private func weekday(_ d: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: app.english ? "en" : "ar")
        f.dateFormat = "EEE"
        return f.string(from: d)
    }

    private func kpi(_ k: (String, Color, String, String)) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            PhIcon(k.0, k.1, 18).frame(width: 32, height: 32).background(RoundedRectangle(cornerRadius: 10).fill(k.1.opacity(0.14)))
            Spacer().frame(height: 10)
            Text(k.2).font(Fonts.display(20)).foregroundColor(Lib.text).lineLimit(1).minimumScaleFactor(0.7)
            Text(k.3).font(Fonts.sans(12)).foregroundColor(Lib.muted).lineLimit(1)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 18).fill(Lib.card))
        .roundedBorder(Lib.line, radius: 18)
    }

    private func legend(_ c: Color, _ label: String) -> some View {
        HStack(spacing: 6) {
            RoundedRectangle(cornerRadius: 3).fill(c).frame(width: 10, height: 10)
            Text(label).font(Fonts.sans(12)).foregroundColor(Lib.muted).lineLimit(1)
        }
    }

    private func subjectCard(_ r: Report, _ color: Color, _ maxTotal: Int64, _ monthTotal: Int64) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 10) {
                RoundedRectangle(cornerRadius: 4).fill(color).frame(width: 12, height: 12)
                Text(r.name).font(Fonts.display(15)).foregroundColor(Lib.text).lineLimit(1)
                Spacer()
                Text(formatReading(r.total)).font(Fonts.display(15)).foregroundColor(goldText).lineLimit(1)
            }
            Spacer().frame(height: 12)
            GeometryReader { g in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 4).fill(Lib.line)
                    RoundedRectangle(cornerRadius: 4).fill(color).frame(width: g.size.width * max(0.02, min(1, CGFloat(r.total) / CGFloat(maxTotal))))
                }
            }
            .frame(height: 8)
            Spacer().frame(height: 8)
            let share = monthTotal > 0 ? r.total * 100 / monthTotal : 0
            Text(L("\(share)% من الشهر · \(r.days) \(r.days == 1 ? "يوم" : "أيام")", "\(share)% of the month · \(r.days) \(r.days == 1 ? "day" : "days")") +
                 (r.days > 0 ? L(" · بمتوسط \(formatReading(r.total / Int64(r.days))) لليوم", " · avg \(formatReading(r.total / Int64(r.days)))/day") : ""))
                .font(Fonts.sans(12)).foregroundColor(Lib.muted)
            ForEach(0..<min(5, r.docs.count), id: \.self) { i in
                HStack(spacing: 8) {
                    PhIcon("ph_note", Lib.muted, 14)
                    Text(r.docs[i].0).font(Fonts.sans(13)).foregroundColor(Lib.text).lineLimit(1)
                    Spacer()
                    Text(formatReading(r.docs[i].1)).font(Fonts.sans(12)).foregroundColor(Lib.muted).lineLimit(1)
                }
                .padding(.top, 8)
            }
        }
        .padding(.horizontal, 18).padding(.vertical, 16)
        .background(RoundedRectangle(cornerRadius: 20).fill(Lib.card))
        .roundedBorder(Lib.line, radius: 20)
    }
}

/// اللهب الصغير (نفس رسم أندرويد).
struct FlameShape: Shape {
    func path(in r: CGRect) -> Path {
        let k = r.width / 24
        var p = Path()
        p.move(to: CGPoint(x: 12 * k, y: 2 * k))
        p.addCurve(to: CGPoint(x: 18 * k, y: 13.5 * k), control1: CGPoint(x: 12 * k, y: 2 * k), control2: CGPoint(x: 18 * k, y: 7 * k))
        p.addArc(center: CGPoint(x: 12 * k, y: 13.5 * k), radius: 6 * k, startAngle: .degrees(0), endAngle: .degrees(180), clockwise: false)
        p.addCurve(to: CGPoint(x: 8.4 * k, y: 8.2 * k), control1: CGPoint(x: 6 * k, y: 11 * k), control2: CGPoint(x: 7.2 * k, y: 9.4 * k))
        p.addCurve(to: CGPoint(x: 10.4 * k, y: 10.8 * k), control1: CGPoint(x: 8.6 * k, y: 9.8 * k), control2: CGPoint(x: 9.4 * k, y: 10.8 * k))
        p.addCurve(to: CGPoint(x: 12 * k, y: 2 * k), control1: CGPoint(x: 10.4 * k, y: 7.4 * k), control2: CGPoint(x: 11.6 * k, y: 5 * k))
        p.closeSubpath()
        return p
    }
}

/// أعمدة أيام الشهر مع خط هدف متقطع؛ الأيام الفارغة خطوط خفيفة، واليوم محاط بإطار.
private struct MonthBars: View {
    @Environment(\.colorScheme) private var themeTick // يعيد الرسم عند تغيير الثيم
    let values: [Int64]
    let goalMs: Int64
    let todayIndex: Int?
    @Environment(\.layoutDirection) private var dir

    var body: some View {
        let mx = max(1, Int64(Double(max(values.max() ?? 0, goalMs)) * 1.1))
        VStack(spacing: 4) {
            Canvas { ctx, size in
                let n = values.count
                let gap: CGFloat = 4
                let bw = max(1, (size.width - gap * CGFloat(n - 1)) / CGFloat(n))
                let rtl = dir == .rightToLeft
                for (i, v) in values.enumerated() {
                    let x = rtl ? size.width - CGFloat(i + 1) * bw - CGFloat(i) * gap : CGFloat(i) * (bw + gap)
                    if v <= 0 {
                        ctx.fill(Path(roundedRect: CGRect(x: x, y: size.height - 4, width: bw, height: 4), cornerRadius: 2), with: .color(Lib.line))
                    } else {
                        let h = max(6, size.height * CGFloat(v) / CGFloat(mx))
                        let r = CGRect(x: x, y: size.height - h, width: bw, height: h)
                        let path = Path(roundedRect: r, cornerRadius: min(5, bw / 2))
                        if v >= goalMs {
                            ctx.fill(path, with: .linearGradient(Gradient(colors: [Color(hex: 0xFFF4C95D), Color(hex: 0xFFE9B73E)]), startPoint: CGPoint(x: 0, y: r.minY), endPoint: CGPoint(x: 0, y: r.maxY)))
                        } else {
                            ctx.fill(path, with: .color(Lib.iconBlue.opacity(0.85)))
                        }
                    }
                    if let t = todayIndex, t == i + 1 {
                        ctx.stroke(Path(roundedRect: CGRect(x: x - 2, y: 0, width: bw + 4, height: size.height), cornerRadius: 6), with: .color(Lib.text.opacity(0.35)), lineWidth: 1.5)
                    }
                }
                let gy = size.height - size.height * CGFloat(goalMs) / CGFloat(mx)
                var g = Path()
                g.move(to: CGPoint(x: 0, y: gy))
                g.addLine(to: CGPoint(x: size.width, y: gy))
                ctx.stroke(g, with: .color(Lib.accentDeep.opacity(0.7)), style: StrokeStyle(lineWidth: 1.5, dash: [6, 5]))
            }
            .frame(height: 170)
            HStack {
                ForEach([1, 5, 10, 15, 20, 25, values.count], id: \.self) { d in
                    Text("\(d)").font(Fonts.sans(10)).foregroundColor(Lib.muted)
                    if d != values.count { Spacer() }
                }
            }
        }
    }
}

private struct GoalSheet: View {
    @EnvironmentObject var app: AppController
    @State private var hours = ""
    @State private var minutes = ""
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        let total = (Int(hours) ?? 0) * 60 + (Int(minutes) ?? 0)
        SheetScaffold(title: L("الهدف اليومي", "Daily goal")) {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 10) {
                    FieldBox(placeholder: L("ساعات", "Hours"), text: $hours).keyboardType(.numberPad)
                    FieldBox(placeholder: L("دقائق", "Minutes"), text: $minutes).keyboardType(.numberPad)
                }
                Text((5...1440).contains(total) ? L("الهدف: \(formatReading(Int64(total) * 60_000)) يوميًا", "Goal: \(formatReading(Int64(total) * 60_000)) a day") : L("اختر بين 5 دقائق و24 ساعة", "Choose between 5 minutes and 24 hours"))
                    .font(Fonts.sans(13)).foregroundColor(Lib.muted)
                HStack {
                    Spacer()
                    NavyButton(label: L("إلغاء", "Cancel"), filled: false) { dismiss() }
                    NavyButton(label: L("حفظ", "Save")) { app.setGoal(total); dismiss() }.disabled(!(5...1440).contains(total))
                }
            }
            .padding(.horizontal, 22)
        }
        .onAppear { hours = "\(app.goalMinutes / 60)"; minutes = "\(app.goalMinutes % 60)" }
        .presentationDetents([.height(260)])
    }
}

// ---------------------------------------------------------------- تسجيل الدخول

/// أول فتح للتطبيق: تسجيل الدخول (يمكن تخطيه)، بنفس تصميم SignInScreen في أندرويد.
/// «المتابعة باستخدام Google» كما في أندرويد، و«المتابعة باستخدام Apple» (شرط App Store مع Google).
struct SignInScreen: View {
    @EnvironmentObject var app: AppController
    @State private var busy = false
    @State private var error: String? = nil

    var body: some View {
        ZStack(alignment: .bottom) {
            Lib.bg.ignoresSafeArea()
            VStack(spacing: 0) {
                ZStack {
                    RoundedRectangle(cornerRadius: 28, style: .continuous).fill(Color(hex: 0xFFFFFDF7))
                        .overlay(RoundedRectangle(cornerRadius: 28, style: .continuous).stroke(Lib.line, lineWidth: 1))
                        .shadow(color: Lib.text.opacity(0.25), radius: 14, y: 4)
                    AppLogo(size: 96, corner: 22)
                }
                .frame(width: 112, height: 112)
                Spacer().frame(height: 22)
                UnderlinedTitle(text: L("هامش", "Hamish"), size: 44)
                Spacer().frame(height: 16)
                Text(L("ملاحظاتك وملفات PDF وأدوات المذاكرة في مكان واحد.\nسجّل الدخول لربط حسابك، أو تخطَّ وابدأ مباشرة.",
                       "Your notes, PDFs and study tools in one place.\nSign in to link your account, or skip and start right away."))
                    .font(Fonts.sans(15)).foregroundColor(Lib.muted).multilineTextAlignment(.center).lineSpacing(7)
                Spacer().frame(height: 36)
                // زر Google مخفي حتى يُضاف معرّف عميل iOS (سر GOOGLE_IOS_CLIENT_ID)
                if GoogleAuth.shared.configured {
                Button {
                    busy = true
                    error = nil
                    Task { @MainActor in
                        switch await GoogleAuth.shared.signIn() {
                        case .success(let r): app.setAccount(email: r.0, name: r.1)
                        case .failure(let f): if f != .cancelled { error = GoogleAuth.message(f) }
                        }
                        busy = false
                    }
                } label: {
                    HStack(spacing: 12) {
                        Image("ic_google").resizable().frame(width: 20, height: 20)
                        Text(busy ? L("جارٍ الدخول…", "Signing in…") : L("المتابعة باستخدام Google", "Continue with Google"))
                            .font(Fonts.sans(16, .bold)).foregroundColor(Color(hex: 0xFF1E2A44))
                    }
                    .frame(maxWidth: .infinity).padding(.vertical, 15)
                    .background(Capsule().fill(Color.white))
                    .overlay(Capsule().stroke(Color(hex: 0xFFDADCE0), lineWidth: 1))
                    .shadow(color: .black.opacity(0.12), radius: 2, y: 1)
                }
                .disabled(busy)
                if let e = error {
                    Text(e).font(Fonts.sans(13)).foregroundColor(Lib.exam).multilineTextAlignment(.center).padding(.top, 12)
                }
                Spacer().frame(height: 12)
                }
                SignInWithAppleButton(.continue) { req in
                    req.requestedScopes = [.fullName, .email]
                } onCompletion: { r in
                    if case .success(let auth) = r, let cred = auth.credential as? ASAuthorizationAppleIDCredential {
                        let name = [cred.fullName?.givenName, cred.fullName?.familyName].compactMap { $0 }.joined(separator: " ")
                        app.setAccount(email: cred.email ?? UserDefaults.standard.string(forKey: "account_email") ?? L("حساب Apple", "Apple account"),
                                       name: name.isEmpty ? nil : name)
                    } else if case .failure = r {
                        app.say(.error, L("تعذر تسجيل الدخول", "Sign-in failed"))
                    }
                }
                .signInWithAppleButtonStyle(.whiteOutline)
                .frame(height: 52)
                .clipShape(Capsule())
                .shadow(color: .black.opacity(0.12), radius: 2, y: 1)
                Spacer().frame(height: 18)
                Button { app.skipLogin() } label: {
                    Text(L("ليس الآن", "Not now")).font(Fonts.sans(15, .bold)).foregroundColor(Lib.muted)
                        .padding(.horizontal, 24).padding(.vertical, 10)
                }
            }
            .frame(maxWidth: 420)
            .padding(.horizontal, 28)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            Text(L("ملاحظاتك تبقى على جهازك. نستخدم اسمك وبريدك فقط.", "Your notes stay on your device. We only use your name and email."))
                .font(Fonts.sans(12)).foregroundColor(Lib.muted).multilineTextAlignment(.center)
                .padding(.horizontal, 28).padding(.vertical, 20)
        }
    }
}
