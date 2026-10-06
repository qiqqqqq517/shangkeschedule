# 代码优化指导计划 · OPT（ShangKeSchedule）

> **定位**：本文件是仓库的**第三本台账**，专门回答「代码该优化什么、按什么顺序、动之前要满足什么前提、怎么算做完」。
> 它**不重新审计、不复制证据原文、不做功能开发** —— 每条只给「来源台账编号 + 证据锚点 + 改动范围 + 开工前提 + 退出判据」，明细一律回引原台账。
>
> **素材基线**：`main` @ `d9c4e61`（**v4.68.4 / versionCode 474**），取证时刻 **2026-10-03**（本文件成稿的同一会话实测）。
> **上位规则**：仓库根 `AGENTS.md`；巡检与收尾细则 `docs/agents/neverstop-watchdog.md`。
> **状态**：本文件本身是**计划**，不含任何代码改动。其中每条的落地状态以执行时的代码与门禁输出为准。

**标注约定**

| 标记 | 含义 |
|---|---|
| 【实测】 | 本文件成稿会话**亲自跑命令/读代码**得到，带取证时刻 |
| 【台账】 | 引自 `REVIEW.md` / `build_qa/watchdog/pending.md` / IMA 笔记快照，**本轮未逐条回代码复核**，开工前必须复核 |
| 【已消除】 | 台账里记为待办，但本轮实测确认**已不成立**，见附录 B |

---

## 0. 这份计划解决什么问题

### 0.1 三本台账的分工（先看清边界，避免第三份副本）

| 文件 | 性质 | 编号 | 是否入库 |
|---|---|---|---|
| `REVIEW.md`（仓库根） | 全自动深度审查台账（14 轮 + 待批清单） | `R1-*` / `R2-*` / `R4-*` / `R6-*` | ❌ 未跟踪（AI 台账） |
| `docs/optimization-checklist-xinglian-benchmark.md` | 竞品（星链）对标台账 | `XL-*` | ✅ 已入库 |
| **本文件** | **优化执行路线 + 判据 + 协议** | **`OPT-*`** | ✅ 计划入库 |
| `docs/agents/neverstop-watchdog.md` | 巡检协议（L0–L4 / 修复协议 / 收尾 / 停机条件） | — | ✅ 已入库 |
| `build_qa/watchdog/{pending.md, round-*.md, audit_*.py}` | 巡检现场与量具（约 150 个脚本） | — | ❌ 不入库（会话产物） |
| `工作日志.md` / `CHANGELOG.md` | 开发过程日志 / 面向用户的发布说明 | — | 前者不入库、后者入库 |

**本文件与它们的唯一区别**：那三本是**记录**（发现了什么、对标了什么、巡检了什么），本文件是**排期与约束**（先做哪个、做不到什么程度不算完、哪些绝对不能碰）。

### 0.2 编号约定

本文件用 `OPT-` 前缀，**刻意不与** `R*`（审查）、`XL-*`（对标）冲突。同一件事在多个台账出现时，**一律以原台账编号为准**，`OPT-*` 只做排期引用：

> 例：`OPT-201`（周课表错误态）= `R1-011` 的剩余部分 = IMA《06》的 `U-1`/`U-3` = `P-55`。

### 0.3 工作量档位

沿用对标清单口径：**S** ≤0.5 天 · **M** 1–2 天 · **L** 3–5 天 · **XL** >5 天。全部为**单人单会话**估时，不含真机验证等待。

### 0.4 维护约定（本文件自己也要守纪律）

1. **数字必须带取证时刻**。本仓已经吃过「验收基线同时是统计对象」的亏（对标清单 §0.6 漂移台账）：凡是能被静态计数验证的数字（测试例数、文件数、行数、门禁违规数），**声称「当前」的必须准确，带日期的历史记录不改写**。
2. **执行完成一条，就把该条状态改为 `✅ 已落地 vX.Y.Z(code)` 并留一行证据**，不静默删除条目。
3. **纯文档变更不 bump 版本**。本文件本身按 `DOCS` 记一条工作日志即可，不做 `--bump`。
4. **回写纪律**：某条落地后若改变了「架构 / 决策 / 进度 / 问题状态」，按 `AGENTS.md` 同步 IMA 对应篇章，**只追加或留痕更正，不静默覆盖**。
5. **本文件不得成为第四条发版路径**。正式版构建 / Release / 夸克上传 / arm64 归档只在 `AGENTS.md`「正式版构建与发布」节规定的流程里发生。

---

## 1. 指导原则（六条，全部出自本仓自己的事故证据）

> 这六条不是通用最佳实践，而是**本仓库付过学费换来的判据**。任何 OPT 项的实施方式与之冲突时，以本节为准。

### 原则 1 · 量具可信度优先于修复数量

**本仓最贵的事故不是缺陷，是假门禁。** 三次实证：

| 假门禁 | 症状 | 证据 |
|---|---|---|
| L3 用了 `:shared:jvmTest` | 该任务是 `NO-SOURCE`，**shared 层 209 例全部静默漏跑**，Gradle 仍报 `BUILD SUCCESSFUL`，巡检报告照写「L3 PASS」 | `build_qa/watchdog/pending.md` 轮 1；已于 `d9c4e61` 订正 |
| 门禁脚本 `pkg=""` 使 `action.startswith(pkg)` **恒真** | 规则永远不可能发火，稳定输出假 PASS | `build_qa/watchdog/round-48.md` §3.7；`P-61` |
| 审计只硬编码扫 3 个文件，实际 4 个 XML / 5 个 string-array / 1357 个资源名 | 写出了**越界的全局结论** | `round-45.md` §一.4；`P-62` |

⇒ **排序含义**：阶段 1 排在所有修复之前，因为它给后面每个阶段提供可信的验收工具。**先保证尺子是准的，再量东西。**

### 原则 2 · 「看起来对」必须由执行回答

`progress_percent_format` 三语被**按「Compose 会用 `String.format`」的推测**改成 `%1$d%%`（占位符编号一致、key 齐全、三语对齐，**所有文本比对全部通过**）；而 Compose Multiplatform 的资源插值实为**一次正则替换**（`Regex("""%(\d+)\$[ds]""")`，见 `components-resources` 的 `StringResourcesUtils.kt`），**不经 `String.format`** ⇒ `%%` 不会被还原，学期进度行真的渲染出「已过 45%%」。**推测的事实**（以为会抛异常）与**真实的事实**（多出一个 `%`）都只有执行能回答。（2026-10-07 订正）

> **通用判据**：格式串是否合法、谓词是否可能为真、契约是否成立 —— **只有执行能回答，文本比对不能。**

### 原则 3 · 常量名与注释不等于契约

- 从竞品逆向出 `ACTION_WIDGET_HIDDEN = "android.appwidget.action.APPWIDGET_HIDDEN"`，照抄到原生 Android **必然不生效且不报错**（该常量是竞品自定义字面量）。判据两条：① SDK 源码里有没有同名常量；② 真机 framework 里有没有这个字符串。
- `values/colors.xml` 注释里的对比度断言曾**错抄六处**（`v4.67.26`）。
- **从别的字段的格式反推出来的 ID ≠ 那个 ID 本身**（2026-10-03 新增）：写回 IMA 时把知识库条目的 `media_id` 去掉 `note_` 前缀当 `note_id`，API 返回 `code 210005 GetNoteContent not author` —— 报错文案直指「不是作者」，**看起来就是权限问题**，于是做出「凭据无作者权限、回写无法代办」的错误结论。真实构造是 `note_<md5>_<note_id><知识库根文件夹 ID>`，`note_id` 只是其中 16 位数字那一段；用 `search_note` 取到真实 `note_id` 后**读取与追加全部成功**。
  ⇒ **接口的报错文案不等于根因**：先证伪「我传的参数构造对不对」，再谈权限/环境。
  ⇒ 配套铁律：**写操作必须回读校验**（`code=0` 只代表请求被受理，不代表落在你想落的位置 —— `add_knowledge` 就曾因 `folder_id` 不是顶层字段而静默落进知识库根目录）。

