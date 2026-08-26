import { parseWeeks } from "../parser.js";

// 超星教务/超星分享导入解析器
// 常用于以超星学习通为载体或被超星收购的教务服务
export default {
    id: "chaoxing",
    name: "超星教务系统",
    provider: `
        (function() {
            try {
                // 超星大部分走移动端接口或 Vue 网页，含有特有的样式类，例如 .course-item 等
                if (document.body.innerText.indexOf('courseName') !== -1 && document.body.innerText.indexOf('classRoom') !== -1) {
                    // 很可能是进入了单纯的 API 返回页
                    document.title = 'KEBIAO_OK:JSON|' + btoa(encodeURIComponent(document.body.innerText));
                    return;
                }

                // 退回常规 DOM 抓取：如果他们渲染了一个包含星期列表的 wrapper
                var courses = [];
                var items = document.querySelectorAll('.course-item, .kc-item, .schedule-item'); 
                if (items.length > 0) {
                    for (var i = 0; i < items.length; i++) {
                        var textNodes = Array.from(items[i].childNodes)
                            .map(n => n.textContent ? n.textContent.trim() : '')
                            .filter(t => t);
                        // 自行结合超星 class 推导坐标... 预留泛型读取
                        courses.push([
                            textNodes[0] || '超星课程',
                            1, // 需按坐标计算
                            '1-2', // 需按坐标计算
                            textNodes[2] || '', // 教室
                            textNodes[1] || '', // 周次
                            textNodes[3] || ''  // 老师
                        ].join('~'));
                    }
                } else {
                    // 后备用老式表格查找
                    var table = document.getElementsByTagName('table')[0];
                    if (!table) return void (document.title = 'KEBIAO_ERR:未识别到超星课程列表');
                    // ...（此处省略同类型的 fallback，简化处理保证不阻断）...
                    return void (document.title = 'KEBIAO_ERR:超星 DOM 需针对不同高校结构微调适配');
                }
                
                document.title = courses.length ? 'KEBIAO_OK:HTML|' + courses.join('|') : 'KEBIAO_ERR:未解析到超星课程';
            } catch(e) { document.title = 'KEBIAO_ERR:' + e.message; }
        })();
    `,
    parser: function(compactStr) {
        if (compactStr.startsWith('JSON|')) return [];
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
                id: index + 1, name: name || "超星课程", day: parseInt(dayStr) || 1, startNode, endNode,
                room: room || "", teacher: teacher || "",
                weeks: parseWeeks(weeksStr || ""), weeksText: weeksStr || ""
            });
        });
        return courses;
    }
}
