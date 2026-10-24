package com.mynotes.app

/**
 * MyNotes Pro: الترتيب المنظَّم للملخص. عام لأي مادة (طب، علوم، تاريخ، قانون، لغات…) وبالعربية والإنجليزية.
 * الكود يبني الهيكل والنموذج يصوغ فقط:
 *  1) كل تظليل يُوضع تحت عنوان المحاضرة الذي فوقه (السطر الأكبر خطًا في الصفحة، أو فهرس الملف).
 *  2) صفوف الجداول تُبنى جدولًا بالكود مع رأسه من الصفحة نفسها، دون نموذج؛ وأسطر «اسم: تفصيل» المتتالية جدول بالكود أيضًا.
 *  3) النص العادي لكل عنوان يُرسل للنموذج في طلب صغير ليصير نقاطًا مرتبة.
 *  4) فحص كل سطر ضد الاختلاق (كلمات/أرقام ليست في التظليل، أو صياغة تغيّر المعنى) وضد الحذف (كلمات، أرقام، نفي)
 *     → عند الشك يُستعمل نص التظليل نفسه مرتبًا.
 *  5) دمج التكرار بالكود داخل كل قسم.
 */
object AiStructure {
    /** جزء يُعالَج: عنوانه وعنوانه الفرعي، وإما جدول جاهز بالكود، أو ملاحظات للنموذج. headed = أول صف في الجدول رأس. */
    class Part(val title: String?, val sub: String?, val table: String?, val notes: String, val marks: List<TextMark>, val headed: Boolean = true) {
        /** نصوص ظُلّلت مرتين (مهمة): تظهر بخط عريض في الملخص. */
        val emphasis: List<String>
            get() = marks.flatMap { m ->
                if (m.emphasis.isNotEmpty()) m.emphasis
                else if (m.level >= 2) listOf(m.text.lineSequence().first().removePrefix("# ").take(120)) else emptyList()
            }.map { it.trim() }.filter { it.length >= 3 }
    }

    /** يتغير عند تغيير طريقة الترتيب فتُهمل النتائج المخزّنة القديمة. */
    const val VERSION = 4

    const val SYSTEM = """You rewrite highlighted notes from ONE section of a lecture or study document into a clean part of a study summary. The subject can be anything (medicine, science, history, law, languages, engineering...).
Rules:
- Use ONLY the notes. Never add facts, numbers, names or terms that are not in the notes.
- Keep every fact, the original meaning and the original words; only split, shorten and order. Do not explain.
- Merge repeated points. Keep names, terms, formulas and symbols exactly as written. Arabic stays Arabic.
- Do NOT write the section title; it is added for you.
- When 3 or more items are each described with the same kinds of details, write them as a table: one row per line, cells separated by " | ", first row is a short header.
- Everything else: short bullets starting with "- ", one fact per bullet, in the same order as the notes.
- Plain text only: no LaTeX, no "$", no markdown symbols other than "- " and " | ". No introduction.

Example
NOTES:
Igneous rocks form when magma cools, for example granite.
Sedimentary rocks form from compacted layers of sediment, for example sandstone.
Metamorphic rocks form under heat and pressure, for example marble.
OUTPUT:
Rock type | Formed by | Example
Igneous | Magma cools | Granite
Sedimentary | Compacted layers of sediment | Sandstone
Metamorphic | Heat and pressure | Marble

Example
NOTES:
Pen V is acid-stable so it is given orally. Used only for mild infections such as pharyngitis and otitis media.
OUTPUT:
- Pen V: acid-stable → given orally
- Used only for mild infections: pharyngitis, otitis media

Example
NOTES:
تبدأ الثورة الصناعية في بريطانيا في القرن الثامن عشر، ولم تنتشر إلى أوروبا إلا بعد ذلك بعقود.
OUTPUT:
- تبدأ الثورة الصناعية في بريطانيا في القرن الثامن عشر
- لم تنتشر إلى أوروبا إلا بعد ذلك بعقود"""

    // ---------- الكلمات: تطبيع عام للعربية والإنجليزية (بلا قوائم خاصة بمادة) ----------

    private val TASHKEEL = Regex("[\\u064B-\\u0652\\u0670\\u0640]")
    private val TOKEN = Regex("[\\p{L}\\p{N}]+")
    private val NUM = Regex("\\d+(?:[.,]\\d+)?")
    private val LATIN = Regex("[a-z]+")
    private val AR_PREFIX = listOf("وبال", "وال", "بال", "فال", "كال", "لل", "ال", "و", "ف", "ب", "ل")

    private const val EN_STOP = "the and for with from that this these those its they them their which who whom whose what when where why how " +
        "but also than then there here into onto over under between about after before during all any each both either " +
        "more most some such very can may might must should will would does did has have had are was were been being " +
        "use used using uses given give include includes including e.g i.e etc example examples note notes table header " +
        "item items detail details section part point points summary like via per whereas while because since are is " +
        "define name list state describe mention explain compare type types main kind kinds called known"
    private const val AR_STOP = "في من على الى إلى عن مع هو هي هم هن هذا هذه ذلك تلك التي الذي الذين اللذان ثم او أو ام أم كما كان كانت يكون تكون " +
        "عند بين بعد قبل حتى اذا إذا ان أن إن لكن بل قد كل بعض اي أي ايضا أيضًا أيضا مثل حيث لذلك عندما ما ماذا لماذا كيف متى اين أين " +
        "هل هناك هنا ذات ذو وهو وهي له لها لهم به بها فيه فيها منه منها عليه عليها اذكر عرف عرّف عدد اشرح قارن الفرق"

