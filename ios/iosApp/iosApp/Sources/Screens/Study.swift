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

struct StatsView: View {
    @EnvironmentObject var app: AppController
    @ObservedObject var pm = Premium.shared
    @State private var month = Date()
    @State private var goalOpen = false
    @Environment(\.dismiss) private var dismiss

    private struct DayStat: Identifiable { let id: Int; let day: Int; let minutes: Double; let met: Bool }

    var body: some View {
        let cal = Calendar.current
        let range = cal.range(of: .day, in: .month, for: month) ?? 1..<31
        let comps = cal.dateComponents([.year, .month], from: month)
        let goalMs = Int64(app.goalMinutes) * 60_000
        let days: [DayStat] = range.map { d in
            var c = comps
            c.day = d
            let date = cal.date(from: c) ?? Date()
            let ms = app.stats[AppController.dayKey(date)] ?? 0
            return DayStat(id: d, day: d, minutes: Double(ms) / 60_000, met: ms >= goalMs)
        }
        let total = days.reduce(0) { $0 + $1.minutes }
        let studied = days.filter { $0.minutes >= 1 }.count
        let goalDays = days.filter { $0.met }.count
        let todayMs = app.stats[app.todayKey()] ?? 0
        let monthPrefix = String(AppController.dayKey(month).prefix(7))
        let pomos = app.stats.filter { $0.key.hasPrefix("pomo|" + monthPrefix) }.values.reduce(0, +)

        SheetScaffold(title: L("إحصاءات الدراسة", "Study stats")) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // اليوم
                    HStack(spacing: 16) {
                        GoalRing(size: 74, line: 7, track: Palette.divider)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(L("اليوم \(formatReading(todayMs))", "Today \(formatReading(todayMs))")).font(Fonts.display(22)).foregroundColor(Palette.navy)
                            Text(todayMs >= goalMs ? L("حققت هدف اليوم", "You reached today’s goal") : L("باقي \(formatReading(goalMs - todayMs)) لهدف اليوم", "\(formatReading(goalMs - todayMs)) left for today’s goal"))
                                .font(Fonts.sans(13)).foregroundColor(Palette.textSecondary)
                            HStack(spacing: 6) {
                                Text(L("هدفك اليومي: \(formatReading(goalMs))", "Your daily goal: \(formatReading(goalMs))")).font(Fonts.sans(13)).foregroundColor(Palette.textPrimary)
                                Button(L("تغيير", "Change")) { goalOpen = true }.font(Fonts.sans(13, .bold)).foregroundColor(Palette.goldDeep)
                            }
                            let streak = app.studyStreak()
                            Text(streak > 0 ? L("🔥 سلسلة \(streak) \(streak == 1 ? "يوم" : "أيام")", "🔥 \(streak)-day streak") : L("ابدأ سلسلة دراسة اليوم", "Start a study streak today"))
                                .font(Fonts.sans(13, .bold)).foregroundColor(Lib.accentDeep)
                        }
                    }
                    // الشهر
                    HStack {
                        Button { month = cal.date(byAdding: .month, value: -1, to: month)! } label: { Text(app.english ? "‹" : "›").font(.system(size: 26)).foregroundColor(Palette.navy) }
                            .accessibilityLabel(L("الشهر السابق", "Previous month"))
                        Text(monthTitle(month)).font(Fonts.sans(16, .bold)).foregroundColor(Palette.textPrimary)
                        Button { month = cal.date(byAdding: .month, value: 1, to: month)! } label: { Text(app.english ? "›" : "‹").font(.system(size: 26)).foregroundColor(Palette.navy) }
                            .accessibilityLabel(L("الشهر التالي", "Next month"))
                        Spacer()
                    }
                    HStack(spacing: 10) {
                        tile(L("مجموع الشهر", "Month total"), formatReading(Int64(total * 60_000)))
                        tile(L("أيام الدراسة", "Study days"), "\(studied)")
                        tile(L("حققت الهدف", "Goal reached"), L("\(goalDays) يوم", "\(goalDays) days"))
                        tile(L("متوسط يوم الدراسة", "Average study day"), formatReading(studied == 0 ? 0 : Int64(total / Double(studied) * 60_000)))
                        tile(L("جلسات التركيز", "Focus sessions"), "\(pomos)")
                    }
                    if pm.active {
                        Text(L("وقت الدراسة اليومي", "Daily study time")).font(Fonts.sans(15, .bold)).foregroundColor(Palette.textPrimary)
                        Chart(days) { d in
                            BarMark(x: .value("day", d.day), y: .value("min", d.minutes))
                                .foregroundStyle(d.met ? Lib.accentDeep : Palette.navy.opacity(0.55))
                                .cornerRadius(3)
                            RuleMark(y: .value("goal", Double(app.goalMinutes))).foregroundStyle(Palette.gold).lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 4]))
                        }
                        .frame(height: 200)
                        Text(L("المواد هذا الشهر", "Subjects this month")).font(Fonts.sans(15, .bold)).foregroundColor(Palette.textPrimary)
                        let subjects = subjectReport(monthPrefix)
                        if subjects.isEmpty {
                            Text(L("لا توجد قراءة مسجلة في هذا الشهر.", "No reading recorded this month.")).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary)
                        } else {
                            ForEach(subjects, id: \.0) { item in
                                HStack {
                                    Art(name: "ic_folder_yellow", size: 20)
                                    Text(item.0).font(Fonts.sans(14, .bold)).foregroundColor(Palette.textPrimary)
                                    Spacer()
                                    Text(formatReading(item.1)).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary)
                                }
                                GeometryReader { g in
                                    Capsule().fill(Palette.navy.opacity(0.7)).frame(width: g.size.width * CGFloat(item.1) / CGFloat(max(1, subjects.first!.1)), height: 6)
                                }
                                .frame(height: 6)
                            }
                        }
                    } else {
                        PremiumTeaser(title: L("افتح الإحصاءات الكاملة", "Unlock full stats"), desc: L("مخطط الشهر وتقرير وقتك في كل مادة.", "The monthly chart and a report of your time in each subject."))
                    }
                }
                .padding(.horizontal, 22).padding(.bottom, 24)
            }
        }
        .sheet(isPresented: $goalOpen) { GoalSheet().environmentObject(app) }
    }

    private func monthTitle(_ d: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: app.english ? "en" : "ar")
        f.dateFormat = "LLLL yyyy"
        return f.string(from: d)
    }

    private func tile(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(value).font(Fonts.display(17)).foregroundColor(Palette.navy).lineLimit(1).minimumScaleFactor(0.7)
            Text(label).font(Fonts.sans(11)).foregroundColor(Palette.textSecondary).lineLimit(2)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 14).fill(Color.white))
        .roundedBorder(Palette.divider, radius: 14)
    }

    private func subjectReport(_ prefix: String) -> [(String, Int64)] {
        var m: [String: Int64] = [:]
        for (k, v) in app.stats where k.hasPrefix("f|") {
            let parts = k.split(separator: "|", omittingEmptySubsequences: false)
            guard parts.count == 3, parts[2].hasPrefix(prefix) else { continue }
            m[String(parts[1]), default: 0] += v
        }
        return m.sorted { $0.value > $1.value }.map { ($0.key, $0.value) }
    }
}

