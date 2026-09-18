// 右江民族医学院（ymun）教务专属适配器
//
// ===== 站点事实（2026-09-17 实测）=====
//   登录：http://emis.ymun.edu.cn/ （乘方教务新版，图形验证码，无法自动登录）
//   主框架：/new/welcome.page
//   课表页：/new/student/xsgrkb/main.page （嵌套 iframe 加载 week.page）
//   实际课表：/new/student/xsgrkb/week.page?xnxqdm=202601
//   课表渲染：FullCalendar agendaWeek 周视图
//   课程数据 API：POST /new/student/xsgrkb/getCalendarWeekDatas
//     参数：d1=YYYY-MM-DD HH:mm:ss, d2=YYYY-MM-DD HH:mm:ss, zc=周次(空=全部), xnxqdm=学期代码
//     返回：{code:0, data:[{kcmc, teaxms, qsxq, jsxq, qssj, jssj, zc, jxcdmc, bapjxcd, xs, ...}]}
//   学期下拉：week.page 中 select#xnxqdm（值如 202601 = 2026-2027-1）
//   周次下拉：select#zc（空=全部，1-22）
//
//   节次时间映射（businessHours）：
//     第1节 08:00 | 第2节 08:50 | 第3节 09:40 | 第4节 10:30 | 第5节 11:20
//     第6节 15:00 | 第7节 15:50 | 第8节 16:40 | 第9节 17:30
//     第10节 19:30 | 第11节 20:20 | 第12节 21:10
//
// ===== 桥接契约 =====
//   window.shangkeImportEntry 由注入器自动调用。
//   课程 JSON 字段：name / teacher / position / day / startSection / endSection / weeks

