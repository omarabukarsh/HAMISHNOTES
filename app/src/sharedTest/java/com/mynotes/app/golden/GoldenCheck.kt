package com.mynotes.app.golden

import com.mynotes.app.SBlock
import com.mynotes.app.SHeading
import com.mynotes.app.SPara
import com.mynotes.app.STable

/**
 * يقارن ناتج المسار بالمرجع بقواعد ثابتة (لا تقييم لغوي):
 *  1. نص كل تمييز يطابق المرجع حرفيًا
 *  2. أجزاء الملخص (نوعها ونصها وخلايا الجداول) تطابق المرجع حرفيًا
 *  3. التغطية: كل كلمة في المرجع موجودة في الملخص (لا فقدان)
 *  4. لا تكرار: لا تظهر كلمة أكثر من عدد مراتها في المرجع
 *  5. الترتيب: كلمات الملخص بنفس ترتيب كلمات المرجع
 *  6. لا كلمات غريبة من خارج التظليل
 *  7. المقاطع الحرجة (أرقام وجرعات ومصطلحات) موجودة حرفيًا
 * المقاييس 3–7 تشرح نوع الخطأ عندما يفشل التطابق الحرفي.
 */
class GoldenCheck(val case: GoldenCase, val result: PipelineResult) {
    val failures = ArrayList<String>()
    val actualSummary: List<String> = Pipeline.render(result.tidy)

    init {
        if (result.markTexts != case.markTexts) failures.add("نص التمييزات لا يطابق المرجع")
        if (actualSummary != case.summary) failures.add("الملخص لا يطابق المرجع")

        val expected = tokens(case.summary.joinToString(" ") { it.substring(3) })
        val actual = tokens(result.tidy.joinToString(" ") { flat(it) })
        val exp = expected.groupingBy { it }.eachCount()
        val act = actual.groupingBy { it }.eachCount()
        val missing = exp.filter { (t, n) -> (act[t] ?: 0) < n }.keys
        val repeated = act.filter { (t, n) -> exp[t] != null && n > exp[t]!! }.keys
        val foreign = act.keys.filter { it !in exp }
        if (missing.isNotEmpty()) failures.add("كلمات مفقودة: " + missing.joinToString(" "))
        if (repeated.isNotEmpty()) failures.add("كلمات مكررة: " + repeated.joinToString(" "))
        if (foreign.isNotEmpty()) failures.add("كلمات غريبة أو مشوّهة: " + foreign.joinToString(" "))
        if (missing.isEmpty() && repeated.isEmpty() && foreign.isEmpty() && actual != expected) {
            failures.add("ترتيب الكلمات مختلف عن المرجع")
        }
        val text = actualSummary.joinToString("\n")
        for (c in case.critical) if (!text.contains(c)) failures.add("مقطع حرج غير موجود حرفيًا: «$c»")
    }

    val passed: Boolean get() = failures.isEmpty()

    fun report(): String = buildString {
        append(if (passed) "PASS " else "FAIL ").append(case.id).append(" — ").append(case.about).append('\n')
        for (f in failures) append("  ✗ ").append(f).append('\n')
        if (!passed) {
            append("  التمييزات المتوقعة:\n")
            case.markTexts.forEach { append(indent(it)) }
            append("  التمييزات الناتجة:\n")
            result.markTexts.forEach { append(indent(it)) }
            append("  الملخص المتوقع:\n")
            case.summary.forEach { append(indent(it)) }
            append("  الملخص الناتج:\n")
            actualSummary.forEach { append(indent(it)) }
        }
    }

    companion object {
        /** كلمات النص دون علامات الترقيم على أطرافها؛ «|» و«—» وحدها ليست كلمات. */
        fun tokens(s: String): List<String> =
            s.split(Regex("\\s+"))
                .map { it.trim { c -> !c.isLetterOrDigit() && c != '%' && c != '½' && c != '®' } }
                .filter { t -> t.any { it.isLetterOrDigit() } }

        fun flat(b: SBlock): String = when (b) {
            is SHeading -> b.text
            is SPara -> b.text
            is STable -> b.rows.joinToString(" ") { it.joinToString(" ") }
        }

        private fun indent(s: String) = s.lines().joinToString("") { "      │ $it\n" }

        fun run(words: Map<Int, List<com.mynotes.app.OcrWord>>, cases: List<GoldenCase> = AntibioticsCases.all): List<GoldenCheck> =
            cases.map { GoldenCheck(it, Pipeline.run(words, it.strokes)) }

        fun summaryReport(checks: List<GoldenCheck>, source: String): String = buildString {
            val ok = checks.count { it.passed }
            append("Golden cases (antibiotics.pdf) — source: ").append(source).append('\n')
            append("passed ").append(ok).append(" / ").append(checks.size).append("\n\n")
            checks.forEach { append(it.report()).append('\n') }
        }
    }
}
