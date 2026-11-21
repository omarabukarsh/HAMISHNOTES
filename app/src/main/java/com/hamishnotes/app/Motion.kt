package com.hamishnotes.app

import androidx.compose.animation.EnterTransition
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asAndroidBitmap
import android.content.Context
import android.view.WindowManager
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * حركات التطبيق كلها من هنا كي تبدو موحّدة:
 * الدخول بنابض ناعم (يخرج العنصر من الأيقونة ويكبر)، والخروج أسرع بقليل.
 */
object Motion {
    const val ENTER = 300
    const val EXIT = 200

    /** نابض النمو: ارتداد خفيف جدًا ثم استقرار. */
    fun <T> grow(): SpringSpec<T> = spring(dampingRatio = 0.82f, stiffness = 420f)

    /** شريط يتمدد من أعلى (شريط الأقلام والأشكال والملاحظات). */
    val barEnter: EnterTransition
        get() = fadeIn(tween(220)) +
            expandVertically(tween(ENTER, easing = FastOutSlowInEasing), expandFrom = Alignment.Top)
    val barExit: ExitTransition
        get() = fadeOut(tween(160)) +
            shrinkVertically(tween(EXIT, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top)

    /** درج ينزلق من حافته (من البداية أو من النهاية). */
    fun drawerEnter(fromStart: Boolean): EnterTransition =
        slideInHorizontally(tween(ENTER + 30, easing = FastOutSlowInEasing)) { if (fromStart) -it else it } +
            fadeIn(tween(220))

    fun drawerExit(toStart: Boolean): ExitTransition =
        slideOutHorizontally(tween(EXIT + 20, easing = FastOutLinearInEasing)) { if (toStart) -it else it } +
            fadeOut(tween(160))
}

/** ظهور ناعم عند دخول العنصر: تلاشٍ مع تكبير خفيف. */
fun Modifier.appear(durationMs: Int = Motion.ENTER, fromScale: Float = 0.94f): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
    graphicsLayer {
        alpha = a.value
        val s = fromScale + (1f - fromScale) * a.value
        scaleX = s
        scaleY = s
    }
}

/** آخر لمسة على الشاشة (بإحداثيات الشاشة)، تسجّلها MainActivity. */
object TouchOrigin {
    private var x = 0f
    private var y = 0f
    private var at = 0L

    fun record(x: Float, y: Float) {
        this.x = x
        this.y = y
        at = android.os.SystemClock.uptimeMillis()
    }

    /** مكان اللمسة إن كانت حديثة؛ وإلا تخرج النافذة من وسطها. */
    fun recent(): Offset? = if (android.os.SystemClock.uptimeMillis() - at < 1500L) Offset(x, y) else null
}

/**
 * نافذة تخرج من الزر الذي فُتحت منه (مكان اللمسة) وتكبر بنابض، وتنكمش إليه عند الإغلاق.
 * تمرّر للمحتوى close ليغلقها بالحركة نفسها.
 */
@Composable
fun OriginDialog(
    onDismiss: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable (close: () -> Unit) -> Unit
) {
    val origin = remember { TouchOrigin.recent() }
    val a = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(Unit) {
        // الحركة تبدأ بعد أن تُبنى النافذة وتُقاس ويُرسم أول إطار لها: بناء المحتوى الثقيل
        // (مثل الإعدادات) لا يأكل أول إطارات الحركة
        snapshotFlow { bounds != null }.first { it }
        withFrameNanos { }
        a.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 320f))
    }
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                a.animateTo(0f, tween(Motion.EXIT + 40, easing = FastOutLinearInEasing))
                onDismiss()
            }
        }
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = close, properties = properties) {
        // النافذة مستقلة عن النشاط: تطلب أعلى معدل تحديث مثله
        WindowHighRefresh()
        Box(
            Modifier
                .onGloballyPositioned { bounds = Rect(it.positionOnScreen(), it.size.toSize()) }
                // لمسات داخل النافذة لا تمر بـ MainActivity، فنسجّلها هنا لتخرج النوافذ الفرعية منها
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            val b = bounds ?: continue
                            e.changes.firstOrNull { it.changedToDown() }?.let { TouchOrigin.record(b.left + it.position.x, b.top + it.position.y) }
                        }
                    }
                }
                .graphicsLayer {
                    val v = a.value
                    val from = if (origin != null) 0.06f else 0.86f
                    val s = from + (1f - from) * v
                    scaleX = s
                    scaleY = s
                    alpha = (v * 2.2f).coerceIn(0f, 1f)
                    val b = bounds
                    transformOrigin = if (origin != null && b != null && b.width > 0f && b.height > 0f) {
                        TransformOrigin((origin.x - b.left) / b.width, (origin.y - b.top) / b.height)
                    } else {
                        TransformOrigin.Center
                    }
                }
        ) { content(close) }
    }
}

