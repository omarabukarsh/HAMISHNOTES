import Foundation
import PencilKit
#if canImport(MLKitDigitalInkRecognition)
import MLKitDigitalInkRecognition
import MLKitCommon
#endif

// قراءة خط اليد من حركة القلم نفسها بمحرك Google ML Kit Digital Ink (نفس محرك نسخة أندرويد):
// أدق من قراءة صورة الكتابة، خاصة في الخط العربي المتصل. حزمة كل لغة تُنزَّل مرة واحدة (نحو 20 ميغابايت)
// ثم تعمل دون إنترنت. حتى تجهز الحزمة (أو في بناء المحاكي بلا ML Kit) تُستعمل قراءة Vision.

enum InkReader {
    /// المحرك مضمَّن في هذا البناء.
    static var available: Bool {
        #if canImport(MLKitDigitalInkRecognition)
        return true
        #else
        return false
        #endif
    }

    /// lang: 0 عربي، 1 إنجليزي، 2 كلاهما (تُختار اللغة لكل سطر من اتجاه الكتابة).
    static func tags(_ lang: Int) -> [String] { lang == 0 ? ["ar"] : (lang == 1 ? ["en-US"] : ["ar", "en-US"]) }

    private static var warned = false

    /// يبدأ تنزيل حزم اللغات إن لم تكن موجودة.
    static func prefetch(_ lang: Int) {
        #if canImport(MLKitDigitalInkRecognition)
        for t in tags(lang) { _ = ensure(t) }
        #endif
    }

    // ---------------------------------------------------------------- تقسيم الكتابة

    struct Line { var strokes: [PKStroke]; var box: CGRect }

    /// يقسم الخطوط إلى أسطر (من الأعلى للأسفل)، وكل سطر بترتيب الكتابة.
    static func lines(_ strokes: [PKStroke]) -> [Line] {
        var out: [Line] = []
        for s in strokes.filter(HandwritingWords.eligible).sorted(by: { $0.renderBounds.midY < $1.renderBounds.midY }) {
            let b = s.renderBounds
            if let i = out.firstIndex(where: { l in
                let ov = min(l.box.maxY, b.maxY) - max(l.box.minY, b.minY)
                return ov > min(l.box.height, b.height) * 0.4 || (b.midY > l.box.minY && b.midY < l.box.maxY)
            }) {
                out[i].strokes.append(s)
                out[i].box = out[i].box.union(b)
            } else {
                out.append(Line(strokes: [s], box: b))
            }
        }
        return out.map { l in
            Line(strokes: l.strokes.sorted { $0.path.creationDate < $1.path.creationDate }, box: l.box)
        }.sorted { $0.box.minY < $1.box.minY }
    }

    /// اتجاه الكتابة من ترتيب الخطوط: آخر خط إلى يسار أولها = من اليمين لليسار.
    static func isRTL(_ l: Line) -> Bool {
        guard let f = l.strokes.first, let z = l.strokes.last, l.strokes.count > 1 else { return true }
        return z.renderBounds.midX < f.renderBounds.midX
    }

    /// مجموعات الكلمات في السطر (فجوة أفقية واضحة = كلمة جديدة)، مرتبة من اليسار لليمين.
    static func wordBoxes(_ l: Line) -> [CGRect] {
        let boxes = l.strokes.map { $0.renderBounds }.sorted { $0.minX < $1.minX }
        let gap = max(4, l.box.height * 0.35)
        var out: [CGRect] = []
        for b in boxes {
            if let last = out.last, b.minX - last.maxX < gap { out[out.count - 1] = last.union(b) } else { out.append(b) }
        }
        return out
    }

    // ---------------------------------------------------------------- القراءة

    /// أسطر النص، أو nil إن لم يكن المحرك جاهزًا (فيُستعمل Vision).
    static func readLines(_ strokes: [PKStroke], lang: Int) async -> [String]? {
        let ls = lines(strokes)
        guard !ls.isEmpty else { return [] }
        var out: [String] = []
        for l in ls {
            guard let t = await read(l, lang: lang) else { return nil }
            if !t.isEmpty { out.append(t) }
        }
        return out
    }

    /// كلمات خط اليد بإحداثيات نسبية لعرض الصفحة، أو nil إن لم يكن المحرك جاهزًا.
    static func words(_ strokes: [PKStroke], pageW: CGFloat, lang: Int) async -> [OcrWord]? {
        var out: [OcrWord] = []
        for (li, l) in lines(strokes).enumerated() {
            guard let text = await read(l, lang: lang) else { return nil }
            let parts = text.split(separator: " ").map(String.init).filter { !$0.isEmpty }
            guard !parts.isEmpty else { continue }
            let rtl = parts.joined().unicodeScalars.contains { (0x0600...0x06FF).contains($0.value) }
            var boxes = wordBoxes(l)
            if rtl { boxes.reverse() }
            if boxes.count != parts.count {
                // تعذّر مطابقة الكلمات بالمجموعات: يُقسَّم عرض السطر بحسب عدد الحروف
                let total = CGFloat(max(1, parts.reduce(0) { $0 + $1.count }))
                var x = rtl ? l.box.maxX : l.box.minX
                boxes = parts.map { p in
                    let w = l.box.width * CGFloat(p.count) / total
                    defer { x += rtl ? -w : w }
                    return CGRect(x: rtl ? x - w : x, y: l.box.minY, width: w, height: l.box.height)
                }
            }
            for (p, b) in zip(parts, boxes) {
                out.append(OcrWord(text: p, l: b.minX / pageW, t: b.minY / pageW, r: b.maxX / pageW, b: b.maxY / pageW, line: li))
            }
        }
        return out
    }

