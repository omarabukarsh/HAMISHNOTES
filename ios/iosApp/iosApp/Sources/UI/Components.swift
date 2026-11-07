import SwiftUI

/// زر أداة في شريط الأدوات: خلفية عند الاختيار، ومؤشر ذهبي صغير أسفلها، وقفل ذهبي للأدوات المدفوعة.
struct TBtn<Content: View>: View {
    var selected = false
    var enabled = true
    var locked = false
    let action: () -> Void
    @ViewBuilder let content: () -> Content

    var body: some View {
        Button(action: action) {
            ZStack {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(selected ? Palette.navySoft : Color.clear)
                content()
                if locked {
                    PremiumBadge(size: 13).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing).padding(3)
                }
                if selected {
                    RoundedRectangle(cornerRadius: 2).fill(Palette.gold).frame(width: 14, height: 3)
                        .frame(maxHeight: .infinity, alignment: .bottom).padding(.bottom, 3)
                        .transition(.scale(scale: 0.3).combined(with: .opacity))
                }
            }
            .frame(width: 42, height: 42)
            .contentShape(Rectangle())
            .animation(Motion.enter, value: selected)
        }
        .buttonStyle(PressScale(scale: 0.9))
        .disabled(!enabled)
    }
}

func tintFor(_ selected: Bool) -> Color { selected ? Palette.navy : Palette.textSecondary }

struct ToolbarSep: View {
    var body: some View { Rectangle().fill(Palette.divider).frame(width: 1, height: 26).padding(.horizontal, 6) }
}

/// شريحة اختيار.
struct ChoiceChip: View {
    let label: String
    let selected: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label)
                .font(Fonts.sans(14, selected ? .medium : .regular))
                .foregroundColor(selected ? .white : Palette.textPrimary)
                .lineLimit(1).fixedSize()
                .padding(.horizontal, 15).padding(.vertical, 7)
                .background(Capsule().fill(selected ? Palette.navy : Color.white))
                .overlay(Capsule().stroke(selected ? Palette.navy : Palette.divider, lineWidth: 1))
        }
        .buttonStyle(PressScale())
    }
}

/// قفل ذهبي صغير على زاوية زر أداة مدفوعة.
struct PremiumBadge: View {
    var size: CGFloat = 14
    var body: some View {
        ZStack {
            Circle().fill(Pw.gold)
            PhIcon("ph_lock", Pw.ink, size * 0.62)
        }
        .frame(width: size, height: size)
    }
}

/// زر دائري بأيقونة فقط.
struct IconCircleButton: View {
    @Environment(\.colorScheme) private var themeTick // يعيد الرسم عند تغيير الثيم
    let icon: String
    let label: String
    var selected = false
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            PhIcon(icon, Lib.text, 20, label)
                .frame(width: 46, height: 46)
                .background(Circle().fill(selected ? Lib.accentSoft : Lib.card))
                .overlay(Circle().stroke(Lib.line, lineWidth: 1))
        }
        .buttonStyle(PressScale())
    }
}

/// زر الإغلاق الدائري للنوافذ.
struct CloseButton: View {
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            PhIcon("ph_close", Palette.textSecondary, 16, L("إغلاق", "Close"))
                .frame(width: 34, height: 34)
                .background(Circle().fill(Color(hex: 0xFFE8EBF0)))
        }
        .buttonStyle(PressScale())
    }
}

/// زر ذهبي عريض (نوافذ Premium).
struct GoldButton: View {
    let label: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label).font(Fonts.sans(16, .bold)).foregroundColor(Pw.ink)
                .frame(maxWidth: .infinity).frame(height: 52)
                .background(Capsule().fill(Pw.goldButton))
                .shadow(color: Pw.goldDeep.opacity(0.35), radius: 6, y: 3)
        }
        .buttonStyle(PressScale(scale: 0.97))
    }
}

struct QuietButton: View {
    let label: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label).font(Fonts.sans(14, .bold)).foregroundColor(Pw.muted)
                .frame(maxWidth: .infinity).frame(height: 46)
        }
    }
}

/// زر نصي بلون الكحلي (لنوافذ الحوار).
struct NavyButton: View {
    let label: String
    var filled = true
    var danger = false
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label).font(Fonts.sans(15, .bold))
                .foregroundColor(filled ? .white : (danger ? Palette.danger : Palette.navy))
                .padding(.horizontal, 18).padding(.vertical, 11)
                .background(RoundedRectangle(cornerRadius: 12).fill(filled ? (danger ? Palette.danger : Palette.navy) : Color.clear))
        }
        .buttonStyle(PressScale())
    }
}

