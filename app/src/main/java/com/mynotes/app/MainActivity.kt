
package com.mynotes.app

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.hypot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(248, 249, 252)
        window.navigationBarColor = android.graphics.Color.rgb(248, 249, 252)
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF1E3A8A),
                    secondary = Color(0xFF3B82F6),
                    background = Color(0xFFF8F9FC),
                    surface = Color.White
                )
            ) {
                NoteScreen()
            }
        }
    }
}

enum class Tool { PEN, ERASER }

data class Pt(val x: Float, val y: Float, val p: Float)

data class Ink(
    val pts: List<Pt>,
    val color: Color,
    val width: Float
)

class PdfDoc(private val pfd: ParcelFileDescriptor) {
    private val renderer = PdfRenderer(pfd)
    private var closed = false

    val count: Int = renderer.pageCount

    val ratios: List<Float> = (0 until count).map { i ->
        val page = renderer.openPage(i)
        val ratio = page.width.toFloat() / page.height.toFloat()
        page.close()
        ratio
    }

    @Synchronized
    fun render(index: Int, widthPx: Int): Bitmap? {
        if (closed || index !in 0 until count) return null

        val page = renderer.openPage(index)
        try {
            val heightPx =
                (widthPx * page.height.toFloat() / page.width)
                    .toInt()
                    .coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(
                widthPx,
                heightPx,
                Bitmap.Config.ARGB_8888
            )

            bitmap.eraseColor(android.graphics.Color.WHITE)
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

    @Synchronized
    fun close() {
        if (!closed) {
            closed = true
            renderer.close()
            pfd.close()
        }
    }
}

data class OpenTab(
    val id: String,
    val file: File,
    val doc: PdfDoc,
    var annotations by mutableStateOf<Map<Int, List<Ink>>>(
        emptyMap()
    ),
    val undo = mutableStateListOf<Map<Int, List<Ink>>>(),
    val redo = mutableStateListOf<Map<Int, List<Ink>>>()
)

fun libraryFolder(context: android.content.Context) =
    File(context.filesDir, "pdf_library").apply {
        if (!exists()) mkdirs()
    }

fun annotationFile(context: android.content.Context, id: String) =
    File(
        File(context.filesDir, "annotations"),
        "${id.hashCode()}.json"
    )

fun loadAnnotations(
    context: android.content.Context,
    id: String
): Map<Int, List<Ink>> {
    val file = annotationFile(context, id)
    if (!file.exists()) return emptyMap()

    return try {
        val root = JSONObject(file.readText())
        val result = mutableMapOf<Int, List<Ink>>()
        val keys = root.keys()

        while (keys.hasNext()) {
            val page = keys.next().toInt()
            val strokesJson = root.getJSONArray(page.toString())
            val strokes = mutableListOf<Ink>()

            for (i in 0 until strokesJson.length()) {
                val strokeJson = strokesJson.getJSONObject(i)
                val pointsJson = strokeJson.getJSONArray("pts")
                val points = mutableListOf<Pt>()

                for (j in 0 until pointsJson.length()) {
                    val point = pointsJson.getJSONArray(j)
                    points.add(
                        Pt(
                            point.getDouble(0).toFloat(),
                            point.getDouble(1).toFloat(),
                            point.getDouble(2).toFloat()
                        )
                    )
                }

                strokes.add(
                    Ink(
                        points,
                        Color(strokeJson.getInt("color")),
                        strokeJson.getDouble("width").toFloat()
                    )
                )
            }

            result[page] = strokes
        }

        result
    } catch (_: Exception) {
        emptyMap()
    }
}

fun saveAnnotations(
    context: android.content.Context,
    id: String,
    annotations: Map<Int, List<Ink>>
) {
    try {
        val folder = File(context.filesDir, "annotations")
        if (!folder.exists()) folder.mkdirs()

        val root = JSONObject()

        annotations.forEach { (page, strokes) ->
            val strokesJson = JSONArray()

            strokes.forEach { stroke ->
                val pointsJson = JSONArray()

                stroke.pts.forEach { point ->
                    pointsJson.put(
                        JSONArray().put(point.x)
                            .put(point.y)
                            .put(point.p)
                    )
                }

                strokesJson.put(
                    JSONObject()
                        .put("pts", pointsJson)
                        .put("color", stroke.color.toArgb())
                        .put("width", stroke.width.toDouble())
                )
            }

            root.put(page.toString(), strokesJson)
        }

        val file = annotationFile(context, id)
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(root.toString())
        if (!temp.renameTo(file)) {
            file.writeText(root.toString())
            temp.delete()
        }
    } catch (_: Exception) {
        // Keep the editor usable if storage fails.
    }
}

fun eraseAt(list: List<Ink>, at: Offset): List<Ink> =
    list.filterNot { stroke ->
        stroke.pts.any {
            hypot(it.x - at.x, it.y - at.y) < 0.025f
        }
    }

@Composable
fun PageView(
    index: Int,
    doc: PdfDoc,
    ratio: Float,
    strokes: List<Ink>,
    tool: Tool,
    color: Color,
    onBegin: () -> Unit,
    onErase: (Offset) -> Unit,
    onFinish: (Ink) -> Unit
) {
    val bitmapState = produceState<Bitmap?>(
        initialValue = null,
        doc,
        index
    ) {
        value = withContext(Dispatchers.IO) {
            try {
                doc.render(index, 1400)
            } catch (_: Exception) {
                null
            }
        }
    }

    var live by remember { mutableStateOf<Ink?>(null) }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .background(Color.White)
    ) {
        bitmapState.value?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "PDF page ${index + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(tool, color, doc, index) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(
                                requireUnconsumed = false
                            )

                            val pointerType = down.type
                            val isPen =
                                pointerType == PointerType.Stylus ||
                                pointerType == PointerType.Eraser ||
                                pointerType == PointerType.Mouse

                            // Finger gestures remain available for scrolling.
                            if (!isPen) continue

                            val canvasWidth = size.width.toFloat()
                                .coerceAtLeast(1f)

                            val erasing =
                                tool == Tool.ERASER ||
                                pointerType == PointerType.Eraser

                            onBegin()

                            var points = listOf(
                                Pt(
                                    down.position.x / canvasWidth,
                                    down.position.y / canvasWidth,
                                    down.pressure.coerceIn(0.1f, 1f)
                                )
                            )

                            if (erasing) {
                                onErase(
                                    Offset(
                                        down.position.x / canvasWidth,
                                        down.position.y / canvasWidth
                                    )
                                )
                            } else {
                                live = Ink(points, color, 0.003f)
                            }

                            down.consume()

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull {
                                    it.id == down.id
                                } ?: break

                                if (!change.pressed) break

                                val position = Offset(
                                    change.position.x / canvasWidth,
                                    change.position.y / canvasWidth
                                )

                                if (erasing) {
                                    onErase(position)
                                } else {
                                    points = points + Pt(
                                        position.x,
                                        position.y,
                                        change.pressure.coerceIn(0.1f, 1f)
                                    )
                                    live = Ink(points, color, 0.003f)
                                }

                                change.consume()
                            }

