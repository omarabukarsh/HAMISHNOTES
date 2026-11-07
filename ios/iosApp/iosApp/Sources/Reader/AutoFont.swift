import SwiftUI
import PencilKit
import Vision

// «Aa تلقائي» و«تغيير الخط» (نفس AutoFont.kt وChangeFont.kt في أندرويد):
// الكتابة اليدوية تتحول إلى الخط المختار بعد إنهاء الجملة — عند التوقف عن الكتابة لحظة،
// أو فورًا عند بدء سطر جديد. النص يوضع مكان الكتابة بلونها وبحجم حروفها تقريبًا،
// والكتابة الأصلية تُحفظ معه («رجوع لخط يدي»).

enum HwLang: Int { case arabic = 0, english = 1 }

/// يقرأ خط اليد من خطوط PencilKit (بنقاط الصفحة) بأداة Vision على الجهاز.
enum HandwritingReader {
    /// الأسطر المقروءة من الأعلى للأسفل.
    static func read(_ strokes: [PKStroke], lang: HwLang?) async -> [String] {
        guard !strokes.isEmpty else { return [] }
        let d = PKDrawing(strokes: strokes.map { s in
            var c = s
            c.ink = PKInk(s.ink.inkType == .marker ? .monoline : s.ink.inkType, color: .black)
            return c
        })
        let r = d.bounds.insetBy(dx: -24, dy: -24)
        let scale: CGFloat = max(1, min(4, 1600 / max(1, r.width)))
        let img = UIGraphicsImageRenderer(size: CGSize(width: r.width * scale, height: r.height * scale)).image { ctx in
            UIColor.white.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: r.width * scale, height: r.height * scale))
            d.image(from: r, scale: scale).draw(in: CGRect(x: 0, y: 0, width: r.width * scale, height: r.height * scale))
        }
        guard let cg = img.cgImage else { return [] }
        return await withCheckedContinuation { cont in
            DispatchQueue.global(qos: .userInitiated).async {
                let req = VNRecognizeTextRequest()
                req.recognitionLevel = .accurate
                req.usesLanguageCorrection = true
                let supported = (try? req.supportedRecognitionLanguages()) ?? []
                let langs: [String]
                switch lang {
                case .arabic?: langs = ["ar-SA", "en-US"]
                case .english?: langs = ["en-US"]
                case nil: langs = I18n.english ? ["en-US", "ar-SA"] : ["ar-SA", "en-US"]
                }
                req.recognitionLanguages = langs.filter { supported.contains($0) }
                try? VNImageRequestHandler(cgImage: cg).perform([req])
                let obs = (req.results ?? []).sorted { $0.boundingBox.midY > $1.boundingBox.midY }
                cont.resume(returning: obs.compactMap { $0.topCandidates(1).first?.string }.filter { !$0.isEmpty })
            }
        }
    }
}

/// مكان النص المتحول وحجمه ليأخذ عرض الكتابة اليدوية تقريبًا ويجلس على سطرها.
struct TextFit { let x: CGFloat, y: CGFloat, w: CGFloat, size: CGFloat }

extension DocSession {
    func fitText(page: Int, strokes: [PKStroke], lines: [String], font: String, snap: Bool) -> TextFit {
        let bb = strokes.reduce(CGRect.null) { $0.union($1.renderBounds) }
        let pw = pageSize(page).width
        let n = CGFloat(max(1, lines.count))
        let hLine = max(pw * 0.005, bb.height / n)
        let probe = FontStore.shared.uiFont(font, size: 100) ?? Fonts.ui(100)
        let perUnit = (lines.map { ($0 as NSString).size(withAttributes: [.font: probe]).width }.max() ?? 100) / 100
        let sizeH = hLine / 0.75
        let sizeW = perUnit > 0 ? bb.width / perUnit : sizeH
        let size = min(max(min(max(sizeW, sizeH * 0.55), sizeH * 1.35), pw * 0.01), pw * 0.15)
        let w = min(max(perUnit * size * 1.05 + pw * 0.012, pw * 0.08), pw)
        let rtl = lines.contains { $0.unicodeScalars.contains { (0x0600...0x06FF).contains($0.value) } }
        let x = min(max(0, rtl ? bb.maxX - w : bb.minX), max(0, pw - w))
        var base = bb.minY + hLine * 0.9
        if snap, isBlank(page) {
            let (kind, _) = Templates.parse(data.pages[page].src)
            let size2 = pageSize(page)
            let ys = Templates.lineYs(kind: kind, w: size2.width, h: size2.height)
            if let near = ys.min(by: { abs($0 - base) < abs($1 - base) }) {
                let step = ys.count > 1 ? ys[1] - ys[0] : pw * 0.04
                if abs(near - base) < step * 0.6 { base = near }
            }
        }
        let asc = probe.ascender / 100
        return TextFit(x: x, y: max(0, base - asc * size), w: w, size: size)
    }

