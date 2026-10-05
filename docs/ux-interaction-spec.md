# 上课 App 交互规格说明书

> 事实基线：工作区 `4.64.25`（code 420）｜文档类型：现状梳理 + 偏差清单（不涉及代码改动）
> 事实来源：本仓库源码与 `strings.xml` 逐条核对，结论均带 `路径:行号`；代码未见实现者标 `⚠️ 待确认（代码未见实现）`
> 适用端：Android（实现主体在 `shared` 模块，Compose Multiplatform 共码）

## 〇、使用说明

### 0.1 这份文档回答什么

1. **页面层级**：App 有哪些页面、如何组织、从哪进入、返回去哪 —— 第 1 部分。
2. **逐屏逐态细节**：四个主页面在每种状态下「看到什么、能点什么、出口是什么」—— 第 2 部分。
3. **核心操作流程**：选校导入、手动建课、文件导入、备份恢复、学期与节次、情侣课表、反馈外链 —— 第 3 部分。
4. **关键组件与三态**：公共组件的加载 / 空白 / 错误 / 反馈四类状态规范与现状覆盖率 —— 第 4 部分。
5. **设计 Token 基线**：颜色、字号、圆角、间距、动效的可引用清单 —— 第 5 部分。
6. **可执行验收标准**：105 条 checkbox 清单（第 6 部分）+ 跨稿偏差汇总（附录 A）。

### 0.2 怎么读

| 角色 | 建议路径 |
| --- | --- |
| 开发 | 第 1 部分定位改动面 → 第 2/3 部分查目标页逐屏逐态 → 附录 A 领取待修项 |
| 测试 | 第 6 部分清单可直接转测试用例；带 `⚠️` 的条目先经产品确认再判定预期 |
| 设计 / 产品 | 第 4/5 部分为组件与 Token 基线；附录 A 是「现状 vs 预期」的差异决策清单 |

### 0.3 记号约定

| 记号 | 含义 |
| --- | --- |
| `路径:行号` | 结论对应的源码位置；行号以本文档事实基线（4.64.25 / 420）为准。分节稿起草期间工作区由 4.64.24 升至 4.64.25，该次改动仅涉及数据层与提交钩子（`data/db/`、`.githooks/`，未触及 `ui/**`），故行号仍然有效 |
| **现状** | 代码当前已实现的行为，不代表设计意图 |
| `⚠️` | 与设计文档 / README / 交互常规不一致，或能力缺失；是待确认项，不是 bug 定性 |
| `- [ ]` | 验收清单条目：勾选 = 现状与预期一致；带 `⚠️` = 需产品确认后判定 |
| `屏:NNNN` / `VM:NNN` / `AS:NNN` / `TS:NNN` / `SET:NNN` / `MORE:NNN` | 分节稿内部的文件缩写，见所在小节开头的缩写说明 |

### 0.4 覆盖范围与已知局限

- **覆盖**：`shared` 模块 37 个 `*Screen.kt` 的页面级交互与导航结构、公共组件四类状态、设计 Token 全表，以及仓库内已有的三份审计（对比度 / 字号缩放 / 色板可区分性）。
- **未覆盖**：Android 桌面小组件的渲染与刷新链路细节、通知渠道逐项配置、iOS 端平台差异、性能与包体（本文档不含性能验收）。
- **代码内未实现特性**：`GRID`（绝对时间网格视图）与 `ScheduleViewMode` 枚举为声明预留，本稿只做标记，不展开设计。
- **补齐说明**：C3（学校与适配器选择）为首轮产出后单独补齐的章节。

### 0.5 目录

1. **第一部分　信息架构与页面层级**
   - A. 信息架构与页面层级 —— 页面树、37 个 Destination、底部 Tab 与返回栈语义、外部入口与深链现状

2. **第二部分　四个主页面逐屏逐态**
   - B1 ·「今日」页结构化事实 —— 今日页 16 个状态：课表/待办/事件/明日/空态/刷新
   - B2 ·「课表」页（周课表）UX 结构事实 —— 周课表 14 个状态：网格与列表、滑周、空周、跨天与跨周拆课
   - B3 · 「日程」页逐屏逐态说明（UX 架构师产出） —— 日程页 14 个状态：月历折叠、分类、完成勾选、长按删除
   - B4 · 「我的」页（设置主页）逐屏逐态说明（UX 架构师产出） —— 设置主页 10 个状态：四组 12 行入口、空课表、权限与配置提示

3. **第三部分　核心操作流程**
   - C1 · 首启与引导（UX 现状核对） —— 首启门控与空课表引导（AddScheduleGuide）
   - C2 · 教务导入（UX 现状核对） —— 选校→适配器→WebView→执行导入的 14 条异常分支
   - C3 · 学校选择与适配器选择（UX 现状核对） —— 选校列表与适配器选择（15 条状态矩阵、12 项缺口）
   - C4 课程与课表编辑（UX 规格 · 资料员摘录） —— 手动建课/编辑的校验与 8 处二次确认
   - C5 文件与文本导入（UX 规格 · 资料员摘录） —— Excel/文本/粘贴/JSON 四类导入与覆盖确认缺口
   - C6 备份与恢复（UX 规格 · 资料员摘录） —— WebDAV 与本地 ZIP 两通道、恢复覆盖确认、部分成功反馈
   - C7 规格梳理：学期设置与节次时间管理 —— 开学日期/总周数/当前周数与节次时长编辑、顺延与重叠校验
   - C8 规格梳理：情侣课表 —— 本地配对建表、双色叠加、解绑不可恢复
   - C9 规格梳理：意见反馈与更多选项 —— 反馈入口与外链通道（无剪贴板兜底、无隐私入口）

4. **第四部分　公共组件与状态规范**
   - D · 跨页面公共组件状态盘点（加载 / 空白 / 错误 / 反馈） —— 约 40 个公共组件的加载/空白/错误/反馈四类状态覆盖

5. **第五部分　设计 Token 基线**
   - E1 · Token 总表（颜色 / 圆角 / 间距） —— 颜色 34 个 token、课程色板 2 套、圆角 10、间距 15+4
   - E2 · Token 总表（字号 / 动效） —— 字号 15 档、动效字段 43、三主题时长对账

6. **第六部分　交互验收清单**
   - E3 · 交互验收清单（页面与流程） —— 55 条页面与流程验收项（8 组）
   - E4 · 验收清单（状态文案 / 无障碍 / 视觉阈值） —— 50 条状态/文案/无障碍/视觉阈值验收项（9 组）

附录 A　偏差与缺口汇总（跨稿）—— 全部 `⚠️` 结论的主题化聚合

附录 B　素材来源与核对方法 —— 分节稿索引、生成方式、未覆盖清单


---

## 第一部分　信息架构与页面层级


### A. 信息架构与页面层级


> 依据代码版本：工作区 `D:\01课程表\shangkeschedule`（任务描述 v4.64.24；README.md:11 徽章为 v3.56.6，徽章落后，以代码为准）。
> 本文全部结论来自源码；未在代码中发现的均标注「⚠️ 待确认（代码未见实现）」。

---

#### 1. 范围声明

本文描述「上课」App（Android/KMP，Compose Multiplatform）的信息架构与页面层级，覆盖：4 个一级 Tab、37 个导航 Destination（`shared/src/commonMain/kotlin/com/shangkeschedule/Navigation.kt`：一级 4 个 L28-32 + 二级 23 个 L35-72 + 导入分类 6 个 L75-81 + 动态传参 4 个 L84-109 = **37**，全部注册于 navSerializersModule L118-165）、页面内弹层，以及小组件/通知等外部入口的落点。仅描述已实现代码；设计稿 `docs/ia5-onboarding-design.md` 与代码的差异见第 6 节。

- 根容器：`shared/src/commonMain/kotlin/com/shangkeschedule/App.kt:100-130` —— `App()` 由启动门控决定：未就绪显示加载占位（L122-129），就绪后按 `startScreen` 进入 COURSE_SCHEDULE→`Destination.CourseSchedule` 或 TODAY_SCHEDULE→`Destination.TodaySchedule`（L110-115）。
- 导航实现：`App.kt:133-384` `AppNavigation`，`rememberNavBackStack(startDestination)`（L134-137），主屏采用「段」式 per-tab 栈（v3.55.0，L146-177）。

---

#### 2. 页面层级树

图例：`[push]` 入栈新增 · `[替换]` 无（代码未见 replace 语义）· `[dialog]` 对话框 · `[sheet]` 底部弹层 · `[tab]` 底栏切换（段重排，非 push）。

```
App 根（App.kt:100-130）
├─ 加载占位 ThemedLoadingIndicator（启动门控未就绪时，App.kt:122-129）
│
├─ T1 Tab 层（MainDestination，底栏 4 Tab，NavigationComponents.kt:141-167）
│  ├─ 今日 TodaySchedule「今日」[tab]
│  │   └─ 弹层：课程详情 TodayCourseDetailSheet [sheet]（TodayScheduleScreen.kt:2213/2233）
│  │        └─ 编辑 → AddEditCourse(courseId) [push]（TodayScheduleScreen.kt:316）
│  ├─ 课表 CourseSchedule「课表」[tab]（默认落地页，见 §4）
│  │   ├─ 顶栏：周次 pill（未设学期时点击→SemesterSettings [push]，WeeklyScheduleScreen.kt:420-427；
│  │   │        已设学期→周选择器 WeekSelectorBottomSheet [sheet]，L870-887）
│  │   ├─ 顶栏：选择课表 CourseTablePickerDialog [dialog]（L475-481、L892-899）
│  │   ├─ ⋮ 溢出菜单（L483-543）：选择学校 [push] / 添加课程 AddEditCourse() [push] /
│  │   │        我的课表 ManageCourseTables [push] / 外观与样式 [push] / 时间段管理 [push]
│  │   ├─ 课程块点击 → CourseDetailBottomSheet [sheet]（L904-919）→ 编辑 AddEditCourse() [push]（L731）
│  │   ├─ 空态（列表视图整周无课）→ AddScheduleGuide 引导组件（L1170-1179，详见 §6）
│  │   │        ├─ 主按钮「教务系统导入」→ SchoolSelectionListScreen [push]（L1175）
│  │   │        └─ 展开「其他添加方式」→ FileImportHub [push]（L1176）/ AddEditCourse() [push]（L1177）
│  │   └─ 长按课程块 → 悬浮编辑模式（非页面，L624-632）
│  ├─ 日程 Schedule「日程」[tab]（叶子页，无 push 出口）
│  │   ├─ 弹层：创建/编辑日程 AppGlassBottomSheet [sheet]（AgendaScreen.kt:998/1482）
│  │   ├─ 弹层：月份选择 AgendaMonthPickerSheet [sheet]（L299-307）
│  │   ├─ 弹层：删除确认 AppDangerDialog「删除日程」[dialog]（L287-297）
│  │   └─ 弹层：时间选择 AgendaTimePickerDialog [dialog]（L1707/1843-1859）
│  └─ 我的 Settings「我的」[tab]
│      ├─ 身份卡 → 我的信息 ProfileInfo [push]（SettingsScreen.kt:191-196）
│      └─ 设置分组列表（SettingsScreen.kt:679-697、L717）→ 11 个二级页 [push]（见 §3）
│
├─ T2 二级页（从 Tab 根直达）
│  ├─ 选择学校 SchoolSelectionListScreen（从课表页 ⋮/空态、课表导入导出、学期管理、情侣课表进入）
│  │   └─ 学校卡片 → 选择适配 AdapterSelection [push]（SchoolSelectionListScreen.kt:143/184）
│  │        └─ 适配项 → 教务网页 WebView [push]（AdapterSelectionScreen.kt:222）
│  │             └─ 导入成功 → 学期设置 SemesterSettings [push]（WebViewScreen.kt:175）
│  ├─ 文件导入 FileImportHub（从课表空态引导、课表导入导出、情侣课表进入）
│  │   ├─ Excel 导入 ExcelImport [push]（FileImportHubScreen.kt:90）
│  │   ├─ JSON 文件导入 JsonFileImport [push]（L96）
│  │   └─ 文本文件导入 TextFileImport("ICS"/"CSV"/"AUTO") [push]（L102/108/114）
│  ├─ 文本粘贴导入 TextImportHub（从课表导入导出进入）
│  │   └─ 格式页 TextImportFormatPage(WAKEUP/PLAIN/JSON/CSV/ICS) [push]（TextImportHubScreen.kt:78-102）
│  ├─ 外观与样式 AppearanceSettings（从我的、课表页 ⋮ 进入）
│  │   ├─ 主题 ThemeSettings [push]（AppearanceSettingsScreen.kt:126）
│  │   ├─ 自定义课表页 ScheduleStyleSettings [push]（L133）
│  │   └─ 个性化显示 PersonalizedDisplay [push]（L142）
│  │        ├─ 玻璃模糊 GlassBlurSettings [push]（PersonalizedDisplayScreen.kt:92）
│  │        ├─ 动画效果 AnimationSettings [push]（L99）
│  │        ├─ 个性化配色 CourseColorSettings [push]（L107）
│  │        └─ 下节课卡 NextCardSettings [push]（L115）
│  ├─ 更多 MoreOptions
│  │   ├─ 语言设置 LanguageSettings [push]（MoreOptionsScreen.kt:191）
│  │   └─ 开源许可证 OpenSourceLicenses [push]（L221）
│  ├─ 课程管理 CourseManagementList（从我的进入）
│  │   ├─ 调课 TweakSchedule [push]（CourseNameListScreen.kt:380）
│  │   ├─ 快速删除 QuickDelete [push]（L388）
│  │   ├─ 添加课程 AddEditCourse(courseId=null) [push]（L221）
│  │   └─ 课程实例 CourseManagementDetail(courseName) [push]（L270）
│  │        └─ 实例列表页内编辑 → AddEditCourse [push]（CourseInstanceListScreen.kt:110/116）
│  ├─ 学期管理 ManageCourseTables（从我的、课表页 ⋮ 进入）
│  │   ├─ 学期设置 SemesterSettings [push]（ManageCourseTablesScreen.kt:271）
│  │   ├─ 选择学校 SchoolSelectionListScreen [push]（L349）
│  │   └─ 备份与恢复 BackupAndRestore [push]（L350）
│  ├─ 课表导入/导出 CourseTableConversion（从我的进入）
│  │   ├─ 文件导入 FileImportHub [push]（CourseTableConversionScreen.kt:191）
│  │   ├─ 选择学校 SchoolSelectionListScreen [push]（L214）
│  │   ├─ 文本粘贴导入 TextImportHub [push]（L220）
│  │   └─ 备份与恢复 BackupAndRestore [push]（L237）
│  ├─ 情侣课表 CoupleScheduleSettings（从我的进入）
│  │   ├─ 时间段管理 TimeSlotSettings(coupleTable.id) [push]（CoupleScheduleSettingsScreen.kt:235）
│  │   ├─ 选择学校 SchoolSelectionListScreen [push]（L327）
│  │   └─ 文件导入 FileImportHub [push]（L334）
│  ├─ 时间段管理 TimeSlotSettings（从我的、课表页 ⋮、情侣课表进入）
│  ├─ 学期设置 SemesterSettings（从我的、课表页周次 pill、WebView、学期管理进入）
│  ├─ 课程提醒设置 NotificationSettings（叶子）
│  ├─ 备份与恢复 BackupAndRestore（叶子；亦可从学期管理/课表导入导出进入）
│  ├─ 我的信息 ProfileInfo（叶子）
│  └─ 添加/编辑课程 AddEditCourse(courseId?, targetCourseTableId?)（从今日、课表、课程管理、课程实例进入）
│
└─ App 级全局弹层
   └─ 导入成功但未设学期 → AppAlertDialog（webview_semester_prompt_*，确认→SemesterSettings）
      （App.kt:408-416、L477-495；这是唯一 App 级全局对话框，其余弹层均在各 Screen 内部）
   └─ 全局 Toast 横幅 AppToastHost（App.kt:116-120）
```

栈行为说明（`App.kt:146-195`）：
- 切 Tab = 目标「段」从栈中间取出追加到尾部（L163-168），**不是 push**；各 Tab 的子页栈、滚动位置、ViewModel 全保留。
- 再点当前 Tab 图标 = 弹回该 Tab 根（L156-160）。
- 二/三/四级页 = `add` 入栈（L171-175）；栈顶已是该页时不重复 push。
- 返回 = 只弹当前段内子页，不跨 Tab（L179-195）。
- 代码未见「替换（replace/popUpTo）」语义的导航调用（⚠️ 以 Navigation.kt 现有 API 与 App.kt 调用面为准）。

---

#### 3. 页面清单总表

Destination 共 **37** 个。路径前缀 `shared/src/commonMain/kotlin/com/shangkeschedule/`；标题为简中实际值，键来自 `shared/src/commonMain/composeResources/values/strings.xml`（en / zh-rTW 三语目录并存：同树 `values-en/`、`values-zh-rTW/`）。

##### 3.1 一级 Tab（4）

| Destination | 页面标题 | Screen 文件（前缀同上） | 层级 | 入口 | 备注 |
|---|---|---|---|---|---|
| CourseSchedule | 课表（nav_course_schedule, strings.xml:35） | ui/schedule/WeeklyScheduleScreen.kt | T1 | 底栏/宽屏侧栏；默认落地页 | 顶栏标题为周次 pill 非固定文案（WeeklyScheduleScreen.kt:431）；左钮=视图切换（L446-469） |
| TodaySchedule | 今日（nav_today, strings.xml:34） | ui/today/TodayScheduleScreen.kt | T1 | 底栏 | AppPageHeader+日期副标题（TodayScheduleScreen.kt:819-825） |
| Schedule | 日程（nav_schedule, strings.xml:37） | ui/agenda/AgendaScreen.kt | T1 | 底栏 | AppPageHeader（AgendaScreen.kt:536-538）；无 push 出口 |
| Settings | 我的（nav_settings, strings.xml:36） | ui/settings/SettingsScreen.kt | T1 | 底栏 | AppPageHeader（SettingsScreen.kt:179-183） |

##### 3.2 二级/三级/四级页（33）

| Destination | 页面标题 | Screen 文件（前缀同上） | 常规层级 | 主要入口 | 备注 |
|---|---|---|---|---|---|
| TimeSlotSettings(targetCourseTableId) | 时间段管理（title_time_slot_management, strings.xml:332） | ui/settings/time/TimeSlotManagementScreen.kt | T2 | 我的、课表⋮、情侣课表 | 情侣路径传 coupleTable.id（CoupleScheduleSettingsScreen.kt:235）；未保存返回有确认弹窗（L509-512） |
| ManageCourseTables | 学期管理（title_semester_management, strings.xml:882） | ui/settings/coursetables/ManageCourseTablesScreen.kt | T2 | 我的、课表⋮ | 命名不一致：Destination=ManageCourseTables、标题=学期管理、入口菜单文案=「我的课表」（title_manage_course_tables, strings.xml:310） |
| SchoolSelectionListScreen | 选择学校（title_select_school, strings.xml:459） | ui/schoolselection/list/SchoolSelectionListScreen.kt | T2 | 课表⋮/空态、导入导出、学期管理、情侣课表 | 搜索态接管返回（L112-119） |
| CourseTableConversion | 课表导入/导出（title_conversion, strings.xml:387） | ui/settings/conversion/CourseTableConversionScreen.kt | T2 | 我的 | 汇聚 4 个导入入口 |
| NotificationSettings | 课程提醒设置（title_course_notification_settings, strings.xml:209） | ui/settings/notification/NotificationSettingsScreen.kt | T2 | 我的 | 叶子 |
| SemesterSettings | 学期设置（title_semester_settings, strings.xml:859） | ui/settings/SemesterSettingsScreen.kt | T2 | 我的、课表周次 pill、WebView、学期管理 | 多入口汇聚页 |
| CoupleScheduleSettings | 情侣课表（section_title_couple_schedule, strings.xml:857） | ui/settings/CoupleScheduleSettingsScreen.kt | T2 | 我的 | |
| MoreOptions | 更多（title_more_options, strings.xml:420） | ui/settings/additional/MoreOptionsScreen.kt | T2 | 我的 | 含默认启动页设置（MoreOptionsScreen.kt:200/281） |
| OpenSourceLicenses | 开源许可证（title_open_source_licenses, strings.xml:428） | ui/settings/additional/OpenSourceLicensesScreen.kt | T3 | 更多 | 叶子 |
| TweakSchedule | 调课（title_tweak_schedule, strings.xml:436） | ui/settings/quickactions/tweaks/TweakScheduleScreen.kt | T3 | 课程管理 | 叶子 |
| QuickDelete | 快速删除课程（item_quick_delete, strings.xml:565） | ui/settings/quickactions/delete/QuickDeleteScreen.kt | T3 | 课程管理 | 叶子 |
| CourseManagementList | 课程管理（item_course_management, strings.xml:321） | ui/settings/coursemanagement/CourseNameListScreen.kt | T2 | 我的 | 多选时标题=「已选择 %1$d 项」（title_selected_items_count, strings.xml:324；CourseNameListScreen.kt:116-119） |
| AppearanceSettings | 外观与样式（item_appearance_settings, strings.xml:586） | ui/settings/appearance/AppearanceSettingsScreen.kt | T2 | 我的、课表⋮ | 同文件还承载 2 个页面（见 3.3） |
| ThemeSettings | 主题（item_theme_settings, strings.xml:587） | （无独立文件，AppearanceSettingsScreen.kt:154） | T3 | 外观与样式 | ⚠️ 见 3.3 |
| ProfileInfo | 我的信息（profile_info_title, strings.xml:922） | ui/settings/profile/ProfileInfoScreen.kt | T2 | 我的身份卡 | 叶子 |
| ScheduleStyleSettings | 自定义课表页（item_schedule_style_settings, strings.xml:588） | （无独立文件，AppearanceSettingsScreen.kt:258、标题 L304） | T3 | 外观与样式 | ⚠️ 见 3.3 |
| PersonalizedDisplay | 个性化显示（item_personalized_display, strings.xml:590） | ui/settings/appearance/PersonalizedDisplayScreen.kt | T3 | 外观与样式 | |
| GlassBlurSettings | 玻璃模糊（glass_section_title, strings.xml:592） | ui/settings/appearance/GlassBlurScreen.kt | T4 | 个性化显示 | 叶子 |
| AnimationSettings | 动画效果（item_animation_settings, strings.xml:620） | ui/settings/appearance/AnimationSettingsScreen.kt | T4 | 个性化显示 | 叶子 |
| CourseColorSettings | 个性化配色（item_course_color_settings, strings.xml:541） | ui/settings/appearance/CourseColorSettingsScreen.kt | T4 | 个性化显示 | 叶子 |
| NextCardSettings | 下节课卡（item_next_card_settings, strings.xml:663） | ui/settings/appearance/NextCardSettingsScreen.kt | T4 | 个性化显示 | 叶子 |
| BackupAndRestore | 备份与恢复（item_backup_restore, strings.xml:686） | ui/settings/backup/BackupScreen.kt | T2 | 我的、学期管理、课表导入导出 | 叶子 |
| LanguageSettings | 语言设置（item_language_settings, strings.xml:430） | ui/settings/additional/LanguageSettingScreen.kt | T3 | 更多 | 叶子 |
| FileImportHub | 文件导入（import_file_hub_title, strings.xml:730） | ui/settings/import/FileImportHubScreen.kt | T2/T3 | 课表空态、导入导出、情侣课表 | |
| ExcelImport | Excel 导入（import_cat_excel, strings.xml:731） | ui/settings/import/ExcelImportScreen.kt | T3 | 文件导入 | |
| JsonFileImport | JSON 文件导入（import_json_title, strings.xml:750） | ui/settings/import/JsonFileImportScreen.kt | T3 | 文件导入 | |
| TextFileImport(format) | 文本文件导入（import_cat_text_file, strings.xml:739） | ui/settings/import/TextFileImportScreen.kt | T3 | 文件导入（ICS/CSV/AUTO） | 强制格式时标题=该格式 screenTitle（TextFileImportScreen.kt:99） |
| TextImportHub | 文本粘贴导入（import_cat_text_paste, strings.xml:789） | ui/settings/import/TextImportHubScreen.kt | T3 | 课表导入导出 | |
| TextImportFormatPage(format) | 动态=所选格式名 | ui/settings/import/TextImportScreen.kt | T4 | 文本粘贴导入（WAKEUP/PLAIN/JSON/CSV/ICS，TextImportHubScreen.kt:78-102） | 缺省=「文本/文件导入」（import_text_any_title, strings.xml:769；TextImportScreen.kt:69） |
| AdapterSelection(schoolId,schoolName,categoryNumber,resourceFolder) | 动态=「校名 - 类别名」 | ui/schoolselection/list/AdapterSelectionScreen.kt | T3 | 选择学校 | 标题为参数拼接（AdapterSelectionScreen.kt:152），⚠️ 无独立三语标题键 |
| WebView(initialUrl,assetJsPath,forceDesktopMode) | 动态=网页标题 | ui/schoolselection/web/WebViewScreen.kt | T4 | 选择适配 | 初始「输入网址」（title_enter_url, strings.xml:475）/「加载中...」（title_loading, strings.xml:15），加载后由 onTitleChange 更新（WebViewScreen.kt:146/250/428） |
| AddEditCourse(courseId?,targetCourseTableId?) | 添加课程（title_add_course, strings.xml:141）/ 编辑课程（title_edit_course, strings.xml:140） | ui/settings/course/AddEditCourseScreen.kt | T2~T4 | 今日、课表、课程管理、课程实例 | 按 isEditing 切换标题（AddEditCourseScreen.kt:164-165）；未保存返回有确认弹窗（L428/432） |
| CourseManagementDetail(courseName) | 动态=课程名 | ui/settings/coursemanagement/CourseInstanceListScreen.kt | T3 | 课程管理 | 多选时=「已选择 %1$d 项」（CourseInstanceListScreen.kt:124-127） |

##### 3.3 无独立 Screen 文件的 Destination（⚠️）

| Destination | 实际定义位置 | 说明 |
|---|---|---|
| ThemeSettings | ui/settings/appearance/AppearanceSettingsScreen.kt:154（同文件第 3 个 @Composable 页面） | Composable 存在且已注册映射（App.kt:418-475），仅文件组织上与「外观与样式」共用一个 .kt；非缺失实现 |
| ScheduleStyleSettings | ui/settings/appearance/AppearanceSettingsScreen.kt:258（标题 L304） | 同上 |

除以上两个外，37 个 Destination 均有对应 Screen Composable 与实体文件（glob `ui/**/*Screen.kt` 共 35 个文件，其中 AppearanceSettingsScreen.kt 一文件承载 3 页）。

---

#### 4. 底部导航规格

| 项 | 规格 | 依据 |
|---|---|---|
| Tab 数 | 4：今日 / 课表 / 日程 / 我的（README.md:19-38 一致） | ui/components/NavigationComponents.kt:141-167（navItems 4 项） |
| 文案来源 | `stringResource`：nav_today / nav_course_schedule / nav_schedule / nav_settings（简中值：今日/课表/日程/我的） | NavigationComponents.kt:144-167；strings.xml:34-37 |
| 图标来源 | 矢量资源对：今日=view_agenda_filled_24px/view_agenda_24px；课表=view_week_filled/view_week；日程=calendar_today_24px（选中未选同图）；我的=account_circle_filled/account_circle | NavigationComponents.kt:141-167 |
| 选中态 | 图标填充↔线性切换 + 颜色补间；方案 B（Haze 兜底）另有共享胶囊指示器位移；点击触发 haptics.tick() | NavigationComponents.kt:567-585、L482 |
| 底栏形态 | 默认自研玻璃 LiquidGlassTabs（v3.48.0）；玻璃能力降级时切换 LegacyGlassBottomBar；宽屏改 NavigationRail | NavigationComponents.kt:285-517、L293、L446-459、L205-283 |
| 底栏隐藏 | 下滑 72dp 阈值隐藏、上滑恢复、切 Tab 复位；系统底部安全区恒定保留（v4.63.3） | NavigationComponents.kt:312-359、L393-409 |
| 默认落地页 | 课表页（CourseSchedule）：`startScreen` 默认值 = `StartScreen.COURSE_SCHEDULE`，App() 按其映射 startDestination | data/model/AppSettingsModel.kt:127（缺省回落 L310）、AppSettingsViewModel.kt:65、App.kt:110-115；可在「更多」改默认启动页（MoreOptionsScreen.kt:200/281） |
| 切 Tab 保留栈 | **保留**。主屏「段」式栈：各 Tab 子页栈/滚动位置/ViewModel 全保留；切 Tab=段重排；再点当前 Tab=弹回该 Tab 根 | App.kt:146-177（L156-160、L163-168） |

---

#### 5. 返回与退出行为

| 场景 | 行为 | 依据 |
|---|---|---|
| 普通二级/三级页（未自定义） | 顶栏返回钮 → `onBack`，弹当前段内子页；系统返回同路径 | App.kt:179-195；各页 AppTopAppBar 返回钮统一 `arrow_back_24px + a11y_back` |
| Tab 根 | 接管并**吞掉**系统返回（栈>1 且栈顶是主屏时 `isBackEnabled=true, onBackCompleted={}`）；栈仅剩起点根（size==1）时不接管 → 系统默认退出 App | App.kt:376-383 |
| 已知缺陷（注释自认） | ≥2 个段时 Tab 根吞返回键 → **无法通过返回退出 App**；预测性返回预览与结果不一致 | App.kt:186-189 |
| 添加/编辑课程 | NavigationBackHandler + 未保存变更弹窗（common_dialog_msg_unsaved_changes / common_action_exit_without_save），确认后丢弃退出 | ui/settings/course/AddEditCourseScreen.kt:120-135、L428/432 |
| 时间段管理 | 同上：未保存返回弹确认 | ui/settings/time/TimeSlotManagementScreen.kt:240-255、L509-512 |
| 选择学校 | 搜索激活态下返回先清搜索（不退页），再按一次才退页 | ui/schoolselection/list/SchoolSelectionListScreen.kt:110-119 |
| 教务网页 | PlatformBackHandler 接管：返回=网页后退（handleBackAction），非退页 | ui/schoolselection/web/WebViewScreen.kt:201（跨平台 expect 见 PlatformWebSpec.kt:15） |
| 课程详情弹层 | 未保存时手势/遮罩关闭 → 先弹「未保存」确认 | ui/schedule/components/CourseDetailBottomSheet.kt:825-829 |
| 双击返回退出 / 退出确认 | ⚠️ 待确认（代码未见实现）：全库 grep「再按一次/退出确认/double back」无命中；且 Tab 根吞返回的已知缺陷使「返回退出」本身不稳定（App.kt:186-189） | — |

---

#### 6. 引导/首启流程：设计稿 vs 实际实现

设计稿：`docs/ia5-onboarding-design.md`（方案 A 全屏 / B 空态卡 / C=B+首启弹层；现状自述首启引导不存在）。代码：`shared/src/commonMain/kotlin/com/shangkeschedule/ui/schedule/components/AddScheduleGuide.kt` + 挂载点 `ui/schedule/WeeklyScheduleScreen.kt:1170-1179`。逐条差异：

| # | 设计稿描述 | 代码实际 | 差异结论 |
|---|---|---|---|
| 1 | 首启引导流程（按「第几次启动」触发） | 不存在首启 flag：全库 grep onboarding/firstLaunch/isFirstRun/hasSeen/Welcome 仅命中 AddScheduleGuide.kt:49 注释；触发条件=**课表为空**（WeeklyScheduleScreen.kt:1170 `pageCourses.isEmpty()`），注释明言「不做全屏 Onboarding…真正决定要不要引导的不是第几次启动，而是有没有课表」（AddScheduleGuide.kt:44-48） | **不一致（有意）**：以数据状态代替首启状态，无 A/C 方案的首启判定 |
| 2 | 方案 A 全屏引导 | 未见实现 | 未实现（代码明确放弃，AddScheduleGuide.kt:46） |
| 3 | 方案 B 空态三步引导 | 已实现：AppEmptyState「本周没有课程」（text_no_courses_this_week, strings.xml:73）+ 不可点击的三步条「选择学校›登录教务›完成导入」（ia5_step_*，strings.xml:979-981；AddScheduleGuide.kt:66-70、L52 注明仅视觉引导无状态机）+ 主按钮「教务系统导入」→ SchoolSelectionListScreen + 可展开「其他添加方式」（ia5_other_ways, strings.xml:982）→ 文件导入 FileImportHub / 手动添加 AddEditCourse()（AddScheduleGuide.kt:120-162；WeeklyScheduleScreen.kt:1175-1177） | 基本一致（实现为 B 的增强版） |
| 4 | 方案 C（B+首启弹层） | 未见实现（无首启弹层逻辑） | 未实现 |
| 5 | 引导位置=课表页空态 | 仅挂载于课表页**列表视图**分支的空态（WeeklyScheduleScreen.kt:614-635 LIST 分支 → L1172）；**网格视图**空态未见该引导 | ⚠️ 待确认：网格视图空态是否有意不放引导（代码未见） |
| 6 | 四条添加路径 | 引导内收敛为 3 条（教务/文件/手动），第 4 条（文本粘贴导入 TextImportHub）不在引导内，仅可从「课表导入/导出」页进入 | 与设计稿「路径平铺会造成选择困难」的取舍一致（AddScheduleGuide.kt:53-54） |
| 7 | 页面树位置 | 引导是课表页内嵌组件，**不新增任何 Destination**、不入导航栈 | 一致（无独立引导页） |

