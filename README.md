# 喝水提醒 WaterReminder

一个轻量的 Android 喝水提醒应用。按小时提醒你喝水，记录每一杯，并按饮品类型折算真实补水量。

Kotlin + Jetpack Compose 编写，无广告、无账号、无联网上传 —— 只有检查更新时会向 GitHub Pages
请求一次静态的版本清单（`docs/version.json`）。

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
- 长按删除单条记录，或一键清空当日记录

**更新**
- 启动时检查版本清单，有新版本时弹窗提示，确认后下载 APK 并校验 SHA-256 再安装
- 自动检查最多每 12 小时一次；首页底部的「关于」里可以手动检查
  （手动检查忽略节流，并区分「已是最新」与「没查到」）

## 下载

官网：<https://os233.github.io/WaterReminder/>（首页 / 下载 / 使用文档 / 更新日志 / 隐私政策）

最新版本从 [GitHub Releases](https://github.com/os233/WaterReminder/releases) 获取。
版本信息有两个数据源，分工固定：

- **App 内更新**读 `docs/version.json` —— Pages 上的静态发布 Manifest：用 `versionCode` 比大小，
  取 `apkUrl` 下载，按 `sha256` 校验。**不经过 Releases API**（原因见「应用内更新怎么工作」）
- **官网**的版本号与更新日志默认现读 Releases API，Release 一发布页面内容就跟着变；
  配额耗尽等失败情况会退到同域的 `docs/version.json` 兜底

## 使用说明

**首次启动**

- Android 13+ 会弹出通知权限申请，允许后提醒才能显示
- Android 12+ 需在系统设置中授予「闹钟和提醒」权限，否则提醒不准时
- 部分国产 ROM 需手动允许自启动与后台运行，否则提醒会被杀掉

**日常使用**

- **记录**：首页点饮品按钮记一杯，或通过「自定义」输入任意毫升数，按水合系数折算入当日总量
- **目标**：点首页的目标数字修改每日目标（1000–4000 ml，步长 100）
- **提醒**：首页进入「设置提醒」，选 1 / 2 / 3 小时间隔；可开启夜间免打扰并设起止时间（支持跨午夜）
- **历史**：从首页进入历史页，查看最近 7 天汇总与月历；长按删除单条记录，或一键清空当日记录
- **更新**：启动时自动检查，有新版本时在弹窗里点「立即更新」即可（自动检查最多每 12 小时一次）

## 环境要求

| 项 | 版本 |
| --- | --- |
| JDK | 17 或 21（Gradle 8.11.1 只支持到 Java 23，**24+ 不可用**；CI 固定 17） |
| Android SDK | platform `android-36`（CI 另装 `build-tools;36.0.0`） |
| Gradle | 8.11.1（wrapper 已内置，无需手动装） |
| AGP / Kotlin / KSP | 8.10.1 / 2.0.21 / 2.0.21-1.0.28 |
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

版本号写在 `app/build.gradle.kts`；**发布 Manifest 是 `docs/version.json`** —— App 内更新
与官网都读它，机器字段由 CI 在发版时写回，人工只写 `changelog`。GitHub Release 负责托管
APK asset 与 Release 页面，页面上的说明由 CI 从 `changelog` 生成（不另写一份）。

```text
      app/build.gradle.kts   (versionCode / versionName，唯一权威来源)
                │
            git tag v1.5.0
                │
                ▼
        GitHub Actions (release.yml)
                │
      ┌─────────┼──────────┐
      ▼         ▼          ▼
   构建签名 APK  算 SHA-256  创建 Release
      └─────────┼──────────┘
                │  ① 先上传 asset
                ▼
    写回 docs/version.json 并推 master
    (versionCode / versionName / apkUrl / sha256)
                │  ② 后更新 Manifest
                ▼
        GitHub Pages (静态托管，无配额)
                │
        ┌───────┴────────┐
        ▼                ▼
   Android App         官网
        │                │
   读 version.json   读 Releases API
   比 versionCode   (失败退到 version.json)
        │
        ▼
  下载 Release 里的 APK → 校验 SHA-256 → 交给安装器
```

```bash
# 1. 改 app/build.gradle.kts 里的 versionCode 与 versionName（versionCode 必须严格递增）
# 2. 手写 docs/version.json 的 changelog —— 这是它唯一人工维护的字段
# 3. 本地构建 + 归档 + 校验（不会改 version.json 的机器字段，原因见下）
./scripts/release.sh
# 4. 提交并推 master（只加这两个文件；不要 git add -A，会扫进无关残留）
#    此时 version.json 的版本号还是旧的，App 不会提示更新，也不会 404
git add app/build.gradle.kts docs/version.json
git commit -m "release: 发布 <版本号>" && git push origin master
# 5. 打 tag 并推送 → CI 构建 APK、创建 Release、上传 asset，然后把 version.json 的
#    versionCode / versionName / apkUrl / sha256 写回并推 master
git tag v<版本号> && git push origin v<版本号>
```

⚠️ **顺序不能反**：先推 `master`，再推 tag —— tag 要打在已经推到 master 的提交上。
CI 写回时会先 `git fetch` + `git rebase origin/master` 再推，所以构建那几分钟里 master
又被推了提交（发版后补文档很常见）也不会丢写回。不加这两步的话推送会被拒，结果是
Release 已经建好、Manifest 却没更新 —— 所有客户端永远收不到这个版本，而且不报任何错。

**为什么机器字段不能本地写**：Manifest 一旦指向新版本，App 就会让用户去下载那个地址。
如果这时 asset 还没上传，用户点更新直接 404。让 CI 在 asset 就位**之后**写回，
这个窗口就不存在了 —— 代价只是「推完 tag 等 CI 跑完，App 才开始提示更新」。

`scripts/release.sh` 依次做：校验 `keystore.properties` 存在 → `./gradlew assembleRelease` →
产物归档到 `app/release/`（本地留档，不入库）→ 版本号一致性校验 → apksigner 签名自检
（找不到工具就跳过并提示）。**它不会自动 commit / push** —— 发布是对外动作，留给人确认。

**务必始终使用同一个密钥库**。签名不一致会导致老用户无法覆盖安装，只能卸载重装。

### 应用内更新怎么工作

App 读 `https://os233.github.io/WaterReminder/version.json` —— Pages 上的静态发布 Manifest：

- **版本比较用 `versionCode`**，与装机版本的 `PackageInfo.longVersionCode` 直接比大小
  （API 28 以下回退到 `versionCode`）。符合 Android 的版本模型，也不依赖 `versionName`
  的数字段恰好有序
- 下载完成后算安装包的 SHA-256 与 `sha256` 字段比对，不一致就删掉重来，不交给安装器
- 自动检查最多每 12 小时一次；手动检查忽略节流，并明确区分「已是最新」和「没查到」
- 任何失败（断网、HTTP 错误、字段缺失或非法）都只当作「没有更新」，不影响使用
- `forceUpdate` 为 `true` 时弹窗没有「稍后」按钮

**为什么不读 GitHub Releases API**：未认证的 API 只有 60 次/小时，而且配额**按出口 IP
共享** —— 公司、校园网、运营商 NAT 这类共用出口很容易被别人的请求用光，拿到 403 之后
更新检查就静默失败了。静态文件在 CDN 上没有配额，App 也不必去猜 API 的响应结构。

⚠️ **字段结构是兼容契约**：已发布的 1.4.0 也用 Gson 按顶层字段反序列化这个文件。
缺字段会被静默当成 0/null（表现为「永远没有更新」）。所以只能**新增**字段 ——
不能改名、删字段，也不能把它们挪进嵌套对象。

### docs/version.json 是发布 Manifest，不能删

它有两个消费者：

**① 应用内更新。** 所有版本都读它 —— 包括 1.4.0 及更早的老客户端（它们的请求 URL 就是它）。
有没有新版本、去哪里下载、下载后拿什么摘要校验，全部来自这个文件。

**② 官网的静态兜底数据源。** 官网默认读 GitHub Releases API，但未认证的 API 只有
**60 次/小时，而且配额按出口 IP 共享** —— 共用出口很容易被别人的请求用光，访客就会看到
「获取失败」。所以 API 失败时 `site.js` 会退回来读同域的 `version.json`
（同域、无配额、永远可达）。因此 **`changelog` 要认真写** —— API 失败时官网直接拿它当更新说明显示。

字段与维护者：

| 字段 | 谁写 | 说明 |
| --- | --- | --- |
| `versionCode` | CI | 机器比较依据，必须与 `build.gradle.kts` 一致 |
| `versionName` | CI | 给人看的版本号 |
| `apkUrl` | CI | 指向 Release asset，必须是 https |
| `sha256` | CI | 该 asset 的摘要（64 位小写十六进制）；为空时 App 跳过下载后校验 |
| `changelog` | **人工** | App 更新弹窗与官网兜底都读它 |
| `forceUpdate` | 人工 | 为 `true` 时弹窗没有「稍后」按钮 |

⚠️ 因此**不要删这个文件**，也不要删 `scripts/sync_version.py` 里的 `VERSION_JSON`。

### 版本号一致性校验

```bash
python scripts/sync_version.py --check    # 只校验不写文件；CI 也跑这个
python scripts/sync_version.py --expect-tag v<版本号>    # 断言 tag 与 versionName 一致；发版工作流用
```

校验的不变量：

- `docs/version.json` 的 versionCode **不能比源码还新**（多半是升了它却忘了升 `app/build.gradle.kts`）
- `apkUrl` 必须是 https，且文件名必须与 versionName 匹配
- `sha256` 非空时必须是 64 位小写十六进制（App 只认这个格式，写错会被当成「没有摘要」）
- `sha256` 缺失不算失败：本地发版时它是空的，CI 在 Release 建好后写回真实值
- `apkUrl` 指向 Releases 时脚本只给提示，**无法校验 asset 是否已上传** —— 不过发版流程里
  这一步由 CI 保证（先上传 asset，再改 Manifest），不需要人工确认

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
Pages 不只是展示 —— **App 的更新检查也读它**（`/version.json`），见「应用内更新怎么工作」。

### CI

| 工作流 | 触发 | 做什么 |
| --- | --- | --- |
| `.github/workflows/ci.yml` | push 到 master / 任何 PR / 手动 | 版本号校验 + `assembleDebug`（lint 目前只报告不拦截）。不需要任何密钥，fork 的 PR 也能安全跑 |
| `.github/workflows/release.yml` | push 形如 `v1.4.0` 的 tag 自动发版；手动触发保留，用于失败重跑（会覆盖已有 asset） | 校验 tag 与 versionName 一致 → 构建签名 APK → 校验签名 → 创建 Release 并上传 asset → 核对 asset 的 SHA-256 与本地产物一致 → 把机器字段写回 `docs/version.json` 并推 master |

tag 过滤器是 `v[0-9]*.[0-9]*.[0-9]*` 加一条排除 `!v*-*`。注意 glob 的 `*` 会吃掉后缀 ——
只写前一条的话预发布 tag（如 `v1.3.0-beta.1`）也会匹配进来，所以用 `!v*-*` 明确挡掉。
本仓库没有 beta 发布流程：预发布 tag 什么都不做，而不是「跑起来再失败」。

release 工作流需要在仓库 Settings → Secrets and variables → Actions 配好
`KEYSTORE_BASE64`（`base64 -w0 water_keystore.jks`）、`STORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`；
缺 `KEYSTORE_BASE64` 时它会在「还原签名配置」那一步明确报错退出，不会静默产出未签名包。

## 项目结构

```
├── AGENTS.md                    # 在本仓库工作的 AI 代理必须遵守的约束（边界、发版规则、环境版本）
├── .github/workflows/           # CI：ci.yml（版本号校验 + 编译）、release.yml（构建签名 APK 发到 Releases）
├── build.gradle.kts             # AGP / Kotlin / KSP 插件版本；app/build.gradle.kts 里是版本号与依赖
├── gradle/ · gradlew            # Gradle wrapper（8.11.1，只走 wrapper）
├── scripts/
│   ├── release.sh               # 一键发版：构建 → 归档 → 校验版本文件
│   └── sync_version.py          # 同步 / 校验版本文件与源码版本号（CI 用它写回 Manifest）
├── docs/                        # GitHub Pages 官网（Pages 源就是这个目录）
│   ├── index.html               # 首页
│   ├── download/ changelog/     # 下载页、更新日志（前端现读 Releases API）
│   ├── docs/ privacy/           # 使用文档、隐私政策
│   ├── assets/                  # style.css + site.js
│   └── version.json             # 发布 Manifest：App 内更新与官网都读它（机器字段由 CI 写）
├── app/release/                 # 本地 APK 留档（已 gitignore，不入库；分发走 GitHub Releases）
└── app/src/main/
    ├── AndroidManifest.xml      # 权限、组件声明（保活服务为 specialUse 前台服务）
    ├── res/                     # 图标、主题、colors、file_paths.xml
    └── java/com/example/waterreminder/
        ├── MainActivity.kt          # 入口：通知 / 精确闹钟 / 电池优化提示、保活服务、NavHost、更新弹窗
        ├── WaterReminderApp.kt      # Application（空实现，仅在清单中声明）
        ├── data/
        │   ├── WaterRecord.kt       # Room 实体
        │   ├── WaterRecordDao.kt    # 查询与统计
        │   ├── WaterDatabase.kt     # 数据库与迁移
        │   ├── DrinkType.kt         # 饮品类型与水合系数
        │   ├── UserPrefs.kt         # SharedPreferences（每日目标）
        │   └── remote/
        │       ├── UpdateChecker.kt # 读 Pages 的 version.json 检查更新、下载并校验 SHA-256 后安装
        │       └── UpdateInfo.kt    # 一次可用更新的数据（对应 version.json 的顶层字段）
        ├── notification/
        │   ├── AlarmManagerHelper.kt # 闹钟调度、免打扰判断
        │   ├── AlarmReceiver.kt     # 提醒触发
        │   ├── BootReceiver.kt      # 开机恢复
        │   └── KeepAliveService.kt  # 前台保活服务
        └── ui/
            ├── WaterReminderScreen.kt # 首页
            ├── HistoryScreen.kt     # 历史与统计
            └── theme/               # 主题配色
```

### 数据模型

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

## 技术栈

- Kotlin 2.0.21 + Jetpack Compose（Compose BOM 2024.09.03）+ Material 3
- Room 2.6.1（KSP 注解处理）
- Navigation Compose 2.8.4
- OkHttp 4.12.0 + Gson 2.10.1（Gson 只取 `JsonParser` 逐字段校验，不做对象反序列化）
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
- 仓库没有测试套件，**应用内更新的「下载 → 校验 → 安装」只能装到设备上手动验证**。
  检查 `shared_prefs/update_prefs.xml` 里出现 `last_check_at` 只能证明请求到了 Manifest，
  不证明版本比较与摘要校验逻辑对
- 仓库 Settings → Pages 的源必须是 `master` 分支的 `/docs` 目录 —— 官网在 `docs/`，
  `docs/version.json` 也靠这个映射，才在 App 与官网读的那个 URL 上可达

## 许可证

本项目基于 [MIT License](LICENSE) 开源。
