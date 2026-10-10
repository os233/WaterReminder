package com.example.waterreminder.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.waterreminder.data.DailyTotal
import com.example.waterreminder.data.DrinkType
import com.example.waterreminder.data.WaterRecordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 首页业务操作的状态持有者：饮水数据观察与「记一笔 / 撤销 / 改目标」从这里走，
 * Composable 只负责呈现与 Snackbar 交互（撤销提示是呈现决策，留在界面层）。
 * 写库经 [WaterRecordRepository] 单一出口，小部件刷新随之统一，不再散布调用点。
 *
 * 「今天」不自建日期源：观察哪个日期、何时跨天刷新仍由界面的 rememberToday() 决定，
 * VM 只接收日期字符串（AGENTS.md 的日期约束见 ui/RememberToday.kt）。
 */
class HomeViewModel(private val repository: WaterRecordRepository) : ViewModel() {

    /** 每日目标：进程内单一状态源，保存时写穿 UserPrefs 并刷新小部件 */
    private val _goal = MutableStateFlow(repository.currentGoal())
    val goal: StateFlow<Int> = _goal.asStateFlow()

    /** 全部日期折算总量：连续达标天数的数据源 */
    val allDailyTotals: Flow<List<DailyTotal>> = repository.allDailyTotals()

    /** 某日折算总量；日期由界面把 rememberToday() 的值传入 */
    fun dailyTotal(date: String): Flow<Int?> = repository.dailyTotal(date)

    /** 记一笔饮品，返回插入行 id 供「撤销」按行精确删除 */
    suspend fun addRecord(drink: DrinkType, amount: Int): Long =
        repository.addRecord(amount, drink.id, drink.hydration)

    /** 撤销刚才记录的那一笔 */
    suspend fun undoRecord(id: Long) = repository.deleteById(id)

    /** 保存每日目标 */
    suspend fun setGoal(goal: Int) {
        _goal.value = goal
        repository.setDailyGoal(goal)
    }

    companion object {
        /** 手动构造（无 DI）：界面上 viewModel(factory = HomeViewModel.factory(context)) */
        fun factory(context: Context) = viewModelFactory {
            initializer { HomeViewModel(WaterRecordRepository.from(context)) }
        }
    }
}
