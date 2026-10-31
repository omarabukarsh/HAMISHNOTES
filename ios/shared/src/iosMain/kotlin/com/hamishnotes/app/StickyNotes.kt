package com.hamishnotes.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID
import kotlin.math.roundToInt

/** ألوان الملاحظات اللاصقة (فاتحة هادئة). */
val StickyColors = listOf(
    0xFFF6B3B5, 0xFFF9CFA6, 0xFFF7E78F, 0xFFC4EDA8, 0xFFAEE5DF,
    0xFFCFE0FA, 0xFFE2D7FA, 0xFFF5C9E8, 0xFFE6E4DF
).map { it.toInt() }

/** نافذة الملاحظة داخل الصفحة: لونها، نمطها، خطوطها، واسم الكاتب. */
@Composable
fun NotesLayer(s: DocSession, page: Int, night: Boolean) {
    val list = s.notes[page] ?: return
    if (list.isEmpty()) return
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val W = constraints.maxWidth.toFloat()
        list.forEach { n ->
            val sel = s.selectedNoteId == n.id
            val nw = if (n.collapsed) NOTE_COLLAPSED else n.w
            val nh = if (n.collapsed) NOTE_COLLAPSED else n.h
            val wDp = with(density) { (nw * W).toDp() }
            val hDp = with(density) { (nh * W).toDp() }
            val base = Color(n.color)
            val col = if (night) nightOf(base) else base
            Box(
                Modifier.offset { IntOffset((n.x * W).roundToInt(), (n.y * W).roundToInt()) }
                    .size(wDp, hDp)
                    .shadow(if (sel) 6.dp else 2.dp, RoundedCornerShape(2.dp))
                    .background(col)
                    .then(if (sel) Modifier.border(2.dp, Color(0xFF3B82F6)) else Modifier)
            ) {
                if (n.collapsed) {
                    // ملاحظة مطوية: زاوية مثنية
                    Canvas(Modifier.fillMaxSize()) {
                        val k = size.width * 0.35f
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(size.width - k, size.height); lineTo(size.width, size.height - k); lineTo(size.width - k, size.height - k); close()
                        }
                        drawPath(path, Color.Black.copy(alpha = 0.12f))
                    }
                } else {
                    Canvas(Modifier.fillMaxSize()) {
                        val lineCol = Color.Black.copy(alpha = 0.08f)
                        when (n.style) {
                            1 -> drawRect(Color.Black.copy(alpha = 0.06f), size = androidx.compose.ui.geometry.Size(size.width * 0.07f, size.height))
                            2 -> {
                                var y = size.height * 0.16f
                                while (y < size.height * 0.95f) {
                                    drawLine(lineCol, Offset(size.width * 0.05f, y), Offset(size.width * 0.95f, y), strokeWidth = 1.5f)
                                    y += size.height * 0.11f
                                }
                            }
                            3 -> {
                                val step = size.width / 10f
                                var x = step
                                while (x < size.width) { drawLine(lineCol, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
                                var y = step
                                while (y < size.height) { drawLine(lineCol, Offset(0f, y), Offset(size.width, y), 1f); y += step }
                            }
                        }
                        n.ink.forEach { st -> drawStroke(st, if (night) nightOf(st.color) else st.color, scaleW = size.width) }
                    }
                    val pad = with(density) { (n.w * W * 0.06f).toDp() }
                    val fs = with(density) { (n.textSize() * W).toSp() }
                    val nameH = if (n.author.isNotBlank()) with(density) { (n.textSize() * W * 1.1f).toDp() } else 0.dp
                    // الاسم في أعلى الملاحظة
                    if (n.author.isNotBlank()) {
                        Text(
                            n.author, fontSize = fs * 0.7f, color = Color.Black.copy(alpha = 0.5f), maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.TopStart).padding(start = pad, end = pad, top = pad * 0.5f)
                        )
                    }
                    val textColor = if (night) Color(0xFFEDEDED) else Color(0xFF1F2933)
                    val textMod = Modifier.fillMaxWidth().padding(start = pad, end = pad, top = pad + nameH)
                    if (s.noteTextEditId == n.id) {
                        // الكتابة مباشرة داخل الملاحظة بلوحة المفاتيح
                        NoteTextEditor(s, n, fs, textColor, textMod)
                    } else if (n.text.isNotBlank()) {
                        Text(n.text, fontSize = fs, lineHeight = fs * 1.3f, color = textColor, overflow = TextOverflow.Clip, modifier = textMod)
                    }
                }
            }
            if (sel && !n.collapsed) {
                // مقابض الزوايا (المقبض السفلي الأيسر/الأيمن حسب الاتجاه للتكبير)
                listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f).forEach { (fx, fy) ->
                    Box(
                        Modifier.offset {
                            IntOffset(((n.x + fx * nw) * W).roundToInt() - with(density) { 7.dp.roundToPx() }, ((n.y + fy * nh) * W).roundToInt() - with(density) { 7.dp.roundToPx() })
                        }.size(14.dp).clip(CircleShape).background(Color.White).border(2.dp, Color(0xFF3B82F6), CircleShape)
                    )
                }
            }
        }
    }
}

