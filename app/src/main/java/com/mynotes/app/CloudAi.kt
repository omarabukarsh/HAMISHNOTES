package com.mynotes.app

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * MyNotes Pro: الذكاء الاصطناعي عبر الإنترنت بمفتاح API من حساب المستخدم (Claude من Anthropic أو ChatGPT من OpenAI).
 * يُستعمل بدل النموذج الذي على الجهاز في: الشرح، قراءة الصفحة، ترتيب الملخص وتعديله، والبطاقات.
 * المفتاح يُحفظ داخل التطبيق على الجهاز فقط ويُرسل مباشرة إلى الشركة المختارة.
 */
object CloudAi {
    enum class Provider { LOCAL, CLAUDE, OPENAI }

    val claudeModels = listOf("claude-sonnet-5-5", "claude-haiku-5-5", "claude-opus-5-5")
    val openaiModels = listOf("gpt-5.5", "gpt-5.4-mini", "gpt-5.4")

    private fun prefs() = AppController.appContext.getSharedPreferences("mynotes_cloud_ai", Context.MODE_PRIVATE)

    var provider by mutableStateOf(Provider.LOCAL)
        private set
    var claudeKey by mutableStateOf("")
        private set
    var openaiKey by mutableStateOf("")
        private set
    var claudeModel by mutableStateOf(claudeModels[0])
        private set
    var openaiModel by mutableStateOf(openaiModels[0])
        private set
    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        val p = prefs()
        provider = try { Provider.valueOf(p.getString("provider", "LOCAL") ?: "LOCAL") } catch (t: Throwable) { Provider.LOCAL }
        claudeKey = p.getString("claude_key", "") ?: ""
        openaiKey = p.getString("openai_key", "") ?: ""
        claudeModel = p.getString("claude_model", claudeModels[0]) ?: claudeModels[0]
        openaiModel = p.getString("openai_model", openaiModels[0]) ?: openaiModels[0]
    }

    fun updateProvider(v: Provider) { provider = v; prefs().edit().putString("provider", v.name).apply() }
    fun updateClaudeKey(v: String) { claudeKey = v.trim(); prefs().edit().putString("claude_key", claudeKey).apply() }
    fun updateOpenaiKey(v: String) { openaiKey = v.trim(); prefs().edit().putString("openai_key", openaiKey).apply() }
    fun updateClaudeModel(v: String) { claudeModel = v.trim(); prefs().edit().putString("claude_model", claudeModel).apply() }
    fun updateOpenaiModel(v: String) { openaiModel = v.trim(); prefs().edit().putString("openai_model", openaiModel).apply() }

    /** الذكاء عبر الإنترنت مختار ومفتاحه موجود. */
    val active: Boolean
        get() {
            load()
            return when (provider) {
                Provider.CLAUDE -> claudeKey.isNotBlank()
                Provider.OPENAI -> openaiKey.isNotBlank()
                Provider.LOCAL -> false
            }
        }

    val label: String
        get() = when (provider) {
            Provider.CLAUDE -> "Claude"
            Provider.OPENAI -> "ChatGPT"
            Provider.LOCAL -> L("على الجهاز", "On device")
        }

    class ApiError(msg: String) : Exception(msg)

    /** طلب واحد بلا ذاكرة، مع تحديث جزئي أثناء الكتابة. image = صورة JPEG اختيارية. */
    suspend fun generate(system: String, prompt: String, image: ByteArray?, onPartial: (String) -> Unit): String =
        withContext(Dispatchers.IO) {
            when (provider) {
                Provider.OPENAI -> openai(system, prompt, image, onPartial)
                else -> claude(system, prompt, image, onPartial)
            }
        }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private suspend fun claude(system: String, prompt: String, image: ByteArray?, onPartial: (String) -> Unit): String {
        val content = JSONArray()
        if (image != null) {
            content.put(JSONObject().put("type", "image").put(
                "source", JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", b64(image))
            ))
        }
        content.put(JSONObject().put("type", "text").put("text", prompt))
        val body = JSONObject()
            .put("model", claudeModel)
            .put("max_tokens", 8000)
            .put("system", system)
            .put("stream", true)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val out = StringBuilder()
        stream(
            "https://api.anthropic.com/v1/messages",
            mapOf("x-api-key" to claudeKey, "anthropic-version" to "2023-06-01"),
            body
        ) { data ->
            val o = JSONObject(data)
            when (o.optString("type")) {
                "content_block_delta" -> {
                    val d = o.optJSONObject("delta")
                    if (d != null && d.optString("type") == "text_delta") {
                        out.append(d.optString("text"))
                        onPartial(out.toString())
                    }
                }
                "error" -> throw ApiError(o.optJSONObject("error")?.optString("message") ?: "error")
            }
        }
        return out.toString()
    }

    private suspend fun openai(system: String, prompt: String, image: ByteArray?, onPartial: (String) -> Unit): String {
        val user: Any = if (image == null) prompt else JSONArray()
            .put(JSONObject().put("type", "text").put("text", prompt))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64," + b64(image))))
        val body = JSONObject()
            .put("model", openaiModel)
            .put("stream", true)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", user)))
        val out = StringBuilder()
        stream(
            "https://api.openai.com/v1/chat/completions",
            mapOf("Authorization" to "Bearer $openaiKey"),
            body
        ) { data ->
            if (data == "[DONE]") return@stream
            val o = JSONObject(data)
            o.optJSONObject("error")?.let { throw ApiError(it.optString("message")) }
            val ch = o.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")
            val piece = ch?.optString("content", "") ?: ""
            if (piece.isNotEmpty() && piece != "null") {
                out.append(piece)
                onPartial(out.toString())
            }
        }
        return out.toString()
    }

    /** يرسل الطلب ويقرأ الرد المتدفق (Server-Sent Events) سطرًا سطرًا. */
    private suspend fun stream(url: String, headers: Map<String, String>, body: JSONObject, onData: (String) -> Unit) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 120_000
            setRequestProperty("content-type", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                val msg = try {
                    JSONObject(err).optJSONObject("error")?.optString("message")
                } catch (t: Throwable) {
                    null
                }
                throw ApiError(
                    when (code) {
                        401 -> L("المفتاح غير صحيح (401)", "Invalid API key (401)")
                        402, 429 -> L("تجاوزت الحد أو نفد الرصيد ($code): ", "Limit reached or no credit ($code): ") + (msg ?: "")
                        404 -> L("اسم النموذج غير موجود (404): ", "Model not found (404): ") + (msg ?: "")
                        else -> "HTTP $code: " + (msg ?: err.take(200))
                    }
                )
            }
            conn.inputStream.bufferedReader().use { r ->
                while (true) {
                    coroutineContext.ensureActive()
                    val line = r.readLine() ?: break
                    if (line.startsWith("data:")) onData(line.removePrefix("data:").trim())
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    /** اختبار الاتصال بالمفتاح والنموذج المختارين. */
    suspend fun test(): String = try {
        val r = generate("Reply with the single word: OK", "Test", null) {}
        L("يعمل ✓ ", "Works ✓ ") + "(" + r.trim().take(20) + ")"
    } catch (e: ApiError) {
        L("فشل: ", "Failed: ") + (e.message ?: "")
    } catch (t: Throwable) {
        L("فشل الاتصال: تأكد من الإنترنت", "Connection failed: check the internet") + " (" + (t.message ?: t.javaClass.simpleName) + ")"
    }
}
