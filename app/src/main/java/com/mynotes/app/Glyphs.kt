package com.mynotes.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * الأيقونات: مجموعة Phosphor (رخصة MIT، انظر licenses/) محوّلة إلى Vector Drawables في res/drawable/ph_*.xml.
 * كل أيقونة لها نسخة عادية (خط) ونسخة ممتلئة للأدوات المختارة.
 */

@Composable
fun PhIcon(id: Int, tint: Color, size: Dp = 22.dp, description: String? = null, modifier: Modifier = Modifier) {
    Icon(painterResource(id), description, modifier.size(size), tint = tint)
}

fun toolIcon(tool: Tool, filled: Boolean): Int = when (tool) {
    Tool.BALLPOINT -> if (filled) R.drawable.ph_pen_fill else R.drawable.ph_pen
    Tool.FOUNTAIN -> if (filled) R.drawable.ph_pen_nib_fill else R.drawable.ph_pen_nib
    Tool.PENCIL -> if (filled) R.drawable.ph_pencil_fill else R.drawable.ph_pencil
    Tool.HIGHLIGHTER -> if (filled) R.drawable.ph_highlighter_fill else R.drawable.ph_highlighter
    Tool.ERASER -> if (filled) R.drawable.ph_eraser_fill else R.drawable.ph_eraser
    Tool.LASER -> if (filled) R.drawable.ph_laser_fill else R.drawable.ph_laser
}

/** النسخة الممتلئة من أيقونة إن وُجدت. */
fun filledOf(id: Int): Int = when (id) {
    R.drawable.ph_recent -> R.drawable.ph_recent_fill
    R.drawable.ph_books -> R.drawable.ph_books_fill
    R.drawable.ph_star -> R.drawable.ph_star_fill
    R.drawable.ph_house -> R.drawable.ph_house_fill
    R.drawable.ph_pages -> R.drawable.ph_pages_fill
    R.drawable.ph_summary -> R.drawable.ph_summary_fill
    R.drawable.ph_timer -> R.drawable.ph_timer_fill
    else -> id
}

/** أيقونة الأداة، مع نقطة صغيرة بلون الحبر في الزاوية (accent). المختارة تظهر ممتلئة. */
@Composable
fun ToolGlyph(
    tool: Tool,
    tint: Color,
    accent: Color = tint,
    modifier: Modifier = Modifier.size(26.dp),
    filled: Boolean = false
) {
    Box(modifier) {
        PhIcon(toolIcon(tool, filled), tint, 22.dp, modifier = Modifier.align(Alignment.Center))
        if (tool != Tool.ERASER && accent != tint) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(7.dp).clip(CircleShape).background(Color.White)
            ) {
                Box(Modifier.align(Alignment.Center).size(5.dp).clip(CircleShape).background(accent))
            }
        }
    }
}

@Composable fun ImageGlyph(tint: Color) = PhIcon(R.drawable.ph_image, tint)
@Composable fun SearchGlyph(tint: Color) = PhIcon(R.drawable.ph_search, tint)
@Composable fun UndoGlyph(tint: Color, redo: Boolean = false) =
    PhIcon(if (redo) R.drawable.ph_redo else R.drawable.ph_undo, tint)
@Composable fun ZoomGlyph(tint: Color, plus: Boolean) =
    PhIcon(if (plus) R.drawable.ph_zoom_in else R.drawable.ph_zoom_out, tint)
@Composable fun PagesGlyph(tint: Color, filled: Boolean = false) =
    PhIcon(if (filled) R.drawable.ph_pages_fill else R.drawable.ph_pages, tint)
@Composable fun OcrGlyph(tint: Color) = PhIcon(R.drawable.ph_scan, tint)
@Composable fun ChevronGlyph(tint: Color, up: Boolean) =
    PhIcon(if (up) R.drawable.ph_caret_up else R.drawable.ph_caret_down, tint, 20.dp)
