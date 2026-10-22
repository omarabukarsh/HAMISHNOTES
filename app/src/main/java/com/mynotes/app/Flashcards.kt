package com.mynotes.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.roundToInt

/** حالة بطاقة في المراجعة المتباعدة: سهولة، فاصل بالأيام، يوم الاستحقاق (epochDay)، عدد مرات النجاح. */
data class SrsState(val ease: Float, val interval: Int, val due: Long, val reps: Int, val intro: Long = -1L)

/** بطاقة: وجه (سؤال) وظهر (إجابة). id ثابت من نصها، فتبقى حالتها إن أعيد توليد البطاقات. */
data class Card(val id: String, val front: String, val back: String, val cloze: Boolean = false)

object Flashcards {
    private const val TERMINAL = ".!?؟…"

    private fun idOf(kind: String, front: String, back: String) =
        kind + ":" + (front.trim() + "|" + back.trim()).hashCode().toUInt().toString(16)

    /**
     * يولّد البطاقات من الملخص (بعد تعديلات المستخدم):
     *  - عنوان + ما تحته من فقرات  → سؤال: العنوان، والإجابة: النص
     *  - كل صف جدول                → سؤال: الخلية الأولى، والإجابة: باقي الخلايا مع أسماء الأعمدة
     *  - النص المهم (المظلل مرتين) → بطاقة «أكمل الفراغ» من جملته
     */
    fun generate(blocks: List<SBlock>): List<Card> {
        val out = ArrayList<Card>()
        var heading: String? = null
        val under = ArrayList<String>()

        fun flushSection() {
            val h = heading
            if (h != null && under.isNotEmpty()) {
                val back = under.joinToString("\n\n").let { if (it.length > 900) it.take(900) + "…" else it }
                out.add(Card(idOf("h", h, back), h, back))
            }
            under.clear()
        }

        for (b in blocks) {
            when (b) {
                is SHeading -> {
                    flushSection()
                    heading = b.text
                }
                is SPara -> {
                    under.add(b.text)
                    for (r in Summary.emphasisRanges(b.text, b.emphasis)) {
                        val phrase = b.text.substring(r.first, r.last + 1)
                        if (phrase.length > 80) continue
                        val s = sentenceAround(b.text, r.first, r.last + 1)
                        if (s.first.length < phrase.length + 12) continue
                        val front = s.first.replaceRange(s.second, s.second + phrase.length, "_____")
                        out.add(Card(idOf("c", front, phrase), front, phrase, cloze = true))
                    }
                }
                is STable -> {
                    val rows = b.rows.filter { r -> r.any { it.isNotBlank() } }
                    if (rows.isEmpty()) continue
                    val head = if (b.header && rows.size > 1) rows[0] else null
                    val data = if (head != null) rows.drop(1) else rows
                    for (r in data) {
                        val key = r.firstOrNull()?.trim().orEmpty()
                        if (key.isEmpty() || r.size < 2) continue
                        val back = r.drop(1).mapIndexedNotNull { i, v ->
                            if (v.isBlank()) null
                            else {
                                val label = head?.getOrNull(i + 1)?.trim().orEmpty()
                                if (label.isNotEmpty()) "$label: $v" else v
                            }
                        }.joinToString("\n")
                        if (back.isEmpty()) continue
                        val front = if (heading != null) "$key\n(${heading})" else key
                        out.add(Card(idOf("t", front, back), front, back))
                    }
                }
            }
        }
        flushSection()
        return out.distinctBy { it.id }
    }

    /** الجملة التي تحتوي النطاق، وموضع بداية النطاق داخلها. */
    private fun sentenceAround(text: String, start: Int, end: Int): Pair<String, Int> {
        var a = start
        while (a > 0 && text[a - 1] !in TERMINAL) a--
        var z = end
        while (z < text.length && text[z] !in TERMINAL) z++
        if (z < text.length) z++
        val raw = text.substring(a, z)
        val lead = raw.length - raw.trimStart().length
        return raw.trim() to (start - a - lead)
    }

    // ---------- الجدولة (SM-2 مبسطة) ----------

    fun today(): Long = LocalDate.now().toEpochDay()

    /** 0 مرة أخرى، 1 صعب، 2 جيد، 3 سهل. */
    fun schedule(s: SrsState?, rating: Int): SrsState {
        val cur = s ?: SrsState(2.5f, 0, today(), 0, intro = today())
        return when (rating) {
            0 -> cur.copy(ease = (cur.ease - 0.2f).coerceAtLeast(1.3f), interval = 0, due = today(), reps = 0)
            1 -> {
                val i = maxOf(1, (cur.interval * 1.2f).roundToInt())
                cur.copy(ease = (cur.ease - 0.15f).coerceAtLeast(1.3f), interval = i, due = today() + i, reps = cur.reps + 1)
            }
            2 -> {
                val i = when (cur.reps) {
                    0 -> 1
                    1 -> 3
                    else -> maxOf(cur.interval + 1, (cur.interval * cur.ease).roundToInt())
                }
                cur.copy(interval = i, due = today() + i, reps = cur.reps + 1)
            }
            else -> {
                val base = when (cur.reps) {
                    0 -> 3
                    1 -> 5
                    else -> maxOf(cur.interval + 2, (cur.interval * cur.ease * 1.3f).roundToInt())
                }
                cur.copy(ease = cur.ease + 0.15f, interval = base, due = today() + base, reps = cur.reps + 1)
            }
        }
    }

