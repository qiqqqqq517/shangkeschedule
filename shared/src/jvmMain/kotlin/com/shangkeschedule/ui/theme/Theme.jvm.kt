package com.shangkeschedule.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import com.shangkeschedule.data.model.AppThemeMode

@Composable
actual fun SetupPlatformThemeEffects(
    colorScheme: ColorScheme,
    darkTheme: Boolean,
    themeMode: AppThemeMode
) {
    // Desktop 端窗口外观交由 Compose Desktop 的 Window 属性控制，此处无需额外处理
    // 如要实现自绘边框需要补充实现
}
