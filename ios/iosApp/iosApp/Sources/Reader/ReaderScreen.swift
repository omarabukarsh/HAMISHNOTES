import SwiftUI
import PDFKit
import PhotosUI
import UniformTypeIdentifiers

enum ReaderSheet: Identifiable {
    case jump, addPage, link, audio, split, export, colorPicker, toText
    var id: String { "\(self)" }
}

struct ReaderScreen: View {
    @ObservedObject var session: DocSession
    @EnvironmentObject var app: AppController

    var body: some View {
        if let f = session.failed {
            VStack(spacing: 8) {
                Text(L("تعذر فتح «\(session.title)»", "Could not open “\(session.title)”")).font(Fonts.sans(18, .bold)).foregroundColor(Palette.textPrimary)
                Text(f).font(Fonts.sans(14)).foregroundColor(Palette.textSecondary)
                HStack(spacing: 10) {
                    NavyButton(label: L("إغلاق التبويب", "Close tab")) { app.closeTab(session.id) }
                    NavyButton(label: L("المكتبة", "Library"), filled: false) { app.activate(nil) }
                }
                .padding(.top, 8)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Palette.desk)
        } else if session.isMap {
            MindMapView(session: session, standalone: true)
        } else {
            ReaderContent(session: session)
        }
    }
}

private struct ReaderContent: View {
    @ObservedObject var session: DocSession
    @EnvironmentObject var app: AppController
    @ObservedObject var audio = AudioNotes.shared
    @State private var sheet: ReaderSheet? = nil
    @State private var pdfPicker = false
    @State private var pdfAfter = 0

    var body: some View {
        let s = session
        ZStack {
            VStack(spacing: 0) {
                if !app.focusMode {
                    VStack(spacing: 0) {
                        ReaderTopBar(session: s, sheet: $sheet)
                        if app.toolbarStyle == .penBar && app.penBarOpen && !app.lassoActive && !app.shapeActive && app.tool.isWriting {
                            PenBar(sheet: $sheet).transition(.move(edge: .top).combined(with: .opacity))
                        }
                        if app.shapeActive && !app.lassoActive { ShapeBar().transition(.move(edge: .top).combined(with: .opacity)) }
                        if app.stickyActive { StickyBar().transition(.move(edge: .top).combined(with: .opacity)) }
                    }
                    .scaleEffect(app.toolbarScale, anchor: .top)
                    .frame(height: nil)
                    .animation(Motion.enter, value: app.penBarOpen)
                    .animation(Motion.enter, value: app.shapeActive)
                    .animation(Motion.enter, value: app.stickyActive)
                    if s.searchOpen { ReaderSearchBar(session: s) }
                    if s.ocrRunning {
                        VStack(spacing: 0) {
                            Text(L("جارٍ قراءة النص… \(s.ocrProgress) / \(s.pageCount)", "Reading text… \(s.ocrProgress) / \(s.pageCount)"))
                                .font(Fonts.sans(12)).foregroundColor(Palette.navy).padding(.horizontal, 14).padding(.vertical, 6)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            ProgressView(value: Double(s.ocrProgress), total: Double(max(1, s.pageCount))).tint(Palette.goldDeep)
                        }
                        .background(Palette.goldSoft)
                    }
                }
                ZStack {
                    PDFReaderView(session: s)
                    if !s.summaryOpen && !s.pageMenuOpen {
                        PageScrubber(session: s).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                    }
                    if !s.drawerOpen && app.toolVisible("readtimer") {
                        ReaderTimerBadge(docId: s.id).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
                    }
                    // الأدراج
                    HStack(spacing: 0) {
                        if s.drawerOpen { PagesDrawer(session: s).transition(.move(edge: .leading)) }
                        Spacer(minLength: 0)
                        if s.summaryOpen { SummaryDrawer(session: s).transition(.move(edge: .trailing)) }
                        if s.pageMenuOpen { PageMenuDrawer(session: s, sheet: $sheet).transition(.move(edge: .trailing)) }
                    }
                    .animation(Motion.enter, value: s.drawerOpen)
                    .animation(Motion.enter, value: s.summaryOpen)
                    .animation(Motion.enter, value: s.pageMenuOpen)
                    if let mode = s.trayMode { SideTray(session: s, mode: mode) }
                }
                if s.zoomBox != nil && !app.focusMode { ZoomPanel(session: s) }
            }
            if app.focusMode {
                HStack(spacing: 4) {
                    PomodoroControl()
                    Button { app.focusMode = false } label: { PhIcon("ph_focus_exit", Palette.textPrimary, 20, L("الخروج من وضع التركيز", "Exit focus mode")).frame(width: 40, height: 40) }
                }
                .padding(.horizontal, 8).padding(.vertical, 4)
                .background(Capsule().fill(Color.white))
                .overlay(Capsule().stroke(Palette.divider, lineWidth: 1))
                .shadow(color: .black.opacity(0.15), radius: 6, y: 3)
                .padding(14)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
            }
            if let rec = audio.playing, rec.0 == s.id {
                AudioPlayerBar(docId: s.id, rec: rec.1).frame(maxHeight: .infinity, alignment: .bottom).padding(16)
            }
            if let back = app.linkBack {
                HStack(spacing: 2) {
                    Button {
                        app.linkBack = nil
                        app.openPage(back.0, back.1)
                    } label: {
                        Text(L("↩ رجوع إلى \(app.entry(back.0)?.name ?? "") ص \(back.1 + 1)", "↩ Back to \(app.entry(back.0)?.name ?? "") p. \(back.1 + 1)"))
                            .font(Fonts.sans(14)).foregroundColor(Palette.navy).lineLimit(1).padding(.horizontal, 12).padding(.vertical, 10)
                    }
                    Button { app.linkBack = nil } label: { PhIcon("ph_close", Palette.textSecondary, 16).frame(width: 36, height: 36) }
                }
                .background(Capsule().fill(Color.white))
                .shadow(color: .black.opacity(0.15), radius: 6, y: 3)
                .padding(16)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
            }
        }
        .background(Palette.desk)
        .sheet(item: $sheet) { sh in
            Group {
                switch sh {
                case .jump: PageJumpSheet(session: s)
                case .addPage:
                    TemplatePickerSheet(mode: .addPage, onPdf: { atEnd in
                        pdfAfter = atEnd ? s.pageCount - 1 : s.currentPage
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { pdfPicker = true }
                    }) { choice, _, atEnd in
                        s.addBlankPage(after: atEnd ? s.pageCount - 1 : s.currentPage, kind: choice.kind, color: choice.color, size: choice.points)
                    }
                case .link: LinkSheet(session: s)
                case .audio: AudioSheet(session: s)
                case .split: SplitPickerSheet(session: s)
                case .export: ShareExportSheet(docIds: [s.id])
                case .colorPicker: ColorPickerSheet()
                case .toText: HandwritingSheet(session: s)
                }
            }
            .environmentObject(app)
            .environment(\.layoutDirection, .leftToRight)
        }
        .sheet(item: Binding(get: { s.editingBox }, set: { s.editingBox = $0 })) { b in
            TextBoxSheet(session: s, box: b).environment(\.layoutDirection, .leftToRight)
        }
        .sheet(item: Binding(get: { s.editingNote }, set: { s.editingNote = $0 })) { n in
            NoteSheet(session: s, note: n).environment(\.layoutDirection, .leftToRight)
        }
        .bigSheet(isPresented: Binding(get: { s.mapOpen }, set: { s.mapOpen = $0 }), maxWidth: 1400) {
            MindMapView(session: s, standalone: false).environmentObject(app)
                .environment(\.layoutDirection, .leftToRight)
        }
        .fileImporter(isPresented: $pdfPicker, allowedContentTypes: [.pdf]) { r in
            if case .success(let u) = r { s.addPdfPages(from: u, after: pdfAfter) }
        }
    }
}

