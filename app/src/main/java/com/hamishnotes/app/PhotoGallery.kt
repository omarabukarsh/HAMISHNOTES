package com.hamishnotes.app

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** صورة في معرض الجهاز. */
class GalleryPhoto(val uri: Uri, val bucket: String)

/**
 * معرض صور الجهاز داخل التطبيق: الصور مباشرة (الأحدث أولًا) مع تبويبات وألبومات،
 * واختيار صورة أو عدة صور. يحتاج إذن قراءة الصور، وإن رُفض يبقى «من الملفات» متاحًا.
 */
object PhotoLibrary {
    private val thumbs = object : LruCache<String, ImageBitmap>(160) {}

    fun permissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun granted(ctx: Context, p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    /** 2 = كل الصور، 1 = بعض الصور فقط (Android 14)، 0 = بلا إذن. */
    fun access(ctx: Context): Int = when {
        Build.VERSION.SDK_INT >= 33 && granted(ctx, Manifest.permission.READ_MEDIA_IMAGES) -> 2
        Build.VERSION.SDK_INT >= 34 && granted(ctx, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> 1
        Build.VERSION.SDK_INT < 33 && granted(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) -> 2
        else -> 0
    }

    fun query(ctx: Context): List<GalleryPhoto> {
        val out = ArrayList<GalleryPhoto>()
        val base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        try {
            ctx.contentResolver.query(base, proj, null, null, MediaStore.Images.Media.DATE_ADDED + " DESC")?.use { c ->
                val id = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val b = c.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                while (c.moveToNext()) {
                    out.add(GalleryPhoto(ContentUris.withAppendedId(base, c.getLong(id)), if (b >= 0) c.getString(b).orEmpty() else ""))
                }
            }
        } catch (t: Throwable) {
        }
        return out
    }

    suspend fun thumbnail(ctx: Context, uri: Uri): ImageBitmap? {
        val key = uri.toString()
        thumbs.get(key)?.let { return it }
        val bmp = withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= 29) ctx.contentResolver.loadThumbnail(uri, Size(256, 256), null)
                else {
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
                    var s = 1
                    while (o.outWidth / s > 512 || o.outHeight / s > 512) s *= 2
                    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
                }
            } catch (t: Throwable) {
                null
            }
        } ?: return null
        return bmp.asImageBitmap().also { thumbs.put(key, it) }
    }

    /** يقرأ صورة كاملة بحجم معقول (لصنع ملصق منها). */
    fun loadBitmap(ctx: Context, uri: Uri, max: Int): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1
        while (o.outWidth / s > max * 2 || o.outHeight / s > max * 2) s *= 2
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
    } catch (t: Throwable) {
        null
    }
}

/**
 * نافذة معرض الصور. multi = اختيار عدة صور. onPicked يستلم الصور بترتيب اختيارها.
 */
