import SwiftUI
import CoreText

// مكتبة الخطوط (نفس FontLibrary.kt في أندرويد): نحو 1900 خط من Google Fonts (تراخيص SIL OFL أو Apache 2.0)،
// منها خطوط مثبتة مع التطبيق، والباقي يُنزَّل عند الطلب ويُحفظ فيعمل بعدها دون إنترنت.

struct FontEntry: Identifiable, Hashable {
    let name: String
    let cat: Character      // H خط يد، S Serif، N Sans، D زخرفي، M Mono
    let arabic: Bool
    let path: String
    let bundled: String?
    var id: String { name }
}

final class FontStore: ObservableObject {
    static let shared = FontStore()

    @Published private(set) var loading: Set<String> = []
    @Published private(set) var favorites: Set<String> = []
    @Published private(set) var recent: [String] = []
    @Published private(set) var readyVersion = 0
    private var postScript: [String: String] = [:]
    private var failed: Set<String> = []
    private let prefs = UserDefaults.standard

    private let arabicHand: Set<String> = [
        "Aref Ruqaa", "Aref Ruqaa Ink", "Alkalami", "Vibes", "Marhey", "Rakkas", "Lalezar", "Blaka", "Blaka Ink",
        "Blaka Hollow", "Reem Kufi", "Reem Kufi Fun", "Reem Kufi Ink", "Kufam", "Lemonada", "Gulzar",
        "Noto Nastaliq Urdu", "Amiri Quran", "Mirza", "Katibeh", "Jomhuria", "Badeen Display", "Handjet"
    ]

    let catalog: [FontEntry]
    private let byName: [String: FontEntry]

    private init() {
        var list: [FontEntry] = []
        if let url = Bundle.main.url(forResource: "catalog", withExtension: "json", subdirectory: "fonts"),
           let data = try? Data(contentsOf: url),
           let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] {
            for o in arr {
                guard let n = o["n"] as? String, let c = (o["c"] as? String)?.first, let p = o["p"] as? String else { continue }
                let b = (o["b"] as? String).flatMap { $0.isEmpty ? nil : $0 }
                list.append(FontEntry(name: n, cat: c, arabic: (o["a"] as? Int) == 1, path: p, bundled: b))
            }
        }
        catalog = list
        byName = Dictionary(list.map { ($0.name, $0) }, uniquingKeysWith: { a, _ in a })
        favorites = Set(prefs.stringArray(forKey: "font_fav") ?? [])
        recent = prefs.stringArray(forKey: "font_recent") ?? []
    }

    func entry(_ name: String) -> FontEntry? { byName[name] }
    func isArabicHand(_ e: FontEntry) -> Bool { e.arabic && (e.cat == "H" || arabicHand.contains(e.name)) }

    private var dir: URL {
        let d = Repository.shared.root.appendingPathComponent("fonts", isDirectory: true)
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }

    private func fileOf(_ e: FontEntry) -> URL {
        let last = e.path.components(separatedBy: "/").last ?? e.name
        let safe = String(last.map { $0.isLetter || $0.isNumber || "_.-".contains($0) ? $0 : "_" })
        return dir.appendingPathComponent(safe)
    }

    private func sourceURL(_ e: FontEntry) -> URL? {
        if let b = e.bundled { return Bundle.main.url(forResource: b, withExtension: nil, subdirectory: "fonts/bundled") }
        let f = fileOf(e)
        return FileManager.default.fileExists(atPath: f.path) ? f : nil
    }

    func isReady(_ name: String) -> Bool {
        if name.isEmpty { return true }
        guard let e = byName[name] else { return false }
        return sourceURL(e) != nil
    }

    func isLoading(_ name: String) -> Bool { loading.contains(name) }

    /// يسجّل ملف الخط في النظام ويعيد اسمه (PostScript) لإنشاء UIFont.
    private func register(_ name: String) -> String? {
        if let p = postScript[name] { return p }
        guard let e = byName[name], let url = sourceURL(e),
              let provider = CGDataProvider(url: url as CFURL), let cg = CGFont(provider),
              let ps = cg.postScriptName as String? else { return nil }
        if UIFont(name: ps, size: 12) == nil {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
        postScript[name] = ps
        return ps
    }

    /// الخط بحجم معين؛ nil = غير متوفر بعد.
    func uiFont(_ name: String, size: CGFloat) -> UIFont? {
        guard !name.isEmpty, let ps = register(name) else { return nil }
        return UIFont(name: ps, size: size)
    }

    func font(_ name: String, size: CGFloat) -> Font? {
        guard !name.isEmpty, let ps = register(name) else { return nil }
        return Font.custom(ps, fixedSize: size)
    }

    func toggleFavorite(_ name: String) {
        if favorites.contains(name) { favorites.remove(name) } else { favorites.insert(name) }
        prefs.set(Array(favorites), forKey: "font_fav")
    }

    func markUsed(_ name: String) {
        guard !name.isEmpty else { return }
        recent = Array(([name] + recent.filter { $0 != name }).prefix(24))
        prefs.set(recent, forKey: "font_recent")
    }

    /// ينزّل الخط إن لم يكن موجودًا. true = جاهز.
    @discardableResult
    @MainActor
    func ensure(_ name: String, retryFailed: Bool = true) async -> Bool {
        if isReady(name) { return true }
        guard let e = byName[name] else { return false }
        if !retryFailed && failed.contains(name) { return false }
        if loading.contains(name) { return false }
        loading.insert(name)
        let dest = fileOf(e)
        let enc = e.path.components(separatedBy: "/").map { $0.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? $0 }.joined(separator: "/")
        let urls = ["https://raw.githubusercontent.com/google/fonts/main/\(enc)", "https://cdn.jsdelivr.net/gh/google/fonts@main/\(enc)"]
        var ok = false
        for u in urls {
            guard let url = URL(string: u) else { continue }
            do {
                let (data, resp) = try await URLSession.shared.data(from: url)
                guard (resp as? HTTPURLResponse)?.statusCode == 200, data.count > 1000,
                      let provider = CGDataProvider(data: data as CFData), CGFont(provider) != nil else { continue }
                try data.write(to: dest, options: .atomic)
                ok = true
                break
            } catch { continue }
        }
        loading.remove(name)
        if ok { failed.remove(name); readyVersion += 1 } else { failed.insert(name) }
        return ok
    }
}

