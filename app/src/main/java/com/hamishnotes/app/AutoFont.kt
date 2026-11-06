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
    var font by mutableStateOf("Tajawal"); private set
    /** مدة التوقف قبل التحويل (ملّي ثانية)؛ 0 = عند الضغط على ✓ فقط. */
    var delayMs by mutableStateOf(2000); private set
    /** الأسطر المتتالية تُجمع في فقرة واحدة (مربع نص واحد). */
    var joinLines by mutableStateOf(true); private set
    /** النص يجلس على أقرب سطر من أسطر الدفتر. */
    var snapToLine by mutableStateOf(true); private set

    fun init(ctx: Context) {
        val p = ctx.getSharedPreferences("hamish_autofont", Context.MODE_PRIVATE)
        sp = p
        on = p.getBoolean("on", false)
        font = p.getString("font", "Tajawal") ?: "Tajawal"
        delayMs = p.getInt("delay", 2000)
        joinLines = p.getBoolean("join", true)
        snapToLine = p.getBoolean("snap", true)
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

    fun changeJoin(v: Boolean) {
        joinLines = v
        sp?.edit()?.putBoolean("join", v)?.apply()
    }

    fun changeSnap(v: Boolean) {
        snapToLine = v
        sp?.edit()?.putBoolean("snap", v)?.apply()
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

    class Fit(val x: Float, val y: Float, val w: Float, val size: Float)

    /**
     * حجم النص وموضعه ليأخذ عرض الكتابة اليدوية تقريباً ويجلس على سطرها:
     * يُقاس النص بالخط المختار، ولا يبتعد الحجم كثيراً عن ارتفاع الحروف.
     */
    fun fit(s: DocSession, pg: Int, strokes: List<Ink>, textLines: List<String>, fontName: String, snap: Boolean): Fit {
        val l = strokes.minOf { k -> k.pts.minOf { it.x } }
        val t = strokes.minOf { k -> k.pts.minOf { it.y } }
        val r = strokes.maxOf { k -> k.pts.maxOf { it.x } }
        val b = strokes.maxOf { k -> k.pts.maxOf { it.y } }
        val n = textLines.size.coerceAtLeast(1)
        val hLine = ((b - t) / n).coerceAtLeast(0.005f)
        val paint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 100f
            FontStore.typeface(fontName)?.let { typeface = it }
        }
        val fm = paint.fontMetrics
        val asc = -fm.ascent / 100f
        val perUnit = (textLines.maxOfOrNull { paint.measureText(it) } ?: 100f) / 100f
        // ارتفاع حروف اليد يقارب ارتفاع الحرف الكبير في الخط (≈ 0.72 من حجمه)
        val sizeH = hLine / 0.75f
        val sizeW = if (perUnit > 0f) (r - l) / perUnit else sizeH
        val size = sizeW.coerceIn(sizeH * 0.55f, sizeH * 1.35f).coerceIn(0.01f, 0.15f)
        val w = (perUnit * size * 1.05f + 0.012f).coerceIn(0.08f, 1f)
        val rtl = textLines.any { line -> line.any { it in '؀'..'ۿ' } }
        val x = (if (rtl) r - w else l).coerceIn(0f, (1f - w).coerceAtLeast(0f))
        // خط القاعدة لأول سطر: قرب أسفل حروف السطر الأول
        var base = t + hLine * 0.9f
        if (snap) {
            val ratio = s.pdf?.ratio(pg) ?: 0.707f
            val src = s.pdf?.pages?.getOrNull(pg)?.src
            if (src != null && src.startsWith("blank:")) {
                val ys = Templates.lineYs(Templates.parse(src).first, 1f, 1f / ratio)
                val near = ys.minByOrNull { kotlin.math.abs(it - base) }
                val step = if (ys.size > 1) ys[1] - ys[0] else 0.04f
                if (near != null && kotlin.math.abs(near - base) < step * 0.6f) base = near
            }
        }
        val y = (base - asc * size).coerceAtLeast(0f)
        return Fit(x, y, w, size)
    }

    // ---------------------------------------------------------------- التحويل

    private suspend fun convert(s: DocSession, pg: Int, batch: List<Ink>) {
        val altsList = ArrayList<List<String>>()
        val words = try {
            HandwritingReader.read(batch, PenPrefs.hwLang, 0, altsList)
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

        val lineTexts = text.split('\n').filter { it.isNotBlank() }.ifEmpty { listOf(text) }

        // 2) فقرة واحدة: السطر الجديد تحت آخر مربع متحول مباشرة يُضاف إليه
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

        // 3) مربع جديد بنفس عرض الكتابة وعلى سطرها
        val f = fit(s, pg, alive, lineTexts, font, snapToLine)
        val box = TextBox(
            id = UUID.randomUUID().toString(), page = pg,
            x = f.x, y = f.y, w = f.w, text = lineTexts.joinToString("\n"),
            color = alive.first().color, size = f.size, font = font, ink = alive, alts = alts
        )
        FontStore.markUsed(font)
        s.autoInkToText(pg, alive, box)
        lastAuto = Triple(s, pg, box.id)
        lastBox = s to box.id
        delay(3500)
        if (lastBox?.second == box.id) lastBox = null
    }

    fun undoLast() {
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
    val last = AutoFont.lastBox?.takeIf { it.first === s }
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

/** إعدادات «Aa تلقائي»: الخط، ومتى تتحول الجملة، والتشغيل/الإيقاف. */
@Composable
fun AutoFontDialog(onDismiss: () -> Unit) {
    var font by remember { mutableStateOf(AutoFont.font) }
    var delayMs by remember { mutableStateOf(AutoFont.delayMs) }
    var join by remember { mutableStateOf(AutoFont.joinLines) }
    var snap by remember { mutableStateOf(AutoFont.snapToLine) }
    var lang by remember { mutableStateOf(PenPrefs.hwLang) }
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
                Spacer(Modifier.height(10.dp))
                AutoFontSwitch(L("جمع الأسطر المتتالية في فقرة واحدة", "Join consecutive lines into one paragraph"), join) { join = it }
                AutoFontSwitch(L("وضع النص على سطر الدفتر", "Sit text on the page's ruled line"), snap) { snap = it }
                Spacer(Modifier.height(8.dp))
                Text(L("لغة الكتابة", "Handwriting language"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.Navy)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        HwLang.BOTH to L("عربي + إنجليزي معاً", "Arabic + English"),
                        HwLang.ARABIC to L("عربي", "Arabic"),
                        HwLang.ENGLISH to L("إنجليزي", "English")
                    ).forEach { (v, label) -> ChoiceChip(label, lang == v) { lang = v } }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    L(
                        "على النص المتحول: شخبطة أو خط فوق كلمة يحذفها، و∧ بين كلمتين يفتح مكاناً لتكتب إضافة. وانقر على كلمة لترى قراءات أخرى لها.",
                        "On converted text: scribble or strike through a word to delete it, draw ∧ between words to insert. Tap a word to see other readings."
                    ),
                    fontSize = 12.sp, color = Palette.TextSecondary, lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                AutoFont.changeFont(font)
                AutoFont.changeDelay(delayMs)
                AutoFont.changeJoin(join)
                AutoFont.changeSnap(snap)
                if (lang != PenPrefs.hwLang) PenPrefs.changeHwLang(lang)
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
