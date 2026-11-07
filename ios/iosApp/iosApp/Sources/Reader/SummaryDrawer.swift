import SwiftUI
import Translation

/// ترتيب نص التمييز: مسافات نظيفة، وإكمال الجمل المقطوعة بين الأسطر.
enum TextTidy {
    static func tidy(_ s: String) -> String {
        var lines = s.replacingOccurrences(of: "\r", with: "").components(separatedBy: "\n").map { $0.trimmingCharacters(in: .whitespaces) }
        lines.removeAll { $0.isEmpty }
        var out = ""
        for l in lines {
            if out.isEmpty { out = l; continue }
            if out.hasSuffix("-") && !out.hasSuffix(" -") {
                out.removeLast()
                out += l
            } else if let last = out.last, ".:!?؟؛".contains(last) {
                out += "\n" + l
            } else {
                out += " " + l
            }
        }
        while out.contains("  ") { out = out.replacingOccurrences(of: "  ", with: " ") }
        return out
    }
}

/// درج «ملخصات»: كل ما ظُلّل في المستند مرتبًا حسب الصفحة أو اللون، قابل للتعديل.
struct SummaryDrawer: View {
    @ObservedObject var session: DocSession
    @EnvironmentObject var app: AppController
    @State private var editing: TextMark? = nil
    @State private var cardFrom: TextMark? = nil
    @State private var colorFilter: UInt32? = nil
    @State private var translateText = ""
    @State private var translating = false
    @State private var exportURL: URL? = nil

