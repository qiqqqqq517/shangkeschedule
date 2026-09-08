# 液态玻璃（Liquid Glass）样式系统 · 交接文档

> 面向后续接手维护的 AI / 开发者。本文档覆盖 v3.23.6(121) 时点液态玻璃样式的全部现状：
> 组件清单、参数表、接线规范、踩坑记录与遗留事项。接手前请先读 `AGENTS.md` 与 `工作日志.md` 顶部最近几条记录。

---

## 1. 本次改动概要（v3.23.4(119) → v3.23.6(121) · REFACTOR + FIX）

| # | 改动 | 文件 |
|---|------|------|
| 1 | 底栏/玻璃表面三层遮盖再下调（更透明）：light base 0.07→**0.04**、veil 0.03→**0.02**、bodyScrim 0.01→**0.005**；dark 0.16→**0.12**/0.04→**0.03**/0.04→**0.03**；壁纸模式 0.10→**0.08**/0.04→**0.03**/0.12→**0.10** | `ui/theme/LiquidGlass.kt` |
| 2 | **FloatingCourseBar（课程挂起悬浮条）由实心 primaryContainer 胶囊改为液态玻璃胶囊**；新增 `hazeState`/`contentColor`/`isTransparent` 参数，文字色由调用方传入 | `ui/schedule/components/FloatingCourseBar.kt`、`ui/schedule/WeeklyScheduleScreen.kt`（调用处） |
| 3 | **AppFab 支持液态玻璃形态**：新增可选 `hazeState: HazeState?` 参数；传入时渲染为玻璃圆钮（主色图标 + ripple + 按压缩放），不传保持原主色实心（向下兼容） | `ui/components/AppBasicComponents.kt` |
| 4 | 4 个使用 AppFab 的页面接线：页面内容容器加 `.hazeSource(hazeState)`，FAB 调用传入 `hazeState` | `TodayScheduleScreen.kt`、`ManageCourseTablesScreen.kt`、`CourseInstanceListScreen.kt`、`CourseNameListScreen.kt` |
| 5 | **v3.23.6 FIX（真机崩溃）**：Today 页 `hazeSource` 原标在最外层 Box（含 FAB 子树），玻璃 FAB 绘制即抛 `IllegalArgumentException: Modifier.haze nodes can not draw Modifier.hazeChild nodes` 闪退。修复：`hazeSource` 下移到内层内容 Box，FAB 保持在 hazeSource 之外 | `ui/today/TodayScheduleScreen.kt` |

**验证状态（v3.23.6(121) 真机 JFKJRC89T87XXOJJ）**：`:shared:compileAndroidMain` ✅；`:androidApp:assembleDebug` ✅；真机复测全通过——底栏透明（课程格透色）、回到本周圆钮（bounds `[940,2050][1001,2111]` 点击回本周）、FloatingCourseBar 玻璃胶囊（挂起/取消）、玻璃 AppFab×3 页（今日课表/管理课表/课程管理）均正常无崩溃。截图证据 `build_qa/121~130`。

---

## 2. 液态玻璃系统现状（截至 v3.23.5）

### 2.1 核心：`Modifier.liquidGlass(...)`（`ui/theme/LiquidGlass.kt`）

统一玻璃修饰符，绘制层级固定为：

```
shadow(14dp, tokens.shadow)
→ border(1dp, Black@outlineAlpha)          ← 外轮廓线（clip 之前，保留形状外半环）
→ clip(shape)                              ← 必须在 hazeEffect 之前，否则模糊画成方形白条
→ hazeEffect(hazeState) { blur + 3 层 tint + fallbackTint }
→ drawBehind { 4 段 Screen 混合高光 }       ← 45° sheen / 左上椭圆镜面光 / 上 14% 亮带 / 下 10% 反光
→ border ×3（宽→中→窄）                     ← 「暗环→亮环→柔光晕」玻璃厚度感
```

**参数签名**：`liquidGlass(hazeState, shape, containerColor, isTransparent=false, shadowElevation=14.dp, blurRadius=6.dp, enabled=true)`

### 2.2 表面遮盖参数表（v3.23.5 当前值）

三层 tint = `containerColor(或黑)@baseAlpha` + `LiquidGlassBodyScrim(0xFF0B1A2B)@bodyScrim` + `White@veilAlpha`。

| 模式 | baseAlpha | veilAlpha | bodyScrim | 备注 |
|------|-----------|-----------|-----------|------|
| 浅色（默认） | 0.04 | 0.02 | 0.005 | 几乎全透，存在感靠边缘光学 |
| 深色 | 0.12 | 0.03 | 0.03 | |
| 壁纸（isTransparent=true） | 0.08 | 0.03 | 0.10 | tints 基色强制为黑，兼顾透壁纸与可读性 |

边缘光学力度（Screen 混合）：sheen 0.12 / topInner 0.26 / bottomInner 0.15 / specular 0.16 / rimBright 0.88 / rimMid 0.52 / rimDim 0.18 / edgeShade 0.16 / outlineAlpha 0.14（壁纸 0.26）。深色模式各有独立取值，见源码。

