import Foundation

// نقل مباشر لـ Summary.kt و TextTidy.kt من تطبيق أندرويد، ليظهر الملخص بنفس الشكل تمامًا.

/// جزء من الملخص: عنوان، فقرة، أو جدول (دون أسماء الصفحات).
enum SBlock {
    case heading(text: String, marks: [TextMark], key: String)
    /// emphasis = أجزاء تظهر بخط عريض وتظليل (نص ظُلّل مرتين).
    case para(text: String, emphasis: [String], marks: [TextMark], key: String)
    /// header = الصف الأول عناوين أعمدة.
    case table(rows: [[String]], marks: [TextMark], key: String, header: Bool)

    var key: String {
        switch self {
        case .heading(_, _, let k), .para(_, _, _, let k), .table(_, _, let k, _): return k
        }
    }

    var marks: [TextMark] {
        switch self {
        case .heading(_, let m, _), .para(_, _, let m, _), .table(_, let m, _, _): return m
        }
    }

    func withKey(_ k: String) -> SBlock {
        switch self {
        case let .heading(t, m, _): return .heading(text: t, marks: m, key: k)
        case let .para(t, e, m, _): return .para(text: t, emphasis: e, marks: m, key: k)
        case let .table(r, m, _, h): return .table(rows: r, marks: m, key: k, header: h)
        }
    }
}

/// جدول من صنع المستخدم أو جدول عدّله.
struct TableGroup: Codable, Equatable {
    var id: String
    var keys: [String]
    var oneRow = false
    var cols = 0
    var rows: [[String]]? = nil
    var header = true
}

/// تعديلات المستخدم على الملخص فوق الترتيب التلقائي.
struct SummaryEdits: Codable, Equatable {
    var order: [String] = []
    var kinds: [String: String] = [:]
    var tables: [TableGroup] = []
    /// الخريطة الذهنية كما وضعها المستخدم (nil = لم تُبنَ بعد).
    var map: MapEdits? = nil

    var isEmpty: Bool { order.isEmpty && kinds.isEmpty && tables.isEmpty && (map?.isEmpty ?? true) }
}

extension TableGroup {
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        keys = c.opt(.keys, [])
        oneRow = c.opt(.oneRow, false)
        cols = c.opt(.cols, 0)
        rows = try? c.decodeIfPresent([[String]].self, forKey: .rows)
        header = c.opt(.header, true)
    }
}

extension SummaryEdits {
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        order = c.opt(.order, [])
        kinds = c.opt(.kinds, [:])
        tables = c.opt(.tables, [])
        map = try? c.decodeIfPresent(MapEdits.self, forKey: .map)
    }
}

private let TERMINAL: Set<Character> = [".", "!", "?", "؟", "…"]

private extension Character {
    var isLowerLetter: Bool { isLetter && isLowercase }
}

private func rx(_ p: String) -> NSRegularExpression { try! NSRegularExpression(pattern: p, options: []) }

private extension String {
    func replacing(_ re: NSRegularExpression, _ tmpl: String) -> String {
        re.stringByReplacingMatches(in: self, range: NSRange(startIndex..., in: self), withTemplate: tmpl)
    }
    func matches(_ re: NSRegularExpression) -> Bool {
        guard let m = re.firstMatch(in: self, range: NSRange(startIndex..., in: self)) else { return false }
        return m.range.location == 0 && m.range.length == (self as NSString).length
    }
}

enum TextTidy {
    private static let junk = rx("^[|_~^`¦•·]+$")
    private static let abbreviations: Set<String> = ["e.g", "i.e", "etc", "vs", "dr", "mr", "mrs", "ms", "prof", "fig", "no", "vol", "approx", "al"]
    private static let rHyphen = rx("(\\p{L})[-‐‑]\\s*\\n\\s*(\\p{Ll})")
    private static let rSpaceBefore = rx("\\s+([,.;:!?،؛؟)\\]}…])")
    private static let rSpaceAfterOpen = rx("([(\\[{])\\s+")
    private static let rSpaceAfterPunct = rx("([,;!?،؛؟])(?=[\\p{L}\\p{N}])")
    private static let rGlued = rx("(?<=\\p{L}{2})([.:])(?=\\p{Lu}\\p{Ll})")
    private static let rRepeat = rx("([,،;؛])\\1+")
    private static let rDots = rx("\\.{4,}")
    private static let rDupWord = rx("(?i)\\b(\\p{L}{3,})\\s+\\1\\b")
    private static let rSpaces = rx(" {2,}")
    private static let rZW = rx("[\\u200B-\\u200D\\uFEFF]")

