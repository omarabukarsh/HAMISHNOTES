package com.hamishnotes.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun hsvColor(h: Float, s: Float, v: Float): Color =
    Color(
        android.graphics.Color.HSVToColor(
            floatArrayOf(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
        )
    )

// ---------------------------------------------------------------- قائمة اللون

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape)
                .then(if (selected) Modifier.border(2.5.dp, Palette.Navy, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.size(23.dp).clip(CircleShape).background(color)
                    .border(1.dp, Palette.Divider, CircleShape)
            )
        }
    }
}

@Composable
fun ColorMenuContent(onCustom: () -> Unit, onDone: () -> Unit) {
    val c = AppController
    val current = c.colorFor(c.tool)
    val colors = c.paletteFor(c.tool) + c.customColors.toList()
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(L("اللون", "Color"), fontSize = 13.sp, color = Palette.TextSecondary)
        Spacer(Modifier.height(6.dp))
        colors.chunked(5).forEach { row ->
            Row {
                row.forEach { col ->
                    ColorSwatch(col, col == current) {
                        c.setColor(col)
                        onDone()
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onCustom, shape = RoundedCornerShape(10.dp)) {
            Text(L("لون مخصص…", "Custom color…"), fontSize = 14.sp)
        }
    }
}

// ---------------------------------------------------------------- شريط الحجم (10 أقسام)

/** شريط مقسّم إلى 10 أقسام؛ المس أو اسحب لتغيير الحجم. value من 0 إلى 9. */
@Composable
fun SizeBar(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val callback by rememberUpdatedState(onChange)
    Canvas(
        modifier.height(38.dp).pointerInput(Unit) {
            awaitEachGesture {
                val d = awaitFirstDown()
                fun update(x: Float) {
                    val i = ((x / size.width) * Sizes.COUNT).toInt().coerceIn(0, Sizes.COUNT - 1)
                    callback(i)
                }
                update(d.position.x)
                d.consume()
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == d.id } ?: break
                    if (!ch.pressed) break
                    update(ch.position.x)
                    ch.consume()
                }
            }
        }
    ) {
        val n = Sizes.COUNT
        val gap = 3.dp.toPx()
        val segW = (size.width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val frac = (i + 1) / n.toFloat()
            val h = size.height * (0.28f + 0.72f * frac)
            val x = i * (segW + gap)
            drawRoundRect(
                color = if (i <= value) Palette.Navy else Palette.Divider,
                topLeft = Offset(x, size.height - h),
                size = Size(segW, h),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
        }
    }
}

// ---------------------------------------------------------------- منتقي اللون المخصص

@Composable
fun ColorPickerDialog(initial: Color, onPick: (Color) -> Unit, onDismiss: () -> Unit) {
    val start = remember {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initial.toArgb(), hsv)
        hsv
    }
    var hue by remember { mutableStateOf(start[0]) }
    var sat by remember { mutableStateOf(start[1]) }
    var value by remember { mutableStateOf(start[2]) }
    val current = hsvColor(hue, sat, value)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("لون مخصص", "Custom color")) },
        text = {
            Column {
                // مربع التشبّع والسطوع
                Box(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(10.dp))) {
                    Box(
                        Modifier.fillMaxSize()
                            .background(Brush.horizontalGradient(listOf(Color.White, hsvColor(hue, 1f, 1f))))
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val d = awaitFirstDown()
                                    fun update(p: Offset) {
                                        sat = (p.x / size.width).coerceIn(0f, 1f)
                                        value = 1f - (p.y / size.height).coerceIn(0f, 1f)
                                    }
                                    update(d.position)
                                    d.consume()
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        val ch = ev.changes.firstOrNull { it.id == d.id } ?: break
                                        if (!ch.pressed) break
                                        update(ch.position)
                                        ch.consume()
                                    }
                                }
                            }
                    )
                    Canvas(Modifier.matchParentSize()) {
                        drawCircle(
                            Color.White, 9.dp.toPx(),
                            Offset(sat * size.width, (1f - value) * size.height),
                            style = Stroke(width = 2.dp.toPx())
                        )
                        drawCircle(
                            Color.Black, 10.dp.toPx(),
                            Offset(sat * size.width, (1f - value) * size.height),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                // شريط الدرجة اللونية
                Box(
                    Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
                        .background(
                            Brush.horizontalGradient((0..6).map { hsvColor(it * 60f, 1f, 1f) })
                        )
                )
                Slider(
                    value = hue,
                    onValueChange = { hue = it },
                    valueRange = 0f..360f
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(current)
                            .border(1.dp, Palette.Divider, CircleShape)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        String.format("#%06X", current.toArgb() and 0xFFFFFF),
                        fontSize = 15.sp, fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(current) }) { Text(L("اختيار", "Choose")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(L("إلغاء", "Cancel")) }
        }
    )
}
