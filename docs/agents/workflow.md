# 开发、验证与提交流程

> 按 `AGENTS.md` 路由读取。本文维护版本、工作区、验证与提交细则；正式发版另见 [发版手册](release-runbook.md)。

## 1. 最小开工流程

1. 按根规则确定读取范围；需要时查 IMA 相关记录与工作日志顶部，从需求中的文件、模块或报错开始定位。入口不明才查 [任务路由](task-routing.md)。
2. 检查状态、分支、worktree。只读任务到此即可；需要改文件时 fetch 一次，确认工作区归属后再快进同步。
3. 小修直接实施；涉及多模块/不确定方案时只列必要步骤，信息不足则先推进独立工作。仅在缺失信息影响正确性、范围或授权时提问。
4. 完成必要验证，符合迭代条件时只 bump 一次；记录实际变化，已获提交授权则提交并同步双端。

```powershell
git status --short
git branch --show-current
git worktree list
git fetch origin
git rev-list --left-right --count main...origin/main
# 仅限当前就是 main 且不会影响他人的在途改动：
git merge --ff-only origin/main
```

命令失败时先处理失败，不能直接执行依赖步骤。只读问答不执行 fetch、merge、版本迭代或构建。

## 2. 分支与隔离

| 情况 | 策略 |
| --- | --- |
| 单会话小改动，一次迭代可完成 | 在干净的 `main` 上改；不动已有无关文件 |
| 多步/实验性工作 | 从最新 `origin/main` 建短分支 `feat/<主题>` 或 `fix/<主题>` |
| 并行会话、主工作区有他人改动 | 独立 worktree + 独立分支，不在主工作区切分支 |

```powershell
git worktree add 'D:\01课程表\shangkeschedule-<主题>' -b 'fix/<主题>' origin/main
```

- 只操作本会话创建的 worktree；新工作区缺本地工具时，从主工作区复制本任务必需的工具，不用它们缺失作为跳过门禁的理由。
- 构建前复制 `local.properties`；正式签名构建另需 `androidApp/keystore.properties` 与 `androidApp/shangkeschedule-release.jks`，均不提交。
- 重要在途改动保存到本会话提交或 `build_qa/` 补丁，不能只依赖共享 stash。
- 推送前再次 fetch 并核对领先/落后：可安全快进时用 `--ff-only`；仅在自己的分支上 rebase，不能改写他人在途工作。
- 自有短分支验证通过、获提交授权后合并回 `main`。清理前核对 `git branch --merged main`，先移除自己的 worktree，再 `git branch -d`。未合并分支强删、远端分支删除另行确认。
- 移除工作区前保存必要产物；Windows 遇长路径时核对绝对目标属于本会话，使用支持长路径的原生方式清理，不跨 shell 拼接删除命令。

## 3. 验证矩阵

先运行能验证本次行为的最小检查；影响 App 的源码/资源改动提交前必须完成三项编译，纯注释按文档处理。已有通过结果可复用，但必须与当前文件内容、构建配置及工具环境一致。

| 变更 | 必需验证 | 版本 |
| --- | --- | --- |
| 纯文档、规则、注释、链接 | `git diff --check`、引用/命令入口核对、规则一致性 | 不升版，不运行 Gradle |
| Kotlin/Compose/Android 源码、资源 | 三项编译；按改动运行相关单测/静态门禁 | FIX patch；FEAT minor |
| 数据库、备份、同步、解析、后台任务 | 三项编译 + 覆盖失败与兼容边界的相关测试 | 按行为变化判定 |
| 学校/适配脚本与索引 | 按适配 SOP 体检、私有双落点与线上核验；App 打包变动再编译 | 新学校 minor；修复 patch |
| 构建/校验脚本、CI 配置 | 语法检查 + 相关成功/失败路径；影响 App 构建再跑三项编译 | 不机械升版，运行时行为变化按 FIX/FEAT |
| 正式版 | 发版手册全部闸门 | 已 bump 的版本不重复 bump |

