// 汕头大学教务（正方教务系统 + CAS 统一身份认证）适配器 v1
// 系统特征：
//   登录：访问 jw.stu.edu.cn/jsxsd/ 重定向到 CAS 统一认证 sso.stu.edu.cn/login
//   教务：正方教务系统（jsxsd 路径），课表页 /jsxsd/xskb/xskb_list.do
//   课表渲染：table#timetable（5大节 × 7天），课程在嵌套 iframe FrameNEW_XSD_PYGL_WDKB_XQLLKB 中
//   课程单元格：.kbcontent div（显示）+ .kbcontent1 div（隐藏提示用），内含 <font title="..."> 结构化字段
//     - 无 title：课程名
//     - title="教师"：教师名
//     - title="周次(节次)"：如 "3-18(周)[01-02节]" 或 "3-4,6-11(周)[06-07节]"
//     - title="教室"：教室名
//   节次支持多段：[03-04-05节]（跨3小节）
//   周次支持多段：3-4,6-11(周)
//
// 适配策略：
//   1. CAS 登录由用户在页面手动完成（账号+密码+验证码）
//   2. 用户登录后进入「培养管理 → 我的课表 → 学期理论课表」页面
//   3. 适配器遍历 table#timetable，从 .kbcontent div 的 <font title> 提取结构化课程数据
//   4. 解析周次（含多段、单双周）和节次（含多段）
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

    function select(title, labels, defaultIndex) {
        return window.shangkeBridgePromise.showSelect(title, labels, defaultIndex || 0);
    }

    function saveCourses(courses) {
        var json = JSON.stringify(courses);
        return new Promise(function (resolve, reject) {
            try {
                window.shangkeBridge.saveImportedCourses(json);
                resolve();
            } catch (e) {
                reject(e);
            }
        });
    }

    // ---------- 登录检测 ----------
    function checkLogin() {
        // 正方教务系统：访问课表页，如果重定向到 CAS 登录页则未登录
        return fetch('/jsxsd/xskb/xskb_list.do', {
            method: 'GET',
            credentials: 'include',
            redirect: 'follow'
        }).then(function (r) {
            // 如果 URL 包含 sso.stu.edu.cn 或 login，说明未登录
            if (r.url.indexOf('sso.stu.edu.cn') !== -1 || r.url.indexOf('/login') !== -1) {
                return false;
            }
            return r.text().then(function (t) {
                // 检查页面内容是否包含登录表单或 CAS 标识
                if (t.indexOf('用户名') !== -1 && t.indexOf('密码') !== -1 && t.indexOf('验证码') !== -1) {
                    return false;
                }
                if (t.indexOf('STU Single Sign On') !== -1) {
                    return false;
                }
                return true;
            });
        }).catch(function () {
            // 网络错误时假设已登录（让后续解析去判断）
            return true;
        });
    }

    // ---------- 课表解析 ----------

    // 收集当前文档及所有 iframe（正方课表常渲染在嵌套 iframe 中）
    function collectDocuments() {
        var docs = [document];
        function collect(doc) {
            try {
                var iframes = doc.querySelectorAll('iframe');
                for (var i = 0; i < iframes.length; i++) {
                    try {
                        var d = iframes[i].contentDocument || iframes[i].contentWindow.document;
                        if (d) {
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

    // 定位课表表格 table#timetable
    function findTimetable() {
        var docs = collectDocuments();
        for (var i = 0; i < docs.length; i++) {
            var table = docs[i].getElementById('timetable');
            if (table) return table;
        }
        // 兜底：查找包含"星期一"表头的表格
        for (var j = 0; j < docs.length; j++) {
            var tables = docs[j].querySelectorAll('table');
            for (var k = 0; k < tables.length; k++) {
                var text = tables[k].textContent || '';
                if (text.indexOf('星期一') !== -1 && text.indexOf('大节') !== -1) {
                    return tables[k];
                }
            }
        }
        return null;
    }

    // 解析周次：支持 3-18(周) / 3-4,6-11(周) / 1-16(单周) / 1-16(双周)
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

    // 解析节次：支持 [01-02节] / [03-04-05节] / [第3节]
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

    // 从 .kbcontent div 中提取课程信息
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

    // 主解析函数：遍历课表表格，提取所有课程
    function parseSchedule() {
        var table = findTimetable();
        if (!table) return [];

        var rows = table.querySelectorAll('tr');
        var courses = [];
        var seen = {};

        // 从第1行开始（第0行是表头），到倒数第2行（最后一行是备注）
        for (var i = 1; i < rows.length - 1; i++) {
            var cells = rows[i].querySelectorAll('td');
            for (var j = 0; j < cells.length; j++) {
                var cell = cells[j];
                var day = j + 1; // col 0 是节次列，col 1=周一

                // 只解析 .kbcontent（显示的），不解析 .kbcontent1（隐藏的提示用）
                var kbDivs = cell.querySelectorAll('.kbcontent');
                for (var k = 0; k < kbDivs.length; k++) {
                    var div = kbDivs[k];
                    // 跳过 kbcontent1
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

    // ---------- 主流程 ----------
    function runImport() {
        // 1. 检测登录状态
        toast('正在检测登录状态...');
        return checkLogin().then(function (loggedIn) {
            if (!loggedIn) {
                return alert(
                    '汕头大学教务导入',
                    '请先在上方页面完成 CAS 统一身份认证登录（学号 + 密码 + 验证码）。\n\n' +
                    '登录成功后，请进入「培养管理 → 我的课表 → 学期理论课表」页面，\n' +
                    '确认课表已显示后，回到本页面点击「确定」开始导入。\n\n' +
                    '提示：如遇验证码无法显示，请点击验证码图片刷新。',
                    '已登录并打开课表页'
                ).then(function (ok) {
                    if (!ok) { toast('导入已取消'); return null; }
                    return checkLogin().then(function (ok2) {
                        if (!ok2) throw new Error('仍未检测到登录状态，请确认已在页面中完成 CAS 登录');
                        return true;
                    });
                });
            }
            return true;
        }).then(function (ready) {
            if (!ready) return null;

            // 2. 解析课表
            toast('正在解析课表...');
            var courses = parseSchedule();

            if (courses.length === 0) {
                // 可能课表在 iframe 中还没加载，或者用户没进入课表页
                return alert(
                    '未检测到课表',
                    '未能在当前页面检测到课表数据。\n\n' +
                    '请确认：\n' +
                    '1. 已进入「培养管理 → 我的课表 → 学期理论课表」页面\n' +
                    '2. 课表已完全加载显示\n' +
                    '3. 已选择正确的学期\n\n' +
                    '确认后请重新点击导入按钮。',
                    '确定'
                );
            }

            // 3. 保存导入
            return saveCourses(courses).then(function () {
                return alert(
                    '导入完成',
                    '成功导入 ' + courses.length + ' 门课程。\n\n' +
                    '说明：本次按节次导入课程，如上下课时间与学校作息不符，\n' +
                    '请到「设置 → 自定义时间段」按学校作息调整。',
                    '完成'
                );
            }).then(function () {
                window.shangkeBridge.notifyTaskCompletion();
            });
        }).catch(function (error) {
            return alert('导入失败', (error && error.message) || String(error), '确定');
        });
    }

    // 注入器（JS_IMPORT_AUTOSTART）会调用 window.shangkeImportEntry
    if (typeof window !== 'undefined') {
        window.shangkeImportEntry = runImport;
    }
})();
