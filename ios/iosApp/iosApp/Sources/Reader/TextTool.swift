import SwiftUI
import UIKit

// أداة النص (نفس TextTool.kt في أندرويد 1.0.271): نقرة على الصفحة تضع مربعًا وتكتب فيه مباشرة،
// وسحب القلم يرسم المربع بالعرض المطلوب. نقرة ثانية على زر الأداة تفتح نافذة مربع النص:
// الخط، الحجم، عريض/مائل/تحته خط، المحاذاة، اللون. الإعدادات تُحفظ للمربعات الجديدة وتُطبَّق على المربع الحالي.

final class TextPrefs: ObservableObject {
    static let shared = TextPrefs()
    private let p = UserDefaults.standard
    static let sizeMin = 6, sizeMax = 54

    @Published var font: String { didSet { p.set(font, forKey: "tp_font") } }
    /// الحجم بالنقاط على صفحة عرضها 595 نقطة (A4).
    @Published var sizePt: Int { didSet { p.set(sizePt, forKey: "tp_size") } }
    @Published var bold: Bool { didSet { p.set(bold, forKey: "tp_bold") } }
    @Published var italic: Bool { didSet { p.set(italic, forKey: "tp_italic") } }
    @Published var underline: Bool { didSet { p.set(underline, forKey: "tp_under") } }
    @Published var align: Int { didSet { p.set(align, forKey: "tp_align") } }
    /// اللون؛ nil = لون القلم الحالي.
    @Published var color: UInt32? { didSet { if let c = color { p.set(Int(c), forKey: "tp_color") } } }

    private init() {
        font = p.string(forKey: "tp_font") ?? ""
        sizePt = min(max(p.object(forKey: "tp_size") as? Int ?? 14, Self.sizeMin), Self.sizeMax)
        bold = p.bool(forKey: "tp_bold")
        italic = p.bool(forKey: "tp_italic")
        underline = p.bool(forKey: "tp_under")
        align = min(max(p.integer(forKey: "tp_align"), 0), 2)
        color = (p.object(forKey: "tp_color") as? Int).map { UInt32(truncatingIfNeeded: $0) }
    }

    func colorNow() -> UInt32 {
        let c = AppController.shared
        return color ?? c.colorFor(c.tool.isPen ? c.tool : c.lastPen)
    }

    /// حجم النص بنقاط صفحة عرضها pageW.
    func size(pageW: CGFloat) -> CGFloat { CGFloat(sizePt) * pageW / 595 }

    func apply(to b: TextBox, pageW: CGFloat) -> TextBox {
        var x = b
        x.font = font; x.size = size(pageW: pageW); x.bold = bold; x.italic = italic; x.underline = underline; x.align = align
        x.color = colorNow()
        return x
    }
}

/// خط مربع النص بنمطه.
func textBoxFont(_ b: TextBox, size: CGFloat) -> UIFont {
    var f: UIFont
    if b.font.isEmpty {
        f = Fonts.ui(size)
    } else if let x = FontStore.shared.uiFont(b.font, size: size) ?? UIFont(name: b.font, size: size) {
        f = x
    } else {
        f = Fonts.ui(size)
        let name = b.font
        Task { @MainActor in await FontStore.shared.ensure(name) }
    }
    var traits: UIFontDescriptor.SymbolicTraits = []
    if b.bold { traits.insert(.traitBold) }
    if b.italic { traits.insert(.traitItalic) }
    if !traits.isEmpty, let d = f.fontDescriptor.withSymbolicTraits(f.fontDescriptor.symbolicTraits.union(traits)) {
        f = UIFont(descriptor: d, size: size)
    }
    return f
}

func textBoxAlignment(_ b: TextBox) -> NSTextAlignment {
    switch b.align {
    case 1: return .center
    case 2: return I18n.english ? .right : .left
    default: return .natural
    }
}

/// نص المربع بنمطه.
func textBoxAttributed(_ b: TextBox, k: CGFloat) -> NSAttributedString {
    let ps = NSMutableParagraphStyle()
    ps.alignment = textBoxAlignment(b)
    ps.lineHeightMultiple = 1.1
    var a: [NSAttributedString.Key: Any] = [.font: textBoxFont(b, size: b.size * k), .foregroundColor: UIColor(argb: b.color), .paragraphStyle: ps]
    if b.underline { a[.underlineStyle] = NSUnderlineStyle.single.rawValue }
    return NSAttributedString(string: b.text, attributes: a)
}

// ---------------------------------------------------------------- الكتابة في المربع على الصفحة

