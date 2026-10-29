package com.mynotes.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * الذكاء الاصطناعي داخل الجهاز (MyNotes Pro): نموذج لغوي صغير يعمل دون إنترنت عبر LiteRT-LM من Google.
 * يُنزَّل النموذج مرة واحدة (أو يُستورد من ملف)، ثم يعمل كل شيء على الجهاز فقط.
 */
object Ai {
    /** نموذج متاح للتنزيل: repo و file من Hugging Face (رخصة Apache-2.0، دون تسجيل دخول). */
    class Model(
        val id: String,
        val title: () -> String,
        val desc: () -> String,
        val repo: String?,
        val file: String,
        val sizeMb: Int,
        /** يقرأ الصور أيضًا (يُستعمل لقراءة صفحات المستند بالذكاء الاصطناعي). */
        val vision: Boolean = false
    ) {
        val url: String? get() = repo?.let { "https://huggingface.co/$it/resolve/main/$file?download=true" }
    }

    val models = listOf(
        Model(
            "gemma4", { L("AI", "AI") },
            { L("الأسرع وذاكرته أقل، مناسب لجهازك", "Faster and lighter on memory; suits your device") },
            "litert-community/gemma-4-E2B-it-litert-lm", "gemma-4-E2B-it.litertlm", 2583, vision = true
        ),
        Model(
            "gemma4plus", { L("AI أقوى", "Stronger AI") },
            { L("أدق وأبطأ ويحتاج ذاكرة أكبر بكثير، وقد لا يناسب جهازك", "More accurate but slower and needs much more memory; it may not suit your device") },
            "litert-community/gemma-4-E4B-it-litert-lm", "gemma-4-E4B-it.litertlm", 3654, vision = true
        )
    )

    private lateinit var ctx: Context
    private val prefs by lazy { ctx.getSharedPreferences("mynotes_ai", Context.MODE_PRIVATE) }

    var selectedId by mutableStateOf("gemma4")
        private set
    var useGpu by mutableStateOf(false)
        private set

    /** تقدم التنزيل لكل نموذج (0..1)، وغياب المفتاح = لا تنزيل جارٍ. */
    val progress = mutableStateMapOf<String, Float>()

    /** يتغير بعد تنزيل/حذف/استيراد ليعيد رسم الواجهة. */
    var filesVersion by mutableStateOf(0)
        private set

    /** حالة تشغيل النموذج: "" جاهز أو غير محمّل، وإلا نص يوضح ما يجري. */
    var engineStatus by mutableStateOf("")
        private set

    private val downloads = HashMap<String, Job>()
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var engine: Engine? = null
    private var enginePath: String? = null
    private var engineGpu = false
    private var idleJob: Job? = null

    fun init(context: Context) {
        if (::ctx.isInitialized) return
        ctx = context.applicationContext
        // النماذج القديمة (Qwen والملف المستورد) أُلغيت: من اختارها ينتقل إلى «AI»، وتُحذف ملفاتها لتحرير المساحة
        val saved = prefs.getString("model", "gemma4") ?: "gemma4"
        selectedId = if (models.any { it.id == saved }) saved else "gemma4"
        if (saved != selectedId) prefs.edit().putString("model", selectedId).apply()
        AppController.scope.launch(Dispatchers.IO) {
            try {
                val keep = models.map { it.file }.toSet()
                modelDir().listFiles()?.forEach { f ->
                    val base = f.name.removeSuffix(".part")
                    if (f.isFile && base.endsWith(".litertlm") && base !in keep) f.delete()
                }
            } catch (t: Throwable) {
            }
        }
        // زر معالج الرسوم أُلغي: التشغيل على المعالج (CPU) دائمًا، ويُمسح الاختيار القديم
        useGpu = false
        if (prefs.contains("gpu")) prefs.edit().remove("gpu").apply()
    }

    fun modelDir(): File = File(ctx.filesDir, "models").also { it.mkdirs() }
    fun fileOf(m: Model) = File(modelDir(), m.file)
    fun installed(m: Model): Boolean {
        @Suppress("UNUSED_VARIABLE") val v = filesVersion
        return fileOf(m).let { it.exists() && it.length() > 1_000_000L }
    }

    val selected: Model get() = models.firstOrNull { it.id == selectedId } ?: models[0]
    val ready: Boolean get() = installed(selected)

    /** النموذج المختار منزّل ويقرأ الصور ولم يفشل تشغيل قارئ الصور (الاستنتاج على المعالج CPU دائمًا). */
    val visionOk: Boolean get() = installed(selected) && selected.vision && !visionFailed

    /** رسالة خطأ واضحة للمستخدم. */
    fun errorText(t: Throwable, local: String): String =
        if (t is VisionUnavailable) L("قراءة الصور غير متاحة على هذا الجهاز مع النموذج الحالي", "Image reading is not available on this device with the current model") else local

