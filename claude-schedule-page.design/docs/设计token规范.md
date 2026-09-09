# 上课Shangke · Claude 设计系统 token 规范

> 本文件是设计包的唯一 token 事实来源（single source of truth）。
> 所有值逐项搬运自设计稿「今日日程 等 5 个设计 / pages」，未做任何改写；
> 代码实现见 `assets/claude-tokens.css`（浅色 `:root` / 深色 `[data-theme="dark"]`）。

---

## 1. 原始色阶

### 1.1 品牌色 Brand

| Token | 浅色 | 深色 |
| --- | --- | --- |
| `--brand-50` | `#fbf2ed` | `#3a2a22` |
| `--brand-100` | `#f4e0d5` | `#4d3528` |
| `--brand-200` | `#ebc6b6` | `#6b4533` |
| `--brand-300` | `#e0a892` | `#8a5740` |
| `--brand-400` | `#d6866a` | `#b0683f` |
| **`--brand-500`** | **`#c96442`** | **`#d97757`** |
| `--brand-600` | `#b0562f` | `#e08d6f` |
| `--brand-700` | `#934828` | `#e8a98f` |
| `--brand-800` | `#753a22` | `#f0c6b3` |
| `--brand-900` | `#582e1d` | `#f8e3d8` |

### 1.2 文字色 Text

| Token | 浅色 | 深色 |
| --- | --- | --- |
| `--text-50` | `#f6f5f0` | `#1b1b19` |
| `--text-100` | `#ece9de` | `#2c2c2b` |
| `--text-200` | `#dad9d4` | `#46443b` |
| `--text-300` | `#c2c0b6` | `#6e6d68` |
| `--text-400` | `#9b988c` | `#908e84` |
| `--text-500` | `#6e6d68` | `#b7b5a9` |
| `--text-600` | `#535146` | `#c3c0b6` |
| `--text-700` | `#46443b` | `#d8d6cd` |
| **`--text-800`** | **`#3d3929`** | **`#f1f1ef`** |
| `--text-900` | `#28261b` | `#faf9f5` |

### 1.3 背景色 Background

| Token | 浅色 | 深色 |
| --- | --- | --- |
| `--bg-50` | `#ffffff` | `#1b1b19` |
| **`--bg-100`** | **`#faf9f5`** | **`#262624`** |
| **`--bg-200`** | **`#f5f4ef`** | **`#2c2c2b`** |
| `--bg-300` | `#ede9de` | `#30302e` |
| `--bg-400` | `#e3e0d4` | `#3e3e38` |
| `--bg-500` | `#dad9d4` | `#4a4a43` |
| `--bg-600` | `#cdcabf` | `#52514a` |
| `--bg-700` | `#b4b2a7` | `#6e6d68` |
| `--bg-800` | `#8a887e` | `#908e84` |
| `--bg-900` | `#535146` | `#b7b5a9` |

### 1.4 边框色 Border

| Token | 浅色 | 深色 |
| --- | --- | --- |
| `--border-50` | `#f0efe9` | `#1f1e1d` |
| `--border-100` | `#ebebeb` | `#2c2c2b` |
| `--border-200` | `#e3e0d4` | `#343430` |
| **`--border-300`** | **`#dad9d4`** | **`#3e3e38`** |
| `--border-400` | `#cdcabf` | `#4a4a43` |
| `--border-500` | `#b4b2a7` | `#52514a` |
| `--border-600` | `#9b988c` | `#6e6d68` |
| `--border-700` | `#6e6d68` | `#908e84` |
| `--border-800` | `#535146` | `#b7b5a9` |
| `--border-900` | `#3d3929` | `#d8d6cd` |

### 1.5 图标色 Icon

| Token | 浅色 | 深色 |
| --- | --- | --- |
| `--icon-100` | `#dad9d4` | `#2c2c2b` |
| `--icon-300` | `#9b988c` | `#6e6d68` |
| `--icon-500` | `#535146` | `#b7b5a9` |
| **`--icon-700`** | **`#3d3929`** | **`#f1f1ef`** |
| `--icon-900` | `#141413` | `#ffffff` |

