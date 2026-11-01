package com.mynotes.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.PointerType
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.hypot

/** عائلة القلم كما نستنتجها من الشركة المصنّعة ومحوّل الشاشة. */
enum class PenFamily { SAMSUNG, XIAOMI, HONOR, HUAWEI, LENOVO, USI, GENERIC }

/** شكل رسم القلم في الإعدادات (رسم داخل التطبيق، لا صور منتجات). */
data class PenShape(val slim: Boolean, val flat: Boolean, val button: Boolean, val eraser: Boolean)

fun PenFamily.shape(): PenShape = when (this) {
    PenFamily.SAMSUNG -> PenShape(slim = true, flat = false, button = true, eraser = false)
    PenFamily.XIAOMI -> PenShape(slim = false, flat = true, button = false, eraser = false)
    PenFamily.HONOR -> PenShape(slim = false, flat = true, button = true, eraser = false)
    PenFamily.HUAWEI -> PenShape(slim = false, flat = false, button = true, eraser = false)
    PenFamily.LENOVO -> PenShape(slim = false, flat = false, button = true, eraser = false)
    PenFamily.USI -> PenShape(slim = false, flat = false, button = false, eraser = true)
    PenFamily.GENERIC -> PenShape(slim = false, flat = false, button = false, eraser = false)
}

fun PenFamily.title(): String = when (this) {
    PenFamily.SAMSUNG -> "S Pen"
    PenFamily.XIAOMI -> L("قلم شاومي", "Xiaomi pen")
    PenFamily.HONOR -> L("قلم هونر", "Honor pen")
    PenFamily.HUAWEI -> L("قلم هواوي", "Huawei pen")
    PenFamily.LENOVO -> L("قلم لينوفو", "Lenovo pen")
    PenFamily.USI -> L("قلم USI", "USI pen")
    PenFamily.GENERIC -> L("قلم نشط", "Active pen")
}

fun PenFamily.maker(): String = when (this) {
    PenFamily.SAMSUNG -> L("قلم سامسونج", "Samsung pen")
    PenFamily.XIAOMI -> "Xiaomi"
    PenFamily.HONOR -> "Honor"
    PenFamily.HUAWEI -> "Huawei"
    PenFamily.LENOVO -> "Lenovo"
    PenFamily.USI -> L("معيار أقلام عام", "Universal stylus")
    PenFamily.GENERIC -> Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
}

/** قلم اكتشفه التطبيق على هذا الجهاز، مع ما عرفناه عنه وإعداداته. */
data class PenInfo(
    val key: String,
    val family: PenFamily,
    val deviceName: String,
    val customName: String = "",
    /** لون الرسم: 0 أسود، 1 عاجي، 2 فضي، 3 كحلي. */
    val finish: Int = 0,
    val hasPressure: Boolean = true,
    val hasTilt: Boolean = false,
    val seenHover: Boolean = false,
    val seenButton: Boolean = false,
    val seenEraser: Boolean = false,
    /** معايرة الضغط: أدنى وأعلى ضغط فعلي يرسله القلم أثناء الكتابة. */
    val lo: Float = 0f,
    val hi: Float = 1f,
    val calibrated: Boolean = false,
    /** القلم لا يرسل ضغطًا متغيرًا (عرض الخط يتبع السرعة). */
    val flatPressure: Boolean = false,
    val strokes: Int = 0,
    val lastUsed: Long = 0L
) {
    val displayName: String get() = customName.ifBlank { family.title() }

    fun toJson(): JSONObject = JSONObject()
        .put("key", key).put("family", family.name).put("device", deviceName)
        .put("name", customName).put("finish", finish)
        .put("pressure", hasPressure).put("tilt", hasTilt)
        .put("hover", seenHover).put("button", seenButton).put("eraser", seenEraser)
        .put("lo", lo.toDouble()).put("hi", hi.toDouble())
        .put("cal", calibrated).put("flat", flatPressure)
        .put("strokes", strokes).put("used", lastUsed)

    companion object {
        fun fromJson(o: JSONObject): PenInfo? = runCatching {
            PenInfo(
                key = o.getString("key"),
                family = runCatching { PenFamily.valueOf(o.getString("family")) }.getOrDefault(PenFamily.GENERIC),
                deviceName = o.optString("device"),
                customName = o.optString("name"),
                finish = o.optInt("finish", 0).coerceIn(0, 3),
                hasPressure = o.optBoolean("pressure", true),
                hasTilt = o.optBoolean("tilt", false),
                seenHover = o.optBoolean("hover"),
                seenButton = o.optBoolean("button"),
                seenEraser = o.optBoolean("eraser"),
                lo = o.optDouble("lo", 0.0).toFloat(),
                hi = o.optDouble("hi", 1.0).toFloat(),
                calibrated = o.optBoolean("cal"),
                flatPressure = o.optBoolean("flat"),
                strokes = o.optInt("strokes"),
                lastUsed = o.optLong("used")
            )
        }.getOrNull()
    }
}

