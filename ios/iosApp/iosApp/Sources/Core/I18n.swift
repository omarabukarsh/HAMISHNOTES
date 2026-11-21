import SwiftUI

/// لغة الواجهة: العربية (الافتراضية) أو الإنجليزية. تُختار من الإعدادات.
enum I18n {
    static var english = false
}

/// نص الواجهة باللغة المختارة.
func L(_ ar: String, _ en: String) -> String { I18n.english ? en : ar }

/// وقت القراءة بصيغة مختصرة: «45 د»، «2 س 15 د».
func formatReading(_ ms: Int64) -> String {
    let m = ms / 60_000
    if m < 1 { return L("أقل من دقيقة", "under a minute") }
    let h = m / 60, mm = m % 60
    if h == 0 { return L("\(mm) د", "\(mm) min") }
    if mm == 0 { return L("\(h) س", "\(h) h") }
    return L("\(h) س \(mm) د", "\(h) h \(mm) min")
}

/// صيغة العدد العربية: مستند واحد، مستندان، 3 مستندات، 11 مستندًا.
func docsCount(_ n: Int) -> String {
    if n == 0 { return L("لا مستندات", "No documents") }
    if n == 1 { return L("مستند واحد", "1 document") }
    if n == 2 { return L("مستندان", "2 documents") }
    if (3...10).contains(n % 100) { return L("\(n) مستندات", "\(n) documents") }
    return L("\(n) مستندًا", "\(n) documents")
}

func pagesCount(_ n: Int) -> String {
    if n == 1 { return L("صفحة واحدة", "1 page") }
    if n == 2 { return L("صفحتان", "2 pages") }
    if (3...10).contains(n % 100) { return L("\(n) صفحات", "\(n) pages") }
    return L("\(n) صفحة", "\(n) pages")
}

func cardsCount(_ n: Int) -> String {
    if n == 1 { return L("بطاقة واحدة", "1 card") }
    if n == 2 { return L("بطاقتان", "2 cards") }
    if (3...10).contains(n % 100) { return L("\(n) بطاقات", "\(n) cards") }
    return L("\(n) بطاقة", "\(n) cards")
}

func formatSize(_ b: Int64) -> String {
    if b >= 1024 * 1024 { return String(format: "%.1f MB", Double(b) / 1048576.0) }
    return "\(max(1, b / 1024)) KB"
}

func nowMs() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

/// «اليوم»، «أمس»، «قبل 3 أيام»، أو التاريخ.
func formatRelative(_ ts: Int64) -> String {
    if ts <= 0 { return "—" }
    let cal = Calendar.current
    let d = Date(timeIntervalSince1970: Double(ts) / 1000)
    let days = cal.dateComponents([.day], from: cal.startOfDay(for: d), to: cal.startOfDay(for: Date())).day ?? 0
    if days <= 0 { return L("اليوم", "today") }
    if days == 1 { return L("أمس", "yesterday") }
    if days < 7 { return L("قبل \(days) أيام", "\(days) days ago") }
    let f = DateFormatter()
    f.locale = Locale(identifier: I18n.english ? "en" : "ar")
    f.dateFormat = "d MMM yyyy"
    return f.string(from: d)
}

/// «امتحان بعد N يومًا» بكلمات قصيرة.
func examLabel(_ days: Int) -> String {
    switch days {
    case 0: return L("الامتحان اليوم", "Exam today")
    case 1: return L("الامتحان غدًا", "Exam tomorrow")
    default: return L("الامتحان بعد \(days) يومًا", "Exam in \(days) days")
    }
}

func mmss(_ ms: Int64) -> String {
    let s = max(0, ms / 1000)
    return String(format: "%d:%02d", s / 60, s % 60)
}

/// اسم ملف آمن.
func safeFileName(_ s: String) -> String {
    let bad = CharacterSet(charactersIn: "\\/:*?\"<>|")
    let t = s.components(separatedBy: bad).joined(separator: "-").trimmingCharacters(in: .whitespaces)
    return t.isEmpty ? L("مستند", "Document") : t
}