// ---------------------------------------------------------------- شريط الأدوات العلوي

private struct ReaderTopBar: View {
    @ObservedObject var session: DocSession
    @EnvironmentObject var app: AppController
    @ObservedObject var pm = Premium.shared
    @ObservedObject var audio = AudioNotes.shared
    @Binding var sheet: ReaderSheet?
    @State private var settingsFor: String? = nil
    @State private var lassoMenu = false
    @State private var autoFont = false

    var body: some View {
        ZStack {
            HStack {
                // زر درج الصفحات
                circleButton(on: s.drawerOpen, bg: Palette.navySoft) { s.drawerOpen.toggle() } content: {
                    PhIcon(s.drawerOpen ? "ph_pages_fill" : "ph_pages", tintFor(s.drawerOpen), 22)
                }
                Spacer()
                // زر درج الملخصات مع عدد التمييزات
                if app.summariesOn {
                    ZStack(alignment: .topTrailing) {
                        circleButton(on: s.summaryOpen, bg: Palette.goldSoft) {
                            if s.summaryOpen || pm.require(.summaries) {
                                s.summaryOpen.toggle()
                                if s.summaryOpen { s.pageMenuOpen = false; s.trayMode = nil }
                            }
                        } content: {
                            ZStack(alignment: .topTrailing) {
                                PhIcon(s.summaryOpen ? "ph_summary_fill" : "ph_summary", s.summaryOpen ? Palette.goldDeep : Palette.textSecondary, 22)
                                if !pm.active { PremiumBadge(size: 13).offset(x: 8, y: -8) }
                            }
                        }
                        let n = s.data.marks.count
                        if n > 0 {
                            Text(n > 99 ? "99" : "\(n)").font(Fonts.sans(9, .bold)).foregroundColor(.white)
                                .frame(width: 18, height: 18).background(Circle().fill(Palette.goldDeep))
                        }
                    }
                }
            }
            // الأدوات في المنتصف
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 0) {
                    navGroup
                    coreGroup
                    insertGroupA
                    insertGroupB
                    viewGroup
                }
                .padding(.horizontal, 10).padding(.vertical, 4)
            }
            .fixedSize(horizontal: false, vertical: true)
            .background(Capsule().fill(Color.white))
            .overlay(Capsule().stroke(Palette.divider, lineWidth: 1))
            .clipShape(Capsule())
            .shadow(color: Palette.ink.opacity(0.18), radius: 8, y: 3)
            .padding(.horizontal, 58)
            .frame(maxWidth: .infinity)
            .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 10).padding(.vertical, 8)
    }

    private var s: DocSession { session }
    private var cur: Int { session.currentPage }
    private func v(_ id: String) -> Bool { app.toolVisible(id) }
    private var dim: Color { Palette.textSecondary.opacity(0.35) }
    @ViewBuilder private var navGroup: some View {
                    if v("nav") {
                        TBtn(action: { s.goTo(page: cur - 1) }) { PhIcon("ph_caret_up", Palette.textPrimary, 20) }
                        Button { sheet = .jump } label: {
                            Text("\(cur + 1) / \(s.pageCount)").font(Fonts.sans(14, .medium)).foregroundColor(Palette.textPrimary)
                                .padding(.horizontal, 12).padding(.vertical, 8)
                                .background(RoundedRectangle(cornerRadius: 10).fill(Palette.background))
                        }
                        TBtn(action: { s.goTo(page: cur + 1) }) { PhIcon("ph_caret_down", Palette.textPrimary, 20) }
                    }
                    if v("bookmark") {
                        let marked = s.isBookmarked(cur)
                        TBtn(selected: marked, action: { s.toggleBookmark(cur) }) {
                            PhIcon(marked ? "ph_bookmark_fill" : "ph_bookmark", marked ? Palette.goldDeep : Palette.textSecondary)
                        }
                    }
                    if v("timer") { PomodoroControl(showStartLabel: false) }
                    if v("nav") || v("bookmark") || v("timer") { ToolbarSep() }
    }
    @ViewBuilder private var coreGroup: some View {
                    // الأدوات الأساسية
                    if app.toolbarStyle == .penBar {
                        let writing = app.tool.isWriting && !app.lassoActive && !app.shapeActive
                        let shown = writing ? app.tool : app.lastWriting
                        TBtn(selected: writing, action: {
                            if !writing { app.selectTool(shown); app.penBarOpen = true } else { app.penBarOpen.toggle() }
                        }) { ToolGlyph(tool: shown, selected: writing) }
                    } else {
                        let penSel = app.tool.isPen && !app.lassoActive && !app.shapeActive
                        let shownPen = app.tool.isPen ? app.tool : app.lastPen
                        toolSlot(shownPen, id: "pen", selected: penSel)
                        toolSlot(.highlighter, id: "hl", selected: app.tool == .highlighter && !app.lassoActive)
                        if app.tool == .highlighter && v("snap") {
                            TBtn(selected: app.highlightSnap && pm.active, locked: !pm.active, action: { if pm.require(.snap) { app.highlightSnap.toggle() } }) {
                                PhIcon("ph_snap", tintFor(app.highlightSnap))
                            }
                        }
                    }
                    toolSlot(.eraser, id: "er", selected: app.tool == .eraser && !app.lassoActive)
                    toolSlot(.laser, id: "ls", selected: app.tool == .laser && !app.lassoActive)
                    if v("lasso") {
                        TBtn(selected: app.lassoActive, action: {
                            if app.lassoActive { app.lassoActive = false } else {
                                app.lassoActive = true
                                app.shapeActive = false
                                app.textActive = false
                                app.stickyActive = false
                                app.message(L("أحِط بالقلم حول ما تريد تحديده، ثم اسحبه أو انقر عليه للنسخ والحذف", "Draw around what you want to select, then drag it or tap it to copy or delete"))
                            }
                        }) { PhIcon("ph_lasso", tintFor(app.lassoActive)) }
                    }
                    if v("shapetool") {
                        TBtn(selected: app.shapeActive && !app.lassoActive, action: {
                            if app.shapeActive && !app.lassoActive { app.shapeActive = false } else {
                                app.lassoActive = false
                                if !app.tool.isWriting { app.selectTool(app.lastPen) }
                                app.shapeActive = true
                            }
                        }) { PhIcon("ph_shapes", tintFor(app.shapeActive && !app.lassoActive)) }
                    }
                    if v("autofont") {
                        TBtn(locked: !pm.active, action: { if pm.require(.toText) { sheet = .toText } }) {
                            Text("Aa").font(Fonts.sans(16, .bold)).foregroundColor(Palette.textSecondary)
                        }
                    }
                    TBtn(selected: app.fingerWrites, action: {
                        app.fingerWrites.toggle()
                        app.message(app.fingerWrites ? L("صار الإصبع يكتب · اسحب بإصبعين للتمرير", "Your finger now writes · drag with two fingers to scroll")
                                    : L("القلم يكتب، والإصبع للتمرير", "The pen writes; your finger scrolls"))
                    }) {
                        PhIcon(app.fingerWrites ? "ph_hand_pointing_fill" : "ph_hand_pointing", app.fingerWrites ? Palette.goldDeep : Palette.textSecondary)
                    }
    }
    @ViewBuilder private var insertGroupA: some View {
                    if ["image", "stickers", "text", "sticky", "zoomwin", "addpage", "ocr", "search", "export"].contains(where: v) { ToolbarSep() }
                    if v("image") {
                        TBtn(selected: s.trayMode == "photos", action: { s.trayMode = s.trayMode == "photos" ? nil : "photos"; s.summaryOpen = false }) {
                            PhIcon("ph_image", tintFor(s.trayMode == "photos"))
                        }
                    }
                    if v("stickers") {
                        TBtn(selected: s.trayMode == "stickers", action: { s.trayMode = s.trayMode == "stickers" ? nil : "stickers"; s.summaryOpen = false }) {
                            PhIcon("ph_sticker", tintFor(s.trayMode == "stickers"))
                        }
                    }
                    if v("text") {
                        TBtn(selected: app.textActive, action: {
                            if app.textActive { app.textActive = false } else {
                                app.lassoActive = false; app.shapeActive = false; app.stickyActive = false
                                app.textActive = true
                                app.message(L("انقر على الصفحة حيث تريد الكتابة", "Tap the page where you want to type"))
                            }
                        }) { PhIcon("ph_text", tintFor(app.textActive)) }
                    }
                    if v("sticky") {
                        TBtn(selected: app.stickyActive, action: {
                            if app.stickyActive { app.stickyActive = false } else {
                                app.lassoActive = false; app.shapeActive = false; app.textActive = false
                                app.stickyActive = true
                                app.message(L("اختر لونًا ثم انقر على الصفحة", "Pick a color and tap the page"))
                            }
                        }) { PhIcon("ph_note", tintFor(app.stickyActive)) }
                    }
                    if v("zoomwin") {
                        TBtn(selected: s.zoomBox != nil, action: {
                            if s.zoomBox != nil { s.zoomBox = nil } else {
                                let size = s.pageSize(s.currentPage)
                                let w = size.width * 0.4
                                s.zoomBox = ZoomBox(page: s.currentPage, x: size.width * (I18n.english ? 0.04 : 0.56), y: size.width * 0.12, w: w, h: w * 0.3)
                            }
                        }) { PhIcon("ph_zoom_write", tintFor(s.zoomBox != nil)) }
                    }
    }
    @ViewBuilder private var insertGroupB: some View {
                    if v("link") { TBtn(action: { sheet = .link }) { PhIcon("ph_link") } }
                    if v("split") {
                        let on = app.splitId != nil
                        TBtn(selected: on, locked: !pm.active, action: { if on { app.closeSplit() } else if pm.require(.split) { sheet = .split } }) {
                            PhIcon("ph_split", tintFor(on))
                        }
                    }
                    if v("record") {
                        let recHere = audio.recordingDoc == s.id
                        TBtn(selected: recHere, locked: !pm.active && !recHere, action: { if recHere || pm.require(.record) { sheet = .audio } }) {
                            if recHere { Text(mmss(audio.elapsed)).font(Fonts.sans(12, .bold)).foregroundColor(Palette.danger) }
                            else { PhIcon("ph_mic") }
                        }
                    }
                    if v("addpage") { TBtn(action: { sheet = .addPage }) { PhIcon("ph_add_page") } }
                    if v("ocr") {
                        TBtn(selected: s.ocrRunning, locked: !pm.active, action: { if s.ocrRunning { s.stopOcr() } else if pm.require(.ocr) { s.startOcr() } }) {
                            PhIcon("ph_scan", tintFor(s.ocrRunning))
                        }
                    }
                    if v("search") {
                        TBtn(selected: s.searchOpen, action: {
                            s.searchOpen.toggle()
                            if !s.searchOpen { s.searchHits = []; s.searchQuery = "" }
                        }) { PhIcon("ph_search", tintFor(s.searchOpen)) }
                    }
                    if v("export") { TBtn(action: { s.flush(); sheet = .export }) { PhIcon("ph_export") } }
    }
    @ViewBuilder private var viewGroup: some View {
                    if v("undo") {
                        ToolbarSep()
                        TBtn(enabled: !s.undoStack.isEmpty, action: { s.undo() }) { PhIcon("ph_undo", s.undoStack.isEmpty ? dim : Palette.textPrimary) }
                        TBtn(enabled: !s.redoStack.isEmpty, action: { s.redo() }) { PhIcon("ph_redo", s.redoStack.isEmpty ? dim : Palette.textPrimary) }
                    }
                    ToolbarSep()
                    if v("zoom") {
                        TBtn(action: { s.zoomHook?(1 / 1.25) }) { PhIcon("ph_zoom_out", Palette.textPrimary) }
                        Button { s.fitHook?() } label: {
                            Text("\(s.scalePercent)%").font(Fonts.sans(14)).foregroundColor(Palette.textPrimary).padding(.horizontal, 8).padding(.vertical, 8)
                        }
                        TBtn(action: { s.zoomHook?(1.25) }) { PhIcon("ph_zoom_in", Palette.textPrimary) }
                    }
                    if v("fit") { TBtn(action: { s.fitHook?() }) { PhIcon("ph_fit", Palette.textPrimary) } }
                    if v("night") { TBtn(selected: app.nightPages, action: { app.nightPages.toggle() }) { PhIcon("ph_moon", tintFor(app.nightPages)) } }
                    if v("focus") {
                        TBtn(action: {
                            s.drawerOpen = false; s.summaryOpen = false
                            withAnimation(Motion.enter) { app.focusMode = true }
                            app.message(L("وضع التركيز: اضغط أيقونة الخروج للعودة", "Focus mode: tap the exit icon to return"))
                        }) { PhIcon("ph_eye", Palette.textPrimary) }
                    }
                    TBtn(selected: s.pageMenuOpen, action: {
                        s.pageMenuOpen.toggle()
                        if s.pageMenuOpen { s.summaryOpen = false }
                    }) { PhIcon("ph_more", tintFor(s.pageMenuOpen)) }
    }

    private func circleButton<C: View>(on: Bool, bg: Color, action: @escaping () -> Void, @ViewBuilder content: () -> C) -> some View {
        Button(action: action) {
            content()
                .frame(width: 46, height: 46)
                .background(Circle().fill(on ? bg : Color.white))
                .shadow(color: .black.opacity(0.14), radius: 5, y: 2)
        }
        .buttonStyle(PressScale())
    }

    /// زر أداة: نقرة تختارها، ونقرة ثانية على الأداة المختارة تفتح إعداداتها.
    private func toolSlot(_ t: Tool, id: String, selected: Bool) -> some View {
        TBtn(selected: selected, action: {
            if selected { settingsFor = id } else { app.selectToolWithHint(t) }
        }) { ToolGlyph(tool: t, selected: selected) }
        .popover(isPresented: Binding(get: { settingsFor == id && selected }, set: { if !$0 { settingsFor = nil } })) {
            ToolSettingsPanel(onCustomColor: { settingsFor = nil; sheet = .colorPicker })
                .environmentObject(app)
                .environment(\.layoutDirection, .leftToRight)
        }
    }
}