---

#### 7. 深链与外部入口

| 外部入口 | Intent 目标 | 落地 Destination | 依据 |
|---|---|---|---|
| 桌面小组件点击（4 款：tiny/compact/double_days/list_vertical） | 拉起 MainActivity，`FLAG_NEW_TASK|FLAG_CLEAR_TOP`，**无 extras** | 无深链 → App 默认落地页（startScreen，默认课表） | ui 组件层：androidApp/src/main/kotlin/com/shangkeschedule/widget/WidgetRemoteViews.kt:14-24；4 个 NativeRenderer（各 widget 子目录）L37-41 调用；Manifest 声明 androidApp/src/main/AndroidManifest.xml:122-167 |
| 通知点击（课程提醒/灵动岛/早八闹钟） | PendingIntent.getActivity → MainActivity，**未见 putExtra 深链** | 无深链 → 默认落地页 | service/notification 下：CourseAlarmReceiver.kt:294-296、DynamicIslandService.kt:473/487、CourseReminderNotifier.kt:106/127、MorningAlarmNotifier.kt:54-57 |
| 快捷设置磁贴 | ⚠️ 待确认（代码未见实现）：androidApp 全量 grep TileService/onTileClicked 零命中 | — | — |
| URI 深链（http/自定义 scheme） | ⚠️ 待确认（代码未见实现）：MainActivity 仅声明 MAIN/LAUNCHER intent-filter（AndroidManifest.xml:59-62），无 VIEW/BROWSABLE/scheme；launchMode=singleTask（L57） | — | AndroidManifest.xml:55-63 |
| 桌面长按快捷方式（App Shortcuts） | ⚠️ 待确认（代码未见实现）：Manifest 无 shortcuts 声明 | — | AndroidManifest.xml 全文 |
| MainActivity 对 Intent 的处理 | `onCreate` 仅 `setContent { App() }`，**不读取 intent/extras**（另含刷新率钉定与早八闹钟补写，均与导航无关） | 所有外部入口等效「冷启动到默认落地页」 | androidApp/src/main/kotlin/com/shangkeschedule/MainActivity.kt:40-66 |

---

#### 8. 待确认清单

| # | 事项 | 为什么没定论 |
|---|---|---|
| 1 | 快捷设置磁贴（Quick Settings Tile） | androidApp 全量 grep TileService/onTileClicked 零命中，判定未实现；如产品规划中则属「待做」而非「已做」 |
| 2 | URI 深链 / App Shortcuts | Manifest 仅有 MAIN/LAUNCHER，无 VIEW/BROWSABLE/scheme/shortcuts 声明；判定未实现 |
| 3 | 小组件/通知点击应否深链 | 代码统一只拉起 MainActivity 且不读 extras（MainActivity.kt:40-66），落点由 startScreen 决定；是否有意如此属产品决策，代码无法判定 |
| 4 | 全屏首启引导（方案 A/C） | 未实现且 AddScheduleGuide.kt:44-48 注释明确放弃全屏方案；设计稿状态仍为「待确认」（docs/ia5-onboarding-design.md:5），两份材料目标态不同 |
| 5 | 课表页网格视图的空态引导 | AddScheduleGuide 仅挂载在列表视图分支（WeeklyScheduleScreen.kt:614-635/1172）；网格分支空态用什么占位、是否有意不放引导，代码未见 |
| 6 | 「双击返回退出」/退出确认 | 全库无命中；且段式栈已知缺陷（≥2 段时 Tab 根吞返回键，无法退出 App，App.kt:186-189 注释自认）使「返回退出」行为本身不确定，是否算缺陷待产品确认 |
| 7 | 导航「替换」语义 | 代码未见 replace/popUpTo 类调用（App.kt 仅 add/remove），判断为纯 push+弹段模型；如有隐藏调用点需进一步全文审计 |
| 8 | 动态标题的三语覆盖 | AdapterSelection 标题为参数拼接「校名 - 类别名」（AdapterSelectionScreen.kt:152）、TextImportFormatPage/TextFileImport 标题随 format 枚举值变化、WebView 为网页实时标题——固定三语键不适用，实际展示值是否已覆盖三语需运行验证 |
| 9 | ManageCourseTables 三处三名 | Destination=ManageCourseTables、页面标题=「学期管理」（strings.xml:882）、入口菜单=「我的课表」（title_manage_course_tables, strings.xml:310）；术语是否统一属文案决策 |
| 10 | README 版本徽章 | README.md:11 为 v3.56.6，任务描述称 v4.64.24；以工作区代码为准，徽章明显落后，文档同步责任待确认 |

— 本文完（依据均为仓库相对路径:行号，检索日期以工作区当前检出版本为准）


---

## 第二部分　四个主页面逐屏逐态


### B1 ·「今日」页结构化事实


> 依据：README.md:27,46（产品意图）。缩写：屏=`shared/src/commonMain/kotlin/com/shangkeschedule/ui/today/TodayScheduleScreen.kt`（2589 行）、VM=`shared/src/commonMain/kotlin/com/shangkeschedule/ui/today/TodayScheduleViewModel.kt`（349 行）、S=`shared/src/commonMain/composeResources/values/strings.xml`。引用格式 `屏:812` = 该文件第 812 行。目录仅 2 个 .kt（无独立组件文件，全部内聚于屏）。

#### 1. 布局分区表（自上而下）
| # | 区块 | 内容来源 state 字段 | 代码依据 |
|---|---|---|---|
| 0 | 全页 Loading（AppLoading） | TodayUiState.Loading | VM:214,332；屏:262 |
| 1 | 页头 AppPageHeader：标题「今日」+ 日期副标题（%s年%s月%s日 + 星期）+ 周次胶囊 TodayWeekPill | state.today→dateText；weekLabel=Normal→weekIndex，其余→statusText | 屏:812-826；statusText 四分支 屏:382-409 |
| 2 | 下节课卡 TodayNextClassCard（Course/Event/Ended/None 四分支） | resolveNextCardSlot(state,now,nextCardMode) | 屏:799,828-861；解析 屏:1499-1551 |
| 3 | 时间轴标题行「今日课程」+「共 N 节」 | state.courses.size | 屏:863-868 |
| 4 | 课程时间轴 TodayTimelineItem（56dp 时间列+圆点连线+课程卡）；无课时空态 AppEmptyState「今天没有课程」 | state.courses；now | 屏:870-901；行 屏:1188-1282；卡 屏:1284-1402 |
| 5 | 今日日程事件区 TodayEventsSection（标签=当天分类去重拼接+「N 个日程」） | state.events | 屏:904-908；行 屏:1892-2021 |
| 6 | 明日预览区（标题「明日 · M月D日」+「共 N 节」+「查看全部」钮+明日卡） | state.tomorrowCourses | 屏:910-942；卡 屏:1754-1830 |
| 7 | 今日自建待办区 TodayTodosSection「今日待办」+「N 项」 | state.todos | 屏:949-958；行 屏:2028-2179 |
| 8 | 课程详情弹层 TodayCourseDetailSheet（AppGlassBottomSheet） | detailCourse（页内本地 state） | 屏:961-974,2211-2343 |

#### 2. 状态矩阵表
| 状态 | 触发条件 | 界面表现（控件+文案+键名） | 用户可执行动作 | 依据 |
|---|---|---|---|---|
| 加载中 | uiState=Loading（stateIn 初始值） | AppLoading 指示器，无文案 | 无 | VM:214；屏:253-262 |
| 无课表(未导入) | status=NoSemesterConfig：config?.semesterStartDate==null | 胶囊「请设置开学日期」(title_semester_not_set)；课程流强制空，时间轴区显示空态 | 无本页引导动作（仅文案） | VM:155,173-179；屏:383 |
| 学期外·假期 | status=Vacation：startDate!=null && today<startDate | 胶囊「假期中（距离开学还有N天）」(title_vacation_until_start)；课程空→空态 | 无 | VM:156；屏:385-390 |
| 学期已结束 | status=SemesterEnded：weekIndex==null | 胶囊「学期已结束 N 天」(status_semester_ended)，N=学期末日距今天，至少 1；课程空→空态 | 无 | VM:157；屏:392-406 |
| 正常有课 | status=Normal（其余情况）且 courses 非空 | 时间轴：已结束=删除线+透明度0.6、进行中=时间列呼吸圆点、未开始=常色；卡右上「N 节」(today_sections_format)、地点/教师行、类型「理论教学/实验教学」 | 点课程卡→详情弹层 | VM:158；屏:1202-1203,1234-1268,1332-1399 |
| 今日无课(空态) | status=Normal 且 courses.isEmpty() | AppEmptyState「今天没有课程」(text_no_courses_today)；待办/事件/明日区照常 | 下拉刷新 | 屏:870-878 |
| 正在上课 | 下节课卡解析 ongoing：start<=now<end 取第一节 | 下节课卡显示该课（today_next_label「下一节课」+时间徽标，无倒计时数字）；时间轴圆点呼吸 | 点下节课卡→详情弹层 | 屏:1504-1513,829-843 |
| 下节课倒计时 | upcoming：start>now 取第一节；minutesUntil>0 | 下节课卡「N」大字+「分钟后开始」(today_countdown_label) | 点卡→详情弹层 | 屏:1509-1513,1716-1741 |
| 今日全部结束·AUTO_NEXT | 无 ongoing/upcoming 且 nextCardMode=AUTO_NEXT | 时间最近者：未来日程→卡「下一日程」(next_card_label_agenda)；否则明日首节→「下一节课」；都没有→隐藏 | 日程卡不可点；明日课卡可点 | 屏:1515-1550,844-851 |
| 今日全部结束·TODAY_ENDED | 同上且 mode=TODAY_ENDED | 变淡提示卡(faded=0.62)「今日课程已结束，自由探索吧」(next_card_today_ended) | 不可点 | 屏:852-859,1517,1580-1588 |
| 今日全部结束·HIDE | 同上且 mode=HIDE | 下节课卡整卡不渲染 | 无 | 屏:860,1516 |
| 数据错误 | ⚠️ 无 Error 态：TodayUiState 仅 Loading/Success；时间解析失败 catch→null/false 兜底 | 不会出现错误界面；解析失败字段显示「--:--」占位 | 无 | VM:331-348；屏:190,988-997,2378-2397 |
| 待办为空 | todos.isEmpty() | ⚠️ 整区静默隐藏，无「暂无待办」提示 | 无 | 屏:949-958 |
| 事件/明日为空 | events.isEmpty() / tomorrowCourses.isEmpty() | 对应区静默隐藏 | 无 | 屏:904,910 |
| 跳过日期 | isSkippedDay：settings.skippedDates 含今天 | 页头仍显「第 N 周」；课程流强制空→空态 | 无 | VM:152,174-179 |
| 加载详情弹层中 | 点课程卡（任意状态卡） | 玻璃弹层：状态徽标「进行中/已结束/未开始」(today_status_live/done/upcoming) + 「上课时间/上课地点/授课教师/学分」行 + 「关闭」「编辑课程」钮 | 关闭 / 编辑课程→AddEditCourse | 屏:2211-2343 |

#### 3. 手势清单表
| 手势 | 实际行为 | 代码依据 |
|---|---|---|
| 下拉刷新 | refreshing=true→viewModel.refresh()（refreshTrigger+1 重装配查询链）→delay(520) 收起 | 屏:264-274；VM:117-122 README✓ |
| 点下节课卡（Course） | 打开课程详情弹层 | 屏:840 |
| 点下节课卡（Event/Ended） | ⚠️ onClick=null，不可点 | 屏:848,856 |
| 点课程时间轴卡 | 打开课程详情弹层 | 屏:896 |
| 点「查看全部」 | onNavigateWeekly→跳「课表」页 | 屏:928-931,314 |
| 勾选日程事件复选框（仅「待办」分类显示） | toggleable+Role.Checkbox→toggleEventDone | 屏:1860-1875 |
| 勾选自建待办复选框 | haptics.tick()+toggleTodo | 屏:2123-2133 |
| 点自建待办整行 | ⚠️ 跳「日程」页编辑（onEditTodo→Destination.Schedule），本页不可编辑 | 屏:2080,313 |
| 弹层「关闭」 | onDismiss | 屏:2310 |
| 弹层「编辑课程」 | onEditCourse(course.id)→AddEditCourse | 屏:2328,968-971 |

#### 4. 转场与动效表
| 场景 | 类型 | 参数/token | 依据 |
|---|---|---|---|
| Loading→Success | AnimatedContent 溶解（fadeIn+fadeOut，contentKey=状态类） | tween(LocalAppMotion.tokens.statusFadeMs) | 屏:252-259 |
| 下拉指示器显隐 | AnimatedVisibility 缩放+淡入淡出（减弱动态自动退化） | fadeIn+scaleIn(0.6f)/fadeOut+scaleOut(0.6f) | 屏:286-290 |
| 课程卡入场 | 错峰淡入+上移（rememberSaveable 防重播，stagger 钳制 cap） | entranceDurationMs/entranceStaggerMs/entranceStaggerCapMs/entranceInitialAlpha/entranceSlideDp | 屏:1129-1186 |
| 已结束渐变 | 透明度 1f→0.6f 取代硬跳 | tween(statusFadeMs) | 屏:1156-1160 |
| 按压缩放（仅通透 SCALE 档） | animateFloatAsState | tokens.cellPressScale/cellPressSpec | 屏:1163-1174 |
| 进行中圆点呼吸 | infiniteRepeatable 1f→0.55f Reverse（graphicsLayer 内读取，不重组） | tween(tokens.pulseDurationMs) | 屏:1245-1262 |
| 圆点颜色切换 | animateColorAsState | tween(tokens.colorDurationMs) | 屏:1237-1244 |
| 下节课卡变淡 | rememberStatusFadeAlpha(faded, 0.62f) | statusFadeMs | 屏:1610 |
| 事件/待办完成降透明 | rememberStatusFadeAlpha(done, 0.5f) | statusFadeMs | 屏:1978,2110 |
| 列表项增删/位移动画 | Modifier.animateItem（reduceMotion 门控） | 全局令牌 | 屏:888,938 |

#### 5. 待确认清单
1. ⚠️ 无数据错误态：TodayUiState 只有 Loading/Success（VM:331-348），任何查询/解析异常只会静默兜底（空列表、「--:--」）。
2. ⚠️ 待办为空、事件为空、明日无课均为静默隐藏，无空态文案/引导。
3. ⚠️ strings 键 today_sheet_delete_occurrence「删除本次课程」(S:64) 未被今日页引用，详情弹层只有「关闭/编辑课程」两钮（屏:2303-2340）。
4. ⚠️ 屏:446-447 注释声称时间轴卡「进行中主色描边+顶部2dp主色线」，实际 TodayTimelineCard（屏:1284-1402）无 isCurrent 专属描边，进行中仅靠时间列呼吸圆点表达。
5. ⚠️ 无课表/假期/学期已结束状态无引导按钮（如跳学期设置/导入），仅页头胶囊文案。
6. ⚠️ 今日待办只能勾选，新建/编辑在「日程」页（屏:312-313），README「今日待办」未说明该限制。
7. 学期外（Vacation/SemesterEnded）仍渲染「今日课程」标题+空态，不隐藏时间轴区块。
8. README 五项声称（日期周次/下节课卡/时间轴区分/待办与明日预告/下拉刷新）代码均已找到对应实现，仅上述 2/6 属口径差异。


### B2 ·「课表」页（周课表）UX 结构事实


读源：WeeklyScheduleScreen.kt(1414行)、WeeklyScheduleViewModel.kt(1262行)、components/ScheduleGrid.kt(766行)、ScheduleViewMode.kt(23行)、components/FloatingCourseBar.kt(191行)、components/AddScheduleGuide.kt(164行)，摘读 components/WeekSelectorBottomSheet.kt(133行)、components/CourseDetailBottomSheet.kt(1100行)、strings.xml、README.md:21-48。
缩写：Screen=ui/schedule/WeeklyScheduleScreen.kt｜VM=ui/schedule/WeeklyScheduleViewModel.kt｜Grid=ui/schedule/components/ScheduleGrid.kt｜Bar/FloatingCourseBar｜Guide/AddScheduleGuide｜WS=WeekSelectorBottomSheet｜CD=CourseDetailBottomSheet｜Str=composeResources/values/strings.xml（行号均指各文件实际行）。

#### 1. 布局分区表（自上而下）

| 区块 | 内容来源 state 字段 | 依据 |
| --- | --- | --- |
| 壁纸层（可选，AsyncImage，有壁纸时 Scaffold 透明） | 设置页壁纸路径（经 style/settings 流） | Screen:384-403 |
| 顶栏·左：视图切换钮（WEEK↔LIST） | scheduleViewModeState | Screen:446-472；ScheduleViewMode.kt |
| 顶栏·中：标题胶囊（可点）「第 N 周/假期中/请设置开学日期」 | isSemesterSet、daysUntilStart、weekIndexInPager、totalWeeks、semesterStartDate | Screen:344-357,420-427 |
| 顶栏·右：选课表钮 + 更多菜单（导入/加课/我的课表/外观/时间段） | showTableSwitcher、showOverflowMenu | Screen:475-544 |
| 网格·星期头 DayHeader（周几+MM-dd，今日高亮） | pageDateStrings、pageTodayIndex、firstDayOfWeek | Grid:94-96,219；Screen:588-612 |
| 网格·节次列 TimeColumn（可点） | timeSlots、currentSectionIndex | Grid:100,230-238；VM:499,559-567 |
| 网格·课程块层（自定义 Layout 分列定位） | courseCache[pageMondayDate.toString()]:List\<MergedCourseBlock\> | Grid:624-685；Screen:612；VM:77-100 |
| 空态引导 AddScheduleGuide | pageCourses.isEmpty() | Screen:1170-1180 |
| 悬浮·挂起条 FloatingCourseBar | floatingCourse、floatingSourceWeek | Screen:831-845；Bar:74-186 |
| 悬浮·回本周 FAB（LiquidGlass 圆钮） | floatingCourse==null && !isOnCurrentWeekPage | Screen:291-294,846-864,933-1013 |
| 动效层 WeekPagerGlassSheen（叠放 Pager 上） | pager 翻页事件 | Screen:818-819,1025-1123 |
| Snackbar（调课失败/删除结果） | gestureSaveFailed、deleteOccurrenceResult | Screen:321-337 |
| 底部导航（挂起时隐藏，padding=0） | floatingCourse==null | Screen:361-373,556-566 |
| 弹层：周选择 sheet / 换课表 dialog / 课程详情 sheet | showWeekSelector、showTableSwitcher、selectedBlockForDetail | Screen:869-920 |

#### 2. 状态矩阵表

| 状态 | 触发条件 | 界面表现（控件+文案+strings 键） | 用户可执行动作 | 依据 |
| --- | --- | --- | --- | --- |
| 加载中 | 课程流未首帧 | ⚠️页面级无 loading 态：courseCache 无 key→空白网格，可能闪空 | 无 | VM:77-100,317-356 |
| 正常网格 | 学期已设+页在学期内+有课 | 周网格+彩块；今日列高亮当前节次 currentSectionIndex | 点/长按/拖块、点空白格 | VM:445-541；Grid:219-685 |
| 空白周（本周无课） | pageCourses.isEmpty() | AddScheduleGuide：「本周没有课程」(text_no_courses_this_week)+三步条+主按钮「教务系统导入」(item_school_system_import)+「其他添加方式」(ia5_other_ways)→文件导入/添加课程 | 导入或手动加课 | Screen:1170-1180；Guide:76-162；Str:73,394 |
| 全学期无课 | 全 courseCache 三窗口空 | 与空白周共用同一空态（无专属文案区分）⚠️ | 同上 | Screen:1170-1180 |
| 课程块重叠（撞课） | 两课时间+周次均重叠（buildOverlappingClusters） | 并簇分列（subColumn 等宽）；本人课优先占左列；非本周幽灵块与活跃课重叠时被滤除不遮挡 | 各块仍可点/长按/拖 | VM:1010-1017,1134-1216 |
| 跨天课程 | 24H 模式拖拽跨午夜 | ⚠️无真跨天渲染：截断 23:59/endSection=24 | — | VM:796-808 |
| 课程块被截断 | 坐标钳制[1,limit] | SECTION 模式最小高 0.3 节、24H 最小 0；情侣叠加且两表作息不同→块顶显示 displayTimeRange（如 8:00-9:50） | 上下把手微调 | VM:1046-1095,1209-1211 |
| 周次超出学期范围 | pageWeekNum∉1..totalWeeks | 标题「假期中」(title_vacation)；数据仍按日期取（getCoursesWithWeeksByDate）；点空白格→snackbar「请在教学周期间添加或调整课程」(snackbar_add_course_within_semester) | 仅浏览，不能加课 | Screen:344-357,705-739；VM:378；Str:154,174 |
| 隐藏周末开启 | showWeekends=false（默认） | 网格 take(5) 天、列表 dayCount=5 | 设置页切换（item_show_weekends） | Grid:94-96；Screen:1145；Str:280 |
| 非本周课程隐藏 | item_show_non_current_week | 关闭时隐藏；显示时降级块=蒙层 demotedOverlayAlpha+斜纹(白/黑 0.06)、alpha=dimmed、zIndex 0；与活跃课重叠→滤除 | — | VM:380-385,1010；CourseBlock.kt:332-342；Str:279 |
| 数据错误 | startDate 脏数据/slot 缺失/保存失败 | startDate runCatching→兜底「请设置开学日期」(title_semester_not_set)；slot 找不到→块静默丢弃；timeSlots 空且节次模式→整周空列表；失败→「调课失败，请重试」(snackbar_course_move_failed)/「删除失败，请重试」(toast_delete_occurrence_failed) | 重试操作 | VM:482,978,863；Screen:321-337；Str:155,67,75 |
| 挂起模式（跨周调课） | floatingCourse!=null | 藏底栏；FloatingCourseBar「已挂起，左右滑周点击空白处放下」(floating_course_hint)+取消钮 | 滑周+点空白格=放下；取消=放弃 | Screen:361-373,556-566,831-845；Str:29 |
| 编辑中（长按展开） | expandedItem!=null | 块展开+zIndex 2f+上下把手；锁滑周+锁纵向滚动 | 拖块/把手；点空白收起 | Grid:225,357-363,495-590；Screen:585 |
| 学期未设 | !isSemesterSet | 标题「请设置开学日期」(title_semester_not_set)；点标题直达学期设置页 | 设置开学日期 | Screen:344-357,420-427；Str:75 |

#### 3. 手势清单表

| 手势 | 实际行为 | 动画期间锁 | 依据 |
| --- | --- | --- | --- |
| 点课程块（未展开） | 弹课程详情 sheet（v4.62.0 弹窗内就地编辑；crush/情侣叠加只读） | 无 | Grid:389-393；Screen:675-677,902-920；CD:117-151 |
| 长按课程块 | 震动+展开编辑态+isGridHolding=true | 锁滑周(userScrollEnabled=false)+锁纵向滚动 | Grid:389-411；Screen:585,745-747 |
| 展开态单击块 | 收起编辑态 | — | Grid:413-416 |
| 拖拽块（展开态） | 周内移动（列/节吸附落库）；拖到左右边缘→跨周挂起 enterFloatingMode | holding 直至落位 resetAllStates | Grid:428-487；Screen:794-803 |
| 拖上下把手 | 调节起止节次/时间（24H 步进 0.25h），弹簧吸附后落库 onCourseTimeAdjusted | 同上+拖动中边缘自动滚动（阈值 40dp/8dp/16ms） | Grid:495-590,754-766,166-216 |
| 点网格空白格 | 挂起课放下 / 新建课程（预填节次，24H=整小时）；越界→snackbar；展开中→收起 | — | Grid:612-623；Screen:679-739 |
| 左右滑（HorizontalPager） | 切周（无限页 CENTER=Int.MAX_VALUE/2）+扫光动效+syncPagerDate | isGridHolding 时禁用 | Screen:191,218-246,581-585 |
| 点标题胶囊 | 学期未设→跳设置页；否则弹周选择 sheet（点周次→offset animateScrollToPage） | 无 | Screen:420-427,869-888；WS:96-129 |
| 点视图切换钮 | WEEK↔LIST 持久化 | 无 | Screen:446-472 |
| 点 FAB | 回本周 animateScrollToPage(CENTER) | 无 | Screen:846-864 |
| 点挂起条取消钮 | exitFloatingMode | 无 | Screen:831-845；Bar:166-186 |
| 点节次列 | 跳自定义时间段 TimeSlotSettings | 无 | Grid:230-238 |
| LIST 视图长按块 | enterFloatingMode（sourceWeek=当前周）；点块=详情 | — | Screen:614-635 |
| 下拉刷新 | 无（README:46 仅「今日」页声称下拉刷新，周课表未声称，不判⚠️） | — | README:46-48 |
| 双击 | 无此手势 | — | — |
| 代码内自警 | 拖拽落位必须 resetAllStates，否则 isEditingActive 恒 true→滚动+滑周永久锁死（已有防护，需回归） | — | Grid:467-475 |

#### 4. 转场与动效表

| 场景 | 类型/时长-token | 依据 |
| --- | --- | --- |
| 切周扫光 WeekPagerGlassSheen | WEEK_PAGER 组开关；柔绘=整屏 BREATHE 换气（sin，白 alpha 0.0126/深色黑 0.014），其余主题=20% 屏宽斜向扫光带（峰值 0.042）；时长=entranceDurationMs×pager.durationScale（柔绘×2）；首次组合不触发；上一道未扫完跳过 | Screen:1025-1123 |
| 课程块入场 | 错峰淡入 delay=entranceStaggerMs×(dayOffset×3+blockIdx) capped entranceStaggerCapMs；列表 rememberSaveable 防滚动回收重播 | Grid:281-290；Screen:1225-1226,1289 |
| FAB 出现/隐藏 | AnimatedVisibility fadeIn+scaleIn（emphasis specs）；GLASS_FLOATING 组关→瞬切；hideFraction→translationY+alpha | Screen:948-967 |
| 挂起条出现/消失 | 同 FAB 玻璃动效（GLASS_FLOATING 组） | Bar:74-90 |
| 课程块按压反馈 | SCALE(通透)/CONCENTRATION(柔绘触点径向浓度 primary 0.14)/COLOR_DARKEN(书卷整块加深 白/黑 0.05) | Grid:707-746 |
| 把手松手吸附 | spring(dampingRatio=1, StiffnessMediumLow) animateSpringSettle | Grid:754-766 |
| 底栏隐藏（挂起时） | tween(motion.tokens.hideDurationMs, hideEasing) | Screen:260-268 |
| 周选择 sheet 打开 | 自动 animateScrollToItem(当前周-1) | WS:59-64 |
| FAB 回本周翻页 | pager.animateScrollToPage 默认动画（页面未自定义 spec） | Screen:846-864 |

#### 5. 待确认清单

- ⚠️GRID 视图：ScheduleViewMode 枚举声明但注释「预留的绝对时间网格视图，暂未实现」，fromString 未知值回退 WEEK（README 未声称，属代码内未完成特性）。
- ⚠️页面级无加载态：课程流首帧前可能闪空白网格（无 isLoading/skeleton）。
- ⚠️跨天课程仅 24H 截断 23:59，不支持跨日渲染；多周课跨周移动走「拆课」实现（原课留剩余周+clone 新 Uuid），删除/编辑拆分课的 UX 后果未在页内提示。
- ⚠️README「点击顶部周次快速跳转」实际依赖学期已设：未设时点标题直达设置页而非弹周选择。
- ⚠️空白周/全学期无课共用 AddScheduleGuide，无区分文案；幽灵块被遮挡时静默滤除，用户无感知提示。
- 挂起条/详情 sheet 的多主题表面（OPAQUE_GROUPED/SOFT_FEATHER/GLASS_UNDERLAY）参数与 CD 编辑保存细节（1100 行仅摘读）未逐行核；撞色修正范围=整表按课程名归一、crush 不参与（VM:546-556,631-650）。


### B3 · 「日程」页逐屏逐态说明（UX 架构师产出）

适用「上课」v4.64.24，底部导航第 3 个 Tab（今日/课表/日程/我的）。
路径缩写：AS=shared/src/commonMain/kotlin/com/shangkeschedule/ui/agenda/AgendaScreen.kt；VM=shared/src/commonMain/kotlin/com/shangkeschedule/ui/agenda/AgendaViewModel.kt；STR=shared/src/commonMain/composeResources/values/strings.xml。

#### 1 页面目标
以「日期轴/整月日历」选日，按全天/上午/下午/晚上分组展示当日课程与待办/活动/考试/作业，可新建、勾选完成、长按删除。

#### 2 布局分区
| 自上而下 | 内容来源（state 字段） | 依据 |
| --- | --- | --- |
| 底部导航（第 3 Tab 选中） | 常量 Destination.Schedule | AS:241 |
| 整页加载占位 AppLoading | uiState.isLoaded=false | AS:253-254 |
| 页头：最大标题「日程」(nav_schedule STR:37) | — | AS:536-538 |
| 页头副区：月份名「N月▾」(month_names 组 STR:122，点击开选择器)+翻月胶囊‹› | uiState.month | AS:542-593 |
| 页头右上「⋮更多」(item_more_options STR:299)→单项「回到今天」(agenda_back_to_today STR:111) | — | AS:595-618 |
| 日期区二态（纵向拖拽阈值 44dp 切换） | monthExpanded(rememberSaveable,默认收起=false) | AS:333,504-531,622-670 |
| ├ 收起：日期轴 7 格横滚（选中日居中高亮） | stripDays(±120天)、stripCenterIndex=120 | VM:61-68,324-334;AS:662-670,710-843 |
| ├ 展开：整月日历 42 格（恒 6 行不跳高，含月外淡化补白格） | monthCells、firstDayOfWeek | VM:61,338-351;AS:650-661,854-916 |
| └ 展开把手（22dp 高，箭头随态旋转；a11y 展开整月日历/收起整月日历 STR:120-121） | monthExpanded | AS:672-692 |
| 日头：「今天」徽标(agenda_today_badge STR:79)+「M月d日 周X」(agenda_day_title_format STR:80，周全称周一~周日 STR:188-194)+「+」新建钮(48dp 热区/40dp 视觉，a11y_agenda_add STR:113) | selectedDate、today | AS:1132-1197 |
| 副信息行「农历xxx · N 个日程」(STR:81-82) | lunarText、entries.size | AS:379-390 |
| 空态 AppEmptyState「这天还没有日程」(agenda_empty STR:83) | entries.isEmpty() | AS:392-397 |
| 分组列表 LazyColumn：组头(组名+条数，heading 语义)+条目行 | entries 按 entryGroup 分组(全天/上午/下午/晚上 STR:84-87) | AS:402-444,1199-1237,1905-1914 |
| 条目行：时间列 56dp+时间轴列 26dp(竖线+圆点)+卡片(复选框/标题/分类·地点/教师或备注/状态标签) | startTime/endTime/isAllDay/done | AS:1239-1414 |
| 覆盖层：新建 sheet/删除确认对话框/月份选择 sheet | showCreateSheet/deletingEntry/showMonthPicker | AS:274-311,980-1130,1440-1733 |

