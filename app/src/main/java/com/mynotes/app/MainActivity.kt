
package com.mynotes.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Canvas as AndroidCanvas
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

private val AppBlue = Color(0xFF1E3A8A)
private val PageBackground = Color(0xFFF3F5F9)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = PageBackground
                ) {
                    NoteScreen()
                }
            }
        }
    }
}

private data class InkPoint(
    val x: Float,
    val y: Float
)

private data class InkStroke(
    val points: List<InkPoint>,
    val color: Int,
    val width: Float = 3.5f
)

private class PdfDoc(file: File) {
    private val descriptor: ParcelFileDescriptor =
        ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_READ_ONLY
        )

    private val renderer = PdfRenderer(descriptor)

    val pageCount: Int
        get() = renderer.pageCount

    @Synchronized
    fun renderPage(
        pageIndex: Int,
        targetWidth: Int
    ): Bitmap {
        val page = renderer.openPage(pageIndex)

        try {
            val width = targetWidth.coerceAtLeast(300)
            val height = (
                page.height.toFloat() *
                    width.toFloat() /
                    page.width.toFloat()
                ).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            )

            bitmap.eraseColor(AndroidColor.WHITE)

            page.render(
                bitmap,
                null,
                null,
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
            )

            return bitmap
        } finally {
            page.close()
        }
    }

    fun close() {
        try {
            renderer.close()
        } finally {
            descriptor.close()
        }
    }
}

private class OpenTab(
    val id: String,
    val file: File,
    val doc: PdfDoc,
    initialAnnotations: Map<Int, List<InkStroke>> = emptyMap()
) {
    var annotations by mutableStateOf(initialAnnotations)

    val undo = mutableStateListOf<Map<Int, List<InkStroke>>>()
    val redo = mutableStateListOf<Map<Int, List<InkStroke>>>()
}

private enum class DrawingTool {
    PEN,
    ERASER
}

