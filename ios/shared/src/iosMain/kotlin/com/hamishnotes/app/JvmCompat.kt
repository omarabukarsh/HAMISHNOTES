package com.hamishnotes.app

import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.native.identityHashCode
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSDate
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSRecursiveLock
import platform.Foundation.timeIntervalSince1970

/*
 * ما يوفره Java تلقائيًا في أندرويد ويغيب عن Kotlin في iOS:
 * System.currentTimeMillis، Math، synchronized، @Volatile، String.format، toByteArray…
 * بنفس الأسماء حتى يبقى الكود المشترك مطابقًا لنسخة أندرويد.
 */

typealias Volatile = kotlin.concurrent.Volatile

object System {
    fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
    fun nanoTime(): Long = (NSProcessInfo.processInfo.systemUptime * 1e9).toLong()
    @OptIn(kotlin.native.runtime.NativeRuntimeApi::class)
    fun gc() = kotlin.native.runtime.GC.collect()
}

object Runtime {
    private val rt = RuntimeInfo()
    fun getRuntime(): RuntimeInfo = rt

    class RuntimeInfo {
        /** ميزانية الذاكرة: ربع ذاكرة الجهاز (iPad يغلق التطبيق إن تجاوز حدًا قريبًا من النصف). */
        fun maxMemory(): Long = (NSProcessInfo.processInfo.physicalMemory.toLong() / 4L).coerceAtMost(1536L * 1024L * 1024L)
        fun availableProcessors(): Int = NSProcessInfo.processInfo.activeProcessorCount.toInt()
    }
}

object Math {
    const val PI = kotlin.math.PI
    const val E = kotlin.math.E
    fun toDegrees(r: Double): Double = r * 180.0 / PI
    fun toRadians(d: Double): Double = d * PI / 180.0
    fun abs(v: Int) = kotlin.math.abs(v)
    fun abs(v: Float) = kotlin.math.abs(v)
    fun abs(v: Double) = kotlin.math.abs(v)
    fun max(a: Int, b: Int) = kotlin.math.max(a, b)
    fun max(a: Float, b: Float) = kotlin.math.max(a, b)
    fun min(a: Int, b: Int) = kotlin.math.min(a, b)
    fun min(a: Float, b: Float) = kotlin.math.min(a, b)
    fun sqrt(v: Double) = kotlin.math.sqrt(v)
    fun hypot(a: Double, b: Double) = kotlin.math.hypot(a, b)
    fun atan2(y: Double, x: Double) = kotlin.math.atan2(y, x)
    fun round(v: Float): Int = kotlin.math.round(v).toInt()
    fun round(v: Double): Long = kotlin.math.round(v).toLong()
    fun floorMod(a: Int, b: Int): Int = ((a % b) + b) % b
    fun floorMod(a: Long, b: Long): Long = ((a % b) + b) % b
}

private val locksGuard = NSRecursiveLock()
private val locks = HashMap<Any, NSRecursiveLock>()

/** بديل synchronized في Java: قفل متكرر لكل كائن. */
inline fun <R> synchronized(lock: Any, block: () -> R): R {
    val l = lockFor(lock)
    l.lock()
    try {
        return block()
    } finally {
        l.unlock()
    }
}

@PublishedApi
internal fun lockFor(o: Any): NSRecursiveLock {
    locksGuard.lock()
    try {
        return locks.getOrPut(LockKey(o)) { NSRecursiveLock() }
    } finally {
        locksGuard.unlock()
    }
}

/** مفتاح بهوية الكائن (لا بمساواته). */
private class LockKey(val o: Any) {
    override fun equals(other: Any?) = other is LockKey && other.o === o
    @OptIn(kotlin.experimental.ExperimentalNativeApi::class)
    override fun hashCode() = o.identityHashCode()
}

object Charsets {
    val UTF_8 = "UTF-8"
}

fun String.toByteArray(@Suppress("UNUSED_PARAMETER") charset: String = Charsets.UTF_8): ByteArray = encodeToByteArray()

// ---------------------------------------------------------------- String.format

fun String.format(vararg args: Any?): String = jformat(this, args)
fun String.format(@Suppress("UNUSED_PARAMETER") locale: Locale, vararg args: Any?): String = jformat(this, args)
fun String.Companion.format(fmt: String, vararg args: Any?): String = jformat(fmt, args)
fun String.Companion.format(@Suppress("UNUSED_PARAMETER") locale: Locale, fmt: String, vararg args: Any?): String = jformat(fmt, args)

