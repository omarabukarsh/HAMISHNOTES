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
                val collector = Collector(box.width)
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

    /** عنصر في فهرس الملف: عنوان، مستواه، ورقم صفحته في الملف الأصلي (من 0). */
    class OutlineItem(val title: String, val level: Int, val pageIndex: Int)

    /** فهرس عناوين الملف (Bookmarks) إن وُجد. */
    fun outline(src: String): List<OutlineItem> {
        val doc = docFor(src) ?: return emptyList()
        return synchronized(this) {
            if (closed) return emptyList()
            val out = ArrayList<OutlineItem>()
            try {
                val root = doc.documentCatalog.documentOutline ?: return emptyList()
                fun walk(node: com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode, level: Int) {
                    var item = node.firstChild
                    var guard = 0
                    while (item != null && guard++ < 2000 && out.size < 1500) {
                        val page = try {
                            item.findDestinationPage(doc)
                        } catch (t: Throwable) {
                            null
                        }
                        val idx = if (page != null) doc.pages.indexOf(page) else -1
                        val title = (item.title ?: "").trim()
                        if (idx >= 0 && title.isNotEmpty()) out.add(OutlineItem(title, level, idx))
                        if (level < 4) walk(item, level + 1)
                        item = item.nextSibling
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
        docs.values.forEach {
            try {
                it?.close()
            } catch (e: Throwable) {
            }
        }
        docs.clear()
    }

    /** يجمع الكلمات بمواضعها. PDFBox يستدعي writeString لكل كلمة بنصها المنطقي (يصلح العربية). */
    private class Collector(private val pageW: Float) : PDFTextStripper() {
        val out = ArrayList<OcrWord>()
        private var line = 0
        private var lineHasWords = false

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            if (textPositions.isEmpty() || pageW <= 0f) return
            // قد يحمل الاستدعاء الواحد عدة كلمات بينها مسافات: نقسم المواضع عند كل مسافة
            val groups = ArrayList<List<TextPosition>>()
            var cur = ArrayList<TextPosition>()
            for (p in textPositions) {
                val u = p.unicode ?: ""
                if (u.isBlank()) {
                    if (cur.isNotEmpty()) groups.add(cur)
                    cur = ArrayList()
                } else {
                    cur.add(p)
                }
            }
            if (cur.isNotEmpty()) groups.add(cur)
            if (groups.isEmpty()) return
            val parts = normalize(text).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val rtl = parts.any { w -> w.any { it in '\u0600'..'\u06FF' } }
            // المواضع بترتيب بصري؛ في العربية الكلمات المنطقية بالعكس
            val ordered = if (rtl) parts.reversed() else parts
            if (ordered.size == groups.size) {
                groups.forEachIndexed { k, g -> emit(ordered[k], g) }
            } else {
                emit(parts.joinToString(" "), textPositions.filter { !(it.unicode ?: "").isBlank() })
            }
            lineHasWords = true
        }

        private fun emit(word: String, ps: List<TextPosition>) {
            if (word.isBlank() || ps.isEmpty()) return
            var l = Float.MAX_VALUE
            var r = -Float.MAX_VALUE
            var t = Float.MAX_VALUE
            var b = -Float.MAX_VALUE
            for (p in ps) {
                // إحداثيات PDFBox نسبية لصندوق القص أصلًا
                val x = p.xDirAdj
                val h = p.heightDir.coerceAtLeast(p.fontSizeInPt * 0.6f)
                val base = p.yDirAdj
                l = minOf(l, x)
                r = maxOf(r, x + p.widthDirAdj)
                t = minOf(t, base - h)
                b = maxOf(b, base + h * 0.22f)
            }
            if (r <= l || b <= t) return
            out.add(OcrWord(ArabicFix.word(word), l / pageW, t / pageW, r / pageW, b / pageW, line, isBold(ps), italic = isItalic(ps)))
        }

        /** الكلمة عريضة إن كان خط أغلب حروفها عريضًا (من اسم الخط أو وزنه). */
        private fun isBold(ps: List<TextPosition>): Boolean {
            var bold = 0
            for (p in ps) {
                val f = try { p.font } catch (t: Throwable) { null } ?: continue
                val name = (try { f.name } catch (t: Throwable) { null } ?: "").lowercase()
                val d = try { f.fontDescriptor } catch (t: Throwable) { null }
                val weight = try { d?.fontWeight ?: 0f } catch (t: Throwable) { 0f }
                val force = try { d?.isForceBold == true } catch (t: Throwable) { false }
                if (force || weight >= 600f || name.contains("bold") || name.contains("black") ||
                    name.contains("heavy") || name.contains("semibold") || name.contains("demi")
                ) bold++
            }
            return bold * 2 > ps.size
        }

        /** الكلمة مائلة إن كان خط أغلب حروفها مائلًا (من اسم الخط أو زاوية الميل أو علامة الخط). */
        private fun isItalic(ps: List<TextPosition>): Boolean {
            var it = 0
            for (p in ps) {
                val f = try { p.font } catch (t: Throwable) { null } ?: continue
                val name = (try { f.name } catch (t: Throwable) { null } ?: "").lowercase()
                val d = try { f.fontDescriptor } catch (t: Throwable) { null }
                val angle = try { d?.italicAngle ?: 0f } catch (t: Throwable) { 0f }
                val flag = try { d?.isItalic == true } catch (t: Throwable) { false }
                if (flag || angle != 0f || name.contains("italic") || name.contains("oblique")) it++
            }
            return it * 2 > ps.size
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
