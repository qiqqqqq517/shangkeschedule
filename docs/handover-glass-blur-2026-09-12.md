# 液态玻璃「模糊 / 折射 / 彩虹色散 全部不生效」排查交接报告

> **✅ 后续更新（2026-09-12 · v3.50.2(194)）：本文 §6 的未解卡点已攻克，且真根因与本文 §4/§6 的判断不同。**
>
> 真根因是**背板录制层恒为空**：Compose 1.11.1 的 `record(density, layoutDirection, size, block)`
> 不像 1.12 的 `record(size){}` 那样临时把**调用方** `drawContext.canvas` 指向录制画布，
> 于是 `GlassBackdropSourceNode` 里用外层作用域调用的 `drawContent()` 画到了**屏幕画布**、
> 层里什么都没有 ⇒ 所有玻璃件都在采样空层 ⇒ 模糊 / 折射 / 色散同时零像素输出
> （**桌面与 Android 一致失效**，与 Skiko 的层画笔限制无关）。
>
> 因此：§3.3 的「格 8 / 格 10 差分 0.000 ⇒ 内容进层」是**误读**（格 8 本身已自注「无法区分层是否为空」；
> 格 10 的模糊来自 `drawContent()` 画进了模糊父节点自己的层）；§6「最后一段拓扑差异」**并不存在**。
> §5 的两条结构性修正（录制顺序、效果挂父节点 + drawLayer 放子节点）仍然正确并已保留。
>
> 修复见 `工作日志.md` v3.50.2 条目（`GlassBackdropSourceNode` 手工补上画布交换）；
> 探针新增「层内容 A/B 直证」两格，并把背板由竖条纹改为**棋盘格**（竖条纹只能显现水平位移，
> 底栏上下边缘的折射是竖直位移 ⇒ 原探针把正常工作的折射量成了 0.2% 像素）。
> **尚未做**：真机像素级验收（当日 `adb devices` 无设备连接）。

> 日期：2026-09-12　基线版本：**v3.50.1 (versionCode 193)**（未做版本迭代，原因见 §7）
> 适用对象：接手继续排查的 AI / 开发者。先读 `AGENTS.md` 与 `工作日志.md` 顶部，再读本文。
> 本次结论全部来自**可复跑的离屏探针实验 + 真机像素取证**，不含猜测；凡未确证的都明确标注「未确证」。

---

## 1. 一句话结论

用户报告的「模糊完全不生效 / 折射不生效 / 彩虹色散不生效」在代码里是**同一个根因**：

**玻璃的效果链（`vibrancy → blur → lens`）作用在「背板图层」上，而该图层在当前渲染路径下拿不到/画不出内容，或效果被合成路径丢弃 ⇒ 三个效果同时静默失效。**

本次已用对照实验定位并修掉了其中**两个确定缺陷**，但还剩**最后一段拓扑差异**未攻克（§6）。**问题尚未解决，玻璃效果目前仍不可见。**

同时明确否掉了用户提出的两个假设（见 §2），避免后续重复走弯路。

---

## 2. 先否掉的两个假设（已用运行时证据排除）

### 2.1 「是不是因为指示器在底层、只读到背景色？」——否

用户假设「点击后放大的那个按钮（选中指示器）位于底层、读取的是背景颜色，所以看不到玻璃效果」。

真机 + 离屏运行时日志证明**指示器已在最顶层、且已同时采样两层**：

```
source recorded size=Size(398.0, 56.0)  layer=1049318007   ← Tab 文字层录制成功且非空
drawGlassBackdrop called layer=1374152636                  ← C 层采样「页面内容层」
drawGlassBackdrop called layer=1049318007                  ← C 层采样「Tab 文字层」
```

三层绘制顺序（`ui/glass/LiquidGlassTabs.kt`）：

| 层 | 内容 | 采样来源 | 层序 |
|---|---|---|---|
| A | 玻璃条 64dp（可见图标/文字） | 页面内容 `backdrop` | 先画（下） |
| B | Tab 录制层（alpha=0） | 页面内容；自身内容录进 `tabsBackdrop` | 中 |
| C | **选中指示器**（按压放大 78/56） | `CombinedGlassBackdrop(backdrop, tabsBackdrop)` = 页面 + Tab 文字 | **最后画（最上）** |

即用户要的「顶层玻璃折射下方所有内容」**就是当前设计**。

### 2.2 「是不是设置被更高优先级代码覆盖？」——否

已验证：`LocalGlassBlurRadius` / `LocalGlassRefraction` 全工程唯一注入点就是 `Theme.kt`；
5 处 `liquidGlass(` 调用点全部不显式传 `blurRadius`/`refraction`；
`AppSettingsModel` 无任何「整份默认值写回」；
跨进程 Widget 通道写的是独立 Room 库，不碰主 DataStore。
**设置值确实被正确读到了渲染层**（设备实测：改为 24dp 时内部计算即为 24dp 对应像素）。

