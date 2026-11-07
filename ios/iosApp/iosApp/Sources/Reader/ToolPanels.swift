import SwiftUI
import PencilKit

// لوحات إعدادات الأدوات (نفس ToolPanels.kt وToolSettingsPanel في أندرويد): تنسدل تحت زر الأداة
// عند الضغط عليه مرة ثانية. لكل أداة: اللون، الحجم، وخياراتها الخاصة.

// ---------------------------------------------------------------- عناصر مشتركة

/// مفتاح صغير في لوحات الأدوات.
struct PanelSwitch: View {
    let label: String
    var icon: String? = nil
    @Binding var isOn: Bool
    var body: some View {
        Button { isOn.toggle() } label: {
            HStack(spacing: 8) {
                if let i = icon { PhIcon(i, Palette.textPrimary, 16) }
                Text(label).font(Fonts.sans(13)).foregroundColor(Palette.textPrimary).lineLimit(2)
                    .multilineTextAlignment(.leading).frame(maxWidth: .infinity, alignment: .leading)
                Toggle("", isOn: $isOn).labelsHidden().tint(Palette.navy).scaleEffect(0.72).frame(width: 40, height: 26)
            }
            .frame(minHeight: 36)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// شريط نسبة مئوية بعنوانه.
struct PercentSlider: View {
    let label: String
    @Binding var value: Double
    var steps: Int = 3
    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text(label).font(Fonts.sans(12)).foregroundColor(Palette.textSecondary)
                Spacer()
                Text("\(Int((value * 100).rounded()))%").font(Fonts.sans(12)).foregroundColor(Palette.textSecondary)
            }
            if steps > 0 {
                Slider(value: $value, in: 0...1, step: 1 / Double(steps + 1)).tint(Palette.navy)
            } else {
                Slider(value: $value, in: 0...1).tint(Palette.navy)
            }
        }
    }
}

struct PanelDivider: View {
    var body: some View { Rectangle().fill(Palette.divider).frame(height: 1).padding(.vertical, 6) }
}

struct PanelLabel: View {
    let text: String
    init(_ t: String) { text = t }
    var body: some View {
        Text(text).font(Fonts.sans(12)).foregroundColor(Palette.textSecondary).frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// خيار في صف: بطاقة صغيرة فيها رسم وعنوان.
struct OptionCard<Art: View>: View {
    let label: String
    let selected: Bool
    let action: () -> Void
    @ViewBuilder let art: () -> Art
    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                art()
                Text(label).font(Fonts.sans(11)).foregroundColor(selected ? .white : Palette.textPrimary).lineLimit(1)
            }
            .frame(maxWidth: .infinity).padding(.vertical, 6)
            .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(selected ? Palette.navy : Palette.background))
        }
        .buttonStyle(PressScale())
    }
}

/// شريط الحجم المقسّم إلى 10 أقسام: المس أو اسحب لتغيير الحجم (نفس SizeBar في أندرويد).
struct SizeBar: View {
    let value: Int
    let onChange: (Int) -> Void
    var height: CGFloat = 28
    var body: some View {
        GeometryReader { g in
            let n = Sizes.count
            let gap: CGFloat = 3
            let segW = (g.size.width - gap * CGFloat(n - 1)) / CGFloat(n)
            ZStack(alignment: .bottomLeading) {
                ForEach(0..<n, id: \.self) { i in
                    let frac = CGFloat(i + 1) / CGFloat(n)
                    let h = g.size.height * (0.28 + 0.72 * frac)
                    RoundedRectangle(cornerRadius: 3).fill(i <= value ? Palette.navy : Palette.divider)
                        .frame(width: segW, height: h)
                        .offset(x: CGFloat(i) * (segW + gap))
                }
            }
            .frame(width: g.size.width, height: g.size.height, alignment: .bottomLeading)
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0).onChanged { v in
                let i = Int((v.location.x / max(1, g.size.width)) * CGFloat(n))
                let c = max(0, min(n - 1, i))
                if c != value { onChange(c) }
            })
        }
        .frame(height: height)
    }
}

