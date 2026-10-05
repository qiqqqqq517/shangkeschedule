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
    text = read(SITE_JS)
    ver = re.search(r"version:\s*'([^']+)'", text)
    code = re.search(r"versionCode:\s*'(\d+)'", text)
    if not ver or not code:
        return None, None
    return ver.group(1), code.group(1)


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
        s_name, s_code = site_versions()
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
    if s_name is None or s_code is None:
        print("[FAIL] 无法从 site.js 解析 SITE.version / SITE.versionCode")
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

    if not quiet:
        for label, a_src, a_val, b_src, b_val in checks:
            mark = "OK  " if a_val == b_val else "FAIL"
            print(f"  [{mark}] {label}: {a_src}={a_val}  {b_src}={b_val}")
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

    if failed or vj_issues:
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