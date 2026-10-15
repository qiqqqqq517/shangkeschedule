package com.shangkeschedule.ui.schedule.components

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 契约审计：**每个已注册的组件 provider 都必须有点击路由**。
 *
 * ## 为什么需要它
 *
 * `WidgetRoute` 是「provider 简单名 → 跳转目标」的手工表。新增一个组件时，
 * 开发者记得在 `AndroidManifest.xml` 注册 provider、在 `WidgetUpdateHelper` 的 `nativeConfigs`
 * 里加一行渲染，但**极易忘记在 `WidgetRoute` 里补一行路由**。
 *
 * 而这个忘记的后果**没有任何报错**：组件照常显示、照常可点，只是点进去落到默认页 ——
 * 属于「编译器不管、单测不管、只有用户会觉得别扭」的盲区，
 * 与 v4.67.36 那次「请求码分段导致闹钟撤不掉」属同一类问题。
 *
 * 本测试直接从 `AndroidManifest.xml` 里**正则提取**所有注册的 provider，
 * 与 `WidgetRoute.KNOWN_PROVIDERS` 对账，把该契约变成可执行的断言。
 */
class WidgetProviderRouteCoverageTest {

    private fun manifestProviders(): List<String> {
        // 定位 AndroidManifest：shared 模块在仓库根的同级，需逐级上溯
        val candidates = generateSequence(File(".").absoluteFile) { it.parentFile }
            .map { File(it, "androidApp/src/main/AndroidManifest.xml") }
            .firstOrNull { it.isFile }
            ?: error("找不到 AndroidManifest.xml")

        val text = candidates.readText()
        val providerRegex = Regex("""android:name\s*=\s*"\.widget\.[A-Za-z0-9_.]*?([A-Za-z0-9_]*NativeProvider)"""")
        return providerRegex.findAll(text).map { it.groupValues[1] }.distinct().toList()
    }

    @Test
    fun everyRegisteredProviderHasClickRoute() {
        val registered = manifestProviders()
        assertTrue(
            "未能从 AndroidManifest 解析出任何 provider（正则失效，审计本身失效）",
            registered.size >= 8
        )
        val missing = registered.filterNot { it in com.shangkeschedule.widget.WidgetRoute.KNOWN_PROVIDERS }
        assertTrue(
            "以下 provider 已注册却**没有点击路由**（点进去会落到默认页）：$missing",
            missing.isEmpty()
        )
    }
}