package com.hamishnotes.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** مكان نافذة الملصقات/الصور، ومقاس ومكان الكتابة بعد تحويلها إلى خط. */
class PlacementTest {

    // ------------------------------------------------ النافذة تحت زرها

    @Test
    fun tray_sitsUnderItsButton() {
        // شاشة 2000×1300، زر الصور وسطه 1000 وأسفله 212، والنافذة بعرض 456
        val p = trayPlacement(2000f, 1300f, 1000f, 212f, 456f, 12f, 9f, 105f)
        assertEquals(1000f - 228f, p.x, 0.5f)
        assertEquals(221f, p.y, 0.5f)
        assertEquals(0.5f, p.pivot, 0.01f)
        assertEquals(1300f - 221f - 12f, p.maxH, 0.5f)
    }

    @Test
    fun tray_staysInsideScreenNearEdges() {
        val right = trayPlacement(2000f, 1300f, 1990f, 212f, 456f, 12f, 9f, 105f)
        assertEquals(2000f - 456f - 12f, right.x, 0.5f)
        assertTrue("arrow still points at the button", right.pivot > 0.85f)
        val left = trayPlacement(2000f, 1300f, 20f, 212f, 456f, 12f, 9f, 105f)
        assertEquals(12f, left.x, 0.5f)
        assertTrue(left.pivot < 0.1f)
    }

    @Test
    fun tray_withoutButton_goesTopRight() {
        val p = trayPlacement(2000f, 1300f, null, null, 456f, 12f, 9f, 105f)
        assertEquals(2000f - 456f - 12f, p.x, 0.5f)
        assertEquals(114f, p.y, 0.5f)
    }

    // ------------------------------------------------ مقاس الكتابة المحوّلة

    @Test
    fun size_matchesHandwritingHeight() {
        // «Micro» بخط يدوي: ارتفاع 0.085 من عرض الصفحة وعرض 0.26؛ حبر الكلمة بالخط 0.72×2.6 من حجمه
        val size = AutoFont.sizeFor(0.26f, 0.085f, 2.6f, 0.72f)
        val inkH = size * 0.72f
        assertTrue("converted height $inkH vs handwriting 0.085", inkH in 0.085f * 0.7f..0.085f * 1.3f)
    }

    @Test
    fun size_doesNotFollowAVeryWideScrawl() {
        val size = AutoFont.sizeFor(0.9f, 0.05f, 2.6f, 0.72f)
        assertTrue(size * 0.72f <= 0.05f * 1.3f + 1e-4f)
    }

    // ------------------------------------------------ الجذب إلى سطر الدفتر

    private val ruled = (0 until 25).map { 0.1135f + it * 0.0453f }

    @Test
    fun snap_bottomSlightlyBelowLine_sitsOnThatLine() {
        // الكتابة نزلت تحت السطر قليلاً (كما في الصورة): تبقى على نفس السطر لا السطر التالي
        assertEquals(0.1135f, AutoFont.snapBase(ruled, 0.12f, 2, 0.05f, null, null), 1e-5f)
    }

    @Test
    fun snap_levels() {
        val base = 0.1135f + 0.0453f * 0.4f
        assertEquals(base, AutoFont.snapBase(ruled, base, 0, 0.05f, null, null), 1e-6f)
        assertEquals(base, AutoFont.snapBase(ruled, base, 1, 0.05f, null, null), 1e-6f)
        assertEquals(0.1135f, AutoFont.snapBase(ruled, base, 2, 0.05f, null, null), 1e-5f)
        assertEquals(0.1135f + 0.0453f, AutoFont.snapBase(ruled, 0.1135f + 0.0453f * 0.7f, 3, 0.05f, null, null), 1e-5f)
    }

    @Test
    fun unifiedSpacing_onPlainPage() {
        // بلا أسطر: السطر التالي على مسافة ثابتة = الحجم × المسافة
        val b = AutoFont.snapBase(emptyList(), 0.31f, 2, 0.04f, 0.2f, 1.5f)
        assertEquals(0.2f + 2 * 0.06f, b, 1e-5f)
        // بعيد جداً: لا يُجذب
        assertEquals(0.6f, AutoFont.snapBase(emptyList(), 0.6f, 2, 0.04f, 0.2f, 1.5f), 1e-6f)
    }

    // ------------------------------------------------ سحب عنصر من النافذة

    @Test
    fun ghost_isCentredUnderTheFinger() {
        val g = ghostRect(Offset(500f, 300f), 120f, 80f)
        assertEquals(500f, g.center.x, 1e-3f)
        assertEquals(300f, g.center.y, 1e-3f)
        assertEquals(120f, g.width, 1e-3f)
        assertEquals(80f, g.height, 1e-3f)
    }

    @Test
    fun landed_matchesWhereTheItemIsPlaced() {
        // صفحة بعرض 1000 تبدأ عند (100, 200): الإفلات في الوسط = العنصر وسطه عند الإصبع
        val page = Rect(100f, 200f, 1100f, 1614f)
        val r = landedRect(page, Offset(0.5f, 0.6f), 0.2f, 0.2f)
        assertEquals(100f + 500f, r.center.x, 0.5f)
        assertEquals(200f + 600f, r.center.y, 0.5f)
        // قرب الحافة: يبقى داخل الصفحة كما تضعه insertImage
        val edge = landedRect(page, Offset(0.02f, 0.01f), 0.2f, 0.2f)
        assertEquals(page.left, edge.left, 0.5f)
        assertEquals(page.top, edge.top, 0.5f)
    }

    @Test
    fun edgeScroll_onlyInsideTheEdgeZones() {
        val vp = Rect(0f, 100f, 800f, 1000f)
        assertEquals(0f, edgeSpeed(Offset(400f, 500f), vp, 72f, 18f), 0f)
        assertTrue(edgeSpeed(Offset(400f, 990f), vp, 72f, 18f) > 0f)
        assertTrue(edgeSpeed(Offset(400f, 110f), vp, 72f, 18f) < 0f)
        // فوق الصفحات (عند النافذة أو شريط الأدوات) أو خارجها: لا تمرير
        assertEquals(0f, edgeSpeed(Offset(400f, 50f), vp, 72f, 18f), 0f)
        assertEquals(0f, edgeSpeed(Offset(900f, 990f), vp, 72f, 18f), 0f)
        // أسرع كلما اقترب الإصبع من الحافة، ولا يتجاوز الحد
        assertTrue(edgeSpeed(Offset(400f, 999f), vp, 72f, 18f) > edgeSpeed(Offset(400f, 940f), vp, 72f, 18f))
        assertTrue(edgeSpeed(Offset(400f, 999f), vp, 72f, 18f) <= 18f)
    }
}
