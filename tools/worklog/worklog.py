# -*- coding: utf-8 -*-
"""
工作日志（工作日志.md）统一读写工具。

背景：本文件历史上被反复用「另起一个 ## 最新改动 标题」而不是「插到已有的
最新改动区顶部」的方式来追加记录，导致：
  1) 出现多段彼此重叠的「最新改动」区；
  2) 畸形合并的第 233 行（标题 / 约定 / 归档说明 / 记录规范 全挤在一行）；
  3) 不同时期用不同记录格式（纯段落式 `2026-09-17 | 3.63.0(240) | FEAT | …`
     与标题式 `### 2026-09-11 · v3.47.0(185) · FEAT - …`）；
  4) UTF-8 乱码（`代?` 之类）。

本工具目标：
  - 写入：append 子命令始终把新记录插到唯一的「最新改动」区顶部，UTF-8 安全；
  - 读取：command/query/stats 基于 SQLite 索引快速检索版本 / 日期 / 类型 / 关键词；
  - 整理：organize 一次性把多段重复块合并成单一规范结构，旧记录归档到独立文件，
    全程非破坏式（先备份 + 输出修复报告）。

用法:
  python worklog.py check [--no-index]
  python worklog.py append --date 2026-09-17 --version 3.63.0 --code 240 --type REFACTOR --summary "摘要" [--body "正文" | --body-file f.txt]
  python worklog.py query [--version 3.63.0] [--type FEAT] [--date 2026-09-16] [--keyword 乱码] [--tail 10] [--json]
  python worklog.py stats [--by type] [--by date]
  python worklog.py organize [--cutoff 3.40.0] [--archive 工作日志_archive.md] [--dry-run]
"""
import io
import json
import os
import re
import shutil
import sqlite3
import sys
import tempfile
from datetime import datetime

if sys.stdout.encoding and sys.stdout.encoding.lower() not in ('utf-8', 'utf8'):
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
LOG_PATH = os.path.join(ROOT, '工作日志.md')
DB_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), '.worklog_index.db')

TYPES = ('FEAT', 'FIX', 'REFACTOR', 'DOCS', 'DATA', 'BUILD')

# 两种历史记录开头格式 => 统一提取 (date, version, code, type, summary_rest)
# 段落式： 2026-09-17 | 3.63.0(240) | FEAT | **【…】…
# 标题式： ### 2026-09-11 · v3.47.0(185) · FEAT - 【…】…
RE_PIPE = re.compile(
    r'^\s*(\d{4}-\d{2}-\d{2})\s*\|\s*(\d+\.\d+\.\d+)(?:\((\d+)\))?\s*\|\s*([A-Z]+)\s*\|\s*(.+)$'
)
RE_HEADING = re.compile(
    r'^###\s*(\d{4}-\d{2}-\d{2})\s*·\s*v(\d+\.\d+\.\d+)(?:\((\d+)\))?\s*·\s*([A-Z]+)\s*[-–]\s*(.+)$'
)
RE_SECTION = re.compile(r'^(#{1,2})\s')
RE_TOP_HEADER = re.compile(r'^#\s+')


def _ver_key(v):
    try:
        return [int(x) for x in v.split('.')]
    except Exception:
        return [0, 0, 0]


def parse_log(text):
    """把文本解析成 (cells, records)。cells = 骨架结构（记录+分区标记，保序）。"""
    lines = text.split('\n')

    class Cell(object):
        __slots__ = ('kind', 'rec')

        def __init__(self, kind, rec=None):
            self.kind = kind  # 'rec' | 'top' | 'section'
            self.rec = rec

    records = []
    cells = []
    cur = None

    def flush():
        nonlocal cur
        if cur is not None:
            records.append(cur)
            cells.append(Cell('rec', cur))
            cur = None

    for ln in lines:
        m = RE_PIPE.match(ln)
        if m:
            flush()
            d, v, c, t, rest = m.group(1), m.group(2), m.group(3), m.group(4), m.group(5)
            cur = {'date': d, 'version': v, 'code': c, 'type': t, 'summary': rest.strip(),
                   'body': [], 'raw_head': ln, 'fmt': 'pipe'}
            continue
        m = RE_HEADING.match(ln)
        if m:
            flush()
            d, v, c, t, rest = m.group(1), m.group(2), m.group(3), m.group(4), m.group(5)
            cur = {'date': d, 'version': v, 'code': c, 'type': t, 'summary': rest.strip(),
                   'body': [], 'raw_head': ln, 'fmt': 'heading'}
            continue
        if RE_TOP_HEADER.match(ln):  # 顶层 # 标题
            flush()
            cells.append(Cell('top'))
            continue
        if RE_SECTION.match(ln):  # ## 或更深
            flush()
            cells.append(Cell('section'))
            continue
        if cur is None:
            if ln.strip() == '':
                continue
            cells.append(Cell('top'))
            cur = None
            continue
        # 属于当前记录
        cur['body'].append(ln)

    flush()
    return cells, records


