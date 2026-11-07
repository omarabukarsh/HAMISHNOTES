import SwiftUI

// البطاقات (نفس Flashcards.kt في أندرويد): بطاقات تقلبها للمذاكرة، تصفية حسب المادة والصعبة،
// خلط، وتعديل وحذف ونجمة لكل بطاقة. البطاقة: عنوان (الوجه) ومحتوى (الظهر).

/// مراجعة بطاقات: deck = nil لكل البطاقات، "doc:<id>" لبطاقات مستند، وإلا بطاقات مادة.
struct DeckView: View {
    @EnvironmentObject var app: AppController
    let deck: String?
    @State private var subject: String? = nil   // nil = الكل، "" = بلا مادة
    @State private var hardOnly = false
    @State private var shuffled: [String]? = nil
    @State private var flipped: Set<String> = []
    @State private var page = 0
    @State private var adding = false
    @State private var editing: Card? = nil
    @Environment(\.dismiss) private var dismiss

    private func folderOf(_ c: Card) -> String {
        if !c.folder.isEmpty { return c.folder }
        if let d = c.docId { return app.entry(d)?.folder ?? "" }
        return ""
    }

    private var loaded: [Card] {
        guard let d = deck else { return app.cards }
        if d.hasPrefix("doc:") { let id = String(d.dropFirst(4)); return app.cards.filter { $0.docId == id } }
        return app.cards.filter { folderOf($0) == d }
    }

    private func source(_ c: Card) -> String {
        let doc = c.docId.flatMap { app.entry($0)?.name }
        let f = folderOf(c)
        return (doc ?? L("بلا ملف", "No file")) + (f.isEmpty ? "" : " · \(f)")
    }

    private var filtered: [Card] {
        loaded.filter { (subject == nil || folderOf($0) == subject) && (!hardOnly || $0.starred) }
    }

    private var ordered: [Card] {
        let base = filtered
        guard let order = shuffled else { return base }
        let head: [Card] = order.compactMap { id in base.first { $0.id == id } }
        let tail: [Card] = base.filter { !order.contains($0.id) }
        return head + tail
    }

    private var title: String {
        guard let d = deck else { return L("كل البطاقات", "All cards") }
        if d.hasPrefix("doc:") {
            let n = app.entry(String(d.dropFirst(4)))?.name ?? ""
            return L("بطاقات \(n)", "\(n) cards")
        }
        return L("بطاقات \(d)", "\(d) cards")
    }

    private func subjects(_ all: [Card]) -> [String] {
        let used = Array(Set(all.map(folderOf).filter { !$0.isEmpty }))
        let known: [String] = app.folders.filter { used.contains($0) }
        let extra: [String] = used.filter { !app.folders.contains($0) }.sorted()
        return known + extra
    }

    var body: some View {
        let all = loaded
        let cards = ordered
        VStack(alignment: .leading, spacing: 0) {
            header(all: all, cards: cards)
            Spacer().frame(height: 8)
            chips(all: all)
            Spacer().frame(height: 12)
            if cards.isEmpty {
                emptyText
            } else {
                pager(cards)
                Spacer().frame(height: 14)
                navRow(cards.count)
                Text(L("انقر البطاقة لقلبها، واسحب للتنقل", "Tap a card to flip it, swipe to move"))
                    .font(Fonts.sans(12)).foregroundColor(Palette.textSecondary).frame(maxWidth: .infinity)
            }
            Spacer(minLength: 0)
        }
        .padding(20)
        // نافذة بحواف دائرية (24) كما في أندرويد
        .background(RoundedRectangle(cornerRadius: 24, style: .continuous).fill(Palette.background))
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .environment(\.colorScheme, .light)
        .dialogSheet(isPresented: $adding) { addSheet }
        .dialogSheet(item: $editing) { c in
            CardEditorSheet(card: c, folder: c.folder, title: L("تعديل البطاقة", "Edit card"), confirm: L("حفظ", "Save")).environmentObject(app)
        }
    }

