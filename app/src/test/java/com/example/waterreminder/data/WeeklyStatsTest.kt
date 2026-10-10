package com.example.waterreminder.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 最近 7 天窗口与周统计：缺失日期补 0、达标/有记录/日均口径 */
class WeeklyStatsTest {

    private val today = LocalDate.of(2026, 10, 7)

    private fun totals(vararg pairs: Pair<LocalDate, Int>): List<DailyTotal> =
        pairs.map { (date, total) -> DailyTotal(date.toString(), total) }

    @Test
    fun `窗口固定为7天且末位是今天`() {
        val week = WaterStats.last7Days(
            totals(today to 2000, today.minusDays(6) to 1500),
            today
        )
        assertEquals(7, week.size)
        assertEquals(today, week.last().first)
        assertEquals(2000, week.last().second)
        assertEquals(1500, week.first().second)
    }

    @Test
    fun `缺失日期补0`() {
        val week = WaterStats.last7Days(totals(today to 2000), today)
        assertEquals(0, week.first().second) // 6 天前无记录
        assertEquals(0, week[5].second)      // 昨天无记录
        assertEquals(2000, week.last().second)
    }

    @Test
    fun `达标数按目标线统计`() {
        val week = WaterStats.last7Days(
            totals(today to 2000, today.minusDays(1) to 1999, today.minusDays(2) to 3000),
            today
        )
        assertEquals(2, WaterStats.weeklySummary(week, goal = 2000).reachedDays)
    }

    @Test
    fun `日均只计有记录的天`() {
        val week = WaterStats.last7Days(
            totals(today to 1000, today.minusDays(1) to 3000),
            today
        )
        val summary = WaterStats.weeklySummary(week, goal = 2000)
        assertEquals(2, summary.loggedDays)
        assertEquals(2000, summary.average) // (1000 + 3000) / 2
    }

    @Test
    fun `无记录时统计全零而不除零`() {
        val summary = WaterStats.weeklySummary(WaterStats.last7Days(emptyList(), today), goal = 2000)
        assertEquals(0, summary.reachedDays)
        assertEquals(0, summary.loggedDays)
        assertEquals(0, summary.average)
    }
}
