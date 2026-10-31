package com.hamishnotes.app

import androidx.compose.ui.graphics.Color
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreFoundation.CFURLRef
import platform.CoreGraphics.CGContextRef
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreText.CTFontManagerRegisterFontsForURL
import platform.CoreText.kCTFontManagerScopeProcess
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSBundle
import platform.Foundation.NSMakeRange
import platform.Foundation.NSMutableAttributedString
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.addAttribute
import platform.Foundation.create
import platform.UIKit.NSBackgroundColorAttributeName
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.NSLineBreakByWordWrapping
import platform.UIKit.NSMutableParagraphStyle
import platform.UIKit.NSParagraphStyleAttributeName
import platform.UIKit.NSStringDrawingUsesLineFragmentOrigin
import platform.UIKit.NSTextAlignmentNatural
import platform.UIKit.NSWritingDirectionNatural
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsPopContext
import platform.UIKit.UIGraphicsPushContext
import platform.UIKit.boundingRectWithSize
import platform.UIKit.drawWithRect

/**
 * كتابة النص في ملفات PDF والصور (بديل StaticLayout في أندرويد): UIKit يتولى تشكيل الحروف العربية
 * واتجاه السطر، والنص في PDF يبقى نصًا حقيقيًا قابلًا للتحديد.
 */
@OptIn(ExperimentalForeignApi::class)
object IosText {
    private var registered = false

    /** يسجّل خطوط التطبيق (Almarai و Caveat) لاستخدامها بالاسم. */
    fun registerFonts() {
        if (registered) return
        registered = true
        fontFiles.forEach { name ->
            val path = NSBundle.mainBundle.pathForResource(name, "ttf", "font") ?: return@forEach
            val url = NSURL.fileURLWithPath(path)
            @Suppress("UNCHECKED_CAST")
            val cf = CFBridgingRetain(url) as CFURLRef?
            CTFontManagerRegisterFontsForURL(cf, kCTFontManagerScopeProcess, null)
        }
    }

    fun font(size: Double, bold: Boolean): UIFont {
        registerFonts()
        return UIFont.fontWithName(if (bold) "Almarai-ExtraBold" else "Almarai-Regular", size)
            ?: if (bold) UIFont.boldSystemFontOfSize(size) else UIFont.systemFontOfSize(size)
    }

    fun color(c: Color): UIColor =
        UIColor(red = c.red.toDouble(), green = c.green.toDouble(), blue = c.blue.toDouble(), alpha = c.alpha.toDouble())

    /** نص منسق: emphasis = مدى المقاطع المهمة (عريض مع تظليل). */
    class Block(
        val text: String,
        val size: Double,
        val color: Color,
        val bold: Boolean = false,
        val spacing: Double = 1.25,
        val emphasis: List<IntRange> = emptyList(),
        val emphasisBg: Color? = null
    ) {
        @Suppress("CAST_NEVER_SUCCEEDS")
        internal fun attributed(): NSMutableAttributedString {
            val para = NSMutableParagraphStyle()
            para.setAlignment(NSTextAlignmentNatural)
            para.setBaseWritingDirection(NSWritingDirectionNatural)
            para.setLineBreakMode(NSLineBreakByWordWrapping)
            para.setLineHeightMultiple(spacing)
            val attrs = mapOf<Any?, Any?>(
                NSFontAttributeName to font(size, bold),
                NSForegroundColorAttributeName to color(color),
                NSParagraphStyleAttributeName to para
            )
            val s = NSMutableAttributedString.create(string = text, attributes = attrs)
            val n = text.length
            for (r in emphasis) {
                val a = r.first.coerceIn(0, n)
                val b = (r.last + 1).coerceIn(a, n)
                if (b <= a) continue
                val range = NSMakeRange(a.toULong(), (b - a).toULong())
                s.addAttribute(NSFontAttributeName, font(size, true), range)
                emphasisBg?.let { s.addAttribute(NSBackgroundColorAttributeName, color(it), range) }
            }
            return s
        }
    }

    /** ارتفاع النص إذا كُتب بعرض width. */
    fun height(b: Block, width: Double): Double =
        b.attributed().boundingRectWithSize(CGSizeMake(width, 1.0e7), NSStringDrawingUsesLineFragmentOrigin, null)
            .useContents { size.height }

    /**
     * يكتب النص في سياق مقلوب (أعلى اليسار، مثل UIKit). يعيد الارتفاع المستخدم.
     */
    fun draw(ctx: CGContextRef, b: Block, x: Double, y: Double, width: Double): Double {
        val s = b.attributed()
        val h = s.boundingRectWithSize(CGSizeMake(width, 1.0e7), NSStringDrawingUsesLineFragmentOrigin, null).useContents { size.height }
        UIGraphicsPushContext(ctx)
        try {
            s.drawWithRect(CGRectMake(x, y, width, h + 1.0), NSStringDrawingUsesLineFragmentOrigin, null)
        } finally {
            UIGraphicsPopContext()
        }
        return h
    }
}
