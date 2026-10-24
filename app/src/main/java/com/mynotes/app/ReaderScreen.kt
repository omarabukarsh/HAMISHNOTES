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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.hypot
import kotlin.math.roundToInt

/** «الخط المستمر» لليزر: يبقى ظاهرًا بعد آخر رسم ثم يتلاشى خلال مدة التلاشي. */
private const val LASER_LINE_HOLD = 1500L
private const val LASER_LINE_FADE = 450L

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
            L("تعذر فتح «${session.title}»", "Could not open “${session.title}”"),
            fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(message, fontSize = 14.sp, color = Palette.TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { AppController.closeTab(session.id) }) { Text(L("إغلاق التبويب", "Close tab")) }
            OutlinedButton(onClick = { AppController.activate(null) }) { Text(L("المكتبة", "Library")) }
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
    val safeTitle = s.title.replace(Regex("[\\\\/:*?\"<>|]"), "-").trim().ifEmpty { L("مستند", "Document") }
    val exportDoc: () -> Unit = { docSaver.launch(L("$safeTitle - مع التعليقات.pdf", "$safeTitle - annotated.pdf")) }
    val exportSummary: () -> Unit = {
        if (s.allMarks().isEmpty()) c.messages.tryEmit(L("لا توجد ملخصات لحفظها بعد", "No summaries to save yet"))
        else summarySaver.launch(L("ملخص $safeTitle.pdf", "Summary $safeTitle.pdf"))
    }

    // إيقاف أداة التحديد الحر يلغي التحديد
    LaunchedEffect(c.lassoActive) { if (!c.lassoActive) s.lassoSel = null }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(Palette.Desk)) {
            if (!c.focusMode) {
                ReaderTopBar(s, pdf.count, exportDoc, exportSummary)
                // شريط الأقلام الثاني (نمط «شريط الأقلام»)
                AnimatedVisibility(
                    visible = c.toolbarStyle == ToolbarStyle.PEN_BAR && c.penBarOpen && !c.lassoActive &&
                        (c.tool.isPen() || c.tool == Tool.HIGHLIGHTER),
                    enter = fadeIn(tween(160)) + expandVertically(tween(200)),
                    exit = fadeOut(tween(140)) + shrinkVertically(tween(180))
                ) { PenBar() }
                if (s.searchOpen) ReaderSearchBar(s)
                c.exporting?.let { ExportStatusBar(it) }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { PageArea(s, pdf, exportSummary) }
            // نافذة الكتابة المكبّرة
            if (s.zoomBox != null && !c.focusMode) ZoomPanel(s, pdf)
        }
        if (c.focusMode) FocusOverlay(Modifier.align(Alignment.TopEnd).padding(14.dp))
        // مشغّل التسجيل الصوتي
        AudioNotes.playing?.let { rec ->
            if (rec.docId == s.id) AudioPlayerBar(rec, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
        // رجوع بعد تتبّع رابط
        c.linkBack?.let { (backId, backPage) ->
            run {
                val backName = c.library.firstOrNull { it.id == backId }?.name ?: ""
                Row(
                    Modifier.align(Alignment.BottomEnd).padding(16.dp)
                        .shadow(6.dp, RoundedCornerShape(20.dp))
                        .background(Color.White, RoundedCornerShape(20.dp))
                        .padding(start = 4.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { c.linkBack = null; c.openPage(backId, backPage) }) {
                        Text(L("↩ رجوع إلى $backName ص ${backPage + 1}", "↩ Back to $backName p. ${backPage + 1}"), color = Palette.Navy, maxLines = 1)
                    }
                    IconButton(onClick = { c.linkBack = null }) { PhIcon(R.drawable.ph_close, Palette.TextSecondary, 16.dp) }
                }
            }
        }
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

/** أدوات وضع التركيز: مؤقت الدراسة والخروج. */
@Composable
private fun FocusOverlay(modifier: Modifier) {
    val c = AppController
    val pill = RoundedCornerShape(24.dp)
    Row(
        modifier.shadow(6.dp, pill).background(Color.White, pill).border(1.dp, Palette.Divider, pill)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PomodoroControl()
        IconButton(onClick = { c.focusMode = false }) {
            PhIcon(R.drawable.ph_focus_exit, Palette.TextPrimary, 20.dp, L("الخروج من وضع التركيز", "Exit focus mode"))
        }
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
        title = { Text(if (isNew) L("إضافة نص", "Add text") else L("تعديل النص", "Edit text")) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(L("اكتب هنا…", "Write here…")) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).focusRequester(focus)
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(if (isNew) L("إضافة", "Add") else L("حفظ", "Save")) } },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text(L("حذف", "Delete"), color = Palette.Danger) }
                TextButton(onClick = onDismiss) { Text(L("إلغاء", "Cancel")) }
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
    var settingsFor by remember { mutableStateOf<String?>(null) }
    // تغيّر الأداة (مثل زر القلم الجانبي) يغلق أي لوحة إعدادات مفتوحة
    LaunchedEffect(c.tool) { settingsFor = null }
    var showPicker by remember { mutableStateOf(false) }
    var addDialog by remember { mutableStateOf(false) }
    var linkDialog by remember { mutableStateOf(false) }
    var audioDialog by remember { mutableStateOf(false) }
    var splitDialog by remember { mutableStateOf(false) }
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
                    .clickable { s.summaryOpen = !s.summaryOpen; if (s.summaryOpen) s.pageMenuOpen = false },
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
                .shadow(8.dp, pill, ambientColor = Palette.Ink, spotColor = Palette.Ink)
                .background(Color.White, pill)
                .border(1.dp, Palette.Divider, pill)
                .clip(pill)
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val v: (String) -> Boolean = { c.toolVisible(it) }
                val dim = Palette.TextSecondary.copy(alpha = 0.35f)

                // التنقل بين الصفحات
                if (v("nav")) {
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
                }
                // علامة مرجعية للصفحة الحالية
                if (v("bookmark")) {
                    val marked = cur in s.bookmarks
                    TBtn(selected = marked, onClick = { s.toggleBookmark(cur) }) {
                        PhIcon(
                            if (marked) R.drawable.ph_bookmark_fill else R.drawable.ph_bookmark,
                            if (marked) Palette.GoldDeep else Palette.TextSecondary
                        )
                    }
                }
                // مؤقت الدراسة: يعمل ويتوقف مؤقتًا هنا وفي وضع التركيز
                if (v("timer")) PomodoroControl(showStartLabel = false)
                if (v("nav") || v("bookmark") || v("timer")) ToolbarSep()

                // الأدوات الأساسية
                if (c.toolbarStyle == ToolbarStyle.PEN_BAR) {
                    // زر قلم واحد: يفتح شريط الأقلام والهايلايتر
                    val writing = (c.tool.isPen() || c.tool == Tool.HIGHLIGHTER) && !c.lassoActive
                    val shown = if (writing) c.tool else c.lastWriting
                    TBtn(selected = writing, onClick = {
                        if (!writing) {
                            c.selectTool(c.lastWriting)
                            c.penBarOpen = true
                        } else {
                            c.penBarOpen = !c.penBarOpen
                        }
                    }) {
                        ToolGlyph(shown, tintFor(writing), c.colorFor(shown), filled = writing)
                    }
                } else {
                    // الأدوات: نقرة تختار الأداة، ونقرة ثانية على الأداة المختارة تفتح إعداداتها
                    val penSelected = c.tool.isPen() && !c.lassoActive
                    val shownPen = if (c.tool.isPen()) c.tool else c.lastPen
                    ToolSlot(
                        tool = shownPen, selected = penSelected, open = settingsFor == "pen",
                        onTap = { if (penSelected) settingsFor = "pen" else c.selectToolWithHint(c.lastPen) },
                        onDismiss = { settingsFor = null },
                        onCustomColor = { settingsFor = null; showPicker = true }
                    )
                    ToolSlot(
                        tool = Tool.HIGHLIGHTER, selected = c.tool == Tool.HIGHLIGHTER && !c.lassoActive, open = settingsFor == "hl",
                        onTap = { if (c.tool == Tool.HIGHLIGHTER && !c.lassoActive) settingsFor = "hl" else c.selectToolWithHint(Tool.HIGHLIGHTER) },
                        onDismiss = { settingsFor = null },
                        onCustomColor = { settingsFor = null; showPicker = true }
                    )
                    // التقاط النص: اختصار سريع يظهر عند اختيار الهايلايتر (وموجود أيضًا في إعداداته)
                    if (c.tool == Tool.HIGHLIGHTER && v("snap")) {
                        TBtn(selected = c.highlightSnap, onClick = { c.toggleHighlightSnap() }) {
                            SnapGlyph(tintFor(c.highlightSnap), c.colorFor(Tool.HIGHLIGHTER))
                        }
                    }
                }
                ToolSlot(
                    tool = Tool.ERASER, selected = c.tool == Tool.ERASER && !c.lassoActive, open = settingsFor == "er",
                    onTap = { if (c.tool == Tool.ERASER && !c.lassoActive) settingsFor = "er" else c.selectToolWithHint(Tool.ERASER) },
                    onDismiss = { settingsFor = null },
                    onCustomColor = {}
                )
                ToolSlot(
                    tool = Tool.LASER, selected = c.tool == Tool.LASER && !c.lassoActive, open = settingsFor == "ls",
                    onTap = { if (c.tool == Tool.LASER && !c.lassoActive) settingsFor = "ls" else c.selectToolWithHint(Tool.LASER) },
                    onDismiss = { settingsFor = null },
                    onCustomColor = { settingsFor = null; showPicker = true }
                )
                // التحديد الحر (لاسو)
                if (v("lasso")) {
                    TBtn(selected = c.lassoActive, onClick = {
                        c.lassoActive = !c.lassoActive
                        if (c.lassoActive) c.messages.tryEmit(L("أحِط بالقلم حول الكتابة لتحديدها", "Draw a loop around your writing to select it"))
                    }) { PhIcon(R.drawable.ph_lasso, tintFor(c.lassoActive)) }
                }

                // صورة / نص / إضافة صفحة / قراءة النص / بحث / حفظ
                if (listOf("image", "text", "sticky", "zoomwin", "addpage", "ocr", "search", "export").any(v)) ToolbarSep()
                if (v("image")) {
                    TBtn(onClick = { imagePicker.launch(arrayOf("image/*")) }) {
                        ImageGlyph(Palette.TextSecondary)
                    }
                }
                if (v("text")) TBtn(onClick = { s.textDialog = TextDialogReq(null, cur) }) { TextGlyph(Palette.TextSecondary) }
                if (v("sticky")) {
                    TBtn(onClick = { s.textDialog = TextDialogReq(null, cur, sticky = true) }) {
                        PhIcon(R.drawable.ph_note, Palette.TextSecondary)
                    }
                }
                if (v("link")) {
                    TBtn(onClick = { linkDialog = true }) { PhIcon(R.drawable.ph_link, Palette.TextSecondary) }
                }
                if (v("split")) {
                    val splitOn = c.splitId != null
                    TBtn(selected = splitOn, onClick = { if (splitOn) c.closeSplit() else splitDialog = true }) {
                        PhIcon(R.drawable.ph_split, tintFor(splitOn))
                    }
                }
                if (v("record")) {
                    val recHere = AudioNotes.recordingDoc == s.id
                    TBtn(selected = recHere, onClick = { audioDialog = true }) {
                        if (recHere) {
                            Text(mmss(AudioNotes.recElapsed), color = Palette.Danger, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else PhIcon(R.drawable.ph_mic, Palette.TextSecondary)
                    }
                }
                if (v("zoomwin")) {
                    TBtn(selected = s.zoomBox != null, onClick = {
                        s.zoomBox = if (s.zoomBox != null) null else ZoomBox(cur, if (I18n.english) 0.04f else 0.56f, 0.12f, 0.4f)
                    }) { PhIcon(R.drawable.ph_zoom_write, tintFor(s.zoomBox != null)) }
                }
                if (v("addpage")) TBtn(onClick = { addDialog = true }) { AddPageGlyph(Palette.TextSecondary) }
                if (v("ocr")) {
                    TBtn(selected = s.ocrRunning, onClick = {
                        if (s.ocrRunning) s.stopOcr() else s.startOcr()
                    }) { OcrGlyph(tintFor(s.ocrRunning)) }
                }
                if (v("search")) {
                    TBtn(selected = s.searchOpen, onClick = {
                        if (s.searchOpen) s.closeSearch() else s.searchOpen = true
                    }) { SearchGlyph(tintFor(s.searchOpen)) }
                }
                if (v("export")) {
                    Box {
                        TBtn(enabled = c.exporting == null, onClick = { exportMenu = true }) {
                            ExportGlyph(Palette.TextSecondary)
                        }
                        DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(L("حفظ المستند مع التعليقات (PDF)", "Save document with annotations (PDF)")) },
                                onClick = {
                                    exportMenu = false
                                    onExportDoc()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(L("حفظ الملخصات (PDF)", "Save summaries (PDF)")) },
                                onClick = {
                                    exportMenu = false
                                    onExportSummary()
                                }
                            )
                        }
                    }
                }

                // تراجع وإعادة
                if (v("undo")) {
                    ToolbarSep()
                    val canUndo = s.undoStack.isNotEmpty()
                    val canRedo = s.redoStack.isNotEmpty()
                    TBtn(enabled = canUndo, onClick = { s.undo() }) {
                        UndoGlyph(if (canUndo) Palette.TextPrimary else dim)
                    }
                    TBtn(enabled = canRedo, onClick = { s.redo() }) {
                        UndoGlyph(if (canRedo) Palette.TextPrimary else dim, redo = true)
                    }
                }

                ToolbarSep()

                // التكبير
                if (v("zoom")) {
                    val canOut = s.scale > MIN_SCALE + 0.01f
                    val canIn = s.scale < 3.99f
                    TBtn(enabled = canOut, onClick = { s.zoomHook?.invoke(1f / 1.25f) }) {
                        ZoomGlyph(if (canOut) Palette.TextPrimary else dim, plus = false)
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .clickable { s.zoomHook?.invoke(1f / s.scale) }
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text("${(s.scale * 100).roundToInt()}%", fontSize = 14.sp, color = Palette.TextPrimary)
                    }
                    TBtn(enabled = canIn, onClick = { s.zoomHook?.invoke(1.25f) }) {
                        ZoomGlyph(if (canIn) Palette.TextPrimary else dim, plus = true)
                    }
                }
                // ملاءمة الصفحة كاملة (مفيد في الوضع الأفقي)، ونقرة ثانية تعيد ملء العرض
                if (v("fit")) TBtn(onClick = { s.fitHook?.invoke() }) { FitGlyph(Palette.TextPrimary) }
                // القراءة الليلية
                if (v("night")) {
                    TBtn(selected = c.nightPages, onClick = { c.toggleNightPages() }) {
                        PhIcon(R.drawable.ph_moon, tintFor(c.nightPages))
                    }
                }
                // وضع التركيز: إخفاء كل الأشرطة
                if (v("focus")) {
                    TBtn(onClick = {
                        s.drawerOpen = false
                        s.summaryOpen = false
                        c.focusMode = true
                        c.messages.tryEmit(L("وضع التركيز: اضغط زر الرجوع أو أيقونة الخروج للعودة", "Focus mode: press Back or the exit icon to return"))
                    }) { PhIcon(R.drawable.ph_eye, Palette.TextPrimary) }
                }
                // الإعدادات: ظاهرة دائمًا
                TBtn(onClick = { c.settingsOpen = true }) { PhIcon(R.drawable.ph_sliders, Palette.TextPrimary) }
                // خيارات الصفحة (درج على اليمين)
                TBtn(selected = s.pageMenuOpen, onClick = {
                    s.pageMenuOpen = !s.pageMenuOpen
                    if (s.pageMenuOpen) s.summaryOpen = false
                }) { PhIcon(R.drawable.ph_more, tintFor(s.pageMenuOpen)) }
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
    if (linkDialog) LinkDialog(s, cur) { linkDialog = false }
    if (audioDialog) AudioDialog(s) { audioDialog = false }
    if (splitDialog) SplitPickerDialog(s) { splitDialog = false }
    if (addDialog) {
        TemplatePickerDialog(
            mode = PickerMode.ADD_PAGE,
            onDismiss = { addDialog = false },
            onPdf = { atEnd ->
                addDialog = false
                pdfAfter = if (atEnd) count - 1 else cur
                pdfPicker.launch(arrayOf("application/pdf"))
            },
            onConfirm = { choice, _, atEnd ->
                addDialog = false
                val after = if (atEnd) count - 1 else cur
                c.addBlankPage(s, after, choice.kind, choice.color, choice.ratio())
            }
        )
    }
}

