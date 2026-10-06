package com.shangkeschedule.tool

import com.shangkeschedule.data.repository.CourseNoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.openZip
import okio.use
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import shangkeschedule.shared.generated.resources.Res

/**
 * 资源与缓存初始化管理器。
 *
 * 负责应用启动时的静态离线资源解压校验，以及过期的分享与下载临时缓存清理。
 */
@Suppress("unused")
@Single(createdAtStart = true)
class ResourceInitializerManager(
    private val fileSystem: FileSystem,
    @Named("FilesDir") private val filesDir: Path,
    @Named("CacheDir") private val cacheDir: Path,
    private val adapterRemoteUpdater: AdapterRemoteUpdater,
    private val courseNoteRepository: CourseNoteRepository
) {
    private val targetRepoDir: Path = filesDir / "repo"
    private val shareTempDir: Path = cacheDir / "share_temp"

    /**
     * 初始化协程作用域。
     *
     * `SupervisorJob`：此前用 `CoroutineScope(Dispatchers.IO)`，其默认 `Job()` 不是
     * supervisor —— 三个子步骤中任一抛出都会**取消整个作用域并把异常交给默认 handler**，
     * Android 上即未捕获异常崩溃。三个步骤现在各自 `runCatching` 吞异常，但 `withContext`
     * 或将来新增语句抛出时这条隐患依旧。与仓库既有惯例一致
     * （CourseTableRepository / WidgetDataSynchronizer / SyncManager / StyleSettingsRepository
     * 均为「类内私有 scope + SupervisorJob」），故就地修而不引入新的 Koin 提供者。
     */
    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        initScope.launch {
            initializeOfflineRepo()
            clearTempCaches()
            // 追加：内置适配就绪后叠加远程安全更新；失败静默回退，不影响既有功能
            adapterRemoteUpdater.sync()
            // 追加：清理「数据库已无引用」的笔记图片文件（删除笔记时若被系统杀进程，
            // 会出现孤儿图片）。必须放在这里而不是更早：prune 以 DB 引用集为准，
            // 早于数据库初始化执行会把全部笔记图片误删。
            runCatching { courseNoteRepository.pruneOrphanImages() }
        }
    }

    /**
     * 校验并解压内置离线适配仓库。
     *
     * 通过对比内置 zip 的内容版本（size + hash）与本地已解压仓库的版本标记，
     * 实现脚本更新后自动重新解压同步，而无需每次启动都重复解压。
     *
     * @param forceOverwrite 是否强制清除并重新解压覆盖现有本地仓库
     */
    @OptIn(ExperimentalResourceApi::class)
    suspend fun initializeOfflineRepo(forceOverwrite: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val zipBytes = Res.readBytes("files/offline_schools.zip")
            val currentVersion = "${zipBytes.size}-${zipBytes.contentHashCode()}"

            val versionMarker = targetRepoDir / ".version"
            val repoReady = fileSystem.exists(targetRepoDir / "index")
            val existingVersion = if (repoReady && fileSystem.exists(versionMarker)) {
                fileSystem.read(versionMarker) { readUtf8() }
            } else {
                null
            }

            // 版本一致且仓库已就绪时，跳过重复解压
            if (!forceOverwrite && repoReady && existingVersion == currentVersion) {
                return@runCatching
            }

            val tempZipFile = filesDir / TEMP_OFFLINE_ZIP_NAME

            fileSystem.write(tempZipFile) {
                write(zipBytes)
            }

            try {
                //FIX:openZip 返回的文件系统持有压缩包句柄，原实现从不关闭会导致句柄泄漏并可能阻塞 tempZipFile 删除
                fileSystem.openZip(tempZipFile).use { zipFileSystem ->
                    // P2-8（2026-10-07）：原实现是「先 deleteRecursively 旧库 → 再原地解压」，
                    // 两步之间进程被杀/断电会留下**残缺的离线库** ⇒ 全部适配脚本失效、App 实质不可用，
                    // 且下次启动因版本标记未写而重试前，用户已经打不开任何学校。
                    // 现改为「解压到临时目录 → 原子换名」：任一步失败都保留**完整可用的旧库**。
                    val stagingDir = filesDir / STAGING_REPO_DIR_NAME
                    if (fileSystem.exists(stagingDir)) {
                        fileSystem.deleteRecursively(stagingDir)
                    }
                    fileSystem.createDirectories(stagingDir)
                    OfflineRepoArchive.extract(fileSystem, zipFileSystem, stagingDir)

                    // 换名提交：先把旧库挪到 backup（不删），再把完整的新库换入，最后才删 backup。
                    // 任一步中断，target 要么是「完整旧库」（换入前）要么是「完整新库」（换入后），
                    // **绝不会是残缺态** —— 这正是本修复的目的。
                    val backupDir = filesDir / BACKUP_REPO_DIR_NAME
                    if (fileSystem.exists(backupDir)) {
                        fileSystem.deleteRecursively(backupDir)
                    }
                    if (fileSystem.exists(targetRepoDir)) {
                        fileSystem.atomicMove(targetRepoDir, backupDir)
                    }
                    fileSystem.atomicMove(stagingDir, targetRepoDir)
                    if (fileSystem.exists(backupDir)) {
                        fileSystem.deleteRecursively(backupDir)
                    }

                    // 记录本次解压对应的版本，供下次启动对比
                    fileSystem.write(versionMarker) {
                        writeUtf8(currentVersion)
                    }
                }
            } finally {
                fileSystem.delete(tempZipFile)
            }
        }
    }

    /**
     * 清理应用生成的临时文件与过期缓存目录。
     */
    suspend fun clearTempCaches(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (fileSystem.exists(shareTempDir)) {
                fileSystem.deleteRecursively(shareTempDir)
            }

            // 离线仓库解压用的临时 zip：正常路径由 initializeOfflineRepo 的 finally 删除。
            // 若进程恰好在写完 `.version` 之后、删临时文件之前被杀，下次启动会因版本一致
            // 而在 :84 提前返回，该文件就永久留在 filesDir（属自动备份 root 域，且离线包含数 MB）。
            val tempOfflineZip = filesDir / TEMP_OFFLINE_ZIP_NAME
            if (fileSystem.exists(tempOfflineZip)) fileSystem.delete(tempOfflineZip)

            val tempSchoolsRepo = cacheDir / "temp_schools_repo"
            val tempIndexRepo = cacheDir / "temp_index_repo"
            if (fileSystem.exists(tempSchoolsRepo)) fileSystem.deleteRecursively(tempSchoolsRepo)
            if (fileSystem.exists(tempIndexRepo)) fileSystem.deleteRecursively(tempIndexRepo)
        }
    }

    private companion object {
        /** 离线仓库解压用的临时 zip 名：写入点与清理点共用，避免两处字符串漂移。 */
        const val TEMP_OFFLINE_ZIP_NAME = "temp_offline_schools.zip"

        /** P2-8：解压暂存目录 —— 解包全部成功后才原子换入正式目录，避免留下残缺库。 */
        const val STAGING_REPO_DIR_NAME = "schools__staging"

        /** P2-8：换入前的旧库备份目录 —— 换入成功即删除，失败时用于保留可用旧库。 */
        const val BACKUP_REPO_DIR_NAME = "schools__backup"
    }
}