    /// يستبدل خطوطًا بمربع نص (والخطوط تُحفظ معه)، خطوةً واحدة في التراجع.
    func inkToTextBox(pageId: String, strokes: [PKStroke], box: TextBox) {
        let old = drawing(for: pageId)
        var nd = old
        nd.strokes = old.strokes.filter { s in !strokes.contains { sameStroke($0, s) } }
        let before = data
        var b = box
        if b.ink == nil { b.ink = PKDrawing(strokes: strokes).dataRepresentation() }
        data.boxes.append(b)
        drawings[pageId] = nd
        dirtyInk.insert(pageId)
        dataDirty = true
        applyInkHook?(pageId, nd)
        inkVersion += 1
        push(.both(.ink(page: pageId, before: old, after: nd), .data(before: before, after: data)))
        scheduleSave()
    }

    /// يعيد مربع نص متحول إلى الكتابة اليدوية الأصلية.
    func textBoxToInk(_ id: String) {
        guard let b = data.boxes.first(where: { $0.id == id }), let raw = b.ink, let ink = try? PKDrawing(data: raw) else { return }
        let old = drawing(for: b.page)
        var nd = old
        nd.strokes.append(contentsOf: ink.strokes)
        let before = data
        data.boxes.removeAll { $0.id == id }
        drawings[b.page] = nd
        dirtyInk.insert(b.page)
        dataDirty = true
        applyInkHook?(b.page, nd)
        inkVersion += 1
        push(.both(.ink(page: b.page, before: old, after: nd), .data(before: before, after: data)))
        scheduleSave()
    }

    /// الخطوط التي ما زالت على الصفحة من قائمة.
    func aliveStrokes(_ pageId: String, _ list: [PKStroke]) -> [PKStroke] {
        let present = drawing(for: pageId).strokes
        return list.filter { s in present.contains { sameStroke($0, s) } }
    }
}

/// مقارنة خطين (PKStroke ليس Equatable): وقت الإنشاء وعدد النقاط والحدود.
func sameStroke(_ a: PKStroke, _ b: PKStroke) -> Bool {
    a.path.creationDate == b.path.creationDate && a.path.count == b.path.count &&
        abs(a.renderBounds.minX - b.renderBounds.minX) < 0.5 && abs(a.renderBounds.minY - b.renderBounds.minY) < 0.5
}

final class AutoFont: ObservableObject {
    static let shared = AutoFont()
    private let prefs = UserDefaults.standard

    @Published var on = false { didSet { prefs.set(on, forKey: "af_on"); if !on { discard() } } }
    @Published var font = "Tajawal" { didSet { prefs.set(font, forKey: "af_font") } }
    /// مدة التوقف قبل التحويل (ملّي ثانية)؛ 0 = عند الضغط على ✓ فقط.
    @Published var delayMs = 2000 { didSet { prefs.set(delayMs, forKey: "af_delay") } }
    @Published var joinLines = true { didSet { prefs.set(joinLines, forKey: "af_join") } }
    @Published var snapToLine = true { didSet { prefs.set(snapToLine, forKey: "af_snap") } }
    @Published var lang: HwLang = .arabic { didSet { prefs.set(lang.rawValue, forKey: "af_lang") } }

    @Published private(set) var pendingCount = 0
    @Published private(set) var preview: (Int, String)? = nil
    @Published private(set) var lastBox: (String, String)? = nil      // (المستند، المربع)

    private weak var session: DocSession?
    private var pageId = ""
    private var pending: [PKStroke] = []
    private var timer: DispatchWorkItem?
    private var penIsDown = false
    private var cancelledId = -1
    private var nextId = 1
    private var lastAuto: (String, String, String)?     // (المستند، الصفحة، المربع)

