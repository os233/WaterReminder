package com.example.waterreminder.ui

import com.example.waterreminder.data.DailyTotal
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 连续达标天数：关键语义是「今天未达标不清零，从昨天起算」以及断档即断。 */
class ComputeStreakTest {

    private val today = LocalDate.of(2026, 10, 6)

    private fun totals(vararg pairs: Pair<LocalDate, Int>): List<DailyTotal> =
        pairs.map { (date, total) -> DailyTotal(date.toString(), total) }

    @Test
    fun `空记录返回0`() {
        assertEquals(0, computeStreak(emptyList(), goal = 2000, today = today))
    }

    @Test
    fun `只有今天达标记1天`() {
        assertEquals(1, computeStreak(totals(today to 2000), goal = 2000, today = today))
    }

    @Test
    fun `今天未达标不清零_从昨天起算`() {
        // 今天还没喝，昨天与前天达标 → 连续 2 天
        assertEquals(
            2,
            computeStreak(
                totals(today.minusDays(1) to 2500, today.minusDays(2) to 2000),
                goal = 2000,
                today = today
            )
        )
    }

    @Test
    fun `今天和昨天都未达标则归零`() {
        // 更早的达标不救场
        assertEquals(
            0,
            computeStreak(totals(today.minusDays(2) to 3000), goal = 2000, today = today)
        )
    }

    @Test
    fun `断档打断连续`() {
        assertEquals(
            1,
            computeStreak(
                totals(
                    today to 2000,
                    today.minusDays(1) to 500, // 未达标 → 断
                    today.minusDays(2) to 2000
                ),
                goal = 2000,
                today = today
            )
        )
    }

    @Test
    fun `恰好等于目标也算达标`() {
        assertEquals(
            2,
            computeStreak(
                totals(today to 2000, today.minusDays(1) to 2000),
                goal = 2000,
                today = today
            )
        )
    }

    @Test
    fun `记录顺序不影响结果`() {
        // DAO 按日期倒序返回，这里故意正序传入
        val list = totals(
            today.minusDays(3) to 2100,
            today.minusDays(2) to 2200,
            today.minusDays(1) to 2300,
            today to 2400
        ).reversed()
        assertEquals(4, computeStreak(list, goal = 2000, today = today))
    }
}
