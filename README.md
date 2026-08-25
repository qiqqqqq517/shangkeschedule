# 上课课程表 · shangkeschedule

> 一款面向中国高校师生的开源课程表 App，基于 [拾光课程表（shiguangschedule）](https://github.com/XingHeYuZhuan/shiguangschedule) 二次开发。
> 在完整保留原项目功能的基础上，重点优化了**课表排版体验**，并新增多作息方案、情侣课表、课程属性识别、离线教务适配仓库等能力。

![License](https://img.shields.io/badge/license-Apache%202.0-blue)
![Android](https://img.shields.io/badge/Android-8.0%2B-32DE84)
![Kotlin](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF)
![Compose](https://img.shields.io/badge/Compose-Multiplatform-4285F4)

## 预览图

| 周课表页面，支持长按调整课程块位置或高度 | 课表页面个性化配置（深色模式演示） | 小组件选择 |
| :---: | :---: | :---: |
| ![周课表](/picture/Screenshot_1.png) | ![个性化配置](/picture/Screenshot_2.png) | ![小组件](/picture/Screenshot_3.png) |

## 为什么有这个项目

原项目功能已经很完整，但课表卡片在**窄块、双排课程、极矮课时**等场景下，文字容易被裁切或排版不自然。本版本从课表卡片渲染入手做了一轮专项优化，并在不破坏原架构的前提下，加入了几个日常高频使用的功能。

## 相对原版的主要改动

### ✨ 课表排版体验
- **字号自适应缩放**：课程块文字根据实际可用宽度动态缩放（约 72% ~ 115%），窄块不再裁切，宽块更清晰。
- **地点智能换行**：优先在 `@`、`-`、中英文括号等连接符处换行，避免 `A-302` 被拆成 `A-3 / 02`。
- **信息优先级重排**：按「名称 > 地点 > 教师」取舍；紧凑块自动隐藏教师，保证核心信息完整可读。

### 🕐 多作息方案（夏令时 / 冬令时）

很多学校会在夏季和冬季使用不同的上课时间。这个功能让**一张课表**可以同时保存多套作息，并按日期自动切换，无需每学期手动改时间。

**从哪里进入**

- 课表时间设置页新增「作息方案」选择器。
- 可直接切换方案、新建方案、删除方案、编辑方案生效日期。
- 打开「自动切换作息」后，系统会按当天日期匹配应生效的方案。

**每个方案能做什么**

- 手动切换：随时在「默认 / 夏令时 / 冬令时 / 自定义方案」之间切换。
- 自动切换：按「月-日」生效区间判断；如 `10-01 ~ 次年 04-30` 表示跨年冬季作息。
- 新建方案：自动复制当前方案的全部时间段作为模板，只需微调，不必重填。
- 删除方案：默认方案不可删除；删除非当前方案不影响其他方案。

**实现要点**

- `TimeSlot` 增加 `schemeId`，主键扩展为 `(节次, 课表, 方案)`。
- 新增 `TimeSlotScheme` 表保存每个方案的生效日期范围。
- `CourseTableConfig` 新增 `currentSchemeId` 和 `autoSwitchScheme`。
- 周课表、今日课表、新增/编辑课程、导出课程，都统一通过 `getActiveTimeSlotsByConfigFlow()` 获取「当前应生效」的作息。

### 💑 crush / 情侣课表

「crush 课表」用于把 TA 的课表导入进来，和自己的课表并存展示，特别适合情侣、室友或想对照看课表的场景。

**导入与删除**

- 导入入口：设置 → 课表导入/导出 → 一键导入 crush 课表。
- 支持两种来源：通过教务系统适配导入，或通过 JSON 文件导入。
- 支持「删除 crush 课表」，一次性清空所有 crush 课程。

**数据隔离**

- 课程表通过 `Course.isCrush` 标记隔离，crush 课程挂在当前课表下，但与本人课程互不影响。
- 所有普通课程查询都会过滤 `isCrush = 0`，crush 查询则只取 `isCrush = 1`。
- 普通 JSON 导出不会混入 crush 课程。
- crush 导入只更新课程数据，**不覆盖时间表**，避免影响你本人的作息设置。

**展示逻辑**

- 在设置中打开「情侣课表」后，周课表和今日课表会同时叠加两套课程。
- 可分别设置「本人课程颜色」和「crush 课程颜色」，默认为蓝色和粉色。
- 时间冲突时本人课程优先排左列，crush 课程排右列。
- 冲突检测新增「周次是否重叠」判断，只把真正同一周共同上课的课程视为冲突课程。

**编辑保护**

- crush 课程在课程详情中仅展示、不提供编辑按钮，防止误改 TA 的课表。

### 🎓 更完整的课程信息

课程不再只有名称、教师、地点，还支持记录**学分、考核方式、实验课**三个结构化字段。

**编辑与展示**

- 新增/编辑课程页新增：学分、考核方式、实验课开关。
- 课程详情底部弹窗会展示学分、考核方式，并在实验课课程上显示「实验课」标记。
- 未填写的字段不会显示，界面保持简洁。

**导入时自动识别**

从教务系统或其他 JSON 导入课程时，会先从备注文本中自动抽取结构化信息，支持中英文混合写法。例如：

```text
备注：学分 3.5，考核方式：考试，实验课
```

可识别为：

- 学分：`3.5`
- 考核方式：`考试`
- 实验课：`是`

支持的常见写法包括 `学分 / Credit / Score / XF`、`考核方式 / Assessment / ExamType`、`实验课 / Lab / Experiment` 等；原始备注仍会保留（最多 300 字）。

**导入 / 导出兼容性**

- `CourseImportExport` 的导入、导出模型都新增 `credit`、`assessmentMethod`、`isLab`。
- 旧版 JSON 没有这些字段时仍可正常导入，字段会回退为空值，不会破坏兼容性。
- 导出时会带上新字段；crush 课表导入同样会执行备注自动识别。

### 🏫 离线教务适配仓库
- 内置 **143 所高校与 5 类通用教务适配，共 155 个适配脚本**，新用户无需联网即可覆盖大部分常见教务系统。
- 提供 [`sync_warehouse.ps1`](./sync_warehouse.ps1)，可一键从适配仓库拉取最新脚本和编译好的学校索引。
- 离线资源初始化增加 `size + hash` 版本校验，适配脚本更新后自动重新解压，避免每次启动重复解压。

### ⚙️ 构建与工程化
- 新增阿里云 Maven 镜像，国内构建更稳定。
- 开启 `android.overridePathCheck`，项目放在中文路径下也能正常构建。
- Release 签名参数外置到 `keystore.properties`，不硬编码、不提交签名密钥。
- 应用重命名为「上课 / Attend Class」，包名重构为 `com.shangkeschedule`。

## 核心功能

### 主页面
- **今日课表**：快速查看当天课程。
- **周课表**：左右滑动切换周次，点击顶部周次可快速跳转；长按课程块可调整位置或高度。
- **设置**：集中管理课表、通知、备份、样式等配置。

### 课表配置
- 时间表与课表绑定，切换课表自动切换作息时间。
- 可独立设置每张课表的一周起始日（周一 / 周日）。

### 小组件
- 提供多种尺寸和样式的桌面小组件。
- 支持明日课程预告；深色模式下小组件也有对应外观。

### 课程导入与导出
- JSON 文件导入 / 导出，方便备份和迁移。
- 通用 ICS 文件导出，可导入多平台日历。
- 支持系统日历账号同步和 WebDAV 云备份。
- 教务系统导入，配合适配脚本一键导入课程和作息时间。

### 课程提醒
- 课程提醒 + 上课自动开启勿扰 / 静音。
- 获取全年节假日数据，避免节假日课程打扰。

### 个性化与多语言
- 自定义背景图片、格子高度 / 圆角 / 间距 / 透明度、课程块颜色与内容样式。
- 支持简体中文、繁体中文、英语。

## 构建运行

### 环境要求
- JDK 21（推荐使用 Android Studio 自带的 JBR）
- Android SDK（`compileSdk` 与项目版本见 `androidApp/build.gradle.kts`）
- Android Studio 或命令行 Gradle

### 命令行

```bash
# 安装 Debug 包到已连接设备
./gradlew :androidApp:installDebug

# 编译 Release APK
./gradlew :androidApp:assembleRelease
```

Windows 下也可以直接双击或在终端运行项目内增强脚本（请先按本机路径修改脚本中的 `JAVA_HOME` / `ANDROID_HOME`）：

```bat
run-android.bat
```

## 同步离线教务适配仓库

项目已经在本地内置了离线适配脚本。若想更新到最新适配列表：

```powershell
.\sync_warehouse.ps1            # 增量同步
.\sync_warehouse.ps1 -Clean     # 完全镜像，覆盖本地适配目录
```

脚本会自动下载适配脚本和编译好的 `school_index.pb`，同步完成即可生效。

## 项目结构

```text
├── androidApp    # Android 应用入口、通知、小组件
├── desktopApp    # Desktop 桌面端（开发中）
├── iosApp        # iOS 入口（开发中）
├── shared        # Compose Multiplatform 公共代码（UI、数据库、业务逻辑）
├── fastlane      # 商店元数据与发布配置
└── sync_warehouse.ps1
```

## 与原项目的关系

本项目是 [XingHeYuZhuan/shiguangschedule](https://github.com/XingHeYuZhuan/shiguangschedule) 的优化衍生版：

- 保留原项目的功能与架构，遵循 Apache License 2.0。
- 包名由 `com.xingheyuzhuan.shiguangschedule` 重构为 `com.shangkeschedule`。
- 上游适配仓库请参考 [XingHeYuZhuan/shiguang_warehouse](https://github.com/XingHeYuZhuan/shiguang_warehouse)。

## License

沿用原项目许可：[Apache License 2.0](./LICENSE)。

## 致谢

由衷感谢 [拾光课程表](https://github.com/XingHeYuZhuan/shiguangschedule) 作者及全体贡献者，本项目的绝大部分能力都建立在他们的开源工作之上。
