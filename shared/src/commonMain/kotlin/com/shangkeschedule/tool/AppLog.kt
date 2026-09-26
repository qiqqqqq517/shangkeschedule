package com.shangkeschedule.tool

/**
 * 统一的轻量日志入口。
 *
 * 历史代码里散落着大量 `e.printStackTrace()`：直接写标准错误流、没有 tag、无法按模块过滤，
 * 且 catch 块的语义（可降级 / 需排查）完全看不出来。这里收敛为一个入口，
 * 各平台 actual 决定输出目的地与格式（Android 走 android.util.Log）。
 */
expect object AppLog {

    /** 警告：异常已被处理并降级，功能可用性可能受影响。 */
    fun w(tag: String, message: String, throwable: Throwable? = null)

    /** 错误：本次操作失败，需要排查。 */
    fun e(tag: String, message: String, throwable: Throwable? = null)
}
