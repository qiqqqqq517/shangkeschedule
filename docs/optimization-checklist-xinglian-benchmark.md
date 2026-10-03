# 星链对标优化清单

> **基线**：本仓库 `main` @ `796d24f`（v4.67.1，versionCode 432）。本文档的技术断言成稿于 `f892c66`（v4.66.0 / 420），此后已推进 5 个版本；**已逐条复核引用路径未被触及，断言仍成立**，漂移台账见 §0.6。
> **对标对象**：星链课表 `com.xlhzcm.starcurriculum` v4.20.0（versionCode 2280），arm64-v8a 单 ABI
> **产出日期**：2026-10-02（含一轮自查核对 + 一轮 watchdog 数字复核）
> **状态**：P0 全部 + P1 可完成项已实施（逐项状态见 §0.5）；余下条目待排期。
>
> **基线漂移提示（已复核，2026-10-02 watchdog 第 3 轮）**：本文档成稿后 `main` 推进到 v4.67.1（`796d24f`）。经逐条核对，
> **本文档引用的所有技术断言所依赖的文件路径均未被触及** —— `androidApp/.../notification/**`、`androidApp/.../receiver/**`、
> `androidApp/.../control/**`、`androidApp/.../widget/**`、`androidApp/src/main/AndroidManifest.xml`、
> `shared/.../tool/CalendarAccountManager*`、`shared/.../data/sync/WidgetDataSynchronizer.kt` 全部未发生语义变更
> （相关改动均为新增文件与新增资源，不改动既有断言）。故技术断言仍然有效。
> **但文档中的三处数字已过时**，已在 §0.6 记录并就地修正。

---

## 0. 文档说明

### 0.1 这份清单解决什么问题

仓库在 commit `7077564 feat(xinglian)`（v4.66.0）已完成一轮星链功能对标（空教室、四类考证查分、共同空闲、课表/主题分享、4 个小组件、AI 导入、意见反馈、隐私政策）。本清单**不重复该轮已补内容**，而是回答三个剩余问题：

1. 对方**系统能力打得比我们深**的地方，我们缺什么、缺到什么程度（带 `file:line`）
2. 对方**做错了**的地方，我们是否也踩了（红线）
3. 我们**做得更好**的地方，如何防止退化

### 0.2 对标方法（可复现）

| 手段 | 结果 |
|---|---|
| `aapt2 dump badging` / `xmltree` / `resources` | 包信息、组件表、小组件 provider 声明 |
| 自写 DEX 解析器（`string_ids` / `type_ids` / `field_ids` / `class_defs` / `class_data`） | **R8 混淆方法名但保留数据类字段名** → 147 个应用类的字段级架构可见 |
| `NOTICES.Z` 解 gzip + 按 80 连字符切块 | 144 个依赖包 |
| `libapp.so` ELF 分段解析 + OneByteString 头格式推导（`hdr = 0x80｜2×len`，45155 组样本验证） | 45936 个字符串对象 |
| `apksigner verify --print-certs` | 签名身份 |

**未取到的部分（诚实声明）**：对方 Dart AOT 快照中的中文 UI 文案未能精确还原。已验证 `OneByteString` 头格式，但枚举 24 组 TwoByteString 头部假设（4 种高位前缀 × 3 种长度倍数 × 2 种偏移，29 万次试验）全部 0 命中 —— 其 size 字段编码与 OneByteString 不同族。需 blutter 类快照解析器方可完成。**所幸中文文案不承载架构信息**：功能 IA 由 64 条命名路由 + 65 个领域模型 + 数据类字段名完整承载。

### 0.3 工作量档位

`S` ≤0.5 天 · `M` 1–2 天 · `L` 3–5 天 · `XL` >5 天

### 0.4 编号约定

本清单用 `XL-` 前缀，**刻意不与 `REVIEW.md` / `FIXLOG.md` 的 `R1-`/`R2-`/`R4-`/`R6-` 编号冲突**，两者是不同台账。

---

### 0.5 实施状态（v4.66.4 / code 429，2026-10-02 批次）

本轮实施了 P0 与 P1 中**全部可在本仓库内完成且可验证**的条目。逐项状态：

| ID | 状态 | 落地位置 |
|---|---|---|
| **XL-001** | ✅ 已修（**根因 + 防御双层**） | 根因：`ReminderEngine.autoModeTransitions` 由「合并转换点」改为「合并区间」，重叠/包含/链式三档全部修正；防御：`AutoModeAlarmReceiver` 在 END 前用 `shouldModeBeOn` 重算，仍在上课则跳过恢复 |
| **XL-002** | ✅ 已修 | `AutoModeController`：DND 分支补「记录—还原进入前过滤档」，两个分支均写入后回读校验，不一致返回 false 并记日志；还原失败保留记录待重试 |
| **XL-003** | ✅ 已修 | 新增 `AlarmPermissionReceiver`（manifest 静态注册，`exported=false`）：撤销时提示 + 降级重排，恢复时重排升级 + 撤提示 |
| **XL-004** | ✅ 改设计后落地 | **不再新建登记表**：改用既有 `NotificationScheduler.reschedule()` 统一入口 + `shouldModeBeOn` 重算。派生状态不落盘就不会漂移；落盘的收益为零、漂移的风险不为零 |
| **XL-010** | ⚠️ 已修，但**首版全量失败**（见 §1.2 真机验证） | `CalendarAccountManager.android.kt`：按**开始时刻**做增删改差分 + 写入后回读条数校验；课表为空时按同一键精确清理。v4.66.4 首版用的两列均被 CalendarProvider 拒绝，真机验出后于 v4.66.5 重做 |
| **XL-011** | ✅ 部分完成 | 8 个 provider XML 全部补 `android:description`（桌面选择器不再只显示工程名）+ 四语文案；顺带补 `android:label`（原先 8 个组件在选择器里标题全是「上课」，见 §1.3）；**v4.67.2 进一步把 label 由工程名（「超小课程2x1」「垂直列表课表4xN」）换成语义名（「课表 · 今日」「日程 · 考试」），description 换成一句用途说明，README / 官网功能页同步改名**。**12 个 widget token × 6 取值的规格扩展未做** —— 与 XL-005 排期决策点耦合，待定 |
| **（本轮新增）** | ✅ 已修 | v4.67.0 新增的 4 个规格引入 5 个新色条色，不在对比度门禁清单内 ⇒ 浅色档两个色实测 2.66:1 / 2.11:1 长期漏检。v4.67.1 修正色值 + 门禁扩到 6 色（已反向验证能拦住），见 §1.4 |
| **XL-012** | ⚠️ 部分完成（**2026-10-03 第 40 轮更正：AOSP 一级其实早已落地**，原记「形态提升未做」有误） | 已建 `LIVE_UPDATE` / `VIVO_ATOMIC` 两个渠道 + 四语文案，使不支持时能正常降级为普通通知。**AOSP 实况提升（Live Updates / 推广常驻）已实现**（v4.66.0 `70775649`，即本清单自身的落地批次）：四处投递点 `setRequestPromotedOngoing(true)` + `setShortCriticalText(...)` —— `CourseReminderNotifier.kt:131-135`、`NextClassNotifier.kt:169-173`（经 `LiveUpdateSupport.supportsLiveUpdate()` 门控 + `runCatching` 降级）、`CourseAlarmReceiver.kt:312-316`、`DynamicIslandService.kt:501-503`；能力探测 `shared/src/androidMain/kotlin/com/shangkeschedule/notification/live/LiveUpdateSupport.kt:30-36`（`SDK_INT >= BAKLAVA(36)` 且 `canPostPromotedNotifications()`）；权限 `POST_PROMOTED_NOTIFICATIONS`（`AndroidManifest.xml:13`）；设置页有实况能力卡。**仍未做**：vivo 原子通知（需厂商私有权限与 SDK，无公开文档）⇒ **不做猜测实现**。原稿「需 Android 17 的 SDK API、本项目 compileSdk 36 取不到该符号」已被实测证伪：现 `compileSdk = 37`，且 `javap` 实测 `android-37.0/android.jar` 里就有这些符号（实现走的是 `NotificationCompat`，本来也不依赖平台符号） |
| **XL-013** | ✅ 已完成 | 新增 `OemGuide` / `OemGuideResolver`（纯逻辑、无文案、7 个单测）+ `WidgetTroubleshootBridge.manufacturer()`（expect + 3 actual）+ 排障页新增引导区块（9 条四语资源）。未识别厂商**不给猜测步骤** |
| **XL-014** | ✅ 已修 | `TimeChangeReceiver` 改为 manifest 静态注册（移除 `MyApplication` 运行时注册）+ 新增 `LOCALE_CHANGED`；修正其 KDoc 中与实现不符的「静态注册」表述 |
| **XL-015** | ✅ 已完成（v4.67.0） | `WidgetPlacement.specs` 逐 provider 明细 + `WidgetTroubleshootBridge.requestPin(key)`（expect + android/jvm/ios 三处 actual）；排障页新增「添加到桌面」区块。**顺带修掉一个真机才暴露的问题**：8 个 provider XML 原本都没有 `android:label`，桌面选择器里 8 个条目标题全是「上课」，只靠 description 区分 —— 补 label 后每个组件在选择器里有自己的名字。详见 §1.3 |

**顺带补的基础设施**：`AppLog` 增加 `i` 级别（expect + android/jvm/ios 三处 actual）—— shared 层此前只有 `w`/`e`，导致信息级日志无处可去。

**验证**：`:shared:compileKotlinJvm` + `:desktopApp:compileKotlin` + `:androidApp:assembleDebug` 全绿；单测 **217 例全通过、0 失败 0 跳过**（新增 5 例重叠回归 + 7 例厂商识别）。
> **数字已过时（2026-10-02 watchdog 第 3 轮实测）**：本批次当时的 217 例是对的，但仓库此后又增 4 例（v4.67.0 的 `WidgetPinStringsTest`）。
> **口径演进（每一步都可静态复现）**：217（v4.66.4 批次）→ 221（2026-10-02 复核）→ **229（2026-10-03 watchdog 第 39 轮实测，v4.67.29）**。
> **当前真实口径 = 229 例 / 0 失败 / 0 错误 / 0 跳过**（shared **162** + androidApp **67**；两次独立计数 —— Gradle XML `count_tests.py` 与 `@Test` 注解数 —— 完全一致）。
> 另注：本仓库 L3 的真实 Gradle 任务是 `:shared:testAndroidHostTest` + `:androidApp:testDebugUnitTest`；
> `:shared:jvmTest` 是 `NO-SOURCE`（`shared/src` 下无 `jvmTest` 源集），写成它会**静默漏跑 162 例**。

**本轮发现并修正的自身错误**（供后续参考）：
1. 重写日历写回时把 `withValueBackReference` 的批次索引算错，且把提醒分钟数放进内容指纹 —— 会导致**每次同步全量重写**，正好退回旧行为。改为查询 `Reminders` 归并真实分钟数后才正确。
2. ~~`Events.UID` 无法通过 `CalendarContract.Events` 解析（该常量在 **protected** 的 `SyncColumns` 上），最终用具名列名常量 `COL_UID = "uid"`。~~ **这条判断整个是错的**，见 §1.2 第 1 条。
3. 向 data class 尾部误插一个 `}`，导致 ViewModel 语法错误被编译器报成「primary constructor must only have property」。

---

### 0.6 漂移台账（watchdog 第 3 轮复核，2026-10-02）

本节记录「文档数字随代码演进而过时」的逐条台账。**原则：文档里凡是能被静态计数验证的数字，都必须随代码一起更新**，
否则下一个读者会拿一个过时的数字当验收基线 —— 这与 v4.67.1 的对比度门禁漏洞是同一类问题（清单没跟上代码）。

| # | 位置 | 成稿时 | 复核实测 | 处理 |
|---|---|---|---|---|
| 1 | §0.5 验证口径 | 单测 217 例 | **221 例**（shared 156 + androidApp 65），0 失败 0 错误 0 跳过 | 就地加注「已过时 + 当前值」，保留原数字并说明其当时是对的 |
| 2 | §7 XL-032 证据 | shared `androidHostTest` 15 文件 / 140 `@Test` | **17 文件 / 156 `@Test`** | 已就地修正 |
| 3 | 附录 · 证据索引 | 同上「15 文件 / 140 例」 | 同上 | 已就地修正 |
| 4 | §7 XL-032 证据 | androidApp 8 文件 / 65 `@Test` | **8 文件 / 65 `@Test`** | ✅ 复核仍准确，不动 |
| 5 | `docs/widget-display-optimization.md` | 全文按 **4 个规格**撰写（7 处：「4 个主布局」「影响 4 个规格 × 全部状态」「4 规格 × 全状态 × 3 主题 × 深浅 = 24 个组合」等） | 实际已 **8 个规格** | **本轮不改**，见下方「为何不动」 |

#### 为何第 5 项本轮不动

`widget-display-optimization.md` 属**方案设计文档**（`§4.4` 明确写它是「与 XL-005 的排期决策点」耦合的）。
按 `docs/agents/neverstop-watchdog.md` §7.3 与本仓库 `AGENTS.md`，watchdog **无权自行修改方案文档与发版流程**；
且 4→8 规格的 token 扩展本身就是 **XL-011 的工作内容**（该文档 §6.3 的 12 token × 6 取值需补到 48 组合），
不是把「4」改成「8」就能收尾 —— 改了数字而不补 token 表，反而会让文档更自相矛盾。

**需要用户决定**：XL-011 与 XL-005 §4.4 是同一个排期决策点（先 Glance S1 试点 / 还是先补 token 扩展）。
建议先定这条，再回来一次性把 `widget-display-optimization.md` 改到位（含 token 表与排期），避免改两遍。

#### 复核方法（可复现）

```powershell
# 测试口径：两份独立计数互相印证（Gradle XML 结果 vs @Test 注解数），不一致即为异常
python build_qa/watchdog/count_tests.py          # 解析 build/test-results/**/*.xml
Select-String -Path (Get-ChildItem -Recurse shared\src\androidHostTest -Filter *.kt).FullName -Pattern '^\s*@Test'
```

两份口径本次（2026-10-02）均得到 shared 156 / androidApp 65，**互相印证**后才写入文档 —— 不采信任何单一口径。

#### 2026-10-03 watchdog 第 39 轮复核（v4.67.29 / code 460）

上一轮台账之后仓库又演进了一天，**被计数的对象本身又变了** —— 正是本节要防的同一类问题，故再记一轮。方法同上（两份独立计数），结论如下：

