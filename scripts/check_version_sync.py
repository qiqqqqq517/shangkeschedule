# -*- coding: utf-8 -*-
"""
版本号一致性门禁：`androidApp/build.gradle.kts` ↔ `website/assets/js/site.js` ↔ `README.md`。

背景：发版时版本号被写进多个地方，而它们都靠人工或被 gitignore 的
`tools/update_website.py` 同步：

  1. `androidApp/build.gradle.kts` → `versionCode` / `versionName`（决定 APK 真实版本）
  2. `website/assets/js/site.js` → `SITE.version` / `SITE.versionCode`
     （运行时覆写官网页上所有 `data-version` / `data-version-code` 占位符）
  3. `CHANGELOG.md` 顶部条目 → 由 `tools/update_website.py` 自动追补进
     `website/changelog.html` 时间轴（该链路已自动化，故此处不断言）
  4. `README.md` 顶部版本徽章 → **无任何脚本同步**，只能人工改

历史状态：`.githooks/` 曾长期没有任何版本检查，`dco.yml` / `check-pr-source.yml`
都不看版本号，`tools/` 整目录被 gitignore（AGENTS.md 明文红线：新 worktree 里根本
没有 `update_website.py`）。结果就是「App 已 bump、官网仍显示旧版」可以静默发生 ——
用户从官网点下载，拿到的却是上一版。实测 README 徽章曾停在 3.56.6 而 App 已 4.64.24。

本门禁把 (1)、(2)、(4) 对齐，不一致即 exit 1。已挂在 `.githooks/pre-commit`，
仅当本次提交触及 `androidApp/build.gradle.kts` / `website/assets/js/site.js` /
`CHANGELOG.md` 时触发。

用法：
    python scripts/check_version_sync.py            # 检查，不一致时 exit 1
    python scripts/check_version_sync.py --quiet    # 仅输出结论
"""
import io
import os
import re
import subprocess
import sys

# 输出编码固定 UTF-8。
#
# 注意（P2-36）：此前 stderr 被绑到 **sys.stdout.buffer** —— 复制粘贴错误，
# 导致 Windows 下 stderr 全部改道 stdout，`2>` 重定向与「只看 stderr 判错误」
# 的调用方（hook / CI 日志收集）全部读到空或错位。两路必须各绑自己的 buffer。
if sys.stdout.encoding and sys.stdout.encoding.lower() not in ("utf-8", "utf8"):
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
if sys.stderr.encoding and sys.stderr.encoding.lower() not in ("utf-8", "utf8"):
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GRADLE = os.path.join(ROOT, "androidApp", "build.gradle.kts")
SITE_JS = os.path.join(ROOT, "website", "assets", "js", "site.js")
README = os.path.join(ROOT, "README.md")
VERSION_JSON = os.path.join(ROOT, "website", "version.json")
WEB_DIR = os.path.join(ROOT, "website")


class MissingFile(Exception):
    """必需文件不存在（区别于「版本不一致」）。"""


def read(path):
    """读取必需文件；缺失时抛 MissingFile 而非裸 traceback。

    P2-37：此前缺文件会走未捕获的 FileNotFoundError，退出码与「版本不一致」
    同为 1，在 hook 层两者不可区分 —— 使用者看到同样的「提交被拦截」，
    无法判断是「版本漂移」还是「仓库不完整」。
    """
    if not os.path.exists(path):
        raise MissingFile(path)
    with open(path, encoding="utf-8") as f:
        return f.read()


def gradle_versions():
    text = read(GRADLE)
    code = re.search(r'^\s*versionCode\s*=\s*(\d+)\s*$', text, re.M)
    name = re.search(r'^\s*versionName\s*=\s*"([^"]+)"\s*$', text, re.M)
    if not code or not name:
        return None, None
    return code.group(1), name.group(1)


