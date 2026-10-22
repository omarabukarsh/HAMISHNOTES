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
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.os.Build
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
            val os = context.contentResolver.openOutputStream(uri, "wt") ?: throw IllegalStateException("output")
            os.use { out.writeTo(it) }
            return snap.pages.size
        } finally {
            out.close()
        }
    }

    /**
     * حفظ بنص قابل للتحديد: تُنسخ صفحات الملف الأصلية كما هي (نصها يبقى نصًا)، وتُرسم فوقها
     * التمييزات والكتابة اليدوية والصور ومربعات النص. الصفحات الفارغة والمدوّرة تُرسم كصورة.
     * إن فشل أي شيء يرجع المستدعي للحفظ كصور.
     */
    fun exportVector(
        context: Context,
        snap: Snapshot,
        fileFor: (String) -> java.io.File?,
        uri: Uri,
        onPage: (Int) -> Unit
    ): Int {
        val out = com.tom_roush.pdfbox.pdmodel.PDDocument()
        val sources = HashMap<String, com.tom_roush.pdfbox.pdmodel.PDDocument>()
        try {
            snap.pages.forEachIndexed { i, ref ->
                onPage(i)
                var page: com.tom_roush.pdfbox.pdmodel.PDPage? = null
                if (!ref.src.startsWith("blank:")) {
                    val src = sources.getOrPut(ref.src) {
                        val f = fileFor(ref.src) ?: throw IllegalStateException("source")
                        com.tom_roush.pdfbox.pdmodel.PDDocument.load(f)
                    }
                    val original = src.getPage(ref.index)
                    if (original.rotation % 360 == 0) {
                        val imported = out.importPage(original)
                        // الموارد (الخطوط والصور) قد تكون موروثة من شجرة الصفحات في الملف الأصلي
                        if (!original.cosObject.containsKey(com.tom_roush.pdfbox.cos.COSName.RESOURCES)) {
                            imported.resources = original.resources
                        }
                        page = imported
                    }
                }
                if (page == null) {
                    // صفحة فارغة أو مدوّرة: صورة الصفحة كاملة
                    val ph = PDF_W / ref.ratio
                    page = com.tom_roush.pdfbox.pdmodel.PDPage(
                        com.tom_roush.pdfbox.pdmodel.common.PDRectangle(PDF_W.toFloat(), ph)
                    )
                    out.addPage(page)
                    val bmp = snap.pdf.renderFresh(ref, RENDER_W) ?: throw IllegalStateException("render")
                    try {
                        val img = com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory.createFromImage(out, bmp)
                        com.tom_roush.pdfbox.pdmodel.PDPageContentStream(out, page).use { cs ->
                            cs.drawImage(img, 0f, 0f, PDF_W.toFloat(), ph)
                        }
                    } finally {
                        bmp.recycle()
                    }
                }
                drawOverlay(out, page, snap, i)
            }
            val os = context.contentResolver.openOutputStream(uri, "wt") ?: throw IllegalStateException("output")
            os.use { out.save(it) }
            return snap.pages.size
        } finally {
            try {
                out.close()
            } catch (e: Throwable) {
            }
            sources.values.forEach {
                try {
                    it.close()
                } catch (e: Throwable) {
                }
            }
        }
    }

    /** يرسم تعليقات صفحة فوق محتواها الأصلي بإحداثيات PDF (الأصل أسفل اليسار). */
    private fun drawOverlay(
        doc: com.tom_roush.pdfbox.pdmodel.PDDocument,
        page: com.tom_roush.pdfbox.pdmodel.PDPage,
        snap: Snapshot,
        i: Int
    ) {
        val box = page.cropBox
        val w = box.width
        val x0 = box.lowerLeftX
        val top = box.upperRightY
        fun px(nx: Float) = x0 + nx * w
        fun py(ny: Float) = top - ny * w

        val marks = snap.marks[i] ?: emptyList()
        val images = snap.images[i] ?: emptyList()
        val ink = snap.ink[i] ?: emptyList()
        val texts = snap.texts[i] ?: emptyList()
        if (marks.isEmpty() && images.isEmpty() && ink.isEmpty() && texts.isEmpty()) return

        com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
            doc, page, com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND, true, true
        ).use { cs ->
            // التمييز الملتقط: مستطيلات بمزج «ضرب» خلف النص
            if (marks.isNotEmpty()) {
                val gs = com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState()
                gs.blendMode = com.tom_roush.pdfbox.pdmodel.graphics.blend.BlendMode.MULTIPLY
                cs.saveGraphicsState()
                cs.setGraphicsStateParameters(gs)
                for (m in marks) {
                    val col = markColor(m.color, strong = m.level >= 2)
                    cs.setNonStrokingColor(col.red, col.green, col.blue)
                    for (r in m.rects) cs.addRect(px(r.l), py(r.b), (r.r - r.l) * w, (r.b - r.t) * w)
                    cs.fill()
                    if (m.level < 2 && m.strongRects.isNotEmpty()) {
                        val strong = markColor(m.color, strong = true)
                        cs.setNonStrokingColor(strong.red, strong.green, strong.blue)
                        for (r in m.strongRects) cs.addRect(px(r.l), py(r.b), (r.r - r.l) * w, (r.b - r.t) * w)
                        cs.fill()
                    }
                    val important = if (m.level >= 2) m.rects else m.strongRects
                    if (important.isNotEmpty()) {
                        cs.setNonStrokingColor(Palette.GoldDeep.red, Palette.GoldDeep.green, Palette.GoldDeep.blue)
                        for (r in important) cs.addRect(px(r.l), py(r.b), (r.r - r.l) * w, w * 0.0016f)
                        cs.fill()
                    }
                }
                cs.restoreGraphicsState()
            }
            // الصور
            for (item in images) {
                val f = AppController.repo.imageFile(snap.docId, item.file)
                val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: continue
                try {
                    val img = com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc, bmp)
                    val h = item.w / item.aspect
                    cs.drawImage(img, px(item.x), py(item.y + h), item.w * w, h * w)
                } finally {
                    bmp.recycle()
                }
            }
            // مربعات النص: تُرسم كصورة شفافة حتى يبقى شكل الحروف العربية صحيحًا
            for (tb in texts) {
                if (tb.text.isBlank()) continue
                val scale = 3f
                val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = tb.color.toArgb()
                    textSize = tb.size * w * scale
                }
                val lw = (tb.w * w * scale).toInt().coerceAtLeast(10)
                val layout = layoutOf(tb.text, tp, lw, 1.25f)
                val bmp = Bitmap.createBitmap(lw, layout.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                try {
                    layout.draw(Canvas(bmp))
                    val img = com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc, bmp)
                    val hh = layout.height / scale
                    cs.drawImage(img, px(tb.x), py(tb.y) - hh, tb.w * w, hh)
                } finally {
                    bmp.recycle()
                }
            }
            // الكتابة اليدوية
            for (s in ink) {
                if (s.pts.isEmpty()) continue
                val alpha = when (s.tool) {
                    Tool.HIGHLIGHTER -> 0.35f
                    Tool.PENCIL -> 0.78f
                    else -> 1f
                }
                cs.saveGraphicsState()
                if (alpha < 1f) {
                    val gs = com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState()
                    gs.strokingAlphaConstant = alpha
                    cs.setGraphicsStateParameters(gs)
                }
                cs.setStrokingColor(s.color.red, s.color.green, s.color.blue)
                cs.setLineCapStyle(if (s.tool == Tool.HIGHLIGHTER) 2 else 1)
                cs.setLineJoinStyle(1)
                val base = s.width * w * when (s.tool) {
                    Tool.PENCIL -> 0.85f
                    else -> 1f
                }
                val pts = s.pts
                if (s.tool == Tool.FOUNTAIN && pts.size > 1) {
                    // قلم الحبر: سماكة تتغير مع الضغط، فكل مقطع بخطه
                    for (j in 1 until pts.size) {
                        cs.setLineWidth(s.width * w * (0.4f + pts[j].p))
                        cs.moveTo(px(pts[j - 1].x), py(pts[j - 1].y))
                        cs.lineTo(px(pts[j].x), py(pts[j].y))
                        cs.stroke()
                    }
                } else {
                    cs.setLineWidth(base)
                    cs.moveTo(px(pts[0].x), py(pts[0].y))
                    if (pts.size == 1) {
                        cs.lineTo(px(pts[0].x) + 0.01f, py(pts[0].y))
                    } else {
                        for (j in 1 until pts.size) cs.lineTo(px(pts[j].x), py(pts[j].y))
                    }
                    cs.stroke()
                }
                cs.restoreGraphicsState()
            }
        }
    }

    private fun drawMarks(c: Canvas, marks: List<TextMark>, w: Float) {
        if (marks.isEmpty()) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.GoldDeep.toArgb() }
        for (m in marks) {
            p.color = markColor(m.color, strong = m.level >= 2).toArgb()
            for (r in m.rects) c.drawRect(r.l * w, r.t * w, r.r * w, r.b * w, p)
            if (m.level < 2) {
                p.color = markColor(m.color, strong = true).toArgb()
                for (r in m.strongRects) c.drawRect(r.l * w, r.t * w, r.r * w, r.b * w, p)
            }
            // خط ذهبي تحت النص المهم
            val important = if (m.level >= 2) m.rects else m.strongRects
            for (r in important) c.drawRect(r.l * w, r.b * w - w * 0.0016f, r.r * w, r.b * w, line)
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

    /** يحفظ الملخص بنفس شكل الدرج: عناوين، فقرات (المهم بخط عريض وتظليل)، وجداول. */
    fun exportSummary(context: Context, title: String, blocks: List<SBlock>, uri: Uri): Int {
        val pageW = PDF_W
        val pageH = 842
        val margin = 50f
        val contentW = (pageW - 2 * margin).toInt()
        val sans = context.resources.getFont(R.font.almarai_regular)
        val bold = context.resources.getFont(R.font.almarai_extrabold)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Palette.Navy.toArgb(); textSize = 21f; typeface = bold
        }
        val headPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Palette.Navy.toArgb(); textSize = 15f; typeface = bold
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Palette.TextPrimary.toArgb(); textSize = 11.5f; typeface = sans
        }
        val cellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Palette.TextPrimary.toArgb(); textSize = 10f; typeface = sans
        }
        val cellHeadPaint = TextPaint(cellPaint).apply { typeface = bold }
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.Gold.toArgb(); strokeWidth = 1.2f }
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Palette.Divider.toArgb(); strokeWidth = 0.8f; style = Paint.Style.STROKE
        }
        val headFill = Paint().apply { color = Palette.NavySoft.toArgb() }
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

        fun ensure(hgt: Float) {
            if (page == null || y + hgt > pageH - margin) newPage()
        }

        fun put(layout: StaticLayout, gapBefore: Float, gapAfter: Float) {
            ensure(gapBefore + layout.height)
            if (y > margin) y += gapBefore
            val c = page!!.canvas
            c.save()
            c.translate(margin, y)
            layout.draw(c)
            c.restore()
            y += layout.height + gapAfter
        }

        fun spanned(text: String, emphasis: List<String>): CharSequence {
            val sp = SpannableString(text)
            for (r in Summary.emphasisRanges(text, emphasis)) {
                val boldSpan: Any = if (Build.VERSION.SDK_INT >= 28) TypefaceSpan(bold) else StyleSpan(Typeface.BOLD)
                sp.setSpan(boldSpan, r.first, r.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sp.setSpan(BackgroundColorSpan(Palette.GoldSoft.toArgb()), r.first, r.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            return sp
        }

        try {
            newPage()
            put(layoutOf(title, titlePaint, contentW, 1.2f), 0f, 6f)
            page!!.canvas.drawLine(margin, y, pageW - margin, y, rule)
            y += 14f
            for (b in blocks) {
                when (b) {
                    is SHeading -> put(layoutOf(b.text, headPaint, contentW, 1.25f), 10f, 4f)
                    is SPara -> {
                        val whole = layoutOf(spanned(b.text, b.emphasis), bodyPaint, contentW, 1.45f)
                        if (whole.height <= pageH - 2 * margin - 20f) {
                            put(whole, 2f, 7f)
                        } else {
                            // فقرة أطول من صفحة: تُكتب جملة جملة لتنتقل بين الصفحات دون أن تُقطع
                            val parts = TextTidy.sentences(b.text)
                            parts.forEachIndexed { k, part ->
                                put(
                                    layoutOf(spanned(part, b.emphasis), bodyPaint, contentW, 1.45f),
                                    if (k == 0) 2f else 0f, if (k == parts.lastIndex) 7f else 1f
                                )
                            }
                        }
                    }
                    is STable -> {
                        val cols = b.rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
                        val colW = contentW / cols
                        y += 4f
                        b.rows.forEachIndexed { ri, row ->
                            val paint = if (b.header && ri == 0) cellHeadPaint else cellPaint
                            val cells = (0 until cols).map { ci ->
                                layoutOf(row.getOrNull(ci) ?: "", paint, (colW - 10).coerceAtLeast(10), 1.25f)
                            }
                            val rowH = (cells.maxOf { it.height } + 10).toFloat()
                            ensure(rowH)
                            val c = page!!.canvas
                            if (b.header && ri == 0) c.drawRect(margin, y, margin + colW * cols, y + rowH, headFill)
                            cells.forEachIndexed { ci, l ->
                                val x = margin + ci * colW
                                c.drawRect(x, y, x + colW, y + rowH, grid)
                                c.save()
                                c.translate(x + 5f, y + 5f)
                                l.draw(c)
                                c.restore()
                            }
                            y += rowH
                        }
                        y += 8f
                    }
                }
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
    fun markColor(c: androidx.compose.ui.graphics.Color, strong: Boolean = false): androidx.compose.ui.graphics.Color {
        val k = if (strong) 0.9f else MARK_ALPHA
        return androidx.compose.ui.graphics.Color(
            red = 1f - k + k * c.red,
            green = 1f - k + k * c.green,
            blue = 1f - k + k * c.blue,
            alpha = 1f
        )
    }
}
