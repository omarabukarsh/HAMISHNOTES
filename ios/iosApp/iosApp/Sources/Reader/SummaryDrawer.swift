import SwiftUI
import Translation

/// درج «ملخصات»: كل ما ظُلّل في هذا المستند فقط، كمستند واحد متصل على ورق عاجي (مثل أندرويد).
/// لا تظهر أسماء الصفحات؛ النص الأكبر على الصفحة يظهر عنوانًا، والجداول جداول، والمظلل مرتين بخط عريض.
struct SummaryDrawer: View {
    @ObservedObject var session: DocSession
    @EnvironmentObject var app: AppController
    @State private var colorFilter: UInt32? = nil
    @State private var editing: TextMark? = nil
    @State private var selecting = false
    @State private var selected: [String] = []
    @State private var tableTarget: String? = nil
    @State private var tableCols = 2
    @State private var editingTable: TableEdit? = nil
    @State private var cardFrom: CardDraft? = nil
    @State private var exportURL: URL? = nil

    struct TableEdit: Identifiable { let id = UUID(); let key: String; let rows: [[String]]; let header: Bool }
    struct CardDraft: Identifiable { let id = UUID(); let front: String; let back: String }

    var body: some View {
        let s = session
        let all = s.allMarks()
        let blocks = Summary.display(all, tidy: app.summaryTidy, edits: s.summaryEdits, filter: colorFilter, byColor: app.summaryByColor) { app.labelOf($0) }
        var used: [UInt32] = []
        for m in all where !used.contains(m.color) { used.append(m.color) }
        let docCards = app.cards.filter { $0.docId == s.id }
        return VStack(spacing: 0) {
            HStack(spacing: 6) {
                PhIcon("ph_summary_fill", Palette.navy, 24)
                    .overlay(Circle().fill(Palette.gold).frame(width: 7, height: 7).offset(x: 9, y: -9))
                VStack(alignment: .leading, spacing: 0) {
                    Text(L("ملخصات", "Summaries")).font(Fonts.display(22)).foregroundColor(Palette.navy)
                    Text(s.title).font(Fonts.sans(12)).foregroundColor(Palette.textSecondary).lineLimit(1)
                }
                .padding(.leading, 4)
                Spacer(minLength: 4)
                Button { if !all.isEmpty { s.mapOpen = true } } label: {
                    HStack(spacing: 5) {
                        PhIcon("ph_mindmap", Palette.goldDeep, 16)
                        Text(L("خريطة", "Map")).font(Fonts.sans(13, .bold)).foregroundColor(Palette.goldDeep)
                    }
                    .padding(.horizontal, 11).padding(.vertical, 6)
                    .background(Capsule().fill(LinearGradient(colors: [Color(hex: 0xFFFFF6DD), Palette.goldSoft], startPoint: .leading, endPoint: .trailing)))
                    .overlay(Capsule().stroke(Palette.gold, lineWidth: 1))
                }
                Button { app.openDeck = "doc:" + s.id } label: {
                    HStack(spacing: 6) {
                        PhIcon("ph_books", Palette.goldDeep, 16)
                        Text(L("بطاقات \(docCards.count)", "Cards \(docCards.count)")).font(Fonts.sans(13, .bold)).foregroundColor(Palette.goldDeep)
                    }
                    .padding(.horizontal, 12).padding(.vertical, 6)
                    .overlay(Capsule().stroke(Palette.gold, lineWidth: 1))
                }
                Button { s.summaryOpen = false } label: { PhIcon("ph_close", Palette.textSecondary, 20, L("إغلاق الملخصات", "Close summaries")).frame(width: 40, height: 40) }
            }
            .padding(.leading, 16).padding(.trailing, 6).padding(.top, 12)
            HStack(spacing: 6) {
                ChoiceChip(label: L("مرتّب", "Tidy"), selected: app.summaryTidy) { app.summaryTidy = true }
                ChoiceChip(label: L("كما التُقط", "As captured"), selected: !app.summaryTidy) { app.summaryTidy = false }
                Spacer()
                Button(L("نسخ الكل", "Copy all")) {
                    UIPasteboard.general.string = Summary.plainText(s.title, blocks)
                    app.message(L("تم النسخ", "Copied"))
                }
                .font(Fonts.sans(13)).foregroundColor(Palette.navy).disabled(blocks.isEmpty)
                Button("PDF") { s.flush(); exportURL = Exporter.summaryPDF(s) }
                    .font(Fonts.sans(13)).foregroundColor(Palette.navy).disabled(blocks.isEmpty).padding(.horizontal, 8)
            }
            .padding(.horizontal, 12).padding(.vertical, 8)
            if used.count > 1 || colorFilter != nil {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ChoiceChip(label: L("كل الألوان", "All colors"), selected: colorFilter == nil) { colorFilter = nil }
                        ForEach(used, id: \.self) { col in
                            let sel = colorFilter == col
                            Button { colorFilter = sel ? nil : col } label: {
                                HStack(spacing: 6) {
                                    RoundedRectangle(cornerRadius: 6).fill(Color(hex: col)).frame(width: 12, height: 12)
                                    Text(app.labelOf(col)).font(Fonts.sans(13)).foregroundColor(sel ? .white : Palette.textPrimary).lineLimit(1).fixedSize()
                                }
                                .padding(.horizontal, 12).padding(.vertical, 7)
                                .background(Capsule().fill(sel ? Palette.navy : Color.white))
                                .overlay(Capsule().stroke(sel ? Palette.navy : Palette.divider, lineWidth: 1))
                            }
                        }
                        ChoiceChip(label: L("تجميع حسب اللون", "Group by color"), selected: app.summaryByColor) { app.summaryByColor.toggle() }
                    }
                    .padding(.horizontal, 12)
                }
                .padding(.bottom, 6)
            }
            Rectangle().fill(Palette.gold.opacity(0.45)).frame(height: 1)
            if selecting { tableBar(blocks) }
            if blocks.isEmpty {
                VStack(spacing: 8) {
                    Text(L("لا توجد ملخصات بعد", "No summaries yet")).font(Fonts.sans(16, .bold)).foregroundColor(Palette.textPrimary)
                    Text(L("اختر الهايلايتر، واضغط عليه مرة ثانية وفعّل «التقاط النص»، ثم مرّر على أي سطر. ", "Pick the highlighter, tap it again and turn on “Text snap”, then run it over any line. ") +
                         L("ظلّل نفس النص مرتين ليظهر مهمًا بخط عريض.\nيعمل مع العربية والإنجليزية في ملفات PDF الرقمية.", "Highlight the same text twice to make it important in bold.\nWorks with Arabic and English in digital PDFs."))
                        .font(Fonts.sans(13)).foregroundColor(Palette.textSecondary).lineSpacing(5)
                }
                .padding(24).frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        UnevenRoundedRectangle(topLeadingRadius: 8, bottomLeadingRadius: 0, bottomTrailingRadius: 0, topTrailingRadius: 8)
                            .fill(Palette.ivory).frame(height: 18).shadow(color: .black.opacity(0.08), radius: 1, y: -1)
                        ForEach(Array(blocks.enumerated()), id: \.offset) { _, b in
                            blockRow(b, shown: blocks.map { $0.key })
                                .padding(.horizontal, 14)
                                .background(Palette.ivory)
                        }
                        UnevenRoundedRectangle(topLeadingRadius: 0, bottomLeadingRadius: 8, bottomTrailingRadius: 8, topTrailingRadius: 0)
                            .fill(Palette.ivory).frame(height: 22)
                    }
                    .padding(14)
                }
            }
        }
        .frame(width: 420)
        .frame(maxHeight: .infinity)
        .background(Palette.drawerBg.shadow(color: .black.opacity(0.18), radius: 14))
        .sheet(item: $editing) { m in MarkEditSheet(session: s, mark: m) }
        .sheet(item: $editingTable) { t in TableEditorSheet(rows: t.rows, header: t.header) { rows, header in s.saveTable(t.key, rows: rows, header: header) } }
        .sheet(item: $cardFrom) { d in
            CardEditorSheet(card: Card(id: UUID().uuidString, front: d.front, back: d.back, folder: app.entry(s.id)?.folder ?? "", docId: s.id, source: s.title),
                            folder: app.entry(s.id)?.folder ?? "")
                .environmentObject(app)
        }
        .sheet(item: Binding(get: { exportURL.map { IdURL(url: $0) } }, set: { exportURL = $0?.url })) { u in ShareSheet(items: [u.url]) }
    }

    // ---------- وضع اختيار النصوص لصنع جدول ----------

    private func tableBar(_ blocks: [SBlock]) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            let target = tableTarget
            Text(target != nil && selected.isEmpty ? L("اختر النصوص التي تريد ضمها إلى الجدول", "Choose the texts to add to the table")
                 : target != nil ? L("اخترت \(selected.count)، وستُضاف كصفوف في آخر الجدول", "\(selected.count) selected; they will be added as rows at the end of the table")
                 : selected.isEmpty ? L("اختر النصوص بالترتيب الذي تريده في الجدول", "Choose texts in the order you want in the table")
                 : L("اخترت \(selected.count). النصوص تملأ الصفوف بالترتيب حسب عدد الأعمدة", "\(selected.count) selected. Texts fill the rows in order by column count"))
                .font(Fonts.sans(13, .bold)).foregroundColor(Palette.navy)
            if target == nil {
                HStack(spacing: 6) {
                    Text(L("الأعمدة:", "Columns:")).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary)
                    ForEach(1...4, id: \.self) { n in ChoiceChip(label: "\(n)", selected: tableCols == n) { tableCols = n } }
                }
            }
            HStack {
                if let t = target {
                    Button(L("إضافة إلى الجدول", "Add to table")) {
                        let rows: [[String]] = selected.compactMap { k in blocks.first { $0.key == k } }.flatMap { b -> [[String]] in
                            switch b {
                            case .table(let r, _, _, _): return r
                            case .heading(let t, _, _): return [[t]]
                            case .para(let t, _, _, _): return [t.components(separatedBy: "|").map { $0.trimmingCharacters(in: .whitespaces) }]
                            }
                        }
                        session.addToTable(t, keys: selected, newRows: rows)
                        endSelecting()
                    }
                    .disabled(selected.isEmpty)
                } else {
                    Button(L("إنشاء الجدول", "Create table")) {
                        session.makeTable(selected, cols: tableCols)
                        endSelecting()
                    }
                    .disabled(selected.isEmpty)
                }
                Spacer()
                Button(L("إلغاء", "Cancel"), action: endSelecting).foregroundColor(Palette.textSecondary)
            }
            .font(Fonts.sans(13, .bold))
        }
        .padding(.horizontal, 14).padding(.vertical, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.navySoft)
    }

    private func endSelecting() {
        selecting = false
        selected = []
        tableTarget = nil
    }

    // ---------- جزء من الملخص ----------

    @ViewBuilder
    private func blockRow(_ b: SBlock, shown: [String]) -> some View {
        let isSel = selected.contains(b.key)
        let selIndex = selected.firstIndex(of: b.key)
        let content = VStack(alignment: .leading, spacing: 2) {
            if selecting, let i = selIndex {
                Text("\(i + 1)").font(Fonts.sans(11, .bold)).foregroundColor(.white)
                    .padding(.horizontal, 7).padding(.vertical, 1).background(RoundedRectangle(cornerRadius: 8).fill(Palette.goldDeep))
            }
            if selecting && b.key == tableTarget {
                Text(L("الجدول الذي تضم إليه", "Table you are adding to")).font(Fonts.sans(11)).foregroundColor(Palette.goldDeep)
            }
            switch b {
            case .heading(let t, _, _):
                Text(t).font(Fonts.display(20)).foregroundColor(Palette.navy).lineSpacing(10).padding(.top, 10)
            case .para(let t, let e, _, _):
                Text(emphasized(t, e)).font(Fonts.sans(15)).foregroundColor(Palette.textPrimary).lineSpacing(10)
            case .table(let rows, _, _, let header):
                TableView(rows: rows, header: header)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 5)
        .padding(.horizontal, 6)
        .background(RoundedRectangle(cornerRadius: 8).fill(selecting && isSel ? Palette.goldSoft : Color.clear))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(selecting ? (isSel ? Palette.gold : Palette.divider) : Color.clear, lineWidth: 1))
        .contentShape(Rectangle())
        if selecting {
            content.onTapGesture {
                guard b.key != tableTarget else { return }
                if isSel { selected.removeAll { $0 == b.key } } else { selected.append(b.key) }
            }
        } else {
            Menu { blockMenu(b, shown: shown) } label: { content }
                .buttonStyle(.plain)
        }
    }

    @ViewBuilder
    private func blockMenu(_ b: SBlock, shown: [String]) -> some View {
        let s = session
        let edits = s.summaryEdits
        let userTable = b.key.hasPrefix("g:")
        let overridden = userTable || edits.kinds[b.key] != nil || edits.order.contains(b.key)
        let pos = shown.firstIndex(of: b.key) ?? -1
        let first = b.marks.first
        let isTable: Bool = { if case .table = b { return true } else { return false } }()
        Section {
            switch b {
            case .heading: Button { s.setBlockKind(b.key, "p") } label: { Label { Text(L("نص عادي", "Plain text")) } icon: { Image("ph_text") } }
            case .para: Button { s.setBlockKind(b.key, "h") } label: { Label { Text(L("اجعله عنوانًا", "Make heading")) } icon: { Image("ph_text") } }
            case .table:
                if !userTable { Button { s.setBlockKind(b.key, "p") } label: { Label { Text(L("تحويل إلى نص", "Convert to text")) } icon: { Image("ph_text") } } }
            }
            if case let .table(rows, _, _, header) = b {
                Button { editingTable = TableEdit(key: b.key, rows: rows, header: header) } label: { Label { Text(L("تحرير الجدول", "Edit table")) } icon: { Image("ph_sliders") } }
                Button { selecting = true; tableTarget = b.key; selected = [] } label: { Label { Text(L("ضم نصوص إلى هذا الجدول", "Add texts to this table")) } icon: { Image("ph_plus") } }
            }
            if userTable {
                Button { s.removeTable(b.key) } label: { Label { Text(L("فك الجدول", "Unmake table")) } icon: { Image("ph_close") } }
            } else if !isTable {
                Button { selecting = true; tableTarget = nil; selected = [b.key] } label: { Label { Text(L("ضعه في جدول…", "Put in a table…")) } icon: { Image("ph_pages") } }
            }
            if pos > 0 { Button { s.moveBlock(shown, b.key, -1) } label: { Label { Text(L("تحريك لأعلى", "Move up")) } icon: { Image("ph_caret_up") } } }
            if pos >= 0 && pos < shown.count - 1 { Button { s.moveBlock(shown, b.key, 1) } label: { Label { Text(L("تحريك لأسفل", "Move down")) } icon: { Image("ph_caret_down") } } }
            if overridden { Button { s.resetBlock(b.key) } label: { Label { Text(L("كما اكتشفه البرنامج", "As detected")) } icon: { Image("ph_recent") } } }
        }
        Section {
            if let f = first {
                Button { if let i = s.index(of: f.page) { s.goTo(page: i) } } label: { Label { Text(L("انتقال إلى الموضع", "Go to location")) } icon: { Image("ph_search") } }
                Button { editing = f } label: { Label { Text(L("تعديل النص", "Edit text")) } icon: { Image("ph_note") } }
            }
            Button {
                if Premium.shared.require(.cards) {
                    let body = Summary.plainText("", [b]).trimmingCharacters(in: .whitespacesAndNewlines)
                    if case let .heading(t, _, _) = b { cardFrom = CardDraft(front: t, back: "") } else { cardFrom = CardDraft(front: "", back: body) }
                }
            } label: { Label { Text(L("اجعله بطاقة", "Make it a card")) } icon: { Image("ph_cards") } }
            Button {
                UIPasteboard.general.string = Summary.plainText("", [b]).trimmingCharacters(in: .whitespacesAndNewlines)
                app.message(L("تم النسخ", "Copied"))
            } label: { Label { Text(L("نسخ", "Copy")) } icon: { Image("ph_copy") } }
            Button(role: .destructive) { b.marks.forEach { s.deleteMark($0.id) } } label: { Label { Text(L("حذف التمييز", "Delete highlight")) } icon: { Image("ph_trash") } }
        }
    }

    /// نص الفقرة مع إبراز الأجزاء المهمة بخط عريض وتظليل ذهبي فاتح.
    private func emphasized(_ text: String, _ emphasis: [String]) -> AttributedString {
        var a = AttributedString(text)
        for r in Summary.emphasisRanges(text, emphasis) {
            guard let lo = AttributedString.Index(r.lowerBound, within: a), let hi = AttributedString.Index(r.upperBound, within: a) else { continue }
            a[lo..<hi].font = Fonts.sans(15, .heavy)
            a[lo..<hi].backgroundColor = Palette.goldSoft
        }
        return a
    }
}

