package com.shangkeschedule

import android.app.Application
import androidx.work.Configuration
import com.shangkeschedule.data.di.SharedModule
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

        startKoin<ScheduleAppConfig> {
            androidLogger()
            androidContext(this@MyApplication)
            workManagerFactory()
        }
    }
}