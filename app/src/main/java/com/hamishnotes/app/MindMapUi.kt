package com.hamishnotes.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------- الألوان

/** ألوان الفروع: ثابتة من ألوان التطبيق حسب ترتيب الفرع (لا من لون الهايلايتر). */
private val MapPalette = listOf(
    Color(0xFF1B365D), Color(0xFF2E7D5B), Color(0xFF8C6A1F), Color(0xFF2F5FB0), Color(0xFF9A3B3B), Color(0xFF5B4B9A)
)

fun mapBranchColor(i: Int): Color = MapPalette[((i % MapPalette.size) + MapPalette.size) % MapPalette.size]

private val MapCenterTop = Color(0xFF2C4F80)
private val MapCenterDeep = Color(0xFF0D1D35)
private val MapDot = Color(0xFFE4DFD3)
private val GhostBg = Color(0xFFFFF8E6)
private val GhostInk = Color(0xFF6B5418)
private val BarDark = Color(0xFF1A2230)

// ---------------------------------------------------------------- درج الملخصات

/** شريط «اختيار عدة نصوص» في درج الملخصات. */
@Composable
fun MapSelectBar(count: Int, onPlace: () -> Unit, onCancel: () -> Unit, onCard: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().background(BarDark).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(
            if (count == 0) L("اختر النصوص بالترتيب الذي تريده في الخريطة", "Pick texts in the order you want on the map")
            else L("اخترت $count · تُوضع كلها في المكان الذي تختاره، بنفس الترتيب", "$count selected · they go where you choose, in this order"),
            fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "◎ " + L("ضعها في الخريطة", "Place on the map"), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = BarDark.copy(alpha = if (count > 0) 1f else 0.4f), textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Palette.Gold.copy(alpha = if (count > 0) 1f else 0.5f))
                    .clickable(enabled = count > 0, onClick = onPlace).padding(vertical = 11.dp)
            )
            if (onCard != null) {
                Text(
                    "▤ " + L("أضفها إلى بطاقة", "Add to card"), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = if (count > 0) 1f else 0.4f), textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(CardTeal.copy(alpha = if (count > 0) 1f else 0.5f))
                        .clickable(enabled = count > 0, onClick = onCard).padding(vertical = 11.dp)
                )
            }
            Text(
                L("إلغاء", "Cancel"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center,
                modifier = Modifier.weight(0.45f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.1f))
                    .clickable(onClick = onCancel).padding(vertical = 11.dp)
            )
        }
    }
}

/** لون المحتوى في صنع البطاقة من الملخص. */
val CardTeal = Color(0xFF0F766E)

/**
 * شريط صنع البطاقة بعد «أضفها إلى بطاقة»: يختار المستخدم Title أو Content على النصوص نفسها.
 */
@Composable
fun CardRoleBar(titles: Int, contents: Int, onBack: () -> Unit, onNext: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF123B37)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(
            L("بطاقة · اختر عنوان أو محتوى على كل نص", "Card · tap Title or Content on each text") +
                L("  ($titles عنوان، $contents محتوى)", "  ($titles title, $contents content)"),
            fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                L("رجوع", "Back"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center,
                modifier = Modifier.weight(0.6f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.1f))
                    .clickable(onClick = onBack).padding(vertical = 11.dp)
            )
            Text(
                L("التالي", "Next"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Palette.Navy)
                    .border(1.dp, Color(0xFF4B6A95), RoundedCornerShape(12.dp))
                    .clickable(onClick = onNext).padding(vertical = 11.dp)
            )
            Text(
                L("إلغاء", "Cancel"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center,
                modifier = Modifier.weight(0.6f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.1f))
                    .clickable(onClick = onCancel).padding(vertical = 11.dp)
            )
        }
    }
}

/** اختيار دور النص في البطاقة، فوق النص نفسه. */
@Composable
fun CardRoleChips(title: Boolean, onSelect: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.shadow(2.dp, RoundedCornerShape(9.dp)).clip(RoundedCornerShape(9.dp)).background(Color.White).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        listOf(true to L("عنوان", "Title"), false to L("محتوى", "Content")).forEach { (v, label) ->
            val on = v == title
            Text(
                label, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = if (on) Color.White else Palette.TextSecondary,
                modifier = Modifier.clip(RoundedCornerShape(7.dp))
                    .background(if (on) (if (v) Palette.Navy else CardTeal) else Color.Transparent)
                    .clickable { onSelect(v) }.padding(horizontal = 9.dp, vertical = 5.dp)
            )
        }
    }
}

// ---------------------------------------------------------------- شاشة الخريطة

/** الخطوط بين العقد، تُحسب أثناء الترتيب وتُرسم خلفها. */
private class MapCurve(val a: Offset, val b: Offset, val color: Color, val width: Float, val dashed: Boolean)

private class MapGeometry {
    var curves: List<MapCurve> = emptyList()
}

private enum class EKind { CENTER, BRANCH, POINT, PLUS_CENTER, PLUS_ROW }

/**
 * عنصر في الترتيب: عقدة أو مكان «+». parent = رقم العنصر الأب في القائمة.
 * depth: 0 فرع، 1 نقطة، 2 فأكثر نقاط فرعية (بلا حد).
 */