### 原则 4 · 一次一个根因，最小 diff，不夹带

禁止「顺手重构 / 顺手优化 / 顺手改视觉基线 / 顺手补功能」。视觉一致性（圆角 / 按钮高 / 胶囊边距）属**需设计定 token** 的判断项，**不自动改**。新增功能（`FEAT`）不该出现在巡检类工作里，出现即越界。

### 原则 5 · 不制造无法验证的改动

`MIGRATION_2_3` 的 `DROP TABLE courses` 在「外键在 `onOpen` 之后才 `PRAGMA foreign_keys = ON`」的分析下理论上有风险，但**只影响未发布的 schema 2/3 设备、且改了也验不了** ⇒ 定案：记录，不动。

### 原则 6 · 棘轮只许收紧，新门禁必须自带反向验证

`theme_leak` / `a11y` 是棘轮门禁：修复方向只能是**消除违规**，`--update-baseline` **仅在违规数净下降时**允许锁定进步，**严禁为让门禁变绿而放宽基线**。

新增任何门禁/量具，必须同时给一条**反向验证**：临时还原缺陷 ⇒ 门禁**必须变红**。做不到反向验证的门禁就是下一个恒绿门禁（原则 1）。

---

## 2. 实测基线（【实测】本会话 @ `main` `d9c4e61`，2026-10-03）

> 后续任何改动都拿这张表判回归。复现命令见附录 A。

### 2.1 版本与测试口径

| 指标 | 值 | 取证方式 |
|---|---|---|
| versionName / versionCode | **4.68.4 / 474** | `androidApp/build.gradle.kts` |
| HEAD | `d9c4e61`（docs(agents): 订正 watchdog L3 命令…） | `git log --oneline -1` |
| shared 单测 | **26 文件 / 209 例**（`shared/src/androidHostTest`） | `@Test` 注解计数 |
| androidApp 单测 | **8 文件 / 67 例**（`androidApp/src/test`） | 同上 |
| **测试总口径** | **276 例 / 0 失败 / 0 错误 / 0 跳过** | 与 `工作日志.md` v4.68.4 记录一致 |

> 📌 **上表是 2026-10-04 v4.68.4 的历史快照，不是当前值**（P2-19 · 2026-10-05 标注）。
> 当前实测口径 = **291 例 / 0 失败 / 0 错误 / 0 跳过**
> （`:shared:testAndroidHostTest` **28 文件 / 224 例** + `:androidApp:testDebugUnitTest` **8 文件 / 67 例**，
> 两种计数法 `@Test` 注解与 Gradle XML 首次闭合对拍）。
> **判定「多少例算对」时不要引用本表**，以 `docs/agents/neverstop-watchdog.md` §L3 的现场核对流程为准。

> ⚠️ **口径陷阱（本仓已踩过）**：`:shared:jvmTest` 在本仓是 `NO-SOURCE`（不存在 `shared/src/jvmTest` 源集），跑它**零覆盖却报成功**。真实任务是 `:shared:testAndroidHostTest`。且必须加 `--rerun-tasks`（否则复用上轮产物，「通过」只代表上一轮）并**核对执行数**（`NO-SOURCE` 不看执行数就会被骗）。

### 2.2 代码规模与 God 文件

| 指标 | 值 |
|---|---|
| Kotlin 总量 | **420 个 `.kt` / 90,682 行**（`shared` + `androidApp` + `desktopApp`，不含 `iosApp`） |
| `>600` 行的文件 | **30 个** |

Top-8 单文件行数：

| 文件 | 行数 |
|---|---|
| `shared/.../ui/today/TodayScheduleScreen.kt` | 2690 |
| `shared/.../ui/agenda/AgendaScreen.kt` | 1968 |
| `shared/.../ui/settings/coursetables/ManageCourseTablesScreen.kt` | 1883 |
| `shared/.../ui/settings/time/TimeSlotManagementScreen.kt` | 1593 |
| `shared/.../ui/schedule/WeeklyScheduleScreen.kt` | 1422 |
| `shared/.../ui/theme/AppStyle.kt` | 1274 |
| `shared/.../ui/schedule/WeeklyScheduleViewModel.kt` | 1262 |
| `shared/.../ui/schedule/components/CourseDetailBottomSheet.kt` | 1118 |

### 2.3 质量门禁现状

| 层 | 内容 | 位置 |
|---|---|---|
| L1 | **6 道**静态门禁：`check_version_sync` / `check_theme_leak` / `check_a11y` / `check_widget_contrast` / `check_adapters` / `check_font_scale` | `scripts/` |
| L1 棘轮基线 | `scripts/theme-leak-baseline.json`、`scripts/a11y-baseline.json` | `scripts/` |
| L1 运行器 | `run_l1.py` —— ⚠️ **仍在 `build_qa/watchdog/`（不入库）** | 会话产物 |
| L2 | `:shared:compileKotlinJvm :desktopApp:compileKotlin :androidApp:assembleDebug` | — |
| L3 | `:shared:testAndroidHostTest :androidApp:testDebugUnitTest --rerun-tasks` | — |
| L4 | 每 4 小时周期深检（提交/日志对账、tag↔CHANGELOG、文档引用、worktree、磁盘、真机盲区清单） | `neverstop-watchdog.md` §3 |
| 性能 | PF6 性能基线（6 场景）—— **`build_qa/soft_jank/baseline.json` 仍空缺** | `docs/agents/perf-baseline.md` |

**`check_version_sync.py` 覆盖 3 处**（【实测】）：`androidApp/build.gradle.kts` ↔ `website/assets/js/site.js` ↔ `README.md`。**`website/version.json` 未纳入**（`E-21`）。

### 2.4 量具存量（本仓最重要的隐形资产）

`build_qa/watchdog/` 下有 **约 150 个审计/量具脚本**，其中**大量已经自带反向探针**（`fa-selftest.py` / `ics-selftest.py` / `ni-selftest.py` / `mig-selftest.py` / `vss-selftest.py` / `rsc-selftest.py` / `wb-selftest.py` / `di-selftest.py` / `cmp-selftest.py` …【实测】文件清单）。

**痛点**：这些脚本全在 `build_qa/` 内 ⇒ **不入库、换机即失、不进 CI**。它们是本仓最强的一批防御，却是**唯一没有版本控制的一批资产**。

---

## 3. 分层优化台账

> 每阶段格式：**开工前提 → 条目表 → 阶段退出判据**。
> 条目表列含义：`来源` = 原台账编号 · `证据锚点` = file:line 或台账章节 · `开工前提` = 动手前必须先满足的条件（`—` 表示无阻塞）· `退出判据` = 算做完的硬标准。

### 执行记录 · 2026-10-04（基准 `ed7fd8c` v4.74.0(481) → 落地 `v4.74.1(482)`）

**OPT-000 ✅ 已落地 v4.74.1(482)** —— §2 基线在当天重测，取证时刻 2026-10-04、HEAD `ed7fd8c`：

| 指标 | §2 基线（`d9c4e61` / v4.68.4） | 本次实测（`ed7fd8c` / v4.74.0） | 差 |
|---|---|---|---|
| versionName / versionCode | 4.68.4 / 474 | **4.74.0 / 481** | +6 minor / +7 code |
| Kotlin 文件 / 总行数 | 420 / 90,682 | **424 / 91,769** | +4 / +1,087 |
| `>600` 行文件 | 30 | **31** | +1 |
| shared 单测 | 26 文件 / 209 例 | **28 文件 / 224 例** | +2 / +15 |
| androidApp 单测 | 8 文件 / 67 例 | 8 文件 / 67 例 | 0 |
| **测试总口径** | 276 | **291** | +15 |
| L3 实跑核对 | 276 / 0 / 0 / 0 | **291 / 0 / 0 / 0（注解数 291 = 执行数 291）** | ✅ 一致 |

