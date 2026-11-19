import SwiftUI
import UIKit
import UIKit.UIGestureRecognizerSubclass

// أدوات الحركة المشتركة، منقولة من Motion.kt في أندرويد كي تبدو الحركات في النسختين واحدة:
// النوافذ تخرج من الزر الذي فُتحت منه، والقوائم من أيقونتها، والأشرطة تتمدد من الأعلى، والأدراج تنزلق من حافتها.

// MARK: - مكان آخر لمسة

/// آخر لمسة على الشاشة (بإحداثيات النافذة)، كي تخرج النافذة من الزر الذي فُتحت منه (TouchOrigin في أندرويد).
enum TouchOrigin {
    private static var point: CGPoint?
    private static var time: CFTimeInterval = 0

    static func record(_ p: CGPoint) {
        point = p
        time = CACurrentMediaTime()
    }

    /// مكان اللمسة إن كانت حديثة (أقل من 1.5 ث)؛ وإلا nil فتخرج النافذة من وسطها.
    static func recent() -> CGPoint? { CACurrentMediaTime() - time < 1.5 ? point : nil }
}

/// يسجّل بداية كل لمسة في النافذة ثم يتنحى فورًا: لا يؤخر اللمسات ولا يلغيها ولا يمنع أي إيماءة أخرى.
private final class TouchOriginRecognizer: UIGestureRecognizer, UIGestureRecognizerDelegate {
    init() {
        super.init(target: nil, action: nil)
        cancelsTouchesInView = false
        delaysTouchesBegan = false
        delaysTouchesEnded = false
        delegate = self
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        if let t = touches.first, let v = view { TouchOrigin.record(t.location(in: v)) }
        state = .failed
    }

    func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }
}

/// يضيف ملتقط اللمسات إلى نافذة التطبيق مرة واحدة (يوضع خلف الواجهة الجذرية).
struct TouchOriginInstaller: UIViewRepresentable {
    func makeUIView(context: Context) -> UIView {
        let v = Probe()
        v.isUserInteractionEnabled = false
        return v
    }

    func updateUIView(_ uiView: UIView, context: Context) {}

    private final class Probe: UIView {
        override func didMoveToWindow() {
            super.didMoveToWindow()
            guard let w = window, !(w.gestureRecognizers ?? []).contains(where: { $0 is TouchOriginRecognizer }) else { return }
            w.addGestureRecognizer(TouchOriginRecognizer())
        }
    }
}

/// يحفظ نقطة خروج كل نافذة ما دامت ظاهرة، فتنكمش عند الإغلاق إلى النقطة نفسها التي خرجت منها.
final class OriginMemo {
    private var points: [String: CGPoint] = [:]
    private var open: Set<String> = []

    func point(_ key: String, shown: Bool) -> CGPoint? {
        if shown && !open.contains(key) {
            open.insert(key)
            points[key] = TouchOrigin.recent()
        } else if !shown {
            open.remove(key)
        }
        return points[key]
    }
}

// MARK: - معدّلات قابلة للتحريك

/// تكبير من نقطة على الشاشة (أو من نقطة نسبية إن لم توجد)، مع شفافية تكتمل مبكرًا (fade مرة أسرع من الحجم).
struct GrowFrom: ViewModifier, Animatable {
    var p: CGFloat
    var from: CGFloat
    var origin: CGPoint?
    var fallback: UnitPoint = .center
    /// الشفافية = min(1, p × fade). صفر = بلا تلاشٍ هنا.
    var fade: CGFloat = 0

    var animatableData: CGFloat {
        get { p }
        set { p = newValue }
    }

    func body(content: Content) -> some View {
        let s = from + (1 - from) * p
        let o = origin
        let fb = fallback
        return content
            .visualEffect { c, g in
                let f = g.frame(in: .global)
                var a = fb
                if let o, f.width > 0, f.height > 0 {
                    a = UnitPoint(x: (o.x - f.minX) / f.width, y: (o.y - f.minY) / f.height)
                }
                return c.scaleEffect(s, anchor: a)
            }
            .opacity(fade > 0 ? Double(min(1, max(0, p * fade))) : 1)
    }
}

