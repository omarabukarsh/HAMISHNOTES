package com.hamishnotes.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/** ترتيب التمييزات في الملخص، ومفاتيح تعديلات الملخص من النسخ السابقة. */
class ReadingOrderTest {
    private fun mark(id: String, text: String, l: Float, t: Float, r: Float, b: Float, page: Int = 0) =
        TextMark(id, page, listOf(MarkRect(l, t, r, b)), Color.Yellow, text, 0L)

    /** على سطر واحد: كلمة أعلى قليلًا من جارتها لا تسبقها إن كانت بعدها في السطر. */
    @Test
    fun sameLineFollowsTheLineNotTinyHeightDifferences() {
        val second = mark("b", "second part", 0.5f, 0.100f, 0.8f, 0.120f)
        val first = mark("a", "first part", 0.1f, 0.102f, 0.4f, 0.121f)
        val nextLine = mark("c", "next line", 0.1f, 0.15f, 0.4f, 0.17f)
        assertEquals(listOf("a", "b", "c"), MarkMerge.readingOrder(listOf(listOf(second, nextLine, first))).map { it.id })
    }

    /** في العربية: السطر يُقرأ من اليمين. */
    @Test
    fun arabicLineReadsRightToLeft() {
        val left = mark("l", "الجزء الثاني", 0.1f, 0.100f, 0.4f, 0.12f)
        val right = mark("r", "الجزء الأول", 0.5f, 0.101f, 0.9f, 0.12f)
        assertEquals(listOf("r", "l"), MarkMerge.readingOrder(listOf(listOf(left, right))).map { it.id })
    }

    @Test
    fun pagesStayInOrder() {
        val p1 = mark("p1", "page one", 0.1f, 0.5f, 0.4f, 0.52f, page = 1)
        val p0 = mark("p0", "page zero", 0.1f, 0.9f, 0.4f, 0.92f, page = 0)
        assertEquals(listOf("p0", "p1"), MarkMerge.readingOrder(listOf(listOf(p1), listOf(p0))).map { it.id })
    }

    /** تعديل «عنوان» حُفظ بمفتاح قديم (نص الفقرة) يبقى يعمل، ويُترجم إلى المفتاح الثابت عند أي تعديل. */
    @Test
    fun legacyTidyKeysStillApply() {
        val m = mark("m1", "Penicillin inhibits cell wall synthesis.", 0.1f, 0.1f, 0.9f, 0.12f)
        val blocks = Summary.build(listOf(m), tidy = true)
        val stable = blocks.single().key
        assertEquals("t:m1#0", stable)
        val legacyKey = "penicillin inhibits cell wall synthesis"
        val old = SummaryEdits(kinds = mapOf(legacyKey to "h"))
        val shown = Summary.view(listOf(m), tidy = true, edits = old)
        assertEquals(SHeading::class, shown.single()::class)
        assertEquals(mapOf(stable to "h"), Summary.migrateLegacyKeys(listOf(m), old).kinds)
    }
}
