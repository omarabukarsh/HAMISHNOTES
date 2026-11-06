package com.hamishnotes.app

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Typeface

/**
 * سطر بخط من المكتبة على شكل حروف مملوءة (لا نص): شكل كل حرف يؤخذ من الخط نفسه كما يرسمه،
 * فيبقى ناعماً بسماكته الحقيقية مهما كُبّر. الإحداثيات بوحدة حجم الخط (1 = حجم الخط)،
 * x من بداية السطر و y من خط القاعدة (موجب للأسفل).
 */
object GlyphInk {
    /** حجم القياس بالبكسل لكل وحدة (الحدود تُؤخذ كمنحنيات ثم تُقسَّم بدقة أقل من ربع بكسل). */
    private const val PX = 160f

    class Line(
        /** القطع: كل قطعة حرف (أو حروف متصلة) = عدة حدود، كل حد قائمة x,y. */
        val pieces: List<List<FloatArray>>,
        /** حدود الحبر الفعلي للسطر. */
        val left: Float, val top: Float, val right: Float, val bottom: Float,
        /** عرض المسافة بين كلمتين بهذا الخط. */
        val space: Float,
        /** كلمات السطر وامتدادها الأفقي (يسار، يمين) بوحدة الحجم: لربط كل حرف بكلمته. */
        val words: List<Triple<String, Float, Float>> = emptyList()
    )

    private val cache = object : LinkedHashMap<String, Line>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Line>?) = size > 100
    }

    fun paint(tf: Typeface?): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = PX
        if (tf != null) typeface = tf
    }

    /** حدود حبر نص بوحدة حجم الخط (يسار، أعلى، يمين، أسفل) من نقطة بدايته على خط القاعدة. */
    fun bounds(text: String, tf: Typeface?): FloatArray {
        val p = paint(tf)
        val r = android.graphics.Rect()
        p.getTextBounds(text, 0, text.length, r)
        if (r.isEmpty) return floatArrayOf(0f, -0.7f, p.measureText(text) / PX, 0f)
        return floatArrayOf(r.left / PX, r.top / PX, r.right / PX, r.bottom / PX)
    }

    /** شكل السطر حروفاً مملوءة (null لنص فارغ). */
    fun line(text: String, fontName: String, tf: Typeface?): Line? {
        if (text.isBlank()) return null
        val key = "$fontName\u0000$text"
        synchronized(cache) { cache[key]?.let { return it } }
        val p = paint(tf)
        val path = Path()
        // getTextPath يشكّل النص (العربي متصلاً) ويرتّبه كما يُرسم
        p.getTextPath(text, 0, text.length, 0f, 0f, path)
        val box = RectF()
        path.computeBounds(box, true)
        if (box.isEmpty) return null
        val contours = ArrayList<FloatArray>()
        val pm = PathMeasure(path, false)
        val pos = FloatArray(2)
        do {
            val len = pm.length
            if (len <= 0.5f) continue
            // عيّنة كل ربع بكسل تقريباً ثم تبسيط: منحنيات الحرف تبقى ناعمة
            val n = (len / 0.25f).toInt().coerceIn(8, 20000)
            val raw = FloatArray(n * 2)
            for (i in 0 until n) {
                pm.getPosTan(len * i / n, pos, null)
                raw[i * 2] = pos[0] / PX
                raw[i * 2 + 1] = pos[1] / PX
            }
            val simple = InkFill.simplify(raw, 0.15f / PX)
            if (simple.size >= 6) contours.add(simple)
        } while (pm.nextContour())
        if (contours.isEmpty()) return null
        val groups = InkFill.group(contours)
        // حدود الحروف كما يضعها الخط (مع التشكيل والاتصال والاتجاه): لقص الحروف المتصلة حرفاً حرفاً
        val rtl = text.any { it in '\u0590'..'\u08FF' }
        val total = p.measureText(text)
        val cuts = ArrayList<Float>()
        for (i in 1 until text.length) {
            val a = text[i - 1]
            val b = text[i]
            if (a.isWhitespace() || b.isWhitespace()) continue
            // لا قص داخل الحرف الواحد (تشكيل، أو أزواج الحروف الخاصة)
            if (Character.getType(b) == Character.NON_SPACING_MARK.toInt() || Character.isLowSurrogate(b)) continue
            val adv = p.getRunAdvance(text, 0, text.length, 0, text.length, rtl, i)
            cuts.add((if (rtl) total - adv else adv) / PX)
        }
        val avg = if (text.isNotEmpty()) total / PX / text.length else 0.5f
        val pieces = groups.flatMap { g -> InkFill.slice(g.map { contours[it] }, cuts, avg * 0.25f, 0.6f / PX) }
        // امتداد كل كلمة كما يضعها الخط
        val words = ArrayList<Triple<String, Float, Float>>()
        Regex("\\S+").findAll(text).forEach { m ->
            val a = p.getRunAdvance(text, 0, text.length, 0, text.length, rtl, m.range.first)
            val b = p.getRunAdvance(text, 0, text.length, 0, text.length, rtl, m.range.last + 1)
            val xa = (if (rtl) total - a else a) / PX
            val xb = (if (rtl) total - b else b) / PX
            words.add(Triple(m.value, minOf(xa, xb), maxOf(xa, xb)))
        }
        val out = Line(
            pieces,
            box.left / PX, box.top / PX, box.right / PX, box.bottom / PX,
            p.measureText(" ") / PX,
            words
        )
        synchronized(cache) { cache[key] = out }
        return out
    }
}
