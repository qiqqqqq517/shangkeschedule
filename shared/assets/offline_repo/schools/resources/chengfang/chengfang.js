// 乘方教务系统通用适配器（广州乘方科技）
//
// ================= 平台事实 =================
// 乘方教务在本仓已知**两条产品线**，页面与接口完全不同（均来自已真机实测的专用适配脚本）：
//
//  A. Struts2 老版 —— 湖医药 HBMU、济宁医学院 JNMC
//     学期列表 GET /xsgrkbcx!getXsgrbkList.action         → <select id="xnxqdm">
//     课表数据 GET /xsgrkbcx!getKbRq.action?xnxqdm=&zc=   ← zc 留空 = 全学期，一次拿全
//              返回 [[事件...],[星期→日期...]]，字段：
//                kcmc 课程名 · teaxms 教师 · jxcdmc 地点 · xq 星期(1-7)
//                jcdm2 节次("04,05") · jcdm 节次("0405") · zc 周次
//                jxbmc 教学班 · jxhjmc 教学形式(理论/实验教学/课外实践)
//     兜底     GET /xsgrkbcx!getDataList.action?xnxqdm=&zc=&page=&rows=&sort=kxh&order=asc
//              返回 easyui datagrid {total, rows:[...]}（字段同上，节次用 jcdm）
//              注意：湖医药上这个接口恒返回 total=0（站内另一个查询），故只作兜底、不作首选。
//     作息     GET /default!getCalendar.action 的 ps/pe + qssj/jssj（有则用，无则不用）
//
//  B. 新版 /new/ —— 广州中医药大学 GZUTCM、右江民族医学院 YMUN
//     课表页 /new/student/xsgrkb/main.page（FullCalendar 周视图）。课程块
//     `.fc-time-grid-event` 的 lay-tips 属性内是一张表：课程 / 星期 / 节次 / 上课周次 /
//     授课教师 / 教学场地 / 类型 / 学时 / 上课班级。
//     数据 API POST /new/student/xsgrkb/getCalendarWeekDatas
//              参数 d1,d2（日期窗口）、zc=''、xnxqdm；返回 {code,data:[...]}
//     学期下拉 week.page 内 <select id="xnxqdm">
//
// ================= 本脚本策略 =================
//   B 线优先解析**当前页面已渲染的 FullCalendar 课块**（不依赖接口名），失败再走 B 线 API；
//   A 线由 getKbRq 优先、getDataList 兜底。两条线都失败时给可执行的提示，绝不静默返回空。
//
//   作息时间**不凭空生成**：各校作息本就不同，硬编码一套会静默把课表时间导错。
//   只有教务系统自己给出完整作息（A 线的 /default!getCalendar.action）才保存；
//   拿不到就只按节次导入，并在完成弹窗里提示去「设置 → 自定义时间段」配置
//   （与 JNMC 的既定口径一致）。
//
// ================= 验证状态（诚实标注） =================
//   A 线：用湖医药的真实接口响应（129 条事件，build_qa/hbmu_verify/raw_kbrq.json）离线跑通，
//         双向零差异 + 计数守恒（口径同 build_qa/hbmu_verify/verify_offline.py）。
//   B 线：逻辑逐行取自 GZUTCM / YMUN 两个真机实测脚本，但**通用化后的代码未在真实新版站点上执行过**
//         （仓内没有新版站点的接口夹具）——首次遇到新版站点时需要按 docs/adapter-sop.md 复核。
//
// 桥接契约：注入器自动调用 window.shangkeImportEntry()（见 WebBridgeProtocol.JS_IMPORT_AUTOSTART）。

