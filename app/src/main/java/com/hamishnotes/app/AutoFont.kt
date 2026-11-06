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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.IdentityHashMap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import java.util.UUID

/**
 * «Aa تلقائي»: الكتابة اليدوية تتحول إلى الخط المختار بعد إنهاء الجملة —
 * عند التوقف عن الكتابة لحظة (المدة من الإعدادات)، أو فوراً عند بدء سطر جديد.
 * النص يوضع مكان الكتابة بلونها وبحجم حروفها تقريباً، والكتابة الأصلية تُحفظ معه (رجوع لخط اليد).
 */
object AutoFont {
    private var sp: SharedPreferences? = null

    var on by mutableStateOf(false); private set
    var font by mutableStateOf("Patrick Hand"); private set
    /** مدة التوقف قبل التحويل (ملّي ثانية)؛ 0 = عند الضغط على ✓ فقط. */
    var delayMs by mutableStateOf(2000); private set
    /** الأسطر المتتالية تُجمع في فقرة واحدة (مربع نص واحد، في وضع النص فقط). */
    var joinLines by mutableStateOf(true); private set
    /** قوة جذب الكتابة إلى أسطر الدفتر: 0 بلا، 1 ضعيف، 2 عادي، 3 قوي. */
    var snap by mutableStateOf(2); private set
    val snapToLine get() = snap > 0
    /** لغة الكتابة للتحويل: عربي أو إنجليزي فقط (لا قراءة مختلطة). */
    var lang by mutableStateOf(HwLang.ARABIC); private set
    /** شكل الناتج: خطوط قلم بالخط المختار (تُمسح بالممحاة) أو مربع نص. */
    var output by mutableStateOf(OUT_INK); private set
    /** خطوط أعرض تتبع سماكة حروف الخط (بدل عرض القلم). */
    var dynamicBold by mutableStateOf(false); private set
    /** حجم واحد لكل الكتابة ومسافة ثابتة بين الأسطر. */
    var unify by mutableStateOf(false); private set
    /** الحجم الموحد بالنقاط (عرض الصفحة = 595 نقطة كورقة A4). */
    var unifySize by mutableStateOf(20); private set
    /** المسافة بين الأسطر الموحدة (مضاعف حجم الخط). */
    var unifySpacing by mutableStateOf(1.5f); private set

    const val OUT_INK = 0
    const val OUT_TEXT = 1
    const val SIZE_MIN = 8
    const val SIZE_MAX = 48

    fun init(ctx: Context) {
        val p = ctx.getSharedPreferences("hamish_autofont", Context.MODE_PRIVATE)
        sp = p
        on = p.getBoolean("on", false)
        font = p.getString("font", "Patrick Hand") ?: "Patrick Hand"
        delayMs = p.getInt("delay", 2000)
        joinLines = p.getBoolean("join", true)
        snap = if (p.contains("snapLevel")) p.getInt("snapLevel", 2).coerceIn(0, 3) else if (p.getBoolean("snap", true)) 2 else 0
        lang = p.getInt("lang", if (I18n.english) HwLang.ENGLISH else HwLang.ARABIC)
            .let { if (it == HwLang.ENGLISH) HwLang.ENGLISH else HwLang.ARABIC }
        output = if (p.getInt("out", OUT_INK) == OUT_TEXT) OUT_TEXT else OUT_INK
        dynamicBold = p.getBoolean("bold", false)
        unify = p.getBoolean("unify", false)
        unifySize = p.getInt("usize", 20).coerceIn(SIZE_MIN, SIZE_MAX)
        unifySpacing = p.getFloat("uspace", 1.5f).coerceIn(1f, 3f)
    }

