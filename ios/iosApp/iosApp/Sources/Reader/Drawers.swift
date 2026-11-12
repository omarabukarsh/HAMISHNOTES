import SwiftUI
import PDFKit
import PencilKit
import PhotosUI
import Photos

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
        .task(id: "\(index)-\(session.pagesVersion)-\(session.inkVersion)-\(session.data.pages.count)") { await render() }
    }

    /// الرسم في الخلفية حتى لا تتقطع الكتابة والدرج مفتوح.
    private func render() async {
        guard let page = session.pdf?.page(at: index), let pid = session.pageId(index) else { return }
        let size = session.pageSize(index)
        let scale: CGFloat = 2
        let tw = width * scale
        let k = tw / max(1, size.width)
        let drawing = session.drawing(for: pid)
        let marks = session.marks(onPage: pid).filter { !$0.free }
        let th = size.height * k
        let img = await Task.detached(priority: .utility) { () -> UIImage in
            let base = page.thumbnail(of: CGSize(width: tw, height: th), for: .cropBox)
            let fmt = UIGraphicsImageRendererFormat()
            fmt.scale = 1
            return UIGraphicsImageRenderer(size: CGSize(width: tw, height: th), format: fmt).image { ctx in
                base.draw(in: CGRect(x: 0, y: 0, width: tw, height: th))
                let c = ctx.cgContext
                c.setBlendMode(.multiply)
                for m in marks {
                    c.setFillColor(markColor(m.color, strong: m.level >= 2).cgColor)
                    for r in m.rects { c.fill(r.scaled(tw)) }
                }
                c.setBlendMode(.normal)
                if !drawing.strokes.isEmpty {
                    drawing.lightImage(from: CGRect(origin: .zero, size: size), scale: k).draw(in: CGRect(x: 0, y: 0, width: tw, height: th))
                }
            }
        }.value
        if !Task.isCancelled { image = img }
    }
}

// ---------------------------------------------------------------- درج الصفحات

/// درج الصفحات (نفس أندرويد): رأس فيه زر التحديد، وأربعة تبويبات (الصفحات، الفهرس، العلامات، التسجيلات)،
/// وقائمة خيارات؛ ثم شبكة صور الصفحات برقم الصفحة وزر «⋯» وعلامة في الزاوية، وبطاقة «إضافة صفحة».
struct PagesDrawer: View {
    static var width: CGFloat { 300 * Fonts.base }
    @ObservedObject var session: DocSession
    @EnvironmentObject var app: AppController
    @ObservedObject var audio = AudioNotes.shared
    @State private var tab = 0
    @State private var cols = 2
    @State private var selecting = false
    @State private var picked: Set<Int> = []
    @State private var confirmDelete: Int? = nil
    @State private var confirmDeleteMany = false
    @State private var addPage = false
    @State private var outlineEdit: OutlineEntry? = nil

