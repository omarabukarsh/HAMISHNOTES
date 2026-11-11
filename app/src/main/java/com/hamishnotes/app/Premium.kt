package com.hamishnotes.app

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException
import java.lang.ref.WeakReference
import kotlin.random.Random

/** الخصائص المدفوعة في especial notes Premium. */
enum class PremiumFeature(val icon: Int) {
    DOCS(R.drawable.ph_books),
    SUMMARIES(R.drawable.ph_summary),
    SNAP(R.drawable.ph_snap),
    OCR(R.drawable.ph_scan),
    TO_TEXT(R.drawable.ph_text_scan),
    TRANSLATE(R.drawable.ph_translate),
    RECORD(R.drawable.ph_mic),
    SPLIT(R.drawable.ph_split),
    STATS(R.drawable.ph_timer),
    CARDS(R.drawable.ph_cards),
    LOCK(R.drawable.ph_lock);

    val title: String
        get() = when (this) {
            DOCS -> L("مستندات بلا حد", "Unlimited documents")
            SUMMARIES -> L("الملخصات", "Summaries")
            SNAP -> L("التقاط النص", "Text snap")
            OCR -> L("قراءة النص", "Read text")
            TO_TEXT -> L("تحويل الكتابة إلى نص", "Handwriting to text")
            TRANSLATE -> L("الترجمة", "Translation")
            RECORD -> L("تسجيل المحاضرة", "Lecture recording")
            SPLIT -> L("مستندان جنبًا إلى جنب", "Split view")
            STATS -> L("الإحصاءات الكاملة", "Full study stats")
            CARDS -> L("البطاقات", "Cards")
            LOCK -> L("قفل المستندات", "Document lock")
        }

    val benefit: String
        get() = when (this) {
            DOCS -> L("أضف مستندات وملاحظات بلا حد.", "Add as many documents and notes as you like.")
            SUMMARIES -> L("كل ما تظلله يُجمع مرتبًا في ملخص تعدّله بنفسك.", "Everything you highlight is collected into a neat summary you can edit.")
            SNAP -> L("يلتصق الهايلايتر بالكلمات ويُضاف نصها إلى الملخص.", "The highlighter snaps to words and adds their text to your summary.")
            OCR -> L("اقرأ نص الصفحات ليعمل البحث والتقاط النص في أي ملف.", "Read page text so search and text snap work in any file.")
            TO_TEXT -> L("حوّل كتابتك اليدوية إلى نص مكتوب بلمسة.", "Turn your handwriting into typed text in one tap.")
            TRANSLATE -> L("حدد أي نص إنجليزي واحصل عليه بالعربية فورًا، على جهازك.", "Select any English text and get it in Arabic instantly, right on your device.")
            RECORD -> L("سجّل المحاضرة واسمع ما قيل لحظة كتابة كل سطر.", "Record the lecture and hear what was said as you wrote each line.")
            SPLIT -> L("اعرض الكتاب والملخص جنبًا إلى جنب.", "Show your book and notes side by side.")
            STATS -> L("مخطط الشهر الكامل وتقرير وقتك في كل مادة.", "The full monthly chart and a report of your time in each subject.")
            CARDS -> L("اصنع بطاقات مراجعة واقلبها للمذاكرة.", "Make review cards and flip them to study.")
            LOCK -> L("اقفل مستنداتك بالرمز أو البصمة.", "Lock your documents with a PIN or fingerprint.")
        }
}

/**
 * حالة الاشتراك.
 *
 * نسخة المتجر: Premium مفتوح فقط بقسيمة موقّعة من الخادم (Lic)، يصدرها بعد أن يتحقق من الشراء
 * عند Google ومن أن التطبيق نسخة أصلية من Google Play (Play Integrity). القسيمة تعمل دون إنترنت
 * 7 أيام ثم تتجدد بصمت.
 *
 * النسخة التجريبية (موقّعة بمفتاح التجربة، BuildConfig.TEST_PREMIUM): زر الاشتراك يفعّل وضعًا تجريبيًا
 * محليًا. هذا الكود كله يُحذف من نسخة المتجر عند البناء.
 */
