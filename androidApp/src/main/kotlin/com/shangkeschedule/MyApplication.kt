package com.shangkeschedule

import android.app.Application
import androidx.work.Configuration
import com.shangkeschedule.data.di.SharedModule
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

        startKoin<ScheduleAppConfig> {
            androidLogger()
            androidContext(this@MyApplication)
            workManagerFactory()
        }
    }
}