    fun changeOn(v: Boolean) {
        on = v
        sp?.edit()?.putBoolean("on", v)?.apply()
        if (v) HandwritingReader.prefetch(lang) else discard()
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

    fun changeJoin(v: Boolean) {
        joinLines = v
        sp?.edit()?.putBoolean("join", v)?.apply()
    }

    fun changeLang(v: Int) {
        lang = if (v == HwLang.ENGLISH) HwLang.ENGLISH else HwLang.ARABIC
        sp?.edit()?.putInt("lang", lang)?.apply()
        HandwritingReader.prefetch(lang)
    }

    fun changeSnap(v: Int) {
        snap = v.coerceIn(0, 3)
        sp?.edit()?.putInt("snapLevel", snap)?.apply()
    }

    fun changeOutput(v: Int) {
        output = if (v == OUT_TEXT) OUT_TEXT else OUT_INK
        sp?.edit()?.putInt("out", output)?.apply()
    }

    fun changeBold(v: Boolean) {
        dynamicBold = v
        sp?.edit()?.putBoolean("bold", v)?.apply()
    }

    fun changeUnify(v: Boolean, size: Int, spacing: Float) {
        unify = v
        unifySize = size.coerceIn(SIZE_MIN, SIZE_MAX)
        unifySpacing = spacing.coerceIn(1f, 3f)
        sp?.edit()?.putBoolean("unify", v)?.putInt("usize", unifySize)?.putFloat("uspace", unifySpacing)?.apply()
    }

    /** الحجم الموحد بوحدة عرض الصفحة. */
    fun unifiedSize(pt: Int = unifySize): Float = pt / 595f

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

    /** آخر مربع صنعه التحويل التلقائي في كل صفحة (لجمع الأسطر التالية فيه). */
    private var lastAuto: Triple<DocSession, Int, String>? = null

    /** مكان إضافة كلمة رُسم بعلامة ∧: الكتابة التالية في المنطقة تُدرج في النص هنا. */
    class InsertTarget(val s: DocSession, val page: Int, val boxId: String, val offset: Int, val area: Rect)
    var insertTarget by mutableStateOf<InsertTarget?>(null); private set

    fun cancelInsert() { insertTarget = null }

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
        val ins = insertTarget?.takeIf { it.s === s && it.page == pg }
        if (ins == null && (!on || !Premium.active)) return
        if (!ink.tool.isPen() || !HandwritingLayout.eligible(ink)) return
        if (pending.isNotEmpty() && (s !== session || pg != page || startsNewLine(ink))) flush()
        session = s
        page = pg
        pending.add(ink)
        pendingCount = pending.size
        // الإضافة بعلامة ∧ تتحول بعد توقف قصير حتى في الوضع اليدوي
        val d = if (ins != null && delayMs == 0) 1500 else delayMs
        if (d > 0) schedule(d)
    }

    private fun schedule(ms: Int = delayMs) {
        timer?.cancel()
        val d = ms.toLong()
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
        insertTarget = null
    }

    fun cancelPreview() {
        preview?.let { cancelledId = it.id }
        preview = null
    }

    // ---------------------------------------------------------------- المقاس والموضع

    /** x,y زاوية المربع العليا، w عرضه، size حجم الخط، base خط القاعدة المطلوب لأول سطر. */
    class Fit(val x: Float, val y: Float, val w: Float, val size: Float, val base: Float)

    /** حدود الكتابة اليدوية: يسار، أعلى، يمين، أسفل. */
    private fun inkBounds(strokes: List<Ink>): FloatArray = floatArrayOf(
        strokes.minOf { k -> k.pts.minOf { it.x } }, strokes.minOf { k -> k.pts.minOf { it.y } },
        strokes.maxOf { k -> k.pts.maxOf { it.x } }, strokes.maxOf { k -> k.pts.maxOf { it.y } }
    )

    /**
     * حجم الخط الذي يجعل حبر النص (ارتفاعه h وعرضه w بوحدة حجم الخط) بمقاس الكتابة اليدوية:
     * يُوازن بين ارتفاع الكتابة وعرضها، ولا يبتعد كثيراً عن الارتفاع.
     */
    internal fun sizeFor(handW: Float, handH: Float, emW: Float, emH: Float): Float {
        val sH = handH / emH.coerceAtLeast(0.2f)
        val sW = if (emW > 0.05f) handW / emW else sH
        return kotlin.math.sqrt(sH * sW).coerceIn(sH * 0.7f, sH * 1.3f).coerceIn(0.008f, 0.2f)
    }

    /** أسطر الدفتر في الصفحة (فارغة للصفحات بلا أسطر). */
    private fun ruledLines(s: DocSession, pg: Int): List<Float> {
        val src = s.pdf?.pages?.getOrNull(pg)?.src ?: return emptyList()
        if (!src.startsWith("blank:")) return emptyList()
        val ratio = s.pdf?.ratio(pg) ?: 0.707f
        return Templates.lineYs(Templates.parse(src).first, 1f, 1f / ratio)
    }

    /**
     * خط القاعدة بعد الجذب: إلى سطر الدفتر القريب (حسب قوة الجذب)، وإلا بمسافة ثابتة
     * تحت السطر السابق إن كان الحجم موحداً.
     */
    internal fun snapBase(lines: List<Float>, base: Float, level: Int, size: Float, prevBase: Float?, spacing: Float?): Float {
        if (level > 0 && lines.isNotEmpty()) {
            val near = lines.minByOrNull { kotlin.math.abs(it - base) }!!
            val step = if (lines.size > 1) lines[1] - lines[0] else 0.04f
            val reach = step * when (level) { 1 -> 0.25f; 2 -> 0.5f; else -> 1f }
            if (kotlin.math.abs(near - base) <= reach) return near
        }
        if (spacing != null && prevBase != null) {
            val st = size * spacing
            val k = kotlin.math.round((base - prevBase) / st)
            if (k in 1f..3f) return prevBase + k * st
        }
        return base
    }

