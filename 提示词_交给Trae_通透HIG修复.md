# 交给 Trae 的修复提示词 ·「通透」Apple HIG 全站视觉改造

> 使用方式：把本文件整段（或本文件本身）作为提示词交给 Trae，让它在本仓库工作区内执行。
> 本提示词由「视觉核验报告」转写，报告原文见仓库根目录 `视觉核验报告_通透HIG_2026-09-09.md`（Trae 执行前必须先读它）。

---

## 0. 提示词正文（可直接复制给 Trae）

你是一个 Kotlin Multiplatform + Compose Multiplatform（Android/iOS/Desktop 共享 `shared` 模块）项目的资深 UI 工程师。
项目路径：`d:/01课程表/shangkeschedule`，当前分支：`qoder/动画`。

### 0.1 你的目标

把 App 全部页面、弹窗、组件、浮层的视觉统一改造为**「通透」（AIRY）Apple HIG 风格主题**，并修复核验报告中列出的所有问题。**本次只允许改样式（颜色 / 圆角 / 间距 / 字阶 / 阴影 / 形状 / 控件外观 / 动画参数），严禁改功能。**

### 0.2 开工前必读（按顺序）

1. `工作日志.md`（仓库根目录）——了解演进历史与记录规范。
2. `AGENTS.md`（仓库根目录）——项目强制规范：改动后必须写日志、自动版本迭代。
3. `视觉核验报告_通透HIG_2026-09-09.md`（仓库根目录）——本次要修的问题全集（含文件:行号）。
4. `shared/src/commonMain/kotlin/com/shangkeschedule/ui/theme/AppStyle.kt`（重点 342-524 行：token 数据类 + AIRY 变体值 + 分发函数 + CompositionLocal）。
5. `shared/src/commonMain/kotlin/com/shangkeschedule/ui/theme/Theme.kt`（含**一处未提交的中间态改动**，需先处理）。
6. `shared/src/commonMain/kotlin/com/shangkeschedule/ui/components/AppBasicComponents.kt`、`ui/components/StyleComponents.kt`、`ui/components/NavigationComponents.kt`（现有玻璃/基础组件样板）。

### 0.3 现状诊断（务必先理解，避免重复造轮子或硬套）

- ✅ 已存在：形状/间距/字阶 token 数据类与 AIRY 变体值（`appShapes()/appSpacing()/appType()` 访问函数 + `AppShapeTokens/AppSpacingTokens/AppTypeTokens`），`appColors()` 色板 token + `withAppSurfaces` 结构性映射，`LocalAppMotion` 动效 token，玻璃组件（底部胶囊导航 / `AppGlassBottomSheet` / `FloatingCourseBar` / `AppSegmentedControl`）。
- ❌ 缺失（需要你新建/补齐）：
  1. **无 `AppAlertDialog` 封装**（全库 0 命中），约 25 处仍是原生 M3 `AlertDialog` / `DatePickerDialog`。
  2. **无 `airyAppColorTokens` / `airyDarkAppColorTokens`**；`AppStyle.kt` 仅通用浅/深两套色板，深色基底为近黑 `#0F1115/#191C23`（应改 Apple 深灰 `#1C1C1E`）。
  3. **无 `AiryGridStyle`**；`AppThemePreset.AIRY.gridStyle = ScheduleGridStyle.DEFAULT`，默认色板是 M3 高饱和马卡龙 12 色、`courseBlockCornerRadiusDp = 8f`、`hideGridLines = false`。
- ⚠️ **编译阻断（P0-0，最先处理）**：`Theme.kt:103`（未提交改动）以**双参**调用 `appColorTokens(darkTheme, themePreset)`，但 `AppStyle.kt` 只定义了**单参** `fun appColorTokens(isDark: Boolean)`，全库无双参重载 → 当前工作区 `:shared` 编译不过。
- ⚠️ 大量页面仍引用**旧静态常量** `AppShape.` / `AppSpacing.` / `AppType.`（v2 基线：卡片 20dp、页边距 16dp、卡距 12dp、行高 52dp、标题 16~24sp），**AIRY 放大值到不了这些页面**。
- ℹ️ 底部导航实际为 **3 个 Tab**（今日课表 / 课表 / 我的），没有第 4 个 Tab，不要新增 Tab。

### 0.4 硬约束（违反即返工）

