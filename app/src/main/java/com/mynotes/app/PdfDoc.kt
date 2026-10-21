package com.mynotes.app

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import java.io.File

/** ذاكرة مؤقتة للصفحات المرسومة، بحد أقصى للذاكرة حتى لا تمتلئ مع الملفات الكبيرة. */
object PageCache {
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 5L).coerceAtMost(160L * 1024L * 1024L).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bmp: Bitmap) {
        cache.put(key, bmp)
    }

    fun removePrefix(prefix: String) {
        cache.snapshot().keys.filter { it.startsWith(prefix) }.forEach { cache.remove(it) }
    }
}

/** ذاكرة مؤقتة للصور المضافة فوق الصفحات. */
object ImageCache {
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8L).coerceAtMost(96L * 1024L * 1024L).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bmp: Bitmap) {
        cache.put(key, bmp)
    }
}

class PdfDoc private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val id: String
) {
    val count: Int = renderer.pageCount

    val ratios: List<Float> = (0 until count).map { i ->
        val p = renderer.openPage(i)
        try {
            if (p.height > 0) p.width.toFloat() / p.height.toFloat() else 0.707f
        } finally {
            p.close()
        }
    }

    private var closed = false

    fun cached(index: Int, widthPx: Int): Bitmap? = PageCache.get("$id:$index:$widthPx")

    private fun draw(index: Int, widthPx: Int): Bitmap {
        val page = renderer.openPage(index)
        try {
            val h = (widthPx * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
            val b = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
            b.eraseColor(android.graphics.Color.WHITE)
            page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return b
        } finally {
            page.close()
        }
    }

    /** للعرض على الشاشة: تُخزَّن في الذاكرة المؤقتة. */
    fun render(index: Int, widthPx: Int): Bitmap? {
        if (widthPx <= 0) return null
        val key = "$id:$index:$widthPx"
        PageCache.get(key)?.let { return it }
        val bmp: Bitmap = synchronized(this) {
            if (closed || index < 0 || index >= count) return null
            draw(index, widthPx)
        }
        PageCache.put(key, bmp)
        return bmp
    }

    /** للقراءة الضوئية: صورة جديدة غير مخزّنة، يجب على المستدعي تحريرها بـ recycle(). */
    fun renderFresh(index: Int, widthPx: Int): Bitmap? {
        if (widthPx <= 0) return null
        return synchronized(this) {
            if (closed || index < 0 || index >= count) return null
            draw(index, widthPx)
        }
    }

    fun close() {
        synchronized(this) {
            if (!closed) {
                closed = true
                try {
                    renderer.close()
                } catch (e: Throwable) {
                }
                try {
                    pfd.close()
                } catch (e: Throwable) {
                }
            }
        }
        PageCache.removePrefix("$id:")
    }

    companion object {
        fun open(id: String, file: File): PdfDoc {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            var renderer: PdfRenderer? = null
            try {
                renderer = PdfRenderer(pfd)
                return PdfDoc(pfd, renderer, id)
            } catch (t: Throwable) {
                try {
                    renderer?.close()
                } catch (e: Throwable) {
                }
                try {
                    pfd.close()
                } catch (e: Throwable) {
                }
                throw t
            }
        }
    }
}
