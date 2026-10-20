
package com.mynotes.app

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.min

private val Navy = Color(0xFF172554)
private val Blue = Color(0xFF2563EB)
private val PageBg = Color(0xFFF5F7FB)
private val Muted = Color(0xFF64748B)
private val InkBlack = Color(0xFF202124)
private val InkBlue = Color(0xFF1565C0)
private val InkRed = Color(0xFFC62828)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Blue,
                    onPrimary = Color.White,
                    background = PageBg,
                    surface = Color.White,
                    onSurface = Navy
                )
            ) {
                NoteScreen()
            }
        }
    }
}

private data class PdfDocumentItem(
    val id: String,
    val name: String,
    val pageCount: Int
)

private data class InkPoint(val x: Float, val y: Float)

private data class InkStroke(
    val points: List<InkPoint>,
    val color: Int,
    val width: Float = 4.5f
)

private enum class DrawingTool { PEN, ERASER }

private fun libraryPrefs(context: Context) =
    context.getSharedPreferences("mynotes_library", Context.MODE_PRIVATE)

private fun pdfDirectory(context: Context) =
    File(context.filesDir, "pdfs").apply { mkdirs() }

private fun annotationFile(
    context: Context,
    docId: String,
    page: Int
) = File(context.filesDir, "annotations/${docId}_$page.json")

