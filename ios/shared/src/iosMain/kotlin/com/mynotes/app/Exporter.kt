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
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import platform.CoreGraphics.CGContextAddLineToPoint
import platform.CoreGraphics.CGContextAddPath
import platform.CoreGraphics.CGContextAddQuadCurveToPoint
import platform.CoreGraphics.CGContextFillEllipseInRect
import platform.CoreGraphics.CGContextFillPath
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextMoveToPoint
import platform.CoreGraphics.CGContextRef
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextRestoreGState
import platform.CoreGraphics.CGContextSaveGState
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextSetAlpha
import platform.CoreGraphics.CGContextSetBlendMode
import platform.CoreGraphics.CGContextSetLineCap
import platform.CoreGraphics.CGContextSetLineDash
import platform.CoreGraphics.CGContextSetLineJoin
import platform.CoreGraphics.CGContextSetLineWidth
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGContextSetRGBStrokeColor
import platform.CoreGraphics.CGContextStrokePath
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGFloatVar
import platform.CoreGraphics.CGPDFDocumentRef
import platform.CoreGraphics.CGPDFDocumentRelease
import platform.CoreGraphics.CGPathCreateWithRoundedRect
import platform.CoreGraphics.CGPathRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGBlendMode
import platform.CoreGraphics.CGLineCap
import platform.CoreGraphics.CGLineJoin
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.set
import kotlin.math.max

/**
 * تصدير ملفات PDF (نسخة iPad، فوق CoreGraphics):
 *  - المستند كما يظهر: الصفحة + التمييز الملتقط + الصور + الكتابة اليدوية + مربعات النص
 *  - الملخصات: صفحات نصية مرتبة حسب صفحات المستند
 * الملف يُكتب في uri.path ثم يُعرض على المستخدم ليحفظه أو يشاركه (AppController).
 * كل الدوال خارج الخيط الرئيسي.
 */
