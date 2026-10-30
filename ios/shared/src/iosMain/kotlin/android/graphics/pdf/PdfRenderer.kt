package android.graphics.pdf

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import com.mynotes.app.IosPdf
import java.io.IOException
import platform.CoreGraphics.CGPDFDocumentRef
import platform.CoreGraphics.CGPDFDocumentRelease

/**
 * نفس واجهة PdfRenderer في أندرويد، فوق CoreGraphics (CGPDFDocument).
 * الأبعاد بالنقاط بعد التدوير، كما في أندرويد.
 */
class PdfRenderer(pfd: ParcelFileDescriptor) {
    private var doc: CGPDFDocumentRef? = IosPdf.openDocument(pfd.file.path)
        ?: throw IOException("not a PDF")

    val pageCount: Int = IosPdf.pageCount(doc!!)

    fun openPage(index: Int): Page {
        val d = doc ?: throw IllegalStateException("closed")
        if (index < 0 || index >= pageCount) throw IllegalArgumentException("page $index")
        return Page(d, index)
    }

    fun close() {
        doc?.let { CGPDFDocumentRelease(it) }
        doc = null
    }

    class Page internal constructor(private val d: CGPDFDocumentRef, val index: Int) {
        private val size = IosPdf.pageSize(d, index)
        val width: Int = size.first.toInt()
        val height: Int = size.second.toInt()
        val widthF: Float = size.first
        val heightF: Float = size.second

        /**
         * يرسم الصفحة في الصورة. matrix = null: الصفحة كلها بحجم الصورة.
         * وإلا: تكبير (scaleX) ثم إزاحة (transX, transY) بإحداثيات أعلى اليسار كما في أندرويد.
         */
        fun render(dest: Bitmap, clip: Rect?, matrix: Matrix?, mode: Int) {
            val fullW: Float
            val left: Float
            val top: Float
            if (matrix == null) {
                fullW = dest.width.toFloat(); left = 0f; top = 0f
            } else {
                fullW = widthF * matrix.scaleX; left = -matrix.transX; top = -matrix.transY
            }
            IosPdf.renderPage(d, index, dest, fullW, left, top, white = false)
        }

        fun close() {}

        companion object {
            const val RENDER_MODE_FOR_DISPLAY = 1
            const val RENDER_MODE_FOR_PRINT = 2
        }
    }
}
