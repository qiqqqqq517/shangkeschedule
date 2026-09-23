# 桌面小组件显示优化方案

> 状态：**提案（本轮只出方案，不含实现）** ｜ 适用版本：v3.66.4(265) 之后
> 约束依据：`docs/design-system.md`（R1–R6）、RemoteViews 能力边界（§9）
> 本文所有「现状值」均已在源码中逐项核对（见 §1 的出处列），所有对比度数值均由脚本实算（§附录 A）。

---

## 0. 结论摘要

**一句话**：小组件目前是「**半联动**」状态 —— 课程色板已经跟着主题走，但**卡片外壳（背景/文字/分隔线/圆角/气泡底色）在三套主题下完全相同**，且用的是与三套主题都不沾边的 Material 3 紫调色表。修的是外壳，不是配色数量。

**最值得改的 3 项**（详见 §2.5）：

| # | 项目 | 为什么排第一 |
|---|---|---|
| 1 | **外壳主题联动**（新增 widget token 组，§6） | 用户「换了主题桌面毫无变化」的直接解；影响 4 个规格 × 全部状态；且是后续所有配色改动的地基 |
| 2 | **浅色三级文字色 AA 失败 + 建立对比度门禁**（§2.2 B2） | 这是**客观缺陷**不是审美问题：`#808B96` 在 `#FEF7FF` 上实测 **3.30:1 < 4.5:1**。先立基线，后续每次改色都能自动拦 |
| 3 | **「进行中 / 还有多久下课」表达**（§2.3 C1+C2） | 小组件最高频的用途就是这两件事，而**数据今天就已经拿得到**，只是没渲染出来。功能价值 > 装饰价值 |

趣味细节（§5）刻意排在 P1/P2：它建立在 1–3 之上，地基没修好时加彩蛋只是往裂缝上贴贴纸。

---

## 1. 现状测绘（含对原始描述的 5 处修正）

### 1.1 核对结论

原始描述与源码一致的部分：4 个规格的声明尺寸、`targetCell`、圆角 18dp、色表 8 个色值、条目 A/B 的字号与内边距、`widget_indicator_shape` 4dp×40dp 纯白、气泡底 `#3498DB`、4×N 条数公式 `(minHeight−24)/36`。以下 5 处需要修正或补充，它们直接影响方案可行性。

### 1.2 修正 1（关键）：日夜机制不止 `layout-night/`，且这决定了主题联动**必须走运行时 setter**

实际只有 **3 个**布局有 `layout-night/` 变体：

| 有 night 变体 | 无 night 变体（靠 `values-night/colors.xml` 换色） |
|---|---|
| `widget_tiny_circle.xml`、`widget_item_course_common.xml`、`widget_item_course_list_node.xml` | **4 个主布局**（tiny / compact / double_days / list_vertical）、`widget_divider_horizontal.xml`、`widget_loading_placeholder.xml` |

前 3 个之所以要 night 变体，是为了**双 ID 技巧**：日间布局里是 `@+id/course_indicator`，夜间布局里是 `@+id/course_indicator_dark`，渲染时两个 ID 都 set 一次，只有一个会命中（`WidgetCourseColor.kt` 的 `applyCourseColor(lightViewId, darkViewId, …)`）。

**这条事实是整份方案的技术枢纽**：`values-night/` 只能按**系统深浅色**切换资源，**无法按 App 主题切换**。因此「小组件跟随用户主题」**不可能**靠资源限定符实现，只能在渲染时把颜色**用 RemoteViews 动作压进去**。§6 的方案就是围绕这一点设计的。

### 1.3 修正 2（关键）：小组件**已经**半联动，不是「完全没用到主题」

| 环节 | 现状 |
|---|---|
| 课程色板 | **已跟随主题**。`DataStore<ScheduleGridStyleProto>` 是 `@Single` 单例（`DataStoreModule.kt:21-33`），小组件与 App 读**同一个文件**；用户切主题时 `StyleSettingsRepository.applyStylePreset()` 把该预设整套样式（含 `course_color_maps`）**快照写入**该 DataStore |
| 卡片外壳 | **完全不跟随**。`widget_bg` `#FEF7FF` 是 M3 紫调浅色，不在任何一套主题 token 里 |
| 主题身份 | **拿不到**。小组件只注入了 `WidgetRepository` + `styleDataStore`（`WidgetUpdateHelper.kt:31-34`），**从未读** `AppSettingsModel.themePreset` |

所以准确的说法是：**色条会变色，卡片不会** —— 观感上像「贴了别人家的壳」。诊断与验收都要按这个口径写，否则会误判改动范围。

### 1.4 修正 3：`drawable-night/widget_loading_background.xml` 与日间**逐字节同义**

两个文件都只有 `solid=@color/widget_bg` + `corners radius=21dp`，唯一差别是所在限定符目录。因为 `@color/widget_bg` 本身已在 `values-night` 里换值，这个 night 副本是**纯冗余文件**（P2 清理）。

### 1.5 修正 4：加载卡圆角 21dp ≠ 真实卡圆角 18dp → 首帧圆角跳变

`widget_loading_background` 21dp、`widget_bg_rounded` 18dp。组件首次添加/重启后先显示 loading 卡，数据到位换成真实卡，**圆角会肉眼可见地跳一下**（P1，改动量极小）。

### 1.6 修正 5：4×N 条数公式的**行高常量偏小约 30%**，会算多

`LIST_ROW_DP = 36`（`WidgetUpdateHelper.kt:212`），但条目 B 的实际内容高按当前字号估算：

```
课程名 13sp ≈ 15.6dp + 间隔 2dp + 地点 10sp ≈ 12dp + 间隔 1dp + 教师 10sp ≈ 12dp = ≈42.6dp
+ paddingVertical 3dp×2 = ≈48.6dp（条目本体）
+ 条目间分隔线 1dp + marginTop/Bottom 2dp×2 = ≈5dp
→ 每条实际占位 ≈ 53dp，而公式按 36dp 算 ⇒ 条数偏多约 47%
```

以 4×2（`minHeight` 110dp）为例：公式给 `(110−24)/36 = 2` 条，实际需要 `24 + 2×53 = 130dp > 110dp` → **列表溢出、末条被裁**。N 越大偏得越多。

同时 `OPTION_APPWIDGET_MIN_HEIGHT` 取的是**可缩放下界**而非当前高度（当前高度落在 MIN/MAX 之间），这是**第二个方向的误差**（偏小）。两个误差方向相反、量级不同，净效果是**偏高**。

> ⚠️ 上面的 42.6/48.6/53dp 是按 `includeFontPadding=false` 与 fontScale=1.0 的**估算**，落地前必须真机实测标定（见 §8 验收项 V6）。方向性结论（36dp 明显偏小、条数会溢出）是确定的。

### 1.7 补充发现：条目 B 的色条比内容矮 17dp，上下不齐

`widget_item_course_list_node.xml:15-21` 的色条是**固定 `4dp × 30dp`** + `centerVertical`，而右侧文字列高约 47dp（见上），时间列约 28dp。三者在同一个 `wrap_content` 的 `RelativeLayout` 里居中 → 色条比文字块**上下各短约 8.5dp**，视觉上「色条没对齐文字」。

对比条目 A（`widget_item_course_common.xml`）用的是 `layout_height="match_parent"` + 上下 2dp margin，是**正确写法**。条目 B 应改成同样的写法（P1，一行改动）。

---

## 2. 诊断

### 2.1 A 类 · 品牌一致性

