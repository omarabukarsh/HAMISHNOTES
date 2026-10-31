package com.hamishnotes.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/** حالات اصطناعية قصيرة لقواعد لا يحتويها ملف antibiotics.pdf، بنتائج معروفة مسبقًا. */
class SummaryRulesTest {

    private fun mark(id: String, text: String, page: Int = 0, top: Float = 0.1f) =
        TextMark(id, page, listOf(MarkRect(0.1f, top, 0.9f, top + 0.02f)), Color(0xFFFFEB3B), text, 1L)

    @Test
    fun thousandsSeparatorsAndChemicalNamesStayIntact() {
        assertEquals("Give 1,000 mg then 2,500 IU of 3,4-diaminopyridine.", TextTidy.clean("Give 1,000 mg then 2,500 IU of 3,4-diaminopyridine."))
    }

    @Test
    fun spaceIsStillAddedAfterCommaBetweenWords() {
        assertEquals("fever, cough", TextTidy.clean("fever,cough"))
    }

    @Test
    fun mixedCaseTermsAreNotCapitalized() {
        assertEquals(listOf("pH is acidic in the stomach."), TextTidy.sentences("pH is acidic in the stomach."))
        assertEquals(listOf("mRNA is read by the 30S subunit."), TextTidy.sentences("mRNA is read by the 30S subunit."))
    }

    @Test
    fun plainLowercaseStartIsStillCapitalized() {
        assertEquals(listOf("Which causes lysis of the cell."), TextTidy.sentences("which causes lysis of the cell."))
    }

    @Test
    fun paragraphKeyStaysStableWhenMarkTextIsEdited() {
        val before = Summary.build(listOf(mark("a", "Penicillin binds PBPs.")), tidy = true)
        val after = Summary.build(listOf(mark("a", "Penicillin binds PBPs and inhibits transpeptidase.")), tidy = true)
        assertEquals(before.single().key, after.single().key)
    }

    @Test
    fun userEditsSurviveTextEdit() {
        val key = Summary.build(listOf(mark("a", "Penicillin binds PBPs.")), tidy = true).single().key
        val edits = SummaryEdits(kinds = mapOf(key to "h"))
        val view = Summary.view(listOf(mark("a", "Penicillin binds PBPs firmly.")), true, edits)
        assertEquals(SHeading::class, view.single()::class)
    }

    @Test
    fun separateParagraphsOfOneMarkAreNotMergedByTidy() {
        val blocks = Summary.build(listOf(mark("a", "Symptoms\n\nFever and cough are common.")), tidy = true)
        assertEquals(listOf("Symptoms", "Fever and cough are common."), blocks.map { (it as SPara).text })
    }

    @Test
    fun sentenceSplitAcrossMarksIsStillJoined() {
        val blocks = Summary.build(
            listOf(mark("a", "Penicillins inhibit the enzyme", top = 0.1f), mark("b", "transpeptidase in the cell wall.", top = 0.2f)),
            tidy = true
        )
        assertEquals(listOf("Penicillins inhibit the enzyme transpeptidase in the cell wall."), blocks.map { (it as SPara).text })
    }

    @Test
    fun literalPipeInParagraphIsNotATable() {
        val blocks = Summary.build(listOf(mark("a", "Where each group acts \\| مكان عمل كل مجموعة")), tidy = false)
        assertEquals(listOf("Where each group acts | مكان عمل كل مجموعة"), blocks.map { (it as SPara).text })
    }
}
