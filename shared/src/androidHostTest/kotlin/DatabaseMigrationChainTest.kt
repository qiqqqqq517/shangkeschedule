import com.shangkeschedule.data.db.main.ALL_MIGRATIONS
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 数据库迁移链完整性门禁（2026-10-03 巡检第 8 轮新增）。
 *
 * 为什么需要它：主库刻意**不开** `fallbackToDestructiveMigration`（真实课表数据不能被静默清空），
 * 所以 `MainAppDatabase.version` 每提升一次却漏写对应的 `Migration(v, v+1)`，老用户升级时就会
 * 直接 `IllegalStateException` 闪退。`DatabaseMigrations.kt` 里 3→4 / 4→5 的历史缺口正是这样留下来
 * 的（成因与处置见该文件 `ALL_MIGRATIONS` 上方的注释）。本测试把「发版前人工核对 schema version」
 * 这条守门方式变成自动门禁：缺口只允许存在于**对外发布之前**的版本区间。
 *
 * 覆盖范围：从 [EARLIEST_PUBLIC_SCHEMA]（实测对外发布过的最低 schema）到 `shared/schemas/` 导出目录
 * 里的最高版本；后者由 Room 在编译期按 `MainAppDatabase.version` 生成，因此「提版本忘了补迁移」
 * 会直接让本测试变红。
 */
class DatabaseMigrationChainTest {

    @Test
    fun `对外发布过的 schema 区间迁移链连续无缺口`() {
        val chain = ALL_MIGRATIONS.map { it.startVersion to it.endVersion }.toSet()
        val current = exportedSchemaVersions().max()
        for (version in EARLIEST_PUBLIC_SCHEMA until current) {
            assertTrue(
                (version to version + 1) in chain,
                "缺少 MIGRATION_${version}_${version + 1}：设备 DB 停留在 schema v$version 时升级到 " +
                    "v$current 会抛 IllegalStateException（主库无 destructive 兜底，用户课表数据无法恢复）",
            )
        }
    }

    @Test
    fun `迁移链终点等于已导出 schema 的最高版本`() {
        val current = exportedSchemaVersions().max()
        val maxMigration = ALL_MIGRATIONS.maxOf { it.endVersion }
        assertEquals(
            current,
            maxMigration,
            "MainAppDatabase.version 已提升到 $current，但 ALL_MIGRATIONS 最高只到 $maxMigration：" +
                "必须补一个单步 Migration($maxMigration, $current) 并加入 ALL_MIGRATIONS",
        )
    }

    @Test
    fun `迁移对象无重复且均为单步`() {
        val pairs = ALL_MIGRATIONS.map { it.startVersion to it.endVersion }
        assertEquals(pairs.size, pairs.toSet().size, "ALL_MIGRATIONS 存在重复的 (startVersion, endVersion)：$pairs")
        pairs.forEach { (start, end) ->
            assertEquals(start + 1, end, "MIGRATION_${start}_$end 不是单步迁移；本项目约定逐步迁移，便于定位与回归")
        }
    }

    /** 读取 `shared/schemas/<库名>/` 下 Room 导出的 schema 版本号（文件名即版本号）。 */
    private fun exportedSchemaVersions(): Set<Int> {
        val dir = File(repoRoot(), SCHEMA_DIR)
        assertTrue(dir.isDirectory, "未找到 Room schema 导出目录：${dir.absolutePath}")
        val versions = dir.listFiles { file -> file.isFile && file.name.endsWith(".json") }
            ?.mapNotNull { it.name.removeSuffix(".json").toIntOrNull() }
            ?.toSet()
            .orEmpty()
        assertTrue(versions.isNotEmpty(), "Room schema 导出目录为空：${dir.absolutePath}")
        return versions
    }

    /** 仓库根由 Gradle 通过 `shangke.repoRoot` 系统属性注入，避免依赖测试进程工作目录。 */
    private fun repoRoot(): File {
        val path = System.getProperty("shangke.repoRoot")
            ?: fail("缺少 shangke.repoRoot 系统属性（应由 Gradle 测试任务注入）")
        return File(path)
    }

    private companion object {
        /** Room schema 导出目录（见 `shared/build.gradle.kts` 的 `room3 { schemaDirectory(...) }`）。 */
        const val SCHEMA_DIR = "shared/schemas/com.shangkeschedule.data.db.main.MainAppDatabase"

        /**
         * 对外发布过的最低 schema 版本 = 9。
         *
         * 2026-10-03 全量取证：`git tag` 的 122 个 tag 中 `MainAppDatabase` 版本只取 {9,10,11,12,13}
         * （v2.12.0–v3.11.0 = 9；v3.13.1–v3.28.0 = 10；v3.33.0–v3.50.7 = 11；v3.50.9–v3.51.2 = 12；
         * v3.53.1–v4.64.23 = 13），`CHANGELOG.md` 最早条目为 v2.12.0（2026-08-25）。
         * v3/v4 只出现在 `shared/schemas/3.json`、`4.json` 记录的开发期，无证据表明有设备停留在此。
         */
        const val EARLIEST_PUBLIC_SCHEMA = 9
    }
}
