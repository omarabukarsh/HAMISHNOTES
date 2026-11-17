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
    val revealed: Boolean = false
)

/** إعدادات أداة الشريط اللاصق. */
object TapeTool {
    /** أداة الشريط فعّالة: سحب القلم يضع شريطًا، والنقرة تكشفه أو تخفيه. */
    var active by mutableStateOf(false)
    var straight by mutableStateOf(true); private set
    var sizeIndex by mutableStateOf(1); private set
    var color by mutableStateOf(0xFFF2A65A.toInt()); private set

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
                    color = o.optInt("c", 0xFFF2A65A.toInt()), revealed = o.optBoolean("rv", false)
                )
            )
        } catch (e: Throwable) {
        }
    }
    return all.groupBy { it.page }
}

// ---------------------------------------------------------------- الرسم

private fun tapePath(t: Tape, w: Float): Path = Path().apply {
    moveTo(t.pts[0] * w, t.pts[1] * w)
    var i = 2
    while (i + 1 < t.pts.size) {
        lineTo(t.pts[i] * w, t.pts[i + 1] * w)
        i += 2
    }
}

/** يرسم شريطًا: مغطٍّ بلونه ونقشه، أو مكشوف (شفاف بخط متقطع). */
fun DrawScope.drawTape(t: Tape, w: Float) {
    if (t.pts.size < 4) return
    val path = tapePath(t, w)
    val tw = t.width * w
    val col = Color(t.color)
    if (!t.revealed) {
        drawPath(path, col.copy(alpha = 1f), style = Stroke(tw, cap = StrokeCap.Butt, join = StrokeJoin.Round))
        drawPath(
            path, Color.White.copy(alpha = 0.22f),
            style = Stroke(tw * 0.62f, cap = StrokeCap.Butt, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(tw * 0.28f, tw * 0.28f)))
        )
    } else {
        drawPath(path, col.copy(alpha = 0.16f), style = Stroke(tw, cap = StrokeCap.Butt, join = StrokeJoin.Round))
        drawPath(
            path, col.copy(alpha = 0.7f),
            style = Stroke(maxOf(1f, tw * 0.06f), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(tw * 0.3f, tw * 0.22f)))
        )
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
        list?.forEach { drawTape(it, w) }
        draft?.let { drawTape(it, w) }
    }
}

/** يرسم الأشرطة المغطّية على صورة صفحة مصدَّرة (المكشوفة لا تُرسم). */
fun drawTapesOn(c: android.graphics.Canvas, tapes: List<Tape>, w: Float) {
    for (t in tapes) {
        if (t.revealed || t.pts.size < 4) continue
        val path = android.graphics.Path().apply {
            moveTo(t.pts[0] * w, t.pts[1] * w)
            var i = 2
            while (i + 1 < t.pts.size) { lineTo(t.pts[i] * w, t.pts[i + 1] * w); i += 2 }
        }
        val tw = t.width * w
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.BUTT
            strokeJoin = android.graphics.Paint.Join.ROUND
            strokeWidth = tw
            color = t.color or (0xFF shl 24)
        }
        c.drawPath(path, p)
        p.color = android.graphics.Color.argb(56, 255, 255, 255)
        p.strokeWidth = tw * 0.62f
        p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(tw * 0.28f, tw * 0.28f), 0f)
        c.drawPath(path, p)
    }
}

// ---------------------------------------------------------------- شريط الأداة

/** شريط أداة الشريط اللاصق: السماكة، اللون، الشريط المستقيم، الكشف والإخفاء، والإزالة من الصفحة. */
@Composable
fun TapeBar(s: DocSession) {
    val pill = RoundedCornerShape(26.dp)
    val page = s.currentPage()
    Box(Modifier.fillMaxWidth().padding(start = 64.dp, end = 64.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.shadow(6.dp, pill).background(Color.White, pill).border(1.dp, Palette.Divider, pill).clip(pill)) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
    Tape(UUID.randomUUID().toString(), page, pts, TapeTool.width(), TapeTool.color)
