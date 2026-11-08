import SwiftUI
import UniformTypeIdentifiers
import VisionKit

/// النوافذ التي تفتح من المكتبة.
enum LibrarySheet: Identifiable {
    case rename(DocEntry), move([DocEntry]), cover(DocEntry), share([String])
    case newNote, newMap, newCard, addFolder, renameFolder(String), exam(String), textSearch, scan
    var id: String {
        switch self {
        case .rename(let e): return "rename" + e.id
        case .move(let l): return "move" + l.map { $0.id }.joined()
        case .cover(let e): return "cover" + e.id
        case .share(let l): return "share" + l.joined()
        case .newNote: return "newNote"
        case .newMap: return "newMap"
        case .newCard: return "newCard"
        case .addFolder: return "addFolder"
        case .renameFolder(let f): return "rf" + f
        case .exam(let f): return "exam" + f
        case .textSearch: return "textSearch"
        case .scan: return "scan"
        }
    }
}

struct LibraryScreen: View {
    @EnvironmentObject var app: AppController
    @ObservedObject var pm = Premium.shared
    @State private var sheet: LibrarySheet? = nil
    @State private var importing = false
    @State private var selecting = false
    @State private var selected: Set<String> = []
    @State private var deleteTarget: [DocEntry] = []
    @State private var confirmDelete = false

    private var query: String { app.libraryQuery.trimmingCharacters(in: .whitespaces) }

    private var docs: [DocEntry] {
        let base = app.library.filter {
            (query.isEmpty || $0.name.localizedCaseInsensitiveContains(query)) && (app.libraryFolder == nil || $0.folder == app.libraryFolder)
        }
        switch app.libraryFilter {
        case .recent: return Array(base.sorted { $0.activityAt > $1.activityAt }.prefix(24))
        case .all: return sortDocs(base)
        case .favorites: return sortDocs(base.filter { $0.favorite })
        }
    }

    private func sortDocs(_ l: [DocEntry]) -> [DocEntry] {
        switch app.librarySort {
        case .lastOpened: return l.sorted { $0.activityAt > $1.activityAt }
        case .name: return l.sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
        case .dateAdded: return l.sorted { $0.addedAt > $1.addedAt }
        }
    }

    var body: some View {
        GeometryReader { g in
            let wide = g.size.width >= 840
            HStack(spacing: 0) {
                if wide { Spine(sheet: $sheet) }
                VStack(spacing: 0) {
                    Group {
                        if selecting {
                            SelectionBar(count: selected.count, allSelected: !docs.isEmpty && docs.allSatisfy { selected.contains($0.id) },
                                         onSelectAll: {
                                             if docs.allSatisfy({ selected.contains($0.id) }) { selected.removeAll() }
                                             else { docs.forEach { selected.insert($0.id) } }
                                         },
                                         onShare: { sheet = .share(Array(selected)) },
                                         onFavorite: {
                                             let list = app.library.filter { selected.contains($0.id) }
                                             let allFav = list.allSatisfy { $0.favorite }
                                             list.filter { $0.favorite == allFav }.forEach { app.toggleFavorite($0.id) }
                                         },
                                         onMove: { sheet = .move(app.library.filter { selected.contains($0.id) }) },
                                         onDuplicate: {
                                             app.library.filter { selected.contains($0.id) }.forEach { app.duplicate($0) }
                                             endSelect()
                                         },
                                         onDelete: {
                                             deleteTarget = app.library.filter { selected.contains($0.id) }
                                             confirmDelete = !deleteTarget.isEmpty
                                         },
                                         onDone: endSelect)
                            .transition(.move(edge: .top).combined(with: .opacity))
                        } else {
                            header(wide)
                                .transition(.opacity)
                        }
                    }
                    .animation(Motion.enter, value: selecting)
                    if !wide { FilterRow() }
                    if let f = app.libraryFolder {
                        ExamPlanCard(folder: f) { sheet = .exam(f) }
                        HStack {
                            Button { app.openDeck = f } label: {
                                HStack(spacing: 6) {
                                    PhIcon("ph_cards", .white, 17)
                                    Text(L("بطاقات المادة", "Subject cards")).font(Fonts.sans(14, .bold)).foregroundColor(.white)
                                }
                                .padding(.horizontal, 16).padding(.vertical, 9)
                                .background(Capsule().fill(CardTeal))
                            }
                            .buttonStyle(PressScale())
                            Spacer()
                        }
                        .padding(.horizontal, 28).padding(.vertical, 4)
                    }
                    if app.importing { ProgressView().progressViewStyle(.linear).tint(Lib.accent).frame(height: 2) }
                    content(wide)
                }
            }
        }
        .background(Lib.bg.ignoresSafeArea())
        .fileImporter(isPresented: $importing, allowedContentTypes: OfficeConverter.types, allowsMultipleSelection: true) { r in
            if case .success(let urls) = r { app.importDocuments(urls) }
        }
        // ملاحظة جديدة: منتقي الورق الكبير؛ والأسماء (مادة جديدة، إعادة تسمية) في نافذة حوار مثل أندرويد
        .bigSheet(isPresented: Binding(get: { sheet?.id == "newNote" }, set: { if !$0 { sheet = nil } }),
                  width: 0.92, height: 0.9, maxHeight: 900, maxWidth: 1040) {
            TemplatePickerSheet(mode: .newNote) { choice, name, _ in app.createNote(name, choice) }
        }
        .dialogSheet(isPresented: Binding(get: { sheet.map(LibraryScreen.isNameDialog) ?? false }, set: { if !$0 { sheet = nil } })) {
            if let s = sheet { sheetView(s) }
        }
        .sheet(item: Binding(get: { sheet.flatMap { LibraryScreen.isNameDialog($0) || $0.id == "newNote" ? nil : $0 } }, set: { sheet = $0 })) { s in sheetView(s) }
        .alert(deleteTarget.count == 1 ? L("حذف المستند؟", "Delete document?") : L("حذف \(deleteTarget.count) مستندات؟", "Delete \(deleteTarget.count) documents?"),
               isPresented: $confirmDelete) {
            Button(L("حذف", "Delete"), role: .destructive) {
                deleteTarget.forEach { app.delete($0) }
                deleteTarget = []
                endSelect()
            }
            Button(L("إلغاء", "Cancel"), role: .cancel) { deleteTarget = [] }
        } message: {
            Text(deleteTarget.count == 1
                 ? L("سيتم حذف «\(deleteTarget.first?.name ?? "")» وكل الكتابة عليه نهائيًا، ولا يمكن التراجع.", "“\(deleteTarget.first?.name ?? "")” and everything written on it will be deleted permanently. This cannot be undone.")
                 : L("ستُحذف المستندات المحددة وكل الكتابة عليها نهائيًا، ولا يمكن التراجع.", "The selected documents and everything written on them will be deleted permanently. This cannot be undone."))
        }
    }

