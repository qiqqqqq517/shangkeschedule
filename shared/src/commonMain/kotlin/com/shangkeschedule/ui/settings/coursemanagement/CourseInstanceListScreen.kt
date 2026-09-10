package com.shangkeschedule.ui.settings.coursemanagement

import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangkeschedule.Destination
import com.shangkeschedule.data.db.main.CourseWithWeeks
import com.shangkeschedule.data.model.DualColor
import com.shangkeschedule.navigation.AddEditCourseChannel
import com.shangkeschedule.navigation.PresetCourseData
import com.shangkeschedule.ui.components.AppTopAppBar
import com.shangkeschedule.ui.components.AppDangerDialog
import com.shangkeschedule.ui.components.AppFab
import com.shangkeschedule.ui.components.AppSelectableCard
import com.shangkeschedule.ui.schedule.components.adaptiveTextColor
import com.shangkeschedule.ui.theme.LocalIsDarkTheme
import com.shangkeschedule.ui.theme.appSpacing
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
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
import shangkeschedule.shared.generated.resources.course_time_day_section_details_tweak
import shangkeschedule.shared.generated.resources.course_time_day_time_details_tweak
import shangkeschedule.shared.generated.resources.delete_24px
import shangkeschedule.shared.generated.resources.label_weeks_format
import shangkeschedule.shared.generated.resources.menu_open_24px
import shangkeschedule.shared.generated.resources.title_selected_items_count
import shangkeschedule.shared.generated.resources.week_days_full_names
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * 二级页面：展示特定课程名称下的所有实例，使用两列网格 (Detail View)。
 * @param courseName 从导航参数中接收，用于 ViewModel 过滤。
 * @param onNavigateBack 导航回上一级
 * @param onNavigate 用于在 Composable 内部处理 FAB 和卡片点击时的导航。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseInstanceListScreen(
    courseName: String,
    onNavigateBack: () -> Unit,
    onNavigate: (Destination) -> Unit,
    viewModel: CourseInstanceListViewModel = koinViewModel()
) {
    LaunchedEffect(courseName) {
        viewModel.initCourseName(courseName)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val courseInstances by viewModel.courseInstances.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedCourseIds by viewModel.selectedCourseIds.collectAsState()
    val scope = rememberCoroutineScope()
    // 液态玻璃 FAB 背板采样：内容 hazeSource，FAB 玻璃模糊其背后的网格
    val hazeState = rememberHazeState()
    // 批量删除二次确认（删除不可撤销）
    var pendingDeleteConfirm by remember { mutableStateOf(false) }

    val onNavigateToAddNewCourse: () -> Unit = {
        scope.launch {
            val presetData = PresetCourseData(
                name = courseName,
                startSection = 1,
                endSection = 2
            )
            AddEditCourseChannel.sendEvent(presetData)
            onNavigate(Destination.AddEditCourse(courseId = null))
        }
    }

    // 编辑课程逻辑
    val onNavigateToEditCourse: (courseId: String) -> Unit = { courseId ->
        onNavigate(Destination.AddEditCourse(courseId = courseId))
    }

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = {
                    Text(
                        if (isSelectionMode) {
                            stringResource(Res.string.title_selected_items_count, selectedCourseIds.size)
                        } else {
                            courseName
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = if (isSelectionMode) viewModel::toggleSelectionMode else onNavigateBack) {
                        Icon(
                            if (isSelectionMode) vectorResource(Res.drawable.close_24px) else vectorResource(Res.drawable.arrow_back_24px),
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        val totalCount = courseInstances.size
                        val selectedCount = selectedCourseIds.size
                        val isAllSelected = totalCount > 0 && selectedCount == totalCount

                        IconButton(onClick = viewModel::toggleSelectAll, enabled = totalCount > 0) {
                            val selectAllStringRes = if (isAllSelected) Res.string.action_deselect_all else Res.string.action_select_all
                            Icon(vectorResource(Res.drawable.check_24px), contentDescription = stringResource(selectAllStringRes))
                        }

                        IconButton(onClick = { pendingDeleteConfirm = true }, enabled = selectedCount > 0) {
                            Icon(vectorResource(Res.drawable.delete_24px), contentDescription = stringResource(Res.string.a11y_delete))
                        }
                    }

                    IconButton(
                        onClick = viewModel::toggleSelectionMode,
                        enabled = courseInstances.isNotEmpty() || isSelectionMode
                    ) {
                        val descriptionRes = if (isSelectionMode) Res.string.a11y_exit_selection_mode else Res.string.a11y_enter_selection_mode
                        Icon(vectorResource(Res.drawable.menu_open_24px), contentDescription = stringResource(descriptionRes))
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                // 统一 AppFab（主色圆形 + 按压缩放）
                AppFab(
                    onClick = onNavigateToAddNewCourse,
                    icon = vectorResource(Res.drawable.add_24px),
                    contentDescription = stringResource(Res.string.action_add),
                    hazeState = hazeState
                )
            }
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(paddingValues).hazeSource(hazeState),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(courseInstances, key = { it.course.id }) { courseWithWeeks ->
                CourseInstanceCard(
                    courseWithWeeks = courseWithWeeks,
                    isSelected = selectedCourseIds.contains(courseWithWeeks.course.id),
                    colorMaps = uiState.courseColorMaps,
                    onCourseClick = { courseId ->
                        if (isSelectionMode) {
                            viewModel.toggleCourseSelection(courseId)
                        } else {
                            onNavigateToEditCourse(courseId)
                        }
                    },
                    onCourseLongClick = { courseId ->
                        if (!isSelectionMode) viewModel.toggleSelectionMode()
                        viewModel.toggleCourseSelection(courseId)
                    }
                )
            }
        }
    }

    // 批量删除二次确认（删除不可撤销）
    if (pendingDeleteConfirm) {
        AppDangerDialog(
            onDismissRequest = { pendingDeleteConfirm = false },
            title = stringResource(Res.string.dialog_title_confirm_delete_course),
            text = stringResource(Res.string.dialog_text_confirm_delete_courses, selectedCourseIds.size),
            confirmText = stringResource(Res.string.confirm_delete),
            onConfirm = {
                pendingDeleteConfirm = false
                viewModel.deleteSelectedCourses()
            }
        )
    }
}

/**
 * 课程实例卡片 Composable (两列网格中的单个卡片)。
 */
@Composable
fun CourseInstanceCard(
    courseWithWeeks: CourseWithWeeks,
    isSelected: Boolean,
    colorMaps: List<DualColor>,
    onCourseClick: (courseId: String) -> Unit,
    onCourseLongClick: (courseId: String) -> Unit
) {
    val course = courseWithWeeks.course
    val courseId = course.id

    val isDarkTheme = LocalIsDarkTheme.current

    // 如果索引不存在，则取列表第一项；如果列表为空，则使用 MaterialTheme 的 SurfaceVariant 颜色兜底
    val fallbackColor = DualColor(
        light = MaterialTheme.colorScheme.surfaceVariant,
        dark = MaterialTheme.colorScheme.surfaceVariant
    )
    val courseColorDual = colorMaps.getOrNull(course.colorInt) ?: colorMaps.firstOrNull() ?: fallbackColor

    // 根据主题获取课程背景色
    val courseBackgroundColor = if (isDarkTheme) courseColorDual.dark else courseColorDual.light

    val weekDays = stringArrayResource(Res.array.week_days_full_names)
    val dayName = weekDays.getOrElse(course.day - 1) { "?" }

    // 卡片颜色：始终使用课程颜色作为背景；文字色按底色明暗自适应（保证深色课程底上的可读性）
    val contentColor = adaptiveTextColor(
        background = courseBackgroundColor,
        fallback = MaterialTheme.colorScheme.onSurface
    )

    AppSelectableCard(
        selected = isSelected,
        onClick = { onCourseClick(courseId) },
        onLongClick = { onCourseLongClick(courseId) },
        modifier = Modifier.height(IntrinsicSize.Max),
        containerColor = courseBackgroundColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = appSpacing().cardInner, vertical = appSpacing().cardInner)
        ) {
            // 教师信息
            Text(
                text = course.teacher,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            // 地点信息
            Text(
                text = course.position,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 上课时间/节次
            val timeText = if (course.isCustomTime) {
                stringResource(
                    Res.string.course_time_day_time_details_tweak,
                    dayName,
                    course.customStartTime ?: "?",
                    course.customEndTime ?: "?"
                )
            } else {
                stringResource(
                    Res.string.course_time_day_section_details_tweak,
                    dayName,
                    course.startSection ?: "?",
                    course.endSection ?: "?"
                )
            }

            Text(
                text = timeText,
                style = MaterialTheme.typography.bodyMedium
            )

            // 周次信息
            val formattedWeeks = courseWithWeeks.weeks.map { it.weekNumber }.joinToString(", ")
            Text(
                text = stringResource(
                    Res.string.label_weeks_format,
                    formattedWeeks
                ),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}