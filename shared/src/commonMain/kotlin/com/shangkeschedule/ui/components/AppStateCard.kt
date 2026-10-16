package com.shangkeschedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.shangkeschedule.ui.theme.AccentTone
import com.shangkeschedule.ui.theme.SettingsPageMaxWidth
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appIconSize
import com.shangkeschedule.ui.theme.appShapes
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.appSurface
import com.shangkeschedule.ui.theme.appType
import org.jetbrains.compose.resources.vectorResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.info_24px
import shangkeschedule.shared.generated.resources.list_alt_24px

internal enum class AppStateKind { EMPTY, ERROR }

/** 状态提示与后续操作共用一张主题表面，宽屏时保持适合阅读的宽度。 */
@Composable
internal fun AppStateCard(
    hint: String,
    kind: AppStateKind,
    modifier: Modifier,
    fillScreen: Boolean,
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    val colors = appColors()
    val spacing = appSpacing()
    val tone = colors.tone(if (kind == AppStateKind.ERROR) AccentTone.WARNING else AccentTone.PRIMARY)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fillScreen) Modifier.fillMaxSize() else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = SettingsPageMaxWidth)
                .fillMaxWidth()
                .then(rememberContentEnterMotion(kind))
                .appSurface(shape = appShapes().card)
                .padding(horizontal = spacing.cardInner, vertical = spacing.sectionGap),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
        ) {
            Box(
                modifier = Modifier.size(spacing.touchMin).clip(CircleShape).background(tone.bg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = vectorResource(
                        if (kind == AppStateKind.ERROR) Res.drawable.info_24px else Res.drawable.list_alt_24px
                    ),
                    contentDescription = null,
                    tint = tone.fg,
                    modifier = Modifier.size(appIconSize().large),
                )
            }
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction,
                    modifier = Modifier.heightIn(min = spacing.touchMin),
                    shape = appShapes().capsule,
                    contentPadding = PaddingValues(
                        horizontal = spacing.cardInner,
                        vertical = spacing.sectionTitleGap,
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.textOnPrimary,
                    ),
                ) {
                    Text(actionLabel, fontWeight = appType().titleWeight, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