#### 3 数据与刷新
| 项目 | 事实 | 依据 |
| --- | --- | --- |
| 数据来源 | 三源合流：课表课程(按选中日换算周次)+自建日程 schedule_events+今日待办 todo_items（与今日页待办同源，勾选完成双向同步，VM:44-45 类注释）；coupleScheduleEnabled 时并入情侣课表（节次时间烘进副本不落库） | VM:41-46,126-153 |
| 事件窗口 | getEventsBetweenDates(选中日±127天)（轴 120+缓冲 7） | VM:68,105-109 |
| 课程查询条件 | 周次在 1..totalWeeks(默认20) 才查，否则空列表（假期/未设课表不显示课程） | VM:58,123-159 |
| 排序 | 全天最先→startTime 升序（缺省 "99:99"）→标题 | VM:59,414-418 |
| 下拉刷新 | ⚠️ 未实现（无 PullToRefresh）；数据为 Flow 自动推送 | AS:314-446 |
| 跨天切换 | 点日期轴/月历格→selectDate；轴手动滑动仅吸附居中不提交选中日（v3.47.0） | VM:178-182;AS:741-773 |
| 跨月切换 | ‹›胶囊/月历横滑(阈值 48dp)/月份选择器；applyMonth 选中日「同月同日」平移、无该日收敛月末（10/31→9/30） | VM:184-232;AS:572-591,876-887 |
| 跨午夜刷新 | currentDateFlow 重算 today，徽标与状态不错天 | VM:94-98 |
| 状态节拍 | 每 30s 刷新 nowMinutes，「进行中→已结束」实时翻转 | AS:335-342 |
| 订阅策略 | stateIn(WhileSubscribed(5000))，离屏 5s 停流 | VM:167-172 |

#### 4 状态矩阵
| 状态 | 触发条件（state/异常） | 界面表现（控件+文案+strings 键） | 用户可执行动作 | 依据 |
| --- | --- | --- | --- | --- |
| 加载中 | stateIn 初始 AgendaUiState(isLoaded=false) | 整页 AppLoading（无文案） | 等待 | VM:490;AS:253-254 |
| 正常态（当日有课有办） | isLoaded=true 且 entries 非空 | 日期区+日头+分组时间轴；课程卡含「教师：%1$s」(agenda_teacher_prefix STR:112) | 选日/翻月/新建/勾选/长按删除 | AS:402-444;VM:355-418 |
| 选中日无安排（空态） | entries.isEmpty() | 居中 AppEmptyState「这天还没有日程」(agenda_empty STR:83) | 「+」新建/切日/回今天 | AS:392-397 |
| 整月无安排 | 该月无 events/todos | ⚠️ 无独立月度空态：月历圆点全无(4dp)，点该月任一日落入上条空态 | 翻月/月份选择器 | VM:317-321;AS:962-973 |
| 仅无时间待办 | todo.time 空白→isAllDay=true | 归入「全天」组(agenda_group_allday STR:87)；时间列留空不渲染「--:--」占位（v3.53.4，列宽 56dp 保留对齐） | 点击行/复选框标完成；长按删除 | VM:405-411;AS:1286-1294,1907 |
| 待办未完成 | done=false | 空勾选框；标题无删除线；右侧状态标签「未开始/进行中/已结束」(STR:88-90；primary/success/textSecondary 色) | 点行或勾选框切换 | AS:1254-1264,1345-1357,1403-1410 |
| 待办已完成 | done=true | 标题删除线(LineThrough AS:1366)+整行 alpha 0.55 渐隐(AS:1251)+状态标签隐藏(AS:1403)+勾选框选中；TalkBack 读「已完成/未完成」(v3.69.2) | 再点击取消完成 | AS:1251,1348-1357,1403 |
| 考试/作业分类显示 | event.category=EXAM/HOMEWORK | 卡内 meta「考试 · 地点」/「作业 · 地点」(STR:94-95)；分类色点仅用于新建 sheet 胶囊（考试=danger/作业=info AS:1426-1435）；⚠️ 不可勾选完成（canToggleDone 仅 TODO） | 长按删除（非课程均可） | AS:1265-1273,1416-1423 |
| 跨月切换 | ‹›/月历横滑/月份选择器 | AnimatedContent 上下展开收起；滚轴重新以新选中日居中 | 「回到今天」菜单复位 | VM:199-232;AS:572-591,730-739 |
| 非学期周/未设课表 | weekIndex null 或 ∉1..totalWeeks | 课程不进时间轴，仅显示自建日程与待办；月历/滚轴正常 | 新建日程不受影响 | VM:123-159 |
| 情侣课表叠加 | settings.coupleScheduleEnabled | TA 课程并入同一时间轴，节次时间按 TA 作息烘进副本（不落库），isCrush 标记 | 同普通条目查看 | VM:126-153,372 |
| 与今日页同步 | 待办条目同源 todo_items（VM:44-45 注释） | 勾选状态与今日页「今日待办」一致（Room flow 双向）；「待办」类自建日程走 setEventDone | 任一侧勾选实时同步 | VM:293-304;AS:266 |
| 数据错误 | repository flow 异常 | ⚠️ 待确认（代码未见实现）：无 error 态 UI/无 try-catch，表现为持续 AppLoading 或空态 | 只能退出重试 | AS:253-268;VM:106-171 |
| 新建表单校验 | 「+」→sheet | 创建钮 enabled=标题非空 &&（全天‖开始<结束）(agenda_create STR:106)；待办默认「全天」；submitted 后锁死防双击 | 取消(action_cancel STR:8)/创建 | AS:1465-1476,1570-1574,1666-1687 |

#### 5 交互清单
| 条目/开关 | 点击/手势目标 | 依据 |
| --- | --- | --- |
| 底部 Tab | onNavigate(Destination.*) 四主页面互切 | AS:241 |
| 月份名「N月▾」 | 打开月份选择 sheet（标题「选择月份」STR:114，年份行+4×3 月份格） | AS:542-563,980-1130 |
| 胶囊‹› | previousMonth/nextMonth（a11y 上个月/下个月 STR:116-117），选中日随之平移 | AS:572-591;VM:184-192 |
| 「⋮」菜单 | 单项「回到今天」→goToToday+收起月历 | AS:595-618,362-365 |
| 纵向拖拽/把手 | 下拉>44dp 展开月历、上收收起；把手点击等效（a11y STR:120-121） | AS:504-532,672-692 |
| 日期轴格 | 点击=selectDate；左右滑仅浏览+吸附居中不提交选中日（v3.47.0） | AS:741-773,785-787 |
| 月历格 | 点选日期后自动收起月历（把空间还给时间轴 AS:353-357）；月外补白格淡化仍可点 | AS:353-357,932-936 |
| 月历横向滑 | ±48dp 翻月并带滑入转场（WEEK_PAGER 分组/减弱动态降级） | AS:876-887,656-660,477-497 |
| 「+」新建钮 | AgendaCreateSheet，初始日期=当前选中日 | AS:259,274-284;VM:86-87 |
| AI 描述条（sheet 顶） | 仅 Toast「AI 智能填写即将上线」(agenda_ai_hint STR:98/coming_soon STR:99，未实现) | AS:1519-1546 |
| 分类胶囊 | 切换 category；待办→allDay 默认开、其它→默认带时间 | AS:1566-1576 |
| 「全天」AppSwitch | 关=显示起止时间 chip，开=隐藏时间行 | AS:1583-1601,1605-1622 |
| 日期/时间 chip | 日期→DatePickerModal；时间→时/分滚轮（解析失败回落 12:00）；结束行尾显示时长「N小时N分钟」(STR:109-110) | AS:1605-1622,1706-1732,1843-1903 |
| 时间轴卡单击 | 仅「待办」类行切换完成（课程/其它分类单击无响应） | AS:1334-1335,1272-1273 |
| 时间轴卡长按 | 非课程→触觉+删除确认（「删除日程/确定要删除「%1$s」吗？/确认删除」STR:107/108/22）；课程长按无动作 | AS:1336-1341,286-297;VM:274-282 |
| 勾选框 | Role.Checkbox→onToggleDone | AS:1345-1357 |
| 搜索 | ⚠️ 未实现 | AS:314-446 |

#### 6 转场与动效
| 动效 | 类型/时长 | 依据 |
| --- | --- | --- |
| 翻月/横滑位移 | Animatable：translationX=0.45×宽、alpha −0.35；时长=entranceDurationMs（通透 320ms/柔绘 460ms）、easing=entranceEasing；WEEK_PAGER 分组禁用时 0 瞬切 | AS:477-515;AppMotion.kt:344,369 |
| 月历展开/收起 | AnimatedContent expandVertically+fadeIn/交叉淡出；expandMs=expandDurationMs（通透 280ms/柔绘 420ms），reduceMotion 时 0；fade ≤220ms | AS:622-670;AppMotion.kt:351,376 |
| 列表条目增删 | animateItem 位移动画 | AS:416-441 |
| 完成渐隐 | statusFadeMs（通透 400ms/柔绘 520ms），alpha→0.55 | AS:1251;AppMotion.kt:350,375 |
| 新建 sheet 时间 chip 显隐 | fadeIn/Out(tween(statusFadeMs))+expand/shrinkHorizontally | AS:1810-1830 |
| 底部面板/对话框 | sheetEnter/ExitMs=320/220、dialogEnter/ExitMs=260/180（通透值） | AppMotion.kt:348-349 |
| 页面转场 | 按主题 NavMotionMode：通透 SLIDE(navDurationMs=350)/柔绘 FADE_UP/书卷 LAYER_PUSH | AppMotion.kt:334,424-452 |

#### 7 待确认清单
1. 【不一致】README:29 首项「整月日历」vs 默认收起只见日期轴（monthExpanded 初始 false AS:333）——主次表述颠倒。
2. 【不一致】README:29「完成状态一目了然」：仅「待办」类可勾选（AS:1272-1273），活动/考试/作业/其他无完成态。
3. 【疑似缺陷】新建「待办」时地点输入被静默丢弃（VM:252-258 addTodo 无 location 参数）。
4. 【待确认】数据错误无 UI：无 error 态/重试入口（AS:253-268），DB 异常只见加载或空态。
5. 【待确认】月历圆点只统计 schedule_events+「选中日」待办（VM:161,317-321）——非选中日有待办无日程时不显示圆点。
6. 【待确认】下拉刷新未实现（今日页有，README:46）；课程长按无任何反馈提示。

#### 与 README 描述的一致性核对表
| README 说法 | 代码实际 | 结论 |
| --- | --- | --- |
| L21-25 底部四主页面，日程第 3 位 | AdaptiveNavigationScaffold(Destination.Schedule) | 一致 |
| L29 首项「整月日历」 | 默认收起只显示日期轴，月历需下拉/把手展开 | 不一致（主次表述颠倒） |
| L29 整月日历+日期轴+当日时间轴 | 42 格月历+7 格日期轴+26dp 时间轴列（竖线+圆点） | 一致（形态齐备，主次见上条） |
| L29 课程与待办/活动/考试/作业同屏 | 五类合流同列（VM:355-418），另有「其他」分类与情侣叠加（VM:126-153） | 一致（README 未列「其他」） |
| L29 分类与完成状态一目了然 | 仅「待办」类可勾选完成，活动/考试/作业无完成态 | 部分一致（完成态覆盖窄） |
| L48 与今日页完成状态双向同步 | 待办同源 todo_items（VM:44-45），toggleEntryDone 双写 | 一致 |
| v3.53.4 无时间待办不显示「--:--」 | 时间列无占位文本、列宽保留 | 一致 |


### B4 · 「我的」页（设置主页）逐屏逐态说明（UX 架构师产出）

适用「上课」v4.64.24，底部导航第 4 个 Tab。
路径缩写：SET=shared/src/commonMain/kotlin/com/shangkeschedule/ui/settings/SettingsScreen.kt；VM=shared/src/commonMain/kotlin/com/shangkeschedule/ui/settings/AppSettingsViewModel.kt；CMP=shared/src/commonMain/kotlin/com/shangkeschedule/ui/settings/AppSettingsComponents.kt；MORE=shared/src/commonMain/kotlin/com/shangkeschedule/ui/settings/additional/MoreOptionsScreen.kt；STR=shared/src/commonMain/composeResources/values/strings.xml。

#### 1 页面目标
设置主入口：身份卡+「课表/课程/偏好/关于」四组入口列表；明细功能全部保留在二级页，主页仅含 2 个快捷开关。

#### 2 布局分区
| 自上而下 | 内容来源（state 字段） | 依据 |
| --- | --- | --- |
| 底部导航（第 4 Tab 选中） | 常量 Destination.Settings | SET:151-154 |
| 页头：标题「我的」(nav_settings STR:36) | — | SET:178-183 |
| 身份卡 AppSettingsUserRow：56dp 圆头像(自定义照片或首字母)+昵称+副标题 | appSettings.profileNickname/profileSchool/profileMajor/profileAvatarPath | SET:184-198;CMP:289-390,347-366 |
| 组①「课表」(settings_group_timetable STR:68) 4 条 | buildSettingsSections 数据驱动（v3.54.0 单一来源） | SET:676-684 |
| 组②「课程」(settings_group_courses STR:69) 2 条 | 同上 | SET:685-691 |
| 组③「偏好」(settings_group_preference STR:70) 5 条（末 2 条带 AppSwitch） | showNonCurrentWeekCourses、courseConfig?.showWeekends | SET:692-713 |
| 组④「关于」(settings_group_about STR:71) 1 条 | — | SET:714-719 |
| 分组渲染：AppGroupLabel(heading 语义)+AppSettingsGroup(三主题表面)+AppSettingRow(16dp 图标徽章+标题+chevron/开关，行高 52dp，0.5dp 分隔线) | settingsSections | SET:734-764;CMP:96-270 |
| 数据模型约束 | SettingsEntry(titleRes,iconRes,tone,destination?,toggle?) **无 detail 字段**→主页除身份卡外不显示任何动态文本值 | SET:653-660 |
| 宽屏适配 | 内容限宽约 640dp 居中（SettingsPageMaxWidth，iPad 设置行为） | SET:129-132,166,181 |

#### 3 数据与刷新
| 项目 | 事实 | 依据 |
| --- | --- | --- |
| uiState 来源 | appSettingsFlow+courseTableConfigFlow 合流；stateIn(Lazily, 默认 SettingsUiState() 兜底) | VM:51-56,82-116 |
| currentWeek | 有课表时按学期起止+每周首日计算，限 1..totalWeeks；假期或无课表=null（主页不消费该字段） | VM:96-104 |
| 下拉刷新 | ⚠️ 未实现（静态列表，开关即时写回） | SET:160-200 |
| 开关写回① | 「显示非本周课程」→copy 写 appSettings.showNonCurrentWeekCourses | SET:698-704;VM:147-153 |
| 开关写回② | 「是否显示周末」→写 courseConfig；无课表（config=null）时静默 no-op | SET:705-711;VM:158-169 |
| 订阅策略 | collectAsStateWithLifecycle；stateIn(Lazily)（非 WhileSubscribed） | VM:92-116;SET:128 |

#### 4 状态矩阵
| 状态 | 触发条件（state/异常） | 界面表现（控件+文案+strings 键） | 用户可执行动作 | 依据 |
| --- | --- | --- | --- | --- |
| 正常分组渲染 | appSettingsFlow+courseConfigFlow 到齐（无 isReady 门控，默认值即渲染） | 页头+身份卡+4 组共 12 行：10 行 chevron 导航、2 行 AppSwitch | 点行/拨开关 | SET:151-203,672-720;CMP:169-270 |
| 未导入课表（空） | currentCourseTableId 空→courseTableConfigFlow=null | 主页外观零变化（⚠️ 无空态引导；「暂无课表可选择」text_no_course_tables STR:304 存在但主页未使用）；「是否显示周末」恒 off | 仍可进全部二级页（可先去「课表导入/导出」） | VM:82-86;SET:705-711 |
| 无学期/假期中 | config 非空但 week=null 或 ∉1..totalWeeks | 主页不显示周次；「假期中」(title_vacation STR:174)/「第 %1$d 周」(status_current_week_format STR:290) 仅二级页滚轮对话框 | 点「学期设置」补填 | VM:96-104;SET:680 |
| 无情侣课表 | coupleScheduleEnabled=false（默认）/未绑定 | 「情侣课表」行恒显示，无已绑定/未绑定标记（SettingsEntry 无 detail 字段） | 点入 CoupleScheduleSettings | SET:653-660,689 |
| 通知权限未授予 | ⚠️ 主页无从感知（无徽标/副标题/红点）；「通知权限被拒绝，无法接收课程提醒。」(toast_notification_permission_denied STR:272) 仅提醒页 Toast | 主页原样渲染 | 点「课程提醒设置」二级页处理 | SET:696 |
| 云备份未配置 | ⚠️ 主页仅入口；「未配置网盘连接」(desc_webdav_unconfigured STR:695) 属二级页 | 主页原样渲染 | 点「备份与恢复」二级页配 WebDAV | SET:697 |
| 数据错误 | SettingsUiState 无 error 字段、combine 无 catch | ⚠️ 待确认（代码未见实现）：无错误文案/重试；缺键回落默认值，表现为「所有设置像未设过」；仅头像读写 AppLog.e(TAG=AppSettingsViewModel) | 无 | VM:51-56,70-116,376-411 |
| 版本号与关于区 | 关于组仅「更多」1 条 | ⚠️ 主页无版本号；「版本: %1$s」(label_version_prefix STR:429) 在「更多」页 koinInject(named("AppVersionName")) 渲染 | 点「更多」查看版本 | SET:714-719;MORE:94,169 |
| 头像已设置 | profileAvatarPath 非空 | AsyncImage 圆形裁剪替代首字母头像 | 点卡进 ProfileInfo 修改 | CMP:347-366;VM:376-411 |
| 昵称/学校为空 | profileNickname/profileSchool 空白 | 昵称回落「上课」(app_name STR:3)；副标题回落「让每一节课都有迹可循」(hero_subtitle STR:4) | 点卡进「我的信息」(profile_info_title STR:922) 填写 | SET:143-149 |

#### 5 交互清单
| 条目/开关 | 点击目标（Destination） | 依据 |
| --- | --- | --- |
| 身份卡 | Destination.ProfileInfo→「我的信息」页 | SET:195 |
| 课表导入/导出(item_course_conversion STR:297) | Destination.CourseTableConversion | SET:679 |
| 学期设置(section_title_semester_settings STR:858) | Destination.SemesterSettings | SET:680 |
| 自定义时间段(item_time_slot_customization STR:298) | Destination.TimeSlotSettings() | SET:681 |
| 我的课表(title_manage_course_tables STR:310) | Destination.ManageCourseTables | SET:682 |
| 课程管理(item_course_management STR:321) | Destination.CourseManagementList | SET:688 |
| 情侣课表(item_couple_schedule STR:397) | Destination.CoupleScheduleSettings | SET:689 |
| 外观与样式(item_appearance_settings STR:586) | Destination.AppearanceSettings | SET:695 |
| 课程提醒设置(title_course_notification_settings STR:209) | Destination.NotificationSettings | SET:696 |
| 备份与恢复(item_backup_restore STR:686) | Destination.BackupAndRestore | SET:697 |
| 显示非本周课程(item_show_non_current_week STR:279) | 纯开关行：destination=null→整行不可点，仅 AppSwitch→onShowNonCurrentWeekChanged | SET:698-704,756;VM:147-153 |
| 是否显示周末(item_show_weekends STR:280) | 纯开关行→onShowWeekendsChanged；无课表时点击静默无效 | SET:705-711;VM:158-169 |
| 更多(item_more_options STR:299) | Destination.MoreOptions | SET:717 |
| 无障碍 | 分组标题 heading() 语义（TalkBack 按标题跳转） | CMP:110-114 |
| 搜索/长按/折叠 | ⚠️ 未实现（静态列表，无折叠、无长按） | SET:734-764 |

#### 6 转场与动效
| 动效 | 类型/时长 | 依据 |
| --- | --- | --- |
| 页面转场 | 按主题 NavMotionMode：通透 SLIDE(navDurationMs=350/navExit 240)/柔绘 FADE_UP/书卷 LAYER_PUSH | AppMotion.kt:334-336,424-452 |
| 开关 AppSwitch | 行尾固定 56×32dp 插槽，钉高防 48dp 触控扩张撑行 | CMP:169-270,84-85 |
| 主题表面差异 | PAPER(书卷)/CARD(通透)/SOFT_BLUR(柔绘) 三材质分派（v3.69.0 组件合一） | CMP:125-156 |
| 页面级动效 | ⚠️ 无（列表静态渲染，无入场/展开动画） | SET:160-200 |

#### 7 待确认清单
1. 【待确认】isReady/currentWeek 主页未消费，无骨架屏/空态（VM:51-56,92-116）。
2. 【待确认】数据错误无 UI：无 error 字段与提示（VM:70-116）。
3. 【缺陷候选】无课表时「是否显示周末」可点但写入静默无效（VM:158-169 无 else/无 Toast，控件未置灰）。
4. 【待确认】版本号下沉「更多」二级页（MORE:94,169）；AppSettingRow 有 detail 参数但主页从未传（CMP:169,243-250）。
5. 【待确认】通知权限/云备份/情侣绑定三种状态主页均无提示位，须逐页进入才发现。
6. 【文案】「是否显示周末」为疑问式，与「显示非本周课程」等名词短语风格不统一。

#### 与 README 描述的一致性核对表
| README 说法 | 代码实际 | 结论 |
| --- | --- | --- |
| L21-25 底部四主页面，我的第 4 位 | AdaptiveNavigationScaffold(Destination.Settings) | 一致 |
| L30 按「课表/课程/偏好/关于」四组归类 | settings_group_timetable/courses/preference/about 四组（STR:68-71；SET:676-719），组内 4/2/5/1 条 | 一致 |
| L30 全部明细功能保留在二级页 | 主页仅 11 个入口+2 开关，明细均在二级 Destination | 一致 |
| L30（隐含）主页即设置全貌 | 版本号/通知权限/云备份状态均在二级页，主页无任何状态提示 | 部分一致（README 未承诺主页显状态，但「设置主入口」的信息完备性弱） |


---

## 第三部分　核心操作流程


### C1 · 首启与引导（UX 现状核对）


#### 1. 判定链（代码事实）

- 全库**无** `firstLaunch` / `onboarding` flag；引导触发条件 = **课表为空**（数据判定，非首启判定）。设计依据：docs/ia5-onboarding-design.md:46-49（"按数据判断⇒无需新增首启 flag"）；docs/ia5-onboarding-design.md:18 检索零命中记录。
- 冷启动门控：shared/src/commonMain/kotlin/com/shangkeschedule/App.kt:105-129 —— `SettingsViewModel.startGate`，`gate.isReady=false` 时整屏只渲染 `ThemedLoadingIndicator`（DB 初始化占位）；`isReady=true` 后按 `gate.startScreen` 决定起始 Tab（COURSE_SCHEDULE / TODAY_SCHEDULE，App.kt:110-115）。
- 引导挂载点：**仅**课表页 LIST 视图分支。shared/src/commonMain/kotlin/com/shangkeschedule/ui/schedule/WeeklyScheduleScreen.kt:1170-1179 —— `if (pageCourses.isEmpty())` 时在 LazyColumn 顶部插 `AddScheduleGuide` item(key="empty-week")。周视图网格 / 今日页 / 日程页等其余视图**无**该引导。

#### 2. 落点表：新装 → 各 Tab → 引导 → 点击去向

| 节点 | 用户看到 | 组件/控件 | 点击去向 | 依据 |
|---|---|---|---|---|
| 新装冷启动 | 居中加载动画（非白屏） | `ThemedLoadingIndicator` | —（等 gate 就绪） | App.kt:122-128 |
| 就绪后首屏 | 起始 Tab（默认课表页） | AppNavigation | — | App.kt:108-121 |
| 课表页 LIST 空态 | 「本周没有课程」+ 三步条 | `AppEmptyState(hint)` + 步骤徽标 Row | — | AddScheduleGuide.kt:76；WeeklyScheduleScreen.kt:1170-1179 |
| 三步条文案 | ① 选择学校 ② 登录教务 ③ 完成导入 | 纯视觉，**不可点** | — | AddScheduleGuide.kt:66-70,80-116；strings `ia5_step_select_school`(values/strings.xml:979)、`ia5_step_login`(:980)、`ia5_step_done`(:981) |
| 主按钮「教务系统导入」 | 实心胶囊主色按钮 | `Button` | `Destination.SchoolSelectionListScreen`（学校选择页） | AddScheduleGuide.kt:120-129；WeeklyScheduleScreen.kt:1175 |
| 「其他添加方式」次入口 | 灰色文字按钮，点击**展开** | `TextButton`(ia5_other_ways, strings:982) | 本地展开，无导航 | AddScheduleGuide.kt:131-137 |
| 展开后·文件导入 | 文字按钮 | `TextButton` | `Destination.FileImportHub` | AddScheduleGuide.kt:144-149；strings `import_file_hub_title`(values/strings.xml:730) |
| 展开后·手动添加 | 文字按钮 | `TextButton` | `Destination.AddEditCourse()` | AddScheduleGuide.kt:155-160；strings `title_add_course`(values/strings.xml:141) |
| 课表页 ⋮ 菜单（有课/无课均在） | 「教务系统导入」菜单项 | TelegramMenuItem | `Destination.SchoolSelectionListScreen` | WeeklyScheduleScreen.kt:493-499 |
| 其他 Tab（今日/日程/我的） | 普通空态（无三步引导） | — | — | ⚠️ 本轮未逐页核对空态文案；设计稿亦承认 B 方案盲区（docs/ia5-onboarding-design.md:51） |

注：设计稿提的 ③ 文本导入 `TextImportHub` 在代码展开项中**未实现**，展开项只有文件导入+手动添加（AddScheduleGuide.kt:140-162）。

#### 3. 与 docs/ia5-onboarding-design.md 的差异清单（设计说 vs 代码做）

1. 设计稿 §3-§4 仍标「状态：待确认 / 三选一」（:5），代码已落地**方案 B**；实现注释自述为「方案 B · 增强空态」（AddScheduleGuide.kt:44）。
2. 设计稿空态框内主按钮文案为「从教务系统导入」（:82），代码复用现有键 `item_school_system_import`＝「教务系统导入」（values/strings.xml:394），未按 :109 的警告新增三步以外的按钮串。
3. 设计稿「其他添加方式 ›」预期展开**三个**入口（文件导入/文本导入/手动添加，:88）；代码只展开**两个**（文件导入·手动添加，AddScheduleGuide.kt:140-162），无文本导入入口。差异 ⚠️。
4. 设计稿未定义三步条与主按钮的间距/样式细节；代码用主题 spacing/shapes（AddScheduleGuide.kt:96-98,118,122），属实现补充而非冲突。
5. 设计稿 §7-4 问「导入完成后是否反馈高亮」——代码无任何完成反馈机制（未检索到相关实现）⚠️。
6. 设计稿 C 方案（首启弹层+`hasSeenAddScheduleGuide` flag）未实现，符合 B 定案（全库无该 flag）。
7. 设计稿 §6 验收要求「有课表时不得出现引导」——与代码一致（`pageCourses.isEmpty()` 门控，WeeklyScheduleScreen.kt:1170）。
8. 设计稿 §1 称 v3.69.4 课表空态已接「课表导入/导出」action 按钮（:19,66-67）；现版本该处已被 `AddScheduleGuide` 取代（ WeeklyScheduleScreen.kt:1170-1179 只挂引导），属自然演进、非冲突。

#### 4. 跳过后恢复路径

- 引导**无跳过按钮、无状态**：任何时刻去其他 Tab 即等于"跳过"（AddScheduleGuide.kt:55 注释「任何一步都可跳过，不阻塞用户」）。
- 恢复：只要当前课表仍为空，回到课表页 LIST 视图即再次看到引导（无一次性 flag，可反复触达，AddScheduleGuide.kt:46-48）。
- 注意：用户切到**周视图网格**（非 LIST）时看不到引导——引导只在 LIST 分支；手动添加 1 门课后 `pageCourses` 非空，引导立即消失，之后清空课程可复现。
- 与已有课程的学期切换导致"本周无课"时同样触发（`pageCourses.isEmpty()` 按当前页判断），用户可能误判为"引导回来了"⚠️（现状即如此，无区分逻辑）。

#### 5. 引导→教务导入衔接

主按钮去向 `SchoolSelectionListScreen` 与 ⋮ 菜单、ManageCourseTablesScreen.kt:349、CourseTableConversionScreen.kt:214、CoupleScheduleSettingsScreen.kt:327 同一 Destination，落点见 C2 文件。


### C2 · 教务导入（UX 现状核对）


#### 1. 逐步交互表（入口→选校→选适配器→登录→抓课→结果→落课表）

入口共 5 处，全部 `Destination.SchoolSelectionListScreen`：引导按钮 WeeklyScheduleScreen.kt:1175、课表页 ⋮ 菜单 WeeklyScheduleScreen.kt:493-499、ManageCourseTablesScreen.kt:349、CourseTableConversionScreen.kt:214、CoupleScheduleSettingsScreen.kt:327。

| 步骤 | 用户动作 | 界面反馈（控件+文案+strings 键） | 下一步/分支 | 依据 |
|---|---|---|---|---|
| 0 入口 | 点引导/菜单/设置项 | — | push 选校页 | 上表各依据；App.kt:427 |
| 1 选校 | 搜索或点分类胶囊 | 胶囊输入框 placeholder「搜索学校名称或首字母」(search_hint_school, values/strings.xml:460)；分类 AppSegmentedControl：本专科/研究生/通用工具/其他 (CategoryTabs, SchoolSelectionListScreen.kt:303-328) | 输入→搜索结果列表；不输入→字母索引列表 | SchoolSelectionListScreen.kt:99-108,121-154,156-197 |
| 1a 最近访问 | 点历史卡或 ✕ 删除 | 「最近访问」(label_recent_visit, strings:27)+关闭按钮 | 同选校分支；历史 resource_folder 漂移时优先用索引最新数据 | SchoolSelectionListScreen.kt:246-287 |
| 2 选适配器 | 点适配器卡片 | 卡片：适配器名+描述(无则「暂无详细描述」text_no_detailed_description)+贡献者(label_contributor_format/unknown, AdapterSelectionScreen.kt:255-289)；页标题「校名 - 类别」 | push WebView，URL 补 https 前缀、拼 assetJsPath、按学校 ID 判定强制电脑模式 | AdapterSelectionScreen.kt:196-231,77-94 |
| 3 登录 | 在 WebView 内自行登录教务 | WebView 页面自身登录页；顶部加载进度条(loadingProgress<1, LinearProgressIndicator) | 登录成功→进入课表页；登录失败=页面自身行为，App 无感知 ⚠️ | WebViewScreen.kt:420-440；docs/adapter-sop.md:14,38（App 不碰账号密码） |
| 4 抓课进度 | 点底部「执行导入」(action_execute_import, strings:488) | ①脚本不存在→Toast「导入脚本文件不存在: 路径」(toast_import_script_not_found, strings:495)；②脚本可读→弹「选择一个课表以导入课程」(dialog_title_select_table_for_import, strings:493)；③选课后注入脚本，底部常驻条「正在执行导入脚本...」(toast_executing_import_script, strings:494)，期间按钮禁用 | 选目标课表→注入 buildImportScript；脚本无入口/报错→Toast(wb_adapter_no_entry / wb_adapter_error_fmt, strings:801/800) | WebViewScreen.kt:369-394,443-460,462-485；WebBridgeHandler.kt:508-528 |
| 5 结果 | 等待脚本回传 | 成功 Toast「课程导入成功！共导入 N 门课。」(wb_import_success_fmt, strings:798，1s 防抖报累计值 WebBridgeHandler.kt:179-186)；失败仅首条 Toast（防覆盖 WebBridgeHandler.kt:157-171），按钮状态保留 Failed 文案 | onTaskCompleted→已设开学日期→push 课表页；未设→push SemesterSettings | WebViewScreen.kt:170-178；WebBridgeHandler.kt:481-499 |
| 5a 学期日期补设 | 完成但未设开学日期 | 弹「请设置开学日期」(webview_semester_prompt_title/message, strings:783-784)→「去设置」/「稍后」 | 去 SemesterSettings 或留下 | App.kt:477-495 |

#### 2. 导航栈（push/返回/残留）

