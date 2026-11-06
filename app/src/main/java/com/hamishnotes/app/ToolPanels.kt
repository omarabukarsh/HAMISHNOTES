package com.hamishnotes.app

import androidx.compose.ui.draw.alpha
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------- عناصر مشتركة

/** مفتاح صغير في لوحات الأدوات. */
@Composable
fun CompactSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) = PanelSwitch(label, checked, null, onChange)

@Composable
private fun PanelSwitch(label: String, checked: Boolean, icon: Int? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(36.dp).clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            PhIcon(icon, Palette.TextPrimary, 16.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(label, fontSize = 13.sp, color = Palette.TextPrimary, modifier = Modifier.weight(1f), maxLines = 2)
        Switch(
            checked = checked, onCheckedChange = onChange,
            modifier = Modifier.scale(0.72f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = Palette.Navy, checkedBorderColor = Palette.Navy
            )
        )
    }
}

@Composable
private fun PercentSlider(label: String, value: Float, steps: Int = 3, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = Palette.TextSecondary, modifier = Modifier.weight(1f))
        Text("${(value * 100).toInt()}%", fontSize = 12.sp, color = Palette.TextSecondary)
    }
    Slider(
        value = value, onValueChange = onChange, steps = steps,
        modifier = Modifier.height(26.dp),
        colors = SliderDefaults.colors(thumbColor = Palette.Navy, activeTrackColor = Palette.Navy)
    )
}

@Composable
private fun Divider() {
    Spacer(Modifier.height(6.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Divider))
    Spacer(Modifier.height(6.dp))
}

/** خيار في صف (بطاقة صغيرة فيها رسم وعنوان). */
@Composable
private fun OptionCard(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit, art: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.clip(shape).background(if (selected) Palette.Navy else Palette.Background)
            .clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        art()
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = if (selected) Color.White else Palette.TextPrimary, maxLines = 1)
    }
}

/** رسم صغير لنمط الخط. */
@Composable
fun LineStyleSample(style: Int, col: Color, width: Float = 3f) {
    Canvas(Modifier.width(44.dp).height(16.dp)) {
        val y = size.height / 2f
        drawLine(
            col, Offset(4f, y), Offset(size.width - 4f, y), strokeWidth = width, cap = StrokeCap.Round,
            pathEffect = dashEffect(style, width)
        )
    }
}

// ---------------------------------------------------------------- نمط الخط وسماكته

