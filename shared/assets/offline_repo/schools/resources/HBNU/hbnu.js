// 湖北师范大学：正方 V9，通过 ehall 统一认证后进入个人课表查询。
// 优先请求整学期接口；失败时才使用带明确周次的页面课表。
(function () {
    'use strict';
    var WEEK_NAMES = ['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日'];
    var lastPageDiag = '';
    var importing = null;
    function toast(text) {
        if (window.shangkeBridge && window.shangkeBridge.showToast) window.shangkeBridge.showToast(text);
    }
    // ---------- 文本工具 ----------
    function extractText(html) {
        if (!html) return '';
        return String(html).replace(/<[^>]+>/g, '').trim();
    }
    function cleanTeacher(name) {
        if (!name) return '';
        return String(name).replace(/（[^）]*）/g, '').replace(/\([^)]*\)/g, '').trim();
    }
    function cleanCourseName(name) {
        if (!name) return '';
        return String(name)
            .replace(/【[^】]*】/g, '')   // 去掉【理论】【上机】【调】等后缀
            .replace(/[■◆▲●★]/g, '')
            .replace(/\s+/g, ' ')
            .trim();
    }
    // 周次：1-16周 / 1-16周(单) / 3周 / 1-15,17-18周 / 6-16周(双)
    function parseWeeks(weekStr) {
        if (!weekStr) return [];
        var weeks = {};
        var found = false;
        var isOdd = /[（(]\s*单\s*[）)]/.test(weekStr);
        var isEven = /[（(]\s*双\s*[）)]/.test(weekStr);

        var re = /(\d+)\s*(?:[-~－—]\s*(\d+))?\s*周/g;
        var m;
        while ((m = re.exec(weekStr)) !== null) {
            var start = parseInt(m[1], 10);
            var end = m[2] ? parseInt(m[2], 10) : start;
            if (isNaN(start) || start < 1) continue;
            if (isNaN(end) || end < start) end = start;
            if (end > 32) end = 32;
            for (var w = start; w <= end; w++) { weeks[w] = true; found = true; }
        }
        if (!found) return [];

        var list = Object.keys(weeks).map(function(k) { return parseInt(k, 10); })
            .sort(function(a, b) { return a - b; });
        if (isOdd) list = list.filter(function(w) { return w % 2 === 1; });
        else if (isEven) list = list.filter(function(w) { return w % 2 === 0; });
        return list;
    }
    // 节次：(1-2节) / 第3节 / 1-2
    function parseSections(text) {
        if (!text) return null;
        var range = String(text).match(/(\d+)\s*[-~－—至到]\s*(\d+)\s*节/);
        if (range) return { start: parseInt(range[1], 10), end: parseInt(range[2], 10) };
        var single = String(text).match(/第?\s*(\d+)\s*节/);
        if (single) { var n = parseInt(single[1], 10); return { start: n, end: n }; }
        return null;
    }
    function cellLines(cell) {
        var text = cell.innerText;
        if (text === undefined || text === null || String(text).trim() === '') {
            text = (cell.innerHTML || '').replace(/<br\s*\/?>/gi, '\n').replace(/<\/(div|p|td|tr|span)>/gi, '\n').replace(/<[^>]+>/g, '');
        }
        return String(text).split(/\r?\n/).map(function(s) { return s.replace(/ /g, ' ').replace(/\s+/g, ' ').trim(); }).filter(function(s) { return s.length > 0; });
    }
    function buildGrid(table) {
        var grid = [];
        var occupied = {};
        var rows = table.rows;
        for (var r = 0; r < rows.length; r++) {
            var cells = rows[r].cells;
            var col = 0;
            for (var i = 0; i < cells.length; i++) {
                var cell = cells[i];
                while (occupied[r + ':' + col]) col++;
                var rs = parseInt(cell.getAttribute('rowspan') || '1', 10);
                var cs = parseInt(cell.getAttribute('colspan') || '1', 10);
                if (isNaN(rs) || rs < 1) rs = 1;
                if (isNaN(cs) || cs < 1) cs = 1;
                for (var rr = 0; rr < rs; rr++) {
                    for (var cc = 0; cc < cs; cc++) {
                        occupied[(r + rr) + ':' + (col + cc)] = true;
                        if (!grid[r + rr]) grid[r + rr] = [];
                        grid[r + rr][col + cc] = cell;
                    }
                }
                col += cs;
            }
        }
        return grid;
    }
    function collectDocuments() {
        var docs = [document];
        var frames = [];
        try { frames = document.querySelectorAll('iframe'); } catch (e) { frames = []; }
        for (var i = 0; i < frames.length; i++) {
            try {
                var d = frames[i].contentDocument || (frames[i].contentWindow && frames[i].contentWindow.document);
                if (d) docs.push(d);
            } catch (e) {}
        }
        return docs;
    }
    function tablesOf(doc) {
        try { return doc.querySelectorAll('table'); } catch (e) { return []; }
    }
    function findScheduleTable() {
        var docs = collectDocuments();
        var best = null, bestScore = 0;
        for (var d = 0; d < docs.length; d++) {
            var tables = tablesOf(docs[d]);
            for (var i = 0; i < tables.length; i++) {
                var text = tables[i].innerText || tables[i].textContent || '';
                var score = 0;
                if (text.indexOf('星期一') !== -1) score += 4;
                if (text.indexOf('节次') !== -1) score += 3;
                if (text.indexOf('上午') !== -1) score += 2;
                if (text.indexOf('周') !== -1) score += 1;
                if (score > bestScore) { bestScore = score; best = tables[i]; }
            }
        }
        return bestScore >= 4 ? best : null;
    }
    function guessTeacher(lines) {
        for (var i = 0; i < lines.length; i++) {
            if (/(教授|副教授|讲师|助教|研究员|老师)/.test(lines[i]) && lines[i].length <= 24) return cleanTeacher(lines[i]);
        }
        for (var j = 0; j < lines.length; j++) {
            if (/^[\u4e00-\u9fa5]{2,4}$/.test(lines[j])) return lines[j];
        }
        return '';
    }
    function guessPosition(lines) {
        for (var i = 0; i < lines.length; i++) {
            if (/(校区|教学楼|楼|教室|场馆|实验|中心)/.test(lines[i]) && lines[i].length <= 30) return lines[i];
            if (/^[\u4e00-\u9fa5]{2,}[\s\-]?[A-Za-z]?\d+/.test(lines[i])) return lines[i];
        }
        return '';
    }

    // 从页面已渲染的课表表格中提取课程
    function parseScheduleFromPage() {
        var table = findScheduleTable();
        if (!table) { lastPageDiag = '未定位到课表表格'; return []; }

        var grid = buildGrid(table);
        var headerRow = -1, dayColumns = {}, sectionCol = -1;

        for (var r = 0; r < grid.length; r++) {
            var row = grid[r] || [];
            for (var c = 0; c < row.length; c++) {
                var cell = row[c];
                if (!cell) continue;
                var text = (cell.innerText || cell.textContent || '').trim();
                if (!text) continue;
                if (sectionCol === -1 && text.indexOf('节次') !== -1) sectionCol = c;
                for (var d = 0; d < WEEK_NAMES.length; d++) {
                    if (text === WEEK_NAMES[d] || text.indexOf(WEEK_NAMES[d]) === 0) {
                        dayColumns[d + 1] = c;
                        if (headerRow === -1) headerRow = r;
                    }
                }
            }
        }
        if (Object.keys(dayColumns).length === 0) { lastPageDiag = '已定位表格但未识别到星期表头'; return []; }

        var courses = [], handled = [];
        var startRow = headerRow >= 0 ? headerRow + 1 : 0;
        var scanned = 0, withText = 0;

        for (var r2 = startRow; r2 < grid.length; r2++) {
            var row2 = grid[r2] || [];
            var fallbackSection = null;
            if (sectionCol >= 0 && row2[sectionCol]) {
                var st = (row2[sectionCol].innerText || row2[sectionCol].textContent || '').trim();
                var sn = parseInt(st.replace(/[^\d]/g, ''), 10);
                if (!isNaN(sn) && sn >= 1 && sn <= 24) fallbackSection = sn;
            }
            for (var day = 1; day <= 7; day++) {
                var col = dayColumns[day];
                if (col === undefined) continue;
                var target = row2[col];
                if (!target || handled.indexOf(target) !== -1) continue;
                handled.push(target);
                scanned++;
                var lines = cellLines(target);
                if (lines.length === 0) continue;
                withText++;
                var name = cleanCourseName(lines[0]);
                if (!name || name.length < 2) continue;
                var full = lines.join(' ');
                var sections = parseSections(full);
                if (!sections) {
                    if (fallbackSection) sections = { start: fallbackSection, end: fallbackSection };
                    else continue;
                }
                if (sections.start < 1 || sections.end < sections.start) continue;
                var weeks = parseWeeks(full);
                courses.push({
                    name: name,
                    teacher: guessTeacher(lines),
                    position: guessPosition(lines),
                    day: day,
                    startSection: sections.start,
                    endSection: sections.end,
                    weeks: weeks,
                    remark: ''
                });
            }
        }
        lastPageDiag = '表头列' + Object.keys(dayColumns).length + '/扫描格' + scanned + '/有文本' + withText + '/有效' + courses.length;
        return courses;
    }

    // 解析接口节次：jcs / jc / djj+cs
    function parseApiSections(item) {
        var numbers = String(item.jcs || item.jc || '').match(/\d+/g);
        if (numbers && numbers.length > 0) {
            var start = parseInt(numbers[0], 10);
            var end = parseInt(numbers[numbers.length - 1], 10);
            if (!isNaN(start) && !isNaN(end) && start >= 1 && end >= start) return { start: start, end: end };
        }
        if (item.djj) {
            var s = parseInt(item.djj, 10);
            var cnt = parseInt(item.cs || '1', 10);
            if (!isNaN(s)) return { start: s, end: s + (isNaN(cnt) ? 0 : cnt) - 1 };
        }
        return null;
    }

    // 解析接口周次：zcd，兼容 1-16 / 1-16(单/双) / 1,3,5
    function parseApiWeeks(value) {
        var weeks = {};
        var found = false;
        String(value || '').replace(/（/g, '(').replace(/）/g, ')').split(/[，,、;]/).forEach(function(part) {
            var numbers = part.match(/\d+/g);
            if (!numbers) return;
            var start = parseInt(numbers[0], 10);
            var end = parseInt(numbers[numbers.length - 1], 10);
            if (isNaN(start) || isNaN(end) || end < start) return;
            if (end > 32) end = 32;
            var odd = part.indexOf('单') !== -1;
            var even = part.indexOf('双') !== -1;
            for (var w = start; w <= end; w++) {
                if (odd && w % 2 === 0) continue;
                if (even && w % 2 !== 0) continue;
                weeks[w] = true;
                found = true;
            }
        });
        if (!found) return [];
        return Object.keys(weeks).map(function(k) { return parseInt(k, 10); })
            .sort(function(a, b) { return a - b; });
    }

    // 解析并导入课程（接口数据）。
    //
    // 返回 `{ courses, skipped }`：**不静默丢课**，也不因个别行残缺就整单失败。
    // 背景（2026-10-27 用户报「缺少周次信息导入失败」）：v4.79.0 起这里是
    // `if (courses.length !== rows.length) throw`，只要 kbList 里有一行 zcd 为空
    // （实践环节/军训/重修等无周次行很常见）整份课表就导不进来。
    // 现改为：能解析的照常导入，解析不了的记进 skipped 并在导入前明确告知课程名，
    // 既不静默漏课，也不让一条脏数据毁掉整次导入（口径同 czit.js / zhengfang.js）。
    function parseAndImport(kbList) {
        var courses = [];
        var skipped = [];
        kbList.forEach(function(item) {
            var name = cleanCourseName(extractText(item.kcmc));
            var teacher = cleanTeacher(extractText(item.xm || ''));
            var position = extractText(item.cdmc || '') || '待定';
            var day = parseInt(item.xqj);
            var sections = parseApiSections(item);
            var weeks = parseApiWeeks(item.zcd);
            if (weeks.length === 0) weeks = parseWeeks(item.zcd || '');

            if (!name || isNaN(day) || day < 1 || day > 7 || !sections || weeks.length === 0) {
                skipped.push(name || ('第 ' + (courses.length + skipped.length + 1) + ' 条记录'));
                return;
            }

            var remarkParts = [];
            if (item.jxbmc) remarkParts.push('教学班：' + item.jxbmc);
            if (item.xf) remarkParts.push('学分：' + item.xf);

            courses.push({
                name: name,
                teacher: teacher || '未安排',
                position: position,
                day: day,
                startSection: sections.start,
                endSection: sections.end,
                weeks: weeks,
                remark: remarkParts.join('；')
            });
        });

        return { courses: courses, skipped: skipped };
    }

    // 递归处理同源门户/代理 iframe；跨域 iframe 交由用户进入后再执行。
    function findContext() {
        var seen = [];
        function visit(w) {
            try {
                if (seen.indexOf(w) >= 0) return null;
                seen.push(w);
                var d = w.document;
                if (/\/(?:kbcx|xtgl)\//i.test(w.location.pathname) ||
                    w.location.hostname === 'jwxt.hbnu.edu.cn') return { window: w, document: d };
                var frames = d.querySelectorAll('iframe');
                for (var i = 0; i < frames.length; i++) {
                    var found = visit(frames[i].contentWindow);
                    if (found) return found;
                }
            } catch (e) { /* 跨域页面不能从门户读取。 */ }
            return null;
        }
        return visit(window);
    }
    function termOf(ctx) {
        var now = new Date(), month = now.getMonth() + 1, year = now.getFullYear();
        var xn = ctx.document.getElementById('xnm');
        var xq = ctx.document.getElementById('xqm');
        // 页面下拉框可能存在但 value 为空串（未选择学期），空值必须回落到日期启发，
        // 否则 xnm='' 会以空参数请求接口，返回的 kbList 全为初始化结构（2026-10-27 用户实测踩坑）。
        var xnValue = xn && xn.value != null ? String(xn.value).trim() : '';
        var xqValue = xq && xq.value != null ? String(xq.value).trim() : '';
        return {
            xnm: xnValue || String(month >= 9 ? year : year - 1),
            xqm: xqValue || (month >= 2 && month < 9 ? '12' : '3')
        };
    }
    function request(ctx, url, body) {
        return new Promise(function (resolve, reject) {
            var Xhr = ctx.window.XMLHttpRequest || XMLHttpRequest;
            var xhr = new Xhr();
            xhr.open('POST', url, true);
            xhr.withCredentials = true;
            xhr.timeout = 15000;
            xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
            xhr.onreadystatechange = function () {
                if (xhr.readyState !== 4) return;
                if (xhr.status === 0) return; // 网络错误/超时由对应事件给出准确提示。
                if (xhr.status !== 200) return reject(new Error('教务接口暂不可用（HTTP ' + xhr.status + '），校外请确认校园网/VPN连接'));
                try {
                    var data = JSON.parse(xhr.responseText);
                    if (!Array.isArray(data.kbList) || !data.kbList.length) return reject(new Error('当前学期没有课表，请确认学年学期后查询'));
                    resolve(data.kbList);
                } catch (e) { reject(new Error('登录可能已失效，请重新登录教务系统后进入个人课表查询')); }
            };
            xhr.onerror = function () { reject(new Error('教务网络请求失败，请检查校园网/VPN连接')); };
            xhr.ontimeout = function () { reject(new Error('教务请求超时，请稍后重试')); };
            xhr.onabort = function () { reject(new Error('教务请求已中断，请重试')); };
            xhr.send(body);
        });
    }
    async function fetchCoursesFromApi(ctx) {
        var location = ctx.window.location;
        var match = location.search.match(/[?&]gnmkdm=([^&]+)/);
        var module = match ? decodeURIComponent(match[1]) : 'N253508';
        var pos = location.pathname.indexOf('/kbcx/');
        var prefix = pos >= 0 ? location.pathname.slice(0, pos) : location.pathname.indexOf('/jwglxt/') >= 0 ? '/jwglxt' : '';
        var candidates = [];
        try {
            (ctx.window.performance.getEntriesByType('resource') || []).forEach(function (entry) {
                var url = new URL(entry.name, location.href);
                if (/xskbcx_cxXsgrkb\.html/i.test(url.pathname) && url.origin === location.origin) candidates.push(url.href);
            });
        } catch (e) { /* 无资源记录时按实际课表路径推导。 */ }
        candidates.push(new URL(prefix + '/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=' + encodeURIComponent(module), location.href).href);
        var term = termOf(ctx), lastError;
        for (var i = 0; i < candidates.length; i++) {
            if (candidates.indexOf(candidates[i]) !== i) continue;
            try {
                var rows = await request(ctx, candidates[i], 'xnm=' + encodeURIComponent(term.xnm) + '&xqm=' + encodeURIComponent(term.xqm));
                var parsed = parseAndImport(rows);
                if (!parsed.courses.length) throw new Error('接口返回的课表没有可用的星期/节次/周次，请确认已查询整学期课表');
                return parsed;
            } catch (e) { lastError = e; }
        }
        throw lastError || new Error('未找到课表接口');
    }
    function mergeCourses(courses) {
        var map = {};
        courses.forEach(function (c) {
            if (!c.name || !c.weeks.length) return;
            var key = JSON.stringify([c.name, c.teacher, c.position, c.day, c.startSection, c.endSection]);
            if (!map[key]) map[key] = c;
            else map[key].weeks = map[key].weeks.concat(c.weeks);
        });
        var rows = Object.keys(map).map(function (key) {
            var c = map[key];
            c.weeks = c.weeks.filter(function (week, index, all) { return all.indexOf(week) === index; }).sort(function (a,b) { return a-b; });
            return c;
        });
        rows.sort(function (a,b) { return a.startSection-b.startSection; });
        for (var i = 0; i < rows.length; i++) {
            for (var j = i+1; j < rows.length; j++) {
                var a = rows[i], b = rows[j];
                if (a.name === b.name && a.teacher === b.teacher && a.position === b.position && a.day === b.day &&
                    a.endSection + 1 === b.startSection && a.weeks.join(',') === b.weeks.join(',')) {
                    a.endSection = b.endSection;
                    rows.splice(j--, 1);
                }
            }
        }
        return rows;
    }
    // 部分接口行残缺（无 zcd / 无节次 / 星期越界）时，先弹窗列明被跳过的课程名，
    // 再继续保存其余课程 —— 既不静默漏课，也不让一条脏数据毁掉整次导入。
    function notifySkippedCourses(skipped) {
        var unique = [];
        for (var i = 0; i < skipped.length; i++) {
            if (skipped[i] && unique.indexOf(skipped[i]) < 0) unique.push(skipped[i]);
        }
        if (!unique.length) return Promise.resolve();
        var shown = unique.slice(0, 6).join('、');
        var suffix = unique.length > 6 ? ' 等 ' + unique.length + ' 条' : '';
        var text = '有 ' + skipped.length + ' 条课程记录缺少星期/节次/周次信息，已跳过：' + shown + suffix +
            '。其余课程已正常导入；如需补录，请在教务系统「个人课表查询」确认后手动添加。';
        toast('部分课程因缺少周次信息被跳过，详见弹窗');
        var bridge = window.shangkeBridgePromise;
        if (bridge && typeof bridge.showAlert === 'function') {
            return bridge.showAlert('部分课程未导入', text, '知道了').catch(function () { /* 用户关闭弹窗不阻断导入 */ });
        }
        return Promise.resolve();
    }

    async function runImport() {
        var ctx = findContext();
        if (!ctx) {
            toast('请先在办事大厅登录并打开「教务系统 → 个人课表查询」，校外请连接校园网/VPN后重试');
            return;
        }
        toast('正在读取完整学期课表…');
        var parsed;
        try { parsed = await fetchCoursesFromApi(ctx); }
        catch (error) {
            var pageCourses = parseScheduleFromPage().filter(function (c) { return c.weeks.length > 0; });
            if (!pageCourses.length) throw error;
            parsed = { courses: pageCourses, skipped: [] };
            toast('接口不可用，已按课表页面解析');
        }
        var courses = mergeCourses(parsed.courses);
        await notifySkippedCourses(parsed.skipped || []);
        var bridge = window.shangkeBridgePromise;
        if (!bridge || typeof bridge.saveImportedCourses !== 'function') throw new Error('导入桥接不可用，请返回 App 重新执行导入');
        var ok = await bridge.saveImportedCourses(JSON.stringify(courses));
        if (ok !== true && ok !== 'true') throw new Error('课表保存未成功，请确认已选择课表后重试');
        toast('成功导入 ' + courses.length + ' 项课程安排');
        if (window.shangkeBridge && window.shangkeBridge.notifyTaskCompletion) window.shangkeBridge.notifyTaskCompletion();
    }
    function startImport() {
        if (!importing) importing = runImport().catch(function (error) {
            if (typeof window.__shangkeReportScriptError === 'function') window.__shangkeReportScriptError(error);
            else toast(error && error.message || '课表导入失败，请重新登录后重试');
        }).then(function () { importing = null; });
        return importing;
    }
    window.zhengfangImport = startImport;
    window.shangkeImportEntry = startImport;
})();