| # | 位置 | 上轮台账值 | 本轮实测 | 变化来源（逐条可 `git log` 复现） |
|---|---|---|---|---|
| 6 | §0.5 验证口径 / 附录 · 证据索引 | 221 例（shared 156 + androidApp 65） | **229 例**（shared **162** + androidApp **67**） | shared：`abc5f4d`（v4.67.16）新增 `LocalSecretsMigrationTest` 3 例；androidApp：v4.67.29 给 `WidgetListCapacityTest` 增 2 例 |
| 7 | §7 XL-032 · shared 口径 | 18 文件 / 159 `@Test` | **19 文件 / 162 `@Test`** | 同第 6 行 shared 侧（`abc5f4d` 那 3 例） |
| 8 | §7 XL-032 · androidApp 口径 | 8 文件 / 65 `@Test` | **8 文件 / 67 `@Test`** | v4.67.29 新增 2 例 |
| 9 | 附录 · manifest receiver 数 | 「15 个 receiver」（v4.66.0 审计快照 `7077564`） | **17 个**（含 8 个小组件 provider） | 本清单自己已实施的两条各加 1 个：XL-003 的 `AlarmPermissionReceiver`、XL-014 把 `TimeChangeReceiver` 由运行时注册改为 manifest 静态注册（同一提交 `ece7a90` v4.66.4）；`7077564` → HEAD 无移除 |
| 10 | 附录 · 小组件布局数 | 「16 个小组件布局 XML」 | **16（`res/layout/`）+ 4（`res/layout-night/` 同名深色档）= 20** | 深色档只覆盖其中 4 份；按 `res/layout*/widget_*.xml` 数会得到 20，故补注口径 |
| 11 | §7 XL-034 · 数据层 | `DatabaseMigrations.kt`（23KB） | **24854 B ≈ 24.3 KiB** | 迁移脚本与注释持续增补 |
| 12 | 附录 · manifest 行范围 | 「L83–238」 | **receiver 段现为 L108–286**（文件共 293 行） | 期间新增/调整了组件与 `<meta-data>` |
| 13 | 附录 · 精确闹钟监听 / `TimeChangeReceiver` 两行 | 按「现状」写成「未监听」「由 `MyApplication` 运行时注册、不在 manifest（不符）」 | **两处均已修**（XL-003 的 `AlarmPermissionReceiver`；XL-014 的静态注册 + KDoc 改正），行内已加「审计时 / 已修」限定 | 附录是证据索引、读法默认「现状」；审计发现一旦被修复就必须标注，否则与本清单 §0.5 的 ✅ 自相矛盾 |
| 14 | 附录 · 日历锚点 | `CalendarAccountManager.android.kt:93,143`、`CALLER_IS_SYNCADAPTER :64` | **`:288-291` / `:333` / `:414`、`:86`** | XL-010 按「开始时刻」增量差分重写该文件，旧行号全部失效 |

**处理原则（本轮起明确写下）**：**带日期的历史记录不改写，声称「当前」的口径必须准确。**
§0.5 的两条注记、§7 XL-032、附录 · 证据索引里所有**当前口径**数字已就地更新为上表实测值；
§1.1「核对轮次记录」与 §0.6 第 1–5 行是**当时那次核对的历史记录**，保留原值不动（否则历史就无法复现「当时为什么改」）。
另注：§7 XL-032 原写「两份口径实测与 `@Test` 注解数完全一致」—— 该句子本身没错，但它的一致性是在**写文档那一刻**成立的；
本轮复发现，此后只要有人加测试而不同步本文档，数字就会再次漂移。**这是本文档的结构性风险：它的验收基线同时是它自己的统计对象。**

#### 2026-10-03 watchdog 第 40 轮复核（v4.67.30 / code 461）

第 39 轮修的是**当前口径数字**；本轮修的是同一根因的另一半：**已修条目的审计块仍在用「现状」语气叙述旧事实**，
外加一处对标对象的**实测数字与自身枚举自相矛盾**。方法：`aapt2 dump xmltree --file AndroidManifest.xml 星链.apk` 实测对手，
`Select-String`/逐行读本仓库 manifest 实测自己 —— 两侧都以命令输出为准，不采信旧稿。

| # | 位置 | 上轮台账值 | 本轮实测 | 变化来源 |
|---|---|---|---|---|
| 15 | §0.6 · 对标段「对方 receiver 的 action 组」 | 「同一组 **8 个** action」 | **12 个**（7 个 `APPWIDGET_*` + 时间四件套 + 1 个厂商私有） | aapt2 实测 8 个 widget receiver **各 12 条**，每个 action 名恰好出现 8 次；原稿标题数字与紧随枚举 7+4+1 自相矛盾 |
| 16 | §0.6 · 对标段「我方 `LOCALE_CHANGED` 位置」 | `TimeChangeReceiver`（`:123`） | **`AndroidManifest.xml:141`**（intent-filter `:144-149`） | 组件增删后行号漂移；并补「时间四件套与星链逐字一致」的实测口径 |
| 17 | §2 XL-003 审计块 | 「manifest 共 15 个 receiver」「grep 命中 0 次」「`TimeChangeReceiver` 运行时注册、不在 manifest」 | 加 `> **状态**` 行：**17 个** receiver；监听者 = `AlarmPermissionReceiver`（`:127-134`）；`TimeChangeReceiver` 已**静态注册**（`:141`） | 该条在 §0.5 是 ✅ 已修 ⇒ 审计块不得被读成现状 |
| 18 | §2 XL-010 审计块 | `:64` / `:93-95` / `:122-136` | 加 `> **状态**` 行：**`:86`** / **`:288-291`** / **`:333`、`:414`** | XL-010 按「开始时刻」增量差分重写该文件，旧行号全部失效 |
| 19 | §2 XL-014 审计块 | 「覆盖 3 个 action」「由 `MyApplication` 运行时注册，不在 manifest」 | 加 `> **状态**` 行：**4 个 action**；已**manifest 静态注册**；`APPWIDGET_VISIBLE/HIDDEN` 按 2026-10-02 决策不跟 | 同第 17 行根因 |
| 20 | §2 章节首 · 读法 | 无（各 XL 块直接以「现状（已核实）」开头） | 章节首加「读法」引用块：**审计快照 ≠ 现状，落地状态以 §0.5 为准** | 系统性歧义：读者会把问题陈述读成当前状态 |
| 21 | §0.6 · 对标表「我方对齐 XL-003」 | 我方 `AndroidManifest.xml:103-107` | **`:127-134`** | 同一提交新增 `AlarmPermissionReceiver` 后行号漂移；原稿是唯一一处被静态证伪的「我方」锚点 |
| 22 | 附录 · 证据索引 `AlarmScheduler.kt` 三处 | `:77`（权限检查）/ `:27`（旧槽位注释）/ `:44`（提示去重） | **`:92`** / **`:42`** / **`:65`**（使用 `:92-96`、重置 `:126`） | 该文件头 KDoc 被重写为「请求码命名空间全表（2026-10-03 逐行实测）」，`setExact` 下移，三处锚点整体漂移 |
| 23 | 附录 · 证据索引 `AutoMode*` 四处 | `AutoModeAlarmReceiver.kt:48`（END 无条件恢复）、`:61,64`（action 常量）、`AutoModeController.kt:62-63`、`:97` | **`:48` 已是 KDoc**（判定在 `:59` + `:106`）；常量在 **`:122,125`**；`toggleDnd` 在 **`:67`**（`:127` 回读确认后才 true）；`toggleSilent` 在 **`:138`** | XL-001/XL-002 已修 ⇒ 索引引用的正是**被删掉的旧实现**；`AutoModeStateProbe.kt:24-33` 复核仍 ✓，`NotificationScheduler.kt:103,111,115-136,138,145-149` 复核仍 ✓ |
| 24 | 附录 · 证据索引「4 条链路 × 10 个 action 常量」 | `ReminderAlarmReceiver.kt:109,112,115`；`AutoModeAlarmReceiver.kt:61,64`；`DynamicIslandManager.kt:126,127` ✓；`CourseAlarmReceiver.kt:52` | **`:111,114,117`**；**`:122,125`**；`:126,127` ✓；**`:53`** | 各接收器 KDoc/常量顺序调整；`DynamicIslandManager.kt` 的 `canScheduleExactAlarms` 复核仍在 `:175` ✓ |
| 25 | 附录 · 章节首 · 读法 | 无（表格直接以结论行开头） | 章节首加「读法」引用块：**本表是审计快照的证据索引，✅ 已修条目引用的是审计当时行号** | 与 §2 同一系统性歧义：证据索引默认被读成当前状态 |
| 26 | §0.5 XL-012 行 + §2 XL-012 现状 | 「**形态提升未做**」「**未见** AOSP 实况通知（Android 17）」 | **AOSP 一级早已实现**（v4.66.0 `70775649`）：四处 `setRequestPromotedOngoing(true)` + `setShortCriticalText`（`CourseReminderNotifier.kt:131-135`、`NextClassNotifier.kt:169-173`、`CourseAlarmReceiver.kt:312-316`、`DynamicIslandService.kt:501-503`）+ `LiveUpdateSupport.kt:30-36` 能力探测 + `LIVE_UPDATE` 渠道 + `POST_PROMOTED_NOTIFICATIONS`（`AndroidManifest.xml:13`）+ 设置页能力卡；仍未做的只有 vivo 原子通知 | **方向相反的同一类漂移**（第 39 轮是「已修写成现状」，这条是「已做写成未做」）：清单把自家落地批次（`70775649`）里的实现记成了待办 |
| 27 | §0.5 XL-012 理由句 | 「需 Android 17 的 SDK API（本项目 **compileSdk 36** 取不到该符号）」 | `gradle/libs.versions.toml:4 android-compileSdk = "37"`；`javap` 实测 `android-37.0/android.jar` 含 `Notification$Builder.setRequestPromotedOngoing(boolean)` / `NotificationManager.canPostPromotedNotifications()` / `Notification.FLAG_PROMOTED_ONGOING` | 版本目录升到 37 后该理由失效；且实现用的是 `NotificationCompat`，从不依赖平台符号 |

**校验方法（可复现，本轮用过的三条）**：
1. **只查可能漂移的文件** —— `git diff --name-only 796d24f..HEAD`（审计基线 `796d24f` = v4.67.1）给出「可能漂移」的文件白名单，白名单外的锚点按定义仍有效（如 `AutoModeStateProbe.kt`、`NotificationScheduler.kt` 本轮复核 ✓）。
2. **两侧都取命令输出** —— 对手数字用 `aapt2 dump xmltree --file AndroidManifest.xml 星链.apk`，自己用 `Select-String` 逐行打印，不以旧稿为准。
3. **按「断言类型」分层** —— 行号/条数 → 必须就地纠正；定性描述 → 只加读法注，不重写（否则文档不可维护）。

**本轮处理原则（对第 39 轮原则的补充）**：**「已修」不是删除审计块的理由，但必须让审计块无法被误读成现状** ——
保留问题陈述（它是条目存在的理由），在块内补一行带日期的 `> **状态**`，并只在含**可静态证伪的数字/行号**的块上做（XL-003/010/014）；
纯定性描述的块不逐条重排，避免把文档改成无法维护的形态。

---

### 1.2 真机验证（v4.66.5 / code 430，2026-10-03）

> 设备：小米 22041216UC · Android 14（SDK 34）· targetSdk 37 · debug 包
> 动机：上一批改的 P0 四项全部只过了编译期与单测，而它们动的都是真实系统状态。

#### 结论先说：真机抓到一个编译期与单测都看不见的回归

v4.66.4 交付的 XL-010（日历增量写回）**在真机上 100% 失败**，且失败发生在写日历的第一步，
用户侧表现为「点了同步到系统日历，什么也没发生」。

单测之所以测不出来：这条链路全程依赖 `ContentResolver` → 真机 CalendarProvider，
仓库里没有任何 androidTest 源集，纯 JVM 单测根本没有能力覆盖它。

#### 三个被真机否定的假设（按发现顺序）

| # | 当时以为 | 真机实际 | 证据 |
|---|---|---|---|
| 1 | `Events` 的稳定标识列叫 `uid`；常量在 protected 的 `SyncColumns` 上，取不到才用字面量 | **`android-36` 的 `CalendarContract.java` 里根本没有 `UID` 常量**（只有 `CAL_SYNC1..10` 与 `DIRTY`）。`uid` 是 Provider 内部列，不在普通调用方的投影白名单 | `IllegalArgumentException: Invalid column uid`（`readExisting` 第 160 行） |
| 2 | 改用公开的 `Events.ORIGINAL_ID`（`"original_id"`，可读可写）即可 | 该列语义是「本事件作为**例外**所归属的原重复事件 `_id`」。在非重复事件上写它，Provider 会去解析那个不存在的原事件 | `applyBatch` 抛 `NullPointerException: Long.longValue() on null`（栈顶在 provider 进程） |
| 3 | 按 `_id` 构造单条事件 URI 用 `appendQueryParameter("_id", …)` 即可 | Provider 不接受 query parameter 形式的 `_id` | `IllegalArgumentException: Invalid URI parameter: _id`。正确写法是 `ContentUris.withAppendedId`（路径段） |

第 3 条尤其值得记：**delete / update 两条路径在首轮验证中根本没被执行过**（日历当时是空的，161 条事件全是 insert），所以前两个 bug 修完仍然只暴露出第三个。**新写的分支必须逐条触发过才算验证过。**

#### 最终方案：不写任何隐藏列

差分需要「认出还是那堂课」的键。隐藏列 `uid` / `original_id` 都被占用或禁止，
于是改用**一次课的开始时刻 `dtstart` 本身**作为天然唯一键：

- 同一时刻在本日历里至多一堂课 —— `IcsExportTool.processCourseInstances`
  对每个 (课程, 周次) 只产出一个跨越全部连排节的实例；
- 公开、可查可写，不参与任何重复规则解析；
- 课程改名 / 换教室时**保持不变**，正好符合「同一堂课」的定义 —— 这是 `uid` 方案想达到而失败的效果。

附带处理了一个真实边界：若同一开始时刻出现多条事件（日历里混进了手工事件或历史脏数据），
多余的会被显式删除。否则它们永远匹配不上期望集合、回读校验会恒定失败、同步每次都判失败。

#### 真机验收结果（四条路径逐条触发）

| 场景 | 日志 | 结果 |
|---|---|---|
| 首次同步（日历为空） | `期望=161 回读=161 （新增 161 / 更新 0 / 删除 0）` | ✅ insert 路径 |
| 立即重复同步 | `期望=161 回读=161 （新增 0 / 更新 0 / 删除 0）` | ✅ **零写入**，增量差分成立 |
| 课程改名后同步 | `期望=161 回读=161 （新增 0 / 更新 29 / 删除 0）`，事件总数不变 | ✅ update 原地改，不删重建 —— 保住事件 `_id`，用户在日历 App 上加的提醒不丢 |
| 缩减上课周次后同步 | `期望=154 回读=154 （新增 3 / 更新 0 / 删除 10）` | ✅ delete 路径 |

设备侧交叉核对：`calendar_id=2` 实际 161 条事件、提醒表 10 → 171（净增 161 条，每事件一条）、
标题分布与课表一致。

#### 这次验证没能覆盖的（据实列出）

