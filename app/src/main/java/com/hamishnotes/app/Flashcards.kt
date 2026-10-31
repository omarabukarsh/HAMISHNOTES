package com.hamishnotes.app

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
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.roundToInt

/** حالة بطاقة في المراجعة المتباعدة: سهولة، فاصل بالأيام، يوم الاستحقاق (epochDay)، عدد مرات النجاح. */
data class SrsState(val ease: Float, val interval: Int, val due: Long, val reps: Int, val intro: Long = -1L)

/** بطاقة يصنعها المستخدم: الوجه عنوان، والظهر المحتوى. */
data class Card(val id: String, val front: String, val back: String, val cloze: Boolean = false)

object Flashcards {
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
        days <= 0 -> L("الآن", "now")
        days == 1 -> L("غدًا", "tomorrow")
        days < 30 -> L("$days يوم", "$days days")
        else -> L("${days / 30} شهر", "${days / 30} months")
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
    // إضافة بطاقة جديدة أو تعديل بطاقة موجودة
    var adding by remember { mutableStateOf(false) }
    var editingCard by remember { mutableStateOf<Card?>(null) }

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
                            L("بطاقات المراجعة", "Review cards"), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold,
                            fontSize = 20.sp, color = Palette.Navy
                        )
                        Text(s.title, fontSize = 12.sp, color = Palette.TextSecondary, maxLines = 1)
                    }
                    TextButton(onClick = { adding = true }) {
                        PhIcon(R.drawable.ph_plus, Palette.Navy, 16.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(L("بطاقة جديدة", "New card"), color = Palette.Navy, fontWeight = FontWeight.Bold)
                    }
                    ChoiceChip(L("الصعبة فقط (${cards.count { it.id in s.starred }})", "Hard only (${cards.count { it.id in s.starred }})"), hardOnly) {
                        hardOnly = !hardOnly
                        flipped = emptySet()
                        scope.launch { pager.scrollToPage(0) }
                    }
                    if (deck.size > 1) {
                        TextButton(onClick = {
                            shuffled = deck.map { it.id }.shuffled()
                            flipped = emptySet()
                            scope.launch { pager.scrollToPage(0) }
                        }) { Text(L("خلط", "Shuffle"), color = Palette.GoldDeep, fontWeight = FontWeight.Bold) }
                    }
                    IconButton(onClick = onDismiss) { PhIcon(R.drawable.ph_close, Palette.TextSecondary, 20.dp, L("إغلاق", "Close")) }
                }
                Spacer(Modifier.height(12.dp))

                if (deck.isEmpty()) {
                    EmptyCards(
                        if (hardOnly) L("لا توجد بطاقات مميزة بنجمة. اضغط النجمة على أي بطاقة صعبة لتظهر هنا.", "No starred cards. Tap the star on any hard card to see it here.")
                        else L("لا توجد بطاقات بعد. اضغط «بطاقة جديدة» واكتب العنوان والمحتوى، ", "No cards yet. Tap “New card” and write a title and content, ") +
                            L("أو حدد نصًا في المستند أو الملخص واختر «اجعله بطاقة».", "or select text in the document or summary and choose “Make it a card”.")
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
                                IconButton(onClick = { editingCard = card }) {
                                    PhIcon(R.drawable.ph_pencil, Palette.TextSecondary, 20.dp, L("تعديل البطاقة", "Edit card"))
                                }
                                IconButton(onClick = { s.deleteCustomCard(card.id) }) {
                                    PhIcon(R.drawable.ph_trash, Palette.TextSecondary, 20.dp, L("حذف البطاقة", "Delete card"))
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
                        ) { Text(L("السابقة", "Previous")) }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${pager.currentPage + 1} / ${deck.size}",
                            fontSize = 14.sp, color = Palette.TextSecondary, fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            enabled = pager.currentPage < deck.lastIndex,
                            onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }
                        ) { Text(L("التالية", "Next")) }
                    }
                    Text(
                        L("انقر البطاقة لقلبها، واسحب للتنقل", "Tap a card to flip it, swipe to move"),
                        fontSize = 12.sp, color = Palette.TextSecondary,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
    if (adding) {
        AddCardDialog("", "", onSave = { f, b ->
            s.addCustomCard(f, b)
            adding = false
        }, onDismiss = { adding = false })
    }
    editingCard?.let { ec ->
        AddCardDialog(ec.front, ec.back, title = L("تعديل البطاقة", "Edit card"), confirm = L("حفظ", "Save"), onSave = { f, b ->
            s.updateCustomCard(ec.id, f, b)
            editingCard = null
        }, onDismiss = { editingCard = null })
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

/** إضافة بطاقة أو تعديلها: العنوان (الوجه) والمحتوى (الظهر) يكتبهما المستخدم. */
@Composable
fun AddCardDialog(
    initialFront: String,
    initialBack: String,
    title: String = L("بطاقة جديدة", "New card"),
    confirm: String = L("إضافة", "Add"),
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var front by remember { mutableStateOf(initialFront) }
    var back by remember { mutableStateOf(initialBack) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = front, onValueChange = { front = it },
                    label = { Text(L("عنوان البطاقة", "Card title")) }, modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.material3.OutlinedTextField(
                    value = back, onValueChange = { back = it },
                    label = { Text(L("محتوى البطاقة", "Card content")) }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
                )
            }
        },
        confirmButton = {
            TextButton(enabled = front.isNotBlank() && back.isNotBlank(), onClick = { onSave(front, back) }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("إلغاء", "Cancel")) } }
    )
}
