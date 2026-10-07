package com.example.waterreminder.data

import android.content.Context

/** 轻量用户设置（每日目标、引导与个人资料等），SharedPreferences 存储 */
object UserPrefs {
    private const val PREFS = "user_prefs"
    private const val KEY_DAILY_GOAL = "daily_goal"

    // 引导与个人资料（v0.2 新增 key；既有 key 名是已发布客户端的持久化接口，不得改名）
    private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    private const val KEY_USER_GENDER = "user_gender"
    private const val KEY_USER_WEIGHT_KG = "user_weight_kg"
    private const val KEY_USER_ACTIVITY_LEVEL = "user_activity_level"

    const val DEFAULT_GOAL = 2000

    fun getDailyGoal(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_DAILY_GOAL, DEFAULT_GOAL)

    fun setDailyGoal(context: Context, goal: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_DAILY_GOAL, goal).apply()
    }

    fun isOnboardingCompleted(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ONBOARDING_COMPLETED, false)

    fun setOnboardingCompleted(context: Context, completed: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    fun getUserGender(context: Context): Gender? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_USER_GENDER, null)
            ?.let { stored -> runCatching { Gender.valueOf(stored) }.getOrNull() }

    fun setUserGender(context: Context, gender: Gender) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_USER_GENDER, gender.name).apply()
    }

    /** 0 表示未填写 */
    fun getUserWeightKg(context: Context): Int? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_USER_WEIGHT_KG, 0)
            .takeIf { it > 0 }

    fun setUserWeightKg(context: Context, weightKg: Int?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_USER_WEIGHT_KG, weightKg ?: 0).apply()
    }

    fun getUserActivityLevel(context: Context): ActivityLevel? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_USER_ACTIVITY_LEVEL, null)
            ?.let { stored -> runCatching { ActivityLevel.valueOf(stored) }.getOrNull() }

    fun setUserActivityLevel(context: Context, level: ActivityLevel) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_USER_ACTIVITY_LEVEL, level.name).apply()
    }
}
