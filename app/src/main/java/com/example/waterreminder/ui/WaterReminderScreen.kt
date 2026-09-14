package com.example.waterreminder.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.waterreminder.data.DailyTotal
import com.example.waterreminder.data.DrinkType
import com.example.waterreminder.data.UserPrefs
import com.example.waterreminder.data.WaterRecord
import com.example.waterreminder.data.WaterRecordDao
import com.example.waterreminder.notification.AlarmManagerHelper
import com.example.waterreminder.notification.KeepAliveService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 矮视口判断：MuMu 模拟器（1080×1920 @ 480DPI）折算后只有 360×640dp，
 * 小屏手机也普遍在这个量级。此时固定的大尺寸主视觉会占满一屏，
 * 需要整体收紧；常规手机（高度 ≥ 700dp）维持原设计。
 */
@Composable
internal fun isCompactViewport(): Boolean =
    LocalConfiguration.current.screenHeightDp < 700

@Composable
fun WaterReminderScreen(
    dao: WaterRecordDao,
    onHistoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var goal by remember { mutableIntStateOf(UserPrefs.getDailyGoal(context)) }
    var showGoalDialog by remember { mutableStateOf(false) }
    var selectedDrink by remember { mutableStateOf(DrinkType.WATER) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var customAmount by remember { mutableStateOf("") }

    // 跟踪"今天"是哪天：跨过午夜后自动刷新，避免界面停留在昨天的数据
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalDateTime.now()
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
            delay(Duration.between(now, nextMidnight).toMillis() + 1_000)
            today = LocalDate.now()
        }
    }

    val todayTotal by remember(today) {
        dao.getTodayTotal(today.toString())
    }.collectAsState(initial = 0)
    val allTotals by dao.getAllDailyTotals().collectAsState(initial = emptyList())

    val percent = (todayTotal?.toFloat() ?: 0f) / goal
    val animatedProgress by animateFloatAsState(
        targetValue = percent.coerceIn(0f, 1f),
        label = "progress"
    )

    val streak = remember(allTotals, goal) { computeStreak(allTotals, goal) }

    // 首次达成今日目标时播放一次庆祝动画（本次会话内不重复）
    val goalReached = (todayTotal ?: 0) >= goal
    var celebrate by remember { mutableStateOf(false) }
    var celebrated by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(goalReached) {
        if (goalReached && !celebrated) {
            celebrated = true
            celebrate = true
            delay(2600)
            celebrate = false
        }
    }

    val currentDate = remember(today) {
        today.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE"))
    }

    val compact = isCompactViewport()
    val heroCircle = if (compact) 160.dp else 200.dp

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // edge-to-edge：避开状态栏、手势条与刘海区域
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (compact) 12.dp else 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "今日饮水",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = currentDate,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (streak > 0) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocalFireDepartment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "连续 $streak 天",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
                FilledTonalIconButton(
                    onClick = onHistoryClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "历史记录",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Progress Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (compact) 16.dp else 24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(if (compact) 16.dp else 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.size(heroCircle),
                        contentAlignment = Alignment.Center
                    ) {
                        // 外圈装饰
                        Box(
                            modifier = Modifier
                                .size(heroCircle)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        )
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.size(if (compact) 134.dp else 170.dp),
                            strokeWidth = if (compact) 10.dp else 14.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WaterDrop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(if (compact) 20.dp else 28.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${todayTotal ?: 0}",
                                fontSize = if (compact) 28.sp else 40.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            // 点击可修改每日目标
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { showGoalDialog = true }
                            ) {
                                Text(
                                    text = "/ $goal ml",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "修改目标",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${(percent * 100).toInt()}%",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                        // 达标庆祝
                        androidx.compose.animation.AnimatedVisibility(
                            visible = celebrate,
                            enter = scaleIn(initialScale = 0.4f) + fadeIn(),
                            exit = scaleOut(targetScale = 1.15f) + fadeOut(),
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.primary,
                                shadowElevation = 6.dp
                            ) {
                                Text(
                                    text = "🎉 目标达成！",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(if (compact) 10.dp else 16.dp))
                    val statusText = when {
                        (todayTotal ?: 0) >= goal -> "🎉 目标达成！太棒了！"
                        (todayTotal ?: 0) >= goal / 2 -> "💪 已经完成一半了！"
                        (todayTotal ?: 0) > 0 -> "👍 继续加油！"
                        else -> "💧 开始喝水吧！"
                    }
                    val statusBg = if ((todayTotal ?: 0) >= goal)
                        MaterialTheme.colorScheme.tertiaryContainer
                    else
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    val statusColor = if ((todayTotal ?: 0) >= goal)
                        MaterialTheme.colorScheme.onTertiaryContainer
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = statusBg
                    ) {
                        Text(
                            text = statusText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }

        // 饮料类型选择
        Text(
            text = "选择饮料",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 一行最多 3 个：5 个挤一行时每个只剩约 60dp，图标加"气泡水"会被迫换行
            DrinkType.entries.chunked(3).forEach { rowDrinks ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowDrinks.forEach { drink ->
                        FilterChip(
                            selected = selectedDrink == drink,
                            onClick = { selectedDrink = drink },
                            label = {
                                Text(
                                    text = drink.label,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = drink.icon,
                                    contentDescription = null,
                                    tint = drink.color,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // 补空位，让最后一行的 chip 与上一行列宽对齐
                    repeat(3 - rowDrinks.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // 快速记录按钮 - 大卡片式
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            WaterAmountCard(
                amount = 200,
                icon = selectedDrink.icon,
                color = selectedDrink.color,
                onClick = { recordDrink(scope, dao, selectedDrink, 200) },
                modifier = Modifier.weight(1f)
            )
            WaterAmountCard(
                amount = 350,
                icon = selectedDrink.icon,
                color = selectedDrink.color,
                onClick = { recordDrink(scope, dao, selectedDrink, 350) },
                modifier = Modifier.weight(1f)
            )
            WaterAmountCard(
                amount = 500,
                icon = selectedDrink.icon,
                color = selectedDrink.color,
                onClick = { recordDrink(scope, dao, selectedDrink, 500) },
                modifier = Modifier.weight(1f)
            )
        }

        // 自定义按钮
        OutlinedButton(
            onClick = { showCustomDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            ),
            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                brush = Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                )
            )
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("自定义水量", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }

        // 提醒区域
        ReminderSection()
    }

    // 自定义水量弹窗
    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            icon = {
                Icon(
                    imageVector = selectedDrink.icon,
                    contentDescription = null,
                    tint = selectedDrink.color,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("输入${selectedDrink.label}量") },
            text = {
                OutlinedTextField(
                    value = customAmount,
                    onValueChange = { customAmount = it.filter { c -> c.isDigit() } },
                    label = { Text("${selectedDrink.label}量 (ml)") },
                    supportingText = {
                        if (selectedDrink.hydration < 1.0) {
                            Text("按水合系数 ${(selectedDrink.hydration * 100).toInt()}% 折算计入总量")
                        }
                    },
                    singleLine = true,
                    leadingIcon = {
                        Icon(selectedDrink.icon, contentDescription = null)
                    },
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    customAmount.toIntOrNull()?.let {
                        if (it > 0 && it <= 5000) {
                            recordDrink(scope, dao, selectedDrink, it)
                        }
                    }
                    showCustomDialog = false
                    customAmount = ""
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) {
                    Text("取消")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // 每日目标设置弹窗
    if (showGoalDialog) {
        var sliderValue by remember { mutableFloatStateOf(goal.toFloat()) }
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("每日目标") },
            text = {
                Column {
                    Text(
                        text = "${sliderValue.toInt()} ml",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = (it / 100).toInt() * 100f },
                        valueRange = 1000f..4000f
                    )
                    Text(
                        text = "范围 1000 – 4000 ml，步长 100",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val newGoal = sliderValue.toInt()
                    goal = newGoal
                    UserPrefs.setDailyGoal(context, newGoal)
                    showGoalDialog = false
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoalDialog = false }) {
                    Text("取消")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

private fun recordDrink(
    scope: kotlinx.coroutines.CoroutineScope,
    dao: WaterRecordDao,
    drink: DrinkType,
    amount: Int
) {
    scope.launch {
        dao.insert(
            WaterRecord(
                amount = amount,
                drinkType = drink.id,
                hydration = drink.hydration
            )
        )
    }
}

/** 连续达标天数：今天未达标则从昨天起算，不因"还没喝"而清零 */
private fun computeStreak(totals: List<DailyTotal>, goal: Int): Int {
    if (totals.isEmpty()) return 0
    val map = totals.associate { it.recordDate to it.total }
    var date = LocalDate.now()
    if ((map[date.toString()] ?: 0) < goal) {
        date = date.minusDays(1)
    }
    var streak = 0
    while ((map[date.toString()] ?: 0) >= goal) {
        streak++
        date = date.minusDays(1)
    }
    return streak
}

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

    Card(
        onClick = {
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

@Composable
fun ReminderSection() {
    val context = LocalContext.current
    val helper = remember { AlarmManagerHelper(context) }
    var selectedInterval by remember { mutableIntStateOf(helper.getSavedInterval()) }
    var showDialog by remember { mutableStateOf(false) }
    var dndEnabled by remember { mutableStateOf(helper.isDndEnabled()) }
    var dndStart by remember { mutableIntStateOf(helper.getDndStartHour()) }
    var dndEnd by remember { mutableIntStateOf(helper.getDndEndHour()) }

    LaunchedEffect(Unit) {
        selectedInterval = helper.getSavedInterval()
    }

    val statusText = if (selectedInterval == 0) {
        "提醒已关闭"
    } else {
        "每${selectedInterval}小时提醒一次"
    }
    val dndText = if (dndEnabled) " · ${"%02d".format(dndStart)}:00–${"%02d".format(dndEnd)}:00 免打扰" else ""

    Card(
        onClick = { showDialog = true },
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "定时提醒",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$statusText$dndText",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            FilledTonalIconButton(
                onClick = { showDialog = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置提醒",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    // 提醒设置弹窗
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("设置提醒间隔") },
            text = {
                Column {
                    Text("选择每隔多久提醒一次：", modifier = Modifier.padding(bottom = 12.dp))
                    listOf(
                        0 to ("🔕 关闭提醒" to MaterialTheme.colorScheme.error),
                        1 to ("1️⃣ 每小时提醒" to MaterialTheme.colorScheme.primary),
                        2 to ("2️⃣ 每2小时提醒" to MaterialTheme.colorScheme.primary),
                        3 to ("3️⃣ 每3小时提醒" to MaterialTheme.colorScheme.primary)
                    ).forEach { (hours, pair) ->
                        val (label, color) = pair
                        val isSelected = selectedInterval == hours
                        Card(
                            onClick = {
                                selectedInterval = hours
                                setReminder(context, hours)
                                showDialog = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    color.copy(alpha = 0.12f)
                                else
                                    MaterialTheme.colorScheme.surface
                            ),
                            border = if (isSelected) {
                                androidx.compose.foundation.BorderStroke(
                                    1.5.dp,
                                    color.copy(alpha = 0.5f)
                                )
                            } else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) color else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = color,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // 夜间免打扰
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "夜间免打扰",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "免打扰时段内的提醒只顺延不弹通知",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = dndEnabled,
                            onCheckedChange = {
                                dndEnabled = it
                                helper.setDndSettings(it, dndStart, dndEnd)
                            }
                        )
                    }
                    if (dndEnabled) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            HourPicker("开始", dndStart) { h ->
                                dndStart = h
                                helper.setDndSettings(dndEnabled, h, dndEnd)
                            }
                            HourPicker("结束", dndEnd) { h ->
                                dndEnd = h
                                helper.setDndSettings(dndEnabled, dndStart, h)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("关闭")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun HourPicker(label: String, hour: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.width(8.dp))
        Box {
            FilledTonalButton(
                onClick = { open = true },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Text("%02d:00".format(hour))
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                modifier = Modifier.heightIn(max = 280.dp)
            ) {
                (0..23).forEach { h ->
                    DropdownMenuItem(
                        text = { Text("%02d:00".format(h)) },
                        onClick = {
                            onPick(h)
                            open = false
                        }
                    )
                }
            }
        }
    }
}

private fun setReminder(context: Context, hours: Int) {
    val helper = AlarmManagerHelper(context)
    if (hours == 0) {
        helper.cancelAlarm()
        context.stopService(Intent(context, KeepAliveService::class.java))
    } else {
        helper.setRepeatingAlarm(hours)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(Intent(context, KeepAliveService::class.java))
        } else {
            context.startService(Intent(context, KeepAliveService::class.java))
        }
    }
}
