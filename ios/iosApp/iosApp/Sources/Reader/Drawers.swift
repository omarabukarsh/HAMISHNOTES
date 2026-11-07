import SwiftUI
import PDFKit
import PencilKit
import PhotosUI

// ---------------------------------------------------------------- الصور المصغّرة للصفحات

/// صورة مصغّرة لصفحة مع حبرها.
struct PageThumb: View {
    @ObservedObject var session: DocSession
    let index: Int
    var width: CGFloat = 150
    @State private var image: UIImage? = nil

    var body: some View {
        let size = session.pageSize(index)
        let h = width * size.height / max(1, size.width)
        ZStack {
            Color.white
            if let img = image { Image(uiImage: img).resizable().scaledToFit() }
        }
        .frame(width: width, height: h)
        .clipShape(RoundedRectangle(cornerRadius: 6))
        .shadow(color: .black.opacity(0.12), radius: 3, y: 1)
        .task(id: "\(index)-\(session.pagesVersion)-\(session.inkVersion)-\(session.data.pages.count)") { render() }
    }

    private func render() {
        guard let page = session.pdf?.page(at: index), let pid = session.pageId(index) else { return }
        let size = session.pageSize(index)
        let scale: CGFloat = 2
        let tw = width * scale
        let k = tw / max(1, size.width)
        let base = page.thumbnail(of: CGSize(width: tw, height: size.height * k), for: .cropBox)
        let drawing = session.drawing(for: pid)
        let marks = session.marks(onPage: pid).filter { !$0.free }
        let img = UIGraphicsImageRenderer(size: CGSize(width: tw, height: size.height * k)).image { ctx in
            base.draw(in: CGRect(x: 0, y: 0, width: tw, height: size.height * k))
            let c = ctx.cgContext
            c.setBlendMode(.multiply)
            for m in marks {
                c.setFillColor(UIColor(argb: m.color).withAlphaComponent(0.42).cgColor)
                for r in m.rects { c.fill(CGRect(x: r.l * k, y: r.t * k, width: (r.r - r.l) * k, height: (r.b - r.t) * k)) }
            }
            c.setBlendMode(.normal)
            if !drawing.strokes.isEmpty {
                drawing.image(from: CGRect(origin: .zero, size: size), scale: k).draw(in: CGRect(x: 0, y: 0, width: tw, height: size.height * k))
            }
        }
        image = img
    }
}

// ---------------------------------------------------------------- درج الصفحات

struct PagesDrawer: View {
    @ObservedObject var session: DocSession
    @EnvironmentObject var app: AppController
    @ObservedObject var audio = AudioNotes.shared
    @State private var tab = 0
    @State private var cols = 1
    @State private var confirmDelete: Int? = nil
    @State private var outlineEdit: OutlineEntry? = nil

