package java.util

import platform.Foundation.NSDate
import platform.Foundation.NSLocale
import platform.Foundation.NSUUID
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.dateWithTimeIntervalSince1970

/* بدائل صغيرة لأصناف java.util المستخدمة في التطبيق (نسخة iPad). */

class UUID private constructor(private val s: String) {
    override fun toString(): String = s
    override fun equals(other: Any?): Boolean = other is UUID && other.s == s
    override fun hashCode(): Int = s.hashCode()

    companion object {
        fun randomUUID(): UUID = UUID(NSUUID().UUIDString.lowercase())
        fun fromString(s: String): UUID = UUID(s.lowercase())
    }
}

class Locale(val language: String, val country: String = "") {
    override fun toString(): String = if (country.isEmpty()) language else "${language}_$country"
    override fun equals(other: Any?): Boolean = other is Locale && other.language == language && other.country == country
    override fun hashCode(): Int = toString().hashCode()

    companion object {
        val US = Locale("en", "US")
        val ENGLISH = Locale("en")
        val ROOT = Locale("")
        fun getDefault(): Locale = Locale(NSLocale.currentLocale.languageCode)
    }
}

class Date(val time: Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()) {
    fun getTime(): Long = time
}

private fun NSDate.ms(): Long = (timeIntervalSince1970 * 1000.0).toLong()

class Calendar private constructor() {
    var timeInMillis: Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

    fun set(field: Int, value: Int) {
        val cal = platform.Foundation.NSCalendar.currentCalendar
        val date = platform.Foundation.NSDate.dateWithTimeIntervalSince1970(timeInMillis / 1000.0)
        when (field) {
            HOUR_OF_DAY, MINUTE, SECOND -> {
                val comps = cal.components(
                    platform.Foundation.NSCalendarUnitYear or platform.Foundation.NSCalendarUnitMonth or
                        platform.Foundation.NSCalendarUnitDay or platform.Foundation.NSCalendarUnitHour or
                        platform.Foundation.NSCalendarUnitMinute or platform.Foundation.NSCalendarUnitSecond,
                    fromDate = date
                )
                when (field) {
                    HOUR_OF_DAY -> comps.hour = value.toLong()
                    MINUTE -> comps.minute = value.toLong()
                    SECOND -> comps.second = value.toLong()
                }
                val ms = timeInMillis % 1000L
                cal.dateFromComponents(comps)?.let { timeInMillis = it.ms() + ms }
            }
            MILLISECOND -> timeInMillis = timeInMillis - (timeInMillis % 1000L) + value
        }
    }

    companion object {
        const val HOUR_OF_DAY = 11
        const val MINUTE = 12
        const val SECOND = 13
        const val MILLISECOND = 14
        fun getInstance(): Calendar = Calendar()
    }
}
