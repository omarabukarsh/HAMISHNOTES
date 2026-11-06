import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun setting(name: String): String = ((project.findProperty(name) as String?) ?: System.getenv(name) ?: "").trim()

// مفتاح المتجر السري: لا يوجد في المستودع أبدًا. يُقرأ من متغيرات البيئة (أو gradle.properties في جهازك):
//   HAMISH_KEYSTORE (مسار الملف) أو HAMISH_KEYSTORE_B64 (الملف نفسه بترميز base64)،
//   HAMISH_KEYSTORE_PASSWORD و HAMISH_KEY_ALIAS و HAMISH_KEY_PASSWORD.
// بدونه تُبنى نسخة تجربة بمفتاح التجربة (keystore/mynotes.jks) وفيها وضع Premium التجريبي.
val storeKeyFile: File? = when {
    setting("HAMISH_KEYSTORE").isNotEmpty() -> file(setting("HAMISH_KEYSTORE"))
    setting("HAMISH_KEYSTORE_B64").isNotEmpty() -> layout.buildDirectory.file("secret/upload.jks").get().asFile.also {
        it.parentFile.mkdirs()
        it.writeBytes(Base64.getMimeDecoder().decode(setting("HAMISH_KEYSTORE_B64")))
    }
    else -> null
}
val storeBuild = storeKeyFile != null

/** بصمة SHA-256 لشهادة المفتاح، ليتحقق التطبيق وقت التشغيل أنه لم يُعَد توقيعه. */
fun certSha256(file: File, password: String, alias: String): String {
    val ks = KeyStore.getInstance(file, password.toCharArray())
    return MessageDigest.getInstance("SHA-256").digest(ks.getCertificate(alias).encoded).joinToString("") { "%02x".format(it) }
}

