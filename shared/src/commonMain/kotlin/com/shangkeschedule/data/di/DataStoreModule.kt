package com.shangkeschedule.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.shangkeschedule.data.model.schedule_style.ScheduleGridStyleProto
import com.shangkeschedule.data.repository.SCHEDULE_STYLE_DATASTORE_FILE_NAME
import com.shangkeschedule.data.repository.ScheduleStyleSerializer
import okio.FileSystem
import okio.Path
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

/**
 * DataStore 损坏兜底（P2-20，2026-10-06）。
 *
 * 四个 DataStore 此前**都没有** corruptionHandler：底层 `preferences_pb` / 样式 proto
 * 一旦损坏（写盘被中断、磁盘位翻转、降级/迁移残留），DataStore 会抛 `CorruptionException`，
 * 而该异常发生在**读取流内部**，调用方无从捕获 —— 表现为设置页/课表样式直接崩溃或永久读不出值。
 *
 * 统一改用 `ReplaceFileCorruptionHandler`：损坏时丢弃坏文件、回退到默认值继续跑。
 * 这是 DataStore 官方推荐的恢复语义；用户丢失的只是损坏的那一份本地偏好，而非整个 App 可用性。
 * 注：数据以 Room 为主库（课表/课程/笔记），此处仅承载偏好与样式，回退代价可控。
 */
private fun <T> replaceOnCorruption(default: () -> T) = ReplaceFileCorruptionHandler { default() }

@Module
@Suppress("unused")
class DataStoreModule {

    @Single
    fun provideScheduleStyleDataStore(
        fileSystem: FileSystem,
        @Named("FilesDir") filesDir: Path
    ): DataStore<ScheduleGridStyleProto> {
        return DataStoreFactory.create(
            storage = OkioStorage(
                fileSystem = fileSystem,
                serializer = ScheduleStyleSerializer,
                producePath = { filesDir / SCHEDULE_STYLE_DATASTORE_FILE_NAME }
            ),
            corruptionHandler = replaceOnCorruption { ScheduleGridStyleProto() }
        )
    }

    @Single
    @Named("SchoolHistory")
    fun provideSchoolHistoryDataStore(
        @Named("FilesDir") filesDir: Path
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.createWithPath(
            corruptionHandler = replaceOnCorruption { emptyPreferences() },
            produceFile = { filesDir / "datastore" / "school_history.preferences_pb" }
        )
    }

    @Single
    @Named("AppSettings")
    fun provideAppSettingsDataStore(
        @Named("FilesDir") filesDir: Path
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.createWithPath(
            corruptionHandler = replaceOnCorruption { emptyPreferences() },
            produceFile = { filesDir / "datastore" / "app_settings.preferences_pb" }
        )
    }

    @Single
    @Named("ApiConfig")
    fun provideApiConfigDataStore(
        @Named("FilesDir") filesDir: Path
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.createWithPath(
            corruptionHandler = replaceOnCorruption { emptyPreferences() },
            produceFile = { filesDir / "datastore" / "api_config.preferences_pb" }
        )
    }
}