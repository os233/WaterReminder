# AGENTS.md

本文件是 WaterReminder 仓库的协作规范，规定代码与数据兼容约束、开发边界及测试策略。所有在本仓库执行代码阅读、修改、构建、验证或发布工作的 AI 代理都必须遵守。

代理的任务模式、工作步骤和汇报规则见 [programs.md](programs.md)。本文件约束优先于 README 中的说明；发现两者矛盾时按本文件执行，并在同一任务中修正相关 README。约束中的「必须」「不得」「禁止」为硬要求；「默认」「优先」为无明确理由时应遵循的做法。用户明确指示可以放宽「默认」「优先」做法，但不得放宽涉及密钥与隐私的条款。

## 第一部分：约束条款

### 1. 安全、隐私与仓库卫生

**适用范围：** 所有文件读写、命令输出、构建与设备日志、对话、提交、文档、截图及外部协作内容。

- 不得提交 `keystore.properties`、`*.jks`、`local.properties`。`keystore.properties.example` 只允许占位值，不得含真实口令或本机路径。
- 不得回显 `keystore.properties` 中 `storeFile`、`storePassword`、`keyAlias`、`keyPassword` 的值、密钥库内容，或 CI Secrets `KEYSTORE_BASE64`、`STORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。`KEYSTORE_BASE64` 与密钥库等价，不能因其为 base64 而视为安全。
- 密钥、token、口令在任何渠道一律以 `***` 或 `<redacted>` 代替；不得写入 memory、日志或临时文件。发现泄漏时立即停止扩散并告知用户；不得自行轮换密钥库或改写历史提交，由用户决定后续处置。
- 用户目录、SDK/JDK 绝对路径及 `adb` 设备标识属于敏感信息，只能记录在工作区 `.workbuddy-ai/memory/`，不得进入仓库文件或对外输出。外发脚本、Gradle、AGP、adb 输出前，先检查并脱敏绝对路径和设备标识。
- 饮水记录、`water_database`、`shared_prefs` 属于用户数据。展示时先脱敏，或使用构造样例。
- `.workbuddy-ai/` 与 `.zcode/` 是本地工具状态，不得提交。提交前查看 `git status`，只暂存任务相关文件；不得用 `git add -A` 把 IDE、本机配置或临时数据带入提交。
- 换行遵循 `.gitattributes`：文本使用 LF，`*.bat` 使用 CRLF；`gradlew` 必须为 LF。Git 索引中的 `gradlew` 和 `scripts/release.sh` 执行位必须为 `100755`，不符时用 `git update-index --chmod=+x` 修正。
- `app/release/` 是本地 APK 留档，已忽略且不入库；除发版流程外不得改动。不得假定本机存在旧 APK 作为线上版本对比依据。

### 2. 开发范围、依赖与工具链

**适用范围：** 所有代码、资源、脚本、构建配置及依赖改动。

- 只实现用户请求及其必要后果；不顺手重构无关代码。优先使用现有 Android API、Kotlin、Compose、Room、OkHttp 等能力。不得为了少量代码引入依赖或抽象。
- 新增第三方依赖前，必须说明用途及现有能力为何不足。依赖版本写在 `app/build.gradle.kts`；本仓库没有 version catalog。仓库来源固定于 `settings.gradle.kts` 的 `google()` / `mavenCentral()`；因启用 `FAIL_ON_PROJECT_REPOS`，不得在模块内另设 repositories。
- `scripts/` 下 Python 脚本必须仅使用标准库。不得新增仅用于验证刚完成改动的检查器；本项目没有测试套件，不得为凑验证新建测试脚手架，除非任务本身要求增加测试。
- 工具链版本是硬约束；构建报错先核对环境，不能通过随意升级工具或依赖绕过：JDK 17 或 21（Gradle 8.11.1 不支持 Java 24+；CI 使用 17）；Android platform `android-36`，`compileSdk` 与 `targetSdk` 为 36；CI 安装 `build-tools;36.0.0`；Gradle 8.11.1 且只用仓库 wrapper；AGP 8.10.1、Kotlin 2.0.21、KSP 2.0.21-1.0.28；Python 3.9+ 仅供脚本使用。
- CLI 构建前必须设置 `JAVA_HOME`。Android SDK 由 `ANDROID_HOME` / `ANDROID_SDK_ROOT` 或本地 `local.properties` 指定；本机路径不得写入入库文件。Java 源码和字节码目标固定为 17；`minSdk = 26`，高于 API 26 的平台 API 必须按 `SDK_INT` 分支。

### 3. 持久数据与 Android 行为兼容

**适用范围：** Room 实体/数据库、SharedPreferences、提醒、日期展示及后台引导相关改动。

- Room 实体变更必须提供对应 `Migration`。当前数据库版本为 2，v1→v2 增加了 `drinkType` 与 `hydration`。不得使用 `fallbackToDestructiveMigration`；饮水记录是用户唯一数据。
- 已发布客户端依赖的 SharedPreferences 文件名/key 是持久化兼容面，不得改名或清空：`user_prefs`、`water_reminder_prefs`、`update_prefs`。
- 提醒必须由 `setExactAndAllowWhileIdle` 调度，不得改为 `setAlarmClock`。免打扰判断必须保留跨午夜（`start > end`）分支。不得引入常驻前台服务保活：既有实测表明它不能阻止 ROM 冻结，反而会妨碍闹钟投递。
- OPPO、realme、一加的后台白名单引导必须常驻显示；不得尝试探测授权后隐藏。不得把 `isIgnoringBatteryOptimizations()` 当成后台已放行的依据。
- 所有界面上的「今天」必须由 `ui/RememberToday.kt` 的 `rememberToday()` 提供：进入 `STARTED` 时立即刷新，可见期间每 60 秒轮询。不得把 `LocalDate.now()` 作为 `remember` 快照，也不得用等待至午夜的 `delay()` 刷新日期，因为设备深度睡眠时单调时钟不前进。

### 4. 应用内更新兼容契约

**适用范围：** `UpdateChecker.kt`、`UpdateInfo.kt`、下载/安装实现、`docs/version.json` 结构以及相关文件提供配置。改动这些部分时逐项核对本节。

- 更新唯一来源是 `https://os233.github.io/WaterReminder/version.json`（`UpdateChecker.VERSION_JSON_URL`）。不得改成 Releases API 或其他地址。
- `docs/version.json` 的现有顶层字段是已发布客户端兼容契约：只能新增字段，不得重命名、删除或移入嵌套对象。
- 必须使用 `JsonParser` 逐字段显式校验；不得直接用 Gson 反序列化更新对象，以免缺失字段静默变成 `0` / `null`。
- 版本比较必须使用整数 `versionCode`，本机版本取 `PackageInfo.longVersionCode`，API 28 以下回退到 `versionCode`；不得按 `versionName` 字符串排序。
- `apkUrl` 仅接受 HTTPS。`sha256` 仅在其为 64 位十六进制时有效；格式非法按无摘要处理，绝不拿未校验的字符串比较。
- 下载完成后必须先计算并核对 SHA-256，再交给安装器。摘要不一致必须删除下载包并提示重试。
- 安装包必须保存到应用外部私有目录：使用 `setDestinationInExternalFilesDir` 和 `getExternalFilesDir(DIRECTORY_DOWNLOADS)`；不得改到公共 Downloads。更改存储位置时必须同步核对 `res/xml/file_paths.xml` 的 `<external-files-path>`，确保 `FileProvider` 可访问。
- `ACTION_DOWNLOAD_COMPLETE` 接收器必须以 `RECEIVER_EXPORTED` 注册，不得改为 `RECEIVER_NOT_EXPORTED`。接收器必须先校验 `downloadId`，再向 `DownloadManager` 核实状态，并执行 SHA-256 校验。
- 更新信息检查遇到断网、非 2xx、JSON 结构错误或字段非法时只返回 `Failed`：不抛出到调用方、不弹错误、不影响其他功能。下载/安装失败按各自阶段提示；摘要校验失败须删包并提示重试。
- 自动检查只有真正得到检查结果时才写 `update_prefs.last_check_at`，以维持 12 小时节流；失败请求不得压住后续重试。