- **XL-001 / XL-002**：需要真的进入上课时段，观察**响铃模式**与**勿扰状态**变化。
  这会打断真机主人自己的通话，属侵入性操作，未做。二者目前只有编译期 + 5 例重叠单测保障。
- **XL-003**：撤销 `SCHEDULE_EXACT_ALARM` 的广播由系统权限 UI 流程触发，
  `adb shell cmd appops set` 改不了（只改 op 不发广播）。已核实 receiver 在**已装 APK 里**
  （`dumpsys package` 可见 `Action: android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`），
  但广播投递未验证。
- **XL-014**：`TIMEZONE_CHANGED` / `LOCALE_CHANGED` / `DATE_CHANGED` 是 protected broadcast，
  shell（uid 2000）无权发送。已核实四个 action 都在**已装 APK 的 manifest** 里，
  但实际投递未验证。

#### 方法论沉淀

本轮为真机驱动写了可复用的工具（`uinav.py`：`uiautomator dump` → 按文本定位节点 → 模拟点击），
并踩掉了三个坑，都与「UI 自动化」本身有关：

1. `uiautomator dump` 要的是**设备端路径**，传 Windows 路径静默失败；
2. `adb shell` 的输出是 UTF-8，Python `subprocess` 不显式给 `encoding='utf-8'` 会被按 GBK 解成乱码；
3. **点一下输入框后输入法弹出，整个界面会上移**（实测 y 从 797 变 319）。
   后续坐标必须重新 dump 取，不能复用 —— 我第一次长按正是因此误触了周次选择器。

---

### 1.3 XL-015 小组件固定到桌面（v4.67.0 / code 431）

#### 做了什么

排障页此前对「一个小组件都没添加」的用户只能提示「去桌面长按添加」。现在给出 8 个可点的入口：

| 层 | 内容 |
|---|---|
| `WidgetPlacement` | 新增 `specs: List<WidgetSpec>`，逐 provider 给出 `key` / `label` / `placedCount` |
| `WidgetTroubleshootBridge` | 新增 `requestPin(key): WidgetPinOutcome`（REQUESTED / REJECTED / UNSUPPORTED） |
| 排障页 | 新增「添加到桌面」区块，位置在「排障操作」**之前** —— 先添加是再排障的前提 |
| `SettingItem` | 新增 `enabled` 参数，供固定过程中防连点 |

两个设计取舍：

1. **`key` 用 provider 的全限定类名**，不用 `courseId:week` 式的业务键。它只在本进程内往返、从不落盘、从不上报，
   类名不会与文案改版冲突，也省掉一层映射。
2. **`label` 取 `AppWidgetProviderInfo.loadLabel()`**，不在 shared 里另写一套 8 条文案。
   这样列表里的名字与用户即将在系统选择器里看到的**必然是同一个字符串**，不会出现两套名字对不上。

用 `requestPinAppWidget` 而非 `ACTION_APPWIDGET_BIND`：后者要求用户输密码绑定启动器，
那与「放一个组件到桌面」不是一回事。不传 `callback` —— 固定成功与否由系统弹窗自己交代，
应用侧只报「请求有没有被受理」，不假装已经放好了。

#### 顺带修掉一个真机才暴露的问题：8 个组件在选择器里同名

provider XML 原本**都没有 `android:label`**。`AppWidgetProviderInfo.loadLabel()` 在没有 label 时会
回落到**应用名**，也就是说桌面选择器里 8 个小组件的标题**全是「上课」**，只靠 description 那行区分：

```
上课   课表 · 超小
上课   课表 · 紧凑
上课   课表 · 连续两日
```

`widget_name_*` 这 8 条标签（"超小课程2x1" 等）**本来就存在于四语文案里**，只是从来没被任何 XML 引用。
补上 `android:label="@string/widget_name_*"` 即可，**零新增文案**。

真机截图确认修复后（MIUI 选择器）：

```
上课
  超小课程2x1   紧凑课程2x2   近日课程4x2   …
  考试倒计时… 3x2   周课程列表… 4x3   日程清单3x3 3x3
```

同一屏上 Telegram / 哔哩哔哩 仍然是「Telegram」「哔哩哔哩」那种同名重复 —— 这是**别的应用**的问题，
不在本仓库范围内，但足以对照出本应用现在的差别。

#### 真机验证

| 环节 | 结果 |
|---|---|
| 排障页渲染 | ✅ 「添加到桌面」区块出现，8 行逐 provider 列出；`近日课程4x2` 显示「已添加 1 个」，其余显示添加提示 |
| `loadLabel` 回退 | ✅ 未识别时回落到类名尾部，不会出现空白行 |
| 请求送达系统 | ✅ `ActivityTaskManager: START u0 {act=android.content.pm.action.CONFIRM_PIN_APPWIDGET ... com.miui.home/.launcher.AddItemActivity}` |
| 实际放置 | ⚠️ **未发生**，`placedCount` 前后都是 1 |

最后一项要如实说明：**MIUI 桌面没有实现「就地确认并放置」**。
它把 `CONFIRM_PIN_APPWIDGET` 这个 intent 走成了「打开小部件选择器」——
截图上看到的是完整的「添加小部件」列表页，而不是一个「要添加这个组件吗？」的确认框。
因此请求**路径正确**（intent 确实发到了桌面且被接收），但**落地行为由桌面实现决定**，应用侧无法保证。

这也是为什么 `requestPin` 不传 callback、不假装成功：`WidgetPinOutcome.REQUESTED` 只表示
「系统受理了请求」，用户是否真的放上桌面，取决于桌面对这个 intent 的实现。
排障页的「已添加 N 个」始终读**系统里的真实实例数**，不会因为请求过就显示成已添加。

---

### 1.4 小组件对比度门禁的覆盖漏洞（v4.67.1 / code 432）

#### 漏洞本身

v4.67.0 把小组件从 4 个规格扩到 8 个，新增的「日程清单」与「考试倒计时」各自引入了一组左侧色条色。
而 `scripts/check_widget_contrast.py` 的 `NON_TEXT_CHECKS` 里只有 **一个**色（`widget_course_fallback`）
加两个文字色 —— **这 5 个新色从来不在门禁覆盖范围内**。

门禁漏检与缺陷是同一件事的两面：既然没检查，就没人会发现不合格。

#### 实测结果：两个色不达 WCAG 2.2 §1.4.11

| 色 | 原值 | 浅色档 | 深色档 | 判定 |
|---|---|---|---|---|
| `widget_agenda_activity`（活动） | `#2BAE85` | **2.66:1** | 9.22:1 | ❌ 浅色独有 |
| `widget_agenda_homework`（作业） | `#E0A32E` | **2.11:1** | 10.71:1 | ❌ 浅色独有 |
| `widget_agenda_todo`（待办） | `#5B8DEF` | 3.07:1 | 7.79:1 | ✅ |
| `widget_agenda_other`（其他） | `#8E8E93` | 3.10:1 | 8.05:1 | ✅ |
| `widget_exam_accent`（考试） | `#C0392B` | 5.17:1 | 6.70:1 | ✅ |

#### 为什么判定为「承载信息」而非装饰

门禁的 `DECORATIVE_CHECKS` 里 `widget_divider` 是 1.13:1 却判定为「不强制」，依据是
§1.4.11 只约束「识别组件与状态**必需**的视觉信息」。这两个色不一样：

- 用在 `layout/widget_course_color_bar.xml` 的 `course_bar_light` / `course_bar_dark`
- 4dp 宽、`match_parent` 高的实心色块
- 颜色区分的是**日程类别** —— 哪一条是待办 / 活动 / 作业

也就是说，用户**靠这个色块识别类别**，去掉它信息就丢了。这正是 §1.4.11 的适用情形。

#### 修法：沿亮度轴最小调整，不重新设计色相

| 色 | 修后 | 浅色档 | 与主文字色 `#2C3E50` 的对比 |
|---|---|---|---|
| `widget_agenda_activity` | `#28A37D` | 3.01:1 | 3.47:1 |
| `widget_agenda_homework` | `#BE861C` | 3.02:1 | 3.46:1 |

在 HLS 空间只动 L、保持色相与饱和度，**刚过 3:1 就停**，不刻意加深。
理由：色条是 4dp 的窄色块，加深到 6:1 会与左侧主文字抢注意力，反而损害可读性。
调整后两者对比 3.46:1，类别色与正文仍可区分。

**深色档一个色都没改** —— 同样这四个色在 `#141218` 上是 7.79 / 9.22 / 10.71 / 8.05 :1 全部合格，
缺陷是浅色独有的。

#### 门禁扩充，并做了反向验证

`NON_TEXT_CHECKS` 从 1 项扩到 6 项（加 `widget_exam_accent` + 4 个 `widget_agenda_*`）。

加完清单后**必须确认它真的会拦**，否则只是加了一行永远 PASS 的装饰。所以做了反向验证：

```
# 把两个色改回原值
$ python scripts/check_widget_contrast.py
  [FAIL] widget_agenda_activity   #2BAE85  需 ≥3.0:1  实测 2.66:1
  [FAIL] widget_agenda_homework   #E0A32E  需 ≥3.0:1  实测 2.11:1
exit=1

# 恢复修复值
$ python scripts/check_widget_contrast.py --quiet
[widget-contrast] 通过
exit=0
```

**遗留**：本轮只改颜色资源，未装机。真机浅色档下「日程清单」色条的实际观感
（加深后是否仍与主文字协调）需要肉眼确认一遍 —— 数值达标不等于观感达标。

#### 新增自动化闸（上一轮）

`WidgetPinStringsTest`（4 例）：三个 locale 的 key 集合必须完全一致 + 占位符编号对齐。
这一组文案是逐 locale 手写新增的，而 compose-resources 缺 key 时是**运行期**才回退默认 locale ——
漏翻一个语言不会编译失败、不会单测失败，只会在那个语言的设备上默默显示简体字。
上一轮 XL-013 的 9 条资源踩的是同一个坑（当时无自动闸），这次补上。

---

### 1.5 对手 APK 实测复核（v4.20.0 / code 2280，2026-10-02）

> 样本：`C:\Users\30458\Downloads\星链.apk`，35.4 MB，SHA-256 `78B3CF1149A169B5E0861C846D1E826F9D7E2EFC2BDC53F87417623A422578A8`
> 工具：`aapt2 dump badging` / `aapt2 dump xmltree --file AndroidManifest.xml` / `apksigner verify --print-certs`（build-tools 37.0.0）
> 目的：对本文档中**可被静态验证**的断言做一次独立复算，防止「文档转述几轮之后与事实脱节」——
> 这与 §0.6 记录的三个过时数字是同一个风险，只是方向相反。

#### 与文档既有断言的一致性

| 文档断言 | 实测 | 结论 |
|---|---|---|
| package `com.xlhzcm.starcurriculum` / 4.20.0 / code 2280 | 一致 | ✅ |
| min 24 / target 36 / compile 37（platformBuild 17） | 一致 | ✅ |
| XL-042：调试证书 `CN=Android Debug` | `C=US, O=Android, CN=Android Debug`，SHA-256 **`e4c2dee99824269cc4d3c8a4ed11a543aa22f60c49244b92da5f220b5b3f40bd`** | ✅ **确认是调试证书**（与我方正式签名 `4ae49d8c…2475f` 完全不同） |
| XL-033「我们无位置/相机/电话/安装包权限」 | 对方 manifest 实测声明 `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` + `CAMERA`（`uses-feature required=false`）+ `READ_PHONE_STATE` + `REQUEST_INSTALL_PACKAGES` | ✅ **我方权限克制优势成立**，且证据比原文档更具体 |
| XL-003：`AlarmPermissionReceiver` + `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` | receiver 实测存在，`exported=false`，intent-filter **只挂这一个 action**（manifest line 470-479） | ✅ 一致；我方 `AndroidManifest.xml:127-134`（2026-10-03 复核；原记 `:103-107` 已失效）已完全对齐（同样 `exported=false` + 单一 action） |

#### 本次新发现（文档此前未记录）

1. **对方 8 个小组件全部是 Glance 实现，且每个 receiver 监听同一组 12 个 action**

   类名一律 `com.xlhzcm.starcurriculum.glance.*GlanceWidgetReceiver`，共 8 个（与文档「8 个 provider」数量吻合）：
   `WeekCourses` / `TodayCourses` / `SmallTodayCourses` / `RecentCourses` /
   `ExamCountdown` / `NextSchedule` / `TodayAgenda` / `WeekAgenda`。

   每个 receiver 的 intent-filter 固定为**同一组 12 个 action**
   （2026-10-03 `aapt2 dump xmltree` 实测：8 个 receiver 各 12 条，且每个 action 名恰好出现 8 次）：
   `APPWIDGET_UPDATE` / `ENABLED` / `DISABLED` / `DELETED` / `RESTORED` / **`VISIBLE`** / **`HIDDEN`**（7 个）
   + 时间四件套（`DATE_CHANGED` / `TIMEZONE_CHANGED` / `TIME_SET` / `LOCALE_CHANGED`，4 个）
   + 厂商私有 `miui.appwidget.action.APPWIDGET_UPDATE`（1 个），并声明 `miuiWidget=false` meta-data。
   > 原稿此处写「同一组 **8 个** action」，与紧随其后的枚举（7 + 4 + 1 = **12**）自相矛盾；
   > 2026-10-03 watchdog 第 40 轮以 aapt2 实测纠正为 12。

   → **这为 XL-014 第 2 条「核查 `APPWIDGET_VISIBLE/HIDDEN` 的必要性」给出了确定答案**：对方确实监听，
   且是**每个 provider 都监听**。我方当前 manifest 仅在 `TimeChangeReceiver`
   （`AndroidManifest.xml:141`，intent-filter `:144-149`）挂了 `LOCALE_CHANGED`
   —— 2026-10-03 复核：该 filter 已含完整**时间四件套**（`TIMEZONE_CHANGED` / `TIME_SET` / `DATE_CHANGED` / `LOCALE_CHANGED`），
   与星链逐字一致；**未挂 `APPWIDGET_VISIBLE/HIDDEN`**。

   **✅ 已决策（2026-10-02，用户裁定）：选 (a) 隐藏时不刷新，以省电。**
   即对齐我方现有行为与本清单 XL-014 验收标准第 4 条「组件被桌面隐藏时不触发刷新（省电）」，
   **不引入该监听**。我方现状即目标态，零代码改动。
   记在此处的目的是：避免后续轮次或他人把「对手有、我们没有」当成待办重复提出 ——
   **「有」不等于「该跟」**，这一条已判定为不跟。

2. **对方有独立的 `BootReceiver` 与 `DndActionReceiver`**
   `com.xlhzcm.starcurriculum.receiver.BootReceiver`、
   `com.xlhzcm.starcurriculum.receiver.DndActionReceiver`（对应自定义 action `ACTION_ENABLE_DND`）。
   → 我方开屏重排走 `LOCKED_BOOT_COMPLETED`（`REVIEW.md` 批12 已清理过死配置）。
   本轮**未核对**我方 `BOOT_COMPLETED` 一路是否仍完整，留待后续轮次。

