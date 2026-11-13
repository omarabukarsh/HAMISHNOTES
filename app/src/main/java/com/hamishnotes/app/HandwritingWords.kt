package com.hamishnotes.app

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.IdentityHashMap
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import com.google.mlkit.vision.digitalink.recognition.Ink as MlInk

/** لغة خط اليد في الإعدادات. */
object HwLang {
    const val BOTH = 0
    const val ARABIC = 1
    const val ENGLISH = 2
}

/**
 * تقسيم خط اليد على الصفحة إلى أسطر وكلمات، وتحديد لغة كل كلمة من اتجاه كتابتها.
 * منطق هندسي خالص (بلا ML Kit) ليُختبر على JVM.
 */
object HandwritingLayout {

    class Word(val strokes: List<Ink>, val l: Float, val t: Float, val r: Float, val b: Float, val rtl: Boolean)

    /** كلمات متتالية من نفس اللغة تُقرأ معًا (rtl = عربي). الكلمات مرتبة بترتيب القراءة. */
    class Run(val words: List<Word>, val rtl: Boolean)

    class Line(val runs: List<Run>)

    /** الخطوط التي تُقرأ كنص: لا الهايلايتر ولا الأشكال ولا الممحاة أو الليزر. */
    fun eligible(s: Ink): Boolean =
        s.pts.isNotEmpty() && !s.shape && !s.fill && s.tool != Tool.HIGHLIGHTER && s.tool != Tool.ERASER && s.tool != Tool.LASER

    /**
     * lang: HwLang. defaultRtl: اللغة عند تعذّر معرفة الاتجاه (كلمة من نقطة واحدة مثلًا).
     * الخطوط بترتيب رسمها (ترتيب القائمة)، فهو ما يكشف اتجاه الكتابة.
     */
    fun lines(strokes: List<Ink>, lang: Int, defaultRtl: Boolean): List<Line> {
        val ok = strokes.filter(::eligible)
        if (ok.isEmpty()) return emptyList()
        val order = IdentityHashMap<Ink, Int>()
        ok.forEachIndexed { i, s -> order[s] = i }
        return InkToText.lines(ok).mapNotNull { lineStrokes ->
            val words = words(lineStrokes, order)
            if (words.isEmpty()) return@mapNotNull null
            val fixed: List<Word> = when (lang) {
                HwLang.ARABIC -> words.map { it.withRtl(true) }
                HwLang.ENGLISH -> words.map { it.withRtl(false) }
                else -> {
                    // كلمة غامضة الاتجاه تأخذ اتجاه أغلب كلمات سطرها، وإلا الافتراضي
                    val dirs = words.map { direction(it.strokes, order) }
                    val known = dirs.filterNotNull()
                    val lineRtl = if (known.isEmpty()) defaultRtl else known.count { it } * 2 >= known.size
                    words.mapIndexed { i, w -> w.withRtl(dirs[i] ?: lineRtl) }
                }
            }
            Line(runs(fixed))
        }
    }

    private fun Word.withRtl(v: Boolean) = Word(strokes, l, t, r, b, v)

    private class Box(val s: Ink, val l: Float, val t: Float, val r: Float, val b: Float)

    private fun box(s: Ink) = Box(s, s.pts.minOf { it.x }, s.pts.minOf { it.y }, s.pts.maxOf { it.x }, s.pts.maxOf { it.y })

    /** يقسم خطوط سطر واحد إلى كلمات حسب الفراغ الأفقي بينها. */
    internal fun words(lineStrokes: List<Ink>, order: Map<Ink, Int>): List<Word> {
        val boxes = lineStrokes.map(::box).sortedBy { it.l }
        if (boxes.isEmpty()) return emptyList()
        val lineH = (boxes.maxOf { it.b } - boxes.minOf { it.t }).coerceAtLeast(0.004f)
        // فراغ أكبر من نحو ثلث ارتفاع السطر يفصل كلمتين
        val gap = lineH * 0.3f
        val groups = ArrayList<MutableList<Box>>()
        var right = -1f
        for (b in boxes) {
            if (groups.isEmpty() || b.l - right > gap) {
                groups.add(mutableListOf(b))
                right = b.r
            } else {
                groups.last().add(b)
                right = max(right, b.r)
            }
        }
        return groups.map { g ->
            Word(
                g.map { it.s }.sortedBy { order[it] ?: 0 },
                g.minOf { it.l }, g.minOf { it.t }, g.maxOf { it.r }, g.maxOf { it.b },
                rtl = false
            )
        }
    }

