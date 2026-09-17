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

        /** 提醒闹钟的 PendingIntent requestCode；查询与取消必须与排程时一致 */
        private const val ALARM_REQUEST_CODE = 1001

        /** 原定触发时刻（epoch ms）。补排时用它还原，而不是从「现在」重新起算 */
        const val KEY_NEXT_TRIGGER = "reminder_next_trigger_ms"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true
    }

    fun openAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
            // 非 Activity context（BroadcastReceiver）启动 Activity 必须带 NEW_TASK，
            // 否则抛 AndroidRuntimeException，直接把 receiver 崩掉
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // 后台启动 Activity 可能被系统拦下，这里不能让调用方（receiver）跟着崩
        runCatching { context.startActivity(intent) }
    }

    /** @return 是否真的排上了。精确闹钟权限被拒时返回 false —— 调用方据此别改界面状态 */
    fun setRepeatingAlarm(intervalHours: Int): Boolean {
        if (intervalHours <= 0) {
            cancelAlarm()
            return true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Toast.makeText(context, "请先允许设置精确闹钟", Toast.LENGTH_LONG).show()
            openAlarmSettings()
            return false
        }

        armAt(
            Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, intervalHours) }.timeInMillis,
            intervalHours
        )
        return true
    }

    /** 按指定时刻排闹钟，并把「原定触发时刻」记进 prefs —— 补排时靠它还原，不重新起算 */
    private fun armAt(triggerTime: Long, intervalHours: Int) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("interval_hours", intervalHours)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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
            putLong(KEY_NEXT_TRIGGER, triggerTime)
        }.apply()
    }

    fun cancelAlarm() {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()

        prefs.edit().apply {
            putBoolean(KEY_ENABLED, false)
            putLong(KEY_NEXT_TRIGGER, 0L)
        }.apply()
    }

    /**
     * 补排闹钟：prefs 记着「已开启」时，每次启动都确认一次闹钟确实排上了。
     * force-stop（系统省电、一键清理）和重启设备都会清掉闹钟，而 prefs 里的开关仍是「开启」——
     * 界面看着正常，实际再也不会响，只能靠这里补。
     *
     * 判据不能用「PendingIntent 还在不在」：它的记录生命周期和闹钟不同步 —— Android 12 实测
     * force-stop 后闹钟已被清空、PendingIntent 记录却原样残留，那样会把补排永久拦死。
     * 改成按 prefs 里记的原定触发时刻重排：同一时刻重复排是幂等的，不会把提醒时间往后推。
     */
    fun restoreAlarmIfNeeded() {
        if (!prefs.getBoolean(KEY_ENABLED, false)) return
        val interval = prefs.getInt(KEY_INTERVAL, 1)
        if (interval <= 0) return

        val scheduledAt = prefs.getLong(KEY_NEXT_TRIGGER, 0L)
        if (scheduledAt > System.currentTimeMillis()) {
            armAt(scheduledAt, interval)
        } else {
            // 原定时刻已过（多半是 force-stop 期间到的点）→ 重新起算，避免补一个过期的闹钟
            setRepeatingAlarm(interval)
        }
    }

    /**
     * 当前「生效中」的提醒间隔；提醒未开启时返回 0。
     * KEY_INTERVAL 会保留用户上次选的值，所以不能只看它 —— 否则关闭提醒后重开 App，
     * 界面仍会显示「每 N 小时提醒一次」，而系统里一个闹钟都没有。
     */
    fun getSavedInterval(): Int {
        if (!prefs.getBoolean(KEY_ENABLED, false)) return 0
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