package com.shangkeschedule.tool

/**
 * 桌面端（JVM）实现：`Windows 11 10.0 · JVM 17.0.9`。
 *
 * `os.name` / `os.version` 属于公开平台信息，不涉及硬件标识。
 */
actual fun systemDescription(): String {
    val osName = System.getProperty("os.name").orEmpty()
    val osVersion = System.getProperty("os.version").orEmpty()
    val javaVersion = System.getProperty("java.version").orEmpty()
    return buildString {
        append(osName)
        if (osVersion.isNotBlank()) {
            append(' ').append(osVersion)
        }
        if (javaVersion.isNotBlank()) {
            append(" · JVM ").append(javaVersion)
        }
    }.ifBlank { "JVM" }
}