### 1.6 状态色

| Token | 浅色 | 深色 |
| --- | --- | --- |
| `--success-50` | `#f0f3ea` | `#232a1c` |
| **`--success-500`** | **`#788c5d`** | **`#8ca06f`** |
| `--error-50` | `#fcecea` | `#3a1f1f` |
| **`--error-500`** | **`#d64545`** | **`#ef4444`** |

### 1.7 图表 / 课程色 Chart

| Token | 浅色 | 深色 | 用途 |
| --- | --- | --- | --- |
| `--chart-1` | `#b05730` | `#b05730` | 课程块 1（必修主色） |
| `--chart-2` | `#9c87f5` | `#9c87f5` | 课程块 2（必修强调） |
| `--chart-3` | `#ded8c4` | `#1a1915` | 课程块 3（实验 / 选修） |
| `--chart-4` | `#dbd3f0` | `#2f2b48` | 课程块 4 |
| `--chart-5` | `#b4552d` | `#b4552d` | 课程块 5 |

---

## 2. 语义 token

| 语义 | 浅色取值 | 深色取值 |
| --- | --- | --- |
| `--background` | `var(--bg-100)` | `var(--bg-100)` |
| `--card` | `var(--bg-200)` | `var(--bg-200)` |
| `--popover` | `var(--bg-50)` | `var(--bg-300)` |
| `--muted` | `var(--bg-300)` | `var(--bg-50)` |
| `--foreground` | `var(--text-800)` | `var(--text-800)` |
| `--card-foreground` | `var(--icon-900)` | `var(--text-900)` |
| `--popover-foreground` | `var(--text-900)` | `#e5e5e2` |
| `--primary` | `var(--brand-500)` | `var(--brand-500)` |
| `--primary-foreground` | `#ffffff` | `#141413` |
| `--secondary` | `#e9e6dc` | `var(--text-900)` |
| `--secondary-foreground` | `var(--text-600)` | `var(--bg-300)` |
| `--muted-foreground` | `var(--text-500)` | `var(--text-500)` |
| `--accent` | `var(--secondary)` | `#1a1915` |
| `--accent-foreground` | `var(--text-900)` | `#f5f4ee` |
| `--border` | `var(--border-300)` | `var(--border-300)` |
| `--input` | `var(--border-500)` | `var(--border-500)` |
| `--ring` | `var(--brand-500)` | `var(--brand-500)` |
| `--destructive` | `var(--error-500)` | `var(--error-500)` |
| `--success` | `var(--success-500)` | `var(--success-500)` |
| `--sidebar` | `#f5f4ee` | `#1f1e1d` |
| `--sidebar-foreground` | `#3d3d3a` | `#c3c0b6` |
| `--sidebar-accent` | `#e9e6dc` | `#0f0f0e` |
| `--sidebar-accent-foreground` | `#343434` | `#c3c0b6` |
| `--sidebar-border` | `var(--border-100)` | `#ebebeb` |

---

## 3. 非色彩 token

### 3.1 字体

| Token | 字族 | 用途 |
| --- | --- | --- |
| `--font-display` | `Newsreader, Georgia, ui-serif, serif` | 页面大标题、统计数字 |
| `--font-sans` | `Poppins, ui-sans-serif, system-ui, sans-serif` | UI 正文、按钮、卡片标题 |
| `--font-serif` | `Lora, Georgia, ui-serif, serif` | 阅读正文、eyebrow 小标题 |
| `--font-mono` | `Geist Mono, ui-monospace, monospace` | token、代码、日期 |
| `--font-cjk` | 系统中文（PingFang SC / HarmonyOS Sans SC / Noto Sans SC / Microsoft YaHei） | 中文回退 |

