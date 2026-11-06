package com.hamishnotes.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.min

// ---------------------------------------------------------------- رسم القلم

/** ألوان الجسم المعدني: أسود، عاجي، فضي، كحلي (لمعة، وسط، ظل، حافة سفلية). */
private val Finishes = listOf(
    listOf(Color(0xFF5B6170), Color(0xFF2A2F3A), Color(0xFF11141A), Color(0xFF3A404C)),
    listOf(Color(0xFFFFFFFF), Color(0xFFF1ECE2), Color(0xFFCFC6B4), Color(0xFFE9E3D6)),
    listOf(Color(0xFFF4F5F7), Color(0xFFC9CED6), Color(0xFF8E95A1), Color(0xFFD8DCE2)),
    listOf(Color(0xFF4A6A99), Color(0xFF1B365D), Color(0xFF0C1A30), Color(0xFF29466F))
)
private val GoldStops = listOf(Color(0xFFFBE7A6), Color(0xFFC9A44C), Color(0xFF7A5A16))
private val TipStops = listOf(Color(0xFFE9E6DF), Color(0xFFB9B3A6), Color(0xFF6F695E))
private val HeroTop = Color(0xFF2C4F80)
private val HeroDeep = Color(0xFF0D1D35)
private val LiveGreen = Color(0xFF4ADE80)

/**
 * قلم فخم مرسوم: جسم أسطواني معدني، طرف، حلقة ذهبية، زر جانبي أو طرف ممحاة، ظل ناعم ولمعة.
 * tip: موضع رأس القلم، angle: زاوية الميل (سالب = نحو الأعلى)، shine: موضع اللمعة المتحركة 0..1 (أو سالب بلا لمعة).
 */
internal fun DrawScope.drawPenArt(
    shape: PenShape, finish: Int, tip: Offset, length: Float, angle: Float, shine: Float = -1f
) {
    val f = Finishes[finish.coerceIn(0, Finishes.size - 1)]
    val h = length * (if (shape.slim) 0.058f else 0.072f)
    val tipLen = length * 0.14f
    val r = if (shape.flat) h * 0.24f else h / 2f
    val top = -h / 2f

    // ظل ناعم أسفل القلم (طبقات شفافة متدرجة بدل التمويه)
    rotate(angle, pivot = tip) {
        translate(tip.x, tip.y) {
            for (k in 6 downTo 1) {
                val spread = k * h * 0.22f
                drawRoundRect(
                    Color.Black.copy(alpha = 0.045f),
                    topLeft = Offset(tipLen * 0.6f - spread, top + h * 0.9f - spread * 0.4f + h * 0.55f),
                    size = Size(length - tipLen * 0.6f + spread * 2f, h + spread * 0.8f),
                    cornerRadius = CornerRadius(h)
                )
            }
        }
    }

    rotate(angle, pivot = tip) {
        translate(tip.x, tip.y) {
            // الطرف المخروطي ورأسه
            val cone = Path().apply {
                moveTo(0f, 0f)
                lineTo(tipLen, top + h * 0.1f)
                lineTo(tipLen, -top - h * 0.1f)
                close()
            }
            drawPath(cone, Brush.verticalGradient(TipStops, startY = top, endY = -top))
            drawCircle(Color(0xFF2A2A2A), radius = h * 0.11f, center = Offset(h * 0.08f, 0f))

            // الجسم
            drawRoundRect(
                Brush.verticalGradient(
                    0f to f[0], 0.35f to f[1], 0.8f to f[2], 1f to f[3],
                    startY = top, endY = -top
                ),
                topLeft = Offset(tipLen - h * 0.1f, top),
                size = Size(length - tipLen + h * 0.1f, h),
                cornerRadius = CornerRadius(r)
            )
            // الجانب المسطّح: انعكاس خفيف على الحافة العليا
            if (shape.flat) {
                drawRect(
                    Color.White.copy(alpha = 0.10f),
                    topLeft = Offset(tipLen + h * 0.3f, top),
                    size = Size(length - tipLen - h * 2f, h * 0.18f)
                )
            }
            // طرف الممحاة
            if (shape.eraser) {
                drawRoundRect(
                    Brush.verticalGradient(listOf(f[1], f[2]), startY = top, endY = -top),
                    topLeft = Offset(length - h * 1.2f, top + h * 0.05f),
                    size = Size(h * 1.2f, h * 0.9f),
                    cornerRadius = CornerRadius(h * 0.45f)
                )
            }
            // الحلقة الذهبية
            val ringX = if (shape.eraser) length - h * 1.6f else length - h * 2.9f
            drawRect(
                Brush.verticalGradient(GoldStops, startY = top, endY = -top),
                topLeft = Offset(ringX, top), size = Size(h * 0.32f, h)
            )
            // الزر الجانبي
            if (shape.button) {
                drawRoundRect(
                    Brush.verticalGradient(listOf(f[1], f[2]), startY = top - h * 0.12f, endY = top + h * 0.12f),
                    topLeft = Offset(tipLen + length * 0.2f, top - h * 0.12f),
                    size = Size(length * 0.1f, h * 0.24f),
                    cornerRadius = CornerRadius(h * 0.12f)
                )
            }
            // اللمعة الثابتة على طول الجسم
            drawRoundRect(
                Brush.horizontalGradient(
                    listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0f)),
                    startX = tipLen + h * 0.5f, endX = length - h * 3f
                ),
                topLeft = Offset(tipLen + h * 0.5f, top + h * 0.14f),
                size = Size(length - tipLen - h * 3.5f, h * 0.14f),
                cornerRadius = CornerRadius(h * 0.07f)
            )
            // لمعة متحركة تمر على الجسم (عند اتصال القلم)
            if (shine in 0f..1f) {
                val cx = tipLen + (length - tipLen) * shine
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0f)),
                        startX = cx - h * 1.6f, endX = cx + h * 1.6f
                    ),
                    topLeft = Offset((cx - h * 1.6f).coerceAtLeast(tipLen), top),
                    size = Size(h * 3.2f, h)
                )
            }
        }
    }
}

