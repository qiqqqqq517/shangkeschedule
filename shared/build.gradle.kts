import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.androidx.room3)
    alias(libs.plugins.wire)
    alias(libs.plugins.aboutLibraries)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    jvm()

    android {
        namespace = "com.shangkeschedule.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        // ------------------------------------------------------------------
        // android 与 jvm 目标的共享源集
        //
        // Android 与桌面 JVM 两端都能用 JDK 的 java.util.zip 等 JVM API，此前 ZipUtils
        // 的 actual 实现被逐字节复制了两份（androidMain + jvmMain）。这里建一个同时被
        // 两端 dependsOn 的中间源集，只留一份实现。
        //
        // 关键约束：只把 androidMain / jvmMain 接到这里，**不**接入 native/apple/ios 层级
        // —— iOS 用的是 Kotlin/Native，没有 java.util.zip，仍由 iosMain 自己的 actual 提供。
        // ------------------------------------------------------------------
        val jvmCommonMain = create("jvmCommonMain") {
            dependsOn(commonMain.get())
        }
        androidMain.get().dependsOn(jvmCommonMain)
        jvmMain.get().dependsOn(jvmCommonMain)

        commonMain {
            dependencies {
                // Compose Multiplatform 核心 UI 库
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.compose.material3.adaptive)
                implementation(libs.compose.material3.adaptive.navigation.suite)
                implementation(libs.compose.ui)
                implementation(libs.compose.animation)
                implementation(libs.compose.components.resources)
                implementation(libs.compose.ui.tooling.preview)

                // Haze 毛玻璃 backdrop blur（悬浮底栏 / 玻璃质感面板）
                implementation(libs.haze)

                // Lifecycle & Navigation3 导航体系
                implementation(libs.androidx.lifecycle.viewmodel.compose)
                implementation(libs.androidx.lifecycle.runtime.compose)
                implementation(libs.androidx.navigation3.ui)
                implementation(libs.androidx.lifecycle.viewmodel.navigation3)

                // UI 补充库 (Coil & 开源许可)
                implementation(libs.coil.compose)
                implementation(libs.aboutlibraries.compose.m3)

                // Koin 依赖注入
                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                implementation(libs.koin.compose.viewmodel)
                implementation(libs.koin.compose.navigation3)

                // Serialization & 工具库
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.serialization.cbor)
                implementation(libs.kotlinx.datetime)
                implementation(libs.okio)

                // 二维码生成（课表分享串 → 二维码；MIT，纯 Kotlin KMP，无传递依赖）
                implementation(libs.qrcode.kotlin)

                // Ktor 核心网络库
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.client.auth)
                implementation(libs.koin.annotations)

                // Room 3.0 & DataStore 存储
                implementation(libs.androidx.room3.runtime)
                implementation(libs.androidx.datastore.preferences)
                implementation(libs.androidx.datastore.core)

                // Wire Protobuf 运行时
                implementation(libs.wire.runtime)
            }
        }

        androidMain.dependencies {
            implementation(libs.androidx.sqlite.framework)
            // Ktor 引擎按平台选择（统一在 HttpClientFactory 里使用无引擎重载，具体引擎由这里决定）：
            // Android 用 OkHttp——它是 Ktor 官方推荐的 Android 引擎，HTTP/2、连接池、系统代理/
            // TLS/DNS 全部走平台原生栈；CIO 是纯 Kotlin 实现，在 Android 上不享受这些集成。
            implementation(libs.ktor.client.okhttp)
        }

        jvmMain.dependencies {
            implementation(libs.androidx.sqlite.bundled)
            // 桌面端用 CIO：无需额外原生依赖，且桌面（含 macOS/Windows/Linux）行为一致。
            implementation(libs.ktor.client.cio)
        }

        iosMain.dependencies {
            implementation(libs.androidx.sqlite.framework)
            implementation(libs.ktor.client.darwin)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.ui.tooling)
    add("kspAndroid", libs.androidx.room3.compiler)
    add("kspJvm", libs.androidx.room3.compiler)
    add("kspIosArm64", libs.androidx.room3.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room3.compiler)
}

