import SwiftUI
import VisionKit
import PDFKit

/// غلاف النوافذ: خلفية عاجية، عنوان، وزر إغلاق.
struct SheetScaffold<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(title).font(Fonts.display(20)).foregroundColor(Palette.navy)
                Spacer()
                CloseButton { dismiss() }
            }
            .padding(.horizontal, 22).padding(.top, 20).padding(.bottom, 12)
            content()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Palette.ivory.ignoresSafeArea())
    }
}

/// اسم واحد (إعادة تسمية، مادة جديدة): نافذة حوار مثل NameDialog في أندرويد.
struct NameSheet: View {
    let title: String
    let initial: String
    let onSave: (String) -> Void
    @State private var name = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let ok = !name.trimmingCharacters(in: .whitespaces).isEmpty
        AlertDialogCard(title: title, width: 360) {
            OutlinedField(label: L("الاسم", "Name"), text: $name)
                .onSubmit { if ok { onSave(name); dismiss() } }
        } buttons: {
            DialogButton(label: L("إلغاء", "Cancel")) { dismiss() }
            DialogButton(label: L("حفظ", "Save"), enabled: ok) { onSave(name); dismiss() }
        }
        .onAppear { name = initial }
    }
}

/// نقل مستند إلى مادة موجودة أو جديدة، أو إزالته من مادته.
struct MoveToFolderSheet: View {
    @EnvironmentObject var app: AppController
    let entries: [DocEntry]
    let onDone: () -> Void
    @State private var newName = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let one = entries.count == 1 ? entries.first : nil
        let cur: String? = Set(entries.map { $0.folder }).count == 1 ? entries.first?.folder : nil
        SheetScaffold(title: one != nil ? L("نقل «\(one!.name)» إلى مادة", "Move “\(one!.name)” to a subject")
                      : L("نقل \(entries.count) مستندات إلى مادة", "Move \(entries.count) documents to a subject")) {
            VStack(alignment: .leading, spacing: 14) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ChoiceChip(label: L("بدون مادة", "No subject"), selected: cur == "") { move("") }
                        ForEach(app.folders, id: \.self) { f in ChoiceChip(label: f, selected: cur == f) { move(f) } }
                    }
                }
                FieldBox(placeholder: L("أو اكتب اسم مادة جديدة", "or type a new subject name"), text: $newName)
                HStack {
                    Spacer()
                    NavyButton(label: L("إلغاء", "Cancel"), filled: false) { dismiss() }
                    NavyButton(label: L("نقل", "Move")) { if !newName.trimmingCharacters(in: .whitespaces).isEmpty { move(newName.trimmingCharacters(in: .whitespaces)) } }
                }
            }
            .padding(.horizontal, 22)
        }
        .presentationDetents([.height(300)])
    }

    private func move(_ f: String) {
        entries.forEach { app.moveToFolder($0.id, f) }
        onDone()
        dismiss()
    }
}

/// تخصيص غلاف الدفتر: اللون والملصق.
struct CoverSheet: View {
    @EnvironmentObject var app: AppController
    let entry: DocEntry
    @State private var color: UInt32 = 0
    @State private var sticker = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        var preview = entry
        preview.coverColor = color
        preview.sticker = sticker
        return SheetScaffold(title: L("غلاف الدفتر", "Notebook cover")) {
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    NotebookCover(entry: preview).frame(width: 120).frame(maxWidth: .infinity)
                    Text(L("اللون", "Color")).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary)
                    HStack(spacing: 6) {
                        ForEach(CoverColors, id: \.self) { c in
                            Button { color = c } label: {
                                Circle().fill(Color(hex: c)).aspectRatio(1, contentMode: .fit)
                                    .overlay(Circle().stroke(Lib.accent, lineWidth: c == color ? 3 : 0))
                            }
                        }
                    }
                    Text(L("الملصق", "Sticker")).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary).padding(.top, 6)
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 4), count: 7), spacing: 4) {
                        ForEach([""] + CoverStickers, id: \.self) { st in
                            Button { sticker = st } label: {
                                ZStack {
                                    RoundedRectangle(cornerRadius: 10).fill(st == sticker ? Lib.accentSoft : Color.clear)
                                    if st.isEmpty { Text(L("بدون", "None")).font(Fonts.sans(10)).foregroundColor(Palette.textSecondary) }
                                    else { StickerIcon(key: st, size: 30) }
                                }
                                .aspectRatio(1, contentMode: .fit)
                            }
                        }
                    }
                    HStack {
                        Spacer()
                        NavyButton(label: L("إلغاء", "Cancel"), filled: false) { dismiss() }
                        NavyButton(label: L("حفظ", "Save")) { app.setCover(entry.id, color: color, sticker: sticker); dismiss() }
                    }
                    .padding(.top, 8)
                }
                .padding(.horizontal, 22).padding(.bottom, 22)
            }
        }
        .onAppear {
            color = entry.coverColor != 0 ? entry.coverColor : 0xFF2F4B7C
            sticker = entry.sticker
        }
    }
}

