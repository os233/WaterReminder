package com.example.waterreminder.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.waterreminder.data.DrinkType

/**
 * 饮料圆形选择器：一枚枚饮料色圆点，选中实色 + 白对勾，未选浅色底 + 饮料图标。
 * 它是轻量的次级选择器（点杯型按钮即记录，这里只决定下一杯记什么），
 * 视觉重量刻意低于杯型按钮，避免主页变成「按钮墙」。
 */
@Composable
fun DrinkDotSelector(
    drinks: List<DrinkType>,
    selected: DrinkType,
    onSelect: (DrinkType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        drinks.forEach { drink ->
            val isSelected = drink == selected
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(drink) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = "${drink.label}${if (isSelected) "，已选择" else ""}"
                    }
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            animateColorAsState(
                                targetValue = if (isSelected) drink.color else drink.color.copy(alpha = 0.15f),
                                animationSpec = tween(150),
                                label = "dotBg"
                            ).value
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Icon(
                            imageVector = drink.icon,
                            contentDescription = null,
                            tint = drink.color,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = drink.label,
                    fontSize = 11.sp,
                    maxLines = 1,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) drink.color else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 杯型快捷记录按钮：固定尺寸（不再 1:1 占满半屏宽），点按即按当前饮料记录一杯。
 * 高频核心操作：轻震动确认「记上了」，仅视觉的缩放脉冲在口袋里感知不到。
 */
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
        targetValue = if (pressed) 0.94f else 1f,
        label = "scale"
    )
    val haptic = LocalHapticFeedback.current

    Card(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            pressed = true
            onClick()
        },
        modifier = modifier
            .height(112.dp)
            .scale(scale),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.10f)
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
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "+$amount",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = "ml",
                fontSize = 11.sp,
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