(function () {
    'use strict';

    var JW_HOST = 'emis.ymun.edu.cn';
    var API_URL = '/new/student/xsgrkb/getCalendarWeekDatas';
    var WEEK_PAGE_URL = '/new/student/xsgrkb/week.page';
    var MAIN_PAGE_URL = '/new/student/xsgrkb/main.page';
    var WELCOME_URL = '/new/welcome.page';

    // 节次开始时间表（按节次编号升序）
    var SECTION_STARTS = [
        { sec: 1,  time: '08:00' },
        { sec: 2,  time: '08:50' },
        { sec: 3,  time: '09:40' },
        { sec: 4,  time: '10:30' },
        { sec: 5,  time: '11:20' },
        { sec: 6,  time: '15:00' },
        { sec: 7,  time: '15:50' },
        { sec: 8,  time: '16:40' },
        { sec: 9,  time: '17:30' },
        { sec: 10, time: '19:30' },
        { sec: 11, time: '20:20' },
        { sec: 12, time: '21:10' }
    ];

    // ---------- 工具 ----------
    function toast(msg) {
        try {
            if (window.shangkeBridge && typeof window.shangkeBridge.showToast === 'function') {
                window.shangkeBridge.showToast(msg);
            }
        } catch (e) { }
    }
    function alert(title, msg, btn) {
        return window.shangkeBridgePromise.showAlert(title, msg, btn || '确定');
    }
    function select(title, items, defaultIdx) {
        return window.shangkeBridgePromise.showSingleSelection(title, JSON.stringify(items), defaultIdx);
    }
    function clean(text) {
        if (!text) return '';
        return String(text).replace(/ /g, ' ').replace(/\s+/g, ' ').trim();
    }
    function isJwHost() {
        return location.host.indexOf(JW_HOST) !== -1;
    }

    // ---------- 节次时间映射 ----------
    // 将 "HH:MM" 转为分钟数
    function timeToMinutes(t) {
        var parts = t.split(':');
        return parseInt(parts[0], 10) * 60 + parseInt(parts[1], 10);
    }
    // 根据开始时间找起始节次
    function timeToStartSection(timeStr) {
        var t = timeToMinutes(timeStr.substring(0, 5));
        var result = 1;
        for (var i = 0; i < SECTION_STARTS.length; i++) {
            if (timeToMinutes(SECTION_STARTS[i].time) <= t) {
                result = SECTION_STARTS[i].sec;
            }
        }
        return result;
    }
    // 根据结束时间找结束节次
    function timeToEndSection(timeStr) {
        var t = timeToMinutes(timeStr.substring(0, 5));
        var result = 12;
        for (var i = SECTION_STARTS.length - 1; i >= 0; i--) {
            if (timeToMinutes(SECTION_STARTS[i].time) <= t) {
                result = SECTION_STARTS[i].sec;
                break;
            }
        }
        return result;
    }

    // ---------- 页面检测 ----------
    // 检测当前是否在课表相关页面
    function isOnTimetablePage() {
        var path = location.pathname;
        return path.indexOf('/xsgrkb/') !== -1 || path.indexOf('/week.page') !== -1;
    }
    // 检测是否在登录页
    function isLoginPage() {
        var path = location.pathname;
        var hasLoginForm = !!document.getElementById('login-form') ||
            !!document.getElementById('L_account') ||
            !!document.querySelector('input[type="password"]');
        return path === '/' || path.indexOf('/login') !== -1 || hasLoginForm;
    }

    // ---------- 数据获取 ----------
    // fetch 课表周页面 HTML，提取学期下拉选项
    function fetchWeekPage(xnxqdm) {
        var url = WEEK_PAGE_URL + '?xnxqdm=' + encodeURIComponent(xnxqdm || '');
        return fetch(url, { credentials: 'include' })
            .then(function (r) { return r.text(); })
            .then(function (html) {
                var doc = new DOMParser().parseFromString(html, 'text/html');
                return doc;
            });
    }

    // 从 week.page 文档中提取学期列表
    function readSemesters(doc) {
        if (!doc || !doc.getElementById) return { list: [], currentIndex: -1 };
        var sel = doc.getElementById('xnxqdm');
        if (!sel) return { list: [], currentIndex: -1 };
        var options = sel.getElementsByTagName('option');
        var list = [];
        var currentIndex = -1;
        for (var i = 0; i < options.length; i++) {
            var value = options[i].getAttribute('value') || '';
            var label = clean(options[i].textContent) || value;
            if (!value) continue;
            list.push({ value: value, label: label });
            if (options[i].selected) currentIndex = list.length - 1;
        }
        if (currentIndex < 0 && list.length) currentIndex = 0;
        return { list: list, currentIndex: currentIndex };
    }

    // 调用课表 API 获取一周课程数据
    function fetchWeekData(xnxqdm, weekStartDate) {
        // weekStartDate 是周一的日期字符串 YYYY-MM-DD
        var d1 = weekStartDate + ' 00:00:00';
        var d2 = weekStartDate + ' 23:59:59';
        var body = 'd1=' + encodeURIComponent(d1) +
            '&d2=' + encodeURIComponent(d2) +
            '&zc=' + encodeURIComponent('') +
            '&xnxqdm=' + encodeURIComponent(xnxqdm);
        return fetch(API_URL, {
            method: 'POST',
            credentials: 'include',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: body
        })
            .then(function (r) { return r.json(); })
            .then(function (json) {
                if (json.code < 0) {
                    throw new Error(json.message || '课表API返回错误 code=' + json.code);
                }
                return json.data || [];
            });
    }

    // ---------- 课程解析 ----------
    // 将 API 返回的原始数据转为标准课程对象
    function parseCourseItem(item) {
        var name = clean(item.kcmc || '');
        var teacher = clean(item.teaxms || '');
        // 教室：bapjxcd="1" 表示不用场地
        var position = '';
        if (item.bapjxcd === '1') {
            position = '';
        } else {
            position = clean(item.jxcdmc || '');
        }
        // 星期：qsxq 1=周日, 2=周一, ..., 7=周六，转换为 1=周一, 7=周日
        var rawDay = parseInt(item.qsxq, 10) || 1;
        var day = rawDay - 1;
        if (day < 1) day = 7;
        // 周次：zc 字段如 "1,2,3" 或 "1-3"，解析为整数数组
        var weeks = parseWeeksString(item.zc || '');
        // 节次：根据 qssj/jssj 计算
        var startSection = timeToStartSection(item.qssj || '08:00');
        var endSection = timeToEndSection(item.jssj || '09:30');
        return {
            name: name,
            teacher: teacher,
            position: position,
            day: day,
            startSection: startSection,
            endSection: endSection,
            weeks: weeks
        };
    }

    // 获取整个学期的课程数据（一次性大范围日期查询）
    function fetchAllWeeks(xnxqdm) {
        // 根据学期代码推算大致的学期日期范围
        // xnxqdm 格式：202601 = 2026-2027学年第1学期
        var year = parseInt(xnxqdm.substring(0, 4), 10);
        var term = parseInt(xnxqdm.substring(4, 6), 10);
        var d1, d2;
        if (term === 1) {
            // 第1学期：9月初 ~ 1月中
            d1 = year + '-08-25 00:00:00';
            d2 = (year + 1) + '-01-20 23:59:59';
        } else {
            // 第2学期：2月底 ~ 7月初
            d1 = year + '-02-20 00:00:00';
            d2 = year + '-07-15 23:59:59';
        }
        var body = 'd1=' + encodeURIComponent(d1) +
            '&d2=' + encodeURIComponent(d2) +
            '&zc=' + encodeURIComponent('') +
            '&xnxqdm=' + encodeURIComponent(xnxqdm);
        return fetch(API_URL, {
            method: 'POST',
            credentials: 'include',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: body
        })
            .then(function (r) { return r.json(); })
            .then(function (json) {
                if (json.code < 0) {
                    throw new Error(json.message || '课表API返回错误 code=' + json.code);
                }
                var items = json.data || [];
                return items.map(parseCourseItem);
            });
    }

    // 解析周次字符串 "1,2,3" 或 "1-3" 为数组
    function parseWeeksString(str) {
        var weeks = [];
        if (!str) return weeks;
        var parts = str.split(',');
        for (var i = 0; i < parts.length; i++) {
            var part = parts[i].trim();
            if (!part) continue;
            var range = part.split('-');
            if (range.length === 2) {
                var start = parseInt(range[0], 10);
                var end = parseInt(range[1], 10);
                for (var w = start; w <= end; w++) {
                    if (!isNaN(w)) weeks.push(w);
                }
            } else {
                var w = parseInt(part, 10);
                if (!isNaN(w)) weeks.push(w);
            }
        }
        return weeks;
    }

    // 猜测当前是第几周
    function guessCurrentWeek(weeksSet, now) {
        // 如果当前周的数据中有某些周次，我们假设当前周就是其中之一
        // 简化：返回1
        return 1;
    }

    // 获取本周周一日期
    function getMonday(d) {
        var date = new Date(d);
        var day = date.getDay();
        var diff = date.getDate() - day + (day === 0 ? -6 : 1);
        return new Date(date.setDate(diff));
    }

    // 格式化日期 YYYY-MM-DD
    function formatDate(d) {
        var year = d.getFullYear();
        var month = ('0' + (d.getMonth() + 1)).slice(-2);
        var day = ('0' + d.getDate()).slice(-2);
        return year + '-' + month + '-' + day;
    }

    // 去重合并：同一门课同一时间的多条记录合并
    function dedupe(courses) {
        var map = {};
        var result = [];
        for (var i = 0; i < courses.length; i++) {
            var c = courses[i];
            var key = c.name + '|' + c.day + '|' + c.startSection + '|' + c.endSection + '|' + c.teacher;
            if (map[key]) {
                // 合并周次数组
                map[key].weeks = mergeWeeksArray(map[key].weeks, c.weeks);
            } else {
                map[key] = {
                    name: c.name,
                    teacher: c.teacher,
                    position: c.position,
                    day: c.day,
                    startSection: c.startSection,
                    endSection: c.endSection,
                    weeks: c.weeks
                };
                result.push(map[key]);
            }
        }
        return result;
    }

    // 合并两个周次数组（去重排序）
    function mergeWeeksArray(a, b) {
        var set = {};
        for (var i = 0; i < a.length; i++) set[a[i]] = true;
        for (var i = 0; i < b.length; i++) set[b[i]] = true;
        return Object.keys(set)
            .map(function (w) { return parseInt(w, 10); })
            .sort(function (x, y) { return x - y; });
    }

    // ---------- 主流程 ----------
    function runImport() {
        if (!isJwHost()) {
            return alert('学校不匹配', '当前页面不是右江民族医学院教务系统。\n请先打开教务系统并登录后再导入。', '确定');
        }

        // 检查是否在登录页
        if (isLoginPage()) {
            return alert('请先登录', '请先在教务系统中完成登录，\n然后再点击「导入课表」。', '确定');
        }

        toast('正在获取学期列表…');

        // 先获取学期列表
        fetchWeekPage('')
            .then(function (doc) {
                var semesters = readSemesters(doc);
                if (!semesters.list.length) {
                    throw new Error('未能获取学期列表，请确认已登录并进入课表页面');
                }
                return semesters;
            })
            .then(function (semesters) {
                // 让用户选择学期
                var items = semesters.list.map(function (s) { return s.label; });
                return select('选择学期', items, semesters.currentIndex)
                    .then(function (idx) {
                        var chosen = semesters.list[idx];
                        return { chosen: chosen, semesters: semesters };
                    });
            })
            .then(function (ctx) {
                var chosen = ctx.chosen;
                toast('正在获取「' + chosen.label + '」的课表…');
                // 获取所有周的课程数据
                return fetchAllWeeks(chosen.value)
                    .then(function (courses) {
                        courses = dedupe(courses);
                        if (!courses.length) {
                            return alert('该学期没有课程', '「' + chosen.label + '」没有解析到课程。', '确定');
                        }
                        return { courses: courses, semesterLabel: chosen.label };
                    });
            })
            .then(function (payload) {
                if (!payload || !payload.courses || !payload.courses.length) return;
                var courses = payload.courses;
                var nameCount = {};
                for (var i = 0; i < courses.length; i++) nameCount[courses[i].name] = true;
                var distinctNames = Object.keys(nameCount).length;
                return window.shangkeBridgePromise.saveImportedCourses(JSON.stringify(courses))
                    .then(function () {
                        return alert('导入完成',
                            '成功导入 ' + distinctNames + ' 门课程（共 ' + courses.length + ' 个课次）' +
                            (payload.semesterLabel ? '\n学期：' + payload.semesterLabel : '') +
                            '\n\n' +
                            '课程按节次导入；如上下课时间与学校作息不符，\n' +
                            '请到「设置 → 自定义时间段」按学校作息调整。',
                            '完成');
                    })
                    .then(function () {
                        window.shangkeBridge.notifyTaskCompletion();
                    });
            })
            .catch(function (error) {
                return alert('导入失败',
                    '错误信息：' + (error && error.message ? error.message : String(error)) +
                    '\n\n请确认已登录并进入课表查询页面后重试。',
                    '确定');
            });
    }

    window.shangkeNavigateToTimetable = function () {
        // 导航到课表主页面
        if (location.host.indexOf(JW_HOST) !== -1) {
            location.href = MAIN_PAGE_URL;
        }
        return true;
    };

    if (typeof window !== 'undefined') {
        window.shangkeImportEntry = runImport;
        window.ymunImport = runImport;
    }
})();
