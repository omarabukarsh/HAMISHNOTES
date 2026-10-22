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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * درج «ملخصات»: كل نص التُقط بالهايلايتر في هذا المستند فقط، مرتبًا حسب صفحاته،
 * وكل صفحة من المستند لها صفحة ملخص مستقلة. يعرض النص مرتبًا (جمل منظمة) أو كما التُقط.
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
    val groups = remember(all, tidy) { Exporter.summaryGroups(all, tidy) }
    var editing by remember { mutableStateOf<TextMark?>(null) }

    Surface(
        modifier.fillMaxHeight().width(400.dp),
        color = Palette.DrawerBg,
        shadowElevation = 14.dp
    ) {
        Column(Modifier.fillMaxSize()) {
            // الترويسة
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SummaryGlyph(Palette.Navy, Palette.Gold)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("ملخصات", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Navy)
                    Text(
                        "${s.title} · ${all.size} تمييز",
                        fontSize = 12.sp, color = Palette.TextSecondary, maxLines = 1
                    )
                }
                IconButton(onClick = { s.summaryOpen = false }) {
                    Icon(Icons.Default.Close, "إغلاق الملخصات", tint = Palette.TextSecondary)
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
                TextButton(enabled = groups.isNotEmpty(), onClick = {
                    copy(context, Exporter.summaryText(s.title, groups))
                }) { Text("نسخ الكل", fontSize = 13.sp) }
                TextButton(enabled = groups.isNotEmpty(), onClick = onExport) {
                    Text("PDF", fontSize = 13.sp)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Gold.copy(alpha = 0.45f)))

            if (groups.isEmpty()) {
                EmptySummary()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    groups.forEach { g ->
                        item(key = "p${g.page}") {
                            SummaryPage(
                                s = s,
                                group = g,
                                marks = all.filter { it.page == g.page },
                                tidy = tidy,
                                onJump = onJump,
                                onEdit = { editing = it }
                            )
                        }
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

@Composable
private fun EmptySummary() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "لا توجد ملخصات بعد",
            fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "اختر الهايلايتر وفعّل «التقاط النص» بجانبه، ثم مرّر على أي سطر. " +
                "أو اضغط مطولًا على النص واختر «إلى الملخصات».\n" +
                "يعمل مع النص الإنجليزي فقط حاليًا.",
            fontSize = 13.sp, color = Palette.TextSecondary, lineHeight = 20.sp
        )
    }
}

/** صفحة ملخص واحدة على ورق عاجي، تخص صفحة واحدة من المستند. */
@Composable
private fun SummaryPage(
    s: DocSession,
    group: Exporter.SummaryGroup,
    marks: List<TextMark>,
    tidy: Boolean,
    onJump: (TextMark) -> Unit,
    onEdit: (TextMark) -> Unit
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(6.dp)
    Column(
        Modifier.fillMaxWidth()
            .shadow(3.dp, shape)
            .background(Palette.Ivory, shape)
            .heightIn(min = 120.dp)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "الصفحة ${group.page + 1}",
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.Navy,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { marks.firstOrNull()?.let(onJump) }) {
                Text("انتقال", fontSize = 12.sp, color = Palette.GoldDeep)
            }
        }
        Box(Modifier.width(36.dp).height(2.dp).background(Palette.Gold))
        Spacer(Modifier.height(10.dp))

        if (tidy) {
            group.items.forEach { line ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text("•", fontSize = 15.sp, color = Palette.GoldDeep, modifier = Modifier.padding(end = 8.dp))
                    Text(line, fontSize = 15.sp, color = Palette.TextPrimary, lineHeight = 22.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    copy(context, group.items.joinToString("\n") { "• $it" })
                }) { Text("نسخ الصفحة", fontSize = 12.sp) }
            }
        } else {
            marks.forEach { m -> MarkEntry(s, m, onJump, onEdit) }
        }
    }
}

@Composable
private fun MarkEntry(
    s: DocSession,
    m: TextMark,
    onJump: (TextMark) -> Unit,
    onEdit: (TextMark) -> Unit
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onJump(m) },
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier.padding(top = 3.dp).width(4.dp).height(18.dp)
                .clip(RoundedCornerShape(2.dp)).background(m.color)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            m.text, fontSize = 15.sp, color = Palette.TextPrimary, lineHeight = 22.sp,
            modifier = Modifier.weight(1f)
        )
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.MoreVert, "خيارات", tint = Palette.TextSecondary, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("انتقال إلى الموضع") }, onClick = { menu = false; onJump(m) })
                DropdownMenuItem(text = { Text("تعديل وترتيب النص") }, onClick = { menu = false; onEdit(m) })
                DropdownMenuItem(text = { Text("نسخ") }, onClick = { menu = false; copy(context, m.text) })
                DropdownMenuItem(
                    text = { Text("حذف التمييز", color = Palette.Danger) },
                    onClick = { menu = false; s.deleteMark(m.id) }
                )
            }
        }
    }
}

/** تعديل نص تمييز، مع زر «ترتيب» يصلح الأسطر والترقيم ويقسم النص إلى جمل. */
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
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp)
                )
                Box(
                    Modifier.clip(RoundedCornerShape(18.dp))
                        .border(1.dp, Palette.Gold, RoundedCornerShape(18.dp))
                        .clickable { text = TextTidy.tidy(text) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("ترتيب النص", fontSize = 14.sp, color = Palette.GoldDeep, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onSave(text.trim()) }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
