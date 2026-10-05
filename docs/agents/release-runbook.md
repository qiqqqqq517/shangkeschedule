# 发版实操手册（Release Runbook）

> 规则正文见仓库根目录 `AGENTS.md` 的「正式版构建与发布」节；本文件是**逐条可执行**的实操步骤，
> 由 2026-09-23 发布 v3.66.2 的真实过程沉淀而来（全程无 CI，本地构建 + 手动上传：gh 最快路径见 §4.A，REST API 备用见 §4.B）。
> 环境前提：Windows + PowerShell，SDK 在 `D:\Android\SDK`，仓库根为 `D:\01课程表\shangkeschedule`。

## 0. 前置条件

| 项 | 值 / 检查方式 |
| --- | --- |
| Java | 环境变量 `JAVA_HOME` 在本机是坏的，每条 Gradle 命令前显式设为 `C:\Program Files\Android\Android Studio\jbr` |
| Android SDK | `D:\Android\SDK`；`build-tools\37.0.0\` 提供 `aapt2` / `apksigner` |
| 签名 | `androidApp/keystore.properties` + `androidApp/shangkeschedule-release.jks`（均在 `.gitignore` 内） |
| `gh` CLI | 已装并登录（2.101，账号 `qiqqqqq517`，2026-09-29 实测）。令牌一律 `gh auth token` 取；**勿用 `git credential fill`**（本机凭据链不可靠，详见 AGENTS.md 警告） |
| CHANGELOG | 必须先有 `### vX.Y.Z（YYYY-MM-DD）· …` 条目；Release body 取自它 |

## 1. 版本与工作区对表

```powershell
Select-String -Pattern 'versionCode|versionName' androidApp/build.gradle.kts | Select-Object -First 2   # 本机未装 rg，勿改回 rg
git fetch origin
git rev-list --left-right --count origin/main...HEAD   # 期望 0  0
git status --short                                     # 只应剩 .mimosa/ 之类未跟踪产物
```

### 1.1 ⚠️ 含适配脚本改动时：私有仓库必须已 push（漏做会让包内新脚本被热更新顶掉）

**适用**：本版本包含适配脚本改动（新增/修改 `shared/assets/offline_repo/schools/resources/**/*.js`，
或重建了 `school_index.pb`）。纯 UI / 逻辑改动可跳过本小节。

**为什么必须查**：APK 里的适配脚本是「发版那一刻的快照」，而 App 每次启动还会用远端热更新覆盖它。
远端若比包内旧，**包内的新脚本会被 OTA 覆盖回旧版** —— 症状是「原有功能正常、本次新增的功能全部失效」，
极易误判成新代码写错而去翻 Kotlin。**2026-10-04 实际发生过一次**（成绩 / 空教室 / 学业三处钩子全失效，
根因是私有仓库只重建未推送）。

```powershell
cd .adapter_private
git status --short                                        # 必须为空
git rev-parse HEAD; git rev-parse origin/main             # 两者必须相同
cd ..
```

线上核验（**不要只看本地 git 状态**；现成脚本 `build_qa/verify_live_adapters.py` 可改清单复用）：

```powershell
$s = ((Select-String -Path adapter_secrets.properties -Pattern '^adapter\.appSecret\s*=').Line -split '=',2)[1].Trim()
# ① 清单可达，file_count 与本次一致
Invoke-RestMethod 'https://adapter.shangke.asia/index.json' -Headers @{'X-App-Secret'=$s} | Select-Object file_count
# ② 本次改动的脚本：远端 sha256 应等于本地 sha256（逐字节，含行尾）
# ③ 实际下载正文，确认含本次新增的特征串
```

> Worker 缓存最长 5 分钟：刚推完立刻核验可能仍是旧值，等约 5 分钟再确认；
> **不要因一次核验失败就重复推送** —— 先确认远端 git 已更新，再等缓存过期。

**发版前显式跑一次带私有仓库要求的体检**（本机应有私有仓库，缺失即失败，不要静默跳过）：

