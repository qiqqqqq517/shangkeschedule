# UI 设计系统 · ShangKeSchedule

> v2 风格规范落地文档（Telegram 形态 + 克制毛玻璃）。所有 UI 代码必须从这里取值。
> token 唯一来源：`shared/src/commonMain/kotlin/com/shangkeschedule/ui/theme/AppStyle.kt`

## 1. 设计原则

1. **淡灰白页面底 + 白色大圆角卡片 + 紫渐变头部**，语义淡底色对（chip），胶囊控件
2. **轻盈**：留白节奏统一（16/12/20dp）、少描边、极轻投影、克制用色
3. **深浅色零成本**：组件一律走 token（`appColors()`），禁止按深浅色硬编码
4. **毛玻璃克制使用**：仅用于「悬浮在滚动内容之上」的表面（底栏/吸顶栏/面板），不在页面内部堆叠

## 2. Token 总表

### 颜色（AppColorTokens，深浅两套）
| token | 用途 |
|---|---|
| pageBg / cardBg / cardBgElevated / inputBg / divider | 页面底 / 卡片 / 浮层卡片 / 输入底 / 分隔 |
| textPrimary / textSecondary | 主文字 / 次级文字 |
| primary / success / info / warning / amber / danger / favorite（各配 *Soft）| 语义色对（IconChip 底 + 前景） |
| gradientStart / gradientEnd | Hero 紫渐变 |
| navBarBg / navSelectedBg | 底部导航 |
| badgeBg / badgeFg | 计数徽标 |
| snackbarBg / snackbarFg | 提示条（固定深底浅字） |
| timetableTextOnDark | 利落预设深色课表文字 |

### 形状（AppShape）
card=20dp · heroCard=24dp · chip=16dp · chipSmall=14dp（IconChip/输入框）· menu=16dp · sheetTop=24dp 顶角 · bubble · capsule

### 间距（AppSpacing）
pageHorizontal=16 · cardGap=12 · listGap=20（列表纵向节奏）· cardInner=16 · rowMinHeight=64 · touchMin=48 · chipIcon=48 · fab=56

### 字阶（AppType）
hero=24 · pageTitle=20 · sectionTitle=18 · timeLabel=17 · rowTitle=16 · body=15 · caption=13 · hint=12 · badge=11 · bigNumber=28

### 网格微字号（AppTypeGrid）
courseName=13f / courseMeta=10f（× fontScale）；dayHeader=14 / timeLabel=12 / timeSmall=10 / timeCompact=11 / timeTiny=8（sp）

### 透明度（AppAlpha）
subtle=0.12 · soft=0.25 · dimmed=0.5 · secondary=0.7 —— 新代码禁止魔法 alpha；特殊设计值（如 0.618 黄金比例）豁免需注释

## 3. 组件清单（单一来源）

| 组件 | 位置 | 用途 |
|---|---|---|
| AppCard / AppSelectableCard | components | 白卡 / 选中卡（primarySoft 底 + 2dp 主色描边，支持 onLongClick、containerColor 覆盖） |
| SettingCard / SettingItem / SectionCard / SectionDivider | settings 包 | 设置行（16sp SemiBold + 13sp 灰副标题 + 48dp IconChip + 灰 chevron）/ 分区大卡 |
| AppSectionHeader | components | 分区标题（labelLarge + 主色） |
| AppEmptyState / AppLoading | components | 空状态（淡灰胶囊）/ 加载态 |
| AppFab | components | 56dp 主色圆形 FAB + 按压缩放 |
| AppDialogActions | components | 弹窗操作区（灰字取消 + 主色胶囊确认）；`danger=true` 为红色胶囊，仅限删除/重置/不保存类 |
| AppDangerDialog | components | 危险操作确认对话框 |
| AppTextField | components | 唯一输入框样式（软填充 14dp；支持 label/placeholder/keyboardOptions/Actions/visualTransformation/isError/supportingText/leadingIcon） |
| AppSegmentedControl | components | 分段切换 |
| AppSwitch / AppBadge / TelegramMenu | components | 开关 / 徽标 / 溢出菜单 |
| AppSnackbarHost / AppGlassBottomSheet | components | 提示条宿主 / 毛玻璃底部面板 |
| IconChip | components | 48dp 语义淡底图标 chip |
| AppFab、AppLoading 等 | — | 文案与行为一律由调用方传入，组件层禁止硬编码文案 |

## 4. 毛玻璃表面（Haze 1.7.3）

| 表面 | 参数 |
|---|---|
| 悬浮底栏（NavigationComponents） | blur 16dp + noise 0.12 + navBarBg 86%；壁纸模式纯模糊 + API<31 黑 20% scrim |
| 设置吸顶栏（SettingsScreen） | blur 16dp + pageBg 72% |
| 6 个底部面板（AppGlassBottomSheet） | blur 20dp + noise 0.12 + cardBg 86% |

规则：新玻璃表面必须复用同一 HazeState 传递链（根 wrap `hazeSource` → 面板 `hazeEffect`），API<31 自动 fallback 实色。

## 5. 豁免清单（功能固定色，允许字面量）

取色器渐变与滑块（AdvancedColorPicker）、裁剪器暗幕（ImageCropper）、渐变 Hero 白字、明暗自适应对（CourseBlock/CourseBlockColorUtil）、颜色池预览对比色（StyleSettingsComponents）、壁纸遮罩（Weekly）、品牌图标底色（DeveloperAndIconComponents）、滚轮插值动画字号（NumberPicker）、教师名行内透明编辑（CourseSchemeCard）、require() 断言文案。

## 6. 已知边界

- 预览演示数据与 Bridge/ViewModel 文案已 i18n（suspend `getString`）；新增非组合式文案必须走 suspend `getString`（协程内）或组合式提升
- 二级页 TopAppBar 左对齐、主 Tab 居中（既定规范）
- Weekly 吸顶栏**不做玻璃（设计决策）**：课表网格虽有垂直滚动，但星期表头为固定 pinned 行——玻璃需要把表头改为覆盖式布局才有效，会压缩课表可视面积，收益/成本比不划算；Today 同理（日期头部 pinned）。玻璃表面限定为：底栏、设置吸顶栏、6 个底部面板
- 触控目标 ≥48dp（AppSpacing.touchMin）；底部导航恢复默认 ripple