    private init() {
        on = prefs.bool(forKey: "af_on")
        font = prefs.string(forKey: "af_font") ?? "Tajawal"
        delayMs = prefs.object(forKey: "af_delay") as? Int ?? 2000
        joinLines = prefs.object(forKey: "af_join") as? Bool ?? true
        snapToLine = prefs.object(forKey: "af_snap") as? Bool ?? true
        lang = HwLang(rawValue: prefs.object(forKey: "af_lang") as? Int ?? (I18n.english ? 1 : 0)) ?? .arabic
    }

    func penDown() {
        penIsDown = true
        timer?.cancel()
    }

    func penUp() {
        penIsDown = false
        if !pending.isEmpty && delayMs > 0 { schedule(delayMs) }
    }

    /// خط قلم جديد حُفظ على الصفحة (بنقاط الصفحة).
    func onStroke(_ s: DocSession, pageId pid: String, stroke: PKStroke) {
        guard on, Premium.shared.active, stroke.ink.inkType != .marker, stroke.randomSeed != shapeSeed else { return }
        if !pending.isEmpty && (s !== session || pid != pageId || startsNewLine(stroke, page: s.index(of: pid) ?? 0, s)) { flush() }
        session = s
        pageId = pid
        pending.append(stroke)
        pendingCount = pending.count
        if delayMs > 0 { schedule(delayMs) }
    }

    private func schedule(_ ms: Int) {
        timer?.cancel()
        let w = DispatchWorkItem { [weak self] in
            guard let self = self, !self.penIsDown else { return }
            self.flush()
        }
        timer = w
        DispatchQueue.main.asyncAfter(deadline: .now() + .milliseconds(ms), execute: w)
    }

    private func startsNewLine(_ st: PKStroke, page: Int, _ s: DocSession) -> Bool {
        let pb = pending.reduce(CGRect.null) { $0.union($1.renderBounds) }
        let ib = st.renderBounds
        let gap = max(s.pageSize(page).width * 0.012, pb.height * 0.3)
        return ib.minY > pb.maxY + gap || ib.maxY < pb.minY - gap
    }

    func flush() {
        timer?.cancel()
        timer = nil
        guard let s = session else { return }
        let batch = pending
        let pid = pageId
        pending = []
        pendingCount = 0
        guard !batch.isEmpty else { return }
        Task { @MainActor in await convert(s, pid, batch) }
    }

    func discard() {
        timer?.cancel()
        timer = nil
        pending = []
        pendingCount = 0
        if let p = preview { cancelledId = p.0 }
        preview = nil
    }

    func cancelPreview() {
        if let p = preview { cancelledId = p.0 }
        preview = nil
    }

    @MainActor
    private func convert(_ s: DocSession, _ pid: String, _ batch: [PKStroke]) async {
        await FontStore.shared.ensure(font)
        let lines = await HandwritingReader.read(batch, lang: lang)
        let text = lines.joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        let id = nextId
        nextId += 1
        preview = (id, text)
        try? await Task.sleep(nanoseconds: 900_000_000)
        while penIsDown { try? await Task.sleep(nanoseconds: 60_000_000) }
        if preview?.0 == id { preview = nil }
        if cancelledId == id { return }
        let alive = s.aliveStrokes(pid, batch)
        guard !alive.isEmpty, let page = s.index(of: pid) else { return }
        let bb = alive.reduce(CGRect.null) { $0.union($1.renderBounds) }
        let color = alive.first.map { $0.ink.color.argb } ?? 0xFF1A2230
        let lineTexts = text.components(separatedBy: "\n").filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }

