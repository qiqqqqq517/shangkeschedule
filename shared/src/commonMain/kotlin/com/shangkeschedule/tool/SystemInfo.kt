package com.shangkeschedule.tool

/**
 * 当前运行环境的可读描述（v4.66.0），用于反馈页「附带排查信息」。
 *
 * 只包含「平台 + 版本」这类公开信息，**不含设备标识、序列号或任何可用于追踪用户的字段**：
 * 反馈页的文案（`feedback_diag_hint`）已向用户明示这一点。
 */
expect fun systemDescription(): String
