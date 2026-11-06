package com.hamishnotes.app

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** ما يُسحب من الدرج الجانبي إلى الصفحة. */
sealed class TrayPayload {
    class BuiltinSticker(val b: StickerStore.Builtin) : TrayPayload()
    class MySticker(val f: File) : TrayPayload()
    class Photo(val uri: Uri) : TrayPayload()
    /** ملاحظة لاصقة تُسحب من شريط ألوانها. */
    class Sticky(val color: Int) : TrayPayload()
}

/** حالة السحب من الدرج: العنصر وموضع الإصبع في النافذة وصورته المصغّرة. */
object TrayDrag {
    var payload by mutableStateOf<TrayPayload?>(null)
    var pos by mutableStateOf(Offset.Zero)
    var thumb by mutableStateOf<ImageBitmap?>(null)
    /** الصفحة تحت الإصبع الآن (لإظهار أنها هدف الإسقاط). */
    var overPage by mutableStateOf(-1)
    /** مكان الدرج في النافذة: الإفلات فوقه يلغي السحب. */
    var trayRect = androidx.compose.ui.geometry.Rect.Zero

    /** الصفحة ونقطة الإسقاط تحت الإصبع (null فوق الدرج أو خارج الصفحات). */
    fun target(s: DocSession): Pair<Int, Offset>? = if (trayRect.contains(pos)) null else s.pageAt(pos)

    fun cancel() {
        payload = null
        thumb = null
        overPage = -1
    }
}

private object TrayPrefs {
    private const val KEY_H = "tray_h"
    fun loadH(ctx: Context): Float = ctx.getSharedPreferences("hamish_ui", Context.MODE_PRIVATE).getFloat(KEY_H, 380f)
    fun saveH(ctx: Context, v: Float) = ctx.getSharedPreferences("hamish_ui", Context.MODE_PRIVATE).edit().putFloat(KEY_H, v).apply()
}

/** يضع العنصر على الصفحة: عند نقطة الإسقاط، أو وسط الجزء الظاهر من الصفحة الحالية. */
private fun placePayload(s: DocSession, p: TrayPayload, page: Int, at: Offset) {
    when (p) {
        is TrayPayload.BuiltinSticker -> StickerStore.render(p.b, 400)?.let { StickerStore.place(s, it, page, at) }
        is TrayPayload.MySticker -> StickerStore.place(s, p.f, page, at)
        is TrayPayload.Photo -> AppController.insertImage(s, p.uri, page, width = 0.45f, at = at)
        is TrayPayload.Sticky -> {
            val pageH = 1f / (s.pdf?.ratio(page) ?: 0.707f)
            s.addNote(newStickyAt(page, at.x, at.y, pageH))
            PenPrefs.stickyActive = false
        }
    }
}

/** عرض العنصر على الصفحة (بوحدات عرض الصفحة) ونسبة عرضه لارتفاعه، كما سيوضع عند الإفلات. */
private fun placedSize(p: TrayPayload, thumb: ImageBitmap?): Pair<Float, Float> {
    val aspect = if (thumb != null && thumb.height > 0) thumb.width.toFloat() / thumb.height else 1f
    return when (p) {
        is TrayPayload.Photo -> 0.45f to aspect
        is TrayPayload.BuiltinSticker -> (if (aspect > 1.5f) 0.26f else 0.16f) to aspect
        is TrayPayload.MySticker -> 0.16f to aspect
        is TrayPayload.Sticky -> 0.2f to 1f
    }
}

/** اتجاه السحب الذي يُخرج العنصر فوراً (الاتجاه الآخر يبقى للتمرير). */
enum class DragAxis { Horizontal, Vertical }

/**
 * سحب عنصر إلى الصفحة: السحب في اتجاه axis يُخرجه فوراً تحت الإصبع، أو ضغطة مطوّلة ثم سحب في أي اتجاه.
 * يتحرك مع الإصبع، والإفلات على أي صفحة يضعه هناك. ضغطة مطوّلة دون سحب = onLongStill.
 * يوضع بعد clickable في سلسلة المعدِّلات حتى لا تُحسب نهاية السحب نقرة.
 */
