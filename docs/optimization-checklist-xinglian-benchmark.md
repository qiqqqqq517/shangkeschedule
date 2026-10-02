# 星链对标优化清单

> **基线**：本仓库 `main` @ `143aae0`，版本 v4.66.0（versionCode 420）
> **对标对象**：星链课表 `com.xlhzcm.starcurriculum` v4.20.0（versionCode 2280），arm64-v8a 单 ABI
> **产出日期**：2026-10-02
> **状态**：待排期。本文档只列「已核实证据」与「建议」，不含任何已实施改动。

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

## 1. 总览速览

| 章 | 桶 | 条数 | P0 | P1 | P2 | 工作量合计 |
|---|---|---|---|---|---|---|
| 2 | 缺陷与可靠性 | 3 | 3 | — | — | M |
| 3 | 架构：统一事件编排层 | 1 | 1 | — | — | L |
| 4 | 战略：小组件 Glance 迁移 | 1 | — | 1 | — | XL |
| 5 | 功能加深 | 6 | — | 6 | — | L |
| 6 | 新功能补齐 | 6 | — | — | 6 | L |
| 7 | 保持优势与防退化 | 5 | — | 持续 | — | S（零成本） |
| 8 | 红线 | 5 | — | — | — | S |
| 9 | 观察项 | 2 | — | — | — | S |

**建议排期顺序**：第 2 章（3 条小而急，含 1 个真 bug）→ 第 3 章（架构，先做可避免后续返工）→ 第 4 章决策点 → 其余。

---

## 2. P0 · 缺陷与可靠性

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
- 自动勿扰采用**每会话独立闹钟槽位**（`AlarmScheduler.kt:27` 注释明确记载旧方案 `50001/50002` 跨课共用已废弃，现按课表分逻辑独立分配，`autoModeSlotLimit = AUTO_MODE_SLOT_LIMIT`）

**风险**

课程时间重叠时（如 11:00–12:00 与 11:30–13:00，或课程与早八闹钟重叠），先结束的那条会话会执行 `toggle(enable=false)` 恢复铃声 —— 而另一条会话仍在静音区间内，导致**用户在课堂上手机突然响铃**。这是课表类 App 最不可接受的体验事故之一。

**目标**

结束会话前检查「是否仍有其他会话处于进行中」，有则跳过恢复，并留下可读日志。

**改动范围**

- `androidApp/.../receiver/AutoModeAlarmReceiver.kt` —— END 分支前置查询
- `androidApp/.../control/AutoModeScheduler.kt` —— 暴露「当前活动会话」查询（会话区间来自已排程的 AlarmCodeBook 槽位 + 课程表）
- 建议新增 `shared/.../data/logic/ActiveSessionProbe.kt`（与 `AutoModeStateProbe` 同层，`commonMain` 可单测）

**依赖** 无（可独立做）。**若与 XL-004 同批做，可直接查统一事件表，此项降为 S。**

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

- `androidApp/src/main/AndroidManifest.xml` —— grep `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` **命中 0 次**
- `AlarmScheduler.kt:77` 与 `DynamicIslandManager.kt:175` 均**排期时**检查 `canScheduleExactAlarms`（这点是对的）
- 但全仓无任何 `BroadcastReceiver` 监听权限状态变更

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
- 复用既有 `PermissionNoticeNotifier`（`AlarmScheduler.kt:44` 已有 `exactAlarmNoticeSentThisRound` 去重，防 261 次 `resolveActivity` 重复提示 —— 沿用该机制）

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

## 3. P0 · 架构：统一事件编排层

### XL-004 · 同一门课被拆到 4 条互不感知的链路，缺单一事实来源

| 字段 | 内容 |
|---|---|
| **优先级** | P0（架构） |
| **工作量** | L |
| **类型** | 架构重构 |
| **单独立项** | 是（用户已确认单独排期） |

**现状（已核实）**

androidApp 现有 10 个自定义 action 常量，分属 4 条**各自独立排程**的链路：

