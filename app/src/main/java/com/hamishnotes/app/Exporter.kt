package com.hamishnotes.app

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
        val docId: String,
        val notes: Map<Int, List<StickyNote>> = emptyMap()
    )

    fun snapshotOf(s: DocSession): Snapshot? {
        val pdf = s.pdf ?: return null
        // الملاحظات اللاصقة تُحفظ في الملف كنص مقروء بعرض مناسب
        val texts = s.texts.mapValues { (_, l) ->
            l.map { if (it.sticky) it.copy(w = 0.3f, x = it.x.coerceAtMost(0.68f)) else it }
        }
        return Snapshot(pdf, pdf.pages, s.annotations, s.images, s.marks, texts, s.id, s.notes)
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
                    // الملاحظات اللاصقة فوق الصفحة
                    (snap.notes[i] ?: emptyList()).forEach { n ->
                        val nw = if (n.collapsed) NOTE_COLLAPSED else n.w
                        val nh = if (n.collapsed) NOTE_COLLAPSED else n.h
                        c.drawRect(n.x * w, n.y * w, (n.x + nw) * w, (n.y + nh) * w, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = n.color })
                        n.pageInk().forEach { drawInk(c, it, w) }
                        n.textBox()?.let { drawTexts(c, listOf(it), w) }
                    }

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
        onPage: (Int) -> Unit,
        /** بيانات النسخة القابلة للتعديل (null = نسخة نهائية). */
        editableData: String? = null,
        editableImages: List<java.io.File> = emptyList()
    ): Int {
        val out = com.tom_roush.pdfbox.pdmodel.PDDocument()
        val counts = ArrayList<Int>()
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
                counts.add(EditablePdf.contentCount(page))
                drawOverlay(out, page, snap, i)
            }
            if (editableData != null) EditablePdf.embed(out, editableData, counts, editableImages)
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
        val pageNotes = snap.notes[i] ?: emptyList()
        val ink = (snap.ink[i] ?: emptyList()) + pageNotes.flatMap { it.pageInk() }
        val texts = (snap.texts[i] ?: emptyList()) + pageNotes.mapNotNull { it.textBox() }
        if (marks.isEmpty() && images.isEmpty() && ink.isEmpty() && texts.isEmpty() && pageNotes.isEmpty()) return

        com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
            doc, page, com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND, true, true
        ).use { cs ->
            // الملاحظات اللاصقة: مربعات ملوّنة (خطوطها تُرسم مع الكتابة)
            for (n in pageNotes) {
                val col = androidx.compose.ui.graphics.Color(n.color)
                val nw = if (n.collapsed) NOTE_COLLAPSED else n.w
                val nh = if (n.collapsed) NOTE_COLLAPSED else n.h
                cs.setNonStrokingColor(col.red, col.green, col.blue)
                cs.addRect(px(n.x), py(n.y + nh), nw * w, nh * w)
                cs.fill()
            }
            // التمييز الملتقط: مستطيلات بمزج «ضرب» خلف النص
            if (marks.isNotEmpty()) {
                val gs = com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState()
                gs.blendMode = com.tom_roush.pdfbox.pdmodel.graphics.blend.BlendMode.MULTIPLY
                cs.saveGraphicsState()
                cs.setGraphicsStateParameters(gs)
                for (m in marks) {
                    val col = markColor(m.color, strong = m.level >= 2)
                    cs.setNonStrokingColor(col.red, col.green, col.blue)
                    for (r in m.rects) addRoundRect(cs, px(r.l), py(r.b), (r.r - r.l) * w, (r.b - r.t) * w)
                    cs.fill()
                    if (m.level < 2 && m.strongRects.isNotEmpty()) {
                        val strong = markColor(m.color, strong = true)
                        cs.setNonStrokingColor(strong.red, strong.green, strong.blue)
                        for (r in m.strongRects) addRoundRect(cs, px(r.l), py(r.b), (r.r - r.l) * w, (r.b - r.t) * w)
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
                    applyTextStyle(this, tb)
                }
                val lw = (tb.w * w * scale).toInt().coerceAtLeast(10)
                val layout = layoutOf(tb.text, tp, lw, 1.25f, alignOf(tb))
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
                if (s.fill) {
                    // حرف مملوء من خط: مسار مغلق لكل حد، يُملأ بقاعدة nonzero
                    cs.saveGraphicsState()
                    cs.setNonStrokingColor(s.color.red, s.color.green, s.color.blue)
                    cs.setStrokingColor(s.color.red, s.color.green, s.color.blue)
                    for (ct in InkFill.contours(s.pts)) {
                        if (ct.size < 2) continue
                        cs.moveTo(px(ct[0].x), py(ct[0].y))
                        for (j in 1 until ct.size) cs.lineTo(px(ct[j].x), py(ct[j].y))
                        cs.closePath()
                    }
                    if (s.width > 0f) {
                        cs.setLineWidth(s.width * w)
                        cs.setLineJoinStyle(1)
                        cs.fillAndStroke()
                    } else cs.fill()
                    cs.restoreGraphicsState()
                    continue
                }
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
                // الأشكال بوصلات حادة (الزوايا المستديرة مبنية في نقاط الشكل)
                cs.setLineJoinStyle(if (s.shape) 0 else 1)
                if (s.shape) cs.setMiterLimit(10f)
                val base = s.width * w * when (s.tool) {
                    Tool.PENCIL -> 0.85f
                    else -> 1f
                }
                val pts = s.pts
                if (s.style == LineStyle.DASHED) cs.setLineDashPattern(floatArrayOf(base * 3.2f + 3f, base * 2.6f + 2.5f), 0f)
                else if (s.style == LineStyle.DOTTED) cs.setLineDashPattern(floatArrayOf(0.05f, base * 2.2f + 1.5f), 0f)
                if ((s.tool == Tool.FOUNTAIN || s.tool == Tool.BRUSH) && pts.size > 1 && s.style == LineStyle.SOLID && !s.shape) {
                    // قلم الحبر: سماكة تتغير مع الضغط، فكل مقطع بخطه
                    for (j in 1 until pts.size) {
                        cs.setLineWidth(s.width * w * (if (s.tool == Tool.BRUSH) 0.15f + 1.5f * pts[j].p else 0.4f + pts[j].p))
                        cs.moveTo(px(pts[j - 1].x), py(pts[j - 1].y))
                        cs.lineTo(px(pts[j].x), py(pts[j].y))
                        cs.stroke()
                    }
                } else {
                    cs.setLineWidth(base)
                    cs.moveTo(px(pts[0].x), py(pts[0].y))
                    if (pts.size == 1) {
                        cs.lineTo(px(pts[0].x) + 0.01f, py(pts[0].y))
                    } else if (s.shape && isClosedShape(pts)) {
                        for (j in 1 until pts.size - 1) cs.lineTo(px(pts[j].x), py(pts[j].y))
                        cs.closePath()
                    } else {
                        for (j in 1 until pts.size) cs.lineTo(px(pts[j].x), py(pts[j].y))
                    }
                    cs.stroke()
                }
                cs.restoreGraphicsState()
            }
        }
    }

    private fun roundRect(c: Canvas, r: MarkRect, w: Float, p: Paint) {
        val rad = markCorner((r.b - r.t) * w)
        c.drawRoundRect(r.l * w, r.t * w, r.r * w, r.b * w, rad, rad, p)
    }

    /** مستطيل بحواف دائرية في ملف PDF (منحنيات بيزييه). */
    private fun addRoundRect(cs: com.tom_roush.pdfbox.pdmodel.PDPageContentStream, x: Float, y: Float, rw: Float, rh: Float) {
        val rad = markCorner(rh).coerceAtMost(minOf(rw, rh) / 2f)
        val k = rad * 0.5523f
        cs.moveTo(x + rad, y)
        cs.lineTo(x + rw - rad, y)
        cs.curveTo(x + rw - rad + k, y, x + rw, y + rad - k, x + rw, y + rad)
        cs.lineTo(x + rw, y + rh - rad)
        cs.curveTo(x + rw, y + rh - rad + k, x + rw - rad + k, y + rh, x + rw - rad, y + rh)
        cs.lineTo(x + rad, y + rh)
        cs.curveTo(x + rad - k, y + rh, x, y + rh - rad + k, x, y + rh - rad)
        cs.lineTo(x, y + rad)
        cs.curveTo(x, y + rad - k, x + rad - k, y, x + rad, y)
        cs.closePath()
    }

    private fun drawMarks(c: Canvas, marks: List<TextMark>, w: Float) {
        if (marks.isEmpty()) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.GoldDeep.toArgb() }
        for (m in marks) {
            p.color = markColor(m.color, strong = m.level >= 2).toArgb()
            for (r in m.rects) roundRect(c, r, w, p)
            if (m.level < 2) {
                p.color = markColor(m.color, strong = true).toArgb()
                for (r in m.strongRects) roundRect(c, r, w, p)
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
        if (s.fill) {
            // حرف مملوء من خط
            val path = Path().apply { fillType = Path.FillType.WINDING }
            for (ct in InkFill.contours(pts)) {
                if (ct.size < 2) continue
                path.moveTo(ct[0].x * w, ct[0].y * w)
                for (i in 1 until ct.size) path.lineTo(ct[i].x * w, ct[i].y * w)
                path.close()
            }
            val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = s.color.toArgb(); style = Paint.Style.FILL }
            c.drawPath(path, fp)
            if (s.width > 0f) {
                fp.style = Paint.Style.STROKE; fp.strokeWidth = s.width * w; fp.strokeJoin = Paint.Join.ROUND
                c.drawPath(path, fp)
            }
            return
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE
        p.strokeJoin = Paint.Join.ROUND
        p.strokeCap = Paint.Cap.ROUND
        val styled = s.style != LineStyle.SOLID || s.shape
        when (if (styled && (s.tool == Tool.FOUNTAIN || s.tool == Tool.BRUSH)) Tool.BALLPOINT else s.tool) {
            Tool.FOUNTAIN, Tool.BRUSH -> {
                p.color = s.color.toArgb()
                val base = if (s.tool == Tool.BRUSH) 0.15f else 0.4f
                val gain = if (s.tool == Tool.BRUSH) 1.5f else 1f
                if (pts.size == 1) {
                    p.style = Paint.Style.FILL
                    c.drawCircle(pts[0].x * w, pts[0].y * w, s.width * w / 2f, p)
                    return
                }
                for (j in 1 until pts.size) {
                    val a = pts[j - 1]
                    val b = pts[j]
                    p.strokeWidth = s.width * w * (base + gain * b.p)
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
        if (s.style == LineStyle.DASHED) p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(p.strokeWidth * 3.2f + 6f, p.strokeWidth * 2.6f + 5f), 0f)
        else if (s.style == LineStyle.DOTTED) p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(0.1f, p.strokeWidth * 2.2f + 3f), 0f)
        if (s.shape) {
            p.strokeJoin = Paint.Join.MITER
            p.strokeMiter = 10f
            val path = Path()
            path.moveTo(pts[0].x * w, pts[0].y * w)
            if (isClosedShape(pts)) {
                for (i in 1 until pts.size - 1) path.lineTo(pts[i].x * w, pts[i].y * w)
                path.close()
            } else {
                for (i in 1 until pts.size) path.lineTo(pts[i].x * w, pts[i].y * w)
            }
            c.drawPath(path, p)
        } else c.drawPath(smoothPath(pts, w), p)
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
            applyTextStyle(tp, tb)
            val layout = layoutOf(tb.text, tp, (tb.w * w).toInt().coerceAtLeast(10), 1.25f, alignOf(tb))
            c.save()
            c.translate(tb.x * w, tb.y * w)
            layout.draw(c)
            c.restore()
        }
    }

    /** خط مربع النص ونمطه (عريض/مائل/تحته خط) على قلم الرسم. */
    private fun applyTextStyle(tp: TextPaint, tb: TextBox) {
        val base = FontStore.typeface(tb.font) ?: Typeface.DEFAULT
        val style = (if (tb.bold) Typeface.BOLD else 0) or (if (tb.italic) Typeface.ITALIC else 0)
        tp.typeface = if (style != 0) Typeface.create(base, style) else base
        // الخطوط بلا نسخة عريضة/مائلة: محاكاة
        if (tb.bold && !tp.typeface.isBold) tp.isFakeBoldText = true
        if (tb.italic && !tp.typeface.isItalic) tp.textSkewX = -0.22f
        tp.isUnderlineText = tb.underline
    }

    private fun alignOf(tb: TextBox): Layout.Alignment = when (tb.align) {
        1 -> Layout.Alignment.ALIGN_CENTER
        2 -> Layout.Alignment.ALIGN_OPPOSITE
        else -> Layout.Alignment.ALIGN_NORMAL
    }

    private fun layoutOf(text: CharSequence, tp: TextPaint, width: Int, spacing: Float, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, tp, width)
            .setAlignment(align)
            .setLineSpacing(0f, spacing)
            .setIncludePad(false)
            .build()

    // ---------------------------------------------------------------- الملخصات

    /** يحفظ الملخص بنفس شكل الدرج: عناوين، فقرات (المهم بخط عريض وتظليل)، وجداول. */
    fun exportSummary(context: Context, title: String, blocks: List<SBlock>, uri: Uri): Int =
        exportSummarySections(context, title, listOf("" to blocks), uri)

    /** عدة أقسام (مستند لكل قسم) في ملف واحد؛ القسم بعنوان فارغ لا يُطبع عنوانه. */
    fun exportSummarySections(context: Context, title: String, sections: List<Pair<String, List<SBlock>>>, uri: Uri): Int {
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
        val sectionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Palette.GoldDeep.toArgb(); textSize = 17f; typeface = bold
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
            for ((secTitle, blocks) in sections) {
            if (secTitle.isNotEmpty()) {
                put(layoutOf(secTitle, sectionPaint, contentW, 1.2f), 18f, 4f)
                page!!.canvas.drawLine(margin, y, margin + 60f, y, rule)
                y += 10f
            }
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
            }
            page?.let { out.finishPage(it) }
            page = null
            val os = context.contentResolver.openOutputStream(uri, "wt") ?: throw IllegalStateException("output")
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
