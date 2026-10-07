package com.example.waterreminder.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.waterreminder.MainActivity
import com.example.waterreminder.R
import com.example.waterreminder.data.UserPrefs
import com.example.waterreminder.data.WaterDatabase
import com.example.waterreminder.notification.QuickAddReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 桌面小部件唯一的渲染与推送出口：读取当日折算总量与每日目标，构造 RemoteViews
 * 推送到全部实例。饮水记录或目标变化后由数据变更方调用；小部件自身不观察数据库。
 *
 * 「今天」在每次刷新时以 [LocalDate.now] 计算，30 分钟系统周期刷新兜底跨午夜；
 * `rememberToday()` 的生命周期约束只针对应用内 Compose 界面（见 AGENTS.md / programs.md）。
 */
object WaterWidgetUpdater {

    /** 数据未在手上时的通用刷新：IO 线程读当日总量与目标后推送 */
    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val dao = WaterDatabase.getDatabase(appContext).waterRecordDao()
        val total = dao.getDailyTotal(LocalDate.now().toString()).first() ?: 0
        push(appContext, total, UserPrefs.getDailyGoal(appContext))
    }

    /** 已持有当日数据时直接推送，避免重复查询（如 QuickAddReceiver 写库后） */
    fun push(context: Context, total: Int, goal: Int) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val component = ComponentName(context, WaterWidgetProvider::class.java)
        if (manager.getAppWidgetIds(component)?.isNotEmpty() == true) {
            manager.updateAppWidget(component, buildViews(context, total, goal))
        }
    }

    private fun buildViews(context: Context, total: Int, goal: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_water_4x2)
        val safeGoal = goal.coerceAtLeast(1)
        views.setTextViewText(
            R.id.widget_date_text,
            LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日 EEEE"))
        )
        views.setTextViewText(R.id.widget_amount_text, "已记 ${total.coerceAtLeast(0)} / $safeGoal ml")
        views.setProgressBar(
            R.id.widget_progress_bar,
            safeGoal,
            total.coerceIn(0, safeGoal),
            false
        )

        // 快捷记录三键：显式广播给 QuickAddReceiver，与提醒通知按钮共用同一条写库路径。
        // requestCode 与通知侧（2001+）错开：filterEquals 不比较 extras，同码会互相覆盖
        val addIds = intArrayOf(R.id.widget_add_200, R.id.widget_add_350, R.id.widget_add_500)
        QuickAddReceiver.QUICK_AMOUNTS.forEachIndexed { index, amount ->
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                3001 + index,
                Intent(context, QuickAddReceiver::class.java)
                    .setAction(QuickAddReceiver.ACTION_QUICK_ADD)
                    .putExtra(QuickAddReceiver.EXTRA_AMOUNT, amount),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(addIds[index], pendingIntent)
        }

        // 点空白区打开主界面
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        return views
    }
}