3. **对方确无 backup rules（再次确认）**
   `application` 段未出现 `android:allowBackup` / `android:dataExtractionRules`，
   与 XL-034「我们三件套齐备、星链均无」一致。✅

4. **对方的 Glance 落地印证了 XL-005 的技术可行性**
   8 个 receiver 全为 `glance.*` 命名空间，说明 **XL-005 路径 A（先 Glance S1 试点）在对方处已完整跑通**，
   可直接作为我方 S1 的可行性背书。但**不等于**应当照搬 —— 收益仍需按 §4.6 逐阶段实测。

#### 本轮未产生任何代码改动

以上全部为**文档层复核**，未触及 `androidApp/` / `shared/` 任何源文件，故不触发 watchdog §6 收尾流程（不 bump、不提交）。
真正待做的两条（`APPWIDGET_VISIBLE/HIDDEN` 补齐、`BOOT_COMPLETED` 核对）属**行为变更**，
按 watchdog §1.11「不扩大范围」不自行开工，已写入 `build_qa/watchdog/pending.md` 等用户决策。

---

## 1. 总览速览

| 章 | 桶 | 条数 | P0 | P1 | P2 | 工作量合计 |
|---|---|---|---|---|---|---|
| 2 | 缺陷与可靠性 | 3 | 3 | — | — | M |
| 3 | 架构：跨进程活动会话登记 | 1 | 1 | — | — | M |
| 4 | 战略：小组件 Glance 迁移 | 1 | — | 1 | — | XL |
| 5 | 功能加深 | 6 | — | 6 | — | L |
| 6 | 新功能补齐 | 6 | — | — | 6 | L |
| 7 | 保持优势与防退化 | 5 | — | 持续 | — | S（零成本） |
| 8 | 红线 | 5 | — | — | — | S |
| 9 | 观察项 | 2 | — | — | — | S |

**建议排期顺序**：第 2 章（3 条小而急，含 1 个真 bug）→ 第 3 章（XL-001/002/003/004/014 建议合为一批「系统事件响应可靠性」批次落地）→ 第 4 章决策点 → 其余。

---

### 1.1 核对轮次记录

本文档经过一轮自查核对，以下断言在核对中被**修正或撤回**（保留修正痕迹以免后续重犯）：

| # | 初版断言 | 核对结论 |
|---|---|---|
| 1 | androidApp 有 7 个单测 | ❌ 实为 **8 个文件 / 65 个 `@Test`**（漏计 `WidgetCourseSelectionTest`） |
| 2 | shared `androidHostTest` 10 例 | ❌ 实为 **15 个文件 / 140 个 `@Test`**（初版采信了 `REVIEW.md` 的历史快照数字） |
| 3 | Glance 消除 `widget-display-optimization.md §9` 中「至少 9 条」不可行项 | ❌ **收回**。逐条核对 13 项后：**7 条完全消除**（1/2/4/5/6/9/11）、2 条绕过或缓解（10/13）、**4 条不解决**（3/7/8/12）。其中第 8 条「秒级整体重绘」Glance 完全不解决（渲染机制同源） |
| 4 | 我方「缺统一事件编排层，4 条链路互不感知」 | ❌ **高估，已收回**。`NotificationScheduler.reschedule()` 已是合格的单点重排入口（单次读库 / 三套共用 `effectiveCourses` / 读库失败保留旧闹钟 / 三策略异常隔离）。XL-004 已收窄为「跨进程活动会话登记」，工作量由 L 降为 M |
| 5 | `TimeChangeReceiver` 已覆盖 3 个 action | ⚠️ **加强**。事实成立，但漏了关键限定：由 `MyApplication` 运行时注册、**不在 manifest**，进程死亡时收不到；且其 KDoc 自称「静态注册到 manifest」与实现不符 |
| 6 | 星链 `WidgetThemeConfig` 有 25 个字段 | ❌ 实为 **22 个**（初版目测字段列表时重复计数） |
| 7 | 星链 `WidgetData` 有 7 个 `show*` 显示开关 | ❌ 实为 **6 个**（`showCourseTag` 被重复计入） |

> **本节是历史记录，不改写**：上表里的「8 个文件 / 65 例」「15 个文件 / 140 例」都是**当时那次核对**的实测值。
> 截止 2026-10-03（v4.67.29）的当前口径是 **shared 19 文件 / 162 例 + androidApp 8 文件 / 67 例 = 229 例 / 0 失败**，见 §0.6 与 §7 XL-032。

---

## 2. P0 · 缺陷与可靠性

> **读法（2026-10-03 watchdog 第 40 轮补注）**：本节每条 XL 的「现状（已核实）」都是 **2026-10-02 的审计快照**（问题陈述），
> 不是「当前状态」；条目的落地状态一律以 §0.5 状态表为准。其中 **XL-003 / XL-010 / XL-014** 的审计块里含有可被静态证伪的
> **条数与行号**，它们随实现演进已漂移，故在这三条块内各加了 `> **状态**` 行给出 2026-10-03 的实测口径；
> 其余条目的「现状」基本为定性描述，个别锚点漂移处就地标注「现 `:NN`」（2026-10-03 逐行复核）。

### XL-001 · 重叠会话无守卫，A 课结束会恢复 B 课期间的铃声

| 字段 | 内容 |
|---|---|
| **优先级** | P0 |
| **工作量** | M |
| **类型** | 缺陷（可复现的行为错误） |

**现状（已核实）**

- `androidApp/.../receiver/AutoModeAlarmReceiver.kt:48` 在 `ACTION_AUTO_MODE_END` 分支**无条件**调用
  `AutoModeController.toggle(ctx, enable = false, modeType)`
- 全模块 grep `overlap|activeSession|currentSessions|ongoingSessions|冲突|重叠` → **仅命中 1 条无关注释**，无任何守卫
- 自动勿扰采用**每会话独立闹钟槽位**（`AlarmScheduler.kt:27` 注释明确记载旧方案 `50001/50002` 跨课共用已废弃，现按课表分逻辑独立分配，`autoModeSlotLimit = AUTO_MODE_SLOT_LIMIT`；**2026-10-03 复核：该注释现位于 `:42`**）

**风险**

课程时间重叠时（如 11:00–12:00 与 11:30–13:00，或课程与早八闹钟重叠），先结束的那条会话会执行 `toggle(enable=false)` 恢复铃声 —— 而另一条会话仍在静音区间内，导致**用户在课堂上手机突然响铃**。这是课表类 App 最不可接受的体验事故之一。

**目标**

结束会话前检查「是否仍有其他会话处于进行中」，有则跳过恢复，并留下可读日志。

**改动范围**

- `androidApp/.../receiver/AutoModeAlarmReceiver.kt` —— END 分支前置查询
- `androidApp/.../control/AutoModeScheduler.kt` —— 暴露「当前活动会话」查询（会话区间来自已排程的 AlarmCodeBook 槽位 + 课程表）
- 建议新增 `shared/.../data/logic/ActiveSessionProbe.kt`（与 `AutoModeStateProbe` 同层，`commonMain` 可单测）—— **未按此落地**：XL-004 最终改设计，改用既有 `NotificationScheduler.reschedule()` + `shouldModeBeOn` 重算，不新建任何探测文件（见 §0.5 的 XL-004 状态行）；2026-10-03 全仓复查确认该路径不存在

**依赖** 建议等 XL-004（活动会话登记）就位后一并实现 —— 届时本项就是一次查表，工作量降到 S。若要抢在 XL-004 之前先止血，可在 `AutoModeAlarmReceiver.kt:48` 直接比较「当前时间是否落在其他已排程会话区间内」，但那是临时方案，XL-004 落地后应替换。

**验收标准**

1. 新增单测：构造 11:00–12:00 / 11:30–13:00 两条会话，触发前者 END 后断言 `AudioManager.ringerMode` **仍为 SILENT**
2. 构造 08:00–09:00 / 10:00–11:00 两条不重叠会话，触发前者 END 后断言恢复为进入前模式
3. 真机验证：手动建两条重叠课程，静音模式下等第一节结束，确认第二节期间不响铃
4. 日志可见跳过原因（对齐 `AppLog` 规范，不用 `println`）

**对标依据**

星链明确实现：`仍有进行中事件占用降噪，跳过恢复`。

**与既有文档的关系**

不重复 `REVIEW.md` 批14（`RINGER_MODE_NORMAL` 覆盖用户铃声偏好 —— 已修为「记录并还原进入前模式」）。本项是**跨会话并发**问题，批14 解决的是**单会话内的模式还原正确性**，两者正交。

---

### XL-002 · 自动降噪无读回校验，且不记录进入前的勿扰状态

| 字段 | 内容 |
|---|---|
| **优先级** | P0 |
| **工作量** | S |
| **类型** | 可靠性缺口 |

**现状（已核实）**

- `AutoModeController.kt:62-63` —— `toggleDnd` 用 `runCatching { setInterruptionFilter(...); true }` 乐观返回；**从不回读 `currentInterruptionFilter` 确认是否真生效**
- `AutoModeController.kt:97` —— `toggleSilent` 成功路径同样直接 `return true`，不回读 `ringerMode`
- `AutoModeStateProbe.kt:24-33` —— 只探测「当前是否已处于目标模式」，**不记录「进入前是什么模式」**（`toggleSilent` 用 SharedPreferences `ringer_mode_before` 单独记了，但 DND 分支完全没有对应机制）

**风险**

1. 部分 OEM 对 `setInterruptionFilter` 会静默忽略（尤其未获「勿扰访问权限」但有 `ACCESS_NOTIFICATION_POLICY` 声明时的边缘态）。返回 `true` 会让上层以为降噪已生效，**实际没有**，用户上课仍响铃且无从排查。
2. DND 分支结束时无条件写 `INTERRUPTION_FILTER_ALL`。若用户在上课期间**自己**打开了勿扰，下课时被我们强制关掉 —— 属越权修改用户设置。

**目标**

1. 切换后回读校验：不一致则记 `AppLog.w` 并把结果返回给上层
2. DND 分支补「进入前 filter」记录与还原，语义对齐 `toggleSilent` 已有的 `ringer_mode_before`

**改动范围**

- `androidApp/.../control/AutoModeController.kt` —— `toggleDnd` 补前后读回 + prefs 记录键
- `androidApp/.../control/AutoModeStateProbe.kt` —— 补「与目标是否一致」的对拍方法
- `androidApp/.../PermissionNoticeNotifier.kt` —— 校验失败时的用户提示（沿用既有 `NOTICE_ID_DND` 通道）

**依赖** 无

**验收标准**

1. 单测：mock `setInterruptionFilter` 无效（回读值 ≠ 请求值）→ `toggle` 返回 `false` 且写日志
2. 单测：DND 打开前 `currentInterruptionFilter = ALL` → 关闭后回到 `ALL`；打开前为 `PRIORITY` → 关闭后回到 `PRIORITY`
3. 权限缺失路径仍走既有 `PermissionNoticeNotifier.notifyDndMissing`，不回归

**对标依据**

星链有完整读回校验与「记录—还原」语义：`setInterruptionFilter 已调用但读回为 …`、`已恢复 Android 17+ 自动静音（勿扰过滤复位为 …）`、`已处于静音模式，不接管`、`系统已处于勿扰状态，不接管`。

**与既有文档的关系**

**与 `REVIEW.md` 第 12 轮待批项之一「DND 只认 PRIORITY 的完整『记录还原』版」是同一问题。** 本清单给出证据定位与验收标准，实施时应在该条下合并进行，避免开两条并行改动。

---

### XL-003 · 未监听精确闹钟权限变更，权限被撤销后闹钟静默失灵

| 字段 | 内容 |
|---|---|
| **优先级** | P0 |
| **工作量** | M |
| **类型** | 可靠性缺口 |

**现状（已核实）**

- `androidApp/src/main/AndroidManifest.xml` —— grep `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` **命中 0 次**；manifest 共声明 **15 个 receiver**，逐一核对后无任何一个监听该 action
- `AlarmScheduler.kt:77` 与 `DynamicIslandManager.kt:175` 均**排期时**检查 `canScheduleExactAlarms`（这点是对的；**2026-10-03 复核：`AlarmScheduler.kt` 侧现为 `:92`**）
- 但全仓无任何 `BroadcastReceiver` 监听权限状态变更

**同类失效模式已在仓库内出现（本轮核对新发现，佐证本项必要性）**

`TimeChangeReceiver` 由 `MyApplication` **运行时注册**（`registerTimeChangeWatcher(context)`），**不在 manifest 中**（已逐条核对 15 个 receiver 名称确认）。后果：

- 进程存活时：能收到 `ACTION_TIMEZONE_CHANGED` / `ACTION_TIME_CHANGED` / `ACTION_DATE_CHANGED`，调 `NotificationScheduler.reschedule()` —— 工作正常
- **进程死亡时收不到**，已排程闹钟不重排。用户改了时区/系统时间后课表整体偏移，且无任何提示

更需注意的是：**`TimeChangeReceiver` 的类 KDoc 自称「该广播由 `TimeChangeReceiver` 静态注册到 manifest」—— 这句注释与实际实现不符**，属过时/错误注释，会误导维护者以为进程死亡时也能收到。这与 `REVIEW.md` 第 6 轮记录的多条「名实不符」同源。

> **状态（2026-10-03 watchdog 第 40 轮实测）**：✅ **已修**（见 §0.5 XL-003 / XL-014）。上方三段均为 2026-10-02 审计快照，其中三项数字都已变化：
> `AndroidManifest.xml` 的 receiver 由 **15 → 17**；`SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` 的监听者是新增的
> `.service.notification.receiver.AlarmPermissionReceiver`（`AndroidManifest.xml:127-134`，静态注册、`exported=false`）；
> `TimeChangeReceiver` 已改为 **manifest 静态注册**（`AndroidManifest.xml:141`，intent-filter `:144-149`），`MyApplication` 不再运行时注册，KDoc 亦已改正。

这恰好说明本项**必须 manifest 注册**：权限变更与时区变更都要求「无论进程是否存活都能响应」，运行时注册的 receiver 天然做不到。

**风险**

Android 12+ / 14+ 用户可在系统设置里随时撤销「闹钟和提醒」权限。此时：

1. 系统**静默取消**全部已注册的精确闹钟，App 收不到任何回调
2. 课程提醒、自动勿扰、灵动岛、早八闹钟**同时全部失灵**
3. 用户侧表现是「提醒莫名其妙不响了」，且**无从排查** —— 这是提醒类 App 最典型的差评来源

**目标**

监听权限变更广播，在权限被撤销时：(a) 降级重排为不精确闹钟或 `setAndAllowWhileIdle`（保证仍会触发，只是可能延迟）；(b) 通过既有 `PermissionNoticeNotifier` 提示用户重新授权。

