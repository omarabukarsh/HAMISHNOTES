package com.mynotes.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ترتيب الملخص بالذكاء الاصطناعي. النتيجة تُحفظ منفصلة لكل مستند، والملخص الأصلي لا يتغير أبدًا.
 */
object AiOrganizer {
    private const val SYSTEM = """You organize study notes for a student (any subject).
Reorganize the notes you receive into a clear, well-structured study summary.
Strict rules:
- Use ONLY information found in the notes. Never add facts, numbers or explanations that are not in the notes.
- Do not drop any information from the notes.
- Keep every note in its original language (Arabic stays Arabic, English stays English). Keep names and terms exactly as written.
- Group related points under short headings. Merge repeated points.
- When several items share the same attributes (for example type, cause, effect, date, use), present them as a table.
You may also receive SOURCE: the text of the document pages the notes come from. Use the source only as a reference:
- to understand each note in its context and put it under the right heading of the document,
- to complete a note whose words or sentence were cut, and to fix broken words,
- to rebuild a table when the notes contain parts of it.
The summary stays about the notes: do not copy other parts of the source that the notes do not mention.
Output format (plain text only, no other markdown, no introduction, no closing remarks):
# Heading
## Subheading
- bullet point
Tables: one row per line, cells separated by " | ", first row is the header."""

    /** نص صفحة كمصدر: القراءة المنظمة بالذكاء الاصطناعي إن وُجدت، وإلا نص الصفحة المقروء. */
    private fun pageSource(s: DocSession, page: Int): String =
        (AiPageReader.result(s, page)?.trim('\u200B') ?: "").ifBlank { s.ocr[page]?.words?.let { AiPageReader.plainText(it) } ?: "" }

