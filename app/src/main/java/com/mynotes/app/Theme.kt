package com.mynotes.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * هوية MyNotes: مجلد دراسة فاخر. كحلي داكن مثل جلد الكعب، ذهبي كختم رقيق، وورق عاجي.
 * الذهبي للخطوط الرفيعة والتحديد فقط، لا للمساحات الكبيرة.
 */
object Palette {
    // الكحلي
    val Navy = Color(0xFF1B365D)
    /** كعب المجلد: الشريط الجانبي وشريط التبويبات. */
    val Ink = Color(0xFF13233B)
    val InkRaised = Color(0xFF1E3250)
    val InkLine = Color(0xFF2A4064)
    val NavySoft = Color(0xFFE8EDF5)

    // الذهبي
    val Gold = Color(0xFFC9A44C)
    val GoldDeep = Color(0xFF8C6A1F)
    val GoldSoft = Color(0xFFF4E9CB)

    // الورق والأسطح
    val Background = Color(0xFFF6F4EF)
    val Ivory = Color(0xFFFFFBF2)
    val DrawerBg = Color(0xFFF9F6EF)
    val Divider = Color(0xFFE4DFD3)

    /** مكتب القراءة: رمادي أزرق هادئ يُظهر بياض الصفحات. */
    val Desk = Color(0xFFDCE1E8)
    val ReaderBackground = Color(0xFFD9DEE5)
    val TabBar = Ink

    // النص
    val TextPrimary = Color(0xFF1A2230)
    val TextSecondary = Color(0xFF667085)
    val OnInk = Color(0xFFEDE6D4)
    val OnInkMuted = Color(0xFF94A2B8)

    val Danger = Color(0xFFB3261E)
}

/** الخطوط: Plex Sans Arabic للواجهة، وAmiri (نسخ) للعناوين وأسماء المستندات. */
object Fonts {
    val Sans = FontFamily(
        Font(R.font.plex_regular, FontWeight.Normal),
        Font(R.font.plex_medium, FontWeight.Medium),
        Font(R.font.plex_semibold, FontWeight.SemiBold),
        Font(R.font.plex_bold, FontWeight.Bold)
    )
    val Serif = FontFamily(
        Font(R.font.amiri_regular, FontWeight.Normal),
        Font(R.font.amiri_bold, FontWeight.Bold)
    )
}

private val AppColors = lightColorScheme(
    primary = Palette.Navy,
    onPrimary = Color.White,
    primaryContainer = Palette.NavySoft,
    onPrimaryContainer = Palette.Navy,
    secondary = Palette.GoldDeep,
    onSecondary = Color.White,
    secondaryContainer = Palette.GoldSoft,
    onSecondaryContainer = Palette.GoldDeep,
    background = Palette.Background,
    onBackground = Palette.TextPrimary,
    surface = Color.White,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.NavySoft,
    onSurfaceVariant = Palette.TextSecondary,
    surfaceContainer = Palette.Ivory,
    surfaceContainerHigh = Palette.Ivory,
    surfaceContainerHighest = Palette.Ivory,
    surfaceContainerLow = Palette.Ivory,
    outline = Palette.Divider,
    outlineVariant = Palette.Divider,
    error = Palette.Danger
)

private fun sans(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Fonts.Sans, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

private fun serif(size: Int, line: Int, weight: FontWeight = FontWeight.Bold) =
    TextStyle(fontFamily = Fonts.Serif, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

/** مقياس الخطوط: العربية تحتاج ارتفاع سطر أكبر من اللاتينية. */
private val AppType = Typography(
    displayLarge = serif(48, 64),
    displayMedium = serif(40, 54),
    displaySmall = serif(34, 46),
    headlineLarge = serif(30, 42),
    headlineMedium = serif(26, 36),
    headlineSmall = serif(22, 32),
    titleLarge = sans(20, 30, FontWeight.SemiBold),
    titleMedium = sans(16, 26, FontWeight.SemiBold),
    titleSmall = sans(14, 22, FontWeight.Medium),
    bodyLarge = sans(16, 26),
    bodyMedium = sans(14, 22),
    bodySmall = sans(12, 19),
    labelLarge = sans(14, 20, FontWeight.Medium),
    labelMedium = sans(12, 18, FontWeight.Medium),
    labelSmall = sans(11, 16, FontWeight.Medium)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun MyNotesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, typography = AppType, shapes = AppShapes, content = content)
}
