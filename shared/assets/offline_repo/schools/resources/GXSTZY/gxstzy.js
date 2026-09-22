// 广西生态工程职业技术学院 专用适配脚本
// ===== 站点事实（2026-09-22 实测）=====
//   登录：https://cas.gxstzy.cn/lyuapServer/login （统一身份认证，CAS 单点登录）
//   教务系统：https://jwgl.gxstzy.cn/ （综合教务管理系统，非标准正方）
//   课表页面：/admin/api/getKbxx?xqid=1&userId=<加密>&xnxq=<学年学期>&role=xs
//   课表 API：GET /admin/api/getXskb?xnxq=...&userId=...&xqid=...&week=...&role=xs
//     返回 JSON: { ret:0, data: { kckbData:[...], xlbzs:[...] } }
//   课程字段：kcmc(课名) tmc(教师) croommc(教室) xingqi(星期1-7)
//             djc(节次) rqxl(星期+节次,如101=周一第1节) zcstr(逗号分隔周次)
//             jxbmc(教学班) xf(学分) xqmc(校区)
//   节次时间（jcsjszList）：
//     1:8:00-8:40  2:8:50-9:30  3:9:50-10:30  4:10:40-11:20  5:11:30-12:10
//     6:14:30-15:10 7:15:20-16:00 8:16:10-16:50 9:17:00-17:40
//     10:19:10-19:50 11:20:00-20:40 12:20:50-21:30
//
// ===== Bridge 约束 =====
//   window.shangkeImportEntry 由注入器自动调用。
//   课程 JSON 字段：name / teacher / position / day / startSection / endSection / weeks