/**
 * يتعلّم مدى ضغط القلم من الكتابة نفسها: مدرّج تكراري يتلاشى ببطء،
 * ومنه أدنى ضغط (2%) وأعلاه (98%).
 */
private class PressureHistogram {
    val bins = FloatArray(64)
    var total = 0f
    var samples = 0

    fun feed(p: Float) {
        if (p <= 0f || p.isNaN()) return
        val i = (p.coerceIn(0f, 1f) * (bins.size - 1)).toInt()
        bins[i] += 1f
        total += 1f
        samples++
        // تلاشٍ بطيء: القلم أو اليد قد تتغير، والقديم يفقد وزنه تدريجيًا
        if (total > 6000f) {
            for (k in bins.indices) bins[k] *= 0.5f
            total *= 0.5f
        }
    }

    fun percentile(q: Float): Float {
        if (total <= 0f) return 0f
        val target = total * q
        var acc = 0f
        for (k in bins.indices) {
            acc += bins[k]
            if (acc >= target) return k / (bins.size - 1f)
        }
        return 1f
    }

    fun reset() {
        bins.fill(0f); total = 0f; samples = 0
    }
}

/** عرض الخط حسب السرعة: للأقلام التي لا ترسل ضغطًا وللإصبع. */
class SpeedWidth {
    private var lx = 0f
    private var ly = 0f
    private var lt = -1L
    private var v = 0.6f

    /** x و y بالبكسل، والعرض بالبكسل (لتوحيد السرعة بين الشاشات). */
    fun next(x: Float, y: Float, t: Long, widthPx: Float): Float {
        if (lt < 0L || widthPx <= 0f) {
            lx = x; ly = y; lt = t
            return v
        }
        val dt = (t - lt).coerceAtLeast(1L)
        // عرض الصفحة في الثانية
        val speed = hypot(x - lx, y - ly) / widthPx * 1000f / dt
        lx = x; ly = y; lt = t
        val target = (0.78f - 0.22f * speed).coerceIn(0.38f, 0.8f)
        v += (target - v) * 0.25f
        return v
    }
}

/**
 * يراقب أحداث القلم في النشاط كله (قبل أن تصل إلى الواجهة):
 * يتعرّف على القلم وقدراته، ويعاير الضغط، ويقرأ الميل، ويعرف هل القلم قريب من الشاشة.
 */
object PenInput {
    private lateinit var sp: SharedPreferences
    private var ready = false

    /** الأقلام المعروفة على هذا الجهاز (الأحدث استخدامًا أولًا). */
    val pens = mutableStateListOf<PenInfo>()

    /** وضع الكتابة: الإصبع يكتب أيضًا (للأقلام العادية غير النشطة). */
    var fingerWrites by mutableStateOf(false)
        private set
    /** اختار المستخدم الوضع بنفسه (فلا نغيّره تلقائيًا بعد ذلك). */
    private var modeChosen = false

    /** زر التبديل بين القلم والإصبع ظاهر في شريط الأدوات. */
    var showToggle by mutableStateOf(true)
        private set

    // ---- حالة حية لشاشة «الأقلام» (تُحدَّث فقط وهي مفتوحة) ----
    var monitoring = 0
    var liveKey by mutableStateOf<String?>(null)
        private set
    var liveNear by mutableStateOf(false)
        private set
    var livePressure by mutableStateOf(0f)
        private set
    var liveTilt by mutableStateOf(0f)
        private set
    var liveRate by mutableStateOf(0)
        private set

