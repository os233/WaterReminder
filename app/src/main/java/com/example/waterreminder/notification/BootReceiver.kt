package com.example.waterreminder.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // 开机后补排；应用被覆盖安装（含应用内更新）同样会清掉进程与闹钟，也要补
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> AlarmManagerHelper(context).restoreAlarmIfNeeded()
        }
    }
}