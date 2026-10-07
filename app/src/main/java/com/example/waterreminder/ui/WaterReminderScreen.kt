package com.example.waterreminder.ui

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.waterreminder.data.DailyTotal
import com.example.waterreminder.data.DrinkType
import com.example.waterreminder.data.UserPrefs
import com.example.waterreminder.data.WaterRecord
import com.example.waterreminder.data.WaterRecordDao
import com.example.waterreminder.widget.WaterWidgetUpdater
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun WaterReminderScreen(
    dao: WaterRecordDao,
    onHistoryClick: () -> Unit,
    onCheckUpdate: () -> Unit,
    onRerunOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var goal by remember { mutableIntStateOf(UserPrefs.getDailyGoal(context)) }
    var showGoalDialog by remember { mutableStateOf(false) }
    var selectedDrink by remember { mutableStateOf(DrinkType.WATER) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var customAmount by remember { mutableStateOf("") }
    var showAboutDialog by remember { mutableStateOf(false) }

    // 「今天」由 rememberToday() 统一驱动：进入 STARTED 立即重算，可见期间每分钟兜底。
    // ⚠️ 不要改回 delay(到下一个午夜) —— delay 走的是 CLOCK_MONOTONIC / uptimeMillis，
    // 熄屏与深度睡眠期间不前进，后台放置一夜后 deadline 到不了，界面会一直停在旧日期。
    val today = rememberToday()

    val todayTotal by remember(today) {
        dao.getTodayTotal(today.toString())
    }.collectAsState(initial = 0)
    // 数字滚动：记录后总数平滑增长（即时反馈，Neer/HydroTracker 式）
    val animatedTotal by animateIntAsState(
        targetValue = todayTotal ?: 0,
        animationSpec = tween(durationMillis = 500),
        label = "total"
    )
    val allTotals by dao.getAllDailyTotals().collectAsState(initial = emptyList())

    val percent = (todayTotal?.toFloat() ?: 0f) / goal
    val animatedProgress by animateFloatAsState(
        targetValue = percent.coerceIn(0f, 1f),
        label = "progress"
    )

    // 连续天数依赖「今天」：必须把 today 作为 key，否则跨天后仍按旧日期计算
    val streak = remember(allTotals, goal, today) { computeStreak(allTotals, goal, today) }

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
    val heroCircle = if (compact) 140.dp else 172.dp
    // 记录撤销：与历史页同款「删除/撤销」Snackbar 模式（HistoryScreen.deleteRecord）
    val snackbarHostState = remember { SnackbarHostState() }

    // Scaffold 负责避让状态栏/手势条并承载记录撤销的 Snackbar；
    // 「今天」仍由 rememberToday() 驱动，与本容器无关
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 480.dp)
                .padding(padding)
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

            // 主视觉：水球直接置于素底，颜色只来自水本身——去渐变卡后深浅两套主题都更干净
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (compact) 12.dp else 20.dp),
                contentAlignment = Alignment.Center
            ) {
                WaterProgressCircle(
                    progress = animatedProgress,
                    modifier = Modifier.size(heroCircle)
                )
                // 中央读数胶囊：半透明底保证任意进度、任意主题下可读
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WaterDrop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(if (compact) 18.dp else 24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$animatedTotal",
                            fontSize = if (compact) 28.sp else 38.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
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
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
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
            Spacer(modifier = Modifier.height(if (compact) 10.dp else 14.dp))
            // 状态提示
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
                MaterialTheme.colorScheme.onSurface
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = statusBg,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = statusText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }

            // 快速记录分组卡：饮料点选 + 杯型按钮一行收纳（同类项目主流布局）
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (compact) 12.dp else 16.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WaterDrop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "快速记录",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        // 自定义水量入口：杯行放不下第四张卡，收进卡头
                        FilledTonalIconButton(
                            onClick = { showCustomDialog = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "自定义水量",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    DrinkDotSelector(
                        drinks = DrinkType.entries.toList(),
                        selected = selectedDrink,
                        onSelect = { selectedDrink = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 档位单一来源：与通知/小部件快捷按钮共用 QuickAddReceiver.QUICK_AMOUNTS，
                        // 改档位只改一处
                        com.example.waterreminder.notification.QuickAddReceiver.QUICK_AMOUNTS.forEach { volume ->
                            WaterAmountCard(
                                amount = volume,
                                icon = selectedDrink.icon,
                                color = selectedDrink.color,
                                onClick = {
                                    recordDrink(context, scope, dao, selectedDrink, volume, snackbarHostState)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 提醒区域
            ReminderSection()

            // 页脚：关于 / 手动检查更新。
            // 放在底部而不是顶栏：窄屏（360dp）下顶栏已经被「日期 + 连续达标徽章 + 历史按钮」
            // 占满，再加一个按钮会被挤爆；追加到 Column 末尾不会改变上面快速记录按钮的位置。
            TextButton(
                onClick = { showAboutDialog = true },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = if (compact) 4.dp else 8.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("关于", fontSize = 14.sp)
            }
        }
    }

    // 自定义水量弹窗
    if (showCustomDialog) {
        // 弹窗每次打开都从零开始校验；remember 在弹窗关闭时随之释放
        var amountError by remember { mutableStateOf(false) }
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
                    onValueChange = {
                        customAmount = it.filter { c -> c.isDigit() }
                        amountError = false
                    },
                    label = { Text("${selectedDrink.label}量 (ml)") },
                    isError = amountError,
                    supportingText = {
                        if (amountError) {
                            Text(
                                "请输入 1 – 5000 的整数",
                                color = MaterialTheme.colorScheme.error
                            )
                        } else if (selectedDrink.hydration < 1.0) {
                            Text("按水合系数 ${(selectedDrink.hydration * 100).toInt()}% 折算计入总量")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    leadingIcon = {
                        Icon(selectedDrink.icon, contentDescription = null)
                    },
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val amount = customAmount.toIntOrNull()
                    // 非法输入不再静默丢弃：标红报错并留在弹窗里改
                    if (amount != null && amount in 1..5000) {
                        recordDrink(context, scope, dao, selectedDrink, amount, snackbarHostState)
                        showCustomDialog = false
                        customAmount = ""
                    } else {
                        amountError = true
                    }
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

    // 关于 / 手动检查更新
    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false },
            onCheckUpdate = {
                showAboutDialog = false
                onCheckUpdate()
            },
            onRerunOnboarding = {
                showAboutDialog = false
                onRerunOnboarding()
            }
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
                        valueRange = 1000f..5000f
                    )
                    Text(
                        text = "范围 1000 – 5000 ml，步长 100",
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
                    // 目标变化直接影响小部件进度分母，随手推送一次
                    WaterWidgetUpdater.push(context, todayTotal ?: 0, newGoal)
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

/**
 * 记一笔饮水，随后弹 Snackbar 提供「撤销」——快速记录卡片热区大，误触后
 * 不应逼用户去历史页长按找回。撤销按插入返回的行 id 精确删除，不影响其他记录。
 */
private fun recordDrink(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    dao: WaterRecordDao,
    drink: DrinkType,
    amount: Int,
    snackbarHostState: SnackbarHostState
) {
    scope.launch {
        val id = dao.insert(
            WaterRecord(
                amount = amount,
                drinkType = drink.id,
                hydration = drink.hydration
            )
        )
        WaterWidgetUpdater.refresh(context)
        val result = snackbarHostState.showSnackbar(
            message = "已记录 ${drink.label} $amount ml",
            actionLabel = "撤销",
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            dao.deleteById(id)
            WaterWidgetUpdater.refresh(context)
        }
    }
}

/** 连续达标天数：today 未达标则从昨天起算，不因"还没喝"而清零 */
internal fun computeStreak(totals: List<DailyTotal>, goal: Int, today: LocalDate): Int {
    if (totals.isEmpty()) return 0
    val map = totals.associate { it.recordDate to it.total }
    var date = today
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

