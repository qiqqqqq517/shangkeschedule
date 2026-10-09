// 正方教务系统通用适配器
// 适配新版正方教务 (jwglxt)
// 使用 Bridge 方式与原生通信
// 优先解析当前页面已渲染的课表，接口抓取作为兜底

(function() {
    'use strict';

    var WEEK_NAMES = ['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日'];
    var lastPageDiag = '';

    // 部分正方页面库（zftal 等）会覆盖 Array.prototype.filter/some/every，
    // 且回调参数序被改成 (index, value, array)，导致依赖标准 filter 的解析把课程过滤掉。
    // 此处备份原生实现，所有过滤统一走原生版本，避免被页面库污染。
    var _nativeFilter = Array.prototype.filter;
    var _nativeSome = Array.prototype.some;
    var _nativeEvery = Array.prototype.every;

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
        if (isOdd) list = _nativeFilter.call(list, function(w) { return w % 2 === 1; });
        else if (isEven) list = _nativeFilter.call(list, function(w) { return w % 2 === 0; });
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
        return _nativeFilter.call(
            String(text)
                .split(/\r?\n/)
                .map(function(s) { return s.replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim(); }),
            function(s) { return s.length > 0; }
        );
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
        // 因「周次解析不出来」被丢弃的课程数（v4.75.14）：必须计数并如实告知用户，
        // 不能静默丢 —— 否则用户只知道「导入成功 N 门」，不知道有 M 门因页面
        // 未给出周次而没收进来（这正是「导入不成功/少课」最容易困惑的地方）。
        var skippedNoWeeks = 0;

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
                // 周次解析不出来 ⇒ **丢弃这门课**，不要落库。
                //
                // 旧写法是 `if (weeks.length === 0) weeks = [];` —— 把空数组赋给空数组，
                // 是个**空操作**，于是「周次没解析出来」的课程照样被 push 并落库。
                // 而课表 UI 的显示判据是 `weeks.any { it.weekNumber == currentWeek }`，
                // 空周次恒为 false ⇒ 该课程**任何一周都不显示**，用户看到
                // 「导入成功 N 门课」但课表上少课/看不到，表现为「导入不成功」。
                // 与同文件接口路径（parseAndImport 里 `weeks.length === 0` 直接 return）
                // 保持同一口径：宁可少收一条脏数据，也不要收进来一条永远不显示的课程。
                if (weeks.length === 0) {
                    skippedNoWeeks++;
                    continue;
                }

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
            '/扫描格' + scanned + '/有文本' + withText + '/有效' + courses.length +
            (skippedNoWeeks > 0 ? '/无周次丢弃' + skippedNoWeeks : '');
        // 有课程因周次缺失被丢弃时如实告知（不静默）：用户才知道「少的那几门」
        // 不是 App 漏了，而是页面本身没给出周次信息。
        if (skippedNoWeeks > 0) {
            Bridge.showToast('有 ' + skippedNoWeeks + ' 门课程因页面未显示周次而跳过，请在课表页确认周次信息是否完整');
        }
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
            if (/bjkbdy_cxBjKb/i.test(name)) return name;
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

    // 判断当前页面是否为班级课表页面（而非学生个人课表）
    function isClassSchedulePage() {
        try {
            var url = window.location.href || '';
            if (/kbdy\/bjkbdy/i.test(url)) return true;
            if (/bjkb/i.test(url)) return true;
            var form = document.getElementById('ajaxForm') || document.querySelector('form');
            if (form) {
                var action = form.getAttribute('action') || '';
                if (/bjkbdy/i.test(action) || /BjKb/i.test(action)) return true;
            }
            var bhId = document.getElementById('bh_id') || document.querySelector('[name=bh_id]');
            var xskbcx = document.getElementById('xskbcx') || document.querySelector('[id*=xskbcx]');
            if (bhId && !xskbcx) return true;
        } catch (e) {}
        return false;
    }

    // 从班级课表页面提取查询参数（年级、专业、班级等）
    function getClassScheduleParams() {
        var params = {};
        var fields = ['xnm', 'xqm', 'njdm_id', 'zyh_id', 'bh_id', 'tjkbzdm', 'tjkbzxsdm', 'zxszjjs'];
        for (var i = 0; i < fields.length; i++) {
            var key = fields[i];
            var el = document.getElementById(key) || document.querySelector('[name="' + key + '"]');
            if (el && el.value !== undefined && el.value !== '') {
                params[key] = el.value;
            }
        }
        try {
            if (window.api && window.api.data && window.api.data.map) {
                var map = window.api.data.map;
                for (var j = 0; j < fields.length; j++) {
                    if (!params[fields[j]] && map[fields[j]] !== undefined && map[fields[j]] !== '') {
                        params[fields[j]] = map[fields[j]];
                    }
                }
            }
        } catch (e) {}
        return params;
    }

    // 从 API 获取课程数据（页面无课表表格时兜底）
    // 自动识别个人课表 / 班级课表，分别调用对应接口
    function fetchCoursesFromApi() {
        if (!isZhengfangPage()) {
            Bridge.showToast('请先进入正方教务系统的课表查询页面');
            return;
        }

        var isClassPage = isClassSchedulePage();
        var xnxq = getXnxq();
        var gnmkdm = getGnmkdm();
        var basePath = window.location.pathname;
        var apiPath;
        var postBody;

        if (isClassPage) {
            // 班级课表接口：需要年级/专业/班级等参数
            apiPath = '/jwglxt/kbdy/bjkbdy_cxBjKb.html?gnmkdm=' + gnmkdm;
            var kbdyIdx = basePath.indexOf('/kbdy/');
            if (kbdyIdx !== -1) {
                apiPath = basePath.substring(0, kbdyIdx) + '/kbdy/bjkbdy_cxBjKb.html?gnmkdm=' + gnmkdm;
            }
            var classParams = getClassScheduleParams();
            if (!classParams.xnm) classParams.xnm = xnxq.xnm;
            if (!classParams.xqm) classParams.xqm = xnxq.xqm;
            var pairs = [];
            for (var k in classParams) {
                if (classParams.hasOwnProperty(k)) {
                    pairs.push(encodeURIComponent(k) + '=' + encodeURIComponent(classParams[k]));
                }
            }
            postBody = pairs.join('&');
        } else {
            // 个人课表接口
            apiPath = '/jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=' + gnmkdm;
            var kbcxIdx = basePath.indexOf('/kbcx/');
            if (kbcxIdx !== -1) {
                apiPath = basePath.substring(0, kbcxIdx) + '/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=' + gnmkdm;
            }
            postBody = 'xnm=' + xnxq.xnm + '&xqm=' + xnxq.xqm;
        }

        var knownApi = findCourseApiFromPerformance();
        if (knownApi) {
            apiPath = knownApi;
        }

        Bridge.showToast(isClassPage ? '正在从班级课表接口获取课程数据...' : '正在从教务接口获取课程数据...');
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
                            Bridge.showToast('未查询到课程数据，请确认已进入课表页面并选择了班级');
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
        xhr.send(postBody);
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

        // 落库必须**等待并判返回值**：Bridge.saveImportedCourses 返回 Promise，resolve(true) 才代表真的写进去了。
        // 旧实现既不 await 也不判返回值，紧随其后无条件提示「正在导入」⇒
        // 未选择课表 / 外键失败 / 落库异常时用户仍看到「成功解析 N 门课程」，
        // 而课表里一门都没进来（本脚本覆盖约 966 所学校，是单点影响面最大的一条）。
        Promise.resolve(Bridge.saveImportedCourses(JSON.stringify(courses)))
            .then(function (ok) {
                if (ok === true || ok === 'true') {
                    Bridge.showToast('成功解析 ' + courses.length + ' 门课程，正在导入...');
                } else {
                    Bridge.showToast('课表保存未成功，请确认已选择课表后重试（识别到 ' + courses.length + ' 门）');
                }
            })
            .catch(function (err) {
                Bridge.showToast('课表保存失败：' + ((err && err.message) || err || '未知错误'));
            });
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

    // ========================================================================
    // 能力钩子（v4.73.0）：成绩 / 空教室 / 学业情况
    //
    // 正方 V9 这三个功能的接口路径是**平台级**的（各校一致，见各函数注释），
    // 因此通用实现在这里写一次，1024 所正方学校即可零改动受益；
    // 学校有定制的再用专用脚本覆盖同名钩子。
    //
    // 三条纪律（详见 schools/ADAPTER_GUIDE.md「能力钩子」）：
    //   1. 钩子只 return 数据，不自己 postMessage —— 拼装与投递由 App 侧完成；
    //   2. 学期一律用接口返回的显示值（xnmmc/xqmmc），不用请求参数里的内部编码；
    //   3. 学业情况只回传「要求学分」的叶子类别，不回传平台汇总行、不回传已获学分。
    // ========================================================================

    // 同源 POST，携带教务登录态（JSESSIONID）
    function zfPost(path, body) {
        return fetch(path, {
            method: 'POST',
            headers: {
                'content-type': 'application/x-www-form-urlencoded;charset=UTF-8',
                'x-requested-with': 'XMLHttpRequest'
            },
            body: body,
            credentials: 'include'
        }).then(function (response) {
            return response.json();
        });
    }

    /**
     * 成绩接口的绩点字段 → 数值（v4.75.0）。
     *
     * 空串 / `-` / 超出 0–5 的取值一律返回 null（**不猜、不填 0**）：
     * 0 会被 App 当成「挂科绩点」参与加权，反而污染汇总；null 才会让 App 回落到按分数换算。
     */
    function gradePointOf(raw) {
        var text = String(raw == null ? '' : raw).trim();
        if (!text) return null;
        var value = Number(text);
        return (isFinite(value) && value >= 0 && value <= 5) ? value : null;
    }

    /**
     * 成绩识别钩子。
     *
     * 接口：POST /jwglxt/cjcx/cjcx_cxXsgrcj.html?doType=query
     * xnm/xqm 留空 = 全部学期（成绩页默认只查当前学期，那样会漏掉历史成绩）。
     *
     * ⚠️ 未取证声明（2026-10-07）：上面这个成绩接口路径**是按正方 V9 惯例写的，未对任何具体学校实测**。
     * 反例：南通大学（`tdjw.ntu.edu.cn`，菜单 N305005）的成绩查询实测在
     * `/jwglxt/cjcx/cjcx_cxDgXscj.html`，本路径在该校返回 404。
     * 正方各校菜单模块号可能不同 ⇒ 本钩子对部分学校可能取不到数据，属于已知未验证项。
     * 需要实测某校时，请参照 `build_qa/ntu-portal-probe.md` 的方法：
     * 登录后从菜单 `onclick="clickMenu(...)"` 读出该校的真实模块号与地址，再按该地址取数。
     */
    window.shangkeScanGrades = function () {
        return zfPost('/jwglxt/cjcx/cjcx_cxXsgrcj.html?doType=query',
            'xnm=&xqm=&sfzgcj=&kcbj=&pkey=&_search=false&nd=' + Date.now() +
            '&queryModel.showCount=500&queryModel.currentPage=1' +
            '&queryModel.sortName=&queryModel.sortOrder=asc&time=0'
        ).then(function (data) {
            var items = (data && data.items) || [];
            var grades = [];
            for (var i = 0; i < items.length; i++) {
                var item = items[i];
                var courseName = String(item.kcmc || '').trim();
                if (!courseName) continue;
                // 数字成绩优先（便于算平均分/绩点），等级制课程回落到显示成绩
                var scoreText = String(item.bfzcj || item.cj || '').trim();
                if (!scoreText) continue;
                var creditText = String(item.xf == null ? '' : item.xf).trim();
                var credit = creditText === '' ? null : Number(creditText);
                // 学期用显示值：请求参数 xqm 是内部编码（3=第1学期、12=第2学期），
                // 直接回传会把「第 1 学期」写成「第 3 学期」
                var year = String(item.xnmmc || '').trim();
                var term = String(item.xqmmc || '').trim();
                grades.push({
                    courseName: courseName,
                    credit: isFinite(credit) ? credit : null,
                    scoreText: scoreText,
                    semester: (year && term) ? (year + '-' + term) : null,
                    category: String(item.kcxzmc || item.kclbmc || '').trim() || null,
                    // 本校绩点（v4.75.0）：实测字段 `jd`（"4.00"）。学校按本校规则算出的
                    // 既成事实，App 的换算表不可能对上，必须整列带回
                    gradePoint: gradePointOf(item.jd)
                });
            }
            return grades;
        });
    };

    /**
     * 空教室读取钩子。
     *
     * 接口：POST /jwglxt/cdjy/cdjy_cxKxcdlb.html?doType=query
     * 条件优先从当前页控件读取（用户在教务页选好校区/楼栋再点读取即可），
     * 读不到就用默认：全部楼栋、不限座位数。
     */
    window.shangkeScanEmptyClassrooms = function () {
        function val(id) {
            var el = document.getElementById(id);
            return el && el.value != null ? String(el.value) : '';
        }
        var xnxq = getXnxq();
        var body = 'xnm=' + encodeURIComponent(xnxq.xnm) +
            '&xqm=' + encodeURIComponent(xnxq.xqm) +
            '&dm=' + encodeURIComponent(xnxq.xnm + '-' + xnxq.xqm) +
            '&xqh_id=' + encodeURIComponent(val('xqh_id') || '1') +
            '&lh=' + encodeURIComponent(val('lh')) +
            '&cdlb_id=' + encodeURIComponent(val('cdlb_id')) +
            '&cdmc=' + encodeURIComponent(val('cdmc')) +
            '&qszws=' + encodeURIComponent(val('qszws')) +
            '&jszws=' + encodeURIComponent(val('jszws')) +
            '&cdejlb_id=&jyfs=2&qssj=&jssj=&sjfw=&qssd=&jssd=' +
            '&_search=false&nd=' + Date.now() +
            '&queryModel.showCount=500&queryModel.currentPage=1' +
            '&queryModel.sortName=&queryModel.sortOrder=asc&time=0';

        return zfPost('/jwglxt/cdjy/cdjy_cxKxcdlb.html?doType=query', body)
            .then(function (data) {
                var items = (data && data.items) || [];
                var rooms = [];
                for (var i = 0; i < items.length; i++) {
                    var item = items[i];
                    var room = String(item.cdmc || '').trim();
                    if (!room) continue;
                    var seats = Number(String(item.zws == null ? '' : item.zws).trim());
                    rooms.push({
                        room: room,
                        campus: String(item.xqmc || '').trim(),
                        // 「无楼号」是正方占位文本，归一成空串，否则会拼进展示串
                        building: String(item.jxlmc || '').trim().replace(/^无楼号$/, ''),
                        capacity: isFinite(seats) ? seats : null,
                        freeSlots: String(item.cdlbmc || '').trim()
                    });
                }
                return rooms;
            });
    };

    /**
     * 学业情况钩子：读培养方案各类别的**要求学分**。
     *
     * 要求学分直接渲染在学业情况页 DOM 里（ul.treeview p.title1），无需额外接口。
     * 「已获学分」一律不回传，由 App 按本机成绩表现算（学校口径与本机必然不一致）。
     *
     * ── 解析规则（在**两所**正方学校的真实页面上核对过，勿凭单校经验改）──────────
     *
     * 正方各校的类别**命名方式并不统一**，实测两例：
     *   南通大学：`通识教育课程平台要求学分:47.0` → 子行 `必修课程要求学分:41.0`
     *   江苏科技大学：`通识教育基础课程-必修要求学分:61.0`（**没有「课程平台」字样**）
     * 因此**不能靠固定关键词识别类别**（按「课程平台」写会在一校产出 0 条）。
     *
     * 通用做法：**只取真叶子行**，并做一次**消歧前缀**——
     *   · 「真叶子」= 该 li 内除自己外没有别的行也带「要求学分」；
     *     这样父行（如江苏科技大学 `外语类` 10 学分）与其子行（`英语` / `日语` 各 10 学分）
     *     不会同时入结果，避免同一份要求被算两遍；
     *   · 消歧：类别名若是 `必修课程` / `选修课程` / `任选课程` / `限选课程` 这类**通用名**，
     *     单独出现毫无区分度（南通大学四个平台各有一条 `必修课程`，不加前缀会合并成一条），
     *     此时用**最近的祖先标签**拼成 `通识教育课程平台/必修`；
     *     而江苏科技大学的 `通识教育基础课程-必修` 本身就自带区分度，保持原样不加前缀。
     *   · 类别名**不做事后归一** —— App 侧按类别名精确匹配成绩的 `category` 统计已获学分
     *     （见 GradeRepository.computeStudyProgress），改名会让已获学分归零。
     *
     * ⚠️ 已知局限（不隐瞒）：江苏科技大学 `外语类` 下 `英语`/`日语` 各 10 学分是
     * 「二选一」，取叶子后两者相加会得到 20 而非 10，**该类别的总量会偏高**。
     * 页面本身没给出「二选一」的结构化表达，无法可靠推断，故照实回传。
     * 用户可在学业情况页手动修正该类别的要求。
     */
    window.shangkeScanStudy = function () {
        // 无区分度的通用类别名：必须在前面缀上所属平台，否则多个平台会合并成一条
        var GENERIC_LABELS = ['必修课程', '选修课程', '任选课程', '限选课程'];
        // 一次拿到**所有**行（含顶层），下面单趟遍历，不做递归下发 ——
        // 递归会与这个总遍历重复访问子行（li.querySelectorAll 返回的是全部后代，
        // 不只是直接子级），导致同一条要求被报多次。
        var rows = document.querySelectorAll('ul.treeview p.title1, ul.treeview .title');
        var requirements = [];

        // 收集某行的「祖先标签」，供通用类别名消歧（沿 li 逐级上溯）
        function ancestorLabels(row) {
            var labels = [];
            var li = row.closest ? row.closest('li') : null;
            while (li) {
                var parentUl = li.parentElement;
                var grandLi = parentUl ? parentUl.closest('li') : null;
                if (!grandLi) break;
                var pr = grandLi.querySelector ? grandLi.querySelector('p.title1, .title') : null;
                if (pr) {
                    var lbl = String(pr.textContent || '').replace(/\s+/g, ' ').trim().replace(/要求学分[:：].*$/, '').trim();
                    if (lbl) labels.unshift(lbl);
                }
                li = grandLi;
            }
            return labels;
        }

        for (var i = 0; i < rows.length; i++) {
            var row = rows[i];
            var text = String(row.textContent || '').replace(/\s+/g, ' ').trim();
            var matched = text.match(/要求学分[:：]([\d.]+)/);
            if (!matched) continue;
            var credits = Number(matched[1]);
            if (!isFinite(credits) || credits <= 0) continue;

            // 父行判定：该 li 的后代里还有别的行带「要求学分」→ 交给那些行报，自己跳过
            var li = row.closest ? row.closest('li') : null;
            if (li) {
                var nested = li.querySelectorAll('ul p.title1');
                var isParent = false;
                for (var k = 0; k < nested.length; k++) {
                    if (nested[k] === row) continue;
                    if (/要求学分/.test(nested[k].textContent || '')) { isParent = true; break; }
                }
                if (isParent) continue;
            }

            var label = text.replace(/要求学分[:：].*$/, '').trim();
            if (!label) continue;

            // 通用名（必修课程/选修课程…）单独无区分度 → 用最近的有意义祖先做前缀
            var finalLabel = label;
            var generic = false;
            for (var g = 0; g < GENERIC_LABELS.length; g++) {
                if (label === GENERIC_LABELS[g]) { generic = true; break; }
            }
            if (generic) {
                var anc = ancestorLabels(row);
                if (anc.length) {
                    finalLabel = anc[anc.length - 1].replace(/课程$/, '') + '/' + label.replace('课程', '');
                }
            }
            // 应修门数（v4.75.0）：子行末尾常带「共（N）门 通过（M）门」，N 即该类别应修门数。
            // 认不出就回传 null（页面只显示已出分门数），**不要猜**。
            // 「通过（M）门」刻意不回传：已修门数一律由本机成绩表现算，混两套口径必然打架。
            var totalMatch = text.match(/共\s*[（(]?\s*(\d+)\s*[）)]?\s*门/);
            var requiredCourses = totalMatch ? Number(totalMatch[1]) : NaN;
            requirements.push({
                category: finalLabel,
                requiredCredits: credits,
                requiredCourses: (isFinite(requiredCourses) && requiredCourses > 0) ? requiredCourses : null
            });
        }
        return { requirements: requirements };
    };

    // 自动检测并提示
    // 注意：三个抓取用途（成绩/空教室/学业）也会注入本文件，它们不是课表页，
    // 因此提示只在课表页给出，避免用户打开成绩页时看到无关的课表提示。
    if (isZhengfangPage() && /\/kbcx\/|\/kbdy\/|xskbcx|bjkbdy/i.test(window.location.href)) {
        Bridge.showToast('检测到正方教务系统，点击导入按钮抓取课表');
    }
})();
