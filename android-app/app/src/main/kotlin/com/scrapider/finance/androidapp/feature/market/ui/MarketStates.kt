package com.scrapider.finance.androidapp.feature.market.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text as MaterialText
import androidx.compose.material3.TextButton as MaterialTextButton
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.scrapider.finance.androidapp.designsystem.LocalFinanceDimensions
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarketBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = LocalFinanceSpacing.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.md)
                .padding(bottom = spacing.md),
        ) {
            MaterialText(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = spacing.xs),
            )
            content()
        }
    }
}

@Composable
internal fun MarketLoadingPanel(
    text: String,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dimensions.compactRowHeight),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(size = dimensions.iconSize)
            Text(
                text = text,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body1,
            )
        }
    }
}

@Composable
internal fun MarketEmptyPanel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        BasicComponent(
            title = text,
            titleColor = top.yukonga.miuix.kmp.basic.BasicComponentDefaults.titleColor(
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            ),
        )
    }
}

@Composable
internal fun MarketMissingTargetScreen(
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MarketMissingStateScreen(
        title = "标的设置",
        message = "该标的已不在当前自选池中",
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@Composable
internal fun MarketMissingTargetDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    Scaffold(
        modifier = modifier,
        topBar = { MarketPageTopBar(title = "标的详情", onNavigateBack = onNavigateBack) },
    ) { contentPadding ->
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = spacing.xl, vertical = spacing.section),
        ) {
            BasicComponent(
                title = "当前未找到该标的的行情数据",
                titleColor = top.yukonga.miuix.kmp.basic.BasicComponentDefaults.titleColor(
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                ),
            )
        }
    }
}

@Composable
internal fun MarketConfirmationDialog(
    show: Boolean,
    title: String,
    message: String,
    confirmText: String,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { MaterialText(title, style = MaterialTheme.typography.titleMedium) },
            text = { MaterialText(message, style = MaterialTheme.typography.bodyMedium) },
            dismissButton = {
                MaterialTextButton(
                    onClick = onDismiss,
                    enabled = !isSaving,
                ) {
                    MaterialText("取消")
                }
            },
            confirmButton = {
                MaterialButton(
                    onClick = onConfirm,
                    enabled = !isSaving,
                ) {
                    MaterialText(confirmText)
                }
            }
        )
    }
}

@Composable
private fun MarketMissingStateScreen(
    title: String,
    message: String,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    Scaffold(
        modifier = modifier,
        topBar = { MarketPageTopBar(title = title, onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
    ) { contentPadding ->
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = spacing.xl, vertical = spacing.section),
        ) {
            BasicComponent(
                title = message,
                titleColor = top.yukonga.miuix.kmp.basic.BasicComponentDefaults.titleColor(
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                ),
            )
        }
    }
}