/**
 * شريط الأقلام: كل الأقلام والهايلايتر، الألوان، الحجم، وخيارات الأداة السريعة.
 * يظهر تحت شريط الأدوات عند الضغط على زر القلم.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PenBar() {
    val c = AppController
    val t = c.tool
    val pill = RoundedCornerShape(26.dp)
    var picker by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(start = 64.dp, end = 64.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.shadow(6.dp, pill, ambientColor = Palette.Ink, spotColor = Palette.Ink)
                .background(Color.White, pill).border(1.dp, Palette.Divider, pill).clip(pill)
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(Tool.BALLPOINT, Tool.FOUNTAIN, Tool.PENCIL, Tool.HIGHLIGHTER).forEach { pt ->
                    val sel = pt == t
                    TBtn(selected = sel, onClick = { c.selectTool(pt) }) {
                        ToolGlyph(pt, tintFor(sel), c.colorFor(pt), filled = sel)
                    }
                }
                ToolbarSep()
                // أقلامي المفضلة: نقرة تطبّقه، ضغط مطوّل يحذفه، والنجمة تحفظ القلم الحالي
                c.presets.toList().forEach { p ->
                    val col = Color(p.color)
                    val active = c.tool == p.tool && c.colorFor(p.tool) == col && c.sizeFor(p.tool) == p.size
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (active) Palette.NavySoft else Color.Transparent)
                            .combinedClickable(onClick = { c.applyPreset(p) }, onLongClick = { c.removePreset(p) }),
                        contentAlignment = Alignment.Center
                    ) {
                        PhIcon(toolIcon(p.tool, true), col, 20.dp)
                        Box(
                            Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
                                .width((6 + p.size * 2).dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(col)
                        )
                    }
                }
                TBtn(onClick = { c.addPreset() }) {
                    PhIcon(R.drawable.ph_star_plus, Palette.GoldDeep, 20.dp, L("حفظ كقلم مفضل", "Save as favorite pen"))
                }
                ToolbarSep()
                val current = c.colorFor(t)
                (c.paletteFor(t) + c.customColors.toList()).forEach { col ->
                    val sel = col == current
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).clickable { c.setColor(col) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.size(if (sel) 26.dp else 22.dp).clip(CircleShape).background(col)
                                .border(if (sel) 2.5.dp else 1.dp, if (sel) Palette.Navy else Palette.Divider, CircleShape)
                        )
                    }
                }
                Box(
                    Modifier.size(34.dp).clip(CircleShape).clickable { picker = true },
                    contentAlignment = Alignment.Center
                ) { PhIcon(R.drawable.ph_plus, Palette.TextSecondary, 18.dp, L("لون مخصص", "Custom color")) }
                ToolbarSep()
                SizeBar(c.sizeFor(t), { c.setSize(it) }, Modifier.width(120.dp).padding(vertical = 4.dp))
                if (t == Tool.HIGHLIGHTER) {
                    ToolbarSep()
                    BarToggle(L("التقاط النص", "Text snap"), c.highlightSnap) { c.toggleHighlightSnap() }
                    Spacer(Modifier.width(6.dp))
                    BarToggle(L("في الملخص", "In summary"), c.highlightToSummary) { c.toggleHighlightToSummary() }
                }
                if (t.isPen()) {
                    ToolbarSep()
                    BarToggle(L("أشكال", "Shapes"), c.shapeSnap) { c.toggleShapeSnap() }
                }
                ToolbarSep()
                Box {
                    TBtn(onClick = { more = true }) { PhIcon(R.drawable.ph_more, Palette.TextSecondary) }
                    DropdownMenu(
                        expanded = more,
                        onDismissRequest = { more = false },
                        shape = RoundedCornerShape(18.dp),
                        containerColor = Color.White,
                        shadowElevation = 12.dp
                    ) {
                        ToolSettingsPanel(onCustomColor = { more = false; picker = true })
                    }
                }
                TBtn(onClick = { c.penBarOpen = false }) {
                    PhIcon(R.drawable.ph_caret_up, Palette.TextSecondary, 20.dp, L("إخفاء شريط الأقلام", "Hide pen bar"))
                }
            }
        }
    }
    if (picker) {
        ColorPickerDialog(
            initial = c.colorFor(c.tool),
            onPick = { col ->
                c.addCustomColor(col)
                c.setColor(col)
                picker = false
            },
            onDismiss = { picker = false }
        )
    }
}

/** زر تبديل صغير داخل شريط الأقلام. */
@Composable
private fun BarToggle(label: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.clip(shape).background(if (on) Palette.Navy else Palette.Background)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (on) {
            PhIcon(R.drawable.ph_check, Color.White, 14.dp)
            Spacer(Modifier.width(4.dp))
        }
        Text(label, fontSize = 12.sp, color = if (on) Color.White else Palette.TextPrimary, maxLines = 1)
    }
}

