# AGENTS.md

本文件约束在本仓库工作的 AI 代理。

核心原则:**完整完成请求的任务,让真实需求驱动复杂度。** 不做投机性防御,
不搞范围蔓延,不用额外流程表演勤奋。以下是这些原则在本仓库的具体化。

## 约束的强度

- **硬约束**:以「必须」「禁止」「不得」表述的条目。违反即视为任务未完成,
  不得以"看起来无害""顺手"为由放宽。
- **默认做法**:以「默认」「优先」表述的条目。没有具体理由就照做;
  偏离时必须在汇报里说明理由。
- 冲突时以更严的为准:用户的明确指示可以放宽默认做法,但**不得放宽
  「本仓库硬边界」里涉及密钥与隐私的条款**。真遇到冲突,先说明再动。

## 停止阶梯

选实现、加机制、扩验证时,按此阶梯决策:

1. **先弄清当前职责**:请求的结果、明确边界、必须保留的既有保证(见「代码里的
   既有保证」)。改动前追踪受影响的调用方与失败路径,数据、测试、文档的连带
   修改一并完成。"计划"或"更简单的子集"不等于完成请求。
2. **从直接方案开始**:优先复用项目现有代码、平台/标准库能力、已装依赖
   (如 OkHttp、Room)。比较真实行为与失败处理即可,不必穷举生态。
3. **只为具体缺口扩展**:说清直接方案覆盖不了哪个输入、消费者或失败路径,
   在负责该问题的层级补齐。仅凭"未来可能需要"不构成理由。
4. **按实际效果评判防御**:说清机制检测什么、拒绝/恢复/诊断改变什么。
   没有落地价值的不加;掩盖失败、重复副作用、妨碍正常使用的防御要修,
   哪怕修复要加代码。
5. **验证后收尾**:用「项目检查」列出的既有检查验证受影响的行为。结果存在、
   证据支持、无已知范围内阻塞,即结束。不要再加一轮审计循环来满足本文件。

## 任务模式

- `review` / `answer` / `monitor`:**只读**。除非用户明确授权,不得改动任何文件。
- `change`:只做请求的工作及其必要后果。不得重构无关代码,不得顺手"优化"。
- 提交前必须看 `git status`:只提交任务相关文件。不相关的未跟踪残留
  (IDE 生成的配置、查 CI / 接口的临时 JSON 等)不得被 `git add -A` 扫进提交;
  一次性草稿用完即删,会反复出现的本地文件进 `.gitignore`。
- 必要工作不得越过明确的文件锁或更窄的边界;确需越界,先说明再动。

## 本仓库硬边界

### 密钥与隐私

- **禁止提交** `keystore.properties`、`*.jks`、`local.properties`;
  `keystore.properties.example` 只放占位值,不得把真实口令或本机路径填进去提交。
- **禁止回显**以下任何值,只描述其存在与用途:`keystore.properties` 里的
  `storeFile` / `storePassword` / `keyAlias` / `keyPassword`、密钥库文件内容、
  CI Secrets(`KEYSTORE_BASE64`、`STORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`)。
  `KEYSTORE_BASE64` 是密钥库的等价物 —— base64 不是加密,同样按密钥对待。
- 脱敏适用于**一切输出渠道**:汇报、对话、日志、提交信息、文档、issue / PR 评论、
  截图。密钥、token、口令一律用 `***` 或 `<redacted>` 替代。
- 本机绝对路径与设备标识(用户目录、SDK / JDK 路径、`adb` 设备串号)同样按敏感信息
  对待:只写进工作区 `.workbuddy-ai/memory/`,不得写进入库文件或对外输出。
  **但口令、token、密钥(含 base64 形式)连 memory 也不得写**,也不得落进任何临时
  文件 —— 它们只存在于 `keystore.properties` 与 CI Secrets 里。