```powershell
python scripts/check_adapters.py --require-private      # 含双落点主仓库↔私有仓一致性
```

> 为何要 `--require-private`：双落点校验**只在 `.adapter_private/` 存在时才运行**，
> 缺失时旧版脚本只记 WARN，而 WARN 默认不影响退出码 ⇒ 门禁静默变绿。
> 发版是本项目最需要这条防线的场合，故必须让它**显式失败**而非静默跳过。
> 注：`.githooks/pre-commit` 在暂存了适配脚本时已默认带上该开关。

细则与成因见 `docs/adapter-sop.md` §6.4、§8.1、§9 第 16 条。

## 2. 本地构建正式版

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :androidApp:assembleRelease --console=plain
```

- `validateSigningRelease` 必须通过。
- 整体 `UP-TO-DATE` 时，用「产物 mtime 晚于 HEAD 提交时间」证明产物已含本次改动：

```powershell
Get-ChildItem androidApp\build\outputs\apk\release\*.apk | Select-Object Name,Length,LastWriteTime
git log -1 --format='%h %ad' --date=iso
```

晚于即合格；否则加 `--rerun-tasks` 重打。

## 3. 逐包校验（三条全过才算合格）

```powershell
$env:Path="D:\Android\SDK\build-tools\37.0.0;$env:Path"
foreach($a in Get-ChildItem androidApp\build\outputs\apk\release\*.apk){
  & aapt2 dump badging $a.FullName | Select-String '^package:|^native-code:'
  & apksigner verify --print-certs $a.FullName | Select-String 'SHA-256 digest'
}
```

1. `versionCode` / `versionName` 与本次版本一致；
2. `native-code` 每包**只有一项**（arm64-v8a / armeabi-v7a / x86_64 各一）；
3. V2 证书 SHA-256 = `4ae49d8c97d881c7c249115b833f932c70f9b429624e88e68807e8fc2232475f`（三包一致且与历史一致）。

## 4. 创建 GitHub Release（gh 最快路径；REST API 备用）

### 4.A 最快路径：gh 一条命令建草稿并上传（推荐）

```powershell
# 4.A.1 从 CHANGELOG 抽取对应版本段落为 notes 临时文件（UTF-8 无 BOM；把 vX\.Y\.Z 换成实际版本）
# ⚠️ Get-Content 必须带 -Encoding utf8：PS 5.1 默认按 GBK 解码无 BOM 的 UTF-8 文件，
# 抽出的段落会是 mojibake（2026-09-30 实测 v4.64.2/v4.64.3 因此乱码，已用 API PATCH 修复）。
$cl = Get-Content CHANGELOG.md -Raw -Encoding utf8
$body = [regex]::Match($cl,'(?ms)^### vX\.Y\.Z.*?(?=^### |\z)').Value.TrimEnd()
[System.IO.File]::WriteAllText("$PWD\.release-notes.md",$body)

# 4.A.2 一条命令：建草稿 + 上传全部 APK（gh 自管令牌；PowerShell 不展开通配符，须先取全路径数组）
$apks = (Get-ChildItem androidApp\build\outputs\apk\release\*.apk).FullName
$sha = (git rev-parse HEAD).Trim()
gh release create vX.Y.Z --draft --target $sha --title "vX.Y.Z · 标题（取 CHANGELOG 条目）" --notes-file .release-notes.md $apks

# 4.A.3 先过防乱码门禁（核对远端正文与 CHANGELOG 逐字一致；不通过就停手排查，不得转正式）
$env:PYTHONUTF8=1; python scripts/check_release_body.py vX.Y.Z
# 通过后再转正式，然后清理临时文件
gh release view vX.Y.Z --json isDraft,assets --jq '{isDraft,assets:[.assets[]|{name,size}]}'
gh release edit vX.Y.Z --draft=false
Remove-Item .release-notes.md -Force

