package com.mynotes.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max

/**
 * تصدير ملفات PDF إلى مكان يختاره المستخدم (التنزيلات، Drive…):
 *  - المستند كما يظهر: الصفحة + التمييز الملتقط + الصور + الكتابة اليدوية + مربعات النص
 *  - الملخصات: صفحات نصية مرتبة حسب صفحات المستند
 * كل الدوال خارج الخيط الرئيسي.
 */
object Exporter {
    private const val RENDER_W = 1400
    private const val PDF_W = 595 // نقاط A4

    // ---------------------------------------------------------------- المستند مع التعليقات

    class Snapshot(
        val pdf: PdfDoc,
        val pages: List<PageRef>,
        val ink: Map<Int, List<Ink>>,
        val images: Map<Int, List<ImageItem>>,
        val marks: Map<Int, List<TextMark>>,
        val texts: Map<Int, List<TextBox>>,
        val docId: String
    )

    fun snapshotOf(s: DocSession): Snapshot? {
        val pdf = s.pdf ?: return null
        return Snapshot(pdf, pdf.pages, s.annotations, s.images, s.marks, s.texts, s.id)
    }

    /** يعيد عدد الصفحات المصدَّرة. */
    fun exportAnnotated(context: Context, snap: Snapshot, uri: Uri, onPage: (Int) -> Unit): Int {
        val out = PdfDocument()
        try {
            snap.pages.forEachIndexed { i, ref ->
                onPage(i)
                val base = snap.pdf.renderFresh(ref, RENDER_W) ?: throw IllegalStateException("render")
                try {
                    val w = base.width.toFloat()
                    val c = Canvas(base)
                    drawMarks(c, snap.marks[i] ?: emptyList(), w)
                    drawImages(c, snap.docId, snap.images[i] ?: emptyList(), w)
                    drawTexts(c, snap.texts[i] ?: emptyList(), w)
                    (snap.ink[i] ?: emptyList()).forEach { drawInk(c, it, w) }

                    val ph = max(1, (PDF_W / ref.ratio).toInt())
                    val page = out.startPage(PdfDocument.PageInfo.Builder(PDF_W, ph, i + 1).create())
                    page.canvas.drawBitmap(
                        base, null, RectF(0f, 0f, PDF_W.toFloat(), ph.toFloat()),
                        Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                    )
                    out.finishPage(page)
                } finally {
                    base.recycle()
                }
            }
            val os = context.contentResolver.openOutputStream(uri) ?: throw IllegalStateException("output")
            os.use { out.writeTo(it) }
            return snap.pages.size
        } finally {
            out.close()
        }
    }

    private fun drawMarks(c: Canvas, marks: List<TextMark>, w: Float) {
        if (marks.isEmpty()) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        for (m in marks) {
            p.color = markColor(m.color).toArgb()
            for (r in m.rects) c.drawRect(r.l * w, r.t * w, r.r * w, r.b * w, p)
        }
    }

    private fun drawImages(c: Canvas, docId: String, items: List<ImageItem>, w: Float) {
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        for (item in items) {
            val f = AppController.repo.imageFile(docId, item.file)
            val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: continue
            try {
                val x = item.x * w
                val y = item.y * w
                c.drawBitmap(bmp, null, RectF(x, y, x + item.w * w, y + item.w / item.aspect * w), p)
            } finally {
                bmp.recycle()
            }
        }
    }

    private fun drawInk(c: Canvas, s: Ink, w: Float) {
        val pts = s.pts
        if (pts.isEmpty()) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE
        p.strokeJoin = Paint.Join.ROUND
        p.strokeCap = Paint.Cap.ROUND
        when (s.tool) {
            Tool.FOUNTAIN -> {
                p.color = s.color.toArgb()
                if (pts.size == 1) {
                    p.style = Paint.Style.FILL
                    c.drawCircle(pts[0].x * w, pts[0].y * w, s.width * w / 2f, p)
                    return
                }
                for (j in 1 until pts.size) {
                    val a = pts[j - 1]
                    val b = pts[j]
                    p.strokeWidth = s.width * w * (0.4f + b.p)
                    c.drawLine(a.x * w, a.y * w, b.x * w, b.y * w, p)
                }
                return
            }
            Tool.HIGHLIGHTER -> {
                p.color = s.color.copy(alpha = 0.35f).toArgb()
                p.strokeWidth = s.width * w
                p.strokeCap = Paint.Cap.SQUARE
            }
            Tool.PENCIL -> {
                p.color = s.color.copy(alpha = 0.78f).toArgb()
                p.strokeWidth = s.width * w * 0.85f
            }
            else -> {
                p.color = s.color.toArgb()
                p.strokeWidth = s.width * w
            }
        }
        c.drawPath(smoothPath(pts, w), p)
    }

