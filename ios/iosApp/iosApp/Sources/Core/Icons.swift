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
