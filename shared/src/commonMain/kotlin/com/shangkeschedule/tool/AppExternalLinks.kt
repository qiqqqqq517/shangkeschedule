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
}
