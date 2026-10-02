package com.shangkeschedule.tool

import android.os.Build

/**
 * Android 端实现：`Android 15 (API 35) · arm64-v8a`。
 *
 * `Build.VERSION.RELEASE` 在 API 35 起被标记为 deprecated（推荐用
 * `Build.VERSION.MEDIA_PERFORMANCE_CLASS` 等细分字段），但它的语义就是「对外显示的版本号」，
 * 正是排查问题需要的，因此这里显式压制警告而不是改用 SDK_INT 拼接。
 */
@Suppress("DEPRECATION")
actual fun systemDescription(): String = buildString {
    append("Android")
    val release = Build.VERSION.RELEASE.orEmpty()
    if (release.isNotBlank()) {
        append(' ').append(release)
    }
    append(" (API ").append(Build.VERSION.SDK_INT).append(')')
    val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
    if (abi.isNotBlank()) {
        append(" · ").append(abi)
    }
}