---

## 3. 失败现象的可复现证据（离屏探针）

### 3.1 探针工具

新增 `desktopApp/src/main/kotlin/com/shangkeschedule/GlassProbe.kt`（仅本地诊断，不参与发布）：

```powershell
.\gradlew.bat :desktopApp:run "-PpreviewMainClass=com.shangkeschedule.GlassProbeKt"
```

产物：`build_qa/glass_probe/probe_*.png` + 控制台差分数据。

探针内容：
- **主场景**：直接渲染真实 `LiquidGlassTabs`（底栏本体）+ 高频竖条纹背板；
  三配置 `full`（blur24 + 折射24/24 + 色散）/ `blurOnly`（blur24，无折射）/ `none`（blur0）各渲一张，
  对「底栏区域」与「控制区域（底栏外）」逐像素差分；控制区必须为 0 才说明场景可比。
- **机制对照场景（mech）**：10 个并排格子，内容同为竖条纹，唯一变量是「效果的应用方式」，
  用来隔离渲染管线。
- **裸平台能力对照（raw）**：直接 `Modifier.graphicsLayer { renderEffect = BlurEffect }`，
  验证平台是否具备能力。

### 3.2 主场景结果（问题复现）

| 对比 | 差分 mean | 结论 |
|---|---|---|
| 控制区 full vs none | 0.000 | 场景可比 ✓ |
| 底栏 blurOnly vs none | **0.000** | 模糊零像素输出 |
| 底栏 full vs blurOnly | **0.000** | 折射 + 色散零像素输出 |

真机同样复现（同页面同内容，状态 A「blur24 + 折射开」vs 状态 B「blur0 + 折射关」逐位相同，滚动位置误差仅 0.001）。

### 3.3 机制对照结果（根因定位）

| 格 | 结构 | 差分 vs 基线 | 判读 |
|---|---|---|---|
| 2 | 节点自带 `renderEffect` + **直接内容** | 115.409 | 生效 ✓ |
| 3 | 节点自带 `renderEffect` + **同节点 `drawLayer`** | **0.000** | **静默失效 ✗** |
| 4 | **父节点** `renderEffect` + 子节点 `drawLayer` | 115.409 | 生效 ✓ |
| 5 | 格4 + 容器 `clip(shape)` | 115.413 | 生效 ✓（父裁剪无副作用） |
| 6 / 7 | **真实 `GlassSurface`**（blur 0 / blur 24） | 6.955 / 6.955 | **两者相同 ⇒ 效果未应用 ✗** |
| 8 | `drawWithContent` + `record{ drawContent() }` + `drawLayer` | 0.000 | 内容上屏正确（无法区分层是否为空） |
| 9 | **另一节点**录制（直接绘制）+ 父效果 + 子 `drawLayer` | 110.700 | 生效 ✓ |
| 10 | `record{ drawContent() }` + 模糊父节点 | 115.409 | 生效 ✓（`drawContent()` **能**进层） |
| raw | 裸 `Modifier.graphicsLayer{ BlurEffect }` | 70.155 | **平台能力可用** ✓ |

其他关键实验：
- **红块实验**：在录制块内**直接** `drawRect(Red)` → 底栏区出现红块（4067 px）；
  同一录制块内 `drawContent()` 画的条纹**完全没出现**（纯红 100%）⇒「先 `drawContent()` 上屏、
  再在 `record{}` 里第二次 `drawContent()`」时，**第二次调用拿不到内容**。
- **录制顺序**：改为「先 `record`、再 `drawLayer` 上屏」后，录制层有内容（格 8/10 差分 0.000，
  与直绘逐像素一致）。

---

## 4. 已确证的根因链

```
背板（backdrop）图层
   ├─ 缺陷① 录制顺序：先 drawContent() 上屏再录制 ⇒ 录制层拿不到内容（层为空）
   └─ 缺陷② 效果应用方式：renderEffect 与 drawLayer 放在同一节点 ⇒ Skiko 合成路径
        不消费该层画笔，效果被静默丢弃
   ⇒ 效果链作用在「空层」/「被丢弃的效果」上
   ⇒ 模糊、折射、色散 三者同时不可见（共因，非三个独立 bug）
```

两条缺陷都能解释「为什么编译/日志全绿、观感却零变化」：**
效果链对象构建成功（`effect!=null`）、参数正确、blend/坐标正确，但最终像素不变。**

---

## 5. 本次已修复（代码已落地）