/// أيقونة الأداة: مملوءة عند الاختيار، مع نقطة بلونها.
struct ToolGlyph: View {
    @EnvironmentObject var app: AppController
    let tool: Tool
    let selected: Bool
    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            PhIcon(selected ? filledIcon(tool.icon) : tool.icon, tintFor(selected), 22)
            if tool != .eraser {
                Circle().fill(Color(hex: app.colorFor(tool))).frame(width: 8, height: 8)
                    .overlay(Circle().stroke(Color.white, lineWidth: 1.5)).offset(x: 3, y: 2)
            }
        }
    }
}

// ---------------------------------------------------------------- شريط الأقلام

private let InkPens: [Tool] = [.fountain, .ballpoint, .brush]

private struct PenBar: View {
    @EnvironmentObject var app: AppController
    @Binding var sheet: ReaderSheet?
    @State private var more = false

    var body: some View {
        let t = app.tool
        let inkPen = InkPens.contains(t) ? t : (InkPens.contains(app.lastPen) ? app.lastPen : .fountain)
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 0) {
                ForEach([inkPen, .pencil, .highlighter], id: \.self) { pt in
                    let sel = pt == t
                    TBtn(selected: sel, action: { if sel { more = true } else { app.selectTool(pt) } }) { ToolGlyph(tool: pt, selected: sel) }
                        .popover(isPresented: Binding(get: { more && sel }, set: { if !$0 { more = false } })) {
                            ToolSettingsPanel(onCustomColor: { more = false; sheet = .colorPicker })
                                .environmentObject(app)
                                .environment(\.layoutDirection, .leftToRight)
                        }
                }
                ToolbarSep()
                ForEach(app.presets) { p in
                    let active = app.tool == p.tool && app.colorFor(p.tool) == p.color && app.sizeFor(p.tool) == p.size
                    Button { app.applyPreset(p) } label: {
                        ZStack(alignment: .bottom) {
                            PhIcon(filledIcon(p.tool.icon), Color(hex: p.color), 20).frame(width: 40, height: 40)
                            RoundedRectangle(cornerRadius: 2).fill(Color(hex: p.color)).frame(width: CGFloat(6 + p.size * 2), height: 3).padding(.bottom, 4)
                        }
                        .background(RoundedRectangle(cornerRadius: 12).fill(active ? Palette.navySoft : Color.clear))
                    }
                    .contextMenu { Button(role: .destructive) { app.presets.removeAll { $0.id == p.id } } label: { Text(L("حذف القلم المفضل", "Remove favorite pen")) } }
                }
                TBtn(action: { app.addPreset() }) { PhIcon("ph_star_plus", Palette.goldDeep, 20, L("حفظ كقلم مفضل", "Save as favorite pen")) }
                ToolbarSep()
                ColorDots(onCustom: { sheet = .colorPicker })
                ToolbarSep()
                SizeSlots(tool: t)
                ToolbarSep()
                TBtn(action: { app.penBarOpen = false }) { PhIcon("ph_caret_up", Palette.textSecondary, 20, L("إخفاء شريط الأقلام", "Hide pen bar")) }
            }
            .padding(.horizontal, 10).padding(.vertical, 4)
        }
        .fixedSize(horizontal: false, vertical: true)
        .background(Capsule().fill(Color.white))
        .overlay(Capsule().stroke(Palette.divider, lineWidth: 1))
        .clipShape(Capsule())
        .shadow(color: Palette.ink.opacity(0.15), radius: 6, y: 2)
        .padding(.horizontal, 64).padding(.bottom, 6)
        .frame(maxWidth: .infinity)
    }
}

