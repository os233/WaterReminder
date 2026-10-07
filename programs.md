# 本仓库 AI 代理职责与软件结构

本文件说明 WaterReminder 的程序结构、模块职责、依赖和接口约定，并规定 AI 代理的工作方式与汇报要求。仓库硬边界、兼容契约及按改动范围适用的验证要求，以 [AGENTS.md](AGENTS.md) 为准。

核心原则：**完整完成用户请求，让真实需求决定复杂度。** 不做投机性防御，不扩大任务范围，也不以额外流程代替交付。本文描述当前真实架构；它不是要求未来必须维持目录原样的架构图，结构演进须遵循下文的职责边界与依赖规则。

## 1. 系统概览

### 1.1 应用组成

WaterReminder 是单体 Android 应用，当前以 Kotlin 编写，主要运行于一个应用进程。代码位于 `app/src/main/java/com/example/waterreminder/`，按职责分为呈现、应用入口与系统协调、本地数据、远程更新四个部分：

```text
Android 系统 / 用户
        │ 生命周期、输入、闹钟与通知广播
        ▼
应用入口与系统协调：MainActivity、notification/
        │ 页面状态和用户操作       │ 闹钟、广播与通知
        ▼                         ▼
呈现：ui/                   Android 平台 API
        │ 查询/写入
        ▼
本地数据：data/ ───────────── 远程更新：data/remote/
  Room、DAO、偏好设置             OkHttp、Manifest 校验
```

图中表达的是当前主要调用方向，不代表所有模块都能互相依赖。依赖规则见第 4 节。

### 1.2 当前不包含的层

目前没有独立的 Domain、Repository、DI 或多模块架构。界面直接调用 Room DAO 和设置对象；提醒功能直接使用 Android 系统 API。不得仅为追求分层形式新增这些层或框架；只有明确的职责缺口、重复实现或可说明的测试/维护收益，才考虑演进。

## 2. 模块与职责边界

### 2.1 应用入口与界面导航

**位置：** `MainActivity.kt`、`WaterReminderApp.kt`

- `MainActivity` 是 Android Activity 入口，承接系统生命周期与权限请求，完成应用级 Compose 装配（`setContent` 提供主题、导航与屏幕组合），并触发更新检查。
- `WaterReminderApp` 是在 Manifest 注册的 `Application` 入口，当前没有额外初始化逻辑；不要预设把装配逻辑放在这里。
- 不在入口层堆放 Room 查询细节、闹钟算法或远程 Manifest 解析；这些逻辑由对应模块负责。

### 2.2 呈现模块 `ui/`

**位置：** `ui/WaterReminderScreen.kt`、`ui/HistoryScreen.kt`、`ui/RememberToday.kt`、`ui/theme/`

- `WaterReminderScreen` 展示饮水概览、每日目标、记录与提醒设置，并将用户操作交给对应数据或提醒能力。
- `HistoryScreen` 展示历史记录及日期汇总。
- `RememberToday` 提供与生命周期同步的当前日期：进入 `STARTED` 时立即刷新，可见期间每 60 秒轮询。界面日期不得自行缓存成跨生命周期快照；更多约束见 `AGENTS.md`。
- `theme/` 只承载 Compose 主题、颜色与字体定义。
- UI 负责呈现和交互，不复制数据存储、闹钟调度或网络协议逻辑。当前界面可直接调用 DAO 与设置对象；不要为此臆造抽象层。

### 2.3 提醒与系统协调模块 `notification/`

**位置：** `notification/AlarmManagerHelper.kt`、`notification/AlarmReceiver.kt`、`notification/QuickAddReceiver.kt`、`notification/BootReceiver.kt`

- `AlarmManagerHelper` 读取提醒与免打扰偏好，安排、取消或恢复精确闹钟，并持久化恢复所需的下一次触发时间。
- `AlarmReceiver` 接收提醒广播；先衔接下一次调度，再按免打扰规则决定是否发布通知。通知通过 `NotificationManager` / `NotificationCompat` 建立，点击后回到主界面，并附带三档快捷记录动作按钮。
- `QuickAddReceiver` 接收提醒通知与小部件的快捷记录广播；按固定「水」语义写一条饮水记录（钳制 1–5000 ml），并以独立静默渠道在原通知 ID 上重发 3 秒自动消失的反馈。它只补记录与反馈，不衔接提醒调度。
- `BootReceiver` 在设备启动或应用更新等系统事件后，根据已保存状态恢复有效提醒。
- 本模块拥有闹钟和通知的系统交互边界，不负责 Compose 页面状态或饮水记录持久化。提醒调度、跨午夜免打扰、后台引导及禁止前台保活等不变量见 `AGENTS.md`。

