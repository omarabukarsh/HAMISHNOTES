package java.text

import java.util.Date
import java.util.Locale
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSString
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.precomposedStringWithCanonicalMapping
import platform.Foundation.precomposedStringWithCompatibilityMapping
import platform.Foundation.decomposedStringWithCanonicalMapping
import platform.Foundation.decomposedStringWithCompatibilityMapping

/* بدائل SimpleDateFormat و Normalizer فوق Foundation (نسخة iPad). */

class SimpleDateFormat(pattern: String, locale: Locale = Locale.getDefault()) {
    private val f = NSDateFormatter().apply {
        dateFormat = pattern
        if (locale.language.isNotEmpty()) this.locale = NSLocale(localeIdentifier = locale.toString())
    }

    fun format(d: Date): String = f.stringFromDate(NSDate.dateWithTimeIntervalSince1970(d.time / 1000.0))
}

object Normalizer {
    enum class Form { NFC, NFD, NFKC, NFKD }

    @Suppress("CAST_NEVER_SUCCEEDS")
    fun normalize(s: CharSequence, form: Form): String {
        val ns = s.toString() as NSString
        return when (form) {
            Form.NFC -> ns.precomposedStringWithCanonicalMapping
            Form.NFD -> ns.decomposedStringWithCanonicalMapping
            Form.NFKC -> ns.precomposedStringWithCompatibilityMapping
            Form.NFKD -> ns.decomposedStringWithCompatibilityMapping
        }
    }
}
