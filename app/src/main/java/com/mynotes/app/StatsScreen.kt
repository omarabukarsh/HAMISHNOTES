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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
fun GoalRing(size: Dp = 34.dp, stroke: Dp = 4.dp, track: Color = Palette.Divider) {
    val goal = AppController.goalMinutes * 60_000L
    val frac = (dayMs(LocalDate.now()).toFloat() / goal).coerceIn(0f, 1f)
    Canvas(Modifier.size(size)) {
        val sw = stroke.toPx()
        val r = Size(this.size.width - sw, this.size.height - sw)
        val o = Offset(sw / 2f, sw / 2f)
        drawArc(track, 0f, 360f, false, o, r, style = Stroke(sw))
        drawArc(Palette.Gold, -90f, 360f * frac, false, o, r, style = Stroke(sw, cap = StrokeCap.Round))
    }
}

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
            byFolder.getOrPut(e.folder.ifEmpty { "بدون مادة" }) { ArrayList() }.add(e.name to m)
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
            Modifier.fillMaxWidth(0.9f).heightIn(max = 760.dp),
            shape = RoundedCornerShape(22.dp),
            color = Palette.Ivory
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "إحصاءات الدراسة", fontFamily = Fonts.Display, fontWeight = FontWeight.Bold,
                        fontSize = 22.sp, color = Palette.Navy, modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) { PhIcon(R.drawable.ph_close, Palette.TextSecondary, 20.dp, "إغلاق") }
                }
                Spacer(Modifier.height(14.dp))

                // اليوم والهدف
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center) {
                        GoalRing(96.dp, 9.dp)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(formatReading(todayMs), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Navy)
                            Text("اليوم", fontSize = 11.sp, color = Palette.TextSecondary)
                        }
                    }
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (todayMs >= goalMs) "حققت هدف اليوم" else "باقي ${formatReading(goalMs - todayMs)} لهدف اليوم",
                            fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("هدفك اليومي: ${formatReading(goalMs)}", fontSize = 14.sp, color = Palette.TextPrimary)
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { editGoal = true }) {
                                Text("تغيير", color = Palette.GoldDeep, fontWeight = FontWeight.Bold)
                            }
                        }
                        val streak = studyStreak()
                        Text(
                            if (streak > 0) "سلسلة الدراسة: $streak ${if (streak == 1) "يوم" else "أيام"} متتالية"
                            else "ابدأ سلسلة دراسة اليوم",
                            fontSize = 13.sp, color = Palette.GoldDeep, fontWeight = FontWeight.Bold
                        )
                    }
                }

                // الشهر
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = month.minusMonths(1) }) { PhIcon(R.drawable.ph_caret_down, Palette.Navy, 20.dp, "الشهر السابق", Modifier.graphicsLayer { rotationZ = 90f }) }
                    Text(
                        month.month.getDisplayName(TextStyle.FULL, Locale("ar")) + " " + month.year,
                        fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Navy,
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center
                    )
                    IconButton(
                        enabled = month.isBefore(today.withDayOfMonth(1)),
                        onClick = { month = month.plusMonths(1) }
                    ) { PhIcon(R.drawable.ph_caret_down, Palette.Navy, 20.dp, "الشهر التالي", Modifier.graphicsLayer { rotationZ = -90f }) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("المجموع", formatReading(monthTotal), Modifier.weight(1f))
                    StatTile("أيام الدراسة", "$studied", Modifier.weight(1f))
                    StatTile("حققت الهدف", "$goalDays يوم", Modifier.weight(1f))
                    StatTile("متوسط يوم الدراسة", if (studied > 0) formatReading(monthTotal / studied) else "—", Modifier.weight(1f))
                }
                if (pomos > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text("جلسات تركيز هذا الشهر: $pomos", fontSize = 13.sp, color = Palette.Navy)
                }
                Spacer(Modifier.height(12.dp))
                MonthBars(values, goalMs, today.takeIf { it.withDayOfMonth(1) == month }?.dayOfMonth)

                // تقرير كل مادة
                Spacer(Modifier.height(22.dp))
                Text("تقرير المواد لهذا الشهر", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
                Spacer(Modifier.height(8.dp))
                if (reports.isEmpty()) {
                    Text("لا توجد قراءة مسجلة في هذا الشهر.", fontSize = 13.sp, color = Palette.TextSecondary)
                }
                val maxTotal = reports.maxOfOrNull { it.total }?.coerceAtLeast(1L) ?: 1L
                reports.forEach { r ->
                    val shape = RoundedCornerShape(14.dp)
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(shape).background(Color.White)
                            .border(1.dp, Palette.Divider, shape).padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Navy, modifier = Modifier.weight(1f))
                            Text(formatReading(r.total), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.GoldDeep)
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Divider)) {
                            Box(
                                Modifier.fillMaxWidth((r.total.toFloat() / maxTotal).coerceIn(0.02f, 1f)).height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)).background(Palette.Gold)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        val share = if (monthTotal > 0) (r.total * 100 / monthTotal) else 0
                        Text(
                            "$share% من وقت الشهر، درستها في ${r.days} ${if (r.days == 1) "يوم" else "أيام"}" +
                                (if (r.days > 0) "، بمتوسط ${formatReading(r.total / r.days)} لليوم" else ""),
                            fontSize = 12.sp, color = Palette.TextSecondary
                        )
                        r.docs.take(5).forEach { (name, ms) ->
                            Row(Modifier.padding(top = 4.dp)) {
                                Text("• $name", fontSize = 13.sp, color = Palette.TextPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                                Text(formatReading(ms), fontSize = 12.sp, color = Palette.TextSecondary)
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
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("الهدف اليومي") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.OutlinedTextField(
                        value = hours, onValueChange = { v -> hours = v.filter { it.isDigit() }.take(2) },
                        label = { Text("ساعات") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                    Spacer(Modifier.width(10.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = minutes, onValueChange = { v -> minutes = v.filter { it.isDigit() }.take(2) },
                        label = { Text("دقائق") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (total in 5..1440) "الهدف: ${formatReading(total * 60_000L)} يوميًا" else "اختر بين 5 دقائق و24 ساعة",
                    fontSize = 13.sp, color = Palette.TextSecondary
                )
            }
        },
        confirmButton = { TextButton(enabled = total in 5..1440, onClick = { onSave(total) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.clip(shape).background(Color.White).border(1.dp, Palette.Divider, shape).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Navy, maxLines = 1)
        Text(label, fontSize = 11.sp, color = Palette.TextSecondary, maxLines = 1)
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
                drawRoundRect(Palette.Divider, Offset(x, 0f), Size(bw, size.height), CornerRadius(3.dp.toPx()))
                val h = size.height * (v.toFloat() / max)
                if (h > 0f) {
                    drawRoundRect(
                        if (v >= goalMs) Palette.Gold else Palette.Navy,
                        Offset(x, size.height - h), Size(bw, h), CornerRadius(3.dp.toPx())
                    )
                }
                if (todayIndex == i + 1) {
                    drawRect(Palette.GoldDeep, Offset(x, size.height - 2.dp.toPx()), Size(bw, 2.dp.toPx()))
                }
            }
            val gy = size.height * (1f - goalMs.toFloat() / max)
            drawLine(Palette.GoldDeep, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.5f)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            values.indices.forEach { i ->
                Text(
                    if ((i + 1) % 5 == 0 || i == 0) "${i + 1}" else "",
                    fontSize = 10.sp, color = Palette.TextSecondary, modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center, maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun BarLine(label: String, ms: Long, max: Long) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(label, fontSize = 13.sp, color = Palette.TextPrimary, maxLines = 1, modifier = Modifier.weight(1f))
            Text(formatReading(ms), fontSize = 12.sp, color = Palette.TextSecondary)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Divider)) {
            Box(
                Modifier.fillMaxWidth((ms.toFloat() / max).coerceIn(0.02f, 1f)).height(6.dp)
                    .clip(RoundedCornerShape(3.dp)).background(Palette.Gold)
            )
        }
    }
}
