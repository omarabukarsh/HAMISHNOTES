package com.mynotes.app

import androidx.compose.foundation.Canvas
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

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth(0.92f).widthIn(max = 860.dp).heightIn(max = 780.dp),
            shape = RoundedCornerShape(28.dp),
            color = Lib.side,
            border = androidx.compose.foundation.BorderStroke(1.dp, Lib.line)
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp)) {
                // العنوان وزر الإغلاق
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { UnderlinedTitle(L("إحصاءات الدراسة", "Study stats"), size = 30) }
                    Spacer(Modifier.width(12.dp))
                    IconCircleButton(R.drawable.ph_close, L("إغلاق", "Close"), onClick = onDismiss)
                }
                Spacer(Modifier.height(20.dp))

                // اليوم والهدف: بطاقة بلون بطاقة «تابع من حيث توقفت»
                val cardShape = RoundedCornerShape(24.dp)
                Row(
                    Modifier.fillMaxWidth().clip(cardShape).background(Lib.resume).padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        GoalRing(104.dp, 10.dp, track = Lib.line)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(formatReading(todayMs), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Lib.text, maxLines = 1)
                            Text(L("اليوم", "today"), fontSize = 12.sp, color = Lib.muted, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (todayMs >= goalMs) L("حققت هدف اليوم", "Today’s goal reached") else L("باقي ${formatReading(goalMs - todayMs)} لهدف اليوم", "${formatReading(goalMs - todayMs)} left for today’s goal"),
                            fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Lib.text
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                L("هدفك اليومي: ${formatReading(goalMs)}", "Daily goal: ${formatReading(goalMs)}"),
                                fontSize = 14.sp, color = Lib.muted, modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(Modifier.width(10.dp))
                            Box(
                                Modifier.clip(RoundedCornerShape(50)).background(Lib.card)
                                    .border(1.dp, Lib.line, RoundedCornerShape(50))
                                    .clickable { editGoal = true }.padding(horizontal = 14.dp, vertical = 6.dp)
                            ) { Text(L("تغيير", "Change"), fontSize = 13.sp, color = goldText(), fontWeight = FontWeight.Bold, maxLines = 1) }
                        }
                        val streak = studyStreak()
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFFE8864A)))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (streak > 0) L(
                                    "سلسلة الدراسة: $streak ${if (streak == 1) "يوم" else "أيام"} متتالية",
                                    "Study streak: $streak ${if (streak == 1) "day" else "days"} in a row"
                                ) else L("ابدأ سلسلة دراسة اليوم", "Start a study streak today"),
                                fontSize = 14.sp, color = Lib.text, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // الشهر
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MonthArrow(R.drawable.ph_caret_down, 90f, L("الشهر السابق", "Previous month"), true) { month = month.minusMonths(1) }
                    Text(
                        month.month.getDisplayName(TextStyle.FULL, if (I18n.english) Locale.ENGLISH else Locale("ar")) + " " + month.year,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Fonts.Display, color = Lib.text,
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1
                    )
                    MonthArrow(R.drawable.ph_caret_down, -90f, L("الشهر التالي", "Next month"), month.isBefore(today.withDayOfMonth(1))) { month = month.plusMonths(1) }
                }
                Spacer(Modifier.height(14.dp))
                val tiles = listOf(
                    L("المجموع", "Total") to formatReading(monthTotal),
                    L("أيام الدراسة", "Study days") to "$studied",
                    L("حققت الهدف", "Goal reached") to L("$goalDays يوم", "$goalDays days"),
                    L("متوسط يوم الدراسة", "Avg study day") to (if (studied > 0) formatReading(monthTotal / studied) else "—")
                )
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth >= 560.dp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            tiles.forEach { (l, v) -> StatTile(l, v, Modifier.weight(1f)) }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            tiles.chunked(2).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    row.forEach { (l, v) -> StatTile(l, v, Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
                if (pomos > 0) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(Lib.accentSoft).padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PhIcon(R.drawable.ph_timer, goldText(), 16.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(L("جلسات تركيز هذا الشهر: $pomos", "Focus sessions this month: $pomos"), fontSize = 13.sp, color = Lib.text)
                    }
                }

                // أعمدة الأيام
                Spacer(Modifier.height(16.dp))
                val chartShape = RoundedCornerShape(20.dp)
                Column(Modifier.fillMaxWidth().clip(chartShape).background(Lib.card).border(1.dp, Lib.line, chartShape).padding(16.dp)) {
                    MonthBars(values, goalMs, today.takeIf { it.withDayOfMonth(1) == month }?.dayOfMonth)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LegendDot(Lib.accent, L("حققت الهدف", "Goal reached"))
                        Spacer(Modifier.width(18.dp))
                        LegendDot(Lib.iconBlue, L("دون الهدف", "Below goal"))
                    }
                }

                // تقرير كل مادة
                Spacer(Modifier.height(26.dp))
                Text(L("تقرير المواد لهذا الشهر", "Subjects report this month"), fontSize = 18.sp, fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, color = Lib.text)
                Spacer(Modifier.height(10.dp))
                if (reports.isEmpty()) {
                    Text(L("لا توجد قراءة مسجلة في هذا الشهر.", "No reading recorded this month."), fontSize = 14.sp, color = Lib.muted)
                }
                val maxTotal = reports.maxOfOrNull { it.total }?.coerceAtLeast(1L) ?: 1L
                reports.forEach { r ->
                    val shape = RoundedCornerShape(20.dp)
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(shape).background(Lib.card)
                            .border(1.dp, Lib.line, shape).padding(18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                r.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Lib.text,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(formatReading(r.total), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = goldText(), maxLines = 1)
                        }
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Lib.line)) {
                            Box(
                                Modifier.fillMaxWidth((r.total.toFloat() / maxTotal).coerceIn(0.02f, 1f)).height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)).background(Lib.accent)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        val share = if (monthTotal > 0) (r.total * 100 / monthTotal) else 0
                        Text(
                            L(
                                "$share% من وقت الشهر، درستها في ${r.days} ${if (r.days == 1) "يوم" else "أيام"}",
                                "$share% of the month, studied on ${r.days} ${if (r.days == 1) "day" else "days"}"
                            ) +
                                (if (r.days > 0) L("، بمتوسط ${formatReading(r.total / r.days)} لليوم", ", avg ${formatReading(r.total / r.days)} per day") else ""),
                            fontSize = 13.sp, color = Lib.muted
                        )
                        if (r.docs.isNotEmpty()) Spacer(Modifier.height(6.dp))
                        r.docs.take(5).forEach { (name, ms) ->
                            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Lib.accent))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    name, fontSize = 14.sp, color = Lib.text, maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(formatReading(ms), fontSize = 13.sp, color = Lib.muted, maxLines = 1)
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
        confirmButton = { TextButton(enabled = total in 5..1440, onClick = { onSave(total) }) { Text(L("حفظ", "Save"), color = goldText()) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("إلغاء", "Cancel"), color = Lib.muted) } }
    )
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape).background(Lib.card).border(1.dp, Lib.line, shape).padding(vertical = 16.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Lib.text, maxLines = 1, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = Lib.muted, maxLines = 2, textAlign = TextAlign.Center, lineHeight = 16.sp)
    }
}