    @ViewBuilder private var addSheet: some View {
        if let d = deck, d.hasPrefix("doc:") {
            CardEditorSheet(card: Card(id: UUID().uuidString, front: "", back: "", docId: String(d.dropFirst(4))), folder: "").environmentObject(app)
        } else {
            NewLooseCardSheet(initialFolder: deck ?? subject ?? "").environmentObject(app)
        }
    }

    private func header(all: [Card], cards: [Card]) -> some View {
        let sub: String = deck == nil
            ? L("كل بطاقات التطبيق، حتى التي بلا ملف", "Every card in the app, including those without a file")
            : L("بطاقات مستندات هذه المادة وبطاقاتها بلا ملف فقط", "Only this subject’s documents and its cards without a file")
        return HStack(spacing: 4) {
            VStack(alignment: .leading, spacing: 1) {
                Text(title + " · \(all.count)").font(Fonts.display(20)).foregroundColor(Palette.navy).lineLimit(1)
                Text(sub).font(Fonts.sans(12)).foregroundColor(Palette.textSecondary).lineLimit(1)
            }
            Spacer()
            Button { adding = true } label: {
                HStack(spacing: 4) {
                    PhIcon("ph_plus", Palette.navy, 16)
                    Text(L("بطاقة جديدة", "New card")).font(Fonts.sans(14, .bold)).foregroundColor(Palette.navy)
                }
                .padding(8)
            }
            if cards.count > 1 {
                Button { shuffled = cards.map { $0.id }.shuffled(); restart() } label: {
                    Text(L("خلط", "Shuffle")).font(Fonts.sans(14, .bold)).foregroundColor(Palette.goldDeep).padding(8)
                }
            }
            Button { dismiss() } label: { PhIcon("ph_close", Palette.textSecondary, 20, L("إغلاق", "Close")).frame(width: 40, height: 40) }
        }
    }

