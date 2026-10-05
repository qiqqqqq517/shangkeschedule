/**
 * 上课 · 教务适配私有中转网关（Cloudflare Worker）
 *
 * 职责：在 APP 与私有 GitHub 适配仓库之间做一层鉴权中转，保证：
 *   1. 私有仓库（schedule-adapter-private）不被直接访问；
 *   2. 浏览器 / 爬虫直接访问本 Worker 一律 403；
 *   3. 只有携带正确 X-App-Secret 请求头的 APP 才能拿到配置；
 *   4. 仅放行 index.json、adapters/** 与 index/school_index.pb（学校索引），其余全部 403；
 *   5. 命中缓存 5 分钟，并对单 IP 做基础限流。
 *
 * 敏感值全部通过环境变量注入（wrangler secret），本文件不含任何密钥：
 *   GH_PAT / GH_OWNER / GH_REPO / GH_BRANCH / APP_SECRET
 */

const MANIFEST_PATH = "index.json";
const ADAPTERS_PREFIX = "adapters/";
const CACHE_SECONDS = 300;
const RATE_WINDOW_MS = 60_000;
const RATE_MAX_REQUESTS = 120;
const RATE_BUCKET_LIMIT = 10_000;

const REJECT_HEADERS = {
  "Cache-Control": "no-store",
  "X-Content-Type-Options": "nosniff",
  "Referrer-Policy": "no-referrer",
};

const buckets = new Map();

function reject(status = 403, body = "Forbidden", extraHeaders = {}) {
  return new Response(body, {
    status,
    headers: { ...REJECT_HEADERS, ...extraHeaders },
  });
}

/** 常量时间比较，避免通过响应时间差推测密钥。 */
function constantTimeEqual(a, b) {
  if (typeof a !== "string" || typeof b !== "string") return false;
  const encoder = new TextEncoder();
  const left = encoder.encode(a);
  const right = encoder.encode(b);
  let diff = left.length ^ right.length;
  const length = Math.max(left.length, right.length);
  for (let i = 0; i < length; i += 1) {
    diff |= (left[i] ?? 0) ^ (right[i] ?? 0);
  }
  return diff === 0;
}

/**
 * 归一化并校验请求路径；非法路径返回 null。
 *
 * 安全要点：
 *  - 连续解码至稳定（最多 3 轮），拦截 %252e%252e 一类二次编码绕过；
 *  - 拒绝 `..` / 反斜杠 / 双斜杠 / 空字节；
 *  - 最后用严格字符白名单收口——适配文件路径只含 [A-Za-z0-9._-] 与 `/`。
 */
function normalizePath(pathname) {
  let path = pathname;
  for (let i = 0; i < 3; i += 1) {
    let decoded;
    try {
      decoded = decodeURIComponent(path);
    } catch {
      return null;
    }
    if (decoded === path) break;
    path = decoded;
  }

  path = path.replace(/^\/+/, "");
  if (path.length === 0) return null;
  if (path.includes("\0")) return null;
  if (path.includes("\\")) return null;
  if (path.includes("..")) return null;
  if (path.includes("//")) return null;
  if (!/^[A-Za-z0-9._/-]+$/.test(path)) return null;
  return path;
}

/** 路径白名单：仅 index.json、adapters/** 与学校索引 index/school_index.pb。 */
function isAllowedPath(path) {
  if (path === MANIFEST_PATH) return true;
  if (path === 'index/school_index.pb') return true;
  return path.startsWith(ADAPTERS_PREFIX) && path.length > ADAPTERS_PREFIX.length;
}

/**
 * 检查网关自身的关键配置是否齐备（P3-23 / P2-14）。
 *
 * 历史缺陷：env.GH_OWNER / GH_REPO / GH_PAT 任一缺失时，`encodeURIComponent(undefined)`
 * 会得到字符串 "undefined"，于是上游 URL 被拼成
 *   https://raw.githubusercontent.com/undefined/undefined/main/xxx
 * GitHub 对它回 404，Worker 便把它当成「文件不存在」返回给 App ——
 * **网关配置错误与「适配脚本真的不存在」完全无法区分**，排查时会被引向错误方向
 * （App 侧表现为「本校暂未适配」）。这类错误必须 fail-closed 且**可区分**。
 *
 * 只返回**变量名**，绝不返回值本身（用户级硬规则：不回显密钥）。
 */
function missingConfig(env) {
  return ["GH_OWNER", "GH_REPO", "GH_PAT"].filter((key) => {
    const value = env[key];
    return typeof value !== "string" || value.trim() === "" || value === "undefined";
  });
}

