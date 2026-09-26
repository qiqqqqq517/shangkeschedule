package com.shangkeschedule.ui.schedule.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shangkeschedule.ui.components.AppEmptyState
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appShapes
import org.jetbrains.compose.resources.stringResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.ia5_other_ways
import shangkeschedule.shared.generated.resources.ia5_step_done
import shangkeschedule.shared.generated.resources.ia5_step_login
import shangkeschedule.shared.generated.resources.ia5_step_select_school
import shangkeschedule.shared.generated.resources.import_file_hub_title
import shangkeschedule.shared.generated.resources.item_school_system_import
import shangkeschedule.shared.generated.resources.text_no_courses_this_week
import shangkeschedule.shared.generated.resources.title_add_course

/**
 * IA5 首启引导（方案 B · 增强空态）：课表为空时给出的「三步添加课表」引导。
 *
 * 为什么不做全屏 Onboarding：真正决定"要不要引导"的不是"第几次启动"，而是
 * **"有没有课表"** —— 按数据判断就无需新增首启 flag、无状态迁移、对老用户零干扰；
 * 而且它能**反复触达**（全屏引导最典型的失败是"用户当时跳过了，之后再也找不到"）。
 * 详见 docs/ia5-onboarding-design.md。
 *
 * 设计要点：
 * - 步骤条仅为**视觉引导**，不可点击，不做分步状态机（避免把简单问题复杂化）；
 * - 主按钮只指向覆盖面最广的**教务系统导入**；文件导入与手动添加收在
 *   「其他添加方式」展开项里 —— 四条路径平铺首屏正是"不知道点哪个"的来源；
 * - 任何一步都可跳过，不阻塞用户去别的页面。
 */
@Composable
fun AddScheduleGuide(
    onSchoolImport: () -> Unit,
    onFileImport: () -> Unit,
    onManualAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = appColors()
    val steps = listOf(
        stringResource(Res.string.ia5_step_select_school),
        stringResource(Res.string.ia5_step_login),
        stringResource(Res.string.ia5_step_done),
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppEmptyState(hint = stringResource(Res.string.text_no_courses_this_week))

        Spacer(Modifier.height(14.dp))

        // 三步示意：序号徽标 + 文案，用「›」分隔
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            steps.forEachIndexed { index, label ->
                if (index > 0) {
                    Text(
                        text = "›",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(colors.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onSchoolImport,
            shape = appShapes().capsule,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.textOnPrimary
            )
        ) {
            Text(stringResource(Res.string.item_school_system_import))
        }

        TextButton(onClick = { expanded = !expanded }) {
            Text(
                text = stringResource(Res.string.ia5_other_ways),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary
            )
        }

        if (expanded) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onFileImport) {
                    Text(
                        text = stringResource(Res.string.import_file_hub_title),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary
                )
                TextButton(onClick = onManualAdd) {
                    Text(
                        text = stringResource(Res.string.title_add_course),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
