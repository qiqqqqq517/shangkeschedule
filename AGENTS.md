# AGENTS.md

## 项目记忆（IMA 知识库）优先（强制）

- **凡有 IMA 记录可用，优先用 IMA 记录**。本项目在 IMA 个人知识库「**上课**」里维护了一套项目记忆，**8 个一级文件夹**（编号 01~08）：总览 · 架构与模块 · 运行部署 · 接口文档 · 决策记录 · 进度与待办 · 问题复盘 · 资料归档。开工时先看《01-总览 / 项目索引》定位，再读对应篇章，**不要一上来就读代码或翻日志**。
  - **不要在此写死条目篇数**：内容持续增长且分层较深（一级下还有二级子文件夹）。需要盘点时现场调 API 读 `get_knowledge_base` 的 `content_count`，或逐文件夹 `get_knowledge_list` 统计，**不得引用历史数字**。
  - 2026-10-05 实测：`content_count = 78`（一级 8 个文件夹 + 根层散落的巡检报告）。此处原写的「14 篇」已于该日按实测更正（P2-10 / P2-41）。
- **必须先查 IMA 的场合**：接手任务 / 摸底现状；问「为什么这么定」（→ 决策记录）；问「还剩什么没做」（→ 进度与待办）；改 UI / 数据库 / 通知 / 小组件等已有约定的模块；发版前的口径确认。
- **IMA 是索引与人读层，不是最终事实来源**：每篇都标了「取材版本 / 生成时间」。涉及**代码事实、版本号、数据库结构、文件数量、环境路径**等会随代码变化的结论，**必须回仓库 / 代码复核**；与代码或 `工作日志.md` 冲突时以后者为准，并按此节把差异回写 IMA。
- **回写纪律**：改动完成后，若结论 / 架构 / 决策 / 进度 / 问题状态发生变化，同步更新 IMA 对应篇章（细则见 IMA 知识库《01-总览 / DSH 调用指南》）。**只追加或留痕更正，不静默覆盖既有结论**。
- **接口与纪律**：IMA 走 `ima-skill`（`C:\Users\30458\.dsh\skills\ima-skills\`）；`knowledge_base_id` 与文件夹 ID 一律**按名称现场解析**，不得把内部 ID 写进面向用户的文档；笔记内容的 Markdown 源文件保存在 `build_qa/ima-init/notes/`（该目录属会话产物，不入库）。

## 工作日志（强制前置读取）

- **开始任何代码 / 数据 / 构建改动之前**，必须先用 Read 读取仓库根目录的 `工作日志.md`，了解项目演进历史、版本状态与遗留问题，再动手。
- **每次改动完成后**，必须在 `工作日志.md` 顶部的「最新改动」区倒序追加一条记录（最新在上），不得遗漏。
- 记录格式：`日期 | 版本 | 类型 | 摘要`；类型取值：`FEAT` 功能 · `FIX` 修复 · `REFACTOR` 重构 · `DOCS` 文档 · `DATA` 数据 · `BUILD` 构建/发布。
- 每条记录须写明：改动原因、涉及关键文件、验证状态（编译 / 装机 / 未验证）。
- 记录规范细节以 `工作日志.md` 内「记录规范」一节为准。

## 版本迭代（自动）

- **每次完成 bug 修复 / 新功能 / 大版本改动并验证通过后，必须自动执行一次版本迭代**，不得遗漏。
- 迭代类型与改动对应关系：
  - `FIX` 修复 bug / 小改动 → `--bump patch`（如 2.14.0 → 2.14.1）
  - `FEAT` 新功能 / 新增学校数据 → `--bump minor`（如 2.14.0 → 2.15.0）
  - 大版本 / 破坏性改动 → `--bump major`（如 2.x.x → 3.0.0）
  - `versionCode` 每次迭代无条件 `+1`
- 执行命令（仅迭代版本号，不触发构建/装机）：
  `python tools/publish_new_version.py --bump <patch|minor|major> --bump-only`
  如需完整构建 + 装机，去掉 `--bump-only`。
- 迭代完成后，把新版本号（`vX.Y.Z` + versionCode）写入 `工作日志.md` 对应改动记录中。

## 更新声明（CHANGELOG，每次发版填充）

- **每次发布新版本（含正式版构建 / GitHub Release）后，必须在 `CHANGELOG.md` 顶部「最新版本」区倒序追加一条更新声明**，不得遗漏。
- 记录格式：`### vX.Y.Z（YYYY-MM-DD）· 发布说明`，内容按「功能 / 修复 / 外观 / 构建」分类，写明关键变更与构建信息（versionCode、支持 ABI）。
- 与 `工作日志.md` 互补：`CHANGELOG.md` 面向用户发布说明，`工作日志.md` 面向开发过程；两者同步维护。
- 若发布 GitHub Release，其 body 应引用 `CHANGELOG.md` 对应条目（或直接使用相同内容）。

