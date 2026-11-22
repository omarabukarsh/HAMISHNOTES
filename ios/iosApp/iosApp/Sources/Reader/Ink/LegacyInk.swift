import UIKit
import PencilKit

// قراءة الحبر القديم فقط: قبل محرك الحبر الخاص كانت الكتابة تُحفظ بصيغة PencilKit. هذا الملف يحوّلها مرة واحدة
// إلى النموذج الجديد عند فتح الصفحة (ثم تُحفظ بالصيغة الجديدة). لا شيء آخر في التطبيق يستعمل PencilKit.

/// علامات الخطوط في الصيغة القديمة: أشكال، وحروف مجمَّلة.
let legacyShapeSeed: UInt32 = 0x5A5E
let legacyGlyphSeed: UInt32 = 0x61F7

extension InkDrawing {
    /// ملف حبر بأي صيغة: الجديدة، أو صيغة PencilKit القديمة. nil إن تعذّرت قراءته.
    static func load(_ data: Data) -> InkDrawing? {
        if let d = parse(data) { return d }
        guard let pk = try? PKDrawing(data: data) else { return nil }
        return LegacyInk.convert(pk)
    }
}

enum LegacyInk {
    static func convert(_ d: PKDrawing) -> InkDrawing {
        InkDrawing(strokes: encode(d).map { InkStroke(android: $0, width: 1) })
    }

    static let step: CGFloat = 1.2

    /// خطوط PencilKit بالنقاط (بنقاط الصفحة، أي W = 1). الحروف المجمَّلة تُجمع في حروف مملوءة.
    static func encode(_ d: PKDrawing) -> [AStroke] {
        let W: CGFloat = 1
        var groups: [Double: [PKStroke]] = [:]
        for s in d.strokes where s.randomSeed == legacyGlyphSeed {
            groups[s.path.creationDate.timeIntervalSinceReferenceDate, default: []].append(s)
        }
        var out: [AStroke] = []
        var doneGlyph = Set<Double>()
        for s in d.strokes {
            let key = s.path.creationDate.timeIntervalSinceReferenceDate
            if s.randomSeed == legacyGlyphSeed {
                guard doneGlyph.insert(key).inserted, let group = groups[key] else { continue }
                let made = glyphs(group, W, nil)
                out += made.isEmpty ? group.flatMap { plain($0, W) } : made
                continue
            }
            out += plain(s, W)
        }
        return out
    }

    static func ms(_ date: Date) -> Int64 { Int64((date.timeIntervalSince1970 * 1000).rounded()) }

    private static func scale(_ t: CGAffineTransform) -> CGFloat { sqrt(abs(t.a * t.d - t.b * t.c)) }

    private static func median(_ v: [CGFloat]) -> CGFloat { v.isEmpty ? 0 : v.sorted()[v.count / 2] }

