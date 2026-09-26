package com.shangkeschedule.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import com.shangkeschedule.data.model.AppThemeMode

/**
 * 从任意 Context 中解包出宿主 Activity。
 *
 * [LocalView] 的 context 通常是 Activity，但在 Dialog / 自定义 ComposeView 等
 * 宿主下会是 ContextThemeWrapper；直接强转会在这些场景抛 ClassCastException。
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
actual fun SetupPlatformThemeEffects(
    colorScheme: ColorScheme,
    darkTheme: Boolean,
    themeMode: AppThemeMode
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // FIX: 原先直接 (view.context as Activity) 强转，非 Activity 宿主会崩溃
            val window = view.context.findActivity()?.window ?: return@SideEffect

            val backgroundColor = colorScheme.background.toArgb()
            window.setBackgroundDrawable(backgroundColor.toDrawable())

            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.Transparent.toArgb()

            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }
}
