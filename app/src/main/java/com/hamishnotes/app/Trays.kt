package com.hamishnotes.app

import android.content.Context
import android.net.Uri
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
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.gestures.scrollBy
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
    /** مكان الشريط العائم الذي سُحب منه العنصر (مثل شريط الملاحظات): الإفلات فوقه يلغي السحب أيضًا. */
    var barRect by mutableStateOf(androidx.compose.ui.geometry.Rect.Zero)

    /** الإصبع فوق مكان يلغي السحب عند الإفلات (الدرج أو الشريط الذي خرج منه العنصر). */
    fun overCancel(): Boolean = trayRect.contains(pos) || barRect.contains(pos)

    /**
     * بعد الإفلات: العنصر يبقى ظاهراً حيث أُفلت حتى يُضاف فعلاً إلى الصفحة
     * (نسخ الصورة يأخذ لحظة)، فلا يختفي ثم يظهر فجأة.
     */
    class Landing(val payload: TrayPayload, val thumb: ImageBitmap?, val page: Int, val at: Offset) {
        var done by mutableStateOf(false)
    }
    var landing by mutableStateOf<Landing?>(null)

    /**
     * عنصر اختير بنقرة (مثل الملاحظات اللاصقة): النقرة التالية على الصفحة تضعه في مكانها،
     * دون إبقاء الإصبع مضغوطاً. نقرة ثانية على نفس العنصر تلغي الاختيار.
     */
    var armed by mutableStateOf<TrayPayload?>(null)
    private var armedThumb: ImageBitmap? = null

    fun arm(p: TrayPayload, img: ImageBitmap?) {
        if (armed === p) {
            armed = null
            return
        }
        armed = p
        armedThumb = img
        AppController.say(MsgKind.TIP, L("انقر على الصفحة حيث تريد وضعه", "Tap the page where you want to place it"))
    }

    /** يضع العنصر المختار عند النقطة (بوحدات عرض الصفحة). false = لا عنصر مختار. */
    fun placeArmed(s: DocSession, page: Int, at: Offset): Boolean {
        val p = armed ?: return false
        armed = null
        drop(s, p, armedThumb, page, at)
        return true
    }

    /** التمرير التلقائي عند الحافة مسموح بعد أن يمر الإصبع فوق وسط الصفحات مرة. */
    internal var scrollArmed = false

    /** يضع العنصر على الصفحة ثم يستدعي onDone (يُستبدل في الاختبارات). */
    internal var placer: (DocSession, TrayPayload, Int, Offset, () -> Unit) -> Unit = ::placePayload

    /** الصفحة ونقطة الإسقاط تحت الإصبع (null فوق الدرج أو خارج الصفحات). */
    fun target(s: DocSession): Pair<Int, Offset>? = if (overCancel()) null else s.pageAt(pos)

    fun cancel() {
        payload = null
        thumb = null
        overPage = -1
        scrollArmed = false
    }

    /** إفلات العنصر على الصفحة: يبقى ظاهراً في مكانه حتى تكتمل إضافته. */
    fun drop(s: DocSession, p: TrayPayload, img: ImageBitmap?, page: Int, at: Offset) {
        val land = Landing(p, img, page, at)
        landing = land
        cancel()
        s.activePage = page
        placer(s, p, page, at) { land.done = true }
    }
}

private object TrayPrefs {
    // مفتاح جديد: الارتفاع المحفوظ أثناء خطأ مكان النافذة كان محصوراً في الحد الأدنى
    private const val KEY_H = "tray_h2"
    fun loadH(ctx: Context): Float = ctx.getSharedPreferences("hamish_ui", Context.MODE_PRIVATE).getFloat(KEY_H, 380f)
    fun saveH(ctx: Context, v: Float) = ctx.getSharedPreferences("hamish_ui", Context.MODE_PRIVATE).edit().putFloat(KEY_H, v).apply()
}

