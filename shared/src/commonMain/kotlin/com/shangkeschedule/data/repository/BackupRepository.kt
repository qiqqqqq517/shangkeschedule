package com.shangkeschedule.data.repository

import com.shangkeschedule.data.db.main.CourseTable
import com.shangkeschedule.data.db.main.CourseTableDao
import com.shangkeschedule.data.model.AppSettingsModel
import com.shangkeschedule.data.model.AppThemeMode
import com.shangkeschedule.data.model.AppThemePreset
import com.shangkeschedule.data.model.AutoControlMode
import com.shangkeschedule.data.model.CourseImportExport
import com.shangkeschedule.data.model.CourseImportExport.AppSettingsBackupEnvelope
import com.shangkeschedule.data.model.CourseImportExport.AppSettingsBackupModel
import com.shangkeschedule.data.model.CourseImportExport.CourseTableImportModel
import com.shangkeschedule.data.model.CourseImportExport.ImportCourseJsonModel
import com.shangkeschedule.data.model.CourseImportExport.SingleTablePack
import com.shangkeschedule.data.model.CourseImportExport.TotalAppBackupEnvelope
import com.shangkeschedule.data.model.StartScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.getString
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.backup_err_corrupted
import shangkeschedule.shared.generated.resources.backup_err_empty
import shangkeschedule.shared.generated.resources.backup_err_version_too_new
import kotlin.time.Clock

/**
 * 模块化备份定义
 */
enum class BackupModule(val key: String) {
    COURSE("course"),
    STYLE("style"),
    APP_SETTINGS("app_settings")
}

/**
 * 各模块的物理隔离载体
 */
data class AppBackupPackage(
    val meta: BackupMeta,
    val payloadMap: Map<String, ByteArray>
)

@Serializable
data class BackupMeta(
    val backupTimestamp: Long,
    val appVersionCode: Int,
    val appVersionName: String,
    val modules: List<ModuleInfo>
)

@Serializable
data class ModuleInfo(
    val key: String,
    val schemaVersion: Int
)

/**
 * 备份与恢复的中央总仓库（KMP 共享层）
 * 职责：调度各业务模块的原子化备份与恢复，确保全软件数据的一致性与扩展性。
 */
