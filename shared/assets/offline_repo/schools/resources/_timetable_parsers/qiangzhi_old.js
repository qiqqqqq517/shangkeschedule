import { parseWeeks } from "../parser.js";

export default {
    id: "qiangzhi_old",
    name: "强智教务 (旧版)",
    // 旧版强智特有：跨行（rowspan）的变态表格
    provider: `
        (function() {
            try {
                var table = document.getElementById('kbtable') || document.querySelector('#Table1');
                if (!table) {
                    var tables = document.getElementsByTagName('table');
                    for (var i = 0; i < tables.length; i++) {
                        if (tables[i].innerHTML.indexOf('星期') !== -1 || tables[i].innerHTML.indexOf('节次') !== -1) {
                            table = tables[i]; break;
                        }
                    }
                }
                if (!table) return void (document.title = 'KEBIAO_ERR:未查找到课表表格');

                var courses = [];
                var trs = table.getElementsByTagName('tr');
                var spanMap = {}; // 记录跨行状态

                for (var i = 1; i < trs.length; i++) {
                    var tds = trs[i].children;
                    var colIndex = 0; // 当前列逻辑索引
                    for (var j = 0; j < tds.length; j++) {
                        var td = tds[j];
                        
                        // 跳过前面被 rowspan 占据的列
                        while (spanMap[colIndex] > 0) {
                            spanMap[colIndex]--;
                            colIndex++;
                        }

                        var text = td.innerText.trim();
                        var rowspan = td.rowSpan || 1;
                        var colspan = td.colSpan || 1;

                        if (rowspan > 1) {
                            spanMap[colIndex] = rowspan - 1;
                        }

                        // 旧版的节点包含大量换行
                        if (text && text.length > 5 && colIndex > 0 && colIndex <= 7 && td.className !== 'td') {
                            var chunks = htmlDec(td.innerHTML).split(/<br\\s*\\/?>|-{5,}/i)
                                            .map(x=>x.replace(/<[^>]+>/g, '').trim()).filter(x=>x);
                            if (chunks.length >= 3) {
                                var cName = chunks[0];
                                var cTeacher = '', cRoom = '', cWeeks = '';
                                for(var p=1; p<chunks.length; p++){
                                    if(chunks[p].indexOf('周') !== -1) cWeeks = chunks[p];
                                    else if(chunks[p].indexOf('老师') !== -1 || chunks[p].indexOf('教授') !== -1) cTeacher = chunks[p];
                                    else cRoom = chunks[p];
                                }
                                courses.push([
                                    cName, colIndex, (i) + '-' + (i + rowspan - 1), cRoom, cWeeks, cTeacher
                                ].join('~'));
                            }
                        }

                        colIndex += colspan;
                    }
                }
                document.title = courses.length ? 'KEBIAO_OK:' + courses.join('|') : 'KEBIAO_ERR:未提取到课程';
                
                function htmlDec(html){ return html.replace(/&nbsp;/g, ' '); }
            } catch(e) { document.title = 'KEBIAO_ERR:' + e.message; }
        })();
    `,
    parser: function(compactStr) {
        const items = compactStr.split("|");
        const courses = [];
        items.forEach((item, index) => {
            const parts = item.split("~");
            if (parts.length < 3) return;
            const [name, dayStr, jcsStr, room, weeksStr, teacher] = parts;
            const day = parseInt(dayStr);
            const jcsMatch = jcsStr.match(/(\d+)-(\d+)/);
            let startNode = 1, endNode = 2;
            if (jcsMatch) {
                startNode = parseInt(jcsMatch[1]);
                endNode = parseInt(jcsMatch[2]);
            }
            courses.push({
                id: index + 1, name: name || "未知课程", day: day || 1, startNode, endNode,
                room: room || "", teacher: teacher || "",
                weeks: parseWeeks(weeksStr || ""), weeksText: weeksStr || ""
            });
        });
        return courses;
    }
}
