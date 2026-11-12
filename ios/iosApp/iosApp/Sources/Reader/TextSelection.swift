import SwiftUI
import PDFKit

// تحديد نص الكتاب بضغط مطوّل بالإصبع (نفس SelectionBar في أندرويد): نسخ، إلى الملخصات،
// اجعله بطاقة، ترجمة، تحديد كل الصفحة.

extension DocSession {
    /// التحديد بمستطيلات نسبية لعرض الصفحة (الأصل أعلى اليسار).
    func selectionRects(_ sel: PDFSelection, page: PDFPage) -> [MarkRect] {
        let w = max(1, TextLayout.displaySize(page).width)
        return sel.selectionsByLine().compactMap { line -> MarkRect? in
            let raw = line.bounds(for: page)
            guard raw.width > 0.5, raw.height > 0.5 else { return nil }
            let r = TextLayout.displayRect(raw, page: page)
            return MarkRect(l: r.minX / w, t: r.minY / w, r: r.maxX / w, b: r.maxY / w)
        }
    }

    /// يضيف النص المحدد إلى الملخصات كتمييز بلون الهايلايتر.
    func markSelection(_ sel: PDFSelection) {
        guard let page = sel.pages.first, let doc = pdf else { return }
        let idx = doc.index(for: page)
        guard let pid = pageId(idx) else { return }
        let text = (sel.string ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let rects = selectionRects(sel, page: page)
        guard !text.isEmpty, !rects.isEmpty else { return }
        let c = AppController.shared
        addMark(TextMark(id: UUID().uuidString, page: pid, rects: rects, color: c.colorFor(.highlighter), text: text, createdAt: nowMs(),
                         inSummary: c.summariesOn))
        c.say(.done, c.summariesOn ? L("أُضيف النص إلى الملخصات", "Text added to summaries")
                                   : L("ظُلّل النص دون إضافته إلى الملخصات", "Text highlighted without adding it to summaries"))
    }
}

extension DocSession {
    /// يضيف الكلمات المحددة (صفحة ممسوحة أو خط يد) إلى الملخصات.
    func markWords(_ sel: WordSel) {
        guard let pid = pageId(sel.page) else { return }
        let text = TextTidy.clean(sel.text).trimmingCharacters(in: .whitespacesAndNewlines)
        let rects = sel.rects
        guard !text.isEmpty, !rects.isEmpty else { return }
        let c = AppController.shared
        addMark(TextMark(id: UUID().uuidString, page: pid, rects: rects, color: c.colorFor(.highlighter), text: text, createdAt: nowMs(),
                         inSummary: c.summariesOn))
        c.say(.done, c.summariesOn ? L("أُضيف النص إلى الملخصات", "Text added to summaries")
                                   : L("ظُلّل النص دون إضافته إلى الملخصات", "Text highlighted without adding it to summaries"))
    }
}

/// شريط النص المحدد في الكتاب.
struct TextSelectionBar: View {
    @ObservedObject var session: DocSession
    @EnvironmentObject var app: AppController
    @State private var translating = false
    @State private var card: SummaryDrawer.CardDraft? = nil

    var body: some View {
        let text = (session.textSel?.string ?? session.wordSel?.text ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        HStack(spacing: 0) {
            DialogButton(label: L("نسخ", "Copy")) {
                UIPasteboard.general.string = text
                app.say(.done, L("تم نسخ النص", "Text copied"))
                clear()
            }
            DialogButton(label: L("إلى الملخصات", "To summaries")) {
                if let sel = session.textSel { session.markSelection(sel) } else if let ws = session.wordSel { session.markWords(ws) }
                clear()
            }
            DialogButton(label: L("اجعله بطاقة", "Make it a card")) {
                if Premium.shared.require(.cards) { card = SummaryDrawer.CardDraft(front: "", back: text) }
            }
            DialogButton(label: L("ترجمة", "Translate")) { if Premium.shared.require(.translate) { translating = true } }
            DialogButton(label: L("تحديد كل الصفحة", "Select whole page")) {
                if let ws = session.wordSel {
                    session.wordSel = WordSel(page: ws.page, words: session.words(ws.page))
                } else if let p = session.textSel?.pages.first ?? session.pdf?.page(at: session.currentPage),
                          let all = p.selection(for: p.bounds(for: .cropBox)) {
                    session.textSel = all
                }
            }
            DialogButton(label: L("إلغاء", "Cancel"), color: Palette.textSecondary) { clear() }
        }
        .padding(.horizontal, 6).padding(.vertical, 2)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Color.white))
        .shadow(color: .black.opacity(0.16), radius: 8, y: 3)
        .modifier(TranslateModifier(text: text, isPresented: $translating))
        .dialogSheet(item: $card) { d in
            CardEditorSheet(card: Card(id: UUID().uuidString, front: d.front, back: d.back, folder: app.entry(session.id)?.folder ?? "",
                                       docId: session.id, source: session.title),
                            folder: app.entry(session.id)?.folder ?? "", onSaved: { clear() })
                .environmentObject(app)
        }
    }

    private func clear() { session.textSel = nil; session.wordSel = nil }
}
