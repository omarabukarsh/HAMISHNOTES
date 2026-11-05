package com.hamishnotes.app

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * خط من مكتبة Google Fonts (ترخيص SIL OFL 1.1 أو Apache 2.0 فقط، فهي مجانية للاستعمال والتضمين).
 * c: H خط يد، S Serif، N Sans، D زخرفي، M Mono. arabic: يدعم العربية. bundled: ملف مثبت داخل التطبيق.
 */
class FontEntry(val name: String, val cat: Char, val arabic: Boolean, val path: String, val bundled: String?)

/**
 * مكتبة الخطوط: نحو 15 خطًا مثبتًا مع التطبيق، والباقي يُنزَّل عند الطلب من مستودع Google Fonts
 * ويُحفظ في التطبيق، فيعمل بعدها دون إنترنت. الاسم الفارغ = خط التطبيق الافتراضي.
 */
object FontStore {
    private lateinit var app: Context
    private lateinit var sp: SharedPreferences
    private val typefaces = HashMap<String, android.graphics.Typeface>()
    private val families = HashMap<String, FontFamily>()
    /** حالة كل خط: true = جاهز، false = يُنزَّل الآن. */
    val state = mutableStateMapOf<String, Boolean>()
    var favorites by mutableStateOf<Set<String>>(emptySet()); private set
    var recent by mutableStateOf<List<String>>(emptyList()); private set
    private val failed = HashSet<String>()
    private val downloads = Semaphore(3)

    /** خطوط عربية بطابع الكتابة اليدوية أو الخط العربي التقليدي. */
    private val arabicHand = setOf(
        "Aref Ruqaa", "Aref Ruqaa Ink", "Alkalami", "Vibes", "Marhey", "Rakkas", "Lalezar", "Blaka", "Blaka Ink",
        "Blaka Hollow", "Reem Kufi", "Reem Kufi Fun", "Reem Kufi Ink", "Kufam", "Lemonada", "Gulzar",
        "Noto Nastaliq Urdu", "Amiri Quran", "Mirza", "Katibeh", "Jomhuria", "Badeen Display", "Handjet"
    )