- **Top-8 第 8 位发生替换**：`CourseDetailBottomSheet.kt`(1118) 被 `WebViewScreen.kt`(1204) 挤出 —— 后者是 v4.73.0 加三个能力钩子时由本仓自己长上来的（+356 行）。**新 God 文件系本次适配工作引入，记账以免下次误判为历史遗留。**
- 口径陷阱复核：`:shared:testAndroidHostTest` + `--rerun-tasks` 实跑 291 = `@Test` 注解 291，**注解数与执行数本次首次闭合**（`OPT-104` 的四个历史数字 229/265/273/276 已全部过时，当前唯一值 = **291**）。

**OPT-101 ✅ 已落地 v4.74.1(482)** —— 普查结论分两类：

| 模式 | 普查结果 |
|---|---|
| A · 空值使谓词恒真（`startswith(<可能为空的变量>)`） | 4 处候选**全部安全**：`SHARED_LIB_PREFIX="_"`、`COMMENT_PREFIXES=("//","*","/*","*/")`、两处 `.endswith(".kt")` 字面量。**均非空**，与历史事故 `pkg=""` 不同类。 |
| B · 「解析失败即判合规」（空扫仍报绿） | ❌ **命中真缺陷**：`check_a11y.py` 与 `check_font_scale.py` 的仓库根用 **CWD 相对**路径（`os.path.join("shared", ...)`），另有四道用 `__file__`。从非仓库目录运行 ⇒ `os.walk` 走空 ⇒ **扫 0 个文件仍 exit 0**。 |

**OPT-102 ⏳ 部分落地（2/6 道）** —— 按原则 6 逐道做反向验证，本轮完成两道：

| 门禁 | 反向验证方式 | 注入缺陷后的结果 | 结论 |
|---|---|---|---|
| `check_font_scale` | 从非仓库目录运行（CWD 漂移） | 修复前：输出「横向溢出候选：**0 处**」且 exit 0（真值 7 处） | ❌ 假 PASS → 已修 |
| `check_a11y` | 同上 | 修复前：输出「合计：**0** / ✅ 通过」（实际未读任何文件） | ❌ 假 PASS → 已修 |
| 另四道（version_sync / theme_leak / widget_contrast / adapters） | 同上 | 均用 `__file__`，外部 CWD 下行为一致 | ✅ 无此缺陷 |
| 两道修复后的守卫 | 注入不存在的 ROOT | 均 `exit=2` + 「❌ 未扫描到任何 .kt 文件，门禁未生效」 | ✅ 反向验证通过 |

**修法（两道同构，最小 diff）**：① `ROOT` 改 `__file__` 相对；② 新增 **fail-closed 守卫**——`scan()` 一并返回实际扫描文件数，`main()` 见 `scanned == 0` 即 `return 2` 并说明 ROOT；③ 报告里的文件路径由「相对 CWD」改为「相对仓库根」。修复后从任意 CWD 均扫到 **248 个 .kt**，且 `?` 计数(7)与「垂直裁切风险」(0 处)读数与修复前**逐字一致**（只增失败模式，不改正常读数）。

**余下 4 道门禁的反向验证（OPT-102 剩余）与 OPT-103～106 未动**，留待后续轮次。

---

### 阶段 0 · 基线快照（S · 必做前置）

| ID | 事项 | 来源 | 证据锚点 | 开工前提 | 退出判据 |
|---|---|---|---|---|---|
| **OPT-000** | 把 §2 的基线表**在开工当天重测一次**，写进本轮报告并注明取证时刻/HEAD | 本文件 §2 | 附录 A 命令 | — | 表内每个数字都能用附录 A 命令复现；数字有出入时以新测为准并记录差异原因 |

**为什么必须重测**：§2 的数字取自 2026-10-03。本仓的「当前口径」半衰期以小时计（历史上有过一天内三份文档三个测试数的记录）。

---

### 阶段 1 · 量具可信度（P0 · 收益最高）

> **本阶段的目标不是修 bug，是让「修 bug 之后的绿灯」可信。**

| ID | 事项 | 来源 | 证据锚点 | 开工前提 | 退出判据 |
|---|---|---|---|---|---|
| **OPT-101** | 「空值使谓词恒真 / 解析不出即豁免」普查：审 `scripts/check_*.py`、`build_qa/watchdog/run_l1.py` 与各 `audit_*.py`，找出 `startswith(<可能为空的变量>)`、恒真/恒假谓词、以及「解析失败即判合规」的分支 | `E-12` / `P-61` | `round-48.md` §3.7 | — | 每处给出「是否恒真」的**结论 + 反例构造**；确认恒真者改判据，并附一条「还原缺陷 ⇒ 门禁变红」的反向验证记录 |
| **OPT-102** | 六道 L1 门禁**逐道做反向验证并留痕**：临时注入该门禁本应拦住的缺陷，确认它变红 | 原则 6 | `scripts/check_*.py` | — | 6 道各一条记录（注入什么、输出什么、退出码）。**不能变红的写成缺口**，不得默默跳过 |
| **OPT-103** | 把三份「已写好但未立为门禁」的一次性审计脚本收编为 **L1 第 7/8/9 道**，并把 `run_l1.py` 从 `build_qa/` 迁到 `scripts/` | `P-60` / `E-11` | `round-41.md` §3.2、§「建议」（`audit_format_args.py` + `fa-selftest.py`）· `round-48.md` §3.6 与 §「建议」（`audit_intent_contract.py`）· `round-9`（`audit_doc_refs.py`） | ⚠️ **需用户授权修改 `docs/agents/neverstop-watchdog.md` §3**（属该文件 §7.3 硬停机）；脚本必须**连同其 `*-selftest.py` 反向探针一起**迁入 | 三道门禁进 `scripts/`、`run_l1.py` 进 `scripts/`、`neverstop-watchdog.md` §3 同步；每道都有反向验证且在档 |
| **OPT-104** | **测试口径单源化**：现存四个互不一致的数字——**229**（对标清单 §7 XL-032 第 39 轮）/ **265**（`工作日志.md` v4.68.0–4.68.1）/ **273**（本地 `build/test-results/**` 解析，含未跟踪的 `CalendarOwnerMarkTest` 8 例）/ **276**（v4.68.4 终态） | `D-6` / IMA《06》§六.5 | `build_qa/watchdog/count_tests.py`（不入库） | — | 一个**入库**的计数脚本 + 文档只引用该处；四个旧数字各自标注「取自何时、为何是那个值」 |
| **OPT-105** | 把 `PercentFormatStringTest` 由「只测三条 `progress_percent_format`」**泛化为枚举全部带占位符的 format 资源**，逐条**按 Compose 的真实插值规则渲染**（正则 `%(\d+)\$[ds]`，**不是 `String.format`**，见 2026-10-07 订正） | 原则 2 · `v4.68.4` | `shared/src/androidHostTest/kotlin/PercentFormatStringTest.kt`【实测存在】 | — | 新增 `@Test` 覆盖全量 format 资源；**把任一 `%1$d%` 改回 `%1$d%%` 时必须渲染出两个 `%`**（反向自证） |
| **OPT-106** | （可选）新增 `scripts/check_god_files.py` 行数棘轮：单文件行数**不得高于基线**，与 `theme_leak`/`a11y` 同构 | 原则 6 | §2.2 Top-8 | — | 带 `--update-baseline`（仅净下降时允许）+ 反向验证；进 L1 后与阶段 4 联动 |

**阶段退出判据**：六道（或九道）门禁**每一道都有在档的反向验证记录**，且 `OPT-104` 的口径单源已落地。

---

### 阶段 2 · 正确性与数据安全残留（P0/P1）

