package com.hamishnotes.app

import java.io.File
import java.text.Normalizer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSURL
import platform.PDFKit.PDFDocument
import platform.PDFKit.PDFOutline
import platform.PDFKit.PDFPage
import platform.PDFKit.kPDFDisplayBoxCropBox

/**
 * طبقة النص الحقيقية داخل ملف PDF (النص الرقمي المكتوب في الملف نفسه، لا صورة الصفحة).
 * أدق من القراءة الضوئية، وتدعم العربية، وتحفظ الرموز مثل β و →.
 * الإحداثيات تُعاد بنفس نظام OcrWord: نسبةً لعرض الصفحة (x و y كلاهما ÷ العرض).
 * الصفحات الممسوحة ضوئيًا (صور بلا نص) تعيد قائمة فارغة، فتُقرأ بالقراءة الضوئية بدلًا منها.
 * في iPad: PDFKit (نص الصفحة بترتيبه المنطقي + موضع كل حرف).
 */
@OptIn(ExperimentalForeignApi::class)
class PdfTextLayer(private val fileFor: (String) -> File?) {
    private val docs = HashMap<String, PDFDocument?>()
    private var closed = false

    private fun docFor(src: String): PDFDocument? = synchronized(this) {
        if (closed) return null
        if (docs.containsKey(src)) return docs[src]
        val f = fileFor(src)
        val d = try {
            if (f != null && f.exists()) PDFDocument(uRL = NSURL.fileURLWithPath(f.path)) else null
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
                val page = doc.pageAtIndex(ref.index.toULong()) ?: return emptyList()
                // الصفحات المدوّرة نتركها للقراءة الضوئية حتى لا تنحرف المواضع
                if (page.rotation.toInt() % 360 != 0) return emptyList()
                collect(page)
            } catch (t: Throwable) {
                emptyList()
            }
        }
    }

    private fun collect(page: PDFPage): List<OcrWord> {
        val text = page.string ?: return emptyList()
        val (bx, by, bw, bh) = page.boundsForBox(kPDFDisplayBoxCropBox).useContents {
            listOf(origin.x, origin.y, size.width, size.height)
        }
        if (bw <= 0.0) return emptyList()
        val out = ArrayList<OcrWord>()
        var line = 0
        var lineHasWords = false
        var i = 0
        val n = text.length
        while (i < n) {
            val ch = text[i]
            if (ch == '\n' || ch == '\r' || ch == '\u2028') {
                if (lineHasWords) line++
                lineHasWords = false
                i++
                continue
            }
            if (ch.isWhitespace()) { i++; continue }
            val start = i
            while (i < n && !text[i].isWhitespace()) i++
            var l = Double.MAX_VALUE
            var r = -Double.MAX_VALUE
            var t = Double.MAX_VALUE
            var b = -Double.MAX_VALUE
            for (k in start until i) {
                page.characterBoundsAtIndex(k.toLong()).useContents {
                    if (size.width > 0.0 || size.height > 0.0) {
                        // إحداثيات PDF (الأصل أسفل اليسار) ← أعلى يسار صندوق القص
                        val x0 = origin.x - bx
                        val x1 = x0 + size.width
                        val yTop = (by + bh) - (origin.y + size.height)
                        val yBot = (by + bh) - origin.y
                        l = minOf(l, x0); r = maxOf(r, x1); t = minOf(t, yTop); b = maxOf(b, yBot)
                    }
                }
            }
            val word = normalize(text.substring(start, i)).trim()
            if (word.isNotEmpty() && r > l && b > t) {
                out.add(OcrWord(word, (l / bw).toFloat(), (t / bw).toFloat(), (r / bw).toFloat(), (b / bw).toFloat(), line))
                lineHasWords = true
            }
        }
        return out
    }

    /** عنصر في فهرس الملف: عنوان، مستواه، ورقم صفحته في الملف الأصلي (من 0). */
    class OutlineItem(val title: String, val level: Int, val pageIndex: Int)

    /** فهرس عناوين الملف (Bookmarks) إن وُجد. */
    fun outline(src: String): List<OutlineItem> {
        val doc = docFor(src) ?: return emptyList()
        return synchronized(this) {
            if (closed) return emptyList()
            val out = ArrayList<OutlineItem>()
            try {
                val root = doc.outlineRoot ?: return emptyList()
                fun walk(node: PDFOutline, level: Int) {
                    val count = node.numberOfChildren.toInt()
                    for (k in 0 until count) {
                        if (out.size >= 1500) return
                        val item = node.childAtIndex(k.toULong()) ?: continue
                        val page = item.destination?.page
                        val idx = if (page != null) doc.indexForPage(page).toInt() else -1
                        val title = (item.label ?: "").trim()
                        if (idx >= 0 && title.isNotEmpty()) out.add(OutlineItem(title, level, idx))
                        if (level < 4) walk(item, level + 1)
                    }
                }
                walk(root, 0)
            } catch (t: Throwable) {
            }
            out
        }
    }

    fun close() = synchronized(this) {
        closed = true
        docs.clear()
    }

    private fun normalize(s: String): String =
        // أشكال الحروف العربية المتصلة (Presentation Forms) تُعاد لحروفها الأصلية
        Normalizer.normalize(s, Normalizer.Form.NFKC).replace('\u00A0', ' ')
}
