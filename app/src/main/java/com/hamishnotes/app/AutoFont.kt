package com.hamishnotes.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.IdentityHashMap
import java.util.UUID

/**
 * «Aa تلقائي»: الكتابة اليدوية تتحول إلى الخط المختار بعد إنهاء الجملة —
 * عند التوقف عن الكتابة لحظة (المدة من الإعدادات)، أو فوراً عند بدء سطر جديد.
 * النص يوضع مكان الكتابة بلونها وبحجم حروفها تقريباً، والكتابة الأصلية تُحفظ معه (رجوع لخط اليد).
 */
object AutoFont {
    private var sp: SharedPreferences? = null

    var on by mutableStateOf(false); private set
    var font by mutableStateOf("Tajawal"); private set
    /** مدة التوقف قبل التحويل (ملّي ثانية)؛ 0 = عند الضغط على ✓ فقط. */
    var delayMs by mutableStateOf(2000); private set

    fun init(ctx: Context) {
        val p = ctx.getSharedPreferences("hamish_autofont", Context.MODE_PRIVATE)
        sp = p
        on = p.getBoolean("on", false)
        font = p.getString("font", "Tajawal") ?: "Tajawal"
        delayMs = p.getInt("delay", 2000)
    }

    fun changeOn(v: Boolean) {
        on = v
        sp?.edit()?.putBoolean("on", v)?.apply()
        if (v) HandwritingReader.prefetch(PenPrefs.hwLang) else discard()
    }

    fun changeFont(v: String) {
        if (v.isBlank()) return
        font = v
        sp?.edit()?.putString("font", v)?.apply()
        AppController.scope.launch { FontStore.ensure(v) }
    }

    fun changeDelay(v: Int) {
        delayMs = v
        sp?.edit()?.putInt("delay", v)?.apply()
    }

    // ---------------------------------------------------------------- الجملة الجارية

    private var session: DocSession? = null
    private var page = -1
    private val pending = ArrayList<Ink>()
    private var timer: Job? = null
    private var penIsDown = false
    private var warned = false

    /** عدد خطوط الجملة التي لم تتحول بعد (لزر ✓ في الوضع اليدوي). */
    var pendingCount by mutableStateOf(0); private set

    class Preview(val id: Long, val text: String)
    /** الجملة المقروءة التي ستتحول بعد لحظة (يمكن إلغاؤها). */
    var preview by mutableStateOf<Preview?>(null); private set
    private var cancelledId = -1L
    private var nextId = 1L

    /** آخر مربع نص تحوّل (لزر «رجوع لخط يدي» لثوانٍ). */
    var lastBox by mutableStateOf<Pair<DocSession, String>?>(null); private set

    /** القلم نزل لبدء خط: لا تحويل أثناء الكتابة. */
    fun penDown() {
        penIsDown = true
        timer?.cancel()
    }

    /** القلم رُفع (بعد حفظ الخط). */
    fun penUp() {
        penIsDown = false
        if (pending.isNotEmpty() && delayMs > 0 && timer?.isActive != true) schedule()
    }

    /** خط جديد حُفظ على الصفحة. */
    fun onStroke(s: DocSession, pg: Int, ink: Ink) {
        if (!on || !Premium.active) return
        if (!ink.tool.isPen() || !HandwritingLayout.eligible(ink)) return
        if (pending.isNotEmpty() && (s !== session || pg != page || startsNewLine(ink))) flush()
        session = s
        page = pg
        pending.add(ink)
        pendingCount = pending.size
        if (delayMs > 0) schedule()
    }

    private fun schedule() {
        timer?.cancel()
        val d = delayMs.toLong()
        timer = AppController.scope.launch {
            delay(d)
            if (!penIsDown) flush()
        }
    }

    /** الخط الجديد تحت الجملة الجارية (أو فوقها) بمسافة واضحة = سطر جديد. */
    private fun startsNewLine(ink: Ink): Boolean {
        val pt = pending.minOf { s -> s.pts.minOf { it.y } }
        val pb = pending.maxOf { s -> s.pts.maxOf { it.y } }
        val it0 = ink.pts.minOf { it.y }
        val ib = ink.pts.maxOf { it.y }
        val gap = maxOf(0.012f, (pb - pt) * 0.3f)
        return it0 > pb + gap || ib < pt - gap
    }

