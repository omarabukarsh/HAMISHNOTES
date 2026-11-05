package com.hamishnotes.app

import androidx.compose.ui.graphics.Color

enum class Tool { BALLPOINT, FOUNTAIN, PENCIL, HIGHLIGHTER, ERASER, LASER, BRUSH }

/** نمط الخط: متصل، متقطع، منقّط. */
object LineStyle {
    const val SOLID = 0
    const val DASHED = 1
    const val DOTTED = 2
}

/** أشكال أداة «الأشكال». */
enum class ShapeKind { LINE, ELBOW, CURVE, RECT, CIRCLE, TRIANGLE, DIAMOND, ELLIPSE }

/** رأس السهم في طرف الخط. */
enum class ArrowHead { NONE, OPEN, CLOSED }

/**
 * ملاحظة لاصقة على الصفحة: مربع ملوّن يُكتب عليه بالقلم.
 * x, y, w, h نسبية لعرض الصفحة؛ ink بإحداثيات نسبية لعرض الملاحظة (0..1).
 * style: 0 سادة، 1 بشريط جانبي، 2 مسطّرة، 3 مربعات.
 */
data class StickyNote(
    val id: String,
    val page: Int,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val color: Int,
    val style: Int = 0,
    val collapsed: Boolean = false,
    val author: String = "",
    val ink: List<Ink> = emptyList(),
    /** نص مكتوب بلوحة المفاتيح. */
    val text: String = ""
)

/** حجم خط نص الملاحظة (نسبة لعرض الصفحة). */
fun StickyNote.textSize(): Float = (w * 0.075f).coerceIn(0.008f, 0.03f)

/** نص الملاحظة كمربع نص (للتصدير). */
fun StickyNote.textBox(): TextBox? =
    if (collapsed || text.isBlank()) null
    else TextBox("note-" + id, page, x + w * 0.06f, y + w * 0.06f, w * 0.88f, text, androidx.compose.ui.graphics.Color(0xFF1F2933), textSize())

/** خطوط الملاحظة بإحداثيات الصفحة. */
fun StickyNote.pageInk(): List<Ink> =
    if (collapsed) emptyList() else ink.map { s -> s.copy(pts = s.pts.map { Pt(x + it.x * w, y + it.y * w, it.p) }, width = s.width * w) }

/** عنوان في فهرس المستند (قابل للتعديل): page = رقم الصفحة الحالي. */
data class OutlineEntry(val title: String, val level: Int, val page: Int)

/** نمط الليزر: نقطة متوهجة، أو خط مستمر يتلاشى بعد ثانية ونصف. */
enum class LaserMode { DOT, LINE }

/** نمط الممحاة: STROKE يمسح الخط المرسوم دفعة واحدة كاملًا، AREA يمسح الجزء الذي تلمسه فقط. */
enum class EraserMode { STROKE, AREA }

/** شكل شريط الأدوات: الكلاسيكي (القلم والهايلايتر زرّان)، أو شريط ثانٍ لكل الأقلام يظهر عند الضغط على القلم. */
enum class ToolbarStyle { CLASSIC, PEN_BAR }

/** الأقلام العادية الثلاثة (جاف، حبر، رصاص). */
fun Tool.isPen(): Boolean =
    this == Tool.BALLPOINT || this == Tool.FOUNTAIN || this == Tool.PENCIL || this == Tool.BRUSH

data class Pt(val x: Float, val y: Float, val p: Float)

/** كل الإحداثيات نسبية لعرض الصفحة (x و y كلاهما مقسوم على العرض). */
data class Ink(
    val pts: List<Pt>,
    val color: Color,
    val width: Float,
    val tool: Tool = Tool.FOUNTAIN,
    /** وقت كتابة الخط (للربط بتسجيل الصوت)؛ 0 = غير معروف. */
    val t: Long = 0L,
    /** نمط الخط (LineStyle). */
    val style: Int = LineStyle.SOLID,
    /** رُسم بأداة الأشكال أو حُوّل إلى شكل. */
    val shape: Boolean = false
)

/** صورة مضافة فوق صفحة. x و y الزاوية العليا اليسرى، w العرض، aspect = العرض ÷ الارتفاع. */
data class ImageItem(
    val id: String,
    val file: String,
    val page: Int,
    val x: Float,
    val y: Float,
    val w: Float,
    val aspect: Float
)

/** مستطيل بإحداثيات نسبية لعرض الصفحة. */
data class MarkRect(val l: Float, val t: Float, val r: Float, val b: Float)

/**
 * تمييز ملتقط على النص: مستطيلات خلف الأسطر المميزة، مع النص نفسه.
 * يظهر في درج «ملخصات» الخاص بالمستند. text قابل للتعديل من الدرج.
 */
