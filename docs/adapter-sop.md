# 教务系统适配标准流程（SOP）

> **读者**：为本项目新增 / 修复教务系统适配的开发者与 AI 会话。
> **覆盖场景**：① 新增学校适配；② 修复既有学校的适配问题；③ 新增通用教务平台支持。
> **覆盖范围**：从「接到一所学校的适配需求」到「用户端可正常导入课表」的端到端流程。
> **相关文档**：脚本 API 细节见 `shared/assets/offline_repo/schools/ADAPTER_GUIDE.md`；私有仓库与安全约定见 `.adapter_private/README.md`（仅本地）；正式发版流程见 `docs/agents/release-runbook.md`。
>
> 本文命令均在本机（Windows / PowerShell）核验。涉及 `tools/` 与 `.adapter_private/` 的脚本**不入主仓库 git**，属本地工具与私有仓库工作副本。

---

## 一、架构总览

适配脚本是一段运行在 **App 内嵌 WebView** 里的 JS：用户在 WebView 中登录教务系统后，App 注入脚本，脚本抓取课表数据并通过 JS Bridge 交回原生侧落库。同一份脚本有**两个分发出口**：

```
适配脚本 <学校代码>/<脚本>.js
   │
   ├─【离线内置 · 随 APK 发布】
   │   shared/assets/offline_repo/                        （主仓库，git 跟踪）
   │     ├─ tools/build_schools.py ──▶ index/school_index.pb   学校索引
   │     └─ tools/rebuild_zip.py ──▶ composeResources/files/offline_schools.zip
   │            → 装机后 ResourceInitializerManager 解压到 filesDir/repo/（离线兜底）
   │
   └─【OTA 热更新 · 不用发版】
       .adapter_private/                                   （独立私有仓库工作副本）
         ├─ adapters/<CODE>/<js> + index/school_index.pb
         ├─ tools/build_index.py ──▶ index.json（逐文件 sha256 清单）
         └─ git push → 私有仓库 schedule-adapter-private
                → Cloudflare Worker adapter.shangke.asia（X-App-Secret 鉴权）
                → App 启动时 AdapterRemoteUpdater.sync() 拉清单、比哈希、下载、校验、原子写入 repo/
```

**运行时执行流**（`WebViewScreen.kt` / `WebBridgeProtocol.kt`）：

1. 用户选学校（读 `repo/index/school_index.pb`）→ 选适配器（含 `import_url` 入口地址）；
2. WebView 打开入口，用户自行完成登录（App 不碰账号密码）；
3. 用户点「执行导入」，App 从 `repo/schools/resources/<资源目录>/<脚本>.js` 读取脚本源码；
4. 注入 `buildImportScript()`：Bridge 初始化 + 适配脚本 + 自启动探测，一次注入完成；
5. 脚本经 `shangkeBridgePromise.saveImportedCourses()` 把课程 JSON 交回原生侧保存，`notifyTaskCompletion()` 收尾返回课表页。

**一句话记住**：改脚本 / 加学校 → 走热更新（阶段四），不用发版；要更新离线兜底包或改 App 原生逻辑 → 才需要发版（见第八节）。

---

## 二、阶段〇 · 前置调研（动手前必做）

### 2.1 输入清单

| 材料 | 必要性 | 说明 |
|---|---|---|
| 学校全称 | 必需 | 用于索引注册与拼音首字母检索 |
| 教务系统入口 URL | 必需 | 注意区分直连 / WebVPN / 统一认证入口 |
| 课表页 DOM 或接口样例 | 强烈建议 | 登录后课表页的 HTML 或 AJAX 响应，解析开发的依据 |
| 测试账号 | 建议 | 无账号时需请需求方配合走查 |

### 2.2 判定教务系统类型

对照七大通用平台（`tools/build_schools.py` 的 `GENERAL_PLATFORMS` / `TYPE_TO_JS`）：

