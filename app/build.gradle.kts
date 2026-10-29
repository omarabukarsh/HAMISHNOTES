plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mynotes.app"
    compileSdk = 34
    defaultConfig {
        // تطبيق مستقل يُثبَّت بجانب MyNotes ولا يحلّ محله (بيانات منفصلة)
        applicationId = "com.mynotes.app.pro"
        minSdk = 26
        targetSdk = 34
        // رقم يزيد مع كل بناء على GitHub Actions، فيُثبَّت كل APK جديد كتحديث فوق القديم
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = 100 + run
        versionName = "pro-1.0.$run"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // مفتاح توقيع ثابت (keystore/mynotes.jks) بدل مفتاح debug العشوائي الذي يتغير في كل بناء
    signingConfigs {
        create("stable") {
            storeFile = rootProject.file("keystore/mynotes.jks")
            storePassword = "mynotes-update-key"
            keyAlias = "mynotes"
            keyPassword = "mynotes-update-key"
        }
    }
    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("stable")
        }
        // نسخة الاستخدام: محسّنة ومضغوطة (R8) وغير قابلة للتصحيح، فتعمل Compose بأقصى سرعة.
        // نفس مفتاح التوقيع، فتُثبَّت كتحديث فوق النسخة الحالية.
        getByName("release") {
            signingConfig = signingConfigs.getByName("stable")
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
    buildFeatures { compose = true }
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
    implementation("com.google.mlkit:digital-ink-recognition:18.1.0")
    implementation("androidx.core:core-ktx:1.13.1")
    // MyNotes Pro: نموذج لغوي يعمل داخل الجهاز دون إنترنت (LiteRT-LM من Google، مفتوح المصدر)
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")
    // فتح المستندات المقفلة بالبصمة
    implementation("androidx.biometric:biometric:1.1.0")
    // قراءة النص الرقمي داخل PDF (يدعم العربية) والكتابة على PDF عند الحفظ
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
