package com.shangkeschedule.widget

import android.content.res.Configuration
import android.os.Build
import com.shangkeschedule.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 深浅切换监听的纯逻辑单测（[isNightUiMode] / [nightChanged]）。
 *
 * `Configuration.UI_MODE_*` 均为编译期常量，JVM 单测可直接引用；
 * 真正的广播收发与渲染只走集成验证（装机 + 切换取证），此处只锁死判定语义。
 */
class WidgetNightModeTest {

    @Test
    fun `night yes is night`() {
        assertTrue(isNightUiMode(Configuration.UI_MODE_NIGHT_YES))
        assertTrue(
            isNightUiMode(
                Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_YES
            )
        )
    }

    @Test
    fun `night no and undefined are not night`() {
        assertFalse(isNightUiMode(Configuration.UI_MODE_NIGHT_NO))
        assertFalse(isNightUiMode(Configuration.UI_MODE_NIGHT_UNDEFINED))
        assertFalse(
            isNightUiMode(
                Configuration.UI_MODE_TYPE_TELEVISION or Configuration.UI_MODE_NIGHT_NO
            )
        )
    }

    @Test
    fun `first observation only records baseline`() {
        assertFalse(nightChanged(null, true))
        assertFalse(nightChanged(null, false))
    }

    @Test
    fun `same mode does not trigger`() {
        assertFalse(nightChanged(false, false))
        assertFalse(nightChanged(true, true))
    }

    @Test
    fun `flip triggers in both directions`() {
        assertTrue(nightChanged(false, true))
        assertTrue(nightChanged(true, false))
    }

    @Test
    fun `card background follows mode`() {
        assertEquals(R.drawable.widget_bg_rounded_dark, widgetCardBackground(true))
        assertEquals(R.drawable.widget_bg_rounded_light, widgetCardBackground(false))
    }

    @Test
    fun `watcher only on old systems`() {
        // Android 10（报障机）与 Android 15（仍有宿主不跟的个案）要监听；
        // Android 16（BAKLAVA）起宿主自理，不注册不重推。
        assertTrue(shouldWatchNightMode(29))
        assertTrue(shouldWatchNightMode(35))
        assertFalse(shouldWatchNightMode(Build.VERSION_CODES.BAKLAVA))
        assertFalse(shouldWatchNightMode(Build.VERSION_CODES.BAKLAVA + 1))
    }
}
