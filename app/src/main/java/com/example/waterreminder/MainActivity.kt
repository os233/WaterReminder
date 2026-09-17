package com.example.waterreminder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.waterreminder.data.WaterDatabase
import com.example.waterreminder.data.remote.UpdateCheckResult
import com.example.waterreminder.data.remote.UpdateChecker
import com.example.waterreminder.data.remote.UpdateInfo
import com.example.waterreminder.notification.AlarmManagerHelper
import com.example.waterreminder.notification.KeepAliveService
import com.example.waterreminder.ui.HistoryScreen
import com.example.waterreminder.ui.WaterReminderScreen
import com.example.waterreminder.ui.theme.WaterReminderTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "通知权限被拒绝，提醒可能无法显示", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 15+ 强制全面屏（targetSdk 35+ 系统忽略 opt-out），
        // 统一在所有版本开启 edge-to-edge，由界面自行避让系统栏
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val alarmHelper = AlarmManagerHelper(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmHelper.canScheduleExactAlarms()) {
            Toast.makeText(this, "请允许设置精确闹钟，确保后台提醒准确", Toast.LENGTH_LONG).show()
            alarmHelper.openAlarmSettings()
        } else {
            // 应用被 force-stop（系统省电、清理工具、一键加速）时，闹钟会连同 PendingIntent
            // 一起被清掉，而 prefs 里的提醒开关仍是「开启」—— 界面看着正常，实际再也不会响。
            // 这里补排一次；已有待触发闹钟时不会重排，所以不会把提醒时间不断往后推。
            alarmHelper.restoreAlarmIfNeeded()
        }

        checkBatteryOptimization()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, KeepAliveService::class.java))
        } else {
            startService(Intent(this, KeepAliveService::class.java))
        }

        val database = WaterDatabase.getDatabase(this)
        val dao = database.waterRecordDao()

        setContent {
            var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                val result = UpdateChecker(this@MainActivity).checkForUpdate()
                if (result is UpdateCheckResult.Available) updateInfo = result.info
            }

            WaterReminderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        dao = dao,
                        // 手动检查：忽略 12 小时节流，并明确告诉用户是「最新」还是「没查到」
                        onCheckUpdate = {
                            scope.launch {
                                val message = when (val result =
                                    UpdateChecker(this@MainActivity).checkForUpdate(force = true)) {
                                    is UpdateCheckResult.Available -> {
                                        updateInfo = result.info
                                        null
                                    }
                                    UpdateCheckResult.UpToDate -> "已是最新版本"
                                    UpdateCheckResult.Failed -> "检查更新失败，请检查网络后重试"
                                }
                                message?.let {
                                    Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }

            // 美化后的更新弹窗
            updateInfo?.let { info ->
                UpdateDialog(
                    info = info,
                    onDismiss = { if (!info.forceUpdate) updateInfo = null },
                    onConfirm = {
                        val checker = UpdateChecker(this@MainActivity)
                        if (!checker.checkInstallPermission()) {
                            checker.requestInstallPermission()
                        } else {
                            checker.downloadAndInstall(info.apkUrl, info.sha256)
                            updateInfo = null
                        }
                    }
                )
            }
        }
    }

    private fun checkBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Toast.makeText(this, "建议关闭电池优化，确保后台提醒稳定", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
fun UpdateDialog(
    info: UpdateInfo,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 顶部渐变装饰
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.NewReleases,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "发现新版本",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    // 版本信息
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Update,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "最新版本",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = info.versionName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 更新内容
                    Text(
                        text = "更新内容",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                                .padding(14.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            // changelog 来自 docs/version.json 的 changelog 字段（人工手写），按轻 Markdown 渲染：
                            // `#` 开头的行当小标题，其余行当条目
                            info.changelog.split("\n")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .forEach { line ->
                                    if (line.startsWith("#")) {
                                        Text(
                                            text = line.trimStart('#', ' '),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                                        )
                                    } else {
                                        Row(
                                            modifier = Modifier.padding(vertical = 3.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text(
                                                text = "•",
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                            Text(
                                                text = line.trimStart('-', '*', '•', ' ').replace("**", ""),
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 20.sp
                                            )
                                        }
                                    }
                                }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!info.forceUpdate) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text("稍后", fontSize = 15.sp)
                            }
                        }
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("立即更新", fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppNavigation(
    dao: com.example.waterreminder.data.WaterRecordDao,
    onCheckUpdate: () -> Unit
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "home",
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300))
        },
        exitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300))
        },
        popEnterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300))
        }
    ) {
        composable("home") {
            WaterReminderScreen(
                dao = dao,
                onHistoryClick = { navController.navigate("history") },
                onCheckUpdate = onCheckUpdate
            )
        }
        composable("history") {
            HistoryScreen(
                dao = dao,
                onBack = { navController.popBackStack() }
            )
        }
    }
}