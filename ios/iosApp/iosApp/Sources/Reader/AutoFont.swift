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
        // محرك ML Kit (من حركة القلم) أولًا، كما في أندرويد؛ وVision حتى تجهز حزمة اللغة
        let code: Int = lang.map { $0 == .arabic ? 0 : 1 } ?? AppController.shared.hwLang
        let ok = strokes.filter(HandwritingWords.eligible)
        guard !ok.isEmpty else { return [] }
        if let lines = await InkReader.readLines(ok, lang: code) { return lines }
        return await visionRead(strokes, lang: lang)
    }

    /// قراءة صورة الكتابة بأداة Vision (احتياطية).
    static func visionRead(_ strokes: [PKStroke], lang: HwLang?) async -> [String] {
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

// ---------------------------------------------------------------- الحذف والإضافة بالقلم

extension AutoFont {
    private enum Gesture { case scribble, strike, caret }

    private static func classify(_ p: [CGPoint], size: CGFloat) -> Gesture? {
        guard p.count >= 3 else { return nil }
        let xs = p.map { $0.x }, ys = p.map { $0.y }
        let l = xs.min()!, r = xs.max()!, t = ys.min()!, b = ys.max()!
        let w = r - l, h = b - t
        var len: CGFloat = 0, rev = 0, lastSign = 0, acc: CGFloat = 0
        for i in 1..<p.count {
            let dx = p[i].x - p[i - 1].x, dy = p[i].y - p[i - 1].y
            len += hypot(dx, dy)
            acc += dx
            if abs(acc) > size * 0.12 {
                let sg = acc > 0 ? 1 : -1
                if lastSign != 0 && sg != lastSign { rev += 1 }
                lastSign = sg
                acc = 0
            }
        }
        if w > size * 0.4 && len > w * 2.8 && rev >= 3 && h < size * 2 { return .scribble }
        if h < size * 0.45 && w > size * 0.6 && len < w * 1.25 { return .strike }
        guard let apex = p.min(by: { $0.y < $1.y }) else { return nil }
        if w < size * 1.5 && w > size * 0.15 && h > size * 0.25 && h < size * 1.5 &&
            apex.x > l + w * 0.2 && apex.x < r - w * 0.2 &&
            p.first!.y - t > h * 0.6 && p.last!.y - t > h * 0.6 && len < (w + h) * 2.6 { return .caret }
        return nil
    }

    /// تخطيط نص المربع بنقاط الصفحة (نفس ما يرسمه المربع على الصفحة).
    private static func layout(_ b: TextBox) -> (NSLayoutManager, NSTextContainer, CGFloat) {
        let font = FontStore.shared.uiFont(b.font, size: b.size) ?? Fonts.ui(b.size)
        let storage = NSTextStorage(string: b.text, attributes: [.font: font])
        let lm = NSLayoutManager()
        let tc = NSTextContainer(size: CGSize(width: b.w, height: .greatestFiniteMagnitude))
        tc.lineFragmentPadding = 0
        lm.addTextContainer(tc)
        storage.addLayoutManager(lm)
        lm.ensureLayout(for: tc)
        return (lm, tc, lm.usedRect(for: tc).height)
    }

    private static func offset(_ lm: NSLayoutManager, _ tc: NSTextContainer, _ p: CGPoint, _ n: Int) -> Int {
        var f: CGFloat = 0
        let i = lm.characterIndex(for: p, in: tc, fractionOfDistanceBetweenInsertionPoints: &f)
        return min(n, f > 0.5 ? i + 1 : i)
    }

    /// خط القلم فوق نص متحوّل: شخبطة أو خط فوق كلمة = حذفها، و∧ بين كلمتين = مكان لإضافة.
    /// true = الخط إشارة نُفذت (لا يُحفظ كحبر).
    func gesture(_ s: DocSession, pageId pid: String, stroke: PKStroke) -> Bool {
        guard stroke.ink.inkType != .marker, stroke.randomSeed != shapeSeed else { return false }
        let boxes = s.data.boxes.filter { $0.page == pid && $0.ink != nil && $0.linkDoc == nil }
        guard !boxes.isEmpty else { return false }
        let p = stroke.path.interpolatedPoints(by: .distance(1.5)).map { $0.location.applying(stroke.transform) }
        guard p.count >= 3 else { return false }
        let xs = p.map { $0.x }, ys = p.map { $0.y }
        let l = xs.min()!, r = xs.max()!, t = ys.min()!, b = ys.max()!
        let cx = (l + r) / 2, cy = (t + b) / 2
        if let it = insertTarget, it.docId == s.id, it.pageId == pid, it.area.contains(CGPoint(x: cx, y: cy)) { return false }
        let pw = s.pageSize(s.index(of: pid) ?? 0).width
        for tb in boxes.reversed() {
            let (lm, tc, bh) = Self.layout(tb)
            let lineH = tb.size * 1.25
            if cx < tb.x - pw * 0.01 || cx > tb.x + tb.w + pw * 0.01 || cy < tb.y - lineH * 0.3 || cy > tb.y + bh + lineH * 0.7 { continue }
            guard let kind = Self.classify(p, size: tb.size) else { return false }
            let chars = Array(tb.text)
            switch kind {
            case .scribble, .strike:
                if cy > tb.y + bh { return false }
                if kind == .strike {
                    let gi = lm.glyphIndex(for: CGPoint(x: cx - tb.x, y: cy - tb.y), in: tc)
                    let line = lm.lineFragmentRect(forGlyphAt: gi, effectiveRange: nil)
                    let top = tb.y + line.minY, bot = tb.y + line.maxY
                    if cy < top + (bot - top) * 0.2 || cy > top + (bot - top) * 0.8 { return false }
                }
                let a = Self.offset(lm, tc, CGPoint(x: l + tb.size * 0.1 - tb.x, y: cy - tb.y), chars.count)
                let z = Self.offset(lm, tc, CGPoint(x: r - tb.size * 0.1 - tb.x, y: cy - tb.y), chars.count)
                var st = min(a, z), en = max(a, z)
                if st >= chars.count { return false }
                while st > 0 && !chars[st - 1].isWhitespace { st -= 1 }
                en = max(en, st + 1)
                while en < chars.count && !chars[en - 1].isWhitespace && !chars[en].isWhitespace { en += 1 }
                en = min(en, chars.count)
                if en <= st { return false }
                let nt = (String(chars[0..<st]) + String(chars[en...])).replacingOccurrences(of: "  ", with: " ").trimmingCharacters(in: .whitespaces)
                s.edit { d in
                    if nt.isEmpty { d.boxes.removeAll { $0.id == tb.id } }
                    else if let i = d.boxes.firstIndex(where: { $0.id == tb.id }) { d.boxes[i].text = nt }
                }
                return true
            case .caret:
                guard let apex = p.min(by: { $0.y < $1.y }) else { return false }
                let o = Self.offset(lm, tc, CGPoint(x: apex.x - tb.x, y: t - lineH * 0.35 - tb.y), chars.count)
                var ws = o, we = o
                while ws > 0 && !chars[ws - 1].isWhitespace { ws -= 1 }
                while we < chars.count && !chars[we].isWhitespace { we += 1 }
                let pos = (o - ws < we - o) ? ws : we
                insertTarget = InsertTarget(docId: s.id, pageId: pid, boxId: tb.id, offset: min(max(0, pos), chars.count),
                                            area: CGRect(x: apex.x - pw * 0.18, y: b - lineH * 0.2, width: pw * 0.36, height: lineH * 2.6))
                AppController.shared.message(L("اكتب الإضافة في المربع المنقط", "Write the addition in the dotted box"))
                return true
            }
        }
        return false
    }
}

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
                let k: CGFloat = [0, 0.3, 0.6, 0.9][min(3, max(0, AutoFont.shared.snap))]
                if abs(near - base) < step * k { base = near }
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

    /// يحوّل خطوطًا إلى حبر بشكل الخط (حدود الحروف وملؤها)، خطوةً واحدة في التراجع. يعيد مفتاح الحبر.
    func inkConvert(pageId: String, page: Int, strokes: [PKStroke], lines: [String], font: String, color: UInt32) -> Double? {
        let af = AutoFont.shared
        let pw = pageSize(page).width
        let bb = strokes.reduce(CGRect.null) { $0.union($1.renderBounds) }
        guard !bb.isNull, bb.width.isFinite else { return nil }
        let f = fitText(page: page, strokes: strokes, lines: lines, font: font, snap: af.snap > 0)
        var size = af.unify ? CGFloat(af.unifySize) * pw / 595 : f.size
        let rtl = lines.contains { $0.unicodeScalars.contains { (0x0600...0x06FF).contains($0.value) } }
        var uf = FontStore.shared.uiFont(font, size: size) ?? Fonts.ui(size)
        var baseline = f.y + uf.ascender
        var leftX = bb.minX, rightX = bb.maxX
        // المتابعة على نفس السطر: الكتابة بجانب جملة محوّلة تجلس على سطرها وتأخذ حجمها إن كانت قريبة منه
        if af.continueLine {
            let near = data.converted.filter { $0.page == pageId }.filter { c in
                abs(c.baseline - (bb.minY + bb.height * 0.75)) < c.size * 0.9 && bb.minY < c.baseline + c.size * 0.4
            }.min { abs($0.baseline - baseline) < abs($1.baseline - baseline) }
            if let c = near {
                baseline = c.baseline
                if !af.unify && bb.height < c.size * 1.6 { size = c.size; uf = FontStore.shared.uiFont(font, size: size) ?? Fonts.ui(size) }
                if af.contAfter {
                    let gap = size * 0.3
                    if rtl { rightX = c.minX - gap } else { leftX = c.maxX + gap }
                }
            }
        }
        let spacing = CGFloat(af.unify ? af.unifySpacing : 1.25)
        let date = Date()
        let ink = UIColor(argb: color)
        var made: [PKStroke] = []
        var words: [OcrWord] = []
        var minX = CGFloat.greatestFiniteMagnitude, maxX: CGFloat = 0
        for (i, line) in lines.enumerated() {
            let lay = GlyphInk.layout(line, font: uf)
            let y = baseline + CGFloat(i) * size * spacing
            let x = rtl ? rightX - lay.width : leftX
            made += GlyphInk.strokes(lay, origin: CGPoint(x: x, y: y), size: size, color: ink, bold: af.dynamicBold, date: date)
            for (w, r) in GlyphInk.words(line, lay, origin: CGPoint(x: x, y: y)) {
                words.append(OcrWord(text: w, l: r.minX / pw, t: r.minY / pw, r: r.maxX / pw, b: r.maxY / pw, line: i))
            }
            minX = min(minX, x); maxX = max(maxX, x + lay.width)
        }
        guard !made.isEmpty else { return nil }
        let old = drawing(for: pageId)
        var nd = old
        nd.strokes = old.strokes.filter { s in !strokes.contains { sameStroke($0, s) } } + made
        let before = data
        let key = date.timeIntervalSinceReferenceDate
        data.converted.append(ConvertedLine(id: UUID().uuidString, page: pageId, key: key, words: words, baseline: baseline,
                                            size: size, minX: minX, maxX: maxX, rtl: rtl, font: font))
        drawings[pageId] = nd
        dirtyInk.insert(pageId)
        dataDirty = true
        wordCache.removeValue(forKey: pageId)
        applyInkHook?(pageId, nd)
        inkVersion += 1
        push(.both(.ink(page: pageId, before: old, after: nd), .data(before: before, after: data)))
        scheduleSave()
        return key
    }

    /// يعيد الكتابة اليدوية الأصلية مكان الحبر المجمَّل.
    func inkUnconvert(pageId: String, key: Double, original: [PKStroke]) {
        let old = drawing(for: pageId)
        var nd = old
        nd.strokes = old.strokes.filter { $0.path.creationDate.timeIntervalSinceReferenceDate != key } + original
        let before = data
        data.converted.removeAll { $0.key == key }
        drawings[pageId] = nd
        dirtyInk.insert(pageId)
        dataDirty = true
        wordCache.removeValue(forKey: pageId)
        applyInkHook?(pageId, nd)
        inkVersion += 1
        push(.both(.ink(page: pageId, before: old, after: nd), .data(before: before, after: data)))
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

    @Published var on = false { didSet { prefs.set(on, forKey: "af_on"); if !on { discard() } else { InkReader.prefetch(AppController.shared.hwLang) } } }
    @Published var font = "Tajawal" { didSet { prefs.set(font, forKey: "af_font") } }
    /// مدة التوقف قبل التحويل (ملّي ثانية)؛ 0 = عند الضغط على ✓ فقط.
    @Published var delayMs = 2000 { didSet { prefs.set(delayMs, forKey: "af_delay") } }
    @Published var joinLines = true { didSet { prefs.set(joinLines, forKey: "af_join") } }
    @Published var snapToLine = true { didSet { prefs.set(snapToLine, forKey: "af_snap") } }
    @Published var lang: HwLang = .arabic { didSet { prefs.set(lang.rawValue, forKey: "af_lang") } }
    // إعدادات أندرويد 1.0.271
    static let outInk = 0, outText = 1
    /// تُحفظ على شكل: حبر قلم بشكل الخط، أو مربع نص.
    @Published var output = 0 { didSet { prefs.set(output, forKey: "af_out") } }
    @Published var dynamicBold = false { didSet { prefs.set(dynamicBold, forKey: "af_bold") } }
    /// الكتابة بجانب جملة محوّلة تجلس على سطرها وتأخذ حجمها.
    @Published var continueLine = true { didSet { prefs.set(continueLine, forKey: "af_cont") } }
    /// بداية الكتابة الملحقة: بعد آخر كلمة (أو حيث بدأت اليد).
    @Published var contAfter = false { didSet { prefs.set(contAfter, forKey: "af_contafter") } }
    @Published var unify = false { didSet { prefs.set(unify, forKey: "af_unify") } }
    @Published var unifySize = 20 { didSet { prefs.set(unifySize, forKey: "af_usize") } }
    @Published var unifySpacing: Double = 1.5 { didSet { prefs.set(unifySpacing, forKey: "af_uspace") } }
    /// قوة الجذب إلى أسطر الدفتر: 0 بلا، 1 ضعيف، 2 عادي، 3 قوي.
    @Published var snap = 0 { didSet { prefs.set(snap, forKey: "af_snapk") } }
    static let sizeMin = 8, sizeMax = 60
    /// آخر تحويل إلى حبر (للرجوع إلى خط اليد): المستند، الصفحة، مفتاح الحبر، الخطوط الأصلية.
    @Published private(set) var lastInk: (String, String, Double, [PKStroke])? = nil

    @Published private(set) var pendingCount = 0
    @Published private(set) var preview: (Int, String)? = nil
    @Published private(set) var lastBox: (String, String)? = nil      // (المستند، المربع)

    /// مكان إضافة كلمة رُسم بعلامة ∧: الكتابة التالية في المنطقة تُدرج في النص هنا.
    struct InsertTarget { let docId: String; let pageId: String; let boxId: String; let offset: Int; let area: CGRect }
    @Published var insertTarget: InsertTarget? = nil
    func cancelInsert() { insertTarget = nil }

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
        output = prefs.object(forKey: "af_out") as? Int ?? 0
        dynamicBold = prefs.bool(forKey: "af_bold")
        continueLine = prefs.object(forKey: "af_cont") as? Bool ?? true
        contAfter = prefs.bool(forKey: "af_contafter")
        unify = prefs.bool(forKey: "af_unify")
        unifySize = min(max(prefs.object(forKey: "af_usize") as? Int ?? 20, Self.sizeMin), Self.sizeMax)
        unifySpacing = min(max(prefs.object(forKey: "af_uspace") as? Double ?? 1.5, 1), 3)
        snap = min(max(prefs.object(forKey: "af_snapk") as? Int ?? (snapToLine ? 2 : 0), 0), 3)
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
        let ins = insertTarget.flatMap { $0.docId == s.id && $0.pageId == pid ? $0 : nil }
        guard ins != nil || (on && Premium.shared.active), stroke.ink.inkType != .marker, stroke.randomSeed != shapeSeed, stroke.randomSeed != glyphSeed else { return }
        if !pending.isEmpty && (s !== session || pid != pageId || startsNewLine(stroke, page: s.index(of: pid) ?? 0, s)) { flush() }
        session = s
        pageId = pid
        pending.append(stroke)
        pendingCount = pending.count
        // الإضافة بعلامة ∧ تتحول بعد توقف قصير حتى في الوضع اليدوي
        let d = ins != nil && delayMs == 0 ? 1500 : delayMs
        if d > 0 { schedule(d) }
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
        insertTarget = nil
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

        // 1) إضافة بعلامة ∧: تُدرج الكلمة في مكانها داخل النص
        if let ins = insertTarget, ins.docId == s.id, ins.pageId == pid,
           ins.area.contains(CGPoint(x: bb.midX, y: bb.midY)), var tb = s.data.boxes.first(where: { $0.id == ins.boxId }) {
            insertTarget = nil
            let chars = Array(tb.text)
            let o = min(max(0, ins.offset), chars.count)
            let head = String(chars[0..<o]).trimmingCharacters(in: .whitespaces)
            let tail = String(chars[o...]).trimmingCharacters(in: .whitespaces)
            tb.text = [head, lineTexts.joined(separator: " "), tail].filter { !$0.isEmpty }.joined(separator: " ")
            var ink = (tb.ink.flatMap { try? PKDrawing(data: $0) }) ?? PKDrawing()
            ink.strokes.append(contentsOf: alive)
            tb.ink = ink.dataRepresentation()
            s.edit { d in d.boxes.removeAll { $0.id == tb.id } }
            s.inkToTextBox(pageId: pid, strokes: alive, box: tb)
            FontStore.shared.markUsed(tb.font)
            return
        }

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

        // حبر قلم بشكل الخط (الافتراضي في أندرويد 1.0.271)
        if output == Self.outInk {
            if let key = s.inkConvert(pageId: pid, page: page, strokes: alive, lines: lineTexts, font: font, color: color) {
                FontStore.shared.markUsed(font)
                lastInk = (s.id, pid, key, alive)
                try? await Task.sleep(nanoseconds: 3_500_000_000)
                if lastInk?.2 == key { lastInk = nil }
            }
            return
        }

        let f = s.fitText(page: page, strokes: alive, lines: lineTexts, font: font, snap: snap > 0)
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
        if let li = lastInk, li.0 == s.id {
            lastInk = nil
            s.inkUnconvert(pageId: li.1, key: li.2, original: li.3)
            return
        }
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
        let last: (String, String)? = af.lastBox.flatMap { $0.0 == session.id ? $0 : nil } ?? af.lastInk.flatMap { $0.0 == session.id ? ($0.0, $0.1) : nil }
        let manual = af.on && af.delayMs == 0 && af.pendingCount > 0
        let ins = af.insertTarget.flatMap { $0.docId == session.id ? $0 : nil }
        if af.preview != nil || last != nil || manual || ins != nil {
            HStack(spacing: 6) {
                if let pv = af.preview {
                    ProgressView().scaleEffect(0.7).tint(Palette.navy)
                    Text("«\(pv.1)» ← " + L("يتحول…", "converting…")).font(Fonts.sans(13)).foregroundColor(Palette.textPrimary).lineLimit(1)
                    Button { af.cancelPreview() } label: {
                        Text("✕ " + L("إبقاء بخط يدي", "Keep handwriting")).font(Fonts.sans(13)).foregroundColor(Palette.danger)
                    }
                } else if ins != nil {
                    Text("∧ " + L("اكتب الإضافة في المربع المنقط", "Write the addition in the dotted box")).font(Fonts.sans(13)).foregroundColor(Palette.navy)
                    Button { af.cancelInsert() } label: { Text(L("إلغاء", "Cancel")).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary) }
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

/// إعدادات تجميل خط اليد (نفس AutoFontDialog في أندرويد 1.0.271): بطاقات، وتُطبَّق فورًا.
struct AutoFontSheet: View {
    @EnvironmentObject var app: AppController
    @ObservedObject var af = AutoFont.shared
    @ObservedObject var store = FontStore.shared
    @Environment(\.dismiss) private var dismiss
    @State private var fontPicker = false

    private var sample: String { af.lang == .arabic ? "الخلية وحدة الحياة" : "The cell is the unit of life" }

    var body: some View {
        let ink = af.output == AutoFont.outInk
        VStack(spacing: 0) {
            ZStack {
                Text(L("تجميل خط اليد", "Handwriting beautification")).font(Fonts.display(18)).foregroundColor(Palette.textPrimary)
                HStack {
                    Spacer()
                    Button { dismiss() } label: {
                        Text(L("تم", "Done")).font(Fonts.sans(16, .semibold)).foregroundColor(Palette.navy).padding(.horizontal, 12).padding(.vertical, 8)
                    }
                }
            }
            .padding(.horizontal, 12).padding(.vertical, 10)
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    SettingsCard {
                        CardSwitchRow(title: L("تجميل خط اليد أثناء الكتابة", "Beautify while writing"),
                                      subtitle: L("عند إنهاء الجملة تُعاد كتابتها في مكانها بالخط المختار", "When you finish a sentence it is rewritten in place in the chosen font"),
                                      isOn: Binding(get: { af.on }, set: { v in
                                          af.on = v
                                          if v { app.message(L("اكتب جملتك، وستتجمّل عند إنهائها", "Write your sentence; it is beautified when you finish")) }
                                      }))
                    }
                    VStack(alignment: .leading, spacing: 0) {
                        CardTitle(L("الخط", "Font"))
                        SettingsCard {
                            NavRow(title: L("خط الكتابة", "Writing font"), action: { fontPicker = true }) {
                                Text(af.font).font(store.font(af.font, size: 17) ?? Fonts.sans(17)).foregroundColor(Palette.navy).lineLimit(1)
                            }
                            Text(sample).font(store.font(af.font, size: 30) ?? Fonts.sans(30)).foregroundColor(Palette.textPrimary).lineLimit(1)
                                .frame(maxWidth: .infinity).padding(.horizontal, 12).padding(.vertical, 14)
                                .background(RoundedRectangle(cornerRadius: 14).fill(Color(hex: 0xFFF7F8FA)))
                                .padding(.horizontal, 16).padding(.bottom, 14)
                                .task(id: af.font) { await store.ensure(af.font) }
                            if ink {
                                RowDividerLine()
                                CardSwitchRow(title: L("خط أعرض", "Bolder"), isOn: $af.dynamicBold)
                            }
                        }
                        CardTitle(L("تُحفظ على شكل", "Save as"))
                        SettingsCard {
                            CardSegmented(options: [(AutoFont.outInk, L("✎ حبر قلم", "✎ Pen ink")), (AutoFont.outText, L("T مربع نص", "T Text box"))],
                                          selected: af.output) { af.output = $0 }
                            Text(ink ? L("حبر بشكل الخط تماماً: تمسحه بالممحاة وتحدده وتلوّنه مثل أي كتابة.", "Ink shaped exactly like the font: erase, select and recolor it like any writing.")
                                     : L("نص يمكن تعديله؛ شخبطة على كلمة تحذفها و∧ يضيف كلمة.", "Editable text; scribble a word to delete it, ∧ to insert one."))
                                .font(Fonts.sans(12.5)).foregroundColor(Muted).fixedSize(horizontal: false, vertical: true)
                                .padding(.horizontal, 16).padding(.bottom, 12)
                        }
                        CardTitle(L("لغة الكتابة", "Writing language"))
                        SettingsCard {
                            CardSegmented(options: [(HwLang.arabic, "العربية"), (HwLang.english, "English")], selected: af.lang) { af.lang = $0 }
                        }
                        CardTitle(L("التنسيق", "Layout"))
                        SettingsCard {
                            if ink {
                                CardSwitchRow(title: L("المتابعة على نفس السطر", "Continue on the same line"),
                                              subtitle: L("الكتابة بجانب جملة محوّلة تجلس على سطرها، وتأخذ حجمها إن كانت قريبة منه", "Writing beside a converted sentence sits on its line, and takes its size when close to it"),
                                              isOn: $af.continueLine)
                                if af.continueLine {
                                    RowDividerLine()
                                    Text(L("بداية الكتابة الملحقة", "Joined writing starts")).font(Fonts.sans(14)).foregroundColor(Muted)
                                        .padding(.horizontal, 16).padding(.top, 10)
                                    CardSegmented(options: [(false, L("حيث بدأت يدي", "Where I started")), (true, L("بعد آخر كلمة", "After the last word"))],
                                                  selected: af.contAfter) { af.contAfter = $0 }
                                }
                                RowDividerLine()
                            }
                            CardSwitchRow(title: L("توحيد حجم الخط والمسافة بين الأسطر", "Unify font size & line spacing"), isOn: $af.unify)
                            if af.unify {
                                RowDividerLine()
                                StepperRow(title: L("حجم الخط", "Font size"), value: Double(af.unifySize), range: Double(AutoFont.sizeMin)...Double(AutoFont.sizeMax), step: 1,
                                           format: { "\(Int($0))" }) { af.unifySize = Int($0) }
                                StepperRow(title: L("المسافة بين الأسطر", "Line spacing"), value: af.unifySpacing, range: 1...3, step: 0.1,
                                           format: { String(format: "%.1f", $0) }) { af.unifySpacing = $0 }
                            }
                            RowDividerLine()
                            VStack(alignment: .leading, spacing: 4) {
                                Text(L("قوة الجذب إلى أسطر الدفتر", "Snapping intensity")).font(Fonts.sans(16)).foregroundColor(Palette.textPrimary)
                                Slider(value: Binding(get: { Double(af.snap) }, set: { af.snap = Int($0.rounded()) }), in: 0...3, step: 1).tint(Palette.navy)
                                HStack {
                                    ForEach(0..<4, id: \.self) { i in
                                        let t = [L("بلا", "Off"), L("ضعيف", "Weak"), L("عادي", "Standard"), L("قوي", "Strong")][i]
                                        Text(t).font(Fonts.sans(12, i == af.snap ? .semibold : .regular)).foregroundColor(i == af.snap ? Palette.navy : Muted)
                                            .frame(maxWidth: .infinity, alignment: i == 0 ? .leading : (i == 3 ? .trailing : .center))
                                    }
                                }
                            }
                            .padding(.horizontal, 16).padding(.vertical, 12)
                        }
                        CardTitle(L("متى تتحول الجملة", "When a sentence converts"))
                        SettingsCard {
                            CardSegmented(options: [(1000, L("١ ث", "1 s")), (2000, L("٢ ث", "2 s")), (3000, L("٣ ث", "3 s")), (0, L("عند ✓", "On ✓"))],
                                          selected: af.delayMs) { af.delayMs = $0 }
                            Text(L("بعد التوقف عن الكتابة، وفوراً عندما تبدأ سطراً جديداً.", "After you pause, and right away when you start a new line."))
                                .font(Fonts.sans(12.5)).foregroundColor(Muted).padding(.horizontal, 16).padding(.bottom, 12)
                            if !ink {
                                RowDividerLine()
                                CardSwitchRow(title: L("جمع الأسطر المتتالية في فقرة واحدة", "Join consecutive lines into one paragraph"), isOn: $af.joinLines)
                            }
                        }
                    }
                    .opacity(af.on ? 1 : 0.55)
                }
                .padding(.horizontal, 16).padding(.bottom, 20)
            }
        }
        .background(SheetBg)
        .environment(\.layoutDirection, .leftToRight)
        .task(id: af.lang) { InkReader.prefetch(af.lang == .arabic ? 0 : 1) }
        .sheet(isPresented: $fontPicker) {
            FontPickerSheet(current: af.font, lang: af.lang) { af.font = $0 }.environmentObject(app)
        }
    }
}

/// نافذة اختيار الخط (نفس FontPickerSheet في أندرويد): بحث وفئات، وكل خط بنص تجريبي بشكله.
struct FontPickerSheet: View {
    let current: String
    let lang: HwLang
    let onPick: (String) -> Void
    @EnvironmentObject var app: AppController
    @ObservedObject var store = FontStore.shared
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var tab = FontStore.shared.recent.isEmpty ? "hand" : "recent"
    @State private var busy: String? = nil

    private var sample: String { lang == .arabic ? "الخلية وحدة الحياة" : "The cell is the unit of life" }

    private var list: [FontEntry] {
        let q = query.trimmingCharacters(in: .whitespaces)
        let arabic = lang == .arabic
        if !q.isEmpty { return store.catalog.filter { $0.name.localizedCaseInsensitiveContains(q) } }
        switch tab {
        case "recent": return store.recent.compactMap { n in store.catalog.first { $0.name == n } }
        case "hand": return store.catalog.filter { arabic ? ($0.arabic && $0.cat == "H") || store.isArabicHand($0) : $0.cat == "H" }
        case "fav": return store.catalog.filter { store.favorites.contains($0.name) }
        default: return store.catalog.filter { !arabic || $0.arabic }
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                Text(L("خط الكتابة", "Writing font")).font(Fonts.display(18)).foregroundColor(Palette.textPrimary)
                HStack {
                    Button { dismiss() } label: {
                        Text("‹ " + L("رجوع", "Back")).font(Fonts.sans(16)).foregroundColor(Palette.navy).padding(.horizontal, 10).padding(.vertical, 8)
                    }
                    Spacer()
                }
            }
            .padding(.horizontal, 12).padding(.vertical, 10)
            HStack(spacing: 8) {
                Text("⌕").font(.system(size: 18)).foregroundColor(Muted)
                TextField(L("ابحث في \(store.catalog.count) خطاً", "Search \(store.catalog.count) fonts"), text: $query).font(Fonts.sans(15))
            }
            .padding(.horizontal, 12).padding(.vertical, 10)
            .background(RoundedRectangle(cornerRadius: 12).fill(Color(hex: 0xFFE6E8EC)))
            .padding(.horizontal, 16)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach([("recent", L("المستعملة", "Recent")), ("hand", L("خط يد", "Handwriting")), ("fav", L("★ المفضلة", "★ Favorites")), ("all", L("الكل", "All"))], id: \.0) { k, label in
                        let sel = tab == k && query.isEmpty
                        Button { tab = k; query = "" } label: {
                            Text(label).font(Fonts.sans(14, sel ? .semibold : .regular)).foregroundColor(sel ? .white : Palette.textPrimary)
                                .padding(.horizontal, 14).padding(.vertical, 7).background(Capsule().fill(sel ? Palette.navy : Color.white))
                        }
                    }
                }
                .padding(.horizontal, 16).padding(.vertical, 10)
            }
            if list.isEmpty {
                Text(tab == "fav" ? L("اضغط ☆ بجانب أي خط ليظهر هنا", "Tap ☆ next to any font to keep it here") : L("لا توجد نتائج", "No results"))
                    .font(Fonts.sans(14)).foregroundColor(Muted).frame(maxWidth: .infinity, minHeight: 200)
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(list) { e in
                            row(e)
                            RowDividerLine()
                        }
                    }
                    .background(RoundedRectangle(cornerRadius: 18).fill(CardBg))
                    .clipShape(RoundedRectangle(cornerRadius: 18))
                    .padding(.horizontal, 16).padding(.bottom, 16)
                }
            }
        }
        .background(SheetBg.ignoresSafeArea())
        .environment(\.layoutDirection, .leftToRight)
        .environment(\.colorScheme, .light)
    }

    private func row(_ e: FontEntry) -> some View {
        let ready = store.isReady(e.name)
        let sel = e.name == current
        return Button {
            busy = e.name
            Task { @MainActor in
                let ok = await store.ensure(e.name)
                busy = nil
                if ok { store.markUsed(e.name); onPick(e.name); dismiss() }
                else { app.message(L("تعذر تنزيل الخط. تأكد من الإنترنت", "Could not download the font. Check your connection")) }
            }
        } label: {
            HStack(spacing: 10) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(ready ? sample : e.name).font(ready ? (store.font(e.name, size: 24) ?? Fonts.sans(24)) : Fonts.sans(17))
                        .foregroundColor(ready ? Palette.textPrimary : Muted).lineLimit(1)
                    Text(e.name).font(Fonts.sans(12)).foregroundColor(Muted).lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if busy == e.name { ProgressView().tint(Palette.navy) }
                else if sel { Text("✓").font(.system(size: 20, weight: .bold)).foregroundColor(Palette.navy) }
                else if !ready { Text("⤓").font(.system(size: 20)).foregroundColor(Muted) }
                Button { store.toggleFavorite(e.name) } label: {
                    Text(store.favorites.contains(e.name) ? "★" : "☆").font(.system(size: 18)).foregroundColor(Palette.gold).padding(4)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 16).padding(.vertical, 12)
            .background(sel ? Color(hex: 0xFFF1F4FA) : Color.clear)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
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