private val spec = Regex("%(\\d+\\$)?([-#+ 0,(]*)(\\d+)?(\\.\\d+)?([a-zA-Z%])")

/** تنسيق printf بالحالات المستخدمة في التطبيق: d s f x X c %. */
private fun jformat(fmt: String, args: Array<out Any?>): String {
    var next = 0
    return spec.replace(fmt) { m ->
        val conv = m.groupValues[5]
        if (conv == "%") return@replace "%"
        if (conv == "n") return@replace "\n"
        val idx = m.groupValues[1].takeIf { it.isNotEmpty() }?.dropLast(1)?.toInt()?.minus(1) ?: next++
        val flags = m.groupValues[2]
        val width = m.groupValues[3].toIntOrNull() ?: 0
        val prec = m.groupValues[4].takeIf { it.isNotEmpty() }?.drop(1)?.toInt()
        val a = args.getOrNull(idx)
        var body = when (conv) {
            "d" -> {
                val v = (a as? Number)?.toLong() ?: 0L
                val s = abs(v).toString().let { if (',' in flags) group(it) else it }
                (if (v < 0) "-" else if ('+' in flags) "+" else "") + s
            }
            "f" -> {
                val v = (a as? Number)?.toDouble() ?: 0.0
                fixed(v, prec ?: 6, ',' in flags).let { if ('+' in flags && v >= 0) "+$it" else it }
            }
            "x", "X" -> {
                val v = when (a) {
                    is Int -> a.toUInt().toString(16)
                    is Long -> a.toULong().toString(16)
                    is Byte -> a.toUByte().toString(16)
                    is Short -> a.toUShort().toString(16)
                    else -> (a as? Number)?.toLong()?.toString(16) ?: "0"
                }
                if (conv == "X") v.uppercase() else v
            }
            "c" -> (a as? Char)?.toString() ?: ((a as? Number)?.toInt()?.toChar()?.toString() ?: "")
            "b" -> (a != null && a != false).toString()
            else -> a.toString().let { if (prec != null) it.take(prec) else it }
        }
        if (body.length < width) {
            body = when {
                '-' in flags -> body.padEnd(width)
                '0' in flags && conv != "s" -> {
                    val neg = body.startsWith("-") || body.startsWith("+")
                    if (neg) body[0] + body.substring(1).padStart(width - 1, '0') else body.padStart(width, '0')
                }
                else -> body.padStart(width)
            }
        }
        body
    }
}

private fun group(digits: String): String {
    val sb = StringBuilder()
    digits.forEachIndexed { i, ch ->
        if (i > 0 && (digits.length - i) % 3 == 0) sb.append(',')
        sb.append(ch)
    }
    return sb.toString()
}

private fun fixed(v: Double, prec: Int, grouping: Boolean): String {
    if (v.isNaN()) return "NaN"
    if (v.isInfinite()) return if (v > 0) "Infinity" else "-Infinity"
    val f = 10.0.pow(prec)
    val r = (abs(v) * f).roundToLong()
    val ip = (r / f.toLong()).toString().let { if (grouping) group(it) else it }
    val fp = if (prec > 0) "." + (r % f.toLong()).toString().padStart(prec, '0') else ""
    val neg = v < 0 && r != 0L
    return (if (neg) "-" else "") + ip + fp
}

open class SecurityException(message: String? = null) : RuntimeException(message)

fun <K, V> MutableMap<K, V>.putIfAbsent(key: K, value: V): V? {
    val cur = get(key)
    if (cur == null) put(key, value)
    return cur
}

/** رقم الإصدار يُكتب من Info.plist (يزيد مع كل بناء على GitHub Actions). */
object BuildConfig {
    val VERSION_NAME: String
        get() = (platform.Foundation.NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String) ?: "1.0"
    /** تسجيل الدخول بحساب Google غير متاح بعد في نسخة iPad. */
    const val GOOGLE_WEB_CLIENT_ID = ""
    /** مثل نسخة أندرويد التجريبية: Premium يُفعَّل محليًا دون دفع. */
    const val TEST_PREMIUM = true
    @OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)
    @kotlin.native.HiddenFromObjC
    const val DEBUG = false
}

/** SHA-256 (بديل MessageDigest) عبر CommonCrypto. */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
fun sha256(data: ByteArray): ByteArray {
    val out = UByteArray(platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH)
    data.usePinned { src ->
        out.usePinned { dst ->
            platform.CoreCrypto.CC_SHA256(
                if (data.isEmpty()) null else src.addressOf(0),
                data.size.toUInt(),
                dst.addressOf(0)
            )
        }
    }
    return out.asByteArray()
}
