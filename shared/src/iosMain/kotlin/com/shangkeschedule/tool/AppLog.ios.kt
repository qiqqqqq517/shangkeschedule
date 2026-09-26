package com.shangkeschedule.tool

/**
 * iOS 端日志：Kotlin/Native 侧没有平台日志封装，统一写标准输出（Console.app 可查看）。
 */
actual object AppLog {

    actual fun w(tag: String, message: String, throwable: Throwable?) {
        println("[W][$tag] $message")
        throwable?.stackTraceToString()?.let { println(it) }
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        println("[E][$tag] $message")
        throwable?.stackTraceToString()?.let { println(it) }
    }
}
