package com.mynotes.app

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ---------------------------------------------------------------- نقطة الدخول

@Composable
fun ReaderScreen(session: DocSession) {
    when (val st = session.status) {
        is SessionStatus.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator()
        }
        is SessionStatus.Failed -> FailedView(session, st.message)
        is SessionStatus.Ready -> ReaderContent(session)
    }
}

@Composable
private fun FailedView(session: DocSession, message: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "تعذر فتح «${session.title}»",
            fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(message, fontSize = 14.sp, color = Palette.TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { AppController.closeTab(session.id) }) { Text("إغلاق التبويب") }
            OutlinedButton(onClick = { AppController.activate(null) }) { Text("المكتبة") }
        }
    }
}

@Composable
private fun ReaderContent(s: DocSession) {
    val pdf = s.pdf ?: return
    Column(Modifier.fillMaxSize()) {
        ReaderToolbar(s, pdf.count)
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Divider))
        PageArea(s, pdf)
    }
}

// ---------------------------------------------------------------- شريط الأدوات

@Composable
private fun ReaderToolbar(s: DocSession, count: Int) {
    val c = AppController
    val scope = rememberCoroutineScope()
    var jump by remember { mutableStateOf(false) }
    val cur = s.listState.firstVisibleItemIndex

    Row(
        Modifier.fillMaxWidth().height(52.dp).background(Color.White)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // التنقل بين الصفحات
        IconButton(onClick = {
            scope.launch { s.listState.animateScrollToItem((cur - 1).coerceAtLeast(0)) }
        }) { Icon(Icons.Default.KeyboardArrowUp, "الصفحة السابقة", tint = Palette.TextPrimary) }
        Box(
            Modifier.clip(RoundedCornerShape(8.dp)).background(Palette.Background)
                .clickable { jump = true }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text("${cur + 1} / $count", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Palette.TextPrimary)
        }
        IconButton(onClick = {
            scope.launch { s.listState.animateScrollToItem((cur + 1).coerceAtMost(count - 1)) }
        }) { Icon(Icons.Default.KeyboardArrowDown, "الصفحة التالية", tint = Palette.TextPrimary) }

        ToolbarSep()

        // الأدوات
        val penOn = c.tool == Tool.PEN
        ToolToggle(penOn, { c.tool = Tool.PEN }) {
            Icon(
                Icons.Default.Create, "قلم", Modifier.size(22.dp),
                tint = if (penOn) Palette.Navy else Palette.TextSecondary
            )
        }
        val erOn = c.tool == Tool.ERASER
        ToolToggle(erOn, { c.tool = Tool.ERASER }) {
            EraserGlyph(if (erOn) Palette.Navy else Palette.TextSecondary)
        }

        ToolbarSep()

        // الألوان
        c.penColors.forEach { col ->
            ColorDot(col, c.penColor == col && c.tool == Tool.PEN) {
                c.penColor = col
                c.tool = Tool.PEN
            }
        }

        ToolbarSep()

        // السماكة
        val dotSizes = listOf(6.dp, 10.dp, 14.dp)
        c.penWidths.forEachIndexed { i, _ ->
            val sel = c.widthIndex == i
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (sel) Palette.NavySoft else Color.Transparent)
                    .clickable {
                        c.widthIndex = i
                        c.tool = Tool.PEN
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(dotSizes[i]).clip(CircleShape)
                        .background(if (sel) Palette.Navy else Palette.TextSecondary)
                )
            }
        }

        ToolbarSep()

        // تراجع وإعادة
        TextAction("↶", s.undoStack.isNotEmpty()) { s.undo() }
        TextAction("↷", s.redoStack.isNotEmpty()) { s.redo() }

        ToolbarSep()

        // التكبير
        TextAction("−", s.scale > 1.01f) { s.scale = (s.scale / 1.25f).coerceAtLeast(1f) }
        Box(
            Modifier.clip(RoundedCornerShape(8.dp)).clickable { s.scale = 1f }
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Text("${(s.scale * 100).roundToInt()}%", fontSize = 14.sp, color = Palette.TextPrimary)
        }
        TextAction("+", s.scale < 3.99f) { s.scale = (s.scale * 1.25f).coerceAtMost(4f) }
    }

    if (jump) {
        PageJumpDialog(
            count = count,
            current = cur,
            onGo = { p -> scope.launch { s.listState.scrollToItem(p) } },
            onDismiss = { jump = false }
        )
    }
}

