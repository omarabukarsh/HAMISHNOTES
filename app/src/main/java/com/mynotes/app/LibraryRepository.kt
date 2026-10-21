package com.mynotes.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
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

class DocData(
    val ink: Map<Int, List<Ink>>,
    val images: Map<Int, List<ImageItem>>
)

/**
 * كل الملفات داخل مساحة التطبيق الخاصة:
 *  files/docs/<id>.pdf          نسخة المستند المستورد
 *  files/annotations/<id>.json  الكتابة اليدوية والصور المضافة (مواضعها)
 *  files/images/<id>/           ملفات الصور المضافة
 *  files/ocr/<id>/<page>.json   نتيجة القراءة الضوئية لكل صفحة
 *  files/thumbs/<id>.jpg        الصورة المصغرة
 *  files/library.json           فهرس المكتبة
 */
class LibraryRepository(context: Context) {
    private val resolver = context.contentResolver
    private val root = context.filesDir
    private val docsDir = File(root, "docs").apply { mkdirs() }
    private val annDir = File(root, "annotations").apply { mkdirs() }
    private val thumbDir = File(root, "thumbs").apply { mkdirs() }
    private val imgRoot = File(root, "images").apply { mkdirs() }
    private val ocrRoot = File(root, "ocr").apply { mkdirs() }
    private val indexFile = File(root, "library.json")