    private func endSelect() {
        selected.removeAll()
        withAnimation(Motion.enter) { selecting = false }
    }

    // ---------------------------------------------------------------- الترويسة

    private func header(_ wide: Bool) -> some View {
        let title = app.libraryFolder ?? filterTitle(app.libraryFilter)
        return VStack(spacing: 14) {
            HStack(spacing: 10) {
                HStack(alignment: .lastTextBaseline, spacing: 12) {
                    UnderlinedTitle(text: title, size: wide ? 40 : 30)
                    ScriptLabel(text: docsCount(docs.count), color: Lib.muted, size: 15)
                }
                Spacer(minLength: 8)
                PomodoroControl(showStartLabel: false)
                if !wide { IconCircleButton(icon: "ph_sliders", label: L("الإعدادات", "Settings")) { app.settingsOpen = true } }
                IconCircleButton(icon: "ph_check", label: L("تحديد", "Select")) { withAnimation(Motion.enter) { selecting = true } }
                NewMenuButton(onNewNote: { sheet = .newNote }, onImport: { importing = true },
                              onScan: { sheet = .scan }, onNewMap: { sheet = .newMap }, onNewCard: { sheet = .newCard })
            }
            HStack(spacing: 10) {
                SearchField(value: $app.libraryQuery)
                Button { sheet = .textSearch } label: {
                    HStack(spacing: 6) {
                        PhIcon("ph_search_all", Lib.text, 19)
                        Text(L("داخل النصوص", "In text")).font(Fonts.sans(14)).foregroundColor(Lib.text).lineLimit(1)
                    }
                    .padding(.horizontal, 16).frame(height: 48)
                    .background(Capsule().fill(Lib.card)).overlay(Capsule().stroke(Lib.line, lineWidth: 1))
                }
                .buttonStyle(PressScale(scale: 0.96))
            }
        }
        .padding(.horizontal, wide ? 28 : 20)
        .padding(.top, wide ? 24 : 16)
        .padding(.bottom, 12)
    }

    // ---------------------------------------------------------------- المحتوى

    @ViewBuilder
    private func content(_ wide: Bool) -> some View {
        if !app.libraryLoaded {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if app.library.isEmpty {
            EmptyLibrary(onNewNote: { sheet = .newNote }, onImport: { importing = true }, onScan: { sheet = .scan },
                         onNewMap: { sheet = .newMap }, onNewCard: { sheet = .newCard })
        } else if docs.isEmpty {
            Text(app.libraryFilter == .favorites && query.isEmpty
                 ? L("لا توجد مستندات مفضلة بعد. اختر «إضافة إلى المفضلة» من قائمة أي مستند.", "No favorites yet. Choose “Add to favorites” from any document’s menu.")
                 : L("لا يوجد مستند باسم «\(query)»", "No document named “\(query)”"))
                .font(Fonts.sans(15)).foregroundColor(Palette.textSecondary).multilineTextAlignment(.center)
                .padding(24).frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            let resume: DocEntry? = (app.libraryFilter == .recent && query.isEmpty) ? docs.first(where: { $0.lastOpened > 0 }) : nil
            ScrollView {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: (wide ? 164 : 140) * Fonts.base), spacing: 24)], alignment: .leading, spacing: 26) {
                    if resume != nil || (wide && app.libraryFolder == nil && !app.folders.isEmpty && query.isEmpty) {
                        Section {
                            EmptyView()
                        } header: {
                            ResumeRow(resume: resume, wide: wide)
                        }
                    }
                    Section {
                        ForEach(docs) { e in
                            DocCard(entry: e, selecting: selecting, selected: selected.contains(e.id),
                                    onToggleSelect: {
                                        if !selecting { withAnimation(Motion.enter) { selecting = true } }
                                        if selected.contains(e.id) { selected.remove(e.id) } else { selected.insert(e.id) }
                                    },
                                    onOpen: { app.openDocument(e.id) },
                                    onRename: { sheet = .rename(e) },
                                    onMove: { sheet = .move([e]) },
                                    onCover: { sheet = .cover(e) },
                                    onShare: {
                                        if e.locked { app.say(.error, L("افتح قفل المستند أولًا", "Unlock the document first")) }
                                        else { sheet = .share([e.id]) }
                                    },
                                    onDelete: { deleteTarget = [e]; confirmDelete = true })
                        }
                    } header: {
                        HStack {
                            Text(L("المستندات", "Documents")).font(Fonts.display(22)).foregroundColor(Lib.text)
                            Spacer()
                            if app.libraryFilter == .recent {
                                Text(L("حسب آخر فتح", "Sorted by last opened")).font(Fonts.sans(13)).foregroundColor(Lib.muted)
                            } else {
                                SortMenu()
                            }
                        }
                        .padding(.top, 6)
                    }
                }
                .padding(.horizontal, 28)
                .padding(.top, 8)
                .padding(.bottom, 32)
                .animation(Motion.enter, value: docs.map { $0.id })
            }
        }
    }

    // ---------------------------------------------------------------- النوافذ

    static func isNameDialog(_ s: LibrarySheet) -> Bool {
        switch s {
        case .rename, .addFolder, .renameFolder, .newCard: return true
        default: return false
        }
    }

    @ViewBuilder
    private func sheetView(_ s: LibrarySheet) -> some View {
        Group {
            switch s {
            case .rename(let e):
                NameSheet(title: L("إعادة تسمية المستند", "Rename document"), initial: e.name) { app.rename(e.id, $0) }
            case .move(let list):
                MoveToFolderSheet(entries: list) { endSelect() }
            case .cover(let e):
                CoverSheet(entry: e)
            case .share(let ids):
                ShareExportSheet(docIds: ids)
            case .newNote:
                TemplatePickerSheet(mode: .newNote) { choice, name, _ in app.createNote(name, choice) }
            case .newMap:
                NewMapSheet(initialFolder: app.libraryFolder ?? "")
            case .newCard:
                NewLooseCardSheet(initialFolder: app.libraryFolder ?? "")
            case .addFolder:
                NameSheet(title: L("مادة جديدة", "New subject"), initial: "") { app.addFolder($0) }
            case .renameFolder(let f):
                NameSheet(title: L("إعادة تسمية المادة", "Rename subject"), initial: f) { app.renameFolder(f, $0) }
            case .exam(let f):
                ExamDateSheet(folder: f)
            case .textSearch:
                LibrarySearchSheet()
            case .scan:
                ScannerView { imgs in app.importScan(imgs) }.ignoresSafeArea()
            }
        }
        .environmentObject(app)
        .environment(\.layoutDirection, .leftToRight)
    }
}