    /** شكل موحّد للكلمة: بلا تشكيل، همزات موحّدة، بلا «ال/و/ب/ل/ف» في أولها، وبلا لواحق الجمع والزمن الإنجليزية. */
    fun normWord(w0: String): String {
        var w = TASHKEEL.replace(w0.lowercase(), "")
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ٱ', 'ا').replace('ة', 'ه').replace('ى', 'ي')
        if (w.any { it in '\u0600'..'\u06FF' }) {
            repeat(3) {
                val p = AR_PREFIX.firstOrNull { w.startsWith(it) && w.length - it.length >= 3 } ?: return@repeat
                w = w.substring(p.length)
            }
        }
        if (LATIN.matches(w)) {
            w = when {
                w.length > 5 && w.endsWith("ies") -> w.dropLast(3) + "y"
                w.length > 6 && w.endsWith("ing") -> w.dropLast(3)
                w.length > 5 && w.endsWith("ed") -> w.dropLast(2)
                w.length > 5 && w.endsWith("ly") -> w.dropLast(2)
                w.length > 3 && w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
                else -> w
            }
        }
        return w
    }

    private val STOP: Set<String> by lazy { (EN_STOP + " " + AR_STOP).split(" ").filter { it.isNotBlank() }.map { normWord(it) }.toSet() }

    /** الكلمات بعد التطبيع (3 أحرف فأكثر، دون الأرقام الخالصة). */
    fun words(s: String): List<String> =
        TOKEN.findAll(TASHKEEL.replace(s, "")).map { it.value }.filter { it.length >= 3 && !it.all { c -> c.isDigit() } }.map { normWord(it) }.toList()

    /** الكلمات المهمة (دون كلمات الربط). */
    fun content(s: String): List<String> = words(s).filter { it !in STOP }

    private fun numbers(s: String): Set<String> = NUM.findAll(s).map { it.value }.toSet()

    private fun negations(s: String): Int = NEGATION.findAll(s).count()

    /** السطر مسنود: كل رقم فيه من المصدر، والكلمات المهمة الجديدة قليلة جدًا (لا شيء للسطر القصير). */
    fun grounded(line: String, allowed: Set<String>, nums: Set<String>): Boolean {
        if (NUM.findAll(line).any { it.value !in nums }) return false
        val cw = content(line)
        val unknown = cw.count { it !in allowed }
        val limit = if (cw.size <= 2) 0 else maxOf(1, cw.size / 4)
        return unknown <= limit
    }

    /** أزواج الكلمات المتتالية في المصدر (مع السماح بحذف كلمتين بينهما)، بترتيبها. */
    private fun pairsOf(seq: List<String>, window: Int = 3): Set<Pair<String, String>> {
        val out = HashSet<Pair<String, String>>()
        for (i in seq.indices) for (j in i + 1 until minOf(seq.size, i + 1 + window)) out.add(seq[i] to seq[j])
        return out
    }

    /** الصياغة أمينة: نصف أزواج الكلمات المتتالية على الأقل موجودة بنفس الترتيب في الملاحظات (إعادة الصياغة قد تغيّر المعنى). */
    private fun faithful(line: String, notePairs: Set<Pair<String, String>>, known: Set<String>): Boolean {
        val cw = content(line).filter { it in known }
        if (cw.size < 4) return true
        val ps = cw.zipWithNext()
        return ps.count { it in notePairs } * 2 >= ps.size
    }

    /** أسطر الناتج التي لا أصل لها في الملاحظات. رأس الجدول يُقبل إن كانت خلاياه قصيرة وبلا أرقام جديدة. */
    fun badLines(out: String, notes: String): List<String> {
        val seq = content(notes)
        val allowed = seq.toSet()
        val nums = numbers(notes)
        val np = pairsOf(seq)
        val bad = ArrayList<String>()
        val ls = out.lines().map { it.trim() }.filter { it.isNotEmpty() }
        var k = 0
        while (k < ls.size) {
            if (ls[k].contains(" | ")) {
                var j = k
                while (j < ls.size && ls[j].contains(" | ")) j++
                for (r in k until j) {
                    val l = ls[r].replace("**", "")
                    if (r == k && j - k >= 2) {
                        if (l.split(" | ").any { it.trim().split(Regex("\\s+")).size > 4 } || NUM.findAll(l).any { it.value !in nums }) bad.add(ls[r])
                    } else if (!grounded(l, allowed, nums)) bad.add(ls[r])
                }
                k = j
            } else {
                val l = ls[k].replace("**", "")
                if (!grounded(l, allowed, nums) || !faithful(l, np, allowed)) bad.add(ls[k])
                k++
            }
        }
        return bad
    }

    private fun median(v: List<Float>): Float = if (v.isEmpty()) 0f else v.sorted()[v.size / 2]

    private fun lines(words: List<OcrWord>): List<List<OcrWord>> {
        val m = LinkedHashMap<Int, MutableList<OcrWord>>()
        words.forEach { m.getOrPut(it.line) { ArrayList() }.add(it) }
        return m.values.toList()
    }