- 单 backStack 按 Tab 分段管理，返回只弹当前段子页到根（App.kt:139-160）；选校页为序列化子类（Navigation.kt:129），链：课表页→SchoolSelectionListScreen→AdapterSelection→WebView（App.kt:463-468）。
- 选校页：搜索态接管系统返回——先清搜索词/激活态，再退本页（SchoolSelectionListScreen.kt:110-119）；顶栏 ← 键在非搜索态=onBack（373-387）。
- 适配器页：顶栏 ←=onBack（AdapterSelectionScreen.kt:153-159）。
- WebView：handleBackAction 优先级=退出网址编辑→WebView 页内后退→onBack（WebViewScreen.kt:186-201，系统返回被 PlatformBackHandler 拦截）。
- 残留：导入完成后 onNavigate(CourseSchedule/SemesterSettings) 是**push**，WebView entry 仍留在段内——返回键会退回 WebView 页（无清栈动作）⚠️（WebBridgeHandler.kt:481-499+WebViewScreen.kt:170-178 无 popSelf）。

#### 3. 异常矩阵

| 异常 | 现状（依据或⚠️） | 用户看到什么 | 出口 |
|---|---|---|---|
| 无网 | 选校/适配器列表走本地离线索引（repo/schools，ADAPTER_GUIDE.md:7-10），不受影响；WebView 加载失败⚠️：webview_load_error_* 四键(strings:860-863)仅桌面用（PlatformWeb.jvm.kt:78），Android 侧 onWebViewLoadError 为空实现（WebViewScreen.kt:430） | Android 见系统 WebView 错误页/白屏；桌面见 App 内错误兜底页 | 手动刷新(⋮ action_refresh, strings)或返回 |
| 搜索无结果 | 有实现 | 「没有找到匹配的学校」(text_no_school_found, strings:462) 空态 | 清词/改词（a11y_clear_search 清除钮） |
| 分类无学校 | 有实现 | 「当前类别暂无适配，请选择其他类别或检查数据源。」(text_no_adapter_for_category, strings:461) | 切类别 |
| 无适配器 | 有实现，与加载失败区分(v3.54.0) | 「该学校在『%s』类别下暂无适配器。」(text_no_adapter_for_category_school, strings:474) | 返回上页 |
| 索引加载失败 | AppErrorState+重试 | 「初始化失败，请重试」(error_load_failed, strings:579)+重试钮 | 重试/返回（SchoolSelectionListScreen.kt:227-233；AdapterSelectionScreen.kt:174-180） |
| 登录超时 | ⚠️ 无 App 侧处理；页面自身超时 | 页面自身提示 | 页面内重试/手动导航 |
| 密码错 | ⚠️ 无 App 侧处理（App 不碰账密，adapter-sop.md:38） | 教务页自身提示 | 页面内重试 |
| 验证码 | ⚠️ 无 App 侧输入辅助；仅 7 所学校强制电脑模式改善输入（AdapterSelectionScreen.kt:77-94） | 页面自身验证码 | 页面内输入/切电脑模式(⋮ action_switch_to_desktop_mode, strings:482→「已切换到电脑模式」strings:483) |
| WebView 白屏 | ⚠️ 同"无网"行：Android 无应用内白屏检测/提示 | 白屏 | 刷新/返回 |
| 中途返回 | 返回键=页内后退优先(WebViewScreen.kt:186-201)；导入 Running 中退出后注入已发生，脚本可继续跑完 | — | 返回后仍可回 WebView 查看结果 |
| 解析失败 | 脚本报错上报→仅 Running 时 Toast「适配脚本执行出错：%s」(wb_adapter_error_fmt, strings:800)，浏览期只写日志(WebBridgeHandler.kt:502-528)；桥接 payload 解析失败**静默**仅日志(572-580)⚠️ | 失败 Toast 1 条（首条规则 157-171） | 复位后可重按「执行导入」 |
| 课表为空(0 门) | ⚠️ saveImportedCourses 无 0 门校验(WebBridgeHandler.kt:375-443) | 仍弹「课程导入成功！共导入 0 门课。」(wb_import_success_fmt, strings:798) | 返回课表页自行发现 |
| 与已有课程冲突 | ⚠️ 落库走所选课表(courseConversionRepository，WebBridgeHandler.kt:462 等)，本轮未见去重/覆盖/合并策略 | —（不提示冲突） | 手动用课表管理页处理 |
| 脚本未启动(30s) | 看门狗 IMPORT_IDLE_TIMEOUT_MS=30_000L(WebModelsAndConstants.kt:93, WebBridgeHandler.kt:139-150) | 「导入无响应：适配脚本未在限定时间内启动…」(wb_import_timeout, strings:799) | 确认登录+课表页后重试 |


### C3 · 学校选择与适配器选择（UX 现状核对）


#### 1. 入口链路与 Destination 链

本段是「教务导入」三段式（选校 → 选适配器 → WebView）的前两段，全链路无参数透传丢失。

| 层 | 事实 | 依据 |
|---|---|---|
| Destination 定义 | `SchoolSelectionListScreen`（无参 data object）；`AdapterSelection(schoolId, schoolName, categoryNumber, resourceFolder)`；`WebView(initialUrl="about:blank", assetJsPath=null, forceDesktopMode=false)` | Navigation.kt:41、85-90、93-97 |
| 序列化注册 | 三者均注册进 `navSerializersModule` | Navigation.kt:129、160-161 |
| 渲染绑定 | 选校页只传 onNavigate/onBack；适配器页按 `schoolId, schoolName, categoryNumber, resourceFolder` 四项透传；WebView 按 `initialUrl, assetJsPath, forceDesktopMode` 三项透传 | App.kt:427、463-465、466-468 |
| 入口 A（我的 → 课表转换） | 「我的」页 → `CourseTableConversion` → 选校页 | SettingsScreen.kt:679；CourseTableConversionScreen.kt:214 |
| 入口 B（我的 → 课表管理） | 「我的」页 → `ManageCourseTables` → 选校页（`onImportSemester`） | SettingsScreen.kt:682；ManageCourseTablesScreen.kt:349 |
| 入口 C（课表页空态引导） | 课表为空时的 `AddScheduleGuide` → `onSchoolImport` → 选校页 | WeeklyScheduleScreen.kt:1170-1175 |
| 入口 D（课表页 ⋮ 菜单） | 溢出菜单项 `item_school_system_import` → 选校页 | WeeklyScheduleScreen.kt:493-499 |
| 入口 E（情侣课表设置） | 情侣课表设置页 → 选校页 | CoupleScheduleSettingsScreen.kt:327 |
| 选校 → 适配器（搜索分支） | 搜索结果点学校：先 `saveLastSchool` 写历史，再 push `AdapterSelection(schoolId=id, schoolName=name, categoryNumber=selectedCategory.value, resourceFolder=resource_folder)`，随后清空搜索词并退出搜索态 | SchoolSelectionListScreen.kt:140-152 |
| 选校 → 适配器（列表分支） | 字母索引列表点学校：同样先写历史再 push 同一四项参数 | SchoolSelectionListScreen.kt:180-191 |
| 适配器 → WebView | 点适配器卡片：`import_url` 空则 `about:blank`；无协议则补 `https://`；`assetJsPath = "$resourceFolder/$asset_js_path"`，`asset_js_path` 为空时退化为 `"$resourceFolder/${adapter_id}.js"`；`forceDesktopMode` 按学校 ID 判定 | AdapterSelectionScreen.kt:199-228 |
| 参数类型 | `categoryNumber` 是 `AdapterCategory` 枚举数值（proto：1 GENERAL_TOOL / 2 BACHELOR_AND_ASSOCIATE / 3 POSTGRADUATE），适配器页用 `AdapterCategory.fromValue` 反解，失败回退 BACHELOR | school_index.proto:12-21；AdapterSelectionScreen.kt:118-120 |

#### 2. 学校选择列表页（`SchoolSelectionListScreen.kt`）

**数据来源**：纯本地，无网络请求。读内部存储 Protobuf 索引 `filesDir/repo/index/school_index.pb`（SchoolRepository.kt:58），`SchoolIndex.ADAPTER.decode` 解析（:80），以「文件 size + mtime」为键做内存缓存（:72-84）。`getSchools()` 只保留「至少有一个适配器落在三类菜单类别内」的学校（:39-43、100-104），并按 `initial+name` 排序（:107）。索引缺失（:60-63）或解码失败（:85-88）都**返回空列表而非抛异常**。

**搜索能力**：纯客户端子串匹配，仅两个字段——`school.name.contains(query, ignoreCase)` 与 `school.initial.contains(query, ignoreCase)`（SchoolSelectionViewModel.kt:66-73）；无拼音全拼/模糊/容错搜索，无搜索历史与热词。placeholder「搜索学校名称或首字母」(strings.xml:460)，顶栏右侧放大镜进入搜索态（SchoolSelectionListScreen.kt:391-397），IME 为 Search 动作、清空词则退出搜索态（:362-372）。搜索态下正文区被 `Box` 占位、列表改由顶栏内部渲染（:194-196、410-428）。

**分组/排序**：ViewModel 按 `initial` 首字符大写、同首字母按 `name` 二级排序（SchoolSelectionViewModel.kt:76-79；列表取 `it.initial.firstOrNull()?.uppercase() ?: "#"`，SchoolSelectionListScreen.kt:244）。分组渲染交给通用组件：sticky header 按首字母（AlphabetIndexerList.kt:70-77），右侧 32dp 通讯录索引条支持点按/滑动跳转（AlphabetIndexerList.kt:102-120）。**搜索态结果列表是普通 LazyColumn，无分组、无索引条**（SchoolSelectionListScreen.kt:418-427）。

**列表项显示字段**：仅一个学校图标（school_24px，a11y_school_icon）+ 学校名 `titleMedium`/主文字色（SchoolSelectionListScreen.kt:453-468）。不显示适配器数量、教务系统类型、是否已验证。点击有 500ms 节流防双击重复进页（:437-447）。

**分类胶囊**：`AppSegmentedControl`，只暴露 3 个类别——本科/专科、研究生、通用工具（SchoolSelectionViewModel.kt:50-54；文案 strings.xml:466-468）。枚举值 `ADAPTER_CATEGORY_UNKNOWN=0` 无入口，`category_other`「其他」文案在选校页不可达（仅有代码分支兜底，SchoolSelectionListScreen.kt:310-317）。切换类别不重置搜索词，但会把列表滚回顶部（:162-169）。

**最近访问**：非搜索态、且当前类别有历史记录时，列表顶部插入「最近访问」卡片（label_recent_visit，strings.xml:27），带 ✕ 删除钮（a11y_delete）：SchoolSelectionListScreen.kt:246-287。历史按类别分槽存 DataStore（id/name/resource_folder 三个键，SchoolHistoryRepository.kt:140-147）；渲染时优先用当前索引中同 id 学校的最新 `resource_folder`，索引中已无该校才回退历史值（SchoolSelectionListScreen.kt:247-254）。

**四态**（`SchoolContent` 的 when 分支，SchoolSelectionListScreen.kt:222-297）：

| 态 | 触发条件 | 呈现 | 依据 |
|---|---|---|---|
| 加载中 | `isLoading == true`（初始值即 true，init 中 `loadSchools()`） | 全屏 `AppLoading()` | SchoolSelectionListScreen.kt:223-225；SchoolSelectionViewModel.kt:31、46-48、88 |
| 错误 | `loadFailed == true`＝索引解码/读取抛异常**或返回空列表** | 全屏 `AppErrorState(hint=error_load_failed, fillScreen=true, onRetry=retryLoad)` | SchoolSelectionListScreen.kt:227-233；SchoolSelectionViewModel.kt:90-96、102；strings.xml:579、500 |
| 空 | `filteredSchools.isEmpty() && !isLoading`（当前类别下无任何学校，或搜索词无命中） | 全屏 `AppEmptyState(text_no_adapter_for_category, fillScreen=true)`，**无动作按钮** | SchoolSelectionListScreen.kt:234-240；strings.xml:461 |
| 正常 | 有过滤结果 | AlphabetIndexerList（可选最近访问 header + sticky 分组 + 索引条） | SchoolSelectionListScreen.kt:241-296 |

**刷新/重试**：无下拉刷新（全仓仅 `TodayScheduleScreen.kt:264` 使用 `PullToRefreshBox`）；唯一重试入口是错误态的 `AppErrorState`「重试」按钮 → `retryLoad()`（SchoolSelectionViewModel.kt:102）。空态与正常态下**没有任何手动刷新入口**。

#### 3. 适配器（分类）选择页（`AdapterSelectionScreen.kt`）

**数据来源**：同样纯本地索引，`getAdaptersForSchool(schoolId)` 取该校全部适配器，再按当前类别过滤（AdapterSelectionScreen.kt:133-145；SchoolSelectionViewModel.kt:127-133；SchoolRepository.kt:113-119）。加载态用 `remember { mutableStateOf(...) }` 而非 ViewModel/rememberSaveable（:111-115），靠 `LaunchedEffect(schoolId, currentCategory, retryKey)` 触发（:133）。

**页面骨架**：`TopAppBar` 标题为「`$schoolName - $categoryDisplayName`」，单行省略号截断（:152）；返回 IconButton `onBack`，无障碍名 a11y_back_to_school_list「返回学校列表」（:153-159）。**本页无类别切换控件**，类别由上一页传入的 `categoryNumber` 固定。

**列表项文案**（`AdapterCard`，:243-291）：适配器名 `titleLarge` + 粗体（:255-260）；描述 `bodyMedium` 次要色，空则「无详细描述。」(text_no_detailed_description, strings.xml:471)（:266-271）；底部 info 图标 + 「贡献者: %1$s」(label_contributor_format, strings.xml:473)，维护者空则「未知」(label_contributor_unknown, strings.xml:472)（:273-289）。**不显示 import_url、不显示脚本文件名/存在性、不显示类别标签**。

**「电脑模式」开关：本页不存在用户可见开关。** 只有硬编码集合 `FORCE_DESKTOP_MODE_SCHOOL_IDS`，含 7 所学校 ID：`u_15f498f5`(汕头大学)、`u_c0a22802`(武汉纺织大学外经贸学院)、`u_26bd7359`(沈阳农业大学)、`u_308bdd18`(西安医学院)、`MANUAL_UCAS`(国科大)、`MANUAL_CHZU`(滁州学院)、`u_c654f04a`(湖北职业技术学院)（:77-94）。点卡片时以 `schoolId in FORCE_DESKTOP_MODE_SCHOOL_IDS` 写入 `forceDesktopMode`（:226）。判定按**学校 ID** 而非适配器 ID（:75 注释）。用户能自己切的开关只在 WebView 页内（⋮ 菜单，C2 稿已记）。

**四态**（when 分支，:169-234）：

| 态 | 触发条件 | 呈现 | 依据 |
|---|---|---|---|
| 加载中 | `isLoading == true`（初始 true） | 居中 `ThemedLoadingIndicator` | AdapterSelectionScreen.kt:170-172、112 |
| 错误 | `loadFailed == true`＝`getAdaptersForSchoolAndCategory` 抛异常 | 居中 `AppErrorState(hint=error_load_failed, onRetry={retryKey++})`；`retryKey` 自增重跑 LaunchedEffect | AdapterSelectionScreen.kt:174-180、136-144、115；strings.xml:579、500 |
| 空 | `adapters.isEmpty()` | 居中 `AppEmptyState`「该学校在「%1$s」类别下暂无适配器。」(text_no_adapter_for_category_school)，**无动作按钮、无重试** | AdapterSelectionScreen.kt:181-186；strings.xml:474 |
| 正常 | 有适配器 | LazyColumn + AdapterCard（主题页面边距/卡片间距，key=adapter_id） | AdapterSelectionScreen.kt:187-232 |

**`resourceFolder` 缺失 / 适配器脚本缺失的表现**：本页**不做任何校验**，仍照常渲染卡片并允许点击，只是把 `assetJsPath` 拼成 `"$resourceFolder/x.js"`（`resourceFolder` 为空即拼出 `/x.js`）（:216-220）。真正的失败延后到 WebView 页点「执行导入」时才暴露：路径不存在 → Toast「导入脚本文件不存在: %s」(toast_import_script_not_found, strings.xml:495)（WebViewScreen.kt:377-380）；读文件抛异常 → Toast「加载导入脚本失败: %s」(toast_load_import_script_failed, strings.xml:496)（:384-386）。本页无从得知脚本是否存在，用户也不会在选适配器时看到任何「该适配器不可用」提示。

#### 4. 与上一流程的衔接

- **传参**：适配器页接收 4 项，其中 `schoolId` 只用于强制电脑模式判定（AdapterSelectionScreen.kt:226），`schoolName` 只用于标题（:152），`categoryNumber` 只用于类别反解（:118-120），`resourceFolder` 只用于拼脚本路径（:216-220）。
- **WebView 侧参数**：`initialUrl` 为 null/blank/`about:blank` 时 `startedEmpty=true`，此时才显示地址栏切换钮（WebViewScreen.kt:125-126）；`assetJsPath == null` 时「执行导入」按钮走 Toast「该适配器没有脚本，请手动导入。」(toast_no_script_manual_import, strings.xml:492)（:372-373、390）。
- **返回键行为**：选校页——搜索态优先被 `NavigationBackHandler` 接管，第一次返回只清搜索词与搜索态，不退出本页（SchoolSelectionListScreen.kt:110-119）；顶栏 ← 在搜索态同样只退出搜索态，非搜索态才 `onBack`（:374-381）。适配器页——顶栏 ← 直接 `onBack`（AdapterSelectionScreen.kt:153-159），**回到选校页后可重新选学校**，但搜索词与列表滚动位置已重置（remember 随离开组合树销毁，:105-107）。WebView——`handleBackAction` 优先级为「退出网址编辑 → 页内后退 → onBack」（WebViewScreen.kt:186-201），页内无历史时才回到适配器页，**因此「退回改选学校」需要连按多次返回**。
- **导入完成后是否清栈**：**不清栈**。导入收尾是 push 到课表页或学期设置页（WebViewScreen.kt:170-178；App.kt:477-495），全仓 grep `popUpTo` / `popSelf` **零命中**（仅 `TodayScheduleScreen.kt` 有 pull-to-refresh 相关命中），WebView entry 与适配器/选校 entry 都留在返回栈内；返回键会先退回 WebView 页。

#### 5. 状态矩阵

| 场景 | 触发条件 | 用户看到什么 | 出口动作 | 依据 |
|---|---|---|---|---|
| 选校页加载中 | 进入页面，`isLoading` 初值 true | 全屏加载指示器 | 无（等待） | SchoolSelectionListScreen.kt:223-225；SchoolSelectionViewModel.kt:31、88 |
| 索引文件缺失/损坏 | `school_index.pb` 不存在或 decode 抛异常 | 错误态「初始化失败，请重试」+ 重试钮 | 点「重试」重跑 `loadSchools()` | SchoolRepository.kt:60-63、85-88；SchoolSelectionListScreen.kt:227-233 |
| 索引存在但零学校 | `getSchools()` 返回空（同被判为 loadFailed） | 同上错误态 | 同上 | SchoolSelectionViewModel.kt:93；SchoolSelectionListScreen.kt:227-233 |
| 当前类别无学校 | 该类别下无任何学校带此类别适配器 | 空态「当前类别暂无适配，请选择其他类别或检查数据源。」**无按钮** | 只能手动切分类胶囊 | SchoolSelectionListScreen.kt:234-240；SchoolSelectionViewModel.kt:62-64 |
| 搜索无结果 | 搜索词与 name/initial 均不匹配 | 空态「没有找到匹配的学校」**无按钮** | 用 ✕ 清空搜索词 | SchoolSelectionListScreen.kt:412-416；strings.xml:462 |
| 搜索命中 | 子串匹配 name 或 initial | 顶栏内平铺结果列表（无分组/无索引条） | 点条目进适配器页 | SchoolSelectionListScreen.kt:418-427 |
| 无双击保护 | 500ms 内连点同一学校 | 第二次点击被吞掉 | — | SchoolSelectionListScreen.kt:437-447 |
| 最近访问历史失效 | 历史 `resource_folder` 与索引不一致 | 自动改用索引最新值，用户无感 | — | SchoolSelectionListScreen.kt:247-254 |
| 适配器页加载中 | `isLoading` 初值 true | 居中加载指示器 | 无（等待） | AdapterSelectionScreen.kt:170-172 |
| 适配器加载抛异常 | `getAdaptersForSchoolAndCategory` 异常 | 「初始化失败，请重试」+ 重试钮 | 重试（retryKey++） | AdapterSelectionScreen.kt:139-141、174-180 |
| 该类别无适配器 | 过滤后列表为空 | 「该学校在「本科/专科」类别下暂无适配器。」**无按钮** | 只能返回上一页换类别 | AdapterSelectionScreen.kt:181-186 |
| 适配器脚本缺失 | `repo/schools/resources/<folder>/<js>` 不存在 | 选适配器时**无提示**；点「执行导入」才 Toast「导入脚本文件不存在: 路径」 | 手动导入/返回换适配器 | AdapterSelectionScreen.kt:216-220；WebViewScreen.kt:377-380 |
| 适配器无脚本字段 | `asset_js_path` 与 `adapter_id` 都拼不出有效文件 | 同上，另一个 Toast 分支 | 同上 | AdapterSelectionScreen.kt:213-220 |
| 强制电脑模式学校 | 7 个硬编码学校 ID | 本页**无任何可见标识**；进 WebView 后是桌面 UA + 1280px 视口 | 无 | AdapterSelectionScreen.kt:77-94、226 |
| 导入完成 | 脚本回传成功 | 跳课表页/学期设置页 | **不清栈**，返回键回 WebView | WebViewScreen.kt:170-178；App.kt:477-495 |

#### ⚠️ 缺口与小节结论

1. **适配器页的「错误态」几乎不可达**：`SchoolRepository.getAdaptersForSchool` 用 `school?.adapters ?: emptyList()` 兜底、从不抛异常（SchoolRepository.kt:113-119），索引缺失时用户看到的是「该学校暂无适配器」空态（AdapterSelectionScreen.kt:181-186），把「数据读不到」误报成「这所学校不支持」——误导且无重试入口。
2. **空态一律无出口动作**：选校页两类空态与适配器页空态都只传 `hint`、不传 `actionLabel`/`onAction`（SchoolSelectionListScreen.kt:236-239、413-416；AdapterSelectionScreen.kt:182-185），与 `AppEmptyState` 已支持的「空态闭环」能力（AppBasicComponents.kt:124-125、155-167）不匹配，用户只能自己猜下一步。
3. **选校页无下拉刷新，空/正常态无任何手动刷新入口**：全仓仅今日页用了 `PullToRefreshBox`（TodayScheduleScreen.kt:264），OTA 适配数据热更新后选校页不会主动重载。
4. **适配器页不预检脚本存在性**：卡片上没有任何「可用/缺脚本」标识，失败被推迟到 WebView 的「执行导入」Toast（AdapterSelectionScreen.kt:243-291；WebViewScreen.kt:377-380），用户要先选课表才发现白走一趟——`WebViewScreen` 已注释承认这是「倒置体验」（WebViewScreen.kt:375-376）。
5. **「其他」类别（`category_other` / `ADAPTER_CATEGORY_UNKNOWN`）在 UI 不可达**：`displayCategories` 只列 3 类（SchoolSelectionViewModel.kt:50-54），而 `RELEVANT_MENU_CATEGORIES` 同样只有 3 类（SchoolRepository.kt:39-43），`category_other` 分支与 strings 条目成为死代码（SchoolSelectionListScreen.kt:310-317；strings.xml:469）。
6. **搜索能力弱且结果视图退化**：仅 `name`/`initial` 子串匹配，无拼音全拼、无错别字容错（SchoolSelectionViewModel.kt:66-73）；搜索态结果列表无字母分组与索引条，学校多时无法快速定位（SchoolSelectionListScreen.kt:418-427）。
7. **首字母为多字符时分组键丢失**：排序取 `initial` 全串、分组却只取首字符，索引条按首字符聚合，「ZJ」类缩写学校只会出现在 Z 段，用户按第二字母找不到（SchoolSelectionViewModel.kt:76-79 vs SchoolSelectionListScreen.kt:244）。
8. **「电脑模式」是纯隐式行为，用户零可见性**：7 所学校硬编码在 UI 文件顶部（AdapterSelectionScreen.kt:77-94），列表项与标题都不提示「将以电脑版打开」，学校新增时只能改代码。
9. **适配器列表状态不持久**：`adapters`/`isLoading`/`loadFailed` 均为 `remember`（AdapterSelectionScreen.kt:111-115），配置变更或进程重建后整段重载，刷新期间无「保留旧列表」处理。
10. **导入完成后不清栈**：`popUpTo`/`popSelf` 全仓零命中，WebView entry 常驻返回栈（WebViewScreen.kt:170-178），返回键会退回已完成的 WebView 页而非课表页。
11. **选校页「无数据」与「加载失败」共用错误态**：`_loadFailed.value = schools.isEmpty()`（SchoolSelectionViewModel.kt:93）使真空索引永远显示「初始化失败，请重试」，而该错误态又无「反馈数据源」出口。
12. **`resourceFolder` 无校验链**：选校页传出 `resource_folder`（SchoolSelectionListScreen.kt:147、188），适配器页原样拼路径（AdapterSelectionScreen.kt:216-220），全程无人验证该目录是否存在——历史漂移只靠选校页渲染时「优先用索引最新值」这一处补丁规避（SchoolSelectionListScreen.kt:247-254）。

**小节结论**：本段流程的骨架（Destination 参数透传、四态分流、最近访问、字母索引、强制电脑模式名单）都已落地且有明确实现；主要缺口集中在「失败不可见（脚本预检、索引缺失伪装成空态）」「空态无出口」「电脑模式无 UI 可见性」三处，且完成导入后返回栈残留会把用户带回已经跑完的 WebView 页。


### C4 课程与课表编辑（UX 规格 · 资料员摘录）


路径前缀 shared/src/commonMain/kotlin/com/shangkeschedule/ui/settings/（下称 ui/settings/）；文案 shared/src/commonMain/composeResources/values/strings.xml（下称 strings.xml）。

#### 1. AddEditCourseScreen 表单字段清单
入口 ui/settings/course/AddEditCourseScreen.kt；状态/保存 同目录 AddEditCourseViewModel.kt。

| 字段 | 控件 | 必填 | 校验 | 依据 |
|---|---|---|---|---|
| 课程名称 | AppTextField 单行 | 是 | blank→toast_name_empty「课程名称不能为空」 | AddEditCourseScreen.kt:219-225,184-186; strings.xml:144 |
| 学分 | AppTextField 单行 | 否 | 无格式校验；保存 ifBlank{null} | AddEditCourseScreen.kt:229-235; AddEditCourseViewModel.kt:347 |
| 考核方式 | AppTextField 单行 | 否 | 无 | AddEditCourseScreen.kt:239-245 |
| 实验课 | AppSwitch | 否 | 无 | AddEditCourseScreen.kt:249-262 |
| 教师/地点/备注 | CourseSchemeCard 内文本输入 | 否 | 无 | AddEditCourseScreen.kt:275-283 |
| 颜色 | ColorPickerBottomSheet | 否 | 默认索引 colorIndex | AddEditCourseScreen.kt:351-362 |
| 时间（节次模式） | CourseTimePickerBottomSheet(day+起止节) | 否 | startSection<=endSection | AddEditCourseScreen.kt:377-389 |
| 时间（自定义） | CustomTimeRangePickerBottomSheet | 否 | 起止非空且 start<end；默认 TimeTextUtils 默认值/09:45 | AddEditCourseScreen.kt:366-376 |
| 周次 | WeekSelectorBottomSheet | 否 | 上限=semesterTotalWeeks | AddEditCourseScreen.kt:337-348 |
| 星期（自定义模式） | DayPickerDialog | 否 | 1..7 | AddEditCourseScreen.kt:393-402 |

方案卡片可增删（仅 >1 个显示移除钮 :300-304）；「添加方案」胶囊按钮 :309-328。
保存校验：名称非空 + 所有方案时间合法，否则 toast_time_invalid「开始节次不能大于结束节次」（AddEditCourseScreen.kt:183-197; strings.xml:150）。
⚠️ toast_time_invalid 文案只提节次，但校验还覆盖自定义时间的非空与先后（:187-193），文案与校验范围不一致。

#### 2. 保存 / 删除 / 退出（AddEditCourseScreen.kt）
- 保存：onSave→UiEvent.SaveSuccess→toast_save_success + onBack（:138-152）；学分空存 null（AddEditCourseViewModel.kt:347）。
- 删除：编辑态顶栏垃圾桶（:177-181）→AppDangerDialog「确认删除课程 / 确定要删除课程「%1$s」吗？此操作无法撤销。」（dialog_title/text_confirm_delete_course，confirm_delete）→onDelete（:407-418; strings.xml:851-852）。
- 退出拦截：hasUnsavedChanges()（AddEditCourseViewModel.kt:250-258，比较 name/credit 等 vs 初始值）→AppAlertDialog「放弃更改？」+「当前内容已修改，退出将丢失未保存的数据。」+ 危险色「不保存退出」/「继续编辑」（:117-135,421-444; strings.xml:23-24）。

#### 3. 课程管理两级页（coursemanagement/）
- CourseNameListScreen.kt（一级）：不重名课程两列网格 + 实例数 Badge；FAB 新增（预设 1-2 节经 AddEditCourseChannel）:209-227；点卡片→CourseManagementDetail :262-271；长按进多选 :273-278；多选 = 全选/反选(:148-165) + 删除钮（选中>0 才启用 :168-181）→AppDangerDialog「确定要删除选中的 %1$d 门课程吗？此操作无法撤销。」（dialog_text_confirm_delete_courses）:286-301；空态 text_no_unique_courses_hint :237-242；页头快捷入口：课程调动→TweakSchedule、快速删除→QuickDelete :368-391。
- CourseInstanceListScreen.kt（二级）：同名课程实例网格，卡片=教师/地点/时间（自定义或节次）/周次 :269-318；点卡片→编辑 :197-203；长按多选 :204-207；FAB 新增同名预设 1-2 节 :102-112；批删二次确认 dialog_text_confirm_delete_courses(选中数) :213-225。

#### 4. 快捷操作（quickactions/）
- QuickDeleteScreen.kt（快速删除）：两筛选维度——周次+星期（FilterBottomSheet FilterChip）与日期范围（DateRangePickerModal）:190-265；预览「预计从课表中移除 %1$d 节课程记录」（hint_affected_count，危险色）或 hint_no_selection :268-278（strings.xml:572-573）；底部危险色删除钮（有受影响记录才出现，isLoading 禁用防重复触发 :154-177）→AppDangerDialog「此操作不可撤销。受影响的课程记录将从选定的时间点中抹除，但课程的基础信息仍会保留。」（dialog_delete_confirm_msg）:296-310。
  ⚠️ 周次筛选硬编码 1..20（QuickDeleteScreen.kt:371），未跟随学期总周数。
- TweakScheduleScreen.kt（课程调动）：选目标课表（:175-186）+ 从/至日期（:189-197）+ 三模式 MERGE/OVERWRITE/EXCHANGE（:332-334）+ From/To 课程卡（:218-267）；顶栏 ✓→AppDangerDialog「确认调课 / 确定执行调课吗？将影响 %1$d 门课程，此操作无法撤销。」（dialog_title/text_confirm_tweak）→moveCourses :276-287（strings.xml:854-855）。

#### 5. 转换枢纽页（conversion/CourseTableConversionScreen.kt）
- 三组入口：文件转换 = 文件导入 Hub / 导出 JSON / 导出 ICS（:186-205）；学校导入 = 教务系统 / 文本粘贴→TextImportHub（:209-222）；同步 = 同步系统日历 / 备份恢复→Backup（:226-239）。
- 导出：JSON 写 cacheDir/share_temp 后走系统分享（:117-128）；ICS 文件名 ShangKe_<epochMillis>.ics、MIME text/calendar（:129-143）；isLoading→顶部 LinearProgressIndicator（:166-172）；消息走 snackbar（:144-146）。
- 本页全部为非破坏性入口，无删除/覆盖确认（符合预期）。

#### 6. 二次确认台账（本文件范围）
存在 5：单课删除（§2）、课程名批删（§3）、实例批删（§3）、快速删除（§4）、调课执行（§4）。范围内破坏性操作均有确认，缺失 0。


