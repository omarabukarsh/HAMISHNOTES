package com.hamishnotes.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** أشكال الشريط اللاصق: حفظ الشكل، وحدود كل شكل حول مساره. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TapeTest {
    private fun tape(style: Int, id: String = "a", pts: List<Float> = listOf(0.1f, 0.5f, 0.6f, 0.5f)) =
        Tape(id, 0, pts, 0.03f, 0xFFF2A65A.toInt(), style = style)

    @Test
    fun styleIsSavedAndOldTapesReadAsSoft() {
        val saved = tapesJson(mapOf(0 to TapeStyle.all.map { tape(it, id = "t$it") }))
        val back = parseTapes(saved)[0]!!
        assertEquals(TapeStyle.all, back.map { it.style })
        // شريط محفوظ قبل إضافة الأشكال (بلا "st")
        val old = org.json.JSONArray("""[{"id":"x","page":0,"p":[0.1,0.2,0.5,0.2],"w":0.028,"c":-1}]""")
        assertEquals(TapeStyle.SOFT, parseTapes(old)[0]!!.single().style)
    }

    @Test
    fun outlineStaysAroundThePath() {
        val w = 1000f
        for (st in TapeStyle.all) {
            val o = tapeOutline(tape(st), w)
            assertTrue("style $st has too few points", o.size >= 8)
            val half = 0.03f * w / 2f
            var i = 0
            while (i + 1 < o.size) {
                // داخل عرض الشريط (مع سماحة صغيرة للطرف المائل)
                assertTrue("style $st y=${o[i + 1]}", kotlin.math.abs(o[i + 1] - 500f) <= half + 0.5f)
                // داخل طوله: لا يمتد الطرف أبعد من نقطتي البداية والنهاية إلا قليلًا
                assertTrue("style $st x=${o[i]}", o[i] >= 100f - half && o[i] <= 600f + half)
                i += 2
            }
        }
    }

    @Test
    fun tornEdgesDoNotChangeWhenTheDraftIsSaved() {
        // المسودة والشريط المحفوظ لهما معرّفان مختلفان: يجب أن تبقى الحواف الممزّقة كما هي
        val draft = tapeOutline(tape(TapeStyle.TORN, id = "draft"), 800f)
        val saved = tapeOutline(tape(TapeStyle.TORN, id = "9f1c"), 800f)
        assertArrayEquals(draft, saved, 0.0001f)
    }

    @Test
    fun freeTapeWithManyPointsMakesAClosedOutline() {
        val pts = listOf(0.1f, 0.1f, 0.2f, 0.15f, 0.3f, 0.12f, 0.4f, 0.2f)
        for (st in TapeStyle.all) {
            val o = tapeOutline(tape(st, pts = pts), 500f)
            assertTrue(o.size >= 16)
            assertTrue(o.none { it.isNaN() })
        }
    }
}
