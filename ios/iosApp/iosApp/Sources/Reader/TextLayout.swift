import Foundation
import PDFKit
import Vision

// نقل مباشر لـ TextLayout.kt و MarkMerge.kt من تطبيق أندرويد: التقاط النص تحت الهايلايتر،
// فهم العناوين والجداول والفقرات، ودمج التمييز المكرر أو الملاصق. كل الإحداثيات نسبية لعرض الصفحة.

/// كلمة على الصفحة (من نص PDF أو من القراءة الضوئية).
struct OcrWord: Codable, Equatable {
    var text: String
    var l: CGFloat, t: CGFloat, r: CGFloat, b: CGFloat
    var line: Int
}

/// نقطة على الصفحة (نسبية لعرضها).
struct RelPt { var x: CGFloat; var y: CGFloat }

private let TERMINAL: Set<Character> = [".", "!", "?", "؟", "…"]

private func median(_ v: [CGFloat]) -> CGFloat { let s = v.sorted(); return s[s.count / 2] }

enum TextLayout {
    private static func h(_ w: OcrWord) -> CGFloat { w.b - w.t }

    /// الارتفاع الوسيط لكلمات الصفحة = حجم النص العادي فيها.
    static func bodyHeight(_ words: [OcrWord]) -> CGFloat {
        if words.isEmpty { return 0.012 }
        let hs = words.map { h($0) }.sorted()
        return hs[hs.count / 2]
    }

    /// يجمع الكلمات في صفوف مرئية (من الأعلى للأسفل)، والأسطر المتقابلة أفقيًا في صف واحد.
    static func visualRows(_ words: [OcrWord], _ idx: [Int]) -> [[Int]] {
        let valid = idx.filter { words.indices.contains($0) }
        if valid.isEmpty { return [] }
        struct Ln { let ids: [Int]; let t: CGFloat; let b: CGFloat; let l: CGFloat }
        let groups = Dictionary(grouping: valid) { words[$0].line }
        let lines = groups.values.map { g -> Ln in
            let ids = g.sorted { words[$0].l < words[$1].l }
            return Ln(ids: ids, t: median(ids.map { words[$0].t }), b: median(ids.map { words[$0].b }), l: ids.map { words[$0].l }.min()!)
        }.sorted { ($0.t + $0.b) / 2 < ($1.t + $1.b) / 2 }
        var rows: [[Ln]] = []
        for ln in lines {
            if let first = rows.last?.first {
                let ov = min(ln.b, first.b) - max(ln.t, first.t)
                let minH = max(1e-6, min(ln.b - ln.t, first.b - first.t))
                if ov >= 0.5 * minH {
                    rows[rows.count - 1].append(ln)
                    continue
                }
            }
            rows.append([ln])
        }
        return rows.map { r in r.sorted { $0.l < $1.l }.flatMap { $0.ids } }
    }

    /// الكلمات التي مرّ عليها خط الهايلايتر، ثم كل كلمات الصف الواقعة بين أول وآخر كلمة مغطاة.
    static func wordsUnder(_ words: [OcrWord], _ pts: [RelPt], band: CGFloat) -> [Int] {
        if words.isEmpty || pts.isEmpty { return [] }
        var samples: [RelPt] = [pts[0]]
        for i in 1..<pts.count {
            let a = pts[i - 1], c = pts[i]
            let d = hypot(c.x - a.x, c.y - a.y)
            let n = max(0, min(200, Int(d / 0.003)))
            if n > 0 {
                for k in 1...n {
                    let f = CGFloat(k) / CGFloat(n + 1)
                    samples.append(RelPt(x: a.x + (c.x - a.x) * f, y: a.y + (c.y - a.y) * f))
                }
            }
            samples.append(c)
        }
        let half = band / 2
        var hit = Set<Int>()
        for i in words.indices {
            let w = words[i]
            let slack = min(half, h(w) * 0.6)
            if samples.contains(where: { $0.x >= w.l && $0.x <= w.r && $0.y >= w.t - slack && $0.y <= w.b + slack }) { hit.insert(i) }
        }
        if hit.isEmpty { return [] }
        var out = Set<Int>()
        for row in visualRows(words, Array(words.indices)) {
            let hits = row.filter { hit.contains($0) }
            if hits.isEmpty { continue }
            let left = hits.map { words[$0].l }.min()!, right = hits.map { words[$0].r }.max()!
            row.filter { words[$0].l >= left - 1e-4 && words[$0].r <= right + 1e-4 }.forEach { out.insert($0) }
        }
        return Array(out)
    }

