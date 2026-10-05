# 永不停止的检查与修复指令（Watchdog Loop）

> 定位：本文件是给 AI agent 的**常驻循环指令**。启动后，agent 必须无限循环执行
> 「对表 → 检查 → 修复 → 验证 → 记录」，轮与轮之间**不休眠**，没有用户新指令也不得自行停止。
> 上位规则：本文件是仓库根目录 `AGENTS.md` 的执行细则；任何冲突以 `AGENTS.md` 为准。
> 环境：Windows + PowerShell（本机 Bash 不可用，禁止改用 Bash），仓库根 `D:\01课程表\shangkeschedule`。

---

## 0. 两种运行模式

| 模式 | 说明 | 适用 |
| --- | --- | --- |
| **A. 会话内常驻**（主路径） | 把 §9 启动段落原样粘贴给一个新会话，agent 在会话内无限循环 | 有人值守、希望发现即修 |
| **B. 定时单轮兜底**（可选） | 用 `doubao-cron-scheduler` 注册周期任务，每轮只跑一遍 §2 循环体，完成即退出；query 固定为「按 `docs/agents/neverstop-watchdog.md` 执行一轮巡检修复，只做一轮，完成即退出」 | 防止会话关闭后无人巡检 |

两种模式可同时存在；模式 B 不得执行 §6 的提交推送之外的任何外发动作（与模式 A 同规则）。

---

## 1. 铁律（每轮开工前逐条对表，违反即事故）

1. **主工作区常驻 `main`**：每轮先 `git fetch origin && git merge --ff-only origin/main`；非快进即挂起，禁止在主工作区 rebase 他人在途工作。
2. **不切分支、不碰 worktree**：watchdog 的修复属单会话小改动，直接在 `main` 上做；`git worktree list` 里其他会话的 worktree 不读、不写、不删。
3. **`git add` 必须显式列文件**：`git add -- <文件1> <文件2> …`；**严禁** `git add .` / `git add -A`。
4. **红线文件永不 add**：`*.jks`、`keystore.properties`、`local.properties`、`google-services.json`、`adapter_secrets.properties`。
5. **AI 产物与本地工具不 add**：`.trae/`、`.zcode/`、`build_qa/`、`工作日志.md`、`tools/`；根目录已有的未跟踪杂物（如 `FIXLOG.md`、`REVIEW.md`、`*.design/`、`gradle_t*.log`）不是本次修复产生的，**一律不碰不 add**。
6. **严禁 CI 发版**：watchdog 永不执行正式版构建、GitHub Release、夸克网盘上传、arm64 归档——这些是人工发版事件，规则见 `AGENTS.md`「正式版构建与发布」。
7. **不做破坏性外发**：不 force push、不删分支（`-D` / `push --delete` 一律停机等人）、不改远端设置。
8. **`JAVA_HOME` 本机是坏的**：每条 Gradle 命令前显式设为 `C:\Program Files\Android\Android Studio\jbr`。
9. **编码**：Python 命令前设 `$env:PYTHONUTF8=1`；PowerShell 读文本文件带 `-Encoding utf8`，避免 GBK 误读产生 mojibake。
10. **只陈述事实**：测试没跑就写没跑、装不了机就写未装机、修不了就挂起；**禁止编造或预设任何检查结果**。
11. **不扩大范围**：只修检查发现的问题，不做「顺手重构 / 顺手优化 / 顺手补功能」；新增功能（FEAT）不应出现在 watchdog，出现即说明越界，挂起等人。
12. **先读工作日志**：会话启动后、首次动手前，用 Read 读 `工作日志.md` 最新改动区，掌握当前版本与遗留问题。

---

## 2. 主循环（伪代码）

```
while true:
    轮次号 += 1
    A. L0 工作区对表
    B. L1 静态门禁（全部跑完，收集完整失败清单，不因首个失败中断）
    C. L2 编译三关（L2 失败则 L3 不跑）
    D. L3 单元测试
    E. L4 周期深检（到达间隔才跑）

    if 存在失败项:
        逐个失败项按 §5 修复协议处理（一次一个根因）
        每个修复完成后，从受影响层级向下重跑全部验证
        验证通过的修复，按 §6 自动收尾（bump → 工作日志 → 提交 → 推送）
        修不了的，写入 build_qa/watchdog/pending.md，继续下一项
    else:
        本轮全绿

    写本轮报告 build_qa/watchdog/round-*.md
    按 §2.1 决定该轮是否上传 IMA
    立即进入下一轮（不休眠，见 §8）
```

