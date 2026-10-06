package com.example.waterreminder.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

/** 日历网格几何：周日开头（dayOfWeek % 7 把周日折成 0），行数向上取整。 */
class CalendarGridTest {

    @Test
    fun `2024年9月1日是周日_30天占5行`() {
        val (offset, rows) = calendarGrid(YearMonth.of(2024, 9))
        assertEquals(0, offset)
        assertEquals(5, rows)
    }

    @Test
    fun `2024年2月闰月1日是周四_29天占5行`() {
        val (offset, rows) = calendarGrid(YearMonth.of(2024, 2))
        assertEquals(4, offset)
        assertEquals(5, rows)
    }

    @Test
    fun `2024年6月1日是周六_30天占6行`() {
        val (offset, rows) = calendarGrid(YearMonth.of(2024, 6))
        assertEquals(6, offset)
        assertEquals(6, rows)
    }
}