- **贴输出前先扫一遍**:把脚本 / 构建 / 设备的输出粘进任何对外内容(汇报、issue、
  PR 评论、文档)之前,先检查有没有带出本机路径或设备串号,该换成相对路径或
  `***` 再贴。已知会带出本机路径的地方:`app/build.gradle.kts` 里 `packageRelease`
  的签名校验消息(打印 `keystore.properties` 的绝对路径),以及 Gradle / AGP /
  `adb` 自身的报错与日志 —— 这类输出不得原样外发。
- 用户数据(饮水记录、`water_database`、`shared_prefs`)属于隐私:要展示其内容时
  先脱敏,或改用构造的样例数据。
- **发现泄漏必须立即处理**:停止继续扩散并告知用户,由用户决定是否轮换口令或重建
  密钥库;代理不得自行更换密钥库、不得删改历史提交 —— 换库意味着老用户只能卸载
  重装(见 README「发布流程」),是不可逆的对外影响。

### 版本与发版

- `versionCode` 必须严格递增(脚本只能防版本文件回退,跨版本递增由发版人保证)。
- `docs/version.json` 是**发布 Manifest**,App 内更新与官网都读它。它的机器字段
  (`versionCode`/`versionName`/`apkUrl`/`sha256`)由 CI 在 Release 建好后写回,
  不得手改;唯一手写的是 `changelog` 与 `forceUpdate`。
  **本地发版不得提前改机器字段** —— 那会让 Manifest 指向一个还没上传的 asset,
  老客户端点更新直接 404。字段结构是**兼容契约**(已发布的旧客户端按顶层字段
  反序列化这个文件),只能新增,不得改名、删字段或挪进嵌套对象。
- Release 缺 `keystore.properties` 时在 `packageRelease` 阶段失败是**刻意设计**
  (避免产出装不上的未签名 APK),**不得"修复"**。
- `scripts/release.sh` 不自动 commit / push 也是**刻意设计**。未经用户明确确认,
  代理不得 push、不得发版。**推形如 `v1.4.0` 的 tag 即发版**(release 工作流会自动
  构建签名 APK 并创建 Release);推 `master` 则上线 Pages 官网(`docs/`)。
  两者都是对外动作,必须分别确认,顺序见 README「发布流程」。

### 文件与目录

- 换行符以 `.gitattributes` 为准:文本一律 LF 入库,`*.bat` 为 CRLF;
  尤其 `gradlew` 必须保持 LF(CRLF 会让 Linux CI 直接挂)。
- `gradlew` 与 `scripts/release.sh` 的 exec 位(100755)在 `git reset` 后会丢,
  丢了必须用 `git update-index --chmod=+x` 补回,否则 CI 上跑不起来。
- `.workbuddy-ai/`、`.zcode/` 是 AI 工具的本地状态(含本机路径),已 gitignore,
  不得提交。
- `app/release/` 是本地 APK 留档(已 gitignore,不入库),只有本地跑过发版脚本才
  存在;分发走 Release asset,除发版流程外不得动。别假设磁盘上有上一版 APK 可供
  比对 —— 校验线上包见「发布链路」。
- `docs/` 是 GitHub Pages 的站点根,只放静态文件,不得引入构建步骤。
  页面里的版本信息与更新日志优先现读 GitHub Releases API(未认证配额按出口 IP
  共享,容易被用光),失败时首页与下载页退到同域的 `docs/version.json` 兜底;
  除 `version.json` 外不得再另存版本清单。

## 发布链路

**权威来源**:版本号在 `app/build.gradle.kts`;**发布 Manifest 是 `docs/version.json`**
—— App 内更新与官网都读它。GitHub Release 托管 APK asset;Release 页面上的说明由 CI
从 `changelog` 生成,不另写一份。

- 推形如 `v1.4.0` 的 tag → `release.yml` 构建签名 APK → 创建 Release 并上传 asset →
  核对 asset 的 SHA-256 → **把 `versionCode`/`versionName`/`apkUrl`/`sha256` 写回
  `docs/version.json` 并推 master**。预发布 tag(如 `v1.5.0-beta.1`)被 `!v*-*`
  挡掉,不会触发 workflow。
