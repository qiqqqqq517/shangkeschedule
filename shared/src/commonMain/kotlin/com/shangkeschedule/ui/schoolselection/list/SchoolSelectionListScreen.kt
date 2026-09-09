package com.shangkeschedule.ui.schoolselection.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.shangkeschedule.Destination
import com.shangkeschedule.data.model.SchoolHistoryModel
import com.shangkeschedule.ui.components.AlphabetIndexerList
import com.shangkeschedule.ui.components.AppCard
import com.shangkeschedule.ui.components.AppEmptyState
import com.shangkeschedule.ui.components.AppLoading
import com.shangkeschedule.ui.components.AppSegmentedControl
import com.shangkeschedule.ui.components.AppTextField
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appShapes
import com.shangkeschedule.ui.theme.appSpacing
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import school_index.AdapterCategory
import school_index.School
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.a11y_clear_search
import shangkeschedule.shared.generated.resources.a11y_delete
import shangkeschedule.shared.generated.resources.a11y_school_icon
import shangkeschedule.shared.generated.resources.a11y_search
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.category_bachelor_associate
import shangkeschedule.shared.generated.resources.category_general_tool
import shangkeschedule.shared.generated.resources.category_other
import shangkeschedule.shared.generated.resources.category_postgraduate
import shangkeschedule.shared.generated.resources.close_24px
import shangkeschedule.shared.generated.resources.label_recent_visit
import shangkeschedule.shared.generated.resources.school_24px
import shangkeschedule.shared.generated.resources.search_24px
import shangkeschedule.shared.generated.resources.search_hint_school
import shangkeschedule.shared.generated.resources.text_no_adapter_for_category
import shangkeschedule.shared.generated.resources.text_no_school_found
import shangkeschedule.shared.generated.resources.title_select_school

/**
 * 主学校选择屏幕，现在通过 ViewModel 管理状态和数据获取。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolSelectionListScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    isCrushImport: Boolean = false,
    viewModel: SchoolSelectionViewModel = koinViewModel()
) {
    // 观察 ViewModel 状态
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val filteredSchools by viewModel.filteredSchools.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val schoolHistory by viewModel.schoolHistory.collectAsState()

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var isSearchActive by remember { mutableStateOf(false) }

    val titleText = stringResource(Res.string.title_select_school)
    val placeholderText = stringResource(Res.string.search_hint_school)

    Scaffold(
        topBar = {
            SearchBarWithTitle(
                onBack = onBack,
                searchQuery = searchQuery,
                onQueryChange = viewModel::updateSearchQuery,
                searchActive = isSearchActive,
                onSearchActiveChange = { active ->
                    isSearchActive = active
                    if (!active) {
                        viewModel.updateSearchQuery("")
                    }
                },
                placeholderText = placeholderText,
                titleText = titleText,
                filteredSchools = filteredSchools,
                onSchoolSelected = { selectedSchool ->
                    viewModel.saveLastSchool(selectedSchool)
                    onNavigate(
                        Destination.AdapterSelection(
                            schoolId = selectedSchool.id,
                            schoolName = selectedSchool.name,
                            categoryNumber = selectedCategory.value,
                            resourceFolder = selectedSchool.resource_folder,
                            isCrushImport = isCrushImport
                        )
                    )
                    isSearchActive = false
                    viewModel.updateSearchQuery("")
                }
            )
        }
    ) { paddingValues ->
        if (!isSearchActive) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                CategoryTabs(
                    selectedCategory = selectedCategory,
                    onCategorySelected = { category ->
                        viewModel.updateSelectedCategory(category)
                        coroutineScope.launch { lazyListState.scrollToItem(0) }
                    },
                    displayCategories = viewModel.displayCategories
                )

                SchoolContent(
                    isLoading = isLoading,
                    filteredSchools = filteredSchools,
                    lazyListState = lazyListState,
                    selectedCategory = selectedCategory,
                    schoolHistory = schoolHistory,
                    onClearHistory = { viewModel.clearHistory(it) },
                    onSchoolSelected = { school, category ->
                        viewModel.saveLastSchool(school)
                        // 列表点击跳转
                        onNavigate(
                            Destination.AdapterSelection(
                                schoolId = school.id,
                                schoolName = school.name,
                                categoryNumber = category.value,
                                resourceFolder = school.resource_folder,
                                isCrushImport = isCrushImport
                            )
                        )
                    }
                )
            }
        } else {
            Box(modifier = Modifier.padding(paddingValues))
        }
    }
}

/**
 * 集中管理加载状态和列表显示。
 */
