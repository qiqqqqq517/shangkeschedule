// HBNU：页面库污染 Array.prototype 时脚本仍须正确解析（防 (index,value,array) 参数序翻转）。
// 背景：zhengfang.js 同源问题已在通用脚本修掉；hbnu.js 2026-10-28 补齐。
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.resolve(__dirname,
  '../../shared/assets/offline_repo/schools/resources/HBNU/hbnu.js'), 'utf8');

// 与 HBNU 真实接口同构：两行课程（一行 zcd 正常、一行 zcd 为空）。
const kbList = {
  kbList: [
    { kcmc: '高等数学', xm: '张老师', cdmc: '教育大楼A101', xqj: '1', jcs: '1-2节', zcd: '1-16周' },
    { kcmc: '军训', xm: '', cdmc: '', xqj: '', jcs: '', zcd: '' },
  ],
};

function harness({ href, kb = kbList, polluted }) {
  const calls = { saved: [], alerts: [], toasts: [] };
  const location = { href, hostname: href.includes('jwxt') ? 'jwxt.hbnu.edu.cn' : 'ehall.hbnu.edu.cn',
    protocol: 'https:', search: '', pathname: '/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151' };
  const docs = [];
  const fakeFetch = (url, options = {}) => {
    if (/xskbcx_cxXsgrkb\.html/i.test(String(url))) {
      return Promise.resolve({ status: 200, text: () => Promise.resolve(JSON.stringify(kb)) });
    }
    return Promise.resolve({ status: 404, text: () => Promise.resolve('') });
  };
  const window = {
    location,
    document: { getElementById: () => null, querySelectorAll: () => [] },
    fetch: fakeFetch,
    performance: { getEntriesByType: () => [] },
    shangkeBridgePromise: {
      saveImportedCourses(json) { calls.saved.push(JSON.parse(json)); return Promise.resolve(true); },
      showAlert(title, content) { calls.alerts.push({ title, content }); return Promise.resolve(true); },
    },
    shangkeBridge: { showToast: t => calls.toasts.push(t), notifyTaskCompletion: () => { calls.done = 1; } },
    setTimeout, clearTimeout,
  };
  // hbnu.js 走 XMLHttpRequest（不是 fetch）：提供最小可用 mock。
  function FakeXHR() {
    this.readyState = 0;
    this.status = 0;
    this.responseText = '';
    this._url = '';
  }
  FakeXHR.prototype.open = function (method, url) { this._url = String(url); };
  FakeXHR.prototype.setRequestHeader = function () {};
  FakeXHR.prototype.getAllResponseHeaders = function () { return ''; };
  FakeXHR.prototype.send = function () {
    const self = this;
    setTimeout(() => {
      self.readyState = 4;
      self.status = 200;
      self.responseText = JSON.stringify(kbList);
      if (typeof self.onreadystatechange === 'function') self.onreadystatechange();
      if (typeof self.onload === 'function') self.onload();
    }, 0);
  };
  window.XMLHttpRequest = FakeXHR;
  const ctx = vm.createContext({ window, document: window.document, fetch: fakeFetch,
    XMLHttpRequest: FakeXHR,
    performance: window.performance, URL, Date, JSON, Promise, setTimeout, clearTimeout, console });
  vm.runInContext(source, ctx);
  if (polluted) {
    // 只污染该 vm 上下文自己的 Array（不动宿主 Node 的 Array.prototype）。
    // 时序必须与线上一致：脚本先在页面上注入（此时备份到原生实现），
    // 之后正方页面库（zftal 等）才覆盖 filter/some，并把回调参数序翻转成 (index, value, array)。
    // 若污染发生在脚本注入**之前**，备份拿到的就是污染版——这是与 zhengfang.js 相同的已知前提，
    // 因此本用例只覆盖「页面库污染晚于脚本注入」这一实际观测到的时序。
    vm.runInContext(`
      Array.prototype.filter = function (cb) {
        var out = [];
        for (var i = 0; i < this.length; i++) { if (cb(i, this[i], this)) out.push(this[i]); }
        return out;
      };
      Array.prototype.some = function (cb) {
        for (var i = 0; i < this.length; i++) { if (cb(i, this[i], this)) return true; }
        return false;
      };
    `, ctx);
  }
  return { window, calls, ctx };
}

test('HBNU 基线（未污染）：正常导入 + 缺周次跳过弹窗', async () => {
  const h = harness({ href: 'https://jwxt.hbnu.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151' });
  await h.window.shangkeImportEntry();
  await new Promise(r => setTimeout(r, 30));
  assert.equal(h.calls.saved.length, 1, '基线：应保存一次（toasts=' + JSON.stringify(h.calls.toasts) + '）');
  const courses = h.calls.saved[0] || [];
  assert.equal(courses.length, 1);
  assert.equal(courses[0].name, '高等数学');
  assert.equal(h.calls.alerts.length, 1);
  assert.ok(/军训/.test(h.calls.alerts[0].content));
});

test('HBNU 页面库污染 Array.prototype 时仍能正常导入（过滤不被翻转）', async () => {
  const h = harness({ href: 'https://jwxt.hbnu.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151', polluted: true });
  await h.window.shangkeImportEntry();
  await new Promise(r => setTimeout(r, 30));
  assert.equal(h.calls.saved.length, 1, '应保存一次（toasts=' + JSON.stringify(h.calls.toasts) + '）');
  const courses = h.calls.saved[0] || [];
  assert.equal(courses.length, 1, '污染环境下仍应解析出 1 门带周次课程');
  assert.equal(courses[0].name, '高等数学');
  assert.ok(courses[0].weeks.length >= 16, '周次应为 1-16 共 16 周');
  // 空周次的「军训」应进 skipped 并弹窗告知
  assert.equal(h.calls.alerts.length, 1, '缺周次课程应弹窗告知');
  assert.ok(/军训/.test(h.calls.alerts[0].content), '弹窗应列明被跳过课程名');
});
