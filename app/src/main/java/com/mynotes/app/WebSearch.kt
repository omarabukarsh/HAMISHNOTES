package com.mynotes.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * بحث الإنترنت للذكاء الاصطناعي (اختياري، مطفأ افتراضيًا).
 * المصادر بالترتيب: ويكيبيديا (بلا مفتاح)، ثم محرك بحث: Brave إن وضع المستخدم مفتاحه في الإعدادات، وإلا DuckDuckGo بلا مفتاح.
 * تُفضَّل المصادر المعتمدة (موسوعات، مواقع علمية وجامعية وحكومية)، وتُهمل مواقع التواصل والمنتديات.
 * ما يُرسل للإنترنت: نص البحث فقط (اسم الموضوع وعنوان القسم، أو كلمات النص المحدد في «اشرح»).
 */
object WebSearch {
    class Hit(val title: String, val url: String, val text: String)
    class Evidence(val text: String, val sources: List<Pair<String, String>>) {
        val empty get() = text.isBlank()
    }

    private fun prefs() = AppController.appContext.getSharedPreferences("mynotes_ai", Context.MODE_PRIVATE)

    /** تصحيح الملخص وشرح النص بالإنترنت. */
    var enabled by mutableStateOf(prefs().getBoolean("web_search", false))
        private set

    fun toggle(on: Boolean) {
        enabled = on
        prefs().edit().putBoolean("web_search", on).apply()
    }

    /** مفتاح Brave Search الاختياري (يضعه المستخدم في الإعدادات). */
    var braveKey by mutableStateOf(prefs().getString("brave_key", "") ?: "")
        private set

    fun saveBraveKey(k: String) {
        braveKey = k.trim()
        prefs().edit().putString("brave_key", braveKey).apply()
    }

    private fun dir() = File(AppController.appContext.filesDir, "ai/web/q").also { it.mkdirs() }

