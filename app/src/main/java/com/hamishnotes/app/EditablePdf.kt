package com.hamishnotes.app

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentNameDictionary
import com.tom_roush.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification
import com.tom_roush.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.TreeMap

/**
 * نسخة PDF «قابلة للتعديل»: الصفحات تُحفظ مع التعليقات مرسومة فوقها (تظهر في أي تطبيق)،
 * ويُرفق داخل الملف نفسه نص بيانات التعليقات وعدد طبقات كل صفحة الأصلية.
 * عند فتح الملف في «هامش» تُزال الطبقة المرسومة وتُستعاد التعليقات قابلة للتعديل.
 */
object EditablePdf {
    private const val DATA = "hamish-data.json"
    private const val META = "hamish-meta.json"
    private const val IMG = "hamish-img-"

    /** الصيغة الموحدة (v2): الـPDF النظيف مرفقًا، فيُستعمل أصلًا عند الفتح بلا حذف طبقات. */
    const val ORIG = "hamish-orig.pdf"

    /** مرفقات تطبيق الآيباد (حبر PencilKit وبياناته): لا تُقرأ هنا، تُحفظ ويُعاد إرفاقها عند التصدير. */
    const val IOS = "hamish-ios-"

    /** عدد تدفقات المحتوى في الصفحة (قبل رسم التعليقات فوقها). */
    fun contentCount(page: PDPage): Int = when (val c = page.cosObject.getDictionaryObject(COSName.CONTENTS)) {
        is COSArray -> c.size()
        is COSStream -> 1
        else -> 0
    }

    /** يرفق البيانات داخل الملف قبل حفظه. */
    fun embed(
        doc: PDDocument,
        dataJson: String,
        counts: List<Int>,
        images: List<File>,
        /** الـPDF النظيف (قبل رسم التعليقات)؛ null = الصيغة القديمة v1. */
        orig: File? = null,
        /** مرفقات الآيباد المحفوظة من الاستيراد (أسماؤها تبدأ بـ hamish-ios-). */
        extras: List<File> = emptyList()
    ) {
        val map = TreeMap<String, PDComplexFileSpecification>()
        fun addStream(name: String, input: java.io.InputStream, size: Long) {
            val ef = input.use { PDEmbeddedFile(doc, it) }
            ef.subtype = "application/octet-stream"
            ef.size = size.toInt()
            val fs = PDComplexFileSpecification()
            fs.file = name
            fs.embeddedFile = ef
            map[name] = fs
        }
        fun add(name: String, bytes: ByteArray) = addStream(name, ByteArrayInputStream(bytes), bytes.size.toLong())
        fun addFile(name: String, f: File) = addStream(name, f.inputStream(), f.length())
        val hasOrig = orig != null && orig.exists() && orig.length() > 0
        add(DATA, dataJson.toByteArray(Charsets.UTF_8))
        // counts الحقيقية تبقى: النسخ القديمة (1.0.271) تفتح الملف بحذف الطبقات كعادتها
        add(META, JSONObject().put("v", if (hasOrig) 2 else 1).put("counts", JSONArray(counts)).toString().toByteArray(Charsets.UTF_8))
        images.filter { it.exists() }.forEach { addFile(IMG + it.name, it) }
        if (hasOrig) addFile(ORIG, orig!!)
        extras.filter { it.exists() && it.name.startsWith(IOS) }.forEach { addFile(it.name, it) }
        val tree = PDEmbeddedFilesNameTreeNode()
        tree.names = map
        val names = PDDocumentNameDictionary(doc.documentCatalog)
        names.embeddedFiles = tree
        doc.documentCatalog.names = names
        doc.documentInformation.setCustomMetadataValue("HamishEditable", "1")
    }

