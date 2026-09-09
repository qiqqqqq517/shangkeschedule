package com.shangkeschedule.ui.schedule.components

import com.shangkeschedule.ui.theme.appType

import com.shangkeschedule.ui.theme.appColors
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangkeschedule.ui.components.AppGlassBottomSheet
import org.jetbrains.compose.resources.stringResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.title_select_week

/**
 * 周选择器底部动作条（毛玻璃面板：传入 hazeState 时背板模糊，内容透出玻璃后）。
 *
 * @param totalWeeks 学期总周数。
 * @param currentWeek 当前的自然周数。
 * @param selectedWeek 当前选中的周次。
 * @param onWeekSelected 当用户选择周次时触发的回调。
 * @param onDismissRequest 当底部动作条被关闭时触发的回调。
 * @param hazeState 主窗口内容的 HazeState；null 时退化为实色面板。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekSelectorBottomSheet(
    totalWeeks: Int,
    currentWeek: Int?,
    selectedWeek: Int?,
    onWeekSelected: (Int) -> Unit,
    onDismissRequest: () -> Unit,
    hazeState: dev.chrisbanes.haze.HazeState? = null
) {
    val gridState = rememberLazyGridState()

    // 默认滚动到当前周
    LaunchedEffect(currentWeek) {
        if (currentWeek != null && currentWeek > 0) {
            // 注意：网格索引同样从 0 开始
            gridState.animateScrollToItem(currentWeek - 1)
        }
    }

    AppGlassBottomSheet(
        hazeState = hazeState,
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(Res.string.title_select_week),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = appType().sectionTitle,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.padding(16.dp)
            )

            // 网格状的周次选择器
            LazyVerticalGrid(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                columns = GridCells.Adaptive(minSize = 60.dp),
                state = gridState,
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(totalWeeks) { weekIndex ->
                    val weekNumber = weekIndex + 1
                    val isCurrentWeek = weekNumber == currentWeek
                    val isSelectedWeek = weekNumber == selectedWeek

                    // 根据周次状态决定颜色
                    val backgroundColor = when {
                        isSelectedWeek -> MaterialTheme.colorScheme.primary
                        isCurrentWeek -> appColors().primarySoft
                        else -> Color.Transparent
                    }
                    val textColor = when {
                        isSelectedWeek -> MaterialTheme.colorScheme.onPrimary
                        isCurrentWeek -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    Box(
                        modifier = Modifier
                            // 触控标准 ≥44dp（原 32dp 偏小，与全局 touchMin 语言靠拢）
                            .height(44.dp)
                            .clip(CircleShape)
                            .background(backgroundColor)
                            .clickable { onWeekSelected(weekNumber) }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$weekNumber",
                            color = textColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}