| 链路 | action 常量 | 位置 |
|---|---|---|
| 课程提醒 | `ACTION_COURSE_REMINDER` | `receiver/ReminderAlarmReceiver.kt:109` |
| 自动勿扰 | `ACTION_AUTO_MODE_START` / `_END` | `receiver/AutoModeAlarmReceiver.kt:61,64` |
| 灵动岛 | `ACTION_DYNAMIC_ISLAND_START` / `_STOP` | `DynamicIslandManager.kt:126,127` |
| 早八降级 | `ACTION_MORNING_ALARM_FALLBACK` | `receiver/ReminderAlarmReceiver.kt:115` |

关键事实：

1. **START/END 机制已存在**（勿扰与灵动岛各有），本项**不是**「缺三闹钟」—— 这一点纠正了初步对标判断。
2. 但四者**互不感知**：各自排程、各自取消、各自重算。唯一的共享资源是 `AlarmCodeBook`（码分配器），它只管「不撞号」，**不承载任何事件语义**。
3. 课程提醒只有**单一 lead time**：`reminder/CourseReminderScheduler.kt` 的 KDoc 明确写「每门课程只挂一个提醒闹钟」，「关键提醒时间由逻辑层预计算，插到系统这一层并不重要」。
4. 早八降级续链需单独接线 —— `REVIEW.md` 第 12 轮将其列为待批项。

**风险（均已实际发生或可推导）**

1. **取消漏链**：`REVIEW.md` 批12 实证「`AlarmScheduler` 只按 START action 取消，END 闹钟 PendingIntent 残留」（`filterEquals` 只比 action）—— 已修，但根因未除：只要还有第二条独立链路，同类问题会复发。
2. **重排一致性无保障**：课表一改，需 4 条链路各自重算，无原子性。任一漏算 = 幽灵闹钟或漏提醒。
3. **XL-001 无解**：重叠守卫需要知道「当前有几条会话处于进行中」，而现在这个信息**不存在于任何单一处**。
4. **早八降级续链**难以正确接线，因为「降级后该续哪条链」本身就是跨链路问题。

**目标**

引入持久化的**事件编排表**，让「一门课在某个时间窗内应该发生什么」成为可查询的单一事实来源 —— 对齐星链的 `EventAlarmStore$Record`：

```
eventId, eventType, courseName, location, note,
sectionInfo, timeInfo,
startTimeMs, endTimeMs, upcomingTimeMs,   ← 三锚点
quietMode                                  ← 降噪诉求随事件落盘
```

**改动范围**

- 新增 `shared/.../data/db/widget/EventAlarmRecord.kt` + DAO（放 `widget` 库，与 `WidgetCourse` 同域；或新开 `alarm` 库，视 `DatabaseMigrations.kt` 迁移成本定）
- 新增 `shared/.../notification/plan/EventOrchestrator.kt`（`commonMain`，纯逻辑，可 JVM 单测）
- 改造 `androidApp/.../alarm/AlarmScheduler.kt` —— 从「码分配器 + 单链路排程」升级为「按事件表排程」
- 四个 Receiver 收敛为**读事件表决定行为**，不再各自判断
- `ReminderAlarmReceiver.kt:115` 的 `ACTION_MORNING_ALARM_FALLBACK` 接入编排层（顺带闭环 `REVIEW.md` 第 12 轮待批项）

**依赖**

- 建议在 XL-001/002/003 之后（它们可在编排层落地时顺带受益，但不必等待）
- 需要先定「事件类型枚举」——建议 `COURSE_START` / `COURSE_REMIND` / `AUTO_MODE` / `DYNAMIC_ISLAND` / `MORNING_ALARM` / `EXAM`，与现有 action 一一对应

**验收标准**

