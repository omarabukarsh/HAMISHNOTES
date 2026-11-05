package com.hamishnotes.app

import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.TextButton
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private fun dayMs(day: LocalDate): Long = AppController.stats[day.toString()] ?: 0L

/** عدد الأيام المتتالية التي قرأت فيها دقيقة أو أكثر (حتى اليوم أو أمس). */
fun studyStreak(): Int {
    var d = LocalDate.now()
    if (dayMs(d) < 60_000L) d = d.minusDays(1)
    var n = 0
    while (dayMs(d) >= 60_000L) {
        n++
        d = d.minusDays(1)
    }
    return n
}

/** حلقة صغيرة لتقدم هدف اليوم. */
@Composable
fun GoalRing(size: Dp = 34.dp, stroke: Dp = 4.dp, track: Color = Palette.Divider, flame: Boolean = false) {
    val goal = AppController.goalMinutes * 60_000L
    val frac = (dayMs(LocalDate.now()).toFloat() / goal).coerceIn(0f, 1f)
    val flamePath = remember {
        androidx.compose.ui.graphics.vector.PathParser()
            .parsePathString("M12,2C12,2 18,7 18,13.5A6,6 0 0 1 6,13.5C6,11 7.2,9.4 8.4,8.2C8.6,9.8 9.4,10.8 10.4,10.8C10.4,7.4 11.6,5 12,2Z")
            .toPath()
    }
    Canvas(Modifier.size(size)) {
        val sw = stroke.toPx()
        val r = Size(this.size.width - sw, this.size.height - sw)
        val o = Offset(sw / 2f, sw / 2f)
        drawArc(track, 0f, 360f, false, o, r, style = Stroke(sw))
        drawArc(Palette.Gold, -90f, 360f * frac, false, o, r, style = Stroke(sw, cap = StrokeCap.Round))
        if (flame) {
            // لهب صغير في وسط الحلقة (السلسلة اليومية)
            val k = this.size.width * 0.46f / 24f
            val off = (this.size.width - 24f * k) / 2f
            withTransform({
                translate(off, off + this.size.width * 0.02f)
                scale(k, k, pivot = Offset.Zero)
            }) {
                drawPath(flamePath, Color(0xFFE8864A))
                drawPath(flamePath, Color(0xFFC45F2A), style = Stroke(1.4f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
    }
}

/** لون النص الذهبي: داكن في الفاتح، فاتح في الداكن. */
private fun goldText(): Color = if (ThemeState.light) Lib.accentDeep else Lib.accent

/** تقرير مادة واحدة في شهر. */
private class SubjectReport(
    val name: String,
    val total: Long,
    val days: Int,
    val docs: List<Pair<String, Long>>
)

@Composable
fun StatsDialog(onDismiss: () -> Unit) {
    val c = AppController
    val today = LocalDate.now()
    val todayMs = dayMs(today)
    val goalMs = c.goalMinutes * 60_000L
    var month by remember { mutableStateOf(today.withDayOfMonth(1)) }
    var editGoal by remember { mutableStateOf(false) }

    val days = (0 until month.lengthOfMonth()).map { month.plusDays(it.toLong()) }
    val dayKeys = days.map { it.toString() }.toSet()
    val values = days.map { dayMs(it) }
    val monthTotal = values.sum()
    val studied = values.count { it >= 60_000L }
    val goalDays = values.count { it >= goalMs }
    val pomos = c.stats.entries.filter { it.key.startsWith("pomo|") && it.key.removePrefix("pomo|") in dayKeys }.sumOf { it.value }

    // وقت كل مستند في كل يوم من الشهر
    val perDocDay = HashMap<String, HashMap<String, Long>>()
    c.stats.forEach { (k, v) ->
        val bar = k.indexOf('|')
        if (bar > 0 && !k.startsWith("pomo|")) {
            val day = k.substring(0, bar)
            if (day in dayKeys) perDocDay.getOrPut(k.substring(bar + 1)) { HashMap() }[day] = v
        }
    }
    // التقرير حسب المادة
    val reports = run {
        val byFolder = HashMap<String, MutableList<Pair<String, Map<String, Long>>>>()
        perDocDay.forEach { (id, m) ->
            val e = c.library.firstOrNull { it.id == id } ?: return@forEach
            byFolder.getOrPut(e.folder.ifEmpty { L("بدون مادة", "No subject") }) { ArrayList() }.add(e.name to m)
        }
        byFolder.map { (f, docs) ->
            val daySet = HashSet<String>()
            docs.forEach { (_, m) -> m.forEach { (d, ms) -> if (ms >= 60_000L) daySet.add(d) } }
            SubjectReport(
                name = f,
                total = docs.sumOf { it.second.values.sum() },
                days = daySet.size,
                docs = docs.map { it.first to it.second.values.sum() }.sortedByDescending { it.second }
            )
        }.sortedByDescending { it.total }
    }

    val locale = if (I18n.english) Locale.ENGLISH else Locale("ar")
    OriginDialog(onDismiss = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) { close ->
        Surface(
            Modifier.fillMaxWidth(0.92f).widthIn(max = 900.dp).heightIn(max = 800.dp),
            shape = RoundedCornerShape(28.dp),
            color = Lib.side,
            border = androidx.compose.foundation.BorderStroke(1.dp, Lib.line)
        ) {
            BoxWithConstraints {
                val wide = maxWidth >= 640.dp
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp)) {
                    // العنوان، والشهر، وزر الإغلاق
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            L("إحصاءات الدراسة", "Study stats"), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold,
                            fontSize = 28.sp, color = Lib.text, maxLines = 1, modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        MonthSwitcher(
                            month.month.getDisplayName(TextStyle.FULL, locale) + " " + month.year,
                            canNext = month.isBefore(today.withDayOfMonth(1)),
                            onPrev = { month = month.minusMonths(1) },
                            onNext = { month = month.plusMonths(1) }
                        )
                        Spacer(Modifier.width(10.dp))
                        IconCircleButton(R.drawable.ph_close, L("إغلاق", "Close"), onClick = close)
                    }
                    Spacer(Modifier.height(18.dp))

                    // بطاقة اليوم: حلقة الهدف، والسلسلة، وآخر سبعة أيام
                    TodayHero(todayMs, goalMs, studyStreak(), showWeek = wide, locale = locale) { editGoal = true }

                    // أرقام الشهر
                    Spacer(Modifier.height(16.dp))
                    val tiles = listOf(
                        Kpi(R.drawable.ph_timer, KpiGold, formatReading(monthTotal), L("مجموع الشهر", "Total this month")),
                        Kpi(R.drawable.ph_books, KpiBlue, "$studied", L("أيام الدراسة", "Study days")),
                        Kpi(R.drawable.ph_check_circle, KpiGreen, L("$goalDays يوم", "$goalDays days"), L("حققت الهدف", "Goal reached")),
                        Kpi(R.drawable.ph_recent, KpiPurple, if (studied > 0) formatReading(monthTotal / studied) else "—", L("متوسط يوم الدراسة", "Avg study day")),
                        Kpi(R.drawable.ph_timer_fill, KpiRed, "$pomos", L("جلسات التركيز", "Focus sessions"))
                    )
                    val perRow = if (wide) 5 else 2
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        tiles.chunked(perRow).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { KpiTile(it, Modifier.weight(1f)) }
                                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }

                    if (!Premium.active) {
                        // الخطة المجانية: اليوم والأرقام فقط، والمخطط والمواد في Premium
                        Spacer(Modifier.height(16.dp))
                        PremiumTeaser(
                            L("افتح الإحصاءات الكاملة", "Unlock full study stats"),
                            L("مخطط الشهر وتقرير وقتك في كل مادة.", "The monthly chart and a report of your time in each subject.")
                        )
                    } else {
                        // أعمدة الأيام
                        Spacer(Modifier.height(16.dp))
                        val chartShape = RoundedCornerShape(22.dp)
                        Column(
                            Modifier.fillMaxWidth().clip(chartShape).background(Lib.card).border(1.dp, Lib.line, chartShape)
                                .padding(horizontal = 20.dp, vertical = 18.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    L("وقت الدراسة اليومي", "Daily study time"), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp, color = Lib.text, modifier = Modifier.weight(1f)
                                )
                                LegendDot(Lib.accent, L("حققت الهدف", "Goal reached"))
                                Spacer(Modifier.width(14.dp))
                                LegendDot(Lib.iconBlue, L("دون الهدف", "Below goal"))
                            }
                            Spacer(Modifier.height(14.dp))
                            // الشهر السابق والتالي ينزلقان من جهتهما
                            val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
                            androidx.compose.animation.AnimatedContent(
                                targetState = month,
                                transitionSpec = { sharedAxisX(forward = targetState.isAfter(initialState), rtl = rtl) },
                                label = "monthBars"
                            ) { m ->
                                val vals = (0 until m.lengthOfMonth()).map { dayMs(m.plusDays(it.toLong())) }
                                MonthBars(vals, goalMs, today.takeIf { it.withDayOfMonth(1) == m }?.dayOfMonth)
                            }
                        }

                        // تقرير كل مادة
                        Spacer(Modifier.height(24.dp))
                        Text(
                            L("المواد هذا الشهر", "Subjects this month"), fontSize = 18.sp, fontFamily = Fonts.Display,
                            fontWeight = FontWeight.Bold, color = Lib.text
                        )
                        Spacer(Modifier.height(12.dp))
                        if (reports.isEmpty()) {
                            Text(L("لا توجد قراءة مسجلة في هذا الشهر.", "No reading recorded this month."), fontSize = 14.sp, color = Lib.muted)
                        }
                        val maxTotal = reports.maxOfOrNull { it.total }?.coerceAtLeast(1L) ?: 1L
                        val cols = if (wide) 2 else 1
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            reports.withIndex().chunked(cols).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    row.forEach { (i, r) ->
                                        SubjectCard(r, SubjectColors[i % SubjectColors.size], maxTotal, monthTotal, Modifier.weight(1f))
                                    }
                                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editGoal) GoalDialog(c.goalMinutes, onSave = { c.setGoal(it); editGoal = false }, onDismiss = { editGoal = false })
}

/** تحديد الهدف اليومي بأي عدد من الساعات والدقائق. */
@Composable
private fun GoalDialog(current: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var hours by remember { mutableStateOf((current / 60).toString()) }
    var minutes by remember { mutableStateOf((current % 60).toString()) }
    val total = (hours.toIntOrNull() ?: 0) * 60 + (minutes.toIntOrNull() ?: 0)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Lib.side,
        titleContentColor = Lib.text,
        textContentColor = Lib.text,
        title = { Text(L("الهدف اليومي", "Daily goal")) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.OutlinedTextField(
                        value = hours, onValueChange = { v -> hours = v.filter { it.isDigit() }.take(2) },
                        label = { Text(L("ساعات", "Hours")) }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                    Spacer(Modifier.width(10.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = minutes, onValueChange = { v -> minutes = v.filter { it.isDigit() }.take(2) },
                        label = { Text(L("دقائق", "Minutes")) }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (total in 5..1440) L("الهدف: ${formatReading(total * 60_000L)} يوميًا", "Goal: ${formatReading(total * 60_000L)} a day") else L("اختر بين 5 دقائق و24 ساعة", "Choose between 5 minutes and 24 hours"),
                    fontSize = 13.sp, color = Lib.muted
                )
            }
        },
        confirmButton = { TextButton(enabled = total in 5..1440, onClick = dialogClose { onSave(total) }) { Text(L("حفظ", "Save"), color = goldText()) } },
        dismissButton = { TextButton(onClick = dialogClose(onDismiss)) { Text(L("إلغاء", "Cancel"), color = Lib.muted) } }
    )
}

private class Kpi(val icon: Int, val color: Color, val value: String, val label: String)

private val KpiGold = Color(0xFFD9A21B)
private val KpiBlue = Color(0xFF2F6FDB)
private val KpiGreen = Color(0xFF2E9D6B)
private val KpiPurple = Color(0xFF8B5CF6)
private val KpiRed = Color(0xFFD9534F)
private val SubjectColors = listOf(Color(0xFFF4C95D), Color(0xFF2F4B7C), Color(0xFF2E9D6B), Color(0xFF8B5CF6), Color(0xFFD9534F), Color(0xFF6B7A90))

@Composable
private fun KpiTile(k: Kpi, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.clip(shape).background(Lib.card).border(1.dp, Lib.line, shape).padding(14.dp)) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(k.color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) { PhIcon(k.icon, k.color, 18.dp) }
        Spacer(Modifier.height(10.dp))
        Text(k.value, fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Lib.text, maxLines = 1)
        Text(k.label, fontSize = 12.sp, color = Lib.muted, maxLines = 1, lineHeight = 16.sp)
    }
}

/** مبدّل الشهر: سهمان والاسم في كبسولة واحدة. */
@Composable
private fun MonthSwitcher(label: String, canNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.height(42.dp).clip(shape).background(Lib.card).border(1.dp, Lib.line, shape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val start = if (I18n.english) 90f else -90f
        Box(
            Modifier.size(42.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable(onClick = onPrev),
            contentAlignment = Alignment.Center
        ) { PhIcon(R.drawable.ph_caret_down, Lib.text, 16.dp, L("الشهر السابق", "Previous month"), Modifier.graphicsLayer { rotationZ = start }) }
        Text(label, fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Lib.text, maxLines = 1)
        Box(
            Modifier.size(42.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable(enabled = canNext, onClick = onNext),
            contentAlignment = Alignment.Center
        ) { PhIcon(R.drawable.ph_caret_down, if (canNext) Lib.text else Lib.line, 16.dp, L("الشهر التالي", "Next month"), Modifier.graphicsLayer { rotationZ = -start }) }
    }
}

/** البطاقة الكحلية: تقدم اليوم والهدف والسلسلة وآخر سبعة أيام. */
@Composable
private fun TodayHero(todayMs: Long, goalMs: Long, streak: Int, showWeek: Boolean, locale: Locale, onChangeGoal: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val gold = Color(0xFFF4C95D)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF1B365D), Color(0xFF2F4B7C), Color(0xFF3A5A8F))))
            .padding(horizontal = 24.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            val frac = (todayMs.toFloat() / goalMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
            Canvas(Modifier.size(124.dp)) {
                val sw = 12.dp.toPx()
                val r = Size(size.width - sw, size.height - sw)
                val o = Offset(sw / 2f, sw / 2f)
                drawArc(Color.White.copy(alpha = 0.14f), 0f, 360f, false, o, r, style = Stroke(sw))
                drawArc(gold, -90f, 360f * frac, false, o, r, style = Stroke(sw, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatReading(todayMs), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White, maxLines = 1)
                Text(L("من ${formatReading(goalMs)}", "of ${formatReading(goalMs)}"), fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
            }
        }
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (todayMs >= goalMs) L("حققت هدف اليوم", "Today’s goal reached") else L("باقي ${formatReading(goalMs - todayMs)} لهدف اليوم", "${formatReading(goalMs - todayMs)} left for today’s goal"),
                fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White, lineHeight = 26.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    L("هدفك اليومي: ${formatReading(goalMs)}", "Daily goal: ${formatReading(goalMs)}"),
                    fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.14f))
                        .clickable(onClick = onChangeGoal).padding(horizontal = 12.dp, vertical = 5.dp)
                ) { Text(L("تغيير", "Change"), fontSize = 12.sp, color = gold, fontWeight = FontWeight.Bold, maxLines = 1) }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(gold.copy(alpha = 0.18f)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Flame(14.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (streak > 0) L(
                        "سلسلة دراسة: $streak ${if (streak == 1) "يوم" else "أيام"} متتالية",
                        "$streak-day study streak"
                    ) else L("ابدأ سلسلة دراسة اليوم", "Start a study streak today"),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFE7A3)
                )
            }
        }
        if (showWeek) {
            Spacer(Modifier.width(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                val today = LocalDate.now()
                (6 downTo 0).forEach { back ->
                    val d = today.minusDays(back.toLong())
                    val ms = dayMs(d)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val circle = androidx.compose.foundation.shape.CircleShape
                        Box(
                            Modifier.size(28.dp).clip(circle)
                                .then(
                                    when {
                                        ms >= goalMs -> Modifier.background(gold)
                                        ms >= 60_000L -> Modifier.border(2.dp, gold, circle)
                                        else -> Modifier.border(2.dp, Color.White.copy(alpha = 0.25f), circle)
                                    }
                                )
                                .then(if (back == 0) Modifier.border(2.dp, Color.White, circle) else Modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            if (ms >= goalMs) PhIcon(R.drawable.ph_check, Color(0xFF1E2A44), 14.dp)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            d.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f), maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/** لهب صغير بنفس رسم حلقة الهدف. */
@Composable
private fun Flame(size: Dp) {
    val flamePath = remember {
        androidx.compose.ui.graphics.vector.PathParser()
            .parsePathString("M12,2C12,2 18,7 18,13.5A6,6 0 0 1 6,13.5C6,11 7.2,9.4 8.4,8.2C8.6,9.8 9.4,10.8 10.4,10.8C10.4,7.4 11.6,5 12,2Z")
            .toPath()
    }
    Canvas(Modifier.size(size)) {
        val k = this.size.width / 24f
        withTransform({ scale(k, k, pivot = Offset.Zero) }) { drawPath(flamePath, Color(0xFFE8864A)) }
    }
}

@Composable
private fun SubjectCard(r: SubjectReport, color: Color, maxTotal: Long, monthTotal: Long, modifier: Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.clip(shape).background(Lib.card).border(1.dp, Lib.line, shape).padding(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(color))
            Spacer(Modifier.width(10.dp))
            Text(
                r.name, fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Lib.text,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            Text(formatReading(r.total), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = goldText(), maxLines = 1)
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Lib.line)) {
            Box(
                Modifier.fillMaxWidth((r.total.toFloat() / maxTotal).coerceIn(0.02f, 1f)).height(8.dp)
                    .clip(RoundedCornerShape(4.dp)).background(color)
            )
        }
        Spacer(Modifier.height(8.dp))
        val share = if (monthTotal > 0) (r.total * 100 / monthTotal) else 0
        Text(
            L("$share% من الشهر · ${r.days} ${if (r.days == 1) "يوم" else "أيام"}", "$share% of the month · ${r.days} ${if (r.days == 1) "day" else "days"}") +
                (if (r.days > 0) L(" · بمتوسط ${formatReading(r.total / r.days)} لليوم", " · avg ${formatReading(r.total / r.days)}/day") else ""),
            fontSize = 12.sp, color = Lib.muted
        )
        r.docs.take(5).forEach { (name, ms) ->
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PhIcon(R.drawable.ph_note, Lib.muted, 14.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    name, fontSize = 13.sp, color = Lib.text, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Text(formatReading(ms), fontSize = 12.sp, color = Lib.muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, color = Lib.muted, maxLines = 1)
    }
}

/** أعمدة أيام الشهر مع خط هدف متقطع؛ الأيام الفارغة خطوط خفيفة، واليوم محاط بإطار. */
@Composable
private fun MonthBars(values: List<Long>, goalMs: Long, todayIndex: Int?) {
    val max = (maxOf(values.maxOrNull() ?: 0L, goalMs) * 1.1f).toLong().coerceAtLeast(1L)
    val chartH = 170.dp
    val goalFrac = goalMs.toFloat() / max
    Column {
        Box(Modifier.fillMaxWidth().height(chartH)) {
            Canvas(Modifier.matchParentSize()) {
                val n = values.size
                val gap = 4.dp.toPx()
                val bw = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
                val rtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
                val radius = CornerRadius(minOf(5.dp.toPx(), bw / 2f))
                values.forEachIndexed { i, v ->
                    val x = if (rtl) size.width - (i + 1) * bw - i * gap else i * (bw + gap)
                    if (v <= 0L) {
                        drawRoundRect(Lib.line, Offset(x, size.height - 4.dp.toPx()), Size(bw, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
                    } else {
                        val h = (size.height * (v.toFloat() / max)).coerceAtLeast(6.dp.toPx())
                        val top = size.height - h
                        if (v >= goalMs) {
                            drawRoundRect(
                                Brush.verticalGradient(listOf(Color(0xFFF4C95D), Color(0xFFE9B73E)), startY = top, endY = size.height),
                                Offset(x, top), Size(bw, h), radius
                            )
                        } else {
                            drawRoundRect(Lib.iconBlue, Offset(x, top), Size(bw, h), radius)
                        }
                    }
                    if (todayIndex == i + 1) {
                        val h = if (v <= 0L) 4.dp.toPx() else (size.height * (v.toFloat() / max)).coerceAtLeast(6.dp.toPx())
                        val pad = 2.5.dp.toPx()
                        drawRoundRect(
                            Lib.text, Offset(x - pad, size.height - h - pad), Size(bw + pad * 2, h + pad),
                            CornerRadius(radius.x + pad), style = Stroke(1.5.dp.toPx())
                        )
                    }
                }
                val gy = size.height * (1f - goalFrac)
                drawLine(
                    goldText(), Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.5.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
                )
            }
            // وسم خط الهدف
            Text(
                L("الهدف ${formatReading(goalMs)}", "Goal ${formatReading(goalMs)}"),
                fontSize = 10.sp, fontWeight = FontWeight.Bold, color = goldText(), maxLines = 1,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = (chartH * (1f - goalFrac) - 20.dp).coerceAtLeast(0.dp))
                    .clip(RoundedCornerShape(6.dp)).background(Lib.accentSoft).padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            values.indices.forEach { i ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val isToday = todayIndex == i + 1
                    if ((i + 1) % 5 == 0 || i == 0 || isToday) {
                        Text(
                            if (isToday) L("اليوم", "Today") else "${i + 1}", fontSize = 11.sp,
                            color = if (isToday) Lib.text else Lib.muted,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            softWrap = false, maxLines = 1, modifier = Modifier.wrapContentWidth(unbounded = true)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BarLine(label: String, ms: Long, max: Long) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(label, fontSize = 13.sp, color = Lib.text, maxLines = 1, modifier = Modifier.weight(1f))
            Text(formatReading(ms), fontSize = 12.sp, color = Lib.muted)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Lib.line)) {
            Box(
                Modifier.fillMaxWidth((ms.toFloat() / max).coerceIn(0.02f, 1f)).height(6.dp)
                    .clip(RoundedCornerShape(3.dp)).background(Lib.accent)
            )
        }
    }
}
