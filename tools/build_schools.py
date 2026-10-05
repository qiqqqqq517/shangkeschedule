#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
构建新的 school_index.pb：
1. 通用教务平台（通用工具分类，支持用户输入网址）
2. timetable 1542所本科/专科学校
"""
import json
import sys
import os
import re
import hashlib
from collections import Counter

# 路径一律由 __file__ 派生：此前写死主工作区绝对路径，导致
#   1) 换机器/换目录即失效；
#   2) 在 git worktree 里跑时，输入读主工作区、产物写 worktree（或反之），
#      pb 与 zip 落到不同地方，发版链错配。
# 注：下方 import school_index_pb2 命中的是 tools/ 下的生成代码
# （Python 自动把脚本所在目录放进 sys.path[0]），与 ROOT 无关。
TOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(TOOLS_DIR)
OFFLINE_REPO = os.path.join(ROOT, 'shared', 'assets', 'offline_repo')
import school_index_pb2
from pypinyin import lazy_pinyin, Style

def get_pinyin_initials(name):
    """获取学校名称的完整拼音首字母缩写（小写，如南通大学→ntdx）"""
    try:
        if not name:
            return ''
        pinyin_list = lazy_pinyin(name, style=Style.FIRST_LETTER)
        return ''.join(pinyin_list).lower()
    except:
        return ''

def build_resource_map(resources_dir):
    """遍历资源目录，建立文件夹名→(文件夹名, 第一个JS文件名)的映射（小写键）

    注意：`os.listdir` 的返回顺序在 Windows 上不保证稳定，故对 JS 文件名排序后再取第一个，
    保证同一份资源每次构建得到相同映射（多 JS 目录如 CUIT/CUP/GDOU/HNSF/WBU 会打印提示）。
    """
    resource_map = {}
    if not os.path.exists(resources_dir):
        return resource_map
    for folder_name in os.listdir(resources_dir):
        folder_path = os.path.join(resources_dir, folder_name)
        if not os.path.isdir(folder_path):
            continue
        js_files = sorted(f for f in os.listdir(folder_path) if f.endswith('.js'))
        if js_files:
            if len(js_files) > 1:
                print(f'[提示] 资源目录 {folder_name} 含多个 JS，按字典序取 {js_files[0]}'
                      f'（其余：{", ".join(js_files[1:])}）；如需指定请加入 SPECIAL_ADAPTERS')
            resource_map[folder_name.lower()] = (folder_name, js_files[0])
    return resource_map


def _content_version_id(timetable_json_path):
    """由源数据内容派生 version_id。

    此前是硬编码字符串（'general-platforms-20260831'），不随数据变化，
    无法用于判断 timetable_schools.json 与 school_index.pb 是否同步。
    """
    try:
        with open(timetable_json_path, 'rb') as f:
            # LF 归一化：工作区行尾受 git autocrlf 影响可能为 CRLF，
            # 直接哈希会导致同一份 json 在不同检出设置下产生不同 version_id。
            digest = hashlib.sha256(f.read().replace(b'\r\n', b'\n')).hexdigest()[:16]
        return f'auto-{digest}'
    except Exception:
        return 'auto-unknown'


# 「域名包含文件夹名」兜底匹配的显式白名单（(文件夹名小写, 文件夹) 组合）。
#
# 背景：原实现 `if len(key) >= 3 and key in domain` 无词边界校验，实测把 **44 所学校**
# 分配到了无关学校的专属适配器，例如：
#   大连理工大学 teach.dlut.edu.cn → DLU/dlu.js（大连大学）
#   中国政法大学 jwc.cupl.edu.cn   → CUP/cup_01.js（中国石油大学）
#   浙江理工大学 jw.zstu.edu.cn    → STU/stu.js（汕头大学）
# 这些适配器内含硬编码域名，学生导入必然失败或拿到错误数据。
# 现改为：只有本白名单中的组合才允许兜底，其余一律不兜底（学校回落到通用教务平台适配器）。
# 新增条目必须逐个确认「该域名确实属于该适配器所属学校」。
SUFFIX_FALLBACK_WHITELIST = {
    # ujnpl = University of Jinan Penglai（济南大学蓬莱/泉城学院体系）；
    # 烟台科技学院与济南大学泉城学院同属该体系，命中 ujn → UJN 是正确的。
    ('ujn', 'UJN'),
}


def match_resource_folder(url, resource_map):
    """根据URL域名匹配资源文件夹，返回(文件夹名, JS文件名)或None"""
    if not url or not resource_map:
        return None
    try:
        # 提取域名：http://tdjw.ntu.edu.cn/ → tdjw.ntu.edu.cn
        domain = re.sub(r'^https?://', '', url).split('/')[0].split('?')[0].lower()
        # 取域名各部分，从长到短尝试匹配
        parts = domain.split('.')
        # 尝试完整子域名、主域名等
        candidates = []
        for i in range(len(parts)):
            candidates.append('.'.join(parts[i:]))
            candidates.append(parts[i])
        for cand in candidates:
            if cand in resource_map:
                return resource_map[cand]
        # 兜底：仅允许白名单组合（避免裸子串把学校配到别的学校的适配器）
        for key, (folder, js) in resource_map.items():
            if len(key) >= 3 and key in domain and (key, folder) in SUFFIX_FALLBACK_WHITELIST:
                return (folder, js)
    except:
        pass
    return None

# ========== 通用教务平台 ==========
GENERAL_PLATFORMS = [
    {'id': 'GENERAL_ZHENGFANG', 'name': '正方教务系统（通用）', 'initial': 'zfjwxt', 'resource_folder': 'zhengfang',
     'adapter_id': 'GENERAL_ZHENGFANG', 'adapter_name': '正方教务系统', 'asset_js_path': 'zhengfang.js',
     'description': '适用于所有正方教务系统高校，输入学校教务网址即可导入'},
    {'id': 'GENERAL_URP', 'name': 'URP教务系统（通用）', 'initial': 'urpjwxt', 'resource_folder': 'urp',
     'adapter_id': 'GENERAL_URP', 'adapter_name': 'URP教务系统', 'asset_js_path': 'urp.js',
     'description': '适用于所有URP教务系统高校，输入学校教务网址即可导入'},
    {'id': 'GENERAL_KINGOSOFT', 'name': '青果教务系统（通用）', 'initial': 'qgjwxt', 'resource_folder': 'kingosoft',
     'adapter_id': 'GENERAL_KINGOSOFT', 'adapter_name': '青果教务系统', 'asset_js_path': 'kingosoft.js',
     'description': '适用于所有青果教务系统高校，输入学校教务网址即可导入'},
    {'id': 'GENERAL_QIANGZHI', 'name': '强智教务系统（通用）', 'initial': 'qzjwxt', 'resource_folder': 'qiangzhi',
     'adapter_id': 'GENERAL_QIANGZHI', 'adapter_name': '强智教务系统', 'asset_js_path': 'qiangzhi.js',
     'description': '适用于所有强智教务系统高校，输入学校教务网址即可导入'},
    {'id': 'GENERAL_WISEDU', 'name': '金智(Wisedu)教务系统（通用）', 'initial': 'jzjwxt', 'resource_folder': 'wisedu',
     'adapter_id': 'GENERAL_WISEDU', 'adapter_name': '金智(Wisedu)教务系统', 'asset_js_path': 'wisedu.js',
     'description': '适用于所有金智教务系统高校，输入学校教务网址即可导入'},
    {'id': 'GENERAL_SOUTHSOFT', 'name': '南软教务系统（通用）', 'initial': 'nrjwxt', 'resource_folder': 'south_soft',
     'adapter_id': 'GENERAL_SOUTHSOFT', 'adapter_name': '南软教务系统', 'asset_js_path': 'south_soft.js',
     'description': '适用于所有南软教务系统高校，输入学校教务网址即可导入'},
    {'id': 'GENERAL_CHAOXING', 'name': '超星教务系统（通用）', 'initial': 'cxjwxt', 'resource_folder': 'chaoxing_jiaowu',
     'adapter_id': 'GENERAL_CHAOXING', 'adapter_name': '超星教务系统', 'asset_js_path': 'chaoxing.js',
     'description': '适用于所有超星教务系统高校，输入学校教务网址即可导入'},
    # 乘方（广州乘方科技）：v4.71.0 新增。此前有 4 所学校各写了一份专用适配
    # （HBMU / JNMC 走 Struts2 老版，GZUTCM / YMUN 走 /new/ 新版）却没有通用入口，
    # 其余用乘方的学校只能靠学校专用脚本，无法按「输入网址即可用」的通用方式导入。
    {'id': 'GENERAL_CHENGFANG', 'name': '乘方教务系统（通用）', 'initial': 'cfjwxt', 'resource_folder': 'chengfang',
     'adapter_id': 'GENERAL_CHENGFANG', 'adapter_name': '乘方教务系统', 'asset_js_path': 'chengfang.js',
     'description': '适用于广州乘方科技教务系统（老版 Struts2 与新版 /new/ 两条产品线），输入学校教务网址即可导入'},
]

TYPE_TO_JS = {
    'zhengfang_new': ('zhengfang/zhengfang.js', '正方教务系统'),
    'zhengfang': ('zhengfang/zhengfang.js', '正方教务系统'),
    'qiangzhi': ('qiangzhi/qiangzhi.js', '强智教务系统'),
    'qiangzhi_old': ('qiangzhi/qiangzhi.js', '强智教务系统(旧版)'),
    'kingosoft_new': ('kingosoft/kingosoft.js', '青果教务系统'),
    'kingosoft': ('kingosoft/kingosoft.js', '青果教务系统'),
    'wisedu': ('wisedu/wisedu.js', '金智(Wisedu)教务系统'),
    'chaoxing': ('chaoxing_jiaowu/chaoxing.js', '超星教务系统'),
    'urp_new': ('urp/urp.js', 'URP教务系统'),
    'urp': ('urp/urp.js', 'URP教务系统'),
    'south_soft': ('south_soft/south_soft.js', '南软教务系统'),
    'kangpu': ('HBTCM/hbtcm.js', '康普教务系统'),
    'hufe': ('hufe/hufe.js', '强智教务系统(湖南财政)'),
    'xiyi': ('XIYI/xiyi.js', '青果教务系统(西安医学院)'),
    'xwxy': ('XWXY/xwxy.js', 'URP教务系统(希望学院)'),
    'syau': ('syau/syau.js', 'URP教务系统(沈阳农业)'),
    'gzutcm': ('GZUTCM/gzutcm.js', '乘方教务系统(广州中医药大学)'),
    # v4.71.0：乘方通用入口。timetable 数据集里的 `chengfang` type 现在走通用脚本，
    # 不再逐校指向专用脚本 —— 通用脚本已覆盖 Struts2 老版与 /new/ 新版两条产品线。
    'chengfang': ('chengfang/chengfang.js', '乘方教务系统'),
    'stu': ('STU/stu.js', '正方教务系统(汕头大学)'),
    'ncu': ('NCU_JW/ncu.js', '强智教务系统(南昌大学)'),
    'hbvtc': ('HBVTC/hbvtc.js', '强智教务系统(湖北职业技术学院)'),
    'gzpyp': ('GZPYP/gzpyp.js', '强智教务系统(广州番禺职业技术学院)'),
    'ymun': ('YMUN/ymun.js', '乘方教务系统(右江民族医学院)'),
    'dlut': ('DLUT/dlut.js', '强智教务系统'),
}

# 个别学校需要比「自动导入自 timetable，类型: xxx」更准确的描述时在此覆盖
# （与主仓库已推送的 school_index.pb 保持逐字节一致，改动需同步核对产物哈希）
DESC_OVERRIDES = {
    'dlut': '新版本科教务（jxgl.dlut.edu.cn）课表导入，需统一身份认证登录',
    'u_c62a83b7': '青果教学综合管理服务平台个人课表导入，需统一身份认证（auth.hist.edu.cn）登录',
    'u_a1f45f92': '正方教务系统（jxgl.qut.edu.cn）课表导入，需在教务系统内登录',
}

SPECIAL_ADAPTERS = {
    # 济宁医学院：广州乘方科技教务（Struts2 老版），IP 直连地址，域名匹配无法命中资源目录，手动注册。
    'MANUAL_JNMC': [
        {
            'adapter_id': 'jnmc_01',
            'adapter_name': '乘方教务系统',
            'asset_js_path': 'jnmc.js',
            'resource_folder': 'JNMC',
            'import_url': 'http://210.44.16.13/',
        },
    ],
    # 盐城师范学院：校内与校外环境入口不同，拆成两个选择，避免门户不可达时卡死。
    'u_d6ea120c': [
        {
            'adapter_id': 'u_d6ea120c_intranet',
            'adapter_name': '正方教务系统（内网）',
            'asset_js_path': 'zhengfang.js',
            'resource_folder': 'zhengfang',
            'import_url': 'http://211.65.1.60/jwglxt/xtgl/login_slogin.html',
        },
        {
            'adapter_id': 'u_d6ea120c_webvpn',
            'adapter_name': 'X-Web校外直连',
            'asset_js_path': 'zhengfang.js',
            'resource_folder': 'zhengfang',
            'import_url': 'https://webvpn.yctu.edu.cn:4433/',
        },
    ],
    'u_238cee1c': [
        {
            'adapter_id': 'u_238cee1c_webvpn',
            'adapter_name': '智慧苏医',
            'asset_js_path': 'zhengfang.js',
            'resource_folder': 'zhengfang',
            'import_url': 'https://enlink.jsmc.edu.cn/https/webvpn687f08205c8c1e624baa2cb19eb3199fd410e20ba7f9c39f4a5085643e6a8977/_web/customized/sopplus/index.html',
        },
    ],
    # 湖北工程学院：正方 V9 教务无法直接登录，须经网上办事大厅统一身份认证进入
    'u_400c510a': [
        {
            'adapter_id': 'u_400c510a_ehall',
            'adapter_name': '网上办事大厅（统一认证）',
            'asset_js_path': 'hbeu.js',
            'resource_folder': 'hubei_engineering',
            'import_url': 'https://ehall.hbeu.edu.cn/',
        },
        {
            'adapter_id': 'u_400c510a_zf',
            'adapter_name': '正方教务系统（直连）',
            'asset_js_path': 'hbeu.js',
            'resource_folder': 'hubei_engineering',
            'import_url': 'http://jwgl.hbeu.edu.cn/jwglxt/xtgl/login_slogin.html',
        },
    ],
    # 湖北师范大学：正方 V9 教务（jwxt.hbnu.edu.cn）外网 502，须经 ehall 办事大厅统一认证进入
    # 实测入口：ehall.hbnu.edu.cn → 搜索「教务系统」→ 个人课表查询(/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N253508)
    'u_1eabbae6': [
        {
            'adapter_id': 'u_1eabbae6_ehall',
            'adapter_name': '网上办事大厅（统一认证）',
            'asset_js_path': 'hbnu.js',
            'resource_folder': 'HBNU',
            'import_url': 'https://ehall.hbnu.edu.cn/',
        },
    ],
    # 沈阳农业大学：URP 平台，公网直连正方端点被拒，须经统一认证进入。
    # 校内/校外环境入口不同，拆成两个选择：校外用户走 WebVPN，校内用户走 tpass 统一认证。
    'u_26bd7359': [
        {
            'adapter_id': 'u_26bd7359_intranet',
            'adapter_name': '校内统一认证（tpass）',
            'asset_js_path': 'syau.js',
            'resource_folder': 'syau',
            'import_url': 'https://pass.syau.edu.cn/tpass/login',
        },
        {
            'adapter_id': 'u_26bd7359_webvpn',
            'adapter_name': 'WebVPN校外通道',
            'asset_js_path': 'syau.js',
            'resource_folder': 'syau',
            'import_url': 'https://webvpn.syau.edu.cn/login',
        },
    ],
    # 广州中医药大学：广州乘方科技新版教务（REST API + AES加密密码 + 验证码），
    # 域名 jw.gzucm.edu.cn 含 "gzu" 子串会被误匹配到贵州大学(GZU)资源目录，手动注册强制使用 GZUTCM。
    'u_gzutcm_001': [
        {
            'adapter_id': 'u_gzutcm_001_01',
            'adapter_name': '乘方教务系统',
            'asset_js_path': 'gzutcm.js',
            'resource_folder': 'GZUTCM',
            'import_url': 'https://jw.gzucm.edu.cn/',
        },
    ],
    # 汕头大学：正方教务系统 + CAS 统一身份认证，专用适配器（.kbcontent 结构化解析）
    'u_15f498f5': [
        {
            'adapter_id': 'u_15f498f5_01',
            'adapter_name': '正方教务系统',
            'asset_js_path': 'stu.js',
            'resource_folder': 'STU',
            'import_url': 'https://jw.stu.edu.cn/jsxsd/framework/xsMainV.htmlx',
        },
    ],
    # 广西生态工程职业技术学院：综合教务管理系统（非标准正方，REST API + CAS 单点登录）
    'u_18842e61': [
        {
            'adapter_id': 'u_18842e61_01',
            'adapter_name': '综合教务管理系统',
            'asset_js_path': 'gxstzy.js',
            'resource_folder': 'GXSTZY',
            'import_url': 'https://cas.gxstzy.cn/lyuapServer/login',
        },
    ],
    # 南开大学研究生：allogene 研究生信息管理系统，校内直连 / 校外 WebVPN 两个入口
    # （校外经 WebVPN 代理后地址形如 /https/<加密段>/…，脚本按当前前缀拼接课表路径）
    'nankai_yjs': [
        {
            'adapter_id': 'nankai_yjs_pg_01',
            'adapter_name': '研究生信息管理系统（校内）',
            'asset_js_path': 'nankai_yjs.js',
            'resource_folder': 'NANKAI_YJS',
            'import_url': 'https://yjs.nankai.edu.cn/py/page/student/grkcb.htm',
            'description': '校内直连；登录后进入「培养 → 我的课表」再执行导入',
        },
        {
            'adapter_id': 'nankai_yjs_pg_02',
            'adapter_name': '研究生信息管理系统（校外 WebVPN）',
            'asset_js_path': 'nankai_yjs.js',
            'resource_folder': 'NANKAI_YJS',
            'import_url': 'https://webvpn.nankai.edu.cn/',
            'description': '校外需先登录 WebVPN，打开「研究生信息管理系统」并进入「培养 → 我的课表」',
        },
    ],
}

def main():
    timetable_json = os.path.join(OFFLINE_REPO, 'schools', 'timetable_schools.json')
    output_pb = os.path.join(OFFLINE_REPO, 'index', 'school_index.pb')
    resources_dir = os.path.join(OFFLINE_REPO, 'schools', 'resources')

    # 构建资源文件夹映射
    resource_map = build_resource_map(resources_dir)
    print(f'资源文件夹数量: {len(resource_map)}')

    # 构建新的 SchoolIndex
    new_index = school_index_pb2.SchoolIndex()
    new_index.protocol_version = 1
    new_index.version_id = _content_version_id(timetable_json)

    # 1. 添加通用教务平台（category=1 通用工具）
    for p in GENERAL_PLATFORMS:
        school = new_index.schools.add()
        school.id = p['id']
        school.name = p['name']
        school.initial = p['initial']
        school.resource_folder = p['resource_folder']
        adapter = school.adapters.add()
        adapter.adapter_id = p['adapter_id']
        adapter.adapter_name = p['adapter_name']
        adapter.category = 1  # GENERAL_TOOL
        adapter.asset_js_path = p['asset_js_path']
        adapter.description = p['description']
        adapter.maintainer = 'general-platform'
    print(f'添加通用平台: {len(GENERAL_PLATFORMS)}')

    # 1.5 添加手动维护学校（不在 timetable 数据集内）
    MANUAL_SCHOOLS = [
        {
            'id': 'MANUAL_JNMC',
            'name': '济宁医学院',
            'initial': 'jnyxy',
            'category': 2,
            'adapters': SPECIAL_ADAPTERS['MANUAL_JNMC'],
            'maintainer': 'manual-jnmc',
            'type_desc': '乘方教务(Struts2)',
        },
        {
            'id': 'MANUAL_AHU',
            'name': '安徽大学',
            'initial': 'ahdx',
            'category': 2,
            'adapters': [
                {
                    'adapter_id': 'ahu_01',
                    'adapter_name': '教务系统（WebVPN统一认证）',
                    'asset_js_path': 'ahu.js',
                    'resource_folder': 'AHU',
                    'import_url': 'https://wvpn.ahu.edu.cn/https/77726476706e69737468656265737421fff944d226387d1e7b0c9ce29b5b/cas/login?service=https%3A%2F%2Fone.ahu.edu.cn%2Ftp_up%2Fview%3Fm%3Dup',
                },
            ],
            'maintainer': 'manual-ahu',
            'type_desc': '金智EAMS新版(WebVPN CAS)',
        },
        {
            'id': 'MANUAL_CHZU',
            'name': '滁州学院',
            'initial': 'czxy',
            'category': 2,
            'adapters': [
                {
                    'adapter_id': 'chzu_01',
                    'adapter_name': '教务系统（统一身份认证）',
                    'asset_js_path': 'chzu.js',
                    'resource_folder': 'CHZU',
                    'import_url': 'https://sso.chzu.edu.cn/login?service=https%3A%2F%2Fjwgl.chzu.edu.cn%2Feams%2FhomeExt.action',
                },
            ],
            'maintainer': 'manual-chzu',
            'type_desc': '金智EAMS老版(联创SSO)',
        },
        {
            'id': 'MANUAL_UCAS',
            'name': '中国科学院大学',
            'initial': 'zgkxydx',
            'category': 2,
            'adapters': [
                {
                    'adapter_id': 'ucas_01',
                    'adapter_name': 'SEP 教育业务平台',
                    'asset_js_path': 'ucas.js',
                    'resource_folder': 'UCAS',
                    'import_url': 'https://sep.ucas.ac.cn/',
                },
            ],
            'maintainer': 'manual-ucas',
            'type_desc': 'SEP平台课表(DOM解析)',
        },
        {
            # 湖北医药学院不在 timetable 数据集中，手动注册（与济宁医学院同为乘方 Struts2 系统）。
            # 课表接口：GET /xsgrkbcx!getKbRq.action?xnxqdm=<学期>&zc=（zc 留空=全学期）。
            'id': 'MANUAL_HBMU',
            'name': '湖北医药学院',
            'initial': 'hbyyxy',
            'category': 2,
            'adapters': [
                {
                    'adapter_id': 'hbmu_01',
                    'adapter_name': '乘方教务系统',
                    'asset_js_path': 'hbmu.js',
                    'resource_folder': 'HBMU',
                    'import_url': 'https://jw.hbmu.edu.cn/',
                },
            ],
            'maintainer': 'manual-hbmu',
            'type_desc': '乘方教务(Struts2)',
        },
    ]
    for m in MANUAL_SCHOOLS:
        school = new_index.schools.add()
        school.id = m['id']
        school.name = m['name']
        school.initial = m['initial']
        for cfg in m['adapters']:
            adapter = school.adapters.add()
            adapter.adapter_id = cfg['adapter_id']
            adapter.adapter_name = cfg['adapter_name']
            adapter.category = m['category']
            adapter.asset_js_path = cfg['asset_js_path']
            adapter.import_url = cfg['import_url']
            adapter.description = f"手动注册，类型: {m['type_desc']}"
            school.resource_folder = cfg['resource_folder']
            adapter.maintainer = m['maintainer']
    print(f'添加手动学校: {len(MANUAL_SCHOOLS)}')

    # 2. 读取 timetable 学校数据
    with open(timetable_json, 'r', encoding='utf-8') as f:
        timetable_data = json.load(f)
    print(f'timetable 学校数量: {len(timetable_data)}')

    # 3. 添加 timetable 学校
    # - 不带"研究生"字样的 → 本科/专科分类（category=2）
    # - 带"研究生"字样的 → 研究生分类（category=3），使用独立研究生网址
    added_bachelor = 0
    added_postgrad = 0
    matched_special = 0
    for s in timetable_data:
        school_id = s.get('id', '')
        school_name = s.get('name', '')
        school_type = s.get('type', '')
        school_url = s.get('url', '')

        if not school_name or not school_id:
            continue

        type_info = TYPE_TO_JS.get(school_type)
        if type_info is None:
            type_info = ('zhengfang/zhengfang.js', f'{school_type}教务系统')

        js_path, adapter_name = type_info
        initial = get_pinyin_initials(school_name.replace(' - 研究生', ''))

        # 手动维护的多入口学校（例如校内/校外需要分别给入口）
        if school_id in SPECIAL_ADAPTERS:
            is_pg = '研究生' in school_name
            school = new_index.schools.add()
            school.id = f'{school_id}_pg' if is_pg else school_id
            school.name = school_name.replace(' - 研究生', '') if is_pg else school_name
            school.initial = initial
            school.resource_folder = 'zhengfang'
            for idx, cfg in enumerate(SPECIAL_ADAPTERS[school_id], start=1):
                adapter = school.adapters.add()
                adapter.adapter_id = cfg['adapter_id']
                adapter.adapter_name = cfg['adapter_name']
                adapter.category = cfg.get('category', 3 if is_pg else 2)
                adapter.asset_js_path = cfg['asset_js_path']
                if 'resource_folder' in cfg:
                    school.resource_folder = cfg['resource_folder']
                adapter.import_url = cfg['import_url']
                adapter.description = cfg.get('description') or ('多入口：' + cfg['adapter_name'])
                adapter.maintainer = 'manual-multi-entry'
            if is_pg:
                added_postgrad += 1
            else:
                added_bachelor += 1
            continue

        # 尝试根据URL匹配专用资源文件夹
        matched = match_resource_folder(school_url, resource_map)
        if matched:
            res_folder, res_js = matched
            resource_folder = res_folder
            asset_js_path = res_js
            matched_special += 1
        else:
            # 未匹配到专用脚本，使用通用平台脚本
            # js_path 格式如 "zhengfang/zhengfang.js"，拆分为文件夹和文件名
            if '/' in js_path:
                resource_folder, asset_js_path = js_path.split('/', 1)
            else:
                resource_folder = school_id
                asset_js_path = js_path

        is_postgrad = '研究生' in school_name

        if is_postgrad:
            # 研究生分类（使用独立研究生系统网址）
            clean_name = school_name.replace(' - 研究生', '')
            school = new_index.schools.add()
            school.id = f'{school_id}_pg'
            school.name = clean_name
            school.initial = initial
            school.resource_folder = resource_folder
            adapter = school.adapters.add()
            adapter.adapter_id = f'{school_id}_pg_01'
            adapter.adapter_name = f'{adapter_name}(研究生)'
            adapter.category = 3  # POSTGRADUATE
            adapter.asset_js_path = asset_js_path
            adapter.import_url = school_url
            adapter.description = f'研究生教务系统，类型: {school_type}'
            adapter.maintainer = 'timetable-postgrad'
            added_postgrad += 1
        else:
            # 本科/专科分类
            school = new_index.schools.add()
            school.id = school_id
            school.name = school_name
            school.initial = initial
            school.resource_folder = resource_folder
            adapter = school.adapters.add()
            adapter.adapter_id = f'{school_id}_01'
            adapter.adapter_name = adapter_name
            adapter.category = 2  # BACHELOR_AND_ASSOCIATE
            adapter.asset_js_path = asset_js_path
            adapter.import_url = school_url
            adapter.description = DESC_OVERRIDES.get(school_id) or f'自动导入自 timetable，类型: {school_type}'
            adapter.maintainer = 'auto-import'
            added_bachelor += 1

    print(f'添加本科/专科学校: {added_bachelor}')
    print(f'添加研究生学校: {added_postgrad}')
    print(f'匹配专用资源脚本: {matched_special}')
    print(f'新学校总数: {len(new_index.schools)}')

    # 4. 序列化（不带 BOM）
    pb_bytes = new_index.SerializeToString()
    print(f'生成 Protobuf 大小: {len(pb_bytes)} 字节')

    with open(output_pb, 'wb') as f:
        f.write(pb_bytes)
    print(f'已写入: {output_pb}')

    # 4.5 校验：确保所有 adapter 指向的 JS 文件真实存在，避免"点击导入无反应"
    invalid_paths = []
    for sc in new_index.schools:
        for ad in sc.adapters:
            rel_path = os.path.join(sc.resource_folder, ad.asset_js_path)
            if not os.path.isfile(os.path.join(resources_dir, rel_path)):
                invalid_paths.append(rel_path)

    if invalid_paths:
        path_counter = Counter(invalid_paths)
        print(f'\n[警告] 有 {len(invalid_paths)} 个 adapter 指向不存在的 JS 文件：')
        for rel_path, count in path_counter.most_common(20):
            print(f'  {rel_path} （{count} 个学校）')
    else:
        print('\n校验通过：所有 adapter 均指向存在的 JS 文件')

    # 4.6 反向校验：磁盘上有、但没有任何学校引用的适配器（孤儿）
    #
    # 背景：此前只有 pb→文件 的单向存在性校验，无法发现「适配器写好了却没接线」的情况 ——
    # 实测有 77 个孤儿（含全库最大的 WBU/wbu_02.js），对这些文件的修复对用户完全无效。
    referenced = set()
    for sc in new_index.schools:
        for ad in sc.adapters:
            referenced.add(f'{sc.resource_folder}/{ad.asset_js_path}'.replace('\\', '/'))

    on_disk = set()
    for root, _dirs, files in os.walk(resources_dir):
        for fn in files:
            if fn.endswith('.js'):
                rel = os.path.relpath(os.path.join(root, fn), resources_dir).replace('\\', '/')
                on_disk.add(rel)

    orphans = sorted(on_disk - referenced)
    if orphans:
        print(f'\n[警告] 有 {len(orphans)} 个适配器文件未被任何学校引用（改了也不会对用户生效）：')
        for p in orphans[:40]:
            print(f'  {p}')
        if len(orphans) > 40:
            print(f'  ...（其余 {len(orphans) - 40} 个省略）')
        print('  处理方式：要么在 SPECIAL_ADAPTERS / timetable_schools.json 中接线，要么删除该文件。')
        if '--strict' in sys.argv:
            print('[strict] 存在孤儿适配器，按 --strict 要求退出(1)')
            sys.exit(1)
    else:
        print('\n校验通过：不存在未被引用的适配器文件')

    # 5. 验证
    print('\n前10所学校:')
    for s in list(new_index.schools)[:10]:
        cats = [a.category for a in s.adapters]
        print(f"  [{s.initial}] {s.name} (cats={cats})")

    # 6. 统计分类
    final_cats = Counter()
    for s in new_index.schools:
        for a in s.adapters:
            final_cats[a.category] += 1
    print(f'\n最终分类分布: {dict(final_cats)}')
    print(f'  1=通用工具({final_cats.get(1,0)}), 2=本科/专科({final_cats.get(2,0)}), 3=研究生({final_cats.get(3,0)})')

if __name__ == '__main__':
    main()
