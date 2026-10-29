package com.mynotes.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/** مدة الحركات في التطبيق: أطول قليلًا وأنعم من الافتراضي. */
object Motion {
    const val ENTER = 300
    const val EXIT = 200
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
 * قائمة منسدلة بظهور أنعم (تحل محل DropdownMenu في التطبيق كله):
 * نفس المكان والسلوك، ومحتواها ينفتح بتلاشٍ وتمدد.
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
    androidx.compose.material3.DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        shape = shape,
        containerColor = containerColor,
        shadowElevation = shadowElevation
    ) {
        val st = remember { MutableTransitionState(false) }.apply { targetState = true }
        AnimatedVisibility(
            visibleState = st,
            enter = fadeIn(tween(Motion.ENTER)) +
                expandVertically(tween(Motion.ENTER + 40, easing = FastOutSlowInEasing), expandFrom = Alignment.Top)
        ) {
            Column { content() }
        }
    }
}

/** نافذة تنبيه بظهور ناعم (تحل محل AlertDialog في التطبيق كله). */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties()
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.appear(),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        properties = properties
    )
}

/** نافذة كاملة بظهور ناعم (تحل محل Dialog في التطبيق). */
@Composable
fun Dialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        androidx.compose.foundation.layout.Box(Modifier.appear(Motion.ENTER, 0.96f)) { content() }
    }
}
