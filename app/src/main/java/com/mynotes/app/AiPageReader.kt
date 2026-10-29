package com.mynotes.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
 * القراءة الضوئية (ML Kit أو نص الملف) تعطي الكلمات وأماكنها وأنماطها (حجم، لون، غليظ، مائل، تسطير)،
 * والنموذج يتعرّف منها على العناوين والجداول والنقاط التي تتبع بعضها.
 * والقراءة البصرية: صورة الصفحة بأطر مرقّمة حول المقاطع المظلَّلة يقرؤها النموذج نفسه (إن كان يقرأ الصور على هذا الجهاز).
 * النتائج تُحفظ، وتُستعمل مصدرًا عند ترتيب الملخصات. مفتاح الحفظ بصمة نص الصفحة (لا رقمها)،
 * فلا تضيع القراءة ولا تنتقل إلى صفحة أخرى عند إدراج صفحات أو حذفها أو إعادة ترتيبها.
 */
object AiPageReader {
    private const val SYSTEM = """You read one page of a study document and rewrite its text as clean, structured notes.
Input: the OCR text of the page, one row per line. Each row starts with layout hints in brackets: s = text size relative to normal body text (1.0 = normal, bigger = title), x = indent from the left edge in percent; b = bold row, i = italic row, u = underlined row, c=name = colored row. Inside a row, *word* is italic, __word__ is underlined and **word** is bold. " || " marks a wide horizontal gap between words (separate columns or table cells). A blank line separates text blocks. Lines like [rule] show a drawn horizontal or vertical line.
An image of the page may also be given: then read the page image itself and use the OCR text to check spelling.
Strict rules:
- Keep ALL the text of the page. Do not add, explain, summarize or translate anything.
- Keep every part in its original language. Keep drug names and medical terms exactly as written. Fix only obvious OCR errors (broken or merged words).
- Rebuild the structure: bigger, bold or colored short standalone lines are headings; numbered or bulleted items and their sub-points stay together under their heading; rows with aligned columns are tables.
Output format (plain text only, no other markdown, no introduction, no closing remarks):
# Heading
## Subheading
- bullet point (keep its own number if it has one)
Normal paragraphs as plain lines.
Tables: one row per line, cells separated by " | ", first row is the header."""

    /** نظام القراءة البصرية: صورة صفحة بأطر مرقّمة حول المقاطع المظلَّلة. */
    private const val VISUAL_SYSTEM = """You look at ONE page image of a study document (any subject, Arabic or English). Some regions are marked with numbered colored frames: 1, 2, 3...
For each frame, in order, write what is shown INSIDE the frame exactly as it appears in the image, then the context needed to read it correctly.
Rules:
- Copy the text of each frame faithfully from the image. Keep numbers, units, symbols, Greek letters, formulas, abbreviations, negations and emojis exactly. If something is unreadable write [unreadable] instead of guessing.
- Text around the frame is context only: do not copy it into the frame's text.
- If a frame is inside a table, give the column headers and the row label of its cells.
- If a frame belongs to a diagram or flowchart, say what it is connected to with arrows (for example A -> B) and which branch it belongs to.
- Give the nearest heading and sub-heading above the frame.
- Never summarize, explain or add anything that is not on the page.
Output one block per frame, exactly in this format:
[N] heading: ... | sub: ...
text: ...
context: ... (table headers, diagram relations, or none)"""

    private const val VISION_W = 1280
    private const val FRAMES_PER_IMAGE = 8
    private val FRAME_COLORS = intArrayOf(0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFF43A047.toInt(), 0xFF8E24AA.toInt(), 0xFFFB8C00.toInt(), 0xFF00897B.toInt())
    private val FRAME_NAMES = arrayOf("red", "blue", "green", "purple", "orange", "teal")

    /** القراءة الجارية من الواجهة (زر «قراءة الصفحة»): مفتاح المستند والصفحة. */
    var runningKey by mutableStateOf<String?>(null)
        private set
    var stepText by mutableStateOf("")
        private set
    var partial by mutableStateOf("")
        private set
    private var job: Job? = null

    private val results = mutableStateMapOf<String, String>()
    /** قراءات جارية الآن (بصمات النص): تمنع قراءة الصفحة نفسها مرتين في وقت واحد. */
    private val inflight = HashSet<String>()

    private val pagesDir get() = File(File(AppController.appContext.filesDir, "ai/pages").also { it.mkdirs() }, "")
    private val visDir get() = File(AppController.appContext.filesDir, "ai/vis").also { it.mkdirs() }

    private val cleaned by lazy {
        // الملفات القديمة كانت تُحفظ برقم الصفحة (وقد تحوي نصًا مخترعًا لصفحات بلا نص)؛ تُحذف مرة واحدة
        AppController.scope.launch(Dispatchers.IO) {
            try {
                pagesDir.listFiles()?.forEach { f -> if (Regex(".*_p\\d+\\.txt").matches(f.name)) f.delete() }
            } catch (t: Throwable) {
            }
        }
        true
    }

