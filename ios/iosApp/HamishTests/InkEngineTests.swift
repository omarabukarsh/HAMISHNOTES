import XCTest
import PencilKit
@testable import Hamish

/// محرك الحبر الخاص (بدل PencilKit): الحفظ والقراءة، الممحاة بنوعيها، التحديد، الرسم، وقراءة الحبر القديم.
/// (PencilKit هنا فقط لصنع ملف بالصيغة القديمة واختبار تحويله.)
final class InkEngineTests: XCTestCase {
    private func line(_ a: CGPoint, _ b: CGPoint, tool: Tool = .ballpoint, t: Int64 = 1) -> InkStroke {
        InkStroke(pts: [InkPt(a), InkPt(b)], color: 0xFF000000, width: 2, tool: tool, t: t)
    }

    private func triangle(filled: Bool) -> InkStroke {
        var s = InkStroke(points: [CGPoint(x: 0, y: 0), CGPoint(x: 100, y: 0), CGPoint(x: 50, y: 80), CGPoint(x: 0, y: 0)],
                          color: 0xFF333333, width: 3, t: 2)
        s.shapeFill = filled
        return s
    }

    func testSaveAndReadBack() throws {
        var g = InkStroke(pts: InkFill.encode([[CGPoint(x: 0, y: 0), CGPoint(x: 10, y: 0), CGPoint(x: 10, y: 10)]]), width: 0, tool: .ballpoint, t: 5)
        g.fill = true
        g.word = "خلية"
        let d = InkDrawing(strokes: [line(.zero, CGPoint(x: 30, y: 40), tool: .brush), triangle(filled: true), g])
        let back = try XCTUnwrap(InkDrawing.load(d.dataRepresentation()))
        XCTAssertEqual(back.strokes.count, 3)
        XCTAssertEqual(back.strokes[0].tool, .brush)
        XCTAssertTrue(back.strokes[1].shapeFill && back.strokes[1].isClosed)
        XCTAssertEqual(back.strokes[2].word, "خلية")
        XCTAssertNil(InkDrawing.load(Data([1, 2, 3])))
    }

    /// صورة الشاشة: «الممحاة تمسح الإطار فقط». الآن لمس داخل الشكل المملوء يمسحه.
    func testEraserRemovesFilledShapeFromInside() {
        let shape = triangle(filled: true)
        XCTAssertEqual(InkErase.whole([shape], at: CGPoint(x: 50, y: 30), radius: 5)?.count, 0)
        XCTAssertNil(InkErase.whole([triangle(filled: false)], at: CGPoint(x: 50, y: 30), radius: 5))
    }

    func testAreaEraserCutsTheTouchedPartOnly() throws {
        let l = InkStroke(pts: (0...20).map { InkPt(CGFloat($0) * 5, 0) }, width: 2, tool: .ballpoint)
        let parts = try XCTUnwrap(InkErase.area([l], at: CGPoint(x: 50, y: 0), radius: 6))
        XCTAssertEqual(parts.count, 2)
        XCTAssertFalse(parts.flatMap { $0.pts }.contains { $0.x > 45 && $0.x < 55 })
    }

    /// قص حرف مملوء بالممحاة («الجزء الملموس»): يبقى ما لم تمر عليه.
    func testGlyphCutKeepsTheRest() throws {
        InkCut.install()
        var g = InkStroke(pts: InkFill.encode([[CGPoint(x: 0, y: 0), CGPoint(x: 100, y: 0), CGPoint(x: 100, y: 20), CGPoint(x: 0, y: 20)]]), width: 0, tool: .ballpoint)
        g.fill = true
        let parts = try XCTUnwrap(InkErase.area([g], at: CGPoint(x: 50, y: 10), radius: 15))
        XCTAssertEqual(parts.count, 2)
        XCTAssertTrue(parts.allSatisfy { $0.fill })
    }

    /// صورة الشاشة: «لا يمكن تحديد الشكل». التحديد حوله، والنقرة داخله، يحددانه بملئه.
    func testLassoSelectsFilledShape() {
        let shape = triangle(filled: true)
        let around = [CGPoint(x: -10, y: -10), CGPoint(x: 110, y: -10), CGPoint(x: 110, y: 90), CGPoint(x: -10, y: 90)]
        XCTAssertTrue(InkErase.lassoed(shape, polygon: around))
        let pen = line(CGPoint(x: 200, y: 200), CGPoint(x: 260, y: 200))
        XCTAssertEqual(InkErase.topmost([shape, pen], at: CGPoint(x: 50, y: 30), tolerance: 3), 0)
        XCTAssertNil(InkErase.topmost([shape, pen], at: CGPoint(x: 400, y: 400), tolerance: 3))
    }