        // فقرة واحدة: السطر الجديد تحت آخر مربع متحول مباشرة يُضاف إليه
        if joinLines, let la = lastAuto, la.0 == s.id, la.1 == pid,
           let prev = s.data.boxes.first(where: { $0.id == la.2 }), prev.font == font {
            let lineH = prev.size * 1.25
            let prevB = prev.y + s.boxHeight(prev)
            let overlapX = min(bb.maxX, prev.x + prev.w + 40) - max(bb.minX, prev.x - 40) > 0
            if overlapX && bb.minY > prevB - lineH * 0.5 && bb.minY < prevB + lineH * 1.6 {
                let pw = s.pageSize(page).width
                let rtl = text.unicodeScalars.contains { (0x0600...0x06FF).contains($0.value) }
                let right = max(prev.x + prev.w, bb.maxX)
                let left = min(prev.x, bb.minX)
                let nw = min(max(right - left, min(prev.w, pw)), pw)
                var merged = prev
                merged.text = prev.text.trimmingCharacters(in: .whitespaces) + " " + lineTexts.joined(separator: " ")
                var ink = (prev.ink.flatMap { try? PKDrawing(data: $0) }) ?? PKDrawing()
                ink.strokes.append(contentsOf: alive)
                merged.ink = ink.dataRepresentation()
                merged.x = min(max(0, rtl ? right - nw : prev.x), max(0, pw - nw))
                merged.w = nw
                s.edit { d in d.boxes.removeAll { $0.id == prev.id } }
                s.inkToTextBox(pageId: pid, strokes: alive, box: merged)
                lastBox = (s.id, prev.id)
                FontStore.shared.markUsed(font)
                return
            }
        }

        let f = s.fitText(page: page, strokes: alive, lines: lineTexts, font: font, snap: snapToLine)
        let box = TextBox(id: UUID().uuidString, page: pid, x: f.x, y: f.y, w: f.w, text: lineTexts.joined(separator: "\n"),
                          color: color, size: f.size, font: font)
        FontStore.shared.markUsed(font)
        s.inkToTextBox(pageId: pid, strokes: alive, box: box)
        lastAuto = (s.id, pid, box.id)
        lastBox = (s.id, box.id)
        try? await Task.sleep(nanoseconds: 3_500_000_000)
        if lastBox?.1 == box.id { lastBox = nil }
    }

    func undoLast(_ s: DocSession) {
        guard let lb = lastBox, lb.0 == s.id else { return }
        lastBox = nil
        s.textBoxToInk(lb.1)
    }
}


/// شريحة الحالة أسفل القارئ: «يتحول…»، أو ✓ للوضع اليدوي، أو «رجوع لخط يدي».
struct AutoFontChip: View {
    @ObservedObject var session: DocSession
    @ObservedObject var af = AutoFont.shared

    var body: some View {
        let last = af.lastBox.flatMap { $0.0 == session.id ? $0 : nil }
        let manual = af.on && af.delayMs == 0 && af.pendingCount > 0
        if af.preview != nil || last != nil || manual {
            HStack(spacing: 6) {
                if let pv = af.preview {
                    ProgressView().scaleEffect(0.7).tint(Palette.navy)
                    Text("«\(pv.1)» ← " + L("يتحول…", "converting…")).font(Fonts.sans(13)).foregroundColor(Palette.textPrimary).lineLimit(1)
                    Button { af.cancelPreview() } label: {
                        Text("✕ " + L("إبقاء بخط يدي", "Keep handwriting")).font(Fonts.sans(13)).foregroundColor(Palette.danger)
                    }
                } else if manual {
                    Button { af.flush() } label: {
                        Text("✓ " + L("حوّل إلى \(af.font)", "Convert to \(af.font)")).font(Fonts.sans(14, .bold)).foregroundColor(Palette.navy)
                    }
                    Button { af.discard() } label: {
                        Text(L("إبقاء بخط يدي", "Keep handwriting")).font(Fonts.sans(14)).foregroundColor(Palette.textSecondary)
                    }
                } else {
                    Text("✓ " + L("تم التحويل", "Converted")).font(Fonts.sans(13, .bold)).foregroundColor(Color(hex: 0xFF1F7A4D))
                    Button { af.undoLast(session) } label: {
                        Text("↶ " + L("رجوع لخط يدي", "Back to handwriting")).font(Fonts.sans(13)).foregroundColor(Palette.navy)
                    }
                }
            }
            .padding(.horizontal, 12).padding(.vertical, 8)
            .background(RoundedRectangle(cornerRadius: 16).fill(Color.white).shadow(color: .black.opacity(0.18), radius: 8, y: 3))
        }
    }
}

/// إعدادات «Aa تلقائي»: الخط، ومتى تتحول الجملة، واللغة، والتشغيل/الإيقاف.
struct AutoFontSheet: View {
    @EnvironmentObject var app: AppController
    @ObservedObject var af = AutoFont.shared
    @ObservedObject var store = FontStore.shared
    @Environment(\.dismiss) private var dismiss
    @State private var font = AutoFont.shared.font
    @State private var delayMs = AutoFont.shared.delayMs
    @State private var join = AutoFont.shared.joinLines
    @State private var snap = AutoFont.shared.snapToLine
    @State private var lang = AutoFont.shared.lang
    @State private var library = false

