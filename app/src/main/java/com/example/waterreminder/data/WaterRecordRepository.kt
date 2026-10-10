package com.example.waterreminder.data

import android.content.Context
import android.net.Uri
import com.example.waterreminder.widget.WaterWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 饮水记录与每日目标的单一业务出口（轻量 Repository，无接口抽象）。
 *
 * 为什么有这一层（programs.md 6.3 演进记录）：此前「记一笔」在首页 UI 与
 * QuickAddReceiver 各有一套实现，小部件刷新散布在 7 处调用点，改数据忘了
 * 推小部件、两处口径漂移都是真实发生过的维护问题。现在所有写库操作从这里走，
 * 写完统一刷新小部件；消费者是 UI 层各 ViewModel 与 notification/QuickAddReceiver。
 *
 * 不负责：提醒调度（notification/AlarmManagerHelper）、更新检查（data/remote/）、
 * 统计口径计算（WaterStats）。
 */
class WaterRecordRepository(context: Context) {

    private val appContext = context.applicationContext

    // WaterDatabase 是进程级单例，每次经它取 dao 的开销可忽略
    private val dao get() = WaterDatabase.getDatabase(appContext).waterRecordDao()

    /** 小部件未持有数据时的通用刷新；未添加小部件时 push 内部自会跳过 */
    private suspend fun refreshWidget() = WaterWidgetUpdater.refresh(appContext)

    // ── 观察（Flow 直接透传 DAO 查询，日期参数由调用方显式传入） ──

    /** 某日折算总量；「今天」由界面侧 rememberToday() 提供后传入 */
    fun dailyTotal(date: String): Flow<Int?> = dao.getDailyTotal(date)

    /** 某日全部记录，按时间倒序 */
    fun recordsByDate(date: String): Flow<List<WaterRecord>> = dao.getRecordsByDate(date)

    /** 全部日期的折算总量（连续天数 / 月度达标 / 周窗口的数据源） */
    fun allDailyTotals(): Flow<List<DailyTotal>> = dao.getAllDailyTotals()

    /** 出现过记录的全部日期（首启门控用） */
    fun allRecordDates(): Flow<List<String>> = dao.getAllRecordDates()

    // ── 写入（写完统一刷新小部件） ──

    /**
     * 记一笔饮品（首页快速记录 / 自定义水量），返回插入行 id 供撤销精确删除。
     * 水量合法性由调用方按 [WaterRecord.MIN_AMOUNT_ML]..[WaterRecord.MAX_AMOUNT_ML] 校验。
     */
    suspend fun addRecord(amount: Int, drinkType: String, hydration: Double): Long {
        val id = dao.insert(
            WaterRecord(amount = amount, drinkType = drinkType, hydration = hydration)
        )
        refreshWidget()
        return id
    }

    /** 撤销本次记录：按行 id 删除（不动其他记录） */
    suspend fun deleteById(id: Long) {
        dao.deleteById(id)
        refreshWidget()
    }

    /** 删除单条记录（历史页左滑 / 长按） */
    suspend fun deleteRecord(record: WaterRecord) {
        dao.delete(record)
        refreshWidget()
    }

    /** 撤销删除：重插原记录（id 归零让 Room 重新自增） */
    suspend fun restoreRecord(record: WaterRecord) {
        dao.insert(record.copy(id = 0))
        refreshWidget()
    }

    /** 清空某日全部记录（历史页「清空今日」，不可恢复） */
    suspend fun clearDate(date: String) {
        dao.deleteRecordsByDate(date)
        refreshWidget()
    }

    /**
     * 通知 / 小部件快捷记录入口：固定记「水」（与主页快速卡片的三档量共用
     * QuickAddReceiver.QUICK_AMOUNTS，但语义是水，不做饮料选择），返回记录后的
     * 当日折算总量，供反馈通知直接使用，避免重复查询。
     */
    suspend fun quickAddWater(amount: Int): Int {
        dao.insert(WaterRecord(amount = amount))
        val todayTotal = dao.getDailyTotal(LocalDate.now().toString()).first() ?: 0
        // 已持有当日总量，直接 push 省一次查询
        WaterWidgetUpdater.push(appContext, todayTotal, UserPrefs.getDailyGoal(appContext))
        return todayTotal
    }

    /**
     * 保存每日目标。目标是小部件进度分母，保存后按库内最新总量刷新小部件。
     */
    suspend fun setDailyGoal(goal: Int) {
        UserPrefs.setDailyGoal(appContext, goal)
        refreshWidget()
    }

    /** 当前每日目标（ViewModel 初始化与页面重读用；写入口是 [setDailyGoal]） */
    fun currentGoal(): Int = UserPrefs.getDailyGoal(appContext)

    /** CSV 导出：全量记录按时间升序 */
    suspend fun allRecordsForExport(): List<WaterRecord> = dao.getAllRecordsForExport()

    /**
     * 全量导出 CSV 到 SAF 选定的目标：IO 线程查库、拼 CSV、写流，返回导出条数。
     * 失败原样抛出（云盘供应商报错不吞），成功/失败的提示由调用方呈现。
     */
    suspend fun exportAllToCsv(uri: Uri): Int = withContext(Dispatchers.IO) {
        val records = dao.getAllRecordsForExport()
        val csv = CsvExport.buildCsv(records)
        val output = appContext.contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("所选位置无法写入")
        output.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
        records.size
    }

    companion object {
        fun from(context: Context): WaterRecordRepository =
            WaterRecordRepository(context)
    }
}
