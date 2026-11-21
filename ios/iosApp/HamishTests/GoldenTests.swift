import XCTest
@testable import Hamish

// نفس الاختبارات المرجعية في أندرويد (AntibioticsGoldenTest): خطوط هايلايتر بمواضع ثابتة على كلمات
// antibiotics.pdf (antibiotics_words.tsv)، والنص الصحيح المتوقع كما في الملف. يمر المسار نفسه في التطبيق:
// TextLayout.wordsUnder ← MarkMerge.add ← ترتيب القراءة ← Summary.view.

struct GoldenCase {
    let id: String
    let about: String
    let strokes: [(page: Int, pts: [RelPt])]
    let markTexts: [String]
    let summary: [String]
    var critical: [String] = []
}

/// خط هايلايتر أفقي (نفس stroke() في أندرويد).
private func line(_ page: Int, _ y: CGFloat, _ x0: CGFloat, _ x1: CGFloat) -> (page: Int, pts: [RelPt]) {
    let a = x0 - 0.004, b = x1 + 0.004
    return (page, (0...24).map { k in RelPt(x: a + (b - a) * CGFloat(k) / 24, y: y) })
}

enum AntibioticsCases {
    static let headingAndBullets = GoldenCase(
        id: "p5-heading-bullets",
        about: "عنوان 1.4 لا يندمج مع الفقرات، وكل نقطة فقرة مستقلة، والأرقام كما هي",
        strokes: [line(4, 0.4946, 0.058, 0.471), line(4, 0.5302, 0.079, 0.941), line(4, 0.5528, 0.079, 0.912), line(4, 0.5755, 0.079, 0.250),
                  line(4, 0.5994, 0.079, 0.848), line(4, 0.6246, 0.079, 0.932), line(4, 0.6460, 0.079, 0.476), line(4, 0.6711, 0.079, 0.756)],
        markTexts: [
            "# 1.4 Penicillins — Pharmacokinetics\n\n" +
            "Absorption (oral): varies with acid stability and food; some poorly absorbed because of acid lability — Pen\n" +
            "G. Food delays/decreases absorption of some (e.g. ampicillin) → give ≥1 h before or 2 h after meals.\n" +
            "Parenteral: IM, IV, SC.\n\n" +
            "Intrathecal injection is NOT recommended, especially Pen G — may cause convulsions.\n\n" +
            "Distribution: widely distributed but do not enter mammalian cells; do not cross BBB unless meninges\n" +
            "are inflamed (then therapeutic levels reach CSF).\n\n" +
            "Excretion: mostly rapid renal excretion, mainly by tubular secretion; partly in bile."
        ],
        summary: [
            "H: 1.4 Penicillins — Pharmacokinetics",
            "P: Absorption (oral): varies with acid stability and food; some poorly absorbed because of acid lability — Pen G. " +
            "Food delays/decreases absorption of some (e.g. ampicillin) → give ≥1 h before or 2 h after meals. Parenteral: IM, IV, SC.",
            "P: Intrathecal injection is NOT recommended, especially Pen G — may cause convulsions.",
            "P: Distribution: widely distributed but do not enter mammalian cells; do not cross BBB unless meninges are inflamed " +
            "(then therapeutic levels reach CSF).",
            "P: Excretion: mostly rapid renal excretion, mainly by tubular secretion; partly in bile."
        ],
        critical: ["≥1 h before or 2 h after meals", "IM, IV, SC"]
    )

