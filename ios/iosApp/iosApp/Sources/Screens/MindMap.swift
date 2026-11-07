import SwiftUI

/// الخريطة الذهنية: المركز، والفروع، والنقاط تحتها. تُبنى يدويًا أو من نصوص التمييز.
struct MindMapView: View {
    @ObservedObject var session: DocSession
    @EnvironmentObject var app: AppController
    let standalone: Bool
    @State private var editing: EditTarget? = nil
    @State private var zoom: CGFloat = 1
    @GestureState private var pinch: CGFloat = 1
    @State private var showMarks = false
    @Environment(\.dismiss) private var dismiss

    struct EditTarget: Identifiable {
        let id = UUID()
        let nodeId: String?     // nil = فرع جديد، أو "+parent" لنقطة جديدة
        let parent: String?
        let text: String
    }

    private var map: MindMapData { session.data.map ?? MindMapData(title: session.title) }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                Text(map.title).font(Fonts.display(20)).foregroundColor(Palette.navy).lineLimit(1)
                Text(L("\(MapOps.flatten(map.branches).count) فكرة", "\(MapOps.flatten(map.branches).count) ideas")).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary)
                Spacer()
                if !standalone {
                    Button { showMarks.toggle() } label: {
                        Text(L("من الملخصات", "From summaries")).font(Fonts.sans(13, .bold)).foregroundColor(Palette.navy)
                            .padding(.horizontal, 12).padding(.vertical, 7).background(Capsule().fill(Palette.navySoft))
                    }
                }
                Button { editing = EditTarget(nodeId: nil, parent: nil, text: "") } label: {
                    Text("+ " + L("فرع", "Branch")).font(Fonts.sans(13, .bold)).foregroundColor(Color(hex: 0xFF1E2A44))
                        .padding(.horizontal, 12).padding(.vertical, 7).background(Capsule().fill(Lib.accent))
                }
                Button { zoom = max(0.4, zoom / 1.2) } label: { PhIcon("ph_zoom_out", Palette.textPrimary, 20).frame(width: 36, height: 36) }
                Button { zoom = min(2.5, zoom * 1.2) } label: { PhIcon("ph_zoom_in", Palette.textPrimary, 20).frame(width: 36, height: 36) }
                Button {
                    if standalone { app.closeTab(session.id) } else { session.mapOpen = false; dismiss() }
                } label: { PhIcon("ph_close", Palette.textSecondary, 18).frame(width: 36, height: 36) }
            }
            .padding(.horizontal, 18).padding(.vertical, 10)
            .background(Color.white)
            .overlay(Rectangle().fill(Palette.divider).frame(height: 1), alignment: .bottom)
            HStack(spacing: 0) {
                GeometryReader { geo in
                let layout = MapLayout(map: map, rtl: !app.english, minSize: CGSize(width: geo.size.width / zoom, height: geo.size.height / zoom))
                ScrollView([.horizontal, .vertical]) {
                    ZStack(alignment: .topLeading) {
                        Canvas { ctx, _ in
                            for l in layout.links {
                                var p = Path()
                                p.move(to: l.0)
                                let mx = (l.0.x + l.1.x) / 2
                                p.addCurve(to: l.1, control1: CGPoint(x: mx, y: l.0.y), control2: CGPoint(x: mx, y: l.1.y))
                                ctx.stroke(p, with: .color(l.2.opacity(0.7)), lineWidth: 2.5)
                            }
                        }
                        .frame(width: layout.size.width, height: layout.size.height)
                        // المركز
                        Text(map.title).font(Fonts.display(18)).foregroundColor(.white).multilineTextAlignment(.center)
                            .frame(width: MapLayout.centerW).padding(.vertical, 14)
                            .background(RoundedRectangle(cornerRadius: 22).fill(Palette.navy))
                            .position(layout.center)
                        ForEach(layout.nodes, id: \.node.id) { n in
                            nodeView(n)
                                .position(n.pos)
                        }
                    }
                    .frame(width: layout.size.width, height: layout.size.height)
                    .scaleEffect(zoom * pinch, anchor: .topLeading)
                    .frame(width: layout.size.width * zoom * pinch, height: layout.size.height * zoom * pinch, alignment: .topLeading)
                    .environment(\.layoutDirection, .leftToRight)
                }
                .environment(\.layoutDirection, .leftToRight)
                .gesture(MagnificationGesture().updating($pinch) { v, st, _ in st = v }.onEnded { v in zoom = max(0.4, min(2.5, zoom * v)) })
                }
                if showMarks { marksList }
            }
        }
        .background(Palette.ivory.ignoresSafeArea())
        .sheet(item: $editing) { t in
            NameSheet(title: t.nodeId == nil ? (t.parent == nil ? L("فرع جديد", "New branch") : L("نقطة جديدة", "New point")) : L("تعديل الفكرة", "Edit idea"),
                      initial: t.text) { text in
                session.edit { d in
                    if d.map == nil { d.map = MindMapData(title: session.title) }
                    if let id = t.nodeId { MapOps.update(&d.map!.branches, id) { $0.text = text } }
                    else if let p = t.parent { MapOps.update(&d.map!.branches, p) { $0.children.append(MapNodeData(text: text)); $0.collapsed = false } }
                    else { d.map!.branches.append(MapNodeData(text: text)) }
                }
            }
        }
    }

    private func nodeView(_ n: MapLayout.Placed) -> some View {
        let isBranch = n.depth == 0
        return HStack(spacing: 6) {
            Text(n.node.text).font(Fonts.sans(isBranch ? 15 : 13, isBranch ? .bold : .regular)).foregroundColor(Palette.textPrimary)
                .lineLimit(4).multilineTextAlignment(.leading)
            if n.node.collapsed && !n.node.children.isEmpty {
                Text("+\(n.node.count)").font(Fonts.sans(11, .bold)).foregroundColor(.white).padding(.horizontal, 6).padding(.vertical, 2)
                    .background(Capsule().fill(n.color))
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 9)
        .frame(width: MapLayout.nodeW, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 14).fill(isBranch ? n.color.opacity(0.16) : Color.white))
        .roundedBorder(n.color.opacity(isBranch ? 0.8 : 0.45), radius: 14, width: isBranch ? 2 : 1)
        .environment(\.layoutDirection, .leftToRight)
        .contextMenu {
            Button(L("نقطة تحتها", "Add a point under it")) { editing = EditTarget(nodeId: nil, parent: n.node.id, text: "") }
            Button(L("تعديل", "Edit")) { editing = EditTarget(nodeId: n.node.id, parent: nil, text: n.node.text) }
            if !n.node.children.isEmpty {
                Button(n.node.collapsed ? L("فتح", "Expand") : L("طيّ", "Collapse")) {
                    session.edit { d in MapOps.update(&d.map!.branches, n.node.id) { $0.collapsed.toggle() } }
                }
            }
            if let mid = n.node.markId, let m = session.data.marks.first(where: { $0.id == mid }), let i = session.index(of: m.page) {
                Button(L("انتقال إلى الموضع", "Go to it")) { session.mapOpen = false; session.goTo(page: i) }
            }
            Button(role: .destructive) { session.edit { d in MapOps.remove(&d.map!.branches, n.node.id) } } label: { Text(L("حذف", "Delete")) }
        }
        .onTapGesture { editing = EditTarget(nodeId: nil, parent: n.node.id, text: "") }
    }

    private var marksList: some View {
        let used = Set(MapOps.flatten(map.branches).compactMap { $0.markId })
        return ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                Text(L("انقر نصًا ليصبح فرعًا", "Tap a text to make it a branch")).font(Fonts.sans(12)).foregroundColor(Palette.textSecondary)
                ForEach(session.allMarks().filter { !used.contains($0.id) }) { m in
                    Button {
                        session.edit { d in
                            if d.map == nil { d.map = MindMapData(title: session.title) }
                            d.map!.branches.append(MapNodeData(text: TextTidy.tidy(m.text), markId: m.id))
                        }
                    } label: {
                        Text(TextTidy.tidy(m.text)).font(Fonts.sans(13)).foregroundColor(Palette.textPrimary).lineLimit(3)
                            .frame(maxWidth: .infinity, alignment: .leading).padding(10)
                            .background(RoundedRectangle(cornerRadius: 10).fill(Color(hex: m.color).opacity(0.2)))
                    }
                }
            }
            .padding(14)
        }
        .frame(width: 280)
        .background(Palette.drawerBg)
    }
}