    var body: some View {
        let s = session
        VStack(spacing: 0) {
            HStack(spacing: 6) {
                ForEach(Array([L("الصفحات", "Pages"), L("الفهرس", "Outline"), L("العلامات", "Bookmarks"), L("التسجيلات", "Recordings")].enumerated()), id: \.offset) { k, label in
                    Button { tab = k } label: {
                        Text(label).font(Fonts.sans(13, tab == k ? .bold : .regular))
                            .foregroundColor(tab == k ? .white : Palette.textPrimary)
                            .padding(.horizontal, 10).padding(.vertical, 6)
                            .background(Capsule().fill(tab == k ? Palette.navy : Color.clear))
                    }
                }
                Spacer(minLength: 0)
                Menu {
                    Button(L("صور كبيرة (عمود واحد)", "Large thumbnails (1 column)")) { cols = 1 }
                    Button(L("صور متوسطة (عمودان)", "Medium thumbnails (2 columns)")) { cols = 2 }
                    Button(L("صور صغيرة (3 أعمدة)", "Small thumbnails (3 columns)")) { cols = 3 }
                    Button(L("إضافة صفحة في النهاية", "Add page at the end")) {
                        let c = Templates.defaultChoice()
                        s.addBlankPage(after: s.pageCount - 1, kind: c.kind, color: c.color)
                    }
                    Button(L("إغلاق الشريط", "Close sidebar")) { s.drawerOpen = false }
                } label: { PhIcon("ph_more", Palette.textSecondary, 20).frame(width: 32, height: 32) }
            }
            .padding(.horizontal, 12).padding(.vertical, 10)
            Divider()
            switch tab {
            case 0: pagesGrid((0..<s.pageCount).map { $0 })
            case 1: outline
            case 2:
                let marks = s.data.pages.indices.filter { s.data.bookmarks.contains(s.data.pages[$0].id) }
                if marks.isEmpty {
                    empty(L("لا توجد علامات بعد", "No bookmarks yet"),
                          L("ضع علامة على صفحة من زر العلامة في الشريط أو من قائمة الصفحة لتجدها هنا بسرعة.", "Bookmark a page from the toolbar or the page menu to find it here quickly."))
                } else { pagesGrid(marks) }
            default: recordings
            }
        }
        .frame(width: cols == 1 ? 230 : cols == 2 ? 300 : 360)
        .frame(maxHeight: .infinity)
        .background(Palette.drawerBg)
        .overlay(Rectangle().fill(Palette.divider).frame(width: 1), alignment: .trailing)
        .alert(L("حذف الصفحة \((confirmDelete ?? 0) + 1)؟", "Delete page \((confirmDelete ?? 0) + 1)?"), isPresented: Binding(get: { confirmDelete != nil }, set: { if !$0 { confirmDelete = nil } })) {
            Button(L("حذف", "Delete"), role: .destructive) { if let i = confirmDelete { s.deletePage(i) }; confirmDelete = nil }
            Button(L("إلغاء", "Cancel"), role: .cancel) { confirmDelete = nil }
        } message: { Text(L("ستُحذف الصفحة وكل ما كتبته عليها. يمكنك التراجع.", "The page and everything written on it will be deleted. You can undo.")) }
        .sheet(item: $outlineEdit) { e in
            NameSheet(title: L("إعادة تسمية العنوان", "Rename heading"), initial: e.title) { t in
                s.edit { d in if let i = d.outline.firstIndex(where: { $0.id == e.id }) { d.outline[i].title = t } }
            }
        }
    }

