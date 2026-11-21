package com.shangkeschedule

import android.app.Application
import android.content.Intent
import com.shangkeschedule.tool.ExternalTextImport
import com.shangkeschedule.widget.WidgetRoute
import com.shangkeschedule.widget.WidgetRouteHandoff
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = Application::class)
class ExternalIntentConsumptionTest {
    private fun handle(name: String, intent: Intent) {
        MainActivity::class.java.getDeclaredMethod(name, Intent::class.java).apply { isAccessible = true }
            .invoke(MainActivity(), intent)
    }

    @Test fun widgetRouteIsConsumedFromIntentAfterHandoff() {
        val intent = Intent().putExtra(WidgetRoute.EXTRA_ROUTE, WidgetRoute.AGENDA)
        handle("handleWidgetRouteIntent", intent)
        assertEquals(WidgetRoute.AGENDA, WidgetRouteHandoff.pending.value)
        WidgetRouteHandoff.clear()
        handle("handleWidgetRouteIntent", intent)
        assertNull(WidgetRouteHandoff.pending.value)
    }

    @Test fun selectedTextIsConsumedFromIntentAfterHandoff() {
        val intent = Intent(Intent.ACTION_PROCESS_TEXT).putExtra(Intent.EXTRA_PROCESS_TEXT, "待导入文本")
        handle("handleProcessTextIntent", intent)
        assertEquals("待导入文本", ExternalTextImport.pending.value)
        ExternalTextImport.clear()
        handle("handleProcessTextIntent", intent)
        assertNull(ExternalTextImport.pending.value)
    }
}
