package com.mynotes.app

import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max

/**
 * MyNotes Pro: خريطة ذهنية من الملخص المرتّب، تُبنى بالكود (دون نموذج، فلا اختلاق):
 * العنوان «# » فرع، والعنوان الفرعي «## » فرع داخله، والنقاط وصفوف الجداول أوراق.
 */
object MindMap {
    class Node(val label: String, val level: Int, val children: MutableList<Node> = ArrayList(), var branch: Int = 0) {
        val id: String get() = "$level:$label"
    }

    /** نص قصير مقروء للعقدة. */
    fun shortLabel(s: String, max: Int = 90): String {
        val t = s.replace("**", "").replace(Regex("^[-•]\\s*"), "").trim()
        return if (t.length <= max) t else t.take(max - 1).trimEnd() + "…"
    }

    /** أقسام الشرح والملاحظات لا تدخل الخريطة: الخريطة نقاط فقط. */
    private val EXPLAIN = Regex("(?i)^(شرح|توضيح|ملاحظ|explanation|explain|lecture notes|notes?\\b|figure|شكل)")

    /**
     * النقطة كعبارة مفتاحية قصيرة بلا شرح: يُحذف ما بين الأقواس، ويُقطع عند التعليل («لأن»، because، —)،
     * وتُؤخذ أول جملة؛ والصيغة «مصطلح: قيمة» تبقى بالطرفين.
     */
    fun keyPhrase(s: String, max: Int = 55): String {
        var t = s.replace("**", "").replace(Regex("^[-•]\\s*"), "").trim()
        t = t.replace(Regex("\\s*\\([^)]{0,80}\\)"), "").replace(Regex("\\s*\\[[^\\]]{0,80}\\]"), "")
        val cutters = listOf(" because ", " since ", " which ", " meaning ", " i.e.", " e.g.", " — ", " – ", " لأن", " لان", " حيث ", " أي ", " مثل ", "; ", "؛ ")
        for (c in cutters) {
            val i = t.indexOf(c, ignoreCase = true)
            if (i in 12 until t.length) t = t.substring(0, i)
        }
        val dot = Regex("[.!؟?](\\s|$)").find(t)
        if (dot != null && dot.range.first >= 12) t = t.substring(0, dot.range.first)
        t = t.trim().trimEnd(',', '،', ':', '.')
        return if (t.length <= max) t else t.take(max - 1).trimEnd() + "…"
    }

    /** أهمية النقطة: المظلل مرتين، الأرقام والجرعات، الأدوية المختارة، النفي والتحذير، والآثار الجانبية. */
    private fun score(line: String): Int {
        var s = 0
        if (line.contains("**")) s += 6
        if (Regex("\\d").containsMatchIn(line)) s += 2
        if (Regex("(?i)\\b(DOC|drug of choice|first[- ]line|contraindicat|toxic|side effect|adverse|mechanism|MOA|not|never|avoid|only|most)\\b|لا |ممنوع|سام|أعراض|آلية|الأفضل|فقط").containsMatchIn(line)) s += 2
        if (line.length < 70) s += 1
        return s
    }

