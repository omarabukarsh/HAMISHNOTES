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
    /** جزء يُعالَج: عنوانه، وإما جدول جاهز بالكود، أو ملاحظات للنموذج. */
    class Part(val title: String?, val table: String?, val notes: String, val marks: List<TextMark>)

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

    /** عناوين الصفحة: أسطر خطها أكبر بوضوح من نص الصفحة (موضعها، نصها). */
    private fun pageHeadings(s: DocSession, page: Int): List<Pair<Float, String>> {
        val ls = lines(s.ocr[page]?.words ?: return emptyList())
        if (ls.isEmpty()) return emptyList()
        val normal = median(ls.map { lineHeight(it) })
        return ls.mapNotNull { l ->
            val t = lineText(l, cells = false).trim()
            val ok = lineHeight(l) >= normal * 1.15f && t.length in 3..90 &&
                t.any { it.isLetter() } && !Regex("(?i)page \\d+|^\\d+$").containsMatchIn(t)
            if (ok) Pair(l.minOf { it.t }, t) else null
        }.sortedBy { it.first }
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
        val cache = HashMap<Int, List<Pair<Float, String>>>()
        val outline = s.outlineEntries().sortedBy { it.page }
        fun titleFor(m: TextMark): String? {
            val top = m.rects.minOfOrNull { it.t } ?: 0f
            for (p in m.page downTo maxOf(0, m.page - 3)) {
                val hs = cache.getOrPut(p) { pageHeadings(s, p) }
                val cand = if (p == m.page) hs.filter { it.first <= top + 0.002f } else hs
                cand.lastOrNull()?.let { return it.second }
            }
            return outline.lastOrNull { it.page <= m.page }?.title
        }
        // مجموعات متتالية بنفس العنوان
        val groups = ArrayList<Pair<String?, MutableList<TextMark>>>()
        for (m in marks) {
            val t = titleFor(m)
            if (groups.isNotEmpty() && groups.last().first == t) groups.last().second.add(m)
            else groups.add(Pair(t, mutableListOf(m)))
        }
        val out = ArrayList<Part>()
        for ((title, ms) in groups) {
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
                    out.add(Part(title, rows.joinToString("\n"), rows.joinToString("\n"), rm))
                } else {
                    // نص طويل يُقسم إلى طلبات صغيرة تناسب ذاكرة النموذج
                    val cur = StringBuilder()
                    val curMarks = ArrayList<TextMark>()
                    for (m in rm) {
                        val t = markLines(m).joinToString("\n")
                        if (cur.isNotEmpty() && cur.length + t.length > budget) {
                            out.add(Part(title, null, cur.toString(), curMarks.toList()))
                            cur.clear()
                            curMarks.clear()
                        }
                        if (cur.isNotEmpty()) cur.append('\n')
                        cur.append(t)
                        curMarks.add(m)
                    }
                    if (cur.isNotEmpty()) out.add(Part(title, null, cur.toString(), curMarks.toList()))
                }
            }
        }
        return out
    }

    fun prompt(p: Part): String = "SECTION: " + (p.title ?: "") + "\nNOTES:\n" + p.notes

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
        val cleaned = AiOrganizer.clean(raw)
        val t = p.title?.let { norm(it) }
        val noTitle = cleaned.lines().filter { l -> t == null || norm(l.trimStart('#', '-', ' ')) != t }
            .joinToString("\n").trim()
        if (noTitle.isBlank()) return fallback(p.notes)
        return if (suspicious(noTitle, p.notes + " " + (p.title ?: ""))) fallback(p.notes) else noTitle
    }

    /** يجمع الأجزاء: عنوان لكل قسم، وجداول مفصولة بسطر فارغ، ودون نقاط مكررة. */
    fun assemble(done: List<Pair<String?, String>>): String {
        val sections = LinkedHashMap<String?, MutableList<String>>()
        done.forEach { (t, b) -> sections.getOrPut(t) { ArrayList() }.add(b) }
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        for ((title, bodies) in sections) {
            if (title != null) out.add("# $title")
            for (line in bodies.joinToString("\n\n").lines()) {
                val k = norm(line)
                if (line.isNotBlank() && !line.contains(" | ") && k.isNotEmpty() && !seen.add(k)) continue
                if (line.isBlank() && (out.isEmpty() || out.last().isBlank())) continue
                out.add(line)
            }
            out.add("")
        }
        return out.joinToString("\n").trim()
    }
}
