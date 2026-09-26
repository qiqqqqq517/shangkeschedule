package com.shangkeschedule.tool

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * ZipUtils 的 JVM 系共享实现（androidMain 与 jvmMain 共用）。
 *
 * 之所以放在 jvmCommonMain 而不是 commonMain：本实现依赖 JDK 的 `java.util.zip`，
 * 该 API 在 Kotlin/Native（iOS）上不存在，无法放进 commonMain。iOS 仍由
 * `iosMain/.../ZipUtils.ios.kt` 提供自己的 actual。
 *
 * 源集接线见 shared/build.gradle.kts 的 jvmCommonMain。
 */
actual object ZipUtils {
    actual fun createZip(entries: Map<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos.buffered()).use { zos ->
            entries.forEach { (filename, bytes) ->
                zos.putNextEntry(ZipEntry(filename))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }
}
