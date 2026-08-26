// 正方教务系统通用适配器
// 适配新版正方教务 (jwglxt)
// 使用 Bridge 方式与原生通信

(function() {
    'use strict';

    // 通用工具函数
    function extractText(html) {
        if (!html) return '';
        return html.replace(/<[^>]+>/g, '').trim();
    }

    function cleanTeacher(name) {
        if (!name) return '';
        return name.replace(/（[^）]*）/g, '').replace(/\([^)]*\)/g, '').trim();
    }

    function parseWeeks(weekStr) {
        if (!weekStr) return [];
        const weeks = new Set();
        const isOdd = /单周|单/.test(weekStr) && !/双/.test(weekStr);
        const isEven = /双周|双/.test(weekStr) && !/单/.test(weekStr);
        const cleaned = weekStr.replace(/周/g, '').replace(/第/g, '').replace(/[（(]单[）)]/g, '').replace(/[（(]双[）)]/g, '');
        cleaned.split(/[,，、]/).forEach(seg => {
            seg = seg.trim();
            if (!seg) return;
            const range = seg.match(/(\d+)\s*[-~至到]\s*(\d+)/);
            if (range) {
                const start = parseInt(range[1]), end = parseInt(range[2]);
                for (let w = Math.min(start, end); w <= Math.max(start, end); w++) {
                    if (isOdd && w % 2 === 0) continue;
                    if (isEven && w % 2 === 1) continue;
                    weeks.add(w);
                }
            } else {
                const single = seg.match(/(\d+)/);
                if (single) {
                    const w = parseInt(single[1]);
                    if (isOdd && w % 2 === 0) return;
                    if (isEven && w % 2 === 1) return;
                    weeks.add(w);
                }
            }
        });
        return Array.from(weeks).sort((a, b) => a - b);
    }

    // 检查是否在正方教务页面
    function isZhengfangPage() {
        return window.location.href.indexOf('jwglxt') !== -1 ||
               document.body.innerHTML.indexOf('正方教务') !== -1;
    }

    // 获取学年学期
    function getXnxq() {
        let xnm = '', xqm = '';
        const xnmSelect = document.getElementById('xnm');
        const xqmSelect = document.getElementById('xqm');
        if (xnmSelect) xnm = xnmSelect.value;
        if (xqmSelect) xqm = xqmSelect.value;
        if (!xnm) {
            const now = new Date();
            const year = now.getFullYear();
            const month = now.getMonth() + 1;
            if (month >= 9) { xnm = year.toString(); xqm = '3'; }
            else if (month >= 2) { xnm = (year - 1).toString(); xqm = '12'; }
            else { xnm = (year - 1).toString(); xqm = '3'; }
        }
        return { xnm, xqm };
    }

    // 从 API 获取课程数据
    function fetchCoursesFromApi() {
        if (!isZhengfangPage()) {
            Bridge.showToast('请先进入正方教务系统的课表查询页面');
            return;
        }

        const { xnm, xqm } = getXnxq();
        const basePath = window.location.pathname;
        let apiPath = '/jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N253508';
        const kbcxIdx = basePath.indexOf('/kbcx/');
        if (kbcxIdx !== -1) {
            apiPath = basePath.substring(0, kbcxIdx) + '/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N253508';
        }

        const xhr = new XMLHttpRequest();
        xhr.open('POST', apiPath, true);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
        xhr.withCredentials = true;
        xhr.onreadystatechange = function() {
            if (xhr.readyState === 4) {
                if (xhr.status === 200) {
                    try {
                        const resp = JSON.parse(xhr.responseText);
                        const list = resp.kbList || [];
                        if (list.length === 0) {
                            Bridge.showToast('未查询到课程数据');
                            return;
                        }
                        parseAndImport(list);
                    } catch (e) {
                        Bridge.showToast('解析课程数据失败: ' + e.message);
                    }
                } else {
                    Bridge.showToast('请求失败，状态码: ' + xhr.status);
                }
            }
        };
        xhr.send('xnm=' + xnm + '&xqm=' + xqm);
    }

    // 解析并导入课程
    function parseAndImport(kbList) {
        const courses = [];
        kbList.forEach(item => {
            const name = extractText(item.kcmc);
            const teacher = cleanTeacher(extractText(item.tmc));
            const position = extractText(item.cdmc) || '待定';
            const day = parseInt(item.xqj);
            const startSection = parseInt(item.djj);
            const endSection = startSection + parseInt(item.cs) - 1;
            const weeks = parseWeeks(item.zcd);

            if (!name || isNaN(day) || day < 1 || day > 7 || isNaN(startSection)) return;

            courses.push({
                name: name,
                teacher: teacher,
                position: position,
                day: day,
                startSection: startSection,
                endSection: endSection,
                weeks: weeks,
                remark: item.xm || ''
            });
        });

        if (courses.length === 0) {
            Bridge.showToast('未提取到有效课程');
            return;
        }

        Bridge.saveImportedCourses(JSON.stringify(courses));
        Bridge.showToast('成功解析 ' + courses.length + ' 门课程，正在导入...');
    }

    // 暴露给页面调用
    window.zhengfangImport = fetchCoursesFromApi;

    // 自动检测并提示
    if (isZhengfangPage()) {
        Bridge.showToast('检测到正方教务系统，点击导入按钮抓取课表');
    }
})();
