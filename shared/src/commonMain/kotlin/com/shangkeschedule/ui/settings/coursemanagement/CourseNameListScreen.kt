package com.shangkeschedule.ui.settings.coursemanagement

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shangkeschedule.Destination
import com.shangkeschedule.navigation.AddEditCourseChannel
import com.shangkeschedule.navigation.PresetCourseData
import com.shangkeschedule.ui.components.AppEmptyState
import com.shangkeschedule.ui.components.AppDangerDialog
import com.shangkeschedule.ui.components.AppFab
import com.shangkeschedule.ui.components.AppSectionHeader
import com.shangkeschedule.ui.components.AppSelectableCard
import com.shangkeschedule.ui.theme.AccentTone
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.settings.SettingCard
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.a11y_cancel_selection
import shangkeschedule.shared.generated.resources.a11y_delete
import shangkeschedule.shared.generated.resources.confirm_delete
import shangkeschedule.shared.generated.resources.dialog_text_confirm_delete_courses
import shangkeschedule.shared.generated.resources.dialog_title_confirm_delete_course
import shangkeschedule.shared.generated.resources.a11y_enter_selection_mode
import shangkeschedule.shared.generated.resources.a11y_exit_selection_mode
import shangkeschedule.shared.generated.resources.action_add
import shangkeschedule.shared.generated.resources.action_deselect_all
import shangkeschedule.shared.generated.resources.action_select_all
import shangkeschedule.shared.generated.resources.add_24px
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.check_24px
import shangkeschedule.shared.generated.resources.close_24px
import shangkeschedule.shared.generated.resources.delete_24px
import shangkeschedule.shared.generated.resources.item_course_management
import shangkeschedule.shared.generated.resources.item_schedule_tweak
import shangkeschedule.shared.generated.resources.desc_schedule_tweak
import shangkeschedule.shared.generated.resources.item_quick_delete
import shangkeschedule.shared.generated.resources.quick_delete_subtitle
import shangkeschedule.shared.generated.resources.label_quick_action_category_schedule
import shangkeschedule.shared.generated.resources.chevron_right_24px
import shangkeschedule.shared.generated.resources.swap_horiz_24px
import shangkeschedule.shared.generated.resources.menu_open_24px
import shangkeschedule.shared.generated.resources.text_no_unique_courses_hint
import shangkeschedule.shared.generated.resources.title_selected_items_count
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * 一级页面：展示所有不重复的课程名称列表 (Master View)。
 * 现使用两列网格 (LazyVerticalGrid)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseNameListScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    viewModel: CourseNameListViewModel = koinViewModel()
) {
    val uniqueCourseNames by viewModel.uniqueCourseNames.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    // 液态玻璃 FAB 背板采样：内容 hazeSource，FAB 玻璃模糊其背后的列表
    val hazeState = rememberHazeState()

    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedCourseNames = remember { mutableStateListOf<String>() }
    // 批量删除前的确认快照（删除不可撤销，需二次确认）
    var pendingDeleteNames by remember { mutableStateOf<List<String>?>(null) }

    val exitSelectionMode: () -> Unit = {
        isSelectionMode = false
        selectedCourseNames.clear()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isSelectionMode) {
                            stringResource(Res.string.title_selected_items_count, selectedCourseNames.size)
                        } else {
                            stringResource(Res.string.item_course_management)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSelectionMode) {
                                exitSelectionMode()
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        val icon = if (isSelectionMode) vectorResource(Res.drawable.close_24px) else vectorResource(Res.drawable.arrow_back_24px)
                        val description = if (isSelectionMode) {
                            stringResource(Res.string.a11y_cancel_selection)
                        } else {
                            stringResource(Res.string.a11y_back)
                        }
                        Icon(icon, contentDescription = description)
                    }
                },
                actions = {
                    val totalCount = uniqueCourseNames.size
                    val selectedCount = selectedCourseNames.size
                    val isAllSelected = totalCount > 0 && selectedCount == totalCount

                    // 1. 多选模式下显示 全选/取消全选 按钮
                    if (isSelectionMode) {
                        IconButton(
                            onClick = {
                                if (isAllSelected) {
                                    // 取消全选
                                    selectedCourseNames.clear()
                                } else {
                                    // 全选
                                    selectedCourseNames.clear()
                                    selectedCourseNames.addAll(uniqueCourseNames.map { it.name })
                                }
                            },
                            enabled = totalCount > 0
                        ) {
                            val selectAllStringRes = if (isAllSelected) Res.string.action_deselect_all else Res.string.action_select_all
                            Icon(vectorResource(Res.drawable.check_24px), contentDescription = stringResource(selectAllStringRes))
                        }
                    }

                    // 2. 多选模式下显示删除按钮
                    if (isSelectionMode) {
                        IconButton(
                            onClick = {
                                if (selectedCourseNames.isNotEmpty()) {
                                    // 先弹确认框，确认后才执行批量删除
                                    pendingDeleteNames = selectedCourseNames.toList()
                                }
                            },
                            // 选中数量为 0 时禁用删除按钮
                            enabled = selectedCourseNames.isNotEmpty()
                        ) {
                            Icon(vectorResource(Res.drawable.delete_24px), contentDescription = stringResource(Res.string.a11y_delete))
                        }
                    }

                    // 3. 常驻的切换多选模式按钮 (MenuOpen)
                    IconButton(
                        onClick = {
                            if (isSelectionMode) {
                                // 当前是多选模式，点击退出
                                exitSelectionMode()
                            } else {
                                // 当前是正常模式，点击进入多选模式
                                isSelectionMode = true
                            }
                        }
                    ) {
                        val descriptionRes = if (isSelectionMode) {
                            Res.string.a11y_exit_selection_mode
                        } else {
                            Res.string.a11y_enter_selection_mode
                        }
                        Icon(vectorResource(Res.drawable.menu_open_24px), contentDescription = stringResource(descriptionRes))
                    }
                }
            )
        },
        floatingActionButton = {
            // 多选模式下隐藏 FAB（统一 AppFab：主色圆形 + 按压缩放）
            if (!isSelectionMode) {
                AppFab(
                    onClick = {
                        // 启动协程，先发送默认数据，再导航
                        coroutineScope.launch {
                            // 1. 创建包含默认节次 (1-2) 的预设数据
                            val defaultPresetData = PresetCourseData(
                                startSection = 1,
                                endSection = 2
                            )

                            // 2. 发送数据到 Channel，解除 AddEditCourseViewModel 的阻塞
                            AddEditCourseChannel.sendEvent(defaultPresetData)

                            onNavigate(Destination.AddEditCourse(courseId = null))
                        }
                    },
                    icon = vectorResource(Res.drawable.add_24px),
                    contentDescription = stringResource(Res.string.action_add),
                    hazeState = hazeState
                )
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).hazeSource(hazeState)) {
            QuickActionsSection(
                modifier = Modifier.fillMaxWidth(),
                onNavigate = onNavigate
            )

        if (uniqueCourseNames.isEmpty()) {
            // 统一空状态：淡灰胶囊 + 辅助文案
            AppEmptyState(
                hint = stringResource(Res.string.text_no_unique_courses_hint),
                fillScreen = true
            )
        } else {
            // LazyVerticalGrid 实现两列网格布局
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uniqueCourseNames, key = { it.name }) { item ->
                    val isSelected = item.name in selectedCourseNames

                    CourseNameCard(
                        name = item.name,
                        instanceCount = item.count,
                        isSelected = isSelected,
                        onCourseClick = { clickedName ->
                            if (isSelectionMode) {
                                if (selectedCourseNames.contains(clickedName)) {
                                    selectedCourseNames.remove(clickedName)
                                } else {
                                    selectedCourseNames.add(clickedName)
                                }
                            } else {
                                onNavigate(Destination.CourseManagementDetail(courseName = clickedName))
                            }
                        },
                        onCourseLongClick = { clickedName ->
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedCourseNames.add(clickedName)
                            }
                        }
                    )
                }
            }
        }
        }
    }

    // 批量删除二次确认（删除不可撤销）
    pendingDeleteNames?.let { names ->
        AppDangerDialog(
            onDismissRequest = { pendingDeleteNames = null },
            title = stringResource(Res.string.dialog_title_confirm_delete_course),
            text = stringResource(Res.string.dialog_text_confirm_delete_courses, names.size),
            confirmText = stringResource(Res.string.confirm_delete),
            onConfirm = {
                pendingDeleteNames = null
                coroutineScope.launch {
                    viewModel.deleteSelectedCourses(names)
                    exitSelectionMode()
                }
            }
        )
    }
}

