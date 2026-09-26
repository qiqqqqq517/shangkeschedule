# 设计系统规范 · Design System Rules

> 本文件是「上课 ShangKeSchedule」主题/设计系统的**约束规范**（规则，不是值表）。
> 值表（色阶 / 圆角 / 阴影 / 字阶的具体数值）见 `claude-schedule-page.design/docs/设计token规范.md`。
> 落地校验器：`tools/check_theme_leak.py`（本文所有"必须/不得"均尽量脚本化）。

状态：**P1 立规已生效**（2026-09-23）｜决策依据：`build_qa/design_review/A1-主题系统收口方案.md`（D1–D5 均取 A）

---

## 0. 一句话规则

> **组件层不得知道"当前是哪个主题"。**
> 凡是 `ui/` 里出现的 `if (theme == X)`，都是**缺了一个 token**或**缺了一个角色** —— 正解是补 token，不是保留分支。

---

## 1. 四层结构与职责

| 层 | 位置 | 职责 | 允许写 `when(preset)` |
| --- | --- | --- | --- |
| **L0 语义角色层** | `ui/theme/AppStyle.kt` 内的枚举与常量 | 与主题无关的**角色/比例**命名：`AccentTone`、`SettingsEntryTone`、`AppTypeGrid`、`AppAlpha` | 否 |
| **L1 主题 token 层** | `ui/theme/{IosStyle,ClaudeStyle,SoftStyle}.kt` | 每套主题各提供**一份完整** `AppColorTokens` / `AppShapeTokens` / `AppSpacingTokens` / `AppTypeTokens` | 否（只提供值） |
| **L2 主题适配层** | `ui/theme/Theme.kt`、`withAppSurfaces()`、各主题的 `mapXxx()` | 把 L1 映射成 M3 `ColorScheme` / `Typography` / 角色→色调 等平台期望结构 | **是（唯一允许的地方）** |
| **L3 组件层** | `ui/**`（排除 `ui/theme/**`） | 只通过 `appColors()` / `appShapes()` / `appSpacing()` / `appType()` 取值 | **禁止** |

数据流：

```
L3 组件  ──只读──▶  appColors() / appShapes() / appSpacing() / appType()
                          ▲ provide（唯一注入点：Theme.kt 的 CompositionLocalProvider）
L1 主题 token ◀── L2 主题适配 ──when(preset)──▶ M3 ColorScheme / Typography / 角色→色调
```

---

## 2. 硬性规则（违反即为缺陷）

### R1 · 组件层零主题分支

`shared/src/commonMain/kotlin/com/shangkeschedule/ui/**`（排除 `ui/theme/**`）**不得出现**：

`LocalThemePreset`、`LocalIsSoftTheme`、`LocalIsGlobalSoftTheme`、`AppThemePreset.`、
`isClaudePreset` / `isIosPreset` / `isSoftPreset` 这类布尔、以及 `softMaterial` 这类"把主题身份当参数往下传"的字段。

需要"这个主题不一样"时按两步判定：

1. **是一个值的差异？** → 加 token。例：设置行需要 15sp / 48dp 而 `rowTitle` 是 18sp / `rowMinHeight` 是 56dp ⇒ 新增 `settingsRowTitle` / `settingsRowMinHeight`，**而不是** `if (isClaudePreset)`。
2. **是结构/组件形态的差异？** → 加一个**token 化开关**（如 `groupSeparatorStyle = INSET | CARD`），由组件读 token 决定，不在页面里 `if`。

> 判定存量参考：`ui/settings/SettingsScreen.kt:550-559` 是反例（已列入 P2 修复）。

### R2 · token 必须显式赋值

L1 的每个 token **必须由每套主题显式给出**，**不得依赖 `data class` 默认值**。
理由：默认值等于"隐式继承 Material 基线"，会让某套主题的观感实际由 M3 决定，而非它自己的设计系统。

> 当前已知违反（P2 修复）：`outline` / `outlineVariant` 只有柔绘显式传；通透与书卷继承 M3 基线 `#79747E` / `#CAC4D0`。

### R3 · 角色 → 外观必须单射

同一批语义角色（如 `SettingsEntryTone` 的 10 个）在同一主题内**必须映射到互不相同的外观**（当前是多色，故为互不相同的色调）。
理由：多色装饰承载的是"入口身份"，一旦两个角色撞成同一色调，这条信息就丢失了。

> 当前已知违反（P3 修复）：
> 通透 10→8 —— `MATCHA` 与 `GREEN` 同为 `IosCellTone.GREEN`、`GRAY` 与 `BROWN` 同为 `IosCellTone.GRAY`（2 对撞色）；
> 柔绘 10→9 —— `OLIVE` 与 `GREEN` 同为 `SoftCellTone.SAGE`（1 对撞色）。

