import Foundation
import WidgetKit

/// يكتب ما تعرضه أداة الشاشة الرئيسية في مجموعة التطبيق المشتركة، ويطلب تحديثها.
enum WidgetBridge {
    static let group = "group.com.omarabukarsh.hamish"
    private static var lastReload: Date = .distantPast

    static func update(force: Bool = false) {
        guard let d = UserDefaults(suiteName: group) else { return }
        let c = AppController.shared
        let today = c.stats[c.todayKey()] ?? 0
        d.set(Int(today), forKey: "w_today")
        d.set(c.goalMinutes * 60_000, forKey: "w_goal")
        d.set(c.english, forKey: "w_en")
        // أقرب امتحان
        let now = Int(Date().timeIntervalSince1970 / 86400)
        var exam = ""
        if let best = c.examDates.compactMap({ k, v -> (String, Int)? in v - now >= 0 ? (k, v - now) : nil }).min(by: { $0.1 < $1.1 }) {
            switch best.1 {
            case 0: exam = L("امتحان \(best.0) اليوم", "\(best.0) exam today")
            case 1: exam = L("امتحان \(best.0) غدًا", "\(best.0) exam tomorrow")
            default: exam = L("امتحان \(best.0) بعد \(best.1) يومًا", "\(best.0) exam in \(best.1) days")
            }
        }
        d.set(exam, forKey: "w_exam")
        let last = c.library.filter { !$0.locked && $0.lastOpened > 0 }.max { $0.lastOpened < $1.lastOpened }
        d.set(last?.name ?? "", forKey: "w_last")
        // التحديث المتكرر (وقت القراءة) مرة كل دقيقتين على الأكثر
        if force || Date().timeIntervalSince(lastReload) > 120 {
            lastReload = Date()
            WidgetCenter.shared.reloadAllTimelines()
        }
    }

    /// زر «متابعة» في الأداة: يفتح آخر مستند.
    static func openLast() {
        let c = AppController.shared
        if let last = c.library.filter({ !$0.locked && $0.lastOpened > 0 }).max(by: { $0.lastOpened < $1.lastOpened }) {
            c.openDocument(last.id)
        }
    }
}
