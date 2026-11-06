package com.hamishnotes.app

/**
 * حبر مملوء (حرف من خط): نقاط الحبر هي حدود شكل الحرف، وقد يكون للحرف أكثر من حد
 * (حلقة o، نقطة i، أجزاء متداخلة). نقطة قيمتها p سالبة = بداية حد جديد.
 * يُملأ بقاعدة nonzero كما تُرسم الخطوط، فتبقى الفراغات داخل الحروف فارغة.
 * بدون Android: يُختبر على الحاسوب مباشرة.
 */
object InkFill {
    /** علامة بداية حد جديد في قيمة الضغط. */
    const val START = -1f

    /** يقسم نقاط الحبر المملوء إلى حدوده (كل حد مضلّع مغلق). */
    fun contours(pts: List<Pt>): List<List<Pt>> {
        val out = ArrayList<List<Pt>>()
        var cur = ArrayList<Pt>()
        for (p in pts) {
            if (p.p < 0f && cur.isNotEmpty()) {
                out.add(cur)
                cur = ArrayList()
            }
            cur.add(p)
        }
        if (cur.isNotEmpty()) out.add(cur)
        return out
    }

    /** نقاط الحبر من حدود (كل حد قائمة x,y)، مع علامة بداية كل حد. */
    fun encode(contours: List<FloatArray>): List<Pt> {
        val out = ArrayList<Pt>()
        for (c in contours) {
            var i = 0
            while (i + 1 < c.size) {
                out.add(Pt(c[i], c[i + 1], if (i == 0) START else 1f))
                i += 2
            }
        }
        return out
    }

    /** عدد الالتفاف حول النقطة (غير صفر = داخل الشكل). */
    fun winding(x: Float, y: Float, contours: List<List<Pt>>): Int {
        var wn = 0
        for (c in contours) {
            val n = c.size
            if (n < 3) continue
            for (i in 0 until n) {
                val a = c[i]
                val b = c[(i + 1) % n]
                val side = (b.x - a.x) * (y - a.y) - (x - a.x) * (b.y - a.y)
                if (a.y <= y) {
                    if (b.y > y && side > 0f) wn++
                } else if (b.y <= y && side < 0f) wn--
            }
        }
        return wn
    }

