package com.hamishnotes.app.golden

import com.hamishnotes.app.OcrWord
import java.io.InputStream

/**
 * كلمات صفحات ملف antibiotics.pdf بمواضعها، كما تعيدها PdfTextLayer.
 * الصيغة: page(0-based) \t line \t l \t t \t r \t b \t text
 * اختبار الجهاز PdfTextLayerFixtureTest يتحقق أن PdfTextLayer الحقيقية على Android تعيد نفس الكلمات.
 */
object PdfFixture {
    const val PDF_ASSET = "antibiotics.pdf"
    const val WORDS_ASSET = "antibiotics_words.tsv"

    /** الصفحات المحفوظة في الملف (أرقام من 0). */
    val PAGES = listOf(1, 2, 3, 4, 5, 9, 10, 29)

    fun parse(input: InputStream): Map<Int, List<OcrWord>> {
        val out = LinkedHashMap<Int, MutableList<OcrWord>>()
        input.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (ln in lines) {
                if (ln.isEmpty() || ln.startsWith("#")) continue
                val f = ln.split('\t')
                require(f.size == 7) { "سطر غير صالح: $ln" }
                out.getOrPut(f[0].toInt()) { ArrayList() }.add(
                    OcrWord(f[6], f[2].toFloat(), f[3].toFloat(), f[4].toFloat(), f[5].toFloat(), f[1].toInt())
                )
            }
        }
        return out
    }

    fun format(pages: Map<Int, List<OcrWord>>): String {
        val sb = StringBuilder("# page(0-based)\tline\tl\tt\tr\tb\ttext\n")
        for ((p, ws) in pages) for (w in ws) {
            sb.append(p).append('\t').append(w.line).append('\t').append(w.l).append('\t').append(w.t)
                .append('\t').append(w.r).append('\t').append(w.b).append('\t').append(w.text).append('\n')
        }
        return sb.toString()
    }

    fun load(): Map<Int, List<OcrWord>> {
        val res = PdfFixture::class.java.classLoader!!.getResourceAsStream(WORDS_ASSET)
            ?: error("لم يُعثر على $WORDS_ASSET في موارد الاختبار")
        return res.use { parse(it) }
    }
}