- 禁止修改任何：业务逻辑、点击事件、回调、数据结构、页面结构、Tab 顺序、功能入口位置、字符串语义、导航路由。
- 只做视觉层改动。所有 `clickable/onClick` 回调体保持原样。
- 禁止为省事删功能、隐藏入口、改文案、改默认值语义。
- 若报告行号与现状不符，**以代码为准**，并顺手修正报告中失效行号。
- 改动必须能编译通过（见 0.6 验收）。

### 0.5 任务分解（按 P0 → P3 顺序执行，每完成一批先自测编译）

#### P0 前置阻断

**P0-1｜修复编译阻断**
在 `AppStyle.kt` 新增双参分发函数：
`fun appColorTokens(isDark: Boolean, preset: AppThemePreset): AppColorTokens`
- 浅/深两套通用色板保持现状不变（保证经典/云舒/利落零回归）；
- 新增 `airyAppColorTokens()` / `airyDarkAppColorTokens()`：
  - 浅色底 `systemGroupedBackground #F2F2F7`；深色基底 **深灰 `#1C1C1E`**（禁纯黑 `#000000`）；
  - 主色 Apple System Blue `#007AFF`（深色 `#0A84FF`）；
  - 语义色对齐 Apple：Green / Teal / Orange / Yellow / Red / Pink / Indigo；
  - 文字层级 label / secondaryLabel，保证深色/浅色对比度达标；
  - 卡片分三级背景层级（pageBg / cardBg / cardBgElevated）。
- 让 `Theme.kt` 现有未提交改动（形状/间距/字阶 token 注入 + `MaterialTheme.Shapes` 同步映射）真正生效。

**P0-2｜新建 `AppAlertDialog` 并批量替换 ~25 处原生弹窗**
- 位置：`ui/components/AppBasicComponents.kt`；API 与 M3 `AlertDialog` 兼容（便于用 `import …AppAlertDialog as AlertDialog` 零业务改动迁移）。
- 样式：玻璃容器 = `appColors().cardBg` + `appShapes().card` 大圆角 + 零 tonalElevation + 主标题 `appType().pageTitle` SemiBold / 正文 `appType().body` + textSecondary + 自定义轻遮罩 scrim + `AppDialogActions` 胶囊按钮区。
- 迁移清单（文件:行，见报告 A 组）：
  `AppBasicComponents.kt:202`（AppDangerDialog）、`App.kt:285-302`、`TodayScheduleScreen.kt:619`、`CourseTablePickerDialog.kt:124,204`、`DatePickerModal.kt:34`、`ShareManager.kt:37`、`WebDialogHost.kt:85,115,152`、`WebViewScreen.kt:500`、`SettingsScreen.kt:516,668,724,766`、`AppearanceSettingsScreen.kt:314`、`MoreOptionsDialogs.kt:65,110,158,203`、`StyleSettingsComponents.kt:457`、`BackupScreen.kt:409,465`、`ManageCourseTablesScreen.kt:190,231`、`AddEditCourseScreen.kt:417`、`ImportPreviewComponents.kt:237`、`CourseTableConversionDialogs.kt:87,136`、`TimeSlotManagementScreen.kt:391,594,1117`、`CourseTimeDialogs.kt:303-322`（裸 Dialog+Surface，建议改底部玻璃面板或统一弹窗）、`QuickDeleteScreen.kt:436`（M3 DateRangePicker）。
- 日期选择弹窗优先走主题化 `DatePicker`（或沿用 `AppAlertDialog` 容器包裹），确认/取消改胶囊 `AppDialogActions`。

**P0-3｜新增 `AiryGridStyle` 并让 AIRY 预设生效**
- 在 `data/model/AppThemePreset.kt` 为 `AIRY` 指定专属网格样式：圆角 8→**14dp**、节高→**70f**、时间列→**48f**、内边距 4→**5f**、外边距 2→**3f**、`hideGridLines = true`（Apple 极简，隐藏网格线）。
- 12 课程色改为 **Apple 系统色派生的低饱和淡底**，且必须提供**深浅双套**（沿用现有 `courseColorMaps` light/dark 数据结构与 `adaptiveTextColor`，不要新增数据字段）。

#### P1 让 AIRY 放大值到达每一个页面

