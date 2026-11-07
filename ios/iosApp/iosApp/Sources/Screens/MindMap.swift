import SwiftUI
import UIKit

// الخريطة الذهنية (نفس MindMap.kt وMindMapUi.kt في أندرويد): لا شيء تلقائي — المستخدم يضع نصوص
// الملخص («كما التُقط») أو نصوصًا حرة في أماكن «+»: فرع جديد، نقطة، أو نقطة فرعية بلا حد للعمق.
// المركز في الوسط، والفروع الأولى على اليمين والباقي على اليسار، بخطوط منحنية بألوان الفروع.

// ================================================================ البيانات

enum MapRole: String, Codable { case branch, point, hidden }

/// مكان نص في الخريطة: فرع (parent = nil) أو نقطة تحت parent. pos = ترتيبه بين إخوته.
struct MapNodeEdit: Codable, Equatable {
    var role: MapRole
    var parent: String? = nil
    var pos: Double? = nil
}

/// الخريطة كما وضعها المستخدم. المفاتيح «معرّف التمييز#رقم الجزء»، والنصوص الحرة تبدأ بـ f:.
struct MapEdits: Codable, Equatable {
    var nodes: [String: MapNodeEdit] = [:]
    var collapsed: Set<String> = []
    /// نص عدّله المستخدم في الخريطة فقط (الملخص لا يتغيّر).
    var texts: [String: String] = [:]
    /// نصوص حرة يكتبها المستخدم في الخريطة.
    var free: [String: String] = [:]
    var isEmpty: Bool { nodes.isEmpty && collapsed.isEmpty && texts.isEmpty && free.isEmpty }
}

struct MapNode {
    let key: String
    let text: String
    let original: String
    let mark: TextMark?
    let collapsed: Bool
    let children: [MapNode]
    func count() -> Int { children.count + children.reduce(0) { $0 + $1.count() } }
    var isFree: Bool { mark == nil }
}

struct MindMapModel {
    let title: String
    let branches: [MapNode]
    var total: Int { branches.count + branches.reduce(0) { $0 + $1.count() } }
}

/// أين يوضع النص: تحت parent (nil = فرع جديد)، قبل before (nil = في الآخر).
struct MapTarget: Equatable { let parent: String?; let before: String? }

enum MindMap {
    struct Item { let key: String; let text: String; let mark: TextMark? }

    static let freePrefix = "f:"
    static func isFree(_ k: String) -> Bool { k.hasPrefix(freePrefix) }

    static func nodeKey(_ blockKey: String) -> String? { blockKey.hasPrefix("r:") ? String(blockKey.dropFirst(2)) : nil }

    static func items(_ marks: [TextMark]) -> [Item] {
        Summary.build(marks, tidy: false).compactMap { b in
            guard let k = nodeKey(b.key), let m = b.marks.first else { return nil }
            let text: String
            switch b {
            case let .heading(t, _, _): text = t
            case let .para(t, _, _, _): text = t
            case let .table(rows, _, _, _): text = rows.map { r in r.filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }.joined(separator: " · ") }.joined(separator: "\n")
            }
            let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
            return t.isEmpty ? nil : Item(key: k, text: t, mark: m)
        }
    }

    static func allItems(_ marks: [TextMark], _ e: MapEdits) -> [Item] {
        items(marks) + e.free.keys.sorted().map { Item(key: $0, text: e.free[$0] ?? "", mark: nil) }
    }

    /// الأماكن الفعلية بعد تصحيح ما انكسر (أب محذوف يجعل النقطة فرعًا، والحلقات تُكسر).
    private static func resolve(_ e: MapEdits, _ items: [Item]) -> [String: (String?, Double)] {
        var index: [String: Int] = [:]
        for (i, it) in items.enumerated() where index[it.key] == nil { index[it.key] = i }
        let raw = e.nodes.filter { index[$0.key] != nil && $0.value.role != .hidden }
        var out: [String: (String?, Double)] = [:]
        for (k, v) in raw {
            let pos = v.pos ?? Double(index[k]!)
            let p: String? = v.role == .branch ? nil : (v.parent.flatMap { raw[$0] != nil && $0 != k ? $0 : nil })
            out[k] = (p, pos)
        }
        for k in out.keys.sorted() {
            var seen = Set<String>()
            var cur: String? = k
            while let c = cur {
                if !seen.insert(c).inserted { out[c] = (nil, out[c]!.1); break }
                cur = out[c]?.0
            }
        }
        return out
    }

    static func build(_ title: String, _ marks: [TextMark], _ e: MapEdits, exclude: Set<String> = []) -> MindMapModel {
        let items = allItems(marks, e)
        var byKey: [String: Item] = [:]
        for it in items where byKey[it.key] == nil { byKey[it.key] = it }
        let placed = resolve(e, items)
        var hidden = Set<String>()
        if !exclude.isEmpty {
            for k in placed.keys {
                var cur: String? = k
                while let c = cur {
                    if exclude.contains(c) { hidden.insert(k); break }
                    cur = placed[c]?.0
                }
            }
        }
        let visible = placed.filter { !hidden.contains($0.key) }
        func childrenOf(_ parent: String?) -> [String] {
            visible.filter { $0.value.0 == parent }.sorted { $0.value.1 < $1.value.1 || ($0.value.1 == $1.value.1 && $0.key < $1.key) }.map { $0.key }
        }
        func node(_ k: String) -> MapNode {
            let it = byKey[k]!
            let shown = e.texts[k].flatMap { $0.trimmingCharacters(in: .whitespaces).isEmpty ? nil : $0 } ?? it.text
            return MapNode(key: k, text: shown, original: it.text, mark: it.mark, collapsed: e.collapsed.contains(k), children: childrenOf(k).map(node))
        }
        return MindMapModel(title: title, branches: childrenOf(nil).map(node))
    }

    static func find(_ m: MindMapModel, _ key: String) -> MapNode? {
        func walk(_ n: MapNode) -> MapNode? { n.key == key ? n : n.children.lazy.compactMap(walk).first }
        return m.branches.lazy.compactMap(walk).first
    }

    static func siblingsOf(_ m: MindMapModel, _ key: String) -> (String?, [MapNode])? {
        if m.branches.contains(where: { $0.key == key }) { return (nil, m.branches) }
        func walk(_ n: MapNode) -> (String?, [MapNode])? {
            n.children.contains { $0.key == key } ? (n.key, n.children) : n.children.lazy.compactMap(walk).first
        }
        return m.branches.lazy.compactMap(walk).first
    }

    private static func normalized(_ placed: [String: (String?, Double)]) -> [String: MapNodeEdit] {
        var nodes: [String: MapNodeEdit] = [:]
        for (k, v) in placed { nodes[k] = MapNodeEdit(role: v.0 == nil ? .branch : .point, parent: v.0, pos: v.1) }
        return nodes
    }

    /// يضع النصوص (بترتيبها) في المكان المختار. ما تحتها ينتقل معها.
    static func place(_ e: MapEdits, _ items: [Item], _ keys: [String], _ t: MapTarget) -> MapEdits {
        guard !keys.isEmpty else { return e }
        let placed = resolve(e, items)
        let moving = Set(keys)
        let sibs = placed.filter { $0.value.0 == t.parent && !moving.contains($0.key) }.sorted { $0.value.1 < $1.value.1 }
        let i = t.before.flatMap { b in sibs.firstIndex { $0.key == b } } ?? sibs.count
        let lo = i > 0 ? sibs[i - 1].value.1 : (sibs.isEmpty ? 0 : sibs[0].value.1 - 1)
        let hi = i < sibs.count ? sibs[i].value.1 : lo + Double(keys.count) + 1
        let step = (hi - lo) / Double(keys.count + 1)
        var nodes = normalized(placed)
        for (j, k) in keys.enumerated() {
            nodes[k] = MapNodeEdit(role: t.parent == nil ? .branch : .point, parent: t.parent, pos: lo + step * Double(j + 1))
        }
        var x = e
        x.nodes = nodes
        return x
    }

    static func move(_ e: MapEdits, _ items: [Item], _ key: String, _ delta: Int) -> MapEdits {
        let placed = resolve(e, items)
        guard let me = placed[key] else { return e }
        let sibs = placed.filter { $0.value.0 == me.0 }.sorted { $0.value.1 < $1.value.1 }.map { $0.key }
        guard let i = sibs.firstIndex(of: key) else { return e }
        let j = i + delta
        guard j >= 0 && j < sibs.count else { return e }
        var nodes = normalized(placed)
        let a = nodes[key]!, b = nodes[sibs[j]]!
        nodes[key]!.pos = b.pos
        nodes[sibs[j]]!.pos = a.pos
        var x = e
        x.nodes = nodes
        return x
    }

    static func remove(_ e: MapEdits, _ items: [Item], _ key: String) -> (MapEdits, Int) {
        let placed = resolve(e, items)
        var gone = Set<String>([key])
        for k in placed.keys {
            var cur: String? = k
            while let c = cur {
                if c == key { gone.insert(k); break }
                cur = placed[c]?.0
            }
        }
        var x = e
        for g in gone {
            x.nodes.removeValue(forKey: g)
            x.collapsed.remove(g)
            x.texts.removeValue(forKey: g)
            if isFree(g) { x.free.removeValue(forKey: g) }
        }
        return (x, gone.count)
    }

    static func setText(_ e: MapEdits, _ items: [Item], _ key: String, _ text: String) -> MapEdits {
        let v = text.trimmingCharacters(in: .whitespacesAndNewlines)
        var x = e
        if isFree(key) {
            if !v.isEmpty && e.free[key] != nil { x.free[key] = v }
            return x
        }
        guard let original = items.first(where: { $0.key == key })?.text else { return e }
        if v.isEmpty || v == original { x.texts.removeValue(forKey: key) } else { x.texts[key] = v }
        return x
    }

    static func addFree(_ e: MapEdits, _ marks: [TextMark], _ text: String, _ t: MapTarget) -> MapEdits {
        let v = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !v.isEmpty else { return e }
        let key = freePrefix + UUID().uuidString
        var withText = e
        withText.free[key] = v
        return place(withText, allItems(marks, withText), [key], t)
    }

    static func quote(_ text: String, words: Int = 5) -> String {
        let w = text.trimmingCharacters(in: .whitespacesAndNewlines).split(whereSeparator: { $0.isWhitespace })
        return w.count <= words ? text.trimmingCharacters(in: .whitespacesAndNewlines) : w.prefix(words).joined(separator: " ") + "…"
    }
}

