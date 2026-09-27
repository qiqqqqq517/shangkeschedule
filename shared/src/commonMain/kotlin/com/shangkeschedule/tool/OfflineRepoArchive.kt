package com.shangkeschedule.tool

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use

/**
 * 内置离线适配资源包（`composeResources/files/offline_schools.zip`）的读取实现。
 *
 * ## 容器格式（与 Gradle 任务 `packSchoolsZip` 的写入端严格一一对应）
 *
 * zip 内只有 [ENTRY_NAME] 一个条目，条目内容是自定义顺序流：
 *
 * ```
 * "SKR1"                      4 字节魔数
 * u32 LE fileCount            文件数
 * 重复 fileCount 次 {
 *     u32 LE pathByteLength   相对路径字节数（UTF-8，"a/b/c.js"）
 *     path                    UTF-8 路径字节
 *     u32 LE dataByteLength   文件内容字节数
 *     data                    文件内容
 * }
 * ```
 *
 * ## 为什么不是「逐文件压缩的 zip」
 *
 * 离线仓库里是 195 个学校适配脚本 + 学校索引，脚本之间存在大量公共模板代码。
 * 逐文件 deflate 时每个文件都要从零建立自己的压缩字典，跨文件的重复内容无法复用；
 * 合并成**一条 deflate 流**后，压缩器可以在整个仓库范围内复用字典，实测把
 * 932 KB（逐文件 zip）压到约 723 KB（-22%，省约 209 KB），而解压后的目录结构、
 * 文件内容与逐文件 zip 完全一致（见 `OfflineRepoArchiveTest`）。
 *
 * 读取采用顺序流式解包：不把整个仓库读进内存，按条目边读边落盘。
 */
internal object OfflineRepoArchive {

    /** zip 内唯一条目的名字，必须与 `shared/build.gradle.kts` 的 `packSchoolsZip` 保持一致。 */
    const val ENTRY_NAME: String = "repo.skr"

    /** 格式魔数："SKR1"（ShangKe Repo v1）。 */
    private val MAGIC: ByteArray = byteArrayOf(0x53, 0x4B, 0x52, 0x31)

    /** 单条路径长度上限，防御损坏包导致的异常分配。 */
    private const val MAX_PATH_BYTES = 4096

    /** 单个文件大小上限，离线仓库最大文件（学校索引 pb）约 0.4 MB。 */
    private const val MAX_FILE_BYTES = 64L * 1024 * 1024

    /**
     * 把离线资源包解包到 [targetDir]。
     *
     * @param fileSystem 落盘用的文件系统（真实文件系统）
     * @param zipFileSystem 已打开的离线资源包 zip 文件系统
     * @param targetDir 解包目标根目录（必须已存在）
     */
    fun extract(fileSystem: FileSystem, zipFileSystem: FileSystem, targetDir: Path) {
        val entryPath = ENTRY_NAME.toPath()
        require(zipFileSystem.exists(entryPath)) {
            "离线资源包缺少 $ENTRY_NAME 条目"
        }

        zipFileSystem.source(entryPath).buffer().use { source ->
            val magic = source.readByteArray(MAGIC.size.toLong())
            require(magic.contentEquals(MAGIC)) { "离线资源包格式不匹配（魔数校验失败）" }

            val fileCount = source.readIntLe()
            require(fileCount >= 0) { "离线资源包文件数非法：$fileCount" }

            repeat(fileCount) {
                val pathByteLength = source.readIntLe()
                require(pathByteLength in 1..MAX_PATH_BYTES) { "离线资源包路径长度非法：$pathByteLength" }
                val relativePath = source.readUtf8(pathByteLength.toLong())

                val dataByteLength = source.readIntLe()
                require(dataByteLength >= 0 && dataByteLength.toLong() <= MAX_FILE_BYTES) {
                    "离线资源包文件长度非法：$dataByteLength"
                }

                val destinationPath = resolveInside(targetDir, relativePath)
                destinationPath.parent?.let { fileSystem.createDirectories(it) }
                fileSystem.sink(destinationPath).buffer().use { sink ->
                    sink.write(source, dataByteLength.toLong())
                }
            }
        }
    }

    /**
     * 把包内相对路径解析为目标目录下的安全路径。
     *
     * 与旧 zip 解包保持同一防线：拒绝绝对路径、上跳路径，并保证结果仍在 [targetDir] 内
     * （前缀校验必须要求等于根目录或带路径分隔符，避免 `repo_evil` 被误判为 `repo` 内路径）。
     */
    private fun resolveInside(targetDir: Path, relativePath: String): Path {
        require(relativePath.isNotEmpty()) { "离线资源包出现空路径" }
        require(!relativePath.startsWith("/") && !relativePath.startsWith("\\")) {
            "离线资源包出现绝对路径：$relativePath"
        }
        require(relativePath.split('/', '\\').none { it.isEmpty() || it == "." || it == ".." || it.contains(':') }) {
            "离线资源包出现非法路径：$relativePath"
        }

        // 前缀校验：结果必须落在 targetDir 内，且紧跟根目录之后必须是路径分隔符
        // （否则 repo_evil 会被误判为 repo 内路径）。分隔符同时兼容 "/" 与 "\"：
        // 安卓 / iOS 为 "/"，桌面端在 Windows 上是 "\"。
        val root = targetDir.toString()
        val destination = (targetDir / relativePath).toString()
        require(
            destination.length > root.length &&
                destination.startsWith(root) &&
                (destination[root.length] == '/' || destination[root.length] == '\\')
        ) { "离线资源包出现越界路径：$relativePath" }
        return destination.toPath()
    }
}