1. 单测：改一次课表 → 编排层**一次**重算 → 4 条链路的闹钟集合与预期快照完全一致
2. 单测：构造重叠会话 → 事件表能查出「当前活动会话集合」→ XL-001 的守卫成为一次查表
3. 单测：事件表为空/损坏时降级为「只发课程提醒，不降噪不弹岛」，不崩
4. 真机：改课表后 `dumpsys alarm` 比对，无幽灵闹钟残留
5. 回归：现有 7 个 widget/plan 单测 + `MorningAlarmPlanTest` 全绿

**对标依据**

星链把「一个事件 = 三个时间锚点 + 一个降噪诉求」作为一个 `Record` 一次落盘（`EventAlarmStore$Record`），STARTED 负责「发通知 + 开静音 + 弹岛」、ENDED 负责「恢复」，职责正交且可重放。

**与既有文档的关系**

- **闭环** `REVIEW.md` 第 12 轮待批项「早八降级续链」
- **根因层**覆盖批12 已修的「END 闹钟 PendingIntent 残留」（本项修的是结构，不是那一处代码）
- 不重复 `shared/.../notification/plan/` 现有的 `ReminderEngine` / `ReminderPlan` / `MorningAlarmPlan` —— 那些是**纯选择逻辑**（哪些课该提醒、提前几分钟），已设计良好且有单测；本项在其**之上**补一层「事件编排与落盘」，两者职责不同、上下叠放

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

#### 4.2 核心论证：`docs/widget-display-optimization.md §9` 的 13 条「RemoteViews 明确不可行项」中，至少 9 条被 Glance 直接消除

| §9 条目 | RemoteViews 限制 | Glance/Compose 对应能力 |
|---|---|---|
| 1 | 自定义字体按主题切换（需复制整套布局） | `@Composable` 内直接换 `FontFamily` |
| 2 | 任何动画/过渡/呼吸/庆祝动效 | 完整 Compose 动画 |
| 4 | 圆角裁剪子元素（只能靠 shape drawable） | `Modifier.clip()` |
| 5 | 文字自动缩放（无 `autoSizeTextType`） | `TextStyle` + 自适应布局 |
| 6 | 按主题选布局（**Android 无此资源限定符**） | `when (preset)` 一份代码 |
| 7 | 长按/多击手势收不到 | Glance `actionStartActivity` / `actionRunCallback` |
| 8 | 秒级整体重绘受 15 分钟 tick 约束 | Compose 重组（Chronometer 仍为唯一跨进程方案） |
| 9 | 每主题色条几何（需复制 item 布局） | 一份 `Modifier` |
| 11 | 渐变背景按主题（多份 drawable 切换） | `Brush` 直写 |

第 6 条尤其关键 —— 它是 `widget-display-optimization.md` 整份方案的**技术枢纽**（§1.2 已认定「主题联动必须走运行时 setter，不能靠资源限定符」）。Glance 直接消除了这个约束。

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

**目标**（对齐星链 `SystemCalendarSync`）
1. 建立双向 ID 映射（星链字段名：`IncomingEvent.appId` ↔ `DeviceEvent.deviceEventId`）
2. 增量 diff：新增/更新/删除三类操作分开下发
3. 写入后**回读校验计数**（星链字段：`writtenCount` vs `verifiedCount`）
4. 失败给可操作提示（星链字段：`usedFallback` / `userHint`）

**改动范围** — `CalendarAccountManager.android.kt`（`expect/actual` 签名扩展 `shared/.../tool/CalendarAccountManager.kt`）、`CourseConversionViewModel.kt` 调用侧

**依赖** 无

**验收标准**
1. 单测：课表改 1 门课 → 系统日历 `ContentResolver` 调用序列为 `update` 而非 `delete(all)+insert(all)`
2. 写入后回读计数不一致 → 返回失败并给出提示文案（复用既有 i18n 资源机制，勿裸中文）
3. 真机：连续同步 3 次，日历 App 中无重复事件、无丢失事件
4. `CALLER_IS_SYNCADAPTER=true` 与 `applyBatch` 事务性不回归

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

