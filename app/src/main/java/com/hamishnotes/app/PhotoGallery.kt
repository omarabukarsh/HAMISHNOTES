package com.hamishnotes.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * الصور في التطبيق دون أي إذن للوصول إلى صور الجهاز (سياسة Google Play للصور والفيديو):
 * يختار المستخدم الصور من أداة اختيار الصور في Android (Photo Picker)، فلا يرى التطبيق إلا ما اختاره.
 * كل صورة اختيرت تُنسخ إلى «صور استخدمتها» داخل التطبيق، لتُسحب إلى الصفحات مرة أخرى لاحقًا.
 */
object PhotoLibrary {
    private val thumbs = object : LruCache<String, ImageBitmap>(160) {}

    /** أقصى عدد للصور المحفوظة في «صور استخدمتها»؛ الأقدم استخدامًا يُحذف أولًا. */
    private const val MAX_RECENT = 40

    private lateinit var app: Context

    /** «صور استخدمتها»: الأحدث استخدامًا أولًا. */
    val recent = mutableStateListOf<File>()

    fun init(ctx: Context) {
        app = ctx.applicationContext
        recent.clear()
        dir().listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }?.let { recent.addAll(it) }
    }

    private fun dir() = File(app.filesDir, "recent_photos").apply { mkdirs() }

    fun uriOf(f: File): Uri = Uri.fromFile(f)

    /**
     * ينسخ الصور المختارة إلى «صور استخدمتها» (الصورة نفسها مرتين تُحفظ مرة واحدة)،
     * ويعيد روابط النسخ المحلية بنفس الترتيب. تُستدعى خارج الخيط الرئيسي.
     */
    fun keep(uris: List<Uri>): List<Uri> {
        val out = ArrayList<Uri>()
        for (u in uris) {
            val f = try {
                copyIn(u)
            } catch (t: Throwable) {
                null
            }
            out.add(f?.let { uriOf(it) } ?: u)
        }
        return out
    }

    private fun copyIn(uri: Uri): File? {
        val tmp = File(dir(), ".tmp-" + System.nanoTime())
        val md = MessageDigest.getInstance("SHA-256")
        app.contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                    output.write(buf, 0, n)
                }
            }
        } ?: return null
        val name = md.digest().joinToString("") { "%02x".format(it) }.take(32) + ".img"
        val f = File(dir(), name)
        if (f.exists()) tmp.delete() else if (!tmp.renameTo(f)) {
            tmp.delete()
            return null
        }
        f.setLastModified(System.currentTimeMillis())
        AppController.scope.launch(Dispatchers.Main) { touch(f) }
        return f
    }

    /** ينقل الصورة إلى أول القائمة (استُخدمت الآن)، ويحذف الزائد عن الحد. */
    private fun touch(f: File) {
        recent.remove(f)
        recent.add(0, f)
        while (recent.size > MAX_RECENT) recent.removeAt(recent.lastIndex).delete()
    }

    fun used(f: File) {
        f.setLastModified(System.currentTimeMillis())
        touch(f)
    }

    fun delete(f: File) {
        thumbs.remove(uriOf(f).toString())
        recent.remove(f)
        f.delete()
    }

    suspend fun thumbnail(ctx: Context, uri: Uri): ImageBitmap? {
        val key = uri.toString()
        thumbs.get(key)?.let { return it }
        val bmp = withContext(Dispatchers.IO) { loadBitmap(ctx, uri, 128) } ?: return null
        return bmp.asImageBitmap().also { thumbs.put(key, it) }
    }

    /** يقرأ صورة بحجم معقول (لا يقل عن max*2 إلا إن كانت الصورة أصغر). */
    fun loadBitmap(ctx: Context, uri: Uri, max: Int): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1
        while (o.outWidth / s > max * 2 || o.outHeight / s > max * 2) s *= 2
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
    } catch (t: Throwable) {
        null
    }

    /** طلب أداة اختيار الصور: صور فقط. */
    val imagesOnly: PickVisualMediaRequest
        get() = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
}

/**
 * أداة اختيار الصور في Android. onPicked يستلم نسخًا محلية من الصور المختارة (بعد حفظها في «صور استخدمتها»).
 * multi = اختيار عدة صور بالترتيب.
 */
@Composable
fun rememberPhotoPicker(multi: Boolean, onPicked: (List<Uri>) -> Unit): () -> Unit {
    val handle: (List<Uri>) -> Unit = { uris ->
        if (uris.isNotEmpty()) AppController.scope.launch {
            val local = withContext(Dispatchers.IO) { PhotoLibrary.keep(uris) }
            onPicked(local)
        }
    }
    if (multi) {
        val l: ManagedActivityResultLauncher<PickVisualMediaRequest, List<Uri>> =
            rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { handle(it) }
        return { l.launch(PhotoLibrary.imagesOnly) }
    }
    val l: ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?> =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u -> handle(listOfNotNull(u)) }
    return { l.launch(PhotoLibrary.imagesOnly) }
}
