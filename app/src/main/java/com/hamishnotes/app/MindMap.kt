package com.hamishnotes.app

import androidx.compose.ui.graphics.Color

/** دور نص من التمييز في الخريطة الذهنية. */
enum class MapRole { BRANCH, POINT, HIDDEN }

/** ما اختاره المستخدم لنص: فرع، أو نقطة تحت parent (فرع أو نقطة)، أو مخفي. */
data class MapNodeEdit(val role: MapRole, val parent: String? = null)

/**
 * تعديلات المستخدم على الخريطة، فوق المسودة التلقائية. المفاتيح «معرّف التمييز#رقم الجزء»
 * (مربوطة بالتمييز نفسه، فلا تتأثر بوضع «مرتّب» أو «كما التُقط» ولا بتعديلات الملخص).
 *  started  أُغلق شريط «مسودة جاهزة»
 *  blank    «ابدأ من فارغ»: لا مسودة، يظهر فقط ما اختاره المستخدم
 */
data class MapEdits(
    val nodes: Map<String, MapNodeEdit> = emptyMap(),
    val labels: Map<String, String> = emptyMap(),
    val collapsed: Set<String> = emptySet(),
    val started: Boolean = false,
    val blank: Boolean = false
) {
    fun isEmpty(): Boolean = nodes.isEmpty() && labels.isEmpty() && collapsed.isEmpty() && !started && !blank
}

/** عقدة في الخريطة كما تُرسم. */
class MapNode(
    val key: String,
    val label: String,
    val sub: String?,
    val color: Color,
    val star: Boolean,
    val mark: TextMark?,
    /** وضعها التطبيق (لا المستخدم). */
    val auto: Boolean,
    val collapsed: Boolean,
    val children: List<MapNode>
) {
    /** عدد كل النقاط تحتها (للفرع المطوي). */
    fun count(): Int = children.size + children.sumOf { it.count() }
}

class MindMapModel(val title: String, val total: Int, val branches: List<MapNode>)

object MindMap {
    /** نص واحد من التمييز (جزء من «كما التُقط»). */
    class Item(
        val key: String,
        val text: String,
        val emphasis: List<String>,
        val heading: Boolean,
        val mark: TextMark
    )

    /** مفتاح العقدة من مفتاح جزء «كما التُقط» (r:معرّف#رقم)، أو null لما ليس جزءًا مباشرًا. */
    fun nodeKey(blockKey: String): String? = if (blockKey.startsWith("r:")) blockKey.removePrefix("r:") else null

    fun items(marks: List<TextMark>): List<Item> =
        Summary.build(marks, tidy = false).mapNotNull { b ->
            val k = nodeKey(b.key) ?: return@mapNotNull null
            val m = b.marks.firstOrNull() ?: return@mapNotNull null
            when (b) {
                is SHeading -> Item(k, b.text, emptyList(), true, m)
                is SPara -> Item(k, b.text, b.emphasis, false, m)
                is STable -> Item(k, b.rows.firstOrNull()?.filter { it.isNotBlank() }?.joinToString(" · ") ?: "", emptyList(), false, m)
            }
        }.filter { it.text.isNotBlank() }

    // ---------------------------------------------------------------- الأسماء القصيرة

    private const val SHORT_WORDS = 6

    private fun words(s: String) = s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    /** أول n كلمات، دون قطع كلمة. */
    private fun firstWords(s: String, n: Int): String {
        val w = words(s)
        return if (w.size <= n) s.trim() else w.take(n).joinToString(" ") + "…"
    }

    /** موضع «:» إن كان ما قبلها اسمًا قصيرًا. */
    private fun colonAt(text: String): Int {
        val i = text.indexOfFirst { it == ':' || it == '：' }
        if (i < 2) return -1
        val before = text.substring(0, i)
        return if (words(before).size in 1..5 && i < text.length - 1) i else -1
    }