### 5. 版本、Manifest 与发布边界

**适用范围：** `app/build.gradle.kts` 版本号、`docs/version.json`、发布脚本、Git tags、GitHub Release、Pages 及相关 workflow。

- `versionCode` 必须严格递增，并与 `versionName` 同序；脚本只防版本文件回退，跨发布递增由发布人保证。2026-09-15 的 `0.0.1` 重置豁免已结束。重置造成旧 `1.4.0`（code 7）用户不能接收更低 code 的更新，Android 也会拒绝降级安装；此兼容影响已接受。
- `docs/version.json` 是发布 Manifest。`versionCode`、`versionName`、`apkUrl`、`sha256` 只能由 Release 建立后的 CI 写回；手动只维护 `changelog` 与 `forceUpdate`。本地不得提前改机器字段，以免客户端请求尚未上传的 asset。
- Release 缺少 `keystore.properties` 时在 `packageRelease` 阶段失败是有意设计，禁止绕过或改成生成未签名 Release APK。`scripts/release.sh` 不自动 commit/push 也是有意设计。
- 未经用户明确确认，不得 push、发版或执行其他对外发布动作。推送正式形如 `v1.4.0` 的 tag 会触发签名构建和 Release；推送 `master` 会发布 Pages。二者是独立的对外动作，必须分别获得确认，顺序遵循 README「发布流程」。
- Beta tag 使用 `v<versionName>-<后缀>`，源码 `versionName` 必须包含相同后缀。预发布 workflow 也会触发：Release 必须标记 prerelease；不得写回 `docs/version.json`；预发布说明写入带注释 tag，由 workflow 作为 Release 正文来源。Beta 与其正式版共用同一 `versionCode`，这是唯一递增豁免。Beta 不替代 Manifest 所指正式包。
- 正式 Release 正文取自 tag 指向提交中的 `docs/version.json`。只改 master 的 changelog 后重跑 workflow 不会更新已发布正文。确需补改时用 `git tag -f -a` 移动 tag 并强推该 tag，不要先删除 tag，以免 Releases tag 页面短暂 404。此操作仍属外部发布动作，需用户明确确认。
- Manifest 的机器字段必须指向真实存在的 Release。删除 Release 后不得手改 Manifest；应按正式发布流程处理。线上 APK 摘要从 Releases API asset 的 `digest` 核验，不得下载大体积 APK。
- Pages 根目录为 `master/docs`，只放静态文件，不得引入构建步骤。首页和下载页把 `version.json` 的 `versionName` 作为期望版本，再查询对应 Release；仅当 Release 与 `.apk` asset 都存在时显示下载。查不到时显示无可下载包并链接 Releases，不能回退展示 Manifest 中的旧版本或 apkUrl。更新日志 API 失败或列表为空时，可用带「本站清单」来源标记的兜底记录。不得在 `version.json` 外另存版本清单。
- 本机没有 `gh` CLI。查询 Release/asset 使用 `curl -sL https://api.github.com/repos/os233/WaterReminder/releases` 并用 Python 解析。Pages 设置不能通过可用 API 修改；需要更改 Pages 源时由用户在网页操作，代理不得反复尝试。