func filterTitle(_ f: LibraryFilter) -> String {
    switch f {
    case .recent: return L("الأحدث", "Recent")
    case .all: return L("كل المستندات", "All documents")
    case .favorites: return L("المفضلة", "Favorites")
    }
}

func sortLabel(_ m: SortMode) -> String {
    switch m {
    case .lastOpened: return L("آخر فتح", "Last opened")
    case .name: return L("الاسم", "Name")
    case .dateAdded: return L("تاريخ الإضافة", "Date added")
    }
}

// ---------------------------------------------------------------- الكعب (الشريط الجانبي)

private struct Spine: View {
    @EnvironmentObject var app: AppController
    @ObservedObject var pm = Premium.shared
    @Binding var sheet: LibrarySheet?

    var body: some View {
        let favCount = app.library.filter { $0.favorite }.count
        let pages = app.library.reduce(0) { $0 + $1.pageCount }
        let noFolder = app.libraryFolder == nil
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 10) {
                Art(name: "ic_logo", size: 42)
                VStack(alignment: .leading, spacing: 0) {
                    Text(L("هامش", "hamish")).font(Fonts.display(26)).foregroundColor(Lib.text)
                    Text(L("مكتبة الدراسة", "Study library")).font(Fonts.sans(12)).foregroundColor(Lib.muted)
                }
            }
            .padding(.leading, 4)
            Spacer().frame(height: 22)
            Group {
            SpineItem(label: L("الأحدث", "Recent"), icon: "ph_recent", count: nil, selected: noFolder && app.libraryFilter == .recent) {
                app.libraryFolder = nil; app.libraryFilter = .recent
            }
            SpineItem(label: L("كل المستندات", "All documents"), icon: "ph_books_fill", count: app.library.count,
                      selected: noFolder && app.libraryFilter == .all, tint: Lib.iconBlue) {
                app.libraryFolder = nil; app.libraryFilter = .all
            }
            SpineItem(label: L("المفضلة", "Favorites"), icon: "ph_star_fill", count: favCount,
                      selected: noFolder && app.libraryFilter == .favorites, tint: Lib.iconBlue) {
                app.libraryFolder = nil; app.libraryFilter = .favorites
            }
            SpineItem(label: L("كل البطاقات", "All cards"), icon: "ph_cards", count: nil, selected: false, tint: CardTeal) { app.openDeck = "*" }
            SpineItem(label: L("الإعدادات", "Settings"), icon: "ph_sliders", count: nil, selected: false) { app.settingsOpen = true }
            }

            ScrollView(showsIndicators: false) { FolderSection(sheet: $sheet) }

            if !pm.active {
                PremiumTeaser(title: L("هامش Premium", "Hamish Premium"), desc: L("\(Premium.trialDays) أيام مجانًا", "\(Premium.trialDays) days free"))
                Spacer().frame(height: 10)
            }
            Group {
            StudyCard()
            Spacer().frame(height: 14)
            Text(docsCount(app.library.count) + " · " + pagesCount(pages)).font(Fonts.sans(12)).foregroundColor(Lib.muted)
            Spacer().frame(height: 10)
            ThemeSwitch()
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 22)
        .frame(width: 248 * Fonts.base)
        .frame(maxHeight: .infinity)
        .background(Lib.side.ignoresSafeArea())
    }
}

struct SpineItem: View {
    @Environment(\.colorScheme) private var themeTick // يعيد الرسم عند تغيير الثيم
    let label: String
    let icon: String
    let count: Int?
    let selected: Bool
    var sub: String? = nil
    var tint: Color = Lib.text
    var folderIcon = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                if folderIcon { Art(name: "ic_folder_yellow", size: 22) } else { PhIcon(icon, tint, 20) }
                VStack(alignment: .leading, spacing: 1) {
                    Text(label).font(Fonts.sans(15, selected ? .bold : .regular)).foregroundColor(Lib.text).lineLimit(1)
                    if let s = sub { Text(s).font(Fonts.sans(11, .bold)).foregroundColor(Lib.exam) }
                }
                Spacer(minLength: 4)
                if let c = count { Text("\(c)").font(Fonts.sans(13)).foregroundColor(Lib.muted) }
            }
            .padding(.horizontal, 12).padding(.vertical, 6)
            .frame(minHeight: 46)
            .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(selected ? Lib.accentSoft : Color.clear))
            .contentShape(Rectangle())
            .animation(Motion.enter, value: selected)
        }
        .buttonStyle(PressScale(scale: 0.97))
        .padding(.vertical, 2)
    }
}

