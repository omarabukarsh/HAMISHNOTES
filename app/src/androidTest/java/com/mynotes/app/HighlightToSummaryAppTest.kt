package com.mynotes.app

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mynotes.app.golden.AntibioticsCases
import com.mynotes.app.golden.GoldenCheck
import com.mynotes.app.golden.Pipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * المسار الفعلي داخل التطبيق: استيراد antibiotics.pdf ← فتحه في DocSession ← خطوط هايلايتر عبر
 * DocSession.snapHighlight (نفس ما يستدعيه ReaderScreen عند رفع القلم) ← الملخص كما يعرضه الدرج
 * (Summary.view على allMarks) ← تصدير الملخص PDF بـ Exporter وقراءته مرة أخرى.
 */
@RunWith(AndroidJUnit4::class)
class HighlightToSummaryAppTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /** حالات على صفحات مختلفة حتى يكون الملخص المتوقع هو تتابعها بترتيب الصفحات. */
    private val cases = listOf(
        AntibioticsCases.arabicBox,
        AntibioticsCases.doseTable,
        AntibioticsCases.arabicDiacritics,
        AntibioticsCases.wrappedCells
    )

    @Test
    fun importHighlightSummarizeExport() {
        val c = AppController
        rule.waitUntil(20_000) { c.libraryLoaded }

        val before = rule.runOnUiThread { c.library.map { it.id }.toSet() }
        val pdf = DeviceFiles.pdf()
        rule.runOnUiThread { c.importDocuments(listOf(Uri.fromFile(pdf))) }
        rule.waitUntil(30_000) { c.library.any { it.id !in before } }
        val entry = rule.runOnUiThread { c.library.first { it.id !in before } }
        assertEquals(34, entry.pageCount)

        rule.runOnUiThread { c.openDocument(entry.id) }
        rule.waitUntil(30_000) { c.tabs.firstOrNull { it.id == entry.id }?.status is SessionStatus.Ready }
        val s = rule.runOnUiThread { c.tabs.first { it.id == entry.id } }

        for (case in cases) for (st in case.strokes) {
            val old = s.marks[st.page]
            rule.runOnUiThread { s.snapHighlight(st.page, st.pts, st.color, Pipeline.WIDTH) }
            rule.waitUntil(30_000) { s.marks[st.page] !== old }
        }

        val blocks = rule.runOnUiThread { Summary.view(s.allMarks(), true, s.summaryEdits) }
        val actual = Pipeline.render(blocks)
        val expected = cases.flatMap { it.summary }
        val report = buildString {
            append(if (actual == expected) "PASS" else "FAIL").append(" in-app summary (").append(cases.joinToString { it.id }).append(")\n")
            append("expected:\n").append(expected.joinToString("\n")).append("\nactual:\n").append(actual.joinToString("\n")).append('\n')
        }
        DeviceFiles.writeReport("app-summary.txt", report)
        screenshot("reader-after-highlights.png")

        // تصدير الملخص كما يفعل زر «حفظ PDF» في الدرج، ثم قراءة الملف الناتج بطبقة النص
        val out = File(DeviceFiles.reportDir(), "exported-summary.pdf")
        val pages = Exporter.exportSummary(DeviceFiles.target, s.title, blocks, Uri.fromFile(out))
        assertTrue("التصدير لم ينتج صفحات", pages > 0)
        val exported = DeviceFiles.realWords(out, (0 until pages).toList()).values.flatten().map { it.text }
        val exportedTokens = GoldenCheck.tokens(exported.joinToString(" ")).toSet()
        val missing = cases.flatMap { it.critical }.flatMap { GoldenCheck.tokens(it) }.filter { it !in exportedTokens }
        DeviceFiles.writeReport(
            "app-export.txt",
            "exported pages: $pages\nmissing critical tokens: ${missing.joinToString(" ")}\n\n" + exported.joinToString(" ")
        )

        assertEquals(report, expected, actual)
        assertTrue("مقاطع حرجة غير موجودة في ملف الملخص المصدَّر: $missing", missing.isEmpty())
    }

    private fun screenshot(name: String) {
        val bmp = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        File(DeviceFiles.reportDir(), name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
