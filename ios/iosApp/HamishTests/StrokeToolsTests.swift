import XCTest
@testable import Hamish

// التعرف على الأشكال («ارسم وأبقِ») وأنماط الخط والتحديد الحر وتقسيم خط اليد إلى أسطر.
final class StrokeToolsTests: XCTestCase {
    private let pageW: CGFloat = 600

    func testStraightLineIsRecognized() {
        let pts = (0...40).map { CGPoint(x: 50 + CGFloat($0) * 8, y: 200 + sin(CGFloat($0)) * 1.5) }
        let r = ShapeRecognizer.recognize(pts, pageW: pageW)
        XCTAssertEqual(r?.count, 1)
        XCTAssertEqual(r?.first?.first?.x ?? 0, 50, accuracy: 1)
    }

    func testRoughCircleBecomesEllipse() {
        let pts = (0...80).map { i -> CGPoint in
            let a = CGFloat(i) / 80 * 2 * .pi
            return CGPoint(x: 300 + cos(a) * (100 + sin(a * 5) * 4), y: 300 + sin(a) * (80 + cos(a * 3) * 4))
        }
        let r = ShapeRecognizer.recognize(pts, pageW: pageW)
        XCTAssertNotNil(r)
        XCTAssertGreaterThan(r?.first?.count ?? 0, 90)
    }

    func testSmallLettersAreNotTurnedIntoShapes() {
        let pts = (0...30).map { i -> CGPoint in
            let a = CGFloat(i) / 30 * 2 * .pi
            return CGPoint(x: 100 + cos(a) * 6, y: 100 + sin(a) * 6)
        }
        XCTAssertNil(ShapeRecognizer.recognize(pts, pageW: pageW))
    }

    func testRoughRectangleSnapsToAxisAlignedRectangle() {
        var pts: [CGPoint] = []
        let c = [CGPoint(x: 100, y: 100), CGPoint(x: 400, y: 103), CGPoint(x: 398, y: 300), CGPoint(x: 102, y: 298), CGPoint(x: 100, y: 100)]
        for i in 0..<4 { for k in 0..<20 { let t = CGFloat(k) / 20; pts.append(CGPoint(x: c[i].x + (c[i + 1].x - c[i].x) * t, y: c[i].y + (c[i + 1].y - c[i].y) * t)) } }
        let r = ShapeRecognizer.recognize(pts, pageW: pageW)
        XCTAssertNotNil(r)
        let xs = r?.first?.map { $0.x } ?? []
        XCTAssertEqual(xs.min() ?? 0, 100, accuracy: 3)
        XCTAssertEqual(xs.max() ?? 0, 400, accuracy: 3)
    }

    func testDashedAndDottedSplitIntoPieces() {
        let line = (0...100).map { CGPoint(x: CGFloat($0) * 3, y: 0) }
        XCTAssertEqual(StrokeKit.split(line, style: .solid, width: 2).count, 1)
        XCTAssertGreaterThan(StrokeKit.split(line, style: .dashed, width: 2).count, 10)
        XCTAssertGreaterThan(StrokeKit.split(line, style: .dotted, width: 2).count, 20)
    }

    func testArrowHeads() {
        XCTAssertNil(StrokeKit.arrow(.none, tip: .zero, from: CGPoint(x: -10, y: 0), width: 2))
        XCTAssertEqual(StrokeKit.arrow(.open, tip: .zero, from: CGPoint(x: -10, y: 0), width: 2)?.count, 3)
        XCTAssertEqual(StrokeKit.arrow(.closed, tip: .zero, from: CGPoint(x: -10, y: 0), width: 2)?.count, 4)
    }

    private func stroke(_ pts: [CGPoint], at date: Date) -> InkStroke {
        InkStroke(pts: pts.map { InkPt($0) }, color: 0xFF000000, width: 2, tool: .ballpoint, t: Int64(date.timeIntervalSince1970 * 1000))
    }

    func testHandwritingIsSplitIntoLinesAndWords() {
        let d = Date()
        // سطران، في الأول كلمتان (فجوة كبيرة)، والكتابة من اليمين لليسار
        let s1 = stroke([CGPoint(x: 300, y: 100), CGPoint(x: 340, y: 120)], at: d)
        let s2 = stroke([CGPoint(x: 200, y: 102), CGPoint(x: 240, y: 118)], at: d.addingTimeInterval(1))
        let s3 = stroke([CGPoint(x: 210, y: 200), CGPoint(x: 330, y: 220)], at: d.addingTimeInterval(2))
        let lines = InkReader.lines([s3, s1, s2])
        XCTAssertEqual(lines.count, 2)
        XCTAssertEqual(lines[0].strokes.count, 2)
        XCTAssertTrue(InkReader.isRTL(lines[0]))
        XCTAssertEqual(InkReader.wordBoxes(lines[0]).count, 2)
    }
}
