package com.hamishnotes.app

import android.view.HapticFeedbackConstants
import android.view.View
import android.view.Window
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import kotlin.coroutines.cancellation.CancellationException

/**
 * حركات التطبيق كلها من هنا كي تبدو موحّدة:
 * الدخول بنابض (قوته من «قوة الارتداد» في الإعدادات)، والخروج أسرع بقليل.
 */
object Motion {
    const val ENTER = 300
    const val EXIT = 200

    /** نسبة التخميد حسب «قوة الارتداد». القيمة المعطاة هي قيمة «متوازن». */
    fun damping(balanced: Float): Float = when (UiPrefs.motionFeel) {
        MotionFeel.CALM -> 1f
        MotionFeel.BALANCED -> balanced
        MotionFeel.LIVELY -> (balanced - 0.22f).coerceAtLeast(0.42f)
    }

    /** نابض النمو: النوافذ والقوائم. */
    fun <T> grow(): SpringSpec<T> = spring(dampingRatio = damping(0.82f), stiffness = 420f)

    /** نابض أهدأ للعناصر الكبيرة (الشاشات والأدراج). */
    fun <T> gentle(): SpringSpec<T> = spring(dampingRatio = damping(0.9f), stiffness = 300f)

    /** نابض فيه ارتداد أوضح (قلب البطاقة). */
    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = damping(0.68f), stiffness = 380f)

    /** نابض سريع للأشرطة الصغيرة. */
    fun <T> quick(): SpringSpec<T> = spring(dampingRatio = damping(0.8f), stiffness = 600f)

    /** شريط يتمدد من أعلى (شريط الأقلام والأشكال والملاحظات). بلا ارتداد لأنه يزيح الصفحة. */
    val barEnter: EnterTransition
        get() = fadeIn(tween(220)) +
            expandVertically(spring(dampingRatio = 1f, stiffness = 500f, visibilityThreshold = IntSize.VisibilityThreshold), expandFrom = Alignment.Top)
    val barExit: ExitTransition
        get() = fadeOut(tween(160)) +
            shrinkVertically(tween(EXIT, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top)

    /** درج ينزلق من حافته (من البداية أو من النهاية). */
    fun drawerEnter(fromStart: Boolean): EnterTransition =
        slideInHorizontally(spring(dampingRatio = damping(0.88f), stiffness = 380f, visibilityThreshold = IntOffset.VisibilityThreshold)) { if (fromStart) -it else it } +
            fadeIn(tween(160))

    fun drawerExit(toStart: Boolean): ExitTransition =
        slideOutHorizontally(tween(EXIT + 20, easing = FastOutLinearInEasing)) { if (toStart) -it else it } +
            fadeOut(tween(180))

    /** شريط عائم يرتفع من الأسفل (أشرطة التحديد ومشغل الصوت). */
    val floatEnter: EnterTransition
        get() = slideInVertically(spring(dampingRatio = damping(0.8f), stiffness = 500f, visibilityThreshold = IntOffset.VisibilityThreshold)) { it / 2 } +
            scaleIn(spring(dampingRatio = damping(0.8f), stiffness = 500f), initialScale = 0.9f) +
            fadeIn(tween(140))
    val floatExit: ExitTransition
        get() = slideOutVertically(tween(180, easing = FastOutLinearInEasing)) { it / 2 } + fadeOut(tween(150))

    /** عنصر صغير يهبط من أعلى (زر الرجوع بعد رابط، زر الخروج من التركيز). */
    val dropEnter: EnterTransition
        get() = slideInVertically(spring(dampingRatio = damping(0.8f), stiffness = 500f, visibilityThreshold = IntOffset.VisibilityThreshold)) { -it / 2 } +
            fadeIn(tween(160))
    val dropExit: ExitTransition
        get() = slideOutVertically(tween(160, easing = FastOutLinearInEasing)) { -it / 2 } + fadeOut(tween(140))
}

/**
 * انتقال «المحور المشترك» الأفقي: الصفحة الجديدة تدخل من جهة التقدّم والقديمة تتراجع،
 * والرجوع يعكس الاتجاه. الاتجاه يتبع العربية أو الإنجليزية.
 */
