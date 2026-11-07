import UIKit
import PDFKit
import PencilKit

/// وضع العرض التجريبي (لقطات المحاكي في CI فقط):
///   -hamishDemo /path/file.pdf   يستورد الملف ويملؤه بأمثلة (كتابة، تظليل، ملاحظة، نص، ملصق، مواد، بطاقات، خريطة)
///   -hamishScreen <name>         يفتح شاشة معيّنة لالتقاط صورتها
enum Demo {
    static func run(args: [String]) {
        guard let i = args.firstIndex(of: "-hamishDemo"), i + 1 < args.count else { return }
        let path = args[i + 1]
        let screen = args.firstIndex(of: "-hamishScreen").flatMap { $0 + 1 < args.count ? args[$0 + 1] : nil } ?? "reader"
        let c = AppController.shared
        c.skipLogin()
        if args.contains("-hamishEnglish") { c.setEnglish(true) }
        if args.contains("-hamishLight") { c.setTheme(.light) }
        UserDefaults.standard.set(true, forKey: "test_premium")
        Task { await Premium.shared.refresh() }

        let name = URL(fileURLWithPath: path).deletingPathExtension().lastPathComponent
        var entry = c.library.first { $0.name == name }
        if entry == nil, let e = c.repo.importPDF(from: URL(fileURLWithPath: path), name: name, folder: "") {
            c.library.append(e)
            entry = e
            seed(e)
        }
        guard let e = entry else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { show(screen, e) }
    }