/** «إعدادات الخط»: متصل/متقطع/منقّط، والسماكة بالمليمتر، وزر الاستعادة. */
@Composable
fun StrokeStyleSection(style: Int, onStyle: (Int) -> Unit, size: Int, onSize: (Int) -> Unit, widthOf: (Int) -> Float, onReset: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(L("نمط الخط", "Line style"), fontSize = 12.sp, color = Palette.TextSecondary, modifier = Modifier.weight(1f))
        Text(PenPrefs.mm(widthOf(size)), fontSize = 12.sp, color = Palette.TextSecondary)
        Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onReset), contentAlignment = Alignment.Center) {
            PhIcon(R.drawable.ph_undo, Palette.TextSecondary, 18.dp, L("استعادة", "Reset"))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            LineStyle.SOLID to L("متصل", "Solid"),
            LineStyle.DASHED to L("متقطع", "Dashed"),
            LineStyle.DOTTED to L("منقّط", "Dotted")
        ).forEach { (st, label) ->
            OptionCard(label, style == st, Modifier.weight(1f), { onStyle(st) }) {
                LineStyleSample(st, if (style == st) Color.White else Palette.TextPrimary)
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    PanelLabel(L("السماكة", "Thickness"))
    SizeBar(size, onSize, Modifier.fillMaxWidth().height(28.dp))
}

// ---------------------------------------------------------------- الأحجام الثلاثة

/**
 * ثلاثة أحجام سريعة للأداة: نقرة تختار الحجم، ونقرة ثانية على الحجم المختار
 * تفتح شريطًا لتعديله.
 */
@Composable
fun SizeSlots(t: Tool) {
    val c = AppController
    val slots = PenPrefs.slots(t)
    val cur = c.sizeFor(t)
    Row(verticalAlignment = Alignment.CenterVertically) {
        slots.forEachIndexed { i, sz ->
            val sel = sz == cur
            var edit by remember { mutableStateOf(false) }
            Box {
                Box(
                    Modifier.size(width = 46.dp, height = 40.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (sel) Palette.NavySoft else Color.Transparent)
                        .clickable { if (sel) edit = true else c.setSize(sz) },
                    contentAlignment = Alignment.Center
                ) {
                    // خط بسماكة الحجم
                    val thick = (2 + sz * 1.3f).dp
                    Box(
                        Modifier.width(26.dp).height(thick).clip(RoundedCornerShape(50))
                            .background(if (sel) Palette.Navy else Palette.TextSecondary)
                    )
                }
                DropdownMenu(
                    expanded = edit, onDismissRequest = { edit = false },
                    shape = RoundedCornerShape(18.dp), containerColor = Color.White, shadowElevation = 12.dp
                ) {
                    Column(Modifier.width(260.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(L("الحجم ${i + 1}", "Size ${i + 1}"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary, modifier = Modifier.weight(1f))
                            Text(PenPrefs.mm(Sizes.width(t, slots[i])), fontSize = 12.sp, color = Palette.TextSecondary)
                        }
                        Spacer(Modifier.height(8.dp))
                        SizeBar(slots[i], { v -> PenPrefs.setSlot(t, i, v); c.setSize(v) }, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- إعدادات القلم المتقدمة

/** حدة الطرف، حساسية الضغط، تثبيت الخط، «ارسم وأبقِ». */
@Composable
fun PenAdvancedSection(tool: Tool) {
    val c = AppController
    Divider()
    StrokeStyleSection(
        style = PenPrefs.lineStyle, onStyle = { PenPrefs.changeLineStyle(it) },
        size = c.sizeFor(tool), onSize = { c.setSize(it) },
        widthOf = { Sizes.width(tool, it) },
        onReset = { PenPrefs.resetPen(); c.setSize(2) }
    )
    Divider()
    if (tool == Tool.FOUNTAIN || tool == Tool.BRUSH) {
        PercentSlider(L("حدة الطرف", "Tip sharpness"), PenPrefs.sharpness) { PenPrefs.changeSharpness(it) }
    }
    PercentSlider(L("حساسية الضغط", "Pressure sensitivity"), PenPrefs.pressure) { PenPrefs.changePressure(it) }
    PercentSlider(L("تثبيت الخط", "Stroke stabilization"), PenPrefs.stabilize, steps = 0) { PenPrefs.changeStabilize(it) }
}

/** إعدادات الهايلايتر: تثبيت الخط والرسم المستقيم. */
@Composable
fun HighlighterAdvancedSection() {
    Divider()
    PercentSlider(L("تثبيت الخط", "Stroke stabilization"), PenPrefs.hlStabilize, steps = 0) { PenPrefs.changeHlStabilize(it) }
    PanelSwitch(L("الرسم بخط مستقيم", "Draw in a straight line"), PenPrefs.hlStraight) { PenPrefs.changeHlStraight(it) }
}

// ---------------------------------------------------------------- الممحاة

/** فلتر الممحاة، والرجوع التلقائي للقلم، ومسح الصفحة. */
@Composable
fun EraserAdvancedSection() {
    val c = AppController
    var confirm by remember { mutableStateOf(false) }
    Divider()
    PanelLabel(L("فلتر الممحاة", "Eraser filter"))
    PanelSwitch(L("القلم", "Pen"), PenPrefs.erasePen, R.drawable.ph_pen) { PenPrefs.changeErasePen(it) }
    PanelSwitch(L("قلم الرصاص", "Pencil"), PenPrefs.erasePencil, R.drawable.ph_pencil) { PenPrefs.changeErasePencil(it) }
    PanelSwitch(L("الهايلايتر", "Highlighter"), PenPrefs.eraseHighlighter, R.drawable.ph_highlighter) { PenPrefs.changeEraseHighlighter(it) }
    PanelSwitch(L("الأشكال", "Shapes"), PenPrefs.eraseShapes, R.drawable.ph_shapes) { PenPrefs.changeEraseShapes(it) }
    Text(
        L("أطفئ أداة هنا لتحمي ما رسمته بها من المسح.", "Turn a tool off here to protect what you drew with it from erasing."),
        fontSize = 11.sp, lineHeight = 15.sp, color = Palette.TextSecondary
    )
    Divider()
    PanelSwitch(L("الرجوع إلى القلم تلقائيًا بعد المسح", "Auto-deselect (back to pen after erasing)"), PenPrefs.autoDeselect) {
        PenPrefs.changeAutoDeselect(it)
    }
    TextButton(onClick = { confirm = true }) {
        Text(L("مسح الصفحة", "Clear page"), color = Palette.Danger, fontWeight = FontWeight.Bold)
    }
    if (confirm) {
        val s = c.tabs.firstOrNull { it.id == c.activeId }
        val page = s?.currentPage() ?: 0
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(L("مسح الصفحة ${page + 1}؟", "Clear page ${page + 1}?")) },
            text = { Text(L("تُحذف الكتابة والتمييز والنصوص والصور من هذه الصفحة. يمكنك التراجع.", "Handwriting, highlights, text and images on this page are removed. You can undo.")) },
            confirmButton = {
                TextButton(onClick = { confirm = false; s?.clearPageItems(page, true, true, true, true) }) {
                    Text(L("مسح", "Clear"), color = Palette.Danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(L("إلغاء", "Cancel")) } }
        )
    }
}

// ---------------------------------------------------------------- التحديد الحر

/** نوع التحديد (حر/مستطيل) وما يدخل فيه. */
@Composable
fun LassoMenuContent(s: DocSession?, onOff: () -> Unit) {
    var changeFont by remember { mutableStateOf(false) }
    var toText by remember { mutableStateOf(false) }
    Column(Modifier.width(260.dp).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(L("التحديد", "Lasso selection"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
        Spacer(Modifier.height(6.dp))
        PanelLabel(L("نوع التحديد", "Lasso type"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionCard(L("حر", "Freehand"), !PenPrefs.lassoRect, Modifier.weight(1f), { PenPrefs.changeLassoRect(false) }) {
                PhIcon(R.drawable.ph_lasso, if (!PenPrefs.lassoRect) Color.White else Palette.TextPrimary)
            }
            OptionCard(L("مستطيل", "Rectangular"), PenPrefs.lassoRect, Modifier.weight(1f), { PenPrefs.changeLassoRect(true) }) {
                PhIcon(R.drawable.ph_select_rect, if (PenPrefs.lassoRect) Color.White else Palette.TextPrimary)
            }
        }
        Spacer(Modifier.height(12.dp))
        PanelLabel(L("يدخل في التحديد", "Included in selection"))
        PanelSwitch(L("الكتابة اليدوية", "Handwriting"), PenPrefs.lassoInk) { PenPrefs.changeLassoInk(it) }
        PanelSwitch(L("الهايلايتر", "Highlighter"), PenPrefs.lassoHighlight) { PenPrefs.changeLassoHighlight(it) }
        PanelSwitch(L("الأشكال والأسهم", "Shapes and arrows"), PenPrefs.lassoShapes) { PenPrefs.changeLassoShapes(it) }
        PanelSwitch(L("الصور", "Images"), PenPrefs.lassoImages) { PenPrefs.changeLassoImages(it) }
        PanelSwitch(L("مربعات النص", "Text boxes"), PenPrefs.lassoTexts) { PenPrefs.changeLassoTexts(it) }
        PanelSwitch(L("الملاحظات اللاصقة", "Sticky notes"), PenPrefs.lassoStickies) { PenPrefs.changeLassoStickies(it) }
        // أوامر ما بعد التحديد (نفس أوامر الشريط الذي يظهر فوق التحديد)
        Spacer(Modifier.height(12.dp))
        PanelLabel(L("بعد التحديد", "After selecting"))
        val has = s?.lassoSel?.isEmpty() == false
        if (!has) {
            Text(
                L("أحِط بالقلم حول شيء أولاً لتعمل هذه الأوامر.", "Draw around something first to use these."),
                fontSize = 12.sp, color = Palette.TextSecondary, lineHeight = 16.sp
            )
            Spacer(Modifier.height(6.dp))
        }
        Column(Modifier.alpha(if (has) 1f else 0.45f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LassoAction("Aa " + L("تغيير الخط", "Change font"), has, Modifier.weight(1f)) { if (Premium.require(PremiumFeature.TO_TEXT)) changeFont = true }
                LassoAction(L("إلى نص", "To text"), has, Modifier.weight(1f)) { if (Premium.require(PremiumFeature.TO_TEXT)) toText = true }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LassoAction("☺ " + L("ملصق", "Sticker"), has, Modifier.weight(1f)) { s?.let { StickerStore.saveFromLasso(it) } }
                LassoAction(L("نسخ", "Duplicate"), has, Modifier.weight(1f)) { s?.lassoDuplicate() }
                LassoAction(L("حذف", "Delete"), has, Modifier.weight(1f), Color(0xFFC62828)) { s?.lassoDelete() }
            }
            // تلوين المحدد
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(L("تلوين", "Recolor"), fontSize = 13.sp, color = Palette.TextPrimary, modifier = Modifier.padding(end = 4.dp))
                AppController.penPalette.take(6).forEach { col ->
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).clickable(enabled = has) { s?.lassoRecolor(col) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(22.dp).clip(CircleShape).background(col).border(1.dp, Palette.Divider, CircleShape))
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onOff) { Text(L("إيقاف التحديد", "Turn off lasso"), color = Palette.Navy, fontWeight = FontWeight.Bold) }
    }
    if (s != null && changeFont) ChangeFontDialog(s) { changeFont = false }
    if (s != null && toText) InkToTextDialog(s) { toText = false }
}

/** زر أمر من أوامر ما بعد التحديد (بنفس شكل خيارات النافذة). */
@Composable
private fun LassoAction(label: String, enabled: Boolean, modifier: Modifier, color: Color = Palette.TextPrimary, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(Palette.Background)
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 12.5.sp, color = color, maxLines = 1) }
}

// ---------------------------------------------------------------- الأشكال

private fun shapeIcon(k: ShapeKind): Int = when (k) {
    ShapeKind.LINE -> R.drawable.ph_shape_line
    ShapeKind.ELBOW -> R.drawable.ph_shape_elbow
    ShapeKind.CURVE -> R.drawable.ph_shape_curve
    ShapeKind.RECT -> R.drawable.ph_shape_rect
    ShapeKind.CIRCLE -> R.drawable.ph_shape_circle
    ShapeKind.TRIANGLE -> R.drawable.ph_shape_triangle
    ShapeKind.DIAMOND -> R.drawable.ph_shape_diamond
    ShapeKind.ELLIPSE -> R.drawable.ph_shape_ellipse
}

/** شريط الأشكال: التعرف على الأشكال، أنواع الأشكال، اللون، وإعدادات الخط. */
@Composable
fun ShapeBar() {
    val c = AppController
    val pill = RoundedCornerShape(26.dp)
    var strokeMenu by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(start = 64.dp, end = 64.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.shadow(6.dp, pill).background(Color.White, pill).border(1.dp, Palette.Divider, pill).clip(pill)) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // التعرّف على الأشكال المرسومة بالقلم
                // «ارسم وأبقِ»: التعرّف على الشكل المرسوم بالقلم عند إبقائه ثابتًا
                TBtn(selected = PenPrefs.drawAndHold, onClick = { PenPrefs.changeDrawAndHold(!PenPrefs.drawAndHold) }) {
                    PhIcon(R.drawable.ph_shape_detect, tintFor(PenPrefs.drawAndHold))
                }
                ToolbarSep()
                ShapeKind.values().forEach { k ->
                    val sel = PenPrefs.shapeKind == k
                    TBtn(selected = sel, onClick = { PenPrefs.changeShapeKind(k) }) { PhIcon(shapeIcon(k), tintFor(sel)) }
                }
                ToolbarSep()
                // زر واحد (كبسولة): نمط الخط ولونه، ويفتح نافذة إعدادات الشكل كلها
                Box {
                    Row(
                        Modifier.padding(horizontal = 4.dp).height(40.dp).clip(RoundedCornerShape(20.dp))
                            .background(Palette.NavySoft.copy(alpha = 0.5f))
                            .border(1.5.dp, Palette.Navy, RoundedCornerShape(20.dp))
                            .clickable { strokeMenu = true }
                            .padding(start = 10.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LineStyleSample(PenPrefs.shapeStyle, Color(PenPrefs.shapeColor), (1.5f + PenPrefs.shapeSize * 0.6f).coerceAtMost(6f))
                        Box(Modifier.size(18.dp).clip(CircleShape).background(Color(PenPrefs.shapeColor)).border(1.dp, Palette.Divider, CircleShape))
                        PhIcon(R.drawable.ph_caret_down, Palette.Navy, 12.dp)
                    }
                    DropdownMenu(
                        expanded = strokeMenu, onDismissRequest = { strokeMenu = false },
                        shape = RoundedCornerShape(20.dp), containerColor = Color.White, shadowElevation = 12.dp
                    ) {
                        ShapeSettingsWindow(
                            onClose = { strokeMenu = false },
                            onCustom = { strokeMenu = false; picker = true }
                        )
                    }
                }
                ToolbarSep()
                TBtn(onClick = { PenPrefs.shapeActive = false }) {
                    PhIcon(R.drawable.ph_close, Palette.TextSecondary, 20.dp, L("إغلاق الأشكال", "Close shapes"))
                }
            }
        }
    }
    if (picker) {
        ColorPickerDialog(
            initial = Color(PenPrefs.shapeColor),
            onPick = { col -> c.addCustomColor(col); PenPrefs.changeShapeColor(col.toArgb()); picker = false },
            onDismiss = { picker = false }
        )
    }
}

/** سماكات سريعة للأشكال (من مستويات Sizes). */
private val SHAPE_SIZES = listOf(1, 3, 5, 8)

/**
 * نافذة «إعدادات الشكل» الموحّدة: معاينة، اللون، نمط الخط، السماكة، الملء وشفافيته،
 * الزوايا المستديرة ورؤوس الأسهم. تفتح داخل قائمة منسدلة (بلا مكوّنات تقيس محتواها لاحقاً).
 */
@Composable
internal fun ShapeSettingsWindow(onClose: () -> Unit, onCustom: () -> Unit) {
    val c = AppController
    val col = Color(PenPrefs.shapeColor)
    val cardShape = RoundedCornerShape(12.dp)
    @Composable
    fun Choice(sel: Boolean, modifier: Modifier, h: androidx.compose.ui.unit.Dp = 42.dp, onClick: () -> Unit, content: @Composable () -> Unit) {
        Box(
            modifier.height(h).clip(cardShape)
                .background(if (sel) Palette.NavySoft else Palette.Background)
                .border(if (sel) 2.dp else 0.dp, if (sel) Palette.Navy else Color.Transparent, cardShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) { content() }
    }
    Column(Modifier.width(340.dp).padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(L("إعدادات الشكل", "Shape settings"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary, modifier = Modifier.weight(1f))
            Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                PhIcon(R.drawable.ph_close, Palette.TextSecondary, 18.dp, L("إغلاق", "Close"))
            }
        }
        // معاينة الشكل بإعداداته
        Canvas(Modifier.fillMaxWidth().height(70.dp).clip(cardShape).background(Color(0xFFFCF6E4))) {
            val sw = (1.5f + PenPrefs.shapeSize * 0.7f).dp.toPx()
            val l = size.width * 0.28f; val r = size.width * 0.72f
            val t = size.height * 0.2f; val b = size.height * 0.8f
            val cx = (l + r) / 2f; val cy = (t + b) / 2f
            val kind = PenPrefs.shapeKind
            val path = Path()
            when (kind) {
                ShapeKind.LINE -> { path.moveTo(l, b); path.lineTo(r, t) }
                ShapeKind.ELBOW -> { path.moveTo(l, t); path.lineTo(l, b); path.lineTo(r, b) }
                ShapeKind.CURVE -> { path.moveTo(l, b); path.cubicTo(cx, b, cx, t, r, t) }
                ShapeKind.RECT -> path.addRoundRect(androidx.compose.ui.geometry.RoundRect(l, t, r, b, if (PenPrefs.shapeRounded) 6.dp.toPx() else 0f, if (PenPrefs.shapeRounded) 6.dp.toPx() else 0f))
                ShapeKind.CIRCLE -> path.addOval(androidx.compose.ui.geometry.Rect(Offset(cx, cy), (b - t) / 2f))
                ShapeKind.ELLIPSE -> path.addOval(androidx.compose.ui.geometry.Rect(l, t, r, b))
                ShapeKind.TRIANGLE -> { path.moveTo(cx, t); path.lineTo(r, b); path.lineTo(l, b); path.close() }
                ShapeKind.DIAMOND -> { path.moveTo(cx, t); path.lineTo(r, cy); path.lineTo(cx, b); path.lineTo(l, cy); path.close() }
            }
            if (PenPrefs.shapeFill && kind.closed()) drawPath(path, col.copy(alpha = PenPrefs.shapeFillAlpha))
            drawPath(path, col, style = Stroke(sw, cap = StrokeCap.Round, join = if (PenPrefs.shapeRounded) StrokeJoin.Round else StrokeJoin.Miter, pathEffect = dashEffect(PenPrefs.shapeStyle, sw)))
        }
        PanelSmallLabel(L("اللون", "Color"))
        val colors = (c.penPalette + c.customColors.toList()).distinctBy { it.toArgb() }.take(7)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            colors.forEach { cl ->
                val sel = cl.toArgb() == PenPrefs.shapeColor
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(cl)
                        .border(if (sel) 2.5.dp else 1.dp, if (sel) Palette.Navy else Palette.Divider, CircleShape)
                        .clickable { PenPrefs.changeShapeColor(cl.toArgb()) }
                )
            }
            Box(
                Modifier.size(32.dp).clip(CircleShape).border(1.dp, Palette.Divider, CircleShape).clickable(onClick = onCustom),
                contentAlignment = Alignment.Center
            ) { PhIcon(R.drawable.ph_plus, Palette.TextSecondary, 18.dp, L("لون آخر", "Other color")) }
        }
        PanelSmallLabel(L("نمط الخط", "Line style"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(LineStyle.SOLID, LineStyle.DASHED, LineStyle.DOTTED).forEach { st ->
                Choice(PenPrefs.shapeStyle == st, Modifier.weight(1f), onClick = { PenPrefs.changeShapeStyle(st) }) {
                    LineStyleSample(st, Palette.TextPrimary)
                }
            }
        }
        PanelSmallLabel(L("السماكة", "Thickness"))
        val nearest = SHAPE_SIZES.minByOrNull { kotlin.math.abs(it - PenPrefs.shapeSize) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SHAPE_SIZES.forEach { sz ->
                Choice(nearest == sz, Modifier.weight(1f), h = 38.dp, onClick = { PenPrefs.changeShapeSize(sz) }) {
                    Box(Modifier.width(30.dp).height((1.5f + sz * 0.8f).dp).clip(RoundedCornerShape(50)).background(Palette.TextPrimary))
                }
            }
        }
        Divider()
        PanelSwitch(L("ملء بلون الشكل", "Fill with color"), PenPrefs.shapeFill) { PenPrefs.changeShapeFill(it) }
        if (PenPrefs.shapeFill) {
            PercentSlider(L("الشفافية", "Opacity"), PenPrefs.shapeFillAlpha, steps = 8) {
                PenPrefs.changeShapeFillAlpha(kotlin.math.round(it * 10f) / 10f)
            }
        }
        Divider()
        PanelSwitch(L("زوايا مستديرة", "Rounded corners"), PenPrefs.shapeRounded) { PenPrefs.changeShapeRounded(it) }
        PanelSmallLabel(L("رؤوس الأسهم (للخطوط): النهاية ثم البداية", "Arrowheads (lines): end, then start"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ArrowHead.values().forEach { h ->
                Box(Modifier.weight(1f)) { ArrowOption(h, start = false, selected = PenPrefs.arrowEnd == h) { PenPrefs.changeArrowEnd(h) } }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ArrowHead.values().forEach { h ->
                Box(Modifier.weight(1f)) { ArrowOption(h, start = true, selected = PenPrefs.arrowStart == h) { PenPrefs.changeArrowStart(h) } }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PanelSmallLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextSecondary, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
}

@Composable
private fun ArrowOption(h: ArrowHead, start: Boolean, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.fillMaxWidth().height(40.dp).clip(shape)
            .background(if (selected) Palette.NavySoft else Palette.Background)
            .border(if (selected) 1.5.dp else 0.dp, if (selected) Palette.Navy else Color.Transparent, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (h == ArrowHead.NONE) {
            Text(L("بدون", "None"), fontSize = 13.sp, color = Palette.TextPrimary)
        } else {
            val col = Palette.TextPrimary
            Canvas(Modifier.width(80.dp).height(20.dp)) {
                val y = size.height / 2f
                val x0 = 6f; val x1 = size.width - 6f
                drawLine(col, Offset(x0, y), Offset(x1, y), strokeWidth = 3f, cap = StrokeCap.Round)
                val tipX = if (start) x0 else x1
                val dir = if (start) 1f else -1f
                val path = Path().apply {
                    moveTo(tipX + dir * 14f, y - 8f)
                    lineTo(tipX, y)
                    lineTo(tipX + dir * 14f, y + 8f)
                    if (h == ArrowHead.CLOSED) close()
                }
                drawPath(path, col, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