| # | 文件 | 改动 |
|---|---|---|
| ① | `ui/glass/GlassBackdrop.kt` | `GlassBackdropSourceNode.draw()` 改为「先 `record`、再 `drawLayer` 上屏」，修正录制层为空的缺陷 |
| ② | `ui/glass/GlassSurface.kt` | 重写为**容器组件**：效果挂**父节点**、`drawLayer` 放**子节点**；容器级 `clip(shape)` 约束效果外溢 |
| ③ | `ui/theme/LiquidGlass.kt` | `Modifier.liquidGlass(...)` → `LiquidGlass(...)` 容器组件（FAB / 圆钮 / 挂起条 / 预览 全部迁移） |
| ④ | `ui/glass/LiquidGlassTabs.kt` | A/B/C 三层由「修饰符链」改为 `GlassSurface` 容器调用；折射语义与 `liquidGlass` 对齐（关闭即整层省略） |
| ⑤ | `ui/glass/GlassEffectScope.kt` | 新增 `paddingEnabled` 与 `applyForLayer(...)`（UI 树路径专用，padding 语义停用） |
| ⑥ | 调用点迁移 | `WeeklyScheduleScreen.kt`（回到本周圆钮）、`FloatingCourseBar.kt`、`AppBasicComponents.kt`（AppFab）、`GlassBlurScreen.kt`（预览 ×2） |

> 说明：以上结构性修正**方向已被机制对照实验证明正确**（父效果 + 子 drawLayer 在格 4/5/9 生效），
> 但**尚未让真实底栏出效果**——见 §6。

---

## 6. 未解决：最后一段差异（本次卡点）

### 6.1 现象

把「父效果 + 子 `drawLayer`」结构（格 9 证明可用）放进真实 `GlassSurface`、
且背板来自 `Modifier.glassBackdropSource` 的**共享图层**时，效果仍不渲染：

| 对比 | 差分 |
|---|---|
| 真实 `GlassSurface` blur=24 vs blur=0（离屏隔离） | **0.000** |
| 底栏 blurOnly vs none（真实底栏） | **0.000** |

### 6.2 已逐项排除的因素（都做过实验）

- 效果对象本身（换成硬编码 `BlurEffect(null, 24f, 24f, Clamp)` 同样无效）
- 容器 `clip(shape)`
- 三个装饰修饰符 `glassHighlight` / `glassShadow` / `glassInnerShadow`（移除亦无效）
- `onGloballyPositioned`（移除亦无效）
- `applyForLayer(...)` 调用（去掉亦无效）
- 录制顺序、`fillMaxSize` vs `matchParentSize`
- `drawGlassBackdrop` 的 `withTransform/translate` 包装（绕过、直接 `drawLayer` 亦无效）
- 录制层内容是否存在（红块实验证明层**有**内容）
- 效果入参（日志确认 `effect != null`、`size` 正确如 398×64 / 150×180、`offset` 正确如 (16,844) / (0,0)）
- 平台能力（裸 `Modifier.graphicsLayer{BlurEffect}` 差分 70.155，能力可用）

### 6.3 剩余可疑点（**未确证**，建议从这里继续）

差异只落在这一点拓扑上：

> **同一个 `GraphicsLayer` 由 A 节点录制、并被 A 与 B 两个节点先后 `drawLayer`**，
> 且 B 的绘制发生在一个带 `renderEffect` 的父层内部。

对照实验中格 9 也是「两个节点先后 drawLayer 同一层」且生效，所以还差一个条件未定位
（候选：录制节点是 `DrawModifierNode` 而非 `Modifier.drawWithContent`；
或共享层被先画的节点"消费"后，第二次在效果层内的绘制被跳过）。

**下一步建议**：把格 9 逐步改造成与真实拓扑一致（录制节点换成 `DrawModifierNode`、
内容换成 `drawContent()`、层由脚手架持有），每步跑一次探针，直到复现 0.000，即锁定最后一个条件。

---

## 7. 当前状态

### 7.1 代码状态

- **编译状态**：三端通过 —— `:shared:compileKotlinJvm` / `:desktopApp:compileKotlin` /
  `:androidApp:assembleDebug` 均 BUILD SUCCESSFUL（最后验证 2026-09-12）。
- **未提交**（`git status` 显示为 modified/untracked）；**未做版本迭代**——按 `AGENTS.md`
  只有「验证通过」的改动才 bump，本次未通过，故版本仍为 **3.50.1 (193)**。
- ⚠️ 代码注释里出现的 **「v3.50.2」是暂定标签**，实际版本号未 bump；接手时请按实际情况统一。
- ⚠️ 引擎内诊断插桩已全部清理（无调试 `println` / 红块 / 硬编码效果）。
- ⚠️ `工作日志.md` 本轮**尚未追加** DOCS 记录（本次按要求只产出本报告）。

### 7.2 设备状态

