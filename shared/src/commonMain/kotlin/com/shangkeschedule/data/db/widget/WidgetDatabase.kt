package com.shangkeschedule.data.db.widget

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import com.shangkeschedule.data.di.AppStorage

@Database(
    entities = [WidgetCourse::class, WidgetAppSettings::class],
    // v3 → v4：WidgetAppSettings 新增 snapshotVersion（快照版本戳）。
    // 该库是纯派生缓存且启用 destructive migration，清库后的重建正是该字段要自愈的场景，
    // 故不提供迁移 —— 见 WidgetAppSettings.snapshotVersion 的 KDoc。
    version = 4,
    exportSchema = false
)
@ConstructedBy(WidgetDatabaseConstructor::class)
abstract class WidgetDatabase : RoomDatabase() {

    abstract fun widgetCourseDao(): WidgetCourseDao
    abstract fun widgetAppSettingsDao(): WidgetAppSettingsDao

    companion object {
        fun getDatabase(appStorage: AppStorage): WidgetDatabase {
            return createWidgetDatabase(appStorage)
        }
    }
}

expect fun createWidgetDatabase(appStorage: AppStorage): WidgetDatabase

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object WidgetDatabaseConstructor : RoomDatabaseConstructor<WidgetDatabase>