(function () {
    'use strict';

    // ---------- 平台路径 ----------
    var LEGACY_SEMESTER_PAGE = '/xsgrkbcx!getXsgrbkList.action';
    var LEGACY_KB_ALL = '/xsgrkbcx!getKbRq.action';
    var LEGACY_KB_PAGED = '/xsgrkbcx!getDataList.action';
    var LEGACY_CALENDAR = '/default!getCalendar.action';
    var LEGACY_PAGE_ROWS = 200;
    var LEGACY_MAX_PAGES = 8;

    var NEW_WEEK_PAGE = '/new/student/xsgrkb/week.page';
    var NEW_KB_API = '/new/student/xsgrkb/getCalendarWeekDatas';

    var MAX_WEEK = 30;
    var MAX_SECTION = 30;

    // ---------- Bridge ----------
    function toast(msg) {
        try {
            if (window.shangkeBridge && typeof window.shangkeBridge.showToast === 'function') {
                window.shangkeBridge.showToast(msg);
            }
        } catch (e) { /* 提示失败不影响主流程 */ }
    }

    function alert(title, msg, btn) {
        return window.shangkeBridgePromise.showAlert(title, msg, btn);
    }

    function select(title, labels, defaultIndex) {
        return window.shangkeBridgePromise.showSingleSelection(title, JSON.stringify(labels), defaultIndex);
    }

    function finish() {
        try {
            if (window.shangkeBridge && typeof window.shangkeBridge.notifyTaskCompletion === 'function') {
                window.shangkeBridge.notifyTaskCompletion();
            }
        } catch (e) { /* 收尾失败不影响已落库的数据 */ }
    }

    // ---------- 网络（同源，credentials 带教务登录态） ----------
    function parseJson(text, hint) {
        try {
            return JSON.parse(text);
        } catch (e) {
            throw new Error(hint || '教务系统未返回有效数据，登录可能已失效，请重新登录后重试。');
        }
    }

    function httpGetText(path, params) {
        var url = path;
        if (params) {
            var qs = [];
            for (var k in params) {
                if (Object.prototype.hasOwnProperty.call(params, k)) {
                    qs.push(encodeURIComponent(k) + '=' + encodeURIComponent(params[k]));
                }
            }
            if (qs.length) url += '?' + qs.join('&');
        }
        return fetch(url, {
            method: 'GET',
            credentials: 'include',
            headers: { 'X-Requested-With': 'XMLHttpRequest' }
        }).then(function (r) {
            if (!r.ok) throw new Error('教务系统返回 HTTP ' + r.status + '（' + url + '）');
            return r.text();
        });
    }

    function httpPostForm(path, body) {
        return fetch(path, {
            method: 'POST',
            credentials: 'include',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: body
        }).then(function (r) {
            if (!r.ok) throw new Error('教务系统返回 HTTP ' + r.status + '（' + path + '）');
            return r.text();
        });
    }

    // ---------- 文本清理 ----------
    function text(v) {
        return String(v === null || v === undefined ? '' : v)
            .replace(/\u00a0/g, ' ')
            .replace(/\s+/g, ' ')
            .trim();
    }

    function cleanCourseName(v) {
        return text(v).replace(/【[^】]*】/g, '').replace(/[■◆▲●★]/g, '').replace(/\s+/g, ' ').trim();
    }

    function cleanTeacher(v) {
        var s = text(v).replace(/（[^）]*）/g, '').replace(/\([^)]*\)/g, '').replace(/\s+/g, ' ').trim();
        return s || '未安排';
    }

    function pickField(row, candidates) {
        for (var i = 0; i < candidates.length; i++) {
            var v = row[candidates[i]];
            if (v !== undefined && v !== null && v !== '') return v;
        }
        return '';
    }

    // ---------- 学期下拉（两条产品线的页面结构一致：select#xnxqdm） ----------
    function parseSemesters(html) {
        var block = String(html || '').match(/<select[^>]*id=['"]xnxqdm['"][\s\S]*?<\/select>/i);
        if (!block) return null;

        var optionRe = /<option[^>]*value=['"]([^'"]*)['"]([^>]*)>([\s\S]*?)<\/option>/gi;
        var labels = [], values = [], defaultIndex = 0, m;
        while ((m = optionRe.exec(block[0])) !== null) {
            var value = m[1];
            var attrs = m[2] || '';
            var label = m[3].replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim();
            if (!value || !label) continue;
            if (/\bselected\b/i.test(attrs)) defaultIndex = labels.length;
            labels.push(label);
            values.push(value);
        }
        if (!labels.length) return null;
        return { labels: labels, values: values, defaultIndex: defaultIndex };
    }

    // 登录页判定：乘方两条线的登录页都带 login-form / 账号密码输入框
    function looksLikeLoginPage(html) {
        var s = String(html || '');
        return s.indexOf('login-form') !== -1
            || s.indexOf('name="account"') !== -1
            || s.indexOf('name="pwd"') !== -1
            || (s.indexOf('<title>') !== -1 && s.indexOf('xnxqdm') === -1 && s.length < 4000);
    }

    // ---------- 单条记录 → 节次 / 星期 / 周次 ----------
    // 节次：显式字段 → jcdm2("04,05") → jcdm("0405") / "1-2" / "第1-2节" / 单节
    function parseSections(row) {
        var s = pickField(row, ['startSection', 'start_section', 'startJc', 'djj', 'ksjc', 'beginSection']);
        var e = pickField(row, ['endSection', 'end_section', 'endJc', 'jsjc', 'finishSection']);
        var nums = [];

        if (s) {
            var start = parseInt(s, 10);
            var end = e ? parseInt(e, 10) : start;
            var cs = pickField(row, ['cs', 'sectionCount', 'count']);
            if (!e && cs) end = start + parseInt(cs, 10) - 1;
            if (!isNaN(start) && !isNaN(end) && start >= 1 && end >= start && end <= MAX_SECTION) {
                return { start: start, end: end };
            }
        }

        // "04,05" / "04，05"
        var jcdm2 = pickField(row, ['jcdm2']);
        if (jcdm2) {
            var parts = String(jcdm2).split(/[,，、]/);
            for (var i = 0; i < parts.length; i++) {
                var n = parseInt(parts[i], 10);
                if (!isNaN(n)) nums.push(n);
            }
        }

        if (!nums.length) {
            var raw = pickField(row, ['jcdm', 'jcs', 'jc', 'section', 'sections']);
            var str = text(raw);
            if (str) {
                if (/^\d+$/.test(str) && str.length >= 2 && str.length % 2 === 0) {
                    // 纯数字连续两位一节："0405" / "0607080910"
                    for (var j = 0; j + 1 < str.length; j += 2) nums.push(parseInt(str.substr(j, 2), 10));
                } else {
                    var range = str.match(/(\d+)\s*[-~－—至到]\s*(\d+)/);
                    if (range) {
                        nums.push(parseInt(range[1], 10), parseInt(range[2], 10));
                    } else {
                        var single = str.match(/(\d+)/);
                        if (single) nums.push(parseInt(single[1], 10));
                    }
                }
            }
        }

        nums = nums.filter(function (n) { return !isNaN(n) && n >= 1 && n <= MAX_SECTION; });
        if (!nums.length) return null;
        nums.sort(function (a, b) { return a - b; });
        // 教务系统的节次是连续段，取首尾即起止节
        return { start: nums[0], end: nums[nums.length - 1] };
    }

    // 星期：xq / xqj / weekday 直接是 1-7（1=周一）；qsxq 是乘方日历接口的「1=周日」口径
    function parseDay(row) {
        var d = pickField(row, ['xq', 'xqj', 'weekday', 'weekDay', 'dayOfWeek', 'day']);
        if (d !== '') {
            var n = parseInt(d, 10);
            if (!isNaN(n) && n >= 1 && n <= 7) return n;
        }
        var q = pickField(row, ['qsxq']);
        if (q !== '') {
            var raw = parseInt(q, 10);
            if (!isNaN(raw)) {
                var day = raw - 1;
                return day < 1 ? 7 : day;
            }
        }
        return null;
    }

    // 周次：老版的 zc 是单周；新版可能是单周、区间或列表
    function parseWeeks(row) {
        var raw = pickField(row, ['zc', 'zcd', 'weeks', 'weekList', 'week', 'weekDescription', 'weekText']);
        if (raw === '') return [];
        if (Object.prototype.toString.call(raw) === '[object Array]') {
            var arr = [];
            for (var i = 0; i < raw.length; i++) {
                var n = parseInt(raw[i], 10);
                if (!isNaN(n) && n >= 1 && n <= MAX_WEEK) arr.push(n);
            }
            return sortUnique(arr);
        }
        var str = text(raw);
        if (/^\d+$/.test(str)) {
            var single = parseInt(str, 10);
            return (single >= 1 && single <= MAX_WEEK) ? [single] : [];
        }
        var out = [];
        var rangeRe = /(\d+)\s*[-~－—至到]\s*(\d+)/g, m;
        while ((m = rangeRe.exec(str)) !== null) {
            var st = parseInt(m[1], 10), en = parseInt(m[2], 10);
            if (st >= 1 && en <= MAX_WEEK && st <= en) {
                for (var w = st; w <= en; w++) out.push(w);
            }
        }
        if (!out.length) {
            var singleRe = /(\d+)/g;
            while ((m = singleRe.exec(str)) !== null) {
                var v = parseInt(m[1], 10);
                if (v >= 1 && v <= MAX_WEEK) out.push(v);
            }
        }
        return sortUnique(out);
    }

    function sortUnique(arr) {
        var seen = {}, out = [];
        for (var i = 0; i < arr.length; i++) {
            if (!seen[arr[i]]) { seen[arr[i]] = true; out.push(arr[i]); }
        }
        out.sort(function (a, b) { return a - b; });
        return out;
    }

    // 地点：jxcdmc 优先；只有 jxcdmc2（"地点-周次,地点-周次"）时按地点拆
    function positionsOf(row) {
        var direct = text(pickField(row, ['jxcdmc', 'cdmc', 'position', 'location', 'room', 'classroom', 'jsmc']));
        if (direct) return [{ position: direct, weeks: null }];

        var packed = text(pickField(row, ['jxcdmc2']));
        if (!packed) return [{ position: '待定', weeks: null }];

        var parts = packed.split(',');
        var map = {}, order = [], last = '';
        for (var i = 0; i < parts.length; i++) {
            var part = parts[i].trim();
            if (!part) continue;
            var dash = part.lastIndexOf('-');
            if (dash === -1) continue;
            var week = parseInt(part.substring(dash + 1).trim(), 10);
            if (isNaN(week) || week < 1 || week > MAX_WEEK) continue;
            var loc = part.substring(0, dash).trim() || last;
            if (!loc) continue;
            last = loc;
            if (!map[loc]) { map[loc] = []; order.push(loc); }
            map[loc].push(week);
        }
        if (!order.length) return [{ position: direct || '待定', weeks: null }];
        return order.map(function (loc) { return { position: loc, weeks: sortUnique(map[loc]) }; });
    }

    // ---------- 记录 → 课程（按 课程|教师|地点|星期|起止节|教学形式 合并周次） ----------
    // 分组键含「教学形式」：同一课位的理论课与实验课不混成一条（JNMC 既定口径）。
    // 周次互不重叠的同一课位（如按周轮换讲师）会各自成条，保证「第几周是哪位老师」不丢。
    function buildCourses(records) {
        var order = [], byKey = {}, skipped = 0;

        for (var i = 0; i < records.length; i++) {
            var row = records[i];
            if (!row) continue;

            var name = cleanCourseName(row.kcmc);
            if (!name) { skipped++; continue; }

            var day = parseDay(row);
            if (!day) { skipped++; continue; }

            var sec = parseSections(row);
            if (!sec) { skipped++; continue; }

            var weeks = parseWeeks(row);
            if (!weeks.length) { skipped++; continue; }

            var teacher = cleanTeacher(row.teaxms);
            var type = text(row.jxhjmc);
            var jxbmc = text(row.jxbmc);
            var positions = positionsOf(row);

            for (var p = 0; p < positions.length; p++) {
                var position = positions[p].position || '待定';
                var posWeeks = positions[p].weeks || weeks;
                var key = [name, teacher, position, day, sec.start, sec.end, type].join('\u0001');
                var item = byKey[key];
                if (!item) {
                    var remarkParts = [];
                    if (type) remarkParts.push('课程类型：' + type);
                    if (jxbmc) remarkParts.push('教学班：' + jxbmc);
                    item = byKey[key] = {
                        name: name,
                        teacher: teacher,
                        position: position,
                        day: day,
                        startSection: sec.start,
                        endSection: sec.end,
                        weeks: [],
                        remark: remarkParts.join('；'),
                        isLab: type === '实验教学'
                    };
                    order.push(item);
                }
                item.weeks = item.weeks.concat(posWeeks);
            }
        }

        for (var k = 0; k < order.length; k++) {
            order[k].weeks = sortUnique(order[k].weeks);
        }
        return { courses: order, skipped: skipped };
    }

    // ---------- A 线：Struts2 老版 ----------
    // getKbRq 一次拿全学期；拿不到再按 easyui datagrid 分页兜底
    function fetchLegacyAll(xnxqdm) {
        return httpGetText(LEGACY_KB_ALL, { xnxqdm: xnxqdm, zc: '' }).then(function (raw) {
            var json = parseJson(raw);
            if (json && json[0] && json[0].length) return json[0];
            return fetchLegacyPaged(xnxqdm, 1, []);
        });
    }

    function fetchLegacyPaged(xnxqdm, page, acc) {
        var params = { xnxqdm: xnxqdm, zc: '', page: page, rows: LEGACY_PAGE_ROWS, sort: 'kxh', order: 'asc' };
        return httpGetText(LEGACY_KB_PAGED, params).then(function (raw) {
            var json = parseJson(raw);
            var rows = (json && json.rows) ? json.rows : null;
            if (!rows) return acc;                       // 该接口不存在/无数据：直接交回已拿到的
            var all = acc.concat(rows);
            var total = parseInt(json.total, 10);
            if (rows.length === 0 || page >= LEGACY_MAX_PAGES
                || (!isNaN(total) && all.length >= total)) {
                return all;
            }
            return fetchLegacyPaged(xnxqdm, page + 1, all);
        });
    }

    // 作息：只有教务系统自己给出完整作息时才用（ps 节次 + qssj/jssj 起止时间）
    function fetchLegacyTimeSlots() {
        return httpGetText(LEGACY_CALENDAR).then(function (raw) {
            var json = parseJson(raw);
            var list = [];
            if (json && Object.prototype.toString.call(json) === '[object Array]') list = json;
            else if (json && Object.prototype.toString.call(json.ps) === '[object Array]') list = json.ps;
            var slots = [];
            for (var i = 0; i < list.length; i++) {
                var it = list[i];
                if (!it) continue;
                var number = parseInt(pickField(it, ['ps', 'number', 'jc', 'section']), 10);
                var startTime = text(pickField(it, ['qssj', 'startTime', 'start']));
                var endTime = text(pickField(it, ['jssj', 'endTime', 'end']));
                if (isNaN(number) || number < 1 || number > MAX_SECTION) continue;
                if (!/^\d{1,2}:\d{2}$/.test(startTime) || !/^\d{1,2}:\d{2}$/.test(endTime)) continue;
                slots.push({ number: number, startTime: startTime, endTime: endTime });
            }
            slots.sort(function (a, b) { return a.number - b.number; });
            // 少于 5 节或节次不连续一律视为「没拿到完整作息」，宁可不存也不要存错
            if (slots.length < 5) return null;
            for (var k = 0; k < slots.length; k++) {
                if (slots[k].number !== k + 1) return null;
            }
            return slots;
        }).catch(function () { return null; });
    }

    function runLegacy(semesterHtml) {
        var sem = parseSemesters(semesterHtml);
        if (!sem) {
            throw new Error('未能读取学期列表。请确认已登录教务系统，并打开「信息查询 → 学生个人课表」页面后重试。');
        }
        var semesterLabel = '';
        return select('选择学期', sem.labels, sem.defaultIndex).then(function (idx) {
            if (idx === null || idx === undefined || idx < 0 || idx >= sem.values.length) {
                toast('导入已取消');
                return null;
            }
            semesterLabel = sem.labels[idx];
            toast('正在获取「' + semesterLabel + '」课表...');
            return fetchLegacyAll(sem.values[idx]);
        }).then(function (records) {
            if (!records) return null;
            var built = buildCourses(records);
            if (!built.courses.length) {
                throw new Error('「' + semesterLabel + '」未查询到课程数据。'
                    + '请确认该学期确实有课，或改选其它学期后重试。');
            }
            return fetchLegacyTimeSlots().then(function (slots) {
                return saveAndReport(built.courses, semesterLabel, slots, built.skipped);
            });
        });
    }

    // ---------- B 线：新版 /new/ ----------
    // 优先解析当前页面已渲染的 FullCalendar 课块（不依赖接口名）
    function parseLayTips(html) {
        var doc = new DOMParser().parseFromString(html, 'text/html');
        var rows = doc.querySelectorAll('tr');
        var data = {}, dataHtml = {};
        for (var i = 0; i < rows.length; i++) {
            var ths = rows[i].querySelectorAll('th');
            var tds = rows[i].querySelectorAll('td');
            for (var j = 0; j < ths.length && j < tds.length; j++) {
                var key = text(ths[j].textContent).replace(/[：:]$/, '');
                if (!key) continue;
                data[key] = text(tds[j].textContent);
                dataHtml[key] = tds[j].innerHTML;
            }
        }

        var name = text(data['课程']).replace(/^\[[^\]]*\]/, '').trim();
        if (!name) return null;

        var day = parseInt(data['星期'], 10);
        if (isNaN(day) || day < 1 || day > 7) return null;

        var sec = parseSections({ jc: data['节次'] });
        if (!sec) return null;

        var weeks = parseWeeks({ zc: text(data['上课周次']).replace(/周/g, ',') });
        if (!weeks.length) return null;

        var position = '待定';
        var locationHtml = dataHtml['教学场地'] || '';
        if (locationHtml) {
            var firstLoc = String(locationHtml).split(/<br\s*\/?>/i)[0]
                .replace(/<[^>]+>/g, '').replace(/\s*\[[^\]]*\]\s*$/g, '').trim();
            if (firstLoc) position = firstLoc;
        }

        var typeRaw = text(data['类型']);
        var typeMatch = typeRaw.match(/\[([^\]]+)\]/);
        var type = typeMatch ? typeMatch[1] : '';

        var remarkParts = [];
        if (type) remarkParts.push('课程类型：' + type);
        if (text(data['学时'])) remarkParts.push('学时：' + text(data['学时']));
        if (text(data['上课班级'])) remarkParts.push('教学班：' + text(data['上课班级']));

        return {
            name: name,
            teacher: cleanTeacher(data['授课教师']),
            position: position,
            day: day,
            startSection: sec.start,
            endSection: sec.end,
            weeks: weeks,
            remark: remarkParts.join('；'),
            isLab: type === '教学实验' || type === '实验教学' || type === '实验'
        };
    }

    // 遍历当前文档与所有同源 iframe，收集 FullCalendar 课块
    function parseFromFullCalendar() {
        var docs = [document];
        function collect(doc) {
            try {
                var iframes = doc.querySelectorAll('iframe');
                for (var i = 0; i < iframes.length; i++) {
                    try {
                        var d = iframes[i].contentDocument || (iframes[i].contentWindow && iframes[i].contentWindow.document);
                        if (d) { docs.push(d); collect(d); }
                    } catch (e) { /* 跨域忽略 */ }
                }
            } catch (e) { /* 无 iframe 能力时忽略 */ }
        }
        collect(document);

        var courses = [], seen = {};
        for (var di = 0; di < docs.length; di++) {
            var events;
            try {
                events = docs[di].querySelectorAll('.fc-time-grid-event');
            } catch (e) {
                continue;
            }
            for (var i = 0; i < events.length; i++) {
                var layTips = events[i].getAttribute('lay-tips');
                if (!layTips) continue;
                var course = null;
                try {
                    course = parseLayTips(layTips);
                } catch (e) {
                    course = null;
                }
                if (!course) continue;
                var key = [course.name, course.day, course.startSection, course.endSection, course.weeks.join(',')].join('\u0001');
                if (!seen[key]) { seen[key] = true; courses.push(course); }
            }
        }
        return courses;
    }

    // 页面（或接口）里若带 businessHours 形态的作息，取来当节次表用；取不到就返回 null（不猜）
    function parseSectionTimesFromHtml(html) {
        var times = [];
        var re = /startTime\s*:\s*['"](\d{1,2}:\d{2})['"]/g, m;
        while ((m = re.exec(String(html || ''))) !== null) {
            if (times.indexOf(m[1]) === -1) times.push(m[1]);
        }
        if (times.length < 5) return null;
        times.sort(function (a, b) { return minutes(a) - minutes(b); });
        var table = [];
        for (var i = 0; i < times.length; i++) table.push({ sec: i + 1, time: times[i] });
        return table;
    }

    function minutes(t) {
        var p = String(t).split(':');
        return parseInt(p[0], 10) * 60 + parseInt(p[1], 10);
    }

    function sectionByTime(table, timeStr) {
        var t = text(timeStr).substring(0, 5);
        if (!/^\d{1,2}:\d{2}$/.test(t)) return null;
        var result = null;
        for (var i = 0; i < table.length; i++) {
            if (minutes(table[i].time) <= minutes(t)) result = table[i].sec;
        }
        return result;
    }

    // 新版记录：节次优先取显式字段；只剩「开始/结束时间」时必须先拿到该校作息表，否则不猜
    function newRecordToCourses(row, sectionTable) {
        var name = cleanCourseName(row.kcmc);
        if (!name) return [];

        var day = parseDay(row);
        if (!day) return [];

        var sec = parseSections(row);
        if (!sec && sectionTable) {
            var start = sectionByTime(sectionTable, pickField(row, ['qssj', 'startTime', 'start']));
            var end = sectionByTime(sectionTable, pickField(row, ['jssj', 'endTime', 'end']));
            if (start && end) sec = { start: Math.min(start, end), end: Math.max(start, end) };
        }
        if (!sec) return [];

        var weeks = parseWeeks(row);
        if (!weeks.length) return [];

        var teacher = cleanTeacher(row.teaxms);
        var type = text(row.jxhjmc);
        var positions = positionsOf(row);

        var out = [];
        for (var p = 0; p < positions.length; p++) {
            out.push({
                name: name,
                teacher: teacher,
                position: positions[p].position || '待定',
                day: day,
                startSection: sec.start,
                endSection: sec.end,
                weeks: sortUnique(positions[p].weeks || weeks),
                remark: type ? '课程类型：' + type : '',
                isLab: type === '实验教学'
            });
        }
        return out;
    }

    function fetchNewViaApi(weekPageHtml) {
        var sectionTable = parseSectionTimesFromHtml(weekPageHtml);
        var sem = parseSemesters(weekPageHtml);

        function askSemester() {
            if (!sem) return Promise.resolve(null);
            return select('选择学期', sem.labels, sem.defaultIndex).then(function (idx) {
                if (idx === null || idx === undefined || idx < 0 || idx >= sem.values.length) {
                    toast('导入已取消');
                    return null;
                }
                return { label: sem.labels[idx], value: sem.values[idx] };
            });
        }

        return askSemester().then(function (picked) {
            if (!picked) return null;
            var year = parseInt(String(picked.value).substring(0, 4), 10);
            if (isNaN(year)) throw new Error('学期代码无法解析：' + picked.value);
            // 学期代码前 4 位是学年起始年：第 2 学期会跨到次年，故用整个学年窗口查询，
            // 由服务端按 xnxqdm 过滤（YMUN / CMC 的既定口径）。
            var body = 'd1=' + encodeURIComponent(year + '-08-01 00:00:00')
                + '&d2=' + encodeURIComponent((year + 1) + '-08-31 23:59:59')
                + '&zc=' + encodeURIComponent('')
                + '&xnxqdm=' + encodeURIComponent(picked.value);
            return httpPostForm(NEW_KB_API, body).then(function (raw) {
                var json = parseJson(raw, '课表接口未返回有效数据（可能不是乘方新版系统）。');
                if (json && typeof json.code === 'number' && json.code < 0) {
                    throw new Error(json.message || ('课表接口返回错误 code=' + json.code));
                }
                var items = (json && json.data) || [];
                var courses = [];
                for (var i = 0; i < items.length; i++) {
                    courses = courses.concat(newRecordToCourses(items[i], sectionTable));
                }
                if (!courses.length) {
                    throw new Error('「' + picked.label + '」未解析出课程。'
                        + (sectionTable ? '' : '该系统未提供节次字段，也没能从页面读到作息表。')
                        + '请确认已登录并打开课表页后重试。');
                }
                return { label: picked.label, courses: courses };
            });
        });
    }

    function runNewGen() {
        // 1. 页面已渲染的 FullCalendar 课块：优先，且不需要选学期
        var rendered = [];
        try {
            rendered = parseFromFullCalendar();
        } catch (e) {
            rendered = [];
        }
        if (rendered.length) {
            return saveAndReport(rendered, '', null, 0);
        }

        // 2. 未渲染课表：读 week.page 拿学期与作息，再调课表接口
        toast('正在读取课表页面...');
        return httpGetText(NEW_WEEK_PAGE).then(function (html) {
            if (looksLikeLoginPage(html)) {
                throw new Error('尚未登录教务系统。请先在本页面完成登录，进入课表查询页后再点「执行导入」。');
            }
            return fetchNewViaApi(html);
        }).then(function (result) {
            if (!result) return null;
            return saveAndReport(result.courses, result.label, null, 0);
        });
    }

    // ---------- 保存与收尾 ----------
    function saveAndReport(courses, semesterLabel, slots, skipped) {
        var where = semesterLabel ? '「' + semesterLabel + '」' : '当前页面';
        return window.shangkeBridgePromise.saveImportedCourses(JSON.stringify(courses))
            .then(function () {
                if (!slots || !slots.length) return null;
                // 作息保存失败不阻断导入，仅提示
                return window.shangkeBridgePromise.savePresetTimeSlots(JSON.stringify(slots))
                    .catch(function (e) {
                        toast('作息时间保存失败：' + ((e && e.message) || e));
                    });
            })
            .then(function () {
                var lines = ['已从 ' + where + ' 导入 ' + courses.length + ' 门课程。', ''];
                if (!slots || !slots.length) {
                    lines.push('说明：未从教务系统读到完整作息，课程按节次导入；');
                    lines.push('如上下课时间与学校作息不符，请到「设置 → 自定义时间段」调整。');
                } else {
                    lines.push('说明：作息时间也已按教务系统设置同步。');
                }
                if (skipped > 0) {
                    lines.push('');
                    lines.push('另有 ' + skipped + ' 条记录因缺少课程名/星期/节次/周次被跳过。');
                }
                return alert('导入完成', lines.join('\n'), '完成');
            })
            .then(function () {
                finish();
            });
    }

    // ---------- 入口 ----------
    function looksLikeNewGenPage() {
        var path = location.pathname || '';
        if (path.indexOf('/student/xsgrkb') !== -1 || path.indexOf('/xsgrkb/week.page') !== -1) return true;
        try {
            return !!document.querySelector('.fc-time-grid-event');
        } catch (e) {
            return false;
        }
    }

    function runImport() {
        toast('正在检测登录状态...');

        if (looksLikeNewGenPage()) {
            return runNewGen().catch(showFailure);
        }

        // 默认按老版（Struts2）探测：能读到学期下拉就是老版；读不到再看是不是新版页面
        return httpGetText(LEGACY_SEMESTER_PAGE).then(function (html) {
            if (parseSemesters(html)) return runLegacy(html);
            if ((location.pathname || '').indexOf('/new/') !== -1) return runNewGen();
            throw new Error('未能在本页识别乘方教务系统。'
                + '请先登录教务系统，打开「学生个人课表」页（老版）或课表查询页（新版）后重试。');
        }).catch(showFailure);
    }

    function showFailure(err) {
        return alert('导入失败', (err && err.message) || String(err), '确定');
    }

    // 注入器（JS_IMPORT_AUTOSTART）会调用 window.shangkeImportEntry
    if (typeof window !== 'undefined') {
        window.shangkeImportEntry = runImport;
    }
})();
