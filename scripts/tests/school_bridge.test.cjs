const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../..');
const protocol = fs.readFileSync(path.join(root,
  'shared/src/commonMain/kotlin/com/shangkeschedule/ui/schoolselection/web/WebBridgeProtocol.kt'), 'utf8');
function bridgeSource(name) {
  const match = protocol.match(new RegExp('val ' + name + ' = """([\\s\\S]*?)"""\\.trimIndent'));
  assert.ok(match, '必须使用当前 App 的实际桥接与自启动脚本');
  return match[1].replaceAll("${'$'}", '$');
}

for (const school of ['WHIT', 'HBNU']) {
  test(school + ' 通过当前 App 真实桥接和自启动流程发送一次课程数组并完成', async () => {
    const messages = [];
    const window = {
      location: new URL(school === 'WHIT' ? 'https://casp.whit.edu.cn/index.html#/hall'
        : 'https://jwxt.hbnu.edu.cn/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N253508'),
      addEventListener() {},
      __shangkeImportTriggered: false,
      __shangkeImportActive: true,
    };
    window._shangkeNativeBridge = {postMessage(text) {
      const message = JSON.parse(text);
      message.payload = JSON.parse(message.payload || '{}');
      messages.push(message);
      if (message.callbackId) queueMicrotask(() => window._shangkeNativeCallback(message.callbackId, true,
        message.action === 'showPrompt' ? message.payload.defaultText : 'true'));
    }};
    const document = {body:{innerText:''},getElementById:id=>({value:id==='xnm'?'2026':'3'}),querySelectorAll:()=>[]};
    window.document = document;
    window.performance = {getEntriesByType:()=>[]};
    class Xhr {
      open() {} setRequestHeader() {}
      send() { queueMicrotask(() => {
        this.status=200;this.readyState=4;
        this.responseText=JSON.stringify({kbList:[{kcmc:'课程甲',xm:'甲老师',xqj:'1',jcs:'1-2',zcd:'1-8周'}]});
        this.onreadystatechange();
      }); }
    }
    window.XMLHttpRequest = Xhr;
    const fetch = async (url,options) => ({ok:true,json:async()=>({errcode:'0',data:
      JSON.parse(options.body).method==='getWeek' ? {underInfo:{begDate:'2026-08-31'}}
        : {datas:{'2026-10-05':[{eventTitle:'课程甲',startTime:'2026-10-05T08:00:00',ext:{jc:'1-2'},isLessonTable:1}]}}})});
    const context = vm.createContext({window,document,performance:window.performance,XMLHttpRequest:Xhr,
      fetch,URL,AbortController,setTimeout,clearTimeout,console});
    vm.runInContext(bridgeSource('JS_BRIDGE_INIT'),context);
    vm.runInContext(fs.readFileSync(path.join(root,
      'shared/assets/offline_repo/schools/resources',school,school.toLowerCase()+'.js'),'utf8'),context);
    vm.runInContext(bridgeSource('JS_IMPORT_AUTOSTART'),context);
    await new Promise(resolve=>setTimeout(resolve,30));
    assert.equal(messages.filter(m=>m.action==='reportAdapterError').length,0);
    const saves=messages.filter(m=>m.action==='saveImportedCourses');
    assert.equal(saves.length,1);
    const courses=JSON.parse(saves[0].payload.coursesJsonString);
    assert.ok(Array.isArray(courses),'不能出现重复 JSON 编码');
    assert.equal(courses[0].name,'课程甲');
    assert.equal(messages.filter(m=>m.action==='notifyTaskCompletion').length,1);
  });
}
