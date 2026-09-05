package com.scrapider.finance.androidapp.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scrapider.finance.androidapp.core.session.UserSession
import com.scrapider.finance.androidapp.designsystem.LocalFinanceDimensions
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing

@Composable
fun ProfileScreen(
    session: UserSession,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = spacing.xl,
            top = spacing.section + spacing.sm,
            end = spacing.xl,
            bottom = spacing.section,
        ),
    ) {
        item {
            Text(
                text = "我的",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        item { Spacer(Modifier.height(spacing.section + spacing.sm)) }
        item { ProfileIdentity(session = session) }

        item { Spacer(Modifier.height(spacing.section + spacing.lg)) }
        item { ProfileSectionTitle(title = "研究偏好") }
        item { Spacer(Modifier.height(spacing.lg)) }
        item {
            ProfileSettingsCard(
                settings = researchPreferenceSettings,
            )
        }

        item { Spacer(Modifier.height(spacing.section + spacing.sm)) }
        item { ProfileSectionTitle(title = "提醒设置") }
        item { Spacer(Modifier.height(spacing.lg)) }
        item { ReminderSettingsCard() }

        item { Spacer(Modifier.height(spacing.section + spacing.sm)) }
        item { ProfileSectionTitle(title = "隐私与外观") }
        item { Spacer(Modifier.height(spacing.lg)) }
        item {
            ProfileSettingsCard(
                settings = privacyAndAppearanceSettings,
            )
        }
    }
}

@Composable
private fun ProfileIdentity(session: UserSession) {
    val spacing = LocalFinanceSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ProfileLayoutTokens.identityMinHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Surface(
            modifier = Modifier.size(ProfileLayoutTokens.avatarSize),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = session.displayName.avatarInitial(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.displayName,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = "个人研究空间",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProfileSectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun ProfileSettingsCard(
    settings: List<ProfileSetting>,
) {
    val dimensions = LocalFinanceDimensions.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = dimensions.outlineWidth,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column {
            settings.forEachIndexed { index, setting ->
                ProfileSettingsRow(setting = setting)
                if (index < settings.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun ProfileSettingsRow(setting: ProfileSetting) {
    val dimensions = LocalFinanceDimensions.current
    val spacing = LocalFinanceSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ProfileLayoutTokens.settingsRowMinHeight)
            .padding(horizontal = spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = setting.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = setting.value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.size(spacing.sm))
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(dimensions.iconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReminderSettingsCard() {
    val dimensions = LocalFinanceDimensions.current
    val spacing = LocalFinanceSpacing.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = dimensions.outlineWidth,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ProfileLayoutTokens.reminderRowMinHeight)
                    .padding(horizontal = spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "研究提醒",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = "阈值触发、报告完成",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = true,
                    onCheckedChange = null,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ProfileSettingsRow(
                setting = ProfileSetting(
                    title = "免打扰时段",
                    value = "22:00–08:00",
                ),
            )
        }
    }
}

private data class ProfileSetting(
    val title: String,
    val value: String,
)

private fun String.avatarInitial(): String =
    trim().firstOrNull()?.toString().orEmpty().ifBlank { "我" }

private object ProfileLayoutTokens {
    val avatarSize = 72.dp
    val identityMinHeight = 88.dp
    val settingsRowMinHeight = 64.dp
    val reminderRowMinHeight = 92.dp
}

private val researchPreferenceSettings = listOf(
    ProfileSetting(title = "关注范围", value = "A 股、指数、可转债"),
    ProfileSetting(title = "研究周期", value = "中长期"),
    ProfileSetting(title = "风险偏好", value = "稳健"),
)

private val privacyAndAppearanceSettings = listOf(
    ProfileSetting(title = "外观模式", value = "跟随系统"),
    ProfileSetting(title = "字体大小", value = "标准"),
    ProfileSetting(title = "隐私与数据", value = "权限与本机数据"),
)
