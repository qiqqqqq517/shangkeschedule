import com.shangkeschedule.tool.AdapterRemoteUpdater
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * P2-13：`AdapterRemoteUpdater` 此前**零单测**，而其中三个纯函数（路径安全判定、
 * 沙箱约束、远程路径映射）正是「清单不可信时不许写到 repo 之外」这条防线的全部逻辑
 * —— 一旦它们退化，OTA 就能把文件写到沙箱外。这里逐个补回归。
 *
 * 刻意**不引入 `okio-fakefilesystem` 依赖**：这三个函数完全不碰磁盘
 * （只用 `path.normalized()` 做纯字符串归一），传 `FileSystem.SYSTEM` 即可，
 * 全程不产生任何真实文件读写。
 */
class AdapterRemoteUpdaterPathTest {

    private fun updater(): AdapterRemoteUpdater =
        AdapterRemoteUpdater(FileSystem.SYSTEM, "/files".toPath())

    // ---------- isSafeRelativePath ----------

    @Test
    fun safeRelativePathAcceptsPlainNestedPaths() {
        val u = updater()
        assertTrue(u.isSafeRelativePath("foo.js"))
        assertTrue(u.isSafeRelativePath("a/b/c.js"))
        assertTrue(u.isSafeRelativePath("a_b-c.d.js"))
    }

    @Test
    fun safeRelativePathRejectsTraversalAndAbsolute() {
        val u = updater()
        // 每一项都是必须被拦下的形态；漏掉任一项就等于开了穿越口子。
        val bad = listOf(
            "",                 // 空
            "../etc/passwd",    // 上级目录
            "a/../../b",        // 中途上跳
            "a/./b",            // 当前目录段
            "a//b",             // 空段
            "/etc/passwd",      // 绝对路径
            "a\\b",             // 反斜杠（Windows 分隔符）
            "a\u0000b",         // 空字节（截断技巧）
            "%2e%2e/x",         // URL 编码的 ..
            "a/%2f/b",          // URL 编码的 /
            "a/%5C/b",          // URL 编码的 \
        )
        for (p in bad) {
            assertTrue(!u.isSafeRelativePath(p), "应拒绝：[$p]")
        }
    }

    // ---------- confinedToRepo ----------

    @Test
    fun confinedToRepoAcceptsPathsInsideRepo() {
        val u = updater()
        assertNotNull(u.confinedToRepo("/files/repo/schools/resources/a.js".toPath()))
        assertNotNull(u.confinedToRepo("/files/repo/index/school_index.pb".toPath()))
    }

    @Test
    fun confinedToRepoRejectsPathsOutsideOrSiblingPrefix() {
        val u = updater()
        assertNull(u.confinedToRepo("/files/other/a.js".toPath()), "repo 之外必须拒绝")
        // 前缀混淆：repo_evil 以 "repo" 开头但不是 repo 目录，必须被拦
        assertNull(u.confinedToRepo("/files/repo_evil/a.js".toPath()), "repo_evil 不得被当作 repo 内")
    }

    // ---------- resolveLocalPath ----------

    @Test
    fun resolveLocalPathMapsAdaptersAndIndex() {
        val u = updater()
        val a = u.resolveLocalPath("adapters/jxzy/foo.js")
        assertEquals("/files/repo/schools/resources/jxzy/foo.js", a?.toString())

        val idx = u.resolveLocalPath("index/school_index.pb")
        assertEquals("/files/repo/index/school_index.pb", idx?.toString())
    }

    @Test
    fun resolveLocalPathRejectsUnknownAndTraversal() {
        val u = updater()
        assertNull(u.resolveLocalPath("random/foo.js"), "未知前缀必须拒绝")
        assertNull(u.resolveLocalPath("adapters/../../escape.js"), "穿越必须拒绝")
        assertNull(u.resolveLocalPath("adapters/"), "空相对路径必须拒绝")
        assertNull(u.resolveLocalPath(""), "空路径必须拒绝")
    }
}
