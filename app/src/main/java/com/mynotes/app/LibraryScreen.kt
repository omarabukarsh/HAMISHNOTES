package com.mynotes.app

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
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
    // «متابعة القراءة»: آخر مستند فُتح، يظهر في «الأحدث» فقط ودون بحث
    val resume: DocEntry? =
        if (c.libraryFilter == LibraryFilter.RECENT && query.isEmpty())
            docs.firstOrNull { it.lastOpened > 0L }
        else null

    BoxWithConstraints(Modifier.fillMaxSize().background(Palette.Background)) {
        val wide = maxWidth >= 840.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) Spine()
            Column(Modifier.weight(1f).fillMaxHeight()) {
                LibraryHeader(wide, docs.size, startImport) { showNewNote = true }
                if (!wide) FilterRow()
                if (c.importing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Palette.Gold)
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        !c.libraryLoaded -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                            CircularProgressIndicator(color = Palette.Navy)
                        }
                        c.library.isEmpty() -> EmptyLibrary(startImport) { showNewNote = true }
                        docs.isEmpty() -> NoResults(
                            if (c.libraryFilter == LibraryFilter.FAVORITES && query.isEmpty())
                                "لا توجد مستندات مفضلة بعد. اختر «إضافة إلى المفضلة» من قائمة أي مستند."
                            else
                                "لا يوجد مستند باسم «$query»"
                        )
                        else -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(if (wide) 172.dp else 148.dp),
                            contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 32.dp),
                            horizontalArrangement = Arrangement.spacedBy(26.dp),
                            verticalArrangement = Arrangement.spacedBy(30.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (resume != null) {
                                item(key = "resume", span = { GridItemSpan(maxLineSpan) }) {
                                    ResumeCard(resume, wide) { c.openDocument(resume.id) }
                                }
                            }
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

/** صيغة العدد العربية: مستند واحد، مستندان، 3 مستندات، 11 مستندًا. */
private fun docsCount(n: Int): String = when {
    n == 0 -> "لا مستندات"
    n == 1 -> "مستند واحد"
    n == 2 -> "مستندان"
    n % 100 in 3..10 -> "$n مستندات"
    else -> "$n مستندًا"
}

private fun pagesCount(n: Int): String = when {
    n == 1 -> "صفحة واحدة"
    n == 2 -> "صفحتان"
    n % 100 in 3..10 -> "$n صفحات"
    else -> "$n صفحة"
}

// ---------------------------------------------------------------- الكعب (الشريط الجانبي)

/** شعار مختوم: حرف M ذهبي داخل حلقة ذهبية رفيعة. */
@Composable
private fun Monogram(size: Int, onDark: Boolean) {
    Box(
        Modifier.size(size.dp).clip(CircleShape)
            .background(if (onDark) Palette.InkRaised else Palette.Ink)
            .border(1.5.dp, Palette.Gold, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "M", color = Palette.Gold, fontFamily = Fonts.Serif, fontWeight = FontWeight.Bold,
            fontSize = (size * 0.5f).sp, modifier = Modifier.offset(y = (size * 0.03f).dp)
        )
    }
}

@Composable
private fun Spine() {
    val c = AppController
    val favCount = c.library.count { it.favorite }
    val pages = c.library.sumOf { it.pageCount }
    Column(
        Modifier.width(248.dp).fillMaxHeight().background(Palette.Ink)
            .padding(horizontal = 16.dp, vertical = 22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Monogram(44, onDark = true)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "MyNotes", color = Palette.OnInk, fontFamily = Fonts.Serif,
                    fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 26.sp
                )
                Text("مكتبة الدراسة", color = Palette.OnInkMuted, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        Spacer(Modifier.height(22.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.InkLine))
        Spacer(Modifier.height(16.dp))

        SpineItem("الأحدث", Icons.Default.DateRange, null, c.libraryFilter == LibraryFilter.RECENT) {
            c.libraryFilter = LibraryFilter.RECENT
        }
        SpineItem("كل المستندات", Icons.Default.Home, c.library.size, c.libraryFilter == LibraryFilter.ALL) {
            c.libraryFilter = LibraryFilter.ALL
        }
        SpineItem("المفضلة", Icons.Default.Star, favCount, c.libraryFilter == LibraryFilter.FAVORITES) {
            c.libraryFilter = LibraryFilter.FAVORITES
        }

        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.InkLine))
        Spacer(Modifier.height(14.dp))
        Text(docsCount(c.library.size), color = Palette.OnInk, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(pagesCount(pages), color = Palette.OnInkMuted, fontSize = 12.sp)
    }
}

@Composable
private fun SpineItem(label: String, icon: ImageVector, count: Int?, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).height(46.dp).clip(shape)
            .background(if (selected) Palette.InkRaised else Color.Transparent)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // شريط ذهبي على حافة العنصر المختار
        Box(
            Modifier.width(3.dp).height(22.dp).clip(RoundedCornerShape(2.dp))
                .background(if (selected) Palette.Gold else Color.Transparent)
        )
        Spacer(Modifier.width(11.dp))
        Icon(
            icon, null, Modifier.size(19.dp),
            tint = if (selected) Palette.Gold else Palette.OnInkMuted
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label, fontSize = 15.sp, modifier = Modifier.weight(1f),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Palette.OnInk else Palette.OnInk.copy(alpha = 0.78f)
        )
        if (count != null) {
            Text(
                "$count", fontSize = 12.sp, color = Palette.OnInkMuted,
                modifier = Modifier.padding(end = 12.dp)
            )
        }
    }
}