    /** الاسم القصير والسطر الصغير تحته، وهل هو مهم (★). */
    fun shortLabel(text: String, emphasis: List<String>): Triple<String, String?, Boolean> {
        val t = text.replace('\n', ' ').trim()
        val emph = emphasis.firstOrNull { it.isNotBlank() && it.trim().length < t.length - 2 }
        val wholeStar = emphasis.any { it.trim() == t }
        // قاعدة 3: الجزء المظلّل مرتين يصبح الاسم
        if (emph != null) return Triple(firstWords(emph, SHORT_WORDS + 2), null, true)
        // قاعدة 1: ما قبل «:» هو الاسم
        val c = colonAt(t)
        if (c > 0) {
            val sub = t.substring(c + 1).trim().takeIf { it.isNotEmpty() }?.let { firstWords(it, 7) }
            return Triple(t.substring(0, c).trim(), sub, wholeStar)
        }
        // قاعدة 2: أول 6 كلمات تقريبًا
        return Triple(firstWords(t, SHORT_WORDS), null, wholeStar)
    }

    // ---------------------------------------------------------------- المسودة التلقائية

    private fun colorKey(c: Color) = "color:${Summary.argbOf(c)}"

    /**
     * المسودة: العناوين فروع؛ وإن لم توجد فالنصوص من نوع «اسم: شرح»؛ وإن لم توجد فالألوان؛ وإلا الصفحات.
     * synthetic = فروع ليست نصوصًا (لون أو صفحة) مع أسمائها.
     */
    private fun auto(items: List<Item>, labelOf: (Color) -> String): Pair<Map<String, MapNodeEdit>, Map<String, Pair<String, Color>>> {
        val roles = LinkedHashMap<String, MapNodeEdit>()
        val synthetic = LinkedHashMap<String, Pair<String, Color>>()
        fun byOrder(isBranch: (Item) -> Boolean) {
            val firstBranch = items.firstOrNull(isBranch)?.key
            var last: String? = null
            for (it in items) {
                if (isBranch(it)) {
                    roles[it.key] = MapNodeEdit(MapRole.BRANCH)
                    last = it.key
                } else {
                    roles[it.key] = MapNodeEdit(MapRole.POINT, last ?: firstBranch)
                }
            }
        }
        when {
            items.any { it.heading } -> byOrder { it.heading }
            items.count { colonAt(it.text) > 0 } >= 2 -> byOrder { colonAt(it.text) > 0 }
            items.map { Summary.argbOf(it.mark.color) }.distinct().size >= 2 -> {
                for (it in items) {
                    val k = colorKey(it.mark.color)
                    synthetic.putIfAbsent(k, labelOf(it.mark.color) to it.mark.color)
                    roles[it.key] = MapNodeEdit(MapRole.POINT, k)
                }
            }
            else -> {
                for (it in items) {
                    val k = "page:${it.mark.page}"
                    synthetic.putIfAbsent(k, L("صفحة ${it.mark.page + 1}", "Page ${it.mark.page + 1}") to it.mark.color)
                    roles[it.key] = MapNodeEdit(MapRole.POINT, k)
                }
            }
        }
        return roles to synthetic
    }

