import XCTest
import UIKit
@testable import Hamish

/// لمسة القلم أو الإصبع على الصفحة يجب أن تصل إلى طبقة الكتابة، لا إلى الطبقة الشفافة فوقها.
final class OverlayTouchTests: XCTestCase {
    func testEmptyAreaPassesThroughToCanvas() {
        let root = UIView(frame: CGRect(x: 0, y: 0, width: 400, height: 600))
        let canvas = InkCanvas(frame: root.bounds)
        let top = PassThroughView(frame: root.bounds)
        root.addSubview(canvas)
        root.addSubview(top)
        let hit = root.hitTest(CGPoint(x: 200, y: 300), with: nil)
        XCTAssertTrue(hit === canvas || hit?.isDescendant(of: canvas) == true, "اللمسة لم تصل إلى طبقة الكتابة: \(String(describing: hit))")
    }

    func testItemOnTopStillReceivesTouch() {
        let root = UIView(frame: CGRect(x: 0, y: 0, width: 400, height: 600))
        let canvas = InkCanvas(frame: root.bounds)
        let top = PassThroughView(frame: root.bounds)
        let note = UIView(frame: CGRect(x: 50, y: 50, width: 100, height: 100))
        top.addSubview(note)
        root.addSubview(canvas)
        root.addSubview(top)
        XCTAssertTrue(root.hitTest(CGPoint(x: 80, y: 80), with: nil) === note)
    }
}
