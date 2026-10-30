package android.graphics

import java.io.File
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.PaintStrokeJoin
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect as SkRect
import org.jetbrains.skia.SurfaceProps

/*
 * بدائل أصناف android.graphics المستخدمة في التطبيق، فوق Skia (نفس محرك رسم Compose في iOS).
 * الصور بصيغة RGBA_8888 حتى يرسم فيها CoreGraphics مباشرة (صفحات PDF).
 */

class Bitmap internal constructor(val skia: org.jetbrains.skia.Bitmap) {
    enum class Config { ARGB_8888, RGB_565, ALPHA_8 }
    enum class CompressFormat { JPEG, PNG, WEBP }

    val width: Int get() = skia.width
    val height: Int get() = skia.height
    val byteCount: Int get() = width * height * 4
    val allocationByteCount: Int get() = byteCount
    val config: Config get() = Config.ARGB_8888
    var isRecycled: Boolean = false
        private set

    fun eraseColor(c: Int) {
        skia.erase(c)
        skia.notifyPixelsChanged()
    }

    fun recycle() {
        if (!isRecycled) {
            isRecycled = true
            skia.close()
        }
    }

    fun copy(@Suppress("UNUSED_PARAMETER") cfg: Config?, @Suppress("UNUSED_PARAMETER") mutable: Boolean): Bitmap? {
        val b = createBitmap(width, height, Config.ARGB_8888)
        Canvas(b).drawBitmap(this, 0f, 0f, null)
        return b
    }

    /** يرمّز الصورة (JPEG أو PNG). */
    fun encode(format: CompressFormat, quality: Int): ByteArray? {
        val img = Image.makeFromBitmap(skia)
        try {
            val f = when (format) {
                CompressFormat.PNG -> EncodedImageFormat.PNG
                CompressFormat.WEBP -> EncodedImageFormat.WEBP
                CompressFormat.JPEG -> EncodedImageFormat.JPEG
            }
            return img.encodeToData(f, quality)?.bytes
        } finally {
            img.close()
        }
    }

    /** يحفظ الصورة في ملف (بديل compress إلى OutputStream). */
    fun compressTo(format: CompressFormat, quality: Int, file: File): Boolean {
        val bytes = encode(format, quality) ?: return false
        file.writeBytes(bytes)
        return true
    }

    companion object {
        fun info(w: Int, h: Int) = ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.PREMUL)

        fun createBitmap(w: Int, h: Int, @Suppress("UNUSED_PARAMETER") cfg: Config): Bitmap {
            val b = org.jetbrains.skia.Bitmap()
            if (!b.allocPixels(info(w.coerceAtLeast(1), h.coerceAtLeast(1)))) throw OutOfMemoryError()
            b.erase(0)
            return Bitmap(b)
        }

        fun createScaledBitmap(src: Bitmap, w: Int, h: Int, @Suppress("UNUSED_PARAMETER") filter: Boolean): Bitmap {
            val b = createBitmap(w, h, Config.ARGB_8888)
            Canvas(b).drawBitmap(src, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            return b
        }

        fun fromImage(img: Image): Bitmap {
            val b = createBitmap(img.width, img.height, Config.ARGB_8888)
            val c = org.jetbrains.skia.Canvas(b.skia, SurfaceProps())
            try { c.drawImage(img, 0f, 0f) } finally { c.close() }
            return b
        }
    }
}

class OutOfMemoryError : Error("Out of memory")

object BitmapFactory {
    class Options {
        var inJustDecodeBounds = false
        var inSampleSize = 1
        var inPreferredConfig: Bitmap.Config = Bitmap.Config.ARGB_8888
        var outWidth = 0
        var outHeight = 0
    }

    fun decodeByteArray(data: ByteArray, offset: Int, length: Int, opts: Options? = null): Bitmap? {
        val bytes = if (offset == 0 && length == data.size) data else data.copyOfRange(offset, offset + length)
        val img = try {
            Image.makeFromEncoded(bytes)
        } catch (t: Throwable) {
            return null
        }
        try {
            opts?.outWidth = img.width
            opts?.outHeight = img.height
            if (opts?.inJustDecodeBounds == true) return null
            val s = (opts?.inSampleSize ?: 1).coerceAtLeast(1)
            if (s == 1) return Bitmap.fromImage(img)
            val w = (img.width / s).coerceAtLeast(1)
            val h = (img.height / s).coerceAtLeast(1)
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = org.jetbrains.skia.Canvas(b.skia, SurfaceProps())
            try { c.drawImageRect(img, SkRect.makeWH(w.toFloat(), h.toFloat())) } finally { c.close() }
            return b
        } finally {
            img.close()
        }
    }

