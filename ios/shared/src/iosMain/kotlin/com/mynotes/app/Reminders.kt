package com.mynotes.app

import android.content.Context
import java.time.LocalDate
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * التذكير اليومي: إشعار في الساعة التي يختارها المستخدم، فيه ما تبقى من هدف اليوم وأقرب امتحان.
 * في iPad: إشعار محلي متكرر كل يوم (UNUserNotificationCenter)، ويُحدَّث نصه كلما فُتح التطبيق.
 */
object Reminders {
    private const val PREFS = "mynotes_session"
    private const val ID = "study_reminder"

    fun schedule(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removePendingNotificationRequestsWithIdentifiers(listOf(ID))
        if (!prefs.getBoolean("reminder_on", false)) return
        val minute = prefs.getInt("reminder_min", 20 * 60)
        val (title, text) = message(context)
        val content = UNMutableNotificationContent()
        content.setTitle(title)
        content.setBody(text)
        content.setSound(UNNotificationSound.defaultSound)
        val at = NSDateComponents()
        at.hour = (minute / 60).toLong()
        at.minute = (minute % 60).toLong()
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(at, repeats = true)
        center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(ID, content, trigger), null)
    }

    /** نص الإشعار حسب هدف اليوم وأقرب امتحان. */
    fun message(context: Context): Pair<String, String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        I18n.english = prefs.getBoolean("lang_en", false)
        val goalMs = prefs.getInt("goal_min", 60) * 60_000L
        val today = try {
            LibraryRepository(context.applicationContext).loadStats()[LocalDate.now().toString()] ?: 0L
        } catch (t: Throwable) {
            0L
        }
        val parts = ArrayList<String>()
        parts.add(
            if (today >= goalMs) L("حققت هدف اليوم، أحسنت", "You reached today’s goal, well done")
            else L("باقي ${formatReading(goalMs - today)} لهدف اليوم", "${formatReading(goalMs - today)} left for today’s goal")
        )
        try {
            val o = org.json.JSONObject(prefs.getString("exam_dates", "{}") ?: "{}")
            val todayDay = LocalDate.now().toEpochDay()
            var best: Pair<String, Long>? = null
            for (k in o.keys()) {
                val left = o.getLong(k) - todayDay
                if (left >= 0 && (best == null || left < best.second)) best = k to left
            }
            best?.let { (name, left) ->
                parts.add(
                    when (left) {
                        0L -> L("امتحان $name اليوم", "$name exam is today")
                        1L -> L("امتحان $name غدًا", "$name exam is tomorrow")
                        else -> L("امتحان $name بعد $left أيام", "$name exam in $left days")
                    }
                )
            }
        } catch (t: Throwable) {
        }
        return L("وقت المذاكرة", "Study time") to parts.joinToString(L("، ", " · "))
    }
}
