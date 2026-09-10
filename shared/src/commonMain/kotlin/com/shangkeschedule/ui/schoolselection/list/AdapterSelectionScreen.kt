package com.shangkeschedule.ui.schoolselection.list

import com.shangkeschedule.ui.components.AppCard
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shangkeschedule.Destination
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import school_index.Adapter
import school_index.AdapterCategory
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back_to_school_list
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.category_bachelor_associate
import shangkeschedule.shared.generated.resources.category_general_tool
import shangkeschedule.shared.generated.resources.category_other
import shangkeschedule.shared.generated.resources.category_postgraduate
import shangkeschedule.shared.generated.resources.info_24px
import shangkeschedule.shared.generated.resources.label_contributor_format
import shangkeschedule.shared.generated.resources.label_contributor_unknown
import shangkeschedule.shared.generated.resources.text_no_adapter_for_category_school
import shangkeschedule.shared.generated.resources.text_no_detailed_description

/**
 * 需要强制以「电脑版」进入的学校 ID 集合。
 *
 * 这些学校的教务 / 门户页面是**固定宽度的桌面布局**，在手机 UA + `width=device-width` 视口下会被压扁，
 * 表现为左侧菜单点不开、看不到课表网格、验证码难以输入等。
 * 强制桌面模式会同时启用桌面 UA 与 1280px 视口修正（见 `WebCompatDelegate.injectDesktopViewportFix`），
 * 后者会移除页面自带的 `width=device-width` 并改写为 `width=1280`，桌面布局才恢复正常比例。
 *
 * 以「学校 ID」而非适配器 ID 判定：一所学校下的所有适配器共享同一套前端页面。
 */
private val FORCE_DESKTOP_MODE_SCHOOL_IDS = setOf(
    // 汕头大学：门户 xsMainV.htmlx 自带 width=device-width，但实际是固定宽度桌面布局
    // （左侧 edu-sideMenu 侧栏 + 内容 iframe），手机 UA 下被压缩，登录后进不去课表
    "u_15f498f5",
    // 武汉纺织大学外经贸学院：手机端教务菜单无法打开课表
    "u_c0a22802",
    // 沈阳农业大学：手机端课表页渲染不完整，桌面 UA 下解析稳定
    "u_26bd7359",
    // 西安医学院：金智 ehall 手机端入口登录 / 验证码体验差，桌面版登录后进教务稳定
    "u_308bdd18",
    // 国科大：xkgo 老选课系统手机端布局错乱，且拦截器仅桌面模式生效
    "MANUAL_UCAS",
    // 滁州学院：金智 EAMS 老版手机端页面布局错乱，无法看到课表网格
    "MANUAL_CHZU",
)

/**
 * 二级页面：显示特定学校和当前类别下的所有适配器列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdapterSelectionScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    schoolId: String,
    schoolName: String,
    categoryNumber: Int,
    resourceFolder: String,
    isCrushImport: Boolean = false,
    viewModel: SchoolSelectionViewModel = koinViewModel()
) {
    // 异步加载状态
    var adapters by remember { mutableStateOf<List<Adapter>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // 从传入的 number 计算当前的 AdapterCategory
    val currentCategory = remember(categoryNumber) {
        AdapterCategory.fromValue(categoryNumber) ?: AdapterCategory.BACHELOR_AND_ASSOCIATE
    }

    @Composable
    fun getCategoryDisplayName(): String {
        return when (currentCategory) {
            AdapterCategory.BACHELOR_AND_ASSOCIATE -> stringResource(Res.string.category_bachelor_associate)
            AdapterCategory.POSTGRADUATE -> stringResource(Res.string.category_postgraduate)
            AdapterCategory.GENERAL_TOOL -> stringResource(Res.string.category_general_tool)
            else -> stringResource(Res.string.category_other)
        }
    }

    // 数据加载逻辑
    LaunchedEffect(schoolId, currentCategory) {
        isLoading = true
        try {
            viewModel.updateSelectedCategory(currentCategory)
            adapters = viewModel.getAdaptersForSchoolAndCategory(schoolId)
        } catch (e: Exception) {
            adapters = emptyList()
        } finally {
            isLoading = false
        }
    }

    val categoryDisplayName = getCategoryDisplayName()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$schoolName - $categoryDisplayName", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            vectorResource(Res.drawable.arrow_back_24px),
                            contentDescription = stringResource(Res.string.a11y_back_to_school_list)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                adapters.isEmpty() -> {
                    Text(
                        text = stringResource(Res.string.text_no_adapter_for_category_school, categoryDisplayName),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(adapters, key = { it.adapter_id }) { adapter ->
                            AdapterCard(
                                adapter = adapter,
                                onClick = { selectedAdapter ->
                                    val rawUrl = (selectedAdapter.import_url ?: "").ifBlank { "about:blank" }
                                    // 学校数据中的 import_url 可能不带协议（如 ehall.szit.edu.cn/...），
                                    // 而 WebView 对无协议地址会按 http 发起，会被 network_security_config
                                    // 的"禁止明文 HTTP"策略拦截，导致"浏览器能进、App 进不去"。
                                    // 这里统一补全为 https，与地址栏手动输入的行为保持一致。
                                    val initialUrl = if (rawUrl != "about:blank" &&
                                        !rawUrl.startsWith("http://") &&
                                        !rawUrl.startsWith("https://")
                                    ) {
                                        "https://$rawUrl"
                                    } else {
                                        rawUrl
                                    }
                                    val jsFileName = selectedAdapter.asset_js_path

                                    // 构建正确的 JS 路径
                                    val assetJsPath = if (jsFileName.isNotBlank()) {
                                        "$resourceFolder/$jsFileName"
                                    } else {
                                        "$resourceFolder/${selectedAdapter.adapter_id}.js"
                                    }
                                    onNavigate(
                                        Destination.WebView(
                                            initialUrl = initialUrl,
                                            assetJsPath = assetJsPath,
                                            isCrushImport = isCrushImport,
                                            // 名单与判定理由见文件顶部 FORCE_DESKTOP_MODE_SCHOOL_IDS
                                            forceDesktopMode = schoolId in FORCE_DESKTOP_MODE_SCHOOL_IDS
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 适配器信息卡片 Composable。
 */
@Composable
fun AdapterCard(
    adapter: Adapter,
    onClick: (Adapter) -> Unit
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onClick(adapter) }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = appSpacing().cardInner, vertical = appSpacing().cardInner)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = adapter.adapter_name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(8.dp))

            // 描述
            Text(
                text = adapter.description.ifBlank { stringResource(Res.string.text_no_detailed_description) },
                style = MaterialTheme.typography.bodyMedium,
                color = appColors().textSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = vectorResource(Res.drawable.info_24px),
                    contentDescription = null,
                    tint = appColors().divider,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(
                        Res.string.label_contributor_format,
                        adapter.maintainer.ifBlank { stringResource(Res.string.label_contributor_unknown) }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = appColors().divider
                )
            }
        }
    }
}