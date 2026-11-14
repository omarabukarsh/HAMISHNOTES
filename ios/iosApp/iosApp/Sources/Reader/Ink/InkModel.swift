import Foundation
#if canImport(CoreGraphics)
import CoreGraphics
#endif

// محرك الحبر الخاص بهامش (بدل PencilKit): نفس نموذج تطبيق أندرويد (Models.kt) — كل خط نقاط
// (x، y، ضغط) مع نوع القلم وخصائصه. الفرق الوحيد: الإحداثيات هنا بنقاط الصفحة (لا نسبية لعرضها)،
// لأن كل ما على صفحة الآيباد (النصوص والصور والملاحظات) بنقاط الصفحة. التحويل إلى النسبي عند التصدير.
// هذا الملف بلا UIKit: يُختبر على أي حاسوب.

/// نقطة في خط: الموضع بنقاط الصفحة، والضغط 0…1. في الحروف المملوءة: ضغط سالب = بداية حد جديد.
struct InkPt: Equatable {
    var x: CGFloat
    var y: CGFloat
    var p: CGFloat

    init(_ x: CGFloat, _ y: CGFloat, _ p: CGFloat = 0.5) { self.x = x; self.y = y; self.p = p }
    init(_ c: CGPoint, _ p: CGFloat = 0.5) { x = c.x; y = c.y; self.p = p }

    var cg: CGPoint { CGPoint(x: x, y: y) }
}

/// خط واحد على الصفحة (نفس Ink في أندرويد).
struct InkStroke: Equatable {
    var pts: [InkPt]
    var color: UInt32 = 0xFF000000
    /// سماكة الخط بنقاط الصفحة. في الحروف المملوءة: تعريض إضافي حول الحرف (0 = بلا).
    var width: CGFloat = 1
    var tool: Tool = .fountain
    /// وقت كتابة الخط (ملّي ثانية منذ 1970)؛ 0 = غير معروف. خطوط السطر المحوَّل الواحد تشترك فيه (مفتاح السطر).
    var t: Int64 = 0
    var style: LineStyle = .solid
    /// رُسم بأداة الأشكال أو حُوّل إلى شكل: زواياه حادة وعرضه ثابت.
    var shape = false
    /// حرف مملوء من خط (تجميل خط اليد): النقاط حدود شكل الحرف.
    var fill = false
    /// للحروف المملوءة: خط القاعدة وحجم الخط كنسبة من ارتفاع حدود الحرف. -1 = غير معروف.
    var lineF: CGFloat = -1
    var emF: CGFloat = 0
    /// شكل مغلق يُملأ بلونه (الملء يُرسم تحت الكتابة).
    var shapeFill = false
    var fillAlpha: CGFloat = InkStroke.defaultFillAlpha
    /// للحروف المحوّلة: الكلمة التي ينتمي إليها الحرف ورقمها.
    var word = ""
    var wid: Int64 = 0

    static let defaultFillAlpha: CGFloat = 0.2

    init(pts: [InkPt], color: UInt32 = 0xFF000000, width: CGFloat = 1, tool: Tool = .fountain, t: Int64 = 0,
         style: LineStyle = .solid, shape: Bool = false) {
        self.pts = pts
        self.color = color
        self.width = width
        self.tool = tool
        self.t = t
        self.style = style
        self.shape = shape
    }

    /// خط بعرض ثابت من نقاط (للأشكال).
    init(points: [CGPoint], color: UInt32, width: CGFloat, tool: Tool = .ballpoint, t: Int64 = 0,
         style: LineStyle = .solid, shape: Bool = true) {
        self.init(pts: points.map { InkPt($0) }, color: color, width: width, tool: tool, t: t, style: style, shape: shape)
    }

    var isHighlighter: Bool { tool == .highlighter }

    /// يُرسم في طبقة الخلفية (تحت الكتابة): الهايلايتر وملء الأشكال.
    var inUnderlay: Bool { tool == .highlighter || shapeFill }
    /// يُرسم في طبقة الكتابة: كل شيء عدا الهايلايتر.
    var inOverlay: Bool { tool != .highlighter }

    /// حدود النقاط نفسها (بلا سماكة)؛ null إن لم تكن نقاط.
    var pointBounds: CGRect {
        guard let f = pts.first else { return .null }
        var l = f.x, r = f.x, t = f.y, b = f.y
        for p in pts.dropFirst() {
            if p.x < l { l = p.x }
            if p.x > r { r = p.x }
            if p.y < t { t = p.y }
            if p.y > b { b = p.y }
        }
        guard l.isFinite, r.isFinite, t.isFinite, b.isFinite else { return .null }
        return CGRect(x: l, y: t, width: r - l, height: b - t)
    }