    var body: some View {
        SheetScaffold(title: "Aa " + L("تحويل الكتابة تلقائياً", "Auto-convert handwriting")) {
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    Text(L("اكتب بالقلم كالعادة، وعندما تنهي الجملة تتحول إلى نص بالخط الذي تختاره، في نفس مكانها وبلونها.",
                           "Write with your pen as usual; when you finish a sentence it turns into text in the font you choose, in the same place and color."))
                        .font(Fonts.sans(13)).foregroundColor(Palette.textSecondary)
                    Text(L("الخط", "Font")).font(Fonts.sans(13, .bold)).foregroundColor(Palette.navy)
                    Text(L("الخلية وحدة الحياة", "The cell is the unit of life"))
                        .font(store.font(font, size: 24) ?? Fonts.sans(24)).foregroundColor(Palette.navy)
                        .frame(maxWidth: .infinity, alignment: .leading).padding(12)
                        .background(RoundedRectangle(cornerRadius: 12).fill(Palette.ivory))
                        .task(id: font) { await store.ensure(font) }
                    QuickFontRow(font: $font, extra: [font])
                    Button { library = true } label: {
                        Text(L("مكتبة الخطوط كاملة (\(store.catalog.count))…", "Full font library (\(store.catalog.count))…")).font(Fonts.sans(14, .bold)).foregroundColor(Palette.goldDeep)
                    }
                    Text(L("متى تتحول الجملة", "When a sentence converts")).font(Fonts.sans(13, .bold)).foregroundColor(Palette.navy).padding(.top, 6)
                    Text(L("وتتحول فوراً عندما تبدأ سطراً جديداً.", "It also converts as soon as you start a new line.")).font(Fonts.sans(12)).foregroundColor(Palette.textSecondary)
                    HStack(spacing: 6) {
                        ForEach([(1000, L("بعد ثانية", "After 1 s")), (2000, L("بعد ثانيتين", "After 2 s")), (3000, L("بعد 3 ثوانٍ", "After 3 s")), (0, L("عند الضغط على ✓", "When I tap ✓"))], id: \.0) { v in
                            ChoiceChip(label: v.1, selected: delayMs == v.0) { delayMs = v.0 }
                        }
                    }
                    Toggle(L("جمع الأسطر المتتالية في فقرة واحدة", "Join consecutive lines into one paragraph"), isOn: $join)
                    Toggle(L("وضع النص على سطر الدفتر", "Sit text on the page's ruled line"), isOn: $snap)
                    Text(L("لغة الكتابة", "Handwriting language")).font(Fonts.sans(13, .bold)).foregroundColor(Palette.navy).padding(.top, 6)
                    HStack(spacing: 6) {
                        ChoiceChip(label: L("عربي", "Arabic"), selected: lang == .arabic) { lang = .arabic }
                        ChoiceChip(label: L("إنجليزي", "English"), selected: lang == .english) { lang = .english }
                    }
                    HStack {
                        if af.on {
                            Button(L("إيقاف", "Turn off")) { af.on = false; dismiss() }.foregroundColor(Palette.danger)
                        } else {
                            Button(L("إلغاء", "Cancel")) { dismiss() }.foregroundColor(Palette.textSecondary)
                        }
                        Spacer()
                        NavyButton(label: af.on ? L("حفظ", "Save") : L("تشغيل", "Turn on")) {
                            af.font = font
                            af.delayMs = delayMs
                            af.joinLines = join
                            af.snapToLine = snap
                            af.lang = lang
                            let was = af.on
                            af.on = true
                            if !was { app.message(L("اكتب جملتك، وستتحول عند إنهائها", "Write your sentence; it converts when you finish")) }
                            dismiss()
                        }
                    }
                    .font(Fonts.sans(15)).padding(.top, 8)
                }
                .font(Fonts.sans(14))
                .tint(Palette.navy)
                .padding(.horizontal, 22).padding(.bottom, 20)
            }
        }
        .sheet(isPresented: $library) {
            FontLibrarySheet(sample: L("الخلية وحدة الحياة", "The cell"), current: font) { if !$0.isEmpty { font = $0 } }.environmentObject(app)
        }
    }
}