/// قائمة المواد في الشريط الجانبي.
private struct FolderSection: View {
    @EnvironmentObject var app: AppController
    @Binding var sheet: LibrarySheet?
    @State private var exportFolder: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Spacer().frame(height: 16)
            HStack {
                Text(L("المواد", "SUBJECTS")).font(Fonts.sans(12)).kerning(1.5).foregroundColor(Lib.muted).padding(.leading, 8)
                Spacer()
                Button { sheet = .addFolder } label: {
                    Text(L("+ مادة", "+ Subject")).font(Fonts.sans(13, .bold)).foregroundColor(Lib.accentDeep)
                        .padding(.horizontal, 8).padding(.vertical, 4)
                }
            }
            if app.folders.isEmpty {
                Text(L("رتّب مستنداتك حسب المادة: أضف مادة، ثم «نقل إلى مادة» من قائمة أي مستند.", "Organize documents by subject: add a subject, then “Move to subject” from any document’s menu."))
                    .font(Fonts.sans(12)).foregroundColor(Lib.muted).lineSpacing(4).padding(.top, 4).padding(.leading, 4)
            }
            ForEach(app.folders, id: \.self) { f in
                SpineItem(label: f, icon: "ph_folder", count: app.library.filter { $0.folder == f }.count,
                          selected: app.libraryFolder == f, sub: app.daysToExam(f).map(examLabel), folderIcon: true) {
                    app.libraryFolder = f
                    app.libraryFilter = .all
                }
                .contextMenu {
                    Button { exportFolder = f } label: { Text(L("حفظ ملخص المادة كاملة (PDF)", "Save the whole subject summary (PDF)")) }
                    Button { app.openDeck = f } label: { Text(L("بطاقات المادة", "Subject cards")) }
                    Button { sheet = .exam(f) } label: { Text(L("موعد الامتحان…", "Exam date…")) }
                    Button { sheet = .renameFolder(f) } label: { Text(L("إعادة تسمية", "Rename")) }
                    Button(role: .destructive) { app.deleteFolder(f) } label: { Text(L("حذف المادة (تبقى المستندات)", "Delete subject (documents stay)")) }
                }
            }
        }
        .sheet(item: Binding(get: { exportFolder.map { IdString(id: $0) } }, set: { exportFolder = $0?.id })) { f in
            if let url = Exporter.folderSummary(f.id) { ShareSheet(items: [url]) }
            else { Text(L("لا توجد ملخصات في هذه المادة بعد", "No summaries in this subject yet")).font(Fonts.sans(15)).padding(40) }
        }
    }
}

struct IdString: Identifiable { let id: String }

/// بطاقة الدراسة: هدف اليوم والسلسلة، وتفتح الإحصاءات.
private struct StudyCard: View {
    @EnvironmentObject var app: AppController
    var body: some View {
        let today = app.stats[app.todayKey()] ?? 0
        let streak = app.studyStreak()
        let goal = formatReading(Int64(app.goalMinutes) * 60_000)
        Button { app.statsOpen = true } label: {
            HStack(spacing: 10) {
                GoalRing(size: 40, line: 4, track: Lib.line)
                VStack(alignment: .leading, spacing: 2) {
                    Text(L("اليوم \(formatReading(today))", "Today \(formatReading(today))")).font(Fonts.sans(16, .bold)).foregroundColor(Lib.text)
                    Text(streak > 0
                         ? L("سلسلة \(streak) \(streak == 1 ? "يوم" : "أيام")، الهدف \(goal)", "Streak \(streak) \(streak == 1 ? "day" : "days"), goal \(goal)")
                         : L("الهدف \(goal)", "Goal \(goal)"))
                        .font(Fonts.sans(11)).foregroundColor(Lib.muted)
                }
                Spacer(minLength: 0)
            }
            .padding(12)
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(Lib.study))
            .roundedBorder(Lib.line, radius: 18)
        }
        .buttonStyle(PressScale(scale: 0.97))
    }
}

private struct ThemeSwitch: View {
    @EnvironmentObject var app: AppController
    var body: some View {
        HStack(spacing: 4) {
            ForEach([(AppTheme.navy, L("داكن", "Dark")), (AppTheme.light, L("فاتح", "Light"))], id: \.0) { item in
                let t = item.0
                let label = item.1
                let sel = app.theme == t
                Button { app.setTheme(t) } label: {
                    HStack(spacing: 8) {
                        Circle().fill(t == .navy ? Color(hex: 0xFF111111) : Lib.accent).frame(width: 12, height: 12)
                            .overlay(Circle().stroke(Lib.line, lineWidth: 1))
                        Text(label).font(Fonts.sans(14, sel ? .bold : .regular)).foregroundColor(Lib.text)
                    }
                    .frame(maxWidth: .infinity).padding(.vertical, 9)
                    .background(RoundedRectangle(cornerRadius: 11).fill(sel ? Lib.card : Color.clear))
                }
            }
        }
        .padding(4)
        .background(RoundedRectangle(cornerRadius: 14).fill(Lib.line.opacity(0.6)))
    }
}

// ---------------------------------------------------------------- الترويسة والتصفية

private struct FilterRow: View {
    @EnvironmentObject var app: AppController
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(LibraryFilter.allCases, id: \.self) { f in
                    ChoiceChip(label: filterTitle(f), selected: app.libraryFolder == nil && app.libraryFilter == f) {
                        app.libraryFolder = nil; app.libraryFilter = f
                    }
                }
                ForEach(app.folders, id: \.self) { f in
                    ChoiceChip(label: f, selected: app.libraryFolder == f) { app.libraryFolder = f; app.libraryFilter = .all }
                }
                ChoiceChip(label: "▤ " + L("كل البطاقات", "All cards"), selected: false) { app.openDeck = "*" }
            }
            .padding(.horizontal, 20).padding(.vertical, 6)
        }
    }
}