# 4.A.4 核验 tag 指向发布提交
git ls-remote --tags origin 'refs/tags/vX.Y.Z'
```

收尾确认：`draft=false`、`prerelease=false`、`assets=3`，tag 指向发布提交。

### 4.B 备用：REST API 逐步操作（gh 不可用时）

```powershell
# 4.0 取令牌：gh 已登录，直接取（不落盘、不打印）
$tok = (gh auth token).Trim()
$h = @{ Authorization="token $tok"; 'User-Agent'='shangke-release'; Accept='application/vnd.github+json' }

# 4.1 body = CHANGELOG 对应段落（发版前务必确认该段落存在；-Encoding utf8 必带，原因见 §4.A.1 警告）
$cl = Get-Content CHANGELOG.md -Raw -Encoding utf8
$body = [regex]::Match($cl,'(?ms)^### vX\.Y\.Z.*?(?=^### |\z)').Value.TrimEnd()

# 4.2 先建草稿；tag 由 API 创建并指向 HEAD
$sha = (git rev-parse HEAD).Trim()
$payload = @{ tag_name='vX.Y.Z'; target_commitish=$sha; name='vX.Y.Z · 标题'; body=$body; draft=$true; prerelease=$false } | ConvertTo-Json -Depth 4
$rel = Invoke-RestMethod -Uri 'https://api.github.com/repos/qiqqqqq517/shangkeschedule/releases' -Method Post `
  -Headers $h -ContentType 'application/json; charset=utf-8' `
  -Body ([System.Text.Encoding]::UTF8.GetBytes($payload))
"id=$($rel.id)"

# 4.3 上传三个 ABI 包（二进制用 curl，避免 PowerShell 编码问题）
foreach($a in Get-ChildItem androidApp\build\outputs\apk\release\*.apk){
  & curl.exe -sS -X POST -H "Authorization: token $tok" -H 'User-Agent: shangke-release' `
    -H 'Content-Type: application/vnd.android.package-archive' --data-binary "@$($a.FullName)" `
    "https://uploads.github.com/repos/qiqqqqq517/shangkeschedule/releases/$($rel.id)/assets?name=$($a.Name)"
}

# 4.4 资产齐了再转正式
Invoke-RestMethod -Uri "https://api.github.com/repos/qiqqqqq517/shangkeschedule/releases/$($rel.id)" `
  -Method Patch -Headers $h -ContentType 'application/json' -Body (@{ draft=$false } | ConvertTo-Json)

# 4.5 核验
git ls-remote --tags origin 'refs/tags/vX.Y.Z'
```

收尾确认：`draft=false`、`prerelease=false`、`assets=3`，tag 指向发布提交。

## 5. 官网同步

- `website/changelog.html`：时间轴加 `tl-item`（`id="vXYZ"`）、TOC 顶部加锚点、页头 `data-version` / `data-version-code`。
- `website/assets/js/site.js`：`SITE.version` / `SITE.versionCode`（页头版本号由它渲染，改一处即可）。
- `website/sitemap.xml`：`/changelog` 的 `lastmod`。
- **三类「兜底文本」（易漏，P1-9）**：`index.html` / `features.html` / `changelog.html` 里 `data-version` / `data-version-code` / `data-asset-name` 元素的**标签内静态文本**。它们被 `site.js` 在运行时覆写，但**禁用 JS / 爬虫 / 查看源码**时看的就是这些写死的值；实测曾停在 `v3.71.2`／`290`（落后约 100 个版本）。同步工具：`python scripts/sync_web_version_fallbacks.py --check|--apply`（已接入 `update_website.py` 第 4 步）。

一条命令完成以上全部同步（**不含部署**）：

```powershell
python tools\update_website.py --no-deploy
```

同步后必须先过门禁，确认「App / site.js / README 徽章 / 各页兜底文本 / version.json 指向最新 tag」五处一致：

```powershell
python scripts\check_version_sync.py
```

### 5.1 部署（硬规则：只同步不部署 = 线上仍是旧版）

**这一步以前不在本 runbook 里、发版流程又一律用 `--no-deploy`，导致线上曾停在 `v4.69.0` 落后 5 个版本（P1-55）。**

