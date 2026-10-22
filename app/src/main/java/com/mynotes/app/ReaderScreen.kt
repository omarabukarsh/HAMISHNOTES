package com.mynotes.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.MotionEvent
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.absoluteOffset
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
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** أدنى مقياس للتصغير (25%). غيّر الرقم لتقليل أو زيادة التصغير. */
private const val MIN_SCALE = 0.25f

// ---------------------------------------------------------------- نقطة الدخول

@Composable
fun ReaderScreen(session: DocSession) {
    when (val st = session.status) {
        is SessionStatus.Loading -> Box(
            Modifier.fillMaxSize().background(Palette.Desk), Alignment.Center
        ) { CircularProgressIndicator() }
        is SessionStatus.Failed -> FailedView(session, st.message)
        is SessionStatus.Ready -> ReaderContent(session)
    }
}

@Composable
private fun FailedView(session: DocSession, message: String) {
    Column(
        Modifier.fillMaxSize().background(Palette.Desk).padding(24.dp),
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
    val c = AppController
    val context = LocalContext.current
    val docSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? -> if (uri != null) c.exportDocument(context, s, uri) }
    val summarySaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? -> if (uri != null) c.exportSummary(context, s, uri) }
    val exportDoc: () -> Unit = { docSaver.launch("${s.title} - مع التعليقات.pdf") }
    val exportSummary: () -> Unit = {
        if (s.allMarks().isEmpty()) c.messages.tryEmit("لا توجد ملخصات لحفظها بعد")
        else summarySaver.launch("ملخص ${s.title}.pdf")
    }

    Column(Modifier.fillMaxSize().background(Palette.Desk)) {
        ReaderTopBar(s, pdf.count, exportDoc, exportSummary)
        if (s.searchOpen) ReaderSearchBar(s)
        if (s.ocrRunning) OcrStatusBar(s)
        c.exporting?.let { ExportStatusBar(it) }
        PageArea(s, pdf, exportSummary)
    }

    val req = s.textDialog
    if (req != null) {
        TextBoxDialog(
            initial = s.textBox(req.boxId)?.text ?: "",
            isNew = req.boxId == null,
            onSave = { text ->
                c.saveTextBox(s, req, text)
                s.textDialog = null
            },
            onDelete = {
                req.boxId?.let { s.deleteTextBox(it) }
                s.textDialog = null
            },
            onDismiss = { s.textDialog = null }
        )
    }
}

@Composable
private fun ExportStatusBar(text: String) {
    Column(Modifier.fillMaxWidth().background(Palette.GoldSoft)) {
        Text(
            text, fontSize = 12.sp, color = Palette.Navy,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Palette.GoldDeep)
    }
}

@Composable
private fun TextBoxDialog(
    initial: String,
    isNew: Boolean,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            focus.requestFocus()
        } catch (e: Throwable) {
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "إضافة نص" else "تعديل النص") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("اكتب هنا…") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).focusRequester(focus)
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(if (isNew) "إضافة" else "حفظ") } },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("حذف", color = Palette.Danger) }
                TextButton(onClick = onDismiss) { Text("إلغاء") }
            }
        }
    )
}

// ---------------------------------------------------------------- شريط الأدوات

private fun tintFor(selected: Boolean): Color = if (selected) Palette.Navy else Palette.TextSecondary

@Composable
private fun TBtn(
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) Palette.NavySoft else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
        // مؤشر ذهبي صغير أسفل الأداة المختارة
        if (selected) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp)
                    .width(14.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
                    .background(Palette.Gold)
            )
        }
    }
}

@Composable
private fun ToolbarSep() {
    Box(
        Modifier.padding(horizontal = 6.dp).width(1.dp).height(26.dp).background(Palette.Divider)
    )
}

