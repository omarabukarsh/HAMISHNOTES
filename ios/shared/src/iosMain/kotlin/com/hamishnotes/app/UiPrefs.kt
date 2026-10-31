package com.hamishnotes.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** حجم شريط الأدوات، حجم الكتابة في التطبيق، وتفعيل الملخصات. */
object UiPrefs {
    private var sp: SharedPreferences? = null

    /** مقياس أشرطة الأدوات (0.8 .. 1.3). */
    var toolbarScale by mutableStateOf(1f); private set
    /** مقياس حجم الخط في التطبيق (0.85 .. 1.3). */
    var textScale by mutableStateOf(1f); private set
    /** ميزة الملخصات (زر الدرج، وإضافة التمييز إلى الملخص). */
    var summariesOn by mutableStateOf(true); private set

    fun init(ctx: Context) {
        val p = ctx.getSharedPreferences("mynotes_ui", Context.MODE_PRIVATE)
        sp = p
        toolbarScale = p.getFloat("toolbar_scale", 1f)
        textScale = p.getFloat("text_scale", 1f)
        summariesOn = p.getBoolean("summaries_on", true)
    }

    fun changeToolbarScale(v: Float) { toolbarScale = v.coerceIn(0.8f, 1.3f); sp?.edit()?.putFloat("toolbar_scale", toolbarScale)?.apply() }
    fun changeTextScale(v: Float) { textScale = v.coerceIn(0.85f, 1.3f); sp?.edit()?.putFloat("text_scale", textScale)?.apply() }
    fun changeSummariesOn(v: Boolean) { summariesOn = v; sp?.edit()?.putBoolean("summaries_on", v)?.apply() }
}
