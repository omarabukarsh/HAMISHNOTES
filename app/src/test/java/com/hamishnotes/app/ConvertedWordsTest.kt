package com.hamishnotes.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/** كلمات الكتابة المحوّلة تُقرأ من الحروف نفسها (للهايلايتر والبحث والملخصات). */
class ConvertedWordsTest {
    private fun letter(x0: Float, y0: Float, x1: Float, y1: Float, word: String, wid: Long) = Ink(
        InkFill.encode(listOf(floatArrayOf(x0, y0, x1, y0, x1, y1, x0, y1))), Color.Black, 0f, Tool.BALLPOINT,
        fill = true, word = word, wid = wid
    )

    @Test
    fun lettersOfAWord_becomeOneWord_inReadingOrder() {
        // سطر إنجليزي: Ahmed (حرفان) ثم Omar، وسطر ثانٍ تحته
        val page = listOf(
            letter(0.30f, 0.10f, 0.34f, 0.15f, "Omar", 2L),
            letter(0.10f, 0.10f, 0.14f, 0.15f, "Ahmed", 1L),
            letter(0.15f, 0.10f, 0.20f, 0.15f, "Ahmed", 1L),
            letter(0.10f, 0.30f, 0.20f, 0.35f, "Next", 3L)
        )
        val words = ConvertedWords.read(page)
        assertEquals(listOf("Ahmed", "Omar", "Next"), words.map { it.text })
        assertEquals(0.10f, words[0].l, 1e-6f); assertEquals(0.20f, words[0].r, 1e-6f)
        assertEquals(words[0].line, words[1].line)
        assertEquals(words[0].line + 1, words[2].line)
    }

    @Test
    fun arabicLine_readsRightToLeft_andCopiesStaySeparate() {
        val page = listOf(
            letter(0.50f, 0.10f, 0.60f, 0.15f, "مرحبا", 7L),
            letter(0.30f, 0.10f, 0.40f, 0.15f, "بكم", 8L),
            // نسخة من «بكم» بعيدة (نسخ ولصق): كلمة مستقلة
            letter(0.30f, 0.60f, 0.40f, 0.65f, "بكم", 8L)
        )
        val words = ConvertedWords.read(page)
        assertEquals(listOf("مرحبا", "بكم", "بكم"), words.map { it.text })
        assertEquals(0.60f, words[2].t, 1e-6f)
    }

    @Test
    fun letterBelongsToTheWordAroundItsCentre() {
        val words = listOf(Triple("Ahmed", 0f, 2.5f), Triple("Omar", 2.8f, 5f))
        assertEquals(0, ConvertedWords.wordOf(listOf(floatArrayOf(1f, 0f, 1.5f, 0f, 1.5f, 1f)), words))
        assertEquals(1, ConvertedWords.wordOf(listOf(floatArrayOf(3f, 0f, 3.6f, 0f, 3.6f, 1f)), words))
    }
}