    private func pagesGrid(_ indices: [Int]) -> some View {
        let s = session
        let w: CGFloat = cols == 1 ? 150 : cols == 2 ? 118 : 94
        return ScrollViewReader { proxy in
            ScrollView {
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: cols), spacing: 14) {
                    ForEach(indices, id: \.self) { i in
                        let cur = i == s.currentPage
                        VStack(spacing: 4) {
                            ZStack(alignment: .topTrailing) {
                                PageThumb(session: s, index: i, width: w)
                                    .overlay(RoundedRectangle(cornerRadius: 6).stroke(cur ? Palette.gold : Color.clear, lineWidth: 3))
                                if s.isBookmarked(i) { PhIcon("ph_bookmark_fill", Palette.goldDeep, 18).padding(4) }
                            }
                            Text("\(i + 1)").font(Fonts.sans(13, cur ? .bold : .regular)).foregroundColor(cur ? Palette.navy : Palette.textSecondary)
                        }
                        .id(i)
                        .onTapGesture { s.goTo(page: i) }
                        .contextMenu { pageMenu(i) }
                    }
                    if tab == 0 {
                        Button {
                            let c = Templates.defaultChoice()
                            s.addBlankPage(after: s.pageCount - 1, kind: c.kind, color: c.color)
                        } label: {
                            VStack(spacing: 6) {
                                PhIcon("ph_add_page", Palette.navy, 26)
                                Text(L("إضافة صفحة", "Add page")).font(Fonts.sans(13)).foregroundColor(Palette.textPrimary)
                            }
                            .frame(width: w, height: w * 1.2)
                            .background(RoundedRectangle(cornerRadius: 8).stroke(Palette.divider, style: StrokeStyle(lineWidth: 1.5, dash: [6, 4])))
                        }
                    }
                }
                .padding(14)
            }
            .onAppear { proxy.scrollTo(s.currentPage, anchor: .center) }
        }
    }

    @ViewBuilder private func pageMenu(_ i: Int) -> some View {
        let s = session
        let n = s.pageCount
        Button { s.toggleBookmark(i) } label: { Text(s.isBookmarked(i) ? L("إزالة العلامة المرجعية", "Remove bookmark") : L("علامة مرجعية للصفحة", "Bookmark page")) }
        if i > 0 { Button(L("تحريك لأعلى", "Move up")) { s.movePage(i, to: i - 1) } }
        if i < n - 1 { Button(L("تحريك لأسفل", "Move down")) { s.movePage(i, to: i + 1) } }
        if i > 0 { Button(L("نقل إلى البداية", "Move to start")) { s.movePage(i, to: 0) } }
        if i < n - 1 { Button(L("نقل إلى النهاية", "Move to end")) { s.movePage(i, to: n - 1) } }
        Button(L("نسخ الصفحة", "Duplicate page")) { s.duplicatePage(i) }
        Button(L("تدوير الصفحة", "Rotate page")) { s.rotatePage(i) }
        Button(L("إضافة الصفحة إلى الفهرس", "Add page to outline")) {
            s.edit { $0.outline.append(OutlineEntry(title: L("الصفحة \(i + 1)", "Page \(i + 1)"), level: 0, page: i)) }
            tab = 1
        }
        if n > 1 { Button(role: .destructive) { confirmDelete = i } label: { Text(L("حذف الصفحة", "Delete page")) } }
    }

    private var outline: some View {
        let s = session
        let list = s.data.outline
        return Group {
            if list.isEmpty {
                VStack(spacing: 12) {
                    empty(L("لا توجد عناوين بعد", "No headings yet"), L("أضف عناوين لتنظيم مستندك والتنقل فيه بسرعة.", "Add headings to organize your document and move around quickly."))
                    NavyButton(label: L("عنوان للصفحة \(s.currentPage + 1)", "Heading for page \(s.currentPage + 1)")) {
                        s.edit { $0.outline.append(OutlineEntry(title: L("الصفحة \(s.currentPage + 1)", "Page \(s.currentPage + 1)"), level: 0, page: s.currentPage)) }
                    }
                }
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 2) {
                        ForEach(Array(list.enumerated()), id: \.element.id) { k, e in
                            Button { s.goTo(page: e.page) } label: {
                                HStack {
                                    Text(e.title).font(Fonts.sans(e.level == 0 ? 14 : 13, e.level == 0 ? .bold : .regular))
                                        .foregroundColor(Palette.textPrimary).lineLimit(2)
                                    Spacer()
                                    Text("\(e.page + 1)").font(Fonts.sans(12)).foregroundColor(Palette.textSecondary)
                                }
                                .padding(.leading, CGFloat(e.level) * 14 + 10).padding(.trailing, 10).padding(.vertical, 8)
                            }
                            .contextMenu {
                                Button(L("إعادة تسمية", "Rename")) { outlineEdit = e }
                                if e.level < 3 { Button(L("عنوان فرعي (إزاحة للداخل)", "Make subheading (indent)")) { s.edit { $0.outline[k].level += 1 } } }
                                if e.level > 0 { Button(L("عنوان رئيسي (إزاحة للخارج)", "Promote (outdent)")) { s.edit { $0.outline[k].level -= 1 } } }
                                if k > 0 { Button(L("تحريك لأعلى", "Move up")) { s.edit { $0.outline.swapAt(k, k - 1) } } }
                                if k < list.count - 1 { Button(L("تحريك لأسفل", "Move down")) { s.edit { $0.outline.swapAt(k, k + 1) } } }
                                Button(role: .destructive) { s.edit { $0.outline.remove(at: k) } } label: { Text(L("حذف", "Delete")) }
                            }
                        }
                        Button {
                            s.edit { $0.outline.append(OutlineEntry(title: L("الصفحة \(s.currentPage + 1)", "Page \(s.currentPage + 1)"), level: 0, page: s.currentPage)) }
                        } label: {
                            Text("+ " + L("عنوان للصفحة \(s.currentPage + 1)", "Heading for page \(s.currentPage + 1)")).font(Fonts.sans(13, .bold)).foregroundColor(Palette.navy).padding(10)
                        }
                    }
                    .padding(.vertical, 8)
                }
            }
        }
    }

    private var recordings: some View {
        let recs = session.data.recordings
        return Group {
            if recs.isEmpty {
                empty(L("تسجيلات المحاضرة", "Lecture recordings"), L("سجّل من زر الميكروفون في الشريط؛ كل ما تكتبه أثناء التسجيل يُربط بلحظته.", "Record from the microphone button; everything you write while recording is linked to that moment."))
            } else {
                ScrollView {
                    VStack(spacing: 6) {
                        ForEach(recs) { r in
                            Button { audio.play(session, r) } label: {
                                HStack {
                                    PhIcon("ph_play", Palette.navy, 18)
                                    VStack(alignment: .leading) {
                                        Text(Date(timeIntervalSince1970: Double(r.start) / 1000).formatted(date: .abbreviated, time: .shortened))
                                            .font(Fonts.sans(14)).foregroundColor(Palette.textPrimary)
                                        Text(mmss(r.durationMs)).font(Fonts.sans(12)).foregroundColor(Palette.textSecondary)
                                    }
                                    Spacer()
                                }
                                .padding(10).background(RoundedRectangle(cornerRadius: 10).fill(Color.white))
                            }
                            .contextMenu { Button(role: .destructive) { audio.delete(session, r) } label: { Text(L("حذف", "Delete")) } }
                        }
                    }
                    .padding(12)
                }
            }
        }
    }

    private func empty(_ t: String, _ b: String) -> some View {
        VStack(spacing: 6) {
            Text(t).font(Fonts.sans(17, .bold)).foregroundColor(Palette.textPrimary).multilineTextAlignment(.center)
            Text(b).font(Fonts.sans(14)).foregroundColor(Palette.textSecondary).multilineTextAlignment(.center).lineSpacing(3)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// ---------------------------------------------------------------- خيارات الصفحة

struct PageMenuDrawer: View {
    @ObservedObject var session: DocSession
    @EnvironmentObject var app: AppController
    @Binding var sheet: ReaderSheet?
    @State private var confirmClear = false
    @State private var confirmDelete = false
    @State private var changeTemplate = false

    var body: some View {
        let s = session
        let p = s.currentPage
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(L("الصفحة \(p + 1)", "Page \(p + 1)")).font(Fonts.display(18)).foregroundColor(Palette.navy)
                    Text((s.isBlank(p) ? Templates.labelOf(Templates.parse(s.data.pages[p].src).0) : L("صفحة من الملف", "Page from the file")) + L(" · من \(s.pageCount)", " · of \(s.pageCount)"))
                        .font(Fonts.sans(12)).foregroundColor(Palette.textSecondary)
                }
                Spacer()
                Button { s.pageMenuOpen = false } label: { PhIcon("ph_close", Palette.textSecondary, 18, L("إغلاق", "Close")).frame(width: 34, height: 34) }
            }
            .padding(16)
            ScrollView {
                VStack(alignment: .leading, spacing: 4) {
                    Group {
                    row("ph_bookmark", s.isBookmarked(p) ? L("إزالة العلامة المرجعية", "Remove bookmark") : L("علامة مرجعية للصفحة", "Bookmark page")) { s.toggleBookmark(p) }
                    row("ph_copy", L("تكرار الصفحة", "Duplicate page")) { s.duplicatePage(p); app.message(L("كُرّرت الصفحة \(p + 1)", "Page \(p + 1) duplicated")) }
                    row("ph_add_page", L("إضافة صفحة بعدها", "Add a page after")) { sheet = .addPage }
                    row("ph_list", L("إضافة الصفحة إلى الفهرس", "Add page to outline")) {
                        s.edit { $0.outline.append(OutlineEntry(title: L("الصفحة \(p + 1)", "Page \(p + 1)"), level: 0, page: p)) }
                        app.message(L("أُضيفت الصفحة إلى الفهرس", "Page added to the outline"))
                    }
                    if s.isBlank(p) { row("ph_grid", L("تغيير القالب", "Change template")) { changeTemplate = true } }
                    row("ph_mindmap", L("الخريطة الذهنية", "Mind map")) { s.mapOpen = true }
                    row("ph_text_scan", L("تحويل الكتابة إلى نص", "Handwriting to text")) { if Premium.shared.require(.toText) { sheet = .toText } }
                    }
                    Group {
                    label(L("نقل الصفحة", "Move page"))
                    HStack(spacing: 6) {
                        ChoiceChip(label: L("للأعلى", "Up"), selected: false) { s.movePage(p, to: p - 1); s.goTo(page: max(0, p - 1)) }
                        ChoiceChip(label: L("للأسفل", "Down"), selected: false) { s.movePage(p, to: p + 1); s.goTo(page: min(s.pageCount - 1, p + 1)) }
                        ChoiceChip(label: L("للبداية", "Start"), selected: false) { s.movePage(p, to: 0); s.goTo(page: 0) }
                        ChoiceChip(label: L("للنهاية", "End"), selected: false) { s.movePage(p, to: s.pageCount - 1); s.goTo(page: s.pageCount - 1) }
                    }
                    .padding(.horizontal, 12)
                    row("ph_hand_pointing", L("الانتقال إلى صفحة", "Go to page")) { sheet = .jump }
                    label(L("مسح أو حذف", "Clear or delete"))
                    row("ph_eraser", L("مسح الصفحة", "Clear page")) { confirmClear = true }
                    row("ph_trash", L("حذف الصفحة", "Delete page"), danger: true) { confirmDelete = true }
                    label(L("الإعدادات", "Settings"))
                    row("ph_sliders", L("كل الإعدادات وتخصيص الأدوات", "All settings and toolbar")) { app.settingsOpen = true }
                    }
                }
                .padding(.bottom, 20)
            }
        }
        .frame(width: 290)
        .frame(maxHeight: .infinity)
        .background(Palette.drawerBg)
        .overlay(Rectangle().fill(Palette.divider).frame(width: 1), alignment: .leading)
        .alert(L("مسح الصفحة \(p + 1)؟", "Clear page \(p + 1)?"), isPresented: $confirmClear) {
            Button(L("مسح", "Clear"), role: .destructive) { clearPage(p) }
            Button(L("إلغاء", "Cancel"), role: .cancel) {}
        } message: { Text(L("تُمسح الكتابة والتظليل والنصوص والصور من هذه الصفحة، وتبقى الصفحة نفسها. يمكنك التراجع.", "Handwriting, highlights, text and images are cleared from this page; the page itself stays. You can undo.")) }
        .alert(L("حذف الصفحة \(p + 1)؟", "Delete page \(p + 1)?"), isPresented: $confirmDelete) {
            Button(L("حذف", "Delete"), role: .destructive) { s.deletePage(p) }
            Button(L("إلغاء", "Cancel"), role: .cancel) {}
        } message: { Text(L("ستُحذف الصفحة وكل ما كتبته عليها.", "The page and everything written on it will be deleted.")) }
        .sheet(isPresented: $changeTemplate) {
            TemplatePickerSheet(mode: .defaultTemplate) { choice, _, _ in
                // صفحة جديدة بالقالب مكان الحالية، مع بقاء الكتابة عليها
                guard let pid = s.pageId(p), let doc = s.pdf,
                      let page = Templates.makePage(kind: choice.kind, bg: choice.color, size: s.pageSize(p)) else { return }
                doc.removePage(at: p)
                doc.insert(page, at: p)
                s.edit { d in if let i = d.pages.firstIndex(where: { $0.id == pid }) { d.pages[i].src = Templates.src(choice.kind, choice.color) } }
                s.reloadHook?()
            }
        }
    }

    private func clearPage(_ p: Int) {
        let s = session
        guard let pid = s.pageId(p) else { return }
        s.setInk(pid, PKDrawing())
        s.edit { d in
            d.marks.removeAll { $0.page == pid }
            d.boxes.removeAll { $0.page == pid }
            d.images.removeAll { $0.page == pid }
            d.notes.removeAll { $0.page == pid }
        }
    }

    private func label(_ t: String) -> some View {
        Text(t).font(Fonts.sans(12, .bold)).foregroundColor(Palette.textSecondary).padding(.horizontal, 16).padding(.top, 14).padding(.bottom, 4)
    }

    private func row(_ icon: String, _ t: String, danger: Bool = false, _ a: @escaping () -> Void) -> some View {
        Button(action: a) {
            HStack(spacing: 12) {
                PhIcon(icon, danger ? Palette.danger : Palette.navy, 19)
                Text(t).font(Fonts.sans(14)).foregroundColor(danger ? Palette.danger : Palette.textPrimary)
                Spacer()
            }
            .padding(.horizontal, 16).padding(.vertical, 10)
            .contentShape(Rectangle())
        }
    }
}