```powershell
npx wrangler pages deploy website --project-name shangkeschedule --branch main
```

（`python tools\update_website.py` **不带** `--no-deploy` 时会在同步后自动执行上面这条；`--no-deploy` 仅限本地试跑/预览。）

部署后核验线上实际内容（**必须用 `curl.exe`**；Cloudflare Pages 有缓存，未生效时**间隔数分钟复查，不要因一次核验失败就重复部署**）：

```powershell
curl.exe -s https://shangke.asia/ | Select-String -Pattern 'data-version'
curl.exe -s https://shangke.asia/version.json
```

期望：页面里的版本号与 `version.json` 都与本版一致（`version.json` 按「最新已真实发布」口径，可低于当前代码版本，但必须等于最新 tag）。

## 6. 夸克网盘同步（每次发版必做）

分发目录是夸克网盘「上课-课程表」（面向用户的下载页），里面只留当版最新包，旧包统一收进其子目录「旧版本在此」。

```powershell
python scripts\quark_publish_apk.py --dry-run   # 先预览将上传/归档哪些包（不改动网盘）
python scripts\quark_publish_apk.py             # 上传当版 arm64 包 + 归档其余 .apk
```

脚本行为与约束：

- 按 `androidApp/build.gradle.kts` 的 `versionName` 取 `shangke-vX.Y.Z-arm64-v8a-release.apk`（只传 arm64-v8a 一种 ABI）；先在 `androidApp/build/outputs/apk/release/` 找，找不到回退仓库外的 `..\正式版-arm64\`。
- **发版闸**：默认要求 `vX.Y.Z` 在 GitHub 上已是「已发布（非 draft）」Release，否则中止——防止把未发版构建公开到分发目录；确需上传才加 `--allow-unreleased`。
- 「上课-课程表」与「旧版本在此」的 fid **按目录名现场解析**，不写死（文件夹分享/重建后 fid 前缀会变，硬编码必失效）。
- 同名包已存在即跳过上传，脚本可重复执行（幂等）；随后把该目录内**其余全部 .apk** 分批（每批 ≤100）移入「旧版本在此」。
- 前置：本机已安装并授权夸克网盘 Skill（`C:\Users\30458\.dsh\skills\quarkclouddrive\`）；脚本会自动跑一次该 Skill 的 `scripts/install.sh` 作安装检查。Skill 的 CLI 要求每次调用带 `--session-input` / `--session-id`，脚本已自动附加。
- 收尾核验：夸克「上课-课程表」内有且仅有当版一个 .apk，旧包已出现在「旧版本在此」。

## 7. 提交与推送

```powershell
git add -- website/changelog.html website/assets/js/site.js website/sitemap.xml   # 一条命令显式列出全部文件，禁用 git add . / -A
git commit -m "docs(website): 同步 vX.Y.Z 更新日志 vX.Y.Z"
git fetch origin; git rev-list --left-right --count origin/main...HEAD
git push origin main
git push gitee main
```

`origin` 推送失败的已知故障与处理（**不要**用其它"绕路"办法，都会失败）：

| 现象 | 处理 |
| --- | --- |
| `schannel: failed to receive handshake` | 等约 20 秒，用 `git ls-remote --heads origin main` 探活，恢复后**原样重推** |
| `Recv failure: Connection was reset`（清空代理直连） | 不要走这条路径；回到带代理的原样重推 |
| `SSL_ERROR_SYSCALL`（`http.sslBackend=openssl`） | 不要切 openssl 后端 |

## 8. 工作日志

```powershell
python tools\worklog\worklog.py append --date YYYY-MM-DD --version X.Y.Z --code NNN --type BUILD `
  --summary "发布 GitHub Release vX.Y.Z：上传 N 个 ABI 正式包并同步官网更新日志" --body-file <正文文件>
```

正文须含：构建命令与结果、三包体积、versionCode / ABI / 签名校验结论、Release id 与 URL、
tag 指向、官网同步提交、origin 与 gitee 推送状态、以及验证状态（是否装机）。
