package com.example.waterreminder.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * 可见期间两次日期校准之间的最大间隔。
 *
 * 为什么不是「精确等到下一个午夜」：`delay()` 的计时基准是 `CLOCK_MONOTONIC`
 * （Compose 的 `AndroidUiDispatcher` 不实现 `Delay`，`delay` 落到 kotlinx 的
 * `DefaultExecutor`，内部是 `LockSupport.parkNanos` + `System.nanoTime()`；
 * 即便走 `Handler.postDelayed`，时间基也是 `SystemClock.uptimeMillis()`）。
 * 这两个时钟**都不计入系统深度睡眠**，所以「后台放一夜」时按午夜算出来的 deadline
 * 根本到不了，界面会一直停在旧日期。改成固定间隔轮询后，最长误差就是一个间隔，
 * 且不依赖任何「当前时刻到某时刻还有多久」的算术。
 */
private const val DATE_RECHECK_INTERVAL_MS = 60_000L

/**
 * 返回随系统日期变化的「今天」，供界面在跨天、改时区、长时间后台后自动刷新。
 *
 * 两个触发条件：
 * 1. 生命周期进入 [Lifecycle.State.STARTED]（含每次从后台回到前台）→ 立即重算一次，
 *    这是「后台放置两天后回到应用」这一场景的兜底；
 * 2. 可见期间每 [DATE_RECHECK_INTERVAL_MS] 毫秒重算一次 → 覆盖「App 一直开着跨过午夜」
 *    与「运行中手动改了系统日期 / 时区」。
 *
 * 屏幕熄灭时 Activity 会退到 STOPPED，循环随 [repeatOnLifecycle] 一起取消，
 * 因此不会在后台空转唤醒。
 */
@Composable
internal fun rememberToday(): LocalDate {
    val lifecycleOwner = LocalLifecycleOwner.current
    var today by remember { mutableStateOf(LocalDate.now()) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                today = LocalDate.now()
                delay(DATE_RECHECK_INTERVAL_MS)
            }
        }
    }

    return today
}