/** انبثاق بنابض: يكبر العنصر من وسطه مع تلاشٍ سريع (للنوافذ). */
fun Modifier.popIn(fromScale: Float = 0.86f): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, Motion.grow()) }
    graphicsLayer {
        val s = fromScale + (1f - fromScale) * a.value
        scaleX = s
        scaleY = s
        alpha = (a.value * 2.5f).coerceIn(0f, 1f)
    }
}

/** انضغاط خفيف للزر عند لمسه (لا يمنع النقر). */
fun Modifier.pressScale(pressedScale: Float = 0.92f): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val s by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = 0.55f, stiffness = 700f), label = "press"
    )
    graphicsLayer { scaleX = s; scaleY = s }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                waitForUpOrCancellation()
                pressed = false
            }
        }
}

/**
 * يضع القائمة تحت الأيقونة (أو فوقها إن لم يتسع المكان)، ويحسب نقطة الانطلاق
 * لتكون عند مركز الأيقونة فتبدو القائمة كأنها تخرج منها.
 */
private class AnchoredMenuPosition(
    private val dx: Int,
    private val dy: Int,
    private val onOrigin: (TransformOrigin) -> Unit
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val w = popupContentSize.width
        val h = popupContentSize.height
        val margin = 8
        var x = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left + dx else anchorBounds.right - w - dx
        if (x + w > windowSize.width - margin) x = anchorBounds.right - w
        x = x.coerceIn(margin, maxOf(margin, windowSize.width - w - margin))
        var y = anchorBounds.bottom + dy
        if (y + h > windowSize.height - margin) {
            val above = anchorBounds.top - h - dy
            y = if (above >= margin) above else maxOf(margin, windowSize.height - h - margin)
        }
        if (w > 0 && h > 0) {
            onOrigin(
                TransformOrigin(
                    (anchorBounds.center.x - x).toFloat() / w,
                    (anchorBounds.center.y - y).toFloat() / h
                )
            )
        }
        return IntOffset(x, y)
    }
}

/**
 * قائمة منسدلة تحل محل DropdownMenu في التطبيق كله (وتشمل لوحات إعدادات الأقلام):
 * تخرج من مركز الأيقونة التي فُتحت منها وتكبر بنابض ناعم، وتنكمش إليها عند الإغلاق.
 */
@Composable
fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    shape: Shape = RoundedCornerShape(14.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    shadowElevation: Dp = 8.dp,
    fromEdge: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    if (fromEdge) {
        EdgeMenu(expanded, onDismissRequest, modifier, offset, shape, containerColor, shadowElevation, content)
        return
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(expanded) {
        if (expanded) progress.animateTo(1f, Motion.grow())
        else progress.animateTo(0f, tween(Motion.EXIT - 40, easing = FastOutLinearInEasing))
    }
    // تبقى النافذة قائمة أثناء حركة الإغلاق
    val alive by remember { derivedStateOf { progress.value > 0.001f } }
    if (expanded || alive) {
        val density = LocalDensity.current
        val dx = with(density) { offset.x.roundToPx() }
        val dy = with(density) { offset.y.roundToPx() }
        val origin = remember { mutableStateOf(TransformOrigin(0.5f, 0f)) }
        val position = remember(dx, dy) { AnchoredMenuPosition(dx, dy) { origin.value = it } }
        Popup(
            popupPositionProvider = position,
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = true)
        ) {
            Surface(
                modifier = modifier
                    .graphicsLayer {
                        val p = progress.value
                        val s = 0.25f + 0.75f * p
                        scaleX = s
                        scaleY = s
                        alpha = (p * 2.2f).coerceIn(0f, 1f)
                        transformOrigin = origin.value
                    }
                    .sizeIn(minWidth = 112.dp, maxWidth = 280.dp),
                shape = shape,
                color = containerColor,
                shadowElevation = shadowElevation
            ) {
                Column(
                    Modifier.width(IntrinsicSize.Max).verticalScroll(rememberScrollState()),
                    content = content
                )
            }
        }
    }
}

/** فتحة الدخول بإحداثيات النافذة: بين left و right على حافة النافذة العليا (تحت الزر مباشرة). */
internal class MenuEdge(val left: Float, val right: Float)

