package com.shangkeschedule.ui.settings.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.shangkeschedule.tool.FileManagerCallbacks
import com.shangkeschedule.tool.rememberFileManager
import com.shangkeschedule.ui.components.AppTextField
import com.shangkeschedule.ui.components.AppTopAppBar
import com.shangkeschedule.ui.components.ImageCropper
import com.shangkeschedule.ui.settings.SettingsViewModel
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appShapes
import com.shangkeschedule.ui.theme.appSpacing
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.a11y_back
import shangkeschedule.shared.generated.resources.app_name
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.profile_avatar_label
import shangkeschedule.shared.generated.resources.profile_change_avatar
import shangkeschedule.shared.generated.resources.profile_college
import shangkeschedule.shared.generated.resources.profile_college_hint
import shangkeschedule.shared.generated.resources.profile_grade
import shangkeschedule.shared.generated.resources.profile_grade_hint
import shangkeschedule.shared.generated.resources.profile_info_title
import shangkeschedule.shared.generated.resources.profile_major
import shangkeschedule.shared.generated.resources.profile_major_hint
import shangkeschedule.shared.generated.resources.profile_nickname
import shangkeschedule.shared.generated.resources.profile_nickname_hint
import shangkeschedule.shared.generated.resources.profile_remove_avatar
import shangkeschedule.shared.generated.resources.profile_school
import shangkeschedule.shared.generated.resources.profile_school_hint
import shangkeschedule.shared.generated.resources.profile_section_basic
import shangkeschedule.shared.generated.resources.profile_section_school
import shangkeschedule.shared.generated.resources.profile_signature
import shangkeschedule.shared.generated.resources.profile_signature_hint

