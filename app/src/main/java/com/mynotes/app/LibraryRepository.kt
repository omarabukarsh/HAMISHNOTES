package com.mynotes.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors

/**
 * كل الملفات داخل مساحة التطبيق الخاصة:
 *  files/docs/<id>.pdf          نسخة المستند المستورد
 *  files/annotations/<id>.json  الكتابة اليدوية
 *  files/thumbs/<id>.jpg        الصورة المصغرة
 *  files/library.json           فهرس المكتبة
 */
class LibraryRepository(context: Context) {
    private val resolver = context.contentResolver
    private val root = context.filesDir
    private val docsDir = File(root, "docs").apply { mkdirs() }
    private val annDir = File(root, "annotations").apply { mkdirs() }
    private val thumbDir = File(root, "thumbs").apply { mkdirs() }
    private val indexFile = File(root, "library.json")

    private val writer = Executors.newSingleThreadExecutor { r ->
        Thread(r, "mynotes-writer").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val thumbCache = LruCache<String, Bitmap>(40)

    fun file(entry: DocEntry): File = File(docsDir, entry.fileName)

    // ---------- أدوات مساعدة ----------

    private fun atomicWrite(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    private fun queryName(uri: Uri): String? =
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: Throwable) {
            null
        }

    private fun probePageCount(f: File): Int {
        val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val r = PdfRenderer(pfd)
            try {
                return r.pageCount
            } finally {
                r.close()
            }
        } finally {
            pfd.close()
        }
    }

    // ---------- الفهرس ----------