private fun toolName(t: Tool): String = when (t) {
    Tool.BALLPOINT -> L("قلم جاف", "Ballpoint")
    Tool.FOUNTAIN -> L("قلم حبر", "Fountain pen")
    Tool.PENCIL -> L("قلم رصاص", "Pencil")
    Tool.HIGHLIGHTER -> L("هايلايتر", "Highlighter")
    Tool.ERASER -> L("ممحاة", "Eraser")
    Tool.LASER -> L("مؤشر ليزر", "Laser pointer")
}

/** زر أداة في الشريط، ولوحة إعداداتها تنسدل تحته. */
@Composable
private fun ToolSlot(
    tool: Tool,
    selected: Boolean,
    open: Boolean,
    onTap: () -> Unit,
    onDismiss: () -> Unit,
    onCustomColor: () -> Unit
) {
    val c = AppController
    Box {
        TBtn(selected = selected, onClick = onTap) {
            ToolGlyph(
                tool, tintFor(selected),
                if (tool == Tool.ERASER) tintFor(selected) else c.colorFor(tool),
                filled = selected
            )
        }
        DropdownMenu(
            expanded = open && selected,
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(18.dp),
            containerColor = Color.White,
            shadowElevation = 12.dp
        ) {
            ToolSettingsPanel(onCustomColor)
        }
    }
}

