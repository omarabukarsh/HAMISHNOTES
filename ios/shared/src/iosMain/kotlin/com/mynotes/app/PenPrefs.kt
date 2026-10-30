package com.mynotes.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * إعدادات الأقلام والممحاة والتحديد والأشكال (تُحفظ في الجهاز).
 */
object PenPrefs {
    private lateinit var sp: SharedPreferences

    // ---- القلم ----
    var lineStyle by mutableStateOf(LineStyle.SOLID); private set
    /** حدة الطرف 0..1: تدبيب بداية ونهاية خط قلم الحبر والفرشاة. */
    var sharpness by mutableStateOf(0.25f); private set
    /** حساسية الضغط 0..1 (0.5 = الافتراضي). */
    var pressure by mutableStateOf(0.5f); private set
    /** تثبيت الخط 0..1: تنعيم ارتعاش اليد أثناء الكتابة. */
    var stabilize by mutableStateOf(0f); private set
    /** ارسم ثم أبقِ القلم ثابتًا لحظة: يتحول الرسم إلى شكل مرتب. */
    var drawAndHold by mutableStateOf(true); private set

    // ---- الهايلايتر ----
    var hlStabilize by mutableStateOf(0.3f); private set
    /** الهايلايتر يرسم خطًا مستقيمًا من بداية السحب إلى نهايته. */
    var hlStraight by mutableStateOf(true); private set

    // ---- الممحاة ----
    var erasePen by mutableStateOf(true); private set
    var erasePencil by mutableStateOf(true); private set
    var eraseHighlighter by mutableStateOf(true); private set
    var eraseShapes by mutableStateOf(true); private set
    /** بعد المسح يعود إلى آخر قلم تلقائيًا. */
    var autoDeselect by mutableStateOf(false); private set

    // ---- التحديد ----
    var lassoRect by mutableStateOf(false); private set
    var lassoInk by mutableStateOf(true); private set
    var lassoHighlight by mutableStateOf(true); private set
    var lassoShapes by mutableStateOf(true); private set
    var lassoImages by mutableStateOf(true); private set
    var lassoTexts by mutableStateOf(true); private set
    var lassoStickies by mutableStateOf(true); private set

    // ---- الأشكال ----
    /** أداة الأشكال فعّالة. */
    var shapeActive by mutableStateOf(false)
    var shapeKind by mutableStateOf(ShapeKind.RECT); private set
    var shapeStyle by mutableStateOf(LineStyle.SOLID); private set
    var shapeSize by mutableStateOf(2); private set
    var shapeRounded by mutableStateOf(true); private set
    var arrowStart by mutableStateOf(ArrowHead.NONE); private set
    var arrowEnd by mutableStateOf(ArrowHead.NONE); private set
    var shapeColor by mutableStateOf(0xFF000000.toInt()); private set

    // ---- الملاحظات اللاصقة ----
    /** وضع إضافة ملاحظة: النقرة التالية على الصفحة تضع ملاحظة. */
    var stickyActive by mutableStateOf(false)
    var stickyColor by mutableStateOf(0xFFF6B3B5.toInt()); private set
    var stickyStyle by mutableStateOf(0); private set
    /** الاسم الذي يظهر أسفل الملاحظات. */
    var author by mutableStateOf(""); private set

    fun changeStickyColor(v: Int) { stickyColor = v; put("sticky_color", v) }
    fun changeStickyStyle(v: Int) { stickyStyle = v; put("sticky_style", v) }
    fun changeAuthor(v: String) { author = v; put("author", v) }

    /** آخر نوع قلم (حبر/جاف/فرشاة) يظهر في زر القلم. */
    var inkPen by mutableStateOf(Tool.FOUNTAIN)
        private set

