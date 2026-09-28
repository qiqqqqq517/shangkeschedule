package com.shangkeschedule.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * 系统底部安全区（系统导航栏 / 手势条）——**全 App 唯一取值口径**。
 *
 * 缘起（v4.63.3 · Android 10 课表被系统导航栏遮挡）：全局 `enableEdgeToEdge()` 让内容
 * 铺满屏幕并压在系统导航栏之下，底部留白一直由「App 自有底栏占位 + 系统 inset」合成。
 * 真正的 bug 是：**系统 inset 被并入了底栏的隐藏动画** —— 内容底部 padding 写成
 * `(底栏高 + 系统 inset) × (1 - hideFraction)`，底栏下滑隐藏到 `hideFraction = 1` 时整个
 * padding 归零，连系统导航栏那一截也一起收回了，内容的可滚下限因此伸进系统导航栏底下，
 * 末节课程被压住**且再也滚不上来**。系统 inset 必须**恒定保留**，只有「App 底栏自身那块」
 * 才随隐藏收缩（见 `NavigationComponents.kt`）。Android 11+ 手势导航系统区很窄故症状轻微，
 * Android 10 三键导航 48dp 实心导航栏则整块压住课表。
 *
 * ⚠️ 取值口径（v4.63.3 修正过一次）：**以 `navigationBars` 为主，仅在它报 0 时才用
 * `systemGestures` 兜底**，绝不是 `maxOf(...)`。
 * 手势导航机型上 `systemGestures` 的底部手势区通常比 `navigationBars` **高一截**（前者含
 * 强制手势区），做并集等于凭空把系统安全区加宽 —— 表现为底栏被整体抬高、离系统导航栏更远，
 * 与「贴住系统导航栏」的目标正好相反。`navigationBars` 报 0 是真实存在的异常形态
 * （部分 API 29 ROM 的手势导航少报），此时用 `systemGestures` 补位才是安全的。
 *
 * @return 系统底部安全区高度（px），供 `graphicsLayer` 位移等需要 px 的场合使用。
 */
@Composable
fun systemBottomInsetPx(): Int {
    val density = LocalDensity.current
    val navigationBarsBottom = WindowInsets.navigationBars.getBottom(density)
    return if (navigationBarsBottom > 0) {
        navigationBarsBottom
    } else {
        WindowInsets.systemGestures.getBottom(density)
    }
}

/** [systemBottomInsetPx] 的 Dp 版本，供 padding 使用。 */
@Composable
fun systemBottomInset(): Dp = with(LocalDensity.current) { systemBottomInsetPx().toDp() }
