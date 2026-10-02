package com.shangkeschedule

import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shangkeschedule.data.repository.collectLegacyLocalSecrets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「只在本机」数据的一次性迁移名单（v4.67.16）。
 *
 * ## 为什么要有这个测试
 *
 * AI API Key 与考证查分凭据原本住在 `app_settings.preferences_pb`，而那份文件参与
 * Android 云备份与设备间迁移 —— 与两者的「只在本机 / 不参与云备份」声明矛盾。
 * 修复动作是把它们搬到已被 `backup_rules.xml` / `data_extraction_rules.xml` 排除的
 * `api_config.preferences_pb`。
 *
 * 迁移名单是这个链条上唯一「错了也不报错」的环节：
 * - 漏认一个键 ⇒ 该键永远留在会被上云的旧存储里，等于本轮修复白做；
 * - 多认一个键 ⇒ 普通设置被搬走，用户设置丢失。
 * 两种错都不会编译失败、也不会被其它单测拦住，所以单独把名单钉住。
 */
class LocalSecretsMigrationTest {

    @Test
    fun `只挑出 AI 密钥与考证凭据`() {
        val prefs = preferencesOf(
            stringPreferencesKey("ai_api_key") to "sk-local",
            stringPreferencesKey("cert_name_cet4") to "张三",
            stringPreferencesKey("cert_ticket_cet4") to "2026010001",
            stringPreferencesKey("theme_mode") to "DARK",
            stringPreferencesKey("ai_api_base_url") to "https://api.example.com/v1",
            stringPreferencesKey("ai_api_model") to "gpt-4o-mini",
            stringPreferencesKey("profile_nickname") to "小明"
        )

        val moved = collectLegacyLocalSecrets(prefs).toMap()

        assertEquals(3, moved.size)
        assertEquals("sk-local", moved[stringPreferencesKey("ai_api_key")])
        assertEquals("张三", moved[stringPreferencesKey("cert_name_cet4")])
        assertEquals("2026010001", moved[stringPreferencesKey("cert_ticket_cet4")])
        assertFalse(moved.containsKey(stringPreferencesKey("theme_mode")))
        assertFalse(moved.containsKey(stringPreferencesKey("ai_api_base_url")))
        assertFalse(moved.containsKey(stringPreferencesKey("ai_api_model")))
        assertFalse(moved.containsKey(stringPreferencesKey("profile_nickname")))
    }

    @Test
    fun `空值也算命中要删的旧键`() {
        // 空串不往新存储写（避免用空值覆盖已有凭据），但旧键必须被认出来从旧存储删掉。
        val prefs = preferencesOf(
            stringPreferencesKey("cert_name_cet4") to "",
            stringPreferencesKey("ai_api_key") to ""
        )

        val moved = collectLegacyLocalSecrets(prefs).toMap()

        assertEquals(2, moved.size)
        assertEquals("", moved[stringPreferencesKey("cert_name_cet4")])
        assertEquals("", moved[stringPreferencesKey("ai_api_key")])
    }

    @Test
    fun `名字相近但不是密钥的键一个都不动`() {
        val prefs = preferencesOf(
            stringPreferencesKey("theme_preset") to "claude",
            stringPreferencesKey("ai_import_enabled") to "true",
            stringPreferencesKey("adapter_sync_at") to "1700000000000",
            stringPreferencesKey("cert_copy_notice") to "x"
        )

        assertTrue(collectLegacyLocalSecrets(prefs).isEmpty())
    }
}
