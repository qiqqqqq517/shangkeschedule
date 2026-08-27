import sqlite3
import os
from datetime import date, timedelta

db_path = r'D:\01课程表\shangkeschedule\main_app_database.db'
conn = sqlite3.connect(db_path)
cursor = conn.cursor()

# 1. 查看应用设置
cursor.execute("SELECT * FROM app_settings LIMIT 1;")
settings_cols = [desc[0] for desc in cursor.description]
settings = cursor.fetchone()
print("=== 应用设置 ===")
for col, val in zip(settings_cols, settings):
    if col in ['currentCourseTableId', 'coupleScheduleEnabled', 'skippedDates']:
        print(f"  {col}: {val}")

current_table_id = settings[settings_cols.index('currentCourseTableId')]
print(f"\n当前课表ID: {current_table_id}")

# 2. 查看课表配置
cursor.execute("SELECT * FROM course_table_configs WHERE courseTableId = ?;", (current_table_id,))
config_cols = [desc[0] for desc in cursor.description]
config = cursor.fetchone()
print("\n=== 课表配置 ===")
if config:
    for col, val in zip(config_cols, config):
        if col in ['semesterStartDate', 'semesterTotalWeeks', 'firstDayOfWeek']:
            print(f"  {col}: {val}")
else:
    print("  无配置")

# 3. 计算当前教学周
if config:
    start_date_str = config[config_cols.index('semesterStartDate')]
    total_weeks = config[config_cols.index('semesterTotalWeeks')]
    if start_date_str:
        start_date = date.fromisoformat(start_date_str)
        today = date(2026, 8, 27)  # 周四
        days_diff = (today - start_date).days
        current_week = days_diff // 7 + 1
        print(f"\n开学日期: {start_date}")
        print(f"今天: {today} (周四, day=4)")
        print(f"当前教学周: 第 {current_week} 周 (共 {total_weeks} 周)")
        print(f"距开学: {days_diff} 天")

# 4. 查看周四的所有课程
print("\n=== 周四(day=4)的所有课程 ===")
cursor.execute("""
    SELECT id, name, teacher, position, day, startSection, endSection, 
           isCustomTime, customStartTime, customEndTime, isCrush
    FROM courses 
    WHERE courseTableId = ? AND day = 4 AND isCrush = 0
    ORDER BY startSection;
""", (current_table_id,))
courses = cursor.fetchall()
print(f"周四课程总数: {len(courses)}")
for c in courses:
    print(f"  id={c[0][:8]}... name={c[1]} teacher={c[2]} pos={c[3]} "
          f"section={c[4]}-{c[5]} customTime={c[6]}({c[7]}-{c[8]})")

# 5. 查看这些课程的周次记录
if courses:
    course_ids = [c[0] for c in courses]
    placeholders = ','.join(['?'] * len(course_ids))
    cursor.execute(f"""
        SELECT courseId, weekNumber 
        FROM course_weeks 
        WHERE courseId IN ({placeholders})
        ORDER BY courseId, weekNumber;
    """, course_ids)
    weeks = cursor.fetchall()
    print(f"\n=== 这些课程的周次记录 ===")
    print(f"总周次记录: {len(weeks)}")
    from collections import defaultdict
    week_map = defaultdict(list)
    for cid, wn in weeks:
        week_map[cid].append(wn)
    for cid, wns in week_map.items():
        cursor.execute("SELECT name FROM courses WHERE id = ?;", (cid,))
        name = cursor.fetchone()[0]
        print(f"  {name}: 周次 {sorted(wns)}")

    # 6. 模拟查询：当前周的周四课程
    if config and start_date_str:
        print(f"\n=== 模拟查询：第 {current_week} 周周四的课程 ===")
        cursor.execute(f"""
            SELECT c.id, c.name, c.day, c.startSection
            FROM courses c
            WHERE c.courseTableId = ? 
              AND c.isCrush = 0 
              AND c.day = 4
              AND c.id IN (SELECT courseId FROM course_weeks WHERE weekNumber = ?)
            ORDER BY c.startSection;
        """, (current_table_id, current_week))
        result = cursor.fetchall()
        print(f"查询结果: {len(result)} 门课程")
        for r in result:
            print(f"  {r[1]} (day={r[2]}, section={r[3]})")

conn.close()
