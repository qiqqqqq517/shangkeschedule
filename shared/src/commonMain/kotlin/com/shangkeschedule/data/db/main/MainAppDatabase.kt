package com.shangkeschedule.data.db.main

import androidx.room3.AutoMigration
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.migration.AutoMigrationSpec
import com.shangkeschedule.data.di.AppStorage

@Database(
    entities = [
        CourseTable::class,
        Course::class,
        CourseWeek::class,
        TimeSlot::class,
        CourseTableConfig::class,
        TimeSlotScheme::class
    ],
    version = 9,
    autoMigrations = [
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5, spec = MainAppDatabase.RemoveAppSettingsSpec::class)
    ],
    exportSchema = true
)
@ConstructedBy(MainAppDatabaseConstructor::class)
abstract class MainAppDatabase : RoomDatabase() {

    @androidx.room3.DeleteTable(tableName = "app_settings")
    class RemoveAppSettingsSpec : AutoMigrationSpec

    abstract fun courseTableDao(): CourseTableDao
    abstract fun courseDao(): CourseDao
    abstract fun courseWeekDao(): CourseWeekDao
    abstract fun timeSlotDao(): TimeSlotDao
    abstract fun courseTableConfigDao(): CourseTableConfigDao
    abstract fun timeSlotSchemeDao(): TimeSlotSchemeDao

    companion object {
        fun getDatabase(appStorage: AppStorage): MainAppDatabase {
            return createMainDatabase(appStorage)
        }
    }
}

expect fun createMainDatabase(appStorage: AppStorage): MainAppDatabase

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object MainAppDatabaseConstructor : RoomDatabaseConstructor<MainAppDatabase>