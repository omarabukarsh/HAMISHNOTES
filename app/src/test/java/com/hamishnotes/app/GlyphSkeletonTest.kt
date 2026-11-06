package com.hamishnotes.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * أشكال الحروف تتحول إلى خطوط قلم تغطي الشكل نفسه: يُرسم شكل سميك، ثم تُرسم الخطوط الناتجة
 * بسماكتها، ويجب أن تطابق الصورتان (بلا رسم خارج الشكل).
 */
class GlyphSkeletonTest {

    private class Seg(val x0: Float, val y0: Float, val x1: Float, val y1: Float)

    private fun distToSeg(px: Float, py: Float, s: Seg): Float {
        val dx = s.x1 - s.x0
        val dy = s.y1 - s.y0
        val l2 = dx * dx + dy * dy
        val t = if (l2 == 0f) 0f else (((px - s.x0) * dx + (py - s.y0) * dy) / l2).coerceIn(0f, 1f)
        return kotlin.math.hypot(px - (s.x0 + t * dx), py - (s.y0 + t * dy))
    }

    /** شكل من خطوط مستقيمة بسماكة thick. */
    private fun shape(w: Int, h: Int, thick: Float, segs: List<Seg>) = BooleanArray(w * h) { i ->
        val x = i % w + 0.5f
        val y = i / w + 0.5f
        segs.any { distToSeg(x, y, it) <= thick / 2f }
    }

    /** الخطوط الناتجة مرسومة بسماكة الحرف عند كل نقطة. */
    private fun redraw(res: GlyphSkeleton.Result, w: Int, h: Int): BooleanArray {
        val out = BooleanArray(w * h)
        for (l in res.lines) for (i in 0 until l.size) {
            val j = (i + 1).coerceAtMost(l.size - 1)
            val s = Seg(l.xs[i], l.ys[i], l.xs[j], l.ys[j])
            val r = (l.thick[i] + l.thick[j]) / 4f
            val x0 = (minOf(s.x0, s.x1) - r - 1).toInt().coerceAtLeast(0)
            val x1 = (maxOf(s.x0, s.x1) + r + 1).toInt().coerceAtMost(w - 1)
            val y0 = (minOf(s.y0, s.y1) - r - 1).toInt().coerceAtLeast(0)
            val y1 = (maxOf(s.y0, s.y1) + r + 1).toInt().coerceAtMost(h - 1)
            for (y in y0..y1) for (x in x0..x1) if (distToSeg(x + 0.5f, y + 0.5f, s) <= r) out[y * w + x] = true
        }
        return out
    }