    static let twoTables = GoldenCase(
        id: "p5-two-tables",
        about: "جدولان متتاليان لا يندمجان، وخلية من سطرين تبقى في صفها وعمودها",
        strokes: [line(4, 0.1253, 0.067, 0.927), line(4, 0.1568, 0.067, 0.927), line(4, 0.1769, 0.332, 0.569), line(4, 0.2084, 0.067, 0.840),
                  line(4, 0.2285, 0.332, 0.423), line(4, 0.2600, 0.067, 0.898), line(4, 0.2801, 0.332, 0.557), line(4, 0.3116, 0.067, 0.919),
                  line(4, 0.3556, 0.067, 0.927), line(4, 0.3871, 0.067, 0.886), line(4, 0.4173, 0.067, 0.861), line(4, 0.4475, 0.067, 0.851)],
        markTexts: [
            "Mechanism | Detail\n" +
            "1. Enzymatic cleavage | β-lactamases (penicillinases, cephalosporinases, carbapenemases) hydrolyze the amide bond of the β-lactam ring\n" +
            "2. Target (PBP) alteration | Low-affinity PBPs: mecA gene → PBP2a in MRSA; mutated PBP in S. pneumoniae\n" +
            "3. Porin-channel loss | Down-regulation/mutation of outer-membrane porins in Gram-negatives (e.g. OprD loss in Pseudomonas)\n" +
            "4. Active efflux pumps | Over-expressed multidrug efflux pumps pump drug out of the periplasmic space\n\n" +
            "β-lactamase inhibitor | Combined with | Brand\n" +
            "Clavulanic acid | Amoxicillin (co-amoxiclav) or Ticarcillin | Augmentin®\n" +
            "Sulbactam | Ampicillin | Unasyn®\n" +
            "Tazobactam | Piperacillin | Zosyn®"
        ],
        summary: [
            "T: Mechanism | Detail || " +
            "1. Enzymatic cleavage | β-lactamases (penicillinases, cephalosporinases, carbapenemases) hydrolyze the amide bond of the β-lactam ring || " +
            "2. Target (PBP) alteration | Low-affinity PBPs: mecA gene → PBP2a in MRSA; mutated PBP in S. pneumoniae || " +
            "3. Porin-channel loss | Down-regulation/mutation of outer-membrane porins in Gram-negatives (e.g. OprD loss in Pseudomonas) || " +
            "4. Active efflux pumps | Over-expressed multidrug efflux pumps pump drug out of the periplasmic space",
            "T: β-lactamase inhibitor | Combined with | Brand || " +
            "Clavulanic acid | Amoxicillin (co-amoxiclav) or Ticarcillin | Augmentin® || " +
            "Sulbactam | Ampicillin | Unasyn® || " +
            "Tazobactam | Piperacillin | Zosyn®"
        ],
        critical: ["PBP2a in MRSA", "Augmentin®", "Zosyn®"]
    )

    static let doseTable = GoldenCase(
        id: "p6-dose-table",
        about: "الجرعات والوحدات في خلايا الجدول تبقى صحيحة، وتكملة الخلية لا تصير صفًا جديدًا",
        strokes: [line(5, 0.1165, 0.067, 0.883), line(5, 0.1467, 0.067, 0.919), line(5, 0.1782, 0.067, 0.380),
                  line(5, 0.2084, 0.067, 0.923), line(5, 0.2298, 0.306, 0.585), line(5, 0.2600, 0.067, 0.482)],
        markTexts: [
            "Streptococcal | Pharyngitis, tonsillitis, otitis media, scarlet fever, rheumatic fever, endocarditis\n" +
            "Pneumococcal | Pneumonia, meningitis, arthritis, osteomyelitis, endocarditis, pericarditis, peritonitis\n" +
            "Meningococcal | Meningitis\n" +
            "Gonococcal | Resistance ↑ → Ceftriaxone 250 mg IM single dose (DOC), or Procaine Pen G 4.8 million units + Probenecid 1 g orally\n" +
            "Syphilis | Pen G or Procaine Pen G"
        ],
        summary: [
            "T: Streptococcal | Pharyngitis, tonsillitis, otitis media, scarlet fever, rheumatic fever, endocarditis || " +
            "Pneumococcal | Pneumonia, meningitis, arthritis, osteomyelitis, endocarditis, pericarditis, peritonitis || " +
            "Meningococcal | Meningitis || " +
            "Gonococcal | Resistance ↑ → Ceftriaxone 250 mg IM single dose (DOC), or Procaine Pen G 4.8 million units + Probenecid 1 g orally || " +
            "Syphilis | Pen G or Procaine Pen G"
        ],
        critical: ["Ceftriaxone 250 mg IM single dose", "4.8 million units", "Probenecid 1 g orally"]
    )

    static let reHighlight = GoldenCase(
        id: "p5-re-highlight",
        about: "تظليل نفس النص مرتين لا يكرره، بل يجعله مهمًا",
        strokes: [line(4, 0.8033, 0.079, 0.520), line(4, 0.8033, 0.079, 0.520)],
        markTexts: ["60–70 % destroyed by gastric acid → given IV or IM."],
        summary: ["P: 60–70 % destroyed by gastric acid → given IV or IM."]
    )

    static let all = [headingAndBullets, twoTables, doseTable, reHighlight]
}