### C5 文件与文本导入（UX 规格 · 资料员摘录）


目录 shared/src/commonMain/kotlin/com/shangkeschedule/ui/settings/import/（下称 import/）；文案 shared/src/commonMain/composeResources/values/strings.xml。六个 Screen 中三个内容页共用 TextImportViewModel.kt。

#### 1. 入口与支持格式
| Screen | 入口 | 支持格式/动作 | 依据 |
|---|---|---|---|
| FileImportHubScreen | Conversion→import_file_hub_title | 分类导航：Excel→ExcelImport / JSON→JsonFileImport / ICS→TextFileImport("ICS") / CSV→TextFileImport("CSV") / 文本→TextFileImport("AUTO") + 底部提示 import_file_hub_hint | FileImportHubScreen.kt:86-123 |
| ExcelImportScreen | Hub→import_cat_excel | 文件选择 xlsx（importFile(listOf("xlsx"))）→parseFileBytes | ExcelImportScreen.kt:106-112 |
| JsonFileImportScreen | Hub→import_cat_json | 两步：选目标课表（import_json_step1）→选 .json 导入；覆盖所选课表课程数据 | JsonFileImportScreen.kt:63-64,133-166 |
| TextFileImportScreen | Hub→分类 | 扩展名按格式：ICS ics/ical/ifb、CSV csv、HTML html/htm、JSON json、AUTO csv/ics/html/txt（AUTO 明确不收 json） | TextFileImportScreen.kt:68-74 |
| TextImportHubScreen | Conversion→import_cat_text_paste | 分类：WAKEUP/PLAIN/JSON/CSV/ICS→TextImportFormatPage + 提示 import_text_hub_hint | TextImportHubScreen.kt:74-111 |
| TextImportScreen | 文本分类页（format 参数） | 粘贴文本→解析预览→命名→导入；null=自动嗅探 | TextImportScreen.kt:46-59,107 |

#### 2. 共用流程（Text/Excel/TextFile 三屏一致）
选文件/输入→解析（Loading）→ImportPreviewSection 预览（P1-6 可编辑，onCoursesChanged=updateParsedCourses 回写 VM，保证预览与导入一致）→ImportDestinationForm（另存新课表：默认表名=文件名去扩展名 take(20)；或覆盖已有课表）→成功 toast import_toast_success + reset + onImportSuccess(tableId)。
依据：ExcelImportScreen.kt:147-183；TextImportScreen.kt:129-163；TextImportViewModel.kt:185-235；ImportPreviewComponents.kt:88。

#### 3. 状态矩阵（解析失败/空结果/部分成功）
| 状态 | 表现 | 文案键 | 出口 | 依据 |
|---|---|---|---|---|
| 空输入（粘贴） | 红字 error | tivm_error_empty_input | 留在页面 | TextImportViewModel.kt:64-69 |
| 空文件（0 字节） | 红字 error | tivm_error_empty_file | 留在页面 | TextImportViewModel.kt:86-91,141-144 |
| 旧版二进制 .xls | 解析失败红字（提示转换） | tivm_error_old_xls | 留在页面 | TextImportViewModel.kt:99-104 |
| 非 UTF-8（含 U+FFFD） | 解析失败红字 | tivm_error_not_utf8_guidance（文件）/ tivm_error_not_utf8（JSON） | 留在页面 | TextImportViewModel.kt:114-118,147-149 |
| 解析异常 | 红字「导入失败:%s」 | tivm_import_failed_fmt | 留在页面 | TextImportViewModel.kt:122-124,173-174,209 |
| 解析失败（格式不识别） | 红字 result.message，parseResult=null | UniversalScheduleParser 消息 | 留在页面 | TextImportViewModel.kt:245-250 |
| 解析中 | 整段加载态（仅 parseResult==null 时）/按钮禁用 | import_status_parsing（Excel/文件）、import_status_importing（JSON）、import_status_processing（预览表单） | — | ExcelImportScreen.kt:137-144; JsonFileImportScreen.kt:168-175; ImportPreviewComponents.kt:398-430 |
| 成功 | toast + reset + 导航回调 | import_toast_success | onImportSuccess(tableId) | TextImportScreen.kt:144-161 |
| 空结果（0 门课） | ⚠️ 未拦截：applyParseResult 不检查 courses.isEmpty，空预览照常渲染可继续导入 | — | — | TextImportViewModel.kt:237-252 |
| 部分成功 | ⚠️ 导入无部分成功概念；JSON 导入失败整体失败（本 App 格式严格解析→失败回退 WakeUp/通用 JSON→再失败 tivm_json_parse_failed） | tivm_json_parse_failed | 留在页面 | TextImportViewModel.kt:146-177 |
| 未选目标课表（JSON） | toast | import_error_no_table | 留在页面 | JsonFileImportScreen.kt:94-95 |
| 文件取消/空回调 | toast | import_error_no_file | 留在页面 | ExcelImportScreen.kt:71-73; TextFileImportScreen.kt:86-89 |
| 检测到格式 | 主色小字 | import_detected_fmt | — | TextImportScreen.kt:113-119 |

#### 4. 覆盖确认
- JSON 文件导入有二次确认：AppDangerDialog「确认覆盖导入 / 导入将覆盖课表「%1$s」的全部课程数据，此操作无法撤销。确定继续吗？」（dialog_title/text_confirm_overwrite_import，action_overwrite_import）：JsonFileImportScreen.kt:97-99,196-216; strings.xml:847-849。
- ⚠️ 缺失：Excel/文本文件/粘贴三屏的「覆盖已有课表」走 importToExistingTable 直接入库、无确认弹窗（ImportPreviewComponents.kt:341-430 表单无 dialog；TextImportViewModel.kt:217-235）。

#### 5. 二次确认台账：存在 1（JSON 覆盖导入）；缺失 3 入口（Excel/文本文件/粘贴覆盖已有表，共用同一表单路径）。


### C6 备份与恢复（UX 规格 · 资料员摘录）


目录 shared/src/commonMain/kotlin/com/shangkeschedule/ui/settings/backup/；文案 shared/src/commonMain/composeResources/values/strings.xml。

#### 1. 通道清单（grep 核实）
BackupScreen.kt 仅两通道：enum BackupTarget { WEBDAV(backup_target_webdav), LOCAL_ZIP(backup_target_local_zip) }（BackupScreen.kt:111-114）。
- 本地 ZIP：备份 = exportToLocalZip → ShangKe_Backup_yyyyMMdd.zip → 系统导出（:320-340）；恢复 = importFile(listOf("zip")) → importFromLocalZip（:142-153,380）。
- WebDAV：备份 backupToWebDav / 恢复 restoreFromWebDav（BackupViewModel.kt:194,213）；配置对话框 = URL/账号/密码（label_webdav_pwd_saved|pwd_empty，密码掩码）/根路径（desc_webdav_path_hint）（BackupScreen.kt:481-547）；自动同步开关未配置时禁用（:260-284）；保存即测试 testWebDavConnection（:294-297）。
- ICS 导出：未实现于本页——挂在 conversion/CourseTableConversionScreen.kt:200-204（item_export_ics_file，文件名 ShangKe_<epochMillis>.ics :129-143）。
- 系统日历同步：未实现于本页——同文件 :228-232（item_sync_to_system_calendar→requestCalendarSync）。
⚠️ 「备份页含 ICS/系统日历通道」不成立：两者挂在转换枢纽页，BackupScreen 无相关键（grep 其 import 段 :55-105 无 ics/calendar）。

#### 2. 页面结构（BackupScreen.kt）
- 数据维护组：备份 item_backup_data/desc_backup_data、恢复 item_restore_data/desc_restore_data，isBusy 时禁用（:222-242）。
- 服务配置组：WebDAV 配置（副标题 = 未配置 desc_webdav_unconfigured / 已连接 desc_webdav_connected(baseUrl) :245-254）；自动同步三态副标题（未配置 / 已启用 / 已禁用 :260-284）。
- 进行中：isBusy||isTesting→顶部 LinearProgressIndicator（:218-220）。

#### 3. 状态矩阵（按通道，SnackBars）
| 状态 | 表现 | 文案键 | 依据 |
|---|---|---|---|
| 进行中（确认钮） | 「测试中…」/加载态 | title_loading（action 确认钮替换） | BackupScreen.kt:534-537 |
| 成功 | Snackbar 短时 | toast_operation_success | BackupScreen.kt:178-183 |
| 失败 | Snackbar「操作失败:」+message | toast_operation_failed + 具体错误 | BackupScreen.kt:165-170 |
| 部分成功（恢复缺模块） | Snackbar 长时「恢复完成，但以下模块缺失：%1$s」 | backup_warn_restore_partial | BackupScreen.kt:171-177; BackupViewModel.kt:224-277; strings.xml:869 |
| WebDAV 连接失败 | TestResult.Error | backup_err_connect_failed | BackupViewModel.kt:156 |
| WebDAV 未配置 | 选择备份/恢复时 Snackbar；自动同步拒绝开启 | error_webdav_unconfigured | BackupScreen.kt:314-318,354-358; BackupViewModel.kt:177-184,218 |
| WebDAV 上传失败 | TestResult.Error+message | backup_err_upload_failed | BackupViewModel.kt:203 |
| 包损坏/空/本地导出失败 | 对应错误 | backup_err_corrupted / backup_err_empty / backup_err_local_export_failed | BackupViewModel.kt:230,306,322-334,363 |
| 恢复失败 | 前缀+异常信息 | backup_err_restore_failed_prefix | BackupViewModel.kt:275,422 |
| 文件流打开失败 | Snackbar | error_stream_open_failed | BackupScreen.kt:134,150,157,336 |
| 无网 | ⚠️ 无专用无网检测/文案键，断网归入 backup_err_connect_failed | （同上） | grep backup/ 无 offline/network 键 |
| 权限缺失 | ⚠️ 未实现：本地 ZIP 走系统文件选择器 rememberFileManager，无存储权限请求分支 | — | BackupScreen.kt:140-161 |

TestResult 定义：sealed interface { Idle / Success / PartialSuccess(message) / Error(message) }（BackupViewModel.kt:71-76）。

#### 4. 恢复覆盖确认与解绑确认
- 目标选择：备份 TargetSelectionDialog「选择备份目标渠道」（dialog_title_backup_target）/ 恢复「选择恢复数据来源」（dialog_title_restore_source），AppAlertDialog+单选行（:442-478; strings.xml:701-702）。
- 恢复二次确认（WebDAV/本地共用 1 处）：AppDangerDialog「确认恢复数据 / 恢复数据将覆盖当前课表、样式与设置，此操作无法撤销。确定要恢复吗？」（dialog_title/text_confirm_restore，action_restore「确认恢复」）→确认后才执行 restoreFromWebDav 或打开文件选择器（BackupScreen.kt:346-384; strings.xml:842-844）。
- WebDAV 解绑二次确认：配置弹窗灰字 action_reset 触发（:538-543）→AppDangerDialog「解除 WebDAV 绑定 / 将清除已保存的 WebDAV 账号与密码，此操作无法撤销。确定要解除绑定吗？」（dialog_title/text_confirm_webdav_disconnect，action_confirm）→disconnectWebDav（:298-302,386-399; strings.xml:845-846）。

#### 5. 二次确认台账：存在 2（恢复覆盖、WebDAV 解绑）；缺失 0（备份/导出为非破坏性，无需确认）。


### C7 规格梳理：学期设置与节次时间管理


依据文件：`shared/src/commonMain/kotlin/com/shangkeschedule/ui/settings/SemesterSettingsScreen.kt`（244 行）、
`shared/src/commonMain/kotlin/com/shangkeschedule/ui/settings/time/TimeSlotManagementScreen.kt`（1593 行）、
`ui/settings/AppSettingsViewModel.kt`（456 行）、`ui/settings/time/TimeSlotViewModel.kt`。
行号均指上述文件，除非另注。

#### 1. 页面与入口
- 学期设置页 `SemesterSettingsScreen(onBack, viewModel: SettingsViewModel = koinViewModel())` :67；入口在设置页（item 学期设置）。
- 节次管理页 `TimeSlotManagementScreen(onBack, targetCourseTableId: String? = null, timeSlotViewModel)` :176-180；`LaunchedEffect(targetCourseTableId){initWithTargetTable}` :184-186——从情侣课表等入口可携带目标课表 id，读写均指向该表。
- TimeSlotViewModel 表目标解析：`resolveEffectiveTableId() = targetTableId.value ?: 当前课表` :46-49（注释：显式目标>当前表，读写路径统一防跨表误写）。

#### 2. 学期设置（表单与校验）
- 加载门控：`!uiState.isReady` → 仅顶栏空白页 :74-93（无骨架屏）。
- 数据默认值：总周数 `?: 20` :99；每周起始 `?: MONDAY.isoDayNumber` :100；开学日期字符串 `LocalDate.parse` try-catch 失败→null :102-110。
- 单个 SectionCard 四条目 :146-198：
  1. 开学日期 → DatePickerModal :147-161、:203-210，trailing=格式化日期或 `status_not_set`；
  2. 总周数 → NumberPickerDialog，范围 **1..30** :163-171、:211-222；
  3. 当前周数 → ManualWeekPickerDialog :173-184、:223-233；trailing 三态：开学日期未设→`status_set_start_date_first`；按日期算不出周→`title_vacation`（假期）；否则 `status_current_week_format`；
  4. 每周起始日 → DayOfWeekPickerDialog（周一/周日二选）:186-197、:234-243。
- 写入（AppSettingsViewModel :174-217）：开学日期→`Instant→LocalDate→config.copy(semesterStartDate)`→`insertOrUpdateCourseConfig` :174-186；总周数/起始日→copy 后入库 :191-197、:211-217；**手动设当前周→`setSemesterStartDateFromWeek(week)` 反向推算开学日期** :202-206。
- 校验：无显式错误分支（⚠️ 无输入错误 toast）；日期受 DatePicker 约束、周数受 1..30 滚轮约束。配置存于 `CourseTableConfig`（按课表隔离）。

#### 3. 节次管理（增删改与校验）
- 本地草稿模型：编辑先落 `localTimeSlots`/默认时长草稿 :188-192；回填仅在 `(isDataLoaded, currentSchemeId)` 变化时 :220-227（注释：key 收窄防草稿被无关发射清空）。
- 未保存返回拦截：`hasUnsavedChanges` → 退出确认弹窗（危险色"不保存退出"+灰字"继续编辑"）:232-243、:504-524。
- 顶栏保存 :280-304：按 `startTime` 排序 + `mapIndexed` 重编号→`onSaveAllSettings`→`toast_settings_saved`；保存钮 `enabled = isDataLoaded && localTimeSlots.isNotEmpty()` :297-301（注释：空列表保护，防方案被清空仍提示已保存）。
- 作息方案：选择器（切换/新建/删除/生效日期）:325-336；自动切换开关 :338-341；自动切换开启且当日生效方案≠编辑方案→error 色提示 :357-373。
  - 新建方案校验：空名→`toast_scheme_name_empty`，重名→`toast_scheme_name_duplicate`（VM :208-230 错误码 empty/duplicate）。
  - 删除方案→危险确认弹窗 :527-542。
  - 生效日期弹窗 :1416-1527：默认 3-1→10-1 :1439-1442；起始>结束显示跨年提示 :1490-1498；可清除（null,null）:1499-1504；月/日不完整→`toast_scheme_dates_incomplete` :1512-1515。
- 节次新增（底部弹窗 :424-502）：初始时间=末节结束+课间→开始、+上课时长→结束；空表 08:00 :776-795；编号=最大+1 :445。
- 节次编辑：**结束时间变化→后续节次整体顺延**（上节结束+课间→开始，+上课时长→结束，钳 23:59）:464-481；确认后统一排序+重编号 :490-495。
- 节次删除：移除+重排+`toast_slot_removed_unsaved` :387-395；恢复默认→`DEFAULT_TIME_SLOTS` 模板（保存后生效）:399-420。
- 时间合法性（重叠校验，TimeSlotEditContent :1060-1319）：
  - `minAllowedTime` = 上一节结束时间，否则 00:00 :1079-1085；
  - `maxAllowedTime`：编辑模式=23:59（注释：顺延机制取代旧"不得侵占下节"钳制）；新增=下一节开始时间，否则 23:59 :1087-1095；
  - 确认校验：①结束≤开始→`toast_end_time_must_be_later` :1294-1298；②越界（start<min || end>max）→`toast_time_conflict` :1300-1304；
  - 别名≤5 字带计数 :1139-1154；时/分原生滚轮 :1214-1270。
- 默认时长校验 :802-863：上课时长须>0（空串=0 哨兵，≤0→`toast_class_duration_positive`）；课间须≥0（-1 哨兵，<0→`toast_break_duration_non_negative`）。
- 保存落库（TimeSlotViewModel :147-188）：`resolveEffectiveTableId`→表存在校验→slots 绑定 `courseTableId/currentSchemeId`→`saveSchemeSettings`（原子，含新时长 config）→更新备份点→onSuccess。⚠️ 230 行后（onDeleteScheme/onToggleAutoSwitch/onSaveSchemeDates/resolveActiveSchemeId）未逐行核对。

#### 4. 修改后的即时影响
- 学期配置按课表存于 `CourseTableConfig`，全链路 Flow 驱动：课表页按 `semesterStartDate/firstDayOfWeek/totalWeeks` 实时重算当前周与周次过滤（WeeklyScheduleViewModel.kt:372-387：`pageWeekNum=getWeekIndexAtDate(...)`、`isWithinSemester = pageWeekNum in 1..totalWeeks`）→ 改完返回即生效，无手动刷新。
- 手动设当前周会反向改写开学日期（AppSettingsViewModel.kt:202-206）——开学日期与当前周联动，非独立字段。
- 节次保存为原子事务（TimeSlotViewModel.kt:175-180），课表网格消费 `timeSlotsFlow`（WeeklyScheduleViewModel.kt:258-263）响应式重排；今日页同样按节次时间重排。⚠️ 今日页对节次变更的具体重排逻辑未逐行核对。

#### 5. 状态矩阵
| 状态 | 学期设置 | 节次管理 |
|---|---|---|
| 加载 | `!isReady`→顶栏空白页 :74-93 | `isDataLoaded=false`→保存禁用，草稿回填挂起 :297-301、:220-227 |
| 正常 | 四条目表单 | 列表+方案选择器+默认时长 |
| 空 | 无空态（默认值兜底 :99-100） | `localTimeSlots.isEmpty()`→`AppEmptyState(text_no_time_slots_hint)` :350-353 |
| 错误 | ⚠️ 无错误分支（解析失败静默 null :102-110） | ⚠️ 无全局错误态；方案/保存错误仅 toast |
| 校验失败 | 无（控件约束代替） | 结束≤开始/越界/时长非法/方案名非法/日期不完整→对应 toast（见 §3） |


### C8 规格梳理：情侣课表


依据文件：`shared/src/commonMain/kotlin/com/shangkeschedule/ui/settings/CoupleScheduleSettingsScreen.kt`（438 行）、
`ui/settings/CoupleScheduleViewModel.kt`（211 行）；另引 `CourseTableRepository.kt`、`WeeklyScheduleViewModel.kt`、
`AddEditCourseViewModel.kt` 等交叉证据。行号均指上述文件，除非另注。

#### 1. 模型与"绑定"流程（核心澄清）
- 情侣课表=**与本人课表并排管理的独立 CourseTable**（`isCouple=true`，自有课程/作息/学期配置）——页面文档注释 :100-107；`CourseTable.kt:19` 同注释。
- ⚠️ 待确认级别澄清：**无输入码、无扫码、无网络配对**。"绑定"=本地创建配对课表：创建条目→`viewModel.createCoupleTable()`→toast_couple_created :190-202；VM `createCoupleTable()`→`repository.createCoupleTable(selfId)`（幂等，CourseTableRepository.kt:152 `isCouple=true`）:144-148。
- 配对关系字段：`pairedCourseTableId`（CourseTable.kt:19 邻近字段；DatabaseMigrations.kt:325-369 列迁移）；查询 `getCoupleTableFor(selfTableId)`（CourseTableDao.kt:83,89，LIMIT 1——一人最多一张情侣表）。
- 状态矩阵（页面门控）：
  - 未创建：`coupleTable==null` → 仅显示"创建情侣课表"条目 :190-202；
  - 已创建：表名行 :205-208；查看（切到情侣表单独显示，副标题显示课程数）:209-221；重命名（空名→toast_name_empty，成功→toast_couple_renamed）:223-228、:381-416；作息设置→`Destination.TimeSlotSettings(targetCourseTableId=coupleTable.id)` :229-237；删除→危险确认 :238-245、:419-437；
  - 加载中：`!uiState.isReady` → 门控空白 :134-153；
  - 绑定中：⚠️ 无该状态（创建为同步本地操作，无 loading/进度 UI）；
  - 对方课程加载失败：⚠️ 无显式错误态——VM 各 Flow 静默降级 null/0（CoupleScheduleViewModel.kt:69-108，无 error 字段）。

#### 2. 显示与叠加规则
- UI 状态 `CoupleManageUiState`：coupleScheduleEnabled（双人同显）、coupleShowTimeRanges（显示时间段）、selfCourseColorIndex=0、crushCourseColorIndex=1、courseColorMaps（:29-46）。
- 「双人同显」开关 :281-285：`checked = coupleScheduleEnabled && !单独显示 && 已创建`；`enabled = !单独显示 && 已创建`。副标题三态：单独显示→`couple_overlay_disabled_solo`；未创建→`couple_manage_not_created`；否则 `couple_overlay_desc` :275-279。
- 开关失效保护：当前就在看情侣表时 `onCoupleScheduleEnabledChanged` 直接 return（VM :114-123）。
- 叠加显示规则（WeeklyScheduleViewModel.kt）：
  - 仅当 **当前显示的是本人课表** 且开关开：`currentTable.takeIf { !it.isCouple }` + `getCoupleTableFor(self.id)` → `CoupleOverlayContext(active, coupleTableId, coupleTimeSlots, timesDiffer=双方作息时间是否不同, showTimeRanges)` :281-315；
  - 单独显示情侣表时不叠加（本人课才是叠加宿主）；今日页同样受 `coupleScheduleEnabled` 门控（TodayScheduleViewModel.kt:66、AgendaViewModel.kt:126）；
  - 课程颜色区分：本人=色 index `selfCourseColorIndex`，TA=`crushCourseColorIndex`（CoupleScheduleSettingsScreen :298-318 颜色选择器；WeeklyScheduleViewModel.kt:326-336 CourseSourceKey 携带双色 index）；⚠️ 重叠课程的具体渲染样式（同格冲突如何呈现）在 WeeklyScheduleViewModel 388 行后的 merge 段，未逐行核对；
  - 作息不同（timesDiffer）时按「显示时间段」偏好决定是否展示双方节次时间段标签。
- 单独显示：`switchToCouple()`（currentCourseTableId=couple.id，:167-171）→顶部横幅 `couple_solo_banner`+"返回本人"→`switchToSelf()`（经 pairedCourseTableId 回跳，:174-180）:249-268。

#### 3. 解除绑定（=删除情侣课表）
- 入口：删除条目→`AppDangerDialog`（couple_delete_confirm_message，文案明确删除的是 TA 的整张课表）:238-245、:419-437。
- 执行：`deleteCoupleTable()`→`deleteCourseTableAndResolveCurrent` :161-164；**仅删除成功才 `resetOverlaySwitch()`（关闭 coupleScheduleEnabled）** :203-205；失败 toast_couple_delete_failed。
- 仓储层兜底：删除情侣表时若开关开着→settings 复位 `coupleScheduleEnabled=false`（CourseTableRepository.kt:303-335）。
- ⚠️ 无"解绑但保留课表"的中间态：解绑=删表，不可恢复（备份/恢复保留 isCouple 与配对字段，BackupRepository.kt:290,362,409）。

#### 4. 叠加模式下的编辑行为（targetCourseTableId 语义）
- `AddEditCourseViewModel`：`initWithId(courseId, targetCourseTableId)`→`_targetTableId` :80-84；`effectiveTableId = _targetTableId` :117——**非空=显式指定写入目标课表；空=跟随当前选中课表**。
- 该 id 驱动：已用颜色查询 :125、课程表 config 流 :137-140、已有课程查询 :150、保存目标 `currentCourseTableId=effectiveTableId` :220。
- 路由链：`Navigation.kt:38`（TimeSlotSettings）/`:103`（AddEditCourse）两个目的地带默认 null 参数；`App.kt:423,470` 透传。情侣课表页「作息设置」显式携带 `coupleTable.id`（CoupleScheduleSettingsScreen :229-237）；TimeSlotViewModel 同构解析 `target?:当前课表`（TimeSlotViewModel.kt:46-49）。
- 结论：从情侣课表单独显示进入课程编辑（targetCourseTableId=null，跟随当前=情侣表）或从设置页携带情侣表 id 进入，课程/作息都写入情侣表而非本人表；防跨表误写。⚠️ 叠加模式（看本人表+开关开）下点 TA 课程格子是否可进入编辑，未在本页与 AddEditCourse 中找到证据。

#### 5. 其他入口与持久化
- 导入：无情侣表→toast `couple_need_create_first`；否则教务导入→`Destination.SchoolSelectionListScreen`、文件导入→`Destination.FileImportHub` :322-350（导入流程与主课表复用，目标课表在导入流程内选择）。
- 课表管理页：情侣表单列展示、与本人表配对归组（ManageCourseTablesViewModel.kt:77-86）。
- 备份/恢复：保留 isCouple、pairedCourseTableId、coupleScheduleEnabled（BackupRepository.kt:290,362,409,530,615）。
- 本页入口：设置页 item（SettingsScreen.kt:689，PINK 色 favorite 图标）；路由 App.kt:425。

#### 6. 状态矩阵汇总
| 状态 | 依据 | 表现 |
|---|---|---|
| 未创建 | :190-202 | 仅"创建情侣课表"条目+导入卡禁用（toast 提示先创建） |
| 已创建 | :205-245 | 表名/查看/重命名/作息设置/删除 + 显示设置卡 + 导入卡 |
| 单独显示 | :156,:249-268 | 顶部横幅；双人同显开关禁用；"查看"条目隐藏 |
| 绑定中 | ⚠️ | 无（本地同步创建，无 loading 态） |
| 对方课程加载失败 | ⚠️ | 无错误态，Flow 静默降级（课程数=0、叠加为空） |
| 删除确认 | :419-437 | 危险弹窗；成功→重置当前表+关叠加；失败→toast |


### C9 规格梳理：意见反馈与更多选项


依据文件：`shared/src/commonMain/kotlin/com/shangkeschedule/ui/settings/additional/MoreOptionsScreen.kt`（289 行）+ strings.xml（values）。
⚠️ 待确认级别澄清（文件定位）：任务指定的三个文件**均不存在于仓库**——
- `ui/feedback/FeedbackScreen.kt`：glob/grep 全库无 Feedback 页面（"feedback"仅命中触感反馈 ThemeIndication.kt:31、AppHaptics.kt:5-42 等）；
- `tool/AppExternalLinks.kt`：glob 空；外链常量内联在 MoreOptionsScreen.kt:79-81；
- `tool/ClipboardHelper.kt`：glob 空，且全库 `.kt` grep `Clipboard|clipboard` **零命中**（无任何剪贴板 API 使用）。
反馈入口实际内联在 MoreOptionsScreen「联系作者反馈」卡片 :246-268。

#### 1. 反馈三出口：实际行为
- 入口与导航：设置页 item_more_options（SettingsScreen.kt:717，BROWN）→ Navigation.kt:46 → App.kt:430；`MoreOptionsScreen(onNavigate, onBack, viewModel: SettingsViewModel = koinViewModel())` :85。
- 统一外链方式：`LocalUriHandler.current` :91；`openUri` 仅 3 处调用（:209 官网、:215 GitHub、:253 mailto）；**无 try/catch、无平台分支、无剪贴板兜底**——openUri 失败行为未捕获（⚠️ 无无效链接处理，依赖系统 dispatch）。
- 出口①邮件（联系作者反馈卡）:246-268：`openUri("mailto:hhixingchen520@163.com")` :253；邮箱同时以纯文本展示 :257（contact_author_email，strings.xml:723）；**无复制动作**（Text 无任何 onClick 复制）；附文案 contact_author_hint（strings.xml:724：适配教务需附学校名/账号/密码）。⚠️ mailto 实际跳转由系统处理（Android 拉起邮件应用选择器 / 桌面交由 OS），仓库内无平台自定义 dispatch，具体各平台行为未验证。
- 出口②GitHub：独立设置条目 item_github_repo（strings.xml:425）→ `openUri("https://github.com/qiqqqqq517/shangkeschedule")` :212-216——**打开仓库首页而非 issue/反馈模板**（GITHUB_REPO_URL :79）。
- 出口③复制：**反馈场景不存在复制出口**（⚠️ 与任务假设不符）。字符串 action_copy（strings.xml:898）仅用于 ManageCourseTablesScreen.kt:118,1343 的"复制为新学期"（课表复制），与反馈无关。
- 剪贴板兜底：⚠️ 不存在——全库无剪贴板 API（见文件定位）。
- 不采集标识符声明：⚠️ **仓库中不存在任何隐私声明/采集声明文案**——strings.xml grep `隐私|采集|标识|数据收集|privacy|Privacy` 零命中；MoreOptionsScreen 亦无相关条目。反馈卡 hint 只引导用户提供教务账号密码（strings.xml:724），⚠️ 无"不会存储密码"承诺文案。

#### 2. MoreOptions 各条目去向（自上而下）
| 条目 | 去向 | 依据 |
|---|---|---|
| 动态图标头部（开发者模式隐藏触发） | 连续点击触发开发者模式：`onDeveloperModeChanged(true)` | :157-160 |
| 开发者模式设置项 | 展开开发者选项（开发者模式开启后可见） | :178-182 |
| 语言设置 | `Destination.LanguageSettings`（页内导航） | :188-192 |
| 启动页 | 就地弹窗 `StartScreenSelectionDialog`→`onStartScreenChanged` | :194-203、:279-287 |
| 上课官网 | `openUri("https://shangke.asia")`，显示名 shangke.asia | :205-210、:80-81、strings.xml:426 |
| GitHub 仓库 | `openUri(GITHUB_REPO_URL)`（仓库首页） | :212-216、:79 |
| 开源许可证 | `Destination.OpenSourceLicenses` | :218-222 |
| 自动同步教务 | 触发 `triggerAdapterSync`（适配器远程同步），subtitle 三态：syncing/结果文案/`desc_auto_sync_adapter`；结果映射 Updated(fileCount)→sync_status_updated、UpToDate→up_to_date、Disabled→disabled、VerificationFailed/Failed→adapter_remote_update_failed/sync_status_failed | :105-123、:228-241 |
| 联系作者反馈 | mailto（见 §1 出口①） | :246-268 |
| 致谢 | `AcknowledgmentContent()`（页内内容块） | :271 |
- 版本号展示：`koinInject(named("AppVersionName"))` :94（⚠️ 具体展示位置/条目未单独核对，与 About 区相关）。

#### 3. 行为要点与风险
- 所有外链共用 uriHandler，静默失败：无效 URL/无邮件应用时无降级提示（:91,:253 无 try-catch）。
- 自动同步是本页唯一带异步状态反馈的条目（syncing→结果文案 :105-123）；其余条目为即时导航/外链。
- GitHub 条目与反馈卡互相独立：issue 反馈需用户自行从仓库首页进入 Issues。
- 隐私与合规：⚠️ 无隐私政策入口、无数据采集声明——与「不采集标识符」预期不符，建议在规格评审中确认为缺失项。

#### 4. 状态矩阵
| 状态 | 依据 | 表现 |
|---|---|---|
| 正常 | :85-289 | 各条目可点，外链 dispatch 系统 |
| 同步中 | :105-123 | 自动同步条目 subtitle=syncing（进度文案） |
| 同步结果 | :105-123 | Updated/UpToDate/Disabled/VerificationFailed/Failed→五种文案 |
| 外链失败 | ⚠️ | 无处理：无 toast/无剪贴板兜底/无平台分支 |
| 邮件客户端缺失 | ⚠️ | mailto dispatch 失败行为系统决定，应用内无反馈 |
| 反馈内容校验 | — | 无（非表单式反馈，无输入框） |


---

## 第四部分　公共组件与状态规范


