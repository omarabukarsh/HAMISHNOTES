package com.mynotes.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** لماذا فُتح منتقي الورق. */
enum class PickerMode { NEW_NOTE, ADD_PAGE, CHANGE, DEFAULT }

/** صور مصغّرة للقوالب، تُرسم مرة واحدة لكل قالب ولون ونسبة. */
private object PreviewCache {
    val map = mutableStateMapOf<String, ImageBitmap>()
    fun get(kind: String, color: Int, ratio: Float): ImageBitmap {
        val k = "$kind:$color:${(ratio * 1000).toInt()}"
        map[k]?.let { return it }
        val w = 260
        val h = (w / ratio).toInt().coerceIn(120, 600)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Templates.draw(Canvas(b), w.toFloat(), h.toFloat(), kind, color)
        val img = b.asImageBitmap()
        map[k] = img
        return img
    }
}

/** صورة مصغّرة لورقة بقالبها ولونها ونسبتها. */
@Composable
fun PaperPreview(kind: String, color: Int, ratio: Float, width: Dp, selected: Boolean, modifier: Modifier = Modifier) {
    val img = remember(kind, color, ratio) { PreviewCache.get(kind, color, ratio) }
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier.width(width).aspectRatio(ratio.coerceIn(0.4f, 2.5f))
            .shadow(if (selected) 6.dp else 2.dp, shape)
            .clip(shape)
            .background(Color(color))
            .border(if (selected) 3.dp else 1.dp, if (selected) Palette.Gold else Palette.Divider, shape)
    ) {
        Image(img, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(0.4f, 2.5f)))
    }
}

/**
 * منتقي الورق: صورة القالب كما سيظهر بلونه ومقاسه، مقسّم إلى أقسام.
 * يُستعمل لإنشاء ملاحظة، إضافة صفحة، تغيير قالب صفحة، واختيار القالب الافتراضي.
 * onConfirm(اختيار، الاسم، في نهاية الملف، اجعله الافتراضي)
 */
