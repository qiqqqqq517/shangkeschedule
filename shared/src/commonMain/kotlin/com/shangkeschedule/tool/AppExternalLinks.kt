package com.shangkeschedule.tool

/**
 * 应用内用到的外部地址常量（v4.65.0 新增）。
 *
 * 此前这些地址散落在各页面（如 [com.shangkeschedule.ui.settings.additional.MoreOptionsScreen]
 * 顶部的三个 private const），适配申请需要「更多选项」与「学校选择」两处共用同一个入口，
 * 因此收敛到一处，避免两处地址漂移。
 */
object AppExternalLinks {

    const val OFFICIAL_WEBSITE = "https://shangke.asia"

    /** 官网地址的展示用短域名（设置项副标题）。 */
    const val OFFICIAL_WEBSITE_DISPLAY = "shangke.asia"

    const val GITHUB_REPO = "https://github.com/qiqqqqq517/shangkeschedule"

    const val GITHUB_NEW_ISSUE = "$GITHUB_REPO/issues/new"

    /** 反馈邮箱（历史上以纯文本展示在「更多选项 → 联系作者」卡片中）。 */
    const val SUPPORT_EMAIL = "hhixingchen520@163.com"

    /**
     * 教务系统适配申请表单（WPS 表单，第三方页面）。
     *
     * 该表单不支持 URL 预填学校名，因此入口统一先复制学校名到剪贴板再打开表单。
     * 置为空字符串即隐藏所有入口（表单下线 / 更换渠道时只需改这一处）。
     */
    const val ADAPTER_REQUEST_FORM = "https://f.wps.cn/g/AIIAb71u/"

    /**
     * 「检查更新」的版本源（v4.66.0 新增）：官网静态 JSON。
     *
     * 夸克网盘分享页是 JS 渲染的动态页面、解析不出「最新版本号」，所以版本号放在官网
     * 静态文件里（见方案 §5 K4）。发版时必须与 `website/assets/js/site.js` 的
     * `SITE.version`/`versionCode`、`website/changelog.html`、`website/sitemap.xml`
     * 一起同步 `website/version.json`，否则「检查更新」会永远说已是最新。
     */
    const val VERSION_MANIFEST_URL = "$OFFICIAL_WEBSITE/version.json"

    /**
     * 社群入口（v4.66.0，K6）：QQ 群号（纯数字，不要填群链接）。
     *
     * 与 [ADAPTER_REQUEST_FORM] 同一约定：**置为空字符串即隐藏「加入 QQ 群」入口**。
     * 点击行为是「复制群号 + 提示打开 QQ 搜索」，不直接拉起 QQ 的加入群 scheme——
     * 未安装 QQ、或 QQ 版本不支持该 scheme 时会静默失败，复制群号是最稳的做法。
     */
    const val COMMUNITY_QQ_GROUP = ""

    /**
     * 社群入口（v4.66.0，K6）：小红书主页分享短链（由作者在小红书「分享主页 → 复制链接」得到）。
     *
     * 置为空字符串即隐藏「关注小红书」入口。这里放**分享短链**而不是手拼主页地址：
     * 短链里带着官方访问令牌（2026-10-01 解析结果为
     * `https://www.xiaohongshu.com/user/profile/6680afe1000000000303116f`），
     * 光凭 user id 直接访问主页在未登录时会被拦；短链在手机浏览器里还能唤起小红书 App。
     * 作者换号 / 换主页时只需改这一处。
     */
    const val COMMUNITY_XIAOHONGSHU_URL = "https://xhslink.cn/o/12DvJClaac3"
}