**对标依据** — 星链 `WidgetThemeConfig` 25 个字段（壁纸 type/path/color/opacity/darkness/blurLevel + **六个区域的「底/内容」双重透明度** + fontScale + cornerRadius + cellHeight + contentAlignment + `enableTextAutoContrast` + `hideFinishedCourses` + `courseColors`）；8 个 provider XML 均声明 `previewLayout`、`minResize*`、`maxResize*`（最大到 600×600）、`targetCellWidth/Height`

**与既有文档的关系** — 本项**不重新设计** token，沿用 `widget-display-optimization.md §6.3`；只做规格扩展 + 文档同步。

---

### XL-012 · 通知形态升级：vivo 原子通知 / AOSP Live Updates

| 字段 | 内容 |
|---|---|
| **优先级 / 工作量** | P1 / M |

**现状**：已具备小米超级岛（`DynamicIslandManager` / `DynamicIslandService` / `DynamicIslandAlarmReceiver`）。**未见** vivo 原子通知与 AOSP 实况通知（Android 17）。

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

**改动范围** — `WidgetTroubleshootViewModel.kt`（厂商分派）、新增 `shared/.../data/model/OemGuide.kt`（数据化文案表）、`androidApp` 侧 Intent 跳转

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

**现状（已核实）**：`TimeChangeReceiver` 已覆盖 3 个 action（`REVIEW.md` 批15 已修 `TIMEZONE_CHANGED` 缺失）。星链额外监听 `LOCALE_CHANGED` 与 `APPWIDGET_VISIBLE/HIDDEN/RESTORED`。

**目标**：
1. 补 `LOCALE_CHANGED` —— 语言切换会影响「周一/周一」与日期格式渲染
2. 核查 `APPWIDGET_VISIBLE/HIDDEN` 的必要性（桌面组件可见性变化时刷新，避免后台无谓刷新耗电）
3. 核查跨日重算等价性：我们用 `DailyRolloverWorker` + 版本戳自愈（`REVIEW.md` 第 14 轮已修「widget 快照永不自愈」），星链用 `dataDate` 比对 + 从 `week_courses_data` 重算 —— **两者机制不同，需实测是否等价**

**改动范围** — `androidApp/.../service/notification/TimeChangeReceiver.kt`（action 白名单）、`WidgetUpdateHelper.kt`

**依赖** 无

**验收标准**
1. 切换系统语言 → 8 个小组件的星期/日期文案随之更新
2. 跨零点（可改系统时间验证）→ 小组件自动切到新一天，无需打开 App
3. 组件被桌面隐藏时不触发刷新（省电）

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
| **XL-030** | 单一闹钟入口 | `AlarmScheduler.kt` KDoc 自述「系统闹钟层唯一入口」，`AlarmCodeBook` 统一分配槽位（200 课程 + 60 勿扰 + 1 早八 = 261） | 新增闹钟需求一律经 `AlarmScheduler`，禁止直连 `AlarmManager` |
| **XL-031** | 静默失败兜底 | `PostedNotificationRegistry` —— 系统闹钟未真正注册时仍投递通知 | 保持该登记簿；新增通知类型接入 |
| **XL-032** | 单元测试 | androidApp 7 个（`WidgetListCapacityTest` / `WidgetCoursePaletteTest` / `WidgetBubbleContrastTest` / `WidgetNightModeTest` / `WidgetTextScaleTest` / `MorningAlarmPlanTest` / `MorningAlarmDiffTest`）；shared 10 例 hostTest | 本清单每条 P0/P1 的验收标准均含单测；新功能须带测 |
| **XL-033** | 权限克制 | 12 项权限，**无** `READ_PHONE_STATE` / `CAMERA` / 位置 / `REQUEST_INSTALL_PACKAGES`；日历权限为可选功能但静态声明（可优化为按需申请） | 新增权限需在本文档登记理由 |
| **XL-034** | 数据层工程化 | Room + `DatabaseMigrations.kt`（23KB）；`backup_rules.xml` / `data_extraction_rules.xml` / `network_security_config.xml` 三件套齐备（星链**均无** backup rules）；WebDAV 自动同步（星链无） | 迁移脚本不得省略；发版前核对 schema version |
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
| 「早八降级续链」（第12轮） | **⊂ XL-004**（编排层落地时顺带闭环） |
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

