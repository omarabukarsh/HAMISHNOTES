package com.mynotes.app

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

/**
 * تحليل شكل الصفحة من الصورة نفسها: لون الخط، التسطير، الغليظ (من كثافة الحبر)، والخطوط المرسومة (حدود الجداول والفواصل).
 * ML Kit يعطي النص ومكانه فقط، فهذه الأنماط تُستخرج هنا من البكسلات. كله على الجهاز ودون إنترنت.
 */
object OcrStyle {
    private const val DARK = 120

    /** اسم اللون بالإنجليزية (للنموذج)، أو null للأسود والرمادي والأبيض ولون غير معروف. */
    fun colorName(argb: Int): String? {
        if (argb == -1) return null
        val hsv = FloatArray(3)
        Color.colorToHSV(argb, hsv)
        if (hsv[1] < 0.35f || hsv[2] < 0.3f) return null
        val h = hsv[0]
        return when {
            h < 15f || h >= 340f -> "red"
            h < 45f -> "orange"
            h < 70f -> "yellow"
            h < 165f -> "green"
            h < 255f -> "blue"
            else -> "purple"
        }
    }

    private fun lum(p: Int): Int = (Color.red(p) * 3 + Color.green(p) * 6 + Color.blue(p)) / 10

    /** الكلمات بعد إضافة اللون والتسطير والغليظ، مع الخطوط المرسومة. إحداثيات الكلمات نسبية لعرض الصورة. */
    fun analyze(bmp: Bitmap, words: List<OcrWord>): Pair<List<OcrWord>, List<OcrShape>> {
        val w = bmp.width
        val h = bmp.height
        if (w < 50 || h < 50) return Pair(words, emptyList())
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val shapes = shapes(px, w, h)
        if (words.isEmpty()) return Pair(words, shapes)

        // كثافة الحبر لكل كلمة (لتقدير الغليظ نسبة إلى باقي الصفحة)
        val density = FloatArray(words.size)
        val colors = IntArray(words.size) { -1 }
        words.forEachIndexed { i, wd ->
            val x0 = (wd.l * w).toInt().coerceIn(0, w - 1)
            val x1 = (wd.r * w).toInt().coerceIn(x0 + 1, w)
            val y0 = (wd.t * w).toInt().coerceIn(0, h - 1)
            val y1 = (wd.b * w).toInt().coerceIn(y0 + 1, h)
            var dark = 0
            var total = 0
            var rs = 0L
            var gs = 0L
            var bs = 0L
            var inkN = 0
            val stepY = max(1, (y1 - y0) / 24)
            val stepX = max(1, (x1 - x0) / 48)
            var y = y0
            while (y < y1) {
                var x = x0
                while (x < x1) {
                    val p = px[y * w + x]
                    total++
                    if (lum(p) < 150) {
                        dark++
                        // لون الحبر: أغمق البكسلات فقط (حواف الحروف تخلطها بلون الخلفية)
                        if (lum(p) < 110 || Color.red(p) < 90 || Color.green(p) < 90 || Color.blue(p) < 90) {
                            rs += Color.red(p); gs += Color.green(p); bs += Color.blue(p); inkN++
                        }
                    }
                    x += stepX
                }
                y += stepY
            }
            density[i] = if (total == 0) 0f else dark / total.toFloat()
            if (inkN >= 4) colors[i] = Color.rgb((rs / inkN).toInt(), (gs / inkN).toInt(), (bs / inkN).toInt())
        }
        val sortedD = density.filter { it > 0f }.sorted()
        val medD = if (sortedD.isEmpty()) 0f else sortedD[sortedD.size / 2]

        // حدود كل سطر: تسطير العنوان يمتد على السطر كله، فيُقاس امتداده نسبة إلى السطر لا إلى الكلمة
        val lineL = HashMap<Int, Float>()
        val lineR = HashMap<Int, Float>()
        for (wd in words) {
            lineL[wd.line] = min(lineL[wd.line] ?: wd.l, wd.l)
            lineR[wd.line] = max(lineR[wd.line] ?: wd.r, wd.r)
        }
        val out = words.mapIndexed { i, wd ->
            val col = colors[i]
            val named = colorName(col)
            val bold = wd.bold || (medD > 0f && density[i] > medD * 1.45f && (wd.r - wd.l) * w > 18)
            wd.copy(
                bold = bold,
                underline = wd.underline || underlined(px, w, h, wd, lineL[wd.line] ?: wd.l, lineR[wd.line] ?: wd.r),
                color = if (named != null) col else -1
            )
        }
        return Pair(out, shapes)
    }