private struct SearchField: View {
    @Environment(\.colorScheme) private var themeTick // يعيد الرسم عند تغيير الثيم
    @Binding var value: String
    var body: some View {
        HStack(spacing: 10) {
            PhIcon("ph_search", Lib.muted, 19)
            TextField("", text: $value, prompt: Text(L("ابحث باسم المستند", "Search by document name")).foregroundColor(Lib.muted))
                .font(Fonts.sans(15)).foregroundColor(Lib.text)
            if !value.isEmpty {
                Button { value = "" } label: { PhIcon("ph_close", Palette.textSecondary, 16, L("مسح البحث", "Clear search")) }
            }
        }
        .padding(.horizontal, 16).frame(height: 48)
        .background(Capsule().fill(Lib.card))
        .overlay(Capsule().stroke(Lib.line, lineWidth: 1))
    }
}

private struct SortMenu: View {
    @EnvironmentObject var app: AppController
    var body: some View {
        Menu {
            ForEach(SortMode.allCases, id: \.self) { m in
                Button { app.librarySort = m } label: {
                    if m == app.librarySort { Label(sortLabel(m), systemImage: "checkmark") } else { Text(sortLabel(m)) }
                }
            }
        } label: {
            HStack(spacing: 4) {
                Text(L("الترتيب: ", "Sort: ") + sortLabel(app.librarySort)).font(Fonts.sans(13)).foregroundColor(Lib.text)
                PhIcon("ph_caret_down", Palette.textSecondary, 16)
            }
            .padding(.horizontal, 14).frame(height: 40)
            .roundedBorder(Lib.line, radius: 12)
        }
    }
}

/// زر «جديد» الأصفر.
struct NewMenuButton: View {
    @Environment(\.colorScheme) private var themeTick // يعيد الرسم عند تغيير الثيم
    let onNewNote: () -> Void
    let onImport: () -> Void
    var onScan: () -> Void = {}
    var onNewMap: () -> Void = {}
    var onNewCard: () -> Void = {}

    var body: some View {
        Menu {
            Button(action: onNewNote) { Label { Text(L("ملاحظة جديدة", "New note")) } icon: { Image("ph_note") } }
            Button(action: onImport) { Label { Text(L("استيراد PDF", "Import PDF")) } icon: { Image("ph_export") } }
            Button(action: onNewMap) { Label { Text(L("خريطة ذهنية جديدة", "New mind map")) } icon: { Image("ph_mindmap") } }
            Button(action: onNewCard) { Label { Text(L("بطاقة جديدة", "New card")) } icon: { Image("ph_cards") } }
        } label: {
            HStack(spacing: 6) {
                PhIcon("ph_plus", Color(hex: 0xFF1E2A44), 18)
                Text(L("جديد", "New")).font(Fonts.sans(15, .bold)).foregroundColor(Color(hex: 0xFF1E2A44))
                PhIcon("ph_caret_down", Color(hex: 0xFF1E2A44), 14)
            }
            .padding(.horizontal, 18).frame(height: 46)
            .background(Capsule().fill(Lib.accent))
        }
    }
}

/// شريط وضع التحديد.
private struct SelectionBar: View {
    let count: Int
    let allSelected: Bool
    let onSelectAll: () -> Void
    let onShare: () -> Void
    let onFavorite: () -> Void
    let onMove: () -> Void
    let onDuplicate: () -> Void
    let onDelete: () -> Void
    let onDone: () -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                Text(count == 0 ? L("اختر المستندات", "Choose documents") : L("\(count) محدد", "\(count) selected"))
                    .font(Fonts.sans(16, .bold)).foregroundColor(.white).padding(.trailing, 10)
                action(allSelected ? L("إلغاء تحديد الكل", "Deselect all") : L("تحديد الكل", "Select all"), true, onSelectAll)
                action(L("مشاركة أو حفظ", "Share or save"), count > 0, onShare)
                action(L("المفضلة", "Favorite"), count > 0, onFavorite)
                action(L("نقل إلى مادة", "Move to subject"), count > 0, onMove)
                action(L("نسخ", "Duplicate"), count > 0, onDuplicate)
                action(L("حذف", "Delete"), count > 0, onDelete, danger: true)
                action(L("تم", "Done"), true, onDone, gold: true)
            }
            .padding(.horizontal, 14).padding(.vertical, 8)
        }
        .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(Palette.navy))
        .padding(.horizontal, 20).padding(.vertical, 14)
    }

    private func action(_ label: String, _ enabled: Bool, _ a: @escaping () -> Void, danger: Bool = false, gold: Bool = false) -> some View {
        Button(action: a) {
            Text(label).font(Fonts.sans(14, .medium))
                .foregroundColor(gold ? Palette.navy : !enabled ? .white.opacity(0.4) : danger ? Color(hex: 0xFFFF8A80) : .white)
                .padding(.horizontal, 14).padding(.vertical, 8)
                .background(RoundedRectangle(cornerRadius: 14).fill(gold ? Palette.gold : Color.white.opacity(enabled ? 0.12 : 0.05)))
        }
        .disabled(!enabled)
    }
}

// ---------------------------------------------------------------- متابعة القراءة

private struct ResumeRow: View {
    @EnvironmentObject var app: AppController
    let resume: DocEntry?
    let wide: Bool
    var body: some View {
        WeightedRow(spacing: 18, weights: resume != nil && wide && !app.folders.isEmpty ? [1.25, 1] : [1]) {
            if let r = resume {
                ResumeCard(entry: r, wide: wide) { app.openDocument(r.id) }
            }
            if wide && !app.folders.isEmpty {
                WeightedRow(spacing: 8, weights: [1, 1, 1]) {
                    ForEach(Array(app.folders.prefix(3)), id: \.self) { f in
                        FolderTile(name: f, count: app.library.filter { $0.folder == f }.count, selected: app.libraryFolder == f) {
                            app.libraryFolder = f
                            app.libraryFilter = .all
                        }
                    }
                    ForEach(0..<max(0, 3 - app.folders.count), id: \.self) { _ in Color.clear.frame(height: 1) }
                }
            }
        }
        .padding(.bottom, 4)
    }
}