    /**
     * اتجاه كتابة الكلمة من ترتيب رسم خطوطها: true = من اليمين لليسار (عربي)،
     * false = من اليسار لليمين، null = غير واضح. النقاط والتشكيل الصغيرة لا تُحسب.
     */
    internal fun direction(strokes: List<Ink>, order: Map<Ink, Int>): Boolean? {
        if (strokes.isEmpty()) return null
        val boxes = strokes.sortedBy { order[it] ?: 0 }.map(::box)
        val wordL = boxes.minOf { it.l }
        val wordR = boxes.maxOf { it.r }
        val wordH = (boxes.maxOf { it.b } - boxes.minOf { it.t }).coerceAtLeast(1e-4f)
        val width = (wordR - wordL).coerceAtLeast(1e-4f)
        val major = boxes.filter { max(it.r - it.l, it.b - it.t) >= wordH * 0.35f }
        if (major.isEmpty()) return null
        var score = 0f
        // داخل الخط: أين انتهى القلم بالنسبة لبدايته
        for (m in major) score += m.s.pts.last().x - m.s.pts.first().x
        // بين الخطوط: إلى أين انتقل القلم من خط إلى الخط التالي
        for (i in 1 until major.size) {
            score += (major[i].l + major[i].r) / 2f - (major[i - 1].l + major[i - 1].r) / 2f
        }
        if (abs(score) < width * 0.15f) return null
        return score < 0f
    }

    /** يرتب كلمات السطر بترتيب القراءة ويجمع المتتالية منها من نفس اللغة. */
    internal fun runs(words: List<Word>): List<Run> {
        val lineRtl = words.count { it.rtl } * 2 > words.size
        val ordered = if (lineRtl) words.sortedByDescending { it.r } else words.sortedBy { it.l }
        val out = ArrayList<Run>()
        var cur = ArrayList<Word>()
        for (w in ordered) {
            if (cur.isNotEmpty() && cur[0].rtl != w.rtl) {
                out.add(Run(cur, cur[0].rtl))
                cur = ArrayList()
            }
            cur.add(w)
        }
        if (cur.isNotEmpty()) out.add(Run(cur, cur[0].rtl))
        // داخل كل مجموعة: الكلمات بترتيب قراءة لغتها نفسها
        return out.map { r -> Run(if (r.rtl) r.words.sortedByDescending { it.r } else r.words.sortedBy { it.l }, r.rtl) }
    }

    /**
     * يوزّع كلمات النص المقروء على كلمات المجموعة بالترتيب (الأولى من اليمين للعربي).
     * null إن لم يتطابق العدد، فتُقرأ كل كلمة وحدها.
     */
    fun assign(run: Run, text: String): List<Pair<Word, String>>? {
        val tokens = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty() || tokens.size > run.words.size) return null
        // كلمات أقل من الأجزاء: جزء الكلمة الواحدة انفصل (حروف عربية لا تتصل بما بعدها)،
        // فتُضم الأجزاء الأقرب بعضها إلى بعض حتى يتطابق العدد
        val ws = run.words.toMutableList()
        while (ws.size > tokens.size) {
            var best = 0
            var bestGap = Float.MAX_VALUE
            for (i in 0 until ws.size - 1) {
                val g = if (run.rtl) ws[i].l - ws[i + 1].r else ws[i + 1].l - ws[i].r
                if (g < bestGap) {
                    bestGap = g
                    best = i
                }
            }
            val a = ws[best]
            val c = ws[best + 1]
            ws[best] = Word(a.strokes + c.strokes, minOf(a.l, c.l), minOf(a.t, c.t), maxOf(a.r, c.r), maxOf(a.b, c.b), a.rtl)
            ws.removeAt(best + 1)
        }
        return ws.zip(tokens)
    }

    fun toWord(w: Word, text: String, line: Int) = OcrWord(text, w.l, w.t, w.r, w.b, line)

    /** نص بحروف لاتينية فقط (لا عربية): قراءة لكتابة إنجليزية. */
    fun latinOnly(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val letters = text.filter { it.isLetter() }
        return letters.isNotEmpty() && letters.all { it in 'a'..'z' || it in 'A'..'Z' }
    }
}

/**
 * قراءة خط اليد من خطوط القلم نفسها بـ ML Kit Digital Ink (عربي وإنجليزي، على الجهاز دون إنترنت).
 * حزمة كل لغة تُنزَّل مرة واحدة في الخلفية. إن لم تكن جاهزة يُرجع read() القيمة null
 * فتبقى القراءة الضوئية القديمة بديلًا.
 */
object HandwritingReader {
    const val AR = "ar"
    const val EN = "en-US"