fun <S> androidx.compose.animation.AnimatedContentTransitionScope<S>.sharedAxisX(forward: Boolean, rtl: Boolean): androidx.compose.animation.ContentTransform {
    val emph = androidx.compose.animation.core.CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    // في الإنجليزية التقدّم يأتي من اليمين، وفي العربية من اليسار
    val dir = if (forward != rtl) 1 else -1
    return androidx.compose.animation.ContentTransform(
        slideInHorizontally(tween(380, easing = emph)) { w -> dir * w / 4 } + fadeIn(tween(210, delayMillis = 90)),
        slideOutHorizontally(tween(380, easing = emph)) { w -> -dir * w / 4 } + fadeOut(tween(90))
    )
}

/** تبديل محتوى في المكان نفسه: تلاشٍ مع انزلاق رأسي قصير (أعلى أو أسفل حسب الترتيب). */
fun <S> androidx.compose.animation.AnimatedContentTransitionScope<S>.fadeShift(down: Boolean): androidx.compose.animation.ContentTransform {
    val d = if (down) 1 else -1
    return androidx.compose.animation.ContentTransform(
        slideInVertically(tween(300, easing = FastOutSlowInEasing)) { h -> d * h / 12 } + fadeIn(tween(220, delayMillis = 60)),
        fadeOut(tween(100))
    )
}

/** اهتزاز خفيف (يُوقف من الإعدادات). */
object Haptics {
    internal var view: WeakReference<View>? = null

    /** فتح قائمة، أو بداية إمساك. */
    fun tick() = perform(HapticFeedbackConstants.CLOCK_TICK)

    /** اكتمال سحب أو تحوّل القلم إلى ممحاة. */
    fun confirm() = perform(
        if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
    )

    /** الحذف. */
    fun heavy() = perform(HapticFeedbackConstants.LONG_PRESS)

    private fun perform(type: Int) {
        if (!UiPrefs.haptics) return
        try { view?.get()?.performHapticFeedback(type) } catch (_: Throwable) {}
    }
}