    var body: some View {
        let s = session
        let marks = s.allMarks().filter { $0.inSummary && (colorFilter == nil || $0.color == colorFilter) }
        let colors = Array(Set(s.data.marks.map { $0.color })).sorted()
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                Text(L("ملخصات", "Summaries")).font(Fonts.display(20)).foregroundColor(Palette.navy)
                Spacer()
                Button { s.mapOpen = true } label: { pill("ph_mindmap", L("خريطة", "Map")) }
                Button {
                    s.flush()
                    exportURL = Exporter.summaryPDF(s)
                } label: { pill("ph_export", L("حفظ", "Save")) }
                Button { s.summaryOpen = false } label: { PhIcon("ph_close", Palette.textSecondary, 18, L("إغلاق الملخصات", "Close summaries")).frame(width: 32, height: 32) }
            }
            .padding(.horizontal, 16).padding(.top, 14).padding(.bottom, 8)
            HStack(spacing: 6) {
                ChoiceChip(label: L("مرتّب", "Tidy"), selected: app.summaryTidy) { app.summaryTidy = true }
                ChoiceChip(label: L("كما التُقط", "As captured"), selected: !app.summaryTidy) { app.summaryTidy = false }
                Spacer()
                Button {
                    UIPasteboard.general.string = summaryText(marks)
                    app.message(L("تم النسخ", "Copied"))
                } label: { Text(L("نسخ الكل", "Copy all")).font(Fonts.sans(13, .bold)).foregroundColor(Palette.navy) }
            }
            .padding(.horizontal, 16)
            if colors.count > 1 {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ChoiceChip(label: L("كل الألوان", "All colors"), selected: colorFilter == nil) { colorFilter = nil }
                        ForEach(colors, id: \.self) { c in
                            Button { colorFilter = c } label: {
                                HStack(spacing: 6) {
                                    Circle().fill(Color(hex: c)).frame(width: 14, height: 14)
                                    Text(app.labelOf(c)).font(Fonts.sans(13)).foregroundColor(Palette.textPrimary)
                                }
                                .padding(.horizontal, 12).padding(.vertical, 7)
                                .background(Capsule().fill(colorFilter == c ? Palette.goldSoft : Color.white))
                                .overlay(Capsule().stroke(Palette.divider, lineWidth: 1))
                            }
                        }
                    }
                    .padding(.horizontal, 16).padding(.vertical, 8)
                }
            }
            Divider().padding(.top, 6)
            if marks.isEmpty {
                VStack(spacing: 8) {
                    Text(L("لا توجد ملخصات بعد", "No summaries yet")).font(Fonts.sans(17, .bold)).foregroundColor(Palette.textPrimary)
                    Text(L("اختر الهايلايتر، واضغط عليه مرة ثانية وفعّل «التقاط النص»، ثم مرّر على أي سطر. ظلّل نفس النص مرتين ليظهر مهمًا بخط عريض.\nيعمل مع العربية والإنجليزية في ملفات PDF الرقمية.",
                           "Choose the highlighter, tap it again and turn on “Text snap”, then run it over any line. Highlight the same text twice to mark it important in bold.\nWorks with Arabic and English in digital PDFs."))
                        .font(Fonts.sans(14)).foregroundColor(Palette.textSecondary).multilineTextAlignment(.center).lineSpacing(4)
                }
                .padding(24).frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 10) {
                        ForEach(marks) { m in
                            markRow(m)
                        }
                    }
                    .padding(14)
                }
            }
        }
        .frame(width: 340)
        .frame(maxHeight: .infinity)
        .background(Palette.drawerBg)
        .overlay(Rectangle().fill(Palette.divider).frame(width: 1), alignment: .leading)
        .sheet(item: $editing) { m in MarkEditSheet(session: s, mark: m) }
        .sheet(item: $cardFrom) { m in
            CardEditorSheet(card: Card(id: UUID().uuidString, front: "", back: displayText(m), folder: app.entry(s.id)?.folder ?? "", docId: s.id,
                                       source: L("\(s.title) — ص \((s.index(of: m.page) ?? 0) + 1)", "\(s.title) — p. \((s.index(of: m.page) ?? 0) + 1)")),
                            folder: app.entry(s.id)?.folder ?? "")
                .environmentObject(app)
        }
        .sheet(item: Binding(get: { exportURL.map { IdURL(url: $0) } }, set: { exportURL = $0?.url })) { u in ShareSheet(items: [u.url]) }
        .modifier(TranslateModifier(text: translateText, isPresented: $translating))
    }

    private func pill(_ icon: String, _ t: String) -> some View {
        HStack(spacing: 4) {
            PhIcon(icon, Palette.navy, 15)
            Text(t).font(Fonts.sans(12, .bold)).foregroundColor(Palette.navy)
        }
        .padding(.horizontal, 10).padding(.vertical, 6)
        .background(Capsule().fill(Palette.navySoft))
    }

    private func displayText(_ m: TextMark) -> String { app.summaryTidy ? TextTidy.tidy(m.text) : m.text }

    private func summaryText(_ marks: [TextMark]) -> String {
        marks.map { displayText($0) }.joined(separator: "\n\n")
    }

    private func markRow(_ m: TextMark) -> some View {
        let s = session
        let page = (s.index(of: m.page) ?? 0) + 1
        let text = displayText(m)
        let isHeading = text.hasPrefix("# ")
        return HStack(alignment: .top, spacing: 10) {
            RoundedRectangle(cornerRadius: 2).fill(Color(hex: m.color)).frame(width: 4)
            VStack(alignment: .leading, spacing: 4) {
                Text(isHeading ? String(text.dropFirst(2)) : text)
                    .font(Fonts.sans(isHeading ? 17 : 14, m.level > 1 || isHeading ? .bold : .regular))
                    .foregroundColor(Palette.textPrimary).lineSpacing(4)
                    .frame(maxWidth: .infinity, alignment: .leading)
                HStack {
                    Text(L("ص \(page)", "p. \(page)")).font(Fonts.sans(11)).foregroundColor(Palette.textSecondary)
                    if m.level > 1 { Text(L("مهم", "Important")).font(Fonts.sans(11, .bold)).foregroundColor(Palette.goldDeep) }
                    Spacer()
                    Menu {
                        Button(L("انتقال إلى الموضع", "Go to it")) { if let i = s.index(of: m.page) { s.goTo(page: i) } }
                        Button(L("تعديل النص", "Edit text")) { editing = m }
                        Button(L("اجعله بطاقة", "Make it a card")) { if Premium.shared.require(.cards) { cardFrom = m } }
                        Button(isHeading ? L("نص عادي", "Plain text") : L("اجعله عنوانًا", "Make it a heading")) {
                            s.updateMark(m.id) { $0.text = isHeading ? String($0.text.dropFirst(2)) : "# " + $0.text }
                        }
                        Button(m.level > 1 ? L("عادي", "Normal") : L("مهم", "Important")) { s.updateMark(m.id) { $0.level = $0.level > 1 ? 1 : 2 } }
                        Button(L("ضعه في الخريطة", "Put it in the map")) {
                            s.edit { d in
                                if d.map == nil { d.map = MindMapData(title: s.title) }
                                d.map?.branches.append(MapNodeData(text: text, markId: m.id))
                            }
                            app.message(L("أُضيف إلى الخريطة الذهنية", "Added to the mind map"))
                        }
                        Button(L("ترجمة", "Translate")) {
                            if Premium.shared.require(.translate) {
                                translateText = text
                                translating = true
                            }
                        }
                        Button(L("نسخ", "Copy")) { UIPasteboard.general.string = text; app.message(L("تم النسخ", "Copied")) }
                        Button(L("إخفاء من الملخص (يبقى التظليل)", "Hide from summary (keep highlight)")) { s.updateMark(m.id) { $0.inSummary = false } }
                        Button(role: .destructive) { s.deleteMark(m.id) } label: { Text(L("حذف التمييز", "Delete highlight")) }
                    } label: { PhIcon("ph_more", Palette.textSecondary, 18).frame(width: 28, height: 28) }
                }
            }
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 14).fill(m.level > 1 ? Color(hex: m.color).opacity(0.12) : Color.white))
        .roundedBorder(Palette.divider, radius: 14)
        .onTapGesture { if let i = s.index(of: m.page) { s.goTo(page: i) } }
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
                Text(L("سطر يبدأ بـ # يصبح عنوانًا. سطر فارغ يبدأ فقرة جديدة.", "A line starting with # becomes a heading. An empty line starts a new paragraph."))
                    .font(Fonts.sans(12)).foregroundColor(Palette.textSecondary)
                TextEditor(text: $text).font(Fonts.sans(15)).frame(minHeight: 200)
                    .padding(8).background(RoundedRectangle(cornerRadius: 12).fill(Color.white)).roundedBorder(Palette.divider, radius: 12)
                HStack {
                    NavyButton(label: L("ترتيب النص", "Tidy text"), filled: false) { text = TextTidy.tidy(text) }
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
