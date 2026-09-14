package com.example.waterreminder.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface WaterRecordDao {
    @Insert
    suspend fun insert(record: WaterRecord)

    @Delete
    suspend fun delete(record: WaterRecord)

    // 总量按"水合系数"折算：SUM(amount * hydration)，结果取整
    @Query("SELECT CAST(SUM(amount * hydration) AS INTEGER) FROM water_records WHERE date(timestamp) = date(:date)")
    fun getTodayTotal(date: String = LocalDate.now().toString()): Flow<Int?>

    @Query("SELECT * FROM water_records WHERE date(timestamp) = date(:date) ORDER BY timestamp DESC")
    fun getRecordsByDate(date: String): Flow<List<WaterRecord>>

    @Query("SELECT CAST(SUM(amount * hydration) AS INTEGER) FROM water_records WHERE date(timestamp) = date(:date)")
    fun getDailyTotal(date: String): Flow<Int?>

    @Query("SELECT DISTINCT date(timestamp) as recordDate FROM water_records ORDER BY recordDate DESC")
    fun getAllRecordDates(): Flow<List<String>>

    @Query("DELETE FROM water_records WHERE date(timestamp) = date(:date)")
    suspend fun deleteRecordsByDate(date: String)

    @Query("SELECT date(timestamp) as recordDate, CAST(SUM(amount * hydration) AS INTEGER) as total FROM water_records GROUP BY date(timestamp) ORDER BY recordDate DESC")
    fun getAllDailyTotals(): Flow<List<DailyTotal>>
}

data class DailyTotal(
    val recordDate: String,
    val total: Int
)
