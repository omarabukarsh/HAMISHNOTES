package com.hamishnotes.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** طبقات الرسم (الهايلايتر وملء الأشكال تحت النص) وكشف الكتابة الإنجليزية المقروءة بالعربية. */
class LayersTest {
    private fun ink(tool: Tool, shape: Boolean = false, fill: Boolean = false) =
        Ink(listOf(Pt(0f, 0f, 1f), Pt(1f, 0f, 1f), Pt(1f, 1f, 1f), Pt(0f, 0f, 1f)), Color.Black, 0.01f, tool, shape = shape, shapeFill = fill)

    @Test
    fun highlighterGoesUnder_penStaysOver_filledShapeIsInBoth() {
        val hl = ink(Tool.HIGHLIGHTER)
        assertTrue(hl.inUnderlay()); assertFalse(hl.inOverlay())
        val pen = ink(Tool.BALLPOINT)
        assertFalse(pen.inUnderlay()); assertTrue(pen.inOverlay())
        // الشكل المملوء: ملؤه تحت الكتابة وإطاره فوقها
        val box = ink(Tool.BALLPOINT, shape = true, fill = true)
        assertTrue(box.inUnderlay()); assertTrue(box.inOverlay())
    }

    @Test
    fun latinReading_isDetected() {
        assertTrue(HandwritingLayout.latinOnly("arom"))
        assertTrue(HandwritingLayout.latinOnly("Omar 2"))
        assertFalse(HandwritingLayout.latinOnly("عمر"))
        assertFalse(HandwritingLayout.latinOnly("Omar عمر"))
        assertFalse(HandwritingLayout.latinOnly("123"))
        assertFalse(HandwritingLayout.latinOnly(null))
    }

    @Test
    fun onlyClosedShapesCanBeFilled() {
        assertTrue(ShapeKind.RECT.closed()); assertTrue(ShapeKind.ELLIPSE.closed())
        assertFalse(ShapeKind.LINE.closed()); assertFalse(ShapeKind.CURVE.closed())
    }
}
