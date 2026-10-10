package com.example.waterreminder.ui

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.waterreminder.data.ActivityLevel
import com.example.waterreminder.data.Gender
import com.example.waterreminder.data.UserPrefs
import com.example.waterreminder.notification.AlarmManagerHelper
import com.example.waterreminder.widget.WaterWidgetUpdater

/**
 * 首启引导的状态持有者：「完成引导」的落库编排（个人资料、目标、提醒排定、
 * 小部件推送）从 Composable 收口到这里，并持有本屏唯一的 [AlarmManagerHelper]。
 * 资料项的组合期预填读取留在界面层（展示态初始化，非业务写操作）。
 */
class OnboardingViewModel(
    private val appContext: Context,
    private val alarmHelper: AlarmManagerHelper
) : ViewModel() {

    /**
     * 完成引导的落库编排。
     * @return 提醒是否成功排定——`intervalHours > 0` 且精确闹钟权限被拒时为 false，
     *         界面据此提示；选「不提醒」走取消分支（与提醒卡片同语义）并返回 true。
     */
    fun finishOnboarding(
        gender: Gender,
        weightKg: Int?,
        activity: ActivityLevel,
        goal: Int,
        intervalHours: Int
    ): Boolean {
        UserPrefs.setOnboardingCompleted(appContext, true)
        UserPrefs.setUserGender(appContext, gender)
        UserPrefs.setUserWeightKg(appContext, weightKg)
        UserPrefs.setUserActivityLevel(appContext, activity)
        UserPrefs.setDailyGoal(appContext, goal)
        val scheduled = if (intervalHours > 0) {
            alarmHelper.setRepeatingAlarm(intervalHours)
        } else {
            // 取消已有闹钟，否则旧提醒继续生效、BootReceiver 还会按旧 prefs 补排
            alarmHelper.cancelAlarm()
            true
        }
        // 新装用户尚无记录：把目标先推给小部件，进度条立刻可见
        WaterWidgetUpdater.push(appContext, 0, goal)
        return scheduled
    }

    /** 跳过也要落标记：否则下次启动（仍无记录）会再次进引导 */
    fun skipOnboarding() {
        UserPrefs.setOnboardingCompleted(appContext, true)
    }

    /** 精确闹钟权限是否可用（API < S 视为可用） */
    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmHelper.canScheduleExactAlarms()

    /** 跳系统「闹钟 & 提醒」设置页 */
    fun openAlarmSettings() = alarmHelper.openAlarmSettings()

    companion object {
        /** 手动构造（无 DI）：界面上 viewModel(factory = OnboardingViewModel.factory(context)) */
        fun factory(context: Context) = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                OnboardingViewModel(appContext, AlarmManagerHelper(appContext))
            }
        }
    }
}
