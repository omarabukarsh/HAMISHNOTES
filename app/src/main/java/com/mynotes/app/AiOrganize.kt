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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ترتيب الملخص بالذكاء الاصطناعي. النتيجة تُحفظ منفصلة لكل مستند، والملخص الأصلي لا يتغير أبدًا.
 */
object AiOrganizer {
    private const val SYSTEM = """You organize study notes for a pharmacy student.
Reorganize the notes you receive into a clear, well-structured study summary.
Strict rules:
- Use ONLY information found in the notes. Never add facts, numbers, doses or explanations that are not in the notes.
- Do not drop any information from the notes.
- Keep every note in its original language (Arabic stays Arabic, English stays English). Keep drug names and medical terms exactly as written.
- Group related points under short headings. Merge repeated points.
- When several items share the same attributes (for example drug, class, mechanism, side effects), present them as a table.
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
        AiPageReader.result(s.id, page) ?: s.ocr[page]?.words?.let { AiPageReader.plainText(it) } ?: ""

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
                        rows.add(lines[i].trim().split(" | ").map { it.trim() })
                        i++
                    }
                    i--
                    out.add(STable(rows, emptyList(), "ai$start", header = true))
                }
                l.startsWith("- ") -> out.add(SPara("•  " + l.removePrefix("- ").trim(), emptyList(), emptyList(), "ai$i"))
                else -> out.add(SPara(l, emptyList(), emptyList(), "ai$i"))
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
    fun clean(raw: String): String {
        val lines = raw.replace("```", "").lines().map { it.trimEnd() }
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
    var autoContinue by mutableStateOf(prefs().getBoolean("auto_continue", true))
        private set
    fun setAutoContinue(on: Boolean) { autoContinue = on; prefs().edit().putBoolean("auto_continue", on).apply() }

    /** بطاقات مراجعة تلقائية بالذكاء الاصطناعي من النصوص المظللة. */
    var autoCards by mutableStateOf(prefs().getBoolean("auto_cards", true))
        private set
    fun setAutoCards(on: Boolean) { autoCards = on; prefs().edit().putBoolean("auto_cards", on).apply() }

    private fun prefs() = AppController.appContext.getSharedPreferences("mynotes_ai", Context.MODE_PRIVATE)

    private const val CARDS_SYSTEM = """You write study flashcards for a pharmacy student from highlighted notes.
Strict rules:
- Use ONLY information found in the notes. Never add facts, numbers or doses that are not in the notes.
- One card per important fact: a clear question and a short answer.
- Write each card in the language of its note. Keep drug names and medical terms exactly as written.
- At most 8 cards. Skip trivial facts.
Output format (plain text only, nothing else):
Q: question
A: answer
(blank line between cards)"""

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
        for (raw0 in raw.replace("**", "").lines()) {
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
                while (match.second.isNotEmpty() && match.second.last().isBlank()) match.second.removeAt(match.second.lastIndex)
                if (content.isNotEmpty()) match.second.add(content)
                match.second.add("")
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

    private fun organize(s: DocSession, notes: String, append: Boolean, marks: List<TextMark>, quiet: Boolean = false) {
        if (job?.isActive == true || AiPageReader.runningKey != null) return
        if (!Ai.ready) return
        val docId = s.id
        // ذاكرة النموذج 4096 رمزًا: العربية تأخذ رموزًا أكثر، فأجزاء أصغر.
        // جزء من الذاكرة لنص صفحات المستند (المصدر)، فالملاحظات في كل جزء أقل قليلًا.
        val arabic = notes.count { it in '؀'..'ۿ' } > notes.length / 4
        val parts = chunks(notes, if (arabic) 1300 else 2400)
        val sourceBudget = if (arabic) 1300 else 2400
        if (parts.isEmpty()) return
        // ما رُتّب: نص كل تمييز كما هو الآن (للتمييز الذي أضيف إليه نص: نصه الكامل)
        val snapshot = s.allMarks().filter { m -> marks.any { it.id == m.id } }.associate { it.id to it.text }
        val before = if (append) result(docId) ?: "" else ""
        runningDoc = docId
        partial = ""
        stepFraction = 0f
        job = AppController.scope.launch {
            val done = StringBuilder()
            val cards = ArrayList<Pair<String, String>>()
            fun shown() = if (append) merge(before, done.toString()) else done.toString()
            try {
                // نص صفحات المستند كاملًا مصدرًا: تُقرأ الصفحات التي فيها تمييزات إن لم تكن مقروءة
                stepText = L("جارٍ قراءة صفحات المستند…", "Reading the document pages…")
                marks.map { it.page }.distinct().forEach { p -> if (s.ocr[p] == null) s.ocrPage(p) }
                val pages = (0 until (s.pdf?.count ?: 0)).associateWith { pageSource(s, it) }.filterValues { it.isNotBlank() }
                val heads = if (append) before.lines().filter { it.trim().startsWith("# ") }.joinToString("\n").take(900) else ""
                parts.forEachIndexed { i, part ->
                    stepText = when {
                        append -> L("جارٍ إكمال الملخص بالتظليل الجديد…", "Adding new highlights to the summary…")
                        parts.size == 1 -> L("جارٍ الترتيب…", "Organizing…")
                        else -> L("جارٍ ترتيب الجزء ${i + 1} من ${parts.size}…", "Organizing part ${i + 1} of ${parts.size}…")
                    }
                    stepFraction = i / parts.size.toFloat()
                    val src = sourceFor(part, pages, sourceBudget)
                    val sb = StringBuilder()
                    if (src.isNotBlank()) sb.append("SOURCE (document text, reference only):\n\n").append(src).append("\n\n")
                    if (heads.isNotBlank()) {
                        sb.append("EXISTING HEADINGS of the summary (when a note belongs under one of them, use that heading exactly):\n")
                            .append(heads).append("\n\n")
                    }
                    sb.append(if (sb.isEmpty()) "Notes:\n\n" else "NOTES (organize these):\n\n").append(part)
                    val res = Ai.generate(SYSTEM, sb.toString()) { p ->
                        partial = (if (append) merge(before, (done.toString() + "\n\n" + clean(p)).trim())
                        else (done.toString() + "\n\n" + clean(p)).trim())
                    }
                    if (done.isNotEmpty()) done.append("\n\n")
                    done.append(clean(res))
                    partial = shown()
                    if (autoCards) {
                        stepText = L("جارٍ إعداد بطاقات المراجعة…", "Making review cards…")
                        try {
                            cards.addAll(parseCards(Ai.generate(CARDS_SYSTEM, "Notes:\n\n$part")))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                        }
                    }
                }
                save(docId, shown())
                setProcessed(docId, if (append) processed(docId) + snapshot else snapshot)
                if (autoCards) s.putAiCards(cards, replace = !append)
                refreshPending(s)
                run {
                    AppController.messages.tryEmit(
                        when {
                            append && cards.isNotEmpty() -> L("أُكمل الملخص وأُضيفت ${cards.size} بطاقة مراجعة", "Summary updated, ${cards.size} review cards added")
                            append -> L("أُكمل الملخص بالتظليل الجديد", "Summary updated with new highlights")
                            cards.isNotEmpty() -> L("اكتمل ترتيب الملخص و${cards.size} بطاقة مراجعة. راجعها قبل الاعتماد عليها", "Summary organized with ${cards.size} review cards. Review before relying on them")
                            else -> L("اكتمل ترتيب الملخص. راجعه قبل الاعتماد عليه", "Summary organized. Review it before relying on it")
                        }
                    )
                }
            } catch (e: CancellationException) {
                // ترتيب كامل لم يكتمل: يُحفظ ما كُتب، ولا يُعرف ما رُتّب فيُطفأ الإكمال حتى يُعاد الترتيب
                if (!append && done.isNotEmpty()) {
                    save(docId, done.toString())
                    setProcessed(docId, emptyMap())
                }
            } catch (t: Throwable) {
                if (!quiet) AppController.messages.tryEmit(
                    L("تعذر تشغيل النموذج. جرّب النموذج «الخفيف» من الإعدادات، أو أغلق تطبيقات أخرى لتحرير الذاكرة.",
                        "Could not run the model. Try the “Light” model in Settings, or close other apps to free memory.")
                )
            } finally {
                runningDoc = null
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
        "You are a helpful pharmacy tutor. Explain the given text simply and briefly (max 8 short lines). Keep medical terms. Do not invent doses."
    else
        "You are a helpful pharmacy tutor. Explain the given text simply and briefly in Arabic (max 8 short lines). Keep English drug names and medical terms as they are. Do not invent doses."
}

/** يعرض الملخص المرتّب: عناوين، نقاط، فقرات، وجداول. */
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
                        rows.add(lines[i].split(" | ").map { it.trim() })
                        i++
                    }
                    i--
                    AiTable(rows)
                }
                l.startsWith("- ") -> Row {
                    Text("•  ", fontSize = 14.sp, color = Palette.GoldDeep)
                    Text(l.removePrefix("- "), fontSize = 14.sp, lineHeight = 21.sp, color = Palette.TextPrimary)
                }
                else -> Text(l, fontSize = 14.sp, lineHeight = 21.sp, color = Palette.TextPrimary)
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
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // قراءة منظمة للصفحة الحالية (عناوين، جداول، نقاط) — تُستعمل أيضًا مصدرًا للترتيب
        val cur = s.listState.firstVisibleItemIndex
        val pageDone = AiPageReader.result(s.id, cur) != null
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, Palette.Gold, RoundedCornerShape(10.dp))
                .clickable { readPage = cur }.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    L("✦ قراءة الصفحة ${cur + 1} بالذكاء", "✦ Read page ${cur + 1} with AI"),
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.Navy
                )
                Text(
                    if (pageDone) L("مقروءة ومنظمة — اضغط للعرض", "Read and structured — tap to view")
                    else L("يتعرّف على العناوين والجداول والنقاط في الصفحة", "Recognizes headings, tables and bullets on the page"),
                    fontSize = 12.sp, color = Palette.TextSecondary
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        // تنبيه دائم: اقتراح يُراجع
        Text(
            L("ترتيب مقترح من نموذج يعمل داخل جهازك دون إنترنت. ملخصك الأصلي لا يتغير. راجع المعلومات قبل الاعتماد عليها.",
                "Suggested by a model running on your device, offline. Your original summary is unchanged. Review the information before relying on it."),
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
                    Button(
                        enabled = notes.isNotBlank() && !busyElsewhere && AiPageReader.runningKey == null,
                        onClick = { AiOrganizer.start(s, notes) },
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.Navy)
                    ) { Text(if (saved == null) L("رتّب الملخص", "Organize summary") else L("أعد الترتيب", "Organize again")) }
                    if (saved != null) {
                        TextButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("MyNotes Pro", saved))
                            c.messages.tryEmit(L("تم النسخ", "Copied"))
                        }) { Text(L("نسخ", "Copy")) }
                        TextButton(onClick = { editing = true }) { Text(L("تعديل", "Edit")) }
                        TextButton(onClick = { AiOrganizer.save(s.id, "") }) { Text(L("حذف", "Delete"), color = Palette.Danger) }
                    }
                }
                // إكمال الملخص بما ظُلّل بعد آخر ترتيب
                val pending = AiOrganizer.pendingCount[s.id] ?: 0
                if (saved != null && AiOrganizer.canContinue(s.id) && pending > 0) {
                    Spacer(Modifier.height(6.dp))
                    Button(
                        enabled = !busyElsewhere && AiPageReader.runningKey == null,
                        onClick = { AiOrganizer.continueSummary(s) },
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.GoldDeep)
                    ) { Text(L("✦ أكمل الملخص ($pending تظليل جديد)", "✦ Continue summary ($pending new highlights)")) }
                } else if (saved != null && !AiOrganizer.canContinue(s.id)) {
                    Text(
                        L("لتفعيل الإكمال بعد التظليل الجديد اضغط «أعد الترتيب» مرة واحدة.", "To continue after new highlights, tap “Organize again” once."),
                        fontSize = 12.sp, color = Palette.TextSecondary
                    )
                }
                Spacer(Modifier.height(6.dp))
                AiToggle(
                    L("إكمال تلقائي بعد التظليل الجديد", "Continue automatically after new highlights"),
                    AiOrganizer.autoContinue
                ) { AiOrganizer.setAutoContinue(!AiOrganizer.autoContinue) }
                AiToggle(
                    L("بطاقات مراجعة تلقائية بالذكاء الاصطناعي (تظهر في «بطاقات»)", "Automatic AI review cards (shown in “Cards”)"),
                    AiOrganizer.autoCards
                ) { AiOrganizer.setAutoCards(!AiOrganizer.autoCards) }
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
private fun AiToggle(label: String, on: Boolean, onToggle: () -> Unit) {
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
fun AiExplainDialog(text: String, onDismiss: () -> Unit) {
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
                val r = Ai.generate(AiOrganizer.explainSystem(), text) { answer = AiOrganizer.clean(it) }
                answer = AiOrganizer.clean(r)
                done = true
            } catch (e: CancellationException) {
            } catch (t: Throwable) {
                error = L("تعذر تشغيل النموذج", "Could not run the model")
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