def format_record(rec):
    """规范输出单条记录。"""
    head = (f"{rec['date']} | {rec['version']}" +
            (f"({rec['code']})" if rec['code'] else '') +
            f" | {rec['type']} | {rec['summary']}")
    body = [ln.rstrip() for ln in rec['body']]
    while body and body[0].strip() == '':
        body.pop(0)
    while body and body[-1].strip() == '':
        body.pop()
    return head + ('\n' + '\n'.join(body) if body else '')


def load_db(rebuild=True):
    os.makedirs(os.path.dirname(DB_PATH), exist_ok=True)
    con = sqlite3.connect(DB_PATH)
    con.execute('PRAGMA journal_mode=WAL')
    con.execute('''
        CREATE TABLE IF NOT EXISTS records (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            date TEXT, version TEXT, code INTEGER, type TEXT,
            summary TEXT, body TEXT, raw_head TEXT
        )
    ''')
    if rebuild:
        cells, records = parse_log(open(LOG_PATH, encoding='utf-8').read())
        con.execute('DELETE FROM records')
        con.executemany(
            'INSERT INTO records(date,version,code,type,summary,body,raw_head) VALUES (?,?,?,?,?,?,?)',
            [(r['date'], r['version'], r['code'], r['type'], r['summary'],
              '\n'.join(r['body']), r.get('raw_head', '')) for r in records]
        )
        con.commit()
    return con


def _dump(cursor):
    rows = cursor.fetchall()
    return [dict(date=r[0], version=r[1], code=r[2], type=r[3], summary=r[4], body=r[5])
            for r in rows]


def cmd_check(args):
    text = open(LOG_PATH, encoding='utf-8').read()
    lines = text.split('\n')
    cells, records = parse_log(text)

    # 直接按行统计真实标题，避免把记录区外的说明文字误算成标题
    tops = sum(1 for ln in lines if re.match(r'^#\s+[^#]', ln))
    sections = sum(1 for ln in lines if re.match(r'^##\s+[^#]', ln))

    dup = {}
    for r in records:
        key = (r['version'], r['type'])
        dup.setdefault(key, []).append(r)

    issues = []
    if tops != 1:
        issues.append(f'顶层标题数量={tops}（应为 1）')
    if sections != 2:
        issues.append(f'二级分区数量={sections}（应为 2：记录规范 + 最新改动）')
    for k, rs in dup.items():
        if len(rs) > 1:
            issues.append(f'重复记录：v{k[0]} {k[1]} × {len(rs)}')
    if '代?' in text:
        issues.append('检测到乱码字符')

    print(f'记录总数: {len(records)}')
    print(f'顶层标题: {tops}  二级分区: {sections}')
    print('重复(版本+类型):')
    if dup:
        for k, rs in dup.items():
            if len(rs) > 1:
                print(f'  v{k[0]} {k[1]} × {len(rs)}')
    if issues:
        print('问题:')
        for it in issues:
            print('  - ' + it)
        return 1
    print('结构健康 ✔')
    return 0