/**
 * 单 IP 滑动窗口限流（按 isolate 内存，best-effort 防高频爬取）。
 *
 * P2-15 / P2-42 修正：原实现在桶表写满时执行 `buckets.clear()`，把**包括攻击者在内**
 * 的全部客户端计数一并清零 —— 攻击者只要在窗口内制造 RATE_BUCKET_LIMIT 个不同 IP，
 * 就能免费重置全局限流，把 120/分 的保护变成「每 1 万个 IP 重置一次」。
 * 现改为：表满时**只淘汰已过窗口的条目**；若仍满，则对新出现的键按**超限**处理
 * （fail-closed），既有键的计数**绝不**被清空。
 */
function isRateLimited(clientKey) {
  const now = Date.now();
  const bucket = buckets.get(clientKey);
  if (!bucket || now - bucket.start >= RATE_WINDOW_MS) {
    if (buckets.size >= RATE_BUCKET_LIMIT) {
      for (const [key, value] of buckets) {
        if (now - value.start >= RATE_WINDOW_MS) buckets.delete(key);
      }
    }
    if (buckets.size >= RATE_BUCKET_LIMIT && !buckets.has(clientKey)) {
      return true;
    }
    buckets.set(clientKey, { start: now, count: 1 });
    return false;
  }
  bucket.count += 1;
  return bucket.count > RATE_MAX_REQUESTS;
}

function contentTypeFor(path) {
  if (path.endsWith(".json")) return "application/json; charset=utf-8";
  if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
  return "application/octet-stream";
}

/**
 * 从私有仓库读取原始文件内容（绝不透出 PAT）。
 *
 * 注意必须走 raw.githubusercontent.com 而非 api.github.com/contents：
 * GitHub contents API 会把二进制内容（如 school_index.pb）按文本转码，
 * 导致 APP 侧 sha256 校验失败；raw 域名返回逐字节原样内容。
 */
async function fetchFromGitHub(path, env) {
  const owner = encodeURIComponent(env.GH_OWNER);
  const repo = encodeURIComponent(env.GH_REPO);
  const branch = encodeURIComponent(env.GH_BRANCH || "main");
  const url =
    `https://raw.githubusercontent.com/${owner}/${repo}/${branch}/${path}`;

  return fetch(url, {
    method: "GET",
    headers: {
      Authorization: `Bearer ${env.GH_PAT}`,
      "User-Agent": "shangke-adapter-gateway",
    },
  });
}

function forMethod(response, method) {
  if (method !== "HEAD") return response;
  return new Response(null, { status: response.status, headers: response.headers });
}

export default {
  async fetch(request, env, ctx) {
    try {
      if (request.method !== "GET" && request.method !== "HEAD") {
        return reject();
      }

      const provided = request.headers.get("X-App-Secret");
      if (!env.APP_SECRET || !provided || !constantTimeEqual(provided, env.APP_SECRET)) {
        return reject();
      }

      const path = normalizePath(new URL(request.url).pathname);
      if (!path || !isAllowedPath(path)) {
        return reject();
      }

      // 网关自身配置缺失 ⇒ 503 且**可区分**，绝不伪装成「文件不存在」（P3-23 / P2-14）。
      // 放在鉴权之后、限流之前：配置错误是运维问题，不该消耗客户端配额。
      const missing = missingConfig(env);
      if (missing.length > 0) {
        // 只打印变量名，不打印取值。
        console.error(`[gateway] 配置缺失：${missing.join(", ")}`);
        return reject(503, "Gateway Misconfigured", { "Retry-After": "300" });
      }

      // P2-16：Cloudflare 正常都会注入 CF-Connecting-IP。缺失时**无法识别客户端**，
      // 此时把它们并入同一个 "unknown" 桶是**刻意的保守选择** ——
      // 换成「每个无头请求各自一个桶」会让限流形同虚设（fail-open）。
      // 代价是同批无头请求共享 120/分 预算，属可接受；该情形在正常链路下不会出现。
      const clientKey = request.headers.get("CF-Connecting-IP") || "unknown";
      if (isRateLimited(clientKey)) {
        return reject(429, "Too Many Requests", { "Retry-After": "60" });
      }

      const cache = caches.default;
      const cacheKey = new Request(
        `https://adapter-gateway.internal/${path}`,
        { method: "GET" },
      );

      const cached = await cache.match(cacheKey);
      if (cached) return forMethod(cached, request.method);

      const upstream = await fetchFromGitHub(path, env);
      if (!upstream.ok) {
        return upstream.status === 404
          ? reject(404, "Not Found")
          : reject(502, "Upstream Error");
      }

      const body = await upstream.arrayBuffer();
      const response = new Response(body, {
        status: 200,
        headers: {
          "Content-Type": contentTypeFor(path),
          "Cache-Control": `public, max-age=${CACHE_SECONDS}`,
          "X-Content-Type-Options": "nosniff",
          "Referrer-Policy": "no-referrer",
        },
      });

      ctx.waitUntil(cache.put(cacheKey, response.clone()));
      return forMethod(response, request.method);
    } catch {
      // 任何异常都按拒绝处理，且绝不打印请求头 / 密钥。
      return reject(500, "Internal Error");
    }
  },
};