    fun decodeFile(path: String, opts: Options? = null): Bitmap? = try {
        val bytes = File(path).readBytes()
        decodeByteArray(bytes, 0, bytes.size, opts)
    } catch (t: Throwable) {
        null
    }
}

object Color {
    const val WHITE = -0x1
    const val BLACK = -0x1000000
    const val TRANSPARENT = 0
    fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b
    fun rgb(r: Int, g: Int, b: Int): Int = argb(255, r, g, b)

    /** نفس تحويل أندرويد بين اللون و(الصبغة، التشبع، القيمة). */
    fun colorToHSV(c: Int, hsv: FloatArray) {
        val r = red(c) / 255f
        val g = green(c) / 255f
        val b = blue(c) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        var h = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }
        if (h < 0f) h += 360f
        hsv[0] = h
        hsv[1] = if (max == 0f) 0f else d / max
        hsv[2] = max
    }

    fun HSVToColor(hsv: FloatArray): Int = HSVToColor(255, hsv)

    fun HSVToColor(alpha: Int, hsv: FloatArray): Int {
        val h = ((hsv[0] % 360f) + 360f) % 360f
        val s = hsv[1].coerceIn(0f, 1f)
        val v = hsv[2].coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = v - c
        val (r, g, b) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return argb(alpha, ((r + m) * 255f + 0.5f).toInt(), ((g + m) * 255f + 0.5f).toInt(), ((b + m) * 255f + 0.5f).toInt())
    }
    fun alpha(c: Int) = (c ushr 24) and 0xFF
    fun red(c: Int) = (c shr 16) and 0xFF
    fun green(c: Int) = (c shr 8) and 0xFF
    fun blue(c: Int) = c and 0xFF
}

class RectF(var left: Float = 0f, var top: Float = 0f, var right: Float = 0f, var bottom: Float = 0f) {
    fun width() = right - left
    fun height() = bottom - top
    internal fun sk() = SkRect.makeLTRB(left, top, right, bottom)
}

class Rect(var left: Int = 0, var top: Int = 0, var right: Int = 0, var bottom: Int = 0) {
    fun width() = right - left
    fun height() = bottom - top
    internal fun sk() = SkRect.makeLTRB(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
}

class Paint(flags: Int = 0) {
    enum class Style { FILL, STROKE, FILL_AND_STROKE }
    enum class Cap { BUTT, ROUND, SQUARE }
    enum class Join { MITER, ROUND, BEVEL }

    internal val sk = org.jetbrains.skia.Paint().apply { isAntiAlias = (flags and ANTI_ALIAS_FLAG) != 0 }
    internal var filterBitmap = (flags and FILTER_BITMAP_FLAG) != 0

    constructor(other: Paint) : this(0) {
        color = other.color
        strokeWidth = other.strokeWidth
        style = other.style
        strokeCap = other.strokeCap
        strokeJoin = other.strokeJoin
        isAntiAlias = other.isAntiAlias
        filterBitmap = other.filterBitmap
    }

