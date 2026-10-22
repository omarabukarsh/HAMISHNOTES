package com.mynotes.app

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.text.Normalizer

/**
 * طبقة النص الحقيقية داخل ملف PDF (النص الرقمي المكتوب في الملف نفسه، لا صورة الصفحة).
 * أدق من القراءة الضوئية، وتدعم العربية، وتحفظ الرموز مثل β و →.
 * الإحداثيات تُعاد بنفس نظام OcrWord: نسبةً لعرض الصفحة (x و y كلاهما ÷ العرض).
 * الصفحات الممسوحة ضوئيًا (صور بلا نص) تعيد قائمة فارغة، فتُقرأ بالقراءة الضوئية بدلًا منها.
 */
class PdfTextLayer(private val fileFor: (String) -> File?) {
    private val docs = HashMap<String, PDDocument?>()
    private var closed = false

    private fun docFor(src: String): PDDocument? = synchronized(this) {
        if (closed) return null
        if (docs.containsKey(src)) return docs[src]
        val f = fileFor(src)
        val d = try {
            if (f != null && f.exists()) PDDocument.load(f) else null
        } catch (t: Throwable) {
            null
        }
        docs[src] = d
        d
    }

    /** كلمات صفحة واحدة من ملفها الأصلي، أو قائمة فارغة إن لم يكن فيها نص رقمي. */
    fun words(ref: PageRef): List<OcrWord> {
        if (ref.src.startsWith("blank:")) return emptyList()
        val doc = docFor(ref.src) ?: return emptyList()
        return synchronized(this) {
            if (closed) return emptyList()
            try {
                val page = doc.getPage(ref.index)
                val box = page.cropBox
                val rot = page.rotation % 360
                // الصفحات المدوّرة نتركها للقراءة الضوئية حتى لا تنحرف المواضع
                if (rot != 0) return emptyList()
                val collector = Collector(box.width, box.lowerLeftX, box.upperRightY)
                collector.sortByPosition = true
                collector.startPage = ref.index + 1
                collector.endPage = ref.index + 1
                collector.getText(doc)
                collector.out
            } catch (t: Throwable) {
                emptyList()
            }
        }
    }

    fun close() = synchronized(this) {
        closed = true
        docs.values.forEach {
            try {
                it?.close()
            } catch (e: Throwable) {
            }
        }
        docs.clear()
    }

    /** يجمع الكلمات بمواضعها. PDFBox يستدعي writeString لكل كلمة بنصها المنطقي (يصلح العربية). */
    private class Collector(
        private val pageW: Float,
        private val originX: Float,
        @Suppress("unused") private val topY: Float
    ) : PDFTextStripper() {
        val out = ArrayList<OcrWord>()
        private var line = 0
        private var lineHasWords = false

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            val clean = normalize(text)
            if (clean.isBlank() || textPositions.isEmpty() || pageW <= 0f) return
            var l = Float.MAX_VALUE
            var r = -Float.MAX_VALUE
            var t = Float.MAX_VALUE
            var b = -Float.MAX_VALUE
            for (p in textPositions) {
                val x = p.xDirAdj - originX
                val h = p.heightDir.coerceAtLeast(p.fontSizeInPt * 0.6f)
                val base = p.yDirAdj
                l = minOf(l, x)
                r = maxOf(r, x + p.widthDirAdj)
                t = minOf(t, base - h)
                b = maxOf(b, base + h * 0.22f)
            }
            if (r <= l || b <= t) return
            out.add(OcrWord(clean.trim(), l / pageW, t / pageW, r / pageW, b / pageW, line))
            lineHasWords = true
        }

        override fun writeLineSeparator() {
            if (lineHasWords) line++
            lineHasWords = false
        }

        private fun normalize(s: String): String =
            // أشكال الحروف العربية المتصلة (Presentation Forms) تُعاد لحروفها الأصلية
            Normalizer.normalize(s, Normalizer.Form.NFKC).replace(' ', ' ')
    }
}