@Composable
private fun SchoolContent(
    isLoading: Boolean,
    filteredSchools: List<School>,
    lazyListState: LazyListState,
    selectedCategory: AdapterCategory,
    schoolHistory: SchoolHistoryModel,
    onClearHistory: (AdapterCategory) -> Unit,
    onSchoolSelected: (School, AdapterCategory) -> Unit
) {
    val recentRecord = when (selectedCategory) {
        AdapterCategory.BACHELOR_AND_ASSOCIATE -> schoolHistory.bachelor
        AdapterCategory.POSTGRADUATE -> schoolHistory.postgraduate
        AdapterCategory.GENERAL_TOOL -> schoolHistory.general
        else -> null
    }

    when {
        isLoading -> {
            AppLoading()
        }
        filteredSchools.isEmpty() && !isLoading -> {
            // 统一空状态：淡灰胶囊 + 辅助文案
            AppEmptyState(
                hint = stringResource(Res.string.text_no_adapter_for_category),
                fillScreen = true
            )
        }
        else -> {
            AlphabetIndexerList(
                data = filteredSchools,
                getInitial = { it.initial.firstOrNull()?.uppercase() ?: "#" },
                lazyListState = lazyListState,
                headerContent = {
                    // 历史记录可能保存了旧的 resource_folder（学校适配更新后旧值会失效，
                    // 如沈阳农业 urp→syau），若沿用旧值会把脚本路径拼错并提示“导入脚本文件不存在”。
                    // 这里优先用当前索引中同 id 学校的最新数据；索引中已无该校时回退到历史记录。
                    val recentSchool = if (recentRecord != null && !recentRecord.isEmpty) {
                        filteredSchools.firstOrNull { it.id == recentRecord.id } ?: recentRecord.toSchool()
                    } else {
                        null
                    }
                    if (recentSchool != null) {
                        Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                            Text(
                                text = stringResource(Res.string.label_recent_visit),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
                            )
                            Box(modifier = Modifier.fillMaxWidth()) {
                                SchoolItem(
                                    school = recentSchool,
                                    onClick = { onSchoolSelected(it, selectedCategory) }
                                )
                                IconButton(
                                    onClick = { onClearHistory(selectedCategory) },
                                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = vectorResource(Res.drawable.close_24px),
                                        contentDescription = stringResource(Res.string.a11y_delete),
                                        modifier = Modifier.size(20.dp),
                                        tint = appColors().divider
                                    )
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(top = 12.dp, start = 8.dp, end = 8.dp),
                                thickness = 0.5.dp,
                                color = appColors().divider
                            )
                        }
                    }
                }
            ) { school ->
                Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                    SchoolItem(
                        school = school,
                        onClick = { onSchoolSelected(it, selectedCategory) }
                    )
                }
            }
        }
    }
}

/**
 * 类别选择器，使用胶囊分段控件替代 M3 PrimaryTabRow。
 */
@Composable
fun CategoryTabs(
    selectedCategory: AdapterCategory,
    onCategorySelected: (AdapterCategory) -> Unit,
    displayCategories: List<AdapterCategory>
) {
    @Composable
    fun getDisplayName(category: AdapterCategory): String {
        return when (category) {
            AdapterCategory.BACHELOR_AND_ASSOCIATE -> stringResource(Res.string.category_bachelor_associate)
            AdapterCategory.POSTGRADUATE -> stringResource(Res.string.category_postgraduate)
            AdapterCategory.GENERAL_TOOL -> stringResource(Res.string.category_general_tool)
            else -> stringResource(Res.string.category_other)
        }
    }

    val selectedIndex = displayCategories.indexOf(selectedCategory).coerceAtLeast(0)
    val options = displayCategories.map { getDisplayName(it) }

    AppSegmentedControl(
        options = options,
        selectedIndex = selectedIndex,
        onSelect = { index -> onCategorySelected(displayCategories[index]) },
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * 带有标题和搜索功能的自定义搜索组件。
 * 用胶囊输入框替代 M3 SearchBar，行为与旧实现保持一致。
 */
@Composable
fun SearchBarWithTitle(
    onBack: () -> Unit,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    searchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    placeholderText: String,
    titleText: String,
    filteredSchools: List<School>,
    onSchoolSelected: (School) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val tokens = appColors()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 8.dp)
    ) {
        AppTextField(
            value = searchQuery,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = if (searchActive) placeholderText else titleText,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (searchQuery.isBlank()) {
                        onSearchActiveChange(false)
                    } else {
                        if (!searchActive) onSearchActiveChange(true)
                        keyboardController?.hide()
                    }
                }
            ),
            leadingIcon = {
                IconButton(onClick = {
                    if (searchActive) {
                        onSearchActiveChange(false)
                        onQueryChange("")
                    } else {
                        onBack()
                    }
                }) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.arrow_back_24px),
                        contentDescription = stringResource(Res.string.a11y_back),
                        tint = tokens.textSecondary
                    )
                }
            },
            trailingIcon = {
                if (!searchActive) {
                    IconButton(onClick = { onSearchActiveChange(true) }) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.search_24px),
                            contentDescription = stringResource(Res.string.a11y_search),
                            tint = tokens.textSecondary
                        )
                    }
                } else if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.close_24px),
                            contentDescription = stringResource(Res.string.a11y_clear_search),
                            tint = tokens.textSecondary
                        )
                    }
                }
            }
        )

        if (searchActive) {
            // 搜索结果内容
            if (filteredSchools.isEmpty() && searchQuery.isNotBlank()) {
                AppEmptyState(
                    hint = stringResource(Res.string.text_no_school_found),
                    fillScreen = true
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = appSpacing().pageHorizontal, vertical = appSpacing().cardGap),
                    verticalArrangement = Arrangement.spacedBy(appSpacing().cardGap)
                ) {
                    items(filteredSchools) { school ->
                        SchoolItem(school = school) { onSchoolSelected(it) }
                    }
                }
            }
        }
    }
}

/**
 * 学校列表项
 */
@Composable
fun SchoolItem(school: School, onClick: (School) -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onClick(school) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = appSpacing().cardInner, vertical = appSpacing().cardInner),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.school_24px),
                contentDescription = stringResource(Res.string.a11y_school_icon),
                modifier = Modifier
                    .size(24.dp)
                    .padding(end = 8.dp),
                tint = appColors().textSecondary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = school.name,
                    style = MaterialTheme.typography.titleMedium,
                    // 学校名是行主标题：用主文字色（原 onSurfaceVariant 层级偏弱）
                    color = appColors().textPrimary
                )
            }
        }
    }
}