/**
 * 课程名称卡片 Composable
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CourseNameCard(
    name: String,
    instanceCount: Int,
    isSelected: Boolean,
    onCourseClick: (String) -> Unit,
    onCourseLongClick: (String) -> Unit
) {
    // 统一选中态（AppSelectableCard：primarySoft 底 + 2dp 主色描边）
    AppSelectableCard(
        selected = isSelected,
        onClick = { onCourseClick(name) },
        onLongClick = { onCourseLongClick(name) },
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // 课程名称和实例数量
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 32.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else appColors().textPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            }

            // 右下角：实例数量 Badge（Telegram 灰底白字胶囊徽标，v2 规范 §4.4）
            Badge(
                content = { Text(instanceCount.toString(), style = MaterialTheme.typography.labelSmall) },
                containerColor = appColors().badgeBg,
                contentColor = appColors().badgeFg,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .sizeIn(minWidth = 20.dp, minHeight = 20.dp)
            )
        }
    }
}


/**
 * 快捷操作区块：课程调动 / 快速删除课程。
 * 并入课程管理页，统一使用设置语言（AppSectionHeader + SettingCard）。
 */
@Composable
private fun QuickActionsSection(
    modifier: Modifier = Modifier,
    onNavigate: (Destination) -> Unit
) {
    Column(modifier = modifier.padding(horizontal = appSpacing().pageHorizontal, vertical = 8.dp)) {
        AppSectionHeader(stringResource(Res.string.label_quick_action_category_schedule))
        SettingCard(
            title = stringResource(Res.string.item_schedule_tweak),
            subtitle = stringResource(Res.string.desc_schedule_tweak),
            leadingIcon = vectorResource(Res.drawable.swap_horiz_24px),
            accent = AccentTone.INFO,
            onClick = { onNavigate(Destination.TweakSchedule) }
        )
        Spacer(modifier = Modifier.height(appSpacing().cardGap))
        SettingCard(
            title = stringResource(Res.string.item_quick_delete),
            subtitle = stringResource(Res.string.quick_delete_subtitle),
            leadingIcon = vectorResource(Res.drawable.delete_24px),
            accent = AccentTone.DANGER,
            onClick = { onNavigate(Destination.QuickDelete) }
        )
    }
}