    private fun tokens(t: String): Set<String> =
        t.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }.toSet()

    /** يختار من صفحات المستند ما يناسب هذا الجزء من الملخص (حسب الكلمات المشتركة) ضمن حدّ الطول. */
    private fun sourceFor(part: String, pages: Map<Int, String>, budget: Int): String {
        val want = tokens(part)
        if (want.isEmpty() || pages.isEmpty()) return ""
        val ranked = pages.mapNotNull { (p, txt) ->
            val score = tokens(txt).count { it in want }
            if (score >= 2) Triple(p, txt, score) else null
        }.sortedByDescending { it.third }.take(3)
        if (ranked.isEmpty()) return ""
        val each = budget / ranked.size
        return ranked.sortedBy { it.first }.joinToString("\n\n") { (p, txt, _) ->
            "[Page ${p + 1}]\n" + bestWindow(txt, want, each)
        }
    }

    /** إن كانت الصفحة أطول من الحد: الجزء الذي فيه أكثر الكلمات المشتركة. */
    private fun bestWindow(txt: String, want: Set<String>, max: Int): String {
        if (txt.length <= max) return txt
        val lines = txt.lines()
        var best = 0
        var bestScore = -1
        for (i in lines.indices) {
            var len = 0
            var sc = 0
            var j = i
            while (j < lines.size && len + lines[j].length <= max) {
                sc += tokens(lines[j]).count { it in want }
                len += lines[j].length + 1
                j++
            }
            if (sc > bestScore) {
                bestScore = sc
                best = i
            }
        }
        val sb = StringBuilder()
        for (j in best until lines.size) {
            if (sb.length + lines[j].length > max) break
            sb.append(lines[j]).append('\n')
        }
        return sb.toString().ifBlank { txt.take(max) }
    }

    /** حالة الترتيب الجاري (واحد فقط في كل مرة). */
    var runningDoc by mutableStateOf<String?>(null)
        private set
    var stepText by mutableStateOf("")
        private set
    var partial by mutableStateOf("")
        private set
    var stepFraction by mutableStateOf(0f)
    /** ضغط صريح على الترتيب: نافذة معالجة تمنع استعمال التطبيق حتى ينتهي أو يُلغى (التشغيل التلقائي لا يحجب). */
    var blocking by mutableStateOf(false)
        private set
    /** كيف قُرئت صفحات التظليل في آخر ترتيب صريح (للتقرير الصادق في الرسالة الختامية). */
    private var visualPages = 0
    private var visualFailed = false

    fun cancelRunning() { job?.cancel() }
        private set
    private var job: Job? = null

    /** النتائج المحفوظة (تُحمَّل عند الطلب). */
    private val results = mutableStateMapOf<String, String>()

    private fun file(docId: String) = File(File(AppController.appContext.filesDir, "ai").also { it.mkdirs() }, "$docId.txt")

    fun result(docId: String): String? {
        if (!results.containsKey(docId)) {
            val f = file(docId)
            results[docId] = if (f.exists()) f.readText() else ""
        }
        return results[docId]?.takeIf { it.isNotBlank() }
    }

    fun save(docId: String, text: String) {
        results[docId] = text
        AppController.scope.launch(Dispatchers.IO) {
            val f = file(docId)
            if (text.isBlank()) f.delete() else f.writeText(text)
        }
    }

    /**
     * يحوّل الملخص المرتّب بالذكاء الاصطناعي إلى أجزاء ملخص (عناوين، فقرات، جداول)
     * بنفس قواعد عرضه في الدرج، ليُحفظ PDF بنفس شكل ملفات الملخص الأخرى.
     */
    /** يفصل **المهم** عن النص: (النص بدون النجوم، الأجزاء المهمة). */
    fun boldParts(s: String): Pair<String, List<String>> {
        if (!s.contains("**")) return Pair(s, emptyList())
        val em = ArrayList<String>()
        val sb = StringBuilder()
        val parts = s.split("**")
        parts.forEachIndexed { k, part ->
            if (k % 2 == 1 && k < parts.lastIndex) em.add(part)
            sb.append(part)
        }
        return Pair(sb.toString(), em.filter { it.isNotBlank() })
    }

    fun toBlocks(text: String): List<SBlock> {
        val out = ArrayList<SBlock>()
        val lines = text.lines()
        var i = 0
        while (i < lines.size) {
            val l = lines[i].trim()
            when {
                l.isEmpty() -> {}
                l.startsWith("## ") -> out.add(SHeading(l.removePrefix("## ").trim(), emptyList(), "ai$i"))
                l.startsWith("# ") -> out.add(SHeading(l.removePrefix("# ").trim(), emptyList(), "ai$i"))
                l.contains(" | ") -> {
                    val start = i
                    val rows = ArrayList<List<String>>()
                    while (i < lines.size && lines[i].contains(" | ")) {
                        rows.add(lines[i].trim().replace("**", "").split(" | ").map { it.trim() })
                        i++
                    }
                    i--
                    out.add(STable(rows, emptyList(), "ai$start", header = true))
                }
                l.startsWith("- ") -> { val (tx, em) = boldParts("•  " + l.removePrefix("- ").trim()); out.add(SPara(tx, em, emptyList(), "ai$i")) }
                else -> { val (tx, em) = boldParts(l); out.add(SPara(tx, em, emptyList(), "ai$i")) }
            }
            i++
        }
        return out
    }

    /** يقسّم النص إلى أجزاء تناسب ذاكرة النموذج (عند حدود الفقرات). */
    fun chunks(text: String, maxChars: Int): List<String> {
        val paras = text.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }
        val out = ArrayList<String>()
        val cur = StringBuilder()
        for (p in paras) {
            if (cur.isNotEmpty() && cur.length + p.length + 2 > maxChars) {
                out.add(cur.toString())
                cur.clear()
            }
            if (p.length > maxChars) {
                // فقرة طويلة جدًا: تُقسم عند الأسطر
                p.lines().forEach { line ->
                    if (cur.isNotEmpty() && cur.length + line.length + 1 > maxChars) {
                        out.add(cur.toString())
                        cur.clear()
                    }
                    cur.append(line).append('\n')
                }
            } else {
                cur.append(p).append("\n\n")
            }
        }
        if (cur.isNotBlank()) out.add(cur.toString())
        return out
    }

    /** ينظّف مخرجات النموذج إلى صيغة العرض: عناوين، نقاط، جداول بـ " | ". */
    /** النماذج تكتب أحيانًا رموز LaTeX مثل $\\beta$ و $\\rightarrow$: تُحوّل إلى رموزها. */
    fun latex(s: String): String {
        if (!s.contains('$') && !s.contains('\\')) return s
        val map = mapOf(
            "\\beta" to "β", "\\alpha" to "α", "\\gamma" to "γ", "\\delta" to "δ", "\\mu" to "µ",
            "\\rightarrow" to "→", "\\to" to "→", "\\leftarrow" to "←", "\\uparrow" to "↑", "\\downarrow" to "↓",
            "\\approx" to "≈", "\\ge" to "≥", "\\geq" to "≥", "\\le" to "≤", "\\leq" to "≤", "\\times" to "×",
            "\\pm" to "±", "\\%" to "%", "\\frac{1}{2}" to "½"
        )
        var out = Regex("\\$([^$\\n]{1,40})\\$").replace(s) { m ->
            var x = m.groupValues[1]
            map.entries.sortedByDescending { it.key.length }.forEach { (k, v) -> x = x.replace(k, v) }
            x.replace(Regex("_\\{([^}]*)\\}"), "$1").replace("{", "").replace("}", "").trim()
        }
        map.entries.sortedByDescending { it.key.length }.forEach { (k, v) -> out = out.replace(k, v) }
        return out
    }

    fun clean(raw: String): String {
        val lines = latex(raw).replace("```", "").lines().map { it.trimEnd() }
        val out = ArrayList<String>()
        for (l0 in lines) {
            var l = l0.replace("**", "").replace("__", "")
            val t = l.trim()
            // سطر فاصل في جداول markdown مثل |---|---|
            if (t.isNotEmpty() && t.all { it == '|' || it == '-' || it == ':' || it == ' ' }) continue
            if (t.startsWith("|") && t.endsWith("|") && t.length > 2) {
                l = t.removePrefix("|").removeSuffix("|").split("|").joinToString(" | ") { it.trim() }
            }
            if (t.startsWith("* ") || t.startsWith("• ")) l = "- " + t.substring(2)
            out.add(l)
        }
        return out.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
    }

    // ---------- المتابعة: ما رُتّب من التمييزات (معرّف التمييز ← نصه عند الترتيب) ----------

    private fun doneFile(docId: String) = File(File(AppController.appContext.filesDir, "ai").also { it.mkdirs() }, "$docId.done.json")
    private val doneCache = HashMap<String, Map<String, String>>()

    /** عدد التمييزات الجديدة منذ آخر ترتيب (للعرض). */
    val pendingCount = mutableStateMapOf<String, Int>()

    fun processed(docId: String): Map<String, String> = doneCache.getOrPut(docId) {
        try {
            val f = doneFile(docId)
            if (!f.exists()) emptyMap() else {
                val o = org.json.JSONObject(f.readText())
                o.keys().asSequence().associateWith { o.getString(it) }
            }
        } catch (t: Throwable) {
            emptyMap()
        }
    }

    private fun setProcessed(docId: String, m: Map<String, String>) {
        doneCache[docId] = m
        AppController.scope.launch(Dispatchers.IO) {
            try {
                val f = doneFile(docId)
                if (m.isEmpty()) f.delete() else f.writeText(org.json.JSONObject(m).toString())
            } catch (t: Throwable) {
            }
        }
    }

    /** هل الإكمال ممكن: يوجد ملخص مرتّب ومعروف ما رُتّب منه. */
    fun canContinue(docId: String) = result(docId) != null && processed(docId).isNotEmpty()

    /** التمييزات الجديدة، أو الجزء الذي أضيف إلى تمييز قديم، منذ آخر ترتيب. */
    fun pendingMarks(s: DocSession): List<TextMark> {
        val done = processed(s.id)
        return s.allMarks().mapNotNull { m ->
            val old = done[m.id]
            when {
                old == null -> m
                old == m.text -> null
                old.isNotBlank() && m.text.contains(old) -> m.copy(text = m.text.replace(old, "").trim()).takeIf { it.text.isNotBlank() }
                else -> m
            }
        }
    }

    fun refreshPending(s: DocSession) {
        pendingCount[s.id] = if (canContinue(s.id)) pendingMarks(s).size else 0
    }

    private fun notesOf(marks: List<TextMark>): String =
        Summary.plainText("", Summary.display(marks, true, SummaryEdits(), null, false) { AppController.labelOf(it) })

    /** هل الإكمال التلقائي مفعّل (بعد التظليل الجديد). */
    var autoContinue by mutableStateOf(prefs().getBoolean("auto_continue", false))
        private set
    fun toggleAutoContinue(on: Boolean) { autoContinue = on; prefs().edit().putBoolean("auto_continue", on).apply() }

    /** قراءة صفحات التظليل بالذكاء تلقائيًا قبل الترتيب (عناوين أدق، أبطأ في أول مرة). */
    var autoReadPages by mutableStateOf(prefs().getBoolean("auto_read_pages", false))
        private set
    fun toggleAutoReadPages(on: Boolean) { autoReadPages = on; prefs().edit().putBoolean("auto_read_pages", on).apply() }

    /** مراجعة ذاتية: بعد كتابة كل جزء يراجعه النموذج مقابل التظليل ويصححه (يُقبل التصحيح فقط إن اجتاز الفحوص). */
    var selfReview by mutableStateOf(prefs().getBoolean("self_review", true))
        private set
    fun toggleSelfReview(on: Boolean) { selfReview = on; prefs().edit().putBoolean("self_review", on).apply() }

    /** بطاقات مراجعة تلقائية بالذكاء الاصطناعي من النصوص المظللة. */
    var autoCards by mutableStateOf(prefs().getBoolean("auto_cards", false))
        private set
    fun toggleAutoCards(on: Boolean) { autoCards = on; prefs().edit().putBoolean("auto_cards", on).apply() }

    private fun prefs() = AppController.appContext.getSharedPreferences("mynotes_ai", Context.MODE_PRIVATE)

    const val CARDS_SYSTEM = """You write exam flashcards from part of a student's study summary (any subject).
Rules:
- Use ONLY the summary. Every answer is copied from it: a short phrase or one short sentence (at most 12 words). Never add facts, numbers or names.
- Ask what an exam would ask: definitions, causes and effects, mechanisms, steps, differences, numbers and dates, uses, exceptions, what is NOT done and why.
- One fact per card. No yes/no questions. Do not put the answer inside the question. Never two cards about the same fact.
- Write each card in the language of its point. Keep names and terms exactly as written.
- Points between ** ** are important: cover them first. At most one card per two points, and at most 6 cards.
Output format (plain text only, nothing else):
Q: question
A: answer
(blank line between cards)

Example
SUMMARY:
## Light
- Light travels in straight lines in a uniform medium.
## الضوء
- لا ينتقل الضوء في الفراغ ببطء، بل بسرعة ثابتة.
OUTPUT:
Q: How does light travel in a uniform medium?
A: In straight lines

Q: كيف ينتقل الضوء في الفراغ؟
A: بسرعة ثابتة"""

    /** يقرأ البطاقات من مخرجات النموذج (Q:/A: أو س:/ج:). */
    fun parseCards(raw: String): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        var q: String? = null
        val a = StringBuilder()
        fun flush() {
            val qq = q?.trim()
            val aa = a.toString().trim()
            if (!qq.isNullOrEmpty() && aa.isNotEmpty()) out.add(qq to aa)
            q = null
            a.clear()
        }
        val qRe = Regex("^(?:\\d+[.)]\\s*)?(?:Q|Question|س|سؤال)\\s*[:：]\\s*(.*)$", RegexOption.IGNORE_CASE)
        val aRe = Regex("^(?:A|Answer|ج|جواب|الإجابة)\\s*[:：]\\s*(.*)$", RegexOption.IGNORE_CASE)
        for (raw0 in latex(raw).replace("**", "").lines()) {
            val l = raw0.trim()
            val qm = qRe.find(l)
            val am = aRe.find(l)
            when {
                qm != null -> { flush(); q = qm.groupValues[1] }
                am != null && q != null -> a.append(am.groupValues[1])
                l.isNotEmpty() && q != null && a.isNotEmpty() -> a.append(' ').append(l)
            }
        }
        flush()
        return out
    }

    private fun normHead(h: String) = h.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** يدمج ملخص التمييزات الجديدة في الملخص القائم: تحت نفس العنوان إن وُجد، وإلا في آخره. */
    /**
     * يضيف محتوى قسم إلى قسم موجود: ما تحت عنوان فرعي «## » موجود يُضاف في آخر ذلك العنوان الفرعي،
     * والعنوان الفرعي الجديد وما بلا عنوان فرعي يُضاف في آخر القسم.
     */
    private fun mergeSubs(target: MutableList<String>, addition: List<String>) {
        // أجزاء الإضافة: (عنوان فرعي أو null، أسطره)
        val chunks = ArrayList<Pair<String?, MutableList<String>>>()
        chunks.add(null to ArrayList())
        for (l in addition) if (l.trim().startsWith("## ")) chunks.add(l.trim() to ArrayList()) else chunks.last().second.add(l)
        for ((sub, lines) in chunks) {
            val content = lines.joinToString("\n").trim()
            while (target.isNotEmpty() && target.last().isBlank()) target.removeAt(target.lastIndex)
            if (sub == null) {
                if (content.isNotEmpty()) target.add(content)
                continue
            }
            val at = target.indexOfFirst { it.trim().startsWith("## ") && normHead(it.trim().removePrefix("## ")) == normHead(sub.removePrefix("## ")) }
            if (at < 0) {
                target.add("")
                target.add(sub)
                if (content.isNotEmpty()) target.add(content)
            } else if (content.isNotEmpty()) {
                // نهاية العنوان الفرعي: قبل العنوان الفرعي التالي أو آخر القسم
                var end = target.size
                for (k in at + 1 until target.size) if (target[k].trim().startsWith("## ")) { end = k; break }
                while (end > at + 1 && target[end - 1].isBlank()) end--
                target.add(end, content)
            }
        }
        target.add("")
    }

    fun merge(existing: String, addition: String): String {
        fun sections(text: String): MutableList<Pair<String?, MutableList<String>>> {
            val out = ArrayList<Pair<String?, MutableList<String>>>()
            out.add(null to ArrayList<String>())
            for (l in text.lines()) {
                if (l.trim().startsWith("# ")) out.add(l.trim() to ArrayList<String>()) else out.last().second.add(l)
            }
            return out
        }
        val base = sections(existing)
        for ((h, body) in sections(addition)) {
            val content = body.joinToString("\n").trim()
            if (h == null) {
                if (content.isNotEmpty()) base.last().second.addAll(listOf("", content))
                continue
            }
            val match = base.firstOrNull { it.first != null && normHead(it.first!!.removePrefix("# ")) == normHead(h.removePrefix("# ")) }
            if (match != null) {
                mergeSubs(match.second, body)
            } else {
                base.add(h to mutableListOf<String>().apply { if (content.isNotEmpty()) add(content); add("") })
            }
        }
        return base.joinToString("\n") { (h, body) ->
            (if (h != null) h + "\n" else "") + body.joinToString("\n")
        }.replace(Regex("\n{3,}"), "\n\n").trim()
    }

    /** ترتيب كامل للملخص (append = false) أو إكمال بالتمييزات الجديدة فقط (append = true). */
    fun start(s: DocSession, notes: String) = organize(s, notes, append = false, marks = s.allMarks())

    /** ترتيب كامل تلقائي (أول مرة، أو بعد حذف تظليل)، بهدوء. */
    fun startAuto(s: DocSession) {
        val marks = s.allMarks()
        if (marks.isEmpty()) return
        organize(s, notesOf(marks), append = false, marks = marks, quiet = true)
    }

    /**
     * ما يلزم تلقائيًا: "full" = لا ملخص بعد، أو ملخص قديم، أو حُذف تظليل منذ آخر ترتيب؛
     * "continue" = تظليل جديد فقط؛ null = لا شيء.
     */
    fun autoMode(s: DocSession): String? {
        if (!canContinue(s.id)) return "full"
        val ids = s.allMarks().map { it.id }.toSet()
        if (processed(s.id).keys.any { it !in ids }) return "full"
        return if (pendingMarks(s).isNotEmpty()) "continue" else null
    }

    /** يكمل الملخص المرتّب بما ظُلّل بعده. */
    fun continueSummary(s: DocSession, quiet: Boolean = false) {
        if (!canContinue(s.id)) return
        val news = pendingMarks(s)
        if (news.isEmpty()) {
            if (!quiet) AppController.messages.tryEmit(L("لا توجد تظليلات جديدة", "No new highlights"))
            return
        }
        organize(s, notesOf(news), append = true, marks = news, quiet = quiet)
    }

    // ---------- ذاكرة الأجزاء المرتّبة (لإعادة الترتيب السريعة) ----------

    private fun memoFile(docId: String) = File(File(AppController.appContext.filesDir, "ai/memo").also { it.mkdirs() }, "$docId.json")

    private fun loadMemo(docId: String): MutableMap<String, String> = try {
        val f = memoFile(docId)
        if (!f.exists()) HashMap() else {
            val o = org.json.JSONObject(f.readText())
            o.keys().asSequence().associateWith { o.getString(it) }.toMutableMap()
        }
    } catch (t: Throwable) {
        HashMap()
    }

    private fun saveMemo(docId: String, m: Map<String, String>) {
        try {
            // حد أعلى حتى لا يكبر الملف بلا نهاية
            val keep = if (m.size > 600) m.entries.toList().takeLast(600).associate { it.toPair() } else m
            memoFile(docId).writeText(org.json.JSONObject(keep).toString())
        } catch (t: Throwable) {
        }
    }

    private fun organize(s: DocSession, notes: String, append: Boolean, marks: List<TextMark>, quiet: Boolean = false) {
        if (job?.isActive == true || AiPageReader.runningKey != null) return
        if (!Ai.ready || marks.isEmpty() || notes.isBlank()) return
        val docId = s.id
        // ذاكرة النموذج على الجهاز صغيرة: طلبات صغيرة لكل عنوان
        val arabic = notes.count { it in '؀'..'ۿ' } > notes.length / 4
        val budget = if (arabic) 1300 else 2400
        val explicit = !quiet
        // ما رُتّب: نص كل تمييز كما هو الآن (للتمييز الذي أضيف إليه نص: نصه الكامل)
        val snapshot = s.allMarks().filter { m -> marks.any { it.id == m.id } }.associate { it.id to it.text }
        val before = if (append) result(docId) ?: "" else ""
        runningDoc = docId
        blocking = explicit
        visualPages = 0
        visualFailed = false
        partial = ""
        stepFraction = 0f
        job = AppController.scope.launch {
            val done = ArrayList<Triple<String?, String?, String>>()
            val cards = ArrayList<Pair<String, String>>()
            // ذاكرة الأجزاء: ما لم يتغير لا يُعاد إرساله للنموذج
            val memo = withContext(Dispatchers.IO) { loadMemo(docId) }
            val model = (if (selfReview) "r|" else "") + Ai.selected.id
            fun shown(extra: Triple<String?, String?, String>? = null): String {
                val cur = AiStructure.assemble(if (extra == null) done else done + extra)
                return if (append) merge(before, cur) else cur
            }
            try {
                // الصفحات التي فيها تمييزات تُقرأ إن لم تكن مقروءة: منها تُؤخذ عناوين المحاضرة ورؤوس الجداول
                stepText = L("جارٍ قراءة صفحات المستند…", "Reading the document pages…")
                marks.map { it.page }.distinct().forEach { p -> if (s.ocr[p] == null) s.ocrPage(p) }
                // «قراءة الصفحة بالذكاء» تلقائيًا لكل صفحة فيها تظليل ولم تُقرأ بعد: عناوينها تُستعمل في الهيكل.
                // من النص المقروء فقط (أسرع)؛ الصورة فقط للصفحات التي لا نص فيها. كل صفحة تُقرأ مرة واحدة وتُحفظ.
                // في الضغط الصريح مع قارئ الصور تُقرأ صفحات التظليل بصريًا (أدناه) فلا حاجة لهذه القراءة النصية.
                val visualMode = explicit && Ai.visionOk
                if (autoReadPages && !visualMode && Ai.installed(Ai.selected)) {
                    val todo = marks.map { it.page }.distinct().sorted().filter { AiPageReader.result(s, it) == null }
                    todo.forEachIndexed { k, pg ->
                        stepFraction = k / (todo.size + 1f)
                        try {
                            AiPageReader.read(s, pg, useImage = false, onStep = {
                                stepText = L("قراءة صفحات التظليل قبل الكتابة (${k + 1} من ${todo.size})", "Reading highlighted pages first (${k + 1} of ${todo.size})")
                            })
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            // صفحة تعذرت قراءتها: يكمل الترتيب بعناوين شكل الأسطر
                        }
                    }
                }
                // القراءة البصرية: صفحة التظليل كاملة بصورتها مع إطار مرقّم حول كل تظليل (الضغط الصريح فقط).
                // تُقبل قراءة النموذج لتظليل فقط إن كانت كاملة ومعقولة الطول؛ وإلا يبقى نص القراءة الضوئية لذلك التظليل.
                var readMarks = marks
                if (visualMode) {
                    val byPage = marks.groupBy { it.page }.toSortedMap()
                    val visual = HashMap<String, String>()
                    var failed = false
                    var k = 0
                    for ((pg, pm) in byPage) {
                        stepFraction = k / (byPage.size + 1f) * 0.6f
                        k++
                        try {
                            visual.putAll(AiPageReader.readVisual(s, pg, pm) { stepText = it })
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            failed = true
                            break
                        }
                    }
                    visualFailed = failed
                    readMarks = marks.map { m ->
                        val v = visual[m.id] ?: return@map m
                        val txt = Regex("(?is)text:\\s*(.*?)(?:\\ncontext:|$)").find(v)?.groupValues?.get(1)?.trim() ?: ""
                        val ok = txt.isNotBlank() && !txt.contains("[unreadable]") &&
                            txt.length >= m.text.length * 0.5 && txt.length <= m.text.length * 2.2 + 20
                        if (ok) { visualPages++; m.copy(text = AiOrganizer.clean(txt)) } else m
                    }
                }
                val parts = AiStructure.parts(s, readMarks, budget)
                // سياق من الصفحات غير المظللة (نصها المحفوظ): للفهم فقط، قصير ليبقى الطلب سريعًا على الجهاز
                run {
                    val hl = marks.map { it.page }.toSet()
                    val others = HashMap<Int, String>()
                    for (pg in 0 until (s.pdf?.count ?: 0)) {
                        if (pg in hl) continue
                        val t = pageSource(s, pg)
                        if (t.isNotBlank()) others[pg] = t
                    }
                    if (others.isNotEmpty()) parts.forEach { p ->
                        if (p.table == null && !AiStructure.trivial(p)) p.context = sourceFor(p.notes, others, if (arabic) 500 else 900)
                    }
                }
                // بطاقات المراجعة من الملخص نفسه (ما اجتاز الفحوص): النقاط تُرسل للنموذج دفعات لكل عنوان رئيسي،
                // والجداول بطاقات بالكود لكل صف. كل بطاقة تُفحص (جواب من الملخص، بلا أرقام جديدة) ويُحذف المكرر بالمعنى.
                val cardBuf = StringBuilder()
                val cardTables = ArrayList<Pair<String, String>>()
                suspend fun flushCards(force: Boolean) {
                    if (!autoCards) return
                    // أقل من نقطتين للنموذج: لا طلب (لا يستحق وقته على الجهاز)
                    if (cardBuf.isNotBlank() && cardBuf.lines().count { it.startsWith("- ") } < 2 && force) cardBuf.clear()
                    if (cardBuf.isNotBlank() && (force || cardBuf.length >= budget / 2)) {
                        // ذاكرة البطاقات: نفس النقاط = نفس البطاقات (إعادة الترتيب بعد حذف تظليل لا تعيد كتابتها)
                        val src = cardBuf.toString()
                        val ck = "cards2:" + AiStructure.hash("$model|$src")
                        val cached = memo[ck]
                        try {
                            if (cached != null) {
                                cards.addAll(cached.lines().mapNotNull { l -> l.split('\t').takeIf { it.size == 2 }?.let { it[0] to it[1] } })
                            } else {
                                stepText = L("جارٍ إعداد بطاقات المراجعة…", "Making review cards…")
                                val made = AiStructure.dedupeCards(
                                    parseCards(Ai.generate(CARDS_SYSTEM, AiStructure.cardsPrompt(src)))
                                        .map { (q, a) -> AiStructure.arabicQuestion(q, a) to a }
                                        .filter { (q, a) -> AiStructure.cardOk(q, a, src) }
                                )
                                memo[ck] = made.joinToString("\n") { (q, a) -> q.replace('\t', ' ').replace('\n', ' ') + "\t" + a.replace('\t', ' ').replace('\n', ' ') }
                                cards.addAll(made)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                        }
                        cardBuf.clear()
                    }
                    if (force || cardBuf.isEmpty()) {
                        cards.addAll(cardTables)
                        cardTables.clear()
                    }
                }
                parts.forEachIndexed { i, p ->
                    stepFraction = i / parts.size.toFloat()
                    stepText = when {
                        append -> L("جارٍ إكمال الملخص بالتظليل الجديد…", "Adding new highlights to the summary…")
                        else -> L("جارٍ الترتيب: ${p.sub ?: p.title ?: "…"} (${i + 1} من ${parts.size})", "Organizing: ${p.sub ?: p.title ?: "…"} (${i + 1} of ${parts.size})")
                    }
                    val key = AiStructure.cacheKey(p, model)
                    val body = AiStructure.emphasize(
                        p.table ?: memo[key] ?: if (AiStructure.trivial(p)) AiStructure.fallback(p.notes) else {
                            val draft = AiStructure.body(
                                p,
                                Ai.generate(AiStructure.SYSTEM, AiStructure.prompt(p)) { x -> partial = shown(Triple(p.title, p.sub, clean(x))) }
                            )
                            // مراجعة ذاتية: فقط إن بقي في المسودة ما يحتاجها (تنسيق أو نقطة طويلة)؛ التصحيح يُقبل فقط إن اجتاز الفحوص
                            val fin = if (selfReview && draft != AiStructure.fallback(p.notes) && AiStructure.needsReview(draft)) {
                                stepText = L("مراجعة ما كُتب: ${p.sub ?: p.title ?: "…"}", "Reviewing: ${p.sub ?: p.title ?: "…"}")
                                try {
                                    AiStructure.reviewed(p, Ai.generate(AiStructure.REVIEW_SYSTEM, AiStructure.reviewPrompt(p, draft))) ?: draft
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (t: Throwable) {
                                    draft
                                }
                            } else draft
                            fin.also { memo[key] = it }
                        },
                        p.emphasis
                    )
                    done.add(Triple(p.title, p.sub, body))
                    partial = shown()
                    // البطاقات لكل عنوان رئيسي (حدود ثابتة فتنفع ذاكرة البطاقات بعد أي تعديل)
                    if (i > 0 && parts[i - 1].title != p.title) flushCards(true)
                    val topic = p.sub ?: p.title
                    if (p.table != null) {
                        cardTables.addAll(AiStructure.tableCards(p.table, p.headed, topic))
                    } else {
                        val all = body.lines().filter { it.isNotBlank() }
                        // نقاط «بند: تفصيل» بطاقات بالكود، والباقي للنموذج
                        val pts = ArrayList<String>()
                        for (l in all) {
                            val c = AiStructure.keyedCards(listOf(l), topic)
                            if (c.isEmpty()) pts.add(l) else cardTables.addAll(c)
                        }
                        if (pts.isNotEmpty()) {
                            if (cardBuf.isNotEmpty()) cardBuf.append("\n\n")
                            if (topic != null) cardBuf.append("## ").append(topic).append('\n')
                            cardBuf.append(pts.joinToString("\n"))
                        }
                    }
                    flushCards(false)
                }
                flushCards(true)
                val finalCards = AiStructure.dedupeCards(cards)
                cards.clear()
                cards.addAll(finalCards)
                withContext(Dispatchers.IO) { saveMemo(docId, memo) }
                save(docId, shown())
                setProcessed(docId, if (append) processed(docId) + snapshot else snapshot)
                if (autoCards) s.putAiCards(cards, replace = !append)
                refreshPending(s)
                if (explicit && visualPages > 0) AppController.messages.tryEmit(
                    L("قُرئ $visualPages تظليل من صورة الصفحة نفسها، والباقي من نص القراءة الضوئية", "$visualPages highlights were read from the page image; the rest from the OCR text")
                )
                if (explicit && Ai.visionOk && visualFailed) AppController.messages.tryEmit(
                    L("تعذرت القراءة البصرية للصفحات؛ رُتّب الملخص من نص القراءة الضوئية", "Visual reading failed; the summary used the OCR text")
                )
                AppController.messages.tryEmit(
                    when {
                        append && cards.isNotEmpty() -> L("أُكمل الملخص وأُضيفت ${cards.size} بطاقة مراجعة", "Summary updated, ${cards.size} review cards added")
                        append -> L("أُكمل الملخص بالتظليل الجديد", "Summary updated with new highlights")
                        cards.isNotEmpty() -> L("اكتمل ترتيب الملخص و${cards.size} بطاقة مراجعة. راجعها قبل الاعتماد عليها", "Summary organized with ${cards.size} review cards. Review before relying on them")
                        else -> L("اكتمل ترتيب الملخص. راجعه قبل الاعتماد عليه", "Summary organized. Review it before relying on it")
                    }
                )
            } catch (e: CancellationException) {
                // ترتيب كامل لم يكتمل: يُحفظ ما كُتب، ولا يُعرف ما رُتّب فيُطفأ الإكمال حتى يُعاد الترتيب
                withContext(NonCancellable + Dispatchers.IO) { saveMemo(docId, memo) }
                if (!append && done.isNotEmpty()) {
                    save(docId, shown())
                    setProcessed(docId, emptyMap())
                }
            } catch (t: Throwable) {
                if (!quiet) AppController.messages.tryEmit(
                    Ai.errorText(t, L("تعذر تشغيل النموذج. جرّب نموذجًا آخر من الإعدادات، أو أغلق تطبيقات أخرى لتحرير الذاكرة.",
                        "Could not run the model. Try another model in Settings, or close other apps to free memory."))
                )
            } finally {
                runningDoc = null
                blocking = false
                stepText = ""
                partial = ""
            }
        }
    }

    private const val TABLE_SYSTEM = """You turn the points of ONE section of a study summary (any subject) into ONE comparison table.
Rules:
- Choose 2 to 5 columns from the attributes the points share (for example: Item | Type | Use | Note).
- One row per item. Use ONLY the words, numbers and names in the points. Keep every number, every name and every negation (not, no, لا).
- Keep the language of the points. Empty cell: "—".
- Output only the table: one row per line, cells separated by " | ", first row is the header. No introduction.

Example
POINTS:
Pen G: destroyed by gastric acid, given IV or IM
Pen V: acid-stable, given orally, only for mild infections
OUTPUT:
Drug | Acid stability | Route | Note
Pen G | Destroyed by gastric acid | IV or IM | —
Pen V | Acid-stable | Oral | Only for mild infections"""

    /** أقسام الملخص المرتّب التي فيها نقطتان أو أكثر (تصلح لجدول): سطر العنوان كما هو. */
    fun tableSections(text: String): List<String> {
        val lines = text.lines()
        val out = ArrayList<String>()
        lines.forEachIndexed { i, l ->
            val h = l.trim()
            if (!h.startsWith("# ") && !h.startsWith("## ")) return@forEachIndexed
            var n = 0
            var j = i + 1
            while (j < lines.size && !lines[j].trim().startsWith("#")) {
                val x = lines[j].trim()
                if (x.isNotEmpty() && !x.contains(" | ")) n++
                j++
            }
            if (n >= 2) out.add(h)
        }
        return out
    }

    /** يحوّل نقاط قسم إلى جدول مقارنة بالذكاء؛ يُستبدل فقط إن لم يُسقط رقمًا أو دواءً أو نفيًا. */
    fun tableFromSection(s: DocSession, heading: String) {
        if (job?.isActive == true || AiPageReader.runningKey != null || !Ai.ready) return
        val docId = s.id
        val text = result(docId) ?: return
        runningDoc = docId
        blocking = true
        partial = ""
        stepFraction = 0f
        stepText = L("جارٍ إعداد جدول: ${heading.trimStart('#', ' ')}", "Making a table: ${heading.trimStart('#', ' ')}")
        job = AppController.scope.launch {
            try {
                val lines = text.lines().toMutableList()
                val i = lines.indexOfFirst { it.trim() == heading.trim() }
                if (i < 0) return@launch
                var j = i + 1
                while (j < lines.size && !lines[j].trim().startsWith("#")) j++
                val block = lines.subList(i + 1, j).toList()
                val points = block.map { it.trim() }.filter { it.isNotEmpty() && !it.contains(" | ") }
                    .map { it.removePrefix("- ").replace("**", "").trim() }
                if (points.size < 2) return@launch
                val notes = points.joinToString("\n")
                val res = clean(Ai.generate(TABLE_SYSTEM, "SECTION: ${heading.trimStart('#', ' ')}\nPOINTS:\n$notes") { partial = clean(it) })
                val table = res.lines().map { it.trim() }.filter { it.contains(" | ") }
                val tableText = table.joinToString("\n")
                // جدول مقارنة: صفّان على الأقل، أو صف واحد مع 3 أعمدة أو أكثر (جدول خصائص لدواء واحد)
                val enough = table.size >= 3 || (table.size == 2 && table[0].split(" | ").size >= 3)
                val ok = enough && !AiStructure.suspicious(tableText, notes + " " + heading) && !AiStructure.dropped(tableText, notes)
                if (!ok) {
                    AppController.messages.tryEmit(
                        L("لم يكن الجدول دقيقًا (أسقط أو أضاف معلومة)، فبقي القسم كما هو. جرّب قسمًا آخر أو «AI أقوى».",
                            "The table wasn’t accurate (it dropped or added information), so the section was kept. Try another section or “Stronger AI”.")
                    )
                    return@launch
                }
                // النقاط تُستبدل بالجدول، والجداول الموجودة في القسم تبقى بعده
                val keep = block.filter { it.trim().contains(" | ") }
                val newBlock = listOf("") + table + (if (keep.isNotEmpty()) listOf("") + keep else emptyList()) + listOf("")
                val updated = lines.subList(0, i + 1) + newBlock + lines.subList(j, lines.size)
                save(docId, updated.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim())
                AppController.messages.tryEmit(L("صار القسم جدولًا. راجعه قبل الاعتماد عليه", "The section is now a table. Review it before relying on it"))
            } catch (e: CancellationException) {
            } catch (t: Throwable) {
                AppController.messages.tryEmit(Ai.errorText(t, L("تعذر تشغيل النموذج", "Could not run the model")))
            } finally {
                runningDoc = null
                blocking = false
                stepText = ""
                partial = ""
            }
        }
    }

    private const val EDIT_SYSTEM = """You edit a student's study summary (any subject) following their instruction.
Rules:
- Apply the instruction to the summary and return the whole edited summary.
- Keep the same format: "# " headings, "## " subheadings, "- " bullets, tables with " | " between cells (first row header).
- Keep names and terms exactly as written. Keep each part in its language unless the instruction asks to translate.
- Do not add facts or numbers that are not in the summary unless the instruction explicitly asks you to add or explain something.
Output only the edited summary, no introduction or closing remarks."""

    /** تعديل الملخص المرتّب بتعليمات المستخدم (مثل: اختصر، اجعله جدولًا، ترجم العناوين). */
    fun editWithInstruction(s: DocSession, instruction: String) {
        if (job?.isActive == true || AiPageReader.runningKey != null || !Ai.ready) return
        val docId = s.id
        val before = result(docId) ?: return
        if (instruction.isBlank()) return
        if (before.length > 5000) {
            AppController.messages.tryEmit(
                L("الملخص أطول من قدرة النموذج الذي على الجهاز. جرّب «AI أقوى» من الإعدادات، أو عدّله يدويًا.",
                    "The summary is longer than the on-device model can handle. Try “Stronger AI” in Settings, or edit it manually.")
            )
            return
        }
        runningDoc = docId
        blocking = true
        partial = ""
        stepFraction = 0f
        stepText = L("جارٍ تعديل الملخص…", "Editing the summary…")
        job = AppController.scope.launch {
            try {
                val res = Ai.generate(EDIT_SYSTEM, "SUMMARY:\n\n$before\n\nINSTRUCTION: $instruction") { p -> partial = clean(p) }
                val out = clean(res)
                if (out.isNotBlank()) {
                    save(docId, out)
                    AppController.messages.tryEmit(L("عُدّل الملخص. راجعه قبل الاعتماد عليه", "Summary edited. Review it before relying on it"))
                }
            } catch (e: CancellationException) {
            } catch (t: Throwable) {
                AppController.messages.tryEmit(Ai.errorText(t, L("تعذر تشغيل النموذج", "Could not run the model")))
            } finally {
                runningDoc = null
                blocking = false
                stepText = ""
                partial = ""
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    // ---------- شرح نص محدد ----------

    fun explainSystem(): String = if (I18n.english)
        "You are a helpful tutor for any subject. Explain the given text simply and briefly (max 8 short lines). Keep technical terms. Do not invent numbers or facts."
    else
        "You are a helpful tutor for any subject. Explain the given text simply and briefly in Arabic (max 8 short lines). Keep English names and technical terms as they are. Do not invent numbers or facts."
}

/** يعرض الملخص المرتّب: عناوين، نقاط، فقرات، وجداول. */
/** **المهم** بخط عريض وتظليل ذهبي فاتح، كما في الملخص العادي. */
fun richText(s: String): androidx.compose.ui.text.AnnotatedString {
    val (plain, em) = AiOrganizer.boldParts(s)
    return emphasized(plain, em)
}

@Composable
fun AiSummaryText(text: String) {
    val lines = text.lines()
    var i = 0
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        while (i < lines.size) {
            val l = lines[i].trim()
            when {
                l.isEmpty() -> Spacer(Modifier.height(4.dp))
                l.startsWith("## ") -> Text(
                    l.removePrefix("## "), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.GoldDeep,
                    modifier = Modifier.padding(top = 6.dp)
                )
                l.startsWith("# ") -> Text(
                    l.removePrefix("# "), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Navy,
                    modifier = Modifier.padding(top = 10.dp)
                )
                l.contains(" | ") -> {
                    // جدول: الأسطر المتتالية التي فيها " | "
                    val rows = ArrayList<List<String>>()
                    while (i < lines.size && lines[i].contains(" | ")) {
                        rows.add(lines[i].replace("**", "").split(" | ").map { it.trim() })
                        i++
                    }
                    i--
                    AiTable(rows)
                }
                l.startsWith("- ") -> Row {
                    Text("•  ", fontSize = 14.sp, color = Palette.GoldDeep)
                    Text(richText(l.removePrefix("- ")), fontSize = 14.sp, lineHeight = 21.sp, color = Palette.TextPrimary)
                }
                else -> Text(richText(l), fontSize = 14.sp, lineHeight = 21.sp, color = Palette.TextPrimary)
            }
            i++
        }
    }
}

@Composable
private fun AiTable(rows: List<List<String>>) {
    val cols = rows.maxOf { it.size }
    val shape = RoundedCornerShape(6.dp)
    Column(
        Modifier.padding(vertical = 4.dp).horizontalScroll(rememberScrollState())
            .clip(shape).border(1.dp, Palette.Divider, shape)
    ) {
        rows.forEachIndexed { r, row ->
            Row(Modifier.background(if (r == 0) Palette.GoldSoft else Color.White)) {
                for (k in 0 until cols) {
                    Text(
                        row.getOrNull(k) ?: "",
                        fontSize = 12.sp, lineHeight = 17.sp,
                        fontWeight = if (r == 0) FontWeight.Bold else FontWeight.Normal,
                        color = Palette.TextPrimary,
                        modifier = Modifier.widthIn(min = 70.dp, max = 160.dp).border(0.5.dp, Palette.Divider).padding(6.dp)
                    )
                }
            }
        }
    }
}

/** لوحة «بالذكاء الاصطناعي» داخل درج الملخصات. */
@Composable
fun AiSummaryPanel(s: DocSession, notes: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = AppController
    val running = AiOrganizer.runningDoc == s.id
    val busyElsewhere = AiOrganizer.runningDoc != null && !running
    val saved = AiOrganizer.result(s.id)
    var editing by remember { mutableStateOf(false) }
    var readPage by remember { mutableStateOf<Int?>(null) }
    var mindMap by remember { mutableStateOf(false) }
    var tablePick by remember { mutableStateOf(false) }
    var aiSettings by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // بطاقة «قراءة الصفحة بالذكاء» أُزيلت بطلب المستخدم: الصفحات تُقرأ تلقائيًا (في الخلفية وقبل الترتيب)
        // تنبيه دائم: اقتراح يُراجع
        Text(
            L("ترتيب مقترح من نموذج يعمل داخل جهازك دون إنترنت. ", "Suggested by a model running on your device, offline. ") +
                L("ملخصك الأصلي لا يتغير. راجع المعلومات قبل الاعتماد عليها.", "Your original summary is unchanged. Review the information before relying on it."),
            fontSize = 12.sp, lineHeight = 18.sp, color = Palette.TextSecondary
        )
        Spacer(Modifier.height(10.dp))
        when {
            !Ai.ready -> {
                Text(
                    L("لم يُنزَّل نموذج الذكاء الاصطناعي بعد. نزّله مرة واحدة من الإعدادات، ثم يعمل دون إنترنت.",
                        "The AI model isn’t downloaded yet. Download it once from Settings; then it works offline."),
                    fontSize = 14.sp, color = Palette.Navy
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { c.settingsOpen = true }, colors = ButtonDefaults.buttonColors(containerColor = Palette.Navy)) {
                    Text(L("فتح الإعدادات", "Open settings"))
                }
            }
            running -> {
                Text(AiOrganizer.stepText.ifEmpty { Ai.engineStatus }, fontSize = 14.sp, color = Palette.Navy, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { AiOrganizer.stepFraction },
                    modifier = Modifier.fillMaxWidth(), color = Palette.Gold
                )
                if (Ai.engineStatus.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(Ai.engineStatus, fontSize = 12.sp, color = Palette.TextSecondary)
                }
                TextButton(onClick = { AiOrganizer.stop() }) { Text(L("إيقاف", "Stop"), color = Palette.Danger) }
                if (AiOrganizer.partial.isNotBlank()) AiSummaryText(AiOrganizer.partial)
            }
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // زر الطلب الصريح الوحيد: الذكاء لا يعمل على الملخص إلا بالضغط عليه (أو بتفعيل المحفزات التلقائية من الإعدادات)
                    val mode = AiOrganizer.autoMode(s)
                    if (mode == "full") {
                        Button(
                            enabled = notes.isNotBlank() && !busyElsewhere && AiPageReader.runningKey == null,
                            onClick = { AiOrganizer.start(s, notes) },
                            colors = ButtonDefaults.buttonColors(containerColor = Palette.Navy)
                        ) { Text(if (saved == null) L("رتّب الملخص", "Organize summary") else L("أعد ترتيب الملخص", "Re-organize summary")) }
                    }
                    if (saved != null) {
                        TextButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("MyNotes Pro", saved))
                            c.messages.tryEmit(L("تم النسخ", "Copied"))
                        }) { Text(L("نسخ", "Copy")) }
                        TextButton(onClick = { editing = true }) { Text(L("تعديل", "Edit")) }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { aiSettings = true }) { Text(L("⚙ إعدادات", "⚙ Settings"), color = Palette.GoldDeep) }
                }
                // إكمال الملخص بما ظُلّل بعد آخر ترتيب
                val pending = AiOrganizer.pendingCount[s.id] ?: 0
                if (saved != null && AiOrganizer.autoMode(s) == "continue" && pending > 0) {
                    Spacer(Modifier.height(6.dp))
                    Button(
                        enabled = !busyElsewhere && AiPageReader.runningKey == null,
                        onClick = { AiOrganizer.continueSummary(s) },
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.GoldDeep)
                    ) { Text(L("✦ أكمل الملخص ($pending تظليل جديد)", "✦ Continue summary ($pending new highlights)")) }
                }
                if (notes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (Ai.visionOk) L("عند الضغط تُقرأ صفحات التظليل بصورتها، ويُستعمل نص باقي الصفحات سياقًا فقط.",
                            "On press, highlighted pages are read from their image; other pages are used as context only.")
                        else L("قراءة الصور غير مفعّلة: سيُرتَّب الملخص من نص القراءة الضوئية.", "Image reading is off: the summary will use the OCR text."),
                        fontSize = 11.sp, color = Palette.TextSecondary
                    )
                }
                // ✦ خريطة ذهنية (زر الجداول المنفصلة أُزيل: الجداول تُكتب داخل الملخص نفسه)
                if (saved != null || s.mindNotes.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { mindMap = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.GoldDeep)
                    ) { Text(L("✦ خريطة ذهنية", "✦ Mind map")) }
                }
                // ✦ تعديل الملخص بتعليمات (على الجهاز)
                if (saved != null) {
                    var instr by remember { mutableStateOf("") }
                    Spacer(Modifier.height(8.dp))
                    Text(L("✦ عدّل الملخص بالذكاء", "✦ Edit the summary with AI"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.Navy)
                    OutlinedTextField(
                        value = instr, onValueChange = { instr = it },
                        placeholder = { Text(L("مثال: اختصره إلى نقاط، اجعل المقارنة في جدول، أضف عنوانًا لكل مجموعة", "e.g. shorten into bullets, put the comparison in a table, add a heading per group"), fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            enabled = instr.isNotBlank() && !busyElsewhere && AiPageReader.runningKey == null,
                            onClick = { AiOrganizer.editWithInstruction(s, instr.trim()); instr = "" },
                            colors = ButtonDefaults.buttonColors(containerColor = Palette.Navy)
                        ) { Text(L("تطبيق التعديل", "Apply edit")) }
                        Spacer(Modifier.width(8.dp))
                        Text(L("يعمل بـ: ", "Using: ") + Ai.selected.title(), fontSize = 12.sp, color = Palette.TextSecondary)
                    }
                }
                if (busyElsewhere) {
                    Text(L("يعمل الذكاء الاصطناعي على مستند آخر الآن", "AI is working on another document now"), fontSize = 12.sp, color = Palette.TextSecondary)
                }
                if (notes.isBlank()) {
                    Text(L("لا توجد ملخصات في هذا المستند بعد", "No summaries in this document yet"), fontSize = 13.sp, color = Palette.TextSecondary)
                }
                Spacer(Modifier.height(12.dp))
                if (saved != null) {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Palette.Ivory)
                            .padding(14.dp)
                    ) { AiSummaryText(saved) }
                }
            }
        }
    }
    readPage?.let { p -> AiPageDialog(s, p) { readPage = null } }
    if (mindMap && (saved != null || s.mindNotes.isNotEmpty())) MindMapDialog(s.id, s.title, saved ?: "", s.mindNotes) { mindMap = false }
    if (aiSettings) {
        AlertDialog(
            onDismissRequest = { aiSettings = false },
            title = { Text(L("⚙ إعدادات الذكاء الاصطناعي", "⚙ AI settings")) },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    AiToggle(
                        L("كتابة الملخص وإكماله تلقائيًا بعد التظليل", "Write and continue the summary automatically after highlighting"),
                        AiOrganizer.autoContinue
                    ) { AiOrganizer.toggleAutoContinue(!AiOrganizer.autoContinue) }
                    AiToggle(
                        L("مراجعة ذاتية: الذكاء يراجع ما كتبه ويصححه", "Self-review: the AI checks and fixes what it wrote"),
                        AiOrganizer.selfReview
                    ) { AiOrganizer.toggleSelfReview(!AiOrganizer.selfReview) }
                    AiToggle(
                        L("قراءة المستند كاملًا بالذكاء في الخلفية (مجاني على الجهاز، للملخص والشرح)", "Read the whole document with AI in the background (free, on device; for summaries and explanations)"),
                        AiDocReader.enabled
                    ) { AiDocReader.toggle(!AiDocReader.enabled); if (AiDocReader.enabled) AiDocReader.enqueue(s.id) }
                    AiDocReader.progress[s.id]?.let { (r, n) ->
                        Text(
                            if (r >= n) L("قُرئ المستند كاملًا ✓ ($n صفحة)", "Whole document read ✓ ($n pages)")
                            else L("قُرئت $r من $n صفحة" + if (AiDocReader.currentDoc == s.id) " — جارٍ القراءة…" else "", "$r of $n pages read" + if (AiDocReader.currentDoc == s.id) " — reading…" else ""),
                            fontSize = 12.sp, color = Palette.TextSecondary
                        )
                    }
                    AiToggle(
                        L("قراءة صفحات التظليل بالذكاء قبل الكتابة (عناوين أدق، أبطأ أول مرة)", "Read highlighted pages with AI before writing (better headings, slower the first time)"),
                        AiOrganizer.autoReadPages
                    ) { AiOrganizer.toggleAutoReadPages(!AiOrganizer.autoReadPages) }
                    AiToggle(
                        L("بطاقات مراجعة تلقائية (تظهر في «بطاقات»)", "Automatic review cards (shown in “Cards”)"),
                        AiOrganizer.autoCards
                    ) { AiOrganizer.toggleAutoCards(!AiOrganizer.autoCards) }
                }
            },
            confirmButton = { TextButton(onClick = { aiSettings = false }) { Text(L("تم", "Done")) } }
        )
    }
    if (tablePick && saved != null) {
        val secs = AiOrganizer.tableSections(saved)
        AlertDialog(
            onDismissRequest = { tablePick = false },
            title = { Text(L("أي قسم تريده جدولًا؟", "Which section should become a table?")) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    secs.forEach { h ->
                        Text(
                            h.trimStart('#', ' '),
                            fontSize = if (h.startsWith("## ")) 14.sp else 15.sp,
                            fontWeight = if (h.startsWith("## ")) FontWeight.Normal else FontWeight.Bold,
                            color = Palette.Navy,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .clickable { tablePick = false; AiOrganizer.tableFromSection(s, h) }
                                .padding(start = if (h.startsWith("## ")) 18.dp else 4.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { tablePick = false }) { Text(L("إلغاء", "Cancel")) } }
        )
    }
    if (editing && saved != null) {
        var t by remember { mutableStateOf(saved) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(L("تعديل الملخص المرتّب", "Edit organized summary")) },
            text = {
                OutlinedTextField(
                    value = t, onValueChange = { t = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 520.dp)
                )
            },
            confirmButton = { TextButton(onClick = { AiOrganizer.save(s.id, t); editing = false }) { Text(L("حفظ", "Save")) } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(L("إلغاء", "Cancel")) } }
        )
    }
}

