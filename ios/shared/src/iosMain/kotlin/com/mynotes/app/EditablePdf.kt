package com.mynotes.app

import java.io.File

/**
 * نسخة PDF «قابلة للتعديل» (من نسخة أندرويد): في iPad تُفتح كملف عادي بتعليقات مرسومة،
 * ويُحفظ دائمًا كنسخة نهائية. استعادة التعليقات قابلة للتعديل تحتاج إعادة كتابة صفحات الملف.
 */
object EditablePdf {
    fun restore(
        @Suppress("UNUSED_PARAMETER") file: File,
        @Suppress("UNUSED_PARAMETER") writeData: (String) -> Unit,
        @Suppress("UNUSED_PARAMETER") imgDir: File
    ): Boolean = false
}
