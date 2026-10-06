package com.example.waterreminder.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 免打扰时段判断的边界行为，重点是跨午夜分支（AGENTS.md 契约：start > end 分支必须保留）。 */
class IsQuietHourTest {

    @Test
    fun `start等于end视为未设区间_任何小时都不拦`() {
        for (hour in 0..23) {
            assertFalse(isQuietHour(hour, start = 8, end = 8))
            assertFalse(isQuietHour(hour, start = 0, end = 0))
            assertFalse(isQuietHour(hour, start = 22, end = 22))
        }
    }

    @Test
    fun `普通区间含起点不含终点`() {
        // 8 点到 22 点：8..21 算，22 点与 7 点不算
        assertTrue(isQuietHour(8, 8, 22))
        assertTrue(isQuietHour(12, 8, 22))
        assertTrue(isQuietHour(21, 8, 22))
        assertFalse(isQuietHour(22, 8, 22))
        assertFalse(isQuietHour(7, 8, 22))
    }

    @Test
    fun `跨午夜区间覆盖两段且终点不含`() {
        // 22 点到次日 8 点：22、23、0..7 算；8 点与 21 点不算
        for (hour in listOf(22, 23, 0, 1, 5, 7)) {
            assertTrue("hour=$hour 应处于免打扰", isQuietHour(hour, 22, 8))
        }
        assertFalse(isQuietHour(8, 22, 8))
        assertFalse(isQuietHour(21, 22, 8))
    }

    @Test
    fun `与默认偏好一致_22点到8点`() {
        // AlarmManagerHelper 的 getDndStartHour/getDndEndHour 默认值
        assertTrue(isQuietHour(23, 22, 8))
        assertFalse(isQuietHour(12, 22, 8))
    }
}
