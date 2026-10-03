package com.shangkeschedule.ui.schoolselection.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.model.SchoolCategoryTab
import com.shangkeschedule.data.model.SchoolHistoryModel
import com.shangkeschedule.data.repository.SchoolHistoryRepository
import com.shangkeschedule.data.repository.SchoolRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import school_index.School
import org.koin.core.annotation.KoinViewModel

/**
 * 负责一级学校选择页面的数据管理、状态维护和过滤逻辑。
 * 使用 Koin 注解注入所需的 Repository 实例。
 */
@KoinViewModel
class SchoolSelectionViewModel(
    private val schoolRepository: SchoolRepository,
    private val historyRepository: SchoolHistoryRepository
) : ViewModel() {

    private val _allSchools = MutableStateFlow<List<School>>(emptyList())
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

    // 过滤逻辑
    val filteredSchools: StateFlow<List<School>> = combine(
        _allSchools,
        _searchQuery,
        _selectedTab
    ) { allSchools, query, tab ->
        val categoryFiltered = allSchools.filter { school ->
            school.adapters.any { adapter -> tab.matches(adapter.category) }
        }

        val searched = if (query.isBlank()) {
            categoryFiltered
        } else {
            categoryFiltered.filter { school ->
                school.name.contains(query, ignoreCase = true) ||
                        school.initial.contains(query, ignoreCase = true)
            }
        }

        // 按 initial 首字母 ABCD 排序，同首字母按学校名称排序
        searched.sortedWith(
            compareBy<School> { it.initial.firstOrNull()?.uppercase() ?: "#" }
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
                    _allSchools.value = schools
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

    fun saveLastSchool(school: School) {
        viewModelScope.launch {
            historyRepository.saveLastSchool(_selectedTab.value, school)
        }
    }

    fun clearHistory(tab: SchoolCategoryTab) {
        viewModelScope.launch {
            historyRepository.clearHistory(tab)
        }
    }

    /**
     * 获取某校在指定分类口径下的适配器列表。
     *
     * 「教务系统」口径 = 本科/专科 + 研究生，二级页因此会把两类适配器放在一起给用户选。
     * 口径由调用方显式传入：二级页有独立的 ViewModel 实例，不能依赖本页当前选中的分类。
     */
    suspend fun getAdaptersForSchoolInTab(
        schoolId: String,
        tab: SchoolCategoryTab
    ): List<school_index.Adapter> {
        return schoolRepository.getAdaptersForSchool(schoolId).filter { adapter ->
            tab.matches(adapter.category)
        }
    }
}