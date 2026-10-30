package com.mynotes.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.IO
import kotlinx.coroutines.Dispatchers

class DocData(
    val ink: Map<Int, List<Ink>>,
    val images: Map<Int, List<ImageItem>>,
    val marks: Map<Int, List<TextMark>> = emptyMap(),
    val texts: Map<Int, List<TextBox>> = emptyMap(),
    val edits: SummaryEdits = SummaryEdits(),
    /** بطاقات صنعها المستخدم بنفسه. */
    val cards: List<Card> = emptyList(),
    /** معرّفات البطاقات المميزة بنجمة (صعبة). */
    val starred: Set<String> = emptySet(),
    /** الصفحات المعلَّمة (أرقام من 0). */
    val bookmarks: Set<Int> = emptySet(),
    /** الملاحظات اللاصقة لكل صفحة. */
    val notes: Map<Int, List<StickyNote>> = emptyMap()
)

/**
 * كل الملفات داخل مساحة التطبيق الخاصة:
 *  files/docs/<id>.pdf          نسخة المستند المستورد
 *  files/annotations/<id>.json  الكتابة اليدوية والصور والتمييزات الملتقطة ومربعات النص
 *  files/images/<id>/           ملفات الصور المضافة
 *  files/ocr/<id>/<page>.json   نتيجة القراءة الضوئية لكل صفحة
 *  files/thumbs/<id>.jpg        الصورة المصغرة
 *  files/library.json           فهرس المكتبة
 */
class LibraryRepository(context: Context) {
    private val root = context.filesDir
    private val docsDir = File(root, "docs").apply { mkdirs() }
    private val annDir = File(root, "annotations").apply { mkdirs() }
    private val thumbDir = File(root, "thumbs").apply { mkdirs() }
    private val imgRoot = File(root, "images").apply { mkdirs() }
    private val ocrRoot = File(root, "ocr").apply { mkdirs() }
    private val partsRoot = File(root, "parts").apply { mkdirs() }
    private val pagesDir = File(root, "pages").apply { mkdirs() }
    private val indexFile = File(root, "library.json")
    private val statsFile = File(root, "stats.json")
    private val srsDir = File(root, "srs").apply { mkdirs() }

    // كاتب واحد بالترتيب (مثل خيط الكتابة في أندرويد)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val writer = Dispatchers.IO.limitedParallelism(1)

    private val thumbCache = LruCache<String, Bitmap>(40)

    fun file(entry: DocEntry): File = File(docsDir, entry.fileName)

    fun imageFile(docId: String, name: String): File = File(File(imgRoot, docId), name)

    fun partsDir(docId: String): File = File(partsRoot, docId)

    // ---------- أدوات مساعدة ----------