> 设计稿的 4 款字体均不含 CJK 字形，中文按平台系统字体渲染（设计稿本身亦然）。
> 设计包已把 4 款字体下载到 `assets/fonts/`，离线可用。

### 3.2 字阶（设计稿实测）

| 场景 | 字号 / 字重 / 行高 | 字族 |
| --- | --- | --- |
| 页面标题（今日日程） | 28px / 600 / 1.15 | Newsreader |
| 页面标题（教务导入） | 32px / 600 / 1.1 | Newsreader |
| 分区大标题 | 22px / 600 / 1.2 | Poppins |
| 卡片标题 | 18–21px / 600 / 1.2 | Poppins |
| 行标题 | 15–17px / 600 / 1.25 | Poppins |
| 正文 | 14–15px / 400 / 1.6 | Poppins |
| 辅助文字 | 12–13px / 500 / 1.4 | Poppins |
| eyebrow / 分区标签 | 10–12px / 600 / 1.2 + `letter-spacing: .12em` + 大写 | Lora |
| token / 代码 | 11–13px / 500 | Geist Mono |

### 3.3 圆角

| Token | 值 | 用途 |
| --- | --- | --- |
| `--radius-sm` | `8px` | 课程块、小标签 |
| `--radius-md` | `12px` | 色卡、图标容器 |
| `--radius` | `16px` | 卡片、按钮、输入框（主力圆角） |
| `--radius-xl` | `20px` | 底栏、弹层顶角 |
| `--radius-2xl` | `24px` | 底部弹层 |
| `--radius-full` | `9999px` | 胶囊、圆点、头像 |

### 3.4 阴影

| Token | 值 |
| --- | --- |
| `--shadow-xs` | `0 1px 3px 0 rgba(0,0,0,.05)` |
| `--shadow-sm` | `0 1px 3px 0 rgba(0,0,0,.1), 0 1px 2px -1px rgba(0,0,0,.1)` |
| `--shadow-md` | `0 1px 3px 0 rgba(0,0,0,.1), 0 2px 4px -1px rgba(0,0,0,.1)` |
| `--shadow-lg` | `0 1px 3px 0 rgba(0,0,0,.1), 0 4px 6px -1px rgba(0,0,0,.1)` |
| `--shadow-xl` | `0 1px 3px 0 rgba(0,0,0,.1), 0 8px 10px -1px rgba(0,0,0,.1)` |
| `--shadow-2xl` | `0 1px 3px 0 rgba(0,0,0,.25)` |

### 3.5 间距

| Token | 值 | 说明 |
| --- | --- | --- |
| `--spacing` | `0.25rem` = 4px | 全站间距基数，所有 padding/gap 均为其倍数 |

常用倍数：页面水平内边距 `×5`（20px）、卡片内边距 `×4`（16px）、卡片间距 `×3`（12px）、分区间距 `×6`（24px）、状态栏顶部 `×3`（12px）。

### 3.6 动效

| 场景 | 曲线 / 时长 |
| --- | --- |
| 颜色 / 边框 / 阴影过渡 | `.16s ease` |
| 卡片悬浮位移 | `.16s ease`，`translateY(-1px)` |
| 弹层进出 | `.2s`（透明度）+ `.24–.25s cubic-bezier(.2,.8,.2,1)`（位移） |
| 进度条 | `.35–.4s cubic-bezier(.2,.8,.2,1)` |
| 进行中圆点呼吸 | `1.5s ease-in-out infinite` |

> 已实现 `prefers-reduced-motion: reduce` 降级。

---

## 4. 多端适配断点

| 名称 | 宽度 | 导航形态 | 布局 |
| --- | --- | --- | --- |
| `phone-sm` | ≤ 380px | 底部浮起胶囊 | 单列；页面内边距 16px、标题降级 |
| `phone` | 381–767px | 底部浮起胶囊 | 单列，内容宽 430px 居中 |
| `tablet` | 768–1199px | 左侧栏 236px | 主区居中（≤720px），列表转多列网格 |
| `desktop` | ≥ 1200px | 左侧栏 + 右详情栏 360px | 三栏：侧栏 / 主区 / 概览 |

