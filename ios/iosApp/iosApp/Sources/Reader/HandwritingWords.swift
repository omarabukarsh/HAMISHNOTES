import UIKit
import PencilKit
import Vision
import PDFKit

// قراءة خط اليد على الصفحة إلى كلمات (نفس HandwritingWords.kt في أندرويد):
// فتلتقطها بالهايلايتر الذكي كأي نص، وتجدها في البحث. القراءة على الجهاز بأداة Vision.

enum HandwritingWords {
    /// الخطوط التي تُقرأ كنص: لا الهايلايتر ولا الأشكال.
    /// الخطوط التي تُقرأ كنص: لا الهايلايتر ولا الأشكال، ولا بقايا المسح الفارغة.
    static func eligible(_ s: PKStroke) -> Bool {
        guard s.ink.inkType != .marker, s.randomSeed != shapeSeed, s.path.count >= 2 else { return false }
        let b = s.renderBounds
        return !b.isNull && !b.isInfinite && !b.isEmpty && b.width.isFinite && b.height.isFinite
    }

    /// مفتاح يتغيّر مع الكتابة ولغة خط اليد.
    /// (بعد مسح كل الكتابة تكون الحدود «فارغة» بقيم لا نهائية: تحويلها إلى عدد صحيح كان يُغلق التطبيق)
    static func key(_ d: PKDrawing, lang: Int) -> String {
        let ok = d.strokes.filter(eligible)
        let b = ok.reduce(CGRect.null) { $0.union($1.renderBounds) }
        guard !b.isNull, b.minX.isFinite, b.minY.isFinite else { return "0|\(lang)" }
        return "\(ok.count)|\(Int(b.minX))|\(Int(b.minY))|\(Int(b.width))|\(Int(b.height))|\(lang)"
    }

    /// كلمات خط اليد: بمحرك ML Kit من حركة القلم إن كان جاهزًا، وإلا بقراءة الصورة (Vision).
    static func readAsync(_ d: PKDrawing, pageW: CGFloat, lang: Int) async -> [OcrWord] {
        let ok = d.strokes.filter(eligible)
        guard !ok.isEmpty else { return [] }
        if let w = await InkReader.words(ok, pageW: pageW, lang: lang) { return w }
        return read(d, pageW: pageW, lang: lang)
    }

    /// كلمات خط اليد بإحداثيات نسبية لعرض الصفحة (مثل كلمات النص).
    static func read(_ d: PKDrawing, pageW: CGFloat, lang: Int) -> [OcrWord] {
        let ok = d.strokes.filter(eligible).map { s -> PKStroke in
            var c = s
            c.ink = PKInk(s.ink.inkType, color: .black)
            return c
        }
        guard !ok.isEmpty else { return [] }
        let dd = PKDrawing(strokes: ok)
        let r = dd.bounds.insetBy(dx: -16, dy: -16)
        guard r.width > 4, r.height > 4 else { return [] }
        let scale: CGFloat = max(1, min(3, 2000 / max(r.width, r.height)))
        let size = CGSize(width: r.width * scale, height: r.height * scale)
        let img = UIGraphicsImageRenderer(size: size).image { ctx in
            UIColor.white.setFill()
            ctx.fill(CGRect(origin: .zero, size: size))
            dd.image(from: r, scale: scale).draw(in: CGRect(origin: .zero, size: size))
        }
        guard let cg = img.cgImage else { return [] }
        let req = VNRecognizeTextRequest()
        req.recognitionLevel = .accurate
        req.usesLanguageCorrection = true
        let supported = (try? req.supportedRecognitionLanguages()) ?? []
        let langs: [String] = lang == 0 ? ["ar-SA"] : (lang == 1 ? ["en-US"] : ["ar-SA", "en-US"])
        req.recognitionLanguages = langs.filter { supported.contains($0) }
        try? VNImageRequestHandler(cgImage: cg, options: [:]).perform([req])
        var out: [OcrWord] = []
        for (li, o) in (req.results ?? []).enumerated() {
            guard let cand = o.topCandidates(1).first else { continue }
            let s = cand.string
            var idx = s.startIndex
            for part in s.split(separator: " ", omittingEmptySubsequences: true) {
                guard let range = s.range(of: part, range: idx..<s.endIndex) else { continue }
                idx = range.upperBound
                let box = (try? cand.boundingBox(for: range))?.boundingBox ?? o.boundingBox
                // من إحداثيات الصورة (الأصل أسفل اليسار، 0..1) إلى نقاط الصفحة ثم نسبة لعرضها
                let x0 = r.minX + box.minX * r.width, x1 = r.minX + box.maxX * r.width
                let y0 = r.minY + (1 - box.maxY) * r.height, y1 = r.minY + (1 - box.minY) * r.height
                out.append(OcrWord(text: String(part), l: x0 / pageW, t: y0 / pageW, r: x1 / pageW, b: y1 / pageW, line: li))
            }
        }
        return out
    }
}

/// نتيجة بحث: تحديد في نص الملف، أو مستطيل (خط يد أو صفحة مقروءة ضوئيًا) بنقاط الصفحة.
struct SearchHit {
    let page: Int
    let sel: PDFSelection?
    let rect: CGRect?
}