    var color: Int
        get() = sk.color
        set(v) { sk.color = v }
    var alpha: Int
        get() = (sk.color ushr 24) and 0xFF
        set(v) { sk.color = (sk.color and 0x00FFFFFF) or ((v and 0xFF) shl 24) }
    var strokeWidth: Float
        get() = sk.strokeWidth
        set(v) { sk.strokeWidth = v }
    var isAntiAlias: Boolean
        get() = sk.isAntiAlias
        set(v) { sk.isAntiAlias = v }
    var style: Style = Style.FILL
        set(v) {
            field = v
            sk.mode = when (v) {
                Style.FILL -> PaintMode.FILL
                Style.STROKE -> PaintMode.STROKE
                Style.FILL_AND_STROKE -> PaintMode.STROKE_AND_FILL
            }
        }
    var strokeCap: Cap = Cap.BUTT
        set(v) {
            field = v
            sk.strokeCap = when (v) {
                Cap.BUTT -> PaintStrokeCap.BUTT
                Cap.ROUND -> PaintStrokeCap.ROUND
                Cap.SQUARE -> PaintStrokeCap.SQUARE
            }
        }
    var xfermode: PorterDuffXfermode? = null
        set(v) {
            field = v
            sk.blendMode = when (v?.mode) {
                PorterDuff.Mode.MULTIPLY -> org.jetbrains.skia.BlendMode.MULTIPLY
                PorterDuff.Mode.CLEAR -> org.jetbrains.skia.BlendMode.CLEAR
                PorterDuff.Mode.SRC -> org.jetbrains.skia.BlendMode.SRC
                else -> org.jetbrains.skia.BlendMode.SRC_OVER
            }
        }
    var pathEffect: DashPathEffect? = null
        set(v) {
            field = v
            sk.pathEffect = v?.let { org.jetbrains.skia.PathEffect.makeDash(it.intervals, it.phase) }
        }
    var strokeJoin: Join = Join.MITER
        set(v) {
            field = v
            sk.strokeJoin = when (v) {
                Join.MITER -> PaintStrokeJoin.MITER
                Join.ROUND -> PaintStrokeJoin.ROUND
                Join.BEVEL -> PaintStrokeJoin.BEVEL
            }
        }

    companion object {
        const val ANTI_ALIAS_FLAG = 1
        const val FILTER_BITMAP_FLAG = 2
    }
}

class Path {
    internal val sk = org.jetbrains.skia.Path()
    fun moveTo(x: Float, y: Float) { sk.moveTo(x, y) }
    fun lineTo(x: Float, y: Float) { sk.lineTo(x, y) }
    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) { sk.quadTo(x1, y1, x2, y2) }
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) { sk.cubicTo(x1, y1, x2, y2, x3, y3) }
    fun close() { sk.closePath() }
    fun reset() { sk.reset() }
}

class Canvas(private val bitmap: Bitmap) {
    private val sk = org.jetbrains.skia.Canvas(bitmap.skia, SurfaceProps())

    val width: Int get() = bitmap.width
    val height: Int get() = bitmap.height

    private fun done() = bitmap.skia.notifyPixelsChanged()

    fun drawColor(c: Int) { sk.clear(c); done() }
    fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, p: Paint) { sk.drawLine(x0, y0, x1, y1, p.sk); done() }
    fun drawRect(l: Float, t: Float, r: Float, b: Float, p: Paint) { sk.drawRect(SkRect.makeLTRB(l, t, r, b), p.sk); done() }
    fun drawRect(r: RectF, p: Paint) { sk.drawRect(r.sk(), p.sk); done() }
    fun drawRoundRect(l: Float, t: Float, r: Float, b: Float, rx: Float, ry: Float, p: Paint) {
        sk.drawRRect(RRect.makeLTRB(l, t, r, b, rx, ry), p.sk); done()
    }
    fun drawCircle(cx: Float, cy: Float, r: Float, p: Paint) { sk.drawCircle(cx, cy, r, p.sk); done() }
    fun drawPoint(x: Float, y: Float, p: Paint) { sk.drawPoint(x, y, p.sk); done() }
    fun drawPath(path: Path, p: Paint) { sk.drawPath(path.sk, p.sk); done() }

    fun drawBitmap(b: Bitmap, left: Float, top: Float, p: Paint?) {
        val img = Image.makeFromBitmap(b.skia)
        try {
            sk.drawImage(img, left, top, p?.sk)
        } finally {
            img.close()
        }
        done()
    }

    fun drawBitmap(b: Bitmap, src: Rect?, dst: RectF, p: Paint?) {
        val img = Image.makeFromBitmap(b.skia)
        try {
            val s = src?.sk() ?: SkRect.makeWH(b.width.toFloat(), b.height.toFloat())
            sk.drawImageRect(img, s, dst.sk(), org.jetbrains.skia.FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE), p?.sk, true)
        } finally {
            img.close()
        }
        done()
    }

    fun translate(dx: Float, dy: Float) { sk.translate(dx, dy) }
    fun scale(sx: Float, sy: Float) { sk.scale(sx, sy) }
    fun save(): Int = sk.save()
    fun restore() { sk.restore() }
}

object PorterDuff {
    enum class Mode { CLEAR, SRC, SRC_OVER, MULTIPLY }
}

class PorterDuffXfermode(val mode: PorterDuff.Mode)

class DashPathEffect(val intervals: FloatArray, val phase: Float)
