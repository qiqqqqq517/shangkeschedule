# 上课 - 教务系统适配开发指南

## 架构概述

本应用采用 **WebView + JS Bridge** 架构实现教务系统适配：

1. **学校索引**：`school_index.pb`（Protobuf 格式），包含学校列表和适配器配置
2. **适配器资源**：每个学校/教务系统一个目录，包含 HTML/JS 资源
3. **JS Bridge**：JS 端通过 Bridge 调用原生功能（登录提示、课程导入等）
4. **通用工具库**：`_common/timetable_parser_utils.js`，所有适配器可复用

## 支持的教务系统类型

| 类型 | 通用适配器目录 | 状态 |
|------|--------------|------|
| 超星 | `chaoxing_jiaowu/` | ✅ 已完成 |
| 正方 | `zhengfang/` | ✅ 基础版（API抓取） |
| URP | `urp/` | ✅ 基础版（HTML表格解析） |
| 青果 | `kingosoft/` | ✅ 基础版（HTML表格解析） |
| 强智 | `qiangzhi/` | ✅ 基础版（需手动核对节次） |
| 金智(Wisedu) | `wisedu/` | ✅ 基础版（HTML表格+JSON） |
| 南软 | `south_soft/` | ✅ 基础版（HTML表格解析） |

> 注：基础版适配器需根据实际学校页面进行测试和调整。通用工具库 `_common/timetable_parser_utils.js` 可复用。

## 添加新学校步骤

### 方式一：使用已有通用适配器（推荐）

如果学校使用的是已支持的教务系统类型：

1. 在 `timetable_schools.json` 中添加学校条目：

   > P2-40（2026-10-07）更正：此处原写 `schools.json` 并给出 `shortName`/`adapterType`/`loginUrl`/`category`
   > 四个字段 —— 该文件名**从未存在**，字段名也与实际不符。照原文操作必然失败。
   > 实际数据文件为 `shared/assets/offline_repo/schools/timetable_schools.json`，字段如下：

```json
{
  "id": "pku",
  "name": "北京大学",
  "type": "urp",
  "url": "https://elective.pku.edu.cn/"
}
```

   - `id`：学校唯一标识（小写、作目录名与索引键）
   - `name`：学校全称
   - `type`：教务系统类型（对应 `resources/<类型>/` 下的适配脚本，如 `urp`、`zhengfang_new`、`chaoxing`）
   - `url`：教务系统入口地址

2. 在 `school_index.pb` 中注册该学校（需更新 Protobuf 索引）

### 方式二：创建专用适配器

如果学校教务系统有特殊逻辑：

1. 创建目录 `resources/SCHOOL_CODE/`
2. 编写 `SCHOOL_CODE.js`，参考现有适配器
3. 使用通用工具库减少重复代码：
```javascript
// 引入通用工具（WebView 环境下全局可用）
const parser = TimetableParser;

// 解析课程
const courses = rawData.map(item => parser.normalizeCourse(item, {
    name: 'kcmc',
    teacher: 'tmc',
    position: 'croommc',
    day: 'xingqi',
    sections: 'djc',
    weeks: 'zcstr'
})).filter(c => c !== null);

// 合并连续节次
const merged = parser.mergeConsecutiveCourses(courses);

// 调用 Bridge 导入
Bridge.saveImportedCourses(JSON.stringify(parser.buildBridgeCourses(merged)));
```

## JS Bridge 协议

### 可用 Action