    /**
     * الخريطة كما تُعرض: المسودة (إلا في «ابدأ من فارغ») ثم اختيارات المستخدم فوقها.
     * byColor = «حسب اللون فقط»: كل لون فرع، دون اختيارات المستخدم.
     */
    fun build(
        title: String,
        marks: List<TextMark>,
        edits: MapEdits,
        byColor: Boolean,
        labelOf: (Color) -> String
    ): MindMapModel {
        val items = items(marks)
        val byKey = items.associateBy { it.key }
        val roles = LinkedHashMap<String, MapNodeEdit>()
        val synthetic = LinkedHashMap<String, Pair<String, Color>>()
        if (byColor) {
            for (it in items) {
                val k = colorKey(it.mark.color)
                synthetic.putIfAbsent(k, labelOf(it.mark.color) to it.mark.color)
                roles[it.key] = MapNodeEdit(MapRole.POINT, k)
            }
        } else {
            if (!edits.blank) {
                val (r, syn) = auto(items, labelOf)
                roles.putAll(r)
                synthetic.putAll(syn)
            }
            for ((k, e) in edits.nodes) if (k in byKey) roles[k] = e
        }

        // الأب الصحيح: موجود وظاهر، وإلا أقرب فرع قبل النص
        fun isBranch(k: String) = k in synthetic || roles[k]?.role == MapRole.BRANCH
        fun nearestBranchBefore(k: String): String? {
            val i = items.indexOfFirst { it.key == k }
            for (j in i - 1 downTo 0) if (roles[items[j].key]?.role == MapRole.BRANCH) return items[j].key
            return items.firstOrNull { roles[it.key]?.role == MapRole.BRANCH }?.key ?: synthetic.keys.firstOrNull()
        }
        val parentOf = HashMap<String, String>()
        for (it in items) {
            val e = roles[it.key] ?: continue
            if (e.role != MapRole.POINT) continue
            var p = e.parent
            val pr = p?.let { roles[it] }
            val ok = p != null && (isBranch(p) || (pr?.role == MapRole.POINT && pr.parent != null && isBranch(pr.parent)))
            if (!ok) p = nearestBranchBefore(it.key)
            if (p != null && p != it.key) parentOf[it.key] = p
        }
        // نقطة تحت نقطة أصبحت أبعد من مستويين: تُرفع إلى فرعها
        for ((k, p) in parentOf.toMap()) {
            if (!isBranch(p)) {
                val gp = parentOf[p]
                if (gp == null || !isBranch(gp)) {
                    val b = nearestBranchBefore(k)
                    if (b != null) parentOf[k] = b else parentOf.remove(k)
                }
            }
        }

        fun nodeFor(k: String, depth: Int): MapNode {
            val syn = synthetic[k]
            val kids = if (depth >= 2) emptyList() else items.filter { parentOf[it.key] == k }.map { nodeFor(it.key, depth + 1) }
            if (syn != null) {
                return MapNode(k, edits.labels[k] ?: syn.first, null, syn.second, false, null, true, k in edits.collapsed, kids)
            }
            val it = byKey.getValue(k)
            val (lbl, sub, star) = shortLabel(it.text, it.emphasis)
            val custom = edits.labels[k]
            return MapNode(
                k, custom ?: lbl, if (custom != null) null else sub, it.mark.color,
                star || it.mark.level >= 2, it.mark, !byColor && k !in edits.nodes, k in edits.collapsed, kids
            )
        }
        val branchKeys = items.filter { roles[it.key]?.role == MapRole.BRANCH }.map { it.key }
        // ترتيب الفروع: ترتيب أول ظهور في المستند (الألوان والصفحات بترتيب ظهورها)
        val ordered = (synthetic.keys + branchKeys).distinct().sortedBy { k ->
            if (k in synthetic) items.indexOfFirst { parentOf[it.key] == k } else items.indexOfFirst { it.key == k }
        }
        val branches = ordered.map { nodeFor(it, 0) }.filter { it.mark != null || it.children.isNotEmpty() }
        return MindMapModel(title, items.size, branches)
    }

    /** الفروع الحالية (لقائمة «نقطة في فرع») مع الفرع الأقرب قبل key أولًا. */
    fun branchChoices(model: MindMapModel, items: List<Item>, key: String?): List<MapNode> {
        val list = model.branches
        if (key == null) return list
        val idx = items.indexOfFirst { it.key == key }
        val before = list.filter { b -> b.mark != null && items.indexOfFirst { it.key == b.key } in 0 until idx }
        val nearest = before.maxByOrNull { b -> items.indexOfFirst { it.key == b.key } }
        return if (nearest == null) list else listOf(nearest) + (list - nearest)
    }

    /** يجد العقدة بمفتاحها في الشجرة. */
    fun find(model: MindMapModel, key: String): MapNode? {
        fun walk(n: MapNode): MapNode? = if (n.key == key) n else n.children.firstNotNullOfOrNull { walk(it) }
        return model.branches.firstNotNullOfOrNull { walk(it) }
    }

    /** دمج التمييزات: ما كان للتمييزات القديمة ينتقل إلى التمييز الجديد. */
    fun migrate(e: MapEdits, removed: List<String>, newId: String): MapEdits {
        if (e.isEmpty() || removed.isEmpty()) return e
        fun conv(k: String): String {
            val id = k.substringBefore('#')
            return if (id in removed) "$newId#${k.substringAfter('#', "0")}" else k
        }
        val nodes = LinkedHashMap<String, MapNodeEdit>()
        for ((k, v) in e.nodes) {
            val nk = conv(k)
            val nv = v.copy(parent = v.parent?.let { conv(it) })
            if (nk !in nodes) nodes[nk] = nv
        }
        nodes.entries.removeAll { (k, v) -> v.parent == k }
        val labels = LinkedHashMap<String, String>()
        for ((k, v) in e.labels) labels.putIfAbsent(conv(k), v)
        return e.copy(nodes = nodes, labels = labels, collapsed = e.collapsed.map { conv(it) }.toSet())
    }
}
