package com.hamishnotes.app

import kotlinx.coroutines.launch
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.animation.togetherWith
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.ui.draw.clipToBounds
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex
import kotlin.math.abs
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : androidx.fragment.app.FragmentActivity() {
    /** نحفظ مكان كل لمسة لتخرج النافذة التي تفتحها منه. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) TouchOrigin.record(ev.rawX, ev.rawY)
        // التعرّف على القلم ومعايرته قبل أن يصل الحدث إلى الواجهة
        PenInput.observe(ev)
        return super.dispatchTouchEvent(ev)
    }

    /** أحداث تحويم القلم فوق الشاشة. */
    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        PenInput.observe(ev)
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppController.initialize(applicationContext)
        Premium.init(applicationContext)
        requestHighRefreshRate()
        handleOpenIntent(intent)
        Haptics.view = java.lang.ref.WeakReference(window.decorView)
        setContent {
            val light = ThemeState.light
            SideEffect { applySystemBars(light) }
            // حجم الكتابة في التطبيق (من الإعدادات)
            val d = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, d.fontScale * UiPrefs.textScale)
            ) {
                MyNotesTheme { MyNotesApp() }
            }
        }
    }

    /**
     * من Android 15 يُرسم التطبيق خلف شريطي النظام إلزاميًا (edge-to-edge) ولا يُقبل تلوينهما:
     * نجعلهما شفافين ونرسم لونهما بأنفسنا (systemBarColor)، مع أيقونات داكنة أو فاتحة حسب الوضع.
     */
    private fun applySystemBars(light: Boolean) {
        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (light) SystemBarStyle.light(transparent, transparent) else SystemBarStyle.dark(transparent)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    /**
     * يطلب أعلى معدل تحديث تدعمه الشاشة (90/120/144 هرتز) بنفس الدقة،
     * فتكون الحركة والتمرير والكتابة بالقلم أنعم على الشاشات عالية الهرتز.
     */
    private fun requestHighRefreshRate() {
        try {
            @Suppress("DEPRECATION")
            val d = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay) ?: return
            val cur = d.mode
            val best = d.supportedModes
                .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
                .maxByOrNull { it.refreshRate } ?: return
            val lp = window.attributes
            lp.preferredDisplayModeId = best.modeId
            lp.preferredRefreshRate = best.refreshRate
            window.attributes = lp
        } catch (t: Throwable) {
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleOpenIntent(intent)
    }

    /** ملف PDF فُتح من تطبيق آخر: يُضاف إلى المكتبة (أو يُفتح إن كان موجودًا) ويظهر مباشرة. */
    private fun handleOpenIntent(i: android.content.Intent?) {
        // أداة الشاشة الرئيسية: متابعة آخر مستند
        if (i?.getBooleanExtra("open_last", false) == true) {
            i.removeExtra("open_last")
            AppController.openLast()
            return
        }
        if (i?.action != android.content.Intent.ACTION_VIEW) return
        val uri = i.data ?: return
        val type = i.type
        i.data = null
        AppController.openExternal(uri, type)
    }

    /** النقر المزدوج على القلم يصل كمفتاح (حسب نوع القلم والجهاز). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        if (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP) {
            if (AppController.handlePenKey(event.keyCode, down, event.repeatCount)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        requestHighRefreshRate()
        AppController.setVisible(true)
        Premium.attach(this)
        Premium.evaluate()
    }

    override fun onPause() {
        super.onPause()
        AppController.setVisible(false)
        PenInput.flush()
    }

    override fun onStop() {
        super.onStop()
        AppController.flush()
    }
}

@Composable
fun MyNotesApp() {
    val c = AppController
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        c.messages.collect { snackbar.showSnackbar(it) }
    }

    // زر الرجوع من المستند يعيد إلى المكتبة بدل إغلاق التطبيق.
    // مع الرجوع التنبؤي: أثناء السحب يصغر المستند وتظهر المكتبة خلفه.
    val backP = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    if (UiPrefs.predictiveBack) {
        androidx.activity.compose.PredictiveBackHandler(enabled = c.activeId != null && !c.focusMode) { events ->
            try {
                events.collect { backP.snapTo(it.progress) }
                c.activate(null)
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                scope.launch { backP.animateTo(0f, Motion.grow()) }
                throw e
            }
        }
    } else {
        BackHandler(enabled = c.activeId != null) { c.activate(null) }
    }
    // بعد انتهاء انتقال الرجوع يعود المقياس كما كان
    LaunchedEffect(c.activeId) {
        if (c.activeId != null) backP.snapTo(0f) else { kotlinx.coroutines.delay(500); backP.snapTo(0f) }
    }
    // في وضع التركيز: الرجوع يخرج من الوضع أولًا
    BackHandler(enabled = c.focusMode) { c.focusMode = false }

    // أول فتح للتطبيق: صفحة تسجيل الدخول (يمكن تخطيها)
    if (!c.loginSeen) {
        SignInScreen()
        return
    }

    Box(
        Modifier.fillMaxSize().background(systemBarColor(ThemeState.light)).systemBarsPadding().background(Palette.Background)
            // أي لمسة تعني أن المستخدم حاضر (لعداد القراءة)، دون التأثير على اللمس نفسه
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        c.noteInteraction()
                    }
                }
            }
    ) {
        Column(Modifier.fillMaxSize()) {
            if (c.tabs.isNotEmpty() && !(c.focusMode && c.activeId != null)) TabBar()
            val container = remember { arrayOfNulls<androidx.compose.ui.geometry.Rect>(1) }
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .onGloballyPositioned { container[0] = it.boundsInWindow() }
            ) {
                // المستند يتمدد من غلافه في المكتبة وينكمش إليه عند الرجوع؛ وبين التبويبات تلاشٍ عابر
                androidx.compose.animation.AnimatedContent(
                    targetState = c.activeId,
                    transitionSpec = { screenTransition(initialState, targetState, container[0]) },
                    label = "screen"
                ) { id ->
                    val active = c.tabs.firstOrNull { it.id == id }
                    if (active == null) {
                        LibraryCapture { LibraryScreen() }
                    } else {
                        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                        val showShot by remember { androidx.compose.runtime.derivedStateOf { backP.value > 0f } }
                        Box(Modifier.fillMaxSize()) {
                            // أثناء سحب الرجوع: صورة المكتبة خلف المستند
                            if (showShot) LibraryShot.image?.let { img ->
                                androidx.compose.foundation.Image(
                                    img, null, Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.FillBounds
                                )
                                Box(Modifier.fillMaxSize().graphicsLayer { alpha = 0.25f * (1f - backP.value) }.background(Color.Black))
                            }
                            Box(
                                Modifier.fillMaxSize().graphicsLayer {
                                    val p = backP.value
                                    val sc = 1f - 0.12f * p
                                    scaleX = sc
                                    scaleY = sc
                                    translationX = (if (rtl) -1f else 1f) * 0.04f * size.width * p
                                    if (p > 0f) {
                                        shape = RoundedCornerShape((24f * p).dp)
                                        clip = true
                                        shadowElevation = 16f * p
                                    }
                                }
                            ) {
                                val second = c.tabs.firstOrNull { it.id == c.splitId && it.id != active.id }
                                if (second == null || c.focusMode) {
                                    key(active.id) { ReaderScreen(active) }
                                } else {
                                    SplitReaders(active, second)
                                }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp)) { data ->
            androidx.compose.material3.Snackbar(data, Modifier.riseIn())
        }
    }
    if (c.settingsOpen) SettingsDialog(onDismiss = { c.settingsOpen = false })
    PremiumHost()
    // عرض باوربوينت فُتح من تطبيق آخر: تحويله إلى PDF
    PresentationConvertDialog()
    LockDialogs()
}

@Composable
private fun TabBar() {
    val c = AppController
    // التبويب النشط بلون ما تحته تمامًا فيبدو متصلًا به: المكتبة بلون شريطها الجانبي، والمستند بلون مكتب القراءة.
    // الشريط أغمق، والتبويبات الأخرى بلون ثالث واضح فيظهر التباين.
    val libraryActive = c.activeId == null
    val activeBg = if (libraryActive) Palette.Spine else Palette.Desk
    // ترتيب التبويبات: ضغط مطوّل ثم سحب أفقي
    var dragId by remember { mutableStateOf<String?>(null) }
    // التبويبات الموجودة عند فتح التطبيق لا تنمو؛ الجديدة بعدها تنمو من عرض صفر
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { settled = true }
    var dragDx by remember { mutableStateOf(0f) }
    val widths = remember { mutableMapOf<String, Int>() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(
        Modifier.fillMaxWidth().background(Palette.TabRail)
            .horizontalScroll(rememberScrollState())
            .padding(start = 10.dp, end = 10.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        TabItem(
            title = L("المكتبة", "Library"),
            active = libraryActive,
            home = true,
            activeBg = activeBg,
            onClick = { c.activate(null) },
            onClose = null
        )
        c.tabs.forEach { s ->
            key(s.id) {
                val dragging = dragId == s.id
                val grow = remember { androidx.compose.animation.core.Animatable(if (settled) 0f else 1f) }
                val tabScope = androidx.compose.runtime.rememberCoroutineScope()
                LaunchedEffect(Unit) { if (grow.value < 1f) grow.animateTo(1f, Motion.grow()) }
                TabItem(
                    title = s.title,
                    active = c.activeId == s.id,
                    home = false,
                    activeBg = activeBg,
                    onClick = { c.activate(s.id) },
                    // ينكمش التبويب أولًا ثم يُغلق، فتنزلق التبويبات المجاورة إلى مكانه
                    onClose = {
                        tabScope.launch {
                            grow.animateTo(0f, androidx.compose.animation.core.tween(170, easing = androidx.compose.animation.core.FastOutLinearInEasing))
                            c.closeTab(s.id)
                        }
                    },
                    modifier = Modifier
                        .growWidth { grow.value }
                        .onSizeChanged { widths[s.id] = it.width }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationX = if (dragging) dragDx else 0f
                            val sc = if (dragging) 1.05f else 1f
                            scaleX = sc
                            scaleY = sc
                            alpha = if (dragging) 0.92f else 1f
                        }
                        .pointerInput(s.id) {
                            val gap = 4.dp.toPx()
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragId = s.id; dragDx = 0f },
                                onDragEnd = { dragId = null; dragDx = 0f; c.commitTabOrder() },
                                onDragCancel = { dragId = null; dragDx = 0f; c.commitTabOrder() }
                            ) { change, amount ->
                                change.consume()
                                dragDx += amount.x
                                val idx = c.tabs.indexOfFirst { it.id == s.id }
                                val dir = if (dragDx > 0f) 1 else -1
                                val nIdx = idx + (if (rtl) -dir else dir)
                                val nw = c.tabs.getOrNull(nIdx)?.let { widths[it.id] } ?: 0
                                if (idx >= 0 && nw > 0 && abs(dragDx) > nw / 2f) {
                                    c.moveTab(idx, nIdx)
                                    dragDx -= dir * (nw + gap)
                                }
                            }
                        }
                )
            }
        }
    }
}

/** تبويب على شكل لسان ملف بزوايا علوية مستديرة. */
@Composable
private fun TabItem(
    title: String,
    active: Boolean,
    home: Boolean,
    activeBg: Color,
    onClick: () -> Unit,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
    // لون النص حسب خلفية التبويب (داكنة أو فاتحة)
    val bg = if (active) activeBg else Palette.TabIdle
    val darkBg = bg.luminance() < 0.4f
    val fg = if (darkBg) Color(0xFFEDE6D4) else Palette.Navy
    val fgMuted = if (darkBg) Color(0xFFB4BFD0) else Palette.TextSecondary
    Column(modifier.width(IntrinsicSize.Max)) {
        Row(
            Modifier.height(if (active) 40.dp else 36.dp)
                .pressScale(0.95f)
                .clip(shape)
                .background(bg)
                .clickable(onClick = onClick)
                .padding(start = 14.dp, end = if (onClose == null) 16.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (home) {
                PhIcon(
                    if (active) R.drawable.ph_books_fill else R.drawable.ph_books,
                    if (active) Palette.Gold else fgMuted, 18.dp
                )
                Spacer(Modifier.width(7.dp))
            } else if (active) {
                // نقطة ذهبية تميّز المستند المفتوح حاليًا
                Box(Modifier.size(6.dp).clip(CircleShape).background(Palette.Gold))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) fg else fgMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp)
            )
            if (onClose != null) {
                Spacer(Modifier.width(2.dp))
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(30.dp).clip(CircleShape)
                ) {
                    PhIcon(R.drawable.ph_close, fgMuted, 15.dp, L("إغلاق التبويب", "Close tab"))
                }
            }
        }
    }
}