// ---------------------------------------------------------------- قسم «الأقلام»

/** قسم «الأقلام» في إعدادات القلم: القلم المكتشف، وضع الكتابة، وجرّب قلمك. */
@Composable
fun PensSection() {
    // الإعدادات نافذة مستقلة: نراقب القلم فيها أيضًا
    PenWindowObserver()
    DisposableEffect(Unit) {
        PenInput.monitoring++
        onDispose { PenInput.monitoring-- }
    }
    var viewKey by remember { mutableStateOf<String?>(null) }
    val pen = PenInput.pens.firstOrNull { it.key == viewKey } ?: PenInput.current()
    var renameFor by remember { mutableStateOf<PenInfo?>(null) }

    // «متصل الآن»: القلم قريب من الشاشة أو لمسها قبل لحظة
    var active by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            active = PenInput.stylusActiveRecently(1500L)
            delay(350L)
        }
    }
    val showingActive = active && pen != null && PenInput.liveKey == pen.key

    PenLabel(L("قلمك", "Your pen"))
    PenHeroCard(pen, showingActive)

    if (pen != null) {
        // لون الرسم: لا يمكن معرفة لون القلم، فيختاره المستخدم
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(L("لون الرسم:", "Art color:"), fontSize = 12.sp, color = Palette.TextSecondary)
            Spacer(Modifier.width(10.dp))
            Finishes.forEachIndexed { i, f ->
                val on = pen.finish == i
                Box(
                    Modifier.padding(horizontal = 5.dp).size(28.dp)
                        .border(2.dp, if (on) Palette.Gold else Color.Transparent, CircleShape)
                        .padding(3.dp).clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(f[0], f[2])))
                        .border(1.dp, Palette.Divider, CircleShape)
                        .clickable { PenInput.changeFinish(pen.key, i) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        PenGroup {
            PenRow(
                R.drawable.ph_check, if (pen.calibrated) Color(0xFF2E9D6B) else Color(0xFFD9A21B),
                L("معايرة الضغط", "Pressure calibration"),
                when {
                    pen.flatPressure -> L("القلم لا يرسل ضغطًا: عرض الخط يتبع سرعة الكتابة", "This pen sends no pressure: line width follows writing speed")
                    pen.calibrated -> L("تمّت تلقائيًا من كتابتك", "Done automatically from your writing")
                    else -> L("تتم تلقائيًا أثناء كتابتك العادية", "Happens automatically as you write")
                }
            ) {
                PenChipButton(L("إعادة", "Reset")) {
                    PenInput.recalibrate(pen.key)
                    AppController.messages.tryEmit(L("ستُعاد المعايرة من كتابتك القادمة", "Calibration restarts from your next writing"))
                }
            }
            PenDivider()
            PenRow(R.drawable.ph_pen, Color(0xFF2F6FDB), L("الاسم", "Name"), pen.displayName) {
                PenChipButton(L("تسمية", "Rename")) { renameFor = pen }
            }
        }
    }

    PenLabel(L("وضع الكتابة", "Writing mode"))
    PenGroup {
        Row(
            Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFF1EEE7)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ModeOption(Modifier.weight(1f), R.drawable.ph_pen_nib, L("القلم فقط", "Pen only"), !PenInput.fingerWrites) {
                PenInput.chooseFingerWrites(false)
            }
            ModeOption(Modifier.weight(1f), R.drawable.ph_hand_pointing, L("القلم والإصبع", "Pen and finger"), PenInput.fingerWrites) {
                PenInput.chooseFingerWrites(true)
            }
        }
        Text(
            if (PenInput.fingerWrites) L("إصبع واحد يكتب، وإصبعان يمرّران ويكبّران. إن لمس القلم الشاشة تُتجاهل راحة يدك.",
                "One finger writes; two fingers scroll and zoom. When the pen touches the screen, your palm is ignored.")
            else L("القلم يكتب، والإصبع يمرّر ويكبّر ولا يترك خطوطًا.", "The pen writes; your finger scrolls and zooms and leaves no marks."),
            fontSize = 12.sp, lineHeight = 17.sp, color = Palette.TextSecondary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
        )
        PenDivider()
        PenRow(
            R.drawable.ph_hand_pointing, Color(0xFFD9A21B),
            L("زر التبديل في شريط الأدوات", "Toggle button in the toolbar"),
            L("بدّل بين القلم والإصبع بلمسة", "Switch between pen and finger with one tap"),
            onClick = { PenInput.changeShowToggle(!PenInput.showToggle) }
        ) {
            Switch(
                checked = PenInput.showToggle, onCheckedChange = { PenInput.changeShowToggle(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Palette.Navy, checkedBorderColor = Palette.Navy)
            )
        }
    }

    PenLabel(L("جرّب قلمك", "Try your pen"))
    TryPenPad()

    val others = PenInput.pens.filter { it.key != pen?.key }
    if (others.isNotEmpty()) {
        PenLabel(L("أقلام أخرى على هذا الجهاز", "Other pens on this device"))
        PenGroup {
            others.forEachIndexed { i, p ->
                if (i > 0) PenDivider()
                Row(
                    Modifier.fillMaxWidth().clickable { viewKey = p.key }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Canvas(Modifier.size(width = 64.dp, height = 36.dp)) {
                        val ltr = layoutDirection == LayoutDirection.Ltr
                        scale(if (ltr) -1f else 1f, 1f) {
                            drawPenArt(p.family.shape(), p.finish, Offset(size.width * 0.06f, size.height * 0.82f), size.width * 0.95f, -20f)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.displayName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
                        val ago = if (p.lastUsed > 0) android.text.format.DateUtils.getRelativeTimeSpanString(p.lastUsed).toString() else ""
                        Text(p.family.title() + if (ago.isNotEmpty()) " · $ago" else "", fontSize = 12.sp, color = Palette.TextSecondary)
                    }
                    PenChipButton(L("نسيان", "Forget")) { PenInput.forget(p.key) }
                }
            }
        }
    }

    renameFor?.let { p ->
        var name by remember(p.key) { mutableStateOf(p.customName.ifBlank { p.family.title() }) }
        AlertDialog(
            onDismissRequest = { renameFor = null },
            title = { Text(L("اسم القلم", "Pen name"), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(30) }, singleLine = true,
                    placeholder = { Text(p.family.title()) }
                )
            },
            confirmButton = {
                TextButton(onClick = { PenInput.rename(p.key, name); renameFor = null }) { Text(L("حفظ", "Save")) }
            },
            dismissButton = { TextButton(onClick = { renameFor = null }) { Text(L("إلغاء", "Cancel")) } }
        )
    }
}