extension DocSession {
    var mapEdits: MapEdits { summaryEdits.map ?? MapEdits() }

    func mapItems() -> [MindMap.Item] { MindMap.allItems(allMarks(), mapEdits) }

    func changeMap(_ f: (MapEdits) -> MapEdits) {
        let next = f(mapEdits)
        if next == mapEdits { return }
        changeEdits { e in var x = e; x.map = next; return x }
    }

    func mapPlace(_ keys: [String], _ t: MapTarget) { changeMap { MindMap.place($0, mapItems(), keys, t) } }
    func mapMove(_ key: String, _ d: Int) { changeMap { MindMap.move($0, mapItems(), key, d) } }
    func mapRemove(_ key: String) -> Int {
        var n = 0
        changeMap { e in let r = MindMap.remove(e, mapItems(), key); n = r.1; return r.0 }
        return n
    }
    func mapSetText(_ key: String, _ text: String) { changeMap { MindMap.setText($0, mapItems(), key, text) } }
    func mapAddFree(_ text: String, _ t: MapTarget) { changeMap { MindMap.addFree($0, allMarks(), text, t) } }
    func mapToggleCollapsed(_ key: String) {
        changeMap { e in var x = e; if x.collapsed.contains(key) { x.collapsed.remove(key) } else { x.collapsed.insert(key) }; return x }
    }
    func mapSetCollapsed(_ keys: Set<String>) { changeMap { e in var x = e; x.collapsed = keys; return x } }

    /// خرائط النسخة السابقة (شجرة نصوص) تتحول مرة واحدة إلى نصوص حرة بنفس الترتيب.
    func migrateLegacyMap() {
        guard summaryEdits.map == nil, let old = data.map, !old.branches.isEmpty else { return }
        var e = MapEdits()
        func add(_ n: MapNodeData, parent: String?, pos: Double) {
            let k = MindMap.freePrefix + n.id
            e.free[k] = n.text
            e.nodes[k] = MapNodeEdit(role: parent == nil ? .branch : .point, parent: parent, pos: pos)
            if n.collapsed { e.collapsed.insert(k) }
            for (i, c) in n.children.enumerated() { add(c, parent: k, pos: Double(i)) }
        }
        for (i, b) in old.branches.enumerated() { add(b, parent: nil, pos: Double(i)) }
        changeEdits { x in var y = x; y.map = e; return y }
    }
}

// ================================================================ الألوان

private let mapPalette: [UInt32] = [0xFF1B365D, 0xFF2E7D5B, 0xFF8C6A1F, 0xFF2F5FB0, 0xFF9A3B3B, 0xFF5B4B9A]
func mapBranchColor(_ i: Int) -> Color { Color(hex: mapPalette[((i % mapPalette.count) + mapPalette.count) % mapPalette.count]) }
private let mapCenterTop = Color(hex: 0xFF2C4F80)
private let mapCenterDeep = Color(hex: 0xFF0D1D35)
private let mapDot = Color(hex: 0xFFE4DFD3)
private let ghostBg = Color(hex: 0xFFFFF8E6)
private let ghostInk = Color(hex: 0xFF6B5418)
private let barDark = Color(hex: 0xFF1A2230)
let freeInk = Color(hex: 0xFF6D4AC2)

// ================================================================ الترتيب

private enum EKind { case center, branch, point, plusCenter, plusRow }

private struct MapEntry {
    let kind: EKind
    let node: MapNode?
    let branch: Int
    let parent: Int
    var target: MapTarget? = nil
    var ghost = false
    var label: String? = nil
    var depth = 0
}

private struct MapCurve { let a: CGPoint; let b: CGPoint; let color: Color; let width: CGFloat; let dashed: Bool }

