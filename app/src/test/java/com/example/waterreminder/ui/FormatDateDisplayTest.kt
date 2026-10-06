package com.example.waterreminder.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 历史页日期列文案：今天 / 昨天 / 「N月N日 周X」，坏数据原样回显（dayOfWeek % 7 把周日折成 0）。 */
class FormatDateDisplayTest {

    /** 2026-10-06 是周二 */
    private val today = LocalDate.of(2026, 10, 6)

    @Test
    fun `当天显示今天`() {
        assertEquals("今天", formatDateDisplay("2026-10-06", today))
    }

    @Test
    fun `前一天显示昨天`() {
        assertEquals("昨天", formatDateDisplay("2026-10-05", today))
    }

    @Test
    fun `更早日期显示月日与星期`() {
        // 2026-01-30 是周五；2026-10-04 是周日 —— dayOfWeek=7，%7 后落在「周日」位
        assertEquals("1月30日 周五", formatDateDisplay("2026-01-30", today))
        assertEquals("10月4日 周日", formatDateDisplay("2026-10-04", today))
    }

    @Test
    fun `跨年日期正常格式化`() {
        // 2025-12-31 是周三
        assertEquals("12月31日 周三", formatDateDisplay("2025-12-31", today))
    }

    @Test
    fun `解析失败原样回显不崩溃`() {
        assertEquals("not-a-date", formatDateDisplay("not-a-date", today))
        assertEquals("", formatDateDisplay("", today))
    }
}
