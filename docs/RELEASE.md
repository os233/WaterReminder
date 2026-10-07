# 发布与维护（维护者）

WaterReminder 的发版流程、版本 Manifest 契约与应用内更新原理。仓库主页见 [README](../README.md)。

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
      └─────────┼──────────┐
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
   比 versionCode   (仅更新日志页退到 version.json)
        │
        ▼
  下载 Release 里的 APK → 校验 SHA-256 → 交给安装器
```

```bash
# 1. 改 app/build.gradle.kts 里的 versionCode 与 versionName（versionCode 必须严格递增）
# 2. 手写 docs/version.json 的 changelog —— 这是它唯一人工维护的字段
# 3. 若本版有界面或功能的可见变化：更新 docs/images/ 三张截图（README 界面预览与官网首页
#    共用同一批文件，改一次两边生效），并同步 docs/index.html 功能卡片与
#    docs/docs/index.html 文档页文案。版本号、下载链接、更新说明不用管 ——
#    官网会随 Release 自动更新（原理见下「GitHub Pages」）
# 4. 本地构建 + 归档 + 校验（不会改 version.json 的机器字段，原因见下）
./scripts/release.sh
# 5. 提交并推 master（只加这两个文件；不要 git add -A，会扫进无关残留）
#    此时 version.json 的版本号还是旧的，App 不会提示更新，也不会 404
git add app/build.gradle.kts docs/version.json
git commit -m "release: 发布 <版本号>" && git push origin master
# 6. 打 tag 并推送 → CI 构建 APK、创建 Release、上传 asset，然后把 version.json 的
#    versionCode / versionName / apkUrl / sha256 写回并推 master
git tag -a v<版本号> -m "release: v<版本号>" && git push origin v<版本号>
```

⚠️ **顺序不能反**：先推 `master`，再推 tag —— tag 要打在已经推到 master 的提交上。
CI 写回时会先 `git fetch` + `git rebase origin/master` 再推，所以构建那几分钟里 master
又被推了提交（发版后补文档很常见）也不会丢写回。不加这两步的话推送会被拒，结果是
Release 已经建好、Manifest 却没更新 —— 所有客户端永远收不到这个版本，而且不报任何错。

**为什么机器字段不能本地写**：Manifest 一旦指向新版本，App 就会让用户去下载那个地址。
如果这时 asset 还没上传，用户点更新直接 404。让 CI 在 asset 就位**之后**写回，
这个窗口就不存在了 —— 代价只是「推完 tag 等 CI 跑完，App 才开始提示更新」。

`scripts/release.sh` 依次做：校验 `keystore.properties` 存在 → `./gradlew assembleRelease` →
产物归档到 `app/release/`（本地留档，不入库）→ Manifest 校验 → apksigner 签名自检
（找不到工具就跳过并提示）。**它不会自动 commit / push** —— 发布是对外动作，留给人确认。

**务必始终使用同一个密钥库**。签名不一致会导致老用户无法覆盖安装，只能卸载重装。

## 补改已发布版本的更新说明

Release 正文取自 **tag 指向的那个提交**里的 `docs/version.json` —— `release.yml` 的
`actions/checkout` 用 `ref: ${{ inputs.tag || github.ref }}`，所以 `workflow_dispatch`
手动重跑读的也是 tag 里那份。这意味着「改完 `changelog` 再重跑一次 workflow」
**不会生效**：读到的还是 tag 里的旧文字，而且**没有任何报错**。

要让新文字生效，必须把 tag 挪到新提交上：

```bash
# 1. 改 docs/version.json 的 changelog，推 master
git add docs/version.json
git commit -m "docs: 补写 <版本号> 的更新说明" && git push origin master
# 2. 把 tag 移到这个新提交上（-f 重打，不要删 tag）
git tag -f -a v<版本号> -m "release: v<版本号>" <新提交>
git push --force origin v<版本号>
# 3. 重跑 workflow：Actions → Release → Run workflow，填 v<版本号>
```

⚠️ 用 `-f` 重打而**不删 tag**：删掉会让 `releases/tags/v<版本号>` 出现 404 窗口，
而官网首页、下载页与 App 内更新都查这个端点。重跑走的是 PATCH 分支 —— Release 的
`id` 与 `published_at` 都不变，只是替换正文，**不是新建一个 Release**。

⚠️ 正式版的 Release 正文**不读 tag 注释**（读 Manifest 的 `changelog`），所以重打 tag 时
`-m` 写什么都不影响正文；但必须保留 `-a`，否则 tag 变成 lightweight，预发布通道的
说明来源会静默退化成兜底文案。

## 预发布一版（beta）

预发布和正式发版走**同一条链路、同一个 workflow**，区别只在 tag 带后缀、以及不写 Manifest：

```bash
# 1. 改 app/build.gradle.kts：versionName 带后缀；versionCode 用「对应正式版」那个数，
#    不是沿用上一版的（发 0.0.1-beta.1 用 1，之后发 0.0.2-beta.1 用 2）
#    versionCode = 1
#    versionName = "0.0.1-beta.1"
# 2. docs/version.json 一个字都不改（预发布不写回，changelog 也不必为它更新）
# 3. 本地构建 + 校验（可选，但推荐：能在推之前就发现问题）
./scripts/release.sh
# 4. 推 master —— tag 必须打在已推到 master 的提交上，顺序与正式版相同
git add app/build.gradle.kts
git commit -m "release: 预发布 0.0.1-beta.1" && git push origin master
# 5. 打**带注释**的 tag（-a + -m）：正文就是这一版的更新说明，CI 拿它当 Release body。
#    标题行会被去掉，从空行后开始算正文；不写正文就只能落一句「没有填写更新说明」。
git tag -a v0.0.1-beta.1 -m "$(printf 'WaterReminder 0.0.1-beta.1\n\n1. xxx\n2. yyy\n')"
git push origin v0.0.1-beta.1
```

CI 会构建签名 APK、建一个标着 **Pre-release** 的 Release 并上传 asset，然后**跳过** Manifest
写回 —— 所以老客户端不会收到 beta，官网首页也不会把它当成最新版。包在
`releases/tag/v0.0.1-beta.1`，更新日志页会带上「预发布」标签。

**更新说明的来源按通道分**（`release.yml` 的「判定发布通道」与「创建 Release」两步）：

| 通道 | Release body 取自 | 为什么 |
| --- | --- | --- |
| 正式版 | `docs/version.json` 的 `changelog` | 唯一手写的正式说明，App 更新弹窗读的也是它 |
| 预发布 | **tag 的注释** | 预发布不写回 Manifest，在 Manifest 里没有自己的说明，照抄只会抄到上一版正式版的文字 |

⚠️ 因此**预发布的 tag 必须用 `-a` 写注释**。`git tag v0.0.2-beta.1`（lightweight，无注释）
不会报错，但 Release 说明会退化成一句「本次发版没有填写更新说明」——因为 lightweight tag
没有注释对象，事后也补不上（只能重打 tag 并 force push）。

⚠️ `versionCode` 与它**对应的正式版**共用 —— 也就是跟着 `versionName` 的第二段走，
**不是沿用上一版的**。`0.0.3-beta.1` 用 `3`（它的正式版 `0.0.3` 也是 3），而不是沿用 `0.0.2` 的 `2`。
这是「严格递增」的唯一豁免，理由是 beta 不写回 Manifest、客户端感知不到它。

⚠️ 沿用上一版的 `versionCode` 会让装了上一个 beta 的用户**永远收不到更新提示** ——
版本比较用的是整数 `versionCode`，相同就不算「有新版」。

⚠️ 预发布**不解决**「Manifest 指向的正式包不存在」这类问题：它不写回 Manifest，所以 App 内更新
与官网下载页读到的仍是上一个正式版。要让客户端真正拿到包，必须发正式 tag。

## docs/version.json 是发布 Manifest，不能删

它有两个消费者：

**① 应用内更新。** 所有版本都读它 —— 包括 1.4.0 及更早的老客户端（它们的请求 URL 就是它）。
有没有新版本、去哪里下载、下载后拿什么摘要校验，全部来自这个文件。

**② 官网的判断依据与兜底。** `site.js` 读它拿到「期望的 `versionName`」，再去
`GET /releases/tags/v<versionName>`，证实这个版本**真的存在**（且挂着 `.apk` asset）才显示 ——
未认证的 API 只有 **60 次/小时、配额按出口 IP 共享**，但绝不能因此把 `version.json` 当权威：
它是手工维护的，Release 被删时不会跟着变，照它显示就会把不存在的版本号和一条 404 直链
当正式版本发出去。取不到版本信息时页面明说取不到。

**②b** 更新日志页在 API 失败**或列表为空**时读它渲染一条带「本站清单」标签的记录 ——
这是唯一一处 `version.json` 会成为展示内容的地方，且明确标注了来源。因此
**`changelog` 要认真写**：API 失败时官网直接拿它当更新说明显示。

字段与维护者：

| 字段 | 谁写 | 说明 |
| --- | --- | --- |
| `versionCode` | CI | 机器比较依据，必须与 `build.gradle.kts` 一致 |
| `versionName` | CI | 给人看的版本号 |
| `apkUrl` | CI | 指向 Release asset，必须是 https |
| `sha256` | CI | 该 asset 的摘要（64 位小写十六进制）；为空时 App 跳过下载后校验 |
| `changelog` | **人工** | App 更新弹窗读它；官网仅在 API 失败或列表为空时用它当兜底记录 |
| `forceUpdate` | 人工 | 为 `true` 时弹窗没有「稍后」按钮 |

⚠️ 因此**不要删这个文件**，也不要删 `scripts/sync_version.py` 里的 `VERSION_JSON`。

## Manifest 校验

```bash
python scripts/sync_version.py --check    # 只校验不写文件；CI 也跑这个
python scripts/sync_version.py --expect-tag v<版本号>    # 断言 tag 与 versionName 一致；发版工作流用
```

`--check` 校验的不变量：

- `docs/version.json` 的 `versionCode` 必须是正整数，且**不能比源码还新**
  （多半是升了它却忘了升 `app/build.gradle.kts`）
- `versionName` 必须是 `x.y.z` 形态
- `apkUrl` 必须是 https，且文件名必须与 `versionName` 匹配
  （`WaterReminder_v<versionName>_release.apk`）；指向 Pages 时还要求 `app/release/` 下真有这个文件
- `sha256` 非空时必须是 64 位小写十六进制（App 只认这个格式，写错会被当成「没有摘要」）
- `sha256` 缺失不算失败：本地发版时它是空的，CI 在 Release 建好后写回真实值
- `app/release/output-metadata.json` 存在时，它的版本号必须与 Manifest 一致
- `apkUrl` 指向 Releases 时脚本只给提示，**无法校验 asset 是否已上传** —— 不过发版流程里
  这一步由 CI 保证（先上传 asset，再改 Manifest），不需要人工确认

**它不要求 Manifest 的版本等于源码版本** —— 允许落后（开发中先升源码版本号是正常的），
只拦「超前」这一种：Manifest 比源码新，客户端就会去下载一个还没构建出来的版本。

## 应用内更新怎么工作

App 读 `https://os233.github.io/WaterReminder/version.json` —— Pages 上的静态发布 Manifest：

