# 喝水提醒 WaterReminder

一个轻量的 Android 喝水提醒应用。按小时提醒你喝水，记录每一杯，并按饮品类型折算真实补水量。

Kotlin + Jetpack Compose 编写，无广告、无账号、无联网上传（只在检查更新时访问一次 `version.json`）。

## 功能

**记录**
- 一键记录饮水量，可选 5 种饮品类型
- 按**水合系数**折算：不同饮品按实际补水效果计入总量，而不是简单累加毫升数

  | 饮品 | 系数 | 说明 |
  | --- | --- | --- |
  | 水 | 1.00 | 白水 |
  | 茶 | 0.95 | |
  | 果汁 | 0.85 | |
  | 咖啡 | 0.80 | |
  | 气泡水 | 1.00 | |

- 例如 200ml 咖啡按 160ml 计入当日总量

**目标与统计**
- 每日目标可设 1000–4000 ml（步长 100，默认 2000）
- 首页环形进度显示当日完成百分比
- 连续达标天数（streak）统计

**提醒**
- 按 1 / 2 / 3 小时间隔循环提醒
- 免打扰时段可自定义起止时间，**支持跨午夜**（如 22:00 – 次日 08:00）
- 开机自启，重启后自动恢复提醒
- 常驻前台服务保活，避免系统杀掉闹钟

**历史**
- 最近 7 天汇总：达标天数、日均饮水量
- 月历视图，按日查看当天记录
- 支持多选删除

**更新**
- 启动时检查 GitHub Pages 上的 `version.json`，有新版本时提示下载安装

## 下载

最新版本从 GitHub Pages 获取：

- 版本信息：<https://os233.github.io/WaterReminder/version.json>
- 应用内「检查更新」会自动读取上面的地址

## 环境要求

| 项 | 版本 |
| --- | --- |
| JDK | 17 或更高（21 亦可；**不要用 24+**，Gradle 8.11.1 只支持到 Java 23） |
| Android SDK | compileSdk 36 对应的 platform |
| Gradle | 8.11.1（wrapper 已内置，无需手动装） |
| AGP / Kotlin | 8.10.1 / 2.0.21 |

## 构建

### Debug

```bash
export JAVA_HOME=/path/to/jdk-17
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/WaterReminder_v<版本号>_debug.apk`

### Release

Release 包**必须签名**，否则打出来的 APK 装不上。签名信息从项目根目录的 `keystore.properties` 读取，该文件不入库。

```bash
cp keystore.properties.example keystore.properties
# 编辑 keystore.properties，填 storeFile / storePassword / keyAlias / keyPassword
./gradlew assembleRelease
```

产物：`app/build/outputs/apk/release/WaterReminder_v<版本号>_release.apk`

缺少 `keystore.properties` 或密钥库路径无效时，打包会在 `packageRelease` 阶段直接中止并给出提示 —— 这是刻意设计的，避免又产出一个装不上的未签名 APK。

## 发布流程

1. 修改 `app/build.gradle.kts` 里的 `versionCode` 与 `versionName`
2. `./gradlew assembleRelease`
3. 把产物复制到 `app/release/`，并同步更新 `app/release/output-metadata.json`
4. 更新根目录 `version.json`：
   - `versionCode` 必须**严格大于**上一版，否则应用内不会提示更新
   - `apkUrl` 指向新 APK 在 Pages 上的地址
   - `changelog` 写本次更新内容（`\n` 分隔）
5. commit 并 push 到 `master`

GitHub Pages 从 `master` 分支根目录发布，push 后约 1 分钟生效。

**务必始终使用同一个密钥库**。签名不一致会导致老用户无法覆盖安装，只能卸载重装。

## 项目结构

```
app/src/main/java/com/example/waterreminder/
├── MainActivity.kt              # 入口：初始化数据库、启动保活服务、NavHost、启动时检查更新
├── WaterReminderApp.kt          # Application（空实现，仅在清单中声明）
├── data/
│   ├── WaterRecord.kt           # Room 实体
│   ├── WaterRecordDao.kt        # 查询与统计
│   ├── WaterDatabase.kt         # 数据库与迁移
│   ├── DrinkType.kt             # 饮品类型与水合系数
│   ├── UserPrefs.kt             # SharedPreferences（每日目标）
│   └── remote/
│       ├── UpdateChecker.kt     # 检查更新、下载安装
│       └── UpdateInfo.kt        # version.json 结构
├── notification/
│   ├── AlarmManagerHelper.kt    # 闹钟调度、免打扰判断
│   ├── AlarmReceiver.kt         # 提醒触发
│   ├── BootReceiver.kt          # 开机恢复
│   └── KeepAliveService.kt      # 前台保活服务
└── ui/
    ├── WaterReminderScreen.kt   # 首页
    ├── HistoryScreen.kt         # 历史与统计
    └── theme/                   # 主题配色
```

### 数据模型

```kotlin
@Entity(tableName = "water_records")
data class WaterRecord(
    val id: Int,
    val amount: Int,               // 实际饮用毫升数
    val timestamp: LocalDateTime,
    val drinkType: String,         // 饮品类型 id
    val hydration: Double          // 该次饮水的水合系数
)
```

数据库当前版本为 2，v1 → v2 的迁移新增了 `drinkType` 与 `hydration` 两列。

## 技术栈

- Kotlin 2.0.21 + Jetpack Compose（Compose BOM 2024.09.03）+ Material 3
- Room 2.6.1（KSP 注解处理）
- Navigation Compose 2.8.4
- OkHttp 4.12.0 + Gson 2.10.1
- minSdk 26 / targetSdk 36 / compileSdk 36

## 注意事项

- `keystore.properties` 与 `*.jks` 已在 `.gitignore` 中，**不要提交密钥库**
- Android 13+ 需要授予通知权限；Android 12+ 需要「闹钟和提醒」权限，否则提醒不准时
- 保活服务使用 `specialUse` 类型前台服务（`dataSync` 在 Android 15 上有 6 小时强制停止限制）
- 部分国产 ROM 需要手动允许自启动与后台运行，否则提醒会被杀掉

## 许可证

仓库未包含 LICENSE 文件。
