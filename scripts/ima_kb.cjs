#!/usr/bin/env node
/**
 * ima_kb.cjs —— 「上课」项目 IMA 知识库统一入口（共享库优先）
 *
 * 上位规则：仓库根 AGENTS.md「项目记忆与协作规范（IMA 知识库，强制）」节；
 * 协作者请先读 docs/agents/ima-shared-kb.md。
 *
 * 落点约定（2026-10-10 起）：
 *   · **共享知识库「上课-共享」= 唯一写入目标**，所有协作者共用同一份；
 *   · **个人知识库「上课」= 只读备份**，只由 --backup 镜像写入，冲突以共享库为准。
 *   · 库名 / 文件夹名一律**按名称现场解析**，脚本内不写死任何内部 ID。
 *
 * 用法（node scripts/ima_kb.cjs <命令> …）：
 *   kb                                  列出账号下全部知识库（含类型）
 *   ls [--folder <名>] [--kb <名>]      列出共享库内容；带 --folder 则列该文件夹
 *   find <关键词> [--kb <名>]           在共享库内搜索
 *   read <note_id>                      读一篇笔记正文（notes 通道）
 *   note-new <标题> <md文件> [--folder <名>] [--kb <名>] [--backup] [--dry-run]
 *                                      新建笔记 → add_knowledge(media_type=11) 入目标文件夹
 *   note-append <note_id> <md文件> [--marker <纯文本片段>] [--max-ratio N] [--dry-run]
 *                                      追加正文（幂等守卫：--marker 命中则跳过）
 *   up <文件> [--folder <名>] [--kb <名>] [--backup] [--dry-run]
 *                                      文件上传：preflight → 重名 → create_media → COS → add_knowledge
 *   verify <标题片段> [--folder <名>] [--kb <名>]
 *                                      落库核验：条目是否在目标文件夹 + 检索是否命中
 *
 * 依赖：ima-skill（C:\Users\30458\.dsh\skills\ima-skills）+ ~/.config/ima 凭证。
 * 退出码：0 = 成功/核验通过；1 = 失败；2 = 用法错误。
 */
'use strict';

const fs = require('fs');
const path = require('path');
const os = require('os');
const { execFileSync } = require('child_process');

const SKILL_DIR = process.env.IMA_SKILL_DIR
  || path.join(os.homedir(), '.dsh', 'skills', 'ima-skills');
const IMA_API = path.join(SKILL_DIR, 'ima_api.cjs');
const PREFLIGHT = path.join(SKILL_DIR, 'knowledge-base', 'scripts', 'preflight-check.cjs');
const COS_UPLOAD = path.join(SKILL_DIR, 'knowledge-base', 'scripts', 'cos-upload.cjs');

/** 协作主库：共享知识库，唯一写入目标 */
const DEFAULT_KB = process.env.IMA_KB_SHARED || '上课-共享';
/** 备份库：个人知识库，只读留档（历史快照 + 镜像），不作为事实来源 */
const BACKUP_KB = process.env.IMA_KB_BACKUP || '上课';

// ────────────────────────────── 基础设施 ──────────────────────────────

let tmpSeq = 0;
function tmpFile(suffix, content) {
  const p = path.join(os.tmpdir(), `ima_kb_${process.pid}_${tmpSeq++}${suffix}`);
  fs.writeFileSync(p, content, 'utf8');
  return p;
}

function credentials() {
  const dir = path.join(os.homedir(), '.config', 'ima');
  const cid = path.join(dir, 'client_id');
  const key = path.join(dir, 'api_key');
  if (!fs.existsSync(cid) || !fs.existsSync(key)) {
    throw new Error(
      `缺少 IMA 凭证（${cid} / ${key}）。到 https://ima.qq.com/agent-interface 取 Client ID 与 API Key 后写入这两个文件。`);
  }
  return {
    clientId: fs.readFileSync(cid, 'utf8').replace(/^\uFEFF/, '').trim(),
    apiKey: fs.readFileSync(key, 'utf8').replace(/^\uFEFF/, '').trim(),
  };
}

/**
 * 调用 IMA OpenAPI。业务 code≠0 一律抛错并带上 msg（ima-skill「失败即停」规则）。
 * @param {string} apiPath 例如 openapi/wiki/v1/get_knowledge_list
 * @param {object} bodyObj
 * @param {string} [raw] 'notes' 时不做「code≠0 即抛」的强判定，由调用方判定
 */