/// نافذة من مكان اللمسة (OriginDialog في أندرويد): من 6% إن عُرف مكان اللمسة، وإلا من 86% في وسطها.
func originReveal(_ p: CGFloat, _ origin: CGPoint?) -> GrowFrom {
    GrowFrom(p: p, from: origin == nil ? 0.86 : 0.06, origin: origin, fade: 2.2)
}

/// انبثاق من الوسط (popIn): الحجم من `from`، والشفافية × fade، ثم تلاشٍ للإغلاق بقيمة k.
struct PopReveal: ViewModifier, Animatable {
    var p: CGFloat
    var k: CGFloat = 1
    var from: CGFloat = 0.86
    var fade: CGFloat = 2.5
    var scales = true

    var animatableData: AnimatablePair<CGFloat, CGFloat> {
        get { AnimatablePair(p, k) }
        set { p = newValue.first; k = newValue.second }
    }

    func body(content: Content) -> some View {
        content
            .scaleEffect(scales ? from + (1 - from) * p : 1)
            .opacity(Double(min(1, max(0, p * fade)) * max(0, k)))
    }
}

/// قائمة تخرج من مركز أيقونتها: 25% ← 100%، والشفافية × 2.2.
struct DropReveal: ViewModifier, Animatable {
    var p: CGFloat
    var anchor: UnitPoint

    var animatableData: CGFloat {
        get { p }
        set { p = newValue }
    }

    func body(content: Content) -> some View {
        content
            .scaleEffect(0.25 + 0.75 * p, anchor: anchor)
            .opacity(Double(min(1, max(0, p * 2.2))))
    }
}

/// يكشف المحتوى من الأعلى إلى الأسفل بارتفاع يكبر (expandVertically من الأعلى)، فيُدفع ما تحته بنعومة.
private struct VRevealLayout: Layout {
    var f: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        guard let v = subviews.first else { return .zero }
        let s = v.sizeThatFits(proposal)
        return CGSize(width: s.width, height: s.height * f)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard let v = subviews.first else { return }
        let s = v.sizeThatFits(ProposedViewSize(width: bounds.width, height: proposal.height))
        v.place(at: bounds.origin, anchor: .topLeading, proposal: ProposedViewSize(width: bounds.width, height: s.height))
    }
}

struct VReveal: ViewModifier, Animatable {
    var f: CGFloat

    var animatableData: CGFloat {
        get { f }
        set { f = newValue }
    }

    func body(content: Content) -> some View {
        let done = f >= 0.999
        return VRevealLayout(f: max(0, f)) { content }
            .mask(alignment: .topLeading) {
                // أثناء الحركة يُقص ما لم يُكشف بعد من الأسفل فقط؛ وعند الاكتمال تبقى الظلال حرة
                GeometryReader { g in
                    Rectangle()
                        .frame(width: g.size.width + 160, height: g.size.height + (done ? 120 : 40))
                        .offset(x: -80, y: -40)
                }
            }
    }
}

/// إزاحة عمودية بنسبة من ارتفاع العنصر نفسه (slideInVertically { -it / 4 }).
struct SlideFraction: ViewModifier, Animatable {
    var f: CGFloat

    var animatableData: CGFloat {
        get { f }
        set { f = newValue }
    }

    func body(content: Content) -> some View {
        let k = f
        return content.visualEffect { c, g in c.offset(y: g.size.height * k) }
    }
}

// MARK: - الانتقالات

extension AnyTransition {
    /// نافذة تخرج من مكان اللمسة وتكبر بنابض (0.78/320)، وتنكمش إلى النقطة نفسها في 240ms.
    static func origin(_ point: CGPoint?) -> AnyTransition {
        let m = AnyTransition.modifier(active: originReveal(0, point), identity: originReveal(1, point))
        return .asymmetric(insertion: m.animation(Motion.origin), removal: m.animation(Motion.acc(0.24)))
    }