- **版本比较用 `versionCode`**，与装机版本的 `PackageInfo.longVersionCode` 直接比大小
  （API 28 以下回退到 `versionCode`）。符合 Android 的版本模型，也不依赖 `versionName`
  的数字段恰好有序
- 下载完成后算安装包的 SHA-256 与 `sha256` 字段比对，不一致就删掉重来，不交给安装器
- 安装包由系统的 `DownloadManager` 下到**应用自己的外部私有目录**
  （`getExternalFilesDir(DIRECTORY_DOWNLOADS)`）。**不能落公共「下载」目录**：本应用没有
  声明任何存储权限，分区存储（FUSE）下 `File.exists()` 对公共目录恒为 `false`，
  下载成功了也会被当成「文件不存在」静默放弃。落点必须与 `res/xml/file_paths.xml` 的
  `<external-files-path>` 一致，否则 `FileProvider.getUriForFile` 会抛异常
- 下载完成的广播（`ACTION_DOWNLOAD_COMPLETE`）由系统的下载提供方发出，接收器必须用
  `RECEIVER_EXPORTED` 注册才收得到 —— 它既不是本应用也不是系统 uid，用
  `RECEIVER_NOT_EXPORTED` 会被系统直接丢弃，表现是「下载完装不上」且毫无报错。
  放开导出没有实际风险：先按 `downloadId` 过滤，再向 `DownloadManager` 复核状态，
  最后还要比对 SHA-256
