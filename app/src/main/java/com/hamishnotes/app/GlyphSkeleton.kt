package com.hamishnotes.app

/**
 * يحوّل صورة حروف (قناع أبيض/أسود) إلى خطوط قلم تمر في وسط كل حرف:
 * تنحيف الحروف إلى هيكل بعرض بكسل واحد، ثم تتبّع الهيكل إلى خطوط متصلة مع سماكة الحرف عند كل نقطة.
 * بدون Android: يُختبر على الحاسوب مباشرة.
 */
object GlyphSkeleton {

    /** خط واحد: نقاط (x, y) بالبكسل وسماكة الحرف عند كل نقطة. */
    class Polyline(val xs: FloatArray, val ys: FloatArray, val thick: FloatArray) {
        val size get() = xs.size
    }

    class Result(val lines: List<Polyline>, /** متوسط سماكة الحروف بالبكسل. */ val thickness: Float)

    private val DX = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
    private val DY = intArrayOf(-1, -1, 0, 1, 1, 1, 0, -1)

    fun trace(mask: BooleanArray, w: Int, h: Int): Result {
        require(mask.size == w * h)
        val dist = distance(mask, w, h)
        val sk = mask.copyOf()
        thin(sk, w, h)
        removeStairs(sk, w, h)

        var sum = 0f
        var n = 0
        for (i in sk.indices) if (sk[i]) { sum += dist[i]; n++ }
        val avgThick = if (n > 0) (2f * sum / n).coerceAtLeast(1f) else 1f

        val raw = edges(sk, w, h, dist)
        val pruned = prune(raw, sk, w, h, avgThick * 0.9f)
        val out = pruned.map { finish(it, sk, w, h, dist) }.filter { it.size > 0 }
        return Result(out, avgThick)
    }

    // ------------------------------------------------------------ المسافة لحافة الحرف