/** يضع العنصر على الصفحة عند نقطة الإسقاط (وسطه عندها)، ثم onDone بعد إضافته فعلاً. */
private fun placePayload(s: DocSession, p: TrayPayload, page: Int, at: Offset, onDone: () -> Unit) {
    when (p) {
        is TrayPayload.BuiltinSticker -> StickerStore.render(p.b, 400)?.let { StickerStore.place(s, it, page, at, onDone) } ?: onDone()
        is TrayPayload.MySticker -> StickerStore.place(s, p.f, page, at, onDone)
        is TrayPayload.Photo -> AppController.insertImage(s, p.uri, page, width = 0.45f, at = at, onDone = onDone)
        is TrayPayload.Sticky -> {
            val pageH = 1f / (s.pdf?.ratio(page) ?: 0.707f)
            s.addNote(newStickyAt(page, at.x, at.y, pageH))
            PenPrefs.stickyActive = false
            onDone()
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
                    if (!moved) {
                        TrayDrag.cancel()
                        onLongStill?.invoke()
                    } else if (hit != null) {
                        TrayDrag.drop(s, payload, TrayDrag.thumb ?: img, hit.first, hit.second)
                    } else {
                        TrayDrag.cancel()
                    }
                } finally {
                    if (TrayDrag.payload === payload) TrayDrag.cancel()
                }
            }
        }
}

/** مكان النافذة المنبثقة (بالبكسل داخل الشاشة) وأقصى ارتفاع لها ومكان السهم (0..1 من عرضها). */
internal class TrayPlace(val x: Float, val y: Float, val maxH: Float, val pivot: Float)

/**
 * النافذة تحت زرها: وسطها على الزر، داخل الشاشة بهامش. بلا زر معروف: أعلى اليمين.
 * anchorX/anchorBottom: وسط الزر وأسفله بإحداثيات الشاشة نفسها.
 */
internal fun trayPlacement(
    hostW: Float, hostH: Float, anchorX: Float?, anchorBottom: Float?,
    wPx: Float, margin: Float, gap: Float, fallbackTop: Float
): TrayPlace {
    val ax = anchorX ?: (hostW - margin - wPx / 2f)
    val ay = (anchorBottom ?: fallbackTop) + gap
    val x = (ax - wPx / 2f).coerceIn(margin, (hostW - wPx - margin).coerceAtLeast(margin))
    val maxH = (hostH - ay - margin).coerceAtLeast(0f)
    val pivot = if (wPx > 0f) ((ax - x) / wPx).coerceIn(0.06f, 0.94f) else 0.5f
    return TrayPlace(x, ay, maxH, pivot)
}

/** حدود الصندوق الأب في النافذة كحالة: تتحدث عند أول قياس وعند كل تغيّر (دوران الشاشة مثلاً). */
@Composable
private fun BoxScope.rememberHostRect(): androidx.compose.ui.geometry.Rect {
    var host by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    Box(Modifier.matchParentSize().onGloballyPositioned { c ->
        val p = c.positionInWindow()
        val r = androidx.compose.ui.geometry.Rect(p.x, p.y, p.x + c.size.width, p.y + c.size.height)
        if (r != host) host = r
    })
    return host
}

/**
 * نافذة الملصقات والصور: تخرج من زرها بحركة وتستقر تحته مباشرة، وتبقى مفتوحة أثناء العمل.
 * نقرة تضع العنصر وسط الصفحة الحالية، وسحبه جانباً (أو ضغطة مطوّلة) يُخرجه تحت الإصبع إلى أي صفحة.
 * يُغيَّر ارتفاعها بسحب حافتها السفلية.
 */
@Composable
fun BoxScope.SideTray(s: DocSession) {
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
        // إغلاق النافذة يلغي العنصر المختار (حتى لا توضعه نقرة لاحقة على الصفحة)
        if (!visible) TrayDrag.armed = null
    }

    // المكان: تحت الزر مباشرة، وسطها على الزر قدر الإمكان. مقاس الشاشة ومكانها يُقرآن كحالة
    // (لا من إحداثيات قديمة)، فتنتقل النافذة إلى مكانها الصحيح فور معرفتهما.
    val host = rememberHostRect()
    val anchorWin = s.trayAnchors[if (mode == "photos") "photos" else "stickers"]
    val conf = androidx.compose.ui.platform.LocalConfiguration.current
    val hostW = if (host.width > 0f) host.width else with(density) { conf.screenWidthDp.dp.toPx() }
    val hostH = if (host.height > 0f) host.height else with(density) { conf.screenHeightDp.dp.toPx() }
    val wPx = with(density) { widthDp.dp.toPx() }
    val place = trayPlacement(
        hostW, hostH,
        anchorWin?.let { it.center.x - host.left }, anchorWin?.let { it.bottom - host.top },
        wPx, with(density) { 8.dp.toPx() }, with(density) { 6.dp.toPx() }, with(density) { 70.dp.toPx() }
    )
    val x = place.x
    val ay = place.y
    // أقصى ارتفاع بوحدة dp، ولا يقل عن 160
    val maxH = with(density) { place.maxH.toDp().value }.coerceAtLeast(160f)
    val hDp = heightDp.coerceIn(160f, maxH)
    val pivot = place.pivot
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

