package com.hamishnotes.app

/** دور نص من التمييز في الخريطة الذهنية (HIDDEN بقي لقراءة بيانات قديمة فقط). */
enum class MapRole { BRANCH, POINT, HIDDEN }

/**
 * مكان نص في الخريطة: فرع (parent = null) أو نقطة تحت parent (فرع أو نقطة).
 * pos = ترتيبه بين إخوته (الأصغر أولًا).
 */
data class MapNodeEdit(val role: MapRole, val parent: String? = null, val pos: Double? = null)

/**
 * الخريطة كما وضعها المستخدم. لا شيء تلقائي: ما ليس هنا لا يظهر.
 * المفاتيح «معرّف التمييز#رقم الجزء»: مربوطة بالتمييز نفسه، فلا تتأثر بوضع «مرتّب» أو «كما التُقط».
 */
data class MapEdits(
    val nodes: Map<String, MapNodeEdit> = emptyMap(),
    val collapsed: Set<String> = emptySet(),
    /** نص عدّله المستخدم في الخريطة فقط (الملخص لا يتغيّر). */
    val texts: Map<String, String> = emptyMap(),
    /** نصوص حرة يكتبها المستخدم في الخريطة، غير مرتبطة بالملخص (المفتاح يبدأ بـ f:). */
    val free: Map<String, String> = emptyMap()
) {
    fun isEmpty(): Boolean = nodes.isEmpty() && collapsed.isEmpty() && texts.isEmpty() && free.isEmpty()
}

/** عقدة في الخريطة كما تُرسم: النص كاملًا كما التُقط. */
class MapNode(
    val key: String,
    val text: String,
    /** النص كما التُقط (قبل تعديل المستخدم في الخريطة). */
    val original: String,
    /** التمييز الذي جاء منه النص؛ null = نص حر كتبه المستخدم في الخريطة. */
    val mark: TextMark?,
    val collapsed: Boolean,
    val children: List<MapNode>
) {
    /** عدد كل النقاط تحتها (للعقدة المطوية). */
    fun count(): Int = children.size + children.sumOf { it.count() }

    val isFree: Boolean get() = mark == null
}

class MindMapModel(val title: String, val branches: List<MapNode>) {
    val total: Int get() = branches.size + branches.sumOf { it.count() }
}

/** أين يوضع النص: تحت parent (null = المركز، أي فرع جديد)، قبل before (null = في الآخر). */
data class MapTarget(val parent: String?, val before: String?)

object MindMap {
    /** نص واحد من التمييز (جزء من «كما التُقط»)، أو نص حر (mark = null). */
    class Item(val key: String, val text: String, val mark: TextMark?)

    const val FREE_PREFIX = "f:"

    fun isFree(key: String) = key.startsWith(FREE_PREFIX)

    /** نصوص التمييز ثم النصوص الحرة: كل ما يمكن أن يظهر في الخريطة. */
    fun allItems(marks: List<TextMark>, edits: MapEdits): List<Item> =
        items(marks) + edits.free.map { (k, v) -> Item(k, v, null) }

    /** مفتاح العقدة من مفتاح جزء «كما التُقط» (r:معرّف#رقم)، أو null لما ليس جزءًا مباشرًا. */
    fun nodeKey(blockKey: String): String? = if (blockKey.startsWith("r:")) blockKey.removePrefix("r:") else null

    fun items(marks: List<TextMark>): List<Item> =
        Summary.build(marks, tidy = false).mapNotNull { b ->
            val k = nodeKey(b.key) ?: return@mapNotNull null
            val m = b.marks.firstOrNull() ?: return@mapNotNull null
            val text = when (b) {
                is SHeading -> b.text
                is SPara -> b.text
                is STable -> b.rows.joinToString("\n") { r -> r.filter { it.isNotBlank() }.joinToString(" · ") }
            }
            if (text.isBlank()) null else Item(k, text.trim(), m)
        }

