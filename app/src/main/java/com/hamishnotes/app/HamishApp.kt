package com.hamishnotes.app

import android.app.Application
import android.util.Log
import java.io.File

/**
 * يُركّب معالج الأعطال مرة واحدة لكل عملية (لا مع كل فتح لـ StartActivity، فلا تتراكم المعالجات)،
 * ويشمل كل نقاط الدخول: الشاشة، والأداة، والتذكير بعد إعادة التشغيل.
 * StartActivity تعرض السجل المحفوظ في الفتح التالي ثم تحذفه.
 */
class HamishApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val f = File(filesDir, CRASH_FILE)
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                f.writeText(Log.getStackTraceString(e))
            } catch (_: Throwable) {
            }
            old?.uncaughtException(t, e)
        }
    }

    companion object {
        const val CRASH_FILE = "crash.txt"
    }
}
