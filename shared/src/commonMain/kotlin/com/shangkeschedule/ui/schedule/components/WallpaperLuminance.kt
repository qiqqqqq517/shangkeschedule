package com.shangkeschedule.ui.schedule.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 采样壁纸平均亮度（expect 侧）。
 *
 * 返回值语义：`null` = 亮度未知（未采样完 / 采样失败 / 无壁纸 / 平台暂无实现），
 * 调用方据此回退到主题色，**不会因为采样失败而改变界面现状**。
 *
 * @param imagePath 壁纸的本地文件路径；为空表示未设壁纸。
 */
@Composable
expect fun rememberWallpaperLuminance(imagePath: String): State<Float?>

/**
 * 无平台实现时的通用兜底：始终返回「亮度未知」。
 *
 * 供 iOS / Desktop 等尚未实现像素采样的平台复用，保证三端都能编译且行为一致
 * （不采样 ⇒ 文字色维持原样，不会比现状更差）。
 */
@Composable
internal fun rememberWallpaperLuminanceUnsupported(imagePath: String): State<Float?> {
    // 读一下 imagePath 以便将来需要时触发重组；当前恒为 null。
    val state = remember(imagePath) { mutableStateOf<Float?>(null) }
    return state
}