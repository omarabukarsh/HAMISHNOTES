package com.hamishnotes.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
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

// ---------------------------------------------------------------- مشترك مع درج الملخصات

/** مكان النص في الخريطة (شارة فوقه في «كما التُقط»). */
data class MapTag(val branch: Boolean, val color: Color, val parent: String?, val auto: Boolean)

fun mapTagsOf(model: MindMapModel): Map<String, MapTag> {
    val out = HashMap<String, MapTag>()
    for (b in model.branches) {
        if (b.mark != null) out[b.key] = MapTag(true, b.color, null, b.auto)
        for (p in b.children) {
            out[p.key] = MapTag(false, b.color, b.label, p.auto)
            for (k in p.children) out[k.key] = MapTag(false, b.color, p.label, k.auto)
        }
    }
    return out
}

/** لون الخطوط والشارات: أغمق من لون الهايلايتر الفاتح ليظهر على الخلفية العاجية. */
fun mapInk(c: Color): Color {
    val l = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
    val k = when {
        l > 0.75f -> 0.62f
        l > 0.55f -> 0.75f
        else -> 0.9f
    }
    return Color(c.red * k, c.green * k, c.blue * k, 1f)
}

private val MapCenterTop = Color(0xFF2C4F80)
private val MapCenterDeep = Color(0xFF0D1D35)
private val MapDot = Color(0xFFE4DFD3)

/** شريط وضع «اختيار عدة نصوص للخريطة» في درج الملخصات. */
@Composable
fun MapSelectBar(count: Int, onFirstBranch: () -> Unit, onToBranch: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF1A2230)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(
            if (count == 0) L("اختر النصوص بالترتيب: الأول يمكن أن يصبح فرعًا والباقي نقاطًا تحته", "Pick texts in order: the first can become a branch and the rest points under it")
            else L("اخترت $count · ماذا تفعل بها؟", "$count selected · what next?"),
            fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BarAction(Modifier.weight(1.2f), "◆ " + L("الأول فرع", "First is a branch"), L("والباقي نقاط تحته", "the rest go under it"), true, count > 0, onFirstBranch)
            BarAction(Modifier.weight(1f), "↳ " + L("إلى فرع…", "To a branch…"), L("كلها نقاط", "all as points"), false, count > 0, onToBranch)
            BarAction(Modifier.weight(0.6f), L("إلغاء", "Cancel"), null, false, true, onCancel)
        }
    }
}

@Composable
private fun BarAction(modifier: Modifier, title: String, sub: String?, primary: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(if (primary) Palette.Gold else Color.White.copy(alpha = 0.1f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val fg = if (primary) Color(0xFF1A2230) else Color.White
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg.copy(alpha = if (enabled) 1f else 0.4f), maxLines = 1)
        if (sub != null) Text(sub, fontSize = 10.5.sp, color = fg.copy(alpha = if (enabled) 0.75f else 0.3f), maxLines = 1)
    }
}

