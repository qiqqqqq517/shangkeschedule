# 仓库结构与构建产物

## 命名与放置

| 对象 | 约定 |
| --- | --- |
| 产品名称 | 窗口显示「上课 · ShangKeSchedule」，桌面分发名 `ShangKeSchedule`；Linux 系统包标识使用小写 `shangkeschedule` |
| 仓库与技术命名 | 仓库 slug `shangkeschedule`，Kotlin 包前缀 `com.shangkeschedule` |
| 模块 | `shared` 放跨平台业务与 UI；`androidApp`、`desktopApp`、`iosApp` 只放对应入口与平台设施 |
| Kotlin 文件 | 类、对象与入口文件采用 PascalCase；声明包与目录对应，按现有业务模块归位 |
| 维护脚本 | 新增可复用脚本放 `scripts/`，相关测试放 `scripts/tests/`；保留 `tools/` 中既有发版入口，不继续扩充临时脚本白名单 |
| 文档 | 项目说明放 `docs/`，协作细则放 `docs/agents/`；根目录保留入口文档 |
| 本地产物 | 编译输出放模块 `build/`；报告、截图、临时脚本放 `build_qa/`，不提交 |

Android applicationId、Kotlin 包前缀、iOS 标识及数据库名称属于兼容契约，不随显示名称调整。三套主题的历史枚举名也保留，避免已有设置失效。已有设计包路径保留，避免破坏引用。

## 源集边界

- `shared/src/commonMain/`：跨平台实现；`androidMain`、`jvmMain`、`iosMain`：平台实现。
- `shared/src/jvmCommonMain/`：Android/JVM 共用的 JDK 实现，不接入 iOS。
- 自定义 Android/JVM 中间源集前显式应用默认层级模板，确保两个 iOS 目标仍包含 `iosMain`。
- `shared/src/commonTest/`：公共测试；`androidHostTest`：Android 宿主与业务回归；`jvmTest`：桌面数据库、备份等 JVM 集成回归。两类宿主测试互不替代，以实际 XML 报告为准。
- `desktopApp/src/main/`：正式应用入口；`desktopApp/src/preview/`：主题截图与色彩、玻璃诊断工具，只有预览任务编译它。

已有明确包声明的宿主测试按包路径放置；旧的默认包测试暂保留名称，避免既有定向测试命令失效。新增测试按被测模块放置。

```powershell
# 三端编译
.\gradlew.bat :shared:compileKotlinJvm :desktopApp:compileKotlin :androidApp:assembleDebug
# 按需验证不同平台的测试
.\gradlew.bat :shared:testAndroidHostTest :shared:jvmTest :androidApp:testDebugUnitTest
# 单独编译预览，或运行指定诊断（运行时会生成本地报告/截图）
.\gradlew.bat :desktopApp:compilePreviewKotlin
.\gradlew.bat :desktopApp:runPreview '-PpreviewMainClass=com.shangkeschedule.preview.ContrastProbeKt'
```

预览入口为 `BookPreviewKt`（书卷）、`ClearPreviewKt`（通透）、`SoftPreviewKt`（柔绘），诊断入口为 `ContrastProbeKt`、`GlassProbeKt`、`PaletteProbeKt`，包名均为 `com.shangkeschedule.preview`。历史 `:desktopApp:run -PpreviewMainClass=com.shangkeschedule.*` 入口保留别名；正式打包始终使用 `com.shangkeschedule.MainKt`。

## 资源与产物

| 内容 | 输入 / 任务 | 输出 |
| --- | --- | --- |
| 手写 Compose 资源 | `shared/src/commonMain/composeResources/` | 由汇总任务复制，不在源码目录生成文件 |
| 离线适配容器 | `shared/assets/offline_repo/` → `:shared:packSchoolsZip` | `shared/build/generated/offlineResources/files/offline_schools.zip` |
| 开源许可 JSON | `:shared:exportLibraryDefinitions` | `shared/build/generated/licenseResources/files/aboutlibraries.json`，紧凑编码 |
| Compose 资源汇总 | `:shared:assembleCommonResources` | `shared/build/generated/composeResources/commonMain/` |
| Android APK | `:androidApp:assembleDebug` / `assembleRelease` | `androidApp/build/outputs/apk/<类型>/shangke-v<版本>-<ABI>-<类型>.apk` |
| 桌面模块 JAR | `:desktopApp:jar` | `desktopApp/build/libs/shangkeschedule-desktop-<版本>.jar`，仅应用入口，运行需要依赖 |
| 桌面安装包 | 当前主机支持的 Compose Desktop 打包任务 | `desktopApp/build/compose/binaries/`，产品名 `ShangKeSchedule` |

Compose 资源的应用内路径仍为 `files/offline_schools.zip` / `files/aboutlibraries.json`。汇总任务排除旧源码目录内残留的同名生成文件，通过 Provider 连接生成与消费任务，支持增量构建与 `clean` 后重建。

离线容器仅包含运行时数据，排除文档、模板与学校构建源数据；路径排序、固定 ZIP 本地时间，保证相同输入不因机器时区变化产生不同字节。桌面 JAR 固定条目顺序并移除文件时间戳。

Compose 自定义资源目录遵循[官方配置接口](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-setup.html#custom-resource-directories)。默认目录被替换，因此汇总任务必须同时包含手写资源与生成资源。

自定义中间源集遵循[官方层级模板配置](https://kotlinlang.org/docs/multiplatform/multiplatform-hierarchy.html#creating-additional-source-sets)。iOS 编译与装机需在 macOS/Xcode 环境另行验证。