    var body: some View {
        let s = session
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                Button { if tab == 0 { selecting.toggle(); picked.removeAll() } } label: {
                    PhIcon("ph_check_circle", selecting ? Palette.navy : Palette.textSecondary, 22)
                        .frame(width: 38, height: 38).background(Circle().fill(selecting ? Palette.navySoft : Color.clear))
                }
                Spacer(minLength: 0)
                HStack(spacing: 2) {
                    ForEach(Array(["ph_grid", "ph_list", "ph_bookmark", "ph_waveform"].enumerated()), id: \.offset) { k, icon in
                        Button {
                            tab = k
                            if k != 0 { selecting = false; picked.removeAll() }
                        } label: {
                            PhIcon(icon, tab == k ? Palette.navy : Palette.textSecondary, 20)
                                .frame(width: 46, height: 34)
                                .background(RoundedRectangle(cornerRadius: 9).fill(tab == k ? Color.white : Color.clear))
                        }
                    }
                }
                .padding(3)
                .background(RoundedRectangle(cornerRadius: 12).fill(Palette.textSecondary.opacity(0.12)))
                Spacer(minLength: 0)
                Menu {
                    Button(L("صور كبيرة (عمود واحد)", "Large thumbnails (1 column)")) { cols = 1 }
                    Button(L("صور متوسطة (عمودان)", "Medium thumbnails (2 columns)")) { cols = 2 }
                    Button(L("صور صغيرة (3 أعمدة)", "Small thumbnails (3 columns)")) { cols = 3 }
                    Button(L("إضافة صفحة في النهاية", "Add page at the end")) { addPage = true }
                    Button(L("إغلاق الشريط", "Close sidebar")) { s.drawerOpen = false }
                } label: { PhIcon("ph_more", Palette.textSecondary, 22).frame(width: 38, height: 38) }
            }
            .padding(8)
            Rectangle().fill(Palette.divider).frame(height: 1)
            ZStack(alignment: .bottom) {
                switch tab {
                case 1: outline
                case 3: recordings
                default:
                    let shown = tab == 2 ? s.data.pages.indices.filter { s.data.bookmarks.contains(s.data.pages[$0].id) } : Array(0..<s.pageCount)
                    if tab == 2 && shown.isEmpty {
                        EmptyPanel(icon: "ph_bookmark", title: L("لا توجد علامات بعد", "No bookmarks yet"),
                                   message: L("ضع علامة على صفحة من زاوية صورتها أو من قائمة الصفحة لتجدها هنا بسرعة.", "Mark a page from its thumbnail corner or the page menu to find it here quickly."))
                    } else {
                        grid(shown)
                    }
                }
                if selecting {
                    HStack(spacing: 4) {
                        Text(L("\(picked.count) محددة", "\(picked.count) selected")).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary).padding(.horizontal, 8)
                        Button {
                            let all = picked.allSatisfy { s.isBookmarked($0) }
                            picked.forEach { p in if s.isBookmarked(p) == all { s.toggleBookmark(p) } }
                        } label: { PhIcon("ph_bookmark", Palette.navy, 20).frame(width: 40, height: 40) }
                        .disabled(picked.isEmpty)
                        Button { confirmDeleteMany = true } label: { PhIcon("ph_trash", Palette.danger, 20).frame(width: 40, height: 40) }
                            .disabled(picked.isEmpty || picked.count >= s.pageCount)
                        Button(L("تم", "Done")) { selecting = false; picked.removeAll() }.font(Fonts.sans(14, .bold)).foregroundColor(Palette.navy).padding(.horizontal, 10)
                    }
                    .padding(.horizontal, 6)
                    .background(RoundedRectangle(cornerRadius: 16).fill(Color.white).shadow(color: .black.opacity(0.15), radius: 6, y: 2))
                    .padding(12)
                }
            }
        }
        .frame(width: PagesDrawer.width)
        .frame(maxHeight: .infinity)
        .background(Palette.drawerBg.shadow(color: .black.opacity(0.16), radius: 10))
        .alert(L("حذف الصفحة \((confirmDelete ?? 0) + 1)؟", "Delete page \((confirmDelete ?? 0) + 1)?"), isPresented: Binding(get: { confirmDelete != nil }, set: { if !$0 { confirmDelete = nil } })) {
            Button(L("حذف", "Delete"), role: .destructive) { if let i = confirmDelete { s.deletePage(i) }; confirmDelete = nil }
            Button(L("إلغاء", "Cancel"), role: .cancel) { confirmDelete = nil }
        } message: { Text(L("ستُحذف الصفحة وكل ما كتبته عليها. يمكنك التراجع.", "The page and everything written on it will be deleted. You can undo.")) }
        .alert(L("حذف \(picked.count) صفحة؟", "Delete \(picked.count) pages?"), isPresented: $confirmDeleteMany) {
            Button(L("حذف", "Delete"), role: .destructive) {
                for i in picked.sorted(by: >) { s.deletePage(i) }
                picked.removeAll()
                selecting = false
            }
            Button(L("إلغاء", "Cancel"), role: .cancel) {}
        } message: { Text(L("ستُحذف الصفحات المحددة وكل ما كتبته عليها.", "The selected pages and everything written on them will be deleted.")) }
        .bigSheet(isPresented: $addPage, width: 0.92, height: 0.9, maxHeight: 900, maxWidth: 1040) {
            TemplatePickerSheet(mode: .addPage) { choice, _, atEnd in
                s.addBlankPage(after: atEnd ? s.pageCount - 1 : s.currentPage, kind: choice.kind, color: choice.color, size: choice.points)
            }
        }
        .dialogSheet(item: $outlineEdit) { e in
            NameSheet(title: L("إعادة تسمية العنوان", "Rename heading"), initial: e.title) { t in
                s.edit { d in if let i = d.outline.firstIndex(where: { $0.id == e.id }) { d.outline[i].title = t } }
            }
        }
    }

    private func grid(_ indices: [Int]) -> some View {
        let s = session
        let w = (300 - 28 - CGFloat(cols - 1) * 14) / CGFloat(cols)
        return ScrollViewReader { proxy in
            ScrollView {
                LazyVGrid(columns: Array(repeating: GridItem(.fixed(w), spacing: 14), count: cols), spacing: 14) {
                    ForEach(indices, id: \.self) { i in
                        let cur = i == s.currentPage
                        let isPicked = picked.contains(i)
                        VStack(spacing: 2) {
                            ZStack(alignment: .topTrailing) {
                                PageThumb(session: s, index: i, width: w)
                                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(selecting && isPicked ? Palette.navy : (!selecting && cur ? Color(hex: 0xFF3B82F6) : Palette.divider),
                                                                                      lineWidth: (selecting && isPicked) || (!selecting && cur) ? 3 : 1))
                                Button { if !selecting { s.toggleBookmark(i) } } label: {
                                    PhIcon(s.isBookmarked(i) ? "ph_bookmark_fill" : "ph_bookmark", s.isBookmarked(i) ? Palette.gold : Palette.textSecondary.opacity(0.55), 18)
                                        .frame(width: 32, height: 32)
                                }
                                if selecting {
                                    ZStack {
                                        Circle().fill(isPicked ? Palette.navy : Color.white)
                                        Circle().stroke(isPicked ? Palette.navy : Palette.textSecondary, lineWidth: 1.5)
                                        if isPicked { PhIcon("ph_check", .white, 15) }
                                    }
                                    .frame(width: 24, height: 24).padding(6)
                                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                                }
                            }
                            .onTapGesture {
                                if selecting { if isPicked { picked.remove(i) } else { picked.insert(i) } } else { s.goTo(page: i) }
                            }
                            HStack {
                                Text("\(i + 1)").font(Fonts.sans(13, cur ? .bold : .regular)).foregroundColor(cur ? Palette.navy : Palette.textSecondary).padding(.leading, 2)
                                Spacer()
                                if !selecting {
                                    Menu { pageMenu(i) } label: { PhIcon("ph_more", Palette.textSecondary, 18).frame(width: 36, height: 28) }
                                }
                            }
                        }
                        .id(i)
                    }
                    if tab == 0 && !selecting {
                        Button { addPage = true } label: {
                            VStack(spacing: 6) {
                                PhIcon("ph_plus", Palette.textPrimary, 26)
                                Text(L("إضافة صفحة", "Add page")).font(Fonts.sans(14)).foregroundColor(Palette.textPrimary)
                            }
                            .frame(width: w, height: w * 1.414)
                            .background(RoundedRectangle(cornerRadius: 10).fill(Palette.textSecondary.opacity(0.10)))
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Palette.divider, lineWidth: 1))
                        }
                    }
                }
                .padding(.horizontal, 14).padding(.top, 14).padding(.bottom, 80)
            }
            .onAppear { proxy.scrollTo(max(0, s.currentPage - 2), anchor: .top) }
            .onChange(of: s.currentPage) { _, p in withAnimation { proxy.scrollTo(max(0, p - 2), anchor: .top) } }
        }
    }

    @ViewBuilder private func pageMenu(_ i: Int) -> some View {
        let s = session
        let n = s.pageCount
        if i > 0 { Button(L("تحريك لأعلى", "Move up")) { s.movePage(i, to: i - 1) } }
        if i < n - 1 { Button(L("تحريك لأسفل", "Move down")) { s.movePage(i, to: i + 1) } }
        if i > 0 { Button(L("نقل إلى البداية", "Move to start")) { s.movePage(i, to: 0) } }
        if i < n - 1 { Button(L("نقل إلى النهاية", "Move to end")) { s.movePage(i, to: n - 1) } }
        Button(L("نسخ الصفحة", "Duplicate page")) { s.duplicatePage(i) }
        if n > 1 { Button(role: .destructive) { confirmDelete = i } label: { Text(L("حذف الصفحة", "Delete page")) } }
    }

    private var outline: some View {
        let s = session
        let list = s.data.outline
        let orig: [OutlineEntry] = s.pdf.map { Repository.outline(of: $0) } ?? []
        let sig: ([OutlineEntry]) -> [String] = { $0.map { "\($0.title)|\($0.level)|\($0.page)" } }
        let changed = !orig.isEmpty && sig(orig) != sig(list)
        return VStack(spacing: 0) {
            outlineList(s, list)
            // فهرس الملف الأصلي يعود إن عُدّل (كما في أندرويد)
            if changed {
                Button { s.edit { $0.outline = orig } } label: {
                    Text(L("استعادة الأصل", "Restore original")).font(Fonts.sans(13)).foregroundColor(Palette.navy).padding(10)
                }
            }
        }
    }

    private func outlineList(_ s: DocSession, _ list: [OutlineEntry]) -> some View {
        Group {
            if list.isEmpty {
                VStack(spacing: 0) {
                    EmptyPanel(icon: "ph_list", title: L("لا توجد عناوين بعد", "No headings yet"),
                               message: L("أضف عناوين لتنظيم مستندك والتنقل فيه بسرعة.", "Add headings to organize your document and move around quickly."))
                    Button { s.edit { $0.outline.append(OutlineEntry(title: L("الصفحة \(s.currentPage + 1)", "Page \(s.currentPage + 1)"), level: 0, page: s.currentPage)) } } label: {
                        Text("+ " + L("عنوان للصفحة \(s.currentPage + 1)", "Heading for page \(s.currentPage + 1)")).font(Fonts.sans(14)).foregroundColor(Palette.navy).padding(14)
                    }
                }
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        ForEach(Array(list.enumerated()), id: \.element.id) { k, e in
                            let cur = e.page == s.currentPage
                            HStack {
                                Text(e.title).font(Fonts.sans(e.level == 0 ? 14 : 13, e.level == 0 || cur ? .bold : .regular))
                                    .foregroundColor(cur ? Palette.navy : Palette.textPrimary).lineLimit(2)
                                Spacer()
                                if e.page >= 0 { Text("\(e.page + 1)").font(Fonts.sans(12)).foregroundColor(Palette.textSecondary) }
                                Menu {
                                    Button(L("إعادة تسمية", "Rename")) { outlineEdit = e }
                                    if e.level < 3 { Button(L("عنوان فرعي (إزاحة للداخل)", "Make subheading (indent)")) { s.edit { $0.outline[k].level += 1 } } }
                                    if e.level > 0 { Button(L("عنوان رئيسي (إزاحة للخارج)", "Promote (outdent)")) { s.edit { $0.outline[k].level -= 1 } } }
                                    if k > 0 { Button(L("تحريك لأعلى", "Move up")) { s.edit { $0.outline.swapAt(k, k - 1) } } }
                                    if k < list.count - 1 { Button(L("تحريك لأسفل", "Move down")) { s.edit { $0.outline.swapAt(k, k + 1) } } }
                                    Button(role: .destructive) { s.edit { $0.outline.remove(at: k) } } label: { Text(L("حذف", "Delete")) }
                                } label: { PhIcon("ph_more", Palette.textSecondary, 18).frame(width: 30, height: 30) }
                            }
                            .padding(.leading, CGFloat(e.level) * 16 + 14).padding(.trailing, 6).padding(.vertical, 6)
                            .background(cur ? Palette.navySoft : Color.clear)
                            .contentShape(Rectangle())
                            .onTapGesture { s.goTo(page: e.page) }
                        }
                        Button { s.edit { $0.outline.append(OutlineEntry(title: L("الصفحة \(s.currentPage + 1)", "Page \(s.currentPage + 1)"), level: 0, page: s.currentPage)) } } label: {
                            Text("+ " + L("عنوان للصفحة \(s.currentPage + 1)", "Heading for page \(s.currentPage + 1)")).font(Fonts.sans(14)).foregroundColor(Palette.navy).padding(14)
                        }
                    }
                    .padding(.vertical, 8)
                }
            }
        }
    }

    private var recordings: some View {
        let recs = session.data.recordings.sorted { $0.start > $1.start }
        return Group {
            if recs.isEmpty {
                EmptyPanel(icon: "ph_waveform", title: L("تسجيلات المحاضرة", "Lecture recordings"),
                           message: L("سجّل من زر الميكروفون في الشريط؛ كل ما تكتبه أثناء التسجيل يُربط بلحظته.", "Record with the microphone button in the toolbar; everything you write is linked to its moment."))
            } else {
                ScrollView {
                    VStack(spacing: 0) {
                        ForEach(recs) { r in
                            let playing = audio.playing?.1.id == r.id
                            HStack(spacing: 12) {
                                PhIcon(playing ? "ph_stop" : "ph_play", Palette.navy, 20)
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(Date(timeIntervalSince1970: Double(r.start) / 1000).formatted(.dateTime.day().month(.defaultDigits).hour().minute()))
                                        .font(Fonts.sans(14)).foregroundColor(Palette.textPrimary)
                                    Text(mmss(r.durationMs)).font(Fonts.sans(12)).foregroundColor(Palette.textSecondary)
                                }
                                Spacer()
                                Button { audio.delete(session, r) } label: { PhIcon("ph_trash", Palette.textSecondary, 18).frame(width: 34, height: 34) }
                            }
                            .padding(.horizontal, 14).padding(.vertical, 10)
                            .contentShape(Rectangle())
                            .onTapGesture { if playing { audio.stopPlayback() } else { audio.play(session, r) } }
                        }
                    }
                    .padding(.vertical, 8)
                }
            }
        }
    }
}

