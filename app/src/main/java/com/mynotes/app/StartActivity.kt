package com.mynotes.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

class StartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val f = File(filesDir, "crash.txt")
        if (f.exists()) {
            val tv = TextView(this)
            tv.text = f.readText()
            tv.textSize = 12f
            tv.setPadding(24, 80, 24, 24)
            val sv = ScrollView(this)
            sv.addView(tv)
            setContentView(sv)
            f.delete()
            return
        }
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { f.writeText(Log.getStackTraceString(e)) } catch (_: Throwable) {}
            old?.uncaughtException(t, e)
        }
        // ملف مفتوح من تطبيق آخر (عرض أو مشاركة): نمرره مع صلاحية القراءة إلى الشاشة الرئيسية
        val next = Intent(this, MainActivity::class.java)
        val src = intent
        val uri = when (src?.action) {
            Intent.ACTION_VIEW -> src.data
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                src.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
            }
            else -> null
        }
        if (uri != null) {
            next.action = Intent.ACTION_VIEW
            next.setDataAndType(uri, src?.type ?: "application/pdf")
            next.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(next)
        finish()
    }
}
