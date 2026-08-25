package com.shangkeschedule.data.db.main

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.shangkeschedule.data.di.AppStorage

actual fun createMainDatabase(appStorage: AppStorage): MainAppDatabase {
    val dbPath = appStorage.getDatabasePath("main_app_database")
    return Room.databaseBuilder<MainAppDatabase>(
        name = dbPath,
        factory = { MainAppDatabaseConstructor.initialize() }
    )
        .addMigrations(*ALL_MIGRATIONS)
        .setDriver(BundledSQLiteDriver())
        .build()
}