enum MapOps {
    static func update(_ list: inout [MapNodeData], _ id: String, _ f: (inout MapNodeData) -> Void) {
        for i in list.indices {
            if list[i].id == id { f(&list[i]); return }
            update(&list[i].children, id, f)
        }
    }

    static func remove(_ list: inout [MapNodeData], _ id: String) {
        list.removeAll { $0.id == id }
        for i in list.indices { remove(&list[i].children, id) }
    }

    static func flatten(_ list: [MapNodeData]) -> [MapNodeData] {
        list.flatMap { [$0] + flatten($0.children) }
    }
}

/// تخطيط الشجرة: المركز على جانب، والفروع في أعمدة متتالية.
struct MapLayout {
    struct Placed { let node: MapNodeData; let depth: Int; let pos: CGPoint; let color: Color }
    static let nodeW: CGFloat = 220
    static let centerW: CGFloat = 200
    static let colGap: CGFloat = 70
    static let rowH: CGFloat = 76
    static let colors: [Color] = [Color(hex: 0xFF2F6FDB), Color(hex: 0xFF2E9D6B), Color(hex: 0xFFD9A21B), Color(hex: 0xFFB0413E),
                                  Color(hex: 0xFF8B5CF6), Color(hex: 0xFF0F766E), Color(hex: 0xFFB5651D)]