    fun noteInkPen(t: Tool) {
        if (t == Tool.FOUNTAIN || t == Tool.BALLPOINT || t == Tool.BRUSH) { inkPen = t; put("ink_pen", t.name) }
    }

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("mynotes_pens", Context.MODE_PRIVATE)
        stickyColor = sp.getInt("sticky_color", 0xFFF6B3B5.toInt())
        stickyStyle = sp.getInt("sticky_style", 0)
        author = sp.getString("author", "") ?: ""
        inkPen = try { Tool.valueOf(sp.getString("ink_pen", "FOUNTAIN")!!) } catch (t: Throwable) { Tool.FOUNTAIN }
        lineStyle = sp.getInt("line_style", 0)
        sharpness = sp.getFloat("sharpness", 0.25f)
        pressure = sp.getFloat("pressure", 0.5f)
        stabilize = sp.getFloat("stabilize", 0f)
        drawAndHold = sp.getBoolean("draw_hold", true)
        hlStabilize = sp.getFloat("hl_stabilize", 0.3f)
        hlStraight = sp.getBoolean("hl_straight", true)
        erasePen = sp.getBoolean("er_pen", true)
        erasePencil = sp.getBoolean("er_pencil", true)
        eraseHighlighter = sp.getBoolean("er_hl", true)
        eraseShapes = sp.getBoolean("er_shapes", true)
        autoDeselect = sp.getBoolean("auto_deselect", false)
        lassoRect = sp.getBoolean("lasso_rect", false)
        lassoInk = sp.getBoolean("ls_ink", true)
        lassoHighlight = sp.getBoolean("ls_hl", true)
        lassoShapes = sp.getBoolean("ls_shapes", true)
        lassoImages = sp.getBoolean("ls_img", true)
        lassoTexts = sp.getBoolean("ls_txt", true)
        lassoStickies = sp.getBoolean("ls_sticky", true)
        shapeKind = try { ShapeKind.valueOf(sp.getString("shape_kind", "RECT")!!) } catch (t: Throwable) { ShapeKind.RECT }
        shapeStyle = sp.getInt("shape_style", 0)
        shapeSize = sp.getInt("shape_size", 2)
        shapeRounded = sp.getBoolean("shape_round", true)
        arrowStart = try { ArrowHead.valueOf(sp.getString("arrow_s", "NONE")!!) } catch (t: Throwable) { ArrowHead.NONE }
        arrowEnd = try { ArrowHead.valueOf(sp.getString("arrow_e", "NONE")!!) } catch (t: Throwable) { ArrowHead.NONE }
        shapeColor = sp.getInt("shape_color", 0xFF000000.toInt())
    }

    private fun put(k: String, v: Any) {
        if (!::sp.isInitialized) return
        val e = sp.edit()
        when (v) {
            is Boolean -> e.putBoolean(k, v)
            is Int -> e.putInt(k, v)
            is Float -> e.putFloat(k, v)
            is String -> e.putString(k, v)
        }
        e.apply()
    }

    fun changeLineStyle(v: Int) { lineStyle = v; put("line_style", v) }
    fun changeSharpness(v: Float) { sharpness = v; put("sharpness", v) }
    fun changePressure(v: Float) { pressure = v; put("pressure", v) }
    fun changeStabilize(v: Float) { stabilize = v; put("stabilize", v) }
    fun changeDrawAndHold(v: Boolean) { drawAndHold = v; put("draw_hold", v) }
    fun resetPen() {
        changeLineStyle(0); changeSharpness(0.25f); changePressure(0.5f); changeStabilize(0f)
    }

    fun changeHlStabilize(v: Float) { hlStabilize = v; put("hl_stabilize", v) }
    fun changeHlStraight(v: Boolean) { hlStraight = v; put("hl_straight", v) }

    /** التثبيت حسب الأداة. */
    fun stabilizeFor(t: Tool): Float = if (t == Tool.HIGHLIGHTER) hlStabilize else stabilize

    /** خط الهايلايتر المستقيم: من أول نقطة إلى آخر نقطة. */
    fun straighten(pts: List<Pt>): List<Pt> {
        if (pts.size < 2) return pts
        val a = pts.first(); val b = pts.last()
        val n = 12
        return (0..n).map { i -> val f = i / n.toFloat(); Pt(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f, 0.5f) }
    }

    // ---- الأحجام الثلاثة السريعة لكل أداة ----
    private val slotState = androidx.compose.runtime.mutableStateMapOf<Tool, List<Int>>()

    fun slots(t: Tool): List<Int> = slotState[t] ?: run {
        val def = if (t == Tool.HIGHLIGHTER) listOf(2, 4, 7) else listOf(1, 3, 6)
        val saved = if (::sp.isInitialized) sp.getString("slots_" + t.name, null) else null
        val l = saved?.split(",")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 3 } ?: def
        slotState[t] = l
        l
    }

    fun setSlot(t: Tool, i: Int, size: Int) {
        val l = slots(t).toMutableList()
        l[i] = size.coerceIn(0, Sizes.COUNT - 1)
        slotState[t] = l
        put("slots_" + t.name, l.joinToString(","))
    }

    fun changeErasePen(v: Boolean) { erasePen = v; put("er_pen", v) }
    fun changeErasePencil(v: Boolean) { erasePencil = v; put("er_pencil", v) }
    fun changeEraseHighlighter(v: Boolean) { eraseHighlighter = v; put("er_hl", v) }
    fun changeEraseShapes(v: Boolean) { eraseShapes = v; put("er_shapes", v) }
    fun changeAutoDeselect(v: Boolean) { autoDeselect = v; put("auto_deselect", v) }

    fun changeLassoRect(v: Boolean) { lassoRect = v; put("lasso_rect", v) }
    fun changeLassoInk(v: Boolean) { lassoInk = v; put("ls_ink", v) }
    fun changeLassoHighlight(v: Boolean) { lassoHighlight = v; put("ls_hl", v) }
    fun changeLassoShapes(v: Boolean) { lassoShapes = v; put("ls_shapes", v) }
    fun changeLassoImages(v: Boolean) { lassoImages = v; put("ls_img", v) }
    fun changeLassoTexts(v: Boolean) { lassoTexts = v; put("ls_txt", v) }
    fun changeLassoStickies(v: Boolean) { lassoStickies = v; put("ls_sticky", v) }

    fun changeShapeKind(v: ShapeKind) { shapeKind = v; put("shape_kind", v.name) }
    fun changeShapeStyle(v: Int) { shapeStyle = v; put("shape_style", v) }
    fun changeShapeSize(v: Int) { shapeSize = v; put("shape_size", v) }
    fun changeShapeRounded(v: Boolean) { shapeRounded = v; put("shape_round", v) }
    fun changeArrowStart(v: ArrowHead) { arrowStart = v; put("arrow_s", v.name) }
    fun changeArrowEnd(v: ArrowHead) { arrowEnd = v; put("arrow_e", v.name) }
    fun changeShapeColor(v: Int) { shapeColor = v; put("shape_color", v) }
    fun resetShape() {
        changeShapeStyle(0); changeShapeSize(2); changeShapeRounded(true); changeArrowStart(ArrowHead.NONE); changeArrowEnd(ArrowHead.NONE)
    }

    /** هل تمسح الممحاة هذا الخط حسب «فلتر الممحاة». */
    fun erasable(s: Ink): Boolean = when {
        s.shape -> eraseShapes
        s.tool == Tool.HIGHLIGHTER -> eraseHighlighter
        s.tool == Tool.PENCIL -> erasePencil
        else -> erasePen
    }

    /** هل يدخل هذا الخط في التحديد الحر. */
    fun lassoable(s: Ink): Boolean = when {
        s.shape -> lassoShapes
        s.tool == Tool.HIGHLIGHTER -> lassoHighlight
        else -> lassoInk
    }

    /** عرض الخط بالمليمتر تقريبًا (الصفحة A4 عرضها 210 مم). */
    fun mm(width: Float): String = "%.2f mm".format(java.util.Locale.US, width * 210f)

    // ---------- معالجة الكتابة ----------

    /** الضغط بعد تطبيق حساسية الضغط. */
    fun applyPressure(p: Float): Float {
        val k = pressure * 2f // 0..2
        return (0.55f + (p - 0.55f) * k).coerceIn(0.05f, 1f)
    }

    /** تدبيب طرفي الخط حسب حدة الطرف (لقلم الحبر والفرشاة). */
    fun taper(pts: List<Pt>, tool: Tool): List<Pt> {
        if (sharpness <= 0.01f || pts.size < 4 || !(tool == Tool.FOUNTAIN || tool == Tool.BRUSH)) return pts
        var total = 0f
        for (i in 1 until pts.size) total += hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
        if (total <= 0f) return pts
        val span = min(total * 0.3f, 0.02f + sharpness * 0.03f)
        val out = ArrayList<Pt>(pts.size)
        var d = 0f
        for (i in pts.indices) {
            if (i > 0) d += hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
            val fromEnd = total - d
            val f = min(1f, min(d, fromEnd) / span)
            val k = (1f - sharpness) + sharpness * f
            out.add(Pt(pts[i].x, pts[i].y, pts[i].p * k))
        }
        return out
    }
}