| 平台 | 资源目录 | 判定线索 |
|---|---|---|
| 正方 | `zhengfang/` | URL 含 `jwglxt`、页面「正方教务管理系统」 |
| URP | `urp/` | URL 含 `urp`、老式 frame 页面 |
| 青果（金智 Kingosoft） | `kingosoft/` | URL 含 `kingosoft` / `kjgl` |
| 强智 | `qiangzhi/` | URL 含 `zhxg` / `tfstu` 等 |
| 金智 Wisedu | `wisedu/` | URL 含 `wisedu`，EAMS 页面 `/student/for-std/...` |
| 南软 | `south_soft/` | URL 含 `southsoft` / `nssoft` |
| 超星 | `chaoxing_jiaowu/` | 超星教务 / 学习通入口 |

判定不了就打开课表页看 DOM / 抓 AJAX，再对照 `_timetable_parsers/` 与各平台脚本确认。

### 2.3 判定入口形态

- **直连**：公网可达教务域名；
- **WebVPN / 校外通道**：域名形如 `webvpn.xxx.edu.cn`，路径带编码串（参考 `SPECIAL_ADAPTERS` 中盐城师范、沈阳农大条目）；
- **统一认证（CAS/SSO/ehall）**：登录在门户、教务在跳转后（参考安徽大学 `AHU`、湖北工程学院条目）。

校外不可达时应考虑**多入口拆分**（校内一个、校外一个），让用户按环境选择，避免「门户不可达卡死」。

### 2.4 决策树（选路径）

```
学校是否已在学校索引中（搜 timetable_schools.json / build_schools.py 输出）？
├─ 已在，且用户反馈适配坏了 ────────────▶ 修复脚本：阶段一 → 阶段三 → 阶段四（通常无需发版）
├─ 已在，但教务换了系统 / 入口变了 ──────▶ 修正注册数据（type/url）+ 视情况换脚本：
│                                          阶段一(可选) → 阶段二 → 阶段三 → 阶段四
├─ 全新学校，属七大通用平台 ─────────────▶ 只注册、不写新脚本：阶段二 → 阶段三 → 阶段四
├─ 全新学校，页面有定制 / 平台不识别 ────▶ 写专用适配器：阶段一 → 二 → 三 → 四
└─ 需新增一个通用平台（如「乘方」类系统）─▶ 写平台脚本进 `adapters/<平台>/`，
                                           并在 `build_schools.py` 的 `GENERAL_PLATFORMS`
                                           与 `TYPE_TO_JS` 注册，后续走阶段二 → 三 → 四
```

> 判据：通用平台脚本在目标学校页面上能跑通就绝不写专用脚本；专用脚本只在「通用脚本确实解析不了」时写。

---

## 三、阶段一 · 编写适配器

### 3.1 目录与命名

- 目录：`<学校代码>/`（**大写**，与索引 `resource_folder` 一致，如 `AHU`、`UESTC`）；
- 脚本：`<名称小写>.js`（如 `ahu.js`；同校多版本可拆多个文件，但注意阶段二「多 JS 取字典序第一个」的坑）；
- 一份脚本**必须同时落到两处**（内容一致；跨仓库比较按 LF 归一化哈希，见 4.4）。

### 3.2 运行环境与入口契约

- 脚本在教务系统页面的 WebView 内执行，**与教务站点同源**：用相对路径 `fetch(path, { credentials: 'include' })` 即可携带登录态；
- **必须挂统一入口**（自启动探测的第一优先级）：

```js
window.shangkeImportEntry = myRunImport;   // 函数需返回 Promise，异常走 .catch
```

- 未挂 `shangkeImportEntry` 时，探测会依次尝试 `startImport / runImport / scheduleImport / importCourses / importSchedule`，最后兜底扫描脚本新挂载的 `*Import` 函数；
- 注入后 1500ms 内没触发任何 Bridge 动作，App 会提示「未找到导入入口」；
- **禁止**在脚本里「等待外部再调用」的旧模式——一次点击必须能跑完整个导入。

### 3.3 Bridge API 速查

异步（`window.shangkeBridgePromise.*`，全部返回 Promise）：

