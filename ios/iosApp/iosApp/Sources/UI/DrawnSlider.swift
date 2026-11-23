import SwiftUI

/// شريط منزلق مرسوم بـ SwiftUI بدل Slider النظام (UISlider).
/// Slider النظام مبني على UIKit فلا يُرسم داخل حركة نوافذ الشريط (EdgeFold): يظهر مكانه مربع أصفر عليه 🚫.
/// هذا الشريط يُرسم داخل الحركة كما يُرسم DrawnSwitch، وبنفس شكل وسلوك الشريط الأصلي (سحب ونقر وخطوة).
struct DrawnSlider: View {
    @Binding var value: Double
    var range: ClosedRange<Double> = 0...1
    var step: Double? = nil
    var tint: Color = Palette.navy

    private let thumb: CGFloat = 28
    private let trackH: CGFloat = 4

    private func snapped(_ v: Double) -> Double {
        var x = min(max(v, range.lowerBound), range.upperBound)
        if let s = step, s > 0 {
            x = range.lowerBound + ((x - range.lowerBound) / s).rounded() * s
            x = min(max(x, range.lowerBound), range.upperBound)
        }
        return x
    }

    var body: some View {
        GeometryReader { g in
            let span = max(1, g.size.width - thumb)
            let total = range.upperBound - range.lowerBound
            let frac = total > 0 ? CGFloat((min(max(value, range.lowerBound), range.upperBound) - range.lowerBound) / total) : 0
            let x = frac * span
            ZStack(alignment: .leading) {
                Capsule().fill(Palette.divider).frame(height: trackH)
                    .padding(.horizontal, thumb / 2)
                Capsule().fill(tint).frame(width: x + thumb / 2, height: trackH)
                Circle().fill(Color.white)
                    .frame(width: thumb, height: thumb)
                    .shadow(color: .black.opacity(0.22), radius: 2, y: 1)
                    .overlay(Circle().stroke(Color.black.opacity(0.06), lineWidth: 0.5))
                    .offset(x: x)
            }
            .frame(width: g.size.width, height: g.size.height)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { d in
                        let f = min(max(Double((d.location.x - thumb / 2) / span), 0), 1)
                        let v = snapped(range.lowerBound + f * total)
                        if v != value { value = v }
                    }
            )
        }
        .frame(height: 32)
        .environment(\.layoutDirection, .leftToRight)
    }
}
