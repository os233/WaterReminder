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
- 启动时检查 `version.json`，有新版本时提示下载安装（APK 从 GitHub Releases 下载）

## 下载

最新版本从 [GitHub Releases](https://github.com/os233/WaterReminder/releases) 获取：

- 版本信息：<https://os233.github.io/WaterReminder/version.json>（GitHub Pages 仍托管这个 JSON）
- 应用启动时自动读取上面的地址检查更新，发现新版本会弹窗提示，确认后按其中的 `apkUrl` 到 Releases 下载 APK

## 使用说明

**首次启动**

- Android 13+ 会弹出通知权限申请，允许后提醒才能显示
- Android 12+ 需在系统设置中授予「闹钟和提醒」权限，否则提醒不准时
- 部分国产 ROM 需手动允许自启动与后台运行，否则提醒会被杀掉

**日常使用**

- **记录**：首页点饮品按钮记一杯，或通过「自定义」输入任意毫升数，按水合系数折算入当日总量
- **目标**：点首页的目标数字修改每日目标（1000–4000 ml，步长 100）
- **提醒**：首页进入「设置提醒」，选 1 / 2 / 3 小时间隔；可开启夜间免打扰并设起止时间（支持跨午夜）
- **历史**：从首页进入历史页，查看最近 7 天汇总与月历，支持多选删除
- **更新**：启动时自动检查，有新版本时在弹窗里点「立即更新」即可

## 环境要求

| 项 | 版本 |
| --- | --- |
| JDK | 17 或更高（21 亦可；**不要用 24+**，Gradle 8.11.1 只支持到 Java 23） |
| Android SDK | compileSdk 36 对应的 platform |
| Gradle | 8.11.1（wrapper 已内置，无需手动装） |
| AGP / Kotlin | 8.10.1 / 2.0.21 |
| Python | 3.9+（只有 `scripts/` 下的发版脚本用，不参与构建） |

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

版本号写在两处（`app/build.gradle.kts` 与 `version.json`），历史上出现过脱节 —— `version.json` 停在旧版本，
导致应用内更新永远不触发。现在由脚本统一同步 + 校验。

```bash
# 1. 改 app/build.gradle.kts 里的 versionCode 与 versionName（versionCode 必须严格递增）
# 2. 一键构建 → 归档 → 同步版本号
./scripts/release.sh
# 3. 手工补 version.json 的 changelog（脚本不动它）
# 4. 提交发版提交
git add -A && git commit -m "release: 发布 <版本号>"
# 5. 打 tag 并只推 tag → CI 自动构建签名 APK、创建 Release 并上传 asset
#    （tag 必须打在 versionName 一致的提交上，工作流第一步会校验）
git tag v<版本号> && git push origin v<版本号>
# 6. 等 CI 跑完、Release 页能看到 APK asset 后，再推 master
#    （version.json 上线后老版本用户才会拿到新 apkUrl）
git push origin master
```

`scripts/release.sh` 依次做：校验 `keystore.properties` 存在 → `./gradlew assembleRelease` →
产物归档到 `app/release/`（本地留档，不入库）→ 同步 `version.json` 与
`app/release/output-metadata.json` → 版本号一致性校验 → apksigner 签名自检（找不到工具就跳过并提示）。
**它不会自动 commit / push** —— 发布是对外动作，留给人确认。

GitHub Pages 从 `master` 分支根目录发布，但只托管 `version.json`（APK 走 Releases），push 后约 1 分钟生效。
**务必先让 Release 里的 APK 就位，再 push `version.json`**：否则旧版本用户检查更新时会拿到一个 404 的 `apkUrl`。
所以顺序是「推 tag → 等 Release 工作流跑完 → 再推 master」，不要一口气全推。

**务必始终使用同一个密钥库**。签名不一致会导致老用户无法覆盖安装，只能卸载重装。

### 版本号一致性校验

```bash
python scripts/sync_version.py --check    # 只校验不写文件；CI 也跑这个
python scripts/sync_version.py --expect-tag v1.4.0    # 断言 tag 与 versionName 一致；发版工作流用
```

校验的不变量：

- `version.json` 的 versionCode **不能比源码还新**（多半是升了它却忘了升 `app/build.gradle.kts`）
- `apkUrl` 的文件名必须与 versionName 匹配
- `apkUrl` 指向 Releases 时脚本只给提示，**无法校验 asset 是否已上传** —— 发版后要自己确认
  Release 里有这个文件，否则应用内更新会 404（Pages 形态则校验 `app/release/` 下文件是否存在）

允许 `version.json` 落后于源码 —— 开发中先升源码版本号是正常的。

### CI

| 工作流 | 触发 | 做什么 |
| --- | --- | --- |
| `.github/workflows/ci.yml` | push 到 master / 任何 PR / 手动 | 版本号校验 + `assembleDebug`（lint 目前只报告不拦截）。不需要任何密钥，fork 的 PR 也能安全跑 |
| `.github/workflows/release.yml` | push `v*` tag 自动发版；手动触发保留，用于失败重跑（会覆盖已有 asset） | 校验 tag 与 versionName 一致 → 构建签名 APK → 创建 Release 并上传 asset |

release 工作流需要在仓库 Settings → Secrets and variables → Actions 配好
`KEYSTORE_BASE64`（`base64 -w0 water_keystore.jks`）、`STORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`；
没配的话它会在第一步明确报错退出，不会静默产出未签名包。

## 项目结构

```
├── .github/workflows/           # CI：ci.yml（版本号校验 + 编译）、release.yml（构建签名 APK 发到 Releases）
├── scripts/
│   ├── release.sh               # 一键发版：构建 → 归档 → 同步版本号
│   └── sync_version.py          # 同步 / 校验 version.json 与源码版本号
├── version.json                 # 应用内更新检查读取的版本信息（由脚本同步）
├── app/release/                 # 本地 APK 留档（已 gitignore，不入库；分发走 GitHub Releases）
└── app/src/main/java/com/example/waterreminder/
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
- `local.properties` 也不再入库（含本机 SDK 绝对路径），clone 后用 Android Studio 打开会自动生成
- 换行符由 `.gitattributes` 统一：文本文件一律 LF 入库，`*.bat` 保持 CRLF。
  Linux CI 上 `./gradlew` 若是 CRLF 会直接报 `bash\r: No such file or directory`
- Android 13+ 需要授予通知权限；Android 12+ 需要「闹钟和提醒」权限，否则提醒不准时
- 保活服务使用 `specialUse` 类型前台服务（`dataSync` 在 Android 15 上有 6 小时强制停止限制）
- 部分国产 ROM 需要手动允许自启动与后台运行，否则提醒会被杀掉
- APK 不再入库，改由 GitHub Release asset 分发（`.github/workflows/release.yml`）；
  `app/release/` 只作本地留档并已加入 `.gitignore`。迁移前 Pages 上的旧直链（`app/release/*.apk`）会随之失效

## 许可证

本项目基于 [MIT License](LICENSE) 开源。
