package com.hamishnotes.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * شريط لاصق فوق الصفحة يخفي ما تحته (للمراجعة): نقرة عليه تكشف ما تحته، ونقرة ثانية تعيد إخفاءه.
 * pts = نقاط مسار الشريط (x0, y0, x1, y1، …) نسبية لعرض الصفحة، width = عرض الشريط نسبةً لعرض الصفحة.
 * revealed = مكشوف الآن (يظهر شفافًا). يُحفظ في hamish-data.json تحت المفتاح "tapes" (نفس الصيغة في الآيباد).
 */
data class Tape(
    val id: String,
    val page: Int,
    val pts: List<Float>,
    val width: Float,
    val color: Int,
    val revealed: Boolean = false,
    /** شكل الشريط: 0 واشي مخطّط، 1 ورقي ممزّق، 2 منقّط، 3 ناعم بسيط (TapeStyle). */
    val style: Int = TapeStyle.SOFT
)

/** أشكال الشريط اللاصق (تُحفظ رقمًا تحت "st"، بنفس الأرقام في الآيباد). */
object TapeStyle {
    const val WASHI = 0
    const val TORN = 1
    const val DOTS = 2
    const val SOFT = 3
    val all = listOf(WASHI, TORN, DOTS, SOFT)
    fun name(s: Int): String = when (s) {
        WASHI -> L("واشي مخطّط", "Striped washi")
        TORN -> L("ورقي ممزّق", "Torn paper")
        DOTS -> L("منقّط", "Dotted")
        else -> L("ناعم بسيط", "Soft")
    }
}

/** إعدادات أداة الشريط اللاصق. */
object TapeTool {
    /** أداة الشريط فعّالة: سحب القلم يضع شريطًا، والنقرة تكشفه أو تخفيه. */
    var active by mutableStateOf(false)
    var straight by mutableStateOf(true); private set
    var sizeIndex by mutableStateOf(1); private set
    var color by mutableStateOf(0xFFF2A65A.toInt()); private set
    var style by mutableStateOf(TapeStyle.WASHI); private set

    val widths = listOf(0.018f, 0.028f, 0.042f)
    val palette = listOf(
        0xFFF2A65A.toInt(), 0xFFEF8E8E.toInt(), 0xFFF2D35B.toInt(),
        0xFF8CC08B.toInt(), 0xFF7FB0DE.toInt(), 0xFFAB93D6.toInt(), 0xFF9AA3AD.toInt()
    )

    private var sp: SharedPreferences? = null
    private fun prefs(): SharedPreferences? {
        if (sp == null) {
            sp = try {
                AppController.appContext.getSharedPreferences("tape_prefs", Context.MODE_PRIVATE).also { p ->
                    straight = p.getBoolean("straight", true)
                    sizeIndex = p.getInt("size", 1).coerceIn(0, widths.size - 1)
                    color = p.getInt("color", color)
                    style = p.getInt("style", TapeStyle.WASHI).coerceIn(0, TapeStyle.all.size - 1)
                }
            } catch (e: Throwable) {
                null
            }
        }
        return sp
    }

    /** يقرأ الإعدادات المحفوظة (مرة واحدة). */
    fun load() { prefs() }

    fun width(): Float = widths[sizeIndex.coerceIn(0, widths.size - 1)]

    fun changeStraight(v: Boolean) { straight = v; prefs()?.edit()?.putBoolean("straight", v)?.apply() }
    fun changeSize(i: Int) { sizeIndex = i.coerceIn(0, widths.size - 1); prefs()?.edit()?.putInt("size", sizeIndex)?.apply() }
    fun changeColor(c: Int) { color = c; prefs()?.edit()?.putInt("color", c)?.apply() }
    fun changeStyle(v: Int) { style = v.coerceIn(0, TapeStyle.all.size - 1); prefs()?.edit()?.putInt("style", style)?.apply() }

