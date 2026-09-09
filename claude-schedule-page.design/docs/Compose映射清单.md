# 设计包 → Compose 落地映射清单

> 本文件说明设计包（HTML 高保真原型）如何逐项落到 `shared` 模块的 Compose Multiplatform 代码里。
> 设计 token 的权威定义见 [`设计token规范.md`](./设计token规范.md)。

---

## 1. 总体策略

设计包与 App 共用同一套 token 语义，落地分三层：

| 层 | 设计包（HTML） | App（Compose） |
| --- | --- | --- |
| 原始色阶 | `assets/claude-tokens.css` 的 `--brand-*` / `--bg-*` / `--text-*` / `--border-*` | `ui/theme/ClaudeStyle.kt` 中的 `Color(0x…)` 常量 |
| 语义 token | `--background` / `--primary` / `--card` … | `ColorScheme` + `AppColorTokens` |
| 组件样式 | `.card` / `.btn` / `.badge` / `.field` … | `AppShapeTokens` / `AppSpacingTokens` / `AppTypeTokens` + 既有组件 |

App 侧新增主题预设 **`CLAUDE`**（显示名「Claude」），与既有 `IOS`（通透）/ `ORIGINAL`（经典）/ `SLEEPY`（云舒）/ `TIMETABLE`（利落）并列，互不影响。

---

## 2. 色板映射

### 2.1 M3 ColorScheme

| M3 槽位 | 浅色 | 深色 | 设计 token 来源 |
| --- | --- | --- | --- |
| `primary` | `#c96442` | `#d97757` | `--brand-500` |
| `onPrimary` | `#ffffff` | `#141413` | `--primary-foreground` |
| `primaryContainer` | brand-50 系淡底 | `#3a2a22` | `--brand-50` |
| `secondary` | `#e9e6dc` | `#faf9f5` | `--secondary` |
| `tertiary` | `#9c87f5` | `#9c87f5` | `--chart-2` |
| `error` | `#d64545` | `#ef4444` | `--error-500` |
| `background` / `surface` | `#faf9f5` | `#262624` | `--bg-100` |
| `surfaceContainer*` | `#f5f4ef` / `#ffffff` | `#2c2c2b` / `#1b1b19` | `--bg-200` / `--bg-50` |

### 2.2 AppColorTokens（组件层实际读取的语义色）

| 字段 | 浅色 | 深色 |
| --- | --- | --- |
| `pageBg` | `#faf9f5` | `#262624` |
| `cardBg` | `#f5f4ef` | `#2c2c2b` |
| `cardBgElevated` | `#ffffff` | `#30302e` |
| `inputBg` | `#ede9de` | `#1b1b19` |
| `divider` | `#dad9d4` | `#3e3e38` |
| `textPrimary` | `#3d3929` | `#f1f1ef` |
| `textSecondary` | `#6e6d68` | `#b7b5a9` |
| `primary` / `primarySoft` | `#c96442` / 品牌淡底 | `#d97757` / 品牌淡底 |
| `success` | `#788c5d` | `#8ca06f` |
| `danger` | `#d64545` | `#ef4444` |
| `gradientStart` → `gradientEnd` | `#c96442` → `#b05730` | `#d97757` → `#b05730` |
| `snackbarBg` | `#28261b` | `#30302e` |

> 完整 32 个字段的取值以 `ui/theme/ClaudeStyle.kt` 为准。

### 2.3 课表课程块配色

`ScheduleGridStyle.courseColorMaps` 使用 5 个 chart 色循环成 12 组：

| 序号 | 浅色底 | 深色底 |
| --- | --- | --- |
| 1 | `--chart-1` 低透明 | `#b05730` |
| 2 | `--chart-2` 低透明 | `#9c87f5` |
| 3 | `--chart-3` 低透明 | `#1a1915` |
| 4 | `--chart-4` 低透明 | `#2f2b48` |
| 5 | `--chart-5` 低透明 | `#b4552d` |

---

## 3. 字体映射

字体文件已内置到 `shared/src/commonMain/composeResources/font/`：

| 文件 | 字族 | 字重 |
| --- | --- | --- |
| `Poppins-Regular.ttf` | Poppins | 400 |
| `Poppins-Medium.ttf` | Poppins | 500 |
| `Poppins-SemiBold.ttf` | Poppins | 600 |
| `Poppins-Bold.ttf` | Poppins | 700 |
| `Lora-Variable.ttf` | Lora | 300–700（可变字体） |
| `Newsreader-Variable.ttf` | Newsreader | 200–800（可变字体） |
| `GeistMono-Variable.ttf` | Geist Mono | 300–700（可变字体） |

字族 → 字阶槽位：

