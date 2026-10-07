package com.example.waterreminder.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.waterreminder.notification.AlarmManagerHelper

@Composable
fun ReminderSection() {
    val context = LocalContext.current
    val helper = remember { AlarmManagerHelper(context) }
    var selectedInterval by remember { mutableIntStateOf(helper.getSavedInterval()) }
    var showDialog by remember { mutableStateOf(false) }
    var dndEnabled by remember { mutableStateOf(helper.isDndEnabled()) }
    var dndStart by remember { mutableIntStateOf(helper.getDndStartHour()) }
    var dndEnd by remember { mutableIntStateOf(helper.getDndEndHour()) }
    var notificationsAllowed by remember { mutableStateOf(true) }
    var batteryUnrestricted by remember { mutableStateOf(true) }

    // OPPO 系 ROM 的白名单状态在系统里**探测不到**（加与不加，dumpsys 里没有任何标志位变化），
    // 所以这个入口只能常驻显示，不能做成「已开启就隐藏」—— 否则就是现有电池优化提示的翻版：
    // 条件永不成立，用户永远看不到。
    val isOplus = remember { isOplusDevice() }

    LaunchedEffect(Unit) {
        selectedInterval = helper.getSavedInterval()
        notificationsAllowed = hasNotificationPermission(context)
        batteryUnrestricted = isIgnoringBatteryOptimizations(context)
    }

    // 每次回到前台都重读权限类状态：点「关闭电池优化」会跳到系统对话框，授权后返回
    // 只触发 ON_RESUME —— LaunchedEffect(Unit) 与 LaunchedEffect(showDialog) 都不会重跑，
    // 卡片会一直显示「未关闭电池优化」直到重启 App（2026-09-18 真机实测到的缺陷）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsAllowed = hasNotificationPermission(context)
                batteryUnrestricted = isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 每次打开设置弹窗都重读一次 —— 用户可能刚从系统设置里改完回来，
    // 不重读的话卡片会一直显示旧状态
    LaunchedEffect(showDialog) {
        if (showDialog) {
            notificationsAllowed = hasNotificationPermission(context)
            batteryUnrestricted = isIgnoringBatteryOptimizations(context)
        }
    }

    val statusText = if (selectedInterval == 0) {
        "提醒已关闭"
    } else {
        "每${selectedInterval}小时提醒一次"
    }
    val dndText = if (dndEnabled) " · ${"%02d".format(dndStart)}:00–${"%02d".format(dndEnd)}:00 免打扰" else ""
    // 通知权限被拒时闹钟照常触发，通知却被系统静默丢弃 —— 界面必须讲出来，
    // 否则用户只会看到「设了提醒却从来不响」，无从判断问题在哪
    val notifText =
        if (selectedInterval != 0 && !notificationsAllowed) " · 通知权限未开启，提醒不会显示" else ""
    // 未加入电池优化白名单时，系统（国产 ROM 尤其激进）会在后台清掉应用连同已排好的闹钟，
    // 而 prefs 里的开关仍是「开启」—— 界面看着正常，实际再也不会响
    val batteryText =
        if (selectedInterval != 0 && !batteryUnrestricted) " · 未关闭电池优化，可能不提醒" else ""
    // OPPO 系的延后机制探测不到，只能常驻给一句提醒；用「若…请…」的说法，
    // 不宣称「已受限」（用户很可能已经加过白名单了）
    val oplusText =
        if (isOplus && selectedInterval != 0) " · 若后台不提醒，请开后台运行权限" else ""

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
                        text = "$statusText$dndText$notifText$batteryText$oplusText",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            FilledTonalIconButton(
                onClick = { showDialog = true },
                modifier = Modifier.size(48.dp)
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
                        1 to ("每小时提醒" to MaterialTheme.colorScheme.primary),
                        2 to ("每2小时提醒" to MaterialTheme.colorScheme.primary),
                        3 to ("每3小时提醒" to MaterialTheme.colorScheme.primary)
                    ).forEach { (hours, pair) ->
                        val (label, color) = pair
                        val isSelected = selectedInterval == hours
                        Card(
                            onClick = {
                                // 只在真的排上时才改本地状态；排不上（精确闹钟权限被拒）时保持原状，
                                // 卡片才不会显示一个系统里并不存在的提醒。
                                // 不自动关弹窗：选完间隔通常还要顺手设免打扰
                                if (setReminder(context, hours)) selectedInterval = hours
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
                                        imageVector = Icons.Default.Check,
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

                    // 被系统 force-stop 后闹钟会被一起清掉，而唯一的补排入口是「打开 App」——
                    // 未加白名单时这是提醒失效的头号原因，光提示不给入口等于什么都没做。
                    // OPPO 系另有「延后后台闹钟 3 天」的机制（2026-09-21 实测），
                    // 且其白名单状态探测不到，所以对这类 ROM 一律常驻入口。
                    if (isOplus || !batteryUnrestricted) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        Text(
                            text = if (isOplus) "让提醒在后台也能响" else "后台运行受限",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isOplus) {
                                "本机系统会延后后台应用的提醒（实测最多延后 3 天），" +
                                    "到点不会响，只有重新打开本应用才会补上。请到系统设置里放行：" +
                                    "「应用耗电管理」里点进本应用，打开「允许完全后台行为」；" +
                                    "再到「设置 → 应用 → 自启动」里允许本应用。"
                            } else {
                                "系统可能在本应用退到后台后清掉它，连同已排好的提醒；" +
                                    "被清掉后只有重新打开本应用才会恢复，这期间不会有任何提醒。" +
                                    "关闭电池优化能明显降低被清的概率。"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isOplus) {
                                // 「自启动管理」那一页要 OPLUS 签名级权限，第三方应用拉不起来，
                                // 所以它只写在文案里；这里给的两个入口都是实测能打开的
                                FilledTonalButton(
                                    onClick = { openOplusPowerConsumption(context) },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text("应用耗电管理", fontSize = 14.sp)
                                }
                                FilledTonalButton(
                                    onClick = { openAppSettings(context) },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text("应用设置", fontSize = 14.sp)
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = { requestIgnoreBatteryOptimization(context) },
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Text("关闭电池优化", fontSize = 14.sp)
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

/**
 * 是否是 OPPO 系（OPPO / realme / 一加）。
 *
 * 这几家的 ROM 会把**后台应用的闹钟整体搬到 3 天后**（2026-09-21 在 realme RMX3800 /
 * Android 16 / realme UI 16 上实测：`dumpsys alarm` 里 `whenElapsed` 被直接改写，
 * 前台时又原样搬回来），表现就是「后台到点不提醒，回到 App 才补上」。
 * 它与用哪个闹钟 API 无关（`setAlarmClock` 也挡不住），唯一解药是系统里的白名单 ——
 * 「允许完全后台行为」可以直跳，见 [openOplusPowerConsumption]；「自启动」页因 OPLUS
 * 签名级权限拉不起来，只能在文案里写路径引导（见 [openAppSettings] 的说明）。
 */
private fun isOplusDevice(): Boolean {
    val brand = (Build.BRAND + Build.MANUFACTURER).lowercase()
    return brand.contains("oppo") || brand.contains("realme") || brand.contains("oneplus")
}

/**
 * 跳「应用耗电管理」。点进本应用后可开「允许完全后台行为」——
 * 2026-09-21 真机实测：这一项 + 自启动打开后，后台闹钟的 3 天延后立刻消失。
 *
 * ⚠️ **不能用组件名直跳**：`com.oplus.battery/com.oplus.powermanager.fuelgaue.PowerConsumptionActivity`
 * 声明了 `oplus.permission.OPLUS_COMPONENT_SAFE`（OPLUS 签名级权限），第三方应用显式启动会被
 * `SecurityException: Permission Denial` 拒掉 —— 实测连 shell（uid 2000）都起不来。
 * 改用标准 action `ACTION_POWER_USAGE_SUMMARY`：它解析到的**正是同一个页面**，且不受该权限限制。
 */
private fun openOplusPowerConsumption(context: Context) {
    val intent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        // 个别 ROM 没有这个页面时至少把应用详情页给出来，别让点击静默无效
        .onFailure { openAppSettings(context) }
}

/**
 * 跳本应用的系统详情页。
 *
 * 「自启动管理」（`com.oplus.battery/...startupapp.view.StartupAppListActivity`）同样要
 * OPLUS 签名级权限，第三方应用拉不起来，所以那一项只能在文案里写路径、给不出按钮。
 */
private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

/** Android 13+ 通知是运行时权限；被拒后闹钟照常触发，但通知会被系统静默丢弃 */
private fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * 是否已加入电池优化白名单（即系统不再限制它后台运行）。
 * 未加入时系统可能在后台清掉应用连同已排好的闹钟，而 prefs 里的开关仍是「开启」——
 * 界面看着正常，实际再也不会响。国产 ROM 尤其激进。
 */
private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

/**
 * 弹系统的「允许后台运行」对话框（即关闭电池优化）。
 * 国产 ROM 的自启动 / 后台运行开关没有公开 API，只能靠文案引导用户自己去设置里找，
 * 这里先把系统层面能做的做掉。
 */
private fun requestIgnoreBatteryOptimization(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    // 部分 ROM 会拦这个 intent，不能让 Compose 的点击回调跟着崩
    runCatching { context.startActivity(intent) }
}

/**
 * 设置 / 关闭提醒。
 * @return 是否真的生效 —— 精确闹钟权限被拒时排不上，返回 false，调用方据此别改界面状态，
 *         否则卡片会显示一个系统里并不存在的提醒（用户以为设上了，其实永远不会响）。
 */
private fun setReminder(context: Context, hours: Int): Boolean {
    val helper = AlarmManagerHelper(context)
    if (hours == 0) {
        helper.cancelAlarm()
        return true
    }
    return helper.setRepeatingAlarm(hours)
}
