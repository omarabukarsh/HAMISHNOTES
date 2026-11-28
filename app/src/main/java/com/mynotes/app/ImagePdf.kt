package com.mynotes.app

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.OutputStream
import java.util.Locale

/**
 * يكتب ملف PDF صفحاته صور JPEG، كل صورة في صفحة.
 * الصورة تُضمَّن كما هي (DCTDecode) فيبقى حجم الملف قريبًا من حجم الصور نفسها،
 * بخلاف PdfDocument الذي يخزّن الصور بلا ضغط فيكبر الملف كثيرًا.
 */
class ImagePdfWriter(out: OutputStream) : Closeable {
    private val o = Counting(BufferedOutputStream(out, 64 * 1024))
    /** موضع كل كائن في الملف: الكائن رقم n في الخانة n. */
    private val offsets = ArrayList<Long>().apply { add(0L); add(0L); add(0L) }
    private val pageIds = ArrayList<Int>()
    private var finished = false

    init {
        // السطر الثاني بأحرف فوق 127 يخبر البرامج أن الملف ثنائي
        o.write("%PDF-1.4\n".toByteArray(Charsets.US_ASCII))
        o.write(byteArrayOf(0x25, 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), 0x0A))
    }

    val pageCount: Int get() = pageIds.size

    /**
     * يضيف صفحة بمقاس pageW × pageH (بالنقاط) ترسم عليها الصورة داخل المستطيل (x, y, w, h)
     * مقيسًا من أسفل يسار الصفحة كما في PDF.
     */
    fun addPage(jpeg: ByteArray, imgW: Int, imgH: Int, pageW: Float, pageH: Float, x: Float, y: Float, w: Float, h: Float) {
        check(!finished)
        val img = begin()
        text("<< /Type /XObject /Subtype /Image /Width $imgW /Height $imgH /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size} >>\nstream\n")
        o.write(jpeg)
        text("\nendstream\nendobj\n")

        val draw = "q ${n(w)} 0 0 ${n(h)} ${n(x)} ${n(y)} cm /Im0 Do Q".toByteArray(Charsets.US_ASCII)
        val content = begin()
        text("<< /Length ${draw.size} >>\nstream\n")
        o.write(draw)
        text("\nendstream\nendobj\n")

        val page = begin()
        text(
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${n(pageW)} ${n(pageH)}] " +
                "/Resources << /XObject << /Im0 $img 0 R >> >> /Contents $content 0 R >>\nendobj\n"
        )
        pageIds.add(page)
    }

    /** يكتب فهرس الصفحات ونهاية الملف. */
    fun finish() {
        if (finished) return
        finished = true
        offsets[1] = o.count
        text("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        offsets[2] = o.count
        text("2 0 obj\n<< /Type /Pages /Kids [${pageIds.joinToString(" ") { "$it 0 R" }}] /Count ${pageIds.size} >>\nendobj\n")
        val xref = o.count
        val sb = StringBuilder()
        sb.append("xref\n0 ${offsets.size}\n")
        sb.append("0000000000 65535 f \n")
        for (k in 1 until offsets.size) sb.append(String.format(Locale.US, "%010d 00000 n \n", offsets[k]))
        sb.append("trailer\n<< /Size ${offsets.size} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        text(sb.toString())
        o.flush()
    }

    override fun close() {
        o.flush()
    }

    private fun begin(): Int {
        val id = offsets.size
        offsets.add(o.count)
        text("$id 0 obj\n")
        return id
    }

    private fun text(s: String) = o.write(s.toByteArray(Charsets.US_ASCII))

    private fun n(v: Float): String = String.format(Locale.US, "%.2f", v)

    private class Counting(private val out: OutputStream) : OutputStream() {
        var count = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }

        override fun flush() = out.flush()
    }
}
