package com.mynotes.app

/**
 * MyNotes Pro: الترتيب المنظَّم للملخص (مجرَّب على Gemma 4 E2B بتظليلات حقيقية من antibiotics.pdf).
 * الكود يبني الهيكل والنموذج يصوغ فقط:
 *  1) كل تظليل يُوضع تحت عنوان المحاضرة الذي فوقه (السطر الأكبر خطًا في الصفحة، أو فهرس الملف).
 *  2) صفوف الجداول تُبنى جدولًا بالكود مع رأسه من الصفحة نفسها، دون نموذج.
 *  3) النص العادي لكل عنوان يُرسل للنموذج في طلب صغير ليصير نقاطًا مرتبة.
 *  4) فحص ضد الاختلاق: رقم جديد أو كلمات إنجليزية كثيرة غير موجودة في التظليل → يُستعمل النص الأصلي مرتبًا.
 *  5) دمج التكرار بالكود.
 */
object AiStructure {
    /** جزء يُعالَج: عنوانه وعنوانه الفرعي، وإما جدول جاهز بالكود، أو ملاحظات للنموذج. */
    class Part(val title: String?, val sub: String?, val table: String?, val notes: String, val marks: List<TextMark>) {
        /** نصوص ظُلّلت مرتين (مهمة): تظهر بخط عريض في الملخص. */
        val emphasis: List<String>
            get() = marks.flatMap { m ->
                if (m.emphasis.isNotEmpty()) m.emphasis
                else if (m.level >= 2) listOf(m.text.lineSequence().first().removePrefix("# ").take(120)) else emptyList()
            }.map { it.trim() }.filter { it.length >= 3 }
    }

    /** يتغير عند تغيير طريقة الترتيب فتُهمل النتائج المخزّنة القديمة. */
    const val VERSION = 2

    const val SYSTEM = """You rewrite highlighted notes from ONE section of a pharmacy lecture into a clean part of a study summary.
Rules:
- Use ONLY the notes. Never add facts, numbers, doses or drug names that are not in the notes.
- Keep every fact and keep the original meaning and wording; only split, shorten and order. Do not add explanations.
- Merge repeated points. Keep drug names and medical terms exactly as written. Arabic stays Arabic.
- Do NOT write the section title; it is added for you.
- If several items share the same attributes, output a table: one row per line, cells separated by " | ", first row is a short header.
- Otherwise output short bullets starting with "- ", one fact per bullet, in the same order as the notes.
- Plain text only: no LaTeX, no "$", no markdown symbols other than "- " and " | ". No introduction.

Example
NOTES:
Clavulanic acid | Amoxicillin (co-amoxiclav) | Augmentin
Sulbactam | Ampicillin | Unasyn
OUTPUT:
Inhibitor | Combined with | Brand
Clavulanic acid | Amoxicillin (co-amoxiclav) | Augmentin
Sulbactam | Ampicillin | Unasyn

Example
NOTES:
Pen V is acid-stable so it is given orally. Used only for mild infections such as pharyngitis and otitis media.
OUTPUT:
- Pen V: acid-stable → given orally
- Used only for mild infections: pharyngitis, otitis media"""

    private val COMMON = ("drug drugs class route spectrum mechanism action uses use side effect effects adverse notes note " +
        "example examples generation dose doses indication indications contraindication feature features type group name " +
        "comparison summary important other infection infections inhibitor combined brand detail details resistance " +
        "treatment organism given used only with from that this table header").split(" ").toSet()

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

    /** نص التظليل كأسطر بسيطة (بدون "# "). */
    private fun markLines(m: TextMark): List<String> =
        m.text.replace("\r", "").split('\n').map { it.trim().removePrefix("# ").trim() }.filter { it.isNotEmpty() }

