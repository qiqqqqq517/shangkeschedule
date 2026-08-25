package com.shangkeschedule.data.db.widget

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.shangkeschedule.data.di.AppStorage
import org.koin.mp.KoinPlatform

actual fun createWidgetDatabase(appStorage: AppStorage): WidgetDatabase {
    val context = KoinPlatform.getKoin().get<Context>()
    val dbPath = appStorage.getDatabasePath("widget_database")
    return Room.databaseBuilder<WidgetDatabase>(
        context = context,
        name = dbPath,
        factory = { WidgetDatabaseConstructor.initialize() }
    )
        .fallbackToDestructiveMigration(true)
        .setDriver(AndroidSQLiteDriver())
        .build()
}