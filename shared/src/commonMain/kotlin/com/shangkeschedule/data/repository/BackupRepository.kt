package com.shangkeschedule.data.repository

import androidx.room3.withWriteTransaction
import com.shangkeschedule.data.db.main.CourseTable
import com.shangkeschedule.data.db.main.CourseTableDao
import com.shangkeschedule.data.db.main.MainAppDatabase
import com.shangkeschedule.data.db.main.ScheduleEvent
import com.shangkeschedule.data.db.main.TodoItem
import com.shangkeschedule.data.model.AppThemeMode
import com.shangkeschedule.data.model.AppThemePreset
import com.shangkeschedule.data.model.AutoControlMode
import com.shangkeschedule.data.model.CourseImportExport
import com.shangkeschedule.data.model.CourseImportExport.AppSettingsBackupEnvelope
import com.shangkeschedule.data.model.CourseImportExport.ScheduleEventBackupModel
import com.shangkeschedule.data.model.CourseImportExport.TodoBackupModel
import com.shangkeschedule.data.model.CourseImportExport.UserDataBackupEnvelope
import com.shangkeschedule.data.model.NextCardMode
import com.shangkeschedule.data.model.RefreshRateMode
import com.shangkeschedule.ui.theme.MotionSpeed
import com.shangkeschedule.data.model.CourseImportExport.AppSettingsBackupModel
import com.shangkeschedule.data.model.CourseImportExport.CourseTableImportModel
import com.shangkeschedule.data.model.CourseImportExport.ImportCourseJsonModel
import com.shangkeschedule.data.model.CourseImportExport.SingleTablePack
import com.shangkeschedule.data.model.CourseImportExport.TotalAppBackupEnvelope
import com.shangkeschedule.data.model.StartScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.SYSTEM
import org.jetbrains.compose.resources.getString
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.backup_err_corrupted
import shangkeschedule.shared.generated.resources.backup_err_empty
import shangkeschedule.shared.generated.resources.backup_err_upload_failed
import shangkeschedule.shared.generated.resources.backup_err_version_too_new
import shangkeschedule.shared.generated.resources.error_webdav_unconfigured
import kotlin.time.Clock

/**
 * 模块化备份定义
 */
enum class BackupModule(val key: String) {
    COURSE("course"),
    STYLE("style"),
    APP_SETTINGS("app_settings"),

