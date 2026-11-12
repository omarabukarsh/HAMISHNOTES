import Foundation
import CoreGraphics

/// مرفقات PDF القياسية (EmbeddedFiles): الصيغة الموحدة للنسخة القابلة للتعديل بين أندرويد والآيباد.
/// الكتابة: تحديث تزايدي في آخر الملف يضيف المرفقات وشجرة أسمائها ونسخة جديدة من الـCatalog.
/// القراءة: عبر CGPDFDocument، فتعمل مع ملفات أندرويد (PDFBox) وملفاتنا.
enum PdfAttachments {
    // ------------------------------------------------------------ القراءة

    /// كل ما أُرفق داخل الملف باسمه، أو nil إن لم يكن فيه مرفقات.
    static func read(_ url: URL) -> [String: Data]? {
        guard let doc = CGPDFDocument(url as CFURL), !doc.isEncrypted || doc.isUnlocked, let cat = doc.catalog else { return nil }
        var names: CGPDFDictionaryRef?
        guard CGPDFDictionaryGetDictionary(cat, "Names", &names), let n = names else { return nil }
        var tree: CGPDFDictionaryRef?
        guard CGPDFDictionaryGetDictionary(n, "EmbeddedFiles", &tree), let t = tree else { return nil }
        var out: [String: Data] = [:]
        walk(t, depth: 0, into: &out)
        return out.isEmpty ? nil : out
    }

    private static func walk(_ node: CGPDFDictionaryRef, depth: Int, into out: inout [String: Data]) {
        guard depth < 16 else { return }
        var arr: CGPDFArrayRef?
        if CGPDFDictionaryGetArray(node, "Names", &arr), let a = arr {
            let n = CGPDFArrayGetCount(a)
            var i = 0
            while i + 1 < n {
                var s: CGPDFStringRef?
                var spec: CGPDFDictionaryRef?
                if CGPDFArrayGetString(a, i, &s), let str = s, CGPDFArrayGetDictionary(a, i + 1, &spec), let sp = spec,
                   let name = CGPDFStringCopyTextString(str) as String?, let d = fileData(sp) {
                    out[name] = d
                }
                i += 2
            }
        }
        var kids: CGPDFArrayRef?
        if CGPDFDictionaryGetArray(node, "Kids", &kids), let k = kids {
            for i in 0..<CGPDFArrayGetCount(k) {
                var child: CGPDFDictionaryRef?
                if CGPDFArrayGetDictionary(k, i, &child), let c = child { walk(c, depth: depth + 1, into: &out) }
            }
        }
    }

    private static func fileData(_ spec: CGPDFDictionaryRef) -> Data? {
        var ef: CGPDFDictionaryRef?
        guard CGPDFDictionaryGetDictionary(spec, "EF", &ef), let e = ef else { return nil }
        var stream: CGPDFStreamRef?
        guard CGPDFDictionaryGetStream(e, "F", &stream) || CGPDFDictionaryGetStream(e, "UF", &stream), let st = stream else { return nil }
        var format = CGPDFDataFormat.raw
        guard let d = CGPDFStreamCopyData(st, &format) else { return nil }
        return d as Data
    }

    // ------------------------------------------------------------ الكتابة