function call(apiPath, bodyObj) {
  const creds = credentials();
  const bodyFile = tmpFile('.json', JSON.stringify(bodyObj));
  try {
    const out = execFileSync(process.execPath, [IMA_API, apiPath, fs.readFileSync(bodyFile, 'utf8'),
      JSON.stringify(creds)], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
    const json = JSON.parse(out);
    if (json.code !== 0) throw new Error(`${apiPath} 业务失败 code=${json.code} msg=${json.msg}`);
    return json.data;
  } finally {
    try { fs.unlinkSync(bodyFile); } catch (_) { /* 临时文件清理失败不阻断 */ }
  }
}

/** 读本地文本文件并强制 UTF-8（剥 BOM、非法字节用 U+FFFD 替换，绝不静默产出 ANSI） */
function readUtf8(file) {
  const buf = fs.readFileSync(file);
  const text = buf.toString('utf8');
  return text.charCodeAt(0) === 0xfeff ? text.slice(1) : text;
}

/** 判断是否合法的 UTF-8（用于写入前阻断，避免不可逆乱码） */
function assertUtf8(text, what) {
  // Buffer→utf8 会把非法字节替换为 U+FFFD；若原文已含 U+FFFD 则说明源头就有问题
  if (text.includes('\uFFFD')) {
    throw new Error(`${what} 含非法 UTF-8 字节（U+FFFD），已阻断写入以免产生不可修复的乱码`);
  }
  return text;
}

function normalizeTitle(s) {
  return String(s == null ? '' : s).trim();
}

function listItems(d) {
  return d.knowledge_list || d.info_list || [];
}

/** 游标翻页取全量条目（IMA 列表一律游标分页，is_end=true 才停） */
function listAll(kbId, folderId) {
  const out = [];
  let cursor = '';
  for (let i = 0; i < 60; i++) {
    const body = { knowledge_base_id: kbId, cursor, limit: 50 };
    if (folderId) body.folder_id = folderId;
    const d = call('openapi/wiki/v1/get_knowledge_list', body);
    for (const it of listItems(d)) out.push(it);
    if (d.is_end) break;
    cursor = d.next_cursor;
  }
  return out;
}

/** 按名称解析知识库（AGENTS.md：不得把内部 ID 写进面向用户的文档/脚本） */
function resolveKb(name) {
  const d = call('openapi/wiki/v1/search_knowledge_base', { query: name, cursor: '', limit: 20 });
  const hit = listItems(d).find((x) => normalizeTitle(x.kb_name) === name);
  if (!hit) {
    const cands = listItems(d).map((x) => x.kb_name).join('、') || '（无）';
    throw new Error(`未按名称解析到知识库「${name}」；本次候选：${cands}`);
  }
  return { id: hit.kb_id, name: hit.kb_name, contentCount: hit.content_count, baseType: hit.base_type };
}

/** 按名称解析一级文件夹（权威口径：title 全等 + media_type=99） */
function resolveFolder(kbId, folderName) {
  const hit = listAll(kbId).find((x) => normalizeTitle(x.title) === folderName && x.media_type === 99);
  if (!hit) {
    const roots = listAll(kbId).map((x) => x.title).join('、');
    throw new Error(`未找到一级文件夹「${folderName}」；根层现有：${roots}`);
  }
  return hit.media_id;
}

function parseArgs(argv) {
  const positional = [];
  const flags = {};
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a.startsWith('--')) {
      const key = a.slice(2);
      const next = argv[i + 1];
      if (next === undefined || next.startsWith('--')) flags[key] = true;
      else { flags[key] = next; i++; }
    } else positional.push(a);
  }
  return { positional, flags };
}

// ────────────────────────────── 写通道 ──────────────────────────────

/**
 * 文件上传：preflight → 重名检查 → create_media → COS 上传 → add_knowledge。
 * 关键实测点：folder_id 必须是 add_knowledge 的**顶层字段**，否则条目静默落到知识库根层。
 */