| ID | 事项 | 来源 | 证据锚点 | 开工前提 | 退出判据 |
|---|---|---|---|---|---|
| **OPT-201** | **周课表链错误态**：数据链异常时用户看到的是持续 `AppLoading` 或空态，**没有重试入口** | `R1-011` 剩余 · `U-1` · `U-3` · `P-55` | `WeeklyScheduleViewModel.kt`【实测】含 **7 处 `combine`**（`:281/:317/:346/:403/:456/:469/:473`）；同级 `Today`/`Agenda`/启动门控已在 `v4.64.28` 修完 | **只加 `.catch` + `UiState.Error` 分支，禁止同期拆文件**（1262 行、并行会话冲突热点）；先补特征化测试 | 注入列流异常 ⇒ 页面显示可重试错误态（非永久加载）；`Weekly` 用例与基线逐例一致 |
| **OPT-202** | **写操作失败的错误态**（保存课表身等）：写失败仍走 toast 路径，未纳入 `v4.64.28` 批次 | `U-2` | `工作日志.md:2691` | — | 每处写失败都有可见且可重试的反馈；不吞异常 |
| **OPT-203** | `WebViewRequestInterceptor.kt:147-149` **POST >1M 静默丢**：超限直接丢弃、无任何提示 | `R1-016` · `P-57` | `REVIEW.md:54` / `:148` | ⚠️ **需用户定文案**（回落时 `AppLog.e` + 导入页提示），或批准提高限额 | 超限有可见提示且留日志；有可复现路径 |
| **OPT-204** | `BackupRepository.kt:497` **样式版本迁移 TODO**（全仓唯一「真」待办标记）：旧样式包无版本钳制 | `R1-003` · `E-4` · `P-56` | 【实测】`shared/.../data/repository/BackupRepository.kt:497` | ⚠️ **需先定设计**：补版本号 + 迁移 or 拒绝 | 老样式包有显式处置路径（迁移成功或明确拒绝），不静默按原始字节写入 |
| **OPT-205** | **备份不含笔记图片字节**，恢复却把图片路径原样带回 ⇒ 恢复后图片全断 | `DA-1` | IMA《06》§4.5；`pending.md` 轮 46 | ⚠️ **三选一**：(a) 图片进备份包（体积/耗时↑）(b) 恢复时清空图片引用并提示（**建议**）(c) 维持现状 | 选定方案后：恢复路径行为与选择一致，并有 hostTest 守住 |
| **OPT-206** | **WebView 登录态 `app_webview/` 落在自动备份 root 域内** | `DA-2` | `工作日志.md:489-491`；`pending.md` 轮 43 | ⚠️ **三选一**：(a) 排除（换机需重登）(b) 维持 + 隐私政策说明（**建议**）(c) 运行时清理 | 备份域实测结果与选择一致（`bmgr` 或规则文件级取证） |

**阶段退出判据**：每一项都有「失败可见 / 可重试 / 显式拒绝」的**可复现路径**（hostTest 或真机步骤），**不允许只改注释就算完**。

---

### 阶段 3 · 分层与耦合（P1）

| ID | 事项 | 来源 | 证据锚点 | 开工前提 | 退出判据 |
|---|---|---|---|---|---|
| **OPT-301** | 两个 ViewModel **直接写文件**（头像 / 壁纸）⇒ 下沉 Storage 网关 | `R1-022` · `E-5` | `REVIEW.md:151` | 先补特征化测试（写路径/失败路径） | VM 内零 `fileSystem` 直写；网关单测覆盖写失败 |
| **OPT-302** | `ResourceInitializerManager` 的**类内私有 scope** 改为注入应用级 scope | `R1-030`（部分完成）· `E-2` · `P-58` | 【实测】`shared/.../tool/ResourceInitializerManager.kt:46` `private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`（`v4.64.22` 已补 `SupervisorJob`，KDoc `:39-44` 已说明「就地修而不引入新的 Koin 提供者」） | ⚠️ **DI 改动**：先确认不引入新的生命周期问题；保留 SupervisorJob 语义 | scope 由容器注入；任一子步骤抛出**不再**影响其他步骤（有测试） |
| **OPT-303** | 两处平台侧兜底：① `ShareManager.android.kt:34` `FileProvider.getUriForFile(...)` **无异常兜底**（路径未在 `file_paths` 声明即抛 `IllegalArgumentException` —— `v4.67.15` 已踩过一次同类）；② 冻结过渡实现 `CourseAlarmReceiver` 的通知在默认模式下「**不可划掉且不会自动失效**」 | `E-16` · `E-18` | 【实测】`shared/src/androidMain/.../ui/components/ShareManager.android.kt:34`；`pending.md` 轮 35；`工作日志.md:676`、`:840` | — | 分享失败有明确反馈而非崩溃；冻结通知有自动失效路径 |
| **OPT-304** | `website/version.json` 纳入 `check_version_sync.py`（补丁已生成**但未应用**） | `E-21` | 【实测】`build_qa/patches/version-sync-gate-and-changelog.patch`（13,730 B） | ⚠️ **先定 `version.json` 的正确取值**：本地是 `420 / 4.66.0`，而 `v4.66.0` **从未发布**；线上该文件曾 404。规则是「必须指向**已真实发布**的包」 | 门禁覆盖 4 处；`version.json` 取值有明文依据（指向最近已发布版本） |

**阶段退出判据**：分层项**不得改变对外行为** —— 全部先补特征化测试再改，改完逐例与基线一致。

---

### 阶段 4 · 可读性与结构（P1/P2 · **风险最高，每批单文件**）

> 本阶段触碰的是并行会话的**共享冲突热点**（`WeeklyScheduleScreen.kt` / `TodayScheduleScreen.kt` / `AgendaScreen.kt` / `AppStyle.kt` 被平板适配、玻璃、主题等多条线共同修改过）。
> **铁律**：每批**只动一个文件**、**只做一类改法**、**独立提交**、改动前后各取一次对照证据（截图 / 探针 / 逐例测试）。

| ID | 事项 | 来源 | 证据锚点 | 开工前提 | 退出判据 |
|---|---|---|---|---|---|
| **OPT-401** | **名实不符 8 项重命名**（`Destination.Schedule` 实挂 Agenda、`thisMonday` 实为可配首日、`isCrush` 迁移遗留恒 false、`floatingCourse` 实为调课 staging、`todayXPalette(peach)` 实为奇偶条纹、`isGridHolding` 实为拖拽中、`pageGlassBackdrop=null` 实为诊断开关…） | `R6-READ` · `E-7` | `REVIEW.md:167` | 先补断言再改名（重命名会连带测试与调用点） | 每项一处改名 + 调用点同步；L3 逐例与基线一致 |
| **OPT-402** | **God 文件分批拆分**（§2.2 的 30 个 `>600` 行文件，优先 Top-8） | `R6-READ` · `E-7` · `P-55` 关联 | `REVIEW.md:170`；本文件 §2.2 | **每批单文件**；与 `OPT-201` **不得同期做同一个文件**；先截图/探针留底 | 拆分后文件行数下降且**渲染与交互逐项对照一致**；无行为变化证据在档 |
| **OPT-403** | **超长函数拆分**（Weekly 200 行+ / AgendaCreateSheet 288 行等 5 处） | `R1-023` · `E-6` | `REVIEW.md:152` | 按段拆小 Composable；同类可合并批次但**仍单文件** | 每个被拆函数 ≤ 约定行数；行为不变可证 |
| **OPT-404** | **魔法数字命名**（`delay(520)` / `30_000L` / `44.dp` / `20.sp` 等 10 条；命名正例 `TIME_COLUMN_WIDTH`） | `R1-028` · `E-8` | `REVIEW.md:156` | 可分批；值不得改变 | 每条有具名常量且值逐字不变 |
| **OPT-405** | **魔法语义 3 项**（Agenda `GROUP`/`STATUS` 裸 `Int`、NextCard 今日明日复用、`firstDayOfWeek` Int 无约束 + GRID 预留） | `R6-READ` | `REVIEW.md:171` | — | 裸 `Int` → 受约束类型/枚举；序列化兼容性验证在档 |

**阶段退出判据**：**行为不变可证** —— L3 逐例与基线一致（`276 例 / 0 失败`）+ 每批有对照证据；且每批独立提交，便于单独回退。

---

### 阶段 5 · 性能与资源（P2 · 需真机）

