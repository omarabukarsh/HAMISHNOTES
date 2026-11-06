package com.hamishnotes.app

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * نوافذ الأدوات بتصميم البطاقات (مثل نافذة تجميل خط اليد).
 */

/** عنوان نافذة أداة: أيقونة في مربع كحلي واسم الأداة. */
@Composable
internal fun ToolWindowHeader(icon: Int, title: String) {
    Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Palette.Navy), contentAlignment = Alignment.Center) {
            PhIcon(icon, Color.White, 18.dp)
        }
        Spacer(Modifier.width(10.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
    }
}

/** دوائر الألوان مع زر «+» للون مخصص. */
@Composable
internal fun SwatchGrid(colors: List<Color>, current: Color, onPick: (Color) -> Unit, onCustom: (() -> Unit)?) {
    val all: List<Color?> = colors + if (onCustom != null) listOf(null) else emptyList()
    Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
        all.chunked(6).forEach { row ->
            Row {
                row.forEach { col ->
                    Box(Modifier.size(44.dp).clip(CircleShape).clickable { if (col == null) onCustom?.invoke() else onPick(col) }, contentAlignment = Alignment.Center) {
                        if (col == null) {
                            Box(Modifier.size(30.dp).clip(CircleShape).border(1.5.dp, Color(0xFFB9BFCA), CircleShape), contentAlignment = Alignment.Center) {
                                Text("+", fontSize = 18.sp, color = Muted)
                            }
                        } else {
                            val sel = col == current
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).then(if (sel) Modifier.border(2.5.dp, Palette.Navy, CircleShape) else Modifier),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(Modifier.size(if (sel) 27.dp else 30.dp).clip(CircleShape).background(col).border(1.dp, Color(0x14000000), CircleShape))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** شريط حجم من 10 درجات مع قيمته. */
@Composable
internal fun SizeSliderRow(title: String, index: Int, valueText: String, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row {
            Text(title, fontSize = 15.sp, color = Palette.TextPrimary, modifier = Modifier.weight(1f))
            Text(valueText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.Navy)
        }
        Slider(
            value = index.toFloat(), onValueChange = { onChange(kotlin.math.round(it).toInt().coerceIn(0, Sizes.COUNT - 1)) },
            valueRange = 0f..(Sizes.COUNT - 1).toFloat(), steps = Sizes.COUNT - 2,
            colors = SliderDefaults.colors(
                thumbColor = Color.White, activeTrackColor = Palette.Navy, inactiveTrackColor = Color(0xFFE3E6EB),
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent
            ),
            modifier = Modifier.height(34.dp)
        )
    }
}

/** نافذة مؤشر الليزر: نقطة أو أثر مع معاينة، اللون، الحجم. */
@Composable
internal fun LaserWindow(onCustomColor: () -> Unit) {
    val c = AppController
    val col = c.colorFor(Tool.LASER)
    Column(Modifier.width(320.dp).background(SheetBg).padding(12.dp)) {
        ToolWindowHeader(R.drawable.ph_laser, L("مؤشر الليزر", "Laser pointer"))
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            Segmented(listOf(LaserMode.DOT to L("● نقطة", "● Dot"), LaserMode.LINE to L("〰 أثر", "〰 Trail")), c.laserMode) { c.updateLaserMode(it) }
            // معاينة
            val trail = c.laserMode == LaserMode.LINE
            Canvas(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp).height(44.dp)
                    .clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFFBF2))
            ) {
                val w = size.width
                val h = size.height
                val end = Offset(w * 0.86f, h * 0.42f)
                if (trail) {
                    val p = Path().apply {
                        moveTo(w * 0.08f, h * 0.7f)
                        cubicTo(w * 0.3f, h * 0.1f, w * 0.55f, h * 0.95f, end.x, end.y)
                    }
                    drawPath(p, col.copy(alpha = 0.25f), style = Stroke(width = 10f, cap = StrokeCap.Round))
                    drawPath(p, col, style = Stroke(width = 4f, cap = StrokeCap.Round))
                }
                val d = Offset(if (trail) end.x else w / 2f, if (trail) end.y else h / 2f)
                drawCircle(col.copy(alpha = 0.25f), h * 0.32f, d)
                drawCircle(col, h * 0.15f, d)
            }
        }
        CardTitle(L("اللون", "Color"))
        SettingsCard {
            Spacer(Modifier.height(8.dp))
            SwatchGrid(c.paletteFor(Tool.LASER) + c.customColors.toList(), col, { c.setColor(it) }, onCustomColor)
        }
        Spacer(Modifier.height(10.dp))
        SettingsCard {
            val i = c.sizeFor(Tool.LASER)
            SizeSliderRow(L("الحجم", "Size"), i, PenPrefs.mm(Sizes.width(Tool.LASER, i))) { c.setSize(it) }
        }
    }
}
