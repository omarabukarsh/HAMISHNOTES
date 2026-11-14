import XCTest
import PDFKit
@testable import Hamish

/// اختبارات مطابقة أندرويد 1.0.271: النسخة القابلة للتعديل، بصمة الحبر، تحديد الكلمات، والقراءات الأخرى.
final class ParityTests: XCTestCase {
    private func makePDF(pages: Int) -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".pdf")
        let r = UIGraphicsPDFRenderer(bounds: CGRect(x: 0, y: 0, width: 595, height: 842))
        try? r.writePDF(to: url) { ctx in
            for i in 0..<pages {
                ctx.beginPage()
                ("Page \(i + 1)" as NSString).draw(at: CGPoint(x: 60, y: 60), withAttributes: [.font: UIFont.systemFont(ofSize: 24)])
            }
        }
        return url
    }

    /// النسخة القابلة للتعديل تبقى ملف PDF سليمًا يفتحه أي تطبيق، وما أُرفق فيها يعود كما هو.
    func testEditableCopyStaysValidAndRestores() throws {
        let url = makePDF(pages: 3)
        let original = makePDF(pages: 2)
        let origData = try Data(contentsOf: original)
        let payload: [String: Data] = ["pdf": origData, "data": Data("{\"pages\":[]}".utf8), "ink/p1": Data([1, 2, 3, 4]), "media/img-a.jpg": Data([9, 8, 7])]
        XCTAssertTrue(EditablePDF.append(to: url, payload: payload))
        let doc = PDFDocument(url: url)
        XCTAssertNotNil(doc)
        XCTAssertEqual(doc?.pageCount, 3)
        let back = EditablePDF.extract(url)
        XCTAssertEqual(back?["pdf"], origData)
        XCTAssertEqual(back?["ink/p1"], Data([1, 2, 3, 4]))
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let dest = dir.appendingPathComponent("doc.pdf")
        try FileManager.default.copyItem(at: url, to: dest)
        XCTAssertTrue(EditablePDF.restore(pdf: dest, into: dir))
        XCTAssertEqual(PDFDocument(url: dest)?.pageCount, 2)
        XCTAssertEqual(try Data(contentsOf: dir.appendingPathComponent("ink/p1")), Data([1, 2, 3, 4]))
        XCTAssertTrue(FileManager.default.fileExists(atPath: dir.appendingPathComponent("media/img-a.jpg").path))
    }

    /// ملف PDF عادي ليس نسخة قابلة للتعديل.
    func testPlainPDFIsNotEditable() {
        let url = makePDF(pages: 1)
        XCTAssertNil(EditablePDF.extract(url))
    }

    private func stroke(_ pts: [CGPoint], color: UInt32 = 0xFF000000) -> InkStroke {
        InkStroke(pts: pts.map { InkPt($0) }, color: color, width: 2, tool: .fountain, t: 1000)
    }

    /// بصمة الحبر تتغير بالإضافة والنقل والتلوين، وتثبت إن لم يتغير شيء.
    func testInkSignature() {
        let a = stroke([CGPoint(x: 10, y: 10), CGPoint(x: 60, y: 40)])
        let d = InkDrawing(strokes: [a])
        XCTAssertEqual(inkSig(d), inkSig(InkDrawing(strokes: [a])))
        XCTAssertNotEqual(inkSig(d), inkSig(InkDrawing()))
        XCTAssertNotEqual(inkSig(d), inkSig(InkDrawing(strokes: [a, a])))
        let moved = a.transformed(CGAffineTransform(translationX: 5, y: 0))
        XCTAssertNotEqual(inkSig(d), inkSig(InkDrawing(strokes: [moved])))
        XCTAssertNotEqual(inkSig(d), inkSig(InkDrawing(strokes: [stroke([CGPoint(x: 10, y: 10), CGPoint(x: 60, y: 40)], color: 0xFFC62828)])))
    }

    /// الكلمات المحددة: نصها بأسطره، ومستطيل لكل سطر.
    func testWordSelectionTextAndRects() {
        let ws = [OcrWord(text: "one", l: 0.1, t: 0.1, r: 0.2, b: 0.12, line: 0),
                  OcrWord(text: "two", l: 0.22, t: 0.1, r: 0.3, b: 0.125, line: 0),
                  OcrWord(text: "three", l: 0.1, t: 0.15, r: 0.25, b: 0.17, line: 1)]
        let sel = WordSel(page: 0, words: ws)
        XCTAssertEqual(sel.text, "one two\nthree")
        XCTAssertEqual(sel.rects.count, 2)
        XCTAssertEqual(sel.rects[0].l, 0.1, accuracy: 0.0001)
        XCTAssertEqual(sel.rects[0].r, 0.3, accuracy: 0.0001)
        XCTAssertEqual(PageOverlayView.wordAt(ws, RelPt(x: 0.25, y: 0.11)), 1)
        XCTAssertEqual(PageOverlayView.nearestWord(ws, RelPt(x: 0.9, y: 0.16)), 2)
    }

    /// القراءات الأخرى تُوزَّع على الكلمات التي اختلفت فقط.
    func testWordAlternatives() {
        ReadAlts.note("the cell wall", ["the call wall", "the cell well", "thecell wall"])
        let alts = ReadAlts.wordAlts("the cell wall")
        XCTAssertEqual(alts.count, 3)
        XCTAssertEqual(alts[0], [])
        XCTAssertEqual(alts[1], ["call"])
        XCTAssertEqual(alts[2], ["well"])
    }

    /// الشكل المغلق (للملء مع «ارسم وأبقِ») يُعرف من التقاء طرفيه.
    func testClosedPolygonDetection() {
        let square = [CGPoint(x: 0, y: 0), CGPoint(x: 100, y: 0), CGPoint(x: 100, y: 100), CGPoint(x: 0, y: 100), CGPoint(x: 0, y: 0)]
        XCTAssertNotNil(PageOverlayView.closedPolygon([square]))
        let line = [CGPoint(x: 0, y: 0), CGPoint(x: 50, y: 0), CGPoint(x: 100, y: 0), CGPoint(x: 150, y: 0)]
        XCTAssertNil(PageOverlayView.closedPolygon([line]))
    }
}
