// 芜湖职业技术大学：融合门户的日程校历课表（统一身份认证后同源读取）。
// 2026-10-16 实测 getWeek 提供学期起始日，renderData.datas 提供整学期事件。
// 只读取 isLessonTable=1 的课程，ext.jc 是实际节次；接口不提供教师时不推测。
(function () {
    'use strict';
    var CARD_ID = 'CUS_CARD_CALENDAR';
    var CARD_WID = '5631612758965776';
    var DAY_MS = 86400000;
    var running = null;
    // 本学期门户的单节、双节和三连堂起止时刻相互核对后得到的作息。
    var TIME_SLOTS = [
        { number: 1, startTime: '08:00', endTime: '08:45' },
        { number: 2, startTime: '08:55', endTime: '09:40' },
        { number: 3, startTime: '10:00', endTime: '10:45' },
        { number: 4, startTime: '10:55', endTime: '11:40' },
        { number: 5, startTime: '14:00', endTime: '14:45' },
        { number: 6, startTime: '14:55', endTime: '15:40' },
        { number: 7, startTime: '15:50', endTime: '16:35' },
        { number: 8, startTime: '16:45', endTime: '17:30' },
        { number: 9, startTime: '19:00', endTime: '19:45' },
        { number: 10, startTime: '19:55', endTime: '20:40' },
        { number: 11, startTime: '20:50', endTime: '21:35' }
    ];

    function toast(text) {
        if (window.shangkeBridge && window.shangkeBridge.showToast) window.shangkeBridge.showToast(text);
    }
    function parseDate(text) {
        if (!/^\d{4}-\d{2}-\d{2}$/.test(text || '')) return null;
        var value = Date.parse(text + 'T00:00:00Z');
        return Number.isFinite(value) && new Date(value).toISOString().slice(0, 10) === text ? value : null;
    }
    function dateText(value) { return new Date(value).toISOString().slice(0, 10); }
    function today() {
        var now = new Date();
        return now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0') + '-' + String(now.getDate()).padStart(2, '0');
    }
    async function request(method, param) {
        var controller = new AbortController();
        var timer = setTimeout(function () { controller.abort(); }, 20000);
        try {
            var response = await fetch('/execCardMethod/' + CARD_WID + '/' + CARD_ID, {
                method: 'POST', credentials: 'include', signal: controller.signal,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ cardId: CARD_ID, cardWid: CARD_WID, method: method,
                    param: Object.assign({ lang: 'zh_CN', platformType: 0 }, param) })
            });
            if (!response.ok) throw new Error('课表接口暂不可用（HTTP ' + response.status + '），请稍后重试');
            var data;
            try { data = await response.json(); }
            catch (error) { throw new Error('登录可能已失效，请重新登录融合门户'); }
            if (String(data.errcode) !== '0' || !data.data) throw new Error('请先登录芜湖职业技术大学融合门户，再执行导入');
            return data.data;
        } catch (error) {
            if (error.name === 'AbortError') throw new Error('课表请求超时，请检查网络后重试');
            throw error;
        } finally { clearTimeout(timer); }
    }
    function parseCourses(datas, start, totalWeeks) {
        var map = {}, invalid = 0;
        Object.keys(datas || {}).forEach(function (key) {
            var events = datas[key];
            if (!Array.isArray(events)) return;
            events.forEach(function (event) {
                if (String(event.isLessonTable) !== '1') return;
                var dayText = String(event.startTime || event.startDate || '').slice(0, 10);
                var date = parseDate(dayText);
                if (date === null) { invalid++; return; }
                var week = Math.floor((date - start) / (7 * DAY_MS)) + 1;
                if (week < 1 || week > totalWeeks) return;
                var ext = event.ext;
                if (typeof ext === 'string') {
                    try { ext = JSON.parse(ext); } catch (error) { ext = null; }
                }
                var sections = String(ext && ext.jc || '').match(/^(\d+)(?:\s*[-~至]\s*(\d+))?$/);
                var first = sections ? Number(sections[1]) : 0;
                var last = sections ? Number(sections[2] || sections[1]) : 0;
                var name = String(event.eventTitle || '').trim();
                if (!name || first < 1 || last < first || last > 30) { invalid++; return; }
                var course = { name: name, teacher: '未安排', position: String(event.location || '').trim() || '待定',
                    day: new Date(date).getUTCDay() || 7, startSection: first, endSection: last, weeks: [] };
                var id = JSON.stringify([course.name, course.position, course.day, first, last]);
                if (!map[id]) map[id] = course;
                if (map[id].weeks.indexOf(week) === -1) map[id].weeks.push(week);
            });
        });
        // 数据不完整时整体停止，防止提示成功却漏掉部分课程。
        if (invalid) throw new Error('有 ' + invalid + ' 项课程的日期或节次不完整，请在门户核对后重试');
        return Object.keys(map).map(function (key) {
            var course = map[key];
            course.weeks.sort(function (a,b) { return a-b; });
            return course;
        }).sort(function (a,b) { return a.day-b.day || a.startSection-b.startSection || a.name.localeCompare(b.name); });
    }
    async function runImport() {
        if (window.location.hostname !== 'casp.whit.edu.cn') {
            throw new Error('请先在芜湖职业技术大学融合门户完成登录，再执行导入');
        }
        var bridge = window.shangkeBridgePromise;
        if (!bridge || !bridge.showPrompt || !bridge.saveImportedCourses || !bridge.saveCourseConfig || !bridge.savePresetTimeSlots) {
            throw new Error('导入桥接不可用，请返回 App 重新执行导入');
        }
        var selected = await bridge.showPrompt('芜湖职业技术大学课表', '请输入要导入学期内的任意日期（YYYY-MM-DD）', today());
        if (selected === null || selected === undefined) return;
        selected = String(selected).trim();
        if (parseDate(selected) === null) throw new Error('日期格式不正确，请填写有效日期（YYYY-MM-DD）');
        var info = await request('getWeek', { selectDate: selected });
        var term = info.underInfo || info.postInfo;
        var start = term && parseDate(term.begDate);
        if (!term || start === null) throw new Error('没有查询到该日期所属的学期，请确认已登录并选择学期内的日期');
        var weeksText = await bridge.showPrompt('导入周数', '从 ' + term.begDate + ' 开始读取的总周数（1–32）', '24');
        if (weeksText === null || weeksText === undefined) return;
        weeksText = String(weeksText).trim();
        var totalWeeks = /^\d+$/.test(weeksText) ? Number(weeksText) : 0;
        if (totalWeeks < 1 || totalWeeks > 32) throw new Error('周数不正确，请输入 1–32 之间的整数');
        toast('正在读取完整学期课表…');
        var data = await request('renderData', { startTime: term.begDate + ' 00:00:00',
            endTime: dateText(start + (totalWeeks * 7 - 1) * DAY_MS) + ' 23:59:59', selectDate: selected });
        var courses = parseCourses(data.datas, start, totalWeeks);
        if (!courses.length) throw new Error('本学期没有查询到课程，请核对所选日期或到门户查看课表');
        var config = await bridge.saveCourseConfig(JSON.stringify({ semesterStartDate: term.begDate,
            semesterTotalWeeks: totalWeeks, firstDayOfWeek: 1, defaultClassDuration: 45,
            showWeekends: courses.some(function (course) { return course.day >= 6; }) }));
        if (config !== true && config !== 'true') throw new Error('学期配置保存未成功，请重试');
        var slots = await bridge.savePresetTimeSlots(JSON.stringify(TIME_SLOTS));
        if (slots !== true && slots !== 'true') throw new Error('学校作息保存未成功，请重试');
        var saved = await bridge.saveImportedCourses(JSON.stringify(courses));
        if (saved !== true && saved !== 'true') throw new Error('课表保存未成功，请确认已选择课表后重试');
        toast('成功导入 ' + courses.length + ' 项课程安排');
        if (window.shangkeBridge && window.shangkeBridge.notifyTaskCompletion) window.shangkeBridge.notifyTaskCompletion();
    }
    window.shangkeImportEntry = function () {
        if (!running) running = runImport().catch(function (error) {
            if (typeof window.__shangkeReportScriptError === 'function') window.__shangkeReportScriptError(error);
            else toast(error && error.message || '课表导入失败，请重新登录后重试');
        }).then(function () { running = null; });
        return running;
    };
})();