/// لوحة فارغة: أيقونة في مربع وعنوان وشرح.
struct EmptyPanel: View {
    let icon: String
    let title: String
    let message: String

    var body: some View {
        VStack(spacing: 0) {
            PhIcon(icon, Palette.navy, 44).frame(width: 96, height: 96).background(RoundedRectangle(cornerRadius: 24).fill(Palette.navySoft))
            Spacer().frame(height: 18)
            Text(title).font(Fonts.sans(17, .bold)).foregroundColor(Palette.textPrimary).multilineTextAlignment(.center)
            Spacer().frame(height: 6)
            Text(message).font(Fonts.sans(14)).foregroundColor(Palette.textSecondary).multilineTextAlignment(.center).lineSpacing(3)
        }
        .padding(28)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// ---------------------------------------------------------------- خيارات الصفحة

/// درج «خيارات الصفحة» (نفس PagePanel في أندرويد): مجموعات بطاقات بيضاء على ورق عاجي،
/// والخيارات الخطرة بالأحمر في قسم منفصل.
struct PageMenuDrawer: View {
    @ObservedObject var session: DocSession
    @EnvironmentObject var app: AppController
    @Binding var sheet: ReaderSheet?
    @State private var moveOpen = false
    @State private var confirmClear = false
    @State private var confirmDelete = false
    @State private var changeTemplate = false
    @State private var outlineAdd = false
    @State private var itemsDialog = false

    var body: some View {
        let s = session
        let n = s.pageCount
        let p = max(0, min(n - 1, s.currentPage))
        let blank = s.isBlank(p)
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    VStack(alignment: .leading, spacing: 0) {
                        Text(L("الصفحة \(p + 1)", "Page \(p + 1)")).font(Fonts.display(24)).foregroundColor(Palette.navy)
                        Text((blank ? Templates.labelOf(Templates.parse(s.data.pages[p].src).0) : L("صفحة من الملف", "Page from the file")) + L(" · من \(n)", " · of \(n)"))
                            .font(Fonts.sans(12)).foregroundColor(Palette.textSecondary)
                    }
                    Spacer()
                    Button { s.pageMenuOpen = false } label: { PhIcon("ph_close", Palette.textSecondary, 20, L("إغلاق", "Close")).frame(width: 44, height: 44) }
                }
                .padding(.leading, 20).padding(.trailing, 6).padding(.top, 14).padding(.bottom, 8)
                Rectangle().fill(Palette.gold.opacity(0.45)).frame(height: 1)

                group {
                    let marked = s.isBookmarked(p)
                    row(marked ? L("إزالة العلامة المرجعية", "Remove bookmark") : L("علامة مرجعية للصفحة", "Bookmark page"),
                        marked ? "ph_bookmark_fill" : "ph_bookmark", tint: marked ? Palette.goldDeep : Palette.navy) { s.toggleBookmark(p) }
                    divider
                    row(L("تكرار الصفحة", "Duplicate page"), "ph_copy") {
                        s.duplicatePage(p)
                        app.say(.done, L("كُرّرت الصفحة \(p + 1)", "Page \(p + 1) duplicated"))
                    }
                    divider
                    row(L("إضافة صفحة بعدها", "Add page after"), "ph_add_page") { sheet = .addPage }
                    divider
                    row(L("إضافة الصفحة إلى الفهرس", "Add page to outline"), "ph_list") { outlineAdd = true }
                    divider
                    row(L("تغيير القالب", "Change template"), "ph_pages", sub: blank ? nil : L("لصفحات الملاحظات الفارغة فقط", "Blank note pages only"), enabled: blank) { changeTemplate = true }
                    divider
                    row(L("نقل الصفحة", "Move page"), "ph_caret_down", trailing: moveOpen ? "▴" : "▾") { moveOpen.toggle() }
                    if moveOpen {
                        HStack(spacing: 6) {
                            small(L("للأعلى", "Up"), p > 0) { s.movePage(p, to: p - 1); s.goTo(page: p - 1) }
                            small(L("للأسفل", "Down"), p < n - 1) { s.movePage(p, to: p + 1); s.goTo(page: p + 1) }
                            small(L("للبداية", "Start"), p > 0) { s.movePage(p, to: 0); s.goTo(page: 0) }
                            small(L("للنهاية", "End"), p < n - 1) { s.movePage(p, to: n - 1); s.goTo(page: n - 1) }
                        }
                        .padding(.horizontal, 14).padding(.bottom, 10)
                    }
                    divider
                    row(L("الانتقال إلى صفحة", "Go to page"), "ph_list", trailing: "\(p + 1) / \(n)") { sheet = .jump }
                }

                section(L("مسح أو حذف", "Clear or delete"))
                group {
                    row(L("حذف عناصر محددة", "Delete specific items"), "ph_shapes") { itemsDialog = true }
                    divider
                    row(L("مسح الصفحة", "Clear page"), "ph_eraser", tint: Palette.danger, textColor: Palette.danger) { confirmClear = true }
                    divider
                    row(L("حذف الصفحة", "Delete page"), "ph_trash", tint: Palette.danger, textColor: Palette.danger, enabled: n > 1) { confirmDelete = true }
                }

                section(L("الإعدادات", "Settings"))
                group {
                    row(L("كل الإعدادات وتخصيص الأدوات", "All settings and tool customization"), "ph_sliders") {
                        s.pageMenuOpen = false
                        app.settingsOpen = true
                    }
                }
            }
            .padding(.bottom, 24)
        }
        .frame(width: 380 * Fonts.base)
        .frame(maxHeight: .infinity)
        .background(Palette.drawerBg.shadow(color: .black.opacity(0.18), radius: 14))
        .alert(L("مسح الصفحة \(p + 1)؟", "Clear page \(p + 1)?"), isPresented: $confirmClear) {
            Button(L("مسح", "Clear"), role: .destructive) { clear(p, ink: true, hl: true, tx: true, im: true) }
            Button(L("إلغاء", "Cancel"), role: .cancel) {}
        } message: { Text(L("تُمسح الكتابة والتظليل والنصوص والصور من هذه الصفحة، وتبقى الصفحة نفسها. يمكنك التراجع.", "Writing, highlights, text and images are removed from this page; the page itself stays. You can undo.")) }
        .alert(L("حذف الصفحة \(p + 1)؟", "Delete page \(p + 1)?"), isPresented: $confirmDelete) {
            Button(L("حذف", "Delete"), role: .destructive) { s.deletePage(p) }
            Button(L("إلغاء", "Cancel"), role: .cancel) {}
        } message: { Text(L("ستُحذف الصفحة وكل ما كتبته عليها. يمكنك التراجع.", "The page and everything written on it will be deleted. You can undo.")) }
        .dialogSheet(isPresented: $outlineAdd) {
            NameSheet(title: L("عنوان للصفحة \(p + 1)", "Heading for page \(p + 1)"), initial: "") { t in
                s.edit { $0.outline.append(OutlineEntry(title: t, level: 0, page: p)) }
                app.say(.done, L("أُضيفت الصفحة إلى الفهرس", "Page added to the outline"))
            }
        }
        .sheet(isPresented: $itemsDialog) { ClearItemsSheet(page: p) { ink, hl, tx, im in clear(p, ink: ink, hl: hl, tx: tx, im: im) } }
        .bigSheet(isPresented: $changeTemplate, width: 0.92, height: 0.9, maxHeight: 900, maxWidth: 1040) {
            TemplatePickerSheet(mode: .change, sizeLocked: true) { choice, _, _ in
                s.changeTemplate(p, kind: choice.kind, color: choice.color)
            }
        }
    }

    private func clear(_ p: Int, ink: Bool, hl: Bool, tx: Bool, im: Bool) {
        let s = session
        guard let pid = s.pageId(p) else { return }
        if ink { s.setInk(pid, PKDrawing()) }
        s.edit { d in
            if hl { d.marks.removeAll { $0.page == pid } }
            if tx { d.boxes.removeAll { $0.page == pid }; d.notes.removeAll { $0.page == pid } }
            if im { d.images.removeAll { $0.page == pid } }
        }
    }

    private func group<C: View>(@ViewBuilder _ content: () -> C) -> some View {
        VStack(spacing: 0) { content() }
            .background(RoundedRectangle(cornerRadius: 16).fill(Color.white))
            .roundedBorder(Palette.divider, radius: 16)
            .padding(.horizontal, 14).padding(.top, 12)
    }

    private func section(_ t: String) -> some View {
        Text(t).font(Fonts.sans(17, .bold)).foregroundColor(Palette.navy).padding(.horizontal, 20).padding(.top, 22)
    }

    private var divider: some View { Rectangle().fill(Palette.divider).frame(height: 1).padding(.leading, 52) }

    private func row(_ label: String, _ icon: String, tint: Color = Palette.navy, textColor: Color = Palette.textPrimary,
                     sub: String? = nil, trailing: String? = nil, enabled: Bool = true, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 14) {
                PhIcon(icon, tint.opacity(enabled ? 1 : 0.4), 22)
                VStack(alignment: .leading, spacing: 1) {
                    Text(label).font(Fonts.sans(16)).foregroundColor(textColor.opacity(enabled ? 1 : 0.4))
                    if let s = sub { Text(s).font(Fonts.sans(11)).foregroundColor(Palette.textSecondary) }
                }
                Spacer()
                if let t = trailing { Text(t).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary) }
            }
            .padding(.horizontal, 16).padding(.vertical, 13)
            .contentShape(Rectangle())
        }
        .disabled(!enabled)
    }

    private func small(_ label: String, _ enabled: Bool, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(Fonts.sans(13, .medium)).foregroundColor(enabled ? Palette.navy : Palette.textSecondary).lineLimit(1).fixedSize()
                .padding(.horizontal, 12).padding(.vertical, 8)
                .background(RoundedRectangle(cornerRadius: 12).fill(enabled ? Palette.navySoft : Palette.background))
        }
        .disabled(!enabled)
    }
}