/** بناء نقاط الأشكال من نقطة البداية a إلى النهاية b. كل قائمة = خط مستقل. */
object ShapeBuilder {
    private fun p(x: Float, y: Float) = Pt(x, y, 0.5f)

    fun build(kind: ShapeKind, a: Pt, b: Pt, rounded: Boolean, start: ArrowHead, end: ArrowHead, width: Float): List<List<Pt>> {
        val l = min(a.x, b.x); val r = maxOf(a.x, b.x)
        val t = min(a.y, b.y); val bt = maxOf(a.y, b.y)
        val cx = (l + r) / 2f; val cy = (t + bt) / 2f
        val body: List<Pt> = when (kind) {
            ShapeKind.LINE -> listOf(p(a.x, a.y), p(b.x, b.y))
            ShapeKind.ELBOW -> {
                val corner = if (abs(b.x - a.x) >= abs(b.y - a.y)) p(b.x, a.y) else p(a.x, b.y)
                val raw = listOf(p(a.x, a.y), corner, p(b.x, b.y))
                if (rounded) roundCorners(raw, false) else raw
            }
            ShapeKind.CURVE -> {
                // منحنى S بين النقطتين
                val out = ArrayList<Pt>()
                val c1 = p(a.x + (b.x - a.x) * 0.5f, a.y)
                val c2 = p(a.x + (b.x - a.x) * 0.5f, b.y)
                for (i in 0..32) {
                    val f = i / 32f; val g = 1 - f
                    out.add(p(
                        g * g * g * a.x + 3 * g * g * f * c1.x + 3 * g * f * f * c2.x + f * f * f * b.x,
                        g * g * g * a.y + 3 * g * g * f * c1.y + 3 * g * f * f * c2.y + f * f * f * b.y
                    ))
                }
                out
            }
            ShapeKind.RECT -> closed(listOf(p(l, t), p(r, t), p(r, bt), p(l, bt)), rounded)
            ShapeKind.TRIANGLE -> closed(listOf(p(cx, t), p(r, bt), p(l, bt)), rounded)
            ShapeKind.DIAMOND -> closed(listOf(p(cx, t), p(r, cy), p(cx, bt), p(l, cy)), rounded)
            ShapeKind.CIRCLE -> {
                val rad = min(r - l, bt - t) / 2f
                // الدائرة تبدأ من نقطة البداية باتجاه السحب
                val ox = a.x + (if (b.x >= a.x) rad else -rad)
                val oy = a.y + (if (b.y >= a.y) rad else -rad)
                ellipse(ox, oy, rad, rad)
            }
            ShapeKind.ELLIPSE -> ellipse(cx, cy, (r - l) / 2f, (bt - t) / 2f)
        }
        val out = arrayListOf(body)
        if (kind == ShapeKind.LINE || kind == ShapeKind.ELBOW || kind == ShapeKind.CURVE) {
            if (body.size >= 2) {
                if (end != ArrowHead.NONE) out.add(head(body[body.size - 2], body.last(), end, width))
                if (start != ArrowHead.NONE) out.add(head(body[1], body[0], start, width))
            }
        }
        return out
    }

