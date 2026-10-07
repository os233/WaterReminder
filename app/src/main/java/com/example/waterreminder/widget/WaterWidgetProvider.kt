package com.example.waterreminder.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 4x2 桌面小部件；日常刷新由数据变更方经 [WaterWidgetUpdater] 主动推送 */
class WaterWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Log.i(TAG, "widget onUpdate (${appWidgetIds.size} instances)")
        // 系统回调里读库需要让出时间窗口：goAsync + IO 协程，完成后必须手工 finish
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WaterWidgetUpdater.refresh(context)
            } catch (e: Exception) {
                // 刷新失败只记日志：小部件停留在上次内容，不拖垮进程
                Log.e(TAG, "widget refresh failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/** logcat 统一 tag：用户报障时 `adb logcat -s WaterReminder` 即可取到本应用全部诊断日志 */
private const val TAG = "WaterReminder"
