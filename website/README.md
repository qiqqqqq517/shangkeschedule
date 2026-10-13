# 官网（website）

「上课 · ShangKeSchedule」的产品官网，纯静态站点，部署在 **Cloudflare Pages**。

- 线上地址：<https://shangke.asia>（自定义域）/ `https://<项目名>.pages.dev`（默认域）
- 设计语言：**书卷**（暖砂纸底 `#faf9f5` + 赤陶主色 `#c96442` + 衬线标题），与 App 内「书卷」主题同源
- 无构建步骤、无框架、无第三方 CDN：`wrangler pages deploy` 直接上传本目录即可

---

## 目录结构

```text
website/
├── index.html          首页（Hero / 核心功能 / 教务导入 / 排版演示 / 主题 / 界面速览 / 下载 / 更新 / FAQ）
├── features.html       完整功能说明（13 类能力逐项）
├── changelog.html      更新日志（版本时间线）
├── privacy.html        隐私政策
├── 404.html            未找到页面
├── _headers            Cloudflare Pages 响应头（CSP、缓存策略）
├── _redirects          Cloudflare Pages 跳转规则（/download、/github、/issues 短链）
├── robots.txt
├── sitemap.xml
└── assets/
    ├── css/site.css    全站样式（含设计 token 与响应式）
    ├── js/site.js      站点配置 + 交互（主题切换、滚动入场、链接注入）
    ├── fonts/          Poppins / Newsreader / Geist Mono / Noto Serif SC（子集）
    │                   每个西文字体提供 woff2（主源）+ ttf（回退）
    └── img/            App 截图（取自仓库根目录 picture/）与应用图标
                        每张图提供 webp（主源）+ 原图（回退）
```

---

## 发新版本时要改哪里

站点里所有版本号、下载链接、年份都由 `assets/js/site.js` 顶部的一个常量统一驱动：

```js
const SITE = {
  version: '4.77.4',                                  // ← 发版时改这里
  versionCode: '544',
  quark: 'https://pan.quark.cn/s/02947cbc1d4e',       // ← 换网盘分享链接时改这里
  quarkCode: '~9a263aTFyF~:/',                        // ← 对应的夸克口令
  ...
};
```

改完后页面上这些位置会自动同步，无需逐个页面手改：

- 所有带 `data-version` 的元素（Hero 按钮、下载区、更新日志页头）
- 带 `data-version-code` 的元素
- 带 `data-asset-name="arm64-v8a | armeabi-v7a | x86_64"` 的元素 → 自动拼出
  `shangke-v<version>-<abi>-release.apk` 文件名
- 带 `data-quark-code` 的元素 → 夸克口令文本；带 `data-copy` 的按钮 → 一键复制

### 下载入口的规则

站点有**两个**分发渠道，靠 `data-dl` 区分：

| 标记 | 指向 | 用在哪 |
| --- | --- | --- |
| `data-dl="quark"` | 夸克网盘分享（`SITE.quark`） | 全部主下载入口：导航栏、首屏、下载区按钮、各页脚、404 页 |
| `data-dl="release"` | GitHub Releases 最新版 | 仅下载区「安装提示」里那一句备用通道说明 |

> 换网盘链接时只改 `SITE.quark` / `SITE.quarkCode` 两个值即可，页面无需改动。
> `_redirects` 里的 `/download` 短链也要同步改（它不走 JS）。

> 每次发版后同步更新 `changelog.html`（顶部新增一条 `tl-item`）与 `sitemap.xml` 的 `lastmod`。

---

## 本地预览

```powershell
# 站点根目录起一个静态服务器（不要直接 file:// 打开，字体的跨域与相对路径会异常）
python -m http.server 8080 --directory website
# 然后访问 http://localhost:8080/
```

## 部署到 Cloudflare Pages

```powershell
cd D:\01课程表\shangkeschedule

# 首次：创建 Pages 项目
npx wrangler pages project create shangkeschedule --production-branch main

# 每次发布：直接上传本目录（无需构建）
npx wrangler pages deploy website --project-name shangkeschedule --branch main
```