    /// أقصى سماكة يُرسم بها الخط (قلم الحبر والفرشاة يتبعان الضغط).
    var maxDrawnWidth: CGFloat {
        if fill { return max(0, width) }
        if shape || style != .solid { return width }
        switch tool {
        case .fountain: return width * 1.4
        case .brush: return width * 1.65
        default: return width
        }
    }

    /// حدود الخط كما يُرسم (مع سماكته).
    var renderBounds: CGRect {
        let b = pointBounds
        if b.isNull { return .null }
        let m = maxDrawnWidth / 2 + 0.5
        return b.insetBy(dx: -m, dy: -m)
    }

    /// شكل مغلق: آخر نقطة هي الأولى.
    var isClosed: Bool {
        guard pts.count > 3, let a = pts.first, let b = pts.last else { return false }
        return a.x == b.x && a.y == b.y
    }

    /// نسخة بعد تحويل هندسي (تحريك وتكبير موحّد): المواضع والسماكة.
    func transformed(_ t: CGAffineTransform) -> InkStroke {
        var s = self
        let k = sqrt(abs(t.a * t.d - t.b * t.c))
        s.pts = pts.map { p in
            let q = p.cg.applying(t)
            return InkPt(q.x, q.y, p.p)
        }
        s.width = width * k
        return s
    }
}

/// كل خطوط صفحة (أو ملاحظة، أو الكتابة الأصلية لمربع نص).
struct InkDrawing: Equatable {
    var strokes: [InkStroke] = []

    init() {}
    init(strokes: [InkStroke]) { self.strokes = strokes }

    var isEmpty: Bool { strokes.isEmpty }

    var bounds: CGRect { strokes.reduce(CGRect.null) { $0.union($1.renderBounds) } }

    func transformed(using t: CGAffineTransform) -> InkDrawing {
        InkDrawing(strokes: strokes.map { $0.transformed(t) })
    }
}

/// مفتاح السطر المحوَّل (ConvertedLine.key): ثوانٍ منذ 2001 كما كان مع PencilKit، ويقابله وقت الخط بالملّي ثانية.
enum InkKey {
    private static let epoch: Double = 978_307_200

    static func ms(_ key: Double) -> Int64 {
        let v = ((key + epoch) * 1000).rounded()
        guard v.isFinite, abs(v) < 9e18 else { return 0 }
        return Int64(v)
    }

    static func key(_ ms: Int64) -> Double { Double(ms) / 1000 - epoch }

    static func now() -> Int64 { Int64((Date().timeIntervalSince1970 * 1000).rounded()) }

    /// وقت لا يستعمله خط في القائمة (مفتاح جديد لسطر محوَّل أو لنسخة منه).
    static func unused(in strokes: [InkStroke], from start: Int64? = nil) -> Int64 {
        let taken = Set(strokes.map { $0.t })
        var t = start ?? now()
        while taken.contains(t) { t += 1 }
        return t
    }
}

// ---------------------------------------------------------------- الحفظ

extension Tool {
    /// اسم القلم كما يحفظه أندرويد.
    var inkName: String {
        switch self {
        case .ballpoint: return "BALLPOINT"
        case .fountain: return "FOUNTAIN"
        case .pencil: return "PENCIL"
        case .brush: return "BRUSH"
        case .highlighter: return "HIGHLIGHTER"
        case .eraser, .laser: return "FOUNTAIN"
        }
    }

    init(inkName: String) {
        switch inkName {
        case "BALLPOINT": self = .ballpoint
        case "PENCIL": self = .pencil
        case "BRUSH": self = .brush
        case "HIGHLIGHTER": self = .highlighter
        default: self = .fountain
        }
    }
}

extension InkDrawing {
    /// علامة ملفات الحبر الجديدة (الملفات القديمة بصيغة PencilKit الثنائية).
    static let magic = "{\"hamishInk\":"

    /// نص JSON قصير: {"hamishInk":1,"s":[{…خط…}]} بمفاتيح أندرويد نفسها والإحداثيات بنقاط الصفحة.
    func dataRepresentation() -> Data {
        var s = InkDrawing.magic + "1,\"s\":["
        s.reserveCapacity(64 + strokes.reduce(0) { $0 + $1.pts.count * 18 })
        for (i, st) in strokes.enumerated() {
            if i > 0 { s += "," }
            InkDrawing.write(st, &s)
        }
        s += "]}"
        return Data(s.utf8)
    }

