# 喝水提醒 WaterReminder

一个轻量的 Android 喝水提醒应用。按小时提醒你喝水，记录每一杯，并按饮品类型折算真实补水量。

Kotlin + Jetpack Compose 编写，无广告、无账号、无联网上传（只在检查更新时请求一次 GitHub 的公开 API）。

项目官网（GitHub Pages）：<https://os233.github.io/WaterReminder/>

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
- 启动时自动检查 GitHub Releases，有新版本时弹窗提示，确认后下载 APK 并校验 SHA-256 再安装
- 自动检查最多每 12 小时一次；首页底部的「关于」里可以手动检查

## 下载

官网：<https://os233.github.io/WaterReminder/>（首页 / 下载 / 文档 / 更新日志 / 隐私政策）

最新版本从 [GitHub Releases](https://github.com/os233/WaterReminder/releases) 获取：

- App 启动时直接读 GitHub Releases API 判断有没有新版本，不经过 GitHub Pages
- 官网的下载链接与更新日志同样现读 Releases API，Release 一发布页面内容就跟着变；
  配额耗尽等失败情况会退到同域的 `docs/version.json`（见下）

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
- **更新**：启动时自动检查，有新版本时在弹窗里点「立即更新」即可（自动检查最多每 12 小时一次）

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

版本号的权威来源只有 `app/build.gradle.kts` 一处；Release 由 tag 触发 CI 自动创建，
应用内更新和官网都直接读这个 Release，没有需要单独维护的版本清单。

```bash
# 1. 改 app/build.gradle.kts 里的 versionCode 与 versionName（versionCode 必须严格递增）
# 2. 本地一键构建 → 归档 → 同步版本文件与元数据
./scripts/release.sh
# 3. 打 tag 并推送 → CI 自动构建签名 APK、创建 Release 并上传 asset
#    （tag 必须打在 versionName 一致的提交上，工作流第一步会校验）
git tag v<版本号> && git push origin v<版本号>
# 4. 等 CI 跑完，在 Release 页面把发布说明写成「新增 / 优化 / 修复」分段
#    App 的更新弹窗与官网更新日志都直接读它
# 5. 补 docs/version.json 的 changelog（老客户端与官网兜底都要读它，见下），然后提交并推 master
git add -A && git commit -m "release: 发布 <版本号>" && git push origin master
```

`scripts/release.sh` 依次做：校验 `keystore.properties` 存在 → `./gradlew assembleRelease` →
产物归档到 `app/release/`（本地留档，不入库）→ 同步 `docs/version.json` 与
`app/release/output-metadata.json` → 版本号一致性校验 → apksigner 签名自检（找不到工具就跳过并提示）。
**它不会自动 commit / push** —— 发布是对外动作，留给人确认。

**务必始终使用同一个密钥库**。签名不一致会导致老用户无法覆盖安装，只能卸载重装。

### 应用内更新怎么工作

App 启动时请求 `https://api.github.com/repos/os233/WaterReminder/releases/latest`，取 `tag_name`
当版本号、`body` 当更新说明、assets 里第一个 `.apk` 当下载地址、该 asset 的 `digest` 当 SHA-256：

- 版本比较按数字段逐段比（`1.10.0` > `1.9.0`），不用字符串比较。GitHub 的 Release 里没有
  Android 的 `versionCode` 字段，所以用 `versionName` 的数字段排序，发布时保证两者同序即可
- 下载完成后先算安装包的 SHA-256 与 `digest` 比对，不一致就删掉重来，不交给安装器
- 自动检查最多每 12 小时一次（未认证的 GitHub API 配额只有 60 次/小时）
- 任何失败（断网、HTTP 错误、JSON 结构不符、没有 APK asset）都只是「没有更新」，不影响使用
- Release 说明里写 `<!-- force-update -->` 会变成强制更新弹窗（没有「稍后」按钮）

### docs/version.json 有两个用途，不能删

1.5.0 起应用内更新改读 GitHub Releases API，`docs/version.json` 不再是 App 的更新源，
但它仍然承担两件事：

**① 服务还没升级的老客户端。** 1.5.0 之前的已安装版本仍会请求
`https://os233.github.io/WaterReminder/version.json`（Pages 源设为 `master` 的 `/docs` 目录，
该 URL 正好映射到 `docs/version.json`）。

**② 官网的静态兜底数据源。** 官网默认读 GitHub Releases API，但未认证的 API 只有
**60 次/小时，而且配额按出口 IP 共享** —— 公司、校园网、运营商 NAT 这类共用出口很容易
被别人的请求用光，访客就会看到「获取失败」。所以 API 失败时 `site.js` 会退回来读同域的
`version.json`（同域、无配额、永远可达）。

由此带来两条约定：

- **`changelog` 要认真写**。它不只是给老客户端看的 —— API 失败时官网会直接拿它当更新说明显示。
- 兜底数据里**没有 APK 大小和 SHA-256**（`version.json` 没这两项），走兜底时下载页会缺这两个值；
  版本号和下载链接不受影响。

⚠️ 因此**不要删这个文件**，也不要删 `scripts/sync_version.py` 里的 `VERSION_JSON`。

### 版本号一致性校验

```bash
python scripts/sync_version.py --check    # 只校验不写文件；CI 也跑这个
python scripts/sync_version.py --expect-tag v1.4.0    # 断言 tag 与 versionName 一致；发版工作流用
```

校验的不变量：

- `docs/version.json` 的 versionCode **不能比源码还新**（多半是升了它却忘了升 `app/build.gradle.kts`）
- `apkUrl` 的文件名必须与 versionName 匹配
- `apkUrl` 指向 Releases 时脚本只给提示，**无法校验 asset 是否已上传** —— 发版后要自己确认
  Release 里有这个文件，否则老版本客户端的更新会 404

允许 `docs/version.json` 落后于源码 —— 开发中先升源码版本号是正常的。

### GitHub Pages

Pages 源为 `master` 分支的 `/docs` 目录，站点就是 `docs/` 下的静态文件（无构建步骤）：

| 路径 | 内容 |
| --- | --- |
| `/` | 首页：功能、水合系数、最新版本 |
| `/download/` | 下载页：最新版本、APK 直链、SHA-256 |
| `/docs/` | 使用文档与常见问题 |
| `/changelog/` | 更新日志，前端直接读 GitHub Releases API |
| `/privacy/` | 隐私政策 |

`docs/assets/site.js` 在浏览器里请求 GitHub 的公开 API，所以版本信息与更新日志都不需要手工同步 ——
Release 一发布，页面内容就跟着变。但未认证的 API 只有 60 次/小时，且配额**按出口 IP 共享**
（公司、校园网、运营商 NAT 下很容易被别人的请求用光），所以首页与下载页在 API 失败时
会**退回来读同域的 `version.json`**；更新日志页没有兜底源，失败时显示提示 + Releases 链接。
Pages 只做展示，App 的更新检查完全不经过它。

### CI

| 工作流 | 触发 | 做什么 |
| --- | --- | --- |
| `.github/workflows/ci.yml` | push 到 master / 任何 PR / 手动 | 版本号校验 + `assembleDebug`（lint 目前只报告不拦截）。不需要任何密钥，fork 的 PR 也能安全跑 |
| `.github/workflows/release.yml` | push `v*.*.*` tag 自动发版（预发布 tag 如 `v1.3.0-beta.1` 不触发）；手动触发保留，用于失败重跑（会覆盖已有 asset） | 校验 tag 与 versionName 一致 → 构建签名 APK → 校验签名 → 创建 Release 并上传 asset → 核对 asset 的 SHA-256 与本地产物一致 |

release 工作流需要在仓库 Settings → Secrets and variables → Actions 配好
`KEYSTORE_BASE64`（`base64 -w0 water_keystore.jks`）、`STORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`；
没配的话它会在第一步明确报错退出，不会静默产出未签名包。

## 项目结构

```
├── .github/workflows/           # CI：ci.yml（版本号校验 + 编译）、release.yml（构建签名 APK 发到 Releases）
├── scripts/
│   ├── release.sh               # 一键发版：构建 → 归档 → 同步版本号
│   └── sync_version.py          # 同步 / 校验版本文件与源码版本号
├── docs/                        # GitHub Pages 官网（Pages 源就是这个目录）
│   ├── index.html               # 首页
│   ├── download/ changelog/     # 下载页、更新日志（前端现读 Releases API）
│   ├── docs/ privacy/           # 使用文档、隐私政策
│   ├── assets/                  # style.css + site.js
│   └── version.json             # 老客户端过渡 + 官网兜底数据源（见「docs/version.json」）
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
    │       ├── UpdateChecker.kt     # 读 GitHub Releases API 检查更新、下载安装
    │       └── UpdateInfo.kt        # 一次可用更新的数据 + 版本号比较
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
- 仓库 Settings → Pages 的源必须是 `master` 分支的 `/docs` 目录 —— 官网在 `docs/`，
  过渡用的 `docs/version.json` 也靠这个映射才在老 URL 上可达

## 许可证

本项目基于 [MIT License](LICENSE) 开源。
