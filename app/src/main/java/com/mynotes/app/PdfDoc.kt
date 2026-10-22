package com.mynotes.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * صفحة في المستند. src:
 *  "main"          الملف الأصلي
 *  "part:<ملف>"    ملف PDF أُضيف لاحقًا
 *  "blank:<قالب>"  صفحة فارغة
 */
data class PageRef(val src: String, val index: Int, val ratio: Float)

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

class PdfDoc private constructor(private val id: String) {
    private class Source(val pfd: ParcelFileDescriptor, val renderer: PdfRenderer)

    private val sources = HashMap<String, Source>()
    private var closed = false

    /** قائمة الصفحات الظاهرة بالترتيب (حالة يراقبها الواجهة). */
    var pages by mutableStateOf<List<PageRef>>(emptyList())
        private set

    val count: Int get() = pages.size

    fun ratio(i: Int): Float = pages.getOrNull(i)?.ratio ?: 0.707f

    fun setPages(list: List<PageRef>) {
        pages = list
    }

    private fun key(ref: PageRef, widthPx: Int): String = "$id:${ref.src}:${ref.index}:$widthPx"

    fun cached(ref: PageRef?, widthPx: Int): Bitmap? =
        if (ref == null) null else PageCache.get(key(ref, widthPx))

    /** يفتح ملف PDF كمصدر صفحات ويعيد صفحاته. خارج الخيط الرئيسي. */
    fun addSource(srcKey: String, file: File): List<PageRef> {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        var opened: PdfRenderer? = null
        try {
            val r = PdfRenderer(pfd)
            opened = r
            val refs = (0 until r.pageCount).map { i ->
                val p = r.openPage(i)
                try {
                    PageRef(srcKey, i, if (p.height > 0) p.width.toFloat() / p.height.toFloat() else 0.707f)
                } finally {
                    p.close()
                }
            }
            synchronized(this) { sources[srcKey] = Source(pfd, r) }
            return refs
        } catch (t: Throwable) {
            try {
                opened?.close()
            } catch (e: Throwable) {
            }
            try {
                pfd.close()
            } catch (e: Throwable) {
            }
            throw t
        }
    }

    private fun draw(ref: PageRef, widthPx: Int): Bitmap? {
        if (ref.src.startsWith("blank:")) {
            val h = (widthPx / ref.ratio).toInt().coerceAtLeast(1)
            val b = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
            val style = Templates.parse(ref.src)
            Templates.draw(Canvas(b), widthPx.toFloat(), h.toFloat(), style.first, style.second)
            return b
        }
        val src = sources[ref.src] ?: return null
        if (ref.index < 0 || ref.index >= src.renderer.pageCount) return null
        val page = src.renderer.openPage(ref.index)
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
    fun render(ref: PageRef?, widthPx: Int): Bitmap? {
        if (ref == null || widthPx <= 0) return null
        val k = key(ref, widthPx)
        PageCache.get(k)?.let { return it }
        val bmp: Bitmap = synchronized(this) {
            if (closed) return null
            draw(ref, widthPx)
        } ?: return null
        PageCache.put(k, bmp)
        return bmp
    }

    /** للقراءة الضوئية: صورة جديدة غير مخزّنة، يجب على المستدعي تحريرها بـ recycle(). */
    fun renderFresh(ref: PageRef?, widthPx: Int): Bitmap? {
        if (ref == null || widthPx <= 0) return null
        return synchronized(this) {
            if (closed) return null
            draw(ref, widthPx)
        }
    }

    fun close() {
        synchronized(this) {
            if (!closed) {
                closed = true
                sources.values.forEach { s ->
                    try {
                        s.renderer.close()
                    } catch (e: Throwable) {
                    }
                    try {
                        s.pfd.close()
                    } catch (e: Throwable) {
                    }
                }
                sources.clear()
            }
        }
        PageCache.removePrefix("$id:")
    }

    companion object {
        /**
         * @param saved قائمة الصفحات المحفوظة (null = المستند لم يُعدَّل، فكل صفحات الملف الأصلي بالترتيب)
         */
        fun open(id: String, main: File, partsDir: File, saved: List<PageRef>?): PdfDoc {
            val doc = PdfDoc(id)
            try {
                val mainPages = doc.addSource("main", main)
                if (saved == null) {
                    doc.setPages(mainPages)
                } else {
                    val failed = HashSet<String>()
                    saved.map { it.src }
                        .filter { it.startsWith("part:") }
                        .distinct()
                        .forEach { k ->
                            try {
                                doc.addSource(k, File(partsDir, k.removePrefix("part:")))
                            } catch (e: Throwable) {
                                failed.add(k)
                            }
                        }
                    val valid = saved.filter { it.src !in failed }
                    doc.setPages(if (valid.isEmpty()) mainPages else valid)
                }
                return doc
            } catch (t: Throwable) {
                doc.close()
                throw t
            }
        }
    }
}
