package com.hamishnotes.app

import com.hamishnotes.app.golden.AntibioticsCases
import com.hamishnotes.app.golden.GoldenCase
import com.hamishnotes.app.golden.GoldenCheck
import com.hamishnotes.app.golden.Pipeline
import com.hamishnotes.app.golden.PdfFixture
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * الحالات المرجعية من antibiotics.pdf على كلمات الملف (antibiotics_words.tsv) دون جهاز.
 * اختبار الجهاز يشغّل نفس الحالات على PdfTextLayer الحقيقية، ويتحقق أن الكلمات تطابق هذا الملف.
 */
@RunWith(Parameterized::class)
class AntibioticsGoldenTest(@Suppress("unused") private val id: String, private val case: GoldenCase) {

    @Test
    fun matchesReference() {
        val check = GoldenCheck(case, Pipeline.run(words, case.strokes))
        reports.add(check.report())
        assertTrue(check.report(), check.passed)
    }

    companion object {
        private val words by lazy { PdfFixture.load() }
        private val reports = ArrayList<String>()

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = AntibioticsCases.all.map { arrayOf(it.id, it) }

        @JvmStatic
        @AfterClass
        fun writeReport() {
            val dir = File("build/reports/golden").apply { mkdirs() }
            File(dir, "jvm-golden.txt").writeText(reports.joinToString("\n"))
        }
    }
}