/** «نقطة في فرع»: يختار الفرع (أو نقطة داخله) مع معاينة قبل الإضافة. */
@Composable
fun MapBranchPicker(
    model: MindMapModel,
    items: List<MindMap.Item>,
    keys: List<String>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onMakeBranch: () -> Unit
) {
    val choices = remember(model, keys) {
        MindMap.branchChoices(model, items, keys.firstOrNull()).filter { it.key !in keys }
    }
    var chosen by remember(choices) { mutableStateOf(choices.firstOrNull()?.key) }
    val texts = keys.mapNotNull { k -> items.firstOrNull { it.key == k } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth(0.92f).widthIn(max = 460.dp).heightIn(max = 640.dp),
            shape = RoundedCornerShape(22.dp), color = Color.White
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    if (keys.size == 1) L("نقطة في فرع", "Point in a branch") else L("${keys.size} نصوص ← إلى فرع", "${keys.size} texts → to a branch"),
                    fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Palette.Navy
                )
                Text(
                    if (keys.size == 1) "«" + MindMap.shortLabel(texts.firstOrNull()?.text ?: "", emptyList()).first + "»"
                    else L("تُضاف بالترتيب الذي اخترته.", "Added in the order you picked."),
                    fontSize = 13.sp, color = Palette.TextSecondary, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    if (choices.isEmpty()) {
                        Text(
                            L("لا توجد فروع بعد. اجعل هذا النص فرعًا جديدًا أولًا.", "No branches yet. Make this text a new branch first."),
                            fontSize = 13.sp, color = Palette.TextSecondary, modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }
                    choices.forEachIndexed { i, b ->
                        val on = chosen == b.key || b.children.any { it.key == chosen }
                        val shape = RoundedCornerShape(14.dp)
                        Column(
                            Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(shape)
                                .background(if (on) Color(0xFFFFFCF3) else Color.White)
                                .border(if (on) 2.dp else 1.dp, if (on) Palette.Gold else Palette.Divider, shape)
                                .clickable { chosen = b.key }.padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(width = 12.dp, height = 32.dp).clip(RoundedCornerShape(6.dp)).background(mapInk(b.color)))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(b.label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        (if (i == 0) L("الأقرب قبله · ", "nearest before · ") else "") +
                                            if (b.children.isEmpty()) L("لا نقاط بعد", "no points yet") else L("${b.children.size} نقاط", "${b.children.size} points"),
                                        fontSize = 12.sp, color = Palette.TextSecondary
                                    )
                                }
                            }
                            // مستوى ثالث: تحت نقطة داخل الفرع
                            if (on && b.children.isNotEmpty()) {
                                Text(
                                    L("أو تحت نقطة:", "Or under a point:"), fontSize = 11.5.sp, color = Palette.GoldDeep,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    b.children.filter { it.key !in keys }.forEach { p ->
                                        val sel = chosen == p.key
                                        Text(
                                            p.label, fontSize = 12.sp, maxLines = 1,
                                            color = if (sel) Color.White else Palette.TextPrimary,
                                            modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                                .background(if (sel) Palette.Navy else b.color.copy(alpha = 0.22f))
                                                .clickable { chosen = if (sel) b.key else p.key }
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "◆ " + if (keys.size == 1) L("اجعله فرعًا جديدًا", "Make it a new branch") else L("الأول فرع جديد", "First as new branch"),
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.Navy,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Palette.NavySoft)
                            .clickable(onClick = onMakeBranch).padding(vertical = 12.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    val target = chosen
                    Text(
                        if (keys.size == 1) L("إضافة", "Add") else L("إضافة ${keys.size} نقاط", "Add ${keys.size} points"),
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (target != null) Palette.Navy else Palette.Navy.copy(alpha = 0.35f))
                            .clickable(enabled = target != null) { if (target != null) onAdd(target) }.padding(vertical = 12.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- شاشة الخريطة

/** الخطوط بين العقد، تُحسب أثناء الترتيب وتُرسم خلفها. */
private class MapCurve(val a: Offset, val b: Offset, val color: Color, val width: Float)

private class MapGeometry {
    var curves: List<MapCurve> = emptyList()
}

/** عقدة كما تُرسم: center = 0، فرع = 1، نقطة = 2، نقطة فرعية = 3. */
private class Placed(val node: MapNode?, val level: Int, val branch: Int, val parent: Int)

@Composable
fun MindMapDialog(s: DocSession, onJump: (TextMark) -> Unit, onDismiss: () -> Unit) {
    val c = AppController
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val all = s.allMarks()
    val mapEdits = s.summaryEdits.map
    var byColor by remember { mutableStateOf(false) }
    val model = remember(all, mapEdits, byColor) { MindMap.build(s.title, all, mapEdits, byColor) { c.labelOf(it) } }
    val items = remember(all) { MindMap.items(all) }
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val selected = selectedKey?.let { MindMap.find(model, it) }
    var renaming by remember { mutableStateOf<MapNode?>(null) }
    var moving by remember { mutableStateOf<String?>(null) }
    var exportMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }

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

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Palette.Ivory) {
            Column(Modifier.fillMaxSize()) {
                // ---------- الشريط العلوي (على الشاشات الضيقة: أزرار العرض في سطر ثانٍ)
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().background(Color.White)) {
                val compact = maxWidth < 640.dp
                Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Palette.Divider, RoundedCornerShape(12.dp))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) { Text(if (I18n.english) "‹" else "›", fontSize = 22.sp, color = Palette.Navy, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(L("الخريطة الذهنية", "Mind map"), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Palette.Navy, maxLines = 1)
                        Text(s.title, fontSize = 12.sp, color = Palette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (!compact) {
                        ModeSeg(byColor) { v -> byColor = v; selectedKey = null; fitted = false }
                        Spacer(Modifier.width(6.dp))
                    }
                    TopAction(R.drawable.ph_fit, L("توسيط", "Fit")) { fit() }
                    val branchKeys = model.branches.map { it.key }.toSet()
                    val anyOpen = model.branches.any { !it.collapsed && it.children.isNotEmpty() }
                    TopAction(R.drawable.ph_list, if (anyOpen) L("طيّ الكل", "Collapse all") else L("فتح الكل", "Expand all")) {
                        s.mapSetCollapsed(if (anyOpen) mapEdits.collapsed + branchKeys else mapEdits.collapsed - branchKeys)
                        fitted = false
                    }
                    Box {
                        Row(
                            Modifier.clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFFFFF6DD), Palette.GoldSoft)))
                                .border(1.dp, Palette.Gold, RoundedCornerShape(12.dp))
                                .clickable(enabled = model.branches.isNotEmpty()) { exportMenu = true }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PhIcon(R.drawable.ph_export, Palette.GoldDeep, 16.dp)
                            if (!compact) {
                                Spacer(Modifier.width(5.dp))
                                Text(L("تصدير", "Export"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.GoldDeep)
                            }
                        }
                        DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                            fun withBitmap(block: suspend (Bitmap) -> Unit) {
                                exportMenu = false
                                selectedKey = null
                                scope.launch {
                                    try {
                                        // إطاران حتى يختفي إطار العقدة المختارة قبل الالتقاط
                                        androidx.compose.runtime.withFrameNanos { }
                                        androidx.compose.runtime.withFrameNanos { }
                                        val img = layer.toImageBitmap().asAndroidBitmap()
                                        block(framed(img))
                                    } catch (t: Throwable) {
                                        c.messages.tryEmit(L("تعذر تجهيز الخريطة", "Could not prepare the map"))
                                    }
                                }
                            }
                            DropdownMenuItem(text = { Text(L("مشاركة صورة PNG", "Share as PNG image")) }, leadingIcon = { PhIcon(R.drawable.ph_image, Palette.TextSecondary, 18.dp) }, onClick = {
                                withBitmap { bmp -> shareFile(ctx, writePng(ctx, bmp, s.title), "image/png") }
                            })
                            DropdownMenuItem(text = { Text(L("مشاركة PDF", "Share as PDF")) }, leadingIcon = { PhIcon(R.drawable.ph_export, Palette.TextSecondary, 18.dp) }, onClick = {
                                withBitmap { bmp -> shareFile(ctx, writePdf(ctx, bmp, s.title), "application/pdf") }
                            })
                            DropdownMenuItem(text = { Text(L("إضافة صفحة في آخر المستند", "Add as a page at the end")) }, leadingIcon = { PhIcon(R.drawable.ph_add_page, Palette.TextSecondary, 18.dp) }, onClick = {
                                withBitmap { bmp ->
                                    addAsPage(s, bmp)
                                    onDismiss()
                                }
                            })
                        }
                    }
                    Box {
                        TopAction(R.drawable.ph_more, L("المزيد", "More")) { moreMenu = true }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(text = { Text(L("استعادة المسودة التلقائية", "Restore the automatic draft")) }, onClick = {
                                moreMenu = false; selectedKey = null; s.mapReset(); fitted = false
                            })
                            DropdownMenuItem(text = { Text(L("ابدأ من فارغ", "Start from empty")) }, onClick = {
                                moreMenu = false; selectedKey = null; s.mapStart(true); fitted = false
                            })
                        }
                    }
                }
                if (compact) {
                    Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
                        ModeSeg(byColor) { v -> byColor = v; selectedKey = null; fitted = false }
                    }
                }
                }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Divider))

                // ---------- مساحة الخريطة
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(0.dp))
                        .drawBehind {
                            // نقاط خفيفة كورق الرسم
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
                    if (model.branches.isEmpty()) {
                        EmptyMap(blank = mapEdits.blank && !byColor) { s.mapReset(); fitted = false }
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
                                selectedKey = selectedKey,
                                showAuto = !byColor && !mapEdits.blank,
                                onTap = { k -> selectedKey = if (selectedKey == k) null else k },
                                modifier = Modifier
                                    .onSizeChanged { content = it }
                                    .drawWithContent {
                                        layer.record { this@drawWithContent.drawContent() }
                                        drawLayer(layer)
                                    }
                            )
                        }
                    }

                    // شريط «مسودة جاهزة»
                    if (!byColor && !mapEdits.started && !mapEdits.blank && model.branches.isNotEmpty()) {
                        DraftBanner(
                            total = model.total,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
                            onStart = { s.mapStart(false) },
                            onBlank = { s.mapStart(true); fitted = false }
                        )
                    }

                    // إجراءات العقدة المختارة
                    if (selected != null) {
                        NodeActions(
                            node = selected,
                            isBranch = model.branches.any { it.key == selected.key },
                            byColor = byColor,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp, start = 12.dp, end = 12.dp),
                            onJump = { m -> onJump(m) },
                            onRename = { renaming = selected },
                            onMove = { moving = selected.key },
                            onMakeBranch = { s.mapMakeBranch(listOf(selected.key)) },
                            onToggle = { s.mapToggleCollapsed(selected.key) },
                            onHide = { s.mapHide(listOf(selected.key)); selectedKey = null },
                            onClose = { selectedKey = null }
                        )
                    } else {
                        Text(
                            L("انقر نقطة لإجراءاتها · حرّك وكبّر بإصبعين", "Tap a node for actions · pan and zoom with two fingers"),
                            fontSize = 12.sp, color = Palette.TextSecondary,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
                                .clip(RoundedCornerShape(12.dp)).background(Color.White)
                                .border(1.dp, Palette.Divider, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
    }

    renaming?.let { n ->
        var name by remember(n.key) { mutableStateOf(n.label) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(L("الاسم في الخريطة", "Name on the map"), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, singleLine = true)
                    val orig = items.firstOrNull { it.key == n.key }?.text
                    if (orig != null) {
                        Text(
                            L("النص الأصلي لا يتغير في الملخص: «", "The original text stays in the summary: “") + orig.take(120) + L("»", "”"),
                            fontSize = 12.sp, color = Palette.TextSecondary, modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { s.mapRename(n.key, name); renaming = null }) { Text(L("حفظ", "Save")) } },
            dismissButton = { TextButton(onClick = { s.mapRename(n.key, null); renaming = null }) { Text(L("الاسم التلقائي", "Automatic name")) } }
        )
    }

    moving?.let { k ->
        MapBranchPicker(
            model = model, items = items, keys = listOf(k),
            onDismiss = { moving = null },
            onAdd = { parent -> s.mapMakePoints(listOf(k), parent); moving = null },
            onMakeBranch = { s.mapMakeBranch(listOf(k)); moving = null }
        )
    }
}

@Composable
private fun ModeSeg(byColor: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFF1EEE7)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        SegItem(L("خريطتي", "My map"), !byColor) { onChange(false) }
        SegItem(L("حسب اللون", "By color"), byColor) { onChange(true) }
    }
}

