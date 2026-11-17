package com.hamishnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * الأشرطة العائمة فوق الصفحة: نقرة فيها حركة صغيرة (كنقرة القلم) تضغط الزر،
 * ولا يصل شيء من لمسات الشريط إلى الصفحة تحته.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h1000dp")
class FloatingBarTapTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aTapThatMovesALittleStillPressesTheButton() {
        var clicks = 0
        var pageEvents = 0
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                // «الصفحة» تحت الشريط
                Box(Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitPointerEventScope { while (true) { awaitPointerEvent(); pageEvents++ } }
                })
                // الشريط العائم
                Box(Modifier.align(Alignment.TopCenter).blockTouches()) {
                    TBtn(onClick = { clicks++ }) { Box(Modifier.size(20.dp).testTag("btn")) }
                }
            }
        }
        rule.onNodeWithTag("btn", useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(2f, 1f))
            moveBy(Offset(1f, 2f))
            up()
        }
        rule.waitForIdle()
        assertEquals(1, clicks)
        assertTrue("touches on the bar reached the page", pageEvents == 0)
    }
}
