package com.mynotes.app

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * طبقة الخطوط المثبّتة كصورة: خطوط الصفحة تُرسم مرة واحدة في Bitmap بحجم الصفحة على الشاشة،
 * وتُعرض بأمر drawImage واحد بدل إعادة كل أوامر الرسم في كل إطار.
 *  - خط جديد في آخر القائمة: يُرسم هو وحده فوق الصورة الحالية
 *  - أي تغيير آخر (مسح، تراجع، تلوين، تحريك باللاسو، الوضع الليلي): إعادة رسم كاملة في الخلفية،
 *    وتبقى الصورة القديمة ظاهرة حتى تجهز الجديدة (مرة كل 100 ms على الأكثر أثناء المسح)
 *  - التكبير بإصبعين: تُكبَّر الصورة الحالية أثناء الحركة، ثم تُرسم بالدقة الجديدة بعد توقفها بـ 250 ms
 * الرسم بنفس drawInk، فشكل الخطوط لا يتغير.
 */
private const val MAX_W = 4096
private const val MAX_PIXELS = 4096L * 4096L
private const val RENDER_GAP_MS = 100L
/** إعادة الرسم بالدقة الجديدة مرة واحدة بعد أن يتوقف التقريب/التصغير نصف ثانية؛ وقبلها تبقى الصورة الأولى كما هي. */
private const val RESIZE_DELAY_MS = 500L

/** حجم الصورة لصفحة معروضة بحجم w×h بكسل، بحد أقصى للعرض وللعدد الكلي للبكسلات. */
internal fun inkBitmapSize(w: Int, h: Int): IntSize {
    if (w <= 0 || h <= 0) return IntSize.Zero
    var bw = minOf(w, MAX_W).toDouble()
    var bh = h.toDouble() * bw / w
    if (bw * bh > MAX_PIXELS) {
        val f = sqrt(MAX_PIXELS / (bw * bh))
        bw *= f
        bh *= f
    }
    return IntSize(bw.toInt().coerceAtLeast(1), bh.toInt().coerceAtLeast(1))
}

/** يرسم الخطوط فوق صورة موجودة بمقياس عرضها (نفس drawInk المستعمل على الشاشة). */
internal fun drawStrokesInto(bmp: Bitmap, strokes: List<Ink>, night: Boolean, check: () -> Unit = {}) {
    val canvas = androidx.compose.ui.graphics.Canvas(bmp.asImageBitmap())
    CanvasDrawScope().draw(
        Density(1f), LayoutDirection.Ltr, canvas, Size(bmp.width.toFloat(), bmp.height.toFloat())
    ) {
        for (s in strokes) {
            check()
            drawStroke(s, if (night) nightOf(s.color) else s.color)
        }
    }
}

/** خطوط هذه القائمة هي خطوط old كما هي ثم خطوط جديدة بعدها. */
private fun extends(old: List<Ink>, new: List<Ink>): Boolean {
    if (new.size < old.size) return false
    for (i in old.indices) if (new[i] !== old[i]) return false
    return true
}

private class InkCache(private val scope: CoroutineScope) {
    var bitmap: Bitmap? = null
    var image: ImageBitmap? = null
    /** الخطوط المرسومة في الصورة، ولون الوضع الذي رُسمت به، وحجمها. */
    private var drawn: List<Ink> = emptyList()
    private var drawnNight = false
    private var drawnSize = IntSize.Zero
    var version by mutableIntStateOf(0)

    private var wantStrokes: List<Ink> = emptyList()
    private var wantNight = false
    private var wantSize = IntSize.Zero
    private var sizeChangedAt = 0L
    private var lastRenderAt = 0L
    private var job: Job? = null
    private var disposed = false

    fun update(strokes: List<Ink>, night: Boolean, size: IntSize) {
        if (size != wantSize) sizeChangedAt = System.currentTimeMillis()
        wantStrokes = strokes
        wantNight = night
        wantSize = size
        if (size == IntSize.Zero) return
        catchUp()
        if (job?.isActive != true && needsWork()) job = scope.launch { loop() }
    }

    private fun needsWork() = bitmap == null || drawn !== wantStrokes || drawnNight != wantNight || drawnSize != wantSize

    /** خطوط أضيفت في آخر القائمة: تُرسم وحدها فوق الصورة الحالية دون انتظار. */
    private fun catchUp() {
        val b = bitmap ?: return
        if (drawn === wantStrokes || drawnNight != wantNight || !extends(drawn, wantStrokes)) return
        drawStrokesInto(b, wantStrokes.subList(drawn.size, wantStrokes.size), wantNight)
        drawn = wantStrokes
        version++
    }

    private suspend fun loop() {
        while (!disposed) {
            catchUp()
            if (!needsWork()) return
            val now = System.currentTimeMillis()
            val onlySize = bitmap != null && drawn === wantStrokes && drawnNight == wantNight
            // تغيّر الحجم وحده (تكبير): الانتظار حتى تتوقف الحركة
            if (onlySize && now - sizeChangedAt < RESIZE_DELAY_MS) {
                delay(RESIZE_DELAY_MS - (now - sizeChangedAt))
                continue
            }
            // إعادة رسم كاملة مرة كل 100 ms على الأكثر (المسح المتواصل)
            if (bitmap != null && now - lastRenderAt < RENDER_GAP_MS) {
                delay(RENDER_GAP_MS - (now - lastRenderAt))
                continue
            }
            val strokes = wantStrokes
            val night = wantNight
            val size = wantSize
            lastRenderAt = now
            val fresh = withContext(Dispatchers.Default) {
                val bmp = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
                drawStrokesInto(bmp, strokes, night) { ensureActive() }
                bmp
            }
            if (disposed) {
                fresh.recycle()
                return
            }
            val old = bitmap
            bitmap = fresh
            image = fresh.asImageBitmap()
            drawn = strokes
            drawnNight = night
            drawnSize = size
            version++
            if (old != null) scope.launch {
                // الصورة القديمة قد تكون في الإطار الجاري رسمه؛ تُحرَّر بعد إطارين
                withFrameNanos { }
                withFrameNanos { }
                old.recycle()
            }
        }
    }

    fun dispose() {
        disposed = true
        job?.cancel()
        bitmap?.recycle()
        bitmap = null
        image = null
    }
}

/** طبقة الخطوط المثبّتة لصفحة واحدة ظاهرة. الصورة تُحرَّر عند خروج الصفحة من الشاشة. */
@Composable
fun InkLayer(strokes: List<Ink>, night: Boolean, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val cache = remember { InkCache(scope) }
    var layout by remember { mutableStateOf(IntSize.Zero) }
    SideEffect { cache.update(strokes, night, inkBitmapSize(layout.width, layout.height)) }
    DisposableEffect(cache) { onDispose { cache.dispose() } }
    Canvas(modifier.fillMaxSize().graphicsLayer().onSizeChanged { layout = it }) {
        @Suppress("UNUSED_VARIABLE")
        val v = cache.version
        val img = cache.image
        if (img == null) {
            // قبل جهوز الصورة الأولى: الرسم المباشر
            strokes.forEach { drawStroke(it, if (night) nightOf(it.color) else it.color) }
        } else {
            drawImage(
                image = img,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(img.width, img.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
            )
        }
    }
}
