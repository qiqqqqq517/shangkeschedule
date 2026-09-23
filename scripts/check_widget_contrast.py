# -*- coding: utf-8 -*-
"""
小组件配色对比度门禁（WCAG 2.2 AA）。

背景：小组件自带一套独立色表（`androidApp/src/main/res/values{,-night}/colors.xml`），
不参与 App 主题 token 体系，因此 `scripts/check_theme_leak.py` 覆盖不到它。
实测发现过的真实缺陷（本门禁即为防止其复发而建）：

  - `widget_text_hint` #808B96 在卡底 #FEF7FF 上仅 **3.30:1**，而它用在 10sp/11sp
    （不适用大字号豁免）⇒ 不达 AA 的 4.5:1。（已移除该层级）
  - `widget_course_fallback` #F2F4F4 在卡底上仅 **1.05:1**、深色档 #3A3A3C 仅 **1.64:1**
    ⇒ 色条兜底「等于看不见」。（已改取次要文字色）

判定口径（WCAG 2.2）：
  - **正文文本** ≥ 4.5:1（1.4.3 Contrast Minimum）
  - **有意义的非文本 UI**（承载信息的图形，如课程色条）≥ 3:1（1.4.11 Non-text Contrast）
  - **纯装饰元素**（如列表项之间的分隔线）**不作强制**：1.4.11 只约束「识别组件与状态所必需」
    的视觉信息，装饰性分隔线不属此列。此处仅打印实测值供参考，不计入通过/失败。
    若产品决定要求分隔线也达 3:1，需显著加深色值（浅色档约需 #9A9A9A 一档），属设计取舍。

用法：
    python scripts/check_widget_contrast.py            # 检查，违规时 exit 1
    python scripts/check_widget_contrast.py --quiet    # 仅输出结论
"""
import io
import os
import re
import sys

if sys.stdout.encoding and sys.stdout.encoding.lower() not in ("utf-8", "utf8"):
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "androidApp", "src", "main", "res")
DAY = os.path.join(RES, "values", "colors.xml")
NIGHT = os.path.join(RES, "values-night", "colors.xml")

CARD_BG = "widget_bg"

# (前景, 背景, 最低要求, 说明)
TEXT_CHECKS = [
    ("widget_text_primary", CARD_BG, 4.5, "主文字（课程名 / 标题）"),
    ("widget_text_secondary", CARD_BG, 4.5, "次文字（地点 / 教师 / 统计 / 空状态）"),
]

NON_TEXT_CHECKS = [
    ("widget_course_fallback", CARD_BG, 3.0, "课程色条兜底色（承载课程色信息）"),
]

# 仅报告，不计入通过/失败（装饰性元素，见模块 docstring）
DECORATIVE_CHECKS = [
    ("widget_divider", CARD_BG, "分隔线（装饰性）"),
]

# 已废弃、不得再出现的色名（防回归）
FORBIDDEN_COLORS = {
    "widget_text_hint": "第三级提示文字色：在卡底上无法同时做到「更浅」与「达 AA」，已并入 widget_text_secondary",
}

RE_COLOR = re.compile(r'<color\s+name="([^"]+)"\s*>\s*#([0-9A-Fa-f]{6,8})\s*</color>')


def parse_colors(path):
    if not os.path.isfile(path):
        sys.exit("!! 找不到配色文件：%s" % path)
    with io.open(path, encoding="utf-8") as f:
        text = f.read()
    out = {}
    for name, hexval in RE_COLOR.findall(text):
        # 支持 #AARRGGBB：取后 6 位
        out[name] = "#" + hexval[-6:].upper()
    return out


def _lin(c):
    c = c / 255.0
    return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4


def luminance(hexcolor):
    h = hexcolor.lstrip("#")[-6:]
    r, g, b = (int(h[i:i + 2], 16) for i in (0, 2, 4))
    return 0.2126 * _lin(r) + 0.7152 * _lin(g) + 0.0722 * _lin(b)


def contrast(fg, bg):
    a, b = luminance(fg), luminance(bg)
    hi, lo = max(a, b), min(a, b)
    return (hi + 0.05) / (lo + 0.05)


def check_mode(label, colors, quiet):
    failures = []
    lines = []
    lines.append("")
    lines.append("── %s ──" % label)

    bg = colors.get(CARD_BG)
    if bg is None:
        sys.exit("!! %s 缺少 %s" % (label, CARD_BG))

    lines.append("  卡底 %s = %s" % (CARD_BG, bg))

    for name, bgname, need, desc in TEXT_CHECKS + NON_TEXT_CHECKS:
        fg = colors.get(name)
        if fg is None:
            failures.append("%s: 缺少色值 %s" % (label, name))
            lines.append("  [缺失] %s" % name)
            continue
        ratio = contrast(fg, colors[bgname])
        ok = ratio >= need
        if not ok:
            failures.append(
                "%s: %s (%s) 在 %s 上仅 %.2f:1，需 ≥%.1f:1 —— %s"
                % (label, name, fg, bgname, ratio, need, desc)
            )
        lines.append(
            "  [%s] %-24s %s  需 ≥%.1f:1  实测 %.2f:1   %s"
            % ("PASS" if ok else "FAIL", name, fg, need, ratio, desc)
        )

    for name, bgname, desc in DECORATIVE_CHECKS:
        fg = colors.get(name)
        if fg is None:
            continue
        ratio = contrast(fg, colors[bgname])
        lines.append(
            "  [ -- ] %-24s %s  （不强制）     实测 %.2f:1   %s"
            % (name, fg, ratio, desc)
        )

    if not quiet:
        print("\n".join(lines))
    return failures


def main():
    quiet = "--quiet" in sys.argv
    day = parse_colors(DAY)
    night = parse_colors(NIGHT)

    failures = []
    failures += check_mode("浅色 values/", day, quiet)
    failures += check_mode("深色 values-night/", night, quiet)

    # 废弃色名不得复活
    for forbidden, why in FORBIDDEN_COLORS.items():
        for label, colors in (("values/", day), ("values-night/", night)):
            if forbidden in colors:
                failures.append("%s 出现已废弃色名 %s —— %s" % (label, forbidden, why))

    # 深浅色资源必须成对（避免「只在浅色定义」）
    only_day = sorted(set(day) - set(night))
    only_night = sorted(set(night) - set(day))
    # loading_text_color / 通用色等允许单侧存在的白名单
    ignore = {"purple_200", "purple_500", "purple_700", "teal_200", "teal_700", "black", "white"}
    only_day = [c for c in only_day if c not in ignore]
    if only_day:
        failures.append("仅浅色定义的 widget 色：%s" % ", ".join(only_day))
    if only_night:
        failures.append("仅深色定义的 widget 色：%s" % ", ".join(only_night))

    print("")
    if failures:
        print("[widget-contrast] 不通过（%d 项）：" % len(failures))
        for f in failures:
            print("  - %s" % f)
        return 1
    print("[widget-contrast] 通过：浅色 / 深色 全部文本 ≥4.5:1、非文本 ≥3:1，且无废弃色名。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