### 2.1 轮次报告上传 IMA（2026-10-04 起，用户指令）

每轮报告写完后，按下列**条件式**策略处理：

```
node build_qa/watchdog/ima_upload_round.cjs <round-N.md 绝对路径>
```

| 本轮性质 | 是否上传 IMA |
| --- | --- |
| **有内容**：修复了缺陷 / 发现新问题 / 挂起清单有增删或状态变化 / L4 深检到期执行 | **上传** |
| **全绿且无事**：无修复、无新发现、挂起无变化、L4 未到期 | **不上传**，仅仓库内留 `round-N.md` |

- **目标位置**：IMA 知识库「**上课**」→ 文件夹「**07-问题复盘**」；
  `knowledge_base_id` / `folder_id` 由脚本**按名称现场解析**，不写死内部 ID。
- **安全门**：`preflight → check_repeated_names → create_media → cos-upload → add_knowledge`，
  任一步失败**立即停止**并把 `msg` 写入本轮报告的"挂起"节，不得静默跳过。
- ⚠️ **上传后必须回查**：`add_knowledge` 返回 `code=0` **不等于**落到了目标文件夹
  （`folder_id` 非顶层字段时会静默落进知识库根目录）。
  须用 `get_knowledge_list` 确认条目 `parent_folder_id` 等于目标 `folder_id`。
- 全绿轮不传 IMA 是**刻意为之**：IMA 是检索层，不是流水账层；
  几十条内容雷同的"全绿"会淹没真正有价值的记录。
  全部轮次**始终**在仓库内保留 `round-N.md`，IMA 只是其中"有内容"部分的检索入口。

---

## 3. 检查清单（逐条可执行命令）

### L0 · 工作区对表（每轮）

```powershell
cd "D:\01课程表\shangkeschedule"
git fetch origin
git merge --ff-only origin/main
git branch --show-current      # 必须输出 main
git status --short
git ls-files | Select-String -Pattern '\.jks$|keystore\.properties$|local\.properties$|google-services\.json$|adapter_secrets\.properties$'
```

判读：

- 最后一条命令**必须无输出**；若红线文件已被跟踪 → 最高优先级事故，挂起等人，不得自行处理历史。
- `git status` 出现**不属于自己**的已修改 / 已暂存文件 → 判定为并行会话在途，本轮只报告、不动手，挂起。
- `merge --ff-only` 失败（本地有提交或分叉）→ 挂起，不 rebase、不 reset。

### L1 · 静态门禁（每轮；便宜，先跑）

```powershell
$env:PYTHONUTF8=1
python scripts/check_version_sync.py
python scripts/check_theme_leak.py --baseline scripts/theme-leak-baseline.json
python scripts/check_a11y.py --baseline scripts/a11y-baseline.json
python scripts/check_widget_contrast.py
python scripts/check_adapters.py
python scripts/check_font_scale.py
```

各门禁口径：

| 脚本 | 门禁语义 | 失败含义 |
| --- | --- | --- |
| `check_version_sync.py` | `androidApp/build.gradle.kts` ↔ `website/assets/js/site.js` ↔ `README.md` 版本三处一致 | 版本号不同步，用户可能从官网拿到旧包 |
| `check_theme_leak.py` | 棘轮：组件层主题泄漏等违规数**不得高于基线** | 新增了主题分支或 token 缺陷 |
| `check_a11y.py` | 棘轮：不可辨识可操作节点数**不得高于基线** | 新增 TalkBack 不可用的控件 |
| `check_widget_contrast.py` | 小组件文本 ≥ 4.5:1、承载信息的非文本色 ≥ 3:1（WCAG 2.2 AA） | 小组件配色回归 |
| `check_adapters.py` | 195+ 教务适配脚本静态体检 + 主/私双落点一致性（LF 归一化） | 适配脚本契约/语法缺陷或双落点漂移 |
| `check_font_scale.py` | 审计项，exit 0；输出 `?` 为「可能裁切」启发式判定 | **记录 `?` 数量；数量较上轮增长即挂起**，不自行改布局 |