function uploadFile(file, kb, folderId, { dryRun = false } = {}) {
  const abs = path.resolve(file);
  if (!fs.existsSync(abs)) throw new Error(`文件不存在：${abs}`);

  const pre = JSON.parse(execFileSync(process.execPath, [PREFLIGHT, '--file', abs],
    { encoding: 'utf8' }));
  if (pre.pass !== true) throw new Error(`预检未通过：${pre.reason || JSON.stringify(pre)}`);
  const { file_name: fileName, file_ext: fileExt, file_size: fileSize,
    media_type: mediaType, content_type: contentType } = pre;

  let finalName = fileName;
  const dupBody = (name) => ({
    params: [{ name, media_type: mediaType }], knowledge_base_id: kb.id, folder_id: folderId,
  });
  const dup = call('openapi/wiki/v1/check_repeated_names', dupBody(finalName));
  const dupHit = (dup.results || []).find((x) => x.name === finalName);
  if (dupHit && dupHit.is_repeated) {
    // 不支持覆盖：追加时间戳保留副本，并再查一次确认不冲突
    const stamp = new Date().toISOString().replace(/[-:T]/g, '').slice(0, 14);
    finalName = `${path.basename(fileName, path.extname(fileName))}_${stamp}${path.extname(fileName)}`;
    const dup2 = call('openapi/wiki/v1/check_repeated_names', dupBody(finalName));
    const dup2Hit = (dup2.results || []).find((x) => x.name === finalName);
    if (dup2Hit && dup2Hit.is_repeated) throw new Error(`重名且时间戳副本仍冲突：${finalName}`);
  }

  if (dryRun) {
    return { dryRun: true, finalName, mediaType, fileSize, contentType, folderId, kb: kb.name };
  }

  const media = call('openapi/wiki/v1/create_media', {
    file_name: finalName, file_size: fileSize, content_type: contentType,
    knowledge_base_id: kb.id, file_ext: fileExt,
  });
  const c = media.cos_credential;
  execFileSync(process.execPath, [COS_UPLOAD,
    '--file', abs,
    '--secret-id', c.secret_id, '--secret-key', c.secret_key, '--token', c.token,
    '--bucket', c.bucket_name, '--region', c.region, '--cos-key', c.cos_key,
    '--content-type', contentType,
    '--start-time', String(c.start_time), '--expired-time', String(c.expired_time),
    '--timeout', '300000'], { stdio: ['ignore', 'ignore', 'inherit'] });

  call('openapi/wiki/v1/add_knowledge', {
    media_type: mediaType, media_id: media.media_id, title: finalName,
    knowledge_base_id: kb.id, folder_id: folderId,
    file_info: { cos_key: c.cos_key, file_size: fileSize, file_name: finalName },
  });
  return { dryRun: false, finalName, mediaId: media.media_id, kb: kb.name, folderId };
}

/** 笔记入库：import_doc 建笔记 → add_knowledge(media_type=11) 关联进知识库文件夹 */
function publishNote(title, mdText, kb, folderId, { dryRun = false } = {}) {
  const content = assertUtf8(mdText, `笔记「${title}」`);
  if (dryRun) return { dryRun: true, title, kb: kb.name, folderId, chars: content.length };

  const imp = call('openapi/note/v1/import_doc', { content, content_format: 1 });
  const noteId = imp.note_id || (imp.data && imp.data.note_id) || imp.id;
  if (!noteId) throw new Error(`import_doc 未返回 note_id：${JSON.stringify(imp).slice(0, 300)}`);

  call('openapi/wiki/v1/add_knowledge', {
    media_type: 11, note_info: { content_id: String(noteId) }, title,
    knowledge_base_id: kb.id, folder_id: folderId,
  });
  return { dryRun: false, title, noteId: String(noteId), kb: kb.name, folderId, chars: content.length };
}

/**
 * 追加正文。
 *
 * ⚠️ 两条实测教训写在这里，改动本函数前务必先读：
 *
 * 1. **marker 必须是「回读形态」的纯文本片段**，不能直接抄源 Markdown。
 *    `get_doc_content` 返回的是**归一化纯文本**：`# ` 行首标记被剥掉、`**`/`反引号` 被去除、
 *    `_` 被转义、`- ` 列表标记归一成 `* `。⇒ 传 `# 标题` 当 marker 在回读文本里**永远不存在**，
 *    守卫恒假放行，正文被整段追加两遍（2026-10-10 实测：协作规范笔记 3126 → 6252 字符）。
 *    正确写法：取**不含 Markdown 标记的连续中文/文字片段**，如 `一、两个库的角色`。
 *
 * 2. **守卫必须真读到非空正文**才允许比较——对空字符串 `includes` 恒真，
 *    且「读到 undefined → 长度算成 0 → 守卫恒真放行」是已发生过的真实事故（2026-10-07）。
 */