**改动范围**

- 新增 `androidApp/.../receiver/AlarmPermissionReceiver.kt`
- `androidApp/src/main/AndroidManifest.xml` —— 注册 action（Receiver 须 `exported=false`，同 `ReminderAlarmReceiver` 现有做法）
- `androidApp/.../notification/alarm/AlarmScheduler.kt` —— 抽出「按当前权限重排」入口供 Receiver 调用
- 复用既有 `PermissionNoticeNotifier`（`AlarmScheduler.kt:44` 已有 `exactAlarmNoticeSentThisRound` 去重，防 261 次 `resolveActivity` 重复提示 —— 沿用该机制；**2026-10-03 复核：声明现为 `:65`，使用在 `:92-96`**）

**依赖** 无

**验收标准**

1. 真机：授予精确闹钟权限 → 触发权限变更广播 → 断言重排后闹钟数量不变
2. 真机：撤销权限 → 断言所有闹钟降级重排且 `canScheduleExactAlarms=false` 时不再走 `setExactAndAllowWhileIdle`
3. 单测：同一轮多次权限变更只提示一次（复用 `exactAlarmNoticeSentThisRound` 语义）
4. 撤销权限后课程提醒**仍会触发**（可能延迟），不静默丢失

**对标依据**

星链有 `AlarmPermissionReceiver` + `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`，日志含 `闹钟权限状态变更: canScheduleExactAlarms=`、`精确闹钟权限被拒绝，降级为不精确闹钟:`、`无精确闹钟权限，使用不精确闹钟`、`精确闹钟不可用，小组件降级 inexact:`。

**与既有文档的关系**

不重复 `REVIEW.md` 批14 的「精确闹钟提示收敛」与第 12 轮对「261 次 `resolveActivity` IPC 浪费」的复核 —— 那两项处理的是**提示频率**与**成本**；本项处理的是**权限被撤销后无任何响应**这一未覆盖路径。

---

## 3. P0 · 架构：跨进程活动会话登记

### XL-004 · 缺跨进程的「活动会话」状态，重叠保护与降级续链因此无法正确实现

| 字段 | 内容 |
|---|---|
| **优先级** | P0（架构） |
| **工作量** | M（**由 L 下调** —— 已有统一编排入口，不需要新建编排层） |
| **类型** | 能力补全 |
| **单独立项** | 是（用户已确认单独排期） |

> ⚠️ **本条为初版高估后的修正结论。** 初版判断是「缺统一事件编排层，同一门课被拆到 4 条互不感知的链路，各排各的、各消各的」。复核 `NotificationScheduler.kt` 后该判断**不成立**，详见下方「事实澄清」。

**事实澄清：统一编排入口已经存在，且设计良好**

`androidApp/.../schedule/NotificationScheduler.kt` 已是一个合格的单点重排入口：

| 行 | 事实 |
|---|---|
| `:63-69` | 持有 4 套子调度器：`alarms`（`AlarmScheduler`）/ `courseReminders` / `autoMode` / `morningAlarms` |
| `:111` | 考试倒计时**先于**课程读库执行 —— 不依赖课程库，课程库读失败时仍能更新 |
| `:115-133` | **单次读库**供三套调度共用；读库失败时**保留既有闹钟不清空**（注释明确说明这是相对旧实现「先清后读」的改进） |
| `:136` | 三套策略共用同一份 `ReminderEngine.effectiveCourses` 结果 |
| `:138` | `alarms.cancelAll()` 后统一重建 |
| `:145-149` | `guard(tag) {}` 三策略异常隔离 + `failedStrategies` 计数（`REVIEW.md` 批12「三策略异常隔离」已落地） |

`TimeChangeReceiver.onReceive` 亦调用同一入口（`NotificationScheduler(...).reschedule()`）。**因此「重排一致性无保障」不成立。**

**真实缺口（复核后收窄为 3 条）**

1. **无跨进程持久化的「活动会话」状态** —— `reschedule()` 的 `Summary` 只在内存返回、不落盘。进程重启后无从得知「上一轮排了哪些会话、当前是否有会话处于进行中」。**这正是 XL-001 重叠守卫无法实现的根因**，也正是星链用 `EventAlarmStore.Record{..., quietMode}` 落盘要解决的问题。
2. **`cancelAll()` 是全量重建，取消粒度粗** —— 无法只取消「已失效的那一条」。结构性后果已经发生过一次：`REVIEW.md` 批12 实证「`AlarmScheduler` 只按 START action 取消，END 闹钟 PendingIntent 残留」（`filterEquals` 只比 action）。虽然那处已修，但只要还是全量重建 + 各子系统自管取消，同类问题会复发。
3. **`Summary` 不落盘 → 崩溃后无法自检** —— 若 `reschedule()` 中途崩溃（已 `cancelAll()` 但未建完），下次只能靠 Worker 退避重试或下次前台同步补上，App 自身无「上次排程是否完整」的判据。

**目标**

在既有 `reschedule()` 之上补一层**轻量落盘的活动会话登记**，不做事件编排层重建：

```
新增概念（对齐星链 EventAlarmStore.Record 的最小子集）
  eventKey, startTimeMs, endTimeMs, quietMode, armedBy
```

- 供 XL-001 查询「当前活动会话集合」（重叠守卫）
- 供降级续链查询「上轮是否已开启静音、本轮是否该交还」（闭环 `REVIEW.md` 第12轮待批的「早八降级续链」）

**改动范围**

- 新增 `androidApp/.../notification/registry/ActiveSessionRegistry.kt`（SharedPreferences 或 DataStore；数据量小，不需要 Room 迁移）—— **未按此落地**：XL-004 改设计后**不新建登记表**（见 §0.5 的 XL-004 状态行）；2026-10-03 全仓复查确认该路径不存在
- `androidApp/.../schedule/NotificationScheduler.kt` —— `reschedule()` 内写入/清理登记，返回 `Summary` 时带上活动会话数
- `androidApp/.../receiver/AutoModeAlarmReceiver.kt` —— END 分支改为查登记（XL-001 的实现落点）
- `androidApp/.../receiver/ReminderAlarmReceiver.kt:115` —— `ACTION_MORNING_ALARM_FALLBACK` 接入登记（早八降级续链）

**依赖** 无前置 —— 可独立开工。**是 XL-001 的前置**（XL-001 的重叠守卫需要查本登记）。建议与 XL-002 同批落地：三者都在 `androidApp/.../notification/` 与 `receiver/` 内，改动范围高度重叠。

**验收标准**

1. 单测：登记表能正确回答「此刻有 N 条会话处于进行中」
2. 单测：改课表 → `reschedule()` → 登记表与实际排程**完全一致**（无幽灵、无漏）
3. 单测：进程重启后登记表仍可读（验证落盘而非仅内存）
4. 单测：`reschedule()` 中途抛异常时，登记表保持上一轮内容不被清空（与「读库失败保留旧闹钟」语义一致）
5. 回归：`TimeChangeReceiver` 触发的重排后登记表一致

**对标依据**

星链把「一个事件 = 三个时间锚点 + 一个降噪诉求」作为一个 `Record` 一次落盘（`EventAlarmStore$Record`），STARTED 负责「发通知 + 开静音 + 弹岛」、ENDED 负责「恢复」，职责正交且**可跨进程重放**。我们要补的是同一件事的最小必要部分。

**与既有文档的关系**

- **闭环** `REVIEW.md` 第 12 轮待批项「早八降级续链」（缺跨链路信息，本条补上）
- **根因层**覆盖批12 已修的「END 闹钟 PendingIntent 残留」（本条修的是结构，不是那一处代码）
- **不重复** `REVIEW.md` 批14「RINGER_MODE_NORMAL 覆盖用户铃声偏好」（单会话内模式还原正确性，与跨会话并发正交）
- **不重复** 现有 `shared/.../notification/plan/` 的 `ReminderEngine` / `ReminderPlan` / `MorningAlarmPlan` —— 那些是纯选择逻辑（哪些课该提醒、提前几分钟），已设计良好且有单测；本条在其**之下**补「运行时状态登记」，不改动选择逻辑

---

## 4. 战略项 · 小组件 Glance/Compose 迁移

### XL-005 · 8 个 RemoteViews 小组件迁移到 Glance/Compose

| 字段 | 内容 |
|---|---|
| **优先级** | 战略（独立立项，不与 P0 同批） |
| **工作量** | XL（>5 天，建议拆 4 阶段） |

#### 4.1 现状

8 个小组件全部基于 `RemoteViews` + 手写 XML：

| 规格 | 目录 | Provider / Renderer |
|---|---|---|
| 超小 | `widget/tiny/` | `TinyNativeProvider` / `TinyNativeRenderer` |
| 紧凑 | `widget/compact/` | `CompactNativeProvider` / `CompactNativeRenderer` |
| 连续两日 | `widget/double_days/` | `DoubleDaysNativeProvider` / `DoubleDaysNativeRenderer` |
| 纵向列表 | `widget/list_vertical/` | `ListVerticalNativeProvider` / `ListVerticalNativeRenderer` |
| 日程清单 | `widget/agenda_list/` | `AgendaListNativeProvider` / `AgendaListNativeRenderer` |
| 下一节课 | `widget/next_course/` | `NextCourseNativeProvider` / `NextCourseNativeRenderer` |
| 一周课程 | `widget/week_courses/` | `WeekCoursesNativeProvider` / `WeekCoursesNativeRenderer` |
| 考试倒计时 | `widget/exam_countdown/` | `ExamCountdownNativeProvider` / `ExamCountdownNativeRenderer` |

外加 **16 个布局 XML**（`res/layout/widget_*.xml`）。渲染入口形如 `WeekCoursesNativeRenderer.render(context, space: WidgetSpaceClass = WidgetSpaceClass.S): RemoteViews`。

#### 4.2 核心论证：`docs/widget-display-optimization.md §9` 的 13 条「RemoteViews 明确不可行项」分类

> 本节数字经逐条核对 §9 原文（13 个编号条目）后重列。**初版曾把「9 条」写宽了** —— 经复核，Glance 并不能解决其中全部，详见下表分类。

**A 类 · Glance 完全消除（7 条）**

| §9 | RemoteViews 限制 | Glance/Compose 对应能力 |
|---|---|---|
| 1 | 自定义字体按主题切换（需复制整套布局） | `@Composable` 内直接换 `FontFamily` |
| 2 | 任何动画/过渡/呼吸/庆祝动效 | 完整 Compose 动画 |
| 4 | 圆角裁剪子元素（只能靠 shape drawable） | `Modifier.clip()` |
| 5 | 文字自动缩放（无 `autoSizeTextType`） | `TextStyle` + 自适应布局 |
| 6 | 按主题选择布局/资源（**Android 无此资源限定符**） | `when (preset)` 一份代码 |
| 9 | 每主题的色条几何（需复制 item 布局） | 一份 `Modifier` |
| 11 | 渐变背景按主题（多份 drawable 切换） | `Brush` 直写 |

**第 6 条尤其关键** —— 它是 `widget-display-optimization.md` 整份方案的**技术枢纽**（§1.2 已认定「主题联动必须走运行时 setter，不能靠资源限定符」）。Glance 直接消除了这个约束。

**B 类 · 绕过或缓解（2 条）**

| §9 | 状况 |
|---|---|
| 10 | `setInt(viewId, "setBackgroundResource", …)` 反射路径 —— Glance 下 Compose 直接赋值，该 workaround 不再需要（**绕过**） |
| 13 | 「卡片贴满边界 + 主题圆角」不可兼得 —— 系统边界裁剪仍存在，但 `W-cardInset` 这个 token 可由 `Modifier.padding` 更自然表达（**缓解，非消除**） |

**C 类 · Glance 不解决（4 条）—— 初版误列，此处更正**

| §9 | 为什么不解决 |
|---|---|
| 3 | 自绘 View 仍不能插入 —— Glance 最终也渲染为 RemoteViews，自绘仍须渲染成 `Bitmap` 经 `setImageViewBitmap` |
| 7 | Konami 式按键序列仍收不到；`actionStartActivity` / `actionRunCallback`（后者需 API 31+）只能覆盖「点击 / 长按」这类单次交互 |
| **8** | **秒级整体重绘不解决** —— Glance 的刷新机制与 RemoteViews 同源，仍受系统更新节流约束；`Chronometer` 依旧是唯一跨进程例外 |
| 12 | 动态取色属产品决策而非技术限制（且与「三套锁定主题」方向冲突） |

**净结论**：13 条中 **7 条完全消除 + 2 条绕过/缓解 + 4 条不解决**。收益仍然可观（尤其第 6 条解除了整个方案的技术枢纽约束），但**不应把 Glance 包装成「RemoteViews 全部限制的解」** —— 秒级重绘与自绘 View 这两类限制会原样保留。

#### 4.3 阶段拆分（每阶段独立可发布、可回滚）

| 阶段 | 范围 | 工作量 | 验收 |
|---|---|---|---|
| **S1 试点** | `tiny`（最简单，1 个课程点）单独走 Glance，与现有 7 个并存，通过 `minResize`/实验开关灰度 | M | tiny 视觉与现状逐像素对齐；Room/WorkManager 数据链不变 |
| **S2 低风险批** | `compact` + `double_days` | L | 两规格对比度门禁全绿 |
| **S3 列表批** | `list_vertical` + `agenda_list`（最复杂，行高计算是历史痛点） | L | `WidgetListCapacityTest` 等价物在 Compose 侧重建；无溢出 |
| **S4 收口** | `next_course` + `week_courses` + `exam_countdown`，删除 16 个 XML | L | 8 规格全部 Glance；`res/layout/widget_*` 清空 |

**建议先只做 S1**，用真实结果决定是否继续 —— 这是本项风险最高、收益待验证的部分。

#### 4.4 ⚠️ 排期决策点（必须在 S1 之前决定）

`docs/widget-display-optimization.md §7` 把「widget token 组 + 三主题 6 份显式赋值 + 快照下发」列为 **P1 / 工作量=大**。该方案是**为 RemoteViews 量身设计**的，依赖两件在 Glance 下不再需要的事：

1. `§6.3` 的 12 个 token 里有 `cardInset`（卡片四周留 2–4dp 外边距），目的是**对冲 Android 12+ 系统边界圆角裁剪** —— Glance/Compose 下圆角由 `Modifier.clip` 控制，不需要这个 workaround
2. 切换主题靠「shape drawable 资源切换」（`§9` 第 10 条明确说明这是「本方案能落地主题圆角的关键手法」）—— Glance 下直接改颜色值

**因此存在真实的重复劳动风险**：若先按原方案做完 token 改造，再做 Glance 迁移，token 层要重写一遍。

**建议决策**：
- 路径 A（推荐）：**先做 Glance S1 试点**，用试点结果决定 token 层按 Compose 实现，`widget-display-optimization.md §7` 的 P1 顺延并改写
- 路径 B：若确定不做 Glance，则按原 §7 排期，本清单 XL-005 关闭

