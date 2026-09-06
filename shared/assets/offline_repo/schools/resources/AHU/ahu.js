// 安徽大学教务（金智 EAMS 新版课表，经 WebVPN + CAS 统一认证）适配器 v1
// 入口：https://wvpn.ahu.edu.cn/https/77726476706e69737468656265737421fff944d226387d1e7b0c9ce29b5b/cas/login
//       （service 指向智慧安大门户；登录后在中间页点「教务系统」进入 jw.ahu.edu.cn）
// 教务：金智 EAMS 新版，页面 /student/for-std/course-table
// 数据源（登录态 AJAX JSON）：
//   GET /student/for-std/course-table/get-data?bizTypeId=2&semesterId={id}&dataId=
//       返回 lessons[]（行政班合并课表）：
//         course.nameZh                     课程名
//         scheduleText.dateTimePlacePersonText.textZh
//             形如 "1~9周 星期一 3~4节 磬苑校区 博学北楼B403 魏丕静"
//                  "7~17(单)周 星期一 6~9节 …" / "10~14(双)周 …"
//         teacherAssignmentString           教师（可能为空）
//   学期选项内嵌在课表页 <select id="allSemesters"> 的 option 中（value=学期id）
// 桥接契约：window.shangkeImportEntry 由注入器自动调用。

var AHU_CT_PATH = '/student/for-std/course-table';

function ahuToast(msg) {
    if (window.shangkeBridge && typeof window.shangkeBridge.showToast === 'function') {
        window.shangkeBridge.showToast(msg);
    }
}

function ahuAlert(title, msg, btn) {
    return window.shangkeBridgePromise.showAlert(title, msg, btn);
}

// 相对路径 fetch（同源，携带登录态；EAMS 接口返回 UTF-8 JSON）
function ahuGetText(path) {
    return fetch(path, { method: 'GET', credentials: 'include' }).then(function (r) {
        if (!r.ok) throw new Error('请求失败（HTTP ' + r.status + '）');
        return r.text();
    });
}

// 解析课表页内嵌的学期下拉
function ahuExtractSemesters(html) {
    var m = html.match(/<select[^>]*id="allSemesters"[^>]*>([\s\S]*?)<\/select>/);
    if (!m) return null;
    var opts = [];
    var re = /<option[^>]*value="([^"]*)"[^>]*>([^<]*)</g;
    var om;
    while ((om = re.exec(m[1])) !== null) {
        var val = String(om[1]).trim();
        var label = String(om[2]).trim();
        if (val && /^\d+$/.test(val)) opts.push({ value: val, label: label });
    }
    return opts.length ? opts : null;
}

// 解析 "1~9周 星期一 3~4节 磬苑校区 博学北楼B403 魏丕静"
// 返回 { weeks:[], day, startSection, endSection, position }；失败返回 null
function ahuParseScheduleText(text) {
    var t = String(text || '').replace(/ /g, ' ').trim();
    if (!t) return null;

    // 周次：1~9周 / 1-9周 / 7~17(单)周 / 10~14(双)周 / 3周
    var weeks = [];
    var isOdd = /[（(]\s*单\s*[）)]/.test(t);
    var isEven = /[（(]\s*双\s*[）)]/.test(t);
    var wm = t.match(/([\d~～\-—\-]+)(?:[（(][单双][）)])?周/);
    if (wm) {
        var seg = wm[1].split(/[~～\-—\-]/);
        var a = parseInt(seg[0], 10);
        var b = seg.length > 1 ? parseInt(seg[1], 10) : a;
        if (!isNaN(a) && !isNaN(b) && a >= 1 && b >= a) {
            for (var w = a; w <= b && w <= 32; w++) {
                if (isOdd && w % 2 === 0) continue;
                if (isEven && w % 2 === 1) continue;
                weeks.push(w);
            }
        }
    }
    if (!weeks.length) return null;

    // 星期
    var day = 0;
    var dmatch = t.match(/星期([一二三四五六日天])/);
    if (dmatch) {
        var dmap = { '一': 1, '二': 2, '三': 3, '四': 4, '五': 5, '六': 6, '日': 7, '天': 7 };
        day = dmap[dmatch[1]] || 0;
    }
    if (!day) return null;

    // 节次：3~4节 / 6-9节 / 3节
    var sm = t.match(/(\d+)\s*[~～\-—\-]?\s*(\d+)?\s*节/);
    if (!sm) return null;
    var start = parseInt(sm[1], 10);
    var end = sm[2] ? parseInt(sm[2], 10) : start;
    if (isNaN(start) || start < 1 || end < start) return null;

    // 地点：节次之后、教师名之前的部分
    var position = '';
    var secIdx = t.indexOf(sm[0]);
    var afterSection = secIdx >= 0 ? t.substring(secIdx + sm[0].length).trim() : '';
    if (afterSection) position = afterSection;

    return { weeks: weeks, day: day, startSection: start, endSection: end, position: position };
}