function appendNote(noteId, mdText, marker, { dryRun = false, maxRatio = 2 } = {}) {
  const content = assertUtf8(mdText, `追加内容（note ${noteId}）`);
  const before = call('openapi/note/v1/get_doc_content',
    { note_id: String(noteId), target_content_format: 0 });
  const beforeText = before.content || before.doc_content || before.text || '';
  if (!beforeText.length) throw new Error('读不到追加前正文（长度 0），拒绝追加以免覆盖既有结论');

  if (marker !== undefined) {
    if (typeof marker !== 'string' || !marker.length) {
      throw new Error('--marker 必须是**非空纯文本**片段（不要含 # / ** / 反引号 / - 列表标记）');
    }
    // 护栏：marker 若带 Markdown 标记，几乎必然在归一化回读里不存在 → 守卫恒假
    if (/^[#>*\-+`]|\*\*|`|^\s*[-*+]\s/m.test(marker)) {
      throw new Error(
        `--marker 含 Markdown 标记，在归一化回读文本中极可能不存在，守卫会恒假放行。\n`
        + `请改用纯文本片段，例如「一、两个库的角色」而不是「# 一、两个库的角色」。`);
    }
    if (beforeText.includes(marker)) {
      return { skipped: true, reason: 'marker 已存在于回读正文', marker, noteId: String(noteId), lenBefore: beforeText.length };
    }
  }

  // 护栏：追加量不得超过原正文长度（防「守卫失效 → 整段翻倍」这类事故静默通过）
  if (!dryRun && content.length > beforeText.length * maxRatio) {
    throw new Error(
      `追加内容 ${content.length} 字符 > 原正文 ${beforeText.length} 字符 × ${maxRatio}，疑似整段重发，已阻断。`
      + `确认无误可显式调大 --max-ratio。`);
  }

  if (dryRun) {
    return { dryRun: true, noteId: String(noteId), lenBefore: beforeText.length, willAppend: content.length };
  }
  call('openapi/note/v1/append_doc', { note_id: String(noteId), content, content_format: 1 });
  const after = call('openapi/note/v1/get_doc_content',
    { note_id: String(noteId), target_content_format: 0 });
  const afterText = after.content || after.doc_content || after.text || '';
  return {
    skipped: false, noteId: String(noteId),
    lenBefore: beforeText.length, lenAfter: afterText.length,
    grew: afterText.length > beforeText.length,
  };
}

// ────────────────────────────── 核验 ──────────────────────────────

/**
 * 落库核验。
 *
 * ⚠️ **权威判据是「文件夹列表命中」**，检索只作辅助信号，**不参与 pass 判定**。
 * 理由（2026-10-10 实测）：`search_knowledge` 的命中受**索引滞后**与**分词规则**影响，
 * 对英文词干尤其明显——同一轮里 `verify "协作规范"` 检索命中 2 条 PASS，
 * 而刚上传完的 `verify "ima-shared-kb.md"` 列表命中 1（条目确在目标文件夹内、
 * parent_folder_id 正确）却检索命中 0。若把检索写进 pass 条件，
 * 上传会被误报为失败，诱导重复上传。
 *
 * 仍保留检索读数与阴性对照：阴性对照报缺说明**索引侧整体失效**（可据此判断该不该等索引），
 * 但它只影响 `searchStale` 提示，不影响 `pass`。
 */
function verifyLanding(kb, folderId, fragment, controlFragment) {
  const items = folderId ? listAll(kb.id, folderId) : listAll(kb.id);
  const frag = normalizeTitle(fragment);
  const matches = (t) => {
    const title = normalizeTitle(t);
    return title.includes(frag) || (frag.includes(title) && title.length >= 4);
  };
  const hitList = items.filter((x) => matches(x.title));
  const hitSearch = listItems(call('openapi/wiki/v1/search_knowledge',
    { query: frag, knowledge_base_id: kb.id, cursor: '' }))
    .filter((x) => matches(x.title));
  const ctl = listItems(call('openapi/wiki/v1/search_knowledge',
    { query: controlFragment || '__no_such_entry_9f3a__', knowledge_base_id: kb.id, cursor: '' }));

  // 空比较不得算通过：一条都没命中时 parentOk 必须是 false，不能默认 true
  const parentOk = hitList.length > 0
    && hitList.every((x) => !folderId || String(x.parent_folder_id || folderId) === String(folderId));

  return {
    kb: kb.name,
    fragment: frag,
    folderItems: items.length,
    listHit: hitList.length,
    listTitles: hitList.map((x) => x.title),
    searchHit: hitSearch.length,
    searchTitles: hitSearch.map((x) => x.title),
    controlHit: ctl.length,
    parentFolderOk: parentOk,
    searchStale: hitList.length > 0 && hitSearch.length === 0,
    pass: hitList.length > 0 && parentOk,
    // 诊断信息：FAIL 时直接能看到文件夹里到底有哪些标题，不必再手工去翻
    folderTitlesSample: hitList.length ? undefined : items.slice(0, 20).map((x) => x.title),
  };
}

// ────────────────────────────── 命令 ──────────────────────────────

async function cmdKb() {
  const d = call('openapi/wiki/v1/search_knowledge_base', { query: '', cursor: '', limit: 20 });
  const rows = listItems(d);
  console.log(`账号下共 ${rows.length} 个知识库：`);
  for (const r of rows) {
    const kind = r.base_type === 2 || /共享/.test(String(r.base_type)) ? '共享' : '个人';
    console.log(`  [${kind}] ${r.kb_name}   content_count=${r.content_count == null ? '?' : r.content_count}`);
  }
}

async function cmdLs(flags) {
  const kb = resolveKb(flags.kb || DEFAULT_KB);
  console.log(`知识库「${kb.name}」（${kb.baseType || '类型未知'}） content_count=${kb.contentCount}`);
  if (flags.folder) {
    const fid = resolveFolder(kb.id, flags.folder);
    const items = listAll(kb.id, fid);
    console.log(`\n[${flags.folder}] ${items.length} 条：`);
    for (const it of items) console.log(`   · ${it.title}`);
  } else {
    const roots = listAll(kb.id);
    const folders = roots.filter((x) => x.media_type === 99);
    console.log(`\n根层 ${roots.length} 条（一级文件夹 ${folders.length} 个）：`);
    let total = roots.length - folders.length;
    for (const f of folders) {
      const n = listAll(kb.id, f.media_id).length;
      total += n;
      console.log(`  📁 ${f.title}  ${n} 条`);
    }
    console.log(`合计条目 ${total}`);
  }
}

async function cmdFind(positional, flags) {
  const q = positional[0];
  if (!q) throw new Error('用法：ima_kb.cjs find <关键词> [--kb 名称]');
  const kb = resolveKb(flags.kb || DEFAULT_KB);
  const items = listItems(call('openapi/wiki/v1/search_knowledge',
    { query: q, knowledge_base_id: kb.id, cursor: '' }));
  console.log(`「${kb.name}」检索「${q}」命中 ${items.length} 条：`);
  for (const it of items) console.log(`   · ${it.title}`);
}

async function cmdRead(positional) {
  const noteId = positional[0];
  if (!noteId) throw new Error('用法：ima_kb.cjs read <note_id>');
  const d = call('openapi/note/v1/get_doc_content',
    { note_id: String(noteId), target_content_format: 0 });
  const text = d.content || d.doc_content || d.text || '';
  console.log(text);
}

async function cmdNoteNew(positional, flags) {
  const [title, file] = positional;
  if (!title || !file) throw new Error('用法：ima_kb.cjs note-new <标题> <md文件> [--folder 名] [--backup] [--dry-run]');
  const kb = resolveKb(flags.kb || DEFAULT_KB);
  const fid = resolveFolder(kb.id, flags.folder || '01-总览');
  const md = readUtf8(file);
  const main = publishNote(title, md, kb, fid, { dryRun: !!flags['dry-run'] });

  if (flags['dry-run']) {
    console.log(`[dry-run] 将把「${title}」（${main.chars} 字符）写入「${kb.name}/${flags.folder || '01-总览'}」`);
    return;
  }
  console.log(`✓ 共享库「${kb.name}」/${flags.folder || '01-总览'} 已入库：${title}（note_id=${main.noteId}，${main.chars} 字符）`);

  // 备份镜像：个人库只做备份，失败不阻断主写入，但必须显式报告
  if (flags.backup) {
    try {
      const bkb = resolveKb(BACKUP_KB);
      const bfid = resolveFolder(bkb.id, flags.folder || '01-总览');
      const b = publishNote(title, md, bkb, bfid, {});
      console.log(`✓ 个人备份库「${bkb.name}」已镜像（note_id=${b.noteId}）`);
    } catch (e) {
      console.error(`⚠ 个人备份库镜像失败（共享库写入已成功，不受影响）：${e.message}`);
    }
  }
}

async function cmdNoteAppend(positional, flags) {
  const [noteId, file] = positional;
  if (!noteId || !file) throw new Error('用法：ima_kb.cjs note-append <note_id> <md文件> [--marker "纯文本片段"] [--dry-run] [--max-ratio N]');
  const md = readUtf8(file);
  const r = appendNote(noteId, md, flags.marker, {
    dryRun: !!flags['dry-run'],
    maxRatio: flags['max-ratio'] ? Number(flags['max-ratio']) : 2,
  });
  console.log(JSON.stringify(r, null, 2));
}

async function cmdUp(positional, flags) {
  const file = positional[0];
  if (!file) throw new Error('用法：ima_kb.cjs up <文件> [--folder 名] [--backup] [--dry-run]');
  const kb = resolveKb(flags.kb || DEFAULT_KB);
  const folderName = flags.folder || '07-问题复盘';
  const fid = resolveFolder(kb.id, folderName);
  const r = uploadFile(file, kb, fid, { dryRun: !!flags['dry-run'] });

  if (flags['dry-run']) {
    console.log(`[dry-run] 将上传 ${r.finalName}（${r.fileSize} B, media_type=${r.mediaType}）→ 「${kb.name}/${folderName}」`);
    return;
  }
  console.log(`✓ 共享库「${kb.name}」/${folderName} 已入库：${r.finalName}`);

  if (flags.backup) {
    try {
      const bkb = resolveKb(BACKUP_KB);
      const bfid = resolveFolder(bkb.id, folderName);
      const b = uploadFile(file, bkb, bfid, {});
      console.log(`✓ 个人备份库「${bkb.name}」已镜像：${b.finalName}`);
    } catch (e) {
      console.error(`⚠ 个人备份库镜像失败（共享库写入已成功，不受影响）：${e.message}`);
    }
  }

  const v = verifyLanding(kb, fid, r.finalName, '__no_such_entry_9f3a__');
  console.log(`核验：列表命中 ${v.listHit} / 检索命中 ${v.searchHit} / 对照组 ${v.controlHit} → ${v.pass ? 'PASS' : 'FAIL'}`);
  if (!v.pass) { console.error(JSON.stringify(v, null, 2)); process.exitCode = 1; }
}

async function cmdVerify(positional, flags) {
  const frag = positional[0];
  if (!frag) throw new Error('用法：ima_kb.cjs verify <标题片段> [--folder 名] [--kb 名称]');
  const kb = resolveKb(flags.kb || DEFAULT_KB);
  const fid = flags.folder ? resolveFolder(kb.id, flags.folder) : null;
  const v = verifyLanding(kb, fid, frag, flags.control);
  console.log(JSON.stringify(v, null, 2));
  if (!v.pass) process.exitCode = 1;
}

const HELP = `用法：node scripts/ima_kb.cjs <命令> [参数]

  kb                                          列出全部知识库
  ls [--folder <名>] [--kb <名>]              浏览共享库 / 指定文件夹
  find <关键词> [--kb <名>]                   检索
  read <note_id>                              读笔记正文
  note-new <标题> <md> [--folder <名>] [--backup] [--dry-run]
  note-append <note_id> <md> [--marker <纯文本片段>] [--max-ratio N] [--dry-run]
  up <文件> [--folder <名>] [--backup] [--dry-run]
  verify <标题片段> [--folder <名>] [--kb <名>]

默认库：${DEFAULT_KB}（共享，唯一写入目标）    备份库：${BACKUP_KB}（个人，只读留档）`;

async function main() {
  const [cmd, ...rest] = process.argv.slice(2);
  const { positional, flags } = parseArgs(rest);
  switch (cmd) {
    case 'kb': return cmdKb();
    case 'ls': return cmdLs(flags);
    case 'find': return cmdFind(positional, flags);
    case 'read': return cmdRead(positional);
    case 'note-new': return cmdNoteNew(positional, flags);
    case 'note-append': return cmdNoteAppend(positional, flags);
    case 'up': return cmdUp(positional, flags);
    case 'verify': return cmdVerify(positional, flags);
    case undefined:
    case '-h':
    case '--help':
    case 'help':
      console.log(HELP);
      return;
    default:
      console.error(`未知命令：${cmd}\n\n${HELP}`);
      process.exit(2);
  }
}

main().catch((e) => {
  console.error(`✗ ${e.message}`);
  process.exit(1);
});
