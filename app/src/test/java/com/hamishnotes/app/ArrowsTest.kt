package com.hamishnotes.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** رؤوس الأسهم: المغلق مثلث مغلق والخط يتوقف عند قاعدته، والرأس حاد ويتجه مع آخر جزء من الخط. */
class ArrowsTest {
    private val a = Pt(0.1f, 0.5f, 0.5f)
    private val b = Pt(0.6f, 0.2f, 0.5f)

    @Test
    fun closedHead_isATriangle_andTheShaftStopsAtItsBase() {
        val parts = ShapeBuilder.build(ShapeKind.LINE, Pt(0.1f, 0.5f, 0.5f), Pt(0.6f, 0.5f, 0.5f), true, ArrowHead.NONE, ArrowHead.CLOSED, 0.003f)
        assertEquals(2, parts.size)
        val head = parts[1]
        assertEquals(4, head.size) // ثلاث زوايا ثم العودة للأولى
        assertTrue(isClosedShape(head))
        // الرأس حاد عند نهاية الخط تماماً
        assertEquals(0.6f, head[1].x, 1e-6f)
        // الخط يتوقف قبل الرأس عند قاعدة المثلث
        val shaftEnd = parts[0].last().x
        val base = (head[0].x + head[2].x) / 2f
        assertEquals(base, shaftEnd, 1e-4f)
    }

    @Test
    fun openHead_onARoundedElbow_pointsAlongTheLastSegment() {
        val parts = ShapeBuilder.build(ShapeKind.ELBOW, a, b, true, ArrowHead.OPEN, ArrowHead.OPEN, 0.003f)
        // المرفق أفقي ثم رأسي: رأس النهاية يتجه للأعلى (الجناحان تحت الرأس بنفس البعد)
        val end = parts[1]
        assertEquals(3, end.size)
        assertEquals(end[0].y, end[2].y, 1e-5f)
        assertTrue(end[0].y > end[1].y)
        // رأس البداية يتجه لليسار (الجناحان على يمين الرأس بنفس البعد)
        val start = parts[2]
        assertEquals(start[0].x, start[2].x, 1e-5f)
        assertTrue(start[0].x > start[1].x)
    }
}
