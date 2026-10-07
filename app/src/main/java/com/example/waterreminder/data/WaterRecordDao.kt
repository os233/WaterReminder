package com.example.waterreminder.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterRecordDao {
    // 返回自增行 id：首页「撤销本次记录」需要按 id 精确删除刚插入的那条
    @Insert
    suspend fun insert(record: WaterRecord): Long

    @Delete
    suspend fun delete(record: WaterRecord)

    @Query("DELETE FROM water_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    // 某日折算总量（「今天」= 调用方传 today.toString()）：SUM(amount * hydration) 取整。
    // ⚠️ date 必须由调用方显式传入，不能给 `LocalDate.now()` 默认值：默认值只在调用那一刻
    // 求值一次，一旦界面持有它就不再随跨天刷新，正是「日期停在旧值」那类 bug 的温床。
    // 界面侧的「今天」统一来自 ui/RememberToday.kt 的 rememberToday()。
    @Query("SELECT CAST(SUM(amount * hydration) AS INTEGER) FROM water_records WHERE date(timestamp) = date(:date)")
    fun getDailyTotal(date: String): Flow<Int?>

    @Query("SELECT * FROM water_records WHERE date(timestamp) = date(:date) ORDER BY timestamp DESC")
    fun getRecordsByDate(date: String): Flow<List<WaterRecord>>

    @Query("SELECT DISTINCT date(timestamp) as recordDate FROM water_records ORDER BY recordDate DESC")
    fun getAllRecordDates(): Flow<List<String>>

    @Query("DELETE FROM water_records WHERE date(timestamp) = date(:date)")
    suspend fun deleteRecordsByDate(date: String)

    @Query("SELECT date(timestamp) as recordDate, CAST(SUM(amount * hydration) AS INTEGER) as total FROM water_records GROUP BY date(timestamp) ORDER BY recordDate DESC")
    fun getAllDailyTotals(): Flow<List<DailyTotal>>

    // 全量导出（CSV）用：按时间升序，表格软件里按时间顺序阅读
    @Query("SELECT * FROM water_records ORDER BY timestamp ASC")
    suspend fun getAllRecordsForExport(): List<WaterRecord>
}

data class DailyTotal(
    val recordDate: String,
    val total: Int
)
