# 性能基线与回归门禁（PF6）

> 对应《界面优化方向清单》**PF6 性能基线与回归门禁**。
> 建立原因：掉帧数据此前只存在于终端输出与 `工作日志.md` 的手工誊写（v3.43.0 那次
> 「柔绘·课表 6.31% / p99 117ms」），`build_qa/soft_jank/` 长期为空 ⇒ 性能回归无法自动发现。

## 测量场景（固定 6 个）

| 主题 | 页面 | 说明 |
|---|---|---|
| 柔绘 / 书卷 / 通透 | 课表 | 历史最差场景在「柔绘·课表」，必须每次都测 |
| 柔绘 / 书卷 / 通透 | 今日 | 与课表页对照，用于区分「玻璃底栏」与「列表内容」的开销 |

负载固定为：每页上滑 / 下滑各 8 次（`SWIPE_PAIRS = 8`，`SWIPE_DY = 1100`，`SWIPE_MS = 160`），
采集 `adb shell dumpsys gfxinfo com.shangkeschedule`。

## 实测记录

### 2026-09-26 · 冷启动（首次采到真实数据）

设备：`JFKJRC89T87XXOJJ`，装 **v3.69.4 debug 包**（arm64-v8a），`am force-stop` 后 `am start -W`：

| 次数 | TotalTime | WaitTime |
|---|---|---|
| 1 | 1127 ms | 1135 ms |
| 2 | 1127 ms | 1133 ms |
| 3 | 1092 ms | 1097 ms |

**基线 ≈ 1.09–1.13 s，三次波动 < 4%，稳定性好。**

⚠️ 这是 **debug 包**的数值（无 R8 优化）、装 release 会因签名不符被拒（设备原包为 debug 签名；
卸载重装会丢课表数据）。因此**绝对值偏悲观，不可与 release 指标直接比较**，
但用于「改动前后同包对比」完全有效 —— 这正是 PF6 的用途。

### 2026-09-26 · 滚动负载基线（输入注入开启后首次采到）

设备：同上台（`JFKJRC89T87XXOJJ`），**v3.69.4 debug**（arm64-v8a），1080×2460。
负载：每页上滑 / 下滑各 8 次（dy 1100、160ms）；采集 `dumpsys gfxinfo`。

| 场景 | 总帧 | 卡顿帧 | 卡顿率 | p50 | p90 | p95 | p99 | Missed Vsync |
|---|---|---|---|---|---|---|---|---|
| 默认页（启动后） | 1191 | 27 | **2.27%** | 6 ms | 9 ms | 10 ms | 32 ms | 10 |
| 课表页 · 轮1 | 1221 | 33 | 2.70% | 7 ms | 10 ms | 11 ms | 22 ms | 12 |
| 课表页 · 轮2 | 1225 | 32 | **2.61%** | 7 ms | 10 ms | 12 ms | 22 ms | — |

**读法与基线**：

- **卡顿率基线 ≈ 2.3–2.7%**，两轮同页重复采样的偏差仅 0.09 个百分点 ⇒ 采样稳定、可用作回归对比。
- **p90 / p95 稳定在 9–12 ms**，远低于 60Hz 的 16.7 ms 帧预算 ⇒ 常规滚动无掉帧压力。
  ⇒ **PF1「削减玻璃层数」要解决的并不是"滚动卡"，而是长尾**：p99 22–32 ms
  与 Missed Vsync 10–12 才是玻璃合成开销的表征。**做 PF1 时应盯 p99 与卡顿率，别看 p50。**
- 仍是 **debug 包**数值（无 R8 优化），绝对值偏悲观，只用于**同包前后对比**。

⚠️ **踩坑：探测权限绝不能用 `input tap`。**
本次 `input tap 1 1` **静默通过、不报任何错**，据此以为权限已开启；
但随后 16 次 `input swipe` 全部报 `SecurityException: INJECT_EVENTS`，
结果采到的是「启动期间 24 帧、58% 卡顿」——**一份看起来像结果的假数据**，
比直接失败危险得多。**tap 与 swipe 的权限检查行为不同**，
探测必须用与实际负载一致的 `input swipe`。
`scripts/perf_jank.py` 的 preflight 已按此修正。