    /** يفعّل الأداة ويطفئ الأدوات الوسطى والتحديد والأشكال. */
    fun turnOn() {
        load()
        val c = AppController
        // الممحاة والليزر يسبقان أدوات الصفحة: يُرجع إلى آخر قلم (اختيار الأداة يطفئ الشريط، فيُفعّل بعده)
        if (!(c.tool.isPen() || c.tool == Tool.HIGHLIGHTER)) c.selectTool(c.lastPen)
        AppController.lassoActive = false
        PenPrefs.shapeActive = false
        PenPrefs.textActive = false
        PenPrefs.stickyActive = false
        AppController.penBarOpen = false
        active = true
    }

    /**
     * مسار الشريط من نقاط السحب: الشريط المستقيم من البداية إلى النهاية (يستقيم أفقيًا أو عموديًا إن كان قريبًا منهما)،
     * والحر بنقاط متباعدة قليلًا.
     */
    fun pathOf(raw: List<Offset>, w: Float): List<Float> {
        if (raw.size < 2) return emptyList()
        if (straight) {
            val a = raw.first()
            var b = raw.last()
            val ang = Math.toDegrees(atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())).toFloat()
            val m = ((ang % 180f) + 180f) % 180f
            if (m < 6f || m > 174f) b = Offset(b.x, a.y)
            else if (abs(m - 90f) < 6f) b = Offset(a.x, b.y)
            return listOf(a.x, a.y, b.x, b.y)
        }
        val out = ArrayList<Float>()
        var last: Offset? = null
        raw.forEachIndexed { i, p ->
            if (last == null || i == raw.lastIndex || hypot(p.x - last!!.x, p.y - last!!.y) >= w * 0.35f) {
                out.add(p.x); out.add(p.y); last = p
            }
        }
        return out
    }
}

/** طول مسار الشريط (نسبةً لعرض الصفحة). */
fun Tape.length(): Float {
    var s = 0f
    var i = 2
    while (i + 1 < pts.size) {
        s += hypot(pts[i] - pts[i - 2], pts[i + 1] - pts[i - 1])
        i += 2
    }
    return s
}

/** هل النقطة (بإحداثيات الصفحة) فوق الشريط؟ slack = سماحة إضافية. */
fun Tape.hits(x: Float, y: Float, slack: Float = 0.006f): Boolean {
    val r = width / 2f + slack
    var i = 2
    while (i + 1 < pts.size) {
        if (segDist(x, y, pts[i - 2], pts[i - 1], pts[i], pts[i + 1]) <= r) return true
        i += 2
    }
    return false
}

private fun segDist(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax
    val dy = by - ay
    val l2 = dx * dx + dy * dy
    val t = if (l2 <= 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / l2).coerceIn(0f, 1f)
    return hypot(px - (ax + t * dx), py - (ay + t * dy))
}

// ---------------------------------------------------------------- الجلسة

/** يضيف شريطًا (يُسجَّل في التراجع). */
fun DocSession.addTape(t: Tape) {
    beginEdit()
    tapes = tapes + (t.page to ((tapes[t.page] ?: emptyList()) + t))
    endEdit()
}

/** نقرة على شريط: تكشف ما تحته أو تعيد إخفاءه (لا تُسجَّل في التراجع). يعيد true إن أصاب شريطًا. */
fun DocSession.toggleTapeAt(page: Int, x: Float, y: Float): Boolean {
    val list = tapes[page] ?: return false
    val hit = list.lastOrNull { it.hits(x, y) } ?: return false
    tapes = tapes + (page to list.map { if (it.id == hit.id) it.copy(revealed = !it.revealed) else it })
    persist()
    return true
}

/** يكشف كل أشرطة الصفحة أو يخفيها. */
fun DocSession.revealAllTapes(page: Int, reveal: Boolean) {
    val list = tapes[page] ?: return
    if (list.isEmpty()) return
    tapes = tapes + (page to list.map { it.copy(revealed = reveal) })
    persist()
}

/** يزيل كل أشرطة الصفحة (يمكن التراجع عنه). */
fun DocSession.removeAllTapes(page: Int) {
    if (tapes[page].isNullOrEmpty()) return
    beginEdit()
    tapes = tapes - page
    endEdit()
}

