package com.hamishnotes.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** تقسيم خط اليد إلى أسطر وكلمات، ولغة كل كلمة من اتجاه كتابتها، وتوزيع النص المقروء. */
class HandwritingLayoutTest {

    /** خط من (x0, top) إلى (x1, bottom): اتجاه رسمه من x0 إلى x1. */
    private fun st(x0: Float, x1: Float, top: Float, bottom: Float, tool: Tool = Tool.FOUNTAIN, shape: Boolean = false) =
        Ink(
            listOf(Pt(x0, top, 0.5f), Pt((x0 + x1) / 2f, bottom, 0.5f), Pt(x1, (top + bottom) / 2f, 0.5f)),
            Color.Black, 0.002f, tool, shape = shape
        )

    /** كلمة إنجليزية: خطان يتقدمان من اليسار لليمين. */
    private fun en(l: Float, top: Float = 0.10f) = listOf(st(l, l + 0.03f, top, top + 0.03f), st(l + 0.035f, l + 0.06f, top, top + 0.03f))

    /** كلمة عربية: خطان يتقدمان من اليمين لليسار. */
    private fun ar(r: Float, top: Float = 0.10f) = listOf(st(r, r - 0.03f, top, top + 0.03f), st(r - 0.035f, r - 0.06f, top, top + 0.03f))

    private fun all(lines: List<HandwritingLayout.Line>) = lines.flatMap { l -> l.runs.flatMap { it.words } }

    @Test
    fun englishLineSplitsIntoWordsLeftToRight() {
        val strokes = en(0.10f) + en(0.20f) + en(0.30f)
        val lines = HandwritingLayout.lines(strokes, HwLang.BOTH, defaultRtl = true)
        assertEquals(1, lines.size)
        assertEquals(1, lines[0].runs.size)
        val run = lines[0].runs[0]
        assertTrue(!run.rtl)
        assertEquals(listOf(0.10f, 0.20f, 0.30f), run.words.map { it.l })
    }

    @Test
    fun arabicWordIsDetectedFromRightToLeftWriting() {
        val lines = HandwritingLayout.lines(ar(0.60f) + ar(0.50f), HwLang.BOTH, defaultRtl = false)
        val run = lines.single().runs.single()
        assertTrue(run.rtl)
        // ترتيب القراءة العربي: الكلمة الأولى هي اليمنى
        assertTrue(run.words[0].r > run.words[1].r)
    }

    @Test
    fun englishTermInsideArabicLineGetsItsOwnRun() {
        val strokes = ar(0.80f) + ar(0.70f) + en(0.40f)
        val runs = HandwritingLayout.lines(strokes, HwLang.BOTH, defaultRtl = true).single().runs
        assertEquals(listOf(true, false), runs.map { it.rtl })
        assertEquals(2, runs[0].words.size)
        assertEquals(1, runs[1].words.size)
    }

    @Test
    fun separateLinesStaySeparate() {
        val lines = HandwritingLayout.lines(en(0.1f, 0.10f) + en(0.1f, 0.20f), HwLang.BOTH, defaultRtl = false)
        assertEquals(2, lines.size)
    }

    @Test
    fun highlighterAndShapesAreNotRead() {
        val strokes = listOf(
            st(0.1f, 0.4f, 0.1f, 0.12f, tool = Tool.HIGHLIGHTER),
            st(0.1f, 0.4f, 0.3f, 0.4f, shape = true)
        )
        assertTrue(HandwritingLayout.lines(strokes, HwLang.BOTH, defaultRtl = true).isEmpty())
    }

    @Test
    fun languageSettingOverridesDirection() {
        val words = all(HandwritingLayout.lines(en(0.1f) + en(0.2f), HwLang.ARABIC, defaultRtl = false))
        assertTrue(words.all { it.rtl })
    }

    @Test
    fun arabicTokensMapFromTheRight() {
        val run = HandwritingLayout.lines(ar(0.60f) + ar(0.50f), HwLang.ARABIC, defaultRtl = true).single().runs.single()
        val pairs = HandwritingLayout.assign(run, "تعطى قبل")
        assertNotNull(pairs)
        assertEquals("تعطى", pairs!![0].second)
        assertTrue(pairs[0].first.r > pairs[1].first.r)
    }

    @Test
    fun brokenWordPartsAreJoinedWhenFewerWordsWereRead() {
        // ثلاثة أجزاء، والجزآن الأيمنان متقاربان: هما كلمة واحدة انفصلت حروفها
        val strokes = ar(0.80f) + ar(0.71f) + ar(0.50f)
        val run = HandwritingLayout.lines(strokes, HwLang.ARABIC, defaultRtl = true).single().runs.single()
        assertEquals(3, run.words.size)
        val pairs = HandwritingLayout.assign(run, "الأكل بساعة")!!
        assertEquals(2, pairs.size)
        assertEquals("الأكل", pairs[0].second)
        assertTrue(pairs[0].first.l < 0.70f && pairs[0].first.r >= 0.80f)
    }

    @Test
    fun moreWordsThanPartsFallsBackToReadingEachPart() {
        val run = HandwritingLayout.lines(en(0.1f), HwLang.ENGLISH, defaultRtl = false).single().runs.single()
        assertEquals(null, HandwritingLayout.assign(run, "two words"))
    }

    @Test
    fun recognizedWordsBecomeLinesOfText() {
        val words = listOf(
            OcrWord("Cardiac", 0.1f, 0.1f, 0.2f, 0.12f, 0), OcrWord("output", 0.21f, 0.1f, 0.3f, 0.12f, 0),
            OcrWord("=", 0.1f, 0.15f, 0.12f, 0.17f, 1), OcrWord("HR", 0.13f, 0.15f, 0.16f, 0.17f, 1)
        )
        assertEquals("Cardiac output\n= HR", handwritingText(words))
    }
}
