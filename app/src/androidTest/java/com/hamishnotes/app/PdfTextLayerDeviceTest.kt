package com.hamishnotes.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hamishnotes.app.golden.GoldenCheck
import com.hamishnotes.app.golden.PdfFixture
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * ملف antibiotics.pdf على Android الحقيقي:
 *  1. PdfTextLayer (pdfbox-android) تعيد نفس كلمات antibiotics_words.tsv ومواضعها، فاختبارات JVM تمثّل الجهاز فعلًا
 *  2. الحالات المرجعية كلها تنجح على كلمات الجهاز نفسها
 */
@RunWith(AndroidJUnit4::class)
class PdfTextLayerDeviceTest {

    @Test
    fun realTextLayerMatchesFixture() {
        val real = DeviceFiles.realWords(DeviceFiles.pdf(), PdfFixture.PAGES)
        DeviceFiles.writeReport("device-words.tsv", PdfFixture.format(real))
        val fixture = DeviceFiles.fixtureWords()
        val diffs = ArrayList<String>()
        for (p in PdfFixture.PAGES) {
            val a = real[p].orEmpty()
            val e = fixture[p].orEmpty()
            if (a.size != e.size) diffs.add("page $p: ${a.size} words on device, ${e.size} in fixture")
            for (i in 0 until minOf(a.size, e.size)) {
                val x = a[i]
                val y = e[i]
                val near = abs(x.l - y.l) < 1e-4f && abs(x.t - y.t) < 1e-4f && abs(x.r - y.r) < 1e-4f && abs(x.b - y.b) < 1e-4f
                if (x.text != y.text || x.line != y.line || !near) {
                    diffs.add("page $p word $i: device «${x.text}» L${x.line} [${x.l},${x.t},${x.r},${x.b}] / fixture «${y.text}» L${y.line} [${y.l},${y.t},${y.r},${y.b}]")
                }
            }
        }
        DeviceFiles.writeReport("device-words-diff.txt", if (diffs.isEmpty()) "identical\n" else diffs.joinToString("\n"))
        assertTrue("PdfTextLayer على الجهاز تختلف عن antibiotics_words.tsv:\n" + diffs.take(30).joinToString("\n"), diffs.isEmpty())
    }

    @Test
    fun goldenCasesOnDeviceWords() {
        val real = DeviceFiles.realWords(DeviceFiles.pdf(), PdfFixture.PAGES)
        val checks = GoldenCheck.run(real)
        val report = GoldenCheck.summaryReport(checks, "PdfTextLayer on device")
        DeviceFiles.writeReport("device-golden.txt", report)
        assertTrue(report, checks.all { it.passed })
    }
}