棘轮纪律（`theme_leak` / `a11y`）：修复方向只能是**消除违规**；`--update-baseline` 仅允许在违规数**净下降**时锁定进步，且必须在工作日志写明。**严禁**为让门禁变绿而放宽基线。

### L2 · 编译三关（每轮）

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :shared:compileKotlinJvm :desktopApp:compileKotlin :androidApp:assembleDebug --console=plain
```

三关全绿才算过。任一失败 → 收集报错，L3 不跑。

### L3 · 单元测试（每轮，L2 通过后）

```powershell
.\gradlew.bat :shared:testAndroidHostTest :androidApp:testDebugUnitTest --rerun-tasks --console=plain
```

> ⚠️ **任务名订正（2026-10-04，v4.68.5）**：本节原先写的是 `:shared:jvmTest`，
> 而该任务在本仓是 **`NO-SOURCE`** —— `shared/src/jvmTest` **目录不存在**，
> 全部 shared 测试文件都在 `shared/src/androidHostTest`。
> 即原命令对 shared 的一批测试零覆盖，却照样报 `BUILD SUCCESSFUL`，
> 是一条**恒绿的假门禁**（历史口径「221 例 / 273 例」实际都来自 `testAndroidHostTest`）。
> 正确任务名为 `:shared:testAndroidHostTest`。已获用户授权订正。

**加 `--rerun-tasks` 的理由**：不加时 Gradle 会在源码未变时直接复用上轮产物，
"测试通过"只代表**上一轮**的结果；watchdog 的用途是抓**当轮**回归，
故每轮必须真实重跑。

**必须核对执行数**：仅看 `BUILD SUCCESSFUL` 不足 ——
`NO-SOURCE` 的任务同样会让整体构建成功。须解析测试报告确认
执行数 = `@Test` 注解数（本仓口径见下），若二者不等即说明有测试被静默跳过。

**测试数量口径（P2-19 · 2026-10-05 修正）**：此前本节把总数写死为 276，
而仓库同时存在 229 / 265 / 273 / 276 四个数字在不同文档里并行引用，
巡检无法判断「多少才算对」。现改为**现场核对**：

- 当前实测（2026-10-05）：**291 例 / 0 失败 / 0 错误 / 0 跳过**
  （`:shared:testAndroidHostTest` 224 + `:androidApp:testDebugUnitTest` 67；
   源集为 shared 28 文件 / androidApp 8 文件）。
- **每轮以实跑为准**：解析 `build/test-results/**/TEST-*.xml` 的 `tests` 属性求和，
  再与 `@Test` 注解计数对拍；二者不等即判 FAIL 并挂起。
- 数字只应因**新增测试**而增加；无理由减少即挂起。
- **文档不得再写死历史数字**（`工作日志.md` 的历史记录除外，它记录的是当时事实）。

### L4 · 周期深检（每 4 小时一次，与轮次解耦计时）

1. **提交与工作日志对账**：`git log --oneline -20` 每个近期提交都能在 `工作日志.md` 找到对应记录；工作日志顶部版本号与 `androidApp/build.gradle.kts` 连续不跳号。
2. **tag 与 CHANGELOG 对账**：最新 git tag 与 `CHANGELOG.md` 顶部条目一致（watchdog 自身只产生未发版的 patch，不要求 CHANGELOG 条目）。
3. **文档引用抽查**：`docs/` 下文档点名的文件路径存在；检查脚本放 `build_qa/watchdog/`（不入库），发现失效引用即挂起或修复（仅当修复是补回被移动/改名的文件且证据明确时）。
4. **worktree 与分支残留**：`git worktree list`、`git branch` 只报告不处理（非本会话创建的不碰）。
5. **磁盘与缓存**：系统盘剩余空间、`build/` 体积；低于 5 GB 只报告，**不擅自删除**任何缓存。
6. **真机覆盖盲区清单复核**：`工作日志.md` 中记录的「真机才能验」的链路（日历写回、响铃/勿扰、通知投递、桌面放置、权限广播）维持「未装机验证」标注，不得在报告中翻转为已验证。

---

## 4. 检查阶段的完整性要求

- L1 必须**全部六条跑完**再统一汇总，不得第一条失败就停。
- L2 编译失败时 L3 跳过，并在报告中注明「因 L2 失败未执行」。
- 每轮报告必须逐层标注 PASS / FAIL / SKIP，SKIP 必须写明原因。

---

## 5. 修复协议（决策树）

```
失败项
 ├─ 1. 读报错原文与门禁输出（不凭印象，必要时重跑取完整日志）
 ├─ 2. 定位根因：用证据定位（复现 / 日志 / 源码对照）；找不到根因不许改代码
 ├─ 3. 一次只改一个根因，最小 diff，不夹带其他改动
 ├─ 4. 验证：
 │     a. 重跑失败的那条门禁 / 编译 / 测试
 │     b. 新增或修改过门禁逻辑时，强制做反向验证（临时还原缺陷确认门禁变红，再恢复）
 │     c. 从受影响层级向下全量重跑：L1 改动 → L1+L2+L3；L2 源码改动 → L2+L3
 ├─ 5. 同一失败项连续 2 轮修复仍未通过 → 停止重试，写入 pending.md，跳到下一项
 └─ 6. 命中 §7 硬停机条件 → 立即挂起等人