/** مستطيل العنصر المسحوب بإحداثيات النافذة: وسطه تحت الإصبع دائماً. */
internal fun ghostRect(finger: Offset, w: Float, h: Float) =
    androidx.compose.ui.geometry.Rect(finger.x - w / 2f, finger.y - h / 2f, finger.x + w / 2f, finger.y + h / 2f)

/** مكان العنصر بعد وضعه على الصفحة (كما تضعه insertImage): وسطه عند النقطة، داخل حدود الصفحة. */
internal fun landedRect(page: androidx.compose.ui.geometry.Rect, at: Offset, wu: Float, hu: Float): androidx.compose.ui.geometry.Rect {
    val pw = page.width
    val pageH = page.height / pw
    val x = (at.x - wu / 2f).coerceIn(0f, (1f - wu).coerceAtLeast(0f))
    val y = (at.y - hu / 2f).coerceIn(0f, (pageH - hu).coerceAtLeast(0f))
    return androidx.compose.ui.geometry.Rect(page.left + x * pw, page.top + y * pw, page.left + (x + wu) * pw, page.top + (y + hu) * pw)
}

/** سرعة التمرير التلقائي (بكسل لكل إطار) والإصبع داخل منطقة الحافة العليا (سالبة) أو السفلى (موجبة) من الصفحات. */
internal fun edgeSpeed(finger: Offset, viewport: androidx.compose.ui.geometry.Rect, zone: Float, maxSpeed: Float): Float {
    if (viewport.height <= zone * 2f || !viewport.contains(finger)) return 0f
    val y = finger.y
    return when {
        y > viewport.bottom - zone -> maxSpeed * ((y - (viewport.bottom - zone)) / zone).coerceIn(0.15f, 1f)
        y < viewport.top + zone -> -maxSpeed * (((viewport.top + zone) - y) / zone).coerceIn(0.15f, 1f)
        else -> 0f
    }
}

/**
 * العنصر المسحوب يبقى تحت الإصبع من لحظة خروجه من النافذة إلى أن يُوضع (فوق كل شيء في شاشة القراءة):
 * فوق صفحة يكبر بسلاسة إلى حجمه الحقيقي، وخارجها يعود صغيراً. بعد الإفلات يبقى في مكانه حتى يظهر
 * العنصر الحقيقي على الصفحة. وقرب أعلى أو أسفل الصفحات تتحرك الصفحات وحدها.
 */
