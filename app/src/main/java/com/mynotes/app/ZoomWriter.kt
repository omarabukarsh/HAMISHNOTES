package com.mynotes.app

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * نافذة الكتابة المكبّرة: تعرض منطقة صغيرة من الصفحة مكبّرة أسفل الشاشة.
 * ما تكتبه فيها بالقلم يظهر صغيرًا ومرتبًا في مكانه على الصفحة.
 * النقر بالإصبع على الصفحة ينقل المنطقة، والأسهم تنقلها جانبيًا أو لسطر جديد.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ZoomPanel(s: DocSession, pdf: PdfDoc) {
    val zb = s.zoomBox ?: return
    val c = AppController
    val ref = pdf.pages.getOrNull(zb.page)
    val bmp by produceState<Bitmap?>(pdf.cached(ref, 1600), ref) {
        value = pdf.cached(ref, 1600) ?: withContext(Dispatchers.IO) {
            try {
                pdf.render(ref, 1600)
            } catch (t: Throwable) {
                null
            }
        }
    }
    val img = remember(bmp) { bmp?.asImageBitmap() }
    val boxNow by rememberUpdatedState(zb)
    val live = remember { ArrayList<Pt>() }
    var tick by remember { mutableStateOf(0) }
    val night = c.nightPages
    val strokes = s.annotations[zb.page] ?: emptyList()

    fun move(dx: Float, dy: Float, resetX: Boolean = false) {
        val b = s.zoomBox ?: return
        val maxY = (1f / pdf.ratio(b.page) - b.h).coerceAtLeast(0f)
        var nx = b.x + dx
        var ny = b.y + dy
        if (resetX) nx = if (I18n.english) 0.04f else (0.96f - b.w)
        s.zoomBox = b.copy(x = nx.coerceIn(0f, (1f - b.w).coerceAtLeast(0f)), y = ny.coerceIn(0f, maxY))
    }

    Row(
        Modifier.fillMaxWidth().height(230.dp).background(Palette.DrawerBg)
            .padding(8.dp)
    ) {
        Canvas(
            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).clipToBounds()
                .border(1.5.dp, Palette.Gold, RoundedCornerShape(12.dp))
                .graphicsLayer()
                .onSizeChanged { sz ->
                    // ارتفاع المنطقة على الصفحة يتبع نسبة النافذة
                    val b = s.zoomBox ?: return@onSizeChanged
                    if (sz.width > 0) {
                        val h = b.w * sz.height / sz.width.toFloat()
                        if (kotlin.math.abs(h - b.h) > 1e-4f) s.zoomBox = b.copy(h = h)
                    }
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val down = ev.changes.firstOrNull {
                                it.changedToDown() && (
                                    it.type == PointerType.Stylus || it.type == PointerType.Eraser || it.type == PointerType.Mouse ||
                                        // وضع «القلم والإصبع»: الإصبع يكتب في النافذة أيضًا (ما لم يكن القلم قريبًا)
                                        (it.type == PointerType.Touch && PenInput.fingerWrites && !PenInput.stylusActiveRecently())
                                    )
                            } ?: continue
                            val b = boxNow
                            val k = size.width / b.w
                            fun toPage(o: Offset) = Pt(b.x + o.x / k, b.y + o.y / k, 0.5f)
                            val tool = c.tool
                            if (tool == Tool.LASER) continue
                            val erasing = tool == Tool.ERASER || down.type == PointerType.Eraser ||
                                ev.buttons.isSecondaryPressed
                            val radius = Sizes.width(Tool.ERASER, c.sizeFor(Tool.ERASER))
                            c.lastInkAt = System.currentTimeMillis()
                            s.beginEdit()
                            live.clear()
                            fun add(o: Offset, p: Float) {
                                val pt = toPage(o)
                                if (erasing) s.eraseAt(b.page, Offset(pt.x, pt.y), radius)
                                else live.add(Pt(pt.x, pt.y, p))
                            }
                            val speedW = SpeedWidth()
                            fun pressureAt(ch: PointerInputChange): Float =
                                PenInput.pressureFor(ch.type, ch.pressure, tool, speedW, ch.position.x, ch.position.y, ch.uptimeMillis, size.width.toFloat())
                            add(down.position, pressureAt(down))
                            down.consume()
                            tick++
                            try {
                                while (true) {
                                    val e = awaitPointerEvent()
                                    val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!ch.pressed) break
                                    val pr = pressureAt(ch)
                                    for (h in ch.historical) add(h.position, pr)
                                    add(ch.position, pr)
                                    ch.consume()
                                    c.lastInkAt = System.currentTimeMillis()
                                    tick++
                                }
                            } finally {
                                var snap: List<Pt>? = null
                                if (!erasing && live.isNotEmpty()) {
                                    val color = c.colorFor(tool)
                                    val width = Sizes.width(tool, c.sizeFor(tool))
                                    if (tool == Tool.HIGHLIGHTER && c.highlightSnap) snap = live.toList()
                                    else s.addInk(b.page, Ink(PenPrefs.taper(live.toList(), tool), color, width, tool, System.currentTimeMillis(), if (tool.isPen()) PenPrefs.lineStyle else LineStyle.SOLID))
                                }
                                live.clear()
                                tick++
                                s.endEdit()
                                if (snap != null) {
                                    s.snapHighlight(b.page, snap, c.colorFor(Tool.HIGHLIGHTER), Sizes.width(Tool.HIGHLIGHTER, c.sizeFor(Tool.HIGHLIGHTER)))
                                }
                            }
                        }
                    }
                }
        ) {
            @Suppress("UNUSED_VARIABLE")
            val t = tick
            val b = s.zoomBox ?: return@Canvas
            val k = size.width / b.w
            drawRect(if (night) Color(0xFF1E232B) else Color.White)
            val im = img
            if (im != null) {
                val bw = im.width.toFloat()
                val sx = (b.x * bw).roundToInt().coerceIn(0, im.width - 1)
                val sy = (b.y * bw).roundToInt().coerceIn(0, im.height - 1)
                val sw = (b.w * bw).roundToInt().coerceIn(1, im.width - sx)
                val sh = (b.h * bw).roundToInt().coerceIn(1, im.height - sy)
                drawImage(
                    im, IntOffset(sx, sy), IntSize(sw, sh), IntOffset.Zero,
                    IntSize(size.width.roundToInt(), (sh * size.width / sw).roundToInt()),
                    colorFilter = if (night) ColorFilter.colorMatrix(ColorMatrix(NightMatrix)) else null
                )
            }
            withTransform({ translate(-b.x * k, -b.y * k) }) {
                for (st in strokes) drawStroke(st, if (night) nightOf(st.color) else st.color, scaleW = k)
                if (live.isNotEmpty()) {
                    val tool = c.tool
                    val col = c.colorFor(tool)
                    drawInk(live, if (night) nightOf(col) else col, Sizes.width(tool, c.sizeFor(tool)), tool, scaleW = k, style = if (tool.isPen()) PenPrefs.lineStyle else LineStyle.SOLID)
                }
            }
            // خط أساس خفيف للكتابة
            drawLine(
                Palette.Gold.copy(alpha = 0.35f), Offset(0f, size.height * 0.78f),
                Offset(size.width, size.height * 0.78f), 1.5f
            )
        }
        Column(
            Modifier.width(56.dp).fillMaxHeight().padding(start = 8.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PanelBtn(R.drawable.ph_caret_down, -90f, L("يمين", "Right")) { move(zb.w * 0.85f, 0f) }
            PanelBtn(R.drawable.ph_caret_down, 90f, L("يسار", "Left")) { move(-zb.w * 0.85f, 0f) }
            PanelBtn(R.drawable.ph_caret_down, 0f, L("سطر جديد", "New line")) { move(0f, zb.h * 0.8f, resetX = true) }
            PanelBtn(R.drawable.ph_close, 0f, L("إغلاق النافذة المكبّرة", "Close zoom window")) { s.zoomBox = null }
        }
    }
}

@Composable
private fun PanelBtn(icon: Int, rotate: Float, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { PhIcon(icon, Palette.Navy, 20.dp, desc, Modifier.graphicsLayer { rotationZ = rotate }) }
}
