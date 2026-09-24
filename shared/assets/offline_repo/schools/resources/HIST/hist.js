// 河南科技学院 · 青果（Kingosoft）高校教学综合管理服务平台 个人课表适配
//
// 入口：http://jwgl.hist.edu.cn/caslogin
//   未登录时 302 到 auth.hist.edu.cn 统一身份认证；登录后回到
//   http://jwgl.hist.edu.cn/frame/homes.action （教学综合管理服务平台门户）。
//
// 课表数据（均为同源 GET，携带登录态）：
//   ① 包装页 /student/xkjg.wdkb.jsp?menucode=S20301 → 内含内部学号 xh
//   ② /jw/common/showYearTerm.action → 当前学年学期 { xn, xqM }
//   ③ /student/wsxk.xskcb10319.jsp?params=base64(xn=..&xq=..&xh=..) → 二维课表表格
//
// 注意：③ 直接顶层导航会命中 /frame/errors/405.jsp（校验 Referer），
//   但用 fetch 从站内任意页面发起即返回正常表格，故统一走 fetch。
//
// 表格结构（table#mytable）：
//   表头行：colspan=2 占位 + 星期一…星期日（第 2..8 列 → 星期 1..7）
//   数据行：上午/中午/下午/晚上（rowspan）+ 节次单元格 + 7 个星期单元格
//   课程单元格内每个 <div> 一门课，文本按 <br> 分行：
//     课程名 / 教师 / 周次[节次]（如 3-4,6-14[1-2]、1-11,13-17 双 [3-4]）/ 地点

