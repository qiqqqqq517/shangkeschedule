package com.shangkeschedule.tool

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
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
    @Named("CacheDir") private val cacheDir: Path
) {
    private val targetRepoDir: Path = filesDir / "repo"
    private val shareTempDir: Path = cacheDir / "share_temp"

    init {
        CoroutineScope(Dispatchers.IO).launch {
            initializeOfflineRepo()
            clearTempCaches()
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

            val tempZipFile = filesDir / "temp_offline_schools.zip"

            fileSystem.write(tempZipFile) {
                write(zipBytes)
            }

            try {
                val zipFileSystem = fileSystem.openZip(tempZipFile)

                if (fileSystem.exists(targetRepoDir)) {
                    fileSystem.deleteRecursively(targetRepoDir)
                }
                fileSystem.createDirectories(targetRepoDir)

                unzipDirectory(zipFileSystem, "/".toPath(), targetRepoDir)

                // 记录本次解压对应的版本，供下次启动对比
                fileSystem.write(versionMarker) {
                    writeUtf8(currentVersion)
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

            val tempSchoolsRepo = cacheDir / "temp_schools_repo"
            val tempIndexRepo = cacheDir / "temp_index_repo"
            if (fileSystem.exists(tempSchoolsRepo)) fileSystem.deleteRecursively(tempSchoolsRepo)
            if (fileSystem.exists(tempIndexRepo)) fileSystem.deleteRecursively(tempIndexRepo)
        }
    }

    /**
     * 递归解压 Zip 虚拟文件系统中的目录与文件。
     */
    private fun unzipDirectory(
        zipFileSystem: FileSystem,
        currentZipPath: Path,
        targetDir: Path
    ) {
        val entries = zipFileSystem.list(currentZipPath)
        for (entry in entries) {
            val destinationPath = targetDir / entry.name

            if (!destinationPath.toString().startsWith(targetDir.toString())) {
                throw IllegalArgumentException("Illegal zip path: ${entry.name}")
            }

            val metadata = zipFileSystem.metadata(entry)

            if (metadata.isDirectory) {
                fileSystem.createDirectories(destinationPath)
                unzipDirectory(zipFileSystem, entry, destinationPath)
            } else {
                destinationPath.parent?.let { fileSystem.createDirectories(it) }

                zipFileSystem.source(entry).use { source ->
                    fileSystem.sink(destinationPath).buffer().use { sink ->
                        sink.writeAll(source)
                    }
                }
            }
        }
    }
}