// 湖北医药学院（湖医药）教务系统适配器 v2
// 目标系统：广州乘方科技 教务管理系统 · https://jw.hbmu.edu.cn/
//
// ---------- 系统特征（2026-10-03 实测） ----------
//   登录页：layui + 图形验证码；登录 POST /new/login（AES-ECB，密钥 = 验证码 × 4，PKCS7）
//   学期列表：/xsgrkbcx!getXsgrbkList.action 内的 <select id="xnxqdm">（value=202601 等，含 selected）
//   课表数据：GET /xsgrkbcx!getKbRq.action?xnxqdm=<学期代码>&zc=<周次>
//     · zc 留空 = 全学期。实测 129 条事件覆盖第 5-17 周，且与逐周（zc=1..20）查询
//       逐周条数完全一致（0 处不符），故一次请求即可拿全，无需按周循环。
//     · 返回 [[事件...], [星期→日期...]]。事件字段：
//         kcmc 课程名 · teaxms 教师 · jxcdmc 地点 · xq 星期(1-7)
//         jcdm2 节次("04,05") · jcdm 节次("0405") · zc 周次
//         jxbmc 教学班 · jxhjmc 教学形式(理论 / 实验教学 / 课外实践) · kcdm / kcbh 课程号
//   作息：第1-5节 08:00 08:50 09:40 10:30 11:20 · 第6-9节 14:30 15:20 16:10 17:00 ·
//         第10-12节 19:00 19:50 20:40（由 /default!getCalendar.action 的 ps/pe + qssj/jssj
//         反推，并与站内课表节次一致）
//
// ---------- 与 v1 的差异（重要） ----------
//   v1 请求 /xsgrkbcx!getDataList.action，实测恒返回 {"total":0,"rows":[]}（那是另一个查询），
//   导入必然 0 门课；且 v1 让用户手填学年 + 学期，实际学期代码应取自教务系统的学期下拉。
//   v2 改为「读学期下拉 → 选学期 → 调 getKbRq（一次拿全学期）」，并以节次/周次为准落库。
//
// 桥接契约：注入器自动调用 window.shangkeImportEntry()（见 WebBridgeProtocol.JS_IMPORT_AUTOSTART）。
// 能力钩子：window.shangkeScanGrades() 回传成绩与绩点（契约见 ADAPTER_GUIDE「能力钩子」）；
// 空教室 / 学业情况本校无可信数据源，不声明钩子。