def version_json_manifest():
    """读取 website/version.json —— App「检查更新」的线上版本源。

    返回 dict 或 None（文件缺失 / 无法解析）。

    语义提醒：本文件驱动 App 的「发现新版本」提示，必须指向**最新已真实发布**
    的版本，而不是当前 build.gradle.kts 里尚未发版的版本。因此它**不参与**
    下方「必须与 gradle 完全相同」的判据，而由 check_version_json() 单独判定。
    """
    import json
    p = VERSION_JSON
    if not os.path.exists(p):
        return None
    try:
        data = json.loads(read(p))
    except Exception:  # noqa: BLE001
        return None
    return data if isinstance(data, dict) else None


def version_json_issues(g_code, g_name):
    """校验 website/version.json 的**形制**与**合理区间**。

    历史缺陷（P1-56）：该文件冻结在 4.66.0/420，63 次版本迭代 0 次同步，
    而三道防线（本脚本判据集 / pre-commit 触发集 / update_website.py 写入清单）
    全都不覆盖它 ⇒ 存量用户被告知「发现新版本 4.66.0」、新用户永远「已是最新」。

    判据（只在**形制**与**区间**上判失败，不要求等于 gradle 版本）：
      1. versionCode 为正整数、versionName 为非空字符串；
      2. versionName 形如 X.Y.Z；
      3. version.json 的 versionCode **不得高于** gradle 的（指向未来版本
         = 用户下载不到，属真缺陷）；
      4. shareToken 必须与 site.js 的 quarkCode 同源（P2-58 同族：
         同一口令两处写法不同，至少一处不可用）。
    """
    issues = []
    m = version_json_manifest()
    if m is None:
        issues.append("website/version.json 缺失或无法解析为 JSON 对象")
        return issues

    vc, vn = m.get("versionCode"), m.get("versionName")
    if not isinstance(vc, int) or vc <= 0:
        issues.append(f"versionCode 非正整数：{vc!r}")
    if not isinstance(vn, str) or not vn.strip():
        issues.append(f"versionName 非空字符串：{vn!r}")
    elif not re.fullmatch(r"\d+\.\d+\.\d+", vn.strip()):
        issues.append(f"versionName 形如 X.Y.Z：{vn!r}")

    if isinstance(vc, int) and vc > int(g_code):
        issues.append(
            f"versionCode {vc} 高于 build.gradle.kts 的 {g_code}（指向尚未发布的版本，"
            f"用户下载不到）"
        )

    # shareToken ↔ site.js quarkCode 同源
    token = m.get("shareToken")
    if isinstance(token, str) and token.strip():
        site_text = read(SITE_JS) if os.path.exists(SITE_JS) else ""
        qm = re.search(r"quarkCode:\s*'([^']+)'", site_text)
        if qm and qm.group(1).strip() != token.strip():
            issues.append(
                f"shareToken 与 site.js 的 quarkCode 不同源："
                f"version.json={token!r} site.js={qm.group(1)!r}"
            )
    return issues


def site_versions():
    """从 `const SITE = { ... }` 对象字面量里取 version / versionCode。

    P1-33：原实现是 `re.search(r"version:\\s*'([^']+)'", text)` —— **全文首个匹配**。
    site.js 里任何一处更早出现的 `version:`（例如别人新增的另一个配置块、或一句带引号的
    注释）都会抢先命中；只要那处的值恰好与 gradle 相同，门禁就 rc=0「通过」，
    而**真正生效的** `SITE.version` 仍是陈旧的 ⇒ 典型 fail-open。
    现改为：先定位 `const SITE = {` 到对应 `};` 的对象体，**只在该体内**取值，
    并要求 `version` / `versionCode` 在体内**各恰好出现一次**（多了即判失败，
    因为无法确定哪个生效）。
    """
    text = read(SITE_JS)
    m = re.search(r"const\s+SITE\s*=\s*\{(.*?)\n\};", text, re.S)
    if not m:
        return None, None, "未找到 `const SITE = { ... };` 对象字面量（脚本结构变了？）"
    body = m.group(1)
    vers = re.findall(r"\bversion:\s*'([^']+)'", body)
    codes = re.findall(r"\bversionCode:\s*'([^']+)'", body)
    if len(vers) != 1:
        return None, None, f"SITE 体内 version 键出现 {len(vers)} 次（必须恰好 1 次）：{vers}"
    if len(codes) != 1:
        return None, None, f"SITE 体内 versionCode 键出现 {len(codes)} 次（必须恰好 1 次）：{codes}"
    return vers[0], codes[0], None