    /** يُرمى حين يُطلب تحليل صورة ولا يستطيع المحرّك قراءتها (فلا يُستبدل بنص دون إعلام). */
    class VisionUnavailable : IllegalStateException("vision unavailable")
    private var visionFailed = false
    private var engineVision = false

    fun select(id: String) {
        selectedId = id
        prefs.edit().putString("model", id).apply()
    }

    // ---------- التنزيل والاستيراد ----------

    /** ينزّل النموذج مع إمكانية الاستكمال إن انقطع الاتصال. */
    fun download(m: Model) {
        val url = m.url ?: return
        if (downloads[m.id]?.isActive == true) return
        progress[m.id] = 0f
        downloads[m.id] = AppController.scope.launch {
            val target = fileOf(m)
            val part = File(target.path + ".part")
            try {
                withContext(Dispatchers.IO) {
                    var attempt = 0
                    while (true) {
                        try {
                            fetch(url, part, m)
                            break
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            if (++attempt >= 4) throw t
                            delay(2000L * attempt)
                        }
                    }
                    if (target.exists()) target.delete()
                    if (!part.renameTo(target)) throw java.io.IOException("rename failed")
                }
                AppController.messages.tryEmit(L("اكتمل تنزيل النموذج، والذكاء الاصطناعي جاهز دون إنترنت", "Model downloaded; AI now works offline"))
            } catch (e: CancellationException) {
                AppController.messages.tryEmit(L("أُوقف التنزيل، ويمكن استكماله لاحقًا", "Download paused; you can resume later"))
            } catch (t: Throwable) {
                AppController.messages.tryEmit(
                    L(
                        "تعذر تنزيل النموذج. تأكد من الإنترنت وأعد المحاولة (يستكمل من حيث توقف).",
                        "Could not download the model. Check your internet and retry (it resumes)."
                    )
                )
            } finally {
                progress.remove(m.id)
                downloads.remove(m.id)
                filesVersion++
            }
        }
    }

