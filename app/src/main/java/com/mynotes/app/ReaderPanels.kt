package com.mynotes.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

fun hsvColor(h: Float, s: Float, v: Float): Color =
    Color(
        android.graphics.Color.HSVToColor(
            floatArrayOf(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
        )
    )

// ---------------------------------------------------------------- أيقونات الأدوات

@Composable
fun ToolGlyph(tool: Tool, tint: Color, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) {
        val s = size.minDimension
        val sw = s * 0.09f
        when (tool) {
            Tool.BALLPOINT -> {
                drawLine(
                    tint, Offset(s * 0.80f, s * 0.12f), Offset(s * 0.30f, s * 0.62f),
                    strokeWidth = sw * 1.7f, cap = StrokeCap.Round
                )
                drawLine(
                    tint, Offset(s * 0.30f, s * 0.62f), Offset(s * 0.16f, s * 0.88f),
                    strokeWidth = sw * 0.8f, cap = StrokeCap.Round
                )
            }
            Tool.FOUNTAIN -> {
                val p = Path().apply {
                    moveTo(s * 0.5f, s * 0.08f)
                    lineTo(s * 0.82f, s * 0.48f)
                    lineTo(s * 0.5f, s * 0.92f)
                    lineTo(s * 0.18f, s * 0.48f)
                    close()
                }
                drawPath(p, tint, style = Stroke(width = sw))
                drawLine(
                    tint, Offset(s * 0.5f, s * 0.48f), Offset(s * 0.5f, s * 0.92f),
                    strokeWidth = sw * 0.7f
                )
                drawCircle(tint, sw * 0.9f, Offset(s * 0.5f, s * 0.48f))
            }
            Tool.PENCIL -> {
                drawLine(
                    tint, Offset(s * 0.82f, s * 0.10f), Offset(s * 0.32f, s * 0.60f),
                    strokeWidth = sw * 1.9f, cap = StrokeCap.Butt
                )
                val tip = Path().apply {
                    moveTo(s * 0.22f, s * 0.58f)
                    lineTo(s * 0.40f, s * 0.76f)
                    lineTo(s * 0.12f, s * 0.90f)
                    close()
                }
                drawPath(tip, tint)
            }
            Tool.HIGHLIGHTER -> {
                drawLine(
                    tint, Offset(s * 0.76f, s * 0.10f), Offset(s * 0.36f, s * 0.50f),
                    strokeWidth = sw * 2.6f, cap = StrokeCap.Butt
                )
                drawLine(
                    tint.copy(alpha = 0.45f), Offset(s * 0.12f, s * 0.88f), Offset(s * 0.64f, s * 0.88f),
                    strokeWidth = sw * 2.2f, cap = StrokeCap.Butt
                )
            }
            Tool.ERASER -> {
                rotate(-35f) {
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(s * 0.12f, s * 0.28f),
                        size = Size(s * 0.76f, s * 0.44f),
                        cornerRadius = CornerRadius(s * 0.12f),
                        style = Stroke(width = sw)
                    )
                    drawLine(
                        tint, Offset(s * 0.42f, s * 0.28f), Offset(s * 0.42f, s * 0.72f),
                        strokeWidth = sw
                    )
                }
            }
            Tool.LASER -> {
                val c = Offset(s * 0.5f, s * 0.5f)
                drawCircle(tint, s * 0.14f, c)
                for (i in 0 until 8) {
                    val a = (i * 45f) * (PI.toFloat() / 180f)
                    val dx = cos(a)
                    val dy = sin(a)
                    drawLine(
                        tint,
                        Offset(c.x + dx * s * 0.26f, c.y + dy * s * 0.26f),
                        Offset(c.x + dx * s * 0.42f, c.y + dy * s * 0.42f),
                        strokeWidth = sw * 0.8f, cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
fun ImageGlyph(tint: Color) {
    Canvas(Modifier.size(22.dp)) {
        val s = size.minDimension
        drawRoundRect(
            color = tint,
            topLeft = Offset(s * 0.1f, s * 0.18f),
            size = Size(s * 0.8f, s * 0.64f),
            cornerRadius = CornerRadius(s * 0.1f),
            style = Stroke(width = s * 0.08f)
        )
        drawCircle(tint, s * 0.07f, Offset(s * 0.34f, s * 0.40f))
        val p = Path().apply {
            moveTo(s * 0.16f, s * 0.74f)
            lineTo(s * 0.42f, s * 0.50f)
            lineTo(s * 0.58f, s * 0.64f)
            lineTo(s * 0.70f, s * 0.54f)
            lineTo(s * 0.84f, s * 0.74f)
            close()
        }
        drawPath(p, tint)
    }
}

// ---------------------------------------------------------------- قائمة اللون

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape)
                .then(if (selected) Modifier.border(2.dp, Palette.Navy, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(color)
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
        Text("اللون", fontSize = 13.sp, color = Palette.TextSecondary)
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
            Text("لون مخصص…", fontSize = 14.sp)
        }
    }
}

// ---------------------------------------------------------------- قائمة الحجم

@Composable
private fun SizeDot(index: Int, selected: Boolean, tool: Tool, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) Palette.NavySoft else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val d = (4 + index * 2).dp
        val col = if (selected) Palette.Navy else Palette.TextSecondary
        when (tool) {
            Tool.HIGHLIGHTER -> Box(
                Modifier.width(26.dp).height((3 + index * 2).dp)
                    .clip(RoundedCornerShape(2.dp)).background(col.copy(alpha = 0.6f))
            )
            Tool.ERASER -> Box(
                Modifier.size(d).clip(CircleShape).border(1.5.dp, col, CircleShape)
            )
            else -> Box(Modifier.size(d).clip(CircleShape).background(col))
        }
    }
}

@Composable
fun SizeMenuContent(onDone: () -> Unit) {
    val c = AppController
    val cur = c.sizeFor(c.tool)
    val label = when (c.tool) {
        Tool.ERASER -> "حجم الممحاة"
        Tool.LASER -> "حجم الليزر"
        Tool.HIGHLIGHTER -> "عرض الهايلايتر"
        else -> "سماكة القلم"
    }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text("$label  ${cur + 1}/${Sizes.COUNT}", fontSize = 13.sp, color = Palette.TextSecondary)
        Spacer(Modifier.height(6.dp))
        (0 until Sizes.COUNT).chunked(5).forEach { row ->
            Row {
                row.forEach { i ->
                    SizeDot(i, i == cur, c.tool) {
                        c.setSize(i)
                        onDone()
                    }
                }
            }
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
        title = { Text("لون مخصص") },
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
            TextButton(onClick = { onPick(current) }) { Text("اختيار") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
