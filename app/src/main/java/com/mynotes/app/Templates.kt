package com.mynotes.app

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.max

/** قوالب الصفحات (بيضاء، مسطّرة، مربعات، نقاط) مع لون الورقة. */
object Templates {
    val options: List<Pair<String, String>> get() = listOf(
        "plain" to L("بيضاء", "Plain"),
        "lined" to L("مسطّرة", "Lined"),
        "grid" to L("مربعات", "Grid"),
        "dots" to L("نقاط", "Dots"),
        "cornell" to L("كورنيل", "Cornell")
    )

    /** ألوان الورق الجاهزة (ARGB، الاسم). */
    val paperColors: List<Pair<Int, String>> get() = listOf(
        0xFFFFFFFF.toInt() to L("أبيض", "White"),
        0xFFFFF8E1.toInt() to L("كريمي", "Cream"),
        0xFFFFF59D.toInt() to L("أصفر", "Yellow"),
        0xFFE3F2FD.toInt() to L("أزرق فاتح", "Light blue"),
        0xFFE8F5E9.toInt() to L("أخضر فاتح", "Light green"),
        0xFFFCE4EC.toInt() to L("وردي", "Pink"),
        0xFFECEFF1.toInt() to L("رمادي", "Gray"),
        0xFF1E232B.toInt() to L("داكن", "Dark")
    )

    /** يبني نص مصدر الصفحة الفارغة، مثل: blank:lined:FFFFF8E1 */
    fun src(template: String, bg: Int): String = "blank:$template:" + String.format("%08X", bg)

    /** يقرأ نص المصدر ويعيد (القالب، لون الورقة). */
    fun parse(src: String): Pair<String, Int> {
        val parts = src.removePrefix("blank:").split(":")
        val kind = parts.getOrNull(0) ?: "plain"
        val bg = parts.getOrNull(1)?.toLongOrNull(16)?.toInt() ?: 0xFFFFFFFF.toInt()
        return Pair(kind, bg)
    }

    fun draw(canvas: Canvas, w: Float, h: Float, kind: String, bg: Int = 0xFFFFFFFF.toInt()) {
        canvas.drawColor(bg)
        if (kind == "plain") return
        val lum = (0.299 * ((bg shr 16) and 0xFF) + 0.587 * ((bg shr 8) and 0xFF) + 0.114 * (bg and 0xFF)) / 255.0
        val lineColor = if (lum < 0.4) 0x55FFFFFF else 0xFFB9C5D8.toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = lineColor
        paint.strokeWidth = max(1f, w / 650f)
        val step = w / 24f
        when (kind) {
            "lined" -> {
                var y = step * 2.5f
                while (y < h - step) {
                    canvas.drawLine(step, y, w - step, y, paint)
                    y += step
                }
            }
            "grid" -> {
                var x = step
                while (x < w) {
                    canvas.drawLine(x, 0f, x, h, paint)
                    x += step
                }
                var y = step
                while (y < h) {
                    canvas.drawLine(0f, y, w, y, paint)
                    y += step
                }
            }
            // قالب كورنيل: عمود الكلمات المفتاحية، مساحة الشرح بأسطر، وخلاصة في الأسفل
            "cornell" -> {
                val strong = Paint(paint).apply { strokeWidth = max(2f, w / 330f) }
                val top = h * 0.085f
                val cueX = w * 0.30f
                val sumY = h * 0.80f
                canvas.drawLine(0f, top, w, top, strong)
                canvas.drawLine(cueX, top, cueX, sumY, strong)
                canvas.drawLine(0f, sumY, w, sumY, strong)
                var y = top + step * 1.2f
                while (y < sumY - step * 0.3f) {
                    canvas.drawLine(cueX + step * 0.5f, y, w - step * 0.6f, y, paint)
                    y += step
                }
                y = sumY + step * 1.2f
                while (y < h - step * 0.5f) {
                    canvas.drawLine(step * 0.6f, y, w - step * 0.6f, y, paint)
                    y += step
                }
            }
            "dots" -> {
                paint.style = Paint.Style.FILL
                val r = max(1.5f, w / 420f)
                var y = step
                while (y < h) {
                    var x = step
                    while (x < w) {
                        canvas.drawCircle(x, y, r, paint)
                        x += step
                    }
                    y += step
                }
            }
        }
    }
}
