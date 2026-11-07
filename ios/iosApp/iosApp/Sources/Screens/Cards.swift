import SwiftUI

/// التكرار المتباعد (SM-2 مبسّط).
enum Srs {
    static let day: Int64 = 86_400_000

    static func grade(_ s: SrsState, _ q: Int) -> SrsState {
        var n = s
        let now = nowMs()
        switch q {
        case 0:
            n.reps = 0
            n.interval = 0
            n.ease = max(1.3, s.ease - 0.2)
            n.due = now + 60_000 * 10
        default:
            n.reps += 1
            if n.reps == 1 { n.interval = q == 1 ? 1 : q == 2 ? 1 : 3 }
            else if n.reps == 2 { n.interval = q == 1 ? 2 : q == 2 ? 4 : 6 }
            else { n.interval = max(1, Int((Double(s.interval) * (q == 1 ? 1.2 : q == 2 ? s.ease : s.ease * 1.3)).rounded())) }
            n.ease = max(1.3, s.ease + (q == 1 ? -0.15 : q == 3 ? 0.15 : 0))
            n.due = now + Int64(n.interval) * day
        }
        return n
    }

    static func isDue(_ c: Card) -> Bool { c.srs.due <= nowMs() }
}

/// بطاقات مادة (أو كلها): القائمة، والمذاكرة بالقلب.
struct DeckView: View {
    @EnvironmentObject var app: AppController
    let deck: String?
    @State private var studying = false
    @State private var editing: Card? = nil
    @State private var adding = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let cards = app.cardsIn(deck)
        let due = cards.filter(Srs.isDue)
        SheetScaffold(title: deck.map { L("بطاقات \($0)", "\($0) cards") } ?? L("كل البطاقات", "All cards")) {
            if studying {
                StudyView(cards: due.isEmpty ? cards.shuffled() : due) { studying = false }
            } else {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 10) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(cardsCount(cards.count)).font(Fonts.sans(16, .bold)).foregroundColor(Palette.textPrimary)
                            Text(due.isEmpty ? L("لا شيء مستحق الآن", "Nothing due now") : L("\(due.count) للمراجعة الآن", "\(due.count) due now"))
                                .font(Fonts.sans(13)).foregroundColor(Palette.textSecondary)
                        }
                        Spacer()
                        NavyButton(label: "+ " + L("بطاقة", "Card"), filled: false) { if Premium.shared.require(.cards) { adding = true } }
                        Button { if Premium.shared.require(.cards) { studying = true } } label: {
                            HStack(spacing: 6) {
                                PhIcon("ph_play", .white, 16)
                                Text(L("ذاكر", "Study")).font(Fonts.sans(15, .bold)).foregroundColor(.white)
                            }
                            .padding(.horizontal, 18).padding(.vertical, 11)
                            .background(Capsule().fill(CardTeal))
                        }
                        .disabled(cards.isEmpty)
                        .opacity(cards.isEmpty ? 0.5 : 1)
                    }
                    if cards.isEmpty {
                        VStack(spacing: 8) {
                            PhIcon("ph_cards", CardTeal, 44)
                            Text(L("لا توجد بطاقات بعد", "No cards yet")).font(Fonts.sans(17, .bold)).foregroundColor(Palette.textPrimary)
                            Text(L("أنشئ بطاقة من «جديد»، أو من أي نص في درج الملخصات ← «اجعله بطاقة».", "Create a card from “New”, or from any text in the summaries drawer → “Make it a card”."))
                                .font(Fonts.sans(14)).foregroundColor(Palette.textSecondary).multilineTextAlignment(.center)
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 40)
                    } else {
                        ScrollView {
                            LazyVGrid(columns: [GridItem(.adaptive(minimum: 220), spacing: 12)], spacing: 12) {
                                ForEach(cards) { c in
                                    Button { editing = c } label: {
                                        VStack(alignment: .leading, spacing: 6) {
                                            HStack {
                                                if c.starred { PhIcon("ph_star_fill", Lib.accentDeep, 14) }
                                                Text(c.folder.isEmpty ? L("بدون مادة", "No subject") : c.folder).font(Fonts.sans(11)).foregroundColor(CardTeal)
                                                Spacer()
                                                if Srs.isDue(c) { Circle().fill(Lib.exam).frame(width: 7, height: 7) }
                                            }
                                            Text(c.front.isEmpty ? c.back : c.front).font(Fonts.sans(15, .bold)).foregroundColor(Palette.textPrimary).lineLimit(3)
                                            if !c.front.isEmpty { Text(c.back).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary).lineLimit(2) }
                                        }
                                        .frame(maxWidth: .infinity, minHeight: 110, alignment: .topLeading)
                                        .padding(14)
                                        .background(RoundedRectangle(cornerRadius: 16).fill(Color.white))
                                        .roundedBorder(Palette.divider, radius: 16)
                                    }
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, 22).padding(.bottom, 20)
            }
        }
        .sheet(item: $editing) { c in CardEditorSheet(card: c, folder: c.folder).environmentObject(app) }
        .sheet(isPresented: $adding) { CardEditorSheet(card: nil, folder: deck ?? "").environmentObject(app) }
    }
}

/// المذاكرة: البطاقة تنقلب بالنقر، ثم تقييم التذكّر.
private struct StudyView: View {
    @EnvironmentObject var app: AppController
    let cards: [Card]
    let onDone: () -> Void
    @State private var i = 0
    @State private var flipped = false