/// ألوان الأداة الحالية ولون مخصص.
struct ColorDots: View {
    @EnvironmentObject var app: AppController
    let onCustom: () -> Void
    var body: some View {
        let current = app.colorFor(app.tool)
        HStack(spacing: 0) {
            ForEach(app.paletteFor(app.tool) + app.customColors, id: \.self) { col in
                let sel = col == current
                Button { app.setColor(col) } label: {
                    Circle().fill(Color(hex: col)).frame(width: sel ? 26 : 22, height: sel ? 26 : 22)
                        .overlay(Circle().stroke(sel ? Palette.navy : Palette.divider, lineWidth: sel ? 2.5 : 1))
                        .frame(width: 34, height: 34)
                }
            }
            Button(action: onCustom) { PhIcon("ph_plus", Palette.textSecondary, 18, L("لون مخصص", "Custom color")).frame(width: 34, height: 34) }
        }
    }
}

/// ثلاثة أحجام قابلة للتعديل لكل أداة (نفس SizeSlots في أندرويد): خط بسماكة الحجم،
/// ونقرة على الحجم المختار تفتح شريطًا لتغييره.
struct SizeSlots: View {
    @EnvironmentObject var app: AppController
    let tool: Tool
    @State private var editing: Int? = nil

    static func slots(_ t: Tool) -> [Int] {
        if let s = UserDefaults.standard.array(forKey: "slots_" + t.rawValue) as? [Int], s.count == 3 { return s }
        return t == .highlighter ? [2, 4, 7] : [1, 3, 6]
    }

