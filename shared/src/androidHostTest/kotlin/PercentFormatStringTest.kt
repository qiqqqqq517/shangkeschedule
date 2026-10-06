package com.shangkeschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「学期进度」百分号文案的**真实渲染结果**（v4.68.4 建，含一次订正）。
 *
 * ## 这个测试在钉死什么
 *
 * `progress_percent_format` 是全仓唯一「位置占位符后面紧跟一个字面百分号」的文案，
 * 调用点为 `ManageCourseTablesScreen.kt`：
 * `stringResource(Res.string.progress_percent_format, weekPercent)`。
 *
 * ## 曾经的错误前提（本测试的第一次教训）
 *
 * 该测试初版假设 Compose Resources 把资源正文交给 `String.format`，于是判定
 * 字面百分号必须写成 `%%`，并据此把三语正文改成 `已过 %1$d%%`。
 * **这个前提是错的。**
 *
 * Compose Multiplatform 的字符串插值实现在 `components-resources` 的
 * `commonMain/org/jetbrains/compose/resources/StringResourcesUtils.kt`：
 *
 * ```
 * private val SimpleStringFormatRegex = Regex("""%(\d+)\$[ds]""")
 * internal fun String.replaceWithArgs(args: List<String>) =
 *     SimpleStringFormatRegex.replace(this) { args[it.groupValues[1].toInt() - 1] }
 * ```
 *
 * 它**不使用 `String.format`**，只做一次正则替换 —— 因此 `%%` 不会被还原，
 * 会**原样显示两个百分号**（用户可见症状：学期进度显示「已过 45%%」）；
 * 而单个 `%1$d%` 里的裸 `%` 既不会被匹配、也不会抛异常，渲染结果正是「已过 45%」。
 *
 * ⇒ 判据：**字面百分号写单个 `%`**，不是 `%%`。
 *
 * ## 为什么四道门禁都没抓到
 *
 * - **编译**：资源是运行期解析的，格式串语法错误编译期不检查；
 * - **单测**：初版测的是 Java `String.format`，**与被测管线不是同一条链路**，属假门禁；
 * - **静态门禁**：`check_*` 系列只看 token / 对比度 / 适配器，不解析格式串；
 * - **深检**：读日志，不看渲染结果语义。
 *
 * 只有「按真实插值规则跑一遍正文」才暴露它 —— 这正是本测试做的事。
 */
class PercentFormatStringTest {

    private val locales = listOf("values", "values-en", "values-zh-rTW")

    /**
     * 与 `StringResourcesUtils.kt` 中 `SimpleStringFormatRegex` 逐字一致。
     * 本测试不引 Compose 内部 API（宿主环境无 Compose 运行时），
     * 故按同一规则复刻；规则若在库侧变化，本测试需同步。
     */
    private val composeFormatRegex = Regex("""%(\d+)\$[ds]""")

    private fun renderComposeString(body: String, vararg args: String): String =
        composeFormatRegex.replace(body) { args[it.groupValues[1].toInt() - 1] }

    private fun repoRoot(): File {
        var dir = File(".").absoluteFile
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: error("未找到仓库根目录")
        }
        return dir
    }

    /** 取出指定 locale 的指定 key 的原始正文（不做 trim）。 */
    private fun body(locale: String, key: String): String {
        val file = File(
            repoRoot(),
            "shared/src/commonMain/composeResources/$locale/strings.xml",
        )
        assertTrue("找不到 ${file.path}", file.exists())
        val m = Regex("<string name=\"$key\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .find(file.readText())
        assertTrue("locale=$locale 缺少 $key", m != null)
        val raw: String = m!!.groupValues[1]
        return raw.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
    }

    @Test
    fun `三个 locale 按 Compose 插值规则渲染后恰好只有一个字面百分号`() {
        locales.forEach { locale ->
            val b = body(locale, "progress_percent_format")
            val rendered = renderComposeString(b, "50")
            assertEquals(
                "locale=$locale 应恰好渲染出一个字面百分号，实际=「$rendered」（body=「$b」）。" +
                    "字面百分号写一个 `%` 即可，写成 `%%` 会原样显示两个。",
                1,
                rendered.count { it == '%' },
            )
            assertTrue(
                "locale=$locale 渲染结果应含传入的数值 50，实际=「$rendered」",
                rendered.contains("50"),
            )
            assertTrue(
                "locale=$locale 渲染结果不应残留未替换的占位符，实际=「$rendered」",
                composeFormatRegex.find(rendered) == null,
            )
        }
    }

    @Test
    fun `写成两个百分号时必须渲染出两个——保证本测试不是恒绿`() {
        // 反向断言：把正文里的 `%1$d%` 改回历史错误写法 `%1$d%%`，
        // 渲染结果必须出现两个百分号。若这条不成立，说明上一条的判定口径已失效
        // （而非缺陷消失）。
        locales.forEach { locale ->
            val b = body(locale, "progress_percent_format")
            val broken = b.replace("%1\$d%", "%1\$d%%")
            assertTrue("locale=$locale 反向构造失败，body=「$b」", broken != b)
            val rendered = renderComposeString(broken, "50")
            assertEquals(
                "locale=$locale 写两个百分号时应渲染出两个，实际=「$rendered」",
                2,
                rendered.count { it == '%' },
            )
        }
    }

    @Test
    fun `三语占位符编号一致且都只有一个位置参数`() {
        val perLocale: Map<String, List<String>> = locales.associateWith { loc ->
            val found: List<String> = Regex("%(\\d+)\\$")
                .findAll(body(loc, "progress_percent_format"))
                .map { it.groupValues[1] }
                .toList()
            found
        }
        assertEquals(
            "三语的占位符编号序列应一致，实际=" + perLocale,
            1,
            perLocale.values.toSet().size,
        )
        perLocale.forEach { (loc, ids) ->
            assertEquals("locale=$loc 应恰好有 1 个位置占位符，实际=$ids", listOf("1"), ids)
        }
    }
}