# -*- coding: utf-8 -*-
"""【已废弃 · 请勿执行】离线资源包的打包入口已迁移到 Gradle 任务。

## 为什么废弃

真正的打包器是 Gradle 任务 `:shared:packSchoolsZip`
（`shared/build.gradle.kts` 约 196–264 行），产物格式为：

    zip 内唯一条目 `repo.skr`（DEFLATE level 9），内容是顺序流：
    "SKR1" | u32LE 文件数 | 重复{ u32LE 路径字节数, 路径(UTF-8), u32LE 数据字节数, 数据 }

读取端 `OfflineRepoArchive` 有硬校验：`require(zipFileSystem.exists("repo.skr"))`
与 `require(magic.contentEquals(MAGIC))`（"SKR1"）。

本脚本（旧版）做的是「把 offline_repo 目录逐文件写进普通 zip」，产出的包
**App 端解不开**：没有 `repo.skr` 条目 → 魔数校验失败 → 抛异常。而调用方
`ResourceInitializerManager.initializeOfflineRepo` 用 `runCatching` 吞异常，
于是**静默不解压**、内置适配资源全失（升级兜底失效），且从表面看不出原因。

它还把 `shared/src/commonMain/composeResources/files/offline_schools.zip`
原地覆盖且无备份，出错后无法回滚。

## 正确做法

    .\\gradlew :shared:packSchoolsZip

该任务已内置可复现性保障（`sortedBy { relativePath }` + 固定时间戳 946684800000
+ level 9），且被 `:shared` 的预构建流程依赖，每次构建自动重生成 ——
**无需也不应手工运行任何 Python 打包脚本**。
"""
import sys

MESSAGE = """已废弃：请改用 Gradle 任务重新打包。

    .\\gradlew :shared:packSchoolsZip

原因：本脚本产出的是「逐文件 zip」，而 App 端要求 zip 内唯一条目为
`repo.skr`（"SKR1" 魔数的顺序流）。直接运行会覆盖掉可用的产物且 App 端
解不开（异常又被 runCatching 吞掉 → 内置适配资源静默失效）。
详见 shared/build.gradle.kts 的 packSchoolsZip 与 OfflineRepoArchive 的魔数校验。
"""

if "--help" in sys.argv or "-h" in sys.argv:
    print(MESSAGE)
    sys.exit(0)

sys.exit(MESSAGE)