| 方法 | 参数 | 用途 |
|---|---|---|
| `showAlert(title, content, confirmText?)` | 文案 | 确认弹窗，resolve `true/false` |
| `showPrompt(title, tip, defaultText?, validatorJs?)` | 文案 | 输入框，resolve 输入值 / `null` |
| `showSingleSelection(title, itemsJson, defaultIndex?)` | 选项 JSON 字符串 | 单选，resolve 索引；取消为 `null` 或负值，需判空 |
| `saveImportedCourses(coursesJson)` | 课程数组 JSON 字符串 | **落库**，导入的核心动作 |
| `saveCourseConfig(configJson)` | 配置 JSON | 保存课表配置（学期起始日等） |
| `savePresetTimeSlots(timeSlotsJson)` | 作息 JSON | 保存预设作息 |

同步（`window.shangkeBridge.*`，无返回值）：

| 方法 | 用途 |
|---|---|
| `showToast(message)` | 轻提示（进度 / 失败原因） |
| `notifyTaskCompletion()` | **导入收尾**，调用后 App 自动返回课表页 |

兼容层：`Bridge` / `BRIDGE` / `AndroidBridge(Promise)` 已由 App 映射到上述实现，新脚本**不要**再用旧命名。

### 3.4 课程 JSON 字段（`saveImportedCourses` 数组元素）

```json
{
  "name": "高等数学",
  "teacher": "张三",
  "position": "教学楼A101",
  "day": 1,
  "startSection": 1,
  "endSection": 2,
  "weeks": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16],
  "isLab": false
}
```

| 字段 | 类型 | 约定 |
|---|---|---|
| `name` | string | 必填，课程名 |
| `teacher` | string | 空时填「未安排」；清理括号职称（可用 `TimetableParser.cleanTeacherName`） |
| `position` | string | 上课地点，缺失填「待定」 |
| `day` | int | 1~7（周一=1，周日=7） |
| `startSection` / `endSection` | int | 节次，`endSection ≥ startSection ≥ 1` |
| `weeks` | int[] | **升序**，支持单双周（如 `[1,3,5,...]`） |
| `isLab` | boolean | 可选，实验课标记 |
| `color` / `remark` | - | 可选扩展；颜色留空由 App 自动分配 |

### 3.5 推荐模板与公共工具库

- **模板**：`.adapter_private/adapters/AHU/ahu.js`（金智 EAMS + WebVPN + CAS，230 行）——完整示范了「登录引导 → 学期选择 → 拉数据 → 解析 → 合并 → 保存 → 收尾 → 异常处理」全流程，新脚本照此骨架写；
- **公共工具库**：`_common/timetable_parser_utils.js` 提供 `TimetableParser`（`parseWeeks` / `parseSections` / `parseDay` / `mergeConsecutiveCourses` / `normalizeCourse` / `buildBridgeCourses` 等），周次 / 节次解析**优先复用**，API 详见 `ADAPTER_GUIDE.md`；
- 平台型解析器集中在 `_timetable_parsers/`（正方 / URP / 青果 / 强智 / 金智 / 南软 / 超星），写同平台专用脚本前先读对应平台脚本。

### 3.6 编码注意事项（检查清单）

- [ ] 脚本顶层不做任何立即执行的重活——可能被注入到非课表页，DOM 操作前先判断页面特征；
- [ ] 所有异步路径有 `.catch`，失败用 `showToast` 给出**人话**原因（「尚未登录，请先登录教务系统」而非裸异常）；
- [ ] `fetch` 一律 `credentials: 'include'`，并先请求课表页校验登录态（命中「登录页特征」即引导用户先登录）；
- [ ] 周次数组**升序**；同一课程连续节次**合并**；同 key 课程（名/师/地点/星期/节次相同）**合并周次**而非重复建块；
- [ ] 单双周（`7~17(单)周`）、多段周次（`1-8,10-16`）要解析正确；
- [ ] 保存成功后调用 `notifyTaskCompletion()`；
- [ ] 不硬编码无关学校信息；不在脚本内打日志输出敏感内容。

---

## 四、阶段二 · 注册学校索引（主仓库侧）

