// 湖北师范大学 教务适配器（正方 V-9.0 · jwxt.hbnu.edu.cn · 经 ehall 统一认证进入）
// 课表页：https://jwxt.hbnu.edu.cn/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N253508
// 数据接口：POST /kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N253508  参数 xnm=<学年值>&xqm=<学期代码>
// 实测（2026-09-29，学号 2026115070433）：
//   - xnm=2026 & xqm=3 返回 kbList（19条）+ sjkList（实践课程）
//   - kbList 字段：kcmc 课程名 / xm 教师 / zcmc 职称 / cdmc 教室 / xqj 星期(1-7) /
//     jcs 节次(如"1-2") / zcd 周次(如"5-10周") / xf 学分 / jxbmc 教学班
// 流程：ehall 统一认证登录 → 打开「教务系统」→ 进入个人课表查询页 → 自动抓取
// 优先解析页面课表表格，接口抓取作为兜底；使用 Bridge 与原生通信。

(function() {
    'use strict';

    var WEEK_NAMES = ['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日'];
    var lastPageDiag = '';

    // ---------- Bridge 桥接（兼容多种宿主封装） ----------
    var Bridge = (function () {
        function toast(msg) {
            try { if (window.shangkeBridge && window.shangkeBridge.showToast) return window.shangkeBridge.showToast(msg); } catch (e) {}
            try { if (window.shangkeBridgePromise && window.shangkeBridgePromise.showToast) return window.shangkeBridgePromise.showToast(msg); } catch (e) {}
            try { if (window.Bridge && window.Bridge.showToast) return window.Bridge.showToast(msg); } catch (e) {}
        }
        function alert(title, msg, confirmText) {
            try { if (window.shangkeBridgePromise && window.shangkeBridgePromise.showAlert) return window.shangkeBridgePromise.showAlert(title, msg, confirmText || '确定'); } catch (e) {}
            try { if (window.Bridge && window.Bridge.showAlert) return window.Bridge.showAlert(title, msg, confirmText || '确定'); } catch (e) {}
            return Promise.resolve(false);
        }
        function save(courses) {
            var s = JSON.stringify(courses);
            try { if (window.shangkeBridge && window.shangkeBridge.saveImportedCourses) return window.shangkeBridge.saveImportedCourses(s); } catch (e) {}
            try { if (window.shangkeBridgePromise && window.shangkeBridgePromise.saveImportedCourses) return window.shangkeBridgePromise.saveImportedCourses(s); } catch (e) {}
            try { if (window.Bridge && window.Bridge.saveImportedCourses) return window.Bridge.saveImportedCourses(s); } catch (e) {}
        }
        return { showToast: toast, showAlert: alert, saveImportedCourses: save };
    })();

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

    // 判断是否在正方教务页面（hbnu 为 V-9.0，路径 /kbcx/ 或 /xtgl/ 下，无 jwglxt 前缀）
    function isZhengfangPage() {
        var url = window.location.href || '';
        if (url.indexOf('jwxt.hbnu.edu.cn') !== -1) return true;
        if (url.indexOf('/kbcx/') !== -1 || url.indexOf('/xtgl/') !== -1) return true;
        var docs = collectDocuments();
        for (var i = 0; i < docs.length; i++) {
            try {
                var body = docs[i].body;
                if (!body) continue;
                var text = body.innerText || body.textContent || '';
                if (text.indexOf('正方软件') !== -1 || text.indexOf('教学管理信息服务平台') !== -1) return true;
                if (text.indexOf('个人课表查询') !== -1) return true;
            } catch (e) {}
        }
        return false;
    }

    // 获取学年学期：优先读页面下拉 xnm/xqm，否则按当前日期推算
    function getXnxq() {
        var xnm = '', xqm = '';
        var xnmSelect = document.getElementById('xnm');
        var xqmSelect = document.getElementById('xqm');
        if (xnmSelect) xnm = xnmSelect.value;
        if (xqmSelect) xqm = xqmSelect.value;
        if (!xnm) {
            var now = new Date();
            var year = now.getFullYear();
            var month = now.getMonth() + 1;
            if (month >= 9) { xnm = year.toString(); xqm = '3'; }
            else if (month >= 2) { xnm = (year - 1).toString(); xqm = '12'; }
            else { xnm = (year - 1).toString(); xqm = '3'; }
        }
        return { xnm: xnm, xqm: xqm };
    }

    // 从 URL / 隐藏域提取功能模块编号 gnmkdm
    function getGnmkdm() {
        try {
            var query = window.location.search || '';
            var qm = query.match(/[?&]gnmkdm=([^&]+)/);
            if (qm) return decodeURIComponent(qm[1]);
            var url = window.location.href || '';
            var um = url.match(/gnmkdm=([^&]+)/);
            if (um) return decodeURIComponent(um[1]);
            var el = document.getElementById('gnmkdm');
            if (el && el.value) return el.value;
        } catch (e) {}
        return 'N253508';  // hbnu 个人课表查询模块实测值
    }

    // 从 performance 资源里找页面已经请求过的课表接口（最可靠）
    function findCourseApiFromPerformance() {
        var entries = [];
        try { entries = performance.getEntriesByType('resource') || []; } catch (e) { return null; }
        var candidates = [];
        for (var i = 0; i < entries.length; i++) {
            var name = entries[i].name || '';
            if (!name) continue;
            if (/xskbcx_cxXsgrkb/.test(name)) return name;
            if (/xskbcx/i.test(name) && /gnmkdm=/.test(name)) candidates.push(name);
        }
        if (candidates.length) {
            for (var j = 0; j < candidates.length; j++) {
                if (!/xskbcx_cxXskbcxIndex/i.test(candidates[j])) return candidates[j];
            }
            return candidates[0];
        }
        return null;
    }

    // 从 API 获取课程数据（页面无课表表格时兜底）
    function fetchCoursesFromApi() {
        if (!isZhengfangPage()) {
            Bridge.showToast('请先进入正方教务系统的「个人课表查询」页面');
            return;
        }

        var xnxq = getXnxq();
        var gnmkdm = getGnmkdm();
        var basePath = window.location.pathname;
        var apiPath = '/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=' + gnmkdm;
        var kbcxIdx = basePath.indexOf('/kbcx/');
        if (kbcxIdx !== -1) {
            apiPath = basePath.substring(0, kbcxIdx) + '/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=' + gnmkdm;
        }
        var knownApi = findCourseApiFromPerformance();
        if (knownApi) apiPath = knownApi;

        Bridge.showToast('正在从教务接口获取课程数据...');
        var xhr = new XMLHttpRequest();
        xhr.open('POST', apiPath, true);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
        xhr.withCredentials = true;
        xhr.onreadystatechange = function() {
            if (xhr.readyState === 4) {
                if (xhr.status === 200) {
                    try {
                        var resp = JSON.parse(xhr.responseText);
                        var list = resp.kbList || [];
                        if (list.length === 0) {
                            Bridge.showToast('未查询到课程数据，请确认已选择正确学年学期并点查询');
                            return;
                        }
                        parseAndImport(list);
                    } catch (e) {
                        Bridge.showToast('课程数据获取失败：返回的不是数据页面，请确认已登录并停留在课表页');
                    }
                } else {
                    var respUrl = xhr.responseURL || apiPath;
                    Bridge.showToast('课程数据请求失败（状态码 ' + xhr.status + '）｜页面:' + window.location.href);
                }
            }
        };
        xhr.send('xnm=' + xnxq.xnm + '&xqm=' + xnxq.xqm);
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

    // 解析并导入课程（接口数据）
    function parseAndImport(kbList) {
        var courses = [];
        kbList.forEach(function(item) {
            var name = cleanCourseName(extractText(item.kcmc));
            var teacher = cleanTeacher(extractText(item.xm || ''));
            var position = extractText(item.cdmc || '') || '待定';
            var day = parseInt(item.xqj);
            var sections = parseApiSections(item);
            var weeks = parseApiWeeks(item.zcd);
            if (weeks.length === 0) weeks = parseWeeks(item.zcd || '');

            if (!name || isNaN(day) || day < 1 || day > 7 || !sections || weeks.length === 0) return;

            var remarkParts = [];
            if (item.jxbmc) remarkParts.push('教学班：' + item.jxbmc);
            if (item.xf) remarkParts.push('学分：' + item.xf);

            courses.push({
                name: name,
                teacher: teacher,
                position: position,
                day: day,
                startSection: sections.start,
                endSection: sections.end,
                weeks: weeks,
                remark: remarkParts.join('；')
            });
        });

        if (courses.length === 0) {
            var sample = kbList.length > 0 ? Object.keys(kbList[0]).join(',') : '空列表';
            Bridge.showToast('未提取到有效课程（接口返回' + kbList.length + '条，字段：' + sample + '）');
            return;
        }

        Bridge.saveImportedCourses(JSON.stringify(courses));
        Bridge.showToast('成功解析 ' + courses.length + ' 门课程，正在导入...');
    }

    // 湖北师范大学引导
    function showGuide() {
        var guide = '湖北师范大学教务导入步骤：\n\n'
            + '1. 在 ehall 办事大厅登录统一身份认证（账号=学号）\n'
            + '2. 搜索并打开「教务系统」（jwxt.hbnu.edu.cn）\n'
            + '3. 进入「个人课表查询」，选择学年学期后点查询\n'
            + '4. 回到本页点「确定」，自动抓取课表\n\n'
            + '提示：jwxt 仅校园网/VPN 可达。';
        return Bridge.showAlert('湖北师范大学教务导入', guide, '确定');
    }

    // 导入入口：优先解析页面课表，失败再走接口
    function fetchCourses() {
        if (!isZhengfangPage()) {
            return showGuide();
        }
        var courses = parseScheduleFromPage();
        if (courses.length > 0) {
            Bridge.saveImportedCourses(JSON.stringify(courses));
            Bridge.showToast('成功解析 ' + courses.length + ' 门课程，正在导入...');
            return;
        }
        var noTextYet = /有文本0/.test(lastPageDiag || '');
        if (noTextYet && !window.__hbnuRetried) {
            window.__hbnuRetried = true;
            Bridge.showToast('正在读取课表数据...');
            window.setTimeout(function () { fetchCourses(); }, 1000);
            return;
        }
        fetchCoursesFromApi();
    }

    window.zhengfangImport = fetchCourses;
    window.shangkeImportEntry = fetchCourses;

    if (isZhengfangPage()) {
        Bridge.showToast('检测到湖北师范大学正方教务，点击导入按钮抓取课表');
    } else if ((window.location.href || '').indexOf('ehall') !== -1 ||
               (window.location.href || '').indexOf('authserver') !== -1) {
        Bridge.showToast('请在办事大厅登录后打开「教务系统」，再进入课表页点击导入');
    }
})();
