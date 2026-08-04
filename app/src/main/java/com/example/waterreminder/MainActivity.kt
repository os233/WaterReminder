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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.waterreminder.data.WaterDatabase
import com.example.waterreminder.notification.AlarmManagerHelper
import com.example.waterreminder.notification.KeepAliveService
import com.example.waterreminder.ui.HistoryScreen
import com.example.waterreminder.ui.WaterReminderScreen
import com.example.waterreminder.ui.theme.WaterReminderTheme

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val alarmHelper = AlarmManagerHelper(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmHelper.canScheduleExactAlarms()) {
            Toast.makeText(this, "请允许设置精确闹钟，确保后台提醒准确", Toast.LENGTH_LONG).show()
            alarmHelper.openAlarmSettings()
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
            WaterReminderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(dao = dao)
                }
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
fun AppNavigation(dao: com.example.waterreminder.data.WaterRecordDao) {
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
                onHistoryClick = { navController.navigate("history") }
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