@Composable
fun AiToggle(label: String, on: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onToggle() }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = Palette.TextPrimary, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(checked = on, onCheckedChange = { onToggle() })
    }
}

/** نافذة «اشرح» لنص محدد: تُكتب الإجابة تدريجيًا. */
@Composable
fun AiExplainDialog(text: String, context: String = "", onDismiss: () -> Unit) {
    var answer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    DisposableEffect(text) {
        val j = AppController.scope.launch {
            try {
                if (!Ai.ready) {
                    error = L("نزّل نموذج الذكاء الاصطناعي أولًا من الإعدادات", "Download the AI model first from Settings")
                    return@launch
                }
                AiExplain.running = true
                // سياق من قراءة المستند (إن قُرئت الصفحة) ليكون الشرح مرتبطًا بالمحاضرة نفسها
                val prompt = if (context.isBlank()) text
                else "CONTEXT (from the same lecture page, reference only):\n$context\n\nEXPLAIN THIS TEXT:\n$text"
                val r = Ai.generate(AiOrganizer.explainSystem(), prompt) { answer = AiOrganizer.clean(it) }
                answer = AiOrganizer.clean(r)
                done = true
            } catch (e: CancellationException) {
            } catch (t: Throwable) {
                error = Ai.errorText(t, L("تعذر تشغيل النموذج", "Could not run the model"))
            } finally {
                AiExplain.running = false
            }
        }
        onDispose { j.cancel() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("شرح", "Explain")) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                Text(text, fontSize = 12.sp, color = Palette.TextSecondary, maxLines = 4)
                Spacer(Modifier.height(10.dp))
                when {
                    error != null -> Text(error!!, fontSize = 14.sp)
                    answer.isBlank() -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(Ai.engineStatus.ifEmpty { L("يفكر…", "Thinking…") }, fontSize = 14.sp)
                    }
                    else -> AiSummaryText(answer)
                }
                if (done) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        L("شرح من نموذج صغير على جهازك؛ قد يخطئ، فراجعه مع مصدرك.", "Explained by a small on-device model; it can be wrong, so check your source."),
                        fontSize = 11.sp, color = Palette.TextSecondary
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(L("إغلاق", "Close")) } }
    )
}

/** نافذة معالجة تمنع استعمال التطبيق أثناء ترتيب الملخص المطلوب صراحةً (التشغيل التلقائي لا يحجب). تحوي إلغاءً. */
@Composable
fun AiBlockingDialog() {
    if (!AiOrganizer.blocking || AiOrganizer.runningDoc == null) return
    AlertDialog(
        onDismissRequest = {},
        properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(L("جارٍ ترتيب الملخص", "Organizing the summary")) },
        text = {
            Column {
                Text(AiOrganizer.stepText.ifEmpty { Ai.engineStatus }, fontSize = 14.sp, color = Palette.Navy, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { AiOrganizer.stepFraction }, modifier = Modifier.fillMaxWidth(), color = Palette.Gold)
                Spacer(Modifier.height(8.dp))
                Text(
                    L("انتظر حتى ينتهي الذكاء الاصطناعي حتى لا تتأثر دقة الملخص. يمكنك الإلغاء.",
                        "Please wait until the AI finishes so the summary stays accurate. You can cancel."),
                    fontSize = 12.sp, color = Palette.TextSecondary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { AiOrganizer.cancelRunning() }) { Text(L("إلغاء", "Cancel"), color = Palette.Danger) }
        }
    )
}