    // ---- حالة داخلية سريعة (بلا إعادة رسم) ----
    @Volatile var lastStylusAt = 0L
        private set
    @Volatile var stylusDown = false
        private set
    /** ميل القلم الحالي بالراديان (0 = عمودي على الشاشة). */
    @Volatile var tilt = 0f
        private set
    private var currentKey: String? = null
    private val keyOfDevice = HashMap<Int, String>()
    private val hist = HashMap<String, PressureHistogram>()
    private var rateCount = 0
    private var rateFrom = 0L
    private var dirty = false

    fun init(ctx: Context) {
        if (ready) return
        sp = ctx.getSharedPreferences("hamish_pens", Context.MODE_PRIVATE)
        ready = true
        runCatching {
            val arr = JSONArray(sp.getString("pens", "[]"))
            for (i in 0 until arr.length()) PenInfo.fromJson(arr.getJSONObject(i))?.let { pens.add(it) }
        }
        pens.sortByDescending { it.lastUsed }
        showToggle = sp.getBoolean("show_toggle", true)
        modeChosen = sp.getBoolean("mode_chosen", false)
        fingerWrites = if (modeChosen) sp.getBoolean("finger_writes", false)
        else !(hasStylusHardware() || pens.isNotEmpty())
    }

    /** هل في الجهاز محوّل قلم (مثل S Pen) حتى قبل أن يلمس القلم الشاشة. */
    fun hasStylusHardware(): Boolean = runCatching {
        InputDevice.getDeviceIds().any { id ->
            val d = InputDevice.getDevice(id) ?: return@any false
            (d.sources and InputDevice.SOURCE_STYLUS) == InputDevice.SOURCE_STYLUS ||
                (d.sources and InputDevice.SOURCE_BLUETOOTH_STYLUS) == InputDevice.SOURCE_BLUETOOTH_STYLUS
        }
    }.getOrDefault(false)

    fun chooseFingerWrites(on: Boolean) {
        fingerWrites = on
        modeChosen = true
        if (ready) sp.edit().putBoolean("finger_writes", on).putBoolean("mode_chosen", true).apply()
    }

    fun changeShowToggle(on: Boolean) {
        showToggle = on
        if (ready) sp.edit().putBoolean("show_toggle", on).apply()
    }

    fun rename(key: String, name: String) = update(key) { it.copy(customName = name.trim().take(30)) }
    fun changeFinish(key: String, f: Int) = update(key) { it.copy(finish = f.coerceIn(0, 3)) }

    /** يبدأ معايرة الضغط من جديد لهذا القلم. */
    fun recalibrate(key: String) {
        hist[key]?.reset()
        update(key) { it.copy(calibrated = false, flatPressure = false, lo = 0f, hi = 1f, strokes = 0) }
    }

    fun forget(key: String) {
        pens.removeAll { it.key == key }
        hist.remove(key)
        save()
    }

    /** القلم النشط الآن (أو آخر قلم استُخدم). */
    fun current(): PenInfo? = pens.firstOrNull { it.key == currentKey } ?: pens.firstOrNull()

    /** القلم قريب من الشاشة أو يلمسها الآن أو قبل لحظة: لمسات الإصبع تُتجاهل (راحة اليد). */
    fun stylusActiveRecently(ms: Long = 600L): Boolean =
        stylusDown || SystemClock.uptimeMillis() - lastStylusAt < ms

    /**
     * ضغط النقطة بعد المعايرة والميل وحساسية المستخدم.
     * الإصبع والأقلام بلا ضغط: العرض يتبع السرعة.
     */
    fun pressureFor(type: PointerType, raw: Float, tool: Tool, speed: SpeedWidth, x: Float, y: Float, t: Long, widthPx: Float): Float {
        val pen = if (type == PointerType.Stylus || type == PointerType.Eraser) current() else null
        val base = when {
            pen == null -> speed.next(x, y, t, widthPx)
            pen.flatPressure || raw <= 0f -> speed.next(x, y, t, widthPx)
            pen.calibrated && pen.hi - pen.lo > 0.05f -> 0.2f + 0.7f * ((raw - pen.lo) / (pen.hi - pen.lo)).coerceIn(0f, 1f)
            else -> raw
        }
        // الفرشاة: إمالة القلم تعرّض الخط كفرشاة حقيقية
        val tilted = if (tool == Tool.BRUSH && pen != null && pen.hasTilt) {
            base * (1f + 0.6f * (tilt / (PI.toFloat() / 2f)).coerceIn(0f, 1f))
        } else base
        return PenPrefs.applyPressure(tilted.coerceIn(0.05f, 1f))
    }

