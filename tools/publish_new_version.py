# -*- coding: utf-8 -*-
"""
统一发布脚本：每次数据/代码更新完成后，一键完成 版本迭代 + 构建 + 装机。

版本迭代按语义化版本（SemVer）常规方式：
    --bump major   大版本 +1（破坏性/重大变更） 如 2.x.x -> 3.0.0
    --bump minor   功能改动（新功能/新增学校数据）如 2.13.x -> 2.14.0
    --bump patch   缺陷修复/小改动（默认）       如 2.14.0 -> 2.14.1
versionCode 每次发布无条件 +1。

用法:
    python publish_new_version.py                    # 默认 patch 迭代
    python publish_new_version.py --bump minor       # 功能改动迭代（推荐用于新增学校）
    python publish_new_version.py --bump major
    python publish_new_version.py --no-install       # 只升版本并构建，不装机
    python publish_new_version.py --bump-only        # 仅迭代版本号并打印，不构建
"""
import argparse
import io
import os
import re
import subprocess
import sys

# Windows GBK 控制台打印 ✔/✖ 等字符会 UnicodeEncodeError（且发生在版本已写入后，
# 曾导致误判失败而重复迭代）。强制 UTF-8 输出根治。
if sys.stdout.encoding and sys.stdout.encoding.lower() not in ('utf-8', 'utf8'):
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GRADLE = os.path.join(BASE, 'androidApp', 'build.gradle.kts')
JAVA_HOME = r'D:\Android\Android Studio\jbr'
ANDROID_HOME = r'D:\Android\Sdk'


def read_version():
    with open(GRADLE, encoding='utf-8') as f:
        content = f.read()
    vc = int(re.search(r'versionCode\s*=\s*(\d+)', content).group(1))
    vn = re.search(r'versionName\s*=\s*"([^"]+)"', content).group(1)
    return vc, vn, content


def bump(vc, vn, kind):
    """语义化版本迭代；versionCode 无条件 +1。
    major: 2.x.x -> 3.0.0；minor: 2.13.x -> 2.14.0；patch: 2.14.0 -> 2.14.1
    """
    parts = [int(x) for x in vn.split('.')]
    if kind == 'major':
        parts[0] += 1
        parts[1] = 0
        parts[2] = 0
    elif kind == 'minor':
        parts[1] += 1
        parts[2] = 0
    else:  # patch
        parts[2] += 1
    return vc + 1, '.'.join(str(p) for p in parts)


def write_version(content, nvc, nvn):
    content = re.sub(r'versionCode\s*=\s*\d+', f'versionCode = {nvc}', content, count=1)
    content = re.sub(r'versionName\s*=\s*"[^"]+"', f'versionName = "{nvn}"', content, count=1)
    with open(GRADLE, 'w', encoding='utf-8', newline='') as f:
        f.write(content)


def run(cmd, cwd=BASE):
    print(f'>>> {cmd}')
    r = subprocess.run(cmd, cwd=cwd, shell=True)
    if r.returncode != 0:
        print(f'!!! 失败: {cmd}')
        sys.exit(r.returncode)
    return r


