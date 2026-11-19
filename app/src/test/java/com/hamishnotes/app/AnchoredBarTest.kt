package com.hamishnotes.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** شريط العنصر المحدد يظهر فوق العنصر مباشرة، وتحته إن لم يتسع فوقه. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h1000dp")
class AnchoredBarTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var barRect = Rect.Zero
    private var pageRect = Rect.Zero

    private fun show(target: Rect): DocSession {
        val s = DocSession(DocEntry("t", "t", DocType.PDF, "t.pdf", 0, 1, 0, 0, false, 0))
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(state = s.listState, modifier = Modifier.fillMaxSize().padding(top = 80.dp).onGloballyPositioned { s.pagesViewport = it.boundsInWindow() }) {
                    item {
                        Box(
                            Modifier.padding(horizontal = 100.dp).fillMaxWidth().height(800.dp).background(Color.White)
                                .onGloballyPositioned { c ->
                                    val p = c.localToWindow(Offset.Zero)
                                    pageRect = Rect(p, c.size.toSize())
                                    s.pageWindowRects[0] = pageRect; s.pageRectsVersion++
                                }
                        )
                    }
                }
                AnchoredBar(s, 0, target) {
                    Box(Modifier.size(200.dp, 42.dp).testTag("bar").onGloballyPositioned { barRect = it.boundsInWindow() })
                }
            }
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        return s
    }

    @Test
    fun barSitsRightAboveTheElement() {
        show(Rect(0.2f, 0.4f, 0.6f, 0.5f))
        val w = pageRect.width
        val elTop = pageRect.top + 0.4f * w
        assertTrue("bar bottom ${barRect.bottom} should be above element top $elTop", barRect.bottom < elTop)
        assertTrue("bar too far from element", elTop - barRect.bottom < 20f * rule.density.density)
        assertEquals(pageRect.left + 0.4f * w, barRect.center.x, 2f)
    }

    @Test
    fun nearTheTopTheBarGoesBelow() {
        show(Rect(0.2f, 0.0f, 0.6f, 0.05f))
        val w = pageRect.width
        assertTrue("bar top ${barRect.top} should be below element bottom", barRect.top > pageRect.top + 0.05f * w)
    }
}