/// ألوان الأداة الحالية في صفوف من خمسة، وزر «لون مخصص…».
struct ColorMenuContent: View {
    @EnvironmentObject var app: AppController
    let onCustom: () -> Void
    var body: some View {
        let current = app.colorFor(app.tool)
        let colors = app.paletteFor(app.tool) + app.customColors
        VStack(alignment: .leading, spacing: 6) {
            PanelLabel(L("اللون", "Color"))
            LazyVGrid(columns: Array(repeating: GridItem(.fixed(40), spacing: 4), count: 5), alignment: .leading, spacing: 4) {
                ForEach(colors, id: \.self) { col in
                    let sel = col == current
                    Button { app.setColor(col) } label: {
                        Circle().fill(Color(hex: col)).frame(width: sel ? 30 : 26, height: sel ? 30 : 26)
                            .overlay(Circle().stroke(sel ? Palette.navy : Palette.divider, lineWidth: sel ? 2.5 : 1))
                            .frame(width: 40, height: 40)
                    }
                    .buttonStyle(PressScale())
                }
            }
            Button(action: onCustom) {
                Text(L("لون مخصص…", "Custom color…")).font(Fonts.sans(14)).foregroundColor(Palette.navy)
                    .padding(.horizontal, 14).padding(.vertical, 8)
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Palette.divider, lineWidth: 1))
            }
            .buttonStyle(PressScale())
        }
    }
}

private func mm(_ w: CGFloat) -> String { String(format: "%.2f mm", Double(w / 600 * 210)) }

// ---------------------------------------------------------------- لوحة الأداة

let InkPens: [Tool] = [.fountain, .ballpoint, .brush]

private func penShortName(_ t: Tool) -> String {
    switch t {
    case .fountain: return L("حبر", "Fountain")
    case .ballpoint: return L("جاف", "Ballpoint")
    case .brush: return L("فرشاة", "Brush")
    default: return t.title
    }
}

/// إعدادات الأداة الحالية: النوع (للأقلام)، اللون، الحجم، وخيارات خاصة.
struct ToolSettingsPanel: View {
    @EnvironmentObject var app: AppController
    @ObservedObject private var pm = Premium.shared
    let onCustomColor: () -> Void
    @State private var label = ""
    @State private var confirmClear = false

    var body: some View {
        let t = app.tool
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                PhIcon(filledIcon(t.icon), Palette.navy, 18)
                Text(t.title).font(Fonts.sans(14, .bold)).foregroundColor(Palette.textPrimary)
            }
            // شريط الأقلام: الرصاص زر مستقل؛ الشريط الكلاسيكي: الرصاص ضمن الأنواع
            let penTypes: [Tool] = app.toolbarStyle == .penBar ? InkPens : InkPens + [.pencil]
            if penTypes.contains(t) {
                HStack(spacing: 6) {
                    ForEach(penTypes, id: \.self) { pt in
                        let sel = pt == t
                        Button { app.selectTool(pt) } label: {
                            VStack(spacing: 2) {
                                PhIcon(sel ? filledIcon(pt.icon) : pt.icon, sel ? .white : Palette.textPrimary, 18)
                                Text(penShortName(pt)).font(Fonts.sans(11)).foregroundColor(sel ? .white : Palette.textPrimary).lineLimit(1)
                            }
                            .frame(maxWidth: .infinity).padding(.vertical, 6)
                            .background(RoundedRectangle(cornerRadius: 10).fill(sel ? Palette.navy : Palette.background))
                        }
                        .buttonStyle(PressScale())
                    }
                }
            }
            if t != .eraser { ColorMenuContent(onCustom: onCustomColor) }

            if !t.isPen {
                PanelLabel(t == .eraser ? L("حجم الممحاة", "Eraser size") : L("الحجم", "Size"))
                SizeBar(value: app.sizeFor(t), onChange: { app.setSize($0) })
            }