@Composable
private fun ReaderTopBar(s: DocSession, count: Int, onExportDoc: () -> Unit, onExportSummary: () -> Unit) {
    val c = AppController
    val scope = rememberCoroutineScope()
    var jump by remember { mutableStateOf(false) }
    var exportMenu by remember { mutableStateOf(false) }
    var penMenu by remember { mutableStateOf(false) }
    var colorMenu by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var addDialog by remember { mutableStateOf(false) }
    var pdfAfter by remember { mutableStateOf(0) }
    val cur = s.listState.firstVisibleItemIndex
    val pill = RoundedCornerShape(28.dp)

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) c.insertImage(s, uri, s.listState.firstVisibleItemIndex)
    }
    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) c.addPdfPages(s, uri, pdfAfter)
    }

    Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
        // زر درج الصفحات في الزاوية اليسرى
        Box(
            Modifier.align(Alignment.CenterStart).size(46.dp)
                .shadow(5.dp, CircleShape)
                .background(if (s.drawerOpen) Palette.NavySoft else Color.White, CircleShape)
                .clip(CircleShape)
                .clickable { s.drawerOpen = !s.drawerOpen },
            contentAlignment = Alignment.Center
        ) { PagesGlyph(tintFor(s.drawerOpen)) }

        // زر درج الملخصات في الزاوية المقابلة، مع عدد التمييزات
        val markCount = s.marks.values.sumOf { it.size }
        Box(Modifier.align(Alignment.CenterEnd)) {
            Box(
                Modifier.size(46.dp)
                    .shadow(5.dp, CircleShape)
                    .background(if (s.summaryOpen) Palette.GoldSoft else Color.White, CircleShape)
                    .clip(CircleShape)
                    .clickable { s.summaryOpen = !s.summaryOpen },
                contentAlignment = Alignment.Center
            ) { SummaryGlyph(tintFor(s.summaryOpen), Palette.Gold) }
            if (markCount > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(18.dp).clip(CircleShape)
                        .background(Palette.GoldDeep),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (markCount > 99) "99" else "$markCount",
                        fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // شريط الأدوات في المنتصف
        Box(
            Modifier.align(Alignment.Center).padding(horizontal = 58.dp)
                .shadow(6.dp, pill)
                .background(Color.White, pill)
                .clip(pill)
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // التنقل بين الصفحات
                TBtn(onClick = {
                    scope.launch { s.listState.animateScrollToItem((cur - 1).coerceAtLeast(0)) }
                }) { ChevronGlyph(Palette.TextPrimary, up = true) }
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Palette.Background)
                        .clickable { jump = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        "${cur + 1} / $count", fontSize = 14.sp,
                        fontWeight = FontWeight.Medium, color = Palette.TextPrimary
                    )
                }
                TBtn(onClick = {
                    scope.launch { s.listState.animateScrollToItem((cur + 1).coerceAtMost(count - 1)) }
                }) { ChevronGlyph(Palette.TextPrimary, up = false) }

                ToolbarSep()

                // الأدوات
                val penSelected = c.tool.isPen()
                val shownPen = if (penSelected) c.tool else c.lastPen
                Box {
                    TBtn(
                        selected = penSelected,
                        onClick = { if (penSelected) penMenu = true else c.selectTool(c.lastPen) }
                    ) { ToolGlyph(shownPen, tintFor(penSelected), c.colorFor(shownPen)) }
                    DropdownMenu(expanded = penMenu, onDismissRequest = { penMenu = false }) {
                        listOf(
                            Tool.BALLPOINT to "قلم جاف",
                            Tool.FOUNTAIN to "قلم حبر",
                            Tool.PENCIL to "قلم رصاص"
                        ).forEach { (t, label) ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        ToolGlyph(t, Palette.Navy, c.colorFor(t), Modifier.size(24.dp))
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
                TBtn(selected = c.tool == Tool.HIGHLIGHTER, onClick = { c.selectTool(Tool.HIGHLIGHTER) }) {
                    ToolGlyph(
                        Tool.HIGHLIGHTER, tintFor(c.tool == Tool.HIGHLIGHTER),
                        c.colorFor(Tool.HIGHLIGHTER)
                    )
                }
                // التقاط النص: يظهر عند اختيار الهايلايتر
                if (c.tool == Tool.HIGHLIGHTER) {
                    TBtn(selected = c.highlightSnap, onClick = { c.toggleHighlightSnap() }) {
                        SnapGlyph(tintFor(c.highlightSnap), c.colorFor(Tool.HIGHLIGHTER))
                    }
                }
                TBtn(selected = c.tool == Tool.ERASER, onClick = { c.selectTool(Tool.ERASER) }) {
                    ToolGlyph(Tool.ERASER, tintFor(c.tool == Tool.ERASER), Palette.Navy)
                }
                TBtn(selected = c.tool == Tool.LASER, onClick = { c.selectTool(Tool.LASER) }) {
                    ToolGlyph(Tool.LASER, tintFor(c.tool == Tool.LASER), c.colorFor(Tool.LASER))
                }

                ToolbarSep()

                // اللون
                if (c.tool != Tool.ERASER) {
                    Box {
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                                .clickable { colorMenu = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier.size(26.dp).clip(CircleShape)
                                    .background(c.colorFor(c.tool))
                                    .border(2.dp, Color.White, CircleShape)
                                    .border(3.dp, Palette.Divider, CircleShape)
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

                // الحجم: شريط من 10 أقسام، اسحب لتغيير الحجم
                Box(Modifier.padding(horizontal = 6.dp)) {
                    SizeBar(c.sizeFor(c.tool), { c.setSize(it) }, Modifier.width(150.dp))
                }

                ToolbarSep()

                // صورة / إضافة صفحة / قراءة النص / بحث
                TBtn(onClick = { imagePicker.launch(arrayOf("image/*")) }) {
                    ImageGlyph(Palette.TextSecondary)
                }
                TBtn(onClick = { s.textDialog = TextDialogReq(null, cur) }) { TextGlyph(Palette.TextSecondary) }
                TBtn(onClick = { addDialog = true }) { AddPageGlyph(Palette.TextSecondary) }
                TBtn(selected = s.ocrRunning, onClick = {
                    if (s.ocrRunning) {
                        s.stopOcr()
                    } else {
                        s.startOcr()
                        c.messages.tryEmit("قراءة النص تدعم الإنجليزية فقط، ولا تدعم العربية")
                    }
                }) { OcrGlyph(tintFor(s.ocrRunning)) }
                TBtn(selected = s.searchOpen, onClick = {
                    if (s.searchOpen) s.closeSearch() else s.searchOpen = true
                }) { SearchGlyph(tintFor(s.searchOpen)) }
                Box {
                    TBtn(enabled = c.exporting == null, onClick = { exportMenu = true }) {
                        ExportGlyph(Palette.TextSecondary)
                    }
                    DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("حفظ المستند مع التعليقات (PDF)") },
                            onClick = {
                                exportMenu = false
                                onExportDoc()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("حفظ الملخصات (PDF)") },
                            onClick = {
                                exportMenu = false
                                onExportSummary()
                            }
                        )
                    }
                }

                ToolbarSep()

                // تراجع وإعادة
                val canUndo = s.undoStack.isNotEmpty()
                val canRedo = s.redoStack.isNotEmpty()
                TBtn(enabled = canUndo, onClick = { s.undo() }) {
                    UndoGlyph(if (canUndo) Palette.TextPrimary else Palette.TextSecondary.copy(alpha = 0.35f))
                }
                TBtn(enabled = canRedo, onClick = { s.redo() }) {
                    UndoGlyph(
                        if (canRedo) Palette.TextPrimary else Palette.TextSecondary.copy(alpha = 0.35f),
                        redo = true
                    )
                }

                ToolbarSep()

                // التكبير
                val canOut = s.scale > MIN_SCALE + 0.01f
                val canIn = s.scale < 3.99f
                TBtn(enabled = canOut, onClick = { s.zoomHook?.invoke(1f / 1.25f) }) {
                    ZoomGlyph(if (canOut) Palette.TextPrimary else Palette.TextSecondary.copy(alpha = 0.35f), plus = false)
                }
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .clickable { s.zoomHook?.invoke(1f / s.scale) }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Text("${(s.scale * 100).roundToInt()}%", fontSize = 14.sp, color = Palette.TextPrimary)
                }
                TBtn(enabled = canIn, onClick = { s.zoomHook?.invoke(1.25f) }) {
                    ZoomGlyph(if (canIn) Palette.TextPrimary else Palette.TextSecondary.copy(alpha = 0.35f), plus = true)
                }
                // ملاءمة الصفحة كاملة (مفيد في الوضع الأفقي)، ونقرة ثانية تعيد ملء العرض
                TBtn(onClick = { s.fitHook?.invoke() }) { FitGlyph(Palette.TextPrimary) }
            }
        }
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
    if (addDialog) {
        AddPagesDialog(
            onBlank = { template, atEnd, color ->
                addDialog = false
                val after = if (atEnd) count - 1 else cur
                c.addBlankPage(s, after, template, color)
            },
            onPdf = { atEnd ->
                addDialog = false
                pdfAfter = if (atEnd) count - 1 else cur
                pdfPicker.launch(arrayOf("application/pdf"))
            },
            onDismiss = { addDialog = false }
        )
    }
}

