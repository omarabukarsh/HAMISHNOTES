package com.hamishnotes.app

import android.graphics.Bitmap
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.useContents
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFURLCreateWithFileSystemPath
import platform.CoreFoundation.CFURLRef
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFURLPOSIXPathStyle
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreGraphics.CGAffineTransformMake
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextBeginPage
import platform.CoreGraphics.CGContextClipToRect
import platform.CoreGraphics.CGContextConcatCTM
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextDrawPDFPage
import platform.CoreGraphics.CGContextEndPage
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextRef
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextRestoreGState
import platform.CoreGraphics.CGContextSaveGState
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextSetInterpolationQuality
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGDataProviderCreateWithCFData
import platform.CoreGraphics.CGDataProviderRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageCreateWithJPEGDataProvider
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGPDFContextClose
import platform.CoreGraphics.CGPDFContextCreateWithURL
import platform.CoreGraphics.CGPDFDocumentCreateWithURL
import platform.CoreGraphics.CGPDFDocumentGetNumberOfPages
import platform.CoreGraphics.CGPDFDocumentGetPage
import platform.CoreGraphics.CGPDFDocumentIsEncrypted
import platform.CoreGraphics.CGPDFDocumentIsUnlocked
import platform.CoreGraphics.CGPDFDocumentRef
import platform.CoreGraphics.CGPDFDocumentRelease
import platform.CoreGraphics.CGPDFPageGetBoxRect
import platform.CoreGraphics.CGPDFPageGetRotationAngle
import platform.CoreGraphics.CGPDFPageRef
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGInterpolationHigh
import platform.CoreGraphics.kCGPDFCropBox
import platform.CoreGraphics.CGColorRenderingIntent

/*
 * طبقة PDF في iPad فوق CoreGraphics:
 *  - رسم الصفحات في صور (بديل PdfRenderer)
 *  - كتابة ملفات PDF (بديل PdfDocument و PDFBox في التصدير)
 */
@OptIn(ExperimentalForeignApi::class)
object IosPdf {

    private fun cfUrl(path: String): CFURLRef? {
        val s = CFStringCreateWithCString(kCFAllocatorDefault, path, kCFStringEncodingUTF8) ?: return null
        val u = CFURLCreateWithFileSystemPath(kCFAllocatorDefault, s, kCFURLPOSIXPathStyle, false)
        CFRelease(s)
        return u
    }

    /** يفتح ملف PDF أو null إن لم يكن صالحًا (أو مقفلًا بكلمة مرور). */
    fun openDocument(path: String): CGPDFDocumentRef? {
        val url = cfUrl(path) ?: return null
        val d = CGPDFDocumentCreateWithURL(url)
        CFRelease(url)
        if (d == null) return null
        if (CGPDFDocumentIsEncrypted(d) && !CGPDFDocumentIsUnlocked(d)) {
            CGPDFDocumentRelease(d)
            return null
        }
        if (CGPDFDocumentGetNumberOfPages(d).toInt() <= 0) {
            CGPDFDocumentRelease(d)
            return null
        }
        return d
    }

    fun pageCount(d: CGPDFDocumentRef): Int = CGPDFDocumentGetNumberOfPages(d).toInt()

    fun pageCount(path: String): Int {
        val d = openDocument(path) ?: return 0
        try {
            return pageCount(d)
        } finally {
            CGPDFDocumentRelease(d)
        }
    }

    private class Geo(val bx: Double, val by: Double, val bw: Double, val bh: Double, val rot: Int) {
        val effW: Double get() = if (rot % 180 == 0) bw else bh
        val effH: Double get() = if (rot % 180 == 0) bh else bw
    }

    private fun geo(page: CGPDFPageRef): Geo {
        val rot = ((CGPDFPageGetRotationAngle(page) % 360) + 360) % 360
        return CGPDFPageGetBoxRect(page, kCGPDFCropBox).useContents {
            Geo(origin.x, origin.y, size.width, size.height, rot)
        }
    }

