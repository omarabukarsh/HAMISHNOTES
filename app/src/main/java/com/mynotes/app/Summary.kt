package com.mynotes.app

/**
 * بنية الملخص: عناوين وفقرات وجداول، دون أسماء الصفحات.
 * يستخدمها درج «ملخصات» وتصدير PDF معًا حتى يظهر الملخص بنفس الشكل في الاثنين.
 */
sealed interface SBlock {
    /** التمييزات التي جاء منها هذا الجزء (للانتقال إلى موضعها). */
    val marks: List<TextMark>
}

data class SHeading(val text: String, override val marks: List<TextMark>) : SBlock

/** فقرة؛ emphasis = أجزاء تظهر بخط عريض وتظليل (نص ظُلّل مرتين). */
data class SPara(val text: String, val emphasis: List<String>, override val marks: List<TextMark>) : SBlock

data class STable(val rows: List<List<String>>, override val marks: List<TextMark>) : SBlock

object Summary {
    private const val TERMINAL = ".!?؟…"

    private sealed interface Piece
    private class PHeading(val text: String) : Piece
    private class PTable(val rows: List<List<String>>) : Piece
    private class PPara(val text: String) : Piece

    /** يقرأ صيغة نص التمييز: فقرات بسطر فارغ، "# " عنوان، " | " بين خلايا الجدول. */
    private fun parse(text: String): List<Piece> {
        val out = ArrayList<Piece>()
        for (raw in text.replace("\r", "").split(Regex("\\n\\s*\\n"))) {
            val lines = raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue
            when {
                lines[0].startsWith("# ") -> {
                    out.add(PHeading(lines[0].removePrefix("# ").trim()))
                    if (lines.size > 1) out.add(PPara(lines.drop(1).joinToString("\n")))
                }
                lines.all { it.contains('|') } ->
                    out.add(PTable(lines.map { l -> l.split('|').map { it.trim() } }))
                else -> out.add(PPara(lines.joinToString("\n")))
            }
        }
        return out
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** هل الفقرة التالية تكمل السابقة؟ */
    private fun continues(prev: String, next: String): Boolean {
        val end = prev.trimEnd().lastOrNull() ?: return false
        if (end == ':' || end == '：') return false
        val startsLower = next.firstOrNull()?.isLowerCase() == true
        return end !in TERMINAL || startsLower
    }

    private fun emphasisOf(m: TextMark, text: String): List<String> =
        if (m.level >= 2) listOf(text) else m.emphasis

    /**
     * @param tidy true = ترتيب: تنظيف، دمج المقاطع التي تكمل بعضها، وجمع المكرر كنص مهم.
     *             false = كما التُقط: كل تمييز كما هو (مع العناوين والجداول).
     */
    fun build(marksInOrder: List<TextMark>, tidy: Boolean): List<SBlock> {
        val out = ArrayList<SBlock>()
        val seen = HashMap<String, Int>() // نص مطبَّع ← موضع الفقرة في out
        for (m in marksInOrder) {
            for (p in parse(m.text)) {
                when (p) {
                    is PHeading -> {
                        val t = if (tidy) TextTidy.clean(p.text) else p.text
                        val last = out.lastOrNull()
                        if (tidy && last is SHeading && norm(last.text) == norm(t)) continue
                        out.add(SHeading(t, listOf(m)))
                    }
                    is PTable -> out.add(STable(p.rows, listOf(m)))
                    is PPara -> {
                        if (!tidy) {
                            out.add(SPara(p.text.replace("\n", " ").trim(), emphasisOf(m, p.text.replace("\n", " ").trim()), listOf(m)))
                            continue
                        }
                        val c = TextTidy.clean(p.text)
                        if (c.isEmpty()) continue
                        val key = norm(c)
                        // نفس النص ظُلّل في تمييز آخر: لا نكرره، بل نجعله مهمًا
                        val dup = seen[key]
                        if (dup != null) {
                            val old = out[dup] as SPara
                            out[dup] = old.copy(emphasis = (old.emphasis + c).distinct(), marks = old.marks + m)
                            continue
                        }
                        val last = out.lastOrNull()
                        if (last is SPara && continues(last.text, c)) {
                            out[out.lastIndex] = SPara(
                                "${last.text} $c",
                                (last.emphasis + emphasisOf(m, c)).distinct(),
                                last.marks + m
                            )
                        } else {
                            out.add(SPara(c, emphasisOf(m, c), listOf(m)))
                        }
                        seen[key] = out.lastIndex
                    }
                }
            }
        }
        if (!tidy) return out
        // تنظيف نهائي للفقرات: جمل واضحة تبدأ بحرف كبير وتنتهي بنقطة
        return out.map { b ->
            if (b is SPara) b.copy(text = TextTidy.sentences(b.text).joinToString(" ")) else b
        }
    }

    /** نطاقات الأجزاء المهمة داخل نص الفقرة (بدون حساسية لحالة الأحرف). */
    fun emphasisRanges(text: String, emphasis: List<String>): List<IntRange> {
        if (emphasis.isEmpty()) return emptyList()
        val out = ArrayList<IntRange>()
        for (e in emphasis) {
            val key = e.trim().trimEnd { it in TERMINAL }
            if (key.length < 2) continue
            var from = 0
            while (true) {
                val i = text.indexOf(key, from, ignoreCase = true)
                if (i < 0) break
                out.add(i until i + key.length)
                from = i + key.length
            }
        }
        return out.sortedBy { it.first }
    }

    /** نص الملخص كاملًا للنسخ والمشاركة. */
    fun plainText(title: String, blocks: List<SBlock>): String {
        val sb = StringBuilder()
        sb.append(title).append("\n")
        for (b in blocks) {
            sb.append('\n')
            when (b) {
                is SHeading -> sb.append(b.text).append('\n')
                is SPara -> sb.append(b.text).append('\n')
                is STable -> b.rows.forEach { sb.append(it.joinToString(" | ")).append('\n') }
            }
        }
        return sb.toString().trimEnd()
    }
}