- 把下列文件中的**静态常量引用**统一改为 `appShapes() / appSpacing() / appType()`：
  - 今日课表页 `TodayScheduleScreen.kt`（最集中）：顶部标题 193、大日期 354、下节课卡 909/977、时间轴 399-400、备注气泡 1212-1235、空态 1245-1253。
  - 课表页：`WeeklyScheduleScreen.kt:264/373/386/769/780/883`、`WeekSelectorBottomSheet.kt:77`。
  - 设置页族：`SettingsScreen.kt:161-162/218/224/239/426/597/614`、`StyleSettingsComponents.kt:185-186/312/329`、`CoupleScheduleSettingsScreen.kt:175-176`、`SemesterSettingsScreen.kt:134-135`、`PersonalizedDisplayScreen.kt:75-76`、`LanguageSettingScreen.kt:108`、`BackupScreen.kt:375`、`NotificationSettingsScreen.kt:54`、`CourseNameListScreen.kt:380/389`、`QuickDeleteScreen.kt:194`、`CourseSchemeCard.kt:76`。
  - 通用组件默认参数（影响面最大）：`StyleComponents.kt:87/120/160/305/318/538/356/581/595/599`、`AppBasicComponents.kt:92/137/230/284/294`、`NavigationComponents.kt:216/309-323/361`、`AlphabetIndexerList.kt:131`、`AdvancedColorPicker.kt:281`、`ImageCropper.kt:216/258`、`DeveloperAndIconComponents.kt:74`。
- **注意**：`SettingsScreen.kt` 的 `SETTING_PADDING / ITEM_SPACING` 是**顶层常量，无法读取 CompositionLocal**，必须下沉为 `@Composable` 函数内的读取；`listGap` 若缺少对应 token 字段，先在 `AppSpacingTokens` 补字段再引用。
- 目标 AIRY 值（与 `AppStyle.kt` 已定义变体保持一致）：卡片 20→**22dp**、菜单项 14→**16dp**、chip 10→**12dp**；页边距 16→**20dp**、卡距 12→**16dp**、卡内距 16→**20dp**、行高 52→**56dp**；hero 34→**36sp**、rowTitle 16→**17sp**、body 15→**16sp**。

#### P2 HIG 观感收口

- **颜色收口**：把直取 `MaterialTheme.colorScheme.*` 的地方收敛到 `appColors()` 语义色：
  `outline → divider`、`onSurfaceVariant → textSecondary`、`error → danger`、`errorContainer → dangerSoft`、`secondaryContainer / primaryContainer → primarySoft`。
  重点（未映射会真分叉）：`ScheduleGrid.kt:87`（网格线 outline 0.2）、`CourseBlock.kt:184-186`、`CourseOtherSelectors.kt:113-117`、`CourseSchemeCard.kt:117`、import 各页错误文案、`QuickDeleteScreen.kt:486-489`、`SchoolSelectionListScreen.kt:245-252`、`StyleSettingsComponents.kt:616/650`、`CourseInstanceListScreen.kt:267`、`ImportPreviewComponents.kt:112`，以及 TodaySchedule / WeeklySchedule / CourseDetailBottomSheet / WeekSelectorBottomSheet / ScheduleGridComponents(246,338-343) / SettingsScreen:563 / MoreOptionsScreen:127 / AppearanceSettingsScreen:519,539-541 / AnimationSettingsScreen:148,172,370,406 / GlassBlurScreen:340-349 / AdvancedSettingsCard:55。
- **分割线**：所有未显式着色的 `HorizontalDivider()` 统一加 `color = appColors().divider`、`thickness = 0.5.dp`（涉及 AppearanceSettings:225/254/414、AnimationSettings:142/166/270、GlassBlur:153、DeveloperAndIconComponents:135-140、StyleSettingsComponents:225/232/256、TimeSlotManagement:275 等）。
- **阴影**：统一淡档（不要厚重）；收敛玻璃件 8/10/14dp 三值分裂与 `QuickDeleteScreen:169`（tonal 8 + shadow 8）叠加。
- **白字前景**：`StyleComponents.kt:599-601`、`AppBasicComponents.kt:137`、`MoreOptionsDialogs.kt:181-186`、`AddEditCourseScreen.kt:313`、`QuickDeleteScreen.kt:178` 改走 token 前景色，保证深色对比度。
- **深色专项**：`CourseBlock.kt:335-352`（黑/白遮罩与 0.618 固定降级 alpha）、`WeeklyScheduleScreen.kt:966-973`（白色扫光 0.14 深色偏亮）、`App.kt:99`（主题外加载占位 Surface 用 M3 默认底色）、`TimetableDefaults.todoCourseColor`（固定青色改为语义色）需适配深色/主题。
- 功能豁免（可不改，但要加注释说明边界）：`AdvancedColorPicker` 等选色器的高饱和 HSV 渐变、`ImageCropper` 恒黑底（iOS 裁剪语义）、`StyleSettingsComponents:310/334` 色池纯白/纯黑底。
- 逐项修正报告 H 组清单：
  - `RadioButton` → 胶囊/对勾行样式（MoreOptionsDialogs 2 处、AnimationSettings:391、BackupScreen:427、LanguageSetting:128、WebDialogHost:161）；
  - `Checkbox`（TodaySchedule:575）→ AppSwitch/胶囊勾选；
  - `DropdownMenu`（WebViewScreen:294、TweakSchedule:319）→ `TelegramMenu`（`appShapes().menu` + cardBg）；
  - M3 `SearchBar`（SchoolSelectionList:337）→ 胶囊 `inputBg` 填充 + 显式 appColors 容器色；
  - PrimaryTabRow 下划线 Tab（SchoolSelectionList:291-313）→ `AppSegmentedControl` 胶囊分段；
  - M3 `NavigationRail`（NavigationComponents:181-200）→ 主题化胶囊 rail；
  - M3 `TopAppBar`（如 TodaySchedule:197）→ 显式主题化容器色；
  - `CourseSchemeCard.kt:94-104` 裸 M3 TextField → `AppTextField`。

