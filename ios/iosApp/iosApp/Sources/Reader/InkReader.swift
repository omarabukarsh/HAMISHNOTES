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
        for s in strokes.sorted(by: { $0.renderBounds.midY < $1.renderBounds.midY }) {
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
    private static var downloading: Set<String> = []
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
        if !downloading.contains(tag) {
            downloading.insert(tag)
            _ = mm.download(m, conditions: ModelDownloadConditions(allowsCellularAccess: true, allowsBackgroundDownloading: true))
            DispatchQueue.main.async {
                if !warned {
                    warned = true
                    AppController.shared.message(L("تُنزَّل حزمة قراءة خط اليد (مرة واحدة)، وحتى تجهز تُستعمل القراءة العادية",
                                                   "Downloading the handwriting pack (once); until it's ready, standard reading is used"))
                }
            }
        }
        return false
    }

    private static func read(_ l: Line, lang: Int) async -> String? {
        let tag = lang == 0 ? "ar" : (lang == 1 ? "en-US" : (isRTL(l) ? "ar" : "en-US"))
        guard ensure(tag) else { return nil }
        lock.lock(); let rec = recognizers[tag]; lock.unlock()
        guard let r = rec else { return nil }
        let strokes: [Stroke] = l.strokes.map { s in
            let base = s.path.creationDate.timeIntervalSince1970
            let pts = s.path.interpolatedPoints(by: .distance(2)).map { p -> StrokePoint in
                let q = p.location.applying(s.transform)
                return StrokePoint(x: Float(q.x), y: Float(q.y), t: Int((base + p.timeOffset) * 1000))
            }
            return Stroke(points: pts)
        }
        let ink = Ink(strokes: strokes)
        return await withCheckedContinuation { cont in
            r.recognize(ink: ink) { result, _ in
                cont.resume(returning: result?.candidates.first?.text ?? "")
            }
        }
    }
    #else
    private static func read(_ l: Line, lang: Int) async -> String? { nil }
    #endif
}