// ---------------------------------------------------------------- درج الملصقات والصور

struct SideTray: View {
    @ObservedObject var session: DocSession
    @EnvironmentObject var app: AppController
    @ObservedObject var store = StickerStore.shared
    let mode: String
    @State private var picks: [PhotosPickerItem] = []
    @State private var importing = false
    @State private var stickerPick: [PhotosPickerItem] = []

    var body: some View {
        let s = session
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(mode == "photos" ? L("الصور", "Photos") : L("الملصقات", "Stickers")).font(Fonts.display(17)).foregroundColor(Palette.navy)
                Spacer()
                Button { s.trayMode = nil } label: { PhIcon("ph_close", Palette.textSecondary, 16).frame(width: 30, height: 30) }
            }
            if mode == "photos" {
                PhotosPicker(selection: $picks, maxSelectionCount: 10, matching: .images) {
                    trayButton("ph_image", L("من مكتبة الصور", "From the photo library"))
                }
                Button { importing = true } label: { trayButton("ph_folder", L("من الملفات", "From Files")) }
                Text(L("تُضاف الصورة إلى الصفحة الحالية. انقر عليها بإصبعك لتحريكها أو تغيير حجمها أو حذفها.", "The picture is added to the current page. Tap it with your finger to move, resize or delete it."))
                    .font(Fonts.sans(12)).foregroundColor(Palette.textSecondary)
            } else {
                ScrollView {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 4), spacing: 8) {
                        ForEach(CoverStickers, id: \.self) { k in
                            Button { s.insertSticker(k) } label: {
                                StickerIcon(key: k, size: 44).frame(width: 56, height: 56)
                                    .background(RoundedRectangle(cornerRadius: 12).fill(Color.white))
                            }
                        }
                        ForEach(["⭐️", "✅", "❗️", "❓", "💡", "📌", "🔥", "❤️", "👍", "📝", "⚠️", "🎯"], id: \.self) { e in
                            Button { s.insertSticker(e) } label: {
                                Text(e).font(.system(size: 34)).frame(width: 56, height: 56)
                                    .background(RoundedRectangle(cornerRadius: 12).fill(Color.white))
                            }
                        }
                        ForEach(store.mine, id: \.self) { f in
                            Button { s.insertSticker("mine:" + f) } label: {
                                Group {
                                    if let img = UIImage(contentsOfFile: Repository.shared.stickersDir.appendingPathComponent(f).path) {
                                        Image(uiImage: img).resizable().scaledToFit()
                                    }
                                }
                                .frame(width: 48, height: 48).frame(width: 56, height: 56)
                                .background(RoundedRectangle(cornerRadius: 12).fill(Color.white))
                            }
                            .contextMenu { Button(role: .destructive) { StickerStore.shared.delete(f) } label: { Text(L("حذف", "Delete")) } }
                        }
                    }
                }
                PhotosPicker(selection: $stickerPick, maxSelectionCount: 1, matching: .images) {
                    trayButton("ph_plus", L("ملصق من صورة", "Sticker from a photo"))
                }
            }
            Spacer(minLength: 0)
        }
        .padding(16)
        .frame(width: 290)
        .frame(maxHeight: 520)
        .background(RoundedRectangle(cornerRadius: 22).fill(Palette.drawerBg))
        .shadow(color: .black.opacity(0.18), radius: 14, y: 6)
        .padding(12)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
        .onChange(of: picks) { items in
            for it in items {
                Task {
                    if let d = try? await it.loadTransferable(type: Data.self), let img = UIImage(data: d) {
                        await MainActor.run { s.insertImage(img) }
                    }
                }
            }
            picks = []
        }
        .onChange(of: stickerPick) { items in
            for it in items {
                Task {
                    if let d = try? await it.loadTransferable(type: Data.self), let img = UIImage(data: d) {
                        await MainActor.run { StickerStore.shared.add(img) }
                    }
                }
            }
            stickerPick = []
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.image], allowsMultipleSelection: true) { r in
            if case .success(let urls) = r {
                for u in urls {
                    let sc = u.startAccessingSecurityScopedResource()
                    if let d = try? Data(contentsOf: u), let img = UIImage(data: d) { s.insertImage(img) }
                    if sc { u.stopAccessingSecurityScopedResource() }
                }
            }
        }
    }

    private func trayButton(_ icon: String, _ t: String) -> some View {
        HStack(spacing: 10) {
            PhIcon(icon, Palette.navy, 20)
            Text(t).font(Fonts.sans(14, .medium)).foregroundColor(Palette.textPrimary)
            Spacer()
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 12).fill(Color.white))
    }
}