@Composable
private fun SegItem(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else Palette.TextSecondary,
        modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(if (on) Palette.Navy else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
private fun TopAction(icon: Int, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { PhIcon(icon, Palette.Navy, 20.dp, desc) }
}

@Composable
private fun DraftBanner(total: Int, modifier: Modifier, onStart: () -> Unit, onBlank: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.padding(horizontal = 12.dp).shadow(10.dp, shape).clip(shape).background(Color.White)
            .border(1.dp, Palette.Gold, shape).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(11.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFFBE7A6), Palette.Gold))),
            contentAlignment = Alignment.Center
        ) { Text("✦", fontSize = 18.sp, color = Color(0xFF1A2230)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.widthIn(max = 440.dp)) {
            Text(L("مسودة جاهزة من $total تمييزًا", "A draft from $total highlights"), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Palette.Navy)
            Text(L("بُنيت تلقائيًا. صحّح ما لا يعجبك فقط، وكل تصحيح يُحفظ.", "Built automatically. Fix only what you don't like; every fix is saved."), fontSize = 12.sp, color = Palette.TextSecondary)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    L("ابدأ التصحيح", "Start fixing"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(11.dp)).background(Palette.Navy).clickable(onClick = onStart).padding(horizontal = 12.dp, vertical = 8.dp)
                )
                Text(
                    L("ابدأ من فارغ", "Start empty"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.Navy,
                    modifier = Modifier.clip(RoundedCornerShape(11.dp)).background(Palette.NavySoft).clickable(onClick = onBlank).padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyMap(blank: Boolean, onRestore: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        PhIcon(R.drawable.ph_mindmap, Palette.Gold, 56.dp)
        Spacer(Modifier.height(12.dp))
        Text(
            if (blank) L("خريطتك فارغة", "Your map is empty") else L("لا توجد نصوص للخريطة بعد", "No texts for the map yet"),
            fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Palette.Navy
        )
        Text(
            if (blank) L("من الملخصات ← «كما التُقط»: اضغط نصًا واختر «فرع جديد»، ثم أضف إليه نقاطًا.", "In Summaries → “As captured”: tap a text, choose “New branch”, then add points to it.")
            else L("ظلّل بالهايلايتر مع «التقاط النص»، وستظهر الخريطة هنا تلقائيًا.", "Highlight with “Text snap” on and the map appears here automatically."),
            fontSize = 14.sp, color = Palette.TextSecondary, modifier = Modifier.padding(top = 6.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (blank) {
            Spacer(Modifier.height(14.dp))
            Text(
                L("استعادة المسودة التلقائية", "Restore the automatic draft"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Palette.Navy).clickable(onClick = onRestore).padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun NodeActions(
    node: MapNode,
    isBranch: Boolean,
    byColor: Boolean,
    modifier: Modifier,
    onJump: (TextMark) -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onMakeBranch: () -> Unit,
    onToggle: () -> Unit,
    onHide: () -> Unit,
    onClose: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.shadow(12.dp, shape).clip(shape).background(Color(0xFF1A2230))
            .horizontalScroll(rememberScrollState()).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val m = node.mark
        if (m != null) ActionChip(L("ص ${m.page + 1} · انتقال", "p. ${m.page + 1} · Go"), true) { onJump(m) }
        ActionChip(L("تعديل الاسم", "Rename"), false, onRename)
        if (!byColor && m != null) {
            ActionChip(if (isBranch) L("اجعله نقطة…", "Make it a point…") else L("نقل إلى فرع", "Move to branch"), false, onMove)
            if (!isBranch) ActionChip(L("اجعله فرعًا", "Make it a branch"), false, onMakeBranch)
        }
        if (node.children.isNotEmpty() || node.collapsed) ActionChip(if (node.collapsed) L("فتح", "Expand") else L("طيّ", "Collapse"), false, onToggle)
        if (!byColor && m != null) ActionChip(L("إخفاء", "Hide"), false, onHide)
        ActionChip("✕", false, onClose)
    }
}

@Composable
private fun ActionChip(label: String, primary: Boolean, onClick: () -> Unit) {
    Text(
        label, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        color = if (primary) Color(0xFF1A2230) else Color.White,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (primary) Palette.Gold else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 8.dp)
    )
}

// ---------------------------------------------------------------- الترتيب والرسم

@Composable
private fun MapLayout(
    model: MindMapModel,
    selectedKey: String?,
    showAuto: Boolean,
    onTap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // قائمة مسطحة بترتيب ثابت: المركز ثم كل فرع ونقاطه
    val placed = remember(model) {
        val out = ArrayList<Placed>()
        out.add(Placed(null, 0, -1, -1))
        model.branches.forEachIndexed { bi, b ->
            val bIdx = out.size
            out.add(Placed(b, 1, bi, 0))
            if (!b.collapsed) b.children.forEach { p ->
                val pIdx = out.size
                out.add(Placed(p, 2, bi, bIdx))
                if (!p.collapsed) p.children.forEach { k -> out.add(Placed(k, 3, bi, pIdx)) }
            }
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
                drawPath(p, cv.color, style = Stroke(width = cv.width, cap = StrokeCap.Round))
            }
        },
        content = {
            placed.forEach { pl ->
                val n = pl.node
                when (pl.level) {
                    0 -> CenterNode(model)
                    1 -> BranchNode(n!!, right = pl.branch < rightCount, selected = selectedKey == n.key, showAuto = showAuto) { onTap(n.key) }
                    else -> {
                        val branchColor = model.branches[pl.branch].color
                        PointNode(n!!, branchColor, kid = pl.level == 3, selected = selectedKey == n.key) { onTap(n.key) }
                    }
                }
            }
        }
    ) { measurables, _ ->
        val dp = density
        val ms = measurables.mapIndexed { i, m ->
            val maxW = when (placed[i].level) {
                0 -> 280.dp; 1 -> 250.dp; else -> 240.dp
            }
            m.measure(Constraints(maxWidth = (maxW.value * dp).toInt()))
        }
        val gapB = 46f * dp
        val gapP = 12f * dp
        val gapKid = 8f * dp
        val toBranch = 80f * dp
        val toPoint = 52f * dp
        val toKid = 34f * dp
        val xs = FloatArray(ms.size)
        val ys = FloatArray(ms.size)
        val curves = ArrayList<MapCurve>()

        val cw = ms[0].width.toFloat()
        val ch = ms[0].height.toFloat()
        val cx = 0f
        val cy = 0f
        xs[0] = cx - cw / 2f
        ys[0] = cy - ch / 2f

        // ارتفاع كل فرع مع نقاطه
        fun rowHeight(pIdx: Int): Float {
            val kids = placed.indices.filter { placed[it].level == 3 && placed[it].parent == pIdx }
            val kh = if (kids.isEmpty()) 0f else kids.sumOf { ms[it].height.toDouble() }.toFloat() + gapKid * (kids.size - 1)
            return max(ms[pIdx].height.toFloat(), kh)
        }
        fun pointsOf(bIdx: Int) = placed.indices.filter { placed[it].level == 2 && placed[it].parent == bIdx }
        fun blockHeight(bIdx: Int): Float {
            val pts = pointsOf(bIdx)
            val ph = if (pts.isEmpty()) 0f else pts.sumOf { rowHeight(it).toDouble() }.toFloat() + gapP * (pts.size - 1)
            return max(ms[bIdx].height.toFloat(), ph)
        }

        val branchIdx = placed.indices.filter { placed[it].level == 1 }
        for (side in listOf(1, -1)) {
            val list = branchIdx.filter { (placed[it].branch < rightCount) == (side == 1) }
            if (list.isEmpty()) continue
            val total = list.sumOf { blockHeight(it).toDouble() }.toFloat() + gapB * (list.size - 1)
            var y = cy - total / 2f
            for (bIdx in list) {
                val bh = blockHeight(bIdx)
                val bw = ms[bIdx].width.toFloat()
                val bHt = ms[bIdx].height.toFloat()
                val bx = if (side == 1) cx + cw / 2f + toBranch else cx - cw / 2f - toBranch - bw
                val by = y + bh / 2f - bHt / 2f
                xs[bIdx] = bx; ys[bIdx] = by
                val col = mapInk(placed[bIdx].node!!.color)
                curves.add(MapCurve(Offset(if (side == 1) cx + cw / 2f else cx - cw / 2f, cy), Offset(if (side == 1) bx else bx + bw, by + bHt / 2f), col, 3.5f * dp))
                val pts = pointsOf(bIdx)
                val ph = if (pts.isEmpty()) 0f else pts.sumOf { rowHeight(it).toDouble() }.toFloat() + gapP * (pts.size - 1)
                var py = y + bh / 2f - ph / 2f
                for (pIdx in pts) {
                    val rh = rowHeight(pIdx)
                    val pw = ms[pIdx].width.toFloat()
                    val pHt = ms[pIdx].height.toFloat()
                    val px = if (side == 1) bx + bw + toPoint else bx - toPoint - pw
                    val ppy = py + rh / 2f - pHt / 2f
                    xs[pIdx] = px; ys[pIdx] = ppy
                    curves.add(MapCurve(Offset(if (side == 1) bx + bw else bx, by + bHt / 2f), Offset(if (side == 1) px else px + pw, ppy + pHt / 2f), col, 2.2f * dp))
                    var ky = py
                    for (kIdx in placed.indices.filter { placed[it].level == 3 && placed[it].parent == pIdx }) {
                        val kw = ms[kIdx].width.toFloat()
                        val kHt = ms[kIdx].height.toFloat()
                        val kx = if (side == 1) px + pw + toKid else px - toKid - kw
                        xs[kIdx] = kx; ys[kIdx] = ky
                        curves.add(MapCurve(Offset(if (side == 1) px + pw else px, ppy + pHt / 2f), Offset(if (side == 1) kx else kx + kw, ky + kHt / 2f), col, 1.6f * dp))
                        ky += kHt + gapKid
                    }
                    py += rh + gapP
                }
                y += bh + gapB
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
        geo.curves = curves.map { MapCurve(Offset(it.a.x + dx, it.a.y + dy), Offset(it.b.x + dx, it.b.y + dy), it.color, it.width) }
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
        Text(model.title, fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(L("${model.total} تمييزًا", "${model.total} highlights"), fontSize = 12.sp, color = Color(0xFFC9D4E6))
    }
}

@Composable
private fun BranchNode(n: MapNode, right: Boolean, selected: Boolean, showAuto: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val ink = mapInk(n.color)
    Box {
        Column(
            Modifier.padding(top = 8.dp).shadow(6.dp, shape).clip(shape).background(Color.White)
                .border(if (selected) 2.dp else 1.dp, if (selected) Palette.Gold else Palette.Divider, shape)
                .drawBehind {
                    // شريط بلون الفرع على الجهة المواجهة للمركز
                    val bw = 5.dp.toPx()
                    val x = if (right) 0f else size.width - bw
                    drawRoundRect(ink, Offset(x, 10.dp.toPx()), androidx.compose.ui.geometry.Size(bw, size.height - 20.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(bw / 2))
                }
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                (if (n.star) "★ " else "") + n.label, fontFamily = Fonts.Display, fontWeight = FontWeight.Bold,
                fontSize = 16.sp, color = Palette.Navy, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (n.sub != null) Text(n.sub, fontSize = 11.5.sp, color = Palette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (showAuto && n.mark != null) {
            val tagShape = RoundedCornerShape(8.dp)
            Text(
                if (n.auto) L("تلقائي", "auto") else "✓ " + L("صحّحته", "fixed"),
                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = if (n.auto) Palette.GoldDeep else Color.White,
                modifier = Modifier.align(if (right) Alignment.TopStart else Alignment.TopEnd).padding(horizontal = 12.dp)
                    .clip(tagShape).background(if (n.auto) Color.White else Palette.Navy)
                    .border(1.dp, if (n.auto) Palette.Gold.copy(alpha = 0.7f) else Palette.Navy, tagShape)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
        if (n.collapsed && n.children.isNotEmpty()) {
            Box(
                Modifier.align(if (right) Alignment.TopEnd else Alignment.TopStart).size(22.dp).clip(CircleShape).background(Palette.Navy),
                contentAlignment = Alignment.Center
            ) { Text("${n.count()}", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun PointNode(n: MapNode, branchColor: Color, kid: Boolean, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val tint = branchColor.copy(alpha = 0.22f)
    Row(
        Modifier.clip(shape).background(if (kid || selected) Color.White else tint)
            .border(if (selected) 2.dp else 1.dp, if (selected) Palette.Gold else tint, shape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (n.star) {
            Text("★ ", fontSize = 13.5.sp, color = Palette.GoldDeep)
        }
        Text(
            n.label + if (n.collapsed && n.children.isNotEmpty()) "  (+${n.count()})" else "",
            fontSize = 13.5.sp, fontWeight = if (n.star) FontWeight.Bold else FontWeight.Normal,
            color = Palette.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
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