/** آخر قيمة غير فارغة: يبقى الشريط مرسومًا أثناء حركة خروجه بعد أن تصبح قيمته فارغة. */
@Composable
fun <T : Any> rememberLast(value: T?): T? {
    val box = remember { arrayOfNulls<Any>(1) }
    if (value != null) box[0] = value
    @Suppress("UNCHECKED_CAST")
    return value ?: box[0] as T?
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

/** حدود آخر بطاقة مستند فُتح منها مستند (بإحداثيات النافذة)، ليتمدد المستند منها. */
object DocOrigin {
    var id: String? = null
        private set
    var rect: Rect? = null
        private set

    fun record(id: String, rect: Rect?) {
        this.id = id
        this.rect = rect
    }
}

// ---------------------------------------------------------------- النوافذ

/**
 * إغلاق النافذة الحالية بحركتها. الزر يمرّر ما يفعله فيُنفَّذ بعد أن تنكمش النافذة إلى مكان فتحها.
 * إن لم يُغلق الزر النافذة (مثلًا حقل فارغ) تعود النافذة كما كانت.
 */
val LocalDialogClose = staticCompositionLocalOf<((() -> Unit) -> Unit)?> { null }

/** يلفّ ما يفعله زر الإغلاق بحركة الخروج (خارج النوافذ يُنفَّذ مباشرة). */
@Composable
fun dialogClose(action: () -> Unit): () -> Unit {
    val close = LocalDialogClose.current ?: return action
    return { close(action) }
}

/** حالة حركة نافذة: الدخول من مكان اللمسة، والخروج إليه، والرجوع التنبؤي، وتعتيم الخلفية. */
@Stable
internal class DialogMotion(private val scope: CoroutineScope) {
    val origin: Offset? = TouchOrigin.recent()
    val progress = Animatable(0f)
    var back by mutableFloatStateOf(0f)
    var bounds: Rect? = null
    var window: Window? = null
    var closing by mutableStateOf(false)
        private set
    private var dim0 = -1f

    fun open() {
        scope.launch {
            progress.animateTo(
                1f,
                if (origin != null) spring(dampingRatio = Motion.damping(0.78f), stiffness = 320f) else Motion.grow()
            )
        }
    }

    fun close(then: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch {
            val w = window
            if (dim0 < 0f) dim0 = w?.attributes?.dimAmount ?: 0f
            try {
                progress.animateTo(0f, tween(Motion.EXIT + 30, easing = FastOutLinearInEasing)) {
                    try { w?.setDimAmount(dim0 * value.coerceIn(0f, 1f)) } catch (_: Throwable) {}
                }
            } finally {
                then()
            }
            // الزر لم يُغلق النافذة: تعود كما كانت
            withFrameNanos { }
            withFrameNanos { }
            closing = false
            back = 0f
            try { w?.setDimAmount(dim0) } catch (_: Throwable) {}
            progress.animateTo(1f, Motion.grow())
        }
    }

    fun cancelBack() {
        scope.launch { animate(back, 0f, animationSpec = Motion.grow()) { v, _ -> back = v } }
    }
}

@Composable
internal fun rememberDialogMotion(): DialogMotion {
    val scope = rememberCoroutineScope()
    val m = remember { DialogMotion(scope) }
    LaunchedEffect(Unit) { m.open() }
    return m
}

/** يربط محتوى النافذة بحركتها: يكبر من مكان اللمسة وينكمش إليه، ويصغر أثناء سحب الرجوع. */
internal fun Modifier.dialogMotion(m: DialogMotion): Modifier = composed {
    val view = LocalView.current
    LaunchedEffect(view) { m.window = (view.parent as? DialogWindowProvider)?.window }
    this
        .onGloballyPositioned { m.bounds = Rect(it.positionOnScreen(), it.size.toSize()) }
        // لمسات داخل النافذة لا تمر بـ MainActivity، فنسجّلها هنا لتخرج النوافذ الفرعية منها
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    val b = m.bounds ?: continue
                    e.changes.firstOrNull { it.changedToDown() }?.let { TouchOrigin.record(b.left + it.position.x, b.top + it.position.y) }
                }
            }
        }
        .graphicsLayer {
            val v = m.progress.value
            val origin = m.origin
            val from = if (origin != null) 0.06f else 0.86f
            val s = (from + (1f - from) * v) * (1f - 0.08f * m.back)
            scaleX = s
            scaleY = s
            alpha = (v * 2.2f).coerceIn(0f, 1f)
            val b = m.bounds
            transformOrigin = if (origin != null && b != null && b.width > 0f && b.height > 0f) {
                TransformOrigin((origin.x - b.left) / b.width, (origin.y - b.top) / b.height)
            } else {
                TransformOrigin.Center
            }
        }
}

/** الرجوع التنبؤي داخل النافذة: تصغر أثناء السحب، وتُغلق عند إكماله، وتعود إن تراجع المستخدم. */
@Composable
internal fun DialogBack(m: DialogMotion, enabled: Boolean, onDismiss: () -> Unit) {
    val dismiss by rememberUpdatedState(onDismiss)
    PredictiveBackHandler(enabled = enabled && UiPrefs.predictiveBack && !m.closing) { events ->
        try {
            events.collect { m.back = it.progress }
            m.close { dismiss() }
        } catch (e: CancellationException) {
            m.cancelBack()
            throw e
        }
    }
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
    val m = rememberDialogMotion()
    val dismiss by rememberUpdatedState(onDismiss)
    val close: () -> Unit = { m.close { dismiss() } }
    CompositionLocalProvider(LocalDialogClose provides m::close) {
        androidx.compose.ui.window.Dialog(onDismissRequest = close, properties = properties) {
            DialogBack(m, properties.dismissOnBackPress) { dismiss() }
            Box(Modifier.dialogMotion(m)) { content(close) }
        }
    }
}

/** انبثاق بنابض: يكبر العنصر من وسطه مع تلاشٍ سريع. */
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
        if (pressed) spring(dampingRatio = 1f, stiffness = 1500f) else spring(dampingRatio = Motion.damping(0.6f), stiffness = 600f),
        label = "press"
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

// ---------------------------------------------------------------- القوائم المنسدلة

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

