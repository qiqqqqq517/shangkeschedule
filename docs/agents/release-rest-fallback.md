# GitHub Release REST 备用流程

> 仅在 gh 发布命令不可用且 API 凭据仍可取得时读取。必须先通过 [发版手册](release-runbook.md) §0–§3。
> 沿用该手册的 `$version/$releaseSha/$body/$apks`，先完成 §4 的 CHANGELOG 抽取，仅跳过 gh 发布命令。正文编码、资产、tag 校验要求相同，任何请求失败停止依赖步骤。

```powershell
# 令牌只在进程内使用，不落盘、不打印
$releaseToken=(gh auth token).Trim()
$headers=@{Authorization="Bearer $releaseToken"; 'User-Agent'='shangke-release'; Accept='application/vnd.github+json'}
$api='https://api.github.com/repos/qiqqqqq517/shangkeschedule'
$payload=@{tag_name="v$version"; target_commitish=$releaseSha; name="v$version · 发布说明"; body=$body; draft=$true; prerelease=$false} | ConvertTo-Json -Depth 4
$rel=Invoke-RestMethod -Uri "$api/releases" -Method Post -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($payload))
```

确认草稿成功后逐包上传原始二进制；任一 HTTP 请求或返回的 name/size 核验失败即停止，不得转正式。不把带认证的完整请求/命令打印到日志。

```powershell
foreach ($apk in $apks) {
    $assetName=[uri]::EscapeDataString($apk.Name)
    $uploadUrl="https://uploads.github.com/repos/qiqqqqq517/shangkeschedule/releases/$($rel.id)/assets?name=$assetName"
    $asset=Invoke-RestMethod -Uri $uploadUrl -Method Post -Headers $headers -ContentType 'application/vnd.android.package-archive' -InFile $apk.FullName
    if ($asset.name -ne $apk.Name -or $asset.size -ne $apk.Length) { throw "资产核验失败：$($apk.Name)" }
}
```

三包上传成功后运行 `python scripts/check_release_body.py vX.Y.Z`，核对远端正文与 CHANGELOG 一致；再检查三资产名称/体积/哈希，全部通过后：

```powershell
Invoke-RestMethod -Uri "$api/releases/$($rel.id)" -Method Patch -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes('{"draft":false}'))
$published=Invoke-RestMethod -Uri "$api/releases/$($rel.id)" -Headers $headers
$published | Select-Object id,html_url,draft,prerelease
Remove-Variable releaseToken,headers
```

核对正式状态、tag 指向发布提交，继续主手册 §5–§7。API 返回已有同名 Release 时查询并核验现有草稿，不能盲目重建。

REST 直接转正式不会自动调用本地百度同步入口。转正式成功后，从 `$apks` 中确认唯一 arm64 包并运行 `python scripts/release_baidu_sync.py --version $version --apk <arm64包完整路径>`；该入口会识别 Release 已发布并只执行同步，不部署官网。如果 `gh release view` 也不可用，则保留待办并报告百度同步未完成，不能把 REST 发布成功当作同步成功。