- 已安装干净调试包 `shangke-v3.50.1-arm64-v8a-debug.apk`（无诊断条纹），启动无 `FATAL`。
- **用户数据完好**（已逐项核验）：`courses` 15 行 / `course_weeks` 178 行 / 学期 `2026-08-31` /
  课表 `我的课表` / `todo_items`、`schedule_events` 均在。
- 玻璃设置现值：**模糊 4dp、折射开、高度 24、强度 24、色散关、厚度感关**。
- 会话开始时另存过一份 `build_qa/glass_probe/prefs_orig.pb`（= 模糊 4 / 折射开 / 14 / 18 / 色散开 / 厚度开），
  与现值不同——**以手机 UI 显示为准**，不要盲目回写该备份。

### 7.3 可用工具

| 工具 | 用途 | 命令 |
|---|---|---|
| `desktopApp/.../GlassProbe.kt` | 离屏探针（主场景 + mech 10 格 + raw 能力对照） | `.\gradlew.bat :desktopApp:run "-PpreviewMainClass=com.shangkeschedule.GlassProbeKt"` |
| `build_qa/glass_probe/prefs_tool.py` | DataStore `preferences_pb` 解码/改键（map 结构，支持 float/bool） | `python build_qa\glass_probe\prefs_tool.py show <file>` |
| `build_qa/glass_probe/*.png` | 探针产物与真机截图 | — |

**注意**：PowerShell 下 `adb ... > file` 重定向会损坏二进制；读 DataStore 请用
`adb shell "run-as com.shangkeschedule base64 files/datastore/app_settings.preferences_pb"`
再本地 `base64 -d`（`prefs_tool.py` 已按此约定）。Gradle 输出用管道过滤时易被截断，
建议 `Start-Process ... -RedirectStandardOutput` 落盘后再读。

### 7.4 回滚方式

改动均未提交，需要回到 v3.50.1 原始状态：

```powershell
git checkout -- shared/src/commonMain/kotlin/com/shangkeschedule/ui/theme/LiquidGlass.kt `
                shared/src/commonMain/kotlin/com/shangkeschedule/ui/glass/ `
                shared/src/commonMain/kotlin/com/shangkeschedule/ui/schedule/ `
                shared/src/commonMain/kotlin/com/shangkeschedule/ui/components/
```

（`desktopApp/.../GlassProbe.kt` 为新增文件，删除即可；`GlassDecorations.kt` /
`GlassInteractions.kt` / `LiquidGlassTabs.kt` 为 v3.48 起新增，视需要保留。）

---

## 8. 下一步可选方案（按代价排序）

| 方案 | 做法 | 代价 / 风险 |
|---|---|---|
| **A. 继续攻拓扑差异**（推荐先试） | 按 §6.3，把机制格 9 逐步改造成真实拓扑，锁定最后一个条件后修正 `GlassSurface` / 录制节点 | 未知，但已排除大部分变量，收敛可期；保持「页面级共享录制」架构（性能好） |
| **B. 改为「每块玻璃自录制」** | 放弃共享 `backdrop`，每块玻璃用格 9 的可用结构自录背景 | 改动大；每件一次离屏录制（性能）；且**未在真机验证过格 9 结构** |
| **C. 升级 Compose 到 1.12** | 上游 backdrop 的目标版本 | 注意：本次 raw 对照已证明 **1.11.1 平台能力可用**（差分 70.155），故「版本太旧」不是主因，升级未必解决，且全项目回归风险高 |
| **D. 暂时降级玻璃形态** | 玻璃只用「表面 tint + 高光/投影」，设置页隐藏/禁用模糊与折射项 | 观感一致、不再给用户「调了没用」的体验；但放弃了液态玻璃效果本身 |

**无论选哪个，都建议先补上「像素级验收」**：此前多轮「装机验证通过」只有日志证据
（如「4dp→11px」），从未做过像素差分，这正是问题长期潜伏的原因。

---

## 9. 待确认事项（未确证，接手请复核）

1. **主页面观感**：真机上「我的」等**带玻璃底栏的主页面**截图观感偏「发灰/发雾」，
   而不带底栏的二级页（如「玻璃模糊」页）清晰正常。**未确证是否为回归**——
   与本次「背板录制改为 `record → drawLayer` 上屏」是否相关需要复核
   （若相关，说明页面内容现在全部经离屏层上屏，合成结果可能有细微变化）。
2. **玻璃设置被改写**：会话中把设备设置恢复为 `14/18/色散开/厚度开` 后，
   再启动应用时变回了 `24/24/色散关/厚度关`。**未确证写入方**（候选：应用侧某回调、
   或恢复操作与应用运行状态竞争）。建议接手时先观察设置页数值是否会自行变化。
3. 本轮**未做**：`CHANGELOG.md` 更新、`工作日志.md` 追加、版本迭代、提交。
