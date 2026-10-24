@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.shangkeschedule.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.shangkeschedule.data.model.AppSettingsModel
import com.shangkeschedule.data.model.AppThemeMode
import com.shangkeschedule.data.model.AppThemePreset
import com.shangkeschedule.ui.components.AppPageHeader
import com.shangkeschedule.ui.components.AppRadioRow
import com.shangkeschedule.ui.components.AppSegmentedControl
import com.shangkeschedule.ui.components.AppSwitch
import com.shangkeschedule.ui.settings.AppSettingRow
import com.shangkeschedule.ui.theme.SettingsEntryTone
import com.shangkeschedule.ui.theme.ShangKeScheduleTheme
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

private data class UxViewport(val width: Int, val height: Int, val fontScale: Float, val rtl: Boolean = false)

private fun SemanticsNode.descendants(): List<SemanticsNode> =
    listOf(this) + children.flatMap { it.descendants() }

private fun SemanticsNode.activate() {
    check(config[SemanticsActions.OnClick].action?.invoke() == true) { "操作未被执行" }
}

/** 使用真实 Compose 语义树检查交互与文本布局，不增加发布依赖，也不把截图当成装机证据。 */
fun main() = runBlocking(Dispatchers.Default) {
    val icon = ImageVector.Builder("probe", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 4f); lineTo(20f, 4f); lineTo(20f, 20f); lineTo(4f, 20f); close()
        }
    }.build()
    val labels = listOf("本周", "全部课程", "已结束")
    val viewports = listOf(
        UxViewport(375, 1200, 1f),
        UxViewport(375, 1600, 2f),
        UxViewport(812, 375, 1f),
        UxViewport(960, 1200, 2f),
        UxViewport(375, 1600, 2f, rtl = true),
    )
    var passed = 0
    for (preset in AppThemePreset.entries) for (dark in listOf(false, true)) for (viewport in viewports) {
        var selectedIndex by mutableIntStateOf(0)
        var checked by mutableStateOf(false)
        var radioSelected by mutableStateOf(false)
        val toggleChanges = mutableListOf<Boolean>()
        var radioClicks = 0
        val scene = ImageComposeScene(
            width = viewport.width,
            height = viewport.height,
            density = Density(1f, viewport.fontScale),
            layoutDirection = if (viewport.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) {
            ShangKeScheduleTheme(
                settings = AppSettingsModel(
                    themePreset = preset,
                    themeMode = if (dark) AppThemeMode.DARK else AppThemeMode.LIGHT,
                    reduceMotionEnabled = true,
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().background(appColors().pageBg)
                        .padding(appSpacing().pageHorizontal),
                    verticalArrangement = Arrangement.spacedBy(appSpacing().cardGap),
                ) {
                    AppPageHeader(title = "我的课表")
                    AppSegmentedControl(labels, selectedIndex, { selectedIndex = it })
                    // 空列表必须保持空布局，不能残留上次选中胶囊或抛异常。
                    AppSegmentedControl(emptyList(), 0, {})
                    AppSettingRow(
                        title = "显示周末",
                        subtitle = "在周课表中显示星期六和星期日",
                        icon = icon,
                        tone = SettingsEntryTone.GREEN,
                        checked = checked,
                        onCheckedChange = { checked = it; toggleChanges += it },
                        trailing = { AppSwitch(checked, onCheckedChange = null) },
                    )
                    AppRadioRow("跟随系统", radioSelected, { radioSelected = true; radioClicks++ })
                }
            }
        }
        try {
            repeat(3) { scene.render(); Thread.sleep(40) }
            fun merged() = scene.semanticsOwners.flatMap { it.rootSemanticsNode.descendants() }
            fun tabs() = merged().filter { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab }
            val initialTabs = tabs()
            check(initialTabs.size == labels.size) { "分段选项数量错误" }
            check(initialTabs.all { it.boundsInRoot.height >= 48f }) { "分段触控区不足 48dp" }
            check(initialTabs.map { it.boundsInRoot.height }.let { it.max() - it.min() <= 1f }) {
                "分段标签换行后高度不一致"
            }
            val centers = initialTabs.map { it.boundsInRoot.center.x }
            check(centers.zipWithNext().all { (a, b) -> if (viewport.rtl) a > b else a < b }) {
                "分段选项方向与布局方向不一致"
            }
            val textNodes = scene.semanticsOwners.flatMap { it.unmergedRootSemanticsNode.descendants() }
                .filter { it.config.getOrNull(SemanticsProperties.Text)?.singleOrNull()?.text in labels }
            check(textNodes.size == labels.size) { "未取得分段标签的真实文字布局" }
            textNodes.forEach { node ->
                val results = mutableListOf<TextLayoutResult>()
                check(node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results) == true)
                check(results.isNotEmpty() && results.none { it.hasVisualOverflow }) {
                    "分段标签被截断: $preset $viewport ${node.config[SemanticsProperties.Text]} " +
                        results.joinToString { "size=${it.size}, lines=${it.lineCount}, width=${it.didOverflowWidth}, height=${it.didOverflowHeight}" }
                }
            }
            initialTabs[1].activate()
            scene.render()
            check(selectedIndex == 1 && tabs()[1].config[SemanticsProperties.Selected]) { "分段切换未同步语义" }
            check(merged().any { SemanticsProperties.Heading in it.config }) { "页头缺少标题语义" }
            fun switchNode() = merged().single {
                it.config.getOrNull(SemanticsProperties.Role) == Role.Switch && SemanticsActions.OnClick in it.config
            }
            check(switchNode().config[SemanticsProperties.Text].any { it.text == "显示周末" }) {
                "开关与文字标签没有合并"
            }
            check(switchNode().boundsInRoot.height >= 48f) { "开关行触控区不足 48dp" }
            switchNode().activate()
            scene.render()
            check(checked && toggleChanges == listOf(true)) { "开关回调漏触发或重复触发" }
            check(switchNode().config[SemanticsProperties.ToggleableState] == ToggleableState.On)
            switchNode().activate()
            scene.render()
            check(!checked && toggleChanges == listOf(true, false)) { "开关无法关闭" }
            val radio = merged().single { it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton }
            check(!radio.config[SemanticsProperties.Selected])
            radio.activate()
            scene.render()
            check(radioSelected && radioClicks == 1)
            check(merged().single { it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton }
                .config[SemanticsProperties.Selected]) { "单选项没有已选状态" }
            passed++
            println("PASS $preset dark=$dark $viewport height=${initialTabs.first().boundsInRoot.height}")
        } finally {
            scene.close()
        }
    }
    println("UI_UX_PROBE_PASS: $passed/${AppThemePreset.entries.size * 2 * viewports.size}")
}
