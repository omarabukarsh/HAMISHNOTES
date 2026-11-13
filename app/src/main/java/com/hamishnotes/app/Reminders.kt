package com.hamishnotes.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * التذكير اليومي: إشعار في الساعة التي يختارها المستخدم، فيه ما تبقى من هدف اليوم وأقرب امتحان.
 * يُجدول بـ AlarmManager ويعاد جدولته بعد كل تذكير وبعد إعادة تشغيل الجهاز.
 */
object Reminders {
    private const val CHANNEL = "study_reminder"
    private const val PREFS = "mynotes_session"

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 7, Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun schedule(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(context)
        am.cancel(pi)
        if (!prefs.getBoolean("reminder_on", false)) return
        val minute = prefs.getInt("reminder_min", 20 * 60)
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(minute / 60, minute % 60)
        if (!next.isAfter(now.plusSeconds(30))) next = next.plusDays(1)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (t: Throwable) {
            am.set(AlarmManager.RTC_WAKEUP, at, pi)
        }
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

    fun show(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, L("تذكير المذاكرة", "Study reminder"), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val (title, text) = message(context)
        val open = PendingIntent.getActivity(
            context, 8, Intent(context, StartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ph_note)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(71, n)
        } catch (e: SecurityException) {
            // صلاحية الإشعارات مرفوضة
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Reminders.show(context)
        Reminders.schedule(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED || intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent?.action == Intent.ACTION_TIME_CHANGED || intent?.action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            Reminders.schedule(context)
        }
    }
}
