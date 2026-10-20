
package com.mynotes.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

private val Navy = Color(0xFF243B64)
private val Blue = Color(0xFF4169A1)
private val Background = Color(0xFFF6F7FA)
private val SidebarBackground = Color(0xFFFFFFFF)
private val MainText = Color(0xFF242B36)
private val SecondaryText = Color(0xFF7C8492)
private val BorderColor = Color(0xFFE7EAF0)
private val SelectedBackground = Color(0xFFEDF2FA)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Background
                ) {
                    MyNotesApp()
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

private enum class LibrarySection {
    LIBRARY,
    RECENT,
    FAVORITES,
    FOLDERS
}

private enum class PenTool {
    PEN,
    ERASER
}

private class PdfDocument(file: File) {
    private val descriptor =
        ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_READ_ONLY
        )

    private val renderer = PdfRenderer(descriptor)

    val pageCount: Int
        get() = renderer.pageCount

    @Synchronized
    fun renderPage(index: Int, targetWidth: Int): Bitmap {
        val page = renderer.openPage(index)
        try {
            val width = targetWidth.coerceAtLeast(400)
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

private class DocumentTab(
    val file: File,
    val document: PdfDocument,
    initialAnnotations: Map<Int, List<InkStroke>>
) {
    val id: String = file.absolutePath

    var annotations by mutableStateOf(initialAnnotations)

    val undo = mutableStateListOf<Map<Int, List<InkStroke>>>()
    val redo = mutableStateListOf<Map<Int, List<InkStroke>>>()
}

@Composable
private fun MyNotesApp() {
    val context = LocalContext.current

    val libraryDirectory = remember {
        File(context.filesDir, "pdf_library").apply {
            mkdirs()
        }
    }

    val documents = remember {
        mutableStateListOf<File>()
    }

    val tabs = remember {
        mutableStateListOf<DocumentTab>()
    }

    val preferences = remember {
        context.getSharedPreferences(
            "mynotes_preferences",
            Context.MODE_PRIVATE
        )
    }

    var activeTabId by remember { mutableStateOf<String?>(null) }
    var activeSection by remember {
        mutableStateOf(LibrarySection.LIBRARY)
    }

    var query by remember { mutableStateOf("") }
    var sortByName by remember { mutableStateOf(false) }
    var favorites by remember {
        mutableStateOf(
            preferences.getStringSet("favorites", emptySet())
                ?.toSet() ?: emptySet()
        )
    }

    fun refreshDocuments() {
        val found = libraryDirectory.listFiles()
            ?.filter {
                it.isFile &&
                    it.extension.equals("pdf", ignoreCase = true)
            }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

        documents.clear()
        documents.addAll(found)
    }

    fun rememberRecent(file: File) {
        preferences.edit()
            .putLong("recent_${file.absolutePath}", System.currentTimeMillis())
            .apply()
    }

    fun openDocument(file: File) {
        val existing = tabs.firstOrNull {
            it.id == file.absolutePath
        }

        if (existing != null) {
            activeTabId = existing.id
            activeSection = LibrarySection.LIBRARY
            rememberRecent(file)
            return
        }

        try {
            val document = PdfDocument(file)

            val tab = DocumentTab(
                file = file,
                document = document,
                initialAnnotations = loadAnnotations(context, file)
            )

            tabs.add(tab)
            activeTabId = tab.id
            activeSection = LibrarySection.LIBRARY
            rememberRecent(file)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "تعذر فتح الملف: ${e.localizedMessage ?: "خطأ غير معروف"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun importDocument(uri: Uri) {
        try {
            var name = "Document.pdf"

            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                    )
                    if (index >= 0) {
                        name = cursor.getString(index) ?: name
                    }
                }
            }

            name = name
                .replace(Regex("""[\\/:*?"<>|]"""), "_")
                .ifBlank { "Document.pdf" }

            if (!name.endsWith(".pdf", true)) {
                name += ".pdf"
            }

            var destination = File(libraryDirectory, name)

            if (destination.exists()) {
                val base = name.removeSuffix(".pdf")
                destination = File(
                    libraryDirectory,
                    "${base}_${System.currentTimeMillis()}.pdf"
                )
            }

            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("تعذرت قراءة الملف")

            input.use { source ->
                FileOutputStream(destination).use { output ->
                    source.copyTo(output)
                }
            }

            refreshDocuments()
            openDocument(destination)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "فشل استيراد الملف: ${e.localizedMessage ?: "خطأ"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importDocument(uri)
    }

    fun closeTab(tab: DocumentTab) {
        saveAnnotations(context, tab.file, tab.annotations)

        try {
            tab.document.close()
        } catch (_: Exception) {
        }

        tabs.remove(tab)

        if (activeTabId == tab.id) {
            activeTabId = tabs.lastOrNull()?.id
        }
    }

    fun toggleFavorite(file: File) {
        favorites = if (file.absolutePath in favorites) {
            favorites - file.absolutePath
        } else {
            favorites + file.absolutePath
        }

        preferences.edit()
            .putStringSet("favorites", favorites)
            .apply()
    }

    DisposableEffect(Unit) {
        onDispose {
            tabs.forEach { tab ->
                saveAnnotations(context, tab.file, tab.annotations)
                try {
                    tab.document.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshDocuments()
    }

    val activeTab = tabs.firstOrNull { it.id == activeTabId }

    if (activeTab != null) {
        PdfWorkspace(
            context = context,
            tab = activeTab,
            tabs = tabs,
            onSwitchTab = { activeTabId = it.id },
            onCloseTab = ::closeTab,
            onBack = {
                activeTabId = null
                activeSection = LibrarySection.LIBRARY
            }
        )
    } else {
        LibraryLayout(
            documents = documents,
            activeSection = activeSection,
            query = query,
            favorites = favorites,
            preferences = preferences,
            sortByName = sortByName,
            onSectionChange = { activeSection = it },
            onQueryChange = { query = it },
            onSortChange = { sortByName = it },
            onImport = {
                importPicker.launch(arrayOf("application/pdf"))
            },
            onOpen = ::openDocument,
            onFavorite = ::toggleFavorite,
            onRefresh = ::refreshDocuments,
            onDelete = { file ->
                val annotation = annotationFile(context, file)
                val removed = file.delete()
                if (removed) {
                    annotation.delete()
                    favorites = favorites - file.absolutePath
                    preferences.edit()
                        .putStringSet("favorites", favorites)
                        .apply()
                    refreshDocuments()
                } else {
                    Toast.makeText(
                        context,
                        "تعذر حذف الملف",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }
}

@Composable
private fun LibraryLayout(
    documents: List<File>,
    activeSection: LibrarySection,
    query: String,
    favorites: Set<String>,
    preferences: android.content.SharedPreferences,
    sortByName: Boolean,
    onSectionChange: (LibrarySection) -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (Boolean) -> Unit,
    onImport: () -> Unit,
    onOpen: (File) -> Unit,
    onFavorite: (File) -> Unit,
    onRefresh: () -> Unit,
    onDelete: (File) -> Unit
) {
    val configuration = LocalConfiguration.current
    val wideScreen = configuration.screenWidthDp >= 700

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        if (wideScreen) {
            LibrarySidebar(
                activeSection = activeSection,
                onSectionChange = onSectionChange,
                onImport = onImport
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            LibraryTopBar(
                query = query,
                onQueryChange = onQueryChange,
                onImport = onImport,
                wideScreen = wideScreen
            )

            if (!wideScreen) {
                CompactNavigation(
                    activeSection = activeSection,
                    onSectionChange = onSectionChange
                )
            }

            val filteredDocuments = remember(
                documents,
                activeSection,
                query,
                favorites,
                sortByName
            ) {
                var result = documents.toList()

                result = when (activeSection) {
                    LibrarySection.FAVORITES ->
                        result.filter { it.absolutePath in favorites }

                    LibrarySection.RECENT ->
                        result.sortedByDescending {
                            preferences.getLong(
                                "recent_${it.absolutePath}",
                                0L
                            )
                        }

                    else -> result
                }

                if (query.isNotBlank()) {
                    result = result.filter {
                        it.name.contains(query, ignoreCase = true)
                    }
                }

                if (sortByName) {
                    result = result.sortedBy {
                        it.name.lowercase()
                    }
                }

                result
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = if (wideScreen) 28.dp else 16.dp
                    )
            ) {
                Spacer(Modifier.height(22.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (activeSection) {
                                LibrarySection.LIBRARY -> "مكتبتك الدراسية"
                                LibrarySection.RECENT -> "المستندات الأخيرة"
                                LibrarySection.FAVORITES -> "المفضلة"
                                LibrarySection.FOLDERS -> "مجلداتك"
                            },
                            color = MainText,
                            fontSize = if (wideScreen) 28.sp else 24.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(5.dp))

                        Text(
                            text = when (activeSection) {
                                LibrarySection.LIBRARY ->
                                    "كل ملفاتك الدراسية في مكان واحد"
                                LibrarySection.RECENT ->
                                    "تابع القراءة من حيث توقفت"
                                LibrarySection.FAVORITES ->
                                    "ملفاتك التي وضعتها في المفضلة"
                                LibrarySection.FOLDERS ->
                                    "تصفح مستنداتك الدراسية"
                            },
                            color = SecondaryText,
                            fontSize = 13.sp
                        )
                    }

                    TextButton(
                        onClick = { onSortChange(!sortByName) }
                    ) {
                        Text(
                            if (sortByName) "الترتيب: الاسم" else "الترتيب: الأحدث",
                            color = Blue
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                if (activeSection == LibrarySection.LIBRARY &&
                    query.isBlank() &&
                    documents.isNotEmpty()
                ) {
                    Text(
                        text = "الأخيرة",
                        color = MainText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        documents
                            .sortedByDescending { it.lastModified() }
                            .take(3)
                            .forEach { file ->
                                RecentDocumentCard(
                                    file = file,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onOpen(file) }
                                )
                            }
                    }

                    Spacer(Modifier.height(26.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (activeSection) {
                            LibrarySection.LIBRARY -> "كل المستندات"
                            LibrarySection.RECENT -> "آخر ما فتحته"
                            LibrarySection.FAVORITES -> "الملفات المفضلة"
                            LibrarySection.FOLDERS -> "المستندات"
                        },
                        color = MainText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "${filteredDocuments.size} ملف",
                        color = SecondaryText,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (filteredDocuments.isEmpty()) {
                    EmptyLibrary(
                        query = query,
                        section = activeSection,
                        onImport = onImport
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(
                            minSize = if (wideScreen) 190.dp else 145.dp
                        ),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(
                            items = filteredDocuments,
                            key = { it.absolutePath }
                        ) { file ->
                            DocumentCard(
                                file = file,
                                isFavorite = file.absolutePath in favorites,
                                onClick = { onOpen(file) },
                                onFavorite = { onFavorite(file) },
                                onDelete = { onDelete(file) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySidebar(
    activeSection: LibrarySection,
    onSectionChange: (LibrarySection) -> Unit,
    onImport: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(230.dp)
            .fillMaxHeight()
            .background(SidebarBackground)
            .border(
                width = 1.dp,
                color = BorderColor
            )
            .padding(horizontal = 18.dp, vertical = 22.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        Navy,
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "M",
                    color = Color.White,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(10.dp))

            Column {
                Text(
                    "MyNotes",
                    color = Navy,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "STUDY LIBRARY",
                    color = SecondaryText,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onImport,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Navy
            )
        ) {
            Text(
                "+   ملف جديد",
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(30.dp))

        Text(
            "المكتبة",
            color = SecondaryText,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp, bottom = 10.dp)
        )

        SidebarItem(
            title = "المكتبة",
            symbol = "▦",
            selected = activeSection == LibrarySection.LIBRARY,
            onClick = { onSectionChange(LibrarySection.LIBRARY) }
        )

        SidebarItem(
            title = "الأخيرة",
            symbol = "◷",
            selected = activeSection == LibrarySection.RECENT,
            onClick = { onSectionChange(LibrarySection.RECENT) }
        )

        SidebarItem(
            title = "المفضلة",
            symbol = "☆",
            selected = activeSection == LibrarySection.FAVORITES,
            onClick = { onSectionChange(LibrarySection.FAVORITES) }
        )

        SidebarItem(
            title = "المجلدات",
            symbol = "▤",
            selected = activeSection == LibrarySection.FOLDERS,
            onClick = { onSectionChange(LibrarySection.FOLDERS) }
        )

        Spacer(Modifier.weight(1f))

        HorizontalDivider(color = BorderColor)

        Spacer(Modifier.height(12.dp))

        Text(
            "MyNotes  •  Study space",
            color = SecondaryText,
            fontSize = 10.sp,
            modifier = Modifier.padding(6.dp)
        )
    }
}

@Composable
private fun SidebarItem(
    title: String,
    symbol: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(
                if (selected) SelectedBackground else Color.Transparent,
                RoundedCornerShape(9.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = symbol,
            color = if (selected) Navy else SecondaryText,
            fontSize = 19.sp,
            modifier = Modifier.width(30.dp)
        )

        Text(
            text = title,
            color = if (selected) Navy else MainText,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun CompactNavigation(
    activeSection: LibrarySection,
    onSectionChange: (LibrarySection) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        listOf(
            LibrarySection.LIBRARY to "المكتبة",
            LibrarySection.RECENT to "الأخيرة",
            LibrarySection.FAVORITES to "المفضلة",
            LibrarySection.FOLDERS to "المجلدات"
        ).forEach { (section, label) ->
            TextButton(
                onClick = { onSectionChange(section) }
            ) {
                Text(
                    label,
                    color = if (section == activeSection) Navy else SecondaryText,
                    fontSize = 12.sp,
                    fontWeight = if (section == activeSection) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Normal
                    }
                )
            }
        }
    }
}

@Composable
private fun LibraryTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onImport: () -> Unit,
    wideScreen: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .border(
                width = 1.dp,
                color = BorderColor
            )
            .padding(
                horizontal = if (wideScreen) 28.dp else 16.dp,
                vertical = 13.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "مساحة الدراسة",
                color = MainText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )

            Text(
                "نظّم مستنداتك وأفكارك",
                color = SecondaryText,
                fontSize = 11.sp
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.width(
                if (wideScreen) 280.dp else 145.dp
            ),
            singleLine = true,
            placeholder = {
                Text(
                    "ابحث في الملفات",
                    fontSize = 12.sp
                )
            },
            leadingIcon = {
                Text(
                    "⌕",
                    fontSize = 23.sp,
                    color = SecondaryText
                )
            },
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = BorderColor,
                focusedBorderColor = Blue
            )
        )

        if (wideScreen) {
            Spacer(Modifier.width(12.dp))

            OutlinedButton(
                onClick = onImport,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("+  استيراد")
            }
        }
    }
}

@Composable
private fun RecentDocumentCard(
    file: File,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = Color.White,
        shape = RoundedCornerShape(11.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            BorderColor
        )
    ) {
        Row(
            modifier = Modifier.padding(11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PdfSymbol()
            Spacer(Modifier.width(9.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.nameWithoutExtension,
                    color = MainText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )

                Text(
                    "PDF  ·  ${formatSize(file.length())}",
                    color = SecondaryText,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun DocumentCard(
    file: File,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف المستند؟") },
            text = {
                Text("سيتم حذف ${file.name} من مكتبة التطبيق.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    }
                ) {
                    Text(
                        "حذف",
                        color = Color(0xFFB42318)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDelete = false }
                ) {
                    Text("إلغاء")
                }
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            BorderColor
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(125.dp)
                    .background(Color(0xFFF0F3F8))
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .height(82.dp)
                            .background(
                                Color.White,
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                1.dp,
                                Color(0xFFD9DFE9),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(10.dp)
                    ) {
                        Column {
                            Box(
                                Modifier
                                    .fillMaxWidth(0.7f)
                                    .height(4.dp)
                                    .background(Blue)
                            )

                            Spacer(Modifier.height(9.dp))

                            repeat(5) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(
                                            if (it % 2 == 0) 1f else 0.75f
                                        )
                                        .height(2.dp)
                                        .background(Color(0xFFD8DEE8))
                                )
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Text(
                        "PDF DOCUMENT",
                        fontSize = 8.sp,
                        color = SecondaryText,
                        letterSpacing = 1.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    TextButton(
                        onClick = { menuExpanded = true },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text(
                            "⋮",
                            fontSize = 22.sp,
                            color = MainText
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (isFavorite) {
                                        "إزالة من المفضلة"
                                    } else {
                                        "إضافة إلى المفضلة"
                                    }
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onFavorite()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("حذف المستند") },
                            onClick = {
                                menuExpanded = false
                                confirmDelete = true
                            }
                        )
                    }
                }

                if (isFavorite) {
                    Text(
                        "★",
                        color = Color(0xFFB17A20),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                        fontSize = 17.sp
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 11.dp)
            ) {
                Text(
                    file.nameWithoutExtension,
                    color = MainText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 2,
                    minLines = 2
                )

                Spacer(Modifier.height(5.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "PDF",
                        color = Blue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.width(7.dp))

                    Text(
                        formatSize(file.length()),
                        color = SecondaryText,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfSymbol() {
    Box(
        modifier = Modifier
            .size(38.dp)
            .background(
                Color(0xFFE9EEF8),
                RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "PDF",
            color = Navy,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}


@Composable
private fun EmptyLibrary(
    onImportPdf: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📚",
            fontSize = 48.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "مكتبتك الدراسية بانتظار ملفاتك",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "استورد ملف PDF لتبدأ القراءة والكتابة عليه.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onImportPdf
        ) {
            Text("استيراد ملف PDF")
        }
    }
}

@Composable
private fun PdfWorkspace(
    context: Context,
    tab: DocumentTab,
    tabs: List<DocumentTab>,
    onSwitchTab: (DocumentTab) -> Unit,
    onCloseTab: (DocumentTab) -> Unit,
    onBack: () -> Unit
) {
    var tool by remember { mutableStateOf(PenTool.PEN) }
    var penColor by remember { mutableStateOf(Color(0xFF172554)) }
    var pageJump by remember { mutableStateOf("1") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("← المكتبة", color = Navy)
            }

            Spacer(Modifier.width(8.dp))

            Text(
                tab.file.name,
                color = MainText,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
        }

        if (tabs.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFEDF0F5))
                    .padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                tabs.forEach { item ->
                    Surface(
                        color = if (item.id == tab.id) {
                            Color.White
                        } else {
                            Color.Transparent
                        },
                        shape = RoundedCornerShape(7.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { onSwitchTab(item) }
                            ) {
                                Text(
                                    item.file.name,
                                    color = Navy,
                                    maxLines = 1
                                )
                            }

                            TextButton(
                                onClick = { onCloseTab(item) }
                            ) {
                                Text("×", color = SecondaryText)
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    tool = PenTool.PEN
                }
            ) {
                Text(
                    "القلم",
                    color = if (tool == PenTool.PEN) Navy else SecondaryText
                )
            }

            TextButton(
                onClick = {
                    tool = PenTool.ERASER
                }
            ) {
                Text(
                    "الممحاة",
                    color = if (tool == PenTool.ERASER) {
                        Color(0xFFB42318)
                    } else {
                        SecondaryText
                    }
                )
            }

            listOf(
                Color(0xFF172554),
                Color(0xFF2563EB),
                Color(0xFFDC2626),
                Color(0xFF15803D)
            ).forEach { color ->
                Surface(
                    onClick = {
                        penColor = color
                        tool = PenTool.PEN
                    },
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(21.dp),
                    shape = RoundedCornerShape(50),
                    color = color,
                    border = if (color == penColor) {
                        androidx.compose.foundation.BorderStroke(
                            2.dp,
                            Color.Black
                        )
                    } else null
                ) {}
            }

            Spacer(Modifier.weight(1f))

            Text(
                "${tab.document.pageCount} صفحة",
                color = SecondaryText,
                fontSize = 11.sp
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = 12.dp,
                vertical = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(tab.document.pageCount) { index ->
                AnnotatedPdfPage(
                    context = context,
                    tab = tab,
                    pageIndex = index,
                    tool = tool,
                    penColor = penColor
                )
            }
        }
    }
}

@Composable
private fun AnnotatedPdfPage(
    context: Context,
    tab: DocumentTab,
    pageIndex: Int,
    tool: PenTool,
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
            tab.document.renderPage(pageIndex, 1100)
        }
    }

    val currentBitmap = bitmap
    val ratio = currentBitmap?.let {
        it.width.toFloat() / it.height.toFloat()
    } ?: (595f / 842f)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .background(Color.White)
                .border(1.dp, BorderColor)
        ) {
            if (currentBitmap != null) {
                Image(
                    bitmap = currentBitmap.asImageBitmap(),
                    contentDescription = "صفحة PDF ${pageIndex + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Navy)
                }
            }

            Canvas(Modifier.fillMaxSize()) {
                val strokes = tab.annotations[pageIndex] ?: emptyList()

                fun drawStroke(stroke: InkStroke) {
                    if (stroke.points.isEmpty()) return

                    if (stroke.points.size == 1) {
                        val point = stroke.points.first()
                        drawCircle(
                            color = Color(stroke.color),
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

                        if (index == 0) path.moveTo(x, y)
                        else path.lineTo(x, y)
                    }

                    drawPath(
                        path = path,
                        color = Color(stroke.color),
                        style = Stroke(
                            width = stroke.width,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                strokes.forEach(::drawStroke)

                if (livePoints.isNotEmpty()) {
                    drawStroke(
                        InkStroke(
                            points = livePoints,
                            color = if (tool == PenTool.ERASER) {
                                AndroidColor.WHITE
                            } else {
                                penColor.toArgbCompat()
                            },
                            width = if (tool == PenTool.ERASER) 24f else 3.5f
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(tab.id, pageIndex, tool, penColor) {
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                requireUnconsumed = false
                            )

                            if (
                                down.type != PointerType.Stylus &&
                                down.type != PointerType.Mouse
                            ) {
                                return@awaitEachGesture
                            }

                            fun normalize(position: Offset): InkPoint {
                                return InkPoint(
                                    x = (position.x / size.width)
                                        .coerceIn(0f, 1f),
                                    y = (position.y / size.height)
                                        .coerceIn(0f, 1f)
                                )
                            }

                            val points = mutableListOf(
                                normalize(down.position)
                            )

                            livePoints = points.toList()
                            down.consume()

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull {
                                    it.id == down.id
                                } ?: break

                                points.add(normalize(change.position))
                                livePoints = points.toList()
                                change.consume()

                                if (!change.pressed) break
                            }

                            livePoints = emptyList()

                            if (points.isEmpty()) {
                                return@awaitEachGesture
                            }

                            val oldStrokes =
                                tab.annotations[pageIndex] ?: emptyList()

                            if (tool == PenTool.PEN) {
                                tab.undo.add(tab.annotations.toMap())
                                tab.redo.clear()

                                val newStroke = InkStroke(
                                    points = points.toList(),
                                    color = penColor.toArgbCompat()
                                )

                                tab.annotations = tab.annotations + (
                                    pageIndex to (oldStrokes + newStroke)
                                )
                            } else {
                                val radius = 0.025f

                                val remaining = oldStrokes.filterNot { stroke ->
                                    stroke.points.any { a ->
                                        points.any { b ->
                                            val dx = a.x - b.x
                                            val dy = a.y - b.y
                                            dx * dx + dy * dy < radius * radius
                                        }
                                    }
                                }

                                if (remaining.size != oldStrokes.size) {
                                    tab.undo.add(tab.annotations.toMap())
                                    tab.redo.clear()

                                    tab.annotations = if (remaining.isEmpty()) {
                                        tab.annotations - pageIndex
                                    } else {
                                        tab.annotations + (pageIndex to remaining)
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
            "صفحة ${pageIndex + 1}",
            color = SecondaryText,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

private fun Color.toArgbCompat(): Int {
    return AndroidColor.argb(
        (alpha * 255).toInt().coerceIn(0, 255),
        (red * 255).toInt().coerceIn(0, 255),
        (green * 255).toInt().coerceIn(0, 255),
        (blue * 255).toInt().coerceIn(0, 255)
    )
}

private fun formatSize(bytes: Long): String {
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
    file: File
): File {
    val directory = File(context.filesDir, "annotations").apply {
        mkdirs()
    }

    return File(
        directory,
        "annotation_${file.absolutePath.hashCode()}.json"
    )
}

private fun saveAnnotations(
    context: Context,
    file: File,
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

        val target = annotationFile(context, file)
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeText(root.toString())

        if (!temp.renameTo(target)) {
            target.writeText(temp.readText())
            temp.delete()
        }
    } catch (_: Exception) {
    }
}

private fun loadAnnotations(
    context: Context,
    file: File
): Map<Int, List<InkStroke>> {
    return try {
        val source = annotationFile(context, file)
        if (!source.exists()) return emptyMap()

        val root = JSONObject(source.readText())
        val pages = root.getJSONArray("pages")
        val result = mutableMapOf<Int, List<InkStroke>>()

        for (i in 0 until pages.length()) {
            val page = pages.getJSONObject(i)
            val pageIndex = page.getInt("page")
            val strokeArray = page.getJSONArray("strokes")
            val strokes = mutableListOf<InkStroke>()

            for (j in 0 until strokeArray.length()) {
                val strokeObject = strokeArray.getJSONObject(j)
                val pointsArray = strokeObject.getJSONArray("points")
                val points = mutableListOf<InkPoint>()

                for (k in 0 until pointsArray.length()) {
                    val pair = pointsArray.getJSONArray(k)
                    points.add(
                        InkPoint(
                            pair.getDouble(0).toFloat(),
                            pair.getDouble(1).toFloat()
                        )
                    )
                }

                strokes.add(
                    InkStroke(
                        points = points,
                        color = strokeObject.getInt("color"),
                        width = strokeObject.optDouble("width", 3.5).toFloat()
                    )
                )
            }

            result[pageIndex] = strokes
        }

        result
    } catch (_: Exception) {
        emptyMap()
    }
}
