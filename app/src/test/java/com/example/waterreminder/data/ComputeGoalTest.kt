package com.example.waterreminder.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 目标计算纯逻辑：EFSA 基数 + 运动固定增量 / 体重交叉校验 / 夹紧取整 */
class ComputeGoalTest {

    @Test
    fun `male sedentary without weight uses EFSA base`() {
        assertEquals(2500, GoalCalculator.calculateRecommendedGoal(Gender.MALE, null, ActivityLevel.SEDENTARY))
    }

    @Test
    fun `female with light activity adds fixed increment`() {
        assertEquals(2400, GoalCalculator.calculateRecommendedGoal(Gender.FEMALE, null, ActivityLevel.LIGHT))
    }

    @Test
    fun `other gender averages both EFSA bases and rounds to hundred`() {
        assertEquals(2300, GoalCalculator.calculateRecommendedGoal(Gender.OTHER, null, ActivityLevel.SEDENTARY))
    }

    @Test
    fun `very active male reaches base plus 800`() {
        assertEquals(3300, GoalCalculator.calculateRecommendedGoal(Gender.MALE, null, ActivityLevel.VERY_ACTIVE))
    }

    @Test
    fun `heavy weight crosses over the base via 30ml per kg`() {
        assertEquals(2100, GoalCalculator.calculateRecommendedGoal(Gender.FEMALE, 70, ActivityLevel.SEDENTARY))
    }

    @Test
    fun `weight check never lowers the base`() {
        assertEquals(2500, GoalCalculator.calculateRecommendedGoal(Gender.MALE, 40, ActivityLevel.SEDENTARY))
    }

    @Test
    fun `extreme weight is clamped to 5000`() {
        assertEquals(5000, GoalCalculator.calculateRecommendedGoal(Gender.MALE, 200, ActivityLevel.VERY_ACTIVE))
    }

    @Test
    fun `raw goal reflects pre-clamp value for clamp disclosure`() {
        assertEquals(6000, GoalCalculator.calculateRawGoal(Gender.MALE, 200, ActivityLevel.VERY_ACTIVE))
    }

    @Test
    fun `suggested interval spreads the goal across awake hours`() {
        assertEquals(3, GoalCalculator.suggestedIntervalHours(1500))
        assertEquals(2, GoalCalculator.suggestedIntervalHours(2000))
        assertEquals(2, GoalCalculator.suggestedIntervalHours(2500))
        assertEquals(1, GoalCalculator.suggestedIntervalHours(4500))
    }
}