# site.js 运行时覆写的三个属性 → 该元素的**静态兜底文本**应当是什么
def html_fallback_issues(g_code, g_name):
    """校验 website/*.html 里被 site.js 覆写的「兜底文本」。

    P1-9：site.js 会在运行时把
      `[data-version]`      → `'v' + SITE.version`
      `[data-version-code]` → `SITE.versionCode`
      `[data-asset-name]`   → `shangke-v<SITE.version>-<abi>-release.apk`
    写进对应元素。HTML 里**写死的静态文本**是「禁用 JS / 爬虫 / 查看源码」时
    用户实际看到的内容 —— 实测 index.html / features.html 停在 `v3.71.2` / `290`
    （落后约 100 个版本），属**用户可见**的陈旧版本。

    枚举来自 `website/*.html` **目录穷举**，不是关键词搜索（否则会漏载体）。
    """
    issues = []
    scanned = 0
    # 注意交替式顺序：Python 的 `|` 取**最左**匹配，把 `version` 放在
    # `version-code` 前面会让 `data-version-code` 被误判成 `data-version`
    # （实测这样把「版本号 482」判成「应等于 v4.74.12」，是量具自身的假阳性）。
    # 长串必须排在短串前面。
    pat = re.compile(
        r"<(\w+)([^>]*\bdata-(asset-name|version-code|version)\b[^>]*)>([^<]*)</\1>",
        re.S | re.I,
    )
    for fn in sorted(os.listdir(WEB_DIR)):
        if not fn.endswith(".html"):
            continue
        p = os.path.join(WEB_DIR, fn)
        if not os.path.exists(p):
            continue
        txt = read(p)
        for m in pat.finditer(txt):
            scanned += 1
            ln = txt[: m.start()].count("\n") + 1
            kind = m.group(3).lower()
            attrs = m.group(2)
            fb = m.group(4).strip()
            if kind == "version":
                expect = "v" + g_name
            elif kind == "version-code":
                expect = g_code
            else:
                am = re.search(r'data-asset-name="([^"]+)"', attrs)
                abi = am.group(1) if am else "?"
                expect = f"shangke-v{g_name}-{abi}-release.apk"
            if fb != expect:
                issues.append(
                    f"{fn}:{ln} [data-{kind}] 兜底文本 = {fb!r}，应为 {expect!r}"
                )
    if scanned == 0:
        # 「空比较」不得算通过：一个都没扫到说明选择器/结构变了，必须显式报出。
        issues.append(
            "website/*.html 里没有扫到任何 data-version / data-version-code / "
            "data-asset-name 元素 —— 选择器或页面结构可能已变，本次**未校验**"
        )
    return issues


