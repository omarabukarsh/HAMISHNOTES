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
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
