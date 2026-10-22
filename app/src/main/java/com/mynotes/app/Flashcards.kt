package com.mynotes.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.foundation.layout.size
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
                        // بطاقة مباشرة: النص المهم في الوجه، وجملته كاملة في الظهر
                        val sentence = sentenceAround(b.text, r.first, r.last + 1).first
                        if (sentence.length < phrase.length + 12) continue
                        out.add(Card(idOf("c", phrase, sentence), phrase, sentence))
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

/**
 * بطاقات مباشرة (Flashcards): الوجه عنوان أو مصطلح، والظهر شرحه. انقر البطاقة لقلبها،
 * واسحب يمينًا أو يسارًا للتنقل. بدون أسئلة ولا تقييم.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StudyDialog(s: DocSession, cards: List<Card>, onDismiss: () -> Unit) {
    var shuffled by remember { mutableStateOf<List<String>?>(null) }
    var hardOnly by remember { mutableStateOf(false) }
    // البطاقات المعروضة: كل البطاقات أو المميزة بنجمة فقط، بترتيب الخلط إن وُجد
    val base = if (hardOnly) cards.filter { it.id in s.starred } else cards
    val deck = shuffled?.let { order -> order.mapNotNull { id -> base.firstOrNull { it.id == id } } + base.filter { it.id !in order } } ?: base
    val pager = rememberPagerState(pageCount = { deck.size })
    val scope = rememberCoroutineScope()
    // البطاقات المقلوبة حاليًا (حسب المعرّف)
    var flipped by remember { mutableStateOf(setOf<String>()) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth(0.82f).heightIn(min = 460.dp, max = 700.dp),
            shape = RoundedCornerShape(24.dp),
            color = Palette.Background
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "بطاقات المراجعة", fontFamily = Fonts.Display, fontWeight = FontWeight.Bold,
                            fontSize = 20.sp, color = Palette.Navy
                        )
                        Text(s.title, fontSize = 12.sp, color = Palette.TextSecondary, maxLines = 1)
                    }
                    ChoiceChip("الصعبة فقط (${cards.count { it.id in s.starred }})", hardOnly) {
                        hardOnly = !hardOnly
                        flipped = emptySet()
                        scope.launch { pager.scrollToPage(0) }
                    }
                    if (deck.size > 1) {
                        TextButton(onClick = {
                            shuffled = deck.map { it.id }.shuffled()
                            flipped = emptySet()
                            scope.launch { pager.scrollToPage(0) }
                        }) { Text("خلط", color = Palette.GoldDeep, fontWeight = FontWeight.Bold) }
                    }
                    IconButton(onClick = onDismiss) { PhIcon(R.drawable.ph_close, Palette.TextSecondary, 20.dp, "إغلاق") }
                }
                Spacer(Modifier.height(12.dp))

                if (deck.isEmpty()) {
                    EmptyCards(
                        if (hardOnly) "لا توجد بطاقات مميزة بنجمة. اضغط النجمة على أي بطاقة صعبة لتظهر هنا."
                        else "لا توجد بطاقات بعد. البطاقات تُصنع من العناوين وما تحتها، ومن صفوف الجداول، " +
                            "ومن النص المظلل مرتين، أو حدد نصًا واختر «اجعله بطاقة»."
                    )
                } else {
                    HorizontalPager(
                        state = pager,
                        pageSpacing = 16.dp,
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                    ) { i ->
                        val card = deck[i]
                        val isFlipped = card.id in flipped
                        Box {
                            FlipCard(card, isFlipped) {
                                flipped = if (isFlipped) flipped - card.id else flipped + card.id
                            }
                            Row(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                                if (card.id.startsWith("u:")) {
                                    IconButton(onClick = { s.deleteCustomCard(card.id) }) {
                                        PhIcon(R.drawable.ph_trash, Palette.TextSecondary, 20.dp, "حذف البطاقة")
                                    }
                                }
                                IconButton(onClick = { s.toggleStar(card.id) }) {
                                    StarGlyph(card.id in s.starred, Palette.Gold, Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            enabled = pager.currentPage > 0,
                            onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }
                        ) { Text("السابقة") }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${pager.currentPage + 1} / ${deck.size}",
                            fontSize = 14.sp, color = Palette.TextSecondary, fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            enabled = pager.currentPage < deck.lastIndex,
                            onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }
                        ) { Text("التالية") }
                    }
                    Text(
                        "انقر البطاقة لقلبها، واسحب للتنقل",
                        fontSize = 12.sp, color = Palette.TextSecondary,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** بطاقة تنقلب ثلاثيًا عند النقر: الوجه ثم الظهر. */
@Composable
private fun FlipCard(card: Card, flipped: Boolean, onFlip: () -> Unit) {
    val angle by animateFloatAsState(if (flipped) 180f else 0f, tween(380), label = "flip")
    val showBack = angle > 90f
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 460.dp)
            .graphicsLayer {
                rotationY = angle
                cameraDistance = 14f * density
            }
            .clip(shape)
            .background(if (showBack) Palette.Ivory else Color.White)
            .border(1.5.dp, if (showBack) Palette.Gold else Palette.Divider, shape)
            .clickable(onClick = onFlip),
        contentAlignment = Alignment.Center
    ) {
        // الظهر يُعكس مرة ثانية حتى لا يظهر النص مقلوبًا
        Column(
            Modifier.graphicsLayer { rotationY = if (showBack) 180f else 0f }
                .verticalScroll(rememberScrollState())
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!showBack) {
                Text(
                    card.front, fontSize = 22.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold,
                    fontFamily = Fonts.Display, color = Palette.Navy, textAlign = TextAlign.Center
                )
            } else {
                Text(
                    card.front.lineSequence().first(), fontSize = 13.sp, color = Palette.GoldDeep,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Box(Modifier.width(48.dp).height(2.dp).background(Palette.Gold))
                Spacer(Modifier.height(12.dp))
                Text(card.back, fontSize = 17.sp, lineHeight = 28.sp, color = Palette.TextPrimary, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun EmptyCards(text: String) {
    Box(Modifier.fillMaxWidth().heightIn(min = 200.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 15.sp, lineHeight = 24.sp, color = Palette.TextSecondary, textAlign = TextAlign.Center)
    }
}

/** إضافة بطاقة يدويًا: الظهر هو النص المحدد، والوجه يكتبه المستخدم. */
@Composable
fun AddCardDialog(initialFront: String, initialBack: String, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var front by remember { mutableStateOf(initialFront) }
    var back by remember { mutableStateOf(initialBack) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بطاقة جديدة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = front, onValueChange = { front = it },
                    label = { Text("الوجه: عنوان أو مصطلح") }, modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.material3.OutlinedTextField(
                    value = back, onValueChange = { back = it },
                    label = { Text("الظهر: الشرح") }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
                )
            }
        },
        confirmButton = {
            TextButton(enabled = front.isNotBlank() && back.isNotBlank(), onClick = { onSave(front, back) }) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
