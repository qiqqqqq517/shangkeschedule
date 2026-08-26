import { parseWeeks } from "../parser.js";

export default {
    id: "kingosoft_new",
    name: "金智教务 (新版)",
    provider: `
        (function() {
            try {
                // 判断是否是含有 API 请求直返信息的 JSON
                if (document.body.innerText.indexOf('kcbxx') !== -1) {
                    document.title = 'KEBIAO_OK:JSON|' + btoa(encodeURIComponent(document.body.innerText));
                    return;
                }

                // 按目前主流新金智，表格渲染会在包含 'jsxsd' 的特定 ID div 或者 table 内
                var table = document.getElementById('kbtable') || document.querySelector('table.scheduleTable');
                if (!table) return void (document.title = 'KEBIAO_ERR:未能找到新版金智课表区域');

                var courses = [];
                var trs = table.getElementsByTagName('tr');
                for (var i = 1; i < trs.length; i++) {
                    var tds = trs[i].getElementsByTagName('td');
                    for (var j = 0; j < tds.length; j++) {
                        var td = tds[j];
                        var html = td.innerHTML.trim();
                        if (html === '' || html === '&nbsp;') continue;

                        var sections = html.split(/<br(?:\\s*\\/?)>|<hr>/i);
                        var currentCourse = [];
                        for (var k = 0; k < sections.length; k++) {
                            var text = sections[k].replace(/<[^>]+>/g, '').trim();
                            if (text) currentCourse.push(text);
                            
                            // 新版的字段基本是：课名, 教师周次教室，或 JSON 直下
                            // 粗略处理：
                            if (currentCourse.length >= 3 && (k === sections.length - 1 || sections[k] === '')) {
                                var day = td.cellIndex || j;
                                courses.push([
                                    currentCourse[0], 
                                    day, 
                                    (i * 2 - 1) + '-' + (i * 2), // 取决于行的代表节次数
                                    currentCourse[2] || '', 
                                    currentCourse[1] || '',
                                    currentCourse[3] || ''
                                ].join('~'));
                                currentCourse = [];
                            }
                        }
                    }
                }
                
                document.title = courses.length ? 'KEBIAO_OK:HTML|' + courses.join('|') : 'KEBIAO_ERR:未能提取新版金智课程';
            } catch(e) { document.title = 'KEBIAO_ERR:' + e.message; }
        })();
    `,
    parser: function(compactStr) {
        if (compactStr.startsWith('JSON|')) {
            return []; // 留给有实际数据结构时改写
        }
        
        compactStr = compactStr.replace('HTML|', '');
        const items = compactStr.split("|");
        const courses = [];
        items.forEach((item, index) => {
            const parts = item.split("~");
            if (parts.length < 3) return;
            const [name, dayStr, jcsStr, room, weeksStr, teacher] = parts;
            const jcsMatch = jcsStr.match(/(\d+)-(\d+)/);
            let startNode = 1, endNode = 2;
            if (jcsMatch) {
                startNode = parseInt(jcsMatch[1]);
                endNode = parseInt(jcsMatch[2]);
            }
            courses.push({
                id: index + 1, name, day: parseInt(dayStr) || 1, startNode, endNode,
                room: room || "", teacher: teacher || "",
                weeks: parseWeeks(weeksStr || ""), weeksText: weeksStr || ""
            });
        });
        return courses;
    }
}
