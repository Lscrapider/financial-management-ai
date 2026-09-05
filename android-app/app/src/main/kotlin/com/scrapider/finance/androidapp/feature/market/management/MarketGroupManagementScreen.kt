package com.scrapider.finance.androidapp.feature.market.management

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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import com.scrapider.finance.androidapp.feature.market.MarketWatchGroup
import com.scrapider.finance.androidapp.feature.market.ui.MarketConfirmationDialog
import com.scrapider.finance.androidapp.feature.market.ui.MarketPageTopBar
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MarketGroupManagementScreen(
    groups: List<MarketWatchGroup>,
    isSaving: Boolean,
    groupSavedSignal: Long,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onSaveGroup: (String?, String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateSheet by rememberSaveable { mutableStateOf(false) }
    var renameGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(groupSavedSignal) {
        if (groupSavedSignal > 0L) {
            showCreateSheet = false
            renameGroupId = null
        }
    }
    val spacing = LocalFinanceSpacing.current
    val groupToRename = groups.firstOrNull { group -> group.id == renameGroupId }
    val groupToDelete = groups.firstOrNull { group -> group.id == deleteGroupId }
    Scaffold(
        modifier = modifier,
        topBar = { MarketPageTopBar(title = "管理自选池", onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
    ) { contentPadding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(
                start = spacing.xl,
                top = spacing.xl,
                end = spacing.xl,
                bottom = spacing.section,
            ),
        ) {
            item {
                Text(
                    text = "我的分组",
                    style = MiuixTheme.textStyles.title2,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }
            item { Spacer(Modifier.height(spacing.lg)) }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    SystemGroupRow()
                    if (groups.isNotEmpty()) HorizontalDivider()
                    groups.forEachIndexed { index, group ->
                        MarketGroupManagementRow(
                            group = group,
                            onRename = { renameGroupId = group.id },
                            onDelete = { deleteGroupId = group.id },
                        )
                        if (index < groups.lastIndex) HorizontalDivider()
                    }
                }
            }
            item { Spacer(Modifier.height(spacing.xl)) }
            item {
                Button(
                    onClick = { showCreateSheet = true },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AddCircleOutline,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(spacing.xs))
                    Text("新建自选池")
                }
            }
        }
    }

    if (showCreateSheet) {
        MarketGroupEditorSheet(
            title = "新建自选池",
            initialName = "",
            isSaving = isSaving,
            confirmText = "创建",
            onDismiss = { showCreateSheet = false },
            onConfirm = { name -> onSaveGroup(null, name) },
        )
    }
    if (groupToRename != null) {
        MarketGroupEditorSheet(
            title = "重命名自选池",
            initialName = groupToRename.name,
            isSaving = isSaving,
            confirmText = "保存",
            onDismiss = { renameGroupId = null },
            onConfirm = { name -> onSaveGroup(groupToRename.id, name) },
        )
    }
    MarketConfirmationDialog(
        show = groupToDelete != null,
        title = "删除自选池",
        message = groupToDelete?.let { group ->
            "删除“${group.name}”会同时移除其中的 ${group.items.size} 个标的。"
        }.orEmpty(),
        confirmText = "删除",
        isSaving = isSaving,
        onDismiss = { deleteGroupId = null },
        onConfirm = {
            groupToDelete?.let { group ->
                deleteGroupId = null
                onDeleteGroup(group.id)
            }
        },
    )
}

@Composable
private fun SystemGroupRow() {
    BasicComponent(
        title = "全部",
        summary = "系统分组 · 显示所有已关注标的",
        endActions = {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = "系统分组不可编辑",
            )
        },
    )
}

@Composable
private fun MarketGroupManagementRow(
    group: MarketWatchGroup,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    BasicComponent(
        title = group.name,
        summary = "${group.items.size} 个标的",
        endActions = {
            MarketGroupActions(
                groupName = group.name,
                onRename = onRename,
                onDelete = onDelete,
            )
        },
    )
}

@Composable
private fun MarketGroupActions(
    groupName: String,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var showActions by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { showActions = true }) {
        Icon(
            imageVector = Icons.Outlined.MoreVert,
            contentDescription = "更多操作：$groupName",
        )
    }
    OverlayBottomSheet(
        show = showActions,
        title = groupName,
        onDismissRequest = { showActions = false },
    ) {
        BasicComponent(
            title = "重命名",
            summary = "修改自选池名称",
            onClick = {
                showActions = false
                onRename()
            },
        )
        BasicComponent(
            title = "删除自选池",
            summary = "将同时移除池内标的",
            onClick = {
                showActions = false
                onDelete()
            },
            titleColor = top.yukonga.miuix.kmp.basic.BasicComponentDefaults.titleColor(
                color = MiuixTheme.colorScheme.error,
            ),
        )
    }
}

@Composable
private fun MarketGroupEditorSheet(
    title: String,
    initialName: String,
    isSaving: Boolean,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var groupName by rememberSaveable(title, initialName) { mutableStateOf(initialName) }
    val spacing = LocalFinanceSpacing.current
    OverlayBottomSheet(
        show = true,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            TextField(
                value = groupName,
                onValueChange = { groupName = it },
                modifier = Modifier.fillMaxWidth(),
                label = "自选池名称",
                singleLine = true,
                enabled = !isSaving,
            )
            Text(
                text = "创建后可从行情添加标的",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(text = "取消", onClick = onDismiss, enabled = !isSaving)
                Button(
                    onClick = { onConfirm(groupName) },
                    enabled = !isSaving && groupName.isNotBlank(),
                ) {
                    Text(if (isSaving) "保存中" else confirmText)
                }
            }
        }
    }
}
