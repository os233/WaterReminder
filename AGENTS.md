# AGENTS.md

本文件约束在本仓库工作的 AI 代理。

核心原则:**完整完成请求的任务,让真实需求驱动复杂度。** 不做投机性防御,
不搞范围蔓延,不用额外流程表演勤奋。以下是这些原则在本仓库的具体化。

## 停止阶梯

选实现、加机制、扩验证时,按此阶梯决策:

1. **先弄清当前职责**:请求的结果、明确边界、必须保留的既有保证。改动前
   追踪受影响的调用方与失败路径,数据、测试、文档的连带修改一并完成。
   "计划"或"更简单的子集"不等于完成请求。
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

- `review` / `answer` / `monitor`:只读;除非用户明确授权,不改任何文件。
- `change`:只做请求的工作及其必要后果。不重构无关代码,不顺手"优化"。
- 提交前先看 `git status`:只提交任务相关文件,不相关的未跟踪残留(IDE
  生成的配置、查 CI/接口的临时 JSON 等)不要被 `git add -A` 扫进提交;
  一次性草稿用完即删,会反复出现的本地文件进 `.gitignore`。
- 必要工作不越过明确的文件锁或更窄的边界;确需越界,先说明再动。

## 本仓库硬边界

- **永不提交** `keystore.properties`、`*.jks`、`local.properties`。
- 敏感信息一律脱敏后再输出:`keystore.properties`、`*.jks`、`local.properties`
  的内容只描述存在与用途,不回显明文密码、口令、别名;汇报、日志、提交
  信息、文档中出现的密钥、token、口令,用 `***` 或 `<redacted>` 替代。
- `versionCode` 严格递增(脚本只能防版本文件回退,跨版本递增由发版人保证)。
  `docs/version.json` 的 `versionCode`/`versionName`/`apkUrl` 由 `scripts/sync_version.py`
  同步,不要手改这三项绕过校验;`changelog` 仍要手写,脚本不动它。
  这个文件只是给 1.5.0 之前的老客户端过渡用,不是应用内更新的来源 ——
  更新信息一律以 GitHub Release 为准。
- Release 缺 `keystore.properties` 时在 `packageRelease` 阶段失败是
  **刻意设计**(避免产出装不上的未签名 APK),不要"修复"它。
- `scripts/release.sh` 不自动 commit/push 也是**刻意设计**;未经用户明确
  确认,代理不得 push 或发版。**推 `v*.*.*` tag 即发版**:release 工作流会自动
  构建签名 APK、发布 Release;推 `master` 则上线 Pages 官网(`docs/`)。
  两者都是对外动作,须分别确认,顺序见 README「发布流程」。
- 换行符以 `.gitattributes` 为准:文本一律 LF 入库,`*.bat` 为 CRLF;
  尤其 `gradlew` 必须保持 LF(CRLF 会让 Linux CI 直接挂)。
- `.workbuddy-ai/`、`.zcode/` 是 AI 工具的本地状态(含本机路径),已 gitignore,
  不要提交。`gradlew` 与 `scripts/release.sh` 的 exec 位(100755)在 `git reset`
  后会丢,丢了要用 `git update-index --chmod=+x` 补回来,否则 CI 上跑不起来。
- `app/release/` 是本地 APK 留档(已 gitignore,不入库);分发走 GitHub Release
  asset,除发版流程外不要动。该目录只有本地跑过发版脚本才存在,别假设磁盘上
  有上一版 APK 可供比对 —— 校验线上包见「发布链路」。
- `docs/` 是 GitHub Pages 的站点根,只放静态文件,不要引入构建步骤;
  页面里的版本信息、更新日志一律现读 GitHub Releases API,不要另存一份。

## 发布链路

版本信息的唯一来源是 **GitHub Release**,不是仓库里的任何文件。

- 推 `v*.*.*` tag → `release.yml` 构建签名 APK → 创建 Release 并上传 asset →
  核对 asset 的 SHA-256。应用内更新(读 `/releases/latest`)与官网 `docs/`
  (前端现读 Releases API)都以它为准,没有需要单独维护的版本清单。
- `docs/version.json` 的 `apkUrl` 指向 Release asset,所以发版后必须确认 Release 里
  真有这个文件名,否则还在跑 1.5.0 之前的老客户端更新会 404。将来这个文件连同
  `sync_version.py` 里的 `VERSION_JSON` 一起删。