@Composable
private fun NoteScreen() {
    val context = LocalContext.current

    val libraryDir = remember {
        File(context.filesDir, "pdf_library").apply {
            mkdirs()
        }
    }

    val pdfFiles = remember {
        mutableStateListOf<File>()
    }

    val tabs = remember {
        mutableStateListOf<OpenTab>()
    }

    var activeTabId by remember {
        mutableStateOf<String?>(null)
    }

    var drawingTool by remember {
        mutableStateOf(DrawingTool.PEN)
    }

    var selectedColor by remember {
        mutableStateOf(Color(0xFF172554))
    }

    fun refreshLibrary() {
        val files = libraryDir.listFiles()
            ?.filter {
                it.isFile &&
                    it.extension.equals("pdf", ignoreCase = true)
            }
            ?.sortedBy {
                it.name.lowercase()
            }
            ?: emptyList()

        pdfFiles.clear()
        pdfFiles.addAll(files)
    }

    fun openPdf(file: File) {
        val existing = tabs.firstOrNull {
            it.file.absolutePath == file.absolutePath
        }

        if (existing != null) {
            activeTabId = existing.id
            return
        }

        try {
            val document = PdfDoc(file)

            val tab = OpenTab(
                id = file.absolutePath,
                file = file,
                doc = document,
                initialAnnotations = loadAnnotations(
                    context,
                    file
                )
            )

            tabs.add(tab)
            activeTabId = tab.id
            drawingTool = DrawingTool.PEN
        } catch (exception: Exception) {
            Toast.makeText(
                context,
                "تعذر فتح ملف PDF: ${exception.localizedMessage ?: "خطأ غير معروف"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun importPdf(uri: Uri): File? {
        return try {
            var originalName = "Document.pdf"

            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val column = cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                    )

                    if (column >= 0) {
                        originalName =
                            cursor.getString(column) ?: originalName
                    }
                }
            }

            originalName = originalName
                .replace(Regex("""[\\/:*?"<>|]"""), "_")
                .trim()
                .ifBlank { "Document.pdf" }

            if (!originalName.endsWith(".pdf", ignoreCase = true)) {
                originalName += ".pdf"
            }

            var destination = File(libraryDir, originalName)

            if (destination.exists()) {
                val baseName = originalName.removeSuffix(".pdf")
                destination = File(
                    libraryDir,
                    "${baseName}_${System.currentTimeMillis()}.pdf"
                )
            }

            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("تعذر قراءة الملف")

            input.use { source ->
                FileOutputStream(destination).use { output ->
                    source.copyTo(output)
                }
            }

            destination
        } catch (exception: Exception) {
            Toast.makeText(
                context,
                "تعذرت إضافة الملف: ${exception.localizedMessage ?: "خطأ غير معروف"}",
                Toast.LENGTH_LONG
            ).show()

            null
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val file = importPdf(uri)

            if (file != null) {
                refreshLibrary()
                openPdf(file)
            }
        }
    }

    fun closeTab(tab: OpenTab) {
        saveAnnotations(context, tab.file, tab.annotations)

        try {
            tab.doc.close()
        } catch (_: Exception) {
            // تجاهل أخطاء إغلاق الملف
        }

        tabs.remove(tab)

        if (activeTabId == tab.id) {
            activeTabId = tabs.lastOrNull()?.id
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            tabs.forEach { tab ->
                saveAnnotations(
                    context,
                    tab.file,
                    tab.annotations
                )

                try {
                    tab.doc.close()
                } catch (_: Exception) {
                    // تجاهل أخطاء الإغلاق عند مغادرة الشاشة
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshLibrary()
    }

    val activeTab = tabs.firstOrNull {
        it.id == activeTabId
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppBlue)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "MyNotes",
                    color = Color.White,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "مكتبتك للكتابة والملاحظات",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }

            TextButton(
                onClick = {
                    activeTabId = null
                }
            ) {
                Text(
                    "المكتبة",
                    color = Color.White
                )
            }

            Button(
                onClick = {
                    pdfPicker.launch(
                        arrayOf("application/pdf")
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = AppBlue
                )
            ) {
                Text("＋ إضافة PDF")
            }
        }

        if (tabs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE5EAF4))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    Row(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .background(
                                if (tab.id == activeTabId) {
                                    Color.White
                                } else {
                                    Color(0xFFDCE3F0)
                                },
                                RoundedCornerShape(8.dp)
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                activeTabId = tab.id
                            }
                        ) {
                            Text(
                                text = tab.file.name,
                                color = if (tab.id == activeTabId) {
                                    AppBlue
                                } else {
                                    Color.DarkGray
                                },
                                maxLines = 1
                            )
                        }

                        TextButton(
                            onClick = {
                                closeTab(tab)
                            }
                        ) {
                            Text(
                                "×",
                                color = Color(0xFFB91C1C),
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            }
        }

        if (activeTab == null) {
            LibraryScreen(
                files = pdfFiles,
                onOpenFile = ::openPdf,
                onImport = {
                    pdfPicker.launch(
                        arrayOf("application/pdf")
                    )
                }
            )
        } else {
            ReaderToolbar(
                tab = activeTab,
                tool = drawingTool,
                selectedColor = selectedColor,
                onSelectTool = {
                    drawingTool = it
                },
                onSelectColor = {
                    selectedColor = it
                    drawingTool = DrawingTool.PEN
                },
                onUndo = {
                    if (activeTab.undo.isNotEmpty()) {
                        activeTab.redo.add(activeTab.annotations)
                        activeTab.annotations =
                            activeTab.undo.removeAt(
                                activeTab.undo.lastIndex
                            )

                        saveAnnotations(
                            context,
                            activeTab.file,
                            activeTab.annotations
                        )
                    }
                },
                onRedo = {
                    if (activeTab.redo.isNotEmpty()) {
                        activeTab.undo.add(activeTab.annotations)
                        activeTab.annotations =
                            activeTab.redo.removeAt(
                                activeTab.redo.lastIndex
                            )

                        saveAnnotations(
                            context,
                            activeTab.file,
                            activeTab.annotations
                        )
                    }
                },
                onBackToLibrary = {
                    activeTabId = null
                }
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(
                    top = 12.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(activeTab.doc.pageCount) { pageIndex ->
                    PageView(
                        context = context,
                        tab = activeTab,
                        pageIndex = pageIndex,
                        tool = drawingTool,
                        penColor = selectedColor
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    files: List<File>,
    onOpenFile: (File) -> Unit,
    onImport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            text = "مكتبة المستندات",
            color = AppBlue,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "افتح ملفات PDF وأضف ملاحظاتك عليها.",
            color = Color.DarkGray,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(18.dp))

        if (files.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "لا توجد ملفات في المكتبة بعد",
                        color = AppBlue,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "أضف ملف PDF للبدء بالقراءة والكتابة عليه.",
                        color = Color.Gray
                    )

                    Spacer(Modifier.height(18.dp))

                    Button(onClick = onImport) {
                        Text("＋ إضافة ملف PDF")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(
                    items = files,
                    key = { it.absolutePath }
                ) { file ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White,
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        Color(0xFFE8EEFC),
                                        RoundedCornerShape(10.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "PDF",
                                    color = AppBlue,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = file.name,
                                    color = Color(0xFF202938),
                                    fontWeight = FontWeight.SemiBold
                                )

                                Text(
                                    text = formatFileSize(file.length()),
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }

                            Button(
                                onClick = {
                                    onOpenFile(file)
                                }
                            ) {
                                Text("فتح")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderToolbar(
    tab: OpenTab,
    tool: DrawingTool,
    selectedColor: Color,
    onSelectTool: (DrawingTool) -> Unit,
    onSelectColor: (Color) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onBackToLibrary: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = tab.file.name,
            modifier = Modifier.padding(
                start = 8.dp,
                top = 4.dp,
                bottom = 4.dp
            ),
            color = AppBlue,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBackToLibrary) {
                Text("المكتبة")
            }

            TextButton(
                onClick = {
                    onSelectTool(DrawingTool.PEN)
                }
            ) {
                Text(
                    text = "✎ قلم",
                    color = if (tool == DrawingTool.PEN) {
                        AppBlue
                    } else {
                        Color.DarkGray
                    },
                    fontWeight = if (tool == DrawingTool.PEN) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Normal
                    }
                )
            }

            TextButton(
                onClick = {
                    onSelectTool(DrawingTool.ERASER)
                }
            ) {
                Text(
                    text = "ممحاة",
                    color = if (tool == DrawingTool.ERASER) {
                        Color(0xFFB91C1C)
                    } else {
                        Color.DarkGray
                    },
                    fontWeight = if (tool == DrawingTool.ERASER) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Normal
                    }
                )
            }

            TextButton(onClick = onUndo) {
                Text("↶ تراجع")
            }

            TextButton(onClick = onRedo) {
                Text("↷ إعادة")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "لون القلم:",
                color = Color.DarkGray,
                fontSize = 12.sp
            )

            Spacer(Modifier.width(8.dp))

            listOf(
                Color(0xFF172554),
                Color(0xFFDC2626),
                Color(0xFF2563EB),
                Color(0xFF15803D),
                Color(0xFF7C3AED)
            ).forEach { color ->
                val selected = color == selectedColor

                Surface(
                    onClick = {
                        onSelectColor(color)
                    },
                    modifier = Modifier
                        .padding(end = 9.dp)
                        .size(if (selected) 27.dp else 22.dp),
                    shape = RoundedCornerShape(50),
                    color = color,
                    border = if (selected) {
                        androidx.compose.foundation.BorderStroke(
                            2.dp,
                            Color.Black
                        )
                    } else {
                        null
                    }
                ) {}
            }
        }
    }
}

@Composable
private fun PageView(
    context: Context,
    tab: OpenTab,
    pageIndex: Int,
    tool: DrawingTool,
    penColor: Color
) {
    var bitmap by remember(tab.id, pageIndex) {
        mutableStateOf<Bitmap?>(null)
    }

    var livePoints by remember(tab.id, pageIndex) {
        mutableStateOf<List<InkPoint>>(emptyList())
    }

    LaunchedEffect(tab.id, pageIndex) {
        bitmap = withContext(Dispatchers.IO) {
            tab.doc.renderPage(
                pageIndex = pageIndex,
                targetWidth = 1200
            )
        }
    }

    val pageBitmap = bitmap

    val aspectRatio = pageBitmap?.let {
        it.width.toFloat() / it.height.toFloat()
    } ?: (595f / 842f)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .background(Color.White)
                .border(1.dp, Color(0xFFD1D5DB))
        ) {
            if (pageBitmap != null) {
                Image(
                    bitmap = pageBitmap.asImageBitmap(),
                    contentDescription = "صفحة PDF رقم ${pageIndex + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = AppBlue
                    )
                }
            }

            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val savedStrokes =
                    tab.annotations[pageIndex] ?: emptyList()

                fun drawInk(stroke: InkStroke) {
                    if (stroke.points.isEmpty()) return

                    val color = Color(stroke.color)

                    if (stroke.points.size == 1) {
                        val point = stroke.points.first()

                        drawCircle(
                            color = color,
                            radius = stroke.width / 2f,
                            center = Offset(
                                point.x * size.width,
                                point.y * size.height
                            )
                        )

                        return
                    }

                    val path = Path()

                    stroke.points.forEachIndexed { index, point ->
                        val x = point.x * size.width
                        val y = point.y * size.height

                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }

                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(
                            width = stroke.width,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                savedStrokes.forEach(::drawInk)

                if (livePoints.isNotEmpty()) {
                    drawInk(
                        InkStroke(
                            points = livePoints,
                            color = if (tool == DrawingTool.ERASER) {
                                AndroidColor.WHITE
                            } else {
                                penColor.toArgbCompat()
                            },
                            width = if (tool == DrawingTool.ERASER) {
                                24f
                            } else {
                                3.5f
                            }
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(
                        tab.id,
                        pageIndex,
                        tool,
                        penColor
                    ) {
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                requireUnconsumed = false
                            )

                            // اللمس بالإصبع لا يرسم، ليبقى التمرير ممكنًا.
                            if (
                                down.type != PointerType.Stylus &&
                                down.type != PointerType.Mouse
                            ) {
                                return@awaitEachGesture
                            }

                            fun normalizedPoint(
                                position: Offset
                            ): InkPoint {
                                return InkPoint(
                                    x = (
                                        position.x / size.width
                                        ).coerceIn(0f, 1f),
                                    y = (
                                        position.y / size.height
                                        ).coerceIn(0f, 1f)
                                )
                            }

                            val collectedPoints =
                                mutableListOf(
                                    normalizedPoint(down.position)
                                )

                            livePoints = collectedPoints.toList()
                            down.consume()

                            while (true) {
                                val event = awaitPointerEvent()

                                val change = event.changes.firstOrNull {
                                    it.id == down.id
                                } ?: break

                                collectedPoints.add(
                                    normalizedPoint(change.position)
                                )

                                livePoints = collectedPoints.toList()
                                change.consume()

                                if (!change.pressed) {
                                    break
                                }
                            }

                            val finishedPoints =
                                collectedPoints.toList()

                            livePoints = emptyList()

                            if (finishedPoints.isEmpty()) {
                                return@awaitEachGesture
                            }

                            val currentStrokes =
                                tab.annotations[pageIndex]
                                    ?: emptyList()

                            if (tool == DrawingTool.PEN) {
                                tab.undo.add(tab.annotations.toMap())
                                tab.redo.clear()

                                val stroke = InkStroke(
                                    points = finishedPoints,
                                    color = penColor.toArgbCompat()
                                )

                                tab.annotations =
                                    tab.annotations + (
                                        pageIndex to
                                            (currentStrokes + stroke)
                                        )
                            } else {
                                val eraseRadius = 0.025f

                                val remaining = currentStrokes.filterNot {
                                    stroke ->
                                    stroke.points.any { savedPoint ->
                                        finishedPoints.any { erasePoint ->
                                            val dx =
                                                savedPoint.x - erasePoint.x
                                            val dy =
                                                savedPoint.y - erasePoint.y

                                            dx * dx + dy * dy <
                                                eraseRadius * eraseRadius
                                        }
                                    }
                                }

                                if (remaining.size != currentStrokes.size) {
                                    tab.undo.add(tab.annotations.toMap())
                                    tab.redo.clear()

                                    tab.annotations =
                                        if (remaining.isEmpty()) {
                                            tab.annotations - pageIndex
                                        } else {
                                            tab.annotations + (
                                                pageIndex to remaining
                                                )
                                        }
                                }
                            }

                            saveAnnotations(
                                context,
                                tab.file,
                                tab.annotations
                            )
                        }
                    }
            )
        }

        Text(
            text = "صفحة ${pageIndex + 1} من ${tab.doc.pageCount}",
            modifier = Modifier.padding(top = 4.dp),
            color = Color.Gray,
            fontSize = 12.sp
        )
    }
}

private fun Color.toArgbCompat(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt().coerceIn(0, 255),
        (red * 255).toInt().coerceIn(0, 255),
        (green * 255).toInt().coerceIn(0, 255),
        (blue * 255).toInt().coerceIn(0, 255)
    )
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 ->
            String.format("%.1f KB", bytes / 1024.0)

        else ->
            String.format(
                "%.1f MB",
                bytes / (1024.0 * 1024.0)
            )
    }
}

