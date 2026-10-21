package com.mynotes.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
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
        if (s.searchOpen) ReaderSearchBar(s)
        if (s.ocrRunning) OcrStatusBar(s)
        PageArea(s, pdf)
    }
}

// ---------------------------------------------------------------- شريط الأدوات

private fun tintFor(selected: Boolean): Color = if (selected) Palette.Navy else Palette.TextSecondary

@Composable
private fun ReaderToolbar(s: DocSession, count: Int) {
    val c = AppController
    val scope = rememberCoroutineScope()
    var jump by remember { mutableStateOf(false) }
    var penMenu by remember { mutableStateOf(false) }
    var colorMenu by remember { mutableStateOf(false) }
    var sizeMenu by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val cur = s.listState.firstVisibleItemIndex

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) c.insertImage(s, uri, s.listState.firstVisibleItemIndex)
    }

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

        // الأقلام
        val penSelected = c.tool.isPen()
        Box {
            ToolToggle(penSelected, {
                if (penSelected) penMenu = true else c.selectTool(c.lastPen)
            }) {
                ToolGlyph(if (penSelected) c.tool else c.lastPen, tintFor(penSelected))
            }
            DropdownMenu(expanded = penMenu, onDismissRequest = { penMenu = false }) {
                listOf(
                    Tool.BALLPOINT to "قلم جاف",
                    Tool.FOUNTAIN to "قلم حبر",
                    Tool.PENCIL to "قلم رصاص"
                ).forEach { (t, label) ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ToolGlyph(t, Palette.Navy, Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(label)
                            }
                        },
                        onClick = {
                            c.selectTool(t)
                            penMenu = false
                        }
                    )
                }
            }
        }
        ToolToggle(c.tool == Tool.HIGHLIGHTER, { c.selectTool(Tool.HIGHLIGHTER) }) {
            ToolGlyph(Tool.HIGHLIGHTER, tintFor(c.tool == Tool.HIGHLIGHTER))
        }
        ToolToggle(c.tool == Tool.ERASER, { c.selectTool(Tool.ERASER) }) {
            ToolGlyph(Tool.ERASER, tintFor(c.tool == Tool.ERASER))
        }
        ToolToggle(c.tool == Tool.LASER, { c.selectTool(Tool.LASER) }) {
            ToolGlyph(Tool.LASER, tintFor(c.tool == Tool.LASER))
        }

        ToolbarSep()

        // اللون
        if (c.tool != Tool.ERASER) {
            Box {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).clickable { colorMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).background(c.colorFor(c.tool))
                            .border(1.dp, Palette.Divider, CircleShape)
                    )
                }
                DropdownMenu(expanded = colorMenu, onDismissRequest = { colorMenu = false }) {
                    ColorMenuContent(
                        onCustom = {
                            colorMenu = false
                            showPicker = true
                        },
                        onDone = { colorMenu = false }
                    )
                }
            }
        }

        // الحجم (10 أحجام)
        Box {
            Box(
                Modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).clickable { sizeMenu = true }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${c.sizeFor(c.tool) + 1}", fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary
                )
            }
            DropdownMenu(expanded = sizeMenu, onDismissRequest = { sizeMenu = false }) {
                SizeMenuContent(onDone = { sizeMenu = false })
            }
        }

        ToolbarSep()

        // صورة / قراءة النص / بحث
        ToolToggle(false, { imagePicker.launch(arrayOf("image/*")) }) {
            ImageGlyph(Palette.TextSecondary)
        }
        ToolToggle(s.ocrRunning, {
            if (s.ocrRunning) {
                s.stopOcr()
            } else {
                s.startOcr()
                c.messages.tryEmit("القراءة تدعم الإنجليزية واللاتينية فقط، ولا تدعم العربية")
            }
        }) {
            Text("OCR", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tintFor(s.ocrRunning))
        }
        ToolToggle(s.searchOpen, {
            if (s.searchOpen) s.closeSearch() else s.searchOpen = true
        }) {
            Icon(Icons.Default.Search, "بحث", Modifier.size(22.dp), tint = tintFor(s.searchOpen))
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
    if (showPicker) {
        ColorPickerDialog(
            initial = c.colorFor(c.tool),
            onPick = { col ->
                c.addCustomColor(col)
                c.setColor(col)
                showPicker = false
            },
            onDismiss = { showPicker = false }
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

// ---------------------------------------------------------------- البحث وحالة القراءة

@Composable
private fun ReaderSearchBar(s: DocSession) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            focus.requestFocus()
        } catch (e: Throwable) {
        }
    }
    val total = s.pdf?.count ?: 0
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(Palette.Background).padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (s.searchQuery.isEmpty()) {
                    Text("ابحث في نص المستند", color = Palette.TextSecondary, fontSize = 15.sp, maxLines = 1)
                }
                BasicTextField(
                    value = s.searchQuery,
                    onValueChange = { s.runSearch(it) },
                    singleLine = true,
                    textStyle = TextStyle(color = Palette.TextPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(Palette.Navy),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
            }
            Text(
                when {
                    s.searchQuery.isBlank() -> ""
                    s.hits.isEmpty() -> "لا نتائج"
                    else -> "${s.hitIndex + 1}/${s.hits.size}"
                },
                fontSize = 13.sp, color = Palette.TextSecondary
            )
            IconButton(onClick = { s.stepHit(-1) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, "السابق", tint = Palette.TextPrimary)
            }
            IconButton(onClick = { s.stepHit(1) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, "التالي", tint = Palette.TextPrimary)
            }
            IconButton(onClick = { s.closeSearch() }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, "إغلاق البحث", tint = Palette.TextSecondary)
            }
        }
        if (!s.ocrRunning && s.ocr.size < total) {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "البحث يشمل ${s.ocr.size} من $total صفحة تمت قراءتها",
                    fontSize = 12.sp, color = Palette.TextSecondary, modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    s.startOcr()
                    AppController.messages.tryEmit("القراءة تدعم الإنجليزية واللاتينية فقط، ولا تدعم العربية")
                }) { Text("قراءة الكل", fontSize = 13.sp) }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Divider))
    }
}

