package com.example.waterreminder.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.waterreminder.data.WaterRecordDao
import com.example.waterreminder.notification.AlarmManagerHelper
import com.example.waterreminder.notification.KeepAliveService
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun WaterReminderScreen(
    dao: WaterRecordDao,
    onHistoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val GOAL = 2000

    val todayTotal by dao.getTodayTotal().collectAsState(initial = 0)

    val percent = (todayTotal?.toFloat() ?: 0f) / GOAL
    val animatedProgress by animateFloatAsState(
        targetValue = percent.coerceIn(0f, 1f),
        label = "progress"
    )

    var showCustomDialog by remember { mutableStateOf(false) }
    var customAmount by remember { mutableStateOf("") }

    val currentDate = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE"))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
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
            // 历史按钮 - 圆形图标按钮
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

        // Progress Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
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
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.size(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // 外圈装饰
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        )
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.size(170.dp),
                            strokeWidth = 14.dp,
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
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${todayTotal ?: 0}",
                                fontSize = 40.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "/ $GOAL ml",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    val statusText = when {
                        (todayTotal ?: 0) >= GOAL -> "🎉 目标达成！太棒了！"
                        (todayTotal ?: 0) >= GOAL / 2 -> "💪 已经完成一半了！"
                        (todayTotal ?: 0) > 0 -> "👍 继续加油！"
                        else -> "💧 开始喝水吧！"
                    }
                    val statusBg = if ((todayTotal ?: 0) >= GOAL)
                        MaterialTheme.colorScheme.tertiaryContainer
                    else
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    val statusColor = if ((todayTotal ?: 0) >= GOAL)
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

        // 快速记录区域标题
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.LocalDrink,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "快速记录",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
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
                icon = Icons.Outlined.LocalDrink,
                color = Color(0xFF4FC3F7),
                onClick = { scope.launch { dao.insert(com.example.waterreminder.data.WaterRecord(amount = 200)) } },
                modifier = Modifier.weight(1f)
            )
            WaterAmountCard(
                amount = 350,
                icon = Icons.Outlined.LocalDrink,
                color = Color(0xFF29B6F6),
                onClick = { scope.launch { dao.insert(com.example.waterreminder.data.WaterRecord(amount = 350)) } },
                modifier = Modifier.weight(1f)
            )
            WaterAmountCard(
                amount = 500,
                icon = Icons.Outlined.LocalDrink,
                color = Color(0xFF039BE5),
                onClick = { scope.launch { dao.insert(com.example.waterreminder.data.WaterRecord(amount = 500)) } },
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
            border = ButtonDefaults.outlinedButtonBorder.copy(
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
                    imageVector = Icons.Outlined.WaterDrop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("输入饮水量") },
            text = {
                OutlinedTextField(
                    value = customAmount,
                    onValueChange = { customAmount = it.filter { c -> c.isDigit() } },
                    label = { Text("水量 (ml)") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Outlined.LocalDrink, contentDescription = null)
                    },
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    customAmount.toIntOrNull()?.let {
                        if (it > 0 && it <= 5000) {
                            scope.launch { dao.insert(com.example.waterreminder.data.WaterRecord(amount = it)) }
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val helper = remember { AlarmManagerHelper(context) }
    var selectedInterval by remember { mutableIntStateOf(helper.getSavedInterval()) }
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        selectedInterval = helper.getSavedInterval()
    }

    val statusText = if (selectedInterval == 0) {
        "提醒已关闭"
    } else {
        "每${selectedInterval}小时提醒一次"
    }

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
                        text = statusText,
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