@Composable
private fun AddPagesDialog(
    onBlank: (String, Boolean, Int) -> Unit,
    onPdf: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var template by remember { mutableStateOf("lined") }
    var atEnd by remember { mutableStateOf(false) }
    var paper by remember { mutableStateOf(Templates.paperColors[1].first) }
    var picker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة صفحة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("المكان", fontSize = 13.sp, color = Palette.TextSecondary)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChoiceChip("بعد الصفحة الحالية", !atEnd) { atEnd = false }
                    ChoiceChip("في نهاية الملف", atEnd) { atEnd = true }
                }
                Text("التخطيط", fontSize = 13.sp, color = Palette.TextSecondary)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Templates.options.forEach { (k, label) ->
                        ChoiceChip(label, template == k) { template = k }
                    }
                }
                Text("لون الورقة", fontSize = 13.sp, color = Palette.TextSecondary)
                PaperColorRow(paper, { paper = it }, { picker = true })
            }
        },
        confirmButton = {
            TextButton(onClick = { onBlank(template, atEnd, paper) }) { Text("إضافة صفحة") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onPdf(atEnd) }) { Text("إضافة ملف PDF…") }
                TextButton(onClick = onDismiss) { Text("إلغاء") }
            }
        }
    )
    if (picker) {
        ColorPickerDialog(
            initial = Color(paper),
            onPick = {
                paper = it.toArgb()
                picker = false
            },
            onDismiss = { picker = false }
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
            TBtn(onClick = { s.stepHit(-1) }) { ChevronGlyph(Palette.TextPrimary, up = true) }
            TBtn(onClick = { s.stepHit(1) }) { ChevronGlyph(Palette.TextPrimary, up = false) }
            IconButton(onClick = { s.closeSearch() }, modifier = Modifier.size(40.dp)) {
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
                TextButton(onClick = { s.startOcr() }) { Text("قراءة الكل", fontSize = 13.sp) }
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
                fontSize = 12.sp, color = Palette.Navy, modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { s.stopOcr() }) { Text("إيقاف", fontSize = 12.sp) }
        }
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

// ---------------------------------------------------------------- التكبير والتحريك

/**
 * قرصة بإصبعين للتكبير حول نقطة الأصابع، وسحب أفقي بإصبع واحد عند التكبير.
 * لا يتدخل في القلم، ولا في التمرير الرأسي بإصبع واحد.
 */
private fun Modifier.zoomAndPan(
    zoomed: () -> Boolean,
    onPinch: (Float, Offset, Offset) -> Unit,
    onPanX: (Float) -> Unit
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var prevDist = 0f
        var prevCentroid = Offset.Zero
        var mode = 0 // 0 غير محدد، 1 سحب أفقي، 2 تمرير رأسي، 3 قرصة
        var total = Offset.Zero
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val stylusDown = event.changes.any { it.pressed && it.type != PointerType.Touch }
            val touches = event.changes.filter { it.pressed && it.type == PointerType.Touch }
            if (!stylusDown && touches.size >= 2) {
                mode = 3
                val c = (touches[0].position + touches[1].position) / 2f
                val d = (touches[0].position - touches[1].position).getDistance()
                if (prevDist > 0f && d > 0f) onPinch(d / prevDist, prevCentroid, c)
                prevDist = d
                prevCentroid = c
                touches.forEach { it.consume() }
            } else {
                prevDist = 0f
                if (!stylusDown && touches.size == 1 && mode != 3 && zoomed()) {
                    val t = touches[0]
                    val delta = t.positionChange()
                    total += delta
                    if (mode == 0 && total.getDistance() > viewConfiguration.touchSlop) {
                        mode = if (abs(total.x) > abs(total.y)) 1 else 2
                    }
                    if (mode == 1) {
                        onPanX(delta.x)
                        t.consume()
                    }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

@Composable
private fun PageArea(s: DocSession, pdf: PdfDoc, onExportSummary: () -> Unit) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val vw = constraints.maxWidth.toFloat()
        val vh = constraints.maxHeight.toFloat()
        val pad = with(density) { 12.dp.toPx() }
        val gap = with(density) { 14.dp.toPx() }

        fun pageH(i: Int, sc: Float): Float = (vw * sc - 2f * pad).coerceAtLeast(1f) / pdf.ratio(i)

        /**
         * تكبير/تصغير بنسبة rIn مع إبقاء نقطة المحتوى التي كانت تحت prev
         * تحت now (مركز الأصابع). الحساب مطلق (فهرس صفحة + إزاحة) فلا يتأثر بتأخر التخطيط.
         */
        fun applyZoom(rIn: Float, prev: Offset, now: Offset) {
            val s0 = s.scale
            val s1 = (s0 * rIn).coerceIn(MIN_SCALE, 4f)
            val r = s1 / s0
            val n = pdf.count
            if (n <= 0) return

            // أفقي
            val newPan = if (s1 <= 1.001f) 0f
            else ((s.panX + prev.x) * r - now.x).coerceIn(0f, vw * (s1 - 1f))

            // رأسي: نحدد الصفحة والنسبة داخلها تحت أصابعنا قبل التكبير
            val first = s.listState.firstVisibleItemIndex.coerceIn(0, n - 1)
            var dFirst = 0f
            for (k in 0 until first) dFirst += pageH(k, s0) + gap
            val sPos = dFirst + s.listState.firstVisibleItemScrollOffset
            val yc = sPos + prev.y - pad
            var p = 0
            var d = 0f
            while (p < n - 1 && d + pageH(p, s0) + gap <= yc) {
                d += pageH(p, s0) + gap
                p++
            }
            val frac = if (yc <= 0f) 0f else ((yc - d) / pageH(p, s0)).coerceIn(0f, 1f)

            // ثم نحسب موضع التمرير الجديد بحيث تبقى نفس النقطة تحت now
            var d1 = 0f
            for (k in 0 until p) d1 += pageH(k, s1) + gap
            val yc1 = d1 + frac * pageH(p, s1)
            val newPos = (yc1 - now.y + pad).coerceAtLeast(0f)
            var q = 0
            var dq = 0f
            while (q < n - 1 && dq + pageH(q, s1) + gap <= newPos) {
                dq += pageH(q, s1) + gap
                q++
            }
            val off = (newPos - dq).roundToInt().coerceAtLeast(0)

            s.scale = s1
            s.panX = newPan
            scope.launch(start = CoroutineStart.UNDISPATCHED) { s.listState.scrollToItem(q, off) }
        }

        val zoomFn = rememberUpdatedState<(Float, Offset, Offset) -> Unit>(
            { r, a, b -> applyZoom(r, a, b) }
        )
        val panFn = rememberUpdatedState<(Float) -> Unit>(
            { dx -> s.panX = (s.panX - dx).coerceIn(0f, vw * (s.scale - 1f)) }
        )
        SideEffect {
            s.zoomHook = { f ->
                val center = Offset(vw / 2f, vh / 2f)
                applyZoom(f, center, center)
            }
            s.fitHook = {
                val page = s.listState.firstVisibleItemIndex.coerceIn(0, maxOf(0, pdf.count - 1))
                val fit = (((vh - 2f * pad) * pdf.ratio(page) + 2f * pad) / vw).coerceIn(MIN_SCALE, 1f)
                val target = if (abs(s.scale - fit) < 0.02f) 1f else fit
                val center = Offset(vw / 2f, vh / 2f)
                applyZoom(target / s.scale, center, center)
                scope.launch { s.listState.scrollToItem(page) }
            }
            s.jumpHook = { m ->
                val top = m.rects.minOfOrNull { it.t } ?: 0f
                val pageW = vw * s.scale - 2f * pad
                val off = (top * pageW - vh / 3f).toInt().coerceAtLeast(0)
                scope.launch { s.listState.animateScrollToItem(m.page, off) }
            }
        }

        // الانتقال إلى نتيجة البحث (عند التنقل فقط، لا عند تقدّم القراءة في الخلفية)
        LaunchedEffect(s.hitNav) {
            val h = s.hits.getOrNull(s.hitIndex) ?: return@LaunchedEffect
            val top = s.ocr[h.page]?.words?.getOrNull(h.start)?.t ?: 0f
            val pageW = vw * s.scale - 2f * pad
            val off = (top * pageW - vh / 3f).toInt().coerceAtLeast(0)
            s.listState.animateScrollToItem(h.page, off)
        }

        Box(
            Modifier.fillMaxSize().zoomAndPan(
                zoomed = { s.scale > 1.01f },
                onPinch = { r, a, b -> zoomFn.value(r, a, b) },
                onPanX = { dx -> panFn.value(dx) }
            )
        ) {
            LazyColumn(
                state = s.listState,
                modifier = Modifier.layout { measurable, constraints ->
                    val w = (constraints.maxWidth * s.scale).roundToInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(w, constraints.maxHeight))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        val x = if (w < constraints.maxWidth) (constraints.maxWidth - w) / 2 else -s.panX.roundToInt()
                        placeable.place(x, 0)
                    }
                },
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(pdf.count, key = { it }) { i ->
                    PageView(
                        pdf = pdf,
                        index = i,
                        ref = pdf.pages.getOrNull(i),
                        ratio = pdf.ratio(i),
                        strokes = s.annotations[i] ?: emptyList(),
                        pageImages = s.images[i] ?: emptyList(),
                        session = s
                    )
                }
            }
        }

        if (s.drawerOpen) {
            PagesDrawer(s, pdf, Modifier.align(Alignment.CenterStart))
        }
        if (s.summaryOpen) {
            SummaryDrawer(
                s = s,
                onJump = { m -> s.jumpHook?.invoke(m) },
                onExport = onExportSummary,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (s.selection != null) SelectionBar(s)
            if (s.selectedImageId != null) ImageBar(s)
            if (s.selectedTextId != null) TextBoxBar(s)
        }
    }
}

@Composable
private fun SelectionBar(s: DocSession) {
    val context = LocalContext.current
    val sel = s.selection ?: return
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 8.dp) {
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
            TextButton(onClick = { s.markSelection(AppController.colorFor(Tool.HIGHLIGHTER)) }) {
                Text("إلى الملخصات", color = Palette.GoldDeep)
            }
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
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 8.dp) {
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

@Composable
private fun TextBoxBar(s: DocSession) {
    val id = s.selectedTextId ?: return
    val tb = s.textBox(id)
    if (tb == null) {
        SideEffect { s.selectedTextId = null }
        return
    }
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 8.dp) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { s.textDialog = TextDialogReq(id, tb.page) }) { Text("تعديل") }
            TextButton(onClick = {
                s.beginEdit()
                s.updateTextBox(id) { it.copy(size = (it.size / 1.15f).coerceAtLeast(0.010f)) }
                s.endEdit()
            }) { Text("خط أصغر") }
            TextButton(onClick = {
                s.beginEdit()
                s.updateTextBox(id) { it.copy(size = (it.size * 1.15f).coerceAtMost(0.09f)) }
                s.endEdit()
            }) { Text("خط أكبر") }
            TextButton(onClick = {
                val pen = AppController.tool.let { if (it.isPen()) it else AppController.lastPen }
                s.beginEdit()
                s.updateTextBox(id) { it.copy(color = AppController.colorFor(pen)) }
                s.endEdit()
            }) { Text("لون القلم") }
            TextButton(onClick = { s.deleteTextBox(id) }) { Text("حذف", color = Palette.Danger) }
            TextButton(onClick = { s.selectedTextId = null }) { Text("تم") }
        }
    }
}