    /**
     * حجم النص وموضعه ليأخذ مكان الكتابة اليدوية ويجلس على سطرها: يُقاس حبر النص بالخط المختار نفسه
     * (لا بمقاييس الخط العامة)، فلا ينزل النص تحت مكان الكتابة مع الخطوط ذات المقاييس الكبيرة.
     */
    fun fit(s: DocSession, pg: Int, strokes: List<Ink>, textLines: List<String>, fontName: String, snapLevel: Int = snap, unified: Boolean = unify): Fit {
        val (l, t, r, b) = inkBounds(strokes).let { listOf(it[0], it[1], it[2], it[3]) }
        val tf = FontStore.typeface(fontName)
        val bs = textLines.ifEmpty { listOf(" ") }.map { GlyphInk.bounds(it, tf) }
        val n = bs.size
        val emW = bs.maxOf { it[2] - it[0] }
        val emH = (n - 1) * LINE + (bs.last()[3] - bs.first()[1])
        val size = if (unified) unifiedSize() else sizeFor(r - l, b - t, emW, emH)
        val w = (emW * size * 1.08f + 0.01f).coerceIn(0.05f, 1f)
        val rtl = textLines.any { line -> line.any { it in '؀'..'ۿ' } }
        val x = (if (rtl) r - w else l - bs.first()[0] * size).coerceIn(0f, (1f - w).coerceAtLeast(0f))
        var base = b - ((n - 1) * LINE + bs.last()[3]) * size
        base = snapBase(ruledLines(s, pg), base, snapLevel, size, null, null)
        val y = (base - firstBaseline(tf) * size).coerceAtLeast(0f)
        return Fit(x, y, w, size, base)
    }

    /** ارتفاع السطر في مربعات النص (lineHeight = 1.25 × الحجم). */
    private const val LINE = 1.25f

    /** تقدير مكان أول خط قاعدة من أعلى مربع النص (بوحدة الحجم)؛ يُصحَّح بعد الرسم. */
    private fun firstBaseline(tf: android.graphics.Typeface?): Float {
        val fm = GlyphInk.paint(tf).fontMetrics
        val asc = -fm.ascent / 120f
        val desc = fm.descent / 120f
        val h = (asc + desc).coerceAtLeast(0.5f)
        return asc + (LINE - h) * (asc / h)
    }

    /**
     * يضع أول خط قاعدة لمربع النص على base بالضبط بعد أن يُرسم (مقاييس الخطوط تختلف كثيراً):
     * لا يضيف خطوة تراجع.
     */
    fun alignBaseline(s: DocSession, boxId: String, base: Float) {
        AppController.scope.launch {
            repeat(40) {
                val lw = s.textLayouts[boxId]
                val tb = s.textBox(boxId) ?: return@launch
                if (lw != null && lw.second > 0f && lw.first.lineCount > 0) {
                    val fb = lw.first.firstBaseline / lw.second
                    val dy = base - (tb.y + fb)
                    if (kotlin.math.abs(dy) > 0.0008f) s.nudgeText(boxId, dy)
                    return@launch
                }
                delay(50)
            }
        }
    }

    // ---------------------------------------------------------------- التحويل

