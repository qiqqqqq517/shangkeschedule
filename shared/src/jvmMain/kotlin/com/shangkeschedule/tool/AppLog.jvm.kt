package com.shangkeschedule.tool

/**
 * 桌面端日志：没有 android.util.Log，统一带 tag 写标准错误流，异常仍保留堆栈便于排查。
 */
actual object AppLog {

    actual fun w(tag: String, message: String, throwable: Throwable?) {
        System.err.println("[W][$tag] $message")
        throwable?.printStackTrace()
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        System.err.println("[E][$tag] $message")
        throwable?.printStackTrace()
    }
}