private fun annotationFile(
    context: Context,
    pdfFile: File
): File {
    val directory = File(
        context.filesDir,
        "annotations"
    ).apply {
        mkdirs()
    }

    val stableName = pdfFile.absolutePath.hashCode()

    return File(
        directory,
        "annotations_$stableName.json"
    )
}

private fun saveAnnotations(
    context: Context,
    pdfFile: File,
    annotations: Map<Int, List<InkStroke>>
) {
    try {
        val root = JSONObject()
        val pages = JSONArray()

        annotations.forEach { (pageIndex, strokes) ->
            val pageObject = JSONObject()
            val strokeArray = JSONArray()

            strokes.forEach { stroke ->
                val strokeObject = JSONObject()
                val pointsArray = JSONArray()

                stroke.points.forEach { point ->
                    pointsArray.put(
                        JSONArray()
                            .put(point.x.toDouble())
                            .put(point.y.toDouble())
                    )
                }

                strokeObject.put("color", stroke.color)
                strokeObject.put("width", stroke.width.toDouble())
                strokeObject.put("points", pointsArray)

                strokeArray.put(strokeObject)
            }

            pageObject.put("page", pageIndex)
            pageObject.put("strokes", strokeArray)

            pages.put(pageObject)
        }

        root.put("pages", pages)

        val target = annotationFile(context, pdfFile)
        val temporary = File(target.parentFile, target.name + ".tmp")

        temporary.writeText(root.toString())

        if (!temporary.renameTo(target)) {
            target.writeText(temporary.readText())
            temporary.delete()
        }
    } catch (_: Exception) {
        // لا يتوقف التطبيق إذا تعذر حفظ التعليقات.
    }
}