    private suspend fun convert(s: DocSession, pg: Int, batch: List<Ink>) {
        val altsList = ArrayList<List<String>>()
        val words = try {
            HandwritingReader.read(batch, lang, 0, altsList)
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
        val alts = HashMap<String, List<String>>()
        words.forEachIndexed { i, w -> altsList.getOrNull(i)?.takeIf { it.isNotEmpty() }?.let { alts[w.text] = it } }
        val id = nextId++
        preview = Preview(id, text)
        // الخط المختار جاهز قبل القياس (وإلا يُقاس بخط آخر فيخطئ المقاس والمكان)
        FontStore.ensure(font)
        delay(700)
        // لا نعدّل الصفحة والقلم يكتب (حتى لا يختلط سجل التراجع)
        while (penIsDown) delay(60)
        if (preview?.id == id) preview = null
        if (cancelledId == id || s.pdf == null) return
        val present = java.util.Collections.newSetFromMap(IdentityHashMap<Ink, Boolean>())
            .apply { addAll(s.annotations[pg] ?: emptyList()) }
        val alive = batch.filter { it in present }
        if (alive.isEmpty()) return
        val (l, t, r, b) = inkBounds(alive).let { listOf(it[0], it[1], it[2], it[3]) }

        // 1) إضافة بعلامة ∧: تُدرج الكلمة في مكانها داخل النص
        val ins = insertTarget
        if (ins != null && ins.s === s && ins.page == pg) {
            val cx = (l + r) / 2f
            val cy = (t + b) / 2f
            val tb = s.textBox(ins.boxId)
            if (tb != null && ins.area.contains(Offset(cx, cy))) {
                insertTarget = null
                val o = ins.offset.coerceIn(0, tb.text.length)
                val merged = (tb.text.substring(0, o).trimEnd() + " " + text + " " + tb.text.substring(o).trimStart()).trim()
                s.autoReplace(pg, alive, tb.copy(text = merged, ink = tb.ink + alive, alts = tb.alts + alts))
                FontStore.markUsed(tb.font)
                return
            }
        }

        // 2) خطوط قلم بالخط المختار: تبقى كتابة يد تُمسح بالممحاة
        if (output == OUT_INK) {
            toInk(s, pg, alive, words)
            return
        }

        val lineTexts = text.split('\n').filter { it.isNotBlank() }.ifEmpty { listOf(text) }

        // 3) فقرة واحدة: السطر الجديد تحت آخر مربع متحول مباشرة يُضاف إليه
        val la = lastAuto
        if (joinLines && la != null && la.first === s && la.second == pg) {
            val prev = s.textBox(la.third)
            val lay = s.textLayouts[la.third]
            if (prev != null && prev.font == font) {
                val prevH = lay?.let { it.first.size.height / it.second } ?: (prev.size * 1.25f)
                val prevB = prev.y + prevH
                val lineH = prev.size * 1.25f
                val overlapX = minOf(r, prev.x + prev.w + 0.1f) - maxOf(l, prev.x - 0.1f) > 0f
                if (overlapX && t > prevB - lineH * 0.5f && t < prevB + lineH * 1.6f) {
                    val rtl = text.any { it in '؀'..'ۿ' }
                    val right = maxOf(prev.x + prev.w, r)
                    val left = minOf(prev.x, l)
                    val nw = (right - left).coerceIn(minOf(prev.w, 1f), 1f)
                    val nx = if (rtl) (right - nw) else prev.x
                    val merged = prev.copy(
                        text = prev.text.trimEnd() + " " + lineTexts.joinToString(" "),
                        ink = prev.ink + alive, alts = prev.alts + alts,
                        x = nx.coerceIn(0f, (1f - nw).coerceAtLeast(0f)), w = nw
                    )
                    s.autoReplace(pg, alive, merged)
                    lastBox = s to prev.id
                    FontStore.markUsed(font)
                    return
                }
            }
        }

        // 4) مربع جديد بنفس عرض الكتابة وعلى سطرها
        val f = fit(s, pg, alive, lineTexts, font)
        val box = TextBox(
            id = UUID.randomUUID().toString(), page = pg,
            x = f.x, y = f.y, w = f.w, text = lineTexts.joinToString("\n"),
            color = alive.first().color, size = f.size, font = font, ink = alive, alts = alts
        )
        FontStore.markUsed(font)
        s.autoInkToText(pg, alive, box)
        alignBaseline(s, box.id, f.base)
        lastAuto = Triple(s, pg, box.id)
        lastBox = s to box.id
        delay(3500)
        if (lastBox?.second == box.id) lastBox = null
    }

    /** آخر تحويل إلى خطوط قلم (لزر «رجوع لخط يدي»): الخطوط الجديدة والكتابة الأصلية. */
    class InkSwap(val s: DocSession, val page: Int, val made: List<Ink>, val original: List<Ink>)
    var lastInk by mutableStateOf<InkSwap?>(null); private set
    /** خط قاعدة آخر سطر تحوّل (للمسافة الموحدة بين الأسطر). */
    private var lastLine: LineInfo? = null

    /** آخر سطر تجمّل: خط قاعدته وبدايته (لمسافة موحدة بين الأسطر ولمحاذاة بداية السطر التالي). */
    private class LineInfo(val s: DocSession, val page: Int, val base: Float, val start: Float, val rtl: Boolean)

    /**
     * بداية السطر (يسار للإنجليزي، يمين للعربي): إن بدأ قريباً من بداية السطر السابق تحته
     * يُحاذى معها، فتبدو الأسطر مرتبة كنص مكتوب.
     */
    internal fun alignStart(start: Float, rtl: Boolean, base: Float, prevStart: Float?, prevRtl: Boolean?, prevBase: Float?, size: Float): Float {
        if (prevStart == null || prevBase == null || prevRtl != rtl) return start
        val below = base - prevBase
        if (below <= 0f || below > size * 4f) return start
        return if (kotlin.math.abs(start - prevStart) <= 0.06f) prevStart else start
    }

    /** يرسم الكلمات المقروءة بالخط المختار كخطوط قلم مكان الكتابة اليدوية (خطوة تراجع واحدة). */
    private suspend fun toInk(s: DocSession, pg: Int, alive: List<Ink>, words: List<OcrWord>) {
        val fontName = font
        val tf = FontStore.typeface(fontName)
        val lines = words.filter { it.text.isNotBlank() }.groupBy { it.line }.toSortedMap().values.toList()
        if (lines.isEmpty()) return
        val ruled = ruledLines(s, pg)
        val first = alive.first()
        val bold = dynamicBold
        var prev = lastLine?.takeIf { it.s === s && it.page == pg }
        val out = ArrayList<Ink>()
        for (line in lines) {
            // السطر كله نص واحد بمسافات الخط العادية (كأنه مكتوب بالكيبورد)، ثم حروفه أشكال مملوءة من الخط
            val text = line.joinToString(" ") { it.text.trim() }.trim()
            val g = withContext(Dispatchers.Default) { GlyphInk.line(text, fontName, tf) } ?: continue
            val l = line.minOf { it.l }
            val t = line.minOf { it.t }
            val r = line.maxOf { it.r }
            val b = line.maxOf { it.b }
            // العرض من الكلمات نفسها لا من الفراغات بينها (المسافات بين كلمات اليد غير منتظمة)
            val handW = line.sumOf { (it.r - it.l).toDouble() }.toFloat()
            val emW = (g.right - g.left - g.space * (line.size - 1)).coerceAtLeast((g.right - g.left) * 0.5f)
            val size = if (unify) unifiedSize() else sizeFor(handW, b - t, emW, g.bottom - g.top)
            var base = b - g.bottom * size
            base = snapBase(ruled, base, snap, size, prev?.base, if (unify) unifySpacing else null)
            val rtl = text.any { it in '\u0600'..'\u06FF' }
            val start = alignStart(if (rtl) r else l, rtl, base, prev?.start, prev?.rtl, prev?.base, size)
            // يبدأ السطر حيث بدأت الكتابة (يسار الإنجليزي، يمين العربي)
            val ox = if (rtl) start - g.right * size else start - g.left * size
            // «سماكة متغيرة»: تعريض الحرف كله قليلاً
            val outline = if (bold) size * 0.035f else 0f
            for (piece in g.pieces) {
                val placed = piece.map { c -> FloatArray(c.size) { i -> if (i % 2 == 0) ox + c[i] * size else base + c[i] * size } }
                out.add(Ink(InkFill.encode(placed), first.color, outline, first.tool, t = first.t, style = LineStyle.SOLID, fill = true))
            }
            prev = LineInfo(s, pg, base, start, rtl)
        }
        if (out.isEmpty()) return
        // داخل حدود الصفحة
        val minX = out.minOf { k -> k.pts.minOf { it.x } }
        val maxX = out.maxOf { k -> k.pts.maxOf { it.x } }
        val dx = when {
            minX < 0f -> -minX
            maxX > 1f -> 1f - maxX
            else -> 0f
        }
        val made = if (dx == 0f) out else out.map { k -> k.copy(pts = k.pts.map { Pt(it.x + dx, it.y, it.p) }) }
        s.replaceInk(pg, alive, made)
        FontStore.markUsed(fontName)
        lastLine = prev
        val swap = InkSwap(s, pg, made, alive)
        lastInk = swap
        delay(3500)
        if (lastInk === swap) lastInk = null
    }

    fun undoLast() {
        lastInk?.let { sw ->
            lastInk = null
            sw.s.replaceInk(sw.page, sw.made, sw.original)
            return
        }
        val (s, id) = lastBox ?: return
        lastBox = null
        s.textBoxToInk(id)
    }

    // ---------------------------------------------------------------- الحذف والإضافة بالقلم

    private enum class Gesture { SCRIBBLE, STRIKE, CARET }

    private fun classify(ink: Ink, size: Float): Gesture? {
        val p = ink.pts
        if (p.size < 3) return null
        val l = p.minOf { it.x }
        val r = p.maxOf { it.x }
        val t = p.minOf { it.y }
        val b = p.maxOf { it.y }
        val w = r - l
        val h = b - t
        var len = 0f
        var rev = 0
        var lastSign = 0
        var acc = 0f
        for (i in 1 until p.size) {
            val dx = p[i].x - p[i - 1].x
            val dy = p[i].y - p[i - 1].y
            len += kotlin.math.sqrt(dx * dx + dy * dy)
            acc += dx
            if (kotlin.math.abs(acc) > size * 0.12f) {
                val sg = if (acc > 0) 1 else -1
                if (lastSign != 0 && sg != lastSign) rev++
                lastSign = sg
                acc = 0f
            }
        }
        if (w > size * 0.4f && len > w * 2.8f && rev >= 3 && h < size * 2f) return Gesture.SCRIBBLE
        if (h < size * 0.45f && w > size * 0.6f && len < w * 1.25f) return Gesture.STRIKE
        val apex = p.minByOrNull { it.y } ?: return null
        if (w < size * 1.5f && w > size * 0.15f && h > size * 0.25f && h < size * 1.5f &&
            apex.x > l + w * 0.2f && apex.x < r - w * 0.2f &&
            p.first().y - t > h * 0.6f && p.last().y - t > h * 0.6f && len < (w + h) * 2.6f
        ) return Gesture.CARET
        return null
    }

    /**
     * خط القلم فوق نص متحول: شخبطة أو خط فوق كلمة = حذفها، و∧ بين كلمتين = مكان لإضافة.
     * true = الخط إشارة نُفذت (لا يُحفظ كحبر).
     */
    fun gesture(s: DocSession, pg: Int, ink: Ink): Boolean {
        if (!ink.tool.isPen() || ink.shape || ink.pts.size < 3) return false
        val boxes = (s.texts[pg] ?: emptyList()).filter { it.ink.isNotEmpty() && !it.sticky && it.linkDoc == null }
        if (boxes.isEmpty()) return false
        val p = ink.pts
        val l = p.minOf { it.x }
        val r = p.maxOf { it.x }
        val t = p.minOf { it.y }
        val b = p.maxOf { it.y }
        val cx = (l + r) / 2f
        val cy = (t + b) / 2f
        // الكتابة داخل مربع الإضافة المنقط كتابة عادية، لا إشارات
        insertTarget?.let { if (it.s === s && it.page == pg && it.area.contains(Offset(cx, cy))) return false }
        for (tb in boxes.asReversed()) {
            val (lay, wp) = s.textLayouts[tb.id] ?: continue
            if (wp <= 0f) continue
            val bh = lay.size.height / wp
            val lineH = tb.size * 1.25f
            if (cx < tb.x - 0.01f || cx > tb.x + tb.w + 0.01f || cy < tb.y - lineH * 0.3f || cy > tb.y + bh + lineH * 0.7f) continue
            val kind = classify(ink, tb.size) ?: return false
            fun off(nx: Float, ny: Float) = lay.getOffsetForPosition(Offset((nx - tb.x) * wp, (ny - tb.y) * wp))
            when (kind) {
                Gesture.SCRIBBLE, Gesture.STRIKE -> {
                    if (cy > tb.y + bh) return false
                    if (kind == Gesture.STRIKE) {
                        // خط تحت السطر (تسطير) ليس حذفاً
                        val li = lay.getLineForVerticalPosition((cy - tb.y) * wp)
                        val top = tb.y + lay.getLineTop(li) / wp
                        val bot = tb.y + lay.getLineBottom(li) / wp
                        if (cy < top + (bot - top) * 0.2f || cy > top + (bot - top) * 0.8f) return false
                    }
                    val a = off(l + tb.size * 0.1f, cy)
                    val z = off(r - tb.size * 0.1f, cy)
                    var st = minOf(a, z)
                    var en = maxOf(a, z)
                    if (st >= tb.text.length) return false
                    st = lay.getWordBoundary(st).start
                    en = lay.getWordBoundary((en - 1).coerceAtLeast(st)).end.coerceAtMost(tb.text.length)
                    if (en <= st) return false
                    val nt = (tb.text.substring(0, st) + tb.text.substring(en)).replace(Regex(" {2,}"), " ").trim()
                    s.texts = s.texts + (pg to (s.texts[pg] ?: emptyList()).mapNotNull {
                        if (it.id != tb.id) it else if (nt.isEmpty()) null else it.copy(text = nt)
                    })
                    if (nt.isEmpty() && s.selectedTextId == tb.id) s.selectedTextId = null
                    return true
                }
                Gesture.CARET -> {
                    val apex = p.minByOrNull { it.y } ?: return false
                    val o = off(apex.x, t - lineH * 0.35f).coerceIn(0, tb.text.length)
                    val wb = lay.getWordBoundary(o)
                    val pos = if (o - wb.start < wb.end - o) wb.start else wb.end
                    insertTarget = InsertTarget(
                        s, pg, tb.id, pos.coerceIn(0, tb.text.length),
                        Rect(apex.x - 0.18f, b - lineH * 0.2f, apex.x + 0.18f, b + lineH * 2.4f)
                    )
                    AppController.messages.tryEmit(L("اكتب الإضافة في المربع المنقط", "Write the addition in the dotted box"))
                    return true
                }
            }
        }
        return false
    }
}

/** شريط صغير أسفل الشاشة: «يتحول…» مع الإلغاء، و«تم التحويل» مع الرجوع، و✓ في الوضع اليدوي. */
@Composable
fun AutoFontChip(s: DocSession) {
    val pv = AutoFont.preview
    val last = AutoFont.lastBox?.takeIf { it.first === s } ?: AutoFont.lastInk?.takeIf { it.s === s }
    val manual = AutoFont.on && AutoFont.delayMs == 0 && AutoFont.pendingCount > 0
    val ins = AutoFont.insertTarget?.takeIf { it.s === s }
    if (pv == null && last == null && !manual && ins == null) return
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
                ins != null -> {
                    Text("∧ " + L("اكتب الإضافة في المربع المنقط", "Write the addition in the dotted box"), fontSize = 13.sp, color = Palette.Navy)
                    TextButton(onClick = { AutoFont.cancelInsert() }) { Text(L("إلغاء", "Cancel"), color = Palette.TextSecondary) }
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

@Composable
private fun AutoFontSwitch(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onChange(!value) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = Palette.TextPrimary, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(checked = value, onCheckedChange = onChange)
    }
}

/** صف رقم بزرّي − و+ وشريط تمرير (مثل «توحيد حجم الخط»). */
@Composable
private fun AutoFontStepper(label: String, value: Float, range: ClosedFloatingPointRange<Float>, step: Float, format: (Float) -> String, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, color = Palette.TextPrimary, modifier = Modifier.weight(1f))
            Text(
                "−", fontSize = 18.sp, color = Palette.Navy, textAlign = TextAlign.Center,
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(15.dp)).background(Palette.NavySoft)
                    .clickable { onChange((value - step).coerceIn(range)) }
            )
            Text(format(value), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.Navy, textAlign = TextAlign.Center, modifier = Modifier.width(44.dp))
            Text(
                "+", fontSize = 18.sp, color = Palette.Navy, textAlign = TextAlign.Center,
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(15.dp)).background(Palette.NavySoft)
                    .clickable { onChange((value + step).coerceIn(range)) }
            )
        }
        androidx.compose.material3.Slider(
            value = value, onValueChange = { onChange((kotlin.math.round(it / step) * step).coerceIn(range)) },
            valueRange = range, modifier = Modifier.fillMaxWidth().height(32.dp)
        )
    }
}

