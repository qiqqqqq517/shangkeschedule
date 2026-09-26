package com.shangkeschedule.ui.settings.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.api.webdav.WebDavConfig
import com.shangkeschedule.data.repository.ApiConfigRepository
import com.shangkeschedule.data.repository.AppBackupPackage
import com.shangkeschedule.data.repository.BackupMeta
import com.shangkeschedule.data.repository.BackupModule
import com.shangkeschedule.data.repository.BackupRepository
import com.shangkeschedule.tool.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import okio.openZip
import okio.use
import org.jetbrains.compose.resources.getString
import org.koin.core.annotation.KoinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.backup_err_connect_failed
import shangkeschedule.shared.generated.resources.backup_err_corrupted
import shangkeschedule.shared.generated.resources.backup_err_empty
import shangkeschedule.shared.generated.resources.backup_err_local_export_failed
import shangkeschedule.shared.generated.resources.backup_err_restore_failed_prefix
import shangkeschedule.shared.generated.resources.backup_err_upload_failed
import shangkeschedule.shared.generated.resources.backup_warn_restore_partial
import shangkeschedule.shared.generated.resources.error_op_failed
import shangkeschedule.shared.generated.resources.error_webdav_unconfigured
import kotlin.random.Random

/**
 * 备份与恢复界面的 UI 状态定义
 *
 * @property baseUrl WebDAV 服务器地址
 * @property username WebDAV 账号用户名
 * @property rootPath 备份文件存储的根目录路径，默认为 "ShangKeSchedule"
 * @property hasSavedPassword 是否已保存密码
 * @property isTesting 是否正在执行 WebDAV 连接测试
 * @property isBusy 是否正在执行备份或恢复等耗时异步任务
 * @property testResult 连接或操作的结果状态
 */
data class BackupUiState(
    val baseUrl: String = "",
    val username: String = "",
    val rootPath: String = "ShangKeSchedule",
    val hasSavedPassword: Boolean = false,
    val autoSyncEnabled: Boolean = false,
    val isTesting: Boolean = false,
    val isBusy: Boolean = false,
    val testResult: TestResult = TestResult.Idle
)

/**
 * 备份与恢复相关异步操作的结果密封接口
 */
sealed interface TestResult {
    data object Idle : TestResult
    data object Success : TestResult
    /** 操作完成但有部分缺失（如云端个别模块下载失败被跳过）：P1-15 静默降级显性化 */
    data class PartialSuccess(val message: String) : TestResult
    data class Error(val message: String) : TestResult
}

/**
 * 管理应用程序数据备份与恢复逻辑的 ViewModel
 *
 * 支持通过 WebDAV 进行云端备份/还原，以及通过 Okio 流进行本地 Zip 包的导出/导入。
 */
