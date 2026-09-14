package com.example.waterreminder.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiFoodBeverage
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WineBar
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 饮料类型。hydration 为"水合系数"：100ml 咖啡按 80ml 水计入每日总量。
 * 参考常见营养学口径：咖啡约 0.8、茶约 0.95、果汁约 0.85，白水/气泡水为 1.0。
 */
enum class DrinkType(
    val id: String,
    val label: String,
    val hydration: Double,
    val color: Color,
    val icon: ImageVector
) {
    WATER("water", "水", 1.0, Color(0xFF29B6F6), Icons.Outlined.WaterDrop),
    TEA("tea", "茶", 0.95, Color(0xFF66BB6A), Icons.Outlined.EmojiFoodBeverage),
    COFFEE("coffee", "咖啡", 0.8, Color(0xFF8D6E63), Icons.Outlined.LocalCafe),
    JUICE("juice", "果汁", 0.85, Color(0xFFFFA726), Icons.Outlined.LocalBar),
    SODA("soda", "气泡水", 1.0, Color(0xFFAB47BC), Icons.Outlined.WineBar);

    companion object {
        fun byId(id: String): DrinkType = entries.firstOrNull { it.id == id } ?: WATER
    }
}
