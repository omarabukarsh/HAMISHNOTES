package com.mynotes.app

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.ceil
import kotlin.math.floor

/**
 * الخط الحي أثناء الكتابة، مرسوم تراكميًا: كل مقطع يُرسم مرة واحدة في صورة بحجم الجزء الظاهر
 * من الصفحة (بدقة الشاشة)، وفي كل إطار تُرسم المقاطع الجديدة فقط، فلا يثقل الرسم مع طول الخط.
 * الشكل مطابق لـ drawInk: نفس المنحنيات عبر منتصفات النقاط ونفس السماكة، والأقلام الشفافة
 * تُرسم بلون كامل في الصورة وتُطبَّق شفافيتها على الصورة كلها (فلا تتداخل المقاطع داكنةً).
 */
internal class LiveInkRaster {
    private var bmp: Bitmap? = null
    private var img: ImageBitmap? = null
    private val scope = CanvasDrawScope()
    private var left = 0
    private var top = 0
    private var scaleW = 0f
    private var gen = -1
    /** عدد النقاط التي رُسمت مقاطعها النهائية في الصورة. */
    private var done = 0

    /** يُرسم تراكميًا فقط الخط المتصل العادي (غير المتقطع، وغير الشكل). */
    fun handles(style: Int, sharp: Boolean): Boolean = style == LineStyle.SOLID && !sharp

    fun release() {
        bmp?.recycle()
        bmp = null
        img = null
    }

    /**
     * يرسم الخط الحي في ds (حجمها حجم الصفحة). visible: الجزء الظاهر من الصفحة بإحداثياتها.
     * gen: رقم الخط الحالي (يتغير مع كل خط جديد).
     */
    fun draw(ds: DrawScope, pts: List<Pt>, color: Color, width: Float, tool: Tool, visible: Rect, gen: Int) {
        val n = pts.size
        if (n == 0) return
        val w = ds.size.width
        val l = floor(visible.left.coerceIn(0f, ds.size.width)).toInt()
        val t = floor(visible.top.coerceIn(0f, ds.size.height)).toInt()
        val r = ceil(visible.right.coerceIn(0f, ds.size.width)).toInt()
        val b = ceil(visible.bottom.coerceIn(0f, ds.size.height)).toInt()
        if (r - l < 1 || b - t < 1) return
        var cur = bmp
        if (cur == null || cur.width != r - l || cur.height != b - t) {
            cur?.recycle()
            cur = Bitmap.createBitmap(r - l, b - t, Bitmap.Config.ARGB_8888)
            bmp = cur
            img = cur.asImageBitmap()
            done = 0
        } else if (l != left || t != top || w != scaleW || gen != this.gen || n < done) {
            cur.eraseColor(0)
            done = 0
        }
        left = l
        top = t
        scaleW = w
        this.gen = gen

        val pen = tool == Tool.FOUNTAIN || tool == Tool.BRUSH
        // الأقلام الشفافة: لون كامل في الصورة والشفافية على الطبقة كلها
        val layerAlpha = when (tool) {
            Tool.HIGHLIGHTER -> 0.35f
            Tool.PENCIL -> 0.78f
            Tool.FOUNTAIN, Tool.BRUSH -> 1f
            else -> color.alpha
        }
        val c = if (pen) color else color.copy(alpha = 1f)
        val strokePx = width * w * (if (tool == Tool.PENCIL) 0.85f else 1f)
        val base = if (tool == Tool.BRUSH) 0.15f else 0.4f
        val gain = if (tool == Tool.BRUSH) 1.5f else 1f
        fun at(i: Int) = Offset(pts[i].x * w, pts[i].y * w)
        fun mid(i: Int) = Offset((pts[i].x + pts[i + 1].x) / 2f * w, (pts[i].y + pts[i + 1].y) / 2f * w)
        /** بداية القطعة k في buildPath: النقطة الأولى أو منتصف (k-1, k). */
        fun start(k: Int) = if (k == 1) at(0) else mid(k - 1)

        // المقاطع النهائية الجديدة فقط
        val upTo = if (pen) n - 1 else n - 2
        if (upTo >= maxOf(1, done)) {
            val from = maxOf(1, done)
            scope.draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(img!!), Size(cur.width.toFloat(), cur.height.toFloat())) {
                translate(-l.toFloat(), -t.toFloat()) {
                    for (i in from..upTo) {
                        if (pen) {
                            drawLine(c, at(i - 1), at(i), strokeWidth = width * w * (base + gain * pts[i].p), cap = StrokeCap.Round)
                        } else {
                            val p = Path()
                            val s = start(i)
                            val e = mid(i)
                            p.moveTo(s.x, s.y)
                            p.quadraticBezierTo(at(i).x, at(i).y, e.x, e.y)
                            drawPath(p, c, style = Stroke(strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
            }
            done = upTo + 1
        }

        // عرض الصورة + الذيل الحي (آخر قطعة تتغير مع النقطة التالية)
        fun drawTail(d: DrawScope) {
            if (pen) {
                if (n == 1) d.drawCircle(c, width * w * (base + gain * pts[0].p) / 2f, at(0))
                return
            }
            val p = Path()
            val s = if (n == 1) at(0) else start(n - 1)
            val e = at(n - 1)
            p.moveTo(s.x, s.y)
            p.lineTo(if (n == 1) e.x + 0.01f else e.x, e.y)
            d.drawPath(p, c, style = Stroke(strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        val image = img ?: return
        if (layerAlpha < 1f) {
            ds.drawIntoCanvas { cv ->
                cv.saveLayer(
                    androidx.compose.ui.geometry.Rect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat()),
                    Paint().apply { alpha = layerAlpha }
                )
                ds.drawImage(image, IntOffset.Zero, IntSize(image.width, image.height), IntOffset(l, t), IntSize(r - l, b - t))
                drawTail(ds)
                cv.restore()
            }
        } else {
            ds.drawImage(image, IntOffset.Zero, IntSize(image.width, image.height), IntOffset(l, t), IntSize(r - l, b - t))
            drawTail(ds)
        }
    }
}
