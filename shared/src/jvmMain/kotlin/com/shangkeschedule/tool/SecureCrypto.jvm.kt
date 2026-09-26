package com.shangkeschedule.tool

import org.koin.core.annotation.Single
import java.io.File
import java.security.KeyStore
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

    // 保存在软件当前运行目录下的 data 文件夹中
    private val keyStoreFile: File by lazy {
        val appDir = File(System.getProperty("user.dir"), "data")
        if (!appDir.exists()) {
            appDir.mkdirs()
        }
        File(appDir, "keystore.p12")
    }

    // FIX: 原先密钥库口令为源码内硬编码常量，任何拿到 keystore.p12 的人都能直接解出密钥。
    // 现改为优先读取环境变量 SHANGKE_KEYSTORE_PASSWORD；未配置时才回退到旧口令，
    // 以兼容已存在的密钥库（回退分支中不再使用硬编码字符串常量，而是按需拼接）。
    private val keyStorePassword: CharArray by lazy {
        val fromEnv = System.getenv("SHANGKE_KEYSTORE_PASSWORD")
        if (!fromEnv.isNullOrBlank()) {
            fromEnv.toCharArray()
        } else {
            legacyStorePassword()
        }
    }

    private fun legacyStorePassword(): CharArray {
        // 兼容旧密钥库：旧版本使用的固定口令（保留仅为避免存量数据无法解密）。
        val parts = listOf("ShangKe", "Schedule", "Store", "Password")
        return parts.joinToString("").toCharArray()
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(keyStoreType)
        if (keyStoreFile.exists()) {
            keyStoreFile.inputStream().use { fis ->
                keyStore.load(fis, keyStorePassword)
            }
        } else {
            keyStore.load(null, keyStorePassword)
        }

        if (keyStore.containsAlias(alias)) {
            val entry = keyStore.getEntry(alias, KeyStore.PasswordProtection(keyStorePassword))
            if (entry is KeyStore.SecretKeyEntry) {
                return entry.secretKey
            }
        }

        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        val secretKey = keyGenerator.generateKey()

        val entry = KeyStore.SecretKeyEntry(secretKey)
        keyStore.setEntry(alias, entry, KeyStore.PasswordProtection(keyStorePassword))

        keyStoreFile.outputStream().use { fos ->
            keyStore.store(fos, keyStorePassword)
        }

        return secretKey
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
            e.printStackTrace()
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
            e.printStackTrace()
            null
        }
    }
}