### 仍未覆盖的场景

清单原设 6 场景（三主题 × 课表/今日）。本次只采了**默认主题**下的两个页面 ——
切主题需在设置里多级导航，坐标不稳定、容易点错反而采到错误场景的数据，
故未纳入。补齐可走「用 `run-as` 直写 DataStore 切主题」的路子（不依赖点击坐标）。


## 命令

```bash
python scripts/perf_jank.py                    # 采集 6 场景，落盘 JSON + Markdown
python scripts/perf_jank.py --scenes 柔绘-课表   # 只采指定场景
python scripts/perf_jank.py --save-baseline     # 把本轮结果固化为回归基线
python scripts/perf_jank.py --check-baseline    # 与基线比对，有回归则退出码 1
```

> **脚本位置（2026-09-26 迁移）**：原 `tools/verify_soft_jank.py` 功能完整，但 `tools/`
> 被 `.gitignore` 忽略 ⇒ 脚本不随仓库分发、换机即失。现迁到 `scripts/perf_jank.py` 并
> 改为自包含（不再依赖 `tools/verify_three_themes`）。

产物落在 `build_qa/soft_jank/`：

- `jank_<YYYYmmdd_HHMMSS>.json` —— 结构化数值，供比对使用；
- `jank_<YYYYmmdd_HHMMSS>.md` —— 人类可读表格，可直接贴进 Release 说明或工作日志；
- `baseline.json` —— 由 `--save-baseline` 生成的当前基线。

## 回归判据

两项**同时**满足才判 FAIL，避免 `gfxinfo` 采样噪声误报：

| 指标 | 门槛 |
|---|---|
| Janky 占比 | 绝对增加 > 2 个百分点 **且** 相对恶化 > 30% |
| p99 帧耗时 | 相对恶化 > 20% |

## 何时必须跑

1. 任何涉及 `ui/glass/**`（玻璃渲染路径）的改动 —— 包括看似纯粹的「减少对象分配」重构；
2. 列表 / 网格布局或测量逻辑改动（如 PF3 一类的计算缓存）；
3. 正式版发布前（`docs/agents/release-runbook.md` 的发布前置项）。

玻璃路径改动另需**离屏探针**通关：
`gradlew :desktopApp:run "-PpreviewMainClass=com.shangkeschedule.GlassProbeKt"`，
判读锚点 `[control]` 必须为 0.000、`realGlass blur24 vs blur0` ≈ 128。
两者职责不同：探针守「玻璃是否还生效」，本基线守「是否变慢」。

## 已知限制

- **需要真机**，且**必须开启「USB 调试（安全设置）」**：`adb shell input tap/swipe` 需要
  `INJECT_EVENTS` 权限，MIUI 等 ROM 默认关闭，表现为
  `SecurityException: Injecting input events requires ... INJECT_EVENTS permission`。
  未开启时脚本在自检阶段直接以退出码 2 结束，**不会产出看似正常实则全 0 的数据**。
- **场景切换是半自动的**：脚本会提示在设备上切好「主题 + 页面」再回车采集，而不是遍历
  UI 树自动点击。原因是自动点击依赖界面文案，文案一改就静默点错地方、采到错误场景的
  数据。牺牲一点便利，换取「采的确实是这个场景」。
- `gfxinfo` 是采样统计，单次波动较大；建议同一版本跑两轮取较优值再 `--save-baseline`。
- **基线数值待补**：截至 2026-09-26 尚未在开启注入权限的设备上跑过完整 6 场景，
  `build_qa/soft_jank/baseline.json` 仍为空缺（`build_qa/` 亦被 gitignore）。
  历史参考值只有工作日志里那次「柔绘·课表 6.31% / p99 117ms」（v3.43.0）。