            if t == .highlighter { highlighterSection }
            if t.isPen { penSection(t) }
            if t == .laser { laserSection }
            if t == .eraser { eraserSection }
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .frame(width: 280)
        .onAppear { label = app.labelOf(app.colorFor(.highlighter)) }
        .onChange(of: app.colorFor(.highlighter)) { _, c in label = app.labelOf(c) }
        .alert(L("مسح الصفحة؟", "Clear page?"), isPresented: $confirmClear) {
            Button(L("إلغاء", "Cancel"), role: .cancel) {}
            Button(L("مسح", "Clear"), role: .destructive) {
                if let s = app.tabs.first(where: { $0.id == app.activeId }) { s.clearPage(s.currentPage) }
            }
        } message: {
            Text(L("تُحذف الكتابة والتمييز والنصوص والصور من هذه الصفحة. يمكنك التراجع.",
                   "Handwriting, highlights, text and images on this page are removed. You can undo."))
        }
    }

    // ---------- الهايلايتر

    @ViewBuilder private var highlighterSection: some View {
        TextField(L("تصنيف هذا اللون", "Label for this color"), text: $label)
            .font(Fonts.sans(13))
            .padding(.horizontal, 10).padding(.vertical, 8)
            .background(RoundedRectangle(cornerRadius: 8).stroke(Palette.divider, lineWidth: 1))
            .onChange(of: label) { _, v in app.setLabel(app.colorFor(.highlighter), v) }
        PanelSwitch(label: L("التقاط النص", "Text snap"), isOn: Binding(get: { app.highlightSnap && pm.active }, set: { v in
            if !v || pm.require(.snap) { app.highlightSnap = v }
        }))
        if app.summariesOn {
            PanelSwitch(label: L("إضافة إلى الملخص", "Add to summary"), isOn: $app.highlightToSummary)
        }
        PanelDivider()
        PercentSlider(label: L("تثبيت الخط", "Stroke stabilization"), value: $app.hlStabilize, steps: 0)
        PanelSwitch(label: L("الرسم بخط مستقيم", "Draw in a straight line"), isOn: $app.hlStraight)
    }

    // ---------- الأقلام

    @ViewBuilder private func penSection(_ t: Tool) -> some View {
        PanelSwitch(label: L("ارسم وأبقِ لتحويل الرسم إلى شكل", "Draw and hold to make a shape"), isOn: $app.drawAndHold)
        PanelDivider()
        HStack {
            PanelLabel(L("نمط الخط", "Line style"))
            Text(mm(Sizes.width(t, app.sizeFor(t)))).font(Fonts.sans(12)).foregroundColor(Palette.textSecondary).fixedSize()
            Button { app.resetPen() } label: {
                PhIcon("ph_undo", Palette.textSecondary, 18, L("استعادة", "Reset")).frame(width: 34, height: 34)
            }
        }
        HStack(spacing: 6) {
            ForEach([(LineStyle.solid, L("متصل", "Solid")), (.dashed, L("متقطع", "Dashed")), (.dotted, L("منقّط", "Dotted"))], id: \.0) { st, name in
                OptionCard(label: name, selected: app.penStyle == st, action: { app.penStyle = st }) {
                    LineStyleSample(style: st, color: app.penStyle == st ? .white : Palette.textPrimary)
                }
            }
        }
        PanelLabel(L("السماكة", "Thickness"))
        SizeBar(value: app.sizeFor(t), onChange: { app.setSize($0) })
        PanelDivider()
        if t == .fountain || t == .brush {
            PercentSlider(label: L("حدة الطرف", "Tip sharpness"), value: $app.penSharpness)
        }
        PercentSlider(label: L("حساسية الضغط", "Pressure sensitivity"), value: $app.penPressure)
        PercentSlider(label: L("تثبيت الخط", "Stroke stabilization"), value: $app.penStabilize, steps: 0)
    }

    // ---------- الليزر

    @ViewBuilder private var laserSection: some View {
        HStack(spacing: 6) {
            ForEach([(LaserMode.dot, L("نقطة", "Dot")), (.line, L("خط مستمر", "Trail"))], id: \.0) { m, name in
                let sel = app.laserMode == m
                OptionCard(label: name, selected: sel, action: { app.laserMode = m }) {
                    LaserModeSample(mode: m, color: sel ? .white : Color(hex: app.colorFor(.laser)))
                }
            }
        }
    }

    // ---------- الممحاة

    @ViewBuilder private var eraserSection: some View {
        HStack(spacing: 6) {
            ForEach([(EraserMode.stroke, L("الخط كاملًا", "Whole stroke")), (.area, L("الجزء الملموس", "Touched part"))], id: \.0) { m, name in
                let sel = app.eraserMode == m
                OptionCard(label: name, selected: sel, action: { app.eraserMode = m }) {
                    EraserModeSample(mode: m, color: sel ? .white : Palette.navy)
                }
            }
        }
        PanelDivider()
        PanelLabel(L("فلتر الممحاة", "Eraser filter"))
        PanelSwitch(label: L("القلم", "Pen"), icon: "ph_pen", isOn: $app.erasePen)
        PanelSwitch(label: L("قلم الرصاص", "Pencil"), icon: "ph_pencil", isOn: $app.erasePencil)
        PanelSwitch(label: L("الهايلايتر", "Highlighter"), icon: "ph_highlighter", isOn: $app.eraseHighlighter)
        PanelSwitch(label: L("الأشكال", "Shapes"), icon: "ph_shapes", isOn: $app.eraseShapes)
        Text(L("أطفئ أداة هنا لتحمي ما رسمته بها من المسح.", "Turn a tool off here to protect what you drew with it from erasing."))
            .font(Fonts.sans(11)).foregroundColor(Palette.textSecondary).fixedSize(horizontal: false, vertical: true)
        PanelDivider()
        PanelSwitch(label: L("الرجوع إلى القلم تلقائيًا بعد المسح", "Auto-deselect (back to pen after erasing)"), isOn: $app.autoDeselect)
        Button { confirmClear = true } label: {
            Text(L("مسح الصفحة", "Clear page")).font(Fonts.sans(14, .bold)).foregroundColor(Palette.danger).padding(.vertical, 6)
        }
    }
}