#### 4.5 改动范围

- `androidApp/build.gradle.kts` —— 引入 `androidx.glance:glance-appwidget` + `glance-material3`
- 新增 `androidApp/.../widget/glance/` —— 与现有 `widget/` 并存的第二套实现（**S4 前不删除现有代码**）
- 复用既有数据链：`shared/.../data/sync/WidgetDataSynchronizer.kt` + `WidgetRepository.kt` + `WidgetCourse` Room 表 —— **不动**，这是迁移可行的关键前提
- 主题 token 从 `ScheduleGridStyle`（`shared/.../data/model/ScheduleGridStyle.kt`）解析下发，落在 `WidgetDataSynchronizer` 产出的快照里（现有机制已支持下发字段）
- S4：`androidApp/src/main/res/layout/widget_*.xml` 全量删除

#### 4.6 风险与回滚

| 风险 | 缓解 | 回滚 |
|---|---|---|
| Glance 渲染性能低于 RemoteViews（Compose 运行时开销） | S1 阶段专门测：冷启动首帧耗时、内存峰值 | S1 独立开关，关闭即回退 |
| 部分第三方 Launcher 兼容问题 | S1 在 ≥2 个 Launcher 验证（含 1 个第三方） | 同上 |
| 8 规格行为漂移（与 RemoteViews 版表现不一致） | 每阶段保留旧实现做对照截图，逐规格逐状态比对 | 每阶段独立提交，可单独回退该规格 |
| 与 XL-004 编排层改造撞车（都要动 `WidgetUpdateHelper`） | **XL-005 排在 XL-004 之后**，或两者错开文件范围 | — |

**关键优势**：我们的数据层（`WidgetDataSynchronizer` + Room + WorkManager）**与渲染技术完全解耦**。这是 RemoteViews 时代积累的架构红利 —— 迁移只需换渲染层，不动数据链。**这正是本次清单把 XL-005 判为「可行且收益高」的前提。**

#### 4.7 验收标准

1. S1：tiny 规格在 2×1 与可拉伸两态下，与现有 RemoteViews 版截图逐像素对齐（误差 ≤2dp）
2. 每阶段：8 个单测（`WidgetListCapacityTest` `WidgetCoursePaletteTest` `WidgetBubbleContrastTest` `WidgetNightModeTest` `WidgetTextScaleTest` 等）在新实现下等价重建并全绿
3. S4：`res/layout/widget_*.xml` 数量 = 0；`grep -rn "RemoteViews" androidApp/src/main/kotlin/com/shhangkeschedule/widget/` 无业务命中
4. 全阶段：性能不回归（首帧耗时、内存峰值不劣于 RemoteViews 基线）
5. 全阶段：`scripts/check_widget_contrast.py` 门禁在新实现下仍 0 违规（脚本需适配 Compose 侧的 token 解析）

---

## 5. P1 · 功能加深

### XL-010 · 日历写回：全量删除重建 → 增量更新 + 写入回读校验

| 字段 | 内容 |
|---|---|
| **优先级 / 工作量** | P1 / L |

**现状（已核实）** — `shared/src/androidMain/.../CalendarAccountManager.android.kt`

| 行 | 现状 | 评价 |
|---|---|---|
| `:64` | `CALLER_IS_SYNCADAPTER=true` | ✅ 正确 |
| `:93-95` | `resolver.delete(EVENTS, "CALENDAR_ID = ?")` | ⚠️ **全量删除** |
| `:122-136` | `applyBatch` 批量 insert Events + Reminders | ✅ 事务性正确 |
| — | 无 `EVENT_ID` ↔ `appId` 映射 | ❌ 无增量能力 |
| — | 无写入后回读校验 | ❌ 无失败可感知 |

**风险**：全量删除重建意味着每次同步都产生 261+ 次事件 ID 变更，系统日历 App 会视为「全部删除再全部新增」→ 用户的历史通知/提醒被连带清除；且部分 OEM 日历对批量删除+新增有事务超时风险，失败时无回滚无提示。

> **状态（2026-10-03 watchdog 第 40 轮实测）**：⚠️ **已修**（见 §0.5 XL-010）。上表三处行号均为审计快照，当前
> `shared/src/androidMain/kotlin/com/shangkeschedule/tool/CalendarAccountManager.android.kt` 中：
> `CALLER_IS_SYNCADAPTER` 在 `:86`（原记 `:64`）、删除路径 `:288-291`（原记 `:93-95` 的 `resolver.delete(EVENTS, …)` 全量删除已不存在）、
> `applyBatch` 在 `:333` / `:414`（原记 `:122-136`）。全量删除重建已改为按**开始时刻**做增删改差分 + 写入后回读条数校验。

**目标**（对齐星链 `SystemCalendarSync`）
1. 建立双向 ID 映射（星链字段名：`IncomingEvent.appId` ↔ `DeviceEvent.deviceEventId`）
2. 增量 diff：新增/更新/删除三类操作分开下发
3. 写入后**回读校验计数**（星链字段：`writtenCount` vs `verifiedCount`）
4. 失败给可操作提示（星链字段：`usedFallback` / `userHint`）

**改动范围** — `CalendarAccountManager.android.kt`（`expect/actual` 签名扩展 `shared/.../tool/CalendarAccountManager.kt`）、`CourseConversionViewModel.kt` 调用侧

**依赖** 无

**验收标准**
1. ~~单测：课表改 1 门课 → 系统日历 `ContentResolver` 调用序列为 `update` 而非 `delete(all)+insert(all)`~~ → **不可行**：链路依赖真机 `CalendarProvider`，仓库无 androidTest 源集。**改为真机驱动验证**，见 §1.2
2. 写入后回读计数不一致 → 返回失败并给出提示文案（复用既有 i18n 资源机制，勿裸中文） ✅
3. 真机：连续同步 3 次，日历 App 中无重复事件、无丢失事件 ✅（连续两次均为 `新增 0 / 更新 0 / 删除 0`）
4. `CALLER_IS_SYNCADAPTER=true` 与 `applyBatch` 事务性不回归 ✅

**实施结果（v4.66.5，2026-10-03）** — 目标 1 改为**以开始时刻 `dtstart` 为匹配键**，
不建 appId ↔ eventId 映射表，理由与真机证据见 §1.2。验收标准 1 无法用单测覆盖，
已如实改为真机验证并逐条触发 insert / 零写入 / update / delete 四条路径。

**对标依据** — 星链 `SystemCalendarSync` 字段组：`CalendarRow`（9 字段含 `writable`/`syncEvents`/`isPrimary`）、`DeviceEvent`（`appEventId`↔`deviceEventId`）、`ReplaceResult`（`writtenCount`/`verifiedCount`/`usedFallback`/`userHint`）；日志含 `已写入「星链课表」。若日历里看不到，请打开系统日历 → 日历列表，勾选「星链课表」。`

---

### XL-011 · 小组件主题契约：从 4 规格扩到 8 规格

| 字段 | 内容 |
|---|---|
| **优先级 / 工作量** | P1 / M |

**现状**：主题契约**已设计完毕**，位于 `docs/widget-display-optimization.md §6.3` —— 12 个 widget token × 6 份取值（3 主题 × 深浅），含 AA 校正值与实测对比度附录 A（可复算）。但该文档 §0 与 §7 的排期均基于 **4 个规格**；v4.66.0 已把小组件扩到 **8 个规格**，文档与 token 表均未覆盖新增的 4 个（`list_vertical` / `agenda_list` / `next_course` / `exam_countdown`）。

**目标**：
1. 8 规格 × 3 主题 × 深浅 = 48 组合的 token 取值补全
2. 新增 4 规格的 `previewLayout` / `android:description`（桌面选择器文案，现有 4 规格的工程名如「超小课程2x1」对用户不友好 —— 星链已用「课表 · 一周」「日程 · 考试」这类文案）
3. 同步更新 `widget-display-optimization.md` 的规格数量与排期

**改动范围** — `docs/widget-display-optimization.md`（更新）、`shared/.../data/model/ScheduleGridStyle.kt`、`shared/.../data/sync/WidgetDataSynchronizer.kt`（快照字段扩展）、4 个新增 Renderer、`androidApp/src/main/res/xml/shangkeschedule_*_widget.xml`

**依赖** ⚠️ **与 XL-005 的 4.4 决策点强耦合** —— 若走 Glance 路径，本项应并入 S2–S4 一起做，不要单独投入

**验收标准**
1. `scripts/check_widget_contrast.py` 覆盖 48 组合，0 FAIL
2. 8 规格在书卷/通透/柔绘 × 深浅下各截 1 张（共 24 张），卡底/圆角/文字色与 §6.3 表逐项一致
3. 桌面选择器 8 个组件的 `android:description` 均为用户可理解的用途说明

**对标依据** — 星链 `WidgetThemeConfig` 22 个字段（壁纸 type/path/color/opacity/darkness/blurLevel + **六个区域的「底/内容」双重透明度** + fontScale + cornerRadius + cellHeight + contentAlignment + `enableTextAutoContrast` + `hideFinishedCourses` + `courseColors`）；8 个 provider XML 均声明 `previewLayout`、`minResize*`、`maxResize*`（最大到 600×600）、`targetCellWidth/Height`

**与既有文档的关系** — 本项**不重新设计** token，沿用 `widget-display-optimization.md §6.3`；只做规格扩展 + 文档同步。

---

### XL-012 · 通知形态升级：vivo 原子通知 / AOSP Live Updates

| 字段 | 内容 |
|---|---|
| **优先级 / 工作量** | P1 / M |

**现状**：已具备小米超级岛（`DynamicIslandManager` / `DynamicIslandService` / `DynamicIslandAlarmReceiver`）。**未见** vivo 原子通知与 AOSP 实况通知（Android 17）。

> **状态（2026-10-03 watchdog 第 40 轮实测）**：**AOSP 实况通知（Live Updates / 推广常驻）已实现** —— 就在 v4.66.0（`70775649`）这个批次里随 XL-012 一起进来的，
> 四处投递点声明 `setRequestPromotedOngoing(true)` + `setShortCriticalText(...)`：`CourseReminderNotifier.kt:131-135`、
> `NextClassNotifier.kt:169-173`（经 `LiveUpdateSupport.supportsLiveUpdate()` 门控 + `runCatching` 降级为普通常驻）、
> `CourseAlarmReceiver.kt:312-316`、`DynamicIslandService.kt:501-503`；能力探测 `LiveUpdateSupport.kt:30-36`
> （`SDK_INT >= BAKLAVA(36)` 且 `canPostPromotedNotifications()`）；渠道 `LIVE_UPDATE`（`NotificationChannels.kt:77,184-192`）与
> 权限 `POST_PROMOTED_NOTIFICATIONS`（`AndroidManifest.xml:13`）均在位；设置页有实况能力卡（`GeneralSettingsCard.kt:239-250`，三态：支持 / 不支持 / 厂商自渲染）。
> 故上句「**未见** AOSP 实况通知」与 §0.5 原记的「compileSdk 36 取不到该符号」**两处均被实测证伪**：
> `gradle/libs.versions.toml:4 android-compileSdk = "37"`，且 `javap` 实测 `D:\Android\SDK\platforms\android-37.0\android.jar` 中存在
> `Notification$Builder.setRequestPromotedOngoing(boolean)`、`NotificationManager.canPostPromotedNotifications()`、`Notification.FLAG_PROMOTED_ONGOING`
> —— 另外实现走的是 `NotificationCompat.Builder`（androidx），本就不依赖平台符号是否存在。
> **仍未做且明确不猜做的**：vivo 原子通知（厂商私有权限 + 无公开协议）。

**目标**：按能力探测逐级升级通知形态，每级失败降级到下一级。

**改动范围** — `androidApp/.../notify/NotificationChannels.kt`（新增 2 个 channel：`vivo_atom_notification_channel`、AOSP Live Updates）、`NotificationScheduler.kt`（形态选择）、vivo METTING 场景能力探测

**依赖** 无

**验收标准**
1. vivo 国行 + 场景开通 → 走原子通知；未开通 → 降级，日志说明原因
2. Android 17 实况通知支持探测成功 → 走 Live Updates；不支持 → 降级常驻倒计时且**不空白**
3. 三种形态的开始/结束两侧都有配对处理（避免岛通知/原子通知残留）

**对标依据** — 星链四级形态：`vivo_atom_notification_channel_v2`、`mi_super_island_channel_v2`、`使用 AOSP 实况通知 (Live Updates)`、`使用标准常驻倒计时通知（兜底）`；日志含 `检测到 vivo 国行设备且 METTING 场景已开通`、`设备不支持岛通知`、`检测实况通知支持失败:`

---

### XL-013 · 厂商适配引导从「诊断面板」升级为「分步引导」

| 字段 | 内容 |
|---|---|
| **优先级 / 工作量** | P1 / S |

**现状**：`shared/.../tool/WidgetTroubleshoot.kt` + `WidgetTroubleshootScreen.kt` 做的是**检测与报告**（是否有权限、是否被省电限制）。

**缺口**：只有「发现问题」，没有「分步引导用户解决」。星链为每个厂商写了分步文案 + 是否有厂商启动管理页可跳（`OemReminderGuide$Copy{title, summary, steps: List<String>, hasStartupPage: Boolean}`），覆盖华为/荣耀、小米/红米、vivo/iQOO、一加/OPPO/realme、三星。

**目标**：在现有排障面板基础上，按厂商给出分步操作指引 + 深链跳转（跳转失败回退应用详情页）。

**改动范围** — `WidgetTroubleshootViewModel.kt`（厂商分派）、新增 `OemGuide.kt`（数据化文案表；**实际落地于 `shared/src/commonMain/kotlin/com/shangkeschedule/tool/OemGuide.kt`**，且最终实现为纯逻辑 `OemGuideResolver` + 9 条四语资源，不含硬编码文案表 —— 见 §0.5 的 XL-013 状态行）、`androidApp` 侧 Intent 跳转

**依赖** 无

**验收标准**
1. 5 个厂商各有分步文案；跳转厂商页失败时回退应用详情页并提示
2. 文案为**数据表**驱动，代码零 `when (manufacturer)` 分支
3. 文案不硬编码在代码里（走既有 i18n 资源机制）

**对标依据** — `OemReminderGuide$Copy` 数据结构 + 文案实例（`一加 / OPPO / realme：锁后台和高耗电不够，还要开自启动和关联启动`、`小米 / 红米：省电策略需设为无限制，并打开自启动`、`电池优化：不限制本应用`、`关联启动：允许（进程被回收后，系统闹钟才能把 App 拉起来）`）

---

### XL-014 · 小组件跨系统事件重算：补 LOCALE，核查等价性

| 字段 | 内容 |
|---|---|
| **优先级 / 工作量** | P1 / M |

