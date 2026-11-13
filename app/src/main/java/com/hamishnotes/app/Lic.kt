package com.hamishnotes.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * قسيمة الاشتراك: يصدرها الخادم (Supabase) بعد التحقق من الشراء ومن سلامة التطبيق،
 * موقّعة بمفتاح ES256 خاص لا يوجد إلا في الخادم. التطبيق يحمل المفتاح العام فقط،
 * فلا يمكن صنع قسيمة مزوّرة، ولا نقلها إلى جهاز آخر أو حساب آخر.
 *
 * الصيغة: "v1.<payload base64url>.<توقيع r||s base64url>"
 */
internal object Lic {
    class Grant(
        val sub: String,
        val dev: String,
        val pkg: String,
        /** تعمل القسيمة دون إنترنت حتى هذا الوقت، ثم تُجدَّد. */
        val until: Long,
        /** نهاية الاشتراك نفسه في Google Play. */
        val exp: Long,
        val trial: Boolean,
        val renewing: Boolean,
        val iat: Long,
    )

    // المفتاح العام (SPKI) مقسّمًا، فلا يظهر كسلسلة واحدة يسهل البحث عنها واستبدالها
    private val K = arrayOf(
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE",
        "uCgZamybxsqReu8tBxWGuWaOGmIVT+wKMY3ita0NOURj",
        "ES3+MkWA+ziLS4HTzYmrMQpIagT9Gq4BtyYbyn2eNQ==",
    )

