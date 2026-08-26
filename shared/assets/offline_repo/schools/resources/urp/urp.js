// URP 教务系统通用适配器
(function() {
    'use strict';
    function extractText(html) { return html ? html.replace(/<[^>]+>/g, '').trim() : ''; }
    function cleanTeacher(name) { return name ? name.replace(/（[^）]*）/g, '').replace(/\([^)]*\)/g, '').trim() : ''; }
    function parseWeeks(s) {
        if (!s) return [];
        const weeks = new Set();
        s.split(/[,，、]/).forEach(seg => {
            const r = seg.match(/(\d+)\s*[-~]\s*(\d+)/);
            if (r) { for (let w = +r[1]; w <= +r[2]; w++) weeks.add(w); }
            else { const n = seg.match(/(\d+)/); if (n) weeks.add(+n[1]); }
        });
        return Array.from(weeks).sort((a,b) => a-b);
    }
    function isUrpHtmlPage() {
        return window.location.href.indexOf('jwgl') !== -1 || document.body.innerHTML.indexOf('URP') !== -1;
    }
    function fetchCourses() {
        if (!isUrpHtmlPage()) { Bridge.showToast('请先进入URP教务系统课表页面'); return; }
        // URP 通常是 HTML 表格，尝试从页面解析
        const table = document.getElementById('kbgrid_table_0') || document.querySelector('.kb-table') || document.querySelector('table[id*=kb]');
        if (!table) { Bridge.showToast('未找到课表表格，请确保在个人课表页面'); return; }
        const courses = [];
        const rows = table.querySelectorAll('tr');
        for (let r = 1; r < rows.length; r++) {
            const cells = rows[r].querySelectorAll('td');
            for (let c = 1; c < cells.length && c <= 7; c++) {
                const cell = cells[c];
                if (!cell || cell.getAttribute('rowspan') === '0') continue;
                const text = cell.innerText || cell.textContent;
                if (!text || text.trim() === '') continue;
                const lines = text.split('\n').map(l => l.trim()).filter(l => l);
                if (lines.length < 2) continue;
                const name = lines[0];
                const teacher = lines[1] ? cleanTeacher(lines[1]) : '';
                const position = lines[2] || '';
                const weekStr = lines.find(l => l.indexOf('周') !== -1) || '';
                const weeks = parseWeeks(weekStr);
                const startSection = (r - 1) * 2 + 1;
                const rowspan = parseInt(cell.getAttribute('rowspan')) || 1;
                const endSection = startSection + rowspan - 1;
                if (name && weeks.length > 0) {
                    courses.push({ name, teacher, position: position || '待定', day: c, startSection, endSection, weeks, remark: text });
                }
            }
        }
        if (courses.length === 0) { Bridge.showToast('未提取到有效课程'); return; }
        Bridge.saveImportedCourses(JSON.stringify(courses));
        Bridge.showToast('成功解析 ' + courses.length + ' 门课程');
    }
    window.urpImport = fetchCourses;
    if (isUrpHtmlPage()) Bridge.showToast('检测到URP教务系统，点击导入按钮抓取课表');
})();