    /** عرض الصفحة وارتفاعها بالنقاط (بعد التدوير). */
    fun pageSize(d: CGPDFDocumentRef, index: Int): Pair<Float, Float> {
        val page = CGPDFDocumentGetPage(d, (index + 1).toULong()) ?: return 595f to 842f
        val g = geo(page)
        return g.effW.toFloat() to g.effH.toFloat()
    }

    /**
     * يضع الصفحة داخل المستطيل (0,0,effW,effH) بإحداثيات PDF (الأصل أسفل اليسار) مع مراعاة التدوير.
     */
    private fun applyPageTransform(ctx: CGContextRef, g: Geo) {
        val t = when (g.rot) {
            90 -> CGAffineTransformMake(0.0, -1.0, 1.0, 0.0, -g.by, g.bw + g.bx)
            180 -> CGAffineTransformMake(-1.0, 0.0, 0.0, -1.0, g.bw + g.bx, g.bh + g.by)
            270 -> CGAffineTransformMake(0.0, 1.0, -1.0, 0.0, g.bh + g.by, -g.bx)
            else -> CGAffineTransformMake(1.0, 0.0, 0.0, 1.0, -g.bx, -g.by)
        }
        CGContextConcatCTM(ctx, t)
        CGContextClipToRect(ctx, CGRectMake(g.bx, g.by, g.bw, g.bh))
    }

    /** سياق CoreGraphics يرسم مباشرة في ذاكرة الصورة (RGBA). يجب تحريره بـ CGContextRelease. */
    fun bitmapContext(dest: Bitmap): CGContextRef? {
        val pm = dest.skia.peekPixels() ?: return null
        val ptr: CPointer<ByteVar> = interpretCPointer(pm.addr) ?: return null
        val cs = CGColorSpaceCreateDeviceRGB()
        val ctx = CGBitmapContextCreate(
            ptr, dest.width.toULong(), dest.height.toULong(), 8u, dest.skia.rowBytes.toULong(), cs,
            CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value
        )
        CGColorSpaceRelease(cs)
        return ctx
    }

    /**
     * يرسم الصفحة في الصورة: الصفحة كاملة بعرض fullW بكسل، والصورة تمثل المستطيل الذي يبدأ عند (left, top).
     */
    fun renderPage(d: CGPDFDocumentRef, index: Int, dest: Bitmap, fullW: Float, left: Float, top: Float, white: Boolean) {
        val page = CGPDFDocumentGetPage(d, (index + 1).toULong()) ?: return
        val g = geo(page)
        if (g.effW <= 0.0) return
        val k = fullW / g.effW
        val fullH = g.effH * k
        val ctx = bitmapContext(dest) ?: return
        try {
            if (white) {
                CGContextSetRGBFillColor(ctx, 1.0, 1.0, 1.0, 1.0)
                CGContextFillRect(ctx, CGRectMake(0.0, 0.0, dest.width.toDouble(), dest.height.toDouble()))
            }
            CGContextSetInterpolationQuality(ctx, kCGInterpolationHigh)
            // الصورة: الصف الأول أعلى الصفحة؛ CoreGraphics: الأصل أسفل اليسار
            CGContextTranslateCTM(ctx, -left.toDouble(), dest.height + top - fullH)
            CGContextScaleCTM(ctx, k, k)
            applyPageTransform(ctx, g)
            CGContextDrawPDFPage(ctx, page)
        } finally {
            CGContextRelease(ctx)
        }
        dest.skia.notifyPixelsChanged()
    }

    /** صورة CoreGraphics من صورة Skia (نسخة). يجب تحريرها بـ CGImageRelease. */
    fun cgImage(b: Bitmap): CGImageRef? {
        val ctx = bitmapContext(b) ?: return null
        try {
            return CGBitmapContextCreateImage(ctx)
        } finally {
            CGContextRelease(ctx)
        }
    }

