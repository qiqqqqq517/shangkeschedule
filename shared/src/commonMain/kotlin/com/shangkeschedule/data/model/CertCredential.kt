package com.shangkeschedule.data.model

/**
 * 考证查分用的查询凭据（v4.66.0）。
 *
 * 只保存在本机 DataStore，不上传任何服务器：官网查分页需要姓名 / 准考证号，
 * 应用提供的价值是「替你记住并一键复制」，而不是代查 —— 代查需要把凭据发往第三方，
 * 这与本项目「不收集用户数据」的定位冲突（见应用内隐私政策）。
 *
 * @param name 姓名（部分考试查分需要）
 * @param ticket 准考证号 / 证件号
 */
data class CertCredential(
    val name: String = "",
    val ticket: String = ""
)
