# 系统字体缩放审计（AC4）

> 对应《界面优化方向清单》**AC4 系统字体缩放适配**。
> 清单原判断：「完全无字体缩放适配」，验收方式为「装机 130/150/200%」。

## 结论

**清单的判断需要修正：本项目在 2.0× 系统字号下不存在裁切问题。**

| 检查项 | 结果 |
|---|---|
| 固定高度容器的垂直裁切（2.0×） | **0 处** |
| 文本单位 | 全库文本用 **sp**（随系统缩放），无「用 dp 当字号」的写法 |
| 单行文本兜底 | 单行 + 固定宽度处均有 `TextOverflow.Ellipsis`，溢出时省略号收尾 |
| 横向溢出 | 5 处候选，抽查均为「固定宽度标签 + 可伸缩文本」的正常设计，有省略号兜底 |

⇒ **不需要引入响应式布局改造**。为放大字体重构固定高度，反而会带来无收益的观感变化风险。

## 为什么「没有适配代码」却仍然通过

这是本次审计最值得记录的认知：**字体缩放的正确性主要来自单位选择，而不是适配代码**。

1. **文本用 sp**：`fontSize = 12.sp` 会随系统字号一起放大。
2. **`lineHeight` 也用 sp**：全库 91 处硬编码 `lineHeight = N.sp`，与 `fontSize` **等比缩放**，
   相对关系不随缩放改变 —— 因此不会出现「字号变大但行高不变」导致的挤压。
   （真正危险的是 `fontSize` 用 sp 而 `lineHeight` 用 dp 固定，本项目没有这种写法。）
3. **固定高度容器留有余量**：按 2.0× 判定，无一处的可用高度小于所需行盒
   （含底栏：图标 28dp + 间距 0.5dp + 文字行盒 33.6dp ≈ 62dp < 容器 64dp）。
4. **单行文本有省略号兜底**：横向即使放不下也只是省略，不会撑破布局。

## 方法

`scripts/check_font_scale.py`

```bash
python scripts/check_font_scale.py            # 默认按 2.0× 判定
python scripts/check_font_scale.py --scale 1.5
```

判据（保守）：

```
容器可用高度 = height − 上下 padding
2.0× 下文本行盒 ≈ fontSize × 1.4 × 2.0
若可用高度 < 行盒 ⇒ 判定可能裁切
```

**实现上踩过的坑（写此类静态审计必看）**：
第一版按「height 位置往前找最近的容器名」配对，把 `Spacer(Modifier.height(4.dp))` 的 4dp
错配到它前面某个 `Column`，再与远处的 `fontSize` 拼在一起，**误报 30 处**
（出现 `Column(height 4.0dp)` 需要 56dp 这种荒谬结论）。
正确做法是要求 **height 出现在该容器自身的参数区内**，且 fontSize 在同一容器块内。

## 局限（务必知悉）

- 只覆盖 `Row / Box / Column / Surface / Card / Button / TextButton / Scaffold`
  这 8 类容器，及容器内**直接出现**的 `fontSize`；自定义组件内部、Lazy 项、
  Compose 自动测量造成的挤压不在范围内。
- 行高系数 1.4 是经验值，不是精确字体度量；结论是「未发现风险」，不等于「数学证明无风险」。
- **真机实测仍是最终依据**。方法（不需要输入注入权限）：

```bash
adb shell settings get system font_scale           # 当前值（本机实测设备为 0.92）
adb shell settings put system font_scale 1.3       # 依次测 1.0 / 1.3 / 1.5 / 2.0
adb shell am force-stop com.shangkeschedule && adb shell am start -n com.shangkeschedule/.MainActivity
# 逐页目视检查后**务必还原**为原值
adb shell settings put system font_scale 0.92
```

> 注意：装机实测会覆盖设备上已安装的版本，需先确认；且改系统字号属于修改用户设备设置，
> 测完必须还原。

## 与清单其他条目的关系

- 清单曾把「底栏图标-文字在大字体下是否重叠」列为待实测（原为 P0，已降 P2）。
  本次判定给出结论：**不会重叠** —— 文字行高与字号同为 sp、等比缩放，
  底栏 64dp 容器在 2.0× 下仍有余量（≈62dp）。
- AC5「无障碍回归纳入流程」可把本脚本纳入例行检查（改布局后重跑）。