    private suspend fun fetch(url: String, part: File, m: Model) {
        val have = if (part.exists()) part.length() else 0L
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "MyNotesPro/1.0")
            if (have > 0) setRequestProperty("Range", "bytes=$have-")
        }
        try {
            val code = conn.responseCode
            val append = code == 206
            if (code != 200 && code != 206) throw java.io.IOException("HTTP $code")
            val total = if (append) have + conn.contentLengthLong else conn.contentLengthLong
            var done = if (append) have else 0L
            conn.inputStream.use { input ->
                FileOutputStream(part, append).use { out ->
                    val buf = ByteArray(256 * 1024)
                    var lastUi = 0L
                    while (true) {
                        kotlin.coroutines.coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - lastUi > 400) {
                            lastUi = now
                            val t = if (total > 0) total else m.sizeMb * 1_048_576L
                            progress[m.id] = (done.toFloat() / t).coerceIn(0f, 1f)
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    fun cancelDownload(m: Model) {
        downloads[m.id]?.cancel()
    }

    fun delete(m: Model) {
        AppController.scope.launch {
            if (enginePath == fileOf(m).path) closeEngine()
            withContext(Dispatchers.IO) {
                fileOf(m).delete()
                File(fileOf(m).path + ".part").delete()
            }
            filesVersion++
        }
    }

    // ---------- تشغيل النموذج ----------

    private suspend fun ensureEngine(): Engine = withContext(dispatcher) {
        val path = fileOf(selected).path
        val e = engine
        val wantVision = selected.vision && !visionFailed
        if (e != null && enginePath == path && engineGpu == useGpu && engineVision == wantVision) return@withContext e
        closeEngineNow()
        engineStatus = L("جارٍ تحميل النموذج في الذاكرة…", "Loading the model into memory…")
        fun create(vision: Boolean) = Engine(
            EngineConfig(
                modelPath = path,
                backend = Backend.CPU(),
                visionBackend = if (vision) Backend.CPU() else null,
                cacheDir = ctx.cacheDir.path
            )
        ).also { it.initialize() }
        var usedVision = wantVision
        val created = try {
            try {
                create(wantVision)
            } catch (t: Throwable) {
                if (!wantVision) throw t
                // تعذّر تشغيل قارئ الصور: نكمل بالنص فقط
                visionFailed = true
                usedVision = false
                create(false)
            }
        } catch (t: Throwable) {
            engineStatus = ""
            throw t
        }
        engine = created
        enginePath = path
        engineGpu = useGpu
        engineVision = usedVision
        engineStatus = ""
        created
    }

    private fun closeEngineNow() {
        try {
            engine?.close()
        } catch (t: Throwable) {
        }
        engine = null
        enginePath = null
    }

    suspend fun closeEngine() = withContext(dispatcher) { closeEngineNow() }

    /** بعد 4 دقائق دون استعمال يُفرَّغ النموذج من الذاكرة. */
    private fun scheduleIdleRelease() {
        idleJob?.cancel()
        idleJob = AppController.scope.launch {
            delay(4 * 60_000L)
            closeEngine()
        }
    }

    /**
     * يرسل طلبًا واحدًا (محادثة جديدة بلا ذاكرة سابقة) ويعيد النص كاملًا، مع تحديث جزئي أثناء الكتابة.
     */
    suspend fun generate(
        system: String,
        prompt: String,
        image: ByteArray? = null,
        /** true = الصورة لازمة (لا نص يكفي): إن تعذّر قارئ الصور يُرمى VisionUnavailable بدل المتابعة بلا صورة. */
        requireImage: Boolean = false,
        onPartial: (String) -> Unit = {}
    ): String {
        if (!installed(selected)) throw IllegalStateException("model not installed")
        idleJob?.cancel()
        val eng = ensureEngine()
        if (image != null && requireImage && !engineVision) {
            scheduleIdleRelease()
            throw VisionUnavailable()
        }
        val out = StringBuilder()
        try {
            withContext(dispatcher) {
                eng.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(system),
                        samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.2)
                    )
                ).use { conv ->
                    try {
                        // صورة الصفحة (إن وُجدت وكان النموذج يقرأ الصور) قبل النص
                        val contents = if (image != null && engineVision) Contents.of(Content.ImageBytes(image), Content.Text(prompt))
                            else Contents.of(prompt)
                        conv.sendMessageAsync(contents, extraContext = mapOf("enable_thinking" to false)).collect { chunk ->
                            out.append(chunk.toString())
                            onPartial(cleanThinking(out.toString()))
                        }
                    } catch (e: CancellationException) {
                        // إيقاف من المستخدم: نوقف النموذج نفسه أيضًا
                        try {
                            conv.cancelProcess()
                        } catch (t: Throwable) {
                        }
                        throw e
                    }
                }
            }
        } finally {
            scheduleIdleRelease()
        }
        return cleanThinking(out.toString())
    }

    /**
     * اختبار قراءة الصور على هذا الجهاز (بطلب المستخدم فقط): يحمّل المحرّك مع قارئ الصور على المعالج CPU
     * ويقرأ صورة فيها رقم معروف في محادثتين جديدتين متتاليتين، ثم يعرض النتيجة والزمن وذاكرة التطبيق.
     */
    suspend fun visionTest(): String {
        if (!installed(selected)) return L("نزّل النموذج أولًا", "Download the model first")
        if (!selected.vision) return L("هذا النموذج لا يقرأ الصور", "This model does not read images")
        val t0 = System.currentTimeMillis()
        return try {
            val png = withContext(Dispatchers.Default) { testImage() }
            val q = "What number is written in the image? Answer with only the number."
            val a = generate("You read images.", q, png, requireImage = true)
            val t1 = System.currentTimeMillis()
            val b = generate("You read images.", q, png, requireImage = true)
            val t2 = System.currentTimeMillis()
            val okA = a.contains("4719")
            val okB = b.contains("4719")
            val mb = android.os.Debug.getPss() / 1024L
            val times = L("الزمن: ${(t1 - t0) / 1000} ثانية ثم ${(t2 - t1) / 1000} ثانية، ذاكرة التطبيق نحو $mb ميغابايت",
                "Time: ${(t1 - t0) / 1000}s then ${(t2 - t1) / 1000}s, app memory about $mb MB")
            when {
                okA && okB -> "✓ " + L("قراءة الصور تعمل على المعالج. ", "Image reading works on the CPU. ") + times
                okA || okB -> "⚠ " + L("قرأ إحدى المرتين فقط، لا تعتمد عليه. ", "Read only one of two tries; do not rely on it. ") + times
                else -> "✗ " + L("عمل قارئ الصور لكن القراءة خاطئة. ", "The image reader ran but read wrongly. ") + times
            }
        } catch (e: VisionUnavailable) {
            "✗ " + L("لم يعمل قارئ الصور على المعالج في هذا الجهاز.", "The image reader did not start on the CPU on this device.")
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            "✗ " + (t.message ?: t.javaClass.simpleName)
        }
    }

    private fun testImage(): ByteArray {
        val bmp = android.graphics.Bitmap.createBitmap(640, 240, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.drawColor(android.graphics.Color.WHITE)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = android.graphics.Color.BLACK
        paint.textSize = 150f
        paint.isFakeBoldText = true
        c.drawText("4719", 90f, 175f, paint)
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
        bmp.recycle()
        return out.toByteArray()
    }

    /** يحذف «تفكير» النموذج إن ظهر في النص. */
    fun cleanThinking(s: String): String {
        var t = s.replace(Regex("(?s)<think>.*?</think>"), "")
        val open = t.indexOf("<think>")
        if (open >= 0) t = t.substring(0, open)
        return t.trimStart()
    }
}
