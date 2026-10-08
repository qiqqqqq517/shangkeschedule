#!/usr/bin/env python3
"""update_website.py —— 发版后一键同步官网到最新版本。

作用（本地开发工具，不入库，用法对应 website/README.md「发新版本时要改哪里」）：

1. 版本号：读取 androidApp/build.gradle.kts 的 versionName / versionCode；
   把 website/assets/js/site.js 的 SITE.version / SITE.versionCode 刷新到此版本。
2. changelog.html：扫描 CHANGELOG.md 顶部所有 ### vX.Y.Z 条目，
   凡官网 changelog.html 里缺失的版本（含历史漏更）一律追补一个 tl-item + 索引入口，
   用户文案由「功能/修复/外观」分节自动转换，跳过开发向的「构建」分节。
3. sitemap.xml：全部 lastmod 改为今天。
4. 部署：默认执行 npx wrangler pages deploy website --project-name shangkeschedule --branch main。

用法：
  python tools/update_website.py            # 同步 + 部署
  python tools/update_website.py --dry-run   # 只打印将要产生的改动，不写文件、不部署
  python tools/update_website.py --no-deploy # 同步文件但不部署
  python tools/update_website.py --version 3.56.7   # 显式指定目标版本（默认读 build.gradle）

脚本是幂等的：site.js 已是目标版本、changelog.html 已含该版本时不会重复改动。
"""

import argparse
import datetime
import io
import re
import shutil
import subprocess
import sys
from pathlib import Path

# P1-55 续：本机 stdout 默认是 GBK，打印 `✓`（U+2713）会抛
# `UnicodeEncodeError: 'gbk' codec can't encode character '\u2713'` ——
# 实测该异常发生在**部署成功之后**的收尾 print 上，于是「部署明明成功、
# 脚本却以非 0 退出」。统一把两路输出绑成 UTF-8，避免这类假失败。
if sys.stdout.encoding and sys.stdout.encoding.lower() not in ("utf-8", "utf8"):
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
if sys.stderr.encoding and sys.stderr.encoding.lower() not in ("utf-8", "utf8"):
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

ROOT = Path(__file__).resolve().parents[1]
WEBSITE = ROOT / "website"
SITE_JS = WEBSITE / "assets" / "js" / "site.js"
CHANGELOG_HTML = WEBSITE / "changelog.html"
SITEMAP = WEBSITE / "sitemap.xml"
BUILD_GRADLE = ROOT / "androidApp" / "build.gradle.kts"
CHANGELOG_MD = ROOT / "CHANGELOG.md"

# CHANGELOG 分节 → changelog.html 的 tag 类别；「构建」等开发向分节不进用户可见文案。
SECTION_TAGS = {
    "功能": "tag-feat",
    "修复": "tag-fix",
    "外观": "tag-ui",
    "重构": "tag-refactor",
    # 2026-10-08 补：CHANGELOG 里长期存在这两类用户可见章节，此前无映射 ⇒ 整节被静默丢弃
    "改动方式": "tag-refactor",
    # 「适配脚本」是适配层修复（已同步线上），对用户等同修复，归 tag-fix
    "适配脚本": "tag-fix",
}
SKIP_SECTIONS = {"构建"}


# ---------------------------------------------------------------- 读取目标版本
def read_target_version():
    text = BUILD_GRADLE.read_text(encoding="utf-8")
    m_ver = re.search(r'versionName = "(\d+\.\d+\.\d+)"', text)
    m_code = re.search(r"versionCode = (\d+)", text)
    if not m_ver or not m_code:
        sys.exit("无法从 androidApp/build.gradle.kts 解析 versionName / versionCode")
    return m_ver.group(1), m_code.group(1)


