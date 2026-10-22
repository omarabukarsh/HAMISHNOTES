package com.mynotes.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Navy = Color(0xFF1B365D)
    val NavySoft = Color(0xFFE6ECF5)
    val Background = Color(0xFFF3F5F8)
    val Divider = Color(0xFFE2E6EC)
    val TextPrimary = Color(0xFF1C2430)
    val TextSecondary = Color(0xFF66707F)
    val Danger = Color(0xFFB3261E)
    val Gold = Color(0xFFE0A100)
    val ReaderBackground = Color(0xFFD9DEE5)
    val TabBar = Color(0xFFE4E9F0)
    val Desk = Color(0xFFE9E4DA)
    val DrawerBg = Color(0xFFF7F4EE)

    /** سطح عاجي للقراءة والكتابة (صفحات الملخصات والملاحظات الجديدة). */
    val Ivory = Color(0xFFFFFBF0)
    val GoldSoft = Color(0xFFF6E7B8)
    val GoldDeep = Color(0xFFB8860B)
}

private val AppColors = lightColorScheme(
    primary = Palette.Navy,
    onPrimary = Color.White,
    primaryContainer = Palette.NavySoft,
    onPrimaryContainer = Palette.Navy,
    background = Palette.Background,
    onBackground = Palette.TextPrimary,
    surface = Color.White,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.NavySoft,
    onSurfaceVariant = Palette.TextSecondary,
    outline = Palette.Divider,
    error = Palette.Danger
)

@Composable
fun MyNotesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, content = content)
}
