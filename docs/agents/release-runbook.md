# 发版实操手册（Release Runbook）

> 规则正文见仓库根目录 `AGENTS.md` 的「正式版构建与发布」节；本文件是**逐条可执行**的实操步骤，
> 由 2026-09-23 发布 v3.66.2 的真实过程沉淀而来（全程无 CI，本地构建 + REST API 发布）。
> 环境前提：Windows + PowerShell，SDK 在 `D:\Android\SDK`，仓库根为 `D:\01课程表\shangkeschedule`。

## 0. 前置条件

| 项 | 值 / 检查方式 |
| --- | --- |
| Java | 环境变量 `JAVA_HOME` 在本机是坏的，每条 Gradle 命令前显式设为 `C:\Program Files\Android\Android Studio\jbr` |
| Android SDK | `D:\Android\SDK`；`build-tools\37.0.0\` 提供 `aapt2` / `apksigner` |
| 签名 | `androidApp/keystore.properties` + `androidApp/shangkeschedule-release.jks`（均在 `.gitignore` 内） |
| `gh` CLI | **本机未安装** —— 发布一律走 GitHub REST API，令牌从 `git credential fill` 取（helper = `manager`，40 位） |
| CHANGELOG | 必须先有 `### vX.Y.Z（YYYY-MM-DD）· …` 条目；Release body 取自它 |

## 1. 版本与工作区对表

```powershell
rg -n 'versionCode|versionName' androidApp/build.gradle.kts | Select-Object -First 2
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

## 4. 创建 GitHub Release（REST API 替代 `gh`）

```powershell
# 4.0 取令牌：来自凭据管理器，不落盘、不打印
$in="protocol=https`nhost=github.com`n`n"; $cred = $in | git credential fill
$tok = (($cred | Select-String '^password=(.*)$').Matches.Groups[1].Value)
$h = @{ Authorization="token $tok"; 'User-Agent'='shangke-release'; Accept='application/vnd.github+json' }

# 4.1 body = CHANGELOG 对应段落（发版前务必确认该段落存在）
$cl = Get-Content CHANGELOG.md -Raw
$body = [regex]::Match($cl,'(?ms)^### v3\.66\.2.*?(?=^### |\z)').Value.TrimEnd()

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
git add -- website/changelog.html website/assets/js/site.js website/sitemap.xml   # 逐个显式，禁用 git add .
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