    /**
     * خط أفقي رفيع تحت الكلمة بطولها تقريبًا. يُبحث من منطقة ذيول الحروف (PowerPoint يرسم التسطير
     * داخل مربع الكلمة تحت خط الأساس) إلى ما تحت المربع قليلًا. الخط الممتد بعيدًا عن حدود السطر
     * (lineL..lineR) حد جدول أو فاصل، لا تسطير.
     */
    private fun underlined(px: IntArray, w: Int, h: Int, wd: OcrWord, lineL: Float, lineR: Float): Boolean {
        val x0 = (wd.l * w).toInt().coerceIn(0, w - 1)
        val x1 = (wd.r * w).toInt().coerceIn(x0 + 1, w)
        val y1 = (wd.b * w).toInt().coerceIn(0, h - 1)
        val hh = max(4, ((wd.b - wd.t) * w).toInt())
        val width = x1 - x0
        if (width < 12) return false
        val l0 = (lineL * w).toInt().coerceIn(0, w - 1)
        val r0 = (lineR * w).toInt().coerceIn(l0 + 1, w)
        val lineW = r0 - l0
        val yEnd = min(h - 1, y1 + max(3, hh / 3))
        for (y in max(0, y1 - (hh * 0.35f).toInt())..yEnd) {
            var dark = 0
            for (x in x0 until x1) if (lum(px[y * w + x]) < 150) dark++
            if (dark < width * 0.85f) continue
            var left = 0
            var x = l0 - 1
            while (x >= 0 && left < lineW && lum(px[y * w + x]) < 150) { left++; x-- }
            var right = 0
            x = r0
            while (x < w && right < lineW && lum(px[y * w + x]) < 150) { right++; x++ }
            if (left + right < lineW * 0.5f) return true
        }
        return false
    }

    /** خطوط أفقية وعمودية طويلة (حدود الجداول والفواصل). kind: 0 أفقي، 1 عمودي. نسبة إلى عرض الصفحة. */
    private fun shapes(px: IntArray, w: Int, h: Int): List<OcrShape> {
        val out = ArrayList<OcrShape>()
        val minH = (w * 0.2f).toInt()
        // أفقي
        var runStart = -1
        var l = w
        var r = 0
        fun closeH(endY: Int) {
            if (runStart >= 0 && endY - runStart <= 10) out.add(OcrShape(0, l / w.toFloat(), runStart / w.toFloat(), r / w.toFloat(), endY / w.toFloat()))
            runStart = -1; l = w; r = 0
        }
        for (y in 0 until h) {
            var best = 0
            var bestStart = 0
            var cur = 0
            var start = 0
            var gap = 0
            for (x in 0 until w) {
                if (lum(px[y * w + x]) < DARK) {
                    if (cur == 0) start = x
                    cur++
                    gap = 0
                    if (cur > best) { best = cur; bestStart = start }
                } else if (cur > 0 && ++gap > 2) {
                    cur = 0
                    gap = 0
                }
            }
            if (best >= minH) {
                if (runStart < 0) runStart = y
                l = min(l, bestStart); r = max(r, bestStart + best)
            } else if (runStart >= 0) closeH(y)
        }
        closeH(h)
        // عمودي
        val minV = (h * 0.12f).toInt()
        var vStart = -1
        var vt = h
        var vb = 0
        fun closeV(endX: Int) {
            if (vStart >= 0 && endX - vStart <= 10) out.add(OcrShape(1, vStart / w.toFloat(), vt / w.toFloat(), endX / w.toFloat(), vb / w.toFloat()))
            vStart = -1; vt = h; vb = 0
        }
        for (x in 0 until w) {
            var best = 0
            var bestStart = 0
            var cur = 0
            var start = 0
            var gap = 0
            for (y in 0 until h) {
                if (lum(px[y * w + x]) < DARK) {
                    if (cur == 0) start = y
                    cur++
                    gap = 0
                    if (cur > best) { best = cur; bestStart = start }
                } else if (cur > 0 && ++gap > 2) {
                    cur = 0
                    gap = 0
                }
            }
            if (best >= minV) {
                if (vStart < 0) vStart = x
                vt = min(vt, bestStart); vb = max(vb, bestStart + best)
            } else if (vStart >= 0) closeV(x)
        }
        closeV(w)
        // صفحة صورة داكنة تنتج خطوطًا كثيرة: تُهمل
        return if (out.size > 60) emptyList() else out
    }
}
