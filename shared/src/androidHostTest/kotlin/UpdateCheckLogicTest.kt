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
              "shareToken": "~9a263aTFyF~:/",
              "releaseNotesUrl": "https://shangke.asia/changelog.html"
            }
        """.trimIndent()

        val manifest = assertNotNull(parseUpdateManifest(body), "完整清单必须解析成功")
        assertEquals(420, manifest.versionCode)
        assertEquals("4.66.0", manifest.versionName)
        assertEquals("https://pan.quark.cn/s/02947cbc1d4e", manifest.downloadUrl)
        assertEquals("~9a263aTFyF~:/", manifest.shareToken)
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
            shareToken = "~9a263aTFyF~:/",
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

    /**
     * P1-34：上面那条体检**只验形制**（正数 / 非空 / 前缀），不比对版本号 ——
     * 结果是 `website/version.json` 可以停在任意旧版本而体检**恒过**：
     * 实测该文件曾冻结在 4.66.0/420 长达 63 次版本迭代，导致存量用户被提示
     * 「发现新版本 4.66.0」、新用户永远「已是最新」。
     *
     * 这里补两条**版本区间**断言：
     *   ① versionName 必须真实出现在 CHANGELOG 里（不是笔误/不存在的版本）；
     *   ② versionCode 不得高于 `build.gradle.kts`（不得指向尚未构建的版本）。
     *
     * **刻意不在这里断言「不得落后 N 个版本」**：`version.json` 的口径是
     * 「最新**已真实发布**的版本」，而不是「最新的 CHANGELOG 条目」。
     * 本仓实测 `v4.74.1` 之后的 4.74.2~4.74.13 **全部未构建、未发 Release**
     * （CHANGELOG 各条均写明），因此 version.json 正确地停在 4.74.1 ——
     * 按「落后 CHANGELOG 几版」来判会**假阳性**。
     * 「是否落后于最新已发布版本」需要 git tag，单测里没有 git；
     * 该判据放在 `scripts/check_version_sync.py`（Python 门禁，可调 git）。
     */
    @Test
    fun `version_json 的版本号必须真实存在且不得指向未来`() {
        val manifest = assertNotNull(
            parseUpdateManifest(File(repoRoot(), "website/version.json").readText()),
            "website/version.json 必须是合法清单",
        )

        // gradle = APK 的真实版本源
        val gradleText = File(repoRoot(), "androidApp/build.gradle.kts").readText()
        val gCode = Regex("""^\s*versionCode\s*=\s*(\d+)\s*$""", RegexOption.MULTILINE)
            .find(gradleText)?.groupValues?.get(1)?.toInt()
            ?: fail("无法从 build.gradle.kts 解析 versionCode")
        val gName = Regex("""^\s*versionName\s*=\s*"([^"]+)"\s*$""", RegexOption.MULTILINE)
            .find(gradleText)?.groupValues?.get(1)
            ?: fail("无法从 build.gradle.kts 解析 versionName")

        // CHANGELOG 里的版本，按文件顺序（新 → 旧）
        val changelogVersions = Regex("""^### v(\d+\.\d+\.\d+)""", RegexOption.MULTILINE)
            .findAll(File(repoRoot(), "CHANGELOG.md").readText())
            .map { it.groupValues[1] }
            .toList()

        assertTrue(changelogVersions.isNotEmpty(), "CHANGELOG.md 里没有解析到任何版本条目")

        // ① 必须是真实发布过的版本 —— 否则说明 version.json 写了个不存在的版本
        assertTrue(
            manifest.versionName in changelogVersions,
            "website/version.json 的 versionName「${manifest.versionName}」不在 CHANGELOG 里，" +
                "说明它不是真实发布过的版本（CHANGELOG 最新几条：${changelogVersions.take(3)}）",
        )

        // ② 不得指向未来
        assertTrue(
            manifest.versionCode <= gCode,
            "website/version.json 的 versionCode（${manifest.versionCode}）高于 " +
                "build.gradle.kts 的（$gCode / $gName）—— 不得指向尚未构建的版本",
        )
    }

    /** 仓库根由 Gradle 通过 `shangke.repoRoot` 系统属性注入，避免依赖测试进程工作目录。 */
    private fun repoRoot(): File {
        val path = System.getProperty("shangke.repoRoot")
            ?: fail("缺少 shangke.repoRoot 系统属性（应由 Gradle 测试任务注入）")
        return File(path)
    }
}
