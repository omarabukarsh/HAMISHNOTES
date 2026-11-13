package com.hamishnotes.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * خدمة أمامية ترافق تسجيل المحاضرة (AudioNotes): بدونها يقطع أندرويد الميكروفون عن التطبيق
 * حين تُطفأ الشاشة أو يُبدَّل التطبيق، وقد يُغلقه فيضيع التسجيل. إشعارها يبيّن أن التسجيل يعمل
 * وفيه زر لإيقافه من أي مكان. التسجيل نفسه في AudioNotes؛ هذه الخدمة تُبقي العملية حية فقط.
 */
class RecordingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            AudioNotes.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification(),
                if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            )
        } catch (t: Throwable) {
            // تعذّر الصعود إلى المقدمة (قيود النظام): التسجيل يستمر ما دام التطبيق ظاهرًا
            stopSelf()
            return START_NOT_STICKY
        }
        // أُعيد تشغيل الخدمة بلا تسجيل جارٍ (عملية جديدة): لا شيء تحرسه
        if (AudioNotes.recordingDoc == null) stopSelf()
        return START_NOT_STICKY
    }

    /** سُحب التطبيق من قائمة التطبيقات الأخيرة: يُنهى التسجيل بسلام فيُحفظ ملفه كاملًا. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        AudioNotes.stop()
        stopSelf()
    }

    private fun notification(): android.app.Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, L("تسجيل المحاضرة", "Lecture recording"), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 12, Intent(this, StartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 13, Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ph_mic)
            .setContentTitle(L("جارٍ تسجيل المحاضرة", "Recording the lecture"))
            .setContentText(L("اضغط للعودة إلى especial notes", "Tap to return to especial notes"))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, L("إيقاف التسجيل", "Stop recording"), stop)
            .build()
    }

    companion object {
        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 72
        private const val ACTION_STOP = "com.hamishnotes.app.STOP_RECORDING"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java))
            } catch (t: Throwable) {
                // لا خدمة: التسجيل يعمل ما دام التطبيق ظاهرًا، كما كان
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, RecordingService::class.java))
            } catch (t: Throwable) {
            }
        }
    }
}
