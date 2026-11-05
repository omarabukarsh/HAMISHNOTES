package com.hamishnotes.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * الملصقات: جاهزة (رسومات وإيموجي وشارات نصية)، و«ملصقاتي» التي يصنعها المستخدم من صورة
 * أو من رسمه. «ملصقاتي» صور PNG شفافة في مجلد التطبيق، فتبقى في كل المستندات.
 * الملصق يوضع على الصفحة كصورة (يُحرَّك ويُكبَّر مثل الصور).
 */
object StickerStore {
    private lateinit var app: Context
    val mine = mutableStateListOf<File>()

    fun init(ctx: Context) {
        app = ctx.applicationContext
        mine.clear()
        dir().listFiles { f -> f.name.endsWith(".png") }?.sortedByDescending { it.lastModified() }?.let { mine.addAll(it) }
    }

    private fun dir() = File(app.filesDir, "stickers").apply { mkdirs() }

    fun save(bmp: Bitmap): File {
        val f = File(dir(), UUID.randomUUID().toString() + ".png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        mine.add(0, f)
        return f
    }

    fun delete(f: File) {
        f.delete()
        mine.remove(f)
    }

    /** ملصق من صورة: مربع من وسطها بحواف مستديرة. */
    fun addFromUri(uri: Uri) {
        AppController.scope.launch {
            val ok = withContext(Dispatchers.IO) {
                val src = PhotoLibrary.loadBitmap(app, uri, 512) ?: return@withContext false
                val side = min(src.width, src.height)
                val out = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                val c = Canvas(out)
                val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                val clip = android.graphics.Path().apply { addRoundRect(RectF(0f, 0f, 512f, 512f), 64f, 64f, android.graphics.Path.Direction.CW) }
                c.clipPath(clip)
                c.drawBitmap(
                    src,
                    android.graphics.Rect((src.width - side) / 2, (src.height - side) / 2, (src.width + side) / 2, (src.height + side) / 2),
                    RectF(0f, 0f, 512f, 512f), p
                )
                src.recycle()
                withContext(Dispatchers.Main) { save(out) }
                true
            }
            AppController.messages.tryEmit(if (ok) L("أُضيف إلى «ملصقاتي»", "Added to “My stickers”") else L("تعذر قراءة الصورة", "Could not read the image"))
        }
    }

    /** ملصق من الكتابة المحددة بالتحديد الحر (بخلفية شفافة). */
    fun saveFromLasso(s: DocSession) {
        val sel = s.lassoSel ?: return
        val strokes = sel.strokes.filter { it.pts.isNotEmpty() }
        if (strokes.isEmpty()) {
            AppController.messages.tryEmit(L("حدّد رسمًا أو كتابة بالقلم أولًا", "Select a drawing or pen writing first"))
            return
        }
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (st in strokes) {
            val pad = st.width
            for (p in st.pts) {
                l = min(l, p.x - pad); r = max(r, p.x + pad); t = min(t, p.y - pad); b = max(b, p.y + pad)
            }
        }
        val span = max(r - l, b - t).coerceAtLeast(0.001f)
        // أطول ضلع 640 بكسل
        val k = 640f / span
        val bw = ((r - l) * k).toInt().coerceIn(8, 1024)
        val bh = ((b - t) * k).toInt().coerceIn(8, 1024)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.translate(-l * k, -t * k)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (st in strokes) {
            paint.color = st.color.toArgb()
            paint.alpha = when (st.tool) {
                Tool.HIGHLIGHTER -> 90
                Tool.PENCIL -> 200
                else -> 255
            }
            paint.strokeWidth = (st.width * k).coerceAtLeast(1.5f)
            if (st.pts.size == 1) {
                paint.style = Paint.Style.FILL
                c.drawCircle(st.pts[0].x * k, st.pts[0].y * k, paint.strokeWidth / 2f, paint)
                paint.style = Paint.Style.STROKE
            } else {
                c.drawPath(buildPath(st.pts, k, null).asAndroidPath(), paint)
            }
        }
        save(bmp)
        s.lassoSel = null
        AppController.messages.tryEmit(L("حُفظ الملصق في «ملصقاتي»، وتجده في أداة الملصقات", "Saved to “My stickers” — find it in the stickers tool"))
    }

    // ---------------------------------------------------------------- الملصقات الجاهزة

    /** ملصق جاهز: c: رسم غلاف، e: إيموجي، t: شارة نصية. */
    class Builtin(val kind: Char, val value: String, val color: Long = 0xFF1B365D)

    val categories: List<Pair<String, List<Builtin>>> by lazy {
        listOf(
            L("طب", "Medical") to CoverStickers.map { Builtin('c', it) },
            L("تنبيهات", "Labels") to listOf(
                Builtin('t', L("مهم", "Important"), 0xFFD9467A), Builtin('t', L("امتحان", "Exam"), 0xFF2F6FDB),
                Builtin('t', L("راجع", "Review"), 0xFFE08A1E), Builtin('t', L("سؤال", "Question"), 0xFF6D4AC2),
                Builtin('t', L("تعريف", "Definition"), 0xFF0F766E), Builtin('t', L("قاعدة", "Rule"), 0xFF1B365D),
                Builtin('t', L("مثال", "Example"), 0xFF2E9D6B), Builtin('t', L("ملاحظة", "Note"), 0xFF8C6A1F),
                Builtin('t', L("احفظ", "Memorize"), 0xFFB4232A), Builtin('t', "✓ " + L("تم", "Done"), 0xFF1F7A4D),
                Builtin('t', "!!", 0xFFB4232A), Builtin('t', "?", 0xFF6D4AC2)
            ),
            L("دراسة", "Study") to "📚 ✏️ 📝 📌 📎 🗂️ ⏰ 💡 ❓ ✅ ❌ ⚠️ 🎯 🔖 🧠 📊 📅 🏆 ⭐ 🔥".split(' ').map { Builtin('e', it) },
            L("علوم", "Science") to "🧪 🧬 🦠 🔬 🫀 🫁 🦷 🦴 💊 💉 🩺 ⚗️ 🌡️ 🧫 🩸 👁️ 🦴 ⚛️ 🧲 🌍".split(' ').distinct().map { Builtin('e', it) },
            L("وجوه", "Faces") to "😀 😅 😴 🤯 🥳 😎 🤔 😭 😍 🙃 👍 👏 🙏 💪 ❤️ 💯".split(' ').map { Builtin('e', it) }
        )
    }

    fun render(b: Builtin, px: Int = 400): Bitmap? = when (b.kind) {
        'c' -> coverStickerBitmap(b.value, px)
        'e' -> Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also { bmp ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = px * 0.8f; textAlign = Paint.Align.CENTER }
            val fm = p.fontMetrics
            Canvas(bmp).drawText(b.value, px / 2f, px / 2f - (fm.ascent + fm.descent) / 2f, p)
        }
        else -> {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 120f
                color = android.graphics.Color.WHITE
                isFakeBoldText = true
                FontStore.typeface("Tajawal")?.let { typeface = android.graphics.Typeface.create(it, android.graphics.Typeface.BOLD) }
            }
            val tw = p.measureText(b.value)
            val w = (tw + 120).toInt()
            val h = 200
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                val c = Canvas(bmp)
                val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = b.color.toInt() }
                c.drawRoundRect(RectF(6f, 6f, w - 6f, h - 6f), 60f, 60f, bg)
                val fm = p.fontMetrics
                c.drawText(b.value, (w - tw) / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, p)
            }
        }
    }

    /** يضع الملصق على الصفحة الظاهرة. */
    fun place(s: DocSession, bmp: Bitmap) {
        AppController.scope.launch {
            val f = withContext(Dispatchers.IO) {
                File(app.cacheDir, "sticker-" + UUID.randomUUID() + ".png").also { out -> out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            }
            val w = if (bmp.width > bmp.height * 1.5f) 0.26f else 0.16f
            AppController.insertImage(s, Uri.fromFile(f), s.listState.firstVisibleItemIndex, width = w, quiet = true)
        }
    }

    fun place(s: DocSession, f: File) {
        AppController.insertImage(s, Uri.fromFile(f), s.listState.firstVisibleItemIndex, width = 0.16f, quiet = true)
    }
}

