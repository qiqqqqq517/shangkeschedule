package com.shangkeschedule.data.repository

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioSerializer
import com.shangkeschedule.data.model.DualColor
import com.shangkeschedule.data.model.ScheduleGridStyle
import com.shangkeschedule.data.model.schedule_style.BorderTypeProto
import com.shangkeschedule.data.model.schedule_style.ScheduleGridStyleProto
import com.shangkeschedule.data.model.schedule_style.ScheduleModeProto
import com.shangkeschedule.data.model.toCompose
import com.shangkeschedule.data.model.toProto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import okio.BufferedSink
import okio.BufferedSource
import org.koin.core.annotation.Single

/** DataStore 文件名常量 */
const val SCHEDULE_STYLE_DATASTORE_FILE_NAME = "schedule_style_settings.pb"

/**
 * 样式配置的 DataStore 序列化器，基于 Wire 协议与 Okio 跨平台流实现。
 */
object ScheduleStyleSerializer : OkioSerializer<ScheduleGridStyleProto> {
    override val defaultValue: ScheduleGridStyleProto
        get() = ScheduleGridStyleProto()

    override suspend fun readFrom(source: BufferedSource): ScheduleGridStyleProto {
        return try {
            ScheduleGridStyleProto.ADAPTER.decode(source)
        } catch (e: Exception) {
            defaultValue
        }
    }

    override suspend fun writeTo(t: ScheduleGridStyleProto, sink: BufferedSink) {
        ScheduleGridStyleProto.ADAPTER.encode(sink, t)
    }
}

/**
 * 样式备份信封，包含版本号及序列化后的字节数据。
 */
@Serializable
data class StyleBackupEnvelope(
    val backupTimestamp: Long,
    val appVersionCode: Int,
    val styleProtoBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StyleBackupEnvelope) return false

        if (backupTimestamp != other.backupTimestamp) return false
        if (appVersionCode != other.appVersionCode) return false
        if (!styleProtoBytes.contentEquals(other.styleProtoBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = backupTimestamp.hashCode()
        result = 31 * result + appVersionCode
        result = 31 * result + styleProtoBytes.contentHashCode()
        return result
    }
}

/**
 * 样式设置的数据仓库，负责与 Proto DataStore 进行交互及状态管理。
 */