/// «حذف عناصر محددة» من الصفحة.
private struct ClearItemsSheet: View {
    let page: Int
    let onDelete: (Bool, Bool, Bool, Bool) -> Void
    @State private var ink = true
    @State private var hl = false
    @State private var tx = false
    @State private var im = false
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        SheetScaffold(title: L("حذف عناصر من الصفحة \(page + 1)", "Delete items from page \(page + 1)")) {
            VStack(alignment: .leading, spacing: 4) {
                Toggle(L("الكتابة اليدوية والرسم", "Handwriting and drawings"), isOn: $ink)
                Toggle(L("التظليل (ويُزال من الملخص)", "Highlights (also removed from the summary)"), isOn: $hl)
                Toggle(L("مربعات النص والملاحظات اللاصقة", "Text boxes and sticky notes"), isOn: $tx)
                Toggle(L("الصور", "Images"), isOn: $im)
                Text(L("يمكنك التراجع عن ذلك.", "You can undo this.")).font(Fonts.sans(12)).foregroundColor(Palette.textSecondary)
                HStack {
                    Spacer()
                    NavyButton(label: L("إلغاء", "Cancel"), filled: false) { dismiss() }
                    NavyButton(label: L("حذف", "Delete"), danger: true) { onDelete(ink, hl, tx, im); dismiss() }.disabled(!(ink || hl || tx || im))
                }
                .padding(.top, 8)
            }
            .font(Fonts.sans(14)).tint(Palette.navy)
            .padding(.horizontal, 22)
        }
        .presentationDetents([.height(340)])
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
    @State private var deleteSticker: String? = nil

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
                Button { importing = true } label: { trayButton("ph_folder", L("أو اختر من الملفات", "Or pick from files")) }
                // معرض الصور داخل الدرج (كما في أندرويد): انقر للإضافة، أو اسحب الصورة إلى الصفحة
                PhotoGalleryGrid { img in place(.images([img])) }
                Text(L("انقر للإضافة · اسحب جانبًا للوضع", "Tap to add · drag sideways to place"))
                    .font(Fonts.sans(12)).foregroundColor(Palette.textSecondary)
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 10) {
                        // ملصقاتي أولًا، ثم الأقسام الجاهزة (كما في أندرويد)
                        if !store.mine.isEmpty {
                            Text(L("ملصقاتي", "My stickers")).font(Fonts.sans(13, .bold)).foregroundColor(Palette.navy)
                            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 4), spacing: 8) {
                                ForEach(store.mine, id: \.self) { f in
                                    Button { place(.sticker("mine:" + f)) } label: {
                                        Group {
                                            if let img = UIImage(contentsOfFile: Repository.shared.stickersDir.appendingPathComponent(f).path) {
                                                Image(uiImage: img).resizable().scaledToFit()
                                            }
                                        }
                                        .frame(width: 48, height: 48).frame(width: 56, height: 56)
                                        .background(RoundedRectangle(cornerRadius: 12).fill(Color.white))
                                    }
                                    .onDrag { NSItemProvider(object: (StickerDragPrefix + "mine:" + f) as NSString) }
                                    .contextMenu { Button(role: .destructive) { deleteSticker = f } label: { Text(L("حذف", "Delete")) } }
                                }
                            }
                            Text(L("اضغط مطوّلًا على ملصق لحذفه", "Long-press a sticker to delete it")).font(Fonts.sans(11)).foregroundColor(Palette.textSecondary)
                        }
                        ForEach(Array(StickerCategories.enumerated()), id: \.offset) { _, cat in
                            Text(cat.0).font(Fonts.sans(13, .bold)).foregroundColor(Palette.navy).padding(.top, 4)
                            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 4), spacing: 8) {
                                ForEach(cat.1, id: \.self) { k in
                                    Button { place(.sticker(k)) } label: {
                                        StickerIcon(key: k, size: 40).frame(width: 56, height: 56)
                                            .background(RoundedRectangle(cornerRadius: 12).fill(Color.white))
                                    }
                                    .onDrag { NSItemProvider(object: (StickerDragPrefix + k) as NSString) }
                                }
                            }
                        }
                    }
                }
                Text(L("انقر الملصق ثم انقر مكانه في الصفحة، أو اضغط عليه مطوّلًا واسحبه إليها", "Tap a sticker, then tap its place on the page; or long-press and drag it there"))
                    .font(Fonts.sans(12)).foregroundColor(Palette.textSecondary)
                Text(L("من رسمي: حدد رسمك بأداة التحديد الحر ثم اضغط «☺ ملصق»", "From my drawing: select your drawing with the lasso, then tap “☺ Sticker”"))
                    .font(Fonts.sans(12)).foregroundColor(Palette.textSecondary)
                PhotosPicker(selection: $stickerPick, maxSelectionCount: 1, matching: .images) {
                    trayButton("ph_plus", L("ملصق من صورة", "Sticker from a photo"))
                }
            }
            Spacer(minLength: 0)
        }
        .padding(16)
        .frame(width: 290 * Fonts.base)
        .frame(maxHeight: 520)
        .background(RoundedRectangle(cornerRadius: 22).fill(Palette.drawerBg))
        .shadow(color: .black.opacity(0.18), radius: 14, y: 6)
        .padding(12)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
        .alert(L("حذف الملصق؟", "Delete sticker?"), isPresented: Binding(get: { deleteSticker != nil }, set: { if !$0 { deleteSticker = nil } })) {
            Button(L("حذف", "Delete"), role: .destructive) { if let f = deleteSticker { StickerStore.shared.delete(f) }; deleteSticker = nil }
            Button(L("إلغاء", "Cancel"), role: .cancel) { deleteSticker = nil }
        } message: { Text(L("يُزال من «ملصقاتي». النسخ الموضوعة على الصفحات تبقى.", "It is removed from “My stickers”. Copies already on pages stay.")) }
        .onChange(of: picks) { _, items in
            let n = items.count
            for (i, it) in items.enumerated() {
                Task {
                    if let d = try? await it.loadTransferable(type: Data.self), let img = UIImage(data: d) {
                        await MainActor.run { collect(img, total: n) }
                    }
                }
            }
            picks = []
        }
        .onChange(of: stickerPick) { _, items in
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
                for (i, u) in urls.enumerated() {
                    let sc = u.startAccessingSecurityScopedResource()
                    if let d = try? Data(contentsOf: u), let img = UIImage(data: d) { _ = i; collect(img, total: urls.count) }
                    if sc { u.stopAccessingSecurityScopedResource() }
                }
            }
        }
    }

    @State private var gathered: [UIImage] = []

    /// انقر العنصر ثم انقر الصفحة حيث تريد وضعه (كما في أندرويد 1.0.271).
    private func place(_ p: DocSession.PendingPlace) {
        session.pendingPlace = p
        app.say(.tip, L("انقر الصفحة حيث تريد وضعه", "Tap the page where you want to place it"))
    }

    /// الصور المختارة معًا تُجمع ثم توضع كلها حيث تنقر.
    private func collect(_ img: UIImage, total: Int) {
        gathered.append(img)
        if gathered.count >= total {
            place(.images(gathered))
            gathered = []
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
            AppController.shared.say(.done, L("أُضيف إلى «ملصقاتي»", "Added to “My stickers”"))
        }
    }

    func delete(_ f: String) {
        try? FileManager.default.removeItem(at: Repository.shared.stickersDir.appendingPathComponent(f))
        mine.removeAll { $0 == f }
    }
}

