package com.hamishnotes.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image

/** ألوان أغلفة الدفاتر التي يختار منها المستخدم. */
val CoverColors = listOf(
    0xFF2F4B7C, 0xFF1F6F6B, 0xFF7A3E48, 0xFF5B4B8A, 0xFF8A5A2B,
    0xFF2E2E2E, 0xFF3F6E3A, 0xFFB5651D, 0xFF9C2F2F, 0xFF486581
).map { it.toInt() }

fun DocEntry.coverColorOrDefault(): Color = if (coverColor != 0) Color(coverColor) else Lib.cover

/** غلاف دفتر: كعب داكن، وملصق أبيض باسم المستند، وملصق مرسوم اختياري، وأوراق خلفه. */
@Composable
fun NotebookCover(entry: DocEntry, modifier: Modifier = Modifier, compact: Boolean = false) {
    val col = entry.coverColorOrDefault()
    val locked = entry.locked && entry.id !in AppController.unlocked
    BoxWithConstraints(modifier.aspectRatio(0.8f)) {
        val w = maxWidth
        val h = maxHeight
        // أوراق تطل من الجانب
        if (entry.pageCount > 1) {
            Box(
                Modifier.fillMaxSize().padding(top = 5.dp, bottom = 5.dp).offset(x = 5.dp)
                    .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                    .background(Color(0xFFF4EFE4)).border(1.dp, Color(0xFFE2D8C3), RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
            )
        }
        Box(
            Modifier.fillMaxSize().shadow(5.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(col)
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                .drawBehind {
                    // الكعب: شريط أغمق مع خط خياطة رفيع (لون الغلاف موحّد بلا تظليل)
                    val sw = size.width * 0.085f
                    drawRect(Color.Black.copy(alpha = 0.24f), size = Size(sw, size.height))
                    drawLine(Color.White.copy(alpha = 0.16f), Offset(sw * 1.3f, 0f), Offset(sw * 1.3f, size.height), strokeWidth = 2f)
                }
        ) {
            // ملصق الاسم
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = if (compact) 8.dp else 14.dp, start = if (compact) 14.dp else 22.dp, end = if (compact) 6.dp else 10.dp)
                    .fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White)
                    .padding(horizontal = if (compact) 6.dp else 9.dp, vertical = if (compact) 4.dp else 7.dp)
            ) {
                Text(
                    entry.name, fontSize = if (compact) 9.sp else 13.sp, lineHeight = if (compact) 11.sp else 16.sp,
                    fontWeight = FontWeight.Bold, color = Color(0xFF1E2A44),
                    maxLines = 3, overflow = TextOverflow.Ellipsis
                )
            }
            when {
                entry.isMap && !locked -> PhIcon(
                    R.drawable.ph_mindmap, Color.White.copy(alpha = 0.92f), if (compact) 26.dp else 44.dp,
                    modifier = Modifier.align(Alignment.Center).offset(x = 6.dp, y = 14.dp)
                )
                locked -> PhIcon(R.drawable.ph_lock, Lib.accent, if (compact) 22.dp else 36.dp, modifier = Modifier.align(Alignment.Center).offset(x = 6.dp, y = 14.dp))
                entry.sticker.isNotEmpty() -> StickerIcon(
                    entry.sticker, w * 0.5f,
                    Modifier.align(Alignment.Center).offset(x = w * 0.05f, y = h * 0.15f)
                )
            }
            if (entry.isMap) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(8.dp)
                        .clip(RoundedCornerShape(6.dp)).background(Color.White)
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) { Text(L("خريطة", "Map"), color = Color(0xFF1E2A44), fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            } else if (entry.isNote) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(8.dp)
                        .clip(RoundedCornerShape(6.dp)).background(Lib.accent)
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) { Text(L("ملاحظة", "Note"), color = Color(0xFF1E2A44), fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** مجلد مادة: رسم مجلد أصفر، والاسم وعدد المستندات تحته. */
@Composable
fun FolderTile(name: String, count: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.pressScale(0.95f).clip(RoundedCornerShape(14.dp))
            .background(if (selected) Lib.accentSoft else Color.Transparent)
            .clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.3f)) {
            val w = size.width
            val h = size.height
            val line = Color(0xFFC59A3C)
            val back = Color(0xFFF1CE79)
            val front = Color(0xFFF9DF94)
            val sw = 2.2.dp.toPx()
            // الظهر مع اللسان، بحد رفيع
            val tab = Path().apply {
                moveTo(w * 0.04f, h * 0.14f)
                lineTo(w * 0.36f, h * 0.14f)
                lineTo(w * 0.44f, h * 0.26f)
                lineTo(w * 0.96f, h * 0.26f)
                lineTo(w * 0.96f, h * 0.96f)
                lineTo(w * 0.04f, h * 0.96f)
                close()
            }
            drawPath(tab, back)
            drawPath(tab, line, style = Stroke(sw, join = StrokeJoin.Round))
            // ورقة بيضاء تطل
            drawRoundRect(Color.White, Offset(w * 0.14f, h * 0.2f), Size(w * 0.72f, h * 0.5f), CornerRadius(8f))
            drawRoundRect(line.copy(alpha = 0.55f), Offset(w * 0.14f, h * 0.2f), Size(w * 0.72f, h * 0.5f), CornerRadius(8f), style = Stroke(sw * 0.7f))
            // الوجه الأمامي
            drawRoundRect(front, Offset(w * 0.04f, h * 0.36f), Size(w * 0.92f, h * 0.6f), CornerRadius(14f))
            drawRoundRect(line, Offset(w * 0.04f, h * 0.36f), Size(w * 0.92f, h * 0.6f), CornerRadius(14f), style = Stroke(sw))
            // مقبض صغير
            drawRoundRect(Color.White, Offset(w * 0.36f, h * 0.56f), Size(w * 0.28f, h * 0.14f), CornerRadius(h * 0.07f))
            drawRoundRect(line, Offset(w * 0.36f, h * 0.56f), Size(w * 0.28f, h * 0.14f), CornerRadius(h * 0.07f), style = Stroke(sw * 0.8f))
        }
        Spacer(Modifier.height(6.dp))
        Text(name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Lib.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            when (count) {
                0 -> L("فارغة، أضف PDF", "empty, add a PDF")
                1 -> L("مستند واحد", "1 document")
                2 -> L("مستندان", "2 documents")
                else -> L("$count مستندات", "$count documents")
            },
            fontSize = 12.sp, color = Lib.muted, maxLines = 1
        )
    }
}

