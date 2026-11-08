import Foundation
import SwiftUI

/// الأدوات: نفس أدوات تطبيق أندرويد.
enum Tool: String, Codable, CaseIterable {
    case ballpoint, fountain, pencil, brush, highlighter, eraser, laser

    var isPen: Bool { self == .ballpoint || self == .fountain || self == .pencil || self == .brush }
    var isWriting: Bool { isPen || self == .highlighter }

    var title: String {
        switch self {
        case .ballpoint: return L("قلم جاف", "Ballpoint")
        case .fountain: return L("قلم حبر", "Fountain pen")
        case .pencil: return L("قلم رصاص", "Pencil")
        case .brush: return L("فرشاة", "Brush")
        case .highlighter: return L("هايلايتر", "Highlighter")
        case .eraser: return L("ممحاة", "Eraser")
        case .laser: return L("ليزر", "Laser")
        }
    }

    var icon: String {
        switch self {
        case .ballpoint: return "ph_pen"
        case .fountain: return "ph_pen_nib"
        case .pencil: return "ph_pencil"
        case .brush: return "ph_brush"
        case .highlighter: return "ph_highlighter"
        case .eraser: return "ph_eraser"
        case .laser: return "ph_laser"
        }
    }
}

enum LaserMode: String, Codable { case dot, line }
enum EraserMode: String, Codable { case stroke, area }
enum ToolbarStyle: String, Codable { case classic, penBar }
enum ShapeKind: String, Codable, CaseIterable { case line, elbow, curve, rect, circle, triangle, diamond, ellipse }

/// عشرة أحجام لكل أداة (بالنقاط على صفحة عرضها نحو 600 نقطة).
enum Sizes {
    static let count = 10
    static func width(_ tool: Tool, _ index: Int) -> CGFloat {
        let i = CGFloat(max(0, min(count - 1, index)))
        switch tool {
        case .highlighter: return (0.010 + i * 0.0035) * 600
        case .eraser: return (0.006 + i * 0.004) * 600 * 2
        case .laser: return (0.004 + i * 0.0015) * 600
        default: return (0.0012 + i * 0.0009) * 600
        }
    }
}

enum LibraryFilter: String, Codable, CaseIterable { case recent, all, favorites }
enum SortMode: String, Codable, CaseIterable { case lastOpened, name, dateAdded }

/// مستند في المكتبة.
struct DocEntry: Codable, Identifiable, Equatable {
    var id: String
    var name: String
    var fileName: String = "doc.pdf"
    var sizeBytes: Int64 = 0
    var pageCount: Int = 0
    var addedAt: Int64
    var lastOpened: Int64 = 0
    var favorite = false
    var lastPage = 0
    var isNote = false
    var readMs: Int64 = 0
    var folder = ""
    var locked = false
    var coverColor: UInt32 = 0
    var sticker = ""
    var isMap = false

    var activityAt: Int64 { max(lastOpened, addedAt) }
    var coverColorOrDefault: Color { coverColor != 0 ? Color(hex: coverColor) : Lib.cover }
}

/// مستطيل بإحداثيات نسبية لعرض الصفحة (مثل أندرويد).
struct MarkRect: Codable, Equatable {
    var l: CGFloat, t: CGFloat, r: CGFloat, b: CGFloat
    init(l: CGFloat, t: CGFloat, r: CGFloat, b: CGFloat) { self.l = l; self.t = t; self.r = r; self.b = b }
    /// المستطيل بنقاط صفحة عرضها w.
    func scaled(_ w: CGFloat) -> CGRect { CGRect(x: l * w, y: t * w, width: (r - l) * w, height: (b - t) * w) }
}

/// تمييز ملتقط على النص: يظهر في درج «ملخصات» (نفس نموذج أندرويد).
struct TextMark: Codable, Identifiable, Equatable {
    var id: String
    var page: String          // معرّف الصفحة
    var rects: [MarkRect]
    var color: UInt32
    var text: String
    var createdAt: Int64
    /// 1 عادي، 2 مهم (ظُلّل مرتين).
    var level = 1
    var inSummary = true
    /// تمييز حر (رسم بالهايلايتر دون نص) لا يدخل الملخص.
    var free = false
    /// أجزاء ظُلّلت مرة ثانية؛ تظهر بخط عريض في الملخص.
    var emphasis: [String] = []
    /// مستطيلات الأجزاء المظللة مرة ثانية (تظليل أقوى على الصفحة).
    var strongRects: [MarkRect] = []

