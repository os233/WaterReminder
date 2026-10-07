# 后台提醒与 ROM 行为

这一节是后台提醒可靠性的实测结论，也是几条刻意设计的出处。仓库主页见 [README](../README.md)；
「提醒不响」的排查入口在 [KNOWN_ISSUES.md](KNOWN_ISSUES.md)。

**保活是反的。** 提醒一律走 `setExactAndAllowWhileIdle`（能穿过 Doze），并且刻意不引入
常驻前台服务。曾经为「后台到点不响」改成 `setAlarmClock`，2026-09-21 真机实测证明**没用**：
realme UI 会把后台应用的闹钟**整体搬到 3 天后**（`dumpsys alarm` 里 `whenElapsed` 被直接改写，
前台时又原样搬回来），换哪个 API 都一样 —— 却要付出「状态栏常驻闹钟图标」的代价，所以回滚了。
不引入前台服务的理由：进程活着会被 ROM 冻结，冻结后投递被丢弃；进程不在时，系统才会为
投递闹钟把它冷启动起来，那条路径反而可靠。

**OPPO 系（OPPO / realme / 一加）的 3 天延后。** 2026-09-21 在 realme RMX3800 / realme UI 16 上
实测：不加「允许完全后台行为 + 自启动」两项时，退到后台的闹钟会被整体搬到 **3 天后**
（`dumpsys alarm` 里 `whenElapsed` 被直接改写），表现就是「后台到点不提醒，回到应用才补上」；
加上之后延后立刻消失。
该白名单状态**无法探测**（加与不加，`dumpsys` 里没有任何标志位变化，
`isIgnoringBatteryOptimizations()` 也一直是 `true`），所以提醒设置弹窗里
常驻显示这两个入口，不会因为「看起来已放行」而隐藏 —— 那正是旧版电池优化提示失效的原因：
条件永不成立，用户永远看不到。**别把 `isIgnoringBatteryOptimizations()` 当成「后台已放行」的判据。**

**「从来没提醒过」的另一种表现：被系统 force-stop。** ROM 在后台直接 force-stop 应用后，
AlarmManager 里所有闹钟与 PendingIntent 会被一起清掉，且被 force-stop 的应用处于 `stopped` 状态，
**任何广播都唤不醒它**（连 `BOOT_COMPLETED` 也不行），只能靠用户手动再打开一次应用。
诊断方法：`adb shell dumpsys package <pkg> | grep stopped`
与 `adb shell dumpsys activity exit-info <pkg>`（`reason=13` + `description=... due to o-stop`
即为被系统强停）。

**电池优化（非 OPPO 系）。** 应用被系统强制停止（厂商的一键清理、智能省电）后，已排好的闹钟会
一起被清掉，而唯一的恢复入口是「重新打开应用」—— 这期间不会有任何提醒。未加入电池优化白名单时
Doze / App Standby 仍会推迟闹钟，非 OPPO 系机型的提醒卡片会显示「未关闭电池优化，可能不提醒」，
弹窗里提供「关闭电池优化」按钮直接拉起系统对话框；不开也能用，只是提醒可能不准时。
