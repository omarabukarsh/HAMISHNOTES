
package com.mynotes.app

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private val Navy = Color(0xFF1E3A8A)
private val Blue = Color(0xFF3B82F6)
private val PageBackground = Color(0xFFF5F6FA)
private val Ink = Color(0xFF172033)
private val Muted = Color(0xFF70798B)

private data class PdfDocumentItem(
    val file: File,
    val name: String
)

private data class InkStroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = PageBackground
                ) {
                    MyNotesApp()
                }
            }
        }
    }
}

@Composable
private fun MyNotesApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var currentDocument by remember {
        mutableStateOf<PdfDocumentItem?>(null)
    }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSection by remember { mutableStateOf("المكتبة") }
    var pageNumber by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(0) }
    var isEraser by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableStateOf(Color(0xFF172033)) }
    var penWidth by remember { mutableStateOf(3f) }

    val strokes = remember { mutableStateListOf<InkStroke>() }
    val currentPoints = remember { mutableStateListOf<Offset>() }

    var refreshKey by remember { mutableIntStateOf(0) }

    val documents = remember(refreshKey) {
        context.filesDir
            .listFiles()
            .orEmpty()
            .filter {
                it.isFile && it.extension.equals("pdf", ignoreCase = true)
            }
            .sortedByDescending { it.lastModified() }
            .map { PdfDocumentItem(it, it.nameWithoutExtension) }
    }

    val filteredDocuments = documents.filter {
        it.name.contains(searchQuery, ignoreCase = true)
    }

    val importPdf = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val copiedFile = withContext(Dispatchers.IO) {
                    try {
                        val input = context.contentResolver.openInputStream(uri)
                            ?: return@withContext null

                        val safeName = "document_${System.currentTimeMillis()}.pdf"
                        val destination = File(context.filesDir, safeName)

                        input.use { source ->
                            FileOutputStream(destination).use { output ->
                                source.copyTo(output)
                            }
                        }

                        destination
                    } catch (_: Exception) {
                        null
                    }
                }

                if (copiedFile != null) {
                    refreshKey++
                    currentDocument = PdfDocumentItem(
                        copiedFile,
                        copiedFile.nameWithoutExtension
                    )
                    pageNumber = 0
                    strokes.clear()
                    currentPoints.clear()
                    totalPages = getPdfPageCount(copiedFile)
                }
            }
        }
    }

    if (currentDocument != null) {
        PdfReaderScreen(
            document = currentDocument!!,
            pageNumber = pageNumber,
            totalPages = totalPages,
            isEraser = isEraser,
            selectedColor = selectedColor,
            penWidth = penWidth,
            strokes = strokes,
            currentPoints = currentPoints,
            onBack = {
                currentDocument = null
                strokes.clear()
                currentPoints.clear()
            },
            onPreviousPage = {
                if (pageNumber > 0) {
                    pageNumber--
                    strokes.clear()
                    currentPoints.clear()
                }
            },
            onNextPage = {
                if (pageNumber < totalPages - 1) {
                    pageNumber++
                    strokes.clear()
                    currentPoints.clear()
                }
            },
            onToggleEraser = { isEraser = !isEraser },
            onSelectColor = { selectedColor = it },
            onPenWidthChange = { penWidth = it },
            onUndo = {
                if (strokes.isNotEmpty()) {
                    strokes.removeAt(strokes.lastIndex)
                } else {
                    currentPoints.clear()
                }
            },
            onClearPage = {
                strokes.clear()
                currentPoints.clear()
            }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PageBackground)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            LibraryHeader(
                onImport = { importPdf.launch(arrayOf("application/pdf")) }
            )

            Spacer(modifier = Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("المكتبة", "الأخيرة", "المفضلة", "المجلدات").forEach { section ->
                    SectionButton(
                        title = section,
                        selected = selectedSection == section,
                        onClick = { selectedSection = section }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                placeholder = { Text("ابحث عن ملف دراسي...") },
                leadingIcon = {
                    Text("⌕", fontSize = 25.sp, color = Muted)
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "مكتبتك الدراسية",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${filteredDocuments.size} ملف",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted
                    )
                }

                OutlinedButton(
                    onClick = {
                        importPdf.launch(arrayOf("application/pdf"))
                    }
                ) {
                    Text("+ ملف جديد")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (filteredDocuments.isEmpty()) {
                EmptyLibrary(
                    hasSearch = searchQuery.isNotBlank(),
                    onImportPdf = {
                        importPdf.launch(arrayOf("application/pdf"))
                    }
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 155.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(
                        items = filteredDocuments,
                        key = { it.file.absolutePath }
                    ) { document ->
                        DocumentCard(
                            document = document,
                            onClick = {
                                currentDocument = document
                                pageNumber = 0
                                strokes.clear()
                                currentPoints.clear()
                                totalPages = getPdfPageCount(document.file)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(onImport: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Navy, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "M",
                color = Color.White,
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "MyNotes",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Navy
            )
            Text(
                text = "مساحة الدراسة الخاصة بك",
                fontSize = 12.sp,
                color = Muted
            )
        }

        Button(onClick = onImport) {
            Text("استيراد PDF")
        }
    }
}

@Composable
private fun SectionButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) Navy else Color.White,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = if (selected) 2.dp else 0.dp
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            color = if (selected) Color.White else Muted,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun EmptyLibrary(
    hasSearch: Boolean,
    onImportPdf: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 45.dp, horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "▤", fontSize = 54.sp, color = Blue)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (hasSearch) "لا توجد نتائج" else "مكتبتك الدراسية بانتظار ملفاتك",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Ink,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (hasSearch) {
                "جرّب تغيير كلمات البحث."
            } else {
                "استورد ملف PDF لتبدأ القراءة والكتابة عليه."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            textAlign = TextAlign.Center
        )

        if (!hasSearch) {
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onImportPdf) {
                Text("استيراد ملف PDF")
            }
        }
    }
}

@Composable
private fun DocumentCard(
    document: PdfDocumentItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.78f)
                    .background(Color(0xFFF0F3FA), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "PDF",
                        fontSize = 29.sp,
                        color = Navy,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    repeat(4) { index ->
                        Box(
                            modifier = Modifier
                                .padding(vertical = 3.dp)
                                .width(if (index == 3) 65.dp else 90.dp)
                                .height(4.dp)
                                .background(Color(0xFFD3DAE8), RoundedCornerShape(4.dp))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = document.name,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "ملف PDF",
                fontSize = 12.sp,
                color = Muted
            )
        }
    }
}

@Composable
private fun PdfReaderScreen(
    document: PdfDocumentItem,
    pageNumber: Int,
    totalPages: Int,
    isEraser: Boolean,
    selectedColor: Color,
    penWidth: Float,
    strokes: MutableList<InkStroke>,
    currentPoints: MutableList<Offset>,
    onBack: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onToggleEraser: () -> Unit,
    onSelectColor: (Color) -> Unit,
    onPenWidthChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onClearPage: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("‹ المكتبة")
            }

            Text(
                text = document.name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Ink,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "${pageNumber + 1}/$totalPages",
                color = Muted,
                fontSize = 12.sp
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onToggleEraser) {
                Text(if (isEraser) "✎ قلم" else "⌫ ممحاة")
            }

            TextButton(onClick = onUndo) {
                Text("تراجع")
            }

            TextButton(onClick = onClearPage) {
                Text("مسح")
            }

            listOf(
                Color(0xFF172033),
                Color(0xFFD62828),
                Color(0xFF2563EB),
                Color(0xFF16803D)
            ).forEach { color ->
                Box(
                    modifier = Modifier
                        .padding(3.dp)
                        .size(23.dp)
                        .background(color, RoundedCornerShape(50))
                        .clickable { onSelectColor(color) }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("سمك القلم", fontSize = 12.sp, color = Muted)
            androidx.compose.material3.Slider(
                value = penWidth,
                onValueChange = onPenWidthChange,
                valueRange = 1f..8f,
                modifier = Modifier.weight(1f)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            PdfPageView(
                file = document.file,
                pageNumber = pageNumber,
                isEraser = isEraser,
                selectedColor = selectedColor,
                penWidth = penWidth,
                strokes = strokes,
                currentPoints = currentPoints
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onPreviousPage,
                enabled = pageNumber > 0
            ) {
                Text("الصفحة السابقة")
            }

            Button(
                onClick = onNextPage,
                enabled = pageNumber < totalPages - 1
            ) {
                Text("الصفحة التالية")
            }
        }
    }
}

@Composable
private fun PdfPageView(
    file: File,
    pageNumber: Int,
    isEraser: Boolean,
    selectedColor: Color,
    penWidth: Float,
    strokes: MutableList<InkStroke>,
    currentPoints: MutableList<Offset>
) {
    val bitmap = remember(file.absolutePath, pageNumber) {
        renderPdfPage(file, pageNumber)
    }

    DisposableEffect(bitmap) {
        onDispose {
            bitmap?.recycle()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE2E6EF), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap == null) {
            Text(
                text = "تعذّر فتح الصفحة. تأكد أن ملف PDF صالح.",
                color = Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(20.dp)
            )
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .background(Color.White)
                    .pointerInput(
                        isEraser,
                        selectedColor,
                        penWidth,
                        pageNumber
                    ) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (isEraser) {
                                    strokes.clear()
                                    currentPoints.clear()
                                } else {
                                    currentPoints.clear()
                                    currentPoints.add(offset)
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                if (!isEraser) {
                                    currentPoints.add(change.position)
                                }
                            },
                            onDragEnd = {
                                if (!isEraser && currentPoints.isNotEmpty()) {
                                    strokes.add(
                                        InkStroke(
                                            points = currentPoints.toList(),
                                            color = selectedColor,
                                            width = penWidth
                                        )
                                    )
                                    currentPoints.clear()
                                }
                            }
                        )
                    }
            ) {
                val image = bitmap.asImageBitmap()

                drawImage(
                    image = image,
                    dstSize = IntSize(
                        width = size.width.toInt(),
                        height = size.height.toInt()
                    )
                )

                strokes.forEach { stroke ->
                    if (stroke.points.size >= 2) {
                        val path = Path().apply {
                            moveTo(
                                stroke.points.first().x,
                                stroke.points.first().y
                            )
                            stroke.points.drop(1).forEach { point ->
                                lineTo(point.x, point.y)
                            }
                        }

                        drawPath(
                            path = path,
                            color = stroke.color,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = stroke.width,
                                cap = StrokeCap.Round
                            )
                        )
                    }
                }

                if (currentPoints.size >= 2) {
                    val path = Path().apply {
                        moveTo(currentPoints.first().x, currentPoints.first().y)
                        currentPoints.drop(1).forEach { point ->
                            lineTo(point.x, point.y)
                        }
                    }

                    drawPath(
                        path = path,
                        color = selectedColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = penWidth,
                            cap = StrokeCap.Round
                        )
                    )
                }
            }
        }
    }
}

private fun getPdfPageCount(file: File): Int {
    return try {
        val descriptor = ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_READ_ONLY
        )

        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                renderer.pageCount.coerceAtLeast(1)
            }
        }
    } catch (_: Exception) {
        1
    }
}

private fun renderPdfPage(file: File, pageNumber: Int): Bitmap? {
    return try {
        val descriptor = ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_READ_ONLY
        )

        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (pageNumber !in 0 until renderer.pageCount) {
                    return null
                }

                renderer.openPage(pageNumber).use { page ->
                    val scale = minOf(
                        2f,
                        1600f / page.width.coerceAtLeast(1)
                    )

                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)

                    val bitmap = Bitmap.createBitmap(
                        width,
                        height,
                        Bitmap.Config.ARGB_8888
                    )

                    bitmap.eraseColor(AndroidColor.WHITE)

                    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

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