    /// صفحة كاملة: الحبر يُرسم فعلًا (بكسلات بلون القلم في مكانه).
    func testRenderingDrawsInk() {
        let d = InkDrawing(strokes: [InkStroke(pts: [InkPt(10, 50, 1), InkPt(90, 50, 1)], color: 0xFFC62828, width: 8, tool: .ballpoint)])
        let img = d.lightImage(from: CGRect(x: 0, y: 0, width: 100, height: 100), scale: 1)
        guard let cg = img.cgImage, let data = cg.dataProvider?.data, let p = CFDataGetBytePtr(data) else { return XCTFail("no image") }
        let bpr = cg.bytesPerRow, bpp = cg.bitsPerPixel / 8
        func alpha(_ x: Int, _ y: Int) -> UInt8 { p[y * bpr + x * bpp + 3] }
        XCTAssertGreaterThan(alpha(50, 50), 200)
        XCTAssertEqual(alpha(50, 10), 0)
    }

    /// الكتابة القديمة (PencilKit) تُقرأ وتُحوَّل إلى المحرك الجديد بمكانها ولونها.
    func testLegacyPencilKitInkIsConverted() throws {
        let cp = (0..<30).map { i in
            PKStrokePoint(location: CGPoint(x: 100 + CGFloat(i) * 5, y: 200), timeOffset: 0, size: CGSize(width: 3, height: 3),
                          opacity: 1, force: 1, azimuth: 0, altitude: .pi / 2)
        }
        let s = PKStroke(ink: PKInk(.monoline, color: .blue), path: PKStrokePath(controlPoints: cp, creationDate: Date()))
        let d = try XCTUnwrap(InkDrawing.load(PKDrawing(strokes: [s]).dataRepresentation()))
        XCTAssertEqual(d.strokes.count, 1)
        // صيغة PencilKit المحفوظة لا تحفظ نوع القلم بدقة: المهم أن السماكة الظاهرة كما كانت
        let st = d.strokes[0]
        XCTAssertTrue([.ballpoint, .fountain].contains(st.tool))
        let p = st.pts.map { $0.p }.max() ?? 1
        let shown = st.tool == .fountain ? st.width * (0.4 + p) : st.width
        XCTAssertEqual(shown, 3, accuracy: 0.2)
        XCTAssertEqual(d.strokes[0].pointBounds.minY, 200, accuracy: 1)
        XCTAssertEqual(d.strokes[0].pointBounds.minX, 100, accuracy: 1)
    }

    /// ملء الأشكال في الملفات القديمة يصير جزءًا من الحبر (يُمسح من الداخل).
    func testLegacyFillBecomesInk() throws {
        let f = ShapeFill(id: "f", page: "p", pts: [CGPoint(x: 0, y: 0), CGPoint(x: 100, y: 0), CGPoint(x: 50, y: 80)], color: 0xFF1565C0, alpha: 0.3, key: 1000)
        let s = try XCTUnwrap(DocSession.fillStroke(f))
        XCTAssertTrue(s.shapeFill && s.isClosed)
        XCTAssertEqual(s.t, InkKey.ms(1000))
        XCTAssertEqual(InkErase.whole([s], at: CGPoint(x: 50, y: 30), radius: 3)?.count, 0)
    }

    /// معرّفات من ملف مستورد لا تصير مسارات خارج مجلد المستند.
    func testUnsafeNamesAreRefused() {
        XCTAssertTrue(Repository.safeName("8F1C2D3E-1111-2222-3333-444455556666"))
        XCTAssertTrue(Repository.safeName("img-1.jpg"))
        for bad in ["", ".", "..", "../../../library.json", "a/b", ".hidden", "x\u{0}"] { XCTAssertFalse(Repository.safeName(bad), bad) }
    }

    func testPenProcessing() {
        XCTAssertEqual(InkPen.applyPressure(0.8, sensitivity: 0.5), 0.8, accuracy: 1e-9)
        XCTAssertEqual(InkPen.pencilPressure(force: 4, maxForce: 4.17), 0.9, accuracy: 1e-9)
        let tp = InkPen.taper((0...20).map { InkPt(CGFloat($0) * 5, 0, 0.8) }, tool: .fountain, sharpness: 0.5, pageW: 600)
        XCTAssertLessThan(tp[0].p, 0.5)
        XCTAssertEqual(tp[10].p, 0.8, accuracy: 1e-9)
    }
}