// ---------------------------------------------------------------- الترويسة والتصفية

@Composable
private fun FilterRow() {
    val c = AppController
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LibraryFilter.values().forEach { f ->
            ChoiceChip(filterTitle(f), c.libraryFilter == f) { c.libraryFilter = f }
        }
    }
}

@Composable
private fun LibraryHeader(wide: Boolean, shown: Int, onImport: () -> Unit, onNewNote: () -> Unit) {
    val c = AppController
    if (wide) {
        Row(
            Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 22.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    filterTitle(c.libraryFilter),
                    style = MaterialTheme.typography.headlineLarge, color = Palette.TextPrimary
                )
                Text(docsCount(shown), fontSize = 13.sp, color = Palette.TextSecondary)
            }
            SearchField(c.libraryQuery, { c.libraryQuery = it }, Modifier.widthIn(min = 260.dp, max = 360.dp))
            if (c.libraryFilter != LibraryFilter.RECENT) {
                SortMenu(c.librarySort) { c.librarySort = it }
            }
            NewNoteButton(onNewNote)
            ImportButton(onImport)
        }
    } else {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Monogram(36, onDark = false)
                Spacer(Modifier.width(10.dp))
                Text(
                    "MyNotes", fontFamily = Fonts.Serif, fontWeight = FontWeight.Bold,
                    fontSize = 22.sp, color = Palette.Navy, modifier = Modifier.weight(1f)
                )
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
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Palette.Navy),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("استيراد PDF", fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun NewNoteButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Palette.Gold),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.GoldDeep),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Icon(Icons.Default.Create, null, Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text("ملاحظة جديدة", fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.height(44.dp)
            .clip(shape)
            .background(Color.White)
            .border(1.dp, Palette.Divider, shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, null, Modifier.size(19.dp), tint = Palette.TextSecondary)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text("ابحث باسم المستند", color = Palette.TextSecondary, fontSize = 14.sp, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Palette.TextPrimary, fontSize = 14.sp, fontFamily = Fonts.Sans),
                cursorBrush = SolidColor(Palette.Navy),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onChange("") }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, "مسح البحث", Modifier.size(16.dp), tint = Palette.TextSecondary)
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
            border = BorderStroke(1.dp, Palette.Divider),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.TextPrimary),
            modifier = Modifier.height(44.dp)
        ) {
            Text("الترتيب: " + sortLabel(sort), fontSize = 13.sp)
            Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortMode.values().forEach { m ->
                DropdownMenuItem(
                    text = {
                        Text(
                            sortLabel(m),
                            fontWeight = if (m == sort) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (m == sort) Palette.Navy else Palette.TextPrimary
                        )
                    },
                    onClick = {
                        onSelect(m)
                        open = false
                    }
                )
            }
        }
    }
}

// ---------------------------------------------------------------- متابعة القراءة

@Composable
private fun ResumeCard(entry: DocEntry, wide: Boolean, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val total = maxOf(1, entry.pageCount)
    val page = (entry.lastPage + 1).coerceIn(1, total)
    Row(
        Modifier.fillMaxWidth().padding(bottom = 4.dp)
            .shadow(2.dp, shape)
            .clip(shape)
            .background(Palette.Ivory)
            .border(1.dp, Palette.Divider, shape)
            .clickable(onClick = onOpen)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(if (wide) 86.dp else 64.dp).aspectRatio(0.74f)
                .shadow(3.dp, RoundedCornerShape(3.dp)).background(Color.White)
        ) { Thumbnail(entry) }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text("تابع من حيث توقفت", fontSize = 12.sp, color = Palette.GoldDeep, fontWeight = FontWeight.Medium)
            Text(
                entry.name, fontFamily = Fonts.Serif, fontWeight = FontWeight.Bold,
                fontSize = if (wide) 24.sp else 20.sp, lineHeight = if (wide) 34.sp else 28.sp,
                color = Palette.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp))
                        .background(Palette.Divider)
                ) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth(page.toFloat() / total)
                            .background(Palette.Gold)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "الصفحة $page من $total", fontSize = 12.sp, color = Palette.TextSecondary
                )
            }
        }
        if (wide) {
            Spacer(Modifier.width(20.dp))
            Button(
                onClick = onOpen,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Navy)
            ) { Text("متابعة القراءة") }
        }
    }
}