    /**
     * الأماكن الفعلية بعد تصحيح ما انكسر: نقطة فقد أبوها (حُذف تمييزه) تصبح فرعًا،
     * والحلقات تُكسر. لا حد للعمق. يعيد (الأب، الترتيب) لكل نص ظاهر.
     */
    private fun resolve(edits: MapEdits, items: List<Item>): Map<String, Pair<String?, Double>> {
        val index = items.withIndex().associate { it.value.key to it.index }
        val raw = edits.nodes.filter { (k, v) -> k in index && v.role != MapRole.HIDDEN }
        val out = LinkedHashMap<String, Pair<String?, Double>>()
        for ((k, v) in raw) {
            val pos = v.pos ?: index.getValue(k).toDouble()
            val p = if (v.role == MapRole.BRANCH) null else v.parent?.takeIf { it in raw && it != k }
            out[k] = p to pos
        }
        // حلقات (أ تحت ب وب تحت أ): أولها يصبح فرعًا
        for (k in out.keys.toList()) {
            val seen = HashSet<String>()
            var cur: String? = k
            while (cur != null) {
                if (!seen.add(cur)) {
                    out[cur] = null to out.getValue(cur).second
                    break
                }
                cur = out[cur]?.first
            }
        }
        return out
    }

    fun build(title: String, marks: List<TextMark>, edits: MapEdits, exclude: Set<String> = emptySet()): MindMapModel {
        val items = allItems(marks, edits)
        val byKey = items.associateBy { it.key }
        val placed = resolve(edits, items)
        // ما يُنقل الآن يختفي من مكانه القديم مع ما تحته
        val hidden = HashSet<String>()
        if (exclude.isNotEmpty()) {
            for (k in placed.keys) {
                var cur: String? = k
                while (cur != null) {
                    if (cur in exclude) { hidden.add(k); break }
                    cur = placed[cur]?.first
                }
            }
        }
        val visible = placed.filterKeys { it !in hidden }
        fun childrenOf(parent: String?): List<String> =
            visible.filter { it.value.first == parent }.entries.sortedBy { it.value.second }.map { it.key }
        fun node(k: String): MapNode {
            val item = byKey.getValue(k)
            val shown = edits.texts[k]?.takeIf { s -> s.isNotBlank() } ?: item.text
            return MapNode(k, shown, item.text, item.mark, k in edits.collapsed, childrenOf(k).map { c -> node(c) })
        }
        return MindMapModel(title, childrenOf(null).map { node(it) })
    }

    /** يجد العقدة بمفتاحها في الشجرة. */
    fun find(model: MindMapModel, key: String): MapNode? {
        fun walk(n: MapNode): MapNode? = if (n.key == key) n else n.children.firstNotNullOfOrNull { walk(it) }
        return model.branches.firstNotNullOfOrNull { walk(it) }
    }

    /** الأب والإخوة (بالترتيب) لعقدة ظاهرة. */
    fun siblingsOf(model: MindMapModel, key: String): Pair<String?, List<MapNode>>? {
        if (model.branches.any { it.key == key }) return null to model.branches
        fun walk(n: MapNode): Pair<String?, List<MapNode>>? =
            if (n.children.any { it.key == key }) n.key to n.children else n.children.firstNotNullOfOrNull { walk(it) }
        return model.branches.firstNotNullOfOrNull { walk(it) }
    }

    /** يضع النصوص (بترتيبها) في المكان المختار. ما تحتها ينتقل معها. */
    fun place(edits: MapEdits, items: List<Item>, keys: List<String>, target: MapTarget): MapEdits {
        if (keys.isEmpty()) return edits
        val placed = resolve(edits, items)
        val moving = keys.toSet()
        // الإخوة في المكان الجديد، بلا النصوص المنقولة
        val sibs = placed.filter { it.value.first == target.parent && it.key !in moving }.entries.sortedBy { it.value.second }
        val i = target.before?.let { b -> sibs.indexOfFirst { it.key == b } }?.takeIf { it >= 0 } ?: sibs.size
        val lo = if (i > 0) sibs[i - 1].value.second else (if (sibs.isNotEmpty()) sibs[0].value.second - 1.0 else 0.0)
        val hi = if (i < sibs.size) sibs[i].value.second else lo + keys.size + 1.0
        val step = (hi - lo) / (keys.size + 1)
        val role = if (target.parent == null) MapRole.BRANCH else MapRole.POINT
        // الأماكن الحالية بقيمها الفعلية (حتى تبقى ثابتة لما كُتب قبل وجود الترتيب)
        val nodes = LinkedHashMap<String, MapNodeEdit>()
        for ((k, v) in placed) nodes[k] = MapNodeEdit(if (v.first == null) MapRole.BRANCH else MapRole.POINT, v.first, v.second)
        keys.forEachIndexed { j, k -> nodes[k] = MapNodeEdit(role, target.parent, lo + step * (j + 1)) }
        return edits.copy(nodes = nodes)
    }