    private val writer = Executors.newSingleThreadExecutor { r ->
        Thread(r, "mynotes-writer").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val thumbCache = LruCache<String, Bitmap>(40)

    fun file(entry: DocEntry): File = File(docsDir, entry.fileName)

    fun imageFile(docId: String, name: String): File = File(File(imgRoot, docId), name)

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
        File(imgRoot, src.id).takeIf { it.exists() }?.copyRecursively(File(imgRoot, id))
        File(ocrRoot, src.id).takeIf { it.exists() }?.copyRecursively(File(ocrRoot, id))
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
        File(imgRoot, entry.id).deleteRecursively()
        File(ocrRoot, entry.id).deleteRecursively()
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

    // ---------- الكتابة اليدوية والصور المضافة ----------

    fun loadDocData(id: String): DocData {
        val f = File(annDir, "$id.json")
        if (!f.exists()) return DocData(emptyMap(), emptyMap())
        return try {
            val root = JSONObject(f.readText())

            val ink = HashMap<Int, List<Ink>>()
            val pages = root.optJSONObject("pages")
            if (pages != null) {
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
                        val kind = try {
                            Tool.valueOf(o.optString("k", "FOUNTAIN"))
                        } catch (e: Throwable) {
                            Tool.FOUNTAIN
                        }
                        val safeKind = if (kind == Tool.ERASER || kind == Tool.LASER) Tool.FOUNTAIN else kind
                        list.add(Ink(pts, Color(o.getInt("c")), o.getDouble("w").toFloat(), safeKind))
                    }
                    ink[key.toInt()] = list
                }
            }

            val images = HashMap<Int, List<ImageItem>>()
            val imgArr = root.optJSONArray("images")
            if (imgArr != null) {
                val all = ArrayList<ImageItem>()
                for (i in 0 until imgArr.length()) {
                    try {
                        val o = imgArr.getJSONObject(i)
                        all.add(
                            ImageItem(
                                id = o.getString("id"),
                                file = o.getString("file"),
                                page = o.getInt("page"),
                                x = o.getDouble("x").toFloat(),
                                y = o.getDouble("y").toFloat(),
                                w = o.getDouble("w").toFloat(),
                                aspect = o.getDouble("a").toFloat()
                            )
                        )
                    } catch (e: Throwable) {
                    }
                }
                all.groupBy { it.page }.forEach { (p, l) -> images[p] = l }
            }
            DocData(ink, images)
        } catch (e: Throwable) {
            try {
                f.copyTo(File(annDir, "$id.json.bad"), overwrite = true)
            } catch (x: Throwable) {
            }
            DocData(emptyMap(), emptyMap())
        }
    }

    suspend fun saveDocData(id: String, ink: Map<Int, List<Ink>>, images: Map<Int, List<ImageItem>>) {
        withContext(writer) {
            try {
                val pages = JSONObject()
                ink.forEach { (page, list) ->
                    if (list.isNotEmpty()) {
                        val arr = JSONArray()
                        list.forEach { s ->
                            val flat = JSONArray()
                            s.pts.forEach {
                                flat.put(it.x.toDouble())
                                flat.put(it.y.toDouble())
                                flat.put(it.p.toDouble())
                            }
                            arr.put(
                                JSONObject()
                                    .put("c", s.color.toArgb())
                                    .put("w", s.width.toDouble())
                                    .put("k", s.tool.name)
                                    .put("p", flat)
                            )
                        }
                        pages.put(page.toString(), arr)
                    }
                }
                val imgs = JSONArray()
                images.values.forEach { list ->
                    list.forEach { im ->
                        imgs.put(
                            JSONObject()
                                .put("id", im.id)
                                .put("file", im.file)
                                .put("page", im.page)
                                .put("x", im.x.toDouble())
                                .put("y", im.y.toDouble())
                                .put("w", im.w.toDouble())
                                .put("a", im.aspect.toDouble())
                        )
                    }
                }
                atomicWrite(
                    File(annDir, "$id.json"),
                    JSONObject().put("version", 2).put("pages", pages).put("images", imgs).toString()
                )
            } catch (e: Throwable) {
            }
        }
    }

    /** ينسخ الصورة إلى مساحة التطبيق بعد تصغيرها وتصحيح اتجاهها. خارج الخيط الرئيسي. */
    fun importImage(docId: String, uri: Uri): Pair<String, Float> {
        val dir = File(imgRoot, docId).apply { mkdirs() }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("صورة غير صالحة")

        var sample = 1
        while (bounds.outWidth / sample > 2000 || bounds.outHeight / sample > 2000) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IOException("تعذر قراءة الصورة")

        val orientation = try {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees != 0f) {
            val m = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (rotated !== bmp) bmp.recycle()
            bmp = rotated
        }

        val isPng = try {
            resolver.getType(uri) == "image/png"
        } catch (e: Throwable) {
            false
        }
        val name = UUID.randomUUID().toString() + if (isPng) ".png" else ".jpg"
        File(dir, name).outputStream().use { out ->
            if (isPng) bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            else bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        val aspect = bmp.width.toFloat() / bmp.height.toFloat()
        bmp.recycle()
        return Pair(name, aspect)
    }

    // ---------- القراءة الضوئية ----------

    fun saveOcrPage(id: String, page: Int, data: OcrPage) {
        try {
            val dir = File(ocrRoot, id).apply { mkdirs() }
            val arr = JSONArray()
            data.words.forEach { w ->
                arr.put(
                    JSONArray()
                        .put(w.text)
                        .put(w.l.toDouble())
                        .put(w.t.toDouble())
                        .put(w.r.toDouble())
                        .put(w.b.toDouble())
                        .put(w.line)
                )
            }
            atomicWrite(
                File(dir, "$page.json"),
                JSONObject().put("v", data.version).put("w", arr).toString()
            )
        } catch (e: Throwable) {
        }
    }

    fun loadOcrAll(id: String): Map<Int, OcrPage> {
        val out = HashMap<Int, OcrPage>()
        val files = File(ocrRoot, id).listFiles() ?: return out
        for (f in files) {
            val page = f.nameWithoutExtension.toIntOrNull() ?: continue
            try {
                val o = JSONObject(f.readText())
                val arr = o.getJSONArray("w")
                val words = ArrayList<OcrWord>(arr.length())
                for (i in 0 until arr.length()) {
                    val a = arr.getJSONArray(i)
                    words.add(
                        OcrWord(
                            a.getString(0),
                            a.getDouble(1).toFloat(),
                            a.getDouble(2).toFloat(),
                            a.getDouble(3).toFloat(),
                            a.getDouble(4).toFloat(),
                            a.getInt(5)
                        )
                    )
                }
                out[page] = OcrPage(words, o.getInt("v"))
            } catch (e: Throwable) {
            }
        }
        return out
    }
}
