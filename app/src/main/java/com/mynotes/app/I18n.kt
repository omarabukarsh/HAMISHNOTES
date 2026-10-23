package com.mynotes.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** لغة الواجهة: العربية (الافتراضية) أو الإنجليزية. تُختار من الإعدادات. */
object I18n {
    var english by mutableStateOf(false)
}

/** نص الواجهة باللغة المختارة. */
fun L(ar: String, en: String): String = if (I18n.english) en else ar
