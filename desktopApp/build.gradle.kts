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
            packageVersion = "2.12.0"
        }
    }
}