final class InlineTextView: UITextView, UITextViewDelegate {
    let boxId: String
    var onChange: ((String) -> Void)?
    var onEnd: (() -> Void)?

    init(box: TextBox, k: CGFloat) {
        boxId = box.id
        super.init(frame: .zero, textContainer: nil)
        backgroundColor = UIColor.white.withAlphaComponent(0.35)
        layer.borderColor = UIColor(Palette.navy).cgColor
        layer.borderWidth = 1.5
        layer.cornerRadius = 4
        textContainerInset = .zero
        textContainer.lineFragmentPadding = 0
        isScrollEnabled = false
        delegate = self
        apply(box, k: k)
    }

    required init?(coder: NSCoder) { fatalError() }

    func apply(_ b: TextBox, k: CGFloat) {
        let sel = selectedRange
        font = textBoxFont(b, size: b.size * k)
        textColor = UIColor(argb: b.color)
        textAlignment = textBoxAlignment(b)
        var a: [NSAttributedString.Key: Any] = [.font: font as Any, .foregroundColor: UIColor(argb: b.color)]
        if b.underline { a[.underlineStyle] = NSUnderlineStyle.single.rawValue }
        typingAttributes = a
        if text != b.text || attributedText.length > 0 {
            let cur = text.isEmpty ? b.text : text
            attributedText = NSAttributedString(string: cur ?? "", attributes: a)
            textAlignment = textBoxAlignment(b)
        }
        selectedRange = sel
        let w = b.w * k
        let fit = sizeThatFits(CGSize(width: w, height: .greatestFiniteMagnitude))
        frame = CGRect(x: b.x * k, y: b.y * k, width: w, height: max(fit.height, b.size * k * 1.4))
    }

    func textViewDidChange(_ textView: UITextView) {
        let fit = sizeThatFits(CGSize(width: frame.width, height: .greatestFiniteMagnitude))
        frame.size.height = max(fit.height, (font?.lineHeight ?? 20) * 1.1)
        onChange?(text)
    }

    func textViewDidEndEditing(_ textView: UITextView) { onEnd?() }
}

extension DocSession {
    /// يبدأ الكتابة في مربع على الصفحة.
    func beginInlineEdit(_ id: String) {
        if let cur = inlineEditId, cur != id { finishInlineEdit() }
        inlineText = data.boxes.first(where: { $0.id == id })?.text ?? ""
        selectedBox = nil
        inlineEditId = id
    }

    /// ينهي الكتابة: يُحفظ النص، والمربع الفارغ يُحذف.
    func finishInlineEdit() {
        guard let id = inlineEditId else { return }
        inlineEditId = nil
        let t = inlineText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let b = data.boxes.first(where: { $0.id == id }) else { return }
        if t.isEmpty {
            edit { d in d.boxes.removeAll { $0.id == id } }
        } else if b.text != inlineText {
            edit { d in if let i = d.boxes.firstIndex(where: { $0.id == id }) { d.boxes[i].text = self.inlineText } }
        }
    }

    /// يضع مربع نص جديدًا بإعدادات أداة النص ويبدأ الكتابة فيه.
    func placeTextBox(pageId: String, at p: CGPoint, width: CGFloat? = nil) {
        guard let idx = index(of: pageId) else { return }
        let size = pageSize(idx)
        let tp = TextPrefs.shared
        let w = min(max(width ?? size.width * 0.4, size.width * 0.06), size.width - p.x)
        var b = TextBox(id: UUID().uuidString, page: pageId, x: max(0, p.x), y: max(0, p.y), w: max(20, w), text: "")
        b = tp.apply(to: b, pageW: size.width)
        b.y = max(0, p.y - b.size * 0.7)
        edit { d in d.boxes.append(b) }
        beginInlineEdit(b.id)
    }

    /// يطبّق إعدادات أداة النص على مربع.
    func applyTextPrefs(to id: String) {
        guard let b = data.boxes.first(where: { $0.id == id }), let idx = index(of: b.page) else { return }
        let nb = TextPrefs.shared.apply(to: b, pageW: pageSize(idx).width)
        edit { d in if let i = d.boxes.firstIndex(where: { $0.id == id }) { var x = nb; x.text = d.boxes[i].text; d.boxes[i] = x } }
    }
}

// ---------------------------------------------------------------- نافذة مربع النص

struct TextToolWindow: View {
    @EnvironmentObject var app: AppController
    @ObservedObject var tp = TextPrefs.shared
    @ObservedObject var fonts = FontStore.shared
    @ObservedObject var session: DocSession
    let onTurnOff: () -> Void
    @State private var fontPicker = false
    @State private var colorPicker = false