    /** تغطية الشكل ودقة الرسم مع سماحية بكسل واحد. */
    private fun score(src: BooleanArray, d: BooleanArray, w: Int, h: Int): Pair<Float, Float> {
        fun near(a: BooleanArray, x: Int, y: Int): Boolean {
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx
                val ny = y + dy
                if (nx in 0 until w && ny in 0 until h && a[ny * w + nx]) return true
            }
            return false
        }
        var a = 0; var ac = 0; var b = 0; var bc = 0
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (src[i]) { a++; if (near(d, x, y)) ac++ }
            if (d[i]) { b++; if (near(src, x, y)) bc++ }
        }
        return ac.toFloat() / a to bc.toFloat() / b.coerceAtLeast(1)
    }

    private fun check(name: String, w: Int, h: Int, thick: Float, segs: List<Seg>, maxStrokes: Int, minRecall: Float = 0.95f) {
        val src = shape(w, h, thick, segs)
        val res = GlyphSkeleton.trace(src, w, h)
        val (recall, precision) = score(src, redraw(res, w, h), w, h)
        assertTrue("$name: ${res.lines.size} strokes", res.lines.size in 1..maxStrokes)
        assertTrue("$name: shape not covered ($recall)", recall >= minRecall)
        assertTrue("$name: drawn outside the shape ($precision)", precision >= 0.95f)
        assertEquals("$name: thickness", thick, res.thickness, thick * 0.3f)
    }

    @Test fun letterT() = check("T", 120, 140, 12f, listOf(Seg(15f, 20f, 105f, 20f), Seg(60f, 20f, 60f, 125f)), 3)
    @Test fun letterL() = check("L", 100, 140, 10f, listOf(Seg(25f, 15f, 25f, 120f), Seg(25f, 120f, 85f, 120f)), 2)
    @Test fun letterX() = check("X", 130, 130, 11f, listOf(Seg(15f, 15f, 115f, 115f), Seg(115f, 15f, 15f, 115f)), 4)
    // زوايا M حادة جداً: قلم دائري لا يصل إلى رأس الزاوية الخارجي تماماً (أجزاء صغيرة عند الرؤوس الثلاثة)
    @Test fun letterM() = check(
        "M", 160, 130, 9f,
        listOf(Seg(20f, 115f, 30f, 15f), Seg(30f, 15f, 80f, 90f), Seg(80f, 90f, 130f, 15f), Seg(130f, 15f, 140f, 115f)), 5, minRecall = 0.94f)

    @Test
    fun straightBar_isOneStrokeWithBarThickness() {
        val w = 80
        val h = 30
        val mask = BooleanArray(w * h) { val x = it % w; val y = it / w; x in 10..69 && y in 10..19 }
        val res = GlyphSkeleton.trace(mask, w, h)
        assertEquals(1, res.lines.size)
        val l = res.lines[0]
        assertTrue(l.ys.all { it in 13f..17f })
        assertTrue("thickness ${res.thickness}", res.thickness in 8f..12f)
        // يمتد قرب طرفي الشريط
        assertTrue(l.xs.minOrNull()!! < 16f && l.xs.maxOrNull()!! > 64f)
    }

    @Test
    fun ring_isOneClosedStroke() {
        val w = 60
        val h = 60
        val mask = BooleanArray(w * h) {
            val dx = it % w - 30f
            val dy = it / w - 30f
            kotlin.math.sqrt(dx * dx + dy * dy) in 15f..21f
        }
        val res = GlyphSkeleton.trace(mask, w, h)
        assertEquals(1, res.lines.size)
        val l = res.lines[0]
        assertTrue(kotlin.math.hypot(l.xs.first() - l.xs.last(), l.ys.first() - l.ys.last()) < 2f)
    }

    @Test
    fun dot_isDrawable() {
        val w = 20
        val h = 20
        val mask = BooleanArray(w * h) { val x = it % w; val y = it / w; x in 8..11 && y in 8..11 }
        val res = GlyphSkeleton.trace(mask, w, h)
        assertEquals(1, res.lines.size)
        assertTrue(res.lines[0].size >= 2)
    }

    @Test
    fun crossing_isTwoContinuousStrokes() {
        // عند التقاء خطين لا ينكسر كل منهما: X = خطان متصلان لا أربع قطع
        val w = 130
        val h = 130
        val src = shape(w, h, 11f, listOf(Seg(15f, 15f, 115f, 115f), Seg(115f, 15f, 15f, 115f)))
        val res = GlyphSkeleton.trace(src, w, h)
        assertEquals(2, res.lines.size)
    }

    @Test
    fun curves_areSmooth_noPolygonCorners() {
        // دائرة كبيرة: لا انعطاف حاد بين قطعتين متتاليتين (بلا تكسير)
        val w = 200
        val h = 200
        val mask = BooleanArray(w * h) {
            val dx = it % w - 100f
            val dy = it / w - 100f
            kotlin.math.sqrt(dx * dx + dy * dy) in 70f..80f
        }
        val l = GlyphSkeleton.trace(mask, w, h).lines.single()
        var worst = 0.0
        for (i in 1 until l.size - 1) {
            val ax = (l.xs[i] - l.xs[i - 1]).toDouble(); val ay = (l.ys[i] - l.ys[i - 1]).toDouble()
            val bx = (l.xs[i + 1] - l.xs[i]).toDouble(); val by = (l.ys[i + 1] - l.ys[i]).toDouble()
            val la = kotlin.math.hypot(ax, ay); val lb = kotlin.math.hypot(bx, by)
            if (la < 1e-6 || lb < 1e-6) continue
            val ang = Math.toDegrees(kotlin.math.acos(((ax * bx + ay * by) / (la * lb)).coerceIn(-1.0, 1.0)))
            if (ang > worst) worst = ang
        }
        assertTrue("sharpest turn on a circle: $worst°", worst < 12.0)
    }
}