private class Entry(
    val kind: EKind,
    val node: MapNode?,
    val branch: Int,
    val parent: Int,
    val target: MapTarget? = null,
    val ghost: Boolean = false,
    val label: String? = null,
    val depth: Int = 0
)

/**
 * الخريطة الذهنية. placing = نصوص يضعها المستخدم الآن (من الملخصات):
 * تظهر أماكن «+» على الخريطة، ويختار المكان ثم «ضعه هنا».
 */
@Composable
fun MindMapDialog(
    s: DocSession,
    onJump: (TextMark) -> Unit,
    onDismiss: () -> Unit,
    placing: List<String>? = null,
    onPlaced: () -> Unit = {}
) {
    val c = AppController
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val all = s.allMarks()
    val mapEdits = s.summaryEdits.map
    val items = remember(all) { MindMap.items(all) }
    val fromSummary = placing != null
    var placingKeys by remember { mutableStateOf(placing?.filter { k -> items.any { it.key == k } }) }
    var target by remember { mutableStateOf<MapTarget?>(null) }
    val keys = placingKeys

    val model = remember(all, mapEdits, keys, target) {
        when {
            keys == null -> MindMap.build(s.title, all, mapEdits)
            target != null -> MindMap.build(s.title, all, MindMap.place(mapEdits, items, keys, target!!))
            else -> MindMap.build(s.title, all, mapEdits, exclude = keys.toSet())
        }
    }
    // النصوص المنقولة وما تحتها في المعاينة (بإطار متقطع، بلا أماكن «+» حولها)
    val ghosts = remember(model, keys, target) {
        if (keys == null || target == null) emptySet() else {
            val out = HashSet<String>()
            fun add(n: MapNode) { out.add(n.key); n.children.forEach { add(it) } }
            keys.forEach { k -> MindMap.find(model, k)?.let { add(it) } }
            out
        }
    }
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val selected = selectedKey?.let { MindMap.find(model, it) }
    // العقدة التي يُعدَّل نصها الآن
    var editKey by remember { mutableStateOf<String?>(null) }
    var exportMenu by remember { mutableStateOf(false) }

    // التكبير والتحريك
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var content by remember { mutableStateOf(IntSize.Zero) }
    var fitted by remember { mutableStateOf(false) }
    fun fit() {
        if (viewport.width <= 0 || content.width <= 0) return
        val sc = min(min(viewport.width / content.width.toFloat(), viewport.height / content.height.toFloat()), 1.15f).coerceAtLeast(0.2f)
        scale = sc
        offset = Offset((viewport.width - content.width * sc) / 2f, (viewport.height - content.height * sc) / 2f)
    }
    LaunchedEffect(content, viewport) {
        if (!fitted && content.width > 0 && viewport.width > 0) {
            fit()
            fitted = true
        }
    }
    val layer = rememberGraphicsLayer()

    fun startMove(k: String) {
        selectedKey = null
        placingKeys = listOf(k)
        target = null
        fitted = false
    }
    fun cancelPlacing() {
        if (fromSummary) onDismiss() else {
            placingKeys = null
            target = null
            fitted = false
        }
    }
    fun confirmPlacing() {
        val k = keys ?: return
        val t = target ?: return
        s.mapPlace(k, t)
        c.messages.tryEmit(if (k.size == 1) L("وُضع النص في الخريطة", "Placed on the map") else L("وُضعت ${k.size} نصوص في الخريطة", "${k.size} texts placed on the map"))
        if (fromSummary) onPlaced() else {
            placingKeys = null
            target = null
            fitted = false
        }
    }

    Dialog(onDismissRequest = { if (keys != null) cancelPlacing() else onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Palette.Ivory) {
            Column(Modifier.fillMaxSize()) {
                if (keys != null) {
                    PlacingHeader(items.filter { it.key in keys }.sortedBy { keys.indexOf(it.key) }) { cancelPlacing() }
                } else {
                    MapTopBar(
                        title = s.title,
                        hasBranches = model.branches.isNotEmpty(),
                        anyOpen = model.branches.any { !it.collapsed && it.children.isNotEmpty() },
                        exportMenu = exportMenu,
                        onExportMenu = { exportMenu = it },
                        onBack = onDismiss,
                        onFit = { fit() },
                        onCollapseAll = { open ->
                            val bk = model.branches.map { it.key }.toSet()
                            s.mapSetCollapsed(if (open) mapEdits.collapsed + bk else mapEdits.collapsed - bk)
                            fitted = false
                        },
                        onExport = { kind ->
                            exportMenu = false
                            selectedKey = null
                            scope.launch {
                                try {
                                    // إطاران حتى يختفي إطار العقدة المختارة قبل الالتقاط
                                    androidx.compose.runtime.withFrameNanos { }
                                    androidx.compose.runtime.withFrameNanos { }
                                    val bmp = framed(layer.toImageBitmap().asAndroidBitmap())
                                    when (kind) {
                                        0 -> shareFile(ctx, writePng(ctx, bmp, s.title), "image/png")
                                        1 -> shareFile(ctx, writePdf(ctx, bmp, s.title), "application/pdf")
                                        else -> { addAsPage(s, bmp); onDismiss() }
                                    }
                                } catch (t: Throwable) {
                                    c.messages.tryEmit(L("تعذر تجهيز الخريطة", "Could not prepare the map"))
                                }
                            }
                        }
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(if (keys != null) Palette.Gold else Palette.Divider))

                // ---------- مساحة الخريطة
                Box(
                    Modifier.fillMaxSize()
                        .drawBehind {
                            val step = 22.dp.toPx()
                            var y = step / 2
                            while (y < size.height) {
                                var x = step / 2
                                while (x < size.width) {
                                    drawCircle(MapDot, 1.3.dp.toPx(), Offset(x, y))
                                    x += step
                                }
                                y += step
                            }
                        }
                        .onSizeChanged { viewport = it }
                        .pointerInput(Unit) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val ns = (scale * zoom).coerceIn(0.2f, 3f)
                                val k = ns / scale
                                offset = Offset(centroid.x - (centroid.x - offset.x) * k + pan.x, centroid.y - (centroid.y - offset.y) * k + pan.y)
                                scale = ns
                            }
                        }
                ) {
                    if (keys == null && model.branches.isEmpty()) {
                        EmptyMap()
                    } else {
                        Box(
                            Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                                .graphicsLayer {
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offset.x
                                    translationY = offset.y
                                }
                        ) {
                            MapLayout(
                                model = model,
                                placing = keys != null,
                                ghosts = ghosts,
                                target = target,
                                selectedKey = selectedKey,
                                onTap = { k -> if (keys == null) selectedKey = if (selectedKey == k) null else k },
                                onTarget = { t -> target = if (target == t) null else t },
                                onLongTap = { k -> if (keys == null) { selectedKey = k; editKey = k } },
                                modifier = Modifier
                                    .onSizeChanged { content = it }
                                    .drawWithContent {
                                        layer.record { this@drawWithContent.drawContent() }
                                        drawLayer(layer)
                                    }
                            )
                        }
                    }

                    val bottom = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp, start = 12.dp, end = 12.dp)
                    when {
                        keys != null && target != null -> ConfirmBar(describe(model, target!!), bottom, onUndo = { target = null }, onConfirm = { confirmPlacing() })
                        keys != null -> HintChip(L("المس أي «+» لتختار مكان النص", "Tap any “+” to choose where it goes"), bottom)
                        selected != null -> {
                            val sib = MindMap.siblingsOf(model, selected.key)
                            val idx = sib?.second?.indexOfFirst { it.key == selected.key } ?: -1
                            NodeActions(
                                node = selected,
                                canUp = idx > 0,
                                canDown = sib != null && idx in 0 until sib.second.lastIndex,
                                modifier = bottom,
                                onJump = { onJump(selected.mark) },
                                onMove = { startMove(selected.key) },
                                onUp = { s.mapMove(selected.key, -1) },
                                onDown = { s.mapMove(selected.key, 1) },
                                onToggle = { s.mapToggleCollapsed(selected.key) },
                                onRemove = {
                                    val n = s.mapRemove(selected.key)
                                    selectedKey = null
                                    c.messages.tryEmit(
                                        if (n > 1) L("أُزيل من الخريطة مع ما تحته (${n - 1})، ويبقى في الملخص", "Removed from the map with ${n - 1} under it; it stays in the summary")
                                        else L("أُزيل من الخريطة، ويبقى في الملخص", "Removed from the map; it stays in the summary")
                                    )
                                },
                                onClose = { selectedKey = null },
                                onEdit = { editKey = selected.key }
                            )
                        }
                        model.branches.isNotEmpty() -> HintChip(L("انقر أي نص لإجراءاته · حرّك وكبّر بإصبعين", "Tap a text for actions · pan and zoom with two fingers"), bottom)
                    }
                }
            }
        }
    }

    val ek = editKey
    val editNode = ek?.let { MindMap.find(model, it) }
    if (editNode != null) {
        NodeTextDialog(
            node = editNode,
            onSave = { txt -> s.mapSetText(editNode.key, txt); editKey = null },
            onRestore = { s.mapSetText(editNode.key, editNode.original); editKey = null },
            onDismiss = { editKey = null }
        )
    }
}