    /**
     * إن كان الملف نسخة قابلة للتعديل: يعيده نظيفًا ويكتب البيانات وصورها. يعيد true عند النجاح.
     * الصيغة الموحدة (v2): الأصل النظيف مرفق فيُكتب مكان الملف كما هو (ملفات أندرويد والآيباد).
     * الصيغة القديمة (v1): تُزال التعليقات المرسومة من الصفحات حسب عدد طبقاتها الأصلية.
     * extrasDir: تُحفظ فيه مرفقات الآيباد ليُعاد إرفاقها عند التصدير.
     */
    fun restore(file: File, writeData: (String) -> Unit, imgDir: File, extrasDir: File? = null): Boolean {
        var doc: PDDocument? = null
        val tmp = File(file.parentFile, file.name + ".tmp")
        return try {
            doc = PDDocument.load(file)
            val tree = doc.documentCatalog.names?.embeddedFiles ?: return false
            val names = allNames(tree)
            val dataSpec = names[DATA] ?: return false
            val metaSpec = names[META] ?: return false
            val data = String(dataSpec.embeddedFile.toByteArray(), Charsets.UTF_8)
            // بيانات تالفة: يبقى الملف كما هو (نسخة نهائية)
            JSONObject(data)
            val origSpec = names[ORIG]
            if (origSpec != null) {
                origSpec.embeddedFile.createInputStream().use { ins -> tmp.outputStream().use { ins.copyTo(it) } }
                if (!isPdf(tmp)) {
                    tmp.delete()
                    return false
                }
            } else {
                val meta = JSONObject(String(metaSpec.embeddedFile.toByteArray(), Charsets.UTF_8))
                val counts = meta.getJSONArray("counts")
                if (counts.length() != doc.numberOfPages) return false
                for (i in 0 until doc.numberOfPages) {
                    val page = doc.getPage(i)
                    val orig = counts.getInt(i)
                    val c = page.cosObject.getDictionaryObject(COSName.CONTENTS) as? COSArray ?: continue
                    val delta = c.size() - orig
                    if (delta <= 0) continue
                    // الرسم أُضيف بـ «q» في البداية وطبقة التعليقات في النهاية
                    if (delta >= 2) c.remove(0)
                    repeat(if (delta >= 2) delta - 1 else delta) { c.remove(c.size() - 1) }
                }
            }
            imgDir.mkdirs()
            names.forEach { (n, spec) ->
                if (n.startsWith(IMG)) safeChild(imgDir, n.removePrefix(IMG))?.writeBytes(spec.embeddedFile.toByteArray())
                else if (extrasDir != null && n.startsWith(IOS)) {
                    extrasDir.mkdirs()
                    safeChild(extrasDir, n)?.writeBytes(spec.embeddedFile.toByteArray())
                }
            }
            if (origSpec == null) {
                doc.documentCatalog.names = null
                doc.documentInformation.setCustomMetadataValue("HamishEditable", null)
                doc.save(tmp)
            }
            doc.close()
            doc = null
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
            writeData(data)
            true
        } catch (t: Throwable) {
            tmp.delete()
            false
        } finally {
            try {
                doc?.close()
            } catch (e: Throwable) {
            }
        }
    }

    /** كل المرفقات بأسمائها (الشجرة قد تكون مسطّحة أو بأبناء). */
    private fun allNames(node: com.tom_roush.pdfbox.pdmodel.common.PDNameTreeNode<PDComplexFileSpecification>): Map<String, PDComplexFileSpecification> {
        val out = LinkedHashMap<String, PDComplexFileSpecification>()
        node.names?.let { out.putAll(it) }
        node.kids?.forEach { out.putAll(allNames(it)) }
        return out
    }

    /** ملف داخل المجلد باسم بسيط فقط (لا مسارات داخل الاسم). */
    private fun safeChild(dir: File, name: String): File? =
        if (name.isEmpty() || name == "." || name == ".." || name.contains('/') || name.contains('\\')) null else File(dir, name)

    private fun isPdf(f: File): Boolean = try {
        f.inputStream().use { ins ->
            val head = ByteArray(1024)
            val n = ins.read(head)
            n > 4 && String(head, 0, n, Charsets.ISO_8859_1).contains("%PDF-")
        }
    } catch (e: Throwable) {
        false
    }
}
