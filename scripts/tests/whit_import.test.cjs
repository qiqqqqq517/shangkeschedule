const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const file = path.resolve(__dirname, '../../shared/assets/offline_repo/schools/resources/WHIT/whit.js');
const source = fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : '';

// 根据门户实际字段构造的脱敏样例，不含账号、姓名或登录信息。
const lesson = (extra = {}) => ({ eventTitle: '课程甲', startTime: '2026-10-05T08:00:00',
  endTime: '2026-10-05T09:40:00', location: '教学楼101', ext: { jc: '1-2' }, isLessonTable: 1, ...extra });
function harness({ events = [lesson()], save = true, config = true, loggedIn = true,
  dates = ['2026-10-16', '24'], noTerm = false, httpStatus = 200 } = {}) {
  const calls = { requests: [], courses: [], configs: [], slots: [], toasts: [], completed: 0 };
  const window = { location: new URL('https://casp.whit.edu.cn/index.html#/hall'),
    shangkeBridge: { showToast: t => calls.toasts.push(t), notifyTaskCompletion: () => calls.completed++ },
    shangkeBridgePromise: {
      showPrompt: async () => dates.shift(),
      saveCourseConfig: async json => { calls.configs.push(JSON.parse(json)); return config; },
      savePresetTimeSlots: async json => { calls.slots.push(JSON.parse(json)); return true; },
      saveImportedCourses: async json => { calls.courses.push(JSON.parse(json)); return save; },
    },
  };
  const fetch = async (url, options) => {
    calls.requests.push({url, ...options});
    const body = JSON.parse(options.body);
    return { ok: httpStatus === 200, status: httpStatus, json: async () => {
      if (!loggedIn) return {errcode:'401',errmsg:'未登录'};
      if (body.method === 'getWeek') return {errcode:'0',data:{underInfo:noTerm ? null : {xn:'2026',xq:'1',begDate:'2026-08-31'}}};
      return {errcode:'0',data:{datas:{'2026-10-05':events}}};
    } };
  };
  const context = vm.createContext({window, fetch, AbortController, setTimeout, clearTimeout, URL, console});
  vm.runInContext(source, context);
  return {calls, window, async run() {
    assert.equal(typeof window.shangkeImportEntry, 'function', '必须注册导入入口');
    await window.shangkeImportEntry();
    return calls;
  }};
}

test('WHIT 从整学期日历课表导入，按实际开学日期还原周次与节次', async () => {
  const h = harness({events:[lesson(), lesson({startTime:'2026-10-12T08:00:00'}),lesson()]});
  const c = await h.run();
  assert.equal(c.courses[0].length, 1);
  assert.deepEqual(c.courses[0][0], {name:'课程甲',teacher:'未安排',position:'教学楼101',day:1,startSection:1,endSection:2,weeks:[6,7]});
  assert.equal(c.configs[0].semesterStartDate, '2026-08-31');
  assert.equal(c.completed, 1);
  assert.ok(c.requests.every(r=>r.credentials==='include'));
  assert.equal(c.slots[0][6].startTime, '15:50');
  assert.equal(c.slots[0][10].endTime, '21:35');
});

test('WHIT 只导入课表事件，过滤假期、个人日程和查询范围外数据', async () => {
  const c = await harness({events:[lesson(),lesson({eventTitle:'休息日',isLessonTable:0}),
    lesson({eventTitle:'个人日程',isLessonTable:undefined}),lesson({startTime:'2027-03-01T08:00:00'})]}).run();
  assert.equal(c.courses[0].length, 1);
  assert.deepEqual(c.courses[0][0].weeks, [6]);
});

test('WHIT 周末实际授课按真实日期导入，单节和三连堂不丢失', async () => {
  const c = await harness({events:[lesson({startTime:'2026-10-10T16:00:00',ext:{jc:'7-7'}}),
    lesson({startTime:'2026-10-11T19:00:00',ext:{jc:'9-11'}})]}).run();
  assert.equal(c.courses[0][0].day, 6);
  assert.equal(c.courses[0][0].startSection, 7);
  assert.equal(c.courses[0][0].endSection, 7);
  assert.equal(c.courses[0][1].day, 7);
  assert.equal(c.courses[0][1].endSection, 11);
});

test('WHIT 缺节次时报告问题，不能根据时钟猜节次后静默导入', async () => {
  const c = await harness({events:[lesson({ext:{}})]}).run();
  assert.equal(c.courses.length, 0);
  assert.ok(c.toasts.some(t=>/节次/.test(t)));
});

test('WHIT 登录失效和接口错误有提示，不能写入空课表', async () => {
  for (const options of [{loggedIn:false},{httpStatus:502},{noTerm:true}]) {
    const c = await harness(options).run();
    assert.equal(c.courses.length, 0);
    assert.equal(c.completed, 0);
    assert.ok(c.toasts.some(t=>/登录|HTTP|学期/.test(t)));
  }
});

test('WHIT 取消任意输入都不保存配置或课程', async () => {
  for (const dates of [[null],['2026-10-16',null]]) {
    const c = await harness({dates}).run();
    assert.equal(c.courses.length, 0);
    assert.equal(c.configs.length, 0);
    assert.equal(c.completed, 0);
  }
});

test('WHIT 保存失败不提示成功或结束导入', async () => {
  for (const options of [{save:false},{config:false}]) {
    const c = await harness(options).run();
    assert.equal(c.completed, 0);
    assert.ok(c.toasts.some(t=>/保存/.test(t)));
  }
});

test('WHIT 无效日期和周数被拒绝，不发起后续课表请求', async () => {
  for (const dates of [['2026-02-31'],['2026-10-16','abc'],['2026-10-16','0'],['2026-10-16','33']]) {
    const c = await harness({dates}).run();
    assert.equal(c.courses.length, 0);
    assert.ok(c.toasts.some(t=>/日期|周数/.test(t)));
  }
});
