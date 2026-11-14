import SwiftUI

/// أيقونة Phosphor من مكتبة الأصول (نفس أيقونات تطبيق أندرويد، محوّلة إلى SVG).
struct PhIcon: View {
    let name: String
    var tint: Color = Palette.textSecondary
    var size: CGFloat = 22
    var label: String? = nil

    init(_ name: String, _ tint: Color = Palette.textSecondary, _ size: CGFloat = 22, _ label: String? = nil) {
        self.name = name
        self.tint = tint
        self.size = size
        self.label = label
    }

    var body: some View {
        Image(name)
            .renderingMode(.template)
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .foregroundColor(tint)
            .accessibilityLabel(label ?? "")
            .accessibilityHidden(label == nil)
    }
}

/// رسم ملون (شعار، مجلد، دفتر) بألوانه الأصلية.
struct Art: View {
    let name: String
    var size: CGFloat
    var body: some View {
        Image(name).resizable().scaledToFit().frame(width: size, height: size)
    }
}

/// اسم الأيقونة المملوءة إن وُجدت.
func filledIcon(_ name: String) -> String {
    let f = name + "_fill"
    return UIImage(named: f) != nil ? f : name
}

/// شعار التطبيق (PNG من موارد أندرويد).
struct AppLogo: View {
    var size: CGFloat
    var corner: CGFloat = 14
    var body: some View {
        Group {
            if let path = Bundle.main.path(forResource: "logo_app", ofType: "png", inDirectory: "drawable-nodpi"),
               let img = UIImage(contentsOfFile: path) {
                Image(uiImage: img).resizable().scaledToFit()
            } else {
                Image("ic_logo").resizable().scaledToFit()
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: corner, style: .continuous))
    }
}

/// ملصق غلاف الدفتر (رسومات أندرويد نفسها)، أو إيموجي قديم كنص.
struct StickerIcon: View {
    let key: String
    var size: CGFloat
    var body: some View {
        if UIImage(named: "st_" + key) != nil {
            Image("st_" + key).resizable().scaledToFit().frame(width: size, height: size)
        } else if let img = badgeImage(key) {
            Image(uiImage: img).resizable().scaledToFit().frame(width: size * 1.25, height: size)
        } else {
            Text(key).font(.system(size: size * 0.85))
        }
    }
}

let CoverStickers: [String] = [
    "pill", "ribbon", "brain", "heart", "lungs", "dna", "germ", "tooth", "bone", "syringe",
    "microscope", "flask", "book", "pencil", "star", "leaf", "bolt", "target", "trophy", "globe"
]

let CoverColors: [UInt32] = [
    0xFF2F4B7C, 0xFF1F6F6B, 0xFF7A3E48, 0xFF5B4B8A, 0xFF8A5A2B,
    0xFF2E2E2E, 0xFF3F6E3A, 0xFFB5651D, 0xFF9C2F2F, 0xFF486581
]

/// شارة نصية ملصقًا (مفتاحها "t:نص|لون").
func badgeKey(_ text: String, _ color: UInt32) -> String { "t:" + text + "|" + String(color, radix: 16) }

func badgeParts(_ key: String) -> (String, UInt32)? {
    guard key.hasPrefix("t:"), let bar = key.lastIndex(of: "|") else { return nil }
    let text = String(key[key.index(key.startIndex, offsetBy: 2)..<bar])
    let col = UInt32(key[key.index(after: bar)...], radix: 16) ?? 0xFF1B365D
    return (text, col)
}

/// صورة الشارة النصية (بيضاء على لونها، بحواف مستديرة).
func badgeImage(_ key: String) -> UIImage? {
    guard let (text, col) = badgeParts(key) else { return nil }
    let font = UIFont.systemFont(ofSize: 120, weight: .bold)
    let tw = (text as NSString).size(withAttributes: [.font: font]).width
    let size = CGSize(width: max(200, tw + 120), height: 200)
    return UIGraphicsImageRenderer(size: size).image { _ in
        UIColor(argb: col).setFill()
        UIBezierPath(roundedRect: CGRect(x: 6, y: 6, width: size.width - 12, height: size.height - 12), cornerRadius: 60).fill()
        let a: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: UIColor.white]
        let ts = (text as NSString).size(withAttributes: a)
        (text as NSString).draw(at: CGPoint(x: (size.width - ts.width) / 2, y: (size.height - ts.height) / 2), withAttributes: a)
    }
}

/// أقسام الملصقات الجاهزة (نفس StickerStore.categories في أندرويد).
var StickerCategories: [(String, [String])] {
    [
        (L("طب", "Medical"), CoverStickers),
        (L("تنبيهات", "Labels"), [
            badgeKey(L("مهم", "Important"), 0xFFD9467A), badgeKey(L("امتحان", "Exam"), 0xFF2F6FDB),
            badgeKey(L("راجع", "Review"), 0xFFE08A1E), badgeKey(L("سؤال", "Question"), 0xFF6D4AC2),
            badgeKey(L("تعريف", "Definition"), 0xFF0F766E), badgeKey(L("قاعدة", "Rule"), 0xFF1B365D),
            badgeKey(L("مثال", "Example"), 0xFF2E9D6B), badgeKey(L("ملاحظة", "Note"), 0xFF8C6A1F),
            badgeKey(L("احفظ", "Memorize"), 0xFFB4232A), badgeKey("✓ " + L("تم", "Done"), 0xFF1F7A4D),
            badgeKey("!!", 0xFFB4232A), badgeKey("?", 0xFF6D4AC2)
        ]),
        (L("دراسة", "Study"), "📚 ✏️ 📝 📌 📎 🗂️ ⏰ 💡 ❓ ✅ ❌ ⚠️ 🎯 🔖 🧠 📊 📅 🏆 ⭐ 🔥".split(separator: " ").map(String.init)),
        (L("علوم", "Science"), "🧪 🧬 🦠 🔬 🫀 🫁 🦷 🦴 💊 💉 🩺 ⚗️ 🌡️ 🧫 🩸 👁️ ⚛️ 🧲 🌍".split(separator: " ").map(String.init)),
        (L("وجوه", "Faces"), "😀 😅 😴 🤯 🥳 😎 🤔 😭 😍 🙃 👍 👏 🙏 💪 ❤️ 💯".split(separator: " ").map(String.init))
    ]
}