@Composable
private fun AutoFontSection(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(10.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Ivory).padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (title.isNotEmpty()) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.Navy)
            Spacer(Modifier.height(4.dp))
        }
        content()
    }
}

/** إعدادات تجميل خط اليد: شكل الناتج، والخط، والحجم الموحد، والجذب لأسطر الدفتر، ومتى تتحول الجملة. */
@Composable
fun AutoFontDialog(onDismiss: () -> Unit) {
    var font by remember { mutableStateOf(AutoFont.font) }
    var delayMs by remember { mutableStateOf(AutoFont.delayMs) }
    var join by remember { mutableStateOf(AutoFont.joinLines) }
    var snap by remember { mutableStateOf(AutoFont.snap) }
    var lang by remember { mutableStateOf(AutoFont.lang) }
    var output by remember { mutableStateOf(AutoFont.output) }
    var bold by remember { mutableStateOf(AutoFont.dynamicBold) }
    var unify by remember { mutableStateOf(AutoFont.unify) }
    var usize by remember { mutableStateOf(AutoFont.unifySize.toFloat()) }
    var uspace by remember { mutableStateOf(AutoFont.unifySpacing) }
    var library by remember { mutableStateOf(false) }
    val ink = output == AutoFont.OUT_INK
    LaunchedEffect(lang) { HandwritingReader.prefetch(lang) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aa " + L("تجميل خط اليد", "Handwriting beautification"), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, color = Palette.Navy) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    if (ink) L(
                        "اكتب بالقلم كالعادة؛ عند إنهاء الجملة تُعاد كتابتها في مكانها بالخط المختار تماماً، وتبقى حبراً لا نصاً: الممحاة تمسح الحرف الذي تمر عليه، وتُحدَّد وتُلوَّن مثل أي كتابة.",
                        "Write with the pen as usual; when you finish a sentence it is rewritten in place in exactly the chosen font and stays ink, not text: the eraser removes each letter it touches, and you can select and recolor it like any writing."
                    ) else L(
                        "اكتب بالقلم كالعادة؛ عند إنهاء الجملة تتحول في مكانها إلى نص بالخط المختار. التراجع يعيدها لخط يدك.",
                        "Write with the pen as usual; when you finish a sentence it turns into text in the chosen font in place. Undo brings your handwriting back."
                    ),
                    fontSize = 13.sp, color = Palette.TextSecondary, lineHeight = 19.sp
                )
                AutoFontSection(L("تُحفظ على شكل", "Save as")) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChoiceChip("✎ " + L("كتابة قلم (تُمسح بالممحاة)", "Pen ink (erasable)"), ink) { output = AutoFont.OUT_INK }
                        ChoiceChip("T " + L("مربع نص", "Text box"), !ink) { output = AutoFont.OUT_TEXT }
                    }
                }
                AutoFontSection(L("خط الكتابة", "Writing font")) {
                    val ready = FontStore.state[font] == true || FontStore.isReady(font)
                    if (!ready) LaunchedEffect(font) { FontStore.ensure(font) }
                    Text(
                        if (lang == HwLang.ARABIC) "الخلية وحدة الحياة" else "The cell is the unit of life",
                        fontFamily = if (ready) FontStore.family(font) else null, fontSize = 24.sp, lineHeight = 32.sp, color = Palette.Navy,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
                            .background(Color.White).padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                    // خطوط اليد المثبتة أولاً (تناسب «كتابة قلم»)، ثم الأحدث استعمالاً
                    val bundled = FontStore.catalog.filter { it.bundled != null }
                    val hand = bundled.filter { it.cat == 'H' && (lang != HwLang.ARABIC || it.arabic) }.map { it.name }
                    val rest = bundled.filter { lang != HwLang.ARABIC || it.arabic }.map { it.name }
                    val quick = (listOf(font) + hand + FontStore.recent + rest).distinct().take(18)
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
                    if (ink) AutoFontSwitch(L("خط أعرض", "Bolder"), bold) { bold = it }
                }
                AutoFontSection(L("لغة الكتابة", "Writing language")) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            HwLang.ARABIC to L("عربي", "Arabic"),
                            HwLang.ENGLISH to L("إنجليزي", "English")
                        ).forEach { (v, label) -> ChoiceChip(label, lang == v) { lang = v } }
                    }
                }
                AutoFontSection("") {
                    AutoFontSwitch(L("توحيد حجم الخط والمسافة بين الأسطر", "Unify font size & line spacing"), unify) { unify = it }
                    if (unify) {
                        AutoFontStepper(L("حجم الخط", "Font size"), usize, AutoFont.SIZE_MIN.toFloat()..AutoFont.SIZE_MAX.toFloat(), 1f, { it.toInt().toString() }) { usize = it }
                        AutoFontStepper(L("المسافة بين الأسطر", "Line spacing"), uspace, 1f..3f, 0.1f, { "%.1f".format(it) }) { uspace = it }
                    }
                }
                AutoFontSection(L("قوة الجذب إلى أسطر الدفتر", "Snapping intensity")) {
                    androidx.compose.material3.Slider(
                        value = snap.toFloat(), onValueChange = { snap = kotlin.math.round(it).toInt().coerceIn(0, 3) },
                        valueRange = 0f..3f, steps = 2, modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth()) {
                        listOf(L("بلا", "Off"), L("ضعيف", "Weak"), L("عادي", "Standard"), L("قوي", "Strong")).forEachIndexed { i, t ->
                            Text(
                                t, fontSize = 11.sp, color = if (i == snap) Palette.Navy else Palette.TextSecondary,
                                fontWeight = if (i == snap) FontWeight.Bold else FontWeight.Normal,
                                textAlign = when (i) { 0 -> TextAlign.Start; 3 -> TextAlign.End; else -> TextAlign.Center },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                AutoFontSection(L("متى تتحول الجملة", "When a sentence converts")) {
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
                    if (!ink) AutoFontSwitch(L("جمع الأسطر المتتالية في فقرة واحدة", "Join consecutive lines into one paragraph"), join) { join = it }
                }
                if (!ink) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        L(
                            "على النص المتحول: شخبطة أو خط فوق كلمة يحذفها، و∧ بين كلمتين يفتح مكاناً لتكتب إضافة. وانقر على كلمة لترى قراءات أخرى لها.",
                            "On converted text: scribble or strike through a word to delete it, draw ∧ between words to insert. Tap a word to see other readings."
                        ),
                        fontSize = 12.sp, color = Palette.TextSecondary, lineHeight = 18.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                AutoFont.changeFont(font)
                AutoFont.changeDelay(delayMs)
                AutoFont.changeJoin(join)
                AutoFont.changeSnap(snap)
                AutoFont.changeLang(lang)
                AutoFont.changeOutput(output)
                AutoFont.changeBold(bold)
                AutoFont.changeUnify(unify, usize.toInt(), uspace)
                AutoFont.changeOn(true)
                AppController.messages.tryEmit(L("اكتب جملتك، وستتجمّل عند إنهائها", "Write your sentence; it is beautified when you finish"))
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