    private fun segDist(px: Float, py: Float, a: Pt, b: Pt): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val l2 = dx * dx + dy * dy
        val t = if (l2 <= 0f) 0f else (((px - a.x) * dx + (py - a.y) * dy) / l2).coerceIn(0f, 1f)
        return kotlin.math.hypot(px - (a.x + t * dx), py - (a.y + t * dy))
    }

    /** الممحاة (دائرة نصف قطرها radius) تلمس الحرف: داخله أو قريبة من حدوده. */
    fun hit(pts: List<Pt>, x: Float, y: Float, radius: Float, outline: Float = 0f): Boolean {
        if (pts.isEmpty()) return false
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (p in pts) {
            if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y
        }
        val r = radius + outline / 2f
        if (x < minX - r || x > maxX + r || y < minY - r || y > maxY + r) return false
        val cs = contours(pts)
        if (winding(x, y, cs) != 0) return true
        for (c in cs) for (i in c.indices) if (segDist(x, y, c[i], c[(i + 1) % c.size]) < r) return true
        return false
    }

    /**
     * يجمع حدود الحروف في قطع: الحدود المتداخلة (حلقة داخل حرف، أجزاء حرف متراكبة، حروف متصلة)
     * قطعة واحدة، والمنفصلة قطعاً مستقلة. كل حد قائمة x,y. يعيد أرقام الحدود لكل قطعة.
     */
    fun group(contours: List<FloatArray>): List<List<Int>> {
        val n = contours.size
        val parent = IntArray(n) { it }
        fun find(i: Int): Int { var x = i; while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }; return x }
        fun union(a: Int, b: Int) { parent[find(a)] = find(b) }
        val box = contours.map { c ->
            var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
            var i = 0
            while (i + 1 < c.size) {
                x0 = minOf(x0, c[i]); x1 = maxOf(x1, c[i]); y0 = minOf(y0, c[i + 1]); y1 = maxOf(y1, c[i + 1])
                i += 2
            }
            floatArrayOf(x0, y0, x1, y1)
        }
        val asPts = contours.map { c -> List(c.size / 2) { Pt(c[it * 2], c[it * 2 + 1], 1f) } }
        fun anyInside(a: Int, b: Int): Boolean {
            // نقطة من حد a داخل المضلع b (بقاعدة الزوجي/الفردي على حد واحد)
            val poly = asPts[b]
            val step = maxOf(1, asPts[a].size / 24)
            var k = 0
            while (k < asPts[a].size) {
                val p = asPts[a][k]
                var c = false
                var j = poly.size - 1
                for (i in poly.indices) {
                    val pi = poly[i]
                    val pj = poly[j]
                    if ((pi.y > p.y) != (pj.y > p.y) && p.x < (pj.x - pi.x) * (p.y - pi.y) / (pj.y - pi.y) + pi.x) c = !c
                    j = i
                }
                if (c) return true
                k += step
            }
            return false
        }
        for (a in 0 until n) for (b in a + 1 until n) {
            val p = box[a]
            val q = box[b]
            if (p[2] < q[0] || q[2] < p[0] || p[3] < q[1] || q[3] < p[1]) continue
            if (find(a) == find(b)) continue
            if (anyInside(a, b) || anyInside(b, a)) union(a, b)
        }
        return (0 until n).groupBy { find(it) }.values.map { it.sorted() }.sortedBy { box[it.first()][0] }
    }

    /** تبسيط حد مغلق (Ramer–Douglas–Peucker) مع إبقاء الشكل ضمن eps. */
    fun simplify(c: FloatArray, eps: Float): FloatArray {
        val n = c.size / 2
        if (n <= 4) return c
        val keep = BooleanArray(n)
        keep[0] = true
        keep[n - 1] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.add(0 to n - 1)
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            if (b <= a + 1) continue
            val ax = c[a * 2]; val ay = c[a * 2 + 1]; val bx = c[b * 2]; val by = c[b * 2 + 1]
            val dx = bx - ax; val dy = by - ay
            val len = kotlin.math.hypot(dx, dy)
            var best = -1
            var bestD = 0f
            for (i in a + 1 until b) {
                val px = c[i * 2]; val py = c[i * 2 + 1]
                val d = if (len < 1e-6f) kotlin.math.hypot(px - ax, py - ay) else kotlin.math.abs(dy * px - dx * py + bx * ay - by * ax) / len
                if (d > bestD) { bestD = d; best = i }
            }
            if (best >= 0 && bestD > eps) {
                keep[best] = true
                stack.add(a to best)
                stack.add(best to b)
            }
        }
        val out = ArrayList<Float>()
        for (i in 0 until n) if (keep[i]) { out.add(c[i * 2]); out.add(c[i * 2 + 1]) }
        return out.toFloatArray()
    }

    /** يقص مضلعاً على نصف المستوى x >= a (keepRight) أو x <= a (Sutherland–Hodgman). */
    private fun clipX(c: FloatArray, a: Float, keepRight: Boolean): FloatArray {
        val n = c.size / 2
        if (n == 0) return c
        val out = ArrayList<Float>(c.size + 8)
        fun inside(x: Float) = if (keepRight) x >= a else x <= a
        for (i in 0 until n) {
            val px = c[i * 2]; val py = c[i * 2 + 1]
            val j = (i + 1) % n
            val qx = c[j * 2]; val qy = c[j * 2 + 1]
            val pin = inside(px)
            val qin = inside(qx)
            if (pin) { out.add(px); out.add(py) }
            if (pin != qin) {
                val t = (a - px) / (qx - px)
                out.add(a); out.add(py + (qy - py) * t)
            }
        }
        return out.toFloatArray()
    }

    /**
     * يقسم قطعة حروف متصلة (خط متصل أو عربي) إلى شرائح عند حدود الحروف، فتمسح الممحاة الحرف
     * الذي تمر عليه لا الكلمة كلها. cuts: مواضع الحدود بين الحروف (x). الشرائح تتداخل قليلاً
     * (overlap) حتى لا يظهر خط رفيع عند القص. القطعة التي لا يمر بها حد تبقى كما هي.
     */
    fun slice(contours: List<FloatArray>, cuts: List<Float>, minPart: Float, overlap: Float): List<List<FloatArray>> {
        var x0 = Float.MAX_VALUE
        var x1 = -Float.MAX_VALUE
        for (c in contours) { var i = 0; while (i < c.size) { x0 = minOf(x0, c[i]); x1 = maxOf(x1, c[i]); i += 2 } }
        // الحدود التي تقع داخل القطعة بعيداً عن طرفيها (لا تقص طرف حرف بارز بلا فائدة)
        val inner = cuts.filter { it > x0 + minPart && it < x1 - minPart }.sorted()
        if (inner.isEmpty()) return listOf(contours)
        val edges = listOf(-Float.MAX_VALUE) + inner + listOf(Float.MAX_VALUE)
        val out = ArrayList<List<FloatArray>>()
        for (k in 0 until edges.size - 1) {
            val a = edges[k]
            val b = edges[k + 1]
            val part = ArrayList<FloatArray>()
            for (c in contours) {
                var cc = c
                if (a > -Float.MAX_VALUE) cc = clipX(cc, a - overlap, true)
                if (cc.size >= 6 && b < Float.MAX_VALUE) cc = clipX(cc, b + overlap, false)
                if (cc.size >= 6 && kotlin.math.abs(area(cc)) > 1e-9f) part.add(cc)
            }
            if (part.isNotEmpty()) out.add(part)
        }
        return out
    }

    /** المساحة الموجّهة لمضلع (لمعرفة الحدود الفارغة بعد القص). */
    fun area(c: FloatArray): Float {
        val n = c.size / 2
        var s = 0f
        for (i in 0 until n) {
            val j = (i + 1) % n
            s += c[i * 2] * c[j * 2 + 1] - c[j * 2] * c[i * 2 + 1]
        }
        return s / 2f
    }

    /**
     * يقسم ناتج Path.approximate (موضع، x، y لكل نقطة) إلى حدود: نقطتان متتاليتان بنفس الموضع
     * = قفزة إلى حد جديد. تُحذف نقطة الإغلاق المكررة في آخر كل حد.
     */
    fun splitApproximation(a: FloatArray): List<FloatArray> {
        val out = ArrayList<FloatArray>()
        var cur = ArrayList<Float>()
        fun flush() {
            if (cur.size >= 4) {
                val n = cur.size
                if (n >= 6 && cur[0] == cur[n - 2] && cur[1] == cur[n - 1]) { cur.removeAt(n - 1); cur.removeAt(n - 2) }
            }
            if (cur.size >= 6) out.add(cur.toFloatArray())
            cur = ArrayList()
        }
        var i = 0
        var prevF = Float.NaN
        while (i + 2 < a.size) {
            val f = a[i]
            if (cur.isNotEmpty() && f == prevF) flush()
            cur.add(a[i + 1]); cur.add(a[i + 2])
            prevF = f
            i += 3
        }
        flush()
        return out
    }

    /** سطر حروف محوّلة: خط القاعدة وحجم الخط وامتداده الأفقي (وحدات الصفحة). */
    class LineRef(val base: Float, val em: Float, val l: Float, val r: Float)

    /** حدود النقاط: يسار، أعلى، يمين، أسفل. */
    fun bounds(pts: List<Pt>): FloatArray {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (p in pts) { if (p.x < l) l = p.x; if (p.x > r) r = p.x; if (p.y < t) t = p.y; if (p.y > b) b = p.y }
        return floatArrayOf(l, t, r, b)
    }

    /** خط القاعدة والحجم المحفوظان في الحرف (نسبةً لحدوده)، أو null إن لم يُحفظا. */
    fun lineOf(pts: List<Pt>, lineF: Float, emF: Float): LineRef? {
        if (lineF < 0f || emF <= 0f || pts.isEmpty()) return null
        val bb = bounds(pts)
        val h = bb[3] - bb[1]
        if (h <= 0f) return null
        return LineRef(bb[1] + lineF * h, emF * h, bb[0], bb[2])
    }

    /** نسبتا خط القاعدة والحجم لحدود هذه النقاط (للحفظ مع الحرف). */
    fun fractions(pts: List<Pt>, base: Float, em: Float): Pair<Float, Float> {
        val bb = bounds(pts)
        val h = (bb[3] - bb[1]).coerceAtLeast(1e-6f)
        return (base - bb[1]) / h to em / h
    }

    /**
     * السطر المحوّل القديم الذي تكمله كتابة جديدة: على ارتفاعها تقريباً وقريب منها أفقياً.
     * يعيد خط قاعدته وحجمه (الوسيط بين حروفه)، أو null.
     */
    fun nearestLine(refs: List<LineRef>, l: Float, r: Float, base: Float, size: Float): LineRef? {
        val near = refs.filter { ref ->
            kotlin.math.abs(ref.base - base) <= 0.6f * maxOf(ref.em, size) &&
                ref.r >= l - 3f * ref.em && ref.l <= r + 3f * ref.em
        }
        if (near.isEmpty()) return null
        val best = near.minByOrNull { kotlin.math.abs(it.base - base) }!!
        val same = refs.filter { kotlin.math.abs(it.base - best.base) < 0.15f * best.em }
        val bases = same.map { it.base }.sorted()
        val ems = same.map { it.em }.sorted()
        return LineRef(bases[bases.size / 2], ems[ems.size / 2], same.minOf { it.l }, same.maxOf { it.r })
    }
}