### R4 · 单一事实来源

- `primary` 等语义色**只在 L1 定义**；M3 `ColorScheme` 必须**由 token 派生**，不得反向覆盖 token（禁止 `copy(primary = colorScheme.primary)` 这类打补丁对齐）。
- 取 token 只走**一条路径**：`appColorTokens(isDark, preset)`；不得在别处直接调 `iosAppColorTokens()` / `softAppColorTokens()`。

### R5 · 组件合并 ≠ 外观合并

同一角色在三套主题下**共用同一个组件**，差异只由 token 表达。
**但以下"主题身份"必须保留、不得收敛**：`seedColor`、字族（Newsreader / SF / 柔绘字阶）、课程色板与 alpha（书卷 `0x40` / 柔绘 `0x33` / 通透 `0x1F`）、触摸指示语言、动效 profile。

### R6 · 色彩数量不做收敛

课程色板与设置页色调的**数量**是既定设计方向，**不因"统一"而减少**。
规则只约束"可区分性 / 映射稳定性 / 语义不冲突"，见 R3。

### R7 · 装饰用量规则（2026-09-26 全局 UI 优化新增）

「轻盈简约」的落地口径：**边框 / 阴影 / 渐变**是**信息载体**，不是默认装饰。新增任何一处前先问它承载什么信息。

**边框保留 4 类**

1. **状态指示**：选中 / 未选中本身就是信息，删掉即丢功能（`AppStyle.kt` 的 `selected` 2dp 主色边、Radio / Checkbox 选中环）。
2. **输入边界**：表单可输入区必须可辨，走 `outline` / `outlineVariant` token。
3. **功能性图形**：裁剪框、进度环等「操作对象边界」，不是装饰。
4. **主题材质语言的必要组成**：书卷 `claudeGroupBorder()` 0.5dp 实色（纸面）、通透 `iosGlassRim`（玻璃高光内描边）、柔绘 `softFeatherRim`（羽化环）。属 R5 的"主题身份"，删除等于抹掉材质。

**边框删除 3 类**

1. **卡片 / 列表块外圈同色描边**：已有 `cardBg ≠ pageBg` 底色分层（三主题 ΔL\* ≈ 4.4 / 3.2 / 2.7），描边是**重复表达**。
2. **非选中态空心描边**：未选中还画一圈灰边 = 纯噪音，改「未选中实心底 + 无描边」。
3. **非网格宽度**（2.5 / 1.5 / 0.5dp 混用）：保留类统一 **1dp**，其余归 0。

**阴影归属**：投影只归①**悬浮层**（FAB / 菜单 / 底部面板）②**拖拽态**（表示"抬起"）③**柔绘 `softShadow`**（材质本体，只可降强度）。
其余静态卡片**不加投影** —— 分层由底色差承担。

**装饰降级优先于删除**：渐变 / 扫光 / 肌理类装饰若承载主题个性（如柔绘 `BREATHE` 换气、通透斜向扫光），只调强度（alpha ×0.6 一类），不断崖删除。

### R8 · 留白网格（2026-09-26 新增）

**基准：4dp 网格 / 8pt 节奏；间距取值集合限定 `{4, 8, 12, 16, 20, 24, 32}`**（圆角与控件尺寸不受此限）。

`AppSpacingTokens` 的 **11 个间距字段三主题必须同值**（当前：20 / 16 / 24 / 16 / 56 / 52 / 48 / 44 / 56 / 16 / 12）+ 留白节奏四字段（`pageTop` 8 / `sectionGap` 24 / `sectionTitleGap` 8 / `contentBottom` 32）。

- 主题间的**宽松感差异由材质层承担**（圆角阶梯、软投影、羽化环、字重），**不得靠调大间距实现** —— 这是"统骨架、留个性"的既定取舍（见 `SoftStyle.kt` 文件头注释）。
- 页面里的魔法 padding（如 `+100.dp`、`padding(horizontal = 84.dp)`）一律走 token，禁止就地写数值。

### R9 · 字重与图标三档（2026-09-26 新增）

- **字重语义化**：`AppTypeTokens` 提供 `titleWeight` / `bodyWeight` / `captionWeight` 三个角色。组件层不得硬编码 `FontWeight.SemiBold` / `Bold`，应读对应角色（主题身份差异由 token 承载：柔绘标题 Medium，书卷 / 通透 SemiBold）。
- **图标尺寸三档**：`AppIconTokens` = small 16 / medium 20 / large 24。`13 / 18 / 22dp` 一类非标值一律归档。
- 图标尺寸与**命中区**解耦：尺寸归尺寸，`touchMin = 48dp` 归命中区（外层 Box 承载 `clickable`）。

