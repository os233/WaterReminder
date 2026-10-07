package com.example.waterreminder.ui

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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
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
import com.example.waterreminder.data.DrinkType
import com.example.waterreminder.data.UserPrefs
import com.example.waterreminder.data.WaterRecord
import com.example.waterreminder.data.WaterRecordDao
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    dao: WaterRecordDao,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val goal = remember { UserPrefs.getDailyGoal(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val allTotals by dao.getAllDailyTotals().collectAsState(initial = emptyList())
    // 「今天」由 rememberToday() 驱动，跨天/长时间后台后自动刷新（原因见 RememberToday.kt）
    val today = rememberToday()
    // 用户显式点选的历史日期；null 表示「跟随今天」—— 这样停在「今天」的页面会随日期滚动，
    // 而手动翻到某一天的不会被悄悄搬走。用 rememberSaveable：旋转屏幕 / 进程被回收重建后
    // 仍停在用户选的那一天，不会被弹回今天
    var pinnedDate by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedDate = pinnedDate ?: today.toString()
    val selectedRecords by dao.getRecordsByDate(selectedDate).collectAsState(initial = emptyList())
    val selectedTotal by dao.getDailyTotal(selectedDate).collectAsState(initial = 0)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(true) }

    // 最近 7 天数据（缺失的日期补 0）。today 是 key：跨天后窗口要跟着挪，否则仍停在旧的一周
    val weekData = remember(allTotals, today) {
        val map = allTotals.associate { it.recordDate to it.total }
        (6 downTo 0).map { offset ->
            val d = today.minusDays(offset.toLong())
            d to (map[d.toString()] ?: 0)
        }
    }

    fun deleteRecord(record: WaterRecord) {
        scope.launch {
            dao.delete(record)
            val result = snackbarHostState.showSnackbar(
                message = "已删除 ${DrinkType.byId(record.drinkType).label} ${record.amount} ml",
                actionLabel = "撤销",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                dao.insert(record.copy(id = 0))
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
                .padding(padding)
                .padding(horizontal = 16.dp)
                .then(if (pageScrollable) Modifier.verticalScroll(pageScrollState) else Modifier)
                // Scaffold 只避让到导航栏边界，放在滚动之后让留白跟随内容：
                // 滑到底时详细记录卡片与手势条之间仍有间距，不会贴死被裁
                .padding(bottom = 16.dp)
        ) {
            // 日历收起时，在统计卡区域向下划可重新展开
            val expandDragThreshold = with(LocalDensity.current) { 48.dp.toPx() }
            var expandDrag by remember { mutableFloatStateOf(0f) }

            Column(
                modifier = Modifier.pointerInput(showCalendar) {
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
                }
            ) {
                // 日期统计卡片
                DateSummaryCard(
                    date = selectedDate,
                    total = selectedTotal ?: 0,
                    recordCount = selectedRecords.size,
                    goal = goal,
                    today = today,
                    onToggleCalendar = { showCalendar = !showCalendar },
                    showCalendar = showCalendar
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 最近 7 天统计图
                WeeklyStatsCard(weekData = weekData, goal = goal, today = today)
            }

            // 日历视图（整块可折叠：上滑收起）
            AnimatedVisibility(
                visible = showCalendar,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
            ) {
                Column {
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

            Spacer(modifier = Modifier.height(16.dp))

            // 详细记录：标题与列表同属一张卡片，避免中间夹一道裸露背景的接缝
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        // 整页可滚动时不能再用 weight（父级高度无界），改给一个最小高度
                        if (pageScrollable) Modifier.heightIn(min = 220.dp)
                        else Modifier.weight(1f)
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (pageScrollable) Modifier else Modifier.fillMaxSize())
                ) {
                    // 标题行
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WaterDrop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "详细记录",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "（左滑或长按删除单条）",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                        if (selectedRecords.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${selectedRecords.size} 条",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // 记录列表
                    if (selectedRecords.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (pageScrollable) Modifier.heightIn(min = 140.dp)
                                    else Modifier.weight(1f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.WaterDrop,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "该日期无记录",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        // 整页可滚动时列表跟着页面滚，否则在卡片内部滚（等价于原来的 LazyColumn 行为）
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (pageScrollable) Modifier
                                    else Modifier.weight(1f).verticalScroll(recordScrollState)
                                )
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        scope.launch { dao.deleteRecordsByDate(today.toString()) }
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
