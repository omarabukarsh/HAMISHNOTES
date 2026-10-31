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

    /** عدد تدفقات المحتوى في الصفحة (قبل رسم التعليقات فوقها). */
    fun contentCount(page: PDPage): Int = when (val c = page.cosObject.getDictionaryObject(COSName.CONTENTS)) {
        is COSArray -> c.size()
        is COSStream -> 1
        else -> 0
    }

    /** يرفق البيانات داخل الملف قبل حفظه. */
    fun embed(doc: PDDocument, dataJson: String, counts: List<Int>, images: List<File>) {
        val map = TreeMap<String, PDComplexFileSpecification>()
        fun add(name: String, bytes: ByteArray) {
            val ef = PDEmbeddedFile(doc, ByteArrayInputStream(bytes))
            ef.subtype = "application/octet-stream"
            ef.size = bytes.size
            val fs = PDComplexFileSpecification()
            fs.file = name
            fs.embeddedFile = ef
            map[name] = fs
        }
        add(DATA, dataJson.toByteArray(Charsets.UTF_8))
        add(META, JSONObject().put("v", 1).put("counts", JSONArray(counts)).toString().toByteArray(Charsets.UTF_8))
        images.filter { it.exists() }.forEach { add(IMG + it.name, it.readBytes()) }
        val tree = PDEmbeddedFilesNameTreeNode()
        tree.names = map
        val names = PDDocumentNameDictionary(doc.documentCatalog)
        names.embeddedFiles = tree
        doc.documentCatalog.names = names
        doc.documentInformation.setCustomMetadataValue("HamishEditable", "1")
    }

    /**
     * إن كان الملف نسخة قابلة للتعديل: يزيل التعليقات المرسومة من صفحاته ويحفظه نظيفًا،
     * ثم يكتب البيانات وصورها. يعيد true عند النجاح.
     */
    fun restore(file: File, writeData: (String) -> Unit, imgDir: File): Boolean {
        var doc: PDDocument? = null
        return try {
            doc = PDDocument.load(file)
            val tree = doc.documentCatalog.names?.embeddedFiles ?: return false
            val names = tree.names ?: return false
            val dataSpec = names[DATA] ?: return false
            val metaSpec = names[META] ?: return false
            val data = String(dataSpec.embeddedFile.toByteArray(), Charsets.UTF_8)
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
            imgDir.mkdirs()
            names.forEach { (n, spec) ->
                if (n.startsWith(IMG)) File(imgDir, n.removePrefix(IMG)).writeBytes(spec.embeddedFile.toByteArray())
            }
            doc.documentCatalog.names = null
            doc.documentInformation.setCustomMetadataValue("HamishEditable", null)
            val tmp = File(file.parentFile, file.name + ".tmp")
            doc.save(tmp)
            doc.close()
            doc = null
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
            writeData(data)
            true
        } catch (t: Throwable) {
            false
        } finally {
            try {
                doc?.close()
            } catch (e: Throwable) {
            }
        }
    }
}
