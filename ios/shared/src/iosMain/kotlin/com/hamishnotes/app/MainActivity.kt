package com.hamishnotes.app

import android.content.AppContext
import androidx.compose.ui.window.ComposeUIViewController
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIPencilInteraction
import platform.UIKit.UIPencilInteractionDelegateProtocol
import platform.UIKit.UIViewController
import platform.UIKit.UIStatusBarStyle
import androidx.compose.ui.uikit.ComposeUIViewControllerDelegate
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.addInteraction
import platform.darwin.NSObject
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

/**
 * نقطة دخول iPad: يستدعيها تطبيق Swift (iosApp/) ويعرضها ملء الشاشة.
 * تقابل MainActivity في أندرويد: التهيئة، حجم الكتابة، الثيم، ودورة حياة التطبيق.
 */
// delegate مهجور في Compose 1.9 لكنه الطريق الوحيد لتحديد لون شريط الحالة من شاشة Compose الجذرية
@Suppress("DEPRECATION")
fun MainViewController(): UIViewController {
    AppController.initialize(AppContext)
    Premium.init(AppContext)
    IosText.registerFonts()
    IosApp.observeLifecycle()
    // نص التذكير اليومي يُحدَّث عند كل فتح
    Reminders.schedule(AppContext)
    var host: UIViewController? = null
    val vc = ComposeUIViewController(configure = {
        // أيقونات شريط الحالة: فاتحة فوق الكحلي، داكنة فوق الرمادي الفاتح
        delegate = object : ComposeUIViewControllerDelegate {
            override val preferredStatusBarStyle: UIStatusBarStyle?
                get() = if (ThemeState.light) UIStatusBarStyleDarkContent else UIStatusBarStyleLightContent
        }
    }) {
        // حجم الكتابة في التطبيق (من الإعدادات)
        val d = androidx.compose.ui.platform.LocalDensity.current
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, d.fontScale * UiPrefs.textScale)
        ) {
            MyNotesTheme { MyNotesApp() }
        }
        val light = ThemeState.light
        androidx.compose.runtime.SideEffect { host?.setNeedsStatusBarAppearanceUpdate() }
    }
    host = vc
    IosApp.attachPencil(vc)
    // لقطات الشاشة على المحاكي (ios-preview.yml): يُفتح ملف تجريبي عند التشغيل
    val args = NSProcessInfo.processInfo.arguments.map { it.toString() }
    val demo = args.indexOf("-hamishDemo")
    if (demo >= 0 && demo + 1 < args.size) IosApp.openFile(args[demo + 1])
    if ("-hamishLandscape" in args) IosApp.rotateLandscape()
    return vc
}

/** ما يصل من النظام إلى التطبيق: فتح ملف من تطبيق آخر، ودخول التطبيق للخلفية والعودة. */
object IosApp {
    private var observing = false
    private var pencilDelegate: NSObject? = null

    fun observeLifecycle() {
        if (observing) return
        observing = true
        val nc = NSNotificationCenter.defaultCenter
        nc.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) { _ ->
            AppController.setVisible(true)
            Premium.evaluate()
        }
        nc.addObserverForName(UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue) { _ ->
            AppController.setVisible(false)
        }
        nc.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) { _ ->
            AppController.flush()
        }
    }

    /** لقطات الشاشة بالعرض على المحاكي: يدير الواجهة بعد ظهور النافذة (iPadOS 16+). */
    fun rotateLandscape() {
        platform.darwin.dispatch_after(
            platform.darwin.dispatch_time(platform.darwin.DISPATCH_TIME_NOW, 1_500_000_000L),
            platform.darwin.dispatch_get_main_queue()
        ) {
            val scene = platform.UIKit.UIApplication.sharedApplication.connectedScenes
                .firstOrNull { it is platform.UIKit.UIWindowScene } as? platform.UIKit.UIWindowScene ?: return@dispatch_after
            scene.requestGeometryUpdateWithPreferences(
                platform.UIKit.UIWindowSceneGeometryPreferencesIOS(platform.UIKit.UIInterfaceOrientationMaskLandscapeRight),
                errorHandler = null
            )
        }
    }

    /** النقر المزدوج على Apple Pencil: تبديل الممحاة وآخر قلم (مثل نقرتي القلم في أندرويد). */
    fun attachPencil(vc: UIViewController) {
        val d = object : NSObject(), UIPencilInteractionDelegateProtocol {
            override fun pencilInteractionDidTap(interaction: UIPencilInteraction) {
                AppController.penDoubleTap()
            }
        }
        pencilDelegate = d
        val pi = UIPencilInteraction()
        pi.delegate = d
        vc.view.addInteraction(pi)
    }

    /** ملف فُتح بالتطبيق من «الملفات» أو المشاركة (من Swift: onOpenURL). */
    fun openFile(path: String) {
        val f = java.io.File(path)
        if (!f.exists()) return
        val mime = if (path.lowercase().endsWith(".pdf")) "application/pdf" else null
        AppController.openExternal(android.net.Uri.fromFile(f), mime)
    }
}

@Composable
fun MyNotesApp() {
    val c = AppController
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        c.messages.collect { snackbar.showSnackbar(it) }
    }

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
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        // مكان اللمسة: تخرج منه النوافذ (ونوافذ المشاركة في iPad)
                        ev.changes.firstOrNull { it.pressed && !it.previousPressed }?.let { TouchOrigin.record(it.position.x, it.position.y) }
                        c.noteInteraction()
                    }
                }
            }
    ) {
        Column(Modifier.fillMaxSize()) {
            if (c.tabs.isNotEmpty() && !(c.focusMode && c.activeId != null)) TabBar()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // انتقال ناعم بين المكتبة والمستندات (وبين التبويبات)
                androidx.compose.animation.Crossfade(
                    targetState = c.activeId,
                    animationSpec = androidx.compose.animation.core.tween(Motion.ENTER),
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
    // عرض باوربوينت فُتح من تطبيق آخر: تحويله إلى PDF
    PresentationConvertDialog()
    LockDialogs()
    PremiumHost()
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
                TabItem(
                    title = s.title,
                    active = c.activeId == s.id,
                    home = false,
                    activeBg = activeBg,
                    onClick = { c.activate(s.id) },
                    onClose = { c.closeTab(s.id) },
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