    /// نافذة الصور والملصقات: تكبر من الزر (8% ← 100%) في 240ms مع تلاشٍ 160ms، وتنكمش إليه في 200ms.
    static func tray(_ point: CGPoint?) -> AnyTransition {
        let m = AnyTransition.modifier(active: GrowFrom(p: 0, from: 0.08, origin: point, fallback: .top),
                                       identity: GrowFrom(p: 1, from: 0.08, origin: point, fallback: .top))
        let fade = AnyTransition.opacity.animation(Motion.std(0.16))
        return .asymmetric(insertion: m.animation(Motion.std(0.24)).combined(with: fade),
                           removal: m.animation(Motion.std(0.20)).combined(with: fade))
    }

    /// شريط عائم: يتمدد من الأعلى 300ms مع تلاشٍ 220ms، وينطوي 200ms مع تلاشٍ 160ms.
    static var barExpand: AnyTransition {
        let m = AnyTransition.modifier(active: VReveal(f: 0), identity: VReveal(f: 1))
        return .asymmetric(
            insertion: m.animation(Motion.std(0.30)).combined(with: AnyTransition.opacity.animation(Motion.std(0.22))),
            removal: m.animation(Motion.std(0.20)).combined(with: AnyTransition.opacity.animation(Motion.std(0.16))))
    }

    /// صفوف تظهر مع مفتاحها (AnimatedVisibility الافتراضي): تمدد وتلاشٍ بنابض ناعم.
    static var rowExpand: AnyTransition {
        AnyTransition.modifier(active: VReveal(f: 0), identity: VReveal(f: 1)).combined(with: .opacity).animation(Motion.soft)
    }

    /// درج ينزلق من حافته: 330ms مع تلاشٍ 220ms، والخروج 220ms متسارعًا مع تلاشٍ 160ms.
    static func drawer(_ edge: Edge) -> AnyTransition {
        .asymmetric(
            insertion: AnyTransition.move(edge: edge).animation(Motion.std(0.33))
                .combined(with: AnyTransition.opacity.animation(Motion.std(0.22))),
            removal: AnyTransition.move(edge: edge).animation(Motion.acc(0.22))
                .combined(with: AnyTransition.opacity.animation(Motion.std(0.16))))
    }

    /// رأس المكتبة وشريط التحديد: يدخل بتلاشٍ 220ms مع نزول من ربع ارتفاعه (300ms)، ويخرج بتلاشٍ 140ms.
    static var headerSwap: AnyTransition {
        .asymmetric(
            insertion: AnyTransition.opacity.animation(Motion.std(0.22))
                .combined(with: AnyTransition.modifier(active: SlideFraction(f: -0.25), identity: SlideFraction(f: 0)).animation(Motion.std(0.30))),
            removal: AnyTransition.opacity.animation(Motion.std(0.14)))
    }

    /// المؤشر الذهبي تحت الأداة المختارة: ينمو من 30% بنابض مع تلاشٍ 300ms، ويختفي بتلاشٍ 200ms.
    static var toolIndicator: AnyTransition {
        .asymmetric(
            insertion: AnyTransition.scale(scale: 0.3).animation(Motion.grow).combined(with: AnyTransition.opacity.animation(Motion.enter)),
            removal: AnyTransition.opacity.animation(Motion.std(0.20)))
    }

    /// دائرة التحديد وعلامة ✓: تنمو من 20% بنابض grow، وتنكمش بنابض ناعم.
    static var selectionMark: AnyTransition {
        let m = AnyTransition.scale(scale: 0.2).combined(with: .opacity)
        return .asymmetric(insertion: m.animation(Motion.grow), removal: m.animation(Motion.soft))
    }
}
