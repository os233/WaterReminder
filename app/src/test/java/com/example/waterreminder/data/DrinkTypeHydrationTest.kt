package com.example.waterreminder.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 水合系数是每日总量的统计口径：README 与官网都按这张表宣传，改值会静默改变所有统计结果。 */
class DrinkTypeHydrationTest {

    @Test
    fun `水合系数与README口径一致`() {
        assertEquals(1.0, DrinkType.WATER.hydration, 1e-9)
        assertEquals(0.95, DrinkType.TEA.hydration, 1e-9)
        assertEquals(0.8, DrinkType.COFFEE.hydration, 1e-9)
        assertEquals(0.85, DrinkType.JUICE.hydration, 1e-9)
        assertEquals(1.0, DrinkType.SODA.hydration, 1e-9)
    }
}
