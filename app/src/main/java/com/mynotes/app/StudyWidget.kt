package com.mynotes.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate

/**
 * أداة الشاشة الرئيسية: وقت دراسة اليوم مقابل الهدف، أقرب امتحان، وزر لمتابعة آخر مستند.
 */
class StudyWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = build(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        private const val PREFS = "mynotes_session"

        fun refresh(context: Context) {
            try {
                val m = AppWidgetManager.getInstance(context)
                val ids = m.getAppWidgetIds(ComponentName(context, StudyWidget::class.java))
                if (ids.isNotEmpty()) {
                    val views = build(context)
                    ids.forEach { m.updateAppWidget(it, views) }
                }
            } catch (t: Throwable) {
            }
        }

        private fun build(context: Context): RemoteViews {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            I18n.english = prefs.getBoolean("lang_en", false)
            val repo = LibraryRepository(context.applicationContext)
            val goal = prefs.getInt("goal_min", 60) * 60_000L
            val today = try {
                repo.loadStats()[LocalDate.now().toString()] ?: 0L
            } catch (t: Throwable) {
                0L
            }
            val v = RemoteViews(context.packageName, R.layout.widget_study)
            v.setTextViewText(R.id.w_today, L("اليوم ${formatReading(today)} من ${formatReading(goal)}", "Today ${formatReading(today)} of ${formatReading(goal)}"))
            v.setProgressBar(R.id.w_progress, 100, ((today * 100) / goal.coerceAtLeast(1L)).toInt().coerceIn(0, 100), false)

            // أقرب امتحان
            var exam = ""
            try {
                val o = org.json.JSONObject(prefs.getString("exam_dates", "{}") ?: "{}")
                val now = LocalDate.now().toEpochDay()
                var best: Pair<String, Long>? = null
                for (k in o.keys()) {
                    val left = o.getLong(k) - now
                    if (left >= 0 && (best == null || left < best.second)) best = k to left
                }
                best?.let { (name, left) ->
                    exam = when (left) {
                        0L -> L("امتحان $name اليوم", "$name exam today")
                        1L -> L("امتحان $name غدًا", "$name exam tomorrow")
                        else -> L("امتحان $name بعد $left يومًا", "$name exam in $left days")
                    }
                }
            } catch (t: Throwable) {
            }
            v.setTextViewText(R.id.w_exam, exam.ifEmpty { L("لا امتحانات قريبة", "No upcoming exams") })

            val last = try {
                repo.loadIndex().filter { !it.locked && it.lastOpened > 0 }.maxByOrNull { it.lastOpened }
            } catch (t: Throwable) {
                null
            }
            v.setTextViewText(R.id.w_open, if (last != null) L("متابعة: ", "Continue: ") + last.name else L("فتح هامش", "Open Hamish"))

            val open = PendingIntent.getActivity(
                context, 9,
                Intent(context, StartActivity::class.java).putExtra("open_last", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val app = PendingIntent.getActivity(
                context, 10, Intent(context, StartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            v.setOnClickPendingIntent(R.id.w_open, open)
            v.setOnClickPendingIntent(R.id.widget_root, app)
            return v
        }
    }
}
