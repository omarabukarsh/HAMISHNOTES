package android.content

import java.io.File
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask

/*
 * بديل Context و SharedPreferences في نسخة iPad.
 * filesDir = مجلد Documents للتطبيق (يظهر في تطبيق «الملفات» ويُنسخ احتياطيًا)،
 * والإعدادات في NSUserDefaults بمجموعة منفصلة لكل اسم.
 */
open class Context {
    val filesDir: File by lazy {
        File(NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first().toString()).also { it.mkdirs() }
    }
    val cacheDir: File by lazy {
        File(NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first().toString()).also { it.mkdirs() }
    }
    val applicationContext: Context get() = this
    val packageName: String get() = "com.mynotes.app"

    private val prefs = HashMap<String, SharedPreferences>()

    fun getSharedPreferences(name: String, @Suppress("UNUSED_PARAMETER") mode: Int): SharedPreferences =
        prefs.getOrPut(name) { SharedPreferences(name) }

    fun getSystemService(name: String): Any? = when (name) {
        CLIPBOARD_SERVICE -> ClipboardManager
        else -> null
    }

    companion object {
        const val MODE_PRIVATE = 0
        const val CLIPBOARD_SERVICE = "clipboard"
    }
}

/** الحافظة: UIPasteboard العامة. */
class ClipData private constructor(val text: CharSequence) {
    class Item(val text: CharSequence?)
    fun getItemAt(@Suppress("UNUSED_PARAMETER") i: Int): Item = Item(text)
    val itemCount: Int get() = 1

    companion object {
        fun newPlainText(@Suppress("UNUSED_PARAMETER") label: CharSequence?, text: CharSequence): ClipData = ClipData(text)
    }
}

object ClipboardManager {
    fun setPrimaryClip(c: ClipData) {
        platform.UIKit.UIPasteboard.generalPasteboard.string = c.text.toString()
    }

    val primaryClip: ClipData?
        get() = platform.UIKit.UIPasteboard.generalPasteboard.string?.let { ClipData.newPlainText(null, it) }

    fun hasPrimaryClip(): Boolean = platform.UIKit.UIPasteboard.generalPasteboard.hasStrings
}

/** سياق التطبيق الوحيد في iOS. */
object AppContext : Context()

class SharedPreferences internal constructor(name: String) {
    private val d = NSUserDefaults(suiteName = "com.mynotes.app.$name")

    fun getString(key: String, def: String?): String? = d.stringForKey(key) ?: def
    fun getInt(key: String, def: Int): Int = if (d.objectForKey(key) != null) d.integerForKey(key).toInt() else def
    fun getLong(key: String, def: Long): Long = if (d.objectForKey(key) != null) d.integerForKey(key) else def
    fun getFloat(key: String, def: Float): Float = if (d.objectForKey(key) != null) d.floatForKey(key) else def
    fun getBoolean(key: String, def: Boolean): Boolean = if (d.objectForKey(key) != null) d.boolForKey(key) else def
    fun getStringSet(key: String, def: Set<String>?): Set<String>? =
        d.stringArrayForKey(key)?.map { it.toString() }?.toSet() ?: def
    fun contains(key: String): Boolean = d.objectForKey(key) != null

    fun edit(): Editor = Editor()

    inner class Editor {
        private val ops = ArrayList<() -> Unit>()
        fun putString(k: String, v: String?): Editor { ops.add { if (v == null) d.removeObjectForKey(k) else d.setObject(v, k) }; return this }
        fun putInt(k: String, v: Int): Editor { ops.add { d.setInteger(v.toLong(), k) }; return this }
        fun putLong(k: String, v: Long): Editor { ops.add { d.setInteger(v, k) }; return this }
        fun putFloat(k: String, v: Float): Editor { ops.add { d.setFloat(v, k) }; return this }
        fun putBoolean(k: String, v: Boolean): Editor { ops.add { d.setBool(v, k) }; return this }
        fun putStringSet(k: String, v: Set<String>?): Editor { ops.add { if (v == null) d.removeObjectForKey(k) else d.setObject(v.toList(), k) }; return this }
        fun remove(k: String): Editor { ops.add { d.removeObjectForKey(k) }; return this }
        fun clear(): Editor {
            ops.add { d.dictionaryRepresentation().keys.forEach { d.removeObjectForKey(it.toString()) } }
            return this
        }
        fun apply() { ops.forEach { it() } }
        fun commit(): Boolean { apply(); return true }
    }
}