- 校验线上包用 Releases API 里 asset 的 `digest`(GitHub 官方算好的 SHA-256),
  **不要真去下那 11MB**;本机代理会截断响应体,下载比哈希更不可靠。
- 本机没有 `gh` CLI。查 Release / asset 用
  `curl -sL https://api.github.com/repos/os233/WaterReminder/releases` + Python 解析。
- Pages 源是 `master` 分支的 `/docs` 目录。**改 Pages 源是纯手工操作**:
  Pages 设置没有可用 API,本机也没有任何 GitHub token —— 需要动源时只能让用户
  去网页点,代理不要反复尝试。

## GitHub Actions 的坑

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

## 代码里的既有保证(改代码时不要破坏)

- **更新检查永远不影响 App 运行**:断网、HTTP 非 2xx、JSON 结构不符、没有可用的
  APK asset、下载地址不是 https —— 一律只返回 `Failed`,不抛异常、不弹错误提示。
  自动检查**只在真问到结果时才写** `update_prefs.last_check_at`,否则一次断网会把
  重试也压掉 12 小时。
- **版本比较按数字段逐段比**(`compareVersionNames`),不要改成字符串比较 ——
  `"1.10.0" > "1.9.0"` 靠它成立。Release 里没有 `versionCode`,所以发版时
  `versionCode` 与 `versionName` 必须同序。
- **下载完先校验 SHA-256,再交给安装器**:摘要取自 Release asset 的 `digest`
  (只认 `sha256:` 前缀),不一致就删包并提示重试。别为"少一步"删掉这段。
- Room 改实体必须补 `Migration`(当前 version 2,v1→v2 加了 `drinkType`/`hydration`);
  **不要用 `fallbackToDestructiveMigration`** —— 饮水记录是用户唯一的数据。
- 提醒用 `setExactAndAllowWhileIdle` 调度;免打扰支持跨午夜(`start > end` 的分支),
  改提醒逻辑要保住这两点。保活服务是 `specialUse` 类型前台服务,不要换成 `dataSync`。
- SharedPreferences 的 key 是已发布客户端的持久状态,改名等于清空老用户的设置
  (`user_prefs`、`water_reminder_prefs`、`update_prefs`)。

## 项目检查

验证受影响行为时使用这些既有检查,不另造:

| 检查 | 命令 |
| --- | --- |
| 编译 | `./gradlew assembleDebug`(JDK 17–23,推荐 17 或 21) |
| 版本一致性 | `python scripts/sync_version.py --check` |

- CLI 构建前先设置 `JAVA_HOME`(Gradle 8.11.1 不支持 24+)。
  未设置时 `./gradlew` 会直接报 `JAVA_HOME is not set`,不是构建坏了。
  本机具体路径见工作区 `.workbuddy-ai/memory/`,不要写进本文件。
- 改过 `.github/workflows/*.yml` 后、推送前,本地用 pyyaml 解析一遍再推
  (`yaml.safe_load`;YAML 1.1 会把 `on:` 解析成布尔 `True`,属正常现象)。
  装了 pyyaml 的解释器路径同样见工作区 `.workbuddy-ai/memory/`。
- 想确认应用内更新真的请求到了 GitHub:自动检查成功时才会写
  `shared_prefs/update_prefs.xml` 的 `last_check_at`,看这个文件即可。
- lint 目前只报告不拦截;仓库没有测试套件。不要为了"凑验证"新建测试
  脚手架,除非任务本身就是加测试。

## 本项目常见过度工程(明确不做)

- 不引入 DI 框架(Hilt/Koin)、不把 SharedPreferences 迁 DataStore、
  不把 OkHttp 换 Retrofit、不给 Room 加仓库抽象层——除非任务明确要求。
- 不加"以防万一"的重试、缓存、超时、抽象层。
- 不新建只为了"验证自己刚才改动"的检查器;要验证就用上面列的检查。
- 不虚构文件清单或验证证据来显得精确;不确定就查证或如实说。

## 汇报

- 报告结果与对应验证证据;有未解决的阻塞要如实说,不要宣称完成。
- tradeoff / 警告 / 限制仅在用户要求或影响结果解读时给出,放在决策点上,
  不堆免责声明。
- 过程性流水账、自我保护的叙述不写进代码、提交信息和文档。
