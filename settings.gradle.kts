// 阿里云镜像开关：本地开发默认开启以加速下载；CI（GitHub Actions 海外运行器）需关闭。
// 原因：海外 IP 访问 maven.aliyun.com 会返回 502 Bad Gateway，而 Gradle 命中 5xx 时
// 不会回退到后续仓库，而是直接判定该构件「解析失败」，导致 KSP 等插件在 CI 上必然失败。
// 关闭方式：./gradlew ... -PuseMirror=false
pluginManagement {
    val useMirror: Boolean = (providers.gradleProperty("useMirror").orNull ?: "true").toBoolean()
    repositories {
        if (useMirror) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
            maven("https://maven.aliyun.com/repository/gradle-plugin")
        }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    val useMirror: Boolean = (providers.gradleProperty("useMirror").orNull ?: "true").toBoolean()
    repositories {
        // Haze 毛玻璃库：镜像同步滞后，直连 mavenCentral（内容过滤优先命中）
        mavenCentral {
            content {
                includeGroup("dev.chrisbanes.haze")
            }
        }
        if (useMirror) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
        }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "shangkeschedule"

include(":androidApp")
include(":shared")
include(":desktopApp")