/** إعدادات الأداة الحالية: النوع (للأقلام)، اللون، الحجم مع معاينة، وخيارات خاصة. */
@Composable
private fun ToolSettingsPanel(onCustomColor: () -> Unit) {
    val c = AppController
    val t = c.tool
    Column(Modifier.width(312.dp).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PhIcon(toolIcon(t, true), Palette.Navy, 22.dp)
            Spacer(Modifier.width(10.dp))
            Text(toolName(t), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
        }
        Spacer(Modifier.height(12.dp))

        if (t.isPen()) {
            PanelLabel(L("نوع القلم", "Pen type"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Tool.BALLPOINT, Tool.FOUNTAIN, Tool.PENCIL).forEach { pt ->
                    val sel = pt == t
                    val shape = RoundedCornerShape(12.dp)
                    Column(
                        Modifier.weight(1f).clip(shape)
                            .background(if (sel) Palette.Navy else Palette.Background)
                            .clickable { c.selectTool(pt) }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PhIcon(toolIcon(pt, sel), if (sel) Color.White else Palette.TextPrimary, 22.dp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            toolName(pt), fontSize = 12.sp,
                            color = if (sel) Color.White else Palette.TextPrimary
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        if (t != Tool.ERASER) {
            ColorMenuContent(onCustom = onCustomColor, onDone = {})
            Spacer(Modifier.height(10.dp))
        }

        PanelLabel(if (t == Tool.ERASER) L("حجم الممحاة", "Eraser size") else L("الحجم", "Size"))
        SizeBar(c.sizeFor(t), { c.setSize(it) }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        ToolPreview(t)

        if (t == Tool.HIGHLIGHTER) {
            // تصنيف اللون الحالي (يظهر في الملخص عند التصفية أو التجميع حسب اللون)
            Spacer(Modifier.height(10.dp))
            val cur = c.colorFor(t)
            var label by remember(cur) { mutableStateOf(c.labelOf(cur)) }
            OutlinedTextField(
                value = label,
                onValueChange = {
                    label = it
                    c.setLabel(cur, it)
                },
                singleLine = true,
                label = { Text(L("تصنيف هذا اللون", "Label for this color")) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Divider))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(L("التقاط النص", "Text snap"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
                    Text(
                        L("يلتصق التمييز بالكلمات ويوضع خلفها، ويُضاف النص إلى الملخصات", "The highlight sticks to words, sits behind them, and the text is added to summaries"),
                        fontSize = 12.sp, lineHeight = 18.sp, color = Palette.TextSecondary
                    )
                }
                Spacer(Modifier.width(10.dp))
                Switch(
                    checked = c.highlightSnap,
                    onCheckedChange = { c.toggleHighlightSnap() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Palette.Navy,
                        checkedBorderColor = Palette.Navy
                    )
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(L("إضافة إلى الملخص", "Add to summary"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
                    Text(
                        L("أوقفه لتظلّل على الصفحة فقط دون أن يظهر النص في الملخصات", "Turn off to highlight on the page only, without adding text to summaries"),
                        fontSize = 12.sp, lineHeight = 18.sp, color = Palette.TextSecondary
                    )
                }
                Spacer(Modifier.width(10.dp))
                Switch(
                    checked = c.highlightToSummary,
                    onCheckedChange = { c.toggleHighlightToSummary() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Palette.Navy,
                        checkedBorderColor = Palette.Navy
                    )
                )
            }
        }
        if (t.isPen()) {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Divider))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(L("تحويل الرسم إلى أشكال", "Turn drawings into shapes"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
                    Text(
                        L("الخط والسهم والمستطيل والمثلث والدائرة تُرسم مرتبة تلقائيًا", "Lines, arrows, rectangles, triangles and circles are tidied automatically"),
                        fontSize = 12.sp, lineHeight = 18.sp, color = Palette.TextSecondary
                    )
                }
                Spacer(Modifier.width(10.dp))
                Switch(
                    checked = c.shapeSnap,
                    onCheckedChange = { c.toggleShapeSnap() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Palette.Navy,
                        checkedBorderColor = Palette.Navy
                    )
                )
            }
        }
        if (t == Tool.LASER) {
            Spacer(Modifier.height(12.dp))
            PanelLabel(L("نمط الليزر", "Laser style"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    LaserMode.DOT to L("نقطة", "Dot"),
                    LaserMode.LINE to L("خط مستمر", "Trail")
                ).forEach { (m, label) ->
                    val sel = c.laserMode == m
                    val shape = RoundedCornerShape(12.dp)
                    Column(
                        Modifier.weight(1f).clip(shape)
                            .background(if (sel) Palette.Navy else Palette.Background)
                            .clickable { c.updateLaserMode(m) }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LaserModeSample(m, if (sel) Color.White else c.colorFor(Tool.LASER))
                        Spacer(Modifier.height(4.dp))
                        Text(label, fontSize = 12.sp, color = if (sel) Color.White else Palette.TextPrimary)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (c.laserMode == LaserMode.DOT) L("نقطة متوهجة تتبع القلم، مع أثر قصير يختفي فورًا.", "A glowing dot follows the pen with a short trail that fades instantly.")
                else L("اضغط بالقلم وارسم: يبقى الخط متوهجًا ثانية ونصف ثم يختفي. بدون لمس تظهر النقطة فقط.", "Press and draw: the line glows for a second and a half, then fades. Without touching, only the dot shows."),
                fontSize = 12.sp, lineHeight = 18.sp, color = Palette.TextSecondary
            )
        }
        if (t == Tool.ERASER) {
            Spacer(Modifier.height(12.dp))
            PanelLabel(L("نمط الممحاة", "Eraser mode"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    EraserMode.STROKE to L("الخط كاملًا", "Whole stroke"),
                    EraserMode.AREA to L("الجزء الملموس", "Touched part")
                ).forEach { (m, label) ->
                    val sel = c.eraserMode == m
                    val shape = RoundedCornerShape(12.dp)
                    Column(
                        Modifier.weight(1f).clip(shape)
                            .background(if (sel) Palette.Navy else Palette.Background)
                            .clickable { c.updateEraserMode(m) }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        EraserModeSample(m, if (sel) Color.White else Palette.Navy)
                        Spacer(Modifier.height(4.dp))
                        Text(label, fontSize = 12.sp, color = if (sel) Color.White else Palette.TextPrimary)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (c.eraserMode == EraserMode.STROKE) L("لمسة واحدة تمسح الخط الذي كُتب دون رفع القلم كاملًا.", "One touch erases the entire stroke drawn without lifting the pen.")
                else L("يمسح ما تمر عليه الممحاة فقط، ويبقى باقي الخط.", "Erases only what the eraser passes over; the rest of the stroke stays."),
                fontSize = 12.sp, lineHeight = 18.sp, color = Palette.TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                L("تمسح الكتابة اليدوية والتمييز الملتقط. زر القلم الجانبي يعمل كممحاة أيضًا.", "Erases handwriting and snapped highlights. The pen’s side button also works as an eraser."),
                fontSize = 12.sp, lineHeight = 18.sp, color = Palette.TextSecondary
            )
        }
    }
}

/** رسم صغير يوضح نمط الممحاة: خط ممسوح كله، أو مقطوع في الوسط. */
@Composable
private fun EraserModeSample(m: EraserMode, col: Color) {
    Canvas(Modifier.width(64.dp).height(22.dp)) {
        val y = size.height / 2f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(4f, y + 5f)
            cubicTo(size.width * 0.3f, y - 10f, size.width * 0.6f, y + 12f, size.width - 4f, y - 4f)
        }
        val cx = size.width / 2f
        if (m == EraserMode.STROKE) {
            drawPath(path, col.copy(alpha = 0.25f), style = Stroke(width = 3f, cap = StrokeCap.Round,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 5f))))
        } else {
            clipRect(right = cx - 9f) { drawPath(path, col, style = Stroke(width = 3f, cap = StrokeCap.Round)) }
            clipRect(left = cx + 9f) { drawPath(path, col, style = Stroke(width = 3f, cap = StrokeCap.Round)) }
        }
        drawCircle(col, 8f, Offset(cx, y), style = Stroke(1.5f))
    }
}

@Composable
private fun LaserModeSample(m: LaserMode, col: Color) {
    Canvas(Modifier.width(56.dp).height(22.dp)) {
        val y = size.height / 2f
        if (m == LaserMode.DOT) {
            drawCircle(col.copy(alpha = 0.25f), size.height * 0.5f, Offset(size.width / 2f, y))
            drawCircle(col, size.height * 0.22f, Offset(size.width / 2f, y))
        } else {
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(4f, y + 4f)
                cubicTo(size.width * 0.3f, y - 10f, size.width * 0.6f, y + 12f, size.width - 4f, y - 4f)
            }
            drawPath(p, col.copy(alpha = 0.25f), style = Stroke(width = 9f, cap = StrokeCap.Round))
            drawPath(p, col, style = Stroke(width = 3.5f, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun PanelLabel(text: String) {
    Text(text, fontSize = 13.sp, color = Palette.TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
}

/** معاينة الخط بالحجم واللون الحاليين. */
@Composable
private fun ToolPreview(t: Tool) {
    val c = AppController
    val color = c.colorFor(t)
    val width = Sizes.width(t, c.sizeFor(t))
    Canvas(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(10.dp)).background(Palette.Ivory)
    ) {
        val w = size.width
        if (t == Tool.ERASER) {
            drawCircle(Palette.TextSecondary, width * w, Offset(w / 2f, size.height / 2f), style = Stroke(1.5.dp.toPx()))
            return@Canvas
        }
        val cy = size.height / 2f / w
        val amp = size.height * 0.22f / w
        val pts = (0..48).map { i ->
            val f = i / 48f
            Pt(0.08f + 0.84f * f, cy + amp * sin(f * 2f * PI.toFloat()), 0.3f + 0.5f * f)
        }
        drawInk(pts, color, width, if (t == Tool.LASER) Tool.BALLPOINT else t)
    }
}

@Composable
internal fun PageJumpDialog(count: Int, current: Int, onGo: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf((current + 1).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("الانتقال إلى صفحة", "Go to page")) },
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
            }) { Text(L("انتقال", "Go")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("إلغاء", "Cancel")) } }
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
                    Text(L("ابحث في نص المستند", "Search the document text"), color = Palette.TextSecondary, fontSize = 15.sp, maxLines = 1)
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
                    s.hits.isEmpty() -> L("لا نتائج", "No results")
                    else -> "${s.hitIndex + 1}/${s.hits.size}"
                },
                fontSize = 13.sp, color = Palette.TextSecondary
            )
            TBtn(onClick = { s.stepHit(-1) }) { ChevronGlyph(Palette.TextPrimary, up = true) }
            TBtn(onClick = { s.stepHit(1) }) { ChevronGlyph(Palette.TextPrimary, up = false) }
            IconButton(onClick = { s.closeSearch() }, modifier = Modifier.size(40.dp)) {
                PhIcon(R.drawable.ph_close, Palette.TextSecondary, 20.dp, L("إغلاق البحث", "Close search"))
            }
        }
        if (!s.ocrRunning && s.ocr.size < total) {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    L("البحث يشمل ${s.ocr.size} من $total صفحة تمت قراءتها", "Search covers ${s.ocr.size} of $total pages read so far"),
                    fontSize = 12.sp, color = Palette.TextSecondary, modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { s.startOcr() }) { Text(L("قراءة الكل", "Read all"), fontSize = 13.sp) }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Divider))
    }
}

// ---------------------------------------------------------------- التكبير والتحريك

/**
 * قرصة بإصبعين للتكبير حول نقطة الأصابع، وسحب أفقي بإصبع واحد عند التكبير.
 * لا يتدخل في القلم، ولا في التمرير الرأسي بإصبع واحد.
 */
/**
 * إن بدأت اللمسة داخل التحديد الحر (أو على مقبضه) يحرّكه أو يكبّره حتى رفع القلم/الإصبع،
 * ويعيد true. وإلا يعيد false دون أن يستهلك شيئًا.
 */
private suspend fun AwaitPointerEventScope.dragLasso(
    session: DocSession,
    index: Int,
    down: PointerInputChange,
    w: Float
): Boolean {
    if (session.lassoSel?.page != index) return false
    val bb = session.lassoBounds() ?: return false
    val nx = down.position.x / w
    val ny = down.position.y / w
    val onHandle = hypot(nx - bb[2], ny - bb[3]) < 0.035f
    val inside = nx >= bb[0] && nx <= bb[2] && ny >= bb[1] && ny <= bb[3]
    if (!onHandle && !inside) return false
    down.consume()
    session.beginEdit()
    var last = down.position
    try {
        while (true) {
            val e = awaitPointerEvent()
            val ch = e.changes.firstOrNull { it.id == down.id } ?: break
            if (!ch.pressed) break
            if (onHandle) {
                // تكبير حول الزاوية العليا: نسبة بُعد القلم الجديد إلى القديم عن الزاوية
                val px = bb[0] * w
                val py = bb[1] * w
                val d0 = hypot(last.x - px, last.y - py)
                val d1 = hypot(ch.position.x - px, ch.position.y - py)
                if (d0 > 4f) {
                    val k = (d1 / d0).coerceIn(0.8f, 1.25f)
                    val cur = session.lassoBounds()
                    val size = if (cur != null) (cur[2] - cur[0]) else 1f
                    if (!(k < 1f && size < 0.03f)) session.lassoTransform(0f, 0f, k, bb[0], bb[1])
                }
            } else {
                session.lassoTransform((ch.position.x - last.x) / w, (ch.position.y - last.y) / w)
            }
            last = ch.position
            ch.consume()
        }
    } finally {
        session.endEdit()
    }
    return true
}

/**
 * نقرة سريعة بإصبعين = تراجع، بثلاثة أصابع = إعادة. يراقب اللمس فقط دون أن يستهلكه،
 * ولا يُحسب إن تحركت الأصابع (قرصة أو تمرير) أو طال الضغط.
 */
private fun Modifier.multiFingerTap(onTwo: () -> Unit, onThree: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (first.type != PointerType.Touch) return@awaitEachGesture
        val start = first.uptimeMillis
        val origin = HashMap<PointerId, Offset>()
        origin[first.id] = first.position
        var maxFingers = 1
        var valid = true
        var end = start
        val slop = viewConfiguration.touchSlop * 1.5f
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            for (ch in ev.changes) {
                if (ch.type != PointerType.Touch) valid = false
                if (ch.pressed && ch.id !in origin) origin[ch.id] = ch.position
                val o = origin[ch.id]
                if (o != null && ch.pressed && (ch.position - o).getDistance() > slop) valid = false
                end = maxOf(end, ch.uptimeMillis)
            }
            maxFingers = maxOf(maxFingers, ev.changes.count { it.pressed })
            if (end - start > 450L) valid = false
            if (ev.changes.none { it.pressed }) break
        }
        if (valid && end - start <= 450L) {
            when (maxFingers) {
                2 -> onTwo()
                3 -> onThree()
            }
        }
    }
}

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

        // تجهيز الصفحات المجاورة مسبقًا: نسخ معاينة سريعة حولك دائمًا، ونسخ واضحة للصفحتين التاليتين عند التوقف
        LaunchedEffect(pdf, vw) {
            snapshotFlow {
                Triple(s.listState.firstVisibleItemIndex, s.listState.isScrollInProgress, s.scale)
            }.collectLatest { (first, scrolling, sc) ->
                if (scrolling) s.lastScrollAt = System.currentTimeMillis()
                val visible = s.listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                val n = pdf.count
                withContext(Dispatchers.IO) {
                    val ctx = coroutineContext
                    val around = (first - 3..first + visible + 6).filter { it in 0 until n }
                    for (k in around) {
                        if (!ctx.isActive) return@withContext
                        try {
                            pdf.render(pdf.pages.getOrNull(k), PREVIEW_W) { ctx.isActive }
                        } catch (t: Throwable) {
                        }
                    }
                }
                if (scrolling) return@collectLatest
                delay(250)
                val w = renderWidthFor((vw * sc - 2f * pad).roundToInt())
                withContext(Dispatchers.IO) {
                    val ctx = coroutineContext
                    for (k in listOf(first + visible, first + visible + 1)) {
                        if (k !in 0 until n) continue
                        if (!ctx.isActive) return@withContext
                        try {
                            pdf.render(pdf.pages.getOrNull(k), w) { ctx.isActive }
                        } catch (t: Throwable) {
                        }
                    }
                }
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

        // على الشاشات العريضة يُزاح المستند بجانب درج الصفحات بدل أن يغطيه
        val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 720
        val sidePad by androidx.compose.animation.core.animateDpAsState(
            if (s.drawerOpen && wide) PAGES_PANEL_W.dp else 0.dp, tween(240), label = "sidePad"
        )
        Box(
            Modifier.fillMaxSize().padding(start = sidePad).multiFingerTap(
                onTwo = { if (AppController.fingerUndo && s.undoStack.isNotEmpty()) s.undo() },
                onThree = { if (AppController.fingerUndo && s.redoStack.isNotEmpty()) s.redo() }
            ).zoomAndPan(
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
                items(pdf.count, key = { it }, contentType = { "page" }) { i ->
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

        // الدرجان ينزلقان من حافتيهما عند الفتح
        AnimatedVisibility(
            visible = s.drawerOpen,
            modifier = Modifier.align(Alignment.CenterStart),
            enter = slideInHorizontally(tween(260)) { -it } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(220)) { -it } + fadeOut(tween(180))
        ) {
            PagesDrawer(s, pdf)
        }
        AnimatedVisibility(
            visible = s.summaryOpen,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(tween(260)) { it } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(180))
        ) {
            SummaryDrawer(
                s = s,
                onJump = { m -> s.jumpHook?.invoke(m) },
                onExport = onExportSummary
            )
        }
        // درج خيارات الصفحة على اليمين
        AnimatedVisibility(
            visible = s.pageMenuOpen,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(tween(260)) { it } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(180))
        ) {
            PagePanel(s, pdf)
        }

        // شريط التمرير بالصفحات على الحافة
        if (!s.summaryOpen) PageScrubber(s, pdf, Modifier.align(Alignment.TopEnd).padding(vertical = 10.dp))

        // عداد وقت القراءة لهذا المستند
        if (!s.drawerOpen && AppController.toolVisible("readtimer")) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(14.dp)
                    .shadow(3.dp, RoundedCornerShape(14.dp))
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReaderTimer(s.id)
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (s.selection != null) SelectionBar(s)
            if (s.lassoSel != null) LassoBar(s)
            if (s.selectedImageId != null) ImageBar(s)
            if (s.selectedTextId != null) TextBoxBar(s)
        }
    }
}

