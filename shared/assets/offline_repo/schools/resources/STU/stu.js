// 汕头大学教务（正方教务系统 + CAS 统一身份认证）适配器 v2
// 系统特征：
//   登录：访问 jw.stu.edu.cn/jsxsd/ 重定向到 CAS 统一认证 sso.stu.edu.cn/login
//   教务：正方教务系统（jsxsd 路径），课表页 /jsxsd/xskb/xskb_list.do
//   课表渲染：table#timetable（5大节 × 7天），课程可能在当前文档或嵌套 iframe 中
//   课程单元格：.kbcontent div（显示）+ .kbcontent1 div（隐藏提示用），内含 <font title="..."> 结构化字段
//     - 无 title：课程名
//     - title="教师"：教师名
//     - title="周次(节次)"：如 "3-18(周)[01-02节]" 或 "3-4,6-11(周)[06-07节]"
//     - title="教室"：教室名
//   节次支持多段：[03-04-05节]（跨3小节）
//   周次支持多段：3-4,6-11(周)
//
// 适配策略（v2 改进）：
//   1. 不做复杂的 fetch 登录检测（WebView 中可能不可靠），直接尝试解析课表
//   2. 解析失败时等待 1.5 秒重试一次（页面可能还在加载）
//   3. 仍失败则提示用户：确认已登录 + 已进入「学期理论课表」页面 + 课表已显示
//   4. 支持当前文档和所有嵌套 iframe 中的课表
//   5. 解析成功后直接保存导入
//
// 桥接契约：window.shangkeImportEntry 由注入器自动调用（WebBridgeProtocol.JS_IMPORT_AUTOSTART）。