    #if canImport(MLKitDigitalInkRecognition)
    private static var models: [String: DigitalInkRecognitionModel] = [:]
    private static var recognizers: [String: DigitalInkRecognizer] = [:]
    /// وقت بدء تنزيل كل حزمة: إن لم تجهز بعد دقيقتين (انقطع الاتصال مثلًا) يُعاد طلبها.
    private static var downloading: [String: Date] = [:]
    private static let lock = NSLock()

    /// النموذج جاهز للاستعمال؛ وإلا يبدأ تنزيله ويعيد false.
    private static func ensure(_ tag: String) -> Bool {
        lock.lock(); defer { lock.unlock() }
        if models[tag] == nil {
            guard let id = DigitalInkRecognitionModelIdentifier(forLanguageTag: tag) else { return false }
            models[tag] = DigitalInkRecognitionModel(modelIdentifier: id)
        }
        guard let m = models[tag] else { return false }
        let mm = ModelManager.modelManager()
        if mm.isModelDownloaded(m) {
            if recognizers[tag] == nil { recognizers[tag] = DigitalInkRecognizer.digitalInkRecognizer(options: DigitalInkRecognizerOptions(model: m)) }
            return true
        }
        if downloading[tag].map({ Date().timeIntervalSince($0) > 120 }) ?? true {
            downloading[tag] = Date()
            _ = mm.download(m, conditions: ModelDownloadConditions(allowsCellularAccess: true, allowsBackgroundDownloading: true))
            DispatchQueue.main.async {
                if !warned {
                    warned = true
                    AppController.shared.say(.done, L("تُنزَّل حزمة قراءة خط اليد (مرة واحدة)، وحتى تجهز تُستعمل القراءة العادية", "Downloading the handwriting pack (once); until it's ready, standard reading is used"))
                }
            }
        }
        return false
    }

    private static func recognizer(_ tag: String) -> DigitalInkRecognizer? {
        lock.lock(); defer { lock.unlock() }
        return recognizers[tag]
    }

    private static func read(_ l: Line, lang: Int) async -> String? {
        let tag = lang == 0 ? "ar" : (lang == 1 ? "en-US" : (isRTL(l) ? "ar" : "en-US"))
        guard ensure(tag), let r = recognizer(tag) else { return nil }
        let strokes: [Stroke] = l.strokes.map { s in
            let base = s.path.creationDate.timeIntervalSince1970
            let pts = s.path.interpolatedPoints(by: .distance(2)).map { p -> StrokePoint in
                let q = p.location.applying(s.transform)
                return StrokePoint(x: Float(q.x), y: Float(q.y), t: Int((base + p.timeOffset) * 1000))
            }
            return Stroke(points: pts)
        }.filter { $0.points.count >= 2 }
        guard !strokes.isEmpty else { return "" }
        let ink = Ink(strokes: strokes)
        return await withCheckedContinuation { cont in
            r.recognize(ink: ink) { result, _ in
                let cands = result?.candidates.map { $0.text } ?? []
                if let f = cands.first { ReadAlts.note(f, Array(cands.dropFirst().prefix(6))) }
                cont.resume(returning: cands.first ?? "")
            }
        }
    }
    #else
    private static func read(_ l: Line, lang: Int) async -> String? { nil }
    #endif
}

/// القراءات الأخرى التي اقترحها قارئ خط اليد لكل سطر (لتصحيح كلمة بنقرة بعد التحويل).
enum ReadAlts {
    private static var map: [String: [String]] = [:]
    private static var order: [String] = []
    private static let lock = NSLock()

    static func note(_ text: String, _ others: [String]) {
        let key = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let list = others.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty && $0 != key }
        guard !key.isEmpty, !list.isEmpty else { return }
        lock.lock(); defer { lock.unlock() }
        if map[key] == nil { order.append(key) }
        map[key] = list
        if order.count > 300 { map.removeValue(forKey: order.removeFirst()) }
    }

    static func of(_ text: String) -> [String] {
        lock.lock(); defer { lock.unlock() }
        return map[text.trimmingCharacters(in: .whitespacesAndNewlines)] ?? []
    }

    /// قراءات أخرى لكل كلمة في السطر (من القراءات التي لها عدد الكلمات نفسه).
    static func wordAlts(_ line: String) -> [[String]] {
        let ws = line.split(whereSeparator: { $0.isWhitespace }).map(String.init)
        var out = Array(repeating: [String](), count: ws.count)
        for alt in of(line) {
            let a = alt.split(whereSeparator: { $0.isWhitespace }).map(String.init)
            guard a.count == ws.count else { continue }
            for i in ws.indices where a[i] != ws[i] && !out[i].contains(a[i]) { out[i].append(a[i]) }
        }
        return out
    }
}