- 自动检查最多每 12 小时一次；手动检查忽略节流，并明确区分「已是最新」和「没查到」
- 任何失败（断网、HTTP 错误、字段缺失或非法）都只当作「没有更新」，不影响使用
- `forceUpdate` 为 `true` 时弹窗没有「稍后」按钮

**为什么不读 GitHub Releases API**：未认证的 API 只有 60 次/小时，而且配额**按出口 IP
共享** —— 公司、校园网、运营商 NAT 这类共用出口很容易被别人的请求用光，拿到 403 之后
更新检查就静默失败了。静态文件在 CDN 上没有配额，App 也不必去猜 API 的响应结构。

⚠️ **字段结构是兼容契约**：已发布的 1.4.0 也按顶层字段解析这个文件。
缺字段会被静默当成 0/null（表现为「永远没有更新」）。所以只能**新增**字段 ——
不能改名、删字段，也不能把它们挪进嵌套对象。

## GitHub Pages

Pages 源为 `master` 分支的 `/docs` 目录，站点就是 `docs/` 下的静态文件（无构建步骤）：

| 路径 | 内容 |
| --- | --- |
| `/` | 首页：功能、界面一览、水合系数、最新版本 |
| `/download/` | 下载页：最新版本、APK 直链、SHA-256 |
| `/docs/` | 使用文档与常见问题 |
| `/changelog/` | 更新日志，前端读 GitHub Releases API，失败或列表为空时退到 `version.json` 里的最新正式版 |
| `/privacy/` | 隐私政策 |

