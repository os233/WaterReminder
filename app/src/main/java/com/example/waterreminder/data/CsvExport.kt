package com.example.waterreminder.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter

/**
 * 饮水记录 CSV 导出（纯函数，便于单测）。
 *
 * 列：timestamp, amount_ml, drink_type, hydration, effective_ml
 * - timestamp 沿用库内 ISO_LOCAL_DATE_TIME 文本（机器可读、Excel 可识别）；
 * - effective_ml = amount × hydration（水合折算），最多保留两位小数；
 * - 转义为 RFC 4180 简化版：仅当字段含逗号/引号/换行时加引号，内部引号翻倍；
 * - 行尾 LF、UTF-8 无 BOM——当前 drink_type 全为 ASCII id，无乱码风险；
 *   若未来引入含逗号的中文自定义饮品名，此转义已能覆盖。
 * 已知教训（Loop Habit Tracker #1652）：绝不能把 data class 默认 toString()
 * 写进单元格——每列都用本文件的专用格式化器。
 */
object CsvExport {
    const val HEADER = "timestamp,amount_ml,drink_type,hydration,effective_ml"

    fun buildCsv(records: List<WaterRecord>): String {
        val lines = ArrayList<String>(records.size + 1)
        lines.add(HEADER)
        records.forEach { record ->
            val effective = record.amount * record.hydration
            lines.add(
                listOf(
                    // 用 ISO_LOCAL_DATE_TIME 格式化器而非 toString()：后者秒为 0 时会省略
                    // 「:00」，与库内存储文本不一致
                    escapeCsv(record.timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)),
                    escapeCsv(record.amount.toString()),
                    escapeCsv(record.drinkType),
                    escapeCsv(formatAmount(record.hydration)),
                    escapeCsv(formatAmount(effective))
                ).joinToString(",")
            )
        }
        return lines.joinToString("\n")
    }

    /** 数值最多保留两位小数（对消浮点噪声如 280.00000000000003），且至少一位小数 */
    internal fun formatAmount(value: Double): String {
        val rounded = BigDecimal(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
        return if (rounded.scale() < 1) rounded.setScale(1).toPlainString()
        else rounded.toPlainString()
    }

    internal fun escapeCsv(field: String): String =
        if (field.contains(',') || field.contains('"') || field.contains('\n') || field.contains('\r')) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
}