/// مكتبة الخطوط: فئات وبحث ومفضلة، ومعاينة كل خط بنص المستخدم.
struct FontLibrarySheet: View {
    let sample: String
    let current: String
    let onPick: (String) -> Void
    @ObservedObject private var store = FontStore.shared
    @EnvironmentObject var app: AppController
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var tab = FontStore.shared.recent.isEmpty ? "hand" : "recent"

    private let tabs = ["fav", "recent", "hand", "arhand", "ar", "S", "N", "D", "M", "all"]

    private func label(_ t: String) -> String {
        switch t {
        case "fav": return L("★ المفضلة", "★ Favorites")
        case "recent": return L("المستعملة", "Recent")
        case "hand": return L("خط يد", "Handwriting")
        case "arhand": return L("خط يد عربي", "Arabic handwriting")
        case "ar": return L("عربي", "Arabic")
        case "S": return "Serif"
        case "N": return "Sans"
        case "D": return L("زخرفي", "Display")
        case "M": return "Mono"
        default: return L("الكل", "All")
        }
    }

    private var list: [FontEntry] {
        let q = query.trimmingCharacters(in: .whitespaces)
        let cat = store.catalog
        let base: [FontEntry]
        switch tab {
        case "fav": base = cat.filter { store.favorites.contains($0.name) }
        case "recent": base = store.recent.compactMap { store.entry($0) }
        case "hand": base = cat.filter { $0.cat == "H" }
        case "arhand": base = cat.filter { store.isArabicHand($0) }
        case "ar": base = cat.filter { $0.arabic }
        case "all": base = cat
        default: base = cat.filter { $0.cat == tab.first }
        }
        let filtered = q.isEmpty ? base : cat.filter { $0.name.localizedCaseInsensitiveContains(q) }
        if tab == "recent" && q.isEmpty { return filtered }
        return filtered.sorted { rank($0) < rank($1) }
    }

    private func rank(_ e: FontEntry) -> Int { e.bundled != nil ? 0 : (store.isReady(e.name) ? 1 : 2) }