| ID | 事项 | 来源 | 证据锚点 | 开工前提 | 退出判据 |
|---|---|---|---|---|---|
| **OPT-501** | **补齐 PF6 性能基线**：6 场景（柔绘/书卷/通透 × 课表/今日），负载为上滑/下滑各 8 次 | `docs/agents/perf-baseline.md` | `perf-baseline.md` §2/§3/§139-149（`baseline.json` **仍空缺**） | ⚠️ **需真机 + 必须开启「USB 调试（安全设置）」**（`INJECT_EVENTS`）；**探测权限只能用 `input swipe`，不能用 `input tap`**（踩过：tap 静默通过、swipe 全报 `SecurityException`，采到过一份「像结果的假数据」） | 6 场景落盘 + `--save-baseline`；两轮采样偏差可接受 |
| **OPT-502** | **探针先行**：启动期单次 `runBlocking`（WebView 拦截，1 处必需 + 2 处小阻塞）与图片钳制 | `REVIEW.md:39`（J 维度：无证据不改） | `REVIEW.md:39` | 先做探针取证据，**有证据才改** | 有量化结论：改 or 不改，二者都留证据 |
| **OPT-503** | 备份体积与文件生命周期复核（沿用 `v4.67.35` 的 `bmgr` 判据与 `v4.67.34` 的生命周期判据） | `工作日志.md:377-445` | `工作日志.md:377`（3,940,864 B → 468,480 B，−88.1%） | 真机 | 复核结论 + 无回归 |
| **OPT-504** | **审计脚本从 `tools/` 迁到 `scripts/`**（死代码 / 零引用资源 / i18n 键三类）：`tools/` 被 `.gitignore` 忽略 ⇒ 换机即失、换 worktree 即不存在（官网同步脚本 `update_website.py` 就因此在新 worktree 里根本不存在，版本漂移可完全静默发生） | `REVIEW.md:107` · `E-15` | `.gitignore`；`工作日志.md:2905` | 与 `OPT-103` 合并规划（同一件事的两个侧翼） | 三类脚本在 `scripts/` 内且可独立运行 |

**本阶段明确暂缓**：**PF1「削减玻璃层数」** —— 量化实验证明「背板加脏标记」**零收益**（2.27% vs 2.28%，课表页反而更差属噪声）；「静止态层数 3→2」与玻璃引擎铁律冲突；「降低模糊分辨率」属**观感变化需用户授权**。⇒ 在「观感必须不变」的硬约束下无有效手段。

---

### 阶段 6 · 文档与台账（P2/P3）

| ID | 事项 | 来源 | 证据锚点 | 开工前提 | 退出判据 |
|---|---|---|---|---|---|
| **OPT-601** | `CHANGELOG.md` 补 **v4.66.1–v4.68.x** 条目（顶部仍停在 `v4.66.0`，与 App 实际差多个 minor） | `D-1` · `P-63` | IMA《06》§4.4；实测 `CHANGELOG.md` | ⚠️ **属发版动作**：`AGENTS.md` 原文是「发布新版本（含正式版构建 / Release）后」必须追加；未发版期间不追加**不违规**。故本项**只在下次发版时**做，或由用户明确要求补历史 | 条目按「功能/修复/外观/构建」分类，含 versionCode 与支持 ABI |
| **OPT-602** | `docs/ux-interaction-spec.md`（1812 行）**与代码对账 + 纳入 git**：已确认 ≥3 条假结论（保存校验时间合法性 / 覆盖二次确认 / 0 门课提示成功），且该文**未跟踪** | `D-2` · `D-3` · `P-64` · `P-66` | `工作日志.md:2752-2762`、`:2782`；`git status` 显示 `??` | 对账需要逐条回代码（40+ 条「⚠️ 待确认」） | 该文入库；假结论就地更正并留痕（不静默覆盖） |
| **OPT-603** | `R1-013` 文案 10 条（省略号 / 感叹号 / 量词 / 术语 / 冒号）+ `R1-015` 组件一致性（卡片圆角 / 按钮高 / 胶囊边距三套口径） | `R1-013` · `R1-015` · `U-4` · `U-5` | `REVIEW.md:53` / `:147` | ⚠️ **用户可见措辞与视觉基线，必须用户定方向**（`R1-015` 须先定 token） | 定方向后一次性改到位；三语齐备 |
| **OPT-604** | 把附录 B 的**已消除条目回写 IMA**（否则下一轮会重新评估已修完的事） —— ✅ **已于 2026-10-03 执行完毕** | `AGENTS.md` 回写纪律 | 本文件附录 B | — | **已落地**：`append_doc` 追加三篇并**回读校验**通过 —— 《06-进度与待办 / 当前进度与待办清单》`+2519` 字符（含附录 B 三条订正 + 口径更新 + 新增待办）·《08-资料归档 / 现有文档与资料索引》`+1286` 字符（新增文档登记 + 规模数字）·《07-问题复盘 / 已知问题与踩坑点》`+1660` 字符（新增坑 32：`media_id` ≠ `note_id`）。**均为追加，未覆盖任何既有结论** |

---

## 4. 用户决策队列（阻塞在「用户拍板」，不拍板就不开工）

> 这些**不是**技术问题，是产品/流程/授权问题。执行者**不得自行替用户决定**。

| # | 决策 | 备选 | 若不决策的影响 | 关联 |
|---|---|---|---|---|
| **D1** | **先做哪个**：8 个 RemoteViews 迁移 Glance（`XL-005`）**还是** widget token 12×6 → 12×48（`XL-011`） | 二者是**同一个排期决策点**（`widget-display-optimization.md` §4.4），必须先定一个再回来一次性改文档到位，否则改两遍 | `docs/widget-display-optimization.md` 仍按「4 个规格」撰写（实际 8 个，7 处），**改了数字不补 token 表会更自相矛盾** | `F-1`/`F-2`/`D-5`/`P-65` |
| **D2** | vivo 原子通知（`XL-012` 剩余） | (a) **确认不做**，渠道留作降级占位（建议）(b) 逆向私有协议（**仓库明文「不伪造厂商键值」**） | `VIVO_ATOMIC` 渠道已建但**无投递点** | `F-3` |
| **D3** | 是否授权修改 `docs/agents/neverstop-watchdog.md` §3 | 授权 ⇒ 可落地 `OPT-103`（三道门禁 + `run_l1.py` 入库） | 三道已成型的门禁继续躺在 `build_qa/` 里，**换机即失、不进 CI** | `OPT-103`/`P-60`/`E-11` |
| **D4** | `DA-1` 备份笔记图片 | (a) 进备份包 (b) 恢复时清空引用 + 提示（建议）(c) 维持 | 恢复后图片全断 | `OPT-205` |
| **D5** | `DA-2` WebView 登录态进备份 | (a) 排除 (b) 维持 + 隐私政策说明（建议）(c) 运行时清理 | 换机恢复可能带出登录态 | `OPT-206` |
| **D6** | `R1-013` 文案措辞 + `R1-015` token | 已给候选方向（`教师：`/`老师：` 二选一；半角→全角仅简中） | 用户可见措辞不统一 | `OPT-603` |
| **D7** | `R1-016` 超限提示文案 | 回落打日志 + 导入页提示 / 提高限额 | >1M 帖子静默丢 | `OPT-203` |
| **D8** | CI `check-pr-source.yml` 要求 PR 来源分支为 `dev`，但**远端无 `dev` 分支** ⇒ 该 CI 恒失败 | 建 `dev` 分支 / 改规则 / 弃用该 CI | CI 长期恒红，噪声掩盖真信号 | `E-13` | 【**已解决 2026-10-06**】分支名限制此前已移除；该文件本轮**合并进 `pr-guard.yml`**（与 `dco.yml` 合并，顺带修掉 DCO 的「空范围恒 PASS」缺陷），旧文件已删除。不建 `dev` 分支——与 AGENTS.md「main 常驻 + 短生命周期 feat/fix 分支」策略一致 |
| **D9** | iOS target（31 错 / 14 独立根因）是否在交付范围 | 不在 ⇒ 明确写入文档，不再重复评估 | 每次巡检都会重新发现它 | `E-14`/`P-59` |
| **D10** | 仓库根 `tmp_*.db`（真机库副本、含用户真实课程数据） | **已实测不存在**（附录 B），本项仅保留为「若再出现则清理」的规则 | — | `E-19`【已消除】 |