    /** مسافة كل بكسل من الحرف إلى أقرب بكسل خارجه (تقريب 3-4 مقسوماً على 3). */
    internal fun distance(mask: BooleanArray, w: Int, h: Int): FloatArray {
        val big = 1_000_000
        val d = IntArray(w * h) { if (mask[it]) big else 0 }
        fun at(x: Int, y: Int) = if (x < 0 || y < 0 || x >= w || y >= h) 0 else d[y * w + x]
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (d[i] == 0) continue
            d[i] = minOf(d[i], at(x - 1, y) + 3, at(x, y - 1) + 3, at(x - 1, y - 1) + 4, at(x + 1, y - 1) + 4)
        }
        for (y in h - 1 downTo 0) for (x in w - 1 downTo 0) {
            val i = y * w + x
            if (d[i] == 0) continue
            d[i] = minOf(d[i], at(x + 1, y) + 3, at(x, y + 1) + 3, at(x + 1, y + 1) + 4, at(x - 1, y + 1) + 4)
        }
        // نصف بكسل: الحافة بين البكسل الأخير وما خارجه
        return FloatArray(w * h) { if (d[it] == 0) 0f else d[it] / 3f - 0.5f }
    }

    // ------------------------------------------------------------ التنحيف (Zhang-Suen)

    internal fun thin(img: BooleanArray, w: Int, h: Int) {
        fun px(x: Int, y: Int) = x >= 0 && y >= 0 && x < w && y < h && img[y * w + x]
        val del = ArrayList<Int>()
        var changed = true
        while (changed) {
            changed = false
            for (step in 0..1) {
                del.clear()
                for (y in 0 until h) for (x in 0 until w) {
                    if (!img[y * w + x]) continue
                    val p = BooleanArray(8) { px(x + DX[it], y + DY[it]) }
                    val b = p.count { it }
                    if (b < 2 || b > 6) continue
                    var a = 0
                    for (k in 0 until 8) if (!p[k] && p[(k + 1) % 8]) a++
                    if (a != 1) continue
                    // p[0]=N p[2]=E p[4]=S p[6]=W
                    if (step == 0) {
                        if (p[0] && p[2] && p[4]) continue
                        if (p[2] && p[4] && p[6]) continue
                    } else {
                        if (p[0] && p[2] && p[6]) continue
                        if (p[0] && p[4] && p[6]) continue
                    }
                    del.add(y * w + x)
                }
                if (del.isNotEmpty()) {
                    changed = true
                    for (i in del) img[i] = false
                }
            }
        }
    }

    /** يزيل بكسلات «الدرج» الزائدة حتى يبقى الهيكل متصلاً بأقل عدد من البكسلات (لا تقاطعات وهمية). */
    internal fun removeStairs(img: BooleanArray, w: Int, h: Int) {
        fun px(x: Int, y: Int) = x >= 0 && y >= 0 && x < w && y < h && img[y * w + x]
        var changed = true
        while (changed) {
            changed = false
            for (y in 0 until h) for (x in 0 until w) {
                if (!img[y * w + x]) continue
                val p = BooleanArray(8) { px(x + DX[it], y + DY[it]) }
                val cnt = p.count { it }
                if (cnt < 2) continue
                // جاران متعامدان (شمال وشرق مثلاً)
                val ortho = (p[0] && p[2]) || (p[2] && p[4]) || (p[4] && p[6]) || (p[6] && p[0])
                if (!ortho) continue
                if (ringComponents(p) != 1) continue
                img[y * w + x] = false
                changed = true
            }
        }
    }

    /** عدد مجموعات الجيران المتصلة ببعضها حول البكسل (بدونه). */
    private fun ringComponents(p: BooleanArray): Int {
        val seen = BooleanArray(8)
        var comps = 0
        for (s in 0 until 8) {
            if (!p[s] || seen[s]) continue
            comps++
            val stack = ArrayList<Int>()
            stack.add(s)
            seen[s] = true
            while (stack.isNotEmpty()) {
                val k = stack.removeAt(stack.lastIndex)
                for (m in 0 until 8) {
                    if (!p[m] || seen[m]) continue
                    if (kotlin.math.abs(DX[k] - DX[m]) <= 1 && kotlin.math.abs(DY[k] - DY[m]) <= 1) {
                        seen[m] = true
                        stack.add(m)
                    }
                }
            }
        }
        return comps
    }

    // ------------------------------------------------------------ التتبّع

    private fun degree(img: BooleanArray, w: Int, h: Int, x: Int, y: Int): Int {
        var c = 0
        for (k in 0 until 8) {
            val nx = x + DX[k]
            val ny = y + DY[k]
            if (nx >= 0 && ny >= 0 && nx < w && ny < h && img[ny * w + nx]) c++
        }
        return c
    }

    /** أجزاء الهيكل بين نقاط النهاية والتفرّع، والحلقات المغلقة، والنقاط المفردة. */
    private fun edges(img: BooleanArray, w: Int, h: Int, dist: FloatArray): List<IntArray> {
        val deg = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) if (img[y * w + x]) deg[y * w + x] = degree(img, w, h, x, y)
        fun isNode(i: Int) = img[i] && deg[i] != 2
        val used = BooleanArray(w * h)
        val out = ArrayList<IntArray>()
        val pairDone = HashSet<Long>()

        fun walk(start: Int, first: Int): IntArray {
            val path = ArrayList<Int>()
            path.add(start)
            var prev = start
            var cur = first
            while (true) {
                path.add(cur)
                if (isNode(cur)) break
                used[cur] = true
                val cx = cur % w
                val cy = cur / w
                var next = -1
                // الجار الأفقي/العمودي أولاً ثم القطري
                for (pass in 0..1) {
                    for (k in 0 until 8) {
                        if ((k % 2 == 1) != (pass == 1)) continue
                        val nx = cx + DX[k]
                        val ny = cy + DY[k]
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                        val j = ny * w + nx
                        if (!img[j] || j == prev || j == start && path.size < 3) continue
                        if (used[j] && !isNode(j)) continue
                        next = j
                        break
                    }
                    if (next >= 0) break
                }
                if (next < 0) break
                if (next == start) {
                    path.add(start)
                    break
                }
                prev = cur
                cur = next
            }
            return path.toIntArray()
        }

        for (i in img.indices) {
            if (!isNode(i)) continue
            if (deg[i] == 0) {
                out.add(intArrayOf(i))
                continue
            }
            val x = i % w
            val y = i / w
            for (k in 0 until 8) {
                val nx = x + DX[k]
                val ny = y + DY[k]
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val j = ny * w + nx
                if (!img[j]) continue
                if (isNode(j)) {
                    // عقدتان متجاورتان: جزء قصير مرة واحدة فقط
                    val key = minOf(i, j).toLong() * (w * h) + maxOf(i, j)
                    if (pairDone.add(key)) out.add(intArrayOf(i, j))
                    continue
                }
                if (used[j]) continue
                out.add(walk(i, j))
            }
        }
        // الحلقات المغلقة بلا تفرّع (مثل o)
        for (i in img.indices) {
            if (!img[i] || used[i] || isNode(i)) continue
            used[i] = true
            val x = i % w
            val y = i / w
            var first = -1
            for (k in 0 until 8) {
                val nx = x + DX[k]
                val ny = y + DY[k]
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val j = ny * w + nx
                if (img[j] && !used[j]) { first = j; break }
            }
            if (first < 0) {
                out.add(intArrayOf(i))
                continue
            }
            out.add(walk(i, first))
        }
        return out.filter { it.isNotEmpty() }
    }

    /** يحذف الزوائد القصيرة (من طرف حر إلى تفرّع) التي يتركها التنحيف عند زوايا الحروف. */
    private fun prune(list: List<IntArray>, img: BooleanArray, w: Int, h: Int, minLen: Float): List<IntArray> {
        fun deg(i: Int) = degree(img, w, h, i % w, i / w)
        fun len(e: IntArray): Float {
            var l = 0f
            for (k in 1 until e.size) {
                val dx = (e[k] % w - e[k - 1] % w).toFloat()
                val dy = (e[k] / w - e[k - 1] / w).toFloat()
                l += kotlin.math.sqrt(dx * dx + dy * dy)
            }
            return l
        }
        return list.filter { e ->
            if (e.size < 2) return@filter true
            val a = deg(e.first())
            val b = deg(e.last())
            val spur = (a == 1 && b >= 3) || (b == 1 && a >= 3)
            // جزء صغير بين تفرّعين (عقدتان متجاورتان) لا يضيف شيئاً: الأطراف المستديرة تغطيه
            val tiny = e.size == 2 && a >= 3 && b >= 3
            !(spur && len(e) < minLen) && !tiny
        }
    }

    /** نقاط الجزء بالبكسل: تنعيم الدرج، وتبسيط، ومدّ الأطراف الحرة إلى حافة الحرف. */
    private fun finish(e: IntArray, img: BooleanArray, w: Int, h: Int, dist: FloatArray): Polyline {
        val n = e.size
        val xs = FloatArray(n) { (e[it] % w) + 0.5f }
        val ys = FloatArray(n) { (e[it] / w) + 0.5f }
        val th = FloatArray(n) { (2f * dist[e[it]]).coerceAtLeast(1f) }
        if (n == 1) {
            // نقطة: خط قصير جداً حتى تُرسم
            return Polyline(floatArrayOf(xs[0] - 0.25f, xs[0] + 0.25f), floatArrayOf(ys[0], ys[0]), floatArrayOf(th[0], th[0]))
        }
        val closed = n > 3 && e.first() == e.last()
        // تنعيم (المتوسط مع الجارين) مع تثبيت الأطراف
        val sx = xs.copyOf()
        val sy = ys.copyOf()
        repeat(2) {
            val tx = sx.copyOf()
            val ty = sy.copyOf()
            for (i in 1 until n - 1) {
                sx[i] = (tx[i - 1] + 2 * tx[i] + tx[i + 1]) / 4f
                sy[i] = (ty[i - 1] + 2 * ty[i] + ty[i + 1]) / 4f
            }
        }
        val keep = rdp(sx, sy, 0.55f)
        var ox = keep.map { sx[it] }.toMutableList()
        var oy = keep.map { sy[it] }.toMutableList()
        var ot = keep.map { th[it] }.toMutableList()
        if (!closed) {
            // الطرف الحر ينتهي قبل حافة الحرف بنصف السماكة: نمدّه قليلاً
            fun extend(atStart: Boolean) {
                val i0 = if (atStart) 0 else ox.lastIndex
                val i1 = if (atStart) 1 else ox.lastIndex - 1
                if (i1 < 0 || i1 >= ox.size) return
                val endPx = e[if (atStart) 0 else n - 1]
                if (degree(img, w, h, endPx % w, endPx / w) != 1) return
                val dx = ox[i0] - ox[i1]
                val dy = oy[i0] - oy[i1]
                val l = kotlin.math.sqrt(dx * dx + dy * dy)
                if (l < 1e-3f) return
                val ext = ot[i0] * 0.3f
                ox[i0] += dx / l * ext
                oy[i0] += dy / l * ext
            }
            extend(true)
            extend(false)
        }
        return Polyline(ox.toFloatArray(), oy.toFloatArray(), ot.toFloatArray())
    }

    /** تبسيط Ramer–Douglas–Peucker: أرقام النقاط الباقية. */
    internal fun rdp(xs: FloatArray, ys: FloatArray, eps: Float): List<Int> {
        val n = xs.size
        if (n <= 2) return (0 until n).toList()
        val keep = BooleanArray(n)
        keep[0] = true
        keep[n - 1] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.add(0 to n - 1)
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            if (b <= a + 1) continue
            val dx = xs[b] - xs[a]
            val dy = ys[b] - ys[a]
            val len = kotlin.math.sqrt(dx * dx + dy * dy)
            var best = -1
            var bestD = 0f
            for (i in a + 1 until b) {
                val d = if (len < 1e-4f) {
                    kotlin.math.hypot(xs[i] - xs[a], ys[i] - ys[a])
                } else {
                    kotlin.math.abs(dy * xs[i] - dx * ys[i] + xs[b] * ys[a] - ys[b] * xs[a]) / len
                }
                if (d > bestD) { bestD = d; best = i }
            }
            if (best >= 0 && bestD > eps) {
                keep[best] = true
                stack.add(a to best)
                stack.add(best to b)
            }
        }
        return (0 until n).filter { keep[it] }
    }
}
