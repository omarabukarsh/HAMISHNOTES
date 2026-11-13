package com.hamishnotes.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

class StartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val f = File(filesDir, HamishApp.CRASH_FILE)
        if (f.exists()) {
            val tv = TextView(this)
            tv.text = f.readText()
            tv.textSize = 12f
            tv.setPadding(24, 80, 24, 24)
            val sv = ScrollView(this)
            // edge-to-edge: النص لا يختفي تحت شريطي النظام
            sv.fitsSystemWindows = true
            sv.addView(tv)
            setContentView(sv)
            f.delete()
            return
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
        if (uri != null && isPrivateFile(uri)) {
            // رابط file:// يشير إلى ملفات التطبيق الخاصة (الجلسة، القسيمة، الإعدادات): تطبيق آخر لا يفتحها عبرنا
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        if (uri != null) {
            next.action = Intent.ACTION_VIEW
            val type = src?.type
            if (type != null) next.setDataAndType(uri, type) else next.data = uri
            next.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (src?.getBooleanExtra("open_last", false) == true) next.putExtra("open_last", true)
        next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(next)
        finish()
    }

    /** هل الرابط ملف داخل مجلد بيانات التطبيق نفسه؟ (بعد حل الروابط الرمزية و «..») */
    private fun isPrivateFile(uri: android.net.Uri): Boolean {
        if (uri.scheme != "file") return false
        val path = uri.path ?: return true
        return try {
            val target = File(path).canonicalPath
            listOfNotNull(applicationInfo.dataDir, filesDir.parent, noBackupFilesDir.parent).any { dir ->
                val d = File(dir).canonicalPath
                target == d || target.startsWith(d + File.separator)
            }
        } catch (e: Throwable) {
            true
        }
    }
}
