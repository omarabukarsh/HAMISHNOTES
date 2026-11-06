package com.hamishnotes.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** إعدادات أداة النص: تُطبَّق على المربعات الجديدة وعلى المربع الذي تكتب فيه الآن. */
object TextPrefs {
    private var sp: SharedPreferences? = null

    var font by mutableStateOf(""); private set
    /** الحجم بالنقاط (عرض الصفحة = 595 نقطة كورقة A4). */
    var sizePt by mutableStateOf(14); private set
    var bold by mutableStateOf(false); private set
    var italic by mutableStateOf(false); private set
    var underline by mutableStateOf(false); private set
    var align by mutableStateOf(0); private set
    /** اللون؛ null = لون القلم الحالي. */
    var color by mutableStateOf<Int?>(null); private set

    const val SIZE_MIN = 6
    const val SIZE_MAX = 54

    fun init(ctx: Context) {
        val p = ctx.getSharedPreferences("hamish_text", Context.MODE_PRIVATE)
        sp = p
        font = p.getString("font", "") ?: ""
        sizePt = p.getInt("size", 14).coerceIn(SIZE_MIN, SIZE_MAX)
        bold = p.getBoolean("bold", false)
        italic = p.getBoolean("italic", false)
        underline = p.getBoolean("underline", false)
        align = p.getInt("align", 0).coerceIn(0, 2)
        color = if (p.contains("color")) p.getInt("color", 0) else null
    }

    fun sizeUnits(): Float = sizePt / 595f

    fun applyTo(tb: TextBox): TextBox = tb.copy(
        font = font, size = sizeUnits(), bold = bold, italic = italic, underline = underline, align = align,
        color = color?.let { Color(it) } ?: tb.color
    )

    fun changeFont(v: String) { font = v; sp?.edit()?.putString("font", v)?.apply() }
    fun changeSize(v: Int) { sizePt = v.coerceIn(SIZE_MIN, SIZE_MAX); sp?.edit()?.putInt("size", sizePt)?.apply() }
    fun changeBold(v: Boolean) { bold = v; sp?.edit()?.putBoolean("bold", v)?.apply() }
    fun changeItalic(v: Boolean) { italic = v; sp?.edit()?.putBoolean("italic", v)?.apply() }
    fun changeUnderline(v: Boolean) { underline = v; sp?.edit()?.putBoolean("underline", v)?.apply() }
    fun changeAlign(v: Int) { align = v.coerceIn(0, 2); sp?.edit()?.putInt("align", align)?.apply() }
    fun changeColor(v: Color) { color = v.toArgb(); sp?.edit()?.putInt("color", v.toArgb())?.apply() }
}