- **顺序**:先推 `master`,再推 tag —— tag 要打在已经推到 master 的提交上。CI 的写回步骤
  会先 `git fetch` + `git rebase origin/master` 再推,所以构建那几分钟里 master 又被推了
  提交(发版后补文档很常见)也不会丢写回;不加这两步的话推送会被拒,结果是 Release 已建好、
  Manifest 却没更新 —— 所有客户端永远收不到这个版本,且不报任何错。
- **机器字段只能由 CI 写**,人工提前改会让 Manifest 指向一个还不存在的下载地址,
  老客户端点更新直接 404。本地发版只写 `changelog` 与 `forceUpdate`
  (见「本仓库硬边界」)。这条取代了以前「发版后人工确认 asset 已上传」的要求 ——
  CI 先上传 asset 再改 Manifest,顺序由机器保证。
- 校验线上包必须用 Releases API 里 asset 的 `digest`(GitHub 官方算好的 SHA-256),
  **不得真去下那 11MB**;本机代理会截断响应体,下载比哈希更不可靠。
- 本机没有 `gh` CLI。查 Release / asset 用
  `curl -sL https://api.github.com/repos/os233/WaterReminder/releases` + Python 解析。
- Pages 源是 `master` 分支的 `/docs` 目录。**改 Pages 源是纯手工操作**:Pages 设置
  没有可用 API,本机也没有任何 GitHub token —— 需要动源时只能让用户去网页点,
  代理不得反复尝试。

## GitHub Actions 的已知坑

以下是排查经验,不是约束,但决定往哪个方向查:

- **workflow「启动失败」不等于构建失败**:run 里没有任何 job、`name` 显示成
  **文件路径**(而不是 `name:` 的值)、conclusion=failure —— 三者同时出现就是
  GitHub 压根没解析成功这个 YAML,别去翻构建日志。
- 一次 push 若修改了某个 workflow 文件,GitHub 会重新注册它,注册失败就产生一个
  startup failure run —— 即使该 workflow 的 `on:` 不匹配这次 push。所以
  「推 master 却冒出 release 的失败 run」多半是 YAML 非法,不是被误触发发版
  (用 `git ls-remote --tags origin` 确认 tag 没变)。
- 块标量(`run: |`)里写多行内联脚本极易踩缩进:块内每行必须缩进到同一列。
  写多行 `python3 -c '...'` 时后续行回落到 0 列,会让块提前结束、后面几行被当成
  顶层内容 → YAML 非法。**宁可写成单行**(`release.yml` 曾因此写坏并阻断发版)。

## 代码里的既有保证(改代码时必须保住)

- **更新检查不得影响 App 运行**:断网、HTTP 非 2xx、JSON 结构不符、没有可用的
  APK asset、下载地址不是 https —— 一律只返回 `Failed`,不抛异常、不弹错误提示。
  自动检查必须**只在真问到结果时才写** `update_prefs.last_check_at`,否则一次断网
  会把重试也压掉 12 小时。
- **版本比较必须走 `compareVersionNames` 逐段比数字**,不得改成字符串比较 ——
  `"1.10.0" > "1.9.0"` 靠它成立。Release 里没有 `versionCode`,所以发版时
  `versionCode` 与 `versionName` 必须同序。
- **下载完必须先校验 SHA-256,再交给安装器**:摘要取自 Release asset 的 `digest`
  (只认 `sha256:` 前缀),不一致就删包并提示重试。不得为"少一步"删掉这段。
- Room 改实体**必须补 `Migration`**(当前 version 2,v1→v2 加了
  `drinkType`/`hydration`);**不得使用 `fallbackToDestructiveMigration`** ——
  饮水记录是用户唯一的数据。
- 提醒**必须**用 `setExactAndAllowWhileIdle` 调度,免打扰**必须**保留跨午夜
  (`start > end`)的分支;保活服务**必须**保持 `specialUse` 类型前台服务,
  不得换成 `dataSync`。
- SharedPreferences 的 key 是已发布客户端的持久状态,**不得改名**(改名等于清空
  老用户的设置):`user_prefs`、`water_reminder_prefs`、`update_prefs`。

## 环境与依赖约束

工具链版本是硬性约束。构建或脚本报错时先怀疑环境,不得靠改版本号、升依赖"修"过去:

