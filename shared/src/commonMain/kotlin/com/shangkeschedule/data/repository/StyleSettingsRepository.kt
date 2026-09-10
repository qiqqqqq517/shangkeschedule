package com.shangkeschedule.data.repository

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioSerializer
import com.shangkeschedule.data.model.AppThemePreset
import com.shangkeschedule.data.model.DualColor
import com.shangkeschedule.data.model.ScheduleGridStyle
import com.shangkeschedule.data.model.schedule_style.BorderTypeProto
import com.shangkeschedule.data.model.schedule_style.ScheduleGridStyleProto
import com.shangkeschedule.data.model.schedule_style.ScheduleModeProto
import com.shangkeschedule.data.model.toCompose
import com.shangkeschedule.data.model.toProto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
 * 已知的「历史预设调色板」→ 当前主题预设。
 *
 * 用途见 [StyleSettingsRepository.upgradeLegacyPresetPalette]：把老用户 DataStore 里快照的旧配色
 * 升级为当前预设配色。
 *
 * v3.41.0：App 只剩「通透」一套主题，所以历史上的各套预设调色板（书卷暖砂 12/20 色、
 * 经典马卡龙 12 色、云舒 Material 12 色）**全部**登记为「旧 → 通透」，
 * 老用户首次启动即被平滑迁移到 iOS 系统色池，不会停留在已删除主题的配色上。
 */
