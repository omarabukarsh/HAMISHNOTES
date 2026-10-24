package com.mynotes.app

/**
 * إصلاح أخطاء استخراج النص العربي من ملفات PDF: حرف «لا» المركّب يُستخرج أحيانًا معكوسًا «ال»،
 * فتصير «ولا يُعطى» «وال يُعطى» ويضيع النفي (ويقلب المعنى في الملخص والشرح).
 * نصلح فقط الحالات التي لا لبس فيها:
 *  - كلمة مستقلة «ال» / «وال» / «فال» (أداة التعريف لا تأتي وحدها) ← «لا» / «ولا» / «فلا».
 *  - «األ» / «اإل» / «اآل» (ألف قبل ألف بهمزة لا تقع في العربية) ← «الأ» / «الإ» / «الآ».
 */
object ArabicFix {
    private val swap = listOf("األ" to "الأ", "اإل" to "الإ", "اآل" to "الآ")

    fun word(w: String): String {
        val core = w.trim { it in "()[]«».,،:؛;!؟?\"'" }
        val fixedCore = when (core) {
            "ال" -> "لا"
            "وال" -> "ولا"
            "فال" -> "فلا"
            else -> null
        }
        var out = if (fixedCore != null) w.replace(core, fixedCore) else w
        if (out.any { it in '؀'..'ۿ' }) swap.forEach { (a, b) -> out = out.replace(a, b) }
        return out
    }

    fun text(s: String): String {
        if (s.none { it in '؀'..'ۿ' }) return s
        return s.split(' ').joinToString(" ") { word(it) }
    }
}