private fun loadAnnotations(
    context: Context,
    pdfFile: File
): Map<Int, List<InkStroke>> {
    return try {
        val file = annotationFile(context, pdfFile)

        if (!file.exists()) {
            return emptyMap()
        }

        val root = JSONObject(file.readText())
        val pages = root.getJSONArray("pages")
        val result = mutableMapOf<Int, List<InkStroke>>()

        for (pageIndex in 0 until pages.length()) {
            val pageObject = pages.getJSONObject(pageIndex)
            val actualPage = pageObject.getInt("page")
            val strokeArray = pageObject.getJSONArray("strokes")
            val strokes = mutableListOf<InkStroke>()

            for (strokeIndex in 0 until strokeArray.length()) {
                val strokeObject =
                    strokeArray.getJSONObject(strokeIndex)

                val pointsArray =
                    strokeObject.getJSONArray("points")

                val points = mutableListOf<InkPoint>()

                for (pointIndex in 0 until pointsArray.length()) {
                    val pair =
                        pointsArray.getJSONArray(pointIndex)

                    points.add(
                        InkPoint(
                            x = pair.getDouble(0).toFloat(),
                            y = pair.getDouble(1).toFloat()
                        )
                    )
                }

                strokes.add(
                    InkStroke(
                        points = points,
                        color = strokeObject.getInt("color"),
                        width = strokeObject
                            .optDouble("width", 3.5)
                            .toFloat()
                    )
                )
            }

            result[actualPage] = strokes
        }

        result
    } catch (_: Exception) {
        emptyMap()
    }
}
