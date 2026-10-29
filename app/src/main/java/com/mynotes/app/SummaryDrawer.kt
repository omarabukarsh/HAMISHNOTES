package com.mynotes.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
    var colorFilter by remember { mutableStateOf<Int?>(null) }
    val byColor = c.summaryByColor
    val blocks = remember(all, tidy, edits, colorFilter, byColor) {
        Summary.display(all, tidy, edits, colorFilter, byColor) { c.labelOf(it) }
    }
    // الألوان المستخدمة في هذا المستند (للتصفية)
    val usedColors = remember(all) {
        val m = LinkedHashMap<Int, Color>()
        all.forEach { m.putIfAbsent(Summary.argbOf(it.color), it.color) }
        m.values.toList()
    }
    var editing by remember { mutableStateOf<TextMark?>(null) }
    // وضع اختيار أجزاء لصنع جدول
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(listOf<String>()) }
    // null = صنع جدول جديد، وإلا مفتاح جدول تُضم إليه النصوص المختارة
    var tableTarget by remember { mutableStateOf<String?>(null) }
    var tableCols by remember { mutableStateOf(2) }
    var editingTable by remember { mutableStateOf<STable?>(null) }
    var studying by remember { mutableStateOf(false) }
    // البطاقات يصنعها المستخدم بنفسه فقط (عنوان ومحتوى)
    val cards = s.customCards
    var cardFrom by remember { mutableStateOf<SBlock?>(null) }

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
                        L("ملخصات", "Summaries"), fontFamily = Fonts.Display, fontSize = 22.sp, lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold, color = Palette.Navy
                    )
                    Text(
                        s.title, fontSize = 12.sp, color = Palette.TextSecondary, maxLines = 1
                    )
                }
                run {
                    Row(
                        Modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, Palette.Gold, RoundedCornerShape(16.dp))
                            .clickable { studying = true }.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PhIcon(R.drawable.ph_books, Palette.GoldDeep, 16.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(L("بطاقات ${cards.size}", "Cards ${cards.size}"), fontSize = 13.sp, color = Palette.GoldDeep, fontWeight = FontWeight.Bold)
                    }
                }
                IconButton(onClick = { s.summaryOpen = false }) {
                    PhIcon(R.drawable.ph_close, Palette.TextSecondary, 20.dp, L("إغلاق الملخصات", "Close summaries"))
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ChoiceChip(L("مرتّب", "Tidy"), tidy) { if (!tidy) c.toggleSummaryTidy() }
                ChoiceChip(L("كما التُقط", "As captured"), !tidy) { if (tidy) c.toggleSummaryTidy() }
                Spacer(Modifier.weight(1f))
                TextButton(enabled = blocks.isNotEmpty(), onClick = {
                    copy(context, Summary.plainText(s.title, blocks))
                }) { Text(L("نسخ الكل", "Copy all"), fontSize = 13.sp) }
                TextButton(enabled = blocks.isNotEmpty(), onClick = onExport) {
                    Text("PDF", fontSize = 13.sp)
                }
            }
            if (usedColors.size > 1 || colorFilter != null) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp).padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ChoiceChip(L("كل الألوان", "All colors"), colorFilter == null) { colorFilter = null }
                    usedColors.forEach { col ->
                        val argb = Summary.argbOf(col)
                        val sel = colorFilter == argb
                        val shape = RoundedCornerShape(18.dp)
                        Row(
                            Modifier.clip(shape)
                                .background(if (sel) Palette.Navy else Color.White)
                                .border(1.dp, if (sel) Palette.Navy else Palette.Divider, shape)
                                .clickable { colorFilter = if (sel) null else argb }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(col))
                            Spacer(Modifier.width(6.dp))
                            Text(c.labelOf(col), fontSize = 13.sp, color = if (sel) Color.White else Palette.TextPrimary)
                        }
                    }
                    ChoiceChip(L("تجميع حسب اللون", "Group by color"), byColor) { c.toggleSummaryByColor() }
                }
            }
            if (!edits.isEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        L("فيه تعديلات يدوية على الترتيب التلقائي", "Has manual edits to the automatic layout"),
                        fontSize = 12.sp, color = Palette.TextSecondary, modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { s.resetSummaryEdits() }) {
                        Text(L("استعادة التلقائي", "Restore automatic"), fontSize = 12.sp, color = Palette.GoldDeep)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Gold.copy(alpha = 0.45f)))
            if (selecting) {
                Column(Modifier.fillMaxWidth().background(Palette.NavySoft).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    val target = tableTarget
                    Text(
                        when {
                            target != null && selected.isEmpty() -> L("اختر النصوص التي تريد ضمها إلى الجدول", "Choose the texts to add to the table")
                            target != null -> L("اخترت ${selected.size}، وستُضاف كصفوف في آخر الجدول", "${selected.size} selected; they will be added as rows at the end of the table")
                            selected.isEmpty() -> L("اختر النصوص بالترتيب الذي تريده في الجدول", "Choose texts in the order you want in the table")
                            else -> L("اخترت ${selected.size}. النصوص تملأ الصفوف بالترتيب حسب عدد الأعمدة", "${selected.size} selected. Texts fill the rows in order by column count")
                        },
                        fontSize = 13.sp, color = Palette.Navy, fontWeight = FontWeight.Bold
                    )
                    if (target == null) {
                        Row(
                            Modifier.padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(L("الأعمدة:", "Columns:"), fontSize = 13.sp, color = Palette.TextSecondary)
                            listOf(1, 2, 3, 4).forEach { n ->
                                ChoiceChip("$n", tableCols == n) { tableCols = n }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (target == null) {
                            TextButton(enabled = selected.isNotEmpty(), onClick = {
                                // ترتيب الاختيار نفسه هو ترتيب الخلايا
                                s.makeTable(selected, cols = tableCols)
                                selecting = false
                                selected = emptyList()
                            }) { Text(L("إنشاء الجدول", "Create table"), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        } else {
                            TextButton(enabled = selected.isNotEmpty(), onClick = {
                                val newRows = selected.mapNotNull { k -> blocks.firstOrNull { it.key == k } }.flatMap { b ->
                                    when (b) {
                                        is STable -> b.rows
                                        is SHeading -> listOf(listOf(b.text))
                                        is SPara -> listOf(b.text.split('|').map { it.trim() })
                                    }
                                }
                                s.addToTable(target, selected, newRows)
                                selecting = false
                                selected = emptyList()
                                tableTarget = null
                            }) { Text(L("إضافة إلى الجدول", "Add to table"), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            selecting = false
                            selected = emptyList()
                            tableTarget = null
                        }) { Text(L("إلغاء", "Cancel"), fontSize = 13.sp, color = Palette.TextSecondary) }
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
                                        if (b.key != tableTarget) {
                                            selected = if (isSel) selected - b.key else selected + b.key
                                        }
                                    },
                                    selectable = !(selecting && b.key == tableTarget),
                                    selectionIndex = selected.indexOf(b.key),
                                    onStartTable = {
                                        selecting = true
                                        tableTarget = null
                                        selected = listOf(b.key)
                                    },
                                    onAddToTable = {
                                        selecting = true
                                        tableTarget = b.key
                                        selected = emptyList()
                                    },
                                    onEditTable = { editingTable = it },
                                    onMakeCard = { cardFrom = it },
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

    if (studying) StudyDialog(s, cards, onDismiss = { studying = false })

    val cf = cardFrom
    if (cf != null) {
        val body = Summary.plainText("", listOf(cf)).trim()
        AddCardDialog(
            initialFront = if (cf is SHeading) cf.text else "",
            initialBack = if (cf is SHeading) "" else body,
            onSave = { f, bk -> s.addCustomCard(f, bk); cardFrom = null },
            onDismiss = { cardFrom = null }
        )
    }

    val et = editingTable
    if (et != null) {
        TableEditorDialog(
            initial = et.rows,
            initialHeader = et.header,
            onSave = { rows, header ->
                s.saveTable(et.key, rows, header)
                editingTable = null
            },
            onDismiss = { editingTable = null }
        )
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
    AppController.messages.tryEmit(L("تم النسخ", "Copied"))
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
    selectable: Boolean,
    selectionIndex: Int,
    onToggleSelect: () -> Unit,
    onStartTable: () -> Unit,
    onAddToTable: () -> Unit,
    onEditTable: (STable) -> Unit,
    onMakeCard: (SBlock) -> Unit,
    onJump: (TextMark) -> Unit,
    onEdit: (TextMark) -> Unit
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val first = b.marks.firstOrNull()
    val edits = s.summaryEdits
    val userTable = b.key.startsWith("g:")
    val overridden = userTable || edits.kinds.containsKey(b.key) || b.key in edits.order
    val pos = shownKeys.indexOf(b.key)
    Box {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                .clickable { if (selecting) onToggleSelect() else menu = true }
                .padding(vertical = 5.dp)
        ) {
            if (selecting && selectionIndex >= 0) {
                Text(
                    "${selectionIndex + 1}", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Palette.GoldDeep)
                        .padding(horizontal = 7.dp, vertical = 1.dp)
                )
            }
            if (selecting && !selectable) {
                Text(L("الجدول الذي تضم إليه", "Table you are adding to"), fontSize = 11.sp, color = Palette.GoldDeep)
            }
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
                is STable -> TableView(b.rows, b.header)
            }
        }
        // تبويب صغير يظهر عند الضغط على الجزء
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            when (b) {
                is SHeading -> MenuRow(L("نص عادي", "Plain text"), R.drawable.ph_text) { menu = false; s.setBlockKind(b.key, "p") }
                is SPara -> MenuRow(L("اجعله عنوانًا", "Make heading"), R.drawable.ph_text) { menu = false; s.setBlockKind(b.key, "h") }
                is STable -> if (!userTable) {
                    MenuRow(L("تحويل إلى نص", "Convert to text"), R.drawable.ph_text) { menu = false; s.setBlockKind(b.key, "p") }
                }
            }
            if (b is STable) {
                MenuRow(L("تحرير الجدول", "Edit table"), R.drawable.ph_sliders) { menu = false; onEditTable(b) }
                MenuRow(L("ضم نصوص إلى هذا الجدول", "Add texts to this table"), R.drawable.ph_plus) { menu = false; onAddToTable() }
            }
            if (userTable) {
                MenuRow(L("فك الجدول", "Unmake table"), R.drawable.ph_close) { menu = false; s.removeTable(b.key) }
            } else if (b !is STable) {
                MenuRow(L("ضعه في جدول…", "Put in a table…"), R.drawable.ph_pages) { menu = false; onStartTable() }
            }
            if (pos > 0) MenuRow(L("تحريك لأعلى", "Move up"), R.drawable.ph_caret_up) { menu = false; s.moveBlock(shownKeys, b.key, -1) }
            if (pos >= 0 && pos < shownKeys.lastIndex) {
                MenuRow(L("تحريك لأسفل", "Move down"), R.drawable.ph_caret_down) { menu = false; s.moveBlock(shownKeys, b.key, 1) }
            }
            if (overridden) {
                MenuRow(L("كما اكتشفه البرنامج", "As detected"), R.drawable.ph_recent) { menu = false; s.resetBlock(b.key) }
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(Palette.Divider))
            if (first != null) {
                MenuRow(L("انتقال إلى الموضع", "Go to location"), R.drawable.ph_search) { menu = false; onJump(first) }
                MenuRow(L("تعديل النص", "Edit text"), R.drawable.ph_note) { menu = false; onEdit(first) }
            }
            MenuRow(L("اجعله بطاقة", "Make it a card"), R.drawable.ph_cards) { menu = false; onMakeCard(b) }
            MenuRow(L("نسخ", "Copy"), R.drawable.ph_copy) {
                menu = false
                copy(context, Summary.plainText("", listOf(b)).trim())
            }
            MenuRow(L("حذف التمييز", "Delete highlight"), R.drawable.ph_trash, Palette.Danger) {
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
private fun TableView(rows: List<List<String>>, header: Boolean = true) {
    val cols = rows.maxOfOrNull { it.size } ?: 1
    val line = Palette.Divider
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .clip(RoundedCornerShape(6.dp)).border(1.dp, line, RoundedCornerShape(6.dp))
    ) {
        rows.forEachIndexed { ri, row ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                    .background(if (header && ri == 0) Palette.NavySoft else Color.Transparent)
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
                            fontWeight = if (header && ri == 0) FontWeight.Bold else FontWeight.Normal,
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
            L("لا توجد ملخصات بعد", "No summaries yet"),
            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            L("اختر الهايلايتر، واضغط عليه مرة ثانية وفعّل «التقاط النص»، ثم مرّر على أي سطر. ", "Pick the highlighter, tap it again and turn on “Text snap”, then run it over any line. ") +
                L("ظلّل نفس النص مرتين ليظهر مهمًا بخط عريض.\nيعمل مع العربية والإنجليزية في ملفات PDF الرقمية.", "Highlight the same text twice to make it important in bold.\nWorks with Arabic and English in digital PDFs."),
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
        title = { Text(L("تعديل النص", "Edit text")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp)
                )
                Text(
                    L("سطر يبدأ بـ # يصبح عنوانًا. خلايا الجدول يفصلها |. سطر فارغ يبدأ فقرة جديدة.", "A line starting with # becomes a heading. Table cells are separated by |. A blank line starts a new paragraph."),
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
                    Text(L("ترتيب النص", "Tidy text"), fontSize = 14.sp, color = Palette.GoldDeep, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onSave(text.trim()) }) { Text(L("حفظ", "Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L("إلغاء", "Cancel")) } }
    )
}