### D · 跨页面公共组件状态盘点（加载 / 空白 / 错误 / 反馈）


- 范围：`shared/src/commonMain/kotlin/com/shangkeschedule/ui/components/`（下称 `components/`）全部 14 个 .kt，公共组件约 40 个；引用数据来自全仓库 `*.kt` grep（build 目录已排除）。页面路径省略前缀 `shared/src/commonMain/kotlin/com/shangkeschedule/`。
- 判定基线：项目自有规范 X2(v3.69.3) 三态收敛——空 → AppEmptyState、加载 → AppLoading、错误 → AppErrorState。

#### 表1 · 组件清单（组件 | 定义处 | 用途 | 被引用处 · 前 5）

| 组件 | 定义（components/ 内） | 用途 | 被引用（前 5） |
|---|---|---|---|
| AppLoading | AppBasicComponents.kt:389 | 全屏居中统一加载态 | ui/today/TodayScheduleScreen.kt:262、ui/agenda/AgendaScreen.kt:254、ui/schoolselection/list/SchoolSelectionListScreen.kt:224、App.kt:126 |
| ThemedLoadingIndicator | AppBasicComponents.kt:238 | 主题三变体加载指示器（SOFT 呼吸圆点 / CLAUDE 墨点 / iOS 八段环） | ui/schoolselection/web/WebViewScreen.kt:453、ui/settings/import/ImportPreviewComponents.kt:403,428、ui/settings/notification/AdvancedSettingsCard.kt:76、ui/settings/import/ExcelImportScreen.kt:140、TextImportScreen.kt:111（含 AppLoading 全仓 17 处调用） |
| AppEmptyState | AppBasicComponents.kt:128 | 空态胶囊提示（可选 actionLabel 动作钮） | TodayScheduleScreen.kt:873、AgendaScreen.kt:393、ui/settings/time/TimeSlotManagementScreen.kt:352、TweakScheduleScreen.kt:350、ManageCourseTablesScreen.kt:557（全仓 11 处调用） |
| AppErrorState | AppBasicComponents.kt:183 | 错误态提示 + 可选重试钮（Res.string.action_retry） | SchoolSelectionListScreen.kt:228（onRetry=viewModel::retryLoad）、AdapterSelectionScreen.kt:175（onRetry=retryKey++）——全仓仅此 2 处 |
| AppAlertDialog | AppBasicComponents.kt:545 | M3 AlertDialog 薄包装（同名同默认值，仅叠统一入场动效） | ui/settings/SettingsScreen.kt:313,510,566,608、TimeSlotManagementScreen.kt:506,749,1453、BackupScreen.kt:449,492、WebDialogHost.kt:106,136,173、ManageCourseTablesScreen.kt:358,399（24 文件 30+ 处） |
| AppDangerDialog | AppBasicComponents.kt:584 | 危险操作确认（danger 胶囊确认钮 + confirm 触觉） | BackupScreen.kt:371,388、TimeSlotManagementScreen.kt:528、ManageCourseTablesScreen.kt:449、AgendaScreen.kt:287、ui/schedule/components/CourseDetailBottomSheet.kt:845（全 16 处） |
| AppGlassBottomSheet | AppBasicComponents.kt:677 | Haze 毛玻璃底部弹层 | TodayScheduleScreen.kt:2233、AgendaScreen.kt:998,1482、CourseDetailBottomSheet.kt:179,473、CourseTimeDialogs.kt:80,183 |
| AppFab | AppBasicComponents.kt:403 | 56dp FAB（实心 / 液态玻璃两形态） | ui/settings/coursemanagement/CourseNameListScreen.kt:208、CourseInstanceListScreen.kt:172 |
| AppSelectableCard | AppBasicComponents.kt:620 | 选中态卡片（主色描边 + primarySoft 底） | components/CourseTablePickerDialog.kt:258、CourseInstanceListScreen.kt:262、CourseNameListScreen.kt:317 |
| AppSectionHeader | AppBasicComponents.kt:100 | 设置分区标题 | ui/settings/notification/GeneralSettingsCard.kt:112,169,197,257、TimeSlotManagementScreen.kt:354,614、CourseTableConversionScreen.kt:186,209,226 |
| ToastManager + AppToastHost | ToastManager.kt:46 / :74 | 全局 Toast 管道（CONFLATED Flow）+ 根部宿主 | App.kt:119 挂载；`ToastManager.show` 全仓 90 处：TimeSlotManagementScreen.kt(11)、ManageCourseTablesScreen.kt(10)、WebBridgeHandler.kt:170-491、WebViewScreen.kt:308-401、导入 4 屏 |
| AppSnackbarHost | StyleComponents.kt:447 | 页内「带操作结果」反馈宿主 | ui/schedule/WeeklyScheduleScreen.kt:553、BackupScreen.kt:202、CourseTableConversionScreen.kt:175、TweakScheduleScreen.kt:137、QuickDeleteScreen.kt:143（共 6 页） |
| AppDialogActions | StyleComponents.kt:693 | 对话框标准按钮组（等宽双钮，danger 变体） | NotificationDialogs.kt:294,342,504、CourseTablePickerDialog.kt:186,224、CourseOtherSelectors.kt:265,361、QuickDeleteScreen.kt:419、ImportPreviewComponents.kt:300（全仓 49 处调用） |
| AppTextField | StyleComponents.kt:624 | 填充式输入框（isError/enabled/readOnly/supportingText 透传 M3） | AgendaScreen.kt:1550,1628,1646、ui/settings/profile/ProfileInfoScreen.kt:275-337、BackupScreen.kt:498-521、ImportPreviewComponents.kt:240-388 |
| AppSwitch | StyleComponents.kt:259 | 开关（ON=success 轨道，enabled 透传） | GeneralSettingsCard.kt:118,175,186,203、AgendaScreen.kt:1600、SettingsScreen.kt:749、TimeSlotManagementScreen.kt:1403、CourseDetailBottomSheet.kt:1038 |
| AppSegmentedControl | StyleComponents.kt:470 | 分段控件（共享胶囊滑块） | SchoolSelectionListScreen.kt:322、StyleSettingsComponents.kt:250、AnimationSettingsScreen.kt:130,177、AppearanceSettingsScreen.kt:448 |
| AppCard | StyleComponents.kt:98 | 白卡容器（appSurface） | SettingsScreen.kt:233,260、TimeSlotManagementScreen.kt:615,816,919、BackupScreen.kt:410、CourseSchemeCard.kt:70 |
| IconChip / AppBadge / TelegramMenu 系 | StyleComponents.kt:128 / :231 / :160-218 | 图标芯片 / 徽标(99+) / 上下文菜单（danger 红字项、Divider） | SettingsScreen.kt:430、AdvancedSettingsCard.kt:61、WeeklyScheduleScreen.kt:489、WebViewScreen.kt:281、TweakScheduleScreen.kt:316 |
| AppRadioIndicator / AppCheckboxIndicator / AppRadioRow | StyleComponents.kt:313 / :355 / :405 | 单选/复选指示器与整行 | TodayScheduleScreen.kt:1866,2123、AgendaScreen.kt:1348、WebDialogHost.kt:179、MoreOptionsDialogs.kt:36、LanguageSettingScreen.kt:116、BackupScreen.kt:459 |
| AppTopAppBar | AppTopAppBar.kt:56 | 设置页顶栏（hazeState 非空走玻璃形态） | 30+ 页：TimeSlotManagementScreen.kt:266、BackupScreen.kt:190、AppearanceSettingsScreen.kt:103,169,303、SemesterSettingsScreen.kt:77、ManageCourseTablesScreen.kt:233 |
| AppPageHeader | AppTopAppBar.kt:119 | 四主页面统一页头（标题+副标题+leading+actions≤2） | TodayScheduleScreen.kt:819、AgendaScreen.kt:536、SettingsScreen.kt:179 |
| AdaptiveNavigationScaffold | NavigationComponents.kt:122 | 底部导航（LiquidGlassTabs 默认 / 崩溃兜底 LegacyGlassBottomBar:547） | App 级根导航 |
| AlphabetIndexerList | AlphabetIndexerList.kt:28 | 粘性字母头 + 右侧索引条（点击/拖拽跳转） | SchoolSelectionListScreen.kt:242（唯一） |
| NativeNumberPicker | NumberPicker.kt:44 | 滚轮数字选择（拨档触觉） | TimeSlotManagementScreen.kt:1214-1570、CourseTimeDialogs.kt:231-358、SettingsScreen.kt:514,570,612、AgendaScreen.kt:1873,1880 |
| CourseTablePickerDialog | CourseTablePickerDialog.kt:107 | 选课表弹窗（内嵌新增课表子弹窗） | CourseTableConversionDialogs.kt:115,138,148、WebViewScreen.kt:463、WeeklyScheduleScreen.kt:892、JsonFileImportScreen.kt:185、ImportPreviewComponents.kt:441 |
| ShareDialog | ShareManager.kt:28 | 系统分享确认弹窗（desktop 不支持直接 return） | CourseTableConversionScreen.kt:252（唯一） |
| DatePickerModal | DatePickerModal.kt:27 | M3 日期选择（裸 TextButton 按钮） | NotificationDialogs.kt:467、SemesterSettingsScreen.kt:204、TweakScheduleScreen.kt:298,302、AgendaScreen.kt:1721 |
| DateRangePickerModal | DateRangePickerModal.kt:37 | 日期范围选择（未选完禁用确认） | NotificationDialogs.kt:477、QuickDeleteScreen.kt:325 |
| ImageCropper | ImageCropper.kt:68 | 全屏头像裁剪 | ui/settings/appearance/AppearanceSettingsScreen.kt:286、ProfileInfoScreen.kt:159 |
| AdvancedColorPicker | AdvancedColorPicker.kt:138 | HSV Canvas 取色器 | StyleSettingsComponents.kt:656、CourseColorSettingsScreen.kt:208 |
| AppHaptics | AppHaptics.kt:19 | 三档触觉（tick/confirm/longPress；reduceMotion 全静音） | 全局 rememberAppHaptics:41 |
| PullToRefreshBox | androidx M3（非自研组件） | 下拉刷新容器 | TodayScheduleScreen.kt:215,264（全仓唯一） |

#### 表2 · 状态矩阵（组件 | 加载态 | 空态 | 错误态 | 禁用态 | 依据）

| 组件 | 加载态 | 空态 | 错误态 | 禁用态 | 依据 |
|---|---|---|---|---|---|
| AppLoading | 本体即加载态（fillMaxSize 居中） | 无 | 无 | 无 | AppBasicComponents.kt:389-400 |
| ThemedLoadingIndicator | 三主题变体；animated=false（关「页面入场」分组或 pulseDurationMs=0）时不建动画时钟、静态呈现 | 无 | 无 | 无 | AppBasicComponents.kt:238-387、rememberLoopPhase:254 |
| AppEmptyState | 无 | 胶囊底提示（bodyMedium textSecondary）+ 可选动作钮（actionLabel+onAction 成对才渲染） | 无 | 无 | AppBasicComponents.kt:128-181 |
| AppErrorState | 无 | 无 | 同空态视觉 + 重试钮（onRetry=null 则纯提示；文案 action_retry） | 无 | AppBasicComponents.kt:183-236、strings.xml:500 |
| AppAlertDialog | 无（内容透传调用方） | 无 | 无 | 无；入场动效统一 rememberDialogEnterMotion | AppBasicComponents.kt:545-583 |
| AppDangerDialog | 无 | 无 | 无 | 无；确认触发 haptics.confirm() | AppBasicComponents.kt:584-618 |
| AppDialogActions | 无 | 无 | 无 | confirmEnabled=false → 容器 0.45f alpha | StyleComponents.kt:693-738 |
| AppToastHost | 无 | 空白文本 show() 静默跳过 | 无（无错误/成功类型区分） | 无 | ToastManager.kt:46-115 |
| AppSnackbarHost | 无 | 无 | 无 | 无 | StyleComponents.kt:447-468 |
| PullToRefreshBox | M3 默认指示器（isRefreshing 驱动） | 无 | 无 | 无 | TodayScheduleScreen.kt:215,264 |
| CourseTablePickerDialog | 无 | 列表空 → AppEmptyState(text_no_course_tables) | 无 | 未选中课表 → confirmEnabled=false | CourseTablePickerDialog.kt:140,186 |
| ImageCropper | isCropping → 确认钮内嵌 20dp ThemedLoadingIndicator | 无 | 无 | isCropping → 取消/确认双钮均 enabled=false | ImageCropper.kt:262（同文件按钮） |
| DateRangePickerModal | 无 | 无 | 无 | 起止未选完 → 确认 TextButton enabled=false | DateRangePickerModal.kt:37-90 |
| DatePickerModal | 无 | 无 | 无 | 无（裸 TextButton，未接 AppDialogActions） | DatePickerModal.kt:27-56 |
| 列表/表单/导航类（AlphabetIndexerList、NativeNumberPicker、AdvancedColorPicker、AppFab、AppSwitch、AppTextField、AppSegmentedControl、AppCard、AppTopAppBar 系） | 无四态分支 | 无 | AppTextField isError/enabled 透传 M3 | AppSwitch enabled 透传；AdaptiveNavigationScaffold 崩溃自动切 LegacyGlassBottomBar | AlphabetIndexerList.kt:28、NumberPicker.kt:44、StyleComponents.kt:98,259,470,624、NavigationComponents.kt:547、AppTopAppBar.kt:56 |

#### 表3 · 全局反馈机制

| 机制 | 入口 / 触发 | 样式 / 结构 | 依据 |
|---|---|---|---|
| Toast 横幅 | `ToastManager.show(message)` 全仓 90 处；空白文本跳过；新事件覆盖旧（CONFLATED） | 底部居中胶囊 CircleShape+cardBg、textPrimary bodyMedium、maxLines=2；2600ms 自动消退；fadeIn+上浮入场；无动作按钮、无类型/图标 | ToastManager.kt:46,74-115；App.kt:118-119 |
| AlertDialog | 各页面 `AppAlertDialog{...}`（24 文件 30+ 处） | M3 默认布局；按钮由调用方传入，主流配 AppDialogActions：取消=灰字 TextButton、确认=胶囊实心、双钮等宽铺满 | AppBasicComponents.kt:545；StyleComponents.kt:693 |
| 危险确认 | `AppDangerDialog` 16 处（删除课表/日程/方案/情侣课表等；删除确认文案族齐全） | danger 确认钮=tokens.danger 底+白字；确认时 haptics.confirm() 触觉 | AppBasicComponents.kt:584-618；strings.xml:22,318,573,851-853 |
| Snackbar | 6 页局部 `hostState.showSnackbar` | snackbarBg/snackbarFg token、actionColor=primary、按 data 上浮入场 | StyleComponents.kt:447-468 |
| 下拉刷新 | 仅今日页 PullToRefreshBox（state :215） | M3 默认指示器，未自绘样式 | TodayScheduleScreen.kt:37,215,264 |
| 分工约定（注释明示） | Snackbar=带操作结果的页内反馈；Toast=轻量操作确认/全局提示（无动作） | — | ToastManager.kt 头注、StyleComponents.kt:447 |

#### 表4 · 缺口清单（常见但缺失 / 未闭环）

| # | 缺口 | 现状 + 依据 |
|---|---|---|
| 1 | 错误态覆盖率极低 | AppErrorState 全仓仅 2 处（SchoolSelectionListScreen.kt:228、AdapterSelectionScreen.kt:175）；其余页加载失败混用 AppEmptyState 或仅弹 Toast，无页内重试入口 ⚠️ X2 三态规范未全站落地 |
| 2 | AppEmptyState 动作钮零使用 | actionLabel/onAction 已实现（AppBasicComponents.kt:124-165）但全仓无调用方传参——空态只有文字，无「去添加」类闭环 |
| 3 | 空态无插图/图标 | AppEmptyState 仅文字胶囊（AppBasicComponents.kt:128-181），全仓无空态插画/图标组件 |
| 4 | 骨架屏缺失 | grep Skeleton/Shimmer 全仓 0 命中；列表页加载一律全屏 AppLoading / spinner，无占位结构 |
| 5 | Toast 无类型区分 | ToastEvent 仅 (text,id)（ToastManager.kt:35）：错误与成功同样式，无图标/色彩/时长分级；90 处调用无错误专用通道 |
| 6 | 下拉刷新仅一处 | 仅 TodayScheduleScreen.kt:264；周课表/日程等列表页无刷新能力 ⚠️ 是否扩展视产品需要 |
| 7 | DatePickerModal 按钮语言不一致 | DatePickerModal.kt:27-56 用裸 TextButton（primary/textSecondary），未走 AppAlertDialog/AppDialogActions；DateRangePickerModal.kt:37 同为裸按钮（但有禁用态） |
| 8 | 硬编码 Toast 文案 | shared/src/androidMain/.../tool/FileManager.android.kt:270-303 直写中文（「无法打开图片选择器：…」「已保存到 …」），未走 strings.xml，英文/繁中回退错乱 |
| 9 | 无全局错误横幅组件 | 页面级错误全靠无动作 Toast 或各页自绘；缺可挂 action 的全局 error banner（仅 WebView 有自绘错误页+重试：WebViewScreen.kt:75 用 webview_load_error_retry） |

> 供 UX 架构师综合：状态类组件底座完备（三态组件、双反馈通道、危险确认、触觉），主要问题在「覆盖率与闭环」——错误态几乎未用、空态动作钮无人用、反馈无错误语义分级。


---

## 第五部分　设计 Token 基线


### E1 · Token 总表（颜色 / 圆角 / 间距）


仓库根：`D:\01课程表\shangkeschedule`｜性质：只读整理产物（不改仓库代码）｜token 名与值一律原文照抄

**路径缩写约定**（下文所有 `X.kt:NN` 按此展开；同目录 = `shared/src/commonMain/kotlin/com/shangkeschedule/`）
- `IosStyle.kt` / `ClaudeStyle.kt` / `SoftStyle.kt` / `AppStyle.kt` / `Type.kt` / `AppMotion.kt` / `SettingsTone.kt` = `ui/theme/` 下同名文件
- `ScheduleGridStyle.kt` = `data/model/ScheduleGridStyle.kt`；`AppThemePreset.kt` = `data/model/AppThemePreset.kt`
- `StyleSettingsRepository.kt` = `data/repository/StyleSettingsRepository.kt`

**列序统一为 通透（IOS）/ 书卷（CLAUDE）/ 柔绘（SOFT）**，每格「浅色 / 深色」。

#### 1. 颜色 token 总表

| 类别 | token 名 | 值（通透 / 书卷 / 柔绘） | Compose 定义位置 | 使用场景 |
|---|---|---|---|---|
| 主色 | `primary` | `#007AFF`/`#0A84FF`；`#C96442`/`#D97757`；`#7C86C9`/`#9AA3DC` | IosStyle.kt:143/193；ClaudeStyle.kt:164/218；SoftStyle.kt:165/200 | 强调色、选中态、图标着色；书卷=brand-500、柔绘=雾蓝紫 |
| 主色 | `textOnPrimary` | `#FFFFFF`/`#FFFFFF`；`#FFFFFF`/`#141413`；`#FFFFFF`/`#23252F` | IosStyle.kt:141/191；ClaudeStyle.kt:162/216；SoftStyle.kt:164/210 | 主色底上的文字与图标 |
| 主色 | `primarySoft` | `0x1F007AFF`/`0x3D0A84FF`；`#FBF2ED`/`#3A2A22`；`0x1F7C86C9`/`0x3D9AA3DC` | IosStyle.kt:144/194；ClaudeStyle.kt:165/219；SoftStyle.kt:166/212 | 选中胶囊底、浅色强调块 |
| 背景 | `pageBg` | `#F2F2F7`/`#000000`；`#FAF9F5`/`#262624`；`#F4F3F7`/`#1F1E24` | IosStyle.kt:128/179；ClaudeStyle.kt:148/204；SoftStyle.kt:155/204（常量 69/93） | 全页底色 |
| 表面 | `cardBg` / `cardBgElevated` | `#FFFFFF`/`#1C1C1E`·`#2C2C2E`；`#F5F4EF`/`#2C2C2B`·`#30302E`；`#FCFBFD`/`#2A2830`·`#322F39` | IosStyle.kt:129-130/180-181；ClaudeStyle.kt:149-150/205-206；SoftStyle.kt:71-72/94-95 | 卡片与分组容器；`Elevated` 归悬浮层/拖拽态 |
| 表面 | `inputBg` | `0x1F787880`/`0x3D787880`；`#EDE9DE`/`#3E3E38`；`#EFEDF4`/`#37343F` | IosStyle.kt:131/182；ClaudeStyle.kt:151/207；SoftStyle.kt:73/96 | 输入框、未选中分段控件底（`surfaceVariant`） |
| 表面 | `navBarBg` | `0xD9F9F9F9`/`0xD91C1C1E`；`#F5F4EE`/`#1F1E1D`；`0xE6F7F5FA`/`0xE62A2830` | IosStyle.kt:161/211；ClaudeStyle.kt:185/236；SoftStyle.kt:181/227 | 底栏/导航栏底（半透明 + 玻璃模糊） |
| 表面 | `navSelectedBg` | `0x1F007AFF`/`0x3D0A84FF`；`#E9E6DC`/`#0F0F0E`；`0x1F7C86C9`/`0x3D9AA3DC` | IosStyle.kt:162/212；ClaudeStyle.kt:186/237；SoftStyle.kt:182/228 | 底栏选中项底 |
| 分隔 | `divider` / `dividerSoft` | `0x493C3C43`/`0x99545458`·`0x243C3C43`/`0x4D545458`；`#DAD9D4`/`#3E3E38`·`0x0F000000`/`0x14FFFFFF`；`0x1F4A4756`/`0x1FE8E5EE`·`0x0F4A4756`/`0x0FE8E5EE` | IosStyle.kt:132,134/183,184；ClaudeStyle.kt:152,155/208,209；SoftStyle.kt:159,161/206,207 | 列表分隔、`dividerSoft` 用于低对比内分线 |
| 描边 | `outline` / `outlineVariant` | M3 基线 `#79747E`/`#CAC4D0`（通透与书卷都继承默认）；柔绘 `0x334A4756`/`0x3DE8E5EE`·`0x144A4756`/`0x17E8E5EE` | AppStyle.kt:102/110（默认值）；IosStyle.kt:136-137；ClaudeStyle.kt:157-158；SoftStyle.kt:191,193/234,235 | 输入边界、功能性描边 ⚠️ 仅柔绘显式传（见 §4 不一致） |
| 文本 | `textPrimary` | `#000000`/`#FFFFFF`；`#3D3929`/`#F1F1EF`；`#4A4756`/`#E8E5EE` | IosStyle.kt:139/189（常量 36/37）；ClaudeStyle.kt:160/214（常量 51/81）；SoftStyle.kt:76/97 | 一级文本、标题 |
| 文本 | `textSecondary` | `#8E8E93`/`#98989D`；`#6E6D68`/`#B7B5A9`；`#8B8798`/`#9C98A8` | IosStyle.kt:140/190；ClaudeStyle.kt:161/215（常量 48/80）；SoftStyle.kt:77/98 | 二级文本、说明行 |
| 文本 | **三级文本** ⚠️ 未找到定义 | 无独立 token；组件层以 `textSecondary` + `AppAlpha`（`dimmed 0.5f` / `hairline 0.12f` / `subtle 0.06f`）派生 | AppStyle.kt:274-287（`object AppAlpha`） | 占位符、禁用态、极弱说明；设计包侧对应 `--text-500` 降档 |
| 语义 | `success` / `successSoft` | `#34C759`/`#30D158`·`0x1F34C759`/`0x3D30D158`；`#788C5D`/`#8CA06F`·`#F0F3EA`/`#232A1C`；`#7BAE8C`/`#97C4A6`·`0x1F7BAE8C`/`0x3D97C4A6` | IosStyle.kt:145-146/195-196；ClaudeStyle.kt:166-167/220-221；SoftStyle.kt:167-168/213-214 | 成功反馈（书卷=`--success-500`） |
| 语义 | `info` / `infoSoft` | `#5AC8FA`/`#64D2FF`·`0x1F5AC8FA`/`0x3D64D2FF`；`#9C87F5`/`#9C87F5`·`0x149C87F5`/`0x3D9C87F5`；`#7FA8C4`/`#9CC0D8`·`0x1F7FA8C4`/`0x3D9CC0D8` | IosStyle.kt:147-148/197-198；ClaudeStyle.kt:168-169/222-223；SoftStyle.kt:169-170/215-216 | 信息提示（书卷取 `--chart-2`） |
| 语义 | `warning` / `amber` 及 `*Soft` | `#FF9500`/`#FF9F0A`·`#FFCC00`/`#FFD60A`；`#B0562F`/`#E08D6F`·`#A8863C`/`#C7A45C`；`#D9A97E`/`#E0BB96`·`#D8C089`/`#E0CD9E` | IosStyle.kt:149-152/199-202；ClaudeStyle.kt:171-175/224-227；SoftStyle.kt:171-174/217-220 | 警告与琥珀强调（书卷 warning=`--brand-600`） |
| 语义 | `danger` / `dangerSoft` | `#FF3B30`/`#FF453A`·`0x1FFF3B30`/`0x3DFF453A`；`#D64545`/`#EF4444`·`#FCECEA`/`#3A1F1F`；`#CC8A8A`/`#DDA0A0`·`0x1FCC8A8A`/`0x3DDDA0A0` | IosStyle.kt:153-154/203-204；ClaudeStyle.kt:176-177/228-229；SoftStyle.kt:175-176/221-222 | 删除/错误（书卷=`--error-500`） |
| 语义 | `favorite` / `favoriteSoft` | `#FF2D55`/`#FF375F`·`0x1FFF2D55`/`0x3DFF375F`；`#C0506A`/`#DA7E92`·`0x14C0506A`/`0x3DDA7E92`；`#C98FA8`/`#DBAAC0`·`0x1FC98FA8`/`0x3FDBAAC0` | IosStyle.kt:155-156/205-206；ClaudeStyle.kt:179-180/230-231；SoftStyle.kt:177-178/223-224 | 收藏/双人课标记 |
| 渐变 | `gradientStart` → `gradientEnd` | `#007AFF`→`#5856D6` / `#0A84FF`→`#5E5CE6`；`#C96442`→`#D6866A` / `#D97757`→`#E08D6F`；`#7C86C9`→`#9A93B8` / `#9AA3DC`→`#B3ACCF` | IosStyle.kt:158-159；ClaudeStyle.kt:182-183；SoftStyle.kt:179-180/226 | hero 卡与封面渐变 |
| 组件 | `badgeBg` / `badgeFg` | `#FF3B30`/`#FFFFFF`；`#C96442`/`#FFFFFF`；`#CC8A8A`/`#2A1E1E`（深 `#DDA0A0`） | IosStyle.kt:163-164/213-214；ClaudeStyle.kt:187-188/238-239；SoftStyle.kt:183-184/229-230 | 角标/未读点数 |
| 组件 | `snackbarBg` / `snackbarFg` | `0xE61C1C1E`/`#FFFFFF`；`#3D3929`/`#FAF9F5`；`0xE63A3742`/`#F4F3F7` | IosStyle.kt:166-167/216-217；ClaudeStyle.kt:190-191；SoftStyle.kt:185-186/231-232 | 底部提示条 |
| 组件 | `shadow` | `0x0F000000`/`0x66000000`；`0x1A000000`/`0x66000000`；`0x0A4A4756`/`0x1F000000` | IosStyle.kt:169/219；ClaudeStyle.kt:193/244；SoftStyle.kt:188/233 | 阴影色基（仅悬浮层/拖拽/柔绘 `softShadow`） |
| 课程彩板 | **数量 = 每主题 12 色**（成对 `DualColor(light, dark)`）；主题身份 alpha：通透 `0x1F`、柔绘 `0x33`、书卷 `0x40` | 通透 `ScheduleGridStyle.kt:191-202`（APPLE_COLOR_MAPS，12 色）；柔绘 `:250-261`（SOFT_COLOR_MAPS，12 色）；书卷 `StyleSettingsRepository.kt:47-58`（12 色 `0x40…`）；基座 `ScheduleGridStyle.kt:34`+`:140-151`（DEFAULT_COLOR_MAPS，12 色，`0xFFFFCDD2`…） | `ScheduleGridStyle.kt:15`（`data class DualColor`）、`:34`（`courseColorMaps`）；取色 `ui/schedule/components/CourseBlock.kt:122-126`、`WeeklyScheduleScreen.kt:1251-1252` | 课程块填色，索引 = `courseColorMaps[colorInt]`；查重 `WeeklyScheduleViewModel.kt:554/631`（`ensureDistinctCourseColors`） |
| 课程彩板 | ⚠️ 另一套 20 色、alpha `0x59` | `AppThemePreset.kt:94-114`（20 色，按色相 18° 步进：赤红 0°…酒红 342°） | 同上 | 与 R5 声明的三主题身份 alpha（`0x40`/`0x33`/`0x1F`）不一致，归属待对账 |
| 设置入口色库 | `SettingsEntryTone` 10 档：PURPLE, ORANGE, RED, OLIVE, MATCHA, PINK, GREEN, GRAY, AMBER, BROWN | 折叠映射：通透 10→8（GREEN=MATCHA、GRAY=BROWN）、柔绘 10→9（OLIVE=GREEN） | SettingsTone.kt:28（枚举）；:20-21（折叠说明）；:58-70、:97-109、:136-148（三主题映射表） | 设置页入口图标底色（非课程色板，勿混用） |
| 图标 | `AppIconTokens` = small 16 / medium 20 / large 24（dp） | 三主题同值 | AppStyle.kt:511-518（定义）；IosStyle.kt:318-320、ClaudeStyle.kt:325-329、SoftStyle.kt:328-332（三主题实例） | 图标尺寸；命中区另由 `touchMin 48dp` 保证 |
| 兜底 | v2 紫渐变基线（`lightAppColorTokens`/`darkAppColorTokens`） | 浅 `primary #6C5CE7`、`pageBg #F2F3F7`；深 `#0F1115`/`#191C23` | AppStyle.kt:128-161/166-210 | 非三主题 preset 的回落；⚠️ `AppThemePreset.fromString()` 已把旧值收敛到 IOS，属"可证不可达"遗留 |

#### 2. 圆角 token（`AppShapeTokens`，定义 AppStyle.kt:339-350）

| 类别 | token 名 | 值（通透 / 书卷 / 柔绘） | Compose 定义位置 | 使用场景 |
|---|---|---|---|---|
| 圆角 | `card` | 16dp / 16dp / 24dp | IosStyle.kt:241-257；ClaudeStyle.kt:255-271；SoftStyle.kt:262-278 | 卡片、分组容器 |
| 圆角 | `heroCard` | 22dp / 20dp / 28dp | 同上 | 今日页 hero 大卡 |
| 圆角 | `sheetTop` | 22dp / 20dp / 28dp（仅顶部圆角） | 同上 | 底部面板顶边 |
| 圆角 | `chip` / `chipSmall` / `chipSmallRadius` | 10/8/8dp；12/8/8dp；12/10/10dp | 同上 | 标签、筛选胶囊 |
| 圆角 | `menu` | 14dp / 12dp / 18dp | 同上 | 下拉菜单/弹层 |
| 圆角 | `capsule` / `fab` | 50（RoundedCornerShape(50)）/ 50 | 同上 | 胶囊按钮、FAB |
| 圆角 | `bubble` | topStart 4/topEnd 18/bottomStart 18/bottomEnd 18；6/18/18/18；8/22/22/22 | 同上 | 聊天气泡/挂起条 |
| 圆角 | ⚠️ 未使用统一 `--radius-*` 命名 | 设计包 `--radius-sm 8px / -md 12px / 16px / -xl 20px / -2xl 24px / -full 9999px`（设计token规范.md §圆角）仅作对照，代码侧为上述三个主题各自的 dp 常量 | 对账结论 | 设计包 8px ≈ `chipSmall`，24px ≈ 柔绘 `card`，无一对一 token 映射 |

#### 3. 间距 token（`AppSpacingTokens`，定义 AppStyle.kt:356-401）

**三主题逐项同值**，故下表不设主题列；定义位置三处一致（IosStyle.kt:266-284、ClaudeStyle.kt:280-298、SoftStyle.kt:280-304）。

