package com.hamishnotes.app

import kotlin.math.abs

/**
 * كلمات الكتابة المحوّلة (تجميل الخط): الحروف المملوءة لا تُقرأ كخط يد، لكن كل حرف يحمل كلمته
 * ورقمها منذ التحويل. من هنا تُبنى كلمات الصفحة بأماكنها للهايلايتر والبحث والملخصات.
 * منطق خالص ليُختبر على JVM.
 */
object ConvertedWords {

    /** الكلمة التي يقع فيها الحرف (مركزه الأفقي بوحدة الحجم)، أو -1. */
    fun wordOf(piece: List<FloatArray>, words: List<Triple<String, Float, Float>>): Int {
        if (words.isEmpty()) return -1
        var sum = 0f
        var n = 0
        for (c in piece) {
            var i = 0
            while (i < c.size) { sum += c[i]; n++; i += 2 }
        }
        if (n == 0) return -1
        val cx = sum / n
        val inside = words.indexOfFirst { cx >= it.second && cx <= it.third }
        if (inside >= 0) return inside
        return words.indices.minByOrNull { i -> minOf(abs(cx - words[i].second), abs(cx - words[i].third)) } ?: -1
    }

    private class Box(val text: String, var l: Float, var t: Float, var r: Float, var b: Float, val em: Float)

    /** كلمات الحروف المحوّلة في الصفحة، أسطراً من الأعلى، وبترتيب القراءة داخل كل سطر. */
    fun read(strokes: List<Ink>, firstLine: Int = 0): List<OcrWord> {
        val letters = strokes.filter { it.fill && it.word.isNotEmpty() && it.pts.isNotEmpty() }
        if (letters.isEmpty()) return emptyList()
        // حروف الكلمة الواحدة (نفس الرقم)، مع فصل نسختين منها إن تباعدتا (كلمة مكررة بالنسخ)
        val boxes = ArrayList<Box>()
        for ((_, group) in letters.groupBy { it.wid }) {
            val items = group.map { k ->
                val bb = InkFill.bounds(k.pts)
                val em = InkFill.lineOf(k.pts, k.lineF, k.emF)?.em ?: (bb[3] - bb[1])
                Triple(k, bb, em)
            }.sortedBy { it.second[0] }
            var cur: Box? = null
            for ((k, bb, em) in items) {
                val c = cur
                val near = c != null && bb[0] - c.r < 1.5f * c.em &&
                    abs((bb[1] + bb[3]) / 2f - (c.t + c.b) / 2f) < maxOf(c.em, em)
                if (c != null && near) {
                    c.l = minOf(c.l, bb[0]); c.t = minOf(c.t, bb[1]); c.r = maxOf(c.r, bb[2]); c.b = maxOf(c.b, bb[3])
                } else {
                    cur = Box(k.word, bb[0], bb[1], bb[2], bb[3], em).also { boxes.add(it) }
                }
            }
        }
        // أسطر: كلمات تتداخل عمودياً بأكثر من نصف ارتفاع أصغرها
        val lines = ArrayList<MutableList<Box>>()
        for (bx in boxes.sortedBy { (it.t + it.b) / 2f }) {
            val line = lines.lastOrNull()
            val fits = line != null && line.any { o ->
                val overlap = minOf(o.b, bx.b) - maxOf(o.t, bx.t)
                overlap > 0.5f * minOf(o.b - o.t, bx.b - bx.t)
            }
            if (fits) line!!.add(bx) else lines.add(mutableListOf(bx))
        }
        val out = ArrayList<OcrWord>()
        lines.forEachIndexed { i, line ->
            val rtl = line.count { b -> b.text.any { it in '؀'..'ۿ' } } * 2 > line.size
            val ordered = if (rtl) line.sortedByDescending { it.r } else line.sortedBy { it.l }
            for (b in ordered) out.add(OcrWord(b.text, b.l, b.t, b.r, b.b, firstLine + i))
        }
        return out
    }
}