`docs/assets/site.js` 在浏览器里请求 GitHub 的公开 API，所以版本信息与更新日志都不需要手工同步 ——
Release 一发布，页面内容就跟着变。功能介绍、使用文档与界面截图是静态文案，需要随发版手动同步，
对应「发布流程」的第 3 步。

未认证的 API 只有 60 次/小时，且配额**按出口 IP 共享**（公司、校园网、运营商 NAT 下很容易被
别人的请求用光）。但这**不是**把 `version.json` 当权威来显示的理由：那个文件是手工维护的，
Release 被删了它不会跟着变。所以首页与下载页的判断顺序是「先证实、再显示」：

1. 读 `version.json` 拿到期望的 `versionName`；
2. 拿它去 `GET /releases/tags/v<versionName>`，且该 Release 必须挂着 `.apk` asset；
3. 成立才显示版本号、直链与 SHA-256（摘要优先用 Release asset 的 `digest`，GitHub 官方算的）；
   不成立就显示「vX.Y.Z 尚无可下载的安装包」并把按钮指向 Releases 页面。

这样 API 挂掉时页面**不会**回退去显示一个未经证实的清单版本号 —— 那正是「Release 已删、
Manifest 还指着它」时把死链当正式版本发出去的原因。取不到版本信息时宁可明说取不到，
也不猜。`version.json` 仍然只保留一条记录，所以更新日志页的兜底不是完整历史，
它只是保证「API 挂掉时页面不会空着、也不会和首页显示的版本号自相矛盾」。

首页/下载页读 `version.json` 却只把它当「期望值」，还有一层原因：GitHub 的
`/releases/latest` 完全不看版本号，Release 被删光、只剩一个预发布时，它会把 beta
的 tag 和 asset 当成「最新版」交给首页 —— 本项目确实发生过这个状态。走 tag 接口就不会。