    /// خط عادي: نقاط على المنحنى نفسه؛ الممسوح جزئيًا تُكتب أجزاؤه الظاهرة فقط.
    private static func plain(_ s: PKStroke, _ W: CGFloat) -> [AStroke] {
        let k = max(0.0001, scale(s.transform))
        let isShape = s.randomSeed == legacyShapeSeed
        var runs: [[PKStrokePoint]] = []
        if s.mask != nil {
            for r in s.maskedPathRanges { runs.append(Array(s.path.interpolatedPoints(in: r, by: .distance(step / k)))) }
        } else if isShape || s.path.count < 3 {
            runs = [Array(s.path)]
        } else {
            var pts = Array(s.path.interpolatedPoints(by: .distance(step / k)))
            if let last = s.path.last, let e = pts.last,
               hypot(last.location.x - e.location.x, last.location.y - e.location.y) > 0.2 { pts.append(last) }
            runs = [pts]
        }
        let type = s.ink.inkType
        var out: [AStroke] = []
        for run in runs where !run.isEmpty {
            let sizes = run.map { ($0.size.width > 0 ? $0.size.width : $0.size.height) * k }
            let mid = median(sizes), top = sizes.max() ?? mid
            var a = AStroke()
            a.t = ms(s.path.creationDate)
            a.color = s.ink.color.argb
            a.sh = isShape
            var press: (CGFloat) -> Double = { _ in 1 }
            switch type {
            case .marker:
                a.k = "HIGHLIGHTER"
                a.color |= 0xFF000000
                a.w = Double(mid / W)
            case .pencil, .crayon:
                a.k = "PENCIL"
                a.w = Double(mid / 0.85 / W)
            case .fountainPen, .pen:
                a.k = "FOUNTAIN"
                if isShape { a.w = Double(mid / W) } else {
                    // أندرويد يرسم العرض × (0.4 + الضغط): يُحسب الضغط من سماكة كل نقطة
                    let base = max(0.01, top / 1.4)
                    a.w = Double(base / W)
                    press = { Double(max(0, min(1, $0 / base - 0.4))) }
                }
            case .watercolor:
                a.k = "BRUSH"
                if isShape { a.w = Double(mid / W) } else {
                    let base = max(0.01, top / 1.65)
                    a.w = Double(base / W)
                    press = { Double(max(0, min(1, ($0 / base - 0.15) / 1.5))) }
                }
            default:
                a.k = "BALLPOINT"
                a.w = Double(mid / W)
            }
            a.pts.reserveCapacity(run.count * 3)
            for (i, p) in run.enumerated() {
                let l = p.location.applying(s.transform)
                a.pts += [Double(l.x / W), Double(l.y / W), press(sizes[i])]
            }
            out.append(a)
        }
        return out
    }

    /// الحروف المجمَّلة (حدود وخطوط ملء في PencilKit) كحروف مملوءة بحدودها، كل كلمة خط واحد يحمل نصها.
    private static func glyphs(_ group: [PKStroke], _ W: CGFloat, _ line: ConvertedLine?) -> [AStroke] {
        var contours: [[CGPoint]] = []
        var outline: CGFloat = 0
        var sample: PKStroke?
        for st in group where st.path.count >= 4 {
            var pts = st.path.map { $0.location.applying(st.transform) }
            guard let a = pts.first, let b = pts.last, hypot(a.x - b.x, a.y - b.y) < 0.05 else { continue }
            pts.removeLast()
            guard pts.count >= 3 else { continue }
            contours.append(pts)
            if sample == nil {
                sample = st
                outline = (st.path.first?.size.width ?? 0) * scale(st.transform)
            }
        }
        guard !contours.isEmpty, let first = sample else { return [] }
        let words = line?.words ?? []
        var byWord: [Int: [[CGPoint]]] = [:]
        for c in contours {
            let cx = Double(c.map { $0.x }.reduce(0, +) / CGFloat(c.count) / W)
            let cy = Double(c.map { $0.y }.reduce(0, +) / CGFloat(c.count) / W)
            byWord[InkCodec.wordIndex(cx, cy, words), default: []].append(c)
        }
        let t = ms(first.path.creationDate)
        let firstLine = words.map { $0.line }.min() ?? 0
        let top0 = words.filter { $0.line == firstLine }.map { Double($0.t) }.min() ?? 0
        var out: [AStroke] = []
        for idx in byWord.keys.sorted() {
            var a = AStroke()
            a.k = "BALLPOINT"
            a.fl = true
            a.t = t
            a.color = first.ink.color.argb | 0xFF000000
            a.w = Double(outline / W)
            for c in byWord[idx] ?? [] {
                for (i, p) in c.enumerated() { a.pts += [Double(p.x / W), Double(p.y / W), i == 0 ? -1 : 1] }
            }
            if idx >= 0, let l = line {
                let w = words[idx]
                a.wd = w.text
                a.wi = t &* 1024 &+ Int64(idx)
                if let bb = a.bounds, bb.b - bb.t > 0 {
                    let h = bb.b - bb.t
                    let base = Double(l.baseline / W) + (Double(w.t) - top0)
                    a.lf = max(0, (base - bb.t) / h)
                    a.ef = Double(l.size / W) / h
                }
            }
            out.append(a)
        }
        return out
    }

}