# ---------------------------------------------------------------- 解析 CHANGELOG
def parse_changelog(path):
    """返回按文件顺序（最新在顶部）的版本条目列表。"""
    header_re = re.compile(
        r"^### v(\d+)\.(\d+)\.(\d+)（(\d{4}-\d{2}-\d{2})）·\s*(.+)$"
    )
    # 章节标题两种写法都要认：旧条目用 **修复**，2026-10 之后的条目用 markdown 标题 #### 修复。
    # 原实现只认前者 ⇒ 新条目的要点全部解析不出来，官网时间线生成出一堆「只有版本号、正文空白」
    # 的空壳条目（2026-10-08 发版时实测：v4.75.1~v4.75.5 的 tl-body 全为空）。
    section_re = re.compile(r"^(?:\*\*(.+?)\*\*|#{3,6}\s+(.+?))$")
    bullet_re = re.compile(r"^-\s+(.+)$")
    entries = []
    cur = None
    for line in path.read_text(encoding="utf-8").splitlines():
        m = header_re.match(line)
        if m:
            cur = {
                "ver": f"{m.group(1)}.{m.group(2)}.{m.group(3)}",
                "date": m.group(4),
                "title": m.group(5),
                "sections": [],
            }
            entries.append(cur)
            continue
        if cur is None:
            continue
        s = section_re.match(line)
        if s:
            # 组1 = **粗体** 写法，组2 = #### 标题写法；取非空的那个
            label = s.group(1) or s.group(2)
            cur["sections"].append({"label": label, "items": []})
            continue
        b = bullet_re.match(line)
        if b and cur["sections"]:
            cur["sections"][-1]["items"].append(b.group(1))
    return entries


# ---------------------------------------------------------------- 生成 changelog 片段
def html_escape(text):
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def anchor_id(ver):
    return "v" + ver.replace(".", "")


def lead_bold(text):
    """若条目以 **标题**[:：] 开头，返回 (标题, 正文)；否则返回 (None, 原文)。"""
    m = re.match(r"^\*\*(.+?)\*\*[:：]\s*(.*)$", text)
    if m:
        return m.group(1), m.group(2)
    return None, text


def render_items(items, hoist_first_lead=False):
    """把一组 CHANGELOG 要点转成 <li>：**x**→<b>x</b>、`x`→<code>x</code>。

    2026-10-08 修正（内容丢失）：原实现写成 `_lead, body = lead_bold(it)`，
    **把每一条的主语粗体都丢掉了** —— 而 [gen_tl_item] 只把**第一条**的主语提升为 h3，
    于是第 2 条起的要点全部变成半截话。实测生成的 v4.75.6 时间线：
      源文「**云端备份可能永久删除你唯一的一份备份**：上传新备份时若某个模块移动失败…」
      页面「上传新备份时若某个模块移动失败…」（主语消失，读者不知道在说哪个功能）
    现改为：只有确实被提升为 h3 的第一条才剥掉主语，其余各条**原样保留**主语粗体。
    """
    lis = []
    for idx, it in enumerate(items):
        lead, body = lead_bold(it)
        if lead is not None and not (hoist_first_lead and idx == 0):
            body = f"<b>{lead}</b>：" + body
        body = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", body)
        body = re.sub(r"`([^`]+)`", r"<code>\1</code>", body)
        lis.append(f"            <li>{body}</li>")
    return lis


def gen_tl_item(entry):
    """把单个 CHANGELOG 条目转成 changelog.html 的一块 tl-item。"""
    parts = []
    for sec in entry["sections"]:
        label = sec["label"]
        cls = SECTION_TAGS.get(label)
        if cls is None or label in SKIP_SECTIONS:
            continue
        items = sec["items"]
        if not items:
            continue
        title = ""
        lead = lead_bold(items[0])[0]
        if lead and lead != label:
            title = lead
        heading = f'          <h3><span class="tl-tag {cls}">{label}</span>{title}</h3>'
        # 只有当第一条的主语确实被提升为 h3（title 非空）时，才允许它剥掉主语
        body = "\n".join(render_items(items, hoist_first_lead=bool(title)))
        parts.append(f"{heading}\n          <ul>\n{body}\n          </ul>")
        if title:
            # 已把首条的加粗标题提为 h3 标题，正文里再保留会冗余——这里不额外处理，
            # render_items 已剔除了该处的加粗引导。
            pass
    body_html = "\n\n".join(parts)
    return (
        f'      <div class="tl-item reveal" id="{anchor_id(entry["ver"])}">\n'
        f'        <div><span class="tl-ver">v{entry["ver"]}</span>'
        f'<span class="tl-date">{entry["date"]}</span></div>\n'
        f"        <div class=\"tl-body\">\n{body_html}\n        </div>\n"
        f"      </div>"
    )