/// خريطة ذهنية جديدة بلا ملف: الاسم والمادة.
struct NewMapSheet: View {
    @EnvironmentObject var app: AppController
    let initialFolder: String
    @State private var name = ""
    @State private var folder = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        SheetScaffold(title: L("خريطة ذهنية جديدة", "New mind map")) {
            VStack(alignment: .leading, spacing: 12) {
                FieldBox(placeholder: L("الاسم (المركز)", "Name (center)"), text: $name)
                Text(L("المادة", "Subject")).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary)
                SubjectChips(selected: folder) { folder = $0 }
                HStack {
                    Spacer()
                    NavyButton(label: L("إلغاء", "Cancel"), filled: false) { dismiss() }
                    NavyButton(label: L("إنشاء", "Create")) {
                        let n = name.trimmingCharacters(in: .whitespaces)
                        guard !n.isEmpty else { return }
                        dismiss()
                        app.createMap(n, folder: folder)
                    }
                }
            }
            .padding(.horizontal, 22)
        }
        .onAppear { folder = initialFolder }
        .presentationDetents([.height(320)])
    }
}

/// اختيار موعد امتحان المادة (أو حذفه).
struct ExamDateSheet: View {
    @EnvironmentObject var app: AppController
    let folder: String
    @State private var date = Date()
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        SheetScaffold(title: L("موعد امتحان \(folder)", "\(folder) exam date")) {
            VStack(spacing: 12) {
                DatePicker("", selection: $date, in: Date()..., displayedComponents: .date)
                    .datePickerStyle(.graphical)
                    .tint(Palette.navy)
                    .environment(\.locale, Locale(identifier: app.english ? "en" : "ar"))
                HStack {
                    if app.examDates[folder] != nil {
                        NavyButton(label: L("حذف الموعد", "Remove date"), filled: false, danger: true) { app.setExamDate(folder, nil); dismiss() }
                    }
                    Spacer()
                    NavyButton(label: L("إلغاء", "Cancel"), filled: false) { dismiss() }
                    NavyButton(label: L("حفظ", "Save")) { app.setExamDate(folder, date); dismiss() }
                }
            }
            .padding(.horizontal, 22).padding(.bottom, 20)
        }
        .onAppear { date = app.examDate(folder) ?? Calendar.current.date(byAdding: .day, value: 14, to: Date())! }
    }
}