### 2.4 本地数据模块 `data/`

**位置：** `data/WaterRecord.kt`、`data/DrinkType.kt`、`data/WaterRecordDao.kt`、`data/WaterDatabase.kt`、`data/UserPrefs.kt`

- `WaterRecord` 定义一条饮品记录：自增 `id`、毫升数 `amount`、`LocalDateTime` 时间戳、饮品类型 `drinkType` 与折算系数 `hydration`。
- `DrinkType` 定义当前支持的饮品分类及其应用内含义。
- `WaterRecordDao` 是记录的持久化查询接口，负责新增、删除、按日读取、每日总量和历史汇总。每日折算量按 `amount × hydration` 计算；日期由调用方明确传入。列表与汇总查询以 `Flow` 暴露变化。
- `WaterDatabase` 是 Room 数据库入口，当前数据库名为 `water_database`，记录表为 `water_records`，数据库版本为 2。
- `UserPrefs` 保存每日目标等轻量用户偏好。提醒偏好与更新偏好分别由对应功能维护；SharedPreferences 文件名和 key 是已发布客户端的持久化接口。
- 饮水记录数据不得依赖通知或联网检查的存活；Room schema 变更必须迁移，具体要求见 `AGENTS.md`。

### 2.5 远程更新模块 `data/remote/`

**位置：** `data/remote/UpdateChecker.kt`、`data/remote/UpdateInfo.kt`

- `UpdateChecker` 使用 OkHttp 获取唯一发布 Manifest，显式解析、校验字段，并根据版本码产生检查结果；更新检查状态使用独立偏好设置维护。
- `UpdateInfo` 是通过校验后的更新数据载体，包含版本码、版本名、HTTPS APK 地址及可选 SHA-256。
- 更新模块承载检查、解析校验以及下载（`DownloadManager`）、SHA-256 核对、安装器调用的完整链路（均实现于 `UpdateChecker`）；各环节不得绕过 `AGENTS.md` 规定的安全步骤。
- 更新检查与本地饮水记录、提醒调度相互独立。网络失败不得阻断应用其他功能。

## 3. 核心数据流与程序协作

### 3.1 饮水记录与每日概览

1. 用户在 Compose 页面新增或删除记录。
2. UI 调用 `WaterRecordDao` 写入 Room。
3. DAO 的 `Flow` 查询发出最新记录或汇总，Compose 收集后更新页面。
4. 每日汇总按记录日期聚合 `amount × hydration`，日期参数由调用方传入；UI 当前日期由 `rememberToday()` 提供。

### 3.2 目标与提醒设置

1. UI 通过 `UserPrefs` 读写每日饮水目标，通过提醒偏好接口读取/保存提醒选项。
2. 提醒设置变化后，`AlarmManagerHelper` 安排或取消系统闹钟。
3. 闹钟触发时，Android 调用 `AlarmReceiver`；接收器安排下一轮提醒，再依据免打扰状态决定是否通知。
4. 设备重启或应用替换后，`BootReceiver` 读取持久状态并委托恢复逻辑。

### 3.3 远程更新

1. 更新入口调用 `UpdateChecker` 请求发布 Manifest。
2. 检查器逐字段校验结构和内容，并将有效结果转换为更新结果或 `UpdateInfo`。
3. 若用户继续下载，下载管理与安装环节按 `AGENTS.md` 执行：应用私有外部目录、完成广播状态复核、SHA-256 校验后再启动安装器。
4. 网络检查失败只结束本次检查，不改变饮水数据和提醒状态。

## 4. 依赖关系与禁止反向耦合

### 4.1 允许的主要依赖方向

