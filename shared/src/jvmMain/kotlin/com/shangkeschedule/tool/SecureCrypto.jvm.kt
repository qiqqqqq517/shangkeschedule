package com.shangkeschedule.tool

import org.koin.core.annotation.Single
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Single
actual class SecureCrypto {

    private val transformation = "AES/GCM/NoPadding"
    private val keyStoreType = "PKCS12"
    private val alias = "ShangKeApiCryptoKeyAlias"

    /** 旧版密钥库位置（进程当前工作目录下的 data/），仅用于把存量密钥库迁移到应用数据目录。 */
    private val legacyKeyStoreFile: File
        get() = File(File(System.getProperty("user.dir"), "data"), "keystore.p12")

    // FIX(SEC-02): 原先密钥库写死在"进程当前工作目录"下——同一份安装从不同快捷方式启动
    // （起始位置不同）会读到不同的密钥库，旧密文直接解不开。现统一放到应用数据目录；
    // 首次升级时把旧文件复制过来以保留存量密文，复制失败则继续使用旧路径（绝不能新建密钥）。
    private val keyStoreFile: File by lazy {
        val target = File(DesktopAppDirs.subDir("keystore"), "keystore.p12")
        if (target.exists()) return@lazy target

        val legacy = legacyKeyStoreFile
        if (!legacy.isFile || legacy.absolutePath == target.absolutePath) return@lazy target

        val copied = try {
            legacy.copyTo(target, overwrite = false)
            target.isFile
        } catch (e: Exception) {
            false
        }
        if (copied) target else legacy
    }

    /**
     * 主口令解析顺序：环境变量 > 已持久化的随机口令 > （密钥库已存在时）旧固定口令 > 新生成并持久化。
     * 只有密钥库尚不存在时才生成新口令：否则会给存量密钥库配上"解不开自己的口令"。
     */
    private fun resolvePrimaryPassword(): CharArray {
        val fromEnv = System.getenv("SHANGKE_KEYSTORE_PASSWORD")
        if (!fromEnv.isNullOrBlank()) return fromEnv.toCharArray()

        DesktopSecretStore.readSecret()?.let { return it }

        if (keyStoreFile.exists()) return legacyStorePassword()

        val generated = generateRandomPassword()
        // 写失败也继续用本次生成的口令：本次会话可用，下次启动会重新生成并再试一次持久化
        DesktopSecretStore.writeSecret(generated)
        return generated
    }

    private fun generateRandomPassword(): CharArray {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes).toCharArray()
    }

    private fun legacyStorePassword(): CharArray {
        // 兼容旧密钥库：旧版本使用的固定口令（保留仅为避免存量数据无法解密）。
        val parts = listOf("ShangKe", "Schedule", "Store", "Password")
        return parts.joinToString("").toCharArray()
    }

    private fun isLegacyPassword(password: CharArray): Boolean = password.contentEquals(legacyStorePassword())

    private fun loadKeyStore(password: CharArray): KeyStore? {
        return try {
            KeyStore.getInstance(keyStoreType).also { keyStore ->
                keyStoreFile.inputStream().use { keyStore.load(it, password) }
            }
        } catch (e: Exception) {
            // 口令不对或文件损坏：交给调用方尝试下一个候选口令
            null
        }
    }

    private fun getSecretKey(): SecretKey {
        val primary = resolvePrimaryPassword()

        if (!keyStoreFile.exists()) {
            val keyStore = KeyStore.getInstance(keyStoreType)
            keyStore.load(null, primary)
            return createAndPersistEntry(keyStore, primary)
        }

        // 依次尝试主口令与旧口令：口令可能刚随机化，但存量密钥库仍由旧口令保护
        for (password in listOf(primary, legacyStorePassword())) {
            val keyStore = loadKeyStore(password) ?: continue

            if (keyStore.containsAlias(alias)) {
                val entry = keyStore.getEntry(alias, KeyStore.PasswordProtection(password))
                if (entry is KeyStore.SecretKeyEntry) {
                    migrateLegacyPasswordIfNeeded(password)
                    return entry.secretKey
                }
            }

            // 能解开但里面还没有密钥条目：补一条并按当前口令落盘
            return createAndPersistEntry(keyStore, password)
        }

        // 密钥库存在却没有任何候选口令能解开：宁可失败（上层 catch 后返回 null），
        // 也不能覆盖写 —— 那会让存量密文永久无法解密。
        throw IllegalStateException("无法解锁桌面端密钥库：${keyStoreFile.absolutePath}")
    }

    private fun createAndPersistEntry(keyStore: KeyStore, password: CharArray): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        val secretKey = keyGenerator.generateKey()

        keyStore.setEntry(alias, KeyStore.SecretKeyEntry(secretKey), KeyStore.PasswordProtection(password))
        keyStoreFile.outputStream().use { keyStore.store(it, password) }

        return secretKey
    }

    /**
     * 存量密钥库仍由硬编码旧口令保护时，把口令替换为随机口令并重新落盘。
     * 顺序刻意设计为"先持久化新口令，再重写密钥库"；任一步失败都回滚已存口令，
     * 保证磁盘上的密钥库与已存口令始终一致（最坏情况是保持旧口令，而不是彻底解不开）。
     */
    private fun migrateLegacyPasswordIfNeeded(usedPassword: CharArray) {
        if (!isLegacyPassword(usedPassword)) return
        if (DesktopSecretStore.hasSecret()) return

        val newPassword = generateRandomPassword()
        if (!DesktopSecretStore.writeSecret(newPassword)) return

        val rewritten = try {
            rewriteKeyStore(usedPassword, newPassword)
        } catch (e: Exception) {
            false
        }

        // 重写失败时必须把刚写入的新口令删掉，否则磁盘上会留下"解不开当前密钥库的口令"，
        // 之后每次启动都只能走旧口令回退分支。
        if (!rewritten) DesktopSecretStore.deleteSecret()
    }

    private fun rewriteKeyStore(oldPassword: CharArray, newPassword: CharArray): Boolean {
        val keyStore = loadKeyStore(oldPassword) ?: return false

        val tmp = File(keyStoreFile.parentFile, "${keyStoreFile.name}.tmp")
        tmp.outputStream().use { keyStore.store(it, newPassword) }

        try {
            Files.move(
                tmp.toPath(), keyStoreFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE
            )
        } catch (e: Exception) {
            Files.move(tmp.toPath(), keyStoreFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        return true
    }

    actual fun encrypt(data: String): CryptoResult? {
        if (data.isEmpty()) return null
        return try {
            val cipher = Cipher.getInstance(transformation)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))

            CryptoResult(
                encryptedData = Base64.getEncoder().encodeToString(encryptedBytes),
                iv = Base64.getEncoder().encodeToString(cipher.iv)
            )
        } catch (e: Exception) {
            AppLog.e(TAG, "桌面端加密失败", e)
            null
        }
    }

    actual fun decrypt(encryptedData: String, ivString: String): String? {
        if (encryptedData.isEmpty() || ivString.isEmpty()) return null
        return try {
            val cipher = Cipher.getInstance(transformation)
            val ivBytes = Base64.getDecoder().decode(ivString)
            val gcmSpec = GCMParameterSpec(128, ivBytes)

            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), gcmSpec)
            val decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedData))
            String(decryptedBytes, Charsets.UTF_8)
                .replace("\u0000", "")
                .trim()
        } catch (e: Exception) {
            AppLog.e(TAG, "桌面端解密失败", e)
            null
        }
    }

    private companion object {
        const val TAG = "SecureCrypto"
    }
}
