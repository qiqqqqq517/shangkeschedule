// 南开大学 · 研究生信息管理系统（allogene 平台）个人课表适配
//
// 入口：
//   校内 https://yjs.nankai.edu.cn/py/page/student/grkcb.htm
//   校外 https://webvpn.nankai.edu.cn/ （WebVPN 资源站点，登录后打开「研究生信息管理系统」
//        再进入「培养 → 我的课表」）
//
// 课表页面：<应用根>/py/page/student/grkcb.htm，表格 table.table-course
//   表头：colspan=2「时间」+ 星期一…星期日（第 2..8 列 → 星期 1..7）
//   数据行：上午/下午/晚上（rowspan）+「第N节」+ 7 个星期单元格
//   课程单元格：<a><strong>课程名</strong><br>|| ( 1-17 )周<br>第7节 -- 第9节<br>教师<br>地点</a>
//
// 校外经 WebVPN 代理后，页面地址形如
//   https://webvpn.nankai.edu.cn/https/<加密段>/allogene/page/home.htm
// 此时课表接口位于同前缀下的 py/page/student/grkcb.htm，故按前缀拼接相对路径。

(function () {
    'use strict';

    var PLATFORM_NAME = '南开大学研究生课表导入';
    var TABLE_PATH = 'py/page/student/grkcb.htm';
    var LOGIN_HINT = '未检测到课表：请先登录并打开「研究生信息管理系统 → 培养 → 我的课表」，再点「执行导入」';

    // 南开大学研究生课程作息（课表页底部标注）
    var TIME_SLOTS = [
        { number: 1, startTime: '08:00', endTime: '08:45' },
        { number: 2, startTime: '08:55', endTime: '09:40' },
        { number: 3, startTime: '10:00', endTime: '10:45' },
        { number: 4, startTime: '10:55', endTime: '11:40' },
        { number: 5, startTime: '12:00', endTime: '12:45' },
        { number: 6, startTime: '12:55', endTime: '13:40' },
        { number: 7, startTime: '14:00', endTime: '14:45' },
        { number: 8, startTime: '14:55', endTime: '15:40' },
        { number: 9, startTime: '16:00', endTime: '16:45' },
        { number: 10, startTime: '16:55', endTime: '17:40' },
        { number: 11, startTime: '18:30', endTime: '19:15' },
        { number: 12, startTime: '19:25', endTime: '20:10' },
        { number: 13, startTime: '20:20', endTime: '21:05' },
        { number: 14, startTime: '21:15', endTime: '22:00' }
    ];

    function toast(msg) {
        try {
            if (window.shangkeBridge && window.shangkeBridge.showToast) window.shangkeBridge.showToast(msg);
            else if (window.Bridge && window.Bridge.showToast) window.Bridge.showToast(msg);
        } catch (e) {}
    }

    function alertBox(title, content, confirmText) {
        return window.shangkeBridgePromise.showAlert(title, content, confirmText || '确定');
    }

    function clean(s) {
        return String(s == null ? '' : s).replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim();
    }

    function cleanTeacher(name) {
        var t = clean(name);
        if (!t) return '未安排';
        return t.replace(/（[^）]*）/g, '').replace(/\([^)]*\)/g, '').trim() || '未安排';
    }

    function cleanCourseName(name) {
        return clean(name).replace(/[★○●◇◆■▲]/g, '').trim();
    }

    // 周次："( 1-17 )周" / "( 1-9,11-17 双 )周"
    function parseWeeks(text) {
        var s = clean(text);
        var odd = /单/.test(s);
        var even = /双/.test(s);
        var set = {};
        var re = /(\d+)\s*(?:[-~—－]\s*(\d+))?/g;
        var m;
        while ((m = re.exec(s)) !== null) {
            var a = parseInt(m[1], 10);
            var b = m[2] ? parseInt(m[2], 10) : a;
            if (isNaN(a) || a < 1) continue;
            if (isNaN(b) || b < a) b = a;
            if (b > 40) b = 40;
            for (var w = a; w <= b; w++) {
                if (odd && w % 2 === 0) continue;
                if (even && w % 2 === 1) continue;
                set[w] = true;
            }
        }
        return Object.keys(set).map(Number).sort(function (x, y) { return x - y; });
    }

    // 节次："第7节 -- 第9节" / "第3节"
    function parseSections(text) {
        var s = clean(text);
        var m = s.match(/第\s*(\d+)\s*节\s*(?:[-~—－]+\s*第?\s*(\d+)\s*节)?/);
        if (!m) return null;
        var start = parseInt(m[1], 10);
        var end = m[2] ? parseInt(m[2], 10) : start;
        if (isNaN(start) || start < 1) return null;
        if (isNaN(end) || end < start) end = start;
        return { startSection: start, endSection: end };
    }

    function anchorLines(a) {
        var html = String(a.innerHTML || '').replace(/<br\s*\/?>/gi, '\n');
        var tmp = document.createElement('div');
        tmp.innerHTML = html;
        return String(tmp.textContent || '')
            .replace(/\u00a0/g, ' ')
            .split('\n')
            .map(function (x) { return clean(x); })
            .filter(function (x) { return x.length > 0 && x.indexOf('||') === -1; });
    }

    function buildMatrix(table) {
        var rows = table.rows;
        var grid = [];
        var carries = [];
        for (var r = 0; r < rows.length; r++) {
            var line = [];
            grid.push(line);
            for (var i = 0; i < carries.length; i++) {
                var car = carries[i];
                if (car.remaining > 0) {
                    for (var b = 0; b < car.span; b++) line[car.start + b] = car.cell;
                    car.remaining--;
                }
            }
            var col = 0;
            var cells = rows[r].cells;
            for (var k = 0; k < cells.length; k++) {
                var cell = cells[k];
                while (line[col] !== undefined) col++;
                var rs = cell.rowSpan || 1;
                var cs = cell.colSpan || 1;
                for (var a = 0; a < cs; a++) line[col + a] = cell;
                if (rs > 1) carries.push({ cell: cell, remaining: rs - 1, start: col, span: cs });
                col += cs;
            }
            carries = carries.filter(function (x) { return x.remaining > 0; });
        }
        return grid;
    }

    function parseTable(root) {
        var table = (root || document).querySelector('table.table-course');
        if (!table) return [];

        var grid = buildMatrix(table);
        if (grid.length < 2) return [];

        var courses = [];
        for (var r = 1; r < grid.length; r++) {
            var seen = [];
            for (var c = 2; c <= 8 && c < grid[r].length; c++) {
                var cell = grid[r][c];
                if (!cell || seen.indexOf(cell) !== -1) continue;
                seen.push(cell);

                var day = c - 1;
                if (day < 1 || day > 7) continue;

                var anchors = cell.querySelectorAll('a');
                for (var i = 0; i < anchors.length; i++) {
                    var lines = anchorLines(anchors[i]);
                    if (lines.length < 2) continue;

                    var name = cleanCourseName(lines[0]);
                    if (!name) continue;

                    var weekLine = '', sectionLine = '';
                    for (var j = 1; j < lines.length; j++) {
                        if (!weekLine && /周/.test(lines[j])) weekLine = lines[j];
                        else if (!sectionLine && /第\s*\d+\s*节/.test(lines[j])) sectionLine = lines[j];
                    }
                    var weeks = parseWeeks(weekLine);
                    var sec = parseSections(sectionLine);
                    if (!weeks.length || !sec) continue;

                    // 节次行之后的文本依次是教师、地点
                    var secIndex = lines.indexOf(sectionLine);
                    var teacher = lines.length > secIndex + 1 ? cleanTeacher(lines[secIndex + 1]) : '未安排';
                    var position = lines.length > secIndex + 2 ? clean(lines.slice(secIndex + 2).join(' ')) : '';

                    courses.push({
                        name: name,
                        teacher: teacher,
                        position: position || '待定',
                        day: day,
                        startSection: sec.startSection,
                        endSection: sec.endSection,
                        weeks: weeks
                    });
                }
            }
        }
        return courses;
    }

    function mergeCourses(list) {
        var map = {};
        var order = [];
        for (var i = 0; i < list.length; i++) {
            var c = list[i];
            var key = [c.name, c.teacher, c.position, c.day, c.startSection, c.endSection].join('|');
            if (!map[key]) {
                map[key] = { name: c.name, teacher: c.teacher, position: c.position, day: c.day,
                    startSection: c.startSection, endSection: c.endSection, weeks: {} };
                order.push(key);
            }
            for (var j = 0; j < c.weeks.length; j++) map[key].weeks[c.weeks[j]] = true;
        }
        return order.map(function (k) {
            var c = map[k];
            return {
                name: c.name, teacher: c.teacher, position: c.position, day: c.day,
                startSection: c.startSection, endSection: c.endSection,
                weeks: Object.keys(c.weeks).map(Number).sort(function (a, b) { return a - b; })
            };
        }).sort(function (a, b) {
            return a.day - b.day || a.startSection - b.startSection || a.name.localeCompare(b.name);
        });
    }

    // 推导应用根：WebVPN 代理前缀（/https/<段>/）或站点根
    function appBase() {
        var href = window.location.href;
        var m = href.match(/^(https?:\/\/[^\/]+\/https\/[0-9a-fA-F]+\/)/);
        if (m) return m[1];
        return window.location.origin + '/';
    }

    function fetchText(url) {
        return fetch(url, { method: 'GET', credentials: 'include' }).then(function (r) {
            if (!r.ok) throw new Error('请求失败（HTTP ' + r.status + '）');
            return r.text();
        });
    }

    function collectCourses() {
        var inline = parseTable(document);
        if (inline.length) return Promise.resolve(inline);
        var url = appBase() + TABLE_PATH;
        toast('正在读取课表...');
        return fetchText(url).then(function (html) {
            var doc = new DOMParser().parseFromString(html, 'text/html');
            var courses = parseTable(doc);
            if (!courses.length) throw new Error(LOGIN_HINT);
            return courses;
        });
    }

    function runImport() {
        return alertBox(
            PLATFORM_NAME,
            '将导入「我的课表」中的研究生课程。\n' +
            '请确认已完成统一身份认证登录，并已打开研究生信息管理系统。',
            '开始导入'
        ).then(function (ok) {
            if (!ok) { toast('导入已取消'); return null; }
            return collectCourses();
        }).then(function (courses) {
            if (!courses) return null;
            var merged = mergeCourses(courses);
            return window.shangkeBridgePromise.saveImportedCourses(JSON.stringify(merged))
                .then(function () {
                    return window.shangkeBridgePromise.savePresetTimeSlots(JSON.stringify(TIME_SLOTS)).catch(function () {});
                })
                .then(function () {
                    toast('成功导入 ' + merged.length + ' 门课程');
                    window.shangkeBridge.notifyTaskCompletion();
                });
        }).catch(function (e) {
            toast('导入失败：' + (e && e.message ? e.message : e));
        });
    }

    window.shangkeImportEntry = runImport;
    window.nankaiImport = runImport;
})();