/** تعديل نص العقدة في الخريطة فقط؛ الملخص لا يتغيّر. */
@Composable
private fun NodeTextDialog(node: MapNode, onSave: (String) -> Unit, onRestore: () -> Unit, onDismiss: () -> Unit) {
    var text by remember(node.key) { mutableStateOf(node.text) }
    val edited = node.text != node.original
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("تعديل نص العقدة", "Edit node text"), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, color = Palette.Navy) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    L("يتغيّر في الخريطة فقط، والملخص يبقى كما هو", "Changes the map only; the summary stays as it is") +
                        if (edited) L("\nالنص الأصلي: «${MindMap.quote(node.original, 12)}» · ص ${node.mark.page + 1}", "\nOriginal: “${MindMap.quote(node.original, 12)}” · p. ${node.mark.page + 1}") else "",
                    fontSize = 12.sp, lineHeight = 17.sp, color = Palette.TextSecondary
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(enabled = text.isNotBlank(), onClick = { onSave(text) }) {
                Text(L("حفظ", "Save"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (edited) androidx.compose.material3.TextButton(onClick = onRestore) { Text(L("استعادة الأصلي", "Restore original"), color = Palette.GoldDeep) }
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text(L("إلغاء", "Cancel")) }
            }
        }
    )
}

/** جملة تشرح أين سيوضع النص. */
private fun describe(model: MindMapModel, t: MapTarget): String {
    val p = t.parent?.let { MindMap.find(model, it) }
    val before = t.before?.let { MindMap.find(model, it) }
    return when {
        p == null -> if (model.branches.size <= 1) L("أول فرع", "the first branch") else L("فرع جديد", "a new branch")
        model.branches.any { it.key == p.key } ->
            L("نقطة تحت «${MindMap.quote(p.text)}»", "a point under “${MindMap.quote(p.text)}”") +
                if (before != null) L(" قبل «${MindMap.quote(before.text)}»", " before “${MindMap.quote(before.text)}”") else L(" في آخرها", " at the end")
        else -> L("نقطة فرعية تحت «${MindMap.quote(p.text)}»", "a sub-point under “${MindMap.quote(p.text)}”")
    }
}