@KoinViewModel
class BackupViewModel(
    private val apiConfigRepository: ApiConfigRepository,
    private val backupRepository: BackupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private var cachedConfig: WebDavConfig? = null
    private val FIXED_BACKUP_DIR = "Backup"

    init {
        // 监听并同步本地存储的 WebDAV 配置变化
        viewModelScope.launch {
            combine(
                apiConfigRepository.webDavConfigFlow,
                apiConfigRepository.webDavAutoSyncEnabledFlow
            ) { config, autoSyncEnabled ->
                config to autoSyncEnabled
            }.collectLatest { (config, autoSyncEnabled) ->
                cachedConfig = config
                _uiState.update { state ->
                    state.copy(
                        baseUrl = config?.baseUrl ?: "",
                        username = config?.username ?: "",
                        rootPath = config?.rootPath ?: "ShangKeSchedule",
                        hasSavedPassword = !config?.password.isNullOrBlank(),
                        autoSyncEnabled = autoSyncEnabled && config != null
                    )
                }
            }
        }
    }

    /**
     * 测试 WebDAV 服务器连接状况
     *
     * 连接成功后会自动持久化保存该配置。
     *
     * @param baseUrl 服务器 URL
     * @param username 用户名
     * @param pwd 密码（留空时优先使用已缓存的密码）
     * @param rootPath 备份根路径
     */
    fun testWebDavConnection(baseUrl: String, username: String, pwd: String, rootPath: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, testResult = TestResult.Idle) }
            val finalPassword = if (pwd.isEmpty()) cachedConfig?.password ?: "" else pwd.trim()
            val processedRootPath = rootPath.trim().removeSuffix("/")

            val testConfig = WebDavConfig(baseUrl.trim(), username.trim(), finalPassword, processedRootPath)
            val testClient = apiConfigRepository.createWebDavClient(testConfig)

            val isConnected = testClient?.ensureRootDirectoryExists() ?: false
            testClient?.close()

            if (isConnected) {
                val saveResult = apiConfigRepository.saveWebDavConfig(testConfig)
                if (saveResult.isSuccess) {
                    _uiState.update { it.copy(isTesting = false, testResult = TestResult.Success) }
                } else {
                    // 连接成功但本地保存失败（如加密服务异常）：必须明确提示，不能静默
                    val errMsg = getString(Res.string.error_op_failed, saveResult.exceptionOrNull()?.message ?: "save failed")
                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            testResult = TestResult.Error(errMsg)
                        )
                    }
                }
            } else {
                val errMsg = getString(Res.string.backup_err_connect_failed)
                _uiState.update {
                    it.copy(
                        isTesting = false,
                        testResult = TestResult.Error(errMsg)
                    )
                }
            }
        }
    }

    /**
     * 断开并清除保存的 WebDAV 配置
     */
    fun disconnectWebDav() {
        viewModelScope.launch { apiConfigRepository.clearWebDavConfig() }
    }

    /**
     * 更新 WebDAV 自动同步开关。
     *
     * 未配置 WebDAV 时拒绝开启，避免后台任务反复失败。
     */
    fun setWebDavAutoSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && cachedConfig == null) {
                _uiState.update {
                    it.copy(testResult = TestResult.Error(getString(Res.string.error_webdav_unconfigured)))
                }
                return@launch
            }
            apiConfigRepository.setWebDavAutoSyncEnabled(enabled)
        }
    }

    /**
     * 将全量应用数据打包并上传备份至 WebDAV 服务器
     */
    fun backupToWebDav() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, testResult = TestResult.Idle) }
            val result = backupRepository.uploadFullBackupToWebDav()
            _uiState.update {
                it.copy(
                    isBusy = false,
                    testResult = result.fold(
                        onSuccess = { TestResult.Success },
                        onFailure = { TestResult.Error(it.message ?: getString(Res.string.backup_err_upload_failed)) }
                    )
                )
            }
        }
    }

    /**
     * 从 WebDAV 服务器拉取备份数据并恢复应用数据
     */
    fun restoreFromWebDav() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, testResult = TestResult.Idle) }

            val client = apiConfigRepository.createWebDavClient() ?: run {
                val errMsg = getString(Res.string.error_webdav_unconfigured)
                _uiState.update { it.copy(isBusy = false, testResult = TestResult.Error(errMsg)) }
                return@launch
            }

            // P1-15 静默降级显性化：云端个别模块下载失败被跳过的 key，完成后提示用户
            val skippedModuleKeys = mutableListOf<String>()
            val result = withContext(Dispatchers.IO) {
                try {
                    val tempDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY
                    val metaPath = tempDir / "meta_restore.json"

                    val corruptedMsg = getString(Res.string.backup_err_corrupted)
                    if (!client.downloadFile("$FIXED_BACKUP_DIR/meta.json", metaPath)) {
                        return@withContext Result.failure(Exception(corruptedMsg))
                    }

                    // 读取并解析元数据
                    val metaText = FileSystem.SYSTEM.read(metaPath) { readUtf8() }
                    val meta = Json.decodeFromString(BackupMeta.serializer(), metaText)

                    // 批量下载并校验各模块数据文件
                    val payloadMap = mutableMapOf<String, ByteArray>()
                    for (module in meta.modules) {
                        // 远端 meta.json 完全由服务器控制，module.key 不可直接用于本地路径拼接：
                        // 形如 "../../files/xxx" 的 key 会写到临时目录之外（okio 的 Path / String
                        // 不做 `..` 归一化）。此处按本 App 已知模块 key 白名单收口。
                        if (BackupModule.entries.none { it.key == module.key }) {
                            skippedModuleKeys.add(module.key)
                            continue
                        }
                        val modulePath = tempDir / "${module.key}_restore.cbor"
                        if (client.downloadFile("$FIXED_BACKUP_DIR/${module.key}.cbor", modulePath)) {
                            val bytes = FileSystem.SYSTEM.read(modulePath) { readByteArray() }
                            payloadMap[module.key] = bytes
                        } else {
                            // P1-15 静默降级显性化：记录下载失败被跳过的模块，恢复完成后提示用户
                            skippedModuleKeys.add(module.key)
                        }
                    }

                    if (payloadMap.isEmpty()) {
                        return@withContext Result.failure(Exception(corruptedMsg))
                    }

                    val availableModules = meta.modules.filter { payloadMap.containsKey(it.key) }
                    val safeMeta = meta.copy(modules = availableModules)

                    val packageObj = AppBackupPackage(safeMeta, payloadMap)
                    backupRepository.restoreFullSoftwareBackup(packageObj)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

            client.close()
            val exception = result.exceptionOrNull()
            val failPrefix = getString(Res.string.backup_err_restore_failed_prefix, exception?.message ?: "")
            val partialWarning = if (result.isSuccess && skippedModuleKeys.isNotEmpty()) {
                getString(Res.string.backup_warn_restore_partial, skippedModuleKeys.joinToString(", "))
            } else {
                null
            }
            _uiState.update {
                it.copy(
                    isBusy = false,
                    testResult = when {
                        result.isSuccess && partialWarning != null -> TestResult.PartialSuccess(partialWarning)
                        result.isSuccess -> TestResult.Success
                        else -> TestResult.Error(failPrefix)
                    }
                )
            }
            skippedModuleKeys.clear()
        }
    }

    /**
     * 将软件数据全量导出至本地 Zip 压缩流
     *
     * @param sink 用于接收导出的 Zip 字节数据的输出流（BufferedSink）
     * @return 导出成功返回 `true`，失败或无数据返回 `false`
     */
    suspend fun exportToLocalZip(sink: BufferedSink): Boolean = withContext(Dispatchers.IO) {
        _uiState.update { it.copy(isBusy = true, testResult = TestResult.Idle) }

        val backupPackage = backupRepository.createFullSoftwareBackup(BackupModule.entries)
        if (backupPackage == null) {
            val emptyMsg = getString(Res.string.backup_err_empty)
            _uiState.update { it.copy(isBusy = false, testResult = TestResult.Error(emptyMsg)) }
            return@withContext false
        }

        try {
            val entries = mutableMapOf<String, ByteArray>()
            val metaJson = Json.encodeToString(BackupMeta.serializer(), backupPackage.meta)
            entries["meta.json"] = metaJson.encodeToByteArray()

            for ((key, bytes) in backupPackage.payloadMap) {
                entries["$key.cbor"] = bytes
            }

            val zipBytes = ZipUtils.createZip(entries)
            if (zipBytes.isEmpty()) {
                val exportFailMsg = getString(Res.string.backup_err_local_export_failed)
                _uiState.update { it.copy(isBusy = false, testResult = TestResult.Error(exportFailMsg)) }
                return@withContext false
            }

            sink.write(zipBytes)
            sink.flush()

            _uiState.update { it.copy(isBusy = false, testResult = TestResult.Success) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            val exportFailMsg = getString(Res.string.backup_err_local_export_failed)
            _uiState.update { it.copy(isBusy = false, testResult = TestResult.Error(exportFailMsg)) }
            false
        }
    }

    /**
     * 从本地 Zip 压缩流中导入并恢复应用数据
     *
     * @param source 包含备份 Zip 字节数据的输入流（BufferedSource）
     * @return 恢复成功返回 `true`，失败返回 `false`
     */
    suspend fun importFromLocalZip(source: BufferedSource): Boolean = withContext(Dispatchers.IO) {
        _uiState.update { it.copy(isBusy = true, testResult = TestResult.Idle) }

        // 临时包路径必须在 try 外声明：Kotlin 的 finally 看不到 try 块内声明的变量
        val tempZipPath = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
            "backup_import_${Random.nextLong(100000, 999999)}.zip"

        try {
            // 将输入流转存为本地临时压缩包文件以便读取 ZipFS
            FileSystem.SYSTEM.sink(tempZipPath).buffer().use { sink ->
                sink.writeAll(source)
            }

            var meta: BackupMeta? = null
            val payloadMap = mutableMapOf<String, ByteArray>()

            // 提前取出，供解压上限校验复用
            val corruptedMsg = getString(Res.string.backup_err_corrupted)

            // 使用 Okio ZipFileSystem 读取解压，并在删除临时文件前关闭文件系统
            val zipFs = FileSystem.SYSTEM.openZip(tempZipPath)
            try {
                val filesInZip = zipFs.list("/".toPath())
                var totalPayloadBytes = 0L
                for (filePath in filesInZip) {
                    val fileName = filePath.name
                    when {
                        fileName == "meta.json" -> {
                            val content = zipFs.read(filePath) { readUtf8() }
                            meta = Json.decodeFromString(BackupMeta.serializer(), content)
                        }
                        fileName.endsWith(".cbor") -> {
                            val key = fileName.removeSuffix(".cbor")
                            // FIX: 分块读取并对单条/累计解压大小设限，
                            // 避免损坏或恶意备份包用超大条目一次性耗尽内存（zip 炸弹）
                            val bytes = zipFs.read(filePath) {
                                val chunkBuffer = okio.Buffer()
                                var entryBytes = 0L
                                while (true) {
                                    val read = read(chunkBuffer, BACKUP_READ_CHUNK_BYTES)
                                    if (read == -1L) break
                                    entryBytes += read
                                    if (entryBytes > MAX_BACKUP_ENTRY_BYTES) {
                                        throw Exception(corruptedMsg)
                                    }
                                }
                                chunkBuffer.readByteArray()
                            }
                            totalPayloadBytes += bytes.size
                            if (totalPayloadBytes > MAX_BACKUP_TOTAL_BYTES) {
                                throw Exception(corruptedMsg)
                            }
                            payloadMap[key] = bytes
                        }
                    }
                }
            } finally {
                zipFs.close()
            }

            val finalMeta = meta ?: throw Exception(corruptedMsg)

            if (payloadMap.isEmpty()) {
                throw Exception(corruptedMsg)
            }

            val availableModules = finalMeta.modules.filter { payloadMap.containsKey(it.key) }
            val safeMeta = finalMeta.copy(modules = availableModules)

            val packageObj = AppBackupPackage(safeMeta, payloadMap)
            backupRepository.restoreFullSoftwareBackup(packageObj).getOrThrow()

            _uiState.update { it.copy(isBusy = false, testResult = TestResult.Success) }
            true
        } catch (e: Exception) {
            val errMsg = e.message ?: ""
            val failPrefix = getString(Res.string.backup_err_restore_failed_prefix, errMsg)
            _uiState.update {
                it.copy(
                    isBusy = false,
                    testResult = TestResult.Error(failPrefix)
                )
            }
            false
        } finally {
            // FIX: 无论成功、失败还是中途抛异常，都必须删除临时压缩包。
            // 原先只在正常路径 delete，异常时会在 SYSTEM_TEMPORARY_DIRECTORY 残留备份明文副本。
            try {
                FileSystem.SYSTEM.delete(tempZipPath)
            } catch (cleanupError: Exception) {
                cleanupError.printStackTrace()
            }
        }
    }
}

/** 单条备份条目允许解压的上限。 */
private const val MAX_BACKUP_ENTRY_BYTES = 32L * 1024 * 1024

/** 一次导入允许累计解压的上限。 */
private const val MAX_BACKUP_TOTAL_BYTES = 128L * 1024 * 1024

/** 分块读取块大小。 */
private const val BACKUP_READ_CHUNK_BYTES = 64L * 1024