def cmd_append(args):
    date = args.get('--date') or datetime.now().strftime('%Y-%m-%d')
    version = args.get('--version')
    code = args.get('--code')
    typ = (args.get('--type') or 'REFACTOR').strip().upper()
    summary = args.get('--summary')
    body = args.get('--body')
    body_file = args.get('--body-file')
    if not version:
        sys.exit('缺少 --version')
    if not summary:
        sys.exit('缺少 --summary')
    if typ not in TYPES:
        sys.exit(f'类型必须是 {"".join(TYPES)} 之一')
    if body_file:
        body = open(body_file, encoding='utf-8').read()
    body = (body or '').strip()

    head = f"{date} | {version}" + (f"({code})" if code else '') + f" | {typ} | {summary}"
    record = head + (('\n\n' + body) if body else '')

    text = open(LOG_PATH, encoding='utf-8').read()
    marker = '## 最新改动'
    idx = text.find(marker)
    if idx < 0:
        sys.exit('找不到 "## 最新改动" 锚点')
    # 最新改动区 = marker 行起，到下一个 section 或文件尾
    after = text[idx + len(marker):]
    seg_end = text.index('\n', idx + len(marker))
    insert_pos = seg_end + 1  # marker 行尾换行后
    # 跳过 marker 后紧邻的空行，在其后插入新记录
    j = insert_pos
    while j < len(text) and text[j] == '\n':
        j += 1
    new_block = '\n\n' + record + '\n'
    new_text = text[:idx] + marker + '\n\n' + record + '\n' + text[j:]

    # 原子写
    fd, tmp = tempfile.mkstemp(prefix='worklog_', suffix='.md')
    try:
        with os.fdopen(fd, 'w', encoding='utf-8', newline='') as f:
            f.write(new_text)
        shutil.move(tmp, LOG_PATH)
    except Exception:
        if os.path.exists(tmp):
            os.remove(tmp)
        raise
    print(f'✔ 已追加记录：{head}')
    return 0


def cmd_query(args):
    con = load_db()
    q = 'SELECT date,version,code,type,summary,body FROM records WHERE 1=1'
    params = []
    if args.get('--version'):
        q += ' AND version=?'
        params.append(args['--version'])
    if args.get('--type'):
        q += ' AND upper(type)=?'
        params.append(args['--type'].upper())
    if args.get('--date'):
        q += ' AND date=?'
        params.append(args['--date'])
    if args.get('--keyword'):
        q += ' AND (summary LIKE ? OR body LIKE ?)'
        params.append('%' + args['--keyword'] + '%')
        params.append('%' + args['--keyword'] + '%')
    q += ' ORDER BY date DESC, version DESC'
    cur = con.execute(q, params)
    rows = _dump(cur)
    if args.get('--tail'):
        rows = rows[: int(args['--tail'])]
    if args.get('--json'):
        print(json.dumps(rows, ensure_ascii=False, indent=2))
        return 0
    if not rows:
        print('（无匹配）')
        return 0
    for r in rows:
        head = f"{r['date']} | {r['version']}" + (f"({r['code']})" if r['code'] else '') + \
            f" | {r['type']} | {r['summary']}"
        print(head)
        # 预览正文前两行
        preview = [x.rstrip() for x in (r['body'] or '').split('\n') if x.strip()][:2]
        for p in preview:
            print('    ' + p[:100])
        print()
    return 0


def cmd_stats(args):
    con = load_db()
    by = args.get('--by') or 'type'
    col = 'type' if by == 'type' else 'date'
    cur = con.execute(f'SELECT {col} AS k, COUNT(*) FROM records GROUP BY k ORDER BY k DESC')
    total = con.execute('SELECT COUNT(*) FROM records').fetchone()[0]
    print(f'记录总数: {total}')
    for k, n in cur.fetchall():
        print(f'  {k}: {n}')
    return 0


def _read_records():
    cells, records = parse_log(open(LOG_PATH, encoding='utf-8').read())
    return records


