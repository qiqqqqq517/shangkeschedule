// 金智(Wisedu)教务系统通用适配器
(function() {
    'use strict';
    function extractText(h) { return h ? h.replace(/<[^>]+>/g, '').trim() : ''; }
    function cleanTeacher(n) { return n ? n.replace(/（[^）]*）/g, '').replace(/\([^)]*\)/g, '').trim() : ''; }
    function parseWeeks(s) {
        if (!s) return [];
        const w = new Set();
        s.split(/[,，、]/).forEach(seg => {
            const r = seg.match(/(\d+)\s*[-~]\s*(\d+)/);
            if (r) { for (let i = +r[1]; i <= +r[2]; i++) w.add(i); }
            else { const n = seg.match(/(\d+)/); if (n) w.add(+n[1]); }
        });
        return Array.from(w).sort((a,b) => a-b);
    }
    function isWiseduPage() {
        return window.location.href.indexOf('wisedu') !== -1 ||
               window.location.href.indexOf('jwapp') !== -1 ||
               document.body.innerHTML.indexOf('金智') !== -1;
    }
    function fetchCourses() {
        if (!isWiseduPage()) { Bridge.showToast('请先进入金智教务系统课表页面'); return; }
        // 金智新版通常是 JSON API
        const table = document.getElementById('kbTable') || document.querySelector('.kb_tb') || document.querySelector('.scheduleTable');
        if (table) {
            parseHtmlTable(table);
        } else {
            // 尝试从页面 JSON 数据提取
            const scripts = document.querySelectorAll('script');
            let jsonData = null;
            scripts.forEach(s => {
                const m = s.textContent.match(/var\s+\w+\s*=\s*(\{.*?kcbxx.*?\})/s);
                if (m) { try { jsonData = JSON.parse(m[1]); } catch(e) {} }
            });
            if (jsonData) {
                parseJsonData(jsonData);
            } else {
                Bridge.showToast('未找到课表数据，请确保在个人课表页面');
            }
        }
    }
    function parseHtmlTable(table) {
        const courses = [];
        const rows = table.querySelectorAll('tr');
        for (let r = 1; r < rows.length; r++) {
            const cells = rows[r].querySelectorAll('td');
            for (let c = 1; c < cells.length && c <= 7; c++) {
                const cell = cells[c];
                if (!cell || !cell.innerText.trim()) continue;
                const sections = cell.innerHTML.split(/<hr\s*\/?>|<br\s*\/?>=============/i);
                sections.forEach(sec => {
                    const lines = sec.split(/<br\s*\/?>/i).map(l => extractText(l)).filter(l => l);
                    if (lines.length >= 3) {
                        const name = lines[0];
                        let teacher = '', position = '', weekStr = '';
                        for (let i = 1; i < lines.length; i++) {
                            if (lines[i].indexOf('周') !== -1) weekStr = lines[i];
                            else if (lines[i].indexOf('老师') !== -1 || lines[i].length <= 3) teacher = cleanTeacher(lines[i]);
                            else position = lines[i];
                        }
                        const weeks = parseWeeks(weekStr);
                        const rs = parseInt(cell.getAttribute('rowspan')) || 1;
                        const start = (r - 1) * 2 + 1;
                        if (name && weeks.length > 0) {
                            courses.push({ name, teacher, position: position || '待定', day: c, startSection: start, endSection: start + rs * 2 - 1, weeks, remark: sec });
                        }
                    }
                });
            }
        }
        if (courses.length === 0) { Bridge.showToast('未提取到有效课程'); return; }
        Bridge.saveImportedCourses(JSON.stringify(courses));
        Bridge.showToast('成功解析 ' + courses.length + ' 门课程');
    }
    function parseJsonData(data) {
        Bridge.showToast('检测到JSON数据格式，请联系开发者适配');
    }
    window.wiseduImport = fetchCourses;
    if (isWiseduPage()) Bridge.showToast('检测到金智教务系统');
})();
