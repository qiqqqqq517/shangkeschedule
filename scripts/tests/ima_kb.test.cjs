// 使用离线 API 响应验证 CLI，测试不访问知识库、不写用户笔记。
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

function fixture(responses = {}, options = {}) {
  const output = [];
  const calls = [];
  const context = { require: options.require || require, process, Buffer, console: { log: x => output.push(String(x)), error: x => output.push(String(x)) } };
  vm.createContext(context);
  const source = fs.readFileSync(path.join(__dirname, '../ima_kb.cjs'), 'utf8').split('main().catch')[0];
  vm.runInContext(source, context);
  context.mockApi = (endpoint, body) => {
    calls.push({ endpoint, body });
    if (!(endpoint in responses)) throw new Error('意外 API 调用：' + endpoint);
    return responses[endpoint];
  };
  if (!options.realCall) vm.runInContext("call=mockApi; resolveKb=()=>({id:'test-kb',name:'上课-共享'}); resolveFolder=()=> 'test-folder';", context);
  return { output, calls, run: code => vm.runInContext(code, context) };
}

test('find --json 一次返回可供后续命令使用的媒体引用', async () => {
  const f = fixture({ 'openapi/wiki/v1/search_knowledge': { info_list: [{ title: '规则', media_id: 'note_test', media_type: 11 }], is_end: true } });
  await f.run("cmdFind(['规则'],{json:true})");
  const result = JSON.parse(f.output.join(''));
  assert.equal(result.items[0].mediaId, 'note_test');
  assert.equal(result.items[0].title, '规则');
  assert.equal(f.calls.length, 1);
});

test('read 接受媒体引用并在 CLI 内解析笔记 ID', async () => {
  const f = fixture({
    'openapi/wiki/v1/get_media_info': { media_type: 11, notebook_ext_info: { notebook_id: '1234' } },
    'openapi/note/v1/get_doc_content': { content: '一二三四五六' },
  });
  await f.run("cmdRead(['note_test'],{tail:'3'})");
  assert.match(f.output.join(''), /四五六/);
  assert.doesNotMatch(f.output.join(''), /一二三/);
  assert.equal(f.calls.length, 2);
  assert.equal(f.calls[1].body.note_id, '1234');
});

test('旧的数字笔记 ID 仍直接读取，不增加元数据请求', async () => {
  const f = fixture({ 'openapi/note/v1/get_doc_content': { content: '内容' } });
  await f.run("cmdRead(['1234'],{})");
  assert.equal(f.output[0], '内容');
  assert.equal(f.calls.length, 1);
});

test('无效读取长度在 API 请求前拒绝', async () => {
  const f = fixture();
  await assert.rejects(f.run("cmdRead(['1234'],{tail:'-1'})"));
  assert.equal(f.calls.length, 0);
});

test('非笔记媒体不能冒充笔记读取', async () => {
  const f = fixture({ 'openapi/wiki/v1/get_media_info': { media_type: 1, url_info: { url: 'https://example.invalid/file' } } });
  await assert.rejects(f.run("cmdRead(['file_test'],{})"));
  assert.equal(f.calls.length, 1);
});

test('ls --json 仅返回指定层级，不递归全库盘点', async () => {
  const f = fixture();
  f.run("listAll=(kb,folder)=>{ if(folder) throw new Error('不应递归'); return [{title:'01-总览',media_id:'folder_test',media_type:99}]; }");
  await f.run("cmdLs({json:true})");
  const result = JSON.parse(f.output.join(''));
  assert.equal(result.items[0].mediaId, 'folder_test');
});

test('API 子进程失败只返回结构化原因，不回显带凭据的命令', () => {
  const secret = ['test-secret', 'value-do-not-echo'].join('-');
  const f = fixture({}, { realCall: true, require: name => name !== 'child_process' ? require(name) : {
    execFileSync() { const error = new Error('Command failed with credential ' + secret); error.stderr = Buffer.from(JSON.stringify({code:-100,msg:'网络失败'})); throw error; },
  } });
  f.run(`credentials=()=>({clientId:'test-client',apiKey:${JSON.stringify(secret)}})`);
  assert.throws(() => f.run("call('openapi/test',{})"), error => /网络失败/.test(error.message) && !error.message.includes(secret));
});