    /// ينظّف نصًا واحدًا ويعيده فقرة واحدة.
    static func clean(_ raw: String) -> String {
        var s = raw.replacingOccurrences(of: "\r", with: "\n").replacingOccurrences(of: "\t", with: " ")
            .replacingOccurrences(of: "\u{00A0}", with: " ").replacing(rZW, "")
        s = s.replacing(rHyphen, "$1$2")
        s = s.components(separatedBy: "\n").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }.joined(separator: " ")
        s = s.components(separatedBy: " ").filter { !$0.matches(junk) }.joined(separator: " ")
        s = s.replacing(rSpaceBefore, "$1")
        s = s.replacing(rSpaceAfterOpen, "$1")
        s = s.replacing(rSpaceAfterPunct, "$1 ")
        s = s.replacing(rGlued, "$1 ")
        s = s.replacing(rRepeat, "$1")
        s = s.replacing(rDots, "...")
        s = s.replacing(rDupWord, "$1")
        s = s.replacing(rSpaces, " ")
        return s.trimmingCharacters(in: .whitespaces)
    }

    /// يقسم فقرة نظيفة إلى جمل، مع تجنّب القطع بعد الاختصارات والحروف المفردة.
    static func sentences(_ text: String) -> [String] {
        var out: [String] = []
        var cur = ""
        let words = text.split(separator: " ").map(String.init)
        for (i, w) in words.enumerated() {
            if !cur.isEmpty { cur += " " }
            cur += w
            guard let last = w.last, TERMINAL.contains(last) else { continue }
            var bare = w
            while let l = bare.last, TERMINAL.contains(l) { bare.removeLast() }
            bare = bare.lowercased()
            let isAbbrev = abbreviations.contains(bare) || (bare.count == 1 && bare.first!.isLetter)
            let next = i + 1 < words.count ? words[i + 1] : nil
            let nextLower = next?.first?.isLowerLetter ?? false
            if !isAbbrev && !nextLower {
                out.append(cur)
                cur = ""
            }
        }
        if !cur.isEmpty { out.append(cur) }
        return out.map(finishSentence).filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
    }

    private static func finishSentence(_ s: String) -> String {
        var t = s.trimmingCharacters(in: .whitespaces)
        guard let first = t.first else { return t }
        if first.isLowerLetter { t = first.uppercased() + t.dropFirst() }
        let wordCount = t.filter { $0 == " " }.count + 1
        if wordCount >= 5, let l = t.last, l.isLetter || l.isNumber { t += "." }
        return t
    }

    /// نص واحد مرتب كجمل، سطر لكل جملة.
    static func tidy(_ raw: String) -> String { sentences(clean(raw)).joined(separator: "\n") }

    /// زر «ترتيب النص» في نافذة التعديل: يرتب الفقرات ويترك العناوين والجداول.
    static func tidyEditable(_ text: String) -> String {
        text.components(separatedBy: rxSplit).map { para -> String in
            let t = para.trimmingCharacters(in: .whitespacesAndNewlines)
            if t.hasPrefix("#") || t.components(separatedBy: "\n").allSatisfy({ $0.contains("|") }) { return t }
            return sentences(clean(t)).joined(separator: " ")
        }.joined(separator: "\n\n")
    }

    private static let rxSplit = "\n\n"
}

enum Summary {
    private enum Piece {
        case heading(String)
        case table([[String]])
        case para(String)
    }

    private static let paraSplit = rx("\\n\\s*\\n")
    private static let nonWord = rx("[^\\p{L}\\p{N}]+")

    /// يقرأ صيغة نص التمييز: فقرات بسطر فارغ، "# " عنوان، " | " بين خلايا الجدول.
    private static func parse(_ text: String) -> [Piece] {
        var out: [Piece] = []
        let t = text.replacingOccurrences(of: "\r", with: "")
        let ns = t as NSString
        var parts: [String] = []
        var last = 0
        for m in paraSplit.matches(in: t, range: NSRange(location: 0, length: ns.length)) {
            parts.append(ns.substring(with: NSRange(location: last, length: m.range.location - last)))
            last = m.range.location + m.range.length
        }
        parts.append(ns.substring(from: last))
        for raw in parts {
            let lines = raw.components(separatedBy: "\n").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
            guard let first = lines.first else { continue }
            if first.hasPrefix("# ") {
                out.append(.heading(String(first.dropFirst(2)).trimmingCharacters(in: .whitespaces)))
                if lines.count > 1 { out.append(.para(lines.dropFirst().joined(separator: "\n"))) }
            } else if lines.allSatisfy({ $0.contains("|") }) {
                out.append(.table(lines.map { $0.components(separatedBy: "|").map { $0.trimmingCharacters(in: .whitespaces) } }))
            } else {
                out.append(.para(lines.joined(separator: "\n")))
            }
        }
        return out
    }

