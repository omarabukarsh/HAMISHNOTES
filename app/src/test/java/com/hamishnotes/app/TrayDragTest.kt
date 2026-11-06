package com.hamishnotes.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * سحب ملصق من النافذة إلى الصفحة باللمس الحقيقي (أحداث إصبع): العنصر يبقى تحت الإصبع في كل خطوة،
 * ولا يختفي بعد الإفلات حتى يُضاف إلى الصفحة.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h1000dp")
class TrayDragTest {
    @get:Rule val rule = createEmptyComposeRule()

    private lateinit var s: DocSession
    private val payload = TrayPayload.Sticky(0xFFFFE27A.toInt())
    private var pageRect = Rect.Zero
    private var tileRect = Rect.Zero

    private class Placed(val page: Int, val at: Offset, val done: () -> Unit)
    private val placed = ArrayList<Placed>()
    private var oldPlacer: ((DocSession, TrayPayload, Int, Offset, () -> Unit) -> Unit)? = null

    @Before
    fun setUp() {
        s = DocSession(DocEntry("t", "t", DocType.PDF, "t.pdf", 0, 1, 0, 0, false, 0))
        oldPlacer = TrayDrag.placer
        TrayDrag.placer = { _, _, page, at, done -> placed.add(Placed(page, at, done)) }
        val act = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        act.setContent {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    // «النافذة»: مربع ملصق واحد
                    Box(
                        Modifier.size(80.dp).testTag("tile").background(Color.White)
                            .onGloballyPositioned { tileRect = it.boundsInWindow() }
                            .dragToPage(s, payload, null)
                    )
                    // الصفحات
                    LazyColumn(state = s.listState, modifier = Modifier.fillMaxSize().onGloballyPositioned { s.pagesViewport = it.boundsInWindow() }) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().height(700.dp).background(Color(0xFFFFF7E3)).onGloballyPositioned { c ->
                                    val p = c.localToWindow(Offset.Zero)
                                    pageRect = Rect(p, c.size.toSize())
                                    s.pageWindowRects[0] = pageRect
                                }
                            )
                        }
                    }
                }
                TrayDragGhost(s)
            }
        }
        rule.waitForIdle()
    }

    @After
    fun tearDown() {
        oldPlacer?.let { TrayDrag.placer = it }
        TrayDrag.cancel()
        TrayDrag.landing = null
    }

    private fun ghost(): Rect = rule.onNodeWithTag("trayGhost").getBoundsInRoot().let { b ->
        with(rule.density) { Rect(b.left.toPx(), b.top.toPx(), b.right.toPx(), b.bottom.toPx()) }
    }

    private fun assertUnderFinger(fingerWin: Offset, what: String) {
        val g = ghost()
        assertEquals("$what: ghost x center", fingerWin.x, g.center.x, 2f)
        assertEquals("$what: ghost y center", fingerWin.y, g.center.y, 2f)
    }

    @Test
    fun itemFollowsTheFingerAllTheWayAndStaysUntilPlaced() {
        val start = Offset(40f, 40f) * rule.density.density
        rule.onNodeWithTag("tile").performTouchInput { down(start) }
        // سحب جانباً يُخرج العنصر فوراً
        var finger = start
        rule.onNodeWithTag("tile").performTouchInput {
            finger += Offset(60f, 0f)
            moveTo(finger)
        }
        rule.waitForIdle()
        assertTrue("item lifted", TrayDrag.payload != null)
        assertUnderFinger(tileRect.topLeft + finger, "beside the tray")

        // نزولاً إلى الصفحة خطوة خطوة: في كل خطوة العنصر تحت الإصبع
        for (i in 1..12) {
            rule.onNodeWithTag("tile").performTouchInput {
                finger += Offset(15f, 40f)
                moveTo(finger)
            }
            rule.mainClock.advanceTimeBy(16)
            assertUnderFinger(tileRect.topLeft + finger, "step $i")
        }
        // فوق الصفحة يكبر إلى حجمه الحقيقي (ملاحظة لاصقة = 0.2 من عرض الصفحة)
        rule.mainClock.advanceTimeBy(1000)
        val g = ghost()
        assertEquals("real size on the page", pageRect.width * 0.2f, g.width, 3f)
        assertUnderFinger(tileRect.topLeft + finger, "on the page")
        assertEquals(0, TrayDrag.overPage)

        // الإفلات: يوضع حيث الإصبع، ويبقى ظاهراً حتى تكتمل الإضافة
        rule.onNodeWithTag("tile").performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(1, placed.size)
        val fingerWin = tileRect.topLeft + finger
        val at = placed[0].at
        assertEquals(0, placed[0].page)
        assertEquals((fingerWin.x - pageRect.left) / pageRect.width, at.x, 0.002f)
        assertEquals((fingerWin.y - pageRect.top) / pageRect.width, at.y, 0.002f)
        rule.mainClock.advanceTimeBy(2000)
        rule.onNodeWithTag("trayGhost").assertExists("still visible while the item is being added")
        val landed = ghost()
        assertEquals(fingerWin.x, landed.center.x, 2f)
        assertEquals(fingerWin.y, landed.center.y, 2f)

        // بعد الإضافة يختفي الظل
        placed[0].done()
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        rule.onNodeWithTag("trayGhost").assertDoesNotExist()
    }

    @Test
    fun droppingBackOnTheTrayCancels() {
        val start = Offset(40f, 40f) * rule.density.density
        rule.onNodeWithTag("tile").performTouchInput {
            down(start)
            moveTo(start + Offset(60f, 0f))
            moveTo(start + Offset(60f, 400f))
            moveTo(start + Offset(-20f, 0f))
        }
        TrayDrag.trayRect = tileRect
        rule.onNodeWithTag("tile").performTouchInput { moveTo(start); up() }
        rule.waitForIdle()
        assertEquals(0, placed.size)
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag("trayGhost").assertDoesNotExist()
        TrayDrag.trayRect = Rect.Zero
    }

    @Test
    fun nearTheBottomEdgeThePagesScroll() {
        // صفحات أطول من الشاشة: الإصبع قرب الحافة السفلية يحرّك الصفحات
        val start = Offset(40f, 40f) * rule.density.density
        rule.onNodeWithTag("tile").performTouchInput { down(start); moveTo(start + Offset(60f, 0f)) }
        val bottom = s.pagesViewport.bottom - tileRect.top - 10f
        rule.onNodeWithTag("tile").performTouchInput { moveTo(Offset(start.x + 60f, bottom)) }
        val before = s.listState.firstVisibleItemScrollOffset
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        assertTrue("scrolled: $before → ${s.listState.firstVisibleItemScrollOffset}", s.listState.firstVisibleItemScrollOffset > before)
        rule.onNodeWithTag("tile").performTouchInput { up() }
    }
}