(function () {
    'use strict';

    // ---------- 桥接工具 ----------
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

    function sleep(ms) {
        return new Promise(function (resolve) { setTimeout(resolve, ms); });
    }

    // ---------- 文档收集 ----------
    // 收集当前文档及所有同源 iframe（正方课表常渲染在嵌套 iframe 中）
    function collectDocuments() {
        var docs = [document];
        var seen = new Set();
        seen.add(document);

        function collect(doc) {
            try {
                var iframes = doc.querySelectorAll('iframe');
                for (var i = 0; i < iframes.length; i++) {
                    try {
                        var d = iframes[i].contentDocument || iframes[i].contentWindow.document;
                        if (d && !seen.has(d)) {
                            seen.add(d);
                            docs.push(d);
                            collect(d);
                        }
                    } catch (e) { /* 跨域忽略 */ }
                }
            } catch (e) { }
        }
        collect(document);
        return docs;
    }

    // ---------- 课表表格定位 ----------
    function findTimetable() {
        var docs = collectDocuments();

        // 1. 优先按 id 查找
        for (var i = 0; i < docs.length; i++) {
            var table = docs[i].getElementById('timetable');
            if (table) return table;
        }

        // 2. 按 class 查找
        for (var j = 0; j < docs.length; j++) {
            var byClass = docs[j].querySelector('table.kbcontent, table#kbgrid_table_0, table[class*="kb"]');
            if (byClass) return byClass;
        }

        // 3. 兜底：查找包含"星期一"和"大节"表头的表格
        for (var k = 0; k < docs.length; k++) {
            var tables = docs[k].querySelectorAll('table');
            for (var t = 0; t < tables.length; t++) {
                var text = tables[t].textContent || '';
                if (text.indexOf('星期一') !== -1 &&
                    (text.indexOf('大节') !== -1 || text.indexOf('节次') !== -1)) {
                    return tables[t];
                }
            }
        }

        return null;
    }

    // ---------- 周次解析 ----------
    // 支持：3-18(周) / 3-4,6-11(周) / 1-16(单周) / 1-16(双周) / 3周
    function parseWeeks(weekRaw) {
        if (!weekRaw) return [];
        var weeks = {};
        var found = false;

        // 去掉节次部分 [01-02节]
        var weekPart = weekRaw.replace(/\[.*?\]/g, '').replace(/\(周\)/g, '').trim();
        var isOdd = /[（(]\s*单\s*[）)]/.test(weekRaw);
        var isEven = /[（(]\s*双\s*[）)]/.test(weekRaw);

        var parts = weekPart.split(/[,，、]/);
        for (var p = 0; p < parts.length; p++) {
            var part = parts[p].trim();
            if (!part) continue;

            var rangeMatch = part.match(/(\d+)\s*[-~－—]\s*(\d+)/);
            if (rangeMatch) {
                var start = parseInt(rangeMatch[1], 10);
                var end = parseInt(rangeMatch[2], 10);
                if (isNaN(start) || start < 1) continue;
                if (isNaN(end) || end < start) end = start;
                if (end > 32) end = 32;
                for (var w = start; w <= end; w++) {
                    if (isOdd && w % 2 === 0) continue;
                    if (isEven && w % 2 !== 0) continue;
                    weeks[w] = true;
                    found = true;
                }
            } else {
                var single = part.match(/(\d+)/);
                if (single) {
                    var n = parseInt(single[1], 10);
                    if (n >= 1 && n <= 32) {
                        weeks[n] = true;
                        found = true;
                    }
                }
            }
        }

        if (!found) return [];
        return Object.keys(weeks).map(function (k) { return parseInt(k, 10); })
            .sort(function (a, b) { return a - b; });
    }

    // ---------- 节次解析 ----------
    // 支持：[01-02节] / [03-04-05节] / [第3节]
    function parseSections(weekRaw) {
        if (!weekRaw) return null;
        var secMatch = weekRaw.match(/\[([\d\-~－—,，]+)\s*节\]/);
        if (secMatch) {
            var nums = secMatch[1].match(/\d+/g);
            if (nums && nums.length >= 2) {
                return {
                    start: parseInt(nums[0], 10),
                    end: parseInt(nums[nums.length - 1], 10)
                };
            } else if (nums && nums.length === 1) {
                var n = parseInt(nums[0], 10);
                return { start: n, end: n };
            }
        }
        return null;
    }

    // ---------- 单个课程块解析 ----------
    function parseKbContent(div, day) {
        var fonts = div.querySelectorAll('font');
        var course = {
            name: '',
            teacher: '',
            weekRaw: '',
            room: '',
            day: day
        };

        for (var i = 0; i < fonts.length; i++) {
            var font = fonts[i];
            var title = font.getAttribute('title') || '';
            var text = font.textContent.trim();
            if (!text) continue;

            if (title === '教师') {
                course.teacher = text;
            } else if (title === '周次(节次)') {
                course.weekRaw = text;
            } else if (title === '教室') {
                course.room = text;
            } else if (!course.name && text.length > 0) {
                course.name = text;
            }
        }

        if (!course.name) return null;
        return course;
    }

    // ---------- 主解析函数 ----------
    function parseCourses() {
        var table = findTimetable();
        if (!table) return [];

        var rows = table.querySelectorAll('tr');
        if (rows.length < 2) return [];

        var courses = [];
        var seen = {};

        // 从第1行开始（第0行是表头），到倒数第2行（最后一行通常是备注）
        for (var i = 1; i < rows.length - 1; i++) {
            var cells = rows[i].querySelectorAll('td');
            for (var j = 0; j < cells.length; j++) {
                var cell = cells[j];
                var day = j + 1; // col 0 是节次列，col 1=周一

                // 查找 .kbcontent div（排除 .kbcontent1 隐藏提示用）
                var kbDivs = cell.querySelectorAll('.kbcontent');
                for (var k = 0; k < kbDivs.length; k++) {
                    var div = kbDivs[k];
                    if (div.classList.contains('kbcontent1')) continue;

                    var course = parseKbContent(div, day);
                    if (!course) continue;

                    course.weeks = parseWeeks(course.weekRaw);
                    var sec = parseSections(course.weekRaw);
                    if (sec) {
                        course.startSection = sec.start;
                        course.endSection = sec.end;
                    } else {
                        // 从行号推断大节（row 1=第一大节=1-2节）
                        course.startSection = (i - 1) * 2 + 1;
                        course.endSection = (i - 1) * 2 + 2;
                    }

                    if (course.weeks.length === 0) continue;

                    // 去重：课程名+星期+节次+周次
                    var key = course.name + '_' + course.day + '_' +
                        course.startSection + '-' + course.endSection + '_' +
                        course.weeks.join(',');
                    if (!seen[key]) {
                        seen[key] = true;
                        delete course.weekRaw;
                        courses.push(course);
                    }
                }
            }
        }

        return courses;
    }

    // ---------- 诊断页面状态（用于错误提示） ----------
    function diagnosePage() {
        var docs = collectDocuments();
        var tableCount = 0;
        var weekHeaderCount = 0;
        var kbContentCount = 0;
        var iframeCount = 0;

        for (var i = 0; i < docs.length; i++) {
            if (docs[i] !== document) iframeCount++;
            var tables = docs[i].querySelectorAll('table');
            tableCount += tables.length;
            for (var t = 0; t < tables.length; t++) {
                var text = tables[t].textContent || '';
                if (text.indexOf('星期一') !== -1) weekHeaderCount++;
            }
            kbContentCount += docs[i].querySelectorAll('.kbcontent').length;
        }

        return {
            docCount: docs.length,
            iframeCount: iframeCount,
            tableCount: tableCount,
            weekHeaderCount: weekHeaderCount,
            kbContentCount: kbContentCount,
            currentUrl: window.location.href
        };
    }

    // ---------- 主流程 ----------
    async function runImport() {
        try {
            // 1. 检测当前页面，如果是教务主页则自动导航到课表页面
            var currentUrl = window.location.href;
            var isMainPage = currentUrl.indexOf('xsMainV') !== -1 ||
                             (currentUrl.indexOf('stu.edu.cn') !== -1 &&
                              currentUrl.indexOf('xskb') === -1 &&
                              currentUrl.indexOf('xsgrkb') === -1);

            if (isMainPage) {
                toast('正在跳转到课表页面...');
                // 导航到课表页面
                window.location.href = '/jsxsd/xskb/xskb_list.do';
                // 等待页面加载（适配器会被重新注入，这里只是提示）
                await sleep(3000);
                // 如果还在当前页面（导航失败），提示用户
                if (window.location.href.indexOf('xskb') === -1) {
                    await alert(
                        '请手动进入课表页面',
                        '自动跳转课表页面失败。\n\n' +
                        '请在页面中手动导航：\n' +
                        '「培养管理 → 我的课表 → 学期理论课表」\n\n' +
                        '进入课表页面并确认课表显示后，重新点击导入按钮。',
                        '确定'
                    );
                    return;
                }
            }

            // 2. 多次尝试解析课表（页面可能还在加载）
            toast('正在解析课表...');
            var courses = [];
            var maxAttempts = 4;
            var waitTimes = [1000, 2000, 3000];

            for (var attempt = 0; attempt < maxAttempts; attempt++) {
                courses = parseCourses();
                if (courses.length > 0) break;
                if (attempt < maxAttempts - 1) {
                    toast('课表加载中，第 ' + (attempt + 2) + ' 次尝试...');
                    await sleep(waitTimes[attempt] || 2000);
                }
            }

            // 3. 仍然失败，给出详细诊断
            if (courses.length === 0) {
                var diag = diagnosePage();
                var diagMsg = '';

                if (diag.tableCount === 0) {
                    diagMsg = '当前页面未检测到任何表格。可能未进入课表页面，或页面尚未加载。';
                } else if (diag.weekHeaderCount === 0) {
                    diagMsg = '检测到 ' + diag.tableCount + ' 个表格，但未找到含"星期一"表头的课表表格。';
                } else if (diag.kbContentCount === 0) {
                    diagMsg = '检测到课表表格（含星期表头），但未找到课程块（.kbcontent）。课表可能尚未加载，或该学期暂无课程。';
                } else {
                    diagMsg = '检测到 ' + diag.kbContentCount + ' 个课程块，但解析失败。可能是页面结构变化。';
                }

                await alert(
                    '未检测到课表',
                    '未能在当前页面检测到可导入的课表数据。\n\n' +
                    '当前页面：' + (diag.currentUrl || '未知') + '\n\n' +
                    '请确认：\n' +
                    '1. 已完成 CAS 登录\n' +
                    '2. 已进入「培养管理 → 我的课表 → 学期理论课表」页面\n' +
                    '3. 课表已完全加载（能看到课程块，不是空白）\n' +
                    '4. 已选择正确的学期\n\n' +
                    '诊断信息：' + diagMsg + '\n\n' +
                    '确认后请重新点击导入按钮。',
                    '确定'
                );
                return;
            }

            // 4. 保存导入
            await window.shangkeBridgePromise.saveImportedCourses(JSON.stringify(courses));

            // 5. 提示成功
            await alert(
                '导入完成',
                '成功导入 ' + courses.length + ' 门课程。\n\n' +
                '说明：本次按节次导入课程，如上下课时间与学校作息不符，\n' +
                '请到「设置 → 自定义时间段」按学校作息调整。',
                '完成'
            );

            // 6. 通知任务完成
            window.shangkeBridge.notifyTaskCompletion();

        } catch (error) {
            await alert(
                '导入失败',
                '错误信息：' + (error && error.message ? error.message : String(error)) + '\n\n' +
                '当前页面：' + window.location.href + '\n\n' +
                '请确认已登录并进入「学期理论课表」页面后重试。',
                '确定'
            );
        }
    }

    // 注入器（JS_IMPORT_AUTOSTART）会调用 window.shangkeImportEntry
    if (typeof window !== 'undefined') {
        window.shangkeImportEntry = runImport;
    }
})();
