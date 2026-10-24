package com.mynotes.app

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
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

@Composable
private fun PanelSwitch(label: String, checked: Boolean, icon: Int? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            PhIcon(icon, Palette.TextPrimary, 20.dp)
            Spacer(Modifier.width(12.dp))
        }
        Text(label, fontSize = 15.sp, color = Palette.TextPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
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
        colors = SliderDefaults.colors(thumbColor = Palette.Navy, activeTrackColor = Palette.Navy)
    )
}

@Composable
private fun Divider() {
    Spacer(Modifier.height(10.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Divider))
    Spacer(Modifier.height(10.dp))
}

/** خيار في صف (بطاقة صغيرة فيها رسم وعنوان). */
@Composable
private fun OptionCard(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit, art: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.clip(shape).background(if (selected) Palette.Navy else Palette.Background)
            .clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        art()
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = if (selected) Color.White else Palette.TextPrimary, maxLines = 1)
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
        Text(L("إعدادات الخط", "Stroke settings"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary, modifier = Modifier.weight(1f))
        Text(PenPrefs.mm(widthOf(size)), fontSize = 12.sp, color = Palette.TextSecondary)
        Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onReset), contentAlignment = Alignment.Center) {
            PhIcon(R.drawable.ph_undo, Palette.TextSecondary, 18.dp, L("استعادة", "Reset"))
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    Spacer(Modifier.height(10.dp))
    PanelLabel(L("سماكة القلم", "Pen thickness"))
    SizeBar(size, onSize, Modifier.fillMaxWidth())
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
        fontSize = 12.sp, lineHeight = 18.sp, color = Palette.TextSecondary
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
        val page = s?.listState?.firstVisibleItemIndex ?: 0
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
fun LassoMenuContent(onOff: () -> Unit) {
    Column(Modifier.width(300.dp).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(L("التحديد", "Lasso selection"), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
        Spacer(Modifier.height(10.dp))
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
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onOff) { Text(L("إيقاف التحديد", "Turn off lasso"), color = Palette.Navy, fontWeight = FontWeight.Bold) }
    }
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
    var colorMenu by remember { mutableStateOf(false) }
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
                Box {
                    TBtn(onClick = { strokeMenu = true }) { LineStyleSample(PenPrefs.shapeStyle, Palette.TextPrimary, 4f) }
                    DropdownMenu(
                        expanded = strokeMenu, onDismissRequest = { strokeMenu = false },
                        shape = RoundedCornerShape(18.dp), containerColor = Color.White, shadowElevation = 12.dp
                    ) { ShapeStrokePanel() }
                }
                Box {
                    Box(
                        Modifier.size(42.dp).clip(CircleShape).clickable { colorMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(Color(PenPrefs.shapeColor)).border(1.dp, Palette.Divider, CircleShape))
                    }
                    DropdownMenu(
                        expanded = colorMenu, onDismissRequest = { colorMenu = false },
                        shape = RoundedCornerShape(18.dp), containerColor = Color.White, shadowElevation = 12.dp
                    ) {
                        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (c.penPalette + c.customColors.toList()).take(10).forEach { col ->
                                val sel = col.toArgb() == PenPrefs.shapeColor
                                Box(
                                    Modifier.size(32.dp).clip(CircleShape).background(col)
                                        .border(if (sel) 2.5.dp else 1.dp, if (sel) Palette.Navy else Palette.Divider, CircleShape)
                                        .clickable { PenPrefs.changeShapeColor(col.toArgb()); colorMenu = false }
                                )
                            }
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).clickable { colorMenu = false; picker = true },
                                contentAlignment = Alignment.Center
                            ) { PhIcon(R.drawable.ph_plus, Palette.TextSecondary, 18.dp) }
                        }
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

/** إعدادات خط الأشكال: النمط، السماكة، الزوايا المستديرة، رؤوس الأسهم. */
@Composable
private fun ShapeStrokePanel() {
    Column(Modifier.width(320.dp).padding(horizontal = 16.dp, vertical = 10.dp)) {
        StrokeStyleSection(
            style = PenPrefs.shapeStyle, onStyle = { PenPrefs.changeShapeStyle(it) },
            size = PenPrefs.shapeSize, onSize = { PenPrefs.changeShapeSize(it) },
            widthOf = { Sizes.width(Tool.BALLPOINT, it) },
            onReset = { PenPrefs.resetShape() }
        )
        Divider()
        PanelSwitch(L("زوايا مستديرة", "Rounded corners"), PenPrefs.shapeRounded) { PenPrefs.changeShapeRounded(it) }
        Spacer(Modifier.height(8.dp))
        PanelLabel(L("رؤوس الأسهم (للخطوط والموصلات)", "Arrowheads (lines and connectors)"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ArrowHead.values().forEach { h ->
                    ArrowOption(h, start = true, selected = PenPrefs.arrowStart == h) { PenPrefs.changeArrowStart(h) }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ArrowOption(ArrowHead.NONE, start = false, selected = PenPrefs.arrowEnd == ArrowHead.NONE) { PenPrefs.changeArrowEnd(ArrowHead.NONE) }
                ArrowOption(ArrowHead.OPEN, start = false, selected = PenPrefs.arrowEnd == ArrowHead.OPEN) { PenPrefs.changeArrowEnd(ArrowHead.OPEN) }
                ArrowOption(ArrowHead.CLOSED, start = false, selected = PenPrefs.arrowEnd == ArrowHead.CLOSED) { PenPrefs.changeArrowEnd(ArrowHead.CLOSED) }
            }
        }
    }
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