```text
MainActivity / 应用装配
          └──► ui/
                 ├──► data/ DAO 与偏好接口
                 └──► notification/ 提醒操作

notification/ ──► Android AlarmManager、BroadcastReceiver、Notification API
               └──► 提醒偏好与调度逻辑

data/remote/ ──► OkHttp、JSON 解析 API、更新偏好
data/         ──► Room、SQLite、SharedPreferences
```

- Android 系统事件由 Activity/Receiver 等入口接收，再交给其负责的功能逻辑处理。
- UI 可以依赖当前公开的 DAO、偏好对象与提醒操作；数据模块和提醒模块不得依赖具体 Compose 页面。
- `data/remote/` 不得依赖饮水记录或提醒模块。更新检查结果只通过明确结果类型返回。
- 数据实体、数据库迁移与偏好存储不应依赖 UI 组件或 Activity 生命周期。
- 若将来需要引入新的协调层，应先指出具体依赖环、重复逻辑或生命周期/测试缺口，再只在该缺口处调整；不能仅因架构图看起来更「完整」而增加层级。

### 4.2 关键不变量

各模块协作必须维持以下跨边界保证，详细硬约束以 `AGENTS.md` 为准：

- 数据库 schema 演进保留用户记录；偏好文件名/key 保持已发布兼容。
- 更新协议字段和版本比较兼容旧客户端；检查失败与应用其他功能隔离。
- 闹钟调度与通知通过 Android 系统边界工作，不引入常驻前台保活。
- 所有「今天」视图使用统一的生命周期日期来源。
- 发布 Manifest 机器字段由 CI 回写，静态网站只显示真实存在的 Release/asset。

## 5. 接口与数据约定

### 5.1 模块接口的选择

- 对外提供最窄、含义明确的函数、DAO 查询、偏好对象或数据类型；调用方不应读取其他模块的内部实现细节。
- 同步调用表达立即完成的本地操作；数据库观察使用 `Flow`；远程操作明确表示成功、无更新或失败，不把失败伪装成默认数据。
- Android 生命周期组件（Activity、Receiver）作为平台适配入口，业务功能应通过可读的操作调用，不把系统回调散落在无关模块。
- 当前规模无需额外通用接口框架。若新增接口，必须能指出第二个真实消费者、隔离的平台边界或显著的可测试性收益。

### 5.2 持久化数据

- 饮水记录 schema 及其 Room Migration 是本地数据格式契约。`WaterRecord` 的字段含义、单位与折算规则变更时，必须同步核对读取、汇总与历史展示。
- SharedPreferences 文件名与 key 是持久化 API；改名相当于重置现有用户设置，禁止直接改名。
- 日期格式、毫升单位、`hydration` 折算方式及汇总整数转换由生产者和消费者共同遵守；变更时同步检查查询端与显示端。
- 不得让远程更新状态成为饮水记录或提醒正常工作的前置条件。

### 5.3 远程更新数据

- 更新唯一来源和顶层 JSON 字段是已发布客户端协议。解析必须逐字段显式校验，不能依赖 Gson 默认值。
- `versionCode` 是比较依据；APK 地址仅允许 HTTPS；有效 SHA-256 为 64 位十六进制。
- 解析失败返回失败结果，不抛出影响主流程的异常；安装前校验下载文件摘要。具体字段与失败行为以 `AGENTS.md`「应用内更新兼容契约」为准。

### 5.4 系统交互接口

- 闹钟通过 `AlarmManager` 和明确的 PendingIntent 身份安排；广播接收器按约定接收事件，并在处理后更新后续调度。
- 通知通过 Android 通知 API 发布，点击意图返回应用主界面。
- 下载完成广播必须通过登记的 downloadId、DownloadManager 状态与文件摘要逐步核验；不可把收到广播本身视为下载成功。
- 平台能力存在最低 API 限制时，按 `minSdk = 26` 与 `SDK_INT` 分支约定实现。

## 6. 目录组织与长期演进

### 6.1 当前目录映射

