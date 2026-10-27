package com.shangkeschedule.ui.schoolselection.web

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 原生网络错误文案归一的判据测试（v4.81.1）。
 *
 * 缺陷来源：199 个适配脚本里 123 个 fetch 链无 `.catch`、38 个 XHR 无 `onerror`，
 * 断网/代理拦截时异常冒泡到上报点，用户看到「适配脚本执行出错：Failed to fetch」。
 *
 * 这里钉住三件事：
 *  1) Chromium 原生网络错误的英文字样必须被识别（真阳性）；
 *  2) 脚本自己给出的中文提示不得被误吞（真阴性，否则用户失去可操作信息）；
 *  3) 非网络类英文（HTTP 500 等）保持原文，便于排查。
 */
class AdapterErrorTextTest {

    @Test
    fun `Chromium 原生网络错误被识别`() {
        val positive = listOf(
            "Failed to fetch",
            "TypeError: Failed to fetch",
            "NetworkError when attempting to fetch resource.",
            "Load failed",
            "Network request failed",
            "net::ERR_CONNECTION_REFUSED",
            "net::ERR_NAME_NOT_RESOLVED",
            "net::ERR_INTERNET_DISCONNECTED",
            "net::ERR_ADDRESS_UNREACHABLE",
            "NS_ERROR_NET_TIMEOUT",
        )
        for (text in positive) {
            assertTrue(isNativeNetworkErrorText(text), "应识别为原生网络错误：$text")
        }
    }

    @Test
    fun `脚本自己的中文提示不被吞掉`() {
        val negative = listOf(
            "当前学期没有课表，请确认学年学期后查询",
            "有 3 条课程因缺少星期/节次/周次被跳过",
            "登录可能已失效，请重新进入教务系统",
            "无法连接常州信息职院教务系统（jx.ccit.js.cn）",
            // 中文提示里带英文域名/错误码：仍按脚本原意展示
            "教务接口暂不可用（HTTP 500）",
        )
        for (text in negative) {
            assertFalse(isNativeNetworkErrorText(text), "不得替换脚本已本地化的提示：$text")
        }
    }

    @Test
    fun `非网络类英文与空消息保持原文`() {
        assertFalse(isNativeNetworkErrorText(""))
        assertFalse(isNativeNetworkErrorText("   "))
        assertFalse(isNativeNetworkErrorText("HTTP 500"))
        assertFalse(isNativeNetworkErrorText("adapter returned no timetable"))
    }
}