def main():
    # argparse 取代裸 sys.argv 扫描（P1-7）：
    #   此前 `--help` 不被识别 ⇒ 落到默认路径**真的重建索引并改写官网**，
    #   一个「只想看用法」的调用会改仓库。argparse 让未知参数直接报错退出。
    ap = argparse.ArgumentParser(
        prog='publish_new_version.py',
        description='版本迭代（默认只迭代版本号 + 官网同步；构建/装机见 runbook）',
        epilog='注意：正式版一律本地构建 + 手动上传，不使用 CI（见 AGENTS.md）。',
    )
    ap.add_argument('--bump', choices=['major', 'minor', 'patch'], default='patch',
                    help='迭代类型（默认 patch）')
    ap.add_argument('--bump-only', action='store_true',
                    help='仅迭代版本号并打印，不构建、不装机')
    ap.add_argument('--no-install', action='store_true',
                    help='只 assembleDebug，不装机')
    ap.add_argument('--deploy-website', action='store_true',
                    help='官网同步时**执行部署**（默认只写文件，不部署）')
    ap.add_argument('--skip-schools-rebuild', action='store_true',
                    help='跳过学校数据重建（build_schools.py）')
    args = ap.parse_args()

    bump_only = args.bump_only
    no_install = args.no_install
    kind = args.bump

    # 1. 重建学校数据（仅 pb）
    #
    # **不再调用 tools/rebuild_zip.py**：该脚本已废弃（文件头自述「已废弃 · 请勿执行」）。
    # 旧版把 offline_repo 逐文件写进普通 zip，产物没有 `repo.skr` 条目，App 端
    # OfflineRepoArchive 的魔数校验会失败并抛异常，而调用方用 runCatching 吞掉
    # ⇒ **静默不解压、内置适配资源全失**；它还会无备份地原地覆盖
    # offline_schools.zip。真正的打包器是 Gradle 任务 `:shared:packSchoolsZip`。
    #
    # 此前这里无条件调用它（P2-148），而该脚本恒以 rc=1 退出 ⇒ run() 内 sys.exit(1)
    # 让「去掉 --bump-only 的完整构建路径」**必然在版本迭代之前中止**，
    # AGENTS.md:29 的指引因此长期不可用。
    if not bump_only and not args.skip_schools_rebuild:
        run('python tools/build_schools.py')

    # 2. 自动迭代版本
    vc, vn, content = read_version()
    nvc, nvn = bump(vc, vn, kind)
    write_version(content, nvc, nvn)
    print(f'✔ 版本迭代({kind}): {vn} (code {vc}) -> {nvn} (code {nvc})')

    # 2.5 同步官网版本号（site.js / changelog.html 追补 / sitemap lastmod）
    #
    # 背景：此前本脚本只改 androidApp/build.gradle.kts 一处，而官网侧没有自动同步 ——
    # 实测 site.js 停在 3.59.1/233 而 App 已 3.64.5/247（落后 5 个版本、14 个 versionCode），
    # 且 privacy.html / index.html 另有硬编码版本、changelog.html 缺 14 个版本。
    #
    # **--no-deploy 改为可配置（P1-8 / P0-2）**：此前这里**硬编码** `--no-deploy`
    # ⇒ 每次版本迭代都只写文件、永不部署，与「同步失败不阻断」叠加后线上整站冻结
    # （实测线上停在 v4.69.0，落后 5 个版本）。现在默认仍只写文件（避免误部署），
    # 但显式 `--deploy-website` 可让本脚本一并部署；正式发版请按
    # docs/agents/release-runbook.md 执行官网同步与部署。
    site_sync = os.path.join(BASE, 'tools', 'update_website.py')
    if os.path.isfile(site_sync):
        # 同步失败必须**可见且可机器判定**（P3-147：此前报错仍 rc=0）。
        sync_failed = False
        r = subprocess.run('python tools/update_website.py --no-deploy',
                           cwd=BASE, shell=True)
        if r.returncode != 0:
            sync_failed = True
            print('!! 官网版本同步失败（update_website.py 非零退出）')
            print('   手动执行：python tools/update_website.py --no-deploy')
        if args.deploy_website:
            print('>>> 显式 --deploy-website：执行官网部署')
            d = subprocess.run('python tools/update_website.py', cwd=BASE, shell=True)
            if d.returncode != 0:
                sync_failed = True
                print('!! 官网部署失败；线上可能仍停留在旧版本')
                print('   检查：docs/agents/release-runbook.md §5 官网同步')
        else:
            print('提示：本次未部署官网（默认只写文件）。线上发布请执行：')
            print('      python tools/update_website.py')
        if sync_failed:
            # 非零退出：让「官网同步失败」对调用方与 CI 可见（此前恒 rc=0）
            sys.exit(1)
    else:
        print('!! 未找到 tools/update_website.py，已跳过官网版本同步（官网版本号可能落后）')
        print('   注意：tools/ 整目录被 .gitignore 忽略，新 worktree 里不会存在')

    if bump_only:
        return

    # 3. 构建装机
    env = dict(os.environ)
    env['JAVA_HOME'] = JAVA_HOME
    env['ANDROID_HOME'] = ANDROID_HOME
    cmd = 'gradlew.bat :androidApp:installDebug'
    if no_install:
        cmd = 'gradlew.bat :androidApp:assembleDebug'
    print(f'>>> {cmd}')
    r = subprocess.run(cmd, cwd=BASE, shell=True, env=env)
    if r.returncode != 0:
        print(f'!!! 构建失败: {cmd}')
        sys.exit(r.returncode)

    # 4. 定位产物
    import glob
    apks = glob.glob(os.path.join(BASE, 'androidApp', 'build', 'outputs', 'apk', 'debug', f'*{nvn}*arm64*.apk'))
    print()
    print('=' * 50)
    print(f'✔ 发布完成  v{nvn} (code {nvc})')
    for a in apks:
        print(f'   APK: {a}')
    print('=' * 50)


if __name__ == '__main__':
    main()