/** عرض الفتحة نسبة إلى عرض الزر: أضيق منه كي تبدو النافذة كأنها تدخل فيه فعلًا. */
private const val SLIT = 0.4f

/** فتحة في منتصف الزر (centerX بإحداثيات النافذة)، محصورة داخل عرض النافذة. */
internal fun slitEdge(centerX: Float, anchorWidth: Float, windowWidth: Float): MenuEdge {
    val half = anchorWidth * SLIT / 2f
    val c = centerX.coerceIn(half, maxOf(half, windowWidth - half))
    return MenuEdge(c - half, c + half)
}

/** أعلى معدل تحديث للشاشة (تحفظه MainActivity)، لتطلبه النوافذ المنبثقة أيضًا. */
object HighRefresh {
    @Volatile var modeId = 0
    @Volatile var rate = 0f
}

/**
 * النوافذ المنبثقة والحوارات نوافذ مستقلة فوق التطبيق، فقد يخفض النظام معدل التحديث إلى 60 هرتز ما دامت ظاهرة.
 * نطلب لها أعلى معدل كالنشاط نفسه، فتبقى الحركة ناعمة على شاشات 90/120/144 هرتز.
 */
@Composable
private fun WindowHighRefresh() {
    val view = LocalView.current
    DisposableEffect(view) {
        val root = view.rootView
        val lp = root.layoutParams as? WindowManager.LayoutParams
        val mode = HighRefresh.modeId
        if (lp != null && mode != 0 && lp.preferredDisplayModeId != mode) {
            lp.preferredDisplayModeId = mode
            lp.preferredRefreshRate = HighRefresh.rate
            runCatching { (view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).updateViewLayout(root, lp) }
        }
        onDispose { }
    }
}

/**
 * حالة حركة «الخروج من حافة الزر»: open تقدّم الفتح وclose تقدّم الانثناء، كلاهما بزمن خطي،
 * والتسارع يُحسب عند الرسم كي لا تتوقف الحركة بين مراحلها.
 */
@Stable
internal class EdgeFx {
    val open = Animatable(0f)
    val close = Animatable(0f)
    var edge by mutableStateOf<MenuEdge?>(null)
    /** النافذة قيست بحجمها النهائي ومكانها معروف: يمكن أن تبدأ الحركة. */
    var ready by mutableStateOf(false)
    var anchor: Rect? = null
    /** تسجيل رسم النافذة (يُحدَّث مع كل رسم لها)، ومنه تُلتقط صورتها عند الإغلاق. */
    internal var layer: GraphicsLayer? = null
    /** صورة النافذة الملتقطة مرة واحدة عند بداية الإغلاق، تُشدّ على الشبكة أثناء الانثناء. */
    internal var image: android.graphics.Bitmap? = null
    val alive: Boolean get() = open.value > 0.001f

    suspend fun show() {
        close.snapTo(0f)
        image = null
        if (open.value < 1f) {
            // ننتظر أول قياس للنافذة ثم إطارًا واحدًا: بناء محتوى النافذة الثقيل لا يأكل أول إطارات الحركة
            snapshotFlow { ready && edge != null }.first { it }
            withFrameNanos { }
            open.animateTo(1f, tween(EDGE_OPEN_MS, easing = LinearEasing))
        }
    }

    suspend fun hide() {
        if (open.value <= 0f) return
        // صورة واحدة للنافذة؛ إن تعذّر التقاطها تختفي النافذة بتلاشٍ بسيط بدل الانثناء
        // (بحد أقصى 150 ms: لا تتعلق النافذة إن تأخر الالتقاط لأي سبب)
        if (image == null) image = withTimeoutOrNull(150L) {
            runCatching { layer?.toImageBitmap()?.asAndroidBitmap() }.getOrNull()
        }
        close.animateTo(1f, tween(EDGE_CLOSE_MS, easing = LinearEasing))
        open.snapTo(0f)
        close.snapTo(0f)
        image = null
    }
}

private const val EDGE_OPEN_MS = 420
private const val EDGE_CLOSE_MS = 520