    static func setSlot(_ t: Tool, _ i: Int, _ v: Int) {
        var l = slots(t)
        l[i] = max(0, min(Sizes.count - 1, v))
        UserDefaults.standard.set(l, forKey: "slots_" + t.rawValue)
    }

    var body: some View {
        let slots = Self.slots(tool)
        let cur = app.sizeFor(tool)
        HStack(spacing: 0) {
            ForEach(0..<3, id: \.self) { i in
                let sz = slots[i]
                let sel = sz == cur
                Button { if sel { editing = i } else { app.setSize(sz) } } label: {
                    Capsule().fill(sel ? Palette.navy : Palette.textSecondary)
                        .frame(width: 26, height: 2 + CGFloat(sz) * 1.3)
                        .frame(width: 46, height: 40)
                        .background(RoundedRectangle(cornerRadius: 12).fill(sel ? Palette.navySoft : Color.clear))
                }
                .popover(isPresented: Binding(get: { editing == i }, set: { if !$0 { editing = nil } })) {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text(L("الحجم \(i + 1)", "Size \(i + 1)")).font(Fonts.sans(15, .bold)).foregroundColor(Palette.textPrimary)
                            Spacer()
                            Text(String(format: "%.2f mm", Double(Sizes.width(tool, Self.slots(tool)[i]) / 600 * 210))).font(Fonts.sans(12)).foregroundColor(Palette.textSecondary)
                        }
                        Slider(value: Binding(get: { Double(Self.slots(tool)[i]) }, set: { v in
                            Self.setSlot(tool, i, Int(v.rounded()))
                            app.setSize(Int(v.rounded()))
                        }), in: 0...Double(Sizes.count - 1), step: 1).tint(Palette.navy)
                    }
                    .padding(.horizontal, 16).padding(.vertical, 12)
                    .frame(width: 260)
                    .environment(\.layoutDirection, .leftToRight)
                }
            }
        }
    }
}

