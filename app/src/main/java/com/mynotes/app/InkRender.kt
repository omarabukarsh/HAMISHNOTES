package com.mynotes.app

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** مسار ناعم عبر النقاط. extra نقطة تنبؤية اختيارية تُضاف في النهاية (لا تُحفظ). */
internal fun buildPath(pts: List<Pt>, w: Float, extra: Pt?): Path {
    val n = pts.size + (if (extra != null) 1 else 0)
    val path = Path()
    if (n == 0) return path
    fun px(i: Int): Float = (if (i < pts.size) pts[i].x else extra!!.x) * w
    fun py(i: Int): Float = (if (i < pts.size) pts[i].y else extra!!.y) * w
    path.moveTo(px(0), py(0))
    if (n == 1) {
        path.lineTo(px(0) + 0.01f, py(0))
        return path
    }
    for (i in 1 until n - 1) {
        val mx = (px(i) + px(i + 1)) / 2f
        val my = (py(i) + py(i + 1)) / 2f
        path.quadraticBezierTo(px(i), py(i), mx, my)
    }
    path.lineTo(px(n - 1), py(n - 1))
    return path
}

/** يرسم خطًا واحدًا حسب نوع القلم. */
internal fun DrawScope.drawInk(
    pts: List<Pt>,
    color: Color,
    width: Float,
    tool: Tool,
    extra: Pt? = null,
    scaleW: Float = size.width
) {
    val n = pts.size + (if (extra != null) 1 else 0)
    if (n == 0) return
    val w = scaleW
    when (tool) {
        Tool.FOUNTAIN -> {
            fun pt(i: Int): Pt = if (i < pts.size) pts[i] else extra!!
            if (n == 1) {
                val a = pt(0)
                drawCircle(color, width * w / 2f, Offset(a.x * w, a.y * w))
                return
            }
            for (j in 1 until n) {
                val a = pt(j - 1)
                val c = pt(j)
                drawLine(
                    color,
                    Offset(a.x * w, a.y * w),
                    Offset(c.x * w, c.y * w),
                    strokeWidth = width * w * (0.4f + c.p),
                    cap = StrokeCap.Round
                )
            }
        }
        Tool.HIGHLIGHTER -> drawPath(
            buildPath(pts, w, extra),
            color.copy(alpha = 0.35f),
            style = Stroke(width = width * w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        Tool.PENCIL -> drawPath(
            buildPath(pts, w, extra),
            color.copy(alpha = 0.78f),
            style = Stroke(width = width * w * 0.85f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        else -> drawPath(
            buildPath(pts, w, extra),
            color,
            style = Stroke(width = width * w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/** نصف قطر زوايا التمييز: حواف دائرية ناعمة نسبةً لارتفاع السطر. */
fun markCorner(heightPx: Float): Float = heightPx * 0.32f

/** مستطيل تمييز بحواف دائرية بمزج «ضرب» (يبدو خلف النص). */
fun DrawScope.drawMarkRect(color: Color, r: MarkRect, w: Float, night: Boolean = false) {
    val h = (r.b - r.t) * w
    // ليلًا: الصفحة داكنة والنص فاتح، فيُرسم التمييز بمزج «تفتيح» بلون أخفت (يبقى النص الفاتح واضحًا)
    val c = if (night) Color(color.red * 0.55f, color.green * 0.55f, color.blue * 0.55f) else color
    drawRoundRect(
        c, Offset(r.l * w, r.t * w), Size((r.r - r.l) * w, h),
        cornerRadius = CornerRadius(markCorner(h)),
        blendMode = if (night) BlendMode.Screen else BlendMode.Multiply
    )
}

/**
 * القراءة الليلية: يعكس السطوع مع الحفاظ على اللون تقريبًا (عكس ثم تدوير اللون 180°)،
 * فالحبر الأسود يصبح أبيض والأزرق يبقى أزرق.
 */
val NightMatrix = floatArrayOf(
    0.333f, -0.667f, -0.667f, 0f, 255f,
    -0.667f, 0.333f, -0.667f, 0f, 255f,
    -0.667f, -0.667f, 0.333f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f
)

fun nightOf(c: Color): Color {
    val m = NightMatrix
    val r = c.red * 255f
    val g = c.green * 255f
    val b = c.blue * 255f
    fun ch(i: Int) = ((m[i] * r + m[i + 1] * g + m[i + 2] * b + m[i + 4]) / 255f).coerceIn(0f, 1f)
    return Color(ch(0), ch(5), ch(10), c.alpha)
}