| 编号 | 问题 | 证据 |
|---|---|---|
| A1 | 小组件色表与三套主题**无一处对齐** | `widget_bg #FEF7FF` 是 M3 紫调浅色（surface 系列），三套主题的 cardBg 分别是 `#F5F4EF` / `#FFFFFF` / `#FCFBFD`，**没有一个是它** |
| A2 | 外壳零主题联动（半联动，见 §1.3） | 小组件从未读 `themePreset` |
| A3 | 圆角 18dp **不对齐任何主题** | 主题 card 圆角：书卷 **16dp** / 通透 **16dp** / 柔绘 **24dp**（`ClaudeStyle.kt:244`、`IosStyle.kt:233`、`SoftStyle.kt:257`） |
| A4 | 气泡底 `#3498DB` 是 Material 通用蓝 | 读起来像系统默认角标；且它只在**取色失败**时才露出来（正常被 colorFilter 覆盖），即「平时看不见、出错时最丑」 |
| A5 | 主文字 `#2C3E50` 是冷灰蓝，与三套主题的 textPrimary（暖褐 `#3D3929` / 纯黑 `#000000` / 暖灰紫 `#4A4756`）都不同族 | — |

### 2.2 B 类 · 信息层级

| 编号 | 问题 | 证据 |
|---|---|---|
| B1 | 字号挤在 10–12sp，层级几乎只靠 bold | 主文字 12/13/14sp、次文字 10/11sp、提示 10/11sp |
| B2 | **三级文字色在浅色下不可达 AA** —— 这是结构性缺陷，不是调色问题 | `#808B96` on `#FEF7FF` 实测 **3.30:1**（需 4.5:1），而它用在 **10sp** 提示文字上（属 normal text，不适用大字号豁免）。深色档 `#8E8E93` on `#141218` = 5.70:1 反而合格 ⇒ **浅色独有缺陷** |
| B3 | 取色兜底色**两种模式下都几乎不可见** | `#F2F4F4` on `#FEF7FF` = **1.05:1**；`#3A3A3C` on `#141218` = **1.64:1**。v3.66.4 修掉了「不加 colorFilter 导致崩溃」，但兜底色本身仍等于「消失」（非文本 UI 需 ≥3:1）。即「不再崩，但依然看不见」 |
| B4 | 四规格共用同一套条目样式，无密度分级 | 2×1 与 4×N 都 include 同一个 item（2×1 反而没用条目） |
| B5 | 分隔线有**两套并行实现**、参数不一致 | `widget_divider_horizontal.xml`（水平边距 6dp、上下 2dp）vs `widget_list_vertical_native.xml:54-66` 内联 ImageView（无边距）；前者是 1dp 实线、alpha 1，后者 alpha 0.2 —— 同一产品里两种分隔线 |
| B6 | 主文字之外**没有可用的第三层级色** | 见 §6.4：三套主题的 textSecondary 有 2 套本身就不达 AA，再加第三级不可能达标 ⇒ 必须改用**排版手段**表达第三级 |

### 2.3 C 类 · 状态表达

| 编号 | 问题 | 证据 / 机会 |
|---|---|---|
| C1 | **「进行中」与「未开始」视觉完全相同** | `WidgetCourseSelection.remainingToday()` 的过滤条件是 `end_time > now`，**包含正在上的那节课** ⇒ 「进行中」的信息**今天就已经在数据里**，只是没渲染 |
| C2 | 无时间进度表达（还剩多久下课） | 同上，`start_time`/`end_time` 都在 `WidgetCourseProto` 里 |
| C3 | 空状态 / 假期态只有居中一行字 | 4 个规格的 `container_status` / `container_full_status` 都是单行 TextView |
| C4 | 无课态文案**信息量过低** | Tiny 只有「今天没有课程」/「今日课程已结束」两种，而用户此刻最想知道的是**「下一节课几点」** |
| C5 | 假期态**丢掉了可算的信息** | 「假期中 / 期待新学期」——而学期开始日期是可读的，「距开学还有 N 天」是能算出来的 |

### 2.4 D 类 · 趣味细节

| 编号 | 问题 |
|---|---|
| D1 | 无任何品牌个性表达（无彩蛋、无俏皮文案、无成就） |
| D2 | 空/假期态是「趣味与惊喜最明显的机会位」，目前是纯灰字 |
| D3 | **4 张预览图已过期**：`widget_preview_*.webp` 全部 mtime `2026-09-06`，而 widget 布局在 `2026-09-22`（v3.65.6、v3.65.8）与 `2026-09-23`（v3.66.4）**连续改过 3 轮**，含**全部布局的字号调整** ⇒ 预览图**必然**与真机不符 |
| D4 | 桌面选择器里 4 个组件的名称是「超小课程2x1」这类工程名，没有一句话说明各自用途 |

### 2.5 最值得改的前 3 项（与 §0 一致，此处给理由）

**① 外壳主题联动（A1+A2+A3+A5）—— 排第一**
- 影响面：4 规格 × 全状态 × 3 主题 × 深浅 = **24 个组合**，是覆盖面最大的单项；
- 用户可感知：切主题是用户的**主动行为**，切完桌面没反应是「产品没听见我」的典型挫败；
- 它是**地基**：B2（对比度）、B6（第三层级色）、趣味文案的配色都依赖同一套 widget token。

**② 浅色文字色 AA 失败 + 对比度门禁（B2+B3）—— 排第二**
- 这是**客观可判定**的缺陷（3.30:1 vs 4.5:1），不是「更美观」这类主观项；
- 修它需要动「三级色阶」这个结构（§2.2 B6），越晚改，越多地方依赖旧色值；
- 同时把对比度检查**脚本化**（§8 V1），后续每次改色自动拦——一次投入长期收益。

**③ 进行中 / 进度表达（C1+C2+C4）—— 排第三**
- 小组件被瞥一眼的场景 90% 是「我现在上什么 / 还有多久下课 / 下一节几点」；
- 数据**已经具备**，属于「纯渲染改动」，性价比最高；
- 顺带解决 C4（无课态给出「下一节几点」）。

> 为什么趣味不排前三：趣味是**乘法**（在地基之上放大好感），地基没修好时加彩蛋是往裂缝上贴贴纸。趣味本身成本很低（§5 多为纯文案 + 现有 API），放在 P1 即可。

---

## 3. 优化方向（3 选 1）

### 方向 A ·「延续书卷暖调」
- **气质**：单一暖奶油底 + 赤陶主色，把书卷的纸感带到桌面；不区分主题。
- **做法**：直接把外壳色表换成书卷色板（`#F5F4EF` / `#3D3929` / `#C96442`），圆角 16dp。
- **适用人群**：书卷主题用户（也是「暖调」审美偏好的泛用户）。
- **代价**：**最小**——只改 8 个色值 + 1 个圆角 + 4 张预览图，**不动 token 结构、不碰 proto**。
- **风险**：通透/柔绘用户会觉得「我的桌面被染成别人的主题了」；与 R5「主题身份必须保留」的精神相悖。

### 方向 B ·「三主题全联动 · token 驱动」✅ **推荐**
- **气质**：小组件成为 App 的**延伸**而非附件——桌面卡片与 App 内卡片同族。
- **做法**：新增 widget 专用 token 组（L1）+ 单一访问器（L2）+ 在快照里**解析成值**下发，渲染层零主题知识（§6）。
- **适用人群**：全部用户；尤其多设备/常切主题的用户。
- **代价**：**中**——新增 1 个 data class（约 12 个字段）× 3 套主题 × 深浅 = 6 份显式赋值（R2 要求不得依赖默认值）；proto 新增 1 个 message；4 个 Renderer 改读快照字段而非资源色。
- **风险**：**最大风险是「直接复用 app token 会引入 AA 失败」**——已实测：通透 textSecondary 在卡底上 **3.26:1**、柔绘 **3.38:1**，三套主题的 primary 作为**文字**全部不达标（3.33–4.02:1）。所以**必须**用对比度校正后的 widget 专用值，不能直接映射（§6.4 给了具体校正值）。这个风险是**已知且有解**的，不是未知风险。