/// المسار نفسه في التطبيق دون واجهة (نفس Pipeline في أندرويد).
enum Pipeline {
    static let yellow: UInt32 = 0xFFFFEB3B
    /// عرض الهايلايتر الافتراضي (المقاس 4) نسبةً لعرض الصفحة.
    static let band = Sizes.width(.highlighter, 4) / 600

    static func run(_ words: [Int: [OcrWord]], _ c: GoldenCase) -> (marks: [TextMark], tidy: [SBlock]) {
        var marks: [Int: [TextMark]] = [:]
        var clock: Int64 = 1000
        for s in c.strokes {
            guard let ws = words[s.page] else { XCTFail("الصفحة \(s.page) غير موجودة"); continue }
            let idx = TextLayout.wordsUnder(ws, s.pts, band: band)
            if idx.isEmpty { continue }
            clock += 1
            guard let res = MarkMerge.add(existing: marks[s.page] ?? [], page: String(s.page), words: ws, idx: idx,
                                          color: yellow, now: clock, inSummary: true) else { continue }
            marks[s.page] = res.marks
        }
        let ordered = marks.keys.sorted().flatMap { p in
            (marks[p] ?? []).filter { $0.inSummary && !$0.free }.sorted { a, b in
                let ta = a.rects.map { $0.t }.min() ?? 0, tb = b.rects.map { $0.t }.min() ?? 0
                if abs(ta - tb) > 0.0001 { return ta < tb }
                return (a.rects.map { $0.l }.min() ?? 0) < (b.rects.map { $0.l }.min() ?? 0)
            }
        }
        return (ordered, Summary.view(ordered, tidy: true, edits: SummaryEdits()))
    }

    static func render(_ b: SBlock) -> String {
        switch b {
        case let .heading(t, _, _): return "H: " + t
        case let .para(t, _, _, _): return "P: " + t
        case let .table(rows, _, _, _): return "T: " + rows.map { $0.joined(separator: " | ") }.joined(separator: " || ")
        }
    }

    /// كلمات ملف antibiotics_words.tsv (page \t line \t l \t t \t r \t b \t text).
    static func loadWords() throws -> [Int: [OcrWord]] {
        let bundle = Bundle(for: GoldenTests.self)
        guard let url = bundle.url(forResource: "antibiotics_words", withExtension: "tsv") else {
            throw NSError(domain: "golden", code: 1, userInfo: [NSLocalizedDescriptionKey: "antibiotics_words.tsv غير موجود"])
        }
        var out: [Int: [OcrWord]] = [:]
        for ln in try String(contentsOf: url, encoding: .utf8).components(separatedBy: "\n") {
            if ln.isEmpty || ln.hasPrefix("#") { continue }
            let f = ln.components(separatedBy: "\t")
            guard f.count == 7, let p = Int(f[0]), let li = Int(f[1]),
                  let l = Double(f[2]), let t = Double(f[3]), let r = Double(f[4]), let b = Double(f[5]) else { continue }
            out[p, default: []].append(OcrWord(text: f[6], l: l, t: t, r: r, b: b, line: li))
        }
        return out
    }
}

final class GoldenTests: XCTestCase {
    private static var words: [Int: [OcrWord]] = [:]

    override class func setUp() {
        super.setUp()
        words = (try? Pipeline.loadWords()) ?? [:]
    }

    private func check(_ c: GoldenCase) {
        XCTAssertFalse(Self.words.isEmpty, "لم تُحمَّل كلمات الملف")
        let r = Pipeline.run(Self.words, c)
        let summary = r.tidy.map(Pipeline.render)
        XCTAssertEqual(r.marks.map { $0.text }, c.markTexts, "نص التمييزات لا يطابق المرجع — \(c.id): \(c.about)")
        XCTAssertEqual(summary, c.summary, "الملخص لا يطابق المرجع — \(c.id): \(c.about)")
        let text = summary.joined(separator: "\n")
        for k in c.critical { XCTAssertTrue(text.contains(k), "مقطع حرج غير موجود حرفيًا: «\(k)» — \(c.id)") }
    }

    func testHeadingAndBullets() { check(AntibioticsCases.headingAndBullets) }
    func testTwoTables() { check(AntibioticsCases.twoTables) }
    func testDoseTable() { check(AntibioticsCases.doseTable) }
    func testReHighlight() { check(AntibioticsCases.reHighlight) }
}