/** الممحاة تزيل الشريط الذي تلمسه كاملًا (داخل beginEdit/endEdit الممحاة). */
fun DocSession.eraseTapesAt(page: Int, at: Offset, radius: Float) {
    val list = tapes[page] ?: return
    val next = list.filterNot { it.hits(at.x, at.y, radius) }
    if (next.size != list.size) tapes = tapes + (page to next)
}

// ---------------------------------------------------------------- الحفظ (JSON)

fun tapesJson(tapes: Map<Int, List<Tape>>): JSONArray = JSONArray().apply {
    tapes.values.forEach { list ->
        list.forEach { t ->
            put(
                JSONObject().put("id", t.id).put("page", t.page)
                    .put("p", JSONArray().apply { t.pts.forEach { put(it.toDouble()) } })
                    .put("w", t.width.toDouble()).put("c", t.color)
                    .put("st", t.style)
                    .apply { if (t.revealed) put("rv", true) }
            )
        }
    }
}

fun parseTapes(a: JSONArray?): Map<Int, List<Tape>> {
    if (a == null) return emptyMap()
    val all = ArrayList<Tape>()
    for (i in 0 until a.length()) {
        try {
            val o = a.getJSONObject(i)
            val pa = o.getJSONArray("p")
            val pts = (0 until pa.length()).map { pa.getDouble(it).toFloat() }
            if (pts.size < 4 || pts.size % 2 != 0) continue
            all.add(
                Tape(
                    id = o.optString("id", UUID.randomUUID().toString()), page = o.getInt("page"),
                    pts = pts, width = o.optDouble("w", 0.028).toFloat().coerceIn(0.004f, 0.2f),
                    color = o.optInt("c", 0xFFF2A65A.toInt()), revealed = o.optBoolean("rv", false),
                    style = o.optInt("st", TapeStyle.SOFT).coerceIn(0, TapeStyle.all.size - 1)
                )
            )
        } catch (e: Throwable) {
        }
    }
    return all.groupBy { it.page }
}

// ---------------------------------------------------------------- الرسم

/**
 * حدود الشريط مضلعًا مغلقًا (x, y بالبكسل): جانبا الشريط على بعد نصف عرضه من مساره، وطرفاه بشكل نوعه
 * (متعرّج للواشي، ممزّق للورقي، مائل للمنقّط، مستدير للناعم). نفس الحساب في الآيباد.
 */