    /**
     * 全局用户数据（待办 + 日程）。
     *
     * 新增原因：`todo_items` / `schedule_events` 是 Room 实体，但此前不属于任何备份模块，
     * 全量备份/恢复（本地 zip 与 WebDAV 共用同一入口）完全不覆盖它们 ——
     * 用户换机或恢复备份后，全部待办与日程丢失且无任何提示。
     */
    USER_DATA("user_data")
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

private const val WEBDAV_BACKUP_DIR = "Backup"

/**
 * 备份与恢复的中央总仓库（KMP 共享层）
 * 职责：调度各业务模块的原子化备份与恢复，确保全软件数据的一致性与扩展性。
 */
@Single
class BackupRepository(
    @Named("AppVersionCode") private val appVersionCode: Int,
    @Named("AppVersionName") private val appVersionName: String,
    private val database: MainAppDatabase,
    private val courseTableDao: CourseTableDao,
    private val courseTableRepository: CourseTableRepository,
    private val courseConversionRepository: CourseConversionRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val styleSettingsRepository: StyleSettingsRepository,
    private val apiConfigRepository: ApiConfigRepository
) {
    private val webDavBackupMutex = Mutex()

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
                        val bytes = exportAllCourseTablesCbor()
                        if (bytes != null) {
                            payloadMap[module.key] = bytes
                            moduleInfos.add(ModuleInfo(module.key, CourseImportExport.COURSE_SCHEMA_VERSION))
                        } else if (courseTableRepository.getAllCourseTables().first().isNotEmpty()) {
                            // 有课表却导出为 null ⇒ 是异常而非「无数据」。
                            // 原实现静默跳过，导致备份「成功」但课表模块缺失，换机恢复才发现课表全空。
                            return@withContext null
                        }
                        // 确实没有任何课表时跳过该模块是正确语义
                    }
                    BackupModule.STYLE -> {
                        val bytes = exportAppStyleBytes() ?: return@withContext null
                        payloadMap[module.key] = bytes
                        moduleInfos.add(ModuleInfo(module.key, StyleSettingsRepository.STYLE_SCHEMA_VERSION))
                    }
                    BackupModule.APP_SETTINGS -> {
                        val bytes = exportAppSettingsBytes() ?: return@withContext null
                        payloadMap[module.key] = bytes
                        moduleInfos.add(ModuleInfo(module.key, APP_SETTINGS_SCHEMA_VERSION))
                    }
                    BackupModule.USER_DATA -> {
                        val bytes = exportUserDataBytes() ?: return@withContext null
                        payloadMap[module.key] = bytes
                        moduleInfos.add(ModuleInfo(module.key, CourseImportExport.USER_DATA_SCHEMA_VERSION))
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
     * 将全量应用数据上传到已配置的 WebDAV。
     *
     * 手动备份与后台自动同步共用此入口，保证两者生成的目录结构、元数据和模块文件完全一致。
     * 串行锁避免自动任务与用户手动备份同时写同一批临时文件。
     */
    suspend fun uploadFullBackupToWebDav(): Result<Unit> = webDavBackupMutex.withLock {
        withContext(Dispatchers.IO) {
            val client = apiConfigRepository.createWebDavClient()
                ?: return@withContext Result.failure(
                    IllegalStateException(getString(Res.string.error_webdav_unconfigured))
                )
            val backupPackage = createFullSoftwareBackup(BackupModule.entries)
                ?: run {
                    client.close()
                    return@withContext Result.failure(IllegalStateException(getString(Res.string.backup_err_empty)))
                }

            try {
                val tempDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY
                val metaPath = tempDir / "meta.json"
                FileSystem.SYSTEM.write(metaPath) {
                    writeUtf8(Json.encodeToString(BackupMeta.serializer(), backupPackage.meta))
                }
                if (!client.uploadFile(metaPath, "$WEBDAV_BACKUP_DIR/meta.json")) {
                    return@withContext Result.failure(IllegalStateException(getString(Res.string.backup_err_upload_failed)))
                }

                for ((key, bytes) in backupPackage.payloadMap) {
                    val modulePath = tempDir / "$key.cbor"
                    FileSystem.SYSTEM.write(modulePath) {
                        write(bytes)
                    }
                    if (!client.uploadFile(modulePath, "$WEBDAV_BACKUP_DIR/$key.cbor")) {
                        return@withContext Result.failure(IllegalStateException(getString(Res.string.backup_err_upload_failed)))
                    }
                }
                Result.success(Unit)
            } catch (_: Exception) {
                Result.failure(IllegalStateException(getString(Res.string.backup_err_upload_failed)))
            } finally {
                client.close()
            }
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
                    BackupModule.USER_DATA.key -> {
                        if (info.schemaVersion > CourseImportExport.USER_DATA_SCHEMA_VERSION) {
                            return@withContext Result.failure(IllegalStateException(getString(Res.string.backup_err_version_too_new)))
                        }
                    }
                }
            }
            val courseSnapshot = exportAllCourseTablesCbor()
            val styleSnapshot = exportAppStyleBytes()
            val appSettingsSnapshot = exportAppSettingsBytes()
            val userDataSnapshot = exportUserDataBytes()

            try {
                backupPackage.meta.modules.forEach { info ->
                    val data = backupPackage.payloadMap[info.key] ?: return@forEach
                    val result = when (info.key) {
                        BackupModule.COURSE.key -> restoreAllCourseTablesCbor(data)
                        BackupModule.STYLE.key -> restoreAppStyleBytes(data)
                        BackupModule.APP_SETTINGS.key -> restoreAppSettingsBytes(data)
                        BackupModule.USER_DATA.key -> restoreUserDataBytes(data)
                        else -> Result.success(Unit)
                    }
                    if (result.isFailure) {
                        throw result.exceptionOrNull()
                            ?: IllegalStateException("备份模块恢复失败：${info.key}")
                    }
                }
                Result.success(Unit)
            } catch (e: Throwable) {
                // 跨 Room/DataStore 无法由单一数据库事务覆盖，因此在恢复前建立三份快照；
                // 任一模块失败时按快照反向恢复，尽量回到恢复前的完整状态。
                runCatching { courseSnapshot?.let { restoreAllCourseTablesCbor(it).getOrThrow() } }
                runCatching { styleSnapshot?.let { restoreAppStyleBytes(it).getOrThrow() } }
                runCatching { appSettingsSnapshot?.let { restoreAppSettingsBytes(it).getOrThrow() } }
                runCatching { userDataSnapshot?.let { restoreUserDataBytes(it).getOrThrow() } }
                Result.failure(e)
            }
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
                SingleTablePack(
                    tableId = table.id,
                    tableName = table.name,
                    createdAt = table.createdAt,
                    tableData = exportModel,
                    isCouple = table.isCouple,
                    pairedCourseTableId = table.pairedCourseTableId
                )
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

            // 完整性校验：allTables 为空的备份（内容为空、或被截断但恰好仍可解码）会让下方
            // 「清空现有课表」删光用户所有课表却什么都不插入，且全程返回成功。
            if (envelope.allTables.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException(getString(Res.string.backup_err_corrupted))
                )
            }