/// يحسب مكان كل عقدة ومقاسها والخطوط بينها (نفس MapLayout في أندرويد).
private struct MapPlan {
    var entries: [MapEntry] = []
    var frames: [CGRect] = []
    var textSizes: [CGSize] = []
    var curves: [MapCurve] = []
    var size: CGSize = .zero
    var rightCount = 0

    static func textSize(_ s: String, _ font: UIFont, lineHeight: CGFloat, maxW: CGFloat) -> CGSize {
        let ps = NSMutableParagraphStyle()
        ps.minimumLineHeight = lineHeight
        ps.maximumLineHeight = lineHeight
        let r = (s as NSString).boundingRect(with: CGSize(width: maxW, height: .greatestFiniteMagnitude), options: [.usesLineFragmentOrigin],
                                             attributes: [.font: font, .paragraphStyle: ps], context: nil)
        return CGSize(width: ceil(min(maxW, r.width)) + 1, height: ceil(r.height))
    }

    /// نفس تكبير خط الواجهة (Fonts.scale × Fonts.base) حتى يطابق القياس ما يُرسم.
    static var k: CGFloat { Fonts.scale * Fonts.base }
    static let displayFont = { (s: CGFloat) in UIFont(name: "Almarai-ExtraBold", size: s * k) ?? .boldSystemFont(ofSize: s * k) }
    static func uiFont(_ s: CGFloat, bold: Bool = false) -> UIFont { Fonts.ui(s * k, bold: bold) }

    init(model: MindMapModel, placing: Bool, ghosts: Set<String>) {
        var out: [MapEntry] = [MapEntry(kind: .center, node: nil, branch: -1, parent: -1)]
        if placing { out.append(MapEntry(kind: .plusCenter, node: nil, branch: -1, parent: 0, target: MapTarget(parent: nil, before: nil), label: L("فرع جديد", "New branch"))) }
        func addKids(_ n: MapNode, _ nIdx: Int, _ depth: Int, _ bi: Int, _ nGhost: Bool) {
            if !n.collapsed || placing {
                var prevReal: MapNode? = nil
                for ch in n.children {
                    let g = ghosts.contains(ch.key) || nGhost
                    if placing && depth == 1 && !g && prevReal != nil {
                        out.append(MapEntry(kind: .plusRow, node: nil, branch: bi, parent: nIdx, target: MapTarget(parent: n.key, before: ch.key), depth: depth))
                    }
                    let idx = out.count
                    out.append(MapEntry(kind: .point, node: ch, branch: bi, parent: nIdx, ghost: g, depth: depth))
                    addKids(ch, idx, depth + 1, bi, g)
                    if !g { prevReal = ch }
                }
            }
            if placing && !nGhost {
                out.append(MapEntry(kind: .plusRow, node: nil, branch: bi, parent: nIdx, target: MapTarget(parent: n.key, before: nil),
                                    label: depth == 1 ? L("نقطة", "Point") : L("نقطة فرعية", "Sub-point"), depth: depth))
            }
        }
        for (bi, b) in model.branches.enumerated() {
            let bIdx = out.count
            let g = ghosts.contains(b.key)
            out.append(MapEntry(kind: .branch, node: b, branch: bi, parent: 0, ghost: g))
            addKids(b, bIdx, 1, bi, g)
        }
        entries = out
        rightCount = (model.branches.count + 1) / 2

        // المقاسات
        var sizes: [CGSize] = []
        var tsz: [CGSize] = []
        for e in out {
            switch e.kind {
            case .center:
                let t = MapPlan.textSize(model.title, MapPlan.displayFont(22), lineHeight: 28 * MapPlan.k, maxW: 280 - 48)
                let w = max(t.width, model.total > 0 ? 60 : 0)
                tsz.append(t)
                sizes.append(CGSize(width: w + 48, height: t.height + 28 + (model.total > 0 ? 16 : 0)))
            case .branch:
                let t = MapPlan.textSize(e.node!.text, MapPlan.displayFont(15), lineHeight: 23 * MapPlan.k, maxW: 260 - 32)
                tsz.append(t)
                sizes.append(CGSize(width: t.width + 32, height: t.height + 20 + 6))
            case .point:
                let maxW: CGFloat = e.depth <= 1 ? 250 : max(160, 220 - 15 * CGFloat(e.depth - 2))
                let txt = e.node!.text + (e.node!.collapsed && !e.node!.children.isEmpty ? "  (+\(e.node!.count()))" : "")
                let t = MapPlan.textSize(txt, MapPlan.uiFont(e.depth >= 2 ? 12.5 : 13.5), lineHeight: 20 * MapPlan.k, maxW: maxW - 24)
                tsz.append(t)
                sizes.append(CGSize(width: t.width + 24, height: t.height + 14))
            case .plusCenter, .plusRow:
                let big = e.kind == .plusCenter
                let circle: CGFloat = big ? 28 : (e.label == nil ? 20 : 24)
                var w = circle + 8
                if let l = e.label { w += 6 + MapPlan.textSize(l, MapPlan.uiFont(big ? 14 : 12, bold: true), lineHeight: 18 * MapPlan.k, maxW: 200).width + 8 }
                tsz.append(.zero)
                sizes.append(CGSize(width: w, height: circle + 8))
            }
        }
        textSizes = tsz

        let gapB: CGFloat = 40
        func gapY(_ d: Int) -> CGFloat { d <= 1 ? 12 : 8 }
        func gapX(_ d: Int) -> CGFloat { d == 0 ? 84 : (d == 1 ? 54 : 34) }
        func lineW(_ d: Int) -> CGFloat { d == 0 ? 3.5 : (d == 1 ? 2.2 : (d == 2 ? 1.6 : 1.3)) }
        var xs = [CGFloat](repeating: 0, count: out.count)
        var ys = [CGFloat](repeating: 0, count: out.count)
        var curves: [MapCurve] = []
        let cw = sizes[0].width, ch = sizes[0].height
        xs[0] = -cw / 2; ys[0] = -ch / 2
        if let i = out.firstIndex(where: { $0.kind == .plusCenter }) {
            xs[i] = -sizes[i].width / 2
            ys[i] = ch / 2 + 12
        }
        var kids: [Int: [Int]] = [:]
        for (i, e) in out.enumerated() where e.kind == .point || e.kind == .plusRow { kids[e.parent, default: []].append(i) }
        var kidsH = [CGFloat](repeating: 0, count: out.count)
        var subH = [CGFloat](repeating: 0, count: out.count)
        for i in out.indices.reversed() {
            if let k = kids[i], !k.isEmpty {
                kidsH[i] = k.reduce(0) { $0 + subH[$1] } + gapY(out[k[0]].depth) * CGFloat(k.count - 1)
            }
            subH[i] = max(sizes[i].height, kidsH[i])
        }
        func place(_ i: Int, _ edge: CGFloat, _ top: CGFloat, _ side: CGFloat, _ col: Color) {
            let w = sizes[i].width, h = sizes[i].height
            let x = side == 1 ? edge : edge - w
            let y = top + subH[i] / 2 - h / 2
            xs[i] = x; ys[i] = y
            guard let k = kids[i] else { return }
            var ky = top + subH[i] / 2 - kidsH[i] / 2
            for c in k {
                let ce = out[c]
                let cEdge = side == 1 ? x + w + gapX(ce.depth) : x - gapX(ce.depth)
                place(c, cEdge, ky, side, col)
                if ce.kind == .point {
                    curves.append(MapCurve(a: CGPoint(x: side == 1 ? x + w : x, y: y + h / 2),
                                           b: CGPoint(x: side == 1 ? xs[c] : xs[c] + sizes[c].width, y: ys[c] + sizes[c].height / 2),
                                           color: ce.ghost ? Palette.gold : col, width: lineW(ce.depth), dashed: ce.ghost))
                }
                ky += subH[c] + gapY(ce.depth)
            }
        }
        let branchIdx = out.indices.filter { out[$0].kind == .branch }
        let rc = rightCount
        for side in [CGFloat(1), -1] {
            let list = branchIdx.filter { (out[$0].branch < rc) == (side == 1) }
            if list.isEmpty { continue }
            let total = list.reduce(0) { $0 + subH[$1] } + gapB * CGFloat(list.count - 1)
            var y = -total / 2
            for b in list {
                let e = out[b]
                let col = e.ghost ? Palette.gold : mapBranchColor(e.branch)
                let edge = side == 1 ? cw / 2 + gapX(0) : -cw / 2 - gapX(0)
                place(b, edge, y, side, mapBranchColor(e.branch))
                curves.append(MapCurve(a: CGPoint(x: side * cw / 2, y: 0),
                                       b: CGPoint(x: side == 1 ? xs[b] : xs[b] + sizes[b].width, y: ys[b] + sizes[b].height / 2),
                                       color: col, width: lineW(0), dashed: e.ghost))
                y += subH[b] + gapB
            }
        }
        var x0 = CGFloat.greatestFiniteMagnitude, y0 = CGFloat.greatestFiniteMagnitude, x1 = -CGFloat.greatestFiniteMagnitude, y1 = -CGFloat.greatestFiniteMagnitude
        for i in out.indices {
            x0 = min(x0, xs[i]); y0 = min(y0, ys[i]); x1 = max(x1, xs[i] + sizes[i].width); y1 = max(y1, ys[i] + sizes[i].height)
        }
        let pad: CGFloat = 28
        let dx = pad - x0, dy = pad - y0
        frames = out.indices.map { CGRect(x: xs[$0] + dx, y: ys[$0] + dy, width: sizes[$0].width, height: sizes[$0].height) }
        self.curves = curves.map { MapCurve(a: CGPoint(x: $0.a.x + dx, y: $0.a.y + dy), b: CGPoint(x: $0.b.x + dx, y: $0.b.y + dy), color: $0.color, width: $0.width, dashed: $0.dashed) }
        size = CGSize(width: x1 - x0 + 2 * pad, height: y1 - y0 + 2 * pad)
    }
}