    /// مستطيل لكل مقطع متصل في كل صف مرئي (الفراغ الكبير بين خلايا جدول لا يُظلَّل).
    static func rectsFor(_ words: [OcrWord], _ idx: [Int]) -> [MarkRect] {
        visualRows(words, idx).flatMap { row -> [MarkRect] in
            let ws = row.map { words[$0] }.sorted { $0.l < $1.l }
            let hh = median(ws.map { $0.b - $0.t })
            var segs: [[OcrWord]] = []
            for w in ws {
                if let last = segs.last?.last, w.l - last.r <= 1.4 * hh { segs[segs.count - 1].append(w) } else { segs.append([w]) }
            }
            return segs.map { seg in
                let l = seg.map { $0.l }.min()!, t = seg.map { $0.t }.min()!, r = seg.map { $0.r }.max()!, b = seg.map { $0.b }.max()!
                let padY = (b - t) * 0.12, padX = (b - t) * 0.10
                return MarkRect(l: l - padX, t: t - padY, r: r + padX, b: b + padY)
            }
        }
    }

    /// الكلمات التي تقع مراكزها داخل مستطيلات تمييز.
    static func wordsInside(_ words: [OcrWord], _ rects: [MarkRect]) -> [Int] {
        words.indices.filter { i in
            let w = words[i]
            let cx = (w.l + w.r) / 2, cy = (w.t + w.b) / 2
            return rects.contains { cx >= $0.l && cx <= $0.r && cy >= $0.t && cy <= $0.b }
        }
    }

    fileprivate static func isArabic(_ c: Character) -> Bool {
        guard let u = c.unicodeScalars.first?.value else { return false }
        return (0x0600...0x06FF).contains(u) || (0x0750...0x077F).contains(u) || (0xFB50...0xFEFF).contains(u)
    }

    fileprivate struct Cell {
        let text: String
        let l: CGFloat, r: CGFloat
        let edge: CGFloat
    }

    fileprivate struct Row {
        let words: [OcrWord]
        let neutral: Bool
        let rtl: Bool
        let l: CGFloat, r: CGFloat, t: CGFloat, b: CGFloat, height: CGFloat
        let cells: [Cell]

        init(_ input: [OcrWord]) {
            var ar = 0, la = 0
            for w in input { for ch in w.text { if TextLayout.isArabic(ch) { ar += 1 } else if ch.isLetter { la += 1 } } }
            neutral = ar == 0 && la == 0
            rtl = ar > la
            let ws = rtl ? input.sorted { $0.r > $1.r } : input.sorted { $0.l < $1.l }
            words = ws
            l = ws.map { $0.l }.min()!
            r = ws.map { $0.r }.max()!
            t = median(ws.map { $0.t })
            b = median(ws.map { $0.b })
            let height = median(ws.map { $0.b - $0.t })
            self.height = height
            var out: [Cell] = []
            var group: [OcrWord] = []
            let isRtl = rtl
            func flush() {
                guard !group.isEmpty else { return }
                let gl = group.map { $0.l }.min()!, gr = group.map { $0.r }.max()!
                out.append(Cell(text: group.map { $0.text }.joined(separator: " "), l: gl, r: gr, edge: isRtl ? gr : gl))
                group = []
            }
            for (k, w) in ws.enumerated() {
                if k > 0 {
                    let prev = ws[k - 1]
                    let gap = rtl ? prev.l - w.r : w.l - prev.r
                    let lineChanged = w.line != prev.line
                    if gap > 1.4 * height || (lineChanged && gap > 1.0 * height) { flush() }
                }
                group.append(w)
            }
            flush()
            cells = out
        }

        var text: String { cells.map { $0.text }.joined(separator: " ") }

        var numberedHeading: Bool {
            let s = text
            let ok = s.range(of: "^\\p{Nd}+([.٫]\\p{Nd}+)*[.٫]?\\s+\\S", options: .regularExpression) != nil
            return ok && s.split(separator: " ").count <= 10 && s.trimmingCharacters(in: .whitespaces).last != "."
        }
    }

