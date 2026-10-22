package com.mynotes.app

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * فهم شكل النص على الصفحة من مواضع الكلمات (لا من ترتيب القراءة الضوئية وحده):
 *  - الصفوف المرئية: كلمات على نفس الارتفاع، مرتبة من اليسار لليمين (يصلح للجداول)
 *  - خلايا الجدول: فراغ أفقي كبير بين كلمتين في نفس الصف
 *  - العناوين: نص أكبر بوضوح من نص الصفحة العادي
 *  - الفقرات: فراغ رأسي، أو سطر قصير منتهٍ بنقطة، أو بداية سطر بإزاحة
 *
 * الناتج نص بصيغة TextMark: فقرات يفصلها سطر فارغ، "# " للعنوان، و" | " بين خلايا الجدول.
 */
object TextLayout {
    private const val TERMINAL = ".!?؟…"

    private fun h(w: OcrWord) = w.b - w.t
    private fun cy(w: OcrWord) = (w.t + w.b) / 2f

    /** الارتفاع الوسيط لكلمات الصفحة = حجم النص العادي فيها. */
    fun bodyHeight(words: List<OcrWord>): Float {
        if (words.isEmpty()) return 0.012f
        val hs = words.map { h(it) }.sorted()
        return hs[hs.size / 2]
    }

    private fun median(v: List<Float>): Float = v.sorted()[v.size / 2]

    /**
     * يجمع الكلمات في صفوف مرئية (من الأعلى للأسفل). الأساس هو أسطر القراءة الضوئية نفسها
     * (فلا تخرج كلمة ذات صندوق أطول مثل «β-lactams» من سطرها)، ثم تُجمع الأسطر المتقابلة
     * أفقيًا (خلايا صف الجدول) في صف واحد مرتب من اليسار لليمين.
     */
    fun visualRows(words: List<OcrWord>, idx: Collection<Int>): List<List<Int>> {
        val valid = idx.filter { it in words.indices }
        if (valid.isEmpty()) return emptyList()
        class Ln(val ids: List<Int>, val t: Float, val b: Float, val l: Float)
        val lines = valid.groupBy { words[it].line }.values.map { g ->
            val ids = g.sortedBy { words[it].l }
            Ln(
                ids,
                median(ids.map { words[it].t }),
                median(ids.map { words[it].b }),
                ids.minOf { words[it].l }
            )
        }.sortedBy { (it.t + it.b) / 2f }

        val rows = ArrayList<MutableList<Ln>>()
        for (ln in lines) {
            val row = rows.lastOrNull()
            if (row != null) {
                val first = row[0]
                val ov = min(ln.b, first.b) - max(ln.t, first.t)
                val minH = min(ln.b - ln.t, first.b - first.t).coerceAtLeast(1e-6f)
                if (ov >= 0.5f * minH) {
                    row.add(ln)
                    continue
                }
            }
            rows.add(mutableListOf(ln))
        }
        return rows.map { r -> r.sortedBy { it.l }.flatMap { it.ids } }
    }

