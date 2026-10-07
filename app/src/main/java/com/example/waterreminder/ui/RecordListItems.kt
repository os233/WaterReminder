package com.example.waterreminder.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.waterreminder.data.DrinkType
import com.example.waterreminder.data.WaterRecord
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 左滑删除：比藏在长按里的入口好发现；删除动作复用 [HistoryScreen.deleteRecord] 的
 * 「删除 + Snackbar 撤销」链路，长按入口保留。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeDismissRecordItem(
    record: WaterRecord,
    onDelete: () -> Unit,
    onLongClick: () -> Unit
) {
    // confirmValueChange 闭包在 remember 里只捕获一次，列表复用组合位时会拿到旧 record；
    // 经 rememberUpdatedState 转发保证每次都调用最新的 onDelete
    val currentOnDelete by rememberUpdatedState(onDelete)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            // 返回 true 让手势完整落位；列表随后随 Flow 移除该项，
            // 撤销重插的是新的组合项，不会残留已滑动状态
            if (value == SwipeToDismissBoxValue.EndToStart) {
                currentOnDelete()
                true
            } else {
                false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            // 记录卡片是半透明色，若红色背景常驻绘制会在未滑动时透过卡片显形；
            // 只在滑动进行中/已滑开（targetValue 离开 Settled）时才画红底
            val revealed = dismissState.targetValue != SwipeToDismissBoxValue.Settled
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (revealed) MaterialTheme.colorScheme.errorContainer
                        else Color.Transparent
                    ),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (revealed) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .padding(end = 24.dp)
                            .size(24.dp)
                    )
                }
            }
        }
    ) {
        HistoryRecordItem(record = record, onLongClick = onLongClick)
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

    // 扁平记录行：不再嵌套卡片，挂在历史页记录分组内
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onLongClick
            )
            .padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(drink.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = drink.icon,
                    contentDescription = null,
                    tint = drink.color,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "${record.amount} ml",
                    fontSize = 15.sp,
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
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = "+${record.amount}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 历史页日期列展示：今天 / 昨天 / 「N月N日 周X」；解析失败（异常数据）原样回显，不崩溃。 */
fun formatDateDisplay(dateStr: String, today: LocalDate): String {
    return try {
        val date = LocalDate.parse(dateStr)
        when {
            date == today -> "今天"
            date == today.minusDays(1) -> "昨天"
            else -> "${date.monthValue}月${date.dayOfMonth}日 ${listOf("周日","周一","周二","周三","周四","周五","周六")[date.dayOfWeek.value % 7]}"
        }
    } catch (_: DateTimeParseException) {
        dateStr
    }
}