| Action | 用途 | Payload |
|--------|------|---------|
| `showToast` | 显示提示 | `{ message: string }` |
| `showAlert` | 显示弹窗 | `{ title, message }` |
| `showPrompt` | 输入框 | `{ title, hint }` |
| `showSingleSelection` | 单选 | `{ title, options[] }` |
| `saveImportedCourses` | 导入课程 | `{ courses: CourseJson[] }` |
| `saveCourseConfig` | 保存课表配置 | `{ semesterStartDate, ... }` |
| `savePresetTimeSlots` | 保存作息 | `{ timeSlots[] }` |
| `notifyTaskCompletion` | 任务完成 | `{ success, message }` |
| `deliverAdapterGrades` | **钩子回传**成绩扫描结果 | `{ dataJsonString }`（Base64(JSON)，见下节） |
| `deliverAdapterEmptyClassrooms` | **钩子回传**空教室扫描结果 | 同上 |
| `deliverAdapterStudy` | **钩子回传**学业情况扫描结果 | 同上 |

> 后三个动作**不需要适配脚本手写**：声明钩子后由 App 注入的调用脚本负责拼装与发送（见「能力钩子」）。

### 课程 JSON 格式

```json
{
  "name": "高等数学",
  "teacher": "张三",
  "position": "教学楼A101",
  "day": 1,
  "startSection": 1,
  "endSection": 2,
  "weeks": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16],
  "color": null,
  "remark": ""
}
```

## 能力钩子（成绩 / 空教室 / 学业情况）

课表导入之外，App 还有三个**抓取用途**：成绩与绩点、空教室查询、学业情况。
它们过去只跑 App 内置的通用脚本（按表头猜列），**学校层无法参与**。

适配脚本可以通过在 `window` 上声明同名函数来接管其中任意一个 —— 这就是「钩子」。
声明是可选的：不声明就自动回落通用脚本（学业情况除外，见下）。

| 钩子名 | 用途 | 返回值 |
|---|---|---|
| `window.shangkeScanGrades` | 识别成绩 | `Array<GradeItem>` 或 Promise |
| `window.shangkeScanEmptyClassrooms` | 读取空教室 | `Array<RoomItem>` 或 Promise |
| `window.shangkeScanStudy` | 读取培养方案学分要求 | `{ requirements: Requirement[] }` 或 Promise |

**统一规则**：

1. **可以返回 Promise** —— 钩子里直接 `fetch` 本校接口是最推荐的写法（不受页面是否已渲染影响），
   结果由 App 自动经桥接回传，脚本**不需要**自己调 `postMessage`；
2. **不声明就回落**：`shangkeScanGrades` / `shangkeScanEmptyClassrooms` 缺失、抛错、或返回空数组时，
   App 自动改用内置通用脚本（按表头解析）；
3. **`shangkeScanStudy` 没有通用回落**：培养方案格式各校千差万别，硬猜出来的要求学分比没有更糟
   （用户会照着错的要求规划选课），因此缺失时 App 明确提示「本校暂未适配」；
4. **不能引入顶层副作用**：三个用途也会注入本文件，若顶层无条件启动课表导入，
   用户一打开成绩页就会弹出导入对话框（见下「自启动守卫」）。

### `shangkeScanGrades` → `Array<GradeItem>`

```json
[
  {
    "courseName": "大学物理B（一）",
    "credit": 3,
    "scoreText": "90",
    "semester": "2025-2026-2",
    "category": "必修",
    "gradePoint": 4.0
  }
]
```

| 字段 | 类型 | 约定 |
|---|---|---|
| `courseName` | string | 必填，空则该项被丢弃 |
| `credit` | number \| null | 学分；拿不到填 `null`，**不要填 0**（0 会拉低学分统计） |
| `scoreText` | string | 必填，空则该项被丢弃。数字成绩优先（便于算平均分 / 绩点），等级制课程填「优秀 / 良好 / 及格」 |
| `semester` | string \| null | **本校真实学期**（如 `2025-2026-1`）。通用脚本拿不到学期，一律落到「未标注学期」；钩子能给就必须给 |
| `category` | string \| null | 课程性质（必修 / 选修 / 通识教育选修…），学业情况页按它分类统计学分 |
| `gradePoint` | number \| null | **本校绩点**（v4.75.0）。见下方硬约束 |