/// بحث داخل نصوص كل المكتبة.
struct LibrarySearchSheet: View {
    @EnvironmentObject var app: AppController
    @State private var q = ""
    @State private var results: [AppController.LibraryHit]? = nil
    @State private var busy = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        SheetScaffold(title: L("بحث داخل نصوص المكتبة", "Search inside library text")) {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 8) {
                    FieldBox(placeholder: L("كلمة أو جملة، عربي أو إنجليزي", "A word or phrase, Arabic or English"), text: $q)
                        .onSubmit(run)
                    NavyButton(label: L("بحث", "Search"), action: run)
                }
                Text(L("يشمل نصوص ملفات PDF والصفحات التي قُرئ نصها.", "Covers PDF text and pages whose text has been read."))
                    .font(Fonts.sans(12)).foregroundColor(Palette.textSecondary)
                if busy { ProgressView().progressViewStyle(.linear).tint(Palette.gold) }
                if let r = results {
                    if r.isEmpty {
                        Text(L("لا نتائج", "No results")).font(Fonts.sans(14)).foregroundColor(Palette.textSecondary)
                    } else {
                        Text(L("\(r.count) نتيجة", "\(r.count) results")).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary)
                        ScrollView {
                            LazyVStack(alignment: .leading, spacing: 4) {
                                ForEach(r) { h in
                                    Button {
                                        dismiss()
                                        app.openAt(h.docId, h.page, query: q)
                                    } label: {
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(L("\(h.docName) — صفحة \(h.page + 1)", "\(h.docName) — page \(h.page + 1)"))
                                                .font(Fonts.sans(13, .bold)).foregroundColor(Palette.navy).lineLimit(1)
                                            Text(h.snippet).font(Fonts.sans(14)).foregroundColor(Palette.textPrimary).lineLimit(3)
                                        }
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .padding(.horizontal, 10).padding(.vertical, 8)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 22).padding(.bottom, 16)
        }
    }

    private func run() {
        guard !q.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        busy = true
        app.searchLibrary(q) { r in
            results = r
            busy = false
        }
    }
}

/// مسح مستند بالكاميرا (VisionKit).
struct ScannerView: UIViewControllerRepresentable {
    let onDone: ([UIImage]) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeCoordinator() -> ScanCoordinator { ScanCoordinator(self) }

    func makeUIViewController(context: Context) -> VNDocumentCameraViewController {
        let v = VNDocumentCameraViewController()
        v.delegate = context.coordinator
        return v
    }

    func updateUIViewController(_ uiViewController: VNDocumentCameraViewController, context: Context) {}

    final class ScanCoordinator: NSObject, VNDocumentCameraViewControllerDelegate {
        let parent: ScannerView
        init(_ p: ScannerView) { parent = p }

        func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFinishWith scan: VNDocumentCameraScan) {
            let imgs = (0..<scan.pageCount).map { scan.imageOfPage(at: $0) }
            parent.dismiss()
            parent.onDone(imgs)
        }

        func documentCameraViewControllerDidCancel(_ controller: VNDocumentCameraViewController) { parent.dismiss() }

        func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFailWithError error: Error) { parent.dismiss() }
    }
}

/// نافذة المشاركة من النظام (AirDrop، الملفات، البريد...).
struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

// ---------------------------------------------------------------- القوالب

enum PickerMode { case newNote, addPage, change, defaultTemplate }

/// منتقي الورق (نفس TemplatePickerDialog في أندرويد): نافذة كبيرة بعمودين — الورقة المختارة كبيرة
/// على الجانب، والمقاس والاتجاه واللون والأقسام في الجانب الآخر، وشريط ثابت في الأسفل فيه زر الإضافة.
struct TemplatePickerSheet: View {
    let mode: PickerMode
    var initial: PaperChoice? = nil
    var sizeLocked = false
    var onPdf: ((Bool) -> Void)? = nil
    let onConfirm: (PaperChoice, String, Bool) -> Void
    @State private var choice = Templates.defaultChoice()
    @State private var name = L("ملاحظة جديدة", "New note")
    @State private var atEnd = false
    @State private var makeDefault = false
    @State private var collapsed: Set<Int> = []
    @State private var custom: Color = .white
    @State private var ready = false
    @Environment(\.dismiss) private var dismiss

    private var ratio: CGFloat { let p = choice.points; return p.width / max(1, p.height) }