def version_json_latest_release_issues(vj):
    """校验 `version.json` 是否指向**最新的已发布版本**（需要 git tag）。

    P1-34 的另一半：`version.json` 的口径是「最新**已真实发布**的版本」，
    而「已发布」在本仓只有 `git tag` 能证明（CHANGELOG 里会写「未构建 APK、
    未发 Release」的条目，4.74.2~4.74.13 就是这种）。因此：

      · 取所有形如 `vX.Y.Z` 的 tag，比较出版本最大的那个 `latest_tag`；
      · 若 `version.json.versionName` 与 `latest_tag` 不一致 ⇒ **报失败**
        （说明要么漏更新，要么指向了尚未发布的版本）。

    注意「空比较不得算通过」：一个 tag 都没有、或拿不到 tag 列表时，
    返回一条**显式的 SKIP 说明**（记为 INFO 而非失败），不得静默当作通过。
    """
    if vj is None:
        return [], ["未读取到 website/version.json，跳过「是否落后最新已发布版本」判定"]
    name = str(vj.get("versionName") or "").strip()
    if not name:
        return [], ["website/version.json 缺 versionName，跳过「是否落后最新已发布版本」判定"]

    try:
        out = subprocess.run(
            ["git", "tag", "--list", "v*"],
            cwd=ROOT, capture_output=True, text=True, timeout=15,
        )
    except Exception as e:  # noqa: BLE001
        return [], [f"无法调用 git 读取 tag（{type(e).__name__}），跳过该项判定"]
    if out.returncode != 0:
        return [], ["git tag 返回非 0（可能不在 git 工作树内），跳过该项判定"]

    tags = [t.strip() for t in out.stdout.splitlines() if re.fullmatch(r"v\d+\.\d+\.\d+", t.strip())]
    if not tags:
        return [], ["仓库内没有形如 vX.Y.Z 的 tag，跳过「是否落后最新已发布版本」判定"]

    def key(t):
        return tuple(int(x) for x in t[1:].split("."))

    latest = max(tags, key=key)
    if name != latest[1:]:
        return [
            f"website/version.json 指向 {name}，但仓库最新的已发布 tag 是 {latest}"
            f"（差 {len(tags)} 个 tag 中共取最大）—— 应指向最新已发布版本，"
            f"否则存量用户会被告知一个陈旧/不存在的新版本号"
        ], []
    return [], [f"version.json = {name}，与最新已发布 tag {latest} 一致"]


def readme_version():
    """README.md 顶部版本徽章 `badge/version-X.Y.Z-<颜色>`。

    找不到徽章时返回 None —— 「用户主动删掉徽章」不算版本不一致，故不判失败，
    只在结论里提示跳过。
    """
    m = re.search(r"badge/version-(\d+(?:\.\d+)+)-", read(README))
    return m.group(1) if m else None


