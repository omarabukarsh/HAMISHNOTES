package com.hamishnotes.app

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hamishnotes.app.golden.AntibioticsCases
import com.hamishnotes.app.golden.Pipeline
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * جولة على شاشات التطبيق تلتقط صورة لكل شاشة، لمقارنة نسخة iPad بها شاشة بشاشة.
 * لا تعمل إلا بطلب صريح (-e tour true) من workflow «Android screens»، فلا تبطئ اختبارات CI العادية.
 * تملأ المكتبة بنفس أمثلة وضع العرض في نسخة iPad (ios/iosApp/iosApp/Sources/Demo.swift).
 */
@RunWith(AndroidJUnit4::class)
class ScreenTourTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val c = AppController

    @Test
    fun tour() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tour") == "true")
        // نافذة إذن الصور تغطي الشاشات وتسرق التركيز، فنمنح الأذونات مسبقًا
        val ua = InstrumentationRegistry.getInstrumentation().uiAutomation
        val pkg = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        for (perm in listOf("android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
                "android.permission.POST_NOTIFICATIONS", "android.permission.RECORD_AUDIO")) {
            try { ua.grantRuntimePermission(pkg, perm) } catch (_: Throwable) {}
        }
        rule.waitUntil(20_000) { c.libraryLoaded }

        // 0) تسجيل الدخول (أول فتح)
        shot("00-signin")
        ui {
            c.skipLogin()
            Premium.activateTest()
        }

        // البيانات التجريبية
        val subject = "علم الأدوية"
        val before = ui { c.library.map { it.id }.toSet() }
        ui { c.importDocuments(listOf(Uri.fromFile(DeviceFiles.pdf()))) }
        rule.waitUntil(30_000) { c.library.any { it.id !in before } }
        val e = ui { c.library.first { it.id !in before } }
        ui {
            c.addFolder(subject); c.addFolder("التشريح"); c.addFolder("علم وظائف الأعضاء")
            c.moveToFolder(e.id, subject)
            c.setCover(e.id, 0xFF1F6F6B.toInt(), "pill")
            c.toggleFavorite(e.id)
            c.setExamDate(subject, java.time.LocalDate.now().plusDays(12).toEpochDay())
            CardDeck.addLoose("كيف يعمل البنسلين؟", "يمنع بناء الجدار الخلوي للبكتيريا.", subject)
            CardDeck.addLoose("اذكر دواءً من الماكروليدات", "Azithromycin", subject)
            CardDeck.addLoose("أثر جانبي للأمينوغليكوزيدات", "سمية الأذن والكلى", subject)
            val today = java.time.LocalDate.now()
            c.stats[today.toString()] = 38 * 60_000L
            listOf(75, 40, 62, 0, 90, 55, 68, 30, 80).forEachIndexed { k, m ->
                c.stats[today.minusDays(k + 1L).toString()] = m * 60_000L
            }
            c.createNote("ملاحظات المحاضرة", PaperChoice("cornell", 0xFFFFF8E1.toInt(), "a4", false))
        }
        Thread.sleep(2500)
        val note = ui { c.library.first { it.isNote } }
        ui {
            c.moveToFolder(note.id, subject)
            c.setCover(note.id, 0xFF7A3E48.toInt(), "heart")
            c.createMap("المضادات الحيوية", subject)
        }
        Thread.sleep(2500)
        val map = ui { c.library.first { it.isMap } }

        ui { c.activate(null) }
        shot("01-library")
        ui { c.libraryFolder = subject; c.libraryFilter = LibraryFilter.ALL }
        shot("02-subject")
        ui { c.libraryFolder = null; c.libraryFilter = LibraryFilter.RECENT }

        // المستند: تظليل بالتقاط النص، كتابة، ملاحظة لاصقة، نص
        ui { c.openDocument(e.id) }
        rule.waitUntil(30_000) { c.tabs.firstOrNull { it.id == e.id }?.status is SessionStatus.Ready }
        val s = ui { c.tabs.first { it.id == e.id } }
        for (case in listOf(AntibioticsCases.headingAndBullets, AntibioticsCases.doseTable)) for (st in case.strokes) {
            val old = s.marks[st.page]
            ui { s.snapHighlight(st.page, st.pts, st.color, Pipeline.WIDTH) }
            rule.waitUntil(30_000) { s.marks[st.page] !== old }
        }
        ui {
            s.beginEdit()
            val circle = (0..60).map { k ->
                val t = k / 60.0 * 2 * Math.PI
                Pt(0.5f + (Math.cos(t) * 0.36).toFloat(), 0.09f + (Math.sin(t) * 0.05).toFloat(), 1f)
            }
            s.addInk(0, Ink(circle, Color(0xFF1565C0), 0.004f))
            s.addInk(0, Ink((0..100).map { k -> Pt(0.1f + k * 0.004f, 1.2f + (Math.sin(k / 3.0) * 0.008).toFloat(), 1f) }, Color(0xFF2E7D32), 0.0035f))
            s.endEdit()
            s.addNote(StickyNote(UUID.randomUUID().toString(), 0, 0.62f, 0.85f, 0.3f, 0.24f, 0xFFFFF59D.toInt(), text = "راجع هذا قبل الامتحان!"))
            s.toggleBookmark(0)
        }
        shot("03-reader")

        ui { c.selectTool(Tool.FOUNTAIN); c.penBarOpen = true }
        shot("04-pen-bar")
        ui { c.penBarOpen = false; c.updateToolbarStyle(ToolbarStyle.CLASSIC); c.selectTool(Tool.HIGHLIGHTER) }
        shot("05-highlighter-classic")
        ui { c.updateToolbarStyle(ToolbarStyle.PEN_BAR); c.selectTool(Tool.FOUNTAIN) }

        ui { s.summaryOpen = true }
        shot("06-summaries")
        click("خريطة")
        shot("12-mind-map")
        // إغلاق الدرج يغلق الخريطة معه (حالتها داخله)، دون زر الرجوع الذي قد يغلق المستند
        ui { s.summaryOpen = false; c.activate(e.id) }

        ui { c.activate(e.id); s.drawerOpen = true }
        shot("07-pages-drawer")
        ui { s.drawerOpen = false; s.pageMenuOpen = true }
        shot("08-page-menu")
        ui { s.pageMenuOpen = false; s.trayMode = "stickers" }
        shot("09-stickers")
        ui { s.trayMode = "photos" }
        shot("09b-photos")
        ui { s.trayMode = null; PenPrefs.shapeActive = true }
        shot("10-shapes")
        ui { PenPrefs.shapeActive = false; PenPrefs.stickyActive = true }
        shot("10b-sticky-bar")
        ui { PenPrefs.stickyActive = false; s.searchOpen = true }
        shot("11-search")
        ui { s.searchOpen = false }

        ui { c.openDocument(map.id) }
        shot("13-standalone-map")
        ui { c.activate(e.id) }
        ui { c.openSplit(note.id) }
        shot("14-split-view")
        ui { c.closeSplit() }
        ui { c.toggleNightPages() }
        shot("15-night")
        ui { c.toggleNightPages(); c.focusMode = true }
        shot("16-focus")
        ui { c.focusMode = false; c.activate(null) }

        ui { CardDeck.openDeck = CardDeck.ALL }
        shot("17-cards")
        ui { CardDeck.openDeck = null }
        click("اليوم")
        shot("18-stats")
        clickDesc("إغلاق")
        ui { c.settingsOpen = true }
        shot("19-settings")
        ui { c.settingsOpen = false; Premium.endTest(); Premium.paywallOpen = true }
        shot("20-premium")
        ui { Premium.paywallOpen = false; Premium.activateTest() }

        // الإنجليزية والثيم الفاتح
        ui { c.setEnglish(true); c.setTheme(AppTheme.LIGHT) }
        shot("24-english-library")
        ui { c.activate(e.id); c.penBarOpen = true }
        shot("25-english-reader")
        ui { c.setEnglish(false); c.setTheme(AppTheme.NAVY); c.penBarOpen = false }
    }

    private fun <T> ui(block: () -> T): T {
        var out: T? = null
        rule.runOnUiThread { out = block() }
        @Suppress("UNCHECKED_CAST")
        return out as T
    }

    private fun click(text: String) {
        try {
            rule.waitForIdle()
            rule.onAllNodesWithText(text, substring = true)[0].performClick()
        } catch (t: Throwable) {
            DeviceFiles.writeReport("tour-missing-$text.txt", t.toString())
        }
    }

    private fun clickDesc(desc: String) {
        try {
            rule.waitForIdle()
            rule.onAllNodesWithContentDescription(desc)[0].performClick()
        } catch (t: Throwable) {
            DeviceFiles.writeReport("tour-missing-desc.txt", t.toString())
        }
    }

    private fun pressBack() {
        try {
            rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        } catch (_: Throwable) {
        }
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        Thread.sleep(2200)
        rule.waitForIdle()
        val bmp = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val dir = File(DeviceFiles.reportDir(), "tour").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
