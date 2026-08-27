import subprocess
import sqlite3
from datetime import date

adb_cmd = ['adb', '-s', 'JFKJRC89T87XXOJJ', 'exec-out', 'run-as', 'com.shangkeschedule', 'cat', 'databases/main_app_database']
result = subprocess.run(adb_cmd, capture_output=True)
with open(r'D:\01课程表\shangkeschedule\temp_db.db', 'wb') as f:
    f.write(result.stdout)

conn = sqlite3.connect(r'D:\01课程表\shangkeschedule\temp_db.db')
cursor = conn.cursor()

table_id = '724d04b3-a2f5-4946-be85-5fa93a7e42d4'
today = date(2026, 8, 27)
start = date(2026, 8, 12)

# 计算当前周（对齐到周一）
def get_previous_or_same_dow(d, target_dow):
    current = d.isoweekday()
    days = current - target_dow if current >= target_dow else 7 - (target_dow - current)
    return date.fromordinal(d.toordinal() - days)

aligned_start = get_previous_or_same_dow(start, 1)  # 周一
aligned_today = get_previous_or_same_dow(today, 1)
week = (aligned_today.toordinal() - aligned_start.toordinal()) // 7 + 1
print(f"开学: {start}, 对齐周一: {aligned_start}")
print(f"今天: {today}, 对齐周一: {aligned_today}")
print(f"当前周: 第 {week} 周")
print(f"今天周四, day=4")

# 模拟子查询（当前代码）
print(f"\n=== 模拟子查询（当前代码）===")
cursor.execute("""
    SELECT c.id, c.name, c.day, c.startSection, c.endSection
    FROM courses c
    WHERE c.courseTableId = ? AND c.isCrush = 0 AND c.day = 4
      AND c.id IN (SELECT courseId FROM course_weeks WHERE weekNumber = ?)
    ORDER BY c.startSection
""", (table_id, week))
result = cursor.fetchall()
print(f"第{week}周周四课程: {len(result)} 门")
for r in result:
    print(f"  {r[1]} (day={r[2]}, section={r[3]}-{r[4]})")

# 模拟 JOIN 查询（旧代码）
print(f"\n=== 模拟 JOIN 查询（旧代码）===")
cursor.execute("""
    SELECT c.id, c.name, c.day, c.startSection
    FROM courses c
    INNER JOIN course_weeks cw ON c.id = cw.courseId
    WHERE c.courseTableId = ? AND c.isCrush = 0 AND c.day = 4 AND cw.weekNumber = ?
    ORDER BY c.startSection
""", (table_id, week))
result2 = cursor.fetchall()
print(f"第{week}周周四课程: {len(result2)} 门")
for r in result2:
    print(f"  {r[1]} (day={r[2]}, section={r[3]})")

# 查看所有周四课程及其周次
print(f"\n=== 所有周四课程及周次 ===")
cursor.execute("""
    SELECT c.id, c.name, c.startSection
    FROM courses c WHERE c.courseTableId=? AND c.day=4 AND c.isCrush=0
    ORDER BY c.startSection
""", (table_id,))
all_courses = cursor.fetchall()
for c in all_courses:
    cursor.execute("SELECT weekNumber FROM course_weeks WHERE courseId=? ORDER BY weekNumber", (c[0],))
    weeks = [r[0] for r in cursor.fetchall()]
    in_current = week in weeks
    print(f"  {c[1]} (节次{c[2]}): 周次{weeks} {'✓包含第'+str(week)+'周' if in_current else '✗'}")

# 检查 crush 课程
print(f"\n=== Crush 课程 ===")
cursor.execute("SELECT COUNT(*) FROM courses WHERE courseTableId=? AND isCrush=1", (table_id,))
crush_count = cursor.fetchone()[0]
print(f"Crush课程总数: {crush_count}")
if crush_count > 0:
    cursor.execute("SELECT name, day FROM courses WHERE courseTableId=? AND isCrush=1 AND day=4", (table_id,))
    crush_thu = cursor.fetchall()
    print(f"周四Crush课程: {len(crush_thu)} 门")
    for c in crush_thu:
        print(f"  {c[0]}")

conn.close()