```text
app/src/main/java/com/example/waterreminder/
├── MainActivity.kt                 # Android 应用入口与页面装配
├── WaterReminderApp.kt             # Application 入口（无额外初始化）
├── ui/                              # 页面、日期状态与主题
├── data/                            # Room、DAO、实体、轻量偏好
│   └── remote/                      # 远程更新协议与检查
└── notification/                   # 闹钟、系统广播与通知

app/src/main/res/                    # Android Manifest、主题、图标及 FileProvider 路径
docs/                                # GitHub Pages 静态站点与发布 Manifest
scripts/                             # 发布与版本校验脚本
.github/workflows/                  # CI 与发布自动化
```

### 6.2 新增功能的归属

- 新页面和展示状态放入 `ui/`；通用 Compose 主题放入 `ui/theme/`。
- 新的饮水记录字段、查询和 Room Migration 放入 `data/` 对应实体/DAO/数据库边界。
- 新的持久偏好放入最贴近其功能的设置模块，先确认是否属于既有持久化兼容面。
- Android 闹钟、广播、通知及启动恢复放入 `notification/` 或该系统能力的专属包。
- 更新协议、Manifest 请求与校验放在 `data/remote/`；静态网站文件保持在 `docs/`，不增加构建步骤。
- 新职责若横跨多个现有模块，先说明其输入、输出、持久状态与系统边界，再选择最小可维护的归属；避免一个文件同时承担网络解析、持久化、UI 和系统副作用。

### 6.3 演进规则

- 优先沿用现有目录和依赖，保持单体应用的直接性。
- 只有出现可描述的维护问题（例如重复业务规则、依赖环、平台边界难以验证、模块职责持续膨胀）时，才考虑抽取新组件或层。
- 抽取时定义该组件负责什么、不负责什么、谁可以调用，以及数据如何流动；同步更新本文的结构图、目录映射和接口约定。
- 涉及持久数据、远程协议、已发布设置或系统回调的重构，必须先识别兼容消费者和失败路径，并遵守 `AGENTS.md` 的迁移、更新与验证要求。
- 不引入 DI 框架、Repository 层、DataStore、Retrofit、常驻服务、重试/缓存/超时或新测试框架，除非任务明确要求且说明现有做法的具体缺口。具体技术限制见 `AGENTS.md`。

## 7. AI 代理工作方式

### 7.1 停止阶梯

选择实现、增加机制或扩大验证时，按以下顺序判断：

1. **明确任务和影响面：** 确认用户要的结果、文件/模块边界、必须保留的不变量；追踪调用方、数据路径、失败路径和必要的文档/迁移改动。
2. **优先直接方案：** 复用现有代码、平台 API、标准库和已安装依赖（如 OkHttp、Room）。围绕真实输入和失败行为判断是否足够，不必穷举生态方案。
3. **针对具体缺口扩展：** 说明直接方案覆盖不了哪个输入、调用方或失败路径，再在负责该职责的边界补齐。「未来可能需要」本身不是增加抽象或依赖的理由。
4. **按效果评估防御：** 说明机制检测什么、怎样拒绝/恢复/诊断，以及是否改变真实结果。移除无实际收益、掩盖失败、重复副作用或妨碍正常使用的机制。
5. **按影响范围验证并结束：** 使用 `AGENTS.md` 中适用于改动的既有检查。证据覆盖请求结果、没有已知范围内阻塞时结束，不继续无目标地增加审计循环。

### 7.2 任务模式

- `review` / `answer` / `monitor`：只读。除非用户明确授权，不得修改文件。
- `change`：完成请求及其必要后果；不得顺手重构或优化无关代码。
- 必要工作不得越过用户明确指定的文件锁或更窄边界。确需越界时，先说明原因与影响，再继续。
- 提交前检查 `git status`，只提交任务相关文件。IDE 配置、CI/API 查询临时数据等不相关残留不得进入提交；一次性草稿用完即清理，反复出现的本地文件加入 `.gitignore`。遵守 `AGENTS.md` 的密钥和隐私规则。

### 7.3 汇报

- 报告完成结果及对应验证证据。未解决的阻塞、未运行的检查和未实测行为必须如实说明，不得宣称已完成或已验证。
- 仅在用户要求或影响结果解读时说明 trade-off、警告与限制，并放在相关决策点上。
- 不把过程流水账、自我保护叙述写进代码、提交信息或文档；文档应描述系统事实、约定和可执行的维护规则。