                            if (!erasing) {
                                live?.let(onFinish)
                            }
                            live = null
                        }
                    }
                }
        ) {
            val canvasWidth = size.width

            (strokes + listOfNotNull(live)).forEach { stroke ->
                if (stroke.pts.size == 1) {
                    val point = stroke.pts.first()
                    drawCircle(
                        stroke.color,
                        stroke.width * canvasWidth / 2,
                        Offset(
                            point.x * canvasWidth,
                            point.y * canvasWidth
                        )
                    )
                }

                for (j in 1 until stroke.pts.size) {
                    val a = stroke.pts[j - 1]
                    val b = stroke.pts[j]

                    drawLine(
                        color = stroke.color,
                        start = Offset(
                            a.x * canvasWidth,
                            a.y * canvasWidth
                        ),
                        end = Offset(
                            b.x * canvasWidth,
                            b.y * canvasWidth
                        ),
                        strokeWidth = stroke.width * canvasWidth *
                            (0.4f + b.p),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
fun NoteScreen() {
    val context = LocalContext.current

    val library = remember {
        mutableStateListOf<File>().apply {
            addAll(
                libraryFolder(context).listFiles()
                    ?.filter {
                        it.isFile &&
                        it.extension.equals("pdf", true)
                    }
                    ?.sortedByDescending { it.lastModified() }
                    ?: emptyList()
            )
        }
    }

    val tabs = remember { mutableStateListOf<OpenTab>() }
    var activeId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var tool by remember { mutableStateOf(Tool.PEN) }
    var color by remember { mutableStateOf(Color.Black) }

    val activeTab = tabs.firstOrNull { it.id == activeId }

    fun openPdf(file: File) {
        val existing = tabs.firstOrNull { it.id == file.name }
        if (existing != null) {
            activeId = existing.id
            return
        }

        try {
            val pfd = ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_READ_ONLY
            )
            val doc = PdfDoc(pfd)

            if (doc.count <= 0) {
                doc.close()
                error = "ملف PDF لا يحتوي على صفحات."
                return
            }

            val tab = OpenTab(
                id = file.name,
                file = file,
                doc = doc
            )
            tab.annotations = loadAnnotations(context, tab.id)

            tabs.add(tab)
            activeId = tab.id
            error = null
        } catch (e: Exception) {
            error = "تعذر فتح الملف: ${e.message}"
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val name = context.contentResolver.query(
                    uri,
                    null,
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val column = cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                    )
                    if (cursor.moveToFirst() && column >= 0) {
                        cursor.getString(column)
                    } else null
                } ?: "Document.pdf"

                if (!name.endsWith(".pdf", true)) {
                    error = "هذه المرحلة تدعم استيراد ملفات PDF فقط."
                    return@rememberLauncherForActivityResult
                }

                val safeName = name.replace(
                    Regex("[^A-Za-z0-9._ -]"),
                    "_"
                )

                val folder = libraryFolder(context)
                var destination = File(folder, safeName)

                if (destination.exists()) {
                    destination = File(
                        folder,
                        "${System.currentTimeMillis()}_$safeName"
                    )
                }

                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) {
                        "تعذر قراءة الملف المحدد."
                    }
                    destination.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                library.clear()
                library.addAll(
                    folder.listFiles()
                        ?.filter {
                            it.isFile &&
                            it.extension.equals("pdf", true)
                        }
                        ?.sortedByDescending { it.lastModified() }
                        ?: emptyList()
                )

                openPdf(destination)
            } catch (e: Exception) {
                error = "تعذر استيراد الملف: ${e.message}"
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            tabs.forEach { tab ->
                saveAnnotations(
                    context,
                    tab.id,
                    tab.annotations
                )
                tab.doc.close()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FC))
            .statusBarsPadding()
    ) {
        // Library header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E3A8A)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "M",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    "MyNotes",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFF172554)
                )
                Text(
                    "مكتبتك الدراسية",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B)
                )
            }

            Button(
                onClick = {
                    picker.launch(arrayOf("application/pdf"))
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("+  استيراد PDF")
            }
        }

        // Multiple open tabs
        if (tabs.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE9EDF5))
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tabs.toList().forEach { tab ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (tab.id == activeId) {
                            Color.White
                        } else {
                            Color(0xFFE9EDF5)
                        }
                    ) {
                        Row(
                            Modifier.padding(
                                start = 12.dp,
                                end = 6.dp,
                                top = 8.dp,
                                bottom = 8.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                tab.file.nameWithoutExtension,
                                modifier = Modifier.clickable {
                                    activeId = tab.id
                                },
                                maxLines = 1,
                                style = MaterialTheme.typography.labelMedium
                            )

                            Spacer(Modifier.width(8.dp))

                            Text(
                                "×",
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        saveAnnotations(
                                            context,
                                            tab.id,
                                            tab.annotations
                                        )
                                        tab.doc.close()
                                        tabs.remove(tab)

                                        if (activeId == tab.id) {
                                            activeId =
                                                tabs.lastOrNull()?.id
                                        }
                                    }
                                    .padding(horizontal = 5.dp),
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            }
        }

        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(12.dp)
            )
        }

        if (activeTab != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = { activeId = null }
                ) {
                    Text("المكتبة")
                }

                Button(
                    onClick = { tool = Tool.PEN },
                    enabled = tool != Tool.PEN
                ) {
                    Text("القلم")
                }

                Button(
                    onClick = { tool = Tool.ERASER },
                    enabled = tool != Tool.ERASER
                ) {
                    Text("الممحاة")
                }

                listOf(
                    Color.Black,
                    Color(0xFF1565C0),
                    Color(0xFFC62828),
                    Color(0xFF2E7D32)
                ).forEach { selectedColor ->
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(selectedColor)
                            .clickable {
                                color = selectedColor
                                tool = Tool.PEN
                            }
                    )
                }

                Spacer(Modifier.weight(1f))

                OutlinedButton(
                    onClick = {
                        if (activeTab.undo.isNotEmpty()) {
                            activeTab.redo.add(
                                activeTab.annotations.toMap()
                            )
                            activeTab.annotations =
                                activeTab.undo.removeAt(
                                    activeTab.undo.lastIndex
                                )
                            saveAnnotations(
                                context,
                                activeTab.id,
                                activeTab.annotations
                            )
                        }
                    },
                    enabled = activeTab.undo.isNotEmpty()
                ) {
                    Text("تراجع")
                }

                OutlinedButton(
                    onClick = {
                        if (activeTab.redo.isNotEmpty()) {
                            activeTab.undo.add(
                                activeTab.annotations.toMap()
                            )
                            activeTab.annotations =
                                activeTab.redo.removeAt(
                                    activeTab.redo.lastIndex
                                )
                            saveAnnotations(
                                context,
                                activeTab.id,
                                activeTab.annotations
                            )
                        }
                    },
                    enabled = activeTab.redo.isNotEmpty()
                ) {
                    Text("إعادة")
                }
            }

            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFFE4E7EC)),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(
                    horizontal = 16.dp,
                    vertical = 12.dp
                )
            ) {
                items(activeTab.doc.count) { pageIndex ->
                    PageView(
                        index = pageIndex,
                        doc = activeTab.doc,
                        ratio = activeTab.doc.ratios[pageIndex],
                        strokes = activeTab.annotations[pageIndex]
                            ?: emptyList(),
                        tool = tool,
                        color = color,
                        onBegin = {
                            activeTab.undo.add(
                                activeTab.annotations.toMap()
                            )
                            activeTab.redo.clear()
                        },
                        onErase = { at ->
                            val updated = eraseAt(
                                activeTab.annotations[pageIndex]
                                    ?: emptyList(),
                                at
                            )
                            activeTab.annotations =
                                activeTab.annotations +
                                (pageIndex to updated)

                            saveAnnotations(
                                context,
                                activeTab.id,
                                activeTab.annotations
                            )
                        },
                        onFinish = { ink ->
                            activeTab.annotations =
                                activeTab.annotations +
                                (
                                    pageIndex to (
                                        (activeTab.annotations[pageIndex]
                                            ?: emptyList()) + ink
                                    )
                                )

                            saveAnnotations(
                                context,
                                activeTab.id,
                                activeTab.annotations
                            )
                        }
                    )
                }
            }
        } else {
            // Tablet library layout
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .width(165.dp)
                        .fillMaxHeight()
                        .background(Color.White)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        "مساحة العمل",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B)
                    )
                    Text(
                        "▦  المكتبة",
                        color = Color(0xFF1E3A8A),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text("◷  الأخيرة", color = Color(0xFF475569))
                    Text("☆  المفضلة", color = Color(0xFF475569))
                    Text("▱  المجلدات", color = Color(0xFF475569))
                }

                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(20.dp)
                ) {
                    Text(
                        "ملفاتي",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color(0xFF172554)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${library.size} ملف",
                        color = Color(0xFF64748B),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(18.dp))

                    if (library.isEmpty()) {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "مكتبتك فارغة",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "استورد ملفات PDF لتبدأ الكتابة عليها",
                                    color = Color(0xFF64748B)
                                )
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        picker.launch(
                                            arrayOf("application/pdf")
                                        )
                                    }
                                ) {
                                    Text("استيراد أول ملف")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(library, key = { it.name }) { file ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { openPdf(file) },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color.White
                                    )
                                ) {
                                    Row(
                                        Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier
                                                .size(62.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFFE8EEF9)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "PDF",
                                                color = Color(0xFF1E3A8A),
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        }

                                        Spacer(Modifier.width(16.dp))

                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                file.nameWithoutExtension,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = Color(0xFF172554)
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                "${file.length() / 1024} KB",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF64748B)
                                            )
                                        }

                                        Text(
                                            "فتح  ›",
                                            color = Color(0xFF3B82F6)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
