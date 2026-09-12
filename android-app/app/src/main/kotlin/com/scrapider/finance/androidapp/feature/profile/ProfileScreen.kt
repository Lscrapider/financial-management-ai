package com.scrapider.finance.androidapp.feature.profile

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scrapider.finance.androidapp.core.session.UserSession
import com.scrapider.finance.androidapp.designsystem.LocalFinanceDimensions
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing

@Composable
internal fun ProfileRoute(
    session: UserSession,
    viewModel: ProfileViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var activeDialog by remember { mutableStateOf<ProfileDialog?>(null) }
    var handledCompletionId by remember { mutableStateOf(state.completedOperationId) }

    LaunchedEffect(session.accessToken) {
        viewModel.loadForSession(session)
    }
    LaunchedEffect(state.completedOperationId) {
        if (
            state.completedOperationId != handledCompletionId &&
            state.completedDialog != null &&
            state.completedDialog == activeDialog
        ) {
            activeDialog = null
        }
        handledCompletionId = state.completedOperationId
    }
    ProfileScreen(
        state = state,
        activeDialog = activeDialog,
        onOpenDialog = { activeDialog = it },
        onDismissDialog = { activeDialog = null },
        onRetry = viewModel::refresh,
        onSaveBasic = viewModel::saveBasic,
        onSaveContacts = viewModel::saveContacts,
        onChangePassword = viewModel::changePassword,
        onEmailNotificationChange = viewModel::updateEmailNotification,
        modifier = modifier,
    )
}

