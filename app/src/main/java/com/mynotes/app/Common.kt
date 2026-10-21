package com.mynotes.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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

/** أيقونة صفحة عليها علامة +. */
@Composable
fun AddPageGlyph(tint: Color) {
    Canvas(Modifier.size(22.dp)) {
        val s = size.minDimension
        drawRoundRect(
            color = tint,
            topLeft = Offset(s * 0.18f, s * 0.08f),
            size = Size(s * 0.64f, s * 0.84f),
            cornerRadius = CornerRadius(s * 0.08f),
            style = Stroke(width = s * 0.08f)
        )
        drawLine(
            tint, Offset(s * 0.5f, s * 0.34f), Offset(s * 0.5f, s * 0.66f),
            strokeWidth = s * 0.08f, cap = StrokeCap.Round
        )
        drawLine(
            tint, Offset(s * 0.34f, s * 0.5f), Offset(s * 0.66f, s * 0.5f),
            strokeWidth = s * 0.08f, cap = StrokeCap.Round
        )
    }
}
