package com.example.waterreminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** CSV 导出纯逻辑：表头 / 转义 / 水合折算 / 行尾格式 */
class CsvExportTest {

    private fun record(
        amount: Int,
        drinkType: String = "water",
        hydration: Double = 1.0,
        timestamp: String = "2026-10-07T09:30:00"
    ) = WaterRecord(
        amount = amount,
        drinkType = drinkType,
        hydration = hydration,
        timestamp = LocalDateTime.parse(timestamp)
    )

    @Test
    fun `empty export is just the header`() {
        assertEquals(CsvExport.HEADER, CsvExport.buildCsv(emptyList()))
    }

    @Test
    fun `rows are lf separated in input order without trailing newline`() {
        val csv = CsvExport.buildCsv(
            listOf(record(200), record(300, "tea", 0.95, "2026-10-07T10:00:00"))
        )
        val lines = csv.split("\n")
        assertEquals(3, lines.size)
        assertEquals(CsvExport.HEADER, lines[0])
        assertEquals("2026-10-07T09:30:00,200,water,1.0,200.0", lines[1])
        assertEquals("2026-10-07T10:00:00,300,tea,0.95,285.0", lines[2])
        assertFalse(csv.contains("\r"))
        assertFalse(csv.endsWith("\n"))
    }

    @Test
    fun `fields containing comma quote or newline get quoted`() {
        val csv = CsvExport.buildCsv(listOf(record(250, drinkType = "a,b")))
        assertEquals(
            CsvExport.HEADER + "\n2026-10-07T09:30:00,250,\"a,b\",1.0,250.0",
            csv
        )
    }

    @Test
    fun `quotes are doubled inside quoted fields`() {
        val csv = CsvExport.buildCsv(listOf(record(250, drinkType = "say \"hi\"")))
        assertEquals(
            CsvExport.HEADER + "\n2026-10-07T09:30:00,250,\"say \"\"hi\"\"\",1.0,250.0",
            csv
        )
    }

    @Test
    fun `hydration multiplies into effective amount without float noise`() {
        val csv = CsvExport.buildCsv(listOf(record(350, "tea", 0.95)))
        assertTrue(csv.endsWith("332.5"))
    }

    @Test
    fun `amounts keep at least one decimal place`() {
        assertEquals("1.0", CsvExport.formatAmount(1.0))
        assertEquals("0.95", CsvExport.formatAmount(0.95))
        assertEquals("280.0", CsvExport.formatAmount(280.00000000000003))
    }
}
