package com.example.waterreminder.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.waterreminder.data.DailyTotal
import com.example.waterreminder.data.WaterRecord
import com.example.waterreminder.data.WaterRecordRepository
import kotlinx.coroutines.flow.Flow

/**
 * 历史页业务操作的状态持有者：记录的删除 / 撤销 / 清空与 CSV 导出从这里走，
 * Composable 只负责呈现与 Snackbar 交互。写库经 [WaterRecordRepository] 单一出口。
 *
 * 「今天」不自建日期源：清空哪一天由界面把 rememberToday() 的值传入。
 */
class HistoryViewModel(private val repository: WaterRecordRepository) : ViewModel() {

    /** 全部日期折算总量：周窗口 / 月度达标 / 连续天数的数据源 */
    fun allDailyTotals(): Flow<List<DailyTotal>> = repository.allDailyTotals()

    /** 某日全部记录，按时间倒序 */
    fun recordsByDate(date: String): Flow<List<WaterRecord>> = repository.recordsByDate(date)

    /** 某日折算总量 */
    fun dailyTotal(date: String): Flow<Int?> = repository.dailyTotal(date)

    /** 当前每日目标（只读；写入口在首页） */
    fun currentGoal(): Int = repository.currentGoal()

    /** 删除单条记录（左滑 / 长按） */
    suspend fun deleteRecord(record: WaterRecord) = repository.deleteRecord(record)

    /** 撤销删除：重插原记录 */
    suspend fun undoDelete(record: WaterRecord) = repository.restoreRecord(record)

    /** 清空某日全部记录（「清空今日」入口，不可恢复） */
    suspend fun clearDate(date: String) = repository.clearDate(date)

    /** 全量导出 CSV 到 SAF 选定的目标，返回条数；失败原样抛出由界面提示 */
    suspend fun exportCsv(uri: Uri): Int = repository.exportAllToCsv(uri)

    companion object {
        /** 手动构造（无 DI）：界面上 viewModel(factory = HistoryViewModel.factory(context)) */
        fun factory(context: Context) = viewModelFactory {
            initializer { HistoryViewModel(WaterRecordRepository.from(context)) }
        }
    }
}
