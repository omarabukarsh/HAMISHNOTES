import XCTest
@testable import Hamish

// نفس اختبارات MindMapTest في أندرويد.
final class MindMapTests: XCTestCase {
    private func mark(_ id: String, _ text: String, _ top: CGFloat) -> TextMark {
        TextMark(id: id, page: "p", rects: [MarkRect(l: 0.1, t: top, r: 0.9, b: top + 0.02)], color: 0xFFFFEB3B, text: text, createdAt: 1)
    }

    private lazy var marks: [TextMark] = [
        mark("a", "المناعة الفطرية: خط الدفاع الأول، وغير متخصصة", 0.1),
        mark("b", "تشمل الجلد والأغشية المخاطية", 0.2),
        mark("c", "المناعة المكتسبة: متخصصة ولها ذاكرة", 0.3),
        mark("d", "الخلايا B تنتج الأجسام المضادة", 0.4),
        mark("e", "الخلايا T المساعدة", 0.5)
    ]
    private var items: [MindMap.Item] { MindMap.items(marks) }
    private func build(_ e: MapEdits) -> MindMapModel { MindMap.build("T", marks, e) }
    private let root = MapTarget(parent: nil, before: nil)

    func testNothingIsAutomatic() {
        XCTAssertTrue(build(MapEdits()).branches.isEmpty)
    }

    func testPlaceBranchesPointsAndKeepFullText() {
        var e = MindMap.place(MapEdits(), items, ["a#0"], root)
        e = MindMap.place(e, items, ["c#0"], root)
        e = MindMap.place(e, items, ["b#0"], MapTarget(parent: "a#0", before: nil))
        let m = build(e)
        XCTAssertEqual(m.branches.map { $0.key }, ["a#0", "c#0"])
        XCTAssertEqual(m.branches[0].text, "المناعة الفطرية: خط الدفاع الأول، وغير متخصصة")
        XCTAssertEqual(m.branches[0].children.map { $0.key }, ["b#0"])
    }

    func testSeveralTextsKeepTheirOrderAndInsertBefore() {
        var e = MindMap.place(MapEdits(), items, ["c#0"], root)
        e = MindMap.place(e, items, ["d#0", "e#0"], MapTarget(parent: "c#0", before: nil))
        e = MindMap.place(e, items, ["b#0"], MapTarget(parent: "c#0", before: "e#0"))
        XCTAssertEqual(build(e).branches[0].children.map { $0.key }, ["d#0", "b#0", "e#0"])
    }

    func testMoveUpDownAndSubPoint() {
        var e = MindMap.place(MapEdits(), items, ["c#0"], root)
        e = MindMap.place(e, items, ["d#0", "e#0"], MapTarget(parent: "c#0", before: nil))
        e = MindMap.move(e, items, "e#0", -1)
        XCTAssertEqual(build(e).branches[0].children.map { $0.key }, ["e#0", "d#0"])
        e = MindMap.place(e, items, ["b#0"], MapTarget(parent: "d#0", before: nil))
        XCTAssertEqual(MindMap.find(build(e), "d#0")?.children.map { $0.key }, ["b#0"])
    }

    func testMovingABranchTakesItsPointsAndRemoveTakesSubtree() {
        var e = MindMap.place(MapEdits(), items, ["a#0", "c#0"], root)
        e = MindMap.place(e, items, ["d#0"], MapTarget(parent: "c#0", before: nil))
        e = MindMap.place(e, items, ["c#0"], MapTarget(parent: "a#0", before: nil))
        let m = build(e)
        XCTAssertEqual(m.branches.map { $0.key }, ["a#0"])
        XCTAssertEqual(MindMap.find(m, "c#0")?.children.map { $0.key }, ["d#0"])
        let (after, n) = MindMap.remove(e, items, "c#0")
        XCTAssertEqual(n, 2)
        XCTAssertNil(MindMap.find(build(after), "d#0"))
    }

    func testOrphanPointBecomesBranchWhenParentHighlightIsDeleted() {
        var e = MindMap.place(MapEdits(), items, ["a#0"], root)
        e = MindMap.place(e, items, ["b#0"], MapTarget(parent: "a#0", before: nil))
        let without = MindMap.build("T", marks.filter { $0.id != "a" }, e)
        XCTAssertEqual(without.branches.map { $0.key }, ["b#0"])
    }

    func testBranchesGoDeeperThanThreeLevels() {
        var e = MindMap.place(MapEdits(), items, ["a#0"], root)
        e = MindMap.place(e, items, ["b#0"], MapTarget(parent: "a#0", before: nil))
        e = MindMap.place(e, items, ["c#0"], MapTarget(parent: "b#0", before: nil))
        e = MindMap.place(e, items, ["d#0"], MapTarget(parent: "c#0", before: nil))
        e = MindMap.place(e, items, ["e#0"], MapTarget(parent: "d#0", before: nil))
        let m = build(e)
        XCTAssertEqual(MindMap.find(m, "d#0")?.children.map { $0.key }, ["e#0"])
        XCTAssertEqual(m.branches.count, 1)
        XCTAssertEqual(m.branches[0].count(), 4)
    }

    func testEditedTextIsForTheMapOnlyAndCanBeRestored() {
        var e = MindMap.place(MapEdits(), items, ["a#0"], root)
        e = MindMap.setText(e, items, "a#0", "  المناعة الفطرية  ")
        let n = build(e).branches[0]
        XCTAssertEqual(n.text, "المناعة الفطرية")
        XCTAssertEqual(n.original, "المناعة الفطرية: خط الدفاع الأول، وغير متخصصة")
        XCTAssertEqual(MindMap.items(marks).first { $0.key == "a#0" }?.text, "المناعة الفطرية: خط الدفاع الأول، وغير متخصصة")
        e = MindMap.place(e, items, ["c#0"], root)
        e = MindMap.move(e, items, "c#0", -1)
        XCTAssertEqual(MindMap.find(build(e), "a#0")?.text, "المناعة الفطرية")
        XCTAssertTrue(MindMap.setText(e, items, "a#0", n.original).texts.isEmpty)
        XCTAssertTrue(MindMap.setText(e, items, "a#0", " ").texts.isEmpty)
    }

    func testFreeTextsLiveOnlyInTheMap() {
        let e1 = MindMap.addFree(MapEdits(), marks, "  جهاز المناعة  ", root)
        guard let k = e1.free.keys.first else { return XCTFail("لم يُضف النص الحر") }
        XCTAssertTrue(MindMap.isFree(k))
        let e = MindMap.place(e1, MindMap.allItems(marks, e1), ["a#0"], MapTarget(parent: k, before: nil))
        let b = MindMap.build("T", marks, e).branches
        XCTAssertEqual(b.count, 1)
        XCTAssertEqual(b[0].text, "جهاز المناعة")
        XCTAssertTrue(b[0].isFree)
        XCTAssertEqual(b[0].children.map { $0.key }, ["a#0"])
        XCTAssertTrue(MindMap.items(marks).allSatisfy { $0.key != k })
        let (after, n) = MindMap.remove(e, MindMap.allItems(marks, e), k)
        XCTAssertEqual(n, 2)
        XCTAssertTrue(after.free.isEmpty)
    }

    func testEmptyFreeTextIsIgnored() {
        XCTAssertTrue(MindMap.addFree(MapEdits(), marks, "   ", root).free.isEmpty)
    }
}
