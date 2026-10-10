package com.example.waterreminder.data

import java.time.LocalDate
import java.time.YearMonth

/**
 * 饮水统计口径（纯函数，便于单测）。连续达标天数、月度达标、最近 7 天窗口与
 * 水合折算被首页、历史页、导出多处消费，口径只在此处定义一次。
 * DAO 汇总 SQL 的 `SUM(amount * hydration)` 与 [effectiveAmount] 同口径——
 * SQL 侧无法复用 Kotlin 函数，靠注释与 DrinkTypeHydrationTest 锚定系数。
 */
object WaterStats {

    /** 单条记录的折算量：amount × hydration（显示取整或导出保留小数由调用方决定） */
    fun effectiveAmount(amount: Int, hydration: Double): Double = amount * hydration

    /** 连续达标天数：today 未达标则从昨天起算，不因"还没喝"而清零 */
    fun computeStreak(totals: List<DailyTotal>, goal: Int, today: LocalDate): Int {
        if (totals.isEmpty()) return 0
        val map = totals.associate { it.recordDate to it.total }
        var date = today
        if ((map[date.toString()] ?: 0) < goal) {
            date = date.minusDays(1)
        }
        var streak = 0
        while ((map[date.toString()] ?: 0) >= goal) {
            streak++
            date = date.minusDays(1)
        }
        return streak
    }

    /**
     * 月度达标统计：返回 [本月达标天数, 统计天数]。
     * 当前月只统计到今天为止（未来天数不计入分母）。
     */
    fun monthReachedStats(
        totals: List<DailyTotal>,
        goal: Int,
        month: YearMonth,
        today: LocalDate
    ): Pair<Int, Int> {
        val map = totals.associate { it.recordDate to it.total }
        val countedDays = if (month == YearMonth.from(today)) today.dayOfMonth else month.lengthOfMonth()
        var reached = 0
        for (day in 1..countedDays) {
            if ((map[month.atDay(day).toString()] ?: 0) >= goal) reached++
        }
        return reached to countedDays
    }

    /** 最近 7 天窗口（缺失的日期补 0），末位是 today；供周柱状图消费 */
    fun last7Days(totals: List<DailyTotal>, today: LocalDate): List<Pair<LocalDate, Int>> {
        val map = totals.associate { it.recordDate to it.total }
        return (6 downTo 0).map { offset ->
            val d = today.minusDays(offset.toLong())
            d to (map[d.toString()] ?: 0)
        }
    }

    /** 周统计摘要：达标天数、有记录天数、有记录日均（无记录时为 0） */
    data class WeeklySummary(val reachedDays: Int, val loggedDays: Int, val average: Int)

    fun weeklySummary(week: List<Pair<LocalDate, Int>>, goal: Int): WeeklySummary {
        val loggedDays = week.count { it.second > 0 }
        return WeeklySummary(
            reachedDays = week.count { it.second >= goal },
            loggedDays = loggedDays,
            average = if (loggedDays > 0) week.sumOf { it.second } / loggedDays else 0
        )
    }
}