/** زر تبديل في شريط النمط (عريض/مائل/تحته خط/المحاذاة). */
@Composable
private fun StyleToggle(on: Boolean, modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier.fillMaxHeight().padding(2.dp)
            .then(if (on) Modifier.shadow(2.dp, RoundedCornerShape(9.dp)) else Modifier)
            .clip(RoundedCornerShape(9.dp)).background(if (on) Color.White else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

/**
 * نافذة مربع النص: الخط، الحجم، النمط، اللون. كل تغيير يُحفظ للمربعات الجديدة
 * ويُطبَّق فوراً على المربع الذي تكتب فيه (أو المحدد).
 */
@Composable
internal fun TextToolWindow(s: DocSession, onTurnOff: () -> Unit) {
    var fontPicker by remember { mutableStateOf(false) }
    var colorPicker by remember { mutableStateOf(false) }
    val target = s.inlineEditId ?: s.selectedTextId
    fun apply() { target?.let { id -> s.updateTextBox(id) { TextPrefs.applyTo(it) } } }
    val c = AppController
    val pen = if (c.tool.isPen()) c.tool else c.lastPen
    val col = TextPrefs.color?.let { Color(it) } ?: c.colorFor(pen)
    Column(Modifier.width(320.dp).background(SheetBg).padding(12.dp)) {
        ToolWindowHeader(R.drawable.ph_text, L("مربع النص", "Text box"))
        Spacer(Modifier.height(8.dp))
        // معاينة
        SettingsCard {
            val ready = TextPrefs.font.isEmpty() || FontStore.state[TextPrefs.font] == true || FontStore.isReady(TextPrefs.font)
            if (!ready) LaunchedEffect(TextPrefs.font) { FontStore.ensure(TextPrefs.font) }
            Text(
                L("الميتوكوندريا", "Mitochondria"),
                fontFamily = if (ready) FontStore.family(TextPrefs.font) else null,
                fontSize = (TextPrefs.sizePt * 1.6f).coerceIn(14f, 40f).sp,
                color = col, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (TextPrefs.bold) FontWeight.Bold else null,
                fontStyle = if (TextPrefs.italic) FontStyle.Italic else null,
                textDecoration = if (TextPrefs.underline) TextDecoration.Underline else null,
                textAlign = when (TextPrefs.align) { 1 -> TextAlign.Center; 2 -> TextAlign.End; else -> TextAlign.Start },
                modifier = Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFFBF2))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        SettingsCard {
            NavRow(L("الخط", "Font"), onClick = { fontPicker = true }) {
                Text(
                    TextPrefs.font.ifEmpty { L("الافتراضي", "Default") },
                    fontFamily = FontStore.family(TextPrefs.font), fontSize = 17.sp, color = Palette.Navy,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            RowDividerLine()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(L("الحجم", "Size"), fontSize = 16.sp, color = Palette.TextPrimary, modifier = Modifier.weight(1f))
                RoundButton("−") { TextPrefs.changeSize(TextPrefs.sizePt - 1); apply() }
                Text(
                    "${TextPrefs.sizePt}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Palette.Navy,
                    textAlign = TextAlign.Center, modifier = Modifier.width(44.dp)
                )
                RoundButton("+") { TextPrefs.changeSize(TextPrefs.sizePt + 1); apply() }
            }
        }
        CardTitle(L("النمط", "Style"))
        SettingsCard {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp).height(42.dp)
                    .clip(RoundedCornerShape(11.dp)).background(Color(0xFFEEF0F3)).padding(2.dp)
            ) {
                StyleToggle(TextPrefs.bold, Modifier.weight(1f), { TextPrefs.changeBold(!TextPrefs.bold); apply() }) {
                    Text("B", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = if (TextPrefs.bold) Palette.Navy else Muted)
                }
                StyleToggle(TextPrefs.italic, Modifier.weight(1f), { TextPrefs.changeItalic(!TextPrefs.italic); apply() }) {
                    Text("I", fontStyle = FontStyle.Italic, fontSize = 17.sp, color = if (TextPrefs.italic) Palette.Navy else Muted)
                }
                StyleToggle(TextPrefs.underline, Modifier.weight(1f), { TextPrefs.changeUnderline(!TextPrefs.underline); apply() }) {
                    Text("U", textDecoration = TextDecoration.Underline, fontSize = 17.sp, color = if (TextPrefs.underline) Palette.Navy else Muted)
                }
                // المحاذاة: نقرة تنتقل بين البداية والوسط والنهاية
                StyleToggle(TextPrefs.align != 0, Modifier.weight(1f), { TextPrefs.changeAlign((TextPrefs.align + 1) % 3); apply() }) {
                    val icon = when (TextPrefs.align) { 1 -> "☰"; 2 -> if (I18n.english) "⇥" else "⇤"; else -> if (I18n.english) "⇤" else "⇥" }
                    Text(icon, fontSize = 17.sp, color = if (TextPrefs.align != 0) Palette.Navy else Muted)
                }
            }
            Text(
                when (TextPrefs.align) {
                    1 -> L("المحاذاة: الوسط", "Alignment: center")
                    2 -> L("المحاذاة: النهاية", "Alignment: end")
                    else -> L("المحاذاة: البداية", "Alignment: start")
                },
                fontSize = 12.sp, color = Muted, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            )
        }
        CardTitle(L("اللون", "Color"))
        SettingsCard {
            Spacer(Modifier.height(8.dp))
            SwatchGrid(c.penPalette + c.customColors.toList(), col, { TextPrefs.changeColor(it); apply() }, { colorPicker = true })
        }
        Spacer(Modifier.height(10.dp))
        SettingsCard {
            Text(
                L("إيقاف أداة النص", "Turn off text tool"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.Navy,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onTurnOff).padding(vertical = 13.dp)
            )
        }
    }
    if (fontPicker) {
        FontPickerSheet(TextPrefs.font, if (I18n.english) HwLang.ENGLISH else HwLang.ARABIC, onPick = {
            TextPrefs.changeFont(it)
            apply()
        }, onDismiss = { fontPicker = false })
    }
    if (colorPicker) {
        ColorPickerDialog(col, onPick = { v ->
            c.addCustomColor(v)
            TextPrefs.changeColor(v)
            apply()
            colorPicker = false
        }, onDismiss = { colorPicker = false })
    }
}
