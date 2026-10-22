package com.mynotes.app

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ---------------------------------------------------------------- الشاشة

@Composable
fun LibraryScreen() {
    val c = AppController

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> -> c.importDocuments(uris) }
    val mimes = remember {
        DocType.values().filter { it.supported }.map { it.mime }.toTypedArray()
    }
    val startImport: () -> Unit = { picker.launch(mimes) }

    var renameTarget by remember { mutableStateOf<DocEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<DocEntry?>(null) }
    var showNewNote by remember { mutableStateOf(false) }

    val query = c.libraryQuery.trim()
    val base = c.library.toList().filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) }
    val docs: List<DocEntry> = when (c.libraryFilter) {
        LibraryFilter.RECENT -> base.sortedByDescending { it.activityAt() }.take(24)
        LibraryFilter.ALL -> sortDocs(base, c.librarySort)
        LibraryFilter.FAVORITES -> sortDocs(base.filter { it.favorite }, c.librarySort)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) {
                Sidebar()
                Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.Divider))
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                LibraryHeader(wide, startImport) { showNewNote = true }
                if (!wide) FilterRow()
                if (c.importing) LinearProgressIndicator(Modifier.fillMaxWidth())
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        !c.libraryLoaded -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        c.library.isEmpty() -> EmptyLibrary(startImport) { showNewNote = true }
                        docs.isEmpty() -> NoResults(
                            if (c.libraryFilter == LibraryFilter.FAVORITES && query.isEmpty())
                                "لا توجد مستندات مفضلة بعد"
                            else
                                "لا توجد نتائج مطابقة"
                        )
                        else -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(if (wide) 176.dp else 150.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(docs, key = { it.id }) { e ->
                                DocCard(
                                    entry = e,
                                    onOpen = { c.openDocument(e.id) },
                                    onFavorite = { c.toggleFavorite(e.id) },
                                    onRename = { renameTarget = e },
                                    onDuplicate = { c.duplicate(e) },
                                    onDelete = { deleteTarget = e }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val rt = renameTarget
    if (rt != null) {
        var name by remember(rt.id) { mutableStateOf(rt.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("إعادة تسمية المستند") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    c.rename(rt.id, name)
                    renameTarget = null
                }) { Text("حفظ") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("إلغاء") }
            }
        )
    }

    if (showNewNote) {
        NewNoteDialog(
            onCreate = { n, t, col ->
                showNewNote = false
                c.createNote(n, t, col)
            },
            onDismiss = { showNewNote = false }
        )
    }

    val dt = deleteTarget
    if (dt != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف المستند؟") },
            text = { Text("سيتم حذف «${dt.name}» وكل الكتابة عليه نهائيًا، ولا يمكن التراجع.") },
            confirmButton = {
                TextButton(onClick = {
                    c.delete(dt)
                    deleteTarget = null
                }) { Text("حذف", color = Palette.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("إلغاء") }
            }
        )
    }
}

private fun sortDocs(list: List<DocEntry>, mode: SortMode): List<DocEntry> = when (mode) {
    SortMode.LAST_OPENED -> list.sortedByDescending { it.activityAt() }
    SortMode.NAME -> list.sortedBy { it.name.lowercase() }
    SortMode.DATE_ADDED -> list.sortedByDescending { it.addedAt }
}

private fun filterTitle(f: LibraryFilter): String = when (f) {
    LibraryFilter.RECENT -> "الأحدث"
    LibraryFilter.ALL -> "كل المستندات"
    LibraryFilter.FAVORITES -> "المفضلة"
}

private fun sortLabel(m: SortMode): String = when (m) {
    SortMode.LAST_OPENED -> "آخر فتح"
    SortMode.NAME -> "الاسم"
    SortMode.DATE_ADDED -> "تاريخ الإضافة"
}

// ---------------------------------------------------------------- الشريط الجانبي والترويسة

@Composable
private fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Palette.Navy),
            contentAlignment = Alignment.Center
        ) {
            Text("M", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text("MyNotes", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Navy)
    }
}

