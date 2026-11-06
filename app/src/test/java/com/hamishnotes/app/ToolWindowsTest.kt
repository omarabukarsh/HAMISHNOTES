package com.hamishnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * نوافذ الأدوات تُفتح داخل قائمة منسدلة (DropdownMenu) تقيس محتواها مسبقاً:
 * أي مكوّن لا يدعم ذلك (مثل BoxWithConstraints) يُسقط التطبيق عند فتح النافذة.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h1000dp")
class ToolWindowsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun session() = DocSession(DocEntry("t", "t", DocType.PDF, "t.pdf", 0, 1, 0, 0, false, 0))

    private fun opensInMenu(content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                DropdownMenu(expanded = true, onDismissRequest = {}) { content() }
            }
        }
        rule.waitForIdle()
        // القائمة نافذة منبثقة: جذر ثانٍ للواجهة
        assertTrue(rule.onAllNodes(isRoot()).fetchSemanticsNodes().size >= 2)
    }

    @Test fun laserWindow_opensInsideAMenu() = opensInMenu { LaserWindow(onCustomColor = {}) }

    @Test fun textWindow_opensInsideAMenu() {
        val s = session()
        opensInMenu { TextToolWindow(s, onTurnOff = {}) }
    }

    @Test fun lassoWindow_opensInsideAMenu() {
        val s = session()
        opensInMenu { LassoMenuContent(s, onOff = {}) }
    }

    @Test fun shapeColorMenu_withFillAndOpacity_opensInsideAMenu() {
        PenPrefs.changeShapeFill(true)
        opensInMenu { ShapeColorMenu(onPick = {}, onCustom = {}) }
    }

    @Test fun segmented_opensInsideAMenu() = opensInMenu {
        Segmented(listOf(1 to "One", 2 to "Two"), 1) {}
    }
}