---

## 5. 不做什么（已定案表 —— 防止重复评估）

> 这些条目**已经有过结论**，没有新证据不得重启。逐条来源可查，新增定案时追加到本表。

| 项 | 定案 | 来源 |
|---|---|---|
| 小组件刷新加时间窗限流（对齐星链） | **不加**。本仓 `Mutex` + `requiredRefreshPending` 是「合并刷新（不丢）」，时间窗是「跳过刷新（可能丢）」——后者是**倒退** | `工作日志.md:59-64` |
| Today toggle 双击防重 | 不修（`setDone` 绝对值写入，天然幂等） | `REVIEW.md:38` |
| Weekly 手势防重 | 不修（`onDragEnd` 单发 + `isNoPositionChange` 守卫 ⇒ 误报） | `REVIEW.md:161` |
| `APPWIDGET_VISIBLE/HIDDEN` 监听 | **不跟**。用户已裁定「隐藏时不刷新，省电」；且已实证这两个 action **系统本身不发**（SDK 源码 + 真机 framework 双向核实） | `pending.md` 轮 3；`工作日志.md:22-42` |
| `WAKE_LOCK` 补进隐私页权限表 | 不做。它是 `androidx.work` **合并引入**、App 自身未声明，补进去属**失真**（应表述为「依赖库引入」） | `REVIEW.md:102` |
| `teacher` 字段从 diff 排除 | 不排除。它被课前提醒通知消费（`CourseReminderNotifier.kt:83-121`），排除会让「只改教师名」不再落库 ⇒ 通知长期显示旧教师名 | `工作日志.md:2899` |
| `CourseDao` check-then-write 改 `IGNORE`/`REPLACE` | 不改。已在同一 `withWriteTransaction` 内，**无 TOCTOU** | `工作日志.md:2899`；`E-3` |
| `WidgetRepository.replaceSnapshotIfChanged` 读在事务外 | 仅补注释。全仓唯一写者 + `syncMutex` 串行 ⇒ 无并发缺口；且 Room 3.0.1 下把 `Flow.first()` 塞进事务属**未验证区域**，收益为零、风险不为零 | `REVIEW.md:101` |
| PF1「削减玻璃层数」 | **暂缓**。脏标记实测零收益；层数 3→2 与玻璃引擎铁律冲突；降模糊分辨率属观感变化需授权 | `perf-baseline.md:69-96` |
| `MIGRATION_2_3` 的 `DROP TABLE` 风险 | 记录不改。只影响**未发布**的 schema 2/3 设备，且 `PRAGMA foreign_keys = ON` 在 `onOpen` **之后**执行；**改动无法验证** | `工作日志.md:837`；`E-24` |
| 导出 schema 缺 `1.json`/`2.json` | 记录不改（`MIGRATION_1_2` 无法逐步回放），已由审计规则 C 兜住 | `工作日志.md:838`；`E-25` |
| 视觉一致性（圆角/按钮高/胶囊边距） | **禁止顺手改**。须先定 token，属用户决策（D6） | `REVIEW.md:147` |

---

## 6. 执行协议（复用既有机制，**不新造流程**）

### 6.1 开工对表（每轮）

```powershell
cd "D:\01课程表\shangkeschedule"
git fetch origin
git merge --ff-only origin/main
git branch --show-current      # 必须输出 main
git status --short
```

- 主工作区**常驻 `main`**；`merge --ff-only` 失败（分叉）⇒ **挂起等人**，不 rebase、不 reset。
- `git status` 出现**不属于自己**的改动 ⇒ 判定为并行会话在途，**本轮只报告、不动手**。
- **并行会话严禁占用主工作区切分支**，一律 `git worktree add`。新 worktree 首次构建前必须复制三样：`androidApp/keystore.properties`、`androidApp/shangkeschedule-release.jks`、`local.properties`（否则 `validateSigningRelease` 必失败）。

### 6.2 分层验证（由浅到深，逐层向下）

| 层 | 命令 | 判读 |
|---|---|---|
| L1 | `python scripts/check_version_sync.py` / `check_theme_leak.py --baseline scripts/theme-leak-baseline.json` / `check_a11y.py --baseline scripts/a11y-baseline.json` / `check_widget_contrast.py` / `check_adapters.py` / `check_font_scale.py` | **六道全部跑完再汇总**，不得首条失败即停；`check_font_scale.py` 记录 `?`（可能裁切）**数量，较上轮增长即挂起**，不自行改布局 |
| L2 | `.\gradlew.bat :shared:compileKotlinJvm :desktopApp:compileKotlin :androidApp:assembleDebug --console=plain` | 三关全绿；任一失败 ⇒ L3 不跑并注明 |
| L3 | `.\gradlew.bat :shared:testAndroidHostTest :androidApp:testDebugUnitTest --rerun-tasks --console=plain` | 必须 `--rerun-tasks`；**必须核对执行数 = `@Test` 注解数**（本仓基线 **276**）；`NO-SOURCE` **一律不算通过** |
| L4 | 每 4 小时：提交↔工作日志对账、tag↔CHANGELOG、文档引用抽查、worktree/分支残留、磁盘与缓存、真机盲区清单复核 | 只报告不擅自处理他人分支；磁盘低于 5 GB 只报告 |
| PF6 | `python scripts/perf_jank.py` / `--save-baseline` / `--check-baseline` | 涉及 `ui/glass/**`、列表/网格测量逻辑、发版前**必跑**；玻璃路径另需离屏探针 `[control]` = 0.000 |

> **`JAVA_HOME` 本机是坏的**：每条 Gradle 命令前显式设 `C:\Program Files\Android\Android Studio\jbr`。

### 6.3 修复纪律

```
失败项
 ├─ 1. 读报错原文与门禁输出（必要时重跑取完整日志），不凭印象
 ├─ 2. 用证据定位根因（复现 / 日志 / 源码对照）；找不到根因不许改代码
 ├─ 3. 一次只改一个根因，最小 diff，不夹带
 ├─ 4. 验证：a. 重跑失败的那条门禁/编译/测试
 │         b. 改过门禁逻辑 ⇒ 强制反向验证（临时还原缺陷确认变红，再恢复）
 │         c. 从受影响层级向下全量重跑
 ├─ 5. 同一失败项连续 2 轮未通过 ⇒ 停止重试，写入挂起清单，跳到下一项
 └─ 6. 命中「硬停机条件」⇒ 立即挂起等人
```

**修复手段优先级**：改代码/资源消除缺陷 **>** 补门禁防复发 **>** 证明误判后调整规则。调整任何检查规则**必须给出误判证据**（文件、行号、判定值）。

**棘轮纪律**：`--update-baseline` 仅在违规数**净下降**时允许，且必须在工作日志写明。**严禁为让门禁变绿而放宽基线。**

### 6.4 收尾（一条改完就走完）

1. **判类型**：`FIX` / `FEAT` / `REFACTOR` / `DOCS` / `DATA` / `BUILD`。
2. **版本迭代**：`FIX`→`python tools/publish_new_version.py --bump patch --bump-only`；`FEAT`→`minor`；破坏性→`major`；`versionCode` 每次无条件 +1。**纯文档变更不 bump**（本文件自身属此类）。
3. **手动改 README 徽章**（无脚本同步；历史教训：徽章曾落后约 100 个版本）。
4. **重跑版本门禁** `python scripts/check_version_sync.py` ⇒ 三条 `[OK]`。
5. **追加工作日志**：`python tools/worklog/worklog.py append --date <YYYY-MM-DD> --version <新版本> --code <新code> --type <类型> --summary "<一句话>" --body-file <正文文件>`。正文写：失败项与报错、根因、关键文件、验证方式与结果（含反向验证）、**验证状态（编译 / 未装机）**。
6. **显式 add**：`git add -- <本次文件…>`（**严禁** `git add .` / `-A`）；add 后 `git status --short` 核对，发现多余文件立即 `git reset HEAD <文件>` 撤出。
7. **提交**：`git commit -m "<type>(<范围>): <中文摘要> vX.Y.Z"`。
8. **推送**：`git push origin main`，**gitee 镜像按同法同步**。

