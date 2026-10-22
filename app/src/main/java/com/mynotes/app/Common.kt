package com.mynotes.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier.clip(shape)
            .background(if (selected) Palette.NavySoft else Color.White)
            .border(1.dp, if (selected) Palette.Navy else Palette.Divider, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            fontSize = 14.sp,
            color = if (selected) Palette.Navy else Palette.TextPrimary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** صف ألوان الورقة. selected و الألوان بصيغة ARGB. */
@Composable
fun PaperColorRow(selected: Int, onSelect: (Int) -> Unit, onCustom: () -> Unit) {
    val presets = Templates.paperColors.map { it.first }
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val all = if (selected in presets) presets else presets + selected
        all.forEach { argb ->
            val sel = argb == selected
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color(argb))
                    .border(if (sel) 3.dp else 1.dp, if (sel) Palette.Navy else Palette.Divider, CircleShape)
                    .clickable { onSelect(argb) }
            )
        }
        Box(
            Modifier.size(36.dp).clip(CircleShape)
                .border(1.dp, Palette.Divider, CircleShape)
                .clickable(onClick = onCustom),
            contentAlignment = Alignment.Center
        ) {
            Text("+", fontSize = 20.sp, color = Palette.TextSecondary)
        }
    }
}