@Composable
fun BoxScope.TrayDragGhost(s: DocSession) {
    val host = rememberHostRect()
    val density = LocalDensity.current
    val dragging = TrayDrag.payload
    val land = TrayDrag.landing

    // تمرير تلقائي قرب حافتي الصفحات أثناء السحب (مفيد في الهاتف والشاشات الصغيرة):
    // يعمل فقط بعد أن يمر الإصبع فوق وسط الصفحات (لا عند الخروج من النافذة أعلى الصفحات)،
    // وفقط ما دام الإصبع عند الحافة والصفحات تستطيع التحرك
    val zone = with(density) { 72.dp.toPx() }
    val maxSpeed = with(density) { 18.dp.toPx() }
    val speedNow = if (dragging != null && !TrayDrag.overCancel()) edgeSpeed(TrayDrag.pos, s.pagesViewport, zone, maxSpeed) else 0f
    if (dragging != null && speedNow == 0f && s.pagesViewport.contains(TrayDrag.pos)) TrayDrag.scrollArmed = true
    val scrolling = speedNow != 0f && TrayDrag.scrollArmed
    LaunchedEffect(scrolling) {
        if (!scrolling) return@LaunchedEffect
        while (TrayDrag.payload != null) {
            androidx.compose.runtime.withFrameNanos { }
            if (TrayDrag.overCancel()) break
            val dy = edgeSpeed(TrayDrag.pos, s.pagesViewport, zone, maxSpeed)
            if (dy == 0f) break
            if (dy > 0f && !s.listState.canScrollForward || dy < 0f && !s.listState.canScrollBackward) break
            s.listState.scrollBy(dy)
            TrayDrag.overPage = TrayDrag.target(s)?.first ?: -1
        }
    }
    // بعد إضافة العنصر: لحظة قصيرة حتى تُرسم صورته على الصفحة، ثم يختفي الظل (وحد أقصى إن تأخرت الإضافة)
    if (land != null) {
        LaunchedEffect(land, land.done) {
            kotlinx.coroutines.delay(if (land.done) 300L else 8000L)
            if (TrayDrag.landing === land) TrayDrag.landing = null
        }
    }

    val p = dragging ?: land?.payload ?: return
    val thumb = if (dragging != null) TrayDrag.thumb else land?.thumb
    val (wu, aspect) = placedSize(p, thumb)
    val hu = wu / aspect
    val small = with(density) { 76.dp.toPx() }
    val target: androidx.compose.ui.geometry.Rect
    val onPage: Boolean
    if (dragging != null) {
        val pr = TrayDrag.overPage.takeIf { it >= 0 }?.let { s.pageWindowRects[it] }
        onPage = pr != null && pr.width > 0f
        target = if (onPage) ghostRect(TrayDrag.pos, wu * pr!!.width, hu * pr.width)
        else ghostRect(TrayDrag.pos, small, small)
    } else {
        val pr = s.pageWindowRects[land!!.page] ?: return
        if (pr.width <= 0f) return
        onPage = true
        target = landedRect(pr, land.at, wu, hu)
    }
    // الحجم يتغير بسلاسة، والموضع يتبع الإصبع فوراً أثناء السحب ثم ينزلق إلى مكانه بعد الإفلات
    val sizeSpec = androidx.compose.animation.core.spring<Float>(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)
    val posSpec: androidx.compose.animation.core.AnimationSpec<Float> =
        if (dragging != null) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(160)
    val gw by androidx.compose.animation.core.animateFloatAsState(target.width, sizeSpec, label = "ghostW")
    val gh by androidx.compose.animation.core.animateFloatAsState(target.height, sizeSpec, label = "ghostH")
    val cx by androidx.compose.animation.core.animateFloatAsState(target.center.x, posSpec, label = "ghostX")
    val cy by androidx.compose.animation.core.animateFloatAsState(target.center.y, posSpec, label = "ghostY")
    val left = cx - gw / 2f - host.left
    val top = cy - gh / 2f - host.top

    val green = Color(0xFF16A34A)
    val stickyColor = (p as? TrayPayload.Sticky)?.let { Color(it.color) }
    val shape = RoundedCornerShape(if (onPage) 4.dp else 12.dp)
    Box(
        Modifier.align(AbsoluteAlignment.TopLeft)
            .absoluteOffset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(with(density) { gw.toDp() }, with(density) { gh.toDp() })
            .testTag("trayGhost")
            .graphicsLayer { alpha = if (dragging != null) 0.9f else 1f }
            .shadow(if (dragging != null) 10.dp else 0.dp, shape)
            .clip(shape)
            .background(stickyColor ?: if (onPage) Color.Transparent else if (p is TrayPayload.Photo) Color(0xFFEEF0F4) else Color.White.copy(alpha = 0.85f))
            .then(
                if (dragging == null) Modifier
                else if (onPage) Modifier.drawWithContent {
                    drawContent()
                    drawRect(
                        green,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                        )
                    )
                } else Modifier.border(2.dp, Palette.Navy, shape)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (thumb != null && stickyColor == null) {
            Image(
                thumb, null,
                Modifier.fillMaxSize().padding(if (onPage || p is TrayPayload.Photo) 0.dp else 6.dp),
                contentScale = when {
                    onPage -> ContentScale.FillBounds
                    p is TrayPayload.Photo -> ContentScale.Crop
                    else -> ContentScale.Fit
                }
            )
        }
    }
}