| 结论 | 位置 |
|---|---|
| 自动勿扰 END 无条件恢复（XL-001 根因） | `androidApp/.../receiver/AutoModeAlarmReceiver.kt:48` |
| 勿扰 START/END action 常量 | `androidApp/.../receiver/AutoModeAlarmReceiver.kt:61,64` |
| `toggleDnd` 乐观返回 true（XL-002） | `androidApp/.../control/AutoModeController.kt:62-63` |
| `toggleSilent` 成功路径直接 true（XL-002） | `androidApp/.../control/AutoModeController.kt:97` |
| DND 分支无「进入前状态」记录 | `androidApp/.../control/AutoModeStateProbe.kt:24-33` |
| `canScheduleExactAlarms` 排期时检查（已有） | `androidApp/.../alarm/AlarmScheduler.kt:77`；`DynamicIslandManager.kt:175` |
| 精确闹钟权限变更广播未监听（XL-003） | `androidApp/src/main/AndroidManifest.xml` grep 命中 0 |
| 每课程独立槽位、旧方案跨课共用已废弃 | `androidApp/.../alarm/AlarmScheduler.kt:27` |
| 精确闹钟提示去重机制（XL-003 复用） | `androidApp/.../alarm/AlarmScheduler.kt:44` |
| 课程提醒单一 lead time | `androidApp/.../reminder/CourseReminderScheduler.kt` KDoc |
| 4 条独立链路 × 10 个 action 常量 | `ReminderAlarmReceiver.kt:109,112,115`；`AutoModeAlarmReceiver.kt:61,64`；`DynamicIslandManager.kt:126,127`；`CourseAlarmReceiver.kt:52` |
| 日历全量删除 + 事务批量插入 | `shared/src/androidMain/.../CalendarAccountManager.android.kt:93,143` |
| 日历 `CALLER_IS_SYNCADAPTER` | 同上 `:64` |
| 8 个 RemoteViews 小组件 | `androidApp/.../widget/{tiny,compact,double_days,list_vertical,agenda_list,next_course,week_courses,exam_countdown}/` |
| 16 个小组件布局 XML | `androidApp/src/main/res/layout/widget_*.xml` |
| 权限克制（12 项，无电话/相机/位置/安装包） | `androidApp/src/main/AndroidManifest.xml` |
| 7 个小组件与闹钟单测 | `androidApp/src/test/kotlin/com/shangkeschedule/` |
| 小组件数据层与渲染解耦 | `shared/.../data/sync/WidgetDataSynchronizer.kt` + `WidgetRepository.kt` |

### 星链课表（APK 实测）

| 结论 | 证据 |
|---|---|
| 版本 / SDK / ABI | `versionCode 2280` / `versionName 4.20.0`；min 24 / target 36 / compile 37；仅 `arm64-v8a` |
| 架构分层 | libapp.so 20.19MB（Dart AOT）+ classes.dex 4.74MB（Kotlin 仅 147 应用类） |
| 8 个小组件 provider | 全部 `updatePeriodMillis=1800000`、`resizeMode=0x3`、`widgetFeatures=0x5`；`maxResize` 最大 600×600 |
| 小组件标签 | `课表 · 一周` `课表 · 今日` `课表 · 今日(小)` `课表 · 近日` `日程 · 今日` `日程 · 本周` `日程 · 下一件` `日程 · 考试` |
| 小组件数据契约 | `WidgetData`（7 个 `show*` 开关 + `festivals` + `themeConfig`）、`WidgetThemeConfig`（25 字段）、`CourseInfo`（含 `weekNotes: Map`） |
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
