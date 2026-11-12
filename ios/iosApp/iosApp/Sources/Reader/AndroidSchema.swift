import UIKit
import PDFKit
import PencilKit

// بيانات المستند بمخطط أندرويد (version 3) من وإلى DocData: الصفحات بأرقامها، والمواضع نسبية لعرض الصفحة.
// ما لا يفهمه الآيباد من ملف أندرويد (البطاقات، المميَّز بنجمة…) يبقى في نسخته الخام ويُعاد كما هو.

enum AndroidSchema {
    /// حجم الصفحة المعروضة بالنقاط (cropBox بعد التدوير) — نفس DocSession.pageSize.
    static func pageSize(_ pdf: PDFDocument, _ i: Int) -> CGSize {
        guard let p = pdf.page(at: i) else { return CGSize(width: 595, height: 842) }
        let b = p.bounds(for: .cropBox)
        return (p.rotation % 180) != 0 ? CGSize(width: b.height, height: b.width) : b.size
    }

    // ============================================================ التصدير

    struct Exported {
        var json: Data
        /// الصور المرفقة: الاسم (بلا البادئة) ومحتواها.
        var images: [(name: String, data: Data)]
    }

    /// widths = عرض كل صفحة بالنقاط؛ ink = حبر الصفحة بمعرّفها؛ raw = ملف أندرويد الخام إن كان المستند مستوردًا منه؛
    /// image = محتوى صورة أو ملصق (PNG/JPEG).
    static func export(_ data: DocData, widths: [CGFloat], ink: (String) -> PKDrawing,
                       raw: [String: Any]? = nil, image: (ImageItem) -> Data?) -> Exported {
        var index: [String: Int] = [:]
        for (i, p) in data.pages.enumerated() where i < widths.count { index[p.id] = i }
        func w(_ i: Int) -> CGFloat { max(1, widths[i]) }

        var pages: [String: Any] = [:]
        for (pid, i) in index {
            let list = InkCodec.encode(ink(pid), width: w(i), fills: data.fills.filter { $0.page == pid },
                                       converted: data.converted.filter { $0.page == pid })
            if !list.isEmpty { pages[String(i)] = list.map { $0.json } }
        }

        var images: [[String: Any]] = []
        var files: [(name: String, data: Data)] = []
        for im in data.images {
            guard let i = index[im.page] else { continue }
            let name = im.file.hasPrefix("sticker:") ? "st-" + safeName(im.id) + ".png" : safeName(im.file)
            if !files.contains(where: { $0.name == name }) {
                guard let d = image(im) else { continue }
                files.append((name, d))
            }
            images.append(["id": im.id, "file": name, "page": i, "x": Double(im.x / w(i)), "y": Double(im.y / w(i)),
                           "w": Double(im.w / w(i)), "a": Double(im.aspect)])
        }

        var marks: [[String: Any]] = []
        // التمييز الحر (رسم بالهايلايتر) حبر في الصفحة، فلا يُكتب تمييزًا ملتقطًا
        for m in data.marks where !m.free {
            guard let i = index[m.page] else { continue }
            marks.append(["id": m.id, "page": i, "c": AJSON.signed(m.color), "t": m.text, "at": m.createdAt,
                          "r": flat(m.rects), "lv": m.level, "sum": m.inSummary, "em": m.emphasis, "sr": flat(m.strongRects)])
        }

        var texts: [[String: Any]] = []
        for b in data.boxes {
            guard let i = index[b.page] else { continue }
            var o: [String: Any] = ["id": b.id, "page": i, "x": Double(b.x / w(i)), "y": Double(b.y / w(i)), "w": Double(b.w / w(i)),
                                    "t": b.text, "c": AJSON.signed(b.color), "s": Double(b.size / w(i)), "st": false,
                                    "ld": b.linkDoc ?? "", "lp": b.linkPage]
            if !b.font.isEmpty { o["f"] = b.font }
            if b.bold { o["b"] = true }
            if b.italic { o["i"] = true }
            if b.underline { o["u"] = true }
            if b.align != 0 { o["al"] = b.align }
            if let d = b.ink, let dr = try? PKDrawing(data: d), !dr.strokes.isEmpty {
                o["ink"] = InkCodec.encode(dr, width: w(i)).map { $0.json }
            }
            texts.append(o)
        }

        var notes: [[String: Any]] = []
        for n in data.notes {
            guard let i = index[n.page] else { continue }
            var inkArr: [[String: Any]] = []
            // حبر الملاحظة نسبي لعرضها
            if let d = n.ink, let dr = try? PKDrawing(data: d), !dr.strokes.isEmpty {
                inkArr = InkCodec.encode(dr, width: max(1, n.w)).map { $0.json }
            }
            notes.append(["id": n.id, "page": i, "x": Double(n.x / w(i)), "y": Double(n.y / w(i)), "w": Double(n.w / w(i)),
                          "h": Double(n.h / w(i)), "c": AJSON.signed(n.color), "st": n.style, "cl": n.collapsed,
                          "a": n.author, "tx": n.text, "ink": inkArr])
        }

        var root = raw ?? [:]
        root["version"] = 3
        root["pages"] = pages
        root["images"] = images
        root["marks"] = marks
        root["texts"] = texts
        root["notes"] = notes
        root["bookmarks"] = data.bookmarks.compactMap { index[$0] }.sorted()
        root["summary"] = summary(data.summaryEdits ?? SummaryEdits())
        if root["cards"] == nil { root["cards"] = [Any]() }
        if root["starred"] == nil { root["starred"] = [Any]() }
        return Exported(json: AJSON.write(root), images: files)
    }

