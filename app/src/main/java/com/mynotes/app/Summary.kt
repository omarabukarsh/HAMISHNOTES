package com.mynotes.app

/**
 * بنية الملخص: عناوين وفقرات وجداول، دون أسماء الصفحات.
 * يستخدمها درج «ملخصات» وتصدير PDF معًا حتى يظهر الملخص بنفس الشكل في الاثنين.
 */
sealed interface SBlock {
    /** التمييزات التي جاء منها هذا الجزء (للانتقال إلى موضعها). */
    val marks: List<TextMark>
    /** مفتاح ثابت للجزء (أول تمييز + رقمه) تُربط به تعديلات المستخدم. */
    val key: String
}

data class SHeading(val text: String, override val marks: List<TextMark>, override val key: String = "") : SBlock

/** فقرة؛ emphasis = أجزاء تظهر بخط عريض وتظليل (نص ظُلّل مرتين). */
data class SPara(
    val text: String,
    val emphasis: List<String>,
    override val marks: List<TextMark>,
    override val key: String = ""
) : SBlock

/** header = الصف الأول عناوين أعمدة. */
data class STable(
    val rows: List<List<String>>,
    override val marks: List<TextMark>,
    override val key: String = "",
    val header: Boolean = true
) : SBlock

/**
 * جدول من صنع المستخدم أو جدول عدّله.
 *  keys    الأجزاء التي يضمها (تختفي من مكانها ويظهر الجدول مكان أولها)
 *  cols    عدد الأعمدة عند توزيع النصوص المختارة (0 = كل نص صف بخلاياه)
 *  oneRow  (قديم) كل النصوص في صف واحد
 *  rows    محتوى عدّله المستخدم يدويًا في محرر الجدول (إن وُجد يُعرض كما هو)
 */
data class TableGroup(
    val id: String,
    val keys: List<String>,
    val oneRow: Boolean = false,
    val cols: Int = 0,
    val rows: List<List<String>>? = null,
    val header: Boolean = true
)

/**
 * تعديلات المستخدم على الملخص، تُطبَّق فوق الترتيب التلقائي (الذي يبقى يعمل كما هو):
 *  order  ترتيب يدوي للأجزاء
 *  kinds  "h" عنوان، "p" نص عادي
 *  tables جداول من أجزاء مختارة
 */
data class SummaryEdits(
    val order: List<String> = emptyList(),
    val kinds: Map<String, String> = emptyMap(),
    val tables: List<TableGroup> = emptyList()
) {
    fun isEmpty(): Boolean = order.isEmpty() && kinds.isEmpty() && tables.isEmpty()
}

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
        val mode = if (tidy) "t" else "r"
        for (m in marksInOrder) {
            for ((pi, p) in parse(m.text).withIndex()) {
                // مفتاح ثابت: الوضع + التمييز + رقم الجزء داخل نصه (لا يتغير بدمج أجزاء أخرى)
                val key = "$mode:${m.id}#$pi"
                when (p) {
                    is PHeading -> {
                        val t = if (tidy) TextTidy.clean(p.text) else p.text
                        val last = out.lastOrNull()
                        if (tidy && last is SHeading && norm(last.text) == norm(t)) continue
                        out.add(SHeading(t, listOf(m), key))
                    }
                    is PTable -> out.add(STable(p.rows, listOf(m), key))
                    is PPara -> {
                        if (!tidy) {
                            out.add(SPara(p.text.replace("\n", " ").trim(), emphasisOf(m, p.text.replace("\n", " ").trim()), listOf(m), key))
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
                                last.marks + m,
                                last.key
                            )
                        } else {
                            out.add(SPara(c, emphasisOf(m, c), listOf(m), key))
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

    private fun withKey(b: SBlock, k: String): SBlock = when (b) {
        is SHeading -> b.copy(key = k)
        is SPara -> b.copy(key = k)
        is STable -> b.copy(key = k)
    }

    private fun tableText(t: STable): String =
        t.rows.joinToString("; ") { r -> r.filter { it.isNotBlank() }.joinToString(" — ") }

    private fun cellsOf(text: String): List<String> =
        if (text.contains('|')) text.split('|').map { it.trim() } else listOf(text)

    /** الملخص كما يُعرض: البناء التلقائي ثم تعديلات المستخدم. */
    fun view(marksInOrder: List<TextMark>, tidy: Boolean, edits: SummaryEdits): List<SBlock> {
        // المفاتيح تُعطى في build وتتضمن الوضع («مرتّب» أو «كما التُقط»)، فتعديلات كل وضع مستقلة
        var list: List<SBlock> = build(marksInOrder, tidy)
        if (edits.isEmpty()) return list

        // 1) عنوان / نص عادي
        list = list.map { b ->
            when (edits.kinds[b.key]) {
                "h" -> when (b) {
                    is SPara -> SHeading(b.text, b.marks, b.key)
                    is STable -> SHeading(tableText(b), b.marks, b.key)
                    else -> b
                }
                "p" -> when (b) {
                    is SHeading -> SPara(b.text, emptyList(), b.marks, b.key)
                    is STable -> SPara(tableText(b), emptyList(), b.marks, b.key)
                    else -> b
                }
                else -> b
            }
        }

        // 2) جداول المستخدم
        for (g in edits.tables) {
            val keySet = g.keys.toSet()
            val members = g.keys.mapNotNull { k -> list.firstOrNull { it.key == k } }
            if (members.isEmpty()) continue
            val rows = ArrayList<List<String>>()
            if (g.rows != null) {
                rows.addAll(g.rows)
            } else {
                val texts = ArrayList<String>()
                for (m in members) {
                    when (m) {
                        is STable -> rows.addAll(m.rows)
                        is SHeading -> texts.add(m.text)
                        is SPara -> texts.add(m.text)
                    }
                }
                when {
                    g.cols > 0 -> texts.chunked(g.cols).forEach { r -> rows.add(r + List(g.cols - r.size) { "" }) }
                    g.oneRow -> if (texts.isNotEmpty()) rows.add(texts)
                    else -> texts.forEach { rows.add(cellsOf(it)) }
                }
            }
            val table = STable(
                rows, members.flatMap { it.marks }.distinctBy { it.id }, "g:" + g.id, g.header
            )
            val out = ArrayList<SBlock>()
            var placed = false
            for (b in list) {
                if (b.key in keySet) {
                    if (!placed) {
                        out.add(table)
                        placed = true
                    }
                } else {
                    out.add(b)
                }
            }
            list = out
        }

        // 3) الترتيب اليدوي: الأجزاء المرتبة يدويًا بترتيبها، والجديدة تبقى بعد الجزء الذي سبقها تلقائيًا
        if (edits.order.isNotEmpty()) {
            val rank = HashMap<String, Int>()
            edits.order.forEachIndexed { i, k -> rank[k] = i }
            var last = -1.0
            var step = 0
            val weighted = list.map { b ->
                val r = rank[b.key]
                val v = if (r != null) {
                    last = r.toDouble()
                    step = 0
                    last
                } else {
                    step++
                    last + step * 1e-4
                }
                v to b
            }
            list = weighted.sortedBy { it.first }.map { it.second }
        }
        return list
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
