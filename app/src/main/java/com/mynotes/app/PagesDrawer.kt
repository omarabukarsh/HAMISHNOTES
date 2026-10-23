package com.mynotes.app

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
                // تبديل بين كل الصفحات والصفحات المعلَّمة فقط
                var onlyMarks by remember { mutableStateOf(false) }
                if (s.bookmarks.isNotEmpty() || onlyMarks) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ChoiceChip(L("الكل", "All"), !onlyMarks) { onlyMarks = false }
                        ChoiceChip(L("العلامات ${s.bookmarks.size}", "Bookmarks ${s.bookmarks.size}"), onlyMarks) { onlyMarks = true }
                    }
                }
                val shown = if (onlyMarks) s.bookmarks.filter { it < pdf.count }.sorted() else (0 until pdf.count).toList()
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(ratio)
                .shadow(3.dp, shape)
                .background(Color.White)
                .then(if (current) Modifier.border(2.dp, Palette.Gold, shape) else Modifier)
                .clickable(onClick = onClick)
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
}