    /**
     * important = true: النقاط المهمة فقط (حتى 4 لكل فرع، وصفوف الجداول بأسماءها وأهم خليتين)، دون أقسام الشرح،
     * وكل نقطة عبارة مفتاحية قصيرة. false: كل ما في الملخص.
     */
    fun parse(title: String, text: String, important: Boolean = true): Node {
        val root = Node(shortLabel(title, 60), 0)
        var h1: Node? = null
        var h2: Node? = null
        var header: List<String>? = null
        var skip1 = false
        var skip2 = false
        // نقاط كل فرع مع درجتها، لاختيار الأهم في النهاية
        val scored = HashMap<Node, MutableList<Pair<Node, Int>>>()
        fun parent(): Node = h2 ?: h1 ?: root
        fun skipping() = important && (skip2 || (skip1 && h2 == null))
        for (raw in text.lines()) {
            val l = raw.trim()
            if (l.isEmpty()) { header = null; continue }
            when {
                l.startsWith("## ") -> {
                    val name = l.removePrefix("## ").trim()
                    skip2 = EXPLAIN.containsMatchIn(name)
                    h2 = if (important && skip2) null else Node(shortLabel(name, 60), 2).also { (h1 ?: root).children.add(it) }
                    header = null
                }
                l.startsWith("# ") -> {
                    val name = l.removePrefix("# ").trim()
                    skip1 = EXPLAIN.containsMatchIn(name)
                    skip2 = false
                    h1 = if (important && skip1) null else Node(shortLabel(name, 60), 1).also { root.children.add(it) }
                    h2 = null
                    header = null
                }
                skipping() -> {}
                l.contains(" | ") -> {
                    val cells = l.replace("**", "").split(" | ").map { it.trim() }
                    if (header == null) header = cells
                    else {
                        // صف جدول: الخلية الأولى ثم البقية «العمود: القيمة»
                        val rest = cells.drop(1).mapIndexedNotNull { k, c ->
                            if (c.isBlank() || c == "—") null
                            else (header?.getOrNull(k + 1)?.let { "$it: " } ?: "") + (if (important) keyPhrase(c, 40) else c)
                        }
                        val node = Node(shortLabel(cells.first(), 60), 3)
                        (if (important) rest.take(2) else rest).forEach { node.children.add(Node(shortLabel(it), 4)) }
                        val par = parent()
                        if (important) scored.getOrPut(par) { ArrayList() }.add(node to (score(l) + 1))
                        else par.children.add(node)
                    }
                }
                else -> {
                    val par = parent()
                    if (important) {
                        val k = keyPhrase(l)
                        if (k.length >= 3) scored.getOrPut(par) { ArrayList() }.add(Node(k, 3) to score(l))
                    } else par.children.add(Node(shortLabel(l), 3))
                }
            }
        }
        if (important) {
            // الأهم أولًا (حتى 4 نقاط و6 صفوف جدول لكل فرع)، مع الحفاظ على ترتيبها في الملخص
            for ((par, items) in scored) {
                val rows = items.filter { it.first.children.isNotEmpty() }
                val points = items.filter { it.first.children.isEmpty() }
                val keepRows = rows.withIndex().sortedByDescending { it.value.second }.take(6).sortedBy { it.index }.map { it.value.first }
                val keepPts = points.withIndex().sortedByDescending { it.value.second }.take(4).sortedBy { it.index }.map { it.value.first }
                val seen = HashSet<String>()
                (keepPts + keepRows).forEach { n -> if (seen.add(n.label.lowercase())) par.children.add(n) }
            }
            // فروع فارغة (بلا نقاط) تُحذف
            fun prune(n: Node) {
                n.children.forEach { prune(it) }
                n.children.removeAll { it.level in 1..2 && it.children.isEmpty() }
            }
            prune(root)
        }
        root.children.forEachIndexed { i, n -> paint(n, i) }
        return root
    }

    private fun paint(n: Node, b: Int) {
        n.branch = b
        n.children.forEach { paint(it, b) }
    }

    val branchColors = listOf(
        Color(0xFF1B365D), Color(0xFF8C6A1F), Color(0xFF2E7D6B), Color(0xFFB0475A),
        Color(0xFF5B4B9A), Color(0xFF2F6FA8), Color(0xFF9A5B2E), Color(0xFF4F7A2C)
    )
    fun colorOf(n: Node): Color = if (n.level == 0) Palette.Navy else branchColors[n.branch % branchColors.size]

    /** موضع عقدة في المخطط (بوحدة dp قبل التكبير). */
    class Placed(val node: Node, val x: Float, val y: Float, val w: Float, val h: Float, val parent: Placed?)

    fun widthOf(level: Int) = when (level) { 0 -> 200f; 1 -> 210f; 2 -> 200f; else -> 260f }
    private fun lines(n: Node): Int = max(1, minOf(4, ceil(n.label.length / (widthOf(n.level) / 7.6f)).toInt()))
    fun heightOf(n: Node) = 14f + lines(n) * 18f

    /** شجرة أفقية: الجذر في أول عمود، وكل مستوى من الشجرة في عمود، والأب في منتصف أبنائه. */
    fun layout(root: Node, collapsed: Set<String>): List<Placed> {
        val gapY = 12f
        val colW = 300f
        val pos = LinkedHashMap<Node, FloatArray>()
        val parentOf = HashMap<Node, Node>()
        val depthOf = HashMap<Node, Int>()
        var cursor = 20f
        fun place(n: Node, depth: Int): Float {
            depthOf[n] = depth
            val kids = if (n.id in collapsed) emptyList() else n.children
            val h = heightOf(n)
            val w = widthOf(n.level)
            val x = 20f + depth * colW
            val y = if (kids.isEmpty()) {
                val y0 = cursor
                cursor += h + gapY
                y0
            } else {
                val centers = kids.map { parentOf[it] = n; place(it, depth + 1) }
                val yy = (centers.first() + centers.last()) / 2f - h / 2f
                if (cursor < yy + h + gapY) cursor = yy + h + gapY
                yy
            }
            pos[n] = floatArrayOf(x, y, w, h)
            return y + h / 2f
        }
        place(root, 0)
        val placed = HashMap<Node, Placed>()
        pos.keys.sortedBy { depthOf[it] ?: 0 }.forEach { n ->
            val a = pos.getValue(n)
            placed[n] = Placed(n, a[0], a[1], a[2], a[3], parentOf[n]?.let { placed[it] })
        }
        return pos.keys.mapNotNull { placed[it] }
    }