## 正式版构建与发布（本地构建 或 CI 构建，二选一；手动上传）

> 逐条可执行命令见 `docs/agents/release-runbook.md`；本节是必须遵守的硬规则。

- ⚠️ **CI 构建流与发布路径已于 2026-10-06 按用户明确要求放开**（原规则「严禁使用 CI 构建正式版」**整体废止**，用户已知悉风险并接受）：
  - `android-build.yml` 现为 **active**，已配置 4 个仓库级 secret（`KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`），并**端到端实测通过**：运行 37503332665 `success`，产物三包逐个校验三项全过，**V2 证书 SHA-256 与下方生产证书逐字符一致** ⇒ CI 产物与本地正式版同签名、可覆盖安装升级。
  - 触发方式仍为 `workflow_dispatch`（**仅手动**，不随 push 自动跑）；所在 `Release-Signing` environment **已无任何保护规则**（`protection_rules: []`，2026-10-07 按用户要求清除原 15 分钟 `wait_timer`），触发后立即开始，无需等待或审批。
  - ✅ **CI 可以作为正式发布路径**：CI 产出的三个 APK 可用于正式发布，但**仍必须走下方同一套校验与发布流程**（逐包校验三条 → `gh release create` → 官网同步 + 部署 → 夸克网盘 → arm64 归档），并同样在 `工作日志.md` 追加 `BUILD` 记录。
  - ⚠️ **`android-release.yml`（「需要审核的发布操作」）保持 disabled**（用户 2026-10-07 明确要求）：对外发布仍走 `gh release create` 手动上传，不启用该 workflow 链路。它依赖的 `Production-Release` environment 当前也不存在。
  - **本地构建与 CI 构建并行可选**：两者产物同签名，可按当次情况任选其一；本地路径命令见下方各条与 `docs/agents/release-runbook.md`。
  - ⚠️ **安全边界**：仓库为 public 且有 3 个协作者（两人有 push 权限），**有 push 权限者可通过改 workflow 读取 secret**——这是 GitHub Actions 的固有边界。如需收紧，应把 secret 改为 environment 级（当前 `Release-Signing` 的 environment 级 secrets 数为 0）。
  - 构建脚本依赖的资源仓库 `XingHeYuZhuan/shangke_warehouse` **已 404 不可访问**，但两条克隆步骤均带内置回退，仓库内置资源完整（`school_index.pb` 418,176 B + 179 个资源目录）⇒ 构建可正常继续。
  - **其余启用中的 workflow（2026-10-06）**：`pr-guard.yml`（PR 来源拦截 + DCO 告警，**合并自原 `check-pr-source.yml` 与 `dco.yml`**）、`dependency-submission.yml`（仅手动触发，刷新依赖图谱用）、Dependabot。
  - ⚠️ **DCO 现为告警模式**：本仓库近 50 个提交的 DCO 签名率为 0/50（从未用过 `git commit -s`），而分支策略是「短生命周期分支 → 合并回 main」，故 `pr-guard.yml` 的 DCO 步骤只输出 `::warning::`、不阻断合并（改回硬门禁：把该步骤 `WARN_ONLY` 置 0）。
  - ⚠️ **GitHub 仓库级「Automatic dependency submission」需在网页端关闭**：GitHub 另有仓库托管的自动依赖提交（`dynamic/dependency-graph/auto-submission`，**不在本仓库文件里、也不读本仓库配置**），海外 runner 直连阿里云镜像会 502 并导致 KSP/Koin 插件解析失败（实测 2026-09-13 连失 5 次）。该开关无 REST/GraphQL 端点可用（实测全 404），只能到 Settings → Code security 手动关闭；本仓库内那条 `dependency-submission.yml` 已带 `-PuseMirror=false`，是**可用**的那条，不要删。