@Composable
private fun Sidebar() {
    val c = AppController
    Column(
        Modifier.width(232.dp).fillMaxHeight().background(Color.White)
            .padding(horizontal = 14.dp, vertical = 18.dp)
    ) {
        Brand()
        Spacer(Modifier.height(24.dp))
        NavItem("الأحدث", Icons.Default.DateRange, c.libraryFilter == LibraryFilter.RECENT) {
            c.libraryFilter = LibraryFilter.RECENT
        }
        Spacer(Modifier.height(4.dp))
        NavItem("كل المستندات", Icons.Default.Home, c.libraryFilter == LibraryFilter.ALL) {
            c.libraryFilter = LibraryFilter.ALL
        }
        Spacer(Modifier.height(4.dp))
        NavItem("المفضلة", Icons.Default.Favorite, c.libraryFilter == LibraryFilter.FAVORITES) {
            c.libraryFilter = LibraryFilter.FAVORITES
        }
        Spacer(Modifier.weight(1f))
        Text("${c.library.size} مستند", fontSize = 12.sp, color = Palette.TextSecondary)
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (selected) Palette.NavySoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, null, Modifier.size(20.dp),
            tint = if (selected) Palette.Navy else Palette.TextSecondary
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label, fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Palette.Navy else Palette.TextPrimary
        )
    }
}

@Composable
private fun FilterRow() {
    val c = AppController
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LibraryFilter.values().forEach { f ->
            val sel = c.libraryFilter == f
            Box(
                Modifier.clip(RoundedCornerShape(18.dp))
                    .background(if (sel) Palette.NavySoft else Color.White)
                    .border(1.dp, if (sel) Palette.Navy else Palette.Divider, RoundedCornerShape(18.dp))
                    .clickable { c.libraryFilter = f }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    filterTitle(f), fontSize = 14.sp,
                    color = if (sel) Palette.Navy else Palette.TextPrimary,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun LibraryHeader(wide: Boolean, onImport: () -> Unit, onNewNote: () -> Unit) {
    val c = AppController
    if (wide) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                filterTitle(c.libraryFilter), fontSize = 24.sp,
                fontWeight = FontWeight.Bold, color = Palette.TextPrimary
            )
            Spacer(Modifier.weight(1f))
            SearchField(c.libraryQuery, { c.libraryQuery = it }, Modifier.width(340.dp))
            if (c.libraryFilter != LibraryFilter.RECENT) {
                SortMenu(c.librarySort) { c.librarySort = it }
            }
            NewNoteButton(onNewNote)
            ImportButton(onImport)
        }
    } else {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Brand()
                Spacer(Modifier.weight(1f))
                NewNoteButton(onNewNote)
                Spacer(Modifier.width(8.dp))
                ImportButton(onImport)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchField(c.libraryQuery, { c.libraryQuery = it }, Modifier.weight(1f))
                if (c.libraryFilter != LibraryFilter.RECENT) {
                    SortMenu(c.librarySort) { c.librarySort = it }
                }
            }
        }
    }
}

@Composable
private fun ImportButton(onClick: () -> Unit) {
    Button(onClick = onClick, shape = RoundedCornerShape(12.dp)) {
        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("استيراد")
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier) {
    Row(
        modifier.height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, Palette.Divider, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, null, Modifier.size(20.dp), tint = Palette.TextSecondary)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text("ابحث في المستندات", color = Palette.TextSecondary, fontSize = 15.sp, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Palette.TextPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(Palette.Navy),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onChange("") }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, "مسح", Modifier.size(16.dp), tint = Palette.TextSecondary)
            }
        }
    }
}

@Composable
private fun SortMenu(sort: SortMode, onSelect: (SortMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { open = true },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.height(44.dp)
        ) {
            Text(sortLabel(sort), fontSize = 14.sp)
            Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortMode.values().forEach { m ->
                DropdownMenuItem(
                    text = { Text(sortLabel(m)) },
                    onClick = {
                        onSelect(m)
                        open = false
                    }
                )
            }
        }
    }
}

// ---------------------------------------------------------------- بطاقة المستند