/// عنوان كبير بخط أصفر منحنٍ تحته.
struct UnderlinedTitle: View {
    @Environment(\.colorScheme) private var themeTick // يعيد الرسم عند تغيير الثيم
    let text: String
    var size: CGFloat = 40
    var body: some View {
        Text(text)
            .font(Fonts.display(size))
            .foregroundColor(Lib.text)
            .lineLimit(1)
            .background(alignment: .bottom) {
                GeometryReader { g in
                    Path { p in
                        let y = g.size.height - 2
                        p.move(to: CGPoint(x: 0, y: y))
                        p.addQuadCurve(to: CGPoint(x: g.size.width, y: y - 3), control: CGPoint(x: g.size.width * 0.5, y: y + 8))
                    }
                    .stroke(Lib.accent, style: StrokeStyle(lineWidth: 3.5, lineCap: .round))
                }
            }
    }
}

/// نص بخط يدوي (Caveat) مثل «تابع من حيث توقفت».
struct ScriptLabel: View {
    @Environment(\.colorScheme) private var themeTick // يعيد الرسم عند تغيير الثيم
    let text: String
    var color: Color = Lib.accentDeep
    var size: CGFloat = 16
    var body: some View { Text(text).font(Fonts.script(size + 4)).foregroundColor(color) }
}

/// شارة صغيرة: أيقونة المؤقت + الوقت.
struct ReadingTimeBadge: View {
    let ms: Int64
    var tint: Color = Palette.textSecondary
    var size: CGFloat = 12
    var showIcon = true
    var body: some View {
        HStack(spacing: 4) {
            if showIcon { PhIcon("ph_timer", tint, size + 3) }
            Text(formatReading(ms)).font(Fonts.sans(size)).foregroundColor(tint)
        }
    }
}

/// بطاقة نافذة بيضاء مستديرة.
struct SheetCard<Content: View>: View {
    @ViewBuilder let content: () -> Content
    var body: some View {
        VStack(spacing: 0) { content() }
            .padding(.horizontal, 28).padding(.top, 28).padding(.bottom, 14)
            .frame(maxWidth: 420)
            .background(RoundedRectangle(cornerRadius: 26, style: .continuous).fill(Color.white))
    }
}

/// نافذة حوار بسيطة: عنوان، محتوى، وأزرار.
struct DialogCard<Content: View>: View {
    let title: String
    var width: CGFloat = 460
    @ViewBuilder let content: () -> Content
    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(title).font(Fonts.display(20)).foregroundColor(Palette.navy)
            content()
        }
        .padding(22)
        .frame(maxWidth: width)
        .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(Palette.ivory))
        .shadow(color: .black.opacity(0.18), radius: 24, y: 8)
    }
}

/// خلفية معتمة تغلق النافذة عند النقر.
struct Scrim: View {
    let onTap: () -> Void
    var body: some View {
        Color.black.opacity(0.32).ignoresSafeArea().onTapGesture(perform: onTap).transition(.opacity)
    }
}

/// حقل نص بإطار ناعم.
struct FieldBox: View {
    let placeholder: String
    @Binding var text: String
    var multiline = false
    var body: some View {
        Group {
            if multiline {
                TextField(placeholder, text: $text, axis: .vertical).lineLimit(4...12)
            } else {
                TextField(placeholder, text: $text)
            }
        }
        .font(Fonts.sans(15))
        .foregroundColor(Palette.textPrimary)
        .padding(.horizontal, 14).padding(.vertical, 12)
        .background(RoundedRectangle(cornerRadius: 12).fill(Color.white))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Palette.divider, lineWidth: 1))
    }
}

/// صف ألوان الورقة.
struct PaperColorRow: View {
    let selected: UInt32
    let onSelect: (UInt32) -> Void
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(Templates.paperColors, id: \.0) { item in
                    let argb = item.0
                    let name = item.1
                    let sel = argb == selected
                    Button { onSelect(argb) } label: {
                        Circle().fill(Color(hex: argb)).frame(width: 36, height: 36)
                            .overlay(Circle().stroke(sel ? Palette.gold : Palette.divider, lineWidth: sel ? 3 : 1))
                    }
                    .accessibilityLabel(name)
                }
            }
            .padding(.vertical, 4)
        }
    }
}

/// المادة: شرائح المواد للاختيار.
struct SubjectChips: View {
    @EnvironmentObject var app: AppController
    let selected: String
    let onSelect: (String) -> Void
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ChoiceChip(label: L("بدون مادة", "No subject"), selected: selected.isEmpty) { onSelect("") }
                ForEach(app.folders, id: \.self) { f in
                    ChoiceChip(label: f, selected: selected == f) { onSelect(f) }
                }
            }
        }
    }
}

