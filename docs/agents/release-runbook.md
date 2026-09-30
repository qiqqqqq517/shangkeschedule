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

## 6. 提交与推送

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

## 7. 工作日志

```powershell
python tools\worklog\worklog.py append --date YYYY-MM-DD --version X.Y.Z --code NNN --type BUILD `
  --summary "发布 GitHub Release vX.Y.Z：上传 N 个 ABI 正式包并同步官网更新日志" --body-file <正文文件>
```

正文须含：构建命令与结果、三包体积、versionCode / ABI / 签名校验结论、Release id 与 URL、
tag 指向、官网同步提交、origin 与 gitee 推送状态、以及验证状态（是否装机）。