// ================================================================ الرسم

/// الخريطة مرسومة (تُستعمل للعرض وللتصدير كصورة).
private struct MapCanvas: View {
    let model: MindMapModel
    let plan: MapPlan
    let target: MapTarget?
    let selectedKey: String?
    var onTap: (String) -> Void = { _ in }
    var onLongTap: (String) -> Void = { _ in }
    var onTarget: (MapTarget) -> Void = { _ in }

    var body: some View {
        ZStack(alignment: .topLeading) {
            Canvas { ctx, _ in
                for cv in plan.curves {
                    var p = Path()
                    p.move(to: cv.a)
                    let mx = (cv.a.x + cv.b.x) / 2
                    p.addCurve(to: cv.b, control1: CGPoint(x: mx, y: cv.a.y), control2: CGPoint(x: mx, y: cv.b.y))
                    ctx.stroke(p, with: .color(cv.color), style: StrokeStyle(lineWidth: cv.width, lineCap: .round, dash: cv.dashed ? [cv.width * 3, cv.width * 3] : []))
                }
            }
            .frame(width: plan.size.width, height: plan.size.height)
            ForEach(plan.entries.indices, id: \.self) { i in
                entryView(i)
                    .frame(width: plan.frames[i].width, height: plan.frames[i].height)
                    .offset(x: plan.frames[i].minX, y: plan.frames[i].minY)
            }
        }
        .frame(width: plan.size.width, height: plan.size.height, alignment: .topLeading)
        .environment(\.layoutDirection, .leftToRight)
    }

    @ViewBuilder private func entryView(_ i: Int) -> some View {
        let e = plan.entries[i]
        let col = mapBranchColor(e.branch)
        switch e.kind {
        case .center: centerNode
        case .branch: branchNode(e.node!, col: col, right: e.branch < plan.rightCount, ghost: e.ghost, ts: plan.textSizes[i])
        case .point: pointNode(e.node!, col: col, depth: e.depth, ghost: e.ghost, ts: plan.textSizes[i])
        case .plusCenter, .plusRow: plusChip(e, big: e.kind == .plusCenter)
        }
    }

    private var centerNode: some View {
        VStack(spacing: 2) {
            Text(model.title).font(Fonts.display(22)).foregroundColor(.white).multilineTextAlignment(.center).lineLimit(2)
            if model.total > 0 { Text(L("\(model.total) نصوص", "\(model.total) texts")).font(Fonts.sans(12)).foregroundColor(Color(hex: 0xFFC9D4E6)) }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(RoundedRectangle(cornerRadius: 22).fill(RadialGradient(colors: [mapCenterTop, Palette.navy, mapCenterDeep], center: .center, startRadius: 0, endRadius: 250)))
        .overlay(RoundedRectangle(cornerRadius: 22).stroke(Palette.gold.opacity(0.7), lineWidth: 1.5))
        .shadow(color: mapCenterDeep.opacity(0.35), radius: 10, y: 5)
    }

    private func nodeText(_ s: String, _ font: Font, lineHeight: CGFloat, base: CGFloat, color: Color) -> some View {
        Text(s).font(font).foregroundColor(color).lineSpacing(max(0, (lineHeight - base * 1.2) * MapPlan.k)).fixedSize(horizontal: false, vertical: true)
    }

    private func branchNode(_ n: MapNode, col: Color, right: Bool, ghost: Bool, ts: CGSize) -> some View {
        let sel = selectedKey == n.key
        return ZStack(alignment: right ? .topTrailing : .topLeading) {
            nodeText(n.text, Fonts.display(15), lineHeight: 23, base: 15, color: ghost ? ghostInk : Palette.navy)
                .frame(width: ts.width, alignment: .leading)
                .padding(.horizontal, 16).padding(.vertical, 10)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(
                    RoundedRectangle(cornerRadius: 16).fill(ghost ? ghostBg : Color.white)
                        .shadow(color: .black.opacity(ghost ? 0 : 0.12), radius: 5, y: 2)
                )
                .overlay(alignment: right ? .leading : .trailing) {
                    if !ghost { Capsule().fill(col).frame(width: 5).padding(.vertical, 10) }
                }
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(ghost ? Palette.gold : (sel ? Palette.gold : (n.isFree ? freeInk : Palette.divider)),
                                style: StrokeStyle(lineWidth: ghost || sel ? 2 : (n.isFree ? 1.5 : 1), dash: ghost ? [8, 6] : []))
                )
                .padding(.top, 6)
                .contentShape(Rectangle())
                .onTapGesture { if !ghost { onTap(n.key) } }
                .onLongPressGesture { if !ghost { onLongTap(n.key) } }
            if n.collapsed && !n.children.isEmpty {
                Text("\(n.count())").font(Fonts.sans(11, .bold)).foregroundColor(.white)
                    .frame(width: 22, height: 22).background(Circle().fill(Palette.navy))
            }
        }
    }

