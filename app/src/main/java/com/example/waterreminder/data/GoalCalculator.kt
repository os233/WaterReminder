package com.example.waterreminder.data

import kotlin.math.roundToInt

/**
 * 个性化每日目标计算（纯函数，便于单测）。
 *
 * 口径与出处：以 EFSA (2010) 成人总水分适宜摄入量为基数（男 2500 ml / 女 2000 ml，
 * 「其他」取均值），运动按档位加固定增量——证据显示固定增量比百分比系数更稳
 * （EFSA 给出运动额外补水 0.4–0.8 L/小时，折算成日常档位固定增量）；再与
 * 「体重 × 30 ml/kg」交叉校验取大者，夹紧 1500–5000 ml 后取整到 100 ml。
 * 注意 EFSA 口径是含食物水分的总水分（约 20–30%），作为饮水目标略偏高——
 * 界面须向用户注明口径并允许手动修改。
 */
object GoalCalculator {
    const val MIN_GOAL_ML = 1500
    const val MAX_GOAL_ML = 5000
    const val WEIGHT_FACTOR_ML_PER_KG = 30

    /** 夹紧前的原始推荐值；供界面做「被夹紧」披露与测试断言 */
    fun calculateRawGoal(gender: Gender, weightKg: Int?, activity: ActivityLevel): Int {
        val base = when (gender) {
            Gender.MALE -> 2500
            Gender.FEMALE -> 2000
            Gender.OTHER -> 2250
        }
        val weightBased = (weightKg ?: 0) * WEIGHT_FACTOR_ML_PER_KG
        return maxOf(base + activity.extraMl, weightBased)
    }

    fun calculateRecommendedGoal(gender: Gender, weightKg: Int?, activity: ActivityLevel): Int {
        val clamped = calculateRawGoal(gender, weightKg, activity).coerceIn(MIN_GOAL_ML, MAX_GOAL_ML)
        return ((clamped + 50) / 100) * 100
    }

    /**
     * 按目标倒推建议提醒间隔（小时）：每约 300 ml 一杯，摊到 16 小时清醒时段，
     * 就近归入应用支持的 1/2/3 小时档。
     */
    fun suggestedIntervalHours(goalMl: Int, awakeHours: Double = 16.0): Int {
        val reminders = (goalMl / 300.0).roundToInt().coerceAtLeast(1)
        val minutes = (awakeHours * 60 / reminders).roundToInt()
        return when {
            minutes <= 90 -> 1
            minutes <= 150 -> 2
            else -> 3
        }
    }
}

/** 性别档位（对应 EFSA 适宜摄入量分档；「其他」取两档均值） */
enum class Gender { MALE, FEMALE, OTHER }

/** 运动档位：每档为每日固定增量（ml） */
enum class ActivityLevel(val extraMl: Int) {
    SEDENTARY(0),
    LIGHT(400),
    MODERATE(500),
    ACTIVE(600),
    VERY_ACTIVE(800)
}
