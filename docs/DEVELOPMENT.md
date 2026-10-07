# 开发与构建

WaterReminder 的开发环境、构建方式与项目结构。仓库主页见 [README](../README.md)。

## 环境要求

| 项 | 版本 |
| --- | --- |
| JDK | 17 或 21（Gradle 8.11.1 只支持到 Java 23，**24+ 不可用**；CI 固定 17） |
| Android SDK | platform `android-36`（CI 另装 `build-tools;36.0.0`） |
| Gradle | 8.11.1（wrapper 已内置，无需手动装） |
| AGP / Kotlin / KSP | 8.10.1 / 2.0.21 / 2.0.21-1.0.28 |
| Python | 3.9+（只有 `scripts/` 下的发版脚本用，不参与构建） |

工具链版本是硬约束；构建报错先核对环境，不能通过随意升级工具或依赖绕过。

## 依赖安装

推荐 Android Studio，依赖在首次打开项目时自动下载：

1. 安装 [Android Studio](https://developer.android.com/studio)（自带 JDK 与 Android SDK）
2. 拉取代码：

   ```bash
   git clone https://github.com/os233/WaterReminder.git
   ```

3. 用 Android Studio 打开项目根目录，等 Gradle Sync 跑完 —— AGP、Kotlin、Compose、Room 等依赖都托管在 `google()` 与 `mavenCentral()`，自动下载，无需手动安装

纯命令行构建：

- 安装 JDK 17 或 21，并设置 `JAVA_HOME` 指向它（Gradle 8.11.1 不支持 Java 24+）
- Android SDK 用 Android Studio 安装，或通过 `ANDROID_HOME` / `local.properties` 指定 SDK 路径
- Gradle 无需手动装，wrapper 首次运行会自动下载 8.11.1

## 构建

**Debug**

```bash
export JAVA_HOME=/path/to/jdk-17
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/WaterReminder_v<版本号>_debug.apk`

**单元测试**

纯逻辑（免打扰时段、更新 JSON 逐字段校验、SHA-256、连续达标天数、日历网格、
饮料 id 兜底与水合系数、时间戳转换器、日期展示格式化、每日目标计算、CSV 导出、
月度达标统计）有 JUnit4 单元测试，位于 `app/src/test`：

```bash
./gradlew testDebugUnitTest
```

**Release**

Release 包**必须签名**，否则打出来的 APK 装不上。签名信息从项目根目录的 `keystore.properties` 读取，该文件不入库。

```bash
cp keystore.properties.example keystore.properties
# 编辑 keystore.properties，填 storeFile / storePassword / keyAlias / keyPassword
./gradlew assembleRelease
```

产物：`app/build/outputs/apk/release/WaterReminder_v<版本号>_release.apk`

缺少 `keystore.properties` 或密钥库路径无效时，打包会在 `packageRelease` 阶段直接中止并给出提示 —— 这是刻意设计的，避免又产出一个装不上的未签名 APK。发布流程见 [RELEASE.md](RELEASE.md)。

## 项目结构

```
├── AGENTS.md                    # AI 代理必须遵守的仓库硬边界与技术约束
├── programs.md                  # AI 代理的职责原则、任务处理方式与汇报要求
├── .github/workflows/           # CI：ci.yml（Manifest 校验 → 编译 → 单元测试 → lint 只报告）、release.yml（构建签名 APK 发到 Releases）
├── build.gradle.kts             # AGP / Kotlin / KSP 插件版本；app/build.gradle.kts 里是版本号与依赖
├── gradle/ · gradlew            # Gradle wrapper（8.11.1，只走 wrapper）
├── scripts/
│   ├── release.sh               # 一键发版：构建 → 归档 → 校验版本文件
│   └── sync_version.py          # 同步 / 校验版本文件与源码版本号（CI 用它写回 Manifest）
├── docs/                        # GitHub Pages 官网（Pages 源就是这个目录）+ 本仓库的主题文档
│   ├── index.html               # 首页
│   ├── download/ changelog/     # 下载页、更新日志（前端现读 Releases API，失败退到 version.json）
│   ├── docs/ privacy/           # 使用文档、隐私政策
│   ├── KNOWN_ISSUES.md          # 已知问题与局限（「提醒不响」的排查入口也在这里）
│   ├── USAGE.md DEVELOPMENT.md  # 功能与使用说明、开发与构建（本文件）
│   ├── RELEASE.md BACKGROUND.md # 发布与维护、后台提醒与 ROM 行为
│   ├── images/                  # README 界面截图
│   ├── assets/                  # style.css + site.js
│   └── version.json             # 发布 Manifest：App 内更新与官网都读它（机器字段由 CI 写）
├── app/release/                 # 本地 APK 留档（已 gitignore，不入库；分发走 GitHub Releases）
└── app/src/main/
    ├── AndroidManifest.xml      # 权限、组件声明（无前台服务）
    ├── res/                     # 图标（自适应 + 单色层）、主题、colors、小部件布局、file_paths.xml
    └── java/com/example/waterreminder/
        ├── MainActivity.kt          # 入口：通知 / 精确闹钟权限、NavHost 与首启引导门控
        ├── WaterReminderApp.kt      # Application（空实现，仅在清单中声明）
        ├── data/
        │   ├── WaterRecord.kt       # Room 实体
        │   ├── WaterRecordDao.kt    # 查询与统计
        │   ├── WaterDatabase.kt     # 数据库与迁移
        │   ├── DrinkType.kt         # 饮品类型与水合系数
        │   ├── GoalCalculator.kt    # 个性化每日目标计算（EFSA 口径，纯函数）
        │   ├── CsvExport.kt         # CSV 导出格式化（纯函数）
        │   ├── UserPrefs.kt         # SharedPreferences（每日目标、引导与个人资料）
        │   └── remote/
        │       ├── UpdateChecker.kt # 读 Pages 的 version.json 检查更新、下载并校验 SHA-256 后安装
        │       └── UpdateInfo.kt    # 一次可用更新的数据（对应 version.json 的顶层字段）
        ├── notification/
        │   ├── AlarmManagerHelper.kt # 闹钟调度（setExactAndAllowWhileIdle）、免打扰判断、按原定时刻补排
        │   ├── AlarmReceiver.kt     # 提醒触发、通知渠道（含震动）创建、快捷记录动作按钮
        │   ├── QuickAddReceiver.kt  # 通知 / 小部件快捷记录：写库 + 静默反馈
        │   └── BootReceiver.kt      # 开机 / 覆盖安装后恢复
        ├── widget/
        │   ├── WaterWidgetProvider.kt  # 4×2 桌面小部件
        │   └── WaterWidgetUpdater.kt   # 进度渲染与推送（数据变更方调用）
        └── ui/                      # 首页 / 历史页装配、引导页、拆分后的卡片与对话框组件、
            WaterProgress.kt         # 水球波浪进度
            RememberToday.kt         # 生命周期感知的「今天」，界面统一日期源
            └── theme/               # 主题配色（品牌蓝、深色模式、success 语义色）
```

## 数据模型

```kotlin
@Entity(tableName = "water_records")
data class WaterRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val amount: Int,                                  // 实际饮用毫升数
    val timestamp: LocalDateTime = LocalDateTime.now(),
    @ColumnInfo(defaultValue = "water")
    val drinkType: String = DrinkType.WATER.id,       // 饮品类型 id
    @ColumnInfo(defaultValue = "1.0")
    val hydration: Double = 1.0                       // 该次饮水的水合系数
)
```

数据库当前版本为 2，v1 → v2 的迁移新增了 `drinkType` 与 `hydration` 两列。
Room 实体变更必须提供对应 Migration，禁止 destructive migration —— 饮水记录是用户唯一数据。

## 技术栈

- Kotlin 2.0.21 + Jetpack Compose（Compose BOM 2024.09.03）+ Material 3
- Room 2.6.1（KSP 注解处理）
- Navigation Compose 2.8.4
- OkHttp 4.12.0 + Gson 2.10.1（Gson 只取 `JsonParser` 逐字段校验，不做对象反序列化）
- minSdk 26 / targetSdk 36 / compileSdk 36

## 其他约定

- `keystore.properties` 与 `*.jks` 已在 `.gitignore` 中，**不要提交密钥库**；
  `local.properties`（含本机 SDK 绝对路径）同样不入库，clone 后用 Android Studio 打开会自动生成
- 换行符由 `.gitattributes` 统一：文本文件一律 LF 入库，`*.bat` 保持 CRLF。
  Linux CI 上 `./gradlew` 若是 CRLF 会直接报 `bash\r: No such file or directory`
- lint 目前只报告、不拦截
