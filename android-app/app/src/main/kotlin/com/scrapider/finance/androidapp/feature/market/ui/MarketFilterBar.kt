package com.scrapider.finance.androidapp.feature.market.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import com.scrapider.finance.androidapp.feature.market.MarketSortOption
import com.scrapider.finance.androidapp.feature.market.MarketTargetTypeFilter
import com.scrapider.finance.androidapp.feature.market.MarketWatchGroup
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
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
    val labels = remember(groups) { listOf("全部") + groups.map(MarketWatchGroup::name) }
    val selectedIndex = groups.indexOfFirst { group -> group.id == selectedGroupId }
        .takeIf { index -> index >= 0 }
        ?.plus(1)
        ?: 0
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabRow(
            tabs = labels,
            selectedTabIndex = selectedIndex,
            onTabSelected = { index ->
                onSelectGroup(groups.getOrNull(index - 1)?.id)
            },
            modifier = Modifier.weight(1f),
        )
        TextButton(
            text = "管理",
            onClick = onManageGroups,
            modifier = Modifier.padding(start = spacing.sm),
        )
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
    val selectedIndex = filters.indexOf(selectedFilter).coerceAtLeast(0)
    val spacing = LocalFinanceSpacing.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "筛选",
                style = MiuixTheme.textStyles.title3,
                color = MiuixTheme.colorScheme.onSurface,
            )
            TextButton(
                text = "排序：${sortOption.label}",
                onClick = onOpenSort,
                modifier = Modifier.semantics {
                    contentDescription = "排序方式：${sortOption.label}"
                },
            )
        }
        TabRowWithContour(
            tabs = filters.map(MarketTargetTypeFilter::label),
            selectedTabIndex = selectedIndex,
            onTabSelected = { index -> onSelectFilter(filters[index]) },
        )
    }
}
