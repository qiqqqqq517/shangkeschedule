package com.shangkeschedule.ui.adapters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.model.AdapterSyncRecord
import com.shangkeschedule.data.model.CategoryLastSchool
import com.shangkeschedule.data.model.SchoolCategoryTab
import com.shangkeschedule.data.repository.AppSettingsRepository
import com.shangkeschedule.data.repository.SchoolHistoryRepository
import com.shangkeschedule.data.repository.SchoolRepository
import com.shangkeschedule.tool.AdapterRemoteUpdater
import com.shangkeschedule.tool.AdapterSyncResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Named
import kotlin.time.Clock

/**
 * 教务适配状态页的 UI 状态（v4.66.0）。
 *
 * 页面只呈现**本机真实数据**：不伪造「已支持 / 适配中 / 待适配」进度 ——
 * 远程适配清单（index.json）目前只有 `{path, sha256}`，没有学校维度的状态字段。
 * 等仓库补上 status.json 后，这里再加真正的进度维度。
 */
data class AdapterStatusUiState(
    val loading: Boolean = true,
    /** `repo/schools/resources/` 下的 `.js` 适配脚本总数（内置 + 远程更新）。 */
    val localScriptCount: Int = 0,
    /** 学校索引里的学校数量。 */
    val schoolIndexCount: Int = 0,
    /** 内置离线仓库是否已解压就绪（`repo/index` 存在）。 */
    val repoReady: Boolean = false,
    val selectedSchools: List<SelectedSchoolStatus> = emptyList(),
    val lastSync: AdapterSyncRecord? = null,
    val syncing: Boolean = false
)

/**
 * 某个分类口径下已选学校与它命中的适配脚本状态。
 *
 * [folder] 是**有效**目录：优先用当前索引里同 id 学校的最新 `resource_folder`，
 * 索引里已无该校时才回退到历史记录 —— 与学校选择页的取值口径一致
 * （历史值可能过期，如沈阳农业 urp→syau）。
 */
data class SelectedSchoolStatus(
    val tab: SchoolCategoryTab,
    val schoolName: String,
    val folder: String,
    val scriptCount: Int,
    /** 索引里的目录与历史记录不同（说明适配已换新，历史值已过期）。 */
    val folderRefreshed: Boolean = false
)

/**
 * 教务适配状态（v4.66.0）。
 *
 * 数据来源全部是本机文件与已有的仓库接口，不需要任何后端配合：
 * - 适配脚本数：遍历 `repo/schools/resources`；
 * - 学校索引数：[SchoolRepository.getSchools]；
 * - 已选学校：`SchoolHistory` 的两个分类口径记录（教务系统 / 通用工具）+ 索引校对；
 * - 上次检查结果：[AdapterSyncRecord]（本页新增落盘，此前只存在于内存）。
 */
