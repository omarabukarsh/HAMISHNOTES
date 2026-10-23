package com.mynotes.app

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
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

/**
 * درج الصفحات: عمود واحد بصور كبيرة، أو ثلاثة أعمدة. اسحب حافة الدرج لتغيير العرض
 * فيتبدّل الوضع تلقائيًا عند تجاوز عرض معيّن.
 */
@Composable
fun PagesDrawer(s: DocSession, pdf: PdfDoc, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val cols = if (s.drawerWidth >= 330f) 3 else 1
    val cur = s.listState.firstVisibleItemIndex

    Row(modifier.fillMaxHeight().width(s.drawerWidth.dp)) {
        Surface(
            Modifier.weight(1f).fillMaxHeight(),
            color = Palette.DrawerBg,
            shadowElevation = 14.dp
        ) {
            Column(Modifier.fillMaxSize()) {
                // تبديل بين كل الصفحات، والصفحات المعلَّمة، وفهرس الملف
                var tab by remember { mutableStateOf(0) } // 0 الكل، 1 العلامات، 2 الفهرس
                val hasOutline = s.outline.isNotEmpty()
                if (s.bookmarks.isNotEmpty() || hasOutline || tab != 0) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            .padding(start = 10.dp, end = 10.dp, top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ChoiceChip(L("الكل", "All"), tab == 0) { tab = 0 }
                        if (s.bookmarks.isNotEmpty() || tab == 1) {
                            ChoiceChip(L("العلامات ${s.bookmarks.size}", "Bookmarks ${s.bookmarks.size}"), tab == 1) { tab = 1 }
                        }
                        if (hasOutline) ChoiceChip(L("الفهرس", "Contents"), tab == 2) { tab = 2 }
                    }
                }
                if (tab == 2 && hasOutline) {
                    OutlineList(s) { p -> scope.launch { s.listState.scrollToItem(p) } }
                } else {
                    val shown = if (tab == 1) s.bookmarks.filter { it < pdf.count }.sorted() else (0 until pdf.count).toList()
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(cols),
                        state = gridState,
                        contentPadding = PaddingValues(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(shown.size, key = { shown[it] }) { k ->
                            val i = shown[k]
                            PageThumb(s, pdf, i, i == cur) {
                                scope.launch { s.listState.scrollToItem(i) }
                            }
                        }
                    }
                }
            }
        }
        // مقبض السحب لتغيير العرض
        Box(
            Modifier.width(22.dp).fillMaxHeight().pointerInput(Unit) {
                detectHorizontalDragGestures { change, dx ->
                    change.consume()
                    s.drawerWidth = (s.drawerWidth + dx / density.density).coerceIn(120f, 560f)
                }
            },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.width(5.dp).height(56.dp).clip(RoundedCornerShape(3.dp))
                    .background(Palette.TextSecondary.copy(alpha = 0.55f))
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PageThumb(s: DocSession, pdf: PdfDoc, i: Int, current: Boolean, onClick: () -> Unit) {
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
    val shape = RoundedCornerShape(4.dp)
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(ratio)
                .shadow(3.dp, shape)
                .background(Color.White)
                .then(if (current) Modifier.border(2.dp, Palette.Gold, shape) else Modifier)
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
        ) {
            PageMenu(s, pdf, i, menu, { menu = false }, { confirmDelete = true })
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
                strokes.forEach { drawInk(it.pts, it.color, it.width, it.tool) }
            }
        }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (i in s.bookmarks) PhIcon(R.drawable.ph_bookmark_fill, Palette.Gold, 13.dp)
            Text(
                "${i + 1}",
                fontSize = 12.sp,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                color = if (current) Palette.GoldDeep else Palette.TextSecondary
            )
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

/** فهرس الملف: عناوين متدرجة، والنقر ينتقل إلى الصفحة. */
@Composable
private fun OutlineList(s: DocSession, onGo: (Int) -> Unit) {
    val list = s.outline
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(list.size) { k ->
            val it = list[k]
            val page = s.pageOfMain(it.pageIndex)
            Row(
                Modifier.fillMaxWidth().clickable(enabled = page >= 0) { onGo(page) }
                    .padding(start = (12 + it.level * 14).dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    it.title, modifier = Modifier.weight(1f),
                    fontSize = if (it.level == 0) 14.sp else 13.sp,
                    fontWeight = if (it.level == 0) FontWeight.Bold else FontWeight.Normal,
                    color = if (page >= 0) Palette.TextPrimary else Palette.TextSecondary,
                    maxLines = 2
                )
                if (page >= 0) Text("${page + 1}", fontSize = 12.sp, color = Palette.TextSecondary)
            }
        }
    }
}