    private func chips(all: [Card]) -> some View {
        let starred = all.filter { $0.starred }.count
        let none = all.filter { folderOf($0).isEmpty }.count
        let subs: [String] = deck == nil ? subjects(all) : []
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ChoiceChip(label: L("الكل \(all.count)", "All \(all.count)"), selected: subject == nil && !hardOnly) {
                    subject = nil; hardOnly = false; restart()
                }
                ForEach(subs, id: \.self) { f in
                    subjectChip(f, count: all.filter { folderOf($0) == f }.count)
                }
                if deck == nil && none > 0 {
                    ChoiceChip(label: L("بلا مادة \(none)", "No subject \(none)"), selected: subject == "") {
                        subject = subject == "" ? nil : ""; restart()
                    }
                }
                ChoiceChip(label: L("الصعبة ★ \(starred)", "Hard ★ \(starred)"), selected: hardOnly) { hardOnly.toggle(); restart() }
            }
        }
    }

    private func subjectChip(_ f: String, count: Int) -> some View {
        ChoiceChip(label: "\(f) \(count)", selected: subject == f) { subject = subject == f ? nil : f; restart() }
    }

    private var emptyText: some View {
        Text(hardOnly ? L("لا توجد بطاقات مميزة بنجمة هنا.", "No starred cards here.")
             : L("لا توجد بطاقات بعد. اضغط «بطاقة جديدة»، أو اصنعها من الملخصات.", "No cards yet. Tap “New card”, or make them from your summaries."))
            .font(Fonts.sans(15)).foregroundColor(Palette.textSecondary).multilineTextAlignment(.center).lineSpacing(5)
            .frame(maxWidth: .infinity, minHeight: 200)
    }

    private func pager(_ cards: [Card]) -> some View {
        TabView(selection: $page) {
            ForEach(Array(cards.enumerated()), id: \.element.id) { i, c in
                cardPage(c).tag(i)
            }
        }
        .tabViewStyle(.page(indexDisplayMode: .never))
        .frame(height: 320)
    }

    private func cardPage(_ c: Card) -> some View {
        let isFlipped = flipped.contains(c.id)
        return ZStack(alignment: .top) {
            FlipCard(card: c, flipped: isFlipped) {
                if flipped.contains(c.id) { flipped.remove(c.id) } else { flipped.insert(c.id) }
            }
            cardActions(c)
        }
        .padding(.horizontal, 2)
    }

    private func cardActions(_ c: Card) -> some View {
        let tint: Color = c.docId == nil ? CardTeal : Palette.textSecondary
        return HStack {
            Text(source(c)).font(Fonts.sans(11, .bold)).foregroundColor(tint).lineLimit(1)
                .padding(.horizontal, 8).padding(.vertical, 3)
                .background(RoundedRectangle(cornerRadius: 8).fill(Color(hex: 0xFFF1F3F7)))
                .padding(14)
            Spacer()
            Button { editing = c } label: { PhIcon("ph_pencil", Palette.textSecondary, 20, L("تعديل البطاقة", "Edit card")).frame(width: 40, height: 40) }
            Button { app.cards.removeAll { $0.id == c.id } } label: { PhIcon("ph_trash", Palette.textSecondary, 20, L("حذف البطاقة", "Delete card")).frame(width: 40, height: 40) }
            Button { toggleStar(c.id) } label: { PhIcon(c.starred ? "ph_star_fill" : "ph_star", Palette.gold, 24).frame(width: 40, height: 40) }
        }
        .padding(.trailing, 8).padding(.top, 2)
    }

    private func toggleStar(_ id: String) {
        if let k = app.cards.firstIndex(where: { $0.id == id }) { app.cards[k].starred.toggle() }
    }

    private func navRow(_ count: Int) -> some View {
        HStack {
            Button(L("السابقة", "Previous")) { withAnimation { page = max(0, page - 1) } }.disabled(page <= 0)
            Spacer()
            Text("\(min(page, count - 1) + 1) / \(count)").font(Fonts.sans(14, .bold)).foregroundColor(Palette.textSecondary)
            Spacer()
            Button(L("التالية", "Next")) { withAnimation { page = min(count - 1, page + 1) } }.disabled(page >= count - 1)
        }
        .font(Fonts.sans(14)).foregroundColor(Palette.navy)
    }

    private func restart() {
        flipped = []
        page = 0
    }
}

/// بطاقة تنقلب بالنقر: الوجه أبيض بالعنوان، والظهر عاجي بالمحتوى.
private struct FlipCard: View {
    let card: Card
    let flipped: Bool
    let onFlip: () -> Void
    @State private var angle: Double = 0

    var body: some View {
        let showBack = angle > 90
        ZStack {
            RoundedRectangle(cornerRadius: 20, style: .continuous).fill(showBack ? Palette.ivory : Color.white)
            ScrollView {
                VStack(spacing: 0) {
                    if !showBack {
                        Text(card.front).font(Fonts.display(22)).foregroundColor(Palette.navy).multilineTextAlignment(.center).lineSpacing(12)
                    } else {
                        Text(card.front.components(separatedBy: "\n").first ?? "").font(Fonts.sans(13, .bold)).foregroundColor(Palette.goldDeep).multilineTextAlignment(.center)
                        Spacer().frame(height: 10)
                        Rectangle().fill(Palette.gold).frame(width: 48, height: 2)
                        Spacer().frame(height: 12)
                        Text(card.back).font(Fonts.sans(17)).foregroundColor(Palette.textPrimary).multilineTextAlignment(.center).lineSpacing(11)
                    }
                }
                .padding(26)
                .frame(maxWidth: .infinity, minHeight: 300)
            }
            .rotation3DEffect(.degrees(showBack ? 180 : 0), axis: (x: 0, y: 1, z: 0))
        }
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(showBack ? Palette.gold : Palette.divider, lineWidth: 1.5))
        .rotation3DEffect(.degrees(angle), axis: (x: 0, y: 1, z: 0), perspective: 0.4)
        .contentShape(Rectangle())
        .onTapGesture(perform: onFlip)
        .onAppear { angle = flipped ? 180 : 0 }
        .onChange(of: flipped) { f in withAnimation(.easeInOut(duration: 0.38)) { angle = f ? 180 : 0 } }
    }
}