/** عنوان كبير بخط أصفر منحنٍ تحته. */
@Composable
fun UnderlinedTitle(text: String, size: Int = 40) {
    Text(
        text, fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = size.sp, lineHeight = (size + 6).sp,
        color = Lib.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.drawBehind {
            val y = size.toFloat() * 0f + this.size.height - 4f
            val p = Path().apply {
                moveTo(0f, y)
                quadraticBezierTo(this@drawBehind.size.width * 0.5f, y + 8f, this@drawBehind.size.width, y - 3f)
            }
            drawPath(p, Lib.accent, style = Stroke(width = 7f, cap = StrokeCap.Round))
        }
    )
}

/** زر دائري بأيقونة فقط. */
@Composable
fun IconCircleButton(icon: Int, description: String, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).pressScale().clip(CircleShape)
            .background(if (selected) Lib.accentSoft else Lib.card)
            .border(1.dp, Lib.line, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { PhIcon(icon, Lib.text, 20.dp, description) }
}

/** زر «جديد» الأصفر: ملاحظة جديدة أو استيراد PDF. */
@Composable
fun NewMenuButton(onNewNote: () -> Unit, onImport: () -> Unit, onNewMap: () -> Unit = {}, onNewCard: () -> Unit = {}) {
    var open by remember { mutableStateOf(false) }
    val caret by animateFloatAsState(if (open) 180f else 0f, Motion.grow(), label = "newCaret")
    Box {
        Row(
            Modifier.height(46.dp).pressScale(0.95f).clip(RoundedCornerShape(23.dp)).background(Lib.accent)
                .clickable { open = true }.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PhIcon(R.drawable.ph_plus, Color(0xFF1E2A44), 18.dp)
            Spacer(Modifier.width(6.dp))
            Text(L("جديد", "New"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E2A44))
            Spacer(Modifier.width(4.dp))
            PhIcon(R.drawable.ph_caret_down, Color(0xFF1E2A44), 14.dp, modifier = Modifier.rotate(caret))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Lib.card) {
            DropdownMenuItem(
                text = { Text(L("ملاحظة جديدة", "New note"), color = Lib.text) },
                leadingIcon = { PhIcon(R.drawable.ph_note, Lib.accentDeep, 20.dp) },
                onClick = { open = false; onNewNote() }
            )
            DropdownMenuItem(
                text = { Text(L("استيراد PDF", "Import PDF"), color = Lib.text) },
                leadingIcon = { PhIcon(R.drawable.ph_export, Lib.text, 20.dp, modifier = Modifier.rotate(180f)) },
                onClick = { open = false; onImport() }
            )
            DropdownMenuItem(
                text = { Text(L("خريطة ذهنية جديدة", "New mind map"), color = Lib.text) },
                leadingIcon = { PhIcon(R.drawable.ph_mindmap, Lib.iconBlue, 20.dp) },
                onClick = { open = false; onNewMap() }
            )
            DropdownMenuItem(
                text = { Text(L("بطاقة جديدة", "New card"), color = Lib.text) },
                leadingIcon = { PhIcon(R.drawable.ph_cards, CardTeal, 20.dp) },
                onClick = { open = false; onNewCard() }
            )
        }
    }
}

