package com.shangkeschedule.tool

import com.shangkeschedule.data.di.OperatingSystem
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * 桌面端密钥库口令的持久化存储（SEC-02）。
 *
 * 历史实现把密钥库口令硬编码在源码里，等价于公开口令——任何拿到 `keystore.p12` 的人都能解出 AES 密钥。
 * 现在改为首次运行生成 32 字节随机口令并持久化，存储位置按平台择优：
 * 1. Windows：DPAPI（`DataProtectionScope.CurrentUser`）加密后的密文文件，换机器 / 换系统账户都无法解开；
 * 2. 其他平台或 DPAPI 不可用：仅属主可读的口令文件（POSIX `0600`；Windows 上依赖用户目录本身的 ACL）。
 *
 * 明文回退文件放在用户主目录下的 `.shangkeschedule`，刻意不与密钥库同目录：
 * 整目录拷贝 / 同步应用数据时不会把口令一起带走。
 */
internal object DesktopSecretStore {

    private const val FILE_NAME = "keystore.secret"
    private const val DPAPI_FILE_NAME = "keystore.dpapi"
    private const val POWERSHELL_TIMEOUT_SECONDS = 10L

    /** 明文回退文件（macOS / Linux，或 Windows 上 DPAPI 不可用时）。 */
    private val fallbackFile: File by lazy {
        val dir = File(System.getProperty("user.home"), ".shangkeschedule")
        if (!dir.exists()) dir.mkdirs()
        File(dir, FILE_NAME)
    }

    /** DPAPI 密文文件：自身已加密，因此可以和应用数据放在一起。 */
    private val dpapiFile: File by lazy { File(DesktopAppDirs.subDir("keystore"), DPAPI_FILE_NAME) }

    fun hasSecret(): Boolean = readSecret() != null

    /** 读取已持久化的口令；不存在或不可解读时返回 null（由调用方决定回退或重新生成）。 */
    fun readSecret(): CharArray? {
        if (OperatingSystem.current == OperatingSystem.WINDOWS) {
            readDpapiSecret()?.let { return it }
        }
        return readPlainSecret()
    }

    /** 持久化口令；返回是否成功（失败时调用方必须放弃"已经改了口令"的假设）。 */
    fun writeSecret(password: CharArray): Boolean {
        val text = String(password)

        if (OperatingSystem.current == OperatingSystem.WINDOWS) {
            val blob = runPowerShell(
                script = DPAPI_PROTECT_SCRIPT,
                stdinText = Base64.getEncoder().encodeToString(text.toByteArray(StandardCharsets.UTF_8))
            )
            if (blob != null && writeAtomically(dpapiFile, blob)) {
                // 同一口令只保留一份：清掉可能存在的明文回退文件
                runCatching { fallbackFile.delete() }
                return true
            }
        }

        return writeAtomically(fallbackFile, text)
    }

    /** 删除已持久化的口令（仅用于口令迁移回滚）。 */
    fun deleteSecret() {
        runCatching { dpapiFile.delete() }
        runCatching { fallbackFile.delete() }
    }

    private fun readDpapiSecret(): CharArray? {
        return try {
            if (!dpapiFile.isFile) return null
            val blob = dpapiFile.readText(StandardCharsets.UTF_8).trim()
            if (blob.isEmpty()) return null

            val plain = runPowerShell(script = DPAPI_UNPROTECT_SCRIPT, stdinText = blob) ?: return null
            plain.trim().ifBlank { null }?.toCharArray()
        } catch (e: Exception) {
            null
        }
    }

    private fun readPlainSecret(): CharArray? {
        return try {
            if (!fallbackFile.isFile) return null
            fallbackFile.readText(StandardCharsets.UTF_8).trim().ifBlank { null }?.toCharArray()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 以"临时文件 + 原子替换"的方式写入口令文件，避免写到一半崩溃留下半截口令。
     * 同时尽力把文件权限收紧到仅属主可读写。
     */
    private fun writeAtomically(file: File, content: String): Boolean {
        return try {
            val parent = file.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()

            val tmp = File(parent, "${file.name}.tmp")
            tmp.writeText(content, StandardCharsets.UTF_8)
            restrictToOwner(tmp)

            try {
                Files.move(
                    tmp.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE
                )
            } catch (e: Exception) {
                // 部分文件系统（如某些网络盘）不支持原子移动，退化为普通替换
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }

            restrictToOwner(file)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun restrictToOwner(file: File) {
        // 非 POSIX 文件系统（Windows）会抛 UnsupportedOperationException，忽略即可：
        // 此时文件权限继承自用户配置目录，本身已限制在当前 Windows 账户内。
        runCatching {
            Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------"))
        }
        runCatching {
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
            file.setExecutable(false, false)
        }
    }

    /**
     * 调用 Windows PowerShell 执行 DPAPI 加解密。
     *
     * 脚本以 Base64（UTF-16LE）经 `-EncodedCommand` 传入，彻底规避 Java 在 Windows 上的参数转义地狱；
     * 待加/解密的文本走 stdin，结果走 stdout。任何一步失败都返回 null，由调用方回退到文件存储。
     */
    private fun runPowerShell(script: String, stdinText: String): String? {
        return try {
            val encodedCommand = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
            val process = ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy", "Bypass",
                "-EncodedCommand", encodedCommand
            ).redirectError(ProcessBuilder.Redirect.DISCARD).start()

            process.outputStream.use { it.write(stdinText.toByteArray(StandardCharsets.UTF_8)) }

            if (!process.waitFor(POWERSHELL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            if (process.exitValue() != 0) return null

            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            output.trim().ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }

    // 注意：PowerShell 脚本内的 `$` 需要转义，否则会被当作 Kotlin 字符串模板。
    private val DPAPI_PROTECT_SCRIPT: String = listOf(
        "Add-Type -AssemblyName System.Security",
        "\$in_b64 = [Console]::In.ReadToEnd().Trim()",
        "\$bytes = [Convert]::FromBase64String(\$in_b64)",
        "\$prot = [System.Security.Cryptography.ProtectedData]::Protect(\$bytes, \$null, " +
            "[System.Security.Cryptography.DataProtectionScope]::CurrentUser)",
        "[Console]::Out.Write([Convert]::ToBase64String(\$prot))"
    ).joinToString("\n")

    private val DPAPI_UNPROTECT_SCRIPT: String = listOf(
        "Add-Type -AssemblyName System.Security",
        "\$in_b64 = [Console]::In.ReadToEnd().Trim()",
        "\$bytes = [Convert]::FromBase64String(\$in_b64)",
        "\$plain = [System.Security.Cryptography.ProtectedData]::Unprotect(\$bytes, \$null, " +
            "[System.Security.Cryptography.DataProtectionScope]::CurrentUser)",
        "[Console]::Out.Write([Text.Encoding]::UTF8.GetString(\$plain))"
    ).joinToString("\n")
}
