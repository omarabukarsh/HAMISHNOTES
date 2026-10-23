package com.mynotes.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
Output format (plain text only, no other markdown, no introduction, no closing remarks):
# Heading
## Subheading
- bullet point
Tables: one row per line, cells separated by " | ", first row is the header."""

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

    fun start(s: DocSession, notes: String) {
        if (job?.isActive == true) return
        val docId = s.id
        // ذاكرة النموذج 4096 رمزًا: العربية تأخذ رموزًا أكثر، فأجزاء أصغر
        val arabic = notes.count { it in '؀'..'ۿ' } > notes.length / 4
        val parts = chunks(notes, if (arabic) 1800 else 3200)
        if (parts.isEmpty()) return
        runningDoc = docId
        partial = ""
        stepFraction = 0f
        job = AppController.scope.launch {
            val done = StringBuilder()
            try {
                parts.forEachIndexed { i, part ->
                    stepText = if (parts.size == 1) L("جارٍ الترتيب…", "Organizing…")
                    else L("جارٍ ترتيب الجزء ${i + 1} من ${parts.size}…", "Organizing part ${i + 1} of ${parts.size}…")
                    stepFraction = i / parts.size.toFloat()
                    val res = Ai.generate(SYSTEM, "Notes:\n\n$part") { p ->
                        partial = (done.toString() + "\n\n" + clean(p)).trim()
                    }
                    if (done.isNotEmpty()) done.append("\n\n")
                    done.append(clean(res))
                    partial = done.toString()
                }
                save(docId, done.toString())
                AppController.messages.tryEmit(L("اكتمل ترتيب الملخص. راجعه قبل الاعتماد عليه", "Summary organized. Review it before relying on it"))
            } catch (e: CancellationException) {
                if (done.isNotEmpty()) save(docId, done.toString())
            } catch (t: Throwable) {
                AppController.messages.tryEmit(
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
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
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
                        enabled = notes.isNotBlank() && !busyElsewhere,
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