/// إعدادات الأداة الحالية: النوع، اللون، الحجم، وخيارات خاصة.
struct ToolSettingsPanel: View {
    @EnvironmentObject var app: AppController
    let onCustomColor: () -> Void

    var body: some View {
        let t = app.tool
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                PhIcon(filledIcon(t.icon), Palette.navy, 18)
                Text(t.title).font(Fonts.sans(14, .bold)).foregroundColor(Palette.textPrimary)
            }
            let penTypes: [Tool] = app.toolbarStyle == .penBar ? InkPens : InkPens + [.pencil]
            if penTypes.contains(t) {
                HStack(spacing: 6) {
                    ForEach(penTypes, id: \.self) { pt in
                        let sel = pt == t
                        Button { app.selectTool(pt) } label: {
                            VStack(spacing: 2) {
                                PhIcon(sel ? filledIcon(pt.icon) : pt.icon, sel ? .white : Palette.textPrimary, 18)
                                Text(pt.title).font(Fonts.sans(10)).foregroundColor(sel ? .white : Palette.textPrimary).lineLimit(1)
                            }
                            .frame(maxWidth: .infinity).padding(.vertical, 6)
                            .background(RoundedRectangle(cornerRadius: 10).fill(sel ? Palette.navy : Palette.background))
                        }
                    }
                }
            }
            if t != .eraser {
                Text(L("اللون", "Color")).font(Fonts.sans(12)).foregroundColor(Palette.textSecondary)
                LazyVGrid(columns: Array(repeating: GridItem(.fixed(30), spacing: 6), count: 7), spacing: 6) {
                    ForEach(app.paletteFor(t) + app.customColors, id: \.self) { col in
                        let sel = col == app.colorFor(t)
                        Button { app.setColor(col) } label: {
                            Circle().fill(Color(hex: col)).frame(width: 26, height: 26)
                                .overlay(Circle().stroke(sel ? Palette.navy : Palette.divider, lineWidth: sel ? 2.5 : 1))
                        }
                    }
                    Button(action: onCustomColor) { PhIcon("ph_plus", Palette.textSecondary, 18) }
                }
            }
            Text(L("الحجم", "Size")).font(Fonts.sans(12)).foregroundColor(Palette.textSecondary)
            HStack {
                Slider(value: Binding(get: { Double(app.sizeFor(t)) }, set: { app.setSize(Int($0.rounded())) }), in: 0...Double(Sizes.count - 1), step: 1)
                    .tint(Palette.navy)
                Circle().fill(t == .eraser ? Palette.textSecondary.opacity(0.3) : Color(hex: app.colorFor(t)))
                    .frame(width: min(34, max(4, Sizes.width(t, app.sizeFor(t)) * 0.6)), height: min(34, max(4, Sizes.width(t, app.sizeFor(t)) * 0.6)))
                    .frame(width: 36, height: 36)
            }
            if t == .eraser {
                HStack(spacing: 6) {
                    ChoiceChip(label: L("الخط كاملًا", "Whole stroke"), selected: app.eraserMode == .stroke) { app.eraserMode = .stroke }
                    ChoiceChip(label: L("ما تلمسه فقط", "Only what it touches"), selected: app.eraserMode == .area) { app.eraserMode = .area }
                }
            }
            if t == .laser {
                HStack(spacing: 6) {
                    ChoiceChip(label: L("نقطة", "Dot"), selected: app.laserMode == .dot) { app.laserMode = .dot }
                    ChoiceChip(label: L("خط يتلاشى", "Fading line"), selected: app.laserMode == .line) { app.laserMode = .line }
                }
            }
            if t == .highlighter {
                Toggle(isOn: Binding(get: { app.highlightSnap }, set: { v in if !v || Premium.shared.require(.snap) { app.highlightSnap = v } })) {
                    Text(L("التقاط النص", "Text snap")).font(Fonts.sans(13))
                }.tint(Palette.navy)
                Toggle(isOn: $app.highlightToSummary) { Text(L("إضافة إلى الملخص", "Add to summary")).font(Fonts.sans(13)) }.tint(Palette.navy)
            }
        }
        .padding(14)
        .frame(width: 280)
        .background(Color.white)
    }
}

/// اختيار لون مخصص.
struct ColorPickerSheet: View {
    @EnvironmentObject var app: AppController
    @State private var color: Color = .black
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        SheetScaffold(title: L("لون مخصص", "Custom color")) {
            VStack(spacing: 18) {
                ColorPicker(L("اللون", "Color"), selection: $color, supportsOpacity: false).font(Fonts.sans(15))
                Circle().fill(color).frame(width: 80, height: 80).overlay(Circle().stroke(Palette.divider))
                HStack {
                    Spacer()
                    NavyButton(label: L("إلغاء", "Cancel"), filled: false) { dismiss() }
                    NavyButton(label: L("اختيار", "Choose")) {
                        let c = color.argb | 0xFF000000
                        app.addCustomColor(c)
                        app.setColor(c)
                        dismiss()
                    }
                }
            }
            .padding(.horizontal, 22)
        }
        .onAppear { color = Color(hex: app.colorFor(app.tool)) }
        .presentationDetents([.height(320)])
    }
}

// ---------------------------------------------------------------- شريط الأشكال والملاحظات