```

修复手段优先级：**改代码/资源消除缺陷 > 补门禁防复发 > 证明门禁误判后调整规则**。
调整任何检查规则必须给出误判证据（文件、行号、判定值），写进工作日志。

---

## 6. 验证通过后的自动收尾（用户已通过本指令预先授权）

> 仅适用于：修复已通过 §5 全部验证、且不涉及任何 §7 硬停机条件的情形。

1. **判定类型**：watchdog 的修复一律为 `FIX`。若实际需要 FEAT / 破坏性改动，说明越界，不收尾、挂起。
2. **版本迭代**（FIX → patch，versionCode 自动 +1；只迭代不构建）：
   ```powershell
   $env:PYTHONUTF8=1
   python tools/publish_new_version.py --bump patch --bump-only
   ```
   该脚本会自动调用 `tools/update_website.py --no-deploy` 同步 `website/assets/js/site.js`、
   `website/changelog.html`、`website/sitemap.xml`；若输出同步失败提示，挂起等人手动处理。
3. **手动改 README 徽章**（无脚本同步，历史教训：徽章曾落后多个版本）：
   把 `README.md` 第 11 行附近 `badge/version-X.Y.Z-blueviolet` 改为新版本号。
4. **重跑版本门禁**确认三处一致：
   ```powershell
   python scripts/check_version_sync.py
   ```
5. **追加工作日志**（先把正文写入 `build_qa/watchdog/wl-body.md`，再调用）：
   ```powershell
   python tools/worklog/worklog.py append --date <YYYY-MM-DD> --version <新版本> --code <新versionCode> --type FIX --summary "<一句话摘要>" --body-file build_qa/watchdog/wl-body.md
   ```
   正文必须写明：失败项与报错、根因、涉及关键文件、验证方式与结果（含反向验证）、
   **验证状态（编译 / 未装机）**——watchdog 修复默认未装机，如实标注。
6. **显式 add（只列本次文件）**，例如：
   ```powershell
   git add -- shared/src/commonMain/kotlin/.../Foo.kt androidApp/src/main/res/values/colors.xml androidApp/build.gradle.kts website/assets/js/site.js website/changelog.html website/sitemap.xml README.md
   ```
   `工作日志.md`、`tools/`、`build_qa/` **不 add**。add 后用 `git status --short` 核对暂存清单，发现多余文件立即 `git reset HEAD <文件>` 撤出。
7. **提交**（沿用仓库惯例）：
   ```powershell
   git commit -m "fix(<范围>): <中文摘要> vX.Y.Z"
   ```
8. **推送**：
   ```powershell
   git push origin main
   ```
   - 遇 `schannel: failed to receive handshake`：等约 20 秒，`git ls-remote --heads origin main` 探活，恢复后原样重推；**不**清空代理、**不**切 `http.sslBackend=openssl`。
   - 遇凭据故障 `could not read Password ...`：`python scripts/push_via_wincred.py` 兜底（代理故障加 `--direct`）。
   - 推送连续失败 3 次：挂起；**本地提交保留不回退**。
   - gitee 镜像按同样方式同步推送，结果如实报告。
9. **watchdog 永不执行**：CHANGELOG 填充、正式版构建、GitHub Release、夸克上传、arm64 归档（均属发版流程，见 `AGENTS.md`）。

---

## 7. 硬停机条件（立即停止自动修复，保留现场，等人指示）

出现以下任一情形，停止当前修复动作、写入 `build_qa/watchdog/pending.md` 与轮次报告，
随后只继续巡检**其他无关项**，不得重试该问题：

1. 需要改签名、密钥、凭据、`adapter_secrets.properties` 或私有适配仓库写入。
2. 需要真机验证：日历写回、响铃/勿扰切换、通知投递、桌面组件放置、权限变更广播等 JVM 测不到的链路。
3. 需要修改 `AGENTS.md` 规则本身、本文件或发版流程。
4. 需要删除/移动用户文件、删除分支、force push、撤销提交。
5. 同一失败项连续 2 轮修复失败（§5）。
6. 依赖下载 / 镜像 / 网络类错误重试 3 次仍失败。
7. Gradle OOM、机器资源不足等环境问题（冷却后仍复现）。
8. 修复需要新功能、新文案、设计或产品决策。
9. 发现红线文件被跟踪、或工作区存在来源不明的改动（并行会话事故苗头）。
10. 用户直接下达新指令——立即让位，先处理用户指令。

---

## 8. 节奏（不休眠）

**轮与轮之间零间隔**：本轮报告写完立即开始下一轮，全程不 sleep、不等待、不做轮间退避。
全绿不是收工理由，也不是减速理由——全绿轮只说明本轮无失败项，下一轮照常从 L0 开始。
零间隔下无需「origin 新提交提前唤醒」机制：每轮 L0 都会 `git fetch`，新提交自然在下一轮被拉取。

| 情形 | 处理（均不停止循环） |
| --- | --- |
| 全绿 | 立即开始下一轮 |
| L4 深检 | 每 4 小时一次（用实际时间判断，重启后不重算），到点在该轮 L3 后追加执行 |
| 瞬时网络错误 | 该条命令等 20 秒重试，最多 3 次，仍失败则该项挂起（§7.6） |
| 机器繁忙 / Gradle OOM | 该项冷却 5 分钟后重试一次，仍复现则按 §7 挂起，继续其他项 |
| 模式 B（定时单轮） | 由外部 cron 触发周期，任务内单轮即退，与本节无关 |

上表中的「等 20 秒 / 冷却 5 分钟」是**单条失败命令的有界重试间隔**，不是轮间休眠；
重试用尽即挂起该项，循环本身永不因等待而停止。

---

## 9. 启动段落（新会话原样粘贴即用）

```
从现在起你是 shangkeschedule 仓库的常驻巡检修复 agent。要求：
1. 先用 Read 读取 D:\01课程表\shangkeschedule\工作日志.md 最新改动区；
2. 严格按 D:\01课程表\shangkeschedule\docs\agents\neverstop-watchdog.md 执行无限循环
   （对表 → L1 静态门禁 → L2 编译三关 → L3 单测 → 周期深检 → 修复 → 验证 → 记录 → 立即下一轮）；
3. 该文件的铁律、修复协议、自动收尾流程、硬停机条件、节奏规则逐条遵守，不得自行变通；
4. 轮与轮之间不休眠，上一轮结束立即开始下一轮，全绿也照常继续，永不自行收工；修不了的写入 build_qa\watchdog\pending.md 并继续巡检其他项；
5. 每轮结果写 build_qa\watchdog\round-*.md，修复验证通过后按文件 §6 自动 bump、记录、提交、推送；
6. 我下达新指令时立即让位处理。现在开始第 1 轮。
```

---

## 附录 · 挂起清单 `build_qa/watchdog/pending.md` 条目格式

```
### [发现时间 YYYY-MM-DD HH:mm] 层级(L0~L4) · 一句话标题
- 报错摘要：（原文关键行，不改写）
- 已尝试：（每轮做了什么、结果如何）
- 阻塞原因：对应 §7 第几条
- 需要用户做什么：（具体动作，如「真机装机验证日历同步四条路径」）
- 状态：待处理 / 已解除（解除时注明时间与对应版本）
```
