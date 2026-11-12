import UIKit
import PencilKit

// الحبر بصيغة أندرويد (مخطط version 3): كل خط نقاط x,y,ضغط نسبية لعرض الصفحة، مع نوع القلم وخصائصه.
// هذا الملف يحوّل PKDrawing إلى تلك النقاط وبالعكس، لتُفتح «النسخة القابلة للتعديل» في التطبيقين.
// الشكل قريب لا مطابق بالبكسل (محرك الرسم مختلف)، والسماكة الظاهرة محفوظة.

/// خط واحد كما يحفظه أندرويد.
struct AStroke: Equatable {
    /// x, y, ضغط … (x و y مقسومان على عرض الصفحة). في الحروف المملوءة: ضغط سالب = بداية حد جديد.
    var pts: [Double] = []
    var color: UInt32 = 0xFF000000
    var w: Double = 0.003
    /// BALLPOINT, FOUNTAIN, PENCIL, HIGHLIGHTER, BRUSH
    var k = "BALLPOINT"
    /// وقت الكتابة (ملّي ثانية منذ 1970).
    var t: Int64 = 0
    var ls = 0
    var sh = false
    var fl = false
    var lf: Double = -1
    var ef: Double = 0
    var sf = false
    var sfa: Double = 0.2
    var wd = ""
    var wi: Int64 = 0

    init() {}

    init?(json o: [String: Any]) {
        guard let p = o["p"] as? [Any] else { return nil }
        pts = p.map { AJSON.double($0) }
        color = AJSON.color(o["c"]) ?? 0xFF000000
        w = AJSON.double(o["w"] ?? 0.003)
        k = o["k"] as? String ?? "FOUNTAIN"
        t = AJSON.int(o["t"]) ?? 0
        ls = Int(AJSON.int(o["ls"]) ?? 0)
        sh = AJSON.bool(o["sh"])
        fl = AJSON.bool(o["fl"])
        lf = o["lf"].map { AJSON.double($0) } ?? -1
        ef = o["ef"].map { AJSON.double($0) } ?? 0
        sf = AJSON.bool(o["sf"])
        sfa = o["sfa"].map { AJSON.double($0) } ?? 0.2
        wd = o["wd"] as? String ?? ""
        wi = AJSON.int(o["wi"]) ?? 0
    }

    var json: [String: Any] {
        var o: [String: Any] = ["c": AJSON.signed(color), "w": w, "k": k, "t": t, "p": pts]
        if ls != 0 { o["ls"] = ls }
        if sh { o["sh"] = true }
        if fl { o["fl"] = true }
        if lf >= 0 { o["lf"] = lf; o["ef"] = ef }
        if sf { o["sf"] = true; o["sfa"] = sfa }
        if !wd.isEmpty { o["wd"] = wd; o["wi"] = wi }
        return o
    }

    var count: Int { pts.count / 3 }
    func x(_ i: Int) -> Double { pts[i * 3] }
    func y(_ i: Int) -> Double { pts[i * 3 + 1] }
    func p(_ i: Int) -> Double { pts[i * 3 + 2] }

    /// حدود النقاط: يسار، أعلى، يمين، أسفل.
    var bounds: (l: Double, t: Double, r: Double, b: Double)? {
        guard count > 0 else { return nil }
        var l = Double.greatestFiniteMagnitude, t = l, r = -l, b = -l
        for i in 0..<count { l = min(l, x(i)); r = max(r, x(i)); t = min(t, y(i)); b = max(b, y(i)) }
        return (l, t, r, b)
    }

    /// خط القاعدة وحجم الخط المحفوظان في الحرف (InkFill.lineOf في أندرويد).
    var line: (base: Double, em: Double)? {
        guard lf >= 0, ef > 0, let bb = bounds, bb.b - bb.t > 0 else { return nil }
        let h = bb.b - bb.t
        return (bb.t + lf * h, ef * h)
    }
}

/// قراءة وكتابة JSON بأرقام قصيرة (ملف النقاط كبير)، وبألوان موقّعة كما يكتبها أندرويد.
enum AJSON {
    static func double(_ v: Any) -> Double { (v as? NSNumber)?.doubleValue ?? 0 }
    static func int(_ v: Any?) -> Int64? { (v as? NSNumber)?.int64Value }
    static func bool(_ v: Any?) -> Bool { (v as? NSNumber)?.boolValue ?? false }
    static func color(_ v: Any?) -> UInt32? { (v as? NSNumber).map { UInt32(truncatingIfNeeded: $0.int64Value) } }
    static func signed(_ c: UInt32) -> Int { Int(Int32(bitPattern: c)) }
    static func string(_ v: Any?) -> String { v as? String ?? "" }

