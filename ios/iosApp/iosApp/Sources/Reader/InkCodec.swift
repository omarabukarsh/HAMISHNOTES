import UIKit

// الحبر بصيغة أندرويد (مخطط version 3): كل خط نقاط x,y,ضغط نسبية لعرض الصفحة، مع نوع القلم وخصائصه.
// محرك الحبر في الآيباد بالنموذج نفسه (بنقاط الصفحة)، فالتحويل ضرب أو قسمة على عرض الصفحة فقط.

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
    // ============================================================ من الآيباد إلى أندرويد

    /// خطوط الصفحة بصيغة أندرويد. W = عرض الصفحة بالنقاط. converted: أسطر الحروف المحوّلة في الصفحة
    /// (لربط حروف الملفات القديمة بكلماتها).
    static func encode(_ d: InkDrawing, width W: CGFloat, converted: [ConvertedLine] = []) -> [AStroke] {
        guard W > 0 else { return [] }
        return d.strokes.compactMap { s -> AStroke? in
            guard !s.pts.isEmpty else { return nil }
            var a = AStroke(s, width: W)
            if s.fill && s.word.isEmpty, let l = converted.first(where: { InkKey.ms($0.key) == s.t }) { attachWord(&a, l, W) }
            return a
        }
    }

    /// حرف من ملف قديم بلا كلمته: أقرب كلمة من سطره، وخط القاعدة والحجم منه.
    private static func attachWord(_ a: inout AStroke, _ l: ConvertedLine, _ W: CGFloat) {
        guard let bb = a.bounds, !l.words.isEmpty else { return }
        let idx = wordIndex((bb.l + bb.r) / 2, (bb.t + bb.b) / 2, l.words)
        guard idx >= 0 else { return }
        let w = l.words[idx]
        a.wd = w.text
        a.wi = a.t &* 1024 &+ Int64(idx)
        if a.lf < 0, bb.b - bb.t > 0 {
            let h = bb.b - bb.t
            let firstLine = l.words.map { $0.line }.min() ?? 0
            let top0 = l.words.filter { $0.line == firstLine }.map { Double($0.t) }.min() ?? 0
            let base = Double(l.baseline / W) + (Double(w.t) - top0)
            a.lf = max(0, (base - bb.t) / h)
            a.ef = Double(l.size / W) / h
        }
    }

    /// الكلمة التي يقع فيها مركز الحد، أو أقربها؛ -1 إن لم توجد كلمات.
    static func wordIndex(_ x: Double, _ y: Double, _ words: [OcrWord]) -> Int {
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

    // ============================================================ من أندرويد إلى الآيباد

    struct Decoded {
        var drawing = InkDrawing()
        var converted: [ConvertedLine] = []
    }

    /// يبني حبر الصفحة من خطوط أندرويد. page = معرّف الصفحة (للنص المحوَّل).
    static func decode(_ list: [AStroke], width W: CGFloat, page: String = "") -> Decoded {
        var out = Decoded()
        guard W > 0 else { return out }
        var strokes = list.map { InkStroke(android: $0, width: W) }
        // الحروف المحوّلة: أسطر، لكل سطر وقت واحد لا يشاركه خط آخر (هو مفتاح السطر)
        var used = Set<Int64>()
        for (i, a) in list.enumerated() where !(a.fl && !a.wd.isEmpty) { used.insert(strokes[i].t) }
        for ln in lines(list) {
            var t = ln.items.map { strokes[$0].t }.min() ?? InkKey.now()
            while used.contains(t) { t += 1 }
            used.insert(t)
            for i in ln.items { strokes[i].t = t }
            let bases = ln.items.compactMap { list[$0].line?.base }.sorted()
            let ems = ln.items.compactMap { list[$0].line?.em }.sorted()
            let bottom = ln.words.map { Double($0.b) }.max() ?? 0
            let height = (ln.words.map { Double($0.b - $0.t) }.max() ?? 0.02)
            out.converted.append(ConvertedLine(
                id: UUID().uuidString, page: page, key: InkKey.key(t), words: ln.words,
                baseline: CGFloat(bases.isEmpty ? bottom : bases[bases.count / 2]) * W,
                size: CGFloat(ems.isEmpty ? height : ems[ems.count / 2]) * W,
                minX: (ln.words.map { $0.l }.min() ?? 0) * W, maxX: (ln.words.map { $0.r }.max() ?? 0) * W,
                rtl: ln.rtl, font: "", alts: nil))
        }
        out.drawing = InkDrawing(strokes: strokes.filter { !$0.pts.isEmpty })
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

extension AStroke {
    /// خط الآيباد (بنقاط الصفحة) بصيغة أندرويد (نسبي لعرض الصفحة).
    init(_ s: InkStroke, width W: CGFloat) {
        self.init()
        let k = 1 / max(0.0001, Double(W))
        color = s.color
        w = Double(s.width) * k
        self.k = s.tool.inkName
        t = s.t
        ls = s.style.rawValue
        sh = s.shape
        fl = s.fill
        lf = Double(s.lineF)
        ef = Double(s.emF)
        sf = s.shapeFill
        sfa = Double(s.fillAlpha)
        wd = s.word
        wi = s.wid
        pts.reserveCapacity(s.pts.count * 3)
        for p in s.pts { pts += [Double(p.x) * k, Double(p.y) * k, Double(p.p)] }
    }
}

extension InkStroke {
    /// خط أندرويد (نسبي لعرض الصفحة) بنقاط صفحة عرضها W.
    init(android a: AStroke, width W: CGFloat) {
        let pts = (0..<a.count).map { InkPt(CGFloat(a.x($0)) * W, CGFloat(a.y($0)) * W, CGFloat(a.p($0))) }
        self.init(pts: pts)
        color = a.color | (a.color <= 0xFFFFFF ? 0xFF000000 : 0)
        width = max(0, CGFloat(a.w) * W)
        tool = Tool(inkName: a.k)
        t = a.t
        style = LineStyle(rawValue: a.ls) ?? .solid
        shape = a.sh
        fill = a.fl
        lineF = CGFloat(a.lf)
        emF = CGFloat(a.ef)
        shapeFill = a.sf
        fillAlpha = CGFloat(a.sfa)
        word = a.wd
        wid = a.wi
    }
}