private fun loadDocuments(context: Context): List<PdfDocumentItem> {
    return try {
        val raw = libraryPrefs(context).getString("documents", "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val doc = PdfDocumentItem(
                    id = obj.optString("id"),
                    name = obj.optString("name"),
                    pageCount = obj.optInt("pages", 1)
                )
                if (File(pdfDirectory(context), "${doc.id}.pdf").exists()) {
                    add(doc)
                }
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun saveDocuments(
    context: Context,
    documents: List<PdfDocumentItem>
) {
    val array = JSONArray()
    documents.forEach { doc ->
        array.put(
            JSONObject()
                .put("id", doc.id)
                .put("name", doc.name)
                .put("pages", doc.pageCount)
        )
    }
    libraryPrefs(context).edit()
        .putString("documents", array.toString())
        .apply()
}

private fun readStrokes(
    context: Context,
    docId: String,
    page: Int
): List<InkStroke> {
    return try {
        val file = annotationFile(context, docId, page)
        if (!file.exists()) return emptyList()

        val array = JSONArray(file.readText())
        buildList {
            for (i in 0 until array.length()) {
                val stroke = array.optJSONObject(i) ?: continue
                val pointsJson = stroke.optJSONArray("points") ?: continue
                val points = buildList {
                    for (j in 0 until pointsJson.length()) {
                        val p = pointsJson.optJSONObject(j) ?: continue
                        add(
                            InkPoint(
                                p.optDouble("x").toFloat(),
                                p.optDouble("y").toFloat()
                            )
                        )
                    }
                }
                if (points.isNotEmpty()) {
                    add(
                        InkStroke(
                            points = points,
                            color = stroke.optInt("color", InkBlack.value.toLong().toInt()),
                            width = stroke.optDouble("width", 4.5).toFloat()
                        )
                    )
                }
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun saveStrokes(
    context: Context,
    docId: String,
    page: Int,
    strokes: List<InkStroke>
) {
    try {
        val file = annotationFile(context, docId, page)
        file.parentFile?.mkdirs()

        val array = JSONArray()
        strokes.forEach { stroke ->
            val points = JSONArray()
            stroke.points.forEach { p ->
                points.put(
                    JSONObject()
                        .put("x", p.x.toDouble())
                        .put("y", p.y.toDouble())
                )
            }
            array.put(
                JSONObject()
                    .put("color", stroke.color)
                    .put("width", stroke.width.toDouble())
                    .put("points", points)
            )
        }

        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(array.toString())
        if (!temp.renameTo(file)) {
            file.writeText(array.toString())
            temp.delete()
        }
    } catch (_: Exception) {
        // لا يتعطل التطبيق إذا تعذرت كتابة ملف الملاحظات.
    }
}

private fun getDisplayName(context: Context, uri: Uri): String {
    var name: String? = null
    var cursor: Cursor? = null

    try {
        cursor = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )
        if (cursor?.moveToFirst() == true) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) name = cursor.getString(index)
        }
    } catch (_: Exception) {
    } finally {
        cursor?.close()
    }

    return name?.takeIf { it.isNotBlank() } ?: "document.pdf"
}

private fun importPdf(
    context: Context,
    uri: Uri
): PdfDocumentItem {
    val id = UUID.randomUUID().toString()
    val destination = File(pdfDirectory(context), "$id.pdf")

    try {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("تعذر فتح الملف")

        input.use { source ->
            FileOutputStream(destination).use { output ->
                source.copyTo(output)
            }
        }

        val pageCount = ParcelFileDescriptor.open(
            destination,
            ParcelFileDescriptor.MODE_READ_ONLY
        ).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                renderer.pageCount
            }
        }

        require(pageCount > 0) { "ملف PDF لا يحتوي على صفحات" }

        return PdfDocumentItem(
            id = id,
            name = getDisplayName(context, uri),
            pageCount = pageCount
        )
    } catch (e: Exception) {
        destination.delete()
        throw e
    }
}

private fun renderPdfPage(
    context: Context,
    docId: String,
    pageIndex: Int
): Bitmap? {
    return try {
        val file = File(pdfDirectory(context), "$docId.pdf")

        ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_READ_ONLY
        ).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (pageIndex !in 0 until renderer.pageCount) return null

                renderer.openPage(pageIndex).use { page ->
                    val scale = min(1800f / page.width, 2f)
                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)

                    val bitmap = Bitmap.createBitmap(
                        width,
                        height,
                        Bitmap.Config.ARGB_8888
                    )

                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(
                        bitmap,
                        null,
                        null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )
                    bitmap
                }
            }
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun NoteScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var documents by remember {
        mutableStateOf(loadDocuments(context))
    }
    var selectedDocument by remember {
        mutableStateOf<PdfDocumentItem?>(null)
    }
    var selectedTab by remember { mutableIntStateOf(0) }
    var importing by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                importing = true
                try {
                    val document = withContext(Dispatchers.IO) {
                        importPdf(context, uri)
                    }
                    documents = documents + document
                    saveDocuments(context, documents)
                    selectedDocument = document
                } catch (e: Exception) {
                    snackbar.showSnackbar(
                        "تعذرت إضافة الملف. تأكد أنه ملف PDF صالح."
                    )
                } finally {
                    importing = false
                }
            }
        }
    }

    if (selectedDocument != null) {
        PdfEditor(
            document = selectedDocument!!,
            onBack = { selectedDocument = null }
        )
        return
    }

    Scaffold(
        containerColor = PageBg,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    importLauncher.launch(arrayOf("application/pdf"))
                },
                containerColor = Blue,
                contentColor = Color.White
            ) {
                Text("＋", style = MaterialTheme.typography.headlineMedium)
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Text("▤") },
                    label = { Text("المكتبة") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Text("⚙") },
                    label = { Text("الإعدادات") }
                )
            }
        }
    ) { padding ->
        if (selectedTab == 0) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .statusBarsPadding()
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp)
                        .padding(top = 18.dp, bottom = 20.dp)
                ) {
                    Text(
                        "MyNotes",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "مساحتك الخاصة للدراسة وتدوين الملاحظات",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted
                    )

                    Spacer(Modifier.height(20.dp))

                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Color(0xFFE5EAF3)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "مكتبتك الدراسية",
                                    fontWeight = FontWeight.SemiBold,
                                    color = Navy
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${documents.size} ملف محفوظ",
                                    color = Muted,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Button(
                                onClick = {
                                    importLauncher.launch(
                                        arrayOf("application/pdf")
                                    )
                                },
                                enabled = !importing
                            ) {
                                Text(if (importing) "جارٍ الاستيراد…" else "＋ إضافة PDF")
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    Text(
                        "ملفاتك",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )
                }

                if (documents.isEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(26.dp))
                                .background(Color(0xFFE5EDFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "PDF",
                                color = Blue,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(
                            "مكتبتك فارغة حاليًا",
                            color = Navy,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "أضف أول ملف PDF لبدء القراءة والكتابة عليه.",
                            color = Muted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = {
                                importLauncher.launch(arrayOf("application/pdf"))
                            }
                        ) {
                            Text("اختيار ملف PDF")
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            start = 18.dp,
                            end = 18.dp,
                            top = 0.dp,
                            bottom = 100.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(documents, key = { it.id }) { doc ->
                            PdfDocumentCard(
                                document = doc,
                                onClick = { selectedDocument = doc },
                                onDelete = {
                                    documents = documents.filterNot {
                                        it.id == doc.id
                                    }
                                    saveDocuments(context, documents)
                                    File(pdfDirectory(context), "${doc.id}.pdf")
                                        .delete()

                                    File(context.filesDir, "annotations")
                                        .listFiles()
                                        ?.filter {
                                            it.name.startsWith("${doc.id}_")
                                        }
                                        ?.forEach { it.delete() }
                                }
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
            ) {
                Text(
                    "الإعدادات",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Navy
                )
                Spacer(Modifier.height(20.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color(0xFFE5EAF3)
                    )
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            "MyNotes",
                            fontWeight = FontWeight.Bold,
                            color = Navy
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "تطبيق لقراءة ملفات PDF والكتابة عليها.",
                            color = Muted
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("الملفات محفوظة داخل التطبيق.")
                        Text("عدد الملفات: ${documents.size}")
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfDocumentCard(
    document: PdfDocumentItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color(0xFFE6EAF1)
        )
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(width = 62.dp, height = 76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFEAF0FF)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "PDF",
                    color = Blue,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    document.name,
                    color = Navy,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    "${document.pageCount} صفحة",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "اضغط لفتح الملف",
                    color = Blue,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            TextButton(onClick = { confirmDelete = true }) {
                Text("حذف", color = Color(0xFFB91C1C))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف الملف؟") },
            text = {
                Text("سيُحذف الملف وملاحظاته المحفوظة من التطبيق.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    }
                ) {
                    Text("حذف", color = Color(0xFFB91C1C))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun PdfEditor(
    document: PdfDocumentItem,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var pageIndex by remember(document.id) { mutableIntStateOf(0) }

    var strokes by remember(document.id, pageIndex) {
        mutableStateOf(readStrokes(context, document.id, pageIndex))
    }
    var livePoints by remember(document.id, pageIndex) {
        mutableStateOf<List<InkPoint>>(emptyList())
    }

    val undoStack: SnapshotStateList<List<InkStroke>> =
        remember(document.id, pageIndex) {
            androidx.compose.runtime.mutableStateListOf()
        }
    val redoStack: SnapshotStateList<List<InkStroke>> =
        remember(document.id, pageIndex) {
            androidx.compose.runtime.mutableStateListOf()
        }

    var tool by remember { mutableStateOf(DrawingTool.PEN) }
    var selectedColor by remember { mutableStateOf(InkBlue) }

    val bitmapState = produceState<Bitmap?>(
        initialValue = null,
        document.id,
        pageIndex
    ) {
        value = withContext(Dispatchers.IO) {
            renderPdfPage(context, document.id, pageIndex)
        }
    }
    val bitmap = bitmapState.value

    LaunchedEffect(document.id, pageIndex, strokes) {
        delay(250)
        withContext(Dispatchers.IO) {
            saveStrokes(context, document.id, pageIndex, strokes)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PageBg)
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("‹ رجوع", color = Blue)
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    document.name,
                    color = Navy,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "صفحة ${pageIndex + 1} من ${document.pageCount}",
                    color = Muted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            FilterChip(
                selected = tool == DrawingTool.PEN,
                onClick = { tool = DrawingTool.PEN },
                label = { Text("✎ قلم") }
            )
            FilterChip(
                selected = tool == DrawingTool.ERASER,
                onClick = { tool = DrawingTool.ERASER },
                label = { Text("ممحاة") }
            )

            listOf(InkBlack, InkBlue, InkRed).forEach { color ->
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(
                            if (selectedColor == color) {
                                Modifier.border(
                                    3.dp,
                                    Color(0xFFCBD5E1),
                                    CircleShape
                                )
                            } else Modifier
                        )
                        .clickable {
                            selectedColor = color
                            tool = DrawingTool.PEN
                        }
                )
            }

            OutlinedButton(
                onClick = {
                    if (undoStack.isNotEmpty()) {
                        redoStack.add(strokes)
                        strokes = undoStack.removeAt(undoStack.lastIndex)
                    }
                },
                enabled = undoStack.isNotEmpty()
            ) {
                Text("↶")
            }

            OutlinedButton(
                onClick = {
                    if (redoStack.isNotEmpty()) {
                        undoStack.add(strokes)
                        strokes = redoStack.removeAt(redoStack.lastIndex)
                    }
                },
                enabled = redoStack.isNotEmpty()
            ) {
                Text("↷")
            }
        }

        HorizontalDivider(color = Color(0xFFE5EAF3))

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (bitmap == null) {
                CircularProgressIndicator(color = Blue)
                Spacer(Modifier.height(12.dp))
                Text("جارٍ تحميل الصفحة…", color = Muted)
            } else {
                val aspectRatio = bitmap.width.toFloat() / bitmap.height
                val density = LocalDensity.current
                val strokeWidthPx = with(density) { 4.5.dp.toPx() }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspectRatio)
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE1E6EF))
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "صفحة PDF",
                        modifier = Modifier.matchParentSize()
                    )

                    Canvas(
                        Modifier
                            .matchParentSize()
                            .pointerInput(
                                document.id,
                                pageIndex,
                                tool,
                                selectedColor
                            ) {
                                detectDragGestures(
                                    onDragStart = { point ->
                                        livePoints = listOf(
                                            InkPoint(
                                                point.x / size.width,
                                                point.y / size.height
                                            )
                                        )
                                    },
                                    onDrag = { change, _ ->
                                        val point = InkPoint(
                                            (change.position.x / size.width)
                                                .coerceIn(0f, 1f),
                                            (change.position.y / size.height)
                                                .coerceIn(0f, 1f)
                                        )
                                        livePoints = livePoints + point
                                        change.consume()
                                    },
                                    onDragEnd = {
                                        if (livePoints.isNotEmpty()) {
                                            undoStack.add(strokes)
                                            redoStack.clear()

                                            if (tool == DrawingTool.PEN) {
                                                strokes = strokes + InkStroke(
                                                    points = livePoints,
                                                    color = selectedColor.value
                                                        .toLong().toInt()
                                                )
                                            } else {
                                                val radius = 0.025f
                                                strokes = strokes.filterNot { stroke ->
                                                    stroke.points.any { existing ->
                                                        livePoints.any { erased ->
                                                            hypot(
                                                                existing.x - erased.x,
                                                                existing.y - erased.y
                                                            ) < radius
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        livePoints = emptyList()
                                    },
                                    onDragCancel = {
                                        livePoints = emptyList()
                                    }
                                )
                            }
                    ) {
                        val allStrokes = strokes + if (
                            tool == DrawingTool.PEN && livePoints.isNotEmpty()
                        ) {
                            listOf(
                                InkStroke(
                                    points = livePoints,
                                    color = selectedColor.value.toLong().toInt()
                                )
                            )
                        } else emptyList()

                        allStrokes.forEach { stroke ->
                            val inkColor = Color(stroke.color)
                            val width = stroke.width * density.density

                            if (stroke.points.size == 1) {
                                val p = stroke.points.first()
                                drawCircle(
                                    color = inkColor,
                                    radius = width / 2,
                                    center = Offset(
                                        p.x * size.width,
                                        p.y * size.height
                                    )
                                )
                            }

                            for (i in 1 until stroke.points.size) {
                                val a = stroke.points[i - 1]
                                val b = stroke.points[i]
                                drawLine(
                                    color = inkColor,
                                    start = Offset(
                                        a.x * size.width,
                                        a.y * size.height
                                    ),
                                    end = Offset(
                                        b.x * size.width,
                                        b.y * size.height
                                    ),
                                    strokeWidth = width,
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { if (pageIndex > 0) pageIndex-- },
                enabled = pageIndex > 0
            ) {
                Text("السابق")
            }

            Text(
                "${pageIndex + 1} / ${document.pageCount}",
                color = Navy,
                fontWeight = FontWeight.SemiBold
            )

            Button(
                onClick = {
                    if (pageIndex < document.pageCount - 1) pageIndex++
                },
                enabled = pageIndex < document.pageCount - 1
            ) {
                Text("التالي")
            }
        }
    }
}
