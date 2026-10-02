import com.shangkeschedule.data.api.UpdateCheckResult
import com.shangkeschedule.data.api.UpdateFailureKind
import com.shangkeschedule.data.api.UpdateManifest
import com.shangkeschedule.data.api.evaluateUpdateManifest
import com.shangkeschedule.data.api.parseUpdateManifest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 「检查更新」（K4，v4.66.0）的纯逻辑单测：
 * 版本清单解析 + 本地/远端比对全分支 + 官网 `website/version.json` 的配置体检。
 *
 * 这里不测网络（`UpdateCheckClient.check()` 需要有响应体），网络层只用真机验收覆盖。
 */
class UpdateCheckLogicTest {

    // --- 解析 ---

    @Test
    fun `解析完整清单`() {
        val body = """
            {
              "versionCode": 420,
              "versionName": "4.66.0",
              "downloadUrl": "https://pan.quark.cn/s/02947cbc1d4e",
              "shareToken": "/~9a263aTFyF~/",
              "releaseNotesUrl": "https://shangke.asia/changelog.html"
            }
        """.trimIndent()

        val manifest = assertNotNull(parseUpdateManifest(body), "完整清单必须解析成功")
        assertEquals(420, manifest.versionCode)
        assertEquals("4.66.0", manifest.versionName)
        assertEquals("https://pan.quark.cn/s/02947cbc1d4e", manifest.downloadUrl)
        assertEquals("/~9a263aTFyF~/", manifest.shareToken)
        assertEquals("https://shangke.asia/changelog.html", manifest.releaseNotesUrl)
    }

    @Test
    fun `未知字段被忽略且缺省字段有默认值`() {
        // 官网以后加字段（例如发布时间）不能让客户端解析失败
        val body = """{"versionCode":420,"versionName":"4.66.0","publishedAt":"2026-10-02"}"""

        val manifest = assertNotNull(parseUpdateManifest(body))
        assertEquals(420, manifest.versionCode)
        assertEquals("4.66.0", manifest.versionName)
        assertEquals("", manifest.downloadUrl)
        assertEquals("", manifest.shareToken)
        assertEquals("", manifest.releaseNotesUrl)
    }

    @Test
    fun `非 JSON 内容解析失败`() {
        assertEquals(null, parseUpdateManifest("<html>404 Not Found</html>"))
        assertEquals(null, parseUpdateManifest(""))
        assertEquals(null, parseUpdateManifest("48"))
    }

    // --- 比对 ---

    @Test
    fun `清单缺失按解析失败处理`() {
        val result = evaluateUpdateManifest(419, "4.65.0", null)
        assertEquals(UpdateCheckResult.Failed(UpdateFailureKind.PARSE), result)
    }

    @Test
    fun `缺少版本号或版本名按信息不完整处理`() {
        val noCode = UpdateManifest(versionCode = 0, versionName = "4.66.0")
        assertEquals(
            UpdateCheckResult.Failed(UpdateFailureKind.INCOMPLETE),
            evaluateUpdateManifest(419, "4.65.0", noCode),
        )

        val blankName = UpdateManifest(versionCode = 420, versionName = "   ")
        assertEquals(
            UpdateCheckResult.Failed(UpdateFailureKind.INCOMPLETE),
            evaluateUpdateManifest(419, "4.65.0", blankName),
        )
    }

    @Test
    fun `远端等于或低于本地都是已是最新`() {
        val same = UpdateManifest(versionCode = 419, versionName = "4.65.0")
        assertEquals(
            UpdateCheckResult.UpToDate("4.65.0"),
            evaluateUpdateManifest(419, "4.65.0", same),
        )

        val older = UpdateManifest(versionCode = 400, versionName = "4.64.5")
        assertEquals(
            UpdateCheckResult.UpToDate("4.65.0"),
            evaluateUpdateManifest(419, "4.65.0", older),
        )
    }

    @Test
    fun `已是最新时不要求下载地址`() {
        // 历史清单（或只改版本号忘了填下载地址）不该把「已是最新」误报成失败
        val same = UpdateManifest(versionCode = 419, versionName = "4.65.0", downloadUrl = "")
        assertEquals(
            UpdateCheckResult.UpToDate("4.65.0"),
            evaluateUpdateManifest(419, "4.65.0", same),
        )
    }

    @Test
    fun `确认有新版但没有下载地址按信息不完整处理`() {
        val broken = UpdateManifest(versionCode = 420, versionName = "4.66.0", downloadUrl = "")
        assertEquals(
            UpdateCheckResult.Failed(UpdateFailureKind.INCOMPLETE),
            evaluateUpdateManifest(419, "4.65.0", broken),
        )
    }

    @Test
    fun `确认有新版且字段齐全时给出更新清单`() {
        val next = UpdateManifest(
            versionCode = 420,
            versionName = "4.66.0",
            downloadUrl = "https://pan.quark.cn/s/02947cbc1d4e",
            shareToken = "/~9a263aTFyF~/",
            releaseNotesUrl = "https://shangke.asia/changelog.html",
        )

        val result = evaluateUpdateManifest(419, "4.65.0", next)
        assertTrue(result is UpdateCheckResult.UpdateAvailable, "应判定为有新版，实际 $result")
        assertEquals(next, result.manifest)
    }

    // --- 官网 version.json 配置体检（防止发版漏更新）---

    @Test
    fun `仓库里的 version_json 字段完整且指向夸克网盘`() {
        val file = File(repoRoot(), "website/version.json")
        assertTrue(file.isFile, "官网版本清单必须存在：${file.path}")

        val manifest = assertNotNull(
            parseUpdateManifest(file.readText()),
            "website/version.json 必须是合法清单：${file.path}",
        )

        assertTrue(manifest.versionCode > 0, "versionCode 必须为正，实际 ${manifest.versionCode}")
        assertTrue(manifest.versionName.isNotBlank(), "versionName 不能为空")
        assertTrue(
            manifest.downloadUrl.startsWith("https://pan.quark.cn/s/"),
            "下载落点应为夸克网盘分享页，实际「${manifest.downloadUrl}」",
        )
        assertTrue(
            manifest.shareToken.isNotBlank(),
            "夸克分享口令不能为空（对话框要用它做「复制分享口令」）",
        )
        assertTrue(
            manifest.releaseNotesUrl.isNotBlank(),
            "更新说明地址不能为空（对话框要用它做「查看更新说明」）",
        )
    }

    /** 仓库根由 Gradle 通过 `shangke.repoRoot` 系统属性注入，避免依赖测试进程工作目录。 */
    private fun repoRoot(): File {
        val path = System.getProperty("shangke.repoRoot")
            ?: fail("缺少 shangke.repoRoot 系统属性（应由 Gradle 测试任务注入）")
        return File(path)
    }
}