### 6. GitHub Actions 维护约束

**适用范围：** `.github/workflows/*.yml` 的修改、诊断与发布前检查。

- 修改 workflow 后、推送前必须用 PyYAML `yaml.safe_load` 本地解析。YAML 1.1 将 `on:` 解析为布尔 `True` 属正常现象。
- startup failure 若同时满足「没有 job」「run 名称显示文件路径而非 workflow 的 `name:`」「结论为 failure」，表示 GitHub 未能解析 YAML，应先查语法而非构建日志。修改 workflow 的 push 可能令 GitHub 重新注册该 workflow，即使其 `on:` 不匹配也可能出现 startup failure。
- `run: |` 多行脚本的行缩进必须一致。多行内联 `python3 -c` 容易因缩进让 YAML 块提前结束；优先改为可靠的单行命令或独立脚本。

## 第二部分：测试与验证策略

### 7. 总体原则

**适用范围：** 所有代码、资源、构建、脚本、文档和发布配置改动。

- 只运行与改动范围匹配的既有检查，不为「凑验证」增加新工具或脚手架。先确认 JDK、SDK 等环境满足约束，再判断构建错误是否由代码导致。
- 每项验证结论必须与实际证据相符。未运行、受环境阻塞或只能手工验证的项目要明确标为未验证；不得把局部成功描述为整条链路通过。
- 输出外发前按第 1 节脱敏。Gradle/AGP/adb 输出可能含本机绝对路径，不得原样贴出。