object Premium {
    const val FREE_DOCS = 3
    const val TRIAL_DAYS = 7
    const val YEAR_PRICE = "$4"
    private const val DAY = 24 * 60 * 60 * 1000L
    /** فرق مسموح في ساعة الجهاز قبل أن نعدّها أُرجعت عمدًا. */
    private const val CLOCK_SLACK = 2 * 60 * 60 * 1000L

    private var sp: SharedPreferences? = null
    private var app: Context? = null
    private var activity: WeakReference<Activity>? = null
    private var testActive = false

    var active by mutableStateOf(false)
        private set
    /** القسيمة الصالحة الآن (نسخة المتجر)؛ null في المجاني أو الوضع التجريبي. */
    internal var grant by mutableStateOf<Lic.Grant?>(null)
        private set
    /** خاصية مقفلة ضغطها المستخدم الآن: تظهر نافذة تشرحها. */
    var lockedFeature by mutableStateOf<PremiumFeature?>(null)
    var paywallOpen by mutableStateOf(false)
    var limitOpen by mutableStateOf(false)

    val testMode: Boolean get() = BuildConfig.TEST_PREMIUM

    fun init(context: Context) {
        val c = context.applicationContext
        app = c
        val p = c.getSharedPreferences("premium", Context.MODE_PRIVATE)
        sp = p
        Cloud.init(c)
        Cloud.onSessionExpired = {
            AppController.scope.launch {
                if (AppController.accountEmail != null) {
                    AppController.signOutAccount()
                    AppController.say(MsgKind.ERROR, L("انتهت جلستك، سجّل الدخول بحساب Google مجددًا", "Your session ended, please sign in with Google again"))
                }
            }
        }
        if (BuildConfig.TEST_PREMIUM) testActive = p.getBoolean("test_active", false)
        evaluate()
        if (!BuildConfig.TEST_PREMIUM && Cloud.configured) {
            PlayBilling.init(c)
            // الفحص لا يحدث لحظة الفتح بل بعد وقت عشوائي، فيصعب تتبّعه
            AppController.scope.launch {
                delay(Random.nextLong(20_000, 75_000))
                background()
            }
            // تربّص متأخر مستقل: فحص سلامة طازج (يتجاوز المخزّن) من موضع وزمن مختلفين عن evaluate.
            // إن كُسر فحص التوقيع في موضعه المعتاد، تُسحب القسيمة هنا بهدوء دون رسالة تدل على موضع الفحص.
            AppController.scope.launch {
                delay(Random.nextLong(90_000, 240_000))
                if (grant != null && !Lic.signerOk(c, fresh = true)) {
                    sp?.edit()?.remove("grant")?.apply()
                    evaluate()
                }
            }
        }
    }

    /** النشاط الظاهر الآن: تحتاجه نوافذ Google (الدفع وإصلاح التثبيت). */
    fun attach(a: Activity) {
        activity = WeakReference(a)
    }

    fun activateTest() {
        if (!BuildConfig.TEST_PREMIUM) return
        testActive = true
        sp?.edit()?.putBoolean("test_active", true)?.apply()
        evaluate()
    }

    fun endTest() {
        if (!BuildConfig.TEST_PREMIUM) return
        testActive = false
        sp?.edit()?.putBoolean("test_active", false)?.apply()
        evaluate()
    }

    /**
     * يقرر حالة الاشتراك من القسيمة المحفوظة، دون إنترنت.
     *
     * حارس إرجاع الساعة: نحفظ أكبر وقت رآه الجهاز (seen). إن رجعت الساعة إلى الوراء أكثر من المهلة
     * فالقسيمة لا تُقبل، فلا يمكن إطالة القسيمة بضبط الساعة على تاريخ قديم مع قطع الإنترنت.
     * وإن كانت الساعة متقدمة خطأً ثم صُحّحت، يُشفى المستخدم عند أول قسيمة جديدة من الخادم،
     * لأن seen يُضبط حينها على وقت الإصدار (iat)، وهو وقت الخادم الموثوق.
     */
    internal fun evaluate() {
        val c = app ?: return
        val p = sp ?: return
        val now = System.currentTimeMillis()
        val seen = p.getLong("seen", 0)
        if (now > seen) p.edit().putLong("seen", now).apply()
        val g = Lic.parse(p.getString("grant", null))
        val ok = g != null && valid(c, g, now) && now + CLOCK_SLACK > seen
        grant = if (ok) g else null
        active = (BuildConfig.TEST_PREMIUM && testActive) || ok
    }

