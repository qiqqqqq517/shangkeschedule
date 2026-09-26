package com.shangkeschedule.ui.schedule.components

import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appShapes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.shangkeschedule.ui.components.AppGlassBottomSheet
import com.shangkeschedule.ui.schedule.MergedCourseBlock
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.action_double_week
import shangkeschedule.shared.generated.resources.action_single_week
import shangkeschedule.shared.generated.resources.label_assessment_method
import shangkeschedule.shared.generated.resources.label_credit
import shangkeschedule.shared.generated.resources.label_is_lab
import shangkeschedule.shared.generated.resources.label_remark
import shangkeschedule.shared.generated.resources.label_section_range_suffix
import shangkeschedule.shared.generated.resources.today_meta_room
import shangkeschedule.shared.generated.resources.today_meta_teacher
import shangkeschedule.shared.generated.resources.today_meta_time
import shangkeschedule.shared.generated.resources.today_meta_weeks
import shangkeschedule.shared.generated.resources.today_sheet_close
import shangkeschedule.shared.generated.resources.today_sheet_edit
import shangkeschedule.shared.generated.resources.week_days_full_names

/**
 * 课程详情弹窗：与「今日日程」详情弹层的视觉语言对齐 ——
 * 顶部小节标题 + 大标题，圆角容器内 label/value 分隔行，底部「关闭/编辑课程」双按钮。
 * 仅承载课程字段展示与编辑入口，不改变任何数据与业务逻辑。
 *
 * [weekNumber] 与 [onTweakOccurrenceClick] 同时提供时，在底部按钮上方展示
 * 「调整本次课程」全宽次级入口（单次课程调整，仅本周生效）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailBottomSheet(
    block: MergedCourseBlock,
    onDismissRequest: () -> Unit,
    onEditClick: (String) -> Unit,
    hazeState: dev.chrisbanes.haze.HazeState? = null
) {
    val courseWrapper = block.courses.firstOrNull() ?: return
    val course = courseWrapper.course

    val colors = appColors()

    val weeksDisplayStr = formatWeeks(courseWrapper.weeks.map { it.weekNumber })

    val weekDaysFullNames = stringArrayResource(Res.array.week_days_full_names)
    val dayStr = remember(course.day, weekDaysFullNames) {
        weekDaysFullNames.getOrNull(course.day - 1) ?: ""
    }

    val labelTime = stringResource(Res.string.today_meta_time)
    val labelRoom = stringResource(Res.string.today_meta_room)
    val labelTeacher = stringResource(Res.string.today_meta_teacher)
    val labelWeeks = stringResource(Res.string.today_meta_weeks)
    val labelCredit = stringResource(Res.string.label_credit)
    val labelAssessment = stringResource(Res.string.label_assessment_method)
    val labelRemark = stringResource(Res.string.label_remark)
    val labelIsLab = stringResource(Res.string.label_is_lab)
    val textClose = stringResource(Res.string.today_sheet_close)
    val textEdit = stringResource(Res.string.today_sheet_edit)

    val sectionSuffix = stringResource(Res.string.label_section_range_suffix)
    val timeStr = if (course.isCustomTime) {
        "${course.customStartTime} - ${course.customEndTime}"
    } else {
        "${course.startSection ?: 0}-${course.endSection ?: 0} $sectionSuffix"
    }

    AppGlassBottomSheet(
        hazeState = hazeState,
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 40.dp)
        ) {
            // 顶部小节标题：星期作为轻量上下文
            Text(
                text = dayStr,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.1.em
                ),
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            // 大标题
            Text(
                text = course.name,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 圆角容器：label/value 分隔行
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(appShapes().chip)
                    .border(1.dp, colors.divider, appShapes().chip)
            ) {
                DetailRow(
                    label = labelTime,
                    value = timeStr
                )
                HorizontalDivider(thickness = 1.dp, color = colors.divider)
                if (course.position.isNotBlank()) {
                    DetailRow(label = labelRoom, value = course.position)
                    HorizontalDivider(thickness = 1.dp, color = colors.divider)
                }
                if (course.teacher.isNotBlank()) {
                    DetailRow(label = labelTeacher, value = course.teacher)
                    HorizontalDivider(thickness = 1.dp, color = colors.divider)
                }
                if (weeksDisplayStr.isNotEmpty()) {
                    DetailRow(label = labelWeeks, value = weeksDisplayStr)
                    HorizontalDivider(thickness = 1.dp, color = colors.divider)
                }
                if (!course.credit.isNullOrBlank()) {
                    DetailRow(label = labelCredit, value = course.credit)
                    HorizontalDivider(thickness = 1.dp, color = colors.divider)
                }
                if (!course.assessmentMethod.isNullOrBlank()) {
                    DetailRow(label = labelAssessment, value = course.assessmentMethod)
                }
                if (course.isLab) {
                    DetailRow(label = labelIsLab, value = "✓")
                }
                if (!course.remark.isNullOrBlank()) {
                    DetailRow(label = labelRemark, value = course.remark)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 底部按钮
            if (course.isCrush) {
                // crush 课程仅展示，禁止编辑 —— 只保留关闭按钮
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(appShapes().chip)
                        .background(colors.inputBg)
                        .clickable(onClick = onDismissRequest),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = textClose,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = colors.textPrimary
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(appShapes().chip)
                            .background(colors.inputBg)
                            .clickable(onClick = onDismissRequest),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = textClose,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = colors.textPrimary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(appShapes().chip)
                            .background(colors.primary)
                            .clickable(onClick = { onEditClick(course.id) }),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = textEdit,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = colors.textOnPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val colors = appColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.cardBgElevated)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            ),
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = colors.textPrimary,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun formatWeeks(weeks: List<Int>): String {
    if (weeks.isEmpty()) return ""
    val sorted = weeks.distinct().sorted()
    val result = mutableListOf<String>()

    val singleLabel = stringResource(Res.string.action_single_week)
    val doubleLabel = stringResource(Res.string.action_double_week)

    var i = 0
    while (i < sorted.size) {
        // 识别等差序列（单双周）
        if (i + 1 < sorted.size && sorted[i + 1] - sorted[i] == 2) {
            var k = i
            while (k + 1 < sorted.size && sorted[k + 1] - sorted[k] == 2) {
                k++
            }
            val suffix = if (sorted[i] % 2 != 0) singleLabel else doubleLabel
            result.add("${sorted[i]}-${sorted[k]}($suffix)")
            i = k + 1
        }
        // 识别连续区间
        else if (i + 1 < sorted.size && sorted[i + 1] == sorted[i] + 1) {
            val start = sorted[i]
            var k = i
            while (k + 1 < sorted.size && sorted[k + 1] == sorted[k] + 1) {
                k++
            }
            result.add("${start}-${sorted[k]}")
            i = k + 1
        }
        // 孤立的周次
        else {
            result.add("${sorted[i]}")
            i++
        }
    }
    return result.joinToString(", ")
}