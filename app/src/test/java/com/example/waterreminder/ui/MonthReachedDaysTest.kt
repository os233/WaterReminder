package com.example.waterreminder.ui

import com.example.waterreminder.data.DailyTotal
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** 月度达标统计纯逻辑：当前月只统计已过天数，过去月统计整月 */
class MonthReachedDaysTest {

    private fun totals(vararg pairs: Pair<String, Int>) =
        pairs.map { DailyTotal(it.first, it.second) }

    @Test
    fun `current month counts only elapsed days`() {
        val (reached, counted) = monthReachedStats(
            totals("2026-10-01" to 2000, "2026-10-07" to 2000),
            2000, YearMonth.of(2026, 10), LocalDate.parse("2026-10-07")
        )
        assertEquals(7, counted)
        assertEquals(2, reached)
    }

    @Test
    fun `past month counts the full month`() {
        val totals = (1..30).map { DailyTotal("2026-09-%02d".format(it), 2000) }
        val (reached, counted) = monthReachedStats(
            totals, 2000, YearMonth.of(2026, 9), LocalDate.parse("2026-10-07")
        )
        assertEquals(30, counted)
        assertEquals(30, reached)
    }

    @Test
    fun `below goal days are not counted`() {
        val (reached, _) = monthReachedStats(
            totals("2026-10-01" to 1999, "2026-10-02" to 2000),
            2000, YearMonth.of(2026, 10), LocalDate.parse("2026-10-07")
        )
        assertEquals(1, reached)
    }

    @Test
    fun `empty totals reach zero but keep the denominator`() {
        val (reached, counted) = monthReachedStats(
            emptyList(), 2000, YearMonth.of(2026, 10), LocalDate.parse("2026-10-07")
        )
        assertEquals(0, reached)
        assertEquals(7, counted)
    }
}