/// إضافة بطاقة أو تعديلها: العنوان (الوجه) والمحتوى (الظهر).
struct CardEditorSheet: View {
    @EnvironmentObject var app: AppController
    let card: Card?
    let folder: String
    var title = L("بطاقة جديدة", "New card")
    var confirm = L("إضافة", "Add")
    @State private var front = ""
    @State private var back = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let ok = !front.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !back.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        // نافذة حوار مثل AddCardDialog في أندرويد
        AlertDialogCard(title: title, width: 480) {
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    OutlinedField(label: L("عنوان البطاقة", "Card title"), text: $front, multiline: true)
                    OutlinedField(label: L("محتوى البطاقة", "Card content"), text: $back, multiline: true, minHeight: 180, autoFocus: false)
                }
            }
            .frame(maxHeight: 460).fixedSize(horizontal: false, vertical: true)
        } buttons: {
            DialogButton(label: L("إلغاء", "Cancel")) { dismiss() }
            DialogButton(label: confirm, enabled: ok) {
                guard Premium.shared.require(.cards) else { return }
                var c = card ?? Card(id: UUID().uuidString, front: "", back: "", folder: folder)
                c.front = front.trimmingCharacters(in: .whitespacesAndNewlines)
                c.back = back.trimmingCharacters(in: .whitespacesAndNewlines)
                if let k = app.cards.firstIndex(where: { $0.id == c.id }) { app.cards[k] = c } else { app.cards.append(c) }
                dismiss()
            }
        }
        .onAppear { front = card?.front ?? ""; back = card?.back ?? "" }
    }
}

/// بطاقة جديدة بلا ملف: العنوان والمحتوى والمادة، و«حفظ وإضافة أخرى».
struct NewLooseCardSheet: View {
    @EnvironmentObject var app: AppController
    let initialFolder: String
    @State private var front = ""
    @State private var back = ""
    @State private var folder = ""
    @State private var added = 0
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let ok = !front.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !back.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        // نافذة حوار مثل NewLooseCardDialog في أندرويد
        AlertDialogCard(title: L("بطاقة جديدة", "New card") + (added > 0 ? L(" · أُضيفت \(added)", " · \(added) added") : ""), width: 480) {
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    OutlinedField(label: L("العنوان (الوجه)", "Title (front)"), text: $front, multiline: true)
                    OutlinedField(label: L("المحتوى (الظهر)", "Content (back)"), text: $back, multiline: true, minHeight: 140, autoFocus: false)
                    Text(L("المادة", "Subject")).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary)
                    SubjectChips(selected: folder) { folder = $0 }
                }
            }
            .frame(maxHeight: 460).fixedSize(horizontal: false, vertical: true)
        } buttons: {
            DialogButton(label: added > 0 ? L("تم", "Done") : L("إلغاء", "Cancel")) { dismiss() }
            DialogButton(label: L("حفظ وإضافة أخرى", "Save & add another"), color: CardTeal, enabled: ok) {
                if save() { added += 1; front = ""; back = "" }
            }
            DialogButton(label: L("حفظ", "Save"), bold: true, enabled: ok) {
                if save() { app.message(L("أُضيفت البطاقة", "Card added")); dismiss() }
            }
        }
        .onAppear { folder = initialFolder }
    }

    private func save() -> Bool {
        guard Premium.shared.require(.cards) else { return false }
        app.addCard(front: front.trimmingCharacters(in: .whitespacesAndNewlines), back: back.trimmingCharacters(in: .whitespacesAndNewlines), folder: folder)
        return true
    }
}
