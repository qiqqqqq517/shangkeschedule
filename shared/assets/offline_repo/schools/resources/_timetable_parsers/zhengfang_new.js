import { parseWeeks } from "../parser.js";

export default {
    id: "zhengfang_new",
    name: "正方教务系统 (新版 jwglxt)",
    
    // provider代码将在 web-view 中的目标网页上下文环境中执行
    provider: `
        (function() {
            try {
                var xnm = '';
                var xqm = '';
                var xnmSelect = document.getElementById('xnm');
                var xqmSelect = document.getElementById('xqm');
                if (xnmSelect) xnm = xnmSelect.value;
                if (xqmSelect) xqm = xqmSelect.value;

                if (!xnm) {
                    var now = new Date();
                    var year = now.getFullYear();
                    var month = now.getMonth() + 1;
                    if (month >= 9) { xnm = year.toString(); xqm = '3'; }
                    else if (month >= 2) { xnm = (year - 1).toString(); xqm = '12'; }
                    else { xnm = (year - 1).toString(); xqm = '3'; }
                }

                // 检查是否在教务系统页面
                if (window.location.href.indexOf('jwglxt') === -1) {
                    document.title = 'KEBIAO_ERR:请先进入教务系统的课表查询页面后再点击导入';
                    return;
                }

                // 构建 API 路径
                var basePath = window.location.pathname;
                var kbcxIdx = basePath.indexOf('/kbcx/');
                var apiPath = '';
                if (kbcxIdx !== -1) {
                    apiPath = basePath.substring(0, kbcxIdx) + '/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N253508';
                } else {
                    var jwIdx = basePath.indexOf('/jwglxt/');
                    if (jwIdx !== -1) {
                        apiPath = basePath.substring(0, jwIdx) + '/jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N253508';
                    } else {
                        apiPath = '/jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N253508';
                    }
                }

                var xhr = new XMLHttpRequest();
                xhr.open('POST', apiPath, true);
                xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
                xhr.withCredentials = true;
                xhr.onreadystatechange = function() {
                    if (xhr.readyState === 4) {
                        if (xhr.status === 200) {
                            try {
                                var resp = JSON.parse(xhr.responseText);
                                var list = resp.kbList || [];
                                if (list.length === 0) {
                                    document.title = 'KEBIAO_ERR:未查询到课程数据';
                                    return;
                                }
                                // 紧凑格式: name~day~jcs~room~weeks~teacher 用 | 分隔课程
                                var parts = [];
                                for (var i = 0; i < list.length; i++) {
                                    var c = list[i];
                                    parts.push([
                                        (c.kcmc || '').replace(/[~|]/g, ' '),
                                        c.xqj || '',
                                        (c.jcs || '').replace(/[~|]/g, ' '),
                                        (c.cdmc || '').replace(/[~|]/g, ' '),
                                        (c.zcd || '').replace(/[~|]/g, ' '),
                                        (c.xm || '').replace(/[~|]/g, ' ')
                                    ].join('~'));
                                }
                                document.title = 'KEBIAO_OK:' + parts.join('|');
                            } catch(pe) {
                                document.title = 'KEBIAO_ERR:数据解析失败 ' + pe.message;
                            }
                        } else {
                            document.title = 'KEBIAO_ERR:请求失败 (HTTP ' + xhr.status + ')';
                        }
                    }
                };
                xhr.onerror = function() {
                    document.title = 'KEBIAO_ERR:网络请求失败';
                };
                xhr.send('xnm=' + xnm + '&xqm=' + xqm);
            } catch(e) {
                document.title = 'KEBIAO_ERR:' + e.message;
            }
        })();
    `,
    
    // parser代码在 uniapp 环境中执行，用于将 provider 回传的数据解析为标准课程数组对象
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
            if (!jcsMatch) continue;
            const startNode = parseInt(jcsMatch[1]);
            const endNode = parseInt(jcsMatch[2]);

            // 解析周次
            const weeks = parseWeeks(weeksStr || "");

            courses.push({
                id: id++,
                name: name || "",
                day,
                startNode,
                endNode,
                room: room || "",
                teacher: teacher || "",
                weeks,
                weeksText: weeksStr || "",
            });
        }
        return courses;
    }
}