### 8. 按改动范围选择检查

| 改动范围 | 必需检查 | 可证明的范围与边界 |
| --- | --- | --- |
| Kotlin、Android 资源、依赖或构建逻辑 | `./gradlew assembleDebug`（先设置 `JAVA_HOME`；推荐 JDK 17 或 21） | 编译与资源处理成功；不等价于设备运行正确 |
| `docs/version.json`、版本同步脚本或发布相关元数据 | `python scripts/sync_version.py --check` | 检查版本字段、格式、HTTPS URL、版本名匹配、摘要格式及可用构建元数据；不要求 Manifest 版本等于当前源码版本 |
| `.github/workflows/*.yml` | PyYAML `yaml.safe_load` 解析 | 检查 YAML 可解析；不代表 GitHub Actions 执行成功 |
| 应用内更新下载、摘要校验、安装或其存储路径 | 设备手工验证下载 → 校验 → 安装 | 该链路没有自动化测试；未在设备走完整流程时，必须说明未实测 |
| 更新检查是否请求到 GitHub | 检查成功后查看设备 `shared_prefs/update_prefs.xml` 的 `last_check_at` | 只证明检查请求得到结果，不证明版本比较、下载、校验或安装正确；用户数据须脱敏 |
| 纯文档改动 | 检查链接、章节交叉引用、命令/字段名与现行约束一致；若触及版本 Manifest 规则，运行其校验命令 | 不要求无关 Android 构建；不得据此声称代码行为已验证 |

本仓库没有测试套件；lint 当前只报告、不拦截。除非任务本身明确增加测试，否则不要创建测试脚手架或新检查器。

### 9. 高风险行为的专项复核

以下项目在对应代码被触及时，除通用构建检查外还必须按契约复核；超出自动检查能力的部分须明确说明：

- **更新协议：** 检查 URL、顶层字段、逐字段校验、整数版本比较、HTTPS 与 SHA-256 格式处理、失败只返回 `Failed`、12 小时节流仅在成功取到结果后写入。
- **下载与安装：** 检查私有目录与 FileProvider 路径一致、广播注册为 `RECEIVER_EXPORTED`、按 downloadId/DownloadManager 状态复核、安装前 SHA-256 校验和失败删包。完整链路需设备手工走通。
- **Room 与偏好设置：** 实体变更检查 Migration 与数据库版本；确认没有 destructive migration；确认已发布偏好文件名/key 保持兼容。
- **闹钟与日期：** 检查 `setExactAndAllowWhileIdle`、跨午夜免打扰分支、无前台保活服务、OPPO 系引导常驻显示，以及 `rememberToday()` 生命周期刷新方案。
- **发布元数据：** 检查机器字段未被手工改动、版本名/code 顺序正确、beta 通道不写 Manifest。发布 tag/push 仍需单独授权。

## 维护本文件

当代码、CI、发布流程或兼容契约发生经确认的变化时，同步更新本文件及必要的 README 说明。只删除已被实现和证据明确取代的规则；遇到尚未确认的实现差异，先查证再改文档。保持规则有明确适用范围、具体行为与对应验证方式，避免写成无法执行的口号。
