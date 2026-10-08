import com.shangkeschedule.tool.OfflineRepoArchive
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.openZip
import okio.use

/**
 * 内置离线适配资源包（单流容器）的回归测试。
 *
 * 锁定三条契约：
 * 1. 容器结构：`offline_schools.zip` 只含唯一入口 `repo.skr`（走 OfflineRepoArchive 的顺序流格式）；
 * 2. 解包完整性：解包出来的目录树与打包源 `shared/assets/offline_repo`（按打包排除规则过滤后）
 *    **路径集合一致、每个文件字节一致**——这是「容器格式改造不影响导入功能」的核心证据；
 * 3. 打包规则：文档 / 模板文件不进包（排除规则与 Gradle 任务保持一致）。
 *
 * 打包端写入格式在 `shared/build.gradle.kts` 的 `packSchoolsZip`，读取端在
 * `OfflineRepoArchive`，两侧必须逐字节对应；本测试是二者的唯一一致性护栏。
 */
class OfflineRepoArchiveTest {

    @Test
    fun archiveHasSingleStreamEntry() {
        val archive = offlineArchive()
        ZipFile(archive).use { zip ->
            val names = zip.entries().toList().map { it.name }.filter { !it.endsWith("/") }
            assertEquals(listOf(OfflineRepoArchive.ENTRY_NAME), names, "离线资源包应当只有单入口")
        }
    }

    @Test
    fun extractsByteIdenticalRepoTree() {
        val sourceRepo = File(repoRoot(), "shared/assets/offline_repo")
        assertTrue(sourceRepo.isDirectory, "打包源目录缺失：$sourceRepo")

        val expected = sourceRepo.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(sourceRepo).invariantSeparatorsPath }
            .filter { !isExcludedFromPackaging(it) }
            .toList()
        assertTrue(expected.size > 100, "打包源文件数量异常：${expected.size}")

        val target = File(System.getProperty("java.io.tmpdir"), "offline_repo_extract_${System.nanoTime()}")
        assertTrue(target.mkdirs(), "无法创建解包临时目录：$target")
        try {
            FileSystem.SYSTEM.openZip(offlineArchive().toOkioPath()).use { zipFileSystem ->
                OfflineRepoArchive.extract(FileSystem.SYSTEM, zipFileSystem, target.toOkioPath())
            }

            val actual = target.walkTopDown()
                .filter { it.isFile }
                .map { it.relativeTo(target).invariantSeparatorsPath }
                .toList()

            assertEquals(expected.sorted(), actual.sorted(), "解包文件集合与打包源不一致")

            for (relativePath in expected) {
                val actualBytes = File(target, relativePath).readBytes()
                val expectedBytes = File(sourceRepo, relativePath).readBytes()
                assertTrue(
                    actualBytes.contentEquals(expectedBytes),
                    "解包内容与打包源不一致：$relativePath"
                )
            }
        } finally {
            target.deleteRecursively()
        }
    }

    /**
     * 打包排除规则 —— **必须与 `shared/build.gradle.kts` 的 `packSchoolsZip.isExcluded` 保持一致**。
     *
     * P2-2：构建侧新增了 `timetable_schools.json`（构建期数据集，运行时从不读它，
     * 入口见 SchoolRepository / AdapterRemoteUpdater.INDEX_RELATIVE_PATH），
     * 若此处不同步，本测试会以「解包文件集合与打包源不一致」失败 —— 那不是被测代码坏了，
     * 而是**测试侧的判据副本漂移**。两处必须同改。
     */
    private fun isExcludedFromPackaging(relativePath: String): Boolean =
        relativePath.endsWith(".md") ||
            relativePath.endsWith("schools_template.json") ||
            relativePath.endsWith("timetable_schools.json")

    private fun offlineArchive(): File =
        File(repoRoot(), "shared/src/commonMain/composeResources/files/offline_schools.zip")

    /** 仓库根由 Gradle 通过 `shangke.repoRoot` 系统属性注入，避免依赖测试进程工作目录。 */
    private fun repoRoot(): File {
        val path = System.getProperty("shangke.repoRoot")
            ?: fail("缺少 shangke.repoRoot 系统属性（应由 Gradle 测试任务注入）")
        return File(path)
    }
}