### 方向 C ·「极简系统感 · 跟随系统」
- **气质**：放弃品牌色，跟随系统深浅 + 动态取色，做成「系统原生小组件」的样子。
- **做法**：只用 `?android:attr/colorBackground` 系资源，圆角对齐系统 `system_app_widget_background_radius`。
- **适用人群**：极简/原生审美用户；第三方 Launcher 用户。
- **代价**：**中**（需要 API 31+ 分支 + 兜底）。
- **风险**：**与产品方向冲突**——三套主题是这个 App 的核心卖点，「跟随系统」等于把主题系统的价值在桌面端清零；且动态取色会再次引入不可控对比度（App 侧已有过「暖色墙纸生成棕/土色容器」的教训，见 `AppStyle.kt:263-265` 注释）。

**建议**：走 **方向 B**。若排期不足以支撑 token 改造，**退到方向 A 作为过渡**（A 是 B 的真子集，A 的色值改动在 B 里会被 token 化保留，不会白做）。

---

## 4. 分规格具体改法

> 通用约定：
> - `W-*` 指 §6.3 定义的 **widget token**，落地时必须按主题取值，**不得**在 Renderer 里写死。
> - 「进行中」= `minutesOf(start_time) <= now < minutesOf(end_time)` 且 `!is_skipped`（数据已有，见 §2.3 C1）。
> - 所有 sp 均为 fontScale=1.0 下的值；行高按 `includeFontPadding=false` + `lineSpacingMultiplier=1.0`。

> ⚠️ **前置约束：卡片必须四周留 2–4dp 外边距，否则主题圆角会被系统吃掉。**
> Android 12+ 起，Launcher 会对组件**边界**施加自己的圆角裁剪（系统 dimen `system_app_widget_background_radius`，Pixel 上约 28dp）。
> 若卡片**贴满**组件边界，可见圆角 = `max(卡片圆角, 系统圆角)` ⇒ **书卷/通透的 16dp 与柔绘的 24dp 都会被系统 28dp 覆盖，三套主题圆角看起来完全一样**，「主题圆角」这个改动等于白做。
> 解法：组件根布局**不加背景**，卡片四周留 **2–4dp** 外边距（`W-cardInset`），让卡片自身圆角在系统裁剪内**可见**。
> 这条同时影响 4 个规格的可用内容高度 ⇒ §4.4 的行数公式必须把 `W-cardInset × 2` 计入（`W-listHeaderDp` 里一并算）。**需真机在 Android 12+ 与 11 及以下各验证一次**（见 §8 V16）。

### 4.1 超小课程 2×1（Tiny）—— 目标：一眼看出「现在上什么 / 还有多久下课」

| 项 | 现状 | 改为 |
|---|---|---|
| 结构 | 卡片 padding 10dp → [左侧文字列] + [右上 22dp 圆气泡] | 卡片 padding `W-cardPadding`（书卷 12 / 通透 10 / 柔绘 14dp）→ **[3dp 色条] + [竖排：课程名 / 一行元信息] + [右上 22dp 状态圆]** |
| 新增 | — | **左侧 3dp 色条**（与其余 3 个规格一致，让 2×1 也有课程色识别；`match_parent` + 上下 2dp margin，与条目 A 同写法） |
| 课程名 | 14sp bold 主文字 | **13sp / 700** / `W-textPrimary`（降 1sp 以腾出元信息行；`maxLines=1` + `ellipsize=end` 保持） |
| 元信息行 | 时间 11sp 次文字（`08:30 - 23:59`）+ 地点 11sp 提示色（**两行**） | **合并为一行 10sp / 500**：<br>· 进行中 → `剩余 25 分`（`W-accent`，**这是 C2 的落点**）<br>· 未开始 → `14:00 · 教一101`（`W-textSecondary`） |
| 状态圆 22dp | 裸数字（剩余节数）10sp bold；底 = 硬编码蓝被 colorFilter 染色 | **两态区分（解决 C1）**：<br>· **进行中** → **实心**：底 = 课程色**实色档**（`dark_color`），数字 = 剩余分钟数，前景色**按亮度算法选**（§4.5）<br>· **未开始** → **空心**：底透明 + **2dp `W-accent` 描边环**，数字 = 今日剩余节数，数字用 `W-textPrimary`（**不能用 accent**，实测 accent 作文字仅 3.33–4.02:1，不达 4.5） |
| 进行中附加 | — | 卡片**顶部叠 2dp `W-accent` 横线**（非文本，≥3:1 达标）——与 App 内既有的「进行中主色描边 + 顶部 2dp 主色线」语言对齐（`TodayScheduleScreen.kt:459`） |
| 圆角 | 18dp | `W-cardRadius`（书卷/通透 16dp、柔绘 24dp） |
| 颜色 | 独立色表 | 全部走 `W-*`（§6.3） |

> ⚠️ 2×1 只有 `40dp` 高，10dp padding 后内容区仅 20dp。**13sp 课程名 + 10sp 元信息 ≈ 15.6 + 12 = 27.6dp > 20dp**。因此 2×1 **必须**二选一：(a) padding 降到 6dp 且课程名降 12sp（内容区 28dp）；或 (b) 元信息行与课程名**同行右对齐**。**建议 (a)**，并在 §8 V7 用真机截图确认不裁切。这是本规格最容易翻车的一点。

### 4.2 紧凑课程 2×2（Compact）

| 项 | 现状 | 改为 |
|---|---|---|
| 头部左 | 星期几 11sp bold **次文字** | **12sp / 700 / `W-textPrimary`**（它是这一格的标题，不该是次文字色） |
| 头部右 | 周次 10sp 次文字 | **10sp / 600 / `W-textSecondary` + `letterSpacing 0.12em`**（eyebrow 处理，对齐 App 的 eyebrow 惯例） |
| 条目 | 共用条目 A | 保持，但**进行中态**：色条 `setColorFilter` 用**实色档全不透明**；未开始用**同色 `alpha 0x99`** ⇒ 一行代码得到「进行中更实」的层级（colorFilter 尊重 alpha，零结构改动） |
| 进行中附加 | — | 条目课程名前**拼 `● ` 前缀**（纯字符串拼接，零成本）+ 卡片顶部 2dp `W-accent` 横线 |
| 底部统计 | `剩余 3 节` 10sp 提示色 | **10sp / 500 / `W-textSecondary`**（原为不达标的提示色，见 B2） |
| 分隔线 | `widget_divider_horizontal`（水平边距 6dp） | 统一为**一种**：`W-divider` 色 + 1dp + 水平边距 6dp + 上下 2dp（与 4×N 内联那份合并成同一实现，解决 B5） |
| 圆角 / padding | 18dp / 10dp | `W-cardRadius` / `W-cardPadding` |
| 空 / 假期态 | 单行字（假期态 16sp bold + 12sp） | 见 §5（文案 + 图标位） |

### 4.3 近日课程 4×2（DoubleDays）

