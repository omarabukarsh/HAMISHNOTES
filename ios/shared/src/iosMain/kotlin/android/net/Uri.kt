package android.net

import java.io.File

/**
 * بديل Uri في نسخة iPad: دائمًا مسار ملف محلي (الملفات المختارة تُنسخ أولًا إلى مساحة التطبيق).
 * للحفظ: Uri «تصدير» يُكتب فيه ثم يُعرض على المستخدم ليختار مكان الحفظ (انظر IosFiles.kt).
 */
class Uri private constructor(val path: String, val exportName: String? = null) {
    val lastPathSegment: String get() = path.substringAfterLast('/')
    val scheme: String get() = "file"
    val file: File get() = File(path)

    override fun toString(): String = "file://$path"
    override fun equals(other: Any?): Boolean = other is Uri && other.path == path
    override fun hashCode(): Int = path.hashCode()

    companion object {
        fun fromFile(f: File): Uri = Uri(f.path)
        fun parse(s: String): Uri = Uri(s.removePrefix("file://"))
        fun forExport(f: File, name: String): Uri = Uri(f.path, name)
    }
}