/// «ملصقاتي»: صور شفافة مربعة في مجلد التطبيق، تبقى في كل المستندات.
final class StickerStore: ObservableObject {
    static let shared = StickerStore()
    @Published var mine: [String] = []

    private init() {
        let dir = Repository.shared.stickersDir
        mine = ((try? FileManager.default.contentsOfDirectory(atPath: dir.path)) ?? []).filter { $0.hasSuffix(".png") }.sorted().reversed()
    }

    func add(_ src: UIImage) {
        let side = min(src.size.width, src.size.height)
        let out = UIGraphicsImageRenderer(size: CGSize(width: 512, height: 512)).image { _ in
            UIBezierPath(roundedRect: CGRect(x: 0, y: 0, width: 512, height: 512), cornerRadius: 64).addClip()
            let k = 512 / side
            src.draw(in: CGRect(x: -(src.size.width - side) / 2 * k, y: -(src.size.height - side) / 2 * k, width: src.size.width * k, height: src.size.height * k))
        }
        let name = UUID().uuidString + ".png"
        if let d = out.pngData() {
            try? d.write(to: Repository.shared.stickersDir.appendingPathComponent(name))
            mine.insert(name, at: 0)
            AppController.shared.message(L("أُضيف إلى «ملصقاتي»", "Added to “My stickers”"))
        }
    }

    func delete(_ f: String) {
        try? FileManager.default.removeItem(at: Repository.shared.stickersDir.appendingPathComponent(f))
        mine.removeAll { $0 == f }
    }
}