**现状（已核实）**：`TimeChangeReceiver` 覆盖 3 个 action（`ACTION_TIMEZONE_CHANGED` / `ACTION_TIME_CHANGED` / `ACTION_DATE_CHANGED`，`REVIEW.md` 批15 已修 `TIMEZONE_CHANGED` 缺失），并有 `HANDLED_ACTIONS` 白名单 + `RECEIVER_NOT_EXPORTED` 运行时注册。星链额外监听 `LOCALE_CHANGED` 与 `APPWIDGET_VISIBLE/HIDDEN/RESTORED`。

**但该 Receiver 由 `MyApplication` 运行时注册，不在 manifest 中**（本轮核对发现，详见 XL-003）。进程死亡时收不到这三个广播，已排程闹钟不重排：用户改了时区或系统时间后课表整体偏移，而 App 无感知。

> **状态（2026-10-03 watchdog 第 40 轮实测）**：✅ **已修**（见 §0.5 XL-014）。上两段为 2026-10-02 审计快照。当前口径：
> `TimeChangeReceiver` 覆盖 **4 个 action** —— `TIMEZONE_CHANGED` / `TIME_SET` / `DATE_CHANGED` / `LOCALE_CHANGED`
> （`AndroidManifest.xml:144-149`），与星链的时间四件套逐字一致（aapt2 实测星链 8 个 widget receiver 各含这 4 条）；
> 且已由运行时注册改为 **manifest 静态注册**（`:141`），`MyApplication` 不再注册，进程死亡时也能收到。第 2 条 `APPWIDGET_VISIBLE/HIDDEN`
> 经 2026-10-02 决策为「隐藏时不刷新」，保持不变。

**目标**：
1. 补 `LOCALE_CHANGED` —— 语言切换会影响星期与日期格式渲染
2. 核查 `APPWIDGET_VISIBLE/HIDDEN` 的必要性（桌面组件可见性变化时刷新，避免后台无谓刷新耗电）
3. **把 `TimeChangeReceiver` 改为 manifest 注册**，使进程死亡时也能响应。同时修正其类 KDoc 中「静态注册到 manifest」的表述 —— 该句与实际实现不符
4. 核查跨日重算等价性：我们用 `DailyRolloverWorker` + 版本戳自愈（`REVIEW.md` 第 14 轮已修「widget 快照永不自愈」），星链用 `dataDate` 比对 + 从 `week_courses_data` 重算 —— 两者机制不同，需实测是否等价

**改动范围** — `AndroidManifest.xml`（注册 `TimeChangeReceiver`）、`TimeChangeReceiver.kt`（KDoc 更正 + `HANDLED_ACTIONS` 增加 `LOCALE_CHANGED`）、`MyApplication.kt`（移除运行时注册以免重复收）、`WidgetUpdateHelper.kt`

**依赖** 建议与 XL-003 同批做（两者是同一个道理：这类广播必须在 manifest 注册）

**验收标准**
1. 切换系统语言 → 8 个小组件的星期/日期文案随之更新
2. 跨零点（可改系统时间验证）→ 小组件自动切到新一天，无需打开 App
3. **杀掉进程后改系统时区** → 重开 App 后闹钟与课表时间均已按新时区重排（当前实现会失败）
4. 组件被桌面隐藏时不触发刷新（省电）

---

### XL-015 · 小组件固定到桌面（Android 17 `requestPinAppWidget`）

| 字段 | 内容 |
|---|---|
| **优先级 / 工作量** | P1 / S |

**现状**：无。用户在桌面长按添加组件，需自行摆放。

**目标**：Android 17+ 支持请求系统把组件「固定」到桌面，对齐星链能力，失败回退到应用内提示。

**改动范围** — 8 个 `*NativeProvider`（或 Glance 版 `GlanceAppWidgetReceiver`）、`WidgetUpdateHelper.kt`

**依赖** 建议与 XL-005 合并做（若走 Glance 路径，Glance 有现成支持）

**验收标准**：Android 17+ 请求固定成功；不支持的版本不崩溃、走既有添加流程

---

## 6. P2 · 新功能补齐（低优先级）

> 以下均为「星链有、我们无」的功能。按用户决策全部写入但排在低优先级 —— 其中 MBTI / 天气属泛娱乐类，与我们「工具型专注」的定位契合度需产品侧判断；学业水平考试与考试资讯是刚需，建议在学业相关需求下优先。

| ID | 功能 | 工作量 | 定位契合度 | 关键约束 | 星链做法 |
|---|---|---|---|---|---|
| **XL-020** | 学业水平考试 | M | 高（刚需） | 需硬编码各省考试院域名并定期维护 | 独立路由 `/academic_status_detail_page` / `_summary_page` / `_webview_page` |
| **XL-021** | 考试资讯 | M | 中 | 需内容源（RSS / 爬取），有合规与稳定性风险 | `ExamNewsModel.fromParsed` + `ExamNewsService`，路由 `/exam_news` |
| **XL-022** | AI 对话 / 识图 | L | 中 | ⚠️ **Key 只能存本机且不参与备份**（我们 AI 导入已如此，`7077564` 已定调，沿用） | 流式：`AiStreamPump` / `AiThinkingLayout` / `AIPersonality`；后端 `omnix-nest` |
| **XL-023** | 天气 | M | 低（泛娱乐） | 需定位权限 —— ⚠️ **我们当前无位置权限，符合权限克制原则，引入需重新评估** | 和风天气 + 动态天气背景（sun/rain/snow/lightning×5/cloud 共 8 张 webp） |
| **XL-024** | MBTI | S | 低（泛娱乐） | 无 | 直接远程拉 16personalities 的 Lottie 动画 + PNG，16 种类型 |
| **XL-025** | 微信 SDK 登录 / 分享 | M | 中 | ⚠️ **`AppSecret` 绝不可进包**（见第 8 章 XL-040） | 对方把 `WECHAT_APP_SECRET` 明文写进 `assets/flutter_assets/.env` —— **这是错误做法，我们若做必须只走服务端** |

**说明**：XL-023 与 XL-025 触碰我们当前刻意保持的两条红线（权限克制、密钥不入包），实施前必须先解决约束，不能照抄对方。

---

## 7. C · 保持优势与防退化

> 以下均为**零成本**项：不新增代码，只在后续改动中不得回退。列出具体证据与守门方式，供 code review 对照。

| ID | 优势 | 已核实证据 | 守门方式 |
|---|---|---|---|
| **XL-030** | 单一闹钟入口 | `AlarmScheduler.kt` 统一分配并挂载三类闹钟：课程提醒 **61000–61199**（上限 200）、自动勿扰 START/END **63000–63059**（上限 60）、早八降级 **65000**（单条）＝ **261**；`AlarmCodeBook` 负责课程槽位分配与全量注销。**2026-10-03 复核补充**：另有 **2 处已登记例外**直连 `AlarmManager` —— `DynamicIslandManager`（60001/60002，独立窗口生命周期）与 `LegacyAlarmMigrator`（50000–50200，反射还原冻结类 Intent 做旧版清理）；两者组件与区间均独立，PendingIntent 按「请求码 + 组件 + action」匹配，不构成误取消 | 新增闹钟需求一律经 `AlarmScheduler`（不得再开新基址）；上述两处例外不得扩大 |
| **XL-031** | 静默失败兜底 | `PostedNotificationRegistry`（occKey → 通知 ID，SharedPreferences）在**重排 `pruneExcept` / 关总开关 `clear` / 升级迁移**三时机回收已投递提醒，专治旧实现「只 `AlarmManager.cancel()` 不 `NotificationManager.cancel()`」造成的常驻提醒残留。**2026-10-03 更正**：该登记簿**不**提供「闹钟未注册仍投递」的兜底（原文表述与代码不符）；真正的静默失败缓解是 ① `AlarmScheduler.setExact` 无 `SCHEDULE_EXACT_ALARM` 时降级 `setAndAllowWhileIdle` + 一轮一次提示（不静默放弃）、② `NotificationScheduler.reschedule` 的 `guard`（单策略抛异常不中断其余）、③ 「下一节课」常驻通知由 `NextClassNotificationWorker`（WorkManager 15 分钟周期）驱动，不依赖闹钟投递 | 保持该登记簿；新增通知类型接入；「闹钟未触发也投递」属新机制，需先立项 |
| **XL-032** | 单元测试 | androidApp **8 个测试文件 / 67 个 `@Test`**（`WidgetListCapacityTest` 14 / `WidgetCourseSelectionTest` 11 / `MorningAlarmDiffTest` 10 / `MorningAlarmPlanTest` 9 / `WidgetNightModeTest` 7 / `WidgetBubbleContrastTest` 6 / `WidgetCoursePaletteTest` 5 / `WidgetTextScaleTest` 5）；shared `androidHostTest` **19 个文件 / 162 个 `@Test`**（**2026-10-03 watchdog 第 39 轮实测复核**：原记「15 文件 / 140 例」已过时 —— v4.66.5 新增 `AutoModePlanTest` 5 例、v4.67.0 新增 `OemGuideResolverTest` 7 例与 `WidgetPinStringsTest` 4 例、v4.67.4 新增 `DatabaseMigrationChainTest` 3 例；此后 `abc5f4d`（v4.67.16）又新增 `LocalSecretsMigrationTest` 3 例 ⇒ 19 文件 / 162 例。两份口径（Gradle XML vs `@Test` 注解）本轮再次实测一致，合计 **229 例 / 0 失败 / 0 错误 / 0 跳过**，见 §0.6） | 本清单每条 P0/P1 的验收标准均含单测；新功能须带测 |
| **XL-033** | 权限克制 | 12 项权限，**无** `READ_PHONE_STATE` / `CAMERA` / 位置 / `REQUEST_INSTALL_PACKAGES`；日历权限为可选功能但静态声明（可优化为按需申请）。**2026-10-03 轮 10 补记**：日历同步权限门此前只检查/申请 `WRITE_CALENDAR`，而该功能同步前有 4 处 `ContentResolver.query`（`Calendars` / `Events` / `Reminders` / 回读计数）需要 `READ_CALENDAR`，权限门无法保证其后继调用的前置条件 ⇒ 已改为读写双检 + `RequestMultiplePermissions` 一并申请（v4.67.6） | 新增权限需在本文档登记理由 |
| **XL-034** | 数据层工程化 | Room + `DatabaseMigrations.kt`（24854 B ≈ 24.3 KiB，2026-10-03 实测）；`backup_rules.xml` / `data_extraction_rules.xml` / `network_security_config.xml` 三件套齐备（星链**均无** backup rules）；WebDAV 自动同步（星链无）。**2026-10-03 核实补充**：迁移链 1→2、2→3、5→6…14→15 齐备；对外发布过的 schema 实测最低为 **9**（122 个 tag 全量扫描），9 → 当前版本**无缺口**；缺 `MIGRATION_3_4` / `MIGRATION_4_5` 是**对外发布之前开发期**的历史遗留（`shared/schemas/` 恰从 `3.json` 起导出，`git log -S "Migration(3, 4)"` 零命中），无证据表明有设备停留在此，成因与不补的理由见 `DatabaseMigrations.kt` 内注释 | 迁移脚本不得省略；发版前核对 schema version；**已由 `shared/src/androidHostTest/kotlin/DatabaseMigrationChainTest.kt` 自动守住**（提版本不补迁移 ⇒ L3 直接失败） |
| **XL-035** | 对比度门禁 | `scripts/check_widget_contrast.py` 已接入 pre-commit（绝对式，0 违规） | 沿用；Glance 迁移后需适配新 token 解析（见 XL-005 验收 5） |

---

## 8. D · 红线（对方踩过、我们必须守住）

> 以下不是「待办」，是**每次发版前的检查项**。证据来自星链 APK 实测，可直接复现。

| ID | 红线 | 对方实测证据 | 我们的现状与守门 |
|---|---|---|---|
| **XL-040** | **密钥 / secret 绝不进包** | `assets/flutter_assets/.env` 与 `.env.production` 均明文含 `WECHAT_APP_SECRET=3157a13029a38362f655dcd4257e7ad7` 与 `ENCRYPT_KEY=StarLinkCurriculum2026AesKey32Bx`（AES-256-CBC 密钥）⇒ 攻击者解包即得密钥，所有课表/成绩数据形同明文 | 我们已有 `SecureCrypto.kt`；**新增任何密钥前必须确认只走 Keystore 或服务端**，不进 `assets`、不进源码 |
| **XL-041** | **`network_security_config` 按域名收窄** | 对方 `cleartextTrafficPermitted=true` + trust-anchors 含 `<certificates src="user">` ⇒ 全链路可被中间人抓包改包 | 我们已有 `network_security_config.xml`，**发版前核对未被改成全局放开** |
| **XL-042** | **正式版必须正式签名** | 对方 `apksigner` 显示 `CN=Android Debug, O=Android, C=US` —— 调试证书，非正式发布包 | 按 `AGENTS.md` 发版流程：逐包校验 V2 证书 SHA-256 = `4ae49d8c…2475f` |
| **XL-043** | **生产包剥离调试日志** | 对方 `libapp.so` 含上千条中文调试日志（`渲染课表: 第`、`小格子数据:`）与内部数据键名（`week_courses_data`），而 `ENABLE_DEBUG_LOG=false` | 我们已用 `AppLog` 统一（`REVIEW.md` 批5 清零 `println`），**新增日志必须走 `AppLog` 而非 `println`**；发版前抽查 |
| **XL-044** | **全 ABI 分发** | 对方**仅 arm64-v8a** 但 `minSdk=24` ⇒ 32 位 Android 7/8 低端机无法安装，实际门槛变成 Android 8+ 64 位 | 我们按 ABI 拆分（`arm64-v8a` / `armeabi-v7a` / `x86_64`），**不得为瘦身砍 ABI** |

---

## 9. E · 观察项

| ID | 事项 | 说明 |
|---|---|---|
| **XL-050** | 竞品迭代节奏监控 | 星链 `versionCode 2280` vs 我们 `420` —— **对方内部迭代密度约为我们的 5 倍**。建议把「近 3 个月发版次数 / 新增功能数」纳入季度竞品复盘，判断我们是否在关键赛道上掉队 |
| **XL-051** | 对方 SDK 供应链 | 对方内置友盟：`libumeng-spy.so` 398KB + DEX 内 400+ 个 `com.umeng` 类。其「数据分析 SDK 声明合规性自检」（`基础组件库完整性自检未通过！请检查应用混淆配置`）值得借鉴 —— 可作为我们隐私合规流程的参考 |

---

## 10. 与既有文档的边界（精确到条）

> 本章是本文档存在的关键：明确哪些**已被覆盖**，避免重复开工。

### 10.1 已被 `REVIEW.md` / `FIXLOG.md` 覆盖（**不要重做**）