    static func parse(_ d: Data) -> [String: Any]? { (try? JSONSerialization.jsonObject(with: d)) as? [String: Any] }

    static func write(_ v: Any) -> Data {
        var s = ""
        put(v, &s)
        return Data(s.utf8)
    }

    private static func put(_ v: Any, _ s: inout String) {
        if let str = v as? String { quote(str, &s); return }
        if let n = v as? NSNumber {
            if CFGetTypeID(n) == CFBooleanGetTypeID() { s += n.boolValue ? "true" : "false"; return }
            let type = String(cString: n.objCType)
            if type == "d" || type == "f" {
                let d = n.doubleValue
                if !d.isFinite { s += "0"; return }
                let r = (d * 1_000_000).rounded() / 1_000_000
                s += r == r.rounded() && abs(r) < 1e15 ? String(Int64(r)) : "\(r)"
            } else {
                s += n.stringValue
            }
            return
        }
        if let a = v as? [Any] {
            s += "["
            for (i, e) in a.enumerated() { if i > 0 { s += "," }; put(e, &s) }
            s += "]"
            return
        }
        if let d = v as? [String: Any] {
            s += "{"
            for (i, k) in d.keys.sorted().enumerated() {
                if i > 0 { s += "," }
                quote(k, &s)
                s += ":"
                put(d[k] ?? NSNull(), &s)
            }
            s += "}"
            return
        }
        s += "null"
    }

    private static func quote(_ str: String, _ s: inout String) {
        s += "\""
        for u in str.unicodeScalars {
            switch u {
            case "\"": s += "\\\""
            case "\\": s += "\\\\"
            case "\n": s += "\\n"
            case "\r": s += "\\r"
            case "\t": s += "\\t"
            default:
                if u.value < 0x20 { s += String(format: "\\u%04x", u.value) } else { s.unicodeScalars.append(u) }
            }
        }
        s += "\""
    }
}

enum InkCodec {
    /// المسافة بين النقاط المأخوذة من منحنى PencilKit (بنقاط الصفحة).
    static let step: CGFloat = 1.2
    /// أقصى مسافة بين نقاط التحكم عند بناء خط PencilKit (فيمر المنحنى قريبًا من النقاط).
    static let gap: CGFloat = 2

    // ============================================================ من PencilKit إلى أندرويد

    /// خطوط الصفحة بصيغة أندرويد. W = عرض الصفحة بالنقاط. الملء والحروف المجمَّلة تُربط بخطوطها.
    static func encode(_ d: PKDrawing, width W: CGFloat, fills: [ShapeFill] = [], converted: [ConvertedLine] = []) -> [AStroke] {
        guard W > 0 else { return [] }
        var groups: [Double: [PKStroke]] = [:]
        for s in d.strokes where s.randomSeed == glyphSeed {
            groups[s.path.creationDate.timeIntervalSinceReferenceDate, default: []].append(s)
        }
        var out: [AStroke] = []
        var doneGlyph = Set<Double>()
        var doneFill = Set<String>()
        for s in d.strokes {
            let key = s.path.creationDate.timeIntervalSinceReferenceDate
            for f in fills where f.key == key && f.pts.count > 2 && doneFill.insert(f.id).inserted {
                out.append(fillStroke(f, W))
            }
            if s.randomSeed == glyphSeed {
                guard doneGlyph.insert(key).inserted, let group = groups[key] else { continue }
                let made = glyphs(group, W, converted.first { $0.key == key })
                out += made.isEmpty ? group.flatMap { plain($0, W) } : made
                continue
            }
            out += plain(s, W)
        }
        return out
    }

    static func ms(_ date: Date) -> Int64 { Int64((date.timeIntervalSince1970 * 1000).rounded()) }

    /// ملء شكل مغلق: خط بلا سماكة يحمل المضلع (إطاره خط آخر يُرسم فوقه).
    private static func fillStroke(_ f: ShapeFill, _ W: CGFloat) -> AStroke {
        var a = AStroke()
        a.k = "BALLPOINT"
        a.color = f.color | 0xFF000000
        a.w = 0
        a.t = ms(Date(timeIntervalSinceReferenceDate: f.key))
        a.sh = true
        a.sf = true
        a.sfa = Double(f.alpha)
        for p in f.pts { a.pts += [Double(p.x / W), Double(p.y / W), 1] }
        if let first = f.pts.first { a.pts += [Double(first.x / W), Double(first.y / W), 1] }
        return a
    }

