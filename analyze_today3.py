import subprocess
import sqlite3
from datetime import date
from collections import defaultdict

adb_cmd = ['adb', '-s', 'JFKJRC89T87XXOJJ', 'exec-out', 'run-as', 'com.shangkeschedule', 'cat', 'databases/main_app_database']
result = subprocess.run(adb_cmd, capture_output=True)
db_data = result.stdout

with open(r'D:\01课程表\shangkeschedule\temp_db.db', 'wb') as f:
    f.write(db_data)

conn = sqlite3.connect(r'D:\01课程表\shangkeschedule\temp_db.db')
cursor = conn.cursor()

# 查看 course_tables
cursor.execute("SELECT * FROM course_tables;")
cols = [desc[0] for desc in cursor.description]
tables = cursor.fetchall()
print("=== 课表列表 ===")
for t in tables:
    print(f"  {dict(zip(cols, t))}")

# 查看 course_table_config
cursor.execute("SELECT * FROM course_table_config;")
cols = [desc[0] for desc in cursor.description]
configs = cursor.fetchall()
print(f"\n=== 课表配置 ({len(configs)} 条) ===")
for c in configs:
    print(f"  {dict(zip(cols, c))}")

# 查看所有课程的 day 分布
cursor.execute("SELECT day, COUNT(*) FROM courses WHERE isCrush=0 GROUP BY day ORDER BY day;")
day_dist = cursor.fetchall()
print(f"\n=== 课程星期分布 ===")
for d in day_dist:
    print(f"  周{d[0]}: {d[1]} 门")

# 查看周四课程
cursor.execute("""
    SELECT id, name, teacher, position, day, startSection, endSection,
           isCustomTime, customStartTime, customEndTime
    FROM courses WHERE day=4 AND isCrush=0 ORDER BY startSection;
""")
courses = cursor.fetchall()
print(f"\n=== 周四(day=4)课程: {len(courses)} 门 ===")
for c in courses:
    print(f"  {c[1]} | 教师:{c[2]} | 地点:{c[3]} | 节次:{c[5]}-{c[6]} | 自定义时间:{c[7]}({c[8]}-{c[9]})")

# 查看这些课程的周次
if courses:
    ids = [c[0] for c in courses]
    ph = ','.join(['?']*len(ids))
    cursor.execute(f"SELECT courseId, weekNumber FROM course_weeks WHERE courseId IN ({ph}) ORDER BY courseId, weekNumber", ids)
    weeks = cursor.fetchall()
    print(f"\n=== 周次记录: {len(weeks)} 条 ===")
    wm = defaultdict(list)
    for cid, wn in weeks:
        wm[cid].append(wn)
    for cid, wns in wm.items():
        cursor.execute("SELECT name FROM courses WHERE id=?", (cid,))
        name = cursor.fetchone()[0]
        print(f"  {name}: {sorted(wns)}")

# 查看 course_table_config 中的学期开始日期
if configs:
    # 找第一个配置
    config_dict = dict(zip(cols, configs[0]))
    print(f"\n=== 配置详情 ===")
    for k, v in config_dict.items():
        print(f"  {k}: {v}")

conn.close()