internal fun tapeOutline(t: Tape, w: Float): FloatArray {
    val n = t.pts.size / 2
    if (n < 2) return FloatArray(0)
    val xs = FloatArray(n) { t.pts[it * 2] * w }
    val ys = FloatArray(n) { t.pts[it * 2 + 1] * w }
    val h = t.width * w / 2f
    // اتجاه كل قطعة
    fun dir(i: Int): Pair<Float, Float> {
        val dx = xs[i + 1] - xs[i]; val dy = ys[i + 1] - ys[i]
        val l = hypot(dx, dy).coerceAtLeast(0.0001f)
        return dx / l to dy / l
    }
    val left = ArrayList<Float>(); val right = ArrayList<Float>()
    for (i in 0 until n) {
        val (ax, ay) = dir((i - 1).coerceAtLeast(0))
        val (bx, by) = dir(i.coerceAtMost(n - 2))
        var nx = -(ay + by); var ny = ax + bx
        val nl = hypot(nx, ny)
        if (nl < 0.001f) { nx = -by; ny = bx } else { nx /= nl; ny /= nl }
        // تعويض الزاوية حتى يبقى العرض ثابتًا (بحد أقصى)
        val cos = (nx * -by + ny * bx).coerceIn(0.5f, 1f)
        val k = h / cos
        left.add(xs[i] + nx * k); left.add(ys[i] + ny * k)
        right.add(xs[i] - nx * k); right.add(ys[i] - ny * k)
    }
    val rnd = java.util.Random(tapeSeed(t))
    // طرف عند نقطة p باتجاه للخارج (ox, oy)، من الجانب الأيسر a إلى الأيمن b
    fun cap(out: ArrayList<Float>, ax: Float, ay: Float, bx: Float, by: Float, ox: Float, oy: Float) {
        val tw = 2f * h
        when (t.style) {
            TapeStyle.WASHI -> {
                val teeth = 4
                for (j in 1 until teeth * 2) {
                    val f = j / (teeth * 2f)
                    val d = if (j % 2 == 1) -tw * 0.16f else 0f
                    out.add(ax + (bx - ax) * f + ox * d); out.add(ay + (by - ay) * f + oy * d)
                }
            }
            TapeStyle.TORN -> {
                val steps = 7
                for (j in 1 until steps) {
                    val f = j / steps.toFloat()
                    val d = -rnd.nextFloat() * tw * 0.24f
                    out.add(ax + (bx - ax) * f + ox * d); out.add(ay + (by - ay) * f + oy * d)
                }
            }
            TapeStyle.SOFT -> {
                val cx = (ax + bx) / 2f; val cy = (ay + by) / 2f
                val rx = ax - cx; val ry = ay - cy
                for (j in 1 until 10) {
                    val th = Math.PI * j / 10.0
                    val c = kotlin.math.cos(th).toFloat(); val sn = kotlin.math.sin(th).toFloat()
                    out.add(cx + rx * c + ox * h * sn); out.add(cy + ry * c + oy * h * sn)
                }
            }
            else -> {}
        }
    }
    val (sx, sy) = dir(0); val (ex, ey) = dir(n - 2)
    // الطرف المائل للمنقّط: يُزاح جانب للأمام وجانب للخلف
    val slant = if (t.style == TapeStyle.DOTS) h * 0.35f else 0f
    // تقصير الطرف المستدير حتى يبقى طول الشريط كما رُسم
    val inset = if (t.style == TapeStyle.SOFT) h else 0f
    fun shift(list: ArrayList<Float>, idx: Int, dx: Float, dy: Float) { list[idx] += dx; list[idx + 1] += dy }
    shift(left, 0, sx * (inset - slant), sy * (inset - slant)); shift(right, 0, sx * (inset + slant), sy * (inset + slant))
    val li = (n - 1) * 2
    shift(left, li, -ex * (inset - slant), -ey * (inset - slant)); shift(right, li, -ex * (inset + slant), -ey * (inset + slant))
    val out = ArrayList<Float>()
    for (i in 0 until n) { out.add(left[i * 2]); out.add(left[i * 2 + 1]) }
    cap(out, left[li], left[li + 1], right[li], right[li + 1], ex, ey)
    for (i in n - 1 downTo 0) { out.add(right[i * 2]); out.add(right[i * 2 + 1]) }
    cap(out, right[0], right[1], left[0], left[1], -sx, -sy)
    return out.toFloatArray()
}

/** بذرة ثابتة للحواف الممزّقة والملمس: من نقطة بداية الشريط (لا تتغير بين المسودة والشريط المحفوظ). */
private fun tapeSeed(t: Tape): Long = (t.pts[0] * 100000f).toLong() * 31L + (t.pts[1] * 100000f).toLong()

