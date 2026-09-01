package com.shangkeschedule.tool

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.shangkeschedule.ui.components.ToastManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.webkit.MimeTypeMap

class AndroidFileManager(
    private val onPickImage: () -> Unit,
    private val onImportFile: (List<String>) -> Unit,
    private val onExportFile: (String, ByteArray) -> Unit
) : FileManager {
    override fun pickImage() = onPickImage()
    override fun importFile(allowedExtensions: List<String>) = onImportFile(allowedExtensions)
    override fun exportFile(defaultFileName: String, bytes: ByteArray) = onExportFile(defaultFileName, bytes)
}

/** 查询 SAF Uri 的显示文件名（含扩展名），失败时退回 uri.lastPathSegment */
private fun queryDisplayName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
    } ?: uri.lastPathSegment
} catch (_: Exception) {
    uri.lastPathSegment
}

/**
 * 构建文件导入 Intent。
 *
 * 背景（本类多次踩坑后的结论，勿轻易改动）：
 * - 标准设备的最佳路径是 ACTION_OPEN_DOCUMENT（直接进 DocumentsUI，支持多 MIME 过滤）。
 * - 但部分 ROM（实测 MIUI/Android 14）把 Google DocumentsUI 禁用了，OPEN_DOCUMENT / CREATE_DOCUMENT
 *   解析不到任何 Activity；而 ACTION_GET_CONTENT 的最高优先级处理者却是媒体库的
 *   PhotoPickerGetContentActivity，它会把请求转发给（已禁用的）DocumentsUI 后立即取消——
 *   表现为「选择器一闪而过/打不开」，旧版还会因 ActivityNotFoundException 直接闪退。
 * - 因此这里按优先级动态选路：
 *   ① OPEN_DOCUMENT 可解析 → 用它；
 *   ② 否则 GET_CONTENT，但把解析到的「媒体库/相册/联系人/播放器」等非文件管理处理者排除，
 *      强制路由到真正的文件管理器（如 MIUI 文件管理的 FileActivity）；
 *   ③ 都没有 → 原样 GET_CONTENT，交给系统解析。
 */
private fun buildImportIntent(context: Context): Intent {
    val pm = context.packageManager

    val openDocument = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
    }
    if (pm.resolveActivity(openDocument, PackageManager.MATCH_DEFAULT_ONLY) != null) {
        return openDocument
    }

    val getContent = Intent(Intent.ACTION_GET_CONTENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
    }
    val handlers = runCatching {
        pm.queryIntentActivities(getContent, PackageManager.MATCH_DEFAULT_ONLY)
    }.getOrDefault(emptyList())

    // 排除会把请求带走/转发的非文件管理器处理者
    val excludedPrefixes = listOf(
        "com.android.providers.media",
        "com.google.android.documentsui", // 被禁用时不会出现在列表，防御性排除
        "com.android.contacts",
        "com.miui.gallery",
        "com.miui.player"
    )
    val candidates = handlers.filter { info ->
        val pkg = info.activityInfo.packageName
        !excludedPrefixes.any { pkg.startsWith(it) }
    }
    // 优先选择通用文件入口（避免视频/音频/PDF 等专用筛选器）
    val generic = candidates.firstOrNull {
        it.activityInfo.name.endsWith(".FileActivity") || it.activityInfo.name.endsWith(".FilesActivity")
    } ?: candidates.firstOrNull()

    return when {
        generic != null -> getContent.apply {
            setClassName(generic.activityInfo.packageName, generic.activityInfo.name)
        }
        else -> getContent
    }
}

/** 设备上是否存在可用的 ACTION_CREATE_DOCUMENT 处理者 */
private fun canCreateDocument(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
    }
    return runCatching {
        context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
    }.getOrDefault(false)
}

private fun mimeForFileName(fileName: String): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(
        fileName.substringAfterLast('.', "").lowercase()
    ) ?: "application/octet-stream"

/**
 * 无 CREATE_DOCUMENT 处理者时的导出兜底：经 MediaStore 写入「下载/ShangKe」目录。
 * 返回成功时的展示路径。
 */
