package com.example.waterreminder.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.waterreminder.MainActivity
import com.example.waterreminder.R

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        // 固定 ID：新提醒覆盖上一条，避免通知栏越积越多
        private const val REMINDER_NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "water_reminder_channel"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val helper = AlarmManagerHelper(context)
        val intervalHours = intent.getIntExtra("interval_hours", 1)
        // 无论是否免打扰都先排下一次，保证提醒链不断
        helper.setRepeatingAlarm(intervalHours)
        // 夜间免打扰时段内静默跳过本次通知
        if (!helper.isQuietHoursNow()) {
            showNotification(context)
        }
    }

    private fun showNotification(context: Context) {
        val channelId = CHANNEL_ID
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "喝水提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "定时提醒你喝水"
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_water_drop)
            .setContentTitle("该喝水啦！💧")
            .setContentText("保持水分充足，让身体更健康~")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        notificationManager.notify(REMINDER_NOTIFICATION_ID, notification)
    }
}