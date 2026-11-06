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

    // ------------------------------------------------ المتابعة على نفس السطر

    @Test
    fun baselineAndSize_surviveMovingAndScalingTheLetter() {
        val pts = InkFill.encode(listOf(rect(0.10f, 0.20f, 0.14f, 0.25f)))
        val (lf, ef) = InkFill.fractions(pts, base = 0.24f, em = 0.06f)
        val ref = InkFill.lineOf(pts, lf, ef)!!
        assertEquals(0.24f, ref.base, 1e-5f)
        assertEquals(0.06f, ref.em, 1e-5f)
        // تحريك وتكبير ×2 حول نقطة: خط القاعدة والحجم يتحركان ويكبران معه
        val moved = pts.map { Pt(0.5f + (it.x - 0.10f) * 2f, 0.6f + (it.y - 0.20f) * 2f, it.p) }
        val ref2 = InkFill.lineOf(moved, lf, ef)!!
        assertEquals(0.6f + (0.24f - 0.20f) * 2f, ref2.base, 1e-5f)
        assertEquals(0.12f, ref2.em, 1e-5f)
        // حرف قديم بلا معلومات سطر
        assertEquals(null, InkFill.lineOf(pts, -1f, 0f))
    }

    @Test
    fun newWriting_continuesTheNearbyOldLine() {
        // جملة قديمة: خط قاعدتها 0.30 وحجمها 0.05، من x=0.10 إلى 0.40
        val old = listOf(
            InkFill.LineRef(0.300f, 0.05f, 0.10f, 0.18f),
            InkFill.LineRef(0.301f, 0.05f, 0.20f, 0.30f),
            InkFill.LineRef(0.299f, 0.05f, 0.32f, 0.40f)
        )
        // كتابة جديدة بعدها مباشرة وأسفل قليلاً (0.315) وبحجم آخر
        val hit = InkFill.nearestLine(old, 0.43f, 0.60f, base = 0.315f, size = 0.07f)!!
        assertEquals(0.300f, hit.base, 1e-5f)
        assertEquals(0.05f, hit.em, 1e-5f)
        // كتابة على السطر التالي (أبعد من نصف السطر): لا تُلحق بالقديم
        assertEquals(null, InkFill.nearestLine(old, 0.43f, 0.60f, base = 0.37f, size = 0.05f))
        // على نفس الارتفاع لكن بعيدة جداً أفقياً: لا تُلحق
        assertEquals(null, InkFill.nearestLine(old, 0.80f, 0.95f, base = 0.30f, size = 0.05f))
    }

    @Test
    fun writingBetweenOrUnderOldLines_staysOnItsOwn() {
        // سطر قديم كبير: قاعدته 0.30 وحجمه 0.10، من 0.10 إلى 0.60 (كما في «Ahmed» الكبيرة)
        val old = listOf(
            InkFill.LineRef(0.30f, 0.10f, 0.10f, 0.25f),
            InkFill.LineRef(0.30f, 0.10f, 0.27f, 0.45f),
            InkFill.LineRef(0.30f, 0.10f, 0.47f, 0.60f)
        )
        // كتابة صغيرة تحت السطر وبين السطرين: لا تُسحب إليه ولا تكبر لحجمه
        assertEquals(null, InkFill.nearestLine(old, 0.30f, 0.45f, base = 0.36f, size = 0.03f))
        // على نفس الارتفاع لكن فوق حروف السطر نفسه: ليست متابعة
        assertEquals(null, InkFill.nearestLine(old, 0.20f, 0.40f, base = 0.30f, size = 0.10f))
        // بجانبه وأصغر منه بوضوح: تلتحق وتأخذ حجمه
        val small = InkFill.nearestLine(old, 0.62f, 0.70f, base = 0.31f, size = 0.04f)!!
        assertEquals(0.30f, small.base, 1e-6f)
        assertEquals(0.10f, small.em, 1e-6f)
        // بجانبه وبحجم قريب: تلتحق
        assertEquals(0.30f, InkFill.nearestLine(old, 0.63f, 0.80f, base = 0.31f, size = 0.08f)!!.base, 1e-6f)
    }

    @Test
    fun cutPieces_keepTheirHolesEmpty_whateverTheDirectionTheyCameIn() {
        // ناتج القص قد يعطي الحد الداخلي بنفس اتجاه الخارجي: الفراغ يمتلئ لولا التوجيه
        val same = listOf(circle(0.5f, 0.5f, 0.4f, true), circle(0.5f, 0.5f, 0.25f, true))
        assertTrue(InkFill.winding(0.5f, 0.5f, InkFill.contours(InkFill.encode(same))) != 0)
        val fixed = InkFill.contours(InkFill.encode(InkFill.orient(same)))
        assertEquals(0, InkFill.winding(0.5f, 0.5f, fixed)) // الفراغ فارغ
        assertTrue(InkFill.winding(0.82f, 0.5f, fixed) != 0) // الجسم مملوء
        // جزيرة داخل الفراغ تبقى مملوءة
        val island = InkFill.orient(same + listOf(circle(0.5f, 0.5f, 0.1f, false)))
        assertTrue(InkFill.winding(0.5f, 0.5f, InkFill.contours(InkFill.encode(island))) != 0)
    }

    @Test
    fun writingInAGapBetweenWords_staysInPlace() {
        // كلمتان على سطر: 0.10-0.30 ثم 0.45-0.65، والكتابة في الفراغ بينهما
        val old = listOf(InkFill.LineRef(0.30f, 0.05f, 0.10f, 0.30f), InkFill.LineRef(0.30f, 0.05f, 0.45f, 0.65f))
        assertEquals(null, InkFill.nearestLine(old, 0.33f, 0.42f, base = 0.30f, size = 0.05f))
        // بعد آخر كلمة: تلتحق
        assertTrue(InkFill.nearestLine(old, 0.68f, 0.75f, base = 0.30f, size = 0.05f) != null)
    }

    @Test
    fun joinedWriting_sizeAndStart() {
        // حجم قريب: حجم السطر؛ أصغر أو أكبر بوضوح: حجمها
        assertEquals(0.10f, AutoFont.joinedSize(0.09f, 0.10f, 0.09f), 0f)
        assertEquals(0.10f, AutoFont.joinedSize(0.12f, 0.10f, 0.12f), 0f)
        assertEquals(0.05f, AutoFont.joinedSize(0.05f, 0.10f, 0.05f), 0f)
        assertEquals(0.20f, AutoFont.joinedSize(0.20f, 0.10f, 0.20f), 0f)
        // «بعد آخر كلمة»: إنجليزي بعد السطر يبدأ بعد نهايته بمسافة كلمة
        assertEquals(0.62f, AutoFont.joinedStart(false, 0.70f, 0.80f, 0.10f, 0.60f, width = 0.2f, gap = 0.02f), 1e-6f)
        // عربي قبل السطر (يساره): حافته اليمنى قبل بداية السطر بمسافة كلمة
        assertEquals(0.08f, AutoFont.joinedStart(true, 0.00f, 0.05f, 0.10f, 0.60f, width = 0.2f, gap = 0.02f), 1e-6f)
    }
}