// ---------------------------------------------------------------- معرض الصور داخل الدرج

/// صور الجهاز (الأحدث أولًا) مع تبويبي الكاميرا ولقطات الشاشة.
final class PhotoGallery: ObservableObject {
    static let shared = PhotoGallery()
    @Published var status: PHAuthorizationStatus = PHPhotoLibrary.authorizationStatus(for: .readWrite)
    @Published var assets: [PHAsset] = []
    @Published var screenshots = false { didSet { load() } }
    private let manager = PHCachingImageManager()

    func request() {
        PHPhotoLibrary.requestAuthorization(for: .readWrite) { st in
            DispatchQueue.main.async { self.status = st; self.load() }
        }
    }

    func load() {
        status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        guard status == .authorized || status == .limited else { assets = []; return }
        let wantShots = screenshots
        DispatchQueue.global(qos: .userInitiated).async {
            let o = PHFetchOptions()
            o.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
            o.fetchLimit = 150
            let shot = PHAssetMediaSubtype.photoScreenshot.rawValue
            o.predicate = wantShots ? NSPredicate(format: "(mediaSubtypes & %d) != 0", shot) : NSPredicate(format: "(mediaSubtypes & %d) == 0", shot)
            let r = PHAsset.fetchAssets(with: .image, options: o)
            var list: [PHAsset] = []
            r.enumerateObjects { a, _, _ in list.append(a) }
            DispatchQueue.main.async { self.assets = list }
        }
    }

