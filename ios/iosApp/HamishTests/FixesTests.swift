import XCTest
import PencilKit
@testable import Hamish

/// اختبارات للأعطال التي أصلحناها بعد تجربة نسخة الآيباد.
final class FixesTests: XCTestCase {
    /// مسح كل الكتابة كان يُغلق التطبيق (حدود لا نهائية تتحول إلى عدد صحيح).
    func testHandwritingKeyOfEmptyDrawingDoesNotCrash() {
        XCTAssertEqual(HandwritingWords.key(PKDrawing(), lang: 0), "0|0")
    }

    func testEraserLeftoversAreNotRead() {
        let p = PKStrokePoint(location: CGPoint(x: 10, y: 10), timeOffset: 0, size: CGSize(width: 2, height: 2), opacity: 1, force: 1, azimuth: 0, altitude: .pi / 2)
        let one = PKStroke(ink: PKInk(.pen, color: .black), path: PKStrokePath(controlPoints: [p], creationDate: Date()))
        XCTAssertFalse(HandwritingWords.eligible(one))
    }

    private func shapeView(_ kind: ShapeKind, end: ArrowHead = .none, start: ArrowHead = .none) -> ShapeCaptureView {
        let v = ShapeCaptureView(frame: CGRect(x: 0, y: 0, width: 600, height: 800))
        v.kind = kind
        v.arrowEnd = end
        v.arrowStart = start
        v.ink = (PKInk(.monoline, color: .black), 3)
        return v
    }

    /// رؤوس الأسهم المختارة تظهر في الشكل المرسوم.
    func testChosenArrowheadsAreDrawn() {
        let plain = shapeView(.line).styledPaths(CGPoint(x: 50, y: 50), CGPoint(x: 400, y: 50))
        let both = shapeView(.line, end: .open, start: .closed).styledPaths(CGPoint(x: 50, y: 50), CGPoint(x: 400, y: 50))
        XCTAssertEqual(plain.count, 1)
        XCTAssertGreaterThan(both.count, 2)
    }

    /// الرأس المغلق: الخط يتوقف عند قاعدته فيبقى الطرف حادًا.
    func testClosedHeadShaftStopsAtBase() {
        let paths = shapeView(.line, end: .closed).styledPaths(CGPoint(x: 50, y: 50), CGPoint(x: 400, y: 50))
        XCTAssertLessThan(paths[0].last!.x, 399)
    }

    /// السهم المنحني يُرسم كأيقونته: برأس سهم حتى دون اختيار رأس.
    func testCurveArrowHasHeadLikeItsIcon() {
        let paths = shapeView(.curve).styledPaths(CGPoint(x: 50, y: 300), CGPoint(x: 400, y: 100))
        XCTAssertGreaterThan(paths.count, 1)
        XCTAssertEqual(paths[0].last!.y, 100, accuracy: 1)
    }

    /// تجميل الكتابة إلى حبر: حروف الخط تتحول إلى خطوط PencilKit ملوّنة بعلامة الحبر المجمَّل.
    func testBeautifyIntoInkMakesGlyphStrokes() {
        let lay = GlyphInk.layout("Cell", font: .systemFont(ofSize: 24))
        let strokes = GlyphInk.strokes(lay, origin: CGPoint(x: 20, y: 60), size: 24, color: .blue, bold: false, date: Date())
        XCTAssertGreaterThan(strokes.count, 20)
        XCTAssertTrue(strokes.allSatisfy { $0.randomSeed == glyphSeed })
        XCTAssertFalse(strokes.contains(where: HandwritingWords.eligible))
        let words = GlyphInk.words("Cell", lay, origin: CGPoint(x: 20, y: 60))
        XCTAssertEqual(words.first?.0, "Cell")
    }

    /// الكتابة العربية المتصلة تُملأ أيضًا.
    func testArabicGlyphInk() {
        let lay = GlyphInk.layout("الخلية", font: .systemFont(ofSize: 24))
        XCTAssertGreaterThan(GlyphInk.strokes(lay, origin: CGPoint(x: 20, y: 60), size: 24, color: .black, bold: true, date: Date()).count, 20)
    }

    /// نسبة الرسم = نسبة ظهور الصفحة على الشاشة (فلا يُكبَّر المرسوم)، بحد أعلى للذاكرة.
    func testRenderScaleFollowsScreenWithMemoryCap() {
        let a4 = CGSize(width: 595, height: 842)
        // ملء العرض على آيباد: النسبة كما هي (حادة دون تكبير)
        XCTAssertEqual(PageOverlayView.clampScale(2.2, size: a4, screen: 2), 2.2, accuracy: 0.001)
        // تكبير شديد: لا تتجاوز مساحة الرسم نحو 20 مليون بكسل
        let s = PageOverlayView.clampScale(12, size: a4, screen: 2)
        XCTAssertLessThanOrEqual(a4.width * s * 2 * a4.height * s * 2, 20_000_001)
        XCTAssertGreaterThan(s, 2.2)
        XCTAssertEqual(PageOverlayView.clampScale(.nan, size: a4, screen: 2), 1)
    }
}