@Composable
private fun ProfileScreen(
    state: ProfileUiState,
    activeDialog: ProfileDialog?,
    onOpenDialog: (ProfileDialog) -> Unit,
    onDismissDialog: () -> Unit,
    onRetry: () -> Unit,
    onSaveBasic: (realName: String, introduction: String) -> Unit,
    onSaveContacts: (email: String, phone: String) -> Unit,
    onChangePassword: (oldPassword: String, newPassword: String, confirmPassword: String) -> Unit,
    onEmailNotificationChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val profile = state.profile
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = spacing.xl,
            top = spacing.md,
            end = spacing.xl,
            bottom = spacing.section,
        ),
    ) {
        item(key = "profile-header", contentType = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "我的",
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        when {
            profile == null && state.isLoading -> {
                item(key = "profile-loading", contentType = "loading") {
                    ProfileLoadingState()
                }
            }

            profile == null && state.errorMessage.isNotBlank() -> {
                item(key = "profile-error", contentType = "error") {
                    ProfileErrorState(message = state.errorMessage, onRetry = onRetry)
                }
            }

            profile == null -> {
                item(key = "profile-empty", contentType = "empty") {
                    ProfileEmptyState()
                }
            }

            else -> {
                if (state.errorMessage.isNotBlank()) {
                    item(key = "profile-inline-error", contentType = "error") {
                        Spacer(Modifier.height(spacing.lg))
                        ProfileErrorState(message = state.errorMessage, onRetry = onRetry)
                    }
                }
                item(key = "profile-identity", contentType = "identity") {
                    Spacer(Modifier.height(spacing.section + spacing.sm))
                    ProfileIdentity(profile)
                }

                item(key = "basic-title", contentType = "section-title") {
                    Spacer(Modifier.height(spacing.section + spacing.sm))
                    ProfileSectionTitle("基本资料")
                    Spacer(Modifier.height(spacing.lg))
                }
                item(key = "basic-content", contentType = "section") {
                    ProfileSectionCard {
                        ProfileValueRow(title = "姓名", value = profile.displayName)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ProfileValueRow(title = "用户名", value = profile.username.ifBlank { "未提供" })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ProfileValueRow(title = "个人简介", value = profile.introduction.ifBlank { "未填写" })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ProfileActionRow(
                            title = "编辑基本资料",
                            summary = "修改姓名和个人简介",
                            onClick = { onOpenDialog(ProfileDialog.Basic) },
                        )
                    }
                }

                item(key = "contacts-title", contentType = "section-title") {
                    Spacer(Modifier.height(spacing.section + spacing.sm))
                    ProfileSectionTitle("安全联系方式")
                    Spacer(Modifier.height(spacing.lg))
                }
                item(key = "contacts-content", contentType = "section") {
                    ProfileSectionCard {
                        ProfileValueRow(title = "备用邮箱", value = profile.email.ifBlank { "未填写" })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ProfileValueRow(title = "密保手机", value = profile.phone.ifBlank { "未填写" })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ProfileActionRow(
                            title = "编辑联系方式",
                            summary = "维护邮箱和手机",
                            onClick = { onOpenDialog(ProfileDialog.Contacts) },
                        )
                    }
                }

                item(key = "password-title", contentType = "section-title") {
                    Spacer(Modifier.height(spacing.section + spacing.sm))
                    ProfileSectionTitle("账号安全")
                    Spacer(Modifier.height(spacing.lg))
                }
                item(key = "password-content", contentType = "section") {
                    ProfileSectionCard {
                        ProfileActionRow(
                            title = "修改密码",
                            summary = "需验证当前密码后才能保存",
                            onClick = { onOpenDialog(ProfileDialog.Password) },
                        )
                    }
                }

                item(key = "notification-title", contentType = "section-title") {
                    Spacer(Modifier.height(spacing.section + spacing.sm))
                    ProfileSectionTitle("消息提醒")
                    Spacer(Modifier.height(spacing.lg))
                }
                item(key = "notification-content", contentType = "section") {
                    ProfileSectionCard {
                        EmailNotificationRow(
                            enabled = profile.emailNotification,
                            isUpdating = state.isUpdatingNotification,
                            hasEmail = profile.email.isNotBlank(),
                            onEnabledChange = onEmailNotificationChange,
                        )
                    }
                }
            }
        }
    }

    if (profile != null) {
        when (activeDialog) {
            ProfileDialog.Basic -> BasicProfileDialog(
                profile = profile,
                isSaving = state.isSavingBasic,
                onDismiss = onDismissDialog,
                onSave = onSaveBasic,
            )

            ProfileDialog.Contacts -> ContactProfileDialog(
                profile = profile,
                isSaving = state.isSavingContacts,
                onDismiss = onDismissDialog,
                onSave = onSaveContacts,
            )

            ProfileDialog.Password -> PasswordDialog(
                isSaving = state.isChangingPassword,
                onDismiss = onDismissDialog,
                onSave = onChangePassword,
            )

            null -> Unit
        }
    }
}

@Composable
private fun ProfileIdentity(profile: ProfileInfo) {
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
                    text = profile.displayName.avatarInitial(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.displayName,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = "用户名：" + profile.username.ifBlank { "未提供" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
private fun ProfileSectionCard(content: @Composable () -> Unit) {
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
        Column { content() }
    }
}

@Composable
private fun ProfileValueRow(
    title: String,
    value: String,
) {
    val dimensions = LocalFinanceDimensions.current
    val spacing = LocalFinanceSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimensions.minTouchTarget)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(0.42f),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.58f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = ProfileLayoutTokens.valueMaxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProfileActionRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    val dimensions = LocalFinanceDimensions.current
    val spacing = LocalFinanceSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = title,
                onClick = onClick,
            )
            .heightIn(min = dimensions.minTouchTarget)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(dimensions.iconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmailNotificationRow(
    enabled: Boolean,
    isUpdating: Boolean,
    hasEmail: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val dimensions = LocalFinanceDimensions.current
    val spacing = LocalFinanceSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ProfileLayoutTokens.notificationRowMinHeight)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "邮件消息通知",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = if (hasEmail) "开启后将通过备用邮箱接收提醒" else "请先填写备用邮箱，才能接收邮件提醒",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(spacing.sm))
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange,
            enabled = !isUpdating,
            modifier = Modifier
                .semantics { contentDescription = "邮件消息通知" }
                .sizeIn(
                    minWidth = dimensions.minTouchTarget,
                    minHeight = dimensions.minTouchTarget,
                ),
        )
    }
}