/// جدول في الملخص.
struct TableView: View {
    let rows: [[String]]
    var header = true
    var body: some View {
        let cols = rows.map { $0.count }.max() ?? 1
        VStack(spacing: 0) {
            ForEach(0..<rows.count, id: \.self) { ri in
                HStack(spacing: 0) {
                    ForEach(0..<cols, id: \.self) { ci in
                        Text(ci < rows[ri].count ? rows[ri][ci] : "")
                            .font(Fonts.sans(12, header && ri == 0 ? .bold : .regular))
                            .foregroundColor(Palette.textPrimary).lineSpacing(5)
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                            .padding(.horizontal, 6).padding(.vertical, 5)
                            .border(Palette.divider, width: 0.5)
                    }
                }
                .fixedSize(horizontal: false, vertical: true)
                .background(header && ri == 0 ? Palette.navySoft : Color.clear)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 6))
        .overlay(RoundedRectangle(cornerRadius: 6).stroke(Palette.divider, lineWidth: 1))
        .padding(.vertical, 4)
    }
}

/// تحرير جدول: الخلايا، إضافة صف أو عمود، وصف العناوين.
struct TableEditorSheet: View {
    @State var rows: [[String]]
    @State var header: Bool
    let onSave: ([[String]], Bool) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let cols = max(1, rows.map { $0.count }.max() ?? 1)
        SheetScaffold(title: L("تحرير الجدول", "Edit table")) {
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    ForEach(0..<rows.count, id: \.self) { ri in
                        HStack(spacing: 4) {
                            ForEach(0..<cols, id: \.self) { ci in
                                TextField("", text: Binding(get: { ci < rows[ri].count ? rows[ri][ci] : "" }, set: { v in
                                    while rows[ri].count <= ci { rows[ri].append("") }
                                    rows[ri][ci] = v
                                }), axis: .vertical)
                                .font(Fonts.sans(13, header && ri == 0 ? .bold : .regular))
                                .padding(6).background(RoundedRectangle(cornerRadius: 6).fill(header && ri == 0 ? Palette.navySoft : Color.white))
                            }
                            Button { rows.remove(at: ri) } label: { PhIcon("ph_trash", Palette.danger, 16) }
                        }
                    }
                    HStack(spacing: 8) {
                        ChoiceChip(label: "+ " + L("صف", "Row"), selected: false) { rows.append(Array(repeating: "", count: cols)) }
                        ChoiceChip(label: "+ " + L("عمود", "Column"), selected: false) { rows = rows.map { $0 + [""] } }
                        Toggle(isOn: $header) { Text(L("الصف الأول عناوين", "First row is a header")).font(Fonts.sans(13)) }.tint(Palette.navy)
                    }
                    HStack {
                        Spacer()
                        NavyButton(label: L("إلغاء", "Cancel"), filled: false) { dismiss() }
                        NavyButton(label: L("حفظ", "Save")) { onSave(rows, header); dismiss() }
                    }
                }
                .padding(.horizontal, 22).padding(.bottom, 20)
            }
        }
    }
}

