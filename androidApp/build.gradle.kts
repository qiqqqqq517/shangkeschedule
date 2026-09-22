import com.android.build.api.variant.FilterConfiguration
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.koin.compiler)
}

kotlin {
    jvmToolchain(21)
}

// 读取 release 签名配置（密码等敏感信息存于 keystore.properties，不硬编码进脚本）。
// CI 环境不提供 keystore.properties，改由 AGP 注入签名（-Pandroid.injected.signing.*）完成；
// 此时不创建本地 release 签名配置，避免读到 null 抛 "null cannot be cast to non-null type"。
val keystoreProperties = Properties().apply {
    val propsFile = file("keystore.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use { load(it) }
    }
}
val hasLocalKeystore = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "com.shangkeschedule"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.shangkeschedule"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 252
        versionName = "3.65.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasLocalKeystore) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasLocalKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    splits {
        abi {
            isEnable = true
            exclude("mips", "mips64", "armeabi", "riscv64", "x86")
            isUniversalApk = false
            include("armeabi-v7a", "arm64-v8a", "x86_64")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf("zh", "zh-rCN", "zh-rTW", "en")
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.animation)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.components.resources)
    implementation(libs.androidx.datastore.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.koin.annotations)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.compose.ui.tooling)
}

configurations.all {
    exclude(group = "io.opencensus", module = "opencensus-api")
    exclude(group = "io.opencensus", module = "opencensus-proto")
    exclude(group = "io.opentelemetry", module = "opentelemetry-api")
    exclude(group = "io.opentelemetry", module = "opentelemetry-context")
}

androidComponents {
    onVariants { variant ->
        val buildType = variant.buildType ?: ""
        val versionName = android.defaultConfig.versionName ?: ""

        variant.outputs.forEach { output ->
            val abiFilter = output.filters.find {
                it.filterType == FilterConfiguration.FilterType.ABI
            }?.identifier ?: "universal"

            // 动态设置输出的 APK 文件名
            output.outputFileName.set("shangke-v${versionName}-${abiFilter}-${buildType}.apk")
        }
    }
}

// ---------------------------------------------------------------------------
// 发布安全闸：release 构建缺少签名时直接失败
//
// 背景：signingConfigs 与 release.signingConfig 都被 `if (hasLocalKeystore)` 包住，
// 且没有 else / 没有 throw —— 忘记拷 keystore.properties 时 release 会「构建成功」并产出
// **未签名** APK；又因上方 outputFileName 覆盖了 AGP 默认的 "-unsigned" 后缀，
// 产物文件名与已签名包完全相同，一旦误发 Release，用户侧统一报「应用未安装 / 解析包错误」。
//
// 注：AGENTS.md 提到的 validateSigningRelease 在当前脚本中并不存在，此处补上等价关卡。
// ---------------------------------------------------------------------------
val releaseSigningInjected = providers.gradleProperty("android.injected.signing.store.file").isPresent

gradle.taskGraph.whenReady {
    val buildingRelease = allTasks.any {
        it.name == "assembleRelease" ||
            it.name == "packageRelease" ||
            it.name == "bundleRelease" ||
            it.name.startsWith("assembleRelease") ||
            it.name.startsWith("bundleRelease")
    }
    if (buildingRelease && !hasLocalKeystore && !releaseSigningInjected) {
        throw GradleException(
            "release 构建缺少签名，已阻止产出未签名包。\n" +
                "  本地构建：请确认 androidApp/keystore.properties 存在（含 storeFile/storePassword/keyAlias/keyPassword）。\n" +
                "  CI 构建：请通过 -Pandroid.injected.signing.store.file=... 等参数注入签名。"
        )
    }
}