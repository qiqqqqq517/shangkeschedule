import subprocess
import sqlite3
import io

# 直接通过 adb exec-out 读取数据库
adb_cmd = ['adb', '-s', 'JFKJRC89T87XXOJJ', 'exec-out', 'run-as', 'com.shangkeschedule', 'cat', 'databases/main_app_database']
result = subprocess.run(adb_cmd, capture_output=True)
db_data = result.stdout
print(f"读取到 {len(db_data)} 字节")

# 写入临时文件
with open(r'D:\01课程表\shangkeschedule\temp_db.db', 'wb') as f:
    f.write(db_data)

# 尝试读取
try:
    conn = sqlite3.connect(r'D:\01课程表\shangkeschedule\temp_db.db')
    cursor = conn.cursor()
    cursor.execute("SELECT name FROM sqlite_master WHERE type='table';")
    tables = cursor.fetchall()
    print(f"表: {[t[0] for t in tables]}")
    
    # 查看应用设置
    cursor.execute("SELECT * FROM app_settings LIMIT 1;")
    cols = [desc[0] for desc in cursor.description]
    row = cursor.fetchone()
    if row:
        settings = dict(zip(cols, row))
        print(f"\n当前课表ID: {settings.get('currentCourseTableId')}")
        print(f"情侣课表启用: {settings.get('coupleScheduleEnabled')}")
        print(f"跳过日期: {settings.get('skippedDates')}")
        
        table_id = settings.get('currentCourseTableId')
        
        # 课表配置
        cursor.execute("SELECT semesterStartDate, semesterTotalWeeks, firstDayOfWeek FROM course_table_configs WHERE courseTableId = ?;", (table_id,))
        config = cursor.fetchone()
        print(f"\n学期配置: {config}")
        
        if config and config[0]:
            from datetime import date
            start = date.fromisoformat(config[0])
            today = date(2026, 8, 27)
            week = (today - start).days // 7 + 1
            print(f"开学: {start}, 今天: {today}, 当前周: 第{week}周")
            
            # 周四课程
            cursor.execute("""
                SELECT id, name, teacher, position, startSection, endSection 
                FROM courses WHERE courseTableId=? AND day=4 AND isCrush=0
                ORDER BY startSection
            """, (table_id,))
            courses = cursor.fetchall()
            print(f"\n周四课程总数: {len(courses)}")
            for c in courses:
                print(f"  {c[1]} | {c[2]} | {c[3]} | 节次{c[4]}-{c[5]}")
            
            # 这些课程的周次
            if courses:
                ids = [c[0] for c in courses]
                ph = ','.join(['?']*len(ids))
                cursor.execute(f"SELECT courseId, weekNumber FROM course_weeks WHERE courseId IN ({ph}) ORDER BY courseId, weekNumber", ids)
                weeks = cursor.fetchall()
                print(f"\n周次记录: {len(weeks)} 条")
                from collections import defaultdict
                wm = defaultdict(list)
                for cid, wn in weeks:
                    wm[cid].append(wn)
                for cid, wns in wm.items():
                    cursor.execute("SELECT name FROM courses WHERE id=?", (cid,))
                    name = cursor.fetchone()[0]
                    print(f"  {name}: {sorted(wns)}")
                
                # 模拟子查询
                cursor.execute(f"""
                    SELECT c.name FROM courses c 
                    WHERE c.courseTableId=? AND c.isCrush=0 AND c.day=4
                    AND c.id IN (SELECT courseId FROM course_weeks WHERE weekNumber=?)
                    ORDER BY c.startSection
                """, (table_id, week))
                result = cursor.fetchall()
                print(f"\n第{week}周周四课程(子查询): {len(result)} 门")
                for r in result:
                    print(f"  {r[0]}")
    
    conn.close()
except Exception as e:
    print(f"错误: {e}")
    # 检查文件头
    print(f"文件头: {db_data[:16]}")