    /** نفس تنعيم buildPath في InkRender لكن لـ android.graphics. */
    private fun smoothPath(pts: List<Pt>, w: Float): Path {
        val path = Path()
        path.moveTo(pts[0].x * w, pts[0].y * w)
        if (pts.size == 1) {
            path.lineTo(pts[0].x * w + 0.01f, pts[0].y * w)
            return path
        }
        for (i in 1 until pts.size - 1) {
            val mx = (pts[i].x + pts[i + 1].x) / 2f * w
            val my = (pts[i].y + pts[i + 1].y) / 2f * w
            path.quadTo(pts[i].x * w, pts[i].y * w, mx, my)
        }
        path.lineTo(pts.last().x * w, pts.last().y * w)
        return path
    }

    private fun drawTexts(c: Canvas, boxes: List<TextBox>, w: Float) {
        for (tb in boxes) {
            if (tb.text.isBlank()) continue
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
            tp.color = tb.color.toArgb()
            tp.textSize = tb.size * w
            val layout = layoutOf(tb.text, tp, (tb.w * w).toInt().coerceAtLeast(10), 1.25f)
            c.save()
            c.translate(tb.x * w, tb.y * w)
            layout.draw(c)
            c.restore()
        }
    }

    private fun layoutOf(text: CharSequence, tp: TextPaint, width: Int, spacing: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, tp, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, spacing)
            .setIncludePad(false)
            .build()

    // ---------------------------------------------------------------- الملخصات

    /** مجموعة ملخص صفحة: رقم الصفحة (من 0) والفقرات. */
    class SummaryGroup(val page: Int, val items: List<String>)

    /** يبني مجموعات الملخص بنفس ترتيب الدرج؛ tidy = ترتيب النص في جمل. */
    fun summaryGroups(marks: List<TextMark>, tidy: Boolean): List<SummaryGroup> =
        marks.groupBy { it.page }.toSortedMap().map { (page, list) ->
            val texts = list.map { it.text }
            SummaryGroup(page, if (tidy) TextTidy.organize(texts) else texts.map { it.trim() }.filter { it.isNotEmpty() })
        }.filter { it.items.isNotEmpty() }

    /** نص الملخص كاملًا للنسخ والمشاركة. */
    fun summaryText(title: String, groups: List<SummaryGroup>): String {
        val sb = StringBuilder()
        sb.append("ملخص: ").append(title).append('\n')
        for (g in groups) {
            sb.append('\n').append("الصفحة ").append(g.page + 1).append('\n')
            g.items.forEach { sb.append("• ").append(it).append('\n') }
        }
        return sb.toString().trimEnd()
    }

    fun exportSummary(context: Context, title: String, groups: List<SummaryGroup>, uri: Uri): Int {
        val pageW = PDF_W
        val pageH = 842
        val margin = 48f
        val contentW = (pageW - 2 * margin).toInt()

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Palette.Navy.toArgb()
            textSize = 19f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Palette.Navy.toArgb()
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Palette.TextPrimary.toArgb()
            textSize = 11.5f
        }
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.Gold.toArgb(); strokeWidth = 1.2f }
        val bg = Palette.Ivory.toArgb()

        val out = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun newPage() {
            page?.let { out.finishPage(it) }
            pageNo++
            val p = out.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            p.canvas.drawColor(bg)
            page = p
            y = margin
        }

        fun put(layout: StaticLayout, gapAfter: Float) {
            if (page == null || y + layout.height > pageH - margin) newPage()
            val c = page!!.canvas
            c.save()
            c.translate(margin, y)
            layout.draw(c)
            c.restore()
            y += layout.height + gapAfter
        }

        try {
            newPage()
            put(layoutOf("ملخص: $title", titlePaint, contentW, 1.2f), 6f)
            page!!.canvas.drawLine(margin, y, pageW - margin, y, rule)
            y += 14f
            for (g in groups) {
                put(layoutOf("الصفحة ${g.page + 1}", headPaint, contentW, 1.2f), 6f)
                for (item in g.items) put(layoutOf("•  $item", bodyPaint, contentW, 1.35f), 5f)
                y += 10f
            }
            page?.let { out.finishPage(it) }
            page = null
            val os = context.contentResolver.openOutputStream(uri) ?: throw IllegalStateException("output")
            os.use { out.writeTo(it) }
            return pageNo
        } finally {
            out.close()
        }
    }

    /** شدة لون التمييز الملتقط. */
    const val MARK_ALPHA = 0.55f

    /**
     * لون التمييز ممزوجًا مسبقًا بالأبيض وبشفافية كاملة، ليُرسم بمزج «ضرب»:
     * الورق الأبيض يأخذ لون التمييز والنص الداكن يبقى داكنًا تمامًا (على كل إصدارات أندرويد).
     */
    fun markColor(c: androidx.compose.ui.graphics.Color): androidx.compose.ui.graphics.Color {
        val k = MARK_ALPHA
        return androidx.compose.ui.graphics.Color(
            red = 1f - k + k * c.red,
            green = 1f - k + k * c.green,
            blue = 1f - k + k * c.blue,
            alpha = 1f
        )
    }
}