/// صف بأوزان مثل Modifier.weight في أندرويد: يقسم العرض بين العناصر حسب أوزانها، ويتوسطها عموديًا.
struct WeightedRow: Layout {
    var spacing: CGFloat = 0
    var weights: [CGFloat]

    private func widths(_ total: CGFloat, _ n: Int) -> [CGFloat] {
        let ws = (0..<n).map { $0 < weights.count ? weights[$0] : 1 }
        let sum = max(0.0001, ws.reduce(0, +))
        let free = max(0, total - spacing * CGFloat(max(0, n - 1)))
        return ws.map { free * $0 / sum }
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let total = proposal.width ?? 800
        let ws = widths(total, subviews.count)
        var h: CGFloat = 0
        for (i, v) in subviews.enumerated() {
            h = max(h, v.sizeThatFits(ProposedViewSize(width: ws[i], height: proposal.height)).height)
        }
        return CGSize(width: total, height: h)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let ws = widths(bounds.width, subviews.count)
        var x = bounds.minX
        for (i, v) in subviews.enumerated() {
            let sz = v.sizeThatFits(ProposedViewSize(width: ws[i], height: bounds.height))
            v.place(at: CGPoint(x: x, y: bounds.midY - sz.height / 2), proposal: ProposedViewSize(width: ws[i], height: sz.height))
            x += ws[i] + spacing
        }
    }
}

private struct ResumeCard: View {
    @Environment(\.colorScheme) private var themeTick // يعيد الرسم عند تغيير الثيم
    let entry: DocEntry
    let wide: Bool
    let onOpen: () -> Void
    var body: some View {
        let total = max(1, entry.pageCount)
        let page = max(1, min(total, entry.lastPage + 1))
        let readMin = entry.readMs / 60_000
        Button(action: onOpen) {
            HStack(spacing: 22) {
                Art(name: "ic_resume_notebook", size: wide ? 124 : 84)
                VStack(alignment: .leading, spacing: 0) {
                    ScriptLabel(text: L("تابع من حيث توقفت", "continue where you left off"), color: Lib.accentDeep, size: 15)
                    Text(entry.name).font(Fonts.display(wide ? 30 : 22)).foregroundColor(Lib.text).lineLimit(1)
                    Spacer().frame(height: 10)
                    GeometryReader { g in
                        let frac = CGFloat(page) / CGFloat(total)
                        ZStack(alignment: .leading) {
                            Capsule().fill(Lib.accent.opacity(0.35)).frame(height: 6)
                            Capsule().fill(Lib.strong).frame(width: g.size.width * frac, height: 6)
                            Circle().fill(Lib.strong).frame(width: 14, height: 14)
                                .overlay(Circle().stroke(Lib.resume, lineWidth: 2))
                                .offset(x: max(0, g.size.width * frac - 14))
                        }
                    }
                    .frame(height: 14)
                    Spacer().frame(height: 10)
                    HStack {
                        Text(L("الصفحة \(page) من \(total)", "Page \(page) of \(total)") + (readMin >= 1 ? " · " + L("\(readMin) د قراءة", "\(readMin) min read") : ""))
                            .font(Fonts.sans(13)).foregroundColor(Lib.muted)
                        Spacer()
                        if wide {
                            Text(L("متابعة القراءة", "Continue reading")).font(Fonts.sans(15, .bold)).foregroundColor(Lib.onStrong)
                                .padding(.horizontal, 22).padding(.vertical, 12)
                                .background(Capsule().fill(Lib.strong))
                        }
                    }
                }
            }
            .padding(.horizontal, 22).padding(.vertical, 18)
            .background(RoundedRectangle(cornerRadius: 26, style: .continuous).fill(Lib.resume))
            .roundedBorder(Lib.accent.opacity(0.45), radius: 26)
        }
        .buttonStyle(PressScale(scale: 0.98))
    }
}

/// مجلد مادة: رسم مجلد أصفر، والاسم وعدد المستندات تحته.
struct FolderTile: View {
    @Environment(\.colorScheme) private var themeTick // يعيد الرسم عند تغيير الثيم
    let name: String
    let count: Int
    let selected: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            VStack(spacing: 6) {
                FolderArt().aspectRatio(1.3, contentMode: .fit)
                Text(name).font(Fonts.sans(15, .bold)).foregroundColor(Lib.text).lineLimit(1)
                Text(count == 0 ? L("فارغة، أضف PDF", "empty, add a PDF") : docsCount(count)).font(Fonts.sans(12)).foregroundColor(Lib.muted).lineLimit(1)
            }
            .padding(8)
            .background(RoundedRectangle(cornerRadius: 14).fill(selected ? Lib.accentSoft : Color.clear))
        }
        .buttonStyle(PressScale())
        .frame(maxWidth: .infinity)
    }
}

