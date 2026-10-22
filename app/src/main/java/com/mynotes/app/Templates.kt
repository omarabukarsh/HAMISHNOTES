package com.mynotes.app

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.max

/** قوالب الصفحات (بيضاء، مسطّرة، مربعات، نقاط) مع لون الورقة. */
object Templates {
    val options: List<Pair<String, String>> = listOf(
        "plain" to "بيضاء",
        "lined" to "مسطّرة",
        "grid" to "مربعات",
        "dots" to "نقاط"
    )

    /** ألوان الورق الجاهزة (ARGB، الاسم). */
    val paperColors: List<Pair<Int, String>> = listOf(
        0xFFFFFFFF.toInt() to "أبيض",
        0xFFFFF8E1.toInt() to "كريمي",
        0xFFFFF59D.toInt() to "أصفر",
        0xFFE3F2FD.toInt() to "أزرق فاتح",
        0xFFE8F5E9.toInt() to "أخضر فاتح",
        0xFFFCE4EC.toInt() to "وردي",
        0xFFECEFF1.toInt() to "رمادي",
        0xFF1E232B.toInt() to "داكن"
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
