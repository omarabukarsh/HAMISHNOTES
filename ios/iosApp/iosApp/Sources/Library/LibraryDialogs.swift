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

/// اسم واحد (إعادة تسمية، مادة جديدة).
struct NameSheet: View {
    let title: String
    let initial: String
    let onSave: (String) -> Void
    @State private var name = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        SheetScaffold(title: title) {
            VStack(alignment: .leading, spacing: 16) {
                FieldBox(placeholder: L("الاسم", "Name"), text: $name)
                HStack {
                    Spacer()
                    NavyButton(label: L("إلغاء", "Cancel"), filled: false) { dismiss() }
                    NavyButton(label: L("حفظ", "Save")) {
                        guard !name.trimmingCharacters(in: .whitespaces).isEmpty else { return }
                        onSave(name)
                        dismiss()
                    }
                    .opacity(name.trimmingCharacters(in: .whitespaces).isEmpty ? 0.5 : 1)
                }
            }
            .padding(.horizontal, 22)
        }
        .onAppear { name = initial }
        .presentationDetents([.height(220)])
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

enum PickerMode { case newNote, addPage, defaultTemplate }

/// اختيار القالب: الأقسام، لون الورقة، المقاس، والاتجاه.
struct TemplatePickerSheet: View {
    let mode: PickerMode
    var onPdf: ((Bool) -> Void)? = nil
    let onConfirm: (PaperChoice, String, Bool) -> Void
    @State private var choice = Templates.defaultChoice()
    @State private var name = L("ملاحظة جديدة", "New note")
    @State private var atEnd = false
    @State private var makeDefault = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let title: String = {
            switch mode {
            case .newNote: return L("ملاحظة جديدة", "New note")
            case .addPage: return L("إضافة صفحة", "Add page")
            case .defaultTemplate: return L("القالب الافتراضي للملاحظات", "Default notebook template")
            }
        }()
        SheetScaffold(title: title) {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if mode == .newNote { FieldBox(placeholder: L("الاسم", "Name"), text: $name) }
                    if mode == .addPage {
                        Text(L("المكان", "Position")).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary)
                        HStack(spacing: 8) {
                            ChoiceChip(label: L("بعد الحالية", "After current"), selected: !atEnd) { atEnd = false }
                            ChoiceChip(label: L("في النهاية", "At the end"), selected: atEnd) { atEnd = true }
                        }
                    }
                    Text(L("الورقة", "Paper")).font(Fonts.sans(13)).foregroundColor(Palette.textSecondary)
                    PaperColorRow(selected: choice.color) { choice.color = $0 }
                    HStack(spacing: 8) {
                        Menu {
                            ForEach(Templates.sizes) { s in Button(s.label) { choice.size = s.id } }
                        } label: {
                            ChoiceChipLabel(text: Templates.sizeOf(choice.size).label + " ▾")
                        }
                        ChoiceChip(label: L("عمودي", "Portrait"), selected: !choice.landscape) { choice.landscape = false }
                        ChoiceChip(label: L("أفقي", "Landscape"), selected: choice.landscape) { choice.landscape = true }
                    }
                    ForEach(Array(Templates.categories.enumerated()), id: \.offset) { _, cat in
                        Text(cat.title()).font(Fonts.sans(14, .bold)).foregroundColor(Palette.textPrimary).padding(.top, 6)
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 104), spacing: 12)], spacing: 12) {
                            ForEach(cat.kinds) { k in
                                let sel = k.id == choice.kind
                                Button { choice.kind = k.id } label: {
                                    VStack(spacing: 6) {
                                        Image(uiImage: Templates.preview(kind: k.id, bg: choice.color,
                                                                         size: CGSize(width: 96, height: choice.landscape ? 68 : 134)))
                                            .resizable().scaledToFit().frame(height: 120)
                                            .clipShape(RoundedRectangle(cornerRadius: 8))
                                            .overlay(RoundedRectangle(cornerRadius: 8).stroke(sel ? Palette.gold : Palette.divider, lineWidth: sel ? 3 : 1))
                                            .shadow(color: .black.opacity(0.08), radius: 3, y: 2)
                                        Text(k.label()).font(Fonts.sans(12, sel ? .bold : .regular)).foregroundColor(sel ? Palette.navy : Palette.textPrimary)
                                    }
                                }
                            }
                        }
                    }
                    if mode != .defaultTemplate {
                        Toggle(isOn: $makeDefault) { Text(L("اجعله القالب الافتراضي", "Make it the default")).font(Fonts.sans(13)) }.tint(Palette.navy)
                    }
                    HStack {
                        if mode == .addPage, let pdf = onPdf {
                            NavyButton(label: L("إضافة ملف PDF…", "Add PDF file…"), filled: false) { dismiss(); pdf(atEnd) }
                        }
                        Spacer()
                        NavyButton(label: L("إلغاء", "Cancel"), filled: false) { dismiss() }
                        NavyButton(label: {
                            switch mode {
                            case .newNote: return L("إنشاء", "Create")
                            case .addPage: return L("إضافة صفحة", "Add page")
                            case .defaultTemplate: return L("حفظ", "Save")
                            }
                        }()) {
                            if makeDefault || mode == .defaultTemplate { Templates.saveDefault(choice) }
                            dismiss()
                            onConfirm(choice, name, atEnd)
                        }
                    }
                    .padding(.top, 6)
                }
                .padding(.horizontal, 22).padding(.bottom, 22)
            }
        }
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