@Composable
fun Modifier.dragToPage(
    s: DocSession?,
    payload: TrayPayload,
    img: ImageBitmap?,
    axis: DragAxis = DragAxis.Horizontal,
    onStart: () -> Unit = {},
    onLongStill: (() -> Unit)? = null
): Modifier {
    val haptic = LocalHapticFeedback.current
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    if (s == null) return this
    return this.onGloballyPositioned { coords[0] = it }
        .pointerInput(payload, s, axis) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val c = coords[0] ?: return@awaitEachGesture
                val slop = viewConfiguration.touchSlop
                var started = false
                var moved = false
                var last = down.position
                fun begin(at: Offset) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStart()
                    started = true
                    TrayDrag.payload = payload
                    TrayDrag.thumb = img
                    TrayDrag.pos = c.localToWindow(at)
                    TrayDrag.overPage = -1
                }
                // المرحلة الأولى: سحب في الاتجاه المطلوب يبدأ فوراً، وضغطة مطوّلة تبدأ أيضاً
                val longAt = System.currentTimeMillis() + viewConfiguration.longPressTimeoutMillis
                while (!started) {
                    val left = longAt - System.currentTimeMillis()
                    val ev = if (left > 0) withTimeoutOrNull(left) { awaitPointerEvent() } else null
                    if (ev == null) {
                        begin(last)
                        break
                    }
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                    if (!ch.pressed || ch.isConsumed) return@awaitEachGesture
                    last = ch.position
                    val d = ch.position - down.position
                    if (d.getDistance() > slop) {
                        val horiz = kotlin.math.abs(d.x) > kotlin.math.abs(d.y)
                        if (horiz == (axis == DragAxis.Horizontal)) {
                            begin(ch.position)
                            ch.consume()
                            moved = true
                        } else {
                            return@awaitEachGesture
                        }
                    }
                }
                // المرحلة الثانية: العنصر يتبع الإصبع
                try {
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        ch.consume()
                        if (!ch.pressed) break
                        if (!moved && (ch.position - down.position).getDistance() > slop) moved = true
                        TrayDrag.pos = c.localToWindow(ch.position)
                        TrayDrag.overPage = TrayDrag.target(s)?.first ?: -1
                    }
                    val hit = TrayDrag.target(s)
                    TrayDrag.cancel()
                    if (!moved) {
                        onLongStill?.invoke()
                    } else if (hit != null) {
                        s.activePage = hit.first
                        placePayload(s, payload, hit.first, hit.second)
                    }
                } finally {
                    if (TrayDrag.payload === payload) TrayDrag.cancel()
                }
            }
        }
}

/**
 * نافذة الملصقات والصور: تخرج من زرها بحركة وتستقر تحته مباشرة، وتبقى مفتوحة أثناء العمل.
 * نقرة تضع العنصر وسط الصفحة الحالية، وسحبه جانباً (أو ضغطة مطوّلة) يُخرجه تحت الإصبع إلى أي صفحة.
 * يُغيَّر ارتفاعها بسحب حافتها السفلية.
 */
