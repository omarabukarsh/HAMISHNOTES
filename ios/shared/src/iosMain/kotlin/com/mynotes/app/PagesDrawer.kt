package com.mynotes.app

import kotlinx.coroutines.IO

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** عرض درج الصفحات (dp): ثابت، ويُزاح المستند بجانبه على الشاشات العريضة. */
const val PAGES_PANEL_W = 300

/**
 * درج الصفحات الجانبي: رأس فيه زر التحديد، وأربعة تبويبات (الصفحات، الفهرس، العلامات، التسجيلات)،
 * وقائمة خيارات؛ ثم شبكة صور الصفحات برقم الصفحة وزر «⋯» وعلامة في الزاوية، وبطاقة «إضافة صفحة».
 */
@Composable
fun PagesDrawer(s: DocSession, pdf: PdfDoc, modifier: Modifier = Modifier) {
    val c = AppController
    val scope = rememberCoroutineScope()
    val cur = s.listState.firstVisibleItemIndex
    // يفتح الدرج عند الصفحة المعروضة لا من البداية
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = (cur - 2).coerceAtLeast(0))
    var tabForFollow by remember { mutableStateOf(0) }
    // تقليب صفحات المستند والدرج مفتوح: تبقى صورة الصفحة الحالية ظاهرة في الدرج
    androidx.compose.runtime.LaunchedEffect(cur, tabForFollow) {
        if (tabForFollow != 0) return@LaunchedEffect
        val vis = gridState.layoutInfo.visibleItemsInfo
        if (vis.isEmpty() || vis.none { it.index == cur }) gridState.animateScrollToItem((cur - 2).coerceAtLeast(0))
    }
    var tab by remember { mutableStateOf(0) } // 0 الصفحات، 1 الفهرس، 2 العلامات، 3 التسجيلات
    var cols by remember { mutableStateOf(2) }
    var selecting by remember { mutableStateOf(false) }
    val picked = remember { mutableStateListOf<Int>() }
    var moreMenu by remember { mutableStateOf(false) }
    var addPage by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val n = pdf.count
    fun go(p: Int) = scope.launch { s.listState.scrollToItem(p) }

    Surface(
        modifier.fillMaxHeight().width(PAGES_PANEL_W.dp),
        color = Palette.DrawerBg,
        shadowElevation = 10.dp
    ) {
        Column(Modifier.fillMaxSize()) {
            // ---- الرأس ----
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape)
                        .background(if (selecting) Palette.NavySoft else Color.Transparent)
                        .clickable(enabled = tab == 0) {
                            selecting = !selecting
                            picked.clear()
                        },
                    contentAlignment = Alignment.Center
                ) { PhIcon(R.drawable.ph_check_circle, if (selecting) Palette.Navy else Palette.TextSecondary) }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(Palette.TextSecondary.copy(alpha = 0.12f)).padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf(R.drawable.ph_grid, R.drawable.ph_list, R.drawable.ph_bookmark, R.drawable.ph_waveform).forEachIndexed { k, icon ->
                        val on = tab == k
                        Box(
                            Modifier.size(width = 46.dp, height = 34.dp).clip(RoundedCornerShape(9.dp))
                                .background(if (on) Color.White else Color.Transparent)
                                .clickable {
                                    tab = k
                                    tabForFollow = k
                                    if (k != 0) { selecting = false; picked.clear() }
                                },
                            contentAlignment = Alignment.Center
                        ) { PhIcon(icon, if (on) Palette.Navy else Palette.TextSecondary, 20.dp) }
                    }
                }
                Spacer(Modifier.weight(1f))
                Box {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).clickable { moreMenu = true },
                        contentAlignment = Alignment.Center
                    ) { PhIcon(R.drawable.ph_more, Palette.TextSecondary) }
                    DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                        DropdownMenuItem(text = { Text(L("صور كبيرة (عمود واحد)", "Large thumbnails (1 column)")) }, onClick = { cols = 1; moreMenu = false })
                        DropdownMenuItem(text = { Text(L("صور متوسطة (عمودان)", "Medium thumbnails (2 columns)")) }, onClick = { cols = 2; moreMenu = false })
                        DropdownMenuItem(text = { Text(L("صور صغيرة (3 أعمدة)", "Small thumbnails (3 columns)")) }, onClick = { cols = 3; moreMenu = false })
                        DropdownMenuItem(text = { Text(L("إضافة صفحة في النهاية", "Add page at the end")) }, onClick = { moreMenu = false; addPage = true })
                        DropdownMenuItem(text = { Text(L("إغلاق الشريط", "Close sidebar")) }, onClick = { moreMenu = false; s.drawerOpen = false })
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Divider))

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    1 -> OutlineList(s, cur) { p -> go(p) }
                    3 -> RecordingsList(s)
                    else -> {
                        val shown = if (tab == 2) s.bookmarks.filter { it < n }.sorted() else (0 until n).toList()
                        if (tab == 2 && shown.isEmpty()) {
                            EmptyPanel(
                                R.drawable.ph_bookmark,
                                L("لا توجد علامات بعد", "No bookmarks yet"),
                                L("ضع علامة على صفحة من زاوية صورتها أو من قائمة الصفحة لتجدها هنا بسرعة.", "Mark a page from its thumbnail corner or the page menu to find it here quickly.")
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(cols),
                                state = gridState,
                                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 80.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(shown.size, key = { shown[it] }) { k ->
                                    val i = shown[k]
                                    PageThumb(
                                        s, pdf, i, i == cur,
                                        selecting = selecting, picked = i in picked
                                    ) {
                                        if (selecting) {
                                            if (i in picked) picked.remove(i) else picked.add(i)
                                        } else go(i)
                                    }
                                }
                                if (tab == 0 && !selecting) {
                                    item(key = "add") {
                                        Box(
                                            Modifier.fillMaxWidth().aspectRatio(pdf.ratio(n - 1).coerceIn(0.5f, 1.6f))
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Palette.TextSecondary.copy(alpha = 0.10f))
                                                .border(1.dp, Palette.Divider, RoundedCornerShape(10.dp))
                                                .clickable { addPage = true },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                PhIcon(R.drawable.ph_plus, Palette.TextPrimary, 26.dp)
                                                Spacer(Modifier.height(6.dp))
                                                Text(L("إضافة صفحة", "Add page"), fontSize = 14.sp, color = Palette.TextPrimary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // شريط التحديد المتعدد
                if (selecting) {
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(12.dp)
                            .shadow(6.dp, RoundedCornerShape(16.dp))
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            L("${picked.size} محددة", "${picked.size} selected"),
                            fontSize = 13.sp, color = Palette.TextSecondary, modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        TextButton(enabled = picked.isNotEmpty(), onClick = {
                            val allMarked = picked.all { it in s.bookmarks }
                            picked.toList().forEach { p -> if ((p in s.bookmarks) == allMarked) s.toggleBookmark(p) }
                        }) { PhIcon(R.drawable.ph_bookmark, Palette.Navy, 20.dp) }
                        TextButton(enabled = picked.isNotEmpty() && picked.size < n, onClick = { confirmDelete = true }) {
                            PhIcon(R.drawable.ph_trash, Palette.Danger, 20.dp)
                        }
                        TextButton(onClick = { selecting = false; picked.clear() }) { Text(L("تم", "Done")) }
                    }
                }
            }
        }
    }

    if (addPage) {
        TemplatePickerDialog(
            mode = PickerMode.ADD_PAGE,
            onDismiss = { addPage = false },
            onConfirm = { choice, _, _ ->
                addPage = false
                c.addBlankPage(s, n - 1, choice.kind, choice.color, choice.ratio())
                scope.launch { gridState.animateScrollToItem(n) }
            }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(L("حذف ${picked.size} صفحة؟", "Delete ${picked.size} pages?")) },
            text = { Text(L("ستُحذف الصفحات المحددة وكل ما كتبته عليها.", "The selected pages and everything written on them will be deleted.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val drop = picked.toSet()
                    s.rearrangePages((0 until n).filter { it !in drop })
                    picked.clear()
                    selecting = false
                }) { Text(L("حذف", "Delete"), color = Palette.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(L("إلغاء", "Cancel")) } }
        )
    }
}

@Composable
private fun EmptyPanel(icon: Int, title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)).background(Palette.NavySoft),
            contentAlignment = Alignment.Center
        ) { PhIcon(icon, Palette.Navy, 44.dp) }
        Spacer(Modifier.height(18.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, fontSize = 14.sp, lineHeight = 20.sp, color = Palette.TextSecondary, textAlign = TextAlign.Center)
    }
}