> 全新学校必须走本阶段；只修既有脚本可跳过（索引没变）。

### 4.1 三条注册路径（`tools/build_schools.py`，本地工具不入库）

| 路径 | 适用 | 改哪里 |
|---|---|---|
| **timetable 数据集** | 学校已在 `shared/assets/offline_repo/schools/timetable_schools.json`（1778 条，含研究生体系） | 修正该条的 `type` / `url`；`type` 按 `TYPE_TO_JS` 映射到通用平台脚本 |
| **MANUAL_SCHOOLS** | 全新学校手动注册 | `build_schools.py` 的 `MANUAL_SCHOOLS` 列表（id/名称/拼音首字母/适配器/import_url） |
| **SPECIAL_ADAPTERS** | 多入口学校（校内/校外、WebVPN/直连拆分） | `build_schools.py` 的 `SPECIAL_ADAPTERS`（一个学校 id 挂多个 adapter 条目） |

选型：全新学校优先加进 `timetable_schools.json`（数据集完备、随构建统计）；需要多入口或特殊接线才用 `SPECIAL_ADAPTERS`；完全不在数据集体系的用 `MANUAL_SCHOOLS`。

### 4.2 域名匹配规则（务必先读，防误配）

`build_schools.py` 按学校 URL 自动匹配专属资源目录：完整子域名 → 主域名 → 域内片段，逐级尝试 `resource_map`（offline_repo 资源目录名 → 首个 JS）。两个铁律：

1. **裸子串兜底仅限白名单**：历史上「域名包含文件夹名」的兜底把 **44 所学校**误配到无关适配器（如中国政法大学 `cupl` 误配中国石油大学 `CUP`），现仅 `SUFFIX_FALLBACK_WHITELIST` 中的组合允许；新增白名单条目必须逐个确认域名归属；
2. **一个资源目录多个 JS 时按字典序取第一个**：需要指定特定 JS（如 `cuit_bk_new.js`）时，必须用 `SPECIAL_ADAPTERS` 显式接线，不能靠文件名排序。

### 4.3 构建索引

```powershell
# 依赖：tools/school_index_pb2.py（本地）与 pip 包 pypinyin；脚本内含本机绝对路径
python tools/build_schools.py
```

输出必须逐项核对：

- `资源文件夹数量` / `匹配专用资源脚本` 数量符合预期；
- 「adapter 指向不存在的 JS」警告 **必须为 0**（有告警即用户点导入会无反应）；
- 「未被任何学校引用的适配器文件（孤儿）」警告必须处理：要么接线、要么删除——**改孤儿文件对用户完全无效**；
- 需要 CI 化把关时可加 `--strict`（存在孤儿即退出 1）。

### 4.4 双落点复制与校验（`scripts/check_adapters.py`）

同一份产物要同时存在于两处，**内容一致**：

| 产物 | 主仓库（离线内置源） | 私有仓库工作副本（热更新源） |
|---|---|---|
| 适配脚本 | `shared/assets/offline_repo/schools/resources/<CODE>/<js>` | `.adapter_private/adapters/<CODE>/<js>` |
| 学校索引 | `shared/assets/offline_repo/index/school_index.pb`（build_schools.py 生成） | `.adapter_private/index/school_index.pb`（**从左侧拷贝**） |

**比较口径是 LF 归一化后的 sha256，不是原始字节**：

```powershell
python scripts/check_adapters.py            # 全量体检 + 双落点核对
python scripts/check_adapters.py --strict   # 提示项也判失败（CI 用）
```

> ⚠️ 为什么不能逐字节比：主仓库工作副本是 CRLF、私有仓库是 LF，同一份文件会因行尾被误报成
> 不一致（历史案例：`DLUT/dlut.js` —— 493 行 CRLF 22681 B ↔ 493 行 LF 22188 B，LF 归一化后
> 内容完全相同）。私有仓库 `school_index.pb` 的 `version_id` 早已改成 LF 归一化内容哈希
> （commit `b9c49b7`），本脚本与它保持同一口径。

