# 上课 · 教务适配私有中转网关

APP 侧教务适配的**唯一**远程入口。它在 APP 与私有 GitHub 适配仓库
（`schedule-adapter-private`）之间做鉴权中转，保证 APP 能安全地拉取最新适配，
同时让私有仓库、脚本内容与密钥都不对外暴露。

- 运行平台：Cloudflare Workers
- 鉴权方式：请求头 `X-App-Secret`
- 放行路径：仅 `index.json` 与 `adapters/**`，其余一律 `403`
- 缓存：命中后 5 分钟（`Cache-Control: public, max-age=300`）
- 限流：单 IP 每分钟 120 次（超出返回 `429`）

---

## 一、环境变量（全部使用 secret，禁止写入配置文件）

| 变量名 | 说明 |
| --- | --- |
| `GH_PAT` | 只读私有仓库 `schedule-adapter-private` 的 GitHub Token |
| `GH_OWNER` | 仓库所有者（例：`qiqqqqq517`） |
| `GH_REPO` | 仓库名（`schedule-adapter-private`） |
| `GH_BRANCH` | 分支名（`main`） |
| `APP_SECRET` | APP 内置的高强度随机密钥，用于 `X-App-Secret` 鉴权 |

> `GH_PAT` 只存在于 Worker 环境变量中，既不写入本仓库代码，也不会进入 APP。

---

## 二、部署清单（从零到可用）

前置：Node.js ≥ 18（本机 `npx wrangler` 会自动拉取 wrangler）。

```bash
# 1. 登录 Cloudflare（浏览器授权）
npx wrangler login

# 2. 进入本目录
cd adapter-worker

# 3. 写入 5 个环境变量（按提示粘贴，输入内容不会回显）
npx wrangler secret put GH_PAT
npx wrangler secret put GH_OWNER      # 例：qiqqqqq517
npx wrangler secret put GH_REPO       # 例：schedule-adapter-private
npx wrangler secret put GH_BRANCH     # 例：main
npx wrangler secret put APP_SECRET    # 与 APP 侧注入的密钥必须完全一致

# 4. 部署
npx wrangler deploy
# 记下输出中的 https://schedule-adapter-gateway.<你的子域>.workers.dev
```

---

## 三、部署后验证

```bash
BASE=https://schedule-adapter-gateway.<你的子域>.workers.dev

# 1) 浏览器直接访问 → 403
curl -i $BASE/

# 2) 无密钥访问适配 → 403
curl -i $BASE/adapters/AHU/ahu.js

# 3) 错误密钥 → 403
curl -i -H "X-App-Secret: wrong" $BASE/index.json

# 4) 正确密钥 → 200，返回 index.json
curl -i -H "X-App-Secret: <APP_SECRET>" $BASE/index.json

# 5) 非白名单路径（即使密钥正确）→ 403
curl -i -H "X-App-Secret: <APP_SECRET>" $BASE/README.md
```

---

## 四、绑定自定义域（大陆可用性必需）

`*.workers.dev` 在中国大陆遭 DNS 污染（实测被解析到 Dropbox / Facebook / Twitter 等无关 IP），
**必须**改用自己的域名才能对大陆用户可用。

本项目使用的域名：**`shangke.asia`**（注册商：阿里云，当前 NS 为 `dns7/dns8.hichina.com`）。

> ⚠️ Cloudflare Workers 的自定义域**要求域名托管在 Cloudflare**。用外部 DNS 加 CNAME 指向
> `*.workers.dev` **无效**——网关按 Host 匹配路由，且 Cloudflare 不会为该 hostname 下发证书。
> 因此必须先迁移 NS。

### 步骤

1. 先确认域名现状：**阿里云 → 域名控制台** → `shangke.asia`。
   如有邮箱（MX）等既有解析记录，先记录下来，方便在 Cloudflare 侧核对。
   > 迁移 NS 只是更换解析服务商，**不需要 ICP 备案**（服务托管在 Cloudflare 境外）。
2. **Cloudflare Dashboard → Add a site** → 输入 `shangke.asia` → 选 **Free** 计划
   → 拿到两个 NS（形如 `xxx.ns.cloudflare.com`）。
3. **阿里云 → 域名控制台 → `shangke.asia` → DNS 修改** → 把 `dns7/dns8.hichina.com`
   替换为 Cloudflare 给的两个 NS → 保存。
4. 等待 Cloudflare 站点状态变为 **Active**（一般几分钟 ~ 24 小时）。
5. 在本目录 `wrangler.toml` 中声明自定义域后重新部署：

   ```toml
   routes = [
     { pattern = "adapter.shangke.asia", custom_domain = true }
   ]
   ```

   ```bash
   npx wrangler deploy
   ```

   `custom_domain = true` 时 Cloudflare 会自动创建所需 DNS 记录，无需手动加 CNAME。
6. 把新域名写入 APP 侧 `adapter_secrets.properties` 的 `adapter.workerUrl`，重新构建 APP。

> 建议保留 `workers_dev` 作为备用入口；正式对外以自定义域为准。
> 子域可换（`api.` / 直接用根域 `shangke.asia`），改 `routes` 的 `pattern` 与
> APP 侧的 `adapter.workerUrl` 保持一致即可。

---

## 五、需要注意

- 本目录只包含 Worker 代码，**不含任何密钥**，可以安全地提交到公开仓库。
- 若更换 `APP_SECRET`，必须同步重新构建并发布 APP（APP 内置该密钥）。
- 适配更新后无需重新部署 Worker；最长 5 分钟后自动生效。
- 如需更强的爬虫防护，可另行开启 Cloudflare 的 Rate Limiting / WAF 规则。