补充适配：

- 安全区：`env(safe-area-inset-*)` 修正左右内边距与底栏底部间距。
- 横屏矮屏（高度 ≤ 520px）：隐藏状态栏、压缩纵向留白。
- 触控目标：按钮最小 44px，底栏项最小 48px 高。
- 焦点可见：所有可交互元素 `:focus-visible` 均有 2px `--ring` 描边。
- 键盘：tab 组支持 ←/→/Home/End 方向键漫游；弹层 Esc 关闭并回焦。

---

## 5. 图标

- 来源：`lucide-static` v1.43.0（ISC License），共 28 个图标落盘在 `assets/icons/`。
- 打包方式：`tools/build-icon-sprite.mjs` 生成 `assets/claude-icons.js`（SVG sprite），页面用
  `<svg class="icon"><use href="#i-clock"></use></svg>` 引用，随 `currentColor` 自动跟随主题。
- 尺寸：`.icon-xs` 12px · `.icon-sm` 14px · `.icon` 18px · `.icon-lg` / `.nav-icon` 20px。

---

## 6. 重新生成与校验

```bash
cd claude-schedule-page.design

# 重新拉取图标（需要网络）
node tools/fetch-icons.mjs

# 重建图标 sprite
node tools/build-icon-sprite.mjs

# 多端 + 交互校验（无头 Chromium，24 个断点组合 + 5 组交互）
node tools/verify-pages.mjs

# 同时输出各断点截图到 _verify/
node tools/verify-pages.mjs --screenshot

# WCAG 2.2 AA 对比度审计
node tools/check-contrast.mjs
```

---

## 7. 无障碍修正（唯一对设计稿取值的偏离）

设计稿中「实底 + 白字」的两个组合低于 WCAG 2.2 AA 的 4.5:1 正文要求：

| 场景 | 设计稿取值 | 实测对比度 | 修正为 | 修正后 |
| --- | --- | --- | --- | --- |
| 主色按钮（白字） | `#c96442` | 3.90:1 ❌ | `--primary-solid: #b0562f`（brand-600） | 4.98:1 ✅ |
| 成功徽章（白字） | `#788c5d` | 3.68:1 ❌ | `--success-solid: #4f5d3a`（success-700） | 7.10:1 ✅ |

处理原则：

- **品牌色本身不变**：`--primary` 仍为 `#c96442`，所有描边、进度条、图标、悬浮态、选中态等大面积品牌观感与设计稿完全一致。
- **仅在「实底 + 小字白字」场景使用降明度变体**（`.btn.primary` / `.badge.filled` / `.badge.success` / 底栏选中项 / 周次胶囊 / 导入步骤圆点），色相与设计稿同族，只降明度。
- 深色模式无需修正：`#d97757` 配深字 5.90:1、`#8ca06f` 配深字 6.47:1 均已达标，沿用设计稿原值。
- 如需 100% 还原设计稿，只需把 `assets/claude-tokens.css` 中的 `--primary-solid` / `--success-solid` 改回 `var(--brand-500)` / `var(--success-500)` 即可（单点回退）。

审计结果：`node tools/check-contrast.mjs` → 浅色 10 项、深色 10 项全部 PASS。

---

## 8. 版本控制说明

仓库根 `.gitignore` 含 `tools/` 与 `_verify/` 两条规则，因此本设计包下的
`claude-schedule-page.design/tools/`（校验脚本）不会入库；页面、资源、字体与文档均会正常入库。

如需把校验脚本一并提交，在 `.gitignore` 的 `tools/` 规则后加一行：

```gitignore
!claude-schedule-page.design/tools/
```

校验截图输出到仓库根 `build_qa/claude-design/`（`build_qa/` 已被忽略），不污染设计包目录。