    /** يُستدعى من النشاط لكل حدث لمس أو تحويم. */
    fun observe(ev: MotionEvent) {
        if (!ready) return
        var idx = -1
        for (i in 0 until ev.pointerCount) {
            val tt = ev.getToolType(i)
            if (tt == MotionEvent.TOOL_TYPE_STYLUS || tt == MotionEvent.TOOL_TYPE_ERASER) { idx = i; break }
        }
        if (idx < 0) return
        val now = SystemClock.uptimeMillis()
        lastStylusAt = now
        val action = ev.actionMasked
        val hover = action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE ||
            action == MotionEvent.ACTION_HOVER_EXIT
        val key = keyFor(ev.deviceId)
        val known = pens.indexOfFirst { it.key == key }
        if (known < 0) {
            addPen(key, ev.deviceId)
            // أول مرة نرى قلمًا: إن كان الإصبع يكتب تلقائيًا (لا باختيار المستخدم) نعيده للتمرير
            if (fingerWrites && !modeChosen) {
                fingerWrites = false
                AppController.messages.tryEmit(L("اكتشفنا قلمًا: صار الإصبع للتمرير والقلم للكتابة", "Pen detected: your finger scrolls and the pen writes"))
            }
        }
        currentKey = key
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> stylusDown = true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> stylusDown = false
        }

        tilt = runCatching { ev.getAxisValue(MotionEvent.AXIS_TILT, idx) }.getOrDefault(0f)

        // معايرة الضغط من النقاط الفعلية (مع النقاط بين الإطارات)
        if (!hover && action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL &&
            ev.getToolType(idx) == MotionEvent.TOOL_TYPE_STYLUS
        ) {
            val h = hist.getOrPut(key) { PressureHistogram() }
            for (k in 0 until ev.historySize) h.feed(ev.getHistoricalPressure(idx, k))
            h.feed(ev.getPressure(idx))
        }

        // ما رأيناه من القلم
        val btn = (ev.buttonState and (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY)) != 0
        val eraser = ev.getToolType(idx) == MotionEvent.TOOL_TYPE_ERASER
        val p = pens.firstOrNull { it.key == key }
        if (p != null && ((hover && !p.seenHover) || (btn && !p.seenButton) || (eraser && !p.seenEraser))) {
            update(key) { it.copy(seenHover = it.seenHover || hover, seenButton = it.seenButton || btn, seenEraser = it.seenEraser || eraser) }
        }

        if (action == MotionEvent.ACTION_UP) finishStroke(key, now)