> ⚠️ **`gradePoint` 必须回传，且必须是学校自己算出的那个数**（正方 V9 成绩接口的 `jd`，实测样例
> `"4.00"`）。各校的绩点档位、是否含重修、等级制折算规则都不同，App 内置的 4.0 / 5.0 换算表
> 不可能对上；此前不回传该字段，绩点在抓取时被整列丢弃，用户看到的「加权绩点」与学校对不上，
> 且无从判断差在哪。**取值无法判定时填 `null`（App 会回落到按分数换算），不得填 `0`**
> —— `0` 会被当作「挂科绩点」参与加权，反而污染汇总。超出 `0–5` 的值同样按 `null` 处理。
> 回传后页面会标注「绩点口径：教务 N 门 + 本机换算 M 门」，让用户知道哪些数字能拿去对账。

> ⚠️ **学期务必用显示值而非接口的学期编码**。正方教务 V9 的 `xqm` 是内部编码
> （实测 `xqm=3` = 第 **1** 学期、`xqm=12` = 第 2 学期），直接回传编码会把「第 1 学期」
> 写成「第 3 学期」，且用户极难发现。取接口返回的 `xnmmc` + `xqmmc` 拼接。

### `shangkeScanEmptyClassrooms` → `Array<RoomItem>`

```json
[
  {
    "room": "18-C610生科微格",
    "campus": "啬园校区",
    "building": "",
    "capacity": 40,
    "freeSlots": "多媒体微格教室"
  }
]
```

| 字段 | 类型 | 约定 |
|---|---|---|
| `room` | string | 必填，教室名，空则该项被丢弃 |
| `campus` | string | 校区 |
| `building` | string | 楼栋；**「无楼号」这类占位文本要归一成空串**，否则会拼进展示串 |
| `capacity` | number \| null | 座位数 |
| `freeSlots` | string | 可用时段 / 场地类别等补充说明 |

> 空教室是即时信息，**不落库**：结果只在弹窗里展示、支持一键复制。

### `shangkeScanStudy` → `{ requirements: Requirement[], courses?: CourseItem[] }`

```json
{
  "requirements": [
    { "category": "通识教育课程平台/必修", "requiredCredits": 41, "requiredCourses": 12 }
  ],
  "courses": [
    { "courseName": "高等数学A", "category": "学科基础课程平台/必修", "credit": 5, "suggestedTerm": "2025-2026-1" }
  ]
}
```

| 字段 | 类型 | 约定 |
|---|---|---|
| `category` | string | 类别 key，**必须与本机成绩的 `category` 对得上**，否则学分归不进该类 |
| `requiredCredits` | number | 培养方案要求的学分，`> 0` 才生效 |
| `requiredCourses` | number \| null | 该类别**应修门数**（v4.75.0）。正方学业情况页子行末尾的「共（N）门 通过（M）门」取 N；认不出填 `null`。**「通过（M）门」不得回传**——已修门数一律由本机成绩表现算 |
| `courses` | array \| 可选 | **培养方案课程清单**（v4.75.0，可选）：学校页面能列出培养方案的课时回传，学业情况页据此算出「已修 / 未修」。抓不到就**留空**，页面会引导用户手填或粘贴导入 |

`courses[]` 字段：

| 字段 | 类型 | 约定 |
|---|---|---|
| `courseName` | string | 必填，空白项被丢弃 |
| `category` | string \| null | 课程类别，须与成绩的 `category` 对得上 |
| `credit` | number \| null | 该课程学分；未知填 `null` |
| `suggestedTerm` | string \| null | 建议修读学期；未知填 `null` |

四条硬约束：

1. **只回传「要求」，绝不回传「已获学分」**。已获学分一律由 App 按本机成绩表现算
   （`GradeRepository.computeStudyProgress`）。学校页面的「获得学分」与本机成绩表的统计口径
   必然不一致（是否含重修、是否含未出分课程），两份数据一起用必然打架。