@Composable
fun BoxScope.SideTray(s: DocSession, root: LayoutCoordinates?) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    var lastMode by remember { mutableStateOf("stickers") }
    val cur = s.trayMode
    androidx.compose.runtime.SideEffect { if (cur != null) lastMode = cur }
    val mode = cur ?: lastMode
    val visible = cur != null
    var heightDp by remember { mutableStateOf(TrayPrefs.loadH(ctx)) }
    val widthDp = 300f
    LaunchedEffect(visible) {
        if (visible && s.summaryOpen) s.summaryOpen = false
        // بعد إغلاق النافذة لا يمنع مكانها القديم الإفلات على الصفحة
        if (!visible) TrayDrag.trayRect = androidx.compose.ui.geometry.Rect.Zero
    }

    // المكان: تحت الزر مباشرة، وسطها على الزر قدر الإمكان
    val anchorWin = s.trayAnchors[if (mode == "photos") "photos" else "stickers"]
    val r = root?.takeIf { it.isAttached }
    val rootW = r?.size?.width?.toFloat() ?: 0f
    val rootH = r?.size?.height?.toFloat() ?: 0f
    val wPx = with(density) { widthDp.dp.toPx() }
    val margin = with(density) { 8.dp.toPx() }
    val anchor = if (anchorWin != null && r != null) r.windowToLocal(Offset(anchorWin.center.x, anchorWin.bottom)) else null
    val ax = anchor?.x ?: (rootW - margin - wPx / 2f)
    val ay = (anchor?.y ?: with(density) { 70.dp.toPx() }) + with(density) { 6.dp.toPx() }
    val x = (ax - wPx / 2f).coerceIn(margin, (rootW - wPx - margin).coerceAtLeast(margin))
    val maxH = with(density) { ((rootH - ay - margin).coerceAtLeast(160f)).toDp().value }
    val hDp = heightDp.coerceIn(160f, maxH)
    val pivot = if (wPx > 0f) ((ax - x) / wPx).coerceIn(0f, 1f) else 0.5f
    val origin = androidx.compose.ui.graphics.TransformOrigin(pivot, 0f)

    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset { IntOffset(x.roundToInt(), ay.roundToInt()) },
        enter = androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(240), initialScale = 0.08f, transformOrigin = origin) +
            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(160)),
        exit = androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(200), targetScale = 0.08f, transformOrigin = origin) +
            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160))
    ) {
        Box(Modifier.width(widthDp.dp).height((hDp + 8f).dp)) {
            // سهم صغير يشير إلى الزر
            Box(
                Modifier.align(AbsoluteAlignment.TopLeft)
                    .absoluteOffset { IntOffset((pivot * wPx - with(density) { 7.dp.toPx() }).roundToInt(), 0) }
                    .size(14.dp).graphicsLayer { rotationZ = 45f; translationY = 4.dp.toPx() }
                    .background(Palette.Background)
            )
            Surface(
                Modifier.padding(top = 8.dp).fillMaxSize()
                    .onGloballyPositioned { c ->
                        TrayDrag.trayRect = androidx.compose.ui.geometry.Rect(
                            c.localToWindow(Offset.Zero),
                            c.localToWindow(Offset(c.size.width.toFloat(), c.size.height.toFloat()))
                        )
                    },
                shape = RoundedCornerShape(18.dp),
                color = Palette.Background,
                shadowElevation = 12.dp
            ) {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when (mode) {
                                    "stickers" -> L("الملصقات", "Stickers")
                                    "stickerPhoto" -> L("صورة للملصق", "Sticker photo")
                                    else -> L("الصور", "Photos")
                                },
                                fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Palette.Navy,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).clickable {
                                    s.trayMode = if (mode == "stickerPhoto") "stickers" else null
                                },
                                contentAlignment = Alignment.Center
                            ) { Text("✕", color = Palette.TextSecondary, fontSize = 15.sp) }
                        }
                        Text(
                            L("انقر للوضع في الصفحة · اسحب جانباً إلى المكان", "Tap to add · drag sideways to place"),
                            fontSize = 10.sp, lineHeight = 13.sp, color = Palette.TextSecondary
                        )
                        Spacer(Modifier.height(6.dp))
                        if (mode == "stickers") StickerTrayContent(s) else PhotoTrayContent(s, forSticker = mode == "stickerPhoto")
                    }
                    // الحافة السفلية: تكبير النافذة وتصغيرها
                    Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(16.dp)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(onDragEnd = { TrayPrefs.saveH(ctx, heightDp) }) { ch, dy ->
                                    ch.consume()
                                    heightDp = (heightDp.coerceAtMost(maxH) + with(density) { dy.toDp().value }).coerceIn(160f, maxH)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.width(44.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Palette.TextSecondary.copy(alpha = 0.35f)))
                    }
                }
            }
        }
    }
}

