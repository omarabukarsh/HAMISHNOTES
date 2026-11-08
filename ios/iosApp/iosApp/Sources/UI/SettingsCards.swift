import SwiftUI

// عناصر نوافذ الأدوات بتصميم البطاقات (نفس BeautifySettings.kt وToolWindows.kt في أندرويد 1.0.271):
// خلفية رمادية فاتحة، بطاقات بيضاء بحواف 18، عناوين صغيرة رمادية، مفاتيح وشرائح اختيار.

let SheetBg = Color(hex: 0xFFF2F3F6)
let CardBg = Color.white
let RowDividerColor = Color(hex: 0xFFE9EBEF)
let Muted = Color(hex: 0xFF8A919E)

struct SettingsCard<Content: View>: View {
    @ViewBuilder let content: () -> Content
    var body: some View {
        VStack(alignment: .leading, spacing: 0) { content() }
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(CardBg))
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

struct CardTitle: View {
    let text: String
    init(_ t: String) { text = t }
    var body: some View {
        Text(text).font(Fonts.sans(13, .medium)).foregroundColor(Muted)
            .padding(.horizontal, 6).padding(.top, 18).padding(.bottom, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct RowDividerLine: View {
    var body: some View { Rectangle().fill(RowDividerColor).frame(height: 0.8).padding(.leading, 16) }
}

/// صف بعنوان (ووصف) ومفتاح.
struct CardSwitchRow: View {
    let title: String
    var subtitle: String? = nil
    @Binding var isOn: Bool
    var enabled = true
    var body: some View {
        Button { if enabled { isOn.toggle() } } label: {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(Fonts.sans(16)).foregroundColor(Palette.textPrimary).multilineTextAlignment(.leading)
                    if let s = subtitle {
                        Text(s).font(Fonts.sans(12.5)).foregroundColor(Muted).multilineTextAlignment(.leading).fixedSize(horizontal: false, vertical: true)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Toggle("", isOn: $isOn).labelsHidden().tint(Palette.navy).disabled(!enabled)
            }
            .padding(.horizontal, 16).padding(.vertical, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .opacity(enabled ? 1 : 0.5)
    }
}

/// صف بقيمة وسهم يفتح نافذة اختيار.
struct NavRow<V: View>: View {
    let title: String
    let action: () -> Void
    @ViewBuilder let value: () -> V
    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Text(title).font(Fonts.sans(16)).foregroundColor(Palette.textPrimary)
                Spacer(minLength: 8)
                value()
                Text("›").font(.system(size: 22)).foregroundColor(Muted)
            }
            .padding(.horizontal, 16).padding(.vertical, 14)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// شريط اختيارات متجاورة: الخيار المختار بخلفية بيضاء تنزلق إليه.
struct CardSegmented<T: Hashable>: View {
    let options: [(T, String)]
    let selected: T
    let onSelect: (T) -> Void
    @Namespace private var ns

    var body: some View {
        HStack(spacing: 0) {
            ForEach(options, id: \.0) { v, label in
                let sel = v == selected
                Button { withAnimation(.easeOut(duration: 0.2)) { onSelect(v) } } label: {
                    Text(label).font(Fonts.sans(14, sel ? .semibold : .regular))
                        .foregroundColor(sel ? Palette.navy : Muted).lineLimit(1)
                        .frame(maxWidth: .infinity).frame(height: 36)
                        .background {
                            if sel {
                                RoundedRectangle(cornerRadius: 10, style: .continuous).fill(Color.white)
                                    .shadow(color: .black.opacity(0.12), radius: 2, y: 1)
                                    .matchedGeometryEffect(id: "seg", in: ns)
                            }
                        }
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(3)
        .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Color(hex: 0xFFEEF0F3)))
        .padding(.horizontal, 16).padding(.vertical, 12)
    }
}

/// صف برقم وزرّي − و+ وشريط.
struct StepperRow: View {
    let title: String
    let value: Double
    let range: ClosedRange<Double>
    let step: Double
    let format: (Double) -> String
    let onChange: (Double) -> Void
    var body: some View {
        VStack(spacing: 4) {
            HStack {
                Text(title).font(Fonts.sans(15)).foregroundColor(Palette.textPrimary)
                Spacer()
                RoundButton(label: "−") { onChange(min(max(value - step, range.lowerBound), range.upperBound)) }
                Text(format(value)).font(Fonts.sans(15, .semibold)).foregroundColor(Palette.navy).frame(width: 48)
                RoundButton(label: "+") { onChange(min(max(value + step, range.lowerBound), range.upperBound)) }
            }
            Slider(value: Binding(get: { value }, set: { onChange(min(max(($0 / step).rounded() * step, range.lowerBound), range.upperBound)) }),
                   in: range).tint(Palette.navy)
        }
        .padding(.horizontal, 16).padding(.vertical, 10)
    }
}

struct RoundButton: View {
    let label: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label).font(.system(size: 18)).foregroundColor(Palette.navy)
                .frame(width: 30, height: 30).background(Circle().fill(Color(hex: 0xFFEEF0F3)))
        }
        .buttonStyle(PressScale())
    }
}

/// عنوان نافذة أداة: أيقونة في مربع كحلي واسم الأداة.
struct ToolWindowHeader: View {
    let icon: String
    let title: String
    var body: some View {
        HStack(spacing: 10) {
            PhIcon(icon, .white, 18).frame(width: 32, height: 32)
                .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(Palette.navy))
            Text(title).font(Fonts.sans(17, .bold)).foregroundColor(Palette.textPrimary)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 4).padding(.top, 2).padding(.bottom, 4)
    }
}

/// دوائر الألوان (ستة في الصف) مع «+» للون مخصص.
struct SwatchGrid: View {
    let colors: [UInt32]
    let current: UInt32
    let onPick: (UInt32) -> Void
    var onCustom: (() -> Void)? = nil
    var body: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.fixed(44), spacing: 0), count: 6), alignment: .leading, spacing: 0) {
            ForEach(colors, id: \.self) { c in
                let sel = c == current
                Button { onPick(c) } label: {
                    Circle().fill(Color(hex: c)).frame(width: sel ? 27 : 30, height: sel ? 27 : 30)
                        .overlay(Circle().stroke(Color.black.opacity(0.08), lineWidth: 1))
                        .padding(sel ? 4.5 : 3)
                        .overlay(Circle().stroke(sel ? Palette.navy : Color.clear, lineWidth: 2.5))
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(PressScale())
            }
            if let custom = onCustom {
                Button(action: custom) {
                    Text("+").font(.system(size: 18)).foregroundColor(Muted)
                        .frame(width: 30, height: 30).overlay(Circle().stroke(Color(hex: 0xFFB9BFCA), lineWidth: 1.5))
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(PressScale())
            }
        }
        .padding(.horizontal, 10).padding(.bottom, 10)
    }
}

/// شريط حجم من 10 درجات مع قيمته.
struct SizeSliderRow: View {
    let title: String
    let index: Int
    let valueText: String
    let onChange: (Int) -> Void
    var body: some View {
        VStack(spacing: 4) {
            HStack {
                Text(title).font(Fonts.sans(15)).foregroundColor(Palette.textPrimary)
                Spacer()
                Text(valueText).font(Fonts.sans(14, .semibold)).foregroundColor(Palette.navy)
            }
            Slider(value: Binding(get: { Double(index) }, set: { onChange(min(max(Int($0.rounded()), 0), Sizes.count - 1)) }),
                   in: 0...Double(Sizes.count - 1), step: 1).tint(Palette.navy)
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
    }
}