    /** يحوّل الجملة الجارية الآن. */
    fun flush() {
        timer?.cancel()
        timer = null
        val s = session ?: return
        val pg = page
        val batch = pending.toList()
        pending.clear()
        pendingCount = 0
        if (batch.isEmpty()) return
        AppController.scope.launch { convert(s, pg, batch) }
    }

    /** يترك الجملة الجارية بخط اليد. */
    fun discard() {
        timer?.cancel()
        timer = null
        pending.clear()
        pendingCount = 0
        preview?.let { cancelledId = it.id }
        preview = null
    }

    fun cancelPreview() {
        preview?.let { cancelledId = it.id }
        preview = null
    }

    private suspend fun convert(s: DocSession, pg: Int, batch: List<Ink>) {
        val words = try {
            HandwritingReader.read(batch, PenPrefs.hwLang, 0)
        } catch (t: Throwable) {
            null
        }
        if (words == null) {
            if (!warned) {
                warned = true
                AppController.messages.tryEmit(
                    L(
                        "حزمة قراءة خط اليد غير جاهزة؛ اتصل بالإنترنت مرة واحدة ليبدأ التحويل التلقائي.",
                        "The handwriting pack isn’t ready; connect to the internet once to start auto-convert."
                    )
                )
            }
            return
        }
        val text = handwritingText(words).trim()
        if (text.isBlank()) return
        val id = nextId++
        preview = Preview(id, text)
        delay(900)
        // لا نعدّل الصفحة والقلم يكتب (حتى لا يختلط سجل التراجع)
        while (penIsDown) delay(60)
        if (preview?.id == id) preview = null
        if (cancelledId == id || s.pdf == null) return
        val present = java.util.Collections.newSetFromMap(IdentityHashMap<Ink, Boolean>())
            .apply { addAll(s.annotations[pg] ?: emptyList()) }
        val alive = batch.filter { it in present }
        if (alive.isEmpty()) return

        val l = alive.minOf { k -> k.pts.minOf { it.x } }
        val t = alive.minOf { k -> k.pts.minOf { it.y } }
        val r = alive.maxOf { k -> k.pts.maxOf { it.x } }
        val b = alive.maxOf { k -> k.pts.maxOf { it.y } }
        val lines = words.map { it.line }.distinct().size.coerceAtLeast(1)
        val size = ((b - t) / lines * 0.62f).coerceIn(0.012f, 0.06f)
        val w = ((r - l) * 1.25f).coerceIn(0.15f, 0.95f)
        // النص العربي يبدأ من اليمين: نُبقي طرفه الأيمن مكان الكتابة
        val rtl = text.any { it in '؀'..'ۿ' }
        val x = (if (rtl) r - w else l).coerceIn(0f, (1f - w).coerceAtLeast(0f))
        val box = TextBox(
            id = UUID.randomUUID().toString(), page = pg,
            x = x, y = t, w = w, text = text,
            color = alive.first().color, size = size, font = font, ink = alive
        )
        FontStore.markUsed(font)
        s.autoInkToText(pg, alive, box)
        lastBox = s to box.id
        delay(3500)
        if (lastBox?.second == box.id) lastBox = null
    }

    fun undoLast() {
        val (s, id) = lastBox ?: return
        lastBox = null
        s.textBoxToInk(id)
    }
}