**红线（用户确认了也不得入库）**：`*.jks` / `keystore.properties` / `local.properties` / `google-services.json` / `adapter_secrets.properties`；AI 会话产物（`.trae/`、`.zcode/`、`build_qa/`、`工作日志.md`、`tools/`）。

### 6.5 已知故障处置（不要自创解法）

| 症状 | 正确处置 | **严禁** |
|---|---|---|
| `schannel: failed to receive handshake` | 等约 20 秒 ⇒ `git ls-remote --heads origin main` 探活 ⇒ 恢复后原样重推 | ❌ 清空代理直连（报 `Connection was reset`）❌ 切 `http.sslBackend=openssl`（报 `SSL_ERROR_SYSCALL`） |
| `could not read Password ... terminal prompts disabled` | `python scripts/push_via_wincred.py`（代理故障加 `--direct`） | ❌ 依赖 `git credential fill`（本机凭据链不可靠） |
| 推送连续失败 3 次 | **挂起**；本地提交**保留不回退** | ❌ force push |
| 依赖下载/镜像/网络错误 | 等 20 秒重试，最多 3 次，仍失败则该项挂起 | ❌ 无限重试 |

### 6.6 硬停机条件（立即停止自动修复，保留现场等人）

1. 需要改签名、密钥、凭据、`adapter_secrets.properties` 或私有适配仓库写入；
2. **需要真机验证**：日历写回、响铃/勿扰切换、通知投递、桌面组件放置、权限变更广播等 JVM 测不到的链路；
3. 需要修改 `AGENTS.md` 规则本身、`docs/agents/neverstop-watchdog.md` 或发版流程；
4. 需要删除/移动用户文件、删除分支、force push、撤销提交；
5. 同一失败项连续 2 轮修复失败；
6. 依赖下载/镜像/网络类错误重试 3 次仍失败；
7. Gradle OOM、机器资源不足等环境问题（冷却后仍复现）；
8. 修复需要**新功能、新文案、设计或产品决策**（⇒ 转入 §4 决策队列）；
9. 发现红线文件被跟踪、或工作区存在来源不明的改动（并行会话事故苗头）；
10. 用户直接下达新指令 —— 立即让位。

---

## 7. 验收标准

### 7.1 对「一条 OPT 项做完」的验收

同时满足才算完成：

1. **代码/资源改动**已落地，最小 diff，无夹带；
2. **L1 六道全绿**（改动后全量重跑，不只看受影响的那条）；
3. **L2 三关 + L3 逐例**通过，**执行数 ≥ 276**（只应因新增测试而增加；无理由减少即挂起）；
4. 若改了门禁或新增门禁：**反向验证在档**（还原缺陷 ⇒ 变红）；
5. **验证状态如实标注**（编译 / 未装机）；测不了的链路不得翻转为「已验证」；
6. 工作日志已追加、版本已迭代（纯文档变更除外）、README 徽章与 `check_version_sync.py` 同步；
7. 本文件对应条目状态已更新为 `✅ 已落地 vX.Y.Z(code)` + 一行证据。

### 7.2 对「整体优化目标」的验收（阶段级）

| 阶段 | 完成判据 |
|---|---|
| 阶段 1 | 每道 L1 门禁都有在档反向验证；测试口径单源；恒真谓词清零（或逐处给出「为何不恒真」的证明） |
| 阶段 2 | 三条主链（周课表 / 今日 / 日程）与写操作路径，异常时**均可重试**；数据策略三项有明文结论 |
| 阶段 3 | VM 层零文件直写；scope 由容器注入；平台侧 API 调用有兜底 |
| 阶段 4 | `>600` 行文件数**净下降**（棘轮只许收紧）；每条改动有行为不变证据 |
| 阶段 5 | PF6 六场景基线落盘且 `--check-baseline` 可跑；探针结论在档 |
| 阶段 6 | 文档与代码不再互相矛盾；未入库的正式文档已入库 |

---

## 8. 风险与失败模式

| 风险 | 说明 | 对策 |
|---|---|---|
| **文档漂移**（本仓已发生多次） | 文档里的可计数数字会随代码漂移；**已修内容可能被记成待办**（历史实证：AOSP 实况通知「早已落地」却被记成未做） | 数字一律带取证时刻；「当前口径」与「历史记录」分开写；每轮开工先跑 `OPT-000` |
| **God 文件改动引入回归** | `WeeklyScheduleViewModel` / `TodayScheduleScreen` / `AgendaScreen` 是并行会话共享冲突热点 | 阶段 2 与阶段 4 **不同期做同一个文件**；每批单文件、独立提交、留对照证据 |
| **新增门禁又变成假门禁** | 门禁自己也会有 `pkg=""` 这类缺陷 | 原则 6：新门禁必须自带反向验证；无反向验证不得进 L1 |
| **真机依赖被绕过** | 日历写回 / 响铃勿扰 / 通知投递 / 桌面放置只能真机验 | 测不了就**如实标「未装机」并挂起**，不翻转结论 |
| **并行会话覆盖** | 历史多次互相覆盖；`stash` 可能被其他会话 pop/clear | 提交要快；重要在途改动写补丁到 `build_qa/`，**不依赖 stash**；`git add` 显式列文件 |
| **schema 版本是跨会话共享的物理事实** | 曾出现 main 是 v13、并行 worktree 是 v15，设备 DB 在 v15 ⇒ Room 无降级路径 ⇒ 真机闪退 | 动 `MainAppDatabase.kt` / `DatabaseMigrations.kt` 前先与所有会话对表；提版本必须补迁移（已由 `DatabaseMigrationChainTest` 守住） |
| **构建链 CVE 误读为安全事件** | 52 个 Dependabot 告警全在**构建期类路径**；`releaseRuntimeClasspath` 中 `netty|bouncycastle|jose4j|jdom|commons-lang3|httpclient|opentelemetry` **零命中** | 属构建链版本陈旧信号，勿当安全事件处理，也勿因此改依赖 |
| **密钥只有一份同盘备份** | `shangkeschedule-release.jks` 全盘扫描确认此前只存在于仓库与 worktree 内 | 已有同盘备份 `D:\01课程表\密钥备份\`，但**同盘备份不防整盘损坏** ⇒ 值得用户补一份异盘/离线备份 |

---

## 9. 与既有文档的边界（不要重做）

| 已被覆盖 | 位置 | 本文件的态度 |
|---|---|---|
| 全自动审查 14 轮 + 批 1–18 修复（`R1-001` 去 `!!`、`R1-006` `println`→`AppLog`、`R1-010` `as?`、`R1-012` 防重、`R1-017` 日志隐私、`R1-026` 裸中文、`R1-027` 重复字面量、`R4-001/002` 零时长与 ICS 周下界） | `REVIEW.md` / `FIXLOG.md` | **不要重做** |
| 星链对标 P0/P1 落地（`XL-001`–`XL-015`）与红线（`XL-040`–`XL-044`） | `docs/optimization-checklist-xinglian-benchmark.md` §0.5 / §7 / §8 | **不要重做**；`XL-005`/`XL-011`/`XL-012` 剩余项见 §4 决策队列 |
| 小组件显示优化（取色兜底、行高、`minResizeHeight`、对比度门禁、12 token × 6 取值设计、13 条 RemoteViews 不可行项） | `docs/widget-display-optimization.md` §1–§9 | **不要重做**；其「4 个规格」滞后项与 `XL-011` 是同一决策点 |
| `v4.66.0` 功能线（成绩/学分、四类考证查分、课堂笔记、共同空闲、课表与主题分享、空教室、8 类小组件、下一节课常驻通知 + 考试倒计时、灵动岛、教务适配状态页、意见反馈 + 隐私政策、AI 识别导入） | `CHANGELOG.md` / commit `7077564` | **不要重做** |
| 巡检协议、修复协议、收尾流程、硬停机条件、节奏 | `docs/agents/neverstop-watchdog.md` | 本文件 §6 是**摘要**，冲突时**以该文件为准** |
| 性能基线的测量场景、负载参数、回归判据、已知限制 | `docs/agents/perf-baseline.md` | 本文件只引用，不重写 |

---

## 附录 A · 复现命令（对应 §2 全部数字）

```powershell
$env:PYTHONUTF8=1
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
$env:Path="$env:JAVA_HOME\bin;$env:Path"