/// «تغيير الخط»: تُقرأ الكتابة المحددة، ثم تختار خطًا فيحل نص بهذا الخط محلها في نفس المكان واللون.
struct ChangeFontSheet: View {
    @ObservedObject var session: DocSession
    @EnvironmentObject var app: AppController
    @ObservedObject var store = FontStore.shared
    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @State private var busy = true
    @State private var error: String? = nil
    @State private var font = FontStore.shared.recent.first ?? "Patrick Hand"
    @State private var library = false
    @State private var strokes: [PKStroke] = []
    @State private var pageId = ""

    var body: some View {
        SheetScaffold(title: L("تغيير الخط", "Change font")) {
            VStack(alignment: .leading, spacing: 10) {
                if busy {
                    HStack(spacing: 10) {
                        ProgressView()
                        Text(L("جارٍ قراءة الكتابة…", "Reading your handwriting…")).font(Fonts.sans(14))
                    }
                } else if let e = error {
                    Text(e).font(Fonts.sans(14)).foregroundColor(Palette.textSecondary)
                } else {
                    Text(L("النص المقروء (صحّحه إن لزم)", "Recognized text (fix it if needed)")).font(Fonts.sans(12)).foregroundColor(Palette.textSecondary)
                    TextEditor(text: $text).font(Fonts.sans(16)).frame(minHeight: 70, maxHeight: 140)
                        .padding(6).background(RoundedRectangle(cornerRadius: 12).fill(Color.white))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.divider, lineWidth: 1))
                    Text(text.isEmpty ? "…" : text)
                        .font(store.font(font, size: 24) ?? Fonts.sans(24))
                        .foregroundColor(strokes.first.map { Color($0.ink.color) } ?? Palette.navy)
                        .frame(maxWidth: .infinity, alignment: .leading).padding(12)
                        .background(RoundedRectangle(cornerRadius: 12).fill(Palette.ivory))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.divider, lineWidth: 1))
                        .task(id: font) { await store.ensure(font) }
                    QuickFontRow(font: $font)
                    Button { library = true } label: {
                        Text(L("مكتبة الخطوط كاملة (\(store.catalog.count))…", "Full font library (\(store.catalog.count))…")).font(Fonts.sans(14, .bold)).foregroundColor(Palette.goldDeep)
                    }
                }
                HStack {
                    Button(L("إلغاء", "Cancel")) { dismiss() }.foregroundColor(Palette.textSecondary)
                    Spacer()
                    NavyButton(label: L("تطبيق", "Apply")) { apply() }
                        .disabled(busy || error != nil || text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
                .font(Fonts.sans(15)).padding(.top, 6)
            }
            .padding(.horizontal, 22).padding(.bottom, 20)
        }
        .task { await load() }
        .sheet(isPresented: $library) {
            FontLibrarySheet(sample: text, current: font) { if !$0.isEmpty { font = $0 } }.environmentObject(app)
        }
    }

    @MainActor
    private func load() async {
        guard let sel = session.lassoSel else { busy = false; error = L("لم يُحدَّد شيء", "Nothing selected"); return }
        let d = session.drawing(for: sel.pageId)
        strokes = sel.strokes.filter { d.strokes.indices.contains($0) }.map { d.strokes[$0] }.filter { $0.randomSeed != shapeSeed && $0.ink.inkType != .marker }
        pageId = sel.pageId
        let lines = await HandwritingReader.read(strokes, lang: nil)
        busy = false
        if lines.isEmpty { error = L("لم يُتعرّف على كتابة في التحديد", "No handwriting recognized in the selection") }
        else { text = lines.joined(separator: "\n") }
    }

    private func apply() {
        guard let page = session.index(of: pageId), !strokes.isEmpty else { return }
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let lines = t.components(separatedBy: "\n").filter { !$0.isEmpty }
        let f = session.fitText(page: page, strokes: strokes, lines: lines.isEmpty ? [t] : lines, font: font, snap: AutoFont.shared.snapToLine)
        let box = TextBox(id: UUID().uuidString, page: pageId, x: f.x, y: f.y, w: f.w, text: t,
                          color: strokes.first?.ink.color.argb ?? 0xFF1A2230, size: f.size, font: font)
        FontStore.shared.markUsed(font)
        session.lassoSel = nil
        session.inkToTextBox(pageId: pageId, strokes: strokes, box: box)
        dismiss()
    }
}