        if (monitoring > 0) {
            liveKey = key
            liveNear = action != MotionEvent.ACTION_HOVER_EXIT && action != MotionEvent.ACTION_CANCEL
            if (!hover && action != MotionEvent.ACTION_UP) livePressure = ev.getPressure(idx).coerceIn(0f, 1f)
            if (action == MotionEvent.ACTION_UP || hover) livePressure = 0f
            liveTilt = tilt
            rateCount += 1 + ev.historySize
            if (now - rateFrom >= 500L) {
                liveRate = if (hover || action == MotionEvent.ACTION_UP) liveRate else (rateCount * 1000L / (now - rateFrom).coerceAtLeast(1L)).toInt()
                rateCount = 0
                rateFrom = now
            }
        }
    }

    private fun keyFor(deviceId: Int): String = keyOfDevice.getOrPut(deviceId) {
        val d = InputDevice.getDevice(deviceId)
        // المعرّف الثابت للجهاز (لا يتغير بإعادة التشغيل)
        d?.descriptor?.takeIf { it.isNotBlank() } ?: "dev-${d?.name ?: deviceId}"
    }

    private fun addPen(key: String, deviceId: Int) {
        val d = InputDevice.getDevice(deviceId)
        val name = d?.name.orEmpty()
        val pressureRange = d?.getMotionRange(MotionEvent.AXIS_PRESSURE, InputDevice.SOURCE_STYLUS)
        val tiltRange = d?.getMotionRange(MotionEvent.AXIS_TILT, InputDevice.SOURCE_STYLUS)
        val info = PenInfo(
            key = key,
            family = familyOf(name),
            deviceName = name,
            hasPressure = pressureRange == null || pressureRange.range > 0f,
            hasTilt = tiltRange != null && tiltRange.range > 0f,
            lastUsed = System.currentTimeMillis()
        )
        pens.add(0, info)
        save()
    }

    /** بعد كل خط: نحدّث المعايرة ونحفظها كل بضعة خطوط. */
    private fun finishStroke(key: String, now: Long) {
        val h = hist[key]
        update(key, persist = false) { p ->
            var q = p.copy(strokes = p.strokes + 1, lastUsed = System.currentTimeMillis())
            if (h != null && h.samples >= 250 && q.strokes >= 4) {
                val lo = h.percentile(0.02f)
                val hi = h.percentile(0.98f)
                q = if (hi - lo < 0.04f) q.copy(flatPressure = true, calibrated = true, lo = lo, hi = hi)
                else q.copy(flatPressure = false, calibrated = true, lo = lo, hi = hi)
            }
            q
        }
        // الأحدث استخدامًا أولًا
        val i = pens.indexOfFirst { it.key == key }
        if (i > 0) pens.add(0, pens.removeAt(i))
        dirty = true
        val p = pens.firstOrNull { it.key == key }
        if (p != null && (p.strokes <= 6 || p.strokes % 10 == 0)) save()
    }

    private fun update(key: String, persist: Boolean = true, f: (PenInfo) -> PenInfo) {
        val i = pens.indexOfFirst { it.key == key }
        if (i < 0) return
        pens[i] = f(pens[i])
        if (persist) save() else dirty = true
    }

    /** يحفظ الأقلام (يُستدعى أيضًا عند إيقاف التطبيق مؤقتًا). */
    fun save() {
        if (!ready) return
        val arr = JSONArray()
        pens.forEach { arr.put(it.toJson()) }
        sp.edit().putString("pens", arr.toString()).apply()
        dirty = false
    }

    fun flush() {
        if (dirty) save()
    }

    /** العائلة من الشركة المصنّعة واسم محوّل الشاشة. */
    fun familyOf(deviceName: String): PenFamily {
        val n = deviceName.lowercase()
        val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return when {
            "usi" in n -> PenFamily.USI
            "samsung" in m || "e-pen" in n || "epen" in n -> PenFamily.SAMSUNG
            "xiaomi" in m || "redmi" in m || "poco" in m -> PenFamily.XIAOMI
            "honor" in m -> PenFamily.HONOR
            "huawei" in m -> PenFamily.HUAWEI
            "lenovo" in m || "motorola" in m -> PenFamily.LENOVO
            else -> PenFamily.GENERIC
        }
    }

    /** الضغط الخام بنسبة مئوية بعد المعايرة (لشاشة «جرّب قلمك»). */
    fun calibratedPercent(raw: Float): Int {
        val p = current() ?: return (raw * 100).toInt()
        if (raw <= 0f) return 0
        if (!p.calibrated || p.flatPressure || p.hi - p.lo <= 0.05f) return (raw * 100).toInt()
        return (((raw - p.lo) / (p.hi - p.lo)).coerceIn(0f, 1f) * 100).toInt()
    }
}

/**
 * النوافذ المنبثقة (مثل الإعدادات) نوافذ مستقلة لا تمر أحداثها بالنشاط:
 * نراقب أحداث القلم فيها أيضًا ما دامت مفتوحة.
 */
@androidx.compose.runtime.Composable
fun PenWindowObserver() {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(view) {
        val window = generateSequence(view as android.view.View?) { it.parent as? android.view.View }
            .firstNotNullOfOrNull { (it as? androidx.compose.ui.window.DialogWindowProvider)?.window }
        val orig = window?.callback
        if (window == null || orig == null) return@DisposableEffect onDispose {}
        val wrapper = object : android.view.Window.Callback by orig {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                PenInput.observe(event)
                return orig.dispatchTouchEvent(event)
            }

            override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
                PenInput.observe(event)
                return orig.dispatchGenericMotionEvent(event)
            }
        }
        window.callback = wrapper
        onDispose { if (window.callback === wrapper) window.callback = orig }
    }
}