private struct GoalSheet: View {
    @EnvironmentObject var app: AppController
    @State private var hours = 1
    @State private var minutes = 0
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        SheetScaffold(title: L("الهدف اليومي", "Daily goal")) {
            VStack(spacing: 14) {
                HStack {
                    Stepper(L("\(hours) ساعات", "\(hours) hours"), value: $hours, in: 0...24).font(Fonts.sans(15))
                }
                HStack {
                    Stepper(L("\(minutes) دقائق", "\(minutes) minutes"), value: $minutes, in: 0...55, step: 5).font(Fonts.sans(15))
                }
                Text(L("الهدف: \(formatReading(Int64(hours * 60 + minutes) * 60_000)) يوميًا", "Goal: \(formatReading(Int64(hours * 60 + minutes) * 60_000)) a day"))
                    .font(Fonts.sans(13)).foregroundColor(Palette.textSecondary)
                HStack {
                    Spacer()
                    NavyButton(label: L("إلغاء", "Cancel"), filled: false) { dismiss() }
                    NavyButton(label: L("حفظ", "Save")) { app.setGoal(max(5, hours * 60 + minutes)); dismiss() }
                }
            }
            .padding(.horizontal, 22)
        }
        .onAppear { hours = app.goalMinutes / 60; minutes = (app.goalMinutes % 60) / 5 * 5 }
        .presentationDetents([.height(300)])
    }
}

// ---------------------------------------------------------------- تسجيل الدخول

/// أول فتح للتطبيق: تسجيل الدخول بحساب Apple (يمكن تخطيه).
struct SignInScreen: View {
    @EnvironmentObject var app: AppController

    var body: some View {
        ZStack {
            LinearGradient(colors: [Color(hex: 0xFFFFFDF7), Color(hex: 0xFFFCEBC2)], startPoint: .top, endPoint: .bottom).ignoresSafeArea()
            VStack(spacing: 0) {
                AppLogo(size: 110, corner: 28).shadow(color: Pw.goldDeep.opacity(0.25), radius: 16, y: 6)
                Spacer().frame(height: 22)
                Text(L("هامش", "Hamish")).font(Fonts.display(44)).foregroundColor(Pw.ink)
                Spacer().frame(height: 10)
                Text(L("ملاحظاتك وملفات PDF وأدوات المذاكرة في مكان واحد.\nسجّل الدخول لربط حسابك، أو تخطَّ وابدأ مباشرة.",
                       "Your notes, PDFs and study tools in one place.\nSign in to link your account, or skip and start right away."))
                    .font(Fonts.sans(16)).foregroundColor(Pw.muted).multilineTextAlignment(.center).lineSpacing(6)
                Spacer().frame(height: 34)
                SignInWithAppleButton(.continue) { req in
                    req.requestedScopes = [.fullName, .email]
                } onCompletion: { r in
                    if case .success(let auth) = r, let cred = auth.credential as? ASAuthorizationAppleIDCredential {
                        let name = [cred.fullName?.givenName, cred.fullName?.familyName].compactMap { $0 }.joined(separator: " ")
                        app.setAccount(email: cred.email ?? UserDefaults.standard.string(forKey: "account_email") ?? L("حساب Apple", "Apple account"),
                                       name: name.isEmpty ? nil : name)
                    } else if case .failure = r {
                        app.message(L("تعذر تسجيل الدخول", "Sign-in failed"))
                    }
                }
                .signInWithAppleButtonStyle(.black)
                .frame(width: 320, height: 52)
                .clipShape(Capsule())
                Spacer().frame(height: 10)
                Button { app.skipLogin() } label: {
                    Text(L("ليس الآن", "Not now")).font(Fonts.sans(15, .bold)).foregroundColor(Pw.muted).frame(width: 320, height: 46)
                }
                Spacer().frame(height: 18)
                Text(L("ملاحظاتك تبقى على جهازك. نستخدم اسمك وبريدك فقط.", "Your notes stay on your device. We only use your name and email."))
                    .font(Fonts.sans(12)).foregroundColor(Pw.muted)
            }
            .padding(30)
        }
    }
}
