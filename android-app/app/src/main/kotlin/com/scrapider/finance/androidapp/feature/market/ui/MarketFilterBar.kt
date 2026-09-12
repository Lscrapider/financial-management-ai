package com.scrapider.finance.androidapp.feature.market.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.scrapider.finance.androidapp.designsystem.LocalFinanceDimensions
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import com.scrapider.finance.androidapp.feature.market.MarketSortOption
import com.scrapider.finance.androidapp.feature.market.MarketTargetTypeFilter
import com.scrapider.finance.androidapp.feature.market.MarketWatchGroup
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MarketGroupTabs(
    groups: List<MarketWatchGroup>,
    selectedGroupId: String?,
    onSelectGroup: (String?) -> Unit,
    onManageGroups: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    val labels = remember(groups) { listOf("全部") + groups.map(MarketWatchGroup::name) }
    val selectedIndex = groups.indexOfFirst { group -> group.id == selectedGroupId }
        .takeIf { index -> index >= 0 }
        ?.plus(1)
        ?: 0
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrimaryScrollableTabRow(
            selectedTabIndex = selectedIndex,
            modifier = Modifier.weight(1f),
            containerColor = Color.Transparent,
            contentColor = MiuixTheme.colorScheme.onSurface,
            edgePadding = 0.dp,
            minTabWidth = dimensions.minTouchTarget,
            divider = {},
            indicator = {
                TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(selectedIndex),
                    width = spacing.xxl,
                    height = spacing.xxs,
                    color = MiuixTheme.colorScheme.primary,
                )
            },
        ) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Tab(
                    selected = selected,
                    onClick = { onSelectGroup(groups.getOrNull(index - 1)?.id) },
                    modifier = Modifier.heightIn(min = dimensions.minTouchTarget),
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.md),
                        style = MiuixTheme.textStyles.title3.copy(
                            fontWeight = if (selected) {
                                MaterialTheme.typography.headlineSmall.fontWeight
                            } else {
                                MaterialTheme.typography.bodyMedium.fontWeight
                            },
                        ),
                        color = if (selected) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        },
                        maxLines = 1,
                    )
                }
            }
        }
        TextButton(
            onClick = onManageGroups,
            modifier = Modifier
                .padding(start = spacing.sm)
                .heightIn(min = dimensions.minTouchTarget)
                .semantics { contentDescription = "管理自选分组" },
        ) {
            Text(
                text = "管理",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
internal fun MarketFilterBar(
    selectedFilter: MarketTargetTypeFilter,
    sortOption: MarketSortOption,
    onSelectFilter: (MarketTargetTypeFilter) -> Unit,
    onOpenSort: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filters = remember { MarketTargetTypeFilter.entries.toList() }
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            filters.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { onSelectFilter(filter) },
                    label = {
                        Text(
                            text = filter.label,
                            style = MiuixTheme.textStyles.body2,
                            color = if (selectedFilter == filter) {
                                MiuixTheme.colorScheme.onPrimaryContainer
                            } else {
                                MiuixTheme.colorScheme.onSurfaceVariantSummary
                            },
                            maxLines = 1,
                        )
                    },
                    border = null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Transparent,
                        selectedContainerColor = MiuixTheme.colorScheme.primaryContainer,
                    ),
                )
            }
        }
        IconButton(
            onClick = onOpenSort,
            modifier = Modifier.sizeIn(
                minWidth = dimensions.minTouchTarget,
                minHeight = dimensions.minTouchTarget,
            ),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Sort,
                contentDescription = "排序方式：${sortOption.label}",
                tint = if (sortOption == MarketSortOption.Default) {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                } else {
                    MiuixTheme.colorScheme.primary
                },
            )
        }
    }
}
