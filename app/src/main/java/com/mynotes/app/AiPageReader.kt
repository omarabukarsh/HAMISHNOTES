package com.mynotes.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt

/**
 * MyNotes Pro: قراءة منظمة للصفحة بالذكاء الاصطناعي.
 * القراءة الضوئية (ML Kit أو نص الملف) تعطي الكلمات وأماكنها، والنموذج يتعرّف منها على
 * العناوين والجداول والنقاط التي تتبع بعضها. إن كان النموذج يقرأ الصور (Gemma 4) تُرسل صورة الصفحة أيضًا.
 * النتيجة تُحفظ لكل صفحة، وتُستعمل مصدرًا عند ترتيب الملخصات.
 */
object AiPageReader {
    private const val SYSTEM = """You read one page of a study document and rewrite its text as clean, structured notes.
Input: the OCR text of the page, one row per line. Each row starts with layout hints in brackets: s = text size relative to normal body text (1.0 = normal, bigger = title), x = indent from the left edge in percent. " || " marks a wide horizontal gap between words (separate columns or table cells).
An image of the page may also be given: then read the page image itself and use the OCR text to check spelling.
Strict rules:
- Keep ALL the text of the page. Do not add, explain, summarize or translate anything.
- Keep every part in its original language. Keep drug names and medical terms exactly as written. Fix only obvious OCR errors (broken or merged words).
- Rebuild the structure: bigger or short standalone lines are headings; numbered or bulleted items and their sub-points stay together under their heading; rows with aligned columns are tables.
Output format (plain text only, no other markdown, no introduction, no closing remarks):
# Heading
## Subheading
- bullet point (keep its own number if it has one)
Normal paragraphs as plain lines.
Tables: one row per line, cells separated by " | ", first row is the header."""

    /** القراءة الجارية (واحدة فقط في كل مرة): مفتاح المستند والصفحة. */
    var runningKey by mutableStateOf<String?>(null)
        private set
    var stepText by mutableStateOf("")
        private set
    var partial by mutableStateOf("")
        private set
    private var job: Job? = null

    private val results = mutableStateMapOf<String, String>()

    fun key(docId: String, page: Int) = "${docId}_p$page"

    private fun file(k: String) =
        File(File(AppController.appContext.filesDir, "ai/pages").also { it.mkdirs() }, "$k.txt")

    fun result(docId: String, page: Int): String? {
        val k = key(docId, page)
        if (!results.containsKey(k)) {
            val f = file(k)
            results[k] = if (f.exists()) f.readText() else ""
        }
        return results[k]?.takeIf { it.isNotBlank() }
    }

    fun save(docId: String, page: Int, text: String) {
        val k = key(docId, page)
        results[k] = text
        AppController.scope.launch(Dispatchers.IO) {
            val f = file(k)
            if (text.isBlank()) f.delete() else f.writeText(text)
        }
    }

    private fun isArabic(s: String) = s.count { it in '؀'..'ۿ' } > s.count { it.isLetter() } / 3

    private fun median(v: List<Float>): Float = if (v.isEmpty()) 0f else v.sorted()[v.size / 2]

    /** أسطر الصفحة بترتيب القراءة الضوئية (يحافظ على ترتيب الأعمدة). */
    private fun lines(words: List<OcrWord>): List<List<OcrWord>> {
        val m = LinkedHashMap<Int, MutableList<OcrWord>>()
        words.forEach { m.getOrPut(it.line) { ArrayList() }.add(it) }
        return m.values.toList()
    }

    /** نص الصفحة مع تلميحات الشكل: حجم الخط، الإزاحة، والفراغات الواسعة بين الأعمدة. */
    fun layoutText(words: List<OcrWord>): String {
        if (words.isEmpty()) return ""
        val ls = lines(words)
        val normal = median(ls.map { l -> median(l.map { it.b - it.t }) }).coerceAtLeast(1e-4f)
        val minL = words.minOf { it.l }
        return ls.joinToString("\n") { l ->
            val arabic = isArabic(l.joinToString(" ") { it.text })
            val sorted = if (arabic) l.sortedByDescending { it.r } else l.sortedBy { it.l }
            val h = median(l.map { it.b - it.t }).coerceAtLeast(1e-4f)
            val sb = StringBuilder()
            sorted.forEachIndexed { i, w ->
                if (i > 0) {
                    val prev = sorted[i - 1]
                    val gap = if (arabic) prev.l - w.r else w.l - prev.r
                    sb.append(if (gap > h * 1.6f) " || " else " ")
                }
                sb.append(w.text)
            }
            val s = (h / normal * 10f).roundToInt() / 10f
            val x = ((l.minOf { it.l } - minL) * 100f).roundToInt()
            "[s=$s x=$x] $sb"
        }
    }

