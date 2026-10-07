package com.example.waterreminder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.waterreminder.data.DailyTotal
import com.example.waterreminder.ui.theme.successColor
import java.time.LocalDate
import java.time.YearMonth

/**
 * 日历网格几何（纯函数）：返回 [首格前的偏移, 行数]。网格以周日开头 ——
 * ISO 的 dayOfWeek 是周一=1…周日=7，`% 7` 把周日折成 0。
 */
internal fun calendarGrid(month: YearMonth): Pair<Int, Int> {
    val firstDayWeekday = month.atDay(1).dayOfWeek.value % 7
    val totalCells = firstDayWeekday + month.lengthOfMonth()
    return firstDayWeekday to (totalCells + 6) / 7
}

/**
 * 月度达标统计（纯函数）：返回 [本月达标天数, 统计天数]。
 * 当前月只统计到今天为止（未来天数不计入分母）。
 */
internal fun monthReachedStats(
    totals: List<DailyTotal>,
    goal: Int,
    month: YearMonth,
    today: LocalDate
): Pair<Int, Int> {
    val map = totals.associate { it.recordDate to it.total }
    val countedDays = if (month == YearMonth.from(today)) today.dayOfMonth else month.lengthOfMonth()
    var reached = 0
    for (day in 1..countedDays) {
        if ((map[month.atDay(day).toString()] ?: 0) >= goal) reached++
    }
    return reached to countedDays
}

@Composable
fun CalendarView(
    dailyTotals: List<DailyTotal>,
    selectedDate: String,
    goal: Int,
    today: LocalDate,
    onCollapse: () -> Unit,
    onDateSelected: (String) -> Unit
) {
    val todayStr = today.toString()
    // 默认停在「今天」所在的月份；用户手动翻过月份后就不再被跨天重置，
    // 否则正在翻历史月份的用户会被一次日期刷新弹回当月
    var navigated by remember { mutableStateOf(false) }
    var currentMonth by remember { mutableStateOf(YearMonth.from(today)) }
    LaunchedEffect(today) {
        if (!navigated) currentMonth = YearMonth.from(today)
    }
    val totalMap = remember(dailyTotals) { dailyTotals.associate { it.recordDate to it.total } }
    val daysInMonth = currentMonth.lengthOfMonth()
    val (firstDayWeekday, rows) = calendarGrid(currentMonth)

    // 上滑收起：累计拖动位移超过阈值才触发，避免误触
    val dragThreshold = with(LocalDensity.current) { 48.dp.toPx() }
    var accumulatedDrag by remember { mutableFloatStateOf(0f) }

    // 日历不再自带卡片底：挂在历史页统计分组卡内，由外层提供表面
    Column(
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
            }
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
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
                    onClick = { navigated = true; currentMonth = currentMonth.minusMonths(1) },
                    modifier = Modifier.size(48.dp)
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
                    onClick = { navigated = true; currentMonth = currentMonth.plusMonths(1) },
                    modifier = Modifier.size(48.dp)
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
                                    isToday = dateStr == todayStr,
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
                            .background(MaterialTheme.successColor)
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
                                if (isGoalReached) MaterialTheme.successColor else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            } else {
                                if (isGoalReached) MaterialTheme.successColor else MaterialTheme.colorScheme.tertiary
                            }
                        )
                )
            }
        }
    }
}
