package com.example.waterreminder.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "water_records")
data class WaterRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val amount: Int,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    // 饮料类型 id，见 DrinkType；hydration 为该次饮水的水合系数
    @ColumnInfo(defaultValue = "water")
    val drinkType: String = DrinkType.WATER.id,
    @ColumnInfo(defaultValue = "1.0")
    val hydration: Double = 1.0
)