@Composable fun AddPageGlyph(tint: Color) = PhIcon(R.drawable.ph_add_page, tint)
@Composable fun TextGlyph(tint: Color) = PhIcon(R.drawable.ph_text, tint)
@Composable fun ExportGlyph(tint: Color) = PhIcon(R.drawable.ph_export, tint)
@Composable fun FitGlyph(tint: Color) = PhIcon(R.drawable.ph_fit, tint)
@Composable fun TimerGlyph(tint: Color, size: Dp = 16.dp) = PhIcon(R.drawable.ph_timer, tint, size)

/** التقاط النص: أيقونة حرف مسطّر، والخط تحته بلون الهايلايتر. */
@Composable
fun SnapGlyph(tint: Color, mark: Color) {
    Box(Modifier.size(26.dp)) {
        Box(
            Modifier.align(Alignment.BottomCenter).size(width = 18.dp, height = 5.dp)
                .clip(CircleShape).background(mark.copy(alpha = 0.85f))
        )
        PhIcon(R.drawable.ph_snap, tint, 22.dp, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
fun SummaryGlyph(tint: Color, @Suppress("UNUSED_PARAMETER") mark: Color, filled: Boolean = false) =
    PhIcon(if (filled) R.drawable.ph_summary_fill else R.drawable.ph_summary, tint)

/** نجمة المفضلة: ممتلئة أو إطار فقط. */
@Composable
fun StarGlyph(filled: Boolean, tint: Color, modifier: Modifier = Modifier.size(18.dp)) {
    Icon(
        painterResource(if (filled) R.drawable.ph_star_fill else R.drawable.ph_star),
        null, modifier, tint = tint
    )
}

/** رسم الحالة الفارغة: أوراق مكدسة مع شريط علامة ذهبي. */
@Composable
fun EmptyShelfArt(modifier: Modifier = Modifier.size(width = 150.dp, height = 120.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val pw = w * 0.52f
        val ph = h * 0.86f
        val x0 = (w - pw) / 2f
        val y0 = h * 0.06f
        val r = CornerRadius(w * 0.02f)
        val line = Color(0xFFE4DFD3)
        // ورقتان خلفيتان مائلتان قليلًا
        rotate(-8f, Offset(x0 + pw / 2f, y0 + ph)) {
            drawRoundRect(Color(0xFFF1ECE1), Offset(x0, y0), Size(pw, ph), r)
        }
        rotate(5f, Offset(x0 + pw / 2f, y0 + ph)) {
            drawRoundRect(Color(0xFFF7F2E8), Offset(x0, y0), Size(pw, ph), r)
            drawRoundRect(line, Offset(x0, y0), Size(pw, ph), r, style = Stroke(width = w * 0.006f))
        }
        // الورقة الأمامية
        drawRoundRect(Color(0xFFFFFBF2), Offset(x0, y0), Size(pw, ph), r)
        drawRoundRect(line, Offset(x0, y0), Size(pw, ph), r, style = Stroke(width = w * 0.006f))
        var ly = y0 + ph * 0.22f
        repeat(5) { i ->
            val lw = if (i == 4) pw * 0.45f else pw * 0.72f
            drawLine(
                Color(0xFFD6DCE6), Offset(x0 + pw * 0.14f, ly), Offset(x0 + pw * 0.14f + lw, ly),
                strokeWidth = w * 0.012f, cap = StrokeCap.Round
            )
            ly += ph * 0.13f
        }
        // شريط علامة ذهبي
        val bx = x0 + pw * 0.74f
        val bw = pw * 0.12f
        val ribbon = Path().apply {
            moveTo(bx, y0 - h * 0.03f)
            lineTo(bx + bw, y0 - h * 0.03f)
            lineTo(bx + bw, y0 + ph * 0.36f)
            lineTo(bx + bw / 2f, y0 + ph * 0.30f)
            lineTo(bx, y0 + ph * 0.36f)
            close()
        }
        drawPath(ribbon, Color(0xFFC9A44C))
    }
}
