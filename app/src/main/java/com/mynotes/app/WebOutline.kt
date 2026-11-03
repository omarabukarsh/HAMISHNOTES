package com.mynotes.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * بنية الموضوع من الإنترنت (اختياري، مطفأ افتراضيًا): عناوين مقالة ويكيبيديا عن موضوع المستند ومستوياتها.
 * تُستعمل فقط لمعرفة أي عناوين المستند رئيسية وأيها فرعية؛ لا يُضاف من الإنترنت أي عنوان أو نص إلى الملخص
 * (العنوان يُقبل فقط إن كان سطرًا موجودًا في صفحة المستند). يُرسل فقط اسم الموضوع (عنوان المستند أو أول عنوان فيه)،
 * لا الملاحظات ولا التظليل. النتيجة تُخزَّن لكل مستند، فلا يتكرر الطلب.
 */
object WebOutline {
    private fun prefs() = AppController.appContext.getSharedPreferences("mynotes_ai", Context.MODE_PRIVATE)

    var enabled by mutableStateOf(prefs().getBoolean("web_outline", false))
        private set

    fun toggle(on: Boolean) {
        enabled = on
        prefs().edit().putBoolean("web_outline", on).apply()
    }

    /** آخر موضوع وُجد (للعرض في الرسالة)، أو null. */
    var lastTopic: String? = null
        private set

    private fun dir() = File(AppController.appContext.filesDir, "ai/web").also { it.mkdirs() }

    private fun isArabic(s: String) = s.count { it in '؀'..'ۿ' } > s.count { it.isLetter() } / 3

    /** اسم الموضوع من اسم الملف: بلا الامتداد والأرقام وكلمات مثل «محاضرة» و lecture. */
    fun topicFrom(name: String): String =
        name.substringBeforeLast('.')
            .replace(Regex("[_\\-]+"), " ")
            .replace(Regex("(?i)\\b(lecture|lec|chapter|ch|part|slides?|week|unit|final|notes?|pdf)\\b"), " ")
            .replace(Regex("(محاضرة|المحاضرة|الفصل|فصل|الجزء|جزء|ملخص|الوحدة|وحدة)"), " ")
            .replace(Regex("\\d+"), " ")
            .replace(Regex("\\s+"), " ").trim()

    private fun get(url: String): JSONObject? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "HamishPro/1.0 (Android study notes app)")
        try {
            if (c.responseCode != 200) null else JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        } finally {
            c.disconnect()
        }
    } catch (t: Throwable) {
        null
    }

    /** عناوين المقالة: (المستوى 1 أو 2 أو 3، النص). */
    private fun sections(lang: String, query: String): Pair<String, List<Pair<Int, String>>>? {
        val q = URLEncoder.encode(query, "UTF-8")
        val search = get("https://$lang.wikipedia.org/w/api.php?action=query&list=search&srsearch=$q&srlimit=1&format=json") ?: return null
        val hits = search.optJSONObject("query")?.optJSONArray("search") ?: return null
        if (hits.length() == 0) return null
        val title = hits.getJSONObject(0).optString("title").ifBlank { return null }
        val parse = get("https://$lang.wikipedia.org/w/api.php?action=parse&page=${URLEncoder.encode(title, "UTF-8")}&prop=sections&redirects=1&format=json")
            ?: return null
        val arr = parse.optJSONObject("parse")?.optJSONArray("sections") ?: return null
        val out = ArrayList<Pair<Int, String>>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val line = o.optString("line").replace(Regex("<[^>]+>"), "").trim()
            val lvl = o.optString("toclevel").toIntOrNull() ?: continue
            if (line.isNotEmpty()) out.add(lvl to line)
        }
        return if (out.isEmpty()) null else title to out
    }

    /**
     * عناوين الموضوع ومستوياتها لهذا المستند (من الذاكرة، وإلا من الإنترنت). topics: أسماء مرشحة بالترتيب.
     * يعيد خريطة: نص العنوان ← مستواه (1 رئيسي، 2 فرعي). فارغة إن لم يُفعَّل أو لم يُوجد شيء أو لا إنترنت.
     */
    suspend fun levels(docId: String, topics: List<String>): Map<String, Int> {
        if (!enabled) return emptyMap()
        val f = File(dir(), "$docId.json")
        val cached = withContext(Dispatchers.IO) { if (f.exists()) try { JSONObject(f.readText()) } catch (t: Throwable) { null } else null }
        val key = topics.joinToString("|")
        if (cached != null && cached.optString("k") == key) {
            lastTopic = cached.optString("t").ifBlank { null }
            val a = cached.optJSONArray("s")
            val m = HashMap<String, Int>()
            if (a != null) for (i in 0 until a.length()) {
                val x = a.getJSONArray(i)
                m[x.getString(1)] = x.getInt(0)
            }
            return m
        }
        val found = withContext(Dispatchers.IO) {
            var r: Pair<String, List<Pair<Int, String>>>? = null
            for (t in topics.map { it.trim() }.filter { it.count { ch -> ch.isLetter() } >= 3 }.distinct().take(3)) {
                val langs = if (isArabic(t)) listOf("ar", "en") else listOf("en", "ar")
                for (lang in langs) {
                    r = sections(lang, t)
                    if (r != null) break
                }
                if (r != null) break
            }
            r
        }
        lastTopic = found?.first
        val m = LinkedHashMap<String, Int>()
        found?.second?.forEach { (lvl, line) -> if (!m.containsKey(line)) m[line] = lvl.coerceIn(1, 3) }
        // تُحفظ النتيجة إن وُجدت فلا يتكرر الطلب لنفس المستند ونفس الموضوع (الفشل لا يُحفظ: قد يكون الإنترنت مقطوعًا)
        if (found != null) withContext(Dispatchers.IO) {
            try {
                val a = org.json.JSONArray()
                m.forEach { (k, v) -> a.put(org.json.JSONArray().put(v).put(k)) }
                f.writeText(JSONObject().put("k", key).put("t", found?.first ?: "").put("s", a).toString())
            } catch (t: Throwable) {
            }
        }
        return m
    }
}