    /** صورة JPEG مضغوطة تُدرج في PDF كما هي (ملفات أصغر). */
    fun jpegImage(bytes: ByteArray): CGImageRef? {
        val data = bytes.usePinned { CFDataCreate(kCFAllocatorDefault, it.addressOf(0).reinterpret(), bytes.size.toLong()) } ?: return null
        val provider = CGDataProviderCreateWithCFData(data)
        CFRelease(data)
        if (provider == null) return null
        val img = CGImageCreateWithJPEGDataProvider(provider, null, true, CGColorRenderingIntent.kCGRenderingIntentDefault)
        CGDataProviderRelease(provider)
        return img
    }

    /** صورة ملف (JPEG/PNG…) كـ CGImage عبر UIImage. لا تحتاج تحريرًا يدويًا (تبقى مع UIImage). */
    fun fileImage(path: String): Pair<platform.UIKit.UIImage, CGImageRef>? {
        val ui = platform.UIKit.UIImage.imageWithContentsOfFile(path) ?: return null
        val cg = ui.CGImage ?: return null
        return ui to cg
    }

    /**
     * يكتب ملف PDF. كل صفحة بإحداثيات أعلى اليسار (مثل Canvas في أندرويد) بوحدة النقاط.
     */
    class Writer(path: String) {
        val ctx: CGContextRef
        var pageW = 0.0
            private set
        var pageH = 0.0
            private set

        init {
            val url = cfUrl(path) ?: throw java.io.IOException("output")
            val c = CGPDFContextCreateWithURL(url, null, null)
            CFRelease(url)
            ctx = c ?: throw java.io.IOException("output")
        }

        fun beginPage(w: Double, h: Double) {
            pageW = w
            pageH = h
            memScoped {
                val box = alloc<CGRect>()
                box.origin.x = 0.0
                box.origin.y = 0.0
                box.size.width = w
                box.size.height = h
                CGContextBeginPage(ctx, box.ptr)
            }
            CGContextSaveGState(ctx)
            CGContextTranslateCTM(ctx, 0.0, h)
            CGContextScaleCTM(ctx, 1.0, -1.0)
        }

        fun endPage() {
            CGContextRestoreGState(ctx)
            CGContextEndPage(ctx)
        }

        /** صورة بإحداثيات أعلى اليسار (تُقلب محليًا لأن CoreGraphics يرسم الصور بأصل أسفل اليسار). */
        fun drawImage(img: CGImageRef, x: Double, y: Double, w: Double, h: Double) {
            CGContextSaveGState(ctx)
            CGContextTranslateCTM(ctx, x, y + h)
            CGContextScaleCTM(ctx, 1.0, -1.0)
            CGContextDrawImage(ctx, CGRectMake(0.0, 0.0, w, h), img)
            CGContextRestoreGState(ctx)
        }

        /** صفحة من ملف PDF آخر كما هي (نصها يبقى نصًا قابلًا للتحديد) بعرض الصفحة الحالية. */
        fun drawPdfPage(d: CGPDFDocumentRef, index: Int) {
            val page = CGPDFDocumentGetPage(d, (index + 1).toULong()) ?: return
            val g = geo(page)
            if (g.effW <= 0.0) return
            val k = pageW / g.effW
            CGContextSaveGState(ctx)
            // نعود لإحداثيات PDF (أسفل اليسار) داخل الصفحة
            CGContextTranslateCTM(ctx, 0.0, pageH)
            CGContextScaleCTM(ctx, 1.0, -1.0)
            CGContextScaleCTM(ctx, k, k)
            applyPageTransform(ctx, g)
            CGContextDrawPDFPage(ctx, page)
            CGContextRestoreGState(ctx)
        }

        fun close() {
            CGPDFContextClose(ctx)
            CGContextRelease(ctx)
        }
    }

    fun release(img: CGImageRef?) {
        if (img != null) CGImageRelease(img)
    }

    fun rect(x: Double, y: Double, w: Double, h: Double): CValue<CGRect> = CGRectMake(x, y, w, h)
}
