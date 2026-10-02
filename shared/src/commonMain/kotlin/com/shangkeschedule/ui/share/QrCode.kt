package com.shangkeschedule.ui.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shangkeschedule.ui.theme.appColors
import qrcode.raw.ErrorCorrectionLevel
import qrcode.raw.QRCodeProcessor

/**
 * 课表分享串 → 二维码点阵。
 *
 * 只取二维码库的**矩阵**（`qrcode-kotlin` 的 `QRCodeProcessor.encode()`），不取它的 PNG 渲染：
 * `render()` 返回 PNG 字节，而 commonMain 没有跨端解码 PNG 的先例（JVM 用
 * `toComposeImageBitmap`、Android 用 `asImageBitmap`），自己画点阵反而三端一致。
 *
 * 分享串是 Base64 文本（字母数字 + `-` `_`），二维码密度等级从 M 开始试；
 * 放不下时降到 L 再试一次。仍然装不下就返回 null，由界面退到精简模式或提示用分享串。
 */
internal object QrCodeMatrix {

    /**
     * 版本 40（177×177 模块）是二维码的容量上限，也是实际能扫的极限：
     * 200~220dp 的面板上 177 模块每格约 1.1dp（约 3 物理像素），凑近仍能扫。
     * 实测 16 门课的**精简**分享串约 1350 字符 → 版本 25~30，可扫；
     * 完整分享串（含教师/备注/学分/作息）通常 4000+ 字符，超出容量，界面会退到精简或提示用分享串。
     */
    const val MAX_MODULES = 177

    private val LEVELS = listOf(ErrorCorrectionLevel.MEDIUM, ErrorCorrectionLevel.LOW)

    /** 版本 40 的字节模式容量（超出后库会直接返回 40 而不是失败，必须自己挡） */
    private const val CAPACITY_LOW = 2953
    private const val CAPACITY_MEDIUM = 2331

    fun of(text: String): Array<BooleanArray>? {
        if (text.isBlank()) return null
        LEVELS.forEach { level ->
            val matrix = runCatching { encode(text, level) }.getOrNull()
            if (matrix != null) return matrix
        }
        return null
    }

    private fun encode(text: String, level: ErrorCorrectionLevel): Array<BooleanArray>? {
        val capacity = if (level == ErrorCorrectionLevel.LOW) CAPACITY_LOW else CAPACITY_MEDIUM
        // 分享串是 Base64Url 纯 ASCII，字符数即字节数
        if (text.length > capacity) return null

        val raw = QRCodeProcessor(text, level).encode()
        val moduleCount = raw.size
        // 库在装不下时会返回最大版本（40）而不是抛异常，这里一并兜住。
        if (moduleCount <= 0 || moduleCount > MAX_MODULES) return null
        return Array(moduleCount) { row -> BooleanArray(moduleCount) { col -> raw[row][col].dark } }
    }
}

/** 二维码静区（quiet zone）模块数，规范要求 ≥4；这里用 2 配合卡片留白即可扫。 */
private const val QUIET_ZONE = 2

/**
 * 把 [matrix] 画成二维码。[sideLength] 是点阵边长（不含静区与内边距）。
 *
 * 底色固定白色而不是主题深色：二维码必须「浅底深块」才能被相机识别，
 * 深色主题下若跟随主题涂底会直接扫不出来。
 */
@Composable
internal fun QrCodePanel(
    matrix: Array<BooleanArray>,
    sideLength: Dp,
    modifier: Modifier = Modifier
) {
    val moduleColor = appColors().textPrimary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(12.dp)
    ) {
        Canvas(modifier = Modifier.size(sideLength)) {
            val total = matrix.size + QUIET_ZONE * 2
            val cell = minOf(size.width, size.height) / total
            val originX = (size.width - cell * total) / 2f
            val originY = (size.height - cell * total) / 2f
            matrix.forEachIndexed { row, cols ->
                cols.forEachIndexed { col, dark ->
                    if (dark) {
                        drawRect(
                            color = moduleColor,
                            topLeft = Offset(
                                originX + (col + QUIET_ZONE) * cell,
                                originY + (row + QUIET_ZONE) * cell
                            ),
                            size = Size(cell, cell)
                        )
                    }
                }
            }
        }
    }
}
