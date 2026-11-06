package com.hamishnotes.app

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * طبقة الخطوط المثبّتة كصورة: خطوط الصفحة تُرسم مرة واحدة في Bitmap، وتُعرض بأمر drawImage واحد.
 *  - الصورة الأساسية للصفحة كاملة بعرض حتى 2400 (مثل صورة صفحة PDF)، بأحجام مقرّبة كل 200 بكسل
 *    فالتغيّرات الصغيرة في الحجم لا تعيد الرسم
 *  - خط جديد في آخر القائمة: يُرسم وحده فوق نسخة من الصورة في الخلفية (لا على الخيط الرئيسي)
 *  - أي تغيير آخر (مسح، تراجع، تلوين، الوضع الليلي): إعادة رسم كاملة في الخلفية،
 *    وتبقى الصورة القديمة ظاهرة حتى تجهز الجديدة (مرة كل 100 ms على الأكثر أثناء المسح)
 *  - عند التكبير أكثر من دقة الصورة الأساسية: طبقة حادة بحجم الجزء الظاهر فقط تُرسم بدقة الشاشة
 *    بعد توقف الحركة
 *  - الصفحة الفارغة بلا صورة، والصورة تبقى في ذاكرة مشتركة بعد خروج الصفحة فتعود فورًا
 * الرسم بنفس drawInk، فشكل الخطوط لا يتغير.
 */
private const val MAX_W = 2400
private const val MAX_PIXELS = 2400L * 3400L
private const val RENDER_GAP_MS = 100L
/** إعادة الرسم بالحجم الجديد بعد أن يتوقف التقريب/التصغير؛ وقبلها تبقى الصورة الحالية مكبّرة. */
private const val RESIZE_DELAY_MS = 250L
/** الطبقة الحادة تُرسم بعد أن يثبت الجزء الظاهر هذه المدة. */
private const val SHARP_DELAY_MS = 150L
/** الطبقة الحادة فقط إذا كانت الصفحة على الشاشة أعرض من الصورة الأساسية بهذه النسبة. */
internal const val SHARP_MIN_RATIO = 1.15f

/** حجم الصورة الأساسية لصفحة معروضة بحجم w×h بكسل: عرض مقرّب كل 200 بحد 2400، وحد للبكسلات. */
internal fun inkBitmapSize(w: Int, h: Int): IntSize {
    if (w <= 0 || h <= 0) return IntSize.Zero
    var bw = renderWidthFor(w).coerceAtMost(MAX_W).toDouble()
    var bh = h.toDouble() * bw / w
    if (bw * bh > MAX_PIXELS) {
        val f = sqrt(MAX_PIXELS / (bw * bh))
        bw *= f
        bh *= f
    }
    return IntSize(bw.toInt().coerceAtLeast(1), bh.toInt().coerceAtLeast(1))
}

/**
 * موضع الصورة من الصفحة: بكسل الصورة = نقطة الصفحة × scale + (dx, dy)
 * (نقاط الخطوط نسبية لعرض الصفحة).
 */
private data class Spec(val w: Int, val h: Int, val scale: Float, val dx: Float, val dy: Float)

/** يرسم الخطوط فوق صورة موجودة (نفس drawInk المستعمل على الشاشة). */
private fun drawStrokesInto(bmp: Bitmap, strokes: List<Ink>, night: Boolean, spec: Spec, check: () -> Unit = {}) {
    val canvas = androidx.compose.ui.graphics.Canvas(bmp.asImageBitmap())
    CanvasDrawScope().draw(
        Density(1f), LayoutDirection.Ltr, canvas, Size(bmp.width.toFloat(), bmp.height.toFloat())
    ) {
        translate(spec.dx, spec.dy) {
            for (s in strokes) {
                check()
                drawStroke(s, if (night) nightOf(s.color) else s.color, scaleW = spec.scale)
            }
        }
    }
}

/** خطوط هذه القائمة هي خطوط old كما هي ثم خطوط جديدة بعدها. */
private fun extends(old: List<Ink>, new: List<Ink>): Boolean {
    if (new.size < old.size) return false
    for (i in old.indices) if (new[i] !== old[i]) return false
    return true
}