def gen_toc_entry(entry):
    return (
        f'        <li><a href="#{anchor_id(entry["ver"])}">'
        f'v{entry["ver"]} · {html_escape(entry["title"])}</a></li>'
    )


# ---------------------------------------------------------------- 补丁各文件
def patch_site_js(version, version_code):
    orig = SITE_JS.read_text(encoding="utf-8")
    text = re.sub(r"(version:\s*')[^']*(')", rf"\g<1>{version}\g<2>", orig)
    text = re.sub(
        r"(versionCode:\s*')[^']*(')", rf"\g<1>{version_code}\g<2>", text
    )
    return text, text != orig


def _existing_max_version(html_text):
    """取官网 changelog.html 里已有的最大版本（tl-ver 文本，形如 v3.56.5）。"""
    vers = re.findall(r'<span class="tl-ver">v(\d+)\.(\d+)\.(\d+)</span>', html_text)
    if not vers:
        return (0, 0, 0)
    return max((int(a), int(b), int(c)) for a, b, c in vers)


def _ver_key(ver):
    return tuple(int(x) for x in ver.split("."))


def patch_changelog_html(new_entries):
    """追补比官网现有最新版本更新的版本，返回 (新文本, 新增了哪些版本)。"""
    orig = CHANGELOG_HTML.read_text(encoding="utf-8")
    text = orig
    max_existing = _existing_max_version(text)
    missing = []
    for ent in new_entries:
        if _ver_key(ent["ver"]) <= max_existing:
            continue  # 已收录或更旧——官网 changelog 是精选近期版本，旧版故意不入
        missing.append(ent)
    if not missing:
        return text, []
    tl_block = "\n\n".join(gen_tl_item(e) for e in missing) + "\n"
    toc_block = "\n".join(gen_toc_entry(e) for e in missing)
    tl_marker = '<div class="timeline doc-body" style="max-width:none">\n'
    toc_marker = "<ol>\n"
    if tl_marker in text and toc_marker in text:
        text = text.replace(tl_marker, tl_marker + "\n" + tl_block, 1)
        text = text.replace(toc_marker, toc_marker + toc_block + "\n", 1)
    return text, missing


def patch_sitemap():
    today = datetime.date.today().isoformat()
    orig = SITEMAP.read_text(encoding="utf-8")
    text = re.sub(r"<lastmod>\d{4}-\d{2}-\d{2}</lastmod>", f"<lastmod>{today}</lastmod>", orig)
    return text, text != orig


# ---------------------------------------------------------------- 部署
def deploy(dry_run):
    cmd = [
        "npx", "wrangler", "pages", "deploy", str(WEBSITE),
        "--project-name", "shangkeschedule", "--branch", "main",
    ]
    if dry_run:
        print("[dry-run] 部署命令:", " ".join(cmd))
        return
    print(">>> 部署到 Cloudflare Pages:", " ".join(cmd))
    # P1-55 续：Windows 上 `npx` 实际是 `npx.cmd`，而 subprocess **不走 shell**
    # 时无法解析 `.cmd`（实测报 FileNotFoundError [WinError 2]）—— 也就是说
    # 这条部署路径在本机**从来跑不通**：即便去掉 `--no-deploy` 也会失败，
    # 与「线上长期落后」互为因果。改为显式解析可执行文件，解析不到才回退 shell。
    exe = shutil.which(cmd[0])
    if exe:
        argv = [exe] + cmd[1:]
        subprocess.run(argv, cwd=str(ROOT), check=True)
    else:
        subprocess.run(" ".join(cmd), cwd=str(ROOT), check=True, shell=True)
    print(">>> 部署完成 ✓")