@Composable
fun TemplatePickerDialog(
    mode: PickerMode,
    initial: PaperChoice = Templates.defaultChoice(),
    sizeLocked: Boolean = false,
    onDismiss: () -> Unit,
    onPdf: ((Boolean) -> Unit)? = null,
    onConfirm: (PaperChoice, String, Boolean) -> Unit
) {
    var choice by remember { mutableStateOf(initial) }
    var name by remember { mutableStateOf(L("ملاحظة جديدة", "New note")) }
    var atEnd by remember { mutableStateOf(false) }
    var makeDefault by remember { mutableStateOf(false) }
    var sizeMenu by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    val collapsed = remember { mutableStateMapOf<Int, Boolean>() }
    val ratio = choice.ratio()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth(0.92f).widthIn(max = 1040.dp).fillMaxHeight(0.9f),
            shape = RoundedCornerShape(22.dp),
            color = Palette.DrawerBg,
            shadowElevation = 18.dp
        ) {
            Column(Modifier.fillMaxWidth()) {
                // العنوان
                Text(
                    when (mode) {
                        PickerMode.NEW_NOTE -> L("ملاحظة جديدة", "New note")
                        PickerMode.ADD_PAGE -> L("إضافة صفحة", "Add page")
                        PickerMode.CHANGE -> L("تغيير قالب الصفحة", "Change page template")
                        PickerMode.DEFAULT -> L("القالب الافتراضي للملاحظات", "Default notebook template")
                    },
                    fontFamily = Fonts.Display, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Navy,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 12.dp)
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Gold.copy(alpha = 0.45f)))
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    // العمود الجانبي: الورقة المختارة كبيرة
                    Column(
                        Modifier.width(280.dp).fillMaxHeight().background(Palette.Ivory)
                            .verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (mode == PickerMode.NEW_NOTE) {
                            OutlinedTextField(
                                value = name, onValueChange = { name = it }, singleLine = true,
                                label = { Text(L("الاسم", "Name")) }
                            )
                        }
                        if (mode == PickerMode.ADD_PAGE) {
                            Text(L("المكان", "Position"), fontSize = 13.sp, color = Palette.TextSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ChoiceChip(L("بعد الحالية", "After current"), !atEnd) { atEnd = false }
                                ChoiceChip(L("في النهاية", "At the end"), atEnd) { atEnd = true }
                            }
                        }
                        Text(L("الورقة", "Paper"), fontSize = 13.sp, color = Palette.TextSecondary)
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            PaperPreview(
                                choice.kind, choice.color, ratio,
                                if (ratio > 1f) 220.dp else (220.dp * ratio).coerceAtLeast(130.dp), selected = true
                            )
                        }
                        Text(
                            Templates.labelOf(choice.kind) + " · " + Templates.sizeOf(choice.size).label +
                                if (choice.landscape) L(" · أفقي", " · landscape") else "",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.Navy,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                        )
                        if (mode == PickerMode.NEW_NOTE || mode == PickerMode.ADD_PAGE) {
                            Row(
                                Modifier.clip(RoundedCornerShape(8.dp)).clickable { makeDefault = !makeDefault }.padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.Checkbox(checked = makeDefault, onCheckedChange = { makeDefault = it })
                                Text(L("اجعله القالب الافتراضي", "Make it the default"), fontSize = 13.sp, color = Palette.TextPrimary)
                            }
                        }
                    }
                    Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.Divider))
                    // الخيارات والأقسام
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // المقاس
                            if (!sizeLocked) {
                                Box {
                                    PillButton(Templates.sizeOf(choice.size).label + "  ▾") { sizeMenu = true }
                                    DropdownMenu(expanded = sizeMenu, onDismissRequest = { sizeMenu = false }) {
                                        Templates.sizes.forEach { sz ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        sz.label, fontSize = 15.sp,
                                                        fontWeight = if (sz.id == choice.size) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (sz.id == choice.size) Palette.GoldDeep else Palette.TextPrimary
                                                    )
                                                },
                                                onClick = { choice = choice.copy(size = sz.id); sizeMenu = false }
                                            )
                                        }
                                    }
                                }
                                // الاتجاه
                                Row(
                                    Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White)
                                        .border(1.dp, Palette.Divider, RoundedCornerShape(12.dp))
                                ) {
                                    OrientationButton(portrait = true, selected = !choice.landscape) { choice = choice.copy(landscape = false) }
                                    OrientationButton(portrait = false, selected = choice.landscape) { choice = choice.copy(landscape = true) }
                                }
                            }
                            // لون الورقة
                            PaperColorRow(choice.color, { choice = choice.copy(color = it) }, { picker = true })
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Divider))
                        Column(
                            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                                .padding(vertical = 8.dp)
                        ) {
                            Templates.categories.forEachIndexed { ci, cat ->
                                val closed = collapsed[ci] == true
                                Row(
                                    Modifier.fillMaxWidth().clickable { collapsed[ci] = !closed }
                                        .padding(horizontal = 18.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(if (closed) "▸" else "▾", fontSize = 14.sp, color = Palette.GoldDeep)
                                    Spacer(Modifier.width(8.dp))
                                    Text(cat.title(), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Navy)
                                }
                                if (!closed) {
                                    Row(
                                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                            .padding(horizontal = 18.dp).padding(bottom = 14.dp),
                                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        cat.kinds.forEach { k ->
                                            val sel = k.id == choice.kind
                                            Column(
                                                Modifier.width(118.dp).clip(RoundedCornerShape(10.dp))
                                                    .clickable { choice = choice.copy(kind = k.id) }
                                                    .padding(4.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Box(Modifier.height(150.dp), contentAlignment = Alignment.Center) {
                                                    PaperPreview(
                                                        k.id, choice.color, ratio,
                                                        if (ratio > 1f) 108.dp else (140.dp * ratio).coerceAtMost(108.dp), sel
                                                    )
                                                }
                                                Spacer(Modifier.height(6.dp))
                                                Text(
                                                    k.label(), fontSize = 13.sp, textAlign = TextAlign.Center,
                                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (sel) Palette.GoldDeep else Palette.TextPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Divider))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onPdf != null) {
                        TextButton(onClick = { onPdf(atEnd) }) { Text(L("إضافة ملف PDF…", "Add PDF file…"), color = Palette.Navy) }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(L("إلغاء", "Cancel"), color = Palette.TextSecondary) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (makeDefault || mode == PickerMode.DEFAULT) Templates.saveDefault(choice)
                            onConfirm(choice, name, atEnd)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.Navy)
                    ) {
                        Text(
                            when (mode) {
                                PickerMode.NEW_NOTE -> L("إنشاء", "Create")
                                PickerMode.ADD_PAGE -> L("إضافة صفحة", "Add page")
                                PickerMode.CHANGE -> L("تطبيق", "Apply")
                                PickerMode.DEFAULT -> L("حفظ", "Save")
                            }
                        )
                    }
                }
            }
        }
    }
    if (picker) {
        ColorPickerDialog(
            initial = Color(choice.color),
            onPick = { choice = choice.copy(color = it.toArgb()); picker = false },
            onDismiss = { picker = false }
        )
    }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(18.dp)).background(Color.White)
            .border(1.dp, Palette.Divider, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp)
    ) { Text(label, fontSize = 14.sp, color = Palette.TextPrimary, fontWeight = FontWeight.Medium) }
}

/** زر الاتجاه: مستطيل طولي أو عرضي صغير. */
@Composable
private fun OrientationButton(portrait: Boolean, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 46.dp, height = 40.dp)
            .background(if (selected) Palette.NavySoft else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(width = if (portrait) 14.dp else 22.dp, height = if (portrait) 20.dp else 14.dp)
                .border(2.dp, if (selected) Palette.Navy else Palette.TextSecondary, RoundedCornerShape(3.dp))
        )
    }
}
