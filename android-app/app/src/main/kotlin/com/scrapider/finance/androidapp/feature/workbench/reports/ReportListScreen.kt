package com.scrapider.finance.androidapp.feature.workbench.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.scrapider.finance.androidapp.R
import com.scrapider.finance.androidapp.designsystem.LocalFinanceDimensions
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing

@Composable
internal fun ReportListScreen(
    state: ReportsUiState,
    typeChoices: List<ReportChoice>,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onTypeSelected: (String) -> Unit,
    onOpenLatest: (ReportTarget) -> Unit,
    onOpenHistory: (ReportTarget) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    var showFilter by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top,
    ) {
        ReportTopBar(
            title = "研究报告",
            onBack = onBack,
            actions = {
                IconButton(
                    onClick = onRefresh,
                    enabled = !state.isLoading,
                    modifier = Modifier.semantics {
                        contentDescription = if (state.isLoading) "正在刷新研究报告" else "刷新研究报告"
                    },
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(dimensions.iconSize),
                            strokeWidth = dimensions.outlineWidth,
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_phosphor_arrows_clockwise),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                TextButton(
                    onClick = { showFilter = true },
                    modifier = Modifier.heightIn(min = dimensions.minTouchTarget),
                ) {
                    Text("筛选")
                }
            },
        )

        ReportSearchField(
            query = state.query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.xs),
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = spacing.xl,
                top = spacing.xs,
                end = spacing.xl,
                bottom = spacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            item(key = "report-list-heading", contentType = "heading") {
                Text(
                    text = "按标的查看最新报告" + typeChoices.find { it.value == state.targetType && it.value.isNotBlank() }
                        ?.let { " · ${it.label}" }.orEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.xs, bottom = spacing.xs),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (state.errorMessage.isNotBlank()) {
                item(key = "report-list-error", contentType = "error") {
                    ReportErrorState(text = state.errorMessage, onRetry = onRefresh)
                }
            }

            when {
                state.isLoading && state.targets.isEmpty() -> {
                    item(key = "report-list-loading", contentType = "loading") {
                        ReportLoadingState(text = "正在加载研究报告")
                    }
                }

                !state.hasLoaded && state.targets.isEmpty() && state.errorMessage.isBlank() -> {
                    item(key = "report-list-loading-initial", contentType = "loading") {
                        ReportLoadingState(text = "正在加载研究报告")
                    }
                }

                state.hasLoaded && state.targets.isEmpty() && state.errorMessage.isBlank() -> {
                    item(key = "report-list-empty", contentType = "empty") {
                        val hasFilter = state.query.isNotBlank() || state.targetType.isNotBlank()
                        ReportEmptyState(
                            text = if (hasFilter) {
                                "没有匹配的报告，试试其他名称、代码或筛选条件"
                            } else {
                                "暂未生成研究报告"
                            },
                        )
                    }
                }

                else -> {
                    items(
                        items = state.targets,
                        key = { it.key },
                        contentType = { "report-target" },
                    ) { target ->
                        ReportTargetRow(
                            target = target,
                            onOpenLatest = { onOpenLatest(target) },
                            onOpenHistory = { onOpenHistory(target) },
                        )
                    }
                }
            }

            if (state.nextPage != null && state.targets.isNotEmpty()) {
                item(key = "report-load-more", contentType = "load-more") {
                    when {
                        state.isLoadingMore -> ReportLoadingState(text = "正在加载更多")
                        state.moreError.isNotBlank() -> ReportErrorState(
                            text = state.moreError,
                            onRetry = onLoadMore,
                        )
                        else -> TextButton(
                            onClick = onLoadMore,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = dimensions.minTouchTarget),
                        ) {
                            Text("加载更多")
                        }
                    }
                }
            }
        }

        Button(
            onClick = onGenerate,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.xl, vertical = spacing.sm)
                .heightIn(min = dimensions.controlHeight),
        ) {
            Text("生成报告")
        }
    }

    if (showFilter) {
        ReportFilterSheet(
            choices = typeChoices,
            selectedValue = state.targetType,
            onDismiss = { showFilter = false },
            onTypeSelected = onTypeSelected,
        )
    }
}