---

## 3. 校验与提交门禁

```bash
python scripts/check_theme_leak.py                       # 打印完整报告
python scripts/check_theme_leak.py --md out.md            # 另存 Markdown 报告
python scripts/check_theme_leak.py --baseline scripts/theme-leak-baseline.json   # 棘轮门禁
python scripts/check_theme_leak.py --update-baseline scripts/theme-leak-baseline.json  # 锁定当前计数
python scripts/check_theme_leak.py --fail                 # 绝对门禁（只要非零即失败）
```

四项检查对应 R1–R4：

| 检查 | 对应规则 | 当前基线（P2 后，2026-09-23） | P1 立规时 |
| --- | --- | --- | --- |
| C1 组件层主题泄漏 | R1 | **A 类分支 46 处 / 11 文件**（目标 0） | 50 |
| C2 色调映射单射性 | R3 | 书卷 10 ✅ / 通透 8 ❌ / 柔绘 9 ❌ | 同 |
| C3 token 显式覆盖 | R2 | 缺 `outline`,`outlineVariant`：书卷 light+dark、通透 light+dark（柔绘已合规） | 同 |
| C4 `primary` 单源 | R4 | ❌ 1 处（`Theme.kt:128`） | 同 |
| **硬指标合计** | — | **53** | 57 |

另：B 类身份直引 17 处（提示项，需人工判定是"取数据"还是"漏 token"）。

> **棘轮已在工作**：P2 补齐 `settingsRowTitle` / `settingsRowTitleWeight` / `settingsRowMinHeight`
> 三个 token 后，`SettingsScreen` 的 4 处分支消失 ⇒ C1 50→46、合计 57→53，基线随之下降并锁定。
> 完整清单：`build_qa/design_review/A1-P1-违规清单.md`（随时可重跑刷新）。

### 3.1 棘轮（ratchet）语义

门禁**不要求存量为零**（那会冻结开发），只要求**不得增加**：

- 当前计数 **≤** 基线 → 通过；**>** 基线 → 拦截并打印差异项。
- 修好一部分后，用 `--update-baseline` 把基线降下来，**锁定收益**（基线只允许下降）。
- 基线文件 `scripts/theme-leak-baseline.json` 已入库；**不要手改**，用 `--update-baseline` 生成。
  手改引入的 UTF-8 BOM 会被容错；但若损坏成非法 JSON 会 **fail-closed 拦截** —— 门禁不允许静默失效。

### 3.2 提交门禁（已启用）

```bash
git config core.hooksPath .githooks     # 启用（新克隆需各自执行一次）
git config --unset core.hooksPath        # 关闭
git commit --no-verify                   # 跳过单次
```

- Hook：`.githooks/pre-commit` —— 调用棘轮检查，**只在回归时拦截**。
- Python 发现顺序：环境变量 `SHANGKE_PYTHON` → `git config shangke.python` → PATH（`python3`/`python`/`py`）。
  **找不到 python 时 fail-open**（只警告不阻断），避免环境问题卡住提交。
- 已实测三态：通过 → `exit 0`；制造回归 → `exit 1` 且打印差异项；基线损坏 → `exit 1`（fail-closed）。

---

## 4. 已知例外（允许存在，但必须有理由）

| 例外 | 位置 | 理由 |
| --- | --- | --- |
| `LocalIsSoftTheme` 在 `ui/theme/Theme.kt` 内被 provide | `Theme.kt:192` | 属于 L2 适配层，合规 |
| `LegacyGlassBottomBar` 分支 | `ui/components/NavigationComponents.kt:428` | 液态玻璃引擎不可用时的**真机兜底**路径；不参与三主题设计规范 |
| v2 紫渐变色板 | `AppStyle.kt`（待 P2 删除） | `AppThemePreset.fromString()` 把旧值与未知值统一收敛到 `IOS`（`AppThemePreset.kt:61-64`）⇒ 该分支**可证不可达** |

---

## 5. 变更流程

改任何视觉 / 配色 / 主题相关代码前：

1. 先读本文件与项目记忆中的「主题系统（A1）现状测绘」；
2. 改完跑 `python scripts/check_theme_leak.py`，**硬指标不得增加**（pre-commit 会拦）；
3. 涉及 token 结构变更时，同步更新 `build_qa/design_review/A1-主题系统收口方案.md` 的批次表；
4. 每批改动后按仓库规范：三目标编译（`:shared:compileKotlinJvm` + `:desktopApp:compileKotlin` + `:androidApp:assembleDebug`）、
   `工作日志.md` 追加记录；纯文档 / 工具改动不 bump 版本，改到 `shared/` 源码的按 `FIX` → `--bump patch`。
