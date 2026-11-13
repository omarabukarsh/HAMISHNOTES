package com.hamishnotes.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** سقفا التباعد: 180 يومًا على الأكثر، ولا تتأجل بطاقة إلى ما بعد امتحان مادتها. */
class FlashcardsScheduleTest {
    private val today get() = Flashcards.today()

    private fun repeat(rating: Int, times: Int, examDay: Long? = null): SrsState {
        var s: SrsState? = null
        repeat(times) { s = Flashcards.schedule(s, rating, examDay) }
        return s!!
    }

    @Test
    fun intervalNeverExceedsTheCap() {
        val good = repeat(2, 20)
        val easy = repeat(3, 20)
        assertEquals(Flashcards.MAX_INTERVAL_DAYS, good.interval)
        assertEquals(Flashcards.MAX_INTERVAL_DAYS, easy.interval)
        assertEquals(today + Flashcards.MAX_INTERVAL_DAYS, easy.due)
        assertTrue(easy.ease <= Flashcards.MAX_EASE)
    }

    @Test
    fun cardIsNeverScheduledPastTheExam() {
        val exam = today + 30
        val s = repeat(3, 10, exam)
        // آخر موعد: اليوم الذي قبل الامتحان
        assertEquals(exam - 1, s.due)
    }

    @Test
    fun examTomorrowStillMovesTheCardForward() {
        val exam = today + 1
        val s = Flashcards.schedule(SrsState(2.5f, 10, today, 3), 2, exam)
        assertEquals(exam, s.due)
        assertEquals(1, s.interval)
    }

    @Test
    fun cardsScheduledPastTheExamComeBackTheDayBefore() {
        val exam = today + 10
        val card = Card("c1", "front", "back")
        val far = SrsState(2.5f, 200, today + 200, 6)
        // بلا امتحان: ليست مستحقة اليوم
        assertTrue(Flashcards.dueCards(listOf(card), mapOf("c1" to far)).isEmpty())
        assertEquals(exam - 1, Flashcards.effectiveDue(far, exam))
        // يوم ما قبل الامتحان: مستحقة
        val dayBefore = SrsState(2.5f, 200, today + 200, 6)
        assertEquals(listOf(card), Flashcards.dueCards(listOf(card), mapOf("c1" to dayBefore)) { today + 1 })
    }

    @Test
    fun againStaysDueToday() {
        val s = Flashcards.schedule(SrsState(2.5f, 40, today + 40, 5), 0, today + 5)
        assertEquals(today, s.due)
        assertEquals(0, s.interval)
    }
}