    init(id: String, page: String, rects: [MarkRect], color: UInt32, text: String, createdAt: Int64,
         level: Int = 1, inSummary: Bool = true, free: Bool = false, emphasis: [String] = [], strongRects: [MarkRect] = []) {
        self.id = id; self.page = page; self.rects = rects; self.color = color; self.text = text; self.createdAt = createdAt
        self.level = level; self.inSummary = inSummary; self.free = free; self.emphasis = emphasis; self.strongRects = strongRects
    }

    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        page = try c.decode(String.self, forKey: .page)
        rects = try c.decode([MarkRect].self, forKey: .rects)
        color = try c.decode(UInt32.self, forKey: .color)
        text = try c.decode(String.self, forKey: .text)
        createdAt = try c.decode(Int64.self, forKey: .createdAt)
        level = try c.decodeIfPresent(Int.self, forKey: .level) ?? 1
        inSummary = try c.decodeIfPresent(Bool.self, forKey: .inSummary) ?? true
        free = try c.decodeIfPresent(Bool.self, forKey: .free) ?? false
        emphasis = try c.decodeIfPresent([String].self, forKey: .emphasis) ?? []
        strongRects = try c.decodeIfPresent([MarkRect].self, forKey: .strongRects) ?? []
    }
}

/// مربع نص فوق الصفحة. x و y الزاوية العليا، w العرض، size حجم الخط (نقاط).
struct TextBox: Codable, Identifiable, Equatable {
    var id: String
    var page: String
    var x: CGFloat, y: CGFloat, w: CGFloat
    var text: String
    var color: UInt32 = 0xFF1A2230
    var size: CGFloat = 16
    var font = ""
    var linkDoc: String? = nil
    var linkPage = 0
    /// الكتابة اليدوية الأصلية لنص تحوّل منها (لزر «رجوع لخط يدي»).
    var ink: Data? = nil
    var bold = false
    var italic = false
    var underline = false
    /// 0 بداية، 1 وسط، 2 نهاية.
    var align = 0
}

extension TextBox {
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        page = try c.decode(String.self, forKey: .page)
        x = try c.decode(CGFloat.self, forKey: .x)
        y = try c.decode(CGFloat.self, forKey: .y)
        w = try c.decode(CGFloat.self, forKey: .w)
        text = try c.decodeIfPresent(String.self, forKey: .text) ?? ""
        color = try c.decodeIfPresent(UInt32.self, forKey: .color) ?? 0xFF1A2230
        size = try c.decodeIfPresent(CGFloat.self, forKey: .size) ?? 16
        font = try c.decodeIfPresent(String.self, forKey: .font) ?? ""
        linkDoc = try c.decodeIfPresent(String.self, forKey: .linkDoc)
        linkPage = try c.decodeIfPresent(Int.self, forKey: .linkPage) ?? 0
        ink = try c.decodeIfPresent(Data.self, forKey: .ink)
        bold = try c.decodeIfPresent(Bool.self, forKey: .bold) ?? false
        italic = try c.decodeIfPresent(Bool.self, forKey: .italic) ?? false
        underline = try c.decodeIfPresent(Bool.self, forKey: .underline) ?? false
        align = try c.decodeIfPresent(Int.self, forKey: .align) ?? 0
    }
}

/// صورة أو ملصق فوق الصفحة.
struct ImageItem: Codable, Identifiable, Equatable {
    var id: String
    var file: String          // اسم الملف في مجلد المستند، أو "sticker:key"
    var page: String
    var x: CGFloat, y: CGFloat, w: CGFloat
    var aspect: CGFloat       // العرض ÷ الارتفاع
}

/// ملاحظة لاصقة: مربع ملوّن يُكتب عليه.
struct StickyNote: Codable, Identifiable, Equatable {
    var id: String
    var page: String
    var x: CGFloat, y: CGFloat, w: CGFloat, h: CGFloat
    var color: UInt32
    var style = 0
    var collapsed = false
    var text = ""
    /// الاسم على الملاحظة (اختياري).
    var author = ""
    /// الكتابة بالقلم داخل الملاحظة (بنقاط الملاحظة).
    var ink: Data? = nil
}

extension StickyNote {
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        page = try c.decode(String.self, forKey: .page)
        x = try c.decode(CGFloat.self, forKey: .x)
        y = try c.decode(CGFloat.self, forKey: .y)
        w = try c.decode(CGFloat.self, forKey: .w)
        h = try c.decode(CGFloat.self, forKey: .h)
        color = try c.decodeIfPresent(UInt32.self, forKey: .color) ?? 0xFFFFE27A
        style = try c.decodeIfPresent(Int.self, forKey: .style) ?? 0
        collapsed = try c.decodeIfPresent(Bool.self, forKey: .collapsed) ?? false
        text = try c.decodeIfPresent(String.self, forKey: .text) ?? ""
        author = try c.decodeIfPresent(String.self, forKey: .author) ?? ""
        ink = try c.decodeIfPresent(Data.self, forKey: .ink)
    }
}

