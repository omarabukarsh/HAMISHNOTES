package com.mynotes.app.golden

import androidx.compose.ui.graphics.Color
import com.mynotes.app.MarkMerge
import com.mynotes.app.OcrWord
import com.mynotes.app.Pt
import com.mynotes.app.SBlock
import com.mynotes.app.SHeading
import com.mynotes.app.SPara
import com.mynotes.app.STable
import com.mynotes.app.Sizes
import com.mynotes.app.Summary
import com.mynotes.app.SummaryEdits
import com.mynotes.app.TextLayout
import com.mynotes.app.TextMark
import com.mynotes.app.Tool

/** خط هايلايتر أفقي على ارتفاع y من x0 إلى x1 (إحداثيات نسبية لعرض الصفحة) كما يرسمه المستخدم. */
fun stroke(y: Float, x0: Float, x1: Float): List<Pt> {
    val n = 24
    return (0..n).map { k -> Pt(x0 + (x1 - x0) * k / n, y, 1f) }
}

/** تمييز واحد: خط هايلايتر واحد على صفحة بلون معيّن. */
data class Stroke(val page: Int, val pts: List<Pt>, val color: Color = Pipeline.YELLOW)

/** ناتج المسار: التمييزات بعد الدمج بترتيب القراءة، ثم الملخص المرتّب وكما التُقط. */
class PipelineResult(val marks: List<TextMark>, val tidy: List<SBlock>, val raw: List<SBlock>) {
    val markTexts: List<String> get() = marks.map { it.text }
}

/**
 * نفس مسار التطبيق دون واجهة: كلمات الصفحة ← TextLayout.wordsUnder (ما تحت خط الهايلايتر)
 * ← MarkMerge.add (بناء النص المظلّل والدمج) ← MarkMerge.readingOrder ← Summary.view.
 */
object Pipeline {
    val YELLOW = Color(0xFFFFEB3B)
    val GREEN = Color(0xFF8BC34A)

    /** عرض الهايلايتر الافتراضي في التطبيق (المقاس 4). */
    val WIDTH = Sizes.width(Tool.HIGHLIGHTER, 4)

    fun run(words: Map<Int, List<OcrWord>>, strokes: List<Stroke>): PipelineResult {
        val marks = HashMap<Int, List<TextMark>>()
        var clock = 1_000L
        var ids = 0
        for (s in strokes) {
            val ws = words[s.page] ?: error("الصفحة ${s.page} غير موجودة في الكلمات")
            val idx = TextLayout.wordsUnder(ws, s.pts, WIDTH)
            if (idx.isEmpty()) continue
            val res = MarkMerge.add(marks[s.page] ?: emptyList(), s.page, ws, idx, s.color, clock++) {
                "m" + (++ids)
            } ?: continue
            marks[s.page] = res.marks
        }
        val ordered = MarkMerge.readingOrder(marks.values)
        return PipelineResult(
            ordered,
            Summary.view(ordered, true, SummaryEdits()),
            Summary.view(ordered, false, SummaryEdits())
        )
    }

    /** شكل نصي ثابت لكل جزء من الملخص للمقارنة: "H: …" عنوان، "P: …" فقرة، "T: a | b || c | d" جدول. */
    fun render(b: SBlock): String = when (b) {
        is SHeading -> "H: " + b.text
        is SPara -> "P: " + b.text
        is STable -> "T: " + b.rows.joinToString(" || ") { it.joinToString(" | ") }
    }

    fun render(blocks: List<SBlock>): List<String> = blocks.map { render(it) }
}