| 已完成项 | 批次 |
|---|---|
| `AlarmScheduler` 只按 START action 取消、END 闹钟 PendingIntent 残留 | 批12 |
| `DynamicIslandManager.sync()` 全仓仅 1 处自举调用、KDoc 承诺的 SyncManager 接线缺失 | 批12 |
| manifest 缺 `<queries>` 导致早八时钟探测自锁 | 批12 |
| `WidgetListCapacity` 闭式解漏 `(N-1)×5dp` 分隔线 | 批12 |
| `TimeChangeReceiver` 缺 `TIMEZONE_CHANGED` | 批15 |
| `toggleSilent` 的 prefs 记录仅在「当前非静音」时写入（连续重排不覆盖） | 批14 |
| `RINGER_MODE_NORMAL` 覆盖用户铃声偏好 → 改为记录并还原 | 批14 |
| 灵动岛绕 `effectiveCourses` → 收敛到 `ReminderEngine.effectiveCourses` | 批16 |
| widget 快照永不自愈（改用版本戳判定） | 第14轮 |
| 通知 ID 命名空间、精确闹钟提示收敛、`LOCKED_BOOT_COMPLETED` 死配置 | 批12 |
| 4 规格间三处不一致（DoubleDays 空态、ListVertical 星期与头部字号） | 批17–18 |
| `importTimeSlots` 空列表清空作息、跨课表主键冲突、单双周过滤 | 第14轮 |

### 10.2 已在 `REVIEW.md` 待批清单中（**合并进行，不要另开**）

| 待批项 | 与本清单的关系 |
|---|---|
| 「DND 只认 PRIORITY 的完整『记录还原』版」（第12轮） | **= XL-002**，同一问题。实施时在该条下合并 |
| 「早八降级续链」（第12轮） | **⊂ XL-004** —— 缺的正是「跨链路/跨进程的会话状态」，活动会话登记落地后即可闭环 |
| 「ICS 导入不聚合（1 门 16 周裂成 16 门）」（第14轮） | 独立，本清单未收，可择机 |
| 「ICS `UID` 每次随机 → 重导产生重复事件」（第14轮） | 独立；与 XL-010 的双向 ID 映射思路可复用 |
| 「建表与导入分属两事务，失败留孤儿空表」（第14轮） | 独立，数据层 |
| R1-011 三屏缺 Error 态 / R1-016 POST 超限静默丢 / R1-030 裸 Scope / R1-003 样式迁移 TODO | 独立，本清单未收（属 UI/数据层，非对标范围） |

### 10.3 已由 `docs/widget-display-optimization.md` 覆盖（**不要重做**）

| 已覆盖 | 位置 |
|---|---|
| 浅色提示文字 AA 失败（3.30:1）→ 取消三级文字色 | §2.2 B2，**P0 已于 v3.66.5 实施** |
| 取色兜底色不可见（1.05/1.64:1）→ 取 `textSecondary` | §2.2 B3，**P0 已实施** |
| 4×N 行高常量偏小 → 改运行时实测 + `WidgetListCapacity` + 8 个单测 | §1.6，**P0 已实施** |
| 条目 B 色条对齐 → `match_parent` | §1.7，**P0 已实施** |
| `minResizeHeight` 70dp → 88dp | §4.4，**P0 已实施** |
| 对比度门禁脚本 `scripts/check_widget_contrast.py` + 接入 pre-commit | §6.6-2，**P0 已实施** |
| **12 个 widget token × 6 份取值的设计** | §6.3 —— **XL-011 沿用此设计，只扩规格，不重新设计** |
| 13 条 RemoteViews 不可行项 | §9 —— **XL-005 的核心论证依据** |

**该文档的滞后项**（本清单 XL-011 覆盖）：§0 与 §7 的排期基于 **4 个规格**，实际已 8 个；§2.5「最值得改前 3 项」中的 P1 token 改造与 XL-005 存在重复劳动风险（见 §4.4）。

### 10.4 已由 commit `7077564 feat(xinglian)` v4.66.0 覆盖（**不要重做**）

成绩与绩点 · 学业情况与学分要求 · 四类考证查分入口（31 个省级考试院）· 课堂笔记（图片 + 级联清理）· 找共同空闲 · 课表分享 / 主题分享（含时间段配置 + 二维码）· 空教室查询 · 4 个新小组件（共达 8 类）· 下一节课常驻通知 + 考试倒计时 · 灵动岛胶囊检测 · 教务适配状态页与申请 · 点击式检查更新 · 小组件排障面板 · 意见反馈表单 + 隐私政策 + 用户协议 · 选中文本导入 · AI 识别导入（默认关闭、Key 只存本机且不参与备份）· 全量审查 27 项缺陷修复

---

## 附录 · 证据索引

### 本仓库（全部经 grep / Read 核实）

> **读法（2026-10-03 watchdog 第 40 轮）**：本表是**审计快照的证据索引** —— §0.5 标 ✅ 的条目，其引用行号指向的是**审计当时**的代码；
> 修复落地后该行可能已变（甚至那段代码已不存在）。本轮已对全部引用行逐行复核，凡漂移处就地标注
> 「审计时 `:NN` → 现 `:MM`」或「修复后该行已不存在」。**引用行号前请先看本列标注。**

| 结论 | 位置 |
|---|---|
| 自动勿扰 END 无条件恢复（XL-001 根因） | `androidApp/.../receiver/AutoModeAlarmReceiver.kt:48`（**审计时**；修复后 `:48` 已是 KDoc，判定改在 `:59` 的 `ACTION_AUTO_MODE_END -> false` + `:106` 的 `shouldModeBeOn` 重算） |
| 勿扰 START/END action 常量 | `androidApp/.../receiver/AutoModeAlarmReceiver.kt:122,125`（2026-10-03 复核；审计时记 `:61,64`，文件重写为 KDoc 优先后整体下移） |
| `toggleDnd` 乐观返回 true（XL-002） | `androidApp/.../control/AutoModeController.kt`（**审计时**记 `:62-63`；修复后 `toggleDnd` 在 `:67`，成功路径改为**回读确认**后 `:127` 才 `return true`） |
| `toggleSilent` 成功路径直接 true（XL-002） | `androidApp/.../control/AutoModeController.kt`（**审计时**记 `:97`；修复后 `toggleSilent` 在 `:138`，`:97` 现为 `INTERRUPTION_FILTER_ALL` 常量引用） |
| DND 分支无「进入前状态」记录 | `androidApp/.../control/AutoModeStateProbe.kt:24-33`（复核：该文件 35 行，`:24` 即 `fun isModeOn`） |
| `canScheduleExactAlarms` 排期时检查（已有） | `androidApp/.../alarm/AlarmScheduler.kt:92`（2026-10-03 复核；审计时记 `:77`）；`DynamicIslandManager.kt:175` ✓ |
| 精确闹钟权限变更广播未监听（**审计时**的 XL-003 缺口，**已修**：v4.66.4 新增 `AlarmPermissionReceiver`） | 审计时 `AndroidManifest.xml` 对该 action grep 命中 0；现状见 §2 XL-003 与下方 receiver 行 |
| 每课程独立槽位、旧方案跨课共用已废弃 | `androidApp/.../alarm/AlarmScheduler.kt:42`（2026-10-03 复核；审计时记 `:27`，文件头 KDoc 重写后下移） |
| 精确闹钟提示去重机制（XL-003 复用） | `androidApp/.../alarm/AlarmScheduler.kt:65`（声明 `exactAlarmNoticeSentThisRound`；使用在 `:92-96`，每轮重置在 `:126`。2026-10-03 复核；审计时记 `:44`） |
| 课程提醒单一 lead time | `androidApp/.../reminder/CourseReminderScheduler.kt` KDoc |
| 4 条独立链路 × 10 个 action 常量 | `ReminderAlarmReceiver.kt:111,114,117`；`AutoModeAlarmReceiver.kt:122,125`；`DynamicIslandManager.kt:126,127` ✓；`CourseAlarmReceiver.kt:53`（2026-10-03 复核；审计时记 `109,112,115` / `61,64` / `126,127` / `52`） |
| 旧实现「全量删除 + 事务批量插入」（XL-010 已重写为按开始时刻增量差分） | 旧代码已不在仓库；当前文件 `shared/src/androidMain/.../CalendarAccountManager.android.kt`：删除路径 `:288-291`、`applyBatch` 批量写入 `:333` / `:414`（2026-10-03 复核行号，原记 `:93,143` 随重写失效） |
| 日历 `CALLER_IS_SYNCADAPTER` | 同上 `:86`（2026-10-03 复核；原记 `:64` 已失效） |
| 8 个 RemoteViews 小组件 | `androidApp/.../widget/{tiny,compact,double_days,list_vertical,agenda_list,next_course,week_courses,exam_countdown}/` |
| 16 个小组件布局 XML（另有 `res/layout-night/` 4 份深色档同名覆盖，合计 20） | `androidApp/src/main/res/layout/widget_*.xml` + `androidApp/src/main/res/layout-night/` |
| 权限克制（12 项，无电话/相机/位置/安装包） | `androidApp/src/main/AndroidManifest.xml` |
| manifest 共 **17** 个 receiver（`7077564` v4.66.0 审计时为 15；XL-003 增 `AlarmPermissionReceiver`、XL-014 把 `TimeChangeReceiver` 改为 manifest 静态注册，同属 v4.66.4）；审计时「无精确闹钟权限变更监听」这一缺口**已由 XL-003 修复**，不再是现状 | `androidApp/src/main/AndroidManifest.xml` L108–286（文件共 293 行） |
| `TimeChangeReceiver` 原先由 `MyApplication` 运行时注册、不在 manifest，且其 KDoc 自称「静态注册」与实现不符（**审计时**的事实；**已修**：XL-014 改为 manifest 静态注册 + 新增 `LOCALE_CHANGED`，并改正 KDoc） | `TimeChangeReceiver.kt` + `AndroidManifest.xml`（现 manifest 中已存在该 receiver，见上方 receiver 行） |
| 统一重排入口 `reschedule()`（单次读库 / 三套共用 `effectiveCourses` / 读库失败保留旧闹钟 / 三策略异常隔离） | `androidApp/.../schedule/NotificationScheduler.kt:103,111,115-136,138,145-149` |
| pre-commit 已接对比度与主题泄漏门禁 | `.githooks/pre-commit`（`core.hooksPath=.githooks`）含 `check_widget_contrast` + `check_theme_leak` |
| 8 个单测 / 67 个 `@Test` | `androidApp/src/test/kotlin/com/shangkeschedule/`（8 个 `*Test.kt`，**2026-10-03 第 39 轮实测**；2026-10-02 时为 65 例，v4.67.29 给 `WidgetListCapacityTest` 增 2 例） |
| shared `androidHostTest` **19 个文件 / 162 个 `@Test`** | `shared/src/androidHostTest/`（**2026-10-03 第 39 轮实测**；2026-10-02 为 17 文件 / 156 例，此后 `abc5f4d`（v4.67.16）新增 `LocalSecretsMigrationTest` 3 例；**原记「15 文件 / 140 例」已过时**，见 §0.6） |
| 8 个小组件与闹钟单测（6 个小组件 + 2 个闹钟；原记 7 个是漏计 `WidgetCourseSelectionTest` 的旧数） | `androidApp/src/test/kotlin/com/shangkeschedule/` |
| 小组件数据层与渲染解耦 | `shared/.../data/sync/WidgetDataSynchronizer.kt` + `WidgetRepository.kt` |

### 星链课表（APK 实测）

| 结论 | 证据 |
|---|---|
| 版本 / SDK / ABI | `versionCode 2280` / `versionName 4.20.0`；min 24 / target 36 / compile 37；仅 `arm64-v8a` |
| 架构分层 | libapp.so 20.19MB（Dart AOT）+ classes.dex 4.74MB（Kotlin 仅 147 应用类） |
| 8 个小组件 provider | 全部 `updatePeriodMillis=1800000`、`resizeMode=0x3`、`widgetFeatures=0x5`；`maxResize` 最大 600×600 |
| 小组件标签 | `课表 · 一周` `课表 · 今日` `课表 · 今日(小)` `课表 · 近日` `日程 · 今日` `日程 · 本周` `日程 · 下一件` `日程 · 考试` |
| 小组件数据契约 | `WidgetData`（**6 个** `show*` 开关 + `festivals` + `themeConfig`）、`WidgetThemeConfig`（**22** 字段）、`CourseInfo`（含 `weekNotes: Map`） |
| 统一事件编排 | `EventAlarmStore$Record{eventId, eventType, courseName, location, note, sectionInfo, timeInfo, startTimeMs, endTimeMs, upcomingTimeMs, quietMode}` |
| 三段式 action | `EVENT_UPCOMING` / `EVENT_STARTED` / `EVENT_ENDED` |
| 重叠守卫 | 日志「仍有进行中事件占用降噪，跳过恢复」 |
| 读回校验 | 日志「setInterruptionFilter 已调用但读回为 …」「已恢复 Android 17+ 自动静音（勿扰过滤复位为 …）」 |
| 降级链 | `AutomaticZenRule`(Android 17) → `setInterruptionFilter` → `setRingerMode` → 震动 |
| 精确闹钟双码 | `WidgetRefreshScheduler$Target{action, receiverClass, kind, exactCode, backupCode}` |
| 通知形态四级 | `vivo_atom_notification_channel_v2` / `mi_super_island_channel_v2` / AOSP Live Updates / 常驻倒计时兜底 |
| 日历写回校验 | `SystemCalendarSync$ReplaceResult{calendarId, calendarName, eventIds, writtenCount, verifiedCount, usedFallback, userHint}` |
| 双向 ID 映射 | `IncomingEvent.appId` ↔ `DeviceEvent.deviceEventId` |
| 厂商引导数据化 | `OemReminderGuide$Copy{title, summary, steps: List, hasStartupPage: Boolean}` |
| 跨日重算 | 数据键 `week_courses_data`；日志「跨日检测: dataDate= …」→「跨日重算: 从 week_courses_data 加载当天课程」 |
| 后端端点 | `/app-version/check` `/v1/detect` `/schools/urge` `/schools/adaptation-progress` `/share/curriculum` `/share/theme` `/ai/chat` `/ai/models` `/weather/config` |
| 请求头 | `x-app-id`、`authorization`、`X-WR-CALNAME`（正方教务课表接口标准头） |
| 正方教务 schema | `xqh_id` `njdm_id` `yx_id` `zyh_id` `jxb_id` `kkbm_id` `bh_id` `gpa_scale_id` |
| 网络安全配置（问题） | `cleartextTrafficPermitted=true` + trust-anchors 含 `src="user"` |
| 密钥明文（问题） | `assets/flutter_assets/.env` 含 `WECHAT_APP_SECRET` / `ENCRYPT_KEY` |
| 调试证书（问题） | `apksigner`: `CN=Android Debug, O=Android, C=US` |
| 无 backup rules | Manifest 未声明 `allowBackup` / `dataExtractionRules` |

---

*本文档为待排期清单，不含任何已实施改动。实施任一条目后，请同步更新本文档状态并按 `AGENTS.md` 追加 `工作日志.md`。*
