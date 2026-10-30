package java.time

import java.time.format.TextStyle
import java.util.Locale
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone
import platform.Foundation.secondsFromGMT
import platform.Foundation.timeIntervalSince1970

/* بديل صغير لـ java.time.LocalDate (التقويم الميلادي، بالأيام منذ 1970) لنسخة iPad. */

private fun floorDiv(a: Long, b: Long): Long = Math.floorDiv(a, b)

private object Math {
    fun floorDiv(a: Long, b: Long): Long {
        var q = a / b
        if ((a % b != 0L) && ((a xor b) < 0)) q--
        return q
    }
}

private fun isLeap(y: Long) = (y % 4 == 0L && y % 100 != 0L) || y % 400 == 0L

enum class DayOfWeek {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY;

    val value: Int get() = ordinal + 1

    fun getDisplayName(style: TextStyle, locale: Locale): String {
        val ar = locale.language == "ar"
        val full = if (ar) listOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")
        else listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val n = full[ordinal]
        return when (style) {
            TextStyle.FULL, TextStyle.FULL_STANDALONE -> n
            TextStyle.SHORT, TextStyle.SHORT_STANDALONE -> if (ar) n else n.take(3)
            TextStyle.NARROW, TextStyle.NARROW_STANDALONE -> if (ar) n.take(1) else n.take(1)
        }
    }
}

enum class Month {
    JANUARY, FEBRUARY, MARCH, APRIL, MAY, JUNE, JULY, AUGUST, SEPTEMBER, OCTOBER, NOVEMBER, DECEMBER;

    val value: Int get() = ordinal + 1

    fun getDisplayName(style: TextStyle, locale: Locale): String {
        val ar = locale.language == "ar"
        val full = if (ar) listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
        else listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        val n = full[ordinal]
        return when (style) {
            TextStyle.FULL, TextStyle.FULL_STANDALONE -> n
            TextStyle.SHORT, TextStyle.SHORT_STANDALONE -> if (ar) n else n.take(3)
            TextStyle.NARROW, TextStyle.NARROW_STANDALONE -> n.take(1)
        }
    }
}

class LocalDate private constructor(val year: Int, val monthValue: Int, val dayOfMonth: Int) : Comparable<LocalDate> {

    val month: Month get() = Month.entries[monthValue - 1]

    val dayOfWeek: DayOfWeek
        get() = DayOfWeek.entries[(floorDiv(toEpochDay() + 3, 7) * -7 + toEpochDay() + 3).toInt()]

    fun lengthOfMonth(): Int = when (monthValue) {
        2 -> if (isLeap(year.toLong())) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    fun lengthOfYear(): Int = if (isLeap(year.toLong())) 366 else 365

    fun toEpochDay(): Long {
        val y = year.toLong()
        val m = monthValue.toLong()
        var total = 365 * y
        total += if (y >= 0) (y + 3) / 4 - (y + 99) / 100 + (y + 399) / 400 else -(y / -4 - y / -100 + y / -400)
        total += (367 * m - 362) / 12
        total += dayOfMonth - 1
        if (m > 2) {
            total--
            if (!isLeap(y)) total--
        }
        return total - DAYS_0000_TO_1970
    }

    fun plusDays(n: Long): LocalDate = if (n == 0L) this else ofEpochDay(toEpochDay() + n)
    fun minusDays(n: Long): LocalDate = plusDays(-n)
    fun plusWeeks(n: Long): LocalDate = plusDays(n * 7)
    fun minusWeeks(n: Long): LocalDate = plusDays(-n * 7)

    fun plusMonths(n: Long): LocalDate {
        if (n == 0L) return this
        val calc = year * 12L + (monthValue - 1) + n
        val ny = floorDiv(calc, 12).toInt()
        val nm = (calc - ny * 12L).toInt() + 1
        val tmp = LocalDate(ny, nm, 1)
        return LocalDate(ny, nm, minOf(dayOfMonth, tmp.lengthOfMonth()))
    }

    fun minusMonths(n: Long): LocalDate = plusMonths(-n)
    fun plusYears(n: Long): LocalDate = plusMonths(n * 12)

    fun withDayOfMonth(d: Int): LocalDate = LocalDate(year, monthValue, d.coerceIn(1, lengthOfMonth()))

    fun atStartOfDay(@Suppress("UNUSED_PARAMETER") z: ZoneOffset): ZonedDateTime = ZonedDateTime(toEpochDay() * 86_400_000L)

    fun isBefore(o: LocalDate): Boolean = compareTo(o) < 0
    fun isAfter(o: LocalDate): Boolean = compareTo(o) > 0

    override fun compareTo(other: LocalDate): Int = toEpochDay().compareTo(other.toEpochDay())
    override fun equals(other: Any?): Boolean = other is LocalDate && other.toEpochDay() == toEpochDay()
    override fun hashCode(): Int = toEpochDay().hashCode()

    override fun toString(): String =
        year.toString().padStart(4, '0') + "-" + monthValue.toString().padStart(2, '0') + "-" + dayOfMonth.toString().padStart(2, '0')

    companion object {
        private const val DAYS_0000_TO_1970 = (146097L * 5L) - (30L * 365L + 7L)

        fun of(year: Int, month: Int, day: Int): LocalDate = LocalDate(year, month, day)

        fun ofEpochDay(epochDay: Long): LocalDate {
            var zeroDay = epochDay + DAYS_0000_TO_1970
            zeroDay -= 60
            var adjust = 0L
            if (zeroDay < 0) {
                val adjustCycles = (zeroDay + 1) / 146097 - 1
                adjust = adjustCycles * 400
                zeroDay += -adjustCycles * 146097
            }
            var yearEst = (400 * zeroDay + 591) / 146097
            var doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400)
            if (doyEst < 0) {
                yearEst--
                doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400)
            }
            yearEst += adjust
            val marchDoy0 = doyEst.toInt()
            val marchMonth0 = (marchDoy0 * 5 + 2) / 153
            val month = (marchMonth0 + 2) % 12 + 1
            val dom = marchDoy0 - (marchMonth0 * 306 + 5) / 10 + 1
            yearEst += marchMonth0 / 10
            return LocalDate(yearEst.toInt(), month, dom)
        }

        /** اليوم حسب المنطقة الزمنية للجهاز. */
        fun now(): LocalDate {
            val secs = NSDate().timeIntervalSince1970.toLong() + NSTimeZone.localTimeZone.secondsFromGMT
            return ofEpochDay(floorDiv(secs, 86400L))
        }

        fun parse(s: String): LocalDate {
            val p = s.trim().split('-')
            return LocalDate(p[0].toInt(), p[1].toInt(), p[2].toInt())
        }
    }
}

object ZoneOffset {
    val UTC = ZoneOffset
}

class Instant private constructor(val epochMilli: Long) {
    fun atZone(@Suppress("UNUSED_PARAMETER") z: ZoneOffset): ZonedDateTime = ZonedDateTime(epochMilli)
    fun toEpochMilli(): Long = epochMilli

    companion object {
        fun ofEpochMilli(ms: Long): Instant = Instant(ms)
    }
}

class ZonedDateTime internal constructor(private val ms: Long) {
    fun toLocalDate(): LocalDate = LocalDate.ofEpochDay(floorDiv(ms, 86_400_000L))
    fun toInstant(): Instant = Instant.ofEpochMilli(ms)
}
