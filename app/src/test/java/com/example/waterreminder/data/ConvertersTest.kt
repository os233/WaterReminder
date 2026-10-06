package com.example.waterreminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/**
 * 时间戳 TypeConverter 决定所有饮水记录在数据库里的存取格式。
 * 落库格式是 DateTimeFormatter.ISO_LOCAL_DATE_TIME 的输出，属于持久化兼容面 ——
 * 改格式等于让已发布客户端写出的历史数据读不回来。
 */
class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `LocalDateTime往返无损`() {
        val samples = listOf(
            LocalDateTime.of(2026, 10, 6, 8, 30),
            LocalDateTime.of(2026, 10, 6, 8, 30, 15),
            LocalDateTime.of(2026, 10, 6, 8, 30, 15, 500_000_000),
            LocalDateTime.of(2024, 2, 29, 0, 0),         // 闰日、零点
            LocalDateTime.of(2025, 12, 31, 23, 59, 59)   // 跨年边界
        )
        for (value in samples) {
            assertEquals(value, converters.toLocalDateTime(converters.fromLocalDateTime(value)))
        }
    }

    @Test
    fun `落库格式是ISO_LOCAL_DATE_TIME`() {
        assertEquals(
            "2026-10-06T08:30:15",
            converters.fromLocalDateTime(LocalDateTime.of(2026, 10, 6, 8, 30, 15))
        )
    }

    @Test
    fun `null双向透传`() {
        assertNull(converters.fromLocalDateTime(null))
        assertNull(converters.toLocalDateTime(null))
    }
}
