package com.shangkeschedule.data.db.main

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * 数据库版本 1 迁移到 版本 2 的迁移代码。
 * 核心任务：
 * 1. 创建 course_table_config 表。
 * 2. 将老版本 app_settings 中的全局设置数据迁移到新表中。
 * 3. 删除 app_settings 表中已迁移的字段。
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // --- 步骤 1: 创建新的 course_table_config 表 ---
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `course_table_config` (
                `courseTableId` TEXT NOT NULL,
                `showWeekends` INTEGER NOT NULL DEFAULT 0,
                `semesterStartDate` TEXT,
                `semesterTotalWeeks` INTEGER NOT NULL DEFAULT 20,
                `defaultClassDuration` INTEGER NOT NULL DEFAULT 45,
                `defaultBreakDuration` INTEGER NOT NULL DEFAULT 10,
                `firstDayOfWeek` INTEGER NOT NULL DEFAULT 1, 
                PRIMARY KEY(`courseTableId`),
                FOREIGN KEY(`courseTableId`) REFERENCES `course_tables`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """
        )
        // 添加索引
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_course_table_config_courseTableId` ON `course_table_config` (`courseTableId`)")

        // --- 步骤 2 & 3: 读取老数据并插入新表 ---

        var currentCourseTableId: String? = null
        var showWeekends = 0
        var semesterStartDate: String? = null
        var semesterTotalWeeks = 20
        var defaultClassDuration = 45
        var defaultBreakDuration = 10

        // 1. 读取旧数据
        connection.prepare(
            "SELECT currentCourseTableId, showWeekends, semesterStartDate, semesterTotalWeeks, defaultClassDuration, defaultBreakDuration FROM app_settings WHERE id = 1 LIMIT 1"
        ).use { stmt ->
            if (stmt.step()) {
                currentCourseTableId = if (!stmt.isNull(0)) stmt.getText(0) else null
                showWeekends = stmt.getLong(1).toInt()
                semesterStartDate = if (!stmt.isNull(2)) stmt.getText(2) else null
                semesterTotalWeeks = stmt.getLong(3).toInt()
                defaultClassDuration = stmt.getLong(4).toInt()
                defaultBreakDuration = stmt.getLong(5).toInt()
            }
        }

        // 2. 将数据插入新表
        if (currentCourseTableId != null) {
            connection.prepare(
                """
                INSERT INTO `course_table_config` (courseTableId, showWeekends, semesterStartDate, semesterTotalWeeks, defaultClassDuration, defaultBreakDuration, firstDayOfWeek)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """
            ).use { stmt ->
                stmt.bindText(1, currentCourseTableId)
                stmt.bindLong(2, showWeekends.toLong())
                if (semesterStartDate != null) {
                    stmt.bindText(3, semesterStartDate)
                } else {
                    stmt.bindNull(3)
                }
                stmt.bindLong(4, semesterTotalWeeks.toLong())
                stmt.bindLong(5, defaultClassDuration.toLong())
                stmt.bindLong(6, defaultBreakDuration.toLong())
                stmt.bindLong(7, 1L)
                stmt.step()
            }
        }

        // --- 步骤 4: 更新 app_settings 表结构 (删除字段) ---
        // 1. 重命名旧表
        connection.execSQL("ALTER TABLE app_settings RENAME TO app_settings_old")

        // 2. 创建新的 app_settings 表
        connection.execSQL(
            """
            CREATE TABLE `app_settings` (
                `id` INTEGER NOT NULL,
                `currentCourseTableId` TEXT,
                `reminderEnabled` INTEGER NOT NULL DEFAULT 0,
                `remindBeforeMinutes` INTEGER NOT NULL DEFAULT 15,
                `skippedDates` TEXT,
                `autoModeEnabled` INTEGER NOT NULL DEFAULT 0,
                `autoControlMode` TEXT NOT NULL DEFAULT 'DND',
                PRIMARY KEY(`id`)
            )
            """
        )

        // 3. 将旧表中保留的字段数据复制到新表
        connection.execSQL(
            """
            INSERT INTO app_settings (id, currentCourseTableId, reminderEnabled, remindBeforeMinutes, skippedDates, autoModeEnabled, autoControlMode)
            SELECT id, currentCourseTableId, reminderEnabled, remindBeforeMinutes, skippedDates, 0, 'DND' FROM app_settings_old
            """
        )

        // 4. 删除旧表
        connection.execSQL("DROP TABLE app_settings_old")
    }
}

/**
 * 数据库版本 2 迁移到 版本 3 的迁移代码。
 * 修改 courses 表，添加自定义时间字段。
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override suspend fun migrate(connection: SQLiteConnection) {

        // 创建具有新结构和约束的临时表 `courses_new`
        connection.execSQL(
            """
            CREATE TABLE `courses_new` (
                `id` TEXT NOT NULL,
                `courseTableId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `teacher` TEXT NOT NULL,
                `position` TEXT NOT NULL,
                `day` INTEGER NOT NULL,
                `startSection` INTEGER,  -- 变为可空 (Int?)
                `endSection` INTEGER,    -- 变为可空 (Int?)
                `isCustomTime` INTEGER NOT NULL DEFAULT 0, -- 新增字段，默认 FALSE
                `customStartTime` TEXT,  -- 新增字段
                `customEndTime` TEXT,    -- 新增字段
                `colorInt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`courseTableId`) REFERENCES `course_tables`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """
        )

        // 将原表数据复制到新表
        connection.execSQL(
            """
            INSERT INTO courses_new (id, courseTableId, name, teacher, position, day, startSection, endSection, colorInt, isCustomTime, customStartTime, customEndTime)
            SELECT id, courseTableId, name, teacher, position, day, startSection, endSection, colorInt, 0, NULL, NULL
            FROM courses
            """
        )

        // 移除原表并重命名新表
        connection.execSQL("DROP TABLE courses")
        connection.execSQL("ALTER TABLE courses_new RENAME TO courses")

        // 重新创建必要的索引和外键索引
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_courses_courseTableId` ON `courses` (`courseTableId`)")
    }
}

/**
 * 数据库版本 5 迁移到 版本 6 的迁移代码。
 * 引入“作息方案”概念：
 * 1. time_slots 表新增 schemeId 列并纳入联合主键，用于区分同一课表的多套作息（如夏令时/冬令时）。
 * 2. course_table_config 表新增 currentSchemeId 列，记录当前课表生效的作息方案。
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // --- 步骤 1: 重建 time_slots 表，新增 schemeId 列并纳入联合主键 ---
        connection.execSQL(
            """
            CREATE TABLE `time_slots_new` (
                `number` INTEGER NOT NULL,
                `startTime` TEXT NOT NULL,
                `endTime` TEXT NOT NULL,
                `courseTableId` TEXT NOT NULL,
                `alias` TEXT,
                `schemeId` TEXT NOT NULL DEFAULT 'default',
                PRIMARY KEY(`number`, `courseTableId`, `schemeId`),
                FOREIGN KEY(`courseTableId`) REFERENCES `course_tables`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """
        )

        // 复制旧数据，schemeId 统一归入默认方案 'default'
        connection.execSQL(
            """
            INSERT INTO time_slots_new (number, startTime, endTime, courseTableId, alias, schemeId)
            SELECT number, startTime, endTime, courseTableId, alias, 'default'
            FROM time_slots
            """
        )

        // 移除旧表并重命名新表
        connection.execSQL("DROP TABLE time_slots")
        connection.execSQL("ALTER TABLE time_slots_new RENAME TO time_slots")

        // 重建索引
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_time_slots_courseTableId_schemeId` ON `time_slots` (`courseTableId`, `schemeId`)")

        // --- 步骤 2: course_table_config 表新增 currentSchemeId 列 ---
        connection.execSQL("ALTER TABLE course_table_config ADD COLUMN currentSchemeId TEXT NOT NULL DEFAULT 'default'")
    }
}

/**
 * 数据库版本 6 迁移到 版本 7 的迁移代码。
 * 引入“作息方案自动切换（冬令时/夏令时）”能力：
 * 1. 新建 time_slot_schemes 表，存储每套作息方案的生效日期范围（月-日，支持跨年）。
 * 2. course_table_config 表新增 autoSwitchScheme 列，控制是否根据日期自动切换作息方案。
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // --- 步骤 1: 创建 time_slot_schemes 表 ---
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `time_slot_schemes` (
                `courseTableId` TEXT NOT NULL,
                `schemeId` TEXT NOT NULL DEFAULT 'default',
                `startMonthDay` TEXT,
                `endMonthDay` TEXT,
                PRIMARY KEY(`courseTableId`, `schemeId`),
                FOREIGN KEY(`courseTableId`) REFERENCES `course_tables`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_time_slot_schemes_courseTableId` ON `time_slot_schemes` (`courseTableId`)"
        )

        // --- 步骤 2: course_table_config 表新增 autoSwitchScheme 列 ---
        connection.execSQL("ALTER TABLE course_table_config ADD COLUMN autoSwitchScheme INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * 数据库版本 7 迁移到 版本 8 的迁移代码。
 * 为 courses 表新增课程学分、考核方式、实验课三个字段。
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE courses ADD COLUMN credit TEXT")
        connection.execSQL("ALTER TABLE courses ADD COLUMN assessmentMethod TEXT")
        connection.execSQL("ALTER TABLE courses ADD COLUMN isLab INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * 数据库版本 8 迁移到 版本 9 的迁移代码。
 * 为 courses 表新增 isCrush 字段，用于标记 crush（情侣）课表课程。
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE courses ADD COLUMN isCrush INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * 数据库版本 9 迁移到 版本 10 的迁移代码。
 * 新增 todo_items 待办事项表（供「今日课表」页内嵌展示当日待办）。
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `todo_items` (
                `id` TEXT NOT NULL,
                `date` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `note` TEXT,
                `time` TEXT,
                `done` INTEGER NOT NULL DEFAULT 0,
                `sortOrder` INTEGER NOT NULL DEFAULT 0,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_items_date` ON `todo_items` (`date`)")
    }
}

/**
 * 数据库版本 10 迁移到 版本 11 的迁移代码。
 * 新增 schedule_events 日程事件表（供「日程」页月历与日程列表展示）。
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `schedule_events` (
                `id` TEXT NOT NULL,
                `date` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `isAllDay` INTEGER NOT NULL,
                `startTime` TEXT,
                `endTime` TEXT,
                `location` TEXT,
                `note` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_events_date` ON `schedule_events` (`date`)")
    }
}

/**
 * 数据库版本 11 迁移到 版本 12 的迁移代码。
 * schedule_events 表新增 done 完成状态列（供今日页「待办」分类日程点击勾选完成）。
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE schedule_events ADD COLUMN done INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * 数据库版本 12 迁移到 版本 13 的迁移代码。
 * 情侣课表独立化：
 * 1. course_tables 新增 isCouple / pairedCourseTableId 两列。
 * 2. 把旧结构中挂在本人课表下的 isCrush=1 课程，搬到自动创建的独立情侣课表：
 *    - 为每个含 crush 课程的课表生成配对情侣表（id = 原表 id + "_couple"）；
 *    - crush 课程整体转移并清零 isCrush 标记（isCrush 列保留仅为旧备份兼容）；
 *    - 复制原表的默认作息方案 time_slots 与 course_table_config，保证升级后作息一致。
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE course_tables ADD COLUMN isCouple INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE course_tables ADD COLUMN pairedCourseTableId TEXT")

        // 收集所有含 crush 课程的本人课表 ID
        val selfTableIds = mutableListOf<String>()
        connection.prepare(
            "SELECT DISTINCT courseTableId FROM courses WHERE isCrush = 1"
        ).use { stmt ->
            while (stmt.step()) {
                if (!stmt.isNull(0)) selfTableIds.add(stmt.getText(0))
            }
        }

        selfTableIds.forEach { selfId ->
            val coupleId = selfId + "_couple"

            // id 冲突（例如用户手动建过同名表）时：不再另建情侣表，
            // crush 课程并入既有情侣表，避免数据滞留 isCrush=1 成为隐形数据。
            val coupleTableExists = connection.prepare(
                "SELECT 1 FROM course_tables WHERE id = ?"
            ).use { stmt ->
                stmt.bindText(1, coupleId)
                stmt.step()
            }

            if (!coupleTableExists) {
                val selfName = connection.prepare(
                    "SELECT name FROM course_tables WHERE id = ?"
                ).use { stmt ->
                    stmt.bindText(1, selfId)
                    if (stmt.step() && !stmt.isNull(0)) stmt.getText(0) else "我的课表"
                }

                connection.prepare(
                    "INSERT INTO course_tables (id, name, createdAt, isCouple, pairedCourseTableId) VALUES (?, ?, ?, 1, ?)"
                ).use { stmt ->
                    stmt.bindText(1, coupleId)
                    stmt.bindText(2, "${selfName} · 情侣")
                    stmt.bindLong(3, kotlin.time.Clock.System.now().toEpochMilliseconds())
                    stmt.bindText(4, selfId)
                    stmt.step()
                }
            }

            // crush 课程整体迁入情侣表并清零标记
            connection.prepare(
                "UPDATE courses SET courseTableId = ?, isCrush = 0 WHERE courseTableId = ? AND isCrush = 1"
            ).use { stmt ->
                stmt.bindText(1, coupleId)
                stmt.bindText(2, selfId)
                stmt.step()
            }

            // 复制本人表**全部方案**的作息（含夏/冬令时等非 default 方案）：
            // 只复制 default 而整体复制 config.currentSchemeId 的话，生效方案为非 default 的用户
            // 升级后情侣表 active 方案将没有任何 slots，情侣课程会整层消失。
            // OR IGNORE 兼容「情侣表已存在」的冲突路径，避免主键冲突中断迁移。
            connection.prepare(
                """
                INSERT OR IGNORE INTO time_slots (number, startTime, endTime, courseTableId, alias, schemeId)
                SELECT number, startTime, endTime, ?, alias, schemeId
                FROM time_slots WHERE courseTableId = ?
                """.trimIndent()
            ).use { stmt ->
                stmt.bindText(1, coupleId)
                stmt.bindText(2, selfId)
                stmt.step()
            }

            // 复制作息方案元信息（生效日期范围，支持夏/冬令时自动切换）
            connection.prepare(
                """
                INSERT OR IGNORE INTO time_slot_schemes (courseTableId, schemeId, startMonthDay, endMonthDay)
                SELECT ?, schemeId, startMonthDay, endMonthDay
                FROM time_slot_schemes WHERE courseTableId = ?
                """.trimIndent()
            ).use { stmt ->
                stmt.bindText(1, coupleId)
                stmt.bindText(2, selfId)
                stmt.step()
            }

            // 复制课表配置（学期起止 / 总周数 / 每周起始 / 默认时长等）
            connection.prepare(
                """
                INSERT OR IGNORE INTO course_table_config (
                    courseTableId, showWeekends, semesterStartDate, semesterTotalWeeks,
                    defaultClassDuration, defaultBreakDuration, firstDayOfWeek, currentSchemeId, autoSwitchScheme
                )
                SELECT ?, showWeekends, semesterStartDate, semesterTotalWeeks,
                       defaultClassDuration, defaultBreakDuration, firstDayOfWeek, currentSchemeId, autoSwitchScheme
                FROM course_table_config WHERE courseTableId = ?
                """.trimIndent()
            ).use { stmt ->
                stmt.bindText(1, coupleId)
                stmt.bindText(2, selfId)
                stmt.step()
            }
        }
    }
}

// 【集中管理所有迁移对象】
val ALL_MIGRATIONS = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10,
    MIGRATION_10_11,
    MIGRATION_11_12,
    MIGRATION_12_13,
)