/** مستطيل بزوايا مستديرة في أي موضع داخل العنصر (يتحرك به إطار النافذة أثناء الفتح). */
private class RoundRectShape(val l: Float, val t: Float, val r: Float, val b: Float, val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(l, t, r, maxOf(b, t + 1f), CornerRadius(radius)))
}

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun ease3(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f
private fun smooth5(d: Float) = d * d * d * (d * (d * 6f - 15f) + 10f)
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/**
 * إطار النافذة وحركتها: تخرج من الفتحة وتتمدد، وعند الإغلاق تنثني كالقمع وتُسحب داخل الفتحة
 * (مثل Genie في أجهزة Apple). الانثناء يشدّ صورة واحدة للنافذة على شبكة ناعمة (drawBitmapMesh): أمر رسم واحد في كل إطار، وحواف بلا درجات.
 */
internal fun Modifier.edgeWindow(fx: EdgeFx, shape: Shape, elevation: Dp): Modifier = composed {
    val snapshot = rememberGraphicsLayer()
    fx.layer = snapshot
    val mesh = remember { FloatArray((MESH_ROWS + 1) * (MESH_COLS + 1) * 2) }
    val clipCache = remember { OutlineCache() }
    val density = LocalDensity.current
    val r0 = with(density) { 6.dp.toPx() }
    val r1 = with(density) { 18.dp.toPx() }
    val elev = with(density) { elevation.toPx() }
    this
        .graphicsLayer {
            val g = fx.edge
            val e = EmphasizedDecelerate.transform(fx.open.value)
            val closing = fx.close.value > 0f
            alpha = if (g == null || !fx.ready) 0f else 1f
            if (g != null && !closing && e < 0.999f) {
                val w = size.width
                val h = size.height
                this.shape = RoundRectShape(lerpF(g.left, 0f, e), 0f, lerpF(g.right, w, e), lerpF(0f, h, e), lerpF(r0, r1, e))
                clip = true
                // الظل يظهر في آخر الحركة فقط: حسابه لإطار يتغير كل لحظة مكلف
                shadowElevation = elev * ((e - 0.85f) / 0.15f).coerceIn(0f, 1f)
            } else {
                this.shape = shape
                clip = false
                shadowElevation = if (closing) 0f else elev
            }
        }
        .drawWithContent {
            val g = fx.edge
            val p = fx.close.value
            val img = fx.image
            if (p <= 0f || g == null || img == null) {
                // الرسم العادي عبر تسجيل محفوظ: منه تُلتقط صورة النافذة لحظة الإغلاق.
                // الزوايا المستديرة تُقص داخل التسجيل نفسه: خلفيات النوافذ مستطيلة وتعتمد على قص الإطار،
                // وصورة النافذة لا تأخذ ذلك القص، فتظهر زواياها حادة لحظة الانثناء دونه
                val outlinePath = clipCache.pathFor(size, shape, layoutDirection, this)
                snapshot.record { clipPath(outlinePath) { this@drawWithContent.drawContent() } }
                if (p > 0f && g != null) {
                    // الصورة لم تجهز بعد (أو تعذّرت): تلاشٍ سريع
                    drawLayer(snapshot.also { it.alpha = (1f - p * 2f).coerceIn(0f, 1f) })
                } else {
                    snapshot.alpha = 1f
                    drawLayer(snapshot)
                }
                return@drawWithContent
            }
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f || img.isRecycled) return@drawWithContent
            // مرحلتان متداخلتان: الانحناء نحو الفتحة، والانسحاب داخلها
            val bend = ease3((p / 0.5f).coerceIn(0f, 1f))
            val x = ((p - 0.2f) / 0.8f).coerceIn(0f, 1f)
            val pull = x * x * (2.2f - 1.2f * x)
            // شبكة ناعمة: كل صف يتصل بالذي يليه، فالحواف منحنيات متصلة بلا درجات
            val verts = mesh
            for (r in 0..MESH_ROWS) {
                val dst = r * h / MESH_ROWS - pull * h
                val y: Float
                val left: Float
                val right: Float
                if (dst <= 0f) {
                    // ما دخل في الفتحة ينطوي على خطها
                    y = 0f; left = g.left; right = g.right
                } else {
                    val d = smooth5((dst / h).coerceIn(0f, 1f))
                    y = dst
                    left = lerpF(0f, lerpF(g.left, 0f, d), bend)
                    right = lerpF(w, lerpF(g.right, w, d), bend)
                }
                for (c in 0..MESH_COLS) {
                    val i = (r * (MESH_COLS + 1) + c) * 2
                    verts[i] = left + (right - left) * c / MESH_COLS
                    verts[i + 1] = y
                }
            }
            drawIntoCanvas { it.nativeCanvas.drawBitmapMesh(img, MESH_COLS, MESH_ROWS, verts, 0, null, 0, meshPaint) }
        }
}

/** مسار حدود النافذة (شكلها بزواياه المستديرة)، يُحسب مرة لكل حجم. */
private class OutlineCache {
    private var size = Size.Unspecified
    private var shape: Shape? = null
    private val path = androidx.compose.ui.graphics.Path()