    /**
     * صلاحية القسيمة دون إنترنت: نفس الحزمة والجهاز والحساب، وضمن مدتها، والساعة ليست قبل وقت إصدارها.
     * Premium يتبع الحساب، فيتوقف عند تسجيل الخروج (uid=null). القسيمة لا تُحذف (انظر onAccountChanged)،
     * فيعود Premium فور الدخول بنفس الحساب، دون انتظار التحقق الخلفي من الخادم.
     */
    internal fun valid(c: Context, g: Lic.Grant, now: Long): Boolean =
        g.pkg == c.packageName && g.dev == Lic.device(c) && g.sub == Cloud.userId &&
            now < g.until && now + CLOCK_SLACK > g.iat && Lic.signerOk(c)

    /**
     * فحص ثانٍ داخل الخصائص نفسها (لا في الواجهة فقط): يعيد قراءة القسيمة والتحقق منها،
     * فلا يكفي تغيير قيمة واحدة لفتح كل شيء. إن لم تصح يعود التطبيق بهدوء إلى المجاني.
     */
    internal fun confirm(): Boolean {
        if (BuildConfig.TEST_PREMIUM) return active
        val c = app ?: return false
        val g = Lic.parse(sp?.getString("grant", null))
        val ok = g != null && valid(c, g, System.currentTimeMillis())
        if (!ok && active) evaluate()
        return ok
    }

    /**
     * بعد الدخول أو الخروج من حساب Google.
     * تسجيل الخروج لا يحذف القسيمة (تبقى مربوطة بالجهاز فيعود Premium فور الدخول بنفس الحساب).
     * الدخول بحساب مختلف عن صاحب القسيمة يحذفها، فلا تنتقل من حساب إلى آخر.
     */
    fun onAccountChanged() {
        val uid = Cloud.userId
        val g = Lic.parse(sp?.getString("grant", null))
        if (uid != null && g != null && g.sub != uid) sp?.edit()?.remove("grant")?.apply()
        evaluate()
    }

    /** true إن كانت الخاصية متاحة؛ وإلا تظهر نافذة الخاصية المقفلة. */
    fun require(f: PremiumFeature): Boolean {
        // لا نكتفي بالراية active في الذاكرة: نعيد اشتقاق الحق من القسيمة الموقّعة نفسها (confirm)،
        // فقلب قيمة واحدة لا يفتح الخاصية ما لم يُهزم توقيع القسيمة وفحص سلامة التطبيق أيضًا.
        if (active && (BuildConfig.TEST_PREMIUM || confirm())) return true
        lockedFeature = f
        return false
    }

    fun freeSlots(): Int = (FREE_DOCS - AppController.library.size).coerceAtLeast(0)

    /** هل يمكن إضافة n مستندات؟ وإلا تظهر نافذة الحد. */
    fun canAdd(n: Int = 1): Boolean {
        if (active || AppController.library.size + n <= FREE_DOCS) return true
        limitOpen = true
        return false
    }

    // ---------- الشراء والتحقق (نسخة المتجر) ----------

    private enum class Outcome { OK, NONE, UNLICENSED, DEVICE, OTHER_ACCOUNT, OFFLINE, SIGN_IN, FAILED }

    /** زر الاشتراك في الشاشة. */
    fun startPurchase(a: Activity, onDone: () -> Unit = {}) {
        if (BuildConfig.TEST_PREMIUM) {
            activateTest()
            AppController.say(MsgKind.DONE, L("فُعّل Premium (وضع تجريبي)", "Premium activated (test mode)"))
            onDone()
            return
        }
        AppController.scope.launch {
            if (!Cloud.configured) {
                AppController.say(MsgKind.ERROR, L("الاشتراك غير متاح في هذه النسخة", "Subscriptions aren’t available in this build"))
                return@launch
            }
            val uid = Cloud.userId ?: signInForPurchase(a) ?: return@launch
            if (!PlayBilling.buy(a, uid)) {
                // أجهزة بلا خدمات Google (مثل هواوي الحديثة): المجاني يبقى كما هو، والاشتراك يحتاج Google Play
                AppController.say(MsgKind.ERROR, L("الاشتراك يحتاج Google Play على هذا الجهاز", "Subscribing needs Google Play on this device"))
            }
            onDone()
        }
    }