/**
 * 「我的信息」页（v3.49.0）。
 *
 * 入口：「我的」页顶部身份卡（原直接跳「外观与样式」，现改为跳本页）。
 * 结构参考常用软件的「我的 → 个人信息」页：顶部大头像 + 昵称/签名预览，下方分组表单
 * （基本资料：昵称 / 个性签名；学校信息：学校 / 学院 / 专业 / 年级）。
 *
 * 交互约定：
 * - 头像：点击唤起系统图片选择器 → 1:1 裁剪 → 存私有目录（`avatar_<uuid>.jpg`），旧文件自动清理；
 * - 文本：本地编辑态即时回显，落库走 500ms 防抖（避免逐字符写 DataStore）；
 * - 返回即已保存（无需显式「保存」按钮，与主流 App 的个人资料页一致）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileInfoScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings = uiState.appSettings

    var nickname by remember { mutableStateOf("") }
    var school by remember { mutableStateOf("") }
    var college by remember { mutableStateOf("") }
    var major by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf("") }
    var signature by remember { mutableStateOf("") }

    // 首次拿到 DataStore 快照时灌入本地编辑态；此后以本地状态为准（避免回写打断输入）
    var seeded by remember { mutableStateOf(false) }
    LaunchedEffect(settings.profileNickname, settings.profileSchool, settings.profileCollege) {
        if (!seeded) {
            nickname = settings.profileNickname
            school = settings.profileSchool
            college = settings.profileCollege
            major = settings.profileMajor
            grade = settings.profileGrade
            signature = settings.profileSignature
            seeded = true
        }
    }

    val scope = rememberCoroutineScope()
    var saveJob by remember { mutableStateOf<Job?>(null) }
    // 防抖落库：任何字段变化后 500ms 统一写一次
    val scheduleSave: () -> Unit = {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(500)
            viewModel.onProfileInfoChanged(nickname, school, college, major, grade, signature)
        }
    }

    // 头像：系统选择器 → 1:1 裁剪 → 保存
    var loadedBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var showCropper by remember { mutableStateOf(false) }
    val fileManager = rememberFileManager(
        callbacks = FileManagerCallbacks(
            onImagePicked = { bitmap ->
                if (bitmap != null) {
                    loadedBitmap = bitmap
                    showCropper = true
                }
            }
        )
    )

    if (showCropper && loadedBitmap != null) {
        ImageCropper(
            imageBitmap = loadedBitmap,
            aspectRatio = 1f,
            onCropConfirmed = { bytes ->
                viewModel.saveProfileAvatar(bytes)
                showCropper = false
                loadedBitmap = null
            },
            onDismiss = {
                showCropper = false
                loadedBitmap = null
            }
        )
    }

    val appName = stringResource(Res.string.app_name)
    val displayName = nickname.ifBlank { appName }
    val displaySubtitle = listOf(school, major)
        .filter { it.isNotBlank() }
        .joinToString(" · ")

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.profile_info_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            vectorResource(Res.drawable.arrow_back_24px),
                            contentDescription = stringResource(Res.string.a11y_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = appSpacing().pageHorizontal)
                .padding(top = 12.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ===== 顶部身份卡：大头像 + 昵称 + 学校·专业 =====
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .clip(appShapes().card)
                    .background(appColors().cardBg)
                    .padding(horizontal = appSpacing().cardInner, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ProfileAvatar(
                    avatarPath = settings.profileAvatarPath,
                    fallbackLetter = displayName.take(1),
                    onClick = { fileManager.pickImage() }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = appColors().textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (displaySubtitle.isNotEmpty()) {
                    Text(
                        text = displaySubtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = appColors().textSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.profile_change_avatar),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(appShapes().chipSmall)
                        .clickable { fileManager.pickImage() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                if (settings.profileAvatarPath.isNotEmpty()) {
                    Text(
                        text = stringResource(Res.string.profile_remove_avatar),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                        color = appColors().textSecondary,
                        modifier = Modifier
                            .clip(appShapes().chipSmall)
                            .clickable { viewModel.removeProfileAvatar() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // ===== 基本资料：昵称 / 个性签名 =====
            ProfileGroupLabel(stringResource(Res.string.profile_section_basic))
            ProfileGroupCard {
                AppTextField(
                    value = nickname,
                    onValueChange = {
                        nickname = it
                        scheduleSave()
                    },
                    label = stringResource(Res.string.profile_nickname),
                    placeholder = stringResource(Res.string.profile_nickname_hint),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                AppTextField(
                    value = signature,
                    onValueChange = {
                        signature = it
                        scheduleSave()
                    },
                    label = stringResource(Res.string.profile_signature),
                    placeholder = stringResource(Res.string.profile_signature_hint),
                    singleLine = false,
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ===== 学校信息：学校 / 学院 / 专业 / 年级 =====
            ProfileGroupLabel(stringResource(Res.string.profile_section_school))
            ProfileGroupCard {
                AppTextField(
                    value = school,
                    onValueChange = {
                        school = it
                        scheduleSave()
                    },
                    label = stringResource(Res.string.profile_school),
                    placeholder = stringResource(Res.string.profile_school_hint),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                AppTextField(
                    value = college,
                    onValueChange = {
                        college = it
                        scheduleSave()
                    },
                    label = stringResource(Res.string.profile_college),
                    placeholder = stringResource(Res.string.profile_college_hint),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                AppTextField(
                    value = major,
                    onValueChange = {
                        major = it
                        scheduleSave()
                    },
                    label = stringResource(Res.string.profile_major),
                    placeholder = stringResource(Res.string.profile_major_hint),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                AppTextField(
                    value = grade,
                    onValueChange = {
                        grade = it
                        scheduleSave()
                    },
                    label = stringResource(Res.string.profile_grade),
                    placeholder = stringResource(Res.string.profile_grade_hint),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * 大头像（96dp）：已设置头像时显示图片，否则回落为「主题主色 → 强调色」渐变 + 首字。
 * 点击唤起图片选择器；右上角相机角标提示可更换。
 */
@Composable
private fun ProfileAvatar(
    avatarPath: String,
    fallbackLetter: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary
                    )
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (avatarPath.isNotEmpty()) {
            AsyncImage(
                model = avatarPath,
                contentDescription = stringResource(Res.string.profile_avatar_label),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = fallbackLetter,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color.White
            )
        }
    }
}

/** 分组标签：与「下节课卡」设置页同构的轻量标题。 */
@Composable
private fun ProfileGroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        ),
        color = appColors().textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
            .padding(start = 4.dp, top = 4.dp)
    )
}

/** 分组卡：卡片底 + 卡片圆角，内部纵向排列输入框。 */
@Composable
private fun ProfileGroupCard(content: @Composable () -> Unit) {
    Surface(
        color = appColors().cardBg,
        shape = appShapes().card,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = appSpacing().cardInner,
                vertical = appSpacing().cardInner
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content()
        }
    }
}
