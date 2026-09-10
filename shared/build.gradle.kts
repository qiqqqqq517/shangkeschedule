import java.util.Properties
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

                // Dynamic Color 主题生成 (MaterialKolor)
                implementation(libs.material.kolor)

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
            implementation(libs.ktor.client.cio)
        }

        jvmMain.dependencies {
            implementation(libs.androidx.sqlite.bundled)
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

// 打包离线资源 Task
val packSchoolsZip = tasks.register<Zip>("packSchoolsZip") {
    group = "build"
    description = "将离线适配资源打包为 composeResources ZIP 资源文件。"

    from(layout.projectDirectory.dir("assets/offline_repo"))
    destinationDirectory.set(layout.projectDirectory.dir("src/commonMain/composeResources/files"))
    archiveFileName.set("offline_schools.zip")
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