    /// أمثلة على المستند والمكتبة.
    private static func seed(_ e: DocEntry) {
        let c = AppController.shared
        let subject = I18n.english ? "Pharmacology" : "علم الأدوية"
        c.addFolder(subject)
        c.addFolder(I18n.english ? "Anatomy" : "التشريح")
        c.addFolder(I18n.english ? "Physiology" : "علم وظائف الأعضاء")
        c.update(e.id) { d in
            d.folder = subject
            d.coverColor = 0xFF1F6F6B
            d.sticker = "pill"
            d.favorite = true
            d.lastOpened = nowMs()
            d.lastPage = 2
            d.readMs = 47 * 60_000
        }
        c.setExamDate(subject, Calendar.current.date(byAdding: .day, value: 12, to: Date()))
        if let n = c.repo.createNote(name: I18n.english ? "Lecture notes" : "ملاحظات المحاضرة",
                                    choice: PaperChoice(kind: "cornell", color: 0xFFFFF8E1, size: "a4", landscape: false), folder: subject) {
            var n2 = n
            n2.coverColor = 0xFF7A3E48
            n2.sticker = "heart"
            c.library.append(n2)
        }
        let m = c.repo.createMap(name: I18n.english ? "Antibiotics" : "المضادات الحيوية", folder: subject)
        c.library.append(m)
        c.saveLibrary()
        c.stats[c.todayKey()] = 38 * 60_000
        for k in 1...9 {
            if let d = Calendar.current.date(byAdding: .day, value: -k, to: Date()) {
                c.stats[AppController.dayKey(d)] = Int64([75, 40, 62, 0, 90, 55, 68, 30, 80][k - 1]) * 60_000
                c.stats["f|\(subject)|\(AppController.dayKey(d))"] = Int64([50, 30, 40, 0, 60, 35, 40, 20, 55][k - 1]) * 60_000
            }
        }
        c.addCard(front: I18n.english ? "How does penicillin work?" : "كيف يعمل البنسلين؟",
                  back: I18n.english ? "It blocks bacterial cell-wall synthesis." : "يمنع بناء الجدار الخلوي للبكتيريا.", folder: subject, docId: e.id)
        c.addCard(front: I18n.english ? "Name a macrolide" : "اذكر دواءً من الماكروليدات", back: "Azithromycin", folder: subject, docId: e.id)
        c.addCard(front: I18n.english ? "Aminoglycoside side effect" : "أثر جانبي للأمينوغليكوزيدات", back: I18n.english ? "Ototoxicity, nephrotoxicity" : "سمية الأذن والكلى", folder: subject)

        // المستند نفسه: تظليل من نص الصفحة، كتابة، ملاحظة لاصقة، نص، ملصق، علامة
        let s = DocSession(entry: e)
        guard let pdf = s.pdf, let page = pdf.page(at: 0), let pid = s.pageId(0) else { return }
        let size = s.pageSize(0)
        let b = page.bounds(for: .cropBox)
        func toTop(_ r: CGRect) -> CGRect { CGRect(x: r.minX - b.minX, y: b.maxY - r.maxY, width: r.width, height: r.height) }
        let lines = (page.selection(for: b)?.selectionsByLine() ?? []).filter { ($0.string ?? "").trimmingCharacters(in: .whitespaces).count > 12 }
        let palette: [UInt32] = [0xFFFFEB3B, 0xFF69F0AE, 0xFFFF80AB, 0xFF40C4FF]
        var marks: [TextMark] = []
        for (k, l) in lines.prefix(12).enumerated() where k % 3 == 1 {
            marks.append(TextMark(id: UUID().uuidString, page: pid, rects: [MarkRect(toTop(l.bounds(for: page)))],
                                  color: palette[(k / 3) % palette.count], text: l.string ?? "", createdAt: nowMs(), level: k == 4 ? 2 : 1))
        }
        // تظليل في صفحات أخرى
        for p in 1..<min(3, pdf.pageCount) {
            guard let pg = pdf.page(at: p), let ppid = s.pageId(p) else { continue }
            let bb = pg.bounds(for: .cropBox)
            for l in (pg.selection(for: bb)?.selectionsByLine() ?? []).filter({ ($0.string ?? "").count > 15 }).prefix(6).enumerated().filter({ $0.offset % 2 == 0 }).map({ $0.element }) {
                let r = l.bounds(for: pg)
                marks.append(TextMark(id: UUID().uuidString, page: ppid, rects: [MarkRect(CGRect(x: r.minX - bb.minX, y: bb.maxY - r.maxY, width: r.width, height: r.height))],
                                      color: palette[p % palette.count], text: l.string ?? "", createdAt: nowMs()))
            }
        }
        s.edit { d in
            d.marks = marks
            d.bookmarks = [pid]
            d.notes.append(StickyNote(id: UUID().uuidString, page: pid, x: size.width * 0.62, y: size.height * 0.62, w: size.width * 0.3, h: size.width * 0.24,
                                      color: 0xFFFFF59D, text: I18n.english ? "Review before the exam!" : "راجع هذا قبل الامتحان!"))
            d.boxes.append(TextBox(id: UUID().uuidString, page: pid, x: size.width * 0.08, y: size.height * 0.9, w: size.width * 0.5,
                                   text: I18n.english ? "Important: dose depends on kidney function" : "مهم: الجرعة حسب وظائف الكلى", color: 0xFFC62828, size: 13))
            d.images.append(ImageItem(id: UUID().uuidString, file: "sticker:star", page: pid, x: size.width * 0.82, y: size.height * 0.04, w: size.width * 0.12, aspect: 1))
            d.map = MindMapData(title: e.name, branches: [
                MapNodeData(text: I18n.english ? "Cell wall" : "الجدار الخلوي", children: [MapNodeData(text: "Penicillins"), MapNodeData(text: "Cephalosporins")]),
                MapNodeData(text: I18n.english ? "Protein synthesis" : "بناء البروتين", children: [MapNodeData(text: "Macrolides"), MapNodeData(text: "Aminoglycosides"), MapNodeData(text: "Tetracyclines")]),
                MapNodeData(text: I18n.english ? "DNA" : "الحمض النووي", children: [MapNodeData(text: "Fluoroquinolones")])
            ])
        }
        // كتابة بالقلم: دائرة حول عنوان وسهم وتوقيع
        var strokes: [PKStroke] = []
        let ink = PKInk(.pen, color: UIColor(argb: 0xFF1565C0))
        func stroke(_ pts: [CGPoint], _ w: CGFloat = 2.4, _ inkx: PKInk? = nil) -> PKStroke {
            let sp = pts.enumerated().map { i, p in
                PKStrokePoint(location: p, timeOffset: Double(i) * 0.01, size: CGSize(width: w, height: w), opacity: 1, force: 1, azimuth: 0, altitude: .pi / 2)
            }
            return PKStroke(ink: inkx ?? ink, path: PKStrokePath(controlPoints: sp, creationDate: Date()))
        }
        let cx = size.width * 0.5, cy = size.height * 0.08
        strokes.append(stroke((0...60).map { k in
            let t = Double(k) / 60 * 2 * .pi
            return CGPoint(x: cx + cos(t) * size.width * 0.36, y: cy + sin(t) * size.height * 0.035)
        }))
        strokes.append(stroke([CGPoint(x: size.width * 0.86, y: size.height * 0.2), CGPoint(x: size.width * 0.93, y: size.height * 0.3), CGPoint(x: size.width * 0.9, y: size.height * 0.45)], 2.4,
                              PKInk(.pen, color: UIColor(argb: 0xFFC62828))))
        var x = size.width * 0.1
        var pts: [CGPoint] = []
        while x < size.width * 0.5 {
            pts.append(CGPoint(x: x, y: size.height * 0.84 + sin(x / 7) * 6))
            x += 2
        }
        strokes.append(stroke(pts, 2.2, PKInk(.pen, color: UIColor(argb: 0xFF2E7D32))))
        var dr = PKDrawing()
        dr.strokes = strokes
        s.setInk(pid, dr, record: false)
        s.flush()
        c.flush()
    }