- **前置：`CHANGELOG.md` 必须已有对应版本条目**。Release body 直接取自该条目（或与之相同的内容）；条目未就绪不得发版。
- **前置：涉及适配脚本改动时，私有适配仓库必须已 commit + push**（`cd .adapter_private` → `git status` 干净 → `HEAD` 与 `origin/main` 相同 → 线上核对远端 sha256 与正文特征串）。**只跑 `build_index.py` 不算已经同步**：远端若比包内旧，App 启动时 `AdapterRemoteUpdater.sync()` 会用旧版**覆盖掉包内新脚本**，症状是「原有功能正常、本次新增功能全失效」，极难排查（2026-10-04 实际发生）。纯 UI / 逻辑改动可跳过。细则见 `docs/adapter-sop.md` §6.4 / §8.1 与 `docs/agents/release-runbook.md` §1.1。
- 构建方式**二选一**（产物同签名，见本节首条）：**本地构建** `./gradlew :androidApp:assembleRelease`，或 **CI 构建**（触发 `android-build.yml`，下载 `app-release-apk` artifact）。产物均为按 ABI 拆分的三个 APK：`shangke-vX.Y.Z-<abi>-release.apk`（arm64-v8a / armeabi-v7a / x86_64）。
  - **本地构建**注意：本机 `JAVA_HOME` 环境变量是坏的，每条 Gradle 命令前必须显式设为本机 JBR：`C:\Program Files\Android\Android Studio\jbr`。
  - **CI 构建**注意：`Release-Signing` environment **无保护规则，触发后立即开跑**（无需等待/审批）；用 `gh run watch <id>` 跟进，产物用 `gh run download <id> -n app-release-apk` 取回（约 8–11 分钟）。
  - 若构建整体 `UP-TO-DATE`，**必须核对产物 mtime 晚于 `HEAD` 提交时间**才能认定产物含本次改动；否则加 `--rerun-tasks` 重打（CI 侧为全新环境，无此问题）。