| 项 | 现状 | 改为 |
|---|---|---|
| 右上周次 | 10sp 次文字 | 10sp / 600 / `W-textSecondary` + `letterSpacing 0.12em` |
| 栏头日期 | `今天 1.07 周三` 11sp bold 次文字 | **12sp / 700 / `W-textPrimary`**；`今天`/`明天` 作为 eyebrow 段（10sp / 600 / `letterSpacing 0.12em` / `W-textSecondary`） |
| 栏头统计 | `剩余 2 节` / `共有 4 节` 10sp 提示色 | 10sp / 500 / `W-textSecondary`（达标色） |
| **今日 / 明日对比** | 两栏视觉权重相同 | **右栏（明日）整体降一档**：文字用 `W-textSecondary`、色条 alpha `0x99` ⇒ 建立「今天优先」的阅读顺序（当前两栏完全等价，是层级扁平的另一处表现） |
| 中间分隔线 | 1dp `widget_divider` + **alpha 0.2** | 1dp `W-divider` + **alpha 1.0**（主题 divider 本身就是低对比色：书卷 `#DAD9D4`、柔绘 `0x1F4A4756`；再叠 0.2 会双重衰减到几乎不可见） |
| 进行中 | 无 | 左栏首条按 §4.2 的进行中处理 |
| 圆角 / padding | 18dp / 10dp | `W-cardRadius` / `W-cardPadding` |
| 空状态 | 居中 `无课程` 11sp 提示色 | 居中 `无课程` 11sp `W-textSecondary` + **下一节时间**（若今日已无课但明日有课，此处可给「明天第一节 08:00」） |

### 4.4 垂直列表课表 4×N（ListVertical）

| 项 | 现状 | 改为 |
|---|---|---|
| 头部 | `第 8 周  周五` 14sp bold 主文字 | 保持 14sp / 700 / `W-textPrimary`；`第 8 周` 与 `周五` 之间改用 **` · `** 分隔（当前是两个半角空格，排版不稳） |
| 头部右 | `今日还有 3 节课` 11sp 次文字 | **10sp / 500 / `W-textSecondary`**（11sp 与 14sp 差距过小，降一档拉开层级） |
| 条目 B 色条 | **4dp × 30dp 固定**（比内容矮 17dp，§1.7） | **`4dp × match_parent` + 上下 2dp margin**（与条目 A 一致）——**修掉对齐缺陷** |
| 条目 B 时间列 | 10sp bold 主 / 10sp bold 次 | 开始 `10sp / 700 / W-textPrimary`；结束 `10sp / 500 / W-textSecondary`（当前两个都 bold，主次不分） |
| 条目 B 间距 | 时间列与文字列间 `FrameLayout 2dp`（硬编码空 View） | 保留但改为 `layout_marginTop`（减少一层 View，降低 RemoteViews 体积） |
| 进行中 | 无 | 进行中条目：色条实色 + 课程名前 `● ` + 该条 `W-accent` 顶部 2dp 线 |
| **条数推算** | `(minHeight−24)/36`，**偏高约 47%**（§1.6） | **改为** `(height − W-listHeaderDp) / W-listRowDp`，其中：<br>· `height` 取 **`OPTION_APPWIDGET_MAX_HEIGHT`**（当前高度上界；现用 MIN 是下界，偏小）<br>· `W-listRowDp` **实测标定**（估算 ≈52dp，含分隔线）<br>· `W-listHeaderDp` 随 `W-cardPadding` 变化 ⇒ 必须由 token 驱动，不能是常量 24 |
| 圆角 / padding | 18dp / 8dp | `W-cardRadius` / `W-cardPadding` |
| 无课 / 假期 | 全屏 `假期中 / 期待新学期` | 见 §5 |

> ⚠️ **`minResizeHeight=70dp` 与条目高 ≈53dp 冲突**：70dp 高度下 `(70−24)/53 < 1` ⇒ 至少显示 1 条也会溢出。建议把 `minResizeHeight` 提到 **`W-listHeaderDp + W-listRowDp`（≈76dp）**，或允许该尺寸下走「单行摘要」形态（不是列表）。**这是必须决策的一项**，否则 4×N 的最小尺寸永远裁切。

### 4.5 气泡前景色算法（Tiny，保证 AA）

气泡填充**课程色实色档**时，白字/黑字不能写死——实测 `#FFFFFF` on `#279B32`（松绿）= **3.61:1 不达标**，而该色用深字是 5.82:1。

**规则**：渲染时算课程色的相对亮度 `L`（sRGB → 线性化 → `0.2126R+0.7152G+0.0722B`），
- `L > 0.179` → 用**深字** `#000000`
- 否则 → 用**白字** `#FFFFFF`

在 `L = 0.179` 处两种选择都等于 **4.58:1**，因此该算法**可证明**恒 ≥ 4.58:1 ≥ 4.5:1。实现在 Kotlin 渲染侧（App 进程），不受 RemoteViews 限制。

### 4.6 字号 / 字重 token 化（解决 B1）

| token | 值 | 用途 |
|---|---|---|
| `W-titleTiny` | 12sp / 700 | 2×1 课程名（见 §4.1 警告） |
| `W-titleCompact` | 12sp / 700 | 2×2、4×2 课程名 |
| `W-titleList` | 13sp / 700 | 4×N 课程名 |
| `W-header` | 14sp / 700 | 4×N 头部 |
| `W-headerSub` | 12sp / 700 | 2×2、4×2 栏头日期 |
| `W-eyebrow` | 10sp / 600 / `letterSpacing 0.12em` | 周次、`今天`/`明天` |
| `W-meta` | 10sp / 400 | 地点、教师 |
| `W-metaStrong` | 10sp / 700 | 4×N 开始时间 |
| `W-stat` | 10sp / 500 | 底部统计 |
| `W-bubble` | 10sp / 700 | 气泡数字 |
| `W-statusTitle` | 16sp / 700 | 空/假期大标题 |
| `W-statusMsg` | 12sp / 400 | 空/假期副文案 |

**层级靠三件事拉开，而不是靠第四级颜色**：字号（10/12/14sp）、字重（400/500/600/700）、字间距（eyebrow 0.12em）。这正是 §2.2 B6 的解法——三套主题里有两套的 textSecondary 本身才勉强/不达 AA，**再加一级更浅的颜色必然失败**，所以第三级只能用排版表达。

---

## 5. 趣味与惊喜细节（含 RemoteViews 可行性判定）

> 判定口径：只允许 §四 列出的控件与 `setTextViewText` / `setInt` / `setImageViewResource` / `setImageViewBitmap` / `setViewVisibility` / `setOnClickPendingIntent` / `addView` / `removeAllViews` 等动作；**无动画、无自绘 View、无自定义字体**。

### 5.1 ✅ 可行的 5 个（按推荐度排序）

**① 下课倒计时（Chronometer）—— 强烈推荐，本方案最大的「活」元素**
- **做法**：进行中时，把元信息行换成 `Chronometer`，`setChronometer(base = 下课时刻, format = "距下课 %s", started = true)` + `setChronometerCountDown(true)`。
- **效果**：**每秒自更新**，`距下课 24:59 → 24:58 → …`，**完全不需要刷新组件**（不占 15 分钟 tick 预算、不耗电）。
- **为什么记住它**：AppWidget 里几乎所有内容都是「死的」，Chronometer 是少数「活的」原生能力。一个每秒跳动的下课倒计时，是桌面小组件里最有存在感的细节。
- **成本**：**小**（1 个 View + 2 个 set 动作）。
- ⚠️ **待验证**：`setChronometerCountDown` 需 **API 24+**；且部分第三方 Launcher 对 Chronometer 的支持不一致 ⇒ 必须有静态兜底文案（降级为 `剩余 25 分`）。见 §8 V8。