struct IdURL: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}

/// الترجمة على الجهاز (iPadOS 17.4 وما بعده).
struct TranslateModifier: ViewModifier {
    let text: String
    @Binding var isPresented: Bool
    func body(content: Content) -> some View {
        if #available(iOS 17.4, *) {
            content.translationPresentation(isPresented: $isPresented, text: text)
        } else {
            content.onChange(of: isPresented) { v in
                guard v else { return }
                isPresented = false
                let q = text.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
                if let u = URL(string: "https://translate.google.com/?sl=auto&tl=ar&text=\(q)") { UIApplication.shared.open(u) }
            }
        }
    }
}

/// تعديل نص التمييز.
struct MarkEditSheet: View {
    @ObservedObject var session: DocSession
    let mark: TextMark
    @State private var text = ""
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        SheetScaffold(title: L("تعديل النص", "Edit text")) {
            VStack(alignment: .leading, spacing: 10) {
                Text(L("سطر يبدأ بـ # يصبح عنوانًا. خلايا الجدول يفصلها |. سطر فارغ يبدأ فقرة جديدة.", "A line starting with # becomes a heading. Table cells are separated by |. A blank line starts a new paragraph."))
                    .font(Fonts.sans(12)).foregroundColor(Palette.textSecondary)
                TextEditor(text: $text).font(Fonts.sans(15)).frame(minHeight: 200)
                    .padding(8).background(RoundedRectangle(cornerRadius: 12).fill(Color.white)).roundedBorder(Palette.divider, radius: 12)
                HStack {
                    NavyButton(label: L("ترتيب النص", "Tidy text"), filled: false) { text = TextTidy.tidyEditable(text) }
                    Spacer()
                    NavyButton(label: L("إلغاء", "Cancel"), filled: false) { dismiss() }
                    NavyButton(label: L("حفظ", "Save")) { session.updateMark(mark.id) { $0.text = text }; dismiss() }
                }
            }
            .padding(.horizontal, 22).padding(.bottom, 16)
        }
        .onAppear { text = mark.text }
    }
}