private struct ShapeBar: View {
    @EnvironmentObject var app: AppController
    var body: some View {
        let kinds: [(ShapeKind, String)] = [(.line, "ph_shape_line"), (.elbow, "ph_shape_elbow"), (.curve, "ph_shape_curve"), (.rect, "ph_shape_rect"),
                                            (.circle, "ph_shape_circle"), (.ellipse, "ph_shape_ellipse"), (.triangle, "ph_shape_triangle"), (.diamond, "ph_shape_diamond")]
        HStack(spacing: 0) {
            ForEach(kinds, id: \.0) { item in
                TBtn(selected: app.shapeKind == item.0, action: { app.shapeKind = item.0 }) { PhIcon(item.1, tintFor(app.shapeKind == item.0)) }
            }
            ToolbarSep()
            ColorDots(onCustom: {})
            ToolbarSep()
            TBtn(action: { app.shapeActive = false }) { PhIcon("ph_close", Palette.textSecondary, 18) }
        }
        .padding(.horizontal, 10).padding(.vertical, 4)
        .background(Capsule().fill(Color.white))
        .overlay(Capsule().stroke(Palette.divider, lineWidth: 1))
        .shadow(color: Palette.ink.opacity(0.15), radius: 6, y: 2)
        .padding(.bottom, 6)
    }
}

private struct StickyBar: View {
    @EnvironmentObject var app: AppController
    var body: some View {
        HStack(spacing: 6) {
            Text(L("لون الملاحظة", "Note color")).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary).padding(.horizontal, 6)
            ForEach(app.stickyPalette, id: \.self) { c in
                Button { app.stickyColor = c } label: {
                    RoundedRectangle(cornerRadius: 6).fill(Color(hex: c)).frame(width: 30, height: 30)
                        .overlay(RoundedRectangle(cornerRadius: 6).stroke(app.stickyColor == c ? Palette.navy : Palette.divider, lineWidth: app.stickyColor == c ? 2.5 : 1))
                }
            }
            ToolbarSep()
            TBtn(action: { app.stickyActive = false }) { PhIcon("ph_close", Palette.textSecondary, 18) }
        }
        .padding(.horizontal, 10).padding(.vertical, 4)
        .background(Capsule().fill(Color.white))
        .overlay(Capsule().stroke(Palette.divider, lineWidth: 1))
        .shadow(color: Palette.ink.opacity(0.15), radius: 6, y: 2)
        .padding(.bottom, 6)
    }
}

// ---------------------------------------------------------------- البحث داخل المستند

private struct ReaderSearchBar: View {
    @ObservedObject var session: DocSession
    var body: some View {
        let s = session
        HStack(spacing: 10) {
            PhIcon("ph_search", Palette.textSecondary, 18)
            TextField(L("ابحث في المستند", "Search in document"), text: Binding(get: { s.searchQuery }, set: { s.searchQuery = $0 }))
                .font(Fonts.sans(15)).onSubmit { s.runSearch() }
            if !s.searchHits.isEmpty {
                Text("\(s.searchIndex + 1) / \(s.searchHits.count)").font(Fonts.sans(13)).foregroundColor(Palette.textSecondary)
                Button { step(-1) } label: { PhIcon("ph_caret_up", Palette.textPrimary, 18).frame(width: 34, height: 34) }
                Button { step(1) } label: { PhIcon("ph_caret_down", Palette.textPrimary, 18).frame(width: 34, height: 34) }
            } else if !s.searchQuery.isEmpty {
                let o = s.ocrHits(s.searchQuery)
                if let first = o.first {
                    Button { s.goTo(page: first.0) } label: {
                        Text(L("في الصفحة المقروءة \(first.0 + 1)", "On read page \(first.0 + 1)")).font(Fonts.sans(13)).foregroundColor(Palette.navy)
                    }
                }
            }
            NavyButton(label: L("بحث", "Search")) { s.runSearch() }
            Button {
                s.searchOpen = false
                s.searchHits = []
                s.searchQuery = ""
            } label: { PhIcon("ph_close", Palette.textSecondary, 18).frame(width: 34, height: 34) }
        }
        .padding(.horizontal, 16).padding(.vertical, 6)
        .background(Color.white)
        .overlay(Rectangle().fill(Palette.divider).frame(height: 1), alignment: .bottom)
    }

    private func step(_ d: Int) {
        let s = session
        guard !s.searchHits.isEmpty, let doc = s.pdf else { return }
        s.searchIndex = (s.searchIndex + d + s.searchHits.count) % s.searchHits.count
        if let p = s.searchHits[s.searchIndex].pages.first { s.goTo(page: doc.index(for: p)) }
    }
}

// ---------------------------------------------------------------- نوافذ القارئ

private struct PageJumpSheet: View {
    @ObservedObject var session: DocSession
    @State private var text = ""
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        SheetScaffold(title: L("الانتقال إلى صفحة", "Go to page")) {
            VStack(alignment: .leading, spacing: 14) {
                FieldBox(placeholder: L("رقم الصفحة (1 - \(session.pageCount))", "Page number (1 - \(session.pageCount))"), text: $text)
                    .keyboardType(.numberPad)
                HStack {
                    Spacer()
                    NavyButton(label: L("إلغاء", "Cancel"), filled: false) { dismiss() }
                    NavyButton(label: L("انتقال", "Go")) {
                        if let n = Int(text.trimmingCharacters(in: .whitespaces)) { session.goTo(page: n - 1) }
                        dismiss()
                    }
                }
            }
            .padding(.horizontal, 22)
        }
        .presentationDetents([.height(220)])
    }
}