    /** بصمة نص الصفحة كما يراه النموذج. */
    private fun keyOf(docId: String, layout: String) = "${docId}_h${"%08x".format(layout.hashCode())}_${layout.length}"

    /** مفتاح قراءة صفحة، أو null إن لم تُقرأ ضوئيًا بعد. */
    fun key(s: DocSession, page: Int): String? {
        val words = s.ocr[page]?.words ?: return null
        return keyOf(s.id, layoutText(words))
    }

    private fun file(k: String) = File(pagesDir, "$k.txt")

    fun result(s: DocSession, page: Int): String? {
        @Suppress("UNUSED_VARIABLE") val c = cleaned
        val k = key(s, page) ?: return null
        return resultByKey(k)
    }

    private fun resultByKey(k: String): String? {
        if (!results.containsKey(k)) {
            val f = file(k)
            results[k] = if (f.exists()) f.readText() else ""
        }
        return results[k]?.takeIf { it.isNotBlank() }
    }

    private fun saveByKey(k: String, text: String) {
        results[k] = text
        AppController.scope.launch(Dispatchers.IO) {
            val f = file(k)
            if (text.isBlank()) f.delete() else f.writeText(text)
        }
    }

    fun save(s: DocSession, page: Int, text: String) {
        val k = key(s, page) ?: return
        saveByKey(k, text)
    }

    private fun isArabic(s: String) = s.count { it in '؀'..'ۿ' } > s.count { it.isLetter() } / 3

    private fun median(v: List<Float>): Float = if (v.isEmpty()) 0f else v.sorted()[v.size / 2]

    /** أسطر الصفحة بترتيب القراءة الضوئية (يحافظ على ترتيب الأعمدة). */
    private fun lines(words: List<OcrWord>): List<List<OcrWord>> {
        val m = LinkedHashMap<Int, MutableList<OcrWord>>()
        words.forEach { m.getOrPut(it.line) { ArrayList() }.add(it) }
        return m.values.toList()
    }