/** شريط صغير أسفل الشاشة: «يتحول…» مع الإلغاء، و«تم التحويل» مع الرجوع، و✓ في الوضع اليدوي. */
@Composable
fun AutoFontChip(s: DocSession) {
    val pv = AutoFont.preview
    val last = AutoFont.lastBox?.takeIf { it.first === s }
    val manual = AutoFont.on && AutoFont.delayMs == 0 && AutoFont.pendingCount > 0
    if (pv == null && last == null && !manual) return
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 8.dp) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                pv != null -> {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Palette.Navy)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "«${pv.text}» ← " + L("يتحول…", "converting…"),
                        fontSize = 13.sp, color = Palette.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    TextButton(onClick = { AutoFont.cancelPreview() }) {
                        Text("✕ " + L("إبقاء بخط يدي", "Keep handwriting"), color = Palette.Danger, fontSize = 13.sp)
                    }
                }
                manual -> {
                    TextButton(onClick = { AutoFont.flush() }) {
                        Text("✓ " + L("حوّل إلى ${AutoFont.font}", "Convert to ${AutoFont.font}"), color = Palette.Navy, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { AutoFont.discard() }) { Text(L("إبقاء بخط يدي", "Keep handwriting"), color = Palette.TextSecondary) }
                }
                else -> {
                    Text("✓ " + L("تم التحويل", "Converted"), fontSize = 13.sp, color = Color(0xFF1F7A4D), fontWeight = FontWeight.Bold)
                    TextButton(onClick = { AutoFont.undoLast() }) {
                        Text("↶ " + L("رجوع لخط يدي", "Back to handwriting"), color = Palette.Navy, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/** إعدادات «Aa تلقائي»: الخط، ومتى تتحول الجملة، والتشغيل/الإيقاف. */
@Composable
fun AutoFontDialog(onDismiss: () -> Unit) {
    var font by remember { mutableStateOf(AutoFont.font) }
    var delayMs by remember { mutableStateOf(AutoFont.delayMs) }
    var library by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { HandwritingReader.prefetch(PenPrefs.hwLang) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aa " + L("تحويل الكتابة تلقائياً", "Auto-convert handwriting"), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, color = Palette.Navy) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    L(
                        "اكتب بالقلم كالعادة؛ عند إنهاء الجملة تتحول في مكانها إلى الخط المختار. التراجع يعيدها لخط يدك.",
                        "Write with the pen as usual; when you finish a sentence it turns into the chosen font in place. Undo brings your handwriting back."
                    ),
                    fontSize = 13.sp, color = Palette.TextSecondary, lineHeight = 19.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(L("الخط", "Font"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.Navy)
                val ready = FontStore.state[font] == true || FontStore.isReady(font)
                if (!ready) LaunchedEffect(font) { FontStore.ensure(font) }
                Text(
                    L("الخلية وحدة الحياة", "The cell is the unit of life"),
                    fontFamily = if (ready) FontStore.family(font) else null, fontSize = 24.sp, lineHeight = 32.sp, color = Palette.Navy,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(12.dp))
                        .background(Palette.Ivory).padding(12.dp)
                )
                val quick = (listOf(font) + FontStore.recent + FontStore.catalog.filter { it.bundled != null }.map { it.name }).distinct().take(16)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    quick.forEach { n ->
                        val sel = n == font
                        Text(
                            n, fontFamily = FontStore.family(n), fontSize = 15.sp,
                            color = if (sel) Color.White else Palette.TextPrimary,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                .background(if (sel) Palette.Navy else Palette.NavySoft)
                                .clickable { font = n }.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                TextButton(onClick = { library = true }) {
                    Text(L("مكتبة الخطوط كاملة (${FontStore.catalog.size})…", "Full font library (${FontStore.catalog.size})…"), color = Palette.GoldDeep, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                Text(L("متى تتحول الجملة", "When a sentence converts"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.Navy)
                Text(L("وتتحول فوراً عندما تبدأ سطراً جديداً.", "It also converts as soon as you start a new line."), fontSize = 12.sp, color = Palette.TextSecondary)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        1000 to L("بعد ثانية", "After 1 s"),
                        2000 to L("بعد ثانيتين", "After 2 s"),
                        3000 to L("بعد 3 ثوانٍ", "After 3 s"),
                        0 to L("عند الضغط على ✓", "When I tap ✓")
                    ).forEach { (v, label) -> ChoiceChip(label, delayMs == v) { delayMs = v } }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                AutoFont.changeFont(font)
                AutoFont.changeDelay(delayMs)
                AutoFont.changeOn(true)
                AppController.messages.tryEmit(L("اكتب جملتك، وستتحول عند إنهائها", "Write your sentence; it converts when you finish"))
                onDismiss()
            }) { Text(if (AutoFont.on) L("حفظ", "Save") else L("تشغيل", "Turn on"), fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            if (AutoFont.on) {
                TextButton(onClick = { AutoFont.changeOn(false); onDismiss() }) { Text(L("إيقاف", "Turn off"), color = Palette.Danger) }
            } else {
                TextButton(onClick = onDismiss) { Text(L("إلغاء", "Cancel")) }
            }
        }
    )
    if (library) FontLibraryDialog(L("الخلية وحدة الحياة", "The cell"), font, onPick = { if (it.isNotEmpty()) font = it }, onDismiss = { library = false })
}