@Composable
private fun SelectionBar(s: DocSession) {
    val context = LocalContext.current
    val sel = s.selection ?: return
    var cardBack by remember { mutableStateOf<String?>(null) }
    var translateText by remember { mutableStateOf<String?>(null) }
    translateText?.let { t -> TranslateDialog(t) { translateText = null } }
    cardBack?.let { back ->
        AddCardDialog(
            initialFront = "",
            initialBack = back,
            onSave = { f, b ->
                s.addCustomCard(f, b)
                cardBack = null
                s.selection = null
            },
            onDismiss = { cardBack = null }
        )
    }
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 8.dp) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                val text = s.selectionText()
                if (text.isNotEmpty()) {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("MyNotes", text))
                    AppController.messages.tryEmit(L("تم نسخ النص", "Text copied"))
                }
            }) { Text(L("نسخ", "Copy")) }
            TextButton(onClick = { s.markSelection(AppController.colorFor(Tool.HIGHLIGHTER)) }) {
                Text(L("إلى الملخصات", "To summaries"), color = Palette.GoldDeep)
            }
            TextButton(onClick = {
                val text = s.selectionText()
                if (text.isNotEmpty()) cardBack = TextTidy.clean(text)
            }) { Text(L("اجعله بطاقة", "Make it a card")) }
            TextButton(onClick = {
                val text = s.selectionText()
                if (text.isNotEmpty()) translateText = TextTidy.clean(text)
            }) { Text(L("ترجمة", "Translate")) }
            TextButton(onClick = {
                val n = s.ocr[sel.page]?.words?.size ?: 0
                if (n > 0) s.selection = TextSelection(sel.page, 0, n - 1)
            }) { Text(L("تحديد كل الصفحة", "Select whole page")) }
            TextButton(onClick = { s.selection = null }) { Text(L("إلغاء", "Cancel")) }
        }
    }
}

/** أوامر التحديد الحر: لون، نسخ، حذف، تم. التحريك بالسحب والتكبير بالمقبض. */
@Composable
private fun LassoBar(s: DocSession) {
    val c = AppController
    var toText by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 8.dp) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            c.penPalette.take(6).forEach { col ->
                Box(
                    Modifier.size(32.dp).clip(CircleShape).clickable { s.lassoRecolor(col) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(col).border(1.dp, Palette.Divider, CircleShape))
                }
            }
            TextButton(onClick = { s.lassoDuplicate() }) { Text(L("نسخ", "Duplicate")) }
            TextButton(onClick = { toText = true }) { Text(L("إلى نص", "To text")) }
            // تشغيل التسجيل الصوتي من لحظة كتابة أول خط محدد
            val sel = s.lassoSel
            val firstT = sel?.strokes?.filter { it.t > 0 }?.minOfOrNull { it.t } ?: 0L
            val rec = if (firstT > 0) AudioNotes.recAt(s.id, firstT) else null
            if (rec != null) {
                TextButton(onClick = { AudioNotes.play(rec, firstT - rec.start - 2000L) }) {
                    Text(L("▶ الصوت", "▶ Audio"), color = Palette.Navy, fontWeight = FontWeight.Bold)
                }
            }
            TextButton(onClick = { s.lassoDelete() }) { Text(L("حذف", "Delete"), color = Color(0xFFC62828)) }
            TextButton(onClick = { s.lassoSel = null }) { Text(L("تم", "Done")) }
        }
    }
    if (toText) InkToTextDialog(s) { toText = false }
}

