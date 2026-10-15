const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.resolve(__dirname,
  '../../shared/assets/offline_repo/schools/resources/HBNU/hbnu.js'), 'utf8');
const lesson = (extra = {}) => ({ kcmc: '课程甲', xm: '甲老师（讲师）', cdmc: 'A101',
  xqj: '1', jcs: '1-2', zcd: '1-8,10-16(双)', ...extra });

function harness({ rows = [lesson()], table = null, child = null, save = true,
  url = 'https://jwxt.hbnu.edu.cn/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N253508',
  timeout = false, responseText, status = 200, resources = [] } = {}) {
  const calls = { requests: [], saved: [], toasts: [], completed: 0 };
  const document = {
    body: { innerText: '' },
    getElementById(id) { return ({ xnm: { value: '2026' }, xqm: { value: '3' } })[id] || null; },
    querySelectorAll(selector) {
      if (selector === 'table') return table ? [table] : [];
      if (selector === 'iframe') return child ? [{ contentDocument: child.document, contentWindow: child.window }] : [];
      return [];
    },
  };
  class Xhr {
    open(method, requestUrl) { this.request = { method, url: requestUrl }; }
    setRequestHeader() {}
    send(body) {
      calls.requests.push({ ...this.request, body, timeout: this.timeout });
      queueMicrotask(() => {
        if (timeout) { this.ontimeout?.(); return; }
        this.status = status;
        this.readyState = 4;
        this.responseText = responseText ?? JSON.stringify({ kbList: rows });
        this.onreadystatechange?.();
        this.onload?.();
      });
    }
  }
  const window = { location: new URL(url), document, setTimeout,
    performance: { getEntriesByType: () => resources }, XMLHttpRequest: Xhr,
    shangkeBridgePromise: {
      saveImportedCourses(json) {
        calls.saved.push(JSON.parse(json));
        return save instanceof Error ? Promise.reject(save) : Promise.resolve(save);
      },
      showAlert: async () => true,
    },
    shangkeBridge: {
      showToast: text => calls.toasts.push(text),
      notifyTaskCompletion: () => calls.completed++,
    },
  };
  const context = vm.createContext({ window, document, XMLHttpRequest: Xhr, URL,
    performance: window.performance, setTimeout, clearTimeout, console });
  vm.runInContext(source, context);
  return { window, document, calls, async run() {
    await window.shangkeImportEntry();
    await new Promise(resolve => setTimeout(resolve, 15));
    return calls;
  } };
}

function grid(text) {
  const cell = value => ({ innerText: value, textContent: value, getAttribute: () => null });
  return {
    innerText: '节次 星期一 星期二 星期三 星期四 星期五 星期六 星期日 上午 周',
    rows: [
      { cells: ['节次', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日'].map(cell) },
      { cells: ['1', text, '', '', '', '', '', ''].map(cell) },
    ],
  };
}

test('HBNU 保存结果等待成功后完成任务，并且课程只编码一次', async () => {
  const h = harness();
  const pending = h.window.shangkeImportEntry();
  assert.equal(typeof pending?.then, 'function', '入口应返回整条导入链的 Promise');
  await pending;
  assert.equal(h.calls.saved.length, 1);
  assert.equal(h.calls.saved[0][0].teacher, '甲老师');
  assert.deepEqual(h.calls.saved[0][0].weeks, [1,2,3,4,5,6,7,8,10,12,14,16]);
  assert.equal(h.calls.completed, 1);
});

test('HBNU 保存失败不能提示导入成功或触发离开页面', async () => {
  for (const save of [false, new Error('保存失败')]) {
    const h = harness({ save });
    await h.run();
    assert.equal(h.calls.completed, 0);
    assert.ok(h.calls.toasts.some(text => /保存.*(失败|未成功)/.test(text)));
    assert.ok(!h.calls.toasts.some(text => /成功解析/.test(text)));
  }
});

test('HBNU 优先整学期接口，避免当前周 DOM 漏掉其他周课程', async () => {
  const h = harness({ table: grid('当前周课程\n1-2节\n5周\n乙老师'), rows: [lesson(), lesson({kcmc:'其他周课程',zcd:'9周'})] });
  const calls = await h.run();
  assert.equal(calls.saved[0].length, 2);
  assert.ok(calls.saved[0].some(row => row.name === '其他周课程'));
});

test('HBNU 页面无周次时继续读取有效接口，兼容 v4.75.14 起的空周次拦截', async () => {
  const h = harness({ table: grid('课程甲\n1-2节\n甲老师'), rows: [lesson()] });
  const calls = await h.run();
  assert.equal(calls.saved.length, 1);
  assert.ok(calls.saved[0].every(course => course.weeks.length > 0));
  assert.equal(calls.completed, 1);
});

test('HBNU 位于门户同源 iframe 中时使用课表窗口的路径与学期', async () => {
  const child = harness({url:'https://ehall.hbnu.edu.cn/proxy/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151'});
  const parent = harness({ url: 'https://ehall.hbnu.edu.cn/new/index.html', child });
  await parent.run();
  const request = parent.calls.requests[0] || child.calls.requests[0];
  assert.ok(request, '必须在识别到的课表上下文发送请求');
  assert.match(request.url, /\/proxy\/kbcx\/xskbcx_cxXsgrkb.html\?gnmkdm=N2151/);
  assert.match(request.body, /xnm=2026&xqm=3/);
  assert.equal(parent.calls.saved.length, 1);
});

test('HBNU API 超时后结束请求并提示重试，不能无限等待', async () => {
  const h = harness({ timeout: true });
  const calls = await h.run();
  assert.ok(calls.requests.every(r => r.timeout > 0 && r.timeout <= 30000));
  assert.ok(calls.toasts.some(text => /超时/.test(text)));
  assert.equal(calls.saved.length, 0);
});

test('HBNU API 失败时不保存没有周次的 DOM 课程', async () => {
  const h = harness({ status: 502, table: grid('课程甲\n1-2节\n甲老师') });
  const calls = await h.run();
  assert.equal(calls.saved.length, 0);
});

test('HBNU 相同课程合并周次并去重，连续节次合并', async () => {
  const h = harness({ rows: [lesson({zcd:'1-4周'}), lesson({zcd:'3-6周'}),
    lesson({zcd:'1-6周',jcs:'3-4'}), lesson({zcd:'1-4周'})] });
  const calls = await h.run();
  assert.equal(calls.saved[0].length, 1);
  assert.equal(calls.saved[0][0].endSection, 4);
  assert.deepEqual(calls.saved[0][0].weeks, [1,2,3,4,5,6]);
});

test('HBNU 登录失效时给出登录引导，不保存 HTML 登录页', async () => {
  const h = harness({ responseText: '<html>统一身份认证 登录</html>' });
  const calls = await h.run();
  assert.equal(calls.saved.length, 0);
  assert.ok(calls.toasts.some(text => /登录/.test(text)));
});

test('HBNU 部分接口记录缺周次时整体报错，避免漏课后提示成功', async () => {
  const calls = await harness({ rows: [lesson(), lesson({ kcmc:'课程乙',zcd:'' })] }).run();
  assert.equal(calls.saved.length, 0);
  assert.ok(calls.toasts.some(text => /周次|不完整/.test(text)));
});