function ahuBuildBlocks(lessons) {
    var map = {};
    var order = [];
    for (var i = 0; i < lessons.length; i++) {
        var l = lessons[i];
        var course = l.course || {};
        var name = String(course.nameZh || l.nameZh || '').trim();
        if (!name) continue;

        var st = l.scheduleText || {};
        var full = (st.dateTimePlacePersonText && (st.dateTimePlacePersonText.textZh || st.dateTimePlacePersonText.text)) || '';
        var roomText = (st.roomSeatText && (st.roomSeatText.textZh || st.roomSeatText.text)) || '';
        var teacher = String(l.teacherAssignmentString || '').trim();
        var parsed = ahuParseScheduleText(full);
        if (!parsed) continue;

        var position = parsed.position || '';
        // 教师在文本尾部："... 地点 教师" → 剥掉尾部教师段
        if (teacher && position &&
            position.lastIndexOf(teacher) === position.length - teacher.length &&
            position.length > teacher.length) {
            position = position.substring(0, position.length - teacher.length).trim();
        }
        if (!position) position = roomText || '待定';

        var type = (course.courseType && course.courseType.nameZh) || (l.courseType && l.courseType.nameZh) || '';
        var key = [name, teacher, position, parsed.day, parsed.startSection, parsed.endSection].join('__');
        if (!map[key]) {
            map[key] = {
                name: name,
                teacher: teacher || '未安排',
                position: position,
                day: parsed.day,
                startSection: parsed.startSection,
                endSection: parsed.endSection,
                weeks: parsed.weeks.slice(),
                type: type
            };
            order.push(key);
        } else {
            for (var w = 0; w < parsed.weeks.length; w++) {
                if (map[key].weeks.indexOf(parsed.weeks[w]) === -1) map[key].weeks.push(parsed.weeks[w]);
            }
        }
    }

    var blocks = [];
    for (var k = 0; k < order.length; k++) {
        var b = map[order[k]];
        b.weeks.sort(function (x, y) { return x - y; });
        blocks.push({
            name: b.name,
            teacher: b.teacher,
            position: b.position,
            day: b.day,
            startSection: b.startSection,
            endSection: b.endSection,
            weeks: b.weeks,
            isLab: /实验/.test(b.type)
        });
    }
    return blocks;
}

function ahuRunImport() {
    var bridge = window.shangkeBridgePromise;
    return bridge.showAlert(
        '安徽大学教务导入',
        '请先完成 WebVPN / 统一认证登录，并进入「教务系统」。\n' +
        '推荐路径：登录后在教务系统打开「我的课表」（任意学期页面均可），再点「确定」导入。',
        '开始导入'
    ).then(function (ok) {
        if (!ok) { ahuToast('导入已取消'); return null; }

        // 1. 课表页（兼登录校验） → 学期列表
        return ahuGetText(AHU_CT_PATH).then(function (html) {
            if (html.indexOf('allSemesters') === -1 || /登入页面|统一身份认证|cas\/login/.test(html)) {
                throw new Error('尚未登录或未进入教务系统：请先登录 WebVPN，打开「教务系统」后再试');
            }
            var sems = ahuExtractSemesters(html);
            if (!sems) throw new Error('未在课表页找到学期数据');
            return sems;
        });
    }).then(function (sems) {
        if (!sems) return null;

        var labels = sems.map(function (s) { return s.label; });
        return bridge.showSingleSelection('选择学期', JSON.stringify(labels), 0)
            .then(function (idx) {
                if (idx === null || idx < 0 || idx >= sems.length) {
                    ahuToast('导入已取消');
                    return null;
                }
                return sems[idx];
            });
    }).then(function (sem) {
        if (!sem) return null;
        ahuToast('正在获取 ' + sem.label + ' 的课表...');

        // 2. 拉课表 JSON（bizTypeId=2 学生课表；dataId 空 = 当前登录学生）
        var url = AHU_CT_PATH + '/get-data?bizTypeId=2&semesterId=' + encodeURIComponent(sem.value) + '&dataId=';
        return ahuGetText(url).then(function (txt) {
            var data;
            try { data = JSON.parse(txt); } catch (e) { throw new Error('课表数据解析失败：' + (txt || '').slice(0, 80)); }
            if (data.message) throw new Error('接口返回错误：' + data.message);
            var lessons = data.lessons || [];
            var blocks = ahuBuildBlocks(lessons);
            if (!blocks.length) throw new Error('该学期没有解析到课程（lessons=' + lessons.length + '）');

            // 3. 保存
            return window.shangkeBridgePromise.saveImportedCourses(JSON.stringify(blocks))
                .then(function () {
                    return ahuAlert(
                        '导入完成',
                        '成功导入 ' + blocks.length + ' 个课程项（' + sem.label + '）。\n' +
                        '课程按节次导入；如上下课时间与学校作息不符，请到「设置 → 自定义时间段」调整。',
                        '完成'
                    );
                })
                .then(function () {
                    window.shangkeBridge.notifyTaskCompletion();
                });
        });
    }).catch(function (e) {
        ahuToast('导入失败：' + (e && e.message ? e.message : e));
    });
}

window.shangkeImportEntry = ahuRunImport;
window.ahuImport = ahuRunImport;