脚本还会一并报出：双落点缺失（only-public / only-private）、内容不一致、**行尾差异**
（原始字节不同但归一化后一致，INFO、不计入失败）、入口契约缺失、`node --check` 语法错误、
未被 bridge 分支保护的裸 `alert/confirm/prompt`、有网络请求却全文无 `.catch`、危险 API
（`eval` / `new Function` / `document.write` / `innerHTML=`）、以及未被内置索引引用的孤儿脚本。

> ⚠️ 历史教训：两处 `school_index.pb` 曾出现不同步（私有仓库侧滞后 5 天），导致热更新用户与离线用户看到的学校集合不一致。**每次 `build_schools.py` 之后必须立刻拷贝到私有仓库侧**，阶段四发布前再跑一次 `check_adapters.py` 核对。

### 4.5 重建离线 zip（仅发版前需要）

```powershell
python tools/rebuild_zip.py   # 将 offline_repo/ 打包为 composeResources/files/offline_schools.zip
```

- 离线 zip 只随 APK 发布生效；日常热更新**不需要**重建；
- 该脚本尾部有一段硬编码的一次性验证代码（检查特定学校），非本次目标学校的报错可忽略；
- 重建后建议装机验证 zip 版本标记触发重新解压（App 以 zip 内容哈希判断是否重解压）。

---

## 五、阶段三 · 本地自测

### 5.1 编译装机

```powershell
# 本机 JAVA_HOME 是坏的，每条 Gradle 命令前必须显式指向 JBR
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew :androidApp:assembleDebug
```

装机：`adb install -r androidApp\build\outputs\apk\debug\*.apk`（或 `installDebug`）。

### 5.2 App 内走查清单

1. 添加课表 → 选择学校：能按名称 / 拼音首字母搜到该学校；
2. 适配器列表：名称、描述、入口 URL 正确；多入口学校能看到全部入口；
3. WebView 打开入口，完成登录（WebVPN / 统一认证全流程走通）；
4. 点「执行导入」：弹出学期选择（若脚本提供）→ 执行 → 成功提示；
5. **核对导入结果**（对照教务官网课表逐项检查）：
   - [ ] 课程数量一致（无丢课、无重复）；
   - [ ] 周次正确（重点：单双周、跨段周次）；
   - [ ] 节次正确（重点：连堂合并）；
   - [ ] 地点 / 教师字段无串位；
6. 异常路径：未登录时点导入 → 应得到友好引导而非静默失败；取消选择 → 不产生半截数据。

### 5.3 典型失败对照表

| 现象 | 常见原因 | 排查动作 |
|---|---|---|
| 点「执行导入」完全无反应 | 脚本文件缺失（App 会 toast 路径）或脚本语法错误整体中断 | 核对 `repo/schools/resources/<folder>/<js>` 是否存在；Chrome `chrome://inspect` 看 WebView console |
| 提示「未找到导入入口」 | 没挂 `shangkeImportEntry`、入口名不在约定名单、或脚本顶层抛错没执行到挂载行 | 检查入口挂载与顶层异常；确认注入的是课表相关页面 |
| 导入 0 门课 | 登录态失效（fetch 302 到登录页）、接口改版、解析函数全量返回 null | 脚本里临时加 `showToast` 打印原始响应长度 / 首段内容 |
| 课程 / 周次错乱 | 周次或节次解析错误、连续节次未合并 | 拿一门具体课对照原始页面文本核对正则 |
| 搜不到学校 | `school_index.pb` 未重建，或私有仓库侧 pb 未同步 | 回阶段二 / 阶段四核对两处 pb 哈希一致 |

---

## 六、阶段四 · 私有仓库热更新发布

> 适配脚本与学校索引均可热更新，**不需要发版**。以下命令在 `.adapter_private/`（私有仓库 `schedule-adapter-private` 的本地工作副本，独立 git 仓库）内执行。

### 6.1 落点核对

- [ ] 本次改动的脚本已在 `.adapter_private/adapters/<CODE>/`，且与 offline_repo 侧**哈希一致**；
- [ ] 若 `school_index.pb` 有变：主仓库侧最新 pb 已拷贝到 `.adapter_private/index/school_index.pb`；
- [ ] 私有仓库内无密钥、无 App 源码、无构建产物（安全约定见 `.adapter_private/README.md`）。