            // 事务保护：先备份当前所有课表到内存，恢复失败时可回滚
            val currentTables = courseTableRepository.getAllCourseTables().first()
            val backupSnapshot = currentTables.mapNotNull { table ->
                val exportModel = courseConversionRepository.exportCourseTableToJson(table.id) ?: return@mapNotNull null
                Triple(table.id, table.name, table.createdAt) to exportModel
            }.toMap()
            val backupCurrentTableId = appSettingsRepository.getAppSettingsOnce().currentCourseTableId

            val backupTargetTableId = envelope.currentCourseTableId
            val finalTableId = if (envelope.allTables.any { it.tableId == backupTargetTableId }) {
                backupTargetTableId
            } else {
                envelope.allTables.firstOrNull()?.tableId ?: ""
            }

            try {
                // 真事务：清空现有课表 + 导入备份课表整体原子化。
                // 内部 importCourseTableFromJson 的写事务会通过 TransactionScope.withNestedTransaction
                // 以 savepoint 形式 join 本事务；任一步失败即整体回滚，不留半恢复状态。
                // 内存快照回滚保留为兜底防线（例如嵌套不可用的极端场景）。
                database.withWriteTransaction {
                    // 清空现有课表
                    currentTables.forEach { courseTableDao.delete(it) }

                    // 导入备份中的课表
                    envelope.allTables.forEach { pack ->
                        courseTableDao.insert(
                            CourseTable(
                                pack.tableId, pack.tableName, pack.createdAt,
                                pack.isCouple, pack.pairedCourseTableId
                            )
                        )
                        val importModel = CourseTableImportModel(
                            timeSlotSchemes = pack.tableData.timeSlotSchemes,
                            courses = pack.tableData.courses.map {
                                ImportCourseJsonModel(
                                    id = it.id,
                                    name = it.name,
                                    teacher = it.teacher,
                                    position = it.position,
                                    day = it.day,
                                    startSection = it.startSection,
                                    endSection = it.endSection,
                                    weeks = it.weeks,
                                    isCustomTime = it.isCustomTime,
                                    customStartTime = it.customStartTime,
                                    customEndTime = it.customEndTime,
                                    color = it.color,
                                    remark = it.remark,
                                    credit = it.credit,
                                    assessmentMethod = it.assessmentMethod,
                                    isLab = it.isLab
                                )
                            },
                            timeSlots = pack.tableData.timeSlots,
                            config = pack.tableData.config
                        )
                        courseConversionRepository.importCourseTableFromJson(pack.tableId, importModel, restoreMode = true)
                    }
                }

                // 恢复「当前课表」指向（DataStore 写入不在 Room 事务范围内，放在事务成功后执行）
                val currentSettings = appSettingsRepository.getAppSettingsOnce()
                appSettingsRepository.insertOrUpdateAppSettings(currentSettings.copy(currentCourseTableId = finalTableId))

                Result.success(Unit)
            } catch (e: Exception) {
                // 回滚：恢复备份的课表。回滚本身也可能失败（例如失败源于同一个脏数据），
                // 因此包一层 try/catch，避免回滚异常覆盖原始异常并把用户留在半恢复状态。
                try {
                    backupSnapshot.forEach { (tableInfo, exportModel) ->
                        // 回滚快照来自当前库，直接复用其情侣课表标记
                        val original = currentTables.firstOrNull { it.id == tableInfo.first }
                        courseTableDao.insert(
                            CourseTable(
                                tableInfo.first, tableInfo.second, tableInfo.third,
                                original?.isCouple ?: false, original?.pairedCourseTableId
                            )
                        )
                        courseConversionRepository.importCourseTableFromJson(tableInfo.first, CourseTableImportModel(
                            courses = exportModel.courses.map {
                                ImportCourseJsonModel(
                                    id = it.id,
                                    name = it.name,
                                    teacher = it.teacher,
                                    position = it.position,
                                    day = it.day,
                                    startSection = it.startSection,
                                    endSection = it.endSection,
                                    weeks = it.weeks,
                                    isCustomTime = it.isCustomTime,
                                    customStartTime = it.customStartTime,
                                    customEndTime = it.customEndTime,
                                    color = it.color,
                                    remark = it.remark,
                                    credit = it.credit,
                                    assessmentMethod = it.assessmentMethod,
                                    isLab = it.isLab
                                )
                            },
                            timeSlots = exportModel.timeSlots,
                            config = exportModel.config,
                            timeSlotSchemes = exportModel.timeSlotSchemes
                        ), restoreMode = true)
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
        /**
         * 应用设置备份规范版本。
         * v2：新增个人信息（昵称/学校/学院/专业/年级/签名/头像）与 5 项此前未备份的设置。
         */
        const val APP_SETTINGS_SCHEMA_VERSION = 2
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
                developerModeEnabled = settings.developerModeEnabled,
                coupleScheduleEnabled = settings.coupleScheduleEnabled,
                coupleShowTimeRanges = settings.coupleShowTimeRanges,
                selfCourseColorIndex = settings.selfCourseColorIndex,
                crushCourseColorIndex = settings.crushCourseColorIndex,
                scheduleViewMode = settings.scheduleViewMode.name,
                glassBlurRadiusDp = settings.glassBlurRadiusDp,
                glassRefractionEnabled = settings.glassRefractionEnabled,
                glassRefractionHeightDp = settings.glassRefractionHeightDp,
                glassRefractionAmountDp = settings.glassRefractionAmountDp,
                glassRefractionDispersion = settings.glassRefractionDispersion,
                glassRefractionDepthEffect = settings.glassRefractionDepthEffect,
                animationStyle = settings.animationStyle.name,
                disabledAnimationGroups = settings.disabledAnimationGroups.map { it.name }.toSet(),
                // v2：此前未纳入备份、换机恢复即丢的字段
                dynamicIslandEnabled = settings.dynamicIslandEnabled,
                reduceMotionEnabled = settings.reduceMotionEnabled,
                motionSpeed = settings.motionSpeed.name,
                nextCardMode = settings.nextCardMode.name,
                refreshRateMode = settings.refreshRateMode.name,
                profileNickname = settings.profileNickname,
                profileSchool = settings.profileSchool,
                profileCollege = settings.profileCollege,
                profileMajor = settings.profileMajor,
                profileGrade = settings.profileGrade,
                profileSignature = settings.profileSignature,
                profileAvatarPath = settings.profileAvatarPath
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

            // 指针有效性校验（E10）：备份里的 currentCourseTableId 可能在本机不存在 ——
            // 例如 WebDAV 部分恢复时 course 模块下载失败被跳过、而 app_settings 恢复成功。
            // 直接采用会让 App 指向不存在的课表 → 界面显示空课表，用户只看到「部分模块恢复」警告，
            // 无从定位原因。
            val validTableIds = courseTableRepository.getAllCourseTables().first().map { it.id }.toSet()
            val resolvedCurrentTableId = bm.currentCourseTableId.takeIf { it.isNotBlank() && it in validTableIds }
                ?: currentSettings.currentCourseTableId.takeIf { it in validTableIds }
                ?: validTableIds.firstOrNull().orEmpty()

            val restoredSettings = currentSettings.copy(
                currentCourseTableId = resolvedCurrentTableId,
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
                developerModeEnabled = bm.developerModeEnabled,
                coupleScheduleEnabled = bm.coupleScheduleEnabled,
                coupleShowTimeRanges = bm.coupleShowTimeRanges ?: currentSettings.coupleShowTimeRanges,
                selfCourseColorIndex = bm.selfCourseColorIndex,
                crushCourseColorIndex = bm.crushCourseColorIndex,
                scheduleViewMode = runCatching { com.shangkeschedule.ui.schedule.ScheduleViewMode.valueOf(bm.scheduleViewMode) }.getOrNull() ?: currentSettings.scheduleViewMode,
                // 旧版备份缺字段时 kotlinx 用默认值 4f 兜底，与 App 默认一致
                glassBlurRadiusDp = bm.glassBlurRadiusDp.coerceIn(0f, 24f),
                // 折射可空字段：旧备份解码为 null ⇒ 保留设备现值，不被默认值静默重置
                glassRefractionEnabled = bm.glassRefractionEnabled ?: currentSettings.glassRefractionEnabled,
                glassRefractionHeightDp = bm.glassRefractionHeightDp ?: currentSettings.glassRefractionHeightDp,
                glassRefractionAmountDp = bm.glassRefractionAmountDp ?: currentSettings.glassRefractionAmountDp,
                glassRefractionDispersion = bm.glassRefractionDispersion ?: currentSettings.glassRefractionDispersion,
                glassRefractionDepthEffect = bm.glassRefractionDepthEffect ?: currentSettings.glassRefractionDepthEffect,
                animationStyle = runCatching { com.shangkeschedule.ui.theme.AnimationStyle.valueOf(bm.animationStyle) }.getOrNull() ?: currentSettings.animationStyle,
                disabledAnimationGroups = bm.disabledAnimationGroups
                    .mapNotNull { runCatching { com.shangkeschedule.ui.theme.AnimationGroup.valueOf(it) }.getOrNull() }
                    .toSet(),
                // v2 可空字段：旧备份解码为 null ⇒ 保留设备现值（避免被 ""/false 默认值静默清空）
                dynamicIslandEnabled = bm.dynamicIslandEnabled ?: currentSettings.dynamicIslandEnabled,
                reduceMotionEnabled = bm.reduceMotionEnabled ?: currentSettings.reduceMotionEnabled,
                motionSpeed = bm.motionSpeed?.let { runCatching { MotionSpeed.valueOf(it) }.getOrNull() }
                    ?: currentSettings.motionSpeed,
                nextCardMode = bm.nextCardMode?.let { runCatching { NextCardMode.valueOf(it) }.getOrNull() }
                    ?: currentSettings.nextCardMode,
                refreshRateMode = bm.refreshRateMode?.let { runCatching { RefreshRateMode.valueOf(it) }.getOrNull() }
                    ?: currentSettings.refreshRateMode,
                profileNickname = bm.profileNickname ?: currentSettings.profileNickname,
                profileSchool = bm.profileSchool ?: currentSettings.profileSchool,
                profileCollege = bm.profileCollege ?: currentSettings.profileCollege,
                profileMajor = bm.profileMajor ?: currentSettings.profileMajor,
                profileGrade = bm.profileGrade ?: currentSettings.profileGrade,
                profileSignature = bm.profileSignature ?: currentSettings.profileSignature,
                profileAvatarPath = bm.profileAvatarPath ?: currentSettings.profileAvatarPath
            )
            appSettingsRepository.insertOrUpdateAppSettings(restoredSettings)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 4. 全局用户数据（待办 / 日程）备份通道

    /**
     * 导出全部待办与日程为 CBOR 字节。
     *
     * 这两张表是全局数据（不随课表切换），故与课表模块分开、单独作为一个备份模块。
     * 即使无任何数据也返回**有效信封**（而非 null），以保证「备份包含该模块」语义明确 ——
     * 用户可能确实没有待办，此时空列表就是正确快照。
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun exportUserDataBytes(): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val todos = database.todoDao().getAllTodosOnce().map {
                TodoBackupModel(
                    id = it.id, date = it.date, title = it.title, note = it.note,
                    time = it.time, done = it.done, sortOrder = it.sortOrder,
                    createdAt = it.createdAt, updatedAt = it.updatedAt
                )
            }
            val events = database.scheduleEventDao().getAllEventsOnce().map {
                ScheduleEventBackupModel(
                    id = it.id, date = it.date, title = it.title, category = it.category,
                    isAllDay = it.isAllDay, startTime = it.startTime, endTime = it.endTime,
                    location = it.location, note = it.note, done = it.done,
                    createdAt = it.createdAt, updatedAt = it.updatedAt
                )
            }
            val envelope = UserDataBackupEnvelope(
                backupTimestamp = Clock.System.now().toEpochMilliseconds(),
                appVersionCode = CourseImportExport.USER_DATA_SCHEMA_VERSION,
                todos = todos,
                events = events
            )
            CourseImportExport.cbor.encodeToByteArray(UserDataBackupEnvelope.serializer(), envelope)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从 CBOR 字节恢复待办与日程。
     *
     * 采用**快照替换**语义（同一事务内先清空再插入），与课表模块的恢复语义保持一致，
     * 避免旧数据残留导致「恢复到某个时间点」的结果不准确。
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun restoreUserDataBytes(bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (bytes.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException(getString(Res.string.backup_err_empty)))
            }

            val envelope = try {
                CourseImportExport.cbor.decodeFromByteArray(UserDataBackupEnvelope.serializer(), bytes)
            } catch (_: Exception) {
                return@withContext Result.failure(IllegalStateException(getString(Res.string.backup_err_corrupted)))
            }

            if (envelope.appVersionCode > CourseImportExport.USER_DATA_SCHEMA_VERSION) {
                return@withContext Result.failure(IllegalStateException(getString(Res.string.backup_err_version_too_new)))
            }

            database.withWriteTransaction {
                database.todoDao().deleteAllTodos()
                database.scheduleEventDao().deleteAllEvents()

                if (envelope.todos.isNotEmpty()) {
                    database.todoDao().insertAll(
                        envelope.todos.map {
                            TodoItem(
                                id = it.id, date = it.date, title = it.title, note = it.note,
                                time = it.time, done = it.done, sortOrder = it.sortOrder,
                                createdAt = it.createdAt, updatedAt = it.updatedAt
                            )
                        }
                    )
                }
                if (envelope.events.isNotEmpty()) {
                    database.scheduleEventDao().insertAll(
                        envelope.events.map {
                            ScheduleEvent(
                                id = it.id, date = it.date, title = it.title, category = it.category,
                                isAllDay = it.isAllDay, startTime = it.startTime, endTime = it.endTime,
                                location = it.location, note = it.note, done = it.done,
                                createdAt = it.createdAt, updatedAt = it.updatedAt
                            )
                        }
                    )
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
