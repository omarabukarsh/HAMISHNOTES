package com.mynotes.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** نسخ تحديد من صفحة ولصقه في صفحة أخرى أو مستند آخر، ومحتوى الصور المصغّرة. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CopyPasteTest {
    private fun session(id: String) = DocSession(DocEntry(id, id, DocType.PDF, "$id.pdf", 0, 1, 0, 0, false, 0))
    private val ink = Ink(listOf(Pt(0.1f, 0.1f, 1f), Pt(0.2f, 0.15f, 1f)), Color.Black, 0.004f)
    private val box = TextBox("tb", 0, 0.1f, 0.2f, 0.3f, "مرحبا", Color.Black, 0.03f)

    @Test
    fun copyOnOnePagePasteOnAnother() {
        val a = session("a")
        a.annotations = mapOf(0 to listOf(ink))
        a.texts = mapOf(0 to listOf(box))
        a.lassoSel = LassoSel(0, listOf(ink), emptyList(), listOf("tb"))
        a.lassoCopy()
        assertNotNull(PageClipboard.clip)

        val b = session("b")
        b.pasteClip(1)
        assertEquals(1, b.annotations[1]?.size)
        val t = b.texts[1]!!.single()
        assertEquals("مرحبا", t.text)
        assertEquals(1, t.page)
        assertTrue(t.id != "tb")
        // المنسوخ يصبح محددًا ليُحرَّك
        assertEquals(1, b.lassoSel?.page)
        // الأصل لم يتغير
        assertEquals(listOf(ink), a.annotations[0])
        PageClipboard.clip = null
    }

    @Test
    fun thumbnailLayersIncludeTextBoxesAndNotes() {
        var texts = 0
        var rects = 0
        val canvas = object : android.graphics.Canvas() {
            override fun drawText(t: CharArray, i: Int, n: Int, x: Float, y: Float, p: android.graphics.Paint) { texts++ }
            override fun drawText(t: String, x: Float, y: Float, p: android.graphics.Paint) { texts++ }
            override fun drawText(t: String, s: Int, e: Int, x: Float, y: Float, p: android.graphics.Paint) { texts++ }
            override fun drawText(t: CharSequence, s: Int, e: Int, x: Float, y: Float, p: android.graphics.Paint) { texts++ }
            override fun drawTextRun(t: CharSequence, s: Int, e: Int, cs: Int, ce: Int, x: Float, y: Float, rtl: Boolean, p: android.graphics.Paint) { texts++ }
            override fun drawTextRun(t: CharArray, i: Int, n: Int, cs: Int, cn: Int, x: Float, y: Float, rtl: Boolean, p: android.graphics.Paint) { texts++ }
            // مربع النص يُرسم بعد نقل القلم إلى موضعه في الصفحة
            override fun translate(dx: Float, dy: Float) { if (dx == 0.1f * 360f && dy == 0.2f * 360f) texts++ }
            override fun drawRect(l: Float, t: Float, r: Float, b: Float, p: android.graphics.Paint) { rects++ }
        }
        val note = StickyNote("n", 0, 0.5f, 0.5f, 0.2f, 0.2f, 0xFFFFE27A.toInt())
        Exporter.drawPageLayers(
            canvas, 360f, "x", emptyList(), emptyList(), emptyList(),
            listOf(box), listOf(note), emptyList()
        )
        assertTrue("text box not drawn in thumbnail", texts > 0)
        assertTrue("sticky note not drawn in thumbnail", rects > 0)
    }
}