private fun saveToPublicDownloads(context: Context, fileName: String, bytes: ByteArray): Result<String> = try {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeForFileName(fileName))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ShangKe")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Downloads.EXTERNAL_CONTENT_URI
    } else {
        // Android 8/9：Downloads 集合不可用，退回应用私有目录（无需存储权限）
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.let { dir ->
            val out = java.io.File(dir, fileName)
            out.outputStream().use { it.write(bytes) }
            return Result.success(out.absolutePath)
        } ?: return Result.failure(IllegalStateException("外部存储不可用"))
    }
    val uri = resolver.insert(collection, values)
        ?: return Result.failure(IllegalStateException("无法创建下载文件"))
    resolver.openOutputStream(uri)?.use { it.write(bytes) }
        ?: return Result.failure(IllegalStateException("无法写入下载文件"))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        resolver.update(uri, done, null, null)
    }
    Result.success("下载/ShangKe/$fileName")
} catch (e: Exception) {
    Result.failure(e)
}

@Composable
actual fun rememberFileManager(callbacks: FileManagerCallbacks): FileManager {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 使用 rememberUpdatedState 保证在重绘时始终能拿到最新的回调
    val currentCallbacks by rememberUpdatedState(callbacks)

    // 1. 图片选择器 (PickVisualMedia)
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) {
            currentCallbacks.onImagePicked?.invoke(null)
            return@rememberLauncherForActivityResult
        }
        scope.launch(Dispatchers.IO) {
            val bitmap = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
            withContext(Dispatchers.Main) {
                currentCallbacks.onImagePicked?.invoke(bitmap)
            }
        }
    }

    // 2. 文件导入器：动态选路（见 buildImportIntent 注释）
    val importLauncher = rememberLauncherForActivityResult(
        contract = object : ActivityResultContracts.GetContent() {
            override fun createIntent(context: Context, input: String): Intent =
                buildImportIntent(context)
        }
    ) { uri ->
        if (uri == null) {
            currentCallbacks.onFileImported?.invoke(null, null)
            return@rememberLauncherForActivityResult
        }
        scope.launch(Dispatchers.IO) {
            val bytes = try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
            val fileName = queryDisplayName(context, uri)
            withContext(Dispatchers.Main) {
                currentCallbacks.onFileImported?.invoke(bytes, fileName)
            }
        }
    }

    // 3. 文件导出器 (CreateDocument)
    var pendingExportBytes by remember { mutableStateOf<ByteArray?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val bytesToWrite = pendingExportBytes
        if (uri == null || bytesToWrite == null) {
            currentCallbacks.onFileExported?.invoke(false)
            return@rememberLauncherForActivityResult
        }
        scope.launch(Dispatchers.IO) {
            val success = try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(bytesToWrite)
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
            pendingExportBytes = null
            withContext(Dispatchers.Main) {
                currentCallbacks.onFileExported?.invoke(success)
            }
        }
    }

    return remember {
        AndroidFileManager(
            onPickImage = {
                try {
                    imageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } catch (e: Exception) {
                    ToastManager.show("无法打开图片选择器：${e.message ?: ""}")
                }
            },
            onImportFile = { _ ->
                try {
                    importLauncher.launch("*/*")
                } catch (e: Exception) {
                    // 兜底：选择器拉起失败不再导致闪退，仅提示用户
                    ToastManager.show("无法打开文件选择器：${e.message ?: ""}")
                    currentCallbacks.onFileImported?.invoke(null, null)
                }
            },
            onExportFile = { fileName, bytes ->
                if (canCreateDocument(context)) {
                    pendingExportBytes = bytes
                    try {
                        exportLauncher.launch(fileName)
                    } catch (e: Exception) {
                        pendingExportBytes = null
                        ToastManager.show("无法打开保存对话框：${e.message ?: ""}")
                        currentCallbacks.onFileExported?.invoke(false)
                    }
                } else {
                    // 本机无 SAF 保存入口（如 MIUI 禁用 DocumentsUI）：直接写入公共下载目录
                    scope.launch(Dispatchers.IO) {
                        val result = saveToPublicDownloads(context, fileName, bytes)
                        withContext(Dispatchers.Main) {
                            result.fold(
                                onSuccess = { path ->
                                    ToastManager.show("已保存到 $path")
                                    currentCallbacks.onFileExported?.invoke(true)
                                },
                                onFailure = { e ->
                                    ToastManager.show("保存失败：${e.message ?: ""}")
                                    currentCallbacks.onFileExported?.invoke(false)
                                }
                            )
                        }
                    }
                }
            }
        )
    }
}
