package com.mynotes.app

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.max

/** قوالب الصفحات الفارغة. تُرسم على صفحة الملاحظة الجديدة وعلى الصفحات الفارغة المضافة. */
object Templates {
    val options: List<Pair<String, String>> = listOf(
        "plain" to "بيضاء",
        "lined" to "مسطّرة",
        "grid" to "مربعات",
        "dots" to "نقاط"
    )

    fun draw(canvas: Canvas, w: Float, h: Float, kind: String) {
        if (kind == "plain") return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xFFB9C5D8.toInt()
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
