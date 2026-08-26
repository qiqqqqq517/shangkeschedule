import { parseWeeks } from "../parser.js";

import { DOM_MATRIX_HELPER } from "./dom_matrix_helper.js";

export default {
    id: "kingosoft",
    name: "金智教务系统",
    provider: `
        (function() {
            ${DOM_MATRIX_HELPER}
            try {
                var table = document.getElementById('kbtable') || document.querySelector('table.kbtable');
                if (!table) {
                    var tables = document.getElementsByTagName('table');
                    for (var i = 0; i < tables.length; i++) {
                        if (tables[i].innerHTML.indexOf('星期') !== -1) {
                            table = tables[i];
                            break;
                        }
                    }
                }
                if (!table) return void (document.title = 'KEBIAO_ERR:未查找到金智课表表格');

                var grid = parseTableToGrid(table);
                var courses = [];
                
                // 从第 1 行开始（跳过表头行），通常 1-7 列为星期一到日
                for (var r = 1; r < grid.length; r++) {
                    if (!grid[r]) continue;
                    for (var c = 1; c <= 7; c++) {
                        var cell = grid[r][c];
                        if (!cell || !cell.isOrigin || cell.html === '' || cell.html === '&nbsp;') continue;
                        
                        var sections = cell.html.split(/<br(?:\\s*\\/?)>|-----------------------/g);
                        var currentCourse = [];
                        
                        for (var k = 0; k < sections.length; k++) {
                            var text = sections[k].replace(/<[^>]+>/g, '').trim();
                            if (text) currentCourse.push(text);
                            
                            if (currentCourse.length >= 3 && (k === sections.length - 1 || sections[k] === '')) {
                                var cName = currentCourse[0];
                                var cTeacher = '', cWeeks = '', cRoom = '';
                                
                                for (var p = 1; p < currentCourse.length; p++) {
                                    var item = currentCourse[p];
                                    if (item.indexOf('周') !== -1) cWeeks = item;
                                    else if (item.indexOf('教师') !== -1 || currentCourse.length === 3 && p === 1) cTeacher = item;
                                    else cRoom = item;
                                }
                                
                                courses.push([
                                    cName.replace(/[~|]/g, ' '),
                                    c, // 矩阵的列天然精确等于星期 
                                    (r * 2 - 1) + '-' + ((r + cell.rowspan - 1) * 2),
                                    cRoom.replace(/[~|]/g, ' '),
                                    cWeeks.replace(/[~|]/g, ' '),
                                    cTeacher.replace(/[~|]/g, ' ')
                                ].join('~'));
                                
                                currentCourse = [];
                            }
                        }
                    }
                }
                
                document.title = courses.length ? 'KEBIAO_OK:' + courses.join('|') : 'KEBIAO_ERR:未能提取到格式正确的课程数据';
            } catch(e) { document.title = 'KEBIAO_ERR:' + e.message; }
        })();
    `,
    parser: function(compactStr) {
        const items = compactStr.split("|");
        const courses = [];
        let id = 0;
        for (const item of items) {
            const parts = item.split("~");
            if (parts.length < 3) continue;
            const [name, dayStr, jcsStr, room, weeksStr, teacher] = parts;
            const day = parseInt(dayStr);
            if (!day || day < 1 || day > 7) continue;

            const jcsMatch = jcsStr.match(/(\d+)-(\d+)/);
            let startNode = 1, endNode = 2;
            if (jcsMatch) {
                startNode = parseInt(jcsMatch[1]);
                endNode = parseInt(jcsMatch[2]);
            }

            const weeks = parseWeeks(weeksStr || "");
            
            courses.push({
                id: ++id,
                name: name || "未知课程",
                day,
                startNode,
                endNode,
                room: room || "",
                teacher: teacher || "",
                weeks,
                weeksText: weeksStr || ""
            });
        }
        return courses;
    }
}