/** لون شريطي النظام (يُرسم خلفهما): بلون شريط التبويبات، كحلي أو رمادي فاتح. */
fun systemBarColor(light: Boolean): Color = if (light) Color(0xFFE4E9F0) else Color(0xFF13233B)

/** رسالة أسفل الشاشة ترتفع بنابض. */
private fun Modifier.riseIn(): Modifier = composed {
    val a = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, Motion.grow()) }
    graphicsLayer {
        translationY = (1f - a.value) * 48.dp.toPx()
        alpha = a.value.coerceIn(0f, 1f)
    }
}

/** صورة من المكتبة تظهر خلف المستند أثناء سحب الرجوع التنبؤي. */
object LibraryShot {
    var image: androidx.compose.ui.graphics.ImageBitmap? = null
}

/** يسجّل رسم المكتبة، ويحفظ منه صورة واحدة عند فتح مستند (لتظهر خلفه أثناء الرجوع). */
@Composable
private fun LibraryCapture(content: @Composable () -> Unit) {
    val layer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val c = AppController
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { c.activeId }.collect { id ->
            if (id != null && UiPrefs.predictiveBack) {
                try { LibraryShot.image = layer.toImageBitmap() } catch (_: Throwable) {}
            }
        }
    }
    Box(
        Modifier.fillMaxSize().drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }
    ) { content() }
}