@Composable
private fun PlacingHeader(texts: List<MindMap.Item>, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFFFFF6DD), Palette.GoldSoft)))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (texts.size > 1) L("اختر مكان ${texts.size} نصوص", "Choose where ${texts.size} texts go") else L("اختر مكان النص", "Choose where the text goes"),
                fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Palette.Navy
            )
            Spacer(Modifier.height(8.dp))
            val shape = RoundedCornerShape(12.dp)
            Column(
                Modifier.fillMaxWidth().clip(shape).background(Color.White)
                    .drawBehind { dashedBorder(Palette.Gold, 12.dp.toPx(), 2.dp.toPx()) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    if (texts.size > 1) L("النصوص التي تضعها (بالترتيب)", "The texts you are placing (in order)") else L("النص الذي تضعه (كما ظلّلته)", "The text you are placing (as highlighted)"),
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Palette.GoldDeep
                )
                texts.take(3).forEachIndexed { i, t ->
                    Text(
                        (if (texts.size > 1) "${i + 1}. " else "") + t.text, fontSize = 14.sp, lineHeight = 21.sp, color = Palette.TextPrimary,
                        maxLines = if (texts.size > 1) 1 else 4, overflow = TextOverflow.Ellipsis
                    )
                }
                if (texts.size > 3) Text(L("و${texts.size - 3} غيرها", "and ${texts.size - 3} more"), fontSize = 12.sp, color = Palette.TextSecondary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            L("إلغاء", "Cancel"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.Navy,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Palette.Divider, RoundedCornerShape(12.dp))
                .clickable(onClick = onCancel).padding(horizontal = 14.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun MapTopBar(
    title: String,
    hasBranches: Boolean,
    anyOpen: Boolean,
    exportMenu: Boolean,
    onExportMenu: (Boolean) -> Unit,
    onBack: () -> Unit,
    onFit: () -> Unit,
    onCollapseAll: (Boolean) -> Unit,
    onExport: (Int) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth().background(Color.White)) {
        val compact = maxWidth < 600.dp
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Palette.Divider, RoundedCornerShape(12.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { Text(if (I18n.english) "‹" else "›", fontSize = 22.sp, color = Palette.Navy, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(L("الخريطة الذهنية", "Mind map"), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Palette.Navy, maxLines = 1)
                Text(title, fontSize = 12.sp, color = Palette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (hasBranches) {
                TopAction(R.drawable.ph_fit, L("توسيط", "Fit"), onFit)
                TopAction(R.drawable.ph_list, if (anyOpen) L("طيّ الكل", "Collapse all") else L("فتح الكل", "Expand all")) { onCollapseAll(anyOpen) }
                Box {
                    Row(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFFFFF6DD), Palette.GoldSoft)))
                            .border(1.dp, Palette.Gold, RoundedCornerShape(12.dp))
                            .clickable { onExportMenu(true) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PhIcon(R.drawable.ph_export, Palette.GoldDeep, 16.dp)
                        if (!compact) {
                            Spacer(Modifier.width(5.dp))
                            Text(L("تصدير", "Export"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.GoldDeep)
                        }
                    }
                    DropdownMenu(expanded = exportMenu, onDismissRequest = { onExportMenu(false) }) {
                        DropdownMenuItem(text = { Text(L("مشاركة صورة PNG", "Share as PNG image")) }, leadingIcon = { PhIcon(R.drawable.ph_image, Palette.TextSecondary, 18.dp) }, onClick = { onExport(0) })
                        DropdownMenuItem(text = { Text(L("مشاركة PDF", "Share as PDF")) }, leadingIcon = { PhIcon(R.drawable.ph_export, Palette.TextSecondary, 18.dp) }, onClick = { onExport(1) })
                        DropdownMenuItem(text = { Text(L("إضافة صفحة في آخر المستند", "Add as a page at the end")) }, leadingIcon = { PhIcon(R.drawable.ph_add_page, Palette.TextSecondary, 18.dp) }, onClick = { onExport(2) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TopAction(icon: Int, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        PhIcon(icon, Palette.Navy, 20.dp, desc)
    }
}

@Composable
private fun HintChip(text: String, modifier: Modifier) {
    Text(
        text, fontSize = 12.sp, color = Palette.TextSecondary,
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Color.White)
            .border(1.dp, Palette.Divider, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
private fun ConfirmBar(what: String, modifier: Modifier, onUndo: () -> Unit, onConfirm: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.widthIn(max = 640.dp).shadow(12.dp, shape).clip(shape).background(BarDark).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            L("سيوضع: ", "Goes as: ") + what, fontSize = 13.5.sp, color = Color.White, modifier = Modifier.weight(1f, fill = false),
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(10.dp))
        Text(
            L("تراجع", "Undo"), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onUndo).padding(horizontal = 8.dp, vertical = 10.dp)
        )
        Text(
            L("ضعه هنا ✓", "Place here ✓"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BarDark,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Palette.Gold).clickable(onClick = onConfirm).padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun EmptyMap() {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        PhIcon(R.drawable.ph_mindmap, Palette.Gold, 56.dp)
        Spacer(Modifier.height(12.dp))
        Text(L("خريطتك فارغة", "Your map is empty"), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Palette.Navy)
        Text(
            L("من الملخصات ← «كما التُقط»: اضغط نصًا واختر «ضعه في الخريطة»، ثم المس مكانه على الخريطة.",
                "In Summaries → “As captured”: tap a text, choose “Place on the map”, then tap where it goes."),
            fontSize = 14.sp, color = Palette.TextSecondary, modifier = Modifier.padding(top = 6.dp), textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun NodeActions(
    node: MapNode,
    canUp: Boolean,
    canDown: Boolean,
    modifier: Modifier,
    onJump: () -> Unit,
    onMove: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
    onEdit: () -> Unit = {}
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.shadow(12.dp, shape).clip(shape).background(BarDark).horizontalScroll(rememberScrollState()).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionChip(L("ص ${node.mark.page + 1} · انتقال", "p. ${node.mark.page + 1} · Go"), primary = true, onClick = onJump)
        ActionChip("✎ " + L("تعديل النص", "Edit text"), onClick = onEdit)
        ActionChip(L("نقل إلى مكان آخر", "Move elsewhere"), onClick = onMove)
        ActionChip("↑ " + L("أعلى", "Up"), enabled = canUp, onClick = onUp)
        ActionChip("↓ " + L("أسفل", "Down"), enabled = canDown, onClick = onDown)
        if (node.children.isNotEmpty() || node.collapsed) ActionChip(if (node.collapsed) L("فتح", "Expand") else L("طيّ", "Collapse"), onClick = onToggle)
        ActionChip(L("إزالة من الخريطة", "Remove from map"), danger = true, onClick = onRemove)
        ActionChip("✕", onClick = onClose)
    }
}

@Composable
private fun ActionChip(label: String, primary: Boolean = false, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        label, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        color = when {
            primary -> BarDark
            danger -> Color(0xFFFFB4B0)
            else -> Color.White
        }.copy(alpha = if (enabled) 1f else 0.35f),
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (primary) Palette.Gold else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 11.dp, vertical = 8.dp)
    )
}

/** إطار متقطع حول العنصر. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.dashedBorder(color: Color, radius: Float, width: Float) {
    drawRoundRect(
        color, topLeft = Offset(width / 2, width / 2), size = Size(size.width - width, size.height - width),
        cornerRadius = CornerRadius(radius),
        style = Stroke(width = width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(width * 4, width * 3)))
    )
}

// ---------------------------------------------------------------- الترتيب والرسم

@Composable
private fun MapLayout(
    model: MindMapModel,
    placing: Boolean,
    ghosts: Set<String>,
    target: MapTarget?,
    selectedKey: String?,
    onTap: (String) -> Unit,
    onTarget: (MapTarget) -> Unit,
    modifier: Modifier = Modifier,
    onLongTap: (String) -> Unit = {}
) {
    // قائمة مسطحة بترتيب ثابت: المركز، ثم كل فرع وما تحته بأي عمق، وأماكن «+»
    val entries = remember(model, placing, ghosts) {
        val out = ArrayList<Entry>()
        out.add(Entry(EKind.CENTER, null, -1, -1))
        if (placing) out.add(Entry(EKind.PLUS_CENTER, null, -1, 0, MapTarget(null, null), label = L("فرع جديد", "New branch")))
        // أبناء عقدة (depth = عمق الأبناء)، ثم مكان «+» في آخرهم
        fun addKids(n: MapNode, nIdx: Int, depth: Int, bi: Int, nGhost: Boolean) {
            if (!n.collapsed || placing) {
                var prevReal: MapNode? = null
                n.children.forEach { ch ->
                    val g = ch.key in ghosts || nGhost
                    // ⊕ بين نقطتين تحت الفرع: في هذا الموضع بالضبط
                    if (placing && depth == 1 && !g && prevReal != null) {
                        out.add(Entry(EKind.PLUS_ROW, null, bi, nIdx, MapTarget(n.key, ch.key), depth = depth))
                    }
                    val idx = out.size
                    out.add(Entry(EKind.POINT, ch, bi, nIdx, ghost = g, depth = depth))
                    addKids(ch, idx, depth + 1, bi, g)
                    if (!g) prevReal = ch
                }
            }
            if (placing && !nGhost) {
                val label = if (depth == 1) L("نقطة", "Point") else L("نقطة فرعية", "Sub-point")
                out.add(Entry(EKind.PLUS_ROW, null, bi, nIdx, MapTarget(n.key, null), label = label, depth = depth))
            }
        }
        model.branches.forEachIndexed { bi, b ->
            val bIdx = out.size
            val bGhost = b.key in ghosts
            out.add(Entry(EKind.BRANCH, b, bi, 0, ghost = bGhost))
            addKids(b, bIdx, 1, bi, bGhost)
        }
        out
    }
    val geo = remember { MapGeometry() }
    val nb = model.branches.size
    // الفروع الأولى على اليمين (بداية القراءة العربية) والباقي على اليسار
    val rightCount = (nb + 1) / 2

    Layout(
        modifier = modifier.drawBehind {
            for (cv in geo.curves) {
                val mx = (cv.a.x + cv.b.x) / 2f
                val p = Path().apply {
                    moveTo(cv.a.x, cv.a.y)
                    cubicTo(mx, cv.a.y, mx, cv.b.y, cv.b.x, cv.b.y)
                }
                drawPath(
                    p, cv.color,
                    style = Stroke(
                        width = cv.width, cap = StrokeCap.Round,
                        pathEffect = if (cv.dashed) PathEffect.dashPathEffect(floatArrayOf(cv.width * 3, cv.width * 3)) else null
                    )
                )
            }
        },
        content = {
            entries.forEach { e ->
                val col = mapBranchColor(e.branch)
                when (e.kind) {
                    EKind.CENTER -> CenterNode(model)
                    EKind.BRANCH -> BranchNode(e.node!!, col, right = e.branch < rightCount, ghost = e.ghost, selected = selectedKey == e.node.key, onLongClick = { onLongTap(e.node.key) }) { onTap(e.node.key) }
                    EKind.POINT -> PointNode(e.node!!, col, depth = e.depth, ghost = e.ghost, selected = selectedKey == e.node.key, onLongClick = { onLongTap(e.node.key) }) { onTap(e.node.key) }
                    else -> PlusChip(
                        label = e.label,
                        big = e.kind == EKind.PLUS_CENTER,
                        on = target == e.target
                    ) { onTarget(e.target!!) }
                }
            }
        }
    ) { measurables, _ ->
        val dp = density
        fun px(v: Dp) = (v.value * dp).toInt()
        val ms = measurables.mapIndexed { i, m ->
            val e = entries[i]
            val maxW = when (e.kind) {
                EKind.CENTER -> 280.dp
                EKind.BRANCH -> 260.dp
                // كل مستوى أعمق أضيق قليلًا حتى لا تتمدد الخريطة كثيرًا
                EKind.POINT -> if (e.depth <= 1) 250.dp else max(160f, 220f - 15f * (e.depth - 2)).dp
                else -> 200.dp
            }
            m.measure(Constraints(maxWidth = px(maxW)))
        }
        val gapB = 40f * dp
        fun gapY(depth: Int) = (if (depth <= 1) 12f else 8f) * dp
        fun gapX(depth: Int) = when (depth) {
            0 -> 84f
            1 -> 54f
            else -> 34f
        } * dp
        fun lineW(depth: Int) = when (depth) {
            0 -> 3.5f
            1 -> 2.2f
            2 -> 1.6f
            else -> 1.3f
        } * dp
        val xs = FloatArray(ms.size)
        val ys = FloatArray(ms.size)
        val curves = ArrayList<MapCurve>()
        val ghostInk = Palette.Gold

        val cw = ms[0].width.toFloat()
        val ch = ms[0].height.toFloat()
        xs[0] = -cw / 2f
        ys[0] = -ch / 2f
        // «+ فرع جديد» تحت المركز
        entries.indices.firstOrNull { entries[it].kind == EKind.PLUS_CENTER }?.let { i ->
            xs[i] = -ms[i].width / 2f
            ys[i] = ch / 2f + 12f * dp
        }

        // أبناء كل عنصر (نقاط وأماكن «+»)
        val kids = HashMap<Int, MutableList<Int>>()
        entries.forEachIndexed { i, e ->
            if (e.kind == EKind.POINT || e.kind == EKind.PLUS_ROW) kids.getOrPut(e.parent) { ArrayList() }.add(i)
        }
        // ارتفاع أبناء كل عنصر مع ما تحتهم، ثم ارتفاع العنصر مع كل ما تحته (من الأعمق للأعلى)
        val kidsH = FloatArray(ms.size)
        val subH = FloatArray(ms.size)
        for (i in entries.indices.reversed()) {
            val k = kids[i]
            kidsH[i] = if (k.isNullOrEmpty()) 0f else k.sumOf { subH[it].toDouble() }.toFloat() + gapY(entries[k[0]].depth) * (k.size - 1)
            subH[i] = max(ms[i].height.toFloat(), kidsH[i])
        }
        fun kidsHeight(i: Int) = kidsH[i]
        fun blockHeight(i: Int) = subH[i]

        /** يضع العنصر وما تحته. edge = الحافة المواجهة لأبيه، top = أعلى كتلته. */
        fun place(i: Int, edge: Float, top: Float, side: Int, col: Color) {
            val e = entries[i]
            val w = ms[i].width.toFloat()
            val h = ms[i].height.toFloat()
            val bh = blockHeight(i)
            val x = if (side == 1) edge else edge - w
            val y = top + bh / 2f - h / 2f
            xs[i] = x; ys[i] = y
            val k = kids[i] ?: return
            var ky = top + bh / 2f - kidsHeight(i) / 2f
            for (c in k) {
                val ce = entries[c]
                val cEdge = if (side == 1) x + w + gapX(ce.depth) else x - gapX(ce.depth)
                place(c, cEdge, ky, side, col)
                if (ce.kind == EKind.POINT) {
                    val cw2 = ms[c].width.toFloat()
                    val chh = ms[c].height.toFloat()
                    curves.add(
                        MapCurve(
                            Offset(if (side == 1) x + w else x, y + h / 2f),
                            Offset(if (side == 1) xs[c] else xs[c] + cw2, ys[c] + chh / 2f),
                            if (ce.ghost) ghostInk else col, lineW(ce.depth), ce.ghost
                        )
                    )
                }
                ky += blockHeight(c) + gapY(ce.depth)
            }
        }

        val branchIdx = entries.indices.filter { entries[it].kind == EKind.BRANCH }
        for (side in listOf(1, -1)) {
            val list = branchIdx.filter { (entries[it].branch < rightCount) == (side == 1) }
            if (list.isEmpty()) continue
            val total = list.sumOf { blockHeight(it).toDouble() }.toFloat() + gapB * (list.size - 1)
            var y = -total / 2f
            for (bIdx in list) {
                val e = entries[bIdx]
                val col = if (e.ghost) ghostInk else mapBranchColor(e.branch)
                val edge = if (side == 1) cw / 2f + gapX(0) else -cw / 2f - gapX(0)
                place(bIdx, edge, y, side, mapBranchColor(e.branch))
                val bw = ms[bIdx].width.toFloat()
                val bHt = ms[bIdx].height.toFloat()
                curves.add(MapCurve(Offset(side * cw / 2f, 0f), Offset(if (side == 1) xs[bIdx] else xs[bIdx] + bw, ys[bIdx] + bHt / 2f), col, lineW(0), e.ghost))
                y += blockHeight(bIdx) + gapB
            }
        }

        // الحدود ثم الإزاحة لتبدأ من الصفر مع هامش
        var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        ms.forEachIndexed { i, p ->
            x0 = min(x0, xs[i]); y0 = min(y0, ys[i]); x1 = max(x1, xs[i] + p.width); y1 = max(y1, ys[i] + p.height)
        }
        val pad = 28f * dp
        val dx = pad - x0
        val dy = pad - y0
        geo.curves = curves.map { MapCurve(Offset(it.a.x + dx, it.a.y + dy), Offset(it.b.x + dx, it.b.y + dy), it.color, it.width, it.dashed) }
        val w = (x1 - x0 + 2 * pad).toInt().coerceAtLeast(1)
        val h = (y1 - y0 + 2 * pad).toInt().coerceAtLeast(1)
        layout(w, h) {
            ms.forEachIndexed { i, p -> p.place((xs[i] + dx).toInt(), (ys[i] + dy).toInt()) }
        }
    }
}

@Composable
private fun CenterNode(model: MindMapModel) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.shadow(14.dp, shape, ambientColor = MapCenterDeep, spotColor = MapCenterDeep).clip(shape)
            .background(Brush.radialGradient(listOf(MapCenterTop, Palette.Navy, MapCenterDeep), radius = 500f))
            .border(1.5.dp, Palette.Gold.copy(alpha = 0.7f), shape)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(model.title, fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        if (model.total > 0) Text(L("${model.total} نصوص", "${model.total} texts"), fontSize = 12.sp, color = Color(0xFFC9D4E6))
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BranchNode(n: MapNode, col: Color, right: Boolean, ghost: Boolean, selected: Boolean, onLongClick: () -> Unit = {}, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box {
        Text(
            n.text, fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 23.sp,
            color = if (ghost) GhostInk else Palette.Navy,
            modifier = Modifier.padding(top = 6.dp)
                .let { if (ghost) it else it.shadow(6.dp, shape) }
                .clip(shape).background(if (ghost) GhostBg else Color.White)
                .let {
                    if (ghost) it.drawBehind { dashedBorder(Palette.Gold, 16.dp.toPx(), 2.dp.toPx()) }
                    else it.border(if (selected) 2.dp else 1.dp, if (selected) Palette.Gold else Palette.Divider, shape)
                }
                .drawBehind {
                    if (!ghost) {
                        // شريط بلون الفرع على الجهة المواجهة للمركز
                        val bw = 5.dp.toPx()
                        val x = if (right) 0f else size.width - bw
                        drawRoundRect(col, Offset(x, 10.dp.toPx()), Size(bw, size.height - 20.dp.toPx()), CornerRadius(bw / 2))
                    }
                }
                .combinedClickable(enabled = !ghost, onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )
        if (n.collapsed && n.children.isNotEmpty()) {
            Box(
                Modifier.align(if (right) Alignment.TopEnd else Alignment.TopStart).size(22.dp).clip(CircleShape).background(Palette.Navy),
                contentAlignment = Alignment.Center
            ) { Text("${n.count()}", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PointNode(n: MapNode, col: Color, depth: Int, ghost: Boolean, selected: Boolean, onLongClick: () -> Unit = {}, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val kid = depth >= 2
    Text(
        n.text + if (n.collapsed && n.children.isNotEmpty()) "  (+${n.count()})" else "",
        fontSize = if (kid) 12.5.sp else 13.5.sp, lineHeight = 20.sp, color = if (ghost) GhostInk else Palette.TextPrimary,
        modifier = Modifier.clip(shape)
            .background(
                when {
                    ghost -> GhostBg
                    kid || selected -> Color.White
                    else -> col.copy(alpha = 0.08f)
                }
            )
            .let {
                if (ghost) it.drawBehind { dashedBorder(Palette.Gold, 12.dp.toPx(), 2.dp.toPx()) }
                else it.border(if (selected) 2.dp else 1.dp, if (selected) Palette.Gold else col.copy(alpha = 0.22f), shape)
            }
            .combinedClickable(enabled = !ghost, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

/** مكان «+»: فرع جديد، نقطة، بين نقطتين (بلا نص)، نقطة فرعية. */
@Composable
private fun PlusChip(label: String?, big: Boolean, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape).background(if (on) Palette.Gold else Color.White)
            .let { if (on) it.border(1.5.dp, Palette.GoldDeep, shape) else it.drawBehind { dashedBorder(Color(0xFFB8A36A), size.height / 2, 1.5.dp.toPx()) } }
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = if (label != null) 12.dp else 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(if (big) 28.dp else if (label == null) 20.dp else 24.dp).clip(CircleShape).background(if (on) Color.White else Palette.GoldSoft),
            contentAlignment = Alignment.Center
        ) { Text("+", fontSize = if (big) 18.sp else 15.sp, fontWeight = FontWeight.Bold, color = Palette.GoldDeep) }
        if (label != null) {
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = if (big) 14.sp else 12.sp, fontWeight = FontWeight.Bold, color = if (on) BarDark else Palette.GoldDeep)
        }
    }
}

// ---------------------------------------------------------------- التصدير

/** يضع الخريطة على خلفية عاجية مع اسم التطبيق في الزاوية. */
private suspend fun framed(src: Bitmap): Bitmap = withContext(Dispatchers.Default) {
    val sw = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src
    val out = Bitmap.createBitmap(sw.width, sw.height, Bitmap.Config.ARGB_8888)
    val cv = android.graphics.Canvas(out)
    cv.drawColor(Palette.Ivory.toArgb())
    cv.drawBitmap(sw, 0f, 0f, null)
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.GoldDeep.toArgb()
        textSize = (sw.width / 90f).coerceIn(18f, 44f)
    }
    val tag = L("هامش · Hamish", "Hamish")
    cv.drawText(tag, sw.width - p.measureText(tag) - p.textSize, sw.height - p.textSize, p)
    out
}

private fun exportDir(ctx: Context): File =
    File(ctx.cacheDir, "share").apply { mkdirs(); listFiles()?.forEach { it.delete() } }

private fun safeName(title: String) = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60).ifBlank { "map" }

private suspend fun writePng(ctx: Context, bmp: Bitmap, title: String): File = withContext(Dispatchers.IO) {
    val f = File(exportDir(ctx), safeName(title) + " - " + L("خريطة", "map") + ".png")
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    f
}

private suspend fun writePdf(ctx: Context, bmp: Bitmap, title: String): File = withContext(Dispatchers.IO) {
    val f = File(exportDir(ctx), safeName(title) + " - " + L("خريطة", "map") + ".pdf")
    val doc = PdfDocument()
    // صفحة بحجم الخريطة نفسها (نقاط PDF)، دون تشويه
    val scale = min(1f, 1600f / max(bmp.width, bmp.height))
    val pw = (bmp.width * scale).toInt().coerceAtLeast(1)
    val ph = (bmp.height * scale).toInt().coerceAtLeast(1)
    val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, 1).create())
    val dst = android.graphics.RectF(0f, 0f, pw.toFloat(), ph.toFloat())
    page.canvas.drawBitmap(bmp, null, dst, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
    doc.finishPage(page)
    f.outputStream().use { doc.writeTo(it) }
    doc.close()
    f
}

private fun shareFile(ctx: Context, f: File, mime: String) {
    val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
        .setType(mime)
        .putExtra(android.content.Intent.EXTRA_STREAM, uri)
        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val chooser = android.content.Intent.createChooser(send, L("مشاركة عبر", "Share via"))
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.startActivity(chooser)
}

/** يضيف صفحة فارغة في آخر المستند وعليها الخريطة كصورة. */
private suspend fun addAsPage(s: DocSession, bmp: Bitmap) {
    val doc = s.pdf ?: return
    val aspect = bmp.width.toFloat() / bmp.height.toFloat()
    val name = withContext(Dispatchers.IO) { AppController.repo.saveImage(s.id, bmp) }
    // صفحة أفقية للخريطة العريضة، وعمودية للطويلة (نسبة العرض إلى الارتفاع)
    val ratio = if (aspect >= 1f) 1.4142f else 0.7071f
    val last = doc.count - 1
    AppController.addBlankPage(s, last, "plain", 0xFFFFFFFF.toInt(), ratio)
    val page = last + 1
    val pageH = 1f / ratio
    var w = 0.92f
    if (w / aspect > pageH * 0.92f) w = pageH * 0.92f * aspect
    val item = ImageItem(UUID.randomUUID().toString(), name, page, (1f - w) / 2f, (pageH - w / aspect) / 2f, w, aspect)
    s.addImage(item)
    s.selectedImageId = null
    AppController.messages.tryEmit(L("أُضيفت الخريطة صفحةً في آخر المستند", "The map was added as the last page"))
}