# 版本与 HEAD
Select-String -Path androidApp\build.gradle.kts -Pattern 'versionName|versionCode'
git log --oneline -1

# 测试口径（与 @Test 注解数必须一致）
$sh = Get-ChildItem -Recurse -File -Path shared\src\androidHostTest -Filter *.kt
$an = Get-ChildItem -Recurse -File -Path androidApp\src\test -Filter *.kt
"shared: $($sh.Count) 文件 / " + ($sh | Select-String -Pattern '^\s*@Test' | Measure-Object).Count + " 例"
"androidApp: $($an.Count) 文件 / " + ($an | Select-String -Pattern '^\s*@Test' | Measure-Object).Count + " 例"

# 代码规模与 God 文件
$kt = Get-ChildItem -Recurse -File -Include *.kt -Path shared,androidApp,desktopApp |
      Where-Object { $_.FullName -notmatch '\\build\\' }
$tot = 0; foreach ($f in $kt) { $tot += (Get-Content $f.FullName).Count }
"kt 文件 $($kt.Count) / 总行数 $tot"
$kt | ForEach-Object { [pscustomobject]@{ n=(Get-Content $_.FullName).Count; p=$_.FullName } } |
      Where-Object { $_.n -gt 600 } | Sort-Object n -Descending | Select-Object -First 30

# L1 六道
python scripts/check_version_sync.py
python scripts/check_theme_leak.py --baseline scripts/theme-leak-baseline.json
python scripts/check_a11y.py --baseline scripts/a11y-baseline.json
python scripts/check_widget_contrast.py
python scripts/check_adapters.py
python scripts/check_font_scale.py

# L2 / L3
.\gradlew.bat :shared:compileKotlinJvm :desktopApp:compileKotlin :androidApp:assembleDebug --console=plain
.\gradlew.bat :shared:testAndroidHostTest :androidApp:testDebugUnitTest --rerun-tasks --console=plain
```

> **PowerShell 读中文文件必须带 `-Encoding utf8`**，否则 GBK 误读产生 mojibake（本仓多次踩到，且 mojibake 会被当成「文件内容就是这样」）。

---

## 附录 B · 台账订正（【实测】确认**已不成立**的待办）

> 这些条目在 `REVIEW.md` / IMA《06》《07》/ `pending.md` 里仍记为待办，但**本轮实测已消除**。
> 记录在此，防止下一轮重新评估；同时按 `AGENTS.md` 回写纪律同步 IMA —— **已于 2026-10-03 执行完毕**（详见 §3 阶段 6 `OPT-604`：三篇笔记均以「追加 + 回读校验」方式写入，未覆盖任何既有结论）。

| 台账原记录 | 原状态 | 本轮实测 | 处置 |
|---|---|---|---|
| `E-23` / `P-54`：`docs/agents/neverstop-watchdog.md` §3 的 L3 写成 `:shared:jvmTest`（`NO-SOURCE` ⇒ shared 层 209 例静默漏跑，却报 `BUILD SUCCESSFUL`） | 待处理（§7 硬停） | **已修**：`d9c4e61 docs(agents): 订正 watchdog L3 命令——:shared:jvmTest 实为 NO-SOURCE…改用 testAndroidHostTest`；`neverstop-watchdog.md` §3 现为 `:shared:testAndroidHostTest`，且已写明「必须核对执行数」 | 关闭 |
| `D-4` / `P-66`：`docs/agents/neverstop-watchdog.md` **未纳入 git** | 未修 | **已入库**：`git ls-files docs/agents/` 现含该文件（同批 `d9c4e61`） | 关闭（`P-66` 另一半 `docs/ux-interaction-spec.md` 仍未跟踪 ⇒ 见 `OPT-602`） |
| `E-19`：仓库根 4 个 `tmp_*.db`（`adb pull` 的真机库副本，含用户真实课程数据 15 行） | 待用户确认删除 | **已不存在**：全仓 `tmp_*.db` 递归检索 **0 命中**（`build_qa/` 内也无） | 关闭；仅保留「若再现则清理」规则（D10） |
| `E-3` / `R1-033`：`CourseDao.kt:68` ABORT + 先查后写 TOCTOU | 待批 | 已定案**无缺口**（同一 `withWriteTransaction` 内） | 移入 §5 不做什么表 |
| `R1-006`：6 处 `println` 残留 | 待批 | 已修（`v4.64.10`，收归 `AppLog.w`）；`REVIEW.md` 第 7 轮复扫确认 commonMain `println` = 0 | 关闭 |
| `R1-026` / `R1-027` / `R1-010` / `R1-017` / `R4-001` / `R4-002` | 待批 | 均已修并验证（见 `FIXLOG.md` 与 `REVIEW.md` 问题总账状态列） | 关闭 |
| 测试口径 `229`（对标清单 §7 第 39 轮）/ `265`（`工作日志.md` v4.68.0–4.68.1）/ `273`（本地 `build/test-results`） | 三口径并存 | **当前实测 276**（shared 26 文件/209 例 + androidApp 8 文件/67 例）；三个旧数字是各自时点的真实值 | 见 `OPT-104`（口径单源化） |
| IMA《06》§一：版本 `4.68.1(471)` / HEAD `be4e1b9`，复核后 `4.68.2(472)` | 快照 | **当前 `4.68.4(474)` / `d9c4e61`**（其后还有 `4.68.3(473)`、`4.68.4(474)`） | 见 `OPT-604` 回写 |

---

## 附录 C · 本文件的取材与诚实声明

1. **取材来源**【实测】：`工作日志.md`（最新改动区）、`REVIEW.md`、`FIXLOG.md`、`docs/optimization-checklist-xinglian-benchmark.md`、`docs/agents/neverstop-watchdog.md`、`docs/agents/perf-baseline.md`、`scripts/`、`androidApp/build.gradle.kts`、`git log/status/ls-files`、实测行数与测试计数。
2. **IMA 侧材料**取自本地 Markdown 源 `build_qa/ima-init/notes/`（《06-进度与待办》《07-问题复盘》），其取材为 `v4.68.1(471)`、复核为 `v4.68.2(472)` ⇒ **落后于当前 HEAD**，凡引用处均已按附录 B 订正。**回写（`OPT-604`）已于 2026-10-03 通过 IMA OpenAPI 完成**（三篇追加 + 回读校验）；回写时发现的坑（`media_id` ≠ `note_id`）已作为原则 3 的第三条记入本文件，并同步写入 IMA《07-问题复盘》坑 32。
3. **本文件未做的事**：未新增任何门禁脚本、未跑 L1/L2/L3、未改任何源码、未执行任何发版动作。唯一的网络动作是 `OPT-604` 的 IMA 回写（`ima.qq.com`）。因此 §2 的「测试 276 例」来自 `@Test` 注解计数与 `工作日志.md` 记录的一致性，**不是本轮 Gradle 实跑结果** —— 首次执行 `OPT-000` 时应以实跑为准。
4. **未确认项**（不写入结论，留给执行者复核）：
   - `website/version.json` 的正确取值（本地 `420 / 4.66.0`，而 `v4.66.0` 从未发布；线上该文件曾 404）；
   - `docs/ux-interaction-spec.md` 中 40+ 条「⚠️ 待确认」的当前有效性（已知 ≥2 条与现状冲突）；
   - `build_qa/watchdog/` 近 150 个脚本中，哪些已失效/已被后续版本取代（未逐个人工判定）。