/// مربع نص: كتابة أو تعديل أو حذف.
struct TextBoxSheet: View {
    @ObservedObject var session: DocSession
    let box: TextBox
    @State private var text = ""
    @State private var size: CGFloat = 16
    @State private var color: UInt32 = 0xFF1A2230
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let isNew = !session.data.boxes.contains { $0.id == box.id }
        SheetScaffold(title: isNew ? L("إضافة نص", "Add text") : L("تعديل النص", "Edit text")) {
            VStack(alignment: .leading, spacing: 12) {
                FieldBox(placeholder: L("اكتب هنا…", "Write here…"), text: $text, multiline: true)
                HStack {
                    Text(L("الحجم", "Size")).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary)
                    Slider(value: $size, in: 8...48).tint(Palette.navy)
                }
                HStack(spacing: 6) {
                    ForEach(AppController.shared.penPalette, id: \.self) { c in
                        Button { color = c } label: {
                            Circle().fill(Color(hex: c)).frame(width: 26, height: 26)
                                .overlay(Circle().stroke(color == c ? Palette.navy : Palette.divider, lineWidth: color == c ? 2.5 : 1))
                        }
                    }
                }
                HStack {
                    if !isNew { NavyButton(label: L("حذف", "Delete"), filled: false, danger: true) { session.deleteBox(box.id); dismiss() } }
                    Spacer()
                    NavyButton(label: L("إلغاء", "Cancel"), filled: false) { dismiss() }
                    NavyButton(label: isNew ? L("إضافة", "Add") : L("حفظ", "Save")) {
                        var b = box
                        b.text = text
                        b.size = size
                        b.color = color
                        if text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { if !isNew { session.deleteBox(b.id) } }
                        else { session.upsertBox(b) }
                        dismiss()
                    }
                }
            }
            .padding(.horizontal, 22)
        }
        .onAppear { text = box.text; size = box.size; color = box.color }
    }
}

/// ملاحظة لاصقة: النص واللون والطيّ.
struct NoteSheet: View {
    @ObservedObject var session: DocSession
    let note: StickyNote
    @State private var text = ""
    @State private var color: UInt32 = 0
    @State private var collapsed = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        SheetScaffold(title: L("ملاحظة لاصقة", "Sticky note")) {
            VStack(alignment: .leading, spacing: 12) {
                TextField(L("اكتب ملاحظتك…", "Write your note…"), text: $text, axis: .vertical)
                    .lineLimit(4...10).font(Fonts.sans(16))
                    .padding(14).background(RoundedRectangle(cornerRadius: 6).fill(Color(hex: color)))
                HStack(spacing: 6) {
                    ForEach(AppController.shared.stickyPalette, id: \.self) { c in
                        Button { color = c } label: {
                            RoundedRectangle(cornerRadius: 6).fill(Color(hex: c)).frame(width: 30, height: 30)
                                .overlay(RoundedRectangle(cornerRadius: 6).stroke(color == c ? Palette.navy : Palette.divider, lineWidth: color == c ? 2.5 : 1))
                        }
                    }
                }
                Toggle(isOn: $collapsed) { Text(L("مطوية (أيقونة صغيرة على الصفحة)", "Collapsed (a small icon on the page)")).font(Fonts.sans(13)) }.tint(Palette.navy)
                HStack {
                    NavyButton(label: L("حذف", "Delete"), filled: false, danger: true) { session.deleteNote(note.id); dismiss() }
                    Spacer()
                    NavyButton(label: L("حفظ", "Save")) {
                        var n = note
                        n.text = text
                        n.color = color
                        n.collapsed = collapsed
                        session.upsertNote(n)
                        dismiss()
                    }
                }
            }
            .padding(.horizontal, 22)
        }
        .onAppear { text = note.text; color = note.color; collapsed = note.collapsed }
    }
}

/// رابط إلى مستند آخر وصفحة فيه.
private struct LinkSheet: View {
    @EnvironmentObject var app: AppController
    @ObservedObject var session: DocSession
    @State private var target: String? = nil
    @State private var page = "1"
    @State private var label = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        SheetScaffold(title: L("رابط إلى مستند", "Link to a document")) {
            VStack(alignment: .leading, spacing: 12) {
                FieldBox(placeholder: L("نص الرابط", "Link text"), text: $label)
                ScrollView {
                    LazyVStack(spacing: 4) {
                        ForEach(app.library.filter { !$0.isMap }) { e in
                            Button { target = e.id; if label.isEmpty { label = e.name } } label: {
                                HStack {
                                    Text(e.name).font(Fonts.sans(14, target == e.id ? .bold : .regular)).foregroundColor(Palette.textPrimary)
                                    Spacer()
                                    if target == e.id { PhIcon("ph_check", Palette.navy, 16) }
                                }
                                .padding(10)
                                .background(RoundedRectangle(cornerRadius: 10).fill(target == e.id ? Palette.navySoft : Color.white))
                            }
                        }
                    }
                }
                .frame(maxHeight: 280)
                FieldBox(placeholder: L("الصفحة", "Page"), text: $page).keyboardType(.numberPad)
                HStack {
                    Spacer()
                    NavyButton(label: L("إلغاء", "Cancel"), filled: false) { dismiss() }
                    NavyButton(label: L("إضافة الرابط", "Add link")) {
                        guard let t = target, let pid = session.pageId(session.currentPage) else { return }
                        let size = session.pageSize(session.currentPage)
                        session.upsertBox(TextBox(id: UUID().uuidString, page: pid, x: size.width * 0.1, y: size.height * 0.1, w: size.width * 0.5,
                                                  text: label.isEmpty ? (app.entry(t)?.name ?? "") : label, size: max(12, size.width * 0.022),
                                                  linkDoc: t, linkPage: max(0, (Int(page) ?? 1) - 1)))
                        dismiss()
                    }
                }
            }
            .padding(.horizontal, 22).padding(.bottom, 16)
        }
    }
}

/// اختيار المستند الثاني لعرضين جنبًا إلى جنب.
private struct SplitPickerSheet: View {
    @EnvironmentObject var app: AppController
    @ObservedObject var session: DocSession
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        SheetScaffold(title: L("مستند بجانب هذا", "A document beside this one")) {
            ScrollView {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 120), spacing: 16)], spacing: 16) {
                    ForEach(app.library.filter { $0.id != session.id && !$0.isMap }) { e in
                        Button {
                            dismiss()
                            app.openSplit(e.id)
                        } label: {
                            VStack(spacing: 6) {
                                NotebookCover(entry: e, compact: true)
                                Text(e.name).font(Fonts.sans(13, .bold)).foregroundColor(Palette.textPrimary).lineLimit(1)
                            }
                        }
                    }
                }
                .padding(22)
            }
        }
    }
}