    func thumb(_ a: PHAsset, _ done: @escaping (UIImage?) -> Void) {
        let o = PHImageRequestOptions()
        o.deliveryMode = .opportunistic
        o.isNetworkAccessAllowed = true
        manager.requestImage(for: a, targetSize: CGSize(width: 220, height: 220), contentMode: .aspectFill, options: o) { img, _ in done(img) }
    }

    func full(_ a: PHAsset, _ done: @escaping (UIImage?) -> Void) {
        let o = PHImageRequestOptions()
        o.deliveryMode = .highQualityFormat
        o.isNetworkAccessAllowed = true
        manager.requestImage(for: a, targetSize: CGSize(width: 2000, height: 2000), contentMode: .aspectFit, options: o) { img, info in
            if (info?[PHImageResultIsDegradedKey] as? Bool) == true { return }
            DispatchQueue.main.async { done(img) }
        }
    }
}

struct PhotoGalleryGrid: View {
    @ObservedObject var gallery = PhotoGallery.shared
    @EnvironmentObject var app: AppController
    let onPick: (UIImage) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            switch gallery.status {
            case .authorized, .limited:
                HStack(spacing: 6) {
                    chip(L("الكاميرا", "Camera"), !gallery.screenshots) { gallery.screenshots = false }
                    chip(L("لقطات الشاشة", "Screenshots"), gallery.screenshots) { gallery.screenshots = true }
                }
                if gallery.status == .limited {
                    Button {
                        if let vc = UIApplication.shared.connectedScenes.compactMap({ ($0 as? UIWindowScene)?.keyWindow?.rootViewController }).first {
                            PHPhotoLibrary.shared().presentLimitedLibraryPicker(from: vc) { _ in gallery.load() }
                        }
                    } label: {
                        Text(L("بعض الصور فقط · انقر للمزيد", "Some photos only · tap for more")).font(Fonts.sans(12)).foregroundColor(Palette.navy)
                    }
                }
                if gallery.assets.isEmpty {
                    Text(L("لا توجد صور هنا", "No photos here")).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary).padding(.vertical, 14)
                } else {
                    ScrollView {
                        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 4), count: 3), spacing: 4) {
                            ForEach(gallery.assets, id: \.localIdentifier) { a in
                                GalleryTile(asset: a, onPick: onPick)
                            }
                        }
                    }
                }
            default:
                Text(L("اسمح لـ especial notes بعرض صورك لتسحبها إلى الصفحة.", "Allow especial notes to show your photos so you can drag them in."))
                    .font(Fonts.sans(12.5)).foregroundColor(Palette.textSecondary)
                Button {
                    if gallery.status == .notDetermined { gallery.request() }
                    else if let u = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(u) }
                } label: {
                    Text(L("السماح بالصور", "Allow photos")).font(Fonts.sans(14, .bold)).foregroundColor(.white)
                        .padding(.horizontal, 16).padding(.vertical, 9)
                        .background(Capsule().fill(Palette.navy))
                }
            }
        }
        .onAppear { gallery.load() }
    }

    private func chip(_ t: String, _ on: Bool, _ a: @escaping () -> Void) -> some View {
        Button(action: a) {
            Text(t).font(Fonts.sans(12.5, on ? .bold : .regular)).foregroundColor(on ? .white : Palette.navy)
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(Capsule().fill(on ? Palette.navy : Palette.navySoft))
        }
        .buttonStyle(.plain)
    }
}

private struct GalleryTile: View {
    let asset: PHAsset
    let onPick: (UIImage) -> Void
    @State private var image: UIImage? = nil

    var body: some View {
        Button {
            PhotoGallery.shared.full(asset) { if let img = $0 { onPick(img) } }
        } label: {
            ZStack {
                Color.white
                if let img = image { Image(uiImage: img).resizable().scaledToFill() }
            }
            .frame(height: 78).frame(maxWidth: .infinity)
            .clipShape(RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
        .onDrag {
            let p = NSItemProvider()
            let a = asset
            p.registerObject(ofClass: UIImage.self, visibility: .all) { done in
                PhotoGallery.shared.full(a) { img in done(img, nil) }
                return nil
            }
            return p
        }
        .onAppear { if image == nil { PhotoGallery.shared.thumb(asset) { img in DispatchQueue.main.async { if let i = img { image = i } } } } }
    }
}
