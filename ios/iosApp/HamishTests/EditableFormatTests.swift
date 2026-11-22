import XCTest
import PDFKit
@testable import Hamish

/// الصيغة الموحدة للنسخة القابلة للتعديل (v2) بين أندرويد والآيباد: مرفقات PDF قياسية،
/// وتحويل الحبر إلى نقاط وبالعكس، وتحويل البيانات من وإلى مخطط أندرويد.
final class EditableFormatTests: XCTestCase {
    private let W: CGFloat = 600

    private func makePDF(pages: Int) -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".pdf")
        let r = UIGraphicsPDFRenderer(bounds: CGRect(x: 0, y: 0, width: 600, height: 800))
        try? r.writePDF(to: url) { ctx in
            for i in 0..<pages {
                ctx.beginPage()
                ("Page \(i + 1)" as NSString).draw(at: CGPoint(x: 60, y: 60), withAttributes: [.font: UIFont.systemFont(ofSize: 24)])
            }
        }
        return url
    }

    private func tempDir() -> URL {
        let u = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try? FileManager.default.createDirectory(at: u, withIntermediateDirectories: true)
        return u
    }

    private func stroke(_ k: String, _ pts: [(Double, Double, Double)], w: Double = 0.005, color: UInt32 = 0xFF1565C0) -> AStroke {
        var a = AStroke()
        a.k = k
        a.w = w
        a.color = color
        a.t = 1_700_000_000_000
        a.pts = pts.flatMap { [$0.0, $0.1, $0.2] }
        return a
    }

    private let wave: [(Double, Double, Double)] = {
        var out: [(Double, Double, Double)] = []
        for i in 0...20 {
            let f = Double(i)
            let x: Double = 0.1 + f * 0.02
            let y: Double = 0.2 + sin(f / 3) * 0.03
            let p: Double = 0.2 + f * 0.035
            out.append((x, y, p))
        }
        return out
    }()

    // ---------------------------------------------------------------- المرفقات

    func testAttachmentsAreStandardAndReadBack() throws {
        let url = makePDF(pages: 3)
        let files: [(name: String, data: Data)] = [
            ("hamish-orig.pdf", Data((0..<5000).map { UInt8($0 % 251) })),
            ("hamish-data.json", Data("{\"version\":3}".utf8)),
            ("hamish-img-a.png", Data([1, 2, 3]))
        ]
        XCTAssertTrue(PdfAttachments.append(to: url, files: files))
        XCTAssertEqual(PDFDocument(url: url)?.pageCount, 3)
        let back = try XCTUnwrap(PdfAttachments.read(url))
        XCTAssertEqual(back.count, 3)
        for f in files { XCTAssertEqual(back[f.name], f.data) }
    }

    func testPlainPdfHasNoAttachments_andUnsafeNamesAreRefused() {
        let url = makePDF(pages: 1)
        XCTAssertNil(PdfAttachments.read(url))
        XCTAssertFalse(PdfAttachments.append(to: url, files: [("a(b).txt", Data([1]))]))
        XCTAssertNil(PdfAttachments.read(url))
    }

    // ---------------------------------------------------------------- الحبر

    func testEachPenKeepsItsKindColorWidthAndPlace() {
        for k in ["BALLPOINT", "FOUNTAIN", "PENCIL", "HIGHLIGHTER", "BRUSH"] {
            let a = stroke(k, wave)
            let d = InkCodec.decode([a], width: W).drawing
            XCTAssertEqual(d.strokes.count, 1, k)
            let back = InkCodec.encode(d, width: W)
            XCTAssertEqual(back.count, 1, k)
            guard let b = back.first, let bb = b.bounds, let ab = a.bounds else { return XCTFail(k) }
            XCTAssertEqual(b.k, k)
            XCTAssertEqual(b.color, a.color, k)
            XCTAssertEqual(b.t, a.t, k)
            XCTAssertEqual(bb.l, ab.l, accuracy: 0.01, k)
            XCTAssertEqual(bb.r, ab.r, accuracy: 0.01, k)
            XCTAssertEqual(bb.t, ab.t, accuracy: 0.01, k)
            XCTAssertEqual(bb.b, ab.b, accuracy: 0.01, k)
            // المحرك بنموذج أندرويد نفسه: السماكة والضغط كما هما
            XCTAssertEqual(b.w, a.w, accuracy: 1e-6, k)
            XCTAssertEqual(b.count, a.count, k)
            for i in 0..<a.count { XCTAssertEqual(b.p(i), a.p(i), accuracy: 1e-6, k) }
        }
    }

    func testFilledShapeKeepsItsFillBothWays() {
        var box = stroke("BALLPOINT", [(0.2, 0.2, 1), (0.4, 0.2, 1), (0.4, 0.3, 1), (0.2, 0.3, 1), (0.2, 0.2, 1)])
        box.sh = true
        box.sf = true
        box.sfa = 0.3
        let dec = InkCodec.decode([box], width: W, page: "p")
        // الشكل وملؤه خط واحد (يُمسح ويُحدَّد ويتحرك معًا)
        XCTAssertEqual(dec.drawing.strokes.count, 1)
        let s = dec.drawing.strokes[0]
        XCTAssertTrue(s.shape && s.shapeFill && s.isClosed)
        XCTAssertEqual(s.fillAlpha, 0.3, accuracy: 0.001)
        let back = InkCodec.encode(dec.drawing, width: W)
        XCTAssertEqual(back.count, 1)
        XCTAssertTrue(back[0].sf && back[0].sh)
        XCTAssertEqual(back[0].sfa, 0.3, accuracy: 0.001)
    }

    func testDashedLineKeepsItsStyle() {
        var line = stroke("BALLPOINT", [(0.1, 0.5, 1), (0.9, 0.5, 1)])
        line.ls = 1
        let d = InkCodec.decode([line], width: W).drawing
        XCTAssertEqual(d.strokes.count, 1)
        XCTAssertEqual(d.strokes[0].style, .dashed)
        XCTAssertEqual(InkCodec.encode(d, width: W).first?.ls, 1)
    }

    /// حرف مملوء بحدّه (مستطيل) يحمل كلمته.
    private func letter(_ l: Double, _ t: Double, _ r: Double, _ b: Double, _ word: String, _ id: Int64) -> AStroke {
        var a = AStroke()
        a.fl = true
        a.w = 0
        a.wd = word
        a.wi = id
        a.t = 1_700_000_000_000
        a.pts = [l, t, -1, r, t, 1, r, b, 1, l, b, 1]
        a.lf = 0.8
        a.ef = 1.2
        return a
    }

    func testConvertedWordsStayReadableBothWays() {
        let list = [
            letter(0.30, 0.10, 0.36, 0.13, "Omar", 2),
            letter(0.10, 0.10, 0.14, 0.13, "Ahmed", 1),
            letter(0.15, 0.10, 0.20, 0.13, "Ahmed", 1),
            letter(0.10, 0.30, 0.20, 0.33, "Next", 3)
        ]
        let dec = InkCodec.decode(list, width: W, page: "p")
        // سطران، وكلمات كل سطر بترتيب القراءة
        XCTAssertEqual(dec.converted.count, 2)
        XCTAssertEqual(dec.converted[0].words.map { $0.text }, ["Ahmed", "Omar"])
        XCTAssertEqual(dec.converted[1].words.map { $0.text }, ["Next"])
        XCTAssertEqual(dec.converted[0].words[0].l, 0.10, accuracy: 0.001)
        XCTAssertEqual(dec.converted[0].words[0].r, 0.20, accuracy: 0.001)
        XCTAssertNotEqual(dec.converted[0].key, dec.converted[1].key)
        // حبر كل سطر بمفتاحه، وكله حروف مملوءة
        XCTAssertTrue(dec.drawing.strokes.allSatisfy { $0.fill })
        let keys = Set(dec.drawing.strokes.map { $0.t })
        XCTAssertEqual(keys, Set(dec.converted.map { InkKey.ms($0.key) }))
        // والعودة إلى أندرويد: حروف مملوءة تحمل كلماتها
        let back = InkCodec.encode(dec.drawing, width: W, converted: dec.converted)
        XCTAssertTrue(back.allSatisfy { $0.fl })
        XCTAssertEqual(Set(back.map { $0.wd }), ["Ahmed", "Omar", "Next"])
        XCTAssertEqual(InkCodec.lines(back).map { $0.words.map { $0.text } }, [["Ahmed", "Omar"], ["Next"]])
        let omar = back.first { $0.wd == "Omar" }
        XCTAssertEqual(omar?.bounds?.l ?? 0, 0.30, accuracy: 0.002)
        XCTAssertGreaterThanOrEqual(omar?.lf ?? -1, 0)
    }

    // ---------------------------------------------------------------- المخطط

    private let androidJSON = """
    {"version":3,"pages":{"1":[{"c":-16777216,"w":0.004,"k":"BALLPOINT","t":1700000000000,"p":[0.1,0.1,1,0.2,0.2,1,0.3,0.1,1]}]},
     "images":[{"id":"im1","file":"pic.png","page":0,"x":0.5,"y":0.25,"w":0.2,"a":2}],
     "marks":[{"id":"m1","page":1,"c":-7350,"t":"hello","at":5,"r":[0.1,0.2,0.3,0.25],"lv":2,"sum":true,"em":["he"],"sr":[]}],
     "texts":[{"id":"t1","page":0,"x":0.1,"y":0.2,"w":0.5,"t":"نص","c":-16777216,"s":0.03,"st":false,"ld":"","lp":0,"b":true}],
     "summary":{"order":["m1#0"],"kinds":{"m1#0":"h"},"tables":[]},
     "cards":[{"id":"c1","f":"q","b":"a"}],"starred":["c1"],"bookmarks":[1],
     "notes":[{"id":"n1","page":0,"x":0.1,"y":0.1,"w":0.3,"h":0.2,"c":-200,"st":1,"cl":false,"a":"me","tx":"note","ink":[]}]}
    """

    func testAndroidDataOpensOnTheIpad() throws {
        let pages = [PageInfo(id: "A"), PageInfo(id: "B")]
        let imp = try XCTUnwrap(AndroidSchema.importJSON(Data(androidJSON.utf8), pages: pages, widths: [600, 500]))
        XCTAssertEqual(imp.ink["B"]?.strokes.count, 1)
        XCTAssertNil(imp.ink["A"])
        let im = try XCTUnwrap(imp.data.images.first)
        XCTAssertEqual(im.page, "A")
        XCTAssertEqual(im.x, 300, accuracy: 0.01)
        XCTAssertEqual(im.w, 120, accuracy: 0.01)
        let m = try XCTUnwrap(imp.data.marks.first)
        XCTAssertEqual(m.page, "B")
        XCTAssertEqual(m.level, 2)
        XCTAssertEqual(m.text, "hello")
        XCTAssertEqual(m.color, UInt32(bitPattern: -7350))
        XCTAssertEqual(m.rects.count, 1)
        let t = try XCTUnwrap(imp.data.boxes.first)
        XCTAssertEqual(t.text, "نص")
        XCTAssertEqual(t.size, 18, accuracy: 0.01)
        XCTAssertTrue(t.bold)
        XCTAssertEqual(imp.data.bookmarks, ["B"])
        XCTAssertEqual(imp.data.notes.first?.w ?? 0, 180, accuracy: 0.01)
        XCTAssertEqual(imp.data.notes.first?.author, "me")
        XCTAssertEqual(imp.data.summaryEdits?.kinds["m1#0"], "h")
    }

    func testIpadDataIsWrittenInAndroidSchema_andUnknownAndroidPartsSurvive() throws {
        let pages = [PageInfo(id: "A"), PageInfo(id: "B")]
        let imp = try XCTUnwrap(AndroidSchema.importJSON(Data(androidJSON.utf8), pages: pages, widths: [600, 500]))
        let raw = AJSON.parse(Data(androidJSON.utf8))
        let ex = AndroidSchema.export(imp.data, widths: [600, 500], ink: { imp.ink[$0] ?? InkDrawing() }, raw: raw) { _ in Data([7]) }
        let root = try XCTUnwrap(AJSON.parse(ex.json))
        XCTAssertEqual(AJSON.int(root["version"]), 3)
        let inkPages = try XCTUnwrap(root["pages"] as? [String: Any])
        XCTAssertEqual(Array(inkPages.keys), ["1"])
        let first = try XCTUnwrap((inkPages["1"] as? [[String: Any]])?.first)
        XCTAssertEqual(first["k"] as? String, "BALLPOINT")
        XCTAssertEqual(AJSON.int(first["c"]), -16777216)
        let mark = try XCTUnwrap((root["marks"] as? [[String: Any]])?.first)
        XCTAssertEqual(AJSON.int(mark["page"]), 1)
        XCTAssertEqual(AJSON.int(mark["c"]), -7350)
        let text = try XCTUnwrap((root["texts"] as? [[String: Any]])?.first)
        XCTAssertEqual(AJSON.double(text["s"] ?? 0), 0.03, accuracy: 0.0001)
        XCTAssertEqual(AJSON.double(text["x"] ?? 0), 0.1, accuracy: 0.0001)
        XCTAssertEqual((root["bookmarks"] as? [Any])?.count, 1)
        // البطاقات والمميَّز بنجمة لا يعرفها الآيباد: تعود كما جاءت
        XCTAssertEqual((root["cards"] as? [[String: Any]])?.first?["id"] as? String, "c1")
        XCTAssertEqual(root["starred"] as? [String], ["c1"])
        XCTAssertEqual(ex.images.map { $0.name }, ["pic.png"])
        // ويقرؤه الآيباد ثانية بنفس النتيجة
        let again = try XCTUnwrap(AndroidSchema.importJSON(ex.json, pages: pages, widths: [600, 500]))
        XCTAssertEqual(again.data.marks.first?.id, "m1")
        XCTAssertEqual(again.data.boxes.first?.x ?? 0, 60, accuracy: 0.01)
        XCTAssertEqual(again.ink["B"]?.strokes.count, 1)
    }

    // ---------------------------------------------------------------- الفتح

    private func unified(_ extra: [(name: String, data: Data)] = [], data: String? = nil) throws -> (file: URL, orig: Data) {
        let file = makePDF(pages: 2)
        let orig = try Data(contentsOf: makePDF(pages: 2))
        var files: [(name: String, data: Data)] = [
            (EditablePDF.origName, orig),
            (EditablePDF.dataName, Data((data ?? androidJSON).utf8)),
            (EditablePDF.metaName, Data("{\"v\":2,\"counts\":[1,1]}".utf8)),
            (EditablePDF.imgPrefix + "pic.png", Data([9, 9]))
        ]
        files += extra
        XCTAssertTrue(PdfAttachments.append(to: file, files: files))
        return (file, orig)
    }

    func testAndroidFileOpensFromItsEmbeddedOriginal() throws {
        let (file, orig) = try unified()
        let dir = tempDir()
        XCTAssertTrue(EditablePDF.restore(pdf: file, into: dir))
        XCTAssertEqual(try Data(contentsOf: file), orig)
        let data = try JSONDecoder().decode(DocData.self, from: Data(contentsOf: dir.appendingPathComponent("data.json")))
        XCTAssertEqual(data.pages.count, 2)
        XCTAssertEqual(data.marks.first?.page, data.pages[1].id)
        let ink = try XCTUnwrap(InkDrawing.load(Data(contentsOf: dir.appendingPathComponent("ink/" + data.pages[1].id))))
        XCTAssertEqual(ink.strokes.count, 1)
        XCTAssertEqual(try Data(contentsOf: dir.appendingPathComponent("media/pic.png")), Data([9, 9]))
        XCTAssertNotNil(try? Data(contentsOf: dir.appendingPathComponent(EditablePDF.androidRaw)))
    }

    private func iosExtras(stamp: String) throws -> (extra: [(name: String, data: Data)], data: DocData) {
        var d = DocData()
        d.pages = [PageInfo(id: "P1", src: "blank:lined:0"), PageInfo(id: "P2")]
        d.outline = [OutlineEntry(title: "Intro", level: 0, page: 1)]
        let dj = try JSONEncoder().encode(d)
        return ([(EditablePDF.iosData, dj), (EditablePDF.iosInk + "P2", Data([4, 5, 6])),
                 (EditablePDF.iosMeta, Data("{\"v\":1,\"sha256\":\"\(stamp)\"}".utf8))], d)
    }

    func testUntouchedIpadFileComesBackWithItsOwnInk() throws {
        let (extra, _) = try iosExtras(stamp: EditablePDF.sha256(Data(androidJSON.utf8)))
        let (file, orig) = try unified(extra)
        let dir = tempDir()
        XCTAssertTrue(EditablePDF.restore(pdf: file, into: dir))
        XCTAssertEqual(try Data(contentsOf: file), orig)
        XCTAssertEqual(try Data(contentsOf: dir.appendingPathComponent("data.json")), extra[0].data)
        XCTAssertEqual(try Data(contentsOf: dir.appendingPathComponent("ink/P2")), Data([4, 5, 6]))
    }

    func testIpadFileEditedOnAndroidIsRebuiltFromPoints_keepingIpadOnlyParts() throws {
        let (extra, _) = try iosExtras(stamp: "not-the-same")
        let (file, _) = try unified(extra)
        let dir = tempDir()
        XCTAssertTrue(EditablePDF.restore(pdf: file, into: dir))
        let data = try JSONDecoder().decode(DocData.self, from: Data(contentsOf: dir.appendingPathComponent("data.json")))
        // معرّفات الصفحات وقوالبها والفهرس من بيانات الآيباد، والكتابة من نقاط أندرويد
        XCTAssertEqual(data.pages.map { $0.id }, ["P1", "P2"])
        XCTAssertEqual(data.pages[0].src, "blank:lined:0")
        XCTAssertEqual(data.outline.first?.title, "Intro")
        XCTAssertEqual(data.marks.first?.page, "P2")
        let ink = try XCTUnwrap(InkDrawing.load(Data(contentsOf: dir.appendingPathComponent("ink/P2"))))
        XCTAssertEqual(ink.strokes.count, 1)
    }

    func testOldIpadFormatStillOpens() throws {
        let file = makePDF(pages: 2)
        let orig = try Data(contentsOf: makePDF(pages: 1))
        var d = DocData()
        d.pages = [PageInfo(id: "X")]
        let dj = try JSONEncoder().encode(d)
        XCTAssertTrue(EditablePDF.append(to: file, payload: ["pdf": orig, "data": dj, "ink/X": Data([1, 2])]))
        let dir = tempDir()
        XCTAssertTrue(EditablePDF.restore(pdf: file, into: dir))
        XCTAssertEqual(try Data(contentsOf: file), orig)
        XCTAssertEqual(try Data(contentsOf: dir.appendingPathComponent("ink/X")), Data([1, 2]))
    }

    func testBrokenDataLeavesTheFileAsAFinalCopy() throws {
        let (file, _) = try unified(data: "not json")
        let before = try Data(contentsOf: file)
        XCTAssertFalse(EditablePDF.restore(pdf: file, into: tempDir()))
        XCTAssertEqual(try Data(contentsOf: file), before)
    }
}