# ---------------------------------------------------------------- 主流程
def main():
    ap = argparse.ArgumentParser(description="发版后自动同步官网")
    ap.add_argument("--dry-run", action="store_true", help="只打印将产生的改动，不改文件、不部署")
    ap.add_argument("--no-deploy", action="store_true", help="同步文件但不部署")
    ap.add_argument("--version", help="目标版本号（默认读取 build.gradle.kts）")
    args = ap.parse_args()

    if args.version:
        version, version_code = args.version, ""
        try:
            _v, version_code = read_target_version()
        except SystemExit:
            version_code = ""
    else:
        version, version_code = read_target_version()

    today = datetime.date.today().isoformat()
    print(f"目标版本：v{version}（versionCode {version_code}） 日期 {today}")

    # 1) site.js 版本号
    new_js, js_changed = patch_site_js(version, version_code)
    print(f"site.js：版本号{'已是最新，无需改动' if not js_changed else f'→ v{version} / {version_code}'}")

    # 2) changelog.html（追补 CHANGELOG 中缺失的全部版本）
    chan = parse_changelog(CHANGELOG_MD)
    new_html, missing = patch_changelog_html(chan)
    if missing:
        for e in missing:
            print(f"changelog.html：追补 v{e['ver']} · {e['title']}")
    else:
        print("changelog.html：已包含 CHANGELOG 全部版本，无需改动")

    # 3) sitemap.xml lastmod
    new_sitemap, sm_changed = patch_sitemap()
    print("sitemap.xml：lastmod" + ("已更新" if sm_changed else "未变化"))

    # 4) website/*.html 里被 site.js 覆写的「兜底文本」（P1-9）
    #
    # data-version / data-version-code / data-asset-name 三个载体的元素文本此前
    # 不在本脚本的写入清单里，导致 index.html / features.html 长期停在
    # v3.71.2 / 290（落后约 100 个版本）—— 禁用 JS、爬虫、查看源码时看到旧版本。
    # 复用**已入库**的 scripts/sync_web_version_fallbacks.py，避免两份逻辑漂移。
    fb = subprocess.run(
        [sys.executable, str(ROOT / "scripts" / "sync_web_version_fallbacks.py"), "--apply"],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    fb_lines = (fb.stdout or "").strip().splitlines()
    print("website/*.html 兜底版本：" + (fb_lines[-2] if len(fb_lines) >= 2 else
                                       (fb_lines[0] if fb_lines else f"rc={fb.returncode}")))
    if fb.returncode != 0:
        # 不静默吞掉：改不动就在 stdout 明说，便于发版时立刻发现
        print(f"  [WARN] sync_web_version_fallbacks.py rc={fb.returncode}")
        for line in fb_lines[-6:]:
            print("        " + line)

    if args.dry_run:
        print("\n[dry-run] 本轮将写入：")
        if js_changed:
            print("  - website/assets/js/site.js")
        if missing:
            print("  - website/changelog.html  （" + ", ".join(e["ver"] for e in missing) + "）")
        if sm_changed:
            print("  - website/sitemap.xml")
        deploy(dry_run=True)
        return

    if js_changed:
        SITE_JS.write_text(new_js, encoding="utf-8")
    if missing:
        CHANGELOG_HTML.write_text(new_html, encoding="utf-8")
    if sm_changed:
        SITEMAP.write_text(new_sitemap, encoding="utf-8")

    print("\n文件同步完成。")
    if not args.no_deploy:
        deploy(dry_run=False)


if __name__ == "__main__":
    main()