def cmd_organize(args):
    dry = bool(args.get('--dry-run'))
    cutoff = args.get('--cutoff') or '3.40.0'
    archive = args.get('--archive') or os.path.join(ROOT, '工作日志_archive.md')

    text = open(LOG_PATH, encoding='utf-8').read()
    cells, records = parse_log(text)

    if not dry:
        shutil.copy(LOG_PATH, LOG_PATH + '.bak')
        print(f'✔ 已备份 -> {LOG_PATH}.bak')

    # 1) 按 (版本, 类型) 去重，保留正文更长者
    by_key = {}
    for r in records:
        key = (r['version'], r['type'])
        if key not in by_key or len('\n'.join(r['body'])) > len('\n'.join(by_key[key]['body'])):
            by_key[key] = r
    dedup = list(by_key.values())

    # 2) 排序：日期降序，版本降序
    dedup.sort(key=lambda r: (r['date'], _ver_key(r['version'])), reverse=True)

    cutoff_key = _ver_key(cutoff)
    keep = [r for r in dedup if _ver_key(r['version']) >= cutoff_key]
    move = [r for r in dedup if _ver_key(r['version']) < cutoff_key]

    hdr = (
        '# 项目工作日志 · ShangKeSchedule\n\n'
        '本文件为项目唯一持续维护的工作日志。\n\n'
        '> **约定**：每次代码 / 数据 / 构建改动完成后，必须在「最新改动」区顶部倒序追加一条记录，'
        '任何改动不得遗漏。\n\n'
        '> 📦 **归档说明**：较早的历史记录已移入 `工作日志_archive.md`（可用本工具 query/检索）。\n\n'
        '## 记录规范\n\n'
        '- 每条记录格式：`日期 | 版本(code) | 类型 | 摘要`，类型取值 `FEAT` 功能 · `FIX` 修复 · '
        '`REFACTOR` 重构 · `DOCS` 文档 · `DATA` 数据 · `BUILD` 构建/发布。\n'
        '- 追加一律使用 `python tools/worklog/worklog.py append ...`，确保插到最新改动区顶部。\n'
        '- 记录需写明：改动原因、涉及关键文件、验证状态（编译 / 装机 / 未验证）。\n'
    )
    body = hdr + '\n## 最新改动\n'
    for r in keep:
        body += '\n' + format_record(r) + '\n'
    body += '\n'

    if dry:
        print(f'[dry-run] 保留 {len(keep)} 条 (>= v{cutoff})，归档 {len(move)} 条，'
              f'去重跳过 {len(records) - len(dedup)} 条重复')
        print('--- 新主文件预览（前 40 行）---')
        print('\n'.join(body.split('\n')[:40]))
        return 0

    with open(LOG_PATH, 'w', encoding='utf-8', newline='') as f:
        f.write(body)
    with open(archive, 'w', encoding='utf-8', newline='') as f:
        f.write('# 工作日志归档（v< ' + cutoff + '）\n')
        for r in move:
            f.write('\n' + format_record(r) + '\n')

    # 报告
    report = os.path.join(ROOT, 'build_qa', 'worklog_organize_report.txt')
    os.makedirs(os.path.dirname(report), exist_ok=True)
    with open(report, 'w', encoding='utf-8') as f:
        f.write(f'解析记录总数: {len(records)}\n')
        f.write(f'去重合并后: {len(dedup)}（跳过重复 {len(records) - len(dedup)} 条）\n')
        f.write(f'保留到主文件: {len(keep)} 条 (>= v{cutoff})\n')
        f.write(f'归档到 {archive}: {len(move)} 条\n')
        f.write('\n保留记录版本:\n')
        for r in keep:
            f.write(f"  {r['date']} | {r['version']} | {r['type']} | {r['summary'][:40]}\n")
        f.write('\n归档记录版本:\n')
        for r in move:
            f.write(f"  {r['date']} | {r['version']} | {r['type']} | {r['summary'][:40]}\n")
    print(f'✔ 主文件已重写（{len(keep)} 条，>= v{cutoff}）')
    print(f'✔ 归档文件: {archive}（{len(move)} 条）')
    print(f'✔ 修复报告: {report}')
    print(f'✔ 备份: {LOG_PATH}.bak')
    return 0


def main():
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        return 0
    cmd, rest = args[0], args[1:]
    d = {}
    for a in rest:
        if a.startswith('--'):
            k = a
            d.setdefault(k, True)
        elif k:
            d[k] = a
            k = None
        else:
            d.setdefault('_pos', []).append(a)

    handlers = {
        'check': cmd_check,
        'append': cmd_append,
        'query': cmd_query,
        'stats': cmd_stats,
        'organize': cmd_organize,
    }
    fn = handlers.get(cmd)
    if fn is None:
        sys.exit(f'未知子命令: {cmd}\n\n' + __doc__)
    rc = fn(d)
    sys.exit(rc or 0)


if __name__ == '__main__':
    main()