/** البطاقة الرئيسية: رسم القلم مع اسمه وقدراته، أو دعوة لتجربة القلم إن لم يُكتشف. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PenHeroCard(pen: PenInfo?, active: Boolean) {
    val shape = RoundedCornerShape(24.dp)
    val inf = rememberInfiniteTransition(label = "pen")
    val float by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse), label = "float")
    val shine by inf.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "shine")
    val ink = remember { Animatable(0f) }
    LaunchedEffect(pen?.key) {
        ink.snapTo(0f)
        ink.animateTo(1f, tween(1100, delayMillis = 200))
    }
    Box(
        Modifier.fillMaxWidth().heightIn(min = 228.dp)
            .shadow(14.dp, shape, ambientColor = HeroDeep, spotColor = HeroDeep)
            .clip(shape)
            .background(Brush.radialGradient(listOf(HeroTop, Palette.Navy, HeroDeep), radius = 900f, center = Offset(900f, 0f)))
            .border(1.dp, Palette.Gold.copy(alpha = 0.45f), shape)
    ) {
        Canvas(Modifier.matchParentSize()) {
            val ltr = layoutDirection == LayoutDirection.Ltr
            // الرسم يكون في الجهة المقابلة للنص (يسارًا بالعربية، يمينًا بالإنجليزية)
            scale(if (ltr) -1f else 1f, 1f) {
                val w = size.width
                val hgt = size.height
                drawCircle(
                    Brush.radialGradient(listOf(Palette.Gold.copy(alpha = 0.30f), Color.Transparent), center = Offset(w * 0.2f, hgt * 0.62f), radius = hgt * 0.62f),
                    radius = hgt * 0.62f, center = Offset(w * 0.2f, hgt * 0.62f)
                )
                val len = min(w * 0.5f, 270.dp.toPx())
                val tip = Offset(w * 0.07f, hgt * 0.80f - 4.dp.toPx() * float)
                // خط الحبر الذهبي يخرج من رأس القلم
                if (pen != null) {
                    val p = Path().apply {
                        moveTo(tip.x, tip.y)
                        cubicTo(tip.x + len * 0.15f, tip.y - len * 0.1f, tip.x + len * 0.22f, tip.y + len * 0.08f, tip.x + len * 0.36f, tip.y - len * 0.02f)
                        cubicTo(tip.x + len * 0.48f, tip.y - len * 0.1f, tip.x + len * 0.56f, tip.y - len * 0.04f, tip.x + len * 0.62f, tip.y - len * 0.03f)
                    }
                    val pm = PathMeasure().apply { setPath(p, false) }
                    val part = Path()
                    pm.getSegment(0f, pm.length * ink.value, part, true)
                    drawPath(
                        part,
                        Brush.horizontalGradient(listOf(Color(0xFFF4D98A), Palette.Gold.copy(alpha = 0.1f)), startX = tip.x, endX = tip.x + len * 0.62f),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                val sh = pen?.family?.shape() ?: PenFamily.GENERIC.shape()
                drawPenArt(sh, pen?.finish ?: 1, tip, len, -32f, if (active) shine else -1f)
                if (pen == null) {
                    // بلا قلم: الرسم باهت
                    drawRect(HeroDeep.copy(alpha = 0.45f))
                }
            }
        }
        Column(
            Modifier.align(Alignment.TopStart).fillMaxWidth(0.62f).padding(18.dp),
            horizontalAlignment = Alignment.Start
        ) {
            if (pen != null) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (active) LiveGreen else Color(0xFF94A3B8)))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (active) L("متصل الآن", "Active now") else L("غير نشط", "Not active"),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(pen.displayName, fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 32.sp, color = Color.White, maxLines = 1)
                // العائلة فقط، دون اسم موديل القلم أو الجهاز
                Text(pen.family.maker(), fontSize = 13.sp, color = Color(0xFFC9D4E6), maxLines = 1)
                Spacer(Modifier.height(14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CapChip(L("الضغط", "Pressure"), if (pen.flatPressure || !pen.hasPressure) Cap.NO else Cap.YES)
                    CapChip(L("الميل", "Tilt"), if (pen.hasTilt) Cap.YES else Cap.NO)
                    CapChip(L("التحويم", "Hover"), if (pen.seenHover) Cap.YES else Cap.UNSEEN)
                    CapChip(L("الزر الجانبي", "Side button"), if (pen.seenButton) Cap.YES else Cap.UNSEEN)
                    CapChip(L("طرف ممحاة", "Eraser end"), if (pen.seenEraser) Cap.YES else Cap.UNSEEN)
                }
            } else {
                Text(
                    L("لم نكتشف قلمًا نشطًا", "No active pen detected"),
                    fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color.White
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    L("المس الشاشة بقلمك ليظهر هنا. إن كان قلمك عاديًا (بلا بطارية) فعّل الكتابة بالإصبع.",
                        "Touch the screen with your pen to see it here. If it is a plain pen (no battery), turn on finger writing."),
                    fontSize = 13.sp, lineHeight = 19.sp, color = Color(0xFFC9D4E6)
                )
                if (!PenInput.fingerWrites) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        L("تفعيل الكتابة بالإصبع", "Turn on finger writing"),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E2A44),
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFF4D98A))
                            .clickable { PenInput.chooseFingerWrites(true) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

private enum class Cap { YES, NO, UNSEEN }

@Composable
private fun CapChip(label: String, cap: Cap) {
    val shape = RoundedCornerShape(50)
    Text(
        label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
        color = Color.White.copy(alpha = if (cap == Cap.YES) 1f else 0.5f),
        textDecoration = if (cap == Cap.NO) TextDecoration.LineThrough else null,
        modifier = Modifier.clip(shape)
            .background(Color.White.copy(alpha = if (cap == Cap.YES) 0.14f else 0.06f))
            .border(1.dp, if (cap == Cap.YES) Palette.Gold.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f), shape)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

/** لوحة «جرّب قلمك»: ارسم، وترى الضغط والميل وعدد النقاط في الثانية لحظيًا. */
@Composable
private fun TryPenPad() {
    val strokes = remember { mutableStateListOf<List<Pt>>() }
    val current = remember { mutableStateListOf<Pt>() }
    PenGroup {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(12.dp).height(140.dp)) {
            val shape = RoundedCornerShape(14.dp)
            Canvas(
                Modifier.matchParentSize().clip(shape).background(Color.White)
                    .border(1.dp, Palette.Divider, shape)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val ev = awaitPointerEvent()
                                val down = ev.changes.firstOrNull { it.pressed && !it.previousPressed } ?: continue
                                // الإصبع يرسم هنا فقط في وضع «القلم والإصبع»
                                if (down.type == PointerType.Touch && !PenInput.fingerWrites) continue
                                val w = size.width.toFloat()
                                val sp = SpeedWidth()
                                fun pt(c: androidx.compose.ui.input.pointer.PointerInputChange) = Pt(
                                    c.position.x / w, c.position.y / w,
                                    PenInput.pressureFor(c.type, c.pressure, Tool.FOUNTAIN, sp, c.position.x, c.position.y, c.uptimeMillis, w)
                                )
                                current.clear()
                                current.add(pt(down))
                                down.consume()
                                while (true) {
                                    val e = awaitPointerEvent()
                                    val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!ch.pressed) break
                                    current.add(pt(ch))
                                    ch.consume()
                                }
                                if (current.isNotEmpty()) strokes.add(current.toList())
                                if (strokes.size > 40) strokes.removeAt(0)
                                current.clear()
                            }
                        }
                    }
            ) {
                // أسطر خفيفة كالورق
                var y = 28.dp.toPx()
                while (y < size.height) {
                    drawLine(Color(0xFFEEF1F6), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    y += 28.dp.toPx()
                }
                val ink = Palette.Navy
                val width = 0.006f
                strokes.forEach { drawInk(it, ink, width, Tool.FOUNTAIN) }
                if (current.isNotEmpty()) drawInk(current.toList(), ink, width, Tool.FOUNTAIN)
            }
            if (strokes.isEmpty() && current.isEmpty()) {
                Text(
                    L("اكتب هنا بقلمك", "Write here with your pen"),
                    fontSize = 13.sp, color = Color(0xFFB0B5BF), modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Text(
                    L("مسح", "Clear"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Palette.Navy,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(50))
                        .background(Palette.NavySoft).clickable { strokes.clear() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val press = PenInput.calibratedPercent(PenInput.livePressure)
            val tiltDeg = (PenInput.liveTilt * 180f / PI.toFloat()).toInt().coerceIn(0, 90)
            Meter(Modifier.weight(1f), L("الضغط", "Pressure"), "$press%", press / 100f)
            Meter(Modifier.weight(1f), L("الميل", "Tilt"), "$tiltDeg°", tiltDeg / 90f)
            Meter(Modifier.weight(1f), L("نقطة/ثانية", "Points/sec"), "${PenInput.liveRate}", (PenInput.liveRate / 360f).coerceIn(0f, 1f))
        }
    }
}

@Composable
private fun Meter(modifier: Modifier, label: String, value: String, frac: Float) {
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.clip(shape).background(Palette.Ivory).border(1.dp, Palette.Divider, shape).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(label, fontSize = 11.sp, color = Palette.TextSecondary)
        Text(value, fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Palette.Navy)
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFEFE7D4))) {
            Box(
                Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(5.dp)
                    .background(Brush.horizontalGradient(listOf(Palette.GoldDeep, Palette.Gold)))
            )
        }
    }
}

@Composable
private fun ModeOption(modifier: Modifier, icon: Int, label: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier.clip(shape).let { if (on) it.background(Palette.Navy) else it }
            .clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PhIcon(icon, if (on) Color.White else Palette.TextSecondary, 18.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else Palette.TextSecondary)
    }
}

// ---------------------------------------------------------------- عناصر بأسلوب الإعدادات

@Composable
private fun PenLabel(text: String) {
    Text(
        text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Palette.GoldDeep,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun PenGroup(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Color.White).border(1.dp, Palette.Divider, shape)) { content() }
}

@Composable
private fun PenDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 60.dp).height(1.dp).background(Color(0xFFF1EEE7)))
}

@Composable
private fun PenRow(icon: Int, color: Color, title: String, desc: String?, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(color), contentAlignment = Alignment.Center) {
            PhIcon(icon, Color.White, 18.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
            if (desc != null) Text(desc, fontSize = 12.sp, lineHeight = 17.sp, color = Palette.TextSecondary)
        }
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}

@Composable
private fun PenChipButton(label: String, onClick: () -> Unit) {
    Text(
        label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.Navy,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Palette.NavySoft)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp)
    )
}