    private fun isArabic(s: String) = s.count { it in '؀'..'ۿ' } > s.count { it.isLetter() } / 3

    private fun lineHeight(l: List<OcrWord>) = median(l.map { it.b - it.t }).coerceAtLeast(1e-4f)

    /** نص السطر؛ الفراغ الواسع بين كلمتين = فاصل خلايا " | ". */
    private fun lineText(l: List<OcrWord>, cells: Boolean = true): String {
        val arabic = isArabic(l.joinToString(" ") { it.text })
        val sorted = if (arabic) l.sortedByDescending { it.r } else l.sortedBy { it.l }
        val h = lineHeight(l)
        val sb = StringBuilder()
        sorted.forEachIndexed { i, w ->
            if (i > 0) {
                val prev = sorted[i - 1]
                val gap = if (arabic) prev.l - w.r else w.l - prev.r
                sb.append(if (cells && gap > h * 1.6f) " | " else " ")
            }
            sb.append(w.text)
        }
        return sb.toString()
    }

    /** عنوان في الصفحة: موضعه، نصه، ومستواه (1 = خط أكبر، 2 = سطر قصير عريض). */
    class Heading(val page: Int, val pos: Float, val text: String, val level: Int)

    /** عناوين الصفحة: أسطر خطها أكبر بوضوح من نص الصفحة، وأسطر قصيرة كلها بخط عريض (عناوين فرعية). */
    private fun pageHeadings(s: DocSession, page: Int): List<Heading> {
        val ls = lines(s.ocr[page]?.words ?: emptyList())
        // عناوين من «قراءة الصفحة بالذكاء» (إن قُرئت): «# » عنوان و«## » عنوان فرعي
        val aiHeads = (AiPageReader.result(s.id, page) ?: "").lines().mapNotNull { l ->
            val x = l.trim()
            when {
                x.startsWith("## ") -> Pair(x.removePrefix("## ").trim(), 2)
                x.startsWith("# ") -> Pair(x.removePrefix("# ").trim(), 1)
                else -> null
            }
        }.filter { norm(it.first).length >= 3 }
        if (ls.isEmpty()) {
            // صفحة بلا نص مقروء (ممسوحة): أول عنوان من قراءة الذكاء في أعلى الصفحة
            return aiHeads.firstOrNull()?.let { listOf(Heading(page, 0f, it.first, it.second)) } ?: emptyList()
        }
        val found = ocrHeadings(page, ls).toMutableList()
        // عنوان الذكاء يُقبل فقط إن وُجد نصه فعلًا في سطر من الصفحة (فلا عناوين مخترعة)، ويأخذ موضع ذلك السطر
        for ((h, lvl) in aiHeads) {
            val nh = norm(h)
            if (found.any { norm(it.text) == nh }) continue
            val line = ls.firstOrNull { l ->
                val nt = norm(lineText(l, cells = false))
                nt == nh || (nh.length >= 8 && nt.length >= 8 && (nt.contains(nh) || nh.contains(nt)))
            } ?: continue
            if (lineText(line).contains(" | ")) continue
            found.add(Heading(page, line.minOf { it.t }, lineText(line, cells = false).trim().trimEnd(':').trim(), lvl))
        }
        return found.sortedBy { it.pos }
    }

    /** عناوين من شكل الأسطر: خط أكبر بوضوح (مستوى 1)، أو أكبر قليلًا أو سطر قصير عريض عند الهامش (مستوى 2). */
    private fun ocrHeadings(page: Int, ls: List<List<OcrWord>>): List<Heading> {
        val normal = median(ls.map { lineHeight(it) })
        // هامش النص: العنوان الفرعي يبدأ من الهامش (أو ينتهي عنده في العربية)، لا من داخل خلية جدول أو رسم
        fun pct(v: List<Float>, q: Float) = v.sorted().let { if (it.isEmpty()) 0f else it[minOf(it.size - 1, (it.size * q).toInt())] }
        val left = pct(ls.map { l -> l.minOf { it.l } }, 0.1f)
        val right = pct(ls.map { l -> l.maxOf { it.r } }, 0.9f)
        val noise = Regex("(?i)page \\d+|^\\d+$")
        return ls.mapNotNull { l ->
            val t = lineText(l, cells = false).trim()
            if (t.length !in 3..90 || !t.any { it.isLetter() } || noise.containsMatchIn(t)) return@mapNotNull null
            val pos = l.minOf { it.t }
            val atMargin = if (isArabic(t)) l.maxOf { it.r } >= right - 0.06f else l.minOf { it.l } <= left + 0.03f
            when {
                lineHeight(l) >= normal * 1.25f -> Heading(page, pos, t, 1)
                !atMargin -> null
                lineHeight(l) >= normal * 1.15f -> Heading(page, pos, t, 2)
                l.count { it.bold } * 5 >= l.size * 4 && l.size <= 10 && !lineText(l).contains(" | ") &&
                    !t.endsWith(".") -> Heading(page, pos, t.trimEnd(':').trim(), 2)
                else -> null
            }
        }.sortedBy { it.pos }
    }

    private fun cells(text: String) = if (text.contains(" | ")) text.split(" | ").size else 0