/** شريط ألوان الملاحظة اللاصقة (عند اختيار أداة الملاحظات): اختر لونًا ثم انقر مكان الملاحظة. */
@Composable
fun StickyBar() {
    val pill = RoundedCornerShape(26.dp)
    var panel by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(start = 64.dp, end = 64.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.shadow(6.dp, pill).background(Color.White, pill).border(1.dp, Palette.Divider, pill).clip(pill)) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StickyColors.forEach { col ->
                    val sel = PenPrefs.stickyColor == col
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (sel) Palette.NavySoft else Color.Transparent)
                            .clickable { PenPrefs.changeStickyColor(col) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(Color(col)).border(1.dp, Palette.Divider, RoundedCornerShape(6.dp)))
                    }
                }
                ToolbarSep()
                if (AppController.noteClipboard != null) {
                    TBtn(selected = AppController.notePastePending, onClick = {
                        AppController.notePastePending = !AppController.notePastePending
                        if (AppController.notePastePending) AppController.messages.tryEmit(L("انقر مكان لصق الملاحظة", "Tap where to paste the note"))
                    }) { PhIcon(R.drawable.ph_copy, tintFor(AppController.notePastePending), 20.dp, L("لصق", "Paste")) }
                }
                Box {
                    TBtn(onClick = { panel = true }) { PhIcon(R.drawable.ph_caret_down, Palette.TextSecondary, 20.dp, L("أنماط الملاحظات", "Note styles")) }
                    DropdownMenu(
                        expanded = panel, onDismissRequest = { panel = false },
                        shape = RoundedCornerShape(18.dp), containerColor = Color.White, shadowElevation = 12.dp
                    ) { StickerPanel { panel = false } }
                }
                TBtn(onClick = { PenPrefs.stickyActive = false; AppController.notePastePending = false }) { PhIcon(R.drawable.ph_close, Palette.TextSecondary, 20.dp) }
            }
        }
    }
}

