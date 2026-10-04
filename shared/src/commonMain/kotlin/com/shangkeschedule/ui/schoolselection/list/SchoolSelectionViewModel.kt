package com.shangkeschedule.ui.schoolselection.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.model.CategoryLastSchool
import com.shangkeschedule.data.model.SchoolCategoryTab
import com.shangkeschedule.data.model.SchoolHistoryModel
import com.shangkeschedule.data.repository.SchoolHistoryRepository
import com.shangkeschedule.data.repository.SchoolRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

/**
 * 负责一级学校选择页面的数据管理、状态维护和过滤逻辑。
 * 使用 Koin 注解注入所需的 Repository 实例。
 *
 * v4.73.0：列表元素由 `School` 换成 [SchoolListEntry]（**一校一行**，按校名合并索引里
 * 的多条记录，详见该类注释）。
 */
@KoinViewModel
class SchoolSelectionViewModel(
    private val schoolRepository: SchoolRepository,
    private val historyRepository: SchoolHistoryRepository
) : ViewModel() {

    private val _allEntries = MutableStateFlow<List<SchoolListEntry>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedTab = MutableStateFlow(SchoolCategoryTab.ACADEMIC_SYSTEM)
    val selectedTab: StateFlow<SchoolCategoryTab> = _selectedTab

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    // 区分「索引加载失败」与「真空列表」（v3.54.0）：失败时给出重试入口
    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed

    // 观察历史记录
    val schoolHistory: StateFlow<SchoolHistoryModel> = historyRepository.historyFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SchoolHistoryModel()
        )

    init {
        loadSchools()
    }

    // 分类胶囊：v4.70.0 起本科/专科与研究生合并为「教务系统」，只保留两个口径
    val displayTabs: List<SchoolCategoryTab> = SchoolCategoryTab.entries

    // 过滤逻辑（v4.73.0：按 SchoolListEntry 过滤，命中组内**任一**记录即可）
    val filteredEntries: StateFlow<List<SchoolListEntry>> = combine(
        _allEntries,
        _searchQuery,
        _selectedTab
    ) { entries, query, tab ->
        val categoryFiltered = entries.filter { entry ->
            entry.allAdaptersIn(tab.categories).isNotEmpty()
        }

        val searched = if (query.isBlank()) {
            categoryFiltered
        } else {
            categoryFiltered.filter { entry ->
                entry.name.contains(query, ignoreCase = true) ||
                        entry.initial.contains(query, ignoreCase = true)
            }
        }

        // 按 initial 首字母 ABCD 排序，同首字母按学校名称排序
        searched.sortedWith(
            compareBy<SchoolListEntry> { it.initial.firstOrNull()?.uppercase() ?: "#" }
                .thenBy { it.name }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private fun loadSchools() {
        viewModelScope.launch {
            _isLoading.value = true
            _loadFailed.value = false
            runCatching { schoolRepository.getSchools() }
                .onSuccess { schools ->
                    // 正常索引至少含一所学校：空结果说明索引未加载成功
                    _loadFailed.value = schools.isEmpty()
                    // 按校名合并：同一所学校的本科/研究生两条索引记录在列表里只占一行
                    _allEntries.value = mergeSchoolsByName(schools)
                }
                .onFailure { _loadFailed.value = true }
            _isLoading.value = false
        }
    }

    /** 加载失败后的手动重试（v3.54.0）。 */
    fun retryLoad() = loadSchools()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSelectedTab(tab: SchoolCategoryTab) {
        _selectedTab.value = tab
    }

    /**
     * 记录「最近访问」。
     *
     * 写入时按**主记录**的 id 落库（与 [SchoolListEntry.representative] 一致），
     * 这样旧版本按本科 id 存的历史记录仍能命中；列表回显时再用 [resolveEntryById]
     * 在「主 id → 组内任一 id」范围内查找，避免因代表记录变化丢掉最近访问。
     */
    fun saveLastSchool(entry: SchoolListEntry) {
        viewModelScope.launch {
            historyRepository.saveLastSchool(
                _selectedTab.value,
                entry.representative.toLastSchool()
            )
        }
    }

    fun clearHistory(tab: SchoolCategoryTab) {
        viewModelScope.launch {
            historyRepository.clearHistory(tab)
        }
    }

    /**
     * 用历史记录里的学校 id 找回对应的列表条目。
     *
     * 先按主 id 精确命中；未命中则在**组内任一 id** 上找 —— 因为历史里存的可能是
     * 合并前的研究生记录 id（如 `u_151d2892_pg`），而列表主 id 是本科那条（`nju`）。
     * 找不到才回退到历史记录自带的信息（索引里已无该校）。
     */
    fun resolveEntryById(entryId: String): SchoolListEntry? =
        resolveEntryById(_allEntries.value, entryId)

    /** 全部条目（供「最近访问」回查用；不进 StateFlow，避免无谓重组）。 */
    val allEntriesSnapshot: List<SchoolListEntry> get() = _allEntries.value

    /**
     * 某校在指定分类口径下的全部适配器（v4.73.0：跨索引记录汇总）。
     *
     * 口径由调用方显式传入：二级页有独立的 ViewModel 实例，不能依赖本页当前选中分类。
     */
    fun adaptersFor(entry: SchoolListEntry, tab: SchoolCategoryTab): List<school_index.Adapter> =
        entry.allAdaptersIn(tab.categories)

    /**
     * 二级页用：把 [schoolIds] 对应的**所有**索引记录里、属于该口径的适配器连同
     * 各自的 `resource_folder` 一起取出（v4.73.0）。
     *
     * 逐条带目录的原因见 [AdapterWithFolder]。`schoolIds` 通常只有 1 个元素；
     * 同校本科/研究生两条记录时为 2 个。跨记录同一 `adapter_id` 会去重。
     */
    suspend fun adaptersWithFolders(
        schoolIds: List<String>,
        tab: SchoolCategoryTab
    ): List<AdapterWithFolder> {
        val out = ArrayList<AdapterWithFolder>()
        val seen = HashSet<String>()
        for (school in schoolRepository.getSchoolsByIds(schoolIds)) {
            for (adapter in school.adapters) {
                if (!tab.matches(adapter.category)) continue
                if (seen.add(adapter.adapter_id)) {
                    out.add(AdapterWithFolder(adapter, school.resource_folder))
                }
            }
        }
        return out
    }
}