    /** رأس الجدول من الصفحة: أول سطر في كتلة الأسطر ذات العدد نفسه من الخلايا فوق أول صف، إن كانت خلاياه قصيرة. */
    private fun tableHeader(s: DocSession, m: TextMark, n: Int): List<String>? {
        val top = m.rects.minOfOrNull { it.t } ?: return null
        val ls = lines(s.ocr[m.page]?.words ?: return null)
        val above = ls.filter { l -> l.minOf { it.t } < top - 0.001f }.sortedBy { l -> l.minOf { it.t } }
        val block = ArrayList<String>()
        for (l in above.reversed()) {
            val t = lineText(l)
            if (cells(t) == n) block.add(0, t) else if (block.isNotEmpty()) break
        }
        if (block.isEmpty() && m.page > 0) {
            // الجدول بدأ في الصفحة السابقة: رأسه أول سطر في آخر كتلة جدول هناك
            val prev = lines(s.ocr[m.page - 1]?.words ?: emptyList()).sortedBy { l -> l.minOf { it.t } }
            for (l in prev.reversed()) {
                val tx = lineText(l)
                if (cells(tx) == n) block.add(0, tx) else if (block.isNotEmpty()) break
            }
        }
        val first = block.firstOrNull() ?: return null
        val cs = first.split(" | ").map { it.trim() }
        // السطر الأول نفسه قد يكون جزءًا من التظليل (صف بيانات): لا يُعدّ رأسًا إن طابق نص التظليل
        if (m.text.trim().startsWith(first.trim())) return null
        return if (cs.size == n && cs.all { it.length <= 22 } && first.length <= 60) cs else null
    }

    private val BULLET = Regex("^([-•▪◦·*–]|\\d{1,2}[.)]|[\\u0660-\\u0669]{1,2}[.)]|[a-h]\\))\\s")
    private val ABBR = Regex("(?i)(\\be\\.g\\.|\\bi\\.e\\.|\\betc\\.|\\bvs\\.|\\bno\\.|\\bdr\\.|\\bfig\\.)$")
    private const val TERMINAL = ".!?؟:;؛"

    /** نهاية جملة: علامة نهاية، دون اختصار مثل e.g. ودون قوس مفتوح. */
    private fun ended(l: String): Boolean =
        l.isNotEmpty() && l.last() in TERMINAL && !ABBR.containsMatchIn(l) && l.count { it == '(' } <= l.count { it == ')' }

    /**
     * نص التظليل كأسطر بسيطة (بدون "# "). أسطر الجملة الواحدة تُوصل حتى نهايتها (حتى لو فصلها فراغ بين الأسطر)،
     * فلا تنقسم جملة على نقطتين؛ العناوين وصفوف الجداول والنقاط المرقّمة تبقى أسطرًا مستقلة.
     */
    fun markLines(text: String): List<String> {
        val out = ArrayList<String>()
        var open = false
        for (block in ArabicFix.text(text).replace("\r", "").split(Regex("\n\\s*\n"))) {
            for (raw in block.split('\n')) {
                val l = raw.trim()
                if (l.isEmpty()) continue
                if (l.startsWith("# ")) { out.add(l.removePrefix("# ").trim()); open = false; continue }
                if (l.contains(" | ")) { out.add(l); open = false; continue }
                if (open && out.isNotEmpty() && !BULLET.containsMatchIn(l)) {
                    val last = out.last()
                    out[out.lastIndex] = if (last.endsWith("-") && l.first().isLowerCase()) last.dropLast(1) + l else "$last $l"
                } else out.add(l)
                open = !ended(out.last())
            }
        }
        return out.filter { it.isNotEmpty() }
    }

    private fun markLines(m: TextMark): List<String> = markLines(m.text)

    // ---------- أسطر «اسم: تفصيل» المتتالية = جدول بالكود ----------

    private val KEYED = Regex("^(.{2,40}?)(?:\\s*(?<!\\d)[:：](?!\\d)\\s*| [—–=] )(.+)$")
    private val LABELS by lazy { "note notes example examples e.g ملاحظة ملاحظات مثال أمثلة tip".split(" ").map { normWord(it) }.toSet() }

    /** («اسم»، «تفصيل») إن كان السطر بهذا الشكل: اسم قصير ثم «:» أو «—» ثم التفصيل. */
    fun keyed(l0: String): Pair<String, String>? {
        val t = l0.trim().removePrefix("- ").removePrefix("• ")
        if (t.contains(" | ")) return null
        val m = KEYED.find(t) ?: return null
        val k = m.groupValues[1].trim().trim('*').trim()
        val v = m.groupValues[2].trim()
        if (k.split(" ").size > 5 || k.length > 40 || v.length < 2 || v.length > 200) return null
        if (!k.any { it.isLetter() } || k.endsWith(".")) return null
        if (TOKEN.findAll(k).joinToString(" ") { normWord(it.value) } in LABELS) return null
        return k to v
    }

    /** مدى الأسطر المتتالية (3 فأكثر) بشكل «اسم: تفصيل» بأسماء مختلفة. */
    fun keyedRuns(lines: List<String>): List<IntRange> {
        val out = ArrayList<IntRange>()
        var i = 0
        while (i < lines.size) {
            var j = i
            val keys = HashSet<String>()
            while (j < lines.size) {
                val kv = keyed(lines[j]) ?: break
                val nk = words(kv.first).joinToString(" ").ifEmpty { kv.first.lowercase() }
                if (!keys.add(nk)) break
                j++
            }
            if (j - i >= 3) { out.add(i until j); i = j } else i++
        }
        return out
    }