/// حلقة هدف اليوم.
struct GoalRing: View {
    @EnvironmentObject var app: AppController
    var size: CGFloat = 40
    var line: CGFloat = 4
    var track: Color = Lib.line
    var flame = true
    var body: some View {
        let today = app.stats[app.todayKey()] ?? 0
        let frac = min(1, Double(today) / Double(max(1, app.goalMinutes * 60_000)))
        ZStack {
            Circle().stroke(track, lineWidth: line)
            Circle().trim(from: 0, to: frac).stroke(Palette.gold, style: StrokeStyle(lineWidth: line, lineCap: .round))
                .rotationEffect(.degrees(-90))
            if flame {
                ZStack {
                    FlameShape().fill(Color(hex: 0xFFE8864A))
                    FlameShape().stroke(Color(hex: 0xFFC45F2A), style: StrokeStyle(lineWidth: 1, lineJoin: .round))
                }
                .frame(width: size * 0.46, height: size * 0.46)
            }
        }
        .padding(line / 2)
        .frame(width: size, height: size)
    }
}

/// تنبيه صغير أسفل الشاشة.
struct ToastView: View {
    let text: String
    var body: some View {
        Text(text)
            .font(Fonts.sans(14, .medium))
            .foregroundColor(.white)
            .multilineTextAlignment(.center)
            .padding(.horizontal, 18).padding(.vertical, 12)
            .background(Capsule().fill(Color(hex: 0xFF1E2A44).opacity(0.94)))
            .shadow(color: .black.opacity(0.2), radius: 10, y: 4)
            .padding(.bottom, 24)
            .transition(.move(edge: .bottom).combined(with: .opacity))
    }
}

/// نافذة كبيرة في وسط الشاشة (مثل نوافذ أندرويد: 94% من العرض حتى 900 نقطة).
struct BigSheet<C: View>: ViewModifier {
    @Binding var isPresented: Bool
    /// نسبة العرض والارتفاع من الشاشة (كما في نوافذ أندرويد: fillMaxWidth / fillMaxHeight).
    var widthFraction: CGFloat = 0.94
    var heightFraction: CGFloat = 0.92
    var maxHeight: CGFloat = .infinity
    var minHeight: CGFloat = 0
    var maxWidth: CGFloat = .infinity
    /// الارتفاع على قدر المحتوى (بين minHeight وmaxHeight) كما في نوافذ أندرويد heightIn.
    var fitContent = false
    let sheet: () -> C

    func body(content: Content) -> some View {
        content.fullScreenCover(isPresented: $isPresented) {
            ZStack {
                Color.black.opacity(0.32).ignoresSafeArea().onTapGesture { isPresented = false }
                GeometryReader { g in
                    Group {
                        if fitContent {
                            sheet()
                                .frame(width: min(maxWidth, g.size.width * widthFraction))
                                .frame(minHeight: minHeight)
                                .fixedSize(horizontal: false, vertical: true)
                                .frame(maxHeight: min(maxHeight, g.size.height * 0.96))
                        } else {
                            sheet()
                                .frame(width: min(maxWidth, g.size.width * widthFraction),
                                       height: min(g.size.height * 0.96, max(minHeight, min(maxHeight, g.size.height * heightFraction))))
                        }
                    }
                        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
                        .shadow(color: .black.opacity(0.25), radius: 30, y: 10)
                        .position(x: g.size.width / 2, y: g.size.height / 2)
                }
            }
            .presentationBackground(.clear)
        }
    }
}

extension View {
    func bigSheet<C: View>(isPresented: Binding<Bool>, width: CGFloat = 0.94, height: CGFloat = 0.92, maxHeight: CGFloat = .infinity,
                           minHeight: CGFloat = 0, maxWidth: CGFloat = .infinity, fitContent: Bool = false,
                           @ViewBuilder _ sheet: @escaping () -> C) -> some View {
        modifier(BigSheet(isPresented: isPresented, widthFraction: width, heightFraction: height, maxHeight: maxHeight, minHeight: minHeight,
                          maxWidth: maxWidth, fitContent: fitContent, sheet: sheet))
    }

    /// إطار مستدير بخط رفيع.
    func roundedBorder(_ color: Color, radius: CGFloat, width: CGFloat = 1) -> some View {
        overlay(RoundedRectangle(cornerRadius: radius, style: .continuous).stroke(color, lineWidth: width))
    }

    func card(_ fill: Color, radius: CGFloat) -> some View {
        background(RoundedRectangle(cornerRadius: radius, style: .continuous).fill(fill))
    }
}
