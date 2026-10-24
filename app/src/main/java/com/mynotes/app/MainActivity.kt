package com.mynotes.app

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppController.initialize(applicationContext)
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

    /** شريطا النظام بلون شريط التبويبات: كحلي بأيقونات فاتحة، أو رمادي فاتح بأيقونات داكنة. */
    @Suppress("DEPRECATION")
    private fun applySystemBars(light: Boolean) {
        val bar = (if (light) Color(0xFFE4E9F0) else Color(0xFF13233B)).toArgb()
        window.statusBarColor = bar
        window.navigationBarColor = bar
        var flags = window.decorView.systemUiVisibility
        flags = if (light) flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        else flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        flags = if (light) flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        else flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        window.decorView.systemUiVisibility = flags
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
    }

    override fun onPause() {
        super.onPause()
        AppController.setVisible(false)
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

    // زر الرجوع من المستند يعيد إلى المكتبة بدل إغلاق التطبيق
    BackHandler(enabled = c.activeId != null) { c.activate(null) }
    // في وضع التركيز: الرجوع يخرج من الوضع أولًا
    BackHandler(enabled = c.focusMode) { c.focusMode = false }

    Box(
        Modifier.fillMaxSize().background(Palette.Background).systemBarsPadding()
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
            val active = c.tabs.firstOrNull { it.id == c.activeId }
            Box(Modifier.weight(1f).fillMaxWidth()) {
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
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
    if (c.settingsOpen) SettingsDialog(onDismiss = { c.settingsOpen = false })
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
                TabItem(
                    title = s.title,
                    active = c.activeId == s.id,
                    home = false,
                    activeBg = activeBg,
                    onClick = { c.activate(s.id) },
                    onClose = { c.closeTab(s.id) }
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
    onClose: (() -> Unit)?
) {
    val shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
    // لون النص حسب خلفية التبويب (داكنة أو فاتحة)
    val bg = if (active) activeBg else Palette.TabIdle
    val darkBg = bg.luminance() < 0.4f
    val fg = if (darkBg) Color(0xFFEDE6D4) else Palette.Navy
    val fgMuted = if (darkBg) Color(0xFFB4BFD0) else Palette.TextSecondary
    Column(Modifier.width(IntrinsicSize.Max)) {
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
