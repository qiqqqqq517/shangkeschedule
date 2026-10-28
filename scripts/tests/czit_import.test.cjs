const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.resolve(__dirname,
  '../../shared/assets/offline_repo/schools/resources/CZIT/czit.js'), 'utf8');

const JW_URL = 'https://jx.ccit.js.cn/jwglxt/xtgl/index_initMenu.html';
const PORTAL_URL = 'https://ehall.ccit.js.cn/ywtb-portal/standard/index.html?browser=no#/home/home';

const row = (extra = {}) => ({ kcmc: '设计基础\t', xm: '甲老师', cdmc: 'A101',
  xqj: '1', jcs: '1-8节', zcd: '19周', ...extra });

function harness({ href = JW_URL, kbList = [row()], networkError = false } = {}) {
  const url = new URL(href);
  const calls = { fetches: [], saved: [], toasts: [], navigated: null };
  const location = { href, hostname: url.hostname, protocol: url.protocol, search: url.search };
  Object.defineProperty(location, 'href', {
    get: () => href,
    set: v => { calls.navigated = v; },
  });
  function fakeFetch(target, options = {}) {
    const full = String(target);
    calls.fetches.push({ url: full, method: options.method || 'GET' });
    if (networkError) return Promise.reject(new TypeError('Failed to fetch'));
    if (/xskbcx_cxXskbcxIndex\.html/i.test(full)) {
      return Promise.resolve({ status: 200, text: () => Promise.resolve('<html>个人课表查询</html>') });
    }
    if (/xskbcx_cxXsgrkb\.html/i.test(full)) {
      return Promise.resolve({ status: 200, text: () => Promise.resolve(JSON.stringify({ kbList })) });
    }
    return Promise.resolve({ status: 404, text: () => Promise.resolve('') });
  }
  const window = {
    location,
    document: { getElementById: () => null, querySelectorAll: () => [] },
    fetch: fakeFetch,
    performance: { getEntriesByType: () => [] },
    shangkeBridgePromise: {
      saveImportedCourses(json) { calls.saved.push(JSON.parse(json)); return Promise.resolve(true); },
    },
    shangkeBridge: {
      showToast: text => calls.toasts.push(text),
      notifyTaskCompletion: () => { calls.completed = (calls.completed || 0) + 1; },
    },
    setTimeout,
    clearTimeout,
  };
  const context = vm.createContext({ window, document: window.document, fetch: fakeFetch,
    performance: window.performance, URL, Date, JSON, Promise, setTimeout, clearTimeout, console });
  vm.runInContext(source, context);
  return { window, calls, async run() {
    await window.shangkeImportEntry();
    await new Promise(resolve => setTimeout(resolve, 20));
    return calls;
  } };
}

test('CZIT 教务网络不可达时给出可执行中文提示，而不是 Failed to fetch', async () => {
  const h = harness({ networkError: true });
  await h.run();
  assert.equal(h.calls.saved.length, 0);
  const text = h.calls.toasts.join(' ');
  assert.ok(/校园网|代理|VPN/.test(text), '必须提示检查校园网/代理，实际提示：' + text);
  assert.ok(!/Failed to fetch/i.test(text), '不得把英文原生错误直接丢给用户');
});

test('CZIT 门户页跳转教务时跟随当前页协议（https 页面不得降级到 http）', async () => {
  const h = harness({ href: PORTAL_URL });
  await h.run();
  assert.ok(h.calls.navigated, '门户页点击导入应跳转教务应用');
  assert.ok(h.calls.navigated.startsWith('https://ehall.ccit.js.cn/appShow?appId=5843399900513599'),
    '实际跳转：' + h.calls.navigated);
});

test('CZIT 接口返回课表时跳过缺周次的脏行并计数提示', async () => {
  const h = harness({ kbList: [row(), row({ kcmc: '临时课', zcd: '' })] });
  const calls = await h.run();
  assert.equal(calls.saved.length, 1);
  assert.equal(calls.saved[0].length, 1);
  assert.equal(calls.saved[0][0].name, '设计基础');
  assert.ok(calls.toasts.some(t => /跳过/.test(t)), '缺周次行必须计数告知');
});

test('CZIT 在统一认证页点击导入时只提示登录，不发任何请求', async () => {
  const h = harness({ href: 'https://authserver.ccit.js.cn/authserver/login?service=x' });
  const calls = await h.run();
  assert.equal(calls.fetches.length, 0);
  assert.ok(calls.toasts.some(t => /登录/.test(t)));
});

test('CZIT 真实接口字段（jcs 无"节"字、zcd 混合周次、kcmc 带制表符）全部正确解析', async () => {
  // 样例取自 2026-10-28 真实 kbList（35 条中摘 2 条），字段结构勿按惯例改写：
  //   jcs="1-8"（没有"节"字）、zcd="19周" / "3周,8-18周"（逗号分隔混合单周与范围）、
  //   kcmc 带尾部制表符。
  const h = harness({ kbList: [
    { kcmc: '设计基础\t', xm: '刘丹', cdmc: '教学楼D407', xqj: '1', jcs: '1-8', zcd: '19周' },
    { kcmc: '应用语文', xm: '曹洁萍', cdmc: '教学楼B211', xqj: '1', jcs: '3-4', zcd: '3周,8-18周' },
  ]});
  const calls = await h.run();
  assert.equal(calls.saved[0].length, 2, '两条真实记录都应导入');
  const [a, b] = calls.saved[0];
  assert.equal(a.name, '设计基础', '课程名要去掉尾部制表符');
  assert.equal(a.startSection, 1);
  assert.equal(a.endSection, 8);
  assert.deepEqual(a.weeks, [19]);
  assert.equal(a.position, '教学楼D407');
  assert.deepEqual(b.weeks, [3, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18], '混合周次必须拆成单周集合');
  assert.equal(b.startSection, 3);
  assert.equal(b.endSection, 4);
  assert.equal(b.teacher, '曹洁萍');
});
