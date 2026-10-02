package com.shangkeschedule.data.model

/**
 * 最近一次教务适配远程同步的结果（v4.66.0）。
 *
 * 适配状态页要回答「我上次是什么时候检查的、结果如何」，
 * 而 [com.shangkeschedule.tool.AdapterRemoteUpdater.sync] 的结果只存在于内存里，
 * 因此把结果落成三个 tiny 的 DataStore 键（时间 / 类型 / 更新文件数）。
 *
 * [kind] 取值见下面的 `KIND_*` 常量，页面据此映射成文案，
 * 这样新增类型时不需要改数据层。
 */
data class AdapterSyncRecord(
    val atMillis: Long = 0L,
    val kind: String = "",
    val updatedCount: Int = 0
) {
    companion object {
        /** 有文件被更新（含更新数量 [updatedCount]）。 */
        const val KIND_UPDATED = "updated"

        /** 已是最新，无需更新。 */
        const val KIND_UP_TO_DATE = "up_to_date"

        /** 远程同步未启用（未注入仓库地址 / 密钥）。 */
        const val KIND_DISABLED = "disabled"

        /** 下载内容校验失败（sha256 不匹配等），已回退到内置适配。 */
        const val KIND_VERIFICATION_FAILED = "verification_failed"

        /** 网络或清单解析失败，已回退到内置适配。 */
        const val KIND_FAILED = "failed"
    }
}
