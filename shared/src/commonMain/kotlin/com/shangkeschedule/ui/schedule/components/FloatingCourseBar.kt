package com.shangkeschedule.ui.schedule.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.shangkeschedule.data.db.main.CourseWithWeeks
import com.shangkeschedule.data.model.AppThemePreset
import com.shangkeschedule.ui.theme.AnimationGroup
import com.shangkeschedule.ui.theme.LocalAppMotion
import com.shangkeschedule.ui.theme.LocalThemePreset
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.claudeGroupBg
import com.shangkeschedule.ui.theme.claudeGroupBorder
import com.shangkeschedule.ui.theme.liquidGlass
import com.shangkeschedule.ui.theme.softFeatherRim
import com.shangkeschedule.ui.theme.softGlow
import com.shangkeschedule.ui.theme.softShadow
import dev.chrisbanes.haze.HazeState
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.action_cancel
import shangkeschedule.shared.generated.resources.archive_24px
import shangkeschedule.shared.generated.resources.close_24px
import shangkeschedule.shared.generated.resources.floating_course_hint

/**
 * 课程挂起悬浮条（左下角胶囊）。
 *
 * v3.23.5 起改为液态玻璃形态（与玻璃底栏 /「回到本周」圆钮同源玻璃语言）：
 * 表面极淡 + 轻模糊 + 边缘光学，底层课表内容可透出。
 * [contentColor] 由调用方传入（跟随页面自定义文字色/壁纸模式），保证玻璃上图标可读。
 */
@Composable
fun FloatingCourseBar(
    floatingCourse: CourseWithWeeks?,
    onCancelClick: () -> Unit,
    hazeState: HazeState,
    contentColor: Color,
    isTransparent: Boolean = false,
    modifier: Modifier = Modifier
) {
    // v3.26.0 动效收口：出现/消失读全局动效令牌；关掉「玻璃悬浮件」分组 ⇒ 瞬切无动画
    val motion = LocalAppMotion.current
    val glassAnimEnabled = motion.isEnabled(AnimationGroup.GLASS_FLOATING)
    AnimatedVisibility(
        visible = floatingCourse != null,
        enter = if (glassAnimEnabled) {
            fadeIn(motion.tokens.emphasisFadeSpec) +
                scaleIn(motion.tokens.emphasisScaleSpec, initialScale = motion.tokens.emphasisInitialScale)
        } else {
            EnterTransition.None
        },
        exit = if (glassAnimEnabled) {
            fadeOut(motion.tokens.emphasisFadeSpec) +
                scaleOut(motion.tokens.emphasisScaleSpec, targetScale = motion.tokens.emphasisInitialScale)
        } else {
            ExitTransition.None
        },
        modifier = modifier.zIndex(10f)
    ) {
        floatingCourse?.let { cw ->
            val isClaude = LocalThemePreset.current == AppThemePreset.CLAUDE
            val isSoft = LocalThemePreset.current == AppThemePreset.SOFT
            // 书卷：不透明分组底 + 实色描边；通透（iOS 26）：与底栏胶囊同源的 Liquid Glass；
            // 柔绘：薄涂主色淡底 + 软模糊投影 + 漫射柔光 + 羽化描边（无实色描边、无锐利硬边缘）
            val surfaceModifier = when {
                isClaude -> Modifier
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        clip = false,
                        ambientColor = appColors().shadow,
                        spotColor = appColors().shadow
                    )
                    .clip(CircleShape)
                    .background(claudeGroupBg())
                    .border(0.5.dp, claudeGroupBorder(), CircleShape)
                isSoft -> Modifier
                    .softShadow(shape = CircleShape, elevation = 10.dp)
                    .clip(CircleShape)
                    .background(appColors().cardBg)
                    .softGlow(CircleShape)
                    .softFeatherRim(CircleShape)
                else -> Modifier.liquidGlass(
                    hazeState = hazeState,
                    shape = CircleShape,
                    containerColor = appColors().inputBg,
                    isTransparent = isTransparent,
                    shadowElevation = 10.dp
                )
            }
            // 柔绘：悬浮条是页面内的卡片件，文字色回落到语义色（与书卷同口径）；
            // 柔绘低对比配色下，自定义 contentColor（壁纸模式）不保证在薄涂底上的可读性。
            val titleColor = if (isClaude || isSoft) appColors().textPrimary else contentColor
            val accentColor = if (isClaude || isSoft) appColors().primary else contentColor
            Row(
                modifier = surfaceModifier
                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = vectorResource(Res.drawable.archive_24px),
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.widthIn(max = 180.dp)) {
                    Text(
                        text = cw.course.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(Res.string.floating_course_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = titleColor.copy(alpha = 0.8f)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onCancelClick,
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = titleColor.copy(alpha = 0.12f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.close_24px),
                        contentDescription = stringResource(Res.string.action_cancel),
                        tint = titleColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