@Composable
private fun ProfileLoadingState() {
    val spacing = LocalFinanceSpacing.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.section),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ProfileErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    val spacing = LocalFinanceSpacing.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(modifier = Modifier.padding(spacing.lg)) {
            Text(
                text = message,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(spacing.xs))
            TextButton(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}

@Composable
private fun ProfileEmptyState() {
    val spacing = LocalFinanceSpacing.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.section),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "暂未获取到个人资料",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BasicProfileDialog(
    profile: ProfileInfo,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (realName: String, introduction: String) -> Unit,
) {
    val spacing = LocalFinanceSpacing.current
    var realName by remember(profile.realName) { mutableStateOf(profile.realName) }
    var introduction by remember(profile.introduction) { mutableStateOf(profile.introduction) }
    var validationMessage by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("编辑基本资料") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                OutlinedTextField(
                    value = realName,
                    onValueChange = {
                        realName = it
                        validationMessage = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("姓名") },
                    singleLine = true,
                    isError = validationMessage.isNotBlank(),
                )
                OutlinedTextField(
                    value = introduction,
                    onValueChange = { introduction = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("个人简介") },
                    minLines = ProfileLayoutTokens.introductionMinLines,
                    maxLines = ProfileLayoutTokens.introductionMaxLines,
                )
                if (validationMessage.isNotBlank()) {
                    Text(
                        text = validationMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val normalizedName = realName.trim()
                    if (normalizedName.isBlank()) {
                        validationMessage = "请输入姓名"
                    } else {
                        onSave(normalizedName, introduction.trim())
                    }
                },
                enabled = !isSaving,
            ) {
                Text(if (isSaving) "保存中…" else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun ContactProfileDialog(
    profile: ProfileInfo,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (email: String, phone: String) -> Unit,
) {
    val spacing = LocalFinanceSpacing.current
    var email by remember(profile.email) { mutableStateOf(profile.email) }
    var phone by remember(profile.phone) { mutableStateOf(profile.phone) }
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("编辑联系方式") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备用邮箱") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                    ),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("密保手机") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                    ),
                )
                Text(
                    text = "留空即可清除相应联系方式。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(email.trim(), phone.trim()) },
                enabled = !isSaving,
            ) {
                Text(if (isSaving) "保存中…" else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun PasswordDialog(
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (oldPassword: String, newPassword: String, confirmPassword: String) -> Unit,
) {
    val spacing = LocalFinanceSpacing.current
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var validationMessage by remember { mutableStateOf("") }
    val passwordOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password)
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("修改密码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = {
                        oldPassword = it
                        validationMessage = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("当前密码") },
                    singleLine = true,
                    keyboardOptions = passwordOptions,
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        validationMessage = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("新密码") },
                    singleLine = true,
                    keyboardOptions = passwordOptions,
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        validationMessage = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("确认新密码") },
                    singleLine = true,
                    keyboardOptions = passwordOptions,
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (validationMessage.isNotBlank()) {
                    Text(
                        text = validationMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    validationMessage = when {
                        oldPassword.isBlank() -> "请输入当前密码"
                        newPassword.isBlank() -> "请输入新密码"
                        newPassword != confirmPassword -> "两次输入的新密码不一致"
                        else -> ""
                    }
                    if (validationMessage.isBlank()) {
                        onSave(oldPassword, newPassword, confirmPassword)
                    }
                },
                enabled = !isSaving,
            ) {
                Text(if (isSaving) "提交中…" else "确认修改")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("取消")
            }
        },
    )
}

private fun String.avatarInitial(): String = trim().firstOrNull()?.toString().orEmpty().ifBlank { "我" }

private object ProfileLayoutTokens {
    val avatarSize = 72.dp
    val identityMinHeight = 88.dp
    const val valueMaxLines = 2
    const val introductionMinLines = 3
    const val introductionMaxLines = 5
    val notificationRowMinHeight = 92.dp
}