    private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float): List<Pt> =
        (0..64).map { i -> val a = (i / 64.0 * 2 * Math.PI); p(cx + rx * cos(a).toFloat(), cy + ry * sin(a).toFloat()) }

    private fun closed(corners: List<Pt>, rounded: Boolean): List<Pt> =
        if (rounded) roundCorners(corners, true) else corners + corners.first()

    /** زوايا مستديرة: كل زاوية تُستبدل بقوس صغير. */
    private fun roundCorners(pts: List<Pt>, closedShape: Boolean): List<Pt> {
        val n = pts.size
        val out = ArrayList<Pt>()
        val range = if (closedShape) 0 until n else 1 until n - 1
        if (!closedShape) out.add(pts.first())
        for (i in range) {
            val prev = pts[(i - 1 + n) % n]; val cur = pts[i]; val next = pts[(i + 1) % n]
            val d1 = hypot(cur.x - prev.x, cur.y - prev.y); val d2 = hypot(next.x - cur.x, next.y - cur.y)
            val rad = min(min(d1, d2) * 0.25f, 0.02f)
            if (rad <= 1e-5f) { out.add(cur); continue }
            val ax = cur.x + (prev.x - cur.x) / d1 * rad; val ay = cur.y + (prev.y - cur.y) / d1 * rad
            val bx = cur.x + (next.x - cur.x) / d2 * rad; val by = cur.y + (next.y - cur.y) / d2 * rad
            for (k in 0..6) {
                val f = k / 6f; val g = 1 - f
                out.add(p(g * g * ax + 2 * g * f * cur.x + f * f * bx, g * g * ay + 2 * g * f * cur.y + f * f * by))
            }
        }
        if (closedShape) out.add(out.first()) else out.add(pts.last())
        return out
    }

    private fun head(from: Pt, tip: Pt, kind: ArrowHead, width: Float): List<Pt> {
        val ang = atan2(tip.y - from.y, tip.x - from.x)
        val len = (0.018f + width * 3f)
        val spread = 0.45f
        val l = p(tip.x - len * cos(ang - spread), tip.y - len * sin(ang - spread))
        val r = p(tip.x - len * cos(ang + spread), tip.y - len * sin(ang + spread))
        return if (kind == ArrowHead.CLOSED) listOf(l, p(tip.x, tip.y), r, l) else listOf(l, p(tip.x, tip.y), r)
    }
}