    /// جدول من صفوف متتالية. يعيد عدد الصفوف المستهلكة.
    private static func readTable(_ rows: [Row], _ start: Int, _ out: inout [[String]]) -> Int {
        let first = rows[start]
        let anchors = first.cells.map { $0.edge }
        let tol = 2.2 * first.height
        out.append(first.cells.map { $0.text })
        var gaps: [CGFloat] = []
        var j = start + 1
        func colOf(_ c: Cell) -> Int {
            var best = -1
            var bestD = CGFloat.greatestFiniteMagnitude
            for (k, a) in anchors.enumerated() {
                let d = abs(c.edge - a)
                if d < bestD { bestD = d; best = k }
            }
            return bestD <= tol ? best : -1
        }
        while j < rows.count {
            let row = rows[j], prev = rows[j - 1]
            let lh = max(row.height, prev.height)
            let gap = row.t - prev.b
            if gap > 1.8 * lh { break }
            if gaps.count >= 2 && gap > 1.4 * median(gaps) && gap > 1.0 * lh && row.cells.count >= 2 { break }
            if !row.neutral && !first.neutral && row.rtl != first.rtl && row.cells.count >= 2 { break }
            let cols = row.cells.map { colOf($0) }
            if row.cells.count >= 2 {
                if cols.contains(where: { $0 < 0 }) { break }
                var cells = Array(repeating: "", count: anchors.count)
                for (k, c) in row.cells.enumerated() {
                    let col = cols[k]
                    cells[col] = cells[col].isEmpty ? c.text : cells[col] + " " + c.text
                }
                out.append(cells)
            } else {
                let col = cols[0]
                if col < 0 || gap > 0.9 * lh { break }
                if col == 0 && anchors.count > 1 && gap > 0.5 * lh { break }
                while out[out.count - 1].count < anchors.count { out[out.count - 1].append("") }
                let last = out[out.count - 1][col]
                out[out.count - 1][col] = last.isEmpty ? row.cells[0].text : last + " " + row.cells[0].text
            }
            gaps.append(gap)
            j += 1
        }
        return j - start
    }