**② 空状态 / 假期的「有人味」文案 + 可算信息（解决 C3+C4+C5+D2）**
- **做法**：纯 `setTextViewText` + 字符串资源，零结构改动。空状态从「一行灰字」升级为 **[图标位 ImageView] + 主文案 + 副文案**。
- **文案示例**（副文案承载**真实信息**，不只是俏皮）：
  | 场景 | 主文案 | 副文案（信息量所在） |
  |---|---|---|
  | 今日无课 | `今天没有课` | `下一节：周四 08:00 高等数学` |
  | 今日已结束 | `今天的课都上完啦` | `明天第一节 08:00` |
  | 假期中 | `假期中` | `距开学还有 23 天` ← **可算（学期开始日期可读）** |
  | 明日无课 | `明天也没课` | `周末愉快` |
- **图标位**：`ImageView` + `setImageViewResource` 切 3~4 个 drawable（☕/🎒/🌙 之类的极简单色图标）。**不要用 emoji 字符**——emoji 在不同 ROM 上字形差异极大，且部分 Launcher 字体不含 emoji 会显示豆腐块。
- **成本**：**小**（文案 + 3 个 drawable）。

**③ 分区点击跳转 —— 「实用趣味」，用户会自己发现**
- **做法**：`setOnClickPendingIntent` 已具备（`bindWidgetClickIntent`）。扩展为**分区**：点课程名 → 打开该课详情/今日课表；点周次/头部 → 打开设置；点空状态 → 打开添加课程。
- **为什么记住它**：用户在桌面上「点一下试试」的探索欲，是这个功能唯一能被发现的路径；一旦命中，就是「原来可以这样」的小惊喜。
- ⚠️ RemoteViews 的 `setOnClickPendingIntent` 只能设**整个 View**，不能设「View 的某个区域」⇒ 分区靠**拆成多个 View**（现有布局已有多个 TextView，天然满足）。
- **成本**：**小**（PendingIntent 已存在，只是多绑几个 ID）。

**④ 当天最后一节课的彩蛋文案**
- **做法**：`todayRemaining.size == 1 && 进行中` 时，副文案换成 `今天最后一节，撑住！`。纯字符串判断 + `setTextViewText`。
- **成本**：**极小**（1 个 if + 1 条字符串）。
- **注意**：只改文案不改布局 ⇒ 零布局风险；且必须**同时保留**「剩余 N 分」信息（趣味不能吃掉功能）。

**⑤ 进度环（setImageViewBitmap）—— 解决 C2 的「视觉版」**
- **做法**：在 **App 进程**用 `Canvas` 画一段圆弧到 `Bitmap`（如 44×44px），`setImageViewBitmap` 推进去。渲染发生在 App 侧，**不违反「RemoteViews 不能自绘」**（限制的是 RemoteViews 里的 View 不能自绘）。
- **用途**：2×2 的进行中条目、或 2×1 的状态圆外圈，显示「这节课已过 60%」。
- ⚠️ **成本与风险**：**中**。Bitmap 走 Binder 传输，尺寸/内存需克制（见 §8 V9）。**建议排在 ① 之后**——Chronometer 用 1% 的成本给了「时间流逝感」的大部分价值，进度环是锦上添花。

### 5.2 ❌ 明确不可行（不要排期，避免返工）

| 想做的 | 为什么不行 | 替代 |
|---|---|---|
| 呼吸圆点 / 脉冲动画（App 内进行中就有） | RemoteViews **无动画/过渡**，无属性动画、无 `ObjectAnimator` | 静态实心点 + `W-accent` 顶线；「活」的部分交给 Chronometer |
| 完成任务的庆祝动画（撒花、缩放弹出） | 同上；且 AppWidget 无 Overlay 能力 | 文案层面表达（`今天的课都上完啦`） |
| Konami 式按键彩蛋 / 长按手势 / 多击计数 | RemoteViews **收不到任意按键**，也无法监听长按、多击 | 分区点击跳转（§5.1 ③）是唯一可用的交互扩展 |
| 自定义字体（Poppins / Newsreader）**按主题切换** | 字体族只能**静态写在布局 XML**（`android:fontFamily`），RemoteViews **无运行时 setTypeface 动作**；而 Android **没有按 App 主题选择布局的资源限定符** ⇒ 主题级字族需**复制整套布局**（书卷/通透/柔绘各一份 × 日夜） | 保持系统字体，用**字号/字重/字间距**表达主题气质（§4.6）；字族作为 P3 可选，且需先验证 `@font/` 资源在 RemoteViews 布局中是否被 Launcher 正确解析（**待真机验证**） |
| 文字自动缩放适配 | 无 `autoSizeTextType` 支持 | 固定字号 + `ellipsize=end` + 真机截图核对 |
| 圆角裁剪子元素（卡片内条目被卡片圆角裁切） | 只能靠 shape drawable **自身**圆角，父容器无法裁子 | 条目自身用带圆角的 shape；卡片内边距足够时视觉上不需要裁切 |
| 每主题的**色条几何**（宽度/高度按主题变化） | 色条宽高写在布局 XML 里，`setLayoutParams` 不是 remotable 动作 ⇒ 需复制 item 布局 | 色条几何**固定 4dp 药丸**，主题身份只由**颜色**承担（§6.5 有一条曲线方案，但脆弱，不建议） |
| 秒级整体重绘 / 高刷新率动效 | 组件刷新受 WorkManager 15 分钟 tick 与系统广播约束 | Chronometer（§5.1 ①）是唯一例外 |
| 渐变背景（按主题） | shape 的 `<gradient>` **静态可用**，但主题化需多份 drawable + 切换 | 可行但**不建议**：小组件面积小，渐变收益低、drawable 数量翻倍 |

---

## 6. 三主题联动方案

### 6.1 规则约束（`docs/design-system.md`）

| 规则 | 要求 | 对本方案的约束 |
|---|---|---|
| **R1** | L3 组件层**不得**出现 `AppThemePreset.` / `when(preset)` / `isClaudePreset` 等 | 4 个 Renderer **必须**零主题知识 |
| **R2** | L1 每个 token **必须由每套主题显式赋值**，不得依赖 data class 默认值 | 6 份（3 主题 × 深浅）**全部显式**写 |
| **R4** | 取 token 只走**一条路径**（`appColorTokens(isDark, preset)`），不得直接调 `iosAppColorTokens()` | widget token 组不得另立色板 |
| **L2** | `when(preset)` **只允许**出现在 `ui/theme/` 适配层 | 分派逻辑必须落在 `ui/theme/`，不能落在 widget 包 |

### 6.2 方案：**加 token（不是另立 token 层）**，并在**适配边界把值解析进快照**

判定链（按 R1 的两步法）：
1. **「是值的差异吗？」→ 是。** 小组件要的每个差异（卡底、圆角、文字色、分隔线、accent、内边距）都是**值**的差异，不是结构/形态差异 ⇒ **加 token**。
2. 小组件是**第 4 个消费方**（App 页面、桌面组件共用同一批角色）⇒ 差异应表达为**同一 token 体系的扩展**，而不是平行体系。

**为什么不能直接复用现有 token：**
- `appColors()` 是 `@Composable`，widget 渲染在**非 Compose** 环境 ⇒ 用不了；
- 非 Composable 的 `appColorTokens(isDark, preset)` **可用**，但**角色不够**：缺「卡片圆角」「色条」「气泡」「第三级文字色」「widget 内边距」这些**只有桌面组件才需要**的角色；
- 更关键：三套主题的 `textSecondary` **直接在 widget 卡底上不达 AA**（通透 3.26:1、柔绘 3.38:1），`primary` 作为**文字**三套全不达标（3.33–4.02:1）⇒ **必须**有对比度校正后的 widget 专用值。

**落地结构（4 步）：**