/** سهم الشهر السابق/التالي: زر دائري صغير. */
@Composable
private fun MonthArrow(icon: Int, rotate: Float, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .background(Lib.card).border(1.dp, Lib.line, androidx.compose.foundation.shape.CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        PhIcon(icon, if (enabled) Lib.text else Lib.line, 18.dp, description, Modifier.graphicsLayer { rotationZ = rotate })
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

/** أعمدة أيام الشهر مع خط الهدف؛ أرقام الأيام كل 5 أيام. */
@Composable
private fun MonthBars(values: List<Long>, goalMs: Long, todayIndex: Int?) {
    val max = maxOf(values.maxOrNull() ?: 0L, goalMs).coerceAtLeast(1L)
    Column {
        Canvas(Modifier.fillMaxWidth().height(130.dp)) {
            val n = values.size
            val gap = 3.dp.toPx()
            val bw = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
            values.forEachIndexed { i, v ->
                val x = i * (bw + gap)
                drawRoundRect(Lib.line, Offset(x, 0f), Size(bw, size.height), CornerRadius(3.dp.toPx()))
                val h = size.height * (v.toFloat() / max)
                if (h > 0f) {
                    drawRoundRect(
                        if (v >= goalMs) Lib.accent else Lib.iconBlue,
                        Offset(x, size.height - h), Size(bw, h), CornerRadius(3.dp.toPx())
                    )
                }
                if (todayIndex == i + 1) {
                    drawRect(goldText(), Offset(x, size.height - 2.dp.toPx()), Size(bw, 2.dp.toPx()))
                }
            }
            val gy = size.height * (1f - goalMs.toFloat() / max)
            drawLine(goldText(), Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.5f)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            values.indices.forEach { i ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if ((i + 1) % 5 == 0 || i == 0) {
                        Text(
                            "${i + 1}", fontSize = 11.sp, color = Lib.muted, softWrap = false, maxLines = 1,
                            modifier = Modifier.wrapContentWidth(unbounded = true)
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