    private val lock = Any()
    private val models = HashMap<String, DigitalInkRecognitionModel>()
    private val recognizers = HashMap<String, DigitalInkRecognizer>()
    @Volatile private var ready: Set<String> = emptySet()
    private val lastTry = HashMap<String, Long>()

    /** ذاكرة لنتائج القراءة: إعادة قراءة صفحة لا تعيد إلا الأسطر التي تغيّرت. */
    private val cache = object : LinkedHashMap<String, List<String>>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?) = size > 800
    }

    fun tagsFor(lang: Int): List<String> = when (lang) {
        HwLang.ARABIC -> listOf(AR)
        HwLang.ENGLISH -> listOf(EN)
        else -> listOf(AR, EN)
    }

    fun isReady(lang: Int): Boolean = tagsFor(lang).all { it in ready }

    /**
     * ينزّل حزم اللغة في الخلفية إن لم تكن موجودة.
     * allowMetered=false (التنزيل التلقائي عند فتح مستند): على Wi‑Fi أو شبكة غير محدودة فقط،
     * فلا تُستهلك باقة الجوال (نحو 20 ميغابايت للحزمة) دون أن يطلب المستخدم شيئًا.
     */
    fun prefetch(lang: Int, allowMetered: Boolean = true) {
        AppController.scope.launch { tagsFor(lang).forEach { ensure(it, allowMetered) } }
    }

    /** الشبكة الحالية محدودة (بيانات الجوال أو نقطة اتصال)، أو لا شبكة. */
    private fun metered(): Boolean = try {
        val cm = AppController.appContext.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        cm.isActiveNetworkMetered
    } catch (t: Throwable) {
        true
    }

    private fun model(tag: String): DigitalInkRecognitionModel? = synchronized(lock) {
        models[tag] ?: run {
            val id = DigitalInkRecognitionModelIdentifier.fromLanguageTag(tag) ?: return null
            DigitalInkRecognitionModel.builder(id).build().also { models[tag] = it }
        }
    }

    private fun recognizer(tag: String): DigitalInkRecognizer? = synchronized(lock) {
        recognizers[tag] ?: run {
            val m = model(tag) ?: return null
            DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(m).build()).also { recognizers[tag] = it }
        }
    }

    private suspend fun <T> Task<T>.result(): T? = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resume(null) }
        addOnCanceledListener { cont.resume(null) }
    }

    /** true إن نجحت المهمة (لمهام بلا قيمة مثل التنزيل). */
    private suspend fun <T> Task<T>.succeeded(): Boolean = suspendCancellableCoroutine { cont ->
        addOnCompleteListener { cont.resume(it.isSuccessful) }
    }

    /**
     * true إن كانت حزمة اللغة جاهزة (تُنزَّل إن لزم، بمحاولة كل دقيقة على الأكثر).
     * allowMetered=false: لا تنزيل على شبكة محدودة (يعيد false فورًا، ويُعاد المحاولة لاحقًا على Wi‑Fi).
     */
    suspend fun ensure(tag: String, allowMetered: Boolean = true): Boolean {
        if (tag in ready) return true
        val m = model(tag) ?: return false
        val mgr = RemoteModelManager.getInstance()
        if (mgr.isModelDownloaded(m).result() == true) {
            synchronized(lock) { ready = ready + tag }
            return true
        }
        if (!allowMetered && metered()) return false
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (now - (lastTry[tag] ?: 0L) < 60_000L) return false
            lastTry[tag] = now
        }
        if (!mgr.download(m, DownloadConditions.Builder().build()).succeeded()) return false
        val ok = mgr.isModelDownloaded(m).result() == true
        if (ok) synchronized(lock) { ready = ready + tag }
        return ok
    }

    private fun key(tag: String, strokes: List<Ink>): String {
        var h = 17L
        for (s in strokes) {
            h = h * 31 + s.pts.size
            for (p in s.pts) h = h * 31 + (p.x * 100_000).toLong() * 7 + (p.y * 100_000).toLong()
        }
        return "$tag:$h"
    }

    private suspend fun recognize(tag: String, strokes: List<Ink>, w: Float, h: Float): String =
        candidates(tag, strokes, w, h).firstOrNull() ?: ""

    /** قراءات القارئ مرتبة من الأرجح (الأولى هي النتيجة). */
    private suspend fun candidates(tag: String, strokes: List<Ink>, w: Float, h: Float): List<String> {
        val k = key(tag, strokes)
        synchronized(lock) { cache[k] }?.let { return it }
        val rec = recognizer(tag) ?: return emptyList()
        val b = MlInk.builder()
        for (st in strokes) {
            val sb = MlInk.Stroke.builder()
            st.pts.forEach { p -> sb.addPoint(MlInk.Point.create(p.x * 1000f, p.y * 1000f)) }
            b.addStroke(sb.build())
        }
        // مقاس منطقة الكتابة يساعد القارئ على تمييز الحروف الكبيرة من الصغيرة
        val ctx = RecognitionContext.builder()
            .setPreContext("")
            .setWritingArea(WritingArea(max(w * 1000f, 1f), max(h * 1000f, 1f)))
            .build()
        val r = rec.recognize(b.build(), ctx).result()
        val list = r?.candidates?.map { it.text.trim() }?.filter { it.isNotEmpty() }?.distinct() ?: emptyList()
        synchronized(lock) { cache[k] = list }
        return list
    }

    /**
     * كلمات خط اليد في الصفحة، بأرقام أسطر تبدأ من firstLine.
     * null = حزمة لغة لازمة غير جاهزة بعد. قائمة فارغة = لا خط يد.
     */
    suspend fun read(
        strokes: List<Ink>, lang: Int, firstLine: Int,
        /** إن أُعطيت: تُملأ ببدائل كل كلمة (بنفس ترتيب الكلمات المُرجعة). */
        altsOut: MutableList<List<String>>? = null,
        /** false للقراءة التلقائية في الخلفية: لا تنزّل حزمة لغة على بيانات الجوال. */
        allowMetered: Boolean = true
    ): List<OcrWord>? {
        val lines = HandwritingLayout.lines(strokes, lang, defaultRtl = !I18n.english)
        if (lines.isEmpty()) return emptyList()
        val needed = lines.flatMap { l -> l.runs.map { if (it.rtl) AR else EN } }.toSet()
        for (t in needed) if (!ensure(t, allowMetered)) return null
        val out = ArrayList<OcrWord>()
        lines.forEachIndexed { i, line ->
            val lineNo = firstLine + i
            val lineT = line.runs.minOf { r -> r.words.minOf { it.t } }
            val lineB = line.runs.maxOf { r -> r.words.maxOf { it.b } }
            for (run0 in line.runs) {
                var run = run0
                var tag = if (run.rtl) AR else EN
                val w = run.words.maxOf { it.r } - run.words.minOf { it.l }
                var cands = candidates(tag, run.words.flatMap { it.strokes }, w, lineB - lineT)
                // كلمة إنجليزية قُرئت بقارئ العربية (لغة الكتابة «العربية»): حروفها لاتينية فقط،
                // فتُقرأ من جديد من اليسار لليمين بقارئ الإنجليزية (وإلا تنقلب أجزاؤها مثل Omar ← arom)
                if (run.rtl && HandwritingLayout.latinOnly(cands.firstOrNull()) && ensure(EN, allowMetered)) {
                    val ltr = HandwritingLayout.Run(
                        run.words.sortedBy { it.l }.map { HandwritingLayout.Word(it.strokes, it.l, it.t, it.r, it.b, false) }, false
                    )
                    val again = candidates(EN, ltr.words.flatMap { it.strokes }, w, lineB - lineT)
                    if (again.isNotEmpty()) {
                        run = ltr
                        tag = EN
                        cands = again
                    }
                }
                val text = cands.firstOrNull() ?: ""
                val pairs = HandwritingLayout.assign(run, text)
                if (pairs != null) {
                    pairs.forEachIndexed { j, (wd, s) ->
                        out.add(HandwritingLayout.toWord(wd, s, lineNo))
                        // بديل الكلمة j: كلمتها في كل قراءة أخرى لها نفس عدد الكلمات
                        altsOut?.add(
                            cands.drop(1).mapNotNull { c ->
                                val tk = c.split(Regex("\\s+")).filter { it.isNotEmpty() }
                                if (tk.size == pairs.size) tk[j] else null
                            }.filter { it != s }.distinct().take(4)
                        )
                    }
                } else {
                    // عدد الكلمات لم يطابق: تُقرأ كل كلمة وحدها في مكانها
                    for (wd in run.words) {
                        val cs = candidates(tag, wd.strokes, wd.r - wd.l, lineB - lineT)
                        val s = cs.firstOrNull() ?: ""
                        if (s.isNotBlank()) {
                            out.add(HandwritingLayout.toWord(wd, s, lineNo))
                            altsOut?.add(cs.drop(1).filter { it != s && !it.contains(' ') }.take(4))
                        }
                    }
                }
            }
        }
        return out
    }
}