```
① L1 结构定义（AppStyle.kt，与 AppShapeTokens 并列）
   data class AppWidgetTokens(...)          // 字段见 §6.3，无默认值（R2）

② L1 三主题显式赋值（ClaudeStyle.kt / IosStyle.kt / SoftStyle.kt）
   val claudeWidgetTokens: AppWidgetTokens = AppWidgetTokens(...)
   val iosWidgetTokens:    AppWidgetTokens = AppWidgetTokens(...)
   val softWidgetTokens:   AppWidgetTokens = AppWidgetTokens(...)
   // 深浅各一套 ⇒ 共 6 份，全部显式（R2）

③ L2 唯一分派（AppStyle.kt 或 Theme.kt 的适配区，允许 when(preset)）
   fun appWidgetTokens(isDark: Boolean, preset: AppThemePreset): AppWidgetTokens

④ 适配边界解析成「值」，写进快照（WidgetUpdateHelper.performUpdate，非 ui/ 包，但它是 widget 的适配层）
   preset = appSettingsRepository.getAppSettingsOnce().themePreset   // 新增注入
   widgetStyle = appWidgetTokens(isSystemDark, preset)               // 解析成具体值
   snapshot = WidgetSnapshot(..., widget_style = widgetStyle.toProto())
   ⇒ 4 个 Renderer 只读 snapshot.widget_style.***，完全不认识 AppThemePreset
```

**关键点**：分派发生在 **③**（`ui/theme/`，L2 唯一允许处）与 **④**（适配层），**Renderer 一行 `when(preset)` 都没有** ⇒ 满足 R1。若将来把 `check_theme_leak.py` 的扫描范围扩到 widget 包（**建议做**，见 §6.6），C1 计数为 **0**。

**为什么不把 preset 传进 Renderer 让它自己选 token**：那会让 Renderer 认识 `AppThemePreset`，直接违反 R1。**快照里传值、不传身份**，是唯一同时满足 R1 与「Renderer 能拿到主题差异」的形态。

### 6.3 widget token 组（字段与 6 份取值）

| token | 书卷 浅 | 书卷 深 | 通透 浅 | 通透 深 | 柔绘 浅 | 柔绘 深 | 说明 |
|---|---|---|---|---|---|---|---|
| `cardBg` | `#F5F4EF` | `#2C2C2B` | `#FFFFFF` | `#1C1C1E` | `#FCFBFD` | `#2A2830` | = 各主题 `cardBg` |
| `cardRadius` | 16dp | 16dp | 16dp | 16dp | **24dp** | 24dp | = 各主题 `card` 圆角 |
| `textPrimary` | `#3D3929` | `#F1F1EF` | `#000000` | `#FFFFFF` | `#4A4756` | `#E8E5EE` | = 各主题 `textPrimary` |
| `textSecondary` | `#6E6D68` | `#B7B5A9` | **`#6C6C70`** | `#98989D` | **`#6E6A7C`** | `#9C98A8` | **通透/柔绘浅色经校正**（原值 3.26/3.38:1 → 校正后 5.23/5.07:1） |
| `divider` | `#DAD9D4` | `#3E3E38` | `#493C3C43` | `#99545458` | `#1F4A4756` | `#1FE8E5EE` | = 各主题 `divider` |
| `accent` | `#C96442` | `#D97757` | `#007AFF` | `#0A84FF` | `#7C86C9` | `#9AA3DC` | = 各主题 `primary`；**仅用于非文本**（描边/色条/顶线，≥3:1 全部达标） |
| `indicatorFallback` | `#6E6D68` | `#B7B5A9` | `#6C6C70` | `#98989D` | `#6E6A7C` | `#9C98A8` | **直接取 `textSecondary`** ⇒ 天然 ≥5:1，彻底修掉 B3（现为 1.05/1.64:1） |
| `cardPadding` | 12dp | 12dp | 10dp | 10dp | 14dp | 14dp | 主题密度 |
| `cardInset` | 3dp | 3dp | 2dp | 2dp | 4dp | 4dp | **卡片四周外边距**（见 §4 前置约束）；不设则主题圆角被系统裁剪覆盖 |
| `listHeaderDp` | 26dp | 26dp | 24dp | 24dp | 28dp | 28dp | 随 padding 变化（**不能是常量 24**） |
| `listRowDp` | 52dp | 52dp | 52dp | 52dp | 52dp | 52dp | **待真机标定**（§1.6，现为 36dp） |
| `bubbleSize` | 22dp | 22dp | 22dp | 22dp | 24dp | 24dp | 柔绘更圆润 |

> **R4 合规**：本表所有「= 各主题 xxx」的字段，实现时应写成 `cardBg = colors.cardBg`（**引用**同一份主题 token），而不是复制字面量 ⇒ 保持单一事实来源。只有**确实需要校正**的 `textSecondary` 与**新角色**（radius/padding/row 高度）才写独立值，并在注释里写明校正理由与实测对比度。

### 6.4 校正值的依据（实测，§附录 A 可复算）

| 场景 | 原值对比度 | 校正后 | 结论 |
|---|---|---|---|
| 通透 `textSecondary` `#8E8E93` on `#FFFFFF` | **3.26:1** ❌ | `#6C6C70` → **5.23:1** ✅ | 必须校正 |
| 柔绘 `textSecondary` `#8B8798` on `#FCFBFD` | **3.38:1** ❌ | `#6E6A7C` → **5.07:1** ✅ | 必须校正 |
| 书卷 `textSecondary` `#6E6D68` on `#F5F4EF` | 4.71:1 ✅ | 保持 | 合格，不动 |
| 三套主题 `primary` 作**文字** | 3.33 / 3.54 / 4.02:1 ❌ | **不用作文字** | 仅作非文本装饰 |
| 三套主题 `primary` 作**非文本**（描边/色条） | 3.33 / 3.54 / 4.02:1 ✅（需 3:1） | 保持 | 合格 |
| 现有 `widget_text_hint` `#808B96` on `#FEF7FF` | **3.30:1** ❌ | 取消该层级（§4.6） | 结构性修正 |
| 现有兜底条 `#F2F4F4` on `#FEF7FF` | **1.05:1** ❌ | 取 `textSecondary` | 结构性修正 |

### 6.5 已知做不到的部分（诚实边界）

- **主题级字族**：见 §5.2，需复制整套布局，不建议。
- **主题级色条几何**：曲线方案是「item 布局里把色条写成 `layout_width="wrap_content"`，再让每主题的 shape drawable 用 `<size android:width="Ndp"/>` 给出内在宽度，用 `setImageViewResource` 切换」——**技术可行但脆弱**（依赖 drawable 内在尺寸语义、`wrap_content` 与 `match_parent` 混用易出意外），**建议 P2 且默认不做**。
- **主题级布局结构差异**（如柔绘想换一种排布）：需要布局变体，而 Android 无「按 App 主题」的资源限定符 ⇒ 只能在代码里选 layout id，那会把主题分支带进渲染层（违反 R1），除非把 layout id 也作为 token 放进快照。**属于「可做但收益低」**，本方案不做。

### 6.6 配套门禁（建议）

`check_theme_leak.py` 目前只扫 `shared/src/commonMain/kotlin/.../ui/**`，**扫不到 widget 包**（`androidApp/src/main/kotlin/com/shangkeschedule/widget/**`）。建议：

1. 把 widget 包**纳入 C1 扫描范围**，基线设 **0**（因为按本方案 Renderer 里确实一处都没有）；
2. 新增 **C5 检查：widget token 对比度门禁** —— 把 §附录 A 的脚本固化为 `tools/check_widget_contrast.py`，校验 6 组合 × 全部文本色对 ≥4.5:1、非文本 ≥3:1。**这是把 B2 这类缺陷变成「不可能再引入」的唯一办法**（棘轮语义：只允许下降）。

---

## 7. 优先级与工作量

