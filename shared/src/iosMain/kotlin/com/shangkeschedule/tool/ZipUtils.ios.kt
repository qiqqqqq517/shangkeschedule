// shared/src/iosMain/kotlin/com/shangkeschedule/tool/ZipUtils.ios.kt
package com.shangkeschedule.tool

/**
 * iOS 端的 Zip 工具类实现
 * 当前为占位符，尚未实现具体逻辑。
 * 如果在 iOS 运行此代码将会触发 NotImplementedError。
 */
actual object ZipUtils {
    actual fun createZip(entries: Map<String, ByteArray>): ByteArray {
        //FIX:NotImplementedError 属于 Error，BackupViewModel 的 catch(Exception) 无法捕获，iOS 导出备份会直接崩溃
        throw UnsupportedOperationException("ZipUtils.createZip is not yet implemented for iOS platform.")
    }
}
