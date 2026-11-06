package com.hamishnotes.app

import android.graphics.Path

/**
 * المسح الجزئي للحروف المملوءة (تجميل الخط): تُطرح دائرة الممحاة من شكل الحرف بعمليات الأشكال
 * المدمجة في أندرويد (Path.op، نفس محرك الرسم)، فيبقى ما لم تمر عليه الممحاة بحدود ناعمة.
 * الأجزاء التي تنفصل بعد القص تصير قطعاً مستقلة.
 */
object InkCut {
    /** الحسابات بوحدات مكبّرة: إحداثيات الصفحة صغيرة جداً (0..1) لدقة عمليات الأشكال. */
    private const val K = 10000f
    /** أضلاع دائرة الممحاة: كافية لحافة دائرية ناعمة. */
    private const val SIDES = 40

    private fun pathOf(pts: List<Pt>): Path {
        val p = Path()
        p.fillType = Path.FillType.WINDING
        for (c in InkFill.contours(pts)) {
            if (c.size < 3) continue
            p.moveTo(c[0].x * K, c[0].y * K)
            for (i in 1 until c.size) p.lineTo(c[i].x * K, c[i].y * K)
            p.close()
        }
        return p
    }

    private fun circle(x: Float, y: Float, r: Float): Path {
        val p = Path()
        for (i in 0 until SIDES) {
            val a = 2.0 * Math.PI * i / SIDES
            val px = (x + r * kotlin.math.cos(a)).toFloat() * K
            val py = (y + r * kotlin.math.sin(a)).toFloat() * K
            if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
        }
        p.close()
        return p
    }

    /**
     * يقص دائرة الممحاة من الحرف. null = الممحاة لا تلمس الحرف (يبقى كما هو).
     * قائمة فارغة = مُسح الحرف كله.
     */
    fun cut(s: Ink, x: Float, y: Float, radius: Float): List<Ink>? {
        if (!InkFill.hit(s.pts, x, y, radius, s.width)) return null
        val shape = pathOf(s.pts)
        // التعريض حول الحرف (خط أعرض) يُرسم فوق الحواف: تُكبَّر الدائرة بقدره حتى يظهر القص بحجم الممحاة
        val hole = circle(x, y, radius + s.width / 2f)
        val result = Path()
        if (!result.op(shape, hole, Path.Op.DIFFERENCE)) return null
        if (result.isEmpty) return emptyList()
        // حدود الناتج: approximate يعطي (موضع، x، y)، وتكرار الموضع = بداية حد جديد
        val approx = result.approximate(0.02f * K / 1000f)
        val contours = InkFill.splitApproximation(approx).map { c ->
            FloatArray(c.size) { i -> c[i] / K }
        }.map { InkFill.simplify(it, 0.00003f) }.filter { it.size >= 6 && kotlin.math.abs(InkFill.area(it)) > 1e-9f }
        if (contours.isEmpty()) return emptyList()
        return InkFill.group(contours).map { g -> s.copy(pts = InkFill.encode(g.map { contours[it] })) }
    }
}