    /** الاشتراك يُربط بحسابك (فينتقل معك إلى أي جهاز): نطلب الدخول بـ Google إن لم يكن. */
    private suspend fun signInForPurchase(a: Activity): String? {
        AppController.say(MsgKind.ERROR, L("سجّل الدخول بحساب Google ليُربط اشتراكك به", "Sign in with Google so your subscription is linked to you"))
        val r = GoogleAuth.signIn(a)
        r.onSuccess { (email, name) -> AppController.setAccount(email, name) }
            .onFailure { AppController.say(MsgKind.DONE, GoogleAuth.messageFor(it)) }
        return Cloud.userId
    }

    /** رمز شراء جديد من Google Play: يُرسل إلى الخادم ليتحقق منه ويصدر القسيمة. */
    internal fun submitPurchase(purchaseToken: String, done: () -> Unit = {}) {
        AppController.scope.launch {
            val o = call("verify", purchaseToken)
            report(o, success = L("مرحبًا بك في especial notes Premium", "Welcome to especial notes Premium"))
            if (o == Outcome.OK) paywallOpen = false
            done()
        }
    }

    /** «استعادة الشراء»، وأيضًا تلقائيًا بعد إعادة التثبيت أو على جهاز جديد. */
    fun restore(quiet: Boolean) {
        if (BuildConfig.TEST_PREMIUM) {
            if (!quiet) AppController.say(MsgKind.ERROR, L("الاستعادة تعمل في نسخة المتجر", "Restore works in the store version"))
            return
        }
        AppController.scope.launch {
            if (Cloud.userId == null) {
                if (!quiet) AppController.say(MsgKind.ERROR, L("سجّل الدخول بحساب Google الذي اشتركت به", "Sign in with the Google account you subscribed with"))
                return@launch
            }
            val owned = PlayBilling.owned().firstOrNull()
            val o = if (owned != null) call("verify", owned.purchaseToken) else call("refresh", null)
            if (!quiet || o == Outcome.OK) report(o, success = L("استُعيد اشتراكك", "Your subscription is restored"))
        }
    }

    /** يجدد القسيمة بصمت إن قدمت، ويكتشف اشتراكًا موجودًا إن لم توجد قسيمة. */
    private suspend fun background() {
        val now = System.currentTimeMillis()
        val g = Lic.parse(sp?.getString("grant", null))
        if (g != null) {
            if (grant == null || now - g.iat > DAY || g.until - now < 2 * DAY) report(call("refresh", null), success = null)
        } else if (Cloud.userId != null) {
            PlayBilling.owned().firstOrNull()?.let { report(call("verify", it.purchaseToken), success = null) }
        }
        PlayBilling.load()
    }