    private static func show(_ screen: String, _ e: DocEntry) {
        let c = AppController.shared
        c.nightPages = screen == "night"
        c.toolbarStyle = screen == "highlighter" ? .classic : .penBar
        c.penBarOpen = false
        c.shapeActive = false
        c.selectTool(.fountain)
        if ["library", "subject", "cards", "stats", "settings", "paywall", "signin"].contains(screen) { c.activate(nil) }
        if screen == "library" { return }
        if screen == "signin" { c.loginSeen = false; return }
        if screen == "settings" { c.settingsOpen = true; return }
        if screen == "stats" { c.statsOpen = true; return }
        if screen == "cards" { c.openDeck = "*"; return }
        if screen == "paywall" {
            Premium.shared.endTest()
            Premium.shared.paywallOpen = true
            return
        }
        if screen == "subject" { c.libraryFolder = c.entry(e.id)?.folder; c.libraryFilter = .all; return }
        if screen == "mapdoc", let m = c.library.first(where: { $0.isMap }) {
            let s = DocSession(entry: m)
            s.edit { d in d.map = MindMapData(title: m.name, branches: [
                MapNodeData(text: "β-lactams", children: [MapNodeData(text: "Penicillin"), MapNodeData(text: "Amoxicillin")]),
                MapNodeData(text: "Macrolides", children: [MapNodeData(text: "Azithromycin")])
            ]) }
            s.flush()
            c.openDocument(m.id)
            return
        }
        c.openDocument(e.id)
        guard let s = c.tabs.first(where: { $0.id == e.id }) else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
            switch screen {
            case "pages": s.drawerOpen = true
            case "summary": s.summaryOpen = true
            case "menu": s.pageMenuOpen = true
            case "penbar": c.selectTool(.fountain); c.penBarOpen = true
            case "highlighter": c.toolbarStyle = .classic; c.selectTool(.highlighter)
            case "stickers": s.trayMode = "stickers"
            case "map": s.mapOpen = true
            case "split":
                if let n = c.library.first(where: { $0.isNote }) { c.openSplit(n.id) }
            case "search": s.searchQuery = "penicillin"; s.searchOpen = true; s.runSearch()
            case "shapes": c.selectTool(.ballpoint); c.shapeActive = true
            case "focus": c.focusMode = true
            case "night": c.nightPages = true
            default: break
            }
        }
    }
}
