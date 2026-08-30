// 正方教务系统通用适配器
// 适配新版正方教务 (jwglxt)
// 使用 Bridge 方式与原生通信
// 优先解析当前页面已渲染的课表，接口抓取作为兜底

(function() {
    'use strict';

    var WEEK_NAMES = ['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日'];
    var lastPageDiag = '';

    // 通用工具函数
    function extractText(html) {
        if (!html) return '';
        return html.replace(/<[^>]+>/g, '').trim();
    }

    function cleanTeacher(name) {
        if (!name) return '';
        return name.replace(/（[^）]*）/g, '').replace(/\([^)]*\)/g, '').trim();
    }

    // 清理课程名：去掉【调】这类前缀与 ■◆▲ 等图例符号
    function cleanCourseName(name) {
        if (!name) return '';
        return name
            .replace(/【[^】]*】/g, '')
            .replace(/[■◆▲●★]/g, '')
            .replace(/\s+/g, ' ')
            .trim();
    }

    // 解析周次：支持 1-16周 / 1-16周(单) / 3周 / 1-15,17-18周
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
            for (var w = start; w <= end; w++) {
                weeks[w] = true;
                found = true;
            }
        }

        if (!found) return [];

        var list = Object.keys(weeks).map(function(k) { return parseInt(k, 10); })
            .sort(function(a, b) { return a - b; });
        if (isOdd) list = list.filter(function(w) { return w % 2 === 1; });
        else if (isEven) list = list.filter(function(w) { return w % 2 === 0; });
        return list;
    }

    // 解析节次：支持 (1-2节) / 第3节 / 1-2
    function parseSections(text) {
        if (!text) return null;
        var range = text.match(/(\d+)\s*[-~－—至到]\s*(\d+)\s*节/);
        if (range) {
            return { start: parseInt(range[1], 10), end: parseInt(range[2], 10) };
        }
        var single = text.match(/第?\s*(\d+)\s*节/);
        if (single) {
            var n = parseInt(single[1], 10);
            return { start: n, end: n };
        }
        return null;
    }

    // 按行拆分单元格文本，兼容 innerText 缺失的情况
    function cellLines(cell) {
        var text = cell.innerText;
        if (text === undefined || text === null || String(text).trim() === '') {
            text = (cell.innerHTML || '')
                .replace(/<br\s*\/?>/gi, '\n')
                .replace(/<\/(div|p|td|tr|span)>/gi, '\n')
                .replace(/<[^>]+>/g, '');
        }
        return String(text)
            .split(/\r?\n/)
            .map(function(s) { return s.replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim(); })
            .filter(function(s) { return s.length > 0; });
    }

    // 还原表格二维结构，处理 rowspan / colspan
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

    // 收集当前框架及其子框架内的文档（正方课表常渲染在 iframe 中）
    function collectDocuments() {
        var docs = [document];
        var frames = [];
        try {
            frames = document.querySelectorAll('iframe');
        } catch (e) {
            frames = [];
        }
        for (var i = 0; i < frames.length; i++) {
            try {
                var d = frames[i].contentDocument;
                if (!d) {
                    var w = frames[i].contentWindow;
                    if (w) d = w.document;
                }
                if (d) docs.push(d);
            } catch (e) {
                // 跨域框架无法访问，忽略
            }
        }
        return docs;
    }

    function tablesOf(doc) {
        try {
            return doc.querySelectorAll('table');
        } catch (e) {
            return [];
        }
    }

    // 定位页面上的课表表格
    function findScheduleTable() {
        var docs = collectDocuments();
        var best = null;
        var bestScore = 0;
        for (var d = 0; d < docs.length; d++) {
            var tables = tablesOf(docs[d]);
            for (var i = 0; i < tables.length; i++) {
                var text = tables[i].innerText || tables[i].textContent || '';
                var score = 0;
                if (text.indexOf('星期一') !== -1) score += 4;
                if (text.indexOf('节次') !== -1) score += 3;
                if (text.indexOf('上午') !== -1) score += 2;
                if (text.indexOf('周') !== -1) score += 1;
                if (score > bestScore) {
                    bestScore = score;
                    best = tables[i];
                }
            }
        }
        return bestScore >= 4 ? best : null;
    }

    // 诊断页面结构，便于排查"解析不到课程"的原因
    function diagnosePage() {
        var docs = collectDocuments();
        var tableCount = 0;
        var weekHeaderCount = 0;
        for (var i = 0; i < docs.length; i++) {
            var tables = tablesOf(docs[i]);
            tableCount += tables.length;
            for (var t = 0; t < tables.length; t++) {
                var text = tables[t].innerText || tables[t].textContent || '';
                if (text.indexOf('星期一') !== -1) weekHeaderCount++;
            }
        }
        return '文档' + docs.length + '个/表格' + tableCount + '个/含星期表头' + weekHeaderCount + '个';
    }

    // 猜测教师：优先识别职称，其次短中文名
    function guessTeacher(lines) {
        for (var i = 0; i < lines.length; i++) {
            if (/(教授|副教授|讲师|助教|研究员|老师)/.test(lines[i]) && lines[i].length <= 24) {
                return cleanTeacher(lines[i]);
            }
        }
        for (var j = 0; j < lines.length; j++) {
            if (/^[\u4e00-\u9fa5]{2,4}$/.test(lines[j])) return lines[j];
        }
        return '';
    }

    // 猜测上课地点
    function guessPosition(lines) {
        for (var i = 0; i < lines.length; i++) {
            if (/(校区|教学楼|楼|教室|场馆|实验|中心)/.test(lines[i]) && lines[i].length <= 30) {
                return lines[i];
            }
            if (/^[\u4e00-\u9fa5]{2,}[\s\-]?[A-Za-z]?\d+/.test(lines[i])) return lines[i];
        }
        return '';
    }

    // 从页面已渲染的课表表格中提取课程
    function parseScheduleFromPage() {
        var table = findScheduleTable();
        if (!table) {
            lastPageDiag = '未定位到课表表格';
            return [];
        }

        var grid = buildGrid(table);
        var headerRow = -1;
        var dayColumns = {};
        var sectionCol = -1;

        for (var r = 0; r < grid.length; r++) {
            var row = grid[r] || [];
            for (var c = 0; c < row.length; c++) {
                var cell = row[c];
                if (!cell) continue;
                var text = (cell.innerText || cell.textContent || '').trim();
                if (!text) continue;
                if (sectionCol === -1 && text.indexOf('节次') !== -1) {
                    sectionCol = c;
                    headerRow = r;
                }
                for (var d = 0; d < WEEK_NAMES.length; d++) {
                    if (text === WEEK_NAMES[d] || text.indexOf(WEEK_NAMES[d]) === 0) {
                        dayColumns[d + 1] = c;
                        if (headerRow === -1) headerRow = r;
                    }
                }
            }
        }

        if (Object.keys(dayColumns).length === 0) {
            lastPageDiag = '已定位表格但未识别到星期表头';
            return [];
        }

        var courses = [];
        var handled = [];
        var startRow = headerRow >= 0 ? headerRow + 1 : 0;
        var scanned = 0;
        var withText = 0;

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
                if (weeks.length === 0) weeks = [];

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

        lastPageDiag = '表头列' + Object.keys(dayColumns).length +
            '/扫描格' + scanned + '/有文本' + withText + '/有效' + courses.length;
        return courses;
    }

    // 检查是否在正方教务页面（含 iframe 内的子文档）
    // 兼容两类系统：老版 jwglxt 路径 / 新版 V-9.0 教学管理信息服务平台（无 jwglxt 前缀）
    function isZhengfangPage() {
        if (window.location.href.indexOf('jwglxt') !== -1) return true;
        // 新版正方 V-9.0：功能页与课表页路径位于 /xtgl/ 或 /kbcx/ 下
        var url = window.location.href || '';
        if (url.indexOf('/xtgl/') !== -1 || url.indexOf('/kbcx/') !== -1) return true;
        var docs = collectDocuments();
        for (var i = 0; i < docs.length; i++) {
            try {
                var body = docs[i].body;
                if (!body) continue;
                if (body.innerHTML.indexOf('正方教务') !== -1) return true;
                var text = body.innerText || body.textContent || '';
                // 新版正方 V-9.0 登录页/首页特征标题
                if (text.indexOf('教学管理信息服务平台') !== -1) return true;
                if (text.indexOf('星期一') !== -1) return true;
            } catch (e) {
                // 忽略无法访问的文档
            }
        }
        return false;
    }

    // 获取学年学期
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

    // 从当前 URL / 页面隐藏域中提取正方功能模块编号（gnmkdm）
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
        return 'N253508';
    }

    // 从浏览器资源计时里找到页面自己已经请求过的课表接口地址（对 WebVPN 反代最有效）
    function findCourseApiFromPerformance() {
        var entries = [];
        try { entries = performance.getEntriesByType('resource') || []; } catch (e) { return null; }
        var candidates = [];
        for (var i = 0; i < entries.length; i++) {
            var name = entries[i].name || '';
            if (!name) continue;
            if (/xskbcx_cxXsgrkb/i.test(name)) return name;
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
            Bridge.showToast('请先进入正方教务系统的课表查询页面');
            return;
        }

        var xnxq = getXnxq();
        var gnmkdm = getGnmkdm();
        var basePath = window.location.pathname;
        var apiPath = '/jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=' + gnmkdm;
        var kbcxIdx = basePath.indexOf('/kbcx/');
        if (kbcxIdx !== -1) {
            apiPath = basePath.substring(0, kbcxIdx) + '/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=' + gnmkdm;
        }

        var knownApi = findCourseApiFromPerformance();
        if (knownApi) {
            apiPath = knownApi;
        }

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
                            Bridge.showToast('未查询到课程数据，请确认已进入课表页面');
                            return;
                        }
                        parseAndImport(list);
                    } catch (e) {
                        Bridge.showToast('课程数据获取失败：返回的不是数据页面，请确认已登录并停留在课表页');
                    }
                } else {
                    var respUrl = xhr.responseURL || apiPath;
                    var respHead = xhr.responseText ? String(xhr.responseText).replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').slice(0, 120) : '';
                    Bridge.showToast('课程数据请求失败（状态码 ' + xhr.status + '）｜请求:' + respUrl + '｜页面:' + window.location.href + '｜返回:' + respHead);
                }
            }
        };
        xhr.send('xnm=' + xnxq.xnm + '&xqm=' + xnxq.xqm);
    }

    // 解析接口节次字段：jcs / jc / djj+cs
    function parseApiSections(item) {
        var numbers = String(item.jcs || item.jc || '').match(/\d+/g);
        if (numbers && numbers.length > 0) {
            var start = parseInt(numbers[0], 10);
            var end = parseInt(numbers[numbers.length - 1], 10);
            if (!isNaN(start) && !isNaN(end) && start >= 1 && end >= start) {
                return { start: start, end: end };
            }
        }
        if (item.djj) {
            var s = parseInt(item.djj, 10);
            var cnt = parseInt(item.cs || '1', 10);
            if (!isNaN(s)) return { start: s, end: s + (isNaN(cnt) ? 0 : cnt) - 1 };
        }
        return null;
    }

    // 解析接口周次字段：zcd，兼容 1-16 / 1-16(单) / 1,3,5
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
            var teacher = cleanTeacher(extractText(item.xm || item.tmc || ''));
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

    // 导入入口：优先解析页面课表，失败再走接口
    function fetchCourses() {
        if (!isZhengfangPage()) {
            Bridge.showToast('请先进入正方教务系统的课表查询页面');
            return;
        }

        var courses = parseScheduleFromPage();
        if (courses.length > 0) {
            Bridge.saveImportedCourses(JSON.stringify(courses));
            Bridge.showToast('成功解析 ' + courses.length + ' 门课程，正在导入...');
            return;
        }

        var noTextYet = /有文本0/.test(lastPageDiag || '');
        if (noTextYet && !window.__zfRetried) {
            window.__zfRetried = true;
            Bridge.showToast('正在读取课表数据...');
            window.setTimeout(function () { fetchCourses(); }, 1000);
            return;
        }

        fetchCoursesFromApi();
    }

    // 暴露给页面调用
    window.zhengfangImport = fetchCourses;
    // 统一入口声明：导入脚本执行完毕后由宿主自动调用，避免依赖全局属性扫描
    window.shangkeImportEntry = fetchCourses;

    // 自动检测并提示
    if (isZhengfangPage()) {
        Bridge.showToast('检测到正方教务系统，点击导入按钮抓取课表');
    }
})();
