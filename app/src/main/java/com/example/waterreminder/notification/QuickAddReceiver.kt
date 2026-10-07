package com.example.waterreminder.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.waterreminder.R
import com.example.waterreminder.data.UserPrefs
import com.example.waterreminder.data.WaterDatabase
import com.example.waterreminder.data.WaterRecord
import com.example.waterreminder.widget.WaterWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 提醒通知 / 桌面小部件的「快捷记录」入口（阶段二接入小部件）。
 *
 * 走显式组件广播（exported=false）：第三方应用无法伪造触发，也不受 API 34
 * RECEIVER_EXPORTED 规则约束。写库语义与主页快速卡片一致——固定记「水」
 * （WaterRecord 默认 drinkType=water、hydration=1.0），写入量钳制 1–5000 ml。
 *
 * 不触碰提醒链：下一次闹钟由 AlarmReceiver 在触发时自续，这里只补记录与反馈。
 */
class QuickAddReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WaterReminder"

        const val ACTION_QUICK_ADD = "com.example.waterreminder.ACTION_QUICK_ADD"
        const val EXTRA_AMOUNT = "amount"

        /** 反馈用静默渠道：快捷记录是用户主动点击，不需要再震一次提醒他 */
        const val FEEDBACK_CHANNEL_ID = "water_quick_add_feedback"

        /** 与主页快速卡片一致的三档快捷量 */
        val QUICK_AMOUNTS = intArrayOf(200, 350, 500)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_QUICK_ADD) return
        val amount = intent.getIntExtra(EXTRA_AMOUNT, 0).coerceIn(1, 5000)
        Log.i(TAG, "quick add ${amount}ml")

        // goAsync 换取写库时间窗口；Room 禁止主线程访问，IO 协程内完成后必须手工 finish
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = WaterDatabase.getDatabase(context).waterRecordDao()
                dao.insert(WaterRecord(amount = amount))
                val todayTotal = dao.getDailyTotal(LocalDate.now().toString()).first() ?: 0
                // 桌面小部件同步进度；未添加小部件时 push 内部自会跳过
                WaterWidgetUpdater.push(context, todayTotal, UserPrefs.getDailyGoal(context))
                showFeedback(context, amount, todayTotal)
            } catch (e: Exception) {
                // 记一笔不失败，不让点个按钮把进程带走（磁盘满、库异常等）
                Log.e(TAG, "quick add failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * 以同一通知 ID 重发无按钮的短反馈：覆盖掉带按钮的提醒通知本身，用户点击动作后
     * 提醒即完成使命；3 秒后自动消失。复用 ID 1001 同时避免通知栏堆积。
     */
    private fun showFeedback(context: Context, amount: Int, todayTotal: Int) {
        // 通知权限被拒时系统会静默丢弃通知；记录已生效，反馈直接跳过
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "notification permission not granted, skip quick-add feedback")
            return
        }
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // IMPORTANCE_LOW 无声音无震动：这是用户主动点击后的确认，不能再震一次
        // （minSdk 26 起渠道必然可用）
        notificationManager.createNotificationChannel(
            NotificationChannel(
                FEEDBACK_CHANNEL_ID,
                "快捷记录反馈",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "点按通知或小部件记录成功后的轻提示，静默且自动消失"
            }
        )
        val notification =
            NotificationCompat.Builder(context, FEEDBACK_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_water_drop)
                .setContentTitle("已记 $amount ml 💧")
                .setContentText("今日累计 $todayTotal ml")
                .setAutoCancel(true)
                .setTimeoutAfter(3000)
                .build()
        notificationManager.notify(AlarmReceiver.REMINDER_NOTIFICATION_ID, notification)
    }
}