2. **只回传叶子类别，不要回传平台 / 汇总行**。培养方案页通常是「平台 → 必修 | 选修」两级，
   若平台行与子行都回传，同一个平台会被算两遍
   （实测南通大学：11 条全传得 **310** 学分，只传叶子才是真实的 **172**）。
   注意 `li.querySelectorAll()` 返回的是**全部后代**而非直接子级，
   用「递归下行 + 总遍历」的实现会重复访问同一行（实测会报出双份），
   正确做法是**单趟遍历所有行**，逐行判断「它是否还有后代带要求学分」。
3. **类别名各校写法不同，不要靠固定关键词识别**：南通大学写
   `通识教育课程平台要求学分:47.0` + 子行 `必修课程要求学分:41.0`；
   江苏科技大学直接写 `通识教育基础课程-必修要求学分:61.0`（**没有「课程平台」字样**）。
   按「课程平台」写正则会在一整类学校上产出 0 条。
   建议：类别名取行首原文，仅当名称是无区分度的通用名
   （`必修课程`/`选修课程`/`任选课程`/`限选课程`）时才用平台名做前缀消歧 ——
   南通大学四个平台各有一条 `必修课程`，不加前缀会合并成一个数字
   （41 + 11 + 69 + 37），完全失去意义。
4. **不要事后归一类别名**：App 侧按类别名**精确匹配**成绩的 `category`
   统计已获学分，改名会让已获学分归零。用页面上怎么写就怎么传。

App 侧写入是**合并而非覆盖**：学校培养方案里出现的类别以学校为准，
其余保留用户已手工填写的设置。

### 自启动守卫（三个抓取用途的必需项）

钩子要求本文件在**成绩页 / 空教室页 / 学业情况页**也被注入 —— 这些都不是课表页。
因此**顶层无条件启动导入的脚本必须加页面判定**，否则用户一打开成绩页就会弹出
「课表导入」对话框，并因当前页没有课表而失败：

```js
function isTimetablePage() {
    const path = window.location.pathname;
    if (/\/kbcx\/xskbcx/i.test(path) || /\/kbdy\/bjkbdy/i.test(path)) return true;
    const action = document.querySelector("#ajaxForm")?.getAttribute("action") || "";
    return /xskbcx_cxXskbcxIndex|bjkbdy_cxBjkbdyIndex/i.test(action);
}

if (isTimetablePage()) runImport();
```

> 只影响「页面加载时自动跑」这一条路径；用户点「执行导入」的显式路径行为不变。

### 参考实现

`resources/NTU/ntu.js`（南通大学 · 正方教务 V9）是本标准的参考实现，三个钩子齐全，
**接口地址与字段均经真实登录会话实测**（非按教务系统惯例推测）。写同平台脚本时直接参照它，
避免重复踩坑。

## 通用工具库 API

### `parser.extractText(htmlStr)`
从 HTML 中提取纯文本。

### `parser.cleanTeacherName(name)`
清理教师名，去除括号、职称等。

### `parser.parseWeeks(weekStr)`
解析周次，支持 `"1-16周"`、`"1,3,5"`、`"单周"`、`"1-16(双)"` 等格式。

### `parser.parseSections(sectionStr)`
解析节次，返回 `{ start, end }`。

### `parser.parseDay(dayStr)`
解析星期，支持数字、中文（周一）、英文（Mon）。

### `parser.mergeConsecutiveCourses(courses)`
合并连续节次的同一课程。

### `parser.normalizeCourse(raw, fieldMap)`
标准化课程数据，自动验证必填字段。

### `parser.buildBridgeCourses(courses)`
转换为 Bridge 导入格式。

## 注意事项

1. **不要硬编码学校名称**：使用配置文件
2. **课程颜色留空**：由应用自动分配
3. **周次数组必须排序**：升序排列
4. **教师名要清理**：去除括号和职称
5. **合并连续节次**：避免同一课程显示为多个块
6. **异常处理**：登录失败、数据为空时要有友好提示