@Composable
private fun OcrStatusBar(s: DocSession) {
    Column(Modifier.fillMaxWidth().background(Palette.NavySoft)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "جارٍ قراءة النص: ${s.ocrDone} / ${s.ocrTotal}",
                fontSize = 13.sp, color = Palette.Navy, modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { s.stopOcr() }) { Text("إيقاف", fontSize = 13.sp) }
        }
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
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
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Palette.ReaderBackground)
            .pinchToZoom { f -> s.scale = (s.scale * f).coerceIn(1f, 4f) }
    ) {
        val viewportW = maxWidth
        val viewportWpx = constraints.maxWidth
        val viewportHpx = constraints.maxHeight

        // الانتقال إلى نتيجة البحث (عند التنقل فقط، لا عند تقدّم القراءة في الخلفية)
        LaunchedEffect(s.hitNav) {
            val h = s.hits.getOrNull(s.hitIndex) ?: return@LaunchedEffect
            val top = s.ocr[h.page]?.words?.getOrNull(h.start)?.t ?: 0f
            val pageW = viewportWpx * s.scale - with(density) { 24.dp.toPx() }
            val off = (top * pageW - viewportHpx / 3f).toInt().coerceAtLeast(0)
            s.listState.animateScrollToItem(h.page, off)
        }

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
                        pageImages = s.images[i] ?: emptyList(),
                        session = s
                    )
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (s.selection != null) SelectionBar(s)
            if (s.selectedImageId != null) ImageBar(s)
        }
    }
}

