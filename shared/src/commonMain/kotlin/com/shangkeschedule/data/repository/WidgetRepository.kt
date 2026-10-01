package com.shangkeschedule.data.repository

import androidx.room3.withWriteTransaction
import com.shangkeschedule.data.db.widget.WidgetAppSettings
import com.shangkeschedule.data.db.widget.WidgetAppSettingsDao
import com.shangkeschedule.data.db.widget.WidgetCourse
import com.shangkeschedule.data.db.widget.WidgetCourseDao
import com.shangkeschedule.data.db.widget.WidgetDatabase
import com.shangkeschedule.data.time.startOfWeek
import com.shangkeschedule.tool.AppLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Single
import kotlin.time.Clock

private const val TAG = "WidgetRepository"

/**
 * Widget 数据仓库，负责处理与 Widget 数据库相关的所有数据操作。
 */
@Single
class WidgetRepository(
    private val widgetDatabase: WidgetDatabase,
    private val widgetCourseDao: WidgetCourseDao,
    private val widgetAppSettingsDao: WidgetAppSettingsDao
) {

    /**
     * 获取指定日期范围内的 Widget 课程。
     */
    fun getWidgetCoursesByDateRange(startDate: String, endDate: String): Flow<List<WidgetCourse>> {
        return widgetCourseDao.getWidgetCoursesByDateRange(startDate, endDate)
    }

    /**
     * 批量插入或更新 Widget 课程。
     */
    suspend fun insertAll(courses: List<WidgetCourse>) {
        widgetCourseDao.insertAll(courses)
    }

    /**
     * 删除所有 Widget 课程。
     */
    suspend fun deleteAll() {
        widgetCourseDao.deleteAll()
    }

    /**
     * 原子替换完整 Widget 快照：课程与学期设置在同一个数据库事务内提交。
     */
    suspend fun replaceSnapshot(courses: List<WidgetCourse>, settings: WidgetAppSettings) {
        // 版本戳在此处递增：这里是「快照真正落库」的唯一出口。读库方（渲染层）
        // 不消费该字段，只用于让下一次内容比对能识别「库曾被清空」。
        val nextVersion = runCatching {
            widgetAppSettingsDao.getAppSettings().first()?.snapshotVersion?.plus(1) ?: 1L
        }.getOrDefault(settings.snapshotVersion + 1)
        val stamped = settings.copy(snapshotVersion = nextVersion)
        widgetDatabase.withWriteTransaction {
            widgetCourseDao.deleteAll()
            if (courses.isNotEmpty()) {
                widgetCourseDao.insertAll(courses)
            }
            widgetAppSettingsDao.insertOrUpdate(stamped)
        }
    }

    /**
     * 原子替换完整 Widget 快照，但先与现有快照比对：内容一致时跳过写库与更新通知（v3.54.0）。
     * 返回是否实际写入。用于截断「设置流发射 → 全量重写 → 全组件重绘」的放大链。
     */
    suspend fun replaceSnapshotIfChanged(
        courses: List<WidgetCourse>,
        settings: WidgetAppSettings
    ): Boolean {
        val currentCourses = widgetCourseDao.getAllWidgetCourses().first().associateBy { it.id }
        val currentSettings = widgetAppSettingsDao.getAppSettings().first()
        // v4.64.23：原先只要「两边相等」就跳过写库。但 widget 库可能被清空（destructive
        // migration / 系统回收 / 手动清理），而同步器在「无有效配置」路径写下的正是
        // 「空课程 + 默认 settings」—— 两者完全相等 ⇒ 判定「无需更新」⇒ 组件永久空白，
        // 且此后每次同步都命中同一分支，永远不会自愈。
        //
        // 引入版本戳：每次真正写入都递增 [WidgetAppSettings.snapshotVersion]，
        // 于是「内容相同但版本戳不同」必被识别为「需要更新」，数据库一被清空
        // （version 回到 0 或空行）就会立刻被下一轮同步重建。
        if (currentSettings == null) {
            replaceSnapshot(courses, settings)
            return true
        }
        if (currentSettings == settings && currentCourses == courses.associateBy { it.id } &&
            currentSettings.snapshotVersion == settings.snapshotVersion
        ) {
            return false
        }
        replaceSnapshot(courses, settings)
        return true
    }

    /**
     * 插入或更新小组件设置。
     */
    suspend fun insertOrUpdateAppSettings(settings: WidgetAppSettings) {
        widgetAppSettingsDao.insertOrUpdate(settings)
    }

    /**
     * 获取当前学期周数的数据流。
     */
    fun getCurrentWeekFlow(): Flow<Int?> {
        return widgetAppSettingsDao.getAppSettings()
            .map { settings ->
                val totalWeeks = settings?.semesterTotalWeeks ?: 0
                val startDate = settings?.semesterStartDate
                val firstDayOfWeek = firstDayOfWeekOrMonday(settings?.firstDayOfWeek)

                calculateCurrentWeek(startDate, totalWeeks, firstDayOfWeek)
            }
    }

    /**
     * 根据学期开始日期和总周数计算当前周数。
     *
     * @param semesterStartDateStr 学期开始日期字符串，格式为 yyyy-MM-dd
     * @param totalWeeks 学期总周数
     * @param firstDayOfWeekInt 一周起始日 (1=MONDAY, 7=SUNDAY)
     * @return 当前周数 (从1开始)，若未开始或已结束则返回 null
     */
    private fun calculateCurrentWeek(semesterStartDateStr: String?, totalWeeks: Int, firstDayOfWeekInt: Int): Int? {
        if (semesterStartDateStr.isNullOrEmpty() || totalWeeks <= 0) return null

        return try {
            val startDate = LocalDate.parse(semesterStartDateStr)
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

            val alignedStartDate = startOfWeek(startDate, firstDayOfWeekInt)
            val alignedToday = startOfWeek(today, firstDayOfWeekInt)

            if (alignedToday < alignedStartDate) return null

            val diffDays = alignedStartDate.daysUntil(alignedToday)
            val diffWeeks = diffDays / 7
            val calculatedWeek = diffWeeks + 1

            if (calculatedWeek in 1..totalWeeks) calculatedWeek else null
        } catch (e: Exception) {
            AppLog.e(TAG, "解析学期起始日期失败", e)
            null
        }
    }

    private fun firstDayOfWeekOrMonday(value: Int?): Int =
        value?.takeIf { it in 1..7 } ?: DayOfWeek.MONDAY.isoDayNumber
}
