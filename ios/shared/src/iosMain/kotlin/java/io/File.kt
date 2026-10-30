package java.io

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.cinterop.BooleanVar
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.posix.memcpy
import platform.posix.rename

/*
 * بديل java.io.File لنسخة iPad (فوق NSFileManager)، بنفس الدوال التي يستخدمها كود التطبيق.
 */

open class IOException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
class FileNotFoundException(message: String? = null) : IOException(message)

private val fm: NSFileManager get() = NSFileManager.defaultManager

class File(path: String) : Comparable<File> {
    val path: String = path.trimEnd('/').ifEmpty { "/" }

    constructor(parent: File?, child: String) : this(if (parent == null) child else parent.path.trimEnd('/') + "/" + child)
    constructor(parent: String?, child: String) : this(if (parent == null) child else parent.trimEnd('/') + "/" + child)

    val name: String get() = path.substringAfterLast('/')
    val nameWithoutExtension: String get() = name.substringBeforeLast('.')
    val extension: String get() = name.substringAfterLast('.', "")
    val absolutePath: String get() = path
    val canonicalPath: String get() = path
    val parent: String? get() = path.substringBeforeLast('/', "").ifEmpty { null }
    val parentFile: File? get() = parent?.let { File(it) }

    fun exists(): Boolean = fm.fileExistsAtPath(path)

    fun isDirectory(): Boolean = memScoped {
        val dir = alloc<BooleanVar>()
        fm.fileExistsAtPath(path, dir.ptr) && dir.value
    }

    val isDirectory: Boolean get() = isDirectory()
    val isFile: Boolean get() = exists() && !isDirectory()

    fun mkdirs(): Boolean =
        if (exists()) false else fm.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)

    fun mkdir(): Boolean = mkdirs()

    fun delete(): Boolean = exists() && fm.removeItemAtPath(path, null)

    fun deleteRecursively(): Boolean = !exists() || fm.removeItemAtPath(path, null)

    fun length(): Long = (fm.attributesOfItemAtPath(path, null)?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L

    fun lastModified(): Long =
        ((fm.attributesOfItemAtPath(path, null)?.get(NSFileModificationDate) as? NSDate)?.timeIntervalSince1970 ?: 0.0).times(1000).toLong()

    fun list(): Array<String>? =
        fm.contentsOfDirectoryAtPath(path, null)?.map { it.toString() }?.toTypedArray()

    fun listFiles(): Array<File>? = list()?.map { File(this, it) }?.toTypedArray()

    fun listFiles(filter: (File) -> Boolean): Array<File>? = listFiles()?.filter(filter)?.toTypedArray()

    /** مثل أندرويد: يستبدل الهدف إن وُجد (عملية ذرّية). */
    fun renameTo(dest: File): Boolean = rename(path, dest.path) == 0

    fun createNewFile(): Boolean {
        if (exists()) return false
        return fm.createFileAtPath(path, null, null)
    }

    fun readBytes(): ByteArray {
        val d = NSData.dataWithContentsOfFile(path) ?: throw FileNotFoundException(path)
        return d.toByteArray()
    }

    fun writeBytes(bytes: ByteArray) {
        if (!bytes.toNSData().writeToFile(path, atomically = false)) throw IOException("write failed: $path")
    }

    fun appendBytes(bytes: ByteArray) {
        writeBytes(if (exists()) readBytes() + bytes else bytes)
    }

    fun readText(): String = readBytes().decodeToString()

    fun writeText(text: String) = writeBytes(text.encodeToByteArray())

    fun appendText(text: String) = appendBytes(text.encodeToByteArray())

    fun readLines(): List<String> = readText().lines()

    fun copyTo(target: File, overwrite: Boolean = false): File {
        if (!exists()) throw FileNotFoundException(path)
        if (target.exists()) {
            if (!overwrite) throw IOException("The destination file already exists: ${target.path}")
            target.deleteRecursively()
        }
        target.parentFile?.mkdirs()
        if (!fm.copyItemAtPath(path, target.path, null)) throw IOException("copy failed: $path")
        return target
    }

    fun copyRecursively(target: File, overwrite: Boolean = false): Boolean {
        if (target.exists() && !overwrite) return false
        copyTo(target, overwrite = true)
        return true
    }

    fun toURI(): String = "file://$path"

    override fun equals(other: Any?): Boolean = other is File && other.path == path
    override fun hashCode(): Int = path.hashCode()
    override fun toString(): String = path
    override fun compareTo(other: File): Int = path.compareTo(other.path)
}

@OptIn(ExperimentalForeignApi::class)
fun NSData.toByteArray(): ByteArray {
    val n = length.toInt()
    val out = ByteArray(n)
    if (n > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