    private static func num(_ v: CGFloat, _ digits: Int, _ s: inout String) {
        guard v.isFinite else { s += "0"; return }
        var k: CGFloat = 1
        for _ in 0..<digits { k *= 10 }
        let r = (v * k).rounded()
        guard abs(r) < 9e15 else { s += "0"; return }
        var n = Int64(r)
        if n < 0 { s += "-"; n = -n }
        let whole = n / Int64(k), frac = n % Int64(k)
        s += String(whole)
        if frac != 0 {
            var f = String(frac)
            while f.count < digits { f = "0" + f }
            while f.hasSuffix("0") { f.removeLast() }
            s += "." + f
        }
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
                if u.value < 0x20 { s += "\\u" + String(format: "%04x", u.value) } else { s.unicodeScalars.append(u) }
            }
        }
        s += "\""
    }

    private static func write(_ st: InkStroke, _ s: inout String) {
        s += "{\"c\":" + String(Int(Int32(bitPattern: st.color)))
        s += ",\"w\":"
        num(st.width, 3, &s)
        s += ",\"k\":\"" + st.tool.inkName + "\",\"t\":" + String(st.t)
        if st.style != .solid { s += ",\"ls\":" + String(st.style.rawValue) }
        if st.shape { s += ",\"sh\":true" }
        if st.fill { s += ",\"fl\":true" }
        if st.lineF >= 0 {
            s += ",\"lf\":"
            num(st.lineF, 5, &s)
            s += ",\"ef\":"
            num(st.emF, 5, &s)
        }
        if st.shapeFill {
            s += ",\"sf\":true,\"sfa\":"
            num(st.fillAlpha, 3, &s)
        }
        if !st.word.isEmpty {
            s += ",\"wd\":"
            quote(st.word, &s)
            s += ",\"wi\":" + String(st.wid)
        }
        s += ",\"p\":["
        for (i, p) in st.pts.enumerated() {
            if i > 0 { s += "," }
            num(p.x, 2, &s)
            s += ","
            num(p.y, 2, &s)
            s += ","
            num(p.p, 3, &s)
        }
        s += "]}"
    }

    /// يقرأ ملف حبر جديدًا؛ nil إن لم يكن بهذه الصيغة (ملف PencilKit قديم مثلًا).
    static func parse(_ data: Data) -> InkDrawing? {
        guard data.count >= magic.utf8.count, data.prefix(magic.utf8.count) == Data(magic.utf8),
              let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let list = root["s"] as? [[String: Any]] else { return nil }
        return InkDrawing(strokes: list.compactMap { InkStroke(json: $0) })
    }
}

extension InkStroke {
    private static func d(_ v: Any?) -> CGFloat? {
        guard let n = v as? NSNumber else { return nil }
        let x = n.doubleValue
        return x.isFinite ? CGFloat(x) : nil
    }

    /// خط من قاموس JSON (مفاتيح أندرويد). scale يضرب المواضع والسماكة (1 للملفات المحفوظة بنقاط الصفحة،
    /// وعرض الصفحة لملفات أندرويد النسبية).
    init?(json o: [String: Any], scale: CGFloat = 1) {
        guard let raw = o["p"] as? [Any] else { return nil }
        var pts: [InkPt] = []
        pts.reserveCapacity(raw.count / 3)
        var i = 0
        while i + 2 < raw.count {
            let x = InkStroke.d(raw[i]) ?? 0, y = InkStroke.d(raw[i + 1]) ?? 0, p = InkStroke.d(raw[i + 2]) ?? 0.5
            pts.append(InkPt(x * scale, y * scale, p))
            i += 3
        }
        guard !pts.isEmpty else { return nil }
        self.init(pts: pts)
        if let c = o["c"] as? NSNumber {
            var argb = UInt32(truncatingIfNeeded: c.int64Value)
            if argb <= 0xFFFFFF { argb |= 0xFF000000 }
            color = argb
        }
        width = max(0, (InkStroke.d(o["w"]) ?? 0.003) * scale)
        tool = Tool(inkName: o["k"] as? String ?? "FOUNTAIN")
        t = (o["t"] as? NSNumber)?.int64Value ?? 0
        style = LineStyle(rawValue: (o["ls"] as? NSNumber)?.intValue ?? 0) ?? .solid
        shape = (o["sh"] as? NSNumber)?.boolValue ?? false
        fill = (o["fl"] as? NSNumber)?.boolValue ?? false
        lineF = InkStroke.d(o["lf"]) ?? -1
        emF = InkStroke.d(o["ef"]) ?? 0
        shapeFill = (o["sf"] as? NSNumber)?.boolValue ?? false
        fillAlpha = InkStroke.d(o["sfa"]) ?? InkStroke.defaultFillAlpha
        word = o["wd"] as? String ?? ""
        wid = (o["wi"] as? NSNumber)?.int64Value ?? 0
    }
}