/** تبويب التسجيلات: تسجيلات المحاضرة في هذا المستند. */
@Composable
private fun RecordingsList(s: DocSession) {
    val recs = AudioNotes.list(s.id).sortedByDescending { it.start }
    if (recs.isEmpty()) {
        EmptyPanel(
            R.drawable.ph_waveform,
            L("تسجيلات المحاضرة", "Lecture recordings"),
            L("سجّل من زر الميكروفون في الشريط؛ كل ما تكتبه أثناء التسجيل يُربط بلحظته.", "Record with the microphone button in the toolbar; everything you write is linked to its moment.")
        )
        return
    }
    val fmt = remember { java.text.SimpleDateFormat("d/M  HH:mm", java.util.Locale.getDefault()) }
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(recs.size) { k ->
            val r = recs[k]
            val playing = AudioNotes.playing?.id == r.id
            Row(
                Modifier.fillMaxWidth().clickable { if (playing) AudioNotes.stopPlayback() else AudioNotes.play(r, 0L) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PhIcon(if (playing) R.drawable.ph_stop else R.drawable.ph_play, Palette.Navy, 20.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(fmt.format(java.util.Date(r.start)), fontSize = 14.sp, color = Palette.TextPrimary)
                    Text(mmss(r.durationMs), fontSize = 12.sp, color = Palette.TextSecondary)
                }
                Box(
                    Modifier.size(34.dp).clip(CircleShape).clickable { AudioNotes.delete(r) },
                    contentAlignment = Alignment.Center
                ) { PhIcon(R.drawable.ph_trash, Palette.TextSecondary, 18.dp) }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PageThumb(
    s: DocSession, pdf: PdfDoc, i: Int, current: Boolean,
    selecting: Boolean = false, picked: Boolean = false, onClick: () -> Unit
) {
    val ref = pdf.pages.getOrNull(i)
    val ratio = pdf.ratio(i)
    val bmp by produceState<Bitmap?>(pdf.cached(ref, 360), ref) {
        value = pdf.cached(ref, 360) ?: withContext(Dispatchers.IO) {
            try {
                pdf.render(ref, 360)
            } catch (t: Throwable) {
                null
            }
        }
    }
    val strokes = s.annotations[i] ?: emptyList()
    val shape = RoundedCornerShape(8.dp)
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val marked = i in s.bookmarks
    Column {
        Box(
            Modifier.fillMaxWidth().aspectRatio(ratio)
                .shadow(2.dp, shape)
                .clip(shape)
                .background(Color.White)
                .then(
                    when {
                        selecting && picked -> Modifier.border(3.dp, Palette.Navy, shape)
                        !selecting && current -> Modifier.border(3.dp, Color(0xFF3B82F6), shape)
                        else -> Modifier.border(1.dp, Palette.Divider, shape)
                    }
                )
                .combinedClickable(onClick = onClick, onLongClick = { if (!selecting) menu = true })
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
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                (s.marks[i] ?: emptyList()).forEach { m ->
                    val col = Exporter.markColor(m.color, strong = m.level >= 2)
                    m.rects.forEach { r -> drawMarkRect(col, r, w) }
                }
                strokes.forEach { drawStroke(it, it.color) }
            }
            // علامة الصفحة في الزاوية: تُضاف أو تُزال بالنقر
            Box(
                Modifier.align(Alignment.TopEnd).size(32.dp).clickable(enabled = !selecting) { s.toggleBookmark(i) },
                contentAlignment = Alignment.Center
            ) {
                PhIcon(
                    if (marked) R.drawable.ph_bookmark_fill else R.drawable.ph_bookmark,
                    if (marked) Palette.Gold else Palette.TextSecondary.copy(alpha = 0.55f), 18.dp
                )
            }
            if (selecting) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(6.dp).size(24.dp).clip(CircleShape)
                        .background(if (picked) Palette.Navy else Color.White)
                        .border(1.5.dp, if (picked) Palette.Navy else Palette.TextSecondary, CircleShape),
                    contentAlignment = Alignment.Center
                ) { if (picked) PhIcon(R.drawable.ph_check, Color.White, 15.dp) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${i + 1}",
                modifier = Modifier.padding(start = 2.dp),
                fontSize = 13.sp,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                color = if (current) Palette.Navy else Palette.TextSecondary
            )
            Spacer(Modifier.weight(1f))
            if (!selecting) {
                Box {
                    Box(
                        Modifier.size(width = 36.dp, height = 28.dp).clip(RoundedCornerShape(8.dp)).clickable { menu = true },
                        contentAlignment = Alignment.Center
                    ) { PhIcon(R.drawable.ph_more, Palette.TextSecondary, 18.dp) }
                    PageMenu(s, pdf, i, menu, { menu = false }, { confirmDelete = true })
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(L("حذف الصفحة ${i + 1}؟", "Delete page ${i + 1}?")) },
            text = { Text(L("ستُحذف الصفحة وكل ما كتبته عليها، ولا يمكن التراجع.", "The page and everything written on it will be deleted. This cannot be undone.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    s.rearrangePages((0 until pdf.count).filter { it != i })
                }) { Text(L("حذف", "Delete"), color = Color(0xFFC62828)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(L("إلغاء", "Cancel")) } }
        )
    }
}

/** قائمة الصفحة: تحريك، نسخ، حذف. */
@Composable
private fun PageMenu(s: DocSession, pdf: PdfDoc, i: Int, open: Boolean, onClose: () -> Unit, onDelete: () -> Unit) {
    val n = pdf.count
    DropdownMenu(expanded = open, onDismissRequest = onClose) {
        fun order(block: MutableList<Int>.() -> Unit) = (0 until n).toMutableList().apply(block)
        if (i > 0) DropdownMenuItem(text = { Text(L("تحريك لأعلى", "Move up")) }, onClick = {
            onClose()
            s.rearrangePages(order { add(i - 1, removeAt(i)) })
        })
        if (i < n - 1) DropdownMenuItem(text = { Text(L("تحريك لأسفل", "Move down")) }, onClick = {
            onClose()
            s.rearrangePages(order { add(i + 1, removeAt(i)) })
        })
        if (i > 0) DropdownMenuItem(text = { Text(L("نقل إلى البداية", "Move to start")) }, onClick = {
            onClose()
            s.rearrangePages(order { add(0, removeAt(i)) })
        })
        if (i < n - 1) DropdownMenuItem(text = { Text(L("نقل إلى النهاية", "Move to end")) }, onClick = {
            onClose()
            s.rearrangePages(order { add(removeAt(i)) })
        })
        DropdownMenuItem(text = { Text(L("نسخ الصفحة", "Duplicate page")) }, onClick = {
            onClose()
            s.rearrangePages(order { add(i + 1, i) })
        })
        if (n > 1) DropdownMenuItem(text = { Text(L("حذف الصفحة", "Delete page"), color = Color(0xFFC62828)) }, onClick = {
            onClose()
            onDelete()
        })
    }
}

/**
 * فهرس المستند: عناوين متدرجة، والنقر ينتقل إلى الصفحة.
 * زر «⋯» بجانب كل عنوان: إعادة التسمية، إزاحة المستوى، الحذف؛ وزر لإضافة عنوان للصفحة الحالية.
 */
@Composable
private fun OutlineList(s: DocSession, current: Int, onGo: (Int) -> Unit) {
    val list = s.outlineEntries()
    var editIndex by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (list.isEmpty()) {
                EmptyPanel(
                    R.drawable.ph_list,
                    L("لا توجد عناوين بعد", "No headings yet"),
                    L("أضف عناوين لتنظيم مستندك والتنقل فيه بسرعة.", "Add headings to organize your document and move through it quickly.")
                )
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(list.size) { k ->
                        val it = list[k]
                        var menu by remember { mutableStateOf(false) }
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = it.page >= 0) { onGo(it.page) }
                                .padding(start = (12 + it.level * 14).dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                it.title, modifier = Modifier.weight(1f),
                                fontSize = if (it.level == 0) 14.sp else 13.sp,
                                fontWeight = if (it.level == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (it.page >= 0) Palette.TextPrimary else Palette.TextSecondary,
                                maxLines = 2
                            )
                            if (it.page >= 0) Text("${it.page + 1}", fontSize = 12.sp, color = Palette.TextSecondary)
                            Box {
                                Box(
                                    Modifier.size(34.dp).clip(CircleShape).clickable { menu = true },
                                    contentAlignment = Alignment.Center
                                ) { PhIcon(R.drawable.ph_more, Palette.TextSecondary, 16.dp) }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text(L("إعادة تسمية", "Rename")) }, onClick = { menu = false; editIndex = k })
                                    if (it.level < 3) DropdownMenuItem(text = { Text(L("عنوان فرعي (إزاحة للداخل)", "Make subheading (indent)")) }, onClick = {
                                        menu = false
                                        s.saveOutline(list.toMutableList().also { l -> l[k] = it.copy(level = it.level + 1) })
                                    })
                                    if (it.level > 0) DropdownMenuItem(text = { Text(L("عنوان رئيسي (إزاحة للخارج)", "Promote (outdent)")) }, onClick = {
                                        menu = false
                                        s.saveOutline(list.toMutableList().also { l -> l[k] = it.copy(level = it.level - 1) })
                                    })
                                    if (k > 0) DropdownMenuItem(text = { Text(L("تحريك لأعلى", "Move up")) }, onClick = {
                                        menu = false
                                        s.saveOutline(list.toMutableList().also { l -> l.add(k - 1, l.removeAt(k)) })
                                    })
                                    if (k < list.size - 1) DropdownMenuItem(text = { Text(L("تحريك لأسفل", "Move down")) }, onClick = {
                                        menu = false
                                        s.saveOutline(list.toMutableList().also { l -> l.add(k + 1, l.removeAt(k)) })
                                    })
                                    DropdownMenuItem(text = { Text(L("حذف", "Delete"), color = Palette.Danger) }, onClick = {
                                        menu = false
                                        s.saveOutline(list.filterIndexed { i, _ -> i != k })
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Divider))
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { adding = true }) {
                PhIcon(R.drawable.ph_plus, Palette.Navy, 18.dp)
                Spacer(Modifier.width(6.dp))
                Text(L("عنوان للصفحة ${current + 1}", "Heading for page ${current + 1}"), color = Palette.Navy)
            }
            Spacer(Modifier.weight(1f))
            if (s.outlineCustom != null && s.outline.isNotEmpty()) {
                TextButton(onClick = { s.resetOutline() }) { Text(L("استعادة الأصلي", "Restore original"), color = Palette.TextSecondary, fontSize = 12.sp) }
            }
        }
    }
    val ei = editIndex
    if (ei != null && ei < list.size) {
        OutlineTitleDialog(L("إعادة تسمية العنوان", "Rename heading"), list[ei].title, onDismiss = { editIndex = null }) { t ->
            editIndex = null
            s.saveOutline(list.toMutableList().also { l -> l[ei] = l[ei].copy(title = t) })
        }
    }
    if (adding) {
        OutlineTitleDialog(L("عنوان للصفحة ${current + 1}", "Heading for page ${current + 1}"), "", onDismiss = { adding = false }) { t ->
            adding = false
            s.addOutlineEntry(current, t)
        }
    }
}

@Composable
fun OutlineTitleDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                label = { Text(L("العنوان", "Title")) }, modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onSave(text.trim()) }) { Text(L("حفظ", "Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("إلغاء", "Cancel")) } }
    )
}