| 字族 | Compose 槽位 |
| --- | --- |
| Newsreader | `displayLarge` / `displayMedium` / `displaySmall` / `headlineLarge`（页面大标题、统计数字） |
| Poppins | `titleLarge` / `titleMedium` / `bodyLarge` / `bodyMedium` / `labelLarge` / `labelMedium`（UI 主力） |
| Lora | `bodyLarge`（阅读正文，与设计稿 `--font-serif` 对齐） |
| Geist Mono | token / 代码展示（可选，不影响布局） |

中文回退：设计稿字体不含 CJK 字形，Android/iOS/桌面均由系统字体渲染中文，与设计稿表现一致；无需额外打包中文字体。

---

## 4. 形状 / 间距 / 字阶映射

| 设计 token | 值 | Compose |
| --- | --- | --- |
| `--radius` | 16px | `AppShapeTokens.card = RoundedCornerShape(16.dp)` |
| `--radius-xl` | 20px | `heroCard` / `sheetTop` = 20.dp |
| `--radius-md` | 12px | `menu` / `chip` = 12.dp |
| `--radius-sm` | 8px | `chipSmall` / 课程块 = 8.dp |
| `--radius-full` | 9999px | `capsule` / `fab` = `RoundedCornerShape(50)` |
| 页面水平内边距 `×5` | 20px | `AppSpacingTokens.pageHorizontal = 20.dp` |
| 卡片间距 `×3` | 12px | `cardGap = 12.dp` |
| 分区间距 `×6` | 24px | `listGap = 20.dp`（App 既有节奏） |
| 卡片内边距 `×4` | 16px | `cardInner = 16.dp` |
| 页面标题 28px | — | `AppTypeTokens.pageTitle = 28.sp` |
| 卡片标题 18px | — | `rowTitle = 18.sp` |
| 正文 15px | — | `body = 15.sp` |
| 辅助 12–13px | — | `caption = 13.sp` / `hint = 12.sp` |

---

## 5. 页面映射

| 设计包页面 | App 对应界面 | 说明 |
| --- | --- | --- |
| `pages/今日日程.html` | `ui/today/TodayScheduleScreen.kt` | 今日课程卡片流、进行中/已结束态、明日预览 |
| `pages/周课表.html` | `ui/schedule/WeeklyScheduleScreen.kt` + `components/ScheduleGrid.kt` | 7 天网格、课程块、周次切换 |
| `pages/多学期管理.html` | `ui/settings/coursetables/ManageCourseTablesScreen.kt` + `SemesterSettingsScreen.kt` | 学期列表、进度、复制/删除 |
| `pages/教务导入.html` | `ui/schoolselection/list/SchoolSelectionListScreen.kt` + `web/WebViewScreen.kt` | 四步导入向导 |
| `pages/组件预览.html` | `ui/components/AppBasicComponents.kt` 等 | 组件与 token 基线 |

### 5.1 交互落点

| 设计包交互 | Compose 实现 |
| --- | --- |
| 底栏导航（4 项） | `ui/components/NavigationComponents.kt` |
| 课程详情底部弹层 | `ModalBottomSheet` + `ui/schedule/components/CourseDetailBottomSheet.kt` |
| 周次切换 | `ui/schedule/components/WeekSelectorBottomSheet.kt` |
| 学期删除确认 | `AlertDialog`（`destructive` 按钮色取 `AppColorTokens.danger`） |
| 导入步骤指示器 | `ui/schoolselection` 现有步骤状态机 |
| 深色模式 | `AppThemeMode.DARK` / `FOLLOW_SYSTEM` |

### 5.2 断点落点

设计包的 4 档断点对应 Compose 的窗口尺寸类：

| 设计包断点 | Compose |
| --- | --- |
| `phone-sm` / `phone` | `WindowWidthSizeClass.Compact` |
| `tablet` | `WindowWidthSizeClass.Medium`（侧栏导航） |
| `desktop` | `WindowWidthSizeClass.Expanded`（侧栏 + 详情栏） |

App 已引入 `compose-material3-adaptive`，可用 `currentWindowAdaptiveInfo()` 读取宽度类，按上表切换「底栏 ↔ 侧栏」与「单列 ↔ 双列」布局。

---

## 6. 落地检查清单

- [x] 色板逐值对齐（浅色 / 深色两套）
- [x] 字体文件内置（Poppins 静态 + Lora / Newsreader / Geist Mono 可变）
- [x] 圆角 / 间距 / 字阶 token 化
- [x] 课表课程块配色与设计稿 chart 色一致
- [x] 新增 `CLAUDE` 主题预设，不影响既有 4 套预设
- [x] `:androidApp:assembleDebug` 编译通过
- [ ] iOS / 桌面端实机视觉复核（需真机环境）