/** درجة أفتح من اللون (f من 0 إلى 1). */
private fun lighter(c: Int, f: Float): Int {
    val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
    fun m(v: Int) = (v + (255 - v) * f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (m(r) shl 16) or (m(g) shl 8) or m(b)
}

/** درجة أغمق من اللون. */
private fun darker(c: Int, f: Float): Int {
    val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
    fun m(v: Int) = (v * (1f - f)).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (m(r) shl 16) or (m(g) shl 8) or m(b)
}

/**
 * يرسم الشريط على Canvas أندرويد (للشاشة وللتصدير معًا): المغطّي بشكله ونقشه،
 * والمكشوف حوافه فقط بتعبئة شفافة خفيفة جدًا، بدون أي خط في وسطه.
 */
fun drawTapeNative(c: android.graphics.Canvas, t: Tape, w: Float) {
    val o = tapeOutline(t, w)
    if (o.size < 6) return
    val path = android.graphics.Path().apply {
        moveTo(o[0], o[1])
        var i = 2
        while (i + 1 < o.size) { lineTo(o[i], o[i + 1]); i += 2 }
        close()
    }
    val base = t.color or (0xFF shl 24)
    val tw = t.width * w
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    if (t.revealed) {
        p.style = android.graphics.Paint.Style.FILL
        p.color = (base and 0x00FFFFFF) or (0x1A shl 24)
        c.drawPath(path, p)
        p.style = android.graphics.Paint.Style.STROKE
        p.strokeJoin = android.graphics.Paint.Join.ROUND
        p.strokeWidth = maxOf(1.2f, tw * 0.07f)
        p.color = darker(base, 0.12f)
        c.drawPath(path, p)
        return
    }
    if (t.style == TapeStyle.SOFT) {
        // ظل خفيف جدًا تحت الشريط
        p.style = android.graphics.Paint.Style.FILL
        p.color = android.graphics.Color.argb(30, 0, 0, 0)
        c.save(); c.translate(0f, maxOf(1f, tw * 0.06f)); c.drawPath(path, p); c.restore()
    }
    p.style = android.graphics.Paint.Style.FILL
    p.color = base
    c.drawPath(path, p)
    val b = android.graphics.RectF().also { path.computeBounds(it, true) }
    when (t.style) {
        TapeStyle.WASHI -> {
            c.save(); c.clipPath(path)
            p.color = lighter(base, 0.2f)
            p.style = android.graphics.Paint.Style.STROKE
            p.strokeWidth = tw * 0.16f
            val step = tw * 0.36f
            var x = b.left - b.height()
            while (x < b.right + b.height()) {
                c.drawLine(x, b.bottom, x + b.height(), b.top, p); x += step
            }
            c.restore()
        }
        TapeStyle.DOTS -> {
            c.save(); c.clipPath(path)
            p.color = lighter(base, 0.45f)
            val step = tw * 0.32f
            val r = tw * 0.06f
            var row = 0
            var y = b.top + step / 2f
            while (y < b.bottom + step) {
                var x = b.left + (if (row % 2 == 1) step / 2f else 0f)
                while (x < b.right + step) { c.drawCircle(x, y, r, p); x += step }
                y += step; row++
            }
            c.restore()
        }
        TapeStyle.TORN -> {
            // ملمس ورق: ألياف قصيرة فاتحة وداكنة قليلًا
            c.save(); c.clipPath(path)
            val rnd = java.util.Random(tapeSeed(t) * 31)
            p.style = android.graphics.Paint.Style.STROKE
            p.strokeWidth = maxOf(0.6f, tw * 0.025f)
            p.strokeCap = android.graphics.Paint.Cap.ROUND
            val count = ((b.width() * b.height()) / (tw * tw) * 9).toInt().coerceIn(6, 400)
            repeat(count) {
                val x = b.left + rnd.nextFloat() * b.width(); val y = b.top + rnd.nextFloat() * b.height()
                val a = rnd.nextFloat() * 6.283f; val l = tw * (0.08f + rnd.nextFloat() * 0.14f)
                p.color = if (rnd.nextBoolean()) android.graphics.Color.argb(46, 255, 255, 255) else android.graphics.Color.argb(16, 0, 0, 0)
                c.drawLine(x, y, x + kotlin.math.cos(a) * l, y + kotlin.math.sin(a) * l, p)
            }
            c.restore()
        }
        else -> {}
    }
}

/** طبقة الأشرطة فوق الكتابة والملاحظات في صفحة. */
@Composable
fun TapeLayer(session: DocSession, page: Int, modifier: Modifier = Modifier) {
    val list = session.tapes[page]
    val draft = session.tapeDraft?.takeIf { it.page == page }
    if (list.isNullOrEmpty() && draft == null) return
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        drawIntoCanvas { cv ->
            list?.forEach { drawTapeNative(cv.nativeCanvas, it, w) }
            draft?.let { drawTapeNative(cv.nativeCanvas, it, w) }
        }
    }
}

/** يرسم الأشرطة المغطّية على صورة صفحة مصدَّرة (المكشوفة لا تُرسم). */
fun drawTapesOn(c: android.graphics.Canvas, tapes: List<Tape>, w: Float) {
    for (t in tapes) {
        if (t.revealed || t.pts.size < 4) continue
        drawTapeNative(c, t, w)
    }
}

// ---------------------------------------------------------------- شريط الأداة

