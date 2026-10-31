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

    private var signerCache: Boolean? = null

    /**
     * هل وُقّع التطبيق بمفتاحنا (مفتاح Google Play أو مفتاح الرفع)؟
     * النسخة المعدّلة يعاد توقيعها بمفتاح آخر، فتفشل هنا.
     */
    fun signerOk(context: Context): Boolean {
        signerCache?.let { return it }
        val allowed = BuildConfig.SIGNERS.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (allowed.isEmpty()) return true
        val ok = try {
            signerMatches(context, allowed)
        } catch (e: Throwable) {
            false
        }
        signerCache = ok
        return ok
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
