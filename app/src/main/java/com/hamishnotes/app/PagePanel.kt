package com.hamishnotes.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * درج «خيارات الصفحة» على اليمين: كل ما يخص الصفحة الحالية في مكان واحد،
 * مجموعات بطاقات بيضاء على ورق عاجي، والخيارات الخطرة بالأحمر في قسم منفصل.
 */
@Composable
fun PagePanel(s: DocSession, pdf: PdfDoc, modifier: Modifier = Modifier) {
    val c = AppController
    val scope = rememberCoroutineScope()
    val n = pdf.count
    val page = s.currentPage().coerceIn(0, maxOf(0, n - 1))
    val ref = pdf.pages.getOrNull(page)
    val blank = ref?.src?.startsWith("blank:") == true
    var jump by remember { mutableStateOf(false) }
    var addPage by remember { mutableStateOf(false) }
    var outlineAdd by remember { mutableStateOf(false) }
    var changeTpl by remember { mutableStateOf(false) }
    var moveOpen by remember { mutableStateOf(false) }
    var itemsDialog by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun order(f: MutableList<Int>.() -> Unit): List<Int> = (0 until n).toMutableList().apply(f)
    fun go(p: Int) = scope.launch { s.listState.scrollToItem(p.coerceIn(0, maxOf(0, s.pdf?.count?.minus(1) ?: 0))) }

    Surface(
        modifier.fillMaxHeight().width(380.dp),
        color = Palette.DrawerBg,
        shadowElevation = 14.dp
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 6.dp, top = 14.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        L("الصفحة ${page + 1}", "Page ${page + 1}"), fontFamily = Fonts.Display, fontSize = 24.sp,
                        fontWeight = FontWeight.Bold, color = Palette.Navy
                    )
                    Text(
                        (if (blank) Templates.labelOf(Templates.parse(ref!!.src).first) else L("صفحة من الملف", "Page from the file")) +
                            L(" · من $n", " · of $n"),
                        fontSize = 12.sp, color = Palette.TextSecondary
                    )
                }
                IconButton(onClick = { s.pageMenuOpen = false }) {
                    PhIcon(R.drawable.ph_close, Palette.TextSecondary, 20.dp, L("إغلاق", "Close"))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Gold.copy(alpha = 0.45f)))

            Group {
                val marked = page in s.bookmarks
                PanelRow(
                    if (marked) L("إزالة العلامة المرجعية", "Remove bookmark") else L("علامة مرجعية للصفحة", "Bookmark page"),
                    if (marked) R.drawable.ph_bookmark_fill else R.drawable.ph_bookmark,
                    tint = if (marked) Palette.GoldDeep else Palette.Navy
                ) { s.toggleBookmark(page) }
                PanelDivider()
                PanelRow(L("تكرار الصفحة", "Duplicate page"), R.drawable.ph_copy) {
                    s.rearrangePages(order { add(page + 1, page) })
                    c.messages.tryEmit(L("كُرّرت الصفحة ${page + 1}", "Page ${page + 1} duplicated"))
                }
                PanelDivider()
                PanelRow(L("إضافة صفحة بعدها", "Add page after"), R.drawable.ph_add_page) { addPage = true }
                PanelDivider()
                PanelRow(L("إضافة الصفحة إلى الفهرس", "Add page to outline"), R.drawable.ph_list) { outlineAdd = true }
                PanelDivider()
                PanelRow(
                    L("تغيير القالب", "Change template"), R.drawable.ph_pages,
                    sub = if (blank) null else L("لصفحات الملاحظات الفارغة فقط", "Blank note pages only"),
                    enabled = blank
                ) { changeTpl = true }
                PanelDivider()
                PanelRow(L("نقل الصفحة", "Move page"), R.drawable.ph_caret_down, trailing = if (moveOpen) "▴" else "▾") { moveOpen = !moveOpen }
                if (moveOpen) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SmallAction(L("للأعلى", "Up"), page > 0) { s.rearrangePages(order { add(page - 1, removeAt(page)) }); go(page - 1) }
                        SmallAction(L("للأسفل", "Down"), page < n - 1) { s.rearrangePages(order { add(page + 1, removeAt(page)) }); go(page + 1) }
                        SmallAction(L("للبداية", "Start"), page > 0) { s.rearrangePages(order { add(0, removeAt(page)) }); go(0) }
                        SmallAction(L("للنهاية", "End"), page < n - 1) { s.rearrangePages(order { add(removeAt(page)) }); go(n - 1) }
                    }
                }
                PanelDivider()
                PanelRow(L("الانتقال إلى صفحة", "Go to page"), R.drawable.ph_list, trailing = "${page + 1} / $n") { jump = true }
            }

            SectionLabel(L("مسح أو حذف", "Clear or delete"))
            Group {
                PanelRow(L("حذف عناصر محددة", "Delete specific items"), R.drawable.ph_shapes) { itemsDialog = true }
                PanelDivider()
                PanelRow(L("مسح الصفحة", "Clear page"), R.drawable.ph_eraser, tint = Palette.Danger, textColor = Palette.Danger) { confirmClear = true }
                PanelDivider()
                PanelRow(
                    L("حذف الصفحة", "Delete page"), R.drawable.ph_trash, tint = Palette.Danger, textColor = Palette.Danger,
                    enabled = n > 1
                ) { confirmDelete = true }
            }

            SectionLabel(L("الإعدادات", "Settings"))
            Group {
                PanelRow(L("كل الإعدادات وتخصيص الأدوات", "All settings and tool customization"), R.drawable.ph_sliders) {
                    s.pageMenuOpen = false
                    c.settingsOpen = true
                }
            }
        }
    }

    if (jump) PageJumpDialog(n, page, onGo = { p -> jump = false; go(p) }, onDismiss = { jump = false })

    if (outlineAdd) {
        OutlineTitleDialog(L("عنوان للصفحة ${page + 1}", "Heading for page ${page + 1}"), "", onDismiss = { outlineAdd = false }) { t ->
            outlineAdd = false
            s.addOutlineEntry(page, t)
            c.messages.tryEmit(L("أُضيفت الصفحة إلى الفهرس", "Page added to the outline"))
        }
    }
    if (addPage) {
        TemplatePickerDialog(
            mode = PickerMode.ADD_PAGE,
            onDismiss = { addPage = false },
            onConfirm = { choice, _, atEnd ->
                addPage = false
                c.addBlankPage(s, if (atEnd) n - 1 else page, choice.kind, choice.color, choice.ratio())
            }
        )
    }

    if (changeTpl && ref != null) {
        val (kind, color) = Templates.parse(ref.src)
        val d = Templates.defaultChoice()
        TemplatePickerDialog(
            mode = PickerMode.CHANGE,
            initial = d.copy(kind = kind, color = color, landscape = ref.ratio > 1f),
            sizeLocked = true,
            onDismiss = { changeTpl = false },
            onConfirm = { choice, _, _ ->
                changeTpl = false
                s.setPageSrc(page, Templates.src(choice.kind, choice.color))
            }
        )
    }

    if (itemsDialog) {
        var ink by remember { mutableStateOf(true) }
        var hl by remember { mutableStateOf(false) }
        var tx by remember { mutableStateOf(false) }
        var im by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { itemsDialog = false },
            title = { Text(L("حذف عناصر من الصفحة ${page + 1}", "Delete items from page ${page + 1}")) },
            text = {
                Column {
                    CheckRow(L("الكتابة اليدوية والرسم", "Handwriting and drawings"), ink) { ink = it }
                    CheckRow(L("التظليل (ويُزال من الملخص)", "Highlights (also removed from the summary)"), hl) { hl = it }
                    CheckRow(L("مربعات النص والملاحظات اللاصقة", "Text boxes and sticky notes"), tx) { tx = it }
                    CheckRow(L("الصور", "Images"), im) { im = it }
                    Text(L("يمكنك التراجع عن ذلك.", "You can undo this."), fontSize = 12.sp, color = Palette.TextSecondary)
                }
            },
            confirmButton = {
                TextButton(enabled = ink || hl || tx || im, onClick = {
                    itemsDialog = false
                    s.clearPageItems(page, ink, hl, tx, im)
                }) { Text(L("حذف", "Delete"), color = Palette.Danger) }
            },
            dismissButton = { TextButton(onClick = { itemsDialog = false }) { Text(L("إلغاء", "Cancel")) } }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(L("مسح الصفحة ${page + 1}؟", "Clear page ${page + 1}?")) },
            text = { Text(L("تُمسح الكتابة والتظليل والنصوص والصور من هذه الصفحة، وتبقى الصفحة نفسها. يمكنك التراجع.", "Writing, highlights, text and images are removed from this page; the page itself stays. You can undo.")) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; s.clearPageItems(page, true, true, true, true) }) {
                    Text(L("مسح", "Clear"), color = Palette.Danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(L("إلغاء", "Cancel")) } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(L("حذف الصفحة ${page + 1}؟", "Delete page ${page + 1}?")) },
            text = { Text(L("ستُحذف الصفحة وكل ما كتبته عليها، ولا يمكن التراجع.", "The page and everything written on it will be deleted. This cannot be undone.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    s.rearrangePages(order { removeAt(page) })
                }) { Text(L("حذف", "Delete"), color = Palette.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(L("إلغاء", "Cancel")) } }
        )
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = 12.dp)
            .clip(shape).background(Color.White).border(1.dp, Palette.Divider, shape)
    ) { content() }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Navy,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp)
    )
}

@Composable
private fun PanelDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 52.dp).height(1.dp).background(Palette.Divider))
}

@Composable
private fun PanelRow(
    label: String,
    icon: Int,
    tint: Color = Palette.Navy,
    textColor: Color = Palette.TextPrimary,
    sub: String? = null,
    trailing: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val a = if (enabled) 1f else 0.4f
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PhIcon(icon, tint.copy(alpha = a), 22.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 16.sp, color = textColor.copy(alpha = a))
            if (sub != null) Text(sub, fontSize = 11.sp, color = Palette.TextSecondary)
        }
        if (trailing != null) Text(trailing, fontSize = 13.sp, color = Palette.TextSecondary)
    }
}

@Composable
private fun SmallAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.clip(shape).background(if (enabled) Palette.NavySoft else Palette.Background)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 13.sp, color = if (enabled) Palette.Navy else Palette.TextSecondary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, fontSize = 14.sp)
    }
}