android {
    namespace = "com.hamishnotes.app"
    testOptions { unitTests { isIncludeAndroidResources = true } }
    compileSdk = 36
    defaultConfig {
        // اسم التطبيق في المتجر (لا يتغير بعد أول رفع)، وهو نفسه لنسخ التجربة
        applicationId = "com.hamishnotes.app"
        minSdk = 26
        // شرط Google Play للتطبيقات الجديدة والتحديثات منذ أغسطس 2026
        targetSdk = 36
        // رقم يزيد مع كل بناء على GitHub Actions، فيُثبَّت كل APK جديد كتحديث فوق القديم
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = 100 + run
        versionName = "1.0.$run"
        // معرّف عميل Google (من نوع Web) لتسجيل الدخول: من gradle.properties أو متغير بيئة GOOGLE_WEB_CLIENT_ID
        val googleClientId = (project.findProperty("GOOGLE_WEB_CLIENT_ID") as String?) ?: System.getenv("GOOGLE_WEB_CLIENT_ID") ?: ""
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleClientId\"")
        // Supabase: الرابط والمفتاح العام (anon) فقط، وكلاهما ليس سرًا؛ لا مفاتيح سرية في التطبيق.
        // قيم افتراضية هنا كي يعمل البناء المحلي و GitHub Actions دون متغيرات إضافية.
        val supabaseUrl = setting("SUPABASE_URL").ifEmpty { "https://nuwbcgpsyrdqaqzablis.supabase.co" }
        val supabaseAnon = setting("SUPABASE_ANON_KEY").ifEmpty {
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im51d2JjZ3BzeXJkcWFxemFibGlzIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTMzNjQ2ODgsImV4cCI6MjEwODk0MDY4OH0.7umRZmCBShbped48zBM_r_5RX6qBsWg165VHcnKWeO4"
        }
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnon\"")
        // رقم مشروع Google Cloud لـ Play Integrity (مشروع hamish)
        buildConfigField("long", "CLOUD_PROJECT_NUMBER", setting("CLOUD_PROJECT_NUMBER").ifEmpty { "769777388576" } + "L")
        buildConfigField("String", "PREMIUM_PRODUCT", "\"${setting("PREMIUM_PRODUCT").ifEmpty { "hamish_premium" }}\"")
        // وضع Premium التجريبي موجود فقط في نسخ التجربة؛ يُحذف من نسخة المتجر عند البناء
        buildConfigField("boolean", "TEST_PREMIUM", (!storeBuild).toString())
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // مفتاح التجربة (keystore/mynotes.jks): للنسخ التي تُثبَّت يدويًا فقط، لا يُرفع به إلى المتجر
    signingConfigs {
        create("stable") {
            storeFile = rootProject.file("keystore/mynotes.jks")
            storePassword = "mynotes-update-key"
            keyAlias = "mynotes"
            keyPassword = "mynotes-update-key"
        }
        if (storeKeyFile != null) create("store") {
            storeFile = storeKeyFile
            storePassword = setting("HAMISH_KEYSTORE_PASSWORD")
            keyAlias = setting("HAMISH_KEY_ALIAS").ifEmpty { "upload" }
            keyPassword = setting("HAMISH_KEY_PASSWORD").ifEmpty { setting("HAMISH_KEYSTORE_PASSWORD") }
        }
    }
    val signer = signingConfigs.getByName(if (storeBuild) "store" else "stable")
    // الشهادات المسموح بها: مفتاح هذا البناء، ومفتاح Google Play (من Play Console ← App signing).
    // نسخة المتجر يوقّعها Google بمفتاحه، فبدون بصمته يتوقف الفحص (وإلا رُفضت النسخة الأصلية نفسها).
    val playSigners = setting("PLAY_SIGNING_SHA256").split(',').map { it.replace(":", "").trim().lowercase() }.filter { it.isNotEmpty() }
    // نسخة المتجر يوقّعها Google بمفتاحه، فبدون بصمته (من Play Console ← App signing) يتعطّل فحص التوقيع
    // وقت التشغيل كليًا. لا نسمح بذلك صامتًا في نسخة متجر: نفشل البناء إلا بتصريح صريح للتجربة.
    val allowUnverified = setting("ALLOW_UNVERIFIED_SIGNER").equals("true", ignoreCase = true)
    if (storeBuild && playSigners.isEmpty() && !allowUnverified) {
        throw GradleException(
            "PLAY_SIGNING_SHA256 غير مضبوط لنسخة المتجر: فحص التوقيع وقت التشغيل سيتعطّل. " +
                "ضع بصمة مفتاح Google من Play Console ← App signing، أو مرّر ALLOW_UNVERIFIED_SIGNER=true للتجربة فقط."
        )
    }
    if (storeBuild && playSigners.isEmpty()) logger.warn("ALLOW_UNVERIFIED_SIGNER: فحص التوقيع وقت التشغيل معطّل في هذا البناء (للتجربة فقط)")
    val signers = if (storeBuild && playSigners.isEmpty()) ""
    else (listOf(certSha256(signer.storeFile!!, signer.storePassword!!, signer.keyAlias!!)) + playSigners).joinToString(",")
    defaultConfig.buildConfigField("String", "SIGNERS", "\"$signers\"")
    buildTypes {
        getByName("debug") {
            signingConfig = signer
        }
        // نسخة الاستخدام: محسّنة ومضغوطة (R8) وغير قابلة للتصحيح، فتعمل Compose بأقصى سرعة.
        // نسخ التجربة بنفس مفتاح التجربة، فتُثبَّت كتحديث فوق النسخة الحالية.
        getByName("release") {
            signingConfig = signer
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    lint { checkReleaseBuilds = false }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // حالات الاختبار المرجعية (ملف PDF المرفق) مشتركة بين اختبارات JVM واختبارات الجهاز
    sourceSets {
        getByName("test").java.srcDir("src/sharedTest/java")
        getByName("test").resources.srcDir("src/sharedTest/assets")
        getByName("androidTest").java.srcDir("src/sharedTest/java")
        getByName("androidTest").assets.srcDir("src/sharedTest/assets")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    // يثبّت ملفات الأداء الجاهزة (Baseline Profiles) لمكتبات Compose: فتح أسرع وتمرير أنعم من أول تشغيل
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // ترجمة مصطلح بالضغط المطول (حزمة لغة تُنزَّل مرة واحدة ثم تعمل دون إنترنت)
    implementation("com.google.mlkit:translate:17.0.3")
    // تحويل الخط اليدوي إلى نص (حزمة لغة تُنزَّل مرة واحدة)
    // 19.0.0: مكتبتها الأصلية تدعم صفحات الذاكرة 16 KB (شرط Google Play)
    implementation("com.google.mlkit:digital-ink-recognition:19.0.0")
    implementation("androidx.core:core-ktx:1.13.1")
    // تسجيل الدخول بحساب Google (Credential Manager)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    // الاشتراك عبر Google Play، والتحقق من أن النسخة أصلية من المتجر
    implementation("com.android.billingclient:billing:8.3.0")
    implementation("com.google.android.play:integrity:1.6.0")
    // فتح المستندات المقفلة بالبصمة
    implementation("androidx.biometric:biometric:1.1.0")
    // قراءة النص الرقمي داخل PDF (يدعم العربية) والكتابة على PDF عند الحفظ
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    testImplementation("junit:junit:4.13.2")
    // اختبارات الواجهة على الحاسوب (سحب العناصر من النافذة إلى الصفحة) بلا جهاز
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