    /// يبني نص التمييز من الكلمات المختارة مع فهم الشكل (عناوين "# "، جداول " | "، فقرات بسطر فارغ).
    static func buildText(_ pageWords: [OcrWord], _ idx: [Int]) -> String {
        let rowsIdx = visualRows(pageWords, idx)
        if rowsIdx.isEmpty { return "" }
        let rows = rowsIdx.map { r in Row(r.map { pageWords[$0] }) }
        let body = bodyHeight(pageWords)
        let n = rows.count
        let startsTable: [Bool] = (0..<n).map { i in
            let multi = rows[i].cells.count >= 2
            let nextMulti = i < n - 1 && rows[i + 1].cells.count >= 2
            let nextCont = i < n - 1 && rows[i + 1].cells.count == 1 && rows[i + 1].t - rows[i].b < 0.9 * rows[i].height
            return multi && (nextMulti || nextCont || rows[i].cells.count >= 3)
        }
        let isHeading: [Bool] = (0..<n).map { i in
            let ratio: CGFloat = rows[i].words.count >= 2 ? 1.25 : 1.6
            return !startsTable[i] && (rows[i].height >= ratio * body || (rows[i].numberedHeading && rows[i].height >= 1.08 * body))
        }
        let blockL = rows.map { $0.l }.min()!, blockR = rows.map { $0.r }.max()!
        var out = ""
        var i = 0
        while i < n {
            if !out.isEmpty { out += "\n\n" }
            if startsTable[i] {
                var table: [[String]] = []
                let used = readTable(rows, i, &table)
                let cols = table.map { $0.count }.max() ?? 1
                out += table.map { r in (0..<cols).map { k in k < r.count ? r[k].trimmingCharacters(in: .whitespaces) : "" }.joined(separator: " | ") }.joined(separator: "\n")
                i += used
            } else if isHeading[i] {
                var j = i
                var parts: [String] = []
                while j < n && isHeading[j] && (j == i || abs(rows[j].height - rows[i].height) < 0.2 * rows[i].height) {
                    parts.append(rows[j].text)
                    j += 1
                }
                out += "# " + parts.joined(separator: " ")
                i = j
            } else {
                var j = i
                var lines: [String] = []
                while j < n && !startsTable[j] && !isHeading[j] {
                    if j > i && startsNewParagraph(rows[j - 1], rows[j], blockL, blockR) { break }
                    lines.append(rows[j].text)
                    j += 1
                }
                out += lines.joined(separator: "\n")
                i = j
            }
        }
        return out.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func startsNewParagraph(_ prev: Row, _ next: Row, _ blockL: CGFloat, _ blockR: CGFloat) -> Bool {
        let lh = max(prev.height, next.height)
        if next.t - prev.b > 0.9 * lh { return true }
        if abs(next.height - prev.height) > 0.25 * lh { return true }
        let ended = prev.text.trimmingCharacters(in: .whitespaces).last.map { TERMINAL.contains($0) } ?? false
        if prev.rtl {
            if ended && prev.l > blockL + 2.5 * lh { return true }
            if ended && next.r < blockR - 1.5 * lh { return true }
        } else {
            if ended && prev.r < blockR - 2.5 * lh { return true }
            if ended && next.l > blockL + 1.5 * lh { return true }
        }
        return false
    }

    static func area(_ rs: [MarkRect]) -> CGFloat { rs.reduce(0) { $0 + max(0, $1.r - $1.l) * max(0, $1.b - $1.t) } }

    /// مساحة التقاطع بين مجموعتي مستطيلات.
    static func overlap(_ a: [MarkRect], _ b: [MarkRect]) -> CGFloat {
        var s: CGFloat = 0
        for x in a {
            for y in b {
                let w = min(x.r, y.r) - max(x.l, y.l), hh = min(x.b, y.b) - max(x.t, y.t)
                if w > 0 && hh > 0 { s += w * hh }
            }
        }
        return s
    }

    // ---------- كلمات الصفحة ----------

    /// كلمات صفحة PDF من طبقة النص، بإحداثيات نسبية لعرض الصفحة (الأصل أعلى اليسار).
    /// مقاس الصفحة كما تُعرض (بعد تدويرها).
    static func displaySize(_ page: PDFPage) -> CGSize {
        let b = page.bounds(for: .cropBox)
        return (page.rotation % 180) != 0 ? CGSize(width: b.height, height: b.width) : b.size
    }

    /// مستطيل من إحداثيات ملف PDF إلى إحداثيات العرض (بعد تدوير الصفحة، الأصل أعلى اليسار) بالنقاط.
    static func displayRect(_ r: CGRect, page: PDFPage) -> CGRect {
        let b = page.bounds(for: .cropBox)
        let rot = ((page.rotation % 360) + 360) % 360
        func map(_ p: CGPoint) -> CGPoint {
            let u = p.x - b.minX, v = p.y - b.minY
            switch rot {
            case 90: return CGPoint(x: v, y: u)
            case 180: return CGPoint(x: b.width - u, y: v)
            case 270: return CGPoint(x: b.height - v, y: b.width - u)
            default: return CGPoint(x: u, y: b.height - v)
            }
        }
        let a = map(CGPoint(x: r.minX, y: r.minY)), c = map(CGPoint(x: r.maxX, y: r.maxY))
        return CGRect(x: min(a.x, c.x), y: min(a.y, c.y), width: abs(a.x - c.x), height: abs(a.y - c.y))
    }

    static func pdfWords(_ page: PDFPage) -> [OcrWord] {
        let n = page.numberOfCharacters
        guard n > 0, let str = page.string else { return [] }
        let chars = Array(str.utf16)
        let W = max(1, displaySize(page).width)
        var words: [OcrWord] = []
        var cur = ""
        var rect = CGRect.null
        var line = 0
        var lastMidY: CGFloat? = nil
        func flush() {
            if !cur.trimmingCharacters(in: .whitespaces).isEmpty && !rect.isNull {
                words.append(OcrWord(text: cur, l: rect.minX / W, t: rect.minY / W, r: rect.maxX / W, b: rect.maxY / W, line: line))
            }
            cur = ""
            rect = .null
        }
        for i in 0..<min(n, chars.count) {
            let u = chars[i]
            let s = String(utf16CodeUnits: [u], count: 1)
            if u == 10 || u == 13 {
                flush()
                line += 1
                lastMidY = nil
                continue
            }
            if s.trimmingCharacters(in: .whitespaces).isEmpty {
                flush()
                continue
            }
            let raw = page.characterBounds(at: i)
            if raw.isEmpty || raw.width <= 0 { cur += s; continue }
            // بإحداثيات العرض: الصفحة المدوَّرة تُلتقط كلماتها في مكانها الصحيح
            let b = displayRect(raw, page: page)
            // قفزة رأسية كبيرة دون سطر جديد في النص = سطر آخر
            if let m = lastMidY, abs(b.midY - m) > b.height * 0.8 {
                flush()
                line += 1
            }
            lastMidY = b.midY
            cur += s
            rect = rect.union(b)
        }
        flush()
        return words
    }

    /// كلمات صورة الصفحة بالقراءة الضوئية (Vision)، بإحداثيات نسبية لعرض الصفحة.
    static func ocrWords(_ page: PDFPage) -> [OcrWord] {
        let b = CGRect(origin: .zero, size: displaySize(page))
        let scale: CGFloat = 2.5
        let img = page.thumbnail(of: CGSize(width: b.width * scale, height: b.height * scale), for: .cropBox)
        guard let cg = img.cgImage else { return [] }
        let req = VNRecognizeTextRequest()
        req.recognitionLevel = .accurate
        req.usesLanguageCorrection = true
        let supported = (try? req.supportedRecognitionLanguages()) ?? []
        req.recognitionLanguages = ["ar-SA", "en-US"].filter { supported.contains($0) }
        try? VNImageRequestHandler(cgImage: cg, options: [:]).perform([req])
        let aspect = b.height / max(1, b.width)
        var out: [OcrWord] = []
        for (li, o) in (req.results ?? []).enumerated() {
            guard let cand = o.topCandidates(1).first else { continue }
            let s = cand.string
            var idx = s.startIndex
            for part in s.split(separator: " ", omittingEmptySubsequences: true) {
                guard let range = s.range(of: part, range: idx..<s.endIndex) else { continue }
                idx = range.upperBound
                let box = (try? cand.boundingBox(for: range))?.boundingBox ?? o.boundingBox
                out.append(OcrWord(text: String(part), l: box.minX, t: (1 - box.maxY) * aspect, r: box.maxX, b: (1 - box.minY) * aspect, line: li))
            }
        }
        return out
    }
}

/// إضافة تمييز ملتقط ودمجه مع تمييزات الصفحة (نقل MarkMerge.kt).
enum MarkMerge {
    struct Result { let marks: [TextMark]; let message: String? }

