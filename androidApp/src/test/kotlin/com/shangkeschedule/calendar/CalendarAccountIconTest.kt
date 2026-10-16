package com.shangkeschedule.calendar

import android.Manifest
import com.shangkeschedule.tool.CalendarAccountIdentity
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** 账户图标必须沿用应用图标资源，升级图标后系统日历才能同步读取新图。 */
class CalendarAccountIconTest {
    private val androidNs = "http://schemas.android.com/apk/res/android"
    private val manifest = xml("src/main/AndroidManifest.xml")

    @Test
    fun `calendar authenticator uses the application icon at both sizes`() {
        val application = manifest.getElementsByTagName("application").item(0) as Element
        val service = serviceFor("android.accounts.AccountAuthenticator")
        val metadata = service.getElementsByTagName("meta-data").item(0) as Element
        val authenticator = resource(metadata.getAttributeNS(androidNs, "resource"))
        val appIcon = application.getAttributeNS(androidNs, "icon")
        assertEquals(appIcon, authenticator.getAttributeNS(androidNs, "icon"))
        assertEquals(appIcon, authenticator.getAttributeNS(androidNs, "smallIcon"))
        assertEquals("false", service.getAttributeNS(androidNs, "exported"))
        assertEquals("", service.getAttributeNS(androidNs, "permission"))
    }

    @Test
    fun `calendar sync adapter and authenticator declare the same owned account type`() {
        val authService = serviceFor("android.accounts.AccountAuthenticator")
        val syncService = serviceFor("android.content.SyncAdapter")
        val authMeta = authService.getElementsByTagName("meta-data").item(0) as Element
        val syncMeta = syncService.getElementsByTagName("meta-data").item(0) as Element
        val auth = resource(authMeta.getAttributeNS(androidNs, "resource"))
        val sync = resource(syncMeta.getAttributeNS(androidNs, "resource"))
        assertEquals("@string/calendar_account_type", auth.getAttributeNS(androidNs, "accountType"))
        assertEquals(auth.getAttributeNS(androidNs, "accountType"), sync.getAttributeNS(androidNs, "accountType"))
        assertEquals("com.android.calendar", sync.getAttributeNS(androidNs, "contentAuthority"))
        assertEquals("false", syncService.getAttributeNS(androidNs, "exported"))
        assertEquals("", syncService.getAttributeNS(androidNs, "permission"))
        // 课表仍由应用手动写回；系统占位适配器不启用周期网络同步。
        assertEquals("false", sync.getAttributeNS(androidNs, "isAlwaysSyncable"))
        assertEquals("false", sync.getAttributeNS(androidNs, "supportsUploading"))
        val strings = xml("src/main/res/values/calendar_account.xml").getElementsByTagName("string")
        val accountType = (0 until strings.length).map { strings.item(it) as Element }
            .single { it.getAttribute("name") == "calendar_account_type" }.textContent
        assertEquals(CalendarAccountIdentity.TYPE, accountType)
    }

    @Test
    fun `manual account setup has permission to disable automatic system sync`() {
        val declarations = manifest.getElementsByTagName("uses-permission")
        assertTrue("账户初始化调用 setIsSyncable 和 setSyncAutomatically 必须声明 WRITE_SYNC_SETTINGS",
            (0 until declarations.length).any {
                (declarations.item(it) as Element).getAttributeNS(androidNs, "name") == Manifest.permission.WRITE_SYNC_SETTINGS
            }
        )
    }

    private fun serviceFor(action: String): Element {
        val services = manifest.getElementsByTagName("service")
        val result = (0 until services.length).map { services.item(it) as Element }.firstOrNull { service ->
            val actions = service.getElementsByTagName("action")
            (0 until actions.length).any {
                (actions.item(it) as Element).getAttributeNS(androidNs, "name") == action
            }
        }
        assertNotNull("系统日历缺少 $action 服务，无法关联应用账户图标", result)
        return result!!
    }

    private fun resource(reference: String): Element = xml("src/main/res/xml/${reference.substringAfter('/')}.xml")

    private fun xml(path: String): Element = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(File(path)).documentElement
}