    private func pointNode(_ n: MapNode, col: Color, depth: Int, ghost: Bool, ts: CGSize) -> some View {
        let sel = selectedKey == n.key
        let kid = depth >= 2
        let txt = n.text + (n.collapsed && !n.children.isEmpty ? "  (+\(n.count()))" : "")
        return nodeText(txt, Fonts.sans(kid ? 12.5 : 13.5), lineHeight: 20, base: kid ? 12.5 : 13.5, color: ghost ? ghostInk : Palette.textPrimary)
            .frame(width: ts.width, alignment: .leading)
            .padding(.horizontal, 12).padding(.vertical, 7)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(RoundedRectangle(cornerRadius: 12).fill(ghost ? ghostBg : (kid || sel ? Color.white : col.opacity(0.08))))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(ghost ? Palette.gold : (sel ? Palette.gold : (n.isFree ? freeInk : col.opacity(0.22))),
                            style: StrokeStyle(lineWidth: ghost || sel ? 2 : (n.isFree ? 1.5 : 1), dash: ghost ? [8, 6] : []))
            )
            .contentShape(Rectangle())
            .onTapGesture { if !ghost { onTap(n.key) } }
            .onLongPressGesture { if !ghost { onLongTap(n.key) } }
    }

    private func plusChip(_ e: MapEntry, big: Bool) -> some View {
        let on = target == e.target
        let circle: CGFloat = big ? 28 : (e.label == nil ? 20 : 24)
        return HStack(spacing: 6) {
            Text("+").font(.system(size: big ? 18 : 15, weight: .bold)).foregroundColor(Palette.goldDeep)
                .frame(width: circle, height: circle).background(Circle().fill(on ? Color.white : Palette.goldSoft))
            if let l = e.label {
                Text(l).font(Fonts.sans(big ? 14 : 12, .bold)).foregroundColor(on ? barDark : Palette.goldDeep).lineLimit(1).fixedSize()
            }
        }
        .padding(.leading, 4).padding(.trailing, e.label != nil ? 12 : 4).padding(.vertical, 4)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Capsule().fill(on ? Palette.gold : Color.white))
        .overlay(Capsule().stroke(on ? Palette.goldDeep : Color(hex: 0xFFB8A36A), style: StrokeStyle(lineWidth: 1.5, dash: on ? [] : [6, 4])))
        .contentShape(Capsule())
        .onTapGesture { if let t = e.target { onTarget(t) } }
    }
}

// ================================================================ الشاشة

private struct EditKey: Identifiable { let id: String }
private struct FreeTarget: Identifiable { let id = UUID(); let target: MapTarget }

/// الخريطة الذهنية. placing = نصوص يضعها المستخدم الآن (من الملخصات).
struct MindMapView: View {
    @ObservedObject var session: DocSession
    @EnvironmentObject var app: AppController
    let standalone: Bool
    var placing: [String]? = nil
    var onClose: () -> Void = {}

    @State private var placingKeys: [String]? = nil
    @State private var target: MapTarget? = nil
    @State private var selectedKey: String? = nil
    @State private var editKey: EditKey? = nil
    @State private var addingFree = false
    @State private var freeTarget: FreeTarget? = nil
    @State private var scale: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var baseScale: CGFloat = 1
    @State private var baseOffset: CGSize = .zero
    @State private var viewport: CGSize = .zero
    @State private var fitted = false
    @State private var userMoved = false
    @State private var share: [Any]? = nil
    @State private var started = false

    private var fromSummary: Bool { placing != nil }

    var body: some View {
        let all = session.allMarks()
        let edits = session.mapEdits
        let items = MindMap.allItems(all, edits)
        let keys = placingKeys
        let model: MindMapModel = {
            guard let k = keys else { return MindMap.build(session.title, all, edits) }
            if let t = target { return MindMap.build(session.title, all, MindMap.place(edits, items, k, t)) }
            return MindMap.build(session.title, all, edits, exclude: Set(k))
        }()
        let ghosts: Set<String> = {
            guard let k = keys, target != nil else { return [] }
            var out = Set<String>()
            func add(_ n: MapNode) { out.insert(n.key); n.children.forEach(add) }
            for key in k { if let n = MindMap.find(model, key) { add(n) } }
            return out
        }()
        let plan = MapPlan(model: model, placing: keys != nil || addingFree, ghosts: ghosts)

        VStack(spacing: 0) {
            if let k = keys {
                placingHeader(items.filter { k.contains($0.key) }.sorted { (k.firstIndex(of: $0.key) ?? 0) < (k.firstIndex(of: $1.key) ?? 0) })
            } else if addingFree {
                addTextHeader
            } else {
                topBar(model, edits: edits, plan: plan)
            }
            Rectangle().fill(keys != nil || addingFree ? Palette.gold : Palette.divider).frame(height: 1)
            GeometryReader { geo in
                ZStack {
                    dots
                    if keys == nil && !addingFree && model.branches.isEmpty {
                        emptyMap
                    } else {
                        // الخريطة مثبّتة من الزاوية العليا ثم تُكبَّر وتُزاح (ولو كانت أكبر من الشاشة)
                        Color.clear
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .overlay(alignment: .topLeading) {
                                MapCanvas(model: model, plan: plan, target: target, selectedKey: selectedKey,
                                          onTap: { k in if keys == nil && !addingFree { selectedKey = selectedKey == k ? nil : k } },
                                          onLongTap: { k in if keys == nil && !addingFree { selectedKey = k; editKey = EditKey(id: k) } },
                                          onTarget: { t in if addingFree { freeTarget = FreeTarget(target: t) } else { target = target == t ? nil : t } })
                                    .fixedSize()
                                    .scaleEffect(scale, anchor: .topLeading)
                                    .offset(offset)
                            }
                    }
                    bottomBar(model, keys: keys)
                        .frame(maxHeight: .infinity, alignment: .bottom)
                        .padding(.bottom, 16).padding(.horizontal, 12)
                }
                .clipped()
                .contentShape(Rectangle())
                .gesture(
                    SimultaneousGesture(
                        MagnificationGesture()
                            .onChanged { v in
                                userMoved = true
                                let ns = min(3, max(0.2, baseScale * v))
                                scale = ns
                            }
                            .onEnded { _ in baseScale = scale },
                        DragGesture(minimumDistance: 4)
                            .onChanged { g in
                                userMoved = true
                                offset = CGSize(width: baseOffset.width + g.translation.width, height: baseOffset.height + g.translation.height)
                            }
                            .onEnded { _ in baseOffset = offset }
                    )
                )
                .onAppear {
                    viewport = geo.size
                    if !fitted && geo.size.width > 0 { fit(plan, in: geo.size); fitted = true }
                }
                .onChange(of: geo.size) { s in viewport = s; if !userMoved { fit(plan, in: s) } }
                .onChange(of: fitted) { f in if !f { userMoved = false; fit(plan, in: geo.size); fitted = true } }
                // الخريطة تتوسط من جديد كلما تغيّر حجمها (ما لم يحرّكها المستخدم بيده)
                .onChange(of: plan.size) { _ in if !userMoved { fit(plan, in: geo.size) } }
            }
        }
        .background(Palette.ivory.ignoresSafeArea())
        .environment(\.layoutDirection, .leftToRight)
        // الخريطة عاجية في الثيمين: الحقول والقوائم بألوان الوضع الفاتح
        .environment(\.colorScheme, .light)
        .onAppear {
            if !started {
                started = true
                session.migrateLegacyMap()
                placingKeys = placing?.filter { k in items.contains { $0.key == k } }
            }
        }
        .dialogSheet(item: $freeTarget) { ft in
            MapTextSheet(title: L("نص جديد", "New text"), color: freeInk, initial: "",
                         note: L("سيوضع: ", "Goes as: ") + describe(model, ft.target), confirm: L("إضافة", "Add")) { txt in
                session.mapAddFree(txt, ft.target)
                addingFree = false
                fitted = false
            }
        }
        .dialogSheet(item: $editKey) { ek in
            if let n = MindMap.find(model, ek.id) {
                let edited = !n.isFree && n.text != n.original
                let page = n.mark.flatMap { session.index(of: $0.page) }.map { $0 + 1 } ?? 1
                MapTextSheet(title: L("تعديل نص العقدة", "Edit node text"), color: Palette.navy, initial: n.text,
                             note: (n.isFree ? L("نص حر في الخريطة، غير مرتبط بالملخص", "A free text on the map, not linked to the summary")
                                    : L("يتغيّر في الخريطة فقط، والملخص يبقى كما هو", "Changes the map only; the summary stays as it is"))
                                + (edited ? L("\nالنص الأصلي: «\(MindMap.quote(n.original, words: 12))» · ص \(page)", "\nOriginal: “\(MindMap.quote(n.original, words: 12))” · p. \(page)") : ""),
                             confirm: L("حفظ", "Save"),
                             restore: edited ? { session.mapSetText(n.key, n.original) } : nil) { txt in
                    session.mapSetText(n.key, txt)
                }
            }
        }
        .sheet(isPresented: Binding(get: { share != nil }, set: { if !$0 { share = nil } })) { ShareSheet(items: share ?? []) }
    }

