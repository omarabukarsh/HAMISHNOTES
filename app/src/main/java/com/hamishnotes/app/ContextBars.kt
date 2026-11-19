package com.hamishnotes.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/*
 * الأشرطة فوق العنصر المحدد (تحديد نص، تحديد حر، صورة، مربع نص، ملاحظة، لصق):
 * تظهر فوق العنصر مباشرة بسهم صغير يشير إليه، وتحته إن لم يتسع فوقه، وتتبعه مع التمرير والتكبير.
 *
 * المقاسات موحّدة: كل زر بارتفاع 34 وحشوة 11 على الجانبين، والمسافة بين أي زرين 4،
 * والفراغ حول الأزرار داخل الشريط 4 من كل جهة، وزاوية الشريط = زاوية الزر (10) + 4.
 */

internal val CtxShape = RoundedCornerShape(14.dp)
private val CtxBtnShape = RoundedCornerShape(10.dp)

/** جسم الشريط: الأزرار في صف واحد بمسافات متساوية (يتمرر إن لم يتسع). */
@Composable
internal fun CtxBar(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.shadow(8.dp, CtxShape).background(Palette.ChromeBg, CtxShape).clip(CtxShape).blockTouches()
            .horizontalScroll(rememberScrollState()).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/** زر في الشريط: عادي، أو أساسي بخلفية كحلية — بنفس المقاس في الحالتين. */
@Composable
internal fun CtxBtn(
    label: String,
    primary: Boolean = false,
    color: Color = Palette.ChromeFg,
    onClick: () -> Unit
) {
    Box(
        Modifier.height(34.dp).clip(CtxBtnShape)
            .background(if (primary) Palette.Navy else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, fontSize = 13.5.sp, maxLines = 1,
            color = if (primary) Color.White else color,
            fontWeight = if (primary) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/** «⋯»: بقية الأوامر في قائمة صغيرة. */
@Composable
internal fun CtxMore(content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        CtxBtn("⋯") { open = true }
        DropdownMenu(
            expanded = open, onDismissRequest = { open = false },
            shape = RoundedCornerShape(14.dp), containerColor = Color.White, shadowElevation = 12.dp
        ) { content { open = false } }
    }
}

@Composable
internal fun CtxItem(label: String, color: Color = Palette.TextPrimary, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label, color = color, fontSize = 14.sp) }, onClick = onClick)
}

/** سهم صغير (مثلث) أسفل الشريط أو أعلاه. */
private fun arrowShape(down: Boolean) = GenericShape { size, _ ->
    if (down) {
        moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2f, size.height)
    } else {
        moveTo(size.width / 2f, 0f); lineTo(size.width, size.height); lineTo(0f, size.height)
    }
    close()
}

/**
 * يضع الشريط فوق المستطيل rect (بوحدات عرض الصفحة) في الصفحة page.
 * preferBelow: يُفضّل تحت العنصر (مثل «تم التحويل» تحت السطر).
 */
@Composable
internal fun AnchoredBar(
    s: DocSession,
    page: Int,
    rect: Rect,
    preferBelow: Boolean = false,
    bar: @Composable () -> Unit
) {
    var host by remember { mutableStateOf(Offset.Zero) }
    val visible = s.listState.layoutInfo.visibleItemsInfo.any { it.index == page }
    var down by remember { mutableStateOf(true) }
    Layout(
        content = {
            Box(Modifier.size(width = 16.dp, height = 8.dp).background(Palette.ChromeBg, arrowShape(down)))
            bar()
        },
        modifier = Modifier.fillMaxSize().onGloballyPositioned { host = it.localToWindow(Offset.Zero) }
    ) { ms, cons ->
        val margin = 8.dp.roundToPx()
        val gap = 10.dp.roundToPx()
        val b = ms[1].measure(Constraints(maxWidth = (cons.maxWidth - margin * 2).coerceAtLeast(0)))
        val a = ms[0].measure(Constraints())
        layout(cons.maxWidth, cons.maxHeight) {
            // قراءة العدّاد هنا تعيد التموضع (دون إعادة بناء الشريط) كلما تحركت الصفحات
            s.pageRectsVersion
            val pageRect = s.pageWindowRects[page] ?: return@layout
            val vp = s.pagesViewport
            if (!visible || pageRect.width <= 0f) return@layout
            val k = pageRect.width
            // العنصر في إحداثيات هذا الصندوق
            val l = pageRect.left + rect.left * k - host.x
            val r = pageRect.left + rect.right * k - host.x
            val t = pageRect.top + rect.top * k - host.y
            val btm = pageRect.top + rect.bottom * k - host.y
            val top = (if (vp.isEmpty) 0f else vp.top - host.y) + margin
            val bottom = (if (vp.isEmpty) cons.maxHeight.toFloat() else vp.bottom - host.y) - margin
            if (btm < top || t > bottom) return@layout // العنصر خارج الشاشة
            val above = t - gap - b.height
            val below = btm + gap
            val useBelow = if (preferBelow) below + b.height <= bottom || above < top else above < top
            if (down == useBelow) down = !useBelow
            val y = (if (useBelow) below else above).coerceIn(top, (bottom - b.height).coerceAtLeast(top))
            val cx = (l + r) / 2f
            val x = (cx - b.width / 2f).coerceIn(margin.toFloat(), (cons.maxWidth - margin - b.width).toFloat().coerceAtLeast(margin.toFloat()))
            val ax = (cx - a.width / 2f).coerceIn(x + 12.dp.toPx(), x + b.width - 12.dp.toPx() - a.width)
            val ay = if (useBelow) y - a.height + 1f else y + b.height - 1f
            b.place(x.roundToInt(), y.roundToInt())
            a.place(ax.roundToInt(), ay.roundToInt(), zIndex = 1f)
        }
    }
}

/** حدود التحديد الحر (خطوط، صور، مربعات نص) بوحدات عرض الصفحة. */
internal fun DocSession.lassoRect(): Rect? {
    val sel = lassoSel ?: return null
    var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
    fun add(x: Float, y: Float) { l = minOf(l, x); t = minOf(t, y); r = maxOf(r, x); b = maxOf(b, y) }
    sel.strokes.forEach { st -> st.pts.forEach { add(it.x, it.y) } }
    (images[sel.page] ?: emptyList()).filter { it.id in sel.imageIds }.forEach { add(it.x, it.y); add(it.x + it.w, it.y + it.w / it.aspect) }
    (texts[sel.page] ?: emptyList()).filter { it.id in sel.textIds }.forEach { add(it.x, it.y); add(it.x + it.w, it.y + textHeightOf(it)) }
    return if (l > r) null else Rect(l, t, r, b)
}

/** حدود النص المحدد في صفحة PDF. */
internal fun DocSession.selectionBounds(): Rect? {
    val sel = selection ?: return null
    val words = ocr[sel.page]?.words ?: return null
    val a = minOf(sel.start, sel.end).coerceAtLeast(0)
    val z = maxOf(sel.start, sel.end).coerceAtMost(words.lastIndex)
    if (a > z) return null
    var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
    for (i in a..z) { val w = words[i]; l = minOf(l, w.l); t = minOf(t, w.t); r = maxOf(r, w.r); b = maxOf(b, w.b) }
    return Rect(l, t, r, b)
}