/** شريط أداة الشريط اللاصق: السماكة، اللون، الشريط المستقيم، الكشف والإخفاء، والإزالة من الصفحة. */
@Composable
fun TapeBar(s: DocSession) {
    val pill = BarShape
    val page = s.currentPage()
    Box(Modifier.fillMaxWidth().padding(start = 64.dp, end = 64.dp, top = 10.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.shadow(6.dp, pill).background(Color.White, pill).border(1.dp, Palette.Divider, pill).clip(pill).blockTouches()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // الأشكال الأربعة، بلون الشريط الحالي
                TapeStyle.all.forEach { st ->
                    val sel = TapeTool.style == st
                    TBtn(selected = sel, onClick = { TapeTool.changeStyle(st) }) {
                        TapeStylePreview(st, TapeTool.color, TapeStyle.name(st))
                    }
                }
                ToolbarSep()
                // السماكات الثلاث
                TapeTool.widths.indices.forEach { i ->
                    val sel = TapeTool.sizeIndex == i
                    TBtn(selected = sel, onClick = { TapeTool.changeSize(i) }) {
                        Box(
                            Modifier.width(22.dp).height((5 + i * 4).dp).clip(RoundedCornerShape(2.dp))
                                .background(if (sel) Color(TapeTool.color) else Palette.TextSecondary.copy(alpha = 0.55f))
                        )
                    }
                }
                ToolbarSep()
                TapeTool.palette.forEach { col ->
                    val sel = col == TapeTool.color
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).clickable { TapeTool.changeColor(col) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.size(if (sel) 26.dp else 22.dp).clip(CircleShape).background(Color(col))
                                .border(if (sel) 2.5.dp else 1.dp, if (sel) Palette.Navy else Palette.Divider, CircleShape)
                        )
                    }
                }
                ToolbarSep()
                TapeChip(L("شريط مستقيم", "Straight tape"), TapeTool.straight) { TapeTool.changeStraight(!TapeTool.straight) }
                Spacer(Modifier.width(6.dp))
                val list = s.tapes[page] ?: emptyList()
                val anyHidden = list.any { !it.revealed }
                TBtn(onClick = { s.revealAllTapes(page, anyHidden) }) {
                    PhIcon(R.drawable.ph_eye, Palette.TextSecondary, 20.dp,
                        if (anyHidden) L("كشف كل أشرطة الصفحة", "Reveal all tape on the page") else L("إخفاء كل أشرطة الصفحة", "Hide all tape on the page"))
                }
                TBtn(onClick = { s.removeAllTapes(page) }) {
                    PhIcon(R.drawable.ph_trash, Palette.Danger, 20.dp, L("إزالة كل الأشرطة من الصفحة", "Remove all tape from the page"))
                }
                ToolbarSep()
                TBtn(onClick = { TapeTool.active = false }) {
                    PhIcon(R.drawable.ph_close, Palette.TextSecondary, 20.dp, L("إغلاق الشريط اللاصق", "Close tape"))
                }
            }
        }
    }
}

/** معاينة صغيرة لشكل الشريط. */
@Composable
private fun TapeStylePreview(style: Int, color: Int, label: String) {
    Canvas(
        Modifier.width(30.dp).height(16.dp)
            .semantics { contentDescription = label }
    ) {
        val w = size.width
        val y = size.height / 2f / w
        drawIntoCanvas { cv ->
            drawTapeNative(cv.nativeCanvas, Tape("preview", 0, listOf(0.1f, y, 0.9f, y), size.height * 0.8f / w, color, style = style), w)
        }
    }
}

@Composable
private fun TapeChip(label: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.clip(shape).background(if (on) Palette.Navy else Palette.Background)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (on) {
            PhIcon(R.drawable.ph_check, Color.White, 14.dp)
            Spacer(Modifier.width(4.dp))
        }
        Text(label, fontSize = 12.sp, color = if (on) Color.White else Palette.TextPrimary, maxLines = 1)
    }
}

/** يبدأ شريطًا جديدًا بإعدادات الأداة الحالية. */
fun newTape(page: Int, pts: List<Float>): Tape =
    Tape(UUID.randomUUID().toString(), page, pts, TapeTool.width(), TapeTool.color, style = TapeTool.style)