def main():
    quiet = "--quiet" in sys.argv

    try:
        g_code, g_name = gradle_versions()
        s_name, s_code, s_problem = site_versions()
        r_name = readme_version()
    except MissingFile as e:
        # 与「版本不一致」区分开（P2-37）：缺文件是环境问题，不是版本漂移。
        print(f"[FAIL] 必需文件不存在：{e}", file=sys.stderr)
        print("       这通常意味着在**不完整的检出**里运行本门禁。", file=sys.stderr)
        print(f"       仓库根推定：{ROOT}", file=sys.stderr)
        return 2

    if g_code is None or g_name is None:
        print("[FAIL] 无法从 build.gradle.kts 解析 versionCode / versionName")
        return 1
    if s_problem is not None or s_name is None or s_code is None:
        # P1-33：结构异常（找不到 SITE 对象体 / 同名键出现多次）必须**显式失败**，
        # 不能静默降级成「跳过该项」—— 那正是原实现的 fail-open 形态。
        print(f"[FAIL] 解析 site.js 失败：{s_problem}")
        print("       修法：保持 `const SITE = { version: 'x.y.z', versionCode: 'NNN', ... };`"
              " 的单一对象写法。")
        return 1

    checks = [
        ("versionCode", "build.gradle.kts", g_code, "site.js SITE.versionCode", s_code),
        ("versionName", "build.gradle.kts", g_name, "site.js SITE.version", s_name),
    ]
    if r_name is not None:
        checks.append(
            ("versionName", "build.gradle.kts", g_name, "README.md 版本徽章", r_name)
        )
    else:
        # P2-35：徽章缺失/换样式会让这一项**静默降级**（等于少查一处）。
        # 不在 --quiet 下完全不可见，容易被读成「全部通过」。故即便 quiet 也提示到 stderr。
        print("  [SKIP] 未在 README.md 找到版本徽章（badge/version-X.Y.Z-<颜色>），"
              "该项未校验 —— 若确实换过徽章样式，请同步更新本脚本的正则",
              file=sys.stderr)

    failed = [(label, a_src, a_val, b_src, b_val)
              for label, a_src, a_val, b_src, b_val in checks if a_val != b_val]

    # website/version.json —— 形制 + 区间（不要求等于 gradle 版本，见函数文档）
    vj_issues = version_json_issues(g_code, g_name)

    # website/*.html 里被 site.js 覆写的兜底文本（P1-9）
    html_issues = html_fallback_issues(g_code, g_name)

    # version.json 是否指向最新已发布版本（P1-34 的另一半，需 git tag）
    vj_release_issues, vj_skips = version_json_latest_release_issues(version_json_manifest())

    if not quiet:
        for label, a_src, a_val, b_src, b_val in checks:
            mark = "OK  " if a_val == b_val else "FAIL"
            print(f"  [{mark}] {label}: {a_src}={a_val}  {b_src}={b_val}")
        if html_issues:
            print(f"  [FAIL] website/*.html 兜底版本：{len(html_issues)} 处与真值不符")
        else:
            print("  [OK  ] website/*.html 兜底版本与真值一致")
        for it in vj_skips:
            print(f"  [INFO] {it}")
        _m = version_json_manifest()
        if _m is None:
            print("  [FAIL] website/version.json 缺失或无法解析")
        elif vj_issues:
            print(f"  [FAIL] website/version.json: "
                  f"{_m.get('versionName')}/{_m.get('versionCode')}")
        else:
            print(f"  [OK  ] website/version.json: "
                  f"{_m.get('versionName')}/{_m.get('versionCode')}"
                  f"（应指向最新已发布版本，可低于当前代码版本）")

    if vj_issues:
        print()
        print("[version-sync] 未通过：website/version.json 形制或区间不合法")
        for it in vj_issues:
            print(f"  - {it}")
        print("  影响：App「检查更新」读该文件（AppExternalLinks.kt → UpdateCheckClient），")
        print("        值不合法会让存量用户收到错误提示、新用户永远「已是最新」。")
        print("  注意：该值应等于**最新已真实发布**的版本（有 tag + Release 的那个），")
        print("        不是 build.gradle.kts 里尚未发版的版本。")

    if failed:
        print()
        print("[version-sync] 未通过：对外展示的版本与 App 实际版本不一致")
        for label, a_src, a_val, b_src, b_val in failed:
            print(f"  - {label}: {a_src}={a_val}，但 {b_src}={b_val}")
        print("  影响：官网 / README 会把用户导向错误的版本说明或下载链接。")
        print("  修法：")
        if any(b_src.startswith("site.js") for _, _, _, b_src, _ in failed):
            print("    · 同步 website/assets/js/site.js 的 SITE.version / SITE.versionCode")
        if any("README" in b_src for _, _, _, b_src, _ in failed):
            print("    · 同步 README.md 顶部版本徽章（badge/version-X.Y.Z-<颜色>）")
        print("    · 勿改 build.gradle.kts —— 那是 APK 的真实版本。")

    if html_issues:
        print()
        print("[version-sync] 未通过：website/*.html 里的兜底版本与 App 实际版本不一致")
        for it in html_issues:
            print(f"  - {it}")
        print("  影响：禁用 JS、爬虫、查看源码时看到的是**写死的**旧版本号，")
        print("        与站点其它位置（site.js 覆写后）自相矛盾。")
        print("  修法：把这些元素标签内的静态文本改成与真值一致；")
        print("        `tools/update_website.py` 已一并同步这三类兜底文本。")

    if vj_release_issues:
        print()
        print("[version-sync] 未通过：website/version.json 未指向最新已发布版本")
        for it in vj_release_issues:
            print("  - " + it)

    if failed or vj_issues or html_issues or vj_release_issues:
        return 1

    _m = version_json_manifest()
    print()
    print(f"[version-sync] 通过：App 与对外展示版本一致（v{g_name} / {g_code}）")
    if _m is not None:
        print(f"[version-sync] version.json = {_m.get('versionName')}/{_m.get('versionCode')}"
              f"（最新已发布口径）")
    return 0


if __name__ == "__main__":
    sys.exit(main())