    /** يحرك العقدة خطوة بين إخوتها (-1 لأعلى، +1 لأسفل). */
    fun move(edits: MapEdits, items: List<Item>, key: String, delta: Int): MapEdits {
        val placed = resolve(edits, items)
        val parent = placed[key]?.first
        val sibs = placed.filter { it.value.first == parent }.entries.sortedBy { it.value.second }.map { it.key }
        val i = sibs.indexOf(key)
        val j = i + delta
        if (i < 0 || j < 0 || j >= sibs.size) return edits
        val other = sibs[j]
        val nodes = LinkedHashMap<String, MapNodeEdit>()
        for ((k, v) in placed) nodes[k] = MapNodeEdit(if (v.first == null) MapRole.BRANCH else MapRole.POINT, v.first, v.second)
        val a = nodes.getValue(key)
        val b = nodes.getValue(other)
        nodes[key] = a.copy(pos = b.pos)
        nodes[other] = b.copy(pos = a.pos)
        return edits.copy(nodes = nodes)
    }

    /** يزيل النص وكل ما تحته من الخريطة (يبقى في الملخص). يعيد عدد ما أُزيل. */
    fun remove(edits: MapEdits, items: List<Item>, key: String): Pair<MapEdits, Int> {
        val placed = resolve(edits, items)
        val gone = HashSet<String>()
        for (k in placed.keys) {
            var cur: String? = k
            while (cur != null) {
                if (cur == key) { gone.add(k); break }
                cur = placed[cur]?.first
            }
        }
        gone.add(key)
        // النصوص الحرة تُحذف نهائيًا، ونصوص الملخص تُزال من الخريطة فقط
        return edits.copy(
            nodes = edits.nodes - gone, collapsed = edits.collapsed - gone, texts = edits.texts - gone,
            free = edits.free - gone.filter { isFree(it) }.toSet()
        ) to gone.size
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
        val texts = LinkedHashMap<String, String>()
        for ((k, v) in e.texts) texts.putIfAbsent(conv(k), v)
        return e.copy(nodes = nodes, collapsed = e.collapsed.map { conv(it) }.toSet(), texts = texts)
    }

    /** يعدّل نص العقدة في الخريطة فقط. نص فارغ أو مطابق للأصل = يعود كما التُقط. */
    fun setText(edits: MapEdits, items: List<Item>, key: String, text: String): MapEdits {
        if (isFree(key)) {
            val v = text.trim()
            return if (v.isEmpty() || key !in edits.free) edits else edits.copy(free = edits.free + (key to v))
        }
        val original = items.firstOrNull { it.key == key }?.text ?: return edits
        val v = text.trim()
        return if (v.isEmpty() || v == original) edits.copy(texts = edits.texts - key)
        else edits.copy(texts = edits.texts + (key to v))
    }

    /** يضيف نصًا حرًا في المكان المختار. يعيد (الخريطة، مفتاح النص الجديد). */
    fun addFree(edits: MapEdits, marks: List<TextMark>, text: String, target: MapTarget, id: String): Pair<MapEdits, String?> {
        val v = text.trim()
        if (v.isEmpty()) return edits to null
        val key = FREE_PREFIX + id
        val withText = edits.copy(free = edits.free + (key to v))
        return place(withText, allItems(marks, withText), listOf(key), target) to key
    }

    /** وصف قصير لاسم عقدة داخل جملة شرح (لا يغيّر نصها في الخريطة). */
    fun quote(text: String, words: Int = 5): String {
        val w = text.trim().split(Regex("\\s+"))
        return if (w.size <= words) text.trim() else w.take(words).joinToString(" ") + "…"
    }
}
