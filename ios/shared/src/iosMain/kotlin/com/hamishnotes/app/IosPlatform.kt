package com.hamishnotes.app

import android.net.Uri
import java.io.File
import java.io.toByteArray
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.dataWithContentsOfFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeContent
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.CoreGraphics.CGRectMake

/*
 * جسر iPad: اختيار الملفات من تطبيق «الملفات»، وحفظ الملفات المصدَّرة، والمشاركة.
 * يستخدمه بديل rememberLauncherForActivityResult (ActivityCompat.kt) وAppController.
 */

/** ملف من حزمة التطبيق (الخطوط والصور المنسوخة من res/ في أندرويد). */
fun bundleBytes(relPath: String): ByteArray? {
    val dir = relPath.substringBeforeLast('/', "")
    val file = relPath.substringAfterLast('/')
    val path = NSBundle.mainBundle.pathForResource(file.substringBeforeLast('.'), file.substringAfterLast('.'), dir.ifEmpty { null })
        ?: return null
    return NSData.dataWithContentsOfFile(path)?.toByteArray()
}

fun runOnMain(block: () -> Unit) {
    dispatch_async(dispatch_get_main_queue()) { block() }
}

object IosUi {
    /** أعلى شاشة معروضة (لعرض نوافذ النظام فوقها). */
    fun topController(): UIViewController? {
        val app = UIApplication.sharedApplication
        val window = app.connectedScenes.firstNotNullOfOrNull { s ->
            (s as? UIWindowScene)?.windows?.firstOrNull { (it as? UIWindow)?.isKeyWindow() == true } as? UIWindow
        } ?: app.keyWindow
        var vc = window?.rootViewController ?: return null
        while (vc.presentedViewController != null) vc = vc.presentedViewController!!
        return vc
    }

    /** على iPad تظهر نوافذ المشاركة كفقاعة: تحتاج نقطة تخرج منها (آخر لمسة). */
    @OptIn(ExperimentalForeignApi::class)
    fun present(vc: UIViewController) {
        runOnMain {
            val top = topController() ?: return@runOnMain
            vc.popoverPresentationController?.let { pop ->
                pop.sourceView = top.view
                // مكان اللمسة بالبكسل، ونوافذ UIKit بالنقاط
                val o = TouchOrigin.recent()
                val k = platform.UIKit.UIScreen.mainScreen.scale
                val (w, h) = top.view.bounds.useContents { size.width to size.height }
                val x = o?.x?.toDouble()?.div(k) ?: (w / 2)
                val y = o?.y?.toDouble()?.div(k) ?: (h / 2)
                pop.sourceRect = CGRectMake(x, y, 1.0, 1.0)
            }
            top.presentViewController(vc, animated = true, completion = null)
        }
    }
}

/** اختيار الملفات: تُنسخ إلى مساحة التطبيق المؤقتة، ويُعاد مسار كل نسخة. */
object IosFiles {
    // يجب الاحتفاظ بالمفوَّض حتى يُغلق المنتقي
    private var delegate: NSObject? = null

    private fun typesFor(mimes: List<String>) = mimes.mapNotNull { m ->
        when {
            m == "*/*" -> UTTypeData
            m == "image/*" -> UTTypeImage
            m == "application/pdf" -> UTTypePDF
            m.endsWith("/*") -> UTTypeContent
            else -> UTType.typeWithMIMEType(m)
        }
    }.ifEmpty { listOf(UTTypeData) }.distinct()

    fun pick(mimes: List<String>, multiple: Boolean, onResult: (List<Uri>) -> Unit) {
        runOnMain {
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = typesFor(mimes), asCopy = true)
            picker.allowsMultipleSelection = multiple
            val d = object : NSObject(), UIDocumentPickerDelegateProtocol {
                override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                    delegate = null
                    onResult(didPickDocumentsAtURLs.mapNotNull { (it as? NSURL)?.path?.let { p -> Uri.fromFile(File(p)) } })
                }

                override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                    delegate = null
                    onResult(emptyList())
                }
            }
            delegate = d
            picker.delegate = d
            IosUi.present(picker)
        }
    }

    /** ملف مؤقت يُكتب فيه الملف المصدَّر قبل أن يختار المستخدم مكان حفظه. */
    fun exportTarget(name: String): Uri {
        val dir = File(NSTemporaryDirectory() + "export").apply { mkdirs() }
        val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "-").ifBlank { "MyNotes.pdf" }
        val f = File(dir, safe)
        f.delete()
        return Uri.forExport(f, safe)
    }

    /** بعد كتابة ملف التصدير: نافذة «حفظ في الملفات» ليختار المستخدم المكان (iCloud، على الجهاز…). */
    fun deliver(uri: Uri) {
        if (!File(uri.path).exists()) return
        runOnMain {
            val picker = UIDocumentPickerViewController(forExportingURLs = listOf(NSURL.fileURLWithPath(uri.path)), asCopy = true)
            IosUi.present(picker)
        }
    }

    /** نافذة المشاركة (AirDrop، البريد، الرسائل، حفظ في الملفات…). */
    fun share(files: List<File>, text: String? = null) {
        runOnMain {
            val items = ArrayList<Any>()
            files.forEach { items.add(NSURL.fileURLWithPath(it.path)) }
            if (text != null) items.add(text)
            val vc = UIActivityViewController(activityItems = items, applicationActivities = null)
            IosUi.present(vc)
        }
    }
}

/** الصور المضافة: تصحيح الاتجاه والتصغير عبر UIKit. */
object IosImages {
    class Encoded(val jpeg: ByteArray, val png: ByteArray)

    @OptIn(ExperimentalForeignApi::class)
    fun normalized(path: String, maxDim: Int): Pair<Encoded, Float>? {
        val img = platform.UIKit.UIImage.imageWithContentsOfFile(path) ?: return null
        val (w0, h0) = img.size.useContents { width to height }
        if (w0 <= 0.0 || h0 <= 0.0) return null
        val k = minOf(1.0, maxDim / maxOf(w0, h0))
        val w = (w0 * k).coerceAtLeast(1.0)
        val h = (h0 * k).coerceAtLeast(1.0)
        val fmt = platform.UIKit.UIGraphicsImageRendererFormat.defaultFormat()
        fmt.scale = 1.0
        val renderer = platform.UIKit.UIGraphicsImageRenderer(size = platform.CoreGraphics.CGSizeMake(w, h), format = fmt)
        val out = renderer.imageWithActions { _ -> img.drawInRect(CGRectMake(0.0, 0.0, w, h)) }
        val jpg = platform.UIKit.UIImageJPEGRepresentation(out, 0.9)?.toByteArray() ?: return null
        val isPng = path.lowercase().endsWith(".png")
        val png = if (isPng) platform.UIKit.UIImagePNGRepresentation(out)?.toByteArray() ?: jpg else ByteArray(0)
        return Encoded(jpg, png) to (w / h).toFloat()
    }
}

/**
 * ضغط Apple Pencil في iOS قوة (force): 1.0 = لمسة عادية، وحتى ~4.17 أقصى ضغط،
 * بينما يتوقع كود الأقلام قيمة 0..1 كما في أندرويد. الإصبع على iPad بلا ضغط (0) فيعامَل كوسط.
 */
fun pencilPressure(force: Float): Float = if (force <= 0f) 0.5f else (force / 2.6f).coerceIn(0.05f, 1f)