#### P3 一致性打磨

- 卡片统一 `AppCard / AppSelectableCard` + `appShapes().card` + `appSpacing().cardInner`（替换 M3 Card 12dp/默认投影：SchoolSelectionList:422-430、AdapterSelection:200-209、AppearanceSettings:489-496、ImportPreviewComponents:107-114、CourseInstanceList:258-270、TweakSchedule:347、TimeSlotManagement:707-744、CourseSchemeCard:72-104）。
- 页边距/留白 token 化（`appSpacing().pageHorizontal`）：AddEditCourse:206、CourseInstanceList:180、ManageCourseTables:149/159、QuickDelete:193、CourseTableConversion:186、import 各页（Excel:92、Json:119、TextFile:108、TextHub:68、TextImport:79）、TweakSchedule:166、TimeSlotManagement:271、TodaySchedule:307 等 20+ 处。
- 全站自检：无硬编码 `Color(0x...)`/`Color.White/Black` 出现在可见 surface/文字/边框/阴影（功能豁免除外）；无原生 M3 组件直用；无厚重阴影；无高饱和大色块。

### 0.6 验收标准（必须全部满足才算完成）

1. **编译**：`gradlew :shared:compileAndroidMain`（或 `compileKotlinAndroid`）通过；若条件允许再跑 `:androidApp:assembleDebug`。
2. **零功能回归**：逐条对照 0.4 硬约束自查，确认无业务/交互改动。
3. **主题隔离**：切到「经典 / 云舒 / 利落」时视觉与改动前一致（零回归）；只有「通透」走 AIRY 值。
4. **深色模式**：全局无纯黑 `#000000` 背景、无写死白色卡片；文字对比度达标；重点复核弹窗、课表网格、设置页、导入页。
5. **重跑核验清单**：按 `视觉核验报告_通透HIG_2026-09-09.md` 第三节 10 项清单逐项自查，输出「修复前 → 修复后」对照结论。
6. **项目规范（强制）**：
   - 开工前已读 `工作日志.md`；
   - 每完成一批改动，在 `工作日志.md`「最新改动」区**顶部倒序追加**记录：`日期 | 版本 | 类型 | 摘要`（写明改动原因、涉及关键文件、验证状态）；
   - 版本迭代：`FIX/小改 → --bump patch`、`FEAT/新主题数据 → --bump minor`，versionCode +1，执行 `python tools/publish_new_version.py --bump <patch|minor> --bump-only`；
   - 若发布正式版/GitHub Release，在 `CHANGELOG.md` 顶部补「最新版本」发布说明。

### 0.7 交付格式

结束时输出：
1. 修改文件清单（路径 + 改动摘要）；
2. 编译与验证状态（✅/❌）；
3. 10 项 HIG 清单「修复前 → 修复后」对照表；
4. 遗留问题与风险（含报告中已修正的行号偏差）；
5. 工作日志中你追加的记录内容。

### 0.8 参考：现有正面样板（照此风格统一，不要另起风格）

- 底部胶囊液态玻璃导航（`NavigationComponents.kt`）：haze 模糊 + token 配色 + 胶囊选中 + 深浅区分。
- `AppGlassBottomSheet`：cardBg 0.86 tint + 20dp 雾 + `appShapes().sheetTop`。
- `AppSegmentedControl`、`AppSnackbarHost`（snackbarBg/snackbarFg 双套）、`AppDialogActions`（胶囊按钮）、`FloatingCourseBar`。
- 动效一律走 `LocalAppMotion` token（轻柔缓动，禁止生硬跳动）。
