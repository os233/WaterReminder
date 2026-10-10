package com.example.waterreminder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.waterreminder.data.WaterStats
import com.example.waterreminder.ui.theme.successColor
import java.time.LocalDate

/**
 * 最近 7 天统计：柱状图 + 目标参考线 + 星期标签 + 达标/日均小字。
 * 不再自带卡片底——挂在历史页统计分组卡内，由外层提供表面。
 */
@Composable
fun WeeklyStatsSection(weekData: List<Pair<LocalDate, Int>>, goal: Int, today: LocalDate) {
    val summary = remember(weekData, goal) { WaterStats.weeklySummary(weekData, goal) }
    val avg = summary.average
    val reachedColor = MaterialTheme.successColor
    val barColor = MaterialTheme.colorScheme.primary
    val goalLineColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column {
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
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "最近 7 天",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "达标 ${summary.reachedDays}/7 天",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                // Canvas 对 TalkBack 是不透明的；给一句概述代替逐日播报
                .semantics {
                    contentDescription =
                        "最近 7 天饮水柱状图：达标 ${summary.reachedDays} 天" +
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "有记录日均 $avg ml",
                fontSize = 11.sp,
                color = labelColor
            )
        }
    }
}