private struct FolderArt: View {
    var body: some View {
        Canvas { ctx, size in
            let w = size.width, h = size.height
            let line = Color(hex: 0xFFC59A3C), back = Color(hex: 0xFFF1CE79), front = Color(hex: 0xFFF9DF94)
            let sw: CGFloat = 2.2
            var tab = Path()
            tab.move(to: CGPoint(x: w * 0.04, y: h * 0.14))
            tab.addLine(to: CGPoint(x: w * 0.36, y: h * 0.14))
            tab.addLine(to: CGPoint(x: w * 0.44, y: h * 0.26))
            tab.addLine(to: CGPoint(x: w * 0.96, y: h * 0.26))
            tab.addLine(to: CGPoint(x: w * 0.96, y: h * 0.96))
            tab.addLine(to: CGPoint(x: w * 0.04, y: h * 0.96))
            tab.closeSubpath()
            ctx.fill(tab, with: .color(back))
            ctx.stroke(tab, with: .color(line), style: StrokeStyle(lineWidth: sw, lineJoin: .round))
            let paper = Path(roundedRect: CGRect(x: w * 0.14, y: h * 0.2, width: w * 0.72, height: h * 0.5), cornerRadius: 4)
            ctx.fill(paper, with: .color(.white))
            ctx.stroke(paper, with: .color(line.opacity(0.55)), lineWidth: sw * 0.7)
            let f = Path(roundedRect: CGRect(x: w * 0.04, y: h * 0.36, width: w * 0.92, height: h * 0.6), cornerRadius: 7)
            ctx.fill(f, with: .color(front))
            ctx.stroke(f, with: .color(line), lineWidth: sw)
            let hnd = Path(roundedRect: CGRect(x: w * 0.36, y: h * 0.56, width: w * 0.28, height: h * 0.14), cornerRadius: h * 0.07)
            ctx.fill(hnd, with: .color(.white))
            ctx.stroke(hnd, with: .color(line), lineWidth: sw * 0.8)
        }
    }
}

/// بطاقة المادة المختارة: العد التنازلي للامتحان وخطة القراءة اليومية.
private struct ExamPlanCard: View {
    @EnvironmentObject var app: AppController
    let folder: String
    let onPick: () -> Void
    var body: some View {
        let days = app.daysToExam(folder)
        let left = app.pagesLeft(folder)
        HStack(spacing: 12) {
            PhIcon("ph_timer", Palette.goldDeep, 22)
            VStack(alignment: .leading, spacing: 2) {
                if let d = days {
                    Text(examLabel(d)).font(Fonts.sans(16, .bold)).foregroundColor(Palette.navy)
                    let perDay = left <= 0 ? 0 : max(1, (left + d) / (d + 1))
                    Text(left <= 0 ? L("وصلت إلى آخر صفحة في كل مستندات المادة، ركّز على المراجعة", "You reached the last page of every document, focus on review")
                         : L("باقي \(left) صفحة لم تصلها: نحو \(perDay) صفحة يوميًا", "\(left) pages left: about \(perDay) pages a day"))
                        .font(Fonts.sans(13)).foregroundColor(Palette.textSecondary)
                } else {
                    Text(L("حدد موعد امتحان المادة لترى خطتك اليومية", "Set the exam date to see your daily plan"))
                        .font(Fonts.sans(14, .medium)).foregroundColor(Palette.navy)
                }
            }
            Spacer()
            Button(action: onPick) {
                Text(days == nil ? L("تحديد الموعد", "Set date") : L("تغيير", "Change")).font(Fonts.sans(14, .bold)).foregroundColor(Palette.goldDeep)
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
        .background(RoundedRectangle(cornerRadius: 16).fill(Palette.goldSoft))
        .roundedBorder(Palette.gold.opacity(0.5), radius: 16)
        .padding(.horizontal, 28).padding(.vertical, 4)
    }
}

// ---------------------------------------------------------------- بطاقة المستند

private struct DocCard: View {
    @EnvironmentObject var app: AppController
    let entry: DocEntry
    let selecting: Bool
    let selected: Bool
    let onToggleSelect: () -> Void
    let onOpen: () -> Void
    let onRename: () -> Void
    let onMove: () -> Void
    let onCover: () -> Void
    let onShare: () -> Void
    let onDelete: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .topLeading) {
                NotebookCover(entry: entry).padding(.trailing, 6)
                if selecting {
                    ZStack {
                        Circle().fill(selected ? Lib.strong : Color.white.opacity(0.9))
                        Circle().stroke(selected ? Lib.accent : Lib.line, lineWidth: 2)
                        if selected { PhIcon("ph_check", Lib.onStrong, 15).transition(.scale) }
                    }
                    .frame(width: 26, height: 26).padding(8)
                    .transition(.scale(scale: 0.2).combined(with: .opacity))
                }
                if entry.favorite {
                    Button { app.toggleFavorite(entry.id) } label: {
                        PhIcon("ph_star_fill", Lib.accent, 15).frame(width: 26, height: 26).background(Circle().fill(Color.black.opacity(0.35)))
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
                    .padding(.leading, 14).padding(.bottom, 8)
                }
            }
            .animation(Motion.grow, value: selecting)
            .animation(Motion.grow, value: selected)
            Spacer().frame(height: 10)
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(entry.name).font(Fonts.sans(15, .bold)).foregroundColor(Lib.text).lineLimit(1)
                    Text((entry.isMap ? L("خريطة ذهنية", "Mind map") : pagesCount(entry.pageCount)) + " · " +
                         (entry.lastOpened > 0 ? L("فُتح ", "Opened ") + formatRelative(entry.lastOpened) : L("أُضيف ", "Added ") + formatRelative(entry.addedAt)))
                        .font(Fonts.sans(12)).foregroundColor(Lib.muted).lineLimit(1)
                }
                Spacer(minLength: 0)
                Menu { menu } label: {
                    PhIcon("ph_more", Lib.muted, 20, L("خيارات المستند", "Document options")).frame(width: 30, height: 30)
                }
            }
            HStack {
                Text(formatSize(entry.sizeBytes)).font(Fonts.sans(11)).foregroundColor(Lib.muted.opacity(0.8))
                Spacer()
                if entry.readMs >= 60_000 { ReadingTimeBadge(ms: entry.readMs, tint: Lib.accentDeep, size: 11, showIcon: false) }
            }
        }
        .padding(4)
        .background(RoundedRectangle(cornerRadius: 12).fill(selected ? Lib.accentSoft : Color.clear))
        .contentShape(Rectangle())
        .onTapGesture { if selecting { onToggleSelect() } else { onOpen() } }
        .contextMenu { menu }
    }

    @ViewBuilder private var menu: some View {
        Button { app.toggleFavorite(entry.id) } label: {
            Label { Text(entry.favorite ? L("إزالة من المفضلة", "Remove from favorites") : L("إضافة إلى المفضلة", "Add to favorites")) } icon: { Image(entry.favorite ? "ph_star_fill" : "ph_star") }
        }
        Button(action: onCover) { Text(L("غلاف الدفتر (اللون والملصق)…", "Notebook cover (color and sticker)…")) }
        Button(action: onRename) { Text(L("إعادة تسمية", "Rename")) }
        Button { app.duplicate(entry) } label: { Text(L("إنشاء نسخة", "Duplicate")) }
        Button(action: onShare) { Label { Text(L("مشاركة أو تصدير…", "Share or export…")) } icon: { Image("ph_export") } }
        Button { app.toggleLock(entry.id) } label: { Text(entry.locked ? L("إلغاء القفل", "Unlock") : L("قفل المستند", "Lock document")) }
        Button(action: onMove) { Text(entry.folder.isEmpty ? L("نقل إلى مادة…", "Move to subject…") : L("المادة: \(entry.folder)…", "Subject: \(entry.folder)…")) }
        Button(role: .destructive, action: onDelete) { Text(L("حذف", "Delete")) }
    }
}