    private suspend fun call(action: String, purchaseToken: String?): Outcome {
        val c = app ?: return Outcome.FAILED
        val uid = Cloud.userId ?: return Outcome.SIGN_IN
        val dev = Lic.device(c)
        val ts = System.currentTimeMillis()
        // بصمة الطلب: رمز السلامة يصلح لهذا الطلب بعينه فقط، فلا يُعاد استخدامه
        val hash = Lic.sha256(listOf(action, uid, dev, ts.toString(), purchaseToken.orEmpty()).joinToString("|"))
        // تعذّر الحصول على رمز (لا إنترنت، أو خدمات Play غير متاحة): لا نعاقب، ونحاول لاحقًا
        val integrity = try {
            Integrity.token(c, hash)
        } catch (e: Throwable) {
            return Outcome.FAILED
        }
        val body = JSONObject().put("action", action).put("device", dev).put("ts", ts).put("integrity", integrity)
        purchaseToken?.let { body.put("purchaseToken", it) }
        return try {
            val token = Cloud.function("license", body).getString("grant")
            val g = Lic.parse(token)
            if (g == null || !valid(c, g, System.currentTimeMillis())) return Outcome.FAILED
            // قسيمة جديدة من الخادم: وقت إصدارها موثوق، فنصحح به حارس الساعة (يشفي ساعة كانت متقدمة ثم صُحّحت)
            sp?.edit()?.putString("grant", token)?.putLong("seen", g.iat)?.apply()
            evaluate()
            Outcome.OK
        } catch (e: Cloud.HttpError) {
            when (e.body?.optString("error")) {
                "unlicensed", "unrecognized" -> Outcome.UNLICENSED
                "device" -> Outcome.DEVICE
                "owned_by_other" -> Outcome.OTHER_ACCOUNT
                "no_subscription" -> {
                    sp?.edit()?.remove("grant")?.apply()
                    evaluate()
                    Outcome.NONE
                }
                else -> if (e.code == 401) Outcome.SIGN_IN else Outcome.FAILED
            }
        } catch (e: IOException) {
            Outcome.OFFLINE
        } catch (e: Throwable) {
            Outcome.FAILED
        }
    }

    /** ردود لطيفة: لا إغلاق ولا حذف لأي بيانات، فقط رسالة ونافذة Google عند الحاجة. */
    private suspend fun report(o: Outcome, success: String?) {
        val a = activity?.get()
        when (o) {
            Outcome.OK -> success?.let { AppController.say(MsgKind.DONE, it) }
            Outcome.NONE -> if (success != null) AppController.say(MsgKind.ERROR, L("لا يوجد اشتراك فعّال في هذا الحساب", "No active subscription on this account"))
            Outcome.UNLICENSED -> {
                AppController.say(MsgKind.ERROR, L("Premium يعمل مع نسخة especial notes من Google Play فقط", "Premium works only with especial notes from Google Play"))
                if (a != null) Integrity.showGetLicensed(a)
            }
            Outcome.DEVICE -> {
                AppController.say(MsgKind.ERROR, L("تعذّر التحقق من سلامة هذا الجهاز", "This device couldn’t be verified"))
                if (a != null) Integrity.showGetIntegrity(a)
            }
            Outcome.OTHER_ACCOUNT -> AppController.say(MsgKind.ERROR, L("هذا الاشتراك مربوط بحساب especial notes آخر", "This subscription is linked to another especial notes account"))
            Outcome.OFFLINE -> if (success != null) AppController.say(MsgKind.ERROR, L("تحقق من اتصالك بالإنترنت", "Check your internet connection"))
            Outcome.SIGN_IN -> if (success != null) AppController.say(MsgKind.ERROR, L("سجّل الدخول بحساب Google أولًا", "Sign in with Google first"))
            Outcome.FAILED -> if (success != null) AppController.say(MsgKind.ERROR, L("تعذّر التحقق من الاشتراك، حاول لاحقًا", "Couldn’t verify the subscription, try later"))
        }
    }
}

private val PwGold = Color(0xFFF4C95D)
private val PwGoldDeep = Color(0xFFB8860B)
private val PwInk = Color(0xFF1E2A44)
private val PwMuted = Color(0xFF6B7280)
private val PwLine = Color(0xFFEFE7D4)
private val PwGoldButton = Brush.horizontalGradient(listOf(Color(0xFFF4C95D), Color(0xFFE9B73E)))

/** نوافذ Premium كلها، تُعرض من جذر التطبيق. */
@Composable
fun PremiumHost() {
    Premium.lockedFeature?.let { f ->
        LockedFeatureSheet(f, onDismiss = { Premium.lockedFeature = null }) {
            Premium.lockedFeature = null
            Premium.paywallOpen = true
        }
    }
    if (Premium.limitOpen) {
        LimitSheet(onDismiss = { Premium.limitOpen = false }) {
            Premium.limitOpen = false
            Premium.paywallOpen = true
        }
    }
    if (Premium.paywallOpen) PaywallDialog(onDismiss = { Premium.paywallOpen = false })
}