    /** نص الصفحة العادي (بلا تلميحات). */
    fun plainText(words: List<OcrWord>): String =
        lines(words).joinToString("\n") { l -> l.joinToString(" ") { it.text } }

    /** يقسّم النص عند حدود الأسطر. */
    private fun split(text: String, max: Int): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        for (line in text.lines()) {
            if (cur.isNotEmpty() && cur.length + line.length + 1 > max) {
                out.add(cur.toString())
                cur.clear()
            }
            if (cur.isNotEmpty()) cur.append('\n')
            cur.append(line.take(max))
        }
        if (cur.isNotBlank()) out.add(cur.toString())
        return out
    }

    /** صورة الصفحة مع كتابة المستخدم (بدون الهايلايتر)، مضغوطة لإرسالها للنموذج. */
    private suspend fun pageImage(s: DocSession, page: Int): ByteArray? = withContext(Dispatchers.Default) {
        try {
            val doc = s.pdf ?: return@withContext null
            val ref = doc.pages.getOrNull(page) ?: return@withContext null
            val base = doc.renderFresh(ref, 1024) ?: return@withContext null
            val img = OcrEngine.compose(base, s.annotations[page] ?: emptyList())
            try {
                ByteArrayOutputStream().also { img.compress(Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray()
            } finally {
                if (img !== base) img.recycle()
                base.recycle()
            }
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * قراءة صفحة وحفظ النتيجة (القلب المشترك بين زر «قراءة الصفحة» والقراءة التلقائية قبل الترتيب).
     * useImage = false: من النص المقروء فقط (أسرع)، وتُستعمل الصورة فقط إن لم يُقرأ نص من الصفحة.
     * يعيد النص المنظّم، أو null إن لم يكن في الصفحة نص.
     */
    suspend fun read(
        s: DocSession,
        page: Int,
        useImage: Boolean = true,
        local: Boolean = false,
        onStep: (String) -> Unit = {},
        onPartial: (String) -> Unit = {}
    ): String? {
        val docId = s.id
        val done = StringBuilder()
        try {
            onStep(L("جارٍ قراءة الصفحة ${page + 1}…", "Reading page ${page + 1}…"))
            val words = s.ocrPage(page)?.words ?: emptyList()
            val text = layoutText(words)
            val see = if (local) Ai.installed(Ai.selected) && Ai.selected.vision else Ai.canSee
            val img = if (see && (useImage || text.isBlank())) pageImage(s, page) else null
            if (text.isBlank() && img == null) return null
            val arabic = isArabic(text)
            val parts = if (img != null) listOf(text.take(if (local) (if (arabic) 1500 else 2600) else Ai.budget(if (arabic) 1500 else 2600)))
            else split(text, if (local) (if (arabic) 2000 else 3600) else Ai.budget(if (arabic) 2000 else 3600))
            parts.forEachIndexed { i, part ->
                onStep(
                    if (parts.size == 1) L("جارٍ تنظيم الصفحة ${page + 1}…", "Structuring page ${page + 1}…")
                    else L("جارٍ تنظيم الصفحة ${page + 1} (الجزء ${i + 1} من ${parts.size})…", "Structuring page ${page + 1} (part ${i + 1} of ${parts.size})…")
                )
                val prompt = when {
                    img != null && part.isBlank() -> "Read the attached page image."
                    img != null -> "The page image is attached. OCR text of the page (hints, may be incomplete or wrong):\n\n$part"
                    else -> "OCR text of the page:\n\n$part"
                }
                val res = Ai.generate(SYSTEM, prompt, if (i == 0) img else null, local) { p ->
                    onPartial((done.toString() + "\n\n" + AiOrganizer.clean(p)).trim())
                }
                if (done.isNotEmpty()) done.append("\n\n")
                done.append(AiOrganizer.clean(res))
                onPartial(done.toString())
            }
            save(docId, page, done.toString())
            return done.toString()
        } catch (e: CancellationException) {
            if (done.isNotEmpty()) save(docId, page, done.toString())
            throw e
        }
    }

    fun start(s: DocSession, page: Int) {
        if (job?.isActive == true || AiOrganizer.runningDoc != null) return
        if (!Ai.ready) {
            AppController.messages.tryEmit(L("نزّل نموذج الذكاء الاصطناعي أولًا من الإعدادات", "Download the AI model first from Settings"))
            return
        }
        runningKey = key(s.id, page)
        partial = ""
        job = AppController.scope.launch {
            try {
                val r = read(s, page, useImage = true, onStep = { stepText = it }, onPartial = { partial = it })
                if (r == null) {
                    AppController.messages.tryEmit(
                        L("لم يُقرأ نص في هذه الصفحة. للصفحات العربية الممسوحة اختر نموذج Gemma 4 من الإعدادات.",
                            "No text was read on this page. For scanned Arabic pages choose the Gemma 4 model in Settings.")
                    )
                } else {
                    AppController.messages.tryEmit(L("اكتملت قراءة الصفحة ${page + 1}. راجعها قبل الاعتماد عليها", "Page ${page + 1} read. Review it before relying on it"))
                }
            } catch (e: CancellationException) {
            } catch (t: Throwable) {
                AppController.messages.tryEmit(
                    Ai.errorText(t, L("تعذر تشغيل النموذج. جرّب نموذجًا آخر من الإعدادات، أو أغلق تطبيقات أخرى لتحرير الذاكرة.",
                        "Could not run the model. Try another model in Settings, or close other apps to free memory."))
                )
            } finally {
                runningKey = null
                stepText = ""
                partial = ""
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    /** يحوّل القراءة المنظمة إلى صيغة نص التمييز في الملخصات (فقرات بسطر فارغ، "# " عنوان، " | " جدول). */
    fun toMarkText(ai: String): String {
        val out = ArrayList<String>()
        val table = ArrayList<String>()
        fun flush() {
            if (table.isNotEmpty()) {
                out.add(table.joinToString("\n"))
                table.clear()
            }
        }
        for (raw in ai.lines()) {
            val l = raw.trim()
            when {
                l.isEmpty() -> flush()
                l.contains(" | ") -> table.add(l)
                l.startsWith("## ") -> { flush(); out.add("# " + l.removePrefix("## ").trim()) }
                l.startsWith("# ") -> { flush(); out.add("# " + l.removePrefix("# ").trim()) }
                l.startsWith("- ") -> { flush(); out.add("• " + l.removePrefix("- ").trim()) }
                else -> { flush(); out.add(l) }
            }
        }
        flush()
        return out.joinToString("\n\n")
    }
}

/** نافذة «✦ قراءة الصفحة»: تقرأ الصفحة وتنظّمها، مع النسخ والإضافة إلى الملخصات. */
@Composable
fun AiPageDialog(s: DocSession, page: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val c = AppController
    val k = AiPageReader.key(s.id, page)
    val running = AiPageReader.runningKey == k
    val busy = (AiPageReader.runningKey != null && !running) || AiOrganizer.runningDoc != null
    val saved = AiPageReader.result(s.id, page)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("✦ قراءة الصفحة ${page + 1}", "✦ Read page ${page + 1}")) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text(
                    if (Ai.canSee) L("النموذج يقرأ صورة الصفحة ويتعرّف على العناوين والجداول والنقاط.", "The model reads the page image and recognizes headings, tables and bullet points.")
                    else L("النموذج ينظّم النص المقروء من الصفحة إلى عناوين وجداول ونقاط. لقراءة صورة الصفحة نفسها (مثل الصفحات العربية الممسوحة) اختر Gemma 4 من الإعدادات.",
                        "The model structures the page’s recognized text into headings, tables and bullets. To read the page image itself (e.g. scanned Arabic pages) choose Gemma 4 in Settings."),
                    fontSize = 12.sp, lineHeight = 18.sp, color = Palette.TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                when {
                    !Ai.ready -> Text(L("نزّل نموذج الذكاء الاصطناعي أولًا من الإعدادات.", "Download the AI model first from Settings."), fontSize = 14.sp, color = Palette.Navy)
                    running -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(AiPageReader.stepText.ifEmpty { Ai.engineStatus }, fontSize = 14.sp, color = Palette.Navy)
                        }
                        if (Ai.engineStatus.isNotEmpty()) Text(Ai.engineStatus, fontSize = 12.sp, color = Palette.TextSecondary)
                        if (AiPageReader.partial.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            AiSummaryText(AiPageReader.partial)
                        }
                    }
                    saved != null -> Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Palette.Ivory).padding(12.dp)
                    ) { AiSummaryText(saved) }
                    busy -> Text(L("الذكاء الاصطناعي مشغول بمهمة أخرى الآن", "AI is busy with another task now"), fontSize = 13.sp, color = Palette.TextSecondary)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                when {
                    running -> TextButton(onClick = { AiPageReader.stop() }) { Text(L("إيقاف", "Stop"), color = Palette.Danger) }
                    Ai.ready && !busy -> TextButton(onClick = { AiPageReader.start(s, page) }) {
                        Text(if (saved == null) L("اقرأ الصفحة", "Read page") else L("أعد القراءة", "Read again"))
                    }
                }
                if (saved != null && !running) {
                    TextButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("MyNotes Pro", saved))
                        c.messages.tryEmit(L("تم النسخ", "Copied"))
                    }) { Text(L("نسخ", "Copy")) }
                    TextButton(onClick = {
                        s.addTextMark(page, AiPageReader.toMarkText(saved))
                        c.messages.tryEmit(L("أُضيفت الصفحة إلى الملخصات", "Page added to summaries"))
                    }) { Text(L("أضف للملخصات", "Add to summaries")) }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("إغلاق", "Close")) } }
    )
}