    private static func scale(_ t: CGAffineTransform) -> CGFloat { sqrt(abs(t.a * t.d - t.b * t.c)) }

    private static func median(_ v: [CGFloat]) -> CGFloat { v.isEmpty ? 0 : v.sorted()[v.count / 2] }

    /// خط عادي: نقاط على المنحنى نفسه؛ الممسوح جزئيًا تُكتب أجزاؤه الظاهرة فقط.
    private static func plain(_ s: PKStroke, _ W: CGFloat) -> [AStroke] {
        let k = max(0.0001, scale(s.transform))
        let isShape = s.randomSeed == shapeSeed
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
            byWord[wordIndex(cx, cy, words), default: []].append(c)
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

    /// الكلمة التي يقع فيها مركز الحد، أو أقربها؛ -1 إن لم توجد كلمات.
    private static func wordIndex(_ x: Double, _ y: Double, _ words: [OcrWord]) -> Int {
        guard !words.isEmpty else { return -1 }
        var best = 0
        var bestD = Double.greatestFiniteMagnitude
        for (i, w) in words.enumerated() {
            let dx = max(0, max(Double(w.l) - x, x - Double(w.r)))
            let dy = max(0, max(Double(w.t) - y, y - Double(w.b)))
            let d = dx + dy * 2
            if d < bestD { bestD = d; best = i }
        }
        return best
    }

    // ============================================================ من أندرويد إلى PencilKit

    struct Decoded {
        var drawing = PKDrawing()
        var fills: [ShapeFill] = []
        var converted: [ConvertedLine] = []
    }

    /// يبني حبر الصفحة من خطوط أندرويد. page = معرّف الصفحة (للملء والنص المحوَّل).
    static func decode(_ list: [AStroke], width W: CGFloat, page: String = "") -> Decoded {
        var out = Decoded()
        guard W > 0 else { return out }
        func date(_ i: Int) -> Date {
            list[i].t > 0 ? Date(timeIntervalSince1970: Double(list[i].t) / 1000) : Date(timeIntervalSinceReferenceDate: Double(i + 1))
        }
        // الحروف المحوّلة: أسطر، لكل سطر وقت إنشاء واحد لا يشاركه خط آخر (هو مفتاح السطر)
        var used = Set<Double>()
        for i in list.indices where !(list[i].fl && !list[i].wd.isEmpty) { used.insert(date(i).timeIntervalSinceReferenceDate) }
        var lineDate: [Int: Date] = [:]
        for ln in lines(list) {
            var d = date(ln.items.min { list[$0].t < list[$1].t } ?? ln.items[0])
            while used.contains(d.timeIntervalSinceReferenceDate) { d = d.addingTimeInterval(0.001) }
            used.insert(d.timeIntervalSinceReferenceDate)
            for i in ln.items { lineDate[i] = d }
            let bases = ln.items.compactMap { list[$0].line?.base }.sorted()
            let ems = ln.items.compactMap { list[$0].line?.em }.sorted()
            let bottom = ln.words.map { Double($0.b) }.max() ?? 0
            let height = (ln.words.map { Double($0.b - $0.t) }.max() ?? 0.02)
            out.converted.append(ConvertedLine(
                id: UUID().uuidString, page: page, key: d.timeIntervalSinceReferenceDate, words: ln.words,
                baseline: CGFloat(bases.isEmpty ? bottom : bases[bases.count / 2]) * W,
                size: CGFloat(ems.isEmpty ? height : ems[ems.count / 2]) * W,
                minX: (ln.words.map { $0.l }.min() ?? 0) * W, maxX: (ln.words.map { $0.r }.max() ?? 0) * W,
                rtl: ln.rtl, font: "", alts: nil))
        }
        var strokes: [PKStroke] = []
        for (i, a) in list.enumerated() where a.count > 0 {
            let color = UIColor(argb: a.color | (a.color <= 0xFFFFFF ? 0xFF000000 : 0))
            if a.fl {
                strokes += filled(a, W, color, lineDate[i] ?? date(i))
                continue
            }
            let d = date(i)
            let pts = (0..<a.count).map { CGPoint(x: CGFloat(a.x($0)) * W, y: CGFloat(a.y($0)) * W) }
            if a.sf && pts.count >= 3 {
                var poly = pts
                if let f = poly.first, let l = poly.last, f == l { poly.removeLast() }
                out.fills.append(ShapeFill(id: UUID().uuidString, page: page, pts: poly, color: a.color | 0xFF000000,
                                           alpha: CGFloat(a.sfa), key: d.timeIntervalSinceReferenceDate))
                // خط الملء وحده (بلا سماكة) لا يُرسم: إطاره خط آخر
                if CGFloat(a.w) * W < 0.05 { continue }
            }
            let width = max(0.2, CGFloat(a.w) * W * (a.k == "PENCIL" ? 0.85 : 1))
            if a.sh || a.ls != 0 {
                let style = LineStyle(rawValue: a.ls) ?? .solid
                let paths = style == .solid ? [pts] : StrokeKit.split(pts, style: style, width: width)
                let ink = a.k == "HIGHLIGHTER" ? PKInk(.marker, color: color.withAlphaComponent(0.6)) : PKInk(.monoline, color: color)
                strokes += StrokeKit.strokes(paths.map { dense($0) }, ink: ink, width: width, seed: shapeSeed, date: d)
                continue
            }
            strokes.append(pen(a, W, color, d))
        }
        out.drawing = PKDrawing(strokes: strokes)
        return out
    }

    /// نقاط كثيفة على الخط المستقيم بين كل نقطتين.
    static func dense(_ pts: [CGPoint]) -> [CGPoint] {
        guard pts.count > 1 else { return pts }
        var out: [CGPoint] = [pts[0]]
        for i in 1..<pts.count {
            let a = pts[i - 1], b = pts[i]
            let n = max(1, Int((hypot(b.x - a.x, b.y - a.y) / gap).rounded(.up)))
            for j in 1...n {
                let f = CGFloat(j) / CGFloat(n)
                out.append(CGPoint(x: a.x + (b.x - a.x) * f, y: a.y + (b.y - a.y) * f))
            }
        }
        return out
    }

    /// خط قلم: سماكة كل نقطة كما يرسمها أندرويد لذلك القلم.
    private static func pen(_ a: AStroke, _ W: CGFloat, _ color: UIColor, _ date: Date) -> PKStroke {
        let base = CGFloat(a.w) * W
        func size(_ p: CGFloat) -> CGFloat {
            switch a.k {
            case "BALLPOINT", "HIGHLIGHTER": return base
            case "PENCIL": return base * 0.85
            case "BRUSH": return base * (0.15 + 1.5 * p)
            default: return base * (0.4 + p)
            }
        }
        let ink: PKInk
        switch a.k {
        case "BALLPOINT": ink = PKInk(.monoline, color: color)
        case "PENCIL": ink = PKInk(.pencil, color: color)
        case "BRUSH": ink = PKInk(.watercolor, color: color)
        case "HIGHLIGHTER": ink = PKInk(.marker, color: color.withAlphaComponent(0.6))
        default: ink = PKInk(.fountainPen, color: color)
        }
        var cp: [PKStrokePoint] = []
        func add(_ l: CGPoint, _ p: CGFloat) {
            let s = max(0.2, size(max(0, min(1, p))))
            cp.append(PKStrokePoint(location: l, timeOffset: TimeInterval(cp.count) * 0.005, size: CGSize(width: s, height: s),
                                    opacity: 1, force: max(0, min(1, p)), azimuth: 0, altitude: .pi / 2))
        }
        var prev = CGPoint(x: CGFloat(a.x(0)) * W, y: CGFloat(a.y(0)) * W)
        var prevP = CGFloat(a.p(0))
        add(prev, prevP)
        for i in 1..<max(1, a.count) {
            let l = CGPoint(x: CGFloat(a.x(i)) * W, y: CGFloat(a.y(i)) * W)
            let p = CGFloat(a.p(i))
            let n = max(1, Int((hypot(l.x - prev.x, l.y - prev.y) / gap).rounded(.up)))
            for j in 1...n {
                let f = CGFloat(j) / CGFloat(n)
                add(CGPoint(x: prev.x + (l.x - prev.x) * f, y: prev.y + (l.y - prev.y) * f), prevP + (p - prevP) * f)
            }
            prev = l
            prevP = p
        }
        if cp.count == 1 { add(CGPoint(x: prev.x + 0.05, y: prev.y), prevP) }
        return PKStroke(ink: ink, path: PKStrokePath(controlPoints: cp, creationDate: date))
    }

    /// حدود الحرف المملوء (ضغط سالب = بداية حد جديد).
    static func contours(_ a: AStroke, _ W: CGFloat) -> [[CGPoint]] {
        var out: [[CGPoint]] = []
        var cur: [CGPoint] = []
        for i in 0..<a.count {
            if a.p(i) < 0 && !cur.isEmpty { out.append(cur); cur = [] }
            cur.append(CGPoint(x: CGFloat(a.x(i)) * W, y: CGFloat(a.y(i)) * W))
        }
        if !cur.isEmpty { out.append(cur) }
        return out.filter { $0.count >= 3 }
    }

    /// حرف مملوء كحبر PencilKit: حدوده وخطوط متقاربة تملأ داخله (كما يصنعها GlyphInk).
    private static func filled(_ a: AStroke, _ W: CGFloat, _ color: UIColor, _ date: Date) -> [PKStroke] {
        let polys = contours(a, W)
        guard !polys.isEmpty else { return [] }
        let all = polys.flatMap { $0 }
        let h = (all.map { $0.y }.max() ?? 0) - (all.map { $0.y }.min() ?? 0)
        let size = a.line.map { CGFloat($0.em) * W } ?? h
        let wOut = max(0.35, max(CGFloat(a.w) * W, size * 0.04))
        let wIn = max(0.6, size * 0.065)
        var paths: [[CGPoint]] = []
        for (y, x0, x1) in GlyphInk.scanlines(polys, step: wIn * 0.8) {
            let inset = min(wIn / 2, (x1 - x0) / 2)
            let l = x0 + inset, r = x1 - inset
            paths.append(r > l ? [CGPoint(x: l, y: y), CGPoint(x: r, y: y)]
                               : [CGPoint(x: (x0 + x1) / 2, y: y), CGPoint(x: (x0 + x1) / 2 + 0.01, y: y)])
        }
        let ink = PKInk(.monoline, color: color)
        var out = StrokeKit.strokes(paths, ink: ink, width: wIn, seed: glyphSeed, date: date)
        out += StrokeKit.strokes(polys.map { $0 + [$0[0]] }, ink: ink, width: wOut, seed: glyphSeed, date: date)
        return out
    }

    /// سطر حروف محوّلة: فهارس خطوطه وكلماته بترتيب القراءة (نفس ConvertedWords.read في أندرويد).
    struct WordLine {
        var items: [Int]
        var words: [OcrWord]
        var rtl: Bool
    }

    static func lines(_ list: [AStroke]) -> [WordLine] {
        struct Box { var text: String; var l, t, r, b, em: Double; var items: [Int] }
        var byWord: [Int64: [Int]] = [:]
        var order: [Int64] = []
        for (i, a) in list.enumerated() where a.fl && !a.wd.isEmpty && a.count > 0 {
            if byWord[a.wi] == nil { order.append(a.wi) }
            byWord[a.wi, default: []].append(i)
        }
        var boxes: [Box] = []
        for id in order {
            let items = (byWord[id] ?? []).compactMap { i -> (Int, (l: Double, t: Double, r: Double, b: Double), Double)? in
                guard let bb = list[i].bounds else { return nil }
                return (i, bb, list[i].line?.em ?? (bb.b - bb.t))
            }.sorted { $0.1.l < $1.1.l }
            var cur: Int? = nil
            for (i, bb, em) in items {
                if let c = cur, bb.l - boxes[c].r < 1.5 * boxes[c].em,
                   abs((bb.t + bb.b) / 2 - (boxes[c].t + boxes[c].b) / 2) < max(boxes[c].em, em) {
                    boxes[c].l = min(boxes[c].l, bb.l); boxes[c].t = min(boxes[c].t, bb.t)
                    boxes[c].r = max(boxes[c].r, bb.r); boxes[c].b = max(boxes[c].b, bb.b)
                    boxes[c].items.append(i)
                } else {
                    boxes.append(Box(text: list[i].wd, l: bb.l, t: bb.t, r: bb.r, b: bb.b, em: em, items: [i]))
                    cur = boxes.count - 1
                }
            }
        }
        var grouped: [[Box]] = []
        for bx in boxes.sorted(by: { ($0.t + $0.b) / 2 < ($1.t + $1.b) / 2 }) {
            if let last = grouped.last, last.contains(where: { o in
                min(o.b, bx.b) - max(o.t, bx.t) > 0.5 * min(o.b - o.t, bx.b - bx.t)
            }) {
                grouped[grouped.count - 1].append(bx)
            } else {
                grouped.append([bx])
            }
        }
        return grouped.map { line in
            let arabic = line.filter { b in b.text.unicodeScalars.contains { $0.value >= 0x0600 && $0.value <= 0x06FF } }.count
            let rtl = arabic * 2 > line.count
            let ordered = rtl ? line.sorted { $0.r > $1.r } : line.sorted { $0.l < $1.l }
            return WordLine(items: ordered.flatMap { $0.items },
                            words: ordered.map { OcrWord(text: $0.text, l: CGFloat($0.l), t: CGFloat($0.t), r: CGFloat($0.r), b: CGFloat($0.b), line: 0) },
                            rtl: rtl)
        }
    }
}
