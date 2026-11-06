package com.hamishnotes.app

import android.content.Context
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.hamishnotes.app.golden.PdfFixture
import java.io.File

/** ملفات اختبارات الجهاز: ملف PDF المرفق من موارد الاختبار، ومجلد التقارير الذي يسحبه CI. */
object DeviceFiles {
    val target: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val testAssets get() = InstrumentationRegistry.getInstrumentation().context.assets

    fun pdf(): File {
        val f = File(target.cacheDir, PdfFixture.PDF_ASSET)
        if (!f.exists() || f.length() == 0L) {
            testAssets.open(PdfFixture.PDF_ASSET).use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        }
        return f
    }

    fun fixtureWords() = testAssets.open(PdfFixture.WORDS_ASSET).use { PdfFixture.parse(it) }

    /** /sdcard/Android/data/com.hamishnotes.app/files/test-reports (يسحبه CI بعد الاختبارات). */
    fun reportDir(): File = File(target.getExternalFilesDir(null), "test-reports").apply { mkdirs() }

    fun writeReport(name: String, text: String) {
        File(reportDir(), name).writeText(text)
        text.lines().chunked(40).forEach { Log.i("MyNotesGolden", it.joinToString("\n")) }
    }

    fun realWords(file: File, pages: List<Int>): Map<Int, List<OcrWord>> {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(target)
        val layer = PdfTextLayer { file }
        try {
            return pages.associateWith { p -> layer.words(PageRef("antibiotics", p, 1f)) }
        } finally {
            layer.close()
        }
    }
}