    private static func norm(_ s: String) -> String {
        s.lowercased().replacing(nonWord, " ").trimmingCharacters(in: .whitespaces)
    }

    /// هل الفقرة التالية تكمل السابقة؟
    private static func continues(_ prev: String, _ next: String) -> Bool {
        guard let end = prev.trimmingCharacters(in: .whitespaces).last else { return false }
        if end == ":" || end == "：" { return false }
        let startsLower = next.first?.isLowerLetter ?? false
        return !TERMINAL.contains(end) || startsLower
    }

    private static func emphasisOf(_ m: TextMark, _ text: String) -> [String] {
        m.level >= 2 ? [text] : m.emphasis
    }

    /// tidy = ترتيب (تنظيف، دمج، جمع المكرر كمهم)؛ false = كما التُقط.
    static func build(_ marksInOrder: [TextMark], tidy: Bool) -> [SBlock] {
        var out: [SBlock] = []
        var seen: [String: Int] = [:]
        let mode = tidy ? "t" : "r"
        for m in marksInOrder {
            for (pi, p) in parse(m.text).enumerated() {
                let key = "\(mode):\(m.id)#\(pi)"
                switch p {
                case .heading(let h):
                    let t = tidy ? TextTidy.clean(h) : h
                    if tidy, case let .heading(lt, _, _)? = out.last, norm(lt) == norm(t) { continue }
                    out.append(.heading(text: t, marks: [m], key: key))
                case .table(let rows):
                    out.append(.table(rows: rows, marks: [m], key: key, header: true))
                case .para(let text):
                    if !tidy {
                        let one = text.replacingOccurrences(of: "\n", with: " ").trimmingCharacters(in: .whitespaces)
                        out.append(.para(text: one, emphasis: emphasisOf(m, one), marks: [m], key: key))
                        continue
                    }
                    let c = TextTidy.clean(text)
                    if c.isEmpty { continue }
                    let nk = norm(c)
                    if let dup = seen[nk], case let .para(ot, oe, om, okey) = out[dup] {
                        out[dup] = .para(text: ot, emphasis: Array(NSOrderedSet(array: oe + [c])) as! [String], marks: om + [m], key: okey)
                        continue
                    }
                    if case let .para(lt, le, lm, lk)? = out.last, continues(lt, c) {
                        out[out.count - 1] = .para(text: lt + " " + c, emphasis: Array(NSOrderedSet(array: le + emphasisOf(m, c))) as! [String], marks: lm + [m], key: lk)
                    } else {
                        out.append(.para(text: c, emphasis: emphasisOf(m, c), marks: [m], key: key))
                    }
                    seen[nk] = out.count - 1
                }
            }
        }
        if !tidy { return out }
        return out.map { b in
            if case let .para(t, e, m, k) = b { return .para(text: TextTidy.sentences(t).joined(separator: " "), emphasis: e, marks: m, key: k) }
            return b
        }
    }

