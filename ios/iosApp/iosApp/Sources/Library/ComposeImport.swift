import SwiftUI
import PDFKit
import UIKit

/// عنصر في نافذة المراجعة: ملف PDF أو صورة من مكتبة الصور.
struct ComposeItem: Identifiable, Equatable {
    let id = UUID()
    var url: URL? = nil
    var image: Data? = nil
}

/// طلب نافذة المراجعة (كما في أندرويد ComposeImport): صور لملاحظة جديدة، أو عدة ملفات PDF.
struct ComposeRequest: Identifiable {
    let id = UUID()
    let pdf: Bool
    let items: [ComposeItem]
}

/// ما يُقرأ عن كل عنصر لعرضه: الاسم وعدد الصفحات والحجم والمصغّرة.
/// يُقرأ في الخلفية ثم يُعرض؛ لا يتغير بعد إنشائه (UIImage آمن بين الخيوط).
private struct ComposeInfo: @unchecked Sendable {
    var name: String
    /// عدد صفحات الملف (-1 = تعذرت قراءته). للصور دائمًا 1.
    var pages: Int
    var size: Int64
    var thumb: UIImage?
}

/// نافذة المراجعة بألوان المكتبة (كنافذة إحصاءات الدراسة): الاسم، والعناصر بترتيبها (تحريك وحذف)، والمادة.
/// للملفات: «في مستند واحد» أو «كل ملف وحده».
struct ComposeReviewView: View {
    @EnvironmentObject var app: AppController
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var themeTick // يعيد الرسم عند تغيير الثيم
    let request: ComposeRequest
    let initialFolder: String

    @State private var items: [ComposeItem] = []
    @State private var info: [UUID: ComposeInfo] = [:]
    @State private var combine = true
    @State private var folder = ""
    @State private var name = ""
    @State private var nameEdited = false
    @FocusState private var nameFocused: Bool

