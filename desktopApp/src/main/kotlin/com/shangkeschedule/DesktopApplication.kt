package com.shangkeschedule

import com.shangkeschedule.data.di.SharedModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

/** 正式桌面入口的依赖根，与预览工具的生命周期分开。 */
@Module(includes = [SharedModule::class])
@ComponentScan("com.shangkeschedule")
class DesktopModule {
    @Single
    @Named("AppVersionCode")
    fun versionCode(): Int = DesktopBuildInfo.VERSION_CODE

    @Single
    @Named("AppVersionName")
    fun versionName(): String = DesktopBuildInfo.VERSION_NAME
}

@KoinApplication(modules = [DesktopModule::class])
class DesktopAppConfig
