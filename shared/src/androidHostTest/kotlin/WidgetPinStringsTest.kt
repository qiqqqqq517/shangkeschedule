package com.shangkeschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * XL-015「添加到桌面」的三语资源完整性。
 *
 * ## 为什么要有这个测试
 *
 * 这一组文案是**逐个 locale 手写新增**的（`values` / `values-en` / `values-zh-rTW`），
 * 而 compose-resources 在缺 key 时是**运行期**才回退到默认 locale 的 ——
 * 漏翻一个 locale 不会编译失败、不会单测失败，只会在那个语言的设备上默默显示简体字。
 *
 * 上一轮 XL-013 的 9 条资源是同一个坑（无自动闸）。这里补上闸：
 * 三份 `strings.xml` 的 key 集合必须完全一致，且占位符编号必须对齐。
 *
 * 占位符那条尤其重要：`%1$d` 在中文语境排第 1、在英语语境排第 1，但在需要调换语序的
 * 译文里可能变成 `%2$s`；只比 key 集合比不出这种错位，必须逐 key 对比 `%N` 的出现情况。
 */
class WidgetPinStringsTest {

    private val locales = listOf("values", "values-en", "values-zh-rTW")

    /** 本次新增的 6 个 key（其余既有 key 由其它测试/人工把关）。 */
    private val pinKeys = listOf(
        "widget_troubleshoot_section_add",
        "widget_troubleshoot_add_desc",
        "widget_troubleshoot_added_count",
        "widget_troubleshoot_added_none",
        "widget_troubleshoot_toast_pin_requested",
        "widget_troubleshoot_toast_pin_rejected",
    )

    private fun repoRoot(): File {
        var dir = File(".").absoluteFile
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: error("未找到仓库根目录")
        }
        return dir
    }

    /** key -> 值；同时保留原始顺序以便报错可读。 */
    private fun readStrings(locale: String): Map<String, String> {
        val file = File(
            repoRoot(),
            "shared/src/commonMain/composeResources/$locale/strings.xml",
        )
        assertTrue("找不到 ${file.path}", file.exists())
        val text = file.readText()
        val out = LinkedHashMap<String, String>()
        Regex("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .findAll(text)
            .forEach { m -> out[m.groupValues[1]] = m.groupValues[2].trim() }
        return out
    }

    @Test
    fun `三个 locale 的 key 集合完全一致`() {
        val sets = locales.associateWith { readStrings(it).keys }
        val base = sets.getValue("values")
        locales.drop(1).forEach { loc ->
            val other = sets.getValue(loc)
            assertEquals(
                "$loc 缺少的 key：${base - other}",
                base,
                other,
            )
        }
    }

    @Test
    fun `新增的 6 个 key 在三语里都存在且非空`() {
        locales.forEach { loc ->
            val map = readStrings(loc)
            pinKeys.forEach { key ->
                assertTrue("$loc 缺少 $key", map.containsKey(key))
                val value = map.getValue(key)
                assertFalse("$loc 的 $key 是空串", value.isBlank())
            }
        }
    }

    @Test
    fun `占位符编号在三语之间一致`() {
        // %1$d / %2$s 这类占位符按编号收集；编号集合不同即说明译文调换了参数顺序而未同步
        fun placeholders(s: String) =
            Regex("%(\\d+)\\$[a-zA-Z]").findAll(s).map { it.groupValues[1] }.toSet()

        val byKey = pinKeys.associateWith { key ->
            locales.associateWith { loc -> placeholders(readStrings(loc).getValue(key)) }
        }
        byKey.forEach { (key, perLocale) ->
            val base = perLocale.getValue("values")
            // 不是每条文案都要有占位符（区块标题「添加到桌面」就没有）——
            // 只要**有**的编号必须三语对齐，没出现过的 key 自然三语都为空集，也自洽。
            perLocale.filterKeys { it != "values" }.forEach { (loc, set) ->
                assertEquals("$key 在 $loc 的占位符编号与默认 locale 不一致", base, set)
            }
        }
    }

    @Test
    fun `added_count 的占位符排在最后`() {
        // 「已添加 %1$d 个」这类文案，若译文把数字放到句首，占位符编号不变但语序变了 ——
        // 这种情况 Kotlin 侧发现不了。这里只守住「每条文案恰好一个占位符」这条底线。
        locales.forEach { loc ->
            val v = readStrings(loc).getValue("widget_troubleshoot_added_count")
            assertEquals("$loc 的 added_count 应恰好一个占位符", 1, Regex("%\\d+\\$[a-zA-Z]").findAll(v).count())
        }
    }
}