    /** نص الصفحة مع تلميحات الشكل: حجم الخط، الإزاحة، الغليظ والمائل والتسطير واللون، والفراغات الواسعة، وفواصل الكتل، والخطوط المرسومة. */
    fun layoutText(words: List<OcrWord>, shapes: List<OcrShape> = emptyList()): String {
        if (words.isEmpty()) return ""
        val ls = lines(words)
        val normal = median(ls.map { l -> median(l.map { it.b - it.t }) }).coerceAtLeast(1e-4f)
        val minL = words.minOf { it.l }
        val sb = StringBuilder()
        var lastBlock = ls.first().first().block
        ls.forEach { l ->
            val arabic = isArabic(l.joinToString(" ") { it.text })
            val sorted = if (arabic) l.sortedByDescending { it.r } else l.sortedBy { it.l }
            val h = median(l.map { it.b - it.t }).coerceAtLeast(1e-4f)
            val n = l.size
            val rowBold = l.count { it.bold } * 10 >= n * 7
            val rowItalic = l.count { it.italic } * 10 >= n * 7
            val rowUnder = l.count { it.underline } * 10 >= n * 7
            val colorNames = l.mapNotNull { OcrStyle.colorName(it.color) }
            val rowColor = colorNames.groupingBy { it }.eachCount().maxByOrNull { it.value }?.takeIf { it.value * 10 >= n * 6 }?.key
            val line = StringBuilder()
            sorted.forEachIndexed { i, w ->
                if (i > 0) {
                    val prev = sorted[i - 1]
                    val gap = if (arabic) prev.l - w.r else w.l - prev.r
                    line.append(if (gap > h * 1.6f) " || " else " ")
                }
                var t = w.text
                if (w.italic && !rowItalic) t = "*$t*"
                if (w.underline && !rowUnder) t = "__${t}__"
                if (w.bold && !rowBold) t = "**$t**"
                line.append(t)
            }
            val s = (h / normal * 10f).roundToInt() / 10f
            val x = ((l.minOf { it.l } - minL) * 100f).roundToInt()
            val flags = (if (rowBold) " b" else "") + (if (rowItalic) " i" else "") + (if (rowUnder) " u" else "") + (if (rowColor != null) " c=$rowColor" else "")
            val block = l.first().block
            if (sb.isNotEmpty() && block != lastBlock) sb.append('\n')
            lastBlock = block
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append("[s=$s x=$x$flags] ").append(line)
        }
        // خطوط مرسومة طويلة (حدود جداول وفواصل) تُذكر للنموذج ليفهم الجداول
        val rules = shapes.filter { (it.r - it.l) >= 0.2f || (it.b - it.t) >= 0.2f }.take(40)
        if (rules.isNotEmpty()) {
            sb.append("\n[rule] ").append(rules.joinToString("; ") { sh ->
                (if (sh.kind == 0) "horizontal" else "vertical") + " at x=${(sh.l * 100f).roundToInt()}% y=${(sh.t * 100f).roundToInt()}%"
            })
        }
        return sb.toString()
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
     * صورة الصفحة مع إطار مرقّم ملوّن حول كل تمييز (إحداثيات التمييز نسبية لعرض الصفحة فتُضرب في عرض الصورة).
     * الرسم المخزّن مؤقتًا يُنسخ ولا يُعدَّل. كتابة المستخدم (بلا الهايلايتر) تبقى ظاهرة.
     */
    private suspend fun framedImage(s: DocSession, page: Int, marks: List<TextMark>): ByteArray? = withContext(Dispatchers.Default) {
        try {
            val doc = s.pdf ?: return@withContext null
            val ref = doc.pages.getOrNull(page) ?: return@withContext null
            val base = doc.renderFresh(ref, VISION_W) ?: return@withContext null
            val composed = OcrEngine.compose(base, s.annotations[page] ?: emptyList())
            val bmp = composed.copy(Bitmap.Config.ARGB_8888, true)
            if (composed !== base) composed.recycle()
            base.recycle()
            if (bmp == null) return@withContext null
            try {
                val canvas = Canvas(bmp)
                val w = bmp.width.toFloat()
                val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = maxOf(3f, w * 0.003f) }
                val tag = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = w * 0.03f; isFakeBoldText = true }
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
                marks.forEachIndexed { i, m ->
                    val col = FRAME_COLORS[i % FRAME_COLORS.size]
                    stroke.color = col
                    fill.color = col
                    m.rects.forEach { r -> canvas.drawRect(r.l * w, r.t * w, r.r * w, r.b * w, stroke) }
                    val first = m.rects.minByOrNull { it.t } ?: return@forEachIndexed
                    val label = "${i + 1}"
                    val tw = tag.measureText(label)
                    val x = (first.l * w - tw - 14f).coerceAtLeast(0f)
                    val y = (first.t * w).coerceAtLeast(0f)
                    canvas.drawRect(x, y, x + tw + 12f, y + tag.textSize + 6f, fill)
                    canvas.drawText(label, x + 6f, y + tag.textSize, tag)
                }
                ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray()
            } finally {
                bmp.recycle()
            }
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * القراءة البصرية للتمييزات على صفحة واحدة: النموذج نفسه يقرأ صورة الصفحة ويكتب ما داخل كل إطار مع سياقه
     * (العنوان، رؤوس الجدول، علاقات المخطط). تُخزَّن النتيجة وتُعاد منها. يعيد: معرّف التمييز ← كتلته.
     * يرمي Ai.VisionUnavailable إن لم يعمل قارئ الصور (فلا يُدّعى أن الصفحة قُرئت بصريًا).
     */
    suspend fun readVisual(s: DocSession, page: Int, marks: List<TextMark>, onStep: (String) -> Unit = {}): Map<String, String> {
        val out = HashMap<String, String>()
        if (!Ai.visionOk || marks.isEmpty()) throw Ai.VisionUnavailable()
        val layout = layoutText(s.ocr[page]?.words ?: emptyList())
        val sorted = marks.sortedBy { m -> m.rects.minOfOrNull { it.t } ?: 0f }
        val chunks = sorted.chunked(FRAMES_PER_IMAGE)
        chunks.forEachIndexed { ci, chunk ->
            val sig = Ai.selected.id + "|" + layout.hashCode() + "|" + chunk.joinToString(";") { m ->
                m.id + m.rects.joinToString(",") { r -> "%.4f:%.4f:%.4f:%.4f".format(r.l, r.t, r.r, r.b) }
            }
            val f = File(visDir, "${s.id}_${AiStructure.hash(sig)}.txt")
            var text = if (f.exists()) f.readText() else ""
            if (text.isBlank()) {
                onStep(L("قراءة الصفحة ${page + 1} بصريًا (${ci + 1} من ${chunks.size})", "Reading page ${page + 1} visually (${ci + 1} of ${chunks.size})"))
                val img = framedImage(s, page, chunk) ?: throw Ai.VisionUnavailable()
                val hints = chunk.mapIndexed { i, m ->
                    "[${i + 1}] (${FRAME_NAMES[i % FRAME_NAMES.size]} frame) OCR hint, may contain mistakes: " + m.text.replace("\n", " / ").take(400)
                }.joinToString("\n")
                val prompt = "The page image is attached with ${chunk.size} numbered frame(s). The image is the truth; the OCR hints only help you find the frames.\n\n$hints"
                text = AiOrganizer.clean(Ai.generate(VISUAL_SYSTEM, prompt, img, requireImage = true))
                if (text.isNotBlank()) withContext(Dispatchers.IO) { f.writeText(text) }
            }
            val found = Regex("(?m)^\\s*\\[(\\d+)\\]").findAll(text).toList()
            found.forEachIndexed { i, mr ->
                val n = mr.groupValues[1].toIntOrNull()?.minus(1) ?: return@forEachIndexed
                val end = if (i + 1 < found.size) found[i + 1].range.first else text.length
                val body = text.substring(mr.range.last + 1, end).trim()
                chunk.getOrNull(n)?.let { m -> if (body.isNotBlank()) out[m.id] = body }
            }
        }
        return out
    }

    /**
     * قراءة صفحة وحفظ النتيجة (القلب المشترك بين زر «قراءة الصفحة» وقراءة صفحات التظليل قبل الترتيب).
     * useImage = false: من النص المقروء فقط (أسرع)، وتُستعمل الصورة فقط إن لم يُقرأ نص من الصفحة.
     * يعيد النص المنظّم، أو null إن لم يكن في الصفحة نص (ولا صورة يقرؤها النموذج).
     */
    suspend fun read(
        s: DocSession,
        page: Int,
        useImage: Boolean = true,
        onStep: (String) -> Unit = {},
        onPartial: (String) -> Unit = {}
    ): String? {
        val done = StringBuilder()
        var k: String? = null
        var owned = false
        try {
            onStep(L("جارٍ قراءة الصفحة ${page + 1}…", "Reading page ${page + 1}…"))
            val ocrPage = s.ocrPage(page)
            val words = ocrPage?.words ?: emptyList()
            val text = layoutText(words, ocrPage?.shapes ?: emptyList())
            k = keyOf(s.id, layoutText(words))
            if (!inflight.add(k)) return resultByKey(k)
            owned = true
            val img = if (Ai.visionOk && (useImage || text.isBlank())) pageImage(s, page) else null
            if (text.isBlank() && img == null) return null
            val arabic = isArabic(text)
            val parts = if (img != null) listOf(text.take(if (arabic) 1500 else 2600))
            else split(text, if (arabic) 2000 else 3600)
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
                // صفحة بلا نص مقروء: الصورة لازمة، فإن تعذّر قارئ الصور يُرمى خطأ بدل أن يخترع النموذج صفحة
                val res = Ai.generate(SYSTEM, prompt, if (i == 0) img else null, requireImage = part.isBlank()) { p ->
                    onPartial((done.toString() + "\n\n" + AiOrganizer.clean(p)).trim())
                }
                if (done.isNotEmpty()) done.append("\n\n")
                done.append(AiOrganizer.clean(res))
                onPartial(done.toString())
            }
            saveByKey(k, done.toString())
            return done.toString()
        } catch (e: CancellationException) {
            if (done.isNotEmpty() && k != null) saveByKey(k, done.toString())
            throw e
        } finally {
            if (owned && k != null) inflight.remove(k)
        }
    }