@Composable
fun PhotoGalleryDialog(multi: Boolean, title: String, onPicked: (List<Uri>) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var access by remember { mutableStateOf(PhotoLibrary.access(ctx)) }
    var asked by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = PhotoLibrary.access(ctx)
        refresh++
    }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) onPicked(if (multi) uris else uris.take(1))
        onDismiss()
    }
    LaunchedEffect(Unit) {
        if (access == 0 && !asked) {
            asked = true
            permLauncher.launch(PhotoLibrary.permissions())
        }
    }
    val photos by produceState<List<GalleryPhoto>?>(null, access, refresh) {
        value = if (access == 0) emptyList() else withContext(Dispatchers.IO) { PhotoLibrary.query(ctx) }
    }
    // التبويب: null الكل، "cam" الكاميرا، "shot" لقطات الشاشة، وإلا اسم الألبوم
    var tab by remember { mutableStateOf<String?>(null) }
    var albumsOpen by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(listOf<Uri>()) }
    val all = photos ?: emptyList()
    val shown = when (tab) {
        null -> all
        "cam" -> all.filter { it.bucket.equals("Camera", true) || it.bucket.contains("DCIM", true) }
        "shot" -> all.filter { it.bucket.contains("screenshot", true) }
        else -> all.filter { it.bucket == tab }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.96f).height(620.dp), shape = RoundedCornerShape(22.dp), color = Color.White) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Palette.Navy, modifier = Modifier.weight(1f))
                    TextButton(onClick = { filePicker.launch(arrayOf("image/*")) }) { Text(L("من الملفات", "From files"), color = Palette.TextSecondary) }
                    if (multi) {
                        Text(
                            L("إضافة ${picked.size}", "Add ${picked.size}"), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = if (picked.isEmpty()) 0.6f else 1f),
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Palette.Navy.copy(alpha = if (picked.isEmpty()) 0.4f else 1f))
                                .clickable(enabled = picked.isNotEmpty()) { onPicked(picked); onDismiss() }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("✕", color = Palette.TextSecondary) }
                }
                if (access == 1) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(Palette.GoldSoft)
                            .clickable { permLauncher.launch(PhotoLibrary.permissions()) }.padding(10.dp)
                    ) {
                        Text(L("سمحت ببعض الصور فقط · المس لاختيار المزيد", "You allowed some photos only · tap to choose more"), fontSize = 13.sp, color = Palette.GoldDeep, fontWeight = FontWeight.Bold)
                    }
                }
                if (access != 0) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChoiceChip(L("الأحدث", "Recent"), tab == null) { tab = null }
                        ChoiceChip(L("الكاميرا", "Camera"), tab == "cam") { tab = "cam" }
                        ChoiceChip(L("لقطات الشاشة", "Screenshots"), tab == "shot") { tab = "shot" }
                        Box {
                            val albumLabel = tab?.takeIf { it != "cam" && it != "shot" }
                            ChoiceChip((albumLabel ?: L("الألبومات", "Albums")) + " ▾", albumLabel != null) { albumsOpen = true }
                            DropdownMenu(expanded = albumsOpen, onDismissRequest = { albumsOpen = false }) {
                                all.groupBy { it.bucket }.filterKeys { it.isNotBlank() }.toList().sortedByDescending { it.second.size }.forEach { (b, l) ->
                                    DropdownMenuItem(text = { Text("$b (${l.size})") }, onClick = { tab = b; albumsOpen = false })
                                }
                            }
                        }
                    }
                }
                when {
                    access == 0 -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(
                            L("اسمح لهامش بعرض صورك لتختار منها مباشرة.", "Allow Hamish to show your photos so you can pick them directly."),
                            fontSize = 15.sp, color = Palette.TextSecondary
                        )
                        Spacer(Modifier.height(10.dp))
                        TextButton(onClick = { permLauncher.launch(PhotoLibrary.permissions()) }) { Text(L("السماح بعرض الصور", "Allow photos"), fontWeight = FontWeight.Bold) }
                        TextButton(onClick = { filePicker.launch(arrayOf("image/*")) }) { Text(L("أو اختر من الملفات", "Or pick from files")) }
                    }
                    photos == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.Navy) }
                    shown.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(L("لا توجد صور هنا", "No photos here"), color = Palette.TextSecondary) }
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(104.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(shown, key = { it.uri.toString() }) { p ->
                            val n = picked.indexOf(p.uri)
                            GalleryCell(p.uri, n) {
                                if (!multi) {
                                    onPicked(listOf(p.uri))
                                    onDismiss()
                                } else {
                                    picked = if (n >= 0) picked - p.uri else picked + p.uri
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryCell(uri: Uri, order: Int, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val img by produceState<ImageBitmap?>(null, uri) { value = PhotoLibrary.thumbnail(ctx, uri) }
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(Color(0xFFEEF0F4))
            .then(if (order >= 0) Modifier.border(3.dp, Palette.Gold, RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = onClick)
    ) {
        img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        if (order >= 0) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(5.dp).size(22.dp).clip(CircleShape).background(Palette.Gold),
                contentAlignment = Alignment.Center
            ) { Text("${order + 1}", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}
