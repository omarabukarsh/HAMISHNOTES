package com.mynotes.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
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

    val configured: Boolean get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    /** معرّف المستخدم في Supabase، أو null إن لم تُربط الجلسة بعد. */
    val userId: String? get() = sp?.getString("uid", null)

    fun init(context: Context) {
        sp = context.getSharedPreferences("cloud", Context.MODE_PRIVATE)
    }

    /** يبدّل رمز Google (ID token) بجلسة Supabase. */
    suspend fun signInWithGoogle(idToken: String): Result<String> = runCatching {
        check(configured)
        val r = post("/auth/v1/token?grant_type=id_token", JSONObject().put("provider", "google").put("id_token", idToken), bearer = null)
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
        val access = p.getString("access", null)
        if (access != null && System.currentTimeMillis() < p.getLong("access_until", 0)) return access
        val refresh = p.getString("refresh", null) ?: return null
        return try {
            val r = post("/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", refresh), bearer = null)
            save(r)
            r.getString("access_token")
        } catch (e: HttpError) {
            // رمز التحديث أُبطل (خروج من جهاز آخر أو حذف الحساب): نبدأ جلسة جديدة عند الدخول التالي
            if (e.code in 400..499) signOut()
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
            c.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            c.setRequestProperty("Authorization", "Bearer " + (bearer ?: BuildConfig.SUPABASE_ANON_KEY))
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