    private var pdf: Bool { request.pdf }
    private var merge: Bool { !pdf || combine }
    private var totalPages: Int { items.reduce(0) { $0 + max(0, info[$1.id]?.pages ?? 0) } }
    /// لون العناوين الصغيرة: ذهبي غامق في الفاتح، وأصفر المكتبة في الداكن.
    private var labelGold: Color { ThemeState.light ? Lib.accentDeep : Lib.accent }
    private let onAccent = Color(hex: 0xFF1E2A44)

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // العنوان وزر الإغلاق كما في نوافذ المكتبة
            HStack(spacing: 12) {
                Text(pdf ? L("استيراد ملفات PDF", "Import PDF files") : L("ملاحظة من الصور", "Note from photos"))
                    .font(Fonts.display(26)).foregroundColor(Lib.text).lineLimit(1)
                Spacer(minLength: 8)
                IconCircleButton(icon: "ph_close", label: L("إغلاق", "Close")) { dismiss() }
            }
            .padding(.leading, 28).padding(.trailing, 20).padding(.top, 22)
            Text(pdf ? L("اخترت \(items.count) من الملفات. اجمعها في مستند واحد، أو استورد كل ملف وحده.", "You picked \(items.count) files. Combine them into one document, or import each one on its own.")
                     : L("كل صورة تصبح صفحة على مقاسها، بالترتيب الظاهر هنا.", "Each photo becomes a page in its own shape, in the order shown here."))
                .font(Fonts.sans(14)).lineSpacing(4).foregroundColor(Lib.muted)
                .padding(.horizontal, 28).padding(.top, 4).padding(.bottom, 6)

            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    if pdf {
                        segmented.padding(.top, 10)
                    }
                    if merge {
                        VStack(alignment: .leading, spacing: 0) {
                            label(L("الاسم", "Name"))
                            TextField("", text: Binding(get: { name }, set: { name = $0; nameEdited = true }))
                                .font(Fonts.sans(16, .medium)).foregroundColor(Lib.text).tint(Lib.accentDeep)
                                .focused($nameFocused)
                                .padding(.horizontal, 16).frame(height: 54)
                                .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Lib.card))
                                .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous)
                                    .stroke(nameFocused ? Lib.accent : Lib.line, lineWidth: nameFocused ? 2 : 1))
                        }
                        .transition(.opacity.combined(with: .move(edge: .top)))
                    }
                    label(listTitle)
                    ForEach(Array(items.enumerated()), id: \.element.id) { i, it in
                        row(i, it)
                            .transition(.opacity)
                    }
                    label(L("المادة", "Subject"))
                    subjects
                }
                .padding(.horizontal, 28).padding(.bottom, 18)
                .animation(Motion.enter, value: items)
                .animation(Motion.enter, value: combine)
            }

            // الزر الرئيسي في أسفل النافذة
            Rectangle().fill(Lib.line).frame(height: 1)
            HStack(spacing: 12) {
                Text(summary).font(Fonts.sans(14)).foregroundColor(Lib.muted).lineLimit(1)
                Spacer(minLength: 8)
                Button(action: submit) {
                    HStack(spacing: 8) {
                        PhIcon(pdf ? "ph_export" : "ph_plus", onAccent, 18)
                            .rotationEffect(.degrees(pdf ? 180 : 0))
                        Text(pdf ? L("استيراد", "Import") : L("إنشاء الملاحظة", "Create note"))
                            .font(Fonts.sans(15, .bold)).foregroundColor(onAccent)
                    }
                    .padding(.horizontal, 22).frame(height: 46)
                    .background(Capsule().fill(Lib.accent))
                    .opacity(canSubmit ? 1 : 0.45)
                }
                .buttonStyle(PressScale())
                .disabled(!canSubmit)
            }
            .padding(.leading, 28).padding(.trailing, 20).padding(.top, 14).padding(.bottom, 16)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Lib.side)
        .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).stroke(Lib.line, lineWidth: 1))
        .onAppear {
            guard items.isEmpty else { return }
            items = request.items
            folder = app.folders.contains(initialFolder) ? initialFolder : ""
            if !pdf {
                let f = DateFormatter()
                f.dateFormat = "d MMMM"
                f.locale = Locale(identifier: app.english ? "en" : "ar")
                name = L("صور ", "Photos ") + f.string(from: Date())
            }
        }
        // الاسم المقترح لملفات PDF: اسم أول ملف بالترتيب الحالي، ما لم يكتب المستخدم اسمًا
        .onChange(of: firstName) { _, n in
            if pdf, !nameEdited, let n { name = n }
        }
    }

    private var firstName: String? { items.first.flatMap { info[$0.id]?.name } }

    private var listTitle: String {
        if !pdf { return L("الصفحات بالترتيب (\(items.count))", "Pages in order (\(items.count))") }
        if !merge { return L("الملفات", "Files") }
        return totalPages > 0 ? L("الملفات بالترتيب (\(totalPages) صفحة)", "Files in order (\(totalPages) pages)") : L("الملفات بالترتيب", "Files in order")
    }

    private var summary: String {
        if !pdf { return L("\(items.count) صفحة", "\(items.count) pages") }
        if merge { return totalPages > 0 ? L("\(totalPages) صفحة في مستند واحد", "\(totalPages) pages in one document") : "" }
        return L("\(items.count) مستندات منفصلة", "\(items.count) separate documents")
    }

    private var canSubmit: Bool {
        let unreadable = pdf && !items.isEmpty && items.allSatisfy { info[$0.id]?.pages == -1 }
        return !items.isEmpty && !unreadable && (!merge || !name.trimmingCharacters(in: .whitespaces).isEmpty)
    }

    private func submit() {
        let list = items
        let n = name.trimmingCharacters(in: .whitespaces)
        let f = folder
        let photos = !pdf
        let together = combine
        dismiss()
        if photos {
            app.createImageNote(list.compactMap { $0.image }, name: n, folder: f)
        } else if together {
            app.importMerged(list.compactMap { $0.url }, name: n, folder: f)
        } else {
            app.importDocuments(list.compactMap { $0.url }, folder: f)
        }
    }

    // ---------------------------------------------------------------- الأجزاء

    private func label(_ t: String) -> some View {
        Text(t.uppercased()).font(Fonts.sans(12, .bold)).foregroundColor(labelGold)
            .padding(.leading, 4).padding(.top, 16).padding(.bottom, 4)
    }

    /// اختيار من اثنين: المختار أصفر كزر «جديد».
    private var segmented: some View {
        HStack(spacing: 4) {
            ForEach(0..<2, id: \.self) { i in
                let on = (i == 0) == combine
                Button { withAnimation(Motion.enter) { combine = i == 0 } } label: {
                    Text(i == 0 ? L("في مستند واحد", "One document") : L("كل ملف وحده", "Each file separately"))
                        .font(Fonts.sans(14, .bold)).foregroundColor(on ? onAccent : Lib.muted).lineLimit(1)
                        .frame(maxWidth: .infinity).frame(height: 42)
                        .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(on ? Lib.accent : Color.clear))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(4)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Lib.bg))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Lib.line, lineWidth: 1))
    }

    /// المادة: «بلا مادة» ثم مواد المكتبة، بشكل أزرار المكتبة (المختارة صفراء).
    private var subjects: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                chip(L("بلا مادة", "No subject"), folder.isEmpty) { folder = "" }
                ForEach(app.folders, id: \.self) { f in chip(f, folder == f) { folder = f } }
            }
        }
    }

    private func chip(_ t: String, _ on: Bool, _ a: @escaping () -> Void) -> some View {
        Button(action: a) {
            Text(t).font(Fonts.sans(14, on ? .bold : .regular)).foregroundColor(on ? onAccent : Lib.text)
                .lineLimit(1).fixedSize()
                .padding(.horizontal, 16).frame(height: 40)
                .background(Capsule().fill(on ? Lib.accent : Lib.card))
                .overlay(Capsule().stroke(on ? Lib.accent : Lib.line, lineWidth: 1))
        }
        .buttonStyle(PressScale(scale: 0.96))
    }

    private func row(_ i: Int, _ it: ComposeItem) -> some View {
        let inf = info[it.id]
        let thumb = inf?.thumb
        let wide = (thumb?.size.width ?? 0) > (thumb?.size.height ?? 1)
        return HStack(spacing: 0) {
            if merge {
                Text("\(i + 1)").font(Fonts.sans(12, .bold)).foregroundColor(labelGold)
                    .frame(width: 26, height: 26).background(Circle().fill(Lib.accentSoft))
                    .padding(.trailing, 10)
            }
            // المصغّرة: الصورة بشكلها، أو أول صفحة من الملف
            ZStack {
                RoundedRectangle(cornerRadius: 6, style: .continuous).fill(Lib.bg)
                if let thumb {
                    Image(uiImage: thumb).resizable().scaledToFill()
                } else if pdf {
                    Text("PDF").font(Fonts.sans(10, .bold)).foregroundColor(Lib.exam)
                }
            }
            .frame(width: wide ? 52 : 40, height: wide ? 38 : 52)
            .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 6, style: .continuous).stroke(Lib.line, lineWidth: 1))
            .padding(.trailing, 12)
            VStack(alignment: .leading, spacing: 2) {
                Text(pdf ? (inf?.name ?? "…") : L("صفحة \(i + 1)", "Page \(i + 1)"))
                    .font(Fonts.sans(15, .bold)).foregroundColor(Lib.text).lineLimit(1).truncationMode(.middle)
                let sub = subtitle(inf, thumb: thumb, wide: wide)
                if !sub.isEmpty {
                    Text(sub).font(Fonts.sans(12)).foregroundColor(inf?.pages == -1 ? Lib.exam : Lib.muted).lineLimit(1)
                }
            }
            Spacer(minLength: 6)
            if merge {
                iconButton("ph_caret_up", L("تقديم", "Move up"), enabled: i > 0) { move(i, -1) }
                iconButton("ph_caret_down", L("تأخير", "Move down"), enabled: i < items.count - 1) { move(i, 1) }
            }
            iconButton("ph_trash", L("حذف", "Remove"), enabled: true, tint: Lib.exam) { remove(i) }
        }
        .padding(.leading, 12).padding(.trailing, 8).padding(.vertical, 10)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Lib.card))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Lib.line, lineWidth: 1))
        .task(id: it.id) { await load(it) }
    }

    private func subtitle(_ inf: ComposeInfo?, thumb: UIImage?, wide: Bool) -> String {
        if !pdf { return thumb == nil ? "" : wide ? L("صورة أفقية", "Landscape photo") : L("صورة عمودية", "Portrait photo") }
        guard let inf else { return L("قراءة الملف…", "Reading the file…") }
        if inf.pages < 0 { return L("تعذرت قراءة الملف", "Could not read the file") }
        let size = inf.size > 0 ? " · " + ByteCountFormatter.string(fromByteCount: inf.size, countStyle: .file) : ""
        return L("\(inf.pages) صفحة", "\(inf.pages) pages") + size
    }

    private func iconButton(_ icon: String, _ label: String, enabled: Bool, tint: Color = Lib.muted, _ a: @escaping () -> Void) -> some View {
        Button(action: a) {
            PhIcon(icon, tint, 19, label).frame(width: 38, height: 38).contentShape(Circle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.3)
    }

    private func move(_ i: Int, _ d: Int) {
        let j = i + d
        guard items.indices.contains(i), items.indices.contains(j) else { return }
        withAnimation(Motion.enter) { items.swapAt(i, j) }
    }

    private func remove(_ i: Int) {
        guard items.indices.contains(i) else { return }
        _ = withAnimation(Motion.enter) { items.remove(at: i) }
        if items.isEmpty { dismiss() }
    }

    /// يقرأ اسم الملف وعدد صفحاته ومصغّرة أول صفحة، أو مصغّرة الصورة. خارج الخيط الرئيسي.
    private func load(_ it: ComposeItem) async {
        guard info[it.id] == nil else { return }
        let pdf = self.pdf
        let r = await Task.detached(priority: .userInitiated) { () -> ComposeInfo in
            if pdf, let u = it.url {
                let scoped = u.startAccessingSecurityScopedResource()
                defer { if scoped { u.stopAccessingSecurityScopedResource() } }
                let name = u.deletingPathExtension().lastPathComponent
                let size = Int64((try? u.resourceValues(forKeys: [.fileSizeKey]))?.fileSize ?? 0)
                guard let doc = PDFDocument(url: u), doc.pageCount > 0, !doc.isLocked else {
                    return ComposeInfo(name: name, pages: -1, size: size, thumb: nil)
                }
                let thumb = doc.page(at: 0)?.thumbnail(of: CGSize(width: 120, height: 160), for: .mediaBox)
                return ComposeInfo(name: name, pages: doc.pageCount, size: size, thumb: thumb)
            }
            let img = it.image.flatMap { UIImage(data: $0) }
            let thumb = img?.preparingThumbnail(of: ComposeReviewView.fit(img?.size ?? .zero, 160))
            return ComposeInfo(name: "", pages: 1, size: Int64(it.image?.count ?? 0), thumb: thumb)
        }.value
        info[it.id] = r
    }

    nonisolated private static func fit(_ s: CGSize, _ m: CGFloat) -> CGSize {
        guard s.width > 0, s.height > 0 else { return CGSize(width: m, height: m) }
        let k = m / max(s.width, s.height)
        return CGSize(width: s.width * k, height: s.height * k)
    }
}