- **发布前必须逐包校验三条**（任一不过即不得发布）：① `aapt2 dump badging` 的 `versionCode`/`versionName` 与本次版本一致；② `native-code` 每包**只有单一 ABI**；③ `apksigner verify --print-certs` 的 V2 证书 SHA-256 = `4ae49d8c97d881c7c249115b833f932c70f9b429624e88e68807e8fc2232475f`（三包一致且与历史一致）。工具在 `D:\Android\SDK\build-tools\37.0.0\`。
- **上传方式（最快路径）**：`gh` 一条命令建草稿并上传全部资产（账号 `qiqqqqq517`；`gh` 2.101 已装并登录该账号，2026-09-29 实测）：`gh release create vX.Y.Z --draft --target <发布提交> --notes-file <CHANGELOG 段落文件> <三个 APK>` → `gh release edit vX.Y.Z --draft=false` 转正式 → `git ls-remote` 核验 tag 指向发布提交。逐条命令见 `docs/agents/release-runbook.md` §4（REST API 逐步操作降为备用）。**不得绕过校验在网页手工上传**；构建产物可来自本地或 CI（见本节首条），但**逐包校验三条必须对实际要发布的那份产物执行**。
  ⚠️ **令牌一律用 `gh auth token` 获取**（实测可用，scopes 含 `repo`）。**不要依赖 `git credential fill`**：本机凭据链不可靠（`~/.gitconfig` 曾被写入空值 `credential.helper =` 或多行写法错误的 helper，导致报 `could not read Password ... terminal prompts disabled`，而凭据其实一直存在 Windows 凭据管理器里）。
  推送遇凭据故障时的兜底：`python scripts/push_via_wincred.py`（绕开 git 凭据链，直接调 wincred helper 取凭据注入 URL；默认走 git 配置的代理，代理故障时 `--direct` 直连备用）。
- **官网同步（每次发版必做，单独提交）**：`website/changelog.html` 补时间线条目 + 页头版本号、`website/assets/js/site.js` 的 `SITE.version`/`versionCode`、`website/sitemap.xml` 的 `/changelog` lastmod；**另有三类「兜底文本」必须一并同步** —— `index.html` / `features.html` / `changelog.html` 里 `data-version` / `data-version-code` / `data-asset-name` 元素的**标签内静态文本**（被 site.js 在运行时覆写，但禁用 JS / 爬虫 / 查看源码时看的就是这些写死的值；实测曾停在 `v3.71.2`／`290`，落后约 100 个版本）。一条命令完成全部同步：`python tools/update_website.py --no-deploy`（该脚本第 4 步已含兜底文本）。提交信息 `docs(website): 同步 vX.Y.Z 更新日志 vX.Y.Z`。
- **官网部署（每次发版必做，硬规则）**：**只同步文件、不部署 = 线上仍是旧版** —— 实测线上曾停在 `v4.69.0` 落后 5 个版本，成因正是「部署」从未写进硬规则，而发版流程又一律用 `--no-deploy` 跳过了它。部署命令：`npx wrangler pages deploy website --project-name shangkeschedule --branch main`（`python tools/update_website.py` **不带** `--no-deploy` 时会在同步后自动执行）。**`--no-deploy` 仅限本地试跑/预览**，对外发布必须部署，并在收尾记录里写明部署结果。部署后用 `curl.exe` 核验线上版本号与本版一致（Cloudflare Pages 有缓存，未生效时间隔数分钟复查，**不要因一次核验失败就重复部署**）。发版前跑 `python scripts/check_version_sync.py` 确认版本一致（含 `website/*.html` 兜底文本，以及 `version.json` 是否指向最新已发布 tag）。
- **arm64 正式包归档（每次发版必做）**：把当版 `shangke-vX.Y.Z-arm64-v8a-release.apk` 放入仓库外的 `D:\01课程表\正式版-arm64\`（仅此一种包，规则见该目录 `README.md`：一版一包、放入即按 README 第 4 条校验三项）；该目录不入库、不提交。
- **夸克网盘同步（每次发版必做）**：把当版 `shangke-vX.Y.Z-arm64-v8a-release.apk` 上传到夸克网盘「上课-课程表」用户下载目录，并把该目录内**其余全部 .apk 旧包**移入其子目录「旧版本在此」（该目录只留最新一个包）。一条命令：`python scripts\quark_publish_apk.py`（`--dry-run` 只预览）。脚本按目录名现场解析 fid、同名包跳过上传（幂等），并设有**发版闸**：该版本在 GitHub 上没有已发布的 Release 时会拒绝上传，未发版构建须显式加 `--allow-unreleased`。前置：本机已安装并授权夸克网盘 Skill（`C:\Users\30458\.dsh\skills\quarkclouddrive\`）。逐条见 `docs/agents/release-runbook.md` §6。
- **推送 origin 的已知故障**：可能报 `schannel: failed to receive handshake`。**不要**清空代理直连（报 `Connection was reset`）、**不要**切 `http.sslBackend=openssl`（报 `SSL_ERROR_SYSCALL`）；正确做法是等约 20 秒后用 `git ls-remote --heads origin main` 探活，恢复后原样重推。`gitee` 镜像每次同步推送。
- 本地签名依赖 `androidApp/keystore.properties` + `androidApp/shangkeschedule-release.jks`（均已 git 忽略，不入库）。**CI 侧已于 2026-10-06 配置同名 4 个仓库级 secret**（见本节首条）；本地与 CI 使用**同一份**签名材料，故两者产物证书一致。
- 发布完成后在 `工作日志.md` 追加 `BUILD` 记录（构建结果、三包体积、versionCode/ABI/签名校验结论、Release id 与 URL、tag 指向、官网同步提交、origin/gitee 推送状态、是否装机验证）。

## 分支使用规范（强制：每次开工前先对表）

- **主工作区常驻 `main`**：任何工作开始前，主工作区必须处于 `main` 且与远端一致——`git fetch origin && git merge --ff-only origin/main`。因任务临时切出的分支，收尾时必须切回 `main`。
- **每次按任务类型选分支策略**（决策树）：
  1. **单会话小改动**（一个修复 / 一次迭代内可完成）→ 直接在 `main` 上改 → 版本迭代 → 提交 → 推送（保持快进）。
  2. **多步 / 实验性 / 去留未定的工作** → 基于最新 `main` 开**短生命周期分支**：`feat/<主题>` 或 `fix/<主题>`；验证通过合并回 `main` 后**立即删除该分支**，不留长命分支。
  3. **并行会话（本仓库最高频事故源，历史多次互相覆盖）** → **严禁占用主工作区切分支**；一律独立 worktree + 独立分支：`git worktree add D:\01课程表\shangkeschedule-<主题> -b <分支名>`。不读、不写、不切、不删其他会话的 worktree 与分支。
- **worktree 注意事项**：新 worktree 首次构建前必须复制三样文件（`androidApp/keystore.properties`、`androidApp/shangkeschedule-release.jks`、`local.properties`），否则 `validateSigningRelease` 必失败；分支被 worktree 占用时无法删除，须先 `git worktree remove <路径>`；worktree 目录删除遇 Windows「Filename too long」时改用 `rm -rf` 补删（构建缓存深嵌套是常态）。
- **删除分支（任务收尾必做）**：只用安全模式 `git branch -d`，删除前先 `git branch --merged main` 核验。**`-D` 强删未合并分支必须先经用户确认**；远端分支删除（`git push origin --delete`）属外发操作，同样必须经用户确认。
- **推送与同步纪律**：推送前必须 `git fetch` 核对领先 / 落后；本地落后一律 `--ff-only` 快进；出现分叉优先 `rebase` 保持线性历史；推送遇凭据 / 代理问题时按「正式版构建与发布」节的已知故障处理（等约 20 秒探活后原样重推；凭据故障用 `python scripts/push_via_wincred.py` 兜底），**不要**清空代理直连，**不要**切 `http.sslBackend=openssl`。
- **未提交工作的保险**：`stash` 是易失保险——并行会话可能 pop / clear 它（已发生过）；重要在途改动要么尽快提交，要么把补丁写到 `build_qa/`，**不得只依赖 stash**。

## 提交与推送（自动：用户确认即执行）

- **触发**：用户对本次改动表示确认（「可以」「没问题」「提交吧」「结束」等认可表述均算）后，**立即自动完成提交与推送**——不得再次询问、不得把已确认的改动留在工作区不提交。
- **动作序列**：
  1. **先验证**：涉及源码的改动必须先通过编译（`:shared:compileKotlinJvm` + `:desktopApp:compileKotlin` + `:androidApp:assembleDebug`）再提交；
  2. 需要版本迭代的按「版本迭代」节先 bump；
  3. `git add` **一条命令显式列出**本次全部改动文件（`git add -- <文件1> <文件2> …`；禁止 `git add .` / `git add -A`，防止卷入 AI 会话产物、本地杂物与其他会话在途改动）；
  4. 提交信息沿用仓库惯例：`<type>(<范围>): <中文摘要> vX.Y.Z`；
  5. `git push origin <当前分支>`；失败按「分支使用规范」的凭据 / 代理方法重试，结果如实报告（含 gitee 镜像是否同步）。
- **红线（用户确认了也不得入库）**：`*.jks` / `keystore.properties` / `local.properties` / `google-services.json` 等敏感文件；AI 会话产物（`.trae/`、`.zcode/`、`build_qa/`、`工作日志.md`、`tools/`）。
- **例外**：删除远端分支等破坏性外发操作仍需单独确认（见「分支使用规范」）；正式版构建 / GitHub Release 按「正式版构建与发布」节执行。

## Agent skills

### Issue tracker

Issues live in this repo's GitHub Issues (`qiqqqqq517/shangkeschedule`, via the `gh` CLI); the `gitee` remote is a mirror only. See `docs/agents/issue-tracker.md`.

### Triage labels

Default five canonical triage roles (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`), label strings equal to role names. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` + `docs/adr/` at the repo root, created lazily by `/domain-modeling`. See `docs/agents/domain.md`.