    private fun http(url: String, headers: Map<String, String> = emptyMap()): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 9000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android 14; Mobile) HamishPro/1.0")
        c.setRequestProperty("Accept-Language", "ar,en;q=0.8")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        try {
            if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { r ->
                val sb = StringBuilder()
                val buf = CharArray(8192)
                while (sb.length < 400_000) {
                    val n = r.read(buf)
                    if (n < 0) break
                    sb.append(buf, 0, n)
                }
                sb.toString()
            }
        } finally {
            c.disconnect()
        }
    } catch (t: Throwable) {
        null
    }

    private fun isArabic(s: String) = s.count { it in '؀'..'ۿ' } > s.count { it.isLetter() } / 3

    private fun plain(html: String): String = android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_COMPACT).toString()
        .replace(Regex("\\s+"), " ").trim()

    private val BAD = listOf("facebook.", "instagram.", "tiktok.", "twitter.", "x.com", "pinterest.", "quora.", "reddit.", "youtube.", "t.me", "snapchat.", "linkedin.")
    private val GOOD = listOf(
        "wikipedia.org", "britannica.com", "who.int", "nih.gov", "ncbi.nlm", "medlineplus", "mayoclinic", "cdc.gov", "nhs.uk",
        "khanacademy", "springer", "nature.com", "sciencedirect", "cochrane", "msdmanuals", "clevelandclinic", "webmd", "drugs.com", "fda.gov",
        "wikibooks", "wikidoc", "statpearls", "merriam-webster", "oxford", "cambridge.org", "ox.ac.uk", ".edu", ".gov", ".ac."
    )

    private fun host(u: String): String = try { URL(u).host.lowercase() } catch (t: Throwable) { "" }
    private fun rank(u: String): Int {
        val h = host(u)
        return when {
            BAD.any { h.contains(it) } -> -1
            GOOD.any { h.contains(it) } -> 2
            else -> 1
        }
    }

    /** ويكيبيديا: مقدمة أقرب مقالتين (بلا مفتاح، موثوق، يدعم العربية والإنجليزية). */
    private fun wikipedia(lang: String, q: String): List<Hit> {
        val s = http(
            "https://$lang.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=${URLEncoder.encode(q, "UTF-8")}&gsrlimit=2" +
                "&prop=extracts&exintro=1&explaintext=1&exlimit=2&exchars=1400&format=json&redirects=1"
        ) ?: return emptyList()
        return try {
            val pages = JSONObject(s).optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
            val out = ArrayList<Pair<Int, Hit>>()
            for (k in pages.keys()) {
                val p = pages.getJSONObject(k)
                val ex = p.optString("extract").trim()
                if (ex.length < 80) continue
                out.add(p.optInt("index", 9) to Hit(p.optString("title") + " — Wikipedia", "https://$lang.wikipedia.org/?curid=$k", ex))
            }
            out.sortedBy { it.first }.map { it.second }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    private fun brave(q: String): List<Hit> {
        if (braveKey.isBlank()) return emptyList()
        val s = http(
            "https://api.search.brave.com/res/v1/web/search?count=6&q=${URLEncoder.encode(q, "UTF-8")}",
            mapOf("X-Subscription-Token" to braveKey, "Accept" to "application/json")
        ) ?: return emptyList()
        return try {
            val a = JSONObject(s).optJSONObject("web")?.optJSONArray("results") ?: return emptyList()
            (0 until a.length()).map { a.getJSONObject(it) }.map { Hit(plain(it.optString("title")), it.optString("url"), plain(it.optString("description"))) }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    private fun duck(q: String): List<Hit> {
        val s = http("https://html.duckduckgo.com/html/?q=${URLEncoder.encode(q, "UTF-8")}") ?: return emptyList()
        val links = Regex("class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL).findAll(s).toList()
        val snips = Regex("class=\"result__snippet\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL).findAll(s).toList()
        val out = ArrayList<Hit>()
        for ((i, m) in links.withIndex()) {
            var u = m.groupValues[1].replace("&amp;", "&")
            Regex("[?&]uddg=([^&]+)").find(u)?.let { u = URLDecoder.decode(it.groupValues[1], "UTF-8") }
            if (u.startsWith("//")) u = "https:$u"
            if (!u.startsWith("http")) continue
            out.add(Hit(plain(m.groupValues[2]), u, plain(snips.getOrNull(i)?.groupValues?.get(1) ?: "")))
            if (out.size >= 6) break
        }
        return out
    }

    /** نص الصفحة الأساسي: فقرات طويلة فقط، حتى max حرفًا. */
    private fun pageText(url: String, max: Int): String {
        val s = http(url) ?: return ""
        val body = s.replace(Regex("(?is)<(script|style|nav|header|footer|aside|form|noscript)[^>]*>.*?</\\1>"), " ")
        val paras = Regex("(?is)<p[^>]*>(.*?)</p>").findAll(body).map { plain(it.groupValues[1]) }.filter { it.length >= 70 }.toList()
        val sb = StringBuilder()
        for (p in paras) {
            if (sb.length + p.length > max) break
            sb.append(p).append(' ')
        }
        return sb.toString().trim()
    }

    /**
     * أدلة للسؤال: مقاطع من مصادر معتمدة مرقّمة. تُخزَّن بحسب النص فلا يتكرر الطلب.
     * تعيد أدلة فارغة إن لم يُفعَّل البحث أو لا اتصال أو لم يُوجد شيء.
     */
    suspend fun evidence(query: String, maxChars: Int = 1600, pages: Int = 1): Evidence {
        val q = query.replace(Regex("\\s+"), " ").trim().take(160)
        if (!enabled || q.count { it.isLetter() } < 3) return Evidence("", emptyList())
        val f = File(dir(), AiStructure.hash("$q|$maxChars|$pages") + ".json")
        withContext(Dispatchers.IO) {
            try {
                if (f.exists() && System.currentTimeMillis() - f.lastModified() < 7L * 24 * 3600 * 1000) {
                    val o = JSONObject(f.readText())
                    val src = o.getJSONArray("s")
                    return@withContext Evidence(o.getString("t"), (0 until src.length()).map { src.getJSONArray(it).let { a -> a.getString(0) to a.getString(1) } })
                }
            } catch (t: Throwable) {
            }
            null
        }?.let { return it }
        val ev = withContext(Dispatchers.IO) {
            val ar = isArabic(q)
            val hits = ArrayList<Hit>()
            hits.addAll(wikipedia(if (ar) "ar" else "en", q))
            if (hits.isEmpty()) hits.addAll(wikipedia(if (ar) "en" else "ar", q))
            val engine = brave(q).ifEmpty { duck(q) }
            val ranked = engine.filter { rank(it.url) > 0 && hits.none { h -> host(h.url) == host(it.url) } }.sortedByDescending { rank(it.url) }
            var fetched = 0
            for (h in ranked) {
                if (hits.size >= 4) break
                // الصفحة نفسها للأفضل فقط (أبطأ)؛ وإلا مقتطف نتيجة البحث
                val t = if (fetched < pages && rank(h.url) >= 2) pageText(h.url, 1200).also { fetched++ } else ""
                val txt = t.ifBlank { h.text }
                if (txt.length >= 60) hits.add(Hit(h.title, h.url, txt))
            }
            val sb = StringBuilder()
            val src = ArrayList<Pair<String, String>>()
            for (h in hits) {
                val room = maxChars - sb.length
                if (room < 200) break
                src.add(h.title to h.url)
                sb.append('[').append(src.size).append("] ").append(h.title).append(" (").append(host(h.url)).append(")\n")
                sb.append(h.text.take(minOf(room - 40, 700))).append("\n\n")
            }
            Evidence(sb.toString().trim(), src)
        }
        if (!ev.empty) withContext(Dispatchers.IO) {
            try {
                f.writeText(JSONObject().put("t", ev.text).put("s", JSONArray().also { a -> ev.sources.forEach { a.put(JSONArray().put(it.first).put(it.second)) } }).toString())
            } catch (t: Throwable) {
            }
        }
        return ev
    }
}

/**
 * تصحيح الملخص بالإنترنت بلا زيادة: يُقبل فقط استبدال كلمات موجودة في الملخص بكلمات تؤيدها المصادر.
 * لا تُضاف نقطة ولا جملة، والمصدر يُسجَّل لكل تصحيح ليراجعه المستخدم.
 */
object WebFix {
    class Fix(val old: String, val new: String, val source: String)

    private val fixes = androidx.compose.runtime.mutableStateMapOf<String, List<Fix>>()

    private fun file(docId: String) = File(File(AppController.appContext.filesDir, "ai/web").also { it.mkdirs() }, "$docId.fixes.json")

    fun forDoc(docId: String): List<Fix> = fixes.getOrPut(docId) {
        try {
            val f = file(docId)
            if (!f.exists()) emptyList() else JSONArray(f.readText()).let { a ->
                (0 until a.length()).map { a.getJSONObject(it).let { o -> Fix(o.getString("o"), o.getString("n"), o.getString("s")) } }
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    fun clear(docId: String) {
        fixes[docId] = emptyList()
        file(docId).delete()
    }

    fun add(docId: String, list: List<Fix>) {
        if (list.isEmpty()) return
        val all = (forDoc(docId) + list).distinctBy { it.old + "→" + it.new }
        fixes[docId] = all
        try {
            file(docId).writeText(JSONArray().also { a -> all.forEach { a.put(JSONObject().put("o", it.old).put("n", it.new).put("s", it.source)) } }.toString())
        } catch (t: Throwable) {
        }
    }

    const val SYSTEM = """You check ONE section of a study summary against SOURCES from the internet.
Output ONLY corrections of clear mistakes: a misspelled or wrong word, a wrong name, or a wrong number that the SOURCES contradict.
Format, one correction per line: OLD: <words copied exactly from the summary> => NEW: <the replacement>
Never add a point, never rewrite a sentence, never explain, never change something the sources do not cover.
If nothing is clearly wrong, output exactly: OK"""

    private val LINE = Regex("(?i)OLD:\\s*(.+?)\\s*=>\\s*NEW:\\s*(.+)")

    private fun words(s: String) = Regex("[\\p{L}\\p{N}]{3,}").findAll(s.lowercase()).map { it.value }.toList()

    /** يقبل تصحيحًا واحدًا إن كان استبدالًا صغيرًا لنص موجود، وكل كلمة جديدة فيه مدعومة بالمصادر أو بالملخص نفسه. */
    fun acceptable(old: String, new: String, body: String, evidence: String): Boolean {
        if (old.isBlank() || new.isBlank() || old == new) return false
        if (old.length > 90 || new.length > old.length * 2 + 12) return false
        if (!body.contains(old)) return false
        val ev = evidence.lowercase()
        val known = words(body).toSet()
        return words(new).filter { it !in words(old) }.all { w -> w in known || ev.contains(w) } &&
            Regex("\\d+(?:[.,]\\d+)?").findAll(new).map { it.value }.all { n -> n in old || ev.contains(n) }
    }

    /** يعيد (النص بعد التصحيح، التصحيحات المطبقة). */
    suspend fun correct(docId: String, topic: String, heading: String?, body: String, memo: MutableMap<String, String>): Pair<String, List<Fix>> {
        if (!WebSearch.enabled || body.isBlank()) return body to emptyList()
        val q = listOfNotNull(topic.takeIf { it.isNotBlank() }, heading).joinToString(" ")
        val mk = "webfix:" + AiStructure.hash("$q|$body")
        val ev = WebSearch.evidence(q)
        if (ev.empty) return body to emptyList()
        val srcName = ev.sources.firstOrNull()?.let { it.first + " — " + it.second } ?: ""
        val raw = memo[mk] ?: try {
            Ai.generate(SYSTEM, "SOURCES:\n${ev.text}\n\nSUMMARY SECTION:\n${body.replace("**", "")}").also { memo[mk] = it }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            return body to emptyList()
        }
        var out = body
        val applied = ArrayList<Fix>()
        for (l in raw.lines()) {
            val m = LINE.find(l.trim()) ?: continue
            val o = m.groupValues[1].trim().trim('"', '`')
            val n = m.groupValues[2].trim().trim('"', '`')
            if (applied.size >= 3) break
            if (!acceptable(o, n, out, ev.text)) continue
            out = out.replaceFirst(o, n)
            applied.add(Fix(o, n, srcName))
        }
        return out to applied
    }
}