@KoinViewModel
class AdapterStatusViewModel(
    private val fileSystem: FileSystem,
    @Named("FilesDir") private val filesDir: Path,
    private val schoolRepository: SchoolRepository,
    private val schoolHistoryRepository: SchoolHistoryRepository,
    private val adapterRemoteUpdater: AdapterRemoteUpdater,
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdapterStatusUiState())
    val uiState: StateFlow<AdapterStatusUiState> = _uiState.asStateFlow()

    init {
        // 上次检查记录独立于本机扫描：同步完成落盘后这里会自动刷新
        viewModelScope.launch {
            appSettingsRepository.getAdapterSyncRecord().collect { record ->
                _uiState.update { it.copy(lastSync = record) }
            }
        }
        refresh()
    }

    /** 重新扫描本机适配资源（进入页面、同步完成后各调一次）。 */
    fun refresh() {
        viewModelScope.launch {
            // scanLocal() 要读数据库 + 递归遍历适配脚本目录，属阻塞 IO：
            // 必须换到 IO 调度器，否则每次进页面 / 同步完成都会卡主线程。
            val snapshot = withContext(Dispatchers.IO) { runCatching { scanLocal() }.getOrNull() }
            _uiState.update { current ->
                if (snapshot == null) {
                    current.copy(loading = false)
                } else {
                    current.copy(
                        loading = false,
                        localScriptCount = snapshot.localScriptCount,
                        schoolIndexCount = snapshot.schoolIndexCount,
                        repoReady = snapshot.repoReady,
                        selectedSchools = snapshot.selectedSchools
                    )
                }
            }
        }
    }

    /**
     * 手动触发一次远程适配同步。
     *
     * 结果同时做两件事：写进 [AdapterSyncRecord]（这样「上次检查」能跨启动保留），
     * 并让页面立刻重扫本机脚本数（更新成功后数字会变）。
     */
    fun sync() {
        if (_uiState.value.syncing) return
        _uiState.update { it.copy(syncing = true) }
        viewModelScope.launch {
            val result = runCatching { adapterRemoteUpdater.sync() }
                .getOrElse { AdapterSyncResult.Failed("exception") }
            val updatedCount = (result as? AdapterSyncResult.Updated)?.fileCount ?: 0
            runCatching {
                appSettingsRepository.updateAdapterSyncRecord(
                    kind = result.toRecordKind(),
                    updatedCount = updatedCount,
                    atMillis = Clock.System.now().toEpochMilliseconds()
                )
            }
            _uiState.update { it.copy(syncing = false) }
            refresh()
        }
    }

    private suspend fun scanLocal(): LocalSnapshot {
        val schools = runCatching { schoolRepository.getSchools() }.getOrDefault(emptyList())
        val history = runCatching { schoolHistoryRepository.historyFlow.first() }.getOrNull()

        val selected = listOf(
            // v4.70.0：本科/专科与研究生合并为「教务系统」，这里也只列两个口径
            SchoolCategoryTab.ACADEMIC_SYSTEM to (history?.academic ?: CategoryLastSchool()),
            SchoolCategoryTab.GENERAL_TOOL to (history?.general ?: CategoryLastSchool())
        ).filter { (_, last) -> !last.isEmpty }.map { (tab, last) ->
            val indexed = schools.firstOrNull { it.id == last.id }
            val indexedFolder = indexed?.resource_folder?.takeIf { it.isNotBlank() }
            val folder = indexedFolder ?: last.resourceFolder
            SelectedSchoolStatus(
                tab = tab,
                schoolName = indexed?.name?.takeIf { it.isNotBlank() } ?: last.name,
                folder = folder,
                scriptCount = countScriptsInFolder(folder),
                folderRefreshed = indexedFolder != null && indexedFolder != last.resourceFolder
            )
        }

        return LocalSnapshot(
            localScriptCount = countAllScripts(),
            schoolIndexCount = schools.size,
            repoReady = runCatching { fileSystem.exists(filesDir / REPO_DIR_NAME / "index") }.getOrDefault(false),
            selectedSchools = selected
        )
    }

    /** 全部 `.js` 适配脚本数：递归遍历适配脚本根目录。 */
    private fun countAllScripts(): Int {
        val root = filesDir / REPO_DIR_NAME / RESOURCES_DIR_NAME
        return runCatching {
            if (!fileSystem.exists(root)) {
                0
            } else {
                fileSystem.listRecursively(root).count { it.name.endsWith(SCRIPT_EXTENSION, ignoreCase = true) }
            }
        }.getOrDefault(0)
    }

    /** 某个适配器目录里的脚本数；目录不存在或为空都返回 0，由页面区分「目录为空」与「没有记录」。 */
    private fun countScriptsInFolder(folder: String): Int {
        if (folder.isBlank()) return 0
        val dir = filesDir / REPO_DIR_NAME / RESOURCES_DIR_NAME / folder
        return runCatching {
            if (!fileSystem.exists(dir)) {
                0
            } else {
                fileSystem.list(dir).count { it.name.endsWith(SCRIPT_EXTENSION, ignoreCase = true) }
            }
        }.getOrDefault(0)
    }

    private data class LocalSnapshot(
        val localScriptCount: Int,
        val schoolIndexCount: Int,
        val repoReady: Boolean,
        val selectedSchools: List<SelectedSchoolStatus>
    )

    private companion object {
        const val REPO_DIR_NAME = "repo"
        const val RESOURCES_DIR_NAME = "schools/resources"
        const val SCRIPT_EXTENSION = ".js"
    }
}

/** 同步结果 → 落盘用的类型标记（页面据此复用已有的 sync_status_* 文案）。 */
private fun AdapterSyncResult.toRecordKind(): String = when (this) {
    is AdapterSyncResult.Updated -> AdapterSyncRecord.KIND_UPDATED
    AdapterSyncResult.UpToDate -> AdapterSyncRecord.KIND_UP_TO_DATE
    AdapterSyncResult.Disabled -> AdapterSyncRecord.KIND_DISABLED
    is AdapterSyncResult.VerificationFailed -> AdapterSyncRecord.KIND_VERIFICATION_FAILED
    // P2-6：部分成功按「已更新」归档 —— 本轮确实落地了文件，页面文案复用 KIND_UPDATED 即可。
    is AdapterSyncResult.PartiallyUpdated -> AdapterSyncRecord.KIND_UPDATED
    is AdapterSyncResult.Failed -> AdapterSyncRecord.KIND_FAILED
}
