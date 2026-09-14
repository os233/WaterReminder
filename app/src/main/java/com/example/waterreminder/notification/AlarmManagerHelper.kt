package com.example.waterreminder.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.getSystemService
import java.util.Calendar

class AlarmManagerHelper(private val context: Context) {
    private val alarmManager = context.getSystemService<AlarmManager>()!!

    companion object {
        const val PREFS_NAME = "water_reminder_prefs"
        const val KEY_INTERVAL = "reminder_interval_hours"
        const val KEY_ENABLED = "reminder_enabled"
        const val KEY_DND_ENABLED = "dnd_enabled"
        const val KEY_DND_START_HOUR = "dnd_start_hour"
        const val KEY_DND_END_HOUR = "dnd_end_hour"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true
    }

    fun openAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
    }

    fun setRepeatingAlarm(intervalHours: Int) {
        if (intervalHours <= 0) {
            cancelAlarm()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Toast.makeText(context, "请先允许设置精确闹钟", Toast.LENGTH_LONG).show()
            openAlarmSettings()
            return
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("interval_hours", intervalHours)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 1001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, intervalHours)
        }.timeInMillis

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }

        prefs.edit().apply {
            putInt(KEY_INTERVAL, intervalHours)
            putBoolean(KEY_ENABLED, true)
        }.apply()
    }

    fun cancelAlarm() {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 1001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()

        prefs.edit().apply {
            putBoolean(KEY_ENABLED, false)
        }.apply()
    }

    fun restoreAlarmIfNeeded() {
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        val interval = prefs.getInt(KEY_INTERVAL, 1)
        if (enabled) {
            setRepeatingAlarm(interval)
        }
    }

    fun getSavedInterval(): Int {
        return prefs.getInt(KEY_INTERVAL, 0)
    }

    fun isDndEnabled(): Boolean = prefs.getBoolean(KEY_DND_ENABLED, false)

    fun getDndStartHour(): Int = prefs.getInt(KEY_DND_START_HOUR, 22)

    fun getDndEndHour(): Int = prefs.getInt(KEY_DND_END_HOUR, 8)

    fun setDndSettings(enabled: Boolean, startHour: Int, endHour: Int) {
        prefs.edit()
            .putBoolean(KEY_DND_ENABLED, enabled)
            .putInt(KEY_DND_START_HOUR, startHour)
            .putInt(KEY_DND_END_HOUR, endHour)
            .apply()
    }

    /** 当前是否处于免打扰时段（支持跨午夜区间，如 22 点到次日 8 点） */
    fun isQuietHoursNow(): Boolean {
        if (!isDndEnabled()) return false
        val hour = java.time.LocalTime.now().hour
        val start = getDndStartHour()
        val end = getDndEndHour()
        return when {
            start == end -> false
            start < end -> hour in start until end
            else -> hour >= start || hour < end
        }
    }
}