    private var text: String {
        let t = sample.trimmingCharacters(in: .whitespacesAndNewlines)
        let first = (t.isEmpty ? L("النص الذي كتبته", "Your text") : t).components(separatedBy: "\n").first ?? ""
        return String(first.prefix(48))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(L("مكتبة الخطوط", "Font library")).font(Fonts.display(20)).foregroundColor(Palette.navy)
                Spacer()
                Button(L("الخط الافتراضي", "Default font")) { onPick(""); dismiss() }.foregroundColor(Palette.navy)
                Button(L("إغلاق", "Close")) { dismiss() }.foregroundColor(Palette.textSecondary).padding(.leading, 8)
            }
            .font(Fonts.sans(14))
            TextField(L("ابحث عن خط… (\(store.catalog.count))", "Search fonts… (\(store.catalog.count))"), text: $query)
                .font(Fonts.sans(15)).padding(10)
                .background(RoundedRectangle(cornerRadius: 12).fill(Color.white))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.divider, lineWidth: 1))
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(tabs, id: \.self) { t in
                        ChoiceChip(label: label(t), selected: tab == t && query.isEmpty) { tab = t; query = "" }
                    }
                }
            }
            let items = list
            if items.isEmpty {
                Text(tab == "fav" ? L("اضغط ☆ على أي خط ليظهر هنا", "Tap ☆ on any font to keep it here") : L("لا توجد نتائج", "No results"))
                    .font(Fonts.sans(14)).foregroundColor(Palette.textSecondary).frame(maxWidth: .infinity, minHeight: 160)
            } else {
                ScrollView {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 250), spacing: 8)], spacing: 8) {
                        ForEach(items) { e in
                            FontCard(entry: e, sample: text, selected: e.name == current) {
                                Task {
                                    if await store.ensure(e.name) {
                                        store.markUsed(e.name)
                                        onPick(e.name)
                                        dismiss()
                                    } else {
                                        app.message(L("تعذر تنزيل الخط. تأكد من الإنترنت", "Could not download the font. Check your connection"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        .padding(16)
        .background(Palette.background.ignoresSafeArea())
    }
}

private struct FontCard: View {
    let entry: FontEntry
    let sample: String
    let selected: Bool
    let action: () -> Void
    @ObservedObject private var store = FontStore.shared

    var body: some View {
        let ready = store.isReady(entry.name)
        let fam = ready ? store.font(entry.name, size: 21) : nil
        Button(action: action) {
            VStack(alignment: .leading, spacing: 4) {
                Group {
                    if let f = fam {
                        Text(sample).font(f).foregroundColor(Palette.textPrimary)
                    } else {
                        Text(entry.name).font(Fonts.sans(16)).foregroundColor(Palette.textSecondary)
                    }
                }
                .lineLimit(1)
                .frame(maxWidth: .infinity, minHeight: 34, alignment: .leading)
                HStack(spacing: 6) {
                    Text(entry.name).font(Fonts.sans(11.5, .bold)).foregroundColor(Palette.textSecondary).lineLimit(1)
                    Spacer(minLength: 0)
                    if store.isLoading(entry.name) {
                        ProgressView().scaleEffect(0.6).frame(width: 14, height: 14)
                    } else if entry.bundled != nil {
                        tag(L("مثبت", "Built-in"), Palette.navySoft, Palette.navy)
                    } else if ready {
                        tag(L("منزّل", "Downloaded"), Palette.navySoft, Palette.navy)
                    } else {
                        tag(L("⤓ تنزيل", "⤓ Get"), Palette.goldSoft, Palette.goldDeep)
                    }
                    Button { store.toggleFavorite(entry.name) } label: {
                        Text(store.favorites.contains(entry.name) ? "★" : "☆").font(.system(size: 17)).foregroundColor(Palette.gold).padding(.horizontal, 4)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 12).padding(.vertical, 10)
            .background(RoundedRectangle(cornerRadius: 12).fill(selected ? Palette.navySoft : Color.white))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(selected ? Palette.navy : Palette.divider, lineWidth: selected ? 2 : 1))
        }
        .buttonStyle(.plain)
    }

    private func tag(_ t: String, _ bg: Color, _ fg: Color) -> some View {
        Text(t).font(Fonts.sans(10.5, .bold)).foregroundColor(fg).padding(.horizontal, 7).padding(.vertical, 2)
            .background(RoundedRectangle(cornerRadius: 7).fill(bg))
    }
}

/// شريط اختيار سريع: الخطوط المستعملة مؤخرًا ثم المثبتة.
struct QuickFontRow: View {
    @Binding var font: String
    var extra: [String] = []
    @ObservedObject private var store = FontStore.shared

    var body: some View {
        let quick = Array(NSOrderedSet(array: extra + store.recent + store.catalog.filter { $0.bundled != nil }.map { $0.name }).array as? [String] ?? [])
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(Array(quick.prefix(16)), id: \.self) { n in
                    let on = n == font
                    Button { font = n } label: {
                        Text(n).font(store.font(n, size: 15) ?? Fonts.sans(15)).foregroundColor(on ? .white : Palette.textPrimary)
                            .padding(.horizontal, 10).padding(.vertical, 6)
                            .background(RoundedRectangle(cornerRadius: 10).fill(on ? Palette.navy : Palette.navySoft))
                    }
                }
            }
        }
    }
}
