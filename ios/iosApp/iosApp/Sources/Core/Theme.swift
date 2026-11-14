import SwiftUI
import UIKit

/// الثيم المختار. navy = داكن فاخر (افتراضي)، light = فاتح كلاسيكي.
/// الأيقونات والخطوط والذهبي نفسها في الثيمين؛ تتغير ألوان الأشرطة والخلفيات فقط.
enum AppTheme: String, Codable { case navy, light }

enum ThemeState {
    static var theme: AppTheme = .navy
    static var light: Bool { theme == .light }
}

extension Color {
    /// لون من 0xAARRGGBB أو 0xRRGGBB.
    init(hex: UInt32) {
        let a: Double = hex > 0xFFFFFF ? Double((hex >> 24) & 0xFF) / 255 : 1
        self.init(.sRGB,
                  red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255,
                  opacity: a)
    }

    /// ARGB كرقم (للحفظ).
    var argb: UInt32 {
        let c = UIColor(self)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        c.getRed(&r, green: &g, blue: &b, alpha: &a)
        func q(_ v: CGFloat) -> UInt32 { UInt32(max(0, min(255, (v * 255).rounded()))) }
        return (q(a) << 24) | (q(r) << 16) | (q(g) << 8) | q(b)
    }

    var luminance: Double {
        let c = UIColor(self)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        c.getRed(&r, green: &g, blue: &b, alpha: &a)
        return 0.2126 * Double(r) + 0.7152 * Double(g) + 0.0722 * Double(b)
    }
}

extension UIColor {
    convenience init(argb: UInt32) {
        let a: CGFloat = argb > 0xFFFFFF ? CGFloat((argb >> 24) & 0xFF) / 255 : 1
        self.init(red: CGFloat((argb >> 16) & 0xFF) / 255,
                  green: CGFloat((argb >> 8) & 0xFF) / 255,
                  blue: CGFloat(argb & 0xFF) / 255,
                  alpha: a)
    }

    var argb: UInt32 { Color(self).argb }
}

/// لون التمييز على الصفحة (Exporter.markColor في أندرويد): مزج مع الأبيض، ويُرسم بمزج «ضرب».
func markColor(_ argb: UInt32, strong: Bool = false) -> UIColor {
    let k: CGFloat = strong ? 0.9 : 0.55
    let c = UIColor(argb: argb)
    var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
    c.getRed(&r, green: &g, blue: &b, alpha: &a)
    return UIColor(red: 1 - k + k * r, green: 1 - k + k * g, blue: 1 - k + k * b, alpha: 1)
}

/// هوية هامش: مجلد دراسة فاخر. كحلي داكن، ذهبي كختم رقيق، وورق عاجي.
enum Palette {
    static let navy = Color(hex: 0xFF1B365D)
    static var ink: Color { ThemeState.light ? Color(hex: 0xFFE4E9F0) : Color(hex: 0xFF000000) }
    static var spine: Color { ThemeState.light ? .white : Color(hex: 0xFF0A0A0A) }
    static var inkRaised: Color { ThemeState.light ? Color(hex: 0xFFE8EDF5) : Color(hex: 0xFF1C1C1C) }
    static var inkLine: Color { ThemeState.light ? Color(hex: 0xFFE2E6EC) : Color(hex: 0xFF262626) }
    static let navySoft = Color(hex: 0xFFE8EDF5)

    static let gold = Color(hex: 0xFFC9A44C)
    static let goldDeep = Color(hex: 0xFF8C6A1F)
    static let goldSoft = Color(hex: 0xFFF4E9CB)

    static var background: Color { ThemeState.light ? Color(hex: 0xFFF3F5F8) : Color(hex: 0xFFF6F4EF) }
    static let ivory = Color(hex: 0xFFFFFBF2)
    static let drawerBg = Color(hex: 0xFFF9F6EF)
    static let divider = Color(hex: 0xFFE4DFD3)

    /// مكتب القراءة: رمادي أزرق هادئ يُظهر بياض الصفحات.
    static var desk: Color { ThemeState.light ? Color(hex: 0xFFEBEEF2) : Color(hex: 0xFFDCE1E8) }
    static var tabRail: Color { ThemeState.light ? Color(hex: 0xFFD3DAE4) : Color(hex: 0xFF000000) }
    static var tabIdle: Color { ThemeState.light ? Color(hex: 0xFFB9C4D3) : Color(hex: 0xFF1F1F1F) }

    static let textPrimary = Color(hex: 0xFF1A2230)
    static let textSecondary = Color(hex: 0xFF667085)
    static var onInk: Color { ThemeState.light ? Color(hex: 0xFF1A2230) : Color(hex: 0xFFF2F2F2) }
    static var onInkMuted: Color { ThemeState.light ? Color(hex: 0xFF667085) : Color(hex: 0xFF9A9A9A) }