    private static func flat(_ s: String) -> String {
        TextTidy.clean(s.replacingOccurrences(of: "# ", with: "").replacingOccurrences(of: " | ", with: " "))
    }

    private static func dedup<T: Equatable>(_ a: [T]) -> [T] {
        var out: [T] = []
        for x in a where !out.contains(x) { out.append(x) }
        return out
    }

    static func add(existing: [TextMark], page: String, words: [OcrWord], idx: [Int], color: UInt32, now: Int64, inSummary: Bool) -> Result? {
        let others = existing.filter { $0.inSummary != inSummary || $0.free }
        let same = existing.filter { $0.inSummary == inSummary && !$0.free }
        guard let res = addSame(same, page, words, idx, color, now, inSummary) else { return nil }
        return Result(marks: others + res.marks, message: res.message)
    }

    private static func addSame(_ existing: [TextMark], _ page: String, _ words: [OcrWord], _ idx: [Int], _ color: UInt32, _ now: Int64, _ inSummary: Bool) -> Result? {
        let text = TextLayout.buildText(words, idx).trimmingCharacters(in: .whitespacesAndNewlines)
        if text.isEmpty { return nil }
        let rects = TextLayout.rectsFor(words, idx)
        let newArea = max(1e-9, TextLayout.area(rects))
        var next: [TextMark]? = nil
        var message: String? = nil
        func addStrong(_ list: [MarkRect], _ add: [MarkRect]) -> [MarkRect] {
            TextLayout.overlap(add, list) / newArea >= 0.6 ? list : list + add
        }
        // 1) نفس المساحة أو جزء داخل تمييز قديم
        for old in existing {
            let ov = TextLayout.overlap(rects, old.rects)
            if ov <= 0 { continue }
            let oldArea = max(1e-9, TextLayout.area(old.rects))
            let ofNew = ov / newArea, ofOld = ov / oldArea
            if ofNew >= 0.6 && ofOld >= 0.6 {
                next = existing.map { m in var x = m; if m.id == old.id { x.level = 2; x.strongRects = m.rects }; return x }
                message = L("ظُلّل النص مرة ثانية، فصار مهمًا في الملخص", "Highlighted twice, so it is now important in the summary")
                break
            }
            if ofNew >= 0.6 {
                let f = flat(text)
                next = existing.map { m in
                    var x = m
                    if m.id == old.id {
                        x.emphasis = dedup(m.emphasis + [f])
                        x.strongRects = m.level >= 2 ? m.strongRects : addStrong(m.strongRects, rects)
                    }
                    return x
                }
                message = L("أُبرز هذا الجزء كمهم في الملخص", "This part is now marked important in the summary")
                break
            }
        }
        // 2) تمييز أكبر يحتوي تمييزات قديمة
        if next == nil {
            let contained = existing.filter { old in
                TextLayout.overlap(rects, old.rects) / max(1e-9, TextLayout.area(old.rects)) >= 0.6
            }
            if !contained.isEmpty {
                var emphasis: [String] = []
                var strong: [MarkRect] = []
                for old in contained {
                    emphasis += old.level >= 2 ? [flat(old.text)] : old.emphasis
                    strong += old.level >= 2 ? old.rects : old.strongRects
                }
                let merged = TextMark(id: UUID().uuidString, page: page, rects: rects, color: color, text: text,
                                      createdAt: contained.map { $0.createdAt }.min()!, inSummary: inSummary,
                                      emphasis: dedup(emphasis), strongRects: dedup(strong))
                let ids = Set(contained.map { $0.id })
                next = existing.filter { !ids.contains($0.id) } + [merged]
            }
        }
        // 3) تمييز ملاصق لتمييز سابق: يُدمجان في تمييز واحد بترتيب القراءة الصحيح
        if next == nil {
            let order = TextLayout.visualRows(words, Array(words.indices)).flatMap { $0 }
            var pos = Array(repeating: 0, count: words.count)
            for (k, i) in order.enumerated() { pos[i] = k }
            func wordsIn(_ m: TextMark) -> [Int] { TextLayout.wordsInside(words, m.rects) }
            let newMin = idx.map { pos[$0] }.min()!, newMax = idx.map { pos[$0] }.max()!
            func touches(_ old: TextMark) -> Bool {
                if old.color != color { return false }
                let o = wordsIn(old)
                if o.isEmpty { return false }
                let oMin = o.map { pos[$0] }.min()!, oMax = o.map { pos[$0] }.max()!
                return oMax + 1 >= newMin && newMax + 1 >= oMin
            }
            let adjacent = existing.filter(touches)
            if !adjacent.isEmpty {
                var union = Set(idx)
                for old in adjacent { wordsIn(old).forEach { union.insert($0) } }
                let first = adjacent.min { $0.createdAt < $1.createdAt }!
                let u = Array(union)
                let merged = TextMark(id: UUID().uuidString, page: page, rects: TextLayout.rectsFor(words, u), color: first.color,
                                      text: TextLayout.buildText(words, u).trimmingCharacters(in: .whitespacesAndNewlines),
                                      createdAt: first.createdAt, inSummary: inSummary,
                                      emphasis: dedup(adjacent.flatMap { $0.level >= 2 ? [flat($0.text)] : $0.emphasis }),
                                      strongRects: dedup(adjacent.flatMap { $0.level >= 2 ? $0.rects : $0.strongRects }))
                let ids = Set(adjacent.map { $0.id })
                next = existing.filter { !ids.contains($0.id) } + [merged]
            }
        }
        let list = next ?? (existing + [TextMark(id: UUID().uuidString, page: page, rects: rects, color: color, text: text, createdAt: now, inSummary: inSummary)])
        return Result(marks: list, message: message)
    }
}