    var body: some View {
        VStack(spacing: 18) {
            if i >= cards.count {
                VStack(spacing: 10) {
                    Text("🎉").font(.system(size: 54))
                    Text(L("أنهيت المراجعة", "Review finished")).font(Fonts.display(22)).foregroundColor(Palette.navy)
                    NavyButton(label: L("رجوع", "Back"), action: onDone)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                let c = cards[i]
                Text("\(i + 1) / \(cards.count)").font(Fonts.sans(13)).foregroundColor(Palette.textSecondary)
                ZStack {
                    face(c.front.isEmpty ? L("تذكّر…", "Recall…") : c.front, back: false).opacity(flipped ? 0 : 1)
                    face(c.back, back: true).opacity(flipped ? 1 : 0).rotation3DEffect(.degrees(180), axis: (x: 0, y: 1, z: 0))
                }
                .rotation3DEffect(.degrees(flipped ? 180 : 0), axis: (x: 0, y: 1, z: 0))
                .animation(.spring(response: 0.45, dampingFraction: 0.8), value: flipped)
                .onTapGesture { flipped.toggle() }
                if flipped {
                    HStack(spacing: 10) {
                        grade(L("مرة أخرى", "Again"), 0, Lib.exam)
                        grade(L("صعب", "Hard"), 1, Color(hex: 0xFFB5651D))
                        grade(L("جيد", "Good"), 2, CardTeal)
                        grade(L("سهل", "Easy"), 3, Palette.navy)
                    }
                } else {
                    Text(L("انقر البطاقة لترى الجواب", "Tap the card to see the answer")).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary)
                }
            }
        }
        .padding(22)
    }

    private func face(_ t: String, back: Bool) -> some View {
        Text(t).font(Fonts.sans(back ? 18 : 22, back ? .regular : .bold)).foregroundColor(back ? Palette.textPrimary : .white)
            .multilineTextAlignment(.center).lineSpacing(6)
            .padding(30)
            .frame(maxWidth: 560, minHeight: 300)
            .background(RoundedRectangle(cornerRadius: 26).fill(back ? Color.white : CardTeal))
            .roundedBorder(back ? Palette.divider : Color.clear, radius: 26)
            .shadow(color: .black.opacity(0.12), radius: 12, y: 6)
    }

    private func grade(_ label: String, _ q: Int, _ col: Color) -> some View {
        Button {
            let c = cards[i]
            if let k = app.cards.firstIndex(where: { $0.id == c.id }) { app.cards[k].srs = Srs.grade(c.srs, q) }
            flipped = false
            i += 1
        } label: {
            Text(label).font(Fonts.sans(15, .bold)).foregroundColor(.white).padding(.horizontal, 18).padding(.vertical, 12)
                .background(Capsule().fill(col))
        }
    }
}

/// إنشاء بطاقة أو تعديلها.
struct CardEditorSheet: View {
    @EnvironmentObject var app: AppController
    let card: Card?
    let folder: String
    @State private var front = ""
    @State private var back = ""
    @State private var sub = ""
    @State private var starred = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let isNew = card == nil || !app.cards.contains { $0.id == card!.id }
        SheetScaffold(title: isNew ? L("بطاقة جديدة", "New card") : L("تعديل البطاقة", "Edit card")) {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text(L("الوجه (السؤال)", "Front (question)")).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary)
                    FieldBox(placeholder: L("مثلًا: ما آلية عمل البنسلين؟", "e.g. How does penicillin work?"), text: $front, multiline: true)
                    Text(L("الظهر (الجواب)", "Back (answer)")).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary)
                    FieldBox(placeholder: L("الجواب", "The answer"), text: $back, multiline: true)
                    Text(L("المادة", "Subject")).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary)
                    SubjectChips(selected: sub) { sub = $0 }
                    Toggle(isOn: $starred) { Text(L("مميزة بنجمة", "Starred")).font(Fonts.sans(14)) }.tint(Palette.navy)
                    if let src = card?.source, !src.isEmpty { Text(src).font(Fonts.sans(12)).foregroundColor(Palette.textSecondary) }
                    HStack {
                        if !isNew, let c = card {
                            NavyButton(label: L("حذف", "Delete"), filled: false, danger: true) { app.cards.removeAll { $0.id == c.id }; dismiss() }
                        }
                        Spacer()
                        NavyButton(label: L("إلغاء", "Cancel"), filled: false) { dismiss() }
                        NavyButton(label: L("حفظ البطاقة", "Save card")) {
                            guard !(front + back).trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
                            if Premium.shared.require(.cards) {
                                var c = card ?? Card(id: UUID().uuidString, front: "", back: "")
                                c.front = front; c.back = back; c.folder = sub; c.starred = starred
                                if let k = app.cards.firstIndex(where: { $0.id == c.id }) { app.cards[k] = c } else { app.cards.append(c) }
                                if !sub.isEmpty { app.addFolder(sub) }
                                dismiss()
                            }
                        }
                    }
                }
                .padding(.horizontal, 22).padding(.bottom, 20)
            }
        }
        .onAppear {
            front = card?.front ?? ""
            back = card?.back ?? ""
            sub = card?.folder ?? folder
            starred = card?.starred ?? false
        }
    }
}