    static let danger = Color(hex: 0xFFB3261E)
}

/// ألوان المكتبة: فاتح كريمي دافئ، أو داكن أسود تمامًا (يوفّر البطارية في شاشات OLED).
enum Lib {
    private static var L: Bool { ThemeState.light }
    static var bg: Color { L ? Color(hex: 0xFFFAF4E6) : Color(hex: 0xFF000000) }
    static var side: Color { L ? Color(hex: 0xFFFFFEFB) : Color(hex: 0xFF0A0A0A) }
    static var card: Color { L ? .white : Color(hex: 0xFF141414) }
    static var text: Color { L ? Color(hex: 0xFF1E2A44) : Color(hex: 0xFFF2F2F2) }
    static var muted: Color { L ? Color(hex: 0xFF6B7280) : Color(hex: 0xFF9A9A9A) }
    static var line: Color { L ? Color(hex: 0xFFEADFC8) : Color(hex: 0xFF262626) }
    static let accent = Color(hex: 0xFFF4C95D)
    static let accentDeep = Color(hex: 0xFFB8860B)
    static var accentSoft: Color { L ? Color(hex: 0xFFFCEBC2) : Color(hex: 0xFF2B2410) }
    static var resume: Color { L ? Color(hex: 0xFFFCEBBE) : Color(hex: 0xFF17140A) }
    static var strong: Color { L ? Color(hex: 0xFF1E2A44) : Color(hex: 0xFFF2F2F2) }
    static var onStrong: Color { L ? .white : Color(hex: 0xFF000000) }
    static let exam = Color(hex: 0xFFB0413E)
    static var iconBlue: Color { L ? Color(hex: 0xFF2F4B7C) : Color(hex: 0xFF8FA8D6) }
    static var study: Color { L ? Color(hex: 0xFFEEF2F9) : Color(hex: 0xFF10141C) }
    static let cover = Color(hex: 0xFF2F4B7C)
}

/// ألوان شاشات Premium.
enum Pw {
    static let gold = Color(hex: 0xFFF4C95D)
    static let goldDeep = Color(hex: 0xFFB8860B)
    static let ink = Color(hex: 0xFF1E2A44)
    static let muted = Color(hex: 0xFF6B7280)
    static let line = Color(hex: 0xFFEFE7D4)
    static let goldButton = LinearGradient(colors: [Color(hex: 0xFFF4C95D), Color(hex: 0xFFE9B73E)], startPoint: .leading, endPoint: .trailing)
}

let CardTeal = Color(hex: 0xFF0F766E)

/// الخط: Almarai لكل التطبيق (عربي ولاتيني بتصميم واحد)، وCaveat للعبارات اليدوية.
enum Fonts {
    /// حجم النص الذي يختاره المستخدم.
    static var scale: CGFloat = 1
    /// شاشة iPad (1376×1032 نقطة) أوسع من الجهاز اللوحي في أندرويد (1280×800):
    /// نكبّر النص بنفس النسبة فتبدو الواجهة بنفس تناسبها.
    static let base: CGFloat = 1.08

    static func sans(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        .custom(name(for: weight), size: size * scale * base)
    }

    /// العناوين وأسماء المستندات.
    static func display(_ size: CGFloat, bold: Bool = true) -> Font {
        .custom(bold ? "Almarai-ExtraBold" : "Almarai-Bold", size: size * scale * base)
    }

    static func script(_ size: CGFloat) -> Font { .custom("Caveat-Medium", size: size * scale * base) }

    static func name(for weight: Font.Weight) -> String {
        switch weight {
        case .light, .thin, .ultraLight: return "Almarai-Light"
        case .medium, .semibold, .bold: return "Almarai-Bold"
        case .heavy, .black: return "Almarai-ExtraBold"
        default: return "Almarai-Regular"
        }
    }

    static func ui(_ size: CGFloat, bold: Bool = false) -> UIFont {
        UIFont(name: bold ? "Almarai-Bold" : "Almarai-Regular", size: size) ?? .systemFont(ofSize: size, weight: bold ? .bold : .regular)
    }
}

extension View {
    /// نص بخط Almarai.
    func sans(_ size: CGFloat, _ weight: Font.Weight = .regular, _ color: Color? = nil) -> some View {
        self.font(Fonts.sans(size, weight)).foregroundColor(color)
    }
}

/// انضغاط خفيف عند اللمس لكل الأزرار.
struct PressScale: ButtonStyle {
    var scale: CGFloat = 0.95
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? scale : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

enum Motion {
    static let enter = Animation.easeOut(duration: 0.26)
    static let exit = Animation.easeIn(duration: 0.16)
    static let grow = Animation.spring(response: 0.34, dampingFraction: 0.78)
}