### 6.2 重建并校验清单

```powershell
cd .adapter_private
python tools/build_index.py     # 重新生成 index.json（遍历 adapters/ 与 index/，逐文件 sha256）
python tools/verify_index.py    # 校验清单与文件逐一致；退出码非 0 禁止提交
```

> 铁律：**任何适配文件新增 / 修改 / 删除后必须重建 index.json**，否则 App 比对哈希不匹配会拒收该文件（提示「适配更新失败」）。

### 6.3 提交推送

```powershell
git add -A
git commit -m "adapter: update <学校代码>"
git push
```

（私有仓库目录只含适配产物，`add -A` 是其 README 约定做法；主仓库仍遵守「显式逐文件暂存」纪律，两不冲突。）

推送后 Worker **无需任何操作**：缓存最长 5 分钟自动过期，App 下次启动 `sync()` 即拉到新适配；失败静默回退内置资源，不影响既有功能。

---

## 七、阶段五 · 线上验收

### 7.1 网关自测（curl 三连）

```bash
BASE=https://adapter.shangke.asia

# 无密钥 → 403
curl -s -o /dev/null -w '%{http_code}\n' "$BASE/index.json"

# 错误密钥 → 403
curl -s -o /dev/null -w '%{http_code}\n' -H "X-App-Secret: wrong" "$BASE/index.json"

# 正确密钥 → 200，返回清单（file_count 应与本次改动一致）
curl -s -o /dev/null -w '%{http_code}\n' -H "X-App-Secret: $APP_SECRET" "$BASE/index.json"
```

`APP_SECRET` 存于主仓库根 `adapter_secrets.properties`（gitignore，构建期注入 App）——**严禁写入任何仓库 / 脚本 / 日志**。

### 7.2 真机验收

1. 推送后等待 ≥ 5 分钟（Worker 缓存过期）；
2. 真机杀掉 App 进程冷启动（触发 `sync()`），确认无「适配更新失败」提示；
3. 完整走一遍 5.2 清单（搜学校 → 选适配器 → 登录 → 导入 → 核对）。

**验收通过标准**：一名不了解内部实现的该校学生，拿到 App 后能在无人工指引下完成课表导入。

---

## 八、热更新 vs 发版边界

| 改动类型 | 热更新可交付 | 是否需要发版 |
|---|---|---|
| 修复 / 优化既有适配脚本 | ✅（阶段四即可，分钟级生效） | 否 |
| 新增学校（新脚本 + 索引注册） | ✅（`adapters/**` 与 `index/school_index.pb` 一并热更） | 否；但建议下个版本并入离线兜底包 |
| 更新离线兜底包（offline_schools.zip） | ❌ | 是（`rebuild_zip.py` + 发版） |
| 修改 App 原生逻辑（Bridge 协议、解析、UI） | ❌ | 是 |
| 修改 Worker 网关本身 | —（`wrangler deploy`，见 `adapter-worker/README.md`） | 否 |

发版操作严格按 `docs/agents/release-runbook.md` 执行；发版说明中涉及适配变更的，按 `CHANGELOG.md` 分类写入「适配」一节。

---

## 九、常见坑与故障排查（历史事故沉淀）