/** تخصيص غلاف الدفتر: اللون والملصق. */
@Composable
fun CoverDialog(entry: DocEntry, onDismiss: () -> Unit) {
    var color by remember { mutableStateOf(entry.coverColorOrDefault().toArgb()) }
    var sticker by remember { mutableStateOf(entry.sticker) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L("غلاف الدفتر", "Notebook cover")) },
        text = {
            Column {
                Box(Modifier.width(120.dp).align(Alignment.CenterHorizontally)) {
                    NotebookCover(entry.copy(coverColor = color, sticker = sticker), Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(14.dp))
                Text(L("اللون", "Color"), fontSize = 13.sp, color = Palette.TextSecondary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    CoverColors.forEach { c ->
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(Color(c))
                                .border(if (c == color) 3.dp else 0.dp, Lib.accent, CircleShape)
                                .clickable { color = c }
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(L("الملصق", "Sticker"), fontSize = 13.sp, color = Palette.TextSecondary)
                Spacer(Modifier.height(6.dp))
                (listOf("") + CoverStickers).chunked(7).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { st ->
                            Box(
                                Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                                    .background(if (st == sticker) Lib.accentSoft else Color.Transparent)
                                    .clickable { sticker = st },
                                contentAlignment = Alignment.Center
                            ) {
                                if (st.isEmpty()) Text(L("بدون", "None"), fontSize = 10.sp, color = Palette.TextSecondary, textAlign = TextAlign.Center)
                                else StickerIcon(st, 30.dp)
                            }
                        }
                        repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = dialogClose { AppController.setCover(entry.id, color, sticker); onDismiss() }) { Text(L("حفظ", "Save")) }
        },
        dismissButton = { TextButton(onClick = dialogClose(onDismiss)) { Text(L("إلغاء", "Cancel")) } }
    )
}

/** نص بخط يدوي (Caveat) مثل «تابع من حيث توقفت». */
@Composable
fun ScriptLabel(text: String, color: Color = Lib.accentDeep, size: Int = 16) {
    Text(text, fontSize = (size + 4).sp, fontFamily = Fonts.Script, color = color, fontWeight = FontWeight.Medium)
}

/** رسم دفتر بعلامة حمراء وقلم أزرق لبطاقة «تابع من حيث توقفت». */
@Composable
fun ResumeIllustration(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.ic_resume_notebook), null, modifier)
}
