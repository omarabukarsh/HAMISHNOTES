# MyNotes — قواعد R8 لنسخة الاستخدام

# pdfbox-android: يحمّل الخطوط والموارد ويستخدم الانعكاس داخليًا؛ نبقيه كما هو
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn javax.xml.**
-dontwarn org.w3c.dom.**

# ML Kit يأتي بقواعده؛ تحذيرات فقط
-dontwarn com.google.mlkit.**

# أسماء التعدادات تُحفظ في الإعدادات وتُقرأ بـ valueOf
-keepclassmembers enum com.hamishnotes.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# أرقام الأسطر في تقارير الأعطال، دون أسماء الملفات الأصلية (لا تكشف أين يقع كود الاشتراك)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# صعوبة أكبر في قراءة الكود المفكوك: كل الأصناف في حزمة واحدة بأسماء قصيرة
-repackageclasses ''
-allowaccessmodification

# لا رسائل تتبّع في نسخة الاستخدام
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Credential Manager (تسجيل الدخول بـ Google): حسب توصية توثيق androidx.credentials
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}