    fun start(s: DocSession, page: Int) {
        if (job?.isActive == true || AiOrganizer.runningDoc != null) return
        if (!Ai.ready) {
            AppController.messages.tryEmit(L("نزّل نموذج الذكاء الاصطناعي أولًا من الإعدادات", "Download the AI model first from Settings"))
            return
        }
        runningKey = "${s.id}_p$page"
        partial = ""
        job = AppController.scope.launch {
            try {
                s.ocrPage(page)
                runningKey = key(s, page) ?: runningKey
                val r = read(s, page, useImage = true, onStep = { stepText = it }, onPartial = { partial = it })
                if (r == null) {
                    AppController.messages.tryEmit(
                        L("لم يُقرأ نص في هذه الصفحة.", "No text was read on this page.")
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
    val k = AiPageReader.key(s, page)
    val running = k != null && AiPageReader.runningKey == k
    val busy = (AiPageReader.runningKey != null && !running) || AiOrganizer.runningDoc != null
    val saved = AiPageReader.result(s, page)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("✦ قراءة الصفحة ${page + 1}", "✦ Read page ${page + 1}")) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text(
                    if (Ai.visionOk) L("النموذج يقرأ صورة الصفحة ويتعرّف على العناوين والجداول والنقاط.", "The model reads the page image and recognizes headings, tables and bullet points.")
                    else L("النموذج ينظّم النص المقروء من الصفحة إلى عناوين وجداول ونقاط. لقراءة صورة الصفحة نفسها (مثل الصفحات العربية الممسوحة) اختر «AI» من الإعدادات.",
                        "The model structures the page’s recognized text into headings, tables and bullets. To read the page image itself (e.g. scanned Arabic pages) choose “AI” in Settings."),
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