| 组件 | 版本 | 约束 |
| --- | --- | --- |
| JDK | 17 或 21 | Gradle 8.11.1 只支持到 Java 23,**24+ 直接不可用**;CI 固定 17 |
| Android SDK | platform `android-36` | `compileSdk` / `targetSdk` 都是 36;CI 另装 `build-tools;36.0.0` |
| Gradle | 8.11.1 | 只走 wrapper(wrapper jar 已入库),不得手装、不得随手升版本 |
| AGP / Kotlin / KSP | 8.10.1 / 2.0.21 / 2.0.21-1.0.28 | 与 Gradle 8.11.1 配套,不要单独升级 |
| Python | 3.9+ | **只给 `scripts/` 用**,不参与构建 |

- **必须先设置 `JAVA_HOME`** 再跑 CLI 构建;未设置时 `./gradlew` 会直接报
  `JAVA_HOME is not set` —— 这是环境问题,不是代码坏了。本机具体路径见工作区
  `.workbuddy-ai/memory/`,不得写进本文件。
- Android SDK 路径通过 `ANDROID_HOME` / `ANDROID_SDK_ROOT` 或 `local.properties`
  指定;`local.properties` 不入库,不得把本机绝对路径写进任何入库文件。
- 依赖版本直接写在 `app/build.gradle.kts` 的 `dependencies` 块里(**仓库没有
  version catalog**);仓库来源固定在 `settings.gradle.kts` 的 `google()` /
  `mavenCentral()`。那里设了 `FAIL_ON_PROJECT_REPOS`,在模块里另写 `repositories`
  会直接构建失败,不得这么做。
- 新增第三方依赖前必须先说明用途,以及为什么现有能力(已装的 OkHttp / Room /
  Compose 等、平台 API)不够用;不得为省几行代码引库(见「本项目常见过度工程」)。
- `scripts/` 下的 Python 脚本**必须只用标准库**:CI 用裸 `python3` 直接跑
  `sync_version.py`,没有任何 pip 安装步骤。
- Java 源码与字节码级别固定 17(`compileOptions` 与 `kotlinOptions.jvmTarget`),
  `minSdk = 26`:用到的平台 API 必须在 API 26 起可用,更高的要按 `SDK_INT` 分支。

## 项目检查

验证受影响行为时使用这些既有检查,不另造:

| 检查 | 命令 |
| --- | --- |
| 编译 | `./gradlew assembleDebug`(JDK 17–23,推荐 17 或 21) |
| 版本一致性 | `python scripts/sync_version.py --check` |

- 改过 `.github/workflows/*.yml` 后、推送前,必须本地用 pyyaml 解析一遍
  (`yaml.safe_load`;YAML 1.1 会把 `on:` 解析成布尔 `True`,属正常现象)。
  装了 pyyaml 的解释器路径同样见工作区 `.workbuddy-ai/memory/`。
- 想确认应用内更新真的请求到了 GitHub:自动检查成功时才会写
  `shared_prefs/update_prefs.xml` 的 `last_check_at`,看这个文件即可。
- lint 目前只报告不拦截;仓库没有测试套件。不得为了"凑验证"新建测试脚手架,
  除非任务本身就是加测试。

## 本项目常见过度工程(明确不做)

- 不引入 DI 框架(Hilt/Koin)、不把 SharedPreferences 迁 DataStore、不把 OkHttp 换
  Retrofit、不给 Room 加仓库抽象层 —— 除非任务明确要求。
- 不加"以防万一"的重试、缓存、超时、抽象层。
- 不新建只为了"验证自己刚才改动"的检查器;要验证就用上面列的检查。
- 不虚构文件清单或验证证据来显得精确;不确定就查证或如实说。

## 汇报

- 必须报告结果与对应的验证证据;有未解决的阻塞要如实说,不得宣称完成。
- tradeoff / 警告 / 限制仅在用户要求或影响结果解读时给出,放在决策点上,
  不堆免责声明。
- 过程性流水账、自我保护的叙述不写进代码、提交信息和文档。