    /**
     * الكلمات التي مرّ عليها خط الهايلايتر. الكلمة مغطاة إذا مرّ الخط داخل عرضها وتداخل
     * شريطه مع ارتفاعها، ثم تُضم كل كلمات الصف المرئي الواقعة بين أول وآخر كلمة مغطاة فيه.
     */
    fun wordsUnder(words: List<OcrWord>, pts: List<Pt>, band: Float): List<Int> {
        if (words.isEmpty() || pts.isEmpty()) return emptyList()
        val samples = ArrayList<Pt>(pts.size * 2)
        samples.add(pts[0])
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val c = pts[i]
            val d = hypot(c.x - a.x, c.y - a.y)
            val n = (d / 0.003f).toInt().coerceIn(0, 200)
            for (k in 1..n) {
                val f = k.toFloat() / (n + 1)
                samples.add(Pt(a.x + (c.x - a.x) * f, a.y + (c.y - a.y) * f, c.p))
            }
            samples.add(c)
        }
        val half = band / 2f
        val hit = HashSet<Int>()
        for (i in words.indices) {
            val w = words[i]
            val slack = minOf(half, h(w) * 0.6f)
            if (samples.any { p -> p.x >= w.l && p.x <= w.r && p.y >= w.t - slack && p.y <= w.b + slack }) {
                hit.add(i)
            }
        }
        if (hit.isEmpty()) return emptyList()
        val out = HashSet<Int>()
        for (row in visualRows(words, words.indices.toList())) {
            val hitsInRow = row.filter { it in hit }
            if (hitsInRow.isEmpty()) continue
            val left = hitsInRow.minOf { words[it].l }
            val right = hitsInRow.maxOf { words[it].r }
            row.filter { words[it].l >= left - 1e-4f && words[it].r <= right + 1e-4f }.forEach { out.add(it) }
        }
        return out.toList()
    }

    /** مستطيل واحد لكل صف مرئي يغطي كلماته المختارة. */
    fun rectsFor(words: List<OcrWord>, idx: Collection<Int>): List<MarkRect> =
        visualRows(words, idx).map { row ->
            val ws = row.map { words[it] }
            val l = ws.minOf { it.l }
            val t = ws.minOf { it.t }
            val r = ws.maxOf { it.r }
            val b = ws.maxOf { it.b }
            val padY = (b - t) * 0.12f
            val padX = (b - t) * 0.10f
            MarkRect(l - padX, t - padY, r + padX, b + padY)
        }

    private class Row(val words: List<OcrWord>) {
        val l = words.minOf { it.l }
        val r = words.maxOf { it.r }
        val t = median(words.map { it.t })
        val b = median(words.map { it.b })
        val height: Float = median(words.map { it.b - it.t })
        /** خلايا الصف: فراغ أفقي أكبر من 1.4 ضعف ارتفاع السطر يبدأ خلية جديدة. */
        val cells: List<String> = run {
            val out = ArrayList<String>()
            val sb = StringBuilder()
            for ((k, w) in words.withIndex()) {
                if (k > 0) {
                    val prev = words[k - 1]
                    val gap = w.l - prev.r
                    val lineChanged = w.line != prev.line
                    if (gap > 1.4f * height || (lineChanged && gap > 0.6f * height)) {
                        out.add(sb.toString())
                        sb.setLength(0)
                    } else {
                        sb.append(' ')
                    }
                }
                sb.append(w.text)
            }
            out.add(sb.toString())
            out
        }
        val text: String get() = cells.joinToString(" ")
    }

    /**
     * يبني نص التمييز من الكلمات المختارة مع فهم الشكل.
     * @param pageWords كل كلمات الصفحة (لمعرفة حجم النص العادي)
     */
    fun buildText(pageWords: List<OcrWord>, idx: Collection<Int>): String {
        val rowsIdx = visualRows(pageWords, idx)
        if (rowsIdx.isEmpty()) return ""
        val rows = rowsIdx.map { r -> Row(r.map { pageWords[it] }) }
        val body = bodyHeight(pageWords)

        // الجدول: صفّان متتاليان على الأقل فيهما أكثر من خلية، أو صف واحد بثلاث خلايا أو أكثر
        val isTable = BooleanArray(rows.size)
        for (i in rows.indices) {
            val multi = rows[i].cells.size >= 2
            val prevMulti = i > 0 && rows[i - 1].cells.size >= 2
            val nextMulti = i < rows.lastIndex && rows[i + 1].cells.size >= 2
            isTable[i] = multi && (prevMulti || nextMulti || rows[i].cells.size >= 3)
        }
        val isHeading = BooleanArray(rows.size) { i ->
            val ratio = if (rows[i].words.size >= 2) 1.3f else 1.6f
            !isTable[i] && rows[i].height >= ratio * body
        }

        val blockL = rows.minOf { it.l }
        val blockR = rows.maxOf { it.r }
        val out = StringBuilder()
        var i = 0
        while (i < rows.size) {
            if (out.isNotEmpty()) out.append("\n\n")
            when {
                isTable[i] -> {
                    var j = i
                    val lines = ArrayList<String>()
                    while (j < rows.size && isTable[j]) {
                        lines.add(rows[j].cells.joinToString(" | ") { it.trim() })
                        j++
                    }
                    out.append(lines.joinToString("\n"))
                    i = j
                }
                isHeading[i] -> {
                    // أسطر العنوان المتتالية بنفس الحجم تُجمع في عنوان واحد
                    var j = i
                    val parts = ArrayList<String>()
                    while (j < rows.size && isHeading[j] &&
                        (j == i || abs(rows[j].height - rows[i].height) < 0.2f * rows[i].height)
                    ) {
                        parts.add(rows[j].text)
                        j++
                    }
                    out.append("# ").append(parts.joinToString(" "))
                    i = j
                }
                else -> {
                    var j = i
                    val lines = ArrayList<String>()
                    while (j < rows.size && !isTable[j] && !isHeading[j]) {
                        if (j > i && startsNewParagraph(rows[j - 1], rows[j], blockL, blockR)) break
                        lines.add(rows[j].text)
                        j++
                    }
                    out.append(lines.joinToString("\n"))
                    i = j
                }
            }
        }
        return out.toString().trim()
    }

    private fun startsNewParagraph(prev: Row, next: Row, blockL: Float, blockR: Float): Boolean {
        val lh = max(prev.height, next.height)
        // فراغ رأسي واضح بين السطرين
        if (next.t - prev.b > 0.9f * lh) return true
        // تغيّر واضح في حجم الخط
        if (abs(next.height - prev.height) > 0.25f * lh) return true
        val prevEnd = prev.text.trimEnd().lastOrNull()
        val ended = prevEnd != null && prevEnd in TERMINAL
        // سطر قصير منتهٍ بنقطة = نهاية فقرة
        if (ended && prev.r < blockR - 2.5f * lh) return true
        // بداية سطر بإزاحة بعد سطر منتهٍ = فقرة جديدة
        if (ended && next.l > blockL + 1.5f * lh) return true
        return false
    }

    // ---------- التداخل (لمعرفة تكرار تظليل نفس النص) ----------

    private fun area(r: MarkRect) = max(0f, r.r - r.l) * max(0f, r.b - r.t)

    fun area(rs: List<MarkRect>): Float = rs.sumOf { area(it).toDouble() }.toFloat()

    /** مساحة التقاطع بين مجموعتي مستطيلات. */
    fun overlap(a: List<MarkRect>, b: List<MarkRect>): Float {
        var s = 0f
        for (x in a) for (y in b) {
            val w = min(x.r, y.r) - max(x.l, y.l)
            val hh = min(x.b, y.b) - max(x.t, y.t)
            if (w > 0f && hh > 0f) s += w * hh
        }
        return s
    }
}