/** قفل ذهبي صغير يوضع على زاوية زر أداة مدفوعة. */
@Composable
fun PremiumBadge(modifier: Modifier = Modifier, size: Dp = 14.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(PwGold),
        contentAlignment = Alignment.Center
    ) { PhIcon(R.drawable.ph_lock, PwInk, size * 0.62f) }
}

@Composable
private fun LockedFeatureSheet(f: PremiumFeature, onDismiss: () -> Unit, onSee: () -> Unit) {
    OriginDialog(onDismiss = onDismiss) { close ->
        SheetCard {
            FeatureIcon(f.icon)
            Spacer(Modifier.height(18.dp))
            Text(
                L("${f.title} من مزايا Premium", "${f.title} is part of Premium"), fontFamily = Fonts.Display,
                fontWeight = FontWeight.Bold, fontSize = 20.sp, color = PwInk, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(f.benefit, fontSize = 14.sp, lineHeight = 21.sp, color = PwMuted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(22.dp))
            GoldButton(L("شاهد Premium · ${Premium.TRIAL_DAYS} أيام مجانًا", "See Premium · ${Premium.TRIAL_DAYS} days free"), onSee)
            QuietButton(L("ليس الآن", "Not now"), close)
        }
    }
}

@Composable
private fun LimitSheet(onDismiss: () -> Unit, onUpgrade: () -> Unit) {
    OriginDialog(onDismiss = onDismiss) { close ->
        SheetCard {
            FeatureIcon(R.drawable.ph_books)
            Spacer(Modifier.height(18.dp))
            Text(
                L("وصلت إلى ${Premium.FREE_DOCS} مستندات", "You’ve reached ${Premium.FREE_DOCS} documents"), fontFamily = Fonts.Display,
                fontWeight = FontWeight.Bold, fontSize = 20.sp, color = PwInk, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                L(
                    "الخطة المجانية تتسع لـ ${Premium.FREE_DOCS} مستندات. مستنداتك الحالية بأمان، واشترك لتضيف ما تشاء.",
                    "The free plan keeps up to ${Premium.FREE_DOCS} documents. Your current documents stay safe. Upgrade to add as many as you like."
                ),
                fontSize = 14.sp, lineHeight = 21.sp, color = PwMuted, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            val used = AppController.library.size.coerceAtMost(Premium.FREE_DOCS)
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFF2EDE1))) {
                Box(
                    Modifier.fillMaxWidth(used.toFloat() / Premium.FREE_DOCS).height(10.dp)
                        .clip(RoundedCornerShape(5.dp)).background(PwGoldButton)
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(L("$used من ${Premium.FREE_DOCS}", "$used of ${Premium.FREE_DOCS} used"), fontSize = 12.sp, color = PwMuted, modifier = Modifier.weight(1f))
                Text(L("الخطة المجانية", "Free plan"), fontSize = 12.sp, color = PwMuted)
            }
            Spacer(Modifier.height(20.dp))
            GoldButton(L("مستندات بلا حد", "Get unlimited documents"), onUpgrade)
            QuietButton(L("ليس الآن", "Not now"), close)
        }
    }
}

@Composable
private fun SheetCard(content: @Composable () -> Unit) {
    Column(
        Modifier.widthIn(max = 420.dp).fillMaxWidth(0.9f).clip(RoundedCornerShape(26.dp)).background(Color.White)
            .padding(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) { content() }
}

@Composable
private fun FeatureIcon(icon: Int) {
    Box {
        Box(
            Modifier.size(76.dp).clip(RoundedCornerShape(22.dp)).background(Palette.NavySoft),
            contentAlignment = Alignment.Center
        ) { PhIcon(icon, Palette.Navy, 38.dp) }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(start = 0.dp).size(30.dp).clip(CircleShape).background(Color.White).padding(3.dp)
        ) { PremiumBadge(Modifier.fillMaxSize(), 24.dp) }
    }
}

@Composable
private fun GoldButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(52.dp).shadow(6.dp, RoundedCornerShape(26.dp), spotColor = PwGoldDeep)
            .clip(RoundedCornerShape(26.dp)).background(PwGoldButton).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PwInk) }
}