@Composable
private fun DocCard(
    entry: DocEntry,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.clip(shape).background(Color.White)
            .border(1.dp, Palette.Divider, shape)
            .clickable(onClick = onOpen)
            .padding(10.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.78f)
                .clip(RoundedCornerShape(8.dp)).background(Palette.Background)
        ) {
            Thumbnail(entry)
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(30.dp)
                    .clip(CircleShape).background(Color.White.copy(alpha = 0.88f))
                    .clickable(onClick = onFavorite),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (entry.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    if (entry.favorite) "إزالة من المفضلة" else "إضافة إلى المفضلة",
                    Modifier.size(18.dp),
                    tint = if (entry.favorite) Palette.Gold else Palette.TextSecondary
                )
            }
            Box(
                Modifier.align(Alignment.BottomStart).padding(6.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Palette.Navy)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    if (entry.isNote) "NOTE" else entry.type.ext.uppercase(), color = Color.White,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    entry.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = Palette.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${entry.pageCount} صفحة · ${formatSize(entry.sizeBytes)}",
                    fontSize = 12.sp, color = Palette.TextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (entry.lastOpened > 0L) "فُتح " + formatRelative(entry.lastOpened)
                    else "أُضيف " + formatRelative(entry.addedAt),
                    fontSize = 12.sp, color = Palette.TextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.MoreVert, "خيارات", Modifier.size(20.dp), tint = Palette.TextSecondary)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (entry.favorite) "إزالة من المفضلة" else "إضافة إلى المفضلة") },
                        onClick = { menu = false; onFavorite() }
                    )
                    DropdownMenuItem(
                        text = { Text("إعادة تسمية") },
                        onClick = { menu = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("إنشاء نسخة") },
                        onClick = { menu = false; onDuplicate() }
                    )
                    DropdownMenuItem(
                        text = { Text("حذف", color = Palette.Danger) },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(entry: DocEntry) {
    val bmp by produceState<Bitmap?>(null, entry.id) {
        value = withContext(Dispatchers.IO) {
            try {
                AppController.repo.thumbnail(entry)
            } catch (t: Throwable) {
                null
            }
        }
    }
    val b = bmp
    if (b != null) {
        val img = remember(b) { b.asImageBitmap() }
        Image(
            bitmap = img,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter
        )
    } else {
        Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
            Text("PDF", color = Palette.TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ---------------------------------------------------------------- الحالات الفارغة

@Composable
private fun EmptyLibrary(onImport: () -> Unit, onNewNote: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(Palette.NavySoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, null, Modifier.size(28.dp), tint = Palette.Navy)
        }
        Spacer(Modifier.height(14.dp))
        Text("مكتبتك فارغة", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "استورد ملف PDF من جهازك لتبدأ القراءة والكتابة عليه بالقلم.",
            fontSize = 14.sp, color = Palette.TextSecondary, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NewNoteButton(onNewNote)
            ImportButton(onImport)
        }
    }
}

@Composable
private fun NoResults(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 15.sp, color = Palette.TextSecondary, textAlign = TextAlign.Center)
    }
}

// ---------------------------------------------------------------- تنسيق

@Composable
private fun NewNoteButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(12.dp)) {
        Icon(Icons.Default.Create, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("ملاحظة جديدة")
    }
}

/** إنشاء ملاحظة فارغة بالكامل داخل المكتبة: الاسم، التخطيط، ولون الورقة. */
@Composable
private fun NewNoteDialog(onCreate: (String, String, Int) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("ملاحظة جديدة") }
    var template by remember { mutableStateOf("lined") }
    var paper by remember { mutableStateOf(Templates.paperColors[0].first) }
    var picker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ملاحظة جديدة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("الاسم") }
                )
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
            TextButton(onClick = { onCreate(name, template, paper) }) { Text("إنشاء") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
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

private fun formatSize(b: Long): String =
    if (b >= 1024L * 1024L) String.format(Locale.US, "%.1f MB", b / 1048576.0)
    else String.format(Locale.US, "%d KB", maxOf(1L, b / 1024L))

private fun dayStart(t: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = t
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun formatRelative(ts: Long): String {
    if (ts <= 0L) return "—"
    val d = ((dayStart(System.currentTimeMillis()) - dayStart(ts)) / 86_400_000.0).roundToInt()
    return when {
        d <= 0 -> "اليوم"
        d == 1 -> "أمس"
        d < 7 -> "قبل $d أيام"
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ts))
    }
}