/// غلاف دفتر: كعب داكن، وملصق أبيض باسم المستند، وملصق مرسوم اختياري، وأوراق خلفه.
struct NotebookCover: View {
    @EnvironmentObject var app: AppController
    let entry: DocEntry
    var compact = false

    var body: some View {
        let locked = entry.locked && !app.unlocked.contains(entry.id)
        GeometryReader { g in
            let w = g.size.width, h = g.size.height
            ZStack {
                if entry.pageCount > 1 {
                    UnevenRoundedRectangle(topLeadingRadius: 0, bottomLeadingRadius: 0, bottomTrailingRadius: 8, topTrailingRadius: 8)
                        .fill(Color(hex: 0xFFF4EFE4))
                        .overlay(UnevenRoundedRectangle(topLeadingRadius: 0, bottomLeadingRadius: 0, bottomTrailingRadius: 8, topTrailingRadius: 8).stroke(Color(hex: 0xFFE2D8C3), lineWidth: 1))
                        .padding(.vertical, 5)
                        .offset(x: 5)
                }
                ZStack(alignment: .top) {
                    RoundedRectangle(cornerRadius: 10, style: .continuous).fill(entry.coverColorOrDefault)
                    // الكعب
                    HStack(spacing: 0) {
                        Rectangle().fill(Color.black.opacity(0.24)).frame(width: w * 0.085)
                        Rectangle().fill(Color.white.opacity(0.16)).frame(width: 1).padding(.leading, w * 0.025)
                        Spacer()
                    }
                    .environment(\.layoutDirection, .leftToRight)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    // ملصق الاسم
                    Text(entry.name).font(Fonts.sans(compact ? 9 : 13, .bold)).foregroundColor(Color(hex: 0xFF1E2A44))
                        .lineLimit(3).frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, compact ? 6 : 9).padding(.vertical, compact ? 4 : 7)
                        .background(RoundedRectangle(cornerRadius: 8).fill(Color.white))
                        .padding(.top, compact ? 8 : 14).padding(.leading, compact ? 14 : 22).padding(.trailing, compact ? 6 : 10)
                        .environment(\.layoutDirection, .leftToRight)
                    Group {
                        if entry.isMap && !locked {
                            PhIcon("ph_mindmap", .white.opacity(0.92), compact ? 26 : 44)
                        } else if locked {
                            PhIcon("ph_lock", Lib.accent, compact ? 22 : 36)
                        } else if !entry.sticker.isEmpty {
                            StickerIcon(key: entry.sticker, size: w * 0.5)
                        }
                    }
                    .offset(x: w * 0.05, y: h * 0.15)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    if entry.isMap || entry.isNote {
                        Text(entry.isMap ? L("خريطة", "Map") : L("ملاحظة", "Note")).font(Fonts.sans(10, .bold)).foregroundColor(Color(hex: 0xFF1E2A44))
                            .padding(.horizontal, 6).padding(.vertical, 1)
                            .background(RoundedRectangle(cornerRadius: 6).fill(entry.isMap ? Color.white : Lib.accent))
                            .padding(8)
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                    }
                }
                .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).stroke(Color.white.opacity(0.14), lineWidth: 1))
                .shadow(color: .black.opacity(0.22), radius: 5, y: 3)
            }
        }
        .aspectRatio(0.8, contentMode: .fit)
    }
}

// ---------------------------------------------------------------- الحالة الفارغة

private struct EmptyLibrary: View {
    @Environment(\.colorScheme) private var themeTick // يعيد الرسم عند تغيير الثيم
    let onNewNote: () -> Void
    let onImport: () -> Void
    let onScan: () -> Void
    let onNewMap: () -> Void
    let onNewCard: () -> Void
    var body: some View {
        VStack(spacing: 0) {
            Art(name: "ic_resume_notebook", size: 150)
            Spacer().frame(height: 20)
            Text(L("ابدأ مكتبتك", "Start your library")).font(Fonts.display(26)).foregroundColor(Lib.text)
            Spacer().frame(height: 6)
            Text(L("استورد محاضرة أو كتابًا بصيغة PDF لتقرأه وتكتب عليه بالقلم،\nأو أنشئ ملاحظة فارغة.", "Import a lecture or book as PDF to read and write on it with your pen,\nor create a blank note."))
                .font(Fonts.sans(15)).lineSpacing(6).foregroundColor(Palette.textSecondary).multilineTextAlignment(.center)
            Spacer().frame(height: 20)
            NewMenuButton(onNewNote: onNewNote, onImport: onImport, onScan: onScan, onNewMap: onNewMap, onNewCard: onNewCard)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