@Composable
private fun ImageBar(s: DocSession) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 8.dp) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { s.deleteSelectedImage() }) {
                Text(L("حذف الصورة", "Delete image"), color = Palette.Danger)
            }
            TextButton(onClick = {
                val id = s.selectedImageId
                if (id != null) {
                    s.beginEdit()
                    s.updateImage(id) { it.copy(w = (it.w * 0.87f).coerceAtLeast(0.05f)) }
                    s.endEdit()
                }
            }) { Text(L("تصغير", "Smaller")) }
            TextButton(onClick = {
                val id = s.selectedImageId
                if (id != null) {
                    s.beginEdit()
                    s.updateImage(id) { it.copy(w = (it.w * 1.15f).coerceAtMost(1.5f)) }
                    s.endEdit()
                }
            }) { Text(L("تكبير", "Larger")) }
            TextButton(onClick = { s.selectedImageId = null }) { Text(L("تم", "Done")) }
        }
    }
}

/** معزول في دالة مستقلة كي لا تُعاد رسم الصفحات مع كل تحديث للعداد. */
@Composable
private fun ReaderTimer(id: String) {
    ReadingTimeBadge(AppController.readMsOf(id), Palette.Navy, 12)
}

@Composable
private fun TextBoxBar(s: DocSession) {
    val id = s.selectedTextId ?: return
    val tb = s.textBox(id)
    if (tb == null) {
        SideEffect { s.selectedTextId = null }
        return
    }
    if (tb.linkDoc != null) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 8.dp) {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { s.selectedTextId = null; AppController.followLink(s, tb.linkDoc, tb.linkPage) }) {
                    Text(L("فتح الرابط", "Open link"), fontWeight = FontWeight.Bold, color = Palette.Navy)
                }
                TextButton(onClick = { s.deleteTextBox(id) }) { Text(L("حذف", "Delete"), color = Palette.Danger) }
                TextButton(onClick = { s.selectedTextId = null }) { Text(L("تم", "Done")) }
            }
        }
        return
    }
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 8.dp) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { s.textDialog = TextDialogReq(id, tb.page) }) { Text(L("تعديل", "Edit")) }
            TextButton(onClick = {
                s.beginEdit()
                s.updateTextBox(id) { it.copy(size = (it.size / 1.15f).coerceAtLeast(0.010f)) }
                s.endEdit()
            }) { Text(L("خط أصغر", "Smaller text")) }
            TextButton(onClick = {
                s.beginEdit()
                s.updateTextBox(id) { it.copy(size = (it.size * 1.15f).coerceAtMost(0.09f)) }
                s.endEdit()
            }) { Text(L("خط أكبر", "Larger text")) }
            TextButton(onClick = {
                val pen = AppController.tool.let { if (it.isPen()) it else AppController.lastPen }
                s.beginEdit()
                s.updateTextBox(id) { it.copy(color = AppController.colorFor(pen)) }
                s.endEdit()
            }) { Text(L("لون القلم", "Pen color")) }
            TextButton(onClick = { s.deleteTextBox(id) }) { Text(L("حذف", "Delete"), color = Palette.Danger) }
            TextButton(onClick = { s.selectedTextId = null }) { Text(L("تم", "Done")) }
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

/** ink = نقطة من «الخط المستمر» (القلم ملامس للشاشة في نمط الخط). */
private class LaserPt(val x: Float, val y: Float, var t: Long, val seg: Int, val ink: Boolean = false)

private class LaserState {
    var lastInkAt: Long = 0L
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
    val renderW = renderWidthFor(wPx)