@Single
class BackupRepository(
    @Named("AppVersionCode") private val appVersionCode: Int,
    @Named("AppVersionName") private val appVersionName: String,
    private val courseTableDao: CourseTableDao,
    private val courseTableRepository: CourseTableRepository,
    private val courseConversionRepository: CourseConversionRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val styleSettingsRepository: StyleSettingsRepository
) {

    /**
     * 构建全软件多模块统一内存备份包
     */
    suspend fun createFullSoftwareBackup(modules: List<BackupModule>): AppBackupPackage? = withContext(Dispatchers.IO) {
        try {
            val payloadMap = mutableMapOf<String, ByteArray>()
            val moduleInfos = mutableListOf<ModuleInfo>()

            modules.forEach { module ->
                when (module) {
                    BackupModule.COURSE -> {
                        exportAllCourseTablesCbor()?.let {
                            payloadMap[module.key] = it
                            moduleInfos.add(ModuleInfo(module.key, CourseImportExport.COURSE_SCHEMA_VERSION))
                        }
                    }
                    BackupModule.STYLE -> {
                        exportAppStyleBytes()?.let {
                            payloadMap[module.key] = it
                            moduleInfos.add(ModuleInfo(module.key, StyleSettingsRepository.STYLE_SCHEMA_VERSION))
                        }
                    }
                    BackupModule.APP_SETTINGS -> {
                        exportAppSettingsBytes()?.let {
                            payloadMap[module.key] = it
                            moduleInfos.add(ModuleInfo(module.key, APP_SETTINGS_SCHEMA_VERSION))
                        }
                    }
                }
            }

            if (payloadMap.isEmpty()) return@withContext null

            AppBackupPackage(
                meta = BackupMeta(
                    backupTimestamp = Clock.System.now().toEpochMilliseconds(),
                    appVersionCode = appVersionCode,
                    appVersionName = appVersionName,
                    modules = moduleInfos
                ),
                payloadMap = payloadMap
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 原子化分发恢复网关
     */
    suspend fun restoreFullSoftwareBackup(backupPackage: AppBackupPackage): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            backupPackage.meta.modules.forEach { info ->
                when (info.key) {
                    BackupModule.COURSE.key -> {
                        if (info.schemaVersion > CourseImportExport.COURSE_SCHEMA_VERSION) {
                            return@withContext Result.failure(IllegalStateException(getString(Res.string.backup_err_version_too_new)))
                        }
                    }
                    BackupModule.STYLE.key -> {
                        if (info.schemaVersion > StyleSettingsRepository.STYLE_SCHEMA_VERSION) {
                            return@withContext Result.failure(IllegalStateException(getString(Res.string.backup_err_version_too_new)))
                        }
                    }
                    BackupModule.APP_SETTINGS.key -> {
                        if (info.schemaVersion > APP_SETTINGS_SCHEMA_VERSION) {
                            return@withContext Result.failure(IllegalStateException(getString(Res.string.backup_err_version_too_new)))
                        }
                    }
                }
            }
            backupPackage.meta.modules.forEach { info ->
                val data = backupPackage.payloadMap[info.key] ?: return@forEach
                val result = when (info.key) {
                    BackupModule.COURSE.key -> restoreAllCourseTablesCbor(data)
                    BackupModule.STYLE.key -> restoreAppStyleBytes(data)
                    BackupModule.APP_SETTINGS.key -> restoreAppSettingsBytes(data)
                    else -> Result.success(Unit)
                }
                if (result.isFailure) return@withContext result
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 1. 课表核心业务通道

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun exportAllCourseTablesCbor(): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val allTablesFromDb = courseTableRepository.getAllCourseTables().first()
            if (allTablesFromDb.isEmpty()) return@withContext null
            val appSettings = appSettingsRepository.getAppSettingsOnce()

            val tablePacks = allTablesFromDb.mapNotNull { table ->
                val exportModel = courseConversionRepository.exportCourseTableToJson(table.id) ?: return@mapNotNull null
                SingleTablePack(table.id, table.name, table.createdAt, exportModel)
            }

            val envelope = TotalAppBackupEnvelope(
                backupTimestamp = Clock.System.now().toEpochMilliseconds(),
                appVersionCode = CourseImportExport.COURSE_SCHEMA_VERSION,
                currentCourseTableId = appSettings.currentCourseTableId,
                allTables = tablePacks
            )

            CourseImportExport.cbor.encodeToByteArray(TotalAppBackupEnvelope.serializer(), envelope)
        } catch (e: Exception) {
            null
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun restoreAllCourseTablesCbor(cborBytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (cborBytes.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException(getString(Res.string.backup_err_empty)))
            }

            val envelope = try {
                CourseImportExport.cbor.decodeFromByteArray(TotalAppBackupEnvelope.serializer(), cborBytes)
            } catch (_: Exception) {
                return@withContext Result.failure(IllegalStateException(getString(Res.string.backup_err_corrupted)))
            }

            if (envelope.appVersionCode > CourseImportExport.COURSE_SCHEMA_VERSION) {
                return@withContext Result.failure(IllegalStateException(getString(Res.string.backup_err_version_too_new)))
            }

            // 事务保护：先备份当前所有课表到内存，恢复失败时可回滚
            val currentTables = courseTableRepository.getAllCourseTables().first()
            val backupSnapshot = currentTables.mapNotNull { table ->
                val exportModel = courseConversionRepository.exportCourseTableToJson(table.id) ?: return@mapNotNull null
                Triple(table.id, table.name, table.createdAt) to exportModel
            }.toMap()
            val backupCurrentTableId = appSettingsRepository.getAppSettingsOnce().currentCourseTableId

            try {
                // 清空现有课表
                currentTables.forEach { courseTableDao.delete(it) }

                // 导入备份中的课表
                envelope.allTables.forEach { pack ->
                    courseTableDao.insert(CourseTable(pack.tableId, pack.tableName, pack.createdAt))
                    val importModel = CourseTableImportModel(
                        courses = pack.tableData.courses.map {
                            ImportCourseJsonModel(it.id, it.name, it.teacher, it.position, it.day, it.startSection, it.endSection, it.weeks, it.isCustomTime, it.customStartTime, it.customEndTime, it.color, it.remark)
                        },
                        timeSlots = pack.tableData.timeSlots,
                        config = pack.tableData.config
                    )
                    courseConversionRepository.importCourseTableFromJson(pack.tableId, importModel)
                }

                val backupTargetTableId = envelope.currentCourseTableId
                val finalTableId = if (envelope.allTables.any { it.tableId == backupTargetTableId }) {
                    backupTargetTableId
                } else {
                    envelope.allTables.firstOrNull()?.tableId ?: ""
                }

                val currentSettings = appSettingsRepository.getAppSettingsOnce()
                appSettingsRepository.insertOrUpdateAppSettings(currentSettings.copy(currentCourseTableId = finalTableId))

                Result.success(Unit)
            } catch (e: Exception) {
                // 回滚：恢复备份的课表。回滚本身也可能失败（例如失败源于同一个脏数据），
                // 因此包一层 try/catch，避免回滚异常覆盖原始异常并把用户留在半恢复状态。
                try {
                    backupSnapshot.forEach { (tableInfo, exportModel) ->
                        courseTableDao.insert(CourseTable(tableInfo.first, tableInfo.second, tableInfo.third))
                        courseConversionRepository.importCourseTableFromJson(tableInfo.first, CourseTableImportModel(
                            courses = exportModel.courses.map {
                                ImportCourseJsonModel(it.id, it.name, it.teacher, it.position, it.day, it.startSection, it.endSection, it.weeks, it.isCustomTime, it.customStartTime, it.customEndTime, it.color, it.remark)
                            },
                            timeSlots = exportModel.timeSlots,
                            config = exportModel.config
                        ))
                    }
                    val settings = appSettingsRepository.getAppSettingsOnce()
                    appSettingsRepository.insertOrUpdateAppSettings(settings.copy(currentCourseTableId = backupCurrentTableId))
                } catch (_: Exception) {
                    // 回滚失败：至少保留原始失败原因，避免把二次异常抛给上层
                }
                Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 2. 个性化样式核心业务通道

    /**
     * 导出样式独立通道
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun exportAppStyleBytes(): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val rawProtoBytes = styleSettingsRepository.exportRawStyleBytes()
            val envelope = StyleBackupEnvelope(
                backupTimestamp = Clock.System.now().toEpochMilliseconds(),
                appVersionCode = StyleSettingsRepository.STYLE_SCHEMA_VERSION,
                styleProtoBytes = rawProtoBytes
            )
            CourseImportExport.cbor.encodeToByteArray(StyleBackupEnvelope.serializer(), envelope)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 样式恢复独立通道
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun restoreAppStyleBytes(styleBytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (styleBytes.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException(getString(Res.string.backup_err_empty)))
            }

            val envelope = try {
                CourseImportExport.cbor.decodeFromByteArray(StyleBackupEnvelope.serializer(), styleBytes)
            } catch (_: Exception) {
                return@withContext Result.failure(IllegalStateException(getString(Res.string.backup_err_corrupted)))
            }

            if (envelope.appVersionCode > StyleSettingsRepository.STYLE_SCHEMA_VERSION) {
                return@withContext Result.failure(IllegalStateException(getString(Res.string.backup_err_version_too_new)))
            }

            // TODO: 样式版本迁移逻辑待实现（当前版本兼容，直接使用原始字节）
            val migratedProtoBytes = envelope.styleProtoBytes
            styleSettingsRepository.restoreRawStyleBytes(migratedProtoBytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 3. 应用设置备份通道

    companion object {
        const val APP_SETTINGS_SCHEMA_VERSION = 1
    }

    /**
     * 导出应用设置为 CBOR 字节
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun exportAppSettingsBytes(): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val settings = appSettingsRepository.getAppSettingsOnce()
            val backupModel = AppSettingsBackupModel(
                currentCourseTableId = settings.currentCourseTableId,
                reminderEnabled = settings.reminderEnabled,
                remindBeforeMinutes = settings.remindBeforeMinutes,
                skippedDates = settings.skippedDates,
                autoModeEnabled = settings.autoModeEnabled,
                autoControlMode = settings.autoControlMode.name,
                compatWearableSync = settings.compatWearableSync,
                showNonCurrentWeekCourses = settings.showNonCurrentWeekCourses,
                startScreen = settings.startScreen.name,
                themeMode = settings.themeMode.name,
                themePreset = settings.themePreset.name,
                useDynamicColor = settings.useDynamicColor,
                customLightPrimary = settings.customLightPrimary,
                customDarkPrimary = settings.customDarkPrimary,
                developerModeEnabled = settings.developerModeEnabled,
                coupleScheduleEnabled = settings.coupleScheduleEnabled,
                selfCourseColorIndex = settings.selfCourseColorIndex,
                crushCourseColorIndex = settings.crushCourseColorIndex,
                scheduleViewMode = settings.scheduleViewMode.name
            )
            val envelope = AppSettingsBackupEnvelope(
                backupTimestamp = Clock.System.now().toEpochMilliseconds(),
                appVersionCode = APP_SETTINGS_SCHEMA_VERSION,
                settings = backupModel
            )
            CourseImportExport.cbor.encodeToByteArray(AppSettingsBackupEnvelope.serializer(), envelope)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从 CBOR 字节恢复应用设置
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun restoreAppSettingsBytes(settingsBytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (settingsBytes.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException(getString(Res.string.backup_err_empty)))
            }

            val envelope = try {
                CourseImportExport.cbor.decodeFromByteArray(AppSettingsBackupEnvelope.serializer(), settingsBytes)
            } catch (_: Exception) {
                return@withContext Result.failure(IllegalStateException(getString(Res.string.backup_err_corrupted)))
            }

            if (envelope.appVersionCode > APP_SETTINGS_SCHEMA_VERSION) {
                return@withContext Result.failure(IllegalStateException(getString(Res.string.backup_err_version_too_new)))
            }

            val bm = envelope.settings
            val currentSettings = appSettingsRepository.getAppSettingsOnce()
            val restoredSettings = currentSettings.copy(
                currentCourseTableId = bm.currentCourseTableId,
                reminderEnabled = bm.reminderEnabled,
                remindBeforeMinutes = bm.remindBeforeMinutes,
                skippedDates = bm.skippedDates,
                autoModeEnabled = bm.autoModeEnabled,
                autoControlMode = runCatching { AutoControlMode.valueOf(bm.autoControlMode) }.getOrNull() ?: currentSettings.autoControlMode,
                compatWearableSync = bm.compatWearableSync,
                showNonCurrentWeekCourses = bm.showNonCurrentWeekCourses,
                startScreen = runCatching { StartScreen.valueOf(bm.startScreen) }.getOrNull() ?: currentSettings.startScreen,
                themeMode = runCatching { AppThemeMode.valueOf(bm.themeMode) }.getOrNull() ?: currentSettings.themeMode,
                themePreset = runCatching { AppThemePreset.valueOf(bm.themePreset) }.getOrNull() ?: currentSettings.themePreset,
                useDynamicColor = bm.useDynamicColor,
                customLightPrimary = bm.customLightPrimary,
                customDarkPrimary = bm.customDarkPrimary,
                developerModeEnabled = bm.developerModeEnabled,
                coupleScheduleEnabled = bm.coupleScheduleEnabled,
                selfCourseColorIndex = bm.selfCourseColorIndex,
                crushCourseColorIndex = bm.crushCourseColorIndex,
                scheduleViewMode = runCatching { com.shangkeschedule.ui.schedule.ScheduleViewMode.valueOf(bm.scheduleViewMode) }.getOrNull() ?: currentSettings.scheduleViewMode
            )
            appSettingsRepository.insertOrUpdateAppSettings(restoredSettings)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}