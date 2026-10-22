package com.mynotes.app

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * تحويل الرسم باليد إلى أشكال مرتبة عند رفع القلم: خط مستقيم، سهم، مثلث، مستطيل، دائرة/بيضاوي.
 * إن لم يشبه الخط أي شكل بوضوح يبقى كما رُسم.
 */
object ShapeRecognizer {
    private fun d(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)

    private fun pathLength(p: List<Pt>): Float {
        var s = 0f
        for (i in 1 until p.size) s += d(p[i - 1], p[i])
        return s
    }

    /** بُعد النقطة عن المستقيم المار بـ a و b. */
    private fun perp(p: Pt, a: Pt, b: Pt): Float {
        val len = d(a, b)
        if (len < 1e-6f) return d(p, a)
        return abs((b.x - a.x) * (a.y - p.y) - (a.x - p.x) * (b.y - a.y)) / len
    }

    /** تبسيط المسار (Ramer–Douglas–Peucker). */
    private fun simplify(p: List<Pt>, eps: Float): List<Pt> {
        if (p.size < 3) return p
        var idx = 0
        var maxD = 0f
        for (i in 1 until p.size - 1) {
            val dd = perp(p[i], p.first(), p.last())
            if (dd > maxD) {
                maxD = dd
                idx = i
            }
        }
        if (maxD <= eps) return listOf(p.first(), p.last())
        val left = simplify(p.subList(0, idx + 1), eps)
        val right = simplify(p.subList(idx, p.size), eps)
        return left.dropLast(1) + right
    }

    private fun isStraight(p: List<Pt>): Boolean {
        if (p.size < 2) return false
        val a = p.first()
        val b = p.last()
        val len = d(a, b)
        if (len < 1e-4f) return false
        val maxDev = p.maxOf { perp(it, a, b) }
        return maxDev < 0.06f * len && pathLength(p) < 1.18f * len
    }

    /** نقاط كثيفة على أضلاع مضلع، مع تكرار الرؤوس حتى تبقى الزوايا حادة بعد التنعيم. */
    private fun densify(corners: List<Pt>, step: Float): List<Pt> {
        val out = ArrayList<Pt>()
        for (i in 1 until corners.size) {
            val a = corners[i - 1]
            val b = corners[i]
            val n = (d(a, b) / step).toInt().coerceIn(1, 400)
            out.add(a)
            out.add(a)
            for (k in 1 until n) {
                val f = k.toFloat() / n
                out.add(Pt(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f, 0.5f))
            }
        }
        out.add(corners.last())
        out.add(corners.last())
        return out.map { Pt(it.x, it.y, 0.5f) }
    }

    fun recognize(pts: List<Pt>): List<Pt>? {
        if (pts.size < 6) return null
        val minX = pts.minOf { it.x }
        val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }
        val maxY = pts.maxOf { it.y }
        val diag = hypot(maxX - minX, maxY - minY)
        // حجم أدنى (6% من عرض الصفحة) حتى لا تتحول الحروف المكتوبة باليد مثل o و l إلى أشكال
        if (diag < 0.06f) return null
        val step = diag / 60f
        val total = pathLength(pts)
        val first = pts.first()
        val last = pts.last()
        val closed = d(first, last) < 0.2f * diag && total > 2.2f * diag

        if (!closed) {
            // خط مستقيم
            if (isStraight(pts)) return densify(listOf(first, last), step)
            // سهم: جزء مستقيم حتى أبعد نقطة عن البداية، ثم رأس قصير
            val k = pts.indices.maxByOrNull { d(pts[it], first) } ?: return null
            if (k in 4 until pts.size - 2) {
                val shaft = pts.subList(0, k + 1)
                val head = pts.subList(k, pts.size)
                val main = d(first, pts[k])
                val headLen = pathLength(head)
                if (isStraight(shaft) && headLen > 0.08f * main && headLen < 0.9f * main) {
                    val tip = pts[k]
                    val ang = atan2(tip.y - first.y, tip.x - first.x)
                    val h = (0.18f * main).coerceAtMost(0.05f)
                    val a1 = ang + Math.PI.toFloat() * (1f - 0.16f)
                    val a2 = ang - Math.PI.toFloat() * (1f - 0.16f)
                    val h1 = Pt(tip.x + h * cos(a1), tip.y + h * sin(a1), 0.5f)
                    val h2 = Pt(tip.x + h * cos(a2), tip.y + h * sin(a2), 0.5f)
                    return densify(listOf(first, tip, h1, tip, h2), step)
                }
            }
            return null
        }

        // أشكال مغلقة
        var poly = simplify(pts, 0.07f * diag)
        if (poly.size >= 2 && d(poly.first(), poly.last()) < 0.2f * diag) poly = poly.dropLast(1)
        // رؤوس زائدة على ضلع مستقيم (من التبسيط) تُحذف
        var changed = true
        while (changed && poly.size > 3) {
            changed = false
            for (i in poly.indices) {
                val a = poly[(i - 1 + poly.size) % poly.size]
                val c = poly[(i + 1) % poly.size]
                if (perp(poly[i], a, c) < 0.07f * diag) {
                    poly = poly.filterIndexed { j, _ -> j != i }
                    changed = true
                    break
                }
            }
        }
        if (poly.size == 3) return densify(poly + poly.first(), step)
        if (poly.size == 4) {
            val right = (0 until 4).all { i ->
                val a = poly[(i + 3) % 4]
                val b = poly[i]
                val c = poly[(i + 1) % 4]
                val v1x = a.x - b.x
                val v1y = a.y - b.y
                val v2x = c.x - b.x
                val v2y = c.y - b.y
                val cosA = (v1x * v2x + v1y * v2y) / (hypot(v1x, v1y) * hypot(v2x, v2y)).coerceAtLeast(1e-6f)
                abs(cosA) < 0.35f
            }
            if (right) {
                val axisAligned = (0 until 4).all { i ->
                    val a = poly[i]
                    val b = poly[(i + 1) % 4]
                    val ang = Math.toDegrees(atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())).let { ((it % 90) + 90) % 90 }
                    ang < 12 || ang > 78
                }
                val rect = if (axisAligned) listOf(
                    Pt(minX, minY, 0.5f), Pt(maxX, minY, 0.5f), Pt(maxX, maxY, 0.5f), Pt(minX, maxY, 0.5f)
                ) else poly
                return densify(rect + rect.first(), step)
            }
        }
        // دائرة أو بيضاوي: المسافة النسبية عن المركز ثابتة تقريبًا
        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f
        val rx = ((maxX - minX) / 2f).coerceAtLeast(1e-4f)
        val ry = ((maxY - minY) / 2f).coerceAtLeast(1e-4f)
        val radii = pts.map { sqrt(((it.x - cx) / rx).let { v -> v * v } + ((it.y - cy) / ry).let { v -> v * v }) }
        val mean = radii.average().toFloat()
        val sd = sqrt(radii.map { (it - mean) * (it - mean) }.average()).toFloat()
        if (sd < 0.13f * mean) {
            val n = 96
            return (0..n).map { i ->
                val t = (2.0 * Math.PI * i / n).toFloat()
                Pt(cx + rx * cos(t), cy + ry * sin(t), 0.5f)
            }
        }
        return null
    }
}
