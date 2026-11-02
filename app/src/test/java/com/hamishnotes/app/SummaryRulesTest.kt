package com.hamishnotes.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/** حالات اصطناعية قصيرة لقواعد لا يحتويها ملف antibiotics.pdf، بنتائج معروفة مسبقًا. */
class SummaryRulesTest {

    private fun mark(id: String, text: String, page: Int = 0, top: Float = 0.1f) =
        TextMark(id, page, listOf(MarkRect(0.1f, top, 0.9f, top + 0.02f)), Color(0xFFFFEB3B), text, 1L)

    @Test
    fun spaceIsStillAddedAfterCommaBetweenWords() {
        assertEquals("fever, cough", TextTidy.clean("fever,cough"))
    }

    @Test
    fun plainLowercaseStartIsStillCapitalized() {
        assertEquals(listOf("Which causes lysis of the cell."), TextTidy.sentences("which causes lysis of the cell."))
    }

    @Test
    fun sentenceSplitAcrossMarksIsStillJoined() {
        val blocks = Summary.build(
            listOf(mark("a", "Penicillins inhibit the enzyme", top = 0.1f), mark("b", "transpeptidase in the cell wall.", top = 0.2f)),
            tidy = true
        )
        assertEquals(listOf("Penicillins inhibit the enzyme transpeptidase in the cell wall."), blocks.map { (it as SPara).text })
    }

}