| 优先级 | 项目 | 工作量 | 风险 | 依赖 |
|---|---|---|---|---|
| **P0** | 修 `widget_text_hint` 浅色 AA 失败（3.30:1）+ 建立对比度脚本门禁（§6.6-2） | **小** | 低 | 无 |
| **P0** | 修取色兜底色不可见（B3，1.05/1.64:1）→ 取 `textSecondary` | **小** | 低 | 无 |
| **P0** | 4×N 条数公式行高常量（§1.6）+ 改用 `MAX_HEIGHT` | **中** | **中**（需真机标定；改错会导致显示条数突变） | §8 V6 实测 |
| **P0** | 条目 B 色条改 `match_parent`（§1.7 对齐缺陷） | **小** | 低 | 无 |
| **P1** | **widget token 组 + 三主题 6 份显式赋值 + 快照下发**（§6） | **大** | **中**（proto 变更 + 4 Renderer 改造；但风险已知有解） | P0 门禁先立 |
| **P1** | 外壳全部改走 token（卡底/圆角/文字/分隔线/accent） | **中** | 低 | 上一项 |
| **P1** | 进行中 / 未开始区分（实心-空心气泡、色条 alpha、顶线） | **中** | 低 | P1 token |
| **P1** | 元信息行合并 + 「剩余 N 分」/「下一节 几点」（C2+C4） | **小** | 低 | 无 |
| **P1** | 加载卡圆角 21dp → 18dp（§1.5 首帧跳变） | **极小** | 低 | 无 |
| **P1** | 分隔线两套实现合一（B5） | **小** | 低 | 无 |
| **P1** | 空/假期态文案升级 + 图标位（§5.1 ②） | **小** | 低 | 无 |
| **P1** | Chronometer 下课倒计时（§5.1 ①） | **小** | **中**（Launcher 兼容性 + API 24，需兜底） | §8 V8 |
| **P1** | 分区点击跳转（§5.1 ③） | **小** | 低 | 无 |
| **P1** | 重出 4 张预览图（D3，已过期 3 轮） | **小** | 低 | 视觉定稿后 |
| **P2** | 气泡亮度算法选前景色（§4.5，保证 AA） | **小** | 低 | 无 |
| **P2** | 进度环 `setImageViewBitmap`（§5.1 ⑤） | **中** | 中（Binder/内存） | §8 V9 |
| **P2** | 字号/字重/字间距 token 化（§4.6） | **小** | 低 | 无 |
| **P2** | 当天最后一节课彩蛋文案（§5.1 ④） | **极小** | 低 | 无 |
| **P2** | 删除冗余 `drawable-night/widget_loading_background.xml`（§1.4） | **极小** | 低 | 无 |
| **P2** | `minResizeHeight` 70dp 与条目高冲突的决策（§4.4） | **小** | 低 | 需产品决策 |
| **P2** | 桌面选择器 `android:description`（API 31+，D4） | **极小** | 低 | 无 |
| **P3** | 主题级字族（§5.2） | **大** | **高** | 需先验证 + 复制布局 |
| **P3** | 主题级色条几何（§6.5） | **中** | **高**（脆弱） | 不建议 |

**建议排期**：P0（1 个小批次，可独立发 patch）→ P1 token 改造（一个 minor）→ P1 体验项（同批次或紧随）→ P2 打磨。

---

## 8. 验收标准（可客观判定）

| 编号 | 验收项 | 判定方式 |
|---|---|---|
| **V1** | **WCAG 2.2 AA 对比度**：所有**文本**色对 ≥ **4.5:1**；所有**非文本** UI（色条/描边/进度环/分隔线）≥ **3:1** | 跑 `tools/check_widget_contrast.py`，覆盖 **3 主题 × 深浅 × 全部文本/非文本角色**；**0 项 FAIL** 方可通过。附录 A 是可复算基线 |
| **V2** | 浅色提示类文字不再出现 3.30:1 这类值 | 同上脚本；且 `#808B96` 不再作为 10sp 文本色出现（`grep` 可查） |
| **V3** | 取色兜底可见 | 构造 `colorInt = -1` 与 `colorInt = 999` 两种异常数据，截图中色条**肉眼可见**且脚本判定 ≥3:1 |
| **V4** | 三主题联动生效 | 依次切到 书卷/通透/柔绘，**各截浅色+深色共 6 张**；卡底/圆角/文字色/分隔线/accent **与 §6.3 表逐项一致**（圆角在截图上量取，误差 ≤1dp） |
| **V5** | Renderer 零主题分支 | `grep -rn "AppThemePreset\|when(preset)\|isClaudePreset" androidApp/src/main/kotlin/com/shangkeschedule/widget/` **结果为空**；`check_theme_leak.py` 扩范围后 C1 计数 = **0** |
| **V6** | 4×N 条数不溢出 | 在 4×2 / 4×3 / 4×4 / 4×5 四档高度下截图，**最后一条完整可见、无裁切**；记录实测行高并回写 `W-listRowDp` |
| **V7** | 2×1 不裁切 | 2×1 真机截图：课程名 + 元信息行**均完整**（含 fontScale 1.3 的放大字号场景） |
| **V8** | Chronometer 兜底 | 在至少 2 个 Launcher（原生 + 1 个第三方）验证：支持则显示每秒倒计时；不支持则**必须**显示静态「剩余 N 分」，不得空白 |
| **V9** | Bitmap 体积可控 | 进度环 Bitmap ≤ 目标尺寸的 2× 分辨率；单次 RemoteViews 事务 **< 1MB**（Binder 上限），记录实测值 |
| **V10** | 条目 B 色条对齐 | 截图上色条上下端与右侧文字块**上下端对齐**（误差 ≤2dp） |
| **V11** | 首帧无圆角跳变 | 冷启动录屏，loading 卡与真实卡**圆角一致**（均为 `W-cardRadius`） |
| **V12** | 预览图与真机一致 | 4 张 webp 的 mtime **晚于**本次改动提交时间；逐张与真机截图对比布局/字号/配色一致 |
| **V13** | 无障碍 | 每个组件设置 `setContentDescription`（如「计算机网络，08:30 到 09:35，教一101，进行中」），TalkBack 可完整朗读；纯装饰 ImageView 设 `importantForAccessibility=no` |
| **V14** | 性能不回归 | 单次全量渲染（4 规格）耗时**不高于** v3.66.4 基线；无新增主线程阻塞 |
| **V15** | 深浅色资源完整 | `values` 与 `values-night` 的 widget 色值**成对存在**；无「只在浅色定义」的颜色（现 `widget_course_fallback` 已修，需保持） |
| **V16** | **系统圆角裁剪不吞主题圆角** | 在 **Android 12+** 与 **11 及以下** 各验证：三套主题下卡片圆角**肉眼可区分**（16dp vs 24dp）；截图量取圆角半径，误差 ≤2dp。若不可区分 ⇒ 说明 `W-cardInset` 不足，需加大 |
| **V17** | 外边距计入行数 | 4×N 在计入 `W-cardInset × 2` 后条数仍不溢出（与 V6 合并验证） |

---

## 9. 明确不可行项（避免返工）