@Composable
private fun ToolbarSep() {
    Box(
        Modifier.padding(horizontal = 6.dp).width(1.dp).height(24.dp).background(Palette.Divider)
    )
}

@Composable
private fun ToolToggle(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) Palette.NavySoft else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun TextAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, fontSize = 22.sp,
            color = if (enabled) Palette.TextPrimary else Palette.TextSecondary.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape)
                .then(if (selected) Modifier.border(2.dp, Palette.Navy, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun EraserGlyph(tint: Color) {
    Canvas(Modifier.size(22.dp)) {
        rotate(-35f) {
            drawRoundRect(
                color = tint,
                topLeft = Offset(size.width * 0.12f, size.height * 0.28f),
                size = Size(size.width * 0.76f, size.height * 0.44f),
                cornerRadius = CornerRadius(4.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )
            drawLine(
                tint,
                Offset(size.width * 0.42f, size.height * 0.28f),
                Offset(size.width * 0.42f, size.height * 0.72f),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

@Composable
private fun PageJumpDialog(count: Int, current: Int, onGo: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf((current + 1).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("الانتقال إلى صفحة") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { ch -> ch.isDigit() }.take(5) },
                singleLine = true,
                label = { Text("1 - $count") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val n = text.toIntOrNull()
                if (n != null) onGo(n.coerceIn(1, count) - 1)
                onDismiss()
            }) { Text("انتقال") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

// ---------------------------------------------------------------- منطقة الصفحات

/** تكبير بإصبعين فقط. لا يتدخل في القلم ولا في التمرير بإصبع واحد. */
private fun Modifier.pinchToZoom(onScale: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var prev = 0f
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val stylusDown = event.changes.any { it.pressed && it.type != PointerType.Touch }
            val touches = event.changes.filter { it.pressed && it.type == PointerType.Touch }
            if (!stylusDown && touches.size >= 2) {
                val d = (touches[0].position - touches[1].position).getDistance()
                if (prev > 0f && d > 0f) onScale(d / prev)
                prev = d
                touches.forEach { it.consume() }
            } else {
                prev = 0f
            }
        } while (event.changes.any { it.pressed })
    }
}

@Composable
private fun PageArea(s: DocSession, pdf: PdfDoc) {
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Palette.ReaderBackground)
            .pinchToZoom { f -> s.scale = (s.scale * f).coerceIn(1f, 4f) }
    ) {
        val viewportW = maxWidth
        Box(
            Modifier.fillMaxSize().horizontalScroll(s.hScroll, enabled = s.scale > 1.01f)
        ) {
            LazyColumn(
                state = s.listState,
                modifier = Modifier.width(viewportW * s.scale).fillMaxHeight(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(pdf.count, key = { it }) { i ->
                    PageView(
                        pdf = pdf,
                        index = i,
                        ratio = pdf.ratios.getOrElse(i) { 0.707f },
                        strokes = s.annotations[i] ?: emptyList(),
                        session = s
                    )
                }
            }
        }
    }
}

private fun pressureOf(c: PointerInputChange): Float = if (c.pressure > 0f) c.pressure else 0.5f

private fun DrawScope.drawInk(pts: List<Pt>, color: Color, width: Float) {
    if (pts.isEmpty()) return
    val w = size.width
    if (pts.size == 1) {
        val a = pts[0]
        drawCircle(color, width * w / 2f, Offset(a.x * w, a.y * w))
        return
    }
    for (j in 1 until pts.size) {
        val a = pts[j - 1]
        val c = pts[j]
        drawLine(
            color,
            Offset(a.x * w, a.y * w),
            Offset(c.x * w, c.y * w),
            strokeWidth = width * w * (0.4f + c.p),
            cap = StrokeCap.Round
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PageView(
    pdf: PdfDoc,
    index: Int,
    ratio: Float,
    strokes: List<Ink>,
    session: DocSession
) {
    var wPx by remember { mutableStateOf(0) }
    val renderW = if (wPx <= 0) 0 else (((wPx + 299) / 300) * 300).coerceAtMost(2400)

    val bmp by produceState<Bitmap?>(pdf.cached(index, renderW), pdf, index, renderW) {
        if (renderW > 0) {
            val b = withContext(Dispatchers.IO) {
                ensureActive()
                try {
                    pdf.render(index, renderW)
                } catch (t: Throwable) {
                    null
                }
            }
            if (b != null) value = b
        }
    }

    val toolNow by rememberUpdatedState(AppController.tool)
    val colorNow by rememberUpdatedState(AppController.penColor)
    val widthNow by rememberUpdatedState(AppController.penWidths[AppController.widthIndex])

    val livePts = remember { ArrayList<Pt>() }
    var tick by remember { mutableStateOf(0) }

    Box(
        Modifier.fillMaxWidth().aspectRatio(ratio)
            .onSizeChanged { wPx = it.width }
            .background(Color.White)
    ) {
        val b = bmp
        if (b != null) {
            val img = remember(b) { b.asImageBitmap() }
            Image(
                bitmap = img,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        }

        // الخطوط المثبّتة
        Canvas(Modifier.fillMaxSize()) {
            strokes.forEach { drawInk(it.pts, it.color, it.width) }
        }

        // الخط الحي + استقبال القلم
        Canvas(
            Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        val down = ev.changes.firstOrNull {
                            it.changedToDown() && (
                                it.type == PointerType.Stylus ||
                                    it.type == PointerType.Eraser ||
                                    it.type == PointerType.Mouse
                                )
                        } ?: continue
                        val w = size.width.toFloat()
                        if (w <= 0f) continue
                        val id = down.id
                        // زر القلم الجانبي = ممحاة مؤقتة (حيث يدعمه الجهاز)
                        val erasing = toolNow == Tool.ERASER ||
                            down.type == PointerType.Eraser ||
                            ev.buttons.isSecondaryPressed ||
                            ev.buttons.isTertiaryPressed
                        val penColor = colorNow
                        val penWidth = widthNow

                        session.beginEdit()
                        livePts.clear()
                        if (erasing) {
                            session.eraseAt(index, Offset(down.position.x / w, down.position.y / w))
                        } else {
                            livePts.add(Pt(down.position.x / w, down.position.y / w, pressureOf(down)))
                            tick++
                        }
                        down.consume()

                        try {
                            while (true) {
                                val e = awaitPointerEvent()
                                val ch = e.changes.firstOrNull { it.id == id } ?: break
                                if (!ch.pressed) {
                                    ch.consume()
                                    break
                                }
                                if (erasing) {
                                    for (h in ch.historical) {
                                        session.eraseAt(index, Offset(h.position.x / w, h.position.y / w))
                                    }
                                    session.eraseAt(index, Offset(ch.position.x / w, ch.position.y / w))
                                } else {
                                    val pr = pressureOf(ch)
                                    for (h in ch.historical) {
                                        livePts.add(Pt(h.position.x / w, h.position.y / w, pr))
                                    }
                                    livePts.add(Pt(ch.position.x / w, ch.position.y / w, pr))
                                    tick++
                                }
                                ch.consume()
                            }
                        } finally {
                            if (!erasing && livePts.isNotEmpty()) {
                                session.addInk(index, Ink(livePts.toList(), penColor, penWidth))
                            }
                            livePts.clear()
                            tick++
                            session.endEdit()
                        }
                    }
                }
            }
        ) {
            @Suppress("UNUSED_VARIABLE")
            val t = tick
            drawInk(livePts, colorNow, widthNow)
        }
    }
}
