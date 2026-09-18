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

        /**
         * 渠道 ID 带 `_v2`：v1 创建时没开震动，而 `createNotificationChannel()` 只更新名称与描述，
         * **不会改动已有渠道的震动 / 重要级别**（2026-09-18 真机实测 `mVibrationEnabled=false`）。
         * 所以要让震动生效只能换 ID 重建，否则已经装过的用户永远拿不到震动。
         */
        private const val CHANNEL_ID = "water_reminder_channel_v2"
        private const val LEGACY_CHANNEL_ID = "water_reminder_channel"

        /** 震动节奏：立即、震 500ms、停 200ms、再震 500ms */
        private val VIBRATION_PATTERN = longArrayOf(0, 500, 200, 500)
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
            // 换 ID 的迁移：旧渠道留着会让通知设置里出现两个同名的「喝水提醒」
            if (notificationManager.getNotificationChannel(LEGACY_CHANNEL_ID) != null) {
                notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
            }

            val channel = NotificationChannel(
                channelId,
                "喝水提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "定时提醒你喝水"
                // 震动必须设在渠道上：Android 8+ 会忽略 NotificationCompat.setVibrate()，
                // 而新建渠道默认 mVibrationEnabled=false，不写这两行就永远不会震
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN
                // 这里刻意不调 setBypassDnd(true)：2026-09-18 实测（Android 17、且
                // ACCESS_NOTIFICATION_POLICY 已 granted）渠道的 mBypassDnd 仍是 false，
                // 拿不到任何落地效果；而且夜间本就不该绕开用户自己开的系统免打扰 ——
                // 这个需求已由本应用内置的「夜间免打扰时段」覆盖
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
            // 不调 setVibrate()：Android 8+ 的震动由渠道决定，在这里设了会被静默忽略（见上面渠道配置）
            .build()

        notificationManager.notify(REMINDER_NOTIFICATION_ID, notification)
    }
}