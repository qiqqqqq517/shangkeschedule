// 大连理工大学（DLUT）本科教务 课表适配脚本
// 适配系统：jxgl.dlut.edu.cn 学生端（树维/强智新版，supwisdom）
// 课表页：http://jxgl.dlut.edu.cn/student/for-std/course-table
// 登录：统一身份认证（CAS，sso.dlut.edu.cn）；登录页本地账密表单被隐藏，
//       入口为「从统一身份认证平台登录」→ /student/ucas-sso/login
// 适配范围：本科（BACHELOR_AND_ASSOCIATE）
//
// ===== 站点事实（2026-09-23 实测）=====
//   1. 课表页 /student/for-std/course-table 服务端渲染，内联脚本带：
//        #allSemesters 下拉框：value=学期ID（如 443），text=学期名（如 2026-2027学年第一学期），
//                              带 selected 的是当前学期（实测 79 项，倒序）
//        data['stdPersonId'] = <personId>（如 1051335）
//   2. 数据接口（同源 fetch，需登录会话）：
//        GET  /student/for-std/course-table/get-data?bizTypeId=2&semesterId=<id>
//             → { timeTableLayoutId, lessonIds[], lessonId2Flag{id:'publish'|'noPublish'},
//                 weekIndices[], currentWeek, lessons[] }
//        POST /student/ws/schedule-table/timetable-layout  body:{"timeTableLayoutId":<id>}
//             → { result: { courseUnitList: [{indexNo, startTime:800, endTime:845,
//                                             startTimeText:"08:00", endTimeText:"08:45"}] } }
//        POST /student/ws/schedule-table/datum  body:{lessonIds, studentId:null, stdPersonId, weekIndex:null}
//             → { result: { lessonList: [{id, courseName, name, teacherAssignmentList:[{name}]}],
//                           scheduleList: [{lessonId, weekday, startTime, endTime,
//                                           room:{nameZh}, personName, weekIndex}] } }
//        GET  /student/ws/semester/get/<id> → { startDate:"2026-08-31", endDate:"2027-02-28" }
//   3. 时间字段为 HHmm 整数（800=08:00），与 courseUnitList 的 startTime/endTime 精确对应；
//      datum 不传 stdPersonId 也能返回数据（降级可用）。
//   4. 同一教学班同一时段可能有多位教师（合授时 personName 各一条、周次相同），
//      按「周次→教师串」聚合，避免同一时段出现重复课块；换教师（代课）时按周次自然拆分。
//
// ===== 桥接契约 =====
//   window.shangkeImportEntry 由注入器（WebBridgeProtocol.JS_IMPORT_AUTOSTART）自动调用。
//   课程 JSON 字段：name / teacher / position / day / startSection / endSection / weeks
//   （position 为 Kotlin 侧 ImportCourseJsonModel 必填字段，不可写成 room）
//   另提供 window.shangkeNavigateToTimetable 供「一键导航到课表」按钮使用。
(function () {
    'use strict';

    var CTX = '/student';
    var BIZ_TYPE = 2;
    // 教务系统绝对入口（用于「一键导航到课表」及跨域场景提示；jxgl 仅支持 http）
    var JXGL_ORIGIN = 'http://jxgl.dlut.edu.cn';
    var COURSE_TABLE_URL = JXGL_ORIGIN + CTX + '/for-std/course-table';
    // 学期下拉最多展示最近 20 个（10 学年）：实测共 79 项，全量列表在弹窗里滚动过长
    var MAX_SEMESTER_OPTIONS = 20;

    // 作息接口失败时的兜底（2026-09-23 实测本校 timeTableLayoutId=21 的 12 小节布局）
    var DEFAULT_UNITS = [
        { indexNo: 1, startTime: 800, endTime: 845, startTimeText: '08:00', endTimeText: '08:45' },
        { indexNo: 2, startTime: 850, endTime: 935, startTimeText: '08:50', endTimeText: '09:35' },
        { indexNo: 3, startTime: 950, endTime: 1035, startTimeText: '09:50', endTimeText: '10:35' },
        { indexNo: 4, startTime: 1040, endTime: 1125, startTimeText: '10:40', endTimeText: '11:25' },
        { indexNo: 5, startTime: 1330, endTime: 1415, startTimeText: '13:30', endTimeText: '14:15' },
        { indexNo: 6, startTime: 1420, endTime: 1505, startTimeText: '14:20', endTimeText: '15:05' },
        { indexNo: 7, startTime: 1510, endTime: 1555, startTimeText: '15:10', endTimeText: '15:55' },
        { indexNo: 8, startTime: 1600, endTime: 1645, startTimeText: '16:00', endTimeText: '16:45' },
        { indexNo: 9, startTime: 1800, endTime: 1845, startTimeText: '18:00', endTimeText: '18:45' },
        { indexNo: 10, startTime: 1850, endTime: 1935, startTimeText: '18:50', endTimeText: '19:35' },
        { indexNo: 11, startTime: 1940, endTime: 2025, startTimeText: '19:40', endTimeText: '20:25' },
        { indexNo: 12, startTime: 2030, endTime: 2115, startTimeText: '20:30', endTimeText: '21:15' }
    ];

    // ---------- 基础工具 ----------
    function clean(s) {
        return String(s == null ? '' : s).replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim();
    }

    // 去掉重复标记类括号（如 "王胜法(1)"），保留有意义的括号（如 "孙亮(软)"）
    function cleanTeacher(s) {
        return clean(s).replace(/[(（]\d+[)）]/g, '').trim();
    }

    function toast(msg) {
        try {
            if (window.shangkeBridge && typeof window.shangkeBridge.showToast === 'function') {
                window.shangkeBridge.showToast(msg);
            }
        } catch (e) { }
    }

    function showAlert(title, msg, btn) {
        return window.shangkeBridgePromise.showAlert(title, msg, btn || '确定');
    }

    function hhmmText(n) {
        if (n == null || n === '') return '';
        var s = String(n);
        if (s.length === 3) s = '0' + s;
        if (s.length !== 4) return '';
        return s.slice(0, 2) + ':' + s.slice(2);
    }

    // ---------- 网络 ----------
    function isJxglOrigin() {
        try {
            return /(^|\.)jxgl\.dlut\.edu\.cn$/.test(window.location.hostname);
        } catch (e) {
            return false;
        }
    }

    function fetchText(url, options) {
        var opt = options || {};
        opt.credentials = 'include';
        return fetch(url, opt).then(function (res) {
            if (!res.ok) throw new Error('HTTP ' + res.status);
            return res.text();
        });
    }

    function fetchJson(url, options) {
        return fetchText(url, options).then(function (text) {
            var data;
            try {
                data = JSON.parse(text);
            } catch (e) {
                throw new Error('返回内容不是 JSON（登录可能已失效）');
            }
            return data;
        });
    }

    function postJson(url, body) {
        return fetchJson(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
    }

    // ---------- 课表页解析 ----------
    // 解析 /student/for-std/course-table 的 HTML：学期下拉 + stdPersonId
    function parseCourseTablePage(html) {
        var semesters = [];
        var selectedIndex = 0;
        var text = String(html || '');

        var selectMatch = text.match(/<select[^>]*id=["']?allSemesters["']?[^>]*>([\s\S]*?)<\/select>/i);
        if (selectMatch) {
            var optRe = /<option([^>]*)>([\s\S]*?)<\/option>/gi;
            var m;
            while ((m = optRe.exec(selectMatch[1])) !== null) {
                var attrs = m[1] || '';
                var name = clean(m[2].replace(/<[^>]*>/g, ''));
                var valueMatch = attrs.match(/value\s*=\s*["']([^"']*)["']/i);
                var id = valueMatch ? clean(valueMatch[1]) : name;
                if (!id || !name) continue;
                if (/\bselected\b/i.test(attrs)) selectedIndex = semesters.length;
                semesters.push({ id: id, name: name });
            }
        }

        var pidMatch = text.match(/stdPersonId'\]\s*=\s*(\d+)/);
        return {
            semesters: semesters,
            selectedIndex: selectedIndex,
            stdPersonId: pidMatch ? pidMatch[1] : null
        };
    }

    // ---------- 数据获取 ----------
    function fetchSemesterData(semesterId) {
        return fetchJson(CTX + '/for-std/course-table/get-data?bizTypeId=' + BIZ_TYPE +
            '&semesterId=' + encodeURIComponent(semesterId));
    }

    function fetchUnits(layoutId) {
        if (layoutId == null) return Promise.resolve(DEFAULT_UNITS.slice());
        return postJson(CTX + '/ws/schedule-table/timetable-layout', { timeTableLayoutId: layoutId })
            .then(function (layout) {
                var list = layout && layout.result && layout.result.courseUnitList;
                if (list && list.length) return list;
                return DEFAULT_UNITS.slice();
            })
            .catch(function (e) {
                console.warn('[DLUT] timetable-layout 获取失败，使用默认作息：' + (e && e.message));
                return DEFAULT_UNITS.slice();
            });
    }

    function fetchDatum(gd, stdPersonId) {
        return postJson(CTX + '/ws/schedule-table/datum', {
            lessonIds: gd.lessonIds,
            studentId: null,
            stdPersonId: stdPersonId ? Number(stdPersonId) : null,
            weekIndex: null
        });
    }

    function fetchSemesterInfo(semesterId) {
        return fetchJson(CTX + '/ws/semester/get/' + encodeURIComponent(semesterId))
            .catch(function () { return null; });
    }

    // ---------- 数据转换 ----------
    function mergeTeachers(a, b) {
        var list = [];
        function add(s) {
            var parts = String(s || '').split(',');
            for (var i = 0; i < parts.length; i++) {
                var t = clean(parts[i]);
                if (t && list.indexOf(t) === -1) list.push(t);
            }
        }
        add(a);
        add(b);
        return list.join(',');
    }

    // HHmm → 小节号：先精确匹配，再按区间兜底
    function timeToUnit(units, hhmm, isEnd) {
        if (hhmm == null || !units || !units.length) return null;
        var t = Number(hhmm);
        if (isNaN(t)) return null;
        var i, u;
        for (i = 0; i < units.length; i++) {
            u = units[i];
            if (Number(isEnd ? u.endTime : u.startTime) === t) return u.indexNo;
        }
        for (i = 0; i < units.length; i++) {
            u = units[i];
            if (t >= Number(u.startTime) && t <= Number(u.endTime)) return u.indexNo;
        }
        return null;
    }

    function buildTimeSlots(units) {
        var slots = [];
        for (var i = 0; i < units.length; i++) {
            var u = units[i];
            var st = u.startTimeText || hhmmText(u.startTime);
            var et = u.endTimeText || hhmmText(u.endTime);
            if (u.indexNo == null || !st || !et) continue;
            slots.push({ number: u.indexNo, startTime: st, endTime: et });
        }
        return slots;
    }

    function lessonName(lesson, gdLesson) {
        var name = clean(lesson && lesson.courseName);
        if (!name && gdLesson) {
            name = clean(gdLesson.course && gdLesson.course.nameZh);
        }
        if (!name) name = clean(lesson && lesson.name);
        return name;
    }

    function lessonTeacherStr(lesson) {
        var arr = (lesson && lesson.teacherAssignmentList) || [];
        var names = [];
        for (var i = 0; i < arr.length; i++) {
            var t = cleanTeacher(arr[i].name || (arr[i].person && arr[i].person.nameZh) || '');
            if (t && names.indexOf(t) === -1) names.push(t);
        }
        return names.join(',');
    }

    // 把 datum 的排课数据聚合为课程条目：
    // 同一（教学班, 星期, 起止时间, 教室）按「周次→教师串」聚合；
    // 合授（同周次多教师）合并为一条，代课（不同周次不同教师）按周次拆分。
    function buildCourses(datumResult, gd, units) {
        var lessons = {};
        var gdLessons = {};
        var i, s;

        var lessonList = (datumResult && datumResult.lessonList) || [];
        for (i = 0; i < lessonList.length; i++) lessons[lessonList[i].id] = lessonList[i];
        var gdLessonList = (gd && gd.lessons) || [];
        for (i = 0; i < gdLessonList.length; i++) gdLessons[gdLessonList[i].id] = gdLessonList[i];

        var flags = (gd && gd.lessonId2Flag) || {};
        var groups = {};
        var order = [];
        var schedules = (datumResult && datumResult.scheduleList) || [];

        for (i = 0; i < schedules.length; i++) {
            s = schedules[i];
            if (!s || s.lessonId == null || !s.weekday || s.weekIndex == null) continue;
            var flag = flags[s.lessonId];
            if (flag && flag !== 'publish') continue; // 与页面一致：未发布排课不展示
            var room = clean(s.room && s.room.nameZh ? s.room.nameZh : '');
            var key = [s.lessonId, s.weekday, s.startTime, s.endTime, room].join('|');
            var g = groups[key];
            if (!g) {
                g = groups[key] = {
                    lessonId: s.lessonId,
                    weekday: s.weekday,
                    startTime: s.startTime,
                    endTime: s.endTime,
                    room: room,
                    weekTeacher: {}
                };
                order.push(key);
            }
            var teacher = cleanTeacher(s.personName || '');
            g.weekTeacher[s.weekIndex] = g.weekTeacher[s.weekIndex]
                ? mergeTeachers(g.weekTeacher[s.weekIndex], teacher)
                : teacher;
        }

        var courses = [];
        for (i = 0; i < order.length; i++) {
            var grp = groups[order[i]];
            var lesson = lessons[grp.lessonId] || {};
            var name = lessonName(lesson, gdLessons[grp.lessonId]);
            if (!name) continue;

            var startUnit = timeToUnit(units, grp.startTime, false);
            var endUnit = timeToUnit(units, grp.endTime, true);
            if (startUnit == null || endUnit == null || endUnit < startUnit) continue;

            var fallbackTeacher = lessonTeacherStr(lesson);
            var byTeacher = {};
            for (var w in grp.weekTeacher) {
                if (!Object.prototype.hasOwnProperty.call(grp.weekTeacher, w)) continue;
                var tkey = grp.weekTeacher[w] || fallbackTeacher;
                if (!byTeacher[tkey]) byTeacher[tkey] = [];
                byTeacher[tkey].push(parseInt(w, 10));
            }
            for (var tkey2 in byTeacher) {
                if (!Object.prototype.hasOwnProperty.call(byTeacher, tkey2)) continue;
                var weeks = byTeacher[tkey2].sort(function (a, b) { return a - b; });
                courses.push({
                    name: name,
                    teacher: tkey2 || '未知',
                    position: grp.room || '待定',
                    day: grp.weekday,
                    startSection: startUnit,
                    endSection: endUnit,
                    weeks: weeks
                });
            }
        }
        return courses;
    }

    function buildConfig(gd, semesterInfo) {
        var totalWeeks = 0;
        var weekIndices = (gd && gd.weekIndices) || [];
        for (var i = 0; i < weekIndices.length; i++) {
            if (weekIndices[i] > totalWeeks) totalWeeks = weekIndices[i];
        }
        var startDate = semesterInfo ? (semesterInfo.startDate || null) : null;
        var endDate = semesterInfo ? (semesterInfo.endDate || null) : null;
        if (!totalWeeks && startDate && endDate) {
            var days = Math.ceil((new Date(endDate + 'T00:00:00') - new Date(startDate + 'T00:00:00')) / 86400000);
            totalWeeks = Math.max(1, Math.round(days / 7));
        }
        if (!totalWeeks) totalWeeks = 20;
        return {
            semesterStartDate: startDate,
            semesterTotalWeeks: totalWeeks,
            defaultClassDuration: 45,
            defaultBreakDuration: 10,
            firstDayOfWeek: 1
        };
    }

    // ---------- 主导入流程 ----------
    async function runImportFlow() {
        try {
            var confirmed = await showAlert(
                '大连理工大学课表导入',
                '导入前请确保已通过「统一身份认证」登录教务系统（jxgl.dlut.edu.cn）。\n\n' +
                '本适配将自动读取学期列表、作息时间与课程数据。',
                '开始导入'
            );
            if (!confirmed) {
                toast('导入已取消');
                return;
            }

            // 0. 页面必须在教务域内（jxgl 学生端任意页面均可，数据全部走同源 fetch）；
            //    若停在统一身份认证（sso.dlut.edu.cn）等外部页面，引导先回教务域登录。
            if (!isJxglOrigin()) {
                var go = await showAlert(
                    '需要登录',
                    '当前页面不在教务系统内。\n\n点击「前往课表页」跳转到 jxgl.dlut.edu.cn，完成统一身份认证登录后，再次点击「执行导入」。',
                    '前往课表页'
                );
                if (go) window.location.href = COURSE_TABLE_URL;
                return;
            }

            // 1. 拉取课表页 HTML（同源任意页面均可），解析学期与 stdPersonId
            toast('正在检查登录状态...');
            var html;
            try {
                html = await fetchText(CTX + '/for-std/course-table');
            } catch (e) {
                await showAlert('导入失败', '无法访问课表页面（' + (e && e.message ? e.message : e) + '）。\n请检查网络后重试。', '知道了');
                return;
            }

            var page = parseCourseTablePage(html);
            if (!page.semesters.length) {
                await showAlert(
                    '需要登录',
                    '未检测到课表数据，登录可能已失效。\n\n' +
                    '请先点击页面上的「从统一身份认证平台登录」完成登录，再点击「执行导入」。',
                    '知道了'
                );
                return;
            }

            var semesters = page.semesters;
            var defaultIndex = page.selectedIndex;
            if (semesters.length > MAX_SEMESTER_OPTIONS) {
                semesters = semesters.slice(0, MAX_SEMESTER_OPTIONS);
                if (defaultIndex >= semesters.length) defaultIndex = 0;
            }
            var names = [];
            for (var i = 0; i < semesters.length; i++) names.push(semesters[i].name);

            // 2. 选择学期 → 拉取数据；该学期无数据时允许重新选择
            var courses = null;
            var timeSlots = null;
            var config = null;
            while (true) {
                var selectedIndex = await window.shangkeBridgePromise.showSingleSelection(
                    '选择学期', JSON.stringify(names), defaultIndex);
                if (selectedIndex === null || selectedIndex < 0 || selectedIndex >= semesters.length) {
                    toast('导入已取消');
                    return;
                }
                var semester = semesters[selectedIndex];

                toast('正在获取「' + semester.name + '」课表数据...');
                var gd = await fetchSemesterData(semester.id);
                if (!gd || !gd.lessonIds || !gd.lessonIds.length) {
                    if (!await showAlert('暂无课程', '「' + semester.name + '」没有课程数据，请选择其他学期。', '重新选择')) {
                        toast('导入已取消');
                        return;
                    }
                    continue;
                }

                var units = await fetchUnits(gd.timeTableLayoutId);
                var datum = await fetchDatum(gd, page.stdPersonId);
                var result = datum && datum.result;
                if (!result || !(result.scheduleList || []).length) {
                    if (!await showAlert('暂无排课', '「' + semester.name + '」未返回排课数据，请选择其他学期。', '重新选择')) {
                        toast('导入已取消');
                        return;
                    }
                    continue;
                }

                // 3. 转换
                courses = buildCourses(result, gd, units);
                if (!courses.length) {
                    if (!await showAlert('解析失败', '未能从排课数据中解析出课程，请把该学期反馈给开发者排查。', '重新选择')) {
                        toast('导入已取消');
                        return;
                    }
                    continue;
                }
                timeSlots = buildTimeSlots(units);
                var semesterInfo = await fetchSemesterInfo(semester.id);
                config = buildConfig(gd, semesterInfo);
                break;
            }

            // 4. 保存：作息 → 配置 → 课程
            if (timeSlots.length) {
                try {
                    await window.shangkeBridgePromise.savePresetTimeSlots(JSON.stringify(timeSlots));
                } catch (e) {
                    toast('作息保存失败：' + (e && e.message ? e.message : e));
                }
            }
            await window.shangkeBridgePromise.saveCourseConfig(JSON.stringify(config));
            await window.shangkeBridgePromise.saveImportedCourses(JSON.stringify(courses));

            toast('导入完成：' + courses.length + ' 条课程');
            if (window.shangkeBridge && typeof window.shangkeBridge.notifyTaskCompletion === 'function') {
                window.shangkeBridge.notifyTaskCompletion();
            }
        } catch (e) {
            console.error('[DLUT] 导入异常:', e);
            try {
                await showAlert('导入异常', (e && e.message) ? e.message : String(e), '知道了');
            } catch (e2) { }
        }
    }

    // 一键导航到课表（供 APP 底部按钮使用）
    window.shangkeNavigateToTimetable = function () {
        window.location.href = COURSE_TABLE_URL;
    };

    // 统一入口：由注入器的 JS_IMPORT_AUTOSTART 自动调用
    window.shangkeImportEntry = runImportFlow;
})();
