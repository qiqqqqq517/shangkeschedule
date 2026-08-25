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
- 同一个课表可维护**多套作息时间**，例如「夏令时」「冬令时」。
- 每套方案可设置**生效日期范围**（月-日），支持跨年（如 10-01 ~ 次年 04-30）。
- 支持手动切换，也支持**按当天日期自动切换**。
- 新建方案会复制当前方案作为模板；默认方案不可删除。

### 💑 crush / 情侣课表
- 一键导入 TA 的课表，与本人课表**分开存储、分开管理**。
- 打开「情侣课表」后，周课表和今日课表可同时叠加展示两套课程。
- 可为本人课程和 crush 课程分别选择颜色，一眼区分。
- 支持一键清空 crush 课表，不影响本人数据。

### 🎓 更完整的课程信息
- 课程新增**学分、考核方式、实验课**字段。
- 编辑课程时可直接填写；从教务系统导入时，会从备注文本中自动识别 `学分 / Credit / Score / XF`、`考核方式 / Assessment / ExamType`、`实验课 / Lab / Experiment` 等常见中英文写法并回填到结构化字段。

### 🏫 离线教务适配仓库
- 内置 **147 个学校 / 155 个适配脚本**，新用户无需联网即可使用大部分学校适配。
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