### 2.3 已上玻璃的悬浮组件清单

**blur 一律不单独传参**：`liquidGlass` 默认读 `LocalGlassBlurRadius`（由用户在
「外观与样式 → 个性化显示」设定，默认 4dp）。下表 blur 列写的是**默认值**——
**同一个数字，改一处（或让用户调一次）全体同步**。

| 组件 | 位置 | 形态 | blur | 关键说明 |
|------|------|------|------|----------|
| 底栏胶囊 `LiquidBottomTabs` | `NavigationComponents.kt`（约 L305） | `AppShape.capsule` | 4dp 默认·可调 | `isTransparent = 壁纸模式`；`containerColor = bottomBarContainerColor ?: tokens.inputBg`；玻璃件在 hazeSource **之外**（正确拓扑） |
| 「回到本周」圆钮 `BackToCurrentWeekFab` | `WeeklyScheduleScreen.kt`（约 L841） | CircleShape | 4dp 默认·可调 | **v3.24.7 修复**：此前写在页面 hazeSource 子树内 → 背板被过滤 → 从未真正模糊；现与底栏同拓扑同参数 |
| 课程挂起悬浮条 `FloatingCourseBar` | `FloatingCourseBar.kt` | CircleShape 胶囊 | 4dp 默认·可调（原默认 6dp） | 文字/图标用调用方传入的 `contentColor`（= 页面 customTextColor）；关闭钮底 = contentColor@0.12；同 v3.24.7 移出 hazeSource |
| 统一 FAB `AppFab` | `AppBasicComponents.kt` | CircleShape | 4dp 默认·可调 | `hazeState == null` 时回退主色实心；图标 tint = `appColors().primary`；这些页面的 FAB 在 Scaffold `floatingActionButton` slot，天然在 hazeSource 之外，一直正常 |

**可调入口（v3.25.0）**：「我的 → 外观与样式 → 个性化显示」——实时预览 + 滑杆（0~12dp，0=关闭模糊）
+ 四档预设（关闭 0 / 清澈 2 / 标准 4 / 磨砂 8）。值存 `AppSettingsModel.glassBlurRadiusDp`，
经 `LocalGlassBlurRadius` 注入，上表四件同时生效。

**吸顶栏 / 底部面板不属于本族**：`SettingsScreen` 顶栏 `hazeEffect` 16dp、`AppGlassBottomSheet`
20dp 是"大面积面板"语言（自带 tint/noise），刻意与悬浮件不同，勿并入常量。

### 2.4 新页面接入玻璃 FAB 的规范（照抄即可）

```kotlin
// 1) composable 顶部
val hazeState = rememberHazeState()
// 2) Scaffold 内容容器（列表/网格/Column）追加
.modifier = Modifier.fillMaxSize().padding(paddingValues).hazeSource(hazeState)
// 3) AppFab 调用传入
AppFab(..., hazeState = hazeState)
```

**⚠️ 关键约束（v3.23.6 崩溃教训）**：`hazeSource` 必须只标在**内容容器**上，绝不能标在包含玻璃 FAB/玻璃件的外层容器上——haze 库在记录 backdrop 内容层时若遇到子树内的 hazeEffect 节点会直接抛 `IllegalArgumentException` 闪退（Today 页实锤，真机必崩）。玻璃 FAB 必须位于 hazeSource 节点之外（Scaffold floatingActionButton slot 满足此条件，只要 hazeSource 不标在外层 Box 即可）。

壁纸模式（`isTransparent`）仅课表页有，其余页面不用传。

---

## 3. 踩坑记录（改参数前必读）

0. **【v3.24.7 实锤】玻璃件绝不能写在它自己采样的 `hazeSource` 子树内**：
   `HazeEffectNode.updateEffect()` 在「effect 节点的最近祖先 source 与它同一个 HazeState」时，
   按 `area.zIndex < ancestorSourceNode.zIndex` 过滤背板区域。若玻璃件与 source 同为默认
   zIndex=0，则 `0 < 0` 为假 → **areas 被清空** → `draw()` 直接跳过 blur 与 tint，
   玻璃件只剩描边/高光/投影，**看起来"清澈"其实根本没模糊**，且静默无报错。
   真机日志对照（v3.24.6 修复前）：底栏(796x170) `Included=true` + 创建
   `RenderEffectParams(blurRadius=4.0.dp)`；「回到本周」圆钮(132x132) `Included=false`、
   draw 首尾之间无任何 `Drawing HazeArea GraphicsLayer`、无 RenderEffect。
   ⇒ 排查"玻璃不管用/两处不一致"时，先看这个过滤条件，再怀疑参数。
   取值姿势：把 `hazeSource` 只标在**背板内容层**，玻璃件放进与它平级的兄弟 Box（Today 页
   用 Scaffold `floatingActionButton` slot 天然满足；课表页 v3.24.7 已改为兄弟 Box）。
   注意：v3.23.6 那次「把 hazeSource 从外层 Box 下移」之所以让崩溃消失，正是因为踩中了本条
   过滤——崩溃换成了"静默不渲染"，并非真正修好。