| 类别 | token 名 | 值 | Compose 定义位置 | 使用场景 |
|---|---|---|---|---|
| 间距 | `pageHorizontal` | 20dp | AppStyle.kt:356-401（字段）；三主题实例同上 | 页面左右留白（=设计包 ×5=20px） |
| 间距 | `cardGap` | 16dp | 同上 | 卡片之间 |
| 间距 | `listGap` | 24dp | 同上 | 列表项之间 |
| 间距 | `cardInner` | 16dp | 同上 | 卡片内边距（=设计包 ×4=16px） |
| 间距 | `rowMinHeight` / `settingsRowMinHeight` | 56dp / 52dp | 同上 | 课程行 / 设置行最小高（`SettingsRowHeight` 枚举已删，统一读此值） |
| 间距 | `touchMin` | 48dp | 同上 | 最小命中区（R9：尺寸与命中区解耦） |
| 间距 | `chipIcon` / `fab` | 44dp / 56dp | 同上 | 小图标按钮 / 圆形悬浮钮 |
| 间距 | `navBarHorizontal` / `navBarBottom` | 16dp / 4dp | 同上 | 底栏内边距（`navBarBottom` 于 v4.64.1 由 12dp 收窄为 4dp） |
| 留白 | `pageTop` / `sectionGap` / `sectionTitleGap` / `contentBottom` | 8 / 24 / 8 / 32dp | 同上 | 页顶、分区之间、分区标题、内容底部 |
| 栅格 | 基准 4dp 网格/8pt，取值集合 `{4,8,12,16,20,24,32}` | — | docs/design-system.md R8 | 新增间距必须落在此集合内 |
| 宽度 | `SettingsPageMaxWidth` | 640.dp | AppStyle.kt:408 | 设置页最大内容宽（平板/桌面居中） |
| 字阶栅格 | `AppTypeGrid` | courseName 13f、courseMeta 10f、dayHeader 14.sp、timeLabel 12.sp、timeSmall 10.sp、timeCompact 11.sp | AppStyle.kt:251-264 | 课程格点内文字（非 `AppTypeTokens` 档位） |

#### 4. 与文档对账：不一致与未找到清单

- **R2 描边违规（P2 未修）**：`outline`/`outlineVariant` 仅柔绘显式传（SoftStyle.kt:191,193/234,235）；通透（IosStyle.kt:136-137）与书卷（ClaudeStyle.kt:157-158）直接传 M3 基线 `#79747E`/`#CAC4D0`，与设计包 `--border-300 #dad9d4`/`#3e3e38` 不一致。
- **R3 色库容量收敛**：通透 10→8、柔绘 10→9（SettingsTone.kt:20-21）；设计包侧未见对应条目 → 属代码侧自决，需回写设计规范。
- **C4 `primary` 单源**：`Theme.kt:128` 有 1 处违反；合规例外 = `Theme.kt:192`（provide `LocalIsSoftTheme`）、`ui/components/NavigationComponents.kt:428`（LegacyGlassBottomBar 真机兜底）。
- ⚠️ **未找到定义（共 3 项）**：①「三级文本」token（无独立定义，只有 `AppAlpha` 派生，AppStyle.kt:274-287）；② 课程色板"常量列表"命名类型（无 `CourseColorPalette`/`CourseColors`，只有 `DualColor` 列表，ScheduleGridStyle.kt:15/34）；③ 设计包 `--radius-*` 与代码 `AppShapeTokens` 的一对一映射表（无任何映射文件，仅能逐值对照，见 §2 末行）。
- **数字对账**：`AppSpacingTokens` 15 字段三主题逐项同值，与 design-system.md R8 完全一致；`AppIconTokens` 与 R9 一致；`touchMin 48dp` 与 R9 一致。


### E2 · Token 总表（字号 / 动效）


仓库根：`D:\01课程表\shangkeschedule`｜性质：只读整理产物（不改仓库代码）｜token 名与值一律原文照抄

**路径缩写**（同目录 = `shared/src/commonMain/kotlin/com/shangkeschedule/`）：`IosStyle.kt`/`ClaudeStyle.kt`/`SoftStyle.kt`/`AppStyle.kt`/`Type.kt`/`AppMotion.kt` = `ui/theme/` 下同名文件；`AnimationSettingsScreen.kt` = `ui/settings/appearance/AnimationSettingsScreen.kt`；`App.kt` = 根包 `App.kt`
**列序 = 通透（IOS）/ 书卷（CLAUDE）/ 柔绘（SOFT）**；**所有毫秒值 = `MotionTokens` 基线值**，实际渲染还需 `resolveMotion` 链路：`style.tokens → withThemePreset(preset) → reduced() → speedScaled(speed.factor) → gatedBy(disabledGroups)`（AppMotion.kt:654-675），默认 `MotionSpeed.STANDARD = 2.00f` ⇒ **实际时长 = 基线 ÷ 2**（AppMotion.kt:85-104、527-553）。

#### 1. 字号档位表（`AppTypeTokens`，定义 AppStyle.kt:414-445；实例 IosStyle.kt:297-315 / ClaudeStyle.kt:303-322 / SoftStyle.kt:306-325）

| 档位 | 通透 | 书卷 | 柔绘 | 使用场景 |
|---|---|---|---|---|
| `bigNumber` | 28sp | 28sp | 26sp | 大数字统计（周数/节数） |
| `hero` | 34sp | 34sp | 30sp | 今日页 hero 主标题 |
| `pageTitle` | 22sp | 28sp | 21sp | 页面标题 |
| `sectionTitle` | 17sp | 18sp | 16sp | 分区标题 |
| `rowTitle` | 17sp | 18sp | 16sp | 列表行标题 |
| `settingsRowTitle` | 17sp | 15sp | 16sp | 设置行标题 |
| `body` | 17sp | 15sp | 16sp | 正文 |
| `caption` / `hint` | 13sp / 11sp | 13sp / 12sp | 13sp / 11sp | 辅助说明 / 更弱提示 |
| `timeLabel` / `badge` | 13sp / 11sp | 12sp / 11sp | 13sp / 11sp | 时间轴刻 / 角标数字 |
| `titleWeight` | SemiBold | SemiBold | **Medium**（柔绘轻一档） | 标题字重 |
| `settingsRowTitleWeight` | SemiBold | Medium | SemiBold | 设置行标题字重 |
| `bodyWeight` / `captionWeight` | Normal / Medium | Normal / Medium | Normal / Medium | 正文字重 / 说明字重 |
| 页头（`AppPageHeaderTokens`） | ⚠️ 未取到具体值（本次未读到该段） | 28sp / SemiBold / 0.sp（ClaudeStyle.kt:332-335） | 21sp / Medium / -0.1.sp（SoftStyle.kt:335-338） | 大标题导航栏 |
| 格点字阶（`AppTypeGrid`） | courseName 13f、courseMeta 10f、dayHeader 14.sp、timeLabel 12.sp、timeSmall 10.sp、timeCompact 11.sp（AppStyle.kt:251-264，三主题共用） | 同左 | 同左 | 课程块内文字 |
| 图标尺寸 | small 16 / medium 20 / large 24 dp（AppStyle.kt:511-518；三主题实例 IosStyle.kt:318-320 / ClaudeStyle.kt:325-329 / SoftStyle.kt:328-332） | 同左 | 同左 | 图标 |
| M3 Typography 覆盖 | `titleLarge` 20.sp Bold/28.sp、`headlineSmall` 20.sp SemiBold/28.sp、`bodyLarge` 16.sp Normal/24.sp/letterSpacing 0.2.sp，fontFamily 全 Default（Type.kt:1-38，全文仅此三档） | 同左 | 同左 | 组件层 `MaterialTheme.typography.*` |
| 设计包字阶对照 | `--font-display` Newsreader（今日 28px/600、教务导入 32px/600）、分区 22px/600、卡片 18–21px/600、行 15–17px/600、正文 14–15px/400、辅助 12–13px/500、eyebrow 10–12px/600（设计token规范.md 字阶节） | — | — | ⚠️ 与 `AppTypeTokens` 无一对一映射表，仅能逐档近似对照 |

#### 2. 动效枚举与速度档（AppMotion.kt）

| 类别 | token 名 | 值 | Compose 定义位置 | 使用场景 |
|---|---|---|---|---|
| 速度 | `MotionSpeed` | `RELAXED(1.30f)`、`STANDARD(2.00f)`（默认）、`FAST(2.80f)`；`fromString` 默认 STANDARD | AppMotion.kt:85-104 | 「动效速度」分段控件（AnimationSettingsScreen.kt:164-182）；倍率越高越快，`pulseDurationMs` 与弹簧类**不缩放**（:527-553） |
| 风格 | `AnimationStyle` | `GLASS`（默认/推荐）、`GENTLE`、`SNAPPY` → `GlassTokens`/`GentleTokens`/`SnappyTokens` | AppMotion.kt:107-133 | 「动效风格」三档单选卡（AnimationSettingsScreen.kt:140-159） |
| 按压模式 | `MotionPressMode` | SCALE（通透）/ CONCENTRATION（柔绘，零缩放零位移）/ COLOR_DARKEN（书卷） | AppMotion.kt:139-148 | 课程格点按压反馈 |
| 导航形态 | `NavMotionMode` | SLIDE（通透）/ FADE_UP（柔绘）/ LAYER_PUSH（书卷） | AppMotion.kt:151-160 | 二级页 push/pop |
| 主题档位 | `ThemeMotionProfile(pressMode, navMode, indicationStyle, indicationAlpha)` | IosProfile = SCALE/SLIDE/`HIG_HIGHLIGHT`/0.06f；SoftProfile = CONCENTRATION/FADE_UP/`SOFT_RADIAL`/0.12f；ClaudeProfile = COLOR_DARKEN/LAYER_PUSH/`COLOR_DARKEN`/0.055f | AppMotion.kt:413-452 | 触摸指示语言（R5 身份项） |
| 全局注入 | `LocalAppMotion` / `AppMotion(style, tokens, disabledGroups, profile, reduceMotion)` / `isEnabled(group)` | `compositionLocalOf { resolveMotion(GLASS, emptySet()) }`；`isEnabled = group !in disabledGroups && !reduceMotion` | AppMotion.kt:597-606、654-682 | 所有动画调用点读 `LocalAppMotion.current` |

#### 3. `motion.tokens` 字段表（`MotionTokens`，字段定义 AppMotion.kt:207-285）

**必列 5 字段（导航转场专用）**

| 字段 | 三档基线（Glass / Gentle / Snappy） | 三主题最终值（通透 / 书卷 / 柔绘） | 定义处与覆写处 | 用途 |
|---|---|---|---|---|
| `navDurationMs` | 350 / 420 / 240 | 350 / 380 / 420（实际 175 / 190 / 210ms） | 字段 :207-285；Glass :333-355；Gentle :358-380；Snappy :383-405；主题覆写 withThemePreset :465-513 | 二级页转场时长 |
| `navEasing` | IosSheetEase / GentleEase / SnappyEase | IosSheetEase / IosEase / `SoftEnterEase` | :288-303 缓动常量；:465-513 | 转场曲线（SLIDE 态实为弹簧，此值仅 FADE_UP/LAYER_PUSH 用，App.kt:212-236） |
| `navTrailFraction` | 1/3 / 1/3 / 1/3 | **1/3**（SLIDE 尾随视差）/ 0 / 0 | :465-513 | 旧页尾随位移比例 |
| `navOffsetDp` | 0 / 0 / 0 | 0 / **14dp** / **6dp** | :465-513 | 新页入场偏移（14dp=书卷右缘淡入、6dp=柔绘上浮） |
| `navExitDurationMs` | 240 / 300 / 160 | 240 / 240 / 220（实际 120 / 120 / 110ms） | 字段 :207-285；主题覆写 :465-513；退场曲线 `navExitEasing`（IosEase/IosEase/IosEase） | 退出动画独立更快（App.kt:238-241） |

**其余字段（同一张表，按语义分组；三档值 = Glass / Gentle / Snappy）**

| 字段组 | 字段与基线值 | 定义处 | 使用场景 |
|---|---|---|---|
| 隐藏 | `hideDurationMs` 240/320/160 + `hideEasing` IosEase/GentleEase/SnappyEase | AppMotion.kt:333-405 | 滚动时底栏/圆钮下滑淡出 |
| 强调 | `emphasisScaleSpec` IosBouncy/IosSmooth/IosSnappy、`emphasisFadeSpec` tween(220,IosEase)/tween(320,GentleEase)/tween(140,LinearOutSlowInEasing)、`emphasisInitialScale` 0.88/0.94/0.72、`emphasisTargetScale` 1 | 同上 | 玻璃悬浮件出现/隐藏 |
| 按压 | `pressSpec` IosSnappy/IosSmooth/IosSnappy、`pressScale` 0.97/0.95/0.88；`cellPressSpec` 同上、`cellPressScale` 0.97/0.985/0.94、`cellLiftDp` 1/2/3；`cardPressMs`/`cardReleaseMs` 140/240、180/300、110/180 | 同上 | 按钮与课程格点按压 |
| 入场 | `entranceDurationMs` 320/460/240、`entranceEasing` IosEase/GentleEase/SnappyEase、`entranceStaggerMs` 40/70/25、`entranceSlideDp` 10/8/16、`entranceStaggerCapMs` 240/240/200、`entranceInitialAlpha` 0.35 | 同上 | 页面入场错峰淡入 + 周翻页扫光（复用） |
| 触摸 | `touchExpandMs`/`touchFadeMs`/`touchEasing` 400/220/IosSheetEase、400/220/IosSheetEase、260/160/SnappyEase | 同上 | 触摸指示扩散 |
| Tab | `tabIndicatorMs`/`tabIconMs` 220/200、260/220、160/140 | 同上 | Tab 选中胶囊迁移 + 图标变体 |
| 面板 | `sheetEnterMs`/`sheetExitMs`/`sheetScrimMs`/`sheetSlideDp` 320/220/320/0、380/260/360/8、240/160/240/0 | 同上 | 底部面板 |
| 弹窗 | `dialogEnterMs`/`dialogExitMs` 260/180、320/220、200/140 | 同上 | 对话框 |
| 其他 | `statusFadeMs` 400/520/260、`expandDurationMs` 280/420/220 + `expandEasing` IosSheetEase、`colorDurationMs` 320/520/260、`resizeDurationMs` 300/420/240 + `resizeEasing` IosSheetEase/GentleEase/SnappyEase、`pulseDurationMs` 1200/1400/800（不随速度缩放） | 同上 | 状态栏淡入、展开、颜色过渡、尺寸变化、呼吸 |
| 主题覆写其余项 | 柔绘 `withThemePreset` 另覆写：emphasis CriticallyDampedLow/tween(260,SoftEnterEase)/initScale 1f、press CriticallyDamped/1f、cell CriticallyDamped/1f/lift 0、entranceEasing SoftEnterEase/slide 0/cap 220、touch 380/240/SoftEnterEase、tab 320/200、sheet 420/220/320/0、dialog 400/220、card 160/320、status 300、resizeEasing SymmetricEase；书卷另覆写：emphasis CriticallyDampedLow/tween(300,IosEase)/0.94、press/cell CriticallyDamped/1f/lift 0、cap 240、touch 180/240/IosEase、tab 280/160、sheet 380/280/320/24dp、dialog 280/220、card 160/240、status 400 | AppMotion.kt:465-513 | 三主题手感差异的落点 |

#### 4. 缓动与弹簧常量（AppMotion.kt:288-330）

| 类别 | token 名 | 值 | 定义位置 | 使用场景 |
|---|---|---|---|---|
| 缓动 | `IosEase` / `IosSheetEase` | CubicBezier(0.25,0.1,0.25,1) / (0.32,0.72,0,1) | :288-303 | 通透主缓动 / 面板与导航 |
| 缓动 | `GentleEase` / `SoftEnterEase` | (0.4,0,0.2,1) / (0.22,0.55,0.24,1) | 同上 | 柔绘主缓动 / 柔绘入场（v3.45.1） |
| 缓动 | `SnappyEase` / `SymmetricEase` | (0.2,0,0,1) / (0.45,0,0.55,1) | 同上 | 灵动跟手 / 柔绘尺寸变化 |
| 弹簧 | `IosSmoothSpring` / `IosSnappySpring` / `IosBouncySpring` | spring(1.0, StiffnessMediumLow) / spring(0.86, StiffnessMedium) / spring(0.62, StiffnessMediumLow) | :309-330 | 通透柔和/短促/回弹 |
| 弹簧 | `CriticallyDampedSpring` / `CriticallyDampedLowSpring` | spring(1.0, StiffnessMedium) / spring(1.0, StiffnessMediumLow) | 同上 | 书卷与柔绘的零过冲按压 |
| 导航专用弹簧 | `spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)`（位移与透明度同参，v3.54.0） | App.kt:212-236 | 通透 SLIDE 转场 |

#### 5. 三主题动效差异对照表

| 维度 | 通透（IOS） | 书卷（CLAUDE） | 柔绘（SOFT） |
|---|---|---|---|
| 导航形态 | SLIDE：整屏横推 + **1/3 尾随视差**，物理弹簧（可中途反向） | LAYER_PUSH：新页**右缘 14dp** 淡入，旧页原地降 **6% 不透明度** | FADE_UP：淡入 + **6dp 上浮**，零横向位移 |
| 导航时长 / 退场 | 350 / 240ms（实际 175 / 120） | 380 / 240ms（实际 190 / 120） | 420 / 220ms（实际 210 / 110） |
| 导航缓动 | IosSheetEase（SLIDE 实为弹簧） | IosEase | SoftEnterEase |
| 按压模式 | SCALE（缩放 + 抬起，lift 1dp） | COLOR_DARKEN（底色+描边加深，零缩放，lift 0） | CONCENTRATION（浓度响应，零缩放零位移，lift 0） |
| 触摸指示 | `IndicationStyle.HIG_HIGHLIGHT`，alpha 0.06f | `IndicationStyle.COLOR_DARKEN`，alpha 0.055f | `IndicationStyle.SOFT_RADIAL`，alpha 0.12f |
| 入场（PAGE_ENTRANCE） | 320ms / 错峰 40 / 位移 10dp | 240ms / cap 240 / 位移同上表覆写 | 420ms / `SoftEnterEase` / 错峰沿用 70 / 位移 0 |
| 面板 / 弹窗 | sheet 320/220/320/0，dialog 260/180 | sheet 380/280/320/**24dp**，dialog 280/220 | sheet 420/220/320/0，dialog 400/220 |
| 尺寸变化 | resize 300 / IosSheetEase | resize 240 / SnappyEase（沿用 base） | resize `SymmetricEase`（对称缓动） |
| 课程色板 alpha（身份项 R5） | 0x1F | 0x40 | 0x33 |

#### 6. `AnimationGroup` 清单与语义 + 动效规格表

`enum class AnimationGroup`（AppMotion.kt:163-199，9 组，顺序即设置页顺序；`fromString` 无默认）：`COURSE_CELL` `WEEK_PAGER` `GLASS_FLOATING` `PAGE_ENTRANCE` `NAV_TRANSITION` `BAR_HIDE` `BOTTOM_SHEET`(v3.43.0) `DIALOG`(v3.43.0) `TAB_SWITCH`(v3.43.0)。设置页 `AnimationGroup.entries.forEach { MotionGroupToggle(label = group.labelRes, desc = group.descRes, enabled = group !in settings.disabledAnimationGroups, …) }`（AnimationSettingsScreen.kt:200-207），另加「减弱动态效果」开关（:217-222）与预留占位（:225-241）；**关掉某分组 ⇒ 该类动画瞬切无动效**（:61-72 类注释）。

| 场景（AnimationGroup · 语义） | 类型 | 时长或 token | 关闭动效时降级 | 依据 |
|---|---|---|---|---|
| `NAV_TRANSITION` 二级页 push/pop | 位移 + 淡入（三主题形态见表 §5） | `navDurationMs` / `navEasing` / `navTrailFraction` / `navOffsetDp` / `navExitDurationMs` | **瞬切**：`navDurationMs`/`navExitDurationMs` 0、`navOffsetDp` 0、`navTrailFraction` 0 | App.kt:197-241；AppMotion.kt:611-645 |
| `WEEK_PAGER` 周切换翻页/玻璃滑动过渡 | 位移 + 扫光 | 复用 `entrance*` 字段（时长/错峰/位移） | **不归零**（归零会误杀扫光，AppMotion.kt:611-645 显式排除） | AppMotion.kt:611-645 |
| `PAGE_ENTRANCE` 今日页/周页首次进入课程块错峰淡入 | 淡入 + 位移错峰 | `entranceDurationMs`/`entranceStaggerMs`/`entranceSlideDp`/`entranceStaggerCapMs`/`entranceInitialAlpha` | **不归零**；调用点用 `motion.isEnabled(PAGE_ENTRANCE)` 自门控 | AppMotion.kt:611-645 |
| `GLASS_FLOATING` 玻璃悬浮件出现·隐藏·按压（底栏胶囊/回到本周圆钮/挂起条/玻璃 FAB） | 缩放 + 淡入 + 弹簧 | `emphasisScaleSpec`/`emphasisFadeSpec`/`emphasisInitialScale`/`emphasisTargetScale`/`pressSpec`/`pressScale` | 弹簧改 `snap()`、`initScale` 1f、`pressScale` 1f | AppMotion.kt:611-645 |
| `BAR_HIDE` 滚动时底栏/圆钮下滑淡出 | 位移 + 淡出 | `hideDurationMs`/`hideEasing` | `hideDurationMs` 0（瞬隐） | 同上 |
| `COURSE_CELL` 课程格点按反馈 | 缩放/浓度 + 抬起 | `cellPressSpec`/`cellPressScale`/`cellLiftDp`/`cardPressMs`/`cardReleaseMs` | `snap()`、`cellPressScale` 1f、`cellLiftDp` 0、`cardPress*` 0 | 同上 |
| `BOTTOM_SHEET` 底部面板升起/收起 + 遮罩渐入 | 位移 + 遮罩淡入 | `sheetEnterMs`/`sheetExitMs`/`sheetScrimMs`/`sheetSlideDp` | sheet 全字段 0 | 同上 |
| `DIALOG` 对话框淡入/收起缩放回弹 | 淡入 + 缩放 | `dialogEnterMs`/`dialogExitMs` | dialog 全字段 0 | 同上 |
| `TAB_SWITCH` Tab 选中胶囊迁移 + 图标变体切换（页面内容不做淡入以免底栏闪烁） | 位移 + 尺寸 | `tabIndicatorMs`/`tabIconMs` | tab 时长 0 | 同上 |
| 全局「减弱动态效果」（`reduceMotion`） | 全站降级 | `reduced()`：nav 180/trail 0/offset 0、exit 140、emphasis snap/1f、press snap/1f、cell snap/1f/0、entrance 错峰 0/位移 0/cap 0/alpha 1f、touch 90/120、tab 120/120、sheet 160/120/160/0、dialog 140/110、resize 0、status 260、pulse 0；profile.pressMode SCALE→COLOR_DARKEN、navMode→FADE_UP | 系统级无障碍开关（AnimationSettingsScreen.kt:217-222） | AppMotion.kt:561-590 |
| 「动效速度」档位 | 统一倍率 | RELAXED 1.30 / STANDARD 2.00（默认）/ FAST 2.80 ⇒ 实际时长 = 基线 ÷ factor | — | AppMotion.kt:85-104、527-553 |

#### 7. 不一致与未找到清单

- **文档内部注释不一致**：AppMotion.kt:465-513 段注释写「叠默认倍率（1.6 → 2.0）」，与 `MotionSpeed.STANDARD = 2.00f`（:85-104）一致，但同段「375→210ms」的推算按 2.0 倍约掉后为 210（440÷2=220 才对），属历史遗留表述，需以注释与实现分别核对。
- **设计包 vs 代码无映射**：设计token规范.md §3.6 的 `.16s / .2s / .24–.25s / .35–.4s / 1.5s` 与 `MotionTokens` 毫秒值无一对一映射文件；代码侧为「基线 ÷ 速度倍率」的双参数模型，设计包为单值 CSS 变量。
- ⚠️ **未找到定义（2 项）**：① 通透侧 `iosPageHeaderTokens` 的具体数值（本次未读到该段，仅书卷 28sp/柔绘 21sp 已取到）；② `MotionSpeed` 的「设计基线时长」独立定义表（代码只有倍率常数，基线时长全部内联在各 `*Tokens` 对象里）。
- 备注（非缺陷）：`PAGE_ENTRANCE` 与 `WEEK_PAGER` 无「关闭即归零」降级，是刻意设计（复用同一组入场字段），评估时不应报为漏实现。


---

## 第六部分　交互验收清单


### E3 · 交互验收清单（页面与流程）


> 用途：对「上课」四主页面 + 六条核心流程做逐条验收，每条均为客观可判定项。
> 事实来源：`build_qa/ux-spec-parts/` 的 A/B1-B4、C1-C9、D 分节稿；抽查源码核实 3 条（`WeeklyScheduleScreen.kt:1170-1180` 空态引导、`TodayScheduleScreen.kt:264-290` 下拉刷新、`strings.xml:64/72/73/83/154/798/829/853` 键值）。路径前缀 `shared/src/commonMain/kotlin/com/shangkeschedule/`，下称 `…/`。
> 判定：`- [ ]` 勾选＝现状与验收预期一致；未勾选＝需修正。⚠️＝现状与规格存在偏差，需产品确认后再判定。

#### 1. 今日页（屏=…/ui/today/TodayScheduleScreen.kt；VM=…/ui/today/TodayScheduleViewModel.kt）
- [ ] Loading 态走 AppLoading，且与 Success 之间用 AnimatedContent（contentKey=状态类）淡入淡出（屏:253-262）
- [ ] 未导入课表（semesterStartDate==null）→页头胶囊「请设置开学日期」，课程流强制空并落空态（VM:155,173-179；屏:383）
- [ ] 假期中→胶囊「假期中（距离开学还有N天）」；学期结束→「学期已结束 N 天」且 N≥1（屏:385-406）
- [ ] 今日无课→空态「今天没有课程」(text_no_courses_today)，待办/事件/明日三区照常渲染（屏:870-878）
- [ ] 时间轴三态可辨：已结束=删除线+透明度 0.6、进行中=时间列呼吸圆点、未开始=常色（屏:1202-1203,1234-1268）
- [ ] 下节课卡覆盖 Course/Event/Ended/None 四分支；Event 卡与 TODAY_ENDED 卡 onClick=null 不可点；课程卡→玻璃详情弹层（状态徽标+时间/地点/教师/学分+「关闭」「编辑课程」）（屏:828-861,2211-2343）

#### 2. 课表页（屏=…/ui/schedule/WeeklyScheduleScreen.kt；VM=…/WeeklyScheduleViewModel.kt；Grid=…/ui/schedule/components/ScheduleGrid.kt；Guide=…/ui/schedule/components/AddScheduleGuide.kt）
- [ ] 顶栏左视图切换 WEEK↔LIST 即时生效并持久化（屏:446-472）
- [ ] 点标题胶囊：学期未设→跳学期设置；已设→弹周选择 sheet 并自动滚到当前周（屏:420-427,869-888）
- [ ] 左右滑切周（无限页 CENTER）+扫光动效；长按进入编辑态后锁滑周与纵向滚动（屏:191,581-585,818-819）
- [ ] 拖块到左右边缘→跨周挂起（藏底栏+浮动条）；点空白格放下、取消钮退出挂起（屏:794-803,831-845）
- [ ] 拖拽落位必须 resetAllStates；退出编辑态后滚动与滑周恢复可用（否则 isEditingActive 恒 true 永久锁死）（Grid:467-475）
- [ ] 空白周到 AddScheduleGuide：「本周没有课程」+三步条（①选学校②登录教务③完成导入，不可点）+主按钮「教务系统导入」+「其他添加方式」展开文件导入/手动添加两项（屏:1170-1180；Guide:76-162）
- [ ] 撞课并簇分列等宽、本人课优先占左列；非本周幽灵块与活跃课重叠时被滤除不遮挡（VM:1134-1216）

#### 3. 日程页（屏=…/ui/agenda/AgendaScreen.kt；VM=…/ui/agenda/AgendaViewModel.kt）
- [ ] isLoaded=false 时整页 AppLoading，就绪后才渲染日期区与分组列表（屏:253-254）
- [ ] 日期区二态：下拉>44dp 展开 42 格整月日历（恒 6 行不跳高），上收/把手点击收起（屏:504-532,650-670）
- [ ] 月历点选日期后自动收起月历；月外补白格淡化但仍可点（屏:353-357,932-936）
- [ ] 空态「这天还没有日程」(agenda_empty)；「+」新建初始日期=当前选中日（屏:392-397,259-284）
- [ ] 待办完成态：标题删除线+整行 alpha 0.55+状态标签隐藏+勾选框选中，TalkBack 读「已完成/未完成」（屏:1251,1345-1357,1403）
- [ ] 待办与今日页同源 todo_items，任一侧勾选实时双向同步（VM:44-45,293-304）
- [ ] 非课程条目长按→删除确认「确定要删除「%s」吗？」；课程长按无任何动作（屏:1336-1341）

#### 4. 我的与设置（SET=…/ui/settings/SettingsScreen.kt；CMP=…/ui/settings/AppSettingsComponents.kt；SS=…/ui/settings/SemesterSettingsScreen.kt；TS=…/ui/settings/time/TimeSlotManagementScreen.kt；CP=…/ui/settings/CoupleScheduleSettingsScreen.kt；MORE=…/ui/settings/additional/MoreOptionsScreen.kt）
- [ ] 「我的」主页渲染 4 组共 12 行（课表 4/课程 2/偏好 5/关于 1），其中 10 行 chevron 导航、2 行 AppSwitch（SET:672-720）
- [ ] 分组标题带 heading() 语义，TalkBack 可按标题跳转（CMP:110-114）
- [ ] 无课表（config=null）时「是否显示周末」整行仍可点，但写入静默 no-op、控件未置灰且无提示（SET:705-711；VM:158-169）
- [ ] 设置内容限宽约 640dp 居中，宽屏不铺满（SET:129-132,166）
- [ ] 学期设置：总周数滚轮范围 1..30；当前周 trailing 三态（未设开学日期/假期/第N周）；手设当前周反向改写开学日期（SS:163-184；AppSettingsViewModel.kt:202-206）
- [ ] 节次管理：保存钮 enabled=isDataLoaded && localTimeSlots 非空；改结束时间则后续节次整体顺延并钳 23:59（TS:297-301,464-481）
- [ ] 节次未保存返回→退出确认（危险色「不保存退出」/灰字「继续编辑」）；删除作息方案→危险确认（TS:232-243,504-524,527-542）
- [ ] 情侣课表无输入码/扫码/网络配对（创建为本地幂等操作）；删除=删整张 TA 课表且不可恢复，仅删除成功后才复位叠加开关（CP:190-245,203-205）
- [ ] 「更多」页官网/GitHub/mailto 三处 openUri 无 try-catch 与剪贴板兜底、失败静默；全库无隐私政策入口与数据采集声明（MORE:209,212-216,253；C9 §1/§3）

#### 5. 教务导入（选校=…/ui/schoolselection/list/SchoolSelectionListScreen.kt；适配=…/ui/schoolselection/list/AdapterSelectionScreen.kt；WV=…/ui/schoolselection/web/WebViewScreen.kt；桥=…/ui/schoolselection/web/WebBridgeHandler.kt）
- [ ] 入口共 5 处且同指向 SchoolSelectionListScreen：课表空态主按钮+课表页 ⋮ 菜单+课表管理页+转换枢纽页+情侣课表页（WeeklyScheduleScreen.kt:1175,493-499；ManageCourseTablesScreen.kt:349；CourseTableConversionScreen.kt:214；CoupleScheduleSettingsScreen.kt:327）
- [ ] 选校页：placeholder「搜索学校名称或首字母」+4 分类胶囊（本专科/研究生/通用工具/其他）；无结果→「没有找到匹配的学校」；分类无适配→「当前类别暂无适配…」（选校:99-197；strings.xml:460-462）
- [ ] 执行导入三步：脚本不存在→Toast 路径；脚本可读→弹「选择一个课表以导入课程」；注入期间底部常驻「正在执行导入脚本...」且按钮禁用（WV:369-485）
- [ ] 成功 Toast「课程导入成功！共导入 N 门课。」1s 防抖只报累计值；失败仅首条 Toast 防覆盖（桥:157-186）
- [ ] 脚本未启动看门狗 IMPORT_IDLE_TIMEOUT_MS=30_000L→「导入无响应：适配脚本未在限定时间内启动…」（WebModelsAndConstants.kt:93；桥:139-150）
- [ ] 0 门课仍提示「共导入 0 门课。」（saveImportedCourses 无 0 门校验）（桥:375-443）
- [ ] 导入成功但未设开学日期→弹「请设置开学日期」+「去设置」/「稍后」（App.kt:477-495）
- [ ] 完成后为 push，WebView entry 仍留栈，返回键会退回 WebView 页（无清栈动作）（桥:481-499；WV:170-178）

#### 6. 手动编辑课程（AE=…/ui/settings/course/AddEditCourseScreen.kt；CN=…/ui/settings/coursemanagement/CourseNameListScreen.kt；QD=…/ui/settings/quickactions/QuickDeleteScreen.kt）
- [ ] 课程名称留空→toast「课程名称不能为空」且不保存（AE:184-186；strings.xml:144）
- [ ] 时间非法→toast「开始节次不能大于结束节次」；⚠️ 该文案只提节次，校验实际还覆盖自定义时间的非空与先后（AE:183-197）
- [ ] 保存成功→toast_save_success + onBack；学分留空存 null（AE:138-152）
- [ ] 编辑态删除→AppDangerDialog「确认删除课程 / 确定要删除课程「%1$s」吗？此操作无法撤销。」（AE:177-181,407-418）
- [ ] 有未保存改动时退出→拦截弹窗「放弃更改？/当前内容已修改，退出将丢失未保存的数据。」+危险色「不保存退出」（AE:117-135,421-444）
- [ ] 课程管理两级页：一级不重名课程两列网格+实例数 Badge+长按多选+批删二次确认；二级同名实例网格同构（CN:209-227,262-301）
- [ ] 快速删除：预览「预计从课表中移除 %1$d 节课程记录」或 hint_no_selection + 危险色删除钮+不可撤销确认；⚠️ 周次筛选硬编码 1..20，未跟随学期总周数（QD:268-310,371）

#### 7. 文件导入（Hub=…/ui/settings/import/FileImportHubScreen.kt；VM=…/ui/settings/import/TextImportViewModel.kt；JSON=…/ui/settings/import/JsonFileImportScreen.kt）
- [ ] Hub 呈现 5 类入口（Excel/JSON/ICS/CSV/文本）+底部提示 import_file_hub_hint（Hub:86-123）
- [ ] 三屏共用流程：选文件/输入→解析→ImportPreviewSection 预览可编辑并回写 VM→目标表单（另存新课表默认表名=文件名去扩展名 take(20)，或覆盖已有）→成功 toast+reset+onImportSuccess(tableId)（VM:185-235）
- [ ] 解析失败红字留在原页：空输入/空文件/旧版二进制 .xls（提示先转 .xlsx）/非 UTF-8/解析异常/格式不识别（VM:64-124,245-250；strings.xml:829）
- [ ] 覆盖确认：JSON 文件导入有二次确认「导入将覆盖课表「%1$s」的全部课程数据…」；⚠️ Excel/文本文件/粘贴三屏覆盖已有课表直接入库、无确认弹窗（JSON:97-99,196-216；ImportPreviewComponents.kt:341-430）
- [ ] ⚠️ 0 门课未拦截：applyParseResult 不检查 courses.isEmpty，空预览照常渲染并可继续导入（VM:237-252）

#### 8. 备份恢复（BK=…/ui/settings/backup/BackupScreen.kt；BVM=…/ui/settings/backup/BackupViewModel.kt）
- [ ] 仅两通道 BackupTarget{WEBDAV, LOCAL_ZIP}；ICS 导出与系统日历同步挂在转换枢纽页，本页无相关键（BK:111-114；CourseTableConversionScreen.kt:200-204,228-232）
- [ ] 恢复二次确认（WebDAV/本地共用 1 处）：「恢复数据将覆盖当前课表、样式与设置，此操作无法撤销。」（BK:346-384）
- [ ] WebDAV 解绑二次确认：「将清除已保存的 WebDAV 账号与密码…」→disconnectWebDav（BK:298-302,386-399）
- [ ] 部分成功（恢复缺模块）→长时 Snackbar「恢复完成，但以下模块缺失：%1$s」（BK:171-177；BVM:224-277）
- [ ] WebDAV 未配置：选择备份/恢复→Snackbar error_webdav_unconfigured，且自动同步开关禁用（BK:260-284,314-318）
- [ ] isBusy 时备份/恢复两行禁用，并在顶部显示 LinearProgressIndicator（BK:218-242）

与 docs/design-system.md 的引用关系：E3 的页面/流程判定以 design-system.md §2 硬性规则为上游依据——R1（组件层零主题分支，页面不得自行分主题）、R4（单一事实来源，组件与文案须单点维护，故 DatePickerModal 裸 TextButton、FileManager.android.kt 硬编码文案、未走 strings.xml 的提示均判不通过）、R5（组件合并≠外观合并）；本清单引用的每个控件均为 D-公共组件状态.md 表1 已登记组件，出现表外自绘等价控件即视为 R4 违背。


### E4 · 验收清单（状态文案 / 无障碍 / 视觉阈值）


> 用途：验收空态与错误态、文案一致性、加载刷新、手势防误触、危险操作确认、对比度与色板、字号缩放与无障碍、主题深色、权限九组。
> 事实来源：`build_qa/ux-spec-parts/`（B1-B4、C1-C9、D）+ `docs/agents/contrast-audit.md`、`docs/agents/font-scale-audit.md`、`docs/agents/palette-audit.md`；阈值逐条给数值与文件行号。路径前缀 `shared/src/commonMain/kotlin/com/shangkeschedule/`，下称 `…/`。
> 判定：`- [ ]` 勾选＝达标；未勾选＝需修正。⚠️＝现状偏离阈值或规范，需产品确认后判定。

#### 1. 空态与错误态（三态收敛基线：D-公共组件状态.md:4）
- [ ] 三态收敛（项目自有规范 X2 / v3.69.3）：空→AppEmptyState、加载→AppLoading、错误→AppErrorState（AppBasicComponents.kt:128/183/389）
- [ ] 错误态覆盖率：AppErrorState 全仓仅 2 处调用（SchoolSelectionListScreen.kt:228、AdapterSelectionScreen.kt:175），其余页加载失败混用空态或仅弹 Toast、无页内重试（D:13,78）
- [ ] 空态动作钮：AppEmptyState 的 actionLabel/onAction 已实现（AppBasicComponents.kt:124-165）但全仓零调用方；空态仅文字胶囊、无插图/图标（D:79-80）
- [ ] 骨架屏：grep Skeleton/Shimmer 全仓 0 命中，列表页加载一律全屏 AppLoading/spinner（D:81）
- [ ] 今日页无错误态：TodayUiState 仅 Loading/Success，解析失败静默兜底为「--:--」占位（B1:32；TodayScheduleViewModel.kt:331-348）
- [ ] 日程页与我的页无 error UI：日程页无 error 态/无 try-catch（B3:56）；我的页 SettingsUiState 无 error 字段、combine 无 catch（B4:41）
- [ ] 反馈语义分级：ToastEvent 仅 (text,id)，成功与错误同样式、无图标/色彩/时长分级（ToastManager.kt:35；D:82）

#### 2. 文案与术语一致性
- [ ] 双通道分工约定：Snackbar=带操作结果的页内反馈（6 页），Toast=轻量操作确认/全局提示、无动作按钮（D:72；StyleComponents.kt:447）
- [ ] 页面名与 strings 键一致：底部四 Tab「今日/课表/日程/我的」与 nav_today/nav_schedule/nav_settings 对应，agenda_empty 用「日程」术语（B3:13；B4:12；strings.xml:36-37,83）
- [ ] ⚠️ 开关命名风格统一：「是否显示周末」(item_show_weekends) 为疑问式，与「显示非本周课程」(item_show_non_current_week) 名词短语不一致（B4:79；strings.xml:279-280）
- [ ] ⚠️ 校验文案与校验范围一致：toast_time_invalid「开始节次不能大于结束节次」只提节次，实际还覆盖自定义时间的非空与先后（C4:23；strings.xml:150）
- [ ] ⚠️ 文案不得硬编码：FileManager.android.kt:270-303 直写「无法打开图片选择器：…」「已保存到 …」未走 strings.xml，英文/繁中回退错乱（D:85）

#### 3. 加载与刷新
- [ ] 冷启动门控：startGate.isReady=false 时整屏仅渲染 ThemedLoadingIndicator（DB 初始化占位，非白屏），就绪后按 gate.startScreen 决定起始 Tab（App.kt:105-129）
- [ ] 下拉刷新覆盖：全仓仅今日页 1 处（TodayScheduleScreen.kt:215,264）；周课表/日程/我的页无刷新能力（D:83）
- [ ] 今日页刷新行为：refreshing=true→refresh()→delay(520) 收起；自定义指示器仅 refreshing‖distanceFraction>0.01f 可见（屏:264-290；VM:117-122）
- [ ] 周课表页面级无 loading 态：courseCache 无对应 key 时直接渲染空白网格，可能闪空（B2:29）
- [ ] 解析中态：parseResult==null 时整段加载态（import_status_parsing/importing/processing）且解析钮禁用（C5:28）

#### 4. 手势与防误触
- [ ] 越界加课拦截：学期外点空白格→snackbar「请在教学周期间添加或调整课程」且不落库（WeeklyScheduleScreen.kt:705-739；strings.xml:154）
- [ ] 拖拽状态复位：落位必须 resetAllStates，否则 isEditingActive 恒 true→滚动+滑周永久锁死（ScheduleGrid.kt:467-475）
- [ ] 新建日程防误提交：创建钮 enabled=标题非空 &&（全天‖开始<结束），submitted 后锁死防双击（AgendaScreen.kt:1465-1476,1666-1687）
- [ ] 异步钮防重复触发：快速删除钮 isLoading 时禁用、无受影响记录时不出现；节次保存钮空列表禁用（QuickDeleteScreen.kt:154-177；TimeSlotManagementScreen.kt:297-301）
- [ ] 触觉分级与降级：tick/confirm/longPress 三档，reduceMotion 开启时全静音（AppHaptics.kt:19-42）

#### 5. 危险操作确认
- [ ] 课程编辑域台账齐全：单课删除/课程名批删/同名实例批删/快速删除/调课执行 5 项均有 AppDangerDialog，缺失 0（C4:44-45）
- [ ] ⚠️ 文件导入覆盖缺口：JSON 导入有二次确认；Excel/文本文件/粘贴三屏「覆盖已有课表」直接入库、无确认弹窗（C5:37-38；ImportPreviewComponents.kt:341-430）
- [ ] 备份恢复域台账齐全：恢复覆盖 + WebDAV 解绑各 1 处确认、缺失 0；备份/导出为非破坏性无需确认（C6:38-41）
- [ ] 情侣课表删除=删整张 TA 课表，危险弹窗文案明示且不可恢复（CoupleScheduleSettingsScreen.kt:238-245,419-437）
- [ ] 对话框按钮语言一致：DatePickerModal.kt:27-56 用裸 TextButton（primary/textSecondary），未走 AppAlertDialog/AppDialogActions（D:84）

#### 6. 对比度与色板（阈值＝WCAG 2.2 AA）
- [ ] 对比度判据：正文文本 ≥4.5:1；大号文本 / 非文本 UI ≥3:1；三处用途校准——语义色 on 语义软底按 3:1（软底唯一消费点 iconBg=colors.successSoft，承载图标）、outline on cardBg 按 3:1（M3 必要边界）、divider on cardBg 仅记录不判不达标（docs/agents/contrast-audit.md:45,50-52）
- [ ] 基线：三主题×明暗 132 项中 39 项不达标；书卷深色 0 项，问题集中在浅色（contrast-audit.md:17-18）
- [ ] 语义图标 on 软底：柔绘浅色 amber/amberSoft=**1.43**、通透浅色 info/warning/success=1.75/1.99/2.01、柔绘浅色 7 项全不达标（contrast-audit.md:73-77）
- [ ] 次要文本 textSecondary：通透浅色 on pageBg=**2.92**、柔绘浅色=3.16、书卷浅色=4.92✅；深色三主题 7.3/5.9/7.4✅（contrast-audit.md:60-63）
- [ ] 徽标与主色底文字：柔绘浅色 badgeFg on badgeBg=**2.77**；textOnPrimary on primary=通透浅色 4.02/书卷浅色 3.90/柔绘浅色 3.44；底栏选中态 primary on navSelectedBg 柔绘浅色 2.83 属有意取舍（以字号 11sp→12sp 补偿），不建议按 WCAG 单方面改动（contrast-audit.md:88-117）
- [ ] 色板可区分性：判据 ΔE ≥10 明显可区分、3–10 可察觉但相近、<3 基本无法分辨；六场景最小 ΔE 全部 <10——柔绘浅色 **1.1**(#5/#9)、柔绘深色 1.4、通透浅色 2.5、通透深色 3.4、书卷浅色 4.9、书卷深色 8.0（docs/agents/palette-audit.md:10-21）
- [ ] 色板规格与渲染口径：色板为 **12 色**（非清单所述 20 色）；色条主题淡底 alpha 浅色 **0.30**/深色 **0.38**，需与色板两层同调（提 alpha 至 0.45–0.55 仅可把 ΔE 从 1.1 拉回 4–6，仍 <10）（palette-audit.md:29-33,74）
- [ ] 色觉缺陷：Machado 2009 severity 1.0 下柔绘浅色 #5/#9 红盲 ΔE=**0.5**、书卷深色 #2/#4 绿盲=**0.2**；红绿色盲约占男性 8%，明度差应作为选色硬约束（palette-audit.md:46-58）

