package com.shangkeschedule.ui.schedule.components

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * Android 侧壁纸亮度采样（actual 实现）。
 *
 * 做法：`BitmapFactory` 带 `inSampleSize` **降采样解码**（只取缩略图，不把整张原图读进内存），
 * 再按固定步长抽点累加 RGB，交给纯函数 [averageLuminanceFrom] 求平均亮度。
 *
 * 为什么要降采样：壁纸可能是 4000×3000 的原图，全量解码约 48MB 位图，
 * 而判定明暗只需要几百个采样点。降采样后内存占用降到几十 KB 量级。
 *
 * 失败一律返回 `null`（亮度未知），调用方回退主题色 —— 采样只是「锦上添花」，
 * 任何异常都不应影响课表正常显示。
 */
@Composable
actual fun rememberWallpaperLuminance(imagePath: String): State<Float?> {
    return produceState<Float?>(initialValue = null, key1 = imagePath) {
        value = if (imagePath.isEmpty()) {
            null
        } else {
            runCatching { sampleAverageLuminance(imagePath) }.getOrNull()
        }
    }
}

/**
 * 对本地图片文件采样平均亮度。
 *
 * @return 平均亮度 [0,1]；文件不存在 / 解码失败 / 无有效像素时返回 null。
 */
private fun sampleAverageLuminance(imagePath: String): Float? {
    val file = File(imagePath)
    if (!file.exists() || !file.isFile) return null

    // 第一步：只读尺寸，据此算降采样倍率（目标最长边约 128px）。
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(imagePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val targetLongSide = 128
    var sampleSize = 1
    val longSide = maxOf(bounds.outWidth, bounds.outHeight)
    while (longSide / (sampleSize * 2) >= targetLongSide) {
        sampleSize *= 2
    }

    // 第二步：按倍率解码缩略图（ARGB_8888 保证通道值可直接取用）。
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
    }
    val bitmap = BitmapFactory.decodeFile(imagePath, options) ?: return null

    // Bitmap 不实现 AutoCloseable，用 try/finally 显式回收，避免降采样位图滞留。
    return try {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null

        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var count = 0L

        // 逐像素累加。降采样后最多约 128×128 = 16384 个点，开销可忽略；
        // 若仍偏大则再按步长抽点，保证上限。
        val step = if (width * height > 16_384) 2 else 1
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val pixel = bitmap.getPixel(x, y)
                sumR += (pixel shr 16) and 0xFF
                sumG += (pixel shr 8) and 0xFF
                sumB += pixel and 0xFF
                count++
                x += step
            }
            y += step
        }

        averageLuminanceFrom(sumR, sumG, sumB, count)
    } finally {
        bitmap.recycle()
    }
}