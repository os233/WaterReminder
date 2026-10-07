package com.example.waterreminder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.waterreminder.ui.theme.successColor
import java.time.LocalDate

@Composable
fun DateSummaryCard(
    date: String,
    total: Int,
    recordCount: Int,
    goal: Int,
    today: LocalDate,
    onToggleCalendar: () -> Unit,
    showCalendar: Boolean
) {
    val percent = (total.toFloat() / goal).coerceIn(0f, 1f)
    val isToday = date == today.toString()
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
                        text = formatDateDisplay(date, today),
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
                            color = if (percent >= 1f) MaterialTheme.successColor else MaterialTheme.colorScheme.primary,
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
                    color = if (percent >= 1f) MaterialTheme.successColor else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
fun WeeklyStatsCard(weekData: List<Pair<LocalDate, Int>>, goal: Int, today: LocalDate) {
    val reachedCount = weekData.count { it.second >= goal }
    val validDays = weekData.count { it.second > 0 }
    val avg = if (validDays > 0) weekData.sumOf { it.second } / validDays else 0
    val reachedColor = MaterialTheme.successColor
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
                    // Canvas 对 TalkBack 是不透明的；给一句概述代替逐日播报
                    .semantics {
                        contentDescription =
                            "最近 7 天饮水柱状图：达标 $reachedCount 天" +
                                if (avg > 0) "，有记录日均 $avg 毫升" else ""
                    }
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
                // 目标参考线（虚线）。柱子自底向上生长（topLeft.y = H - barHeight），
                // 线的高度同样要从底部量：H·(1 − goal/maxVal)。写成 H·(goal/maxVal)
                // 是它的上下镜像，只有 goal 恰为 maxVal 的一半时才碰巧重合。
                val goalY = size.height * (1 - goal / maxVal)
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
                    val isToday = date == today
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