0b. **blur 只有一个事实来源**：`LiquidGlass.kt` 的 `LiquidGlassBlurRadius`（= 4dp）是 `liquidGlass`
   的 **blurRadius 默认值 = Dp.Unspecified** 时的兜底，实际取值来自 `LocalGlassBlurRadius`，
   由 `ShangKeScheduleTheme` 从 `AppSettingsModel.glassBlurRadiusDp`（v3.25.0）注入。
   底栏 / 圆钮 / 挂起条 / 玻璃 AppFab **一律不单独传 blurRadius**，因此用户改一次即全端同步。
   历史上四处分别写死 1.5 / 4 / 6 / 4dp，是"改了仍不一致"的直接原因。
   另：haze 的 `HazeInputScale.Auto` 在 blurRadius ≥ 7dp 时会把输入降到 0.3334 倍再模糊，
   降采样本身会额外加糊——所以 7dp 上下不是线性可比，跨档调参必须重新真机看
   （用户侧同理：「磨砂」档 8dp 比「标准」4dp 明显更软，不是翻倍）。
0c. **雾度是用户可调项**（v3.25.0）：入口在「我的 → 外观与样式 → 个性化显示」，
   页面带实时预览（模拟课程格做 hazeSource + 平级玻璃胶囊/圆钮）+ 滑杆（0~12dp，0=关闭）+
   四档预设（关闭 0 / 清澈 2 / 标准 4 / 磨砂 8）。**预览也必须遵守 0 条的拓扑规则**，
   否则预览永远看不出差别。顶栏吸顶玻璃（16dp）与底部面板（20dp）属面板语言，刻意不受此设置影响。
1. **clip 必须在 hazeEffect 之前**：Modifier 链由外向内生效，反序会导致模糊以整节点矩形渲染，玻璃层变成直角白条（v3.22.3 实锤）。
2. **高光混合必须用 `BlendMode.Screen`，禁用 `Plus`**：Plus 直接 RGB 相加，浅色内容上瞬间顶到 255 纯白把底下内容洗掉（v3.23.3 实锤）；Screen 是饱和式加亮，既亮又透。
3. **`DrawScope.drawOutline` 在本项目 Compose 版本不可用**；内壁环用「clip 已裁掉形状外半环的链式 border」实现，宽度顺序 宽→中→窄（后者覆盖前者内侧）。
4. **表面遮盖不能降到 0**：纯白底色上玻璃会彻底消失、图标不可读；0.04+边缘光学是当前「全透但形状可辨」的下限。若用户仍嫌不透，优先减 blur（雾度）而非 alpha。
5. **AppFab 玻璃形态要求页面有 hazeSource 内容**：不传 hazeState 时静默回退实心，编译不报错但观感不统一——新增调用点记得接线。
6. 量化验证工具：历史用 Pillow 采样截图（`build_qa/` 目录，QA 截图不入库），对比玻璃下内容色与原始内容色的通道差。

---

## 4. 遗留事项 / 下一步

- [x] 真机验证已完成（v3.23.6(121) 玻璃化 + 崩溃修复；v3.23.8(123) 底栏 blur 1.5dp + noise 减半"清澈见字"；v3.23.10(125) 导航对比增强定稿为"选中项字号 11→12sp"，均已装机复测通过）。
- [ ] 若用户反馈玻璃 FAB 图标可读性不足，可给图标加轻投影或提高 `blurRadius` 至 6dp。
- [ ] 版本号已迭代至 v3.23.10(125)（`--bump-only`，未构建 Release）；发 Release 前需跑 `:androidApp:assembleRelease` 三 ABI + `CHANGELOG.md` 补条目（见 AGENTS.md 流程）。
- [ ] **配色注意（v3.23.10 定稿）**：底栏选中项配色跟随页面 contentColor / `tokens.navSelectedBg`（v3.23.9 的"选中固定主色"方案被用户否决已回退，勿再引入）；对比感由选中项字号 12sp(hint)+Bold vs 未选中 11sp(badge)+Medium 拉开。另：本项目 Compose 版本 `TextUnit + TextUnit` operator 不可用，字号运算用现成 token 替代。
- [ ] `CourseDetailBottomSheet` / `WeekSelectorBottomSheet` / `AppGlassBottomSheet` 属 ModalBottomSheet 玻璃体系，不在本次范围，勿混用 `liquidGlass`。
- [ ] CourseInstanceListScreen 玻璃 FAB 与另外两设置页同构（编译通过），真机未单独进入复测，风险极低，下轮回归时顺带确认即可。

---

*文档更新：2026-09-08，对应 v3.25.0(134)。同步记录见 `工作日志.md` 顶部。*