    private static func tableText(_ rows: [[String]]) -> String {
        rows.map { r in r.filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }.joined(separator: " — ") }.joined(separator: "; ")
    }

    private static func cellsOf(_ text: String) -> [String] {
        text.contains("|") ? text.components(separatedBy: "|").map { $0.trimmingCharacters(in: .whitespaces) } : [text]
    }

    /// الملخص كما يُعرض: البناء التلقائي ثم تعديلات المستخدم.
    static func view(_ marksInOrder: [TextMark], tidy: Bool, edits: SummaryEdits) -> [SBlock] {
        var list = build(marksInOrder, tidy: tidy)
        if edits.isEmpty { return list }
        // 1) عنوان / نص عادي
        list = list.map { b in
            switch (edits.kinds[b.key], b) {
            case ("h", .para(let t, _, let m, let k)): return .heading(text: t, marks: m, key: k)
            case ("h", .table(let r, let m, let k, _)): return .heading(text: tableText(r), marks: m, key: k)
            case ("p", .heading(let t, let m, let k)): return .para(text: t, emphasis: [], marks: m, key: k)
            case ("p", .table(let r, let m, let k, _)): return .para(text: tableText(r), emphasis: [], marks: m, key: k)
            default: return b
            }
        }
        // 2) جداول المستخدم
        for g in edits.tables {
            let keySet = Set(g.keys)
            let members = g.keys.compactMap { k in list.first { $0.key == k } }
            if members.isEmpty { continue }
            var rows: [[String]] = []
            if let r = g.rows {
                rows = r
            } else {
                var texts: [String] = []
                for m in members {
                    switch m {
                    case .table(let r, _, _, _): rows += r
                    case .heading(let t, _, _): texts.append(t)
                    case .para(let t, _, _, _): texts.append(t)
                    }
                }
                if g.cols > 0 {
                    var i = 0
                    while i < texts.count {
                        let r = Array(texts[i..<min(texts.count, i + g.cols)])
                        rows.append(r + Array(repeating: "", count: g.cols - r.count))
                        i += g.cols
                    }
                } else if g.oneRow {
                    if !texts.isEmpty { rows.append(texts) }
                } else {
                    texts.forEach { rows.append(cellsOf($0)) }
                }
            }
            var seenIds = Set<String>()
            let ms = members.flatMap { $0.marks }.filter { seenIds.insert($0.id).inserted }
            let table = SBlock.table(rows: rows, marks: ms, key: "g:" + g.id, header: g.header)
            var out: [SBlock] = []
            var placed = false
            for b in list {
                if keySet.contains(b.key) {
                    if !placed { out.append(table); placed = true }
                } else { out.append(b) }
            }
            list = out
        }
        // 3) الترتيب اليدوي
        if !edits.order.isEmpty {
            var rank: [String: Int] = [:]
            for (i, k) in edits.order.enumerated() { rank[k] = i }
            var last = -1.0
            var step = 0
            let weighted = list.map { b -> (Double, SBlock) in
                if let r = rank[b.key] {
                    last = Double(r)
                    step = 0
                    return (last, b)
                }
                step += 1
                return (last + Double(step) * 1e-4, b)
            }
            list = weighted.enumerated().sorted { a, b in a.element.0 == b.element.0 ? a.offset < b.offset : a.element.0 < b.element.0 }.map { $0.element.1 }
        }
        return list
    }

    /// للعرض والحفظ: تصفية بلون واحد، أو تجميع حسب اللون (كل لون قسم بعنوان تصنيفه).
    static func display(_ marksInOrder: [TextMark], tidy: Bool, edits: SummaryEdits, filter: UInt32?, byColor: Bool, labelOf: (UInt32) -> String) -> [SBlock] {
        let marks = filter == nil ? marksInOrder : marksInOrder.filter { $0.color == filter }
        if !byColor { return view(marks, tidy: tidy, edits: edits) }
        var out: [SBlock] = []
        var colors: [UInt32] = []
        for m in marks where !colors.contains(m.color) { colors.append(m.color) }
        for col in colors {
            let blocks = view(marks.filter { $0.color == col }, tidy: tidy, edits: edits)
            if blocks.isEmpty { continue }
            out.append(.heading(text: labelOf(col), marks: [], key: "lbl:\(col)"))
            out += blocks
        }
        return out
    }

    /// نطاقات الأجزاء المهمة داخل نص الفقرة.
    static func emphasisRanges(_ text: String, _ emphasis: [String]) -> [Range<String.Index>] {
        var out: [Range<String.Index>] = []
        for e in emphasis {
            var key = e.trimmingCharacters(in: .whitespaces)
            while let l = key.last, TERMINAL.contains(l) { key.removeLast() }
            if key.count < 2 { continue }
            var from = text.startIndex
            while let r = text.range(of: key, options: .caseInsensitive, range: from..<text.endIndex) {
                out.append(r)
                from = r.upperBound
            }
        }
        return out.sorted { $0.lowerBound < $1.lowerBound }
    }

    /// نص الملخص كاملًا للنسخ والمشاركة.
    static func plainText(_ title: String, _ blocks: [SBlock]) -> String {
        var s = title + "\n"
        for b in blocks {
            s += "\n"
            switch b {
            case .heading(let t, _, _): s += t + "\n"
            case .para(let t, _, _, _): s += t + "\n"
            case .table(let r, _, _, _): r.forEach { s += $0.joined(separator: " | ") + "\n" }
            }
        }
        return s.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
