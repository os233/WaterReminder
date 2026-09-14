package com.example.waterreminder.data

import android.content.Context

/** 轻量用户设置（每日目标等），SharedPreferences 存储 */
object UserPrefs {
    private const val PREFS = "user_prefs"
    private const val KEY_DAILY_GOAL = "daily_goal"

    const val DEFAULT_GOAL = 2000

    fun getDailyGoal(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_DAILY_GOAL, DEFAULT_GOAL)

    fun setDailyGoal(context: Context, goal: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_DAILY_GOAL, goal).apply()
    }
}
