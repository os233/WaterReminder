package com.example.waterreminder.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.waterreminder.data.DailyTotal
import com.example.waterreminder.data.DrinkType
import com.example.waterreminder.data.UserPrefs
import com.example.waterreminder.data.WaterRecord
import com.example.waterreminder.data.WaterRecordDao
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

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
    var selectedDate by remember { mutableStateOf(LocalDate.now().toString()) }
    val selectedRecords by dao.getRecordsByDate(selectedDate).collectAsState(initial = emptyList())
    val selectedTotal by dao.getDailyTotal(selectedDate).collectAsState(initial = 0)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(true) }

    // 最近 7 天数据（缺失的日期补 0）
    val weekData = remember(allTotals) {
        val map = allTotals.associate { it.recordDate to it.total }
        (6 downTo 0).map { offset ->
            val d = LocalDate.now().minusDays(offset.toLong())
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
                    if (selectedDate == LocalDate.now().toString()) {
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
                    onToggleCalendar = { showCalendar = !showCalendar },
                    showCalendar = showCalendar
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 最近 7 天统计图
                WeeklyStatsCard(weekData = weekData, goal = goal)
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
                        onCollapse = { showCalendar = false },
                        onDateSelected = { selectedDate = it }
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
                                text = "（长按删除单条）",
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
                                HistoryRecordItem(
                                    record = record,
                                    onLongClick = { deleteRecord(record) }
                                )
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
                        scope.launch { dao.deleteRecordsByDate(LocalDate.now().toString()) }
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

@Composable
fun DateSummaryCard(
    date: String,
    total: Int,
    recordCount: Int,
    goal: Int,
    onToggleCalendar: () -> Unit,
    showCalendar: Boolean
) {
    val percent = (total.toFloat() / goal).coerceIn(0f, 1f)
    val isToday = date == LocalDate.now().toString()
    // 矮视口（MuMu/小屏手机）下收紧汇总卡，给日历和周统计多留可见空间
    val compact = isCompactViewport()

    Card(
        onClick = onToggleCalendar,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(if (compact) 14.dp else 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = formatDateDisplay(date),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (isToday) {
                        Text(
                            text = "今天",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                IconButton(onClick = onToggleCalendar) {
                    Icon(
                        imageVector = if (showCalendar) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = if (showCalendar) "收起日历" else "展开日历",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (compact) 10.dp else 16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "$total",
                        fontSize = if (compact) 28.sp else 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "/ $goal ml",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Box(
                        modifier = Modifier
                            .size(if (compact) 46.dp else 56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { percent },
                            modifier = Modifier.size(if (compact) 38.dp else 48.dp),
                            strokeWidth = 4.dp,
                            color = if (percent >= 1f) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Text(
                            text = "${(percent * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (recordCount > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { percent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (percent >= 1f) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
fun WeeklyStatsCard(weekData: List<Pair<LocalDate, Int>>, goal: Int) {
    val reachedCount = weekData.count { it.second >= goal }
    val validDays = weekData.count { it.second > 0 }
    val avg = if (validDays > 0) weekData.sumOf { it.second } / validDays else 0
    val reachedColor = Color(0xFF4CAF50)
    val barColor = MaterialTheme.colorScheme.primary
    val goalLineColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "最近 7 天",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "达标 $reachedCount/7 天",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            ) {
                val maxVal = maxOf(goal.toFloat(), (weekData.maxOfOrNull { it.second } ?: 0).toFloat()) * 1.15f
                val barWidth = size.width / weekData.size
                weekData.forEachIndexed { index, (_, total) ->
                    if (total > 0) {
                        val barHeight = (size.height * (total / maxVal)).coerceAtLeast(4.dp.toPx())
                        drawRoundRect(
                            color = if (total >= goal) reachedColor else barColor,
                            topLeft = Offset(index * barWidth + barWidth * 0.25f, size.height - barHeight),
                            size = Size(barWidth * 0.5f, barHeight),
                            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                        )
                    }
                }
                // 目标参考线（虚线）
                val goalY = size.height * (goal / maxVal)
                drawLine(
                    color = goalLineColor,
                    start = Offset(0f, goalY),
                    end = Offset(size.width, goalY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                weekData.forEach { (date, _) ->
                    val isToday = date == LocalDate.now()
                    Text(
                        text = "日一二三四五六"[date.dayOfWeek.value % 7].toString(),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) barColor else labelColor
                    )
                }
            }

            if (avg > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "有记录日均 $avg ml",
                    fontSize = 12.sp,
                    color = labelColor
                )
            }
        }
    }
}

@Composable
fun CalendarView(
    dailyTotals: List<DailyTotal>,
    selectedDate: String,
    goal: Int,
    onCollapse: () -> Unit,
    onDateSelected: (String) -> Unit
) {
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    val today = LocalDate.now().toString()
    val totalMap = remember(dailyTotals) { dailyTotals.associate { it.recordDate to it.total } }
    val firstDayOfMonth = currentMonth.atDay(1)
    val daysInMonth = currentMonth.lengthOfMonth()
    val firstDayWeekday = firstDayOfMonth.dayOfWeek.value % 7
    val totalCells = firstDayWeekday + daysInMonth
    val rows = (totalCells + 6) / 7

    // 上滑收起：累计拖动位移超过阈值才触发，避免误触
    val dragThreshold = with(LocalDensity.current) { 48.dp.toPx() }
    var accumulatedDrag by remember { mutableFloatStateOf(0f) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { accumulatedDrag = 0f },
                    onDragCancel = { accumulatedDrag = 0f },
                    onDragEnd = {
                        if (accumulatedDrag <= -dragThreshold) onCollapse()
                        accumulatedDrag = 0f
                    }
                ) { _, dragAmount -> accumulatedDrag += dragAmount }
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 拖拽手柄
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 月份导航
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = { currentMonth = currentMonth.minusMonths(1) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text(
                        "‹",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${currentMonth.year}年",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${currentMonth.monthValue}月",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                FilledTonalIconButton(
                    onClick = { currentMonth = currentMonth.plusMonths(1) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text(
                        "›",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 星期标题
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("日", "一", "二", "三", "四", "五", "六").forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 日期网格
            Column {
                for (row in 0 until rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (col in 0..6) {
                            val cellIndex = row * 7 + col
                            val dayNumber = cellIndex - firstDayWeekday + 1

                            if (cellIndex < firstDayWeekday || dayNumber > daysInMonth) {
                                Box(modifier = Modifier.size(40.dp))
                            } else {
                                val dateStr = currentMonth.atDay(dayNumber).toString()
                                val total = totalMap[dateStr] ?: 0
                                CalendarDayCell(
                                    day = dayNumber,
                                    total = total,
                                    goal = goal,
                                    isSelected = dateStr == selectedDate,
                                    isToday = dateStr == today,
                                    onClick = { onDateSelected(dateStr) }
                                )
                            }
                        }
                    }
                    if (row < rows - 1) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }

            // 图例
            if (totalMap.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "达标",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "有记录",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "选中",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 上滑收起提示（也可点击）
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onCollapse() }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDropUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "上滑收起日历",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CalendarDayCell(
    day: Int,
    total: Int,
    goal: Int,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val hasRecord = total > 0
    val isGoalReached = total >= goal

    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }

    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bgColor)
            .border(
                width = if (isToday && !isSelected) 2.dp else 0.dp,
                color = if (isToday && !isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = day.toString(),
                fontSize = 14.sp,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
            if (hasRecord) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .width(if (isGoalReached) 20.dp else 14.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (isSelected) {
                                if (isGoalReached) Color(0xFF81C784) else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            } else {
                                if (isGoalReached) Color(0xFF4CAF50) else MaterialTheme.colorScheme.tertiary
                            }
                        )
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryRecordItem(
    record: WaterRecord,
    onLongClick: () -> Unit
) {
    val drink = DrinkType.byId(record.drinkType)
    val effective = (record.amount * record.hydration).toInt()
    val time = record.timestamp.format(DateTimeFormatter.ofPattern("HH:mm"))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongClick
                )
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(drink.color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = drink.icon,
                        contentDescription = null,
                        tint = drink.color,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "${record.amount} ml",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = buildString {
                            append(drink.label)
                            append(" · ")
                            append(time)
                            if (record.hydration < 1.0) {
                                append(" · 折合 $effective ml")
                            }
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Text(
                    text = "+${record.amount}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

fun formatDateDisplay(dateStr: String): String {
    return try {
        val date = LocalDate.parse(dateStr)
        val today = LocalDate.now()
        when {
            date == today -> "今天"
            date == today.minusDays(1) -> "昨天"
            else -> "${date.monthValue}月${date.dayOfMonth}日 ${listOf("周日","周一","周二","周三","周四","周五","周六")[date.dayOfWeek.value % 7]}"
        }
    } catch (e: Exception) {
        dateStr
    }
}
