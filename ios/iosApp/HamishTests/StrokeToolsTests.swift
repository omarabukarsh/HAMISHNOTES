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

    /// شكل مرسوم باليد بزوايا مستديرة (تثبيت الخط)، يبدأ من منتصف ضلع.
    private func handDrawn(_ corners: [CGPoint], smooth: CGFloat) -> [CGPoint] {
        var raw: [CGPoint] = []
        let n = corners.count
        for k in 0...n {
            let a = corners[k % n], b = corners[(k + 1) % n]
            let from: CGFloat = k == 0 ? 0.5 : 0, to: CGFloat = k == n ? 0.55 : 1
            var t = from
            while t < to { raw.append(CGPoint(x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t)); t += 0.02 }
        }
        var s = raw[0]
        return raw.map { p in s.x += (p.x - s.x) * (1 - smooth); s.y += (p.y - s.y) * (1 - smooth); return s }
    }

    private func corners(_ path: [CGPoint]) -> Int {
        var n = 0
        for i in 1..<(path.count - 1) {
            let a = path[i - 1], b = path[i], c = path[i + 1]
            var d = abs(atan2(c.y - b.y, c.x - b.x) - atan2(b.y - a.y, b.x - a.x))
            if d > .pi { d = 2 * .pi - d }
            if d > 0.3 { n += 1 }
        }
        return n + 1
    }

    /// صورة المستخدم: كل شكل كان يصير دائرة. المثلث والمعيّن يبقيان مثلثًا ومعيّنًا ولو رُسما بزوايا مستديرة.
    func testTriangleAndDiamondAreNotCircles() throws {
        let tri = handDrawn([CGPoint(x: 300, y: 100), CGPoint(x: 450, y: 350), CGPoint(x: 150, y: 350)], smooth: 0.8)
        let t = try XCTUnwrap(ShapeRecognizer.recognize(tri, pageW: 600))
        XCTAssertEqual(t.count, 1)
        XCTAssertNotEqual(t[0].count, 97, "triangle became an ellipse")
        XCTAssertEqual(corners(t[0]), 3)
        let dia = handDrawn([CGPoint(x: 300, y: 100), CGPoint(x: 420, y: 250), CGPoint(x: 300, y: 400), CGPoint(x: 180, y: 250)], smooth: 0.7)
        let d = try XCTUnwrap(ShapeRecognizer.recognize(dia, pageW: 600))
        XCTAssertNotEqual(d[0].count, 97, "diamond became an ellipse")
        XCTAssertEqual(corners(d[0]), 4)
    }
}
