# 拾光课程表 - 教务系统适配开发指南

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

1. 在 `schools.json` 中添加学校条目：
```json
{
  "name": "学校全称",
  "shortName": "简称",
  "code": "SCHOOL_CODE",
  "adapterType": "chaoxing",
  "loginUrl": "https://jwxt.example.edu.cn/login",
  "category": "BACHELOR_AND_ASSOCIATE"
}
```

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