    fun intervalLabel(days: Int): String = when {
        days <= 0 -> "الآن"
        days == 1 -> "غدًا"
        days < 30 -> "$days يوم"
        else -> "${days / 30} شهر"
    }

    /** البطاقات المستحقة اليوم، والجديدة بحد 20 بطاقة جديدة في اليوم (لا في كل جلسة). */
    fun dueCards(cards: List<Card>, state: Map<String, SrsState>): List<Card> {
        val t = today()
        val due = cards.filter { c -> state[c.id]?.let { it.due <= t } ?: false }
        val introducedToday = state.values.count { it.intro == t }
        val fresh = cards.filter { it.id !in state }.take((20 - introducedToday).coerceAtLeast(0))
        return due + fresh
    }
}

/** شاشة المراجعة: البطاقة، «أظهر الإجابة»، ثم تقييم يحدد موعد ظهورها التالي. */
@Composable
fun StudyDialog(s: DocSession, cards: List<Card>, onDismiss: () -> Unit) {
    val repo = AppController.repo
    var state by remember { mutableStateOf<Map<String, SrsState>?>(null) }
    var queue by remember { mutableStateOf<List<Card>>(emptyList()) }
    var shown by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val st = withContext(Dispatchers.IO) { repo.loadSrs(s.id) }
        state = st
        queue = Flashcards.dueCards(cards, st)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth(0.82f).heightIn(min = 420.dp, max = 680.dp),
            shape = RoundedCornerShape(24.dp),
            color = Palette.Background
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "مراجعة البطاقات", fontFamily = Fonts.Display, fontWeight = FontWeight.Bold,
                            fontSize = 20.sp, color = Palette.Navy
                        )
                        Text(
                            "${cards.size} بطاقة من ملخص «${s.title}»", fontSize = 12.sp,
                            color = Palette.TextSecondary, maxLines = 1
                        )
                    }
                    IconButton(onClick = onDismiss) { PhIcon(R.drawable.ph_close, Palette.TextSecondary, 20.dp, "إغلاق") }
                }
                val total = done + queue.size
                if (total > 0) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { done.toFloat() / total },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = Palette.Gold, trackColor = Palette.Divider
                    )
                    Text("باقي ${queue.size}", fontSize = 12.sp, color = Palette.TextSecondary, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(14.dp))

                val st = state
                val card = queue.firstOrNull()
                when {
                    st == null -> Text("جارٍ التحميل…", color = Palette.TextSecondary)
                    cards.isEmpty() -> EmptyCards(
                        "لا توجد بطاقات بعد. البطاقات تُصنع من العناوين وما تحتها، ومن صفوف الجداول، " +
                            "ومن النص المظلل مرتين في الملخص."
                    )
                    card == null -> EmptyCards(
                        if (done > 0) "أنهيت مراجعة اليوم: $done بطاقة. ستظهر البطاقات مجددًا في مواعيدها."
                        else "لا توجد بطاقات مستحقة اليوم. عُد غدًا."
                    )
                    else -> {
                        AnimatedContent(targetState = card to shown, label = "card") { (cd, sh) ->
                            CardFace(cd, sh) { shown = true }
                        }
                        Spacer(Modifier.height(14.dp))
                        if (!shown) {
                            Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Navy)
                                    .clickable { shown = true }.padding(14.dp),
                                contentAlignment = Alignment.Center
                            ) { Text("أظهر الإجابة", color = Color.White, fontWeight = FontWeight.Bold) }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    Triple(0, "مرة أخرى", Palette.Danger),
                                    Triple(1, "صعب", Palette.GoldDeep),
                                    Triple(2, "جيد", Palette.Navy),
                                    Triple(3, "سهل", Color(0xFF2E7D32))
                                ).forEach { (rating, label, col) ->
                                    val next = Flashcards.schedule(st[card.id], rating)
                                    Column(
                                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                            .border(1.5.dp, col, RoundedCornerShape(12.dp))
                                            .clickable {
                                                val ns = st + (card.id to next)
                                                state = ns
                                                // الحفظ على نطاق التطبيق حتى لا يضيع آخر تقييم عند إغلاق النافذة
                                                AppController.scope.launch { repo.saveSrs(s.id, ns) }
                                                queue = if (rating == 0) queue.drop(1) + card else queue.drop(1)
                                                if (rating != 0) done++
                                                shown = false
                                            }
                                            .padding(vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(label, color = col, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(Flashcards.intervalLabel(next.interval), color = Palette.TextSecondary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardFace(card: Card, shown: Boolean, onReveal: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 420.dp)
            .clip(shape).background(Palette.Ivory).border(1.dp, Palette.Divider, shape)
            .clickable(enabled = !shown, onClick = onReveal)
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (card.cloze) "أكمل الفراغ" else "السؤال",
            fontSize = 12.sp, color = Palette.GoldDeep, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            card.front, fontSize = 19.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold,
            color = Palette.TextPrimary, textAlign = TextAlign.Center
        )
        if (shown) {
            Spacer(Modifier.height(14.dp))
            Box(Modifier.width(48.dp).height(2.dp).background(Palette.Gold))
            Spacer(Modifier.height(14.dp))
            Text(card.back, fontSize = 16.sp, lineHeight = 26.sp, color = Palette.TextPrimary, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun EmptyCards(text: String) {
    Box(Modifier.fillMaxWidth().heightIn(min = 200.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 15.sp, lineHeight = 24.sp, color = Palette.TextSecondary, textAlign = TextAlign.Center)
    }
}
