import XCTest
@testable import Hamish

/// ملفات تالفة لا تُضيع بيانات المستخدم: النسخة الاحتياطية تُقرأ، والحبر لا يُمسح، والمستندات لا تختفي من المكتبة.
final class DataSafetyTests: XCTestCase {
    private let repo = Repository.shared
    private var ids: [String] = []

    private func newId() -> String {
        let id = "test-" + UUID().uuidString
        ids.append(id)
        return id
    }

    override func tearDown() {
        ids.forEach { repo.delete($0) }
        ids = []
        super.tearDown()
    }

    private func pages(_ names: [String]) -> DocData {
        var d = DocData()
        d.pages = names.map { PageInfo(id: $0) }
        return d
    }

    /// data.json تالف: تُقرأ النسخة السابقة المحفوظة بجانبه.
    func testDamagedDataFallsBackToBackup() throws {
        let id = newId()
        repo.saveData(id, pages(["a"]))
        repo.saveData(id, pages(["a", "b"]))
        try Data("{not json".utf8).write(to: repo.dir(id).appendingPathComponent("data.json"))
        let (data, state) = repo.loadDataChecked(id)
        XCTAssertEqual(state, .fromBackup)
        XCTAssertEqual(data.pages.map { $0.id }, ["a"])
    }

    /// data.json تالف بلا نسخة احتياطية: يُبلَّغ عنه، ولا يُعدّ مستندًا جديدًا.
    func testUnreadableDataWithoutBackupIsReported() throws {
        let id = newId()
        try FileManager.default.createDirectory(at: repo.dir(id), withIntermediateDirectories: true)
        try Data("{not json".utf8).write(to: repo.dir(id).appendingPathComponent("data.json"))
        XCTAssertEqual(repo.loadDataChecked(id).1, .unreadable)
        XCTAssertFalse(repo.updateData(id) { $0.bookmarks = ["x"] })
    }

    /// حبر لا يطابق أي صفحة حالية لا يُمسح (معرّفات الصفحات هي التي ضاعت)، وحبر الصفحات المحذوفة يُمسح.
    func testOrphanInkIsKeptWhenNoPageMatches() throws {
        let id = newId()
        let ink = repo.dir(id).appendingPathComponent("ink", isDirectory: true)
        try FileManager.default.createDirectory(at: ink, withIntermediateDirectories: true)
        for f in ["x", "y"] { try Data([1]).write(to: ink.appendingPathComponent(f)) }

        repo.removeOrphanInk(id, keep: ["z"])
        XCTAssertEqual(Set(try FileManager.default.contentsOfDirectory(atPath: ink.path)), ["x", "y"])

        repo.removeOrphanInk(id, keep: ["x"])
        XCTAssertEqual(try FileManager.default.contentsOfDirectory(atPath: ink.path), ["x"])
    }

    /// بيانات لا تُقرأ: الحبر يُنقل جانبًا ولا يُحذف.
    func testUnreadableDataSetsInkAside() throws {
        let id = newId()
        let ink = repo.dir(id).appendingPathComponent("ink", isDirectory: true)
        try FileManager.default.createDirectory(at: ink, withIntermediateDirectories: true)
        try Data([1]).write(to: ink.appendingPathComponent("p1"))
        repo.setAsideInk(id)
        let names = try FileManager.default.contentsOfDirectory(atPath: repo.dir(id).path)
        XCTAssertFalse(names.contains("ink"))
        let aside = try XCTUnwrap(names.first { $0.hasPrefix("ink-unreadable-") })
        XCTAssertTrue(FileManager.default.fileExists(atPath: repo.dir(id).appendingPathComponent(aside).appendingPathComponent("p1").path))
    }

    /// مستند على الجهاز لا يذكره الفهرس يعود إلى المكتبة.
    func testDocumentMissingFromIndexReturnsToLibrary() throws {
        let note = try XCTUnwrap(repo.createNote(name: "x", choice: PaperChoice(kind: "plain", color: 0xFFFFFFFF, size: "a4", landscape: false), folder: ""))
        ids.append(note.id)
        let list = repo.loadLibrary()
        let found = try XCTUnwrap(list.first { $0.id == note.id })
        XCTAssertEqual(found.pageCount, 1)
        XCTAssertTrue(found.isNote)
        XCTAssertTrue(repo.libraryRepaired)
    }
}
