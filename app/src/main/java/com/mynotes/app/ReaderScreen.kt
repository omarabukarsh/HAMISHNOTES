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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.input.pointer.PointerEvent
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
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
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
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
        if (s.summaryAiMode) {
            if (AiOrganizer.result(s.id) == null) c.messages.tryEmit(L("رتّب الملخص بالذكاء الاصطناعي أولًا", "Organize the summary with AI first"))
            else summarySaver.launch(L("ملخص ذكاء $safeTitle.pdf", "AI summary $safeTitle.pdf"))
        } else if (s.allMarks().isEmpty()) c.messages.tryEmit(L("لا توجد ملخصات لحفظها بعد", "No summaries to save yet"))
        else summarySaver.launch(L("ملخص $safeTitle.pdf", "Summary $safeTitle.pdf"))
    }

    // إيقاف أداة التحديد الحر يلغي التحديد
    LaunchedEffect(c.lassoActive) { if (!c.lassoActive) s.lassoSel = null }

    // MyNotes Pro: بعد تظليل جديد يُكمل الذكاء الاصطناعي الملخص المرتّب (وبطاقات المراجعة) تلقائيًا،
    // بعد توقف قصير عن الكتابة حتى لا يبطئ القلم
    // الكتابة تلقائية بالكامل (لا «أعد الترتيب»): أول ملخص يُكتب وحده، والتظليل الجديد يُضاف، وحذف تظليل
    // يعيد الترتيب (سريع: الأجزاء التي لم تتغير تؤخذ من الذاكرة).
    LaunchedEffect(s.marks) {
        AiOrganizer.refreshPending(s)
        if (!AiOrganizer.autoContinue || !Ai.ready || s.allMarks().isEmpty()) return@LaunchedEffect
        val mode = AiOrganizer.autoMode(s) ?: return@LaunchedEffect
        delay(20_000)
        while (System.currentTimeMillis() - c.lastInkAt < 5_000 || AiOrganizer.runningDoc != null || AiPageReader.runningKey != null) {
            delay(3_000)
        }
        if (mode == "full") AiOrganizer.startAuto(s) else AiOrganizer.continueSummary(s, quiet = true)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(Palette.Desk)) {
            if (!c.focusMode) {
              // حجم أشرطة الأدوات (من الإعدادات): كثافة مكبّرة/مصغّرة لهذه الأشرطة فقط
              val baseDensity = androidx.compose.ui.platform.LocalDensity.current
              androidx.compose.runtime.CompositionLocalProvider(
                  androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                      baseDensity.density * UiPrefs.toolbarScale, baseDensity.fontScale
                  )
              ) {
                ReaderTopBar(s, pdf.count, exportDoc, exportSummary)
                // شريط الأقلام الثاني (نمط «شريط الأقلام»)
                AnimatedVisibility(
                    visible = c.toolbarStyle == ToolbarStyle.PEN_BAR && c.penBarOpen && !c.lassoActive && !PenPrefs.shapeActive &&
                        (c.tool.isPen() || c.tool == Tool.HIGHLIGHTER),
                    enter = Motion.barEnter,
                    exit = Motion.barExit
                ) { PenBar() }
                // شريط الأشكال
                AnimatedVisibility(
                    visible = PenPrefs.shapeActive && !c.lassoActive,
                    enter = Motion.barEnter,
                    exit = Motion.barExit
                ) { ShapeBar() }
                // شريط الملاحظات اللاصقة
                AnimatedVisibility(
                    visible = PenPrefs.stickyActive,
                    enter = Motion.barEnter,
                    exit = Motion.barExit
                ) { StickyBar() }
              }
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

internal fun tintFor(selected: Boolean): Color = if (selected) Palette.Navy else Palette.TextSecondary

@Composable
internal fun TBtn(
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    // انتقال لوني ناعم عند الاختيار، وانضغاط خفيف عند اللمس
    val bg by androidx.compose.animation.animateColorAsState(
        if (selected) Palette.NavySoft else Color.Transparent, tween(Motion.ENTER), label = "tbtn"
    )
    Box(
        Modifier.size(42.dp).pressScale().clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
        // مؤشر ذهبي صغير أسفل الأداة المختارة
        androidx.compose.animation.AnimatedVisibility(
            visible = selected,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(Motion.ENTER)) + androidx.compose.animation.scaleIn(Motion.grow(), initialScale = 0.3f),
            exit = fadeOut(tween(Motion.EXIT))
        ) {
            Box(
                Modifier.padding(bottom = 3.dp)
                    .width(14.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
                    .background(Palette.Gold)
            )
        }
    }
}

@Composable
internal fun ToolbarSep() {
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

        // زر درج الملخصات في الزاوية المقابلة (بلا رقم، طلب المستخدم)
        if (UiPrefs.summariesOn) Box(Modifier.align(Alignment.CenterEnd)) {
            Box(
                Modifier.size(46.dp)
                    .shadow(5.dp, CircleShape)
                    .background(if (s.summaryOpen) Palette.GoldSoft else Color.White, CircleShape)
                    .clip(CircleShape)
                    .clickable { s.summaryOpen = !s.summaryOpen; if (s.summaryOpen) s.pageMenuOpen = false },
                contentAlignment = Alignment.Center
            ) { SummaryGlyph(tintFor(s.summaryOpen), Palette.Gold) }
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
                    val writing = (c.tool.isPen() || c.tool == Tool.HIGHLIGHTER) && !c.lassoActive && !PenPrefs.shapeActive
                    val shown = if (writing) c.tool else c.lastWriting
                    TBtn(selected = writing, onClick = {
                        if (!writing) {
                            c.selectTool(if (c.tool.isPen() || c.tool == Tool.HIGHLIGHTER) c.tool else c.lastWriting)
                            c.penBarOpen = true
                        } else {
                            c.penBarOpen = !c.penBarOpen
                        }
                    }) {
                        ToolGlyph(shown, tintFor(writing), c.colorFor(shown), filled = writing)
                    }
                } else {
                    // الأدوات: نقرة تختار الأداة، ونقرة ثانية على الأداة المختارة تفتح إعداداتها
                    val penSelected = c.tool.isPen() && !c.lassoActive && !PenPrefs.shapeActive
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
                    var lassoMenu by remember { mutableStateOf(false) }
                    Box {
                        // نقرة تفعّل التحديد، ونقرة ثانية تفتح إعداداته
                        TBtn(selected = c.lassoActive, onClick = {
                            if (c.lassoActive) {
                                lassoMenu = true
                            } else {
                                c.lassoActive = true
                                PenPrefs.shapeActive = false
                                c.messages.tryEmit(L("أحِط بالقلم حول ما تريد تحديده، ونقرة ثانية على الزر لإعداداته", "Draw around what you want to select; tap the button again for options"))
                            }
                        }) { PhIcon(if (PenPrefs.lassoRect) R.drawable.ph_select_rect else R.drawable.ph_lasso, tintFor(c.lassoActive)) }
                        DropdownMenu(
                            expanded = lassoMenu, onDismissRequest = { lassoMenu = false },
                            shape = RoundedCornerShape(18.dp), containerColor = Color.White, shadowElevation = 12.dp
                        ) { LassoMenuContent(onOff = { lassoMenu = false; c.lassoActive = false }) }
                    }
                }
                // الأشكال
                if (v("shapetool")) {
                    TBtn(selected = PenPrefs.shapeActive && !c.lassoActive, onClick = {
                        if (PenPrefs.shapeActive && !c.lassoActive) {
                            PenPrefs.shapeActive = false
                        } else {
                            c.lassoActive = false
                            if (!(c.tool.isPen() || c.tool == Tool.HIGHLIGHTER)) c.selectTool(c.lastPen)
                            PenPrefs.shapeActive = true
                        }
                    }) { PhIcon(R.drawable.ph_shapes, tintFor(PenPrefs.shapeActive && !c.lassoActive)) }
                }
                // التبديل بين الكتابة بالقلم فقط، والكتابة بالقلم والإصبع
                if (PenInput.showToggle) {
                    TBtn(selected = PenInput.fingerWrites, onClick = {
                        val on = !PenInput.fingerWrites
                        PenInput.chooseFingerWrites(on)
                        c.messages.tryEmit(
                            if (on) L("صار الإصبع يكتب · اسحب بإصبعين للتمرير", "Your finger now writes · drag with two fingers to scroll")
                            else L("القلم يكتب، والإصبع للتمرير", "The pen writes; your finger scrolls")
                        )
                    }) {
                        PhIcon(
                            if (PenInput.fingerWrites) R.drawable.ph_hand_pointing_fill else R.drawable.ph_hand_pointing,
                            if (PenInput.fingerWrites) Palette.GoldDeep else Palette.TextSecondary
                        )
                    }
                }

                // صورة / نص / إضافة صفحة / قراءة النص / بحث / حفظ
                if (listOf("image", "text", "sticky", "zoomwin", "addpage", "ocr", "search", "export", "ai").any(v)) ToolbarSep()
                if (v("image")) {
                    TBtn(onClick = { imagePicker.launch(arrayOf("image/*")) }) {
                        ImageGlyph(Palette.TextSecondary)
                    }
                }
                if (v("text")) TBtn(onClick = { s.textDialog = TextDialogReq(null, cur) }) { TextGlyph(Palette.TextSecondary) }
                if (v("sticky")) {
                    // أداة الملاحظات اللاصقة: شريط ألوان، ثم نقرة على الصفحة تضع الملاحظة
                    TBtn(selected = PenPrefs.stickyActive, onClick = {
                        if (PenPrefs.stickyActive) {
                            PenPrefs.stickyActive = false
                        } else {
                            c.lassoActive = false
                            PenPrefs.shapeActive = false
                            PenPrefs.stickyActive = true
                            c.messages.tryEmit(L("اختر لونًا ثم انقر على الصفحة لوضع الملاحظة", "Pick a color, then tap the page to place the note"))
                        }
                    }) { PhIcon(R.drawable.ph_note, tintFor(PenPrefs.stickyActive)) }
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
                // ✦ الذكاء الاصطناعي: يفتح الملخصات على تبويب «ذكاء»
                if (v("ai")) {
                    val aiOn = s.summaryOpen && s.summaryAiMode
                    TBtn(selected = aiOn, onClick = {
                        if (aiOn) s.summaryOpen = false
                        else { s.summaryAiMode = true; s.summaryOpen = true; s.pageMenuOpen = false }
                    }) { Text("✦", fontSize = 20.sp, color = if (aiOn) Palette.GoldDeep else Palette.Gold, fontWeight = FontWeight.Bold) }
                }
                if (v("search")) {
                    TBtn(selected = s.searchOpen, onClick = {
                        if (s.searchOpen) s.closeSearch() else s.searchOpen = true
                    }) { SearchGlyph(tintFor(s.searchOpen)) }
                }
                if (v("export")) {
                    // مشاركة مباشرة أو حفظ: نسخة قابلة للتعديل أو نهائية، والملخصات
                    TBtn(enabled = c.exporting == null, onClick = { exportMenu = true }) {
                        ExportGlyph(Palette.TextSecondary)
                    }
                    if (exportMenu) ShareExportDialog(s.id) { exportMenu = false }
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
                // زر الإعدادات أُزيل من الشريط: الإعدادات من درج «⋯» ← «كل الإعدادات وتخصيص الأدوات»
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
                // قلم واحد (حبر/جاف/فرشاة يُبدَّل بينها من إعداداته)، ثم الرصاص والهايلايتر.
                // نقرة ثانية على الأداة المختارة تفتح إعداداتها تحتها مباشرة.
                val inkPen = if (t in InkPens) t else PenPrefs.inkPen
                listOf(inkPen, Tool.PENCIL, Tool.HIGHLIGHTER).forEach { pt ->
                    val sel = pt == t
                    Box {
                        TBtn(selected = sel, onClick = { if (sel) more = true else c.selectTool(pt) }) {
                            ToolGlyph(pt, tintFor(sel), c.colorFor(pt), filled = sel)
                        }
                        DropdownMenu(
                            expanded = more && sel,
                            onDismissRequest = { more = false },
                            shape = RoundedCornerShape(18.dp),
                            containerColor = Color.White,
                            shadowElevation = 12.dp
                        ) {
                            ToolSettingsPanel(onCustomColor = { more = false; picker = true })
                        }
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
                SizeSlots(t)
                ToolbarSep()
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

/** أنواع القلم التي تُبدَّل من تبويب واحد (الرصاص منفصل). */
val InkPens = listOf(Tool.FOUNTAIN, Tool.BALLPOINT, Tool.BRUSH)

/** اسم قصير لتبويب نوع القلم. */
private fun penShortName(t: Tool): String = when (t) {
    Tool.FOUNTAIN -> L("حبر", "Fountain")
    Tool.BALLPOINT -> L("جاف", "Ballpoint")
    Tool.BRUSH -> L("فرشاة", "Brush")
    else -> toolName(t)
}

private fun toolName(t: Tool): String = when (t) {
    Tool.BALLPOINT -> L("قلم جاف", "Ballpoint")
    Tool.FOUNTAIN -> L("قلم حبر", "Fountain pen")
    Tool.PENCIL -> L("قلم رصاص", "Pencil")
    Tool.HIGHLIGHTER -> L("هايلايتر", "Highlighter")
    Tool.ERASER -> L("ممحاة", "Eraser")
    Tool.LASER -> L("مؤشر ليزر", "Laser pointer")
    Tool.BRUSH -> L("قلم فرشاة", "Brush pen")
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
    Column(Modifier.width(264.dp).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PhIcon(toolIcon(t, true), Palette.Navy, 18.dp)
            Spacer(Modifier.width(8.dp))
            Text(toolName(t), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
        }
        Spacer(Modifier.height(8.dp))

        // شريط الأقلام: الرصاص زر مستقل؛ الشريط الكلاسيكي: الرصاص ضمن الأنواع
        val penTypes = if (c.toolbarStyle == ToolbarStyle.PEN_BAR) InkPens else InkPens + Tool.PENCIL
        if (t in penTypes) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                penTypes.forEach { pt ->
                    val sel = pt == t
                    val shape = RoundedCornerShape(10.dp)
                    Column(
                        Modifier.weight(1f).clip(shape)
                            .background(if (sel) Palette.Navy else Palette.Background)
                            .clickable { c.selectTool(pt) }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PhIcon(toolIcon(pt, sel), if (sel) Color.White else Palette.TextPrimary, 18.dp)
                        Text(penShortName(pt), fontSize = 11.sp, maxLines = 1, softWrap = false, color = if (sel) Color.White else Palette.TextPrimary)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (t != Tool.ERASER) {
            ColorMenuContent(onCustom = onCustomColor, onDone = {})
            Spacer(Modifier.height(6.dp))
        }

        if (!t.isPen()) {
            PanelLabel(if (t == Tool.ERASER) L("حجم الممحاة", "Eraser size") else L("الحجم", "Size"))
            SizeBar(c.sizeFor(t), { c.setSize(it) }, Modifier.fillMaxWidth().height(28.dp))
        }

        if (t == Tool.HIGHLIGHTER) {
            Spacer(Modifier.height(6.dp))
            val cur = c.colorFor(t)
            var label by remember(cur) { mutableStateOf(c.labelOf(cur)) }
            OutlinedTextField(
                value = label,
                onValueChange = {
                    label = it
                    c.setLabel(cur, it)
                },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                label = { Text(L("تصنيف هذا اللون", "Label for this color"), fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth()
            )
            CompactSwitch(L("التقاط النص", "Text snap"), c.highlightSnap) { c.toggleHighlightSnap() }
            if (UiPrefs.summariesOn) {
                CompactSwitch(L("إضافة إلى الملخص", "Add to summary"), c.highlightToSummary) { c.toggleHighlightToSummary() }
            }
            HighlighterAdvancedSection()
        }
        if (t.isPen()) {
            CompactSwitch(L("ارسم وأبقِ لتحويل الرسم إلى شكل", "Draw and hold to make a shape"), PenPrefs.drawAndHold) {
                PenPrefs.changeDrawAndHold(it)
            }
            PenAdvancedSection(t)
        }
        if (t == Tool.LASER) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    LaserMode.DOT to L("نقطة", "Dot"),
                    LaserMode.LINE to L("خط مستمر", "Trail")
                ).forEach { (m, label) ->
                    val sel = c.laserMode == m
                    val shape = RoundedCornerShape(10.dp)
                    Column(
                        Modifier.weight(1f).clip(shape)
                            .background(if (sel) Palette.Navy else Palette.Background)
                            .clickable { c.updateLaserMode(m) }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LaserModeSample(m, if (sel) Color.White else c.colorFor(Tool.LASER))
                        Text(label, fontSize = 11.sp, color = if (sel) Color.White else Palette.TextPrimary)
                    }
                }
            }
        }
        if (t == Tool.ERASER) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    EraserMode.STROKE to L("الخط كاملًا", "Whole stroke"),
                    EraserMode.AREA to L("الجزء الملموس", "Touched part")
                ).forEach { (m, label) ->
                    val sel = c.eraserMode == m
                    val shape = RoundedCornerShape(10.dp)
                    Column(
                        Modifier.weight(1f).clip(shape)
                            .background(if (sel) Palette.Navy else Palette.Background)
                            .clickable { c.updateEraserMode(m) }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        EraserModeSample(m, if (sel) Color.White else Palette.Navy)
                        Text(label, fontSize = 11.sp, color = if (sel) Color.White else Palette.TextPrimary)
                    }
                }
            }
            EraserAdvancedSection()
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
internal fun PanelLabel(text: String) {
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
    onPinchEnd: () -> Unit,
    onPanX: (Float) -> Unit
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var prevDist = 0f
        var prevCentroid = Offset.Zero
        var mode = 0 // 0 غير محدد، 1 سحب أفقي، 2 تمرير رأسي، 3 قرصة
        var total = Offset.Zero
        var pinching = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val stylusDown = event.changes.any { it.pressed && it.type != PointerType.Touch }
            val touches = event.changes.filter { it.pressed && it.type == PointerType.Touch }
            if (!stylusDown && touches.size >= 2) {
                mode = 3
                pinching = true
                val c = (touches[0].position + touches[1].position) / 2f
                val d = (touches[0].position - touches[1].position).getDistance()
                if (prevDist > 0f && d > 0f) onPinch(d / prevDist, prevCentroid, c)
                prevDist = d
                prevCentroid = c
                touches.forEach { it.consume() }
            } else {
                prevDist = 0f
                // انتهت القرصة (رُفع أحد الإصبعين): يُطبَّق التكبير الحقيقي مرة واحدة
                if (pinching) {
                    pinching = false
                    onPinchEnd()
                }
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
        if (pinching) onPinchEnd()
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

        /**
         * القرصة الجارية تُعرض بطبقة رسم فقط (بلا إعادة ترتيب الصفحات أو رسمها):
         * موضع الشاشة = موضع المحتوى × pinchK + (pinchTx, pinchTy). عند انتهائها تُطبَّق مرة واحدة.
         */
        var pinchK by remember { mutableStateOf(1f) }
        var pinchTx by remember { mutableStateOf(0f) }
        var pinchTy by remember { mutableStateOf(0f) }
        fun pinch(rIn: Float, prev: Offset, now: Offset) {
            val k1 = (pinchK * rIn).coerceIn(MIN_SCALE / s.scale, 4f / s.scale)
            val r = k1 / pinchK
            pinchTx = r * (pinchTx - prev.x) + now.x
            pinchTy = r * (pinchTy - prev.y) + now.y
            pinchK = k1
        }
        fun endPinch() {
            val k = pinchK
            // تكبير، أو سحب بإصبعين بلا تكبير (التمرير في وضع «القلم والإصبع»)
            if (abs(k - 1f) > 0.001f || abs(pinchTx) > 1f || abs(pinchTy) > 1f) {
                // نقطة المحتوى الظاهرة في وسط الشاشة تبقى في وسطها بعد التطبيق
                val c = Offset(vw / 2f, vh / 2f)
                val src = Offset((c.x - pinchTx) / k, (c.y - pinchTy) / k)
                applyZoom(k, src, c)
            }
            pinchK = 1f
            pinchTx = 0f
            pinchTy = 0f
        }
        val zoomFn = rememberUpdatedState<(Float, Offset, Offset) -> Unit>(
            { r, a, b -> pinch(r, a, b) }
        )
        val pinchEndFn = rememberUpdatedState({ endPinch() })
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
                onPinchEnd = { pinchEndFn.value() },
                onPanX = { dx -> panFn.value(dx) }
            )
        ) {
            LazyColumn(
                state = s.listState,
                modifier = Modifier.graphicsLayer {
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    scaleX = pinchK
                    scaleY = pinchK
                    translationX = pinchTx
                    translationY = pinchTy
                }.layout { measurable, constraints ->
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
            enter = Motion.drawerEnter(fromStart = true),
            exit = Motion.drawerExit(toStart = true)
        ) {
            PagesDrawer(s, pdf)
        }
        AnimatedVisibility(
            visible = s.summaryOpen && UiPrefs.summariesOn,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = Motion.drawerEnter(fromStart = false),
            exit = Motion.drawerExit(toStart = false)
        ) {
            SummaryDrawer(
                s = s,
                onJump = { m -> s.jumpHook?.invoke(m) },
                onExport = onExportSummary
            )
        }
        // MyNotes Pro: درج خيارات الصفحة على اليمين
        AnimatedVisibility(
            visible = s.pageMenuOpen,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = Motion.drawerEnter(fromStart = false),
            exit = Motion.drawerExit(toStart = false)
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
            if (s.selectedNoteId != null) NoteBar(s)
        }
    }
}

/** أعلى موضع في التحديد (نسبة إلى عرض الصفحة) لمعرفة العنوان الذي يقع تحته. */
private fun selectionTop(s: DocSession, sel: TextSelection): Float {
    val ws = s.ocr[sel.page]?.words ?: return 0.5f
    if (ws.isEmpty()) return 0.5f
    val lo = minOf(sel.start, sel.end).coerceIn(0, ws.size - 1)
    return ws[lo].t
}

@Composable
private fun SelectionBar(s: DocSession) {
    val context = LocalContext.current
    val sel = s.selection ?: return
    var translateText by remember { mutableStateOf<String?>(null) }
    translateText?.let { t -> TranslateDialog(t) { translateText = null } }
    var explainText by remember { mutableStateOf<String?>(null) }
    var explainCtx by remember { mutableStateOf("") }
    explainText?.let { t -> AiExplainDialog(t, explainCtx) { explainText = null } }
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
            // بطاقة بنقرة واحدة: الجواب هو النص المحدد، والسؤال عنوانه في المستند (أو أول كلماته)
            TextButton(onClick = {
                val text = s.selectionText()
                if (text.isNotEmpty()) {
                    val back = TextTidy.clean(text)
                    val (title, sub) = AiStructure.headingFor(s, sel.page, selectionTop(s, sel))
                    val front = sub ?: title ?: (back.split(Regex("\\s+")).take(8).joinToString(" ") + "…")
                    s.addCustomCard(front, back)
                    s.selection = null
                    AppController.messages.tryEmit(L("أُضيفت بطاقة", "Card added"))
                }
            }) { Text(L("اجعله بطاقة", "Make it a card")) }
            TextButton(onClick = {
                val text = s.selectionText()
                if (text.isNotEmpty()) {
                    s.addMindNote(sel.page, TextTidy.clean(text), selectionTop(s, sel))
                    s.selection = null
                }
            }) { Text(L("إلى الخريطة الذهنية", "To mind map")) }
            TextButton(onClick = {
                val text = s.selectionText()
                if (text.isNotEmpty()) translateText = TextTidy.clean(text)
            }) { Text(L("ترجمة", "Translate")) }
            TextButton(onClick = {
                val text = s.selectionText()
                if (text.isNotEmpty()) {
                    explainCtx = AiDocReader.context(s, sel.page, 1800)
                    explainText = ArabicFix.text(TextTidy.clean(text))
                }
            }) { Text(L("✦ اشرح", "✦ Explain"), color = Palette.GoldDeep) }
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
    var style: Int = LineStyle.SOLID
    var sharp: Boolean = false
}

/** ink = نقطة من «الخط المستمر» (القلم ملامس للشاشة في نمط الخط). */
private class LaserPt(val x: Float, val y: Float, var t: Long, val seg: Int, val ink: Boolean = false)

private class LaserState {
    var lastInkAt: Long = 0L
    var seg: Int = 0
    var lastEvt: Long = 0L
    var present: Boolean = false
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
    /** الخط الحي مرسومًا تراكميًا (LiveInk.kt)، ورقم الخط الحالي. */
    val liveRaster = remember { LiveInkRaster() }
    val liveGen = remember { intArrayOf(0) }
    DisposableEffect(liveRaster) { onDispose { liveRaster.release() } }
    /** خطوط رُفع القلم عنها ولم تظهر بعد في صورة الخطوط: تبقى مرسومة هنا حتى تظهر هناك. */
    val pendingInk = remember { ArrayList<Ink>() }
    val strokesNow by rememberUpdatedState(strokes)
    /** الجزء الظاهر من الصفحة على الشاشة بإحداثياتها (بالبكسل). */
    val visRect = remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
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
            .onGloballyPositioned { co ->
                val b = co.boundsInWindow()
                visRect.value = if (b.isEmpty) androidx.compose.ui.geometry.Rect.Zero
                else androidx.compose.ui.geometry.Rect(co.windowToLocal(b.topLeft), co.windowToLocal(b.bottomRight))
            }
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
        // التكبير الكبير: الجزء الظاهر بدقة الشاشة فوق صورة الصفحة
        PdfSharpPatch(pdf, ref, wPx, ratio, visRect, night)

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
        InkLayer("${session.id}:$index", strokes, night, visRect, Modifier.fillMaxSize()) { drawnLists ->
            if (pendingInk.isNotEmpty()) {
                val cur = strokesNow
                val before = pendingInk.size
                pendingInk.removeAll { p -> drawnLists.all { l -> l.any { it === p } } || cur.none { it === p } }
                if (pendingInk.size != before) tick++
            }
        }
        // الملاحظات اللاصقة فوق الكتابة
        NotesLayer(session, index, night)

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
                    // ---------- القلم (والإصبع في وضع «القلم والإصبع») ----------
                    awaitPointerEventScope {
                        /** حدث نزل فيه القلم أثناء لمسة إصبع ألغيناها (راحة اليد): يُعالَج كبداية جديدة. */
                        var carry: PointerEvent? = null
                        /** الإصبع يكتب هنا: الوضع مفعّل، ولا نافذة كتابة مكبّرة، والقلم ليس قريبًا (راحة اليد). */
                        fun fingerInk(e: PointerEvent): Boolean =
                            PenInput.fingerWrites && session.zoomBox == null && !PenInput.stylusActiveRecently() &&
                                e.changes.count { it.pressed && it.type == PointerType.Touch } == 1
                        while (true) {
                            val ev = carry ?: awaitPointerEvent()
                            carry = null

                            // التحديد والأشكال والملاحظات تتقدم على الليزر (الأداة تبقى ليزرًا عند تفعيلها)
                            if (toolNow == Tool.LASER && !lassoNow && !PenPrefs.shapeActive && !PenPrefs.stickyActive) {
                                val lc = ev.changes.firstOrNull {
                                    it.type == PointerType.Stylus || it.type == PointerType.Mouse ||
                                        (it.type == PointerType.Touch && it.pressed && fingerInk(ev))
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
                                        it.type == PointerType.Mouse ||
                                        (it.type == PointerType.Touch && fingerInk(ev))
                                    )
                            } ?: continue
                            val byFinger = down.type == PointerType.Touch
                            /** إصبع ثانٍ (تمرير/تكبير) أو قلم نزل أثناء لمسة الإصبع. */
                            fun fingerInterrupted(e: PointerEvent): Boolean = byFinger && (
                                e.changes.count { it.pressed && it.type == PointerType.Touch } >= 2 ||
                                    e.changes.any { it.pressed && (it.type == PointerType.Stylus || it.type == PointerType.Eraser) }
                                )
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
                                        val np = Pt(ch.position.x / w, ch.position.y / w, 0.5f)
                                        // التحديد المستطيل: نقطة البداية والنقطة الحالية فقط
                                        if (PenPrefs.lassoRect) {
                                            if (livePts.size > 1) livePts[1] = np else livePts.add(np)
                                        } else livePts.add(np)
                                        ch.consume()
                                        tick++
                                    }
                                } finally {
                                    val poly = if (PenPrefs.lassoRect && livePts.size >= 2) {
                                        val a = livePts[0]
                                        val b = livePts[1]
                                        listOf(Pt(a.x, a.y, 0.5f), Pt(b.x, a.y, 0.5f), Pt(b.x, b.y, 0.5f), Pt(a.x, b.y, 0.5f))
                                    } else livePts.toList()
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
                            live.style = if (toolNow.isPen()) PenPrefs.lineStyle else LineStyle.SOLID
                            live.sharp = false
                            val eraseR = eraserNow

                            // أداة الملاحظات: نقرة القلم تضع ملاحظة
                            if (PenPrefs.stickyActive && down.type != PointerType.Eraser) {
                                down.consume()
                                placeSticky(session, index, down.position.x / w, down.position.y / w, 1f / ratio)
                                while (true) {
                                    val e = awaitPointerEvent()
                                    val ch = e.changes.firstOrNull { it.id == id } ?: break
                                    ch.consume()
                                    if (!ch.pressed) break
                                }
                                continue
                            }
                            // الكتابة داخل ملاحظة لاصقة تنتمي إليها (تتحرك معها)
                            val noteTarget = if (PenPrefs.shapeActive) null
                            else session.noteAt(index, down.position.x / w, down.position.y / w)?.id

                            // أداة الأشكال: سحب من نقطة إلى نقطة يرسم الشكل المختار
                            if (PenPrefs.shapeActive && down.type != PointerType.Eraser &&
                                !ev.buttons.isSecondaryPressed && !ev.buttons.isTertiaryPressed
                            ) {
                                down.consume()
                                val kind = PenPrefs.shapeKind
                                val sw = Sizes.width(Tool.BALLPOINT, PenPrefs.shapeSize)
                                live.tool = Tool.BALLPOINT
                                live.color = Color(PenPrefs.shapeColor)
                                live.width = sw
                                live.style = PenPrefs.shapeStyle
                                live.sharp = true
                                val a = Pt(down.position.x / w, down.position.y / w, 0.5f)
                                var bEnd = a
                                if (session.selection != null) session.selection = null
                                livePts.clear()
                                try {
                                    while (true) {
                                        val e = awaitPointerEvent()
                                        val ch = e.changes.firstOrNull { it.id == id } ?: break
                                        if (!ch.pressed) { ch.consume(); break }
                                        bEnd = Pt(ch.position.x / w, ch.position.y / w, 0.5f)
                                        val parts = ShapeBuilder.build(kind, a, bEnd, PenPrefs.shapeRounded, ArrowHead.NONE, ArrowHead.NONE, sw)
                                        livePts.clear()
                                        livePts.addAll(parts.first())
                                        tick++
                                        ch.consume()
                                    }
                                } finally {
                                    livePts.clear()
                                    live.sharp = false
                                    tick++
                                    if (hypot(bEnd.x - a.x, bEnd.y - a.y) > 0.004f) {
                                        val parts = ShapeBuilder.build(kind, a, bEnd, PenPrefs.shapeRounded, PenPrefs.arrowStart, PenPrefs.arrowEnd, sw)
                                        session.beginEdit()
                                        val now = System.currentTimeMillis()
                                        parts.forEach { pts ->
                                            session.addInk(index, Ink(pts, Color(PenPrefs.shapeColor), sw, Tool.BALLPOINT, now, PenPrefs.shapeStyle, true))
                                        }
                                        session.endEdit()
                                    }
                                }
                                continue
                            }

                            AppController.lastInkAt = System.currentTimeMillis()
                            if (session.selection != null) session.selection = null
                            session.beginEdit()
                            livePts.clear()
                            // عرض الخط: ضغط القلم بعد المعايرة، أو السرعة للإصبع والأقلام بلا ضغط
                            val speedW = SpeedWidth()
                            fun pressureAt(c: PointerInputChange): Float =
                                PenInput.pressureFor(c.type, c.pressure, live.tool, speedW, c.position.x, c.position.y, c.uptimeMillis, w)
                            // الممحاة بالإصبع تبدأ بعد حركة واضحة (حتى لا تمسح لمسة بداية تمرير بإصبعين)
                            var eraseArmed = !byFinger
                            if (erasing) {
                                if (eraseArmed) session.eraseAt(index, Offset(down.position.x / w, down.position.y / w), eraseR)
                                eraserCursor[0] = down.position
                                eraserR[0] = eraseR
                                tick++
                            } else {
                                liveGen[0]++
                                livePts.add(Pt(down.position.x / w, down.position.y / w, pressureAt(down)))
                                tick++
                            }
                            down.consume()
                            // تثبيت الخط: كل نقطة تقترب من موضع القلم بنسبة (أقل = أنعم)
                            val follow = 1f - PenPrefs.stabilizeFor(toolNow) * 0.85f
                            var sx = down.position.x / w
                            var sy = down.position.y / w
                            var lastMoveAt = System.currentTimeMillis()
                            var lastMoveX = down.position.x
                            var lastMoveY = down.position.y
                            fun addLive(px: Float, py: Float, pp: Float) {
                                sx += (px / w - sx) * follow
                                sy += (py / w - sy) * follow
                                livePts.add(Pt(sx, sy, pp))
                                if (hypot(px - lastMoveX, py - lastMoveY) > 6f) {
                                    lastMoveAt = System.currentTimeMillis()
                                    lastMoveX = px
                                    lastMoveY = py
                                }
                            }

                            // آخر عيّنتين (للتنبؤ بموضع القلم وتقليل التأخر الظاهر)
                            var tA = down.uptimeMillis
                            var xA = down.position.x
                            var yA = down.position.y
                            var tB = tA
                            var xB = xA
                            var yB = yA

                            // «ارسم وأبقِ»: بعد ثباته لحظة يتحول الرسم إلى شكل وهو ما زال على الشاشة
                            var snappedLive: List<Pt>? = null
                            /** آخر توقف جُرّب عنده التعرف على الشكل (مرة واحدة لكل توقف، لا مع كل حدث). */
                            var snapTriedAt = -1L
                            fun trySnapLive() {
                                if (snappedLive != null || erasing || !live.tool.isPen() || !PenPrefs.drawAndHold) return
                                if (System.currentTimeMillis() - lastMoveAt < 450L || livePts.size < 3) return
                                if (snapTriedAt == lastMoveAt) return
                                snapTriedAt = lastMoveAt
                                val shape = ShapeRecognizer.recognize(livePts.toList()) ?: return
                                snappedLive = shape
                                livePts.clear()
                                livePts.addAll(shape)
                                live.hasPred = false
                                live.sharp = true
                                tick++
                            }
                            /** خط الإصبع أُلغي: بداية تمرير بإصبعين أو راحة يد قبل نزول القلم. */
                            var aborted = false
                            try {
                                while (true) {
                                    val e = withTimeoutOrNull(480L) { awaitPointerEvent() }
                                    if (e == null) {
                                        trySnapLive()
                                        continue
                                    }
                                    val ch = e.changes.firstOrNull { it.id == id } ?: break
                                    if (!ch.pressed) {
                                        ch.consume()
                                        break
                                    }
                                    if (fingerInterrupted(e)) {
                                        val stylus = e.changes.any { it.pressed && (it.type == PointerType.Stylus || it.type == PointerType.Eraser) }
                                        var len = 0f
                                        for (k in 1 until livePts.size) len += hypot(livePts[k].x - livePts[k - 1].x, livePts[k].y - livePts[k - 1].y)
                                        // نزل القلم: لمسة الإصبع كانت راحة اليد. إصبع ثانٍ مبكرًا أو بعد خط قصير: تمرير لا كتابة
                                        aborted = stylus || ch.uptimeMillis - down.uptimeMillis < 300L || len < 0.012f
                                        if (stylus) carry = e
                                        break
                                    }
                                    if (erasing && !eraseArmed) {
                                        if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop ||
                                            ch.uptimeMillis - down.uptimeMillis > 120L
                                        ) eraseArmed = true else { ch.consume(); continue }
                                    }
                                    AppController.lastInkAt = System.currentTimeMillis()
                                    val pr = pressureAt(ch)
                                    if (snappedLive != null) {
                                        // بعد التحول إلى شكل: لا تُضاف نقاط حتى رفع القلم
                                        ch.consume()
                                        continue
                                    }
                                    if (erasing) {
                                        for (h in ch.historical) {
                                            session.eraseAt(index, Offset(h.position.x / w, h.position.y / w), eraseR)
                                        }
                                        session.eraseAt(index, Offset(ch.position.x / w, ch.position.y / w), eraseR)
                                        eraserCursor[0] = ch.position
                                        tick++
                                    } else {
                                        for (h in ch.historical) {
                                            addLive(h.position.x, h.position.y, pr)
                                            tA = tB
                                            xA = xB
                                            yA = yB
                                            tB = h.uptimeMillis
                                            xB = h.position.x
                                            yB = h.position.y
                                        }
                                        addLive(ch.position.x, ch.position.y, pr)
                                        tA = tB
                                        xA = xB
                                        yA = yB
                                        tB = ch.uptimeMillis
                                        xB = ch.position.x
                                        yB = ch.position.y

                                        // لا تنبؤ بموضع القلم: كان يرسم خطًا وهميًا أمام القلم يظهر ثم يختفي عند الرفع
                                        live.hasPred = false
                                        tick++
                                        trySnapLive()
                                    }
                                    ch.consume()
                                }
                            } finally {
                                live.hasPred = false
                                live.sharp = false
                                // بعد رفع القلم تختفي الحدود (وتعود مع التحويم إن دعمه الجهاز)
                                if (erasing) {
                                    eraserCursor[0] = null
                                    tick++
                                }
                                // «إلغاء التحديد تلقائيًا»: بعد المسح بالممحاة يعود آخر قلم
                                val backToPen = erasing && toolNow == Tool.ERASER && PenPrefs.autoDeselect
                                // «ارسم وأبقِ»: القلم بقي ثابتًا لحظة قبل الرفع
                                val held = System.currentTimeMillis() - lastMoveAt >= 450L
                                if (aborted) {
                                    livePts.clear()
                                    snappedLive = null
                                }
                                var snapPts: List<Pt>? = null
                                if (!erasing && livePts.isNotEmpty()) {
                                    val hlStraight = live.tool == Tool.HIGHLIGHTER && PenPrefs.hlStraight
                                    if (live.tool == Tool.HIGHLIGHTER && AppController.highlightSnap && noteTarget == null) {
                                        snapPts = if (hlStraight) PenPrefs.straighten(livePts.toList()) else livePts.toList()
                                    } else if (hlStraight) {
                                        val ink = Ink(PenPrefs.straighten(livePts.toList()), live.color, live.width, live.tool, System.currentTimeMillis())
                                        if (noteTarget != null) session.addNoteInk(noteTarget, ink) else {
                                            pendingInk.add(ink)
                                            session.addInk(index, ink)
                                        }
                                    } else {
                                        // الأقلام: رسم يشبه شكلًا واضحًا يتحول إلى شكل مرتب
                                        val raw = livePts.toList()
                                        // الشكل المثالي فقط عند إبقاء القلم ثابتًا لحظة بعد الرسم
                                        val shaped = snappedLive ?: if (live.tool.isPen() && PenPrefs.drawAndHold && held) {
                                            ShapeRecognizer.recognize(raw)
                                        } else null
                                        // بلا تدبيب عند الحفظ: الخط المحفوظ يطابق ما كان ظاهرًا أثناء الكتابة
                                        val pts = shaped ?: raw
                                        val ink = Ink(pts, live.color, live.width, live.tool, System.currentTimeMillis(), live.style, shaped != null)
                                        if (noteTarget != null) session.addNoteInk(noteTarget, ink) else {
                                            pendingInk.add(ink)
                                            session.addInk(index, ink)
                                        }
                                    }
                                }
                                livePts.clear()
                                tick++
                                session.endEdit()
                                // الهايلايتر الملتقط: يتحول الخط إلى تمييز خلف كلمات السطر ويُضاف إلى الملخصات
                                if (snapPts != null) session.snapHighlight(index, snapPts, live.color, live.width)
                                if (backToPen) AppController.selectTool(AppController.lastWriting)
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

                        // الملاحظات اللاصقة: وضع، تحديد، تحريك، تكبير
                        if (PenPrefs.stickyActive) {
                            down.consume()
                            placeSticky(session, index, nx, ny, 1f / ratio)
                            return@awaitEachGesture
                        }
                        if (dragNote(session, index, down, w)) return@awaitEachGesture

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

                        // وضع «القلم والإصبع»: الإصبع يكتب (يعالجه مستقبل القلم)، فلا تحديد نص بالضغط المطوّل
                        if (PenInput.fingerWrites && session.zoomBox == null && !PenInput.stylusActiveRecently()) return@awaitEachGesture

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
                    val shown = if (PenPrefs.lassoRect && livePts.size >= 2) {
                        val a = livePts[0]
                        val b = livePts[1]
                        listOf(a, Pt(b.x, a.y, 0.5f), b, Pt(a.x, b.y, 0.5f), a)
                    } else livePts
                    shown.forEachIndexed { i, p ->
                        if (i == 0) pth.moveTo(p.x * size.width, p.y * size.width) else pth.lineTo(p.x * size.width, p.y * size.width)
                    }
                    drawPath(
                        pth, Palette.Navy,
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                    )
                }
            } else {
                // خطوط رُفع عنها القلم ولم تظهر بعد في صورة الخطوط
                pendingInk.forEach { drawStroke(it, if (night) nightOf(it.color) else it.color) }
                val lc = if (night) nightOf(live.color) else live.color
                if (livePts.isNotEmpty() && extra == null && liveRaster.handles(live.style, live.sharp)) {
                    liveRaster.draw(this, livePts, lc, live.width, live.tool, visRect.value, liveGen[0])
                } else {
                    drawInk(livePts, lc, live.width, live.tool, if (live.sharp) null else extra, style = live.style, sharp = live.sharp)
                }
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

private class PdfPatch(val bmp: Bitmap, val fullW: Int, val l: Int, val t: Int) {
    val img: ImageBitmap = bmp.asImageBitmap()
}

/**
 * عند التكبير أكثر من دقة صورة الصفحة: يُرسم الجزء الظاهر وحده بدقة الشاشة بعد توقف الحركة،
 * ويبقى في مكانه من الصفحة أثناء التمرير حتى يُرسم الجزء الجديد.
 */
@Composable
private fun PdfSharpPatch(pdf: PdfDoc, ref: PageRef?, wPx: Int, ratio: Float, vis: androidx.compose.runtime.State<androidx.compose.ui.geometry.Rect>, night: Boolean) {
    val scope = rememberCoroutineScope()
    var patch by remember { mutableStateOf<PdfPatch?>(null) }
    val wNow by rememberUpdatedState(wPx)
    fun set(p: PdfPatch?) {
        val old = patch
        patch = p
        if (old != null) scope.launch {
            // قد تكون في الإطار الجاري رسمه
            withFrameNanos { }
            withFrameNanos { }
            old.bmp.recycle()
        }
    }
    LaunchedEffect(pdf, ref, ratio) {
        snapshotFlow { vis.value to wNow }.collectLatest { (v, w) ->
            if (ref == null || w <= 0 || w < renderWidthFor(w) * SHARP_MIN_RATIO) {
                if (patch != null) set(null)
                return@collectLatest
            }
            val h = (w / ratio).toInt()
            val l = floor(v.left.coerceIn(0f, w.toFloat())).toInt()
            val t = floor(v.top.coerceIn(0f, h.toFloat())).toInt()
            val r = ceil(v.right.coerceIn(0f, w.toFloat())).toInt()
            val b = ceil(v.bottom.coerceIn(0f, h.toFloat())).toInt()
            if (r - l < 1 || b - t < 1) return@collectLatest
            val cur = patch
            if (cur != null && cur.fullW == w && cur.l == l && cur.t == t && cur.bmp.width == r - l && cur.bmp.height == b - t) return@collectLatest
            delay(150)
            var out: Bitmap? = null
            try {
                withContext(Dispatchers.IO) {
                    val ctx = coroutineContext
                    out = try {
                        pdf.renderRegion(ref, w, l, t, r - l, b - t) { ctx.isActive }
                    } catch (e: Throwable) {
                        null
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                out?.recycle()
                throw e
            }
            out?.let { set(PdfPatch(it, w, l, t)) }
        }
    }
    DisposableEffect(Unit) { onDispose { patch?.bmp?.recycle() } }
    val p = patch ?: return
    Canvas(Modifier.fillMaxSize()) {
        if (p.fullW == size.width.roundToInt() && !p.bmp.isRecycled) {
            drawImage(
                p.img, IntOffset.Zero, IntSize(p.bmp.width, p.bmp.height),
                IntOffset(p.l, p.t), IntSize(p.bmp.width, p.bmp.height),
                colorFilter = if (night) ColorFilter.colorMatrix(ColorMatrix(NightMatrix)) else null
            )
        }
    }
}

/**
 * شريط تمرير بالصفحات على حافة الشاشة: موضع المقبض يقابل موضع التمرير في المستند بشكل متصل
 * (صفحة + موضع داخلها)، والمستند يلحق المقبض بحركة ناعمة سريعة مارًّا بالصفحات بدل القفز إليها،
 * وعند الإفلات يكمل بسرعة الإصبع ويتباطأ حتى يتوقف حيث وصل. تظهر صورة الصفحة ورقمها أثناء الحركة.
 */
@Composable
fun PageScrubber(s: DocSession, pdf: PdfDoc, modifier: Modifier) {
    val n = pdf.count
    if (n < 2) return
    /** 0 ساكن، 1 سحب، 2 استمرار بعد الإفلات. */
    var mode by remember { mutableStateOf(0) }
    var dragPage by remember { mutableStateOf(0) }
    var dragY by remember { mutableStateOf(0f) }
    /** موضع المستند الذي يقوده الشريط بوحدة الصفحات (0 … n-1)، والهدف الذي يلحقه، والسرعة بعد الإفلات. */
    var pos by remember { mutableStateOf(0f) }
    val target = remember { floatArrayOf(0f) }
    val vel = remember { floatArrayOf(0f) }
    BoxWithConstraints(modifier.fillMaxHeight().width(150.dp)) {
        val trackH = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        val handleH = with(density) { 54.dp.toPx() }
        val range = (trackH - handleH).coerceAtLeast(1f)
        val last = (n - 1).toFloat()
        /** موضع التمرير الفعلي بوحدة الصفحات. يُقرأ داخل طبقة الرسم فقط فلا تُعاد التركيبة. */
        fun docPos(): Float {
            val st = s.listState
            val h = (st.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1).coerceAtLeast(1)
            return (st.firstVisibleItemIndex + st.firstVisibleItemScrollOffset / h.toFloat()).coerceIn(0f, last)
        }
        fun handleY(): Float = when (mode) {
            1 -> dragY
            2 -> range * pos / last
            else -> range * docPos() / last
        }

        // محرك واحد يقود المستند إطارًا بإطار أثناء السحب وبعد الإفلات
        LaunchedEffect(mode != 0) {
            if (mode == 0) return@LaunchedEffect
            var prevT = 0L
            while (mode != 0) {
                val t = withFrameNanos { it }
                val dt = if (prevT == 0L) 0.016f else ((t - prevT) / 1e9f).coerceIn(0.001f, 0.05f)
                prevT = t
                if (mode == 2) {
                    // الاستمرار بعد الإفلات: تباطؤ تدريجي
                    target[0] = (target[0] + vel[0] * dt).coerceIn(0f, last)
                    vel[0] *= exp(-dt / 0.35f)
                    if (target[0] <= 0f || target[0] >= last) vel[0] = 0f
                }
                // يلحق الهدف بسرعة (ثابت زمني 70 ms) فيمر بالصفحات ولا يقفز
                val k = 1f - exp(-dt / 0.07f)
                var p = pos + (target[0] - pos) * k
                if (abs(target[0] - p) < 0.002f) p = target[0]
                pos = p
                val page = floor(p).toInt().coerceIn(0, n - 1)
                val first = s.listState.layoutInfo.visibleItemsInfo.firstOrNull()
                // ارتفاع الصفحة الهدف من عرض الصفحات الظاهرة ونسبة أبعادها
                val pageW = if (first != null) first.size * pdf.ratio(first.index) else 0f
                val pageH = if (pageW > 0f) pageW / pdf.ratio(page) else 0f
                val off = ((p - page) * pageH).roundToInt().coerceAtLeast(0)
                try {
                    s.listState.scrollToItem(page, off)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // لمسة على المستند أخذت التمرير: يتوقف الشريط
                    if (!kotlinx.coroutines.currentCoroutineContext().isActive) throw e
                    mode = 0
                    break
                }
                if (mode == 2) {
                    dragPage = p.roundToInt().coerceIn(0, n - 1)
                    if (abs(vel[0]) < 0.05f && abs(target[0] - pos) < 0.01f) mode = 0
                }
            }
        }

        // فقاعة المعاينة أثناء الحركة
        if (mode != 0) {
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
                    .offset { IntOffset(-with(density) { 34.dp.roundToPx() }, (handleY() - handleH).roundToInt().coerceAtLeast(0)) }
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
        // منطقة اللمس أعرض من المقبض المرئي، والحركة بطبقة رسم (بلا إعادة تركيب أو قياس)
        Box(
            Modifier.align(Alignment.TopEnd)
                .graphicsLayer { translationY = handleY() }
                .width(44.dp).height(54.dp)
                .pointerInput(n, trackH) {
                    val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            // يبدأ من موضع المستند الحالي (أو من حيث وصل الاستمرار السابق)
                            if (mode == 0) pos = docPos()
                            dragY = range * pos / last
                            target[0] = pos
                            vel[0] = 0f
                            dragPage = pos.roundToInt().coerceIn(0, n - 1)
                            mode = 1
                        },
                        onDragEnd = {
                            // سرعة الإصبع (بكسل/ث) بوحدة الصفحات
                            val v = tracker.calculateVelocity().y
                            vel[0] = v / range * last
                            mode = 2
                        },
                        onDragCancel = { mode = 2 }
                    ) { change, dy ->
                        change.consume()
                        // موضع متراكم يتبع الإصبع بدقة، والمستند يلحقه بشكل متصل
                        dragY = (dragY + dy).coerceIn(0f, range)
                        // المقبض يتحرك مع الإصبع، فالسرعة تُحسب من موضعه المتراكم
                        tracker.addPosition(change.uptimeMillis, Offset(0f, dragY))
                        target[0] = dragY / range * last
                        val p = target[0].roundToInt().coerceIn(0, n - 1)
                        if (p != dragPage) dragPage = p
                    }
                }
        ) {
            Box(
                Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)
                    .width(18.dp).height(54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (mode == 1) Palette.Navy else Palette.Navy.copy(alpha = 0.35f))
            )
        }
    }
}


/** يضع ملاحظة لاصقة (أو الملاحظة المنسوخة) عند النقطة، ثم يُطفئ وضع الإضافة. */
private fun placeSticky(session: DocSession, page: Int, nx: Float, ny: Float, pageH: Float) {
    val clip = AppController.noteClipboard
    val fresh = newStickyAt(page, nx, ny, pageH)
    val n = if (AppController.notePastePending && clip != null) {
        clip.copy(id = fresh.id, page = page, x = (nx - clip.w / 2f).coerceIn(0f, 1f - clip.w), y = (ny - clip.h / 2f).coerceAtLeast(0f), collapsed = false)
    } else fresh
    AppController.notePastePending = false
    session.addNote(n)
    PenPrefs.stickyActive = false
}

/**
 * لمسة الإصبع على ملاحظة: نقرة تحددها (أو تفتح المطوية)، والسحب يحركها،
 * والسحب من زاوية الملاحظة المحددة يغيّر حجمها. يعيد true إن تعاملت مع اللمسة.
 */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.dragNote(
    session: DocSession, page: Int, down: PointerInputChange, w: Float
): Boolean {
    val nx = down.position.x / w
    val ny = down.position.y / w
    val sel = session.note(session.selectedNoteId)?.takeIf { it.page == page && !it.collapsed }
    val corner = 0.035f
    val resize = sel != null && hypot(nx - (sel.x + sel.w), ny - (sel.y + sel.h)) < corner
    val target = if (resize) sel else session.noteAt(page, nx, ny, includeCollapsed = true)
    if (target == null) {
        session.noteTextEditId = null
        if (session.selectedNoteId != null) session.selectedNoteId = null
        return false
    }
    down.consume()
    session.beginEdit()
    var last = down.position
    var moved = false
    try {
        while (true) {
            val e = awaitPointerEvent()
            val ch = e.changes.firstOrNull { it.id == down.id } ?: break
            if (!ch.pressed) break
            if (!moved && (ch.position - down.position).getDistance() < viewConfiguration.touchSlop) continue
            moved = true
            val dx = (ch.position.x - last.x) / w
            val dy = (ch.position.y - last.y) / w
            last = ch.position
            if (resize) {
                session.updateNote(target.id) { it.copy(w = (it.w + dx).coerceIn(0.08f, 0.9f), h = (it.h + dy).coerceIn(0.08f, 1.2f)) }
            } else {
                session.updateNote(target.id) { it.copy(x = (it.x + dx).coerceIn(-0.05f, 0.98f), y = (it.y + dy).coerceAtLeast(0f)) }
            }
            ch.consume()
        }
    } finally {
        session.endEdit()
    }
    if (!moved) {
        if (target.collapsed) session.editNote(target.id) { it.copy(collapsed = false) }
        // نقرة على الملاحظة المحددة تفتح كتابة النص بلوحة المفاتيح
        else if (session.selectedNoteId == target.id) session.noteTextEditId = target.id
        session.selectedNoteId = target.id
    } else {
        session.selectedNoteId = target.id
    }
    return true
}