@Single
class StyleSettingsRepository(
    private val dataStore: DataStore<ScheduleGridStyleProto>
) {

    companion object {
        /** 当前样式备份的版本号 */
        const val STYLE_SCHEMA_VERSION = 1
    }

    // 内存缓存：避免进入外观设置页时因异步加载导致初始值闪烁
    private val styleCache = MutableStateFlow<ScheduleGridStyle?>(null)

    init {
        // 异步预热缓存
        GlobalScope.launch(Dispatchers.Default) {
            try {
                val current = dataStore.data.map { it.toCompose() }.first()
                styleCache.value = current
            } catch (_: Exception) {
                // 预热失败，后续仍可从 dataStore 读取
            }
        }
    }

    // --- 备份与恢复扩展 API ---

    /**
     * 仅导出当前原生的样式配置字节数组，排除壁纸路径。
     */
    suspend fun exportRawStyleBytes(): ByteArray {
        val currentProto = dataStore.data.first()
        val exportProto = currentProto.copy(background_image_path = "")
        return ScheduleGridStyleProto.ADAPTER.encode(exportProto)
    }

    /**
     * 将还原的字节数组与本地壁纸路径合并后写入 DataStore。
     */
    suspend fun restoreRawStyleBytes(bytes: ByteArray): Result<Unit> = runCatching {
        val currentLocalProto = dataStore.data.first()
        val localWallpaperPath = currentLocalProto.background_image_path

        val backupProto = ScheduleGridStyleProto.ADAPTER.decode(bytes)
        val finalProto = backupProto.copy(background_image_path = localWallpaperPath)

        dataStore.updateData { finalProto }
    }

    /**
     * 获取当前样式的单次快照。
     */
    suspend fun getStyleOnce(): ScheduleGridStyle {
        return dataStore.data.map { it.toCompose() }.first()
    }

    /**
     * 响应式样式数据流。
     * 每次发射时同步更新内存缓存，供 ViewModel 作为初始值避免闪烁。
     */
    val styleFlow: Flow<ScheduleGridStyle> = dataStore.data
        .map { proto -> proto.toCompose() }
        .onEach { style -> styleCache.value = style }

    /**
     * 获取内存缓存的样式（可能为 null，用于 ViewModel 初始值）。
     */
    fun getCachedStyle(): ScheduleGridStyle? = styleCache.value

    private suspend fun updateStyle(
        transform: (ScheduleGridStyleProto) -> ScheduleGridStyleProto
    ) {
        dataStore.updateData { currentProto ->
            transform(currentProto)
        }
    }

    // --- 原子化公共写入 API (Setters) ---

    /** 设置时间列宽度 (DP) */
    suspend fun setTimeColumnWidth(widthDp: Float) = updateStyle {
        it.copy(time_column_width_dp = widthDp)
    }

    /** 设置日表头高度 (DP) */
    suspend fun setDayHeaderHeight(heightDp: Float) = updateStyle {
        it.copy(day_header_height_dp = heightDp)
    }

    /** 设置节次高度 (DP) */
    suspend fun setSectionHeight(heightDp: Float) = updateStyle {
        it.copy(section_height_dp = heightDp)
    }

    /** 设置课程块圆角半径 (DP) */
    suspend fun setCourseBlockCornerRadius(radiusDp: Float) = updateStyle {
        it.copy(course_block_corner_radius_dp = radiusDp)
    }

    /** 设置课程块外部边距 (DP) */
    suspend fun setCourseBlockOuterPadding(paddingDp: Float) = updateStyle {
        it.copy(course_block_outer_padding_dp = paddingDp)
    }

    /** 设置课程块内部填充 (DP) */
    suspend fun setCourseBlockInnerPadding(paddingDp: Float) = updateStyle {
        it.copy(course_block_inner_padding_dp = paddingDp)
    }

    /** 设置课程块透明度 */
    suspend fun setCourseBlockAlpha(alpha: Float) = updateStyle {
        it.copy(course_block_alpha_float = alpha)
    }

    /** 设置课程颜色映射列表 */
    suspend fun setCourseColorMaps(maps: List<DualColor>) {
        updateStyle {
            it.copy(course_color_maps = maps.map { dc -> dc.toProto() })
        }
    }

    /** 重置所有样式设置为默认值 */
    suspend fun resetAllStyleSettings() {
        dataStore.updateData {
            ScheduleGridStyleProto()
        }
    }

    /**
     * 一键应用某个完整视觉预设。
     *
     * 保留用户自己的壁纸与时间轴模式（传统节次/24小时），避免切换外观时
     * 意外清掉背景图或改变展示逻辑。
     */
    suspend fun applyStylePreset(style: ScheduleGridStyle) = updateStyle { currentProto ->
        val wallpaper = currentProto.background_image_path
        val scheduleMode = currentProto.schedule_mode
        style.toProto().copy(
            background_image_path = wallpaper,
            schedule_mode = scheduleMode
        )
    }

    /** 设置是否隐藏左侧时间列的具体时间 */
    suspend fun setHideSectionTime(hide: Boolean) = updateStyle {
        it.copy(hide_section_time = hide)
    }

    /** 设置是否隐藏星期栏下的日期 */
    suspend fun setHideDateUnderDay(hide: Boolean) = updateStyle {
        it.copy(hide_date_under_day = hide)
    }

    /** 设置是否隐藏网格线 */
    suspend fun setHideGridLines(hide: Boolean) = updateStyle {
        it.copy(hide_grid_lines = hide)
    }

    /** 设置是否在课程格内显示开始时间 */
    suspend fun setShowStartTime(show: Boolean) = updateStyle {
        it.copy(show_start_time = show)
    }

    /** 设置课程块字体缩放比例 */
    suspend fun setCourseBlockFontScale(scale: Float) = updateStyle {
        it.copy(course_block_font_scale = scale)
    }

    /** 设置是否隐藏上课地点 */
    suspend fun setHideLocation(hide: Boolean) = updateStyle {
        it.copy(hide_location = hide)
    }

    /** 设置是否隐藏授课老师 */
    suspend fun setHideTeacher(hide: Boolean) = updateStyle {
        it.copy(hide_teacher = hide)
    }

    /** 设置是否移除地点前的 @ 符号 */
    suspend fun setRemoveLocationAt(remove: Boolean) = updateStyle {
        it.copy(remove_location_at = remove)
    }

    /** 设置文字水平居中 */
    suspend fun setTextAlignCenterHorizontal(center: Boolean) = updateStyle {
        it.copy(text_align_center_horizontal = center)
    }

    /** 设置文字垂直居中 */
    suspend fun setTextAlignCenterVertical(center: Boolean) = updateStyle {
        it.copy(text_align_center_vertical = center)
    }

    /** 设置边框类型 */
    suspend fun setBorderType(type: BorderTypeProto) = updateStyle {
        it.copy(border_type = type)
    }

    /** 设置课表展示模式 */
    suspend fun setScheduleMode(mode: ScheduleModeProto) = updateStyle {
        it.copy(schedule_mode = mode)
    }

    /** 设置页面文本颜色 */
    suspend fun setPageTextColor(color: Color?) = updateStyle {
        it.copy(page_text_color_long = color?.toArgb()?.toLong())
    }

    /** 设置课程块文字颜色 */
    suspend fun setCourseTextColor(color: Color?) = updateStyle {
        it.copy(course_text_color_long = color?.toArgb()?.toLong())
    }

    /** 设置背景壁纸路径 */
    suspend fun setBackgroundImagePath(path: String) = updateStyle {
        it.copy(background_image_path = path)
    }

    /** 重置样式设置但保留壁纸 */
    suspend fun resetAllStyleSettingsExceptWallpaper() {
        dataStore.updateData { currentProto ->
            val currentPath = currentProto.background_image_path
            ScheduleGridStyleProto().copy(background_image_path = currentPath)
        }
    }
}