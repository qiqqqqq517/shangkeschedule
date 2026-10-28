# 正式版构建与发布手册

> 仅在正式版构建/发布任务加载。通用开发、版本迭代、授权与凭据故障见 [开发流程](workflow.md)。
> 本文是发布细则的唯一维护位置；历史日志记录当时结果，不替代本流程。

## 0. 完成标准与构建选择

仅请求构建时完成 §1–§3 与构建记录；用户要求发版时才执行下面的完整发布流程。

完整发版必须完成：CHANGELOG → 发布提交双端同步 → 构建 → 三包校验 → GitHub Release → 官网单独提交并部署 → arm64 归档 → 夸克同步 → 日志与 IMA。失败或待授权的环节如实保留为未完成。

- **本地/CI 二选一**，两者均可用于正式发布，实际产物均须过同一套校验。`android-build.yml` 仅手动 `workflow_dispatch`，环境为 `Release-Signing`。
- 对外发布用手动 `gh release create`；`android-release.yml` 保持 disabled，不启用自动发布链路。
- 构建输入必须对应明确且已推送的发布提交；不混入其他会话改动，不重复 bump 已迭代版本。
- CI 环境保护/secret 等远端状态在选择 CI 时现场核对，不沿用历史运行 ID 或审批等待时长。签名 secret 名为 `KEYSTORE_BASE64/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD`；不输出值。有 push 权限者修改 workflow 可读取仓库 secret，相关权限策略变更需用户授权。
- 资源仓库克隆失败时 workflow 可回退内置索引/资源；确认回退完整再继续，不把历史字节数/目录数当校验依据。

| 本机依赖 | 检查 |
| --- | --- |
| JBR | `C:\Program Files\Android\Android Studio\jbr`，每条 Gradle 命令前显式设置并确认存在 |
| SDK 工具 | `D:\Android\SDK\build-tools\37.0.0\`，含 `aapt2.exe/apksigner.bat` |
| 本地签名 | `androidApp/keystore.properties` + `androidApp/shangkeschedule-release.jks`，已忽略，不提交 |
| GitHub | `gh auth status` 确认账号 `qiqqqqq517` 与仓库；令牌用 `gh auth token`，不回显 |

## 1. 发布前对表

按开发流程检查工作区、远端和当前版本，读取本次变更相关 IMA 口径。先查同名 tag/Release，避免版本冲突；已完成步骤优先核验并续做，不重建/重复发布。

1. `CHANGELOG.md` 顶部必须已有 `### vX.Y.Z（YYYY-MM-DD）· 发布说明`；按功能/修复/外观/构建写实际内容，包含 versionCode、三个 ABI。**先写声明再发布**。
2. 本批次尚未迭代时按 FIX/FEAT/破坏性变更 bump；已有已验证版本则沿用。
3. 完成源码提交所需验证，提交并推送发布提交，记录 SHA；后续官网提交不改变这个取材 SHA。

完成本节及适配对表后，用 `python scripts/project_checks.py --full` 执行一次静态预检，涉及适配则加 `--require-private`。可复用相同输入/环境的成功证据；不再逐项重复调用同一批子脚本。

```powershell
rg -n 'versionCode|versionName' androidApp/build.gradle.kts
$version='X.Y.Z'                 # 从当前源码取值
$releaseSha=(git rev-parse HEAD).Trim()
gh release view "v$version" --json tagName,isDraft,isPrerelease
git ls-remote --tags origin "refs/tags/v$version*"
```

「尚无 Release」是预期可继续的情况，鉴权/网络失败不能当成未占用。不要因一次查询失败再次创建 Release。

### 1.1 涉及适配脚本/索引时

按 [适配 SOP](../adapter-sop.md) §6.4/§8.1 核对主/私双落点，私有 `.adapter_private` 必须已 commit+push、工作区干净且 `HEAD=origin/main`；再从线上核对本次脚本 sha256 与新增特征串。仅跑 `build_index.py` 不算同步，旧远端可能覆盖包内新脚本。

适配体检由上述统一预检执行（包含真实 node 语法检查与危险 API 基线）；私有仓库缺失不能静默跳过。

