package com.example.waterreminder.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.waterreminder.data.DrinkType
import com.example.waterreminder.data.WaterRecord
import com.example.waterreminder.data.WaterStats
import com.example.waterreminder.ui.theme.successColor
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(context))
    // 历史页改不了目标；离开本页组合即销毁，回来时经 currentGoal() 重读，天然与首页同步
    val goal = remember { viewModel.currentGoal() }
    val snackbarHostState = remember { SnackbarHostState() }
    val allTotals by viewModel.allDailyTotals().collectAsState(initial = emptyList())
    // 「今天」由 rememberToday() 驱动，跨天/长时间后台后自动刷新（原因见 RememberToday.kt）
    val today = rememberToday()
    // 用户显式点选的历史日期；null 表示「跟随今天」—— 这样停在「今天」的页面会随日期滚动，
    // 而手动翻到某一天的不会被悄悄搬走。用 rememberSaveable：旋转屏幕 / 进程被回收重建后
    // 仍停在用户选的那一天，不会被弹回今天
    var pinnedDate by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedDate = pinnedDate ?: today.toString()
    val selectedRecords by remember(selectedDate) { viewModel.recordsByDate(selectedDate) }
        .collectAsState(initial = emptyList())
    val selectedTotal by remember(selectedDate) { viewModel.dailyTotal(selectedDate) }
        .collectAsState(initial = 0)
    val selPercent = ((selectedTotal ?: 0).toFloat() / goal).coerceIn(0f, 1f)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(true) }

    // 最近 7 天数据（缺失的日期补 0）。today 是 key：跨天后窗口要跟着挪，否则仍停在旧的一周
    val weekData = remember(allTotals, today) { WaterStats.last7Days(allTotals, today) }
    // 月度达标 + 连续天数（月度按今天所在月、只计已过天数）
    val monthReached = WaterStats.monthReachedStats(allTotals, goal, YearMonth.from(today), today)
    val streak = WaterStats.computeStreak(allTotals, goal, today)

    fun deleteRecord(record: WaterRecord) {
        scope.launch {
            viewModel.deleteRecord(record)
            val result = snackbarHostState.showSnackbar(
                message = "已删除 ${DrinkType.byId(record.drinkType).label} ${record.amount} ml",
                actionLabel = "撤销",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete(record)
            }
        }
    }

    // CSV 导出：SAF CreateDocument 零权限；用户取消（uri == null）静默返回
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val count = viewModel.exportCsv(uri)
                    Toast.makeText(context, "已导出 $count 条记录", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("历史记录")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { exportLauncher.launch(exportFileName()) }) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = "导出 CSV"
                        )
                    }
                    if (selectedDate == today.toString()) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "清空今日",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        // 日历展开时，上方三块固定高度的卡片会顶破屏幕，把日历底部和记录列表裁掉。
        // 这种情况下让整页可滚动，被裁的部分就能滑到；日历收起时维持原来的布局（列表撑满剩余高度）。
        val pageScrollable = showCalendar
        val pageScrollState = rememberScrollState()
        val recordScrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 480.dp)
                .padding(padding)
                .padding(horizontal = 16.dp)
                .then(if (pageScrollable) Modifier.verticalScroll(pageScrollState) else Modifier)
                // Scaffold 只避让到导航栏边界，放在滚动之后让留白跟随内容：
                // 滑到底时详细记录卡片与手势条之间仍有间距，不会贴死被裁
                .padding(bottom = 16.dp)
        ) {
            // 三合一统计分组卡：日期切换 + 数据行 + 周图 + 月度达标 + 日历（可折叠）。
            // 日历收起时，在卡片上向下划可重新展开
            val expandDragThreshold = with(LocalDensity.current) { 48.dp.toPx() }
            var expandDrag by remember { mutableFloatStateOf(0f) }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(showCalendar) {
                        if (!showCalendar) {
                            detectVerticalDragGestures(
                                onDragStart = { expandDrag = 0f },
                                onDragCancel = { expandDrag = 0f },
                                onDragEnd = {
                                    if (expandDrag >= expandDragThreshold) showCalendar = true
                                    expandDrag = 0f
                                }
                            ) { _, dragAmount -> expandDrag += dragAmount }
                        }
                    },
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 卡头：选中日期 + 日历折叠切换
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDateDisplay(selectedDate, today),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = { showCalendar = !showCalendar }) {
                            Icon(
                                imageVector = if (showCalendar) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = if (showCalendar) "收起日历" else "展开日历",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    // 数据行：大数字 + 单位 + 单个圆环进度（同一进度不再画两遍）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${selectedTotal ?: 0}",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "/ $goal ml",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 5.dp)
                            )
                        }
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { selPercent },
                                modifier = Modifier.size(44.dp),
                                strokeWidth = 4.dp,
                                color = if (selPercent >= 1f) MaterialTheme.successColor else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            Text(
                                text = "${(selPercent * 100).toInt()}%",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    // 最近 7 天
                    WeeklyStatsSection(weekData = weekData, goal = goal, today = today)
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    Spacer(modifier = Modifier.height(10.dp))
                    // 月度达标统计
                    Text(
                        text = "本月达标 ${monthReached.first}/${monthReached.second} 天 · 连续 $streak 天",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // 日历（上滑收起，点日期切换查看日）
                    AnimatedVisibility(
                        visible = showCalendar,
                        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                            Spacer(modifier = Modifier.height(12.dp))
                            CalendarView(
                                dailyTotals = allTotals,
                                selectedDate = selectedDate,
                                goal = goal,
                                today = today,
                                onCollapse = { showCalendar = false },
                                onDateSelected = { pinnedDate = if (it == today.toString()) null else it }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 记录列表（扁平化：去卡中卡嵌套）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.WaterDrop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (selectedDate == today.toString()) "今日记录"
                    else "${formatDateDisplay(selectedDate, today)}记录",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "（左滑或长按删除）",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                if (selectedRecords.isNotEmpty()) {
                    Text(
                        text = "${selectedRecords.size} 条",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (selectedRecords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (pageScrollable) Modifier.heightIn(min = 120.dp)
                            else Modifier.weight(1f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.WaterDrop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "该日期无记录",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (pageScrollable) Modifier
                            else Modifier.weight(1f).verticalScroll(recordScrollState)
                        )
                ) {
                    selectedRecords.forEach { record ->
                        // key 必不可少：列表无 LazyColumn item key 时，删除后上移的条目
                        // 会复用同位组合位并继承「已滑开」的 dismiss 状态，
                        // 触发级联 confirm 误删下一行（实测一次滑动删掉两条）。
                        // 绑定 record.id 后上移条目拿到全新 Settled 状态。
                        key(record.id) {
                            SwipeDismissRecordItem(
                                record = record,
                                onDelete = { deleteRecord(record) },
                                onLongClick = { deleteRecord(record) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("清空今日记录") },
            text = { Text("确定要清空今日所有喝水记录吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { viewModel.clearDate(today.toString()) }
                        showDeleteConfirm = false
                    }
                ) {
                    Text("确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

/** 导出文件名：water_records_20261007.csv */
private fun exportFileName(): String =
    "water_records_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv"