#### 7. 字号缩放与无障碍
- [ ] 缩放判据：可用高度 < 行盒 ⇒ 可能裁切，行盒 ≈ fontSize×1.4×**2.0**；实测 2.0× 下固定高度容器垂直裁切 **0 处**（docs/agents/font-scale-audit.md:13,42-46）
- [ ] 单位一致性：全库文本用 sp（无「dp 当字号」）；lineHeight 亦用 sp（91 处硬编码），与 fontSize 等比缩放，不出现「字号变大行高不变」（font-scale-audit.md:14,25-26）
- [ ] 底栏 2.0× 不重叠：28dp 图标 + 0.5dp 间距 + 33.6dp 文字行盒 ≈ **62dp < 64dp** 容器（font-scale-audit.md:28,77）
- [ ] 溢出兜底：单行 + 固定宽度处均有 TextOverflow.Ellipsis；横向 5 处候选均为「固定宽度标签 + 可伸缩文本」（font-scale-audit.md:15）
- [ ] 局限须随结论标注：脚本仅覆盖 Row/Box/Column/Surface/Card/Button/TextButton/Scaffold 8 类容器内直接 fontSize，行高系数 1.4 为经验值；最终依据为真机 adb font_scale 1.0/1.3/1.5/2.0 目视检查且须还原原值（本机实测原值 0.92）（font-scale-audit.md:56-68）
- [ ] 语义与播报：我的页分组标题 heading()；日程页组头 heading 语义；勾选框 Role.Checkbox；完成态 TalkBack 读「已完成/未完成」（AppSettingsComponents.kt:110-114；AgendaScreen.kt:1905-1914,1345-1357；B3:50）

#### 8. 主题切换与深色模式
- [ ] 三主题（通透/柔绘/书卷）× 明/暗均可切换并持久化；组件层零主题分支（R1）（docs/design-system.md:39）
- [ ] 深色优于浅色的既有事实：书卷深色 **0** 项不达标；通透深色 4、柔绘深色 2（contrast-audit.md:11-17）
- [ ] 三材质表面分派：PAPER(书卷)/CARD(通透)/SOFT_BLUR(柔绘)，主题差异只允许发生在表面 token 层（AppSettingsComponents.kt:125-156；design-system.md §2 R5）
- [ ] 色条主题淡底压缩色差：书卷浅色实色 ΔE 4.9 → 柔绘淡底 **1.1**（×0.22）；深色 8.0 → **1.4**（×0.18）；色调映射单射性由门禁 C2 规则守护（基线 0）（palette-audit.md:36-39,66-67）

#### 9. 权限
- [ ] 清单声明齐备：POST_NOTIFICATIONS / SCHEDULE_EXACT_ALARM / ACCESS_NOTIFICATION_POLICY / FOREGROUND_SERVICE(_SPECIAL_USE) / RECEIVE_BOOT_COMPLETED / READ_CALENDAR / WRITE_CALENDAR（androidApp/src/main/AndroidManifest.xml）
- [ ] 入口与引导：提醒设置内「权限与后台」+「提醒能否准时送达，取决于下面几项设置」+「点按各项即可跳转到对应的系统设置页面」（strings.xml:215-217）
- [ ] 精确闹钟/勿扰各有引导弹窗（dialog_title/text_exact_alarm_permission、dialog_title/text_dnd_permission）；缺勿扰时行内警示「（缺少勿扰权限，启用勿扰/静音可能无效）」（strings.xml:248,252-258）
- [ ] 拒绝后文案到位：通知→「通知权限被拒绝，无法接收课程提醒。」；日历→「需要日历权限才能同步到系统日历」/「同步失败，请检查日历权限」（strings.xml:272,850,417）
- [ ] 无存储权限分支：本地 ZIP 备份/恢复走系统文件选择器 rememberFileManager，清单亦无 READ/WRITE_EXTERNAL_STORAGE（BackupScreen.kt:140-161；C6:32）

与 docs/design-system.md 的引用关系：E4 所有视觉与无障碍判定以 design-system.md 为上游——阈值与基线取自 §3 校验与提交门禁（对比度与色板审计脚本即门禁输入）；第 6/8 组引用其 §2 硬性规则 R1（组件层零主题分支）、R2（token 必须显式赋值）、R3（角色→外观单射）、R6（色彩数量不做收敛，故色板仍为 12 色、不建议减色）；第 2 组的文案一致性依据 R4（单一事实来源）；审计中标注的「有意取舍/非缺陷项」（底栏选中态 2.83、柔绘 outline 1.39、divider 1.22–1.69）以 §4 已知例外留档处理，不列为验收不通过。


---

## 附录 A　偏差与缺口汇总（跨稿）

> 本附录是 19 份分节稿中全部 `⚠️` 结论的去重聚合，按主题分组，便于直接排期；单条细节请回正文对应章节。严重度未评级，需产品按版本目标取舍。

### A1　导航与返回

- **Tab 根吞系统返回**：任一 Tab 根按返回无响应（`App.kt:376-383`）；代码注释自认「≥2 段时无法用返回退出 App」，且与预测性返回预览不一致（`App.kt:186-189`）。
- **双击返回退出 / 退出确认未实现**：全库 grep「再按一次 / 退出确认 / double back」零命中 —— 缺失。
- **导入完成后 WebView 未清栈**：`onNavigate` 为 push（`WebBridgeHandler.kt:481-499`；`WebViewScreen.kt:170-178` 无 popSelf），返回键会退回已完成的 WebView 页。
- **无 replace / popUpTo 语义调用**：改选学校只能依赖返回栈自然回退（`Navigation.kt` 与 `App.kt` 调用面）。
- **外部入口全部无深链**：小组件（`WidgetRemoteViews.kt:14-24`）与通知 PendingIntent 只拉 `MainActivity`，`MainActivity.kt:40-66` 不读 intent；Manifest 仅 MAIN/LAUNCHER（`AndroidManifest.xml:59-62`），无 VIEW/BROWSABLE/scheme。
- **无 App Shortcuts、无快捷设置磁贴**：`AndroidManifest.xml` 全文无 shortcuts；androidApp 全量 grep `TileService` 零命中。

### A2　加载 / 空白 / 错误三态

- **错误态覆盖率极低**：`AppErrorState` 全仓仅 2 处（`SchoolSelectionListScreen.kt:228`、`AdapterSelectionScreen.kt:175`），其余页面失败混用 `AppEmptyState` 或仅弹 Toast，且无页内重试入口。
- **空态一律无出口动作**：`AppEmptyState` 的 `actionLabel/onAction` 已实现但零调用（`AppBasicComponents.kt:124-165`）。
- **多个页面无错误态**：今日页（`TodayUiState` 仅 Loading/Success，`TodayScheduleViewModel.kt:331-348`）、日程页（`AgendaViewModel.kt:90-175` 无 error 字段）、我的页（`AppSettingsViewModel.kt:51-56` 无 error 字段）、节次时间（解析失败静默 null）。异常一律静默兜底为空列表或「--:--」。
- **无骨架屏、无空态插图**；周课表页面级无 loading，课程流首帧前可能闪空白网格（`WeeklyScheduleViewModel.kt:77-100,317-356`）。
- **下拉刷新仅今日页一处**（`TodayScheduleScreen.kt:264`）；日程页与周课表无刷新能力。
- **`GRID` 视图未实现**：`ScheduleViewMode` 枚举声明但注释「暂未实现」，未知值回退 WEEK。
- **选校页错误态不可达**：空索引被判定为 loadFailed，与真实「加载失败」共用错误态；适配器页错误态几乎不可达（被伪装成「该校无适配器」空态）。

### A3　文案、术语与空态引导

- **术语三名不一致**：`Destination.ManageCourseTables` / 页面标题「学期管理」（`strings.xml:882`）/ 入口文案「我的课表」（`strings.xml:310`）。
- **空态无引导按钮**：无课表、假期、学期已结束仅有页头文案（B1 §）；我的页无课表时「是否显示周末」仍可点但写入静默 no-op 且控件未置灰（`AppSettingsViewModel.kt:158-169`），`text_no_course_tables`（`strings.xml:304`）存在却未在主页使用。
- **引导入口缺一**：`AddScheduleGuide` 仅挂课表页列表视图空态（`WeeklyScheduleScreen.kt:1170-1179`），网格视图空态无引导；设计稿预期展开三个添加方式（`docs/ia5-onboarding-design.md:88`），代码只展开两个（`AddScheduleGuide.kt:140-162`），无文本导入入口。
- **校验文案窄于校验范围**：`toast_time_invalid`（`strings.xml:150`）只提节次，实际还覆盖自定义时间的非空与先后（`AddEditCourseScreen.kt:183-197`）。
- **硬编码中文未走 strings**：`FileManager.android.kt:270-303`。
- **开关命名风格不一致**：「是否显示周末」(`item_show_weekends`) 为疑问式，与「显示非本周课程」(`item_show_non_current_week`) 名词短语不一致（`strings.xml:279-280`）。
- **周次筛选硬编码 1..20**，未跟随学期总周数（`QuickDeleteScreen.kt:371`）。
- **文案键未被引用**：`today_sheet_delete_occurrence`「删除本次课程」（`strings.xml:64`）在今日页详情弹层未使用（弹层只有关闭/编辑）。
- **今日待办只能勾选**：新建/编辑在日程页（`TodayScheduleScreen.kt:312-313`），README 未说明该限制。
- **搜索未实现**：日程页（`AgendaScreen.kt:314-446`）、我的页（`AppSettingsScreen.kt:734-764`）；选校搜索仅 name/initial 子串，无拼音全拼与容错。
- **「其他」类别在 UI 不可达**：三处集合都只枚举 3 类。
- **「电脑模式」为隐式硬编码行为**，用户零可见性（`AdapterSelectionScreen.kt:77-94`）。

### A4　数据正确性与防误触

- **0 门课仍提示成功**：教务导入 `saveImportedCourses` 无空结果校验（`WebBridgeHandler.kt:375-443`）→ 仍弹「课程导入成功！共导入 0 门课。」（`strings.xml:798`）；文本导入 `applyParseResult` 不检查 `courses.isEmpty`，空预览可继续导入（`TextImportViewModel.kt:237-252`）。
- **覆盖类导入缺二次确认**：Excel / 文本文件 / 粘贴三屏「覆盖已有课表」直接入库（`ImportPreviewComponents.kt:341-430`；`TextImportViewModel.kt:217-235`），仅 JSON 导入有确认（`JsonFileImportScreen.kt:97-99`）。
- **时间合法性不校验**：保存不校验起止节次（`AddEditCourseScreen.kt:183-197`）。
- **新建待办地点输入被静默丢弃**：`addTodo` 无 location 参数（`AgendaViewModel.kt:252-258`）。
- **跨天课程 24H 截断 23:59**，不跨日渲染；跨周移动走拆课（clone 新 Uuid），页内无提示（`WeeklyScheduleViewModel.kt:796-808`）。
- **空白周与全学期无课共用同一空态**，无区分文案；幽灵块被遮挡时静默滤除无提示。
- **情侣课表解绑 = 删除整张 TA 课表**且不可恢复，仅删除成功后复位叠加开关（`CoupleScheduleSettingsScreen.kt:190-245,203-205`）；无「绑定中 / 对方课程加载失败」状态，Flow 静默降级（`CoupleScheduleViewModel.kt:69-108`）。
- **适配器 `resourceFolder` 无校验链**：仅选校页渲染时一处补丁规避漂移；适配器页不预检脚本存在性，失败推迟到 WebView「执行导入」。
- **适配器列表状态用 `remember`**：配置变更 / 进程重建即整段重载。

### A5　无障碍与设计 Token（承接 docs/agents/ 三份审计）

- **对比度**：基线 39/132 不达标；阈值 WCAG 2.2 AA 正文 ≥4.5:1、非文本与大号 ≥3:1（`docs/agents/contrast-audit.md:45`）。
- **色板可区分性**：六场景最小 ΔE 1.1–8.0，全部 <10（`docs/agents/palette-audit.md:20-21`，阈值 ΔE≥10）。
- **字号缩放已达标的项**：2.0× 缩放下裁切 0 处，行盒 ≈ fontSize×1.4×2.0（`docs/agents/font-scale-audit.md:13,42-46`）。
- **Token 未找到定义 5 项**：① 三级文本 token（仅 `AppAlpha` 派生，`AppStyle.kt:274-287`）；② 课程色板具名类型（仅 `DualColor` 列表，`ScheduleGridStyle.kt:15/34`）；③ 设计包 `--radius-*` ↔ `AppShapeTokens` 映射表；④ 通透侧 `iosPageHeaderTokens` 数值；⑤ `MotionSpeed` 设计基线时长表。
- **课程色板另一套 20 色** alpha `0x59`（`AppThemePreset.kt:94-114`）与三主题身份 alpha（`0x40`/`0x33`/`0x1F`）不一致。
- **`outline` 继承 M3 基线** `#79747E`/`#CAC4D0`（阐释见 `AppStyle.kt:102/110`；`IosStyle.kt:136-137`；`ClaudeStyle.kt:157-158`），仅柔绘显式传值。
- **动效倍率对账**：`resolveMotion` 默认带 `MotionSpeed.STANDARD = 2.00f`，故三主题导航实际时长 通透 175ms / 书卷 190ms / 柔绘 210ms，退场 120/120/110ms —— 若设计基线按 1.00 倍计，则实现时长只有设计的一半，需确认。

### A6　合规与外部依赖

- **无隐私政策入口、无数据采集声明**：`strings.xml` grep 隐私|采集|标识|数据收集|privacy 零命中；`MoreOptionsScreen` 亦无相关条目。
- **反馈卡 hint 引导用户提供教务账号密码**（`strings.xml:724`），但无「不会存储密码」承诺文案。
- **外链无兜底**：`openUri` 三处调用（官网 / GitHub / mailto）无 try-catch、无平台分支、无剪贴板兜底，失败静默；全库无剪贴板 API。
- **教务页异常全部由 WebView 自身承担**：登录超时、密码错、验证码 App 均无感知（`WebViewScreen.kt:420-440`；`docs/adapter-sop.md:14,38`）；Android 侧 `onWebViewLoadError` 为空实现（`WebViewScreen.kt:430`），白屏无应用内提示。
- **无网**：选校与适配器列表走本地离线索引，不受影响；WebView 加载失败的四个 `webview_load_error_*` 键（`strings.xml:860-863`）仅桌面端使用（`PlatformWeb.jvm.kt:78`）。


---

## 附录 B　素材来源与核对方法

### B1　分节稿索引

正文各部分的原始分节稿如下（AI 会话产物，按 `AGENTS.md` 红线不入库，仅作溯源用）：

| 分节 | 文件 | 行数 | 覆盖内容 |
| --- | --- | --- | --- |

| 分节 | 文件 | 行数 | 覆盖内容 |
| --- | --- | --- | --- |
| 第一部分 | `A-信息架构与页面树.md` | 249 | 页面树、37 个 Destination、底部 Tab 与返回栈语义、外部入口与深链现状 |
| 第二部分 | `B1-今日页.md` | 74 | 今日页 16 个状态：课表/待办/事件/明日/空态/刷新 |
| 第二部分 | `B2-课表页.md` | 86 | 周课表 14 个状态：网格与列表、滑周、空周、跨天与跨周拆课 |
| 第二部分 | `B3-日程页.md` | 108 | 日程页 14 个状态：月历折叠、分类、完成勾选、长按删除 |
| 第二部分 | `B4-我的页.md` | 87 | 设置主页 10 个状态：四组 12 行入口、空课表、权限与配置提示 |
| 第三部分 | `C1-首启与引导.md` | 46 | 首启门控与空课表引导（AddScheduleGuide） |
| 第三部分 | `C2-教务导入.md` | 43 | 选校→适配器→WebView→执行导入的 14 条异常分支 |
| 第三部分 | `C3-学校与适配器选择.md` | 110 | 选校列表与适配器选择（15 条状态矩阵、12 项缺口） |
| 第三部分 | `C4-课程与课表编辑.md` | 45 | 手动建课/编辑的校验与 8 处二次确认 |
| 第三部分 | `C5-文件与文本导入.md` | 40 | Excel/文本/粘贴/JSON 四类导入与覆盖确认缺口 |
| 第三部分 | `C6-备份与恢复.md` | 41 | WebDAV 与本地 ZIP 两通道、恢复覆盖确认、部分成功反馈 |
| 第三部分 | `C7-学期与节次时间.md` | 55 | 开学日期/总周数/当前周数与节次时长编辑、顺延与重叠校验 |
| 第三部分 | `C8-情侣课表.md` | 55 | 本地配对建表、双色叠加、解绑不可恢复 |
| 第三部分 | `C9-反馈与更多.md` | 48 | 反馈入口与外链通道（无剪贴板兜底、无隐私入口） |
| 第四部分 | `D-公共组件状态.md` | 88 | 约 40 个公共组件的加载/空白/错误/反馈四类状态覆盖 |
| 第五部分 | `E1-token颜色圆角间距.md` | 82 | 颜色 34 个 token、课程色板 2 套、圆角 10、间距 15+4 |
| 第五部分 | `E2-token字号动效.md` | 116 | 字号 15 档、动效字段 43、三主题时长对账 |
| 第六部分 | `E3-验收清单-页面与流程.md` | 78 | 55 条页面与流程验收项（8 组） |
| 第六部分 | `E4-验收清单-状态文案无障碍.md` | 75 | 50 条状态/文案/无障碍/视觉阈值验收项（9 组） |
| 合计 | 19 份 | 1526 | — |

### B2　生成与核对方法

1. **事实来源**：全部结论取自工作区源码与 `strings.xml`（4.64.25 / 420 快照），逐条标注 `路径:行号`；推测与未见实现一律记 `⚠️`，不做补全式臆断。
2. **合成方式**：分节稿按「信息架构 → 主页面 → 核心流程 → 公共组件 → Token → 验收清单」六段合成；正文保持分节稿原文，仅标题降一级，未改写事实。
3. **附录 A** 是对全部分节稿 `⚠️` 条目的去重聚合，按主题重排，未新增事实。
4. **验收清单**（第 6 部分）共 105 条 checkbox，与 `docs/design-system.md` 的 R1/R4/R5 规则、三份审计（对比度 / 字号 / 色板）阈值绑定。
5. **维护约定**：代码版本变动后，`路径:行号` 需重新核对；建议在每次涉及 UI 结构的发版后同步刷新第 1、2 部分。

### B3　未覆盖清单（后续可补）

- Android 桌面小组件（`WidgetRemoteViews.kt`）的渲染细节与刷新链路。
- 通知渠道（`settings/notification/`）逐项配置与免打扰策略。
- iOS 端平台差异实现（本轮以 Android 实现为准）。
- 真机截图对照与像素级视觉走查（本稿为结构与状态级规格）。