// 导出第三方依赖许可信息
aboutLibraries {
    export {
        outputPath = file("src/commonMain/composeResources/files/aboutlibraries.json")
        prettyPrint = true
    }
    library {
        duplicationMode = com.mikepenz.aboutlibraries.plugin.DuplicateMode.MERGE
    }
}

// Room 3.0 插件配置 schema 导出路径
room3 {
    schemaDirectory("$projectDir/schemas")
}

// Wire 编译配置
wire {
    sourcePath {
        srcDir("src/commonMain/proto")
    }

    kotlin {
        escapeKotlinKeywords = true
        enumMode = "enum_class"
        rpcRole = "none"
    }
}

// ---------------------------------------------------------------------------
// 打包离线资源 Task（单流容器）
//
// 容器格式必须与读取端 `shared/src/commonMain/.../tool/OfflineRepoArchive.kt` 逐字节对应：
//   zip（唯一入口 repo.skr，DEFLATE level 9）内为顺序流：
//     "SKR1" | u32LE 文件数 | 重复{ u32LE 路径字节数, 路径(UTF-8), u32LE 数据字节数, 数据 }
//
// 为什么把 197 个文件合成「一条 deflate 流」而不是逐文件压缩：适配脚本之间有大量公共
// 模板代码，合并后压缩器可在整个仓库范围内复用字典，实测 932 KB(逐文件) → ~723 KB(-22%)。
// 固定条目时间戳 + 按路径排序，保证产物可复现（内容不变则字节不变）。
// ---------------------------------------------------------------------------
val offlineRepoRoot = layout.projectDirectory.dir("assets/offline_repo").asFile
val offlineRepoArchive = layout.projectDirectory
    .file("src/commonMain/composeResources/files/offline_schools.zip").asFile

val packSchoolsZip = tasks.register("packSchoolsZip") {
    group = "build"
    description = "将离线适配资源打包为单流压缩容器（composeResources/files/offline_schools.zip）。"

    inputs.dir(offlineRepoRoot).withPropertyName("offlineRepo")
    outputs.file(offlineRepoArchive)

    // 任务动作内只使用下面捕获的普通 File 对象与自身 inputs/outputs（配置缓存要求）
    val repoRoot = offlineRepoRoot
    val archive = offlineRepoArchive

    doLast {
        // 与旧实现保持完全一致的排除规则：文档 / 模板不进正式包
        fun isExcluded(relativePath: String): Boolean =
            relativePath.endsWith(".md") || relativePath.endsWith("schools_template.json")

        fun writeLeInt(out: OutputStream, value: Int) {
            out.write(value and 0xFF)
            out.write((value ushr 8) and 0xFF)
            out.write((value ushr 16) and 0xFF)
            out.write((value ushr 24) and 0xFF)
        }

        val entries = repoRoot.walkTopDown()
            .filter { it.isFile }
            .map { it to it.relativeTo(repoRoot).invariantSeparatorsPath }
            .filter { (_, relativePath) -> !isExcluded(relativePath) }
            .sortedBy { (_, relativePath) -> relativePath }
            .toList()

        archive.parentFile?.mkdirs()
        var rawBytes = 0L
        ZipOutputStream(BufferedOutputStream(FileOutputStream(archive))).use { zipOut ->
            zipOut.setLevel(9)
            val entry = ZipEntry("repo.skr")
            // 2000-01-01T00:00:00Z：固定时间戳，避免每次构建都因时间戳不同而被判定为变更
            entry.time = 946684800000L
            zipOut.putNextEntry(entry)

            zipOut.write(byteArrayOf(0x53, 0x4B, 0x52, 0x31)) // "SKR1"
            writeLeInt(zipOut, entries.size)
            for ((file, relativePath) in entries) {
                val pathBytes = relativePath.toByteArray(Charsets.UTF_8)
                val data = file.readBytes()
                writeLeInt(zipOut, pathBytes.size)
                zipOut.write(pathBytes)
                writeLeInt(zipOut, data.size)
                zipOut.write(data)
                rawBytes += data.size
            }
            zipOut.closeEntry()
        }

        logger.lifecycle(
            "离线适配包：${entries.size} 个文件 / 解包 $rawBytes 字节 → 容器 ${archive.length()} 字节"
        )
    }
}