    /** حفظ الخريطة كملف PDF بصفحة واحدة بحجمها. */
    /**
     * حفظ الخريطة كملف PDF: صفحات أفقية بعرض الخريطة، تُقطع بين العقد لا في منتصفها
     * (صفحة واحدة طويلة جدًا تُثقل القراءة وقد تُغلق التطبيق عند فتحها).
     */
    fun exportPdf(context: Context, root: Node, collapsed: Set<String>, uri: Uri) {
        val placed = layout(root, collapsed)
        val contentW = placed.maxOf { it.x + it.w } + 30f
        val contentH = placed.maxOf { it.y + it.h } + 30f
        // صفحات A4 أفقية (842 × 595 نقطة): الخريطة تُصغَّر لتناسب عرض الصفحة، والملف رسوم متجهة صغيرة الحجم
        val pageWpt = 842
        val pageHpt = 595
        val scale = (pageWpt / contentW).coerceIn(0.45f, 1f)
        val pageH = pageHpt / scale
        // نقاط القطع: أكبر موضع لا تعبره أي عقدة، في النصف الثاني من الصفحة
        val breaks = ArrayList<Float>()
        var start = 0f
        while (start + pageH < contentH) {
            val limit = start + pageH
            val cands = placed.map { it.y + it.h + 6f }.filter { c ->
                c > start + pageH * 0.5f && c <= limit && placed.none { it.y < c && it.y + it.h > c }
            }
            val cut = cands.maxOrNull() ?: limit
            breaks.add(cut)
            start = cut
        }
        val ranges = ArrayList<Pair<Float, Float>>()
        var from = 0f
        for (b in breaks) { ranges.add(from to b); from = b }
        ranges.add(from to contentH)

        val doc = PdfDocument()
        try {
            ranges.forEachIndexed { k, (y0, y1) ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pageWpt, pageHpt, k + 1).create())
                val c = page.canvas
                c.drawColor(Palette.Ivory.toArgb())
                c.scale(scale, scale)
                c.translate(0f, -y0)
                c.clipRect(0f, y0, contentW, y1)
                drawMap(context, c, placed)
                doc.finishPage(page)
            }
            context.contentResolver.openOutputStream(uri, "wt")?.use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    private fun drawMap(context: Context, c: android.graphics.Canvas, placed: List<Placed>) {
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.6f }
        for (p in placed) {
            val par = p.parent ?: continue
            line.color = colorOf(p.node).copy(alpha = 0.6f).toArgb()
            val path = android.graphics.Path()
            val sx = par.x + par.w
            val sy = par.y + par.h / 2f
            val ex = p.x
            val ey = p.y + p.h / 2f
            path.moveTo(sx, sy)
            path.cubicTo(sx + 28f, sy, ex - 28f, ey, ex, ey)
            c.drawPath(path, line)
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11.5f
            typeface = context.resources.getFont(R.font.almarai_regular)
        }
        for (p in placed) {
            val col = colorOf(p.node)
            val strong = p.node.level <= 2
            fill.color = if (strong) col.toArgb() else col.copy(alpha = 0.10f).toArgb()
            c.drawRoundRect(RectF(p.x, p.y, p.x + p.w, p.y + p.h), 10f, 10f, fill)
            tp.color = if (strong) android.graphics.Color.WHITE else Palette.TextPrimary.toArgb()
            tp.isFakeBoldText = strong
            val sl = StaticLayout.Builder.obtain(p.node.label, 0, p.node.label.length, tp, (p.w - 16f).toInt().coerceAtLeast(10))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(4).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
            c.save()
            c.translate(p.x + 8f, p.y + (p.h - sl.height) / 2f)
            sl.draw(c)
            c.restore()
        }
    }
}

