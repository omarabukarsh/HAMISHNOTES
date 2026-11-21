package com.hamishnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h1000dp")
class EdgeMenuAndInkTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun roots() = rule.onAllNodes(isRoot()).fetchSemanticsNodes().size

    /** نافذة الأداة تخرج من حافة الزر، وعند الإغلاق تكمل الانثناء ثم تختفي تمامًا. */
    @Test fun edgeMenu_opensThenFullyCloses() {
        var open by mutableStateOf(true)
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.size(46.dp)) {
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }, fromEdge = true) {
                        LaserWindow(onCustomColor = {})
                    }
                }
            }
        }
        rule.waitForIdle()
        assertTrue("the window opens as a popup", roots() >= 2)
        open = false
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        assertEquals("after the close animation the popup is gone", 1, roots())
    }

    /**
     * صفحة فيها كتابة قديمة: صورة الخطوط تُجهَّز بعد معرفة حجم الصفحة دون أي خط جديد
     * (كانت تبقى بلا صورة وتُرسم الخطوط كلها في كل إطار حتى يكتب المستخدم).
     */
    @Test fun oldInk_getsItsImage_withoutAnyNewStroke() {
        val strokes = listOf(
            Ink(listOf(Pt(0.1f, 0.1f, 0.5f), Pt(0.4f, 0.3f, 0.5f), Pt(0.6f, 0.5f, 0.5f)), Color.Black, 0.004f)
        )
        var drawn: List<List<Ink>> = emptyList()
        val visible = mutableStateOf(Rect(0f, 0f, 600f, 800f))
        rule.setContent {
            Box(Modifier.size(300.dp, 400.dp)) {
                InkLayer("edge-test-page", strokes, night = false, visible = visible, onDrawn = { drawn = it })
            }
        }
        rule.waitUntil(10_000) { drawn.firstOrNull()?.isNotEmpty() == true }
        assertEquals(strokes, drawn.first())
    }
}