/** لوحة أنماط الملاحظات: سادة، بشريط، مسطّرة، مربعات؛ بعدة ألوان. */
@Composable
fun StickerPanel(onDone: () -> Unit) {
    Column(Modifier.width(330.dp).heightIn(max = 460.dp).verticalScroll(rememberScrollState()).padding(14.dp)) {
        Text(L("ملاحظات لاصقة", "Sticky notes"), fontSize = 15.sp, color = Palette.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(L("اختر نمطًا ثم انقر على الصفحة لوضع الملاحظة", "Pick a style, then tap the page to place the note"), fontSize = 12.sp, color = Palette.TextSecondary)
        Spacer(Modifier.height(10.dp))
        val combos = listOf(0, 1, 2, 3).flatMap { st -> StickyColors.take(6).map { st to it } }
        combos.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                row.forEach { (st, col) ->
                    Box(
                        Modifier.weight(1f).height(72.dp).shadow(2.dp).background(Color(col))
                            .clickable {
                                PenPrefs.changeStickyStyle(st)
                                PenPrefs.changeStickyColor(col)
                                PenPrefs.stickyActive = true
                                onDone()
                            }
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val lc = Color.Black.copy(alpha = 0.09f)
                            when (st) {
                                1 -> drawRect(Color.Black.copy(alpha = 0.06f), size = androidx.compose.ui.geometry.Size(size.width * 0.08f, size.height))
                                2 -> { var y = size.height * 0.2f; while (y < size.height) { drawLine(lc, Offset(6f, y), Offset(size.width - 6f, y), 1.5f); y += size.height * 0.18f } }
                                3 -> {
                                    val step = size.width / 8f
                                    var x = step; while (x < size.width) { drawLine(lc, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
                                    var y = step; while (y < size.height) { drawLine(lc, Offset(0f, y), Offset(size.width, y), 1f); y += step }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** شريط أدوات الملاحظة المحددة. */
@Composable
fun NoteBar(s: DocSession) {
    val c = AppController
    val id = s.selectedNoteId ?: return
    val n = s.note(id) ?: return
    var colors by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var authorDialog by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 8.dp) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).clickable { colors = true },
                    contentAlignment = Alignment.Center
                ) { Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(Color(n.color)).border(1.dp, Palette.Divider, RoundedCornerShape(6.dp))) }
                DropdownMenu(expanded = colors, onDismissRequest = { colors = false }) {
                    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StickyColors.forEach { col ->
                            Box(
                                Modifier.size(30.dp).clip(RoundedCornerShape(6.dp)).background(Color(col))
                                    .border(if (col == n.color) 2.dp else 1.dp, if (col == n.color) Palette.Navy else Palette.Divider, RoundedCornerShape(6.dp))
                                    .clickable { s.editNote(id) { it.copy(color = col) }; colors = false }
                            )
                        }
                    }
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(L("سادة", "Plain"), L("شريط", "Strip"), L("أسطر", "Lined"), L("مربعات", "Grid")).forEachIndexed { st, label ->
                            TextButton(onClick = { s.editNote(id) { it.copy(style = st) }; colors = false }) {
                                Text(label, color = if (n.style == st) Palette.Navy else Palette.TextSecondary, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            // كتابة نص بلوحة المفاتيح
            TBtn(selected = s.noteTextEditId == id, onClick = { s.noteTextEditId = if (s.noteTextEditId == id) null else id }) {
                PhIcon(R.drawable.ph_text, Palette.TextSecondary, 20.dp, L("كتابة نص", "Type text"))
            }
            // طيّ الملاحظة إلى مربع صغير
            TBtn(onClick = { s.editNote(id) { it.copy(collapsed = !it.collapsed) } }) {
                PhIcon(if (n.collapsed) R.drawable.ph_zoom_in else R.drawable.ph_zoom_out, Palette.TextSecondary, 20.dp, L("طيّ/فتح", "Collapse/expand"))
            }
            TBtn(onClick = {
                c.noteClipboard = n
                s.deleteNote(id)
                c.messages.tryEmit(L("قُصّت الملاحظة؛ الصقها من أداة الملاحظات", "Note cut; paste it from the sticky note tool"))
            }) { PhIcon(R.drawable.ph_scissors, Palette.TextSecondary, 20.dp, L("قص", "Cut")) }
            TBtn(onClick = { s.duplicateNote(id) }) { PhIcon(R.drawable.ph_copy, Palette.TextSecondary, 20.dp, L("تكرار", "Duplicate")) }
            TBtn(onClick = { s.deleteNote(id) }) { PhIcon(R.drawable.ph_trash, Palette.Danger, 20.dp, L("حذف", "Delete")) }
            Box {
                TBtn(onClick = { more = true }) { PhIcon(R.drawable.ph_more, Palette.TextSecondary, 20.dp) }
                DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                    DropdownMenuItem(text = { Text(L("نسخ", "Copy")) }, onClick = {
                        more = false; c.noteClipboard = n
                        c.messages.tryEmit(L("نُسخت الملاحظة", "Note copied"))
                    })
                    DropdownMenuItem(text = { Text(L("مسح الكتابة داخلها", "Clear its writing")) }, onClick = {
                        more = false; s.editNote(id) { it.copy(ink = emptyList()) }
                    })
                    DropdownMenuItem(text = { Text(L("الاسم على الملاحظات…", "Name on notes…")) }, onClick = { more = false; authorDialog = true })
                }
            }
            TextButton(onClick = { s.noteTextEditId = null; s.selectedNoteId = null }) { Text(L("تم", "Done")) }
        }
    }
    if (authorDialog) {
        var name by remember { mutableStateOf(PenPrefs.author) }
        AlertDialog(
            onDismissRequest = { authorDialog = false },
            title = { Text(L("الاسم على الملاحظات", "Name on notes")) },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(L("اسمك (اختياري)", "Your name (optional)")) })
            },
            confirmButton = {
                TextButton(onClick = {
                    PenPrefs.changeAuthor(name.trim())
                    s.editNote(id) { it.copy(author = name.trim()) }
                    authorDialog = false
                }) { Text(L("حفظ", "Save")) }
            },
            dismissButton = { TextButton(onClick = { authorDialog = false }) { Text(L("إلغاء", "Cancel")) } }
        )
    }
}

/** ملاحظة جديدة مركزها عند النقطة (nx, ny) بإحداثيات الصفحة. */
fun newStickyAt(page: Int, nx: Float, ny: Float, pageH: Float): StickyNote {
    val size = 0.2f
    val x = (nx - size / 2f).coerceIn(0f, 1f - size)
    val y = (ny - size / 2f).coerceIn(0f, (pageH - size).coerceAtLeast(0f))
    return StickyNote(
        id = UUID.randomUUID().toString(), page = page, x = x, y = y, w = size, h = size,
        color = PenPrefs.stickyColor, style = PenPrefs.stickyStyle, author = PenPrefs.author
    )
}


/** محرر نص داخل الملاحظة: يفتح لوحة المفاتيح مباشرة، والتعديل كله خطوة تراجع واحدة. */
@Composable
private fun NoteTextEditor(s: DocSession, n: StickyNote, fs: androidx.compose.ui.unit.TextUnit, color: Color, modifier: Modifier) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    var value by remember(n.id) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(n.text, androidx.compose.ui.text.TextRange(n.text.length))) }
    androidx.compose.runtime.DisposableEffect(n.id) {
        s.beginEdit()
        onDispose { s.endEdit() }
    }
    androidx.compose.runtime.LaunchedEffect(n.id) { focus.requestFocus() }
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = { v ->
            value = v
            s.updateNote(n.id) { it.copy(text = v.text) }
        },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = fs, lineHeight = fs * 1.3f, color = color),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Palette.Navy),
        modifier = modifier.focusRequester(focus)
    )
}