1. **自定义字体按主题切换** —— 字体族只能静态写在布局 XML；RemoteViews 无运行时 `setTypeface`；Android **无「按 App 主题」的资源限定符**。需复制整套布局，不建议。（`@font/` 资源能否在 RemoteViews 布局中被正确解析，**仍需真机验证**，验证前不要承诺。）
2. **任何动画/过渡/呼吸/庆祝动效** —— RemoteViews 无动画能力，无 Overlay。
3. **Canvas 自绘 View 直接放进组件** —— 不允许。**但**可在 App 进程预渲染成 `Bitmap` 再 `setImageViewBitmap`（§5.1 ⑤）。
4. **圆角裁剪子元素** —— 只能靠 shape drawable 自身圆角。
5. **文字自动缩放** —— 无 `autoSizeTextType`。
6. **按主题选择布局/资源** —— 不存在这种限定符；`values-night/` + `layout-night/` 只认系统深浅色。
7. **Konami 式按键彩蛋 / 长按 / 多击手势** —— RemoteViews 收不到按键，也无手势回调。唯一可用的交互扩展是**分区点击跳转**。
8. **秒级整体重绘** —— 刷新受 15 分钟 tick 与系统广播约束；**Chronometer 是唯一例外**。
9. **每主题的色条几何** —— 需复制 item 布局；曲线方案（`wrap_content` + drawable 内在尺寸）脆弱，不建议。
10. **`setInt(viewId, "setBackgroundResource", …)` 这类反射路径** —— 依赖目标方法是否 `@RemotableViewMethod`，跨进程调用不保证成功。**本方案统一改用公开 API `setImageViewResource`**：把卡片底/色条/气泡底做成 `ImageView`（`scaleType=fitXY`），背景是带圆角的 shape drawable，切主题 = 切 drawable 资源 ⇒ **不依赖任何反射动作**，同时解决「圆角无法运行时改变」的问题。这是本方案能落地主题圆角的关键手法。
11. **渐变背景按主题** —— 技术可行（多份 drawable + 切换），但小组件面积小、收益低，**建议不做**。
12. **动态取色（Material You）** —— 会重新引入不可控对比度（App 侧已有前车之鉴），且与「三套锁定主题」的产品方向冲突。
13. **「卡片贴满组件边界 + 主题圆角」二者不可兼得** —— Android 12+ 的系统边界圆角裁剪会覆盖更大的卡片圆角（见 §4 前置约束）。必须二选一：(a) **留 `W-cardInset` 外边距**，保住主题圆角（**本方案选这条**）；(b) 贴满边界并用 `@android:dimen/system_app_widget_background_radius` 对齐系统圆角，此时**放弃主题圆角**（等于 A3 不做）。**不能既贴满又要求 16dp/24dp 可区分。**

---

## 附录 A · 实测对比度表（可复算）

复算脚本：`build_qa/contrast_check.py`、`build_qa/contrast_candidates.py`（WCAG 2.x 相对亮度公式）。

### A.1 现状（发现问题）

| 色对 | 对比度 | 需 | 判定 |
|---|---|---|---|
| `#2C3E50` on `#FEF7FF`（浅·主文字） | 10.44:1 | 4.5 | ✅ |
| `#5D6D7E` on `#FEF7FF`（浅·次文字） | 5.05:1 | 4.5 | ✅ |
| **`#808B96` on `#FEF7FF`（浅·提示文字）** | **3.30:1** | 4.5 | ❌ |
| **`#F2F4F4` on `#FEF7FF`（浅·兜底色条）** | **1.05:1** | 3.0 | ❌ |
| `#F2F2F7` on `#141218`（深·主文字） | 16.66:1 | 4.5 | ✅ |
| `#AEA9AF` on `#141218`（深·次文字） | 8.05:1 | 4.5 | ✅ |
| `#8E8E93` on `#141218`（深·提示文字） | 5.70:1 | 4.5 | ✅ |
| **`#3A3A3C` on `#141218`（深·兜底色条）** | **1.64:1** | 3.0 | ❌ |
| `#2C3E50` on `#72AFB8`（气泡最差估算，见注） | 4.47:1 | 4.5 | ⚠️ 临界 |

> 注：气泡底 = `#3498DB` 经 `SRC_ATOP` 与课程色 `light_color`（35% alpha）混合。`#72AFB8` 为「橄榄金 `#E5DA76`」的估算混色，**需真机取色复核**。结论方向明确：**气泡对比度随课程色浮动、无保证**。

### A.2 主题映射（证明「不能直接复用」）

| 色对 | 对比度 | 需 4.5 | 判定 |
|---|---|---|---|
| 书卷 `#6E6D68` on `#F5F4EF` | 4.71:1 | ✅ | 可直接用 |
| **通透 `#8E8E93` on `#FFFFFF`** | **3.26:1** | ❌ | **需校正** |
| **柔绘 `#8B8798` on `#FCFBFD`** | **3.38:1** | ❌ | **需校正** |
| 书卷 `primary #C96442` on `#F5F4EF` | 3.54:1 | ❌ | 作**文字**不行 |
| 通透 `primary #007AFF` on `#FFFFFF` | 4.02:1 | ❌ | 作**文字**不行 |
| 柔绘 `primary #7C86C9` on `#FCFBFD` | 3.33:1 | ❌ | 作**文字**不行 |

（以上三套 primary 作**非文本**装饰均 ≥3:1，**全部通过**。）

### A.3 校正后取值（§6.3 的依据）

| 用途 | 候选 | 对比度 |
|---|---|---|
| 通透 浅 `textSecondary` | `#7C7C80` → 4.16 ❌ / **`#6C6C70` → 5.23 ✅** | 取 `#6C6C70` |
| 柔绘 浅 `textSecondary` | `#7B7789` → 4.20 ❌ / **`#6E6A7C` → 5.07 ✅** | 取 `#6E6A7C` |
| 浅·提示层级 | `#737E8A` → 3.93 ❌ / `#68737F` → 4.59 ✅ | **结论：不设第四级色**（§4.6） |
| 深·兜底色条 | `#5A5A5E` → 2.71 ❌ / `#6B6B70` → 3.51 ✅ | 取 `textSecondary` 更稳 |

### A.4 气泡亮度算法（§4.5）的可证明性

白字与黑字对比度相等时：`1.05/(L+0.05) = (L+0.05)/0.05` ⇒ `L = 0.1791`，此时两者均为 **4.58:1**。故「`L > 0.179` 取深字、否则取白字」**恒 ≥ 4.58:1 ≥ 4.5:1**。

---

## 附录 B · 改动文件清单（预估）

| 类型 | 文件 |
|---|---|
| 新增 | `AppWidgetTokens` 结构 + 3 主题 × 深浅 6 份赋值（并入 `AppStyle.kt` / `ClaudeStyle.kt` / `IosStyle.kt` / `SoftStyle.kt`） |
| 新增 | widget 形状 drawable × 3 主题（卡底/色条/气泡底/描边环，各含日/夜 = 约 8~12 个文件） |
| 新增 | `tools/check_widget_contrast.py`（对比度门禁） |
| 新增 | 空/假期态图标 drawable × 3~4 |
| 修改 | `WidgetSnapshot` proto（新增 `widget_style` message） |
| 修改 | `WidgetUpdateHelper.kt`（注入 `AppSettingsRepository`、读 preset、解析 token 入快照、条数公式） |
| 修改 | 4 个 Renderer（改读快照 token；进行中/未完成态；Chronometer；分区点击） |
| 修改 | 4 个主布局 + 2 个条目布局 + `widget_tiny_circle`（含 `layout-night/` 对应文件） |
| 修改 | `values/colors.xml`、`values-night/colors.xml`（对比度修正） |
| 修改 | 4 个 `shangkeschedule_*_widget.xml`（`minResizeHeight`、`description`） |
| 修改 | 4 张 `widget_preview_*.webp`（重出） |
| 修改 | 4 套 `strings.xml`（空/假期/彩蛋文案） |
| 删除 | `drawable-night/widget_loading_background.xml`（冗余，§1.4） |
| 修改 | `scripts/theme-leak-baseline.json`（扩范围后经 `--update-baseline` 生成，**不得手改**） |