    fun keyedTable(lines: List<String>): String {
        val head = if (isArabic(lines.joinToString(" "))) "البند | التفصيل" else "Item | Details"
        return (listOf(head) + lines.mapNotNull { l -> keyed(l)?.let { "${it.first} | ${it.second}" } }).joinToString("\n")
    }

    /** يقسم التظليلات إلى أجزاء حسب عناوين المحاضرة، والجداول تُبنى بالكود. */
    fun parts(s: DocSession, marks: List<TextMark>, budget: Int): List<Part> {
        val cache = HashMap<Int, List<Heading>>()
        val outline = s.outlineEntries().sortedBy { it.page }
        /**
         * (العنوان، العنوان الفرعي) فوق التظليل: آخر عنوان كبير قبله (حتى 8 صفحات مقروءة للخلف، فلا يضيع عنوان القسم الطويل)،
         * وآخر عنوان فرعي بعد ذلك العنوان.
         */
        fun titleFor(m: TextMark): Pair<String?, String?> {
            val top = m.rects.minOfOrNull { it.t } ?: 0f
            // 3 صفحات للخلف دائمًا، وأبعد (حتى 8) فقط عبر صفحات مقروءة: صفحة لم تُقرأ قد يكون فيها عنوان أحدث
            var lo = m.page
            var allRead = true
            while (lo > 0 && m.page - lo < 8) {
                val read = s.ocr[lo - 1] != null
                if (m.page - (lo - 1) > 3 && !(allRead && read)) break
                allRead = allRead && read
                lo--
            }
            val before = (lo..m.page).flatMap { p ->
                cache.getOrPut(p) { pageHeadings(s, p) }.filter { p < m.page || it.pos <= top + 0.002f }
            }
            val main = before.lastOrNull { it.level == 1 }
            val sub = before.lastOrNull { it.level == 2 && (main == null || it.page > main.page || (it.page == main.page && it.pos > main.pos)) }
            val title = main?.text ?: outline.lastOrNull { it.page <= m.page }?.title
            // العنوان الفرعي البعيد جدًا (أكثر من صفحة) لا يُعتمد
            val subOk = sub != null && m.page - sub.page <= 1
            return Pair(title, if (subOk) sub!!.text else null)
        }
        // مجموعات متتالية بنفس العنوان والعنوان الفرعي
        val groups = ArrayList<Pair<Pair<String?, String?>, MutableList<TextMark>>>()
        for (m in marks) {
            val t = titleFor(m)
            if (groups.isNotEmpty() && groups.last().first == t) groups.last().second.add(m)
            else groups.add(Pair(t, mutableListOf(m)))
        }
        val out = ArrayList<Part>()
        for ((key, ms) in groups) {
            val (title, sub) = key
            // داخل المجموعة: صفوف جدول متتالية بنفس عدد الخلايا = جدول، والباقي نص
            val runs = ArrayList<Pair<Int, MutableList<TextMark>>>()
            for (m in ms) {
                val ls = markLines(m)
                if (ls.isEmpty()) continue
                val k = if (ls.all { it.contains(" | ") }) cells(ls.first()) else 0
                if (runs.isNotEmpty() && runs.last().first == k && k >= 2) runs.last().second.add(m)
                else if (runs.isNotEmpty() && runs.last().first == 0 && k == 0) runs.last().second.add(m)
                else runs.add(Pair(k, mutableListOf(m)))
            }
            for ((k, rm) in runs) {
                if (k >= 2) {
                    val head = tableHeader(s, rm.first(), k)
                    val rows = (if (head != null) listOf(head.joinToString(" | ")) else emptyList()) +
                        rm.flatMap { markLines(it) }.map { it.replace("®", "") }
                    out.add(Part(title, sub, rows.joinToString("\n"), rows.joinToString("\n"), rm, headed = head != null))
                    continue
                }
                // النص: أسطر «اسم: تفصيل» المتتالية جدول بالكود، والباقي طلبات صغيرة تناسب ذاكرة النموذج
                val lines = ArrayList<Pair<String, TextMark>>()
                rm.forEach { m -> markLines(m).forEach { lines.add(it to m) } }
                val keyedR = keyedRuns(lines.map { it.first })
                val cur = StringBuilder()
                val curMarks = LinkedHashSet<TextMark>()
                fun flush() {
                    if (cur.isNotEmpty()) out.add(Part(title, sub, null, cur.toString(), curMarks.toList()))
                    cur.clear()
                    curMarks.clear()
                }
                var i = 0
                while (i < lines.size) {
                    val r = keyedR.firstOrNull { it.first == i }
                    if (r != null) {
                        flush()
                        val tb = keyedTable(r.map { lines[it].first })
                        out.add(Part(title, sub, tb, tb, r.map { lines[it].second }.distinct()))
                        i = r.last + 1
                        continue
                    }
                    val (t, m) = lines[i]
                    if (cur.isNotEmpty() && cur.length + t.length > budget) flush()
                    if (cur.isNotEmpty()) cur.append('\n')
                    cur.append(t)
                    curMarks.add(m)
                    i++
                }
                flush()
            }
        }
        return out
    }

    fun prompt(p: Part): String = "SECTION: " + listOfNotNull(p.title, p.sub).joinToString(" › ") + "\nNOTES:\n" + p.notes

