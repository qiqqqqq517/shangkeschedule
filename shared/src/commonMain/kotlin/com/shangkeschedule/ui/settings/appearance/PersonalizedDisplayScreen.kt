package com.shangkeschedule.ui.settings.appearance

import com.shangkeschedule.ui.components.AppTopAppBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shangkeschedule.Destination
import com.shangkeschedule.ui.settings.SettingCard
import com.shangkeschedule.ui.theme.AccentTone
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.animation_24px
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.blur_on_24px
import shangkeschedule.shared.generated.resources.desc_animation_settings
import shangkeschedule.shared.generated.resources.desc_course_color_settings
import shangkeschedule.shared.generated.resources.desc_glass_blur_settings
import shangkeschedule.shared.generated.resources.item_animation_settings
import shangkeschedule.shared.generated.resources.item_course_color_settings
import shangkeschedule.shared.generated.resources.item_glass_blur_settings
import shangkeschedule.shared.generated.resources.item_personalized_display
import shangkeschedule.shared.generated.resources.palette_24px
import shangkeschedule.shared.generated.resources.item_next_card_settings
import shangkeschedule.shared.generated.resources.desc_next_card_settings
import shangkeschedule.shared.generated.resources.view_agenda_24px

/**
 * 「外观与样式 → 个性化显示」二级页（v3.26.0 起为 hub）。
 *
 * 职责边界（与 v3.24.0 拆分口径一致）：
 * - 主题页 = 配色；自定义课表页 = 课表本体；
 * - 本页 = 悬浮件那一层 + 全局动效 + 课程配色，向下拆三级卡：
 *   ① 玻璃模糊（[GlassBlurScreen]，v3.25.0 内容原样下沉）；
 *   ② 动画效果（[AnimationSettingsScreen]，v3.26.0 新增：三风格 × 六分组开关）；
 *   ③ 个性化配色（[CourseColorSettingsScreen]：颜色池 + 课程块/页面文字颜色 + 一键重置配色，
 *      自「自定义课表页」整块迁入）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalizedDisplayScreen(
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit = {}
) {
    Scaffold(
        topBar = {
            AppTopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.item_personalized_display),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            vectorResource(Res.drawable.arrow_back_24px),
                            contentDescription = stringResource(Res.string.a11y_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingCard(
                title = stringResource(Res.string.item_glass_blur_settings),
                subtitle = stringResource(Res.string.desc_glass_blur_settings),
                leadingIcon = vectorResource(Res.drawable.blur_on_24px),
                accent = AccentTone.SUCCESS,
                onClick = { onNavigate(Destination.GlassBlurSettings) }
            )
            SettingCard(
                title = stringResource(Res.string.item_animation_settings),
                subtitle = stringResource(Res.string.desc_animation_settings),
                leadingIcon = vectorResource(Res.drawable.animation_24px),
                accent = AccentTone.INFO,
                onClick = { onNavigate(Destination.AnimationSettings) }
            )
            // 课程配色自「自定义课表页」整块迁入，独立成页：颜色池 + 课程块/页面文字颜色 + 一键重置配色
            SettingCard(
                title = stringResource(Res.string.item_course_color_settings),
                subtitle = stringResource(Res.string.desc_course_color_settings),
                leadingIcon = vectorResource(Res.drawable.palette_24px),
                accent = AccentTone.PRIMARY,
                onClick = { onNavigate(Destination.CourseColorSettings) }
            )
            // 下节课卡：今日课程结束后卡片如何显示（自动下一次 / 已结束变淡 / 消失），v3.47.0 新增
            SettingCard(
                title = stringResource(Res.string.item_next_card_settings),
                subtitle = stringResource(Res.string.desc_next_card_settings),
                leadingIcon = vectorResource(Res.drawable.view_agenda_24px),
                accent = AccentTone.INFO,
                onClick = { onNavigate(Destination.NextCardSettings) }
            )
        }
    }
}
