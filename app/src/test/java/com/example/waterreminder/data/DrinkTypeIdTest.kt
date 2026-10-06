package com.example.waterreminder.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** drinkType 列存的是 id 字符串，byId 的兜底决定老数据 / 异常数据的显示安全。 */
class DrinkTypeIdTest {

    @Test
    fun `已知id映射到对应饮料`() {
        assertEquals(DrinkType.WATER, DrinkType.byId("water"))
        assertEquals(DrinkType.TEA, DrinkType.byId("tea"))
        assertEquals(DrinkType.COFFEE, DrinkType.byId("coffee"))
        assertEquals(DrinkType.JUICE, DrinkType.byId("juice"))
        assertEquals(DrinkType.SODA, DrinkType.byId("soda"))
    }

    @Test
    fun `未知或空id回退到水`() {
        // v1→v2 迁移后 drinkType 列是自由字符串，异常值全靠这个兜底
        assertEquals(DrinkType.WATER, DrinkType.byId("unknown"))
        assertEquals(DrinkType.WATER, DrinkType.byId(""))
    }
}