    private static func flat(_ r: [MarkRect]) -> [Double] { r.flatMap { [Double($0.l), Double($0.t), Double($0.r), Double($0.b)] } }

    /// اسم ملف بسيط يصلح اسم مرفق.
    static func safeName(_ s: String) -> String {
        let out = String(s.unicodeScalars.map { u -> Character in
            let ok = (u.value >= 48 && u.value <= 57) || (u.value >= 65 && u.value <= 90) || (u.value >= 97 && u.value <= 122)
                || u == "." || u == "-" || u == "_"
            return ok ? Character(u) : "_"
        })
        return out.isEmpty ? "file" : out
    }

    private static func summary(_ e: SummaryEdits) -> [String: Any] {
        var o: [String: Any] = ["order": e.order, "kinds": e.kinds]
        o["tables"] = e.tables.map { g -> [String: Any] in
            var t: [String: Any] = ["id": g.id, "row": g.oneRow, "cols": g.cols, "header": g.header, "keys": g.keys]
            if let r = g.rows { t["rows"] = r }
            return t
        }
        if let m = e.map, !m.isEmpty {
            var nodes: [String: Any] = [:]
            for (k, v) in m.nodes {
                var n: [String: Any] = ["role": v.role.rawValue.uppercased()]
                if let p = v.parent { n["parent"] = p }
                if let p = v.pos { n["pos"] = p }
                nodes[k] = n
            }
            var mo: [String: Any] = ["nodes": nodes, "collapsed": Array(m.collapsed).sorted()]
            if !m.texts.isEmpty { mo["texts"] = m.texts }
            if !m.free.isEmpty { mo["free"] = m.free }
            o["map"] = mo
        }
        return o
    }

    // ============================================================ الاستيراد

    struct Imported {
        var data: DocData
        /// حبر كل صفحة بمعرّفها.
        var ink: [String: PKDrawing]
    }