/**
 * مربع في الدرج: نقرة = اختياره ثم نقرة على الصفحة تضعه، وضغطة مطوّلة ثم سحب = إسقاط على الصفحة.
 * ضغطة مطوّلة دون سحب = onLongStill (مثل حذف ملصق من «ملصقاتي»).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrayTile(
    s: DocSession,
    payload: TrayPayload,
    img: ImageBitmap?,
    photo: Boolean = false,
    onLongStill: (() -> Unit)? = null
) {
    val armed = TrayDrag.armed === payload
    val shape = RoundedCornerShape(if (photo) 8.dp else 12.dp)
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f)
            .clip(shape)
            .background(if (photo) Color(0xFFEEF0F4) else Color.White)
            .then(
                when {
                    armed -> Modifier.border(2.5.dp, Palette.Navy, shape)
                    photo -> Modifier
                    else -> Modifier.border(1.dp, Palette.Divider, shape)
                }
            )
            .graphicsLayer { alpha = if (TrayDrag.payload === payload) 0.35f else 1f }
            .combinedClickable(onClick = { TrayDrag.arm(payload, img) })
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
                    AppController.say(MsgKind.TIP, 
                        L("حدّد رسمك بأداة التحديد الحر، ثم اضغط «☺ ملصق»", "Select your drawing with the lasso, then tap “☺ Sticker”")
                    )
                }
            }
            items(StickerStore.mine.toList(), key = { it.name }) { f ->
                val img by produceState<ImageBitmap?>(null, f) {
                    value = withContext(Dispatchers.IO) { android.graphics.BitmapFactory.decodeFile(f.path)?.asImageBitmap() }
                }
                val payload = remember(f) { TrayPayload.MySticker(f) }
                TrayTile(s, payload, img, onLongStill = { deleting = f })
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
                TrayTile(s, payload, img)
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

/**
 * درج الصور: زر يفتح أداة اختيار الصور في Android، ثم «صور استخدمتها» لتُسحب إلى الصفحة مرة أخرى.
 * لا يطلب أي إذن للوصول إلى صور الجهاز. forSticker = اختيار صورة لصنع ملصق منها.
 */
@Composable
private fun PhotoTrayContent(s: DocSession, forSticker: Boolean) {
    val ctx = LocalContext.current
    var removing by remember { mutableStateOf<File?>(null) }
    val toSticker: (Uri) -> Unit = { u ->
        StickerStore.addFromUri(u)
        s.trayMode = "stickers"
    }
    val pick = rememberPhotoPicker(multi = !forSticker) { uris ->
        if (forSticker) uris.firstOrNull()?.let(toSticker)
        else {
            val page = s.currentPage()
            val at = s.visibleCenter(page)
            uris.forEachIndexed { i, u -> AppController.insertImage(s, u, page, index = i, quiet = i > 0, at = at) }
        }
    }

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Palette.Navy)
            .clickable { pick() }.padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "＋  " + if (forSticker) L("اختر صورة من الجهاز", "Choose a photo") else L("اختر صورًا من الجهاز", "Choose photos"),
            fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1
        )
    }
    val recent = PhotoLibrary.recent
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(L("صور استخدمتها", "Recently used"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Palette.TextSecondary, modifier = Modifier.weight(1f))
        if (recent.isNotEmpty()) {
            Text(
                "${recent.size}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Palette.Navy,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Palette.NavySoft).padding(horizontal = 7.dp, vertical = 1.dp)
            )
        }
    }
    if (recent.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                L("الصور التي تضيفها تظهر هنا، لتسحبها إلى أي صفحة مرة أخرى.", "Photos you add appear here, so you can drag them onto any page again."),
                fontSize = 12.sp, color = Palette.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(66.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(recent.toList(), key = { it.name }) { f ->
                val uri = remember(f) { PhotoLibrary.uriOf(f) }
                val img by produceState<ImageBitmap?>(null, uri) { value = PhotoLibrary.thumbnail(ctx, uri) }
                if (forSticker) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(Color(0xFFEEF0F4))
                            .clickable { PhotoLibrary.used(f); toSticker(uri) }
                    ) { img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
                } else {
                    val payload = remember(f) { TrayPayload.Photo(uri) }
                    TrayTile(s, payload, img, photo = true, onLongStill = { removing = f })
                }
            }
        }
    }
    removing?.let { f ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(L("إزالة من «صور استخدمتها»؟", "Remove from “Recently used”?")) },
            text = { Text(L("تُزال من هذه القائمة فقط. الصورة الأصلية في جهازك وما وضعته منها على الصفحات يبقيان.", "It is removed from this list only. The original on your device and copies already on pages stay.")) },
            confirmButton = { TextButton(onClick = { PhotoLibrary.delete(f); removing = null }) { Text(L("إزالة", "Remove"), color = Palette.Danger) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(L("إلغاء", "Cancel")) } }
        )
    }
}
