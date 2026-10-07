package com.example.waterreminder.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.waterreminder.data.DrinkType

@Composable
fun WaterAmountCard(
    amount: Int,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        label = "scale"
    )
    val haptic = LocalHapticFeedback.current

    Card(
        onClick = {
            // 高频核心操作：轻震动确认「记上了」，仅视觉的缩放脉冲在口袋里感知不到
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            pressed = true
            onClick()
        },
        modifier = modifier
            .scale(scale)
            .aspectRatio(1f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.12f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "+$amount",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = "ml",
                fontSize = 12.sp,
                color = color.copy(alpha = 0.7f)
            )
        }
    }

    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(150)
            pressed = false
        }
    }
}

/**
 * 饮料选择卡片：替代默认 FilterChip。
 * 选中态用饮料身份色（浅色底 + 1.5dp 描边 + 标签加重为身份色），与快速记录卡片的
 * 「+200」同语言；悬停态叠一层更浅的身份色（鼠标 / 触控笔等指针设备可见）。
 * 最小高度对齐「自定义水量」按钮（52dp，矮视口 46dp），触控目标远超 48dp。
 */
@Composable
internal fun DrinkTypeCard(
    drink: DrinkType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val container = animateColorAsState(
        targetValue = when {
            selected -> drink.color.copy(alpha = 0.12f)
            hovered -> drink.color.copy(alpha = 0.06f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
        animationSpec = tween(150),
        label = "drinkContainer"
    )
    val border = animateColorAsState(
        targetValue = when {
            selected -> drink.color.copy(alpha = 0.55f)
            hovered -> drink.color.copy(alpha = 0.35f)
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "drinkBorder"
    )
    // 选中时标签加重为身份色 —— 深浅两套主题下身份色对背景都有足够对比
    val labelColor = if (selected) drink.color else MaterialTheme.colorScheme.onSurface

    Surface(
        selected = selected,
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = container.value,
        border = BorderStroke(1.5.dp, border.value)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (isCompactViewport()) 46.dp else 52.dp)
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(drink.color.copy(alpha = if (selected) 0.2f else 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = drink.icon,
                    contentDescription = null,
                    tint = drink.color,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = drink.label,
                fontSize = 14.sp,
                maxLines = 1,
                softWrap = false,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = labelColor
            )
        }
    }
}
