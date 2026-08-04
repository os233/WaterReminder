package com.example.waterreminder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface WaterRecordDao {
    @Insert
    suspend fun insert(record: WaterRecord)

    @Query("SELECT * FROM water_records WHERE date(timestamp) = date(:date) ORDER BY timestamp DESC")
    fun getTodayRecords(date: String = LocalDate.now().toString()): Flow<List<WaterRecord>>

    @Query("SELECT SUM(amount) FROM water_records WHERE date(timestamp) = date(:date)")
    fun getTodayTotal(date: String = LocalDate.now().toString()): Flow<Int?>

    @Query("SELECT * FROM water_records WHERE date(timestamp) = date(:date) ORDER BY timestamp DESC")
    fun getRecordsByDate(date: String): Flow<List<WaterRecord>>

    @Query("SELECT SUM(amount) FROM water_records WHERE date(timestamp) = date(:date)")
    fun getDailyTotal(date: String): Flow<Int?>

    @Query("SELECT DISTINCT date(timestamp) as recordDate FROM water_records ORDER BY recordDate DESC")
    fun getAllRecordDates(): Flow<List<String>>

    @Query("DELETE FROM water_records WHERE date(timestamp) = date(:date)")
    suspend fun deleteRecordsByDate(date: String)

    @Query("SELECT date(timestamp) as recordDate, SUM(amount) as total FROM water_records GROUP BY date(timestamp) ORDER BY recordDate DESC")
    fun getAllDailyTotals(): Flow<List<DailyTotal>>

    @Query("SELECT COUNT(*) FROM water_records WHERE date(timestamp) = date(:date)")
    suspend fun getRecordCountByDate(date: String): Int
}

data class DailyTotal(
    val recordDate: String,
    val total: Int
)