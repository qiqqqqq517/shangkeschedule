package com.shangkeschedule.ui.settings.quickactions

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * 带有格式化参数的资源字符串包装类：让 ViewModel 传递「待解析的文案 + 参数」，
 * 避免在状态层硬编码字符串。
 */
data class UiTextRes(
    val resource: StringResource,
    val args: List<Any> = emptyList()
)

/** 在 Composable 中按当前语言解析 [UiTextRes]。 */
@Composable
fun UiTextRes.asString(): String = stringResource(resource, *args.toTypedArray())