API 返回**空列表**时更新日志页走 `version.json` 兜底：那多半是 Release 被删了而 Manifest 还在，
这时写「还没有发布过版本」会和 Manifest 冲突。兜底记录带「本站清单」标签，不冒充 Release 记录。
Pages 不只是展示 —— **App 的更新检查也读它**（`/version.json`），见「应用内更新怎么工作」。

## CI

| 工作流 | 触发 | 做什么 |
| --- | --- | --- |
| `.github/workflows/ci.yml` | push 到 master / 任何 PR / 手动 | Manifest 校验 + `assembleDebug` + 单元测试 `testDebugUnitTest`（lint 目前只报告不拦截）。不需要任何密钥，fork 的 PR 也能安全跑 |
| `.github/workflows/release.yml` | push 形如 `v1.4.0` 的正式 tag 或 `v1.4.0-beta.1` 的预发布 tag；手动触发保留，用于失败重跑（会覆盖已有 asset） | 校验 tag 与 versionName 一致 → 判定发布通道 → 构建签名 APK → 校验签名 → 创建 Release 并上传 asset（预发布标记为 prerelease）→ 核对 asset 的 SHA-256 与本地产物一致 → 正式版把机器字段写回 `docs/version.json` 并推 master（预发布跳过这一步） |

tag 过滤器是两条 glob：`v[0-9]*.[0-9]*.[0-9]*`（正式）与 `v[0-9]*.[0-9]*.[0-9]*-*`（预发布）。
两条都显式列出 —— 图省事写成 `v*` 的话，`vfoo` 这类无关 tag 也会拉起一次 workflow，
虽然会在「校验 tag 与 versionName 一致」那步失败，但白占一次 runner。

⚠️ **第一条其实就已经匹配预发布 tag**：glob 里的 `*` 是通配符而不是量词，所以
`v[0-9]*.[0-9]*.[0-9]*` 的末尾那个 `*` 会吃掉 `-beta.1`，`v0.0.1-beta.1` 照样命中。
第二条只是把这个意图显式写出来，**不是冗余，别删**。历史上这里曾经是一条 `!v*-*` 排除项，
那正是「推 beta tag 什么都不发生」的原因。

**预发布**（tag 形如 `v0.0.1-beta.1`）与正式发版的差别有三处，其余步骤完全一致：

| | 正式 tag | 预发布 tag |
| --- | --- | --- |
| Release 标记 | 普通 Release | 标记为 prerelease（工作流走 REST API 的 `prerelease` 字段） |
| `docs/version.json` | CI 写回 | **完全不动** |
| Release 说明来源 | Manifest 的 `changelog` | **tag 的注释**（`git tag -a -m "<正文>"`） |
| 官网首页 / 下载页 | 显示这一版 | 不显示（首页/下载页按 Manifest 的 `versionName` 查 tag，而 Manifest 不动，查的还是上一个正式版） |
| 官网更新日志页 | 列出 | 列出，带「预发布」标签 |
| App 内更新 | 会提示 | 不会提示（Manifest 没变） |

前三行是机制差别，后三行是它们的表现 —— 所以「要不要动 Manifest」这一条决定了后面三行的全部行为。

⚠️ **预发布绝不能写回 Manifest**：Manifest 是所有客户端（含已发布的正式版）唯一的更新源，
写进去等于把 beta 推给全部用户。所以预发布只存在于 Releases 里，只有主动去 GitHub 下载的人
才装得到 —— 这也是它安全的原因。

⚠️ 预发布必须标记为 prerelease（当前工作流通过 GitHub API 设置）而不是「建好再手改」：一旦某个 Release 没被标成 prerelease，
它就会成为 `/releases/latest` 的候选，beta 会被顶到官网首页的下载按钮上。

release 工作流需要在仓库 Settings → Secrets and variables → Actions 配好
`KEYSTORE_BASE64`（`base64 -w0 water_keystore.jks`）、`STORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`；
缺 `KEYSTORE_BASE64` 时它会在「还原签名配置」那一步明确报错退出，不会静默产出未签名包。