    /// يبني بيانات الآيباد من ملف أندرويد. pages = معرّفات الصفحات (بعددها)، widths = عرض كل صفحة بالنقاط.
    static func importJSON(_ json: Data, pages: [PageInfo], widths: [CGFloat]) -> Imported? {
        guard let root = AJSON.parse(json), pages.count == widths.count else { return nil }
        var data = DocData()
        data.pages = pages
        var ink: [String: PKDrawing] = [:]
        func page(_ v: Any?) -> Int? {
            guard let i = AJSON.int(v).map({ Int($0) }), i >= 0, i < pages.count else { return nil }
            return i
        }
        func w(_ i: Int) -> CGFloat { max(1, widths[i]) }
        func strokes(_ v: Any?) -> [AStroke] { (v as? [[String: Any]] ?? []).compactMap { AStroke(json: $0) } }

        for (key, v) in root["pages"] as? [String: Any] ?? [:] {
            guard let i = Int(key), i >= 0, i < pages.count else { continue }
            let dec = InkCodec.decode(strokes(v), width: w(i), page: pages[i].id)
            if !dec.drawing.strokes.isEmpty { ink[pages[i].id] = dec.drawing }
            data.fills += dec.fills
            data.converted += dec.converted
        }

        for o in root["images"] as? [[String: Any]] ?? [] {
            guard let i = page(o["page"]), let id = o["id"] as? String, let file = o["file"] as? String else { continue }
            data.images.append(ImageItem(id: id, file: safeName(file), page: pages[i].id,
                                         x: CGFloat(AJSON.double(o["x"] ?? 0)) * w(i), y: CGFloat(AJSON.double(o["y"] ?? 0)) * w(i),
                                         w: CGFloat(AJSON.double(o["w"] ?? 0.3)) * w(i), aspect: max(0.01, CGFloat(AJSON.double(o["a"] ?? 1)))))
        }

        for o in root["marks"] as? [[String: Any]] ?? [] {
            guard let i = page(o["page"]), let id = o["id"] as? String else { continue }
            data.marks.append(TextMark(id: id, page: pages[i].id, rects: rects(o["r"]), color: AJSON.color(o["c"]) ?? 0xFFFFE27A,
                                       text: AJSON.string(o["t"]), createdAt: AJSON.int(o["at"]) ?? 0,
                                       level: Int(AJSON.int(o["lv"]) ?? 1), inSummary: (o["sum"] as? NSNumber)?.boolValue ?? true,
                                       emphasis: o["em"] as? [String] ?? [], strongRects: rects(o["sr"])))
        }

        for o in root["texts"] as? [[String: Any]] ?? [] {
            guard let i = page(o["page"]), let id = o["id"] as? String else { continue }
            var b = TextBox(id: id, page: pages[i].id, x: CGFloat(AJSON.double(o["x"] ?? 0)) * w(i),
                            y: CGFloat(AJSON.double(o["y"] ?? 0)) * w(i), w: CGFloat(AJSON.double(o["w"] ?? 0.5)) * w(i),
                            text: AJSON.string(o["t"]))
            b.color = AJSON.color(o["c"]) ?? 0xFF1A2230
            b.size = max(4, CGFloat(AJSON.double(o["s"] ?? 0.024)) * w(i))
            b.font = AJSON.string(o["f"])
            let ld = AJSON.string(o["ld"])
            b.linkDoc = ld.isEmpty ? nil : ld
            b.linkPage = Int(AJSON.int(o["lp"]) ?? 0)
            b.bold = AJSON.bool(o["b"])
            b.italic = AJSON.bool(o["i"])
            b.underline = AJSON.bool(o["u"])
            b.align = Int(AJSON.int(o["al"]) ?? 0)
            let hand = strokes(o["ink"])
            if !hand.isEmpty { b.ink = InkCodec.decode(hand, width: w(i)).drawing.dataRepresentation() }
            data.boxes.append(b)
        }

        for o in root["notes"] as? [[String: Any]] ?? [] {
            guard let i = page(o["page"]), let id = o["id"] as? String else { continue }
            let nw = CGFloat(AJSON.double(o["w"] ?? 0.3)) * w(i)
            var n = StickyNote(id: id, page: pages[i].id, x: CGFloat(AJSON.double(o["x"] ?? 0)) * w(i),
                               y: CGFloat(AJSON.double(o["y"] ?? 0)) * w(i), w: nw, h: CGFloat(AJSON.double(o["h"] ?? 0.3)) * w(i),
                               color: AJSON.color(o["c"]) ?? 0xFFFFE27A)
            n.style = Int(AJSON.int(o["st"]) ?? 0)
            n.collapsed = AJSON.bool(o["cl"])
            n.author = AJSON.string(o["a"])
            n.text = AJSON.string(o["tx"])
            let hand = strokes(o["ink"])
            if !hand.isEmpty { n.ink = InkCodec.decode(hand, width: max(1, nw)).drawing.dataRepresentation() }
            data.notes.append(n)
        }

        for v in root["bookmarks"] as? [Any] ?? [] {
            if let i = page(v) { data.bookmarks.append(pages[i].id) }
        }

        if let s = root["summary"] as? [String: Any] {
            let e = edits(s)
            if !e.isEmpty { data.summaryEdits = e }
        }
        return Imported(data: data, ink: ink)
    }

    private static func rects(_ v: Any?) -> [MarkRect] {
        let f = (v as? [Any] ?? []).map { CGFloat(AJSON.double($0)) }
        return stride(from: 0, to: f.count - 3, by: 4).map { MarkRect(l: f[$0], t: f[$0 + 1], r: f[$0 + 2], b: f[$0 + 3]) }
    }

    private static func edits(_ s: [String: Any]) -> SummaryEdits {
        var e = SummaryEdits()
        e.order = s["order"] as? [String] ?? []
        e.kinds = s["kinds"] as? [String: String] ?? [:]
        e.tables = (s["tables"] as? [[String: Any]] ?? []).compactMap { t in
            guard let id = t["id"] as? String, let keys = t["keys"] as? [String] else { return nil }
            return TableGroup(id: id, keys: keys, oneRow: AJSON.bool(t["row"]), cols: Int(AJSON.int(t["cols"]) ?? 0),
                              rows: t["rows"] as? [[String]], header: (t["header"] as? NSNumber)?.boolValue ?? true)
        }
        if let m = s["map"] as? [String: Any] {
            var me = MapEdits()
            for (k, v) in m["nodes"] as? [String: [String: Any]] ?? [:] {
                guard let role = MapRole(rawValue: AJSON.string(v["role"]).lowercased()) else { continue }
                let parent = AJSON.string(v["parent"])
                me.nodes[k] = MapNodeEdit(role: role, parent: parent.isEmpty ? nil : parent, pos: (v["pos"] as? NSNumber)?.doubleValue)
            }
            me.collapsed = Set(m["collapsed"] as? [String] ?? [])
            me.texts = m["texts"] as? [String: String] ?? [:]
            me.free = m["free"] as? [String: String] ?? [:]
            if !me.isEmpty { e.map = me }
        }
        return e
    }
}
