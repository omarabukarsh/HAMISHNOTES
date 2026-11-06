package com.hamishnotes.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** الحروف المملوءة: الحدود، الفراغ داخل الحرف، الممحاة، وجمع أجزاء الحرف الواحد. */
class InkFillTest {

    /** دائرة كحد مغلق (اتجاهها: موجب = عكس عقارب الساعة على الشاشة). */
    private fun circle(cx: Float, cy: Float, r: Float, ccw: Boolean, n: Int = 64) = FloatArray(n * 2) {
        val k = it / 2
        val a = (if (ccw) -1 else 1) * 2.0 * Math.PI * k / n
        if (it % 2 == 0) (cx + r * kotlin.math.cos(a)).toFloat() else (cy + r * kotlin.math.sin(a)).toFloat()
    }

    private fun rect(x0: Float, y0: Float, x1: Float, y1: Float) = floatArrayOf(x0, y0, x1, y0, x1, y1, x0, y1)

    @Test
    fun encode_thenSplit_keepsEachContour() {
        val a = rect(0f, 0f, 1f, 1f)
        val b = rect(2f, 0f, 3f, 1f)
        val cs = InkFill.contours(InkFill.encode(listOf(a, b)))
        assertEquals(2, cs.size)
        assertEquals(4, cs[0].size)
        assertEquals(2f, cs[1][0].x, 0f)
    }

    @Test
    fun letterO_holeStaysEmpty() {
        // حرف o: حد خارجي بعكس اتجاه الحد الداخلي، فالوسط فارغ بقاعدة nonzero
        val pts = InkFill.encode(listOf(circle(0.5f, 0.5f, 0.4f, true), circle(0.5f, 0.5f, 0.25f, false)))
        val cs = InkFill.contours(pts)
        assertTrue(InkFill.winding(0.5f + 0.32f, 0.5f, cs) != 0) // في جسم الحرف
        assertEquals(0, InkFill.winding(0.5f, 0.5f, cs)) // في الفراغ
        assertEquals(0, InkFill.winding(0.0f, 0.0f, cs)) // خارجه
    }

    @Test
    fun overlappingParts_fillTogether() {
        // أجزاء متراكبة بنفس الاتجاه (خطوط متغيرة السماكة): التداخل يبقى مملوءاً
        val pts = InkFill.encode(listOf(rect(0f, 0f, 2f, 1f), rect(1f, 0f, 3f, 1f)))
        assertTrue(InkFill.winding(1.5f, 0.5f, InkFill.contours(pts)) != 0)
    }

    @Test
    fun eraser_hitsTheLetterOnlyWhenItTouchesIt() {
        val pts = InkFill.encode(listOf(circle(0.5f, 0.5f, 0.4f, true), circle(0.5f, 0.5f, 0.25f, false)))
        assertTrue("inside the stroke of the letter", InkFill.hit(pts, 0.82f, 0.5f, 0.01f))
        assertTrue("touching its edge from outside", InkFill.hit(pts, 0.93f, 0.5f, 0.04f))
        assertFalse("far away", InkFill.hit(pts, 2f, 2f, 0.1f))
        assertFalse("small eraser in the empty middle", InkFill.hit(pts, 0.5f, 0.5f, 0.05f))
        assertTrue("bolder letter reaches further", InkFill.hit(pts, 0.93f, 0.5f, 0.01f, outline = 0.1f))
    }

    @Test
    fun group_holesAndDotsAndSeparateLetters() {
        val outerO = circle(0.5f, 0.5f, 0.4f, true)
        val innerO = circle(0.5f, 0.5f, 0.25f, false)
        val stemI = rect(1.2f, 0.2f, 1.3f, 0.9f)
        val dotI = circle(1.25f, 0.05f, 0.04f, true)
        val l1 = rect(2f, 0f, 2.2f, 0.9f)
        val l2 = rect(2.1f, 0.7f, 2.6f, 0.9f) // يتراكب مع l1 (حرف L من جزأين)
        val g = InkFill.group(listOf(outerO, innerO, stemI, dotI, l1, l2))
        // o (حدان) | جذع i | نقطة i | L (جزآن متراكبان)
        assertEquals(4, g.size)
        assertTrue(g.any { it == listOf(0, 1) })
        assertTrue(g.any { it == listOf(4, 5) })
        assertTrue(g.any { it == listOf(2) })
        assertTrue(g.any { it == listOf(3) })
    }

    @Test
    fun simplify_keepsTheCurveClose() {
        val c = circle(0f, 0f, 1f, true, 2000)
        val s = InkFill.simplify(c, 0.001f)
        assertTrue("fewer points: ${s.size / 2}", s.size < c.size / 4)
        // كل نقطة باقية على الدائرة
        var i = 0
        while (i < s.size) {
            assertEquals(1f, kotlin.math.hypot(s[i], s[i + 1]), 1e-4f)
            i += 2
        }
    }

    @Test
    fun connectedLetters_areSlicedAtLetterBoundaries() {
        // كلمة متصلة = شكل واحد بعرض 3؛ حدود الحروف عند 1 و2 ← ثلاث شرائح، تتداخل قليلاً
        val word = listOf(rect(0f, 0f, 3f, 1f))
        val parts = InkFill.slice(word, listOf(1f, 2f), 0.2f, 0.01f)
        assertEquals(3, parts.size)
        val xs = parts.map { p -> p.flatMap { c -> c.filterIndexed { i, _ -> i % 2 == 0 }.toList() } }
        assertEquals(0f, xs[0].min(), 1e-6f); assertEquals(1.01f, xs[0].max(), 1e-6f)
        assertEquals(0.99f, xs[1].min(), 1e-6f); assertEquals(2.01f, xs[1].max(), 1e-6f)
        assertEquals(1.99f, xs[2].min(), 1e-6f); assertEquals(3f, xs[2].max(), 1e-6f)
        // المساحة الكلية لم تنقص (التداخل فقط يزيدها قليلاً)
        val area = parts.sumOf { p -> p.sumOf { kotlin.math.abs(InkFill.area(it)).toDouble() } }
        assertTrue(area >= 3.0 && area < 3.1)
        // حرف منفصل لا يمر به حد (أو حد قريب من طرفه فقط): لا يُقص
        assertEquals(1, InkFill.slice(listOf(rect(0f, 0f, 1f, 1f)), listOf(0.1f, 5f), 0.2f, 0.01f).size)
    }

    @Test
    fun cutResult_isSplitIntoItsContours() {
        // ناتج approximate لحدين: مربع ثم مثلث (نقطتان بنفس الموضع = قفزة لحد جديد)
        val a = floatArrayOf(
            0f, 0f, 0f, 0.1f, 1f, 0f, 0.2f, 1f, 1f, 0.3f, 0f, 1f, 0.4f, 0f, 0f,
            0.4f, 5f, 5f, 0.6f, 6f, 5f, 0.8f, 5f, 6f, 1f, 5f, 5f
        )
        val cs = InkFill.splitApproximation(a)
        assertEquals(2, cs.size)
        assertEquals(8, cs[0].size) // 4 نقاط بلا نقطة الإغلاق المكررة
        assertEquals(6, cs[1].size)
        assertEquals(5f, cs[1][0], 0f)
    }
}