// ---------------------------------------------------------------- بطاقة المستند

/** المستند ككتاب على الرف: غلاف بظل ورقي، وأوراق خلفه إن كان متعدد الصفحات. */
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
    val sheet = RoundedCornerShape(3.dp)
    Column(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpen)) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.74f).padding(end = 6.dp, top = 6.dp)) {
            if (entry.pageCount > 1) {
                // ورقتان خلف الغلاف توحيان بسماكة المستند
                Box(
                    Modifier.matchParentSize().offset(x = 6.dp, y = (-6).dp)
                        .clip(sheet).background(Color(0xFFEDE8DD)).border(1.dp, Palette.Divider, sheet)
                )
                Box(
                    Modifier.matchParentSize().offset(x = 3.dp, y = (-3).dp)
                        .clip(sheet).background(Color(0xFFF6F2EA)).border(1.dp, Palette.Divider, sheet)
                )
            }
            Box(
                Modifier.matchParentSize().shadow(4.dp, sheet).background(Color.White)
            ) {
                Thumbnail(entry)
                if (entry.favorite) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp)
                            .clip(CircleShape).background(Palette.Ink.copy(alpha = 0.82f))
                            .clickable(onClick = onFavorite),
                        contentAlignment = Alignment.Center
                    ) { StarGlyph(filled = true, tint = Palette.Gold, modifier = Modifier.size(15.dp)) }
                }
                if (entry.isNote) {
                    Box(
                        Modifier.align(Alignment.BottomStart).padding(6.dp)
                            .clip(RoundedCornerShape(6.dp)).background(Palette.GoldSoft)
                            .padding(horizontal = 7.dp, vertical = 1.dp)
                    ) {
                        Text("ملاحظة", color = Palette.GoldDeep, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        // تقدم القراءة: خط ذهبي رفيع تحت الغلاف
        if (entry.lastOpened > 0L && entry.pageCount > 1) {
            val frac = ((entry.lastPage + 1).toFloat() / entry.pageCount).coerceIn(0f, 1f)
            Box(
                Modifier.padding(top = 8.dp, end = 6.dp).fillMaxWidth().height(2.dp)
                    .clip(RoundedCornerShape(1.dp)).background(Palette.Divider)
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(Palette.Gold))
            }
        } else {
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    entry.name, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold,
                    color = Palette.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    pagesCount(entry.pageCount) + "، " + (
                        if (entry.lastOpened > 0L) "فُتح " + formatRelative(entry.lastOpened)
                        else "أُضيف " + formatRelative(entry.addedAt)
                        ),
                    fontSize = 12.sp, color = Palette.TextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.MoreVert, "خيارات المستند", Modifier.size(19.dp), tint = Palette.TextSecondary)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (entry.favorite) "إزالة من المفضلة" else "إضافة إلى المفضلة") },
                        leadingIcon = {
                            StarGlyph(filled = entry.favorite, tint = Palette.GoldDeep, modifier = Modifier.size(18.dp))
                        },
                        onClick = { menu = false; onFavorite() }
                    )
                    DropdownMenuItem(text = { Text("إعادة تسمية") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("إنشاء نسخة") }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(
                        text = { Text("حذف", color = Palette.Danger) },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
        }
        Text(formatSize(entry.sizeBytes), fontSize = 11.sp, color = Palette.TextSecondary.copy(alpha = 0.75f))
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
        Box(Modifier.fillMaxSize().background(Palette.Ivory), contentAlignment = Alignment.Center) {
            Text("PDF", color = Palette.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
        EmptyShelfArt()
        Spacer(Modifier.height(20.dp))
        Text("ابدأ مكتبتك", style = MaterialTheme.typography.headlineMedium, color = Palette.TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "استورد محاضرة أو كتابًا بصيغة PDF لتقرأه وتكتب عليه بالقلم،\nأو أنشئ ملاحظة فارغة.",
            fontSize = 15.sp, lineHeight = 24.sp, color = Palette.TextSecondary, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NewNoteButton(onNewNote)
            ImportButton(onImport)
        }
    }
}

@Composable
private fun NoResults(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 15.sp, lineHeight = 24.sp, color = Palette.TextSecondary, textAlign = TextAlign.Center)
    }
}

// ---------------------------------------------------------------- تنسيق

/** إنشاء ملاحظة فارغة بالكامل داخل المكتبة: الاسم، التخطيط، ولون الورقة. */
@Composable
private fun NewNoteDialog(onCreate: (String, String, Int) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("ملاحظة جديدة") }
    var template by remember { mutableStateOf("lined") }
    var paper by remember { mutableStateOf(Templates.paperColors[1].first) }
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
