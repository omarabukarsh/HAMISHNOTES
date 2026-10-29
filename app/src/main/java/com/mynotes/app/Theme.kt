package com.mynotes.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * الثيم المختار. NAVY = كحلي فاخر (افتراضي)، LIGHT = فاتح كلاسيكي بأشرطة رمادية فاتحة.
 * الأيقونات والخطوط والذهبي نفسها في الثيمين؛ تتغير ألوان الأشرطة والخلفيات فقط.
 */
enum class AppTheme { NAVY, LIGHT }

object ThemeState {
    var theme by mutableStateOf(AppTheme.NAVY)
    val light: Boolean get() = theme == AppTheme.LIGHT
}

/**
 * هوية MyNotes: مجلد دراسة فاخر. كحلي داكن مثل جلد الكعب، ذهبي كختم رقيق، وورق عاجي.
 * الذهبي للخطوط الرفيعة والتحديد فقط، لا للمساحات الكبيرة.
 */
object Palette {
    // الكحلي
    val Navy = Color(0xFF1B365D)
    /** كعب المجلد: شريط التبويبات (وفي الكحلي الشريط الجانبي أيضًا). */
    val Ink: Color get() = if (ThemeState.light) Color(0xFFE4E9F0) else Color(0xFF000000)
    /** الشريط الجانبي للمكتبة. */
    val Spine: Color get() = if (ThemeState.light) Color.White else Color(0xFF0A0A0A)
    val InkRaised: Color get() = if (ThemeState.light) Color(0xFFE8EDF5) else Color(0xFF1C1C1C)
    val InkLine: Color get() = if (ThemeState.light) Color(0xFFE2E6EC) else Color(0xFF262626)
    val NavySoft = Color(0xFFE8EDF5)

    // الذهبي
    val Gold = Color(0xFFC9A44C)
    val GoldDeep = Color(0xFF8C6A1F)
    val GoldSoft = Color(0xFFF4E9CB)

    // الورق والأسطح
    val Background: Color get() = if (ThemeState.light) Color(0xFFF3F5F8) else Color(0xFFF6F4EF)
    val Ivory = Color(0xFFFFFBF2)
    val DrawerBg = Color(0xFFF9F6EF)
    val Divider = Color(0xFFE4DFD3)

    /** مكتب القراءة: رمادي أزرق هادئ يُظهر بياض الصفحات. */
    val Desk: Color get() = if (ThemeState.light) Color(0xFFEBEEF2) else Color(0xFFDCE1E8)
    val ReaderBackground = Color(0xFFD9DEE5)
    val TabBar: Color get() = Ink
    /** خلفية شريط التبويبات: أغمق من الشريط الجانبي للمكتبة كي يظهر التبويب النشط متصلًا بما تحته. */
    val TabRail: Color get() = if (ThemeState.light) Color(0xFFD3DAE4) else Color(0xFF000000)
    /** التبويبات غير النشطة: لون ثالث واضح بين الشريط والتبويب النشط. */
    val TabIdle: Color get() = if (ThemeState.light) Color(0xFFB9C4D3) else Color(0xFF1F1F1F)

    // النص
    val TextPrimary = Color(0xFF1A2230)
    val TextSecondary = Color(0xFF667085)
    val OnInk: Color get() = if (ThemeState.light) Color(0xFF1A2230) else Color(0xFFF2F2F2)
    val OnInkMuted: Color get() = if (ThemeState.light) Color(0xFF667085) else Color(0xFF9A9A9A)

    val Danger = Color(0xFFB3261E)
}

/**
 * ألوان المكتبة: فاتح كريمي دافئ، أو داكن أسود تمامًا (يوفّر البطارية في شاشات OLED).
 */
object Lib {
    private val L get() = ThemeState.light
    val bg: Color get() = if (L) Color(0xFFFAF4E6) else Color(0xFF000000)
    val side: Color get() = if (L) Color(0xFFFFFEFB) else Color(0xFF0A0A0A)
    val card: Color get() = if (L) Color.White else Color(0xFF141414)
    val text: Color get() = if (L) Color(0xFF1E2A44) else Color(0xFFF2F2F2)
    val muted: Color get() = if (L) Color(0xFF6B7280) else Color(0xFF9A9A9A)
    val line: Color get() = if (L) Color(0xFFEADFC8) else Color(0xFF262626)
    val accent = Color(0xFFF4C95D)
    val accentDeep = Color(0xFFB8860B)
    val accentSoft: Color get() = if (L) Color(0xFFFCEBC2) else Color(0xFF2B2410)
    val resume: Color get() = if (L) Color(0xFFFCEBBE) else Color(0xFF17140A)
    /** الزر الرئيسي الداكن (في الوضع الداكن يصبح فاتحًا). */
    val strong: Color get() = if (L) Color(0xFF1E2A44) else Color(0xFFF2F2F2)
    val onStrong: Color get() = if (L) Color.White else Color(0xFF000000)
    val exam = Color(0xFFB0413E)
    /** أيقونات القائمة الجانبية الملوّنة (الكتب والنجمة). */
    val iconBlue: Color get() = if (L) Color(0xFF2F4B7C) else Color(0xFF8FA8D6)
    val study: Color get() = if (L) Color(0xFFEEF2F9) else Color(0xFF10141C)
    /** لون غلاف الدفتر الافتراضي. */
    val cover = Color(0xFF2F4B7C)
}

/**
 * الخط: Almarai لكل التطبيق (عربي ولاتيني بتصميم واحد).
 * Display = نفس العائلة بوزن أثقل للعناوين وأسماء المستندات.
 */
object Fonts {
    val Sans = FontFamily(
        Font(R.font.almarai_light, FontWeight.Light),
        Font(R.font.almarai_regular, FontWeight.Normal),
        Font(R.font.almarai_bold, FontWeight.Bold),
        Font(R.font.almarai_extrabold, FontWeight.ExtraBold)
    )
    /** خط يدوي للنصوص الصغيرة المائلة (Caveat). */
    val Script = FontFamily(Font(R.font.caveat_medium, FontWeight.Medium))
    val Display = FontFamily(
        Font(R.font.almarai_bold, FontWeight.Normal),
        Font(R.font.almarai_extrabold, FontWeight.Bold)
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
    TextStyle(fontFamily = Fonts.Display, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

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
    val theme = ThemeState.theme
    val scheme = remember(theme) { AppColors.copy(background = Palette.Background) }
    MaterialTheme(colorScheme = scheme, typography = AppType, shapes = AppShapes, content = content)
}
