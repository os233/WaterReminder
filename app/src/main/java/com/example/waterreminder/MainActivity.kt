package com.example.waterreminder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.waterreminder.data.UserPrefs
import com.example.waterreminder.data.WaterDatabase
import com.example.waterreminder.data.remote.UpdateCheckResult
import com.example.waterreminder.data.remote.UpdateChecker
import com.example.waterreminder.data.remote.UpdateInfo
import com.example.waterreminder.notification.AlarmManagerHelper
import com.example.waterreminder.ui.HistoryScreen
import com.example.waterreminder.ui.OnboardingScreen
import com.example.waterreminder.ui.UpdateDialog
import com.example.waterreminder.ui.WaterReminderScreen
import com.example.waterreminder.ui.theme.WaterReminderTheme
import kotlinx.coroutines.flow.first
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
            // 这里补排一次；按 prefs 记的原定时刻重排，同一时刻重复排是幂等的，
            // 不会把提醒时间不断往后推（见 AlarmManagerHelper.restoreAlarmIfNeeded）。
            alarmHelper.restoreAlarmIfNeeded()
        }

        // 电池优化的提示不在启动时弹 Toast —— 用户看到提示也不知道去哪关。
        // 改为在提醒卡片的设置弹窗里给出可点的入口（见 WaterReminderScreen.ReminderSection）。

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

                // 美化后的更新弹窗：必须留在主题作用域内 —— 挪出去的话 colorScheme 全部落到
                // Material3 默认浅色方案（深色模式弹白底窗、主色变默认紫），且没有任何报错
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
    }
}

@Composable
fun AppNavigation(
    dao: com.example.waterreminder.data.WaterRecordDao,
    onCheckUpdate: () -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // 首启门控：仅在「无任何记录且未完成引导」时进引导——老用户升级后不受影响
    //（Hidroly 的数据驱动判定式）；判定需异步查库，查清前给最小加载态
    var startDestination by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val hasRecords = dao.getAllRecordDates().first().isNotEmpty()
        startDestination =
            if (!UserPrefs.isOnboardingCompleted(context) && !hasRecords) "onboarding" else "home"
    }
    val start = startDestination
    if (start == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    NavHost(
        navController = navController,
        startDestination = start,
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
        composable("onboarding") {
            OnboardingScreen(
                onComplete = {
                    navController.navigate("home") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }
        composable("home") {
            WaterReminderScreen(
                dao = dao,
                onHistoryClick = { navController.navigate("history") },
                onCheckUpdate = onCheckUpdate,
                onRerunOnboarding = { navController.navigate("onboarding") }
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