```powershell
$env:PYTHONUTF8=1
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
# 每次调用 Gradle 前确认以上 JBR 路径有效。
.\gradlew.bat :shared:compileKotlinJvm :desktopApp:compileKotlin :androidApp:assembleDebug --console=plain
# 需要相关业务回归或完整巡检时运行；shared 测试的实际来源是 androidHostTest：
.\gradlew.bat :shared:testAndroidHostTest :androidApp:testDebugUnitTest --console=plain
```

- 不以 `:shared:jvmTest` 的 `NO-SOURCE` 认定 shared 测试通过。核对 XML 报告确实执行了目标测试，记录失败/错误/跳过；不写死历史测试总数。
- 默认使用 Gradle 增量构建；只有缓存/旧产物疑点、门禁反向验证等明确原因才 `--rerun-tasks`。不要为同一份未变源码反复全量验证。
- 静态检查通过 [统一入口](checks.md) 按实际输入选择，不在本文维护第二份触发清单。`--plan --json` 可预览，提交钩子使用 `--staged`，完整巡检显式 `--full`。
- 业务缺陷用有意义的回归测试验证；纯文案或可直接静态核验的小改动不补镜像实现的测试。测试、装机、外部环境的覆盖边界如实记录。
- 不为通过门禁放宽基线或默认使用 `--no-verify`。提交钩子的强制检查仍生效，成功后无需手动再跑同样的检查。

## 4. 一次迭代与日志

验证通过后执行一次（禁止只为查看帮助而调用发布脚本）：

```powershell
python tools/publish_new_version.py --bump <patch|minor|major> --bump-only
```

`FIX → patch`，`FEAT/新增学校 → minor`，破坏性改动 `→ major`，`versionCode` 无条件 +1。纯 DOCS 不迭代。同一批次多个修复合并迭代一次，后续构建/发布使用该版本。

脚本可能更新官网文件；执行后检查 diff、同步 README 徽章并用统一入口验证本次版本文件。官网 `version.json` 仍指向最新已发布版本，不将未发布的代码版本冒充线上版本。

每次改动结束用统一工具追加到工作日志顶部：

```powershell
python tools/worklog/worklog.py append --date YYYY-MM-DD --version X.Y.Z --code NNN --type DOCS --summary '<摘要>' --body-file '<本地正文文件>'
```

类型按实际取 `FEAT/FIX/REFACTOR/DOCS/DATA/BUILD`。正文写原因、关键文件、验证结果与限制；DOCS 使用当前版本。正文、完整日志和笔记草稿保存在 `build_qa/`，不提交。

## 5. 提交与双端同步

授权条件与禁止提交清单见 `AGENTS.md`。已授权后按顺序执行，不重复征询：

1. 确认本次所需验证通过，版本/日志已完成；再次检查工作区是否被其他会话改变。
2. `git add -- <本次全部文件>`；用 `git diff --cached --name-only` 核对范围。空白/红线/新增行密钥由统一门禁检查，不带入无关文件、凭据或会话产物。
3. `git commit -m '<type>(<范围>): <中文摘要> vX.Y.Z'`；纯文档沿用当前版本，不 bump。钩子失败先修复，不绕过门禁。
4. fetch 核对后推送发布分支；主线收尾同步 `origin main` 与 `gitee main`。普通任务不凭空创建 tag；正式版另按发版手册同步 tag。
5. 报告提交号、双端状态与验证限制；无提交授权则交付可审阅 diff，等用户认可后立即提交推送。

网络/凭据故障仅在发生时查本节：

| 现象 | 有界处理 |
| --- | --- |
| `schannel: failed to receive handshake` | 等约 20 秒，`git ls-remote --heads origin main` 探活后原样重推；不清空代理、不切 `openssl` |
| `could not read Password` 等凭据故障 | `python scripts/push_via_wincred.py`；保留已成功的本地提交，令牌只在进程内使用 |
| 确认当前代理不可用 | 先核对故障原因；备用脚本支持 `--direct`，普通握手失败不据此直连 |

同一故障最多尝试 3 次；仍失败保留现场并报告。需要 GitHub API 令牌时用 `gh auth token`，不打印、不落盘，不依赖 `git credential fill`。
