# 设置界面融合重构 · 设计交付文档

> 任务：设置界面卡片与配图视觉融合 + 图标语义适配 + 底栏半透明玻璃与滚动隐藏。
> 迭代方式：设计方案 → 真机实施 → 截图自查 → 修复 → 再验证（3 轮循环，18 张真机截图留档 build_qa/）。
> 设计系统基线：`docs/ui-design-system.md`；实现：`ui/components/StyleComponents.kt`（GradientHeroCard/AppHeroMotif）、`ui/components/NavigationComponents.kt`。

## 一、布局说明

### 设置主页（融景卡片结构，自上而下）
1. **Hero 融合卡**（GradientHeroCard）：
   - 主题色渐变底（左上→右下，主色跟随用户主题/动态取色）
   - 柔光斑 ×2：左上 190dp / 右下 150dp 径向渐变白晕（AppAlpha.faint，深色减半），营造景深
   - **课程格纸插画**（AppHeroMotif）：3 条圆角课程块（64/50/38dp，中条 0.55↔1.0 呼吸）+ 时间刻度线（42×6dp），onPrimary tint，8° 旋转，右缘溢出裁剪（offset -14dp × rtlSign），部分穿出卡片边缘被 shape 裁剪——与渐变无缝融合
   - 层级：光斑（最底）< 插画 < 文字（IconChip 48dp + 标题 hero 24sp ExtraBold + 副标题 hint 12sp，文字列 weight(1f) 防窄屏挤压）
2. **功能卡列表**：白色 20dp 圆角卡 × N（语义 IconChip 48dp + 行标题 16sp SemiBold + 副标题 13sp 灰 + chevron），卡片间 12dp
3. **开关行卡**：显示非本周课程 / 是否显示周末 双开关同卡，竖分隔
4. **底部毛玻璃胶囊导航**：内容滚动穿越玻璃后；下滑（累积 72dp）隐藏（translationY + alpha 220ms），上滑/overscroll/切 Tab 恢复

### 母题推广（AppHeroMotif 复用）
- **今日页空态**：淡灰胶囊内主色 tint 缩小版（blockWidth 40dp，pulse=false 静态）——「今天没课」语义贴合
- **推广边界**：仅 Hero/空态两类留白区；内容卡片与二级页不加插画（工具页加插画违背轻盈原则，一屏一个插画焦点）

## 二、配色

| 元素 | 色彩来源 | 适配机制 |
|---|---|---|
| Hero 渐变 | colorScheme.primary（start）+ primary.copy(alpha=0.82)（end） | 跟随用户主题/动态取色/预设种子色 |
| 插画全部元素 | colorScheme.onPrimary × AppAlpha（dimmed 0.5 / 呼吸 0.55↔1.0） | M3 保证任意主色色相下对比协调，禁用 Color.White 硬编码 |
| 光斑 | Color.White × AppAlpha.faint(0.06)，深色减半 | 深色减量防脏灰斑 |
| IconChip | AccentTone 语义色对（successSoft/infoSoft…） | 固定语义，不随主色 |
| 底栏玻璃 | inputBg 85% tint + inputBg 10% scrim + divider 60% 描边 | 嵌入表面语义；深色 token 自动翻转 |

**关键修复（主色同源）**：原实现 token primary 固定紫与 M3 colorScheme.primary（用户主题派生）同屏分裂（FAB 紫 vs 网格高亮棕橙）。修复：Theme.kt 构建 styledTokens（primary/primarySoft/Hero 渐变 = colorScheme 实际色）+ `LocalAppColorTokens` CompositionLocal，`appColors()` 全组件读同步 token。

## 三、图标选型（开源图标库：Material Symbols，Apache 2.0）

| 设置项 | 图标 | 说明 |
|---|---|---|
| 课表导入/导出 | school | 教务语义 |
| 学期设置 | calendar_today | 日历 |
| 自定义时间段 | schedule | 时钟 |
| 管理课表 | book | 课表本体 |
| 课程管理 | edit | 编辑 |
| 情侣课表 | favorite | 情感 |
| 外观与样式 | palette | 调色 |
| **课程提醒设置** | **notifications（新增资源）** | 修正原 info 语义错位；官方 Material Symbols 960 视口，group translateY 适配项目坐标系 |
| 更多 | more_horiz | 三点 |

资源新增方式：官方 SVG → vector XML（group translateY=960 适配负 Y 视口），与既有 24px 家族同规格。

## 四、潜在问题点（自查+验收发现，全部闭环）

| # | 问题 | 修复 | 状态 |
|---|---|---|---|
| 1 | 半透明过度致底栏隐形（62% 白融入浅灰底） | tint 改 inputBg 85% 嵌入表面语义 + 描边 + 14dp 投影 | ✅ 真机验证 |
| 2 | FAB 与底栏重叠（去外层 padding 副作用） | FAB bottom padding 避让（barInset） | ✅ 真机验证 |
| 3 | 顶部 overscroll 底栏不恢复 | onPostScroll 补双侧 overscroll 恢复分支 | ✅ 代码验证 |
| 4 | 插画溢出方向内缩（offset 符号） | 改 -14dp × rtlSign 右缘溢出 | ✅ 真机验证 |
| 5 | 意外副本文件致编译冲突（commonMain NotificationDialogs.kt） | 删除 | ✅ 编译验证 |
| 6 | token 主色与主题主色分裂 | styledTokens + LocalAppColorTokens | ✅ 真机验证 |
| 7 | info 图标语义错位 | notifications 资源 + 调用替换 | ✅ 真机验证 |
| 8 | 窄屏 Hero 文字挤入插画区 | 文字列 weight(1f) | ✅ 代码约束 |

**豁免登记**：教师名行内透明编辑裸 TextField（CourseSchemeCard）、滚轮插值动画字号（NumberPicker）——均为功能性设计，已注释声明。

## 五、迭代修改记录

| 轮次 | 动作 | 发现/修复 |
|---|---|---|
| 设计 | 创意设计师评审草案 | 母题改课程格纸（弃月历：撞学期设置语义）、onPrimary 替代 White、alpha 入档、抽组件 |
| 实施 1 | Hero motif + 光斑 + 阴影弱化 + notifications + 空态复用 | 编译通过、装机 |
| 自查 1（截图） | 底栏隐形 + FAB 重叠 | tint 0.62→0.78；FAB bottom padding |
| 自查 2（截图） | 底栏仍偏淡 | 描边 + shadow 14dp |
| 自查 3（截图） | 色差仍不足 | tint 改 inputBg 嵌入表面语义 |
| 验收 | 视觉验收设计师终审 | 「可发布」+ 3 WARN（溢出方向/顶部恢复/深色证据）→ 全部修复 |

## 六、最终状态

- 真机 v3.22.0(105)：浅色/深色、静止/滚动全场景截图验证 PASS（build_qa/ 18 张留档）
- 编译：`:shared:compileAndroidMain` / `:androidApp:assembleDebug` / `assembleRelease` 三 ABI 全通过
- 验收：UI 视觉验收设计师终审「可发布」，P3 尾项已清
