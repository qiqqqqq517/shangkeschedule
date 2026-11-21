package com.shangkeschedule

import com.shangkeschedule.data.api.UpdateCheckClient
import com.shangkeschedule.ui.settings.SettingsViewModel
import androidx.lifecycle.ViewModelStore
import org.junit.Assert.*
import org.junit.Test
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.plugin.module.dsl.module

class DesktopStartupTest {
    @Test fun productionModuleResolvesFirstScreenAndVersionBindings() {
        // 只检查正式依赖根；不执行离线资源初始化中的联网更新任务。
        val app = koinApplication(createEagerInstances = false) { module<DesktopModule>() }
        val store = ViewModelStore()
        try {
            val vm = app.koin.get<SettingsViewModel>()
            store.put("settings", vm)
            val update = app.koin.get<UpdateCheckClient>()
            assertEquals(DesktopBuildInfo.VERSION_CODE, app.koin.get<Int>(named("AppVersionCode")))
            assertEquals(DesktopBuildInfo.VERSION_NAME, update.currentVersionName)
            assertTrue(update.currentVersionCode > 0)
        } finally {
            store.clear()
            app.close()
        }
    }
}
