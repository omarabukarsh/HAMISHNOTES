import SwiftUI
import UIKit
import Photos

// السحب من درج الملصقات والصور: السحب جانبًا يحمل العنصر إلى الصفحة، والسحب عموديًا يمرّر الدرج.
// (كان السحب يحتاج ضغطة مطوّلة، والسحب العادي يمرّر الدرج فقط.)

enum TrayPayload {
    case sticker(String)
    case photo(PHAsset)
}

/// العناصر الظاهرة في الدرج وأماكنها في النافذة، ليعرف السحب ما تحت الإصبع.
final class TrayDrag {
    static let shared = TrayDrag()
    weak var session: DocSession?
    fileprivate var items: [String: (frame: CGRect, payload: TrayPayload)] = [:]

    fileprivate func item(at p: CGPoint) -> (frame: CGRect, payload: TrayPayload)? {
        items.values.first { $0.frame.contains(p) }
    }
}

extension View {
    /// عنصر في الدرج يمكن سحبه جانبًا إلى الصفحة.
    func trayDraggable(_ id: String, _ payload: TrayPayload) -> some View {
        onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { TrayDrag.shared.items[id] = ($0, payload) }
            .onDisappear { TrayDrag.shared.items[id] = nil }
    }
}

/// يُوضع داخل محتوى ScrollView الدرج: يضيف إلى ScrollView سحبًا جانبيًا، والتمرير ينتظر حتى يتبيّن الاتجاه.
struct TrayDragInstaller: UIViewRepresentable {
    func makeUIView(context: Context) -> Probe { Probe() }
    func updateUIView(_ v: Probe, context: Context) { v.install() }

    final class Probe: UIView {
        override init(frame: CGRect) {
            super.init(frame: frame)
            isUserInteractionEnabled = false
        }
        required init?(coder: NSCoder) { fatalError() }

        override func didMoveToWindow() {
            super.didMoveToWindow()
            install()
        }

        func install() {
            var s = superview
            while let v = s, !(v is UIScrollView) { s = v.superview }
            guard let sv = s as? UIScrollView,
                  !(sv.gestureRecognizers ?? []).contains(where: { $0 is TraySidewaysPan }) else { return }
            let pan = TraySidewaysPan()
            sv.addGestureRecognizer(pan)
            sv.panGestureRecognizer.require(toFail: pan)
        }
    }
}

/// يبدأ فقط إذا كانت الحركة أفقية أكثر منها عمودية وبدأت على عنصر؛ وإلا يفشل فورًا فيمرّر الدرج.
final class TraySidewaysPan: UIPanGestureRecognizer, UIGestureRecognizerDelegate {
    private var preview: UIView?
    private var payload: TrayPayload?
    private var origin: CGRect = .zero

    init() {
        super.init(target: nil, action: nil)
        addTarget(self, action: #selector(step))
        delegate = self
        maximumNumberOfTouches = 1
    }

    private var startPoint: CGPoint {
        let p = location(in: nil), t = translation(in: nil)
        return CGPoint(x: p.x - t.x, y: p.y - t.y)
    }

    func gestureRecognizerShouldBegin(_ g: UIGestureRecognizer) -> Bool {
        guard let v = view else { return false }
        let t = translation(in: v)
        guard abs(t.x) > abs(t.y) * 1.2 else { return false }
        let start = startPoint
        guard v.convert(v.bounds, to: nil).contains(start) else { return false }
        return TrayDrag.shared.item(at: start) != nil
    }

    @objc private func step() {
        guard let w = view?.window else { return cleanup() }
        let p = location(in: nil)
        switch state {
        case .began:
            guard let it = TrayDrag.shared.item(at: startPoint) else { return }
            payload = it.payload
            origin = it.frame
            let snap = w.resizableSnapshotView(from: it.frame, afterScreenUpdates: false, withCapInsets: .zero) ?? UIView(frame: it.frame)
            snap.frame = it.frame
            snap.isUserInteractionEnabled = false
            snap.layer.shadowColor = UIColor.black.cgColor
            snap.layer.shadowOpacity = 0.25
            snap.layer.shadowRadius = 8
            snap.layer.shadowOffset = CGSize(width: 0, height: 4)
            w.addSubview(snap)
            preview = snap
            UISelectionFeedbackGenerator().selectionChanged()
            UIView.animate(withDuration: 0.15) {
                snap.center = p
                snap.transform = CGAffineTransform(scaleX: 1.15, y: 1.15)
                snap.alpha = 0.92
            }
        case .changed:
            preview?.center = p
            _ = dropTarget(at: p, done: false)
        case .ended:
            finish(at: p)
        default:
            _ = dropTarget(at: p, done: true)
            returnPreview()
        }
    }

    /// الصفحة والنقطة تحت الإصبع (بنقاط الصفحة)، أو لا شيء فوق الدرج نفسه أو خارج الصفحات.
    private func dropTarget(at p: CGPoint, done: Bool) -> (Int, CGPoint)? {
        guard let s = TrayDrag.shared.session else { return nil }
        if let v = view, v.convert(v.bounds, to: nil).contains(p) { _ = s.trayDropHook?(nil, done); return nil }
        return s.trayDropHook?(p, done)
    }

    private func finish(at p: CGPoint) {
        guard let s = TrayDrag.shared.session, let pl = payload, let hit = dropTarget(at: p, done: true) else { return returnPreview() }
        let (page, at) = hit
        switch pl {
        case .sticker(let k):
            s.insertSticker(k, page: page, at: at)
        case .photo(let a):
            PhotoGallery.shared.full(a) { img in
                guard let img else { return }
                DispatchQueue.main.async { s.insertImage(img, page: page, at: at) }
            }
        }
        let v = preview
        UIView.animate(withDuration: 0.12, animations: { v?.alpha = 0 }, completion: { _ in v?.removeFromSuperview() })
        preview = nil
        payload = nil
    }

    /// إفلات خارج الصفحة: يرجع العنصر إلى مكانه في الدرج.
    private func returnPreview() {
        let v = preview, o = origin
        UIView.animate(withDuration: 0.2, animations: {
            v?.transform = .identity
            v?.frame = o
            v?.alpha = 0
        }, completion: { _ in v?.removeFromSuperview() })
        preview = nil
        payload = nil
    }

    private func cleanup() {
        preview?.removeFromSuperview()
        preview = nil
        payload = nil
    }
}
