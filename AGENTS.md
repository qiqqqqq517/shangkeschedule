# AGENTS.md

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

## 正式版构建与发布（强制：本地构建 + 手动上传）

- **严禁使用 CI（GitHub Actions）构建正式版**。`.github/workflows/android-build.yml` 与 `android-release.yml` 仅作历史保留，已在仓库 Actions 中禁用，**不得再作为发布路径**（`settings.gradle.kts` 的阿里云镜像开关 `-PuseMirror` 亦因此仅在本地生效，默认开启）。
- 正式版一律**本地构建**：`./gradlew :androidApp:assembleRelease`，产物为 `androidApp/build/outputs/apk/release/shangke-vX.Y.Z-<abi>-release.apk`（按 ABI 拆分，arm64-v8a / armeabi-v7a / x86_64）。
- 构建完成后**手动上传**到 GitHub Release（`gh release create`），说明取自 `CHANGELOG.md` 对应版本条目。
- 本地签名依赖仓库根目录 `keystore.properties`（已 git 忽略，不入库）；CI 侧不再需要签名密钥。
- 发布完成后按上一节补 `CHANGELOG.md`，并在 `工作日志.md` 追加 `BUILD` 记录。

## 分支使用规范（强制：每次开工前先对表）

- **主工作区常驻 `main`**：任何工作开始前，主工作区必须处于 `main` 且与远端一致——`git fetch origin && git merge --ff-only origin/main`。因任务临时切出的分支，收尾时必须切回 `main`。
- **每次按任务类型选分支策略**（决策树）：
  1. **单会话小改动**（一个修复 / 一次迭代内可完成）→ 直接在 `main` 上改 → 版本迭代 → 提交 → 推送（保持快进）。
  2. **多步 / 实验性 / 去留未定的工作** → 基于最新 `main` 开**短生命周期分支**：`feat/<主题>` 或 `fix/<主题>`；验证通过合并回 `main` 后**立即删除该分支**，不留长命分支。
  3. **并行会话（本仓库最高频事故源，历史多次互相覆盖）** → **严禁占用主工作区切分支**；一律独立 worktree + 独立分支：`git worktree add D:\01课程表\shangkeschedule-<主题> -b <分支名>`。不读、不写、不切、不删其他会话的 worktree 与分支。
- **worktree 注意事项**：新 worktree 首次构建前必须复制三样文件（`androidApp/keystore.properties`、`androidApp/shangkeschedule-release.jks`、`local.properties`），否则 `validateSigningRelease` 必失败；分支被 worktree 占用时无法删除，须先 `git worktree remove <路径>`；worktree 目录删除遇 Windows「Filename too long」时改用 `rm -rf` 补删（构建缓存深嵌套是常态）。
- **删除分支（任务收尾必做）**：只用安全模式 `git branch -d`，删除前先 `git branch --merged main` 核验。**`-D` 强删未合并分支必须先经用户确认**；远端分支删除（`git push origin --delete`）属外发操作，同样必须经用户确认。
- **推送与同步纪律**：推送前必须 `git fetch` 核对领先 / 落后；本地落后一律 `--ff-only` 快进；出现分叉优先 `rebase` 保持线性历史；推送遇凭据 / 代理问题时用 `gh auth token` 注入 URL 或 `-c http.proxy= -c https.proxy=` 直连（本机已知问题）。
- **未提交工作的保险**：`stash` 是易失保险——并行会话可能 pop / clear 它（已发生过）；重要在途改动要么尽快提交，要么把补丁写到 `build_qa/`，**不得只依赖 stash**。

## 提交与推送（自动：用户确认即执行）

- **触发**：用户对本次改动表示确认（「可以」「没问题」「提交吧」「结束」等认可表述均算）后，**立即自动完成提交与推送**——不得再次询问、不得把已确认的改动留在工作区不提交。
- **动作序列**：
  1. **先验证**：涉及源码的改动必须先通过编译（`:shared:compileKotlinJvm` + `:desktopApp:compileKotlin` + `:androidApp:assembleDebug`）再提交；
  2. 需要版本迭代的按「版本迭代」节先 bump；
  3. `git add` **逐个显式**暂存本次改动文件（禁止 `git add .` / `git add -A`，防止卷入 AI 会话产物与本地杂物）；
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