    private fun b64(s: String): ByteArray = Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    /** يتحقق من التوقيع ويقرأ القسيمة؛ null إن كانت مزوّرة أو تالفة. */
    fun parse(token: String?): Grant? {
        if (token.isNullOrBlank()) return null
        val parts = token.split('.')
        if (parts.size != 3 || parts[0] != "v1") return null
        return try {
            val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.decode(K.joinToString(""), Base64.DEFAULT)))
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(key)
            sig.update("${parts[0]}.${parts[1]}".toByteArray())
            if (!sig.verify(der(b64(parts[2])))) return null
            val j = JSONObject(String(b64(parts[1])))
            Grant(
                sub = j.getString("sub"), dev = j.getString("dev"), pkg = j.getString("pkg"),
                until = j.getLong("until"), exp = j.getLong("exp"),
                trial = j.optBoolean("trial"), renewing = j.optBoolean("renew"), iat = j.getLong("iat"),
            )
        } catch (e: Throwable) {
            null
        }
    }

    /** توقيع r||s (كما يخرجه WebCrypto في الخادم) ← صيغة DER التي يقرؤها Java. */
    private fun der(raw: ByteArray): ByteArray {
        require(raw.size == 64)
        fun int(b: ByteArray): ByteArray {
            var i = 0
            while (i < b.size - 1 && b[i] == 0.toByte()) i++
            val t = b.copyOfRange(i, b.size)
            val v = if (t[0] < 0) byteArrayOf(0) + t else t
            return byteArrayOf(0x02, v.size.toByte()) + v
        }
        val body = int(raw.copyOfRange(0, 32)) + int(raw.copyOfRange(32, 64))
        return byteArrayOf(0x30, body.size.toByte()) + body
    }

    fun sha256(s: String): String = sha256(s.toByteArray())

    fun sha256(b: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    /** معرّف ثابت لهذا الجهاز وهذا التطبيق، تُربط به القسيمة. */
    @SuppressLint("HardwareIds")
    fun device(context: Context): String {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "none"
        return sha256("hamish|$id|${context.packageName}").take(32)
    }

    // لا نحفظ النتيجة للأبد: نعيد الفحص بعد مدة، فلا تكفي ضربة واحدة من أداة حقن لتعطيله دائمًا
    @Volatile private var signerCache: Boolean? = null
    @Volatile private var signerCacheAt = 0L
    private const val SIGNER_TTL = 5 * 60 * 1000L

    // فحص الأرشيف يحلّل ملف الـ APK كله (بطيء)، فلا يجري أبدًا على الخيط الرئيسي:
    // نتيجته تُحسب في الخلفية وتُخزَّن، وحتى تجهز أول مرة يكفي الفحص الحي.
    @Volatile private var archiveCache: Boolean? = null
    @Volatile private var archiveCacheAt = 0L
    private val archiveRunning = java.util.concurrent.atomic.AtomicBoolean(false)

    /** يُستدعى (من خيط خلفي) إن كشف فحص الأرشيف في الخلفية توقيعًا غريبًا، ليعيد Premium التقييم. */
    @Volatile var onSignerMismatch: (() -> Unit)? = null

    private fun allowedSigners(): List<String> =
        BuildConfig.SIGNERS.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    /**
     * هل وُقّع التطبيق بمفتاحنا (مفتاح Google Play أو مفتاح الرفع)؟
     * النسخة المعدّلة يعاد توقيعها بمفتاح آخر، فتفشل هنا.
     *
     * fresh=false (الاستعمال العادي، آمن على الخيط الرئيسي): الفحص الحي + آخر نتيجة لفحص الأرشيف.
     * fresh=true: كل شيء من جديد ومباشرة، ويجب استدعاؤها خارج الخيط الرئيسي.
     */
    fun signerOk(context: Context, fresh: Boolean = false): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        if (!fresh) signerCache?.let { if (now - signerCacheAt < SIGNER_TTL) return it }
        val allowed = allowedSigners()
        if (allowed.isEmpty()) return true
        val ok = try {
            // فحصان مستقلان: سجل التثبيت الحي في الذاكرة، وشهادة ملف الـ APK نفسه على القرص.
            // أدوات إعادة التوقيع تزيّف جواب PackageManager الحي غالبًا، لكنها نادرًا ما تزيّف قراءة الأرشيف.
            signerMatches(context, allowed) && (if (fresh) archiveNow(context, allowed) else archiveCached(context, allowed, now))
        } catch (e: Throwable) {
            false
        }
        signerCache = ok
        signerCacheAt = now
        return ok
    }

    /** يبدأ فحص الأرشيف في الخلفية مبكرًا (عند فتح التطبيق)، فتكون نتيجته جاهزة حين تُطلب. */
    fun warmUp(context: Context) {
        val allowed = allowedSigners()
        if (allowed.isNotEmpty()) refreshArchiveAsync(context.applicationContext, allowed)
    }

    private fun archiveNow(context: Context, allowed: List<String>): Boolean {
        val r = archiveSignerMatches(context, allowed)
        archiveCache = r
        archiveCacheAt = android.os.SystemClock.elapsedRealtime()
        return r
    }

    private fun archiveCached(context: Context, allowed: List<String>, now: Long): Boolean {
        val cached = archiveCache
        if (cached == null || now - archiveCacheAt >= SIGNER_TTL) refreshArchiveAsync(context.applicationContext, allowed)
        return cached ?: true
    }

    private fun refreshArchiveAsync(app: Context, allowed: List<String>) {
        if (!archiveRunning.compareAndSet(false, true)) return
        Thread({
            try {
                val before = archiveCache
                val r = archiveNow(app, allowed)
                if (!r && before != false) {
                    // نتيجة الفحص الحي المخزّنة لم تعد كافية: يُعاد الفحص كاملًا عند الطلب التالي
                    signerCache = null
                    onSignerMismatch?.invoke()
                }
            } catch (_: Throwable) {
            } finally {
                archiveRunning.set(false)
            }
        }, "hamish-verify").apply { isDaemon = true }.start()
    }

    /** الفحص الثاني: شهادة ملف الـ APK على القرص مباشرة (sourceDir)، لا سجل التثبيت في الذاكرة. */
    @Suppress("DEPRECATION")
    private fun archiveSignerMatches(context: Context, allowed: List<String>): Boolean = try {
        val path = context.applicationInfo.sourceDir
        val pm = context.packageManager
        when {
            // تعذّرت قراءة مسار الأرشيف: لا نعاقب المستخدم الشرعي، ونكتفي بالفحص الحي
            path == null -> true
            Build.VERSION.SDK_INT >= 28 -> {
                val si = pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNING_CERTIFICATES)?.signingInfo
                // null: تعذّر تحليل الأرشيف (قيود مصنّع مثلًا)؛ نعود إلى الفحص الحي وحده
                if (si == null) true
                else {
                    val s = si.apkContentsSigners.orEmpty()
                    s.isNotEmpty() && s.all { sha256(it.toByteArray()) in allowed }
                }
            }
            else -> {
                val s = pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNATURES)?.signatures.orEmpty()
                s.isNotEmpty() && s.all { sha256(it.toByteArray()) in allowed }
            }
        }
    } catch (e: Throwable) {
        true // تعذّر فحص الأرشيف: نعود إلى الفحص الحي وحده بدل معاقبة المستخدم الشرعي
    }

    /** عدة موقّعين: يجب أن يكونوا كلهم لنا. موقّع واحد: يكفي أن يكون في سلسلته (عند تدوير المفتاح) مفتاح لنا. */
    @Suppress("DEPRECATION")
    private fun signerMatches(context: Context, allowed: List<String>): Boolean {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= 28) {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo ?: return false
            return if (info.hasMultipleSigners()) {
                val s = info.apkContentsSigners.orEmpty()
                s.isNotEmpty() && s.all { sha256(it.toByteArray()) in allowed }
            } else {
                info.signingCertificateHistory.orEmpty().any { sha256(it.toByteArray()) in allowed }
            }
        }
        val s = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures.orEmpty()
        return s.isNotEmpty() && s.all { sha256(it.toByteArray()) in allowed }
    }

    /** هل ثُبّت التطبيق من Google Play؟ إشارة مساعدة فقط (Play Integrity هو الحكم الحقيقي). */
    @Suppress("DEPRECATION")
    fun fromPlay(context: Context): Boolean = try {
        val installer = if (Build.VERSION.SDK_INT >= 30) {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            context.packageManager.getInstallerPackageName(context.packageName)
        }
        installer == "com.android.vending"
    } catch (e: Throwable) {
        false
    }
}