1. **域名子串误配**：裸子串兜底曾把 44 所学校配到无关适配器（`cupl`→CUP、`dlut`→DLU 等）。对策：兜底白名单外的组合一律不配；新配学校必须人工确认「域名确实属于该学校」。
2. **孤儿适配器**：文件在磁盘上但没有学校引用，改了也对用户无效（历史上最多 77 个）。对策：`build_schools.py` 的孤儿警告必须清零或显式接线。
3. **多 JS 目录取字典序第一个**：目录里第二个及以后的 JS 永远不会被自动选中。对策：需要非字典序首个的脚本时用 `SPECIAL_ADAPTERS` 显式指定。
4. **两处 `school_index.pb` 不同步**：已实际发生过（私有仓库侧滞后）。对策：`build_schools.py` 跑完立即拷贝，发布前哈希复核（见 4.4）。
5. **改了文件没重建 index.json**：App 哈希比对失败直接拒收。对策：私有仓库内任何改动后必跑 `build_index.py` + `verify_index.py`。
6. **扩大自启动入口名单**：给探测脚本加更多入口名会让自启动型适配器并发跑两遍（重复导入）。对策：只挂 `window.shangkeImportEntry`，不要动 App 侧探测逻辑。
7. **脚本被注入到非课表页即抛错**：顶层直接操作 DOM，在登录页就中断。对策：顶层只做函数定义与入口挂载，DOM 逻辑全部放进入口函数，入口内先做页面特征校验。
8. **fetch 丢登录态**：漏 `credentials: 'include'`，请求被 302 到登录页拿到错误内容。对策：统一封装 GET 函数并先做登录态校验（照抄 `ahu.js` 的 `ahuGetText` 模式）。
9. **周次未排序 / 连堂未合并**：同一课程显示成一堆碎块。对策：交付前用 5.2 清单第 5 项逐项核对。
10. **通用脚本被误当专用脚本改**：改了 `zhengfang/` 等平台目录脚本会影响全国大量学校。对策：改平台脚本必须多校抽样回归（至少 3 所不同规模 / 版本的学校）。

---

## 十、附录 · 关键文件索引

| 路径 | 职责 | 入主仓库 git |
|---|---|---|
| `shared/assets/offline_repo/schools/resources/<CODE>/<js>` | 内置适配脚本（离线兜底源，174 目录） | ✅ |
| `shared/assets/offline_repo/schools/timetable_schools.json` | 学校数据集（1778 条，id/name/type/url） | ✅ |
| `shared/assets/offline_repo/schools/ADAPTER_GUIDE.md` | 适配脚本开发指南（Bridge / TimetableParser API） | ✅ |
| `shared/assets/offline_repo/index/school_index.pb` | 内置学校索引（build_schools.py 产物） | ✅ |
| `shared/src/commonMain/composeResources/files/offline_schools.zip` | 离线资源包（rebuild_zip.py 产物，随 APK） | ✅ |
| `shared/src/commonMain/kotlin/.../web/WebBridgeProtocol.kt` | JS Bridge 协议与注入脚本（App 侧契约源头） | ✅ |
| `shared/src/commonMain/kotlin/.../web/WebViewScreen.kt` | 导入执行页（脚本读取与注入入口） | ✅ |
| `shared/src/commonMain/kotlin/.../tool/AdapterRemoteUpdater.kt` | 热更新同步器（清单比对 / sha256 / 原子写） | ✅ |
| `shared/src/commonMain/kotlin/.../tool/ResourceInitializerManager.kt` | 离线 zip 解压初始化 | ✅ |
| `adapter-worker/` | Cloudflare Worker 鉴权网关（部署见其 README） | ✅ |
| `.adapter_private/adapters/<CODE>/<js>` | 私有仓库工作副本：热更新脚本源 | ❌（独立私有仓库） |
| `.adapter_private/index/school_index.pb` | 私有仓库侧索引（OTA 用，须与主仓库侧同步） | ❌ |
| `.adapter_private/index.json` | 热更清单（逐文件 sha256） | ❌ |
| `.adapter_private/tools/build_index.py` / `verify_index.py` | 清单重建 / 校验 | ❌ |
| `.adapter_private/README.md` | 私有仓库维护与安全约定 | ❌ |
| `tools/build_schools.py` | 学校索引生成（含三注册路径与两道校验） | ❌（本地） |
| `tools/rebuild_zip.py` | 离线包重打包 | ❌（本地） |
| `adapter_secrets.properties` | 网关密钥（构建期注入 App） | ❌ **严禁入库** |

---

*本文所述流程中的命令与路径均与仓库现状（v3.66.5）逐项核验；适配体系改动（Bridge 协议、目录结构、工具脚本）时须同步更新本文。*