    private var target: String? { session.inlineEditId ?? session.selectedBox }
    private func apply() { if let t = target { session.applyTextPrefs(to: t) } }

    var body: some View {
        let col = tp.colorNow()
        VStack(alignment: .leading, spacing: 0) {
            ToolWindowHeader(icon: "ph_text", title: L("مربع النص", "Text box")).padding(.bottom, 8)
            SettingsCard {
                Text(L("الميتوكوندريا", "Mitochondria"))
                    .font(Font(textBoxFont(TextBox(id: "", page: "", x: 0, y: 0, w: 0, text: "", font: tp.font, bold: tp.bold, italic: tp.italic),
                                           size: min(40, max(14, CGFloat(tp.sizePt) * 1.6)))))
                    .underline(tp.underline)
                    .foregroundColor(Color(hex: col)).lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: tp.align == 1 ? .center : (tp.align == 2 ? .trailing : .leading))
                    .padding(.horizontal, 14).padding(.vertical, 12)
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color(hex: 0xFFFFFBF2)))
                    .padding(12)
            }
            SettingsCard {
                NavRow(title: L("الخط", "Font"), action: { fontPicker = true }) {
                    Text(tp.font.isEmpty ? L("الافتراضي", "Default") : tp.font)
                        .font(Font(fonts.uiFont(tp.font, size: 17) ?? Fonts.ui(17))).foregroundColor(Palette.navy).lineLimit(1)
                }
                RowDividerLine()
                HStack {
                    Text(L("الحجم", "Size")).font(Fonts.sans(16)).foregroundColor(Palette.textPrimary)
                    Spacer()
                    RoundButton(label: "−") { tp.sizePt = max(TextPrefs.sizeMin, tp.sizePt - 1); apply() }
                    Text("\(tp.sizePt)").font(Fonts.sans(16, .semibold)).foregroundColor(Palette.navy).frame(width: 44)
                    RoundButton(label: "+") { tp.sizePt = min(TextPrefs.sizeMax, tp.sizePt + 1); apply() }
                }
                .padding(.horizontal, 16).padding(.vertical, 10)
            }
            .padding(.top, 10)
            CardTitle(L("النمط", "Style"))
            SettingsCard {
                HStack(spacing: 0) {
                    toggle(tp.bold, { tp.bold.toggle(); apply() }) { Text("B").font(.system(size: 17, weight: .bold)) }
                    toggle(tp.italic, { tp.italic.toggle(); apply() }) { Text("I").font(.system(size: 17).italic()) }
                    toggle(tp.underline, { tp.underline.toggle(); apply() }) { Text("U").font(.system(size: 17)).underline() }
                    toggle(tp.align != 0, { tp.align = (tp.align + 1) % 3; apply() }) {
                        Text(tp.align == 1 ? "☰" : (tp.align == 2 ? "⇥" : "⇤")).font(.system(size: 17))
                    }
                }
                .padding(2).frame(height: 42)
                .background(RoundedRectangle(cornerRadius: 11).fill(Color(hex: 0xFFEEF0F3)))
                .padding(.horizontal, 14).padding(.vertical, 12)
                Text(tp.align == 1 ? L("المحاذاة: الوسط", "Alignment: center") : (tp.align == 2 ? L("المحاذاة: النهاية", "Alignment: end") : L("المحاذاة: البداية", "Alignment: start")))
                    .font(Fonts.sans(12)).foregroundColor(Muted).padding(.horizontal, 16).padding(.bottom, 12)
            }
            CardTitle(L("اللون", "Color"))
            SettingsCard {
                SwatchGrid(colors: app.penPalette + app.customColors, current: col, onPick: { tp.color = $0; apply() }, onCustom: { colorPicker = true })
                    .padding(.top, 8)
            }
            SettingsCard {
                Button(action: onTurnOff) {
                    Text(L("إيقاف أداة النص", "Turn off text tool")).font(Fonts.sans(15, .semibold)).foregroundColor(Palette.navy)
                        .frame(maxWidth: .infinity).padding(.vertical, 13)
                }
            }
            .padding(.top, 10)
        }
        .padding(12)
        .frame(width: 320)
        .background(SheetBg)
        .sheet(isPresented: $fontPicker) {
            FontLibrarySheet(sample: L("الميتوكوندريا", "Mitochondria"), current: tp.font) { if !$0.isEmpty { tp.font = $0; apply() } }
                .environmentObject(app)
        }
        .sheet(isPresented: $colorPicker) {
            TextColorPickerSheet(initial: col) { c in app.addCustomColor(c); tp.color = c; apply() }
        }
    }

    private func toggle<C: View>(_ on: Bool, _ action: @escaping () -> Void, @ViewBuilder _ content: () -> C) -> some View {
        Button(action: action) {
            content().foregroundColor(on ? Palette.navy : Muted)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background {
                    if on { RoundedRectangle(cornerRadius: 9).fill(Color.white).shadow(color: .black.opacity(0.12), radius: 2, y: 1) }
                }
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .padding(2)
    }
}

