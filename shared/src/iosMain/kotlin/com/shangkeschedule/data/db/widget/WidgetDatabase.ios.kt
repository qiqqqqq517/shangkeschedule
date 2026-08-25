package com.shangkeschedule.data.db.widget

import androidx.room3.Room
import androidx.sqlite.driver.NativeSQLiteDriver
import com.shangkeschedule.data.di.AppStorage

actual fun createWidgetDatabase(appStorage: AppStorage): WidgetDatabase {
    val dbPath = appStorage.getDatabasePath("widget_database")
    return Room.databaseBuilder<WidgetDatabase>(
        name = dbPath,
        factory = { WidgetDatabaseConstructor.initialize() }
    )
        .fallbackToDestructiveMigration(true)
        .setDriver(NativeSQLiteDriver())
        .build()
}