    fun pathFor(s: Size, sh: Shape, dir: LayoutDirection, d: Density): androidx.compose.ui.graphics.Path {
        if (s != size || sh != shape) {
            size = s
            shape = sh
            path.reset()
            path.addOutline(sh.createOutline(s, dir, d))
        }
        return path
    }
}

private const val MESH_ROWS = 48
private const val MESH_COLS = 2
private val meshPaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)

/** المحتوى يظهر بعد أن يتسع الإطار، ويبقى كما هو أثناء الانثناء. */
internal fun Modifier.edgeContent(fx: EdgeFx): Modifier = graphicsLayer {
    alpha = if (fx.close.value > 0f) 1f else ((EmphasizedDecelerate.transform(fx.open.value) - 0.3f) / 0.4f).coerceIn(0f, 1f)
}

/** النافذة دائمًا تحت الزر (لا تنقلب فوقه)، وارتفاعها محدود بالمساحة تحته ويُمرَّر المحتوى إن طال. */
private class EdgeMenuPosition(
    private val dx: Int,
    private val dy: Int,
    private val onPlaced: (anchor: IntRect, x: Int, w: Int, h: Int, avail: Int) -> Unit
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val w = popupContentSize.width
        val h = popupContentSize.height
        val margin = 8
        var x = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left + dx else anchorBounds.right - w - dx
        if (x + w > windowSize.width - margin) x = anchorBounds.right - w
        x = x.coerceIn(margin, maxOf(margin, windowSize.width - w - margin))
        val y = anchorBounds.bottom + dy
        if (w > 0 && h > 0) onPlaced(anchorBounds, x, w, h, windowSize.height - y - margin)
        return IntOffset(x, y)
    }
}

/** نوافذ شريط الأدوات: تخرج من فتحة في حافة الزر وتعود إليها (انظر edgeWindow). */
@Composable
private fun EdgeMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    offset: DpOffset,
    shape: Shape,
    containerColor: Color,
    shadowElevation: Dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val fx = remember { EdgeFx() }
    LaunchedEffect(expanded) { if (expanded) fx.show() else fx.hide() }
    val alive by remember { derivedStateOf { fx.open.value > 0.001f } }
    if (!(expanded || alive)) return
    val density = LocalDensity.current
    val dx = with(density) { offset.x.roundToPx() }
    val dy = with(density) { offset.y.roundToPx() }
    var avail by remember { mutableIntStateOf(0) }
    val position = remember(dx, dy) {
        EdgeMenuPosition(dx, dy) { anchor, x, w, h, room ->
            val a = Rect(anchor.left.toFloat(), anchor.top.toFloat(), anchor.right.toFloat(), anchor.bottom.toFloat())
            fx.anchor = a
            val e = slitEdge(a.center.x - x, a.width, w.toFloat())
            val old = fx.edge
            if (old == null || old.left != e.left || old.right != e.right) fx.edge = e
            val cap = room.coerceAtLeast(with(density) { 160.dp.roundToPx() })
            if (avail != cap) avail = cap
            val fits = h <= cap + 1
            if (fx.ready != fits) fx.ready = fits
        }
    }
    Popup(
        popupPositionProvider = position,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true)
    ) {
        WindowHighRefresh()
        Box(Modifier.edgeWindow(fx, shape, shadowElevation)) {
            Surface(
                modifier = modifier
                    .sizeIn(minWidth = 112.dp, maxWidth = 280.dp)
                    .then(if (avail > 0) Modifier.heightIn(max = with(density) { avail.toDp() }) else Modifier),
                shape = shape,
                color = containerColor,
                shadowElevation = 0.dp
            ) {
                Column(
                    Modifier.edgeContent(fx).width(IntrinsicSize.Max).verticalScroll(rememberScrollState()),
                    content = content
                )
            }
        }
    }
}

/** نافذة تنبيه تنبثق بنابض ناعم (تحل محل AlertDialog في التطبيق كله). */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
    containerColor: Color = androidx.compose.material3.AlertDialogDefaults.containerColor,
    titleContentColor: Color = androidx.compose.material3.AlertDialogDefaults.titleContentColor,
    textContentColor: Color = androidx.compose.material3.AlertDialogDefaults.textContentColor
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.popIn(),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        properties = properties,
        containerColor = containerColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor
    )
}

/** نافذة كاملة تنبثق بنابض ناعم (تحل محل Dialog في التطبيق). */
@Composable
fun Dialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        Box(Modifier.popIn(0.9f)) { content() }
    }
}