    fun loadIndex(): List<DocEntry> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val arr = JSONObject(indexFile.readText()).getJSONArray("docs")
            (0 until arr.length()).mapNotNull { i ->
                try {
                    val o = arr.getJSONObject(i)
                    DocEntry(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        type = DocType.fromExt(o.optString("type", "pdf")) ?: DocType.PDF,
                        fileName = o.getString("file"),
                        sizeBytes = o.optLong("size"),
                        pageCount = o.optInt("pages"),
                        addedAt = o.optLong("added"),
                        lastOpened = o.optLong("opened"),
                        favorite = o.optBoolean("fav"),
                        lastPage = o.optInt("page")
                    )
                } catch (e: Throwable) {
                    null
                }
            }
        } catch (e: Throwable) {
            try {
                indexFile.copyTo(File(root, "library.json.bad"), overwrite = true)
            } catch (x: Throwable) {
            }
            emptyList()
        }
    }

    suspend fun saveIndex(list: List<DocEntry>) {
        withContext(writer) {
            try {
                val arr = JSONArray()
                list.forEach { e ->
                    arr.put(
                        JSONObject()
                            .put("id", e.id)
                            .put("name", e.name)
                            .put("type", e.type.ext)
                            .put("file", e.fileName)
                            .put("size", e.sizeBytes)
                            .put("pages", e.pageCount)
                            .put("added", e.addedAt)
                            .put("opened", e.lastOpened)
                            .put("fav", e.favorite)
                            .put("page", e.lastPage)
                    )
                }
                atomicWrite(indexFile, JSONObject().put("version", 1).put("docs", arr).toString())
            } catch (e: Throwable) {
            }
        }
    }

    // ---------- الاستيراد والنسخ والحذف ----------

    /** ينسخ الملف إلى مساحة التطبيق ويتحقق أنه سليم. يجب استدعاؤها خارج الخيط الرئيسي. */
    fun importFile(uri: Uri): DocEntry {
        val displayName = queryName(uri) ?: "document.pdf"
        val ext = displayName.substringAfterLast('.', "").lowercase()
        val mime = try {
            resolver.getType(uri)
        } catch (e: Throwable) {
            null
        }
        val type = when {
            mime == DocType.PDF.mime || ext == "pdf" -> DocType.PDF
            else -> DocType.fromExt(ext)
        }
        if (type == null || !type.supported) throw IOException("نوع الملف غير مدعوم بعد")

        val id = UUID.randomUUID().toString()
        val fileName = "$id.${type.ext}"
        val dest = File(docsDir, fileName)
        try {
            val input = resolver.openInputStream(uri) ?: throw IOException("تعذر قراءة الملف")
            input.use { ins -> dest.outputStream().use { out -> ins.copyTo(out) } }
            val pages = probePageCount(dest)
            if (pages <= 0) throw IOException("الملف فارغ")
            val now = System.currentTimeMillis()
            val entry = DocEntry(
                id = id,
                name = displayName.substringBeforeLast('.', displayName).ifBlank { "مستند" },
                type = type,
                fileName = fileName,
                sizeBytes = dest.length(),
                pageCount = pages,
                addedAt = now,
                lastOpened = 0L,
                favorite = false,
                lastPage = 0
            )
            makeThumbnail(entry)
            return entry
        } catch (t: Throwable) {
            dest.delete()
            throw t
        }
    }

    fun duplicate(src: DocEntry, suffix: String): DocEntry {
        val id = UUID.randomUUID().toString()
        val fileName = "$id.${src.type.ext}"
        file(src).copyTo(File(docsDir, fileName))
        File(annDir, "${src.id}.json").takeIf { it.exists() }?.copyTo(File(annDir, "$id.json"))
        File(thumbDir, "${src.id}.jpg").takeIf { it.exists() }?.copyTo(File(thumbDir, "$id.jpg"))
        return src.copy(
            id = id,
            name = src.name + suffix,
            fileName = fileName,
            addedAt = System.currentTimeMillis(),
            lastOpened = 0L,
            favorite = false,
            lastPage = 0
        )
    }

    fun delete(entry: DocEntry) {
        file(entry).delete()
        File(annDir, "${entry.id}.json").delete()
        File(thumbDir, "${entry.id}.jpg").delete()
        thumbCache.remove(entry.id)
        PageCache.removePrefix("${entry.id}:")
    }

    // ---------- الصور المصغرة ----------

    fun thumbnail(entry: DocEntry): Bitmap? {
        thumbCache.get(entry.id)?.let { return it }
        val f = File(thumbDir, entry.id + ".jpg")
        if (!f.exists()) makeThumbnail(entry)
        if (!f.exists()) return null
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        val b = BitmapFactory.decodeFile(f.absolutePath, opts)
        if (b != null) thumbCache.put(entry.id, b)
        return b
    }

    private fun makeThumbnail(entry: DocEntry) {
        try {
            val pfd = ParcelFileDescriptor.open(file(entry), ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val r = PdfRenderer(pfd)
                try {
                    val page = r.openPage(0)
                    try {
                        val w = 420
                        val h = (w * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        File(thumbDir, entry.id + ".jpg").outputStream().use { out ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        }
                        bmp.recycle()
                    } finally {
                        page.close()
                    }
                } finally {
                    r.close()
                }
            } finally {
                pfd.close()
            }
        } catch (e: Throwable) {
        }
    }

    // ---------- الكتابة اليدوية ----------

    fun loadAnnotations(id: String): Map<Int, List<Ink>> {
        val f = File(annDir, "$id.json")
        if (!f.exists()) return emptyMap()
        return try {
            val pages = JSONObject(f.readText()).optJSONObject("pages") ?: return emptyMap()
            val out = HashMap<Int, List<Ink>>()
            for (key in pages.keys()) {
                val arr = pages.getJSONArray(key)
                val list = ArrayList<Ink>(arr.length())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val flat = o.getJSONArray("p")
                    val pts = ArrayList<Pt>(flat.length() / 3)
                    var j = 0
                    while (j + 2 < flat.length()) {
                        pts.add(
                            Pt(
                                flat.getDouble(j).toFloat(),
                                flat.getDouble(j + 1).toFloat(),
                                flat.getDouble(j + 2).toFloat()
                            )
                        )
                        j += 3
                    }
                    list.add(Ink(pts, Color(o.getInt("c")), o.getDouble("w").toFloat()))
                }
                out[key.toInt()] = list
            }
            out
        } catch (e: Throwable) {
            try {
                f.copyTo(File(annDir, "$id.json.bad"), overwrite = true)
            } catch (x: Throwable) {
            }
            emptyMap()
        }
    }

    suspend fun saveAnnotations(id: String, data: Map<Int, List<Ink>>) {
        withContext(writer) {
            try {
                val pages = JSONObject()
                data.forEach { (page, list) ->
                    if (list.isNotEmpty()) {
                        val arr = JSONArray()
                        list.forEach { ink ->
                            val flat = JSONArray()
                            ink.pts.forEach {
                                flat.put(it.x.toDouble())
                                flat.put(it.y.toDouble())
                                flat.put(it.p.toDouble())
                            }
                            arr.put(
                                JSONObject()
                                    .put("c", ink.color.toArgb())
                                    .put("w", ink.width.toDouble())
                                    .put("p", flat)
                            )
                        }
                        pages.put(page.toString(), arr)
                    }
                }
                atomicWrite(
                    File(annDir, "$id.json"),
                    JSONObject().put("version", 1).put("pages", pages).toString()
                )
            } catch (e: Throwable) {
            }
        }
    }
}
