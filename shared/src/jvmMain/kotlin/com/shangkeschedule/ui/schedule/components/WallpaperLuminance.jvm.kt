package com.shangkeschedule.ui.schedule.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

/**
 * Desktop（JVM）侧壁纸亮度采样。
 *
 * 当前**未实现**像素采样：直接复用「亮度未知」兜底，文字色维持原样。
 * 若要补齐，可在此用 `javax.imageio.ImageIO.read(File(path))` 读图后复用
 * [averageLuminanceFrom] 求平均亮度（与 Android 侧同一套纯函数口径）。
 */
@Composable
actual fun rememberWallpaperLuminance(imagePath: String): State<Float?> =
    rememberWallpaperLuminanceUnsupported(imagePath)