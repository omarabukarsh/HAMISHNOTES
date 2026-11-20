package com.hamishnotes.app

import androidx.compose.ui.geometry.Offset

import androidx.compose.animation.togetherWith

import androidx.compose.ui.draw.drawWithContent

import androidx.compose.foundation.interaction.collectIsPressedAsState

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
import androidx.compose.ui.draw.drawBehind
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

    // المظهر «تلقائي»: يتبع مظهر الجهاز ويتغير وحده معه
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    LaunchedEffect(c.themeFollowsSystem, systemDark) {
        if (c.themeFollowsSystem) ThemeState.theme = if (systemDark) AppTheme.NAVY else AppTheme.LIGHT
    }

    // زر الرجوع من المستند يعيد إلى المكتبة بدل إغلاق التطبيق
    BackHandler(enabled = c.activeId != null) { c.activate(null) }
    // في وضع التركيز: الرجوع يخرج من الوضع أولًا
    BackHandler(enabled = c.focusMode) { c.focusMode = false }

    // أول فتح للتطبيق: صفحة تسجيل الدخول (يمكن تخطيها)
    if (!c.loginSeen) {
        SignInScreen()
        return
    }
    // تنبيه مرة واحدة عند تحديث سياسة الخصوصية أو الشروط
    PolicyUpdateNotice()

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
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // انتقال ناعم بين المكتبة والمستندات (وبين التبويبات)
                // التنقل بين التبويبات: القديم يختفي بسرعة ثم يظهر الجديد، فلا يتداخل نصّا المستندين
                androidx.compose.animation.AnimatedContent(
                    targetState = c.activeId,
                    transitionSpec = {
                        androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(160, delayMillis = 90)) togetherWith
                            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(90))
                    },
                    label = "screen"
                ) { id ->
                    val active = c.tabs.firstOrNull { it.id == id }
                    if (active == null) {
                        LibraryScreen()
                    } else {
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
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
    if (c.settingsOpen) SettingsDialog(onDismiss = { c.settingsOpen = false })
    PremiumHost()
    ImportProgressDialog()
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
    // في المستند: التبويب النشط بلون بطاقة الأدوات (أبيض) ويتصل بها، والشريط بلون مكتب القراءة
    val activeBg = if (libraryActive) Palette.Spine else Palette.ChromeBg
    val railBg = if (libraryActive) Palette.TabRail else Palette.ChromeRail
    // ترتيب التبويبات: ضغط مطوّل ثم سحب أفقي
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragDx by remember { mutableStateOf(0f) }
    val widths = remember { mutableMapOf<String, Int>() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(
        Modifier.fillMaxWidth().background(railBg)
            .padding(start = 26.dp, end = 26.dp, top = 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        // «المكتبة» ثابتة في البداية؛ تبويبات المستندات بعدها تتقاسم العرض ثم تثبت وتُسحب
        TabItem(
            title = L("المكتبة", "Library"),
            active = libraryActive,
            home = true,
            activeBg = activeBg,
            onClick = { c.activate(null) },
            onClose = null
        )
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f)) {
        val n = c.tabs.size
        // عرض التبويب: يتقاسم المستندون الشريط (حتى 240)، ويصغر حتى 170 ثم يثبت ويُسحب الباقي
        val tabW = if (n == 0) 0.dp else ((maxWidth - (n * 1).dp) / n).coerceIn(170.dp, 240.dp)
        val scroll = rememberScrollState()
        val density = androidx.compose.ui.platform.LocalDensity.current
        // التبويب النشط يظهر دائمًا (عند فتحه أو التنقل إليه)
        LaunchedEffect(c.activeId, n) {
            val i = c.tabs.indexOfFirst { it.id == c.activeId }
            if (i < 0) return@LaunchedEffect
            val wPx = with(density) { (tabW + 1.dp).toPx() }
            val start = (i * wPx).toInt()
            val view = with(density) { maxWidth.toPx() }.toInt()
            val target = when {
                start < scroll.value -> start
                start + wPx > scroll.value + view -> (start + wPx - view).toInt()
                else -> return@LaunchedEffect
            }
            scroll.animateScrollTo(target.coerceIn(0, scroll.maxValue.coerceAtLeast(target)))
        }
        Row(Modifier.horizontalScroll(scroll).scrollFadeH(scroll, railBg), verticalAlignment = Alignment.Bottom) {
        c.tabs.forEachIndexed { i, s ->
            val prevActive = if (i == 0) libraryActive else c.activeId == c.tabs[i - 1].id
            if (!prevActive && c.activeId != s.id) TabSeparator()
            key(s.id) {
                val dragging = dragId == s.id
                TabItem(
                    title = s.title,
                    active = c.activeId == s.id,
                    home = false,
                    activeBg = activeBg,
                    onClick = { c.activate(s.id) },
                    onClose = { c.closeTab(s.id) },
                    fixedWidth = tabW,
                    modifier = Modifier
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
                            val gap = 0f
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
    }
}

/** تلاشٍ على طرف شريط التبويبات الذي فيه تبويبات مخفية (تُسحب لإظهارها). */
private fun Modifier.scrollFadeH(state: androidx.compose.foundation.ScrollState, bg: Color): Modifier = this.drawWithContent {
    drawContent()
    val f = 36.dp.toPx()
    if (state.canScrollBackward) drawRect(
        androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(bg, bg.copy(alpha = 0f)), startX = 0f, endX = f),
        size = androidx.compose.ui.geometry.Size(f, size.height)
    )
    if (state.canScrollForward) drawRect(
        androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(bg.copy(alpha = 0f), bg), startX = size.width - f, endX = size.width),
        topLeft = Offset(size.width - f, 0f), size = androidx.compose.ui.geometry.Size(f, size.height)
    )
}

/** فاصل رفيع بين تبويبين غير نشطين. */
@Composable
private fun TabSeparator() {
    Box(Modifier.padding(bottom = 10.dp).width(1.dp).height(16.dp).background(Palette.TextSecondary.copy(alpha = 0.25f)))
}

/**
 * تبويب كتبويبات المستندات: غير النشط نص فقط بلا خلفية، والنشط بلون ما تحته
 * بزوايا علوية مستديرة وزاويتين مقعّرتين أسفله فينساب في البطاقة التي تحته بلا زوايا حادة.
 */
@Composable
private fun TabItem(
    title: String,
    active: Boolean,
    home: Boolean,
    activeBg: Color,
    onClick: () -> Unit,
    onClose: (() -> Unit)?,
    fixedWidth: androidx.compose.ui.unit.Dp? = null,
    modifier: Modifier = Modifier
) {
    val r = 12.dp
    // أثر النقر: إضاءة ناعمة داخل شكل التبويب تتلاشى (بدل التموّج المستطيل الحاد)
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val glow by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 1f else 0f, androidx.compose.animation.core.tween(if (pressed) 90 else 220), label = "tabPress"
    )
    val shape = RoundedCornerShape(topStart = r, topEnd = r)
    val bg = if (active) activeBg else Color.Transparent
    // لون النص حسب خلفية التبويب (داكنة أو فاتحة)
    val under = if (active) activeBg else (if (AppController.activeId == null) Palette.TabRail else Palette.ChromeRail)
    val darkBg = under.luminance() < 0.4f
    val fg = if (darkBg) Color(0xFFEDE6D4) else Palette.Navy
    val fgMuted = if (darkBg) Color(0xFFB4BFD0) else Palette.TextSecondary
    Column(if (fixedWidth != null) modifier.width(fixedWidth) else modifier.width(IntrinsicSize.Max)) {
        Row(
            Modifier.height(40.dp)
                .drawBehind {
                    if (!active) return@drawBehind
                    // زاويتان مقعّرتان عند التقاء التبويب بالبطاقة
                    val rp = r.toPx(); val h = size.height; val w = size.width
                    val left = androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, h - rp); lineTo(0f, h); lineTo(-rp, h)
                        arcTo(androidx.compose.ui.geometry.Rect(-2 * rp, h - 2 * rp, 0f, h), 90f, -90f, false)
                        close()
                    }
                    val right = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w, h - rp); lineTo(w, h); lineTo(w + rp, h)
                        arcTo(androidx.compose.ui.geometry.Rect(w, h - 2 * rp, w + 2 * rp, h), 90f, 90f, false)
                        close()
                    }
                    drawPath(left, bg); drawPath(right, bg)
                }
                .clip(shape)
                .background(bg)
                .drawBehind {
                    if (glow > 0f) {
                        val inset = 3.dp.toPx()
                        drawRoundRect(
                            fg.copy(alpha = 0.10f * glow),
                            topLeft = Offset(inset, inset),
                            size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx())
                        )
                    }
                }
                .clickable(interactionSource = press, indication = null, onClick = onClick)
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
                modifier = if (fixedWidth != null) Modifier.weight(1f) else Modifier.widthIn(max = 200.dp)
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


/** نافذة إضافة ملف: الاسم والنسبة والمرحلة وزر الإلغاء؛ التطبيق يبقى يستجيب أثناءها. */
@Composable
private fun ImportProgressDialog() {
    val c = AppController
    val p = c.importProgress ?: return
    androidx.compose.ui.window.Dialog(
        onDismissRequest = {},
        properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        val frac by androidx.compose.animation.core.animateFloatAsState(p.fraction, label = "importFrac")
        Column(
            Modifier.width(360.dp).clip(RoundedCornerShape(22.dp)).background(Palette.Surface).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(40.dp), color = Palette.Navy, strokeWidth = 3.5.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                if (p.count > 1) L("جارٍ إضافة الملفات (${p.index + 1} من ${p.count})…", "Adding files (${p.index + 1} of ${p.count})…")
                else L("جارٍ إضافة الملف…", "Adding the file…"),
                fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(p.name, fontSize = 13.sp, color = Palette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))
            androidx.compose.material3.LinearProgressIndicator(
                progress = { frac },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = Palette.Navy, trackColor = Palette.NavySoft
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Text("${(frac * 100).toInt()}%", fontSize = 12.5.sp, color = Palette.TextSecondary)
                Spacer(Modifier.weight(1f))
                Text(p.phase, fontSize = 12.5.sp, color = Palette.TextSecondary)
            }
            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.TextButton(onClick = { c.importCancel = true }) {
                Text(L("إلغاء", "Cancel"), color = Palette.Danger, fontSize = 14.sp)
            }
        }
    }
}