@Composable
private fun SelectionBar(s: DocSession) {
    val context = LocalContext.current
    val sel = s.selection ?: return
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White, shadowElevation = 8.dp) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                val text = s.selectionText()
                if (text.isNotEmpty()) {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("MyNotes", text))
                    AppController.messages.tryEmit("تم نسخ النص")
                }
            }) { Text("نسخ") }
            TextButton(onClick = {
                val n = s.ocr[sel.page]?.words?.size ?: 0
                if (n > 0) s.selection = TextSelection(sel.page, 0, n - 1)
            }) { Text("تحديد كل الصفحة") }
            TextButton(onClick = { s.selection = null }) { Text("إلغاء") }
        }
    }
}

@Composable
private fun ImageBar(s: DocSession) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White, shadowElevation = 8.dp) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { s.deleteSelectedImage() }) {
                Text("حذف الصورة", color = Palette.Danger)
            }
            TextButton(onClick = {
                val id = s.selectedImageId
                if (id != null) {
                    s.beginEdit()
                    s.updateImage(id) { it.copy(w = (it.w * 0.87f).coerceAtLeast(0.05f)) }
                    s.endEdit()
                }
            }) { Text("تصغير") }
            TextButton(onClick = {
                val id = s.selectedImageId
                if (id != null) {
                    s.beginEdit()
                    s.updateImage(id) { it.copy(w = (it.w * 1.15f).coerceAtMost(1.5f)) }
                    s.endEdit()
                }
            }) { Text("تكبير") }
            TextButton(onClick = { s.selectedImageId = null }) { Text("تم") }
        }
    }
}

// ---------------------------------------------------------------- الرسم

private class LiveInk {
    var tool: Tool = Tool.FOUNTAIN
    var color: Color = Color.Black
    var width: Float = 0.003f
}

private class LaserPt(val x: Float, val y: Float, var t: Long)

private fun pressureOf(c: PointerInputChange): Float = if (c.pressure > 0f) c.pressure else 0.5f

private fun buildPath(pts: List<Pt>, w: Float): Path {
    val path = Path()
    path.moveTo(pts[0].x * w, pts[0].y * w)
    if (pts.size == 1) {
        path.lineTo(pts[0].x * w + 0.01f, pts[0].y * w)
        return path
    }
    for (i in 1 until pts.size - 1) {
        val mx = (pts[i].x + pts[i + 1].x) / 2f * w
        val my = (pts[i].y + pts[i + 1].y) / 2f * w
        path.quadraticBezierTo(pts[i].x * w, pts[i].y * w, mx, my)
    }
    path.lineTo(pts.last().x * w, pts.last().y * w)
    return path
}

