package com.shangkeschedule.tool

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 外部文本导入的跨平台交接点（v4.66.0 · L1 / J1）。
 *
 * 场景一（L1）：在浏览器 / 微信 / 备忘录里选中课表文本 → 系统文本选择工具栏点「上课」。
 * Android 侧系统投递的是 `Intent.ACTION_PROCESS_TEXT`（文本在
 * `Intent.EXTRA_PROCESS_TEXT` 里），`MainActivity` 取到后调用 [offer]，
 * 导航层（`AppNavigation`）观察到非空值就把「文本粘贴导入」页推到栈顶，
 * `TextImportScreen` 预填文本后调用 [clear]。
 *
 * 场景二（J1）：AI 识别导入识别出的课表文本，同样交给「文本粘贴导入」页预览与导入
 * （一条链路上只保留一个复核界面），此时来源是 [ExternalTextSource.AI]，
 * 页面据此显示对应的提示文案。
 *
 * 之所以用单例而不是导航参数：文本可能很长（整张课表几 KB），
 * 塞进导航路由的序列化参数既浪费又容易撞 Bundle 上限；这里只做一次性交接，
 * 页面消费即清空（消费后不会重复预填）。
 */
object ExternalTextImport {

    private val _pending = MutableStateFlow<String?>(null)

    /** 待导入的外部文本；null = 无。页面消费后置回 null。 */
    val pending: StateFlow<String?> = _pending.asStateFlow()

    private val _source = MutableStateFlow(ExternalTextSource.SELECTION)

    /** 本次交接的来源，用于让导入页给出对得上的提示文案。 */
    val source: StateFlow<ExternalTextSource> = _source.asStateFlow()

    /** 接收外部文本（空白/空串忽略）。 */
    fun offer(text: String?, source: ExternalTextSource = ExternalTextSource.SELECTION) {
        if (!text.isNullOrBlank()) {
            _source.value = source
            _pending.value = text
        }
    }

    /** 页面已消费，清空交接位。 */
    fun clear() {
        _pending.value = null
    }
}

/** 外部文本的来源。 */
enum class ExternalTextSource {
    /** 系统文本选择工具栏（ACTION_PROCESS_TEXT）。 */
    SELECTION,

    /** AI 识别导入（J1）。 */
    AI,
}