@OptIn(ExperimentalForeignApi::class)
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

    /** صفحات كصور (احتياط إن فشل الحفظ بنص قابل للتحديد). يعيد عدد الصفحات المصدَّرة. */
    fun exportAnnotated(@Suppress("UNUSED_PARAMETER") context: Context, snap: Snapshot, uri: Uri, onPage: (Int) -> Unit): Int {
        val out = IosPdf.Writer(uri.path)
        try {
            snap.pages.forEachIndexed { i, ref ->
                onPage(i)
                val base = snap.pdf.renderFresh(ref, RENDER_W) ?: throw IllegalStateException("render")
                try {
                    val w = base.width.toFloat()
                    val c = Canvas(base)
                    drawMarks(c, snap.marks[i] ?: emptyList(), w)
                    drawImages(c, snap.docId, snap.images[i] ?: emptyList(), w)
                    drawTextsRaster(base, snap.texts[i] ?: emptyList(), w)
                    (snap.ink[i] ?: emptyList()).forEach { drawInk(c, it, w) }
                    // الملاحظات اللاصقة فوق الصفحة
                    (snap.notes[i] ?: emptyList()).forEach { n ->
                        val nw = if (n.collapsed) NOTE_COLLAPSED else n.w
                        val nh = if (n.collapsed) NOTE_COLLAPSED else n.h
                        c.drawRect(n.x * w, n.y * w, (n.x + nw) * w, (n.y + nh) * w, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = n.color })
                        n.pageInk().forEach { drawInk(c, it, w) }
                        n.textBox()?.let { drawTextsRaster(base, listOf(it), w) }
                    }
                    val ph = max(1, (PDF_W / ref.ratio).toInt())
                    out.beginPage(PDF_W.toDouble(), ph.toDouble())
                    val jpg = base.encode(Bitmap.CompressFormat.JPEG, 88) ?: throw IllegalStateException("encode")
                    val img = IosPdf.jpegImage(jpg) ?: throw IllegalStateException("image")
                    out.drawImage(img, 0.0, 0.0, PDF_W.toDouble(), ph.toDouble())
                    IosPdf.release(img)
                    out.endPage()
                } finally {
                    base.recycle()
                }
            }
            return snap.pages.size
        } finally {
            out.close()
        }
    }

    /**
     * حفظ بنص قابل للتحديد: صفحات الملف الأصلية تُرسم كما هي (نصها يبقى نصًا)، وتُرسم فوقها
     * التمييزات والكتابة اليدوية والصور ومربعات النص. الصفحات الفارغة تُرسم كصورة.
     * في iPad النسخة دائمًا نهائية (editableData يُتجاهل).
     */
    fun exportVector(
        @Suppress("UNUSED_PARAMETER") context: Context,
        snap: Snapshot,
        fileFor: (String) -> java.io.File?,
        uri: Uri,
        onPage: (Int) -> Unit,
        @Suppress("UNUSED_PARAMETER") editableData: String? = null,
        @Suppress("UNUSED_PARAMETER") editableImages: List<java.io.File> = emptyList()
    ): Int {
        val out = IosPdf.Writer(uri.path)
        val sources = HashMap<String, CGPDFDocumentRef>()
        try {
            snap.pages.forEachIndexed { i, ref ->
                onPage(i)
                val ph = PDF_W / ref.ratio
                out.beginPage(PDF_W.toDouble(), ph.toDouble())
                if (ref.src.startsWith("blank:")) {
                    val bmp = snap.pdf.renderFresh(ref, RENDER_W) ?: throw IllegalStateException("render")
                    try {
                        val jpg = bmp.encode(Bitmap.CompressFormat.JPEG, 90) ?: throw IllegalStateException("encode")
                        val img = IosPdf.jpegImage(jpg) ?: throw IllegalStateException("image")
                        out.drawImage(img, 0.0, 0.0, PDF_W.toDouble(), ph.toDouble())
                        IosPdf.release(img)
                    } finally {
                        bmp.recycle()
                    }
                } else {
                    val d = sources.getOrPut(ref.src) {
                        val f = fileFor(ref.src) ?: throw IllegalStateException("source")
                        IosPdf.openDocument(f.path) ?: throw IllegalStateException("source")
                    }
                    out.drawPdfPage(d, ref.index)
                }
                drawOverlay(out.ctx, snap, i, PDF_W.toFloat())
                out.endPage()
            }
            return snap.pages.size
        } finally {
            out.close()
            sources.values.forEach { CGPDFDocumentRelease(it) }
        }
    }

    // ---------------------------------------------------------------- رسم التعليقات بـ CoreGraphics (أعلى اليسار)

    private fun fill(ctx: CGContextRef, c: Color) = CGContextSetRGBFillColor(ctx, c.red.toDouble(), c.green.toDouble(), c.blue.toDouble(), c.alpha.toDouble())

    private fun roundRectPath(ctx: CGContextRef, l: Double, t: Double, rw: Double, rh: Double) {
        val rad = markCorner(rh.toFloat()).toDouble().coerceAtMost(minOf(rw, rh) / 2.0).coerceAtLeast(0.0)
        val p = CGPathCreateWithRoundedRect(CGRectMake(l, t, rw, rh), rad, rad, null) ?: return
        CGContextAddPath(ctx, p)
        CGPathRelease(p)
    }

    /** تعليقات صفحة فوق محتواها، والصفحة بعرض w نقطة. */
    private fun drawOverlay(ctx: CGContextRef, snap: Snapshot, i: Int, w: Float) {
        val wd = w.toDouble()
        val marks = snap.marks[i] ?: emptyList()
        val images = snap.images[i] ?: emptyList()
        val pageNotes = snap.notes[i] ?: emptyList()
        val ink = (snap.ink[i] ?: emptyList()) + pageNotes.flatMap { it.pageInk() }
        val texts = (snap.texts[i] ?: emptyList()) + pageNotes.mapNotNull { it.textBox() }
        if (marks.isEmpty() && images.isEmpty() && ink.isEmpty() && texts.isEmpty() && pageNotes.isEmpty()) return

        // الملاحظات اللاصقة: مربعات ملوّنة (خطوطها تُرسم مع الكتابة)
        for (n in pageNotes) {
            val nw = if (n.collapsed) NOTE_COLLAPSED else n.w
            val nh = if (n.collapsed) NOTE_COLLAPSED else n.h
            fill(ctx, Color(n.color))
            CGContextFillRect(ctx, CGRectMake(n.x * wd, n.y * wd, nw * wd, nh * wd))
        }
        // التمييز الملتقط: مستطيلات بمزج «ضرب» خلف النص
        if (marks.isNotEmpty()) {
            CGContextSaveGState(ctx)
            CGContextSetBlendMode(ctx, CGBlendMode.kCGBlendModeMultiply)
            for (m in marks) {
                fill(ctx, markColor(m.color, strong = m.level >= 2))
                for (r in m.rects) roundRectPath(ctx, r.l * wd, r.t * wd, (r.r - r.l) * wd, (r.b - r.t) * wd)
                CGContextFillPath(ctx)
                if (m.level < 2 && m.strongRects.isNotEmpty()) {
                    fill(ctx, markColor(m.color, strong = true))
                    for (r in m.strongRects) roundRectPath(ctx, r.l * wd, r.t * wd, (r.r - r.l) * wd, (r.b - r.t) * wd)
                    CGContextFillPath(ctx)
                }
                val important = if (m.level >= 2) m.rects else m.strongRects
                if (important.isNotEmpty()) {
                    fill(ctx, Palette.GoldDeep)
                    for (r in important) CGContextFillRect(ctx, CGRectMake(r.l * wd, r.b * wd - wd * 0.0016, (r.r - r.l) * wd, wd * 0.0016))
                }
            }
            CGContextRestoreGState(ctx)
        }
        // الصور
        for (item in images) {
            val f = AppController.repo.imageFile(snap.docId, item.file)
            val (ui, img) = IosPdf.fileImage(f.path) ?: continue
            val h = item.w / item.aspect
            // UIImage يصحح اتجاه الصورة إن رُسم بنفسه، لكن الصور محفوظة مصححة مسبقًا
            CGContextSaveGState(ctx)
            CGContextTranslateCTM(ctx, item.x * wd, (item.y + h) * wd)
            CGContextScaleCTM(ctx, 1.0, -1.0)
            platform.CoreGraphics.CGContextDrawImage(ctx, CGRectMake(0.0, 0.0, item.w * wd, h * wd), img)
            CGContextRestoreGState(ctx)
            ui.hashCode()
        }
        // مربعات النص: نص حقيقي (يبقى قابلًا للتحديد)
        for (tb in texts) {
            if (tb.text.isBlank()) continue
            IosText.draw(ctx, IosText.Block(tb.text, (tb.size * w).toDouble(), tb.color, spacing = 1.25), tb.x * wd, tb.y * wd, tb.w * wd)
        }
        // الكتابة اليدوية
        for (s in ink) drawInkCg(ctx, s, wd)
    }

    private fun drawInkCg(ctx: CGContextRef, s: Ink, w: Double) {
        val pts = s.pts
        if (pts.isEmpty()) return
        val alpha = when (s.tool) {
            Tool.HIGHLIGHTER -> 0.35
            Tool.PENCIL -> 0.78
            else -> 1.0
        }
        CGContextSaveGState(ctx)
        CGContextSetAlpha(ctx, alpha)
        CGContextSetRGBStrokeColor(ctx, s.color.red.toDouble(), s.color.green.toDouble(), s.color.blue.toDouble(), 1.0)
        CGContextSetLineCap(ctx, if (s.tool == Tool.HIGHLIGHTER) CGLineCap.kCGLineCapSquare else CGLineCap.kCGLineCapRound)
        CGContextSetLineJoin(ctx, CGLineJoin.kCGLineJoinRound)
        val base = s.width * w * (if (s.tool == Tool.PENCIL) 0.85 else 1.0)
        if (s.style == LineStyle.DASHED || s.style == LineStyle.DOTTED) memScoped {
            val dash = allocArray<DoubleVar>(2)
            if (s.style == LineStyle.DASHED) {
                dash[0] = base * 3.2 + 3.0; dash[1] = base * 2.6 + 2.5
            } else {
                dash[0] = 0.05; dash[1] = base * 2.2 + 1.5
            }
            CGContextSetLineDash(ctx, 0.0, dash, 2u)
        }
        val variable = (s.tool == Tool.FOUNTAIN || s.tool == Tool.BRUSH) && s.style == LineStyle.SOLID && !s.shape
        if (variable && pts.size > 1) {
            // قلم الحبر: سماكة تتغير مع الضغط، فكل مقطع بخطه
            for (j in 1 until pts.size) {
                CGContextSetLineWidth(ctx, s.width * w * (if (s.tool == Tool.BRUSH) 0.15 + 1.5 * pts[j].p else 0.4 + pts[j].p))
                CGContextMoveToPoint(ctx, pts[j - 1].x * w, pts[j - 1].y * w)
                CGContextAddLineToPoint(ctx, pts[j].x * w, pts[j].y * w)
                CGContextStrokePath(ctx)
            }
        } else if (pts.size == 1) {
            val r = base / 2.0
            CGContextSetRGBFillColor(ctx, s.color.red.toDouble(), s.color.green.toDouble(), s.color.blue.toDouble(), 1.0)
            CGContextFillEllipseInRect(ctx, CGRectMake(pts[0].x * w - r, pts[0].y * w - r, 2 * r, 2 * r))
        } else {
            CGContextSetLineWidth(ctx, base)
            CGContextMoveToPoint(ctx, pts[0].x * w, pts[0].y * w)
            if (s.shape) {
                for (j in 1 until pts.size) CGContextAddLineToPoint(ctx, pts[j].x * w, pts[j].y * w)
            } else {
                // نفس تنعيم buildPath في InkRender
                for (j in 1 until pts.size - 1) {
                    val mx = (pts[j].x + pts[j + 1].x) / 2.0 * w
                    val my = (pts[j].y + pts[j + 1].y) / 2.0 * w
                    CGContextAddQuadCurveToPoint(ctx, pts[j].x * w, pts[j].y * w, mx, my)
                }
                CGContextAddLineToPoint(ctx, pts.last().x * w, pts.last().y * w)
            }
            CGContextStrokePath(ctx)
        }
        CGContextRestoreGState(ctx)
    }

    // ---------------------------------------------------------------- رسم التعليقات على صورة الصفحة (Skia)

    private fun roundRect(c: Canvas, r: MarkRect, w: Float, p: Paint) {
        val rad = markCorner((r.b - r.t) * w)
        c.drawRoundRect(r.l * w, r.t * w, r.r * w, r.b * w, rad, rad, p)
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
            val path = Path()
            path.moveTo(pts[0].x * w, pts[0].y * w)
            for (i in 1 until pts.size) path.lineTo(pts[i].x * w, pts[i].y * w)
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

    /** مربعات النص على صورة الصفحة (CoreGraphics فوق ذاكرة الصورة نفسها). */
    private fun drawTextsRaster(bmp: Bitmap, boxes: List<TextBox>, w: Float) {
        val list = boxes.filter { it.text.isNotBlank() }
        if (list.isEmpty()) return
        val ctx = IosPdf.bitmapContext(bmp) ?: return
        try {
            CGContextTranslateCTM(ctx, 0.0, bmp.height.toDouble())
            CGContextScaleCTM(ctx, 1.0, -1.0)
            for (tb in list) {
                IosText.draw(ctx, IosText.Block(tb.text, (tb.size * w).toDouble(), tb.color, spacing = 1.25), (tb.x * w).toDouble(), (tb.y * w).toDouble(), (tb.w * w).toDouble())
            }
        } finally {
            CGContextRelease(ctx)
        }
        bmp.skia.notifyPixelsChanged()
    }

    // ---------------------------------------------------------------- الملخصات

    /** يحفظ الملخص بنفس شكل الدرج: عناوين، فقرات (المهم بخط عريض وتظليل)، وجداول. */
    fun exportSummary(context: Context, title: String, blocks: List<SBlock>, uri: Uri): Int =
        exportSummarySections(context, title, listOf("" to blocks), uri)

    /** عدة أقسام (مستند لكل قسم) في ملف واحد؛ القسم بعنوان فارغ لا يُطبع عنوانه. */
    fun exportSummarySections(@Suppress("UNUSED_PARAMETER") context: Context, title: String, sections: List<Pair<String, List<SBlock>>>, uri: Uri): Int {
        val pageW = PDF_W.toDouble()
        val pageH = 842.0
        val margin = 50.0
        val contentW = pageW - 2 * margin

        fun titleB(t: String) = IosText.Block(t, 21.0, Palette.Navy, bold = true, spacing = 1.2)
        fun headB(t: String) = IosText.Block(t, 15.0, Palette.Navy, bold = true, spacing = 1.25)
        fun sectionB(t: String) = IosText.Block(t, 17.0, Palette.GoldDeep, bold = true, spacing = 1.2)
        fun bodyB(t: String, emphasis: List<String>) = IosText.Block(
            t, 11.5, Palette.TextPrimary, spacing = 1.45,
            emphasis = Summary.emphasisRanges(t, emphasis), emphasisBg = Palette.GoldSoft
        )
        fun cellB(t: String, head: Boolean) = IosText.Block(t, 10.0, Palette.TextPrimary, bold = head, spacing = 1.25)

        val out = IosPdf.Writer(uri.path)
        val ctx = out.ctx
        var pageNo = 0
        var open = false
        var y = 0.0

        fun newPage() {
            if (open) out.endPage()
            pageNo++
            out.beginPage(pageW, pageH)
            open = true
            fill(ctx, Palette.Ivory)
            CGContextFillRect(ctx, CGRectMake(0.0, 0.0, pageW, pageH))
            y = margin
        }

        fun ensure(hgt: Double) {
            if (!open || y + hgt > pageH - margin) newPage()
        }

        fun put(b: IosText.Block, gapBefore: Double, gapAfter: Double) {
            val h = IosText.height(b, contentW)
            ensure(gapBefore + h)
            if (y > margin) y += gapBefore
            IosText.draw(ctx, b, margin, y, contentW)
            y += h + gapAfter
        }

        fun rule(x0: Double, x1: Double) {
            fill(ctx, Palette.Gold)
            CGContextFillRect(ctx, CGRectMake(x0, y - 0.6, x1 - x0, 1.2))
        }

        try {
            newPage()
            put(titleB(title), 0.0, 6.0)
            rule(margin, pageW - margin)
            y += 14.0
            for ((secTitle, blocks) in sections) {
                if (secTitle.isNotEmpty()) {
                    put(sectionB(secTitle), 18.0, 4.0)
                    rule(margin, margin + 60.0)
                    y += 10.0
                }
                for (b in blocks) {
                    when (b) {
                        is SHeading -> put(headB(b.text), 10.0, 4.0)
                        is SPara -> {
                            val whole = bodyB(b.text, b.emphasis)
                            if (IosText.height(whole, contentW) <= pageH - 2 * margin - 20.0) {
                                put(whole, 2.0, 7.0)
                            } else {
                                // فقرة أطول من صفحة: تُكتب جملة جملة لتنتقل بين الصفحات دون أن تُقطع
                                val parts = TextTidy.sentences(b.text)
                                parts.forEachIndexed { k, part ->
                                    put(bodyB(part, b.emphasis), if (k == 0) 2.0 else 0.0, if (k == parts.lastIndex) 7.0 else 1.0)
                                }
                            }
                        }
                        is STable -> {
                            val cols = b.rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
                            val colW = contentW / cols
                            y += 4.0
                            b.rows.forEachIndexed { ri, row ->
                                val head = b.header && ri == 0
                                val cells = (0 until cols).map { ci -> cellB(row.getOrNull(ci) ?: "", head) }
                                val rowH = cells.maxOf { IosText.height(it, (colW - 10.0).coerceAtLeast(10.0)) } + 10.0
                                ensure(rowH)
                                if (head) {
                                    fill(ctx, Palette.NavySoft)
                                    CGContextFillRect(ctx, CGRectMake(margin, y, colW * cols, rowH))
                                }
                                cells.forEachIndexed { ci, cell ->
                                    val x = margin + ci * colW
                                    CGContextSaveGState(ctx)
                                    CGContextSetRGBStrokeColor(ctx, Palette.Divider.red.toDouble(), Palette.Divider.green.toDouble(), Palette.Divider.blue.toDouble(), 1.0)
                                    CGContextSetLineWidth(ctx, 0.8)
                                    platform.CoreGraphics.CGContextStrokeRect(ctx, CGRectMake(x, y, colW, rowH))
                                    CGContextRestoreGState(ctx)
                                    IosText.draw(ctx, cell, x + 5.0, y + 5.0, (colW - 10.0).coerceAtLeast(10.0))
                                }
                                y += rowH
                            }
                            y += 8.0
                        }
                    }
                }
            }
            if (open) out.endPage()
            open = false
            return pageNo
        } finally {
            if (open) out.endPage()
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
