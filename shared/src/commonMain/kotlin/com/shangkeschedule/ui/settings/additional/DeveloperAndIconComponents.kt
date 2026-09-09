package com.shangkeschedule.ui.settings.additional

import com.shangkeschedule.ui.theme.LocalAppMotion
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.appShapes
import com.shangkeschedule.ui.theme.appSpacing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_app_icon
import shangkeschedule.shared.generated.resources.developer_mode_24px
import shangkeschedule.shared.generated.resources.ic_launcher_foreground
import shangkeschedule.shared.generated.resources.item_developer_options

// 图标背景颜色定义
// 功能色（豁免声明）：应用图标背景品牌色，固定不随主题
    private val NormalIconBgColor = Color(0xFF73CAF8)
    private val DeveloperIconBgColor = Color(0xFFBD0000)

/**
 * 动态 App 图标头部组件（包含连续 5 次点击解锁开发者模式逻辑）
 */
@Composable
fun DynamicAppIconHeader(
    isDeveloperModeEnabled: Boolean,
    onTriggerDeveloperMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    var clickCount by remember { mutableIntStateOf(0) }

    // 背景色过渡动画（v3.26.0 动效收口：时长读全局令牌 colorDurationMs）
    val targetColor = if (isDeveloperModeEnabled) DeveloperIconBgColor else NormalIconBgColor
    val motion = LocalAppMotion.current
    val animatedBgColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = motion.tokens.colorDurationMs)
    )

    Box(
        modifier = modifier
            .size(120.dp)
            .clip(appShapes().heroCard)
            .background(animatedBgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                clickCount++
                if (clickCount >= 5) {
                    clickCount = 0
                    onTriggerDeveloperMode()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_launcher_foreground),
            contentDescription = stringResource(Res.string.a11y_app_icon),
            modifier = Modifier
                .fillMaxSize()
                .scale(0.82f),
            contentScale = ContentScale.Fit
        )
    }
}

/**
 * 开发者模式列表选项组件（带展开/收起动画）
 */
@Composable
fun DeveloperModeSettingItem(
    isDeveloperModeEnabled: Boolean,
    onDeveloperModeChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // v3.26.0 动效收口：展开/收起时长与缓动读全局令牌 expandDurationMs/expandEasing
    val itemMotion = LocalAppMotion.current
    val expandFadeSpec = remember(itemMotion) {
        tween<Float>(durationMillis = itemMotion.tokens.expandDurationMs, easing = itemMotion.tokens.expandEasing)
    }
    val expandSizeSpec = remember(itemMotion) {
        tween<IntSize>(durationMillis = itemMotion.tokens.expandDurationMs, easing = itemMotion.tokens.expandEasing)
    }
    AnimatedVisibility(
        visible = isDeveloperModeEnabled,
        enter = fadeIn(expandFadeSpec) + expandVertically(expandSizeSpec),
        exit = fadeOut(expandFadeSpec) + shrinkVertically(expandSizeSpec),
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SettingListItem(
                icon = vectorResource(Res.drawable.developer_mode_24px),
                title = stringResource(Res.string.item_developer_options),
                onClick = { onDeveloperModeChanged(!isDeveloperModeEnabled) },
                showDivider = false,
                trailingContent = {
                    com.shangkeschedule.ui.components.AppSwitch(
                        checked = isDeveloperModeEnabled,
                        onCheckedChange = { onDeveloperModeChanged(it) }
                    )
                }
            )
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = appSpacing().pageHorizontal),
                color = appColors().divider,
                thickness = 0.5.dp
            )
        }
    }
}