// ---------------------------------------------------------------- الصفحة

private class LiveInk {
    var tool: Tool = Tool.FOUNTAIN
    var color: Color = Color.Black
    var width: Float = 0.003f
    var hasPred: Boolean = false
    var predX: Float = 0f
    var predY: Float = 0f
    var predP: Float = 0.5f
}

private class LaserPt(val x: Float, val y: Float, var t: Long, val seg: Int)

private class LaserState {
    var seg: Int = 0
    var lastEvt: Long = 0L
    var present: Boolean = false
}

private fun pressureOf(c: PointerInputChange): Float = if (c.pressure > 0f) c.pressure else 0.5f

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
    ref: PageRef?,
    ratio: Float,
    strokes: List<Ink>,
    pageImages: List<ImageItem>,
    session: DocSession
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    var wPx by remember { mutableStateOf(0) }
    val renderW = if (wPx <= 0) 0 else (((wPx + 299) / 300) * 300).coerceAtMost(2400)

    val lastRef = remember { arrayOfNulls<PageRef>(1) }
    val bmp by produceState<Bitmap?>(pdf.cached(ref, renderW), pdf, ref, renderW) {
        if (lastRef[0] != ref) {
            value = pdf.cached(ref, renderW)
            lastRef[0] = ref
        }
        if (renderW > 0) {
            val b = withContext(Dispatchers.IO) {
                ensureActive()
                try {
                    pdf.render(ref, renderW)
                } catch (t: Throwable) {
                    null
                }
            }
            if (b != null) value = b
        }
    }

    val paperColor = remember(ref) {
        val src = ref?.src
        if (src != null && src.startsWith("blank:")) Color(Templates.parse(src).second) else Color.White
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
    val lz = remember { LaserState() }
    var laserOn by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }

    // تلاشي أثر الليزر وإبقاء نقطته حاضرة طالما القلم فوق الشاشة
    LaunchedEffect(laserOn) {
        if (laserOn) {
            while (true) {
                withFrameNanos { }
                val now = System.currentTimeMillis()
                if (lz.present && now - lz.lastEvt > 2500L) lz.present = false
                if (lz.present && laser.isNotEmpty()) laser[laser.lastIndex].t = now
                laser.removeAll { now - it.t > 260L }
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
    val pageMarks = session.marks[index] ?: emptyList()
    val pageTexts = session.texts[index] ?: emptyList()
    val textsNow by rememberUpdatedState(pageTexts)
    /** ارتفاع كل مربع نص بعد قياسه، نسبةً لعرض الصفحة (للنقر والسحب). */
    val textHeights = remember { mutableStateMapOf<String, Float>() }
    val density = LocalDensity.current

    val paper = RoundedCornerShape(4.dp)
    Box(
        Modifier.fillMaxWidth().aspectRatio(ratio)
            .onSizeChanged { wPx = it.width }
            .shadow(6.dp, paper)
            .background(paperColor)
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

        // التمييز الملتقط: يُرسم بمزج «ضرب» فوق صورة الصفحة فيبدو خلف النص والنص واضح فوقه
        if (pageMarks.isNotEmpty()) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                for (m in pageMarks) {
                    val col = m.color.copy(alpha = Exporter.MARK_ALPHA)
                    for (r in m.rects) {
                        drawRect(
                            col, Offset(r.l * w, r.t * w), Size((r.r - r.l) * w, (r.b - r.t) * w),
                            blendMode = BlendMode.Multiply
                        )
                    }
                }
            }
        }

        // رقم الصفحة (صغير في الزاوية، مثل دفتر الملاحظات)
        Text(
            "${index + 1}",
            fontSize = 11.sp,
            color = Palette.TextSecondary.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)
        )

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

        // مربعات النص المكتوب
        if (wPx > 0) {
            val wp = wPx.toFloat()
            pageTexts.forEach { tb ->
                key(tb.id) {
                    val fs = with(density) { (tb.size * wp).toSp() }
                    val selected = session.selectedTextId == tb.id
                    Text(
                        tb.text,
                        color = tb.color,
                        fontSize = fs,
                        lineHeight = fs * 1.25f,
                        modifier = Modifier
                            .align(AbsoluteAlignment.TopLeft)
                            .absoluteOffset { IntOffset((tb.x * wp).roundToInt(), (tb.y * wp).roundToInt()) }
                            .width(with(density) { (tb.w * wp).toDp() })
                            .onSizeChanged { textHeights[tb.id] = it.height / wp }
                            .then(
                                if (selected) Modifier.border(1.5.dp, Palette.Navy, RoundedCornerShape(3.dp))
                                else Modifier
                            )
                    )
                }
            }
            val selTb = session.selectedTextId?.let { id -> pageTexts.firstOrNull { it.id == id } }
            if (selTb != null) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = (textHeights[selTb.id] ?: 0.03f) * w
                    val cx = (selTb.x + selTb.w) * w
                    val cy = selTb.y * w + h
                    drawCircle(Palette.Navy, 9.dp.toPx(), Offset(cx, cy))
                    drawCircle(Color.White, 3.5.dp.toPx(), Offset(cx, cy))
                }
            }
        }

        // الخطوط المثبّتة
        Canvas(Modifier.fillMaxSize()) {
            strokes.forEach { drawInk(it.pts, it.color, it.width, it.tool) }
        }

        // الخط الحي + الليزر + استقبال القلم والإصبع
        Canvas(
            Modifier.fillMaxSize()
                // طلب إرسال أحداث القلم دون تجميع/تأخير لتقليل زمن الاستجابة
                .pointerInteropFilter { ev ->
                    if (ev.actionMasked == MotionEvent.ACTION_DOWN &&
                        (ev.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS ||
                            ev.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER)
                    ) {
                        view.requestUnbufferedDispatch(ev)
                    }
                    false
                }
                .pointerInput(Unit) {
                    // ---------- القلم ----------
                    awaitPointerEventScope {
                        while (true) {
                            val ev = awaitPointerEvent()

                            if (toolNow == Tool.LASER) {
                                val lc = ev.changes.firstOrNull {
                                    it.type == PointerType.Stylus || it.type == PointerType.Mouse
                                }
                                if (lc != null && size.width > 0) {
                                    if (ev.type == PointerEventType.Exit) {
                                        lz.present = false
                                    } else if (ev.type == PointerEventType.Move ||
                                        ev.type == PointerEventType.Press ||
                                        ev.type == PointerEventType.Enter
                                    ) {
                                        val w = size.width.toFloat()
                                        val nx = lc.position.x / w
                                        val ny = lc.position.y / w
                                        val now = System.currentTimeMillis()
                                        val prev = laser.lastOrNull()
                                        // قفزة أو توقف طويل = بداية مقطع جديد، فلا يُرسم خط بين الموضعين
                                        if (prev == null || now - lz.lastEvt > 160L ||
                                            hypot(nx - prev.x, ny - prev.y) > 0.12f
                                        ) {
                                            lz.seg++
                                        }
                                        laser.add(LaserPt(nx, ny, now, lz.seg))
                                        if (laser.size > 120) laser.removeAt(0)
                                        lz.lastEvt = now
                                        lz.present = true
                                        laserOn = true
                                        tick++
                                        if (lc.pressed) lc.consume()
                                    }
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
                            live.hasPred = false
                            val eraseR = eraserNow

                            AppController.lastInkAt = System.currentTimeMillis()
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

                            // آخر عيّنتين (للتنبؤ بموضع القلم وتقليل التأخر الظاهر)
                            var tA = down.uptimeMillis
                            var xA = down.position.x
                            var yA = down.position.y
                            var tB = tA
                            var xB = xA
                            var yB = yA

                            try {
                                while (true) {
                                    val e = awaitPointerEvent()
                                    val ch = e.changes.firstOrNull { it.id == id } ?: break
                                    if (!ch.pressed) {
                                        ch.consume()
                                        break
                                    }
                                    AppController.lastInkAt = System.currentTimeMillis()
                                    val pr = pressureOf(ch)
                                    if (erasing) {
                                        for (h in ch.historical) {
                                            session.eraseAt(index, Offset(h.position.x / w, h.position.y / w), eraseR)
                                        }
                                        session.eraseAt(index, Offset(ch.position.x / w, ch.position.y / w), eraseR)
                                    } else {
                                        for (h in ch.historical) {
                                            livePts.add(Pt(h.position.x / w, h.position.y / w, pr))
                                            tA = tB
                                            xA = xB
                                            yA = yB
                                            tB = h.uptimeMillis
                                            xB = h.position.x
                                            yB = h.position.y
                                        }
                                        livePts.add(Pt(ch.position.x / w, ch.position.y / w, pr))
                                        tA = tB
                                        xA = xB
                                        yA = yB
                                        tB = ch.uptimeMillis
                                        xB = ch.position.x
                                        yB = ch.position.y

                                        val dt = (tB - tA).toFloat()
                                        if (dt > 0f) {
                                            var dx = (xB - xA) / dt * 14f
                                            var dy = (yB - yA) / dt * 14f
                                            val len = hypot(dx, dy)
                                            val cap = w * 0.03f
                                            if (len > cap) {
                                                dx *= cap / len
                                                dy *= cap / len
                                            }
                                            live.predX = (xB + dx) / w
                                            live.predY = (yB + dy) / w
                                            live.predP = pr
                                            live.hasPred = true
                                        }
                                        tick++
                                    }
                                    ch.consume()
                                }
                            } finally {
                                live.hasPred = false
                                var snapPts: List<Pt>? = null
                                if (!erasing && livePts.isNotEmpty()) {
                                    if (live.tool == Tool.HIGHLIGHTER && AppController.highlightSnap) {
                                        snapPts = livePts.toList()
                                    } else {
                                        session.addInk(index, Ink(livePts.toList(), live.color, live.width, live.tool))
                                    }
                                }
                                livePts.clear()
                                tick++
                                session.endEdit()
                                // الهايلايتر الملتقط: يتحول الخط إلى تمييز خلف كلمات السطر ويُضاف إلى الملخصات
                                if (snapPts != null) session.snapHighlight(index, snapPts, live.color, live.width)
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

                        // سحب/تغيير عرض مربع النص المحدد
                        val selTextId = session.selectedTextId
                        val selText = if (selTextId != null) textsNow.firstOrNull { it.id == selTextId } else null
                        if (selText != null) {
                            val th = textHeights[selText.id] ?: 0.03f
                            val onHandle = hypot(nx - (selText.x + selText.w), ny - (selText.y + th)) < 0.04f
                            val inside = nx >= selText.x && nx <= selText.x + selText.w &&
                                ny >= selText.y && ny <= selText.y + th
                            if (onHandle || inside) {
                                down.consume()
                                session.beginEdit()
                                var last = down.position
                                var moved = false
                                try {
                                    while (true) {
                                        val e = awaitPointerEvent()
                                        val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!ch.pressed) break
                                        if (!moved && (ch.position - down.position).getDistance() < viewConfiguration.touchSlop) {
                                            continue
                                        }
                                        moved = true
                                        val dx = (ch.position.x - last.x) / w
                                        val dy = (ch.position.y - last.y) / w
                                        last = ch.position
                                        if (onHandle) {
                                            session.updateTextBox(selText.id) {
                                                it.copy(w = (it.w + dx).coerceIn(0.08f, 1f))
                                            }
                                        } else {
                                            session.updateTextBox(selText.id) {
                                                it.copy(x = it.x + dx, y = it.y + dy)
                                            }
                                        }
                                        ch.consume()
                                    }
                                } finally {
                                    session.endEdit()
                                }
                                // نقرة على المربع المحدد (بلا سحب) تفتح التعديل
                                if (!moved && inside) session.textDialog = TextDialogReq(selText.id, index)
                                return@awaitEachGesture
                            }
                        }

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
                                val textHit = textsNow.lastOrNull {
                                    val th = textHeights[it.id] ?: 0.03f
                                    nx >= it.x && nx <= it.x + it.w && ny >= it.y && ny <= it.y + th
                                }
                                if (textHit != null) {
                                    session.selectedTextId = textHit.id
                                    session.selectedImageId = null
                                    session.selection = null
                                    return@awaitEachGesture
                                }
                                if (session.selectedTextId != null) session.selectedTextId = null
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
            val extra = if (live.hasPred) Pt(live.predX, live.predY, live.predP) else null
            drawInk(livePts, live.color, live.width, live.tool, extra)

            // ليزر: نقطة حمراء متوهجة، مع أثر قصير جدًا داخل المقطع الواحد فقط
            if (laser.isNotEmpty()) {
                val w = size.width
                val now = System.currentTimeMillis()
                val r = laserSizeNow * w
                val col = laserColorNow
                for (i in 1 until laser.size) {
                    val a = laser[i - 1]
                    val c = laser[i]
                    if (a.seg != c.seg) continue
                    val alpha = (1f - (now - c.t) / 260f).coerceIn(0f, 1f)
                    drawLine(
                        col.copy(alpha = alpha * 0.55f),
                        Offset(a.x * w, a.y * w),
                        Offset(c.x * w, c.y * w),
                        strokeWidth = r * 0.9f,
                        cap = StrokeCap.Round
                    )
                }
                val head = laser.last()
                val headAlpha = (1f - (now - head.t) / 300f).coerceIn(0f, 1f)
                val center = Offset(head.x * w, head.y * w)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            col.copy(alpha = 0.60f * headAlpha),
                            col.copy(alpha = 0.22f * headAlpha),
                            Color.Transparent
                        ),
                        center = center,
                        radius = r * 3.4f
                    ),
                    radius = r * 3.4f,
                    center = center
                )
                drawCircle(col.copy(alpha = headAlpha), r * 0.9f, center)
                drawCircle(Color.White.copy(alpha = 0.92f * headAlpha), r * 0.38f, center)
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