    private fun atomicWrite(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    private fun queryName(uri: Uri): String? = uri.lastPathSegment.takeIf { it.isNotBlank() }

    private fun probePageCount(f: File): Int = IosPdf.pageCount(f.path)

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
                        lastPage = o.optInt("page"),
                        isNote = o.optBoolean("note"),
                        readMs = o.optLong("read"),
                        folder = o.optString("folder", ""),
                        locked = o.optBoolean("lock", false),
                        coverColor = o.optInt("cover", 0),
                        sticker = o.optString("sticker", "")
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
                            .put("note", e.isNote)
                            .put("read", e.readMs)
                            .put("folder", e.folder)
                            .put("lock", e.locked)
                            .put("cover", e.coverColor)
                            .put("sticker", e.sticker)
                    )
                }
                atomicWrite(indexFile, JSONObject().put("version", 1).put("docs", arr).toString())
            } catch (e: Throwable) {
            }
        }
    }

    // ---------- إحصاءات الدراسة ----------

    /** مفاتيح: "2026-10-22" مجموع اليوم، و"2026-10-22|<id>" وقت مستند في ذلك اليوم (مللي ثانية). */
    fun loadStats(): Map<String, Long> {
        if (!statsFile.exists()) return emptyMap()
        return try {
            val o = JSONObject(statsFile.readText())
            val out = HashMap<String, Long>()
            for (k in o.keys()) out[k] = o.optLong(k)
            out
        } catch (e: Throwable) {
            emptyMap()
        }
    }

    suspend fun saveStats(stats: Map<String, Long>) {
        withContext(writer) {
            try {
                val o = JSONObject()
                stats.forEach { (k, v) -> o.put(k, v) }
                atomicWrite(statsFile, o.toString())
            } catch (e: Throwable) {
            }
        }
    }

    // ---------- بطاقات المراجعة (المراجعة المتباعدة) ----------

    fun loadSrs(docId: String): Map<String, SrsState> {
        val f = File(srsDir, "$docId.json")
        if (!f.exists()) return emptyMap()
        return try {
            val o = JSONObject(f.readText())
            val out = HashMap<String, SrsState>()
            for (k in o.keys()) {
                val a = o.getJSONArray(k)
                out[k] = SrsState(a.getDouble(0).toFloat(), a.getInt(1), a.getLong(2), a.getInt(3), a.optLong(4, -1L))
            }
            out
        } catch (e: Throwable) {
            emptyMap()
        }
    }

    suspend fun saveSrs(docId: String, data: Map<String, SrsState>) {
        withContext(writer) {
            try {
                val o = JSONObject()
                data.forEach { (k, s) ->
                    o.put(k, JSONArray().put(s.ease.toDouble()).put(s.interval).put(s.due).put(s.reps).put(s.intro))
                }
                atomicWrite(File(srsDir, "$docId.json"), o.toString())
            } catch (e: Throwable) {
            }
        }
    }

    // ---------- الاستيراد والنسخ والحذف ----------

    /** ينسخ الملف إلى مساحة التطبيق ويتحقق أنه سليم. يجب استدعاؤها خارج الخيط الرئيسي. */
    fun importFile(uri: Uri): DocEntry {
        val displayName = queryName(uri) ?: "document.pdf"
        val ext = displayName.substringAfterLast('.', "").lowercase()
        val type = when {
            ext == "pdf" -> DocType.PDF
            else -> DocType.fromExt(ext)
        }
        if (type == null || !type.supported) throw IOException(L("نوع الملف غير مدعوم بعد", "This file type is not supported yet"))

        val id = UUID.randomUUID().toString()
        val fileName = "$id.${type.ext}"
        val dest = File(docsDir, fileName)
        try {
            if (!File(uri.path).exists()) throw IOException(L("تعذر قراءة الملف", "Could not read the file"))
            File(uri.path).copyTo(dest, overwrite = true)
            // نسخة «قابلة للتعديل» من هامش: تُستعاد تعليقاتها قابلة للتعديل
            if (type == DocType.PDF) {
                EditablePdf.restore(dest, { json -> atomicWrite(File(annDir, "$id.json"), json) }, File(imgRoot, id))
            }
            val pages = probePageCount(dest)
            if (pages <= 0) throw IOException(L("الملف فارغ", "The file is empty"))
            val now = System.currentTimeMillis()
            val entry = DocEntry(
                id = id,
                name = displayName.substringBeforeLast('.', displayName).ifBlank { L("مستند", "Document") },
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
        File(pagesDir, "${src.id}.json").takeIf { it.exists() }?.copyTo(File(pagesDir, "$id.json"))
        File(partsRoot, src.id).takeIf { it.exists() }?.copyRecursively(File(partsRoot, id))
        return src.copy(
            id = id,
            name = src.name + suffix,
            fileName = fileName,
            addedAt = System.currentTimeMillis(),
            lastOpened = 0L,
            favorite = false,
            lastPage = 0,
            readMs = 0L
        )
    }

    fun delete(entry: DocEntry) {
        file(entry).delete()
        File(annDir, "${entry.id}.json").delete()
        File(thumbDir, "${entry.id}.jpg").delete()
        File(imgRoot, entry.id).deleteRecursively()
        File(ocrRoot, entry.id).deleteRecursively()
        File(pagesDir, "${entry.id}.json").delete()
        File(partsRoot, entry.id).deleteRecursively()
        File(srsDir, "${entry.id}.json").delete()
        thumbCache.remove(entry.id)
        PageCache.removePrefix("${entry.id}:")
    }

    // ---------- الصور المصغرة ----------

    fun thumbnail(entry: DocEntry): Bitmap? {
        thumbCache.get(entry.id)?.let { return it }
        val f = File(thumbDir, entry.id + ".jpg")
        if (!f.exists()) makeThumbnail(entry)
        if (!f.exists()) return null
        val b = BitmapFactory.decodeFile(f.absolutePath)?.also { it.skia.setImmutable() }
        if (b != null) thumbCache.put(entry.id, b)
        return b
    }

    private fun makeThumbnail(entry: DocEntry) {
        try {
            val d = IosPdf.openDocument(file(entry).path) ?: return
            try {
                val (pw, ph) = IosPdf.pageSize(d, 0)
                val w = 420
                val h = (w * ph / pw).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                IosPdf.renderPage(d, 0, bmp, w.toFloat(), 0f, 0f, white = false)
                bmp.compressTo(Bitmap.CompressFormat.JPEG, 85, File(thumbDir, entry.id + ".jpg"))
                bmp.recycle()
            } finally {
                platform.CoreGraphics.CGPDFDocumentRelease(d)
            }
        } catch (e: Throwable) {
        }
    }

    // ---------- الكتابة اليدوية والصور المضافة ----------

    fun loadDocData(id: String): DocData {
        val f = File(annDir, "$id.json")
        if (!f.exists()) return DocData(emptyMap(), emptyMap())
        return try {
            parseDocData(f.readText())
        } catch (e: Throwable) {
            try {
                f.copyTo(File(annDir, "$id.json.bad"), overwrite = true)
            } catch (x: Throwable) {
            }
            DocData(emptyMap(), emptyMap())
        }
    }

    /** يقرأ بيانات المستند من نص JSON (يرمي استثناءً إن كان تالفًا). */
    fun parseDocData(text: String): DocData {
        run {
            val root = JSONObject(text)

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
                        list.add(Ink(pts, Color(o.getInt("c")), o.getDouble("w").toFloat(), safeKind, o.optLong("t", 0L), o.optInt("ls", 0), o.optBoolean("sh", false)))
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

            val marks = HashMap<Int, List<TextMark>>()
            val markArr = root.optJSONArray("marks")
            if (markArr != null) {
                val all = ArrayList<TextMark>()
                for (i in 0 until markArr.length()) {
                    try {
                        val o = markArr.getJSONObject(i)
                        val flat = o.getJSONArray("r")
                        val rects = ArrayList<MarkRect>(flat.length() / 4)
                        var j = 0
                        while (j + 3 < flat.length()) {
                            rects.add(
                                MarkRect(
                                    flat.getDouble(j).toFloat(),
                                    flat.getDouble(j + 1).toFloat(),
                                    flat.getDouble(j + 2).toFloat(),
                                    flat.getDouble(j + 3).toFloat()
                                )
                            )
                            j += 4
                        }
                        val em = o.optJSONArray("em")
                        val sr = o.optJSONArray("sr")
                        val strong = ArrayList<MarkRect>()
                        if (sr != null) {
                            var k = 0
                            while (k + 3 < sr.length()) {
                                strong.add(
                                    MarkRect(
                                        sr.getDouble(k).toFloat(), sr.getDouble(k + 1).toFloat(),
                                        sr.getDouble(k + 2).toFloat(), sr.getDouble(k + 3).toFloat()
                                    )
                                )
                                k += 4
                            }
                        }
                        all.add(
                            TextMark(
                                id = o.getString("id"),
                                page = o.getInt("page"),
                                rects = rects,
                                color = Color(o.getInt("c")),
                                text = o.optString("t", ""),
                                createdAt = o.optLong("at"),
                                level = o.optInt("lv", 1),
                                emphasis = if (em == null) emptyList() else (0 until em.length()).map { em.getString(it) },
                                strongRects = strong,
                                inSummary = o.optBoolean("sum", true)
                            )
                        )
                    } catch (e: Throwable) {
                    }
                }
                all.groupBy { it.page }.forEach { (p, l) -> marks[p] = l }
            }

            val texts = HashMap<Int, List<TextBox>>()
            val textArr = root.optJSONArray("texts")
            if (textArr != null) {
                val all = ArrayList<TextBox>()
                for (i in 0 until textArr.length()) {
                    try {
                        val o = textArr.getJSONObject(i)
                        all.add(
                            TextBox(
                                id = o.getString("id"),
                                page = o.getInt("page"),
                                x = o.getDouble("x").toFloat(),
                                y = o.getDouble("y").toFloat(),
                                w = o.getDouble("w").toFloat(),
                                text = o.optString("t", ""),
                                color = Color(o.getInt("c")),
                                size = o.getDouble("s").toFloat(),
                                sticky = o.optBoolean("st", false),
                                linkDoc = o.optString("ld", "").ifEmpty { null },
                                linkPage = o.optInt("lp", 0)
                            )
                        )
                    } catch (e: Throwable) {
                    }
                }
                all.groupBy { it.page }.forEach { (p, l) -> texts[p] = l }
            }
            val cards = root.optJSONArray("cards")?.let { a ->
                (0 until a.length()).mapNotNull { i ->
                    try {
                        val o = a.getJSONObject(i)
                        Card(o.getString("id"), o.getString("f"), o.getString("b"))
                    } catch (e: Throwable) {
                        null
                    }
                }
            } ?: emptyList()
            val starred = root.optJSONArray("starred")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
            val bookmarks = root.optJSONArray("bookmarks")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: emptySet()
            val notes = HashMap<Int, List<StickyNote>>()
            root.optJSONArray("notes")?.let { a ->
                val all = ArrayList<StickyNote>()
                for (i in 0 until a.length()) {
                    try {
                        val o = a.getJSONObject(i)
                        val inks = ArrayList<Ink>()
                        o.optJSONArray("ink")?.let { ia ->
                            for (k in 0 until ia.length()) {
                                val so = ia.getJSONObject(k)
                                val flat = so.getJSONArray("p")
                                val pts = ArrayList<Pt>(flat.length() / 3)
                                var j = 0
                                while (j + 2 < flat.length()) {
                                    pts.add(Pt(flat.getDouble(j).toFloat(), flat.getDouble(j + 1).toFloat(), flat.getDouble(j + 2).toFloat()))
                                    j += 3
                                }
                                val kind = try { Tool.valueOf(so.optString("k", "FOUNTAIN")) } catch (e: Throwable) { Tool.FOUNTAIN }
                                inks.add(Ink(pts, Color(so.getInt("c")), so.getDouble("w").toFloat(), kind, so.optLong("t", 0L), so.optInt("ls", 0), so.optBoolean("sh", false)))
                            }
                        }
                        all.add(
                            StickyNote(
                                id = o.getString("id"), page = o.getInt("page"),
                                x = o.getDouble("x").toFloat(), y = o.getDouble("y").toFloat(),
                                w = o.getDouble("w").toFloat(), h = o.getDouble("h").toFloat(),
                                color = o.getInt("c"), style = o.optInt("st", 0),
                                collapsed = o.optBoolean("cl", false), author = o.optString("a", ""),
                                ink = inks, text = o.optString("tx", "")
                            )
                        )
                    } catch (e: Throwable) {
                    }
                }
                all.groupBy { it.page }.forEach { (p, l) -> notes[p] = l }
            }
            return DocData(ink, images, marks, texts, parseEdits(root.optJSONObject("summary")), cards, starred, bookmarks, notes)
        }
    }

    /** نص JSON لبيانات المستند (الكتابة، الصور، التمييز، النصوص، الملاحظات…). */
    fun docDataJson(data: DocData): String {
        val ink = data.ink
        val images = data.images
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
                                .put("t", s.t)
                                .apply { if (s.style != 0) put("ls", s.style); if (s.shape) put("sh", true) }
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
            val marks = JSONArray()
            data.marks.values.forEach { list ->
                list.forEach { m ->
                    val flat = JSONArray()
                    m.rects.forEach {
                        flat.put(it.l.toDouble())
                        flat.put(it.t.toDouble())
                        flat.put(it.r.toDouble())
                        flat.put(it.b.toDouble())
                    }
                    marks.put(
                        JSONObject()
                            .put("id", m.id)
                            .put("page", m.page)
                            .put("c", m.color.toArgb())
                            .put("t", m.text)
                            .put("at", m.createdAt)
                            .put("r", flat)
                            .put("lv", m.level)
                            .put("sum", m.inSummary)
                            .put("em", JSONArray().apply { m.emphasis.forEach { put(it) } })
                            .put("sr", JSONArray().apply {
                                m.strongRects.forEach {
                                    put(it.l.toDouble()); put(it.t.toDouble()); put(it.r.toDouble()); put(it.b.toDouble())
                                }
                            })
                    )
                }
            }
            val texts = JSONArray()
            data.texts.values.forEach { list ->
                list.forEach { tb ->
                    texts.put(
                        JSONObject()
                            .put("id", tb.id)
                            .put("page", tb.page)
                            .put("x", tb.x.toDouble())
                            .put("y", tb.y.toDouble())
                            .put("w", tb.w.toDouble())
                            .put("t", tb.text)
                            .put("c", tb.color.toArgb())
                            .put("s", tb.size.toDouble())
                            .put("st", tb.sticky)
                            .put("ld", tb.linkDoc ?: "")
                            .put("lp", tb.linkPage)
                    )
                }
            }
            return JSONObject().put("version", 3).put("pages", pages).put("images", imgs)
                    .put("marks", marks).put("texts", texts).put("summary", editsJson(data.edits))
                    .put("cards", JSONArray().apply {
                        data.cards.forEach { c -> put(JSONObject().put("id", c.id).put("f", c.front).put("b", c.back)) }
                    })
                    .put("starred", JSONArray().apply { data.starred.forEach { put(it) } })
                    .put("bookmarks", JSONArray().apply { data.bookmarks.sorted().forEach { put(it) } })
                    .put("notes", JSONArray().apply {
                        data.notes.values.forEach { list ->
                            list.forEach { n ->
                                put(
                                    JSONObject().put("id", n.id).put("page", n.page)
                                        .put("x", n.x.toDouble()).put("y", n.y.toDouble())
                                        .put("w", n.w.toDouble()).put("h", n.h.toDouble())
                                        .put("c", n.color).put("st", n.style).put("cl", n.collapsed).put("a", n.author).put("tx", n.text)
                                        .put("ink", JSONArray().apply {
                                            n.ink.forEach { s ->
                                                val flat = JSONArray()
                                                s.pts.forEach { flat.put(it.x.toDouble()); flat.put(it.y.toDouble()); flat.put(it.p.toDouble()) }
                                                put(
                                                    JSONObject().put("c", s.color.toArgb()).put("w", s.width.toDouble())
                                                        .put("k", s.tool.name).put("t", s.t)
                                                        .apply { if (s.style != 0) put("ls", s.style); if (s.shape) put("sh", true) }
                                                        .put("p", flat)
                                                )
                                            }
                                        })
                                )
                            }
                        }
                    })
                    .toString()
    }

    suspend fun saveDocData(id: String, data: DocData) {
        withContext(writer) {
            try {
                atomicWrite(File(annDir, "$id.json"), docDataJson(data))
            } catch (e: Throwable) {
            }
        }
    }

    private fun parseEdits(o: JSONObject?): SummaryEdits {
        if (o == null) return SummaryEdits()
        return try {
            val order = o.optJSONArray("order")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            val kinds = HashMap<String, String>()
            o.optJSONObject("kinds")?.let { k -> for (key in k.keys()) kinds[key] = k.getString(key) }
            val tables = o.optJSONArray("tables")?.let { a ->
                (0 until a.length()).map { i ->
                    val t = a.getJSONObject(i)
                    val ks = t.getJSONArray("keys")
                    val rowsArr = t.optJSONArray("rows")
                    TableGroup(
                        id = t.getString("id"),
                        keys = (0 until ks.length()).map { ks.getString(it) },
                        oneRow = t.optBoolean("row"),
                        cols = t.optInt("cols", 0),
                        rows = rowsArr?.let { ra ->
                            (0 until ra.length()).map { r ->
                                val cells = ra.getJSONArray(r)
                                (0 until cells.length()).map { cells.getString(it) }
                            }
                        },
                        header = t.optBoolean("header", true)
                    )
                }
            } ?: emptyList()
            SummaryEdits(order, kinds, tables)
        } catch (e: Throwable) {
            SummaryEdits()
        }
    }

    private fun editsJson(e: SummaryEdits): JSONObject {
        val kinds = JSONObject()
        e.kinds.forEach { (k, v) -> kinds.put(k, v) }
        val tables = JSONArray()
        e.tables.forEach { g ->
            tables.put(
                JSONObject().put("id", g.id).put("row", g.oneRow).put("cols", g.cols).put("header", g.header)
                    .put("keys", JSONArray().apply { g.keys.forEach { put(it) } })
                    .apply {
                        val rows = g.rows
                        if (rows != null) {
                            put("rows", JSONArray().apply { rows.forEach { r -> put(JSONArray().apply { r.forEach { put(it) } }) } })
                        }
                    }
            )
        }
        return JSONObject()
            .put("order", JSONArray().apply { e.order.forEach { put(it) } })
            .put("kinds", kinds)
            .put("tables", tables)
    }

    /** ينسخ الصورة إلى مساحة التطبيق بعد تصغيرها وتصحيح اتجاهها. خارج الخيط الرئيسي. */
    fun importImage(docId: String, uri: Uri): Pair<String, Float> {
        val dir = File(imgRoot, docId).apply { mkdirs() }
        // UIImage يقرأ اتجاه الكاميرا (EXIF)، والرسم يعيد الصورة معتدلة ومصغّرة
        val (bytes, aspect) = IosImages.normalized(uri.path, 2000) ?: throw IOException(L("صورة غير صالحة", "Invalid image"))
        val isPng = uri.path.lowercase().endsWith(".png")
        val name = UUID.randomUUID().toString() + if (isPng) ".png" else ".jpg"
        File(dir, name).writeBytes(if (isPng) bytes.png else bytes.jpeg)
        return Pair(name, aspect)
    }

    // ---------- القراءة الضوئية ----------

    // ---------- بنية الصفحات (إضافة صفحات وملفات) ----------

    suspend fun savePages(id: String, pages: List<PageRef>) {
        withContext(writer) {
            try {
                val arr = JSONArray()
                pages.forEach {
                    arr.put(
                        JSONObject()
                            .put("s", it.src)
                            .put("i", it.index)
                            .put("r", it.ratio.toDouble())
                    )
                }
                atomicWrite(
                    File(pagesDir, "$id.json"),
                    JSONObject().put("version", 1).put("pages", arr).toString()
                )
            } catch (e: Throwable) {
            }
        }
    }

    fun loadPages(id: String): List<PageRef>? {
        val f = File(pagesDir, "$id.json")
        if (!f.exists()) return null
        return try {
            val arr = JSONObject(f.readText()).getJSONArray("pages")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PageRef(o.getString("s"), o.getInt("i"), o.getDouble("r").toFloat())
            }
        } catch (e: Throwable) {
            null
        }
    }

    /** ينسخ ملف PDF مضافًا إلى مجلد المستند. يعيد (اسم الملف، عدد الصفحات). خارج الخيط الرئيسي. */
    fun importPart(docId: String, uri: Uri): Pair<String, Int> {
        val dir = partsDir(docId).apply { mkdirs() }
        val name = UUID.randomUUID().toString() + ".pdf"
        val dest = File(dir, name)
        try {
            if (!File(uri.path).exists()) throw IOException(L("تعذر قراءة الملف", "Could not read the file"))
            File(uri.path).copyTo(dest, overwrite = true)
            val pages = probePageCount(dest)
            if (pages <= 0) throw IOException(L("الملف فارغ", "The file is empty"))
            return Pair(name, pages)
        } catch (t: Throwable) {
            dest.delete()
            throw t
        }
    }

    /** ينشئ ملاحظة جديدة: ملف PDF من صفحة A4 واحدة بالقالب المختار. خارج الخيط الرئيسي. */
    fun createNote(name: String, template: String, color: Int, pw: Int = 595, ph: Int = 842): DocEntry {
        val id = UUID.randomUUID().toString()
        val fileName = "$id.pdf"
        val dest = File(docsDir, fileName)
        try {
            // القالب يُرسم بدقة مضاعفة كصورة داخل الصفحة
            val k = 2
            val bmp = Bitmap.createBitmap(pw * k, ph * k, Bitmap.Config.ARGB_8888)
            Templates.draw(android.graphics.Canvas(bmp), (pw * k).toFloat(), (ph * k).toFloat(), template, color)
            val jpg = bmp.encode(Bitmap.CompressFormat.JPEG, 92) ?: throw IOException("encode")
            bmp.recycle()
            val pdf = IosPdf.Writer(dest.path)
            try {
                pdf.beginPage(pw.toDouble(), ph.toDouble())
                val img = IosPdf.jpegImage(jpg) ?: throw IOException("image")
                pdf.drawImage(img, 0.0, 0.0, pw.toDouble(), ph.toDouble())
                IosPdf.release(img)
                pdf.endPage()
            } finally {
                pdf.close()
            }
        } catch (t: Throwable) {
            dest.delete()
            throw t
        }
        val entry = DocEntry(
            id = id,
            name = name.trim().ifBlank { L("ملاحظة جديدة", "New note") },
            type = DocType.PDF,
            fileName = fileName,
            sizeBytes = dest.length(),
            pageCount = 1,
            addedAt = System.currentTimeMillis(),
            lastOpened = 0L,
            favorite = false,
            lastPage = 0,
            isNote = true
        )
        makeThumbnail(entry)
        return entry
    }

    /** يعيد كتابة نتائج القراءة الضوئية كلها (بعد إزاحة أرقام الصفحات). خارج الخيط الرئيسي. */
    fun rewriteOcr(id: String, data: Map<Int, OcrPage>) {
        File(ocrRoot, id).deleteRecursively()
        data.forEach { (page, ocrPage) -> saveOcrPage(id, page, ocrPage) }
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