(function () {
    'use strict';

    var SEMESTER_PAGE = '/xsgrkbcx!getXsgrbkList.action';
    var KB_API = '/xsgrkbcx!getKbRq.action';

    // 成绩查询：easyui datagrid 分页接口（同乘方老版课表 getDataList 的返回形态）
    var GRADE_API = '/xskccjxx!getDataList.action';
    var GRADE_PAGE_ROWS = 200;
    var GRADE_MAX_PAGES = 10;

    // 湖北医药学院作息（节次 → 起止时间）
    var TIME_SLOTS = [
        { number: 1, startTime: '08:00', endTime: '08:40' },
        { number: 2, startTime: '08:50', endTime: '09:30' },
        { number: 3, startTime: '09:40', endTime: '10:20' },
        { number: 4, startTime: '10:30', endTime: '11:10' },
        { number: 5, startTime: '11:20', endTime: '12:00' },
        { number: 6, startTime: '14:30', endTime: '15:10' },
        { number: 7, startTime: '15:20', endTime: '16:00' },
        { number: 8, startTime: '16:10', endTime: '16:50' },
        { number: 9, startTime: '17:00', endTime: '17:40' },
        { number: 10, startTime: '19:00', endTime: '19:40' },
        { number: 11, startTime: '19:50', endTime: '20:30' },
        { number: 12, startTime: '20:40', endTime: '21:20' }
    ];

    // ---------- Bridge 包装 ----------
    function toast(msg) {
        try {
            if (window.shangkeBridge && typeof window.shangkeBridge.showToast === 'function') {
                window.shangkeBridge.showToast(msg);
            }
        } catch (e) { /* 提示失败不影响主流程 */ }
    }

    function showAlert(title, msg, btn) {
        return window.shangkeBridgePromise.showAlert(title, msg, btn);
    }

    function finish() {
        try {
            if (window.shangkeBridge && typeof window.shangkeBridge.notifyTaskCompletion === 'function') {
                window.shangkeBridge.notifyTaskCompletion();
            }
        } catch (e) { /* 收尾失败不影响已落库的数据 */ }
    }

    // ---------- 网络 ----------
    // 同源请求，credentials:'include' 携带教务系统登录态
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
            if (!r.ok) throw new Error('教务系统返回 HTTP ' + r.status);
            return r.text();
        });
    }

    // ---------- 登录态与学期列表 ----------
    function looksLikeLoginPage(html) {
        return html.indexOf('login-form') !== -1 || html.indexOf('name="account"') !== -1;
    }

    // 解析 <select id="xnxqdm"> 得到学期列表与当前选中项
    function parseSemesters(html) {
        var blockMatch = html.match(/<select[^>]*id=['"]xnxqdm['"][\s\S]*?<\/select>/i);
        if (!blockMatch) return null;

        var optionRe = /<option[^>]*value=['"]([^'"]*)['"]([^>]*)>([\s\S]*?)<\/option>/gi;
        var labels = [], values = [], defaultIndex = 0, m;
        while ((m = optionRe.exec(blockMatch[0])) !== null) {
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

    // ---------- 课表数据 ----------
    // zc 传空串 = 全学期
    function fetchEvents(xnxqdm) {
        return httpGetText(KB_API, { xnxqdm: xnxqdm, zc: '' }).then(function (text) {
            var json;
            try {
                json = JSON.parse(text);
            } catch (e) {
                throw new Error('课表接口未返回有效数据，登录可能已失效，请重新登录后重试。');
            }
            if (!json || !json[0]) return [];
            return json[0];
        });
    }

    // 节次：优先 jcdm2("04,05")，回退 jcdm("0405") 每两位一节
    function parseSections(ev) {
        var nums = [];
        var i;

        if (ev.jcdm2) {
            var parts = String(ev.jcdm2).split(/[,，、]/);
            for (i = 0; i < parts.length; i++) {
                var n = parseInt(parts[i], 10);
                if (!isNaN(n)) nums.push(n);
            }
        }
        if (!nums.length && ev.jcdm) {
            var digits = String(ev.jcdm).replace(/\D/g, '');
            for (i = 0; i + 1 < digits.length; i += 2) {
                nums.push(parseInt(digits.substr(i, 2), 10));
            }
            if (!nums.length && digits) nums.push(parseInt(digits, 10));
        }

        nums = nums.filter(function (n) { return !isNaN(n) && n >= 1 && n <= 30; });
        if (!nums.length) return null;
        nums.sort(function (a, b) { return a - b; });
        // 教务系统的节次是连续段（实测 jcdm2 全部为连续值），取首尾即起止节
        return { start: nums[0], end: nums[nums.length - 1] };
    }

    function cleanCourseName(v) {
        return String(v === null || v === undefined ? '' : v)
            .replace(/【[^】]*】/g, '')
            .replace(/[■◆▲●★]/g, '')
            .replace(/\s+/g, ' ')
            .trim();
    }

    function cleanTeacher(v) {
        var s = String(v === null || v === undefined ? '' : v)
            .replace(/（[^）]*）/g, '')
            .replace(/\([^)]*\)/g, '')
            .replace(/\s+/g, ' ')
            .trim();
        return s || '未安排';
    }

    // 事件列表 → 课程列表（同 课程/教师/地点/星期/节次 的行合并周次）
    //
    // 分组键含「教师 / 地点」是本仓既定口径（同 HEBMU / JNMC / NIIT 的
    // 「课程|教师|场地|星期|节次」）。湖医药常见「同一课位按周轮换讲师」
    // （实测系统解剖学 周一第1-2节 10 周换了 10 位老师）—— 按 DLUT 的既定判据
    // 「合授（同周次多教师）合并为一条，代课（不同周次不同教师）按周次拆分」，
    // 此处同样按周次拆开，保证「第几周是哪位老师」不丢信息。
    // 同格的其它老师条目周次互不重叠，周视图/今日视图同一时刻只会显示当周那一条。
    function buildCourses(events) {
        var order = [], byKey = {};

        for (var i = 0; i < events.length; i++) {
            var ev = events[i];
            if (!ev) continue;

            var name = cleanCourseName(ev.kcmc);
            if (!name) continue;

            var day = parseInt(ev.xq, 10);
            if (isNaN(day) || day < 1 || day > 7) continue;

            var sec = parseSections(ev);
            if (!sec) continue;

            var week = parseInt(ev.zc, 10);
            if (isNaN(week) || week < 1 || week > 30) continue;

            var teacher = cleanTeacher(ev.teaxms);
            var position = String(ev.jxcdmc === null || ev.jxcdmc === undefined ? '' : ev.jxcdmc).trim() || '待定';
            var type = String(ev.jxhjmc === null || ev.jxhjmc === undefined ? '' : ev.jxhjmc).trim();

            var key = [name, teacher, position, day, sec.start, sec.end].join('\u0001');
            var item = byKey[key];
            if (!item) {
                var remarkParts = [];
                if (type) remarkParts.push('课程类型：' + type);
                var jxbmc = String(ev.jxbmc === null || ev.jxbmc === undefined ? '' : ev.jxbmc).trim();
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
            item.weeks.push(week);
        }

        // 周次去重并升序（合同约定 weeks 必须升序）
        for (var k = 0; k < order.length; k++) {
            var seen = {}, weeks = [], arr = order[k].weeks;
            for (var w = 0; w < arr.length; w++) {
                if (!seen[arr[w]]) {
                    seen[arr[w]] = true;
                    weeks.push(arr[w]);
                }
            }
            weeks.sort(function (a, b) { return a - b; });
            order[k].weeks = weeks;
        }
        return order;
    }

    // ---------- 主流程 ----------
    function runImport() {
        var semesterLabel = '';

        toast('正在检测登录状态...');

        return httpGetText(SEMESTER_PAGE).then(function (html) {
            if (looksLikeLoginPage(html)) {
                throw new Error('尚未登录教务系统。请先在本页面完成登录（学号 + 密码 + 验证码），'
                    + '登录成功后进入「信息查询 → 课表查询」，再点「执行导入」。');
            }
            var sem = parseSemesters(html);
            if (!sem) {
                throw new Error('未能读取学期列表。请确认已登录教务系统，并打开「信息查询 → 课表查询」页面后重试。');
            }
            return window.shangkeBridgePromise
                .showSingleSelection('选择学期', JSON.stringify(sem.labels), sem.defaultIndex)
                .then(function (idx) {
                    if (idx === null || idx === undefined || idx < 0 || idx >= sem.values.length) {
                        toast('导入已取消');
                        return null;
                    }
                    semesterLabel = sem.labels[idx];
                    return sem.values[idx];
                });
        }).then(function (xnxqdm) {
            if (!xnxqdm) return null;

            toast('正在获取「' + semesterLabel + '」课表...');
            return fetchEvents(xnxqdm).then(function (events) {
                var courses = buildCourses(events);
                if (!courses.length) {
                    throw new Error('「' + semesterLabel + '」未查询到课程数据。'
                        + '请确认该学期确实有课，或改选其它学期后重试。');
                }
                return courses;
            });
        }).then(function (courses) {
            if (!courses) return null;

            return window.shangkeBridgePromise.saveImportedCourses(JSON.stringify(courses))
                .then(function () {
                    // 作息保存失败不阻断导入，仅提示
                    return window.shangkeBridgePromise.savePresetTimeSlots(JSON.stringify(TIME_SLOTS))
                        .catch(function (e) {
                            toast('作息时间保存失败：' + ((e && e.message) || e));
                        });
                })
                .then(function () {
                    return showAlert(
                        '导入完成',
                        '已导入「' + semesterLabel + '」共 ' + courses.length + ' 门课程。\n\n'
                        + '说明：本次按教务系统的节次与周次导入；如上下课时间与学校作息不符，'
                        + '请到「设置 → 自定义时间段」调整。',
                        '完成'
                    );
                })
                .then(function () {
                    finish();
                });
        }).catch(function (err) {
            return showAlert('导入失败', (err && err.message) || String(err), '确定');
        });
    }

    // ---------- 能力钩子：成绩与绩点 ----------
    //
    // 契约见 schools/ADAPTER_GUIDE.md「能力钩子」：钩子只 return 数据，不自己 postMessage，
    // 由 App 注入的调用脚本负责拼装与投递。
    //
    // 本校空教室、学业情况（培养方案学分要求）两个用途：教务系统内**没有空教室模块**，
    // 培养方案 / 学习计划模块虽在但查询结果恒为空，无法取得可信数据，故**不声明**对应钩子
    // （缺失时 App 会明确提示「本校暂未适配」，好过回传猜出来的数字）。

    // 按候选字段名取第一个非空值：乘方各校字段名有出入，用候选表兜住
    function pickField(row, candidates) {
        for (var i = 0; i < candidates.length; i++) {
            var v = row[candidates[i]];
            if (v !== undefined && v !== null && String(v).trim() !== '') return v;
        }
        return '';
    }

    // 学期显示值：优先用教务返回的名称；若只有 6 位编码（如 202601）则还原为「2026-2027-1」
    function semesterText(row) {
        var name = String(pickField(row, ['xnxqmc', 'xnxq', 'xqmc', 'semesterName'])).trim();
        if (name) return name;
        var code = String(pickField(row, ['xnxqdm', 'xqdm'])).trim();
        if (/^\d{6}$/.test(code)) {
            var year = parseInt(code.substring(0, 4), 10);
            var term = parseInt(code.substring(4), 10);
            if (year > 2000 && term >= 1 && term <= 3) return year + '-' + (year + 1) + '-' + term;
        }
        return code || null;
    }

    // 分页拉全量成绩：easyui datagrid {total, rows:[...]}；接口不存在/无数据时交回已拿到的
    function fetchGradeRows(page, acc) {
        var params = {
            xnxqdm: '',          // 空 = 全部学期（只查当前学期会漏历史成绩）
            jhlxdm: '', jhlx: '', // 计划类型：留空 = 不限
            page: page,
            rows: GRADE_PAGE_ROWS
        };
        return httpGetText(GRADE_API, params).then(function (text) {
            var json;
            try {
                json = JSON.parse(text);
            } catch (e) {
                throw new Error('成绩接口未返回有效数据，登录可能已失效，请重新登录后重试。');
            }
            var rows = null;
            if (Object.prototype.toString.call(json) === '[object Array]') rows = json;
            else if (json && Object.prototype.toString.call(json.rows) === '[object Array]') rows = json.rows;
            if (!rows) return acc;

            var all = acc.concat(rows);
            var total = parseInt(json && json.total, 10);
            if (rows.length === 0 || page >= GRADE_MAX_PAGES
                || (!isNaN(total) && all.length >= total)) {
                return all;
            }
            return fetchGradeRows(page + 1, all);
        });
    }

    // 成绩钩子：返回 GradeItem 数组（字段见 ADAPTER_GUIDE「shangkeScanGrades」）
    function shangkeScanGrades() {
        return fetchGradeRows(1, []).then(function (rows) {
            var out = [];
            for (var i = 0; i < rows.length; i++) {
                var row = rows[i];
                if (!row) continue;

                var courseName = String(pickField(row, ['kcmc', 'courseName', 'kcbmc'])).trim();
                if (!courseName) continue;

                // 数字成绩优先（便于算平均分 / 绩点）；等级制课程回落到显示成绩
                var scoreText = String(pickField(row, ['zcj', 'zzcj', 'cj', 'kscj', 'score'])).trim();
                if (!scoreText) continue;

                var creditRaw = String(pickField(row, ['xf', 'credit', 'kxf'])).trim();
                var credit = creditRaw === '' ? null : Number(creditRaw);

                out.push({
                    courseName: courseName,
                    credit: isFinite(credit) ? credit : null,
                    scoreText: scoreText,
                    semester: semesterText(row),
                    category: String(pickField(row, ['kclbmc', 'kcxzmc', 'jhlxmc', 'category'])).trim() || null
                });
            }
            return out;
        });
    }

    // 注入器（JS_IMPORT_AUTOSTART）会调用 window.shangkeImportEntry
    if (typeof window !== 'undefined') {
        window.shangkeImportEntry = runImport;
        window.shangkeScanGrades = shangkeScanGrades;
    }
})();