    /** يقسم التظليلات إلى أجزاء حسب عناوين المحاضرة، والجداول تُبنى بالكود. */
    fun parts(s: DocSession, marks: List<TextMark>, budget: Int): List<Part> {
        val cache = HashMap<Int, List<Heading>>()
        val outline = s.outlineEntries().sortedBy { it.page }
        /** (العنوان، العنوان الفرعي) فوق التظليل: آخر عنوان كبير قبله، وآخر عنوان فرعي بعد ذلك العنوان. */
        fun titleFor(m: TextMark): Pair<String?, String?> {
            val top = m.rects.minOfOrNull { it.t } ?: 0f
            val before = (maxOf(0, m.page - 3)..m.page).flatMap { p ->
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
                val k = if (markLines(m).all { it.contains(" | ") }) cells(markLines(m).first()) else 0
                if (runs.isNotEmpty() && runs.last().first == k && k >= 2) runs.last().second.add(m)
                else if (runs.isNotEmpty() && runs.last().first == 0 && k == 0) runs.last().second.add(m)
                else runs.add(Pair(k, mutableListOf(m)))
            }
            for ((k, rm) in runs) {
                if (k >= 2) {
                    val head = tableHeader(s, rm.first(), k)
                    val rows = (if (head != null) listOf(head.joinToString(" | ")) else emptyList()) +
                        rm.flatMap { markLines(it) }.map { it.replace("®", "") }
                    out.add(Part(title, sub, rows.joinToString("\n"), rows.joinToString("\n"), rm))
                } else {
                    // نص طويل يُقسم إلى طلبات صغيرة تناسب ذاكرة النموذج
                    val cur = StringBuilder()
                    val curMarks = ArrayList<TextMark>()
                    for (m in rm) {
                        val t = markLines(m).joinToString("\n")
                        if (cur.isNotEmpty() && cur.length + t.length > budget) {
                            out.add(Part(title, sub, null, cur.toString(), curMarks.toList()))
                            cur.clear()
                            curMarks.clear()
                        }
                        if (cur.isNotEmpty()) cur.append('\n')
                        cur.append(t)
                        curMarks.add(m)
                    }
                    if (cur.isNotEmpty()) out.add(Part(title, sub, null, cur.toString(), curMarks.toList()))
                }
            }
        }
        return out
    }

    fun prompt(p: Part): String = "SECTION: " + listOfNotNull(p.title, p.sub).joinToString(" › ") + "\nNOTES:\n" + p.notes

    /** جزء صغير جدًا (سطر أو سطران قصيران) لا يحتاج النموذج: يُرتّب بالكود مباشرة. */
    fun trivial(p: Part): Boolean = p.table == null && p.notes.length <= 200 && p.notes.lines().count { it.isNotBlank() } <= 2

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

    /** هل في الناتج ما ليس في الملاحظات (رقم جديد، أو كلمات إنجليزية كثيرة جديدة)؟ */
    fun suspicious(out: String, allowed: String): Boolean {
        val num = Regex("\\d+(?:[.,]\\d+)?")
        val okNums = num.findAll(allowed).map { it.value }.toSet()
        if (num.findAll(out).any { it.value !in okNums }) return true
        val word = Regex("[A-Za-z][A-Za-z-]{4,}")
        val words = word.findAll(out).map { it.value.lowercase() }.toList()
        if (words.isEmpty()) return false
        val okWords = word.findAll(allowed).map { it.value.lowercase() }.toSet() + COMMON
        val unknown = words.count { it !in okWords }
        return unknown > maxOf(3, words.size / 4)
    }

    /** بديل آمن: الملاحظات نفسها نقاطًا (والجداول كما هي). */
    fun fallback(notes: String): String =
        notes.lines().filter { it.isNotBlank() }.joinToString("\n") { if (it.contains(" | ")) it else "- " + it.trim() }

    /** ناتج النموذج لجزء: ينظَّف، ويُحذف تكرار العنوان، ويُفحص ضد الاختلاق. */
    fun body(p: Part, raw: String): String {
        val cleaned = AiOrganizer.clean(raw).replace("**", "")
        val heads = listOfNotNull(p.title, p.sub).map { norm(it) }
        val noTitle = cleaned.lines().filter { l -> norm(l.trimStart('#', '-', ' ')) !in heads }
            .map { l -> if (l.trimStart().startsWith("#")) "- " + l.trimStart('#', ' ') else l }
            .joinToString("\n").trim()
        if (noTitle.isBlank()) return fallback(p.notes)
        return if (suspicious(noTitle, p.notes + " " + (p.title ?: ""))) fallback(p.notes) else noTitle
    }

    /** يجمع الأجزاء: عنوان لكل قسم، وعنوان فرعي عند الحاجة، وجداول مفصولة بسطر فارغ، ودون نقاط مكررة. */
    fun assemble(done: List<Triple<String?, String?, String>>): String {
        val sections = LinkedHashMap<Pair<String?, String?>, MutableList<String>>()
        done.forEach { (t, s, b) -> sections.getOrPut(Pair(t, s)) { ArrayList() }.add(b) }
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        var lastTitle: String? = "\u0000"
        for ((key, bodies) in sections) {
            val (title, sub) = key
            if (title != lastTitle) {
                if (out.isNotEmpty() && out.last().isNotBlank()) out.add("")
                if (title != null) out.add("# $title")
                lastTitle = title
            }
            if (sub != null) out.add("## $sub")
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
}