private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

/** صورة أساسية محفوظة لصفحة خرجت من الشاشة. */
private class StoredInk(val bitmap: Bitmap, val drawn: List<Ink>, val night: Boolean, val spec: Spec)

/** ذاكرة مشتركة لصور الحبر، بحد أقصى للذاكرة؛ الصورة المطرودة تُحرَّر. */
private object InkStore {
    private val cache = object : LruCache<String, StoredInk>(
        (Runtime.getRuntime().maxMemory() / 8L).coerceAtMost(96L * 1024L * 1024L).toInt()
    ) {
        override fun sizeOf(key: String, value: StoredInk): Int = value.bitmap.byteCount

        override fun entryRemoved(evicted: Boolean, key: String, oldValue: StoredInk, newValue: StoredInk?) {
            // take() يأخذ الصورة لاستعمالها، فلا تُحرَّر إلا عند الطرد أو الاستبدال
            if (evicted || (newValue != null && newValue.bitmap !== oldValue.bitmap)) oldValue.bitmap.recycle()
        }
    }

    fun take(key: String): StoredInk? = cache.remove(key)

    fun put(key: String, v: StoredInk) {
        cache.put(key, v)
    }
}

/** صورة واحدة مرسومة من خطوط الصفحة حسب Spec، تُحدَّث في الخلفية. */
private class Raster(
    private val scope: CoroutineScope,
    private val settleMs: Long,
    private val onDrawn: () -> Unit
) {
    var bitmap: Bitmap? = null
        private set
    var image: ImageBitmap? = null
        private set
    /** Spec الصورة الحالية. */
    var spec: Spec? = null
        private set
    var drawn: List<Ink> = emptyList()
        private set
    private var drawnNight = false
    /** صورة سابقة بنفس الحجم يُعاد استعمالها بدل حجز صورة جديدة. */
    private var spare: Bitmap? = null

    private var wantStrokes: List<Ink> = emptyList()
    private var wantNight = false
    private var wantSpec: Spec? = null
    private var specChangedAt = 0L
    private var lastRenderAt = 0L
    private var job: Job? = null
    private var disposed = false

    fun adopt(s: StoredInk) {
        bitmap = s.bitmap
        image = s.bitmap.asImageBitmap()
        spec = s.spec
        drawn = s.drawn
        drawnNight = s.night
    }

    fun update(strokes: List<Ink>, night: Boolean, spec: Spec?) {
        if (disposed) return
        if (spec != wantSpec) specChangedAt = System.currentTimeMillis()
        wantStrokes = strokes
        wantNight = night
        wantSpec = spec
        if (job?.isActive != true && needsWork()) job = scope.launch { loop() }
    }

    private fun needsWork(): Boolean {
        val ws = wantSpec ?: return bitmap != null
        if (wantStrokes.isEmpty()) return bitmap != null || drawn !== wantStrokes
        return bitmap == null || drawn !== wantStrokes || drawnNight != wantNight || spec != ws
    }

    /** على الخيط الرئيسي: الصورة الاحتياطية إن كان حجمها مناسبًا. */
    private fun takeSpare(s: Spec): Bitmap? {
        val sp = spare ?: return null
        spare = null
        if (!sp.isRecycled && sp.width == s.w && sp.height == s.h) return sp
        sp.recycle()
        return null
    }

    /** في الخلفية: صورة فارغة بحجم s (الاحتياطية بعد مسحها، أو جديدة). */
    private fun blank(reuse: Bitmap?, s: Spec): Bitmap =
        reuse?.also { it.eraseColor(0) } ?: Bitmap.createBitmap(s.w, s.h, Bitmap.Config.ARGB_8888)

    private fun show(fresh: Bitmap?, strokes: List<Ink>, night: Boolean, s: Spec?) {
        val old = bitmap
        bitmap = fresh
        image = fresh?.asImageBitmap()
        spec = s
        drawn = strokes
        drawnNight = night
        onDrawn()
        if (old != null && old !== fresh) scope.launch {
            // الصورة القديمة قد تكون في الإطار الجاري رسمه؛ تُحفظ للاستعمال التالي بعد إطارين
            withFrameNanos { }
            withFrameNanos { }
            if (disposed) {
                old.recycle()
            } else {
                spare?.takeIf { it !== old }?.recycle()
                spare = old
            }
        }
    }

    private suspend fun loop() {
        while (!disposed) {
            if (!needsWork()) return
            val s = wantSpec
            val strokes = wantStrokes
            val night = wantNight
            // لا خطوط أو لا شيء ظاهر: بلا صورة
            if (s == null || strokes.isEmpty()) {
                show(null, strokes, night, s)
                continue
            }
            val cur = bitmap
            val curSpec = spec
            // خطوط أضيفت في آخر القائمة: تُرسم وحدها فوق نسخة من الصورة الحالية
            if (cur != null && curSpec == s && drawnNight == night && extends(drawn, strokes)) {
                val added = strokes.subList(drawn.size, strokes.size)
                val reuse = takeSpare(s)
                val fresh = withContext(Dispatchers.Default) {
                    val b = blank(reuse, s)
                    android.graphics.Canvas(b).drawBitmap(cur, 0f, 0f, null)
                    drawStrokesInto(b, added, night, s)
                    b
                }
                if (disposed) {
                    fresh.recycle()
                    return
                }
                show(fresh, strokes, night, s)
                continue
            }
            val now = System.currentTimeMillis()
            val onlySpec = cur != null && drawn === strokes && drawnNight == night
            // تغيّر الحجم/الموضع وحده: الانتظار حتى تتوقف الحركة
            if (onlySpec && now - specChangedAt < settleMs) {
                delay(settleMs - (now - specChangedAt))
                continue
            }
            // إعادة رسم كاملة مرة كل 100 ms على الأكثر (المسح المتواصل)
            if (cur != null && now - lastRenderAt < RENDER_GAP_MS) {
                delay(RENDER_GAP_MS - (now - lastRenderAt))
                continue
            }
            lastRenderAt = now
            val reuse = takeSpare(s)
            val fresh = withContext(Dispatchers.Default) {
                val b = blank(reuse, s)
                drawStrokesInto(b, strokes, night, s) { ensureActive() }
                b
            }
            if (disposed) {
                fresh.recycle()
                return
            }
            show(fresh, strokes, night, s)
        }
    }

    /** إيقاف العمل؛ ثم release(keep) بعد انتهاء أي رسم جارٍ في الخلفية يستعمل الصورة. */
    fun dispose(release: () -> Unit) {
        disposed = true
        val j = job
        if (j != null && !j.isCompleted) {
            j.cancel()
            j.invokeOnCompletion { mainHandler.post(release) }
        } else {
            release()
        }
    }

    /** يعطي الصورة الحالية لمن يحتفظ بها (أو null) ويحرر الباقي. */
    fun release(keep: (Bitmap, List<Ink>, Boolean, Spec) -> Boolean) {
        val b = bitmap
        val s = spec
        if (b != null && s != null && !b.isRecycled && !keep(b, drawn, drawnNight, s)) b.recycle()
        bitmap = null
        image = null
        spare?.recycle()
        spare = null
    }
}