    var nodes: [Placed] = []
    var links: [(CGPoint, CGPoint, Color)] = []
    var center: CGPoint = .zero
    var size: CGSize = .zero
    var total = 0

    init(map: MindMapData, rtl: Bool, minSize: CGSize = CGSize(width: 700, height: 500)) {
        var row: CGFloat = 0
        var maxDepth = 0
        var raw: [(MapNodeData, Int, CGFloat, Int)] = []   // العقدة، العمق، الصف، الفرع
        func place(_ n: MapNodeData, _ depth: Int, _ branch: Int) -> CGFloat {
            maxDepth = max(maxDepth, depth)
            let kids = n.collapsed ? [] : n.children
            let y: CGFloat
            if kids.isEmpty {
                y = row
                row += 1
            } else {
                let ys = kids.map { place($0, depth + 1, branch) }
                y = (ys.first! + ys.last!) / 2
            }
            raw.append((n, depth, y, branch))
            return y
        }
        var branchYs: [CGFloat] = []
        for (b, n) in map.branches.enumerated() { branchYs.append(place(n, 0, b)) }
        total = MapOps.flatten(map.branches).count
        let rows = max(1, row)
        let height = rows * Self.rowH + 80
        let cols = CGFloat(maxDepth + 1)
        let width = Self.centerW + 60 + cols * (Self.nodeW + Self.colGap) + 40
        let W = max(minSize.width, width), H = max(minSize.height, height)
        size = CGSize(width: W, height: H)
        let cy = branchYs.isEmpty ? H / 2 : ((branchYs.min()! + branchYs.max()!) / 2) * Self.rowH + 40 + Self.rowH / 2
        func x(_ depth: Int) -> CGFloat {
            let lx = 20 + Self.centerW + 60 + CGFloat(depth) * (Self.nodeW + Self.colGap) + Self.nodeW / 2
            return rtl ? W - lx : lx
        }
        center = CGPoint(x: rtl ? W - 20 - Self.centerW / 2 : 20 + Self.centerW / 2, y: cy)
        var posById: [String: CGPoint] = [:]
        for (n, d, y, b) in raw {
            let p = CGPoint(x: x(d), y: y * Self.rowH + 40 + Self.rowH / 2)
            posById[n.id] = p
            nodes.append(Placed(node: n, depth: d, pos: p, color: Self.colors[b % Self.colors.count]))
        }
        let half = Self.nodeW / 2, ch = Self.centerW / 2
        func edge(_ p: CGPoint, out: Bool, w: CGFloat) -> CGPoint {
            CGPoint(x: p.x + ((out != rtl) ? w : -w), y: p.y)
        }
        for (b, n) in map.branches.enumerated() {
            if let p = posById[n.id] { links.append((edge(center, out: true, w: ch), edge(p, out: false, w: half), Self.colors[b % Self.colors.count])) }
        }
        func link(_ n: MapNodeData, _ b: Int) {
            guard !n.collapsed, let p = posById[n.id] else { return }
            for c in n.children {
                if let q = posById[c.id] { links.append((edge(p, out: true, w: half), edge(q, out: false, w: half), Self.colors[b % Self.colors.count])) }
                link(c, b)
            }
        }
        for (b, n) in map.branches.enumerated() { link(n, b) }
    }
}
