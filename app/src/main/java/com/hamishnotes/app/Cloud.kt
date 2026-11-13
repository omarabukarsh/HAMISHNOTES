package com.hamishnotes.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * اتصال خفيف بـ Supabase عبر REST (بلا مكتبات إضافية):
 * جلسة المستخدم من رمز Google، واستدعاء دالة التحقق من الاشتراك.
 * لا يحمل التطبيق أي مفتاح سري: المفتاح العام (anon/publishable) فقط.
 */
internal object Cloud {
    class HttpError(val code: Int, val body: JSONObject?) : Exception("HTTP $code")

    private var sp: SharedPreferences? = null

    /** تجديد واحد للجلسة في كل مرة: طلبان معًا برمز التحديث نفسه قد يُبطل أحدهما الآخر فيُسجَّل الخروج. */
    private val refreshLock = Mutex()

    /** يُستدعى عندما يرفض الخادم تجديد الجلسة (أُبطلت أو حُذف الحساب)، ليُحدَّث الحساب ويُبلَّغ المستخدم. */
    var onSessionExpired: (() -> Unit)? = null

    val configured: Boolean get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    /** معرّف المستخدم في Supabase، أو null إن لم تُربط الجلسة بعد. */
    val userId: String? get() = sp?.getString("uid", null)

    fun init(context: Context) {
        sp = context.getSharedPreferences("cloud", Context.MODE_PRIVATE)
    }

    /**
     * يبدّل رمز Google (ID token) بجلسة Supabase.
     * rawNonce: القيمة الأصلية التي أُرسلت بصمتها (SHA-256) إلى Google، فيتحقق Supabase أن الرمز صُنع لهذا الدخول بعينه.
     */
    suspend fun signInWithGoogle(idToken: String, rawNonce: String?): Result<String> = runCatching {
        check(configured)
        val req = JSONObject().put("provider", "google").put("id_token", idToken)
        if (rawNonce != null) req.put("nonce", rawNonce)
        val r = post("/auth/v1/token?grant_type=id_token", req, bearer = null)
        save(r)
        r.getJSONObject("user").getString("id")
    }

    fun signOut() {
        sp?.edit()?.clear()?.apply()
    }

    private fun save(r: JSONObject) {
        val expiresIn = r.optLong("expires_in", 3600)
        sp?.edit()
            ?.putString("access", r.getString("access_token"))
            ?.putString("refresh", r.getString("refresh_token"))
            ?.putLong("access_until", System.currentTimeMillis() + (expiresIn - 60) * 1000)
            ?.putString("uid", r.getJSONObject("user").getString("id"))
            ?.apply()
    }

    /** رمز دخول صالح، يُجدَّد تلقائيًا من رمز التحديث. */
    private suspend fun accessToken(): String? {
        val p = sp ?: return null
        fun current(): String? {
            val access = p.getString("access", null)
            return if (access != null && System.currentTimeMillis() < p.getLong("access_until", 0)) access else null
        }
        current()?.let { return it }
        return refreshLock.withLock {
            // ربما جدّدها طلب آخر بينما كنا ننتظر
            current()?.let { return@withLock it }
            refreshSession(p)
        }
    }

    private suspend fun refreshSession(p: SharedPreferences): String? {
        val refresh = p.getString("refresh", null) ?: return null
        return try {
            val r = post("/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", refresh), bearer = null)
            save(r)
            r.getString("access_token")
        } catch (e: HttpError) {
            // رمز التحديث أُبطل (خروج من جهاز آخر أو حذف الحساب): نبدأ جلسة جديدة عند الدخول التالي،
            // ونبلغ التطبيق بدل فقدان الجلسة بصمت بينما الإعدادات ما زالت تُظهر الحساب مسجّلًا
            if (e.code in 400..499) {
                signOut()
                onSessionExpired?.invoke()
            }
            null
        }
    }

    /** يستدعي دالة Edge باسمها. */
    suspend fun function(name: String, body: JSONObject): JSONObject {
        val token = accessToken() ?: throw HttpError(401, null)
        return post("/functions/v1/$name", body, bearer = token)
    }

    private suspend fun post(path: String, body: JSONObject, bearer: String?): JSONObject = withContext(Dispatchers.IO) {
        val c = URL(BuildConfig.SUPABASE_URL.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            // apikey يكفي لطلبات الدخول؛ Authorization لرمز المستخدم فقط (فيعمل المفتاح القديم anon والجديد publishable)
            c.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            if (bearer != null) c.setRequestProperty("Authorization", "Bearer $bearer")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (code !in 200..299) throw HttpError(code, json)
            json ?: JSONObject()
        } finally {
            c.disconnect()
        }
    }
}
