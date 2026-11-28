package com.mynotes.app

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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.graphicsLayer
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
    val reveal = LocalCtxReveal.current
    Row(
        Modifier.shadow(8.dp, CtxShape).background(Palette.ChromeBg, CtxShape).clip(CtxShape).blockTouches()
            .horizontalScroll(rememberScrollState()).padding(4.dp)
    ) {
        // الأزرار تظهر بعد أن تتمدد القطرة إلى الشريط
        Row(
            Modifier.graphicsLayer { alpha = reveal(); translationY = (1f - reveal()) * 4.dp.toPx() },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/** ظهور محتوى الشريط (0..1) أثناء حركة القطرة. */
internal val LocalCtxReveal = androidx.compose.runtime.staticCompositionLocalOf<() -> Float> { { 1f } }

/**
 * حركة «قطرة الماء» (كفيديو التصميم drop_animation): نقطة صغيرة عند رأس السهم تتمدد بسرعة إلى شريط فارغ
 * بعرضه الكامل مع ارتداد خفيف، ويبقى فارغًا لحظة، ثم تظهر الأزرار، ثم السهم. بلا حركة رأسية تُذكر.
 * p من 0 إلى 1 (750ms خطيًا) → (عرض، ارتفاع، إزاحة رأسية بوحدات dp، شفافية).
 */
private val dropKeys = arrayOf(
    floatArrayOf(0f, 0.03f, 0.25f, 0f, 0f),
    floatArrayOf(0.05f, 0.05f, 0.40f, 0f, 1f),
    floatArrayOf(0.12f, 0.42f, 0.85f, 0f, 1f),
    floatArrayOf(0.20f, 1.04f, 0.95f, 0f, 1f),
    floatArrayOf(0.27f, 0.99f, 1.02f, 0f, 1f),
    floatArrayOf(0.33f, 1f, 1f, 0f, 1f),
    floatArrayOf(1f, 1f, 1f, 0f, 1f)
)

/** الأزرار تظهر بعد أن يبقى الشريط فارغًا لحظة (62%..85% من الحركة). */
internal fun dropReveal(p: Float): Float = ((p - 0.62f) / 0.23f).coerceIn(0f, 1f)

/** السهم آخر شيء (82%..100%). */
internal fun dropArrow(p: Float): Float = ((p - 0.82f) / 0.18f).coerceIn(0f, 1f)

internal fun dropAt(p: Float): FloatArray {
    val q = p.coerceIn(0f, 1f)
    for (i in 1 until dropKeys.size) {
        val a = dropKeys[i - 1]; val b = dropKeys[i]
        if (q <= b[0]) {
            val t = if (b[0] == a[0]) 1f else (q - a[0]) / (b[0] - a[0])
            return FloatArray(4) { j -> a[j + 1] + (b[j + 1] - a[j + 1]) * t }
        }
    }
    return floatArrayOf(1f, 1f, 0f, 1f)
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
            shape = RoundedCornerShape(14.dp), containerColor = Palette.Surface, shadowElevation = 12.dp
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
    /** false: الشريط يرجع قطرةً ويختفي (أثناء السحب، أو قبل اكتمال التحديد). */
    visible: Boolean = true,
    bar: @Composable () -> Unit
) {
    var host by remember { mutableStateOf(Offset.Zero) }
    // تقدّم حركة القطرة: 0 مخفي، 1 ظاهر بالكامل
    val prog = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            // يظهر بعد اكتمال التحديد بلحظة قصيرة
            if (prog.value == 0f) kotlinx.coroutines.delay(120)
            prog.animateTo(1f, androidx.compose.animation.core.tween(750, easing = androidx.compose.animation.core.LinearEasing))
        } else {
            prog.animateTo(0f, androidx.compose.animation.core.tween(200, easing = androidx.compose.animation.core.FastOutLinearInEasing))
        }
    }
    val visible = s.listState.layoutInfo.visibleItemsInfo.any { it.index == page }
    var down by remember { mutableStateOf(true) }
    if (!visible && prog.value == 0f) return
    val reveal = remember { { dropReveal(prog.value) } }
    Layout(
        content = {
            Box(Modifier.size(width = 16.dp, height = 8.dp).background(Palette.ChromeBg, arrowShape(down)))
            androidx.compose.runtime.CompositionLocalProvider(LocalCtxReveal provides reveal) { bar() }
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
            // القطرة تنبع من رأس السهم
            val d = dropAt(prog.value)
            val ox = if (b.width > 0) ((ax + a.width / 2f - x) / b.width).coerceIn(0f, 1f) else 0.5f
            val dy = d[2] * density * (if (useBelow) -1f else 1f)
            b.placeWithLayer(x.roundToInt(), y.roundToInt()) {
                scaleX = d[0]; scaleY = d[1]; translationY = dy; alpha = d[3]
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(ox, if (useBelow) 0f else 1f)
            }
            a.placeWithLayer(ax.roundToInt(), ay.roundToInt(), zIndex = 1f) {
                alpha = dropArrow(prog.value)
                scaleY = 0.4f + 0.6f * dropArrow(prog.value)
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, if (useBelow) 1f else 0f)
            }
        }
    }
}

/** حدود التحديد الحر (خطوط، صور، مربعات نص) بوحدات عرض الصفحة. */
internal fun DocSession.lassoRect(): Rect? {
    // الحدود محفوظة بعد أول حساب (لا تُعاد على كل النقاط مع كل إطار)
    val bb = lassoBounds() ?: return null
    return Rect(bb[0], bb[1], bb[2], bb[3])
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
