package com.mynotes.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * درج «ملخصات»: كل ما ظُلّل في هذا المستند فقط، كمستند واحد متصل على ورق عاجي.
 * لا تظهر أسماء الصفحات؛ النص الأكبر على الصفحة يظهر عنوانًا، والجداول جداول،
 * والنص المظلل مرتين يظهر بخط عريض وتظليل.
 */
@Composable
fun SummaryDrawer(
    s: DocSession,
    onJump: (TextMark) -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = AppController
    val context = LocalContext.current
    val all = s.allMarks()
    val tidy = c.summaryTidy
    val edits = s.summaryEdits
    val blocks = remember(all, tidy, edits) { Summary.view(all, tidy, edits) }
    var editing by remember { mutableStateOf<TextMark?>(null) }
    // وضع اختيار أجزاء لصنع جدول
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(listOf<String>()) }

    Surface(
        modifier.fillMaxHeight().width(420.dp),
        color = Palette.DrawerBg,
        shadowElevation = 14.dp
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SummaryGlyph(Palette.Navy, Palette.Gold, filled = true)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "ملخصات", fontFamily = Fonts.Display, fontSize = 22.sp, lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold, color = Palette.Navy
                    )
                    Text(
                        s.title, fontSize = 12.sp, color = Palette.TextSecondary, maxLines = 1
                    )
                }
                IconButton(onClick = { s.summaryOpen = false }) {
                    PhIcon(R.drawable.ph_close, Palette.TextSecondary, 20.dp, "إغلاق الملخصات")
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ChoiceChip("مرتّب", tidy) { if (!tidy) c.toggleSummaryTidy() }
                ChoiceChip("كما التُقط", !tidy) { if (tidy) c.toggleSummaryTidy() }
                Spacer(Modifier.weight(1f))
                TextButton(enabled = blocks.isNotEmpty(), onClick = {
                    copy(context, Summary.plainText(s.title, blocks))
                }) { Text("نسخ الكل", fontSize = 13.sp) }
                TextButton(enabled = blocks.isNotEmpty(), onClick = onExport) {
                    Text("PDF", fontSize = 13.sp)
                }
            }
            if (!edits.isEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "فيه تعديلات يدوية على الترتيب التلقائي",
                        fontSize = 12.sp, color = Palette.TextSecondary, modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { s.resetSummaryEdits() }) {
                        Text("استعادة التلقائي", fontSize = 12.sp, color = Palette.GoldDeep)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Gold.copy(alpha = 0.45f)))
            if (selecting) {
                Column(Modifier.fillMaxWidth().background(Palette.NavySoft).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(
                        if (selected.isEmpty()) "اختر النصوص التي تريدها في الجدول"
                        else "اخترت ${selected.size}، ثم حدد شكل الجدول",
                        fontSize = 13.sp, color = Palette.Navy, fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(enabled = selected.size >= 2, onClick = {
                            s.makeTable(blocks.map { it.key }.filter { it in selected }, oneRow = true)
                            selecting = false
                            selected = emptyList()
                        }) { Text("في صف واحد", fontSize = 13.sp) }
                        TextButton(enabled = selected.isNotEmpty(), onClick = {
                            s.makeTable(blocks.map { it.key }.filter { it in selected }, oneRow = false)
                            selecting = false
                            selected = emptyList()
                        }) { Text("كل نص صف", fontSize = 13.sp) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            selecting = false
                            selected = emptyList()
                        }) { Text("إلغاء", fontSize = 13.sp, color = Palette.TextSecondary) }
                    }
                }
            }

            if (blocks.isEmpty()) {
                EmptySummary()
            } else {
                // ورقة واحدة متصلة
                val paper = RoundedCornerShape(8.dp)
                LazyColumn(
                    contentPadding = PaddingValues(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "top") {
                        Box(
                            Modifier.fillMaxWidth().height(18.dp)
                                .shadow(2.dp, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(Palette.Ivory, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        )
                    }
                    blocks.forEachIndexed { i, b ->
                        item(key = "b$i") {
                            val isSel = b.key in selected
                            Box(
                                Modifier.fillMaxWidth().background(Palette.Ivory)
                                    .padding(horizontal = 14.dp)
                                    .then(
                                        if (selecting) Modifier.clip(RoundedCornerShape(8.dp))
                                            .background(if (isSel) Palette.GoldSoft else Color.Transparent)
                                            .border(1.dp, if (isSel) Palette.Gold else Palette.Divider, RoundedCornerShape(8.dp))
                                        else Modifier
                                    )
                                    .padding(horizontal = 6.dp)
                            ) {
                                BlockView(
                                    s = s,
                                    b = b,
                                    shownKeys = blocks.map { it.key },
                                    selecting = selecting,
                                    onToggleSelect = {
                                        selected = if (isSel) selected - b.key else selected + b.key
                                    },
                                    onStartTable = {
                                        selecting = true
                                        selected = listOf(b.key)
                                    },
                                    onJump = onJump,
                                    onEdit = { editing = it }
                                )
                            }
                        }
                    }
                    item(key = "end") {
                        Box(
                            Modifier.fillMaxWidth().height(22.dp)
                                .background(Palette.Ivory, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                        )
                    }
                }
            }
        }
    }

    val e = editing
    if (e != null) {
        EditMarkDialog(
            initial = e.text,
            onSave = {
                s.updateMarkText(e.id, it)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("MyNotes", text))
    AppController.messages.tryEmit("تم النسخ")
}

/** نص الفقرة مع إبراز الأجزاء المهمة بخط عريض وتظليل ذهبي فاتح. */
fun emphasized(text: String, emphasis: List<String>): AnnotatedString = buildAnnotatedString {
    append(text)
    for (r in Summary.emphasisRanges(text, emphasis)) {
        addStyle(
            SpanStyle(fontWeight = FontWeight.ExtraBold, background = Palette.GoldSoft, color = Palette.TextPrimary),
            r.first, r.last + 1
        )
    }
}

@Composable
private fun BlockView(
    s: DocSession,
    b: SBlock,
    shownKeys: List<String>,
    selecting: Boolean,
    onToggleSelect: () -> Unit,
    onStartTable: () -> Unit,
    onJump: (TextMark) -> Unit,
    onEdit: (TextMark) -> Unit
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val first = b.marks.firstOrNull()
    val edits = s.summaryEdits
    val userTable = b.key.startsWith("g:")
    val overridden = userTable || edits.kinds.containsKey(b.key)
    val pos = shownKeys.indexOf(b.key)
    Box {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                .clickable { if (selecting) onToggleSelect() else menu = true }
                .padding(vertical = 5.dp)
        ) {
            when (b) {
                is SHeading -> Text(
                    b.text, fontFamily = Fonts.Display, fontWeight = FontWeight.Bold,
                    fontSize = 20.sp, lineHeight = 30.sp, color = Palette.Navy,
                    modifier = Modifier.padding(top = 10.dp)
                )
                is SPara -> Text(
                    emphasized(b.text, b.emphasis),
                    fontSize = 15.sp, lineHeight = 25.sp, color = Palette.TextPrimary
                )
                is STable -> TableView(b.rows)
            }
        }
        // تبويب صغير يظهر عند الضغط على الجزء
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            when (b) {
                is SHeading -> MenuRow("نص عادي", R.drawable.ph_text) { menu = false; s.setBlockKind(b.key, "p") }
                is SPara -> MenuRow("اجعله عنوانًا", R.drawable.ph_text) { menu = false; s.setBlockKind(b.key, "h") }
                is STable -> if (!userTable) {
                    MenuRow("تحويل إلى نص", R.drawable.ph_text) { menu = false; s.setBlockKind(b.key, "p") }
                }
            }
            if (userTable) {
                MenuRow("فك الجدول", R.drawable.ph_close) { menu = false; s.removeTable(b.key) }
            } else {
                MenuRow("ضعه في جدول…", R.drawable.ph_pages) { menu = false; onStartTable() }
            }
            if (pos > 0) MenuRow("تحريك لأعلى", R.drawable.ph_caret_up) { menu = false; s.moveBlock(shownKeys, b.key, -1) }
            if (pos >= 0 && pos < shownKeys.lastIndex) {
                MenuRow("تحريك لأسفل", R.drawable.ph_caret_down) { menu = false; s.moveBlock(shownKeys, b.key, 1) }
            }
            if (overridden) {
                MenuRow("كما اكتشفه البرنامج", R.drawable.ph_recent) { menu = false; s.resetBlock(b.key) }
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(Palette.Divider))
            if (first != null) {
                MenuRow("انتقال إلى الموضع", R.drawable.ph_search) { menu = false; onJump(first) }
                MenuRow("تعديل النص", R.drawable.ph_note) { menu = false; onEdit(first) }
            }
            MenuRow("نسخ", R.drawable.ph_copy) {
                menu = false
                copy(context, Summary.plainText("", listOf(b)).trim())
            }
            MenuRow("حذف التمييز", R.drawable.ph_trash, Palette.Danger) {
                menu = false
                b.marks.forEach { s.deleteMark(it.id) }
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, icon: Int, color: Color = Palette.TextPrimary, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, fontSize = 14.sp, color = color) },
        leadingIcon = { PhIcon(icon, if (color == Palette.TextPrimary) Palette.TextSecondary else color, 18.dp) },
        onClick = onClick
    )
}

