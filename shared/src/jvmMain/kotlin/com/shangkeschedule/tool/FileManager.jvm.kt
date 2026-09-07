package com.shangkeschedule.tool

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * JVM（桌面端）文件管理实现：Swing JFileChooser 承接系统文件选择/保存对话框。
 * 回调统一经 SwingUtilities.invokeLater 派发（Compose Desktop 主线程 = EDT）。
 */
class DesktopFileManager(
    private val callbacksProvider: () -> FileManagerCallbacks
) : FileManager {

    private fun callbacks(): FileManagerCallbacks = callbacksProvider()

    private fun dispatch(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
    }

    override fun pickImage() {
        dispatch {
            val chooser = JFileChooser()
            chooser.fileFilter = FileNameExtensionFilter("Images (png/jpg/jpeg/webp)", "png", "jpg", "jpeg", "webp")
            val result = chooser.showOpenDialog(null)
            if (result != JFileChooser.APPROVE_OPTION) {
                callbacks().onImagePicked?.invoke(null)
                return@dispatch
            }
            val file = chooser.selectedFile ?: run {
                callbacks().onImagePicked?.invoke(null)
                return@dispatch
            }
            Thread {
                try {
                    val buffered = ImageIO.read(file)
                    val bitmap: ImageBitmap? = buffered?.toComposeImageBitmap()
                    dispatch { callbacks().onImagePicked?.invoke(bitmap) }
                } catch (_: Exception) {
                    dispatch { callbacks().onImagePicked?.invoke(null) }
                }
            }.start()
        }
    }

    override fun importFile(allowedExtensions: List<String>) {
        dispatch {
            val chooser = JFileChooser()
            if (allowedExtensions.isNotEmpty()) {
                chooser.fileFilter = FileNameExtensionFilter(
                    "Files (" + allowedExtensions.joinToString("/") { it.uppercase() } + ")",
                    *allowedExtensions.toTypedArray()
                )
                chooser.isAcceptAllFileFilterUsed = false
            }
            val result = chooser.showOpenDialog(null)
            if (result != JFileChooser.APPROVE_OPTION) {
                callbacks().onFileImported?.invoke(null, null)
                return@dispatch
            }
            val file = chooser.selectedFile ?: run {
                callbacks().onFileImported?.invoke(null, null)
                return@dispatch
            }
            Thread {
                try {
                    val bytes = file.readBytes()
                    dispatch { callbacks().onFileImported?.invoke(bytes, file.name) }
                } catch (_: Exception) {
                    dispatch { callbacks().onFileImported?.invoke(null, file.name) }
                }
            }.start()
        }
    }

    override fun exportFile(defaultFileName: String, bytes: ByteArray) {
        dispatch {
            val chooser = JFileChooser()
            chooser.selectedFile = File(defaultFileName)
            val result = chooser.showSaveDialog(null)
            if (result != JFileChooser.APPROVE_OPTION) {
                callbacks().onFileExported?.invoke(false)
                return@dispatch
            }
            val file = chooser.selectedFile
            if (file == null) {
                callbacks().onFileExported?.invoke(false)
                return@dispatch
            }
            Thread {
                val success = try {
                    file.writeBytes(bytes)
                    true
                } catch (_: Exception) {
                    false
                }
                dispatch { callbacks().onFileExported?.invoke(success) }
            }.start()
        }
    }
}

/**
 * 桌面端 rememberFileManager 实现。
 * rememberUpdatedState 保证重绘时始终读取最新回调（与 Android 端一致）。
 */
@Composable
actual fun rememberFileManager(callbacks: FileManagerCallbacks): FileManager {
    val currentCallbacks by rememberUpdatedState(callbacks)
    return androidx.compose.runtime.remember { DesktopFileManager { currentCallbacks } }
}