// 绑定生成 Task 至 Compose Resources 编译生命周期
val exportLibraryDefinitions = tasks.named("exportLibraryDefinitions")

tasks.matching {
    it.name.startsWith("generateComposeResClass") ||
            it.name.startsWith("copyNonXmlValueResources") ||
            it.name.startsWith("prepareComposeResources")
}.configureEach {
    dependsOn(packSchoolsZip)
    dependsOn(exportLibraryDefinitions)
}

// =====================================================================
// 远程适配更新：构建期从 git 忽略的配置文件注入密钥（不写入公开源码）
// 配置文件：仓库根目录 adapter_secrets.properties（已 .gitignore）
//   adapter.workerUrl=<Worker 域名>
//   adapter.appSecret=<APP_SECRET>
// 文件缺失时生成空值，远程更新功能自动关闭，不影响编译与其它功能。
// =====================================================================
val adapterSecretsFile = rootProject.file("adapter_secrets.properties")
val adapterSecrets = Properties().apply {
    if (adapterSecretsFile.exists()) {
        adapterSecretsFile.inputStream().use { load(it) }
    }
}
val adapterWorkerUrlValue = adapterSecrets.getProperty("adapter.workerUrl").orEmpty().trim()
val adapterAppSecretValue = adapterSecrets.getProperty("adapter.appSecret").orEmpty().trim()
val adapterSecretsOutputDir = layout.buildDirectory.dir("generated/adapterRemoteSecrets/kotlin")

val generateAdapterRemoteSecrets = tasks.register("generateAdapterRemoteSecrets") {
    group = "build"
    description = "生成远程适配更新所需常量（密钥取自 git 忽略的 adapter_secrets.properties）。"

    inputs.property("workerBaseUrl", adapterWorkerUrlValue)
    inputs.property("appSecret", adapterAppSecretValue)
    outputs.dir(adapterSecretsOutputDir)

    doLast {
        // 任务动作只读取自身的 inputs / outputs，不引用构建脚本对象（配置缓存要求）
        val workerBaseUrl = inputs.properties.getValue("workerBaseUrl").toString()
        val appSecret = inputs.properties.getValue("appSecret").toString()
        val packageDir = outputs.files.singleFile.resolve("com/shangkeschedule/tool")
        packageDir.mkdirs()

        fun literal(value: String): String {
            val sb = StringBuilder("\"")
            for (ch in value) {
                when (ch) {
                    '\\' -> {
                        sb.append('\\')
                        sb.append('\\')
                    }
                    '"' -> {
                        sb.append('\\')
                        sb.append('"')
                    }
                    '$' -> {
                        sb.append('\\')
                        sb.append('$')
                    }
                    else -> sb.append(ch)
                }
            }
            return sb.append('"').toString()
        }

        packageDir.resolve("AdapterRemoteSecrets.kt").writeText(
            buildString {
                appendLine("package com.shangkeschedule.tool")
                appendLine()
                appendLine("// 由 Gradle 任务 generateAdapterRemoteSecrets 自动生成，请勿手动修改。")
                appendLine("internal object AdapterRemoteSecrets {")
                appendLine("    const val WORKER_BASE_URL: String = " + literal(workerBaseUrl))
                appendLine("    const val APP_SECRET: String = " + literal(appSecret))
                appendLine("}")
            },
            Charsets.UTF_8
        )
    }
}

kotlin.sourceSets.named("commonMain") {
    kotlin.srcDir(adapterSecretsOutputDir)
}

tasks.matching { it.name.startsWith("compileKotlin") || it.name.startsWith("ksp") }.configureEach {
    dependsOn(generateAdapterRemoteSecrets)
}

// 单测需要定位仓库根（离线资源包与 assets/offline_repo 在模块外），由 Gradle 注入系统属性，
// 避免依赖测试进程的工作目录（AGP host 测试的工作目录不保证是模块目录）。
val repositoryRootPath = rootProject.projectDir.absolutePath
tasks.withType<Test>().configureEach {
    systemProperty("shangke.repoRoot", repositoryRootPath)
}