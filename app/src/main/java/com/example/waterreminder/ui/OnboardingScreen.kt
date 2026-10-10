package com.example.waterreminder.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.waterreminder.data.ActivityLevel
import com.example.waterreminder.data.Gender
import com.example.waterreminder.data.GoalCalculator
import com.example.waterreminder.data.UserPrefs

/**
 * 首启引导：① 欢迎 ② 资料与目标（推荐值实时计算、可手改）③ 提醒。
 *
 * 仅在「无记录且未完成引导」时进入（见 AppNavigation 门控），老用户升级不受影响。
 * 权限不在这里重复弹系统框——启动时已请求（MainActivity.onCreate），本页只读
 * 状态并给系统设置跳转；从设置返回时经 ON_RESUME 重读，避免状态停留在旧值
 * （与 ReminderSection 同款生命周期处理）。
 */
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.factory(context))
    var step by rememberSaveable { mutableIntStateOf(0) }

    // 资料项：重新运行引导时预填已存档的个人资料
    var gender by remember { mutableStateOf(UserPrefs.getUserGender(context)) }
    var weightText by remember { mutableStateOf(UserPrefs.getUserWeightKg(context)?.toString() ?: "") }
    var activity by remember { mutableStateOf(UserPrefs.getUserActivityLevel(context) ?: ActivityLevel.MODERATE) }
    var goal by remember { mutableStateOf(UserPrefs.getDailyGoal(context)) }
    var showClampNote by remember { mutableStateOf(false) }
    var intervalHours by rememberSaveable { mutableIntStateOf(1) }
    var notificationAllowed by remember { mutableStateOf(true) }
    var exactAlarmAllowed by remember { mutableStateOf(true) }

    val weightKg = weightText.toIntOrNull()?.takeIf { it in 20..200 }

    // 资料变化 → 实时重算推荐值（live preview：改体重立刻看到目标跟着动）
    LaunchedEffect(gender, weightKg, activity) {
        val selectedGender = gender ?: return@LaunchedEffect
        val raw = GoalCalculator.calculateRawGoal(selectedGender, weightKg, activity)
        val recommended = GoalCalculator.calculateRecommendedGoal(selectedGender, weightKg, activity)
        goal = recommended
        showClampNote = raw != recommended
    }

    fun refreshPermissionStatus() {
        notificationAllowed = hasNotificationPermission(context)
        exactAlarmAllowed = viewModel.canScheduleExactAlarms()
    }

    LaunchedEffect(step) {
        if (step == 2) {
            // 进入提醒步骤时按目标预选建议间隔（用户仍可改）
            intervalHours = GoalCalculator.suggestedIntervalHours(goal)
            refreshPermissionStatus()
        }
    }

    // 从系统设置授权回来只触发 ON_RESUME：不重读的话状态一直是旧值（2026-09-18 真机实测过的同类缺陷）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermissionStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun finishOnboarding() {
        val scheduled = viewModel.finishOnboarding(
            gender = gender ?: Gender.OTHER,
            weightKg = weightKg,
            activity = activity,
            goal = goal,
            intervalHours = intervalHours
        )
        if (!scheduled) {
            // 精确闹钟权限被拒时排不上：保留引导结论但明确告知，与提醒卡片口径一致
            Toast.makeText(
                context,
                "精确闹钟权限未开启，请到系统设置允许后提醒才能准时",
                Toast.LENGTH_LONG
            ).show()
        }
        onComplete()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "第 ${step + 1} / 3 步",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        // 内容区独占剩余高度：WelcomeStep 内部的 weight 占位只在这里生效，
        // 底部按钮行始终钉在屏幕内（否则欢迎页会把按钮挤出屏幕）
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (step) {
                0 -> WelcomeStep()
                1 -> ProfileStep(
                    gender = gender,
                    onGenderChange = { gender = it },
                    weightText = weightText,
                    onWeightChange = { weightText = it.filter { c -> c.isDigit() } },
                    activity = activity,
                    onActivityChange = { activity = it },
                    goal = goal,
                    onGoalChange = { goal = GoalCalculator.snapManualGoal(it.toInt()) }
                )
                else -> ReminderStep(
                    intervalHours = intervalHours,
                    onIntervalChange = { intervalHours = it },
                    notificationAllowed = notificationAllowed,
                    exactAlarmAllowed = exactAlarmAllowed,
                    goalMl = goal,
                    onOpenAlarmSettings = { viewModel.openAlarmSettings() }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step > 0) {
                OutlinedButton(
                    onClick = { step -= 1 },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("上一步")
                }
            }
            when (step) {
                0 -> Column(modifier = Modifier.weight(2f)) {
                    Button(
                        onClick = { step = 1 },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("下一步")
                    }
                    TextButton(
                        onClick = {
                            viewModel.skipOnboarding()
                            onComplete()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("跳过，稍后自己设置", fontSize = 13.sp)
                    }
                }
                1 -> Button(
                    onClick = { step = 2 },
                    modifier = Modifier.weight(2f),
                    shape = RoundedCornerShape(14.dp),
                    enabled = gender != null
                ) {
                    Text("下一步")
                }
                else -> Button(
                    onClick = { finishOnboarding() },
                    modifier = Modifier.weight(2f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("开始使用")
                }
            }
        }
    }

    if (showClampNote && step == 1) {
        AlertDialog(
            onDismissRequest = { showClampNote = false },
            title = { Text("推荐值已封顶") },
            text = {
                Text(
                    "按你的体重计算出的每日饮水量超过了 ${GoalCalculator.MAX_GOAL_ML} ml 的建议上限，" +
                        "已按上限设置；如确有需要可手动调高。"
                )
            },
            confirmButton = {
                TextButton(onClick = { showClampNote = false }) { Text("知道了") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun WelcomeStep() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Text(text = "💧", fontSize = 64.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "欢迎使用喝水提醒",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        listOf(
            "记录每一杯水，按饮料折算水合度",
            "定时提醒，夜间免打扰自动静默",
            "桌面小部件与通知一键记录"
        ).forEach { line ->
            Text(
                text = "•  $line",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ProfileStep(
    gender: Gender?,
    onGenderChange: (Gender) -> Unit,
    weightText: String,
    onWeightChange: (String) -> Unit,
    activity: ActivityLevel,
    onActivityChange: (ActivityLevel) -> Unit,
    goal: Int,
    onGoalChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "关于你",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text("性别", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Gender.MALE to "男", Gender.FEMALE to "女", Gender.OTHER to "其他")
                .forEach { (value, label) ->
                    FilterChip(
                        selected = gender == value,
                        onClick = { onGenderChange(value) },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
        }

        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = weightText,
            onValueChange = onWeightChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("体重（可选，kg）") },
            supportingText = { Text("20 – 200 kg；留空则不按体重校准") },
            isError = weightText.toIntOrNull()?.let { it !in 20..200 } == true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text("日常运动量", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        listOf(
            ActivityLevel.SEDENTARY to "久坐（几乎不动）",
            ActivityLevel.LIGHT to "轻度（每周 1–2 次）",
            ActivityLevel.MODERATE to "中度（每周 3–4 次）",
            ActivityLevel.ACTIVE to "较高（每周 5–6 次）",
            ActivityLevel.VERY_ACTIVE to "很高（几乎每天）"
        ).forEach { (value, label) ->
            FilterChip(
                selected = activity == value,
                onClick = { onActivityChange(value) },
                label = { Text(label) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "推荐每日目标",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$goal ml",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value = goal.toFloat(),
                    onValueChange = onGoalChange,
                    valueRange = 1000f..5000f
                )
                Text(
                    text = "范围 1000 – 5000 ml，步长 100；EFSA 总水分口径，含食物水分，可按习惯手改",
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ReminderStep(
    intervalHours: Int,
    onIntervalChange: (Int) -> Unit,
    notificationAllowed: Boolean,
    exactAlarmAllowed: Boolean,
    goalMl: Int,
    onOpenAlarmSettings: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "定时提醒",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "按 $goalMl ml 的目标，建议每 ${intervalHours} 小时提醒一次（也可自选）",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "不提醒", 1 to "每小时", 2 to "每2小时", 3 to "每3小时")
                .forEach { (hours, label) ->
                    FilterChip(
                        selected = intervalHours == hours,
                        onClick = { onIntervalChange(hours) },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
        }

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        PermissionRow(
            title = "通知权限",
            granted = notificationAllowed,
            grantedText = "已开启，提醒可以正常显示",
            missingActionLabel = "去开启",
            onMissingAction = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                }
            }
        )
        Spacer(modifier = Modifier.height(12.dp))
        PermissionRow(
            title = "精确闹钟",
            granted = exactAlarmAllowed,
            grantedText = "已允许，提醒时间准确",
            missingActionLabel = "去设置",
            onMissingAction = {
                runCatching { onOpenAlarmSettings() }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "夜间免打扰时段、电池优化白名单等细节，可稍后在首页的提醒卡片里设置。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    grantedText: String,
    missingActionLabel: String,
    onMissingAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$title · ${if (granted) "已开启" else "未开启"}",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (granted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
            )
            Text(
                text = if (granted) grantedText else "提醒可能不会显示或不够准时",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!granted) {
            FilledTonalButton(onClick = onMissingAction) {
                Text(missingActionLabel)
            }
        }
    }
}