    val catalog: List<FontEntry> by lazy {
        try {
            val arr = JSONArray(app.assets.open("fonts/catalog.json").bufferedReader().use { it.readText() })
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                FontEntry(o.getString("n"), o.getString("c")[0], o.optInt("a") == 1, o.getString("p"), o.optString("b").ifEmpty { null })
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }
    private val byName by lazy { catalog.associateBy { it.name } }

    fun init(ctx: Context) {
        app = ctx.applicationContext
        sp = ctx.getSharedPreferences("hamish_fonts", Context.MODE_PRIVATE)
        favorites = sp.getStringSet("fav", emptySet()) ?: emptySet()
        recent = (sp.getString("recent", "") ?: "").split('\n').filter { it.isNotBlank() }
    }

    fun entry(name: String): FontEntry? = byName[name]

    fun isArabicHand(e: FontEntry) = e.arabic && (e.cat == 'H' || e.name in arabicHand)

    private fun dir() = File(app.filesDir, "fonts").apply { mkdirs() }

    private fun fileOf(e: FontEntry) = File(dir(), e.path.substringAfterLast('/').replace(Regex("[^A-Za-z0-9_.-]"), "_"))

    fun isReady(name: String): Boolean {
        if (name.isEmpty()) return true
        val e = byName[name] ?: return false
        return e.bundled != null || fileOf(e).exists()
    }

    fun typeface(name: String): android.graphics.Typeface? {
        if (name.isEmpty()) return null
        synchronized(typefaces) { typefaces[name]?.let { return it } }
        val e = byName[name] ?: return null
        val loaded: android.graphics.Typeface? = try {
            when {
                e.bundled != null -> android.graphics.Typeface.createFromAsset(app.assets, "fonts/bundled/${e.bundled}")
                fileOf(e).exists() -> android.graphics.Typeface.createFromFile(fileOf(e))
                else -> null
            }
        } catch (t: Throwable) {
            null
        }
        val tf = loaded ?: return null
        synchronized(typefaces) { typefaces[name] = tf }
        return tf
    }

    /** الخط لعرضه في Compose؛ null = غير متوفر بعد (يُعرض بالخط الافتراضي). */
    fun family(name: String): FontFamily? {
        if (name.isEmpty()) return null
        families[name]?.let { return it }
        val tf = typeface(name) ?: return null
        return FontFamily(androidx.compose.ui.text.font.Typeface(tf)).also { families[name] = it }
    }

    fun toggleFavorite(name: String) {
        favorites = if (name in favorites) favorites - name else favorites + name
        sp.edit().putStringSet("fav", favorites).apply()
    }

    fun markUsed(name: String) {
        if (name.isEmpty()) return
        recent = (listOf(name) + recent.filter { it != name }).take(24)
        sp.edit().putString("recent", recent.joinToString("\n")).apply()
    }

    fun unmetered(): Boolean {
        return try {
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.isActiveNetworkMetered.not()
        } catch (t: Throwable) {
            false
        }
    }

    /** ينزّل الخط إن لم يكن موجودًا. true = جاهز. */
    suspend fun ensure(name: String, retryFailed: Boolean = true): Boolean {
        if (isReady(name)) return true
        val e = byName[name] ?: return false
        if (!retryFailed && name in failed) return false
        if (state[name] == false) return false
        state[name] = false
        val ok = downloads.withPermit {
            withContext(Dispatchers.IO) {
                val enc = e.path.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
                val urls = listOf(
                    "https://raw.githubusercontent.com/google/fonts/main/$enc",
                    "https://cdn.jsdelivr.net/gh/google/fonts@main/$enc"
                )
                val dest = fileOf(e)
                val tmp = File(dest.path + ".part")
                urls.any { u ->
                    try {
                        val con = URL(u).openConnection() as HttpURLConnection
                        con.connectTimeout = 15_000
                        con.readTimeout = 30_000
                        con.instanceFollowRedirects = true
                        try {
                            if (con.responseCode != 200) return@any false
                            con.inputStream.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
                        } finally {
                            con.disconnect()
                        }
                        // يُتحقق أنه خط صالح قبل اعتماده
                        android.graphics.Typeface.createFromFile(tmp)
                        tmp.length() > 1000 && tmp.renameTo(dest)
                    } catch (t: Throwable) {
                        tmp.delete()
                        false
                    }
                }
            }
        }
        if (ok) {
            state[name] = true
            failed.remove(name)
        } else {
            state.remove(name)
            failed.add(name)
        }
        return ok
    }
}

/**
 * مكتبة الخطوط: فئات وبحث ومفضلة، ومعاينة كل خط بنص المستخدم.
 * الخطوط غير المنزلة تُنزَّل تلقائيًا عند ظهورها على شبكة غير محدودة (Wi-Fi)، أو بلمسة.
 */
@Composable
fun FontLibraryDialog(sample: String, current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val tabs = listOf("fav", "recent", "hand", "arhand", "ar", "S", "N", "D", "M", "all")
    var tab by remember { mutableStateOf(if (FontStore.recent.isNotEmpty()) "recent" else "hand") }
    val auto = remember { FontStore.unmetered() }
    val text = sample.trim().ifEmpty { L("النص الذي كتبته", "Your text") }.lineSequence().first().take(48)
    val list = remember(tab, query, FontStore.favorites, FontStore.recent) {
        val q = query.trim()
        val base = when (tab) {
            "fav" -> FontStore.catalog.filter { it.name in FontStore.favorites }
            "recent" -> FontStore.recent.mapNotNull { FontStore.entry(it) }
            "hand" -> FontStore.catalog.filter { it.cat == 'H' }
            "arhand" -> FontStore.catalog.filter { FontStore.isArabicHand(it) }
            "ar" -> FontStore.catalog.filter { it.arabic }
            "all" -> FontStore.catalog
            else -> FontStore.catalog.filter { it.cat == tab[0] }
        }
        val filtered = if (q.isEmpty()) base else FontStore.catalog.filter { it.name.contains(q, ignoreCase = true) }
        // المثبتة أولًا ثم المنزلة، ثم الباقي (في «المستعملة» الترتيب كما هو)
        if (tab == "recent" && q.isEmpty()) filtered else filtered.sortedBy { if (it.bundled != null) 0 else if (FontStore.isReady(it.name)) 1 else 2 }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth(0.94f).heightIn(min = 420.dp, max = 760.dp),
            shape = RoundedCornerShape(22.dp), color = Palette.Background
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        L("مكتبة الخطوط", "Font library"), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold,
                        fontSize = 20.sp, color = Palette.Navy, modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onPick(""); onDismiss() }) { Text(L("الخط الافتراضي", "Default font"), color = Palette.Navy) }
                    TextButton(onClick = onDismiss) { Text(L("إغلاق", "Close")) }
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text(L("ابحث عن خط… (${FontStore.catalog.size})", "Search fonts… (${FontStore.catalog.size})")) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tabs.forEach { t ->
                        val label = when (t) {
                            "fav" -> L("★ المفضلة", "★ Favorites")
                            "recent" -> L("المستعملة", "Recent")
                            "hand" -> L("خط يد", "Handwriting")
                            "arhand" -> L("خط يد عربي", "Arabic handwriting")
                            "ar" -> L("عربي", "Arabic")
                            "S" -> "Serif"
                            "N" -> "Sans"
                            "D" -> L("زخرفي", "Display")
                            "M" -> "Mono"
                            else -> L("الكل", "All")
                        }
                        ChoiceChip(label, tab == t && query.isBlank()) { tab = t; query = "" }
                    }
                }
                if (list.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (tab == "fav") L("اضغط ☆ على أي خط ليظهر هنا", "Tap ☆ on any font to keep it here") else L("لا توجد نتائج", "No results"),
                            color = Palette.TextSecondary
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(250.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(list, key = { it.name }) { e ->
                            FontCard(e, text, selected = e.name == current, auto = auto) {
                                scope.launch {
                                    if (FontStore.ensure(e.name)) {
                                        FontStore.markUsed(e.name)
                                        onPick(e.name)
                                        onDismiss()
                                    } else {
                                        AppController.messages.tryEmit(L("تعذر تنزيل الخط. تأكد من الإنترنت", "Could not download the font. Check your connection"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FontCard(e: FontEntry, sample: String, selected: Boolean, auto: Boolean, onClick: () -> Unit) {
    val st = FontStore.state[e.name]
    val ready = st == true || FontStore.isReady(e.name)
    // التنزيل التلقائي للمعاينة على Wi-Fi فقط
    if (auto && !ready) LaunchedEffect(e.name) { FontStore.ensure(e.name, retryFailed = false) }
    val fam = if (ready) FontStore.family(e.name) else null
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) Palette.NavySoft else Color.White)
            .border(if (selected) 2.dp else 1.dp, if (selected) Palette.Navy else Palette.Divider, shape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            if (fam != null) sample else e.name, fontFamily = fam, fontSize = if (fam != null) 21.sp else 16.sp,
            color = if (fam != null) Palette.TextPrimary else Palette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().heightIn(min = 34.dp)
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.name, fontSize = 11.5.sp, color = Palette.TextSecondary, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            when {
                st == false -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Palette.GoldDeep)
                e.bundled != null -> Tag(L("مثبت", "Built-in"), Palette.NavySoft, Palette.Navy)
                ready -> Tag(L("منزّل", "Downloaded"), Palette.NavySoft, Palette.Navy)
                else -> Tag(L("⤓ تنزيل", "⤓ Get"), Palette.GoldSoft, Palette.GoldDeep)
            }
            Spacer(Modifier.width(6.dp))
            Text(
                if (e.name in FontStore.favorites) "★" else "☆", fontSize = 17.sp, color = Palette.Gold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { FontStore.toggleFavorite(e.name) }.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun Tag(text: String, bg: Color, fg: Color) {
    Text(
        text, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = fg,
        modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(bg).padding(horizontal = 7.dp, vertical = 2.dp)
    )
}