用已授权的适配网关认证读取线上索引/正文，不打印密钥。Worker 缓存未刷新时等待后复查，不重复推送。纯 UI/逻辑改动可跳过本小节。

## 2. 构建（二选一）

### 2.A 本地

并行会话用自己的干净 worktree，构建前复制本地配置/签名；本地工具按需复制，不入库。

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :androidApp:assembleRelease --console=plain -PrequireAdapterRemoteConfig=true
$apkDir='androidApp/build/outputs/apk/release'
```

`-PrequireAdapterRemoteConfig=true` 是**发布必带**的门禁：缺 `adapter_secrets.properties`（或 `SHANGKE_ADAPTER_WORKER_URL` / `SHANGKE_ADAPTER_APP_SECRET` 环境变量）时构建立即失败，而不是静默产出「更多 → 自动同步教务系统」提示「远程同步未启用」的包（该提示的唯一成因就是注入值为空，2026-10-26 排查确认）。贡献者/无密钥环境不要加此参数（保持可编译）。

`validateSigningRelease`、R8/lint 等构建检查必须成功。整体 `UP-TO-DATE` 时确认 APK mtime 晚于发布提交时间；无法证明包含本次变更才加 `--rerun-tasks`，不要默认全量重打。

### 2.B CI

```powershell
gh workflow view android-build.yml
gh workflow run android-build.yml --ref main
gh run list --workflow android-build.yml --limit 5 --json databaseId,headSha,status,conclusion
# 选 headSha 等于 $releaseSha 的本次运行，不盲取最新一条
gh run watch <run-id> --exit-status
$apkDir='build_qa/release-X.Y.Z/ci-apks'
gh run download <run-id> -n app-release-apk -D $apkDir
```

仅本次运行 `success` 后取产物。下载后按实际目录取三个 APK，不直接发布未经本机校验的 artifact。

## 3. 三包逐项校验（任一失败停止发布）

实际待上传文件必须恰好是 `shangke-vX.Y.Z-<abi>-release.apk` 三包，ABI 为 `arm64-v8a/armeabi-v7a/x86_64`，无额外混包。

```powershell
$apks=@(Get-ChildItem -LiteralPath $apkDir -Filter "shangke-v$version-*-release.apk")
if ($apks.Count -ne 3) { throw '正式包数量不是 3' }
foreach ($apk in $apks) {
    $badging = & 'D:\Android\SDK\build-tools\37.0.0\aapt2.exe' dump badging $apk.FullName
    if ($LASTEXITCODE -ne 0) { throw "badging 失败：$($apk.Name)" }
    $badging | Select-String '^package:|^native-code:'
    $certs = & 'D:\Android\SDK\build-tools\37.0.0\apksigner.bat' verify --verbose --print-certs $apk.FullName
    if ($LASTEXITCODE -ne 0) { throw "签名验证失败：$($apk.Name)" }
    $certs | Select-String 'Verified using v2|SHA-256 digest'
}
```

逐包核对并留存原始结果：

1. `versionCode/versionName` 等于本次源码版本。
2. `native-code` 每包仅一项，且与文件名对应，三包覆盖三个不同 ABI。
3. V2 验证成功，证书 SHA-256 三包均为 `4ae49d8c97d881c7c249115b833f932c70f9b429624e88e68807e8fc2232475f`。

记录体积与 `Get-FileHash -Algorithm SHA256`；后续上传、下载核验均使用这同一份文件。不能绕过校验在网页手工上传。

## 4. GitHub Release

默认用 gh；确实不可用才读取 [REST 备用流程](release-rest-fallback.md)。PowerShell 读 CHANGELOG 必须显式 UTF-8，正文为空立即停止。

```powershell
$cl=Get-Content CHANGELOG.md -Raw -Encoding utf8
$pattern='(?ms)^### v'+[regex]::Escape($version)+'[（(].*?(?=^### |\z)'
$body=[regex]::Match($cl,$pattern).Value.TrimEnd()
if (-not $body) { throw '未找到本版本发布说明' }
$notesPath=Join-Path $PWD 'build_qa/release-notes.md'
New-Item -ItemType Directory -Path (Split-Path $notesPath) -Force | Out-Null
[System.IO.File]::WriteAllText($notesPath,$body,[System.Text.UTF8Encoding]::new($false))
$apkPaths=@($apks.FullName)
gh release create "v$version" --draft --target $releaseSha --title "v$version · 发布说明" --notes-file $notesPath $apkPaths
```

建草稿/上传成功后才能继续；远端草稿存在时核验并续传，不能重复创建。

```powershell
$env:PYTHONUTF8=1
python scripts/check_release_body.py "v$version"
# 上条门禁通过且三资产名称/体积均正确后才转正式
gh release view "v$version" --json isDraft,isPrerelease,assets
gh release edit "v$version" --draft=false
git ls-remote --tags origin "refs/tags/v$version*"
```

收尾核对 `draft=false/prerelease=false`、三资产名称/体积/SHA-256 与已校验本地文件一致、正文与 CHANGELOG 一致、tag 指向 `$releaseSha`（annotated tag 看解引用提交），同步 gitee 的该 tag。

## 5. 官网：同步、单独提交、部署、线上核验

```powershell
python tools/update_website.py --no-deploy
python scripts/check_version_sync.py
```

这一步只生成文件，不代表部署完成。检查 diff 包含时间线、`SITE.version/versionCode`、sitemap lastmod、`index/features/changelog.html` 中 `data-version/data-version-code/data-asset-name` 的静态兜底文本。`website/version.json` 必须指向最新**已发布** tag；脚本未更新的内容按事实补齐。

显式列出本次实际变更的官网文件，单独提交 `docs(website): 同步 vX.Y.Z 更新日志 vX.Y.Z`，双端推送。至少核对 `website/changelog.html`、`website/assets/js/site.js`、`website/sitemap.xml`、`website/index.html`、`website/features.html`、`website/version.json`，不要照抄固定三文件的旧暂存清单。

```powershell
npx wrangler pages deploy website --project-name shangkeschedule --branch main
curl.exe -s https://shangke.asia/ | Select-String 'data-version'
curl.exe -s https://shangke.asia/version.json
```

线上版本必须与本次正式版一致。缓存未生效时间隔数分钟复查，不重复部署；记录部署结果/URL。若使用 `update_website.py` 不带 `--no-deploy` 自动部署，则不再执行第二次 deploy。

## 6. arm64 本地归档与夸克

1. 将已校验的当版 arm64 包复制到仓库外 `D:\01课程表\正式版-arm64\`，按该目录 `README.md` 再核对版本/单 ABI/V2 证书。一版一包，不入库、不提交。
2. 夸克已安装并授权 `~/.dsh/skills/quarkclouddrive/` 后，先预览再同步：

```powershell
python scripts/quark_publish_apk.py --dry-run
python scripts/quark_publish_apk.py
```

脚本先找 release 输出，缺失回退仓库外归档；CI 产物需先完成上述归档。目录 ID 按名称解析；同名包跳过，分发目录其余所有 APK 移至「旧版本在此」。

默认发版闸要求 GitHub 已有正式 Release；不能为了通过而加 `--allow-unreleased`，仅在用户明确要求分发未发布包时使用。授权失败按 Skill 提示保留待办，不反复登录或宣告完成。回查「上课-课程表」仅一个当版 arm64，体积与 Release 相同，旧包已归档。

## 7. 收尾记录

- 双端核对 main 与发布 tag；推送失败按开发流程有界处理，不清代理/切 SSL 后端。仅清理本次创建的临时工作区。
- 工作日志用统一 append 工具追加 BUILD：构建取材/命令/结果、三包体积、版本/ABI/V2 与资产哈希结论、Release id/URL、tag 指向、官网提交/部署/线上核验、夸克、归档、origin/gitee 状态、是否装机。
- IMA 写入对应发布记录，进度留摘要；未完成步骤明确待办，不把同一全文复制到多个条目。
- 自动依赖提交等远端设置故障只在遇到时排查：GitHub 托管的 `dynamic/dependency-graph/auto-submission` 与仓库的手动 `dependency-submission.yml` 是两条路径；不要为修前者删除后者，远端配置改动须有用户授权。
