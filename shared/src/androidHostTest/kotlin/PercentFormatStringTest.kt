package com.shangkeschedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「学期进度」百分号文案的**格式串可执行性**（v4.68.4）。
 *
 * ## 这个测试在钉死什么
 *
 * `progress_percent_format` 是全仓唯一「位置占位符后面紧跟一个字面百分号」的文案，
 * 调用点为 `ManageCourseTablesScreen.kt`：
 * `stringResource(Res.string.progress_percent_format, weekPercent)`。
 *
 * Compose Resources 的 `getString(id, vararg formatArgs)` 会把资源正文交给
 * `String.format`。而 `String.format` 对「`%1$d` 之后紧跟一个裸 `%`」的判定是
 * **`UnknownFormatConversionException: Conversion = '%'`** —— 不是「输出成 %1$d%」，
 * 而是**直接抛异常**。也就是说：只要课程表管理页渲染出学期进度这一行，
 * 就会走到 `stringResource(...)` 并抛异常。
 *
 * ## 为什么四道门禁都没抓到（这才是本测试存在的理由）
 *
 * - **编译**：资源是运行期解析的，格式串语法错误编译期不检查；
 * - **单测**：此前没有任何测试真正调用过 `stringResource(Res.string.progress_percent_format, …)`；
 * - **静态门禁**：`check_*` 系列只看 token / 对比度 / 适配器，不解析格式串；
 * - **深检**：读日志，不看格式串语义。
 *
 * 只有「把真实格式串丢给真实的 `String.format`」才暴露它 —— 这正是本测试做的事。
 *
 * ## 判定口径
 *
 * 对三条 locale 的正文各跑一次 `String.format(body, 50)`：
 * **不抛异常**即通过（并断言渲染结果确实带一个字面 `%` 与传入的数值，
 * 防止有人用「删掉整个百分号」这种「让异常消失」的错误方式蒙混过关）。
 */
class PercentFormatStringTest {

    private val locales = listOf("values", "values-en", "values-zh-rTW")

    private fun repoRoot(): File {
        var dir = File(".").absoluteFile
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: error("未找到仓库根目录")
        }
        return dir
    }

    /** 取出指定 locale 的指定 key 的原始正文（不做 trim，交给 String.format 自己判）。 */
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
        return raw.trim().replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
    }

    @Test
    fun `三个 locale 的学期进度格式串都能被 String format 正常执行`() {
        locales.forEach { locale ->
            val b = body(locale, "progress_percent_format")
            val rendered = try {
                String.format(b, 50)
            } catch (t: Throwable) {
                throw AssertionError(
                    "locale=$locale 的 progress_percent_format 会被 String.format 拒绝：" +
                        "body=「$b」 ⇒ ${t.javaClass.simpleName}: ${t.message}。" +
                        "字面百分号必须写成 %%（两个），否则 stringResource 调用点直接抛异常。",
                    t,
                )
            }
            assertTrue(
                "locale=$locale 渲染结果应含一个字面百分号，实际=「$rendered」（body=「$b」）",
                rendered.contains("%"),
            )
            assertTrue(
                "locale=$locale 渲染结果应含传入的数值 50，实际=「$rendered」",
                rendered.contains("50"),
            )
        }
    }

    @Test
    fun `去掉转义后应当抛异常——保证本测试不是恒绿`() {
        // 反向断言：把 %% 还原成 % 之后，String.format 必须失败。
        // 这条保证判定口径**本身有效**——若将来 String.format 语义变了，
        // 第一条可能仍绿，但这条会红，从而暴露"门槛失效"而非"缺陷消失"。
        locales.forEach { locale ->
            val b = body(locale, "progress_percent_format")
            val broken = b.replace("%%", "%")
            var threw = false
            try {
                String.format(broken, 50)
            } catch (t: Throwable) {
                threw = true
            }
            assertTrue(
                "locale=$locale 去掉 %% 后应当抛异常，若不抛说明判定口径已失效（body=「$b」）",
                threw,
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