private fun DrawScope.drawInk(pts: List<Pt>, color: Color, width: Float, tool: Tool) {
    if (pts.isEmpty()) return
    val w = size.width
    when (tool) {
        Tool.FOUNTAIN -> {
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
        Tool.HIGHLIGHTER -> drawPath(
            buildPath(pts, w),
            color.copy(alpha = 0.35f),
            style = Stroke(width = width * w, cap = StrokeCap.Square, join = StrokeJoin.Round)
        )
        Tool.PENCIL -> drawPath(
            buildPath(pts, w),
            color.copy(alpha = 0.78f),
            style = Stroke(width = width * w * 0.85f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        else -> drawPath(
            buildPath(pts, w),
            color,
            style = Stroke(width = width * w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

private fun nearestWord(words: List<OcrWord>, x: Float, y: Float, maxDist: Float): Int {
    var best = -1
    var bestD = Float.MAX_VALUE
    for (i in words.indices) {
        val w = words[i]
        val dx = when {
            x < w.l -> w.l - x
            x > w.r -> x - w.r
            else -> 0f
        }
        val dy = when {
            y < w.t -> w.t - y
            y > w.b -> y - w.b
            else -> 0f
        }
        val d = hypot(dx, dy)
        if (d < bestD) {
            bestD = d
            best = i
        }
    }
    return if (bestD <= maxDist) best else -1
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PageView(
    pdf: PdfDoc,
    index: Int,
    ratio: Float,
    strokes: List<Ink>,
    pageImages: List<ImageItem>,
    session: DocSession
) {
    val scope = rememberCoroutineScope()
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

    // الصور المضافة
    val bitmaps = remember { mutableStateMapOf<String, ImageBitmap>() }
    pageImages.forEach { item ->
        key(item.file) {
            LaunchedEffect(item.file) {
                if (!bitmaps.containsKey(item.file)) {
                    val b = withContext(Dispatchers.IO) {
                        val path = AppController.repo.imageFile(session.id, item.file).absolutePath
                        ImageCache.get(path) ?: BitmapFactory.decodeFile(path)?.also { ImageCache.put(path, it) }
                    }
                    if (b != null) bitmaps[item.file] = b.asImageBitmap()
                }
            }
        }
    }

    val toolNow by rememberUpdatedState(AppController.tool)
    val colorNow by rememberUpdatedState(AppController.colorFor(AppController.tool))
    val widthNow by rememberUpdatedState(
        Sizes.width(AppController.tool, AppController.sizeFor(AppController.tool))
    )
    val eraserNow by rememberUpdatedState(Sizes.width(Tool.ERASER, AppController.sizeFor(Tool.ERASER)))
    val laserColorNow by rememberUpdatedState(AppController.colorFor(Tool.LASER))
    val laserSizeNow by rememberUpdatedState(Sizes.width(Tool.LASER, AppController.sizeFor(Tool.LASER)))
    val imagesNow by rememberUpdatedState(pageImages)

    val live = remember { LiveInk() }
    val livePts = remember { ArrayList<Pt>() }
    val laser = remember { ArrayList<LaserPt>() }
    val lastLaserEvent = remember { LongArray(1) }
    var laserOn by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }

    // تلاشي أثر الليزر
    LaunchedEffect(laserOn) {
        if (laserOn) {
            while (true) {
                withFrameNanos { }
                val now = System.currentTimeMillis()
                if (laser.isNotEmpty() && now - lastLaserEvent[0] < 1200L) {
                    laser[laser.lastIndex].t = now
                }
                laser.removeAll { now - it.t > 900L }
                tick++
                if (laser.isEmpty()) {
                    laserOn = false
                    break
                }
            }
        }
    }

    // حالات التمييز والتحديد
    val sel = session.selection?.takeIf { it.page == index }
    val words = session.ocr[index]?.words
    val pageHits = session.hits.filter { it.page == index }
    val curHit = session.hits.getOrNull(session.hitIndex)
    val selImg = session.selectedImageId?.let { id -> pageImages.firstOrNull { it.id == id } }

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

        // طبقة الصور
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            for (item in pageImages) {
                val bm = bitmaps[item.file] ?: continue
                drawImage(
                    image = bm,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(bm.width, bm.height),
                    dstOffset = IntOffset((item.x * w).roundToInt(), (item.y * w).roundToInt()),
                    dstSize = IntSize(
                        (item.w * w).roundToInt().coerceAtLeast(1),
                        (item.w / item.aspect * w).roundToInt().coerceAtLeast(1)
                    )
                )
            }
        }

        // طبقة التمييز: نتائج البحث + النص المحدد + إطار الصورة المحددة
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            if (words != null) {
                for (h in pageHits) {
                    val isCur = curHit != null && curHit.page == h.page && curHit.start == h.start
                    val col = if (isCur) Color(0x99FF9800) else Color(0x66FFEB3B)
                    for (i in h.start..h.end) {
                        val wd = words.getOrNull(i) ?: continue
                        drawRect(
                            col, Offset(wd.l * w, wd.t * w),
                            Size((wd.r - wd.l) * w, (wd.b - wd.t) * w)
                        )
                    }
                }
                if (sel != null) {
                    val a = minOf(sel.start, sel.end)
                    val e = maxOf(sel.start, sel.end)
                    for (i in a..e) {
                        val wd = words.getOrNull(i) ?: continue
                        drawRect(
                            Color(0x553D7BF5), Offset(wd.l * w, wd.t * w),
                            Size((wd.r - wd.l) * w, (wd.b - wd.t) * w)
                        )
                    }
                }
            }
            if (selImg != null) {
                val x = selImg.x * w
                val y = selImg.y * w
                val ww = selImg.w * w
                val hh = selImg.w / selImg.aspect * w
                drawRect(Palette.Navy, Offset(x, y), Size(ww, hh), style = Stroke(width = 2.dp.toPx()))
                drawCircle(Palette.Navy, 10.dp.toPx(), Offset(x + ww, y + hh))
                drawCircle(Color.White, 4.dp.toPx(), Offset(x + ww, y + hh))
            }
        }

        // الخطوط المثبّتة
        Canvas(Modifier.fillMaxSize()) {
            strokes.forEach { drawInk(it.pts, it.color, it.width, it.tool) }
        }

        // الخط الحي + الليزر + استقبال القلم والإصبع
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    // ---------- القلم ----------
                    awaitPointerEventScope {
                        while (true) {
                            val ev = awaitPointerEvent()

                            if (toolNow == Tool.LASER) {
                                val lc = ev.changes.firstOrNull {
                                    it.type == PointerType.Stylus || it.type == PointerType.Mouse
                                }
                                if (lc != null && size.width > 0 &&
                                    (ev.type == PointerEventType.Move ||
                                        ev.type == PointerEventType.Press ||
                                        ev.type == PointerEventType.Enter)
                                ) {
                                    val w = size.width.toFloat()
                                    val now = System.currentTimeMillis()
                                    laser.add(LaserPt(lc.position.x / w, lc.position.y / w, now))
                                    if (laser.size > 240) laser.removeAt(0)
                                    lastLaserEvent[0] = now
                                    laserOn = true
                                    tick++
                                    if (lc.pressed) lc.consume()
                                }
                                continue
                            }

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
                            live.tool = toolNow
                            live.color = colorNow
                            live.width = widthNow
                            val eraseR = eraserNow

                            if (session.selection != null) session.selection = null
                            session.beginEdit()
                            livePts.clear()
                            if (erasing) {
                                session.eraseAt(index, Offset(down.position.x / w, down.position.y / w), eraseR)
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
                                            session.eraseAt(index, Offset(h.position.x / w, h.position.y / w), eraseR)
                                        }
                                        session.eraseAt(index, Offset(ch.position.x / w, ch.position.y / w), eraseR)
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
                                    session.addInk(index, Ink(livePts.toList(), live.color, live.width, live.tool))
                                }
                                livePts.clear()
                                tick++
                                session.endEdit()
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    // ---------- الإصبع: صور، نقر، ضغط مطوّل لتحديد النص ----------
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (down.type != PointerType.Touch) return@awaitEachGesture
                        val w = size.width.toFloat()
                        if (w <= 0f) return@awaitEachGesture
                        val nx = down.position.x / w
                        val ny = down.position.y / w

                        // سحب/تغيير حجم الصورة المحددة
                        val selId = session.selectedImageId
                        val selItem = if (selId != null) imagesNow.firstOrNull { it.id == selId } else null
                        if (selItem != null) {
                            val ih = selItem.w / selItem.aspect
                            val onHandle = hypot(nx - (selItem.x + selItem.w), ny - (selItem.y + ih)) < 0.04f
                            val inside = nx >= selItem.x && nx <= selItem.x + selItem.w &&
                                ny >= selItem.y && ny <= selItem.y + ih
                            if (onHandle || inside) {
                                down.consume()
                                session.beginEdit()
                                var last = down.position
                                try {
                                    while (true) {
                                        val e = awaitPointerEvent()
                                        val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!ch.pressed) break
                                        val dx = (ch.position.x - last.x) / w
                                        val dy = (ch.position.y - last.y) / w
                                        last = ch.position
                                        if (onHandle) {
                                            session.updateImage(selItem.id) {
                                                it.copy(w = (it.w + dx).coerceIn(0.05f, 1.5f))
                                            }
                                        } else {
                                            session.updateImage(selItem.id) {
                                                it.copy(x = it.x + dx, y = it.y + dy)
                                            }
                                        }
                                        ch.consume()
                                    }
                                } finally {
                                    session.endEdit()
                                }
                                return@awaitEachGesture
                            }
                        }

                        // نقر قصير أو ضغط مطوّل
                        try {
                            val up = withTimeout(viewConfiguration.longPressTimeoutMillis) {
                                waitForUpOrCancellation()
                            }
                            if (up != null) {
                                val hit = imagesNow.lastOrNull {
                                    nx >= it.x && nx <= it.x + it.w &&
                                        ny >= it.y && ny <= it.y + it.w / it.aspect
                                }
                                if (hit != null) {
                                    session.selectedImageId = hit.id
                                    session.selection = null
                                } else {
                                    if (session.selectedImageId != null) session.selectedImageId = null
                                    if (session.selection != null) session.selection = null
                                }
                            }
                        } catch (e: PointerEventTimeoutCancellationException) {
                            // ضغط مطوّل: تحديد نص
                            val pageWords = session.ocr[index]?.words
                            if (pageWords != null && pageWords.isNotEmpty()) {
                                val startIdx = nearestWord(pageWords, nx, ny, 0.05f)
                                if (startIdx >= 0) {
                                    session.selectedImageId = null
                                    session.selection = TextSelection(index, startIdx, startIdx)
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!ch.pressed) break
                                        val idx = nearestWord(
                                            pageWords, ch.position.x / w, ch.position.y / w, 10f
                                        )
                                        if (idx >= 0) session.selection = TextSelection(index, startIdx, idx)
                                        ch.consume()
                                    }
                                } else {
                                    AppController.messages.tryEmit("لا يوجد نص عند هذه النقطة")
                                }
                            } else {
                                // الصفحة لم تُقرأ بعد: اقرأها الآن ثم حدّد الكلمة
                                scope.launch {
                                    session.pageBusy = index
                                    val page = session.ocrPage(index)
                                    session.pageBusy = null
                                    val list = page?.words
                                    if (list == null || list.isEmpty()) {
                                        AppController.messages.tryEmit("لم يُعثر على نص مقروء في هذه الصفحة")
                                    } else {
                                        val i = nearestWord(list, nx, ny, 0.06f)
                                        if (i >= 0) {
                                            session.selectedImageId = null
                                            session.selection = TextSelection(index, i, i)
                                        } else {
                                            AppController.messages.tryEmit("لا يوجد نص عند هذه النقطة")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            @Suppress("UNUSED_VARIABLE")
            val t = tick
            drawInk(livePts, live.color, live.width, live.tool)

            // أثر الليزر
            if (laser.isNotEmpty()) {
                val w = size.width
                val now = System.currentTimeMillis()
                val r = laserSizeNow * w
                for (i in 1 until laser.size) {
                    val a = laser[i - 1]
                    val c = laser[i]
                    val alpha = (1f - (now - c.t) / 900f).coerceIn(0f, 1f)
                    drawLine(
                        laserColorNow.copy(alpha = alpha * 0.7f),
                        Offset(a.x * w, a.y * w),
                        Offset(c.x * w, c.y * w),
                        strokeWidth = r * 1.1f,
                        cap = StrokeCap.Round
                    )
                }
                val head = laser.last()
                val headAlpha = (1f - (now - head.t) / 900f).coerceIn(0f, 1f)
                drawCircle(
                    laserColorNow.copy(alpha = 0.25f * headAlpha), r * 2.2f,
                    Offset(head.x * w, head.y * w)
                )
                drawCircle(
                    laserColorNow.copy(alpha = headAlpha), r,
                    Offset(head.x * w, head.y * w)
                )
                drawCircle(
                    Color.White.copy(alpha = 0.8f * headAlpha), r * 0.35f,
                    Offset(head.x * w, head.y * w)
                )
            }
        }

        if (session.pageBusy == index) {
            Row(
                Modifier.align(Alignment.TopCenter).padding(8.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.92f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("جارٍ قراءة الصفحة…", fontSize = 13.sp, color = Palette.TextPrimary)
            }
        }
    }
}