/** نافذة الخريطة الذهنية: تكبير وتصغير، طيّ الفروع بالنقر، وحفظ PDF. */
@Composable
fun MindMapDialog(title: String, summary: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var important by remember { mutableStateOf(true) }
    val root = remember(summary, important) { MindMap.parse(title, summary, important) }
    val collapsed = remember(summary) { mutableStateListOf<String>() }
    var zoom by remember { mutableStateOf(1f) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) AppController.scope.launch {
            try {
                withContext(Dispatchers.IO) { MindMap.exportPdf(context, root, collapsed.toSet(), uri) }
                AppController.messages.tryEmit(L("حُفظت الخريطة الذهنية", "Mind map saved"))
            } catch (t: Throwable) {
                AppController.messages.tryEmit(L("تعذر حفظ الخريطة", "Could not save the mind map"))
            }
        }
    }
    val placed = remember(root, collapsed.toList()) { MindMap.layout(root, collapsed.toSet()) }
    val totalW = (placed.maxOfOrNull { it.x + it.w } ?: 300f) + 40f
    val totalH = (placed.maxOfOrNull { it.y + it.h } ?: 300f) + 40f

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(16.dp), shape = RoundedCornerShape(22.dp), color = Palette.Ivory) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        L("✦ خريطة ذهنية", "✦ Mind map"), fontFamily = Fonts.Display, fontSize = 20.sp,
                        fontWeight = FontWeight.Bold, color = Palette.Navy, modifier = Modifier.weight(1f)
                    )
                    ChoiceChip(L("المهم فقط", "Key points"), important) { important = true; collapsed.clear() }
                    ChoiceChip(L("كل النقاط", "All points"), !important) { important = false; collapsed.clear() }
                    TextButton(onClick = { zoom = (zoom / 1.2f).coerceAtLeast(0.4f) }) { Text("−", fontSize = 20.sp) }
                    Text("${(zoom * 100).toInt()}%", fontSize = 13.sp, color = Palette.TextSecondary)
                    TextButton(onClick = { zoom = (zoom * 1.2f).coerceAtMost(2.5f) }) { Text("+", fontSize = 20.sp) }
                    TextButton(onClick = {
                        collapsed.clear()
                        root.children.forEach { b -> b.children.filter { it.children.isNotEmpty() }.forEach { collapsed.add(it.id) } }
                    }) { Text(L("طيّ الكل", "Collapse")) }
                    TextButton(onClick = { collapsed.clear() }) { Text(L("فتح الكل", "Expand")) }
                    TextButton(onClick = { saver.launch(L("خريطة ذهنية - $title.pdf", "Mind map - $title.pdf")) }) { Text("PDF") }
                    TextButton(onClick = onDismiss) { Text(L("إغلاق", "Close")) }
                }
                Text(
                    L("انقر على فرع لطيّه أو فتحه. «المهم فقط»: أهم النقاط كعبارات قصيرة دون الشرح، من ملخصك نفسه دون إضافات.",
                        "Tap a branch to fold or unfold it. “Key points”: the most important points as short phrases without explanations, from your own summary."),
                    fontSize = 12.sp, color = Palette.TextSecondary, modifier = Modifier.padding(horizontal = 18.dp)
                )
                Box(
                    Modifier.fillMaxSize().padding(8.dp)
                        .horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())
                ) {
                    Box(Modifier.size((totalW * zoom).dp, (totalH * zoom).dp)) {
                        Canvas(Modifier.size((totalW * zoom).dp, (totalH * zoom).dp)) {
                            val d = density * zoom
                            for (p in placed) {
                                val par = p.parent ?: continue
                                val sx = (par.x + par.w) * d
                                val sy = (par.y + par.h / 2f) * d
                                val ex = p.x * d
                                val ey = (p.y + p.h / 2f) * d
                                val path = Path().apply {
                                    moveTo(sx, sy)
                                    cubicTo(sx + 28f * d, sy, ex - 28f * d, ey, ex, ey)
                                }
                                drawPath(path, MindMap.colorOf(p.node).copy(alpha = 0.55f), style = Stroke(width = 2f * d))
                            }
                        }
                        for (p in placed) {
                            val col = MindMap.colorOf(p.node)
                            val strong = p.node.level <= 2
                            val folded = p.node.id in collapsed && p.node.children.isNotEmpty()
                            Box(
                                Modifier.absoluteOffset((p.x * zoom).dp, (p.y * zoom).dp)
                                    .size((p.w * zoom).dp, (p.h * zoom).dp)
                                    .clip(RoundedCornerShape((10 * zoom).dp))
                                    .background(if (strong) col else col.copy(alpha = 0.10f))
                                    .border(if (folded) 2.dp else 0.dp, Palette.Gold, RoundedCornerShape((10 * zoom).dp))
                                    .clickable(enabled = p.node.children.isNotEmpty()) {
                                        if (p.node.id in collapsed) collapsed.remove(p.node.id) else collapsed.add(p.node.id)
                                    }
                                    .padding(horizontal = (8 * zoom).dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    p.node.label + if (folded) "  (${p.node.children.size})" else "",
                                    fontSize = (12 * zoom).sp, lineHeight = (16 * zoom).sp,
                                    fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
                                    color = if (strong) Color.White else Palette.TextPrimary,
                                    maxLines = 4, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Start
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
