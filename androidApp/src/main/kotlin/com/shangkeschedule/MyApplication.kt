package com.shangkeschedule

import android.app.Application
import androidx.work.Configuration
import com.shangkeschedule.data.di.SharedModule
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.syncNightModeFromStoredThemeMode
import com.shangkeschedule.ui.glass.applyGlassNativeCrashFallback
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.plugin.module.dsl.startKoin

@Module(includes = [
    SharedModule::class
])
@ComponentScan("com.shangkeschedule")
class AppModule

@KoinApplication(modules = [AppModule::class])
class ScheduleAppConfig

class MyApplication : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()

        // 玻璃渲染自愈兜底：上次进程崩溃退出 ⇒ 本进程玻璃降级为色调面板（见 ui/glass/GlassPlatform.android.kt）
        applyGlassNativeCrashFallback(this)

        val koinApp = startKoin<ScheduleAppConfig> {
            androidLogger()
            androidContext(this@MyApplication)
            workManagerFactory()
        }

        // PF4（v3.69.0）：冷启动首帧由 `themes.xml` 的 windowBackground 绘制（@color/launch_background），
        // 而资源是按**系统** DayNight 分档的；用户在 App 内可选「强制深色 / 强制浅色」而系统仍是浅色，
        // 此时深色用户首帧会闪白。故按持久化的 themeMode 同步 AppCompatDelegate 夜间模式，
        // 使其与 Compose 侧 `ShangKeScheduleTheme` 的 darkTheme 口径一致（Theme.kt:41-45）。
        //
        // 在 Application 阶段读一次 DataStore：Compose 首帧本来也要读设置，这里先读反而让后续命中缓存，
        // 不额外增加磁盘 IO；读取失败则保持系统默认（安全降级）。
        // 读取与 AppCompatDelegate 的同步下沉到 shared 的 androidMain —— androidApp 模块
        // 没有 datastore 依赖，直接在这里读 DataStore 会编译失败。
        runCatching {
            syncNightModeFromStoredThemeMode(koinApp.koin.get<AppSettingsRepository>())
        }
    }
}