(function () {
    'use strict';

    var JW_HOST = 'jwgl.gxstzy.cn';
    var KB_PAGE_PATH = '/admin/api/getKbxx';
    var KB_API_PATH = '/admin/api/getXskb';

    // 节次时间（兜底，实际从页面 jcsjszList 读取）
    var DEFAULT_TIME_SLOTS = [
        { number: 1,  startTime: '08:00', endTime: '08:40' },
        { number: 2,  startTime: '08:50', endTime: '09:30' },
        { number: 3,  startTime: '09:50', endTime: '10:30' },
        { number: 4,  startTime: '10:40', endTime: '11:20' },
        { number: 5,  startTime: '11:30', endTime: '12:10' },
        { number: 6,  startTime: '14:30', endTime: '15:10' },
        { number: 7,  startTime: '15:20', endTime: '16:00' },
        { number: 8,  startTime: '16:10', endTime: '16:50' },
        { number: 9,  startTime: '17:00', endTime: '17:40' },
        { number: 10, startTime: '19:10', endTime: '19:50' },
        { number: 11, startTime: '20:00', endTime: '20:40' },
        { number: 12, startTime: '20:50', endTime: '21:30' }
    ];

    // ---------- 工具 ----------
    function toast(msg) {
        try {
            if (window.shangkeBridge && typeof window.shangkeBridge.showToast === 'function') {
                window.shangkeBridge.showToast(msg);
            }
        } catch (e) { }
    }

    function clean(text) {
        if (!text) return '';
        return String(text).replace(/\s+/g, ' ').trim();
    }

    function isJwHost() {
        return location.host.indexOf(JW_HOST) !== -1;
    }

    function isKbPage() {
        return location.pathname.indexOf(KB_PAGE_PATH) !== -1;
    }

    // 从页面 hidden field 读取值
    function getHiddenValue(id) {
        var el = document.getElementById(id);
        return el ? el.value : '';
    }

    // 从页面提取学期列表
    function getSemesterList() {
        var list = [];
        var items = document.querySelectorAll('#xnxqList li p, #xnxqList li');
        for (var i = 0; i < items.length; i++) {
            var onclick = items[i].getAttribute('onclick') || '';
            var m = onclick.match(/'(\d{4}-\d{4}-\d)'/);
            if (m) {
                list.push({ code: m[1], label: clean(items[i].innerText) || m[1] });
            }
        }
        return list;
    }

    // 时间格式补零："8:00" -> "08:00"
    function padTime(t) {
        if (!t) return '00:00';
        var parts = String(t).split(':');
        if (parts.length !== 2) return t;
        var h = parts[0].padStart(2, '0');
        var m = parts[1].padStart(2, '0');
        return h + ':' + m;
    }

    // 从页面 jcsjszList 读取节次时间
    function getTimeSlotsFromPage() {
        try {
            if (typeof kbjc !== 'undefined' && kbjc.jcsjszList && kbjc.jcsjszList.length) {
                return kbjc.jcsjszList.map(function (s) {
                    return {
                        number: parseInt(s.jc, 10),
                        startTime: padTime(s.kssj),
                        endTime: padTime(s.jssj)
                    };
                }).filter(function (t) { return !isNaN(t.number); });
            }
        } catch (e) { }
        return DEFAULT_TIME_SLOTS;
    }

    // 解析周次字符串 "3,4,7,8,9,10,14" → [3,4,7,...]
    function parseWeeks(zcstr) {
        if (!zcstr) return [];
        var weeks = [];
        var parts = String(zcstr).split(',');
        for (var i = 0; i < parts.length; i++) {
            var w = parseInt(parts[i], 10);
            if (!isNaN(w) && w >= 1 && w <= 32) weeks.push(w);
        }
        return weeks.sort(function (a, b) { return a - b; });
    }

    // 从 rqxl 解析节次号（后两位）
    function sectionFromRqxl(rqxl) {
        if (!rqxl) return 0;
        var n = parseInt(String(rqxl).slice(-2), 10);
        return isNaN(n) ? 0 : n;
    }

    // 合并同一课程的连续节次
    function mergeCourses(rawList) {
        var map = {};
        var order = [];

        rawList.forEach(function (item) {
            var key = [item.kcmc, item.tmc, item.croommc, item.xingqi, item.zcstr, item.jxbid].join('|');
            if (!map[key]) {
                map[key] = {
                    name: clean(item.kcmc),
                    teacher: clean(item.tmc),
                    position: clean(item.croommc),
                    day: parseInt(item.xingqi, 10),
                    startSection: 99,
                    endSection: 0,
                    weeks: parseWeeks(item.zcstr),
                    jxbmc: clean(item.jxbmc),
                    xf: item.xf
                };
                order.push(key);
            }
            var sec = sectionFromRqxl(item.rqxl) || parseInt(item.djc, 10);
            if (sec > 0) {
                if (sec < map[key].startSection) map[key].startSection = sec;
                if (sec > map[key].endSection) map[key].endSection = sec;
            }
        });

        var result = [];
        order.forEach(function (key) {
            var c = map[key];
            if (!c.name || isNaN(c.day) || c.day < 1 || c.day > 7) return;
            if (c.startSection > c.endSection || c.startSection < 1) return;
            if (c.weeks.length === 0) return;

            var remarkParts = [];
            if (c.jxbmc) remarkParts.push('教学班：' + c.jxbmc);
            if (c.xf) remarkParts.push('学分：' + c.xf);
            c.remark = remarkParts.join('；');
            result.push(c);
        });

        return result;
    }

    // 调用 API 获取课程数据
    function fetchCourses(xnxq) {
        var userId = getHiddenValue('userId');
        var xqid = getHiddenValue('xqid') || '1';
        var week = getHiddenValue('week') || '';
        var role = getHiddenValue('role') || 'xs';

        if (!userId) {
            toast('未获取到用户信息，请确认已在课表页面');
            return Promise.reject(new Error('no userId'));
        }

        var params = new URLSearchParams({
            xnxq: xnxq,
            userId: userId,
            xqid: xqid,
            week: week,
            role: role
        });

        toast('正在获取课表数据...');
        return fetch(KB_API_PATH + '?' + params.toString(), {
            credentials: 'include'
        }).then(function (r) { return r.json(); });
    }

    // 保存课程到 APP
    function saveCourses(courses) {
        var timeSlots = getTimeSlotsFromPage();

        // 学期总周数：从课程周次推断最大值
        var maxWeek = 20;
        courses.forEach(function (c) {
            c.weeks.forEach(function (w) {
                if (w > maxWeek) maxWeek = w;
            });
        });

        return window.shangkeBridgePromise.saveCourseConfig(JSON.stringify({
            semesterTotalWeeks: maxWeek,
            firstDayOfWeek: 1
        })).then(function () {
            return window.shangkeBridgePromise.savePresetTimeSlots(JSON.stringify(timeSlots));
        }).then(function () {
            return window.shangkeBridgePromise.saveImportedCourses(JSON.stringify(courses));
        });
    }

    // 导航到课表页面
    function navigateToKbPage() {
        var url = KB_PAGE_PATH + '?xqid=1&role=xs';
        location.href = url;
    }

    // 主流程
    function runImport() {
        // 不在教务系统域名
        if (!isJwHost()) {
            return window.shangkeBridgePromise.showAlert(
                '请先进入教务系统',
                '请在内置浏览器中通过统一身份认证登录广西生态工程职业技术学院教务系统（jwgl.gxstzy.cn），进入课表页面后再点击导入。',
                '知道了'
            );
        }

        // 在教务系统但不在课表页面
        if (!isKbPage()) {
            toast('正在跳转到课表页面...');
            navigateToKbPage();
            return;
        }

        // 在课表页面，等待页面参数就绪
        var userId = getHiddenValue('userId');
        if (!userId) {
            toast('课表页面加载中，请稍候...');
            setTimeout(runImport, 1500);
            return;
        }

        // 学期选择
        var currentXnxq = getHiddenValue('xnxq');
        var semesters = getSemesterList();

        function doImport(xnxq) {
            fetchCourses(xnxq).then(function (resp) {
                if (!resp || resp.ret !== 0) {
                    toast('获取课表失败：' + (resp && resp.msg ? resp.msg : '未知错误'));
                    return;
                }
                var rawList = (resp.data && resp.data.kckbData) || [];
                if (rawList.length === 0) {
                    toast('未查询到课程数据，请确认学期选择');
                    return;
                }
                var courses = mergeCourses(rawList);
                if (courses.length === 0) {
                    toast('课程数据解析失败');
                    return;
                }
                saveCourses(courses).then(function () {
                    toast('成功导入 ' + courses.length + ' 门课程');
                    window.shangkeBridge.notifyTaskCompletion();
                }).catch(function (e) {
                    toast('保存失败：' + (e && e.message ? e.message : e));
                });
            }).catch(function (e) {
                toast('请求失败：' + (e && e.message ? e.message : e));
            });
        }

        // 有多个学期时让用户选择
        if (semesters.length > 1) {
            var labels = semesters.map(function (s) { return s.label; });
            var defaultIdx = 0;
            for (var i = 0; i < semesters.length; i++) {
                if (semesters[i].code === currentXnxq) { defaultIdx = i; break; }
            }
            window.shangkeBridgePromise.showSingleSelection(
                '选择学期',
                JSON.stringify(labels),
                defaultIdx
            ).then(function (idx) {
                if (idx === null || idx === undefined) return;
                doImport(semesters[idx].code);
            });
        } else {
            doImport(currentXnxq || (semesters[0] && semesters[0].code) || '');
        }
    }

    // 暴露入口
    window.shangkeImportEntry = runImport;

    // 导航辅助
    window.shangkeNavigateToTimetable = function () {
        if (isJwHost() && !isKbPage()) {
            navigateToKbPage();
        }
    };

    // 自动提示
    if (isJwHost() && isKbPage()) {
        toast('检测到广西生态职院教务系统，点击导入按钮抓取课表');
    }
})();
