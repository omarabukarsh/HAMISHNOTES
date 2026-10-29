package com.mynotes.app

import androidx.compose.animation.EnterTransition
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
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
    content: @Composable ColumnScope.() -> Unit
) {
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