/** الانتقال بين المكتبة والمستندات والتبويبات. */
private fun androidx.compose.animation.AnimatedContentTransitionScope<String?>.screenTransition(
    from: String?, to: String?, container: androidx.compose.ui.geometry.Rect?
): androidx.compose.animation.ContentTransform {
    val card = DocOrigin.rect
    fun cardOrigin(): Pair<androidx.compose.ui.graphics.TransformOrigin, Float>? {
        val r = card ?: return null
        val cn = container ?: return null
        if (cn.width <= 0f || cn.height <= 0f || !cn.overlaps(r)) return null
        val o = androidx.compose.ui.graphics.TransformOrigin(
            ((r.center.x - cn.left) / cn.width).coerceIn(0f, 1f),
            ((r.center.y - cn.top) / cn.height).coerceIn(0f, 1f)
        )
        return o to (r.width / cn.width).coerceIn(0.08f, 0.9f)
    }
    val emph = androidx.compose.animation.core.CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    // المكتبة ← مستند: يتمدد من غلافه
    if (from == null && to != null && DocOrigin.id == to) {
        val (o, s0) = cardOrigin() ?: return fadeThrough(emph)
        return (androidx.compose.animation.scaleIn(Motion.gentle(), initialScale = s0, transformOrigin = o) +
            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(140))) togetherWith
            (androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(320), targetScale = 0.94f) +
                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(220, delayMillis = 100)))
    }
    // مستند ← المكتبة: ينكمش إلى غلافه والمكتبة تتقدم من خلفه
    if (from != null && to == null && DocOrigin.id == from) {
        val (o, s0) = cardOrigin() ?: return fadeThrough(emph)
        return ((androidx.compose.animation.scaleIn(Motion.gentle(), initialScale = 0.94f) +
            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220))) togetherWith
            (androidx.compose.animation.scaleOut(
                androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                targetScale = s0, transformOrigin = o
            ) + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150, delayMillis = 150))))
            .apply { targetContentZIndex = -1f }
    }
    return fadeThrough(emph)
}

/** تلاشٍ عابر: القديم يختفي سريعًا والجديد يظهر مع تكبير خفيف. */
private fun fadeThrough(e: androidx.compose.animation.core.Easing): androidx.compose.animation.ContentTransform =
    (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(210, delayMillis = 90)) +
        androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(300, delayMillis = 90, easing = e), initialScale = 0.96f)) togetherWith
        androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(90))

/** يكبر عرض العنصر من صفر (أو ينكمش إليه) حسب p، مع تلاشٍ. */
private fun Modifier.growWidth(p: () -> Float): Modifier = this
    .layout { m, cons ->
        val pl = m.measure(cons)
        val f = p().coerceAtLeast(0f)
        val w = (pl.width * f).roundToInt().coerceAtLeast(0)
        layout(w, pl.height) {
            pl.placeRelativeWithLayer(0, 0) {
                alpha = f.coerceIn(0f, 1f)
                val sc = 0.85f + 0.15f * f.coerceIn(0f, 1f)
                scaleX = sc
                scaleY = sc
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            }
        }
    }
    .clipToBounds()