    /// يضيف المرفقات إلى آخر الملف (تحديث تزايدي). الأسماء ASCII بسيطة. false = تعذّر (الملف لم يُمَس).
    static func append(to url: URL, files: [(name: String, data: Data)]) -> Bool {
        guard !files.isEmpty, let file = try? Data(contentsOf: url) else { return false }
        let from = max(0, file.count - 8192)
        guard let tail = String(data: file.subdata(in: from..<file.count), encoding: .isoLatin1),
              let sx = tail.range(of: "startxref", options: .backwards),
              let tr = tail.range(of: "trailer", options: .backwards, range: tail.startIndex..<sx.lowerBound) else { return false }
        let dict = String(tail[tr.upperBound..<sx.lowerBound])
        let prevDigits = tail[sx.upperBound...].drop { !$0.isNumber }.prefix { $0.isNumber }
        guard let prev = Int(prevDigits), let size = firstInt(dict, "/Size\\s+(\\d+)"),
              let rootNum = firstInt(dict, "/Root\\s+(\\d+)\\s+0\\s+R") else { return false }
        let info = firstMatch(dict, "/Info\\s+(\\d+\\s+\\d+\\s+R)").map { " /Info " + $0 } ?? ""
        let fileId = firstMatch(dict, "(/ID\\s*\\[[^\\]]*\\])").map { " " + $0 } ?? ""
        guard let catalog = catalogBody(file, rootNum, xrefAt: prev), !catalog.contains("/Names") else { return false }

        var seen = Set<String>()
        let sorted = files.filter { isSafe($0.name) && seen.insert($0.name).inserted }.sorted { $0.name < $1.name }
        guard sorted.count == files.count else { return false }

        var out = Data()
        func add(_ s: String) { out.append(s.data(using: .isoLatin1) ?? Data()) }
        var offsets: [Int] = []
        add("\n")
        var num = size
        var refs: [String] = []
        for f in sorted {
            // تدفق الملف المرفق
            offsets.append(file.count + out.count)
            add("\(num) 0 obj\n<< /Type /EmbeddedFile /Length \(f.data.count) /Params << /Size \(f.data.count) >> >>\nstream\n")
            out.append(f.data)
            add("\nendstream\nendobj\n")
            // وصف الملف
            offsets.append(file.count + out.count)
            add("\(num + 1) 0 obj\n<< /Type /Filespec /F (\(f.name)) /UF (\(f.name)) /EF << /F \(num) 0 R >> >>\nendobj\n")
            refs.append("(\(f.name)) \(num + 1) 0 R")
            num += 2
        }
        // نسخة جديدة من الـCatalog فيها شجرة الأسماء
        let catOffset = file.count + out.count
        add("\(rootNum) 0 obj\n<< \(catalog) /Names << /EmbeddedFiles << /Names [ \(refs.joined(separator: " ")) ] >> >> >>\nendobj\n")
        let xrefOffset = file.count + out.count
        add("xref\n\(rootNum) 1\n" + String(format: "%010d 00000 n \n", catOffset))
        add("\(size) \(offsets.count)\n")
        for o in offsets { add(String(format: "%010d 00000 n \n", o)) }
        add("trailer\n<< /Size \(num) /Root \(rootNum) 0 R\(info)\(fileId) /Prev \(prev) >>\nstartxref\n\(xrefOffset)\n%%EOF\n")
        guard let h = try? FileHandle(forWritingTo: url) else { return false }
        defer { try? h.close() }
        h.seekToEndOfFile()
        h.write(out)
        return true
    }

    /// اسم يصلح داخل نص PDF بلا ترميز.
    static func isSafe(_ name: String) -> Bool {
        !name.isEmpty && name.unicodeScalars.allSatisfy { s in
            s.value > 0x20 && s.value < 0x7F && s != "(" && s != ")" && s != "\\" && s != "/"
        }
    }

    /// محتوى قاموس الـCatalog (بلا << >>) من جدول xref، أو nil إن لم يكن قاموسًا بسيطًا.
    private static func catalogBody(_ file: Data, _ num: Int, xrefAt: Int) -> String? {
        guard let off = objectOffset(file, num, xrefAt: xrefAt), off < file.count else { return nil }
        let end = min(file.count, off + 4096)
        guard let s = String(data: file.subdata(in: off..<end), encoding: .isoLatin1),
              s.hasPrefix("\(num) 0 obj"), let e = s.range(of: "endobj") else { return nil }
        let obj = String(s[s.startIndex..<e.lowerBound])
        guard !obj.contains("stream"), let a = obj.range(of: "<<"), let b = obj.range(of: ">>", options: .backwards),
              a.upperBound <= b.lowerBound else { return nil }
        let body = obj[a.upperBound..<b.lowerBound].trimmingCharacters(in: .whitespacesAndNewlines)
        return body.contains("/Type") ? body : nil
    }

    /// موضع الكائن من جدول xref التقليدي (كما يكتبه CoreGraphics).
    private static func objectOffset(_ file: Data, _ num: Int, xrefAt: Int) -> Int? {
        guard xrefAt >= 0, xrefAt < file.count else { return nil }
        var i = xrefAt
        func line() -> String? {
            guard i < file.count else { return nil }
            var j = i
            while j < file.count, file[j] != 10, file[j] != 13 { j += 1 }
            let s = String(data: file.subdata(in: i..<j), encoding: .isoLatin1)
            while j < file.count, file[j] == 10 || file[j] == 13 { j += 1 }
            i = j
            return s
        }
        guard line()?.trimmingCharacters(in: .whitespaces) == "xref" else { return nil }
        while let head = line() {
            let p = head.split(separator: " ")
            guard p.count == 2, let first = Int(p[0]), let count = Int(p[1]) else { return nil }
            if num >= first && num < first + count {
                let at = i + (num - first) * 20
                guard at + 18 <= file.count, let e = String(data: file.subdata(in: at..<(at + 18)), encoding: .isoLatin1) else { return nil }
                let f = e.split(separator: " ")
                guard f.count >= 3, f[2].hasPrefix("n") else { return nil }
                return Int(f[0])
            }
            i += count * 20
        }
        return nil
    }

    private static func firstMatch(_ s: String, _ pattern: String) -> String? {
        guard let re = try? NSRegularExpression(pattern: pattern),
              let m = re.firstMatch(in: s, range: NSRange(s.startIndex..., in: s)), m.numberOfRanges > 1,
              let r = Range(m.range(at: 1), in: s) else { return nil }
        return String(s[r])
    }

    private static func firstInt(_ s: String, _ pattern: String) -> Int? { firstMatch(s, pattern).flatMap { Int($0) } }
}
