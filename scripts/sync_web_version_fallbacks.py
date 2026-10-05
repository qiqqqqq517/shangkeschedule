# -*- coding: utf-8 -*-
"""
同步 `website/*.html` 里被 site.js 运行时覆写的「兜底文本」。

背景（P1-9）：`website/assets/js/site.js` 会在运行时把三类元素的 textContent 改成
由 `SITE.version` / `SITE.versionCode` 派生的值：

    [data-version]      -> 'v' + SITE.version
    [data-version-code] -> SITE.versionCode
    [data-asset-name]   -> 'shangke-v<SITE.version>-<abi>-release.apk'

HTML 里写死的静态文本是**禁用 JS / 爬虫 / 查看源码**时用户实际看到的内容。
实测 `index.html` / `features.html` 停在 `v3.71.2` / `290`（落后约 100 个版本），
而 `tools/update_website.py`（整目录被 gitignore）从不覆写这些载体 ⇒ 长期静默漂移。

用法：
    python scripts/sync_web_version_fallbacks.py --check   # 只报告，有漂移则 exit 1
    python scripts/sync_web_version_fallbacks.py --apply   # 就地改写
"""
import io
import os
import re
import sys

if sys.stdout.encoding and sys.stdout.encoding.lower() not in ("utf-8", "utf8"):
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
if sys.stderr.encoding and sys.stderr.encoding.lower() not in ("utf-8", "utf8"):
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GRADLE = os.path.join(ROOT, "androidApp", "build.gradle.kts")
WEB_DIR = os.path.join(ROOT, "website")

# 交替式顺序：Python `|` 取最左匹配，`version` 在前会把 `data-version-code`
# 误判成 `data-version`。长串必须在前。
PAT = re.compile(
    r"<(\w+)([^>]*\bdata-(asset-name|version-code|version)\b[^>]*)>([^<]*)</\1>",
    re.S | re.I,
)


def truth():
    t = io.open(GRADLE, encoding="utf-8").read()
    name = re.search(r'^\s*versionName\s*=\s*"([^"]+)"\s*$', t, re.M).group(1)
    code = re.search(r"^\s*versionCode\s*=\s*(\d+)\s*$", t, re.M).group(1)
    return name, code


def expected(kind, attrs, name, code):
    if kind == "version":
        return "v" + name
    if kind == "version-code":
        return code
    m = re.search(r'data-asset-name="([^"]+)"', attrs)
    return "shangke-v%s-%s-release.apk" % (name, m.group(1) if m else "?")


def transform(text, name, code):
    """返回 (新文本, 改动列表)。"""
    edits = []

    def repl(m):
        tag, attrs, kind, fb = m.group(1), m.group(2), m.group(3).lower(), m.group(4)
        exp = expected(kind, attrs, name, code)
        if fb.strip() == exp:
            return m.group(0)
        edits.append((kind, fb.strip(), exp))
        # 保留原有前后空白
        lead = fb[: len(fb) - len(fb.lstrip())]
        trail = fb[len(fb.rstrip()):]
        return "<%s%s>%s%s%s</%s>" % (tag, attrs, lead, exp, trail, tag)

    return PAT.sub(repl, text), edits


def main():
    apply_ = "--apply" in sys.argv
    name, code = truth()
    print("真值：v%s / code %s" % (name, code))

    total = 0
    changed_files = 0
    for fn in sorted(os.listdir(WEB_DIR)):
        if not fn.endswith(".html"):
            continue
        p = os.path.join(WEB_DIR, fn)
        txt = io.open(p, encoding="utf-8").read()
        new, edits = transform(txt, name, code)
        total += len(edits)
        if edits:
            changed_files += 1
            print("  %-18s %d 处待同步" % (fn, len(edits)))
            for kind, old, exp in edits[:6]:
                print("      [data-%s] %r -> %r" % (kind, old, exp))
            if apply_:
                io.open(p, "w", encoding="utf-8", newline="").write(new)

    # 「空比较」不得算通过：一处都没扫到说明选择器或结构变了。
    scanned = 0
    for fn in sorted(os.listdir(WEB_DIR)):
        if fn.endswith(".html"):
            scanned += len(PAT.findall(io.open(os.path.join(WEB_DIR, fn), encoding="utf-8").read()))
    print()
    print("扫到兜底元素 %d 个；待同步 %d 处（涉及 %d 个文件）" % (scanned, total, changed_files))
    if scanned == 0:
        print("[FAIL] 一个兜底元素都没扫到 —— 选择器或页面结构可能已变，本次未校验")
        return 2
    if apply_:
        print("已改写。请再跑 `python scripts/check_version_sync.py` 确认门禁转绿。")
        return 0
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main())