    var body: some View {
        VStack(spacing: 0) {
            Text(title).font(Fonts.display(22)).foregroundColor(Palette.navy)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 24).padding(.top, 18).padding(.bottom, 12)
            Rectangle().fill(Palette.gold.opacity(0.45)).frame(height: 1)
            HStack(spacing: 0) {
                side
                Rectangle().fill(Palette.divider).frame(width: 1)
                options
            }
            .frame(maxHeight: .infinity)
            Rectangle().fill(Palette.divider).frame(height: 1)
            HStack(spacing: 8) {
                if let pdf = onPdf {
                    DialogButton(label: L("إضافة ملف PDF…", "Add PDF file…")) { dismiss(); pdf(atEnd) }
                }
                Spacer()
                DialogButton(label: L("إلغاء", "Cancel"), color: Palette.textSecondary) { dismiss() }
                Button {
                    if makeDefault || mode == .defaultTemplate { Templates.saveDefault(choice) }
                    dismiss()
                    onConfirm(choice, name, atEnd)
                } label: {
                    Text(confirmLabel).font(Fonts.sans(14, .medium)).foregroundColor(.white)
                        .padding(.horizontal, 24).padding(.vertical, 10)
                        .background(Capsule().fill(Palette.navy))
                }
                .buttonStyle(PressScale())
            }
            .padding(.horizontal, 18).padding(.vertical, 12)
        }
        .background(Palette.drawerBg)
        .environment(\.layoutDirection, .leftToRight)
        .onAppear {
            guard !ready else { return }
            ready = true
            if let i = initial { choice = i }
            custom = Color(hex: choice.color)
        }
    }

    private var title: String {
        switch mode {
        case .newNote: return L("ملاحظة جديدة", "New note")
        case .addPage: return L("إضافة صفحة", "Add page")
        case .change: return L("تغيير قالب الصفحة", "Change page template")
        case .defaultTemplate: return L("القالب الافتراضي للملاحظات", "Default notebook template")
        }
    }

    private var confirmLabel: String {
        switch mode {
        case .newNote: return L("إنشاء", "Create")
        case .addPage: return L("إضافة صفحة", "Add page")
        case .change: return L("تطبيق", "Apply")
        case .defaultTemplate: return L("حفظ", "Save")
        }
    }

    // ---------- العمود الجانبي: الورقة المختارة كبيرة

    private var side: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if mode == .newNote { FieldBox(placeholder: L("الاسم", "Name"), text: $name) }
                if mode == .addPage {
                    Text(L("المكان", "Position")).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary)
                    HStack(spacing: 6) {
                        ChoiceChip(label: L("بعد الحالية", "After current"), selected: !atEnd) { atEnd = false }
                        ChoiceChip(label: L("في النهاية", "At the end"), selected: atEnd) { atEnd = true }
                    }
                }
                Text(L("الورقة", "Paper")).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary)
                paper(choice.kind, width: ratio > 1 ? 220 : max(130, 220 * ratio), selected: true)
                    .frame(maxWidth: .infinity)
                Text(Templates.labelOf(choice.kind) + " · " + Templates.sizeOf(choice.size).label + (choice.landscape ? L(" · أفقي", " · landscape") : ""))
                    .font(Fonts.sans(14, .bold)).foregroundColor(Palette.navy)
                    .multilineTextAlignment(.center).frame(maxWidth: .infinity)
                if mode == .newNote || mode == .addPage {
                    Button { makeDefault.toggle() } label: {
                        HStack(spacing: 8) {
                            Image(systemName: makeDefault ? "checkmark.square.fill" : "square")
                                .font(.system(size: 20)).foregroundColor(makeDefault ? Palette.navy : Palette.textSecondary)
                            Text(L("اجعله القالب الافتراضي", "Make it the default")).font(Fonts.sans(13)).foregroundColor(Palette.textPrimary)
                        }
                        .padding(4)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(20)
        }
        .frame(width: 280)
        .background(Palette.ivory)
    }

    // ---------- الخيارات والأقسام

    private var options: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                if !sizeLocked {
                    Menu {
                        ForEach(Templates.sizes) { sz in
                            Button { choice.size = sz.id } label: {
                                if sz.id == choice.size { Label(sz.label, systemImage: "checkmark") } else { Text(sz.label) }
                            }
                        }
                    } label: {
                        Text(Templates.sizeOf(choice.size).label + "  ▾").font(Fonts.sans(14, .medium)).foregroundColor(Palette.textPrimary)
                            .padding(.horizontal, 16).padding(.vertical, 9)
                            .background(RoundedRectangle(cornerRadius: 18).fill(Color.white))
                            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Palette.divider, lineWidth: 1))
                    }
                    HStack(spacing: 0) {
                        orientation(portrait: true)
                        orientation(portrait: false)
                    }
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color.white))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.divider, lineWidth: 1))
                }
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(Templates.paperColors, id: \.0) { item in
                            let sel = item.0 == choice.color
                            Button { choice.color = item.0 } label: {
                                Circle().fill(Color(hex: item.0)).frame(width: 34, height: 34)
                                    .overlay(Circle().stroke(sel ? Palette.gold : Palette.divider, lineWidth: sel ? 3 : 1))
                            }
                            .accessibilityLabel(item.1)
                        }
                        ColorPicker("", selection: $custom, supportsOpacity: false).labelsHidden().frame(width: 34, height: 34)
                            .onChange(of: custom) { _, c in
                                if ready && c.argb | 0xFF000000 != choice.color { choice.color = c.argb | 0xFF000000 }
                            }
                    }
                    .padding(.vertical, 4)
                }
            }
            .padding(.horizontal, 18).padding(.vertical, 12)
            Rectangle().fill(Palette.divider).frame(height: 1)
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(Array(Templates.categories.enumerated()), id: \.offset) { ci, cat in
                        let closed = collapsed.contains(ci)
                        Button {
                            if closed { collapsed.remove(ci) } else { collapsed.insert(ci) }
                        } label: {
                            HStack(spacing: 8) {
                                Text(closed ? "▸" : "▾").font(Fonts.sans(14)).foregroundColor(Palette.goldDeep)
                                Text(cat.title()).font(Fonts.sans(17, .bold)).foregroundColor(Palette.navy)
                                Spacer()
                            }
                            .padding(.horizontal, 18).padding(.vertical, 10)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        if !closed {
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(alignment: .top, spacing: 18) {
                                    ForEach(cat.kinds) { k in
                                        let sel = k.id == choice.kind
                                        Button { choice.kind = k.id } label: {
                                            VStack(spacing: 6) {
                                                paper(k.id, width: ratio > 1 ? 108 : min(108, 140 * ratio), selected: sel)
                                                    .frame(height: 150)
                                                Text(k.label()).font(Fonts.sans(13, sel ? .bold : .regular))
                                                    .foregroundColor(sel ? Palette.goldDeep : Palette.textPrimary)
                                                    .multilineTextAlignment(.center)
                                            }
                                            .frame(width: 118)
                                            .padding(4)
                                        }
                                        .buttonStyle(PressScale())
                                    }
                                }
                                .padding(.horizontal, 18).padding(.bottom, 14)
                            }
                        }
                    }
                }
                .padding(.vertical, 8)
            }
        }
        .frame(maxWidth: .infinity)
    }

    /// صورة الورقة بقالبها ولونها ونسبتها.
    private func paper(_ kind: String, width: CGFloat, selected: Bool) -> some View {
        let r = min(max(ratio, 0.4), 2.5)
        let h = width / r
        return Image(uiImage: Templates.preview(kind: kind, bg: choice.color, size: CGSize(width: 260, height: 260 / r)))
            .resizable()
            .frame(width: width, height: h)
            .background(Color(hex: choice.color))
            .clipShape(RoundedRectangle(cornerRadius: 6))
            .overlay(RoundedRectangle(cornerRadius: 6).stroke(selected ? Palette.gold : Palette.divider, lineWidth: selected ? 3 : 1))
            .shadow(color: .black.opacity(selected ? 0.16 : 0.08), radius: selected ? 6 : 2, y: 2)
    }

    private func orientation(portrait: Bool) -> some View {
        let sel = portrait != choice.landscape
        return Button { choice.landscape = !portrait } label: {
            RoundedRectangle(cornerRadius: 3).stroke(sel ? Palette.navy : Palette.textSecondary, lineWidth: 2)
                .frame(width: portrait ? 14 : 22, height: portrait ? 20 : 14)
                .frame(width: 46, height: 40)
                .background(sel ? Palette.navySoft : Color.clear)
        }
        .buttonStyle(.plain)
    }
}

struct ChoiceChipLabel: View {
    let text: String
    var body: some View {
        Text(text).font(Fonts.sans(14)).foregroundColor(Palette.textPrimary)
            .padding(.horizontal, 15).padding(.vertical, 7)
            .background(Capsule().fill(Color.white))
            .overlay(Capsule().stroke(Palette.divider, lineWidth: 1))
    }
}