/// لون مخصص للنص.
struct TextColorPickerSheet: View {
    let initial: UInt32
    let onPick: (UInt32) -> Void
    @State private var color: Color = .black
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        SheetScaffold(title: L("لون مخصص", "Custom color")) {
            VStack(spacing: 18) {
                ColorPicker(L("اللون", "Color"), selection: $color, supportsOpacity: false).font(Fonts.sans(15))
                HStack {
                    Spacer()
                    NavyButton(label: L("إلغاء", "Cancel"), filled: false) { dismiss() }
                    NavyButton(label: L("اختيار", "Choose")) { onPick(color.argb | 0xFF000000); dismiss() }
                }
            }
            .padding(.horizontal, 22)
        }
        .onAppear { color = Color(hex: initial) }
        .presentationDetents([.height(240)])
    }
}

// ---------------------------------------------------------------- شريط مربع النص المحدد

/// شريط المربع المحدد (نفس TextBoxBar في أندرويد): تعديل، خط أصغر، خط أكبر، لون القلم، الخط،
/// فتح الرابط، الرجوع إلى الكتابة اليدوية، حذف، تم.
struct TextBoxBar: View {
    @ObservedObject var session: DocSession
    @EnvironmentObject var app: AppController
    @State private var colors = false
    @State private var fontSheet = false

    var body: some View {
        if let id = session.selectedBox, let b = session.data.boxes.first(where: { $0.id == id }) {
            HStack(spacing: 0) {
                if let d = b.linkDoc {
                    DialogButton(label: L("فتح الرابط", "Open link"), bold: true) { app.followLink(from: session, docId: d, page: b.linkPage) }
                }
                DialogButton(label: L("تعديل", "Edit")) { session.beginInlineEdit(id) }
                DialogButton(label: L("خط أصغر", "Smaller text")) { resize(id, 1 / 1.15) }
                DialogButton(label: L("خط أكبر", "Larger text")) { resize(id, 1.15) }
                DialogButton(label: L("لون القلم", "Pen color")) { colors = true }
                    .dropdown(isPresented: $colors) {
                        SwatchGrid(colors: app.penPalette + app.customColors, current: b.color, onPick: { c in
                            session.edit { d in if let i = d.boxes.firstIndex(where: { $0.id == id }) { d.boxes[i].color = c } }
                            colors = false
                        })
                        .padding(.top, 10).frame(width: 290)
                    }
                DialogButton(label: L("الخط", "Font")) { fontSheet = true }
                if b.ink != nil {
                    DialogButton(label: L("↶ رجوع إلى الكتابة اليدوية", "↶ Back to handwriting")) { session.selectedBox = nil; session.textBoxToInk(id) }
                }
                DialogButton(label: L("حذف", "Delete"), color: Palette.danger) {
                    session.selectedBox = nil
                    session.edit { d in d.boxes.removeAll { $0.id == id } }
                }
                DialogButton(label: L("تم", "Done")) { session.selectedBox = nil }
            }
            .padding(.horizontal, 6).padding(.vertical, 2)
            .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Color.white))
            .shadow(color: .black.opacity(0.16), radius: 8, y: 3)
            .sheet(isPresented: $fontSheet) {
                FontLibrarySheet(sample: b.text.isEmpty ? L("الميتوكوندريا", "Mitochondria") : b.text, current: b.font) { f in
                    if !f.isEmpty { session.edit { d in if let i = d.boxes.firstIndex(where: { $0.id == id }) { d.boxes[i].font = f } } }
                }
                .environmentObject(app)
            }
        }
    }

    private func resize(_ id: String, _ f: CGFloat) {
        session.edit { d in
            if let i = d.boxes.firstIndex(where: { $0.id == id }) {
                d.boxes[i].size = max(4, d.boxes[i].size * f)
                d.boxes[i].w *= f
            }
        }
    }
}