data class TextMark(
    val id: String,
    val page: Int,
    val rects: List<MarkRect>,
    val color: Color,
    val text: String,
    val createdAt: Long,
    /** 1 عادي، 2 مهم (ظُلّل مرتين): يظهر بخط عريض وتظليل أقوى. */
    val level: Int = 1,
    /** أجزاء من النص ظُلّلت مرة ثانية؛ تظهر بخط عريض في الملخص. */
    val emphasis: List<String> = emptyList(),
    /** مستطيلات الأجزاء المظللة مرة ثانية (تظليل أقوى على الصفحة). */
    val strongRects: List<MarkRect> = emptyList(),
    /** false = تمييز على الصفحة فقط، لا يظهر في الملخصات. */
    val inSummary: Boolean = true
)

/*
 * صيغة نص التمييز (قابلة للتعديل من الملخصات):
 *   فقرة عن فقرة بسطر فارغ، والسطر داخل الفقرة يُدمج عند الترتيب.
 *   "# " في أول الفقرة = عنوان (نص أكبر من نص الصفحة).
 *   صفوف الجدول: الخلايا يفصلها " | ".
 */

/** مربع نص مكتوب بالكيبورد فوق صفحة. x و y الزاوية العليا، w العرض، size حجم الخط نسبةً لعرض الصفحة. */
data class TextBox(
    val id: String,
    val page: Int,
    val x: Float,
    val y: Float,
    val w: Float,
    val text: String,
    val color: Color,
    val size: Float,
    /** ملاحظة لاصقة: تظهر أيقونة صغيرة مطوية، وتُفتح بالنقر. */
    val sticky: Boolean = false,
    /** رابط إلى مستند (معرّفه) وصفحة فيه؛ null = ليس رابطًا. */
    val linkDoc: String? = null,
    val linkPage: Int = 0,
    /** اسم الخط من مكتبة الخطوط؛ فارغ = الخط الافتراضي. */
    val font: String = "",
    /** الكتابة اليدوية التي حُوّلت إلى هذا النص (لإرجاعها)؛ فارغة لمربعات النص العادية. */
    val ink: List<Ink> = emptyList()
)

/** عشرة أحجام لكل أداة. للممحاة القيمة هي نصف القطر، وللهايلايتر عرض الشريط. */
object Sizes {
    const val COUNT = 10

    fun width(tool: Tool, index: Int): Float {
        val i = index.coerceIn(0, COUNT - 1)
        return when (tool) {
            Tool.HIGHLIGHTER -> 0.010f + i * 0.0035f
            Tool.ERASER -> 0.006f + i * 0.004f
            Tool.LASER -> 0.004f + i * 0.0015f
            else -> 0.0012f + i * 0.0009f
        }
    }
}

// ---------- القراءة الضوئية (OCR) ----------

data class OcrWord(
    val text: String,
    val l: Float,
    val t: Float,
    val r: Float,
    val b: Float,
    val line: Int
)

data class OcrPage(val words: List<OcrWord>, val version: Int)

data class SearchHit(val page: Int, val start: Int, val end: Int)

data class TextSelection(val page: Int, val start: Int, val end: Int)

// ---------- المكتبة ----------

/**
 * أنواع المستندات. للإضافة لاحقًا (DOCX / PPTX) يكفي تحويل supported إلى true
 * وتوفير مسار فتح/تحويل للنوع الجديد في المستودع.
 */
enum class DocType(val ext: String, val mime: String, val supported: Boolean) {
    PDF("pdf", "application/pdf", true),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", false),
    PPTX("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", false);

    companion object {
        fun fromExt(ext: String): DocType? =
            values().firstOrNull { it.ext.equals(ext, ignoreCase = true) }
    }
}

data class DocEntry(
    val id: String,
    val name: String,
    val type: DocType,
    val fileName: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val addedAt: Long,
    val lastOpened: Long,
    val favorite: Boolean,
    val lastPage: Int,
    val isNote: Boolean = false,
    /** مجموع وقت القراءة الفعلي بالمللي ثانية. */
    val readMs: Long = 0L,
    /** المادة (المجلد) التي ينتمي إليها المستند؛ فارغ = بدون مادة. */
    val folder: String = "",
    /** مستند مقفل برمز أو بصمة. */
    val locked: Boolean = false,
    /** لون غلاف الدفتر في المكتبة (ARGB)؛ 0 = اللون الافتراضي. */
    val coverColor: Int = 0,
    /** ملصق على الغلاف (رمز تعبيري)؛ فارغ = بدون. */
    val sticker: String = "",
    /** خريطة ذهنية مستقلة (من «جديد»): تُفتح على الخريطة مباشرة، وكل عقدها نصوص حرة. */
    val isMap: Boolean = false
)

fun DocEntry.activityAt(): Long = maxOf(lastOpened, addedAt)

enum class LibraryFilter { RECENT, ALL, FAVORITES }

enum class SortMode { LAST_OPENED, NAME, DATE_ADDED }
