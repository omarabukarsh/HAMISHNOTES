package com.hamishnotes.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface

/**
 * كلمة بخط من المكتبة على شكل خطوط قلم (لا نص): تُرسم الكلمة بالخط، ثم تُتبَّع حروفها إلى خطوط
 * تمر في وسطها. الإحداثيات بوحدة حجم الخط (1 = حجم الخط)، x من بداية الكلمة و y من خط القاعدة (موجب للأسفل).
 */
object GlyphInk {
    /** حجم الرسم بالبكسل لكل وحدة: دقة كافية لحروف ناعمة حتى بالأحجام الكبيرة. */
    private const val PX = 160f

    class Word(
        /** كل خط: نقاط x,y وسماكة الحرف عند النقطة (بوحدة حجم الخط). */
        val strokes: List<GlyphSkeleton.Polyline>,
        /** متوسط سماكة حروف الخط. */
        val thickness: Float,
        /** حدود الحبر الفعلي للكلمة. */
        val left: Float, val top: Float, val right: Float, val bottom: Float,
        /** عرض المسافة بين كلمتين بهذا الخط. */
        val space: Float
    )

    private val cache = object : LinkedHashMap<String, Word>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Word>?) = size > 200
    }

    fun paint(tf: Typeface?): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = PX
        color = android.graphics.Color.BLACK
        if (tf != null) typeface = tf
    }

    /** حدود حبر نص بوحدة حجم الخط (يسار، أعلى، يمين، أسفل) من نقطة بدايته على خط القاعدة. */
    fun bounds(text: String, tf: Typeface?): FloatArray {
        val p = paint(tf)
        val r = Rect()
        p.getTextBounds(text, 0, text.length, r)
        if (r.isEmpty) return floatArrayOf(0f, -0.7f, p.measureText(text) / PX, 0f)
        return floatArrayOf(r.left / PX, r.top / PX, r.right / PX, r.bottom / PX)
    }

    fun word(text: String, fontName: String, tf: Typeface?): Word? {
        if (text.isBlank()) return null
        val key = "$fontName\u0000$text"
        synchronized(cache) { cache[key]?.let { return it } }
        val p = paint(tf)
        val r = Rect()
        p.getTextBounds(text, 0, text.length, r)
        if (r.isEmpty) return null
        val pad = 6
        val w = r.width() + pad * 2
        val h = r.height() + pad * 2
        if (w <= 0 || h <= 0 || w.toLong() * h > 6_000_000L) return null
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val ox = (pad - r.left).toFloat()
        val oy = (pad - r.top).toFloat()
        Canvas(bmp).drawText(text, ox, oy, p)
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        bmp.recycle()
        val mask = BooleanArray(w * h) { (px[it] ushr 24) > 110 }
        val res = GlyphSkeleton.trace(mask, w, h)
        val lines = res.lines.map { l ->
            GlyphSkeleton.Polyline(
                FloatArray(l.size) { (l.xs[it] - ox) / PX },
                FloatArray(l.size) { (l.ys[it] - oy) / PX },
                FloatArray(l.size) { l.thick[it] / PX }
            )
        }
        val out = Word(
            lines, res.thickness / PX,
            r.left / PX, r.top / PX, r.right / PX, r.bottom / PX,
            p.measureText(" ") / PX
        )
        synchronized(cache) { cache[key] = out }
        return out
    }
}
