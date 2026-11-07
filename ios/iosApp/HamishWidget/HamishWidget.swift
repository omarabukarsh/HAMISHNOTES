import WidgetKit
import SwiftUI

// أداة الشاشة الرئيسية (نفس StudyWidget في أندرويد): وقت دراسة اليوم مقابل الهدف،
// أقرب امتحان، وزر لمتابعة آخر مستند. التطبيق يكتب البيانات في مجموعة التطبيق المشتركة.

let appGroup = "group.com.omarabukarsh.hamish"

struct StudyEntry: TimelineEntry {
    let date: Date
    let todayMs: Int64
    let goalMs: Int64
    let exam: String
    let last: String
    let english: Bool
}

private func L(_ ar: String, _ en: String, _ english: Bool) -> String { english ? en : ar }

private func reading(_ ms: Int64, _ en: Bool) -> String {
    let m = Int(ms / 60_000)
    if m < 60 { return en ? "\(m) min" : "\(m) د" }
    let h = m / 60, r = m % 60
    if r == 0 { return en ? "\(h) h" : "\(h) س" }
    return en ? "\(h) h \(r) min" : "\(h) س \(r) د"
}

struct StudyProvider: TimelineProvider {
    func load() -> StudyEntry {
        let d = UserDefaults(suiteName: appGroup)
        return StudyEntry(date: Date(),
                          todayMs: Int64(d?.integer(forKey: "w_today") ?? 0),
                          goalMs: Int64(d?.integer(forKey: "w_goal") ?? 3_600_000),
                          exam: d?.string(forKey: "w_exam") ?? "",
                          last: d?.string(forKey: "w_last") ?? "",
                          english: d?.bool(forKey: "w_en") ?? false)
    }
    func placeholder(in context: Context) -> StudyEntry {
        StudyEntry(date: Date(), todayMs: 38 * 60_000, goalMs: 3_600_000, exam: "امتحان علم الأدوية بعد 12 يومًا", last: "المضادات الحيوية", english: false)
    }
    func getSnapshot(in context: Context, completion: @escaping (StudyEntry) -> Void) { completion(context.isPreview ? placeholder(in: context) : load()) }
    func getTimeline(in context: Context, completion: @escaping (Timeline<StudyEntry>) -> Void) {
        completion(Timeline(entries: [load()], policy: .after(Date().addingTimeInterval(30 * 60))))
    }
}

struct StudyWidgetView: View {
    let e: StudyEntry
    @Environment(\.widgetFamily) var family

    var body: some View {
        let en = e.english
        let p = min(1, Double(e.todayMs) / Double(max(1, e.goalMs)))
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Image(systemName: "flame.fill").foregroundColor(Color(red: 0.91, green: 0.47, blue: 0.24))
                Text(L("هامش", "Hamish", en)).font(.system(size: 13, weight: .heavy)).foregroundColor(Color(red: 0.96, green: 0.79, blue: 0.36))
                Spacer()
            }
            Text(L("اليوم \(reading(e.todayMs, en)) من \(reading(e.goalMs, en))", "Today \(reading(e.todayMs, en)) of \(reading(e.goalMs, en))", en))
                .font(.system(size: family == .systemSmall ? 15 : 17, weight: .bold)).foregroundColor(.white).lineLimit(2)
            GeometryReader { g in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color.white.opacity(0.15))
                    Capsule().fill(LinearGradient(colors: [Color(red: 0.91, green: 0.72, blue: 0.24), Color(red: 0.96, green: 0.79, blue: 0.36)], startPoint: .leading, endPoint: .trailing))
                        .frame(width: g.size.width * p)
                }
            }
            .frame(height: 7)
            Text(e.exam.isEmpty ? L("لا امتحانات قريبة", "No upcoming exams", en) : e.exam)
                .font(.system(size: 12)).foregroundColor(Color(red: 0.95, green: 0.6, blue: 0.6)).lineLimit(1)
            Spacer(minLength: 0)
            Link(destination: URL(string: "hamish://open-last")!) {
                Text(e.last.isEmpty ? L("فتح هامش", "Open Hamish", en) : L("متابعة: ", "Continue: ", en) + e.last)
                    .font(.system(size: 12, weight: .bold)).foregroundColor(Color(red: 0.12, green: 0.16, blue: 0.27)).lineLimit(1)
                    .padding(.horizontal, 10).padding(.vertical, 6)
                    .background(Capsule().fill(Color(red: 0.96, green: 0.79, blue: 0.36)))
            }
        }
        .environment(\.layoutDirection, en ? .leftToRight : .rightToLeft)
        .containerBackground(for: .widget) {
            LinearGradient(colors: [Color(red: 0.11, green: 0.21, blue: 0.36), Color(red: 0.05, green: 0.11, blue: 0.21)], startPoint: .topLeading, endPoint: .bottomTrailing)
        }
        .widgetURL(URL(string: "hamish://open"))
    }
}

@main
struct HamishWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "HamishStudy", provider: StudyProvider()) { e in StudyWidgetView(e: e) }
            .configurationDisplayName("هامش")
            .description("وقت دراستك اليوم، وأقرب امتحان، ومتابعة آخر مستند.")
            .supportedFamilies([.systemSmall, .systemMedium])
    }
}