(function () {
    'use strict';

    var PLATFORM_NAME = '河南科技学院课表导入';
    var WRAPPER_PATH = '/student/xkjg.wdkb.jsp?menucode=S20301';
    var TERM_PATH = '/jw/common/showYearTerm.action';
    var GRID_PATH = '/student/wsxk.xskcb10319.jsp';
    var LOGIN_HINT = '尚未登录或登录已过期：请先在统一身份认证页面完成登录，再点「执行导入」';

    // 河南科技学院作息（青果平台展示的节次时间）
    var TIME_SLOTS = [
        { number: 1, startTime: '08:00', endTime: '08:45' },
        { number: 2, startTime: '08:55', endTime: '09:40' },
        { number: 3, startTime: '10:00', endTime: '10:45' },
        { number: 4, startTime: '10:55', endTime: '11:40' },
        { number: 5, startTime: '12:00', endTime: '12:45' },
        { number: 6, startTime: '12:55', endTime: '13:40' },
        { number: 7, startTime: '14:30', endTime: '15:15' },
        { number: 8, startTime: '15:25', endTime: '16:10' },
        { number: 9, startTime: '16:20', endTime: '17:05' },
        { number: 10, startTime: '17:15', endTime: '18:00' },
        { number: 11, startTime: '19:00', endTime: '19:45' },
        { number: 12, startTime: '19:55', endTime: '20:40' }
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

    // 青果平台为 GBK 页面，必须按 GBK 解码后再交给 DOMParser，否则课程名乱码
    function decodeBuffer(buffer, encoding) {
        try {
            return new TextDecoder(encoding || 'gbk').decode(buffer);
        } catch (e) {
            return new TextDecoder('utf-8').decode(buffer);
        }
    }

    function fetchBuffer(path) {
        return fetch(path, { method: 'GET', credentials: 'include' }).then(function (r) {
            if (!r.ok) throw new Error('请求失败（HTTP ' + r.status + '）');
            return r.arrayBuffer();
        });
    }

    function fetchText(path, encoding) {
        return fetchBuffer(path).then(function (buf) { return decodeBuffer(buf, encoding); });
    }

    function isLoginHtml(html) {
        return /login\.action|统一身份认证|<title>\s*用户登录/.test(html) || html.indexOf('caslogin') !== -1;
    }

    // 解析周次 + 节次："3-4,6-14[1-2]" / "1-11,13-17 双 [3-4]" / "15[1-2]"
    function parseWeekSection(text) {
        var s = clean(text);
        var sm = s.match(/\[(\d+)\s*(?:[-~—－]\s*(\d+))?\]/);
        if (!sm) return null;
        var start = parseInt(sm[1], 10);
        var end = sm[2] ? parseInt(sm[2], 10) : start;
        if (isNaN(start) || start < 1) return null;
        if (isNaN(end) || end < start) end = start;

        var weekPart = s.substring(0, s.indexOf('['));
        var odd = /单/.test(weekPart);
        var even = /双/.test(weekPart);
        var set = {};
        var re = /(\d+)\s*(?:[-~—－]\s*(\d+))?/g;
        var m;
        while ((m = re.exec(weekPart)) !== null) {
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
        var weeks = Object.keys(set).map(Number).sort(function (x, y) { return x - y; });
        if (!weeks.length) return null;
        return { weeks: weeks, startSection: start, endSection: end };
    }

    // 课程单元格内每个 <div> 一门课，按 <br> 拆行
    function divLines(div) {
        var html = String(div.innerHTML || '').replace(/<br\s*\/?>/gi, '\n');
        var tmp = document.createElement('div');
        tmp.innerHTML = html;
        return String(tmp.textContent || '')
            .replace(/\u00a0/g, ' ')
            .split('\n')
            .map(function (x) { return clean(x); })
            .filter(function (x) { return x.length > 0; });
    }

    // 展开 rowspan / colspan，得到 row → col → td 的矩阵
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

    function parseGrid(html) {
        var doc = new DOMParser().parseFromString(html, 'text/html');
        var table = doc.getElementById('mytable') || doc.querySelector('table.table');
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

                var day = c - 1; // 第 2..8 列 = 星期一..星期日
                if (day < 1 || day > 7) continue;

                var divs = cell.querySelectorAll('div');
                for (var d = 0; d < divs.length; d++) {
                    var div = divs[d];
                    if (/div_nokb/.test(div.className || '')) continue;
                    var lines = divLines(div);
                    if (!lines.length) continue;

                    var wsIndex = -1;
                    for (var i = 0; i < lines.length; i++) {
                        if (/\[/.test(lines[i])) { wsIndex = i; break; }
                    }
                    if (wsIndex < 0) continue;

                    var ws = parseWeekSection(lines[wsIndex]);
                    if (!ws) continue;

                    var name = cleanCourseName(lines[0]);
                    if (!name) continue;

                    var teacher = wsIndex > 1 ? cleanTeacher(lines.slice(1, wsIndex).join(' ')) : '未安排';
                    var position = lines.length > wsIndex + 1 ? clean(lines.slice(wsIndex + 1).join(' ')) : '';

                    courses.push({
                        name: name,
                        teacher: teacher,
                        position: position || '待定',
                        day: day,
                        startSection: ws.startSection,
                        endSection: ws.endSection,
                        weeks: ws.weeks
                    });
                }
            }
        }
        return courses;
    }

    // 同一课程（名/师/地点/星期/节次）合并周次，避免碎块
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
                name: c.name,
                teacher: c.teacher,
                position: c.position,
                day: c.day,
                startSection: c.startSection,
                endSection: c.endSection,
                weeks: Object.keys(c.weeks).map(Number).sort(function (a, b) { return a - b; })
            };
        }).sort(function (a, b) {
            return a.day - b.day || a.startSection - b.startSection || a.name.localeCompare(b.name);
        });
    }

    function extractXh(html) {
        var m = html.match(/id="xh"[^>]*value="([^"]*)"/) || html.match(/name="xh"[^>]*value="([^"]*)"/);
        return m ? clean(m[1]) : '';
    }

    function base64Ascii(str) {
        try { return btoa(str); } catch (e) { return ''; }
    }

    function fetchCourses() {
        // ① 包装页取内部学号（同时校验登录态）
        return fetchText(WRAPPER_PATH, 'gbk').then(function (wrapper) {
            if (isLoginHtml(wrapper) || wrapper.indexOf('errors/405') !== -1) {
                throw new Error(LOGIN_HINT);
            }
            var xh = extractXh(wrapper);
            if (!xh) throw new Error('未取到学号，请确认已登录教学综合管理服务平台');

            // ② 当前学年学期
            return fetchText(TERM_PATH, 'utf-8').then(function (termText) {
                var term = null;
                try { term = JSON.parse(termText); } catch (e) {}
                if (!term || !term.xn) throw new Error('未取到当前学年学期，请稍后重试');
                var xn = clean(term.xn);
                var xq = clean(term.xqM);
                var params = base64Ascii('xn=' + xn + '&xq=' + xq + '&xh=' + xh);
                if (!params) throw new Error('课表参数构造失败');

                // ③ 二维课表
                return fetchText(GRID_PATH + '?params=' + encodeURIComponent(params), 'gbk').then(function (gridHtml) {
                    if (isLoginHtml(gridHtml)) throw new Error(LOGIN_HINT);
                    var courses = parseGrid(gridHtml);
                    if (!courses.length) {
                        throw new Error('未解析到课程（' + xn + '-' + xn + ' 学年）');
                    }
                    return { courses: mergeCourses(courses), term: xn + '-' + (Number(xn) + 1) + ' 学年' };
                });
            });
        });
    }

    function runImport() {
        return alertBox(
            PLATFORM_NAME,
            '将从教学综合管理服务平台导入当前学期的个人课表。\n请确认已完成统一身份认证登录。',
            '开始导入'
        ).then(function (ok) {
            if (!ok) { toast('导入已取消'); return null; }
            toast('正在获取课表...');
            return fetchCourses();
        }).then(function (result) {
            if (!result) return null;
            return window.shangkeBridgePromise.saveImportedCourses(JSON.stringify(result.courses))
                .then(function () {
                    return window.shangkeBridgePromise.savePresetTimeSlots(JSON.stringify(TIME_SLOTS)).catch(function () {});
                })
                .then(function () {
                    toast('成功导入 ' + result.courses.length + ' 门课程（' + result.term + '）');
                    window.shangkeBridge.notifyTaskCompletion();
                });
        }).catch(function (e) {
            toast('导入失败：' + (e && e.message ? e.message : e));
        });
    }

    window.shangkeImportEntry = runImport;
    window.histImport = runImport;
})();