/** أداة الملصقات: «ملصقاتي» والملصقات الجاهزة. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StickerDialog(s: DocSession, onDismiss: () -> Unit) {
    val cats = StickerStore.categories
    var tab by remember { mutableStateOf(if (StickerStore.mine.isNotEmpty()) -1 else 0) }
    var fromPhoto by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<File?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).height(560.dp), shape = RoundedCornerShape(22.dp), color = Palette.Background) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(L("الملصقات", "Stickers"), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Palette.Navy, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(L("إغلاق", "Close")) }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ChoiceChip("★ " + L("ملصقاتي", "My stickers") + " ${StickerStore.mine.size}", tab == -1) { tab = -1 }
                    cats.forEachIndexed { i, (name, _) -> ChoiceChip(name, tab == i) { tab = i } }
                }
                if (tab == -1) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(92.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item { AddTile("＋\n" + L("من صورة", "From photo")) { fromPhoto = true } }
                        item {
                            AddTile("＋\n" + L("من رسمي", "From my drawing")) {
                                onDismiss()
                            }
                        }
                        items(StickerStore.mine.toList(), key = { it.name }) { f ->
                            val img by produceState<ImageBitmap?>(null, f) {
                                value = withContext(Dispatchers.IO) { android.graphics.BitmapFactory.decodeFile(f.path)?.asImageBitmap() }
                            }
                            Tile(onClick = { StickerStore.place(s, f); onDismiss() }, onLong = { deleting = f }) {
                                img?.let { Image(it, null, Modifier.fillMaxSize().padding(8.dp)) }
                            }
                        }
                    }
                    Text(
                        L("اضغط مطوّلًا على ملصق لحذفه", "Long-press a sticker to delete it"),
                        fontSize = 11.sp, color = Palette.TextSecondary
                    )
                } else {
                    val list = cats.getOrNull(tab)?.second ?: emptyList()
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(if (list.firstOrNull()?.kind == 't') 120.dp else 76.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(list) { b ->
                            val img = remember(b) { StickerStore.render(b, 200)?.asImageBitmap() }
                            Tile(onClick = { StickerStore.render(b, 400)?.let { StickerStore.place(s, it) }; onDismiss() }) {
                                img?.let { Image(it, null, Modifier.fillMaxSize().padding(8.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (fromPhoto) {
        PhotoGalleryDialog(multi = false, title = L("اختر صورة للملصق", "Choose a photo for the sticker"), onPicked = { it.firstOrNull()?.let { u -> StickerStore.addFromUri(u) } }, onDismiss = { fromPhoto = false })
    }
    deleting?.let { f ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(L("حذف الملصق؟", "Delete sticker?")) },
            text = { Text(L("يُحذف من «ملصقاتي». ما وضعته منه على الصفحات يبقى.", "It is removed from “My stickers”. Copies already on pages stay.")) },
            confirmButton = { TextButton(onClick = dialogClose { StickerStore.delete(f); deleting = null }) { Text(L("حذف", "Delete"), color = Palette.Danger) } },
            dismissButton = { TextButton(onClick = dialogClose { deleting = null }) { Text(L("إلغاء", "Cancel")) } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(onClick: () -> Unit, onLong: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(androidx.compose.ui.graphics.Color.White)
            .border(1.dp, Palette.Divider, RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLong),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun AddTile(label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Palette.GoldSoft)
            .border(1.5.dp, Palette.Gold, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.GoldDeep, textAlign = TextAlign.Center) }
}