/**
 * طبقة الخطوط المثبّتة لصفحة واحدة ظاهرة.
 * @param key مفتاح الصفحة في الذاكرة المشتركة (المستند والصفحة)
 * @param visible الجزء الظاهر من الصفحة بالبكسل بإحداثياتها (للطبقة الحادة عند التكبير)
 * @param onDrawn يُستدعى بعد كل تحديث للصور بقوائم الخطوط المرسومة في كل صورة ظاهرة
 */
@Composable
fun InkLayer(
    key: String,
    strokes: List<Ink>,
    night: Boolean,
    visible: State<Rect>,
    modifier: Modifier = Modifier,
    onDrawn: (List<List<Ink>>) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    val onDrawnNow by rememberUpdatedState(onDrawn)
    val base = remember {
        Raster(scope, RESIZE_DELAY_MS) { version++ }.also { r ->
            InkStore.take(key)?.let { st ->
                if (st.drawn === strokes || extends(st.drawn, strokes)) r.adopt(st) else st.bitmap.recycle()
            }
        }
    }
    val sharp = remember { Raster(scope, SHARP_DELAY_MS) { version++ } }
    var layout by remember { mutableStateOf(IntSize.Zero) }

    // بعد كل تحديث للصورة: إبلاغ الصفحة بالخطوط المرسومة الآن
    LaunchedEffect(version) {
        onDrawnNow(if (sharp.image != null) listOf(base.drawn, sharp.drawn) else listOf(base.drawn))
    }

    fun sharpSpec(size: IntSize, vis: Rect): Spec? {
        val bs = inkBitmapSize(size.width, size.height)
        if (size.width <= 0 || bs.width <= 0 || size.width < bs.width * SHARP_MIN_RATIO) return null
        val l = floor(vis.left.coerceIn(0f, size.width.toFloat()))
        val t = floor(vis.top.coerceIn(0f, size.height.toFloat()))
        val r = ceil(vis.right.coerceIn(0f, size.width.toFloat()))
        val b = ceil(vis.bottom.coerceIn(0f, size.height.toFloat()))
        if (r - l < 1f || b - t < 1f) return null
        return Spec((r - l).toInt(), (b - t).toInt(), size.width.toFloat(), -l, -t)
    }

    SideEffect {
        val bs = inkBitmapSize(layout.width, layout.height)
        // قبل معرفة الحجم: لا تحديث (حتى لا تضيع الصورة المحفوظة)
        if (bs.width > 0) base.update(strokes, night, Spec(bs.width, bs.height, bs.width.toFloat(), 0f, 0f))
        sharp.update(strokes, night, sharpSpec(layout, visible.value))
    }
    // الجزء الظاهر يتغير مع التمرير دون إعادة تركيب
    val strokesNow by rememberUpdatedState(strokes)
    val nightNow by rememberUpdatedState(night)
    LaunchedEffect(sharp) {
        snapshotFlow { visible.value to layout }.collect { (vis, sz) ->
            sharp.update(strokesNow, nightNow, sharpSpec(sz, vis))
        }
    }
    DisposableEffect(base) {
        onDispose {
            base.dispose {
                base.release { b, d, n, s ->
                    if (d.isEmpty()) false else {
                        InkStore.put(key, StoredInk(b, d, n, s))
                        true
                    }
                }
            }
            sharp.dispose { sharp.release { _, _, _, _ -> false } }
        }
    }
    Canvas(modifier.fillMaxSize().graphicsLayer().onSizeChanged { layout = it }) {
        @Suppress("UNUSED_VARIABLE")
        val v = version
        val img = base.image
        if (img == null) {
            // قبل جهوز الصورة الأولى: الرسم المباشر
            strokes.forEach { drawStroke(it, if (night) nightOf(it.color) else it.color) }
            return@Canvas
        }
        val full = IntSize(size.width.roundToInt(), size.height.roundToInt())
        // الطبقة الحادة صالحة ما دام عرض الصفحة كما رُسمت به (موضعها بإحداثيات الصفحة)
        val sp = sharp.spec
        val sImg = sharp.image
        val useSharp = sImg != null && sp != null && abs(sp.scale - size.width) < 0.5f
        if (useSharp && sp != null && sImg != null) {
            val l = -sp.dx
            val t = -sp.dy
            clipRect(l, t, l + sp.w, t + sp.h, ClipOp.Difference) {
                drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset.Zero, full)
            }
            drawImage(
                sImg, IntOffset.Zero, IntSize(sImg.width, sImg.height),
                IntOffset(l.roundToInt(), t.roundToInt()), IntSize(sp.w, sp.h)
            )
        } else {
            drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset.Zero, full)
        }
    }
}