/// رسم صغير يوضح نمط الممحاة: خط ممسوح كله، أو مقطوع في الوسط.
struct EraserModeSample: View {
    let mode: EraserMode
    let color: Color
    var body: some View {
        Canvas { ctx, size in
            let y = size.height / 2
            var p = Path()
            p.move(to: CGPoint(x: 4, y: y + 5))
            p.addCurve(to: CGPoint(x: size.width - 4, y: y - 4), control1: CGPoint(x: size.width * 0.3, y: y - 10), control2: CGPoint(x: size.width * 0.6, y: y + 12))
            let cx = size.width / 2
            if mode == .stroke {
                ctx.stroke(p, with: .color(color.opacity(0.25)), style: StrokeStyle(lineWidth: 2, lineCap: .round, dash: [4, 3]))
            } else {
                var a = ctx; a.clip(to: Path(CGRect(x: 0, y: 0, width: cx - 7, height: size.height)))
                a.stroke(p, with: .color(color), style: StrokeStyle(lineWidth: 2, lineCap: .round))
                var b = ctx; b.clip(to: Path(CGRect(x: cx + 7, y: 0, width: size.width, height: size.height)))
                b.stroke(p, with: .color(color), style: StrokeStyle(lineWidth: 2, lineCap: .round))
            }
            ctx.stroke(Path(ellipseIn: CGRect(x: cx - 6, y: y - 6, width: 12, height: 12)), with: .color(color), lineWidth: 1.2)
        }
        .frame(width: 64, height: 22)
    }
}

/// رسم صغير لنمط الليزر: نقطة، أو خط يتلاشى.
struct LaserModeSample: View {
    let mode: LaserMode
    let color: Color
    var body: some View {
        Canvas { ctx, size in
            let y = size.height / 2
            if mode == .dot {
                ctx.fill(Path(ellipseIn: CGRect(x: size.width / 2 - 6, y: y - 6, width: 12, height: 12)), with: .color(color.opacity(0.3)))
                ctx.fill(Path(ellipseIn: CGRect(x: size.width / 2 - 3.5, y: y - 3.5, width: 7, height: 7)), with: .color(color))
            } else {
                var p = Path()
                p.move(to: CGPoint(x: 4, y: y + 4))
                p.addCurve(to: CGPoint(x: size.width - 6, y: y - 3), control1: CGPoint(x: size.width * 0.35, y: y - 8), control2: CGPoint(x: size.width * 0.6, y: y + 8))
                ctx.stroke(p, with: .linearGradient(Gradient(colors: [color.opacity(0.1), color]), startPoint: .zero, endPoint: CGPoint(x: size.width, y: 0)),
                           style: StrokeStyle(lineWidth: 3, lineCap: .round))
            }
        }
        .frame(width: 64, height: 22)
    }
}