/// عنوان في فهرس المستند.
struct OutlineEntry: Codable, Equatable, Identifiable {
    var id = UUID().uuidString
    var title: String
    var level: Int
    var page: Int
}

/// صفحة في المستند: معرّف ثابت (للحبر والتمييز) ومصدرها.
struct PageInfo: Codable, Equatable {
    var id: String
    /// "pdf" أو "blank:قالب:لون"
    var src: String = "pdf"
}

/// كل ما يُكتب على المستند (يُحفظ بجانب ملف PDF).
struct DocData: Codable {
    var pages: [PageInfo] = []
    var bookmarks: [String] = []
    var marks: [TextMark] = []
    var boxes: [TextBox] = []
    var images: [ImageItem] = []
    var notes: [StickyNote] = []
    var outline: [OutlineEntry] = []
    var map: MindMapData? = nil
    var recordings: [AudioRec] = []
    /// تعديلات المستخدم على الملخص (عناوين، ترتيب، جداول).
    var summaryEdits: SummaryEdits? = nil
    /// ملء الأشكال المغلقة (تحت الكتابة).
    var fills: [ShapeFill] = []
    /// نصوص الكتابة المجمَّلة إلى حبر: تُقرأ في البحث والتقاط النص والملخصات.
    var converted: [ConvertedLine] = []
}

extension DocData {
    /// قراءة متسامحة: الحقول الجديدة اختيارية فلا تضيع بيانات الملفات القديمة.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        pages = try c.decodeIfPresent([PageInfo].self, forKey: .pages) ?? []
        bookmarks = try c.decodeIfPresent([String].self, forKey: .bookmarks) ?? []
        marks = try c.decodeIfPresent([TextMark].self, forKey: .marks) ?? []
        boxes = try c.decodeIfPresent([TextBox].self, forKey: .boxes) ?? []
        images = try c.decodeIfPresent([ImageItem].self, forKey: .images) ?? []
        notes = try c.decodeIfPresent([StickyNote].self, forKey: .notes) ?? []
        outline = try c.decodeIfPresent([OutlineEntry].self, forKey: .outline) ?? []
        map = try c.decodeIfPresent(MindMapData.self, forKey: .map)
        recordings = try c.decodeIfPresent([AudioRec].self, forKey: .recordings) ?? []
        summaryEdits = try c.decodeIfPresent(SummaryEdits.self, forKey: .summaryEdits)
        fills = try c.decodeIfPresent([ShapeFill].self, forKey: .fills) ?? []
        converted = try c.decodeIfPresent([ConvertedLine].self, forKey: .converted) ?? []
    }
}

/// ملء شكل مغلق: مضلع بنقاط الصفحة، مرتبط بخط إطاره (وقت إنشائه) فيُمسح ويتحرك معه.
struct ShapeFill: Codable, Identifiable, Equatable {
    var id: String
    var page: String
    var pts: [CGPoint]
    var color: UInt32
    var alpha: CGFloat
    /// وقت إنشاء خط الإطار (timeIntervalSinceReferenceDate).
    var key: Double
}

/// سطر كتابة مجمَّل إلى حبر: نصه وكلماته بمواضعها، ومفتاح خطوطه (وقت إنشائها).
struct ConvertedLine: Codable, Identifiable, Equatable {
    var id: String
    var page: String
    var key: Double
    var words: [OcrWord]
}

/// تسجيل صوتي مرتبط بالمستند.
struct AudioRec: Codable, Identifiable, Equatable {
    var id: String
    var start: Int64
    var durationMs: Int64
    var file: String
}

// ---------- البطاقات ----------

struct SrsState: Codable, Equatable {
    var ease: Double = 2.5
    var interval: Int = 0
    var due: Int64 = 0
    var reps: Int = 0
}

struct Card: Codable, Identifiable, Equatable {
    var id: String
    var front: String
    var back: String
    var folder: String = ""
    var docId: String? = nil
    var source: String = ""
    var starred = false
    var srs = SrsState()
    var createdAt: Int64 = nowMs()
}

// ---------- الخريطة الذهنية ----------

struct MapNodeData: Codable, Identifiable, Equatable {
    var id: String = UUID().uuidString
    var text: String
    var markId: String? = nil
    var collapsed = false
    var children: [MapNodeData] = []

    var count: Int { children.count + children.reduce(0) { $0 + $1.count } }
}

struct MindMapData: Codable, Equatable {
    var title: String
    var branches: [MapNodeData] = []
}

// ---------- أنماط الأقلام المحفوظة ----------

struct PenPreset: Codable, Identifiable, Equatable {
    var id = UUID().uuidString
    var tool: Tool
    var color: UInt32
    var size: Int
}