    // ---------- التكبير

    private func fit(_ plan: MapPlan, in size: CGSize? = nil) {
        let viewport = size ?? self.viewport
        guard viewport.width > 0, plan.size.width > 0 else { return }
        let sc = max(0.2, min(min(viewport.width / plan.size.width, viewport.height / plan.size.height), 1.15))
        scale = sc
        baseScale = sc
        offset = CGSize(width: (viewport.width - plan.size.width * sc) / 2, height: (viewport.height - plan.size.height * sc) / 2)
        baseOffset = offset
    }

    // ---------- الأشرطة

    private func topBar(_ model: MindMapModel, edits: MapEdits, plan: MapPlan) -> some View {
        let anyOpen = model.branches.contains { !$0.collapsed && !$0.children.isEmpty }
        return HStack(spacing: 10) {
            Button(action: close) {
                Text(I18n.english ? "‹" : "›").font(.system(size: 22, weight: .bold)).foregroundColor(Palette.navy)
                    .frame(width: 38, height: 38)
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.divider, lineWidth: 1))
            }
            VStack(alignment: .leading, spacing: 1) {
                Text(L("الخريطة الذهنية", "Mind map")).font(Fonts.display(20)).foregroundColor(Palette.navy).lineLimit(1)
                Text(session.title).font(Fonts.sans(12)).foregroundColor(Palette.textSecondary).lineLimit(1)
            }
            Spacer()
            Button { selectedKey = nil; addingFree = true; fitted = false } label: {
                HStack(spacing: 4) {
                    Text("＋").font(.system(size: 15, weight: .bold))
                    Text(L("أضف نصًا", "Add text")).font(Fonts.sans(13, .bold))
                }
                .foregroundColor(.white).padding(.horizontal, 12).padding(.vertical, 8)
                .background(RoundedRectangle(cornerRadius: 12).fill(freeInk))
            }
            if !model.branches.isEmpty {
                Button { userMoved = false; fit(plan) } label: { PhIcon("ph_fit", Palette.navy, 20, L("توسيط", "Fit")).frame(width: 40, height: 40) }
                Button {
                    let bk = Set(model.branches.map { $0.key })
                    session.mapSetCollapsed(anyOpen ? edits.collapsed.union(bk) : edits.collapsed.subtracting(bk))
                    fitted = false
                } label: { PhIcon("ph_list", Palette.navy, 20, anyOpen ? L("طيّ الكل", "Collapse all") : L("فتح الكل", "Expand all")).frame(width: 40, height: 40) }
                Menu {
                    Button { export(model, plan, kind: 0) } label: { Label { Text(L("مشاركة صورة PNG", "Share as PNG image")) } icon: { Image("ph_image") } }
                    Button { export(model, plan, kind: 1) } label: { Label { Text(L("مشاركة PDF", "Share as PDF")) } icon: { Image("ph_export") } }
                    if !standalone {
                        Button { export(model, plan, kind: 2) } label: { Label { Text(L("إضافة صفحة في آخر المستند", "Add as a page at the end")) } icon: { Image("ph_add_page") } }
                    }
                } label: {
                    HStack(spacing: 5) {
                        PhIcon("ph_export", Palette.goldDeep, 16)
                        Text(L("تصدير", "Export")).font(Fonts.sans(13, .bold)).foregroundColor(Palette.goldDeep)
                    }
                    .padding(.horizontal, 12).padding(.vertical, 8)
                    .background(RoundedRectangle(cornerRadius: 12).fill(LinearGradient(colors: [Color(hex: 0xFFFFF6DD), Palette.goldSoft], startPoint: .topLeading, endPoint: .bottomTrailing)))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.gold, lineWidth: 1))
                }
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .background(Color.white)
    }

    private var addTextHeader: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(L("أضف نصًا حرًا", "Add a free text")).font(Fonts.display(18)).foregroundColor(freeInk)
                Text(L("المس مكانه: فرع جديد، أو نقطة، أو نقطة فرعية تحت أي نص. لا يرتبط بالملخص.", "Tap where it goes: a new branch, a point, or a sub-point under any text. Not linked to the summary."))
                    .font(Fonts.sans(12)).foregroundColor(Palette.textSecondary)
            }
            Spacer()
            cancelButton { addingFree = false; fitted = false }
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .background(Color(hex: 0xFFF1ECFF))
    }

    private func placingHeader(_ texts: [MindMap.Item]) -> some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 8) {
                Text(texts.count > 1 ? L("اختر مكان \(texts.count) نصوص", "Choose where \(texts.count) texts go") : L("اختر مكان النص", "Choose where the text goes"))
                    .font(Fonts.display(18)).foregroundColor(Palette.navy)
                VStack(alignment: .leading, spacing: 2) {
                    Text(texts.count > 1 ? L("النصوص التي تضعها (بالترتيب)", "The texts you are placing (in order)") : L("النص الذي تضعه (كما ظلّلته)", "The text you are placing (as highlighted)"))
                        .font(Fonts.sans(11, .bold)).foregroundColor(Palette.goldDeep)
                    ForEach(Array(texts.prefix(3).enumerated()), id: \.offset) { i, t in
                        Text((texts.count > 1 ? "\(i + 1). " : "") + t.text).font(Fonts.sans(14)).foregroundColor(Palette.textPrimary)
                            .lineLimit(texts.count > 1 ? 1 : 4)
                    }
                    if texts.count > 3 { Text(L("و\(texts.count - 3) غيرها", "and \(texts.count - 3) more")).font(Fonts.sans(12)).foregroundColor(Palette.textSecondary) }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 12).padding(.vertical, 8)
                .background(RoundedRectangle(cornerRadius: 12).fill(Color.white))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.gold, style: StrokeStyle(lineWidth: 2, dash: [8, 6])))
            }
            cancelButton { cancelPlacing() }
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .background(LinearGradient(colors: [Color(hex: 0xFFFFF6DD), Palette.goldSoft], startPoint: .topLeading, endPoint: .bottomTrailing))
    }

    private func cancelButton(_ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(L("إلغاء", "Cancel")).font(Fonts.sans(14, .bold)).foregroundColor(Palette.navy)
                .padding(.horizontal, 14).padding(.vertical, 9)
                .background(RoundedRectangle(cornerRadius: 12).fill(Color.white))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.divider, lineWidth: 1))
        }
    }

    @ViewBuilder private func bottomBar(_ model: MindMapModel, keys: [String]?) -> some View {
        if keys != nil, let t = target {
            HStack(spacing: 10) {
                Text(L("سيوضع: ", "Goes as: ") + describe(model, t)).font(Fonts.sans(13.5)).foregroundColor(.white).lineLimit(2)
                Button { target = nil } label: { Text(L("تراجع", "Undo")).font(Fonts.sans(13.5, .bold)).foregroundColor(.white.opacity(0.85)).padding(.horizontal, 8).padding(.vertical, 10) }
                Button { confirmPlacing() } label: {
                    Text(L("ضعه هنا ✓", "Place here ✓")).font(Fonts.sans(14, .bold)).foregroundColor(barDark)
                        .padding(.horizontal, 16).padding(.vertical, 10).background(RoundedRectangle(cornerRadius: 12).fill(Palette.gold))
                }
            }
            .padding(.leading, 16).padding(.trailing, 8).padding(.vertical, 8)
            .frame(maxWidth: 640)
            .background(RoundedRectangle(cornerRadius: 18).fill(barDark).shadow(color: .black.opacity(0.25), radius: 12, y: 4))
        } else if keys != nil {
            hint(L("المس أي «+» لتختار مكان النص", "Tap any “+” to choose where it goes"))
        } else if addingFree {
            hint(L("المس أي «+» ثم اكتب النص الجديد", "Tap any “+”, then type the new text"))
        } else if let sk = selectedKey, let n = MindMap.find(model, sk) {
            nodeActions(model, n)
        } else if !model.branches.isEmpty {
            hint(L("انقر أي نص لإجراءاته · حرّك وكبّر بإصبعين", "Tap a text for actions · pan and zoom with two fingers"))
        }
    }

    private func hint(_ t: String) -> some View {
        Text(t).font(Fonts.sans(12)).foregroundColor(Palette.textSecondary)
            .padding(.horizontal, 12).padding(.vertical, 7)
            .background(RoundedRectangle(cornerRadius: 12).fill(Color.white))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.divider, lineWidth: 1))
    }

    private func nodeActions(_ model: MindMapModel, _ n: MapNode) -> some View {
        let sib = MindMap.siblingsOf(model, n.key)
        let idx = sib?.1.firstIndex { $0.key == n.key } ?? -1
        let canUp = idx > 0
        let canDown = sib != nil && idx >= 0 && idx < (sib!.1.count - 1)
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 2) {
                if let m = n.mark {
                    let page = (session.index(of: m.page) ?? 0) + 1
                    actionChip(L("ص \(page) · انتقال", "p. \(page) · Go"), primary: true) { jump(m) }
                }
                actionChip("✎ " + L("تعديل النص", "Edit text")) { editKey = EditKey(id: n.key) }
                actionChip("＋ " + L("نقطة فرعية", "Sub-point")) { freeTarget = FreeTarget(target: MapTarget(parent: n.key, before: nil)) }
                actionChip(L("نقل إلى مكان آخر", "Move elsewhere")) { startMove(n.key) }
                actionChip("↑ " + L("أعلى", "Up"), enabled: canUp) { session.mapMove(n.key, -1) }
                actionChip("↓ " + L("أسفل", "Down"), enabled: canDown) { session.mapMove(n.key, 1) }
                if !n.children.isEmpty || n.collapsed {
                    actionChip(n.collapsed ? L("فتح", "Expand") : L("طيّ", "Collapse")) { session.mapToggleCollapsed(n.key) }
                }
                actionChip(n.isFree ? L("حذف", "Delete") : L("إزالة من الخريطة", "Remove from map"), danger: true) {
                    let free = n.isFree
                    let c = session.mapRemove(n.key)
                    selectedKey = nil
                    app.message(free && c > 1 ? L("حُذف النص مع ما تحته (\(c - 1))", "Deleted with \(c - 1) under it")
                                : free ? L("حُذف النص", "Text deleted")
                                : c > 1 ? L("أُزيل من الخريطة مع ما تحته (\(c - 1))، ويبقى في الملخص", "Removed from the map with \(c - 1) under it; it stays in the summary")
                                : L("أُزيل من الخريطة، ويبقى في الملخص", "Removed from the map; it stays in the summary"))
                }
                actionChip("✕") { selectedKey = nil }
            }
            .padding(6)
        }
        .fixedSize(horizontal: true, vertical: false)
        .background(RoundedRectangle(cornerRadius: 16).fill(barDark).shadow(color: .black.opacity(0.25), radius: 12, y: 4))
    }

    private func actionChip(_ label: String, primary: Bool = false, danger: Bool = false, enabled: Bool = true, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(Fonts.sans(13, .bold)).lineLimit(1)
                .foregroundColor((primary ? barDark : (danger ? Color(hex: 0xFFFFB4B0) : .white)).opacity(enabled ? 1 : 0.35))
                .padding(.horizontal, 11).padding(.vertical, 8)
                .background(RoundedRectangle(cornerRadius: 10).fill(primary ? Palette.gold : Color.clear))
        }
        .disabled(!enabled)
    }

    private var dots: some View {
        Canvas { ctx, size in
            let step: CGFloat = 22
            var y = step / 2
            while y < size.height {
                var x = step / 2
                while x < size.width {
                    ctx.fill(Path(ellipseIn: CGRect(x: x - 1.3, y: y - 1.3, width: 2.6, height: 2.6)), with: .color(mapDot))
                    x += step
                }
                y += step
            }
        }
        .allowsHitTesting(false)
    }

    private var emptyMap: some View {
        VStack(spacing: 6) {
            PhIcon("ph_mindmap", Palette.gold, 56)
            Text(L("خريطتك فارغة", "Your map is empty")).font(Fonts.display(20)).foregroundColor(Palette.navy).padding(.top, 6)
            Text(standalone ? L("اكتب أول فرع في خريطتك.", "Write the first branch of your map.")
                 : L("من الملخصات ← «كما التُقط»: اضغط نصًا واختر «ضعه في الخريطة»، أو اكتب نصًا حرًا هنا.",
                     "In Summaries → “As captured”: tap a text and choose “Place on the map”, or write a free text here."))
                .font(Fonts.sans(14)).foregroundColor(Palette.textSecondary).multilineTextAlignment(.center)
            Button { addingFree = true; fitted = false } label: {
                Text("＋ " + L("أضف نصًا", "Add text")).font(Fonts.sans(15, .bold)).foregroundColor(.white)
                    .padding(.horizontal, 18).padding(.vertical, 11).background(RoundedRectangle(cornerRadius: 14).fill(freeInk))
            }
            .padding(.top, 8)
        }
        .padding(32)
    }

    // ---------- الإجراءات

    private func close() {
        if standalone { app.closeTab(session.id) } else { onClose() }
    }

    private func jump(_ m: TextMark) {
        onClose()
        if let i = session.index(of: m.page) { session.goTo(page: i) }
    }

    private func startMove(_ k: String) {
        selectedKey = nil
        placingKeys = [k]
        target = nil
        fitted = false
    }

    private func cancelPlacing() {
        if fromSummary { onClose() } else {
            placingKeys = nil
            target = nil
            fitted = false
        }
    }

    private func confirmPlacing() {
        guard let k = placingKeys, let t = target else { return }
        session.mapPlace(k, t)
        app.message(k.count == 1 ? L("وُضع النص في الخريطة", "Placed on the map") : L("وُضعت \(k.count) نصوص في الخريطة", "\(k.count) texts placed on the map"))
        if fromSummary { onClose() } else {
            placingKeys = nil
            target = nil
            fitted = false
        }
    }

    private func describe(_ model: MindMapModel, _ t: MapTarget) -> String {
        let p = t.parent.flatMap { MindMap.find(model, $0) }
        let before = t.before.flatMap { MindMap.find(model, $0) }
        guard let pp = p else { return model.branches.count <= 1 ? L("أول فرع", "the first branch") : L("فرع جديد", "a new branch") }
        if model.branches.contains(where: { $0.key == pp.key }) {
            return L("نقطة تحت «\(MindMap.quote(pp.text))»", "a point under “\(MindMap.quote(pp.text))”")
                + (before.map { L(" قبل «\(MindMap.quote($0.text))»", " before “\(MindMap.quote($0.text))”") } ?? L(" في آخرها", " at the end"))
        }
        return L("نقطة فرعية تحت «\(MindMap.quote(pp.text))»", "a sub-point under “\(MindMap.quote(pp.text))”")
    }

    // ---------- التصدير

    @MainActor
    private func export(_ model: MindMapModel, _ plan: MapPlan, kind: Int) {
        selectedKey = nil
        let content = MapCanvas(model: model, plan: plan, target: nil, selectedKey: nil)
            .background(Palette.ivory)
            .overlay(alignment: .bottomTrailing) {
                Text(L("هامش · Hamish", "Hamish")).font(Fonts.sans(max(18, min(44, plan.size.width / 90)))).foregroundColor(Palette.goldDeep).padding(20)
            }
        let r = ImageRenderer(content: content)
        r.scale = 2
        guard let img = r.uiImage else { app.message(L("تعذر تجهيز الخريطة", "Could not prepare the map")); return }
        let safe = String(session.title.map { "\\/:*?\"<>|".contains($0) ? "_" : $0 }.prefix(60))
        let name = (safe.isEmpty ? "map" : safe) + " - " + L("خريطة", "map")
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("share", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        switch kind {
        case 0:
            let u = dir.appendingPathComponent(name + ".png")
            try? img.pngData()?.write(to: u)
            share = [u]
        case 1:
            let u = dir.appendingPathComponent(name + ".pdf")
            let k = min(1, 1600 / max(img.size.width, img.size.height))
            let rect = CGRect(x: 0, y: 0, width: img.size.width * k, height: img.size.height * k)
            let data = UIGraphicsPDFRenderer(bounds: rect).pdfData { ctx in
                ctx.beginPage()
                img.draw(in: rect)
            }
            try? data.write(to: u)
            share = [u]
        default:
            let aspect = img.size.width / max(1, img.size.height)
            let size = aspect >= 1 ? CGSize(width: 842, height: 595) : CGSize(width: 595, height: 842)
            let last = session.pageCount - 1
            session.addBlankPage(after: last, kind: "plain", color: 0xFFFFFFFF, size: size)
            let page = last + 1
            session.insertImage(img, page: page)
            if let id = session.selectedImage, let pid = session.pageId(page), var it = session.data.images.first(where: { $0.id == id }) {
                var w = size.width * 0.92
                if w / aspect > size.height * 0.92 { w = size.height * 0.92 * aspect }
                it.page = pid
                it.w = w
                it.x = (size.width - w) / 2
                it.y = (size.height - w / aspect) / 2
                session.upsertImage(it)
            }
            session.selectedImage = nil
            app.message(L("أُضيفت الخريطة صفحةً في آخر المستند", "The map was added as the last page"))
            onClose()
        }
    }
}

/// نافذة كتابة نص الخريطة (نص حر جديد، أو تعديل نص عقدة).
private struct MapTextSheet: View {
    let title: String
    let color: Color
    let initial: String
    let note: String
    let confirm: String
    var restore: (() -> Void)? = nil
    let onSave: (String) -> Void
    @State private var text = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let ok = !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        // نافذة حوار مثل FreeTextDialog / NodeTextDialog في أندرويد
        AlertDialogCard(title: title, width: 440, titleColor: color) {
            VStack(alignment: .leading, spacing: 8) {
                OutlinedEditor(text: $text, minHeight: restore == nil && initial.isEmpty ? 70 : 90)
                Text(note).font(Fonts.sans(12)).foregroundColor(Palette.textSecondary).lineSpacing(3)
                    .fixedSize(horizontal: false, vertical: true)
            }
        } buttons: {
            if let r = restore {
                DialogButton(label: L("استعادة الأصلي", "Restore original"), color: Palette.goldDeep) { r(); dismiss() }
            }
            DialogButton(label: L("إلغاء", "Cancel")) { dismiss() }
            DialogButton(label: confirm, bold: true, enabled: ok) { onSave(text); dismiss() }
        }
        .onAppear { text = initial }
    }
}