private val LEGACY_PRESET_PALETTES: List<Pair<List<DualColor>, AppThemePreset>> = listOf(
    // 「书卷」旧 12 色（v3.35.4 及以前）：赤陶/珊瑚/红/玫瑰/棕 等暖色色差过小难以区分
    listOf(
        DualColor(Color(0x40C96442), Color(0xFFC96442)),
        DualColor(Color(0x409C87F5), Color(0xFF9C87F5)),
        DualColor(Color(0x40788C5D), Color(0xFF788C5D)),
        DualColor(Color(0x40E88672), Color(0xFFE88672)),
        DualColor(Color(0x40A8863C), Color(0xFFA8863C)),
        DualColor(Color(0x405A8FB0), Color(0xFF5A8FB0)),
        DualColor(Color(0x40D65450), Color(0xFFD65450)),
        DualColor(Color(0x407A8A4F), Color(0xFF7A8A4F)),
        DualColor(Color(0x405F9A5A), Color(0xFF5F9A5A)),
        DualColor(Color(0x40D86485), Color(0xFFD86485)),
        DualColor(Color(0x409A6B4E), Color(0xFF9A6B4E)),
        DualColor(Color(0x404E8F8A), Color(0xFF4E8F8A)),
    ) to AppThemePreset.IOS,
    // 「书卷」20 色（v3.35.5 ~ v3.40.x）：赤红/赭石/琥珀…342° 等距环绕
    listOf(
        DualColor(Color(0x40913030), Color(0xFFD04343)),
        DualColor(Color(0x40EE4B04), Color(0xFF9B4A27)),
        DualColor(Color(0x40FFA51D), Color(0xFFD09843)),
        DualColor(Color(0x40E5DA76), Color(0xFF9B8F27)),
        DualColor(Color(0x405E7502), Color(0xFFB4D043)),
        DualColor(Color(0x4088F31E), Color(0xFF619B27)),
        DualColor(Color(0x406BAF5B), Color(0xFF5FD043)),
        DualColor(Color(0x4026FF3B), Color(0xFF279B32)),
        DualColor(Color(0x4002B048), Color(0xFF43D07C)),
        DualColor(Color(0x4001593F), Color(0xFF279B78)),
        DualColor(Color(0x401EC0C0), Color(0xFF43D0D0)),
        DualColor(Color(0x406FD4FF), Color(0xFF27789B)),
        DualColor(Color(0x400062F1), Color(0xFF437CD0)),
        DualColor(Color(0x404A5191), Color(0xFF27329B)),
        DualColor(Color(0x40968CBC), Color(0xFF5F43D0)),
        DualColor(Color(0x408F39E6), Color(0xFF61279B)),
        DualColor(Color(0x40E478FF), Color(0xFFB443D0)),
        DualColor(Color(0x40F5CEF1), Color(0xFF9B278F)),
        DualColor(Color(0x40D50D85), Color(0xFFD04398)),
        DualColor(Color(0x40EF628D), Color(0xFF9B274A)),
    ) to AppThemePreset.IOS,
    // 「经典」马卡龙 12 色（ORIGINAL，已删除）
    listOf(
        DualColor(Color(0xFFFFCDD2), Color(0xFFD32F2F)),
        DualColor(Color(0xFFF8BBD0), Color(0xFFC2185B)),
        DualColor(Color(0xFFE1BEE7), Color(0xFF7B1FA2)),
        DualColor(Color(0xFFD1C4E9), Color(0xFF512DA8)),
        DualColor(Color(0xFFC5CAE9), Color(0xFF303F9F)),
        DualColor(Color(0xFFBBDEFB), Color(0xFF1976D2)),
        DualColor(Color(0xFFB2EBF2), Color(0xFF0097A7)),
        DualColor(Color(0xFFB2DFDB), Color(0xFF00796B)),
        DualColor(Color(0xFFC8E6C9), Color(0xFF388E3C)),
        DualColor(Color(0xFFDCEDC8), Color(0xFF689F38)),
        DualColor(Color(0xFFFFF9C4), Color(0xFFFBC02D)),
        DualColor(Color(0xFFFFE0B2), Color(0xFFF57C00)),
    ) to AppThemePreset.IOS,
    // 「云舒」Material 12 色（SLEEPY，已删除）
    listOf(
        DualColor(Color(0xFFEADDFF), Color(0xFF4F378B)),
        DualColor(Color(0xFFD1E4FF), Color(0xFF00497D)),
        DualColor(Color(0xFFB7F397), Color(0xFF295D09)),
        DualColor(Color(0xFFFFD8E4), Color(0xFF633B48)),
        DualColor(Color(0xFFFFDBC8), Color(0xFF783200)),
        DualColor(Color(0xFFD7E3F7), Color(0xFF3B4858)),
        DualColor(Color(0xFFF2DAFF), Color(0xFF523F5F)),
        DualColor(Color(0xFFBCEBEB), Color(0xFF1E4E4E)),
        DualColor(Color(0xFFE9E4AA), Color(0xFF4A481D)),
        DualColor(Color(0xFFFFF9C4), Color(0xFF7A5B00)),
        DualColor(Color(0xFFFFD8D8), Color(0xFF8C1D18)),
        DualColor(Color(0xFFE3F2E9), Color(0xFF1B5E20)),
    ) to AppThemePreset.IOS,
)

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

    /** 仓库私有作用域：替代 GlobalScope，生命周期仍为进程级单例，但可测试、可追踪。 */
    private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // 异步预热缓存
        preloadScope.launch {
            try {
                upgradeLegacyPresetPalette()
                val current = dataStore.data.map { it.toCompose() }.first()
                styleCache.value = current
            } catch (_: Exception) {
                // 预热失败，后续仍可从 dataStore 读取
            }
        }
    }

    /**
     * 一次性把「历史预设调色板」升级为当前预设调色板。
     *
     * 背景：用户选择主题时 [applyStylePreset] 会把整套样式（含 `course_color_maps`）快照进 DataStore，
     * 而读取时 `toCompose()` 优先采用持久化值。于是**之后修改预设调色板对老用户完全无效**——
     * 他们读到的始终是当初快照的旧配色（v3.35.5 重做书卷 20 色后仍显示旧色的根因即在此）。
     *
     * 策略：仅当持久化配色与某个「历史预设调色板」逐色完全一致时才替换为新配色；
     * 用户手动改过颜色（与任何历史预设都不完全一致）时原样保留，不覆盖用户的选择。
     */
    suspend fun upgradeLegacyPresetPalette() {
        val proto = dataStore.data.first()
        if (proto.course_color_maps.isEmpty()) return

        val persisted = proto.course_color_maps.map { it.toCompose() }
        val matchedPreset = LEGACY_PRESET_PALETTES
            .firstOrNull { (legacyPalette, _) -> legacyPalette == persisted }
            ?.second
            ?: return

        val upgraded = matchedPreset.gridStyle.courseColorMaps.map { it.toProto() }
        if (upgraded == proto.course_color_maps) return
        updateStyle { it.copy(course_color_maps = upgraded) }
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