自定义域绑定（在 `shangke.asia` 已托管于 Cloudflare 的前提下）：

```powershell
npx wrangler pages domain add shangke.asia       --project-name shangkeschedule
npx wrangler pages domain add www.shangke.asia   --project-name shangkeschedule
```

---

## 维护提示

- **字体格式**：`assets/fonts/` 下每个西文字体都有 **woff2 + ttf 两份**。`site.css` 里每个
  `@font-face` 声明两条 `src`，woff2 在前、ttf 兜底 —— 浏览器只下载第一条支持的格式，不会重复下载。
  woff2 是由仓库里的 ttf 直接转出的（无需设计侧重新导出）：

  ```bash
  python -c "
  from fontTools.ttLib import TTFont
  import pathlib
  for f in sorted(pathlib.Path('website/assets/fonts').glob('*.ttf')):
      ft = TTFont(str(f)); ft.flavor = 'woff2'; ft.save(str(f.with_suffix('.woff2')))
  "
  ```

  实测体积：Newsreader 441→210KB、Poppins 157→50KB、Geist Mono 168→69KB（合计 -55%）。
  新增字体时请同样提供 woff2 主源。
- **图片格式**：`assets/img/` 下每张图都有 **webp + 原图（jpg/png）两份**。HTML 里用
  `<picture>` 包一层：`<source srcset="*.webp" type="image/webp">` 在前、`<img>` 指原图兜底。
  WebP 由原图生成（`python build_qa/webopt/make_webp.py`，quality=82），实测 -51%。
  **换截图后需同时重新生成 webp**，否则 `<source>` 会指向不存在的文件（浏览器会自动回退原图，
  不会白屏，但省不下体积）。`og:image` / favicon / apple-touch-icon **保持 PNG 不转**（爬虫兼容性）。
- **字体**：`NotoSerifSC-Subset.woff2`（约 99KB）是用 `fonttools` 按站点标题用字（461 字）子集化生成的。
  **如果新增了标题文案且用到了生僻字**，需要重新子集化，否则该字会回退到系统宋体：
  ```python
  # 需要 fonttools + brotli；源字体为 Noto Serif SC 可变字体
  from fontTools import subset
  from fontTools.ttLib import TTFont
  from fontTools.varLib.instancer import instantiateVariableFont
  ```
  子集字符集 = 所有 `*.html` 中 `<h1>`–`<h4>` 内的文字 + ASCII + 常用中文标点。
- **字体粗细**：子集字体是 `wght=500` 的静态实例，`site.css` 中对应 `font-weight: 500`；
  若要把标题改成其他字重，需重新生成子集。
- **截图**：`assets/img/*.jpg` 从仓库根目录 `picture/` 复制而来（540×1170）。更新 App 截图后需重新复制。
- **结构化数据**：4 个可索引页（index / features / changelog / privacy）带 JSON-LD
  （`SoftwareApplication` + `BreadcrumbList`）。**`404.html` 不加** —— 它已带 `noindex`，
  加 canonical/JSON-LD 反而是错误信号。版本号由脚本从 `site.js` 的 `SITE.version` 派生，勿手改。
- **深色模式**：全站支持，偏好存在 `localStorage['sk-theme']`；`<head>` 内联脚本负责首帧前定色，避免闪白。
- **CSP**：`_headers` 里限制了脚本与样式来源，新增外链资源（图片、字体、脚本）时需同步放宽对应指令。
  当前 `img-src 'self' data:` 已覆盖 webp，无需为 WebP 改动。

### 优化后自检脚本

`build_qa/webopt/` 下有一组只读校验脚本，改完站点应全绿：

```bash
python build_qa/webopt/verify_picture.py   # <picture> 开闭配对 + webp 引用存在
python build_qa/webopt/verify_jsonld.py    # JSON-LD 合法性 + softwareVersion 与 site.js 一致
python build_qa/webopt/smoke.py            # 起 http.server 8099 逐页逐资源 200 核验
python build_qa/webopt/savings.py          # 输出传输体积收益
```
