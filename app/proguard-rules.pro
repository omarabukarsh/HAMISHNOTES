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
-keepclassmembers enum com.mynotes.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# أرقام الأسطر في تقارير الأعطال
-keepattributes SourceFile,LineNumberTable

# Credential Manager (تسجيل الدخول بـ Google): حسب توصية توثيق androidx.credentials
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}
