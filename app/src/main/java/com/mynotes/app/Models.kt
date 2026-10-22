package com.mynotes.app

import androidx.compose.ui.graphics.Color

enum class Tool { BALLPOINT, FOUNTAIN, PENCIL, HIGHLIGHTER, ERASER, LASER }

/** الأقلام العادية الثلاثة (جاف، حبر، رصاص). */
fun Tool.isPen(): Boolean =
    this == Tool.BALLPOINT || this == Tool.FOUNTAIN || this == Tool.PENCIL

data class Pt(val x: Float, val y: Float, val p: Float)

/** كل الإحداثيات نسبية لعرض الصفحة (x و y كلاهما مقسوم على العرض). */
data class Ink(
    val pts: List<Pt>,
    val color: Color,
    val width: Float,
    val tool: Tool = Tool.FOUNTAIN
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
    val createdAt: Long
)

/** مربع نص مكتوب بالكيبورد فوق صفحة. x و y الزاوية العليا، w العرض، size حجم الخط نسبةً لعرض الصفحة. */
data class TextBox(
    val id: String,
    val page: Int,
    val x: Float,
    val y: Float,
    val w: Float,
    val text: String,
    val color: Color,
    val size: Float
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
    val isNote: Boolean = false
)

fun DocEntry.activityAt(): Long = maxOf(lastOpened, addedAt)

enum class LibraryFilter { RECENT, ALL, FAVORITES }

enum class SortMode { LAST_OPENED, NAME, DATE_ADDED }