/** عناصر القائمة تظهر متتابعة من أعلى (28ms بين كل عنصر تقريبًا). */
private class CascadePolicy(private val t: () -> Float, private val dy: Float) : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val c = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val placeables = measurables.map { it.measure(c) }
        val w = (placeables.maxOfOrNull { it.width } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        val h = placeables.sumOf { it.height }
        return layout(w, h.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            var y = 0
            placeables.forEachIndexed { i, p ->
                p.placeRelativeWithLayer(0, y) {
                    val start = (i * 0.075f).coerceAtMost(0.5f)
                    val f = FastOutSlowInEasing.transform(((t() - start) / 0.5f).coerceIn(0f, 1f))
                    alpha = f
                    translationY = -dy * (1f - f)
                }
                y += p.height
            }
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables.maxOfOrNull { it.maxIntrinsicWidth(height) } ?: 0

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables.maxOfOrNull { it.minIntrinsicWidth(height) } ?: 0

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        measurables.sumOf { it.maxIntrinsicHeight(width) }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        measurables.sumOf { it.minIntrinsicHeight(width) }
}

/**
 * قائمة منسدلة تحل محل DropdownMenu في التطبيق كله (وتشمل لوحات إعدادات الأقلام):
 * تخرج من مركز الأيقونة التي فُتحت منها وتكبر بنابض، وعناصرها تظهر متتابعة، وتنكمش إليها عند الإغلاق.
 * passThrough: لمسة خارج القائمة تغلقها وتصل أيضًا لما تحتها (للكتابة على الصفحة مباشرة).
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
    passThrough: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val progress = remember { Animatable(0f) }
    val cascade = remember { Animatable(1f) }
    LaunchedEffect(expanded) {
        if (expanded) {
            Haptics.tick()
            cascade.snapTo(0f)
            launch { cascade.animateTo(1f, tween(380, easing = LinearEasing)) }
            progress.animateTo(1f, Motion.grow())
        } else {
            cascade.snapTo(1f)
            progress.animateTo(0f, tween(Motion.EXIT - 40, easing = FastOutLinearInEasing))
        }
    }
    // القائمة التي لا تمسك التركيز لا يصلها زر الرجوع: نغلقها نحن
    if (passThrough) BackHandler(enabled = expanded) { onDismissRequest() }
    // تبقى النافذة قائمة أثناء حركة الإغلاق
    val alive by remember { derivedStateOf { progress.value > 0.001f } }
    if (expanded || alive) {
        val density = LocalDensity.current
        val dx = with(density) { offset.x.roundToPx() }
        val dy = with(density) { offset.y.roundToPx() }
        val shift = with(density) { 6.dp.toPx() }
        val origin = remember { mutableStateOf(TransformOrigin(0.5f, 0f)) }
        val position = remember(dx, dy) { AnchoredMenuPosition(dx, dy) { origin.value = it } }
        val policy = remember(shift) { CascadePolicy({ cascade.value }, shift) }
        Popup(
            popupPositionProvider = position,
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = !passThrough, dismissOnClickOutside = true)
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
                Column(Modifier.width(IntrinsicSize.Max).verticalScroll(rememberScrollState())) {
                    val scope = this
                    Layout(content = { scope.content() }, measurePolicy = policy)
                }
            }
        }
    }
}

/**
 * نافذة تنبيه (تحل محل AlertDialog في التطبيق كله): تخرج من الزر الذي فُتحت منه،
 * وتنكمش إليه عند الإغلاق باللمس خارجها أو بالرجوع أو بزر يمر بـ dialogClose.
 */
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
    val m = rememberDialogMotion()
    val dismiss by rememberUpdatedState(onDismissRequest)
    CompositionLocalProvider(LocalDialogClose provides m::close) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { m.close { dismiss() } },
            confirmButton = {
                DialogBack(m, properties.dismissOnBackPress) { dismiss() }
                confirmButton()
            },
            modifier = modifier.dialogMotion(m),
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
}

/** نافذة كاملة (تحل محل Dialog في التطبيق): نفس حركة نوافذ التنبيه. */
@Composable
fun Dialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    val m = rememberDialogMotion()
    val dismiss by rememberUpdatedState(onDismissRequest)
    CompositionLocalProvider(LocalDialogClose provides m::close) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { m.close { dismiss() } }, properties = properties) {
            DialogBack(m, properties.dismissOnBackPress) { dismiss() }
            Box(Modifier.dialogMotion(m)) { content() }
        }
    }
}