@Composable
private fun QuietButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(23.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PwMuted) }
}

/** شاشة الاشتراك: المزايا على جانب، والخطة والزر على الجانب الآخر. */
@Composable
fun PaywallDialog(onDismiss: () -> Unit) {
    var termsOpen by remember { mutableStateOf(false) }
    var privacyOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (!Premium.testMode && Cloud.configured) PlayBilling.load() }
    OriginDialog(onDismiss = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) { close ->
        Surface(
            Modifier.fillMaxWidth(0.94f).widthIn(max = 900.dp).fillMaxHeight(0.92f),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFFFFFDF7)
        ) {
            BoxWithConstraints {
                val wide = maxWidth >= 640.dp
                val plans: @Composable (Modifier) -> Unit = { m ->
                    PlanSide(m, close, onTerms = { termsOpen = true }, onPrivacy = { privacyOpen = true })
                }
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        BenefitsSide(Modifier.weight(0.48f).fillMaxHeight())
                        plans(Modifier.weight(0.52f).fillMaxHeight())
                    }
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        BenefitsSide(Modifier.fillMaxWidth())
                        plans(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
    if (termsOpen) TermsDialog { termsOpen = false }
    if (privacyOpen) PrivacyDialog { privacyOpen = false }
}

@Composable
private fun BenefitsSide(modifier: Modifier) {
    Column(
        modifier.background(Brush.linearGradient(listOf(Color(0xFF16294A), Color(0xFF1B365D), Color(0xFF2F4B7C))))
            .padding(horizontal = 30.dp, vertical = 28.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(R.drawable.logo_app), null,
                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFFFFDF7))
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(L("especial notes", "especial notes"), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                Text(
                    "PREMIUM", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PwInk, letterSpacing = 1.sp,
                    modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(PwGold).padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(L("ذاكر بذكاء مع", "Study smarter with"), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Color.White, lineHeight = 32.sp)
        Text(L("especial notes Premium", "especial notes Premium"), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = PwGold, lineHeight = 32.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            L("كل ما تحتاجه للمحاضرات والملاحظات والامتحانات.", "Everything you need for lectures, notes and exams."),
            fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f)
        )
        Spacer(Modifier.height(18.dp))
        listOf(
            PremiumFeature.DOCS, PremiumFeature.SUMMARIES, PremiumFeature.OCR, PremiumFeature.TRANSLATE,
            PremiumFeature.RECORD, PremiumFeature.SPLIT, PremiumFeature.STATS
        ).forEach { f ->
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) { PhIcon(f.icon, PwGold, 16.dp) }
                Spacer(Modifier.width(12.dp))
                Text(f.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun PlanSide(modifier: Modifier, close: () -> Unit, onTerms: () -> Unit, onPrivacy: () -> Unit) {
    Box(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 30.dp, vertical = 28.dp)) {
            if (Premium.active) {
                Text(L("أنت مشترك في Premium", "You’re on Premium"), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = PwInk)
                Spacer(Modifier.height(6.dp))
                Text(L("كل المزايا مفتوحة لك.", "Every feature is unlocked for you."), fontSize = 13.sp, color = PwMuted)
                Spacer(Modifier.height(24.dp))
                GoldButton(L("تم", "Done"), close)
                return@Column
            }
            Text(L("خطة Premium", "Your Premium plan"), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = PwInk)
            Text(
                L("جرّب كل شيء مجانًا ${Premium.TRIAL_DAYS} أيام، وألغِ متى شئت.", "Try everything free for ${Premium.TRIAL_DAYS} days. Cancel anytime."),
                fontSize = 13.sp, color = PwMuted
            )
            Spacer(Modifier.height(20.dp))
            // الخطة الوحيدة: سنوية. السعر من Google Play بعملة المستخدم متى توفر
            val price = PlayBilling.price ?: Premium.YEAR_PRICE
            val shape = RoundedCornerShape(18.dp)
            Box {
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp).clip(shape).background(Color.White)
                        .border(2.dp, PwGold, shape).padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(22.dp).clip(CircleShape).border(7.dp, PwGoldDeep, CircleShape))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(L("سنوي", "Yearly"), fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PwInk)
                        Text(
                            L("${Premium.TRIAL_DAYS} أيام مجانًا، ثم $price في السنة", "${Premium.TRIAL_DAYS}-day free trial, then $price/year"),
                            fontSize = 12.sp, color = PwMuted
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(price, fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = PwInk)
                        Text(L("في السنة", "per year"), fontSize = 11.sp, color = PwMuted)
                    }
                }
                Text(
                    L("${Premium.TRIAL_DAYS} أيام مجانًا", "${Premium.TRIAL_DAYS} DAYS FREE"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.padding(start = 18.dp).clip(RoundedCornerShape(8.dp)).background(Palette.Navy)
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            CompareTable()
            Spacer(Modifier.height(22.dp))
            val activity = LocalContext.current.findActivity()
            val trial = Premium.testMode || PlayBilling.trialAvailable
            GoldButton(
                when {
                    PlayBilling.busy -> L("جارٍ التحقق…", "Verifying…")
                    trial -> L("ابدأ ${Premium.TRIAL_DAYS} أيام مجانًا", "Start ${Premium.TRIAL_DAYS}-day free trial")
                    else -> L("اشترك الآن", "Subscribe now")
                }
            ) {
                if (activity != null && !PlayBilling.busy) Premium.startPurchase(activity) { if (Premium.active) close() }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (Premium.testMode) L("وضع تجريبي: الدفع غير مربوط بعد، ولن يُخصم أي مبلغ.", "Test mode: payment isn’t connected yet, nothing will be charged.")
                else L("يتجدد سنويًا عبر Google Play، وتلغيه متى شئت من حسابك في Play.", "Renews yearly through Google Play. Cancel anytime in your Play account."),
                fontSize = 11.5.sp, color = PwMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                LinkText(L("استعادة الشراء", "Restore purchase")) { Premium.restore(quiet = false) }
                Spacer(Modifier.width(16.dp))
                LinkText(L("الشروط", "Terms"), onTerms)
                Spacer(Modifier.width(16.dp))
                LinkText(L("الخصوصية", "Privacy"), onPrivacy)
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(16.dp).size(36.dp).clip(CircleShape).background(Color(0xFFF1EDE3))
                .clickable(onClick = close),
            contentAlignment = Alignment.Center
        ) { PhIcon(R.drawable.ph_close, PwMuted, 18.dp, L("إغلاق", "Close")) }
    }
}

@Composable
private fun CompareTable() {
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Color.White).border(1.dp, PwLine, shape)) {
        @Composable
        fun row(label: String, free: String, pro: String, head: Boolean = false) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 12.sp, color = if (head) PwMuted else PwInk, fontWeight = if (head) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                Text(free, fontSize = 12.sp, color = if (head) PwMuted else PwInk, fontWeight = if (head) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center, modifier = Modifier.width(70.dp))
                Text(pro, fontSize = 12.sp, color = if (head) PwMuted else PwInk, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(80.dp))
            }
        }
        row("", L("مجاني", "Free"), "Premium", head = true)
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF3EEE2)))
        row(L("المستندات", "Documents"), "${Premium.FREE_DOCS}", L("بلا حد", "Unlimited"))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF3EEE2)))
        row(L("الأقلام والهايلايتر والممحاة", "Pens, highlighter, eraser"), "✓", "✓")
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF3EEE2)))
        row(L("الملخصات وقراءة النص والترجمة", "Summaries, OCR, translation"), "—", "✓")
    }
}

@Composable
private fun LinkText(label: String, onClick: () -> Unit) {
    Text(
        label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Palette.Navy,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(4.dp)
    )
}

/** بطاقة صغيرة تدعو للاشتراك (في إحصاءات الدراسة والمكتبة). */
@Composable
fun PremiumTeaser(title: String, desc: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFFFFF8E4), Color(0xFFFCEBC2))))
            .border(1.dp, Color(0xFFF1DFAE), shape)
            .clickable { Premium.paywallOpen = true }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PremiumBadge(size = 34.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = PwInk)
            Text(desc, fontSize = 12.sp, color = PwMuted)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            L("اشترك", "Upgrade"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PwInk,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(PwGold).padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}
