import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.koin.compiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.compose)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.compose.ui.tooling.preview)

    // 视觉回归预览宿主（claude_preview.kt）需要的直接依赖：
    // material3 / kotlinx-datetime 在 shared 内是间接依赖，桌面模块显式声明后才能被预览代码引用
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.datetime)
}


// 版本单源：desktopApp 的安装包版本从 androidApp/build.gradle.kts 的 versionName 派生，
// 不再手写副本（历史上曾停在 2.12.0 / 3.70.5，落后 androidApp 一个主版本；
// tools/publish_new_version.py 只回写 androidApp 的 versionCode/versionName 那一行，
// 派生之后 desktopApp 自动跟随，无需再手工同步）。
// providers.fileContents(...).asText 是配置缓存安全的读取方式：它会把该文件登记为构建
// 输入，androidApp 版本变化时配置缓存正确失效（裸 File.readText 不会登记输入）。
val appVersionName: String = providers.fileContents(
    layout.projectDirectory.file("../androidApp/build.gradle.kts")
).asText.map { text ->
    val raw = Regex("^\\s*versionName\\s*=\\s*\"([^\"]+)\"", RegexOption.MULTILINE)
        .find(text)?.groupValues?.get(1)
        ?: error("desktopApp: 无法从 androidApp/build.gradle.kts 解析 versionName")
    // Compose Desktop 只接受数字型 MAJOR.MINOR.BUILD（MINOR ≤ 255、BUILD ≤ 65535）；
    // 带后缀的版本号要到打包阶段才报错，这里提前给出可定位的信息。
    require(Regex("^\\d+\\.\\d+\\.\\d+$").matches(raw)) {
        "desktopApp: androidApp versionName \"$raw\" 不是数字型 MAJOR.MINOR.BUILD，桌面安装包无法承载"
    }
    raw
}.get()

compose.desktop {
    application {
        // 视觉回归预览宿主可通过 -PpreviewMainClass=... 临时切换入口，默认仍是正式 App
        mainClass = (project.findProperty("previewMainClass") as String?)
            ?: "com.shangkeschedule.MainKt"

        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "shangke"
            // 版本单源：取自上方 appVersionName，见文件顶部派生逻辑。
            packageVersion = appVersionName
        }
    }
}