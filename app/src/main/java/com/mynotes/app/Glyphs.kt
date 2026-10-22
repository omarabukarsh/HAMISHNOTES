package com.mynotes.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** مضلع مغلق من إحداثيات نسبية (x1,y1,x2,y2,...) مضروبة في المقياس s. */
private fun poly(s: Float, vararg v: Float): Path {
    val p = Path()
    p.moveTo(v[0] * s, v[1] * s)
    var i = 2
    while (i + 1 < v.size) {
        p.lineTo(v[i] * s, v[i + 1] * s)
        i += 2
    }
    p.close()
    return p
}

/** أيقونات الأدوات. accent هو لون الحبر/السنّ الظاهر في الأيقونة. */
@Composable
fun ToolGlyph(tool: Tool, tint: Color, accent: Color = tint, modifier: Modifier = Modifier.size(26.dp)) {
    Canvas(modifier) {
        val s = size.minDimension
        val sw = s * 0.07f
        val soft = tint.copy(alpha = 0.16f)
        val c = Offset(s / 2f, s / 2f)
        when (tool) {
            Tool.BALLPOINT -> rotate(45f, c) {
                val r = CornerRadius(s * 0.07f)
                drawRoundRect(soft, Offset(s * 0.39f, s * 0.10f), Size(s * 0.22f, s * 0.50f), r)
                drawRoundRect(
                    tint, Offset(s * 0.39f, s * 0.10f), Size(s * 0.22f, s * 0.50f), r,
                    style = Stroke(width = sw)
                )
                drawRoundRect(
                    tint, Offset(s * 0.45f, s * 0.03f), Size(s * 0.10f, s * 0.07f),
                    CornerRadius(s * 0.03f)
                )
                drawPath(poly(s, 0.39f, 0.60f, 0.61f, 0.60f, 0.55f, 0.70f, 0.45f, 0.70f), tint.copy(alpha = 0.5f))
                drawPath(poly(s, 0.45f, 0.70f, 0.55f, 0.70f, 0.50f, 0.93f), accent)
                drawLine(
                    tint, Offset(s * 0.64f, s * 0.16f), Offset(s * 0.64f, s * 0.38f),
                    strokeWidth = sw * 0.8f, cap = StrokeCap.Round
                )
            }
            Tool.FOUNTAIN -> rotate(45f, c) {
                val r = CornerRadius(s * 0.08f)
                drawRoundRect(soft, Offset(s * 0.38f, s * 0.06f), Size(s * 0.24f, s * 0.44f), r)
                drawRoundRect(
                    tint, Offset(s * 0.38f, s * 0.06f), Size(s * 0.24f, s * 0.44f), r,
                    style = Stroke(width = sw)
                )
                drawLine(
                    tint, Offset(s * 0.38f, s * 0.50f), Offset(s * 0.62f, s * 0.50f),
                    strokeWidth = sw
                )
                drawPath(poly(s, 0.40f, 0.52f, 0.60f, 0.52f, 0.56f, 0.76f, 0.50f, 0.93f, 0.44f, 0.76f), accent)
                drawLine(
                    Color.White, Offset(s * 0.50f, s * 0.68f), Offset(s * 0.50f, s * 0.90f),
                    strokeWidth = sw * 0.55f
                )
                drawCircle(Color.White, sw * 0.65f, Offset(s * 0.50f, s * 0.64f))
            }
            Tool.PENCIL -> rotate(45f, c) {
                drawRect(soft, Offset(s * 0.40f, s * 0.14f), Size(s * 0.20f, s * 0.50f))
                drawRect(
                    tint, Offset(s * 0.40f, s * 0.14f), Size(s * 0.20f, s * 0.50f),
                    style = Stroke(width = sw)
                )
                drawRoundRect(
                    tint.copy(alpha = 0.55f), Offset(s * 0.40f, s * 0.04f), Size(s * 0.20f, s * 0.10f),
                    CornerRadius(s * 0.03f)
                )
                drawPath(poly(s, 0.40f, 0.64f, 0.60f, 0.64f, 0.50f, 0.93f), soft)
                drawPath(poly(s, 0.40f, 0.64f, 0.60f, 0.64f, 0.50f, 0.93f), tint, style = Stroke(width = sw))
                drawPath(poly(s, 0.465f, 0.82f, 0.535f, 0.82f, 0.50f, 0.93f), accent)
            }
            Tool.HIGHLIGHTER -> {
                rotate(45f, c) {
                    val r = CornerRadius(s * 0.07f)
                    drawRoundRect(soft, Offset(s * 0.36f, s * 0.04f), Size(s * 0.28f, s * 0.48f), r)
                    drawRoundRect(
                        tint, Offset(s * 0.36f, s * 0.04f), Size(s * 0.28f, s * 0.48f), r,
                        style = Stroke(width = sw)
                    )
                    drawLine(
                        tint, Offset(s * 0.36f, s * 0.18f), Offset(s * 0.64f, s * 0.18f),
                        strokeWidth = sw * 0.8f
                    )
                    drawPath(poly(s, 0.40f, 0.52f, 0.60f, 0.52f, 0.57f, 0.80f, 0.43f, 0.80f), accent.copy(alpha = 0.9f))
                }
                drawLine(
                    accent.copy(alpha = 0.5f), Offset(s * 0.10f, s * 0.92f), Offset(s * 0.58f, s * 0.92f),
                    strokeWidth = sw * 2.2f, cap = StrokeCap.Butt
                )
            }
            Tool.ERASER -> rotate(-35f, c) {
                val r = CornerRadius(s * 0.12f)
                drawRoundRect(
                    soft, Offset(s * 0.12f, s * 0.30f), Size(s * 0.76f, s * 0.40f), r
                )
                drawRoundRect(
                    accent.copy(alpha = 0.35f), Offset(s * 0.12f, s * 0.30f), Size(s * 0.34f, s * 0.40f), r
                )
                drawRoundRect(
                    tint, Offset(s * 0.12f, s * 0.30f), Size(s * 0.76f, s * 0.40f), r,
                    style = Stroke(width = sw)
                )
                drawLine(
                    tint, Offset(s * 0.46f, s * 0.30f), Offset(s * 0.46f, s * 0.70f),
                    strokeWidth = sw
                )
            }
            Tool.LASER -> {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.55f), Color.Transparent),
                        center = c, radius = s * 0.5f
                    ),
                    radius = s * 0.5f, center = c
                )
                drawCircle(accent, s * 0.16f, c)
                drawCircle(Color.White, s * 0.07f, c)
                for (i in 0 until 8) {
                    val a = (i * 45f) * (PI.toFloat() / 180f)
                    val dx = cos(a)
                    val dy = sin(a)
                    drawLine(
                        tint,
                        Offset(c.x + dx * s * 0.28f, c.y + dy * s * 0.28f),
                        Offset(c.x + dx * s * 0.42f, c.y + dy * s * 0.42f),
                        strokeWidth = sw * 0.8f, cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
fun ImageGlyph(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val s = size.minDimension
        drawRoundRect(
            tint.copy(alpha = 0.14f), Offset(s * 0.10f, s * 0.18f), Size(s * 0.80f, s * 0.64f),
            CornerRadius(s * 0.10f)
        )
        drawRoundRect(
            tint, Offset(s * 0.10f, s * 0.18f), Size(s * 0.80f, s * 0.64f), CornerRadius(s * 0.10f),
            style = Stroke(width = s * 0.07f)
        )
        drawCircle(tint, s * 0.07f, Offset(s * 0.34f, s * 0.40f))
        drawPath(poly(s, 0.16f, 0.74f, 0.42f, 0.50f, 0.58f, 0.64f, 0.70f, 0.54f, 0.84f, 0.74f), tint)
    }
}

@Composable
fun SearchGlyph(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val s = size.minDimension
        drawCircle(tint, s * 0.26f, Offset(s * 0.42f, s * 0.42f), style = Stroke(width = s * 0.09f))
        drawLine(
            tint, Offset(s * 0.62f, s * 0.62f), Offset(s * 0.86f, s * 0.86f),
            strokeWidth = s * 0.10f, cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.arcArrow(tint: Color, s: Float, sw: Float) {
    val cx = s * 0.52f
    val cy = s * 0.58f
    val r = s * 0.27f
    val startDeg = 205f
    drawArc(
        tint, startDeg, 235f, false, Offset(cx - r, cy - r), Size(2f * r, 2f * r),
        style = Stroke(width = sw, cap = StrokeCap.Round)
    )
    val a = (startDeg * PI / 180.0).toFloat()
    val px = cx + r * cos(a)
    val py = cy + r * sin(a)
    val bx = sin(a)
    val by = -cos(a)
    val rx = cos(a)
    val ry = sin(a)
    val h = s * 0.13f
    val wing = s * 0.12f
    val path = Path()
    path.moveTo(px + bx * h, py + by * h)
    path.lineTo(px + rx * wing, py + ry * wing)
    path.lineTo(px - rx * wing, py - ry * wing)
    path.close()
    drawPath(path, tint)
}

@Composable
fun UndoGlyph(tint: Color, redo: Boolean = false) {
    Canvas(Modifier.size(24.dp)) {
        val s = size.minDimension
        val sw = s * 0.09f
        if (redo) {
            scale(-1f, 1f, Offset(s / 2f, s / 2f)) { arcArrow(tint, s, sw) }
        } else {
            arcArrow(tint, s, sw)
        }
    }
}

@Composable
fun ZoomGlyph(tint: Color, plus: Boolean) {
    Canvas(Modifier.size(24.dp)) {
        val s = size.minDimension
        val sw = s * 0.08f
        drawCircle(tint, s * 0.28f, Offset(s * 0.44f, s * 0.44f), style = Stroke(width = sw))
        drawLine(
            tint, Offset(s * 0.65f, s * 0.65f), Offset(s * 0.88f, s * 0.88f),
            strokeWidth = sw * 1.2f, cap = StrokeCap.Round
        )
        drawLine(
            tint, Offset(s * 0.32f, s * 0.44f), Offset(s * 0.56f, s * 0.44f),
            strokeWidth = sw * 0.9f, cap = StrokeCap.Round
        )
        if (plus) {
            drawLine(
                tint, Offset(s * 0.44f, s * 0.32f), Offset(s * 0.44f, s * 0.56f),
                strokeWidth = sw * 0.9f, cap = StrokeCap.Round
            )
        }
    }
}

/** أيقونة الصفحات (للدرج الجانبي). */
@Composable
fun PagesGlyph(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val s = size.minDimension
        val sw = s * 0.07f
        drawRoundRect(
            tint.copy(alpha = 0.5f), Offset(s * 0.30f, s * 0.08f), Size(s * 0.52f, s * 0.62f),
            CornerRadius(s * 0.07f), style = Stroke(width = sw)
        )
        drawRoundRect(
            Color.White.copy(alpha = 0.001f), Offset(s * 0.16f, s * 0.26f), Size(s * 0.52f, s * 0.66f),
            CornerRadius(s * 0.07f)
        )
        drawRoundRect(
            tint, Offset(s * 0.16f, s * 0.26f), Size(s * 0.52f, s * 0.66f),
            CornerRadius(s * 0.07f), style = Stroke(width = sw)
        )
        drawLine(
            tint, Offset(s * 0.26f, s * 0.48f), Offset(s * 0.58f, s * 0.48f),
            strokeWidth = sw * 0.8f, cap = StrokeCap.Round
        )
        drawLine(
            tint, Offset(s * 0.26f, s * 0.64f), Offset(s * 0.58f, s * 0.64f),
            strokeWidth = sw * 0.8f, cap = StrokeCap.Round
        )
    }
}

/** أيقونة قراءة النص (إطار مسح مع أسطر). */
@Composable
fun OcrGlyph(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val s = size.minDimension
        val sw = s * 0.08f
        val m = s * 0.10f
        val l = s * 0.24f
        val e = s - m
        val cap = StrokeCap.Round
        drawLine(tint, Offset(m, m + l), Offset(m, m), strokeWidth = sw, cap = cap)
        drawLine(tint, Offset(m, m), Offset(m + l, m), strokeWidth = sw, cap = cap)
        drawLine(tint, Offset(e - l, m), Offset(e, m), strokeWidth = sw, cap = cap)
        drawLine(tint, Offset(e, m), Offset(e, m + l), strokeWidth = sw, cap = cap)
        drawLine(tint, Offset(m, e - l), Offset(m, e), strokeWidth = sw, cap = cap)
        drawLine(tint, Offset(m, e), Offset(m + l, e), strokeWidth = sw, cap = cap)
        drawLine(tint, Offset(e - l, e), Offset(e, e), strokeWidth = sw, cap = cap)
        drawLine(tint, Offset(e, e), Offset(e, e - l), strokeWidth = sw, cap = cap)
        drawLine(tint, Offset(s * 0.30f, s * 0.38f), Offset(s * 0.70f, s * 0.38f), strokeWidth = sw * 0.8f, cap = cap)
        drawLine(tint, Offset(s * 0.30f, s * 0.52f), Offset(s * 0.70f, s * 0.52f), strokeWidth = sw * 0.8f, cap = cap)
        drawLine(tint, Offset(s * 0.30f, s * 0.66f), Offset(s * 0.54f, s * 0.66f), strokeWidth = sw * 0.8f, cap = cap)
    }
}

@Composable
fun ChevronGlyph(tint: Color, up: Boolean) {
    Canvas(Modifier.size(22.dp)) {
        val s = size.minDimension
        val sw = s * 0.11f
        val y1 = if (up) s * 0.62f else s * 0.38f
        val y2 = if (up) s * 0.38f else s * 0.62f
        drawLine(tint, Offset(s * 0.24f, y1), Offset(s * 0.50f, y2), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(s * 0.50f, y2), Offset(s * 0.76f, y1), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

/** أيقونة صفحة عليها علامة + لإضافة صفحة. */
@Composable
fun AddPageGlyph(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val s = size.minDimension
        val sw = s * 0.08f
        drawRoundRect(
            tint.copy(alpha = 0.14f), Offset(s * 0.18f, s * 0.08f), Size(s * 0.64f, s * 0.84f),
            CornerRadius(s * 0.08f)
        )
        drawRoundRect(
            tint, Offset(s * 0.18f, s * 0.08f), Size(s * 0.64f, s * 0.84f), CornerRadius(s * 0.08f),
            style = Stroke(width = sw)
        )
        drawLine(
            tint, Offset(s * 0.5f, s * 0.34f), Offset(s * 0.5f, s * 0.66f),
            strokeWidth = sw, cap = StrokeCap.Round
        )
        drawLine(
            tint, Offset(s * 0.34f, s * 0.5f), Offset(s * 0.66f, s * 0.5f),
            strokeWidth = sw, cap = StrokeCap.Round
        )
    }
}

/** التقاط النص بالهايلايتر: حرف T فوق شريط تمييز. */
@Composable
fun SnapGlyph(tint: Color, mark: Color) {
    Canvas(Modifier.size(24.dp)) {
        val s = size.minDimension
        val sw = s * 0.09f
        drawRect(mark.copy(alpha = 0.75f), Offset(s * 0.12f, s * 0.58f), Size(s * 0.76f, s * 0.26f))
        drawLine(tint, Offset(s * 0.24f, s * 0.18f), Offset(s * 0.76f, s * 0.18f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(s * 0.50f, s * 0.18f), Offset(s * 0.50f, s * 0.80f), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

/** درج الملخصات: صفحة عليها أسطر وأحدها مميز. */
@Composable
fun SummaryGlyph(tint: Color, mark: Color) {
    Canvas(Modifier.size(24.dp)) {
        val s = size.minDimension
        val sw = s * 0.07f
        drawRoundRect(
            tint, Offset(s * 0.16f, s * 0.08f), Size(s * 0.68f, s * 0.84f), CornerRadius(s * 0.08f),
            style = Stroke(width = sw)
        )
        drawRect(mark.copy(alpha = 0.8f), Offset(s * 0.26f, s * 0.40f), Size(s * 0.48f, s * 0.13f))
        val cap = StrokeCap.Round
        drawLine(tint, Offset(s * 0.30f, s * 0.27f), Offset(s * 0.70f, s * 0.27f), strokeWidth = sw * 0.8f, cap = cap)
        drawLine(tint, Offset(s * 0.30f, s * 0.465f), Offset(s * 0.70f, s * 0.465f), strokeWidth = sw * 0.8f, cap = cap)
        drawLine(tint, Offset(s * 0.30f, s * 0.66f), Offset(s * 0.70f, s * 0.66f), strokeWidth = sw * 0.8f, cap = cap)
        drawLine(tint, Offset(s * 0.30f, s * 0.79f), Offset(s * 0.54f, s * 0.79f), strokeWidth = sw * 0.8f, cap = cap)
    }
}

/** مربع نص مكتوب. */
@Composable
fun TextGlyph(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val s = size.minDimension
        val sw = s * 0.07f
        drawRoundRect(
            tint.copy(alpha = 0.5f), Offset(s * 0.08f, s * 0.12f), Size(s * 0.84f, s * 0.76f), CornerRadius(s * 0.08f),
            style = Stroke(width = sw * 0.8f)
        )
        drawLine(tint, Offset(s * 0.30f, s * 0.30f), Offset(s * 0.70f, s * 0.30f), strokeWidth = sw * 1.3f, cap = StrokeCap.Round)
        drawLine(tint, Offset(s * 0.50f, s * 0.30f), Offset(s * 0.50f, s * 0.72f), strokeWidth = sw * 1.3f, cap = StrokeCap.Round)
    }
}

/** حفظ وتصدير: سهم خارج من صندوق. */
@Composable
fun ExportGlyph(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val s = size.minDimension
        val sw = s * 0.08f
        val cap = StrokeCap.Round
        drawLine(tint, Offset(s * 0.16f, s * 0.56f), Offset(s * 0.16f, s * 0.86f), strokeWidth = sw, cap = cap)
        drawLine(tint, Offset(s * 0.16f, s * 0.86f), Offset(s * 0.84f, s * 0.86f), strokeWidth = sw, cap = cap)
        drawLine(tint, Offset(s * 0.84f, s * 0.86f), Offset(s * 0.84f, s * 0.56f), strokeWidth = sw, cap = cap)
        drawLine(tint, Offset(s * 0.50f, s * 0.66f), Offset(s * 0.50f, s * 0.12f), strokeWidth = sw, cap = cap)
        drawLine(tint, Offset(s * 0.50f, s * 0.12f), Offset(s * 0.32f, s * 0.30f), strokeWidth = sw, cap = cap)
        drawLine(tint, Offset(s * 0.50f, s * 0.12f), Offset(s * 0.68f, s * 0.30f), strokeWidth = sw, cap = cap)
    }
}

/** ملاءمة الصفحة كاملة داخل الشاشة. */
@Composable
fun FitGlyph(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val s = size.minDimension
        val sw = s * 0.08f
        drawRoundRect(
            tint.copy(alpha = 0.16f), Offset(s * 0.30f, s * 0.20f), Size(s * 0.40f, s * 0.60f), CornerRadius(s * 0.05f)
        )
        drawRoundRect(
            tint, Offset(s * 0.30f, s * 0.20f), Size(s * 0.40f, s * 0.60f), CornerRadius(s * 0.05f),
            style = Stroke(width = sw * 0.8f)
        )
        val cap = StrokeCap.Round
        drawLine(tint, Offset(s * 0.06f, s * 0.50f), Offset(s * 0.20f, s * 0.50f), strokeWidth = sw, cap = cap)
        drawLine(tint, Offset(s * 0.80f, s * 0.50f), Offset(s * 0.94f, s * 0.50f), strokeWidth = sw, cap = cap)
    }
}

/** نجمة المفضلة: ممتلئة ذهبية أو إطار فقط. */
@Composable
fun StarGlyph(filled: Boolean, tint: Color, modifier: Modifier = Modifier.size(18.dp)) {
    Canvas(modifier) {
        val s = size.minDimension
        val c = Offset(s / 2f, s / 2f)
        val outer = s * 0.48f
        val inner = s * 0.20f
        val p = Path()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val a = (-PI / 2 + i * PI / 5).toFloat()
            val x = c.x + r * cos(a)
            val y = c.y + r * sin(a) + s * 0.03f
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        if (filled) drawPath(p, tint) else drawPath(p, tint, style = Stroke(width = s * 0.08f))
    }
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
