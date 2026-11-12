package com.hamishnotes.app

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * النسخة القابلة للتعديل بالصيغة الموحدة (v2): الأصل النظيف مرفق داخل الملف ويُستعمل عند الفتح،
 * والصيغة القديمة (v1 بلا أصل) تبقى تُفتح بحذف الطبقات، ومرفقات الآيباد تُحفظ ويُعاد إرفاقها.
 */
@RunWith(RobolectricTestRunner::class)
class EditablePdfTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val data = """{"version":3,"pages":{"0":[{"c":-16777216,"w":0.004,"k":"BALLPOINT","t":5,"p":[0.1,0.1,1,0.2,0.2,1]}]},"images":[],"marks":[],"texts":[]}"""

    private fun rect(doc: PDDocument, page: PDPage, append: Boolean) {
        val cs = if (append) PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)
        else PDPageContentStream(doc, page)
        cs.use {
            it.addRect(50f, 50f, 100f, 100f)
            it.fill()
        }
    }

    /** يحاكي التصدير: صفحتان نظيفتان، تُحفظ نسخة نظيفة، ثم تُرسم طبقة فوق كل صفحة ويُرفق كل شيء. */
    private fun export(withOrig: Boolean, counts: List<Int>? = null, extras: List<File> = emptyList()): Pair<File, File> {
        val doc = PDDocument()
        val pages = (0 until 2).map { PDPage(PDRectangle.A4).also { p -> doc.addPage(p); rect(doc, p, false) } }
        val orig = tmp.newFile()
        doc.save(orig)
        val real = ArrayList<Int>()
        for (p in pages) {
            real.add(EditablePdf.contentCount(p))
            rect(doc, p, true)
        }
        val img = File(tmp.newFolder(), "pic.png").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        EditablePdf.embed(doc, data, counts ?: real, listOf(img), if (withOrig) orig else null, extras)
        val out = tmp.newFile()
        doc.save(out)
        doc.close()
        return out to orig
    }

    private fun layers(f: File): List<Int> = PDDocument.load(f).use { d -> (0 until d.numberOfPages).map { EditablePdf.contentCount(d.getPage(it)) } }

    @Test
    fun v2_restoresTheEmbeddedOriginalWithDataAndImages() {
        val (file, orig) = export(withOrig = true)
        val clean = orig.readBytes()
        assertTrue(layers(file).all { it > 1 })
        var got: String? = null
        val imgDir = tmp.newFolder()
        assertTrue(EditablePdf.restore(file, { got = it }, imgDir, tmp.newFolder()))
        assertEquals(data, got)
        assertArrayEquals(clean, file.readBytes())
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), File(imgDir, "pic.png").readBytes())
        PDDocument.load(file).use { d ->
            assertEquals(2, d.numberOfPages)
            assertNull(d.documentCatalog.names)
        }
    }

    @Test
    fun v1_withoutOriginal_stillStripsTheDrawnLayers() {
        val (file, orig) = export(withOrig = false)
        var got: String? = null
        assertTrue(EditablePdf.restore(file, { got = it }, tmp.newFolder()))
        assertEquals(data, got)
        assertEquals(layers(orig), layers(file))
    }

    @Test
    fun v2_keepsRealCounts_soOlderVersionsCanStillStripLayers() {
        val (file, orig) = export(withOrig = true)
        PDDocument.load(file).use { d ->
            val names = d.documentCatalog.names.embeddedFiles.names
            val meta = org.json.JSONObject(String(names["hamish-meta.json"]!!.embeddedFile.toByteArray()))
            assertEquals(2, meta.getInt("v"))
            val counts = meta.getJSONArray("counts")
            assertEquals(layers(orig), (0 until counts.length()).map { counts.getInt(it) })
            assertTrue(names.containsKey(EditablePdf.ORIG))
        }
    }

    @Test
    fun ipadFile_emptyCounts_opensFromTheOriginal_andKeepsIosAttachments() {
        val ios = File(tmp.newFolder(), EditablePdf.IOS + "data.json").apply { writeText("{\"x\":1}") }
        val (file, orig) = export(withOrig = true, counts = emptyList(), extras = listOf(ios))
        val clean = orig.readBytes()
        val extras = tmp.newFolder()
        assertTrue(EditablePdf.restore(file, { }, tmp.newFolder(), extras))
        assertArrayEquals(clean, file.readBytes())
        assertEquals("{\"x\":1}", File(extras, EditablePdf.IOS + "data.json").readText())
    }

    @Test
    fun emptyCounts_withoutOriginal_isLeftAsAFinalCopy() {
        val (file, _) = export(withOrig = false, counts = emptyList())
        val before = file.readBytes()
        assertFalse(EditablePdf.restore(file, { }, tmp.newFolder()))
        assertArrayEquals(before, file.readBytes())
    }

    @Test
    fun plainPdf_isNotTouched() {
        val (_, orig) = export(withOrig = true)
        val before = orig.readBytes()
        assertFalse(EditablePdf.restore(orig, { }, tmp.newFolder()))
        assertArrayEquals(before, orig.readBytes())
    }
}