@Composable
private fun TableView(rows: List<List<String>>) {
    val cols = rows.maxOfOrNull { it.size } ?: 1
    val line = Palette.Divider
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .clip(RoundedCornerShape(6.dp)).border(1.dp, line, RoundedCornerShape(6.dp))
    ) {
        rows.forEachIndexed { ri, row ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                    .background(if (ri == 0) Palette.NavySoft else Color.Transparent)
            ) {
                for (ci in 0 until cols) {
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .border(0.5.dp, line)
                            .padding(horizontal = 6.dp, vertical = 5.dp)
                    ) {
                        Text(
                            row.getOrNull(ci) ?: "",
                            fontSize = 12.sp, lineHeight = 17.sp,
                            fontWeight = if (ri == 0) FontWeight.Bold else FontWeight.Normal,
                            color = Palette.TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySummary() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "لا توجد ملخصات بعد",
            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "اختر الهايلايتر، واضغط عليه مرة ثانية وفعّل «التقاط النص»، ثم مرّر على أي سطر. " +
                "ظلّل نفس النص مرتين ليظهر مهمًا بخط عريض.\nيعمل مع النص الإنجليزي فقط حاليًا.",
            fontSize = 13.sp, color = Palette.TextSecondary, lineHeight = 21.sp
        )
    }
}

/** تعديل نص تمييز، مع زر «ترتيب» يصلح الأسطر والترقيم. */
@Composable
private fun EditMarkDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل النص") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp)
                )
                Text(
                    "سطر يبدأ بـ # يصبح عنوانًا. خلايا الجدول يفصلها |. سطر فارغ يبدأ فقرة جديدة.",
                    fontSize = 12.sp, lineHeight = 18.sp, color = Palette.TextSecondary
                )
                Box(
                    Modifier.clip(RoundedCornerShape(18.dp))
                        .border(1.dp, Palette.Gold, RoundedCornerShape(18.dp))
                        .clickable {
                            text = text.split(Regex("\\n\\s*\\n")).joinToString("\n\n") { para ->
                                val t = para.trim()
                                if (t.startsWith("#") || t.split('\n').all { it.contains('|') }) t
                                else TextTidy.sentences(TextTidy.clean(t)).joinToString(" ")
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("ترتيب النص", fontSize = 14.sp, color = Palette.GoldDeep, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onSave(text.trim()) }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