    /** جزء صغير جدًا (سطر أو سطران قصيران) لا يحتاج النموذج: يُرتّب بالكود مباشرة. */
    fun trivial(p: Part): Boolean = p.table == null && p.notes.length <= 200 && p.notes.lines().count { it.isNotBlank() } <= 2

    fun hash(raw: String): String =
        java.security.MessageDigest.getInstance("SHA-1").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }

    /** مفتاح ذاكرة الأجزاء: نفس الطريقة والنموذج والعنوان والملاحظات = نفس النتيجة. */
    fun cacheKey(p: Part, model: String): String {
        val raw = "$VERSION|$model|${p.title}|${p.sub}|${p.notes}|${p.emphasis.joinToString("¦")}"
        val md = java.security.MessageDigest.getInstance("SHA-1").digest(raw.toByteArray())
        return md.joinToString("") { "%02x".format(it) }
    }

    /** يضع ** حول النصوص المهمة (المظللة مرتين) إن وُجدت في الناتج. */
    fun emphasize(body: String, emphasis: List<String>): String {
        var out = body
        for (e in emphasis.sortedByDescending { it.length }) {
            val i = out.indexOf(e, ignoreCase = true)
            if (i < 0) continue
            val before = out.substring(0, i)
            if (before.count { it == '*' } % 4 != 0) continue
            out = before + "**" + out.substring(i, i + e.length) + "**" + out.substring(i + e.length)
        }
        return out
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** هل في الناتج كله ما ليس في الملاحظات (رقم جديد، أو كلمات مهمة جديدة كثيرة بأي لغة)؟ */
    fun suspicious(out: String, allowed: String): Boolean {
        val okNums = numbers(allowed)
        if (NUM.findAll(out).any { it.value !in okNums }) return true
        val cw = content(out)
        if (cw.isEmpty()) return false
        val ok = content(allowed).toSet()
        return cw.count { it !in ok } > maxOf(3, cw.size / 4)
    }

    private val NEGATION = Regex("(?i)\\b(not|no|never|without|except|avoid|cannot|contraindicated)\\b|(?<![\\p{L}])[وف]?(لا|ليس|ليست|غير|لن|لم|بدون|دون|يُمنع|ممنوع)(?![\\p{L}])")

    /** نسبة الكلمات المهمة في الملاحظات التي لم تظهر في الناتج. */
    fun missingRatio(out: String, notes: String): Float {
        val nw = content(notes).toSet()
        if (nw.isEmpty()) return 0f
        val ow = content(out).toSet()
        return nw.count { it !in ow } / nw.size.toFloat()
    }

    /**
     * هل أسقط الناتج شيئًا مهمًا من الملاحظات؟ رقم مفقود، أو أكثر من خُمس الكلمات المهمة (بأي لغة)،
     * أو نفي («لا»، not…) — فقدان النفي يقلب المعنى.
     */
    fun dropped(out: String, notes: String): Boolean {
        val outNums = numbers(out)
        if (NUM.findAll(notes).any { it.value !in outNums }) return true
        if (content(notes).toSet().size >= 6 && missingRatio(out, notes) > 0.2f) return true
        return negations(out) < negations(notes)
    }

    /** بديل آمن: الملاحظات نفسها نقاطًا (والجداول كما هي)، والنقطة الطويلة تُقسم عند نهايات الجمل. */
    fun fallback(notes: String): String =
        tidy(notes.lines().filter { it.isNotBlank() }.joinToString("\n") { if (it.contains(" | ")) it else "- " + it.trim() })

    /** نقطة طويلة فيها أكثر من جملة تُقسم عند نهاية كل جملة (كل جزء 20 حرفًا فأكثر). */
    private fun splitLong(line: String, limit: Int = 140): List<String> {
        val t = line.trim()
        if (!t.startsWith("- ") || t.length <= limit) return listOf(line)
        val pieces = ArrayList<String>()
        var cur = ""
        for (tok in t.substring(2).split(Regex("(?<=[.!?؟])\\s+"))) {
            cur = if (cur.isEmpty()) tok else "$cur $tok"
            if (cur.isNotEmpty() && cur.last() in ".!?؟" && ended(cur)) { pieces.add(cur); cur = "" }
        }
        if (cur.isNotEmpty()) pieces.add(cur)
        if (pieces.size < 2 || pieces.any { it.length < 20 }) return listOf(line)
        return pieces.map { "- " + it.trim() }
    }

    /**
     * ينظّم الناتج بالكود: صف جدول منفرد ← نقطة، «|» داخل نقطة ← «;»، سطر بلا «- » ← نقطة، والنقاط الطويلة تُقسم.
     * لا يغيّر الكلمات.
     */
    fun tidy(body: String): String {
        val ls = body.lines().map { it.trimEnd() }
        val out = ArrayList<String>()
        fun isRow(l: String) = l.contains(" | ") && !l.startsWith("- ")
        fun cellsOf(l: String) = l.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        var i = 0
        while (i < ls.size) {
            val l = ls[i].trim()
            if (isRow(l)) {
                var j = i
                while (j < ls.size && isRow(ls[j].trim())) j++
                val run = ls.subList(i, j).map { it.trim().trim('|').trim() }
                if (run.size == 1) out.add("- " + cellsOf(run[0]).joinToString("; ")) else out.addAll(run)
                i = j
                continue
            }
            when {
                l.isEmpty() -> out.add("")
                l.startsWith("- ") -> out.addAll(splitLong(if (l.contains("|")) "- " + cellsOf(l.substring(2)).joinToString("; ") else l))
                l.startsWith("**") -> out.add(l)
                else -> out.addAll(splitLong("- " + l.trimStart('•', '*', '·', '▪', ' ').trim()))
            }
            i++
        }
        return out.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
    }

    /** المراجعة الذاتية فقط إن بقي في المسودة ما يحتاجها: تنسيق غير سليم أو نقطة طويلة جدًا (توفّر وقتًا على الجهاز). */
    fun needsReview(draft: String): Boolean =
        !wellFormed(draft) || draft.lines().any { !it.contains(" | ") && it.trim().length > 160 }

    const val REVIEW_SYSTEM = """You review a DRAFT section of a study summary (any subject) against the NOTES it was written from.
Check that:
- every fact of the notes is in the draft and nothing was added;
- meaning, numbers, names, terms and negations (not, no, لا, ليس) are exactly kept;
- items that share the same kinds of details are in a table (one row per line, cells separated by " | ", first row header);
- everything else is short bullets starting with "- ".
If the draft is correct and well organized, answer only: OK
Otherwise answer only the corrected section in the same format: no title, no explanations, no introduction."""

    fun reviewPrompt(p: Part, draft: String): String =
        "SECTION: " + listOfNotNull(p.title, p.sub).joinToString(" › ") + "\nNOTES:\n" + p.notes + "\n\nDRAFT:\n" + draft

    /** نتيجة المراجعة: «OK» = المسودة صحيحة (null)، وإلا التصحيح إن اجتاز الفحوص (وإلا null فتبقى المسودة). */
    fun reviewed(p: Part, raw: String): String? {
        val r = AiOrganizer.clean(raw).trim()
        if (r.isEmpty() || Regex("^(?i)ok\\b").containsMatchIn(r) || r == "حسنًا") return null
        // التصحيح يجب أن يكون منسقًا سليمًا كما كتبه النموذج (دون تنظيم الكود)
        return checked(p, r, tidyIt = false)?.takeIf { wellFormed(it) }
    }

    /**
     * تصحيح المراجعة يُقبل فقط إن كان منسقًا سليمًا: كل سطر نقطة «- » بلا «|»، أو صف جدول
     * ضمن جدول من صفين فأكثر بعدد خلايا ثابت (2 فأكثر) ولا خلية فارغة. غير ذلك تبقى المسودة.
     */
    fun wellFormed(s: String): Boolean {
        val lines = s.lines().map { it.trim() }
        var run = 0
        var cellsN = -1
        fun endRun(): Boolean = run == 0 || run >= 2
        for (l in lines) {
            if (l.isEmpty()) { if (!endRun()) return false; run = 0; cellsN = -1; continue }
            if (l.contains("|")) {
                if (l.startsWith("- ") || l.startsWith("|") || l.endsWith("|")) return false
                val c = l.split("|").map { it.trim() }
                if (c.size < 2 || c.any { it.isEmpty() }) return false
                if (run > 0 && c.size != cellsN) return false
                cellsN = c.size; run++
            } else {
                if (!endRun()) return false
                run = 0; cellsN = -1
                if (!l.startsWith("- ") && !l.startsWith("**")) return false
            }
        }
        return endRun()
    }

    /** ناتج النموذج لجزء: ينظَّف، ويُحذف تكرار العنوان، ويُفحص ضد الاختلاق والحذف. */
    fun body(p: Part, raw: String): String = checked(p, raw) ?: fallback(p.notes)

    /** مثل body لكن يعيد null إن لم يجتز الفحوص: كلمات جديدة، أو سطر بلا أصل، أو صياغة تغيّر المعنى، أو حذف. */
    fun checked(p: Part, raw: String, tidyIt: Boolean = true): String? {
        val cleaned = AiOrganizer.clean(raw).replace("**", "")
        val heads = listOfNotNull(p.title, p.sub).map { norm(it) }
        val noTitle0 = cleaned.lines().filter { l -> norm(l.trimStart('#', '-', ' ')) !in heads }
            .map { l -> if (l.trimStart().startsWith("#")) "- " + l.trimStart('#', ' ') else l }
            .joinToString("\n").trim()
        val noTitle = if (tidyIt) tidy(noTitle0) else noTitle0
        if (noTitle.isBlank()) return null
        val bad = suspicious(noTitle, p.notes + " " + (p.title ?: "") + " " + (p.sub ?: "")) ||
            badLines(noTitle, p.notes).isNotEmpty() || dropped(noTitle, p.notes)
        return if (bad) null else noTitle
    }

    /** يجمع الأجزاء: عنوان لكل قسم، وعنوان فرعي عند الحاجة، وجداول مفصولة بسطر فارغ، ودون نقاط مكررة. */
    fun assemble(done: List<Triple<String?, String?, String>>): String {
        val sections = LinkedHashMap<Pair<String?, String?>, MutableList<String>>()
        done.forEach { (t, s, b) -> sections.getOrPut(Pair(t, s)) { ArrayList() }.add(b) }
        val out = ArrayList<String>()
        var lastTitle: String? = "\u0000"
        for ((key, bodies) in sections) {
            val (title, sub) = key
            // التكرار يُحذف داخل القسم نفسه فقط: النقطة نفسها في قسم آخر معلومة لذلك القسم أيضًا
            val seen = HashSet<String>()
            var subLine = sub?.let { "## $it" }
            if (title != lastTitle) {
                if (out.isNotEmpty() && out.last().isNotBlank()) out.add("")
                if (title != null) out.add("# $title")
                // عنوان فرعي بلا عنوان رئيسي بعد قسم آخر: يصير عنوانًا مستقلًا، فلا يظهر داخل القسم السابق
                else if (sub != null && lastTitle != "\u0000" && lastTitle != null) subLine = "# $sub"
                lastTitle = title
            }
            if (subLine != null) out.add(subLine)
            for (line in bodies.joinToString("\n\n").lines()) {
                val k = norm(line.replace("**", ""))
                if (line.isNotBlank() && !line.contains(" | ") && k.isNotEmpty() && !seen.add(k)) continue
                if (line.isBlank() && (out.isEmpty() || out.last().isBlank())) continue
                out.add(line)
            }
            out.add("")
        }
        return out.joinToString("\n").trim()
    }

    // ---------- بطاقات المراجعة: فحص، ومنع التكرار، وبطاقات الجداول بالكود ----------

    private val YESNO = Regex("(?i)^(is|are|does|do|can|was|were|has|have|will|هل)\\b")

    /** البطاقة صحيحة: الجواب قصير ومن المصدر نفسه، والسؤال بلا أرقام أو كلمات غريبة، وليس نعم/لا، ولا يحوي جوابه. */
    fun cardOk(q0: String, a0: String, source: String): Boolean {
        val q = q0.trim()
        val a = a0.trim()
        if (q.length !in 6..220 || a.isEmpty() || a.length > 180 || a.split(Regex("\\s+")).size > 25) return false
        val src = content(source).toSet()
        val nums = numbers(source)
        if (!grounded(a, src, nums)) return false
        val qw = content(q)
        if (qw.count { it !in src } > maxOf(1, qw.size / 3) || NUM.findAll(q).any { it.value !in nums }) return false
        val aw = content(a).toSet()
        if (aw.isNotEmpty() && qw.toSet().containsAll(aw)) return false
        if (negations(a) > 0 && negations(source) == 0) return false
        return !YESNO.containsMatchIn(q)
    }

    /** تشابه نصين بالكلمات المهمة (0..1). */
    fun similar(a: String, b: String): Float {
        val x = content(a).toSet()
        val y = content(b).toSet()
        if (x.isEmpty() || y.isEmpty()) return 0f
        return x.intersect(y).size / x.union(y).size.toFloat()
    }

    /** بطاقتان عن الشيء نفسه: سؤال متشابه، أو جواب متطابق تقريبًا لسؤال قريب. */
    fun sameCard(q1: String, a1: String, q2: String, a2: String): Boolean =
        q1.trim().equals(q2.trim(), ignoreCase = true) || similar(q1, q2) >= 0.6f ||
            (similar(a1, a2) >= 0.8f && similar(q1, q2) >= 0.3f)

    fun dedupeCards(cards: List<Pair<String, String>>): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        for ((q, a) in cards) if (out.none { (oq, oa) -> sameCard(q, a, oq, oa) }) out.add(q to a)
        return out
    }

    private val GENERIC_HEAD by lazy {
        "details detail notes note description comments remarks التفصيل التفاصيل ملاحظات الوصف".split(" ").map { words(it).joinToString(" ") }.toSet()
    }

    /** بطاقة لكل صف جدول (حتى 12): السؤال = البند (+ أسماء الأعمدة)، والجواب = بقية الخلايا كما هي. */
    fun tableCards(table: String, headed: Boolean, topic: String?): List<Pair<String, String>> {
        val rowsTxt = table.lines().filter { it.contains(" | ") }
        val cells = rowsTxt.map { r -> r.split(" | ").map { it.trim().replace("**", "") } }
        if (cells.isEmpty()) return emptyList()
        val head = if (headed && cells.size >= 2) cells.first() else null
        val data = if (head != null) cells.drop(1) else cells
        val ar = isArabic(rowsTxt.joinToString(" "))
        val mark = if (ar) "؟" else "?"
        val generic = head != null && head.drop(1).all { words(it).joinToString(" ") in GENERIC_HEAD }
        val empty = setOf("", "—", "-")
        val out = ArrayList<Pair<String, String>>()
        for (r in data.take(12)) {
            if (r.size < 2 || r[0].isEmpty() || r.drop(1).all { it in empty }) continue
            val (q, a) = if (head != null && !generic) {
                val parts = r.withIndex().filter { it.index > 0 && it.value !in empty }.map { (head.getOrNull(it.index) ?: "") to it.value }
                val qq = r[0] + " — " + parts.map { it.first }.filter { it.isNotEmpty() }.joinToString(" / ") + mark
                val aa = parts.joinToString(" · ") { (h, c) -> if (h.isNotEmpty() && parts.size > 1) "$h: $c" else c }
                qq to aa
            } else {
                ((if (topic != null) "$topic: " else "") + r[0] + mark) to r.drop(1).filter { it !in empty }.joinToString(" · ")
            }
            if (a.length <= 200) out.add(q to a)
        }
        return out
    }
}
