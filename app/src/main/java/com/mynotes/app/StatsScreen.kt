package com.mynotes.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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

@Composable
fun StatsDialog(onDismiss: () -> Unit) {
    val c = AppController
    val today = LocalDate.now()
    val todayMs = dayMs(today)
    val goalMs = c.goalMinutes * 60_000L
    val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val weekKeys = week.map { it.toString() }.toSet()
    val weekTotal = week.sumOf { dayMs(it) }

    // وقت كل مستند وكل مادة خلال الأسبوع
    val perDoc = HashMap<String, Long>()
    c.stats.forEach { (k, v) ->
        val bar = k.indexOf('|')
        if (bar > 0 && !k.startsWith("pomo|") && k.substring(0, bar) in weekKeys) {
            val id = k.substring(bar + 1)
            perDoc[id] = (perDoc[id] ?: 0L) + v
        }
    }
    val perFolder = HashMap<String, Long>()
    perDoc.forEach { (id, ms) ->
        val f = c.library.firstOrNull { it.id == id }?.folder?.ifEmpty { "بدون مادة" } ?: return@forEach
        perFolder[f] = (perFolder[f] ?: 0L) + ms
    }
    val topDocs = perDoc.entries.sortedByDescending { it.value }.take(5)
        .mapNotNull { e -> c.library.firstOrNull { it.id == e.key }?.let { it to e.value } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth(0.9f).heightIn(max = 720.dp),
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

                // اليوم: الحلقة + الهدف + السلسلة
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
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("الهدف اليومي:", fontSize = 13.sp, color = Palette.TextSecondary)
                            Spacer(Modifier.width(6.dp))
                            listOf(30, 60, 90, 120).forEach { m ->
                                Box(Modifier.padding(end = 6.dp)) {
                                    ChoiceChip(formatReading(m * 60_000L), c.goalMinutes == m) { c.setGoal(m) }
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        val pomos = c.stats["pomo|" + today.toString()] ?: 0L
                        if (pomos > 0) {
                            Text("جلسات تركيز اليوم: $pomos", fontSize = 13.sp, color = Palette.Navy)
                            Spacer(Modifier.height(4.dp))
                        }
                        val streak = studyStreak()
                        Text(
                            if (streak > 0) "سلسلة الدراسة: $streak ${if (streak == 1) "يوم" else "أيام"} متتالية"
                            else "ابدأ سلسلة دراسة اليوم",
                            fontSize = 13.sp, color = Palette.GoldDeep, fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))
                Text("آخر 7 أيام: ${formatReading(weekTotal)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
                Spacer(Modifier.height(10.dp))
                WeekBars(week.map { dayMs(it) }, goalMs, week.map { it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ar")) })

                if (perFolder.isNotEmpty()) {
                    Spacer(Modifier.height(22.dp))
                    Text("حسب المادة (هذا الأسبوع)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    val max = perFolder.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
                    perFolder.entries.sortedByDescending { it.value }.forEach { (f, ms) ->
                        BarLine(f, ms, max)
                    }
                }
                if (topDocs.isNotEmpty()) {
                    Spacer(Modifier.height(22.dp))
                    Text("أكثر المستندات قراءة (هذا الأسبوع)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    val max = topDocs.first().second.coerceAtLeast(1L)
                    topDocs.forEach { (e, ms) -> BarLine(e.name, ms, max) }
                }
            }
        }
    }
}

@Composable
private fun WeekBars(values: List<Long>, goalMs: Long, labels: List<String>) {
    val max = maxOf(values.maxOrNull() ?: 0L, goalMs).coerceAtLeast(1L)
    Row(
        Modifier.fillMaxWidth().height(150.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        values.forEachIndexed { i, v ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (v >= 60_000L) formatReading(v) else "", fontSize = 10.sp, color = Palette.TextSecondary, maxLines = 1)
                Canvas(Modifier.fillMaxWidth().height(100.dp)) {
                    val h = size.height * (v.toFloat() / max)
                    drawRoundRect(Palette.Divider, Offset(0f, 0f), size, CornerRadius(6.dp.toPx()))
                    if (h > 0f) {
                        drawRoundRect(
                            if (v >= goalMs) Palette.Gold else Palette.Navy,
                            Offset(0f, size.height - h), Size(size.width, h), CornerRadius(6.dp.toPx())
                        )
                    }
                    // خط الهدف
                    val gy = size.height * (1f - goalMs.toFloat() / max)
                    drawLine(Palette.GoldDeep, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.5f)
                }
                Spacer(Modifier.height(4.dp))
                Text(labels[i], fontSize = 11.sp, color = if (i == values.lastIndex) Palette.Navy else Palette.TextSecondary)
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
