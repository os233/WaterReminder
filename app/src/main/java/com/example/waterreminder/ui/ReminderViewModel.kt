package com.example.waterreminder.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.waterreminder.notification.AlarmManagerHelper

/**
 * 提醒设置卡片的状态持有者：持有单一 [AlarmManagerHelper] 实例（此前每次组合
 * 都 new 一个），间隔排定决策与免打扰保存从这里走。跳系统设置页的 intent 启动、
 * 通知权限 / 电池白名单的展示判定留在界面层（生命周期可见性状态，非业务写操作）。
 */
class ReminderViewModel(private val helper: AlarmManagerHelper) : ViewModel() {

    /** 已保存的提醒间隔（0 = 关闭） */
    fun savedInterval(): Int = helper.getSavedInterval()

    fun isDndEnabled(): Boolean = helper.isDndEnabled()

    fun dndStartHour(): Int = helper.getDndStartHour()

    fun dndEndHour(): Int = helper.getDndEndHour()

    /**
     * 设置 / 关闭提醒。
     * @return 是否真的生效 —— 精确闹钟权限被拒时排不上，返回 false，调用方据此别改界面状态，
     *         否则卡片会显示一个系统里并不存在的提醒（用户以为设上了，其实永远不会响）。
     */
    fun setReminder(hours: Int): Boolean {
        if (hours == 0) {
            helper.cancelAlarm()
            return true
        }
        return helper.setRepeatingAlarm(hours)
    }

    /** 保存夜间免打扰设置（跨午夜时段语义由 AlarmManagerHelper 承担） */
    fun saveDnd(enabled: Boolean, startHour: Int, endHour: Int) =
        helper.setDndSettings(enabled, startHour, endHour)

    companion object {
        /** 手动构造（无 DI）：界面上 viewModel(factory = ReminderViewModel.factory(context)) */
        fun factory(context: Context) = viewModelFactory {
            initializer { ReminderViewModel(AlarmManagerHelper(context.applicationContext)) }
        }
    }
}