    // أولًا نسخة صغيرة سريعة (فلا تظهر صفحة فارغة عند التمرير السريع)، ثم النسخة الواضحة
    val lastRef = remember { arrayOfNulls<PageRef>(1) }
    val bmp by produceState<Bitmap?>(
        pdf.cached(ref, renderW) ?: pdf.cached(ref, PREVIEW_W), pdf, ref, renderW
    ) {
        if (lastRef[0] != ref) {
            value = pdf.cached(ref, renderW) ?: pdf.cached(ref, PREVIEW_W)
            lastRef[0] = ref
        }
        if (renderW > 0) {
            if (value == null) {
                val low = withContext(Dispatchers.IO) {
                    val ctx = coroutineContext
                    try {
                        pdf.render(ref, PREVIEW_W) { ctx.isActive }
                    } catch (t: Throwable) {
                        null
                    }
                }
                if (low != null && value == null) value = low
            }
            if (pdf.cached(ref, renderW) == null) {
                // صفحة تعبر الشاشة بسرعة لا تستحق رسمًا كاملًا
                delay(90)
            }
            val b = withContext(Dispatchers.IO) {
                val ctx = coroutineContext
                try {
                    pdf.render(ref, renderW) { ctx.isActive }
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
    val laserModeNow by rememberUpdatedState(AppController.laserMode)
    val imagesNow by rememberUpdatedState(pageImages)

    val night = AppController.nightPages
    val lassoNow by rememberUpdatedState(AppController.lassoActive)
    /** رسم حلقة التحديد الحر جارٍ (يُرسم الخط الحي متقطعًا). */
    val lassoDrawing = remember { booleanArrayOf(false) }
    val live = remember { LiveInk() }
    /** موضع حدود الممحاة (بالبكسل) ونصف قطرها النسبي؛ null = مخفية. */
    val eraserCursor = remember { arrayOfNulls<Offset>(1) }
    val eraserR = remember { floatArrayOf(0f) }
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
                // نقاط «الخط المستمر» تبقى 1.5 ثانية بعد آخر رسم ثم تتلاشى؛ أثر النقطة يختفي سريعًا
                laser.removeAll { p ->
                    if (p.ink) now - lz.lastInkAt > LASER_LINE_HOLD + LASER_LINE_FADE else now - p.t > 260L
                }
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
            .background(if (night) nightOf(paperColor) else paperColor)
    ) {
        val b = bmp
        if (b != null) {
            val img = remember(b) { b.asImageBitmap() }
            Image(
                bitmap = img,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                colorFilter = if (night) ColorFilter.colorMatrix(ColorMatrix(NightMatrix)) else null
            )
        }

        // التمييز الملتقط: يُرسم بمزج «ضرب» فوق صورة الصفحة فيبدو خلف النص والنص واضح فوقه
        if (pageMarks.isNotEmpty()) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                for (m in pageMarks) {
                    val col = Exporter.markColor(m.color, strong = m.level >= 2)
                    for (r in m.rects) drawMarkRect(col, r, w, night)
                    if (m.level < 2) {
                        val strong = Exporter.markColor(m.color, strong = true)
                        for (r in m.strongRects) drawMarkRect(strong, r, w, night)
                    }
                    // خط ذهبي تحت النص المهم (المظلل مرتين)
                    val important = if (m.level >= 2) m.rects else m.strongRects
                    for (r in important) {
                        drawRect(
                            Palette.GoldDeep, Offset(r.l * w, r.b * w - 2.dp.toPx()),
                            Size((r.r - r.l) * w, 2.dp.toPx())
                        )
                    }
                }
            }
        }

        // شريط علامة ذهبي على الصفحة المعلَّمة
        if (index in session.bookmarks) {
            PhIcon(
                R.drawable.ph_bookmark_fill, Palette.Gold, 30.dp,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 18.dp)
            )
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
                    if (tb.sticky) {
                        // ملاحظة لاصقة مطوية: أيقونة صغيرة تُفتح بالنقر
                        val side = with(density) { (tb.w * wp).toDp() }
                        Box(
                            Modifier.align(AbsoluteAlignment.TopLeft)
                                .absoluteOffset { IntOffset((tb.x * wp).roundToInt(), (tb.y * wp).roundToInt()) }
                                .size(side)
                                .onSizeChanged { textHeights[tb.id] = it.height / wp }
                                .shadow(3.dp, RoundedCornerShape(6.dp))
                                .background(Color(0xFFFFE27A), RoundedCornerShape(6.dp))
                                .then(if (selected) Modifier.border(1.5.dp, Palette.Navy, RoundedCornerShape(6.dp)) else Modifier),
                            contentAlignment = Alignment.Center
                        ) { PhIcon(R.drawable.ph_note, Color(0xFF6B4E00), side * 0.6f) }
                    } else if (tb.linkDoc != null) {
                        // رابط إلى صفحة: شارة بعنوانه (نقرة تحدده، ونقرة ثانية تفتحه)
                        Row(
                            Modifier.align(AbsoluteAlignment.TopLeft)
                                .absoluteOffset { IntOffset((tb.x * wp).roundToInt(), (tb.y * wp).roundToInt()) }
                                .widthIn(max = with(density) { (tb.w * wp).toDp() })
                                .onSizeChanged { textHeights[tb.id] = it.height / wp }
                                .clip(RoundedCornerShape(14.dp))
                                .background(Palette.NavySoft)
                                .border(if (selected) 1.5.dp else 1.dp, if (selected) Palette.Navy else Palette.Gold, RoundedCornerShape(14.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PhIcon(R.drawable.ph_link, Palette.Navy, 16.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(tb.text, fontSize = 13.sp, color = Palette.Navy, fontWeight = FontWeight.Medium, maxLines = 1)
                        }
                    } else {
                    Text(
                        tb.text,
                        color = if (night) nightOf(tb.color) else tb.color,
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

        // الخطوط المثبّتة: صورة Bitmap للصفحة تُعرض بأمر واحد (InkLayer.kt)
        InkLayer(strokes, night, Modifier.fillMaxSize())

        // الخط الحي + الليزر + استقبال القلم والإصبع (طبقة مستقلة: تحديثها لا يعيد رسم الصفحة)
        Canvas(
            Modifier.fillMaxSize()
                .graphicsLayer()
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
                                        val ink = laserModeNow == LaserMode.LINE && lc.pressed
                                        // قفزة أو توقف طويل أو تغيّر النوع = بداية مقطع جديد
                                        if (prev == null || prev.ink != ink || now - lz.lastEvt > 160L ||
                                            (!ink && hypot(nx - prev.x, ny - prev.y) > 0.12f)
                                        ) {
                                            lz.seg++
                                        }
                                        laser.add(LaserPt(nx, ny, now, lz.seg, ink))
                                        if (ink) lz.lastInkAt = now
                                        if (laser.size > 3000) laser.removeAt(0)
                                        if (!ink && laser.count { !it.ink } > 120) {
                                            val k = laser.indexOfFirst { !it.ink }
                                            if (k >= 0) laser.removeAt(k)
                                        }
                                        lz.lastEvt = now
                                        lz.present = true
                                        laserOn = true
                                        tick++
                                        if (lc.pressed) lc.consume()
                                    }
                                }
                                continue
                            }

                            // حدود الممحاة: تظهر والقلم فوق الشاشة (حيث يدعمه الجهاز) وأثناء المسح
                            val hv = ev.changes.firstOrNull {
                                it.type == PointerType.Stylus || it.type == PointerType.Eraser || it.type == PointerType.Mouse
                            }
                            if (hv != null && !hv.pressed) {
                                val showCur = toolNow == Tool.ERASER || hv.type == PointerType.Eraser
                                val nextCur = if (showCur && ev.type != PointerEventType.Exit) hv.position else null
                                if (nextCur != eraserCursor[0]) {
                                    eraserCursor[0] = nextCur
                                    tick++
                                }
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

                            // التحديد الحر: تحريك/تكبير التحديد، أو رسم حلقة تحديد جديدة
                            if (lassoNow && down.type != PointerType.Eraser) {
                                down.consume()
                                if (dragLasso(session, index, down, w)) continue
                                session.lassoSel = null
                                livePts.clear()
                                livePts.add(Pt(down.position.x / w, down.position.y / w, 0.5f))
                                lassoDrawing[0] = true
                                tick++
                                try {
                                    while (true) {
                                        val e = awaitPointerEvent()
                                        val ch = e.changes.firstOrNull { it.id == id } ?: break
                                        if (!ch.pressed) break
                                        livePts.add(Pt(ch.position.x / w, ch.position.y / w, 0.5f))
                                        ch.consume()
                                        tick++
                                    }
                                } finally {
                                    val poly = livePts.toList()
                                    livePts.clear()
                                    lassoDrawing[0] = false
                                    tick++
                                    session.lassoSelect(index, poly)
                                }
                                continue
                            }

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
                                eraserCursor[0] = down.position
                                eraserR[0] = eraseR
                                tick++
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
                                        eraserCursor[0] = ch.position
                                        tick++
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
                                // بعد رفع القلم تختفي الحدود (وتعود مع التحويم إن دعمه الجهاز)
                                if (erasing) {
                                    eraserCursor[0] = null
                                    tick++
                                }
                                var snapPts: List<Pt>? = null
                                if (!erasing && livePts.isNotEmpty()) {
                                    if (live.tool == Tool.HIGHLIGHTER && AppController.highlightSnap) {
                                        snapPts = livePts.toList()
                                    } else {
                                        // الأقلام: رسم يشبه شكلًا واضحًا يتحول إلى شكل مرتب
                                        val raw = livePts.toList()
                                        val pts = if (live.tool.isPen() && AppController.shapeSnap) {
                                            ShapeRecognizer.recognize(raw) ?: raw
                                        } else raw
                                        session.addInk(index, Ink(pts, live.color, live.width, live.tool, System.currentTimeMillis()))
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

                        // تحريك التحديد الحر بالإصبع أيضًا
                        if (session.lassoSel?.page == index && dragLasso(session, index, down, w)) return@awaitEachGesture

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
                                                it.copy(
                                                    x = (it.x + dx).coerceIn(-it.w * 0.8f, 0.95f),
                                                    y = (it.y + dy).coerceIn(0f, 1f / ratio - 0.02f)
                                                )
                                            }
                                        }
                                        ch.consume()
                                    }
                                } finally {
                                    session.endEdit()
                                }
                                // نقرة على المربع المحدد (بلا سحب) تفتح التعديل
                                if (!moved && inside) {
                                    val ld = selText.linkDoc
                                    if (ld != null) AppController.followLink(session, ld, selText.linkPage)
                                    else session.textDialog = TextDialogReq(selText.id, index)
                                }
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
                                // نافذة الكتابة المكبّرة مفتوحة: النقر ينقل منطقتها إلى هنا
                                val zb = session.zoomBox
                                if (zb != null) {
                                    session.zoomBox = zb.copy(
                                        page = index,
                                        x = (nx - zb.w / 2f).coerceIn(0f, (1f - zb.w).coerceAtLeast(0f)),
                                        y = (ny - zb.h / 2f).coerceIn(0f, (1f / ratio - zb.h).coerceAtLeast(0f))
                                    )
                                    return@awaitEachGesture
                                }
                                if (session.lassoSel != null) {
                                    session.lassoSel = null
                                    return@awaitEachGesture
                                }
                                val textHit = textsNow.lastOrNull {
                                    val th = textHeights[it.id] ?: 0.03f
                                    nx >= it.x && nx <= it.x + it.w && ny >= it.y && ny <= it.y + th
                                }
                                if (textHit != null) {
                                    session.selectedTextId = textHit.id
                                    session.selectedImageId = null
                                    session.selection = null
                                    // الملاحظة اللاصقة تُفتح مباشرة
                                    if (textHit.sticky) session.textDialog = TextDialogReq(textHit.id, index, sticky = true)
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
                                    // ضغط مطوّل على مكان ليس فيه نص: لا شيء (دون رسالة)
                                    Unit
                                }
                            } else {
                                // الصفحة لم تُقرأ بعد: اقرأها الآن ثم حدّد الكلمة
                                scope.launch {
                                    session.pageBusy = index
                                    val page = session.ocrPage(index)
                                    session.pageBusy = null
                                    val list = page?.words
                                    if (list == null || list.isEmpty()) {
                                        // صفحة بلا نص مقروء: لا شيء (دون رسالة)
                                        Unit
                                    } else {
                                        val i = nearestWord(list, nx, ny, 0.06f)
                                        if (i >= 0) {
                                            session.selectedImageId = null
                                            session.selection = TextSelection(index, i, i)
                                        } else {
                                            // ضغط مطوّل على مكان ليس فيه نص: لا شيء (دون رسالة)
                                    Unit
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
            if (lassoDrawing[0]) {
                // حلقة التحديد الحر: خط متقطع
                if (livePts.size > 1) {
                    val pth = androidx.compose.ui.graphics.Path()
                    livePts.forEachIndexed { i, p ->
                        if (i == 0) pth.moveTo(p.x * size.width, p.y * size.width) else pth.lineTo(p.x * size.width, p.y * size.width)
                    }
                    drawPath(
                        pth, Palette.Navy,
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                    )
                }
            } else {
                drawInk(livePts, if (night) nightOf(live.color) else live.color, live.width, live.tool, extra)
            }

            // التحديد الحر: إطار متقطع حول الخطوط المحددة ومقبض للتكبير
            if (session.lassoSel?.page == index) {
                session.lassoBounds()?.let { bb ->
                    val w = size.width
                    drawRect(
                        Palette.Navy, Offset(bb[0] * w, bb[1] * w), Size((bb[2] - bb[0]) * w, (bb[3] - bb[1]) * w),
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
                    )
                    drawCircle(Palette.Navy, 10.dp.toPx(), Offset(bb[2] * w, bb[3] * w))
                    drawCircle(Color.White, 4.dp.toPx(), Offset(bb[2] * w, bb[3] * w))
                }
            }
            // نافذة الكتابة المكبّرة: إطار المنطقة على الصفحة
            session.zoomBox?.takeIf { it.page == index }?.let { zb ->
                val w = size.width
                drawRect(Palette.Gold.copy(alpha = 0.10f), Offset(zb.x * w, zb.y * w), Size(zb.w * w, zb.h * w))
                drawRect(Palette.Gold, Offset(zb.x * w, zb.y * w), Size(zb.w * w, zb.h * w), style = Stroke(2.dp.toPx()))
            }

            // حدود الممحاة
            eraserCursor[0]?.let { pos ->
                val r = (if (toolNow == Tool.ERASER) eraserNow else eraserR[0]) * size.width
                drawCircle(Color.White.copy(alpha = 0.45f), r, pos)
                drawCircle(Color(0xFF4A5568), r, pos, style = Stroke(1.5.dp.toPx()))
            }

            // ليزر: نقطة حمراء متوهجة، مع أثر قصير جدًا داخل المقطع الواحد فقط
            if (laser.isNotEmpty()) {
                val w = size.width
                val now = System.currentTimeMillis()
                val r = laserSizeNow * w
                val col = laserColorNow
                // نمط «الخط المستمر»: خط متوهج يبقى قليلًا ثم يتلاشى
                val inkAge = now - lz.lastInkAt
                val inkAlpha = if (inkAge <= LASER_LINE_HOLD) 1f
                else (1f - (inkAge - LASER_LINE_HOLD) / LASER_LINE_FADE.toFloat()).coerceIn(0f, 1f)
                if (inkAlpha > 0f) {
                    // مسار واحد لكل خط حتى لا تظهر حبيبات عند تداخل المقاطع الشفافة
                    val paths = ArrayList<androidx.compose.ui.graphics.Path>()
                    var curSeg = -1
                    var path: androidx.compose.ui.graphics.Path? = null
                    for (p in laser) {
                        if (!p.ink) continue
                        if (p.seg != curSeg || path == null) {
                            path = androidx.compose.ui.graphics.Path().also { paths.add(it) }
                            path.moveTo(p.x * w, p.y * w)
                            curSeg = p.seg
                        } else {
                            path.lineTo(p.x * w, p.y * w)
                        }
                    }
                    for (pass in 0..2) {
                        val (wMul, aMul, pc) = when (pass) {
                            0 -> Triple(2.8f, 0.22f, col)
                            1 -> Triple(1.1f, 0.95f, col)
                            else -> Triple(0.35f, 0.85f, Color.White)
                        }
                        for (pth in paths) {
                            drawPath(
                                pth, pc.copy(alpha = inkAlpha * aMul),
                                style = Stroke(width = r * wMul, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                            )
                        }
                    }
                }
                for (i in 1 until laser.size) {
                    val a = laser[i - 1]
                    val c = laser[i]
                    if (a.seg != c.seg || c.ink) continue
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
                Text(L("جارٍ قراءة الصفحة…", "Reading page…"), fontSize = 13.sp, color = Palette.TextPrimary)
            }
        }
    }
}


/** إنشاء رابط من الصفحة الحالية إلى صفحة في هذا المستند أو مستند آخر. */
@Composable
private fun LinkDialog(s: DocSession, cur: Int, onDismiss: () -> Unit) {
    val c = AppController
    val docs = remember { listOfNotNull(c.library.firstOrNull { it.id == s.id }) + c.library.filter { it.id != s.id }.sortedByDescending { it.lastOpened } }
    var target by remember { mutableStateOf(docs.firstOrNull()) }
    var pageText by remember { mutableStateOf("1") }
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("رابط إلى صفحة", "Link to a page")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(L("المستند", "Document"), fontSize = 13.sp, color = Palette.TextSecondary)
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState())
                        .clip(RoundedCornerShape(10.dp)).border(1.dp, Palette.Divider, RoundedCornerShape(10.dp))
                ) {
                    docs.forEach { d ->
                        val sel = target?.id == d.id
                        Text(
                            if (d.id == s.id) L("هذا المستند", "This document") + " — " + d.name else d.name,
                            fontSize = 14.sp, maxLines = 1,
                            color = if (sel) Color.White else Palette.TextPrimary,
                            modifier = Modifier.fillMaxWidth().background(if (sel) Palette.Navy else Color.Transparent)
                                .clickable { target = d }.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = pageText, onValueChange = { pageText = it.filter { ch -> ch.isDigit() }.take(4) },
                    label = { Text(L("رقم الصفحة (من ${target?.pageCount ?: 1})", "Page (of ${target?.pageCount ?: 1})")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text(L("عنوان الرابط (اختياري)", "Link title (optional)")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(enabled = target != null, onClick = {
                val t = target ?: return@TextButton
                val p = ((pageText.toIntOrNull() ?: 1) - 1).coerceIn(0, maxOf(0, t.pageCount - 1))
                val title = label.trim().ifEmpty {
                    (if (t.id == s.id) L("صفحة", "Page") else t.name + L(" — ص", " — p.")) + " ${p + 1}"
                }
                s.addTextBox(
                    TextBox(
                        id = java.util.UUID.randomUUID().toString(), page = cur, x = 0.06f, y = 0.06f, w = 0.42f,
                        text = title, color = Palette.Navy, size = 0.02f, linkDoc = t.id, linkPage = p
                    )
                )
                c.messages.tryEmit(L("أُضيف الرابط: اسحبه لمكانه، وانقر عليه مرتين لفتحه", "Link added: drag it into place; tap it twice to open"))
                onDismiss()
            }) { Text(L("إضافة", "Add")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("إلغاء", "Cancel")) } }
    )
}

/**
 * شريط تمرير بالصفحات على حافة الشاشة: اسحب المقبض فتظهر صورة الصفحة ورقمها،
 * وعند الإفلات تنتقل إليها مباشرة.
 */
@Composable
fun PageScrubber(s: DocSession, pdf: PdfDoc, modifier: Modifier) {
    val n = pdf.count
    if (n < 4) return
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var dragPage by remember { mutableStateOf(0) }
    val cur = s.listState.firstVisibleItemIndex
    BoxWithConstraints(modifier.fillMaxHeight().width(150.dp)) {
        val trackH = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        val handleH = with(density) { 54.dp.toPx() }
        val shown = if (dragging) dragPage else cur
        val frac = shown / (n - 1).toFloat()
        val y = (trackH - handleH) * frac
        // فقاعة المعاينة أثناء السحب
        if (dragging) {
            val ref = pdf.pages.getOrNull(dragPage)
            val bmp by produceState<Bitmap?>(pdf.cached(ref, PREVIEW_W), ref) {
                value = pdf.cached(ref, PREVIEW_W) ?: withContext(Dispatchers.IO) {
                    try {
                        pdf.render(ref, PREVIEW_W)
                    } catch (t: Throwable) {
                        null
                    }
                }
            }
            Column(
                Modifier.align(Alignment.TopEnd)
                    .offset { IntOffset(-with(density) { 34.dp.roundToPx() }, (y - handleH).roundToInt().coerceAtLeast(0)) }
                    .width(104.dp)
                    .shadow(8.dp, RoundedCornerShape(10.dp))
                    .background(Color.White, RoundedCornerShape(10.dp))
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val b = bmp
                if (b != null) {
                    val img = remember(b) { b.asImageBitmap() }
                    Image(img, null, Modifier.fillMaxWidth().aspectRatio(pdf.ratio(dragPage)), contentScale = ContentScale.FillBounds)
                } else {
                    Box(Modifier.fillMaxWidth().aspectRatio(pdf.ratio(dragPage)).background(Palette.Background))
                }
                Spacer(Modifier.height(4.dp))
                Text("${dragPage + 1} / $n", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.Navy)
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd)
                .offset { IntOffset(0, y.roundToInt()) }
                .width(22.dp).height(54.dp)
                .padding(end = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (dragging) Palette.Navy else Palette.Navy.copy(alpha = 0.35f))
                .pointerInput(n, trackH) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragging = true
                            dragPage = s.listState.firstVisibleItemIndex
                        },
                        onDragEnd = {
                            dragging = false
                            val p = dragPage
                            scope.launch { s.listState.scrollToItem(p) }
                        },
                        onDragCancel = { dragging = false }
                    ) { change, dy ->
                        change.consume()
                        val curY = (trackH - handleH) * (dragPage / (n - 1).toFloat()) + dy
                        dragPage = ((curY / (trackH - handleH).coerceAtLeast(1f)) * (n - 1)).roundToInt().coerceIn(0, n - 1)
                    }
                }
        )
    }
}