/**
 * العنصر المسحوب يتبع الإصبع (فوق كل شيء في شاشة القراءة): فوق صفحة يظهر بحجمه الحقيقي
 * في المكان الذي سيوضع فيه بالضبط، وخارج الصفحات يعود صغيراً.
 */
@Composable
fun BoxScope.TrayDragGhost(s: DocSession, root: LayoutCoordinates?) {
    val p = TrayDrag.payload ?: return
    val r = root ?: return
    if (!r.isAttached) return
    val density = LocalDensity.current
    val thumb = TrayDrag.thumb
    val green = Color(0xFF16A34A)
    val stickyColor = (p as? TrayPayload.Sticky)?.let { Color(it.color) }
    val page = TrayDrag.overPage
    val pr = if (page >= 0) s.pageWindowRects[page] else null
    val at = if (pr != null) TrayDrag.target(s)?.second else null
    if (pr != null && at != null && pr.width > 0f) {
        val pw = pr.width
        val (wu, aspect) = placedSize(p, thumb)
        val hu = wu / aspect
        val pageH = pr.height / pw
        val x = (at.x - wu / 2f).coerceIn(0f, (1f - wu).coerceAtLeast(0f))
        val y = (at.y - hu / 2f).coerceIn(0f, (pageH - hu).coerceAtLeast(0f))
        val local = r.windowToLocal(Offset(pr.left + x * pw, pr.top + y * pw))
        Box(
            Modifier.align(AbsoluteAlignment.TopLeft)
                .absoluteOffset { IntOffset(local.x.roundToInt(), local.y.roundToInt()) }
                .size(with(density) { (wu * pw).toDp() }, with(density) { (hu * pw).toDp() })
                .graphicsLayer { alpha = 0.85f }
                .then(if (stickyColor != null) Modifier.shadow(4.dp, RoundedCornerShape(4.dp)).background(stickyColor, RoundedCornerShape(4.dp)) else Modifier)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        green,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                        )
                    )
                }
        ) {
            if (thumb != null && stickyColor == null) Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        }
        return
    }
    val local = r.windowToLocal(TrayDrag.pos)
    val half = with(density) { 38.dp.toPx() }
    Box(
        Modifier.align(AbsoluteAlignment.TopLeft)
            .absoluteOffset { IntOffset((local.x - half).roundToInt(), (local.y - half).roundToInt()) }
            .size(76.dp)
            .graphicsLayer { alpha = 0.9f; scaleX = 1.08f; scaleY = 1.08f }
            .shadow(10.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(stickyColor ?: if (p is TrayPayload.Photo) Color(0xFFEEF0F4) else Color.White.copy(alpha = 0.85f))
            .border(2.dp, Palette.Navy, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (thumb != null && stickyColor == null) {
            Image(thumb, null, Modifier.fillMaxSize().padding(if (p is TrayPayload.Photo) 0.dp else 6.dp), contentScale = if (p is TrayPayload.Photo) ContentScale.Crop else ContentScale.Fit)
        }
    }
}

/**
 * مربع في الدرج: نقرة = onTap، وضغطة مطوّلة ثم سحب = إسقاط على الصفحة.
 * ضغطة مطوّلة دون سحب = onLongStill (مثل حذف ملصق من «ملصقاتي»).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrayTile(
    s: DocSession,
    payload: TrayPayload,
    img: ImageBitmap?,
    photo: Boolean = false,
    onTap: () -> Unit,
    onLongStill: (() -> Unit)? = null
) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f)
            .clip(RoundedCornerShape(if (photo) 8.dp else 12.dp))
            .background(if (photo) Color(0xFFEEF0F4) else Color.White)
            .then(if (photo) Modifier else Modifier.border(1.dp, Palette.Divider, RoundedCornerShape(12.dp)))
            .graphicsLayer { alpha = if (TrayDrag.payload === payload) 0.35f else 1f }
            .combinedClickable(onClick = onTap)
            .dragToPage(s, payload, img, onLongStill = onLongStill),
        contentAlignment = Alignment.Center
    ) {
        img?.let {
            Image(
                it, null,
                Modifier.fillMaxSize().padding(if (photo) 0.dp else 6.dp),
                contentScale = if (photo) ContentScale.Crop else ContentScale.Fit
            )
        }
    }
}

@Composable
private fun TrayAddTile(label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Palette.GoldSoft)
            .border(1.5.dp, Palette.Gold, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, color = Palette.GoldDeep, textAlign = TextAlign.Center) }
}

@Composable
private fun StickerTrayContent(s: DocSession) {
    val cats = StickerStore.categories
    var tab by remember { mutableStateOf(if (StickerStore.mine.isNotEmpty()) -1 else 0) }
    var deleting by remember { mutableStateOf<File?>(null) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        ChoiceChip("★ " + L("ملصقاتي", "Mine"), tab == -1) { tab = -1 }
        cats.forEachIndexed { i, (name, _) -> ChoiceChip(name, tab == i) { tab = i } }
    }
    if (tab == -1) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(62.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item { TrayAddTile("＋\n" + L("من صورة", "Photo")) { s.trayMode = "stickerPhoto" } }
            item {
                TrayAddTile("＋\n" + L("من رسمي", "Drawing")) {
                    AppController.messages.tryEmit(
                        L("حدّد رسمك بأداة التحديد الحر، ثم اضغط «☺ ملصق»", "Select your drawing with the lasso, then tap “☺ Sticker”")
                    )
                }
            }
            items(StickerStore.mine.toList(), key = { it.name }) { f ->
                val img by produceState<ImageBitmap?>(null, f) {
                    value = withContext(Dispatchers.IO) { android.graphics.BitmapFactory.decodeFile(f.path)?.asImageBitmap() }
                }
                val payload = remember(f) { TrayPayload.MySticker(f) }
                TrayTile(s, payload, img, onTap = { StickerStore.place(s, f) }, onLongStill = { deleting = f })
            }
        }
    } else {
        val list = cats.getOrNull(tab)?.second ?: emptyList()
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (list.firstOrNull()?.kind == 't') 96.dp else 56.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(list) { b ->
                val img = remember(b) { StickerStore.render(b, 200)?.asImageBitmap() }
                val payload = remember(b) { TrayPayload.BuiltinSticker(b) }
                TrayTile(s, payload, img, onTap = { StickerStore.render(b, 400)?.let { StickerStore.place(s, it) } })
            }
        }
    }
    deleting?.let { f ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(L("حذف الملصق؟", "Delete sticker?")) },
            text = { Text(L("يُحذف من «ملصقاتي». ما وضعته منه على الصفحات يبقى.", "It is removed from “My stickers”. Copies already on pages stay.")) },
            confirmButton = { TextButton(onClick = { StickerStore.delete(f); deleting = null }) { Text(L("حذف", "Delete"), color = Palette.Danger) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(L("إلغاء", "Cancel")) } }
        )
    }
}

/** درج الصور: «من الملفات» في الأعلى، ثم صور الجهاز (الأحدث أولًا) مع اختيار الألبوم. */
@Composable
private fun PhotoTrayContent(s: DocSession, forSticker: Boolean) {
    val ctx = LocalContext.current
    var access by remember { mutableStateOf(PhotoLibrary.access(ctx)) }
    var refresh by remember { mutableStateOf(0) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = PhotoLibrary.access(ctx)
        refresh++
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        if (forSticker) {
            StickerStore.addFromUri(uris.first())
            s.trayMode = "stickers"
        } else {
            val page = s.currentPage()
            val at = s.visibleCenter(page)
            uris.forEachIndexed { i, u -> AppController.insertImage(s, u, page, index = i, quiet = i > 0, at = at) }
        }
    }
    LaunchedEffect(Unit) { if (access == 0) permLauncher.launch(PhotoLibrary.permissions()) }
    val photos by produceState<List<GalleryPhoto>?>(null, access, refresh) {
        value = if (access == 0) emptyList() else withContext(Dispatchers.IO) { PhotoLibrary.query(ctx) }
    }
    var album by remember { mutableStateOf<String?>(null) }
    var albumsOpen by remember { mutableStateOf(false) }
    val all = photos ?: emptyList()
    val shown = when (album) {
        null -> all
        "cam" -> all.filter { it.bucket.equals("Camera", true) || it.bucket.contains("DCIM", true) }
        "shot" -> all.filter { it.bucket.contains("screenshot", true) }
        else -> all.filter { it.bucket == album }
    }

    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Palette.Navy)
                .clickable { filePicker.launch(arrayOf("image/*")) }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) { Text(L("من الملفات", "From files"), fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1) }
        if (access != 0) {
            Box {
                val label = when (album) {
                    null -> L("الأحدث", "Recent")
                    "cam" -> L("الكاميرا", "Camera")
                    "shot" -> L("لقطات", "Screens")
                    else -> album ?: ""
                }
                ChoiceChip("$label ▾", album != null) { albumsOpen = true }
                DropdownMenu(expanded = albumsOpen, onDismissRequest = { albumsOpen = false }) {
                    DropdownMenuItem(text = { Text(L("الأحدث", "Recent")) }, onClick = { album = null; albumsOpen = false })
                    DropdownMenuItem(text = { Text(L("الكاميرا", "Camera")) }, onClick = { album = "cam"; albumsOpen = false })
                    DropdownMenuItem(text = { Text(L("لقطات الشاشة", "Screenshots")) }, onClick = { album = "shot"; albumsOpen = false })
                    all.groupBy { it.bucket }.filterKeys { it.isNotBlank() }.toList().sortedByDescending { it.second.size }.forEach { (b, l) ->
                        DropdownMenuItem(text = { Text("$b (${l.size})") }, onClick = { album = b; albumsOpen = false })
                    }
                }
            }
        }
    }
    if (access == 1) {
        Text(
            L("بعض الصور فقط · المس للمزيد", "Some photos only · tap for more"),
            fontSize = 11.sp, color = Palette.GoldDeep, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp).clip(RoundedCornerShape(8.dp)).background(Palette.GoldSoft)
                .clickable { permLauncher.launch(PhotoLibrary.permissions()) }.padding(6.dp)
        )
    }
    when {
        access == 0 -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                L("اسمح لهامش بعرض صورك لتسحبها مباشرة.", "Allow Hamish to show your photos so you can drag them in."),
                fontSize = 12.sp, color = Palette.TextSecondary, textAlign = TextAlign.Center
            )
            TextButton(onClick = { permLauncher.launch(PhotoLibrary.permissions()) }) {
                Text(L("السماح بعرض الصور", "Allow photos"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        photos == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.Navy) }
        shown.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(L("لا توجد صور هنا", "No photos here"), fontSize = 12.sp, color = Palette.TextSecondary)
        }
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(66.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(shown, key = { it.uri.toString() }) { p ->
                val img by produceState<ImageBitmap?>(null, p.uri) { value = PhotoLibrary.thumbnail(ctx, p.uri) }
                val payload = remember(p.uri) { TrayPayload.Photo(p.uri) }
                if (forSticker) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(Color(0xFFEEF0F4))
                            .clickable { StickerStore.addFromUri(p.uri); s.trayMode = "stickers" }
                    ) { img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
                } else {
                    TrayTile(s, payload, img, photo = true, onTap = {
                        val page = s.currentPage()
                        AppController.insertImage(s, p.uri, page, width = 0.45f, at = s.visibleCenter(page))
                    })
                }
            }
        }
    }
}
